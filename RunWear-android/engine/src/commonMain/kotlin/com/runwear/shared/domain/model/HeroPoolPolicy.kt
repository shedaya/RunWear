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
 * bundled fallback photo for that temp × weather cell alongside whatever approved
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

/**
 * RW-20 point-grain serving filter (Sol's ruling, 2026-11-02).
 *
 * The pool is stocked, generated, and adjudicated at temperature-BRACKET
 * grain, but the engine prescribes at POINT grain — inside COOL it draws
 * lines the bracket does not have (shorts only at 45°F+, tights below;
 * a shell only when wind or rain calls for one). Bracket-correct stock
 * therefore contradicted the prescription on screen (2026-11-02 field
 * failure: at a feels-like of 39°F with Light Tights prescribed, the
 * rotation dealt three shorts photos, a jacket photo, and a two-runner
 * photo). This filter is the serving-side correction: a pool row may be
 * served only when its ADJUDICATED garment classes and person count
 * agree with the exact prescription for the current conditions.
 *
 * Hard rules (a failure on any one rejects the row):
 *  - person_count: a row adjudicated with 2+ people never serves.
 *    (Multi-person stock is ALSO retired server-side; this is the
 *    belt-and-braces client rule.)
 *  - bottom: the photo's bottom class must sit in the prescribed
 *    bottom's family — shorts family {shorts, short_shorts} for
 *    SHORTS/SHORT_SHORTS prescriptions, tights family {tights, capris}
 *    for tights prescriptions (capris count as leg coverage either
 *    way). An unreadable bottom rejects: the bottom is the most
 *    visible garment and the field failure's core contradiction.
 *  - outer: a shell/jacket in the photo iff the prescription has an
 *    outer layer. (The 39°F jacket photo was exactly this failure.)
 *  - top base: when the enumerator could read the base layer it must
 *    equal the prescribed base's class (sleeveless/short/long). When
 *    it could not be read, the row passes only if a jacket covers the
 *    torso (outer seen) — an unseen base under a prescribed jacket is
 *    not a visible contradiction; an unseen base with NO jacket means
 *    the torso garment is unknown, so the row rejects.
 *
 * Head and hands ARE enforced (RW-23 ruling, Sol, 2026-11-02: no
 * waiver). Serving enforces the full prescription including head and
 * hands: a candidate missing a prescribed accessory, or wearing an
 * unprescribed one, does not serve. Cells will empty more often and
 * cascade/fall back — that is the accepted cost. (The RW-20 waiver
 * let the Juneau field failure through: four bare-handed photos
 * served when light gloves were prescribed.) Mid-layer remains
 * unenforced, and accessories (sunglasses/gaiter) are not head/hands
 * slots.
 *
 * HANDWEAR SUBSTITUTION (Sol's ruling, 2026-11-04 ~22:23 CST): a
 * candidate wearing GLOVES passes the hands slot when MITTENS are
 * prescribed — handwear form alone is not a failure, and no truthful
 * mittens photo exists in the stock (the generator renders fingered
 * gloves for every mittens construction tried). The substitution is
 * ONE direction only: a mittens candidate does NOT satisfy a gloves
 * prescription. Every other slot is judged exactly as before.
 *
 * Rows with no adjudicated classes at all fail closed: every approved
 * row carries adjudicated (photo) classes after the RW-20 backfill,
 * and rows whose classes came from their stored prompt are judged by
 * the same rules against those classes.
 */
object HeroPointGrainPolicy {

    fun matches(
        personCount: Int?,
        topBaseClass: String?,
        outerClass: Boolean?,
        bottomClass: String?,
        prescribedTopBase: ClothingItem,
        prescribedTopOuter: ClothingItem?,
        prescribedBottom: ClothingItem,
        headClass: String?,
        handsClass: String?,
        prescribedHead: ClothingItem?,
        prescribedHands: ClothingItem?
    ): Boolean {
        // Person count: adjudicated multi-person stock never serves.
        if (personCount != null && personCount >= 2) return false

        // Bottom: family agreement with the prescription.
        val bottomOk = when (prescribedBottom) {
            ClothingItem.SHORT_SHORTS, ClothingItem.SHORTS ->
                bottomClass == "shorts" || bottomClass == "short_shorts"
            ClothingItem.CAPRIS ->
                bottomClass == "capris" || bottomClass == "tights"
            ClothingItem.LIGHT_TIGHTS, ClothingItem.THERMAL_TIGHTS ->
                bottomClass == "tights" || bottomClass == "capris"
            // The prescription's bottom slot only ever holds a bottom
            // item; anything else matches no photo.
            else -> false
        }
        if (!bottomOk) return false

        // Outer layer: present in the photo iff prescribed.
        val outerSeen = outerClass == true
        if ((prescribedTopOuter != null) != outerSeen) return false

        // Top base: exact class when readable; when unreadable, only a
        // jacket over the torso makes the row safe to serve.
        if (topBaseClass != null) {
            val expected = when (prescribedTopBase) {
                ClothingItem.TANK_TOP -> "sleeveless"
                ClothingItem.SHORT_SLEEVE -> "short"
                else -> "long"
            }
            if (topBaseClass != expected) return false
        } else if (!outerSeen) {
            return false
        }

        // Head (RW-23): exact recorded class for the prescribed item;
        // no prescription means no head gear may be recorded.
        val headOk = if (prescribedHead == null) {
            headClass == null
        } else {
            val expectedHead = when (prescribedHead) {
                ClothingItem.VISOR -> "visor"
                ClothingItem.BASEBALL_CAP -> "cap"
                ClothingItem.HEADBAND -> "headband"
                ClothingItem.LIGHT_BEANIE -> "beanie"
                ClothingItem.THERMAL_BEANIE -> "beanie"
                ClothingItem.BALACLAVA -> "balaclava"
                else -> null
            }
            expectedHead != null && headClass == expectedHead
        }
        if (!headOk) return false

        // Hands (RW-23): same rule as head — with the one
        // gloves-for-mittens substitution of Sol's 2026-11-04
        // ruling (see the class doc): MITTENS prescribed accepts a
        // "mittens" OR a "gloves" candidate; gloves prescriptions
        // still require gloves exactly (no reverse substitution).
        val handsOk = if (prescribedHands == null) {
            handsClass == null
        } else {
            val expectedHands = when (prescribedHands) {
                ClothingItem.LIGHT_GLOVES -> "gloves"
                ClothingItem.THERMAL_GLOVES -> "gloves"
                ClothingItem.MITTENS -> "mittens"
                else -> null
            }
            expectedHands != null && (handsClass == expectedHands ||
                (prescribedHands == ClothingItem.MITTENS && handsClass == "gloves"))
        }
        if (!handsOk) return false

        return true
    }

    /**
     * Bridge-friendly form of [matches] for the Apple seam, where the
     * Swift side holds item identities as enum-case KEYS (its
     * ClothingItem.key) and nullable Kotlin number/boolean boxes are
     * awkward to construct. A pure adapter — same core, no second
     * implementation to drift:
     *  - [personCount]: negative means unrecorded (bridges as Int32).
     *  - [outerClass]: "true" / "false" / null (unreadable).
     *  - the prescribed*Key values are ClothingItem case names.
     */
    fun matchesByKey(
        personCount: Int,
        topBaseClass: String?,
        outerClass: String?,
        bottomClass: String?,
        prescribedTopBaseKey: String,
        prescribedTopOuterKey: String?,
        prescribedBottomKey: String,
        headClass: String?,
        handsClass: String?,
        prescribedHeadKey: String?,
        prescribedHandsKey: String?
    ): Boolean = matches(
        personCount = if (personCount < 0) null else personCount,
        topBaseClass = topBaseClass,
        outerClass = outerClass?.let { it.equals("true", ignoreCase = true) },
        bottomClass = bottomClass,
        prescribedTopBase = ClothingItem.valueOf(prescribedTopBaseKey),
        prescribedTopOuter = prescribedTopOuterKey?.let { ClothingItem.valueOf(it) },
        prescribedBottom = ClothingItem.valueOf(prescribedBottomKey),
        headClass = headClass,
        handsClass = handsClass,
        prescribedHead = prescribedHeadKey?.let { ClothingItem.valueOf(it) },
        prescribedHands = prescribedHandsKey?.let { ClothingItem.valueOf(it) }
    )
}
