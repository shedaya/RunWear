package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageSelector
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.TempBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The hero fallback contract after WS-7 (RW-26 full fix, 2026-11-03):
 *
 * - The bundled matrix is gender-PAIRED: 22 cells x MALE/FEMALE = 44
 *   assets staged from the verified pool (bundle manifest:
 *   release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md), in BOTH
 *   copies of the table — [HeroImageRepository]'s and the engine's
 *   [HeroImageSelector] — and the two copies agree cell-for-cell.
 * - FREEZING x RAIN is a cell again (Sol's Nov 3 ruling); the RW-20
 *   designed degradation is retired for gendered serves.
 * - The 21 RW-20-era single assets are gone — including the 5 whose
 *   source rows the legacy re-adjudication rejected (D-21).
 * - UNISEX resolution is the legacy chain over the legacy view
 *   (exact cell -> bracket CLEAR -> MILD x CLEAR), unchanged.
 */
class HeroFallbackImagesTest {

    private val repository = HeroImageRepository()
    private val paired = HeroImageRepository.FALLBACK_IMAGES_PAIRED
    private val legacy = HeroImageRepository.FALLBACK_IMAGES_2D

    @Test
    fun `paired table coverage - 22 cells x 2 genders after WS-7`() {
        assertEquals(22, paired.size)
        assertEquals(44, paired.values.sumOf { it.size })
        for ((cell, genders) in paired) {
            assertEquals("$cell must carry both genders", setOf(GenderPreference.FEMALE, GenderPreference.MALE), genders.keys)
        }
        fun bracketCount(b: TempBracket) = paired.keys.count { it.first == b }
        assertEquals(3, bracketCount(TempBracket.HOT))
        assertEquals(3, bracketCount(TempBracket.WARM))
        assertEquals(4, bracketCount(TempBracket.MILD))
        assertEquals(4, bracketCount(TempBracket.COOL))
        assertEquals(4, bracketCount(TempBracket.COLD))
        // FREEZING is back to 4 cells: RAIN restored by Sol's ruling.
        assertEquals(4, bracketCount(TempBracket.FREEZING))
        assertTrue(paired.containsKey(TempBracket.FREEZING to HeroWeatherCondition.RAIN))
        assertFalse(paired.containsKey(TempBracket.HOT to HeroWeatherCondition.SNOW))
        assertFalse(paired.containsKey(TempBracket.WARM to HeroWeatherCondition.SNOW))
    }

    @Test
    fun `every paired asset is bundled, exists, and both tables agree`() {
        assertEquals(HeroImageSelector.pairedFallbackTable(), paired)
        for ((cell, genders) in paired) {
            for ((gender, ref) in genders) {
                assertTrue(
                    "$cell/$gender must be a bundled fallback asset, was: $ref",
                    ref.startsWith("file:///android_asset/hero_fallback/")
                )
                assertTrue("$cell/$gender asset must exist", assetFile(ref).length() > 0)
                assertEquals(
                    "$cell/$gender: engine paired asset disagrees",
                    ref,
                    HeroImageSelector.pairedFallbackAsset(cell.first, cell.second, gender)
                )
                assertEquals(
                    "$cell/$gender: repository paired asset disagrees",
                    ref,
                    repository.pairedFallbackAsset(cell.first, cell.second, gender)
                )
            }
        }
    }

    @Test
    fun `every paired asset name follows the bracket_weather_gender convention`() {
        for ((cell, genders) in paired) {
            for ((gender, ref) in genders) {
                val expected = "file:///android_asset/hero_fallback/" +
                    "${cell.first.name.lowercase()}_${cell.second.name.lowercase()}_${gender.name.lowercase()}.webp"
                assertEquals(expected, ref)
            }
        }
    }

    @Test
    fun `assets directory holds exactly the 44 paired files plus the point-grain set - the RW-20 set is retired`() {
        val dir = assetFile(paired.values.first().values.first()).parentFile
        val names = dir.listFiles()?.filter { it.extension == "webp" }?.map { it.name }?.toSet() ?: emptySet()
        val expected = paired.values.flatMap { it.values }.map { it.substringAfterLast('/') }.toSet()
        // GEN-3 A-HERO (WS-A(1)): the directory additionally holds the
        // 10 point-grain assets (the pg_* files, keyed by gate
        // signature in HeroImageSelector's point-grain tables) — the
        // pinned total is 44 paired + 10 point-grain, nothing else.
        val pointGrain = HeroImageSelector.pointGrainGarmentsTable().keys
            .map { "$it.webp" }.toSet()
        assertEquals(10, pointGrain.size)
        assertEquals(54, names.size)
        assertEquals(expected + pointGrain, names)
        // None of the 21 legacy ungendered names survives (D-21: the
        // set that shipped the 5 re-adjudication-rejected sources).
        for (legacyName in listOf(
            "cool_clear.webp", "cool_cloudy.webp", "cold_rain.webp", "mild_rain.webp",
            "hot_clear.webp", "mild_clear.webp", "freezing_snow.webp"
        )) {
            assertFalse("$legacyName must be retired", names.contains(legacyName))
        }
    }

    @Test
    fun `no stock or remote URL remains anywhere in the fallback path`() {
        for (bracket in TempBracket.values()) {
            for (condition in HeroWeatherCondition.values()) {
                for (gender in GenderPreference.values()) {
                    for (ref in listOf(
                        HeroImageSelector.getWeatherAwareFallback(bracket, condition, gender),
                        repository.getWeatherAwareFallback(bracket, condition, gender)
                    )) {
                        assertFalse("$bracket/$condition/$gender still remote: $ref", ref.startsWith("http"))
                        assertFalse("$bracket/$condition/$gender still stock: $ref", ref.contains("unsplash"))
                        assertTrue(
                            "$bracket/$condition/$gender must resolve to a bundled asset, was: $ref",
                            ref.startsWith("file:///android_asset/hero_fallback/")
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `legacy view - 21 cells, each the census subject's paired file, both copies agree`() {
        assertEquals(6, legacy.size)
        assertEquals(21, legacy.values.sumOf { it.size })
        assertFalse(legacy.getValue(TempBracket.FREEZING).containsKey(HeroWeatherCondition.RAIN))
        for ((bracket, cells) in legacy) {
            for ((condition, ref) in cells) {
                val subject = HeroImageSelector.fallbackSubjectGender(bracket, condition)
                assertTrue("legacy cell $bracket/$condition needs a census subject", subject != null)
                assertEquals(
                    "$bracket/$condition legacy view must be the census subject's paired file",
                    paired.getValue(bracket to condition).getValue(subject!!),
                    ref
                )
                assertEquals(
                    "$bracket/$condition: UNISEX resolver disagrees with legacy view",
                    ref,
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition)
                )
                assertEquals(
                    "$bracket/$condition: repository UNISEX resolver disagrees with legacy view",
                    ref,
                    repository.getWeatherAwareFallback(bracket, condition)
                )
            }
        }
    }

    @Test
    fun `legacy chain is unchanged - missing cells fall back to bracket CLEAR then MILD CLEAR`() {
        fun legacyFile(b: TempBracket, w: HeroWeatherCondition) =
            legacy.getValue(b).getValue(w).substringAfterLast('/')
        // HOT x SNOW and WARM x SNOW are not cells: bracket CLEAR.
        assertEquals(
            legacyFile(TempBracket.HOT, HeroWeatherCondition.CLEAR),
            HeroImageSelector.getWeatherAwareFallback(TempBracket.HOT, HeroWeatherCondition.SNOW).substringAfterLast('/')
        )
        assertEquals(
            legacyFile(TempBracket.WARM, HeroWeatherCondition.CLEAR),
            repository.getWeatherAwareFallback(TempBracket.WARM, HeroWeatherCondition.SNOW).substringAfterLast('/')
        )
        // FREEZING x RAIN under the UNISEX legacy chain still degrades
        // to freezing_clear — byte-for-byte the legacy behavior. (The
        // degradation is retired for GENDERED serves only; see the
        // gender test.)
        assertEquals(
            "freezing_clear_female.webp",
            HeroImageSelector.getWeatherAwareFallback(TempBracket.FREEZING, HeroWeatherCondition.RAIN).substringAfterLast('/')
        )
        assertEquals(
            "freezing_clear_female.webp",
            repository.getWeatherAwareFallback(TempBracket.FREEZING, HeroWeatherCondition.RAIN).substringAfterLast('/')
        )
    }

    private fun assetFile(ref: String): File {
        val rel = ref.removePrefix("file:///android_asset/")
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (prefix in listOf("src/main/assets", "shared/src/main/assets", "RunWear-android/shared/src/main/assets")) {
                val candidate = File(dir, "$prefix/$rel")
                if (candidate.exists()) return candidate
            }
            dir = dir.parentFile
        }
        fail("bundled fallback asset not found under any assets root: $rel")
        throw AssertionError("unreachable")
    }
}
