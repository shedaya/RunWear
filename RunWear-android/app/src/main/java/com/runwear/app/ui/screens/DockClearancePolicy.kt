package com.runwear.app.ui.screens

/**
 * RW-147 (Nov 9, 2026): the Wear I've Run dock's residence and the
 * scroll hosts' end padding are ONE system.
 *
 * The dock is a bottom-anchored overlay: it floats over whatever
 * the scroll position puts beneath it. RW-144's acceptance
 * measured only the first screen's SHEET elements on an 844dp
 * fixture with a 2-item outfit; on Sol's Fold8 the same dock
 * covered the 3rd outfit row (folded portrait) and, in the
 * expanded two-pane layout — where the overlay spans the full
 * window width, over BOTH panes — the Make It Me card's "Not now"
 * line and the hero pane's foot (owner device pass, Nov 9, 2026).
 *
 * The guarantee this policy carries: every scroll host reserves
 * [scrollEndReserveDp] of trailing space, so at scroll end the
 * last readable element rests entirely above the dock's band, and
 * any element the dock overlays mid-scroll can be scrolled clear
 * of it. The reserve is sized from the dock's own maximum height
 * (its wrap-and-grow narration form), its ruled bottom lift and a
 * clear margin — never from the content that happens to be last.
 * The RW-135 dock↔footer handoff is untouched: the footer remains
 * the lists' last item and the handoff fade still hands the dock
 * off to it; the reserve simply guarantees the footer's own
 * content (and everything before it) can rest clear of the band.
 */
object DockClearancePolicy {

    /** Clear air kept between the last readable element at scroll
     * end and the dock's top edge. */
    const val CLEARANCE_DP: Float = 16f

    /**
     * The dock's maximum height: its wrap-and-grow form (RW-138 —
     * the narration wraps instead of truncating) at two lines,
     * plus its internal vertical padding and the wordmark row.
     * Conservative on purpose: the reserve must hold for the
     * dock's tallest state, not its common one.
     */
    const val DOCK_MAX_HEIGHT_DP: Float = 88f

    /** The dock band's height above the bottom safe edge: its
     * ruled 14dp lift (WearIveRunDockPolicy.DOCK_BOTTOM_DP) plus
     * its maximum height. The navigation-bar inset adds on top of
     * this at the call site (the dock itself insets by it via
     * navigationBarsPadding). */
    fun dockBandDp(): Float =
        com.runwear.app.weariverun.WearIveRunDockPolicy.DOCK_BOTTOM_DP + DOCK_MAX_HEIGHT_DP

    /**
     * Trailing reserve (dp) a scroll host must carry while the
     * dock is present: the full dock band + [CLEARANCE_DP] + the
     * navigation-bar inset the dock also clears.
     */
    fun scrollEndReserveDp(navigationBarDp: Float): Float =
        dockBandDp() + CLEARANCE_DP + navigationBarDp

    /**
     * The at-rest rule, stated for proofs: a readable element
     * whose bottom edge sits at [elementBottomDp] (dp from the
     * viewport's top) in a viewport of [viewportDp] is clear of
     * the dock at rest iff it ends at or above the dock's top
     * edge. Mid-scroll overlay is permitted ONLY when the element
     * can be scrolled clear — i.e. the host carries the reserve
     * above; at scroll end nothing readable is under the band.
     */
    fun isClearAtRest(
        elementBottomDp: Float,
        viewportDp: Float,
        navigationBarDp: Float
    ): Boolean =
        elementBottomDp <= viewportDp - navigationBarDp - dockBandDp()
}
