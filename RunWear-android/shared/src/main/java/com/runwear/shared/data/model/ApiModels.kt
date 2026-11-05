package com.runwear.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null,
    // GEN-6 WAVE C (RW-53(ii)): the daily block (sunrise,sunset) —
    // the same forecast payload already carries it; the app simply
    // never requested or parsed it before.
    val daily: DailyWeather? = null
)

@Serializable
data class DailyWeather(
    val time: List<String> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList()
)

@Serializable
data class CurrentWeather(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    val precipitation: Double,
    val rain: Double,
    val snowfall: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("wind_direction_10m") val windDirection: Int = 0,
    @SerialName("uv_index") val uvIndex: Double? = null,
    // Requested in the current= param list; default 0 keeps parsing safe if absent.
    @SerialName("precipitation_probability") val precipitationProbability: Int = 0
)

@Serializable
data class HourlyWeather(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("relative_humidity_2m") val humidity: List<Int>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>,
    val precipitation: List<Double>,
    val rain: List<Double>,
    val snowfall: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("cloud_cover") val cloudCover: List<Int>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double>,
    @SerialName("wind_direction_10m") val windDirection: List<Int> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double>
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null, // State/Province
    val admin2: String? = null, // County
    val admin3: String? = null,
    val admin4: String? = null
) {
    val displayName: String
        get() = buildString {
            append(name)
            admin1?.let { append(", $it") }
            country?.let { append(", $it") }
        }
}

@Serializable
data class ReverseGeocodingResponse(
    val address: Address? = null,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class Address(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val suburb: String? = null,
    val state: String? = null,
    val region: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null
)

@Serializable
data class NominatimSearchResult(
    val lat: String,
    val lon: String,
    @SerialName("display_name") val displayName: String,
    val address: Address? = null
) {
    val latitude: Double get() = lat.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = lon.toDoubleOrNull() ?: 0.0

    val cityName: String
        get() = address?.city ?: address?.town ?: address?.village
            ?: address?.municipality ?: address?.suburb
            ?: displayName.split(",").firstOrNull()?.trim() ?: ""

    val stateName: String
        get() = address?.state ?: address?.region ?: ""

    val countryName: String
        get() = address?.country ?: ""

    val isUSA: Boolean
        get() = address?.countryCode?.equals("us", ignoreCase = true) == true

    /**
     * WS-4: a search result's display name IS a simple place, derived
     * once here at the search boundary (city + abbreviated region) —
     * never the raw "City, Full State Name" geocode string.
     */
    val formattedDisplayName: String
        get() = com.runwear.shared.domain.model.SimplePlace.fromGeocode(
            city = cityName,
            region = stateName.takeIf { it.isNotEmpty() },
            countryCode = address?.countryCode
        ).display

    /** WS-4: this result as a [com.runwear.shared.domain.model.SimplePlace]. */
    val simplePlace: com.runwear.shared.domain.model.SimplePlace
        get() = com.runwear.shared.domain.model.SimplePlace.fromGeocode(
            city = cityName,
            region = stateName.takeIf { it.isNotEmpty() },
            countryCode = address?.countryCode
        )

    val fullLocationDetail: String
        get() = buildString {
            if (stateName.isNotEmpty()) append("$stateName, ")
            append(countryName)
        }
}
