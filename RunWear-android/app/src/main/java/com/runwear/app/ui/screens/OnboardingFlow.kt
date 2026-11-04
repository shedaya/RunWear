package com.runwear.app.ui.screens

import androidx.compose.animation.core.CubicBezierEasing

/**
 * WS-10 (A1 + T-2/R-2 + A2) — the onboarding sequence and copy of
 * record, from the final design package (frames 28–34, notes §§8G/9)
 * and the RW-27 rebuild program (Part 1 §1.4, Part 2 decision).
 *
 * THE SEQUENCE (design §9 — value before any ask):
 *   promise (28) → setup (29–31) → choice/fork (32) → consent (33)
 *   → capture → first look (34, the one soft ask).
 * The preference profile is built FIRST (setup questions, then the
 * location/permission step); Make It Me is introduced only at the
 * fork, consent gates capture, and the first personal look exists
 * before Premium is ever named as an offer.
 *
 * A1: the "Your first 5 looks are free" promise lives ONLY on frame
 * 28's own screen (the promise step below). It is NOT part of the
 * setup form — the pre-WS-10 build carried it in the header above
 * the setup questions, before Make It Me (or the word "looks") had
 * been introduced.
 *
 * A2: the welcome headline is Sol's pick, character for character:
 * "Run, weather or not." (Part 2 decision block, Nov 3 2026). It
 * occupies frame 28's headline slot — the position the design
 * renders the same line in title case as the logo's tagline pairing;
 * Sol's exact casing/punctuation supersedes that rendering.
 *
 * No price string appears anywhere in onboarding (design §9's rule:
 * the price lives only in the paywall's parameterized slot).
 */
object OnboardingCopy {
    // ── Frame 28 · Promise (welcome) ─────────────────────────────
    const val HEADLINE = "Run, weather or not."
    const val PROMISE_1_TITLE = "The right outfit for the weather"
    const val PROMISE_1_BODY =
        "One recommendation for the run you're about to take — feels-like, wind, and rain included."
    const val PROMISE_2_TITLE = "See yourself in every outfit"
    const val PROMISE_2_BODY =
        "Make It Me puts you in the photos. Your first 5 looks are free."
    const val PROMISE_CTA = "Get Started"
    // W-21 (Sol, Nov 4 walkthrough): the app is free forever —
    // premium features are the upsell — so the footer says exactly
    // that, verbatim, not "Free to start".
    const val PROMISE_FOOTER = "Free forever · Premium is optional"

    /** The A1 promise sentence — sanctioned on frame 28 ONLY. */
    const val FREE_LOOKS_PROMISE = "Your first 5 looks are free."

    // ── Frame 29 · Setup (preferences, Android expression) ──────
    const val SETUP_STEP_LABEL = "Step 1 of 2"
    const val SETUP_TITLE = "Make it yours"
    const val SETUP_SUB = "Three quick calls. You can change any of them later in Settings."
    const val TEMP_TITLE = "Temperature"
    const val TEMP_SUB = "How do you measure the weather?"
    const val FIT_TITLE = "Fit preference"
    const val FIT_SUB = "Whose fit should the outfits follow?"
    const val BODY_TITLE = "Body temperature"
    const val BODY_SUB = "Do you get cold easily, or overheat quickly?"
    const val SETUP_CTA = "Continue"

    // ── Frame 30 · Location step ────────────────────────────────
    const val LOCATION_STEP_LABEL = "Step 2 of 2"
    const val LOCATION_TITLE = "One more thing — where are you running?"
    const val LOCATION_BODY = "We need your location to get accurate weather for your run"
    const val LOCATION_CTA_PRIMARY = "Use My Location"
    const val LOCATION_CTA_SECONDARY = "Set a City Instead"
}

/**
 * Every step of onboarding, in the design's order (§9). The first
 * three are steps of [OnboardingScreen] itself; FORK, CONSENT and
 * CAPTURE are hosted by the Make It Me flow (MainActivity +
 * MakeItMeScreens), and FIRST_LOOK is frame 34's moment.
 */
enum class OnboardingStep {
    PROMISE, PREFERENCES, LOCATION, FORK, CONSENT, CAPTURE, FIRST_LOOK
}

/**
 * The sequence as a pure model, so the gate can pin the order, the
 * primary path, and consent's position in it: consent is reachable
 * from every step that precedes it, and NO primary edge reaches
 * capture (or the first look) without passing through consent —
 * the privacy commitment the upload flow's own doc states ("no
 * photo UI exists before the consent screen").
 */
object OnboardingSequence {
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep.PROMISE,
        OnboardingStep.PREFERENCES,
        OnboardingStep.LOCATION,
        OnboardingStep.FORK,
        OnboardingStep.CONSENT,
        OnboardingStep.CAPTURE,
        OnboardingStep.FIRST_LOOK
    )

    /** The steps [OnboardingScreen] itself presents, in order. */
    val inScreenSteps: List<OnboardingStep> = listOf(
        OnboardingStep.PROMISE,
        OnboardingStep.PREFERENCES,
        OnboardingStep.LOCATION
    )

    /** The primary (forward) edge out of [step]; null at the end. */
    fun primaryNext(step: OnboardingStep): OnboardingStep? = when (step) {
        OnboardingStep.PROMISE -> OnboardingStep.PREFERENCES
        OnboardingStep.PREFERENCES -> OnboardingStep.LOCATION
        OnboardingStep.LOCATION -> OnboardingStep.FORK
        OnboardingStep.FORK -> OnboardingStep.CONSENT
        OnboardingStep.CONSENT -> OnboardingStep.CAPTURE
        OnboardingStep.CAPTURE -> OnboardingStep.FIRST_LOOK
        OnboardingStep.FIRST_LOOK -> null
    }

    /** The step Back returns to within [OnboardingScreen]. */
    fun inScreenPrevious(step: OnboardingStep): OnboardingStep? = when (step) {
        OnboardingStep.LOCATION -> OnboardingStep.PREFERENCES
        OnboardingStep.PREFERENCES -> OnboardingStep.PROMISE
        else -> null
    }

    /**
     * True when following primary edges from [from] reaches
     * [target]. Consent's gate is structural: it must be reachable
     * from every earlier step, and capture/first-look must be
     * reachable ONLY through it (they have no other inbound edge).
     */
    fun reaches(from: OnboardingStep, target: OnboardingStep): Boolean {
        var current: OnboardingStep? = from
        val seen = mutableSetOf<OnboardingStep>()
        while (current != null && seen.add(current)) {
            if (current == target) return true
            current = primaryNext(current)
        }
        return false
    }

    fun consentReachableFrom(step: OnboardingStep): Boolean =
        reaches(step, OnboardingStep.CONSENT)
}

/**
 * Motion constants for onboarding (Part 1 §1.4): Android steps
 * advance with the M3 shared-axis horizontal slide + fade
 * (300 ms, emphasized easing); the first-look reveal is the photo
 * crossfade (400 ms — §1.4 is the spec of record; coordinator
 * ruling 2026-11-03 superseded the WS-10 lane assignment's
 * 600 ms), the ONE success haptic fires as the photo lands
 * (§1.7 — first look ready = success, once), and the summary
 * band settles 120 ms after the photo lands (consent accept is
 * SILENT).
 */
object OnboardingMotion {
    const val STEP_TRANSITION_MS = 300
    const val FIRST_LOOK_PHOTO_CROSSFADE_MS = 400
    const val FIRST_LOOK_BAND_SETTLE_MS = 300
    const val FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS = 120

    /** When the ONE success haptic fires: as the photo lands,
     *  at crossfade completion (§1.4/§1.7; iOS parity). */
    const val FIRST_LOOK_HAPTIC_AT_MS = FIRST_LOOK_PHOTO_CROSSFADE_MS

    /** Total delay from reveal start until the band settles. */
    const val FIRST_LOOK_BAND_AT_MS =
        FIRST_LOOK_PHOTO_CROSSFADE_MS + FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS

    /** M3 emphasized easing, cubic-bezier(0.2, 0, 0, 1). */
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
