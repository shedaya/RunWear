package com.runwear.shared.domain.model

/**
 * GEN-4 HERO FALLBACK (Sol's ruling, 2026-11-04 ~20:08 CST): the app
 * must NEVER settle on the bare gradient hero. The RW-20
 * no-wrong-outfit rule governs what a photo may CLAIM to be — it is
 * not a licence for an empty hero. The gen-3 servability census
 * (release-prep/rw-gen3-ahero-2026-11-04/servability-census.md) proved
 * 26 of the 50 gendered gate signatures resolve to NO photo under the
 * exact gate alone: the mittens vocabulary gap (the pool holds zero
 * mittens rows), warm-point long-sleeve + shorts with bare head/hands,
 * warm-point tights combinations, and the male/sleeveless visor gaps.
 *
 * This file is the full fallback chain for the stock hero, resolved
 * from the LIVE candidate set at serve time (no per-signature table —
 * when the replenishment wave's exact rows land in the approved pool,
 * tier 1 wins automatically through the same distribution path):
 *
 *  1. EXACT — a candidate (approved pool row, or bundled asset) that
 *     passes [HeroPointGrainPolicy] for the prescription. Pool exact
 *     beats bundled exact (the pool is the live library; the bundle
 *     is its floor copy). A photo served at this tier may claim the
 *     prescription.
 *  2. SCAFFOLD — no exact photo exists: the NEAREST covered photo
 *     from the live approved pool serves as a scaffold, under the
 *     metric below. A scaffold NEVER claims the prescription: it is
 *     labelled by serve source ([HeroServeSource.SCAFFOLD]) and by
 *     [HeroChainResolution.isScaffold] at every surface that inherits
 *     the resolution (hero, Wear I've Run record/thumbnails,
 *     poster) — never recorded as the exact photo.
 *  3. FLOOR — the pool is unreachable or empty: the nearest BUNDLED
 *     asset under the same metric. The bundled set covers every
 *     gender × bracket-family cell, so this tier always resolves
 *     non-null for a gendered request.
 *
 * LABELLING (existing convention, not new copy): personal scaffolds
 * badge "Your photo" on the hero pill (ScaffoldRenderPolicy); STOCK
 * non-exact serves have always been quiet on the home surface
 * (PersonalServeKind.STOCK_QUIET: "a gate-failed cell serves stock
 * quietly: the home screen never badges it"). Stock scaffold/floor
 * serves follow the stock convention exactly — no home badge — and
 * carry their honest label in the serve record / state
 * (source = "scaffold", isScaffold = true), which is where every
 * non-personal serve has always been attributed (HeroServeRecord).
 *
 * THE METRIC (deterministic nearest neighbour over the gate space).
 * Hard constraint: gender is NEVER crossed — a candidate of another
 * gender is infinitely far (UNISEX requests, which name no gender,
 * may take either). Among same-gender candidates, distance is a
 * LEXICOGRAPHIC tuple, compared component by component, so the
 * ordering guarantees hold by construction rather than by tuned
 * weights:
 *
 *  1. bracket distance — steps between the candidate's temperature
 *     bracket and the requested bracket in warmth order (FREEZING…
 *     HOT). Temperature dominates everything below it: a same-bracket
 *     photo differing in headwear beats a different-bracket photo
 *     with the exact hat. (An unparseable candidate bracket counts
 *     as 2 — worse than an adjacent bracket, better than a far one.)
 *  2. weather distance — 0 for the same weather class, 1 otherwise
 *     (scene proximity, as the RW-26 cell walk already ordered it).
 *  3. bottom distance — coverage steps between bottom families:
 *     shorts family {shorts, short_shorts} = 0, capris = 1,
 *     tights = 2. The bottom is the most visible garment (the RW-20
 *     field failure's core contradiction), so it leads the garment
 *     components.
 *  4. top-base distance — sleeve steps: sleeveless = 0, short = 1,
 *     long = 2. A base hidden under a present shell counts 0 (it is
 *     not visible — the same reading [HeroPointGrainPolicy] makes).
 *  5. outer distance — 0/1 for shell presence agreement.
 *  6. head distance — coverage steps: none = 0, visor/cap = 1,
 *     headband = 2, beanie = 3, balaclava = 4 (visor and cap are the
 *     same sun-coverage step; their difference is charged in the
 *     mismatch count below, not here).
 *  7. hands distance — coverage steps: none = 0, gloves = 1,
 *     mittens = 2. This is what makes a mittens prescription scaffold
 *     to the nearest GLOVES photo of its gender/bracket, never to
 *     bare hands while a gloves photo exists.
 *  8. mismatch count — the number of the five garment slots whose
 *     class differs at all (breaks visor-vs-cap style ties toward
 *     the photo that differs in fewer slots).
 *  9. source rank — pool (0) before bundled (1): the live library is
 *     preferred at equal distance.
 * 10. URL — ascending, so the resolution is fully deterministic.
 */

/** Which tier of the GEN-4 chain served a hero resolution. */
enum class HeroChainTier { EXACT, SCAFFOLD, FLOOR }

/** Where a chain candidate lives. */
enum class HeroCandidateSource { POOL, BUNDLED }

/**
 * The adjudicated garment snapshot of one candidate photo, in the
 * RW-20 vocabulary ([HeroPointGrainPolicy]'s classes). Null = the
 * class could not be read in the photo (the exact gate treats an
 * unreadable slot exactly as it does for pool rows).
 */
data class HeroGarmentSnapshot(
    val topBase: String?,
    val outer: Boolean?,
    val bottom: String?,
    val head: String?,
    val hands: String?
)

/** One photo the chain may serve, with everything the metric needs. */
data class HeroPhotoCandidate(
    val url: String,
    val source: HeroCandidateSource,
    val gender: GenderPreference,
    val bracket: TempBracket?,
    val weather: HeroWeatherCondition?,
    val garments: HeroGarmentSnapshot,
    val personCount: Int? = 1,
    /** Pool row id (pool candidates) — the flag-report identity. */
    val rowId: String? = null,
    /** The candidate's own combination id (pool candidates). */
    val combinationId: String? = null,
    /** Bundled asset file base name (bundled candidates). */
    val assetFile: String? = null
)

/** The chain's verdict for one prescription. */
data class HeroChainResolution(
    val candidate: HeroPhotoCandidate,
    val tier: HeroChainTier,
    val distance: HeroScaffoldDistance
) {
    val url: String get() = candidate.url

    /** True iff this serve must be labelled scaffold, never exact. */
    val isScaffold: Boolean get() = tier != HeroChainTier.EXACT

    /** The serve-source vocabulary of record for this resolution. */
    val serveSource: HeroServeSource
        get() = when {
            isScaffold -> HeroServeSource.SCAFFOLD
            candidate.source == HeroCandidateSource.POOL -> HeroServeSource.POOL
            else -> HeroServeSource.BUNDLED_FALLBACK
        }
}

/**
 * The lexicographic scaffold distance (see the metric definition at
 * the top of this file). Comparable component-by-component in field
 * order; [compareTo] is the metric.
 */
data class HeroScaffoldDistance(
    val bracketSteps: Int,
    val weatherSteps: Int,
    val bottomSteps: Int,
    val topSteps: Int,
    val outerSteps: Int,
    val headSteps: Int,
    val handsSteps: Int,
    val mismatchCount: Int,
    val sourceRank: Int,
    val url: String
) : Comparable<HeroScaffoldDistance> {
    override fun compareTo(other: HeroScaffoldDistance): Int =
        compareValuesBy(
            this, other,
            { it.bracketSteps }, { it.weatherSteps }, { it.bottomSteps },
            { it.topSteps }, { it.outerSteps }, { it.headSteps },
            { it.handsSteps }, { it.mismatchCount }, { it.sourceRank },
            { it.url }
        )
}

object HeroFallbackChain {

    private val WARMTH_ORDER: List<TempBracket> = listOf(
        TempBracket.FREEZING, TempBracket.COLD, TempBracket.COOL,
        TempBracket.MILD, TempBracket.WARM, TempBracket.HOT
    )

    /** Coverage rank of a bottom class (family-collapsed). */
    private fun bottomRank(bottomClass: String?): Int = when (bottomClass) {
        "shorts", "short_shorts" -> 0
        "capris" -> 1
        "tights" -> 2
        else -> 1 // unreadable: mid penalty, never free
    }

    /** Sleeve rank of a top-base class. */
    private fun topRank(topBaseClass: String?): Int = when (topBaseClass) {
        "sleeveless" -> 0
        "short" -> 1
        "long" -> 2
        else -> 1
    }

    /** Coverage rank of a head class; visor and cap share the sun step. */
    private fun headRank(headClass: String?): Int = when (headClass) {
        null -> 0
        "visor", "cap" -> 1
        "headband" -> 2
        "beanie" -> 3
        "balaclava" -> 4
        else -> 1
    }

    /** Coverage rank of a hands class. */
    private fun handsRank(handsClass: String?): Int = when (handsClass) {
        null -> 0
        "gloves" -> 1
        "mittens" -> 2
        else -> 1
    }

    /** The prescription's garment snapshot, in candidate vocabulary. */
    fun prescriptionSnapshot(outfit: OutfitRecommendation): HeroGarmentSnapshot =
        HeroGarmentSnapshot(
            topBase = when (outfit.topBase) {
                ClothingItem.TANK_TOP -> "sleeveless"
                ClothingItem.SHORT_SLEEVE -> "short"
                else -> "long"
            },
            outer = outfit.topOuter != null,
            bottom = when (outfit.bottom) {
                ClothingItem.SHORT_SHORTS -> "short_shorts"
                ClothingItem.SHORTS -> "shorts"
                ClothingItem.CAPRIS -> "capris"
                else -> "tights"
            },
            head = when (outfit.head) {
                null -> null
                ClothingItem.VISOR -> "visor"
                ClothingItem.BASEBALL_CAP -> "cap"
                ClothingItem.HEADBAND -> "headband"
                ClothingItem.LIGHT_BEANIE, ClothingItem.THERMAL_BEANIE -> "beanie"
                ClothingItem.BALACLAVA -> "balaclava"
                else -> "other"
            },
            hands = when (outfit.hands) {
                null -> null
                ClothingItem.LIGHT_GLOVES, ClothingItem.THERMAL_GLOVES -> "gloves"
                ClothingItem.MITTENS -> "mittens"
                else -> "other"
            }
        )

    /**
     * The metric: distance from [candidate] to the prescription
     * ([outfit] at [bracket]/[weather]). Callers must have already
     * applied the gender hard constraint ([resolve] does).
     */
    fun distance(
        candidate: HeroPhotoCandidate,
        outfit: OutfitRecommendation,
        bracket: TempBracket,
        weather: HeroWeatherCondition
    ): HeroScaffoldDistance {
        val prescribed = prescriptionSnapshot(outfit)
        val worn = candidate.garments
        val bracketSteps = candidate.bracket?.let {
            kotlin.math.abs(WARMTH_ORDER.indexOf(it) - WARMTH_ORDER.indexOf(bracket))
        } ?: 2
        val weatherSteps = if (candidate.weather == weather) 0 else 1
        val bottomSteps = kotlin.math.abs(bottomRank(worn.bottom) - bottomRank(prescribed.bottom))
        // A base hidden under a present shell is not visible: it
        // cannot contradict, so it costs nothing (HeroPointGrainPolicy
        // reads it the same way).
        val topSteps = if (worn.topBase == null && worn.outer == true) {
            0
        } else {
            kotlin.math.abs(topRank(worn.topBase) - topRank(prescribed.topBase))
        }
        val outerSteps = if ((worn.outer == true) == (prescribed.outer == true)) 0 else 1
        val headSteps = kotlin.math.abs(headRank(worn.head) - headRank(prescribed.head))
        val handsSteps = kotlin.math.abs(handsRank(worn.hands) - handsRank(prescribed.hands))
        // Mismatch count: slots that differ at all. Bottom counts at
        // family rank (shorts vs short_shorts is one family — the
        // exact gate treats them as one too); head/hands count on
        // class identity, so visor-vs-cap still registers here even
        // though their coverage steps tie.
        var mismatches = 0
        if (bottomRank(worn.bottom) != bottomRank(prescribed.bottom)) mismatches++
        if (topSteps > 0) mismatches++
        if (outerSteps > 0) mismatches++
        if (worn.head != prescribed.head) mismatches++
        if (worn.hands != prescribed.hands) mismatches++
        return HeroScaffoldDistance(
            bracketSteps = bracketSteps,
            weatherSteps = weatherSteps,
            bottomSteps = bottomSteps,
            topSteps = topSteps,
            outerSteps = outerSteps,
            headSteps = headSteps,
            handsSteps = handsSteps,
            mismatchCount = mismatches,
            sourceRank = if (candidate.source == HeroCandidateSource.POOL) 0 else 1,
            url = candidate.url
        )
    }

    /** Does this candidate pass the exact point-grain gate? */
    fun isExact(candidate: HeroPhotoCandidate, outfit: OutfitRecommendation): Boolean =
        HeroPointGrainPolicy.matches(
            personCount = candidate.personCount,
            topBaseClass = candidate.garments.topBase,
            outerClass = candidate.garments.outer,
            bottomClass = candidate.garments.bottom,
            prescribedTopBase = outfit.topBase,
            prescribedTopOuter = outfit.topOuter,
            prescribedBottom = outfit.bottom,
            headClass = candidate.garments.head,
            handsClass = candidate.garments.hands,
            prescribedHead = outfit.head,
            prescribedHands = outfit.hands
        )

    /**
     * Resolve the chain for one prescription from the live candidate
     * set. Gender is a hard filter (never crossed; UNISEX names no
     * gender and may take either). Multi-person candidates never
     * serve at any tier (RW-20). Returns null only when no
     * same-gender candidate exists at all — impossible for a
     * gendered request once the bundled candidates are included.
     */
    fun resolve(
        gender: GenderPreference,
        bracket: TempBracket,
        weather: HeroWeatherCondition,
        outfit: OutfitRecommendation,
        poolCandidates: List<HeroPhotoCandidate>,
        bundledCandidates: List<HeroPhotoCandidate>
    ): HeroChainResolution? {
        fun eligible(c: HeroPhotoCandidate): Boolean =
            (gender == GenderPreference.UNISEX || c.gender == gender) &&
                (c.personCount == null || c.personCount < 2)
        val pool = poolCandidates.filter(::eligible)
        val bundled = bundledCandidates.filter(::eligible)

        fun nearest(candidates: List<HeroPhotoCandidate>): Pair<HeroPhotoCandidate, HeroScaffoldDistance>? =
            candidates
                .map { it to distance(it, outfit, bracket, weather) }
                .minByOrNull { it.second }

        // Tier 1 — EXACT: pool first, then bundled.
        nearest(pool.filter { isExact(it, outfit) })?.let {
            return HeroChainResolution(it.first, HeroChainTier.EXACT, it.second)
        }
        nearest(bundled.filter { isExact(it, outfit) })?.let {
            return HeroChainResolution(it.first, HeroChainTier.EXACT, it.second)
        }
        // Tier 2 — SCAFFOLD: nearest covered photo from the live pool.
        nearest(pool)?.let {
            return HeroChainResolution(it.first, HeroChainTier.SCAFFOLD, it.second)
        }
        // Tier 3 — FLOOR: nearest bundled asset; the bundle covers
        // every gender × bracket family, so this is the non-empty
        // terminus Sol's ruling requires.
        nearest(bundled)?.let {
            return HeroChainResolution(it.first, HeroChainTier.FLOOR, it.second)
        }
        return null
    }
}
