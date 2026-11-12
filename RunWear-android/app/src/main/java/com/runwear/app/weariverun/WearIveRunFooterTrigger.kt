package com.runwear.app.weariverun

/**
 * R-1 (Sol's ruling, Nov 12, 2026 — the RW-135 rebuild's
 * reconciliation of A′ and RW-152): reaching the page end
 * presents the EXPANDED form; scrolling away from the page
 * end re-docks it to MINI — alongside RW-152's user-summoned
 * expansion, which persists until an explicit collapse.
 *
 * The presented state is a pure function:
 *
 *     expanded = userSummoned || (atFooter && !footerSuppressed)
 *
 * - [State.userSummoned]: set by a tap on the bar body or its
 *   expand control (the same action, spec §4.4). A summoned
 *   expansion survives scrolling away from the footer — it
 *   ends only on an explicit collapse (chevron / glide-up /
 *   Back).
 * - [State.footerSuppressed]: set when the user collapses
 *   WHILE at the footer (otherwise the surface would
 *   re-present in the same frame). The latch clears the
 *   moment the scroll position leaves the footer zone, so
 *   returning to the page end presents the expanded form
 *   again.
 *
 * Scroll geometry (from the host's ACTIVE scroll container —
 * the LazyColumn in the compact layout, the LazyVerticalGrid
 * otherwise; layoutInfo of the container actually composed):
 * - the footer ZONE is entered when the last content item is
 *   among the visible items (the last content is within
 *   ~one viewport of the end — a visible last item is by
 *   construction less than a viewport from the viewport's
 *   bottom edge);
 * - AT FOOTER is the zone plus max-scroll reached (the
 *   container can no longer scroll forward).
 *
 * Everything here is pure and unit-tested (R-1 decision
 * tests); the host only feeds it layoutInfo facts and applies
 * the transitions.
 */
object WearIveRunExpansionTrigger {

    data class State(
        val userSummoned: Boolean = false,
        val footerSuppressed: Boolean = false
    )

    /** The presented form for [state] at a scroll position. */
    fun expanded(state: State, atFooter: Boolean): Boolean =
        state.userSummoned || (atFooter && !state.footerSuppressed)

    /** Tap-summon (bar body or expand control). */
    fun summon(state: State): State = state.copy(userSummoned = true)

    /**
     * An explicit collapse (chevron / glide-up / Back):
     * clears any summon; if the collapse happens at the
     * footer, the suppression latch is set so the footer
     * presentation does not immediately re-fire.
     */
    fun collapse(state: State, atFooter: Boolean): State =
        State(
            userSummoned = false,
            footerSuppressed = state.footerSuppressed || atFooter
        )

    /** A full reset (the surface exits — delete / session
     * end): neither a summon nor a latch survives it. */
    fun reset(state: State): State = State()

    /**
     * Scroll bookkeeping: leaving the footer zone clears the
     * suppression latch (and nothing else — a summon is never
     * touched by scroll).
     */
    fun onScroll(state: State, inFooterZone: Boolean): State =
        if (!inFooterZone && state.footerSuppressed) {
            state.copy(footerSuppressed = false)
        } else {
            state
        }

    // -- Scroll geometry from layoutInfo facts ----------------

    /** The last content item is visible: the scroll position
     * is inside the footer zone. Guards the not-yet-laid-out
     * container (no items) — an empty layoutInfo is never the
     * footer. */
    fun inFooterZone(totalItems: Int, lastVisibleIndex: Int?): Boolean =
        totalItems > 0 && lastVisibleIndex != null &&
            lastVisibleIndex == totalItems - 1

    /** At the page end: inside the zone and unable to scroll
     * any further forward. */
    fun atFooter(
        totalItems: Int,
        lastVisibleIndex: Int?,
        canScrollForward: Boolean
    ): Boolean =
        inFooterZone(totalItems, lastVisibleIndex) && !canScrollForward
}
