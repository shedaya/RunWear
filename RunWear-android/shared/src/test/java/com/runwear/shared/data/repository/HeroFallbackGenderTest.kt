package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageSelector
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.TempBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-7 (2026-11-03) — the RW-26 full fix, superseding the RW-26
 * containment contract this file used to pin.
 *
 * The bundled matrix is now gender-PAIRED (22 cells x MALE/FEMALE,
 * derived from verified approved pool stock — bundle manifest in
 * release-prep/rw26-fallback-bundle-2026-11-03/). The contract:
 *
 * - Every paired cell resolves to ITS OWN gender-paired asset, for
 *   both genders: subject match AND scene match at the requested
 *   cell, by direct lookup — no walk is involved at a paired cell.
 * - The resolver bound (D-19): subject gender NEVER outranks the
 *   scene at a paired cell. The RW-26 construction that deterministically
 *   resolved MALE + COOL + CLEAR to the snow asset (COOL's only male
 *   subject in the old matrix) now resolves to cool_clear_male.
 * - FREEZING x RAIN serves its own paired assets for both genders;
 *   the designed degradation survives ONLY in the UNISEX legacy chain.
 * - UNISEX resolution is the legacy chain, unchanged.
 *
 * The RW-20 census below survives as the UNISEX legacy view's subject
 * table (which paired file each legacy cell's legacy chain lands on).
 */
class HeroFallbackGenderTest {

    private val repository = HeroImageRepository()

    private fun assetName(url: String): String =
        url.removePrefix("file:///android_asset/hero_fallback/").removeSuffix(".webp")

    /** The RW-20 provenance census: subject gender per legacy (UNISEX-view) asset. */
    private val expectedSubjects: Map<String, GenderPreference> = mapOf(
        "freezing_clear" to GenderPreference.FEMALE,
        "freezing_cloudy" to GenderPreference.FEMALE,
        "freezing_snow" to GenderPreference.MALE,
        "cold_clear" to GenderPreference.FEMALE,
        "cold_cloudy" to GenderPreference.FEMALE,
        "cold_rain" to GenderPreference.FEMALE,
        "cold_snow" to GenderPreference.FEMALE,
        "cool_clear" to GenderPreference.FEMALE,
        "cool_cloudy" to GenderPreference.FEMALE,
        "cool_rain" to GenderPreference.FEMALE,
        "cool_snow" to GenderPreference.MALE,
        "mild_clear" to GenderPreference.FEMALE,
        "mild_cloudy" to GenderPreference.FEMALE,
        "mild_rain" to GenderPreference.FEMALE,
        "mild_snow" to GenderPreference.FEMALE,
        "warm_clear" to GenderPreference.MALE,
        "warm_cloudy" to GenderPreference.MALE,
        "warm_rain" to GenderPreference.FEMALE,
        "hot_clear" to GenderPreference.FEMALE,
        "hot_cloudy" to GenderPreference.MALE,
        "hot_rain" to GenderPreference.FEMALE
    )

    @Test
    fun `legacy census covers exactly the 21 legacy cells and both copies agree`() {
        val table = HeroImageRepository.FALLBACK_IMAGES_2D
        var cells = 0
        for ((bracket, weathers) in table) {
            for ((condition, ref) in weathers) {
                cells++
                val legacyName = assetName(ref).removeSuffix("_female").removeSuffix("_male")
                val expected = expectedSubjects[legacyName]
                assertTrue("no census entry for ${assetName(ref)}", expected != null)
                assertEquals(
                    "engine census subject for $bracket/$condition",
                    expected,
                    HeroImageSelector.fallbackSubjectGender(bracket, condition)
                )
                assertEquals(
                    "repository census subject for $bracket/$condition",
                    expected,
                    repository.fallbackSubjectGender(bracket, condition)
                )
                // The legacy view's file IS the census subject's paired file.
                assertEquals(
                    "legacy view file for $bracket/$condition",
                    "${legacyName}_${expected!!.name.lowercase()}",
                    assetName(ref)
                )
            }
        }
        assertEquals(21, cells)
        assertEquals(21, expectedSubjects.size)
    }

    @Test
    fun `every paired cell resolves to its own gender-paired asset for both genders`() {
        val paired = HeroImageRepository.FALLBACK_IMAGES_PAIRED
        assertEquals(22, paired.size)
        for ((cell, genders) in paired) {
            for (gender in listOf(GenderPreference.MALE, GenderPreference.FEMALE)) {
                val expected = genders.getValue(gender)
                assertEquals(
                    "engine serve for $cell/$gender",
                    expected,
                    HeroImageSelector.getWeatherAwareFallback(cell.first, cell.second, gender)
                )
                assertEquals(
                    "repository serve for $cell/$gender",
                    expected,
                    repository.getWeatherAwareFallback(cell.first, cell.second, gender)
                )
                // Scene match AND subject match, by construction of the name.
                assertEquals(
                    "${cell.first.name.lowercase()}_${cell.second.name.lowercase()}_${gender.name.lowercase()}",
                    assetName(expected)
                )
            }
        }
    }

    @Test
    fun `D-19 construction case - MALE COOL CLEAR resolves to the paired clear asset, never snow`() {
        // The deterministic RW-26 failure: MALE + COOL + CLEAR walked
        // past CLEAR/CLOUDY/RAIN (female subjects) to COOL x SNOW.
        for (url in listOf(
            HeroImageSelector.getWeatherAwareFallback(
                TempBracket.COOL, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            ),
            repository.getWeatherAwareFallback(
                TempBracket.COOL, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            )
        )) {
            assertEquals("cool_clear_male", assetName(url))
            assertNotEquals("cool_snow_male", assetName(url))
            assertNotEquals("cool_snow", assetName(url))
        }
        // And the field instance behind D-18/D-19's sibling case:
        // MALE + MILD + CLEAR (Sol's flagged waterfront-woman serve)
        // now serves the paired male mild-clear asset in-bracket.
        assertEquals(
            "mild_clear_male",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.MALE
                )
            )
        )
        // Mirror: FEMALE + WARM + CLEAR serves warm_clear_female
        // in-bracket (the RW-26 walk served warm_rain instead).
        assertEquals(
            "warm_clear_female",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.WARM, HeroWeatherCondition.CLEAR, GenderPreference.FEMALE
                )
            )
        )
        assertEquals(
            "cold_cloudy_male",
            assetName(
                repository.getWeatherAwareFallback(
                    TempBracket.COLD, HeroWeatherCondition.CLOUDY, GenderPreference.MALE
                )
            )
        )
    }

    @Test
    fun `FREEZING RAIN serves its own paired assets for both genders - degradation retired`() {
        assertEquals(
            "freezing_rain_female",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.FREEZING, HeroWeatherCondition.RAIN, GenderPreference.FEMALE
                )
            )
        )
        assertEquals(
            "freezing_rain_male",
            assetName(
                repository.getWeatherAwareFallback(
                    TempBracket.FREEZING, HeroWeatherCondition.RAIN, GenderPreference.MALE
                )
            )
        )
    }

    @Test
    fun `gender never outranks scene - no gendered resolution crosses weather at a paired cell`() {
        for (bracket in TempBracket.values()) {
            for (condition in HeroWeatherCondition.values()) {
                val isPairedCell = HeroImageRepository.FALLBACK_IMAGES_PAIRED.containsKey(bracket to condition)
                for (gender in listOf(GenderPreference.MALE, GenderPreference.FEMALE)) {
                    val name = assetName(
                        HeroImageSelector.getWeatherAwareFallback(bracket, condition, gender)
                    )
                    // Subject always matches the request.
                    assertTrue(
                        "$bracket/$condition/$gender served a wrong-gender subject: $name",
                        name.endsWith("_${gender.name.lowercase()}")
                    )
                    if (isPairedCell) {
                        // Scene always matches at a paired cell.
                        assertEquals(
                            "$bracket/$condition/$gender crossed the scene",
                            "${bracket.name.lowercase()}_${condition.name.lowercase()}_${gender.name.lowercase()}",
                            name
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `non-cells resolve scene-first inside the bracket with the requested gender`() {
        // HOT x SNOW / WARM x SNOW have no paired asset; the walk takes
        // the bracket's own CLEAR paired asset for the requested gender.
        assertEquals(
            "hot_clear_male",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.HOT, HeroWeatherCondition.SNOW, GenderPreference.MALE
                )
            )
        )
        assertEquals(
            "warm_clear_female",
            assetName(
                repository.getWeatherAwareFallback(
                    TempBracket.WARM, HeroWeatherCondition.SNOW, GenderPreference.FEMALE
                )
            )
        )
    }

    @Test
    fun `UNISEX resolution is the legacy chain, unchanged`() {
        for (bracket in TempBracket.values()) {
            for (condition in HeroWeatherCondition.values()) {
                assertEquals(
                    "UNISEX drift for $bracket/$condition",
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition),
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition, GenderPreference.UNISEX)
                )
                assertEquals(
                    "UNISEX drift (repository) for $bracket/$condition",
                    repository.getWeatherAwareFallback(bracket, condition),
                    repository.getWeatherAwareFallback(bracket, condition, GenderPreference.UNISEX)
                )
                // Engine and repository agree on every UNISEX serve too.
                assertEquals(
                    "UNISEX engine/repository disagree for $bracket/$condition",
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition, GenderPreference.UNISEX),
                    repository.getWeatherAwareFallback(bracket, condition, GenderPreference.UNISEX)
                )
            }
        }
        // Spot-check the legacy landings: same subjects as the RW-20 set.
        assertEquals(
            "mild_clear_female",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.UNISEX
                )
            )
        )
        assertEquals(
            "hot_clear_female",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.HOT, HeroWeatherCondition.SNOW, GenderPreference.UNISEX
                )
            )
        )
        assertEquals(
            "cool_snow_male",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.COOL, HeroWeatherCondition.SNOW, GenderPreference.UNISEX
                )
            )
        )
    }
}
