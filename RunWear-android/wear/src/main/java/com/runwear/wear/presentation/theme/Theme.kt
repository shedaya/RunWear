package com.runwear.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

// ============================================================================
// RUNWEAR WEAR OS HERO-IMAGE DESIGN SYSTEM
// ============================================================================

/**
 * RunWear Wear OS Color System
 * Matches the phone app's hero-image design with adaptations for small screens.
 */
object RunWearColors {
    // Brand Colors
    val Primary = Color(0xFF00796B)           // Teal - PRIMARY brand color
    val PrimaryLight = Color(0xFF4DB6AC)
    val PrimaryDark = Color(0xFF004D40)

    // Category Colors (for outfit chips)
    val CategoryTop = Color(0xFF00796B)       // Teal (tops)
    val CategoryBottom = Color(0xFF1565C0)    // Blue (bottoms)
    val CategoryHead = Color(0xFF2E7D32)      // Green (head)
    val CategoryHands = Color(0xFF5E35B1)     // Purple (hands)
    val CategoryAccessories = Color(0xFFE65100) // Orange (accessories)

    // Temperature Bracket Colors
    val TempFreezing = Color(0xFF8B5CF6)      // Purple - <20°F
    val TempCold = Color(0xFF6366F1)          // Indigo - 20-34°F
    val TempCool = Color(0xFF3B82F6)          // Blue - 35-49°F
    val TempMild = Color(0xFF10B981)          // Teal - 50-64°F
    val TempWarm = Color(0xFFF59E0B)          // Amber - 65-79°F
    val TempHot = Color(0xFFF97316)           // Orange - 80°F+

    // Surface Colors (OLED optimized)
    val Background = Color(0xFF000000)        // Pure black for OLED
    val Surface = Color(0xFF1A1A1A)           // Dark surface
    val SurfaceElevated = Color(0xFF262626)   // Cards

    // Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF737373)
}

/**
 * Temperature-based color functions for Wear OS
 */
object WeatherColors {
    val Hot = RunWearColors.TempHot
    val Warm = RunWearColors.TempWarm
    val Mild = RunWearColors.TempMild
    val Cool = RunWearColors.TempCool
    val Cold = RunWearColors.TempCold
    val Freezing = RunWearColors.TempFreezing

    fun forTemperature(tempFahrenheit: Double): Color = when {
        tempFahrenheit < 20 -> Freezing
        tempFahrenheit < 35 -> Cold
        tempFahrenheit < 50 -> Cool
        tempFahrenheit < 65 -> Mild
        tempFahrenheit < 80 -> Warm
        else -> Hot
    }

    fun forTemperatureTint(tempFahrenheit: Double): Color {
        return forTemperature(tempFahrenheit).copy(alpha = 0.15f)
    }
}

// ============================================================================
// WEAR OS MATERIAL 3 COLOR SCHEME
// ============================================================================

private val WearColorScheme = ColorScheme(
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
    surfaceContainer = RunWearColors.SurfaceElevated,
    onSurfaceVariant = RunWearColors.TextSecondary,
    outline = RunWearColors.TextMuted,
    outlineVariant = Color(0xFF3D3D3D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@Composable
fun RunWearWatchTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        typography = Typography(),
        content = content
    )
}
