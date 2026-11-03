package com.runwear.app.ui.viewmodel

import com.runwear.app.ui.components.HeroSheetFormat
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-2 (RC-2, D-05) — THE FLIP TEST.
 *
 * A unit change is a state copy (`temperatureUnit = …`) and nothing
 * else: the canonical weather object is untouched (same instance),
 * nothing refetches, and every figure the surfaces project from the
 * state is computed through the ONE unit in that state. This test
 * flips the unit repeatedly, at every data state the home screen can
 * be in (loading / loaded / error-with-retained-data / stale), and
 * asserts that after every single flip the full figure set is
 * consistent with the state's unit — a mixed-unit frame (the D-05
 * "6° beside Actual 45°" frame) is unrepresentable in this model.
 *
 * The figures are produced by the very functions the production
 * composables call: [HeroSheetFormat] for the sheet (numeral,
 * caption, wind pill) and [TemperatureUnit]'s projections for the
 * detail sheet's rows.
 */
class UnitFlipConsistencyTest {

    /**
     * Canonical weather (WS-2): 45°F actual, 41°F feels-like, 10 mph
     * wind — chosen so the °F and °C projections differ in EVERY
     * figure (41°F→41°/5°, 45°F→45°/7°, 10 mph→"10 mph"/"16 km/h"),
     * which is what makes a mixed frame detectable at all.
     */
    private fun canonicalWeather(day: Int = 3) = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 11, day, 7, 0),
        temperature = 45.0,
        feelsLike = 41.0,
        humidity = 40,
        windSpeed = 10.0,
        windGusts = 15.0,
        windDirection = 180,
        precipitationProbability = 5,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    /** Every unit-bearing figure the home surfaces render for a state. */
    private fun figures(state: MainUiState): List<String> {
        val weather = state.weather ?: return emptyList()
        val unit = state.temperatureUnit
        return listOf(
            HeroSheetFormat.heroNumeral(weather, unit),
            HeroSheetFormat.caption(weather, unit),
            HeroSheetFormat.windText(weather, unit),
            // Weather-detail sheet rows (BottomSheets calls these same projections).
            unit.formatTemperature(weather.temperature),
            unit.formatTemperature(weather.feelsLike),
            unit.formatWind(weather.windSpeed),
            unit.formatWind(weather.windGusts)
        )
    }

    /** The same figure set computed under the OTHER unit — the mixed frame's ingredients. */
    private fun otherUnitFigures(state: MainUiState): List<String> =
        figures(state.copy(temperatureUnit = other(state.temperatureUnit)))

    private fun other(unit: TemperatureUnit): TemperatureUnit = when (unit) {
        TemperatureUnit.FAHRENHEIT -> TemperatureUnit.CELSIUS
        TemperatureUnit.CELSIUS -> TemperatureUnit.FAHRENHEIT
    }

    private fun assertConsistent(state: MainUiState, label: String) {
        val mine = figures(state)
        val foreign = otherUnitFigures(state)
        if (state.weather == null) {
            assertTrue("$label: no weather, no figures — nothing can contradict", mine.isEmpty())
            return
        }
        assertTrue("$label: figure set must be non-empty", mine.isNotEmpty())
        for ((index, figure) in mine.withIndex()) {
            assertTrue(
                "$label: figure[$index] '$figure' belongs to the OTHER unit " +
                    "(mixed-unit frame) at unit ${state.temperatureUnit}",
                figure != foreign[index]
            )
        }
    }

    @Test
    fun `repeated flips end on one consistent unit at every data state`() {
        val states = mapOf(
            "loading" to MainUiState(isLoading = true, weather = null),
            "loaded" to MainUiState(isLoading = false, weather = canonicalWeather()),
            "error with retained data" to MainUiState(
                isLoading = false, error = "Weather error", weather = canonicalWeather()
            ),
            // Stale: the same canonical object, days old — a flip must
            // still project it whole, in the new unit, with no refetch.
            "stale" to MainUiState(isLoading = false, weather = canonicalWeather(day = 1))
        )

        for ((label, initial) in states) {
            // Start from BOTH units; flip six times (lands back at start).
            for (start in TemperatureUnit.entries) {
                var state = initial.copy(temperatureUnit = start)
                assertConsistent(state, "$label/start=$start")
                repeat(6) { flip ->
                    val weatherBefore = state.weather
                    val forecastBefore = state.hourlyForecast
                    state = state.copy(temperatureUnit = other(state.temperatureUnit))
                    // A flip rewrites NOTHING but the projection input:
                    // same canonical weather instance, same forecast.
                    assertSame(
                        "$label flip $flip: the weather object must be untouched by a flip",
                        weatherBefore, state.weather
                    )
                    assertSame(
                        "$label flip $flip: the forecast must be untouched by a flip",
                        forecastBefore, state.hourlyForecast
                    )
                    assertConsistent(state, "$label/start=$start/flip=${flip + 1}")
                }
                assertEquals(
                    "$label: six flips return to the starting unit",
                    start, state.temperatureUnit
                )
            }
        }
    }

    @Test
    fun `new data landing mid-flip re-targets as one value`() {
        // A flip is in progress (state now °C) when a fresh canonical
        // fetch lands: the write carries weather AND keeps the unit in
        // one state transaction, so the new figures are °C whole —
        // never the new data in the old unit beside old data in the new.
        var state = MainUiState(isLoading = false, weather = canonicalWeather(day = 3))
        state = state.copy(temperatureUnit = TemperatureUnit.CELSIUS)
        val landed = canonicalWeather(day = 3).copy(
            temperature = 50.0, feelsLike = 48.0, windSpeed = 20.0, windGusts = 25.0
        )
        state = state.copy(weather = landed, isLoading = false)
        assertEquals(TemperatureUnit.CELSIUS, state.temperatureUnit)
        assertEquals(
            listOf("8°", "Actual 10°", "32 km/h", "10°C", "8°C", "32 km/h", "40 km/h"),
            figures(state)
        )
        assertConsistent(state, "mid-flip landing")
    }

    @Test
    fun `the fahrenheit figure set for the field values`() {
        // The D-05 recording's settled imperial frame, as a pin:
        // numeral 41°, Actual 45°, wind 10 mph — from canonical data.
        val state = MainUiState(weather = canonicalWeather())
        assertEquals(
            listOf("41°", "Actual 45°", "10 mph", "45°F", "41°F", "10 mph", "15 mph"),
            figures(state)
        )
    }
}
