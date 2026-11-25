package com.runwear.wear.presentation

import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions

/**
 * GEN-6 WAVE C (DA-13) — the ONE temperature frame for every Wear
 * surface (hero, tile, complications), matching the phone's RW-23
 * rule: the headline figure is the comfort-adjusted FEELS-LIKE —
 * the number the outfit is priced off — and the ACTUAL temperature
 * ([WeatherConditions.temperature]) is the subordinate line.
 *
 * Note on the gen-5 defect's shape: `effectiveTemperature` is a
 * getter for the raw feels-like, so the gen-5 hero/complications
 * headlined the RAW feels-like while labelling the same value
 * "Feels like" underneath, and the tile's "actual" line printed
 * that same value again — the actual temperature appeared nowhere.
 * This frame is the single seam all three surfaces now read.
 */
object WearTempFrame {

    /** Headline: comfort-adjusted feels-like, canonical °F. */
    fun headlineF(weather: WeatherConditions, comfort: ComfortPreference): Double =
        weather.feelsLike + comfort.tempAdjustment

    /** Subordinate: the actual temperature, canonical °F. */
    fun actualF(weather: WeatherConditions): Double = weather.temperature

    fun headlineText(
        weather: WeatherConditions,
        comfort: ComfortPreference,
        unit: TemperatureUnit
    ): String = unit.formatTemperature(headlineF(weather, comfort))

    /** The subordinate line: "Actual 62°F". */
    fun actualLine(
        weather: WeatherConditions,
        unit: TemperatureUnit
    ): String = "Actual ${unit.formatTemperature(actualF(weather))}"
}
