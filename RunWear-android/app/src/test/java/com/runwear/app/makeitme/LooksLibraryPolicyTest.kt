package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE C (RW-61): the "Your looks" library policy — cell
 * labels derive from the self-describing combination id
 * (frame 21's "Snow · Freezing" / "Night · stock photo for now"),
 * and a gate-failed cell is a FAILED job with no banked hero.
 */
class LooksLibraryPolicyTest {

    @Test
    fun `combination id parses into the settled cell labels`() {
        val parsed = LooksLibraryPolicy.parse("UNISEX_SNOW_FREEZING_NIGHT_a1b2c3d4")
        assertEquals(Triple("Snow", "Freezing", "Night"), parsed)
    }

    @Test
    fun `malformed ids do not parse`() {
        assertNull(LooksLibraryPolicy.parse("garbage"))
        assertNull(LooksLibraryPolicy.parse("UNISEX_HAIL_FREEZING_NIGHT_a1b2c3d4"))
    }

    @Test
    fun `failed cell carries the settled stock-photo-for-now sub-label`() {
        val cells = LooksLibraryPolicy.build(
            heroCells = emptySet(),
            jobStatuses = mapOf("UNISEX_SNOW_FREEZING_NIGHT_a1b2c3d4" to "FAILED")
        )
        assertEquals(1, cells.size)
        assertEquals("Snow · Freezing", cells[0].title)
        assertEquals("Night · stock photo for now", cells[0].subLabel)
        assertEquals(LooksLibraryPolicy.CellState.FAILED, cells[0].state)
    }

    @Test
    fun `banked hero outranks a failed job - the cell is ready`() {
        val id = "UNISEX_RAIN_COLD_DAWN_00ff00ff"
        val cells = LooksLibraryPolicy.build(setOf(id), mapOf(id to "FAILED"))
        assertEquals(LooksLibraryPolicy.CellState.READY, cells[0].state)
        assertEquals("Dawn", cells[0].subLabel)
    }

    @Test
    fun `queued job without a hero is making, and failed cells sort first`() {
        val cells = LooksLibraryPolicy.build(
            heroCells = setOf("UNISEX_CLEAR_WARM_MIDDAY_11111111"),
            jobStatuses = mapOf(
                "UNISEX_CLEAR_WARM_MIDDAY_11111111" to "COMPLETED",
                "UNISEX_RAIN_HOT_DUSK_22222222" to "QUEUED",
                "UNISEX_SNOW_FREEZING_NIGHT_33333333" to "FAILED"
            )
        )
        assertEquals(
            listOf(
                LooksLibraryPolicy.CellState.FAILED,
                LooksLibraryPolicy.CellState.MAKING,
                LooksLibraryPolicy.CellState.READY
            ),
            cells.map { it.state }
        )
    }

    @Test
    fun `settled library copy is verbatim`() {
        assertEquals("Your looks", LooksLibraryStrings.TITLE)
        assertEquals("Why am I seeing a stock photo?", LooksLibraryStrings.WHY_DOOR)
        assertEquals(
            "One scenario didn't pass the quality gate after every attempt, " +
                "so it shows the stock photo rather than a version of you " +
                "that isn't right. It retries on its own when generation improves.",
            LooksLibraryStrings.EXPLANATION
        )
        assertTrue(LooksLibraryStrings.EXPLANATION.contains("quality gate"))
    }
}
