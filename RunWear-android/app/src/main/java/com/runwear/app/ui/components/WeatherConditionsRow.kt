package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
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
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions

/**
 * PWA v2.9 Weather Pills Row
 *
 * Weather pills displayed in hero section (all tappable for detail popups):
 * 1. Condition - condition vector icon + the condition's word
 *    (WS-10/A4: the design's strip treatment, frames 1–5; the
 *    pre-WS-10 pill rendered the WeatherCode emoji string, which
 *    the approved design does not have)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherConditionsRow(
    weather: WeatherConditions?,
    modifier: Modifier = Modifier,
    onPillClick: (String) -> Unit = {},
    /**
     * RW-25: on the home sheet the pills render FLAT (tonal surface
     * fill, ink text) instead of the photo-side white-glass treatment.
     * Behavior — which pills appear, their tap targets — is unchanged.
     */
    onSheet: Boolean = false,
    /** WS-2: the render-time unit projection for the wind pill. */
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    if (weather == null) return

    val pillBackground = if (onSheet) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White.copy(alpha = 0.1f)
    }
    val pillInk = if (onSheet) RunWearColors.TextPrimary else com.runwear.app.ui.theme.PhotoInk

    // FlowRow, not Row: in a narrow hero (foldable rail pane, landscape
    // phone) a fixed Row measures pills in order and gives the LAST one
    // whatever width remains — the UV pill was crushed to a few dp and
    // its text stacked one character per line (field report, Oct 30).
    // Flowing wraps the overflow onto a second line instead; when all
    // pills fit on one line the rendering is identical to the old Row.
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Condition pill — the designed strip treatment: the
        // condition's vector icon + its word, like every other pill.
        WeatherPill(
            icon = weather.weatherCode.conditionIcon(),
            text = weather.weatherCode.description,
            onClick = { onPillClick("condition") },
            background = pillBackground,
            ink = pillInk
        )

        // Wind pill
        WeatherPill(
            icon = Icons.Outlined.Air,
            text = HeroSheetFormat.windText(weather, temperatureUnit),
            onClick = { onPillClick("wind") },
            background = pillBackground,
            ink = pillInk
        )

        // Humidity pill - Thermostat icon per PWA v2.7
        WeatherPill(
            icon = Icons.Outlined.Thermostat,
            text = "${weather.humidity}%",
            onClick = { onPillClick("humidity") },
            background = pillBackground,
            ink = pillInk
        )

        // Precipitation pill (only shown if > 0%) - WaterDrop icon
        if (weather.precipitationProbability > 0) {
            WeatherPill(
                icon = Icons.Outlined.WaterDrop,
                text = "${weather.precipitationProbability}%",
                onClick = { onPillClick("precipitation") },
                background = pillBackground,
                ink = pillInk
            )
        }

        // UV Index pill (only shown if UV > 0)
        if (weather.uvIndex > 0) {
            WeatherPill(
                icon = Icons.Outlined.WbSunny,
                text = "UV ${weather.uvIndex.toInt()}",
                onClick = { onPillClick("uv") },
                background = pillBackground,
                ink = pillInk
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
    onPillClick: (String) -> Unit = {},
    onSheet: Boolean = false,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    WeatherConditionsRow(
        weather = weather,
        modifier = modifier,
        onPillClick = onPillClick,
        onSheet = onSheet,
        temperatureUnit = temperatureUnit
    )
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
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.1f), // 10% white per PWA
    ink: Color = com.runwear.app.ui.theme.PhotoInk
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
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
                tint = ink.copy(alpha = 0.8f), // 0.8 opacity per PWA
                modifier = Modifier.size(14.dp)        // 14dp per PWA
            )
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 13.sp,                  // 13px per PWA
                    fontWeight = FontWeight.Medium,
                    color = ink
                ),
                // Belt-and-braces with the FlowRow above: a pill label
                // never wraps — worst case in a pathologically narrow
                // hero it clips, it never stacks character-per-line.
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
