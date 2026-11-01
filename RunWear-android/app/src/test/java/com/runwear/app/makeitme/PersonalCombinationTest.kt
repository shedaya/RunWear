package com.runwear.app.makeitme

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.OutfitCombination
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The personal combination id must match the backend's keying exactly —
 * these are the backend lane's own verified worked examples
 * (`rw14-backend-build-2026-10-31.md`): the same inputs through the
 * engine's [OutfitCombination.fromConditions] (the same function the
 * client uses) must produce the same ids the backend contracted on.
 */
class PersonalCombinationTest {

    private fun weather(feelsLikeF: Double, code: WeatherCode, hour: Int): WeatherConditions =
        WeatherConditions(
            dateTime = LocalDateTime(2026, 10, 31, hour, 0),
            temperature = feelsLikeF,
            feelsLike = feelsLikeF,
            humidity = 50,
            windSpeed = 5.0,
            windGusts = 8.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            isRaining = code == WeatherCode.RAIN_SLIGHT || code == WeatherCode.RAIN_MODERATE,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 3.0,
            weatherCode = code,
            isCelsius = false
        )

    @Test
    fun `clear mild dawn long sleeve and shorts`() {
        val w = weather(55.0, WeatherCode.CLEAR_SKY, 7)
        val outfit = OutfitRecommendation(
            weather = w,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.SHORTS
        )
        val combination = OutfitCombination.fromConditions(w, outfit, GenderPreference.MALE)
        assertEquals("MALE_CLEAR_MILD_DAWN_726beba2", combination.id)
    }

    @Test
    fun `rain mild dawn with jacket and cap`() {
        val w = weather(55.0, WeatherCode.RAIN_SLIGHT, 7)
        val outfit = OutfitRecommendation(
            weather = w,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            topOuter = ClothingItem.RAIN_JACKET,
            bottom = ClothingItem.SHORTS,
            head = ClothingItem.BASEBALL_CAP
        )
        val combination = OutfitCombination.fromConditions(w, outfit, GenderPreference.MALE)
        assertEquals("MALE_RAIN_MILD_DAWN_9236f3e0", combination.id)
    }

    @Test
    fun `clear warm dawn short sleeve shorts cap`() {
        val w = weather(70.0, WeatherCode.CLEAR_SKY, 7)
        val outfit = OutfitRecommendation(
            weather = w,
            topBase = ClothingItem.SHORT_SLEEVE,
            bottom = ClothingItem.SHORTS,
            head = ClothingItem.BASEBALL_CAP
        )
        val combination = OutfitCombination.fromConditions(w, outfit, GenderPreference.MALE)
        assertEquals("MALE_CLEAR_WARM_DAWN_3c451777", combination.id)
    }
}
