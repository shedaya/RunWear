package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4) + the
 * COUNT RULING (Sol, Nov 4 — FINAL, closing the earlier hold): the
 * heading and line are pinned WORD FOR WORD; NO numeric count is
 * shown anywhere in either placement (the earlier "N of your M
 * looks are ready" wording is withdrawn for good — neither 24 nor
 * 48 is hard-coded); the progress bar's fraction still derives
 * from the real banking state; and the band is PREMIUM CONTEXT
 * ONLY — never for free users, never in-trial (banking is
 * Premium-only behaviour). The FREE tier's separate "X of 5"
 * counters are explicitly KEPT (last test).
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
    fun `coverage without a target set reports no denominator`() {
        // RW-126 / review row 19 (Nov 7, 2026): the gen-3
        // engaged-union fallback is DELETED — after process death
        // it invented a small denominator from whatever cells
        // happened to be engaged and read as near-convergence.
        // No target set means the denominator is UNKNOWN: the
        // coverage is empty and the band stays retired until the
        // forecast derivation restores the real target.
        val cov = deriveBankCoverage(
            heroCells = setOf("a", "b", "c"),
            jobCells = setOf("c", "d", "e", "f")
        )
        assertEquals(0, cov.ready)
        assertEquals(0, cov.total)
    }

    @Test
    fun `gen-5 defect 2 - against the target set the bar cannot fake convergence`() {
        // Sol's Nov 5 numbers, exactly as the diagnosis reproduces
        // them: 8 banked cells, 1 further engaged cell, a 48-cell
        // settled bank. The gen-4 formula read 8/9 = 89%.
        val target = (1..48).map { "cell-$it" }.toSet()
        val cov = deriveBankCoverage(
            heroCells = (1..8).map { "cell-$it" }.toSet(),
            jobCells = (1..9).map { "cell-$it" }.toSet(),
            targetCells = target
        )
        assertEquals(8, cov.ready)
        assertEquals(48, cov.total)
        val content = ConvergenceBandPolicy.content(
            MakeItMeMode.SUBSCRIBED, cov, inTrial = false
        )!!
        assertEquals(8f / 48f, content.fraction)
        // Off-target banked cells never inflate readiness, and the
        // band retires only when every target cell is banked.
        val withExtras = deriveBankCoverage(
            heroCells = target + setOf("elsewhere-1", "elsewhere-2"),
            jobCells = emptySet(),
            targetCells = target
        )
        assertEquals(48, withExtras.ready)
        assertEquals(48, withExtras.total)
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, withExtras, inTrial = false)
        )
        val oneMissing = deriveBankCoverage(
            heroCells = target - "cell-48",
            jobCells = emptySet(),
            targetCells = target
        )
        assertEquals(47, oneMissing.ready)
        assertNotNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, oneMissing, inTrial = false)
        )
    }

    @Test
    fun `gen-5 defect 1 - the fill request names the forecast cells`() {
        // The request body is the contract: priority_cells, in
        // forecast order, deduplicated — never the gen-4 "{}".
        assertEquals(
            """{"priority_cells":["a","b","c"]}""",
            fillRequestBody(listOf("a", "b", "a", "c"))
        )
        assertEquals("""{"priority_cells":[]}""", fillRequestBody(emptyList()))
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
        // GEN-5 (RW-28 item 22): state unknown for a SUBSCRIBER
        // means the bank is still converging — the band renders
        // from the moment of subscription at fraction zero (the
        // bar is unlabelled; no count is invented). Only a read
        // proving the bank complete retires it.
        val unknown = ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, null, inTrial = false)
        assertEquals("Making your looks", unknown?.heading)
        assertEquals(0f, unknown?.fraction)
        // Empty engaged set: no band.
        assertNull(
            ConvergenceBandPolicy.content(
                MakeItMeMode.SUBSCRIBED,
                PersonalHeroService.BankCoverage(ready = 0, total = 0),
                inTrial = false
            )
        )
    }

    @Test
    fun `the FREE tier's own counters follow the shared allowance`() {
        // COUNT RULING scope (Sol, Nov 4) as re-pinned by RW-128
        // (Nov 7, 2026): the free counters stay VISIBLE (the
        // no-count treatment is the Premium band only) and now
        // read against the raised allowance of 10.
        assertEquals("2 of 10 looks", MakeItMeStrings.heroPillLookCount(2))
        assertEquals("0 of 10 looks", MakeItMeStrings.heroPillLookCount(0))
        assertEquals(
            "Your first 10 looks are free.",
            com.runwear.app.ui.screens.OnboardingCopy.FREE_LOOKS_PROMISE
        )
    }
}
