package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-135 REBUILD (Phase 2, Nov 12, 2026): pure-policy pins —
 * RB-03 the dissolve morph's narration windows and the
 * legibility invariant, RB-04 the padding/control tokens and
 * rest heights, RB-05 the constant radius, and R-1 the
 * footer-presentation trigger decision function.
 */
class Rw135RebuildPolicyTest {

    // ---- RB-05: radius never animates -------------------------

    @Test
    fun `radius is a constant 16dp at every progress`() {
        for (p in listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)) {
            assertEquals(
                "p=$p", 16f, WearIveRunMorphPolicy.radiusDp(p), 0.001f
            )
        }
    }

    // ---- RB-03: narration dissolve windows --------------------

    @Test
    fun `narration windows - endpoints and dead zone`() {
        assertEquals(1f, WearIveRunMorphPolicy.miniNarrationAlpha(0f), 0.001f)
        assertEquals(
            0f, WearIveRunMorphPolicy.miniNarrationAlpha(0.35f), 0.001f
        )
        assertEquals(
            0f, WearIveRunMorphPolicy.miniNarrationAlpha(1f), 0.001f
        )
        assertEquals(
            0f, WearIveRunMorphPolicy.expandedNarrationAlpha(0f), 0.001f
        )
        assertEquals(
            0f, WearIveRunMorphPolicy.expandedNarrationAlpha(0.55f), 0.001f
        )
        assertEquals(
            1f, WearIveRunMorphPolicy.expandedNarrationAlpha(1f), 0.001f
        )
        // The dead zone: at the midpoint NEITHER placement is
        // visible at all.
        assertEquals(
            0f, WearIveRunMorphPolicy.miniNarrationAlpha(0.45f), 0.001f
        )
        assertEquals(
            0f, WearIveRunMorphPolicy.expandedNarrationAlpha(0.45f), 0.001f
        )
    }

    @Test
    fun `legibility invariant - never both narrations legible at sampled p`() {
        var p = 0f
        while (p <= 1.0001f) {
            assertFalse(
                "both legible at p=$p (mini=" +
                    "${WearIveRunMorphPolicy.miniNarrationAlpha(p)}, exp=" +
                    "${WearIveRunMorphPolicy.expandedNarrationAlpha(p)})",
                WearIveRunMorphPolicy.bothNarrationsLegible(p)
            )
            assertTrue(
                "at most one legible narration at p=$p",
                WearIveRunMorphPolicy.legibleNarrationCount(p) <= 1
            )
            p += 0.1f
        }
        // Exactly one legible narration at both rest states.
        assertEquals(1, WearIveRunMorphPolicy.legibleNarrationCount(0f))
        assertEquals(1, WearIveRunMorphPolicy.legibleNarrationCount(1f))
        // The wordmark still lerps in place 17 -> 31.
        assertEquals(17f, WearIveRunMorphPolicy.wordmarkSp(0f), 0.001f)
        assertEquals(31f, WearIveRunMorphPolicy.wordmarkSp(1f), 0.001f)
    }

    @Test
    fun `expanded content still dissolves over the back window`() {
        assertEquals(0f, WearIveRunMorphPolicy.expandedAlpha(0.45f), 0.001f)
        assertEquals(1f, WearIveRunMorphPolicy.expandedAlpha(1f), 0.001f)
    }

    // ---- RB-04: the room — padding tokens and rest heights ----

    @Test
    fun `padding tokens - mini 11 over 12, expanded 20 over 12`() {
        assertEquals(11f, WearIveRunMorphPolicy.topPaddingDp(0f), 0.001f)
        assertEquals(12f, WearIveRunMorphPolicy.bottomPaddingDp(0f), 0.001f)
        assertEquals(20f, WearIveRunMorphPolicy.topPaddingDp(1f), 0.001f)
        // The retired A-prime in-flow seat's 32dp bottom pad
        // must NOT survive into the overlay: expanded bottom
        // is 12dp — the panel ends where the doorway ends.
        assertEquals(12f, WearIveRunMorphPolicy.bottomPaddingDp(1f), 0.001f)
        assertEquals(12f, WearIveRunMorphPolicy.sidePaddingDp(0f), 0.001f)
        assertEquals(16f, WearIveRunMorphPolicy.sidePaddingDp(1f), 0.001f)
    }

    @Test
    fun `mini rest height is the prototype-measured 49dp for a 26dp row`() {
        val h = WearIveRunMorphPolicy.miniRestHeightDp()
        assertEquals(49f, h, 0.001f)
        assertTrue("mini rest in the 47..52dp acceptance range", h in 47f..52f)
        assertEquals(26f, WearIveRunMorphPolicy.CONTROL_DP, 0.001f)
    }

    // ---- R-1: the trigger decision function -------------------

    private val t = WearIveRunExpansionTrigger
    private fun st() = WearIveRunExpansionTrigger.State()

    @Test
    fun `r1 - arriving at the footer presents the expanded form`() {
        assertTrue(t.expanded(st(), atFooter = true))
    }

    @Test
    fun `r1 - leaving the footer re-docks to mini`() {
        assertFalse(t.expanded(st(), atFooter = false))
    }

    @Test
    fun `r1 - a tap summon survives scrolling away from the footer`() {
        val s = t.summon(st())
        assertTrue(t.expanded(s, atFooter = false))
        assertTrue(t.expanded(s, atFooter = true))
        // Scroll bookkeeping never touches a summon.
        assertEquals(s, t.onScroll(s, inFooterZone = false))
        assertTrue(
            t.expanded(t.onScroll(s, inFooterZone = false), atFooter = false)
        )
    }

    @Test
    fun `r1 - chevron collapse at the footer suppresses until the zone is left`() {
        var s = t.collapse(st(), atFooter = true)
        // Still at the footer: suppressed, stays MINI.
        assertFalse(t.expanded(s, atFooter = true))
        // Scrolling within the zone does not clear the latch.
        s = t.onScroll(s, inFooterZone = true)
        assertFalse(t.expanded(s, atFooter = true))
        // Leaving the zone clears the latch…
        s = t.onScroll(s, inFooterZone = false)
        assertFalse(s.footerSuppressed)
        // …so arriving at the footer again presents EXPANDED.
        assertTrue(t.expanded(s, atFooter = true))
    }

    @Test
    fun `r1 - collapse of a summoned expansion at the footer also latches`() {
        val summoned = t.summon(st())
        val s = t.collapse(summoned, atFooter = true)
        assertFalse(s.userSummoned)
        assertFalse(t.expanded(s, atFooter = true))
    }

    @Test
    fun `r1 - collapse away from the footer does not latch`() {
        val s = t.collapse(t.summon(st()), atFooter = false)
        assertFalse(s.footerSuppressed)
        assertTrue(t.expanded(s, atFooter = true))
    }

    @Test
    fun `r1 - scroll geometry guards the unlaid-out container`() {
        // No items yet (or no visible items): never the footer.
        assertFalse(t.inFooterZone(totalItems = 0, lastVisibleIndex = null))
        assertFalse(t.inFooterZone(totalItems = 0, lastVisibleIndex = 0))
        assertFalse(t.atFooter(0, null, canScrollForward = false))
        // Last item visible + max scroll: at footer.
        assertTrue(t.inFooterZone(totalItems = 7, lastVisibleIndex = 6))
        assertTrue(t.atFooter(7, 6, canScrollForward = false))
        // Last item visible but more scroll remains: in the
        // zone, not yet at the footer.
        assertFalse(t.atFooter(7, 6, canScrollForward = true))
        // Last item not yet visible: outside the zone.
        assertFalse(t.inFooterZone(totalItems = 7, lastVisibleIndex = 5))
    }
}
