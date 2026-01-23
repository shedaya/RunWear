package com.runwear.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

// Brand colors
val PrimaryOrange = Color(0xFFFF6B35)
val PrimaryOrangeDark = Color(0xFFE55A2B)
val SecondaryBlue = Color(0xFF4A90D9)

// Temperature colors
object WeatherColors {
    val Hot = Color(0xFFFF5722)
    val Warm = Color(0xFFFF9800)
    val Mild = Color(0xFF4CAF50)
    val Cool = Color(0xFF2196F3)
    val Cold = Color(0xFF3F51B5)
    val Freezing = Color(0xFF9C27B0)
    
    fun forTemperature(tempFahrenheit: Double): Color = when {
        tempFahrenheit >= 80 -> Hot
        tempFahrenheit >= 65 -> Warm
        tempFahrenheit >= 50 -> Mild
        tempFahrenheit >= 35 -> Cool
        tempFahrenheit >= 20 -> Cold
        else -> Freezing
    }
}

object RunWearColors {
    val Primary = PrimaryOrange
    val Secondary = SecondaryBlue
    val Tertiary = Color(0xFF4CAF50)
}

private val WearColorScheme = ColorScheme(
    primary = PrimaryOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF832800),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF00458D),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF00531A),
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = Color.Black,
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFEAE1D9),
    surfaceContainer = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFD8C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF53433D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

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
