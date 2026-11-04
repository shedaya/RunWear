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
