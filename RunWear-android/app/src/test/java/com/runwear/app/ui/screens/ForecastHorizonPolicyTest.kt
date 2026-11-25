package com.runwear.app.ui.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-26 §8-I: days past the forecast horizon are never dressed.
 * RW-102 (Sol's directive, Nov 6, 2026): the date strip must not
 * INCLUDE dates we can't give projections for — it ends at the
 * horizon. This supersedes the RW-26 form, which rendered
 * beyond-horizon cells named "Beyond forecast"; the refusal
 * guard behind the strip remains.
 */
class ForecastHorizonPolicyTest {

    @Test
    fun `the horizon is seven days`() {
        assertFalse(ForecastHorizonPolicy.isBeyondHorizon(0))
        assertFalse(ForecastHorizonPolicy.isBeyondHorizon(6))
        assertTrue(ForecastHorizonPolicy.isBeyondHorizon(7))
        assertTrue(ForecastHorizonPolicy.isBeyondHorizon(13))
    }

    @Test
    fun `the strip ends at the horizon - RW-102`() {
        val today = LocalDate.of(2026, 11, 6)
        val days = ForecastHorizonPolicy.stripDays(today)
        assertEquals(7, days.size)
        assertEquals(today, days.first())
        assertEquals(today.plusDays(6), days.last())
        // No offered day is beyond the horizon.
        assertTrue(days.none {
            ForecastHorizonPolicy.isBeyondHorizon(
                (it.toEpochDay() - today.toEpochDay()).toInt()
            )
        })
    }
}
