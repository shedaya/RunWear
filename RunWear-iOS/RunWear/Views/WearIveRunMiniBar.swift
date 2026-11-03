import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25 scaffolding; RW-26 full behavior; WS-3 one machine)
//
// The bar is the collection's doorway (no tab bar — notes §1):
// - absent before today's entry exists;
// - collapsed at rest, semi-transparent (light 85% / dark 82% sheet
//   tint layered over .regularMaterial; text/icons 100%);
// - expanded at end-of-scroll, on tap, and on the creation/update
//   cue — with ONE gentle pulse + a soft haptic, then quiet;
// - two grayed right-end controls: expand/collapse and trash, the
//   SAME controls with the SAME semantics in both resting states.
//   Trash removes today's entry in one tap, no confirmation; the
//   bar leaves until a later generation re-creates the entry.
// The narration pattern: the script wordmark leads, a verb, and
// the payload in pine.
//
// WS-3 (RC-3): presentation is owned by ONE state machine
// (`WearIveRunElementMachine` — ABSENT / MINI / EXPANDED / CUE)
// whose driver table is the only transition source. This view
// feeds it events and executes its effects; it holds no
// presentation logic of its own. The pre-WS-3 end-of-scroll
// LATCH (expansion with no collapse counterpart) is retired:
// arrival at the end expands, departure collapses, symmetric.

enum WearIveRunState: Equatable {
    /// No entry for today yet — the bar does not render.
    case preAdd
    /// Today's entry was created at a place and moment.
    case added(place: String, moment: String)
    /// Today's entry was re-settled to a new payload.
    case updated(payload: String)
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
// iOS); the FULL EXPRESSION (EXPANDED and its timed presentation,
// CUE) is fully opaque. Text and icons are always 100% opacity.

enum WearIveRunBackgroundStyle: Equatable {
    case translucentMaterial(sheetOpacity: Double)
    case opaqueSheet
}

enum WearIveRunBackgroundPolicy {
    static func style(
        for state: WearIveRunElementState,
        isDark: Bool
    ) -> WearIveRunBackgroundStyle {
        switch state {
        case .mini:
            return .translucentMaterial(sheetOpacity: isDark ? 0.82 : 0.85)
        case .expanded, .cue:
            return .opaqueSheet
        case .absent:
            // Not rendered; the opaque case is the safe default.
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
    /// Bumped ONLY on a cue-worthy landing (WS-3): the day's
    /// creation, or a settle that changed the entry's KIT. A
    /// settle that changed only weather numbers or the hero image
    /// is an asynchronous landing — it is recorded below, but it
    /// never bumps this counter, so it can never move the element
    /// (D-07's abolished drivers, enforced at the source via
    /// `WearIveRunLandingClassifier`).
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

    /// Record the day's settled version. The store always takes
    /// the settle (the saved entry is the day's LAST settled
    /// version); what the classification decides is only whether
    /// the element hears about it:
    /// - created / kit-changed → the cue counter bumps;
    /// - weather-only or hero-only change → silent (an async
    ///   landing is not a driver);
    /// - identical → nothing at all.
    func recordSettled(_ entry: WearIveRunEntry) {
        let existing = entries.first { $0.id == entry.id }
        let classification = WearIveRunLandingClassifier.classify(
            existing: existing, incoming: entry
        )
        guard classification != .identical else { return }
        store.record(entry)
        entries = store.entries
        switch classification {
        case .created:
            narratedEntryID = entry.id
            todaysEntryWasUpdated = false
            cueCount += 1
        case .kitUpdated:
            if narratedEntryID == entry.id {
                todaysEntryWasUpdated = true
            }
            cueCount += 1
        case .silentUpdate, .identical:
            break
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
    /// Cue-worthy landing counter (creation / kit update only) —
    /// a change is fed to the machine as a landing event.
    let cueCount: Int
    /// The parent scroll's end-of-scroll signal. BOTH edges are
    /// fed to the machine: arrival expands, departure collapses.
    let isAtEndOfScroll: Bool
    /// True while any sheet is presented over home (WS-5 owns the
    /// sheets; this view only consumes their presented state):
    /// the element dims WITH the home backdrop (D-11).
    var isDimmedBySheet: Bool = false
    let onOpenCollection: () -> Void
    let onTrash: () -> Void

    /// The ONE state holder for this element (WS-3).
    @State private var machine = WearIveRunElementMachine()
    /// Sensory-feedback triggers: incremented ONLY by the
    /// machine's effects, so each haptic fires exactly once per
    /// cause (§1.7) and never for a silent transition.
    @State private var cueHapticNonce = 0
    @State private var trashHapticNonce = 0
    /// The cue pulse's scale (1.00 → 1.03 → 1.00 over 900 ms).
    @State private var pulseScale: CGFloat = 1
    @State private var isPulsing = false
    /// The cue's rise into place (12 pt → 0 as it enters CUE).
    @State private var riseOffset: CGFloat = 0
    /// Generation of the pending cue timer: bumped whenever the
    /// timer is (re)started or cancelled, so a stale timer can
    /// never fire into a state that has moved on.
    @State private var cueTimerGeneration = 0
    /// Guards the compose-time reconciliation so it runs once.
    @State private var didReconcileInitialState = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if state != .preAdd {
            Group {
                switch machine.state {
                case .mini:
                    miniBar
                case .expanded, .cue:
                    expandedBar
                case .absent:
                    // The parent stops composing the dock when the
                    // entry goes away; an in-flight trash lands
                    // here for its collapse animation.
                    EmptyView()
                }
            }
            .scaleEffect(reduceMotion ? 1 : pulseScale)
            .shadow(
                color: AppTheme.pinePayload.opacity(isPulsing && !reduceMotion ? 0.35 : 0),
                radius: 12
            )
            .offset(y: reduceMotion ? 0 : riseOffset)
            .animation(.smooth, value: machine.state)
            // D-11: the element dims with the home backdrop while
            // any sheet is up, and does not answer touches through
            // the backdrop.
            .opacity(WearIveRunElementDimming.opacity(sheetPresented: isDimmedBySheet))
            .allowsHitTesting(WearIveRunElementDimming.isInteractive(sheetPresented: isDimmedBySheet))
            // §1.7: the cue's soft haptic and the trash's light
            // haptic are the ONLY sounds this surface makes.
            // Expand/collapse and scrolling are silent.
            .sensoryFeedback(.impact(flexibility: .soft), trigger: cueHapticNonce)
            .sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)
            .onAppear { reconcileInitialState() }
            .onChange(of: cueCount) { oldValue, newValue in
                guard newValue > oldValue else { return }
                // A cue-worthy landing: creation when the machine
                // holds no entry, a user-caused update otherwise.
                send(machine.state == .absent ? .entryCreated : .userCausedUpdate)
            }
            .onChange(of: isAtEndOfScroll) { _, atEnd in
                // BOTH edges — the symmetric rule that retires the
                // latch. In CUE and ABSENT these are machine
                // no-ops by table, so a scroll can never disturb
                // a cue.
                send(atEnd ? .scrolledToEnd : .scrolledAwayFromEnd)
            }
        }
    }

    // MARK: Machine plumbing

    /// Compose-time reconciliation, once: an element composing
    /// with a landing already counted presents that landing's cue;
    /// an element composing over an entry that was already on disk
    /// (cold start) starts at MINI, silently.
    private func reconcileInitialState() {
        guard !didReconcileInitialState else { return }
        didReconcileInitialState = true
        if machine.state == .absent {
            send(cueCount > 0 ? .entryCreated : .entryAlreadyPresent)
        }
    }

    /// Feed one event to the machine and execute its effects
    /// exactly once.
    private func send(_ event: WearIveRunElementEvent) {
        let wasInCue = machine.state == .cue
        let effects = machine.send(event)
        if machine.state == .cue && !wasInCue {
            // Entering CUE: rise 12 pt into place (§1.2, `.smooth`).
            riseOffset = WearIveRunElementMachine.cueRisePoints
            withAnimation(.smooth) { riseOffset = 0 }
        }
        for effect in effects {
            switch effect {
            case .fireCuePulseAndSoftHaptic:
                cueHapticNonce += 1
                runCuePulse()
            case .startCueTimer:
                startCueTimer()
            case .cancelCueTimer:
                cueTimerGeneration += 1
            case .deleteTodaysEntry:
                onTrash()
            case .fireTrashHaptic:
                trashHapticNonce += 1
            }
        }
    }

    /// The ONE cue pulse: scale 1.00 → 1.03 → 1.00, 900 ms total,
    /// ease-in-out, exactly once — never a loop (§1.2). Disabled
    /// under Reduce Motion (§1.0 rule 4); the state change and the
    /// haptic still happen.
    private func runCuePulse() {
        guard !reduceMotion else { return }
        isPulsing = true
        let half = WearIveRunElementMachine.cuePulseDurationSeconds / 2
        withAnimation(.easeInOut(duration: half)) {
            pulseScale = WearIveRunElementMachine.cuePulseScale
        } completion: {
            withAnimation(.easeInOut(duration: half)) {
                pulseScale = 1
            } completion: {
                isPulsing = false
            }
        }
    }

    /// (Re)start the cue's 3.0 s timer. The captured generation
    /// invalidates this timer if anything restarts or cancels it
    /// first; a timer that does fire sends `cueTimedOut`, which
    /// the machine itself no-ops outside CUE.
    private func startCueTimer() {
        cueTimerGeneration += 1
        let generation = cueTimerGeneration
        Task { @MainActor in
            try? await Task.sleep(
                nanoseconds: UInt64(WearIveRunElementMachine.cueDurationSeconds * 1_000_000_000)
            )
            guard generation == cueTimerGeneration else { return }
            send(.cueTimedOut)
        }
    }

    // MARK: Subviews

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
        .onTapGesture { send(.tapMiniBarBody) }
    }

    /// Two tiny grayed controls at the right end: the toggle and
    /// trash. The SAME two controls, with the same semantics, in
    /// MINI, EXPANDED, and CUE (D-10): the toggle's glyph flips
    /// with the machine's `toggleIsExpand`; trash always deletes.
    private var barControls: some View {
        HStack(spacing: 4) {
            Button {
                send(.tapToggleControl)
            } label: {
                Image(systemName: machine.toggleIsExpand
                      ? "arrow.up.left.and.arrow.down.right"
                      : "arrow.down.right.and.arrow.up.left")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(machine.toggleIsExpand ? "Expand" : "Collapse")
            Button {
                send(.tapTrash)
            } label: {
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
            for: machine.state,
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
    /// Its text content is present from frame one; the thumbnail
    /// renders ONLY once its image has decoded — no placeholder
    /// graphic and no broken-image frame ever paints (D-12, the
    /// §1.1 decode rule applied to the entry).
    private var expandedBar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                AsyncImage(url: entry.flatMap { URL(string: $0.heroImageReference) }) { phase in
                    if case .success(let image) = phase {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Color.clear
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
