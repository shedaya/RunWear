package com.runwear.app.widget

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The last-good record is the widget's memory — if it can't survive a
 * persist/restore round-trip intact, the widget is back to having no memory
 * (and the permanent error card comes back with it).
 */
class WidgetLastGoodTest {

    private fun sampleWeather() = WeatherConditions(
        dateTime = LocalDateTime.of(2026, 10, 22, 7, 3),
        temperature = 46.4,
        feelsLike = 40.2,
        humidity = 71,
        windSpeed = 11.0,
        windGusts = 19.5,
        windDirection = 270,
        precipitationProbability = 40,
        precipitation = 0.3,
        isRaining = true,
        isSnowing = false,
        cloudCover = 62,
        uvIndex = 1.4,
        weatherCode = WeatherCode.RAIN_MODERATE,
        isCelsius = false
    )

    private fun sample(weather: WeatherConditions) = WidgetLastGood(
        weather = weather,
        outfit = OutfitRecommendation(
            weather = weather,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            topMid = ClothingItem.QUARTER_ZIP,
            topOuter = ClothingItem.WINDBREAKER,
            bottom = ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.BASEBALL_CAP,
            hands = null,
            accessories = listOf(ClothingItem.SUNGLASSES, ClothingItem.REFLECTIVE_GEAR),
            tips = listOf("not persisted — the widget never renders tips")
        ),
        locationName = "Incheon, South Korea",
        latitude = 37.4684,
        longitude = 126.4376,
        laterTodayLine = "By 6 PM: drop the Windbreaker · rain 40%",
        savedAtLocal = LocalDateTime.of(2026, 10, 22, 7, 5, 30),
        savedAtEpochMs = 1_729_000_000_000L
    )

    @Test
    fun `round trip preserves every rendered field`() {
        val original = sample(sampleWeather())
        val restored = WidgetLastGood.fromEntries(original.toEntries())

        assertEquals(original.weather, restored?.weather)
        assertEquals(original.locationName, restored?.locationName)
        assertEquals(original.latitude, restored?.latitude)
        assertEquals(original.longitude, restored?.longitude)
        assertEquals(original.laterTodayLine, restored?.laterTodayLine)
        assertEquals(original.savedAtLocal, restored?.savedAtLocal)
        assertEquals(original.savedAtEpochMs, restored?.savedAtEpochMs)

        val outfit = restored!!.outfit
        assertEquals(ClothingItem.LONG_SLEEVE_LIGHT, outfit.topBase)
        assertEquals(ClothingItem.QUARTER_ZIP, outfit.topMid)
        assertEquals(ClothingItem.WINDBREAKER, outfit.topOuter)
        assertEquals(ClothingItem.LIGHT_TIGHTS, outfit.bottom)
        assertEquals(ClothingItem.BASEBALL_CAP, outfit.head)
        assertNull(outfit.hands)
        assertEquals(
            listOf(ClothingItem.SUNGLASSES, ClothingItem.REFLECTIVE_GEAR),
            outfit.accessories
        )
        assertTrue(outfit.tips.isEmpty())
    }

    @Test
    fun `minimal outfit round trips with null slots absent`() {
        val weather = sampleWeather()
        val minimal = sample(weather).copy(
            outfit = OutfitRecommendation(
                weather = weather,
                topBase = ClothingItem.TANK_TOP,
                bottom = ClothingItem.SHORT_SHORTS
            ),
            laterTodayLine = null
        )
        val entries = minimal.toEntries()
        assertTrue("null slots must not be written", !entries.containsKey("o_top_mid"))
        assertTrue("null later-line must not be written", !entries.containsKey("later_today_line"))

        val restored = WidgetLastGood.fromEntries(entries)!!
        assertEquals(ClothingItem.TANK_TOP, restored.outfit.topBase)
        assertNull(restored.outfit.topMid)
        assertNull(restored.outfit.topOuter)
        assertNull(restored.laterTodayLine)
    }

    @Test
    fun `corrupt or partial entries read as no last-good, never a crash`() {
        assertNull(WidgetLastGood.fromEntries(emptyMap()))
        assertNull(WidgetLastGood.fromEntries(mapOf("w_temperature" to "hot")))
        val entries = sample(sampleWeather()).toEntries().toMutableMap()
        entries["o_top_base"] = "NOT_A_GARMENT"
        assertNull(WidgetLastGood.fromEntries(entries))
        entries["o_top_base"] = "TANK_TOP"
        entries.remove("saved_at_epoch_ms")
        assertNull(WidgetLastGood.fromEntries(entries))
    }
}
