package com.runwear.app.ui.components

import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-25: the sheet's eyebrow and caption composition — pure format
 * rules, including the moment label in DateTimePill's human formats.
 */
class HeroSheetFormatTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 2, 7, 0) // a Monday

    private fun weather(
        temperature: Double,
        feelsLike: Double,
        humidity: Int,
        windSpeed: Double
    ) = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 11, 2, 7, 0),
        temperature = temperature,
        feelsLike = feelsLike,
        humidity = humidity,
        windSpeed = windSpeed,
        windGusts = 7.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    @Test
    fun `eyebrow reads NOW while the now state is selected`() {
        assertEquals(
            "FEELS LIKE · NEW YORK · NOW",
            HeroSheetFormat.eyebrow(
                locationName = "New York",
                selectedDateTime = now,
                locationNow = now,
                isNowSelected = true
            )
        )
    }

    @Test
    fun `eyebrow names tomorrow in the pill's human format`() {
        assertEquals(
            "FEELS LIKE · NEW YORK · TOMORROW, 7:00 AM",
            HeroSheetFormat.eyebrow(
                locationName = "New York",
                selectedDateTime = LocalDateTime.of(2026, 11, 3, 7, 0),
                locationNow = now,
                isNowSelected = false
            )
        )
    }

    @Test
    fun `eyebrow names a later date in the pill's human format`() {
        // Fri Nov 6, 2026
        assertEquals(
            "FEELS LIKE · NEW YORK · FRI, NOV 6, 7:00 AM",
            HeroSheetFormat.eyebrow(
                locationName = "New York",
                selectedDateTime = LocalDateTime.of(2026, 11, 6, 7, 0),
                locationNow = now,
                isNowSelected = false
            )
        )
    }

    @Test
    fun `caption carries the actual temperature only (RW-26 section 7 amendment 1)`() {
        // WS-2: the stored value is canonical °F (42°F); the caption
        // projects it through the render-time unit — the SAME stored
        // weather reads 42° under °F and 5° under °C (42°F = 5.55°C,
        // truncated by the codebase's display convention).
        assertEquals(
            "Actual 42°",
            HeroSheetFormat.caption(
                weather(temperature = 42.0, feelsLike = 33.0, humidity = 31, windSpeed = 5.0),
                com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
            )
        )
        assertEquals(
            "Actual 5°",
            HeroSheetFormat.caption(
                weather(temperature = 42.0, feelsLike = 33.0, humidity = 31, windSpeed = 5.0),
                com.runwear.shared.domain.model.TemperatureUnit.CELSIUS
            )
        )
    }

    @Test
    fun `location line is place dot moment in sentence case`() {
        assertEquals(
            "New York · Now",
            HeroSheetFormat.locationLine("New York", now, now, isNowSelected = true)
        )
        assertEquals(
            "New York · Tomorrow, 7:00 AM",
            HeroSheetFormat.locationLine(
                "New York", LocalDateTime.of(2026, 11, 3, 7, 0), now, isNowSelected = false
            )
        )
        assertEquals("Feels like", HeroSheetFormat.FEELS_LIKE_LABEL)
    }

    @Test
    fun `location line renders the simple place - a raw geocode string cannot reach it`() {
        // WS-4 (D-08): even if state handed the header the raw
        // reverse-geocoded string, the rendered line is simple.
        assertEquals(
            "New York, NY · Now",
            HeroSheetFormat.locationLine("New York, New York", now, now, isNowSelected = true)
        )
        assertEquals(
            "Winston-Salem, NC · Now",
            HeroSheetFormat.locationLine("Winston-Salem, North Carolina", now, now, isNowSelected = true)
        )
        assertEquals(
            "Truth or Consequences, NM · Now",
            HeroSheetFormat.locationLine("Truth or Consequences, New Mexico", now, now, isNowSelected = true)
        )
        // Already-simple input is idempotent.
        assertEquals(
            "New York, NY · Now",
            HeroSheetFormat.locationLine("New York, NY", now, now, isNowSelected = true)
        )
        assertEquals(
            "FEELS LIKE · NEW YORK, NY · NOW",
            HeroSheetFormat.eyebrow("New York, New York", now, now, isNowSelected = true)
        )
    }
}

/**
 * RW-25: the scroll handoff's pure progress mapping — endpoints,
 * travel fraction, and clamping.
 */
class HeroScrollHandoffTest {

    @Test
    fun `progress is zero at rest and one at the travel distance`() {
        assertEquals(0f, HeroScrollHandoff.progress(0f, 1000f), 0.001f)
        assertEquals(1f, HeroScrollHandoff.progress(550f, 1000f), 0.001f)
        assertEquals(0.5f, HeroScrollHandoff.progress(275f, 1000f), 0.001f)
    }

    @Test
    fun `progress clamps beyond the travel distance and for a missing hero height`() {
        assertEquals(1f, HeroScrollHandoff.progress(5000f, 1000f), 0.001f)
        assertEquals(0f, HeroScrollHandoff.progress(100f, 0f), 0.001f)
    }

    @Test
    fun `endpoints of the settle transform`() {
        assertEquals(1f, HeroScrollHandoff.scale(0f), 0.001f)
        assertEquals(0.94f, HeroScrollHandoff.scale(1f), 0.001f)
        assertEquals(0, HeroScrollHandoff.horizontalInset(0f).value.toInt())
        assertEquals(16, HeroScrollHandoff.horizontalInset(1f).value.toInt())
        assertEquals(0, HeroScrollHandoff.cornerRadius(0f).value.toInt())
        assertEquals(24, HeroScrollHandoff.cornerRadius(1f).value.toInt())
    }
}
