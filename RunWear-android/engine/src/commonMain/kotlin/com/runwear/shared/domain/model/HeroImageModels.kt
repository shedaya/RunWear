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

            // 8-char hex hash from sorted outfit item names (per spec).
            // Common-code replacement for java's Integer.toHexString with
            // identical semantics: String.hashCode() is the same 31-based
            // UTF-16 hash on every Kotlin target, and toUInt().toString(16)
            // renders the same unsigned lowercase hex Integer.toHexString
            // produced (two's-complement form for negative hashes).
            val outfitHash = outfit.allItems
                .map { it.name }
                .sorted()
                .joinToString("-")
                .hashCode()
                .let { it.toUInt().toString(16).takeLast(8).padStart(8, '0') }

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
    val combinationId: String,
    /**
     * Supabase `generated_images` row id, set only when the image was
     * served from the pool. Null for fallback/curated images (bundled assets),
     * which have no pool row. Hero-flag reports key off this: a non-null
     * value identifies the exact photo a user is looking at.
     */
    val imageRowId: String? = null,
    /**
     * The served pool row's own combination_id. Differs from
     * [combinationId] (the *requested* combination) when the selection
     * cascade fell back to a neighbouring cell (different time of day or
     * the opposite gender). Null for fallback/curated images.
     */
    val servedCombinationId: String? = null,
    /**
     * Every servable (review-approved) image for the cell this result
     * was drawn from, in pool order. Powers the hero refresh control:
     * tapping it cycles to the next candidate for the SAME cell without
     * flagging the current one. Empty when the result did not come from
     * a pool lookup (pure fallback paths fill it with the single
     * fallback candidate or leave it empty).
     */
    val candidates: List<HeroCandidate> = emptyList()
)

/**
 * One servable hero image for a cell — the unit the refresh control
 * cycles through. [imageRowId] / [servedCombinationId] carry the same
 * flag-report identity as the parent [HeroImageResult] fields, so a
 * flag raised after a refresh targets the photo actually on screen.
 */
data class HeroCandidate(
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val imageRowId: String? = null,
    val servedCombinationId: String? = null
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
     * Since 2026-10-29 every cell is a bundled asset (in the shared module's
     * assets/hero_fallback/) derived from an approved generated_images pool
     * row, so the fallback renders fully offline; before that the cells were
     * remote Unsplash stock URLs. Keep in sync with HeroImageRepository's
     * copy of this table (a unit test asserts the two agree).
     */
    private val FALLBACK_IMAGES_2D: Map<TempBracket, Map<HeroWeatherCondition, String>> = mapOf(
        // HOT (80°F+) - Snow impossible
        TempBracket.HOT to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/hot_clear.webp", // Tank top, bright sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/hot_cloudy.webp", // Summer overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/hot_rain.webp" // Summer rain
            // SNOW: null - Not possible when HOT
        ),
        // WARM (65-79°F) - Snow very unlikely
        TempBracket.WARM to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/warm_clear.webp", // T-shirt sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/warm_cloudy.webp", // Warm overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/warm_rain.webp" // Warm rain
            // SNOW: null - Very unlikely when WARM
        ),
        // MILD (50-64°F) - All conditions possible
        TempBracket.MILD to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/mild_clear.webp", // Light layers sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/mild_cloudy.webp", // Mild overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/mild_rain.webp", // Spring/fall rain
            HeroWeatherCondition.SNOW to "file:///android_asset/hero_fallback/mild_snow.webp" // Light snow
        ),
        // COOL (35-49°F) - All conditions possible
        TempBracket.COOL to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/cool_clear.webp", // Long sleeves sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/cool_cloudy.webp", // Cool overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/cool_rain.webp", // Cool rainy
            HeroWeatherCondition.SNOW to "file:///android_asset/hero_fallback/cool_snow.webp" // Cool snow
        ),
        // COLD (20-34°F) - All conditions possible
        TempBracket.COLD to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/cold_clear.webp", // Jacket sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/cold_cloudy.webp", // Cold overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/cold_rain.webp", // Cold rain/sleet
            HeroWeatherCondition.SNOW to "file:///android_asset/hero_fallback/cold_snow.webp" // Snowy run
        ),
        // FREEZING (<20°F) - All conditions possible
        TempBracket.FREEZING to mapOf(
            HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/freezing_clear.webp", // Winter gear sunny
            HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/freezing_cloudy.webp", // Freezing overcast
            HeroWeatherCondition.RAIN to "file:///android_asset/hero_fallback/freezing_rain.webp", // Freezing rain/sleet
            HeroWeatherCondition.SNOW to "file:///android_asset/hero_fallback/freezing_snow.webp" // Heavy snow
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
     * Get the weather-aware fallback image reference for a cell.
     * Public so unit tests can assert every cell resolves to a bundled
     * pool-derived asset and both copies of the table agree.
     */
    fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String {
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
            ?: "file:///android_asset/hero_fallback/mild_clear.webp"
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

/**
 * The hero combination mappings in frame form — the Apple-platform seam for
 * the hero decision logic. The Swift hero pipeline builds combination-ID
 * prefix patterns ({GENDER}_{WEATHER}_{TEMP}_{TIME}, no outfit hash), so it
 * consumes the three classification mappings by NAME and keeps its own
 * pattern assembly and pool cascade; the mapping tables themselves (WMO
 * code → condition class, feels-like → bracket, hour → time of day) live
 * only here, shared with Android's [OutfitCombination.fromConditions].
 */
object HeroFrameMapping {

    /** [TempBracket] name for a feels-like temperature in Fahrenheit. */
    fun tempBracketName(feelsLikeF: Double): String =
        TempBracket.fromFahrenheit(feelsLikeF).name

    /** [HeroWeatherCondition] name for an Open-Meteo WMO weather code. */
    fun weatherConditionName(weatherCode: Int): String =
        HeroWeatherCondition.fromWeatherCode(WeatherCode.fromCode(weatherCode)).name

    /** [TimeOfDay] name for a location-local hour (0–23). */
    fun timeOfDayName(hour: Int): String =
        TimeOfDay.fromHour(hour).name
}
