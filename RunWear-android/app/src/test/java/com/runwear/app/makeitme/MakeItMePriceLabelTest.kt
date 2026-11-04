package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-16.30 pricing experiment readiness (Nov 2, 2026): every price in
 * the paywall path renders from the fetched current RevenueCat
 * offering. These tests pin the two halves of that rule:
 *  1. No string constant in the paywall path carries a price — a user
 *     assigned a $19.99/$29.99 experiment offering can never be shown
 *     the $9.99 control price by a fallback or a CTA.
 *  2. Gen-3 WS-F: the annual row carries NO monthly comparison at
 *     any arm; the annual value is framed by per-run fine print
 *     DERIVED from the fetched annual amount (basis stated).
 */
class MakeItMePriceLabelTest {

    private fun micros(dollars: Double): Long = Math.round(dollars * 1_000_000.0)

    @Test
    fun `paywall string constants carry no price`() {
        val constants = listOf(
            MakeItMeStrings.U1D_PRIMARY,
            MakeItMeStrings.U2B_MONTHLY,
            MakeItMeStrings.U2B_ANNUAL,
            MakeItMeStrings.S8_STATE_B_SUBSCRIBE_SUB
        )
        for (s in constants) {
            assertFalse("price leaked into paywall copy: $s", s.contains("$"))
            assertFalse("price leaked into paywall copy: $s", s.contains("0.99"))
            assertFalse("price leaked into paywall copy: $s", s.contains("9.99"))
        }
    }

    @Test
    fun `monthly label renders the offering price`() {
        assertEquals("$19.99 / month", MakeItMeStrings.monthlyRowLabel("$19.99"))
        assertEquals("$0.99 / month", MakeItMeStrings.monthlyRowLabel("$0.99"))
    }

    @Test
    fun `annual row carries no monthly comparison at any arm - WS-F`() {
        // Gen-3 WS-F: the "about 16% less than paying monthly" line
        // is removed at every price point.
        for ((formatted, annual) in listOf(
            "$9.99" to micros(9.99), "$19.99" to micros(19.99), "$29.99" to micros(29.99)
        )) {
            val label = MakeItMeStrings.annualRowLabel(formatted, annual, micros(0.99))
            assertEquals("$formatted / year", label)
            assertFalse(label.contains("less than paying monthly"))
        }
    }

    @Test
    fun `per-run figure is derived from the annual price at all three arms - WS-F`() {
        // annual cents / (4.5 x 52 = 234 runs), rounded honestly.
        assertEquals(4, MakeItMeStrings.perRunCents(micros(9.99)))
        assertEquals(9, MakeItMeStrings.perRunCents(micros(19.99)))
        assertEquals(13, MakeItMeStrings.perRunCents(micros(29.99)))
        assertEquals(
            "At four to five runs a week, $9.99/year works out to only about 4¢ per run.",
            MakeItMeStrings.perRunLine("$9.99", micros(9.99))
        )
        assertEquals(
            "At four to five runs a week, $19.99/year works out to only about 9¢ per run.",
            MakeItMeStrings.perRunLine("$19.99", micros(19.99))
        )
        assertEquals(
            "At four to five runs a week, $29.99/year works out to only about 13¢ per run.",
            MakeItMeStrings.perRunLine("$29.99", micros(29.99))
        )
    }

    @Test
    fun `fine print leads with the derived per-run line and keeps the settled text`() {
        val fp = MakeItMeStrings.finePrint("$9.99", micros(9.99))
        assertTrue(fp.startsWith("At four to five runs a week, $9.99/year works out to only about 4¢ per run."))
        assertTrue(fp.contains(MakeItMeStrings.U2C_FINE_PRINT))
        assertFalse(fp.contains("less than paying monthly"))
    }
}
