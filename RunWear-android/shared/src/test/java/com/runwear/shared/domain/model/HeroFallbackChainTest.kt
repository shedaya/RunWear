package com.runwear.shared.domain.model

import com.runwear.shared.data.repository.GeneratedImage
import com.runwear.shared.data.repository.GarmentClasses
import com.runwear.shared.data.repository.poolPhotoCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-4 HERO FALLBACK (Sol's ruling, 2026-11-04): the chain that
 * replaced the bare-gradient terminus for the servability census's
 * 26 uncovered signatures. Pins:
 *  - chain order: EXACT (pool, then bundled) > SCAFFOLD (pool) >
 *    FLOOR (bundled);
 *  - the metric: bracket distance dominates garment detail, bottom
 *    coverage leads the garment components, hands coverage orders
 *    the scaffold metric (the gate's own handwear substitution —
 *    gloves exact for a mittens prescription, Sol's ruling
 *    2026-11-04 — is pinned separately below);
 *  - one case per census defect family;
 *  - gender is never crossed at any tier;
 *  - floor completeness: every one of the census's 50 gendered gate
 *    signatures resolves non-null with the pool empty;
 *  - the scaffold label (isScaffold / serveSource) is present iff
 *    the tier is not EXACT;
 *  - convergence: resolution is a function of the live candidate
 *    set — a true-mittens row outranks the substituted gloves serve
 *    for the same prescription with no code change.
 */
class HeroFallbackChainTest {

    private fun weatherFixture(): WeatherConditions = WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 4, 8, 0),
        temperature = 45.0,
        feelsLike = 44.0,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.MAINLY_CLEAR
    )

    private fun outfit(
        topBase: ClothingItem,
        bottom: ClothingItem,
        topOuter: ClothingItem? = null,
        head: ClothingItem? = null,
        hands: ClothingItem? = null
    ): OutfitRecommendation = OutfitRecommendation(
        weather = weatherFixture(),
        topBase = topBase,
        topOuter = topOuter,
        bottom = bottom,
        head = head,
        hands = hands
    )

    private fun candidate(
        url: String,
        gender: GenderPreference = GenderPreference.FEMALE,
        bracket: TempBracket? = TempBracket.COOL,
        weather: HeroWeatherCondition? = HeroWeatherCondition.CLEAR,
        topBase: String? = "long",
        outer: Boolean? = false,
        bottom: String? = "tights",
        head: String? = null,
        hands: String? = null,
        source: HeroCandidateSource = HeroCandidateSource.POOL,
        personCount: Int? = 1
    ): HeroPhotoCandidate = HeroPhotoCandidate(
        url = url,
        source = source,
        gender = gender,
        bracket = bracket,
        weather = weather,
        garments = HeroGarmentSnapshot(topBase, outer, bottom, head, hands),
        personCount = personCount,
        rowId = if (source == HeroCandidateSource.POOL) "row-$url" else null,
        assetFile = if (source == HeroCandidateSource.BUNDLED) url else null
    )

    private fun resolve(
        outfit: OutfitRecommendation,
        gender: GenderPreference = GenderPreference.FEMALE,
        bracket: TempBracket = TempBracket.COOL,
        pool: List<HeroPhotoCandidate> = emptyList(),
        bundled: List<HeroPhotoCandidate> = emptyList()
    ): HeroChainResolution? = HeroFallbackChain.resolve(
        gender, bracket, HeroWeatherCondition.CLEAR, outfit, pool, bundled
    )

    // ── Chain order ────────────────────────────────────────────────

    @Test
    fun `exact pool beats scaffold and floor`() {
        val prescription = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.HEADBAND, hands = ClothingItem.LIGHT_GLOVES
        )
        val exact = candidate("pool-exact", head = "headband", hands = "gloves")
        val near = candidate("pool-near", head = null, hands = null)
        val bundledExact = candidate(
            "bundled-exact", head = "headband", hands = "gloves",
            source = HeroCandidateSource.BUNDLED
        )
        val res = resolve(prescription, pool = listOf(near, exact), bundled = listOf(bundledExact))
        assertEquals(HeroChainTier.EXACT, res?.tier)
        assertEquals("pool-exact", res?.url)
        assertFalse(res!!.isScaffold)
        assertEquals(HeroServeSource.POOL, res.serveSource)
    }

    @Test
    fun `exact bundled beats a pool scaffold`() {
        val prescription = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.HEADBAND, hands = ClothingItem.LIGHT_GLOVES
        )
        val near = candidate("pool-near", head = null, hands = null)
        val bundledExact = candidate(
            "bundled-exact", head = "headband", hands = "gloves",
            source = HeroCandidateSource.BUNDLED
        )
        val res = resolve(prescription, pool = listOf(near), bundled = listOf(bundledExact))
        assertEquals(HeroChainTier.EXACT, res?.tier)
        assertEquals("bundled-exact", res?.url)
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, res?.serveSource)
    }

    @Test
    fun `scaffold from the pool beats the bundled floor`() {
        val prescription = outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS)
        val near = candidate("pool-near", head = "headband", hands = "gloves")
        val bundledNear = candidate(
            "bundled-near", head = "headband", hands = "gloves",
            source = HeroCandidateSource.BUNDLED
        )
        val res = resolve(prescription, pool = listOf(near), bundled = listOf(bundledNear))
        assertEquals(HeroChainTier.SCAFFOLD, res?.tier)
        assertEquals("pool-near", res?.url)
        assertTrue(res!!.isScaffold)
        assertEquals(HeroServeSource.SCAFFOLD, res.serveSource)
    }

    @Test
    fun `floor serves the nearest bundled asset when the pool is empty`() {
        val prescription = outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS)
        val bundledNear = candidate(
            "bundled-near", head = "headband", hands = "gloves",
            source = HeroCandidateSource.BUNDLED
        )
        val res = resolve(prescription, pool = emptyList(), bundled = listOf(bundledNear))
        assertEquals(HeroChainTier.FLOOR, res?.tier)
        assertEquals("bundled-near", res?.url)
        assertTrue(res!!.isScaffold)
        assertEquals(HeroServeSource.SCAFFOLD, res.serveSource)
    }

    // ── Metric ─────────────────────────────────────────────────────

    @Test
    fun `bracket distance dominates garment detail`() {
        // Same-bracket photo differing ONLY in headwear must beat a
        // different-bracket photo whose headwear is exact (its other
        // slot differs so it is not itself an exact serve).
        val prescription = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            head = ClothingItem.BASEBALL_CAP
        )
        val sameBracketDiffHead = candidate(
            "same-bracket", bracket = TempBracket.MILD,
            topBase = "short", bottom = "shorts", head = null
        )
        val diffBracketExactHead = candidate(
            "diff-bracket", bracket = TempBracket.WARM,
            topBase = "short", bottom = "shorts", head = "cap", hands = "gloves"
        )
        val res = resolve(
            prescription, bracket = TempBracket.MILD,
            pool = listOf(diffBracketExactHead, sameBracketDiffHead)
        )
        assertEquals(HeroChainTier.SCAFFOLD, res?.tier)
        assertEquals("same-bracket", res?.url)
    }

    @Test
    fun `defect family 1 - mittens prescription serves the gloves photo EXACT under the handwear substitution`() {
        // HANDWEAR SUBSTITUTION (Sol's ruling, 2026-11-04 ~22:23 CST,
        // extended to stock): gloves satisfy a mittens prescription,
        // so the same-gender/bracket gloves photo is an EXACT serve
        // for a sub-15F mittens prescription — this case previously
        // pinned SCAFFOLD and is re-pinned EXACT under the ruling.
        // Bare hands still do not pass the gate (hands prescribed,
        // none worn), so the gloves photo is the resolution.
        val mittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.WINDBREAKER, ClothingItem.THERMAL_BEANIE,
            ClothingItem.MITTENS
        )
        val gloves = candidate(
            "gloves-photo", bracket = TempBracket.FREEZING,
            outer = true, head = "beanie", hands = "gloves"
        )
        val bareHands = candidate(
            "bare-photo", bracket = TempBracket.FREEZING,
            outer = true, head = "beanie", hands = null
        )
        val res = resolve(
            mittens, bracket = TempBracket.FREEZING,
            pool = listOf(bareHands, gloves)
        )
        assertEquals(HeroChainTier.EXACT, res?.tier)
        assertEquals("gloves-photo", res?.url)
        assertFalse(res!!.isScaffold)
    }

    @Test
    fun `defect family 2 - shell shorts bare head and hands scaffolds to the cap shell photo`() {
        // The Overland Park shape: long sleeve + shell + shorts, bare
        // head/hands — zero exact rows. The shell+shorts+cap photo is
        // nearer than the tights cold-end kit.
        val opShape = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
            ClothingItem.WINDBREAKER, null, null
        )
        val shellShortsCap = candidate(
            "shell-shorts-cap", outer = true, bottom = "shorts", head = "cap"
        )
        val tightsKit = candidate(
            "tights-kit", outer = false, bottom = "tights",
            head = "headband", hands = "gloves"
        )
        val res = resolve(opShape, pool = listOf(tightsKit, shellShortsCap))
        assertEquals(HeroChainTier.SCAFFOLD, res?.tier)
        assertEquals("shell-shorts-cap", res?.url)
    }

    @Test
    fun `defect family 3 - warm-point tights scaffolds within the tights family`() {
        // COOL tights + cap + bare hands has no exact row; the
        // same-bracket tights photo (cold-end kit) beats any shorts
        // photo: bottom coverage and bracket both dominate.
        val warmPoint = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            null, ClothingItem.BASEBALL_CAP, null
        )
        val tightsKit = candidate(
            "tights-kit", outer = false, bottom = "tights",
            head = "headband", hands = "gloves"
        )
        val shortsBare = candidate(
            "shorts-bare", bracket = TempBracket.MILD,
            topBase = "short", bottom = "shorts"
        )
        val res = resolve(warmPoint, pool = listOf(shortsBare, tightsKit))
        assertEquals(HeroChainTier.SCAFFOLD, res?.tier)
        assertEquals("tights-kit", res?.url)
    }

    @Test
    fun `defect family 4 - male visor scaffolds to the nearest male photo, never a female visor`() {
        // All 40 pool visor rows are FEMALE. A male visor prescription
        // scaffolds to the nearest MALE photo; the female visor photo
        // is not a candidate at any distance.
        val visor = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            head = ClothingItem.VISOR
        )
        val femaleVisor = candidate(
            "female-visor", gender = GenderPreference.FEMALE,
            bracket = TempBracket.MILD, topBase = "short",
            bottom = "shorts", head = "visor"
        )
        val maleBare = candidate(
            "male-bare", gender = GenderPreference.MALE,
            bracket = TempBracket.MILD, topBase = "short",
            bottom = "shorts", head = null
        )
        val res = resolve(
            visor, gender = GenderPreference.MALE, bracket = TempBracket.MILD,
            pool = listOf(femaleVisor, maleBare)
        )
        assertEquals(HeroChainTier.SCAFFOLD, res?.tier)
        assertEquals("male-bare", res?.url)
        // And with ONLY the female photo in the pool, a male request
        // resolves to nothing from the pool — gender is not crossed
        // to fill the hero; the bundled floor (same gender) is the
        // terminus, covered by the floor-completeness test below.
        assertNull(
            resolve(
                visor, gender = GenderPreference.MALE, bracket = TempBracket.MILD,
                pool = listOf(femaleVisor)
            )
        )
    }

    @Test
    fun `multi-person photos never serve at any tier`() {
        val prescription = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.HEADBAND, hands = ClothingItem.LIGHT_GLOVES
        )
        val twoPeople = candidate(
            "two-people", head = "headband", hands = "gloves", personCount = 2
        )
        val bundled = candidate(
            "bundled-one", head = "beanie", hands = "gloves",
            source = HeroCandidateSource.BUNDLED
        )
        val res = resolve(prescription, pool = listOf(twoPeople), bundled = listOf(bundled))
        assertEquals("bundled-one", res?.url)
        assertTrue(res?.tier != HeroChainTier.EXACT)
    }

    @Test
    fun `convergence - a true mittens row outranks the substituted gloves serve with no code change`() {
        val mittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.WINDBREAKER, ClothingItem.THERMAL_BEANIE,
            ClothingItem.MITTENS
        )
        val gloves = candidate(
            "gloves-photo", bracket = TempBracket.FREEZING,
            outer = true, head = "beanie", hands = "gloves"
        )
        // Under the handwear substitution (Sol's ruling, 2026-11-04)
        // the gloves photo serves EXACT for the mittens prescription
        // — the pre-ruling SCAFFOLD state of this case is superseded.
        val before = resolve(mittens, bracket = TempBracket.FREEZING, pool = listOf(gloves))
        assertEquals(HeroChainTier.EXACT, before?.tier)
        assertEquals("gloves-photo", before?.url)
        // When a true mittens row lands in the pool, the SAME
        // resolution prefers it (hands distance 0 beats the
        // substituted gloves' 1) — resolution is a function of the
        // live candidate set, not a per-signature table.
        val mittensRow = candidate(
            "mittens-photo", bracket = TempBracket.FREEZING,
            outer = true, head = "beanie", hands = "mittens"
        )
        val after = resolve(
            mittens, bracket = TempBracket.FREEZING,
            pool = listOf(gloves, mittensRow)
        )
        assertEquals(HeroChainTier.EXACT, after?.tier)
        assertEquals("mittens-photo", after?.url)
        assertFalse(after!!.isScaffold)
    }

    // ── Pool-row conversion (the repository's live read) ───────────

    @Test
    fun `pool rows convert to same-gender candidates and rejected rows never convert`() {
        val rows = listOf(
            GeneratedImage(
                id = "gloves-row",
                combinationId = "FEMALE_CLEAR_FREEZING_MIDDAY_x",
                imageUrl = "https://pool/gloves.webp",
                reviewStatus = "approved",
                personCount = 1,
                garmentClasses = GarmentClasses(
                    topBase = "long", outer = true, bottom = "tights",
                    head = "beanie", hands = "gloves"
                )
            ),
            GeneratedImage(
                id = "rejected-row",
                combinationId = "FEMALE_CLEAR_FREEZING_MIDDAY_y",
                imageUrl = "https://pool/rejected.webp",
                reviewStatus = "rejected",
                personCount = 1,
                garmentClasses = GarmentClasses(
                    topBase = "long", outer = true, bottom = "tights",
                    head = "beanie", hands = "mittens"
                )
            )
        )
        val candidates = poolPhotoCandidates(rows)
        assertEquals(1, candidates.size)
        assertEquals(GenderPreference.FEMALE, candidates[0].gender)
        assertEquals(TempBracket.FREEZING, candidates[0].bracket)
        assertEquals(HeroWeatherCondition.CLEAR, candidates[0].weather)
        // The mittens prescription serves the approved gloves row
        // EXACT under the handwear substitution (Sol's ruling,
        // 2026-11-04 — re-pinned from SCAFFOLD); the rejected
        // mittens row cannot serve at any tier.
        val mittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.WINDBREAKER, ClothingItem.THERMAL_BEANIE,
            ClothingItem.MITTENS
        )
        val res = HeroFallbackChain.resolve(
            GenderPreference.FEMALE, TempBracket.FREEZING,
            HeroWeatherCondition.CLEAR, mittens, candidates, emptyList()
        )
        assertEquals(HeroChainTier.EXACT, res?.tier)
        assertEquals("https://pool/gloves.webp", res?.url)
        assertEquals("gloves-row", res?.candidate?.rowId)
    }

    // ── Selector-level floor (bundled chain end to end) ────────────

    @Test
    fun `mittens prescription exact-serves the same-gender gloves bundled asset under the handwear substitution`() {
        // HANDWEAR SUBSTITUTION (Sol's ruling, 2026-11-04 ~22:23
        // CST): the bundled freezing_clear_* assets wear beanie +
        // gloves, and gloves now satisfy the (…, beanie, mittens)
        // signature at the exact gate — so the bundled chain serves
        // freezing_clear_female EXACT (BUNDLED_FALLBACK, not
        // scaffold). This case previously pinned FLOOR/scaffold and
        // is re-pinned under the ruling. (GEN-5: the balaclava
        // mittens signatures now exact-serve the top-up balaclava+
        // gloves bundled assets, RWPOOL-T27/T28.)
        val mittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.INSULATED_JACKET, ClothingItem.THERMAL_BEANIE,
            ClothingItem.MITTENS
        )
        val result = HeroImageSelector.getFallbackImage(
            weatherFixture(), mittens,
            GenderPreference.FEMALE, frameFeelsLikeF = 10.0
        )
        assertEquals(
            "file:///android_asset/hero_fallback/freezing_clear_female.webp",
            result.imageUrl
        )
        assertFalse(result.isScaffold)
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, result.serveSource)
        assertEquals("EXACT", result.chainTier)
    }

    @Test
    fun `male visor prescription serves the male visor bundled asset exactly after the replenishment`() {
        val visor = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            head = ClothingItem.VISOR
        )
        val result = HeroImageSelector.getFallbackImage(
            weatherFixture(), visor,
            GenderPreference.MALE, frameFeelsLikeF = 58.0
        )
        // GEN-4 replenishment (RWPOOL-T23): a truthful male visor
        // photo now EXISTS in the bundle, so the chain serves it
        // EXACT — superseding the gen-4 pin above it in history,
        // where the nearest male scaffold (the male cap photo)
        // served labelled scaffold because no male visor photo
        // existed. Gender is still never crossed.
        assertEquals(
            "file:///android_asset/hero_fallback/pg_male_short_o0_shorts_visor_none.webp",
            result.imageUrl
        )
        assertFalse(result.isScaffold)
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, result.serveSource)
        assertEquals("EXACT", result.chainTier)
    }

    /**
     * The census's 25 distinct gate signatures (×2 genders = the 50
     * gendered signatures): (topBase, outer, bottomFamily, head,
     * hands) with a representative bracket each. EVERY one must
     * resolve non-null with the pool empty — the floor-completeness
     * guarantee of Sol's ruling.
     */
    private val censusSignatures: List<Triple<String, TempBracket, OutfitRecommendation>> = listOf(
        Triple("long|true|tights|headband|gloves", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, ClothingItem.WINDBREAKER, ClothingItem.HEADBAND, ClothingItem.LIGHT_GLOVES)),
        Triple("long|true|tights|beanie|gloves", TempBracket.COLD, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.THERMAL_TIGHTS, ClothingItem.WINDBREAKER, ClothingItem.LIGHT_BEANIE, ClothingItem.LIGHT_GLOVES)),
        Triple("long|false|tights|headband|gloves", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, null, ClothingItem.HEADBAND, ClothingItem.LIGHT_GLOVES)),
        Triple("long|false|tights|beanie|gloves", TempBracket.COLD, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, null, ClothingItem.LIGHT_BEANIE, ClothingItem.LIGHT_GLOVES)),
        Triple("long|true|tights|cap|gloves", TempBracket.COLD, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, ClothingItem.RAIN_JACKET, ClothingItem.BASEBALL_CAP, ClothingItem.LIGHT_GLOVES)),
        Triple("long|true|tights|cap|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP, null)),
        Triple("long|true|tights|none|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, ClothingItem.WINDBREAKER, null, null)),
        Triple("long|true|shorts|cap|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS, ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP, null)),
        Triple("long|true|shorts|none|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS, ClothingItem.WINDBREAKER, null, null)),
        Triple("long|false|tights|cap|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, null, ClothingItem.BASEBALL_CAP, null)),
        Triple("long|false|tights|none|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS, null, null, null)),
        Triple("long|false|shorts|cap|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS, null, ClothingItem.BASEBALL_CAP, null)),
        Triple("long|false|shorts|none|none", TempBracket.COOL, outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS, null, null, null)),
        Triple("long|true|tights|balaclava|mittens", TempBracket.FREEZING, outfit(ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS, ClothingItem.INSULATED_JACKET, ClothingItem.BALACLAVA, ClothingItem.MITTENS)),
        Triple("long|true|tights|beanie|mittens", TempBracket.FREEZING, outfit(ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS, ClothingItem.INSULATED_JACKET, ClothingItem.THERMAL_BEANIE, ClothingItem.MITTENS)),
        Triple("long|true|tights|cap|mittens", TempBracket.FREEZING, outfit(ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS, ClothingItem.RAIN_JACKET, ClothingItem.BASEBALL_CAP, ClothingItem.MITTENS)),
        Triple("sleeveless|false|shorts|visor|none", TempBracket.HOT, outfit(ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS, null, ClothingItem.VISOR, null)),
        Triple("sleeveless|false|shorts|none|none", TempBracket.HOT, outfit(ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS, null, null, null)),
        Triple("sleeveless|true|shorts|cap|none", TempBracket.HOT, outfit(ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS, ClothingItem.RAIN_JACKET, ClothingItem.BASEBALL_CAP, null)),
        Triple("sleeveless|true|shorts|visor|none", TempBracket.HOT, outfit(ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS, ClothingItem.RAIN_JACKET, ClothingItem.VISOR, null)),
        Triple("short|false|shorts|cap|none", TempBracket.MILD, outfit(ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS, null, ClothingItem.BASEBALL_CAP, null)),
        Triple("short|false|shorts|visor|none", TempBracket.MILD, outfit(ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS, null, ClothingItem.VISOR, null)),
        Triple("short|false|shorts|none|none", TempBracket.MILD, outfit(ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS, null, null, null)),
        Triple("short|true|shorts|cap|none", TempBracket.MILD, outfit(ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS, ClothingItem.RAIN_JACKET, ClothingItem.BASEBALL_CAP, null)),
        Triple("short|true|shorts|visor|none", TempBracket.MILD, outfit(ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS, ClothingItem.RAIN_JACKET, ClothingItem.VISOR, null))
    )

    @Test
    fun `floor completeness - every census signature resolves non-null for both genders with the pool empty`() {
        assertEquals(25, censusSignatures.size)
        var checked = 0
        for ((signature, bracket, prescription) in censusSignatures) {
            // The fixture outfit must actually carry the signature
            // this row claims — the census's own factoring.
            assertEquals(
                signature, HeroImageSelector.pointGrainSignature(prescription)
            )
            for (gender in listOf(GenderPreference.FEMALE, GenderPreference.MALE)) {
                val result = HeroImageSelector.bundledChainResult(
                    bracket, HeroWeatherCondition.CLEAR, gender,
                    prescription, "test-combination"
                )
                assertNotNull(
                    "no photo for $gender $signature",
                    result.imageUrl
                )
                // A bundled serve never crosses gender.
                val file = result.fallbackAssetFile ?: ""
                assertTrue(
                    "gender crossed for $gender $signature: $file",
                    file.endsWith("_${gender.name.lowercase()}") ||
                        file.contains("_${gender.name.lowercase()}_")
                )
                // The label is present iff the tier is not exact.
                assertEquals(
                    "labelling for $gender $signature",
                    result.chainTier != HeroChainTier.EXACT.name,
                    result.isScaffold
                )
                checked++
            }
        }
        assertEquals(50, checked)
    }

    @Test
    fun `bundled candidate census covers both genders for every bracket family`() {
        val candidates = HeroImageSelector.bundledPhotoCandidates()
        assertEquals(76, candidates.size)  // 54 -> 74: +20 GEN-4 replenishment pg_* assets (RWPOOL-T07..T26); 74 -> 76 GEN-5 top-up balaclava+gloves (RWPOOL-T27/T28)
        for (gender in listOf(GenderPreference.FEMALE, GenderPreference.MALE)) {
            val brackets = candidates.filter { it.gender == gender }.map { it.bracket }.toSet()
            assertEquals(
                "bundled floor must cover every bracket for $gender",
                TempBracket.entries.toSet(), brackets
            )
        }
    }
}
