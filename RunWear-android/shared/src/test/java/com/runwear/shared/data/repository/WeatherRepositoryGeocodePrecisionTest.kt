package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.Address
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
 * Place-name precision posture (RW-7.4 follow-up, 2026-11-01): the
 * Nominatim reverse-geocode request must only ever carry coordinates
 * rounded to 3 decimal places (~111 m). The declared posture is
 * approximate location; sending the GPS fix at full precision to a
 * third party contradicts it. 3 dp — not the weather boundary's 1 dp —
 * because a place name must remain the user's actual locality: 1 dp
 * (~11 km) can name a neighbouring town. Callers keep passing full
 * precision in; rounding lives at this request boundary only, so
 * stored/displayed/manual locations stay exact.
 */
class WeatherRepositoryGeocodePrecisionTest {

    private class CapturingNominatimApi : NominatimApi {
        var reverseLat: Double? = null
        var reverseLon: Double? = null

        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            zoom: Int
        ): ReverseGeocodingResponse {
            reverseLat = latitude
            reverseLon = longitude
            return ReverseGeocodingResponse(
                address = Address(city = "New York", state = "New York")
            )
        }

        override suspend fun searchLocation(
            query: String,
            format: String,
            limit: Int,
            addressDetails: Int
        ): List<NominatimSearchResult> = throw UnsupportedOperationException("not used by these tests")
    }

    private class UnusedOpenMeteoApi : OpenMeteoApi {
        override suspend fun getWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            temperatureUnit: String,
            windSpeedUnit: String,
            timezone: String,
            forecastDays: Int,
            daily: String
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used by these tests")

        override suspend fun getTimeZone(
            latitude: Double,
            longitude: Double,
            timezone: String,
            forecastDays: Int
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used by these tests")
    }

    private class UnusedGeocodingApi : GeocodingApi {
        override suspend fun searchLocation(
            query: String,
            count: Int,
            language: String,
            format: String
        ): GeocodingResponse = throw UnsupportedOperationException("not used by these tests")
    }

    private fun repository(api: CapturingNominatimApi) =
        WeatherRepository(UnusedOpenMeteoApi(), UnusedGeocodingApi(), api)

    @Test
    fun `reverse geocode request carries 3-decimal rounded coordinates only`() = runBlocking {
        val api = CapturingNominatimApi()
        val result = repository(api).reverseGeocode(40.71284, -73.98566)
        assertTrue(result.isSuccess)
        assertEquals(40.713, api.reverseLat!!, 0.0)
        assertEquals(-73.986, api.reverseLon!!, 0.0)
        // Regression: the full-precision fix never reaches the API.
        assertNotEquals(40.71284, api.reverseLat)
        assertNotEquals(-73.98566, api.reverseLon)
    }

    @Test
    fun `reverse geocode rounding holds for southern and eastern hemispheres`() = runBlocking {
        val api = CapturingNominatimApi()
        val result = repository(api).reverseGeocode(-33.85678, 151.21529)
        assertTrue(result.isSuccess)
        assertEquals(-33.857, api.reverseLat!!, 0.0)
        assertEquals(151.215, api.reverseLon!!, 0.0)
    }

    @Test
    fun `already rounded coordinates pass through unchanged`() = runBlocking {
        val api = CapturingNominatimApi()
        val result = repository(api).reverseGeocode(40.713, -73.986)
        assertTrue(result.isSuccess)
        assertEquals(40.713, api.reverseLat!!, 0.0)
        assertEquals(-73.986, api.reverseLon!!, 0.0)
    }
}
