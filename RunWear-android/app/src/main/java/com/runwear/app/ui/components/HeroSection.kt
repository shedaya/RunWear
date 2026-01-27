package com.runwear.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.getTemperatureColor
import com.runwear.app.ui.theme.getTemperatureFallbackGradient
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageSelector
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Hero section with full-bleed AI runner image, weather overlay, and controls.
 * This is the main visual centerpiece of the new design.
 *
 * Layout (top to bottom):
 * - Status bar padding
 * - Location button (left) + Settings button (right)
 * - Flex space with hero image
 * - Date/Time pill (centered)
 * - Large temperature display (color-coded)
 * - "Feels like" label
 * - Actual temperature (smaller)
 * - Weather conditions row
 */
@Composable
fun HeroSection(
    weather: WeatherConditions?,
    outfit: OutfitRecommendation?,
    locationName: String,
    selectedDateTime: LocalDateTime,
    genderPreference: GenderPreference,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTempClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.75).dp

    // Calculate temperature color (in Fahrenheit for consistency)
    val tempFahrenheit = weather?.feelsLikeInFahrenheit ?: 65.0
    val tempColor = remember(tempFahrenheit) {
        getTemperatureColor(tempFahrenheit)
    }

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

    // Get hero image URL
    val heroImageUrl = remember(weather, outfit, genderPreference) {
        HeroImageSelector.getImageUrl(weather, outfit, genderPreference)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // 1. Hero Image (or fallback gradient)
        if (heroImageUrl != null) {
            AsyncImage(
                model = heroImageUrl,
                contentDescription = generateHeroAltText(weather, outfit),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Subtle zoom for parallax feel
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
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

        // 2. Temperature Tint Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tempColor.copy(alpha = 0.12f))
        )

        // 3. Gradient Overlay (6-stop for text readability)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.6f),
                            0.15f to Color.Black.copy(alpha = 0.3f),
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            0.85f to Color.Black.copy(alpha = 0.5f),
                            1.0f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // 4. Top Controls (Location + Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassButton(
                icon = Icons.Outlined.LocationOn,
                text = locationName,
                onClick = onLocationClick
            )
            GlassButton(
                icon = Icons.Outlined.Settings,
                onClick = onSettingsClick
            )
        }

        // 5. Bottom Content (Date/Time, Temp, Conditions)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Date/Time Pill
            DateTimePill(
                dateTime = selectedDateTime,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick
            )

            Spacer(Modifier.height(16.dp))

            // Large Temperature (tappable to toggle units)
            Text(
                text = "${animatedTemp}°",
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                color = tempColor,
                letterSpacing = (-2).sp,
                modifier = Modifier.clickable { onTempClick() }
            )

            Text(
                text = "Feels like",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            weather?.let {
                val unitSymbol = if (it.isCelsius) "C" else "F"
                Text(
                    text = "Actual: ${it.temperature.toInt()}°$unitSymbol",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Weather Conditions Row
            WeatherConditionsRow(weather = weather)
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
