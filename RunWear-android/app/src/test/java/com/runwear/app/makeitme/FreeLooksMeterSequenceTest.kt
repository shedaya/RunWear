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
 * their sum never exceeds the allowance of 5.
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
            line.startsWith("All 5 free looks used") -> 5
            else -> Regex("(\\d+) of 5 free looks used").find(line)
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
            "[$step] used + making = ${used + making} exceeds 5 in '$line'",
            used + making <= 5
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
        for (k in 1..5) {
            val cell = "cell-$k"
            d.request(cell)
            assertStep(d, k - 1, 1, "request $k in flight")
            d.completeClean(cell)
            assertStep(d, k, 0, "completion $k")
        }
        assertEquals("All 5 free looks used", d.line())
    }

    @Test
    fun `stale map completions - the recording pattern never exceeds five`() {
        val d = MeterDriver()
        // Four looks requested; deliveries land via counter refresh
        // while every cell status still says Making.
        listOf("a", "b", "c", "d").forEach { d.request(it) }
        assertStep(d, 0, 4, "four in flight")
        d.completeStale("a")
        assertStep(d, 1, 3, "first stale delivery (raw making still 4)")
        d.completeStale("b")
        // The recording's exact state: used 2, raw making 4.
        assertEquals(4, d.rawMaking())
        assertStep(d, 2, 3, "recording state 2 used + 4 raw making -> sum 5, not 6")
        d.completeStale("c")
        assertStep(d, 3, 2, "third stale delivery")
        d.completeStale("d")
        // The recording's second state: used 4, raw making 4.
        assertStep(d, 4, 1, "recording state 4 used + 4 raw making -> sum 5, not 7")
        d.request("e")
        d.completeStale("e")
        assertStep(d, 5, 0, "exhausted - stale making fully absorbed")
        assertEquals("All 5 free looks used", d.line())
        // The Settings block agrees: no "being made" tail at exhaustion.
        val block = FreeLooksMeter.block(d.deliveredUsed, d.rawMaking(), subscribed = false)
        assertEquals(MakeItMeStrings.S3_STATUS, block[0])
        assertEquals("All 5 free looks used", block[1])
    }

    @Test
    fun `paywall line clamps an over-allowance counter`() {
        assertEquals(
            MakeItMeStrings.u2MeterLine(5),
            FreeLooksMeter.paywallLine(7, lookMaking = false)
        )
        assertEquals(
            MakeItMeStrings.u2MeterLine(3),
            FreeLooksMeter.paywallLine(3, lookMaking = true)
        )
    }
}
