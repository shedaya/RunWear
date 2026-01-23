package com.runwear.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null
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
    @SerialName("uv_index") val uvIndex: Double? = null
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
