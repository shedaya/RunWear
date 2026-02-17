package com.runwear.shared.domain.usecase

import com.runwear.shared.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THE BRAIN: Running-Specific Clothing Recommendation Engine (v4.1)
 *
 * Brackets are mapped directly from real feels-like temperature to running-appropriate clothing.
 * No arithmetic offset — the thresholds themselves reflect that runners generate 5-15× resting
 * metabolic heat. The "Rule of 20" is baked into the brackets, not applied as math.
 *
 * Sources: Nike, Jeff Galloway (US Olympian), Marathon Handbook, Tina Muir (2:36 marathoner),
 * Fleet Feet, Luke Humphrey (Hanson's Brooks Distance Project), NCBI exercise physiology.
 *
 * Key insight: No credible running source recommends an insulated jacket at 20°F.
 * That threshold sits at 0-10°F for active runners.
 */
@Singleton
class GetOutfitRecommendationUseCase @Inject constructor() {

    fun execute(weather: WeatherConditions, comfort: ComfortPreference = ComfortPreference.NEUTRAL): OutfitRecommendation {
        // Convert to Fahrenheit for consistent logic (engine uses F internally)
        val baseTemp = if (weather.isCelsius) weather.feelsLike * 9 / 5 + 32 else weather.feelsLike
        // Comfort adjustment only: "runs hot" (+10) shifts up, "runs cold" (-10) shifts down
        // No separate running offset — the bracket thresholds are already running-specific.
        val adjustedTemp = baseTemp + comfort.tempAdjustment

        return OutfitRecommendation(
            weather = weather,
            topBase = selectTopBase(adjustedTemp),
            topMid = selectTopMid(adjustedTemp),
            topOuter = selectTopOuter(adjustedTemp, weather),
            bottom = selectBottom(adjustedTemp),
            head = selectHead(adjustedTemp, weather),
            hands = selectHands(adjustedTemp),
            accessories = selectAccessories(weather, adjustedTemp),
            tips = generateTips(weather, adjustedTemp)
        )
    }

    // Runners wear short sleeves down to 50°F — body heat makes up the difference
    private fun selectTopBase(temp: Double): ClothingItem = when {
        temp >= 80 -> ClothingItem.TANK_TOP
        temp >= 50 -> ClothingItem.SHORT_SLEEVE
        temp >= 20 -> ClothingItem.LONG_SLEEVE_LIGHT
        else -> ClothingItem.LONG_SLEEVE_THERMAL
    }

    // No mid-layer above 40°F. Below that, progressively warmer mid-layers.
    private fun selectTopMid(temp: Double): ClothingItem? = when {
        temp >= 40 -> null
        temp >= 20 -> ClothingItem.QUARTER_ZIP
        temp >= 10 -> ClothingItem.FLEECE_PULLOVER
        else -> ClothingItem.HALF_ZIP_THERMAL
    }

    // Rain always gets rain jacket. Wind shell beats heavy jacket for running.
    // Insulated jacket ONLY below 10°F — the single biggest change from old engine.
    private fun selectTopOuter(temp: Double, weather: WeatherConditions): ClothingItem? {
        if (weather.isRaining && weather.precipitation > 0.1) return ClothingItem.RAIN_JACKET

        return when {
            temp >= 50 -> null
            temp >= 40 -> if (weather.isWindy) ClothingItem.WINDBREAKER else null
            temp >= 10 -> ClothingItem.WINDBREAKER  // Wind shell for 10-39°F — NOT insulated
            else -> ClothingItem.INSULATED_JACKET    // Insulated ONLY below 10°F
        }
    }

    // Runners wear shorts down to ~45°F — legs generate plenty of heat
    private fun selectBottom(temp: Double): ClothingItem = when {
        temp >= 80 -> ClothingItem.SHORT_SHORTS
        temp >= 45 -> ClothingItem.SHORTS
        temp >= 30 -> ClothingItem.LIGHT_TIGHTS
        else -> ClothingItem.THERMAL_TIGHTS
    }

    private fun selectHead(temp: Double, weather: WeatherConditions): ClothingItem? {
        if (temp >= 65 && weather.uvIndex > 3) return ClothingItem.VISOR
        if (temp >= 50 && weather.uvIndex > 3) return ClothingItem.BASEBALL_CAP
        if (weather.isRaining) return ClothingItem.BASEBALL_CAP

        return when {
            temp >= 40 -> null
            temp >= 30 -> ClothingItem.HEADBAND
            temp >= 15 -> ClothingItem.LIGHT_BEANIE
            temp >= 5 -> ClothingItem.THERMAL_BEANIE
            else -> ClothingItem.BALACLAVA
        }
    }

    private fun selectHands(temp: Double): ClothingItem? = when {
        temp >= 40 -> null
        temp >= 25 -> ClothingItem.LIGHT_GLOVES
        temp >= 15 -> ClothingItem.THERMAL_GLOVES
        else -> ClothingItem.MITTENS
    }

    private fun selectAccessories(weather: WeatherConditions, temp: Double): List<ClothingItem> {
        val accessories = mutableListOf<ClothingItem>()
        if (weather.uvIndex > 3) accessories.add(ClothingItem.SUNGLASSES)
        if (weather.uvIndex > 5) accessories.add(ClothingItem.SUNSCREEN)
        if (temp < 30) accessories.add(ClothingItem.NECK_GAITER)
        // Reflective gear for dawn/dusk/night
        val hour = weather.dateTime.hour
        if (hour < 7 || hour > 18) accessories.add(ClothingItem.REFLECTIVE_GEAR)
        return accessories
    }

    private fun generateTips(weather: WeatherConditions, temp: Double): List<String> {
        val tips = mutableListOf<String>()

        // The key running tip — always show below 50°F
        if (temp < 50) {
            tips.add("🏃 You should feel slightly cool stepping outside. If you're comfortable standing still, you're overdressed for running.")
        }

        // 3-layer ventilation tip
        if (temp < 40 && temp >= 10) {
            tips.add("🧥 Your layered setup lets you regulate heat — unzip your mid-layer or shell if you warm up after the first mile.")
        }

        // Wind tip
        if (weather.isWindy && temp < 50) {
            tips.add("💨 A wind shell beats a heavy jacket for running. It blocks wind chill while letting you vent excess heat.")
        }

        // Extreme cold
        if (temp < 10) {
            tips.add("🥶 Cover all exposed skin — frostbite risk increases below 10°F with wind. Vaseline on cheeks and nose helps.")
        }

        // Heat
        when {
            temp >= 80 -> {
                tips.add("🌡️ High heat risk — consider running early morning or evening.")
                tips.add("💧 Hydrate well before, during, and after your run.")
            }
            temp >= 70 -> tips.add("💧 Stay hydrated — consider carrying water.")
        }

        // Rain
        if (weather.isRaining) {
            tips.add("🌧️ Wear a cap under your hood to keep rain out of your eyes. Avoid cotton — it retains moisture.")
        }

        // Humidity
        if (weather.isHumid && temp > 60) {
            tips.add("💦 High humidity — sweat won't evaporate easily. Wear looser, lighter fabrics and hydrate more.")
        }

        // UV
        if (weather.uvIndex > 6) tips.add("☀️ UV is high — reapply sunscreen every 60-90 minutes if running longer than an hour.")

        // Shorts in cold encouragement
        if (temp in 40.0..49.0) {
            tips.add("🩳 Many runners still wear shorts at this temperature. Your legs generate plenty of heat — try it before adding tights.")
        }

        return tips.take(3)
    }
}
