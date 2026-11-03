package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.CurrentWeather
import com.runwear.shared.data.model.GeocodingResponse
import com.runwear.shared.data.model.HourlyWeather
import com.runwear.shared.data.model.NominatimSearchResult
import com.runwear.shared.data.model.OpenMeteoResponse
import com.runwear.shared.data.model.ReverseGeocodingResponse
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.OutfitCombination
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-2 (RC-2, D-05): the canonical store.
 *
 * Weather is fetched, parsed, and stored in ONE frame — °F
 * temperatures, mph wind, mm precipitation — with NO unit value
 * anywhere in the data. The user's unit preference is a render-time
 * projection ([TemperatureUnit]) and never reaches the request, the
 * model, a cache key, or an image combination id. These tests pin
 * that structure so the D-05 mechanism (a unit stamped into the
 * weather object at fetch time) cannot return.
 */
class WeatherRepositoryCanonicalTest {

    private class RecordingOpenMeteoApi : OpenMeteoApi {
        var temperatureUnitArg: String? = null
        var windSpeedUnitArg: String? = null

        override suspend fun getWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            temperatureUnit: String,
            windSpeedUnit: String,
            timezone: String,
            forecastDays: Int
        ): OpenMeteoResponse {
            temperatureUnitArg = temperatureUnit
            windSpeedUnitArg = windSpeedUnit
            return response()
        }

        override suspend fun getTimeZone(
            latitude: Double,
            longitude: Double,
            timezone: String,
            forecastDays: Int
        ): OpenMeteoResponse = OpenMeteoResponse(latitude, longitude, "America/New_York")

        companion object {
            fun response() = OpenMeteoResponse(
                latitude = 40.7,
                longitude = -74.0,
                timezone = "America/New_York",
                current = CurrentWeather(
                    time = "2026-11-03T07:00",
                    temperature = 45.0,
                    humidity = 40,
                    apparentTemperature = 41.0,
                    precipitation = 0.0,
                    rain = 0.0,
                    snowfall = 0.0,
                    weatherCode = 0,
                    cloudCover = 10,
                    windSpeed = 10.0,
                    windGusts = 15.0,
                    windDirection = 180,
                    uvIndex = 1.0,
                    precipitationProbability = 5
                ),
                hourly = HourlyWeather(
                    time = listOf("2026-11-03T07:00"),
                    temperature = listOf(45.0),
                    humidity = listOf(40),
                    apparentTemperature = listOf(41.0),
                    precipitationProbability = listOf(5),
                    precipitation = listOf(0.0),
                    rain = listOf(0.0),
                    snowfall = listOf(0.0),
                    weatherCode = listOf(0),
                    cloudCover = listOf(10),
                    windSpeed = listOf(10.0),
                    windGusts = listOf(15.0),
                    windDirection = listOf(180),
                    uvIndex = listOf(1.0)
                )
            )
        }
    }

    private class UnusedGeocodingApi : GeocodingApi {
        override suspend fun searchLocation(
            query: String, count: Int, language: String, format: String
        ): GeocodingResponse = throw UnsupportedOperationException("not used")
    }

    private class UnusedNominatimApi : NominatimApi {
        override suspend fun reverseGeocode(
            latitude: Double, longitude: Double, format: String, zoom: Int
        ): ReverseGeocodingResponse = throw UnsupportedOperationException("not used")

        override suspend fun searchLocation(
            query: String, format: String, limit: Int, addressDetails: Int
        ): List<NominatimSearchResult> = throw UnsupportedOperationException("not used")
    }

    private fun repository(api: RecordingOpenMeteoApi) =
        WeatherRepository(api, UnusedGeocodingApi(), UnusedNominatimApi())

    @Test
    fun `the forecast request is always canonical - fahrenheit and mph`() = runBlocking {
        val api = RecordingOpenMeteoApi()
        val result = repository(api).getForecast(40.7128, -73.9857)
        assertTrue(result.isSuccess)
        assertEquals("fahrenheit", api.temperatureUnitArg)
        assertEquals("mph", api.windSpeedUnitArg)
    }

    @Test
    fun `parsed weather carries the API values unchanged - the canonical frame`() = runBlocking {
        val repo = repository(RecordingOpenMeteoApi())
        val response = RecordingOpenMeteoApi.response()

        val current = repo.parseCurrentWeather(response)
        assertEquals(45.0, current.temperature, 0.0)
        assertEquals(41.0, current.feelsLike, 0.0)
        assertEquals(10.0, current.windSpeed, 0.0)
        assertEquals(15.0, current.windGusts, 0.0)

        val hourly = repo.parseHourlyForecast(response)
        assertEquals(1, hourly.size)
        assertEquals(45.0, hourly[0].temperature, 0.0)
        assertEquals(41.0, hourly[0].feelsLike, 0.0)
        assertEquals(10.0, hourly[0].windSpeed, 0.0)

        val atHour = repo.parseWeatherForDateTime(response, LocalDateTime(2026, 11, 3, 7, 0))
        assertEquals(45.0, atHour.temperature, 0.0)
        assertEquals(41.0, atHour.feelsLike, 0.0)

        val fetched = repo.getCurrentWeather(40.7, -74.0).getOrThrow()
        assertEquals(45.0, fetched.temperature, 0.0)
        assertEquals(41.0, fetched.feelsLikeInFahrenheit, 0.0)
    }

    @Test
    fun `no unit value exists on the weather models`() {
        for (type in listOf(WeatherConditions::class.java, HourlyForecast::class.java)) {
            val offenders = type.declaredFields.map { it.name.lowercase() }
                .filter { it.contains("celsius") || it.contains("unit") }
            assertTrue(
                "${type.simpleName} carries unit-bearing fields: $offenders",
                offenders.isEmpty()
            )
        }
    }

    @Test
    fun `the hero combination id - the image request key - contains no unit`() {
        val repo = repository(RecordingOpenMeteoApi())
        val weather = repo.parseCurrentWeather(RecordingOpenMeteoApi.response())
        val outfit = GetOutfitRecommendationUseCase().execute(weather)
        val combination = OutfitCombination.fromConditions(
            weather, outfit, GenderPreference.MALE,
            frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(
                weather, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
            )
        )
        for (token in listOf("CELSIUS", "FAHRENHEIT", "°C", "°F", "KMH", "MPH")) {
            assertFalse(
                "combination id ${combination.id} contains unit token $token",
                combination.id.contains(token)
            )
        }
    }

    @Test
    fun `the render projection converts canonical values - and only at render`() {
        // The D-05 field values: 45°F actual / 41°F feels-like / 10 mph wind.
        assertEquals(45, TemperatureUnit.FAHRENHEIT.displayValue(45.0))
        assertEquals(7, TemperatureUnit.CELSIUS.displayValue(45.0))
        assertEquals(5, TemperatureUnit.CELSIUS.displayValue(41.0))
        assertEquals("45°F", TemperatureUnit.FAHRENHEIT.formatTemperature(45.0))
        assertEquals("7°C", TemperatureUnit.CELSIUS.formatTemperature(45.0))
        assertEquals("10 mph", TemperatureUnit.FAHRENHEIT.formatWind(10.0))
        assertEquals("16 km/h", TemperatureUnit.CELSIUS.formatWind(10.0))
        // Anchor points.
        assertEquals(0, TemperatureUnit.CELSIUS.displayValue(32.0))
        assertEquals(100, TemperatureUnit.CELSIUS.displayValue(212.0))
        assertEquals(-40, TemperatureUnit.CELSIUS.displayValue(-40.0))
    }
}
