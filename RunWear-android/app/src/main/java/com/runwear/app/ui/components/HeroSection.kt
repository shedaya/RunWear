package com.runwear.app.ui.components

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.getTemperatureColor
import com.runwear.app.ui.theme.getTemperatureFallbackGradient
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Hero section with full-bleed AI runner image, weather overlay, and controls.
 * PWA v2.9 aligned.
 *
 * Layout (top to bottom):
 * - Status bar padding
 * - Location button (left) + Share + Settings buttons (right)
 * - Flex space with hero image
 * - Date/Time combined pill (frosted glass)
 * - "FEELS LIKE" label
 * - Large temperature display (color-coded)
 * - "Actual: X°F" line
 * - Weather conditions pills (tappable)
 */
@Composable
fun HeroSection(
    weather: WeatherConditions?,
    outfit: OutfitRecommendation?,
    locationName: String,
    selectedDateTime: LocalDateTime,
    heroImageUrl: String?,
    scrollOffset: Float = 0f,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareClick: () -> Unit,
    onTempClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.75).dp

    // Calculate temperature color with smooth animation (500ms transition)
    val tempFahrenheit = weather?.feelsLikeInFahrenheit ?: 65.0
    val targetTempColor = remember(tempFahrenheit) {
        getTemperatureColor(tempFahrenheit)
    }
    val tempColor by animateColorAsState(
        targetValue = targetTempColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "temperatureColorAnimation"
    )

    // Haptic feedback
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Animated temperature counter
    val actualTemp = weather?.effectiveTemperature?.roundToInt() ?: 0
    val startTemp = maxOf(-10, actualTemp - 10)
    var targetTemp by remember { mutableStateOf(startTemp) }
    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "temperatureCounter"
    )

    LaunchedEffect(actualTemp) {
        targetTemp = startTemp
        kotlinx.coroutines.delay(50)
        targetTemp = actualTemp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
    ) {
        // 1. Hero Image
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (heroImageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(heroImageUrl)
                        .crossfade(500)
                        .build(),
                    contentDescription = generateHeroAltText(weather, outfit),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.1f)
                )
            } else {
                // Fallback gradient when no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    getTemperatureFallbackGradient(tempFahrenheit),
                                    RunWearColors.Background
                                )
                            )
                        )
                )
            }
        }

        // 2. Temperature Tint Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tempColor.copy(alpha = 0.12f))
        )

        // 3. Gradient Overlay - PWA style 6-stop gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.6f),
                            0.15f to Color.Black.copy(alpha = 0.3f),
                            0.40f to Color.Transparent,
                            0.60f to Color.Transparent,
                            0.85f to Color.Black.copy(alpha = 0.5f),
                            1.00f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // 4. Top Controls (Location + Share + Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Location button (pill with text)
            GlassButton(
                icon = Icons.Outlined.LocationOn,
                text = locationName,
                onClick = onLocationClick
            )

            // Right side: Share + Settings (icon-only circular buttons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButtonIcon(
                    icon = Icons.Outlined.Share,
                    onClick = onShareClick,
                    contentDescription = "Share"
                )
                GlassButtonIcon(
                    icon = Icons.Outlined.Settings,
                    onClick = onSettingsClick,
                    contentDescription = "Settings"
                )
            }
        }

        // 5. Bottom Content (Date/Time Pill, Temp, Conditions)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Combined Date/Time Pill (PWA v2.9 frosted glass style)
            DateTimePill(
                dateTime = selectedDateTime,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // "FEELS LIKE" label
            Text(
                text = "FEELS LIKE",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Large Temperature (tappable to toggle units)
            Text(
                text = "${animatedTemp}°",
                fontFamily = BebasNeueFontFamily,
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                color = tempColor,
                letterSpacing = (-2).sp,
                lineHeight = 80.sp,
                modifier = Modifier.clickable {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTempClick()
                }
            )

            // "Actual: X°F" line (PWA format)
            weather?.let {
                val unitSymbol = if (it.isCelsius) "°C" else "°F"
                Text(
                    text = "Actual: ${it.temperature.toInt()}$unitSymbol",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Weather Pills (tappable for detail popups)
            WeatherPillsRow(
                weather = weather,
                onPillClick = onWeatherPillClick
            )
        }
    }
}

/**
 * Generate alt text for hero image for accessibility.
 */
private fun generateHeroAltText(
    weather: WeatherConditions?,
    outfit: OutfitRecommendation?
): String {
    if (weather == null) return "Runner in running gear"

    val tempDesc = when {
        weather.feelsLikeInFahrenheit < 35 -> "cold"
        weather.feelsLikeInFahrenheit < 65 -> "mild"
        else -> "warm"
    }

    val weatherDesc = weather.weatherCode.description.lowercase()
    val itemCount = outfit?.allItems?.size ?: 0

    return "Runner dressed for $tempDesc $weatherDesc conditions with $itemCount items of gear"
}
