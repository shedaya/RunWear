import CoreGraphics

/// WS-1 (RC-1, iOS half): the home composition's geometry, as ONE
/// value set — the constants the scroll handoff and the sheet
/// overlap are built from, named in the motion & interaction
/// specification (rebuild program Part 1, §1.1) and the final
/// design's §7 header pattern.
///
/// Before this type existed the same numbers lived as unrelated
/// literals scattered across `WeatherOutfitView` (the −28 sheet
/// overlap, the /220 handoff range, the 0.94 settled scale, the
/// 24pt card radius, the 16pt card inset) and `HeroImageView`
/// (the flag chip's 40pt bottom inset, which is this overlap plus
/// a 12pt gap). Naming them once is what makes the invariants
/// pinnable: the overlap is a constant of the composition, not a
/// moving part of the handoff, and every handoff-derived value is
/// a pure function of the scroll-coupled progress.
///
/// The handoff itself is scroll-coupled 1:1 (§1.1): progress is a
/// direct function of the scroll offset — no spring, no lag, no
/// animation transaction is applied to it. Reduce Motion is
/// handled at the call site (the transform is not applied).
enum HomeHandoffGeometry {
    /// The sheet overlaps the photo's foot by this much, at rest
    /// and at every point of the handoff (design §7; §1.1: "the
    /// sheet's −28 overlap is constant throughout").
    static let sheetOverlap: CGFloat = 28
    /// The hero's top corners carry the same radius as the overlap
    /// value (the sheet's rounded top edge).
    static let sheetCornerRadius: CGFloat = 28
    /// Scroll travel over which the handoff runs, from the composed
    /// at-rest geometry to the settled card end-state.
    static let handoffRange: CGFloat = 220
    /// The settled card's corner radius (§1.1: radius grows
    /// 0 → 24 across the handoff range).
    static let cardCornerRadius: CGFloat = 24
    /// The settled card's horizontal inset.
    static let cardHorizontalInset: CGFloat = 16
    /// The settled card's scale (§1.1: 1.00 → 0.94).
    static let settledScale: CGFloat = 0.94
    /// Photo-zone chrome fades across this portion of the handoff
    /// (§1.1: opacity 1 → 0 across the FIRST 40% of travel).
    static let chromeFadePortion: CGFloat = 0.4
    /// The flag chip's bottom inset inside the hero frame: the
    /// sheet overlap plus a fixed gap, so the chip clears the
    /// sheet's rounded top edge in the stacked layouts. Capped by
    /// construction — it can never exceed overlap + gap.
    static let flagChipGap: CGFloat = 12
    static var flagChipBottomInset: CGFloat { sheetOverlap + flagChipGap }

    /// Scroll offset → handoff progress, clamped to [0, 1].
    /// Negative (rubber-band) offsets are the at-rest state.
    static func progress(forScrollOffset offset: CGFloat) -> CGFloat {
        min(1, max(0, offset / handoffRange))
    }

    /// The hero's scale at a handoff progress.
    static func scale(progress: CGFloat) -> CGFloat {
        1 - (1 - settledScale) * clamped(progress)
    }

    /// The hero's corner radius at a handoff progress.
    static func cornerRadius(progress: CGFloat) -> CGFloat {
        cardCornerRadius * clamped(progress)
    }

    /// The hero's horizontal inset at a handoff progress.
    static func horizontalInset(progress: CGFloat) -> CGFloat {
        cardHorizontalInset * clamped(progress)
    }

    /// Photo-zone chrome opacity at a handoff progress: fully
    /// present at rest, gone by `chromeFadePortion` of the travel,
    /// linear in between (§1.1). The chrome never slides.
    static func chromeOpacity(progress: CGFloat) -> CGFloat {
        let p = clamped(progress)
        guard p > 0 else { return 1 }
        return max(0, 1 - p / chromeFadePortion)
    }

    /// Photo-zone chrome is interactive ONLY at exact rest: §1.1
    /// makes it non-interactive from the moment the fade begins.
    static func chromeIsInteractive(progress: CGFloat) -> Bool {
        clamped(progress) <= 0
    }

    private static func clamped(_ progress: CGFloat) -> CGFloat {
        min(1, max(0, progress))
    }
}
