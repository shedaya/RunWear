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
    // GEN-5 design pass (RW-28 Addendum 5): the Wear palette joins
    // the adopted design system — the watch is an always-dark
    // surface, so it takes the phone's DARK (Warm Charcoal) pair
    // and the pine family in its dark expression. The gen-4 Wear
    // theme still carried the retired teal primary (#00796B),
    // pure-black / cool-grey surfaces and pure-white ink — the
    // off-system disease Sol named, on the watch.
    // Brand Colors — pine seed #175E4C, dark accent #5CC79F.
    val Primary = Color(0xFF5CC79F)           // Pine accent on dark
    val PrimaryLight = Color(0xFF5CC79F)
    val PrimaryDark = Color(0xFF175E4C)       // Pine seed

    // Category Colors (for outfit chips) — the phone's dark set.
    val CategoryTop = Color(0xFF26A69A)
    val CategoryBottom = Color(0xFF42A5F5)
    val CategoryHead = Color(0xFF66BB6A)
    val CategoryHands = Color(0xFF9575CD)
    val CategoryAccessories = Color(0xFFFF7043)

    // Temperature Bracket Colors — the shared hex set, unchanged.
    val TempFreezing = Color(0xFF8B5CF6)      // Purple - <20°F
    val TempCold = Color(0xFF6366F1)          // Indigo - 20-34°F
    val TempCool = Color(0xFF3B82F6)          // Blue - 35-49°F
    val TempMild = Color(0xFF10B981)          // Teal - 50-64°F
    val TempWarm = Color(0xFFF59E0B)          // Amber - 65-79°F
    val TempHot = Color(0xFFF97316)           // Orange - 80°F+

    // Surface Colors — Warm Charcoal (design of record, Nov 2).
    val Background = Color(0xFF16130F)        // Charcoal ground
    val Surface = Color(0xFF211D17)           // Charcoal sheet
    val SurfaceElevated = Color(0xFF2A251E)   // Charcoal card

    // Text Colors — warm ink pair.
    val TextPrimary = Color(0xFFF5F1EA)
    val TextSecondary = Color(0xFFB8AEA0)
    val TextMuted = Color(0xFFB8AEA0)
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
    onPrimary = Color(0xFF16130F),
    primaryContainer = RunWearColors.PrimaryDark,
    onPrimaryContainer = RunWearColors.TextPrimary,
    secondary = RunWearColors.Primary,
    onSecondary = Color(0xFF16130F),
    secondaryContainer = RunWearColors.PrimaryDark,
    onSecondaryContainer = RunWearColors.TextPrimary,
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5F3D00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = RunWearColors.Background,
    onBackground = RunWearColors.TextPrimary,
    onSurface = RunWearColors.TextPrimary,
    surfaceContainer = RunWearColors.SurfaceElevated,
    onSurfaceVariant = RunWearColors.TextSecondary,
    outline = RunWearColors.TextMuted,
    outlineVariant = Color(0xFF36322C),
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
