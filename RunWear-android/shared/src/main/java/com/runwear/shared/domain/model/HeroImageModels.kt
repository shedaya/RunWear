package com.runwear.shared.domain.model

/**
 * Temperature bracket for hero image and UI color selection.
 * Brackets are based on typical running comfort zones.
 */
enum class TempBracket(
    val minF: Int,
    val maxF: Int,
    val description: String
) {
    HOT(80, 999, "hot summer"),
    WARM(65, 79, "warm"),
    MILD(50, 64, "mild"),
    COOL(35, 49, "cool autumn"),
    COLD(20, 34, "cold winter"),
    FREEZING(-99, 19, "freezing winter");

    companion object {
        fun fromFahrenheit(temp: Double): TempBracket {
            val intTemp = temp.toInt()
            return entries.find { intTemp in it.minF..it.maxF } ?: MILD
        }
    }
}

/**
 * Weather condition for hero image fallback selection.
 * Maps Open-Meteo weather codes to simplified conditions.
 */
enum class HeroWeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW;

    companion object {
        /**
         * Convert WeatherCode to simplified HeroWeatherCondition.
         * Based on Open-Meteo WMO weather codes.
         */
        fun fromWeatherCode(weatherCode: WeatherCode): HeroWeatherCondition {
            return when (weatherCode) {
                WeatherCode.CLEAR_SKY, WeatherCode.MAINLY_CLEAR -> CLEAR
                WeatherCode.PARTLY_CLOUDY, WeatherCode.OVERCAST,
                WeatherCode.FOG, WeatherCode.DEPOSITING_RIME_FOG -> CLOUDY
                WeatherCode.DRIZZLE_LIGHT, WeatherCode.DRIZZLE_MODERATE, WeatherCode.DRIZZLE_DENSE,
                WeatherCode.FREEZING_DRIZZLE_LIGHT, WeatherCode.FREEZING_DRIZZLE_DENSE,
                WeatherCode.RAIN_SLIGHT, WeatherCode.RAIN_MODERATE, WeatherCode.RAIN_HEAVY,
                WeatherCode.FREEZING_RAIN_LIGHT, WeatherCode.FREEZING_RAIN_HEAVY,
                WeatherCode.RAIN_SHOWERS_SLIGHT, WeatherCode.RAIN_SHOWERS_MODERATE, WeatherCode.RAIN_SHOWERS_VIOLENT,
                WeatherCode.THUNDERSTORM, WeatherCode.THUNDERSTORM_HAIL_SLIGHT, WeatherCode.THUNDERSTORM_HAIL_HEAVY -> RAIN
                WeatherCode.SNOW_SLIGHT, WeatherCode.SNOW_MODERATE, WeatherCode.SNOW_HEAVY, WeatherCode.SNOW_GRAINS,
                WeatherCode.SNOW_SHOWERS_SLIGHT, WeatherCode.SNOW_SHOWERS_HEAVY -> SNOW
                WeatherCode.UNKNOWN -> CLEAR
            }
        }
    }
}

/**
 * Time of day for hero image lighting conditions.
 */
enum class TimeOfDay(
    val description: String,
    val hourRange: IntRange
) {
    DAWN("early morning golden hour", 5..9),      // 5:00 - 9:59
    MIDDAY("bright midday", 10..16),              // 10:00 - 16:59
    DUSK("evening golden hour", 17..19),          // 17:00 - 19:59
    NIGHT("nighttime with streetlights", 20..23); // 20:00 - 4:59

    companion object {
        fun fromHour(hour: Int): TimeOfDay {
            // Handle wraparound for 0-4 AM (treat as night)
            val normalizedHour = if (hour < 5) 20 else hour
            return entries.find { normalizedHour in it.hourRange } ?: MIDDAY
        }
    }
}

/**
 * Represents a unique combination of conditions for hero image caching.
 * Each combination maps to a pool of pre-generated AI images.
 */
data class OutfitCombination(
    val id: String,
    val genderPreference: GenderPreference,
    val weatherCode: WeatherCode,
    val tempBracket: TempBracket,
    val timeOfDay: TimeOfDay,
    val outfitHash: String
) {
    companion object {
        /**
         * Create combination from current conditions.
         *
         * Combination ID Format (matches PWA/Supabase):
         * {GENDER}_{WEATHER}_{TEMP_BRACKET}_{TIME_OF_DAY}_{OUTFIT_HASH}
         *
         * Example: "FEMALE_RAIN_COLD_MIDDAY_a1b2c3d4"
         *
         * - GENDER: MALE, FEMALE, UNISEX
         * - WEATHER: CLEAR, CLOUDY, RAIN, SNOW (simplified from Open-Meteo codes)
         * - TEMP_BRACKET: HOT, WARM, MILD, COOL, COLD, FREEZING
         * - TIME_OF_DAY: DAWN, MIDDAY, DUSK, NIGHT
         * - OUTFIT_HASH: 8-char hex from sorted outfit item names
         */
        fun fromConditions(
            weather: WeatherConditions,
            outfit: OutfitRecommendation,
            gender: GenderPreference
        ): OutfitCombination {
            val tempBracket = TempBracket.fromFahrenheit(weather.feelsLikeInFahrenheit)
            val timeOfDay = TimeOfDay.fromHour(weather.dateTime.hour)
            val heroWeather = HeroWeatherCondition.fromWeatherCode(weather.weatherCode)

            // 8-char hex hash from sorted outfit item names (per spec)
            val outfitHash = outfit.allItems
                .map { it.name }
                .sorted()
                .joinToString("-")
                .hashCode()
                .let { Integer.toHexString(it).takeLast(8).padStart(8, '0') }

            // Combination ID format: {GENDER}_{WEATHER}_{TEMP}_{TIME}_{HASH}
            val combinationId = "${gender.name}_${heroWeather.name}_${tempBracket.name}_${timeOfDay.name}_$outfitHash"

            return OutfitCombination(
                id = combinationId,
                genderPreference = gender,
                weatherCode = weather.weatherCode,
                tempBracket = tempBracket,
                timeOfDay = timeOfDay,
                outfitHash = outfitHash
            )
        }
    }
}

/**
 * Result of hero image selection.
 * Contains URL or null if no cached image exists (fallback to gradient).
 */
data class HeroImageResult(
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val combinationId: String
)

/**
 * Static helper for synchronous hero image selection (fallback/placeholder).
 * For live AI images, use HeroImageRepository instead.
 */
object HeroImageSelector {

    /**
     * Weather-aware fallback images: 2D matrix of temp bracket × weather condition.
     * 6 temp brackets × 4 weather conditions = up to 24 unique fallback images.
     *
     * Image selection based on RunWear Hero Image System Specification.
     * Uses Unsplash photo IDs with w=800&h=1200&fit=crop dimensions.
     */
    private val FALLBACK_IMAGES_2D: Map<TempBracket, Map<HeroWeatherCondition, String>> = mapOf(
        // HOT (80°F+) - Snow impossible
        TempBracket.HOT to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop", // Tank top, bright sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=800&h=1200&fit=crop", // Summer overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1534258936925-c58bed479fcb?w=800&h=1200&fit=crop" // Summer rain
            // SNOW: null - Not possible when HOT
        ),
        // WARM (65-79°F) - Snow very unlikely
        TempBracket.WARM to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1486218119243-13883505764c?w=800&h=1200&fit=crop", // T-shirt sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1558017487-06bf9f82613a?w=800&h=1200&fit=crop", // Warm overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1534258936925-c58bed479fcb?w=800&h=1200&fit=crop" // Warm rain
            // SNOW: null - Very unlikely when WARM
        ),
        // MILD (50-64°F) - All conditions possible
        TempBracket.MILD to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop", // Light layers sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1558017487-06bf9f82613a?w=800&h=1200&fit=crop", // Mild overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1515191107209-c28698631303?w=800&h=1200&fit=crop", // Spring/fall rain
            HeroWeatherCondition.SNOW to "https://images.unsplash.com/photo-1491002052546-bf38f186af56?w=800&h=1200&fit=crop" // Light snow
        ),
        // COOL (35-49°F) - All conditions possible
        TempBracket.COOL to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800&h=1200&fit=crop", // Long sleeves sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=800&h=1200&fit=crop", // Cool overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1515191107209-c28698631303?w=800&h=1200&fit=crop", // Cool rainy
            HeroWeatherCondition.SNOW to "https://images.unsplash.com/photo-1491002052546-bf38f186af56?w=800&h=1200&fit=crop" // Cool snow
        ),
        // COLD (20-34°F) - All conditions possible
        TempBracket.COLD to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1517649763962-0c623066013b?w=800&h=1200&fit=crop", // Jacket sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1517649763962-0c623066013b?w=800&h=1200&fit=crop", // Cold overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=800&h=1200&fit=crop", // Cold rain/sleet
            HeroWeatherCondition.SNOW to "https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=800&h=1200&fit=crop" // Snowy run
        ),
        // FREEZING (<20°F) - All conditions possible
        TempBracket.FREEZING to mapOf(
            HeroWeatherCondition.CLEAR to "https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop", // Winter gear sunny
            HeroWeatherCondition.CLOUDY to "https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop", // Freezing overcast
            HeroWeatherCondition.RAIN to "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=800&h=1200&fit=crop", // Freezing rain/sleet
            HeroWeatherCondition.SNOW to "https://images.unsplash.com/photo-1418985991508-e47386d96a71?w=800&h=1200&fit=crop" // Heavy snow
        )
    )

    /**
     * Get a fallback/placeholder image synchronously.
     * Uses 2D matrix: temp bracket × weather condition.
     * Use HeroImageRepository.getHeroImage() for live AI images.
     */
    fun getFallbackImage(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX
    ): HeroImageResult {
        if (weather == null || outfit == null) {
            return HeroImageResult(
                imageUrl = null,
                thumbnailUrl = null,
                combinationId = "unknown"
            )
        }

        val combination = OutfitCombination.fromConditions(weather, outfit, gender)
        val weatherCondition = HeroWeatherCondition.fromWeatherCode(weather.weatherCode)
        val url = getWeatherAwareFallback(combination.tempBracket, weatherCondition)

        return HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id
        )
    }

    /**
     * Get weather-aware fallback image URL.
     */
    private fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String {
        // Try exact temp + weather match
        val bracketImages = FALLBACK_IMAGES_2D[tempBracket]
        val weatherImages = bracketImages?.get(weatherCondition)
        if (weatherImages != null) {
            return weatherImages
        }

        // Fall back to CLEAR for this temp bracket
        val clearFallback = bracketImages?.get(HeroWeatherCondition.CLEAR)
        if (clearFallback != null) {
            return clearFallback
        }

        // Ultimate fallback - mild + clear
        return FALLBACK_IMAGES_2D[TempBracket.MILD]?.get(HeroWeatherCondition.CLEAR)
            ?: "https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop"
    }

    /**
     * Get just the URL string for simple usage.
     */
    fun getImageUrl(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX
    ): String? {
        return getFallbackImage(weather, outfit, gender).imageUrl
    }
}
