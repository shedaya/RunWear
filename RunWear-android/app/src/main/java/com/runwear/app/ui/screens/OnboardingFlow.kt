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
 * A1: the "Your first 5 looks are free" promise lived ONLY on frame
 * 28's own screen (the promise step below), never on the setup
 * form. GEN-3 ROUND 2 (Sol, Nov 4): Sol's final point-2 copy
 * ("Picture yourself at the start line with the perfect outfit ready to go.") supersedes
 * its welcome placement — the sentence remains defined as
 * FREE_LOOKS_PROMISE (the canonical free-looks wording) but is no
 * longer rendered on the welcome screen.
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
    // GEN-3 ROUND 2 (Sol, Nov 4 — the name-led welcome, CHOSEN by
    // Sol; supersedes the A-SETTINGS/WS-E title+body composition
    // and the earlier "no name-led variant" instruction): each
    // point's headline leads with its feature name in PINE BOLD,
    // followed by the plain phrase in regular weight. No phrase
    // repeats its feature name. Point 1's phrase is its existing
    // plain copy, unchanged; points 2 and 3 carry Sol's final
    // wording verbatim.
    const val PROMISE_1_NAME = "RunWear"
    // GEN-5 (RW-28 item 27, design §1): each point's HEADLINE is
    // "Name — headline phrase" in pine bold; the body line beneath
    // is separate. The headline phrases are the settled ones.
    const val PROMISE_1_HEADLINE = "The right outfit for the weather"
    const val PROMISE_1_PHRASE =
        "One recommendation for the run you're about to take — feels-like, wind, and rain included."
    const val PROMISE_2_NAME = "Make It Me"
    const val PROMISE_2_HEADLINE = "See yourself in every outfit"
    // GEN-3 WELCOME UPDATE (Sol, Nov 4): point 2's final phrase,
    // word for word (supersedes "Put yourself in the photos.").
    const val PROMISE_2_PHRASE = "Picture yourself at the start line with the perfect outfit ready to go."
    const val PROMISE_3_NAME = "Wear I've Run"
    const val PROMISE_3_HEADLINE = "Remember every run"
    const val PROMISE_3_PHRASE =
        "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran."

    /** One name-led welcome point: the [headlineLine] in pine bold,
     * the [phrase] as the separate body line beneath it. */
    data class WelcomePoint(val name: String, val headline: String, val phrase: String) {
        val headlineLine: String get() = "$name — $headline"
    }

    /** The three welcome points, in display order. */
    val WELCOME_POINTS: List<WelcomePoint> = listOf(
        WelcomePoint(PROMISE_1_NAME, PROMISE_1_HEADLINE, PROMISE_1_PHRASE),
        WelcomePoint(PROMISE_2_NAME, PROMISE_2_HEADLINE, PROMISE_2_PHRASE),
        WelcomePoint(PROMISE_3_NAME, PROMISE_3_HEADLINE, PROMISE_3_PHRASE)
    )
    const val PROMISE_CTA = "Get Started"
    // GEN-5 (RW-28 item 27, design §1): the settled footer line.
    const val PROMISE_FOOTER = "Free to start · Set up in under a minute"

    /** The A1 promise sentence — sanctioned on frame 28 ONLY. */
    // RW-128: derives from the one allowance constant (10).
    val FREE_LOOKS_PROMISE: String =
        "Your first ${com.runwear.app.makeitme.MakeItMeUpsellRules.FREE_ALLOWANCE} looks are free."

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
    // Combined-screen option fidelity: temperature in FULL WORDS,
    // and the body-temperature slider's end labels. Fit carries
    // exactly TWO options (RW-81, Sol's ruling, Nov 6, 2026,
    // superseding Nov 4): neutral is not a labelled option — it is
    // the state where neither button is selected (tap the selected
    // button again to return to it), so there is no third string.
    const val TEMP_OPTION_FAHRENHEIT = "Fahrenheit"
    const val TEMP_OPTION_CELSIUS = "Celsius"
    const val FIT_OPTION_MENS = "Men's"
    const val FIT_OPTION_WOMENS = "Women's"
    const val BODY_SLIDER_MIN_LABEL = "Runs Cold"
    const val BODY_SLIDER_MAX_LABEL = "Runs Warm"
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
 * The body-temperature slider's five stops (Sol's onboarding ruling,
 * Nov 4): the combined preferences screen carries a 5-stop slider
 * whose ends read "Runs Cold" / "Runs Warm", mapping one-to-one onto
 * the five [com.runwear.shared.domain.model.ComfortPreference]
 * values — the previous 3-option segmented control collapsed the
 * two end stops away.
 */
object ComfortStops {
    val stops: List<com.runwear.shared.domain.model.ComfortPreference> = listOf(
        com.runwear.shared.domain.model.ComfortPreference.RUNS_VERY_COLD,
        com.runwear.shared.domain.model.ComfortPreference.RUNS_COLD,
        com.runwear.shared.domain.model.ComfortPreference.NEUTRAL,
        com.runwear.shared.domain.model.ComfortPreference.RUNS_WARM,
        com.runwear.shared.domain.model.ComfortPreference.RUNS_VERY_WARM
    )

    fun indexOf(preference: com.runwear.shared.domain.model.ComfortPreference): Int =
        stops.indexOf(preference).coerceAtLeast(0)

    fun at(index: Int): com.runwear.shared.domain.model.ComfortPreference =
        stops[index.coerceIn(0, stops.lastIndex)]
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
