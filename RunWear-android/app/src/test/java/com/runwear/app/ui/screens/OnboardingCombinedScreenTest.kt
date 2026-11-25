package com.runwear.app.ui.screens

import com.runwear.shared.domain.model.ComfortPreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Sol's onboarding ruling (Nov 4): the three preference questions
 * (temperature unit, fit preference, body temperature) form ONE
 * COMBINED SCREEN with binding composition, wording, and options:
 * headline "Make it yours", the lede verbatim, temperature in full
 * words, the fit options per RW-81 (exactly two, neutral as the
 * unselected state), and the 5-stop body-temperature
 * slider with named ends. The substantive steps (location, fork,
 * consent, photos) stay one step per screen — the sequence model
 * keeps exactly one PREFERENCES step (count unchanged by the
 * ruling; the step's CONTENTS conform here).
 */
class OnboardingCombinedScreenTest {

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

    private fun screen(): String =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/OnboardingScreen.kt")
            .readText()

    private fun preferencesStep(): String {
        val s = screen()
        val start = s.indexOf("internal fun PreferencesStep")
        val end = s.indexOf("private fun LocationStep")
        assertTrue(start in 0 until end)
        return s.substring(start, end)
    }

    @Test
    fun `headline and lede are the ruled strings verbatim`() {
        assertEquals("Make it yours", OnboardingCopy.SETUP_TITLE)
        assertEquals(
            "Three quick calls. You can change any of them later in Settings.",
            OnboardingCopy.SETUP_SUB
        )
    }

    @Test
    fun `temperature options are full words`() {
        assertEquals("Fahrenheit", OnboardingCopy.TEMP_OPTION_FAHRENHEIT)
        assertEquals("Celsius", OnboardingCopy.TEMP_OPTION_CELSIUS)
        val step = preferencesStep()
        assertTrue(step.contains("OnboardingCopy.TEMP_OPTION_FAHRENHEIT"))
        assertTrue(step.contains("OnboardingCopy.TEMP_OPTION_CELSIUS"))
        assertFalse("symbol-only options are out", step.contains("\"°F\", \"°C\""))
    }

    @Test
    fun `fit carries exactly two options and neutral is the unselected state`() {
        // RW-81 re-pin (Sol's ruling, Nov 6, 2026; supersedes the
        // Nov 4 three-option pin): exactly two labelled options,
        // and NO third "No preference" / neither label anywhere.
        assertEquals("Men's", OnboardingCopy.FIT_OPTION_MENS)
        assertEquals("Women's", OnboardingCopy.FIT_OPTION_WOMENS)
        val step = preferencesStep()
        assertTrue(step.contains("OnboardingCopy.FIT_OPTION_MENS"))
        assertTrue(step.contains("OnboardingCopy.FIT_OPTION_WOMENS"))
        assertFalse(step.contains("FIT_OPTION_NO_PREFERENCE"))
        assertFalse(step.contains("No preference"))
        assertFalse(step.contains("Neither"))
        // A stored UNISEX (neutral) renders as NOTHING selected:
        // the segmented control's selectedIndex is -1, and a fresh
        // install starts there.
        assertTrue(step.contains("GenderPreference.UNISEX -> -1"))
        // The tap rule is the shared RW-81 toggle: tapping the
        // selected option deselects it back to UNISEX; tapping an
        // unselected option selects it.
        assertTrue(step.contains("fitPreferenceAfterTap"))
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.UNISEX,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.MALE,
                com.runwear.shared.domain.model.GenderPreference.MALE
            )
        )
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.UNISEX,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.FEMALE,
                com.runwear.shared.domain.model.GenderPreference.FEMALE
            )
        )
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.MALE,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.UNISEX,
                com.runwear.shared.domain.model.GenderPreference.MALE
            )
        )
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.FEMALE,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.MALE,
                com.runwear.shared.domain.model.GenderPreference.FEMALE
            )
        )
    }

    @Test
    fun `onboarding proceeds with no fit selection`() {
        // RW-81: neutral is a complete, proceedable state — the
        // step's Continue is wired straight to onContinue, with no
        // enabled-gate anywhere in the step (a fresh install starts
        // neutral and can proceed without touching the fit control).
        val step = preferencesStep()
        assertTrue(step.contains("onClick = onContinue"))
        assertFalse(step.contains("enabled"))
    }

    @Test
    fun `body temperature is a five stop slider with named ends`() {
        assertEquals("Runs Cold", OnboardingCopy.BODY_SLIDER_MIN_LABEL)
        assertEquals("Runs Warm", OnboardingCopy.BODY_SLIDER_MAX_LABEL)
        // Five stops, one per ComfortPreference value, in order.
        assertEquals(
            listOf(
                ComfortPreference.RUNS_VERY_COLD,
                ComfortPreference.RUNS_COLD,
                ComfortPreference.NEUTRAL,
                ComfortPreference.RUNS_WARM,
                ComfortPreference.RUNS_VERY_WARM
            ),
            ComfortStops.stops
        )
        // Round trip: every value maps to its own stop and back.
        ComfortStops.stops.forEachIndexed { index, preference ->
            assertEquals(index, ComfortStops.indexOf(preference))
            assertEquals(preference, ComfortStops.at(index))
        }
        // Out-of-range slider positions clamp to the end stops.
        assertEquals(ComfortPreference.RUNS_VERY_COLD, ComfortStops.at(-1))
        assertEquals(ComfortPreference.RUNS_VERY_WARM, ComfortStops.at(99))
        val step = preferencesStep()
        assertTrue(step.contains("Slider("))
        assertTrue(step.contains("ComfortStops.indexOf(comfortPreference)"))
        assertTrue(step.contains("ComfortStops.at("))
        assertTrue(step.contains("OnboardingCopy.BODY_SLIDER_MIN_LABEL"))
        assertTrue(step.contains("OnboardingCopy.BODY_SLIDER_MAX_LABEL"))
        assertFalse("the collapsed 3-option control is out", step.contains("\"Get cold\""))
    }

    @Test
    fun `one combined preferences step - substantive steps stay separate`() {
        assertEquals(
            "exactly one PREFERENCES step carries all three questions",
            1,
            OnboardingSequence.steps.count { it == OnboardingStep.PREFERENCES }
        )
        // Location, fork, consent, capture remain their own steps.
        for (step in listOf(
            OnboardingStep.LOCATION,
            OnboardingStep.FORK,
            OnboardingStep.CONSENT,
            OnboardingStep.CAPTURE
        )) {
            assertEquals(1, OnboardingSequence.steps.count { it == step })
        }
        // All three sections render inside the single step.
        val stepSource = preferencesStep()
        assertTrue(stepSource.contains("OnboardingCopy.TEMP_TITLE"))
        assertTrue(stepSource.contains("OnboardingCopy.FIT_TITLE"))
        assertTrue(stepSource.contains("OnboardingCopy.BODY_TITLE"))
    }
}
