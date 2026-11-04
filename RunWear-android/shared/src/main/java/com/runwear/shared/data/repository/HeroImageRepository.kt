package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.FallbackAssetProvenance
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
            val result = if (searchResult.image == null) {
                getFallbackImage(combination, outfit)
            } else {
                poolResult(combination, searchResult, outfit)
            }
            // WS-7 / RW-26(c): every serve records its source at serve
            // time (pool / bundled-fallback; a rotation-floor pick of
            // the bundled candidate records as bundled-fallback — the
            // URL is the ground truth inside emitForResult).
            HeroServeTelemetry.emitForResult(
                result, combination,
                trigger = if (searchResult.image == null) "bundled-fallback" else "pool-cascade"
            )
            result
        } catch (e: Exception) {
            // On error, return fallback
            val result = getFallbackImage(combination, outfit)
            HeroServeTelemetry.emitForResult(result, combination, trigger = "bundled-fallback-error")
            result
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
        searchResult: ImageSearchResult,
        outfit: OutfitRecommendation
    ): HeroImageResult {
        val image = searchResult.image ?: return getFallbackImage(combination, outfit)
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
        // GEN-2 A2 (W-4): the floor candidate joins the rotation ONLY
        // when its adjudicated garments match this prescription
        // (gatedFloorCandidate) — pre-fix it entered unchecked, so the
        // rotation could deal the bracket's cold-end photo (COOL:
        // headband + gloves) under a prescription carrying neither.
        val floorCandidate = gatedFloorCandidate(combination, outfit)
        val candidates = if (floorCandidate != null &&
            poolCandidates.none { it.imageUrl == floorCandidate.imageUrl }
        ) {
            poolCandidates + floorCandidate
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
                candidates = candidates,
                serveSource = com.runwear.shared.domain.model.HeroServeSource.POOL
            )
        }
        val picked = candidates.random()
        // WS-7: a random pick that lands on the rotation-floor bundled
        // candidate (no pool row id) IS a bundled-fallback serve, and
        // records as one — with the asset's manifest provenance.
        val pickedIsBundled = picked.imageRowId == null &&
            picked.imageUrl.startsWith("file:///android_asset/hero_fallback/")
        return HeroImageResult(
            imageUrl = picked.imageUrl,
            thumbnailUrl = picked.thumbnailUrl ?: picked.imageUrl,
            combinationId = combination.id,
            imageRowId = picked.imageRowId,
            servedCombinationId = picked.servedCombinationId,
            candidates = candidates,
            serveSource = if (pickedIsBundled) {
                com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK
            } else {
                com.runwear.shared.domain.model.HeroServeSource.POOL
            },
            fallbackProvenance = if (pickedIsBundled) {
                com.runwear.shared.domain.model.HeroImageSelector.fallbackProvenance(picked.imageUrl)
            } else {
                null
            },
            fallbackAssetFile = if (pickedIsBundled) {
                com.runwear.shared.domain.model.HeroImageSelector.fallbackAssetFile(picked.imageUrl)
            } else {
                null
            }
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
     *
     * GEN-2 A2 (W-4/W-5): the resolution is GATED by the exact
     * prescription — it walks the bundled matrix through
     * [com.runwear.shared.domain.model.HeroImageSelector.gatedFallbackUrl],
     * which serves only an asset whose adjudicated garments match
     * [outfit]. The pre-fix resolver keyed on bracket × weather ×
     * gender alone, and the matrix's assets are exact at their
     * bracket's COLD-END prescription (COOL's wear a headband and
     * gloves), so any warmer point prescription in the bracket was
     * silently served a photo wearing garments it did not prescribe
     * — and a within-bracket prescription change could never change
     * the photo, its key having no prescription component. When no
     * bundled asset matches, the result carries NO image (the
     * gradient state) — never a contradicting photo.
     */
    private fun getFallbackImage(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ): HeroImageResult {
        val weatherCondition = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        val url = com.runwear.shared.domain.model.HeroImageSelector.gatedFallbackUrl(
            combination.tempBracket, weatherCondition, combination.genderPreference, outfit
        )

        return HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id,
            serveSource = com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK,
            fallbackProvenance = url?.let {
                com.runwear.shared.domain.model.HeroImageSelector.fallbackProvenance(it)
            },
            fallbackAssetFile = url?.let {
                com.runwear.shared.domain.model.HeroImageSelector.fallbackAssetFile(it)
            }
        )
    }

    /**
     * GEN-2 A2 (W-4): the rotation-floor candidate for one
     * resolution, or null when the floor may not deal. The floor
     * stands (HeroPoolPolicy: floor cells only) and the candidate is
     * resolved gender-aware as before (RW-26) — but it joins the
     * rotation ONLY when the asset's adjudicated garments match the
     * current prescription, under the same point-grain gate as every
     * other serve. Internal (not private) so the gate is unit-tested
     * without a Supabase client.
     */
    internal fun gatedFloorCandidate(
        combination: OutfitCombination,
        outfit: OutfitRecommendation
    ): com.runwear.shared.domain.model.HeroCandidate? {
        val heroWeather = HeroWeatherCondition.fromWeatherCode(combination.weatherCode)
        if (!HeroPoolPolicy.isFloorCell(combination.tempBracket, heroWeather, combination.timeOfDay)) {
            return null
        }
        val floorUrl = getWeatherAwareFallback(
            combination.tempBracket, heroWeather, combination.genderPreference
        )
        if (!com.runwear.shared.domain.model.HeroImageSelector
                .fallbackMatchesPrescription(floorUrl, outfit)
        ) {
            return null
        }
        return com.runwear.shared.domain.model.HeroCandidate(
            imageUrl = floorUrl,
            thumbnailUrl = floorUrl,
            imageRowId = null,
            servedCombinationId = combination.id
        )
    }

    /**
     * Get weather-aware fallback image URL.
     * Priority: temp + weather match > temp only > default
     */
    internal fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String =
        getWeatherAwareFallback(tempBracket, weatherCondition, GenderPreference.UNISEX)

    /**
     * WS-7 (2026-11-03): gender-aware form — mirror of the engine's
     * HeroImageSelector resolver (the two copies are kept in agreement
     * by HeroFallbackImagesTest / HeroFallbackGenderTest). A MALE or
     * FEMALE request resolves to the requested cell's own paired asset
     * (every cell is paired); gender never outranks the scene (D-19's
     * bound). UNISEX keeps the legacy chain exactly.
     */
    internal fun getWeatherAwareFallback(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition,
        gender: GenderPreference
    ): String {
        // WS-7 bound (D-19): mirror of HeroImageSelector — a gendered
        // request starts and normally ends at the requested cell's own
        // paired asset; gender never outranks the scene at a paired
        // cell. The scene-first walk serves only non-cells.
        if (gender != GenderPreference.UNISEX) {
            pairedFallbackAsset(tempBracket, weatherCondition, gender)?.let { return it }
            for (cell in orderedFallbackCells(tempBracket, weatherCondition)) {
                pairedFallbackAsset(cell.first, cell.second, gender)?.let { return it }
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
            ?: "file:///android_asset/hero_fallback/mild_clear_female.webp"
    }

    /** WS-7: the gender-paired bundled asset for an exact cell (mirror of the engine's). */
    internal fun pairedFallbackAsset(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition,
        gender: GenderPreference
    ): String? = FALLBACK_IMAGES_PAIRED[tempBracket to weatherCondition]?.get(gender)

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
        return cells.filter { FALLBACK_IMAGES_PAIRED.containsKey(it) }
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
         * RW-26 (2026-11-02), reframed by WS-7 (2026-11-03): subject
         * gender of the RW-20-era bundled asset for each legacy cell.
         * Mirror of the engine HeroImageSelector census — the two
         * copies are pinned to each other by HeroFallbackGenderTest.
         * Since WS-7 every cell carries both genders' paired assets;
         * this census now only names which paired file the UNISEX
         * legacy view (FALLBACK_IMAGES_2D) lands on per legacy cell.
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

        /**
         * WS-7 (RW-26 full fix, 2026-11-03): the gender-paired
         * fallback matrix — 22 cells x MALE/FEMALE = 44 bundled
         * assets from the staged bundle manifest
         * (release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md:
         * 23 NEW + 12 RE-RENDER + 9 KEEP). FREEZING x RAIN restored
         * (Sol's Nov 3 ruling) — the RW-20 designed degradation
         * (FREEZING RAIN -> freezing_clear) is RETIRED for gendered
         * serves. Mirror of the engine HeroImageSelector table; the
         * two copies are pinned to each other by the fallback tests.
         */
        internal val FALLBACK_IMAGES_PAIRED: Map<Pair<TempBracket, HeroWeatherCondition>, Map<GenderPreference, String>> = mapOf(
        (TempBracket.FREEZING to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/freezing_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/freezing_clear_male.webp"
        ),
        (TempBracket.FREEZING to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/freezing_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/freezing_cloudy_male.webp"
        ),
        (TempBracket.FREEZING to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/freezing_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/freezing_rain_male.webp"
        ),
        (TempBracket.FREEZING to HeroWeatherCondition.SNOW) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/freezing_snow_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/freezing_snow_male.webp"
        ),
        (TempBracket.COLD to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cold_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cold_clear_male.webp"
        ),
        (TempBracket.COLD to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cold_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cold_cloudy_male.webp"
        ),
        (TempBracket.COLD to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cold_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cold_rain_male.webp"
        ),
        (TempBracket.COLD to HeroWeatherCondition.SNOW) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cold_snow_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cold_snow_male.webp"
        ),
        (TempBracket.COOL to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cool_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cool_clear_male.webp"
        ),
        (TempBracket.COOL to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cool_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cool_cloudy_male.webp"
        ),
        (TempBracket.COOL to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cool_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cool_rain_male.webp"
        ),
        (TempBracket.COOL to HeroWeatherCondition.SNOW) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/cool_snow_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/cool_snow_male.webp"
        ),
        (TempBracket.MILD to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/mild_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/mild_clear_male.webp"
        ),
        (TempBracket.MILD to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/mild_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/mild_cloudy_male.webp"
        ),
        (TempBracket.MILD to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/mild_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/mild_rain_male.webp"
        ),
        (TempBracket.MILD to HeroWeatherCondition.SNOW) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/mild_snow_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/mild_snow_male.webp"
        ),
        (TempBracket.WARM to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/warm_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/warm_clear_male.webp"
        ),
        (TempBracket.WARM to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/warm_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/warm_cloudy_male.webp"
        ),
        (TempBracket.WARM to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/warm_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/warm_rain_male.webp"
        ),
        (TempBracket.HOT to HeroWeatherCondition.CLEAR) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/hot_clear_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/hot_clear_male.webp"
        ),
        (TempBracket.HOT to HeroWeatherCondition.CLOUDY) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/hot_cloudy_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/hot_cloudy_male.webp"
        ),
        (TempBracket.HOT to HeroWeatherCondition.RAIN) to mapOf(
            GenderPreference.FEMALE to "file:///android_asset/hero_fallback/hot_rain_female.webp",
            GenderPreference.MALE to "file:///android_asset/hero_fallback/hot_rain_male.webp"
        ),
        )

        /**
         * WS-7 / D-22: per-asset provenance (pool source combination,
         * source row id, point-grain rung) for every bundled file,
         * from the staged bundle manifest. Mirror of the engine table.
         */
        internal val FALLBACK_PROVENANCE: Map<String, FallbackAssetProvenance> = mapOf(
        "freezing_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_FREEZING_NIGHT_v4", "dc5b7be8-947c-462d-9cf5-d800e867363b", "T1"),
        "freezing_clear_male" to FallbackAssetProvenance("MALE_CLEAR_FREEZING_MIDDAY_v1", "616cdf4f-6dfb-4ca0-bc51-b045f1c32d57", "T3"),
        "freezing_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_FREEZING_MIDDAY_v1177", "6fcb0994-ade3-4490-b8ca-60704b349490", "T3"),
        "freezing_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_FREEZING_MIDDAY_459e0be6", "69ab00a0-e806-4cf4-a848-3014a4e2029d", "T3"),
        "freezing_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_FREEZING_MIDDAY_b7e866a8", "0b891b30-75ed-4337-939c-6219d9f1309a", "T3"),
        "freezing_rain_male" to FallbackAssetProvenance("MALE_RAIN_FREEZING_DAWN_b7e866a8", "ad138c05-da7e-4cc3-ac63-f73bcf3bc21b", "T1"),
        "freezing_snow_female" to FallbackAssetProvenance("FEMALE_SNOW_FREEZING_MIDDAY_v2", "36a47e4d-685a-4d12-98ff-653a88e8d866", "T1"),
        "freezing_snow_male" to FallbackAssetProvenance("MALE_SNOWY_FREEZING_DAWN_v6", "5c9047fc-3958-4991-9ada-a66ac359e295", "T1"),
        "cold_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_COLD_NIGHT_v2", "4a46575d-3b63-4e94-84e0-f07f9558e50a", "T1"),
        "cold_clear_male" to FallbackAssetProvenance("MALE_CLEAR_COLD_MIDDAY_27f65a1f", "dd83ab86-0030-4b27-90cb-3e60221d9ce0", "T1"),
        "cold_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_COLD_MIDDAY_v3", "579a244b-3160-4350-b8a4-0c69b00bd353", "T3"),
        "cold_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_COLD_MIDDAY_v5", "0d69b957-8755-43f7-ba8b-957b6b144eb8", "T1"),
        "cold_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_COLD_MIDDAY_v18", "9ec95942-fb00-4d38-a8bb-845bcd8c149a", "T3"),
        "cold_rain_male" to FallbackAssetProvenance("MALE_RAIN_COLD_MIDDAY_v12", "ef52a837-8354-40fd-a0cd-362696d6ee23", "T3"),
        "cold_snow_female" to FallbackAssetProvenance("FEMALE_SNOW_COLD_DAWN_v4", "a3f54b7c-81e5-4bd9-8362-aa77f3e4b5d3", "T1"),
        "cold_snow_male" to FallbackAssetProvenance("MALE_SNOW_COLD_DAWN_v2", "c0b3f186-89d5-412d-9398-4065af6c39bd", "T1"),
        "cool_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_COOL_MIDDAY_84c10525", "6e8a4250-d42e-415f-b7a1-ffc5723ac453", "T1"),
        "cool_clear_male" to FallbackAssetProvenance("MALE_CLEAR_COOL_MIDDAY_84c10525", "f4b95431-0943-435b-95c7-304b95762e51", "T1"),
        "cool_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_COOL_MIDDAY_84c10525", "ecaa6fe9-701f-4034-b253-e20ff57409ca", "T1"),
        "cool_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_COOL_MIDDAY_84c10525", "3e6ad6b9-992a-4af9-aacb-bfb96325575b", "T1"),
        "cool_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_COOL_DUSK_f213805f", "6a256bd2-29a4-46c6-a832-b604c2299016", "T1"),
        "cool_rain_male" to FallbackAssetProvenance("MALE_RAIN_COOL_MIDDAY_f213805f", "c345ae20-76d8-4b81-8bd7-8643e9770df5", "T3"),
        "cool_snow_female" to FallbackAssetProvenance("FEMALE_SNOW_COOL_MIDDAY_84c10525", "7ad70bbc-f5ac-4b68-90de-b260404c1f3d", "T1"),
        "cool_snow_male" to FallbackAssetProvenance("MALE_SNOW_COOL_MIDDAY_v26", "f8e3791a-6fe2-472c-ad80-e058a00ed792", "T1"),
        "mild_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_MILD_DUSK_v4", "d42b35c7-c78d-49c3-a918-b3cf5adf755a", "T1"),
        "mild_clear_male" to FallbackAssetProvenance("MALE_CLEAR_MILD_MIDDAY_89c065aa", "e6970789-4adb-41a6-8923-22fd40e17a66", "T1"),
        "mild_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_MILD_DUSK_v3", "207ff224-58ee-4869-8cff-5cf3db231f4e", "T1"),
        "mild_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_MILD_MIDDAY_491df101", "72f96eea-edb5-4d0c-97a0-a20530cfe88f", "T1"),
        "mild_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_MILD_DAWN_v15", "f11a3e51-c7ee-4d1e-9d32-e8b7206654f6", "T1"),
        "mild_rain_male" to FallbackAssetProvenance("MALE_RAINY_MILD_NIGHT_v3", "a9c80dda-fe17-4ad5-88c9-fdf353def761", "T1"),
        "mild_snow_female" to FallbackAssetProvenance("FEMALE_SNOW_MILD_DAWN_v16", "f0f85719-0185-4c4c-8036-b03ef2757738", "T1"),
        "mild_snow_male" to FallbackAssetProvenance("MALE_SNOW_MILD_MIDDAY_v11", "b756edc2-9f19-4ec1-942c-b071ba1e3709", "T1"),
        "warm_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_WARM_MIDDAY_89c065aa", "9886f191-13dd-4ae2-bcce-9f1094fcc9ca", "T1"),
        "warm_clear_male" to FallbackAssetProvenance("MALE_CLEAR_WARM_MIDDAY_v3", "9e4d573a-24cf-4d25-b454-888d9c121a71", "T1"),
        "warm_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_WARM_MIDDAY_v2", "7d3d5933-1dd7-4cd1-89ac-d7c06be8a836", "T1"),
        "warm_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_WARM_DAWN_v5", "121c9ee8-87b3-4e81-9090-e982ea35e62e", "T1"),
        "warm_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_WARM_DAWN_v867", "952172e9-748b-44d7-a68a-3652496d2625", "T1"),
        "warm_rain_male" to FallbackAssetProvenance("MALE_RAINY_WARM_MIDDAY_v4", "c368c886-a602-448b-93d7-4b7f9f1871a2", "T1"),
        "hot_clear_female" to FallbackAssetProvenance("FEMALE_CLEAR_HOT_MIDDAY_v1067", "08c2e0ac-0631-46ad-9a76-36eda0a0bc30", "T1"),
        "hot_clear_male" to FallbackAssetProvenance("MALE_CLEAR_HOT_MIDDAY_v1", "01cae610-4e85-404a-ad6b-79ce037c1d88", "T1"),
        "hot_cloudy_female" to FallbackAssetProvenance("FEMALE_CLOUDY_HOT_MIDDAY_v1", "52097737-5b23-4419-bb63-917d84beaf7d", "T1"),
        "hot_cloudy_male" to FallbackAssetProvenance("MALE_CLOUDY_HOT_DUSK_v2", "c6b19c8f-7e31-45a9-9a83-ba30d1cf9363", "T1"),
        "hot_rain_female" to FallbackAssetProvenance("FEMALE_RAIN_HOT_MIDDAY_49cd4d15", "6ef05e97-3003-428a-9bce-0e61b11b0bee", "T1"),
        "hot_rain_male" to FallbackAssetProvenance("MALE_RAIN_HOT_DAWN_49cd4d15", "66c5acd7-d0a6-4eba-b765-f12ec29e2eca", "T1"),
        )

        /**
         * The UNISEX legacy view: the 21 legacy cells, each mapped to
         * the paired file whose subject matches the RW-20 census
         * (FALLBACK_SUBJECT_GENDER) — the legacy chain (exact cell ->
         * bracket CLEAR -> MILD x CLEAR) lands exactly where it
         * always landed. FREEZING x RAIN is absent by design, so the
         * legacy chain still degrades it to freezing_clear.
         */
        internal val FALLBACK_IMAGES_2D: Map<TempBracket, Map<HeroWeatherCondition, String>> by lazy {
            FALLBACK_SUBJECT_GENDER.entries
                .map { (cell, subject) ->
                    cell.first to (cell.second to FALLBACK_IMAGES_PAIRED.getValue(cell).getValue(subject))
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toMap() }
        }
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


