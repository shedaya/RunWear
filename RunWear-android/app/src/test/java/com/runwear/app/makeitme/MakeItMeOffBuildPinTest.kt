package com.runwear.app.makeitme

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * PIN — the Make It Me OFF build (Sol's ruling, Nov 2, 2026: "off
 * for both submissions, til it actually works"; ships as
 * 1.2.12 / vc27).
 *
 * Every Make It Me entry point in the app consults
 * [MakeItMeAvailability]; this test pins that gate shut for this
 * build: the personal layer must not engage even for a fully
 * opted-in user, no purchase may start, no generation may be
 * requested, and the Settings section must not render. The
 * feature's own policy tests (cache, upsell, look status) are
 * untouched and keep asserting the ON behavior of the policies
 * themselves — the gate lives at the consumers, and this pin is
 * what proves the consumers cannot reach them in this build.
 *
 * If the feature is ever re-enabled, this test is supposed to
 * fail: flipping the gate is a deliberate act, never an accident.
 */
class MakeItMeOffBuildPinTest {

    @Test
    fun `the availability gate is off in this build`() {
        assertFalse(MakeItMeAvailability.ENABLED)
    }

    @Test
    fun `personal layer never engages, even for an opted-in user`() {
        assertFalse(MakeItMeAvailability.personalLayerEngages(personalizationOn = true))
        assertFalse(MakeItMeAvailability.personalLayerEngages(personalizationOn = false))
    }

    @Test
    fun `no purchase can start`() {
        assertFalse(MakeItMeAvailability.purchaseAllowed())
    }

    @Test
    fun `no generation can be requested`() {
        assertFalse(MakeItMeAvailability.generationRequestAllowed())
    }

    @Test
    fun `the Settings section does not render`() {
        assertFalse(MakeItMeAvailability.settingsSectionVisible())
    }
}
