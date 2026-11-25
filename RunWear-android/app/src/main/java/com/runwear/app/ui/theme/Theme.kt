package com.runwear.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.runwear.shared.domain.model.AppearancePreference
import com.runwear.shared.domain.model.TempBracket

// ============================================================================
// RUNWEAR DESIGN SYSTEM — RW-25 theme pair (dark + light, pine seed)
// ============================================================================

/**
 * RW-25: the full token set for one appearance. Every token name that
 * [RunWearColors] has always exposed lives here; [DarkPalette] and
 * [LightPalette] are the two instances, and [RunWearTheme] publishes the
 * active one so the legacy `RunWearColors.X` reads keep working at every
 * existing call site while following the resolved theme.
 *
 * The temperature bracket hexes are SHARED by both palettes and must not
 * drift — they are pinned by unit tests (RW-25 gate).
 */
data class RunWearPalette(
    val Primary: Color,
    val PrimaryLight: Color,
    val PrimaryDark: Color,
    val PrimaryGlow: Color,
    val CategoryTop: Color,
    val CategoryBottom: Color,
    val CategoryHead: Color,
    val CategoryHands: Color,
    val CategoryAccessories: Color,
    val TempFreezing: Color,
    val TempCold: Color,
    val TempCool: Color,
    val TempMild: Color,
    val TempWarm: Color,
    val TempHot: Color,
    val Background: Color,
    /** The home sheet's surface (and the M3 surface role). */
    val Surface: Color,
    /** Card-on-sheet fill: outfit cards sit on the sheet surface. */
    val BgCard: Color,
    /**
     * RW-135 spec §2.2/§6: the Wear I've Run footer's ground —
     * light: the page ground #F5F1E8; dark: #2A251E (Warm Charcoal
     * ground). Deliberately NOT [BgCard] in light (that is the
     * card fill #FDFBF6) and NOT [Background] in dark (#16130F).
     */
    val FooterGround: Color,
    val SurfaceElevated: Color,
    val BgCardLight: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    /**
     * RW-144: description-line ink (outfit row second lines) — a
     * step softer than [TextSecondary]: light #807363, dark #988E80.
     */
    val TextDescription: Color,
    /** RW-144: the outfit rows' garment-tile fill: light #F3EEE5, dark #25201A. */
    val GarmentTile: Color,
    /**
     * RW-144: garment silhouette ink on the tiles: light warm ink
     * #33291F at α168, dark cream #F5F1EA at α178.
     */
    val Silhouette: Color,
    /** RW-144: which mode this palette is (drives theme-keyed asset seams). */
    val isDark: Boolean,
    /**
     * WS-8 (RC-8): pine as TEXT. The design table's pine pair — light
     * themes read the seed pine #175E4C on cream (7.41:1 on sheet);
     * dark themes read the design's dark accent #5CC79F (8.08:1 on the
     * charcoal sheet), because the seed pine is only 2.19:1 there.
     * [Primary] stays the seed in both themes for FILLS (buttons,
     * selected segments, switch tracks — white on it is 7.66:1);
     * anything that renders pine as text or as a foreground glyph on a
     * theme ground must read this token instead.
     */
    val PineAccent: Color,
    val GlassBackground: Color,
    val GlassBorder: Color,
    val GlassBackgroundHover: Color,
    /** RW-26: the mini bar's container tint base (sheet in light,
     * card in dark); the bar applies the policy alpha over it. */
    val MiniBarContainer: Color,
    /**
     * GEN-5 (RW-29 cream map): the design's structure line — the
     * ink at ~10% (light #17191C, dark #F5F1EA). The gen-4 code
     * painted dividers and tracks in BgCardLight, which EQUALS
     * the sheet colour in the light appearance, so Settings lost
     * every structure line there. Hairlines are this token,
     * never a fill colour.
     */
    val Hairline: Color,
    /**
     * GEN-5 (RW-29 cream map): the paywall's highlighted-card
     * fill — the design's pale pine tint #F2F8F5 inside a 2px
     * pine border (mockups .pw.hero). The dark value is the same
     * tint idea on the charcoal card (the mockups draw no dark
     * paywall hero; derived as pine over #2A251E).
     */
    val PaywallTint: Color
)

// Pine seed (RW-25 rebrand): replaces the legacy teal primary family in
// BOTH palettes. Buttons/links that read Primary (shop gradient, the
// feedback link, toggles) rebrand with it — intended.
private val PinePrimary = Color(0xFF175E4C)
private val PinePrimaryLight = Color(0xFF2E7D64)
private val PinePrimaryDark = Color(0xFF0E3D30)
private val PinePrimaryGlow = Color(0x4D175E4C) // pine at 30% alpha

/**
 * WS-8: the design's dark pine accent (#5CC79F, notes §1) — the dark
 * theme's [RunWearPalette.PineAccent], and the pine for surfaces that
 * are dark in BOTH themes (the guided-capture camera surface).
 */
val PineAccentOnDark = Color(0xFF5CC79F)

/**
 * RW-144 hero chrome/scrim inks (theme-independent — they live over
 * the photograph in both modes, per the v8 palette audit):
 * the translucent chrome fill (24,20,14→ (24,20,16)) — place pill,
 * gear circle and the date/time chip draw this ink at their ruled
 * alphas (84 / 84 / 72 of 255).
 */
val HeroChromeInk = Color(0xFF181410)

/** RW-144: the feels-like scrim zone's ink (14,11,8). */
val HeroScrimInk = Color(0xFF0E0B08)

/** RW-144: the hero's top scrim ink (26,20,14). */
val HeroTopScrimInk = Color(0xFF1A140E)

/**
 * WS-8: warm white #F5F1EA — the design's ink for photography and
 * other always-dark surfaces (script logo and scaffold badge over the
 * hero photo, the Morning Poster's lockup and footer over its scrim).
 * Same value as the dark palette's TextPrimary; named separately
 * because these surfaces do not follow the app theme.
 */
val PhotoInk = Color(0xFFF5F1EA)

/**
 * WS-8: the Morning Poster's area-scrim base, rgba(35,35,39) — the
 * design §7.4 scrim color (alpha is applied at the call site).
 */
val PosterScrim = Color(0xFF232327)

// Temperature bracket colors — the newer Android set, kept exactly.
private val TempFreezingHex = Color(0xFF8B5CF6) // Purple - <20°F
private val TempColdHex = Color(0xFF6366F1)     // Indigo - 20-34°F
private val TempCoolHex = Color(0xFF3B82F6)     // Blue - 35-49°F
private val TempMildHex = Color(0xFF10B981)     // Teal - 50-64°F
private val TempWarmHex = Color(0xFFF59E0B)     // Amber - 65-79°F
private val TempHotHex = Color(0xFFF97316)      // Orange - 80°F+

// Glass morphism colors - PWA v2.9 exact values. Photo chrome is
// theme-independent, so both palettes carry the same glass tokens.
private val GlassBackgroundHex = Color(0x1FFFFFFF)   // rgba(255, 255, 255, 0.12)
private val GlassBorderHex = Color(0x26FFFFFF)       // rgba(255, 255, 255, 0.15)
private val GlassBackgroundHoverHex = Color(0x26FFFFFF)

val DarkPalette = RunWearPalette(
    Primary = PinePrimary,
    PrimaryLight = PinePrimaryLight,
    PrimaryDark = PinePrimaryDark,
    PrimaryGlow = PinePrimaryGlow,
    // Category Colors (for outfit items) — WS-8: brightened dark
    // variants. The light set computes to 1.90–3.27:1 on the charcoal
    // card, below the 3:1 non-text floor for the category glyphs; this
    // set clears 3:1 on card and sheet (pinned by TokenContrastTest).
    CategoryTop = Color(0xFF26A69A),
    CategoryBottom = Color(0xFF42A5F5),
    CategoryHead = Color(0xFF66BB6A),
    CategoryHands = Color(0xFF9575CD),
    CategoryAccessories = Color(0xFFFF7043),
    TempFreezing = TempFreezingHex,
    TempCold = TempColdHex,
    TempCool = TempCoolHex,
    TempMild = TempMildHex,
    TempWarm = TempWarmHex,
    TempHot = TempHotHex,
    // RW-26 consolidated tokens (Warm Charcoal): warmth lives in the
    // neutrals only — pine, temperature hexes and data ink unchanged.
    // Card (#2A251E) is DISTINCT from sheet (#211D17): outfit cards sit
    // visibly on the sheet (closes the RW-25 phase-1 flag).
    Background = Color(0xFF16130F),
    Surface = Color(0xFF211D17),
    BgCard = Color(0xFF2A251E),
    FooterGround = Color(0xFF2A251E),
    SurfaceElevated = Color(0xFF2A251E),
    BgCardLight = Color(0xFF2A251E),
    TextPrimary = Color(0xFFF5F1EA),
    TextSecondary = Color(0xFFB8AEA0),
    TextDescription = Color(0xFF988E80),
    GarmentTile = Color(0xFF25201A),
    Silhouette = Color(0xB2F5F1EA),
    isDark = true,
    PineAccent = PineAccentOnDark,
    GlassBackground = GlassBackgroundHex,
    GlassBorder = GlassBorderHex,
    GlassBackgroundHover = GlassBackgroundHoverHex,
    MiniBarContainer = Color(0xFF2A251E),
    // RW-144 palette of record: dark hairline is cream at α22.
    Hairline = Color(0x16F5F1EA),
    PaywallTint = Color(0xFF1E2B24)
)

val LightPalette = RunWearPalette(
    Primary = PinePrimary,
    PrimaryLight = PinePrimaryLight,
    PrimaryDark = PinePrimaryDark,
    PrimaryGlow = PinePrimaryGlow,
    CategoryTop = Color(0xFF00796B),
    CategoryBottom = Color(0xFF1565C0),
    CategoryHead = Color(0xFF2E7D32),
    CategoryHands = Color(0xFF5E35B1),
    CategoryAccessories = Color(0xFFE65100),
    TempFreezing = TempFreezingHex,
    TempCold = TempColdHex,
    TempCool = TempCoolHex,
    TempMild = TempMildHex,
    TempWarm = TempWarmHex,
    TempHot = TempHotHex,
    // RW-26 consolidated tokens (cream pair): ground #F5F1E8,
    // sheet/cards #FDFBF6. RW-144 (Nov 8, 2026) warms the ink per
    // the approved first-screen palette of record: primary ink is
    // now the deep warm brown #33291F (the RW-26 near-black
    // #17191C is retired on the cream — "nothing on the cream
    // stays true black"), secondary #685D4F, descriptions #807363,
    // hairline warm ink at α26.
    Background = Color(0xFFF5F1E8),
    Surface = Color(0xFFFDFBF6),
    BgCard = Color(0xFFFDFBF6),
    FooterGround = Color(0xFFF5F1E8),
    SurfaceElevated = Color(0xFFFDFBF6),
    BgCardLight = Color(0xFFFDFBF6),
    TextPrimary = Color(0xFF33291F),
    TextSecondary = Color(0xFF685D4F),
    TextDescription = Color(0xFF807363),
    GarmentTile = Color(0xFFF3EEE5),
    Silhouette = Color(0xA833291F),
    isDark = false,
    PineAccent = PinePrimary,
    GlassBackground = GlassBackgroundHex,
    GlassBorder = GlassBorderHex,
    GlassBackgroundHover = GlassBackgroundHoverHex,
    MiniBarContainer = Color(0xFFFDFBF6),
    Hairline = Color(0x1A33291F),
    PaywallTint = Color(0xFFF2F8F5)
)

/**
 * RunWear Color System — now a theme-driven token holder (RW-25).
 *
 * Every property delegates to the palette [RunWearTheme] most recently
 * published (dark until a theme composes). Reads during composition
 * subscribe to the palette snapshot, so an appearance change re-themes
 * every surface that reads tokens — which is all of them.
 */
object RunWearColors {
    private var activePalette by mutableStateOf<RunWearPalette>(DarkPalette)

    internal fun setActivePalette(palette: RunWearPalette) {
        activePalette = palette
    }

    /** The palette currently published by [RunWearTheme] (test-visible). */
    val currentPalette: RunWearPalette get() = activePalette

    val Primary: Color get() = activePalette.Primary
    val PrimaryLight: Color get() = activePalette.PrimaryLight
    val PrimaryDark: Color get() = activePalette.PrimaryDark
    val PrimaryGlow: Color get() = activePalette.PrimaryGlow

    val CategoryTop: Color get() = activePalette.CategoryTop
    val CategoryBottom: Color get() = activePalette.CategoryBottom
    val CategoryHead: Color get() = activePalette.CategoryHead
    val CategoryHands: Color get() = activePalette.CategoryHands
    val CategoryAccessories: Color get() = activePalette.CategoryAccessories

    val TempFreezing: Color get() = activePalette.TempFreezing
    val TempCold: Color get() = activePalette.TempCold
    val TempCool: Color get() = activePalette.TempCool
    val TempMild: Color get() = activePalette.TempMild
    val TempWarm: Color get() = activePalette.TempWarm
    val TempHot: Color get() = activePalette.TempHot

    val Background: Color get() = activePalette.Background
    val Surface: Color get() = activePalette.Surface
    val BgCard: Color get() = activePalette.BgCard
    val FooterGround: Color get() = activePalette.FooterGround
    val SurfaceElevated: Color get() = activePalette.SurfaceElevated
    val BgCardLight: Color get() = activePalette.BgCardLight

    val TextPrimary: Color get() = activePalette.TextPrimary
    val TextSecondary: Color get() = activePalette.TextSecondary
    val TextDescription: Color get() = activePalette.TextDescription
    val GarmentTile: Color get() = activePalette.GarmentTile
    val Silhouette: Color get() = activePalette.Silhouette
    /** RW-144: theme-keyed seams (e.g. garment tiles) read this. */
    val isDarkTheme: Boolean get() = activePalette.isDark
    val PineAccent: Color get() = activePalette.PineAccent

    val GlassBackground: Color get() = activePalette.GlassBackground
    val GlassBorder: Color get() = activePalette.GlassBorder
    val GlassBackgroundHover: Color get() = activePalette.GlassBackgroundHover
    val MiniBarContainer: Color get() = activePalette.MiniBarContainer
    val Hairline: Color get() = activePalette.Hairline
    val PaywallTint: Color get() = activePalette.PaywallTint
}

// WS-8 (RC-8): the pre-RW-25 legacy alias colors (NeoMint,
// ElectricCoral, PrimaryOrange, SecondaryBlue, the legacy Temp* set)
// and the deprecated getTempColor() are deleted — none were in the
// design token table, and no built call site read them.

// Font family - use system default (Roboto on Android)
// Plus Jakarta Sans would require custom font files
val InterFontFamily = FontFamily.Default

// ============================================================================
// APPEARANCE RESOLUTION (RW-25)
// ============================================================================

/**
 * Resolve the user's [AppearancePreference] to the dark-theme flag
 * [RunWearTheme] consumes. SYSTEM follows the device; LIGHT/DARK pin.
 * Pure — unit-tested as a truth table.
 */
fun resolveDarkTheme(preference: AppearancePreference, systemInDarkTheme: Boolean): Boolean =
    when (preference) {
        AppearancePreference.SYSTEM -> systemInDarkTheme
        AppearancePreference.LIGHT -> false
        AppearancePreference.DARK -> true
    }

// ============================================================================
// TEMPERATURE COLOR FUNCTIONS
// ============================================================================

/**
 * Get the accent color for a given temperature (in Fahrenheit)
 * Used for temperature display text, accents, and category indicators
 */
fun getTemperatureColor(tempFahrenheit: Double): Color = when {
    tempFahrenheit < 20 -> RunWearColors.TempFreezing
    tempFahrenheit < 35 -> RunWearColors.TempCold
    tempFahrenheit < 50 -> RunWearColors.TempCool
    tempFahrenheit < 65 -> RunWearColors.TempMild
    tempFahrenheit < 80 -> RunWearColors.TempWarm
    else -> RunWearColors.TempHot
}

/**
 * RW-25: the temperature color for a [TempBracket] — the sheet's
 * temperature chip dot. Same hexes as [getTemperatureColor]; the
 * bracket boundaries in [TempBracket.fromFahrenheit] align with the
 * thresholds above. Pinned by unit tests.
 */
fun temperatureBracketColor(bracket: TempBracket): Color = when (bracket) {
    TempBracket.FREEZING -> RunWearColors.TempFreezing
    TempBracket.COLD -> RunWearColors.TempCold
    TempBracket.COOL -> RunWearColors.TempCool
    TempBracket.MILD -> RunWearColors.TempMild
    TempBracket.WARM -> RunWearColors.TempWarm
    TempBracket.HOT -> RunWearColors.TempHot
}

/**
 * Get a subtle tint overlay color for the hero image
 * Uses 12-15% alpha for visual effect without obscuring image
 */
fun getTemperatureTint(tempFahrenheit: Double): Color {
    val baseColor = getTemperatureColor(tempFahrenheit)
    return baseColor.copy(alpha = 0.12f)
}

/**
 * Get stronger tint for fallback gradient when no hero image exists
 */
fun getTemperatureFallbackGradient(tempFahrenheit: Double): Color {
    val baseColor = getTemperatureColor(tempFahrenheit)
    return baseColor.copy(alpha = 0.4f)
}

// ============================================================================
// MATERIAL 3 COLOR SCHEMES — roles rebuilt from the palettes (RW-25)
// ============================================================================

internal val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.Primary,
    onPrimary = com.runwear.app.ui.theme.PhotoInk,
    primaryContainer = DarkPalette.PrimaryDark,
    onPrimaryContainer = DarkPalette.PineAccent,
    secondary = DarkPalette.PineAccent,
    onSecondary = Color.Black,
    secondaryContainer = DarkPalette.PrimaryDark,
    onSecondaryContainer = DarkPalette.PineAccent,
    tertiary = DarkPalette.TempWarm,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5F3D00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = DarkPalette.Background,
    onBackground = DarkPalette.TextPrimary,
    surface = DarkPalette.Surface,
    onSurface = DarkPalette.TextPrimary,
    surfaceVariant = DarkPalette.SurfaceElevated,
    onSurfaceVariant = DarkPalette.TextSecondary,
    // GEN-5 (RW-29 cream map): claim the container family so
    // defaulting components (chips, menus, segmented controls)
    // render in the warm charcoal steps instead of the Material
    // baseline's lavender-grey; outlineVariant is the pre-blended
    // hairline on the sheet, not an invented hex.
    surfaceContainerLowest = DarkPalette.Background,
    surfaceContainerLow = DarkPalette.Surface,
    surfaceContainer = DarkPalette.Surface,
    surfaceContainerHigh = DarkPalette.SurfaceElevated,
    surfaceContainerHighest = DarkPalette.SurfaceElevated,
    inverseSurface = DarkPalette.TextPrimary,
    inverseOnSurface = DarkPalette.Background,
    outline = DarkPalette.TextSecondary,
    outlineVariant = Color(0xFF36322C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

internal val LightColorScheme = lightColorScheme(
    primary = LightPalette.Primary,
    onPrimary = com.runwear.app.ui.theme.PhotoInk,
    primaryContainer = LightPalette.PrimaryLight,
    onPrimaryContainer = PhotoInk,
    secondary = LightPalette.Primary,
    onSecondary = PhotoInk,
    secondaryContainer = LightPalette.BgCard,
    onSecondaryContainer = LightPalette.PrimaryDark,
    tertiary = LightPalette.TempWarm,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF5F3D00),
    background = LightPalette.Background,
    onBackground = LightPalette.TextPrimary,
    surface = LightPalette.Surface,
    onSurface = LightPalette.TextPrimary,
    surfaceVariant = LightPalette.BgCard,
    onSurfaceVariant = LightPalette.TextSecondary,
    // GEN-5 (RW-29 cream map): as the dark scheme — container
    // family claimed in the cream steps; outlineVariant is the
    // pre-blended hairline on the sheet.
    surfaceContainerLowest = LightPalette.BgCard,
    surfaceContainerLow = LightPalette.Background,
    surfaceContainer = LightPalette.Surface,
    surfaceContainerHigh = LightPalette.BgCard,
    surfaceContainerHighest = LightPalette.BgCard,
    inverseSurface = LightPalette.TextPrimary,
    inverseOnSurface = LightPalette.Surface,
    outline = LightPalette.TextSecondary,
    outlineVariant = Color(0xFFEAE5D8),
    error = Color(0xFFB3261E),
    onError = PhotoInk,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@Composable
fun RunWearTheme(
    // RW-25: an unresolved call follows the SYSTEM appearance — the
    // pre-RW-25 hardcoded `= true` forced dark on every surface that
    // did not pass the flag (tests, previews, secondary screens).
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color stays OFF by design (RW-25): Material You would
    // re-seed the scheme from the wallpaper and fight the pine brand
    // seed. The pine palette is the product's color identity.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette

    // Publish the palette to the RunWearColors token holder so every
    // legacy token read follows this theme.
    SideEffect {
        RunWearColors.setActivePalette(palette)
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
