package com.runwear.shared.domain.model

import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroFrameKeyingTest {

    private fun rainWeather(feelsLikeF: Double): WeatherConditions = WeatherConditions(
        dateTime = LocalDateTime(2026, 11, 5, 7, 0),
        temperature = 40.0,
        feelsLike = feelsLikeF,
        humidity = 80,
        windSpeed = 11.0,
        windGusts = 15.0,
        precipitationProbability = 80,
        precipitation = 0.5,
        isRaining = true,
        isSnowing = false,
        cloudCover = 90,
        uvIndex = 0.0,
        weatherCode = WeatherCode.RAIN_MODERATE,
        isCelsius = false
    )

    @Test
    fun `mirror Juneau - bracket keys off comfort-adjusted frame not raw feels-like`() {
        val weather = rainWeather(37.0)
        val outfit = GetOutfitRecommendationUseCase().execute(weather, ComfortPreference.RUNS_COLD)
        val combination = OutfitCombination.fromConditions(
            weather, outfit, GenderPreference.MALE,
            frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(weather, ComfortPreference.RUNS_COLD)
        )
        assertEquals(TempBracket.COLD, combination.tempBracket)
    }

    @Test
    fun `Juneau direction - raw 32 plus runs warm keys COOL with the Juneau prescription`() {
        val weather = rainWeather(32.0)
        val outfit = GetOutfitRecommendationUseCase().execute(weather, ComfortPreference.RUNS_WARM)
        assertEquals(ClothingItem.BASEBALL_CAP, outfit.head)
        assertEquals(ClothingItem.LIGHT_GLOVES, outfit.hands)
        assertEquals(ClothingItem.LIGHT_TIGHTS, outfit.bottom)
        assertEquals(ClothingItem.RAIN_JACKET, outfit.topOuter)
        assertEquals(ClothingItem.LONG_SLEEVE_LIGHT, outfit.topBase)
        val combination = OutfitCombination.fromConditions(
            weather, outfit, GenderPreference.MALE,
            frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(weather, ComfortPreference.RUNS_WARM)
        )
        assertEquals(TempBracket.COOL, combination.tempBracket)
    }
}
