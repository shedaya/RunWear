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
 *
 * RW-170 (Nov 12, 2026): the RB-03 pins are rewritten to the
 * single-narration law — the narration is ONE composition
 * whose slot migrates (font 12sp -> 15sp); the two-placement
 * window functions (miniNarrationAlpha /
 * expandedNarrationAlpha / bothNarrationsLegible /
 * legibleNarrationCount) are deleted with the construction
 * they encoded. Move 1's alpha-only surface law is pinned
 * here too.
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

    // ---- RB-03 / RW-170 Move 4: ONE narration, migrating ----

    @Test
    fun `single narration law - exactly one narration node at every p`() {
        var p = 0f
        while (p <= 1.0001f) {
            assertEquals(
                "exactly one narration node at p=$p",
                1, WearIveRunMorphPolicy.narrationNodeCount(p)
            )
            p += 0.05f
        }
    }

    @Test
    fun `narration font lerps 12sp mini to 15sp expanded`() {
        assertEquals(12f, WearIveRunMorphPolicy.narrationSp(0f), 0.001f)
        assertEquals(13.5f, WearIveRunMorphPolicy.narrationSp(0.5f), 0.001f)
        assertEquals(15f, WearIveRunMorphPolicy.narrationSp(1f), 0.001f)
        // Monotonic across the travel.
        var prev = -1f
        var p = 0f
        while (p <= 1.0001f) {
            val v = WearIveRunMorphPolicy.narrationSp(p)
            assertTrue("narrationSp monotonic at p=$p", v >= prev)
            prev = v
            p += 0.1f
        }
        // The wordmark still lerps in place 17 -> 31.
        assertEquals(17f, WearIveRunMorphPolicy.wordmarkSp(0f), 0.001f)
        assertEquals(31f, WearIveRunMorphPolicy.wordmarkSp(1f), 0.001f)
    }

    // ---- RW-170 Move 1: ONE surface, alpha-only animation ----

    @Test
    fun `surface alpha - mini translucency to opaque, token constant`() {
        // Light: 0.85 -> 1.0. Dark: 0.82 -> 1.0.
        assertEquals(
            0.85f, WearIveRunMorphPolicy.surfaceAlpha(0f, darkTheme = false),
            0.001f
        )
        assertEquals(
            1f, WearIveRunMorphPolicy.surfaceAlpha(1f, darkTheme = false),
            0.001f
        )
        assertEquals(
            0.82f, WearIveRunMorphPolicy.surfaceAlpha(0f, darkTheme = true),
            0.001f
        )
        assertEquals(
            1f, WearIveRunMorphPolicy.surfaceAlpha(1f, darkTheme = true),
            0.001f
        )
        assertEquals(
            0.925f, WearIveRunMorphPolicy.surfaceAlpha(0.5f, darkTheme = false),
            0.001f
        )
    }

    // ---- RW-170 Move 3: shadow + hairline tokens -------------

    @Test
    fun `shadow of record - offset 6, blur 24, black 14, hairline ink 5`() {
        assertEquals(6f, WearIveRunMorphPolicy.SHADOW_OFFSET_Y_DP, 0.001f)
        assertEquals(24f, WearIveRunMorphPolicy.SHADOW_BLUR_DP, 0.001f)
        assertEquals(0.14f, WearIveRunMorphPolicy.SHADOW_ALPHA, 0.001f)
        assertEquals(0.05f, WearIveRunMorphPolicy.HAIRLINE_ALPHA, 0.001f)
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
