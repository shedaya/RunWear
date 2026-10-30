package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageResult
import com.runwear.shared.domain.model.HeroPoolPolicy
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

    /**
     * 1.1.2 safe-boot change: the client is built LAZILY, on first actual
     * use — constructing it eagerly in this constructor meant network
     * machinery (Ktor engines, serializers) was created during app
     * startup, inside the window where the 1.1.x crash loop dies.
     * Construction is guarded: if it fails, [supabase] stays null and
     * every use site falls back (bundled fallback hero, no replenishment
     * queueing) instead of taking the app down. Once construction
     * succeeds, behavior is exactly as before.
     */
    private val supabase: SupabaseClient? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                // v3.9: Ignore unknown fields from DB (style, served_count, rating, etc.)
                defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
                install(Postgrest)
                install(Storage)
            }
        }.onFailure {
            android.util.Log.w("HeroImage", "[Hero] Supabase client construction failed", it)
        }.getOrNull()
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
                poolResult(combination, searchResult)
            } else if (searchResult.exactMatchCount < MIN_VARIANTS) {
                android.util.Log.d("HeroImage", "[Hero] Only ${searchResult.exactMatchCount} variants, queueing more (target: $MIN_VARIANTS)")
                if (shouldQueueReplenishment()) {
                    queueImageGeneration(combination, outfit)
                }
                poolResult(combination, searchResult)
            } else {
                android.util.Log.d("HeroImage", "[Hero] Have ${searchResult.exactMatchCount} variants, no replenishment needed")
                poolResult(combination, searchResult)
            }
        } catch (e: Exception) {
            // On error, return fallback
            getFallbackImage(combination)
        }
    }

    /**
     * Build the result for a successful pool search. Selection stays a
     * uniform random pick (now across the whole winning cascade level, not
     * a single pre-picked row), and for the common floor cells the curated
     * Bundled fallback photo for the temp × weather cell joins the rotation
     * (HeroPoolPolicy) instead of appearing only on the error path.
     */
    private fun poolResult(
        combination: OutfitCombination,
        searchResult: ImageSearchResult
    ): HeroImageResult {
        val image = searchResult.image ?: return getFallbackImage(combination)
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val poolUrls = searchResult.candidates.map { it.imageUrl }
        val candidates = if (HeroPoolPolicy.isFloorCell(combination.tempBracket, heroWeather, combination.timeOfDay)) {
            HeroPoolPolicy.mergeFloorCandidate(
                poolUrls,
                getWeatherAwareFallback(combination.tempBracket, heroWeather)
            )
        } else {
            poolUrls
        }
        if (candidates.size <= 1) {
            return HeroImageResult(
                imageUrl = image.imageUrl,
                thumbnailUrl = image.thumbnailUrl,
                combinationId = combination.id,
                imageRowId = image.id,
                servedCombinationId = image.combinationId
            )
        }
        val picked = candidates.random()
        val pickedImage = searchResult.candidates.firstOrNull { it.imageUrl == picked }
        return HeroImageResult(
            imageUrl = picked,
            thumbnailUrl = pickedImage?.thumbnailUrl ?: picked,
            combinationId = combination.id,
            imageRowId = pickedImage?.id,
            servedCombinationId = pickedImage?.combinationId
        )
    }

    /**
     * Report the hero photo on screen as bad (user tapped the flag).
     *
     * Fire-and-forget by contract: builds the [HeroFlagInsert] for the
     * exact photo shown and hands it to [HeroFlagPayload.submit], which
     * retries once and swallows all failures. This function never throws
     * and never returns a result the UI needs to act on.
     *
     * @param appVersion the host app's version name (the shared module
     *   has no BuildConfig of its own); recorded for triage.
     */
    suspend fun flagHeroImage(
        imageUrl: String?,
        imageRowId: String?,
        servedCombinationId: String?,
        requestedCombinationId: String?,
        reason: com.runwear.shared.domain.model.HeroFlagReason,
        appVersion: String?
    ) {
        val payload = HeroFlagPayload.build(
            imageUrl = imageUrl,
            imageRowId = imageRowId,
            servedCombinationId = servedCombinationId,
            requestedCombinationId = requestedCombinationId,
            reason = reason,
            appVersion = appVersion,
            createdAtIso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        ) ?: return
        // Lazy client (1.1.2): unavailable → the flag is dropped silently.
        val client = supabase ?: return
        withContext(Dispatchers.IO) {
            HeroFlagPayload.submit(payload) { row ->
                client.from("hero_flags").insert(row)
            }
        }
    }

    /**
     * Result from getRandomImage including metadata for replenishment decisions.
     * [candidates] is the SERVABLE (review-approved) pool at the cascade
     * level that produced [image]; [exactMatchCount] counts servable exact
     * matches only, so replenishment targets images users can actually see.
     */
    private data class ImageSearchResult(
        val image: GeneratedImage?,
        val exactMatchCount: Int,
        val foundViaFallback: Boolean,
        val candidates: List<GeneratedImage> = emptyList()
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
        // Lazy client (1.1.2): unavailable → the caller falls back.
        val client = supabase ?: return ImageSearchResult(null, 0, false)
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
        // 4-5. Fall back to opposite gender (better than the bundled fallback)
        queries.add("${oppositeGender}_${weather}_${temp}_%")
        queries.add("${oppositeGender}_CLEAR_${temp}_%")

        var exactMatchCount = 0
        var foundViaFallback = false

        for (pattern in queries) {
            try {
                android.util.Log.d("HeroImage", "[Hero] Trying: $pattern")

                val rows = client.from("generated_images")
                    .select {
                        filter {
                            like("combination_id", pattern)
                        }
                        // 50 (was 20): headroom so the review filter below
                        // cannot starve a level whose top rows are pending.
                        limit(50)
                    }
                    .decodeList<GeneratedImage>()

                // Review gate: pending/rejected generations are never
                // served. Pre-migration rows decode reviewStatus = null
                // and count as approved (HeroPoolPolicy).
                val images = rows.filter { HeroPoolPolicy.isServable(it.reviewStatus) }

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
                    return ImageSearchResult(selected, exactMatchCount, foundViaFallback, images)
                }
            } catch (e: Exception) {
                android.util.Log.w("HeroImage", "[Hero] Query failed: $pattern", e)
            }
        }

        android.util.Log.d("HeroImage", "[Hero] No AI images found, using bundled fallback")
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
        // Lazy client (1.1.2): unavailable → nothing to queue into.
        val client = supabase ?: return
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val baseCombo = "${combination.genderPreference.name}_${heroWeather.name}_${combination.tempBracket.name}_${combination.timeOfDay.name}"

        try {
            // Find highest existing variant number in generation_jobs
            var maxVariant = 0
            val existingJobs = client.from("generation_jobs")
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
            val existingImages = client.from("generated_images")
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
            client.from("generation_jobs")
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
     * v3.14: Uses actual outfit recommendation items instead of static descriptions.
     * Includes random backgrounds for variety per HERO_IMAGE_SPEC.md
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
            TimeOfDay.DAWN -> "early morning golden hour"
            TimeOfDay.MIDDAY -> "bright midday"
            TimeOfDay.DUSK -> "evening golden hour"
            TimeOfDay.NIGHT -> "night with street lights"
        }

        // Random backgrounds for variety
        val backgrounds = listOf(
            "city street with buildings in background",
            "urban park with trees",
            "waterfront boardwalk",
            "scenic trail with nature",
            "downtown area with shops",
            "bridge with city skyline",
            "tree-lined avenue"
        )
        val background = backgrounds.random()

        // Mood based on conditions
        val mood = when {
            heroWeather == HeroWeatherCondition.RAIN -> "determined, pushing through the rain"
            heroWeather == HeroWeatherCondition.SNOW -> "resilient, winter warrior"
            combination.tempBracket == TempBracket.HOT -> "energetic, summer vibes"
            combination.tempBracket == TempBracket.FREEZING -> "tough, braving the cold"
            else -> "focused, confident stride"
        }

        // v3.14/v4.1: Build outfit description from actual recommendation items
        val outfitParts = mutableListOf<String>()
        outfitParts.add(outfit.topBase.displayName.lowercase())
        outfit.topMid?.let { outfitParts.add(it.displayName.lowercase()) }
        outfit.topOuter?.let { outfitParts.add(it.displayName.lowercase()) }
        outfitParts.add(outfit.bottom.displayName.lowercase())
        outfit.head?.let { outfitParts.add(it.displayName.lowercase()) }
        outfit.hands?.let { outfitParts.add(it.displayName.lowercase()) }
        outfit.accessories.filter { it.name != "SUNSCREEN" && it.name != "REFLECTIVE_GEAR" }
            .forEach { outfitParts.add(it.displayName.lowercase()) }

        val outfitDesc = outfitParts.joinToString(", ")

        // Prompt hardening (2026-10-27): every queued prompt carries the
        // anatomy/composition block; the edge function re-applies it
        // server-side so jobs queued by older clients are covered too.
        return HeroPoolPolicy.ensureQualitySuffix(
            "A $genderDesc runner in their 30s running mid-stride along a $background. " +
                "They are wearing $outfitDesc appropriate for $weatherDesc $tempDesc weather. " +
                "Time of day: $timeDesc. Professional running photography, dynamic action shot, " +
                "high quality, sharp focus. MOOD: $mood"
        )
    }

    /**
     * Get a fallback image when no cached image is available.
     * Uses bundled pool-derived images categorized by temperature bracket AND weather condition.
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
    internal fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String {
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
            ?: "file:///android_asset/hero_fallback/mild_clear.webp"
    }

    /**
     * Get the public URL for a storage path.
     */
    private fun getPublicUrl(storagePath: String): String {
        // Lazy client (1.1.2): unavailable → no URL to hand out.
        val client = supabase ?: return ""
        return client.storage.from("hero-images").publicUrl(storagePath)
    }

    companion object {
        private const val SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

        /**
         * Weather-aware fallback images: 2D matrix of temp bracket × weather condition.
         * 6 temp brackets × 4 weather conditions = up to 24 unique fallback images.
         * Based on RunWear Hero Image System Specification.
         *
         * Since 2026-10-29 every cell is a BUNDLED asset (shared/src/main/assets/
         * hero_fallback/) derived from an approved generated_images pool row, so
         * the fallback renders fully offline. Before that, the cells were remote
         * Unsplash stock URLs. Keep in sync with HeroImageSelector's copy of this
         * table in the engine module (a unit test asserts the two agree).
         *
         * Image selection:
         * - CLEAR: Sunny, bright conditions
         * - CLOUDY: Overcast, gray sky
         * - RAIN: Wet conditions, rain gear visible
         * - SNOW: Winter snow conditions
         */
        internal val FALLBACK_IMAGES_2D: Map<TempBracket, Map<HeroWeatherCondition, String>> = mapOf(
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
    }
}

/**
 * Database model for generated_images table.
 * review_status arrives with the 2026-10-27 review-gate migration
 * ('approved' | 'pending' | 'rejected'); until that migration is applied
 * the column does not exist and the field decodes as null, which
 * HeroPoolPolicy treats as approved — serving is unchanged either way.
 */
@Serializable
data class GeneratedImage(
    @SerialName("id") val id: String,
    @SerialName("combination_id") val combinationId: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null
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
