package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RW-26 §8-I: days past the forecast horizon are named, never dressed. */
class ForecastHorizonPolicyTest {

    @Test
    fun `the horizon is seven days`() {
        assertFalse(ForecastHorizonPolicy.isBeyondHorizon(0))
        assertFalse(ForecastHorizonPolicy.isBeyondHorizon(6))
        assertTrue(ForecastHorizonPolicy.isBeyondHorizon(7))
        assertTrue(ForecastHorizonPolicy.isBeyondHorizon(13))
    }

    @Test
    fun `cells inside the horizon keep their date, past it they are named`() {
        assertEquals("Nov 8", ForecastHorizonPolicy.cellSublabel(6, "Nov 8"))
        assertEquals("Beyond forecast", ForecastHorizonPolicy.cellSublabel(7, "Nov 9"))
    }
}
