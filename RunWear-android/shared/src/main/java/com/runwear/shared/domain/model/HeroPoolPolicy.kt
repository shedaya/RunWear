package com.runwear.shared.domain.model

/**
 * Policy for the Supabase hero-image pool: review gating, the curated-photo
 * rotation floor, and the generation-prompt quality block. Pure functions —
 * covered by HeroPoolPolicyTest.
 *
 * REVIEW GATE (2026-10-27). generated_images gains a `review_status` column
 * via supabase/migrations/20261027120000_hero_image_review_gate.sql. The
 * edge function inserts every newly generated image as 'pending', and a
 * pending (or rejected) image is never served — a human approves it first.
 * Rows written before the migration carry no value (decoded as null) and
 * count as approved, so the existing pool serves exactly as before until
 * the migration is applied in the Supabase dashboard.
 *
 * ROTATION FLOOR (2026-10-27). Pool depth per combination is thin (median
 * 5 across the 192-cell space the client can query, 45 cells empty), and
 * the client re-rolls uniformly with no memory of the last image served —
 * so a run of repeats, including of one bad image, is normal behavior for
 * the old design. The floor guarantees the most common cells (every temp
 * bracket × CLEAR/CLOUDY × DAWN/MIDDAY/DUSK) always rotate in the curated
 * Unsplash photo for that temp × weather cell alongside whatever approved
 * AI images exist, instead of the photo appearing only as an error path.
 */
object HeroPoolPolicy {

    const val STATUS_APPROVED = "approved"
    const val STATUS_PENDING = "pending"
    const val STATUS_REJECTED = "rejected"

    /** Only approved images may be served; null = pre-migration legacy row. */
    fun isServable(reviewStatus: String?): Boolean =
        reviewStatus == null || reviewStatus.equals(STATUS_APPROVED, ignoreCase = true)

    /** The common cells that always get the curated photo in rotation. */
    fun isFloorCell(
        tempBracket: TempBracket,
        weather: HeroWeatherCondition,
        timeOfDay: TimeOfDay
    ): Boolean =
        (weather == HeroWeatherCondition.CLEAR || weather == HeroWeatherCondition.CLOUDY) &&
            (timeOfDay == TimeOfDay.DAWN || timeOfDay == TimeOfDay.MIDDAY || timeOfDay == TimeOfDay.DUSK)

    /** Add the curated URL to the rotation exactly once. */
    fun mergeFloorCandidate(poolUrls: List<String>, curatedUrl: String?): List<String> =
        if (curatedUrl != null && curatedUrl !in poolUrls) poolUrls + curatedUrl else poolUrls

    /**
     * Anatomy/composition constraints appended to every generation prompt.
     * FLUX (the generator, via Replicate flux-schnell) has no negative-prompt
     * channel, so the guidance is phrased as positive constraints plus one
     * explicit Avoid clause. The 2026-10-27 motivating failure: a back-view
     * runner whose arm bent across his back and clutched his own shoulder
     * blade — served repeatedly from the MALE_CLEAR_MILD_DUSK pool.
     */
    const val QUALITY_MARKER = "ANATOMY & QUALITY:"

    const val QUALITY_SUFFIX: String =
        " $QUALITY_MARKER anatomically correct human body with natural proportions; " +
            "arms bent at the elbows and swinging naturally beside the torso, hands relaxed " +
            "at the runner's sides — never reaching across the back or touching the shoulders; " +
            "each hand with five correct fingers; legs in a natural mid-stride gait with knees " +
            "and feet pointing in the direction of travel; if the runner is seen from behind, " +
            "arms, legs and hands must remain anatomically coherent from the back view. " +
            "Photorealistic, shot on a full-frame camera. Avoid: distorted or merged limbs, " +
            "extra fingers, hands or limbs, warped joints, backwards-bending arms."

    /** Append the quality block unless a prompt already carries it. */
    fun ensureQualitySuffix(prompt: String): String =
        if (prompt.contains(QUALITY_MARKER)) prompt else prompt + QUALITY_SUFFIX
}
