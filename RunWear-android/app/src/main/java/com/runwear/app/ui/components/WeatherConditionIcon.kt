package com.runwear.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.runwear.shared.domain.model.WeatherCode

/**
 * WS-10 (A4 residue, per R-1's corrected test) — the designed
 * iconography for a weather condition: a Material vector, never an
 * emoji. The design's weather strip and weather-detail panel carry
 * the condition as icon + word (frames 1–5, 40/41); the shipped
 * build rendered [WeatherCode.icon] — an emoji string — as the
 * condition pill under the temperature on the main screen (Sol's
 * named A4 instance) and as the detail panel's 40sp glyph. Both
 * surfaces now draw this vector beside the condition's word, like
 * every other pill and detail row in the app.
 *
 * The mapping is by condition CLASS: sun, part-cloud, cloud/fog,
 * drizzle (grain), rain (umbrella), frozen precipitation (acunit),
 * thunder, unknown. [WeatherCode.icon] itself is model data and is
 * untouched — it is simply no longer rendered by any shipped UI
 * surface (its remaining consumer is the disabled debug overlay).
 */
fun WeatherCode.conditionIcon(): ImageVector = when (this) {
    WeatherCode.CLEAR_SKY,
    WeatherCode.MAINLY_CLEAR -> Icons.Outlined.WbSunny

    WeatherCode.PARTLY_CLOUDY -> Icons.Outlined.WbCloudy

    WeatherCode.OVERCAST,
    WeatherCode.FOG,
    WeatherCode.DEPOSITING_RIME_FOG -> Icons.Outlined.Cloud

    WeatherCode.DRIZZLE_LIGHT,
    WeatherCode.DRIZZLE_MODERATE,
    WeatherCode.DRIZZLE_DENSE -> Icons.Outlined.Grain

    WeatherCode.RAIN_SLIGHT,
    WeatherCode.RAIN_MODERATE,
    WeatherCode.RAIN_HEAVY,
    WeatherCode.RAIN_SHOWERS_SLIGHT,
    WeatherCode.RAIN_SHOWERS_MODERATE -> Icons.Outlined.Umbrella

    WeatherCode.FREEZING_DRIZZLE_LIGHT,
    WeatherCode.FREEZING_DRIZZLE_DENSE,
    WeatherCode.FREEZING_RAIN_LIGHT,
    WeatherCode.FREEZING_RAIN_HEAVY,
    WeatherCode.SNOW_SLIGHT,
    WeatherCode.SNOW_MODERATE,
    WeatherCode.SNOW_HEAVY,
    WeatherCode.SNOW_GRAINS,
    WeatherCode.SNOW_SHOWERS_SLIGHT,
    WeatherCode.SNOW_SHOWERS_HEAVY -> Icons.Outlined.AcUnit

    WeatherCode.RAIN_SHOWERS_VIOLENT,
    WeatherCode.THUNDERSTORM,
    WeatherCode.THUNDERSTORM_HAIL_SLIGHT,
    WeatherCode.THUNDERSTORM_HAIL_HEAVY -> Icons.Outlined.Thunderstorm

    WeatherCode.UNKNOWN -> Icons.Outlined.HelpOutline
}
