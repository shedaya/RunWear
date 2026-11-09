import Foundation

// MARK: - Onboarding sequence & copy (WS-10 · A1 / A2)
//
// The design's onboarding order (§9; frames 28–34), as one value
// the flow and the tests both read:
//
//   promise (28) → preferences (29) → location (30) → choice (32)
//   → consent (33) → first look (34)
//
// iOS expression: native push transitions — welcome is the stack's
// root, each STEP is pushed, Back is the exact reverse. GEN-3
// (Sol, Nov 4 — RULED): the three frame-29 preference questions
// (temperature unit, fit preference, body temperature) are ONE
// COMBINED SCREEN, superseding the one-question-per-screen
// structure for these three questions only; the substantive
// steps (location, fork, consent, photos) stay one step per
// screen. This also closes the gen-2 carried R-3
// onboarding-structure divergence in the ruled direction: both
// platforms now combine. The first-look phase (the fork,
// consent, capture, and the frame-34 moment) is hosted by the
// onboarding view after the location step completes the setup.

enum OnboardingStep: Int, CaseIterable, Hashable {
    /// Frame 28 — the standalone Welcome / promise screen. The
    /// ONLY screen in onboarding that may carry the free-looks
    /// promise (A1): the setup form's header lost it, and no later
    /// onboarding screen repeats it.
    case welcome
    /// Frame 29 — the COMBINED preferences screen (Sol's GEN-3
    /// ruling): temperature unit, fit preference and body
    /// temperature together under "Make it yours".
    case preferences
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
    /// own screen — never on the setup form, never on the fork.
    /// Frame 34's meter ("look 1 of your 10 free looks") is
    /// accounting copy, not this promise, and is not governed here.
    /// GEN-3 ROUND 2 (Sol, Nov 4): Sol's final point-2 copy (the
    /// name-led phrase below) supersedes the promise's welcome
    /// placement — the sentence remains defined here as the
    /// canonical free-looks wording but is no longer rendered on
    /// the welcome screen.
    /// RW-128 (fix wave 2026-11-07): the promise follows the
    /// aligned allowance — 10, from the store's one constant.
    static let freeLooksPromise = "Your first 10 looks are free."

    /// GEN-3 ROUND 2 (Sol, Nov 4 — the name-led welcome, CHOSEN by
    /// Sol; supersedes the I-SETTINGS/WS-E title+detail composition
    /// and the earlier "no name-led variant" instruction): each
    /// point's headline leads with its feature name in PINE BOLD,
    /// followed by the plain phrase in regular weight. No phrase
    /// repeats its feature name. Point 1's phrase is its existing
    /// plain copy, unchanged; points 2 and 3 carry Sol's final
    /// wording verbatim (point 2 as amended by his last ruling:
    /// "Picture yourself at the start line with the perfect outfit
    /// ready to go.").
    struct WelcomePoint: Equatable {
        let name: String
        /// GEN-5 (RW-28 item 27): the headline's plain phrase —
        /// the headline reads "Name — phrase" (name in pine bold),
        /// and the body line below it does NOT repeat the name.
        let phrase: String
        let body: String
    }

    static let welcomePoints: [WelcomePoint] = [
        WelcomePoint(
            name: "RunWear",
            phrase: "The right outfit for the weather",
            body: "One recommendation for the run you're about to take — feels-like, wind, and rain included."
        ),
        WelcomePoint(
            name: "Make It Me",
            phrase: "See yourself in every outfit",
            body: "Picture yourself at the start line with the perfect outfit ready to go."
        ),
        WelcomePoint(
            name: "Wear I've Run",
            phrase: "Remember every run",
            body: "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran."
        ),
    ]

    /// W-21 (GEN-2 walkthrough, 2026-11-04): frame 28's quiet
    /// footer, verbatim. Sol struck the previous line ("Free to
    /// start · Set up in under a minute") on both platforms: the
    /// app is free forever and Premium is the optional paid part,
    /// so "free to start" misstates the model. This wording is
    /// flagged for Sol's walkthrough verdict — do not vary it.
    /// GEN-5 (RW-28 item 27): restored to the settled frame-28
    /// footer (design-spec §1). The W-21 "Free forever" line was
    /// flagged as a deviation by the gen-4 census (A-1) on the
    /// shipped build; the fix wave restores the settled form.
    static let welcomeFooter = "Free to start · Set up in under a minute"

    /// GEN-3 (Sol, Nov 4 — RULED): the combined preferences
    /// screen's headline and lede, verbatim.
    static let preferencesHeadline = "Make it yours"
    static let preferencesLede = "Three quick calls. You can change any of them later in Settings."

    /// The three preference questions, in order, in their SHIPPED
    /// wording — each heads its control on the combined screen
    /// (they were the one-per-screen steps' question lines before
    /// the GEN-3 combination; the wording is unchanged).
    static let preferencesQuestions = [
        "How do you measure the weather?",
        "Whose fit should the outfits follow?",
        "Do you get cold easily, or overheat quickly?",
    ]
}

// MARK: - Fork caption composition (W-23)

/// The Make It Me fork's location caption, composed ONLY from a
/// known place. The fork used to render the view model's
/// in-flight placeholder name verbatim, so for a few hundred
/// milliseconds the caption slot carried that placeholder over a
/// blank photo area. The rule: no known place, no caption — the
/// photo area's loading treatment carries the wait instead, and
/// no raw placeholder string ever reaches the caption slot.
/// (Lives here, with the rest of onboarding's composition, so
/// the test target — which does not compile the flow views —
/// can pin the rule functionally.)
enum ForkContextLine {
    static func make(placeDisplayName: String, isPlaceKnown: Bool, feelsLike: String) -> String? {
        guard isPlaceKnown else { return nil }
        return "\(placeDisplayName) · \(feelsLike)"
    }
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

// MARK: - Frame-34 presentation (GEN-2 I2 · W-16/W-18)
//
// The first-look moment's state, resolved from the SERVER's word
// and the ONE free-looks counter — never from the screen's own
// assumptions. The defect (2026-11-04 walkthrough, W-16): the
// moment appeared already captioned "You're in the photo now."
// with its free-look debit announced, while the photo in the card
// was still the fork's STOCK photo; ~35 s later the view model's
// live hero URL mutated underneath it and the generated photo
// hard-cut in with no state change. Sol's correction: a processing
// notice existed but sat at the screen's FOOTER — the working
// state belongs ON the photo, where the user is looking.
//
// The phases, and what each may claim:
//  * making — the personal look is not the photo on screen yet.
//    The photo area carries the working treatment and the
//    processing copy ON the photo. NO caption, NO meter: nothing
//    about a delivered look may be stated, and no debit is
//    presented as final — the server charges only on delivery.
//  * ready — the generated photo IS the photo on screen. Only
//    now the caption claims it, and the meter renders from the
//    shared counter (FreeLooksMeter's source): the same
//    `freeGenerationsUsed` Settings reads, re-read from the
//    server profile when the look landed. Until that re-read
//    lands (counter still 0 on a free account), the meter waits
//    rather than printing a number the server hasn't confirmed.
//  * failed — the server reported the look failed. The failure
//    line stands; no caption, no meter (nothing was charged).

enum FirstLookPhase: Equatable {
    case making
    case ready
    case failed
}

struct FirstLookPresentation: Equatable {
    let phase: FirstLookPhase
    /// The processing title rendered ON the photo (making only) —
    /// the spec §7 G1 progress line, on the surface the user is
    /// looking at instead of the screen's footer.
    let photoOverlayTitle: String?
    /// The processing detail rendered ON the photo under the
    /// title (making only) — the §7 G2 line, verbatim.
    let photoOverlayDetail: String?
    /// The claim line under the photo (ready only).
    let caption: String?
    /// The free-look meter under the caption (ready only, and
    /// only once the shared counter confirms the delivery).
    let meterLine: String?
    /// The failure line (failed only).
    let failureLine: String?
    /// Whether the §9 Premium band + soft ask may show: the ask
    /// follows the look — it is presented only once the first
    /// look exists (ready), never while it is still being made.
    var showsPremiumBand: Bool { phase == .ready }

    static func resolve(
        lookState: PersonalLookState,
        personalPhotoDisplayed: Bool,
        personalImageLoaded: Bool = false,
        freeGenerationsUsed: Int,
        freeAllowance: Int,
        isSubscribed: Bool
    ) -> FirstLookPresentation {
        // GEN-3 (census-ios Y-1, narrowed by Sol's ruling of
        // 2026-11-04): the claim waits for the PHOTO, not just
        // its address. The GEN-2 seam declared ready from the
        // photo's URL presence alone — the claim and the counter
        // rendered over the card's still-empty image phase for
        // ~0.7 s before any pixels arrived. Ready now requires
        // the displayed personal photo's image to have actually
        // loaded: claim never precedes photo. (Which personal
        // photo serves is not policed here: a look generated from
        // ANY of the user's own uploaded reference photos —
        // including banked photos from references uploaded in an
        // earlier version — is accepted as the user's look per
        // Sol's ruling; the defects in scope were the ordering
        // ones.)
        if personalPhotoDisplayed && personalImageLoaded {
            return FirstLookPresentation(
                phase: .ready,
                photoOverlayTitle: nil,
                photoOverlayDetail: nil,
                caption: MakeItMeCopy.firstLookReadyCaption,
                meterLine: readyMeterLine(
                    used: freeGenerationsUsed,
                    allowance: freeAllowance,
                    isSubscribed: isSubscribed
                ),
                failureLine: nil
            )
        }
        if case .failed = lookState {
            return FirstLookPresentation(
                phase: .failed,
                photoOverlayTitle: nil,
                photoOverlayDetail: nil,
                caption: nil,
                meterLine: nil,
                failureLine: MakeItMeCopy.firstLookFailed
            )
        }
        return FirstLookPresentation(
            phase: .making,
            photoOverlayTitle: MakeItMeCopy.progressTitle,
            photoOverlayDetail: MakeItMeCopy.progressSubline,
            caption: nil,
            meterLine: nil,
            failureLine: nil
        )
    }

    /// The ready meter, composed from the SAME counter Settings
    /// renders (W-18's one source of truth). A subscribed user has
    /// no free-look accounting to state. A free user whose counter
    /// has not yet confirmed the delivery (still 0 — the store
    /// re-reads the profile when the look lands, and that read may
    /// still be in flight) gets NO meter rather than a number
    /// that contradicts Settings.
    private static func readyMeterLine(used: Int, allowance: Int, isSubscribed: Bool) -> String? {
        guard !isSubscribed, used > 0 else { return nil }
        let counted = min(used, allowance)
        let remaining = allowance - counted
        if remaining > 0 {
            return "That was look \(counted) of your \(allowance) free looks — \(remaining) remain, whenever you want them."
        }
        return "That was look \(counted) of your \(allowance) free looks."
    }
}
