package com.runwear.app.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * GEN-2 lane A4 (Nov 4 walkthrough) — W-21 and W-22, Android half.
 *
 * W-21: Sol struck the welcome footer "Free to start · Set up in
 * under a minute" — the app is free forever; premium features are
 * the upsell. The replacement is verbatim, no variants:
 * "Free forever · Premium is optional".
 *
 * W-22: Sol's ruling — a preference choice that is the only content
 * on its screen must be presented directly, not as a collapsed
 * dropdown — was an iOS rendering (its Fit Preference step stands
 * alone). Android's onboarding presents Fit Preference INSIDE the
 * grouped Step 1 of 2, alongside Temperature and Body temperature,
 * as a TokenSegmentedControl whose options ("Male" / "Female",
 * neither-selected = no preference) are directly visible and
 * tappable in place. No dropdown or menu exists anywhere in
 * Android's onboarding screen. These pins record that as-found
 * state so it cannot silently regress into a collapsed control.
 */
class Gen2OnboardingFixTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun onboardingScreen(): String =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/OnboardingScreen.kt")
            .readText()

    // ── W-21 ────────────────────────────────────────────────────

    @Test
    fun `W-21 - the welcome footer is Sol's replacement, verbatim`() {
        assertEquals(
            "Free forever · Premium is optional",
            OnboardingCopy.PROMISE_FOOTER
        )
    }

    @Test
    fun `W-21 - the struck footer appears nowhere in the Android onboarding sources`() {
        val flow = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/OnboardingFlow.kt"
        ).readText()
        assertFalse(flow.contains("Free to start"))
        assertFalse(onboardingScreen().contains("Free to start"))
        // And the footer slot on the promise step still renders the
        // pinned copy constant (the pin is on the rendered slot).
        assertTrue(onboardingScreen().contains("OnboardingCopy.PROMISE_FOOTER"))
    }

    // ── W-22 (as found: already direct — no change) ─────────────

    @Test
    fun `W-22 - Android onboarding has no collapsed dropdown or menu anywhere`() {
        val screen = onboardingScreen()
        assertFalse(screen.contains("DropdownMenu"))
        assertFalse(screen.contains("ExposedDropdownMenuBox"))
    }

    @Test
    fun `W-22 - Fit Preference is a directly visible segmented control in the grouped step`() {
        val screen = onboardingScreen()
        // The Fit section exists in the preferences step…
        val fitStart = screen.indexOf("OnboardingCopy.FIT_TITLE")
        assertTrue("Fit Preference section must exist", fitStart >= 0)
        val fitBlock = screen.substring(fitStart, fitStart + 1200)
        // …and its choices render in place as a segmented control
        // with both options spelled out, not behind any collapsed
        // control.
        assertTrue(fitBlock.contains("TokenSegmentedControl"))
        assertTrue(fitBlock.contains("\"Male\", \"Female\""))
        // The step is the GROUPED step: Temperature and Body
        // temperature sections share it (the iOS one-question-per-
        // screen structure that motivated W-22 is not Android's).
        val stepStart = screen.indexOf("private fun PreferencesStep")
        val stepEnd = screen.indexOf("private fun LocationStep")
        assertTrue(stepStart in 0 until stepEnd)
        val step = screen.substring(stepStart, stepEnd)
        assertTrue(step.contains("OnboardingCopy.TEMP_TITLE"))
        assertTrue(step.contains("OnboardingCopy.FIT_TITLE"))
        assertTrue(step.contains("OnboardingCopy.BODY_TITLE"))
    }
}
