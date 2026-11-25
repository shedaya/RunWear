package com.runwear.shared.domain.model

import kotlinx.datetime.LocalDate

import kotlinx.datetime.LocalDateTime

/**
 * WS-2 (RC-2, D-05): weather is stored CANONICALLY — temperatures in
 * °F, wind in mph, precipitation in mm — and carries NO unit value.
 * The user's temperature-unit preference is a pure render-time
 * projection ([TemperatureUnit]) applied by the surface that draws a
 * figure, never a property of the data: a unit change therefore
 * re-renders everything in one frame and refetches nothing, and a
 * mixed-unit frame is unrepresentable.
 */
data class WeatherConditions(
    val dateTime: LocalDateTime,
    /** Canonical °F. */
    val temperature: Double,
    /** Canonical °F. */
    val feelsLike: Double,
    val humidity: Int,
    /** Canonical mph. */
    val windSpeed: Double,
    /** Canonical mph. */
    val windGusts: Double,
    val windDirection: Int = 0,
    val precipitationProbability: Int,
    /** Canonical mm (the API's native precipitation unit in both unit systems). */
    val precipitation: Double,
    val isRaining: Boolean,
    val isSnowing: Boolean,
    val cloudCover: Int,
    val uvIndex: Double,
    val weatherCode: WeatherCode
) {
    val effectiveTemperature: Double get() = feelsLike
    val isSunny: Boolean get() = cloudCover < 50 && uvIndex > 2
    /** Wind gate in the canonical frame: mph, matching the engine's frame contract. */
    val isWindy: Boolean get() = windSpeed > 10
    val isHumid: Boolean get() = humidity > 65

    /** The stored value itself — storage is canonical °F (WS-2). */
    val temperatureInFahrenheit: Double get() = temperature
    /** The stored value itself — storage is canonical °F (WS-2). */
    val feelsLikeInFahrenheit: Double get() = feelsLike
}

data class HourlyForecast(
    val dateTime: LocalDateTime,
    /** Canonical °F. */
    val temperature: Double,
    /** Canonical °F. */
    val feelsLike: Double,
    val humidity: Int,
    /** Canonical mph. */
    val windSpeed: Double,
    val precipitationProbability: Int,
    val weatherCode: WeatherCode,
    val uvIndex: Double
) {
    val hour: Int get() = dateTime.hour
    /** The stored value itself — storage is canonical °F (WS-2). */
    val temperatureInFahrenheit: Double get() = temperature
}

enum class WeatherCode(val code: Int, val description: String, val icon: String) {
    CLEAR_SKY(0, "Clear sky", "☀️"),
    MAINLY_CLEAR(1, "Mainly clear", "🌤️"),
    PARTLY_CLOUDY(2, "Partly cloudy", "⛅"),
    OVERCAST(3, "Overcast", "☁️"),
    FOG(45, "Fog", "🌫️"),
    DEPOSITING_RIME_FOG(48, "Depositing rime fog", "🌫️"),
    DRIZZLE_LIGHT(51, "Light drizzle", "🌧️"),
    DRIZZLE_MODERATE(53, "Moderate drizzle", "🌧️"),
    DRIZZLE_DENSE(55, "Dense drizzle", "🌧️"),
    FREEZING_DRIZZLE_LIGHT(56, "Light freezing drizzle", "🌨️"),
    FREEZING_DRIZZLE_DENSE(57, "Dense freezing drizzle", "🌨️"),
    RAIN_SLIGHT(61, "Slight rain", "🌧️"),
    RAIN_MODERATE(63, "Moderate rain", "🌧️"),
    RAIN_HEAVY(65, "Heavy rain", "🌧️"),
    FREEZING_RAIN_LIGHT(66, "Light freezing rain", "🌨️"),
    FREEZING_RAIN_HEAVY(67, "Heavy freezing rain", "🌨️"),
    SNOW_SLIGHT(71, "Slight snow", "🌨️"),
    SNOW_MODERATE(73, "Moderate snow", "🌨️"),
    SNOW_HEAVY(75, "Heavy snow", "🌨️"),
    SNOW_GRAINS(77, "Snow grains", "🌨️"),
    RAIN_SHOWERS_SLIGHT(80, "Slight rain showers", "🌦️"),
    RAIN_SHOWERS_MODERATE(81, "Moderate rain showers", "🌦️"),
    RAIN_SHOWERS_VIOLENT(82, "Violent rain showers", "⛈️"),
    SNOW_SHOWERS_SLIGHT(85, "Slight snow showers", "🌨️"),
    SNOW_SHOWERS_HEAVY(86, "Heavy snow showers", "🌨️"),
    THUNDERSTORM(95, "Thunderstorm", "⛈️"),
    THUNDERSTORM_HAIL_SLIGHT(96, "Thunderstorm with slight hail", "⛈️"),
    THUNDERSTORM_HAIL_HEAVY(99, "Thunderstorm with heavy hail", "⛈️"),
    UNKNOWN(-1, "Unknown", "❓");
    
    companion object {
        fun fromCode(code: Int): WeatherCode = entries.find { it.code == code } ?: UNKNOWN
    }
    
    val isPrecipitation: Boolean get() = code in 51..99
    val isRain: Boolean get() = code in 51..67 || code in 80..82 || code >= 95
    val isSnow: Boolean get() = code in 71..77 || code in 85..86
}

/**
 * The user's display-unit preference — and, since WS-2, ONLY that.
 * It is never stored inside a weather object, a cache key, or an
 * image request; it lives in the preferences store and is applied
 * here, at render time, to canonical values (°F temperatures, mph
 * wind). Every figure a surface draws goes through these functions,
 * so one preference value converts the whole screen in one frame.
 */
enum class TemperatureUnit(val symbol: String) {
    FAHRENHEIT("°F"),
    CELSIUS("°C");

    /** Project a canonical °F temperature into this unit. */
    fun convert(tempFahrenheit: Double): Double = when (this) {
        FAHRENHEIT -> tempFahrenheit
        CELSIUS -> (tempFahrenheit - 32) * 5 / 9
    }

    /** The displayed integer for a canonical °F temperature. */
    fun displayValue(tempFahrenheit: Double): Int = convert(tempFahrenheit).toInt()

    /** A canonical °F temperature rendered in this unit, e.g. "42°F" / "6°C". */
    fun formatTemperature(tempFahrenheit: Double): String = "${displayValue(tempFahrenheit)}$symbol"

    /** Legacy alias for [formatTemperature]; the argument is canonical °F. */
    fun format(tempFahrenheit: Double): String = formatTemperature(tempFahrenheit)

    /** The wind-speed label that pairs with this unit. */
    val windLabel: String
        get() = when (this) {
            FAHRENHEIT -> "mph"
            CELSIUS -> "km/h"
        }

    /** Project a canonical mph wind speed into this unit's speed frame. */
    fun convertWind(speedMph: Double): Double = when (this) {
        FAHRENHEIT -> speedMph
        CELSIUS -> speedMph * 1.609344
    }

    /** A canonical mph wind speed rendered in this unit, e.g. "4 mph" / "7 km/h". */
    fun formatWind(speedMph: Double): String = "${convertWind(speedMph).toInt()} $windLabel"
}

/**
 * Comfort preference for outfit recommendations.
 * PWA v2.9 labels: "Run Cold" / "Slightly Cold" / "Neutral" / "Slightly Hot" / "Run Hot"
 * Maps to temperature adjustments: -10, -5, 0, +5, +10
 */
enum class ComfortPreference(val tempAdjustment: Int, val label: String, val shortLabel: String) {
    RUNS_VERY_COLD(-10, "I get cold very easily", "Run Cold"),
    RUNS_COLD(-5, "I tend to get cold", "Slightly Cold"),
    NEUTRAL(0, "Neither", "Neutral"),
    RUNS_WARM(5, "I tend to overheat", "Slightly Hot"),
    RUNS_VERY_WARM(10, "I overheat very easily", "Run Hot")
}

/**
 * Gender/fit preference for affiliate search.
 * PWA v2.9: Only "Male" and "Female" shown as options.
 * UNISEX is the default when neither is selected (toggleable behavior).
 */
enum class GenderPreference(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    UNISEX("") // Empty label - this is the "deselected" state
}

/**
 * GEN-6 WAVE C (RW-53(ii)): one day's sunrise/sunset, in minutes
 * since local midnight, parsed from the forecast payload's `daily`
 * block (sunrise,sunset). The day chart's night shading follows
 * these; the fixed 20:00–06:00 ranges remain only as the fallback
 * when a payload carries no daily block.
 */
data class DaySunTimes(
    val date: LocalDate,
    val sunriseMinutes: Int,
    val sunsetMinutes: Int
)
