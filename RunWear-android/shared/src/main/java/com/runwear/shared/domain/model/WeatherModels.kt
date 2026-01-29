package com.runwear.shared.domain.model

import java.time.LocalDateTime

data class WeatherConditions(
    val dateTime: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val precipitationProbability: Int,
    val precipitation: Double,
    val isRaining: Boolean,
    val isSnowing: Boolean,
    val cloudCover: Int,
    val uvIndex: Double,
    val weatherCode: WeatherCode,
    val isCelsius: Boolean
) {
    val effectiveTemperature: Double get() = feelsLike
    val isSunny: Boolean get() = cloudCover < 50 && uvIndex > 2
    val isWindy: Boolean get() = windSpeed > 10
    val isHumid: Boolean get() = humidity > 65
    
    val temperatureInFahrenheit: Double get() = if (isCelsius) temperature * 9 / 5 + 32 else temperature
    val feelsLikeInFahrenheit: Double get() = if (isCelsius) feelsLike * 9 / 5 + 32 else feelsLike
}

data class HourlyForecast(
    val dateTime: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val precipitationProbability: Int,
    val weatherCode: WeatherCode,
    val uvIndex: Double,
    val isCelsius: Boolean
) {
    val hour: Int get() = dateTime.hour
    val temperatureInFahrenheit: Double get() = if (isCelsius) temperature * 9 / 5 + 32 else temperature
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

enum class TemperatureUnit(val symbol: String) {
    FAHRENHEIT("°F"),
    CELSIUS("°C");
    
    fun convert(tempFahrenheit: Double): Double = when (this) {
        FAHRENHEIT -> tempFahrenheit
        CELSIUS -> (tempFahrenheit - 32) * 5 / 9
    }
    
    fun format(temp: Double): String = "${temp.toInt()}$symbol"
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
