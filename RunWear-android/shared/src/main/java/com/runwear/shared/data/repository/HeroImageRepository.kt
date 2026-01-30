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
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        // v3.9: Ignore unknown fields from DB (style, served_count, rating, etc.)
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
        install(Postgrest)
        install(Storage)
    }

    /**
     * Get gender for hero image selection ONLY (not for affiliate links).
     * When user hasn't selected a gender (UNISEX), randomly pick MALE or FEMALE for variety.
     * v3.10: Matches PWA getHeroImageGender() behavior.
     */
    private fun getHeroImageGender(preference: GenderPreference): GenderPreference {
        return when (preference) {
            GenderPreference.MALE -> GenderPreference.MALE
            GenderPreference.FEMALE -> GenderPreference.FEMALE
            GenderPreference.UNISEX -> if (Random.nextBoolean()) GenderPreference.MALE else GenderPreference.FEMALE
        }
    }

    // Rate limiting: track last replenishment time
    private var lastReplenishTime: Long = 0
    private val RATE_LIMIT_MS = 5 * 60 * 1000L  // 5 minutes
    private val MIN_VARIANTS = 5  // Target number of variants per combination

    /**
     * Check if we should queue replenishment (rate limited to 1 per 5 min).
     */
    private fun shouldQueueReplenishment(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastReplenishTime < RATE_LIMIT_MS) {
            android.util.Log.d("HeroImage", "[Replenish] Rate limited, skipping")
            return false
        }
        lastReplenishTime = now
        return true
    }

    /**
     * Get a hero image for the given conditions.
     * v3.11: Demand-driven replenishment - queues new variants when < 5 exist.
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

        // v3.10: Use hero-specific gender (random when UNISEX) for image variety
        val heroGender = getHeroImageGender(gender)
        val combination = OutfitCombination.fromConditions(weather, outfit, heroGender)

        try {
            // Try to get a cached image for this combination
            val searchResult = getRandomImage(combination)

            // v3.11: Demand-driven replenishment
            // Queue new variant if:
            // 1. No images found at all, OR
            // 2. Found via fallback (missing for requested gender), OR
            // 3. Exact matches < MIN_VARIANTS (want more variety)
            if (searchResult.image == null) {
                android.util.Log.d("HeroImage", "[Hero] No AI images found, queueing replenishment")
                if (shouldQueueReplenishment()) {
                    queueImageGeneration(combination, outfit)
                }
                getFallbackImage(combination)
            } else if (searchResult.foundViaFallback) {
                android.util.Log.d("HeroImage", "[Hero] Found via fallback, queueing for requested gender")
                if (shouldQueueReplenishment()) {
                    queueImageGeneration(combination, outfit)
                }
                HeroImageResult(
                    imageUrl = searchResult.image.imageUrl,
                    thumbnailUrl = searchResult.image.thumbnailUrl,
                    combinationId = combination.id
                )
            } else if (searchResult.exactMatchCount < MIN_VARIANTS) {
                android.util.Log.d("HeroImage", "[Hero] Only ${searchResult.exactMatchCount} variants, queueing more (target: $MIN_VARIANTS)")
                if (shouldQueueReplenishment()) {
                    queueImageGeneration(combination, outfit)
                }
                HeroImageResult(
                    imageUrl = searchResult.image.imageUrl,
                    thumbnailUrl = searchResult.image.thumbnailUrl,
                    combinationId = combination.id
                )
            } else {
                android.util.Log.d("HeroImage", "[Hero] Have ${searchResult.exactMatchCount} variants, no replenishment needed")
                HeroImageResult(
                    imageUrl = searchResult.image.imageUrl,
                    thumbnailUrl = searchResult.image.thumbnailUrl,
                    combinationId = combination.id
                )
            }
        } catch (e: Exception) {
            // On error, return fallback
            getFallbackImage(combination)
        }
    }

    /**
     * Result from getRandomImage including metadata for replenishment decisions.
     */
    private data class ImageSearchResult(
        val image: GeneratedImage?,
        val exactMatchCount: Int,
        val foundViaFallback: Boolean
    )

    /**
     * Get a random cached image for the combination.
     * v3.11: Matches PWA cascade with opposite gender fallback.
     *
     * | Priority | Query Pattern                    | Example                    |
     * |----------|----------------------------------|----------------------------|
     * | 1        | Gender + Weather + Temp + Time   | FEMALE_CLEAR_COLD_MIDDAY_% |
     * | 2        | Gender + Weather + Temp          | FEMALE_CLEAR_COLD_%        |
     * | 3        | Gender + Clear + Temp (if !CLEAR)| FEMALE_CLEAR_COLD_%        |
     * | 4        | Opposite Gender + Weather + Temp | MALE_CLEAR_COLD_%          |
     * | 5        | Opposite Gender + Clear + Temp   | MALE_CLEAR_COLD_%          |
     */
    private suspend fun getRandomImage(combination: OutfitCombination): ImageSearchResult {
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val gender = combination.genderPreference.name
        val weather = heroWeather.name
        val temp = combination.tempBracket.name
        val time = combination.timeOfDay.name

        val requestedCombo = "${gender}_${weather}_${temp}_${time}"
        val oppositeGender = if (gender == "MALE") "FEMALE" else "MALE"

        // v3.11: Extended cascade with opposite gender fallback
        val queries = mutableListOf(
            "${gender}_${weather}_${temp}_${time}_%",  // 1. Exact
            "${gender}_${weather}_${temp}_%",          // 2. Any time
        )
        // 3. Clear weather fallback (only if not already CLEAR)
        if (weather != "CLEAR") {
            queries.add("${gender}_CLEAR_${temp}_%")
        }
        // 4-5. Fall back to opposite gender (better than Unsplash)
        queries.add("${oppositeGender}_${weather}_${temp}_%")
        queries.add("${oppositeGender}_CLEAR_${temp}_%")

        var exactMatchCount = 0
        var foundViaFallback = false

        for (pattern in queries) {
            try {
                android.util.Log.d("HeroImage", "[Hero] Trying: $pattern")

                val images = supabase.from("generated_images")
                    .select {
                        filter {
                            like("combination_id", pattern)
                        }
                        limit(20)
                    }
                    .decodeList<GeneratedImage>()

                if (images.isNotEmpty()) {
                    // Check if this is an exact match query
                    if (pattern.startsWith(requestedCombo)) {
                        exactMatchCount = images.size
                        android.util.Log.d("HeroImage", "[Hero] Exact match count: $exactMatchCount")
                    } else {
                        foundViaFallback = true
                    }

                    val selected = images.random()
                    android.util.Log.d("HeroImage", "[Hero] ✓ Found via ${pattern.dropLast(1)} → ${selected.combinationId}")
                    return ImageSearchResult(selected, exactMatchCount, foundViaFallback)
                }
            } catch (e: Exception) {
                android.util.Log.w("HeroImage", "[Hero] Query failed: $pattern", e)
            }
        }

        android.util.Log.d("HeroImage", "[Hero] No AI images found, using Unsplash fallback")
        return ImageSearchResult(null, 0, false)
    }

    /**
     * Queue an image generation job with auto-incrementing variant number.
     * v3.11: Finds next available variant number to support demand-driven growth.
     */
    private suspend fun queueImageGeneration(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ) {
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val baseCombo = "${combination.genderPreference.name}_${heroWeather.name}_${combination.tempBracket.name}_${combination.timeOfDay.name}"

        try {
            // Find highest existing variant number in generation_jobs
            var maxVariant = 0
            val existingJobs = supabase.from("generation_jobs")
                .select {
                    filter {
                        like("combination_id", "${baseCombo}_%")
                    }
                }
                .decodeList<GenerationJobCheck>()

            existingJobs.forEach { job ->
                val match = Regex("_v(\\d+)$").find(job.combinationId)
                match?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
                    maxVariant = maxOf(maxVariant, num)
                }
            }

            // Also check generated_images for existing variants
            val existingImages = supabase.from("generated_images")
                .select {
                    filter {
                        like("combination_id", "${baseCombo}_%")
                    }
                }
                .decodeList<GeneratedImage>()

            existingImages.forEach { img ->
                val match = Regex("_v?(\\d+)$").find(img.combinationId)
                match?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
                    maxVariant = maxOf(maxVariant, num)
                }
            }

            val nextVariant = maxVariant + 1
            val combinationId = "${baseCombo}_v${nextVariant}"

            android.util.Log.d("HeroImage", "[Replenish] Queueing variant: $combinationId")

            // Queue new generation (only send fields that exist in table)
            val prompt = buildPrompt(combination, outfit)
            supabase.from("generation_jobs")
                .insert(
                    GenerationJobInsert(
                        combinationId = combinationId,
                        prompt = prompt,
                        status = "QUEUED"
                    )
                )

            android.util.Log.d("HeroImage", "[Replenish] ✓ Queued: $combinationId")
        } catch (e: Exception) {
            android.util.Log.w("HeroImage", "[Replenish] Error: ${e.message}", e)
        }
    }

    /**
     * Build a prompt for AI image generation.
     * v3.12: Enhanced prompt with detailed outfit descriptions matching PWA format.
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

        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val weatherDesc = heroWeather.name.lowercase()
        val tempDesc = combination.tempBracket.name.lowercase().replace("_", " ")
        val timeDesc = when (combination.timeOfDay) {
            TimeOfDay.DAWN -> "early morning"
            TimeOfDay.MIDDAY -> "midday"
            TimeOfDay.DUSK -> "evening"
            TimeOfDay.NIGHT -> "night"
        }

        // Detailed outfit descriptions matching getOutfitRecommendation() logic
        val outfitDescriptions = mapOf(
            TempBracket.HOT to "lightweight breathable tank top, very short split running shorts, sunglasses, sweat-wicking headband",
            TempBracket.WARM to "breathable short sleeve tech shirt, standard running shorts, light mesh running cap",
            TempBracket.MILD to "fitted long sleeve moisture-wicking shirt, running shorts or light capris",
            TempBracket.COOL to "quarter-zip pullover or lightweight jacket, full-length running tights, thin gloves, ear-covering headband",
            TempBracket.COLD to "thermal base layer, insulated wind-resistant jacket, thermal tights, warm fleece beanie, insulated gloves, neck gaiter",
            TempBracket.FREEZING to "multiple thermal layers, heavy insulated jacket with hood, thick thermal tights, full balaclava covering face, thick insulated mittens, neck gaiter, visible breath vapor"
        )

        val outfitDesc = outfitDescriptions[combination.tempBracket] ?: outfitDescriptions[TempBracket.MILD]

        return "Professional running photography, $genderDesc runner in motion, $weatherDesc weather, $tempDesc temperature, $timeDesc lighting, urban trail or park setting, dynamic action shot, high quality, sharp focus. OUTFIT: $outfitDesc"
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
 * v3.11: Only includes fields that exist in the table (combination_id encodes gender/weather/temp/time)
 */
@Serializable
data class GenerationJobInsert(
    @SerialName("combination_id") val combinationId: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("status") val status: String
)

/**
 * Minimal model for checking if a generation job exists.
 */
@Serializable
data class GenerationJobCheck(
    @SerialName("combination_id") val combinationId: String
)
