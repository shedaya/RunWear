package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.OpenMeteoResponse
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val displayName: String,
    val detail: String = "",
    val isUSA: Boolean = false
) {
    /** WS-4: this result as a simple place (its [displayName] is its display). */
    val simplePlace: com.runwear.shared.domain.model.SimplePlace
        get() = com.runwear.shared.domain.model.SimplePlace.fromRawDisplayString(displayName)

    /**
     * GEN-3 X-12: this result's place identity — the composite the
     * search-result construction dedupes on: the derived simple
     * place ([displayName]) plus the full detail line ([detail],
     * region + country). Two geocoder hits whose identity matches
     * are the same place AS THIS APP REPRESENTS IT: no rendered
     * field distinguishes them, so showing both is a duplicate row.
     *
     * The identity is deliberately NOT the name alone and NOT the
     * coordinates: "Florida" the US state, "Florida" the Puerto
     * Rican county and "Florida" the Uruguayan department share a
     * name but carry different region/country details, so all three
     * survive; the New York CITY hit and the New York STATE hit
     * derive the same simple place and the same detail (see
     * [dedupeByPlaceIdentity]), so only the first — the city, by
     * geocoder importance — survives. Coordinates are excluded
     * because the state hit's centroid sits ~250 km from the city
     * it duplicates on screen.
     */
    val placeIdentity: String
        get() = displayName.trim().lowercase() + "|" + detail.trim().lowercase()
}

/**
 * GEN-3 X-12: drop results that duplicate an earlier result's
 * [LocationResult.placeIdentity], preserving the original order
 * (the geocoder's importance order — the survivor of a duplicate
 * pair is the hit the geocoder ranked first).
 *
 * Why duplicates exist at all (verified against live Nominatim,
 * 2026-11-04): a search for "new York" returns TWO distinct OSM
 * entities — the city (relation 175905, address carries city +
 * state) and the state (relation 61320, address carries state
 * only). [com.runwear.shared.data.model.NominatimSearchResult]
 * discards every distinguishing field (place_id, osm identity,
 * addresstype are not even parsed), and its cityName fallback
 * synthesizes a city for the region-only state hit from the
 * display name's first segment — so the state hit derives the
 * SAME simple place ("New York, NY") and the SAME detail
 * ("New York, United States") as the city hit, and the sheet
 * rendered both as identical consecutive rows. There is only one
 * source and no merge; the collapse happens at result
 * construction, so construction is where the dedupe belongs.
 */
internal fun dedupeByPlaceIdentity(results: List<LocationResult>): List<LocationResult> =
    results.distinctBy { it.placeIdentity }

/**
 * The coarse-location rounding applied at the weather request boundary:
 * coordinates are rounded to 1 decimal place (~11 km) before they are
 * serialized into an outbound Open-Meteo query — the same posture the iOS
 * app ships (WeatherService.coarseCoordinate) and the Play/App Store
 * privacy declarations state. Stored, displayed and manually entered
 * coordinates are NEVER rounded; this is applied only where
 * [WeatherRepository] hands coordinates to the weather API.
 */
internal fun coarseCoordinate(value: Double): Double =
    (value * 10).roundToInt() / 10.0

/**
 * The place-name rounding applied at the reverse-geocode request
 * boundary: coordinates are rounded to 3 decimal places (~111 m)
 * before they are handed to Nominatim, so the request never carries
 * the full-precision GPS fix — the declared posture is approximate
 * location. Finer than [coarseCoordinate] on purpose: a place name
 * must remain the user's actual locality, and 1 dp (~11 km) can name
 * a neighbouring town. Applied only inside [WeatherRepository.reverseGeocode];
 * callers keep passing full precision in and stored/displayed
 * coordinates are never rounded.
 */
internal fun geocodeCoordinate(value: Double): Double =
    (value * 1000).roundToInt() / 1000.0

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val nominatimApi: NominatimApi
) {

    // Timezone cache keyed by ~11 km coordinate cell — a fixed point's zone
    // effectively never changes, so this is one small lookup per location.
    private val timeZoneCache = mutableMapOf<String, ZoneId>()

    /**
     * The IANA timezone of the SELECTED LOCATION, from the weather API's own
     * response (timezone=auto resolves it from the coordinates). Every date/time
     * the UI shows for a location must be framed in this zone — never silently
     * in the phone's zone.
     */
    suspend fun getLocationTimeZone(latitude: Double, longitude: Double): Result<ZoneId> = runCatching {
        val key = "%.2f,%.2f".format(latitude, longitude)
        timeZoneCache[key]?.let { return@runCatching it }
        val zone = ZoneId.of(
            weatherApi.getTimeZone(coarseCoordinate(latitude), coarseCoordinate(longitude)).timezone
        )
        timeZoneCache[key] = zone
        zone
    }

    /**
     * The ONE forecast fetch. Current conditions, an explicit-hour selection
     * and the hourly list are all parsed from this single `v1/forecast`
     * response — callers must never issue a second identical request for the
     * same refresh (startup fix: three serial round-trips became one).
     */
    suspend fun getForecast(
        latitude: Double,
        longitude: Double
    ): Result<OpenMeteoResponse> = runCatching {
        // WS-2 (RC-2): the fetch is ALWAYS in the canonical frame
        // (°F / mph), whatever the user's display preference is. The
        // preference is a render-time projection and never reaches
        // the request — so a unit change can never trigger a refetch
        // or stamp a unit into the data that comes back.
        weatherApi.getWeather(
            latitude = coarseCoordinate(latitude),
            longitude = coarseCoordinate(longitude),
            temperatureUnit = "fahrenheit",
            windSpeedUnit = "mph"
        )
    }

    /**
     * The location's IANA zone as carried by a forecast response
     * (`timezone=auto` resolves it from the coordinates). Deriving the frame
     * from the weather response itself removes the standalone timezone
     * round-trip from the startup path; the zone is also cached by
     * coordinate cell for [getLocationTimeZone] callers.
     */
    fun recordZone(latitude: Double, longitude: Double, response: OpenMeteoResponse): ZoneId {
        val zone = runCatching { ZoneId.of(response.timezone) }
            .getOrDefault(ZoneId.systemDefault())
        timeZoneCache["%.2f,%.2f".format(latitude, longitude)] = zone
        return zone
    }

    /** Parse the current-conditions block of a shared forecast response. */
    fun parseCurrentWeather(
        response: OpenMeteoResponse
    ): WeatherConditions {
        val current = response.current ?: throw Exception("No current weather data")

        // The API's current.time is already location-local (timezone=auto).
        // Using the phone's clock here was one source of the frame mix-up:
        // "current" conditions were stamped with the device's timezone time.
        val locationNow = runCatching { parseApiLocalDateTime(current.time) }
            .getOrElse {
                val zone = runCatching { ZoneId.of(response.timezone) }
                    .getOrDefault(ZoneId.systemDefault())
                Clock.System.now().toLocalDateTime(TimeZone.of(zone.id))
            }

        return WeatherConditions(
            dateTime = locationNow,
            temperature = current.temperature,
            feelsLike = current.apparentTemperature,
            humidity = current.humidity,
            windSpeed = current.windSpeed,
            windGusts = current.windGusts,
            windDirection = current.windDirection,
            precipitationProbability = current.precipitationProbability,
            precipitation = current.precipitation,
            isRaining = current.rain > 0,
            isSnowing = current.snowfall > 0,
            cloudCover = current.cloudCover,
            uvIndex = current.uvIndex ?: 0.0,
            weatherCode = WeatherCode.fromCode(current.weatherCode)
        )
    }

    /** Convenience: one fetch + current-conditions parse. */
    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double
    ): Result<WeatherConditions> =
        getForecast(latitude, longitude).mapCatching { parseCurrentWeather(it) }

    /** Parse the hourly entry matching [dateTime]'s hour from a shared response. */
    fun parseWeatherForDateTime(
        response: OpenMeteoResponse,
        dateTime: LocalDateTime
    ): WeatherConditions {
        val hourly = response.hourly ?: throw Exception("No hourly weather data")

        // Find the closest hour in the forecast. A miss used to silently fall
        // back to index 0 (potentially ~24h off); now it fails loudly so the
        // UI shows the error screen with retry instead of wrong data (F2).
        // kotlinx LocalDateTime renders ISO ("2026-06-15T12:00"); its first
        // 13 chars are the "yyyy-MM-dd'T'HH" prefix the old formatter built.
        val targetHour = dateTime.toString().substring(0, 13)
        val index = hourly.time.indexOfFirst { it.startsWith(targetHour) }
        if (index < 0) {
            android.util.Log.e(
                "WeatherRepository",
                "No hourly entry for targetHour=$targetHour; refusing silent index-0 fallback"
            )
            throw Exception("No forecast data for the selected time")
        }

        // GEN-5 (RW-29 FA-15): every parallel-array read is
        // bounded. The gen-4 parser indexed the sibling arrays
        // directly, so a truncated series from the service threw
        // IndexOutOfBounds and the whole forecast became the
        // error screen; now a short array degrades to its last
        // value (or the type's zero) and the forecast survives.
        return WeatherConditions(
            dateTime = dateTime,
            temperature = hourly.temperature.atOrLast(index),
            feelsLike = hourly.apparentTemperature.atOrLast(index),
            humidity = hourly.humidity.atOrLast(index),
            windSpeed = hourly.windSpeed.atOrLast(index),
            windGusts = hourly.windGusts.atOrLast(index),
            windDirection = hourly.windDirection.getOrElse(index) { 0 },
            precipitationProbability = hourly.precipitationProbability.atOrLast(index),
            precipitation = hourly.precipitation.atOrLast(index),
            isRaining = hourly.rain.atOrLast(index) > 0,
            isSnowing = hourly.snowfall.atOrLast(index) > 0,
            cloudCover = hourly.cloudCover.atOrLast(index),
            uvIndex = hourly.uvIndex.atOrLast(index),
            weatherCode = WeatherCode.fromCode(hourly.weatherCode.atOrLast(index))
        )
    }

    /** Convenience: one fetch + explicit-hour parse. */
    suspend fun getWeatherForDateTime(
        latitude: Double,
        longitude: Double,
        dateTime: LocalDateTime
    ): Result<WeatherConditions> =
        getForecast(latitude, longitude).mapCatching {
            parseWeatherForDateTime(it, dateTime)
        }

    /** Parse the hourly forecast list from a shared response. */
    fun parseHourlyForecast(
        response: OpenMeteoResponse
    ): List<HourlyForecast> {
        val hourly = response.hourly ?: throw Exception("No hourly weather data")

        return hourly.time.mapIndexed { index, time ->
            HourlyForecast(
                dateTime = parseApiLocalDateTime(time),
                // GEN-5 (FA-15): bounded reads, as above.
                temperature = hourly.temperature.atOrLast(index),
                feelsLike = hourly.apparentTemperature.atOrLast(index),
                humidity = hourly.humidity.atOrLast(index),
                windSpeed = hourly.windSpeed.atOrLast(index),
                precipitationProbability = hourly.precipitationProbability.atOrLast(index),
                weatherCode = WeatherCode.fromCode(hourly.weatherCode.atOrLast(index)),
                uvIndex = hourly.uvIndex.atOrLast(index)
            )
        }
    }

    /** Convenience: one fetch + hourly-list parse. */
    suspend fun getHourlyForecast(
        latitude: Double,
        longitude: Double
    ): Result<List<HourlyForecast>> =
        getForecast(latitude, longitude).mapCatching { parseHourlyForecast(it) }

    suspend fun searchLocation(query: String): Result<List<LocationResult>> = runCatching {
        val results = nominatimApi.searchLocation(query)
        // GEN-3 X-12: dedupe by place identity at construction,
        // BEFORE the USA sort and the take(5), so a duplicate can
        // never crowd a distinct result out of the shown five.
        dedupeByPlaceIdentity(results.map { it.toLocationResult() })
            .sortedByDescending { it.isUSA } // USA results first
            .take(5) // Limit to 5 results like PWA
    }
    
    /**
     * WS-4 (RC-4): the reverse geocode, as a [SimplePlace] — the ONE
     * derivation of the simple form (city + abbreviated region) from
     * the geocoder's raw parts. Every place-bearing surface consumes
     * this value's display string; the raw geocode string
     * ("New York, New York") never leaves this boundary.
     */
    suspend fun reverseGeocodePlace(latitude: Double, longitude: Double): Result<com.runwear.shared.domain.model.SimplePlace> = runCatching {
        val response = nominatimApi.reverseGeocode(
            geocodeCoordinate(latitude),
            geocodeCoordinate(longitude)
        )
        val address = response.address

        val city = address?.city ?: address?.town ?: address?.village
            ?: address?.municipality ?: address?.suburb ?: ""
        val state = address?.state ?: address?.region ?: ""

        when {
            city.isNotEmpty() -> com.runwear.shared.domain.model.SimplePlace.fromGeocode(
                city = city,
                region = state.takeIf { it.isNotEmpty() },
                countryCode = address?.countryCode
            )
            response.displayName != null -> com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString(response.displayName.split(",").take(2).joinToString(", "))
            else -> com.runwear.shared.domain.model.SimplePlace("Your Location")
        }
    }

    /** The simple place's display string (see [reverseGeocodePlace]). */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        reverseGeocodePlace(latitude, longitude).mapCatching { it.display }
    
    /**
     * Parse an Open-Meteo local timestamp into the models' kotlinx
     * LocalDateTime. The API emits "yyyy-MM-dd'T'HH:mm" (no seconds);
     * java.time's ISO parser accepted that form implicitly, so normalize
     * by appending ":00" when the seconds field is absent.
     */
    private fun parseApiLocalDateTime(value: String): LocalDateTime {
        val normalized = if (value.length == 16) "$value:00" else value
        return LocalDateTime.parse(normalized)
    }

    private fun com.runwear.shared.data.model.NominatimSearchResult.toLocationResult() = LocationResult(
        latitude = latitude,
        longitude = longitude,
        name = cityName,
        displayName = formattedDisplayName,
        detail = fullLocationDetail,
        isUSA = isUSA
    )
}

/**
 * GEN-5 (RW-29 FA-15): a bounded parallel-array read — the value
 * at [index] when the series is that long, else the series' last
 * value, else the zero for the element type. A truncated series
 * degrades; it never throws.
 */
private fun List<Double>.atOrLast(index: Int): Double =
    getOrElse(index) { lastOrNull() ?: 0.0 }

private fun List<Int>.atOrLast(index: Int): Int =
    getOrElse(index) { lastOrNull() ?: 0 }
