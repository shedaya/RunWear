package com.runwear.shared.data.repository

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
 * The hero fallback contract after the 2026-10-29 imagery refresh:
 *
 * - Every fallback cell (temp bracket × weather condition) in BOTH copies
 *   of the table — [HeroImageRepository]'s and the engine's
 *   [HeroImageSelector] — resolves to a BUNDLED asset derived from an
 *   approved pool photo, so the fallback renders fully offline.
 * - No Unsplash (or any remote) URL remains anywhere in the fallback path.
 * - The two tables agree cell-for-cell, and the resolution chain
 *   (exact cell → CLEAR of the same bracket → MILD×CLEAR) is unchanged.
 */
class HeroFallbackImagesTest {

    private val repository = HeroImageRepository()
    private val table = HeroImageRepository.FALLBACK_IMAGES_2D

    @Test
    fun `table coverage is unchanged - 22 cells, snow only from MILD down`() {
        assertEquals(6, table.size)
        assertEquals(3, table.getValue(TempBracket.HOT).size)
        assertEquals(3, table.getValue(TempBracket.WARM).size)
        assertEquals(4, table.getValue(TempBracket.MILD).size)
        assertEquals(4, table.getValue(TempBracket.COOL).size)
        assertEquals(4, table.getValue(TempBracket.COLD).size)
        assertEquals(4, table.getValue(TempBracket.FREEZING).size)
        assertFalse(table.getValue(TempBracket.HOT).containsKey(HeroWeatherCondition.SNOW))
        assertFalse(table.getValue(TempBracket.WARM).containsKey(HeroWeatherCondition.SNOW))
    }

    @Test
    fun `every cell is a bundled pool-derived asset and both tables agree`() {
        for ((bracket, cells) in table) {
            for ((condition, ref) in cells) {
                assertTrue(
                    "$bracket/$condition must be a bundled fallback asset, was: $ref",
                    ref.startsWith("file:///android_asset/hero_fallback/")
                )
                assertTrue("$bracket/$condition asset must exist", assetFile(ref).length() > 0)
                assertEquals(
                    "$bracket/$condition: engine table disagrees with repository table",
                    ref,
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition)
                )
                assertEquals(
                    "$bracket/$condition: repository resolver disagrees with its table",
                    ref,
                    repository.getWeatherAwareFallback(bracket, condition)
                )
            }
        }
    }

    @Test
    fun `every asset name follows the bracket_condition convention`() {
        for ((bracket, cells) in table) {
            for ((condition, ref) in cells) {
                val expected = "file:///android_asset/hero_fallback/" +
                    "${bracket.name.lowercase()}_${condition.name.lowercase()}.webp"
                assertEquals(expected, ref)
            }
        }
    }

    @Test
    fun `no stock or remote URL remains anywhere in the fallback path`() {
        for (bracket in TempBracket.values()) {
            for (condition in HeroWeatherCondition.values()) {
                for (ref in listOf(
                    HeroImageSelector.getWeatherAwareFallback(bracket, condition),
                    repository.getWeatherAwareFallback(bracket, condition)
                )) {
                    assertFalse("$bracket/$condition still remote: $ref", ref.startsWith("http"))
                    assertFalse("$bracket/$condition still stock: $ref", ref.contains("unsplash"))
                    assertTrue(
                        "$bracket/$condition must resolve to a bundled asset, was: $ref",
                        ref.startsWith("file:///android_asset/hero_fallback/")
                    )
                }
            }
        }
    }

    @Test
    fun `resolution chain is unchanged - missing cells fall back to bracket CLEAR then MILD CLEAR`() {
        val hotClear = table.getValue(TempBracket.HOT).getValue(HeroWeatherCondition.CLEAR)
        val warmClear = table.getValue(TempBracket.WARM).getValue(HeroWeatherCondition.CLEAR)
        // HOT×SNOW and WARM×SNOW are not defined cells: they resolve to the
        // bracket's CLEAR asset, exactly as with the old stock table.
        assertEquals(hotClear, HeroImageSelector.getWeatherAwareFallback(TempBracket.HOT, HeroWeatherCondition.SNOW))
        assertEquals(hotClear, repository.getWeatherAwareFallback(TempBracket.HOT, HeroWeatherCondition.SNOW))
        assertEquals(warmClear, HeroImageSelector.getWeatherAwareFallback(TempBracket.WARM, HeroWeatherCondition.SNOW))
        assertEquals(warmClear, repository.getWeatherAwareFallback(TempBracket.WARM, HeroWeatherCondition.SNOW))
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
