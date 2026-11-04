package com.runwear.app.ui.viewmodel

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.SimplePlace
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime

/**
 * GEN-2 A2 (W-6): the atomic place-switch commit. The walkthrough
 * caught a frame pairing the "New York, NY" label with Kansas City's
 * "44° COOL" — selectLocation wrote the new name into state at tap
 * time while the weather still described the old place.
 * fetchWeather's success write is now the ONLY commit a switch
 * produces, and it is this pure function: the returned state carries
 * the new place AND the new weather together, or (no switch pending)
 * the old place with the new weather. There is no intermediate state
 * with the new name over old conditions.
 */
class PlaceSwitchCommitTest {

    private fun weather(
        hour: Int,
        feelsLike: Double,
        code: WeatherCode
    ): WeatherConditions = WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 4, hour, 0),
        temperature = feelsLike + 5.0,
        feelsLike = feelsLike,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = code
    )

    private fun outfit(weather: WeatherConditions): OutfitRecommendation =
        OutfitRecommendation(
            weather = weather,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.LIGHT_TIGHTS
        )

    private val kcWeather = weather(8, 44.0, WeatherCode.MAINLY_CLEAR)
    private val nyWeather = weather(9, 58.0, WeatherCode.PARTLY_CLOUDY)

    private val kcState = MainUiState(
        isLoading = true,
        weather = kcWeather,
        outfit = outfit(kcWeather),
        locationName = "Kansas City, MO",
        simplePlace = SimplePlace("Kansas City", "MO")
    )

    @Test
    fun `a switch commits place and weather in the same state`() {
        val committed = kcState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        // The pairing the walkthrough frame violated: wherever the
        // New York name appears, the New York weather is already there.
        assertEquals("New York, NY", committed.locationName)
        assertEquals(SimplePlace("New York", "NY"), committed.simplePlace)
        assertSame(nyWeather, committed.weather)
        assertEquals(false, committed.isLoading)
        // NOW re-anchors on the response's own location-local time.
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.selectedDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.locationNow)
    }

    @Test
    fun `without a pending place the place fields carry over while conditions update`() {
        val refreshed = kcState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = null,
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        assertEquals("Kansas City, MO", refreshed.locationName)
        assertEquals(SimplePlace("Kansas City", "MO"), refreshed.simplePlace)
        assertSame(nyWeather, refreshed.weather)
    }

    @Test
    fun `an explicit selection keeps its moment and only re-anchors locationNow`() {
        val picked = LocalDateTime.of(2026, 11, 5, 7, 30)
        val state = kcState.copy(selectedDateTime = picked, isNowSelected = false)
        val committed = state.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = false
        )
        assertEquals(picked, committed.selectedDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.locationNow)
        assertEquals("New York, NY", committed.locationName)
    }
}
