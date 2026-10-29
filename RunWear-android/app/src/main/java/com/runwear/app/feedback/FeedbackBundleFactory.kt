package com.runwear.app.feedback

import com.runwear.app.BuildConfig
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

/**
 * Assembles the diagnostic bundle for a feedback report (spec §1.3) from the
 * state the app actually holds. Two renderings of the same facts:
 *
 * - [displayFields] — plain labeled lines the user reads in the expandable
 *   "What we're sending with this" section before they send.
 * - [json] — the structured bundle that rides with the report.
 *
 * Derived values only: no raw location coordinates (the city label the app
 * already shows is the finest location included), no health data, nothing
 * the user did not already see on their own screen.
 */
object FeedbackBundleFactory {

    data class Bundle(
        val displayFields: List<Pair<String, String>>,
        val json: JSONObject
    )

    fun build(
        state: MainUiState,
        sessionItemChanges: List<String>,
        swappedItems: List<String>,
        removedItems: List<String>
    ): Bundle {
        val weather = state.weather
        val outfit = state.outfit
        val unitSymbol = if (weather?.isCelsius == true) "°C" else "°F"

        val fitLabel = when (state.genderPreference) {
            GenderPreference.MALE -> "Male"
            GenderPreference.FEMALE -> "Female"
            GenderPreference.UNISEX -> "Not set"
        }

        val frameLabel = if (state.isNowSelected) "Now" else "A time I picked"
        val localMoment = state.selectedDateTime.format(
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
        )

        val fields = mutableListOf<Pair<String, String>>()
        fields += "App version" to BuildConfig.VERSION_NAME
        fields += "Outfit engine" to "v${GetOutfitRecommendationUseCase.ENGINE_VERSION}"
        fields += "City" to state.locationName
        if (weather != null) {
            fields += "Feels like" to "${weather.feelsLike.toInt()}$unitSymbol"
            fields += "Actual temperature" to "${weather.temperature.toInt()}$unitSymbol"
            fields += "Conditions" to weather.weatherCode.description
            fields += "Humidity" to "${weather.humidity}%"
            fields += "Wind" to "${weather.windSpeed.toInt()} (gusts ${weather.windGusts.toInt()})"
            fields += "Chance of precipitation" to "${weather.precipitationProbability}%"
            fields += "UV index" to weather.uvIndex.toInt().toString()
        }
        fields += "Time shown" to "$frameLabel — $localMoment"
        if (outfit != null) {
            fields += "Base layer" to outfit.topBase.displayName
            outfit.topMid?.let { fields += "Mid layer" to it.displayName }
            outfit.topOuter?.let { fields += "Outer layer" to it.displayName }
            fields += "Bottom" to outfit.bottom.displayName
            outfit.head?.let { fields += "Head" to it.displayName }
            outfit.hands?.let { fields += "Hands" to it.displayName }
            if (outfit.accessories.isNotEmpty()) {
                fields += "Accessories" to outfit.accessories.joinToString(", ") { it.displayName }
            }
            fields += "Tips shown" to outfit.tips.size.toString()
        }
        fields += "Comfort preference" to state.comfortPreference.shortLabel
        fields += "Fit preference" to fitLabel
        if (sessionItemChanges.isNotEmpty()) {
            fields += "Changes this session" to sessionItemChanges.joinToString("; ")
        }

        // The wire bundle: exactly the intake's runwear-feedback/1
        // allowlist (feedback-intake/src/schemas.js) — flat scalars and
        // string arrays only, no nested objects, no unlisted keys, or
        // the Worker rejects the whole report with 422.
        val json = JSONObject().apply {
            put("engineVersion", GetOutfitRecommendationUseCase.ENGINE_VERSION)
            put("cityLabel", state.locationName.take(64))
            if (weather != null) {
                put("effectiveTemp", weather.effectiveTemperature)
                put("tempUnits", if (weather.isCelsius) "C" else "F")
                put("feelsLike", weather.feelsLike)
                put("humidity", weather.humidity)
                put("windSpeed", weather.windSpeed)
                put("windGusts", weather.windGusts)
                put("precipProbability", weather.precipitationProbability)
                put("weatherDescription", weather.weatherCode.description.take(64))
                put("uvIndex", weather.uvIndex)
            }
            put("timeFrame", if (state.isNowSelected) "now" else "explicit")
            put("localDate", state.selectedDateTime.toLocalDate().toString())
            put("localHour", state.selectedDateTime.hour)
            if (outfit != null) {
                put("outfitItems", JSONArray().apply {
                    outfit.allItems.take(16).forEach { put(it.displayName.take(64)) }
                })
                put("tipsShownCount", outfit.tips.size)
            }
            if (swappedItems.isNotEmpty()) {
                put("swappedItems", JSONArray().apply {
                    swappedItems.take(16).forEach { put(it.take(64)) }
                })
            }
            if (removedItems.isNotEmpty()) {
                put("removedItems", JSONArray().apply {
                    removedItems.take(16).forEach { put(it.take(64)) }
                })
            }
            put("comfortPreference", state.comfortPreference.shortLabel)
            put("fitPreference", fitLabel)
        }

        return Bundle(displayFields = fields, json = json)
    }
}
