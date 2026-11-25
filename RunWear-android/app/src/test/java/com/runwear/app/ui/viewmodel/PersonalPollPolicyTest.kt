package com.runwear.app.ui.viewmodel

import com.runwear.app.makeitme.PersonalLookStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE A, ITEM 1 (DA-02) — the MAKING poll horizon. Gen-5
 * stopped after 18 × 15 s ≈ 4.5 minutes and stranded the MAKING
 * pill; the schedule must cover the guaranteed delivery window
 * with a wide margin (≥ 15 minutes), back off, and stop ONLY at a
 * terminal state or the horizon.
 */
class PersonalPollPolicyTest {

    @Test
    fun `horizon is at least fifteen minutes`() {
        assertTrue(
            "horizon ${PersonalPollPolicy.HORIZON_MS}ms must be >= 15 min",
            PersonalPollPolicy.HORIZON_MS >= 15 * 60 * 1000L
        )
    }

    @Test
    fun `schedule backs off and never exceeds the horizon`() {
        var previous = 0L
        var elapsed = 0L
        var attempt = 0
        while (true) {
            val delay = PersonalPollPolicy.delayBeforeAttemptMs(attempt)
            assertTrue("delays must be non-decreasing (backoff)", delay >= previous)
            previous = delay
            if (elapsed + delay > PersonalPollPolicy.HORIZON_MS) break
            elapsed += delay
            attempt++
        }
        assertEquals(attempt, PersonalPollPolicy.maxAttempts())
        assertEquals(elapsed, PersonalPollPolicy.elapsedAfterAttempts(attempt))
        // The full schedule consumes (nearly) the whole horizon —
        // the poll does not give up early the way the 4.5-minute
        // gen-5 bound did.
        assertTrue(
            "schedule covers $elapsed of ${PersonalPollPolicy.HORIZON_MS}ms",
            elapsed > PersonalPollPolicy.HORIZON_MS - 60_000L
        )
        assertTrue("more attempts than the gen-5 bound of 18", attempt > 18)
    }

    @Test
    fun `fresh polls are fifteen seconds apart`() {
        assertEquals(15_000L, PersonalPollPolicy.delayBeforeAttemptMs(0))
        assertEquals(15_000L, PersonalPollPolicy.delayBeforeAttemptMs(3))
        assertEquals(30_000L, PersonalPollPolicy.delayBeforeAttemptMs(4))
        assertEquals(60_000L, PersonalPollPolicy.delayBeforeAttemptMs(8))
        assertEquals(60_000L, PersonalPollPolicy.delayBeforeAttemptMs(40))
    }

    @Test
    fun `only Making keeps polling - terminal states stop immediately`() {
        assertFalse(PersonalPollPolicy.shouldPollAgain(PersonalLookStatus.Ready, 0))
        assertFalse(
            PersonalPollPolicy.shouldPollAgain(
                PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR), 0
            )
        )
        assertFalse(PersonalPollPolicy.shouldPollAgain(PersonalLookStatus.None, 0))
        assertTrue(PersonalPollPolicy.shouldPollAgain(PersonalLookStatus.Making, 0))
    }

    @Test
    fun `a Making look polls until the horizon and no further`() {
        var attempts = 0
        while (PersonalPollPolicy.shouldPollAgain(PersonalLookStatus.Making, attempts)) {
            attempts++
            assertTrue(attempts < 10_000) // termination guard
        }
        assertEquals(PersonalPollPolicy.maxAttempts(), attempts)
        assertTrue(PersonalPollPolicy.elapsedAfterAttempts(attempts) >= 15 * 60 * 1000L)
    }
}
