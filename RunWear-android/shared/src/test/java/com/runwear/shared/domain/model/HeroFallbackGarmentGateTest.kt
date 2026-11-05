package com.runwear.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-2 A2 (W-4/W-5): the bundled-fallback garment gate.
 *
 * The walkthrough defect, as fixtures: Kansas City, feels-like 44°F,
 * COOL bracket — the engine prescribes exactly two items (Light Long
 * Sleeve + Light Tights; head and hands drop out at 40°F). The
 * bundled COOL assets were derived at the bracket's COLD-END point
 * (35°F), where the prescription also carries a headband and light
 * gloves — and the photos wear them. Pre-fix, the bracket-keyed
 * resolver served that photo under the 2-item prescription in every
 * frame, and a Body Temperature change that rewrote the prescription
 * (tights → shorts) could never move the photo, because the fallback
 * key contains no prescription component.
 *
 * These tests pin the fixed behavior: the bundled serve is a function
 * of the prescription (through the adjudicated garment census), a
 * serve that cannot match does not happen, and a prescription change
 * re-resolves.
 */
class HeroFallbackGarmentGateTest {

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

    /** The walkthrough's KC prescription at 44°F: exactly 2 items. */
    private val kcTwoItem = outfit(
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.LIGHT_TIGHTS
    )

    /** The same bracket after Body Temperature → Slightly Hot: shorts. */
    private val kcShorts = outfit(
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.SHORTS
    )

    /** COOL's cold-end prescription (35°F): the bundled assets' own outfit. */
    private val coolColdEnd = outfit(
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.LIGHT_TIGHTS,
        head = ClothingItem.HEADBAND,
        hands = ClothingItem.LIGHT_GLOVES
    )

    @Test
    fun `the census covers exactly the 44 bundled assets`() {
        val pairedFiles = HeroImageSelector.pairedFallbackTable().values
            .flatMap { it.values }
            .map { HeroImageSelector.fallbackAssetFile(it) }
            .toSet()
        assertEquals(44, pairedFiles.size)
        assertEquals(pairedFiles, HeroImageSelector.fallbackGarmentsTable().keys)
    }

    @Test
    fun `W-4 - the COOL asset does not match the 2-item KC prescription`() {
        val coolClearFemale =
            "file:///android_asset/hero_fallback/cool_clear_female.webp"
        assertFalse(
            HeroImageSelector.fallbackMatchesPrescription(coolClearFemale, kcTwoItem)
        )
        // The photo DOES match the prescription it was derived for —
        // the census is the photo's truth, not a blanket rejection.
        assertTrue(
            HeroImageSelector.fallbackMatchesPrescription(coolClearFemale, coolColdEnd)
        )
    }

    @Test
    fun `W-4 - no bundled asset serves the 2-item KC prescription`() {
        // Long sleeve + tights + no outer + bare head/hands exists
        // nowhere in the bundled library: the honest terminus is no
        // photo, never the headband-and-gloves photo.
        assertNull(
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, kcTwoItem
            )
        )
        assertNull(
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, kcTwoItem
            )
        )
        assertNull(
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.UNISEX, kcTwoItem
            )
        )
    }

    @Test
    fun `the cold-end COOL prescription still serves its own cell asset`() {
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, coolColdEnd
            )
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_male.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, coolColdEnd
            )
        )
    }

    @Test
    fun `W-5 - resolution is a function of the prescription, so a change re-resolves`() {
        // Same cell, three prescriptions, three different outcomes:
        // the pre-fix bracket key made all three the same photo.
        val cell = TempBracket.COOL to HeroWeatherCondition.CLEAR
        val forTights = HeroImageSelector.gatedFallbackUrl(
            cell.first, cell.second, GenderPreference.FEMALE, kcTwoItem
        )
        val forColdEnd = HeroImageSelector.gatedFallbackUrl(
            cell.first, cell.second, GenderPreference.FEMALE, coolColdEnd
        )
        val forShorts = HeroImageSelector.gatedFallbackUrl(
            cell.first, cell.second, GenderPreference.FEMALE, kcShorts
        )
        assertNull(forTights)
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp", forColdEnd
        )
        // Shorts under a long sleeve exists nowhere in the library
        // either — the change is VISIBLE (the mismatched photo is
        // gone) instead of the photo silently staying.
        assertNull(forShorts)
    }

    @Test
    fun `rain and deep-winter prescriptions serve their gated assets`() {
        val coolRain = outfit(
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.LIGHT_TIGHTS,
            topOuter = ClothingItem.RAIN_JACKET,
            head = ClothingItem.BASEBALL_CAP,
            hands = ClothingItem.LIGHT_GLOVES
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_rain_female.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.COOL, HeroWeatherCondition.RAIN,
                GenderPreference.FEMALE, coolRain
            )
        )

        // FREEZING CLEAR male: the asset's base is unperceived under
        // its shell — the point-grain policy's under-shell rule lets
        // it serve, exactly as for a pool row.
        val freezing = outfit(
            topBase = ClothingItem.LONG_SLEEVE_THERMAL,
            bottom = ClothingItem.THERMAL_TIGHTS,
            topOuter = ClothingItem.WINDBREAKER,
            head = ClothingItem.THERMAL_BEANIE,
            hands = ClothingItem.THERMAL_GLOVES
        )
        assertEquals(
            "file:///android_asset/hero_fallback/freezing_clear_male.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.FREEZING, HeroWeatherCondition.CLEAR,
                GenderPreference.MALE, freezing
            )
        )
    }

    @Test
    fun `warm brackets serve at their own point prescriptions`() {
        val mild = outfit(
            topBase = ClothingItem.SHORT_SLEEVE,
            bottom = ClothingItem.SHORTS
        )
        assertEquals(
            "file:///android_asset/hero_fallback/mild_clear_female.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.MILD, HeroWeatherCondition.CLEAR,
                GenderPreference.FEMALE, mild
            )
        )
        // A shorts prescription is in the shorts family for the
        // short-shorts asset too (point-grain family rule).
        val hot = outfit(
            topBase = ClothingItem.TANK_TOP,
            bottom = ClothingItem.SHORT_SHORTS
        )
        assertEquals(
            "file:///android_asset/hero_fallback/hot_cloudy_male.webp",
            HeroImageSelector.gatedFallbackUrl(
                TempBracket.HOT, HeroWeatherCondition.CLOUDY,
                GenderPreference.MALE, hot
            )
        )
    }

    @Test
    fun `an uncensused asset fails closed`() {
        assertFalse(
            HeroImageSelector.fallbackMatchesPrescription(
                "file:///android_asset/hero_fallback/not_a_real_asset.webp",
                coolColdEnd
            )
        )
    }

    @Test
    fun `getFallbackImage serves the labelled floor scaffold when the gate exhausts`() {
        // GEN-4 (Sol's ruling, 2026-11-04) supersedes the gen-2/3
        // terminus pinned here before: the exact GATE still exhausts
        // for the 2-item KC prescription (gatedFallbackUrl is null —
        // no bundled asset may CLAIM it), but the hero may no longer
        // settle on the bare gradient. The chain's FLOOR tier serves
        // the nearest same-gender bundled asset — the COOL bracket's
        // own cold-end photo — labelled SCAFFOLD, never exact.
        val result = HeroImageSelector.getFallbackImage(
            weatherFixture(), kcTwoItem,
            GenderPreference.FEMALE, frameFeelsLikeF = 44.0
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp",
            result.imageUrl
        )
        assertEquals(HeroServeSource.SCAFFOLD, result.serveSource)
        assertTrue(result.isScaffold)
        assertEquals("FLOOR", result.chainTier)

        val matched = HeroImageSelector.getFallbackImage(
            weatherFixture(), coolColdEnd,
            GenderPreference.FEMALE, frameFeelsLikeF = 44.0
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp",
            matched.imageUrl
        )
        // The exact serve keeps its exact label.
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, matched.serveSource)
        assertFalse(matched.isScaffold)
    }
}
