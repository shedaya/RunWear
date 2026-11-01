package com.runwear.app.makeitme

/**
 * Pure decision logic for RW-14 — kept free of Android/network types so
 * the acceptance-critical rules are unit-testable on the JVM:
 *
 * - [PersonalHeroCachePolicy]: the versioned-marker cache (acceptance 16).
 * - [MakeItMeUpsellRules]: the 6th-cell offer's frequency rules (spec §5.2).
 * - [PhotoQualityPolicy]: the in-flow reference-photo quality check
 *   (acceptance 2, spec §7 C3).
 * - [BillingIdentityPolicy]: when RevenueCat must be (re)linked to the
 *   Supabase user id (acceptance 17).
 * - [PhotoIntakePolicy]: which source a photo add comes from —
 *   camera beside library (RW-16.14).
 * - [SignedUrlPolicy]: how a Storage signedURL becomes a display
 *   URL (RW-16.15).
 * - [PersonalLookStatusPolicy]: the server's per-cell look state
 *   (RW-16.16) mapped onto the status the hero renders.
 */

/**
 * RW-16.16: the personal-look status for one cell, as the SERVER
 * reports it in get-personal-hero's `look_state` / `look_reason`
 * (v5+). This — not any local bookkeeping — decides what the user
 * sees at the hero: the persisted requested-cell set once suppressed
 * both re-requests AND the progress line for a cell, so a generation
 * the save flow had requested arrived (or failed) in total silence
 * (the G4 defect; spec §7 G4 superseded, G5/G6 added Nov 1, 2026).
 *
 * The server owns the truth: a look job in flight is MAKING; a
 * verified hero serving with nothing in flight is READY (the hero
 * itself is the state — no label); a terminal failure is FAILED
 * with a coarse reason class (the raw gate/moderation strings never
 * cross the wire); anything else is NONE.
 */
sealed interface PersonalLookStatus {
    data object None : PersonalLookStatus
    data object Making : PersonalLookStatus
    data object Ready : PersonalLookStatus
    data class Failed(val reason: Reason) : PersonalLookStatus

    enum class Reason { QUALITY_REFUSED, SERVICE_REFUSED, ERROR }
}

object PersonalLookStatusPolicy {

    /**
     * Map one get-personal-hero payload onto the status. A payload
     * with no state field at all (a pre-v5 server) falls back to the
     * one inference that was always safe: found means READY, a miss
     * means NONE.
     */
    fun fromServer(found: Boolean, lookState: String?, lookReason: String?): PersonalLookStatus =
        when (lookState) {
            "making" -> PersonalLookStatus.Making
            "ready" -> PersonalLookStatus.Ready
            "failed" -> PersonalLookStatus.Failed(reasonFromServer(lookReason))
            "none" -> PersonalLookStatus.None
            else -> if (found) PersonalLookStatus.Ready else PersonalLookStatus.None
        }

    fun reasonFromServer(lookReason: String?): PersonalLookStatus.Reason =
        when (lookReason) {
            "quality_refused" -> PersonalLookStatus.Reason.QUALITY_REFUSED
            "service_refused" -> PersonalLookStatus.Reason.SERVICE_REFUSED
            else -> PersonalLookStatus.Reason.ERROR
        }
}

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

/**
 * Acceptance 17: RevenueCat's app_user_id must BE the Supabase user id,
 * so a Play purchase is findable by the backend (the webhook consumer
 * and the reconcile-personal-entitlement backstop both key on the
 * Supabase uid). Until the client links the two, the SDK runs under a
 * generated "$RCAnonymousID:…" id and Android purchases are invisible
 * to the server — the exact failure RW-16.12 proved on iOS's billing
 * hop. (iOS configures Purchases with the Supabase uid from the start;
 * Android configures at process start, before any session exists, so
 * the link happens via logIn as soon as the anonymous session lands.)
 *
 * The rule: link exactly when there is a Supabase uid to link TO and
 * RevenueCat is not already running under it. Re-linking the same id
 * is skipped — logIn is not free (it can merge identities), so the
 * sync points (bootstrap, entitlement change, purchase) stay cheap
 * and idempotent.
 */
object BillingIdentityPolicy {

    fun shouldLogIn(currentRcAppUserId: String?, supabaseUserId: String?): Boolean {
        val uid = supabaseUserId?.takeIf { it.isNotBlank() } ?: return false
        return currentRcAppUserId != uid
    }
}

/**
 * RW-16.14: the photo intake offers the camera beside the library
 * (Sol, Oct 31: "Both apps should allow camera not just photo
 * roll"). The capture runs through the platform camera intent
 * (ActivityResultContracts.TakePicture into a FileProvider file),
 * which needs NO camera permission in the manifest and returns a
 * full-resolution photo — the same Uri shape the library picker
 * produces, so a capture enters the identical PhotoQualityChecker
 * path with no second pipeline.
 *
 * The rule mirrors the iOS PhotoIntakeRouting: a library request is
 * never diverted; a camera request goes to the camera when one can
 * be launched, and otherwise falls back to the library WITH the
 * honest note — never a dead end. (The intent route has no
 * permission-denied state; the only can't is "no camera to launch",
 * which the screens layer also covers if the launch itself throws.)
 */
object PhotoIntakePolicy {

    enum class Source { CAMERA, LIBRARY }

    sealed interface Route {
        data object Camera : Route
        data object Library : Route

        /** Camera asked for but unusable: library, with the reason shown. */
        data class LibraryWithNote(val note: String) : Route
    }

    /** Verbatim-identical to the iOS routing's unavailable note. */
    const val CAMERA_UNAVAILABLE_NOTE = "No camera is available here — choose from your library instead."

    fun route(requested: Source, cameraAvailable: Boolean): Route = when (requested) {
        Source.LIBRARY -> Route.Library
        Source.CAMERA ->
            if (cameraAvailable) Route.Camera
            else Route.LibraryWithNote(CAMERA_UNAVAILABLE_NOTE)
    }
}

/**
 * RW-16.15: the Storage API's `signedURL` is relative to the
 * STORAGE endpoint root (`/storage/v1`), NOT the host root. vc21
 * joined it to the bare host, so every Your Photos tile URL 404'd
 * and Coil painted nothing over the bare tile box — the photos had
 * saved fine; they could simply never display (proven live in the
 * RW-16.15 trace: host-root join → HTTP 404, `/storage/v1` join →
 * 206 image/jpeg, against the user's real reference photo).
 *
 * The rule: absolute URLs pass through untouched; a relative
 * signedURL resolves under `/storage/v1`.
 */
object SignedUrlPolicy {

    fun resolve(baseUrl: String, signedURL: String): String = when {
        signedURL.startsWith("http") -> signedURL
        signedURL.startsWith("/") -> "$baseUrl/storage/v1$signedURL"
        else -> "$baseUrl/storage/v1/$signedURL"
    }
}
