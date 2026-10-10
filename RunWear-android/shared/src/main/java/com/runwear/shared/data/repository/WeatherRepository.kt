package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val displayName: String,
    val detail: String = "",
    val isUSA: Boolean = false
)

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val nominatimApi: NominatimApi
) {
    
    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
    ): Result<WeatherConditions> = runCatching {
        val tempUnit = if (unit == TemperatureUnit.CELSIUS) "celsius" else "fahrenheit"
        val windUnit = if (unit == TemperatureUnit.CELSIUS) "kmh" else "mph"
        
        val response = weatherApi.getWeather(
            latitude = latitude,
            longitude = longitude,
            temperatureUnit = tempUnit,
            windSpeedUnit = windUnit
        )
        
        val current = response.current ?: throw Exception("No current weather data")
        
        WeatherConditions(
            dateTime = LocalDateTime.now(),
            temperature = current.temperature,
            feelsLike = current.apparentTemperature,
            humidity = current.humidity,
            windSpeed = current.windSpeed,
            windGusts = current.windGusts,
            precipitationProbability = current.precipitationProbability,
            precipitation = current.precipitation,
            isRaining = current.rain > 0,
            isSnowing = current.snowfall > 0,
            cloudCover = current.cloudCover,
            uvIndex = current.uvIndex ?: 0.0,
            weatherCode = WeatherCode.fromCode(current.weatherCode),
            isCelsius = unit == TemperatureUnit.CELSIUS
        )
    }
    
    suspend fun getWeatherForDateTime(
        latitude: Double,
        longitude: Double,
        dateTime: LocalDateTime,
        unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
    ): Result<WeatherConditions> = runCatching {
        val tempUnit = if (unit == TemperatureUnit.CELSIUS) "celsius" else "fahrenheit"
        val windUnit = if (unit == TemperatureUnit.CELSIUS) "kmh" else "mph"
        
        val response = weatherApi.getWeather(
            latitude = latitude,
            longitude = longitude,
            temperatureUnit = tempUnit,
            windSpeedUnit = windUnit
        )
        
        val hourly = response.hourly ?: throw Exception("No hourly weather data")

        // Find the closest hour in the forecast. A miss used to silently fall
        // back to index 0 (potentially ~24h off); now it fails loudly so the
        // UI shows the error screen with retry instead of wrong data (F2).
        val targetHour = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"))
        val index = hourly.time.indexOfFirst { it.startsWith(targetHour) }
        if (index < 0) {
            android.util.Log.e(
                "WeatherRepository",
                "No hourly entry for targetHour=$targetHour; refusing silent index-0 fallback"
            )
            throw Exception("No forecast data for the selected time")
        }
        
        WeatherConditions(
            dateTime = dateTime,
            temperature = hourly.temperature[index],
            feelsLike = hourly.apparentTemperature[index],
            humidity = hourly.humidity[index],
            windSpeed = hourly.windSpeed[index],
            windGusts = hourly.windGusts[index],
            precipitationProbability = hourly.precipitationProbability[index],
            precipitation = hourly.precipitation[index],
            isRaining = hourly.rain[index] > 0,
            isSnowing = hourly.snowfall[index] > 0,
            cloudCover = hourly.cloudCover[index],
            uvIndex = hourly.uvIndex[index],
            weatherCode = WeatherCode.fromCode(hourly.weatherCode[index]),
            isCelsius = unit == TemperatureUnit.CELSIUS
        )
    }
    
    suspend fun getHourlyForecast(
        latitude: Double,
        longitude: Double,
        unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
    ): Result<List<HourlyForecast>> = runCatching {
        val tempUnit = if (unit == TemperatureUnit.CELSIUS) "celsius" else "fahrenheit"
        val windUnit = if (unit == TemperatureUnit.CELSIUS) "kmh" else "mph"
        
        val response = weatherApi.getWeather(
            latitude = latitude,
            longitude = longitude,
            temperatureUnit = tempUnit,
            windSpeedUnit = windUnit
        )
        
        val hourly = response.hourly ?: throw Exception("No hourly weather data")
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        hourly.time.mapIndexed { index, time ->
            HourlyForecast(
                dateTime = LocalDateTime.parse(time, formatter),
                temperature = hourly.temperature[index],
                feelsLike = hourly.apparentTemperature[index],
                humidity = hourly.humidity[index],
                windSpeed = hourly.windSpeed[index],
                precipitationProbability = hourly.precipitationProbability[index],
                weatherCode = WeatherCode.fromCode(hourly.weatherCode[index]),
                uvIndex = hourly.uvIndex[index],
                isCelsius = unit == TemperatureUnit.CELSIUS
            )
        }
    }
    
    suspend fun searchLocation(query: String): Result<List<LocationResult>> = runCatching {
        val results = nominatimApi.searchLocation(query)
        results
            .map { it.toLocationResult() }
            .sortedByDescending { it.isUSA } // USA results first
            .take(5) // Limit to 5 results like PWA
    }
    
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> = runCatching {
        val response = nominatimApi.reverseGeocode(latitude, longitude)
        val address = response.address
        
        val city = address?.city ?: address?.town ?: address?.village 
            ?: address?.municipality ?: address?.suburb ?: ""
        val state = address?.state ?: address?.region ?: ""
        
        when {
            city.isNotEmpty() && state.isNotEmpty() -> "$city, $state"
            city.isNotEmpty() -> city
            response.displayName != null -> response.displayName.split(",").take(2).joinToString(",").trim()
            else -> "Your Location"
        }
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
