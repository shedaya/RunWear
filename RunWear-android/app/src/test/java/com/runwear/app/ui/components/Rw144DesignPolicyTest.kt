package com.runwear.app.ui.components

import androidx.compose.ui.graphics.Color
import com.runwear.app.ui.theme.DarkPalette
import com.runwear.app.ui.theme.HeroChromeInk
import com.runwear.app.ui.theme.HeroScrimInk
import com.runwear.app.ui.theme.HeroTopScrimInk
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.temperatureBracketColor
import androidx.compose.ui.unit.dp
import com.runwear.shared.domain.model.TempBracket
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-144 (Nov 8, 2026): the approved first-screen design's pure
 * rules — palette of record, band palette, chip text forms and
 * geometry, the luminance-driven scrim mapping, and the chip's
 * face-zone placement. Rendered proof lives in
 * Rw144FirstScreenRenderTest; these pins are the arithmetic.
 */
class Rw144DesignPolicyTest {

    // ── Palette of record (notes-v8 audit table) ─────────────────

    @Test
    fun `rw144 palette - light mode tokens`() {
        assertEquals(Color(0xFFF5F1E8), LightPalette.Background) // ground
        assertEquals(Color(0xFFFDFBF6), LightPalette.Surface) // sheet/card
        assertEquals(Color(0xFF33291F), LightPalette.TextPrimary) // warm ink
        assertEquals(Color(0xFF685D4F), LightPalette.TextSecondary)
        assertEquals(Color(0xFF807363), LightPalette.TextDescription)
        assertEquals(Color(0xFF175E4C), LightPalette.PineAccent) // pine
        assertEquals(Color(0xFFF3EEE5), LightPalette.GarmentTile)
        assertEquals(Color(0xA833291F), LightPalette.Silhouette) // ink at alpha 168
        assertEquals(Color(0x1A33291F), LightPalette.Hairline) // ink at alpha 26
    }

    @Test
    fun `rw144 palette - dark mode tokens`() {
        assertEquals(Color(0xFF16130F), DarkPalette.Background) // ground
        assertEquals(Color(0xFF211D17), DarkPalette.Surface) // sheet
        assertEquals(Color(0xFF25201A), DarkPalette.GarmentTile) // tiles
        assertEquals(Color(0xFFF5F1EA), DarkPalette.TextPrimary)
        assertEquals(Color(0xFFB8AEA0), DarkPalette.TextSecondary)
        assertEquals(Color(0xFF988E80), DarkPalette.TextDescription)
        assertEquals(Color(0xFF5CC79F), DarkPalette.PineAccent) // accent
        assertEquals(Color(0xB2F5F1EA), DarkPalette.Silhouette) // cream at alpha 178
        assertEquals(Color(0xFF2A251E), DarkPalette.BgCard) // pill/dock fills
        assertEquals(Color(0x16F5F1EA), DarkPalette.Hairline) // cream at alpha 22
    }

    @Test
    fun `rw144 palette - hero chrome and scrim inks`() {
        assertEquals(Color(0xFF181410), HeroChromeInk) // (24,20,16)
        assertEquals(Color(0xFF0E0B08), HeroScrimInk) // (14,11,8)
        assertEquals(Color(0xFF1A140E), HeroTopScrimInk) // (26,20,14)
    }

    @Test
    fun `rw144 palette - no pure white in either palette's surfaces or text`() {
        val white = Color(0xFFFFFFFF)
        for (palette in listOf(LightPalette, DarkPalette)) {
            assertTrue(palette.Background != white)
            assertTrue(palette.Surface != white)
            assertTrue(palette.BgCard != white)
            assertTrue(palette.TextPrimary != white)
            assertTrue(palette.GarmentTile != white)
        }
        // The lightest permitted surface is the card cream.
        assertEquals(Color(0xFFFDFBF6), LightPalette.Surface)
    }

    // ── Band palette (PWA brackets, attached to bracket names) ───

    @Test
    fun `rw144 band palette - bracket colours are the PWA hexes`() {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(LightPalette)
        assertEquals(Color(0xFF8B5CF6), temperatureBracketColor(TempBracket.FREEZING))
        assertEquals(Color(0xFF6366F1), temperatureBracketColor(TempBracket.COLD))
        assertEquals(Color(0xFF3B82F6), temperatureBracketColor(TempBracket.COOL))
        assertEquals(Color(0xFF10B981), temperatureBracketColor(TempBracket.MILD))
        assertEquals(Color(0xFFF59E0B), temperatureBracketColor(TempBracket.WARM))
        assertEquals(Color(0xFFF97316), temperatureBracketColor(TempBracket.HOT))
        assertEquals("MILD", HeroBand.word(TempBracket.MILD))
    }

    @Test
    fun `rw144 hero geometry - photo is 44 percent of screen height`() {
        // RW-145 REDESIGN re-pin (Sol's hero ruling, Nov 9, 2026):
        // the 0.44 fraction is superseded — the hero takes most
        // of the first viewport (0.72 at standard heights; see
        // HeroComposition). The pin now carries the redesign
        // fraction so the geometry it guards stays exact.
        assertEquals(608.dpValue(), heroPhotoHeight(844).value, 1.0f)
        assertEquals(0.72f, heroPhotoHeight(1000).value / 1000f, 0.01f)
    }

    private fun Int.dpValue(): Float = this.toFloat()

    // ── Chip forms ───────────────────────────────────────────────

    @Test
    fun `rw144 chip - now state reads date and Now, no return segment`() {
        val sel = LocalDateTime.of(2026, 11, 8, 7, 0)
        assertEquals("Sun, Nov 8", HeroChipFormat.dateText(sel))
        assertEquals("Now", HeroChipFormat.timeText(sel, isNowSelected = true))
        assertFalse(HeroChipFormat.showNowReturn(isNowSelected = true))
    }

    @Test
    fun `rw144 chip - picked state reads picked date and time, return segment present`() {
        val sel = LocalDateTime.of(2026, 11, 10, 15, 0)
        assertEquals("Tue, Nov 10", HeroChipFormat.dateText(sel))
        assertEquals("3:00 PM", HeroChipFormat.timeText(sel, isNowSelected = false))
        assertTrue(HeroChipFormat.showNowReturn(isNowSelected = false))
    }

    @Test
    fun `rw144 chip - geometry carries the v7 divider separation`() {
        // 48 mock-pt of clear space each side of the divider,
        // translated at the mock's ~2.74 scale and rounded: 17dp.
        assertEquals(17.dp.value, HeroChipGeometry.DIVIDER_CLEAR_SPACE.value, 0.01f)
        assertEquals(72f / 255f, HeroChipGeometry.FILL_ALPHA, 0.001f)
        assertEquals(26f / 255f, HeroChipGeometry.OUTLINE_ALPHA, 0.001f)
        assertEquals(96f / 255f, HeroChipGeometry.DIVIDER_ALPHA, 0.001f)
    }

    // ── Luminance-driven scrim (the contrast guarantee) ──────────

    @Test
    fun `rw144 luminance - relative luminance endpoints`() {
        assertEquals(0f, HeroLuminancePolicy.relativeLuminance(0, 0, 0), 0.001f)
        assertEquals(1f, HeroLuminancePolicy.relativeLuminance(255, 255, 255), 0.001f)
    }

    @Test
    fun `rw144 luminance - scrim peak is the mock value at and below the floor luminance`() {
        val floor = 186f / 255f
        assertEquals(floor, HeroLuminancePolicy.scrimPeakAlpha(0.0f), 0.001f)
        assertEquals(floor, HeroLuminancePolicy.scrimPeakAlpha(0.30f), 0.001f)
        assertEquals(
            floor, HeroLuminancePolicy.scrimPeakAlpha(HeroLuminancePolicy.FLOOR_LUMINANCE),
            0.001f
        )
    }

    @Test
    fun `rw144 luminance - scrim peak scales up for bright photos, capped`() {
        val mid = HeroLuminancePolicy.scrimPeakAlpha(0.65f)
        assertTrue(mid > 186f / 255f)
        assertTrue(mid < HeroLuminancePolicy.SCRIM_PEAK_MAX)
        assertEquals(
            HeroLuminancePolicy.SCRIM_PEAK_MAX,
            HeroLuminancePolicy.scrimPeakAlpha(1.0f), 0.001f
        )
        // Monotonic across the range.
        var prev = 0f
        for (i in 0..20) {
            val a = HeroLuminancePolicy.scrimPeakAlpha(i / 20f)
            assertTrue(a >= prev)
            prev = a
        }
    }

    @Test
    fun `rw144 luminance - chrome boost is 1 at the floor and rises with luminance`() {
        assertEquals(1f, HeroLuminancePolicy.chromeScrimBoost(0.30f), 0.001f)
        assertTrue(HeroLuminancePolicy.chromeScrimBoost(0.9f) > 1.2f)
        assertTrue(HeroLuminancePolicy.chromeScrimBoost(0.0f) < 1f)
        assertTrue(HeroLuminancePolicy.chromeScrimBoost(0.0f) >= 0.8f)
    }

    // ── Chip face-zone placement ─────────────────────────────────

    @Test
    fun `rw144 chip placement - preferred seat used when it clears the head band`() {
        // Chrome bottom 96, head band 260..320: preferred 154..188 clears.
        assertEquals(
            154f,
            HeroChipPlacement.topDp(96f, 34f, headTopDp = 260f, headBottomDp = 320f),
            0.01f
        )
    }

    @Test
    fun `rw144 chip placement - moves above the head when the preferred seat crosses it`() {
        // Head band 191..248 (the 390x371 hero's audited band):
        // preferred 154..188 ends at 188 <= 191? No — 188 < 191, so
        // this actually clears; use a lower head to force the move.
        val top = HeroChipPlacement.topDp(96f, 34f, headTopDp = 180f, headBottomDp = 237f)
        assertEquals(180f - 34f - 8f, top, 0.01f)
        assertTrue(top + 34f <= 180f)
    }

    @Test
    fun `rw144 chip placement - drops below the head when above does not clear the chrome`() {
        // Head starts at 120: above-seat would be 78 < chrome bottom + 8 (104).
        val top = HeroChipPlacement.topDp(96f, 34f, headTopDp = 120f, headBottomDp = 177f)
        assertEquals(177f + 8f, top, 0.01f)
    }

    @Test
    fun `rw144 chip placement - audited band on the production hero never intersects`() {
        // The production phone hero: 390dp wide, heroPhotoHeight(844).
        val density = 2f // xhdpi harness density
        val wPx = 390f * density
        val hPx = heroPhotoHeight(844).value * density
        val scale = HeroImageGeometry.cropScale(wPx, hPx)
        val headTop = HeroImageGeometry.headTopDisplayPx(wPx, hPx) / density
        val headBottom = headTop +
            (HeroImageGeometry.HEAD_BAND_BOTTOM_FRACTION -
                HeroImageGeometry.HEAD_BAND_TOP_FRACTION) *
            HeroImageGeometry.SOURCE_HEIGHT_PX * scale / density
        val top = HeroChipPlacement.topDp(
            chromeBottomDp = 96f,
            chipHeightDp = HeroChipGeometry.HEIGHT.value,
            headTopDp = headTop,
            headBottomDp = headBottom
        )
        assertTrue(
            "chip [$top, ${top + 34f}] must miss head [$headTop, $headBottom]",
            top + 34f <= headTop || top >= headBottom
        )
    }
}
