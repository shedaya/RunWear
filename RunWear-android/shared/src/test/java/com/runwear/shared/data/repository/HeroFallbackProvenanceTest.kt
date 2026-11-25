package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-7 / D-22 — point-grain standing for bundled serves.
 *
 * Pool serves obey the point-grain prescription gate; before WS-7 a
 * bundled fallback serve was exempt by construction (the photo could
 * wear gloves the app did not prescribe, with no record of why that
 * photo was entitled to serve the cell). The bundle's answer is the
 * precomputed verdict: every bundled asset carries its manifest
 * provenance — the pool source combination it was rendered from, that
 * row's id, and its rung (T1 strict rung 1 / T3 recalibrated rung 1,
 * the verdict basis of Sol's Nov 2 gate rulings 2 + 5) — recorded in
 * the bundle wiring on BOTH table copies, so a bundled serve is
 * auditable exactly like a pool serve.
 */
class HeroFallbackProvenanceTest {

    @Test
    fun `every paired asset carries provenance in both copies, and the copies agree`() {
        val engineTable = HeroImageSelector.fallbackProvenanceTable()
        val repoTable = HeroImageRepository.FALLBACK_PROVENANCE
        assertEquals(44, engineTable.size)
        assertEquals(engineTable, repoTable)
        val paired = HeroImageRepository.FALLBACK_IMAGES_PAIRED
        for ((_, genders) in paired) {
            for ((_, ref) in genders) {
                val file = HeroImageSelector.fallbackAssetFile(ref)
                val prov = engineTable[file]
                assertTrue("no provenance for $file", prov != null)
                assertEquals(prov, HeroImageSelector.fallbackProvenance(ref))
                assertTrue("$file rung must be a manifest rung", prov!!.rung in setOf("T1", "T3"))
                assertTrue(
                    "$file source row id must be a pool row id",
                    prov.sourceRowId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                )
            }
        }
    }

    @Test
    fun `provenance combination gender and cell match the asset slot`() {
        for ((file, prov) in HeroImageSelector.fallbackProvenanceTable()) {
            val gender = if (file.endsWith("_female")) "FEMALE_" else "MALE_"
            assertTrue(
                "$file provenance combination ${prov.combinationId} must start with $gender",
                prov.combinationId.startsWith(gender)
            )
        }
    }

    @Test
    fun `manifest pins - the D-19 cell and the restored FREEZING RAIN cell`() {
        val table = HeroImageSelector.fallbackProvenanceTable()
        // MALE + COOL + CLEAR: the asset D-19's construction case now serves.
        assertEquals(
            "MALE_CLEAR_COOL_MIDDAY_84c10525",
            table.getValue("cool_clear_male").combinationId
        )
        assertEquals(
            "f4b95431-0943-435b-95c7-304b95762e51",
            table.getValue("cool_clear_male").sourceRowId
        )
        assertEquals("T1", table.getValue("cool_clear_male").rung)
        // FREEZING x RAIN, restored cell.
        assertEquals(
            "0b891b30-75ed-4337-939c-6219d9f1309a",
            table.getValue("freezing_rain_female").sourceRowId
        )
        assertEquals("T3", table.getValue("freezing_rain_female").rung)
        assertEquals(
            "ad138c05-da7e-4cc3-ac63-f73bcf3bc21b",
            table.getValue("freezing_rain_male").sourceRowId
        )
        assertEquals("T1", table.getValue("freezing_rain_male").rung)
        // The manifest's two alternates were taken at their PRIMARY
        // picks: FREEZING CLEAR F is the T1 NIGHT row (not the T3
        // MIDDAY alternate), MILD RAIN M is the T1 NIGHT row.
        assertEquals(
            "FEMALE_CLEAR_FREEZING_NIGHT_v4",
            table.getValue("freezing_clear_female").combinationId
        )
        assertEquals(
            "MALE_RAINY_MILD_NIGHT_v3",
            table.getValue("mild_rain_male").combinationId
        )
    }

    @Test
    fun `the 5 re-adjudication-rejected RW-20 sources are not in the bundle wiring`() {
        // D-21: these source combinations were retired by the legacy
        // re-adjudication (derivation record §4); none may back a
        // bundled asset anymore.
        val combinations = HeroImageSelector.fallbackProvenanceTable().values.map { it.combinationId }.toSet()
        for (retired in listOf(
            "FEMALE_CLEAR_COOL_DAWN_v15",
            "FEMALE_CLOUDY_COOL_DAWN_v12",
            "FEMALE_RAIN_COLD_DAWN_v12",
            "FEMALE_RAIN_MILD_DUSK_v4",
            "FEMALE_CLEAR_HOT_DAWN_v1"
        )) {
            assertFalse("$retired must not back any bundled asset", combinations.contains(retired))
        }
        // Their slots re-derived at T1 from approved stock.
        val table = HeroImageSelector.fallbackProvenanceTable()
        assertEquals("T1", table.getValue("cool_clear_female").rung)
        assertEquals("T1", table.getValue("cool_cloudy_female").rung)
        assertEquals("T1", table.getValue("mild_rain_female").rung)
        assertEquals("T1", table.getValue("hot_clear_female").rung)
    }

    @Test
    fun `a bundled serve result carries its provenance - selector fallback path`() {
        // HeroImageSelector.getFallbackImage is the synchronous serve
        // (resolve-timeout substitution + last-good seed): its result
        // must carry source + provenance + asset file for the record.
        val result = HeroImageSelector.getFallbackImage(
            TelemetryLikeFixtures.weather, TelemetryLikeFixtures.outfit,
            GenderPreference.MALE, frameFeelsLikeF = 42.0
        )
        assertEquals(
            com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK,
            result.serveSource
        )
        assertEquals("cool_clear_male", result.fallbackAssetFile)
        assertEquals(
            "f4b95431-0943-435b-95c7-304b95762e51",
            result.fallbackProvenance?.sourceRowId
        )
    }
}

/** Minimal weather/outfit fixtures local to this test (42F, mainly clear -> COOL x CLEAR). */
private object TelemetryLikeFixtures {
    val weather = com.runwear.shared.domain.model.WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 3, 7, 0),
        temperature = 45.0,
        feelsLike = 42.0,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = com.runwear.shared.domain.model.WeatherCode.MAINLY_CLEAR
    )
    val outfit = com.runwear.shared.domain.model.OutfitRecommendation(
        weather = weather,
        topBase = com.runwear.shared.domain.model.ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = com.runwear.shared.domain.model.ClothingItem.LIGHT_TIGHTS,
        head = com.runwear.shared.domain.model.ClothingItem.HEADBAND,
        hands = com.runwear.shared.domain.model.ClothingItem.LIGHT_GLOVES
    )
}
