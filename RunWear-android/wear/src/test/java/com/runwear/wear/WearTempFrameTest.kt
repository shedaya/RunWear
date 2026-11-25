package com.runwear.wear

import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.wear.presentation.WearTempFrame
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * GEN-6 WAVE C (DA-13): the Wear temperature frame — the headline
 * is the comfort-adjusted FEELS-LIKE; the actual temperature is
 * the subordinate line only. Gen-5 rendered effectiveTemperature
 * (the actual) as the hero numeral AND as the "feels like" line,
 * so the actual was the only number on the watch.
 */
class WearTempFrameTest {

    private fun weather(actual: Double, feels: Double) = WeatherConditions(
        dateTime = LocalDateTime(2026, 11, 5, 7, 0),
        temperature = actual,
        feelsLike = feels,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 10,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 20,
        uvIndex = 2.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    @Test
    fun `headline is the comfort-adjusted feels-like, not the actual`() {
        val w = weather(actual = 50.0, feels = 44.0)
        assertEquals(44.0, WearTempFrame.headlineF(w, ComfortPreference.NEUTRAL), 0.001)
        assertEquals(39.0, WearTempFrame.headlineF(w, ComfortPreference.RUNS_COLD), 0.001)
        assertEquals(54.0, WearTempFrame.headlineF(w, ComfortPreference.RUNS_VERY_WARM), 0.001)
    }

    @Test
    fun `actual line carries the actual temperature`() {
        val w = weather(actual = 50.0, feels = 44.0)
        assertEquals(50.0, WearTempFrame.actualF(w), 0.001)
        assertEquals("Actual 50°F", WearTempFrame.actualLine(w, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `headline and actual differ whenever feels-like differs from actual`() {
        val w = weather(actual = 61.0, feels = 57.0)
        assertEquals("57°F", WearTempFrame.headlineText(w, ComfortPreference.NEUTRAL, TemperatureUnit.FAHRENHEIT))
        assertFalse(WearTempFrame.headlineText(w, ComfortPreference.NEUTRAL, TemperatureUnit.FAHRENHEIT) ==
            WearTempFrame.actualLine(w, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `hero source has no numeral count-up tween - DA-13`() {
        val source = java.io.File(
            "src/main/java/com/runwear/wear/presentation/screens/HeroWearMainScreen.kt"
        ).takeIf { it.exists() } ?: java.io.File(
            "wear/src/main/java/com/runwear/wear/presentation/screens/HeroWearMainScreen.kt"
        )
        val text = source.readText()
        assertFalse(
            "the hero numeral is a readout — no animateIntAsState tween",
            text.contains("animateIntAsState")
        )
        assertFalse(text.contains("displayedTemp"))
    }
}
