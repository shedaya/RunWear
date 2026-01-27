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
 * Time of day for hero image lighting conditions.
 */
enum class TimeOfDay(
    val description: String,
    val hourRange: IntRange
) {
    DAWN("early morning golden hour", 5..8),
    MIDDAY("bright midday", 9..16),
    DUSK("evening golden hour", 17..20),
    NIGHT("nighttime with streetlights", 21..23);

    companion object {
        fun fromHour(hour: Int): TimeOfDay {
            // Handle wraparound for 0-4 AM (treat as night)
            val normalizedHour = if (hour < 5) 21 else hour
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
        fun fromConditions(
            weather: WeatherConditions,
            outfit: OutfitRecommendation,
            gender: GenderPreference
        ): OutfitCombination {
            val tempBracket = TempBracket.fromFahrenheit(weather.feelsLikeInFahrenheit)
            val timeOfDay = TimeOfDay.fromHour(weather.dateTime.hour)
            val outfitHash = outfit.allItems
                .map { it.name }
                .sorted()
                .joinToString("-")
                .hashCode()
                .toString()

            val combinedHash = "${gender.name}_${weather.weatherCode.name}_${tempBracket.name}_${timeOfDay.name}_$outfitHash"

            return OutfitCombination(
                id = combinedHash.hashCode().toString(),
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
 * Selects appropriate hero images based on weather and outfit conditions.
 *
 * Currently returns a placeholder URL. When the Supabase backend is ready,
 * this will query the image library for cached AI-generated images.
 */
object HeroImageSelector {

    // Placeholder image for development - portrait orientation runner
    private const val PLACEHOLDER_URL =
        "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop&crop=center"

    // Alternate placeholders for variety (unused for now, kept for future random selection)
    @Suppress("unused")
    private val PLACEHOLDER_URLS = listOf(
        "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop&crop=center",
        "https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop&crop=center",
        "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800&h=1200&fit=crop&crop=center"
    )

    /**
     * Select a hero image for the phone app (full-height hero).
     *
     * @param weather Current weather conditions
     * @param outfit Current outfit recommendation
     * @param gender User's gender preference (for matching runner in image)
     * @return HeroImageResult with URL or null for fallback gradient
     */
    fun selectImage(
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

        // TODO: Query Supabase image library here
        // For now, return placeholder
        return HeroImageResult(
            imageUrl = PLACEHOLDER_URL,
            thumbnailUrl = PLACEHOLDER_URL,
            combinationId = combination.id
        )
    }

    /**
     * Select a hero image for the watch app (compact, upper-body crop).
     */
    fun selectWatchImage(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX
    ): HeroImageResult {
        // Same logic but could use different crop parameters
        return selectImage(weather, outfit, gender)
    }

    /**
     * Get just the URL string for simple usage.
     */
    fun getImageUrl(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX
    ): String? {
        return selectImage(weather, outfit, gender).imageUrl
    }
}
