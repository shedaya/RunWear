package com.runwear.app.makeitme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PIN — the Make It Me ON release build (branch
 * release/rw27-flag-on-android-2026-11-03, 1.2.17 / vc32).
 *
 * This branch replaces the train's MakeItMeOffBuildPinTest,
 * which pins [MakeItMeAvailability] shut for store builds under
 * Sol's Nov 2, 2026 ruling ("off for both submissions, til it
 * actually works"). The server pipeline went live Nov 3, 2026,
 * the flag-on test build (1.2.16 / vc31) exercised it, and Sol
 * ruled Nov 3, 2026 that the RW-27 release builds ship with
 * Make It Me ON; this pin proves the consumers CAN reach the
 * feature in this build: the personal layer engages for an
 * opted-in user, a purchase may start, a generation may be
 * requested, and the Settings section renders. The opt-in
 * itself is still required — the personal layer must stay off
 * for a user who has not opted in.
 *
 * This pin is the deliberate-act tripwire in the other
 * direction: the gate flip and this pin travel together, and
 * any future build that changes the gate must revisit both.
 * The train's OFF pin is untouched on the train.
 */
class MakeItMeOnBuildPinTest {

    @Test
    fun `the availability gate is on in this build`() {
        assertTrue(MakeItMeAvailability.ENABLED)
    }

    @Test
    fun `personal layer engages for an opted-in user only`() {
        assertTrue(MakeItMeAvailability.personalLayerEngages(personalizationOn = true))
        assertFalse(MakeItMeAvailability.personalLayerEngages(personalizationOn = false))
    }

    @Test
    fun `a purchase can start`() {
        assertTrue(MakeItMeAvailability.purchaseAllowed())
    }

    @Test
    fun `a generation can be requested`() {
        assertTrue(MakeItMeAvailability.generationRequestAllowed())
    }

    @Test
    fun `the Settings section renders`() {
        assertTrue(MakeItMeAvailability.settingsSectionVisible())
    }
}
