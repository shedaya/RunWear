package com.runwear.app.makeitme

import com.runwear.shared.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * RW-151 client (Nov 10, 2026; DESIGN.md §5): the accompanied fill,
 * the fillable denominator, and the reconcile rule — including the
 * standing gate ("a fresh subscription converges opening nothing")
 * at the unit level: over a fixture forecast, every cell in the
 * coverage denominator is fillable (accompanied or canonical-bare).
 */
class Rw151ClientTest {

    private val day0: LocalDate = LocalDate.of(2026, 11, 10)

    private fun frames(): List<FillFrame> = listOf(
        FillFrame(day0, TimeOfDay.DAWN, "MALE_CLEAR_COOL_DAWN_726beba2",
            listOf("LONG_SLEEVE_LIGHT", "SHORTS")),
        FillFrame(day0, TimeOfDay.MIDDAY, "MALE_CLEAR_MILD_MIDDAY_11111111",
            listOf("SHORT_SLEEVE", "SHORTS")),
        // Same cluster as the first frame — not a representative.
        FillFrame(day0, TimeOfDay.DAWN, "MALE_CLEAR_COOL_DAWN_726beba2",
            listOf("LONG_SLEEVE_LIGHT", "SHORTS")),
        FillFrame(day0.plusDays(1), TimeOfDay.DUSK, "MALE_RAIN_COLD_DUSK_22222222",
            listOf("LONG_SLEEVE_LIGHT", "RAIN_JACKET", "LIGHT_TIGHTS"))
    )

    private val core = listOf(
        "MALE_CLEAR_COOL_DAWN_726beba2", // also a forecast cell
        "MALE_CLEAR_HOT_MIDDAY_33333333"
    )

    @Test
    fun `selectCells keeps cluster representatives and their items in forecast order`() {
        val cells = ForecastFillCells.selectCells(frames())
        assertEquals(
            listOf(
                "MALE_CLEAR_COOL_DAWN_726beba2",
                "MALE_CLEAR_MILD_MIDDAY_11111111",
                "MALE_RAIN_COLD_DUSK_22222222"
            ),
            cells.map { it.combinationId }
        )
        assertEquals(listOf("LONG_SLEEVE_LIGHT", "SHORTS"), cells[0].outfitItems)
    }

    @Test
    fun `composeWire emits accompanied forecast cells first and bare core cells behind, deduped by id`() {
        val wire = ForecastFillCells.composeWire(core, ForecastFillCells.selectCells(frames()))
        assertEquals(
            listOf(
                "MALE_CLEAR_COOL_DAWN_726beba2|LONG_SLEEVE_LIGHT,SHORTS",
                "MALE_CLEAR_MILD_MIDDAY_11111111|SHORT_SLEEVE,SHORTS",
                "MALE_RAIN_COLD_DUSK_22222222|LONG_SLEEVE_LIGHT,RAIN_JACKET,LIGHT_TIGHTS",
                "MALE_CLEAR_HOT_MIDDAY_33333333"
            ),
            wire
        )
        // The wire type is unchanged: plain strings in priority_cells.
        val body = fillRequestBody(wire)
        assertTrue(body.contains("\"MALE_CLEAR_COOL_DAWN_726beba2|LONG_SLEEVE_LIGHT,SHORTS\""))
        assertTrue(body.contains("\"MALE_CLEAR_HOT_MIDDAY_33333333\""))
    }

    @Test
    fun `standing gate - every cell in the denominator is fillable`() {
        val wire = ForecastFillCells.composeWire(core, ForecastFillCells.selectCells(frames()))
        val target = wire.map { ForecastFillCells.cellIdOf(it) }.toSet()
        val fillable = ForecastFillCells.fillableIds(wire, core)
        // Every target cell is fillable by construction.
        assertEquals(target, fillable)
        val coverage = deriveBankCoverage(
            heroCells = emptySet(), jobCells = emptySet(),
            targetCells = target, fillableCells = fillable
        )
        assertEquals(target.size, coverage.total)
        // And the denominator reaches zero as the fill banks —
        // opening nothing.
        val done = deriveBankCoverage(
            heroCells = target, jobCells = emptySet(),
            targetCells = target, fillableCells = fillable
        )
        assertEquals(0, done.outstanding)
    }

    @Test
    fun `a bare non-core unaccompanied cell is excluded from the denominator`() {
        val target = setOf("ACCOMPANIED", "CORE", "BARE_OFFTABLE")
        val fillable = ForecastFillCells.fillableIds(
            listOf("ACCOMPANIED|SHORT_SLEEVE,SHORTS", "CORE", "BARE_OFFTABLE"),
            listOf("CORE")
        )
        assertEquals(setOf("ACCOMPANIED", "CORE"), fillable)
        val coverage = deriveBankCoverage(
            heroCells = setOf("ACCOMPANIED", "CORE"),
            jobCells = emptySet(),
            targetCells = target,
            fillableCells = fillable
        )
        assertEquals(2, coverage.total)
        assertEquals(0, coverage.outstanding)
    }

    @Test
    fun `RW-126 failed-settle is preserved over the fillable denominator`() {
        val coverage = deriveBankCoverage(
            heroCells = setOf("A"),
            jobCells = setOf("A", "B"),
            targetCells = setOf("A", "B"),
            failedCells = setOf("B"),
            fillableCells = setOf("A", "B")
        )
        assertEquals(2, coverage.total)
        assertEquals(1, coverage.ready)
        assertEquals(1, coverage.failed)
        assertEquals(0, coverage.outstanding)
    }

    @Test
    fun `reconcile reissues only when outstanding with nothing in flight`() {
        assertTrue(FillReconcilePolicy.shouldReissue(3, hasNonTerminalJob = false, fillOutstanding = false))
        assertFalse(FillReconcilePolicy.shouldReissue(0, hasNonTerminalJob = false, fillOutstanding = false))
        assertFalse(FillReconcilePolicy.shouldReissue(3, hasNonTerminalJob = true, fillOutstanding = false))
        assertFalse(FillReconcilePolicy.shouldReissue(3, hasNonTerminalJob = false, fillOutstanding = true))
    }
}
