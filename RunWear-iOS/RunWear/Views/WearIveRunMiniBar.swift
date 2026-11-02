import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25, PRE-ADD STATE ONLY)
//
// The collection feature is NOT built in code anywhere in this
// build — there is no store, no persistence, no capture flow. What
// ships here is scaffolding only:
//   (a) a pure state model + narration formatter, unit-tested;
//   (b) the mini bar view, fully styled, driven by a state that
//       only ever yields .preAdd in this build — so NOTHING renders
//       (WeatherOutfitView's dock renders the bar only when the
//       state != .preAdd, and the model's currentState is .preAdd).
// Do not wire a real data source into currentState without the
// collection feature itself landing first.

// MARK: - State model

enum WearIveRunState: Equatable {
    /// Nothing added yet — the only state this build produces.
    case preAdd
    /// An entry was added at a place and moment.
    case added(place: String, moment: String)
    /// The current entry was updated to a new payload.
    case updated(payload: String)
}

/// How the bar is being presented; drives the background policy.
enum WearIveRunPresentation: Equatable {
    case mini
    case expanded
    case endOfScrollCue
}

enum WearIveRunMiniBarModel {
    /// The bar's state for this build: always .preAdd, because the
    /// collection is not built. This is the single source the dock
    /// reads — a future collection feature replaces this value,
    /// nothing else about the dock changes.
    static let currentState: WearIveRunState = .preAdd

    static let wordmark = "Wear I've Run"
}

// MARK: - Narration formatter

enum WearIveRunNarration {
    /// The one narration line under the wordmark.
    /// - preAdd: nil (the bar does not render at all)
    /// - added: "Added {place}, {moment}" — the comma form, e.g.
    ///   "Added New York, Now"
    /// - updated: "Update to {payload}" (the payload renders pine
    ///   at the call site; the plain string is the test surface)
    static func line(for state: WearIveRunState) -> String? {
        switch state {
        case .preAdd:
            return nil
        case .added(let place, let moment):
            return "Added \(place), \(moment)"
        case .updated(let payload):
            return "Update to \(payload)"
        }
    }
}

// MARK: - Background policy (translucency amendment, Sol — binding)
//
// MINI state background is TRANSLUCENT; the FULL EXPRESSION
// (expanded / end-of-scroll / cue) is fully OPAQUE sheet color.
// Text and icons are always 100% opacity in every state.
//
// iOS choice: the mini background is rendered with the native
// Material (.regular) tinted toward the sheet color by layering
// the sheet color at the policy opacity over it — Material tinting
// via a hand-rolled alpha fought the native material's own
// vibrancy, so the shipped recipe is Material + sheet-color layer
// at 78% (dark) / 85% (light), exactly the fallback the amendment
// allows. The policy below is pure and unit-tested; the view only
// renders what it returns.

enum WearIveRunBackgroundStyle: Equatable {
    /// Native Material + sheet-color layer at `sheetOpacity`.
    case translucentMaterial(sheetOpacity: Double)
    /// Fully opaque sheet color.
    case opaqueSheet
}

enum WearIveRunBackgroundPolicy {
    static func style(
        for presentation: WearIveRunPresentation,
        isDark: Bool
    ) -> WearIveRunBackgroundStyle {
        switch presentation {
        case .mini:
            return .translucentMaterial(sheetOpacity: isDark ? 0.78 : 0.85)
        case .expanded, .endOfScrollCue:
            return .opaqueSheet
        }
    }
}

// MARK: - View

struct WearIveRunMiniBarView: View {
    let state: WearIveRunState
    @State private var presentation: WearIveRunPresentation = .mini
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        // Pre-add renders nothing — the dock's guard also checks,
        // and this is the second line of defense.
        if state != .preAdd {
            switch presentation {
            case .mini:
                miniBar
            case .expanded, .endOfScrollCue:
                expandedBar
            }
        }
    }

    // The wordmark. No Great Vibes asset exists in the bundle
    // (checked 2026-11-02: no script font file in the app target),
    // so it renders in a script-style system fallback; bundling the
    // genuine font is a flagged follow-up. This view never renders
    // in this build, so no user sees the fallback.
    private var wordmarkView: some View {
        Text(WearIveRunMiniBarModel.wordmark)
            .font(.custom("Snell Roundhand", size: 17).weight(.bold))
            .foregroundColor(AppTheme.inkPrimary)
    }

    private var miniBar: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                wordmarkView
                if let line = WearIveRunNarration.line(for: state) {
                    narrationText(line)
                        .font(.appFootnote)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            // Two grayed 14pt icons with >=44pt hit areas.
            Button {
                presentation = .expanded
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Expand")
            Button {
                // Remove is inert in this build: there is no
                // collection to remove from. The future feature
                // wires its quiet Remove here.
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background { miniBackground }
    }

    /// The narration line; an update's payload carries the pine
    /// tint, the rest is ink. Text is always 100% opacity.
    private func narrationText(_ line: String) -> Text {
        if case .updated(let payload) = state {
            return Text("Update to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload).foregroundColor(AppTheme.primaryColor)
        }
        return Text(line).foregroundColor(AppTheme.inkSecondary)
    }

    @ViewBuilder private var miniBackground: some View {
        let style = WearIveRunBackgroundPolicy.style(
            for: presentation,
            isDark: colorScheme == .dark
        )
        switch style {
        case .translucentMaterial(let sheetOpacity):
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(AppTheme.sheet.opacity(sheetOpacity))
            }
        case .opaqueSheet:
            Rectangle().fill(AppTheme.sheet)
        }
    }

    /// The full expression: opaque sheet color, thumbnail slot,
    /// entry data, quiet Remove.
    private var expandedBar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                // Thumbnail slot (the future collection's entry art).
                RoundedRectangle(cornerRadius: 10)
                    .fill(AppTheme.cardOnSheet)
                    .frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 2) {
                    wordmarkView
                    if let line = WearIveRunNarration.line(for: state) {
                        narrationText(line)
                            .font(.appFootnote)
                    }
                }
                Spacer(minLength: 0)
                Button {
                    presentation = .mini
                } label: {
                    Image(systemName: "arrow.down.right.and.arrow.up.left")
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.inkMuted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Collapse")
            }
            Button("Remove") {
                // Inert in this build (no collection exists).
            }
            .font(.appFootnote)
            .foregroundColor(AppTheme.inkSecondary)
        }
        .padding(16)
        .background(AppTheme.sheet)
    }
}
