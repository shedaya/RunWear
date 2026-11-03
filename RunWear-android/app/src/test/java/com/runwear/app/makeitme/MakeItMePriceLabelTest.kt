package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RW-16.30 pricing experiment readiness (Nov 2, 2026): every price in
 * the paywall path renders from the fetched current RevenueCat
 * offering. These tests pin the two halves of that rule:
 *  1. No string constant in the paywall path carries a price — a user
 *     assigned a $19.99/$29.99 experiment offering can never be shown
 *     the $9.99 control price by a fallback or a CTA.
 *  2. The annual row's "less than paying monthly" suffix is computed
 *     from the fetched amounts: present and correct at the base
 *     prices, absent whenever it would be false.
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
    fun `base prices reproduce the original annual copy`() {
        // $0.99/mo vs $9.99/yr: 1 - 9.99/11.88 = 15.9% -> "about 16%".
        assertEquals(
            16,
            MakeItMeStrings.annualSavingsPercent(micros(9.99), micros(0.99))
        )
        assertEquals(
            "$9.99 / year — about 16% less than paying monthly",
            MakeItMeStrings.annualRowLabel("$9.99", micros(9.99), micros(0.99))
        )
    }

    @Test
    fun `experiment arms drop the savings claim instead of lying`() {
        // $19.99 and $29.99 annuals cost MORE than 12 x $0.99 monthly;
        // the suffix must disappear, the fetched price must remain.
        assertNull(MakeItMeStrings.annualSavingsPercent(micros(19.99), micros(0.99)))
        assertNull(MakeItMeStrings.annualSavingsPercent(micros(29.99), micros(0.99)))
        assertEquals(
            "$19.99 / year",
            MakeItMeStrings.annualRowLabel("$19.99", micros(19.99), micros(0.99))
        )
        assertEquals(
            "$29.99 / year",
            MakeItMeStrings.annualRowLabel("$29.99", micros(29.99), micros(0.99))
        )
    }

    @Test
    fun `unknown monthly price yields no savings claim`() {
        assertNull(MakeItMeStrings.annualSavingsPercent(micros(9.99), null))
        assertNull(MakeItMeStrings.annualSavingsPercent(micros(9.99), 0L))
        assertEquals(
            "$9.99 / year",
            MakeItMeStrings.annualRowLabel("$9.99", micros(9.99), null)
        )
    }
}
