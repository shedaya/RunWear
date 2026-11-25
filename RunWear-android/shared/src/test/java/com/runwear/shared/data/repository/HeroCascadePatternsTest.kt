package com.runwear.shared.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-23: heroCascadePatterns appends the legacy RAINY/SNOWY token
 * levels only after every canonical level, only for RAIN/SNOW.
 *
 * RW-178 (2026-11-25): gender is a HARD FILTER — the cascade never
 * emits an opposite-gender pattern. The v3.11 levels 4–5 (opposite
 * gender + weather / + clear) and the legacy opposite-gender levels
 * are gone; widening past these patterns happens on the outfit axis
 * via the GEN-4 fallback chain's same-gender scaffold tier.
 */
class HeroCascadePatternsTest {

    @Test
    fun `CLEAR returns exactly the same-gender pair`() {
        assertEquals(
            listOf(
                "MALE_CLEAR_COOL_DAWN_%",
                "MALE_CLEAR_COOL_%"
            ),
            heroCascadePatterns("MALE", "CLEAR", "COOL", "DAWN")
        )
    }

    @Test
    fun `RAIN appends clear then the same-gender RAINY level`() {
        assertEquals(
            listOf(
                "MALE_RAIN_COOL_DAWN_%",
                "MALE_RAIN_COOL_%",
                "MALE_CLEAR_COOL_%",
                "MALE_RAINY_COOL_%"
            ),
            heroCascadePatterns("MALE", "RAIN", "COOL", "DAWN")
        )
    }

    @Test
    fun `SNOW appends clear then the same-gender SNOWY level`() {
        assertEquals(
            listOf(
                "MALE_SNOW_COLD_DAWN_%",
                "MALE_SNOW_COLD_%",
                "MALE_CLEAR_COLD_%",
                "MALE_SNOWY_COLD_%"
            ),
            heroCascadePatterns("MALE", "SNOW", "COLD", "DAWN")
        )
    }

    @Test
    fun `CLOUDY appends only the clear level`() {
        assertEquals(
            listOf(
                "MALE_CLOUDY_COOL_DAWN_%",
                "MALE_CLOUDY_COOL_%",
                "MALE_CLEAR_COOL_%"
            ),
            heroCascadePatterns("MALE", "CLOUDY", "COOL", "DAWN")
        )
    }

    @Test
    fun `no weather ever emits a cross-gender pattern`() {
        for (weather in listOf("CLEAR", "CLOUDY", "RAIN", "SNOW")) {
            val male = heroCascadePatterns("MALE", weather, "COOL", "DAWN")
            assertTrue(
                "MALE $weather emitted a FEMALE pattern: $male",
                male.none { it.startsWith("FEMALE_") }
            )
            val female = heroCascadePatterns("FEMALE", weather, "COOL", "DAWN")
            assertTrue(
                "FEMALE $weather emitted a MALE pattern: $female",
                female.none { it.startsWith("MALE_") }
            )
        }
    }
}
