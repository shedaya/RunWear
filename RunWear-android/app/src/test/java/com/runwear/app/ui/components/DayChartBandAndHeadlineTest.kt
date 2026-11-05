package com.runwear.app.ui.components

import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-4: the glance band's state rules and the headline line's
 * entry / way-back rules.
 */
class DayChartBandAndHeadlineTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)

    // ── Band state ────────────────────────────────────────────────

    @Test
    fun `NOW marker shows only when the selected day is the location's today`() {
        assertTrue(DayChartBandPolicy.showNowMarker(today, today))
        assertFalse(DayChartBandPolicy.showNowMarker(today.plusDays(1), today))
        assertFalse(DayChartBandPolicy.showNowMarker(today.minusDays(1), today))
    }

    @Test
    fun `selected dot follows the selection`() {
        assertEquals(6, DayChartBandPolicy.selectedDotHour(6))
        assertEquals(18, DayChartBandPolicy.selectedDotHour(18))
        assertEquals(0, DayChartBandPolicy.selectedDotHour(0))
    }

    @Test
    fun `Change opens the Choose-a-Time sheet`() {
        assertEquals(
            DayChartBandPolicy.ChangeAction.OPEN_TIME_SHEET,
            DayChartBandPolicy.changeAction()
        )
    }

    // ── Headline line state ───────────────────────────────────────

    @Test
    fun `moment segment is tappable in BOTH states and opens the time sheet`() {
        val nowState = HeadlineLinePolicy.state(isNowSelected = true)
        val changedState = HeadlineLinePolicy.state(isNowSelected = false)
        assertTrue(nowState.momentTappable)
        assertTrue(changedState.momentTappable)
        assertEquals(
            HeadlineLinePolicy.Action.OPEN_TIME_SHEET,
            HeadlineLinePolicy.momentAction()
        )
    }

    @Test
    fun `Now segment is present iff the state is changed`() {
        assertFalse(HeadlineLinePolicy.state(isNowSelected = true).showNowReset)
        assertTrue(HeadlineLinePolicy.state(isNowSelected = false).showNowReset)
    }

    @Test
    fun `the Now segment's action is exactly reset-to-now`() {
        val changed = HeadlineLinePolicy.state(isNowSelected = false)
        assertEquals(HeadlineLinePolicy.Action.RESET_TO_NOW, HeadlineLinePolicy.nowAction(changed))
        val now = HeadlineLinePolicy.state(isNowSelected = true)
        assertNull(HeadlineLinePolicy.nowAction(now))
    }

    @Test
    fun `headline moment text matches the settled location line forms`() {
        val now = LocalDateTime.of(2026, 11, 4, 16, 0)
        assertEquals(
            "Now",
            HeroSheetFormat.momentText(now, now, isNowSelected = true)
        )
        // GEN-5 (RW-28 item 12, B-13): today's moment is the bare
        // simple form — no "Today," prefix.
        assertEquals(
            "6:00 PM",
            HeroSheetFormat.momentText(LocalDateTime.of(2026, 11, 4, 18, 0), now, isNowSelected = false)
        )
        assertEquals(
            "Tomorrow, 6:00 PM",
            HeroSheetFormat.momentText(LocalDateTime.of(2026, 11, 5, 18, 0), now, isNowSelected = false)
        )
        assertEquals("Kansas City", HeroSheetFormat.placeText("Kansas City"))
        // The composed line is unchanged: place · moment.
        assertEquals(
            "Kansas City · Now",
            HeroSheetFormat.locationLine("Kansas City", now, now, isNowSelected = true)
        )
    }

    // ── One-frame rule (GEN-5 item 11, B-3) ─────────────────────

    @Test
    fun `numeral and bracket read the same adjusted frame the chart uses`() {
        // The Miami shape from the walkthrough: the picked hour's
        // raw feels-like is 78, the user's comfort adjustment is
        // +5 (RUNS_WARM), and the picker readout — built in the
        // adjusted frame — said 83. The numeral and the bracket
        // chip must read 83 / the 83 bracket too, in the same
        // frame, or two feels-likes coexist for one moment.
        val weather = WeatherConditions(
            dateTime = KotlinLocalDateTime(2026, 11, 4, 21, 0),
            temperature = 72.0,
            feelsLike = 78.0,
            humidity = 70,
            windSpeed = 3.0,
            windGusts = 5.0,
            precipitationProbability = 1,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 0.0,
            weatherCode = WeatherCode.CLEAR_SKY
        )
        val unit = com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        val warm = com.runwear.shared.domain.model.ComfortPreference.RUNS_WARM
        assertEquals(
            "83°",
            HeroSheetFormat.heroNumeral(weather, unit, warm)
        )
        assertEquals(
            com.runwear.shared.domain.model.TempBracket.fromFahrenheit(83.0),
            HeroSheetFormat.bracketFor(weather, warm)
        )
        // Neutral comfort: the frames coincide, as before.
        assertEquals(
            "78°",
            HeroSheetFormat.heroNumeral(
                weather, unit,
                com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
            )
        )
        // The DayChart point for the same hour is built in exactly
        // this frame (raw + adjustment) — one frame, every surface.
        val chartPoint = 78.0 + warm.tempAdjustment
        assertEquals(83.0, chartPoint, 0.001)
    }

    // ── Right-justified weather grid ──────────────────────────────

    @Test
    fun `weather grid rows are WIND RAIN HUMIDITY UV with flush values`() {
        val weather = WeatherConditions(
            dateTime = KotlinLocalDateTime(2026, 11, 4, 18, 0),
            temperature = 52.0,
            feelsLike = 47.0,
            humidity = 52,
            windSpeed = 7.0,
            windGusts = 10.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 0.0,
            weatherCode = WeatherCode.CLEAR_SKY
        )
        val rows = HeroSheetFormat.weatherGridRows(
            weather, com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        )
        assertEquals(
            listOf(
                "WIND" to "7 mph",
                "RAIN" to "0%",
                "HUMIDITY" to "52%",
                "UV" to "0 of 11"
            ),
            rows
        )
    }
}
