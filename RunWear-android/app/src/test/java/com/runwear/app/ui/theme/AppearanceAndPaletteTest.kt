package com.runwear.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.runwear.shared.domain.model.AppearancePreference
import com.runwear.shared.domain.model.TempBracket
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-25 gates: the appearance-resolution truth table, the pine seed
 * and palette token pins, and the temperature bracket → color hex
 * pins (the bracket hexes must never drift — both palettes share them).
 */
class AppearanceResolutionTest {

    @Test
    fun `SYSTEM follows the device appearance`() {
        assertEquals(true, resolveDarkTheme(AppearancePreference.SYSTEM, systemInDarkTheme = true))
        assertEquals(false, resolveDarkTheme(AppearancePreference.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `LIGHT pins light regardless of the device`() {
        assertEquals(false, resolveDarkTheme(AppearancePreference.LIGHT, systemInDarkTheme = true))
        assertEquals(false, resolveDarkTheme(AppearancePreference.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun `DARK pins dark regardless of the device`() {
        assertEquals(true, resolveDarkTheme(AppearancePreference.DARK, systemInDarkTheme = true))
        assertEquals(true, resolveDarkTheme(AppearancePreference.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `SYSTEM is the first entry - the stored-format default`() {
        // PreferencesRepository reads a missing key as ordinal 0 and
        // falls back to SYSTEM; the enum order is the stored format.
        assertEquals(AppearancePreference.SYSTEM, AppearancePreference.entries[0])
    }
}

class PalettePinsTest {

    @Test
    fun `pine seed pins in both palettes`() {
        listOf(DarkPalette, LightPalette).forEach { palette ->
            assertEquals(Color(0xFF175E4C), palette.Primary)
            assertEquals(Color(0xFF2E7D64), palette.PrimaryLight)
            assertEquals(Color(0xFF0E3D30), palette.PrimaryDark)
            assertEquals(Color(0x4D175E4C), palette.PrimaryGlow)
        }
    }

    @Test
    fun `dark palette is the RW-26 Warm Charcoal token set`() {
        assertEquals(Color(0xFF16130F), DarkPalette.Background)
        assertEquals(Color(0xFF211D17), DarkPalette.Surface)
        // Card token is DISTINCT from the sheet (RW-25 flag closure).
        assertEquals(Color(0xFF2A251E), DarkPalette.BgCard)
        assertEquals(Color(0xFF2A251E), DarkPalette.SurfaceElevated)
        assertEquals(Color(0xFFF5F1EA), DarkPalette.TextPrimary)
        assertEquals(Color(0xFFB8AEA0), DarkPalette.TextSecondary)
        // RW-144 dark additions: descriptions #988E80, tiles #25201A,
        // silhouettes cream at alpha 178, hairline cream at alpha 22.
        assertEquals(Color(0xFF988E80), DarkPalette.TextDescription)
        assertEquals(Color(0xFF25201A), DarkPalette.GarmentTile)
        assertEquals(Color(0xB2F5F1EA), DarkPalette.Silhouette)
        assertEquals(Color(0x16F5F1EA), DarkPalette.Hairline)
        // WS-8: the design's dark pine accent is the dark PineAccent;
        // the third muted tier (TextMuted #9C9484) is deleted.
        assertEquals(Color(0xFF5CC79F), DarkPalette.PineAccent)
        assertEquals(Color(0xFF2A251E), DarkPalette.MiniBarContainer)
    }

    @Test
    fun `light palette is the RW-26 cream token set`() {
        assertEquals(Color(0xFFF5F1E8), LightPalette.Background)
        assertEquals(Color(0xFFFDFBF6), LightPalette.Surface)
        assertEquals(Color(0xFFFDFBF6), LightPalette.SurfaceElevated)
        assertEquals(Color(0xFFFDFBF6), LightPalette.BgCard)
        // RW-144 (Nov 8, 2026): the light ink warmed to the approved
        // palette of record — warm brown #33291F, secondary #685D4F,
        // descriptions #807363 (the RW-26 near-black set retired).
        assertEquals(Color(0xFF33291F), LightPalette.TextPrimary)
        assertEquals(Color(0xFF685D4F), LightPalette.TextSecondary)
        assertEquals(Color(0xFF807363), LightPalette.TextDescription)
        assertEquals(Color(0xFFF3EEE5), LightPalette.GarmentTile)
        assertEquals(Color(0xA833291F), LightPalette.Silhouette)
        assertEquals(Color(0x1A33291F), LightPalette.Hairline)
        // WS-8: light keeps the seed pine as its PineAccent.
        assertEquals(Color(0xFF175E4C), LightPalette.PineAccent)
        assertEquals(Color(0xFFFDFBF6), LightPalette.MiniBarContainer)
    }

    @Test
    fun `category colors - light set in light palette, brightened set in dark`() {
        // Light set (unchanged): the glyph colors of record.
        assertEquals(Color(0xFF00796B), LightPalette.CategoryTop)
        assertEquals(Color(0xFF1565C0), LightPalette.CategoryBottom)
        assertEquals(Color(0xFF2E7D32), LightPalette.CategoryHead)
        assertEquals(Color(0xFF5E35B1), LightPalette.CategoryHands)
        assertEquals(Color(0xFFE65100), LightPalette.CategoryAccessories)
        // WS-8 dark variants: the light set is 1.90–3.27:1 on the
        // charcoal card; these clear the 3:1 non-text floor there.
        assertEquals(Color(0xFF26A69A), DarkPalette.CategoryTop)
        assertEquals(Color(0xFF42A5F5), DarkPalette.CategoryBottom)
        assertEquals(Color(0xFF66BB6A), DarkPalette.CategoryHead)
        assertEquals(Color(0xFF9575CD), DarkPalette.CategoryHands)
        assertEquals(Color(0xFFFF7043), DarkPalette.CategoryAccessories)
    }

    @Test
    fun `glass tokens are theme-independent`() {
        assertEquals(DarkPalette.GlassBackground, LightPalette.GlassBackground)
        assertEquals(DarkPalette.GlassBorder, LightPalette.GlassBorder)
        assertEquals(DarkPalette.GlassBackgroundHover, LightPalette.GlassBackgroundHover)
        assertEquals(Color(0x1FFFFFFF), DarkPalette.GlassBackground)
        assertEquals(Color(0x26FFFFFF), DarkPalette.GlassBorder)
    }
}

class TemperatureBracketColorTest {

    @Test
    fun `bracket to color hex pins`() {
        assertEquals(Color(0xFF8B5CF6), temperatureBracketColor(TempBracket.FREEZING))
        assertEquals(Color(0xFF6366F1), temperatureBracketColor(TempBracket.COLD))
        assertEquals(Color(0xFF3B82F6), temperatureBracketColor(TempBracket.COOL))
        assertEquals(Color(0xFF10B981), temperatureBracketColor(TempBracket.MILD))
        assertEquals(Color(0xFFF59E0B), temperatureBracketColor(TempBracket.WARM))
        assertEquals(Color(0xFFF97316), temperatureBracketColor(TempBracket.HOT))
    }

    @Test
    fun `bracket colors agree with the continuous temperature color at bracket interiors`() {
        listOf(10.0, 25.0, 42.0, 58.0, 72.0, 90.0).forEach { tempF ->
            assertEquals(
                "at $tempF",
                getTemperatureColor(tempF),
                temperatureBracketColor(TempBracket.fromFahrenheit(tempF))
            )
        }
    }

    @Test
    fun `palette temperature tokens carry the same hexes in both palettes`() {
        listOf(DarkPalette, LightPalette).forEach { palette ->
            assertEquals(Color(0xFF8B5CF6), palette.TempFreezing)
            assertEquals(Color(0xFF6366F1), palette.TempCold)
            assertEquals(Color(0xFF3B82F6), palette.TempCool)
            assertEquals(Color(0xFF10B981), palette.TempMild)
            assertEquals(Color(0xFFF59E0B), palette.TempWarm)
            assertEquals(Color(0xFFF97316), palette.TempHot)
        }
    }
}
