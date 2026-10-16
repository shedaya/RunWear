package com.runwear.app.feedback

import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime

/**
 * Shared fixtures for the telemetry contract tests and the live
 * intake smoke: one realistic screen state (39°F feels-like, calm —
 * the session that produced the famous four-item outfit) plus the
 * swaps/removals it generated.
 */
object TelemetryFixtures {

    val weather = WeatherConditions(
        dateTime = LocalDateTime.of(2026, 10, 17, 7, 0),
        temperature = 41.0,
        feelsLike = 39.0,
        humidity = 62,
        windSpeed = 6.0,
        windGusts = 11.0,
        precipitationProbability = 10,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 20,
        uvIndex = 2.0,
        weatherCode = WeatherCode.MAINLY_CLEAR,
        isCelsius = false
    )

    val outfit = OutfitRecommendation(
        weather = weather,
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        topMid = ClothingItem.QUARTER_ZIP,
        bottom = ClothingItem.LIGHT_TIGHTS,
        head = ClothingItem.HEADBAND,
        hands = ClothingItem.LIGHT_GLOVES,
        accessories = listOf(ClothingItem.REFLECTIVE_GEAR),
        tips = listOf("Dress in layers.", "Watch for wind on the bridge.")
    )

    fun uiState(): MainUiState = MainUiState(
        isLoading = false,
        outfit = outfit,
        weather = weather,
        locationName = "Hong Kong",
        selectedDateTime = LocalDateTime.of(2026, 10, 17, 7, 0),
        isNowSelected = true,
        comfortPreference = ComfortPreference.NEUTRAL,
        genderPreference = GenderPreference.MALE
    )

    val sessionChanges = listOf(
        "Mid layer changed: Fleece Pullover to Quarter-Zip Pullover",
        "Hands removed: Light Gloves"
    )
    val swappedItems = listOf("Quarter-Zip Pullover")
    val removedItems = listOf("Light Gloves")

    fun bundle() = FeedbackBundleFactory.build(
        uiState(),
        sessionChanges,
        swappedItems,
        removedItems
    ).json
}
