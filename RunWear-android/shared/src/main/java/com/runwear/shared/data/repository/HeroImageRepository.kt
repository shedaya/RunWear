package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageResult
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.OutfitCombination
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TimeOfDay
import com.runwear.shared.domain.model.WeatherConditions
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Repository for fetching AI-generated hero images from Supabase.
 *
 * Flow:
 * 1. Build combination ID from weather/outfit/gender
 * 2. Query generated_images table for matching images
 * 3. Return random image URL or queue generation if none exist
 */
@Singleton
class HeroImageRepository @Inject constructor() {

    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Storage)
    }

    /**
     * Get a hero image for the given conditions.
     * Returns cached AI image if available, otherwise returns fallback.
     */
    suspend fun getHeroImage(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX
    ): HeroImageResult = withContext(Dispatchers.IO) {
        if (weather == null || outfit == null) {
            return@withContext HeroImageResult(
                imageUrl = null,
                thumbnailUrl = null,
                combinationId = "unknown"
            )
        }

        val combination = OutfitCombination.fromConditions(weather, outfit, gender)

        try {
            // Try to get a cached image for this combination
            val image = getRandomImage(combination)
            if (image != null) {
                HeroImageResult(
                    imageUrl = image.imageUrl,
                    thumbnailUrl = image.thumbnailUrl,
                    combinationId = combination.id
                )
            } else {
                // No cached image - queue generation and return fallback
                queueImageGeneration(combination, outfit)
                getFallbackImage(combination)
            }
        } catch (e: Exception) {
            // On error, return fallback
            getFallbackImage(combination)
        }
    }

    /**
     * Get a random cached image for the combination.
     * Uses cascading fallback strategy (matches PWA v3.9):
     *
     * | Priority | Query Pattern              | Example                    | Matches            |
     * |----------|---------------------------|----------------------------|-------------------|
     * | 1        | Gender + Weather + Temp + Time | MALE_CLOUDY_MILD_NIGHT_% | Exact scenario    |
     * | 2        | Gender + Weather + Temp   | MALE_CLOUDY_MILD_%        | Any time of day   |
     * | 3        | Unisex + Weather + Temp   | UNISEX_CLOUDY_MILD_%      | Generic person    |
     * | 4        | Gender + Clear + Temp     | MALE_CLEAR_MILD_%         | Clear weather fallback |
     *
     * CRITICAL v3.9:
     * - NO status filter (column doesn't exist in DB)
     * - Supabase SDK handles URL encoding of % automatically
     */
    private suspend fun getRandomImage(combination: OutfitCombination): GeneratedImage? {
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val gender = combination.genderPreference.name
        val weather = heroWeather.name
        val temp = combination.tempBracket.name
        val time = combination.timeOfDay.name

        // Build cascade queries from most specific to least specific
        val queries = listOf(
            "${gender}_${weather}_${temp}_${time}_%",  // 1. Exact: MALE_CLOUDY_MILD_NIGHT_*
            "${gender}_${weather}_${temp}_%",          // 2. Any time: MALE_CLOUDY_MILD_*
            "UNISEX_${weather}_${temp}_%",             // 3. Unisex fallback: UNISEX_CLOUDY_MILD_*
            "${gender}_CLEAR_${temp}_%"                // 4. Clear weather fallback: MALE_CLEAR_MILD_*
        )

        for (pattern in queries) {
            try {
                android.util.Log.d("HeroImage", "[Hero] Trying: $pattern")

                val images = supabase.from("generated_images")
                    .select {
                        filter {
                            like("combination_id", pattern)
                        }
                        limit(10)
                    }
                    .decodeList<GeneratedImage>()

                if (images.isNotEmpty()) {
                    val selected = images.random()
                    android.util.Log.d("HeroImage", "[Hero] ✓ Found via ${pattern.dropLast(1)} → ${selected.combinationId}")
                    return selected
                }
            } catch (e: Exception) {
                android.util.Log.w("HeroImage", "[Hero] Query failed: $pattern", e)
            }
        }

        android.util.Log.d("HeroImage", "[Hero] No AI images found, using Unsplash fallback")
        return null
    }

    /**
     * Queue an image generation job for this combination.
     */
    private suspend fun queueImageGeneration(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ) {
        try {
            val prompt = buildPrompt(combination, outfit)

            supabase.from("generation_jobs")
                .insert(
                    GenerationJobInsert(
                        combinationId = combination.id,
                        tempBracket = combination.tempBracket.name.lowercase(),
                        timeOfDay = combination.timeOfDay.name.lowercase(),
                        gender = combination.genderPreference.name.lowercase(),
                        weatherCode = combination.weatherCode.name.lowercase(),
                        prompt = prompt,
                        status = "pending"
                    )
                )
        } catch (e: Exception) {
            // Silently fail - generation is best-effort
        }
    }

    /**
     * Build a prompt for AI image generation.
     */
    private fun buildPrompt(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ): String {
        val genderDesc = when (combination.genderPreference) {
            GenderPreference.MALE -> "male"
            GenderPreference.FEMALE -> "female"
            GenderPreference.UNISEX -> "person"
        }

        val clothingList = outfit.allItems.joinToString(", ") { it.name }

        val weatherDesc = combination.tempBracket.description
        val timeDesc = combination.timeOfDay.description

        return """
            Professional running photography, $genderDesc runner in motion,
            wearing $clothingList,
            $weatherDesc weather conditions,
            $timeDesc lighting,
            urban trail or park setting,
            dynamic action shot,
            high quality, sharp focus
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Get a fallback image when no cached image is available.
     * Uses Unsplash placeholders categorized by temperature bracket AND weather condition.
     * This is a 2D matrix: 6 temp brackets × 4 weather conditions = 24 image categories.
     */
    private fun getFallbackImage(combination: OutfitCombination): HeroImageResult {
        val weatherCondition = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val url = getWeatherAwareFallback(combination.tempBracket, weatherCondition)

        return HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id
        )
    }

    /**
     * Get weather-aware fallback image URL.
     * Priority: temp + weather match > temp only > default
     */
    private fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String {
        // Try to get exact temp + weather match
        val bracketImages = FALLBACK_IMAGES_2D[tempBracket]
        val weatherImages = bracketImages?.get(weatherCondition)
        if (weatherImages != null) {
            return weatherImages
        }

        // Weather-specific fallback not available (e.g., HOT + SNOW is impossible)
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
     * Get the public URL for a storage path.
     */
    private fun getPublicUrl(storagePath: String): String {
        return supabase.storage.from("hero-images").publicUrl(storagePath)
    }

    companion object {
        private const val SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

        /**
         * Weather-aware fallback images: 2D matrix of temp bracket × weather condition.
         * 6 temp brackets × 4 weather conditions = up to 24 unique fallback images.
         * Based on RunWear Hero Image System Specification.
         *
         * Image selection:
         * - CLEAR: Sunny, bright conditions
         * - CLOUDY: Overcast, gray sky
         * - RAIN: Wet conditions, rain gear visible
         * - SNOW: Winter snow conditions
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
    }
}

/**
 * Database model for generated_images table.
 * v3.9: Matches actual Supabase schema - NO status column exists!
 */
@Serializable
data class GeneratedImage(
    @SerialName("id") val id: String,
    @SerialName("combination_id") val combinationId: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("prompt") val prompt: String? = null
)

/**
 * Insert model for generation_jobs table.
 */
@Serializable
data class GenerationJobInsert(
    @SerialName("combination_id") val combinationId: String,
    @SerialName("temp_bracket") val tempBracket: String,
    @SerialName("time_of_day") val timeOfDay: String,
    @SerialName("gender") val gender: String,
    @SerialName("weather_code") val weatherCode: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("status") val status: String
)
