package com.runwear.shared.domain.usecase

import com.runwear.shared.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THE BRAIN: Clothing Recommendation Engine
 * 
 * Based on research from Runner's World, Nike, and running coaches:
 * - "Dress like it's 15-20°F warmer than actual temperature"
 * - Wind reduces effective temp by 5-10°F
 * - Rain requires dressing for 5-10°F cooler
 * - High humidity needs looser, more breathable clothes
 */
@Singleton
class GetOutfitRecommendationUseCase @Inject constructor() {
    
    fun execute(weather: WeatherConditions, comfort: ComfortPreference = ComfortPreference.NEUTRAL): OutfitRecommendation {
        // Convert to Fahrenheit for consistent logic (engine uses F internally)
        val baseTemp = if (weather.isCelsius) weather.feelsLike * 9 / 5 + 32 else weather.feelsLike
        val adjustedTemp = baseTemp + comfort.tempAdjustment
        
        return OutfitRecommendation(
            weather = weather,
            topBase = selectTopBase(adjustedTemp),
            topOuter = selectTopOuter(adjustedTemp, weather),
            bottom = selectBottom(adjustedTemp, weather),
            head = selectHead(adjustedTemp, weather),
            hands = selectHands(adjustedTemp, weather),
            accessories = selectAccessories(weather, adjustedTemp),
            tips = generateTips(weather, adjustedTemp)
        )
    }
    
    private fun selectTopBase(temp: Double): ClothingItem = when {
        temp >= 70 -> ClothingItem.TANK_TOP
        temp >= 60 -> ClothingItem.SHORT_SLEEVE
        temp >= 45 -> ClothingItem.LONG_SLEEVE_LIGHT
        temp >= 30 -> ClothingItem.LONG_SLEEVE_LIGHT
        else -> ClothingItem.LONG_SLEEVE_THERMAL
    }
    
    private fun selectTopOuter(temp: Double, weather: WeatherConditions): ClothingItem? {
        if (weather.isRaining && weather.precipitation > 0.1) return ClothingItem.RAIN_JACKET
        if (weather.isWindy && temp in 40.0..60.0) return ClothingItem.WINDBREAKER
        
        return when {
            temp >= 60 -> null
            temp >= 50 -> if (weather.isWindy) ClothingItem.LIGHT_VEST else null
            temp >= 40 -> ClothingItem.LIGHT_JACKET
            temp >= 25 -> ClothingItem.LIGHT_JACKET
            else -> ClothingItem.INSULATED_JACKET
        }
    }
    
    private fun selectBottom(temp: Double, weather: WeatherConditions): ClothingItem {
        val humidityBonus = if (weather.isHumid && temp > 50) 5 else 0
        val effectiveTemp = temp + humidityBonus
        
        return when {
            effectiveTemp >= 75 -> ClothingItem.SHORT_SHORTS
            effectiveTemp >= 60 -> ClothingItem.SHORTS
            effectiveTemp >= 50 -> ClothingItem.SHORTS
            effectiveTemp >= 40 -> ClothingItem.LIGHT_TIGHTS
            else -> ClothingItem.THERMAL_TIGHTS
        }
    }
    
    private fun selectHead(temp: Double, weather: WeatherConditions): ClothingItem? {
        if (temp >= 60 && weather.isSunny) return ClothingItem.BASEBALL_CAP
        if (weather.isRaining) return ClothingItem.BASEBALL_CAP
        
        return when {
            temp >= 50 -> null
            temp >= 40 -> if (weather.isWindy) ClothingItem.HEADBAND else null
            temp >= 30 -> ClothingItem.HEADBAND
            temp >= 20 -> ClothingItem.LIGHT_BEANIE
            temp >= 5 -> ClothingItem.THERMAL_BEANIE
            else -> ClothingItem.BALACLAVA
        }
    }
    
    private fun selectHands(temp: Double, weather: WeatherConditions): ClothingItem? {
        val windPenalty = if (weather.isWindy) -5 else 0
        val effectiveTemp = temp + windPenalty
        
        return when {
            effectiveTemp >= 45 -> null
            effectiveTemp >= 35 -> ClothingItem.LIGHT_GLOVES
            effectiveTemp >= 20 -> ClothingItem.THERMAL_GLOVES
            else -> ClothingItem.MITTENS
        }
    }
    
    private fun selectAccessories(weather: WeatherConditions, temp: Double): List<ClothingItem> {
        val accessories = mutableListOf<ClothingItem>()
        if (weather.isSunny || weather.uvIndex >= 3) accessories.add(ClothingItem.SUNGLASSES)
        if (weather.uvIndex >= 3) accessories.add(ClothingItem.SUNSCREEN)
        if (weather.cloudCover > 80 || weather.weatherCode.isPrecipitation) accessories.add(ClothingItem.REFLECTIVE_GEAR)
        if (temp < 30 && weather.isWindy) accessories.add(ClothingItem.NECK_GAITER)
        return accessories
    }
    
    private fun generateTips(weather: WeatherConditions, temp: Double): List<String> {
        val tips = mutableListOf<String>()
        
        when {
            temp >= 80 -> {
                tips.add("🌡️ High heat risk — consider running early morning or evening")
                tips.add("💧 Hydrate well before, during, and after your run")
            }
            temp >= 70 -> tips.add("💧 Stay hydrated — consider carrying water")
            temp < 20 -> {
                tips.add("🥶 Extreme cold — keep your run shorter and stay close to home")
                tips.add("🏠 Change out of wet clothes immediately after finishing")
            }
            temp < 32 -> tips.add("❄️ Watch for ice on paths and roads")
        }
        
        if (weather.isWindy) tips.add("💨 Run into the wind first, return with wind at your back")
        if (weather.isRaining) {
            tips.add("🌧️ Wear a hat to keep rain out of your eyes")
            if (temp < 50) tips.add("⚠️ You'll get cold faster when wet — dress warmer")
        }
        if (weather.isHumid && temp > 65) {
            tips.add("💦 High humidity — wear looser clothes for better airflow")
            tips.add("🌍 Expect slower paces — your body works harder to cool down")
        }
        if (weather.uvIndex >= 6) tips.add("☀️ Very high UV — reapply sunscreen every 2 hours")
        
        return tips.take(3)
    }
}
