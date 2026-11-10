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
 * The guarantee this policy carries, enforced as a LAYOUT
 * BOUNDARY: while the dock is present, the scroll area's layout
 * ends at the dock band's top edge (HeroMainScreen applies
 * [dockBandDp] + the navigation inset as the scroll container's
 * bottom padding). List content therefore can never rest — or be
 * scrolled — underneath the dock, in any layout, at any scroll
 * position; at scroll end the last element sits at the boundary,
 * with the footer's own 32dp bottom padding as its clearance.
 * The band is sized from the dock's own maximum height (its
 * wrap-and-grow narration form) and its ruled bottom lift —
 * never from the content that happens to be last.
 * The RW-135 dock↔footer handoff is untouched: the footer remains
 * the lists' last item and the fade still derives from the lists'
 * own layoutInfo — whose viewport is now the bounded one — so the
 * dissolve runs exactly as specified while the footer rises
 * through the clear zone the boundary creates.
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

/**
 * RW-152 §2 (Nov 10, 2026): the expanded Wear I've Run panel's
 * sizing rule — "fill it or shrink it".
 *
 * The panel's height IS its content's height (≈240–300dp at
 * natural narration wrap): there is no minimum beyond the mini
 * dock it grows from and no empty ground inside it. The one cap:
 * the panel may never cover the hero's top chrome zone (wordmark
 * + place pill + status area), so its height is at most the
 * viewport height minus that zone. When the cap binds (large
 * accessibility text, an unusually long wrapped narration), the
 * panel's entry region scrolls internally — the panel itself
 * never grows past the cap.
 *
 * Pure, so the gate assertion — rendered panel height equals
 * measured content height (within animation tolerance) at every
 * viewport class — is pinned at this level.
 */
object WirOverlayPanelPolicy {

    /** The top chrome zone the panel may never cover: status
     * area + wordmark + place pill, with air. */
    const val TOP_CHROME_DP: Float = 120f

    /** The most height the panel may take in a viewport. */
    fun maxPanelHeightDp(viewportDp: Float): Float =
        (viewportDp - TOP_CHROME_DP).coerceAtLeast(0f)

    /** The panel's rendered height for a measured content height:
     * the content height itself, capped. */
    fun panelHeightDp(contentDp: Float, viewportDp: Float): Float =
        minOf(contentDp, maxPanelHeightDp(viewportDp))

    /** Whether the cap binds for this content (the internal
     * entry-region scroll's engagement rule). */
    fun capBinds(contentDp: Float, viewportDp: Float): Boolean =
        contentDp > maxPanelHeightDp(viewportDp)
}
