package com.runwear.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand colors
val PrimaryOrange = Color(0xFFFF6B35)
val PrimaryOrangeDark = Color(0xFFE55A2B)
val SecondaryBlue = Color(0xFF4A90D9)

// Temperature colors
val TempHot = Color(0xFFFF5722)
val TempWarm = Color(0xFFFF9800)
val TempMild = Color(0xFF4CAF50)
val TempCool = Color(0xFF2196F3)
val TempCold = Color(0xFF3F51B5)
val TempFreezing = Color(0xFF9C27B0)

// Light theme colors (fallback when dynamic colors not available)
private val LightColorScheme = lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3FF),
    onSecondaryContainer = Color(0xFF001B3E),
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF002106),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF53433D),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD8C2B8)
)

// Dark theme colors (fallback when dynamic colors not available)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB599),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF832800),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFABC7FF),
    onSecondary = Color(0xFF002F64),
    secondaryContainer = Color(0xFF00458D),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Color(0xFF00531A),
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF53433D)
)

@Composable
fun RunWearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enable Material You dynamic colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Use dynamic colors on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Helper function to get temperature color based on Fahrenheit value
fun getTemperatureColor(tempFahrenheit: Double): Color = when {
    tempFahrenheit >= 80 -> TempHot
    tempFahrenheit >= 65 -> TempWarm
    tempFahrenheit >= 50 -> TempMild
    tempFahrenheit >= 35 -> TempCool
    tempFahrenheit >= 20 -> TempCold
    else -> TempFreezing
}
