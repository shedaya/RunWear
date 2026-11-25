package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.Address
import com.runwear.shared.data.model.GeocodingResponse
import com.runwear.shared.data.model.NominatimSearchResult
import com.runwear.shared.data.model.OpenMeteoResponse
import com.runwear.shared.data.model.ReverseGeocodingResponse
import com.runwear.shared.domain.model.SimplePlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-4 (RC-4): the geocode boundary derives the simple place ONCE.
 * The raw geocode string ("New York, New York") must never be what
 * [WeatherRepository.reverseGeocode] returns, and a search result's
 * display name is simple at creation.
 */
class WeatherRepositorySimplePlaceTest {

    private class StubNominatimApi(
        private val response: ReverseGeocodingResponse
    ) : NominatimApi {
        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            zoom: Int
        ): ReverseGeocodingResponse = response

        override suspend fun searchLocation(
            query: String,
            format: String,
            limit: Int,
            addressDetails: Int
        ): List<NominatimSearchResult> = throw UnsupportedOperationException("not used")
    }

    private class UnusedOpenMeteoApi : OpenMeteoApi {
        override suspend fun getWeather(
            latitude: Double, longitude: Double, current: String, hourly: String,
            temperatureUnit: String, windSpeedUnit: String, timezone: String, forecastDays: Int, daily: String
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used")

        override suspend fun getTimeZone(
            latitude: Double, longitude: Double, timezone: String, forecastDays: Int
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used")
    }

    private class UnusedGeocodingApi : GeocodingApi {
        override suspend fun searchLocation(
            query: String, count: Int, language: String, format: String
        ): GeocodingResponse = throw UnsupportedOperationException("not used")
    }

    private fun repository(response: ReverseGeocodingResponse) =
        WeatherRepository(UnusedOpenMeteoApi(), UnusedGeocodingApi(), StubNominatimApi(response))

    @Test
    fun `reverse geocode returns the simple place, never the raw string`() = runBlocking {
        val repo = repository(
            ReverseGeocodingResponse(
                address = Address(city = "New York", state = "New York", countryCode = "us")
            )
        )
        val name = repo.reverseGeocode(40.713, -73.986)
        assertTrue(name.isSuccess)
        assertEquals("New York, NY", name.getOrThrow())
    }

    @Test
    fun `reverse geocode place carries the value type`() = runBlocking {
        val repo = repository(
            ReverseGeocodingResponse(
                address = Address(city = "Winston-Salem", state = "North Carolina", countryCode = "us")
            )
        )
        val place = repo.reverseGeocodePlace(36.1, -80.24)
        assertTrue(place.isSuccess)
        assertEquals(SimplePlace("Winston-Salem", "NC"), place.getOrThrow())
        assertEquals("Winston-Salem, NC", place.getOrThrow().display)
    }

    @Test
    fun `long city with a multi-word region simplifies at the boundary`() = runBlocking {
        val repo = repository(
            ReverseGeocodingResponse(
                address = Address(
                    city = "Truth or Consequences", state = "New Mexico", countryCode = "us"
                )
            )
        )
        assertEquals(
            "Truth or Consequences, NM",
            repo.reverseGeocode(33.13, -107.25).getOrThrow()
        )
    }

    @Test
    fun `display name fallback is normalized too - no raw string escapes`() = runBlocking {
        val repo = repository(
            ReverseGeocodingResponse(
                address = Address(),
                displayName = "Kansas City, Missouri, United States"
            )
        )
        assertEquals("Kansas City, MO", repo.reverseGeocode(39.1, -94.58).getOrThrow())
    }

    @Test
    fun `search result display name is simple at creation`() {
        val result = NominatimSearchResult(
            lat = "40.7128",
            lon = "-74.0060",
            displayName = "New York, New York, United States",
            address = Address(city = "New York", state = "New York", countryCode = "us")
        )
        assertEquals("New York, NY", result.formattedDisplayName)
        assertEquals(SimplePlace("New York", "NY"), result.simplePlace)
    }

    @Test
    fun `search result with a long name and multi-word region is simple at creation`() {
        val result = NominatimSearchResult(
            lat = "36.0995",
            lon = "-80.2442",
            displayName = "Winston-Salem, North Carolina, United States",
            address = Address(city = "Winston-Salem", state = "North Carolina", countryCode = "us")
        )
        assertEquals("Winston-Salem, NC", result.formattedDisplayName)
    }
}
