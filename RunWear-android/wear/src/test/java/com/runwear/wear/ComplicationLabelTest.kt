package com.runwear.wear

import com.runwear.shared.domain.model.WeatherCode
import com.runwear.wear.complications.shortConditionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranged-value complication's title is a deliberate short label
 * (Sol, Oct 30, 2026: 4-letter-class forms are fine — "CLEA RAIN SNOW
 * etc"). Every weather code must map to a single short line the watch
 * face can truncate cleanly, never a multi-word phrase it cuts mid-word.
 */
class ComplicationLabelTest {

    @Test
    fun everyCodeMapsToAShortLabel() {
        for (code in WeatherCode.entries) {
            val label = shortConditionLabel(code)
            assertTrue(
                "$code maps to '$label' (${label.length} chars) — too long for a small slot",
                label.length <= 8
            )
            assertTrue(
                "$code maps to '$label' — labels are one or two words at most",
                label.count { it == ' ' } <= 1
            )
        }
    }

    @Test
    fun knownLabels() {
        assertEquals("Clear", shortConditionLabel(WeatherCode.CLEAR_SKY))
        assertEquals("Clear", shortConditionLabel(WeatherCode.MAINLY_CLEAR))
        assertEquals("Cloudy", shortConditionLabel(WeatherCode.PARTLY_CLOUDY))
        assertEquals("Overcast", shortConditionLabel(WeatherCode.OVERCAST))
        assertEquals("Fog", shortConditionLabel(WeatherCode.FOG))
        assertEquals("Rain", shortConditionLabel(WeatherCode.RAIN_HEAVY))
        assertEquals("Rain", shortConditionLabel(WeatherCode.RAIN_SHOWERS_VIOLENT))
        assertEquals("Snow", shortConditionLabel(WeatherCode.SNOW_HEAVY))
        assertEquals("Storm", shortConditionLabel(WeatherCode.THUNDERSTORM_HAIL_HEAVY))
        assertEquals("Drizzle", shortConditionLabel(WeatherCode.DRIZZLE_DENSE))
    }
}
