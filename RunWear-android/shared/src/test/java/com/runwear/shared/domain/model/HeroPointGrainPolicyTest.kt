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
        bottom: String? = "tights",
        head: String? = "headband",
        hands: String? = "gloves"
    ): Boolean = HeroPointGrainPolicy.matches(
        personCount = personCount,
        topBaseClass = topBase,
        outerClass = outer,
        bottomClass = bottom,
        prescribedTopBase = outfit.topBase,
        prescribedTopOuter = outfit.topOuter,
        prescribedBottom = outfit.bottom,
        headClass = head,
        handsClass = hands,
        prescribedHead = outfit.head,
        prescribedHands = outfit.hands
    )

    @Test
    fun `engine prescribes light tights and no shell at 39F calm`() {
        val outfit = outfitAt(39.0)
        assertEquals(ClothingItem.LIGHT_TIGHTS, outfit.bottom)
        assertEquals(ClothingItem.LONG_SLEEVE_LIGHT, outfit.topBase)
        assertEquals(null, outfit.topOuter)
        assertEquals(ClothingItem.HEADBAND, outfit.head)
        assertEquals(ClothingItem.LIGHT_GLOVES, outfit.hands)
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
        // At 47F the prescribed base is still a light long sleeve, and
        // no head or hands are prescribed.
        assertEquals(null, outfit.head)
        assertEquals(null, outfit.hands)
        assertTrue(matches(outfit, bottom = "shorts", head = null, hands = null))
        assertTrue(matches(outfit, bottom = "short_shorts", head = null, hands = null))
        assertFalse(matches(outfit, bottom = "tights", head = null, hands = null))
        assertFalse(matches(outfit, bottom = "shorts", personCount = 2, head = null, hands = null))
    }

    @Test
    fun `rain at 39F prescribes a shell and the filter requires one`() {
        val outfit = outfitAt(39.0, raining = true)
        assertEquals(ClothingItem.RAIN_JACKET, outfit.topOuter)
        assertEquals(ClothingItem.BASEBALL_CAP, outfit.head)
        assertFalse(matches(outfit, outer = false, head = "cap"))
        // Base layer unreadable UNDER the prescribed jacket passes.
        assertTrue(matches(outfit, outer = true, topBase = null, head = "cap"))
        // A visible wrong base under the jacket still fails.
        assertFalse(matches(outfit, outer = true, topBase = "sleeveless", head = "cap"))
    }

    @Test
    fun `hot weather requires sleeveless and shorts-family bottoms`() {
        val outfit = outfitAt(85.0)
        assertEquals(ClothingItem.TANK_TOP, outfit.topBase)
        assertEquals(ClothingItem.SHORT_SHORTS, outfit.bottom)
        assertEquals(null, outfit.head)
        assertEquals(null, outfit.hands)
        assertTrue(matches(outfit, topBase = "sleeveless", bottom = "short_shorts", head = null, hands = null))
        assertTrue(matches(outfit, topBase = "sleeveless", bottom = "shorts", head = null, hands = null))
        assertFalse(matches(outfit, topBase = "sleeveless", bottom = "tights", head = null, hands = null))
        assertFalse(matches(outfit, topBase = "short", bottom = "shorts", head = null, hands = null))
    }

    @Test
    fun `a row with no garment classes at all fails closed`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, topBase = null, outer = null, bottom = null, head = null, hands = null))
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
            bottomKey: String = "LIGHT_TIGHTS",
            head: String? = "headband",
            hands: String? = "gloves",
            headKey: String? = "HEADBAND",
            handsKey: String? = "LIGHT_GLOVES"
        ): Boolean = HeroPointGrainPolicy.matchesByKey(
            personCount = personCount,
            topBaseClass = topBase,
            outerClass = outer,
            bottomClass = bottom,
            prescribedTopBaseKey = topBaseKey,
            prescribedTopOuterKey = topOuterKey,
            prescribedBottomKey = bottomKey,
            headClass = head,
            handsClass = hands,
            prescribedHeadKey = headKey,
            prescribedHandsKey = handsKey
        )

        assertTrue(byKey())
        assertFalse(byKey(bottom = "shorts"))
        assertFalse(byKey(personCount = 2))
        assertFalse(byKey(outer = "true"))
        // Unrecorded person count (negative) behaves like null.
        assertTrue(byKey(personCount = -1))
        // Head/hands verdicts agree through the bridge too.
        assertFalse(byKey(head = "cap"))
        assertFalse(byKey(head = null))
        assertFalse(byKey(hands = null))
        assertFalse(byKey(hands = "mittens"))
        // 47F keys: shorts prescribed, no head/hands prescribed.
        assertTrue(
            byKey(bottom = "shorts", bottomKey = "SHORTS", head = null, hands = null, headKey = null, handsKey = null)
        )
        assertFalse(
            byKey(bottom = "tights", bottomKey = "SHORTS", head = null, hands = null, headKey = null, handsKey = null)
        )
        assertFalse(
            byKey(bottom = "shorts", bottomKey = "SHORTS", head = "cap", hands = null, headKey = null, handsKey = null)
        )
    }

    @Test
    fun `beanie family - light and thermal beanie prescriptions both accept recorded beanie`() {
        val lightBeanie = OutfitSelection(
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.LIGHT_BEANIE,
            hands = ClothingItem.LIGHT_GLOVES
        )
        assertTrue(matches(lightBeanie, head = "beanie", hands = "gloves"))
        assertFalse(matches(lightBeanie, head = "headband", hands = "gloves"))

        val thermalBeanie = OutfitSelection(
            topBase = ClothingItem.LONG_SLEEVE_THERMAL,
            bottom = ClothingItem.THERMAL_TIGHTS,
            head = ClothingItem.THERMAL_BEANIE,
            hands = ClothingItem.THERMAL_GLOVES
        )
        assertTrue(matches(thermalBeanie, bottom = "tights", head = "beanie", hands = "gloves"))
        assertFalse(matches(thermalBeanie, bottom = "tights", head = "headband", hands = "gloves"))
    }

    @Test
    fun `mittens prescription accepts mittens and rejects gloves`() {
        val mittens = OutfitSelection(
            topBase = ClothingItem.LONG_SLEEVE_THERMAL,
            bottom = ClothingItem.THERMAL_TIGHTS,
            head = ClothingItem.THERMAL_BEANIE,
            hands = ClothingItem.MITTENS
        )
        assertTrue(matches(mittens, bottom = "tights", head = "beanie", hands = "mittens"))
        assertFalse(matches(mittens, bottom = "tights", head = "beanie", hands = "gloves"))
    }

    @Test
    fun `prescribed-null head and hands reject recorded gear`() {
        val outfit = outfitAt(47.0)
        assertFalse(matches(outfit, bottom = "shorts", head = "cap", hands = null))
        assertFalse(matches(outfit, bottom = "shorts", head = null, hands = "gloves"))
    }

    @Test
    fun `prescribed head and hands with recorded null do not serve`() {
        val outfit = outfitAt(39.0)
        assertFalse(matches(outfit, head = null, hands = "gloves"))
        assertFalse(matches(outfit, head = "headband", hands = null))
    }
}
