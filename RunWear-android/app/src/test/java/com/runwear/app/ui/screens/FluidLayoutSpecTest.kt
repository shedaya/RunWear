package com.runwear.app.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fluid-layout continuum: the layout decisions [HeroMainScreen]
 * makes for every window width, pinned against the PWA's
 * fully-responsive behavior (runwearpwa22 @ 4a26106) — no width cap
 * anywhere, the grid progression the PWA measured (1 col at 600,
 * 2 at 690+, rail at EXPANDED with a 1-column pane at 1024 growing
 * to 2 at 1280 and 3 at 1920), and COMPACT's gutter untouched.
 */
class FluidLayoutSpecTest {

    /** Content width available to the outfit grid at window [width]. */
    private fun gridContentWidth(width: Int): Float {
        val spec = fluidLayoutSpec(width.dp)
        val pane = spec.heroPaneWidth?.value ?: width.toFloat()
        return pane - 2 * spec.pagePadding.value
    }

    private fun columnsAt(width: Int): Int {
        val spec = fluidLayoutSpec(width.dp)
        return outfitGridColumns(gridContentWidth(width).dp, spec.gridGap)
    }

    @Test
    fun breakpointsMatchWindowSizeClasses() {
        assertEquals(AdaptiveLayout.COMPACT, fluidLayoutSpec(320.dp).layout)
        assertEquals(AdaptiveLayout.COMPACT, fluidLayoutSpec(599.dp).layout)
        assertEquals(AdaptiveLayout.MEDIUM, fluidLayoutSpec(600.dp).layout)
        assertEquals(AdaptiveLayout.MEDIUM, fluidLayoutSpec(839.dp).layout)
        assertEquals(AdaptiveLayout.EXPANDED, fluidLayoutSpec(840.dp).layout)
        assertEquals(AdaptiveLayout.EXPANDED, fluidLayoutSpec(1920.dp).layout)
    }

    @Test
    fun compactGutterIsExactlyThePreFluidValue() {
        // The clamp floor: every COMPACT width keeps the fixed 20dp
        // gutter the phone layout has always used (pixel preservation).
        var w = 320
        while (w < 600) {
            assertEquals("padding at ${w}dp", 20f, fluidLayoutSpec(w.dp).pagePadding.value, 0.001f)
            w += 7
        }
    }

    @Test
    fun gutterScalesContinuouslyAndCapsAt44() {
        var previous = 0f
        var w = 600
        while (w <= 1920) {
            val padding = fluidLayoutSpec(w.dp).pagePadding.value
            assertTrue("padding must not shrink as width grows (${w}dp)", padding >= previous)
            assertTrue("padding at ${w}dp within [20, 44]", padding in 20f..44f)
            previous = padding
            w += 20
        }
        assertEquals(44f, fluidLayoutSpec(1920.dp).pagePadding.value, 0.001f)
        // 3vw of the width, mid-range: 1000dp -> 30dp.
        assertEquals(30f, fluidLayoutSpec(1000.dp).pagePadding.value, 0.001f)
    }

    @Test
    fun heroPaneIsThePwaRailMinmax() {
        assertNull(fluidLayoutSpec(839.dp).heroPaneWidth)
        // min 400dp wins while 42% is below it (840dp -> 352.8dp).
        assertEquals(400f, fluidLayoutSpec(840.dp).heroPaneWidth!!.value, 0.001f)
        // 42% beyond that: 1024 -> 430.08, 1280 -> 537.6, 1920 -> 806.4.
        assertEquals(430.08f, fluidLayoutSpec(1024.dp).heroPaneWidth!!.value, 0.01f)
        assertEquals(537.6f, fluidLayoutSpec(1280.dp).heroPaneWidth!!.value, 0.01f)
        assertEquals(806.4f, fluidLayoutSpec(1920.dp).heroPaneWidth!!.value, 0.01f)
    }

    @Test
    fun gridProgressionMatchesThePwaAtItsMeasuredWidths() {
        // Stacked range (hero on top, grid below) — PWA: 1 col at 600x960,
        // 2 cols at 690x829 (Fold open) and 768x1024.
        assertEquals(1, columnsAt(600))
        assertEquals(2, columnsAt(690))
        assertEquals(2, columnsAt(768))
        assertEquals(2, columnsAt(829))
        // Rail range — PWA: 1-column pane at 1024, 2 at 1280, 3 at 1920.
        assertEquals(1, columnsAt(1024))
        assertEquals(2, columnsAt(1280))
        assertEquals(3, columnsAt(1920))
    }

    @Test
    fun columnsNeverShrinkWithinARegime() {
        // Stacked regime: 600..839.
        var previous = 0
        var w = 600
        while (w <= 839) {
            val columns = columnsAt(w)
            assertTrue("columns at ${w}dp ($columns) below previous ($previous)", columns >= previous)
            previous = columns
            w += 13
        }
        // Rail regime: 840..1920 (the pane widens monotonically).
        previous = 0
        w = 840
        while (w <= 1920) {
            val columns = columnsAt(w)
            assertTrue("columns at ${w}dp ($columns) below previous ($previous)", columns >= previous)
            previous = columns
            w += 17
        }
    }

    @Test
    fun foldableAndDuoWidthsLandInTheExpectedClasses() {
        // Galaxy Fold cover (~362dp) and Duo single screen (~514dp): COMPACT.
        assertEquals(AdaptiveLayout.COMPACT, fluidLayoutSpec(362.dp).layout)
        assertEquals(AdaptiveLayout.COMPACT, fluidLayoutSpec(514.dp).layout)
        // Fold unfolded portrait (690dp): MEDIUM with the 2-column grid.
        assertEquals(AdaptiveLayout.MEDIUM, fluidLayoutSpec(690.dp).layout)
        assertEquals(2, columnsAt(690))
        // Duo spanned (~1028dp): EXPANDED rail, 1-column pane — like the
        // PWA at 1024.
        assertEquals(AdaptiveLayout.EXPANDED, fluidLayoutSpec(1028.dp).layout)
        assertEquals(1, columnsAt(1028))
    }
}
