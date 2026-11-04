import Foundation

// MARK: - Onboarding sequence & copy (WS-10 · A1 / A2)
//
// The design's onboarding order (§9; frames 28–34), as one value
// the flow and the tests both read:
//
//   promise (28) → setup (29–31) → choice (32) → consent (33)
//   → first look (34)
//
// iOS expression (§1.4): the setup questions advance ONE question
// per screen with native push transitions — welcome is the stack's
// root, each question is pushed, Back is the exact reverse. The
// first-look phase (the fork, consent, capture, and the frame-34
// moment) is hosted by the onboarding view after the location
// step completes the setup.

enum OnboardingStep: Int, CaseIterable, Hashable {
    /// Frame 28 — the standalone Welcome / promise screen. The
    /// ONLY screen in onboarding that may carry the free-looks
    /// promise (A1): the setup form's header lost it, and no later
    /// onboarding screen repeats it.
    case welcome
    /// Frame 29, question 1 of 3 — temperature unit.
    case temperature
    /// Frame 29, question 2 of 3 — fit preference.
    case fit
    /// Frame 29, question 3 of 3 — body temperature.
    case comfort
    /// Frame 30 — location (the system permission prompt, frame
    /// 31's subject, fires from this step's "Use My Location").
    case location
    /// Frames 32–34 — the first-look phase: fork → consent →
    /// capture → the first personal look and its one soft ask.
    case firstLook

    /// The step this step advances to; nil only past the end.
    var next: OnboardingStep? {
        OnboardingStep(rawValue: rawValue + 1)
    }

    /// The setup questions, in order — one per screen on iOS.
    static var setupQuestions: [OnboardingStep] {
        [.temperature, .fit, .comfort]
    }

    /// Every step from the promise screen through the first look,
    /// in the design's order.
    static var fullSequence: [OnboardingStep] {
        allCases
    }
}

// MARK: - Welcome copy (frame 28 + A2)

enum OnboardingCopy {
    /// A2 — Sol's pick (Nov 3, 2026), installed character for
    /// character in the welcome headline slot: the tagline,
    /// promoted to the headline. Do not "correct" the casing —
    /// the decision records this exact string.
    static let welcomeHeadline = "Run, weather or not."

    /// The free-looks promise. By A1 it lives ONLY on frame 28's
    /// own screen (inside the "See yourself in every outfit"
    /// promise row) — never on the setup form, never on the fork.
    /// Frame 34's meter ("look 1 of your 5 free looks") is
    /// accounting copy, not this promise, and is not governed here.
    static let freeLooksPromise = "Your first 5 looks are free."
}

// MARK: - First-look reveal timing (§1.4, spec of record)
//
// The frame-34 reveal, as numbers the view and the tests share:
// the photo crossfades in (400 ms), the success haptic fires ONCE
// as the photo lands (at crossfade completion), and the §9
// summary band slides up 120 ms after the photo lands (0.52 s
// from reveal start). Program §1.4 is the spec of record for the
// 400 ms crossfade (coordinator ruling, 2026-11-03, superseding
// the WS-10 lane assignment's 600 ms). Consent acceptance
// carries NO haptic (§1.7: the table is the complete list, and
// consent is not in it).

enum FirstLookReveal {
    static let photoCrossfadeSeconds = 0.4
    static let bandDelayAfterPhotoSeconds = 0.12

    /// When the summary band starts, measured from reveal start.
    static var bandStartSeconds: Double {
        photoCrossfadeSeconds + bandDelayAfterPhotoSeconds
    }

    /// Exactly one success haptic per reveal (§1.7).
    static let successHapticCount = 1
}
