package com.runwear.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbSunny
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.components.conditionIcon
import com.runwear.shared.domain.model.WeatherCode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-10 (A1, A2, A4-residue; T-2/R-2 rulings) — the pins for the
 * addendum lane:
 *
 *  - the onboarding step ORDER is the design's (§9): promise →
 *    preferences → location → fork → consent → capture → first
 *    look, with the preference profile built first;
 *  - A1: the free-looks promise exists ONLY on frame 28's own
 *    screen — value-level (it is a promise-step string) and
 *    source-level (the setup step never references it, and the
 *    retired header placement is gone);
 *  - A2: the headline is Sol's pick, character for character;
 *  - consent is reachable from every step that precedes it and
 *    gates capture structurally; consent accept is silent, and
 *    the first-look reveal carries the file's ONE haptic;
 *  - §1.4 motion constants: shared-axis 300 ms step transitions,
 *    the 400 ms photo crossfade (§1.4 spec of record — coordinator
 *    ruling 2026-11-03), the success haptic as the photo lands,
 *    the band at +120 ms after it;
 *  - the fork (frame 32) carries NO free-looks promise sentence —
 *    it lives in frame 28's promise row only (coordinator ruling
 *    2026-11-03 reversing the earlier keep; frame 34's count line
 *    is design-mandated and stays);
 *  - A4: no shipped surface renders the WeatherCode emoji or the
 *    location picker's "✓" text glyph; the designed vector
 *    iconography stands in both places, for every condition.
 */
class OnboardingSequenceTest {

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

    private fun onboardingScreen() =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/OnboardingScreen.kt").readText()

    private fun makeItMeScreens() =
        sourceFile("app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt").readText()

    private fun occurrences(source: String, needle: String): Int =
        source.split(needle).size - 1

    /** The source region from [startMarker] to [endMarker] (exclusive). */
    private fun region(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("marker not found: $startMarker", start >= 0)
        val end = source.indexOf(endMarker, start)
        assertTrue("marker not found: $endMarker", end > start)
        return source.substring(start, end)
    }

    // ── The sequence ────────────────────────────────────────────

    @Test
    fun `the step order is the design sequence - promise, setup, fork, consent, capture, first look`() {
        assertEquals(
            listOf(
                OnboardingStep.PROMISE,
                OnboardingStep.PREFERENCES,
                OnboardingStep.LOCATION,
                OnboardingStep.FORK,
                OnboardingStep.CONSENT,
                OnboardingStep.CAPTURE,
                OnboardingStep.FIRST_LOOK
            ),
            OnboardingSequence.steps
        )
        // The chain of primary edges walks the same order end to end.
        val walked = mutableListOf(OnboardingStep.PROMISE)
        while (true) {
            walked += OnboardingSequence.primaryNext(walked.last()) ?: break
        }
        assertEquals(OnboardingSequence.steps, walked)
    }

    @Test
    fun `the in-screen steps build the preference profile first - promise, preferences, location`() {
        assertEquals(
            listOf(
                OnboardingStep.PROMISE,
                OnboardingStep.PREFERENCES,
                OnboardingStep.LOCATION
            ),
            OnboardingSequence.inScreenSteps
        )
        // Back is the exact reverse (§1.4).
        assertEquals(
            OnboardingStep.PREFERENCES,
            OnboardingSequence.inScreenPrevious(OnboardingStep.LOCATION)
        )
        assertEquals(
            OnboardingStep.PROMISE,
            OnboardingSequence.inScreenPrevious(OnboardingStep.PREFERENCES)
        )
        assertEquals(null, OnboardingSequence.inScreenPrevious(OnboardingStep.PROMISE))
        // The screen's state machine starts on the promise step and
        // steps Back through the same model.
        val screen = onboardingScreen()
        assertTrue(screen.contains("mutableStateOf(OnboardingStep.PROMISE)"))
        assertTrue(screen.contains("OnboardingSequence.inScreenPrevious(step)"))
        assertTrue(screen.contains("BackHandler(enabled = step != OnboardingStep.PROMISE)"))
    }

    // ── A1 — the promise's placement ────────────────────────────

    @Test
    fun `A1 - the free-looks promise sentence stays defined, superseded on the welcome by round 2`() {
        // GEN-3 ROUND 2 + WELCOME UPDATE (Sol, Nov 4): point 2's
        // final copy is "Picture yourself at the start line with
        // the perfect outfit ready to go." — the free-looks
        // sentence no longer rides the welcome point. The
        // canonical sentence itself stays defined verbatim.
        // RW-128 re-pin (Nov 7, 2026): free allowance 5 → 10.
        assertEquals("Your first 10 looks are free.", OnboardingCopy.FREE_LOOKS_PROMISE)
        assertEquals(
            "Picture yourself at the start line with the perfect outfit ready to go.",
            OnboardingCopy.PROMISE_2_PHRASE
        )
    }

    @Test
    fun `A1 - the promise is absent from the setup form and present on its own screen`() {
        val screen = onboardingScreen()
        // The literal sentence never appears in the screen source at
        // all — it reaches the UI only through OnboardingCopy, and
        // only the promise step references the row that carries it.
        assertEquals(
            "the promise sentence must not be placed anywhere in OnboardingScreen",
            0,
            occurrences(screen, OnboardingCopy.FREE_LOOKS_PROMISE)
        )
        val promiseStep = region(screen, "private fun PromiseStep", "private fun PromiseRow")
        assertTrue(promiseStep.contains("OnboardingCopy.WELCOME_POINTS"))
        val setupStep = region(screen, "internal fun PreferencesStep", "private fun LocationStep")
        assertFalse(setupStep.contains("WELCOME_POINTS"))
        assertFalse(setupStep.contains("FREE_LOOKS_PROMISE"))
        assertFalse(setupStep.contains("Make It Me"))
        // The retired A1 placement — a header above the setup form —
        // and the pre-design copy are gone entirely.
        for (retired in listOf(
            "Let's prepare your perfect run",
            "\"Welcome!\"",
            "Let's Go!",
            "Set Your Location",
            "Use Current Location"
        )) {
            assertFalse("retired onboarding copy must not return: $retired", screen.contains(retired))
        }
    }

    @Test
    fun `fork - the free-looks sentence is removed from frame 32, promise row only`() {
        // Coordinator ruling 2026-11-03 (reversing the earlier
        // keep): the frozen final design package carries the
        // promise sentence on frame 28's promise row (and a paywall
        // proof row) — frame 32's fork carries none, and neither
        // does iOS. The fork body is the design/iOS string verbatim,
        // ending at "looking at."
        assertEquals(
            "Upload 1 to 3 photos of yourself, and RunWear makes your outfit photos from them — starting with the one you're looking at.",
            MakeItMeStrings.F4_OPTION_A_BODY
        )
        assertFalse(
            MakeItMeStrings.F4_OPTION_A_BODY.contains(OnboardingCopy.FREE_LOOKS_PROMISE)
        )
        // No string in the Make It Me deck carries the promise
        // sentence at all — its only onboarding home is frame 28's
        // promise row (OnboardingCopy, pinned above).
        val deck = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeStrings.kt"
        ).readText()
        assertEquals(
            "the promise sentence must not appear in the Make It Me deck",
            0,
            occurrences(deck, OnboardingCopy.FREE_LOOKS_PROMISE)
        )
        // Frame 34's accounting line is design-mandated and stays,
        // character for character.
        // RW-128 re-pin (Nov 7, 2026): the frame-34 accounting
        // line follows the shared allowance of 10.
        assertEquals(
            "That was look 1 of your 10 free looks — 9 remain, whenever you want them.",
            MakeItMeStrings.FIRST_LOOK_COUNT_LINE
        )
    }

    @Test
    fun `fork - the lead is frame 32's sub-headline verbatim`() {
        // WS-9 fix, item 6 (coordinator ruling 2026-11-03): the
        // RW-14 deck lead paragraph diverged from frame 32 — the
        // frozen design package carries the sub-headline in the
        // lead slot, and the iOS WS-9 fix lane installed it
        // verbatim. Android now matches, character for character;
        // the retired deck lead must not return anywhere in the deck.
        assertEquals(
            "Your recommendations, with you in the photo.",
            MakeItMeStrings.F2_LEAD
        )
        val deck = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeStrings.kt"
        ).readText()
        assertEquals(
            "the retired RW-14 deck lead must not appear in the deck",
            0,
            occurrences(deck, "That's a model in your outfit photo")
        )
        // And the fork surface renders the deck lead (the pin is on
        // the rendered slot, not just the string's existence).
        assertTrue(makeItMeScreens().contains("MakeItMeStrings.F2_LEAD"))
    }

    @Test
    fun `no price string appears anywhere in onboarding copy - the section-9 rule`() {
        val pricePattern = Regex("\\$\\s?\\d")
        val copy = listOf(
            OnboardingCopy.HEADLINE,
            OnboardingCopy.PROMISE_1_NAME, OnboardingCopy.PROMISE_1_PHRASE,
            OnboardingCopy.PROMISE_2_NAME, OnboardingCopy.PROMISE_2_PHRASE,
            OnboardingCopy.PROMISE_3_NAME, OnboardingCopy.PROMISE_3_PHRASE,
            OnboardingCopy.PROMISE_CTA, OnboardingCopy.PROMISE_FOOTER,
            OnboardingCopy.SETUP_TITLE, OnboardingCopy.SETUP_SUB,
            OnboardingCopy.TEMP_TITLE, OnboardingCopy.TEMP_SUB,
            OnboardingCopy.FIT_TITLE, OnboardingCopy.FIT_SUB,
            OnboardingCopy.BODY_TITLE, OnboardingCopy.BODY_SUB,
            OnboardingCopy.SETUP_CTA,
            OnboardingCopy.LOCATION_TITLE, OnboardingCopy.LOCATION_BODY,
            OnboardingCopy.LOCATION_CTA_PRIMARY, OnboardingCopy.LOCATION_CTA_SECONDARY,
            MakeItMeStrings.FIRST_LOOK_BAND_READINESS,
            MakeItMeStrings.FIRST_LOOK_BAND_NO_CAP,
            MakeItMeStrings.FIRST_LOOK_COUNT_LINE,
            MakeItMeStrings.FIRST_LOOK_HEADLINE
        )
        for (line in copy) {
            assertFalse("price string in onboarding copy: $line", pricePattern.containsMatchIn(line))
        }
    }

    // ── A2 — the headline ───────────────────────────────────────

    @Test
    fun `A2 - the welcome headline is Sol's pick, character for character`() {
        assertEquals("Run, weather or not.", OnboardingCopy.HEADLINE)
        // …and it is the promise step's headline slot that renders it.
        val promiseStep = region(
            onboardingScreen(), "private fun PromiseStep", "private fun PromiseRow"
        )
        assertTrue(promiseStep.contains("OnboardingCopy.HEADLINE"))
    }

    // ── Consent — reachability and the gate ─────────────────────

    @Test
    fun `consent is reachable from every step that precedes it`() {
        for (step in OnboardingSequence.steps) {
            if (step.ordinal < OnboardingStep.CONSENT.ordinal) {
                assertTrue(
                    "consent must be reachable from $step",
                    OnboardingSequence.consentReachableFrom(step)
                )
            }
        }
        // One primary gesture from the fork (the choice step) lands
        // on consent — the §1.4 fork → consent → capture chain.
        assertEquals(
            OnboardingStep.CONSENT,
            OnboardingSequence.primaryNext(OnboardingStep.FORK)
        )
        assertEquals(
            OnboardingStep.CAPTURE,
            OnboardingSequence.primaryNext(OnboardingStep.CONSENT)
        )
    }

    @Test
    fun `capture and the first look sit strictly after consent - no edge skips it`() {
        val steps = OnboardingSequence.steps
        assertTrue(steps.indexOf(OnboardingStep.CONSENT) < steps.indexOf(OnboardingStep.CAPTURE))
        assertTrue(steps.indexOf(OnboardingStep.CAPTURE) < steps.indexOf(OnboardingStep.FIRST_LOOK))
        // The only inbound primary edge to CAPTURE is consent's.
        for (step in steps) {
            if (OnboardingSequence.primaryNext(step) == OnboardingStep.CAPTURE) {
                assertEquals(OnboardingStep.CONSENT, step)
            }
        }
        // The hosted flow honors the same gate: consent is its first
        // step, Continue is attestation-gated, and no photo UI
        // composes before it.
        val mim = makeItMeScreens()
        assertTrue(mim.contains("mutableStateOf(UploadStep.CONSENT)"))
        val flow = region(mim, "fun MakeItMeUploadFlow(", "private fun PickedPhotoRow")
        val consentBranch = region(
            flow, "UploadStep.CONSENT ->", "UploadStep.PHOTOS, UploadStep.SUBMITTING ->"
        )
        assertTrue(consentBranch.contains("enabled = attested && !working"))
        assertTrue(consentBranch.contains("MakeItMeStrings.C1C_ATTESTATION"))
    }

    @Test
    fun `consent accept is silent - the upload flow carries no haptic at all`() {
        val flow = region(
            makeItMeScreens(), "fun MakeItMeUploadFlow(", "private fun PickedPhotoRow"
        )
        assertFalse(
            "no haptic may fire anywhere in the consent/capture flow (§1.7)",
            flow.contains("performHapticFeedback")
        )
    }

    // ── §1.4 motion + the first-look reveal ─────────────────────

    @Test
    fun `motion constants - shared-axis steps, 400ms crossfade, haptic at landing, band at plus 120ms`() {
        assertEquals(300, OnboardingMotion.STEP_TRANSITION_MS)
        assertEquals(400, OnboardingMotion.FIRST_LOOK_PHOTO_CROSSFADE_MS)
        assertEquals(120, OnboardingMotion.FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS)
        assertEquals(520, OnboardingMotion.FIRST_LOOK_BAND_AT_MS)
        // The ONE success haptic fires as the photo lands — at
        // crossfade completion, NOT with the band (§1.4/§1.7).
        assertEquals(400, OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS)
        assertEquals(
            OnboardingMotion.FIRST_LOOK_PHOTO_CROSSFADE_MS,
            OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS
        )
        // Both step hosts animate with the shared spec.
        assertTrue(onboardingScreen().contains("AnimatedContent("))
        assertTrue(onboardingScreen().contains("OnboardingMotion.STEP_TRANSITION_MS"))
        val flow = region(
            makeItMeScreens(), "fun MakeItMeUploadFlow(", "private fun PickedPhotoRow"
        )
        assertTrue(flow.contains("AnimatedContent("))
        assertTrue(flow.contains("OnboardingMotion.STEP_TRANSITION_MS"))
    }

    @Test
    fun `the first-look reveal - crossfade, band, ONE success haptic, no dialog`() {
        val mim = makeItMeScreens()
        val moment = region(mim, "fun FirstLookMoment(", "fun MakeItMeDeleteDataDialog(")
        assertTrue(moment.contains("OnboardingMotion.FIRST_LOOK_PHOTO_CROSSFADE_MS"))
        assertTrue(moment.contains("OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS"))
        assertTrue(moment.contains("OnboardingMotion.FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS"))
        // The reveal's order is pinned in source: the haptic delay
        // (photo landing) precedes the haptic call, which precedes
        // the band settling (+120 ms later).
        val hapticDelayAt = moment.indexOf("OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS")
        val hapticCallAt = moment.indexOf("performHapticFeedback")
        val bandSettleAt = moment.lastIndexOf("bandSettled = true")
        assertTrue(hapticDelayAt in 0 until hapticCallAt)
        assertTrue(hapticCallAt in 0 until bandSettleAt)
        assertTrue(moment.contains("MakeItMeStrings.FIRST_LOOK_BAND_READINESS"))
        // GEN-5 (FA-6): the moment composes its count line from
        // the shared meter via firstLookCountLine.
        assertTrue(moment.contains("MakeItMeStrings.firstLookCountLine("))
        assertFalse(
            "frame 34 is a full-screen moment, not an AlertDialog",
            moment.contains("AlertDialog")
        )
        // Exactly ONE haptic in the whole Make It Me screens file:
        // the first-look success (§1.7). Everything else is silent.
        assertEquals(1, occurrences(mim, "performHapticFeedback"))
        assertTrue(
            moment.contains("performHapticFeedback")
        )
        // Frame 34's heading pattern, with and without a place.
        assertEquals(
            "Your first look · Today, New York",
            MakeItMeStrings.firstLookHeading("New York")
        )
        assertEquals("Your first look · Today", MakeItMeStrings.firstLookHeading(null))
    }

    @Test
    fun `preference selections carry the selection haptic`() {
        val screen = onboardingScreen()
        // TokenSegmentedControl is the file's last composable.
        val control = screen.substring(screen.indexOf("private fun TokenSegmentedControl"))
        assertTrue(control.contains("performHapticFeedback"))
        // WS-9 fix (P-2): the effect is named by the §1.7 map
        // (HapticSpec.SELECTION — the selection-class tick).
        assertTrue(screen.contains("HapticSpec.SELECTION"))
    }

    // ── A4 residue — emoji / glyph sweep pins ───────────────────

    @Test
    fun `A4 - the main-screen weather strip renders no emoji - vector icon plus the word`() {
        val row = sourceFile(
            "app/src/main/java/com/runwear/app/ui/components/WeatherConditionsRow.kt"
        ).readText()
        assertFalse(row.contains("weatherCode.icon"))
        assertFalse(row.contains("WeatherPillEmoji"))
        assertTrue(row.contains("weather.weatherCode.conditionIcon()"))
        assertTrue(row.contains("weather.weatherCode.description"))
    }

    @Test
    fun `A4 - the weather detail panel and the location picker use designed iconography`() {
        val sheets = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        ).readText()
        assertFalse(sheets.contains("weatherCode.icon"))
        assertFalse("the ✓ text glyph must not return", sheets.contains("\"✓\""))
        assertTrue(sheets.contains("weather.weatherCode.conditionIcon()"))
        assertTrue(sheets.contains("Icons.Outlined.Check"))
    }

    @Test
    fun `A4 - every weather condition has a designed vector icon`() {
        // The mapping is exhaustive by construction (a `when` over
        // the enum with no else); this pins the classes the design's
        // strip actually draws, and that the table stays total —
        // every code the model can carry resolves to a vector.
        assertTrue(WeatherCode.entries.isNotEmpty())
        for (code in WeatherCode.entries) {
            // Throws only if a future code loses its mapping.
            code.conditionIcon()
        }
        assertEquals(Icons.Outlined.WbSunny, WeatherCode.CLEAR_SKY.conditionIcon())
        assertEquals(Icons.Outlined.WbSunny, WeatherCode.MAINLY_CLEAR.conditionIcon())
        assertEquals(Icons.Outlined.Cloud, WeatherCode.OVERCAST.conditionIcon())
        assertEquals(Icons.Outlined.Thunderstorm, WeatherCode.THUNDERSTORM.conditionIcon())
    }
}
