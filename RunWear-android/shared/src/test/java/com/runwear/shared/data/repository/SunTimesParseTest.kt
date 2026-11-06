package com.runwear.shared.data.repository

import com.runwear.shared.data.model.DailyWeather
import com.runwear.shared.data.model.OpenMeteoResponse
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE C (RW-53(ii)): the forecast payload's daily block
 * parses into per-day sunrise/sunset minutes — the day chart's
 * night shading source. The payload always carried sunrise and
 * sunset; the app never requested or parsed them before.
 */
class SunTimesParseTest {

    private class UnusedOpenMeteoApi : com.runwear.shared.data.api.OpenMeteoApi {
        override suspend fun getWeather(
            latitude: Double, longitude: Double, current: String, hourly: String,
            temperatureUnit: String, windSpeedUnit: String, timezone: String,
            forecastDays: Int, daily: String
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used")

        override suspend fun getTimeZone(
            latitude: Double, longitude: Double, timezone: String, forecastDays: Int
        ): OpenMeteoResponse = throw UnsupportedOperationException("not used")
    }

    private class UnusedGeocodingApi : com.runwear.shared.data.api.GeocodingApi {
        override suspend fun searchLocation(
            query: String, count: Int, language: String, format: String
        ): com.runwear.shared.data.model.GeocodingResponse =
            throw UnsupportedOperationException("not used")
    }

    private class UnusedNominatimApi : com.runwear.shared.data.api.NominatimApi {
        override suspend fun reverseGeocode(
            latitude: Double, longitude: Double, format: String, zoom: Int
        ): com.runwear.shared.data.model.ReverseGeocodingResponse =
            throw UnsupportedOperationException("not used")

        override suspend fun searchLocation(
            query: String, format: String, limit: Int, addressDetails: Int
        ): List<com.runwear.shared.data.model.NominatimSearchResult> =
            throw UnsupportedOperationException("not used")
    }

    private fun repository() =
        WeatherRepository(UnusedOpenMeteoApi(), UnusedGeocodingApi(), UnusedNominatimApi())

    private fun response(daily: DailyWeather?) = OpenMeteoResponse(
        latitude = 39.0, longitude = -94.5, timezone = "America/Chicago",
        daily = daily
    )

    @Test
    fun `daily sunrise and sunset parse to minutes since local midnight`() {
        val times = repository().parseDailySunTimes(
            response(
                DailyWeather(
                    time = listOf("2026-11-05", "2026-11-06"),
                    sunrise = listOf("2026-11-05T06:12", "2026-11-06T06:13"),
                    sunset = listOf("2026-11-05T19:48", "2026-11-06T19:47")
                )
            )
        )
        assertEquals(2, times.size)
        assertEquals(LocalDate(2026, 11, 5), times[0].date)
        assertEquals(372, times[0].sunriseMinutes)
        assertEquals(1188, times[0].sunsetMinutes)
        assertEquals(373, times[1].sunriseMinutes)
    }

    @Test
    fun `missing daily block yields no sun times - the chart falls back`() {
        assertTrue(repository().parseDailySunTimes(response(null)).isEmpty())
    }

    @Test
    fun `malformed entries are skipped, not fatal`() {
        val times = repository().parseDailySunTimes(
            response(
                DailyWeather(
                    time = listOf("2026-11-05", "not-a-date"),
                    sunrise = listOf("2026-11-05T06:12", "2026-11-06T06:13"),
                    sunset = listOf("2026-11-05T19:48", "2026-11-06T19:47")
                )
            )
        )
        assertEquals(1, times.size)
    }
}
