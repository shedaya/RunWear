package com.runwear.app.ui.components

import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import java.time.LocalDate
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-4 day-chart model: bands, change ticks, low/high, horizon.
 * Sample day shape follows the mock's Kansas City day (cool morning,
 * mild midday peak, cool evening).
 */
class DayChartModelTest {

    private val date: LocalDate = LocalDate.of(2026, 11, 4)

    private fun hourly(hour: Int, feels: Double, day: Int = 4) = HourlyForecast(
        dateTime = KotlinLocalDateTime(2026, 11, day, hour, 0),
        temperature = feels + 5,
        feelsLike = feels,
        humidity = 40,
        windSpeed = 5.0,
        precipitationProbability = 0,
        weatherCode = WeatherCode.CLEAR_SKY,
        uvIndex = 1.0
    )

    private fun sampleDay(): List<HourlyForecast> {
        // 42° low at 6 AM, 53° high at 3-4 PM (the mock's anchors).
        val feels = listOf(
            45.0, 45.0, 44.0, 44.0, 44.0, 43.0, 42.0, 42.0, 43.0, 44.0,
            46.0, 47.0, 48.0, 50.0, 51.0, 53.0, 53.0, 49.0, 47.0, 46.0,
            46.0, 45.0, 45.0, 44.0
        )
        return feels.mapIndexed { h, f -> hourly(h, f) }
    }

    @Test
    fun `bands follow the prescription brackets across the day`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.NEUTRAL)
        // COOL (35-49) until noon, MILD (50-64) 1 PM-4 PM, COOL after.
        assertEquals(
            listOf(
                DayChartBand(0, 13, TempBracket.COOL),
                DayChartBand(13, 17, TempBracket.MILD),
                DayChartBand(17, 24, TempBracket.COOL)
            ),
            model.bands
        )
        assertTrue(model.hours.all { it.bracket == TempBracket.fromFahrenheit(it.feelsLikeF) })
    }

    @Test
    fun `change ticks land exactly where the bracket changes`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.NEUTRAL)
        // The mock's ticks: 1 PM (COOL to MILD) and 5 PM (MILD to COOL).
        assertTrue("change ticks were ${model.changeHours}", 13 in model.changeHours)
        assertTrue("change ticks were ${model.changeHours}", 17 in model.changeHours)
        // No tick at an hour whose bracket and outfit match its predecessor.
        assertFalse(14 in model.changeHours && model.point(14)?.bracket == model.point(13)?.bracket &&
            model.point(14)?.outfitSignature == model.point(13)?.outfitSignature)
    }

    @Test
    fun `low and high carry their values and hours`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.NEUTRAL)
        assertEquals(42.0, model.low!!.feelsLikeF, 0.001)
        assertEquals(6, model.low!!.hour)
        assertEquals(53.0, model.high!!.feelsLikeF, 0.001)
        assertEquals(15, model.high!!.hour) // first hour at the peak
    }

    @Test
    fun `comfort adjustment moves the plotted frame - RW-23`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.RUNS_VERY_WARM)
        // +10 adjustment: the 42° low plots at 52° and brackets shift.
        assertEquals(52.0, model.low!!.feelsLikeF, 0.001)
        assertEquals(TempBracket.MILD, model.low!!.bracket)
    }

    @Test
    fun `horizon cutoff - hours beyond the forecast are absent, never invented`() {
        val partial = (0..14).map { hourly(it, 40.0 + it) }
        val model = DayChartBuilder.build(partial, date, ComfortPreference.NEUTRAL)
        assertEquals(15, model.hours.size)
        assertEquals(14, model.horizonLastHour)
        assertEquals((0..14).toSet(), model.selectableHours)
        assertNull(model.point(15))
        assertNull(model.point(23))
    }

    @Test
    fun `a day with no forecast is an empty model`() {
        val model = DayChartBuilder.build(sampleDay(), date.plusDays(1), ComfortPreference.NEUTRAL)
        assertTrue(model.isEmpty)
        assertNull(model.low)
        assertNull(model.high)
        assertNull(model.horizonLastHour)
        assertTrue(model.bands.isEmpty())
    }

    @Test
    fun `other days in the forecast do not leak into the model`() {
        val twoDays = sampleDay() + (0..23).map { hourly(it, 60.0, day = 5) }
        val model = DayChartBuilder.build(twoDays, date, ComfortPreference.NEUTRAL)
        assertEquals(24, model.hours.size)
        assertEquals(53.0, model.high!!.feelsLikeF, 0.001)
    }

    @Test
    fun `geometry snaps x to the nearest selectable hour and never past the horizon`() {
        val selectable = (0..14).toSet()
        assertEquals(0, DayChartGeometry.hourFromXFraction(0.0f, selectable))
        assertEquals(14, DayChartGeometry.hourFromXFraction(1.0f, selectable))
        assertEquals(6, DayChartGeometry.hourFromXFraction(6.5f / 24f, selectable))
        assertNull(DayChartGeometry.hourFromXFraction(0.5f, emptySet()))
    }

    @Test
    fun `readout is hour and feels-like only`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.NEUTRAL)
        val p = model.point(18)!!
        assertEquals("6 PM · 47°", DayChartFormat.readout(p.hour, p.feelsLikeF, TemperatureUnit.FAHRENHEIT))
        assertEquals("6:00 PM", DayChartFormat.axisLabel(18))
        assertEquals("12 PM", DayChartFormat.hourLabel(12))
        assertEquals("12 AM", DayChartFormat.hourLabel(0))
    }

    @Test
    fun `every hour carries an engine outfit preview`() {
        val model = DayChartBuilder.build(sampleDay(), date, ComfortPreference.NEUTRAL)
        assertTrue(model.hours.all { it.outfitItems.isNotEmpty() })
        // The cold dawn and the mild peak prescribe differently.
        assertTrue(model.point(6)!!.outfitSignature != model.point(15)!!.outfitSignature)
    }
}
