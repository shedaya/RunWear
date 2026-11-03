package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.ComfortPreference
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

    /**
     * Get a hero image for the given conditions.
     *
     * RW-14 (acceptance 11, Sol's pool-freeze ruling 2026-10-31): the
     * demand-driven queue-on-low path is REMOVED. The generic pool is
     * frozen as the permanent library and fallback — this client never
     * inserts into the generic generation queue. A cell with no servable
     * pool image serves the bundled fallback, exactly as the frozen
     * library serves it today.
     */
    suspend fun getHeroImage(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX,
        comfort: ComfortPreference
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
        val combination = OutfitCombination.fromConditions(
            weather, outfit, heroGender,
            frameFeelsLikeF = com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase.adjustedFeelsLikeF(weather, comfort)
        )

        try {
            val searchResult = getRandomImage(combination, outfit)
            if (searchResult.image == null) {
                getFallbackImage(combination)
            } else {
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
        // Full candidate list for the refresh control: every servable
        // pool row at the winning cascade level, in pool order. For the
        // common floor cells the curated bundled fallback photo joins
        // the rotation (HeroPoolPolicy), as a candidate with no pool row.
        val poolCandidates = searchResult.candidates.map { row ->
            com.runwear.shared.domain.model.HeroCandidate(
                imageUrl = row.imageUrl,
                thumbnailUrl = row.thumbnailUrl,
                imageRowId = row.id,
                servedCombinationId = row.combinationId
            )
        }
        val candidates = if (HeroPoolPolicy.isFloorCell(combination.tempBracket, heroWeather, combination.timeOfDay)) {
            // RW-26: the floor candidate is resolved gender-aware, so the
            // curated photo that joins the rotation is never an
            // opposite-gender subject for this combination's (already
            // UNISEX-resolved) gender.
            val floorUrl = getWeatherAwareFallback(combination.tempBracket, heroWeather, combination.genderPreference)
            if (poolCandidates.any { it.imageUrl == floorUrl }) {
                poolCandidates
            } else {
                poolCandidates + com.runwear.shared.domain.model.HeroCandidate(
                    imageUrl = floorUrl,
                    thumbnailUrl = floorUrl,
                    imageRowId = null,
                    servedCombinationId = combination.id
                )
            }
        } else {
            poolCandidates
        }
        if (candidates.size <= 1) {
            return HeroImageResult(
                imageUrl = image.imageUrl,
                thumbnailUrl = image.thumbnailUrl,
                combinationId = combination.id,
                imageRowId = image.id,
                servedCombinationId = image.combinationId,
                candidates = candidates
            )
        }
        val picked = candidates.random()
        return HeroImageResult(
            imageUrl = picked.imageUrl,
            thumbnailUrl = picked.thumbnailUrl ?: picked.imageUrl,
            combinationId = combination.id,
            imageRowId = picked.imageRowId,
            servedCombinationId = picked.servedCombinationId,
            candidates = candidates
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
     * RW-20: both now count only rows that also pass the point-grain
     * prescription filter — the images a user can actually be served for
     * the current conditions, which is the honest replenishment signal.
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
    private suspend fun getRandomImage(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ): ImageSearchResult {
        // Lazy client (1.1.2): unavailable → the caller falls back.
        val client = supabase ?: return ImageSearchResult(null, 0, false)
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val gender = combination.genderPreference.name
        val weather = heroWeather.name
        val temp = combination.tempBracket.name
        val time = combination.timeOfDay.name

        val requestedCombo = "${gender}_${weather}_${temp}_${time}"
        val oppositeGender = if (gender == "MALE") "FEMALE" else "MALE"

        val queries = heroCascadePatterns(gender, weather, temp, time, oppositeGender)

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
                // RW-20 point-grain gate: of the servable rows, only
                // those whose adjudicated garments and person count
                // agree with THIS request's exact prescription may be
                // served; a level with no agreeing row cascades onward.
                val images = pointGrainImages(rows, outfit)

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
     * Get a fallback image when no cached image is available.
     * Uses bundled pool-derived images categorized by temperature bracket AND weather condition.
     * This is a 2D matrix: 6 temp brackets × 4 weather conditions = 24 image categories.
     */
    private fun getFallbackImage(combination: OutfitCombination): HeroImageResult {
        val weatherCondition = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        // RW-26: gender-aware — the combination carries the resolved
        // hero gender, so the last-resort photo matches it (see the
        // subject table on the resolver below).
        val url = getWeatherAwareFallback(combination.tempBracket, weatherCondition, combination.genderPreference)

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
    internal fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String =
        getWeatherAwareFallback(tempBracket, weatherCondition, GenderPreference.UNISEX)

    /**
     * RW-26 (2026-11-02): gender-aware form — mirror of the engine's
     * HeroImageSelector resolver (the two copies are kept in agreement
     * by HeroFallbackImagesTest / HeroFallbackGenderTest). A MALE or
     * FEMALE request never resolves to an asset whose photographed
     * subject is the other gender while any bundled asset matches;
     * UNISEX keeps the legacy chain exactly.
     */
    internal fun getWeatherAwareFallback(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition,
        gender: GenderPreference
    ): String {
        if (gender != GenderPreference.UNISEX) {
            val match = orderedFallbackCells(tempBracket, weatherCondition)
                .firstOrNull { FALLBACK_SUBJECT_GENDER[it] == gender }
            if (match != null) {
                return FALLBACK_IMAGES_2D.getValue(match.first).getValue(match.second)
            }
        }

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

    /** The subject gender of this copy's bundled asset for a cell (RW-26). */
    internal fun fallbackSubjectGender(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition
    ): GenderPreference? = FALLBACK_SUBJECT_GENDER[tempBracket to weatherCondition]

    /** Mirror of HeroImageSelector.orderedFallbackCells (RW-26). */
    private fun orderedFallbackCells(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition
    ): List<Pair<TempBracket, HeroWeatherCondition>> {
        val cells = LinkedHashSet<Pair<TempBracket, HeroWeatherCondition>>()
        fun addBracket(bracket: TempBracket) {
            cells.add(bracket to weatherCondition)
            for (w in listOf(
                HeroWeatherCondition.CLEAR, HeroWeatherCondition.CLOUDY,
                HeroWeatherCondition.RAIN, HeroWeatherCondition.SNOW
            )) {
                cells.add(bracket to w)
            }
        }
        addBracket(tempBracket)
        val idx = BRACKET_WARMTH_ORDER.indexOf(tempBracket)
        val neighbours = BRACKET_WARMTH_ORDER
            .filter { it != tempBracket }
            .sortedWith(
                compareBy(
                    { kotlin.math.abs(BRACKET_WARMTH_ORDER.indexOf(it) - idx) },
                    { -BRACKET_WARMTH_ORDER.indexOf(it) }
                )
            )
        for (b in neighbours) addBracket(b)
        return cells.filter { FALLBACK_IMAGES_2D[it.first]?.containsKey(it.second) == true }
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
         * RW-20 (2026-11-02): every asset's provenance was re-derived by
         * pixel comparison against the pool. 9 assets verified against
         * adjudicated-MATCH, single-person, point-grain-correct source
         * rows; 12 were replaced from such rows in the same cell (their
         * sources were retired, mismatched, multi-person, or merely
         * UNCERTAIN); the FREEZING×RAIN cell was removed — its pool
         * holds no verified-good photo at all. Provenance record:
         * release-prep/rw20-fix-execution-2026-11-02.md.
         *
         * Image selection:
         * - CLEAR: Sunny, bright conditions
         * - CLOUDY: Overcast, gray sky
         * - RAIN: Wet conditions, rain gear visible
         * - SNOW: Winter snow conditions
         */
        /**
         * RW-26 (2026-11-02): subject gender of every bundled fallback
         * asset, from the RW-20-verified provenance of its source pool
         * row. Mirror of the engine HeroImageSelector table — the two
         * copies are pinned to each other by HeroFallbackGenderTest.
         * (Diagnosis: release-prep/rw-gender-preference-diagnosis-2026-11-02.md.)
         */
        internal val FALLBACK_SUBJECT_GENDER: Map<Pair<TempBracket, HeroWeatherCondition>, GenderPreference> = mapOf(
            (TempBracket.FREEZING to HeroWeatherCondition.CLEAR) to GenderPreference.FEMALE,
            (TempBracket.FREEZING to HeroWeatherCondition.CLOUDY) to GenderPreference.FEMALE,
            (TempBracket.FREEZING to HeroWeatherCondition.SNOW) to GenderPreference.MALE,
            (TempBracket.COLD to HeroWeatherCondition.CLEAR) to GenderPreference.FEMALE,
            (TempBracket.COLD to HeroWeatherCondition.CLOUDY) to GenderPreference.FEMALE,
            (TempBracket.COLD to HeroWeatherCondition.RAIN) to GenderPreference.FEMALE,
            (TempBracket.COLD to HeroWeatherCondition.SNOW) to GenderPreference.FEMALE,
            (TempBracket.COOL to HeroWeatherCondition.CLEAR) to GenderPreference.FEMALE,
            (TempBracket.COOL to HeroWeatherCondition.CLOUDY) to GenderPreference.FEMALE,
            (TempBracket.COOL to HeroWeatherCondition.RAIN) to GenderPreference.FEMALE,
            (TempBracket.COOL to HeroWeatherCondition.SNOW) to GenderPreference.MALE,
            (TempBracket.MILD to HeroWeatherCondition.CLEAR) to GenderPreference.FEMALE,
            (TempBracket.MILD to HeroWeatherCondition.CLOUDY) to GenderPreference.FEMALE,
            (TempBracket.MILD to HeroWeatherCondition.RAIN) to GenderPreference.FEMALE,
            (TempBracket.MILD to HeroWeatherCondition.SNOW) to GenderPreference.FEMALE,
            (TempBracket.WARM to HeroWeatherCondition.CLEAR) to GenderPreference.MALE,
            (TempBracket.WARM to HeroWeatherCondition.CLOUDY) to GenderPreference.MALE,
            (TempBracket.WARM to HeroWeatherCondition.RAIN) to GenderPreference.FEMALE,
            (TempBracket.HOT to HeroWeatherCondition.CLEAR) to GenderPreference.FEMALE,
            (TempBracket.HOT to HeroWeatherCondition.CLOUDY) to GenderPreference.MALE,
            (TempBracket.HOT to HeroWeatherCondition.RAIN) to GenderPreference.FEMALE
        )

        /** Warmth order (coldest first) for walking neighbouring brackets (RW-26). */
        internal val BRACKET_WARMTH_ORDER: List<TempBracket> = listOf(
            TempBracket.FREEZING, TempBracket.COLD, TempBracket.COOL,
            TempBracket.MILD, TempBracket.WARM, TempBracket.HOT
        )

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
            // FREEZING (<20°F) - RAIN cell REMOVED by RW-20 (2026-11-02):
            // the cell's pool holds zero verified-good photos (all 77
            // FREEZING rain/rainy rows are rejected), so no honest floor
            // asset exists; the resolver falls back to freezing_clear.
            TempBracket.FREEZING to mapOf(
                HeroWeatherCondition.CLEAR to "file:///android_asset/hero_fallback/freezing_clear.webp", // Winter gear sunny
                HeroWeatherCondition.CLOUDY to "file:///android_asset/hero_fallback/freezing_cloudy.webp", // Freezing overcast
                HeroWeatherCondition.SNOW to "file:///android_asset/hero_fallback/freezing_snow.webp" // Heavy snow
            )
        )
    }
}

/**
 * RW-23: the hero cascade pattern list, in serving order.
 *
 * Canonical levels (v3.11, preserved exactly):
 * 1. exact, 2. any-time, 3. clear-weather only when weather != "CLEAR",
 * 4. opposite gender + weather, 5. opposite gender + clear.
 *
 * Legacy-token levels (appended AFTER every canonical level):
 * 523 pool rows live under legacy RAINY/SNOWY combination tokens no
 * canonical pattern can match (105 of them fully on-prescription);
 * they join the cascade only after every canonical level, under the
 * same review + point-grain gates.
 */
internal fun heroCascadePatterns(
    gender: String,
    weather: String,
    temp: String,
    time: String,
    oppositeGender: String
): List<String> {
    val queries = mutableListOf(
        "${gender}_${weather}_${temp}_${time}_%",
        "${gender}_${weather}_${temp}_%"
    )
    if (weather != "CLEAR") {
        queries.add("${gender}_CLEAR_${temp}_%")
    }
    queries.add("${oppositeGender}_${weather}_${temp}_%")
    queries.add("${oppositeGender}_CLEAR_${temp}_%")
    if (weather == "RAIN") {
        queries.add("${gender}_RAINY_${temp}_%")
        queries.add("${oppositeGender}_RAINY_${temp}_%")
    }
    if (weather == "SNOW") {
        queries.add("${gender}_SNOWY_${temp}_%")
        queries.add("${oppositeGender}_SNOWY_${temp}_%")
    }
    return queries
}

/**
 * THE hero serving gate: the rows from a generated_images query that
 * may be served, in pool order. Every serving path (the random pick
 * and the refresh control's candidate list alike) draws from this
 * filtered list and nothing else — a row whose review_status is
 * 'rejected' or 'pending' can never reach the screen, and a cascade
 * level whose rows are all unservable yields an empty list here, so
 * the caller cascades onward or falls back to the bundled image.
 *
 * This gate's absence is what shipped the Oct 30 vest incident: the
 * 1.2.1–1.2.3 phone lineages branched before the 2026-10-27 review
 * gate landed, fetched without it, and served an already-rejected
 * DAWN row whose slot held no approved rows at all. Pinned by
 * HeroServingGateTest.
 */
internal fun servableImages(rows: List<GeneratedImage>): List<GeneratedImage> =
    rows.filter { HeroPoolPolicy.isServable(it.reviewStatus) }

/**
 * RW-20: the servable rows that ALSO agree with the exact outfit
 * prescription at point grain (HeroPointGrainPolicy). Every serving
 * path draws from this list — a cascade level whose rows all fail the
 * prescription yields an empty list here, so the caller cascades
 * onward or falls back to the bundled image, exactly as it does for a
 * level with no servable rows.
 */
internal fun pointGrainImages(
    rows: List<GeneratedImage>,
    outfit: OutfitRecommendation
): List<GeneratedImage> = servableImages(rows).filter { row ->
    com.runwear.shared.domain.model.HeroPointGrainPolicy.matches(
        personCount = row.personCount,
        topBaseClass = row.garmentClasses?.topBase,
        outerClass = row.garmentClasses?.outer,
        bottomClass = row.garmentClasses?.bottom,
        prescribedTopBase = outfit.topBase,
        prescribedTopOuter = outfit.topOuter,
        prescribedBottom = outfit.bottom,
        headClass = row.garmentClasses?.head,
        handsClass = row.garmentClasses?.hands,
        prescribedHead = outfit.head,
        prescribedHands = outfit.hands
    )
}

/**
 * The adjudicated garment classes of a pool photo (RW-20 backfill,
 * 2026-11-02): what the photo actually shows, as recorded by the
 * RW-16.17 audit / regen adjudication and stored on the row as
 * garment_classes. Vocabulary is the audit harness's: bottom ∈
 * {shorts, short_shorts, capris, tights}, topBase ∈ {sleeveless,
 * short, long}, head ∈ {balaclava, beanie, cap, headband, visor},
 * hands ∈ {gloves, mittens}; null = not readable in the photo.
 * Classes sourced from the row's stored prompt (unadjudicated rows)
 * share the shape; [GeneratedImage.garmentSource] records which.
 */
@Serializable
data class GarmentClasses(
    @SerialName("topBase") val topBase: String? = null,
    @SerialName("outer") val outer: Boolean? = null,
    @SerialName("mid") val mid: Boolean? = null,
    @SerialName("bottom") val bottom: String? = null,
    @SerialName("head") val head: String? = null,
    @SerialName("hands") val hands: String? = null,
    @SerialName("sunglasses") val sunglasses: Boolean? = null,
    @SerialName("gaiter") val gaiter: Boolean? = null
)

/**
 * Database model for generated_images table.
 * review_status arrives with the 2026-10-27 review-gate migration
 * ('approved' | 'pending' | 'rejected'); until that migration is applied
 * the column does not exist and the field decodes as null, which
 * HeroPoolPolicy treats as approved — serving is unchanged either way.
 *
 * person_count / garment_classes arrive with the RW-20 backfill
 * (2026-11-02) and feed the point-grain serving filter
 * (HeroPointGrainPolicy): null decodes on rows the backfill has not
 * reached, and the filter fails closed on unreadable garments.
 */
@Serializable
data class GeneratedImage(
    @SerialName("id") val id: String,
    @SerialName("combination_id") val combinationId: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null,
    @SerialName("person_count") val personCount: Int? = null,
    @SerialName("garment_classes") val garmentClasses: GarmentClasses? = null,
    @SerialName("garment_source") val garmentSource: String? = null
)


