package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE A, ITEM 2 (DA-03 + RW-39) — the fork → intake → submit
 * → generating sequence, driven step by step exactly as the owner's
 * recording ran it, asserting whether/when the first-look moment
 * raises.
 *
 * THE GEN-5 MECHANISM (code evidence): the fork's upload flow was
 * hosted under `forkDue && showForkUpload`, and forkDue contains
 * `!forkDecided`. Submitting photos flips forkDecided via
 * PhotoSubmissionDecided — so at the submit instant forkDue went
 * false, the host unmounted the flow, the flow's SAVED beat (whose
 * completion fires onFinished) was cancelled with the composition,
 * onFinished never ran, and the moment — raised ONLY from that
 * callback on the fork path — never presented. This test encodes
 * both hostings: the gen-5 gate drops the flow at the submit step;
 * the gen-6 ForkHostPolicy keeps it hosted until it finishes, the
 * finish raises the moment, and the moment's claims then resolve
 * Making → (Ready but not displayed) → READY only when the
 * personal photo is displayed AND loaded.
 */
class ForkFirstLookSequenceTest {

    /** The sequence's moving parts, mirroring the production seams. */
    private class Sequence {
        // Host state (MainActivity).
        var onboardingJustCompleted = true
        var outfitReady = true
        var featureEnabled = true
        var uploadOpen = false
        var firstLookRaised = false

        // Make It Me state (MakeItMeViewModel / repository).
        var forkDecided = false

        // Flow state (MakeItMeUploadFlow).
        var flowFinished = false

        // Main view state.
        var lookStatus: PersonalLookStatus = PersonalLookStatus.None
        var personalPhotoDisplayed = false
        var personalPhotoLoaded = false

        fun forkDue(): Boolean =
            onboardingJustCompleted && outfitReady && !forkDecided && featureEnabled

        /** The gen-5 hosting expression, kept here as the record. */
        fun gen5UploadHosted(): Boolean = forkDue() && uploadOpen

        /** The gen-6 hosting expression (ForkHostPolicy). */
        fun gen6UploadHosted(): Boolean =
            ForkHostPolicy.showUploadFlow(uploadOpen, featureEnabled)

        fun tapUseMyPhotos() {
            uploadOpen = true
        }

        /** submitPhotos(): the repository call succeeds; the flow
         * marks the fork decided and enters its SAVED beat. */
        fun submitPhotos() {
            // PhotoSubmissionDecided.onSubmitted — fires at SUBMIT,
            // before the flow's finish callback.
            forkDecided = true
            lookStatus = PersonalLookStatus.Making
        }

        /** The SAVED beat completes: the flow calls onFinished,
         * which raises the moment — IF the flow is still hosted. */
        fun savedBeatCompletes() {
            if (!gen6UploadHosted()) return // unmounted: beat cancelled
            flowFinished = true
            uploadOpen = false
            onboardingJustCompleted = false
            firstLookRaised = true
        }

        fun momentHosted(): Boolean =
            ForkHostPolicy.showFirstLookMoment(firstLookRaised, featureEnabled)

        fun momentState(): FirstLookState =
            FirstLookPresentation.resolve(lookStatus, personalPhotoDisplayed)
    }

    @Test
    fun `the gen-5 gate drops the upload flow at the submit instant`() {
        val s = Sequence()
        assertTrue("fork is due after onboarding", s.forkDue())
        s.tapUseMyPhotos()
        assertTrue("gen-5 host shows the flow before submit", s.gen5UploadHosted())
        s.submitPhotos()
        // THE DEFECT: submission flips forkDecided → forkDue false →
        // the gen-5 host unmounts the flow mid-SAVED-beat.
        assertFalse("forkDecided flipped by submit", s.forkDue())
        assertFalse(
            "gen-5 host drops the flow at submit — onFinished can never fire",
            s.gen5UploadHosted()
        )
    }

    @Test
    fun `the fork sequence raises the moment and resolves its claims in order`() {
        val s = Sequence()

        // 1. Fork due; user chooses "Use my photos".
        assertTrue(ForkHostPolicy.showForkScreen(s.forkDue(), s.uploadOpen))
        s.tapUseMyPhotos()
        assertTrue(s.gen6UploadHosted())
        assertFalse(ForkHostPolicy.showForkScreen(s.forkDue(), s.uploadOpen))

        // 2. Consent + photos + submit: the flow stays hosted
        // through its SAVED beat despite forkDecided flipping.
        s.submitPhotos()
        assertTrue(
            "gen-6 host keeps the flow through the SAVED beat",
            s.gen6UploadHosted()
        )
        assertFalse("moment not raised before the flow finishes", s.momentHosted())

        // 3. The beat completes → onFinished → the moment raises in
        // its MAKING state (the recording's missing screen).
        s.savedBeatCompletes()
        assertTrue(s.flowFinished)
        assertTrue("the moment is raised by the finish", s.momentHosted())
        assertEquals(FirstLookState.MAKING, s.momentState())
        assertFalse(FirstLookPresentation.showsFinishedClaims(s.momentState()))
        assertFalse(
            "no success sequence before the photo loads",
            FirstLookRevealPolicy.shouldRunSuccessSequence(
                s.momentState(), s.personalPhotoLoaded
            )
        )
        assertTrue(
            "\"See My Outfit\" is available even while making — never a gate",
            FirstLookRevealPolicy.seeOutfitAlwaysAvailable(s.momentState())
        )

        // 4. The look reports Ready server-side but the personal
        // photo is not the displayed one yet: still MAKING, still
        // no finished claims (the silent-swap state must not claim).
        s.lookStatus = PersonalLookStatus.Ready
        assertEquals(FirstLookState.MAKING, s.momentState())

        // 5. The personal photo lands on screen but has not LOADED:
        // the presentation is READY, but the success sequence
        // (haptic + band at 0.52 s) still waits for the load.
        s.personalPhotoDisplayed = true
        assertEquals(FirstLookState.READY, s.momentState())
        assertFalse(
            FirstLookRevealPolicy.shouldRunSuccessSequence(
                s.momentState(), s.personalPhotoLoaded
            )
        )

        // 6. Loaded: the full choreography may run, exactly once —
        // READY + loaded is the only success-sequence gate.
        s.personalPhotoLoaded = true
        assertTrue(
            FirstLookRevealPolicy.shouldRunSuccessSequence(
                s.momentState(), s.personalPhotoLoaded
            )
        )
        assertTrue(FirstLookPresentation.showsFinishedClaims(s.momentState()))
        assertTrue(FirstLookPresentation.showsPremiumBand(s.momentState()))
    }

    @Test
    fun `a failed look resolves the moment to FAILED with its own retry`() {
        val s = Sequence()
        s.tapUseMyPhotos()
        s.submitPhotos()
        s.savedBeatCompletes()
        assertTrue(s.momentHosted())
        s.lookStatus = PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR)
        assertEquals(FirstLookState.FAILED, s.momentState())
        // No finished claims, no band — and the phase is not a dead
        // end: "See My Outfit" remains, and the surface now carries
        // its same-cell "Try again" (the FA-12 residual, closed by
        // the onRetry door on FirstLookMoment).
        assertFalse(FirstLookPresentation.showsFinishedClaims(s.momentState()))
        assertFalse(FirstLookPresentation.showsPremiumBand(s.momentState()))
        assertTrue(FirstLookRevealPolicy.seeOutfitAlwaysAvailable(s.momentState()))
        assertEquals("Try again", MakeItMeStrings.G6_RETRY)
    }

    @Test
    fun `reveal timing is the iOS choreography - crossfade 0-4s, haptic at 0-4s, band at 0-52s`() {
        // The constants the moment's LaunchedEffect actually delays
        // on (OnboardingMotion), pinned against the iOS numbers.
        assertEquals(400, OnboardingMotionValues.CROSSFADE_MS)
        assertEquals(400, OnboardingMotionValues.HAPTIC_AT_MS)
        assertEquals(520, OnboardingMotionValues.BAND_AT_MS)
    }

    /** Reads the production constants without a Compose runtime. */
    private object OnboardingMotionValues {
        val CROSSFADE_MS: Int =
            com.runwear.app.ui.screens.OnboardingMotion.FIRST_LOOK_PHOTO_CROSSFADE_MS
        val HAPTIC_AT_MS: Int =
            com.runwear.app.ui.screens.OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS
        val BAND_AT_MS: Int = HAPTIC_AT_MS +
            com.runwear.app.ui.screens.OnboardingMotion.FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS
    }
}
