package com.runwear.shared.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-23: heroCascadePatterns preserves the canonical v3.11 cascade
 * exactly, and appends the legacy RAINY/SNOWY token levels only after
 * every canonical level, only for RAIN/SNOW.
 */
class HeroCascadePatternsTest {

    @Test
    fun `CLEAR returns exactly the legacy four patterns`() {
        assertEquals(
            listOf(
                "MALE_CLEAR_COOL_DAWN_%",
                "MALE_CLEAR_COOL_%",
                "FEMALE_CLEAR_COOL_%",
                "FEMALE_CLEAR_COOL_%"
            ),
            heroCascadePatterns("MALE", "CLEAR", "COOL", "DAWN", "FEMALE")
        )
    }

    @Test
    fun `RAIN appends the RAINY pair after the canonical list`() {
        assertEquals(
            listOf(
                "MALE_RAIN_COOL_DAWN_%",
                "MALE_RAIN_COOL_%",
                "MALE_CLEAR_COOL_%",
                "FEMALE_RAIN_COOL_%",
                "FEMALE_CLEAR_COOL_%",
                "MALE_RAINY_COOL_%",
                "FEMALE_RAINY_COOL_%"
            ),
            heroCascadePatterns("MALE", "RAIN", "COOL", "DAWN", "FEMALE")
        )
    }

    @Test
    fun `SNOW appends the SNOWY pair after the canonical list`() {
        assertEquals(
            listOf(
                "MALE_SNOW_COLD_DAWN_%",
                "MALE_SNOW_COLD_%",
                "MALE_CLEAR_COLD_%",
                "FEMALE_SNOW_COLD_%",
                "FEMALE_CLEAR_COLD_%",
                "MALE_SNOWY_COLD_%",
                "FEMALE_SNOWY_COLD_%"
            ),
            heroCascadePatterns("MALE", "SNOW", "COLD", "DAWN", "FEMALE")
        )
    }

    @Test
    fun `CLOUDY appends nothing`() {
        assertEquals(
            listOf(
                "MALE_CLOUDY_COOL_DAWN_%",
                "MALE_CLOUDY_COOL_%",
                "MALE_CLEAR_COOL_%",
                "FEMALE_CLOUDY_COOL_%",
                "FEMALE_CLEAR_COOL_%"
            ),
            heroCascadePatterns("MALE", "CLOUDY", "COOL", "DAWN", "FEMALE")
        )
    }
}
