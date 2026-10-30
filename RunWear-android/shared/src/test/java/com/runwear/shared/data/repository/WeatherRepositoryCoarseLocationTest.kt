package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.GeocodingResponse
import com.runwear.shared.data.model.NominatimSearchResult
import com.runwear.shared.data.model.OpenMeteoResponse
import com.runwear.shared.data.model.ReverseGeocodingResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coarse-location posture (2026-10-30, parity with iOS
 * WeatherServicePrivacyTests): the weather API must only ever receive
 * coordinates rounded to 1 decimal place (~11 km). Callers keep passing
 * full precision in — rounding lives at this request boundary only, so
 * stored/displayed/manual locations stay exact.
 */
class WeatherRepositoryCoarseLocationTest {

    private class FakeOpenMeteoApi : OpenMeteoApi {
        var weatherLat: Double? = null
        var weatherLon: Double? = null
        var timeZoneLat: Double? = null
        var timeZoneLon: Double? = null

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
            weatherLat = latitude
            weatherLon = longitude
            return OpenMeteoResponse(latitude = latitude, longitude = longitude, timezone = "America/New_York")
        }

        override suspend fun getTimeZone(
            latitude: Double,
            longitude: Double,
            timezone: String,
            forecastDays: Int
        ): OpenMeteoResponse {
            timeZoneLat = latitude
            timeZoneLon = longitude
            return OpenMeteoResponse(latitude = latitude, longitude = longitude, timezone = "America/New_York")
        }
    }

    private class UnusedGeocodingApi : GeocodingApi {
        override suspend fun searchLocation(
            query: String,
            count: Int,
            language: String,
            format: String
        ): GeocodingResponse = throw UnsupportedOperationException("not used by these tests")
    }

    private class UnusedNominatimApi : NominatimApi {
        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            zoom: Int
        ): ReverseGeocodingResponse = throw UnsupportedOperationException("not used by these tests")

        override suspend fun searchLocation(
            query: String,
            format: String,
            limit: Int,
            addressDetails: Int
        ): List<NominatimSearchResult> = throw UnsupportedOperationException("not used by these tests")
    }

    private fun repository(api: FakeOpenMeteoApi) =
        WeatherRepository(api, UnusedGeocodingApi(), UnusedNominatimApi())

    @Test
    fun `rounding rule matches the iOS coarseCoordinate contract`() {
        assertEquals(40.7, coarseCoordinate(40.7128), 0.0)
        assertEquals(-74.0, coarseCoordinate(-73.9857), 0.0)
        assertEquals(51.5, coarseCoordinate(51.5074), 0.0)
        assertEquals(-0.1, coarseCoordinate(-0.1278), 0.0)
        assertEquals(0.0, coarseCoordinate(0.04), 0.0)
        // Already-coarse values pass through unchanged.
        assertEquals(40.7, coarseCoordinate(40.7), 0.0)
        assertEquals(-74.0, coarseCoordinate(-74.0), 0.0)
    }

    @Test
    fun `forecast query carries rounded coordinates only`() = runBlocking {
        val api = FakeOpenMeteoApi()
        val result = repository(api).getForecast(40.7128, -73.9857)
        assertTrue(result.isSuccess)
        assertEquals(40.7, api.weatherLat!!, 0.0)
        assertEquals(-74.0, api.weatherLon!!, 0.0)
        // Regression: the full-precision inputs never reach the API.
        assertNotEquals(40.7128, api.weatherLat)
        assertNotEquals(-73.9857, api.weatherLon)
    }

    @Test
    fun `timezone query carries rounded coordinates only`() = runBlocking {
        val api = FakeOpenMeteoApi()
        val zone = repository(api).getLocationTimeZone(40.7128, -73.9857)
        assertTrue(zone.isSuccess)
        assertEquals("America/New_York", zone.getOrThrow().id)
        assertEquals(40.7, api.timeZoneLat!!, 0.0)
        assertEquals(-74.0, api.timeZoneLon!!, 0.0)
    }
}
