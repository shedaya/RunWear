import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25 scaffolding; RW-26 full behavior)
//
// The bar is the collection's doorway (no tab bar — notes §1):
// - absent before today's entry exists;
// - collapsed at rest, semi-transparent (light 85% / dark 82% sheet
//   tint layered over .regularMaterial; text/icons 100%);
// - expands at end-of-scroll, on tap, and on the creation/update
//   cue — with ONE gentle pulse + a soft haptic, then quiet;
// - two grayed right-end controls: expand and trash. Trash removes
//   today's entry in one tap, no confirmation; the bar leaves
//   until a later generation re-creates the entry.
// The narration pattern: the script wordmark leads, a verb, and
// the payload in pine.

enum WearIveRunState: Equatable {
    /// No entry for today yet — the bar does not render.
    case preAdd
    /// Today's entry was created at a place and moment.
    case added(place: String, moment: String)
    /// Today's entry was re-settled to a new payload.
    case updated(payload: String)
}

enum WearIveRunPresentation: Equatable {
    case mini
    case expanded
    case endOfScrollCue
}

enum WearIveRunMiniBarModel {
    static let wordmark = "Wear I've Run"

    /// The bar's state from today's entry: absent without one;
    /// "Added" on the day's first settle; "Update to" when a later
    /// settle changed the entry the session already narrated.
    ///
    /// WS-4: both payloads derive from the ENTRY, never from a
    /// caller-assembled string — "Added" carries the entry's
    /// place in its simple form (city + abbreviated region), and
    /// "Update to" carries the entry's real payload, its first
    /// kit item. (The pre-WS-4 iOS caller fed "Update to" a
    /// "{n} items · {temp}" summary the entry never said; the
    /// Android holder fed it the raw place + "Today" — D-08 on
    /// both sides, different strings.)
    static func state(
        todaysEntry: WearIveRunEntry?,
        moment: String,
        wasUpdatedThisSession: Bool
    ) -> WearIveRunState {
        guard let entry = todaysEntry else { return .preAdd }
        if wasUpdatedThisSession {
            return .updated(payload: entry.narrationPayload)
        }
        return .added(place: entry.placeDisplayName, moment: moment)
    }
}

// MARK: - Narration formatter

enum WearIveRunNarration {
    /// - preAdd: nil (the bar does not render at all)
    /// - added: "Added {place}, {moment}" — the comma form
    /// - updated: "Update to {payload}" (payload renders pine)
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

    /// The expanded bar's entry line (frame 3 anatomy):
    /// "{Place} · Today — {feels-like} feels like · {n} items".
    /// The place is the simple form (WS-4). This line is the
    /// entry's subtitle, NOT a second narration: it carries the
    /// day form ("Today" — the collection's day label, by design
    /// in frame 3) and the kit count, and it never carries the
    /// narration's payload (place + "Now" on creation, the first
    /// kit item on update), so the expanded card never prints
    /// the narration's payload twice (the D-08 duplication).
    /// The view renders it with NO line limit — it wraps.
    static func expandedLine(for entry: WearIveRunEntry, formattedFeelsLike: String) -> String {
        let items = entry.itemNames.count == 1 ? "1 item" : "\(entry.itemNames.count) items"
        return "\(entry.placeDisplayName) · Today — \(formattedFeelsLike) feels like · \(items)"
    }
}

// MARK: - Background policy (translucency, notes §1)
//
// MINI state is TRANSLUCENT at the settled tints (light 85% / dark
// 82% over the warm surfaces, layered over .regularMaterial on
// iOS); the FULL EXPRESSION (expanded / end-of-scroll / cue) is
// fully opaque. Text and icons are always 100% opacity.

enum WearIveRunBackgroundStyle: Equatable {
    case translucentMaterial(sheetOpacity: Double)
    case opaqueSheet
}

enum WearIveRunBackgroundPolicy {
    static func style(
        for presentation: WearIveRunPresentation,
        isDark: Bool
    ) -> WearIveRunBackgroundStyle {
        switch presentation {
        case .mini:
            return .translucentMaterial(sheetOpacity: isDark ? 0.82 : 0.85)
        case .expanded, .endOfScrollCue:
            return .opaqueSheet
        }
    }
}

// MARK: - Collection model (observable wrapper over the store)

@MainActor
final class WearIveRunCollectionModel: ObservableObject {
    @Published private(set) var entries: [WearIveRunEntry] = []
    /// True when a settle this session replaced today's entry with
    /// different content — the narration's "Update to" case.
    @Published private(set) var todaysEntryWasUpdated = false
    /// Bumped on every creation/update of today's entry — the cue
    /// trigger (expand + one pulse + soft haptic).
    @Published private(set) var cueCount = 0

    private let store: WearIveRunStore
    private var narratedEntryID: String?

    init(store: WearIveRunStore = WearIveRunStore()) {
        self.store = store
        self.entries = store.entries
    }

    func todaysEntry(placeName: String, today: Date = Date()) -> WearIveRunEntry? {
        store.todaysEntry(placeName: placeName, today: today)
    }

    /// Record the day's settled version. The first settle of the
    /// day creates the entry (and fires the cue); a later settle
    /// with different content updates it (and fires the cue); an
    /// identical settle is quiet.
    func recordSettled(_ entry: WearIveRunEntry) {
        let existing = entries.first { $0.id == entry.id }
        store.record(entry)
        entries = store.entries
        if existing == nil {
            narratedEntryID = entry.id
            todaysEntryWasUpdated = false
            cueCount += 1
        } else if existing != entry {
            if narratedEntryID == entry.id {
                todaysEntryWasUpdated = true
            }
            cueCount += 1
        }
    }

    /// The trash control: today's entry at this place goes away in
    /// one tap; the bar leaves with it.
    func removeToday(placeName: String, today: Date = Date()) {
        let calendar = Calendar.current
        let dayStart = calendar.startOfDay(for: today)
        store.remove(dayStart: dayStart, placeName: placeName)
        entries = store.entries
        todaysEntryWasUpdated = false
        narratedEntryID = nil
    }
}

// MARK: - View

struct WearIveRunMiniBarView: View {
    let state: WearIveRunState
    /// Today's entry, for the expanded anatomy; nil only in preAdd.
    let entry: WearIveRunEntry?
    /// The feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    /// Creation/update cue counter — a change expands the bar with
    /// one pulse + soft haptic.
    let cueCount: Int
    /// The parent scroll's end-of-scroll signal.
    let isAtEndOfScroll: Bool
    let onOpenCollection: () -> Void
    let onTrash: () -> Void

    @State private var presentation: WearIveRunPresentation = .mini
    @State private var pulse = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if state != .preAdd {
            Group {
                switch presentation {
                case .mini:
                    miniBar
                case .expanded, .endOfScrollCue:
                    expandedBar
                }
            }
            .scaleEffect(pulse ? 1.02 : 1)
            .animation(.easeInOut(duration: 0.45), value: pulse)
            .sensoryFeedback(.impact(weight: .light), trigger: cueCount)
            .onChange(of: cueCount) { _, _ in
                // The creation/update cue: expand, one gentle
                // pulse, then quiet.
                presentation = .expanded
                pulse = true
                Task {
                    try? await Task.sleep(nanoseconds: 500_000_000)
                    pulse = false
                }
            }
            .onChange(of: isAtEndOfScroll) { _, atEnd in
                if atEnd { presentation = .endOfScrollCue }
            }
        }
    }

    /// The wordmark in the genuine Great Vibes (RW-26: the Snell
    /// Roundhand fallback call site is retired — the font is
    /// bundled and `Font.greatVibes` resolves it).
    private var wordmarkView: some View {
        Text(WearIveRunMiniBarModel.wordmark)
            .font(.greatVibes(size: 20))
            .foregroundColor(AppTheme.inkPrimary)
    }

    private var miniBar: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                wordmarkView
                if let line = WearIveRunNarration.line(for: state) {
                    // One line BY DESIGN (frame 2): the line is
                    // short by construction (WS-4) — wordmark +
                    // verb + a simple place + "Now", or a single
                    // kit item — so the limit never amputates a
                    // raw geocode string at a comma (D-09).
                    narrationText(line)
                        .font(.appFootnote)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            barControls
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background { miniBackground }
        .contentShape(Rectangle())
        .onTapGesture { presentation = .expanded }
    }

    /// Two tiny grayed controls at the right end: expand + trash.
    private var barControls: some View {
        HStack(spacing: 4) {
            Button {
                presentation = presentation == .mini ? .expanded : .mini
            } label: {
                Image(systemName: presentation == .mini
                      ? "arrow.up.left.and.arrow.down.right"
                      : "arrow.down.right.and.arrow.up.left")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(presentation == .mini ? "Expand" : "Collapse")
            Button(action: onTrash) {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove today's entry")
        }
    }

    private func narrationText(_ line: String) -> Text {
        if case .updated(let payload) = state {
            return Text("Update to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload).foregroundColor(AppTheme.pinePayload)
        }
        if case .added(let place, let moment) = state {
            // The payload "{Place}, {Moment}" carries the pine.
            return Text("Added ").foregroundColor(AppTheme.inkSecondary)
                + Text("\(place), \(moment)").foregroundColor(AppTheme.pinePayload)
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

    /// The full expression: opaque, the entry's thumbnail + data,
    /// a door into the collection, and the same grayed controls.
    private var expandedBar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                AsyncImage(url: entry.flatMap { URL(string: $0.heroImageReference) }) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Rectangle().fill(AppTheme.cardOnSheet)
                    }
                }
                .frame(width: 56, height: 56)
                .clipped()
                .cornerRadius(10)

                VStack(alignment: .leading, spacing: 2) {
                    wordmarkView
                    if let entry {
                        // No line limit, by design (WS-4): the
                        // expanded subtitle wraps without limit —
                        // a long simple place name wraps whole
                        // rather than truncating.
                        Text(WearIveRunNarration.expandedLine(
                            for: entry,
                            formattedFeelsLike: formattedFeelsLike
                        ))
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                    }
                }
                Spacer(minLength: 0)
                barControls
            }
            Button(action: onOpenCollection) {
                Text("Open the collection")
                    .font(.appFootnote.weight(.semibold))
                    .foregroundColor(AppTheme.pinePayload)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(AppTheme.sheet)
    }
}
