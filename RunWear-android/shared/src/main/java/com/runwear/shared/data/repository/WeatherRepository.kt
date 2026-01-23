package com.runwear.shared.data.repository

import com.runwear.shared.data.api.GeocodingApi
import com.runwear.shared.data.api.NominatimApi
import com.runwear.shared.data.api.OpenMeteoApi
import com.runwear.shared.data.model.GeocodingResult
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
    val displayName: String
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
            precipitationProbability = 0,
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
        
        // Find the closest hour in the forecast
        val targetHour = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"))
        val index = hourly.time.indexOfFirst { it.startsWith(targetHour) }.takeIf { it >= 0 } ?: 0
        
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
        val response = geocodingApi.searchLocation(query)
        response.results?.map { it.toLocationResult() } ?: emptyList()
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
    
    private fun GeocodingResult.toLocationResult() = LocationResult(
        latitude = latitude,
        longitude = longitude,
        name = name,
        displayName = displayName
    )
}
