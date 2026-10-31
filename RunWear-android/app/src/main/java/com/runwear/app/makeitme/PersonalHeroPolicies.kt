package com.runwear.app.makeitme

/**
 * Pure decision logic for RW-14 — kept free of Android/network types so
 * the acceptance-critical rules are unit-testable on the JVM:
 *
 * - [PersonalHeroCachePolicy]: the versioned-marker cache (acceptance 16).
 * - [MakeItMeUpsellRules]: the 6th-cell offer's frequency rules (spec §5.2).
 * - [PhotoQualityPolicy]: the in-flow reference-photo quality check
 *   (acceptance 2, spec §7 C3).
 */

/**
 * The named cache trap (acceptance 16): a stored personalized image is
 * replaced server-side (matrix cell lands, replenishment variant rotates
 * in, flagged regeneration completes) and the cell's version marker bumps.
 * The client must show the NEW image on next display — never keep serving
 * a stale cached URL, and never re-download when nothing changed.
 *
 * The server hands out signed URLs with a 1-hour TTL, so a cached URL is
 * only reusable inside a shorter freshness window AND at the same version.
 */
object PersonalHeroCachePolicy {

    /** Reuse window for a cached signed URL (server TTL is 1 h). */
    const val URL_FRESH_MS: Long = 50L * 60L * 1000L

    data class CachedEntry(
        val version: Int,
        val imageUrl: String,
        val fetchedAtMs: Long
    )

    sealed interface Decision {
        /** Show this URL (from cache or freshly served — same handling). */
        data class Serve(val imageUrl: String, val version: Int) : Decision

        /** No verified personal hero for the cell: the pool hero serves. */
        data object NoPersonalHero : Decision
    }

    /**
     * Decide what to display for one cell.
     *
     * @param cached the locally persisted entry for the cell, if any.
     * @param found the server's `found` flag from get-personal-hero.
     * @param serverVersion the cell's current version marker (may be
     *   non-null even on a miss, when the cell row exists but no hero is
     *   verified yet).
     * @param serverUrl the freshly signed URL when [found] is true.
     */
    fun decide(
        cached: CachedEntry?,
        found: Boolean,
        serverVersion: Int?,
        serverUrl: String?,
        nowMs: Long
    ): Decision {
        if (!found || serverUrl == null) return Decision.NoPersonalHero
        val version = serverVersion ?: cached?.version ?: 1
        if (cached != null &&
            cached.version == version &&
            cached.imageUrl.isNotBlank() &&
            nowMs - cached.fetchedAtMs in 0 until URL_FRESH_MS
        ) {
            // Same version, URL still fresh: reuse — no re-download, and
            // (critically) the version check above means a REPLACED image
            // (version bump) never takes this branch.
            return Decision.Serve(cached.imageUrl, version)
        }
        return Decision.Serve(serverUrl, version)
    }
}

/**
 * Spec §5.2 frequency rules for the 6th-new-cell subscription card:
 * shown when a free user's allowance is spent and a genuinely new cell
 * has no personal hero; if dismissed, it may reappear on a LATER new
 * cell at most once a week, and never twice for the same cell.
 */
object MakeItMeUpsellRules {

    const val WEEK_MS: Long = 7L * 24L * 3600L * 1000L
    const val FREE_ALLOWANCE: Int = 5

    fun shouldShow(
        personalizationOn: Boolean,
        entitled: Boolean,
        freeGenerationsUsed: Int,
        cellHasPersonalHero: Boolean,
        cellGenerationPending: Boolean,
        combinationId: String,
        lastDismissedAtMs: Long,
        lastDismissedCell: String?,
        nowMs: Long
    ): Boolean {
        if (!personalizationOn || entitled) return false
        if (freeGenerationsUsed < FREE_ALLOWANCE) return false
        if (cellHasPersonalHero || cellGenerationPending) return false
        if (lastDismissedCell != null && lastDismissedCell == combinationId) return false
        if (lastDismissedAtMs > 0L && nowMs - lastDismissedAtMs < WEEK_MS) return false
        return true
    }
}

/** The four in-flow rejection classes (spec §7 C3a–C3d). */
enum class PhotoRejection {
    FACE_UNCLEAR,
    MULTIPLE_PEOPLE,
    TOO_DARK,
    TOO_CLOSE
}

/**
 * The reference-photo quality gate's decision (acceptance 2): a failing
 * photo is rejected IN the flow with a specific reason, never uploaded.
 * Inputs are measured by [PhotoQualityChecker] (ML Kit face detection +
 * a bitmap luminance sample); the decision itself is pure.
 *
 * Order matters: darkness is judged first because a too-dark photo also
 * yields "no face found", and the honest reason is the darkness (C3c).
 */
object PhotoQualityPolicy {

    /** Mean luminance (0.0–1.0) below which a photo is "too dark". */
    const val MIN_MEAN_LUMINANCE: Double = 0.16

    /** A face wider than this fraction of the frame is "cropped too close". */
    const val MAX_FACE_WIDTH_FRACTION: Float = 0.80f

    fun decide(
        faceCount: Int,
        largestFaceWidthFraction: Float,
        meanLuminance: Double
    ): PhotoRejection? {
        if (meanLuminance < MIN_MEAN_LUMINANCE) return PhotoRejection.TOO_DARK
        if (faceCount == 0) return PhotoRejection.FACE_UNCLEAR
        if (faceCount > 1) return PhotoRejection.MULTIPLE_PEOPLE
        if (largestFaceWidthFraction > MAX_FACE_WIDTH_FRACTION) return PhotoRejection.TOO_CLOSE
        return null
    }

    /** The spec §7 C3 string for a rejection. */
    fun stringFor(rejection: PhotoRejection): String = when (rejection) {
        PhotoRejection.FACE_UNCLEAR -> MakeItMeStrings.C3A_REJECT_FACE_UNCLEAR
        PhotoRejection.MULTIPLE_PEOPLE -> MakeItMeStrings.C3B_REJECT_MULTIPLE
        PhotoRejection.TOO_DARK -> MakeItMeStrings.C3C_REJECT_TOO_DARK
        PhotoRejection.TOO_CLOSE -> MakeItMeStrings.C3D_REJECT_TOO_CLOSE
    }
}
