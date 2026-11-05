package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4) + the
 * Nov 4 HOLD on the numeric count: the heading and line are
 * pinned WORD FOR WORD; NO numeric count is shown anywhere (the
 * earlier "N of your M looks are ready" wording is withdrawn —
 * neither 24 nor 48 is hard-coded); the progress bar's fraction
 * still derives from the real banking state; and the band is
 * PREMIUM CONTEXT ONLY — never for free users, never in-trial
 * (banking is Premium-only behaviour).
 */
class ConvergenceBandCopyTest {

    @Test
    fun `the heading and line are Sol's settled wording, word for word`() {
        assertEquals("Making your looks", MakeItMeStrings.BAND_MAKING_HEADING)
        assertEquals(
            "We're generating your outfits across all running conditions, so it will be ready when you need it.",
            MakeItMeStrings.BAND_MAKING_LINE
        )
    }

    @Test
    fun `no numeric count is carried by the band - the count wording is withdrawn`() {
        val content = ConvergenceBandPolicy.content(
            mode = MakeItMeMode.SUBSCRIBED,
            coverage = PersonalHeroService.BankCoverage(ready = 14, total = 48),
            inTrial = false
        )!!
        // The rendered strings carry no digits at all — no "14",
        // no "24", no "48", no fraction label.
        assertFalse(content.heading.any { it.isDigit() })
        assertFalse(content.line.any { it.isDigit() })
        // The bar's fraction still comes from the real state.
        assertEquals(14f / 48f, content.fraction)
    }

    @Test
    fun `coverage derives from the banked set and the engaged set`() {
        val cov = deriveBankCoverage(
            heroCells = setOf("a", "b", "c"),
            jobCells = setOf("c", "d", "e", "f")
        )
        assertEquals(3, cov.ready)
        assertEquals(6, cov.total)
    }

    @Test
    fun `the band is Premium context only - never free, never in-trial`() {
        val mid = PersonalHeroService.BankCoverage(ready = 14, total = 48)
        // Paying subscriber, mid-convergence: the band shows.
        val content = ConvergenceBandPolicy.content(
            MakeItMeMode.SUBSCRIBED, mid, inTrial = false
        )
        assertEquals("Making your looks", content?.heading)

        // In-trial: NO band (trials get on-demand looks, no bank).
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, mid, inTrial = true)
        )
        // Free / stock: no band.
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.FREE, mid, inTrial = false))
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.STOCK, mid, inTrial = false))
        // Bank complete: no band.
        assertNull(
            ConvergenceBandPolicy.content(
                MakeItMeMode.SUBSCRIBED,
                PersonalHeroService.BankCoverage(ready = 48, total = 48),
                inTrial = false
            )
        )
        // State unknown: no band (never an invented fraction).
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, null, inTrial = false))
        // Empty engaged set: no band.
        assertNull(
            ConvergenceBandPolicy.content(
                MakeItMeMode.SUBSCRIBED,
                PersonalHeroService.BankCoverage(ready = 0, total = 0),
                inTrial = false
            )
        )
    }
}
