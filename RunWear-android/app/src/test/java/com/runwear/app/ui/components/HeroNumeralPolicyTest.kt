package com.runwear.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-2 A2 (W-7): the hero numeral's atomicity. The walkthrough
 * caught "44°" rendering through malformed partial glyphs after the
 * switch back to Kansas City. The policy's guarantee is structural:
 * the shown numeral is ALWAYS one of the two whole values — the held
 * (previously committed) one while a transition is in flight, the
 * candidate once it commits. No third, intermediate string exists.
 */
class HeroNumeralPolicyTest {

    @Test
    fun `during a transition the held numeral stays, whole`() {
        assertEquals(
            "58°",
            HeroNumeralPolicy.shown(held = "58°", candidate = "44°", transitionInFlight = true)
        )
    }

    @Test
    fun `at the commit the new numeral replaces it, whole`() {
        assertEquals(
            "44°",
            HeroNumeralPolicy.shown(held = "58°", candidate = "44°", transitionInFlight = false)
        )
    }

    @Test
    fun `the shown value is always exactly the held or the candidate string`() {
        val helds = listOf<String?>(null, "58°", "44°", "102°")
        val candidates = listOf<String?>(null, "58°", "44°", "7°")
        for (held in helds) {
            for (candidate in candidates) {
                for (inFlight in listOf(true, false)) {
                    val shown = HeroNumeralPolicy.shown(held, candidate, inFlight)
                    assertTrue(
                        "shown=$shown must be held=$held or candidate=$candidate",
                        shown == held || shown == candidate
                    )
                }
            }
        }
    }

    @Test
    fun `first load has nothing to hold and shows the candidate`() {
        assertEquals(
            "44°",
            HeroNumeralPolicy.shown(held = null, candidate = "44°", transitionInFlight = true)
        )
        assertNull(
            HeroNumeralPolicy.shown(held = null, candidate = null, transitionInFlight = false)
        )
    }

    @Test
    fun `a vanished candidate after the commit keeps the last whole numeral`() {
        assertEquals(
            "44°",
            HeroNumeralPolicy.shown(held = "44°", candidate = null, transitionInFlight = false)
        )
    }
}
