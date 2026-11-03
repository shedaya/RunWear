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
 * RW-26 (2026-11-02): the bundled fallback matrix is keyed bracket ×
 * weather only, but its assets are photographs of specific people —
 * 15 of 21 subjects are women, and the COLD and MILD brackets hold no
 * male-subject asset at all. Before this fix, every fallback serve
 * (last-resort, resolve-timeout substitution, and the Android rotation
 * floor merge) ignored the user's fit preference: a male-preference
 * user in New York was served a woman's photo by every COLD/MILD/COOL
 * fallback cell he could hit. Field instances, 2026-11-02 ~21:40 EST:
 * the COOL×CLEAR asset (pink long-sleeve) behind a Male-selected
 * settings sheet, and a flagged MALE_CLEAR_MILD_NIGHT serve of the
 * MILD×CLEAR asset. Diagnosis:
 * release-prep/rw-gender-preference-diagnosis-2026-11-02.md.
 *
 * The contract pinned here: a gendered fallback resolution NEVER
 * returns an asset whose photographed subject is the other gender
 * while any bundled asset matches; the subject census itself is
 * pinned against the RW-20 provenance; UNISEX resolution is the
 * legacy chain, byte for byte.
 */
class HeroFallbackGenderTest {

    private val repository = HeroImageRepository()

    private fun assetName(url: String): String =
        url.removePrefix("file:///android_asset/hero_fallback/").removeSuffix(".webp")

    /** The RW-20 provenance census: subject gender per bundled asset. */
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
    fun `subject census covers exactly the 21 bundled cells and both copies agree`() {
        val table = HeroImageRepository.FALLBACK_IMAGES_2D
        var cells = 0
        for ((bracket, weathers) in table) {
            for ((condition, ref) in weathers) {
                cells++
                val expected = expectedSubjects[assetName(ref)]
                assertTrue("no census entry for ${assetName(ref)}", expected != null)
                assertEquals(
                    "engine subject for $bracket/$condition",
                    expected,
                    HeroImageSelector.fallbackSubjectGender(bracket, condition)
                )
                assertEquals(
                    "repository subject for $bracket/$condition",
                    expected,
                    repository.fallbackSubjectGender(bracket, condition)
                )
            }
        }
        assertEquals(21, cells)
        assertEquals(21, expectedSubjects.size)
    }

    @Test
    fun `field instance 2 - MALE MILD CLEAR never resolves to the female mild_clear asset`() {
        // Sol's flagged serve: requested MALE_CLEAR_MILD_NIGHT, served
        // the MILD×CLEAR fallback (a woman). MILD holds no male-subject
        // asset, so the resolver must walk to the nearest matching one:
        // WARM×CLEAR (a man, t-shirt and shorts).
        for (url in listOf(
            HeroImageSelector.getWeatherAwareFallback(
                TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            ),
            repository.getWeatherAwareFallback(
                TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            )
        )) {
            assertNotEquals("mild_clear", assetName(url))
            assertEquals("warm_clear", assetName(url))
        }
    }

    @Test
    fun `field instance 1 - MALE COOL CLEAR never resolves to the female cool_clear asset`() {
        // The screenshot: Fit Preference Male, hero = cool_clear (a
        // woman in a pink long-sleeve). COOL's only male-subject asset
        // is its SNOW cell.
        for (url in listOf(
            HeroImageSelector.getWeatherAwareFallback(
                TempBracket.COOL, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            ),
            repository.getWeatherAwareFallback(
                TempBracket.COOL, HeroWeatherCondition.CLEAR, GenderPreference.MALE
            )
        )) {
            assertNotEquals("cool_clear", assetName(url))
            assertEquals("cool_snow", assetName(url))
        }
    }

    @Test
    fun `FEMALE WARM CLEAR never resolves to the male warm_clear asset`() {
        // Mirror case: WARM×CLEAR and WARM×CLOUDY are men; the bracket's
        // female-subject asset is its RAIN cell.
        val url = HeroImageSelector.getWeatherAwareFallback(
            TempBracket.WARM, HeroWeatherCondition.CLEAR, GenderPreference.FEMALE
        )
        assertEquals("warm_rain", assetName(url))
        assertEquals(
            url,
            repository.getWeatherAwareFallback(
                TempBracket.WARM, HeroWeatherCondition.CLEAR, GenderPreference.FEMALE
            )
        )
    }

    @Test
    fun `matching exact cell is unchanged - FEMALE MILD CLEAR still resolves to mild_clear`() {
        assertEquals(
            "mild_clear",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.FEMALE
                )
            )
        )
        assertEquals(
            "warm_clear",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.WARM, HeroWeatherCondition.CLEAR, GenderPreference.MALE
                )
            )
        )
    }

    @Test
    fun `MALE COLD has no in-bracket match and walks to the nearest male subject`() {
        // Every COLD asset is a woman. Nearest bracket by warmth
        // (warmer first on ties) is COOL; its male subject is SNOW.
        assertEquals(
            "cool_snow",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.COLD, HeroWeatherCondition.CLOUDY, GenderPreference.MALE
                )
            )
        )
    }

    @Test
    fun `every gendered resolution returns a matching subject, for every cell`() {
        for (bracket in TempBracket.values()) {
            for (condition in HeroWeatherCondition.values()) {
                for (gender in listOf(GenderPreference.MALE, GenderPreference.FEMALE)) {
                    val engineUrl = HeroImageSelector.getWeatherAwareFallback(bracket, condition, gender)
                    val repoUrl = repository.getWeatherAwareFallback(bracket, condition, gender)
                    assertEquals("engine/repository disagree for $bracket/$condition/$gender", engineUrl, repoUrl)
                    assertEquals(
                        "wrong-gender subject for $bracket/$condition/$gender",
                        gender,
                        expectedSubjects[assetName(engineUrl)]
                    )
                }
            }
        }
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
            }
        }
        // Spot-check the legacy landings the old test pins.
        assertEquals(
            "mild_clear",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.MILD, HeroWeatherCondition.CLEAR, GenderPreference.UNISEX
                )
            )
        )
        assertEquals(
            "hot_clear",
            assetName(
                HeroImageSelector.getWeatherAwareFallback(
                    TempBracket.HOT, HeroWeatherCondition.SNOW, GenderPreference.UNISEX
                )
            )
        )
    }
}
