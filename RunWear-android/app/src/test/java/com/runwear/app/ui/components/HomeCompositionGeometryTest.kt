package com.runwear.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-1 (RC-1) — the home composition's pure geometry, pinned:
 *
 *  - the −28dp overlap invariant (D-01): the sheet's top sits at
 *    hero − 28dp and the stacked unit reserves exactly
 *    hero + sheet − 28dp, at every sheet height;
 *  - the void is zero (D-04) for short AND long outfits' sheet
 *    heights — the reservation never contains unoccupied space;
 *  - the chrome fade (Part 1 §1.1): opacity 1 → 0 over the first
 *    40% of handoff travel, non-interactive at the end of the fade;
 *  - the parallax anchor drifts at 0.5× from exactly top at rest;
 *  - the head-crop geometry (D-23 home half): computed from the
 *    served image's aspect at the served width, the head band is
 *    inside the served slice and below the reserved top zone
 *    across the phone / unfolded-Fold / split-pane matrix;
 *  - the motion tokens the spec names (600 in / 250 out / 120
 *    settle).
 */
class HomeCompositionGeometryTest {

    @Test
    fun `the sheet overlap is the designed minus 28dp constant`() {
        assertEquals(28.dp, HeroSheetOverlap)
        assertEquals(28.dp, HeroStackedGeometry.OVERLAP)
    }

    @Test
    fun `sheet top and unit height hold the overlap at every sheet height`() {
        val hero = 633.dp
        // Short outfit's sheet, the seeded estimate, and a long
        // sheet — the invariant must not depend on content height.
        for (sheet in listOf(180.dp, 248.dp, 330.dp, 420.dp, 512.dp)) {
            assertEquals(hero - 28.dp, HeroStackedGeometry.sheetTop(hero))
            assertEquals(
                hero + sheet - 28.dp,
                HeroStackedGeometry.unitHeight(hero, sheet)
            )
        }
    }

    @Test
    fun `the reserved unit contains zero void at every content height`() {
        for (hero in listOf(517.dp, 622.dp, 633.dp, 720.dp)) {
            for (sheet in listOf(180.dp, 248.dp, 330.dp, 420.dp)) {
                assertEquals(
                    "void must be 0 for hero=$hero sheet=$sheet",
                    0.dp,
                    HeroStackedGeometry.voidHeight(hero, sheet)
                )
            }
        }
    }

    @Test
    fun `chrome is fully present at rest and gone at 40 percent of travel`() {
        assertEquals(1f, HeroScrollHandoff.chromeAlpha(0f), 0.001f)
        assertEquals(0.5f, HeroScrollHandoff.chromeAlpha(0.2f), 0.001f)
        assertEquals(0f, HeroScrollHandoff.chromeAlpha(0.4f), 0.001f)
        assertEquals(0f, HeroScrollHandoff.chromeAlpha(1f), 0.001f)
    }

    @Test
    fun `chrome is interactive at rest and non-interactive at the end of the fade`() {
        assertTrue(HeroScrollHandoff.chromeInteractive(0f))
        assertTrue(HeroScrollHandoff.chromeInteractive(0.39f))
        assertFalse(HeroScrollHandoff.chromeInteractive(0.4f))
        assertFalse(HeroScrollHandoff.chromeInteractive(1f))
    }

    @Test
    fun `parallax anchor starts exactly at top and drifts at half rate`() {
        assertEquals(-1f, HeroScrollHandoff.parallaxAnchorBias(0f), 0.001f)
        assertEquals(-0.75f, HeroScrollHandoff.parallaxAnchorBias(0.5f), 0.001f)
        assertEquals(-0.5f, HeroScrollHandoff.parallaxAnchorBias(1f), 0.001f)
        assertEquals(0.5f, HeroScrollHandoff.PARALLAX_FACTOR, 0.001f)
    }

    @Test
    fun `handoff transform endpoints are the spec values`() {
        // Scale 1.00 → 0.94, corner radius 0 → 24 across the range.
        assertEquals(1f, HeroScrollHandoff.scale(0f), 0.001f)
        assertEquals(0.94f, HeroScrollHandoff.scale(1f), 0.001f)
        assertEquals(24.dp, HeroScrollHandoff.cornerRadius(1f))
        assertEquals(0.dp, HeroScrollHandoff.cornerRadius(0f))
    }

    @Test
    fun `head band is inside the served slice across the width matrix`() {
        // (display width, hero-zone height) in dp — phone portrait,
        // unfolded Fold, medium tablet portrait, EXPANDED split pane.
        // Ratios are all that matter, so dp serve as the units.
        val matrix = listOf(
            390f to 633f, // phone: 75% of 844
            690f to 622f, // Fold unfolded: 75% of 829
            600f to 720f, // tablet portrait: 75% of 960
            348f to 576f // EXPANDED hero pane (42% of 829 wide, 75% of 768)
        )
        for ((w, h) in matrix) {
            assertEquals(
                "slice must start at source y=0 at rest (w=$w)",
                0f,
                HeroImageGeometry.sliceTopSourcePx(w, h),
                0.001f
            )
            assertTrue(
                "head band must be visible at ${w}x$h",
                HeroImageGeometry.headBandVisible(w, h)
            )
        }
    }

    @Test
    fun `reserved top zone stays above the head at every served width`() {
        // Reserved zone = status-bar inset (~24dp) + the controls
        // row's extent (~76dp incl. its 16dp padding) ≈ 100dp.
        val reserved = 100f
        val matrix = listOf(390f to 633f, 690f to 622f, 600f to 720f, 348f to 576f)
        for ((w, h) in matrix) {
            assertTrue(
                "head top must clear the reserved zone at ${w}x$h " +
                    "(head at ${HeroImageGeometry.headTopDisplayPx(w, h)})",
                HeroImageGeometry.reservedTopZoneFits(reserved, w, h)
            )
        }
    }

    @Test
    fun `motion tokens are the spec values`() {
        assertEquals(600, HeroMotionTokens.HERO_CROSSFADE_IN_MS)
        assertEquals(250, HeroMotionTokens.HERO_CROSSFADE_OUT_MS)
        assertEquals(120, HeroMotionTokens.DAY_STRIP_SETTLE_MS)
    }
}
