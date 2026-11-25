package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 WS-D SETTINGS ADDITION (Sol, Nov 4): while a PREMIUM user's
 * bank is still generating, the Settings Make It Me section shows
 * the SAME "Making your looks" element as the home band — ONE
 * shared element (ConvergenceBand + ConvergenceBandPolicy) rendered
 * in both places, never a re-implementation. Until Sol rules on
 * the count treatment it is the bar WITHOUT a numeric count (the
 * current hold), in both places at once.
 *
 * Pins: subscribed + bank-generating → the shared element renders
 * in Settings; subscribed + bank complete → it does not; free
 * users never see it in either place.
 */
class SettingsConvergenceBandTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "RunWear-android/", "app/")) {
                val file = File(dir, prefix + androidRootRelative)
                if (file.exists()) return file
            }
            dir = dir.parentFile
        }
        error("source file not found: $androidRootRelative")
    }

    private val generating = PersonalHeroService.BankCoverage(ready = 9, total = 48)
    private val complete = PersonalHeroService.BankCoverage(ready = 48, total = 48)

    @Test
    fun `subscribed and bank generating - the shared element resolves`() {
        val content = ConvergenceBandPolicy.content(
            MakeItMeMode.SUBSCRIBED, generating, inTrial = false
        )
        assertEquals("Making your looks", content?.heading)
        assertEquals(9f / 48f, content?.fraction)
    }

    @Test
    fun `subscribed and bank complete - no element`() {
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, complete, inTrial = false)
        )
    }

    @Test
    fun `free users never resolve it - in Settings or at home`() {
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.FREE, generating, inTrial = false)
        )
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.STOCK, generating, inTrial = false)
        )
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, generating, inTrial = true)
        )
    }

    @Test
    fun `Settings renders the SAME shared element from the SAME policy - not a re-implementation`() {
        val screens = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
        val section = screens.substringAfter("fun MakeItMeSettingsSection(")
            .substringBefore("\n/**\n * WS-5 (D-14): Delete-my-data as its OWN settings row")

        // GEN-5 fix: the slice now ends at the section's real extent
        // (the next top-level declaration) — the wave-B/C file layout
        // put other S10-carrying compositions between the section
        // and the old end-of-file delimiter.
        // Resolved through the shared policy from the section's own
        // state (the same inputs home uses).
        assertTrue(section.contains("ConvergenceBandPolicy.content("))
        assertTrue(section.contains("mode = state.mode"))
        assertTrue(section.contains("coverage = state.bankCoverage"))
        assertTrue(section.contains("inTrial = state.inTrial"))
        // Rendered with the shared composable.
        assertTrue(section.contains("ConvergenceBand(content = settingsBand)"))
        // GEN-5 (RW-28 item 22): the band occupies the STATUS SLOT —
        // it is resolved and rendered BEFORE the status block and
        // the offer card, and the offer card renders only when the
        // band does not (in place of, never beneath). The
        // destructive rows no longer live in this section at all
        // (item 25's Danger zone quarantine).
        val bandAt = section.indexOf("ConvergenceBand(content = settingsBand)")
        val cardAt = section.indexOf("premiumOfferCard(")
        assertTrue(bandAt >= 0 && cardAt > bandAt)
        assertTrue(section.contains("if (settingsBand == null)"))
        assertTrue(!section.contains("S10_DELETE_ROW"))

        // Home renders the identical composable (one element, two
        // seats — a future count ruling lands in both at once).
        val home = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        ).readText()
        assertTrue(home.contains("ConvergenceBand("))
        assertTrue(home.contains("ConvergenceBandPolicy.content("))
    }

    @Test
    fun `the FREE Settings counter is untouched by this addition`() {
        // The confirmed-great free counter family still composes
        // from the single FreeLooksMeter seam in the section.
        val screens = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
        val section = screens.substringAfter("fun MakeItMeSettingsSection(")
            .substringBefore("\n/**\n * WS-5 (D-14): Delete-my-data as its OWN settings row")
        assertTrue(section.contains("FreeLooksMeter.block("))
    }
}
