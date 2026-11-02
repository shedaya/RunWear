package com.runwear.shared.domain.model

import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-20 point-grain serving filter (2026-11-02). The prescriptions come
 * from the REAL engine (GetOutfitRecommendationUseCase v4.3), so these
 * tests pin the end-to-end contract — engine decision plus filter —
 * not a hand-copied snapshot of either.
 *
 * The two anchor scenarios are Sol's field failure and its warm-side
 * counterpart:
 *  - 39°F COOL (feels-like), calm and dry: Light Tights prescribed.
 *    Shorts photos, a jacket photo, and a two-runner photo must NEVER
 *    pass; the tights photo must.
 *  - 47°F COOL: Shorts prescribed (the 45°F breakpoint). Shorts photos
 *    MAY pass; a tights photo must not.
 */
class HeroPointGrainPolicyTest {

    private val engine = GetOutfitRecommendationUseCase()

    private fun outfitAt(
        feelsLikeF: Double,
        raining: Boolean = false,
        windy: Boolean = false
    ): OutfitSelection = engine.recommendFrames(
        adjustedFeelsLikeF = feelsLikeF,
        rawFeelsLikeF = feelsLikeF,
        isRaining = raining,
        precipitation = if (raining) 0.5 else 0.0,
        isWindy = windy,
        isHumid = false,
        uvIndex = 0.0,
        hourOfDay = 6
    )

    private fun matches(
        outfit: OutfitSelection,
        personCount: Int? = 1,
        topBase: String? = "long",
        outer: Boolean? = false,
        bottom: String? = "tights"
    ): Boolean = HeroPointGrainPolicy.matches(
        personCount = personCount,
        topBaseClass = topBase,
        outerClass = outer,
        bottomClass = bottom,
        prescribedTopBase = outfit.topBase,
        prescribedTopOuter = outfit.topOuter,
        prescribedBottom = outfit.bottom
    )

    @Test
    fun `engine prescribes light tights and no shell at 39F calm`() {
        val outfit = outfitAt(39.0)
        assertEquals(ClothingItem.LIGHT_TIGHTS, outfit.bottom)
        assertEquals(ClothingItem.LONG_SLEEVE_LIGHT, outfit.topBase)
        assertEquals(null, outfit.topOuter)
    }

    @Test
    fun `39F COOL never surfaces shorts stock`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, bottom = "shorts"))
        assertFalse(matches(outfit, bottom = "short_shorts"))
    }

    @Test
    fun `39F COOL never surfaces multi-person stock`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, personCount = 2))
        assertFalse(matches(outfit, personCount = 5))
    }

    @Test
    fun `39F COOL never surfaces a jacket the prescription does not call for`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, outer = true))
    }

    @Test
    fun `39F COOL surfaces the correct tights photo`() {
        val outfit = outfitAt(39.0)
        assertTrue(matches(outfit))
        // Capris count as leg coverage in the tights family.
        assertTrue(matches(outfit, bottom = "capris"))
    }

    @Test
    fun `39F COOL rejects a short-sleeve photo and an unreadable torso`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, topBase = "short"))
        assertFalse(matches(outfit, topBase = null, outer = false))
    }

    @Test
    fun `engine prescribes shorts at 47F COOL`() {
        val outfit = outfitAt(47.0)
        assertEquals(ClothingItem.SHORTS, outfit.bottom)
        assertEquals(null, outfit.topOuter)
    }

    @Test
    fun `47F COOL may surface shorts stock but not tights`() {
        val outfit = outfitAt(47.0)
        // At 47F the prescribed base is still a light long sleeve.
        assertTrue(matches(outfit, bottom = "shorts"))
        assertTrue(matches(outfit, bottom = "short_shorts"))
        assertFalse(matches(outfit, bottom = "tights"))
        assertFalse(matches(outfit, bottom = "shorts", personCount = 2))
    }

    @Test
    fun `rain at 39F prescribes a shell and the filter requires one`() {
        val outfit = outfitAt(39.0, raining = true)
        assertEquals(ClothingItem.RAIN_JACKET, outfit.topOuter)
        assertFalse(matches(outfit, outer = false))
        // Base layer unreadable UNDER the prescribed jacket passes.
        assertTrue(matches(outfit, outer = true, topBase = null))
        // A visible wrong base under the jacket still fails.
        assertFalse(matches(outfit, outer = true, topBase = "sleeveless"))
    }

    @Test
    fun `hot weather requires sleeveless and shorts-family bottoms`() {
        val outfit = outfitAt(85.0)
        assertEquals(ClothingItem.TANK_TOP, outfit.topBase)
        assertEquals(ClothingItem.SHORT_SHORTS, outfit.bottom)
        assertTrue(matches(outfit, topBase = "sleeveless", bottom = "short_shorts"))
        assertTrue(matches(outfit, topBase = "sleeveless", bottom = "shorts"))
        assertFalse(matches(outfit, topBase = "sleeveless", bottom = "tights"))
        assertFalse(matches(outfit, topBase = "short", bottom = "shorts"))
    }

    @Test
    fun `a row with no garment classes at all fails closed`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, topBase = null, outer = null, bottom = null))
    }

    @Test
    fun `the key-based bridge facade agrees with the typed entry point`() {
        // The Apple seam calls matchesByKey with enum-case names and a
        // negative person count for "unrecorded"; it must reach the
        // same verdicts as the typed entry point.
        fun byKey(
            personCount: Int = 1,
            topBase: String? = "long",
            outer: String? = "false",
            bottom: String? = "tights",
            topBaseKey: String = "LONG_SLEEVE_LIGHT",
            topOuterKey: String? = null,
            bottomKey: String = "LIGHT_TIGHTS"
        ): Boolean = HeroPointGrainPolicy.matchesByKey(
            personCount = personCount,
            topBaseClass = topBase,
            outerClass = outer,
            bottomClass = bottom,
            prescribedTopBaseKey = topBaseKey,
            prescribedTopOuterKey = topOuterKey,
            prescribedBottomKey = bottomKey
        )

        assertTrue(byKey())
        assertFalse(byKey(bottom = "shorts"))
        assertFalse(byKey(personCount = 2))
        assertFalse(byKey(outer = "true"))
        // Unrecorded person count (negative) behaves like null.
        assertTrue(byKey(personCount = -1))
        // 47F keys: shorts prescribed.
        assertTrue(
            byKey(bottom = "shorts", bottomKey = "SHORTS")
        )
        assertFalse(
            byKey(bottom = "tights", bottomKey = "SHORTS")
        )
    }
}
