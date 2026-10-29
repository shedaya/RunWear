package com.runwear.app.widget

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime

/**
 * The Large widget's "Later today" line (spec §B.3.4): outfit change beats
 * weather trend, stated in plain words with the target hour.
 */
class LaterTodayLineTest {

    private fun weather(
        time: LocalDateTime,
        feelsLike: Double = 45.0,
        precipProb: Int = 0
    ) = WeatherConditions(
        dateTime = time.toKotlinLocalDateTime(),
        temperature = feelsLike + 4,
        feelsLike = feelsLike,
        humidity = 60,
        windSpeed = 8.0,
        windGusts = 12.0,
        windDirection = 180,
        precipitationProbability = precipProb,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 30,
        uvIndex = 2.0,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        isCelsius = false
    )

    private val now = LocalDateTime.of(2026, 10, 22, 15, 0) // 3 PM

    private fun outfit(
        weather: WeatherConditions,
        base: ClothingItem = ClothingItem.LONG_SLEEVE_LIGHT,
        outer: ClothingItem? = ClothingItem.WINDBREAKER,
        bottom: ClothingItem = ClothingItem.LIGHT_TIGHTS
    ) = OutfitRecommendation(weather = weather, topBase = base, topOuter = outer, bottom = bottom)

    @Test
    fun `dropping the shell later is the headline, with rain when likely`() {
        val current = outfit(weather(now))
        val later = outfit(weather(now.plusHours(3)), outer = null)
        val line = LaterTodayLine.deltaLine(
            now.plusHours(3), current, later, weather(now.plusHours(3), precipProb = 40)
        )
        assertEquals("By 6 PM: drop the Windbreaker · rain 40%", line)
    }

    @Test
    fun `rain is omitted below the mention threshold`() {
        val current = outfit(weather(now))
        val later = outfit(weather(now.plusHours(3)), outer = null)
        val line = LaterTodayLine.deltaLine(
            now.plusHours(3), current, later, weather(now.plusHours(3), precipProb = 20)
        )
        assertEquals("By 6 PM: drop the Windbreaker", line)
    }

    @Test
    fun `a swap reads as drop plus add, capped at two clauses`() {
        val current = outfit(weather(now))
        val later = outfit(
            weather(now.plusHours(6)),
            base = ClothingItem.LONG_SLEEVE_THERMAL,
            outer = ClothingItem.INSULATED_JACKET,
            bottom = ClothingItem.THERMAL_TIGHTS
        )
        val clauses = LaterTodayLine.outfitDeltaClauses(current, later)
        assertEquals(2, clauses.size)
        assertEquals("drop the Windbreaker", clauses[0])
        assertEquals("add the Insulated Jacket", clauses[1])
    }

    @Test
    fun `identical outfits produce no delta line`() {
        val current = outfit(weather(now))
        assertTrue(LaterTodayLine.outfitDeltaClauses(current, outfit(weather(now.plusHours(3)))).isEmpty())
        assertNull(
            LaterTodayLine.deltaLine(now.plusHours(3), current, outfit(weather(now.plusHours(3))), weather(now.plusHours(3)))
        )
    }

    @Test
    fun `accessory-only changes are not later-today events`() {
        val w = weather(now)
        val current = outfit(w).copy(accessories = listOf(ClothingItem.SUNGLASSES))
        val later = outfit(w).copy(accessories = emptyList())
        assertTrue(LaterTodayLine.outfitDeltaClauses(current, later).isEmpty())
    }

    @Test
    fun `stable fallback states the feels-like range and peak rain`() {
        fun hour(offset: Long, feels: Double, rain: Int) = HourlyForecast(
            dateTime = now.plusHours(offset).toKotlinLocalDateTime(),
            temperature = feels + 4,
            feelsLike = feels,
            humidity = 60,
            windSpeed = 8.0,
            precipitationProbability = rain,
            weatherCode = WeatherCode.PARTLY_CLOUDY,
            uvIndex = 2.0,
            isCelsius = false
        )
        val line = LaterTodayLine.stableLine(
            listOf(hour(1, 48.0, 10), hour(3, 41.0, 55), hour(6, 44.0, 20))
        )
        assertEquals("Later today: feels like 41–48°F · rain up to 55%", line)
    }

    @Test
    fun `stable fallback without rain risk or hours`() {
        fun hour(feels: Double) = HourlyForecast(
            dateTime = now.plusHours(2).toKotlinLocalDateTime(),
            temperature = feels + 4,
            feelsLike = feels,
            humidity = 60,
            windSpeed = 8.0,
            precipitationProbability = 5,
            weatherCode = WeatherCode.CLEAR_SKY,
            uvIndex = 3.0,
            isCelsius = false
        )
        assertEquals("Later today: feels like 50°F", LaterTodayLine.stableLine(listOf(hour(50.0))))
        assertNull(LaterTodayLine.stableLine(emptyList()))
    }
}
