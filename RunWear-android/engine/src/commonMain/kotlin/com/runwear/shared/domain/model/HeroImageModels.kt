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
         *
         * Frame contract (RW-23): [frameFeelsLikeF] is the comfort-adjusted
         * feels-like in °F — the same value the outfit's warmth selectors
         * read (see GetOutfitRecommendationUseCase.adjustedFeelsLikeF).
         * The temp bracket derives from that value, NEVER from the raw
         * feels-like, so the retrieval key and the prescription can never
         * key off different frames.
         */
        fun fromConditions(
            weather: WeatherConditions,
            outfit: OutfitRecommendation,
            gender: GenderPreference,
            frameFeelsLikeF: Double
        ): OutfitCombination {
            val tempBracket = TempBracket.fromFahrenheit(frameFeelsLikeF)
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
    val candidates: List<HeroCandidate> = emptyList(),
    /**
     * WS-7 / RW-26(c): which layer served this hero — recorded on the
     * result so every paint can emit its serve-source record (D-18's
     * attribution fix). Null only for synthetic results that never
     * reached a serving layer (null weather/outfit).
     */
    val serveSource: HeroServeSource? = null,
    /**
     * WS-7 / D-22: for a bundled-fallback serve, the manifest
     * provenance of the exact asset served (its pool source
     * combination + row and its point-grain rung verdict). Null for
     * pool and personal serves, whose identity rides in [imageRowId] /
     * [servedCombinationId].
     */
    val fallbackProvenance: FallbackAssetProvenance? = null,
    /** WS-7: the bundled asset file served, for a bundled serve. */
    val fallbackAssetFile: String? = null
)

/**
 * WS-7 / RW-26(c): the layer that served a hero paint. The wire names
 * are the serve-source vocabulary of record: pool / bundled-fallback /
 * personal / scaffold.
 */
enum class HeroServeSource(val wire: String) {
    POOL("pool"),
    BUNDLED_FALLBACK("bundled-fallback"),
    PERSONAL("personal"),
    SCAFFOLD("scaffold")
}

/**
 * WS-7 / D-22: point-grain standing for one bundled fallback asset —
 * the precomputed prescription verdict the pool obeys, recorded per
 * slot in the staged RW-26 bundle manifest
 * (release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md). [rung]
 * is the manifest's rung: T1 = strict rung 1 (every garment class
 * legible and exact at the cell's cold-end point prescription);
 * T3 = recalibrated rung 1 (every legible class exact incl. head and
 * hands; base unperceived under a present shell — Sol's Nov 2 gate
 * rulings 2 + 5). A bundled serve carries this record, so it is
 * auditable exactly like a pool serve and never exempt from the
 * outfit rules by construction.
 */
data class FallbackAssetProvenance(
    val combinationId: String,
    val sourceRowId: String,
    val rung: String
)

/**
 * WS-7 / RW-26(c): one hero serve-source record — the attributable
 * record emitted at serve time for every hero paint. [summary] is the
 * single-line form the diagnostics channel carries.
 */
data class HeroServeRecord(
    val source: HeroServeSource,
    val requestedCombinationId: String? = null,
    val servedCombinationId: String? = null,
    val imageRowId: String? = null,
    val tempBracket: TempBracket? = null,
    val weatherCondition: HeroWeatherCondition? = null,
    val gender: GenderPreference? = null,
    val fallbackAssetFile: String? = null,
    val fallbackProvenance: FallbackAssetProvenance? = null,
    val trigger: String? = null
) {
    fun summary(): String = buildString {
        append("hero_serve source=").append(source.wire)
        tempBracket?.let { append(" bracket=").append(it.name) }
        weatherCondition?.let { append(" weather=").append(it.name) }
        gender?.let { append(" gender=").append(it.name) }
        requestedCombinationId?.let { append(" requested=").append(it) }
        servedCombinationId?.let { append(" served=").append(it) }
        imageRowId?.let { append(" row=").append(it) }
        fallbackAssetFile?.let { append(" asset=").append(it) }
        fallbackProvenance?.let {
            append(" provenance_combination=").append(it.combinationId)
            append(" provenance_row=").append(it.sourceRowId)
            append(" rung=").append(it.rung)
        }
        trigger?.let { append(" trigger=").append(it) }
    }
}

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
     * WS-7 (RW-26 full fix, 2026-11-03): the gender-paired fallback
     * matrix — 22 cells x MALE/FEMALE = 44 bundled assets, staged in
     * release-prep/rw26-fallback-bundle-2026-11-03/ (23 NEW + 12
     * RE-RENDER + 9 KEEP against the RW-20 set it replaces; the 5
     * re-adjudication-rejected RW-20 sources — cool_clear,
     * cool_cloudy, cold_rain, mild_rain, hot_clear — are retired).
     * FREEZING x RAIN is a real cell again (Sol's Nov 3 ruling): the
     * RW-20 designed degradation (FREEZING RAIN -> freezing_clear) is
     * RETIRED — the cell serves its own paired assets. HOT x SNOW and
     * WARM x SNOW remain non-cells (physically unreachable, no stock).
     * Keep in sync with HeroImageRepository's copy of this table (a
     * unit test asserts the two agree).
     */
    private val FALLBACK_IMAGES_PAIRED: Map<Pair<TempBracket, HeroWeatherCondition>, Map<GenderPreference, String>> = mapOf(
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
     * The UNISEX view of the matrix: the legacy chain's table. Each of
     * the 21 legacy cells maps to the paired file whose subject is the
     * gender the RW-20 asset for that cell carried (the
     * FALLBACK_SUBJECT_GENDER census below), so a UNISEX request keeps
     * the legacy chain byte-for-byte — exact cell -> bracket CLEAR ->
     * MILD x CLEAR, landing on the same subject it always landed on.
     * FREEZING x RAIN is deliberately absent here: under the legacy
     * chain it still degrades to freezing_clear, exactly as before
     * WS-7. No UNISEX-specific asset exists or is needed.
     */
    private val FALLBACK_IMAGES_2D: Map<TempBracket, Map<HeroWeatherCondition, String>> by lazy {
        FALLBACK_SUBJECT_GENDER.entries
            .map { (cell, subject) ->
                cell.first to (cell.second to FALLBACK_IMAGES_PAIRED.getValue(cell).getValue(subject))
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toMap() }
    }

    /**
     * WS-7 / D-22: per-asset provenance for every bundled file, from
     * the staged bundle manifest — the slot's pool source combination,
     * source row id, and point-grain rung verdict (T1/T3). Keyed by
     * asset file base name (no extension).
     */
    private val FALLBACK_PROVENANCE: Map<String, FallbackAssetProvenance> = mapOf(
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

    /** The bundled asset file base name for a fallback URL. */
    fun fallbackAssetFile(url: String): String =
        url.substringAfterLast('/').removeSuffix(".webp")

    /** WS-7 / D-22: the manifest provenance of a bundled asset URL, or null. */
    fun fallbackProvenance(url: String): FallbackAssetProvenance? =
        FALLBACK_PROVENANCE[fallbackAssetFile(url)]

    /**
     * The gender-paired bundled asset for an exact cell, or null when
     * the cell is a non-cell (HOT x SNOW, WARM x SNOW) or the gender
     * is UNISEX (UNISEX has no paired file; it uses the legacy view).
     */
    fun pairedFallbackAsset(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition,
        gender: GenderPreference
    ): String? = FALLBACK_IMAGES_PAIRED[tempBracket to weatherCondition]?.get(gender)

    /**
     * WS-7: the full paired table (cell -> gender -> asset URL).
     * Public so tests can pin the 44-slot matrix and mirrors can be
     * checked.
     */
    fun pairedFallbackTable(): Map<Pair<TempBracket, HeroWeatherCondition>, Map<GenderPreference, String>> =
        FALLBACK_IMAGES_PAIRED

    /** WS-7: the full provenance table, keyed by asset file base name. */
    fun fallbackProvenanceTable(): Map<String, FallbackAssetProvenance> = FALLBACK_PROVENANCE

    /**
     * Get a fallback/placeholder image synchronously.
     * Uses 2D matrix: temp bracket × weather condition.
     * Use HeroImageRepository.getHeroImage() for live AI images.
     */
    fun getFallbackImage(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX,
        frameFeelsLikeF: Double
    ): HeroImageResult {
        if (weather == null || outfit == null) {
            return HeroImageResult(
                imageUrl = null,
                thumbnailUrl = null,
                combinationId = "unknown"
            )
        }

        val combination = OutfitCombination.fromConditions(weather, outfit, gender, frameFeelsLikeF)
        val weatherCondition = HeroWeatherCondition.fromWeatherCode(weather.weatherCode)
        val url = getWeatherAwareFallback(combination.tempBracket, weatherCondition, gender)

        return HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id,
            serveSource = HeroServeSource.BUNDLED_FALLBACK,
            fallbackProvenance = fallbackProvenance(url),
            fallbackAssetFile = fallbackAssetFile(url)
        )
    }

    /**
     * RW-26 (2026-11-02), reframed by WS-7 (2026-11-03): the
     * photographed subject's gender of the RW-20-era bundled asset for
     * each of the 21 legacy cells, from the RW-20-verified provenance
     * of its source pool row (census and field diagnosis in
     * release-prep/rw-gender-preference-diagnosis-2026-11-02.md).
     *
     * Since WS-7 every cell carries BOTH genders' paired assets, so
     * this census no longer decides gendered serves — the paired
     * table does. Its remaining job is the UNISEX legacy view
     * (FALLBACK_IMAGES_2D above): it names which paired file the
     * legacy chain lands on for each legacy cell, preserving the
     * subject a UNISEX request has always been served. FREEZING x
     * RAIN has no entry: it was not a legacy cell, and the legacy
     * chain still degrades it to freezing_clear.
     */
    private val FALLBACK_SUBJECT_GENDER: Map<Pair<TempBracket, HeroWeatherCondition>, GenderPreference> = mapOf(
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

    /** Warmth order (coldest first) for walking neighbouring brackets. */
    private val BRACKET_WARMTH_ORDER: List<TempBracket> = listOf(
        TempBracket.FREEZING, TempBracket.COLD, TempBracket.COOL,
        TempBracket.MILD, TempBracket.WARM, TempBracket.HOT
    )

    /**
     * The subject gender of the cell's UNISEX legacy-view asset, or
     * null when the cell was not a legacy cell (HOT×SNOW, WARM×SNOW,
     * FREEZING×RAIN). Gendered serves do not consult this — every
     * paired cell serves its own gender's asset (see
     * [pairedFallbackAsset]). Public so tests can pin the census and
     * mirrors can be checked.
     */
    fun fallbackSubjectGender(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition
    ): GenderPreference? = FALLBACK_SUBJECT_GENDER[tempBracket to weatherCondition]

    /**
     * Candidate cells for a fallback request, most-preferred first: the
     * requested cell, then the rest of its bracket (CLEAR, CLOUDY, RAIN,
     * SNOW), then neighbouring brackets by warmth distance (warmer first
     * on a tie — lighter garments travel better than heavier ones), each
     * contributing its requested-weather cell first, then CLEAR, CLOUDY,
     * RAIN, SNOW. Only cells that actually hold an asset are returned.
     */
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
     * Get the weather-aware fallback image reference for a cell.
     * Public so unit tests can assert every cell resolves to a bundled
     * pool-derived asset and both copies of the table agree.
     */
    fun getWeatherAwareFallback(tempBracket: TempBracket, weatherCondition: HeroWeatherCondition): String =
        getWeatherAwareFallback(tempBracket, weatherCondition, GenderPreference.UNISEX)

    /**
     * WS-7 bound (D-19, 2026-11-03) — supersedes the RW-26 walk for
     * gendered requests: with every cell paired, a MALE or FEMALE
     * request STARTS AND NORMALLY ENDS at the requested cell's own
     * gendered asset. Subject gender NEVER outranks the scene at a
     * paired cell: the walk may not cross a weather class to satisfy
     * gender when the requested cell's paired asset exists — post-
     * bundle it always does (22 cells x 2 genders). The deterministic
     * RW-26 construction that resolved MALE + COOL + CLEAR to the
     * snow asset (the only male subject in the old COOL bracket) now
     * resolves to cool_clear_male. The scene-first walk below is
     * reachable only from a non-cell (HOT x SNOW, WARM x SNOW),
     * where no paired asset exists at the requested cell: it visits
     * the bracket's own weathers before neighbouring brackets and
     * takes the requested gender's paired asset at each stop, so
     * even there gender is satisfied inside the nearest scene.
     * UNISEX keeps the legacy chain exactly (exact cell -> bracket
     * CLEAR -> MILD x CLEAR over the legacy view), as does a
     * gendered request in the (today impossible) case that no paired
     * asset exists anywhere in the walk.
     */
    fun getWeatherAwareFallback(
        tempBracket: TempBracket,
        weatherCondition: HeroWeatherCondition,
        gender: GenderPreference
    ): String {
        if (gender != GenderPreference.UNISEX) {
            pairedFallbackAsset(tempBracket, weatherCondition, gender)?.let { return it }
            for (cell in orderedFallbackCells(tempBracket, weatherCondition)) {
                pairedFallbackAsset(cell.first, cell.second, gender)?.let { return it }
            }
        }

        // Legacy chain (UNISEX, or no paired asset anywhere).
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
            ?: "file:///android_asset/hero_fallback/mild_clear_female.webp"
    }

    /**
     * Get just the URL string for simple usage.
     */
    fun getImageUrl(
        weather: WeatherConditions?,
        outfit: OutfitRecommendation?,
        gender: GenderPreference = GenderPreference.UNISEX,
        frameFeelsLikeF: Double
    ): String? {
        return getFallbackImage(weather, outfit, gender, frameFeelsLikeF).imageUrl
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
