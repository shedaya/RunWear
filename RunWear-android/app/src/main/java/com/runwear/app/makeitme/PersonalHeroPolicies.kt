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
 * RW-26: HOW the personal hero on screen was served, under the
 * convergence architecture (design §1, frames 19–21):
 *
 * - EXACT — the cell's own banked photo. No badge.
 * - SCAFFOLD — the user's OWN banked photo carrying the exact
 *   prescription under the nearest conditions, served immediately
 *   while the exact photo generates in the background. It shows AT
 *   ONCE with an honest badge — and the surface stays SILENT: no
 *   upsell copy, no meter draped over home (§9).
 * - STOCK_QUIET — a gate-failed cell serves stock quietly: the home
 *   screen never badges it; the explanation lives one tap away in
 *   the looks library (frame 21).
 */
enum class PersonalServeKind { EXACT, SCAFFOLD, STOCK_QUIET }

object ScaffoldRenderPolicy {

    fun kindFor(
        status: PersonalLookStatus,
        serveKindRaw: String?,
        found: Boolean
    ): PersonalServeKind = when {
        status is PersonalLookStatus.Failed -> PersonalServeKind.STOCK_QUIET
        found && serveKindRaw == "scaffold" -> PersonalServeKind.SCAFFOLD
        else -> PersonalServeKind.EXACT
    }

    /** The home badge for a serve kind — factual only, never a pitch. */
    fun homeBadge(kind: PersonalServeKind): String? = when (kind) {
        PersonalServeKind.SCAFFOLD -> "Your photo — made for close-by weather"
        PersonalServeKind.EXACT -> null
        PersonalServeKind.STOCK_QUIET -> null
    }
}

/**
 * GEN-3 (Y-1 parity, iOS census 2026-11-04): the REFERENCE-EPOCH
 * guard at the personal serving seam.
 *
 * The defect (observed on iOS, mechanism platform-universal): a user
 * uploads a new reference photo and commissions his first look; the
 * server answers with a BANKED photo generated from a SUPERSEDED
 * reference set (in the iOS recording, another person's photo from
 * an earlier version's references), and the client presents it as
 * the finished result of the look just commissioned. Retention of
 * the bank is Sol's settled ruling — SERVING a superseded-epoch
 * photo as the user's current representation is the defect.
 *
 * The Android bank lookup is NOT epoch-keyed: get-personal-hero is
 * keyed by combination_id alone, and its payload (found, image_url,
 * version, hero_id, look_state, serve_kind) carries no reference
 * epoch, no generation timestamp, and no provenance for which
 * reference set produced the hero. The reference storage paths are
 * deterministic ({uid}/refs/ref-N.jpg, upserted in place), so the
 * path set cannot key an epoch either. The guard therefore works
 * from what the client itself knows:
 *
 *  - EPOCH: a counter the client increments every time IT registers
 *    a reference set (uploadAndRegisterReferences). Registering new
 *    references is the epoch boundary; nothing else moves it (place
 *    switches, relaunches, and reinstalls with the references
 *    untouched do not — the bank belongs to the only epoch such a
 *    device knows, which is what lets a returning user be served
 *    his own existing photo for the home cell).
 *  - ACCEPTED RECORD: per cell, the hero (id + version) this client
 *    last accepted as a serve, and the epoch it accepted it under
 *    (the versioned cell cache carries it).
 *  - WITNESS: per cell, whether a generation lifecycle was observed
 *    in the current epoch — a request the server QUEUED (not
 *    "already available"), or a look state of MAKING read in this
 *    epoch — before the hit appeared. A delivery through a witnessed
 *    lifecycle was generated from the current reference set by
 *    construction.
 *
 * The rules, in order:
 *  1. The hit IS the accepted hero and it was accepted under the
 *     current epoch → serve (the standing current-epoch photo).
 *  2. The hit IS the accepted hero but was accepted under an OLDER
 *     epoch → BLOCK. That is the provably superseded photo itself:
 *     the same banked image, carried over the boundary.
 *  3. The hit is a DIFFERENT hero (or the cell has no accepted
 *     record):
 *     a. a generation lifecycle was witnessed for the cell in the
 *        current epoch → serve, and accept it under this epoch;
 *     b. no epoch boundary has ever been recorded on this device
 *        (current epoch 0) → serve, and accept it — the bank can
 *        only belong to the one epoch this device knows;
 *     c. otherwise → BLOCK. An unproven hit in a post-boundary
 *        epoch may be exactly the superseded bank photo this device
 *        never accepted (the Y-1 shape: fresh install, new selfie
 *        registered, server answers "already available" with the
 *        old set's photo). It becomes servable only via (a): a real
 *        generation from the current set, witnessed. While blocked,
 *        the truthful stock photo serves and no finished claim is
 *        made — the look stays MAKING from the user's seat.
 *
 * A blocked hit never writes the cell cache: the accepted record of
 * the old epoch remains the baseline that proves supersession.
 * (The durable fix is server-side — get-personal-hero should not
 * return superseded-epoch heroes as the current representation, or
 * should expose the hero's reference epoch; this guard enforces the
 * invariant from the client until then.)
 */
object ReferenceEpochPolicy {

    /** Identity of one banked hero for supersession purposes: its
     * version, plus its row id when both sides know one (a cached
     * entry persisted before hero ids were recorded re-derives its
     * id on the next serve and must still match itself). */
    fun isSameHero(
        hitHeroId: String?,
        hitVersion: Int,
        acceptedHeroId: String?,
        acceptedVersion: Int
    ): Boolean =
        hitVersion == acceptedVersion &&
            (hitHeroId == acceptedHeroId || hitHeroId == null || acceptedHeroId == null)

    fun mayServe(
        hitHeroId: String?,
        hitVersion: Int,
        acceptedHeroId: String?,
        acceptedVersion: Int?,
        acceptedEpoch: Int?,
        currentEpoch: Int,
        generationWitnessedThisEpoch: Boolean
    ): Boolean {
        if (acceptedVersion != null &&
            isSameHero(hitHeroId, hitVersion, acceptedHeroId, acceptedVersion)
        ) {
            // Rules 1–2: the standing photo serves only under the
            // epoch it was accepted in.
            return (acceptedEpoch ?: 0) == currentEpoch
        }
        // Rule 3: a hero this client has not accepted before.
        if (generationWitnessedThisEpoch) return true
        return currentEpoch == 0
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
        val fetchedAtMs: Long,
        /**
         * RW-16.27: the backend hero row id — what a personal flag
         * (flag_hero) addresses. The iOS cache entry carries it for
         * the same reason; Android's entry lacked it, which is part
         * of why Android had no personal-flag path. Null for entries
         * persisted before RW-16.27 (they re-derive it on the next
         * fresh serve).
         */
        val heroId: String? = null,
        /**
         * GEN-3 (Y-1): the reference epoch this entry was ACCEPTED
         * under ([ReferenceEpochPolicy]). Entries persisted before
         * the guard existed parse as epoch 0 — the epoch of a device
         * that has never recorded a boundary — and become
         * superseded the moment a boundary is recorded, exactly
         * like any other pre-boundary acceptance.
         */
        val epoch: Int = 0
    )

    sealed interface Decision {
        /** Show this URL (from cache or freshly served — same handling). */
        data class Serve(
            val imageUrl: String,
            val version: Int,
            val heroId: String? = null
        ) : Decision

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
     * @param serverHeroId the serving hero's backend row id when
     *   [found] is true (get-personal-hero's `hero_id`).
     */
    fun decide(
        cached: CachedEntry?,
        found: Boolean,
        serverVersion: Int?,
        serverUrl: String?,
        nowMs: Long,
        serverHeroId: String? = null
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
            return Decision.Serve(
                cached.imageUrl, version,
                heroId = cached.heroId ?: serverHeroId
            )
        }
        return Decision.Serve(serverUrl, version, heroId = serverHeroId)
    }
}

/**
 * RW-16.27: why a user flagged one of their OWN personalized heroes —
 * the Android mirror of iOS's PersonalHeroFlagReason (same codes,
 * same labels, same order). [code] is the value the backend's
 * flag_hero action validates (the pool flag vocabulary plus the
 * personal one); the first entry is the spec §4 control, verbatim.
 * For a personal photo every reason routes to flag_hero, never to
 * the pool insert — the pool payload cannot identify a personal
 * hero and would retire nothing.
 */
enum class PersonalHeroFlagReason(val code: String, val label: String) {
    DOESNT_LOOK_LIKE_ME("doesnt_look_like_me", "This doesn't look like me"),
    LOOKS_BROKEN("looks_broken", "Looks broken"),
    INAPPROPRIATE("inappropriate", "Inappropriate"),
    OUTFIT_MISMATCH("outfit_mismatch", "Doesn't match the outfit")
}

/**
 * WS-6 (D-17): the personal flag sheet's presentation hierarchy,
 * per design frame 45 — the lead phrase is the sheet's HEADING,
 * not a row under a generic header, and its consequence is stated
 * in the sheet ("We'll stop showing this photo and make it
 * again."). The heading is itself the lead reason's control
 * (tapping it files DOESNT_LOOK_LIKE_ME); the remaining reasons
 * follow as rows. Pure, so the gate pins the hierarchy.
 */
object PersonalFlagSheetContent {

    /** The reason the heading states — and selects. */
    val headingReason: PersonalHeroFlagReason = PersonalHeroFlagReason.DOESNT_LOOK_LIKE_ME

    /** The heading text: the lead phrase itself. */
    val heading: String = headingReason.label

    /** The consequence line under the heading (frame 45, verbatim). */
    const val HEADING_CONSEQUENCE: String = "We'll stop showing this photo and make it again."

    /** The reasons that remain rows, in their settled order. */
    val rowReasons: List<PersonalHeroFlagReason> =
        PersonalHeroFlagReason.entries.filter { it != headingReason }
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
    TOO_CLOSE,
    /** WS-6 (F-1): the subject face's in-plane roll is beyond the
     *  gate and was not a correctable quarter-turn — the photo is
     *  sideways/tilted and must be retaken upright. */
    ROTATED
}

/**
 * The reference-photo quality gate's decision (acceptance 2): a failing
 * photo is rejected IN the flow with a specific reason, never uploaded.
 * Inputs are measured by [PhotoQualityChecker] (ML Kit face detection +
 * a bitmap luminance sample); the decision itself is pure.
 *
 * RW-26 F7 harmonization: the check order is ONE order on both
 * platforms — face-count first (none -> FACE_UNCLEAR, more than one ->
 * MULTIPLE_PEOPLE), then darkness, then too-close. Android previously
 * judged darkness before face count; iOS judged count before darkness;
 * both now use this order. PhotoIntakePolicy defines routing only (no
 * check order), so the harmonized order lives here.
 */
object PhotoQualityPolicy {

    /** Mean luminance (0.0–1.0) below which a photo is "too dark". */
    const val MIN_MEAN_LUMINANCE: Double = 0.16

    /** A face wider than this fraction of the frame is "cropped too close". */
    const val MAX_FACE_WIDTH_FRACTION: Float = 0.80f

    fun decide(
        faceCount: Int,
        largestFaceWidthFraction: Float,
        meanLuminance: Double,
        subjectHeadRollDegrees: Float = 0f
    ): PhotoRejection? {
        if (faceCount == 0) return PhotoRejection.FACE_UNCLEAR
        if (faceCount > 1) return PhotoRejection.MULTIPLE_PEOPLE
        // WS-6 (F-1): the roll gate sits right after the face-count
        // checks — orientation is a framing defect of the one subject
        // face, judged before exposure/framing-size. A photo reaching
        // here with roll beyond the gate was not EXIF-correctable and
        // not a clean quarter-turn (the checker corrects those), so
        // it is rejected for retake, never waved through sideways.
        if (!IntakeOrientation.rollWithinGate(subjectHeadRollDegrees)) {
            return PhotoRejection.ROTATED
        }
        if (meanLuminance < MIN_MEAN_LUMINANCE) return PhotoRejection.TOO_DARK
        if (largestFaceWidthFraction > MAX_FACE_WIDTH_FRACTION) return PhotoRejection.TOO_CLOSE
        return null
    }

    /** The spec §7 C3 string for a rejection. */
    fun stringFor(rejection: PhotoRejection): String = when (rejection) {
        PhotoRejection.FACE_UNCLEAR -> MakeItMeStrings.C3A_REJECT_FACE_UNCLEAR
        PhotoRejection.MULTIPLE_PEOPLE -> MakeItMeStrings.C3B_REJECT_MULTIPLE
        PhotoRejection.TOO_DARK -> MakeItMeStrings.C3C_REJECT_TOO_DARK
        PhotoRejection.TOO_CLOSE -> MakeItMeStrings.C3D_REJECT_TOO_CLOSE
        PhotoRejection.ROTATED -> MakeItMeStrings.C3G_REJECT_ROTATED
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

/**
 * RW-16.15(iv): the session-replacement guard.
 *
 * The anonymous Supabase identity IS the user's account: photos,
 * profile, entitlement, and the free-generation counter all key off
 * it, and nothing anywhere can re-merge a replacement into it. The
 * pre-guard session flow treated ANY refresh failure as a dead
 * session: it cleared the stored session and minted a fresh
 * anonymous uid, silently orphaning everything under the old one.
 * That fold was unsafe because the refresh helper could not tell a
 * definitive server rejection (refresh token unknown/revoked — the
 * identity really is gone) from a transient transport failure (the
 * refresh may even have SUCCEEDED server-side: Supabase refresh
 * tokens rotate on use, so a response lost in transit leaves the
 * client's stored token dead while the account is perfectly alive).
 *
 * The rule this policy owns:
 *  - no stored session → mint (fresh install, post-erasure);
 *  - stored session with a live token → reuse;
 *  - refresh succeeds → store and use the refreshed session;
 *  - refresh DEFINITIVELY REJECTED by the server (the token
 *    endpoint refused the token itself: HTTP 400/401/403) → the
 *    identity is gone; clear and mint is the legitimate recovery;
 *  - refresh outcome UNKNOWN (transport error, 5xx, unparseable —
 *    and 408/409/425/429: a rate-limited or otherwise transiently
 *    refused refresh says nothing about the token's validity, and
 *    GoTrue rate-limits this endpoint) → KEEP the stored session
 *    untouched and let this call fail; the next call retries the
 *    refresh. A stored uid is never replaced on an unproven failure.
 */
object SessionGuardPolicy {

    /** What the refresh attempt established — nothing in between. */
    enum class RefreshOutcome { SUCCEEDED, REJECTED, UNKNOWN }

    enum class Action {
        MINT_FRESH,
        REUSE_STORED,
        USE_REFRESHED,
        CLEAR_AND_MINT,
        KEEP_STORED_AND_FAIL
    }

    /**
     * The HTTP-status half of the refresh classification (the
     * success half also needs the body: parseable → SUCCEEDED,
     * unparseable → UNKNOWN; the service decides that part).
     * REJECTED is restricted to 400/401/403 — the statuses with
     * which the token endpoint definitively refuses a refresh
     * token. Every other non-success status is UNKNOWN: 408, 409,
     * 425, and 429 (rate limiting) are transient server states, and
     * a 5xx is a server failure — none of them proves the stored
     * token invalid, so none of them may cost the user their uid.
     */
    fun classifyHttpFailure(statusCode: Int): RefreshOutcome = when (statusCode) {
        400, 401, 403 -> RefreshOutcome.REJECTED
        else -> RefreshOutcome.UNKNOWN
    }

    fun decide(
        hasStoredSession: Boolean,
        storedTokenValid: Boolean,
        refreshOutcome: RefreshOutcome?
    ): Action = when {
        !hasStoredSession -> Action.MINT_FRESH
        storedTokenValid -> Action.REUSE_STORED
        else -> when (refreshOutcome) {
            RefreshOutcome.SUCCEEDED -> Action.USE_REFRESHED
            RefreshOutcome.REJECTED -> Action.CLEAR_AND_MINT
            RefreshOutcome.UNKNOWN, null -> Action.KEEP_STORED_AND_FAIL
        }
    }
}
