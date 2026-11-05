package com.runwear.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 A-HERO (WS-A(1)): the point-grain extension of the bundled
 * fallback. The RW-26 matrix's 44 assets are qualified at each
 * bracket's cold-end prescription; the engine prescribes at point
 * grain, so prescription families inside a bracket matched NO
 * bundled asset and the gen-2 gate terminated on the designed
 * gradient (walkthrough X-3: ~50 s empty hero at Overland Park,
 * X-6: ~2 s at New York). The extension bundles additional verified
 * approved pool rows keyed by the prescription's gate signature.
 *
 * These tests pin: (1) the point-grain tables are mutually
 * consistent and every nominated asset passes the same point-grain
 * gate for the signature it was bundled for; (2) the gated
 * resolution actually serves those signatures end to end; (3) the
 * signatures with NO qualifying row in the verified pool — the
 * census's defect families (the mittens vocabulary gap, the
 * warm-point bare-head/hands long-sleeve family, the COLD calm
 * beanie family) — still resolve to NO photo rather than a
 * contradicting one (RW-20 invariant: report the defect, never
 * silently serve around it).
 */
class HeroPointGrainFallbackTest {

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

    /** One outfit per bundled point-grain signature. */
    private val signatureOutfits: List<Triple<GenderPreference, String, OutfitRecommendation>> =
        listOf(
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o1_shorts_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o1_tights_headband_gloves",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER, ClothingItem.HEADBAND,
                    ClothingItem.LIGHT_GLOVES
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_short_o0_shorts_cap_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_short_o0_shorts_visor_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    null, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_short_o1_shorts_visor_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_sleeveless_o1_shorts_visor_none",
                outfit(
                    ClothingItem.TANK_TOP, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o1_shorts_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o1_tights_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o1_tights_headband_gloves",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER, ClothingItem.HEADBAND,
                    ClothingItem.LIGHT_GLOVES
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_short_o0_shorts_cap_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            // GEN-4 REPLENISHMENT (RWPOOL-T07..T26, 2026-11-04 night):
            // the 20 signatures the gen-3 census recorded as
            // unservable, bundled from the replenishment's newly
            // approved pool rows.
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o0_shorts_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o0_shorts_none_none",
                outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS)
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o1_shorts_none_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o0_shorts_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o0_shorts_none_none",
                outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS)
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o1_shorts_none_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
                    ClothingItem.WINDBREAKER
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o0_tights_beanie_gloves",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.THERMAL_TIGHTS,
                    null, ClothingItem.LIGHT_BEANIE, ClothingItem.LIGHT_GLOVES
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o0_tights_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o0_tights_none_none",
                outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS)
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o1_tights_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_long_o1_tights_none_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o0_tights_beanie_gloves",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.THERMAL_TIGHTS,
                    null, ClothingItem.LIGHT_BEANIE, ClothingItem.LIGHT_GLOVES
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o0_tights_cap_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    null, ClothingItem.BASEBALL_CAP
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o0_tights_none_none",
                outfit(ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS)
            ),
            Triple(
                GenderPreference.MALE, "pg_male_long_o1_tights_none_none",
                outfit(
                    ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
                    ClothingItem.WINDBREAKER
                )
            ),
            Triple(
                GenderPreference.FEMALE, "pg_female_sleeveless_o0_shorts_visor_none",
                outfit(
                    ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS,
                    null, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_short_o0_shorts_visor_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    null, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_short_o1_shorts_visor_none",
                outfit(
                    ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                    ClothingItem.RAIN_JACKET, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_sleeveless_o0_shorts_visor_none",
                outfit(
                    ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS,
                    null, ClothingItem.VISOR
                )
            ),
            Triple(
                GenderPreference.MALE, "pg_male_sleeveless_o1_shorts_visor_none",
                outfit(
                    ClothingItem.TANK_TOP, ClothingItem.SHORT_SHORTS,
                    ClothingItem.RAIN_JACKET, ClothingItem.VISOR
                )
            )
        )

    @Test
    fun `the point-grain tables are consistent - garments, provenance, nomination`() {
        val garments = HeroImageSelector.pointGrainGarmentsTable()
        val provenance = HeroImageSelector.pointGrainProvenanceTable()
        val nominations = HeroImageSelector.pointGrainFallbackTable()
        assertEquals(30, garments.size)
        assertEquals(garments.keys, provenance.keys)
        assertEquals(garments.keys, nominations.values.toSet())
        // Every PG asset carries the PG rung and a pool row id.
        provenance.values.forEach { prov ->
            assertEquals("PG", prov.rung)
            assertTrue(prov.sourceRowId.isNotBlank())
        }
    }

    @Test
    fun `every bundled point-grain asset passes the gate for its own signature`() {
        for ((gender, asset, prescription) in signatureOutfits) {
            val url = "file:///android_asset/hero_fallback/$asset.webp"
            assertEquals(
                "nomination for $asset",
                url, HeroImageSelector.pointGrainFallbackUrl(gender, prescription)
            )
            assertTrue(
                "gate for $asset",
                HeroImageSelector.fallbackMatchesPrescription(url, prescription)
            )
            assertNotNull(
                "provenance for $asset",
                HeroImageSelector.fallbackProvenance(url)
            )
        }
    }

    @Test
    fun `the gated resolution serves point-grain signatures the 44-asset matrix cannot`() {
        // Sun-gate cap at MILD: the matrix's clear assets are bare-headed.
        val capMild = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            null, ClothingItem.BASEBALL_CAP
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_short_o0_shorts_cap_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.MILD, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, capMild
            )
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_male_short_o0_shorts_cap_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.MILD, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, capMild
            )
        )
        // Visor at WARM (female): the matrix has no visor asset at all.
        val visorWarm = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            null, ClothingItem.VISOR
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_short_o0_shorts_visor_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.WARM, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, visorWarm
            )
        )
        // COOL rain with headband + gloves: the matrix's rain assets
        // wear caps; its headband assets wear no shell.
        val coolRainHeadband = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            ClothingItem.RAIN_JACKET, ClothingItem.HEADBAND,
            ClothingItem.LIGHT_GLOVES
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_long_o1_tights_headband_gloves.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.RAIN,
                GenderPreference.FEMALE, coolRainHeadband
            )
        )
        // Shell + shorts + cap at HOT rain: the matrix's hot-rain
        // assets are sleeveless-based; the long-sleeve point is PG's.
        val hotRainLong = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
            ClothingItem.RAIN_JACKET, ClothingItem.BASEBALL_CAP
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_long_o1_shorts_cap_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.HOT, HeroWeatherCondition.RAIN,
                GenderPreference.FEMALE, hotRainLong
            )
        )
    }

    @Test
    fun `getFallbackImage serves a point-grain photo end to end`() {
        // 44°F COOL CLEAR frame, sun-gate cap prescription (the
        // Overland Park family shape): pre-fix this terminated on
        // the gradient; the point-grain bundle serves a photo.
        val result = HeroImageSelector.getFallbackImage(
            weatherFixture(),
            outfit(
                ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
                null, ClothingItem.BASEBALL_CAP
            ),
            GenderPreference.FEMALE, frameFeelsLikeF = 44.0
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_short_o0_shorts_cap_none.webp",
            result.imageUrl
        )
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, result.serveSource)
    }

    @Test
    fun `defect family - mittens prescriptions have no servable bundled photo`() {
        // Census evidence: the verified pool's adjudicated hands
        // vocabulary is {gloves, none} — zero rows carry mittens, so
        // every sub-15°F MITTENS prescription is unservable by
        // construction. The gate must say so (null), never serve a
        // gloves photo against a mittens prescription.
        val mittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.WINDBREAKER, ClothingItem.THERMAL_BEANIE,
            ClothingItem.MITTENS
        )
        for (gender in GenderPreference.entries) {
            assertNull(
                HeroImageSelector.gatedFallbackUrl(
                    TempBracket.FREEZING, HeroWeatherCondition.CLEAR,
                    gender, mittens
                )
            )
        }
        // Sub-5°F: balaclava + mittens — the pool's balaclava rows
        // all wear gloves, so this is unservable for the same reason.
        val balaclavaMittens = outfit(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.INSULATED_JACKET, ClothingItem.BALACLAVA,
            ClothingItem.MITTENS
        )
        assertNull(
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.FREEZING, HeroWeatherCondition.SNOW,
                GenderPreference.FEMALE, balaclavaMittens
            )
        )
    }

    @Test
    fun `replenished family - the warm-point long-sleeve bare head and hands serve resolves via point grain`() {
        // The Overland Park prescription family WAS a defect family
        // in gen-3 (long sleeve + shell + shorts, bare head/hands:
        // zero qualifying pool rows). The GEN-4 replenishment closed
        // it — RWPOOL-T09 (FEMALE) / T12 (MALE) are bundled from the
        // replenishment's newly approved rows, so the gated
        // resolution now serves them EXACTLY instead of null.
        val opFamily = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.SHORTS,
            ClothingItem.WINDBREAKER, null, null
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_long_o1_shorts_none_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, opFamily
            )
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_male_long_o1_shorts_none_none.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, opFamily
            )
        )
    }

    @Test
    fun `replenished family - COLD calm beanie without a shell resolves via point grain`() {
        // 20–24°F calm WAS a defect family in gen-3 (beanie + gloves
        // + tights with NO shell: every pool row wore a shell). The
        // GEN-4 replenishment closed it — RWPOOL-T13 (FEMALE) / T18
        // (MALE) are bundled from newly approved rows.
        val coldCalm = outfit(
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LIGHT_TIGHTS,
            null, ClothingItem.LIGHT_BEANIE, ClothingItem.LIGHT_GLOVES
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_female_long_o0_tights_beanie_gloves.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COLD, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, coldCalm
            )
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_male_long_o0_tights_beanie_gloves.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COLD, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, coldCalm
            )
        )
    }

    @Test
    fun `male visor assets are bundled after the replenishment - the nomination exists`() {
        // Gen-3 census evidence was that all visor rows in the pool
        // were FEMALE, so no male visor nomination existed. The
        // GEN-4 replenishment generated and approved male visor rows
        // (RWPOOL-T23..T26) and bundled them — the male nomination
        // now exists and serves. UNISEX still names no gender, so
        // the direct nomination stays null by design.
        val visor = outfit(
            ClothingItem.SHORT_SLEEVE, ClothingItem.SHORTS,
            null, ClothingItem.VISOR
        )
        assertEquals(
            "file:///android_asset/hero_fallback/pg_male_short_o0_shorts_visor_none.webp",
            HeroImageSelector.pointGrainFallbackUrl(GenderPreference.MALE, visor)
        )
        assertNull(HeroImageSelector.pointGrainFallbackUrl(GenderPreference.UNISEX, visor))
    }
}
