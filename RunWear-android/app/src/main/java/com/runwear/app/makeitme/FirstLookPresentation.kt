package com.runwear.app.makeitme

/**
 * GEN-3 A-HERO (WS-B, X-1/X-2): the first-look moment's presentation
 * seam — the pure resolution of WHAT the frame-34 moment may claim,
 * from the server's folded look state for the exact cell and whether
 * the generated photo is the one actually on screen. Mirrors the
 * iOS gen-2 W-16 seam's contract, implemented natively.
 *
 * The gen-2 defect (census X-1): the moment rendered its finished
 * state — "You're in the photo now" + the look counter — off the
 * upload flow's completion alone, over whatever photo happened to
 * be serving (stock, or nothing), while the ~3.5 s in between was a
 * bare greyed-button spinner with no label. The rules now:
 *
 *  - READY is claimed ONLY when the look status is Ready AND the
 *    personal photo is the photo actually displayed. The finished
 *    headline, the look counter, the Premium band and the success
 *    haptic all hang off this one state.
 *  - FAILED is the server's terminal failure for the cell: honest
 *    copy, and no look was used (a failed look never charged the
 *    free counter).
 *  - Everything else — no status yet, Making, or Ready reported
 *    before the photo has landed on screen — is MAKING: the
 *    generating state ON the photo (the photo currently serving,
 *    the bundled fallback under WS-A(1)), with honest copy and no
 *    finished-state claims of any kind.
 */
enum class FirstLookState { MAKING, READY, FAILED }

object FirstLookPresentation {

    fun resolve(
        lookStatus: PersonalLookStatus,
        personalPhotoDisplayed: Boolean
    ): FirstLookState = when {
        lookStatus is PersonalLookStatus.Failed -> FirstLookState.FAILED
        lookStatus == PersonalLookStatus.Ready && personalPhotoDisplayed ->
            FirstLookState.READY
        else -> FirstLookState.MAKING
    }

    /** The finished state's claims (headline + counter + band) show
     * only in READY — never while making, never on failure. */
    fun showsFinishedClaims(state: FirstLookState): Boolean =
        state == FirstLookState.READY

    /** The Premium band / second door: withheld while making (the
     * ask waits for the value to exist) and on failure. */
    fun showsPremiumBand(state: FirstLookState): Boolean =
        state == FirstLookState.READY
}

/**
 * RW-177 (2026-11-25): WHICH photo the first-look moment paints.
 *
 * The moment's copy promises the generating state sits on the
 * cell's standard photo ("the photo currently serving"). As built,
 * the moment inherited the HOME SCENE's resolution
 * (MainUiState.effectiveHeroUrl) — and in the 2026-11-25 incident
 * that resolution was empty when the moment was raised from the
 * onboarding upload flow (no scene had committed conditions yet,
 * and refreshHeroAfterPersonalChange no-ops without them), so the
 * first-look screen stood blank for the whole making phase.
 *
 * The rule now: the moment resolves the CELL'S OWN approved
 * generic image (the shared hero cascade for the first-look cell)
 * while the personal photo is pending, and paints by this order —
 * the personal photo when it is the one displayed; else the
 * cell's generic; else the scene's resolution as the last resort
 * (it may be the bundled floor). A photo therefore stands under
 * the making state whenever ANY approved photo for the cell
 * exists, which is the promise the surface makes.
 */
object FirstLookPhotoPolicy {

    /** The photo the moment paints, by the order above. Null only
     * when no photo exists at any tier. */
    fun photoToShow(
        personalPhotoUrl: String?,
        genericPhotoUrl: String?,
        scenePhotoUrl: String?
    ): String? = personalPhotoUrl ?: genericPhotoUrl ?: scenePhotoUrl

    /** A generic resolution for the moment is owed when the
     * personal photo is not the displayed one and no generic has
     * been resolved for THIS cell yet. */
    fun shouldResolveGeneric(
        personalPhotoDisplayed: Boolean,
        genericPhotoCell: String?,
        currentCell: String
    ): Boolean = !personalPhotoDisplayed && genericPhotoCell != currentCell
}

/**
 * GEN-6 WAVE A (DA-03) — the reveal choreography's gating, ported
 * from the iOS gen-5 FirstLookView: the success sequence runs only
 * when the presentation is READY *and* the personal photo has
 * actually LOADED (displayed AND loaded) — then the 0.4 s
 * crossfade, ONE haptic at 0.4 s, and the Premium band slides up
 * at 0.52 s. Gen-5 Android keyed readiness to URL equality alone
 * (displayed), so the haptic and band could fire over a photo that
 * had not painted yet. The timing constants themselves live in
 * OnboardingMotion (FIRST_LOOK_HAPTIC_AT_MS = 400 =
 * FIRST_LOOK_PHOTO_CROSSFADE_MS; band at 400 + 120 = 520) — this
 * seam owns only the gates.
 */
object FirstLookRevealPolicy {

    /** The photo may crossfade in once it has loaded (or when
     * there is no photo to wait for — the skeleton state). */
    fun shouldReveal(
        state: FirstLookState,
        photoLoaded: Boolean,
        hasPhoto: Boolean
    ): Boolean = !hasPhoto || photoLoaded

    /** The success sequence (haptic + band) requires READY and a
     * loaded photo — both, never either alone. */
    fun shouldRunSuccessSequence(
        state: FirstLookState,
        photoLoaded: Boolean
    ): Boolean = state == FirstLookState.READY && photoLoaded

    /** "See My Outfit" is rendered unconditionally in every phase —
     * it is never a gate on the reveal or on anything else. */
    fun seeOutfitAlwaysAvailable(state: FirstLookState): Boolean = true
}

/**
 * GEN-6 WAVE A (DA-03) — the fork host's composition gates, as a
 * pure seam. THE DEFECT this pins: gen-5 hosted the fork's upload
 * flow under `showForkUpload && forkDue`, and `forkDue` contains
 * `!makeItMeState.forkDecided` — but the upload flow itself flips
 * `forkDecided` to true the moment the user SUBMITS their photos
 * (PhotoSubmissionDecided, so a relaunch never re-forks a decided
 * user). The host therefore unmounted the upload flow mid-submit:
 * its SAVED beat (the 2.4 s dwell that ends in `onFinished`) was
 * cancelled with the composition, `onFinished` never fired, and
 * the first-look moment — raised ONLY from that callback on this
 * path — never presented. The user landed on Home over a stock
 * photo with no pill, exactly as the owner's recording shows.
 *
 * The rule now: once the user has opened the upload flow from the
 * fork, the flow stays hosted until IT finishes — submission
 * bookkeeping (`forkDecided`) gates whether the FORK is offered,
 * never whether an in-flight flow survives.
 */
object ForkHostPolicy {

    /** The fork screen itself: offered only while undecided and
     * not already inside the upload flow. */
    fun showForkScreen(forkDue: Boolean, uploadOpen: Boolean): Boolean =
        forkDue && !uploadOpen

    /** The upload flow: hosted whenever it is open (and the feature
     * is enabled) — submission state must never unmount it. */
    fun showUploadFlow(uploadOpen: Boolean, featureEnabled: Boolean): Boolean =
        uploadOpen && featureEnabled

    /** The first-look moment: raised by the flow's finish callback
     * (or the Settings path's explicit request) and hosted until
     * the user takes a door. Independent of fork bookkeeping. */
    fun showFirstLookMoment(raised: Boolean, featureEnabled: Boolean): Boolean =
        raised && featureEnabled
}
