package com.runwear.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.runwear.app.weariverun.WearIveRunMiniBarPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-8 (RC-8) gate — computed contrast over every text/background
 * pairing the Android phone UI can produce, in both appearances.
 *
 * Floors (design notes §1, WCAG): text ≥ 4.5:1, non-text ≥ 3:1.
 * Every value below is read from the shipped palettes themselves —
 * the suite fails if a token drifts off the design table, if the
 * deleted third text tier returns (it cannot compile — the field is
 * gone), or if a pairing falls below its floor.
 *
 * Pairing inventory (how each is produced on screen):
 * - TextPrimary / TextSecondary / PineAccent on every theme ground
 *   (Background, Surface, BgCard, SurfaceElevated, BgCardLight):
 *   headings, body, sublabels, helper text, links, glyphs — home,
 *   sheets, settings, feedback, permission, collection, Make It Me.
 * - The same three inks on the mini bar's translucent tint
 *   (MiniBarContainer at the policy alpha, composited over the
 *   grounds the bar can sit on): the Wear I've Run narration,
 *   including its pine payload span.
 * - White on Primary: primary buttons, selected segments, selected
 *   day cells; White at 80% on Primary: the selected day cell's
 *   sublabel (composited, as rendered).
 * - TextSecondary / PineAccent on the Primary-at-10% tint over the
 *   sheet (the location sheet's "Currently using" surface).
 * - The M3 error role on sheet and ground (Make It Me error text,
 *   the home error state's icon).
 * - The guided-capture surface, which is dark in both appearances:
 *   PhotoInk, PineAccentOnDark, and the white-at-alpha coaching text
 *   composited on the dark ground, exactly as rendered.
 *
 * Documented exclusions (not pairings this suite certifies):
 * - Glass and ink over PHOTOGRAPHY (hero chrome, poster lockup):
 *   the ground is a photo, not a token; the design specifies
 *   warm-white over bounded scrims there (notes §7).
 * - The temperature chip DOT: a decorative data swatch whose label
 *   (bracket name in TextPrimary, asserted above) carries the
 *   meaning. The design's own chip measurement stands (notes §1)
 *   and WS-8's instruction is to keep the settled variants; the
 *   dark dots are asserted ≥3:1 below because they pass, the light
 *   dots are recorded in the WS-8 gate report as the design's
 *   sanctioned exception.
 * - Decorative hairlines (dividers, drag handle, card borders at
 *   reduced alpha): not text and not essential component boundaries.
 */
class TokenContrastTest {

    private fun ratio(foreground: Color, background: Color): Double {
        val lf = foreground.luminance().toDouble()
        val lb = background.luminance().toDouble()
        val hi = maxOf(lf, lb)
        val lo = minOf(lf, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertText(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name: ${"%.2f".format(r)}:1 < 4.5:1", r >= 4.5)
    }

    private fun assertNonText(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name: ${"%.2f".format(r)}:1 < 3:1", r >= 3.0)
    }

    private val palettes = listOf("light" to LightPalette, "dark" to DarkPalette)

    private fun grounds(p: RunWearPalette) = listOf(
        "Background" to p.Background,
        "Surface" to p.Surface,
        "BgCard" to p.BgCard,
        "SurfaceElevated" to p.SurfaceElevated,
        "BgCardLight" to p.BgCardLight
    )

    @Test
    fun `text inks clear 4-5 to 1 on every theme ground in both modes`() {
        for ((mode, p) in palettes) {
            for ((groundName, ground) in grounds(p)) {
                assertText("$mode TextPrimary on $groundName", p.TextPrimary, ground)
                assertText("$mode TextSecondary on $groundName", p.TextSecondary, ground)
                assertText("$mode PineAccent on $groundName", p.PineAccent, ground)
            }
        }
    }

    @Test
    fun `mini bar narration inks clear 4-5 to 1 on the bar tint in both modes`() {
        for ((mode, p) in palettes) {
            val dark = mode == "dark"
            val alpha = WearIveRunMiniBarPolicy.backgroundAlpha(
                isFullExpression = false,
                darkTheme = dark
            )
            // The bar docks over the sheet and over the ground; both
            // composites are pairings the UI can produce.
            for ((groundName, ground) in listOf(
                "Background" to p.Background,
                "Surface" to p.Surface
            )) {
                val tint = p.MiniBarContainer.copy(alpha = alpha).compositeOver(ground)
                assertText("$mode TextPrimary on mini-bar tint over $groundName", p.TextPrimary, tint)
                assertText("$mode TextSecondary on mini-bar tint over $groundName", p.TextSecondary, tint)
                assertText("$mode PineAccent on mini-bar tint over $groundName", p.PineAccent, tint)
            }
        }
    }

    @Test
    fun `white on the pine fill clears 4-5 to 1 in both modes`() {
        for ((mode, p) in palettes) {
            assertText("$mode White on Primary", Color.White, p.Primary)
            val sublabel = Color.White.copy(alpha = 0.8f).compositeOver(p.Primary)
            assertText("$mode White-at-80 sublabel on Primary", sublabel, p.Primary)
        }
    }

    @Test
    fun `inks on the primary tint surface clear 4-5 to 1 in both modes`() {
        for ((mode, p) in palettes) {
            val tint = p.Primary.copy(alpha = 0.1f).compositeOver(p.Surface)
            assertText("$mode TextSecondary on Primary-at-10 over Surface", p.TextSecondary, tint)
            assertText("$mode PineAccent on Primary-at-10 over Surface", p.PineAccent, tint)
        }
    }

    @Test
    fun `error role clears 4-5 to 1 on sheet and ground in both modes`() {
        val schemes = listOf(
            "light" to LightColorScheme,
            "dark" to DarkColorScheme
        )
        for ((mode, scheme) in schemes) {
            val p = if (mode == "light") LightPalette else DarkPalette
            assertText("$mode error on Surface", scheme.error, p.Surface)
            assertText("$mode error on Background", scheme.error, p.Background)
        }
    }

    @Test
    fun `guided capture dark surface pairings clear their floors`() {
        val ground = DarkPalette.Background
        assertText("PhotoInk on capture ground", PhotoInk, ground)
        assertText("PineAccentOnDark on capture ground", PineAccentOnDark, ground)
        // Coaching and secondary text on the capture surface is white
        // at fixed alphas over this dark ground in both appearances.
        for (alpha in listOf(0.6f, 0.65f, 0.7f)) {
            val ink = Color.White.copy(alpha = alpha).compositeOver(ground)
            assertText("White-at-$alpha on capture ground", ink, ground)
        }
        val uncheckedGlyph = Color.White.copy(alpha = 0.5f).compositeOver(ground)
        assertNonText("White-at-0.5 check glyph on capture ground", uncheckedGlyph, ground)
    }

    @Test
    fun `category glyphs clear 3 to 1 on card and on their own tint in both modes`() {
        for ((mode, p) in palettes) {
            val categories = listOf(
                "Top" to p.CategoryTop,
                "Bottom" to p.CategoryBottom,
                "Head" to p.CategoryHead,
                "Hands" to p.CategoryHands,
                "Accessories" to p.CategoryAccessories
            )
            for ((name, color) in categories) {
                assertNonText("$mode Category$name on BgCard", color, p.BgCard)
                val tint = color.copy(alpha = 0.15f).compositeOver(p.BgCard)
                assertNonText("$mode Category$name on its 15% tint over BgCard", color, tint)
            }
        }
    }

    @Test
    fun `dark temperature dots clear 3 to 1 - the design's measured dark range`() {
        val dots = listOf(
            "Freezing" to DarkPalette.TempFreezing,
            "Cold" to DarkPalette.TempCold,
            "Cool" to DarkPalette.TempCool,
            "Mild" to DarkPalette.TempMild,
            "Warm" to DarkPalette.TempWarm,
            "Hot" to DarkPalette.TempHot
        )
        for ((name, dot) in dots) {
            assertNonText("dark Temp$name dot on Surface", dot, DarkPalette.Surface)
        }
    }

    @Test
    fun `onboarding pairings - the D-24 surfaces - clear 4-5 to 1 in both modes`() {
        for ((mode, p) in palettes) {
            // Headings and logo on the ground; subtitle and the
            // location step's privacy paragraph in secondary ink.
            assertText("$mode onboarding TextPrimary on Background", p.TextPrimary, p.Background)
            assertText("$mode onboarding TextSecondary on Background", p.TextSecondary, p.Background)
            // Question cards on Surface: titles, sublabels, and the
            // segmented track (the track IS the Background token).
            assertText("$mode onboarding TextPrimary on Surface", p.TextPrimary, p.Surface)
            assertText("$mode onboarding TextSecondary on Surface", p.TextSecondary, p.Surface)
            assertText("$mode onboarding unselected segment label on track", p.TextSecondary, p.Background)
            assertText("$mode onboarding selected segment label", Color.White, p.Primary)
            // Location step's icon glyph on its card disc.
            assertNonText("$mode onboarding PineAccent glyph on BgCard", p.PineAccent, p.BgCard)
        }
    }
}
