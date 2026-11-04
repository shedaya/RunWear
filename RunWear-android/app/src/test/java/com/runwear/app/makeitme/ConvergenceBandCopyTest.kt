package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4): the band's
 * copy is pinned WORD FOR WORD, its progress is phrased in plain
 * words with the real counts, and the band exists only while a
 * subscribed user's bank is genuinely converging.
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
    fun `progress is phrased in plain words with the real counts`() {
        assertEquals(
            "14 of your 24 looks are ready",
            MakeItMeStrings.bandMakingProgress(ready = 14, total = 24)
        )
        assertEquals(
            "1 of your 20 looks are ready",
            MakeItMeStrings.bandMakingProgress(ready = 1, total = 20)
        )
        // Never a bare fraction, never "N of M ready".
        val line = MakeItMeStrings.bandMakingProgress(ready = 14, total = 24)
        assertTrue(line.contains("of your"))
        assertTrue(line.endsWith("looks are ready"))
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
    fun `the band shows only for a subscribed user mid-convergence with real counts`() {
        val mid = PersonalHeroService.BankCoverage(ready = 14, total = 24)
        val content = ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, mid)
        assertEquals("Making your looks", content?.heading)
        assertEquals("14 of your 24 looks are ready", content?.progressText)
        assertEquals(14f / 24f, content?.fraction)

        // Bank complete: no band.
        assertNull(
            ConvergenceBandPolicy.content(
                MakeItMeMode.SUBSCRIBED,
                PersonalHeroService.BankCoverage(ready = 24, total = 24)
            )
        )
        // Counts unknown: no band (never an invented number).
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, null))
        // Not subscribed: no band.
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.FREE, mid))
        assertNull(ConvergenceBandPolicy.content(MakeItMeMode.STOCK, mid))
        // Empty engaged set: no band.
        assertNull(
            ConvergenceBandPolicy.content(
                MakeItMeMode.SUBSCRIBED,
                PersonalHeroService.BankCoverage(ready = 0, total = 0)
            )
        )
    }
}
