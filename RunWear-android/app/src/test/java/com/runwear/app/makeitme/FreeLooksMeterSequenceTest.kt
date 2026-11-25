package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE A, ITEM 5 (DA-08) — the COUNTER-SEQUENCE test the
 * gen-5 gate lacked. The owner's recording showed Settings reading
 * "2 of 5 free looks used · 4 looks are being made" (sum 6 > 5) and
 * "4 of 5 used · 3 being made" (7 > 5): the delivered counter and
 * the repository's per-cell Making map double-count a look whose
 * delivery was learned through a counter refresh while its cell
 * status still said Making.
 *
 * This test drives REAL successive completions through the meter's
 * actual inputs — request → in-flight → complete, repeatedly to
 * exhaustion — in BOTH completion patterns (clean status settle,
 * and the stale-map pattern from the recording), asserting the
 * displayed used count and in-flight count at EVERY step and that
 * their sum never exceeds the allowance.
 *
 * RW-128 RE-PIN (Nov 7, 2026): the free allowance is 10 (was 5);
 * every bound and sequence below follows the shared allowance.
 */
class FreeLooksMeterSequenceTest {

    /** Mirrors the meter's two real inputs: the server delivered
     *  counter and the repository's per-cell status map. */
    private class MeterDriver {
        var deliveredUsed: Int = 0
            private set
        private val cellStatus = mutableMapOf<String, PersonalLookStatus>()

        fun request(cell: String) {
            cellStatus[cell] = PersonalLookStatus.Making
        }

        /** Delivery learned through the cell's own status read:
         *  counter increments AND the status settles. */
        fun completeClean(cell: String) {
            deliveredUsed++
            cellStatus[cell] = PersonalLookStatus.Ready
        }

        /** Delivery learned through a counter refresh (bank fill /
         *  another surface): the counter increments but the cell's
         *  status entry still says Making — the DA-08 pattern. */
        fun completeStale(cell: String) {
            deliveredUsed++
        }

        fun rawMaking(): Int =
            cellStatus.values.count { it == PersonalLookStatus.Making }

        fun line(): String = FreeLooksMeter.meterLine(deliveredUsed, rawMaking())
    }

    private fun parseDisplayed(line: String): Pair<Int, Int> {
        val used = when {
            line.startsWith("All 10 free looks used") -> 10
            else -> Regex("(\\d+) of 10 free looks used").find(line)
                ?.groupValues?.get(1)?.toInt()
                ?: error("no used count in '$line'")
        }
        val making = Regex("(\\d+) looks? (?:is|are) being made").find(line)
            ?.groupValues?.get(1)?.toInt() ?: 0
        return used to making
    }

    private fun assertStep(
        driver: MeterDriver,
        expectedUsed: Int,
        expectedMaking: Int,
        step: String
    ) {
        val line = driver.line()
        val (used, making) = parseDisplayed(line)
        assertEquals("[$step] displayed used in '$line'", expectedUsed, used)
        assertEquals("[$step] displayed making in '$line'", expectedMaking, making)
        assertTrue(
            "[$step] used + making = ${used + making} exceeds 10 in '$line'",
            used + making <= 10
        )
        // The shared seam agrees with the line it produced.
        assertEquals(expectedUsed, FreeLooksMeter.displayedUsed(driver.deliveredUsed))
        assertEquals(
            expectedMaking,
            FreeLooksMeter.displayedMaking(driver.deliveredUsed, driver.rawMaking())
        )
    }

    @Test
    fun `clean completions - request, in flight, complete, to exhaustion`() {
        val d = MeterDriver()
        assertStep(d, 0, 0, "start")
        for (k in 1..10) {
            val cell = "cell-$k"
            d.request(cell)
            assertStep(d, k - 1, 1, "request $k in flight")
            d.completeClean(cell)
            assertStep(d, k, 0, "completion $k")
        }
        assertEquals("All 10 free looks used", d.line())
    }

    @Test
    fun `stale map completions - the recording pattern never exceeds the allowance`() {
        val d = MeterDriver()
        // Ten looks requested; deliveries land via counter refresh
        // while every cell status still says Making.
        val cells = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        cells.forEach { d.request(it) }
        assertStep(d, 0, 10, "ten in flight")
        d.completeStale("a")
        // used 1 + raw making 10: the displayed making clamps so
        // the sum binds at the allowance, not each input.
        assertStep(d, 1, 9, "first stale delivery (raw making still 10)")
        d.completeStale("b")
        assertEquals(10, d.rawMaking())
        assertStep(d, 2, 8, "recording pattern 2 used + 10 raw making -> sum 10")
        d.completeStale("c")
        assertStep(d, 3, 7, "third stale delivery")
        // Remaining deliveries absorb the stale map one-for-one.
        listOf("d", "e", "f", "g", "h", "i").forEach { d.completeStale(it) }
        assertStep(d, 9, 1, "ninth stale delivery")
        d.completeStale("j")
        assertStep(d, 10, 0, "exhausted - stale making fully absorbed")
        assertEquals("All 10 free looks used", d.line())
        // The Settings block agrees: no "being made" tail at exhaustion.
        val block = FreeLooksMeter.block(d.deliveredUsed, d.rawMaking(), subscribed = false)
        assertEquals(MakeItMeStrings.S3_STATUS, block[0])
        assertEquals("All 10 free looks used", block[1])
    }

    @Test
    fun `paywall line clamps an over-allowance counter`() {
        assertEquals(
            MakeItMeStrings.u2MeterLine(10),
            FreeLooksMeter.paywallLine(12, lookMaking = false)
        )
        assertEquals(
            MakeItMeStrings.u2MeterLine(3),
            FreeLooksMeter.paywallLine(3, lookMaking = true)
        )
    }
}
