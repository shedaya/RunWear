package com.runwear.app.ui.screens

/**
 * RW-147 (Nov 9, 2026), as corrected by RW-170 (Nov 12, 2026):
 * the Wear I've Run dock's residence and the scroll hosts' end
 * padding are ONE system.
 *
 * The dock is a bottom-anchored overlay: it floats over
 * whatever the scroll position puts beneath it. The guarantee
 * this policy carries, enforced as a LAYOUT BOUNDARY: while
 * the dock is present, the scroll area's layout ends at the
 * dock band's top edge (HeroMainScreen applies [dockBandDp] +
 * the navigation inset as the scroll container's bottom
 * padding). List content therefore can never rest — or be
 * scrolled — underneath the dock, in any layout, at any
 * scroll position (RW-147's never-under-the-dock guarantee).
 *
 * RW-170 Move 2 — THE BAND PRICES THE PRESENTED FORM. The
 * pre-RW-170 band priced DOCK_MAX_HEIGHT_DP (88dp): the
 * tallest states' content (the wrap-and-grow narration form,
 * CUE headroom) plus the 14dp lift = a fixed 102dp reserved
 * for a MINI card that paints 49dp — 39dp of empty strip
 * above the card at every scroll position, the blank moat of
 * Sol's Nov 12 device pass. That "price the tallest state"
 * doctrine was the defect. The band is now priced from the
 * PRESENTED (mini) form's measured height, reported by the
 * morph surface itself (WearIveRunMorphSurface's
 * onMiniHeightChanged, measured at p = 0 geometry):
 * band = DOCK_BOTTOM_DP (14) + presentedMiniHeightDp. At the
 * one-line rest that is 14 + 49 = 63dp; a wrapped two-line
 * narration grows the presented height and the band follows
 * by exactly the wrapped line. EXPANSION NEVER CHANGES THE
 * BAND: the expanded form grows over the sheet from the
 * dock's residence (RW-152 §1) and its height is never
 * reserved. Before the surface's first measure, the host
 * uses [DEFAULT_PRESENTED_MINI_HEIGHT_DP] — the one-line
 * policy estimate (11 + 26 + 12 = 49dp) — so first
 * composition is already correct for the one-line form.
 *
 * Every consumer — the residence strip, the scroll-area
 * truncation, the band-edge fade strip, the snackbar seat —
 * consumes this same presented quantity.
 */
object DockClearancePolicy {

    /** Clear air kept between the last readable element at
     * scroll end and the dock's top edge. */
    const val CLEARANCE_DP: Float = 16f

    /** The presented (mini) form's height before its first
     * measure: the one-line policy estimate — MINI top pad
     * 11dp + the 26dp content row + MINI bottom pad 12dp =
     * 49dp (the prototype-measured one-line footprint). The
     * morph surface's measured report replaces it on first
     * layout; a wrapped narration reports its grown height. */
    const val DEFAULT_PRESENTED_MINI_HEIGHT_DP: Float = 49f

    /** The dock band's height above the bottom safe edge: its
     * ruled 14dp lift (WearIveRunDockPolicy.DOCK_BOTTOM_DP)
     * plus the PRESENTED mini height. The navigation-bar
     * inset adds on top of this at the call site (the dock
     * itself insets by it via navigationBarsPadding). */
    fun dockBandDp(
        presentedMiniHeightDp: Float = DEFAULT_PRESENTED_MINI_HEIGHT_DP
    ): Float =
        com.runwear.app.weariverun.WearIveRunDockPolicy.DOCK_BOTTOM_DP +
            presentedMiniHeightDp

    /**
     * Trailing reserve (dp) a scroll host must carry while
     * the dock is present: the full dock band +
     * [CLEARANCE_DP] + the navigation-bar inset the dock
     * also clears.
     */
    fun scrollEndReserveDp(
        navigationBarDp: Float,
        presentedMiniHeightDp: Float = DEFAULT_PRESENTED_MINI_HEIGHT_DP
    ): Float =
        dockBandDp(presentedMiniHeightDp) + CLEARANCE_DP + navigationBarDp

    /**
     * The at-rest rule, stated for proofs: a readable element
     * whose bottom edge sits at [elementBottomDp] (dp from
     * the viewport's top) in a viewport of [viewportDp] is
     * clear of the dock at rest iff it ends at or above the
     * dock band's top edge.
     */
    fun isClearAtRest(
        elementBottomDp: Float,
        viewportDp: Float,
        navigationBarDp: Float,
        presentedMiniHeightDp: Float = DEFAULT_PRESENTED_MINI_HEIGHT_DP
    ): Boolean =
        elementBottomDp <= viewportDp - navigationBarDp -
            dockBandDp(presentedMiniHeightDp)
}

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
