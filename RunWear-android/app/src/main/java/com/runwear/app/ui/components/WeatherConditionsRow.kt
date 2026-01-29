package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.shared.domain.model.WeatherConditions

/**
 * PWA v2.9 Weather Pills Row
 *
 * Weather pills displayed in hero section (all tappable for detail popups):
 * 1. Condition - weather emoji only
 * 2. Wind - wind icon + speed (e.g., "12 mph")
 * 3. Humidity - thermostat icon + percentage (NOT water drop per PWA v2.7)
 * 4. Precipitation - water drop icon + probability (ONLY shown if > 0%)
 * 5. UV - sun icon + index (ONLY shown if > 0)
 *
 * PWA CSS values:
 * - background: rgba(255, 255, 255, 0.1) = 10% white
 * - border-radius: 100px (pill shape)
 * - padding: 8px 12px
 * - gap: 6px (between icon and text)
 * - icon: 14px, opacity 0.8
 * - text: 13px, weight 500, white
 */
@Composable
fun WeatherConditionsRow(
    weather: WeatherConditions?,
    modifier: Modifier = Modifier,
    onPillClick: (String) -> Unit = {}
) {
    if (weather == null) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Condition pill (emoji only)
        WeatherPillEmoji(
            emoji = weather.weatherCode.icon,
            onClick = { onPillClick("condition") }
        )

        // Wind pill
        WeatherPill(
            icon = Icons.Outlined.Air,
            text = "${weather.windSpeed.toInt()} ${if (weather.isCelsius) "km/h" else "mph"}",
            onClick = { onPillClick("wind") }
        )

        // Humidity pill - Thermostat icon per PWA v2.7
        WeatherPill(
            icon = Icons.Outlined.Thermostat,
            text = "${weather.humidity}%",
            onClick = { onPillClick("humidity") }
        )

        // Precipitation pill (only shown if > 0%) - WaterDrop icon
        if (weather.precipitationProbability > 0) {
            WeatherPill(
                icon = Icons.Outlined.WaterDrop,
                text = "${weather.precipitationProbability}%",
                onClick = { onPillClick("precipitation") }
            )
        }

        // UV Index pill (only shown if UV > 0)
        if (weather.uvIndex > 0) {
            WeatherPill(
                icon = Icons.Outlined.WbSunny,
                text = "UV ${weather.uvIndex.toInt()}",
                onClick = { onPillClick("uv") }
            )
        }
    }
}

/**
 * Alias for backward compatibility
 */
@Composable
fun WeatherPillsRow(
    weather: WeatherConditions?,
    modifier: Modifier = Modifier,
    onPillClick: (String) -> Unit = {}
) {
    WeatherConditionsRow(weather = weather, modifier = modifier, onPillClick = onPillClick)
}

/**
 * Weather pill with emoji only (for condition).
 */
@Composable
private fun WeatherPillEmoji(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = 16.sp)
        )
    }
}

/**
 * Individual weather pill with icon and text.
 * PWA v2.9 styled with 10% white background and 100dp pill shape.
 */
@Composable
private fun WeatherPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.1f)) // 10% white per PWA
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f), // 0.8 opacity per PWA
                modifier = Modifier.size(14.dp)        // 14dp per PWA
            )
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 13.sp,                  // 13px per PWA
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            )
        }
    }
}
