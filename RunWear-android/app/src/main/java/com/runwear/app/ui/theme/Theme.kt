package com.runwear.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ============================================================================
// RUNWEAR HERO-IMAGE DESIGN SYSTEM - January 2026
// ============================================================================

/**
 * RunWear Color System
 * Based on hero-image design spec with temperature-reactive tints
 */
object RunWearColors {
    // Brand Colors
    val Primary = Color(0xFF00796B)           // Teal - PRIMARY brand color
    val PrimaryLight = Color(0xFF4DB6AC)      // Lighter teal
    val PrimaryDark = Color(0xFF004D40)       // Darker teal

    // Category Colors (for outfit items) - WCAG AA compliant on white backgrounds
    val CategoryTop = Color(0xFF00796B)       // Teal (tops)
    val CategoryBottom = Color(0xFF1565C0)    // Blue (bottoms)
    val CategoryHead = Color(0xFF2E7D32)      // Green (head)
    val CategoryHands = Color(0xFF5E35B1)     // Purple (hands)
    val CategoryAccessories = Color(0xFFE65100) // Orange (accessories)

    // Temperature Bracket Colors (for hero tint and temperature display)
    val TempFreezing = Color(0xFF8B5CF6)      // Purple - <20°F
    val TempCold = Color(0xFF6366F1)          // Indigo - 20-34°F
    val TempCool = Color(0xFF3B82F6)          // Blue - 35-49°F
    val TempMild = Color(0xFF10B981)          // Teal - 50-64°F
    val TempWarm = Color(0xFFF59E0B)          // Amber - 65-79°F
    val TempHot = Color(0xFFF97316)           // Orange - 80°F+

    // Surface Colors (dark theme base)
    val Background = Color(0xFF0A0A0A)        // Near black
    val Surface = Color(0xFF1A1A1A)           // Dark surface
    val SurfaceElevated = Color(0xFF262626)   // Elevated cards

    // Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF737373)

    // Glass morphism colors
    val GlassBackground = Color(0x26FFFFFF)   // 15% white
    val GlassBorder = Color(0x33FFFFFF)       // 20% white
    val GlassBackgroundHover = Color(0x1AFFFFFF) // 10% white
}

// Legacy color aliases for backward compatibility with existing code
val NeoMint = RunWearColors.Primary
val ElectricCoral = Color(0xFFFF6B6B)
val PrimaryOrange = Color(0xFFFF6B35)
val PrimaryOrangeDark = Color(0xFFE55A2B)
val SecondaryBlue = Color(0xFF4A90D9)

// Legacy temperature colors (kept for backward compatibility)
val TempHot = Color(0xFFFF5722)
val TempWarm = Color(0xFFFF9800)
val TempMild = Color(0xFF4CAF50)
val TempCool = Color(0xFF2196F3)
val TempCold = Color(0xFF3F51B5)
val TempFreezing = Color(0xFF9C27B0)

// Font family - use system default (Roboto on Android)
// Plus Jakarta Sans would require custom font files
val InterFontFamily = FontFamily.Default

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
// MATERIAL 3 COLOR SCHEMES
// ============================================================================

// Dark theme colors (primary theme for hero-image design)
private val DarkColorScheme = darkColorScheme(
    primary = RunWearColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RunWearColors.PrimaryDark,
    onPrimaryContainer = RunWearColors.PrimaryLight,
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00363D),
    onSecondaryContainer = Color(0xFF70F5E6),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5F3D00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = RunWearColors.Background,
    onBackground = RunWearColors.TextPrimary,
    surface = RunWearColors.Surface,
    onSurface = RunWearColors.TextPrimary,
    surfaceVariant = RunWearColors.SurfaceElevated,
    onSurfaceVariant = RunWearColors.TextSecondary,
    outline = RunWearColors.TextMuted,
    outlineVariant = Color(0xFF3D3D3D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// Light theme colors (fallback, but hero design prefers dark)
private val LightColorScheme = lightColorScheme(
    primary = RunWearColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = RunWearColors.PrimaryDark,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = Color(0xFFE65100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFCC80),
    onTertiaryContainer = Color(0xFF5F3D00),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@Composable
fun RunWearTheme(
    darkTheme: Boolean = true, // Default to dark for hero-image design
    dynamicColor: Boolean = false, // Disable dynamic colors to maintain design consistency
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ============================================================================
// LEGACY HELPER FUNCTIONS (for backward compatibility)
// ============================================================================

/**
 * Legacy temperature color function
 * @deprecated Use getTemperatureColor() instead
 */
@Deprecated("Use getTemperatureColor()", ReplaceWith("getTemperatureColor(tempFahrenheit)"))
fun getTempColor(tempF: Double): Color = when {
    tempF >= 85 -> Color(0xFFD32F2F)
    tempF >= 75 -> Color(0xFFD32F2F)
    tempF >= 65 -> Color(0xFFE65100)
    tempF >= 55 -> Color(0xFF00796B)
    tempF >= 45 -> Color(0xFF0277BD)
    tempF >= 35 -> Color(0xFF1565C0)
    tempF >= 25 -> Color(0xFF283593)
    else -> Color(0xFF4A148C)
}
