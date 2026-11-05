package com.runwear.app.makeitme

import com.runwear.shared.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * GEN-5 (RW-28 Addendum 8, Defects 1 + 2): the convergence
 * sequence the coordinator's gate checks — grant → the fill is
 * requested for the forecast cells → the bank converges against
 * the SAME set — exercised at the policy level.
 */
class ForecastFillTest {

    private val day0: LocalDate = LocalDate.of(2026, 11, 5)

    /** A 2-day forecast's frames, in forecast order. */
    private fun frames(): List<FillFrame> {
        val out = mutableListOf<FillFrame>()
        fun add(day: Long, daypart: TimeOfDay, cell: String) {
            out += FillFrame(day0.plusDays(day), daypart, cell)
        }
        // Day 0: early-night cluster, dawn, midday, dusk, late-night.
        add(0, TimeOfDay.NIGHT, "COOL_NIGHT")
        add(0, TimeOfDay.DAWN, "COOL_DAWN")
        add(0, TimeOfDay.MIDDAY, "MILD_MIDDAY")
        add(0, TimeOfDay.DUSK, "COOL_DUSK")
        add(0, TimeOfDay.NIGHT, "COLD_NIGHT")
        // Day 1: dawn prescribes day 0's dusk cell (dedupe target).
        add(1, TimeOfDay.DAWN, "COOL_DUSK")
        add(1, TimeOfDay.MIDDAY, "WARM_MIDDAY")
        add(1, TimeOfDay.DUSK, "WARM_DUSK")
        add(1, TimeOfDay.NIGHT, "WARM_NIGHT")
        return out
    }

    @Test
    fun `select names one cell per day-daypart cluster in forecast order`() {
        assertEquals(
            listOf(
                "COOL_NIGHT", "COOL_DAWN", "MILD_MIDDAY", "COOL_DUSK",
                "COLD_NIGHT", "WARM_MIDDAY", "WARM_DUSK", "WARM_NIGHT"
            ),
            ForecastFillCells.select(frames())
        )
    }

    @Test
    fun `grant to fill to converged - the bank fills without opening cells one by one`() {
        // 1. The grant's fill request names the forecast cells.
        val named = ForecastFillCells.select(frames())
        val body = fillRequestBody(named)
        assertEquals(
            """{"priority_cells":["COOL_NIGHT","COOL_DAWN","MILD_MIDDAY","COOL_DUSK","COLD_NIGHT","WARM_MIDDAY","WARM_DUSK","WARM_NIGHT"]}""",
            body
        )
        // 2. Coverage is measured against the named set from the
        // start: nothing banked, band up at an honest zero.
        val target = named.toSet()
        var coverage = deriveBankCoverage(emptySet(), emptySet(), target)
        assertEquals(0, coverage.ready)
        assertEquals(8, coverage.total)
        assertNotNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
        )
        // 3. As the worker banks the named cells (none of them
        // opened by hand), the fraction climbs against the SAME
        // denominator — no engaged-set inflation at any step.
        for (banked in 1..7) {
            coverage = deriveBankCoverage(named.take(banked).toSet(), emptySet(), target)
            assertEquals(banked, coverage.ready)
            assertEquals(8, coverage.total)
            assertNotNull(
                ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
            )
        }
        // 4. The last named cell lands: the bank has converged and
        // the band retires.
        coverage = deriveBankCoverage(target, emptySet(), target)
        assertEquals(8, coverage.ready)
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
        )
    }
}
