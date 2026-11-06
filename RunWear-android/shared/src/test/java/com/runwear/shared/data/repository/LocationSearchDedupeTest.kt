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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 X-12 (carried W-12): duplicate location-search rows.
 *
 * The fixtures mirror the LIVE Nominatim responses captured
 * 2026-11-04 for the walkthrough's two searches:
 *
 *  - "new York" returns the CITY (relation 175905, address has
 *    city + state, 40.7127,-74.0060) AND the STATE (relation
 *    61320, address has state only, 43.1562,-75.8450). The state
 *    hit's city is synthesized by the model's display-name
 *    fallback, so both hits derive "New York, NY" /
 *    "New York, United States" — identical rows for two different
 *    OSM entities. Dedupe by place identity must keep exactly
 *    one: the city (the geocoder's first-ranked hit).
 *  - "Florida" returns three region-only hits (the US state, a
 *    Puerto Rican county, a Uruguayan department) that share the
 *    NAME "Florida" but are three distinct places — identity
 *    (simple place + region/country detail) keeps all three,
 *    where a name-string dedupe would collapse them to one.
 */
class LocationSearchDedupeTest {

    private class StubNominatimApi(
        private val searchResults: List<NominatimSearchResult>
    ) : NominatimApi {
        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            zoom: Int
        ): ReverseGeocodingResponse = throw UnsupportedOperationException("not used")

        override suspend fun searchLocation(
            query: String,
            format: String,
            limit: Int,
            addressDetails: Int
        ): List<NominatimSearchResult> = searchResults
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

    private fun repository(results: List<NominatimSearchResult>) =
        WeatherRepository(UnusedOpenMeteoApi(), UnusedGeocodingApi(), StubNominatimApi(results))

    // The live "new York" pair (Nominatim, 2026-11-04).
    private val newYorkCity = NominatimSearchResult(
        lat = "40.7127281",
        lon = "-74.0060152",
        displayName = "New York, United States",
        address = Address(city = "New York", state = "New York", country = "United States", countryCode = "us")
    )
    private val newYorkState = NominatimSearchResult(
        lat = "43.1561681",
        lon = "-75.8449946",
        displayName = "New York, United States",
        address = Address(state = "New York", country = "United States", countryCode = "us")
    )

    // The live "Florida" trio (Nominatim, 2026-11-04).
    private val floridaUsa = NominatimSearchResult(
        lat = "27.7567667",
        lon = "-81.4639835",
        displayName = "Florida, United States",
        address = Address(state = "Florida", country = "United States", countryCode = "us")
    )
    private val floridaUruguay = NominatimSearchResult(
        lat = "-33.8333330",
        lon = "-55.9166670",
        displayName = "Florida, Uruguay",
        address = Address(state = "Florida", country = "Uruguay", countryCode = "uy")
    )
    private val floridaPuertoRico = NominatimSearchResult(
        lat = "18.3755793",
        lon = "-66.5602412",
        displayName = "Florida, Puerto Rico, United States",
        address = Address(state = "Puerto Rico", country = "United States", countryCode = "us")
    )

    @Test
    fun `the mechanism - the state hit alone masquerades as the city row`() = runBlocking {
        // Diagnosis pin, through the real construction path: the
        // STATE hit on its own produces the identical row to the
        // CITY hit — same name, display and detail — because its
        // missing city is synthesized from the display name. Only
        // the coordinates differ (~250 km apart). This is WHY the
        // sheet showed the row twice when both hits came back.
        val cityRow = repository(listOf(newYorkCity)).searchLocation("new York").getOrThrow().single()
        val stateRow = repository(listOf(newYorkState)).searchLocation("new York").getOrThrow().single()
        assertEquals(cityRow.name, stateRow.name)
        assertEquals(cityRow.displayName, stateRow.displayName)
        assertEquals(cityRow.detail, stateRow.detail)
        assertEquals(cityRow.placeIdentity, stateRow.placeIdentity)
        assertTrue(cityRow.latitude != stateRow.latitude)
    }

    @Test
    fun `new york search returns the city exactly once`() = runBlocking {
        val results = repository(listOf(newYorkCity, newYorkState))
            .searchLocation("new York").getOrThrow()
        assertEquals(1, results.size)
        // The survivor is the CITY hit (geocoder importance order),
        // not the state centroid.
        assertEquals(40.7127281, results[0].latitude, 0.0000001)
        assertEquals(-74.0060152, results[0].longitude, 0.0000001)
        assertEquals("New York, NY", results[0].displayName)
    }

    @Test
    fun `the three distinct floridas all survive`() = runBlocking {
        val results = repository(listOf(floridaUsa, floridaUruguay, floridaPuertoRico))
            .searchLocation("Florida").getOrThrow()
        assertEquals(3, results.size)
        // USA hits first (repository sort), stable within the group:
        // US state, then the Puerto Rican county, then Uruguay.
        assertEquals(
            listOf("Florida, United States", "Puerto Rico, United States", "Florida, Uruguay"),
            results.map { it.detail }
        )
        assertEquals(3, results.map { it.placeIdentity }.distinct().size)
    }

    @Test
    fun `same name in different regions is not a duplicate`() = runBlocking {
        // A name-string dedupe would collapse these; identity must not.
        val springfieldIl = NominatimSearchResult(
            lat = "39.7817213", lon = "-89.6501481",
            displayName = "Springfield, Illinois, United States",
            address = Address(city = "Springfield", state = "Illinois", country = "United States", countryCode = "us")
        )
        val springfieldMo = NominatimSearchResult(
            lat = "37.2089572", lon = "-93.2922989",
            displayName = "Springfield, Missouri, United States",
            address = Address(city = "Springfield", state = "Missouri", country = "United States", countryCode = "us")
        )
        val results = repository(listOf(springfieldIl, springfieldMo))
            .searchLocation("Springfield").getOrThrow()
        assertEquals(listOf("Springfield, IL", "Springfield, MO"), results.map { it.displayName })
    }

    @Test
    fun `dedupe preserves the stable order of the survivors`() = runBlocking {
        val boston = NominatimSearchResult(
            lat = "42.3600825", lon = "-71.0588801",
            displayName = "Boston, Massachusetts, United States",
            address = Address(city = "Boston", state = "Massachusetts", country = "United States", countryCode = "us")
        )
        val chicago = NominatimSearchResult(
            lat = "41.8781136", lon = "-87.6297982",
            displayName = "Chicago, Illinois, United States",
            address = Address(city = "Chicago", state = "Illinois", country = "United States", countryCode = "us")
        )
        // The duplicate sits between distinct hits; the survivors
        // keep their relative order and the duplicate never
        // re-enters through the take(5).
        val results = repository(listOf(newYorkCity, boston, newYorkState, chicago))
            .searchLocation("new York").getOrThrow()
        assertEquals(
            listOf("New York, NY", "Boston, MA", "Chicago, IL"),
            results.map { it.displayName }
        )
    }

    @Test
    fun `dedupeByPlaceIdentity keeps the first of an identical pair`() {
        val first = LocationResult(40.7127281, -74.0060152, "New York", "New York, NY", "New York, United States", isUSA = true)
        val dupe = LocationResult(43.1561681, -75.8449946, "New York", "New York, NY", "New York, United States", isUSA = true)
        assertEquals(listOf(first), dedupeByPlaceIdentity(listOf(first, dupe)))
        assertEquals(listOf(dupe), dedupeByPlaceIdentity(listOf(dupe, first)))
    }
}
