import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25 scaffolding; RW-26 full behavior; WS-3 one machine)
//
// The bar is the collection's doorway (no tab bar — notes §1):
// - absent before the session's entry exists (GEN-3: one entry
//   per app session, updated in place — see RW26CollectionModels);
// - collapsed at rest, semi-transparent (light 85% / dark 82% sheet
//   tint layered over .regularMaterial; text/icons 100%);
// - expanded at end-of-scroll, on tap, and on the creation/update
//   cue — with ONE gentle pulse + a soft haptic, then quiet;
// - two grayed right-end controls: expand/collapse and trash, the
//   SAME controls with the SAME semantics in both resting states.
//   Trash removes the session's entry in one tap, no confirmation;
//   the bar leaves until a later generation re-creates the entry.
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
    /// GEN-5 (Addendum 8): the moment is the structural
    /// MomentReading — no free-text moment exists.
    case added(place: String, moment: MomentReading)
    /// Today's entry was re-settled to a new payload.
    /// GEN-5 (Addendum 8): the payload is the structural
    /// NarrationPayload (place or moment) — a garment payload
    /// is unrepresentable in this state.
    case updated(payload: NarrationPayload)
}

enum WearIveRunMiniBarModel {
    static let wordmark = "Wear I've Run"

    /// The bar's state from the session's entry: absent without
    /// one; "Added" on the session's first settle; "Update to"
    /// when a later in-session settle changed the entry the
    /// session already narrated (narration-from-diff, X-4).
    ///
    /// WS-4: both payloads derive from the ENTRY, never from a
    /// caller-assembled string — "Added" carries the entry's
    /// place in its simple form (city + abbreviated region), and
    /// "Update to" carries the entry's stored payload (GEN-5:
    /// what changed — the place or the moment reading; the WS-4
    /// first-kit-item payload was the gen-4 garment defect).
    /// (The pre-WS-4 iOS caller fed "Update to" a
    /// "{n} items · {temp}" summary the entry never said; the
    /// Android holder fed it the raw place + "Today" — D-08 on
    /// both sides, different strings.)
    static func state(
        todaysEntry: WearIveRunEntry?,
        moment: MomentReading,
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
            return "Added \(place), \(moment.rendered)"
        case .updated(let payload):
            return "Update to \(payload.rendered)"
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
        // GEN-5 (RW-29 FA-34): the expanded anatomy carries the
        // OUTFIT LINE — the item names themselves — where build
        // 25 stopped at the count. The count form remains only
        // for an entry with no recorded items.
        if entry.itemNames.isEmpty {
            let items = entry.itemNames.count == 1 ? "1 item" : "\(entry.itemNames.count) items"
            return "\(entry.placeDisplayName) · Today — \(formattedFeelsLike) feels like · \(items)"
        }
        return "\(entry.placeDisplayName) · Today — \(formattedFeelsLike) feels like · \(entry.itemNames.joined(separator: " · "))"
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
    /// True when a settle in this session replaced the session's
    /// entry with different narrated content (a kit change or a
    /// move to a new place) — the narration's "Update to" case.
    /// DERIVED, never stored here (GEN-3, census Y-5): the fact
    /// lives on the entry itself (`wasUpdatedInSession`, written
    /// in the same store write as the content it describes), and
    /// this reads it back off the presented entry — the model's
    /// session entry when there is one, else the entry this
    /// model narrated. A separately stored flag is exactly what
    /// desynchronised on device in build 21. (The property's
    /// name predates the session rule; its scope was always
    /// "this session" — see `wasUpdatedThisSession` at the
    /// narration seam.)
    var todaysEntryWasUpdated: Bool {
        if let entry = sessionEntry() { return entry.wasUpdatedInSession }
        if let narratedEntryID {
            return entries.first { $0.id == narratedEntryID }?.wasUpdatedInSession ?? false
        }
        return false
    }
    /// Bumped ONLY on a cue-worthy landing (WS-3): the session's
    /// creation, a settle that moved it to a new PLACE (GEN-3),
    /// or a settle that changed its MOMENT (GEN-5, RW-28 C-7 —
    /// the narration names place and time only). A settle that
    /// changed only the kit, weather numbers, or the hero image
    /// is an asynchronous landing — it is recorded below, but it
    /// never bumps this counter, so it can never move the
    /// element (D-07's abolished drivers, enforced at the source
    /// via `WearIveRunLandingClassifier`). GEN-5 supersedes the
    /// WS-4 kit cue: a kit-only re-solve narrates nothing, so it
    /// cues nothing.
    @Published private(set) var cueCount = 0

    /// The identity of the app session this model records for —
    /// the process session by default (`WearIveRunSession`), so
    /// every home surface in one launch shares the one session
    /// entry; tests inject their own to simulate sessions.
    let sessionID: String

    private let store: WearIveRunStore
    private var narratedEntryID: String?

    init(store: WearIveRunStore = WearIveRunStore(), sessionID: String = WearIveRunSession.currentID) {
        self.store = store
        self.sessionID = sessionID
        self.entries = store.entries
    }

    func todaysEntry(placeName: String, today: Date = Date()) -> WearIveRunEntry? {
        store.todaysEntry(placeName: placeName, today: today)
    }

    /// The dock's entry (W-15): today's entry by date, whichever
    /// place it was recorded at — the entry the element presents
    /// and narrates, independent of the place being viewed.
    /// Retained for the gen-2 pins; the dock itself presents
    /// `sessionEntry()` since the GEN-3 session rule.
    func todaysEntry(today: Date = Date()) -> WearIveRunEntry? {
        store.todaysEntry(today: today)
    }

    /// The dock's entry (GEN-3 session rule): THIS session's
    /// entry — the one entry the session has been settling in
    /// place — independent of the place being viewed and of the
    /// calendar day it last settled on.
    func sessionEntry() -> WearIveRunEntry? {
        store.sessionEntry(sessionID: sessionID)
    }

    /// Record the session's settled version. The store always
    /// takes the settle (the saved entry is the session's LAST
    /// settled version — the upsert keys on the session, so an
    /// in-session settle replaces, never accumulates); what the
    /// classification decides is only whether the element hears
    /// about it:
    /// - created / place-moved / moment-changed → the cue
    ///   counter bumps (a place move after the first add
    ///   narrates "Update to…", never a fresh "Added …" —
    ///   census X-4; a moment change narrates the new reading —
    ///   GEN-5 items 8/9);
    /// - kit-only, weather-only, or hero-only change → silent
    ///   (an async landing is not a driver; GEN-5 C-7: garments
    ///   are never narration subjects, so a kit-only re-solve
    ///   has nothing to say);
    /// - identical → nothing at all.
    func recordSettled(_ entry: WearIveRunEntry) {
        let existing = entries.first { $0.id == entry.id }
        let classification = WearIveRunLandingClassifier.classify(
            existing: existing, incoming: entry
        )
        guard classification != .identical else { return }
        // Y-5 atomicity: the narration kind is resolved HERE and
        // stamped onto the entry, and the ONE store write below
        // commits place, kit, weather, hero, and narration kind
        // together — no facet of the entry can land without the
        // others. A silent settle preserves the kind it found
        // (weather moving under a settled narration does not
        // rewrite the narration).
        // GEN-5 (RW-28, C-7): the payload is computed from the
        // DIFF and stamped with the kind — place name on a move,
        // the new moment reading on a time change. Never a
        // garment: a kit-only settle preserves the narration it
        // found, exactly like a weather-only settle.
        let narrationUpdated: Bool
        let narrationPayload: NarrationPayload?
        switch classification {
        case .created:
            narrationUpdated = false
            narrationPayload = nil
        case .placeUpdated, .momentUpdated:
            narrationUpdated = true
            narrationPayload = existing.map {
                WearIveRunNarrationRules.updatePayload(existing: $0, incoming: entry)
            }
        case .kitUpdated, .silentUpdate, .identical:
            narrationUpdated = existing?.wasUpdatedInSession ?? false
            narrationPayload = existing?.updatePayload
        }
        // GEN-5 (RW-29 FA-36): the session's FIRST settle at a
        // day+place supersedes a legacy (pre-session) entry for
        // the same day+place — the day keeps ONE entry, never a
        // legacy row beside the session's.
        if classification == .created {
            store.removeLegacyTwin(of: entry)
        }
        store.record(entry.settingNarration(wasUpdated: narrationUpdated, payload: narrationPayload))
        entries = store.entries
        switch classification {
        case .created:
            narratedEntryID = entry.id
            cueCount += 1
        case .placeUpdated, .momentUpdated:
            cueCount += 1
        case .kitUpdated, .silentUpdate, .identical:
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
        // The narration kind needs no reset here: it lives on
        // the entry, and the entry is gone (Y-5 derivation).
        narratedEntryID = nil
    }

    /// The trash control under the session rule: the PRESENTED
    /// entry — this session's — goes away in one tap, whatever
    /// place it settled at; the bar leaves with it, and a later
    /// settle in the same session re-creates the session's entry
    /// (a fresh "Added", as after any trash).
    func removeSessionEntry() {
        guard let current = sessionEntry() else { return }
        store.remove(id: current.id)
        entries = store.entries
        narratedEntryID = nil
    }
}

// MARK: - Morph mathematics (motion decision, 2026-11-04)
//
// The MINI ⇄ EXPANDED transition is ONE continuous morph: a
// single `expansionProgress` (0 = MINI, 1 = EXPANDED) drives
// height, corner radius, background tint opacity, and expanded
// content opacity together — there are no per-property
// animations to desynchronize (decision §2, guardrail §7.6).
// The background is the settled iOS treatment: the tint layer
// over `.regularMaterial`; the morph animates the TINT layer's
// opacity (85% light / 82% dark → 1.00) and never the material's
// blur. The pure functions here are what the unit tests pin;
// the view only feeds them the one progress value.
//
// Driver springs (decision §4, iOS column):
// - cue entry: .spring(response 0.46, dampingFraction 0.80) —
//   ≈1.5% overshoot, the ONLY overshoot in the app, inside the
//   ≤3% / ≤3 pt cap by construction; the pulse + soft haptic
//   fire at the SETTLE (first frame progress reaches 1.00),
//   never at the driver;
// - tap: .spring(response 0.42, dampingFraction 0.92) — no
//   visible overshoot;
// - end-of-scroll: the .smooth case — critically damped,
//   response ≈0.34 (expressed as the equivalent spring, since
//   SwiftUI's .smooth preset takes no response parameter);
// - collapse: .spring(response 0.34, dampingFraction 1.0).
// No bouncy or snappy presets anywhere on this element.
// Duration gates (decision §4): cue ≤500 ms, tap ≤450 ms,
// scroll ≤340 ms, collapse ≤320 ms. Reduced Motion replaces
// the morph with a ≤150 ms crossfade: no overshoot, no pulse,
// haptic retained, chevron swaps at the midpoint.

enum WearIveRunMorphDriver: Equatable {
    case cue
    case tap
    case scroll
    case collapse
    case reducedMotion
}

enum WearIveRunMorph {
    /// Spring response per driver (decision §4).
    static func springResponse(for driver: WearIveRunMorphDriver) -> Double {
        switch driver {
        case .cue: return 0.46
        case .tap: return 0.42
        case .scroll, .collapse: return 0.34
        case .reducedMotion: return 0
        }
    }

    /// Spring damping per driver. Overshoot exists ONLY where
    /// damping < 1 with intent — the cue, and only the cue
    /// (tap's 0.92 is the decision's "hair of liveliness" with
    /// no visible settle past target).
    static func springDampingFraction(for driver: WearIveRunMorphDriver) -> Double {
        switch driver {
        case .cue: return 0.80
        case .tap: return 0.92
        case .scroll, .collapse: return 1.0
        case .reducedMotion: return 1.0
        }
    }

    /// The one driver permitted any overshoot (decision §3).
    static func permitsOvershoot(_ driver: WearIveRunMorphDriver) -> Bool {
        driver == .cue
    }

    /// Duration gates, seconds (decision §4): a transition
    /// still visibly moving outside its window is a defect.
    static func durationGateSeconds(for driver: WearIveRunMorphDriver) -> Double {
        switch driver {
        case .cue: return 0.50
        case .tap: return 0.45
        case .scroll: return 0.34
        case .collapse: return 0.32
        case .reducedMotion: return 0.15
        }
    }

    /// The Reduced Motion crossfade's length (≤150 ms, §6).
    static let reducedMotionDurationSeconds: Double = 0.15

    /// The animation a driver runs, built from the pinned
    /// constants above — one animation of the one progress
    /// value, never separate per-property animations.
    static func animation(for driver: WearIveRunMorphDriver) -> Animation {
        switch driver {
        case .reducedMotion:
            return .easeInOut(duration: reducedMotionDurationSeconds)
        case .cue, .tap, .scroll, .collapse:
            return .spring(
                response: springResponse(for: driver),
                dampingFraction: springDampingFraction(for: driver)
            )
        }
    }

    private static func clamped(_ progress: Double) -> Double {
        min(1, max(0, progress))
    }

    /// Background tint opacity: the MINI tint (85% light /
    /// 82% dark, the BackgroundPolicy values) → 1.00, linear
    /// in progress, clamped at 1.00 — so the cue's overshoot
    /// expresses itself in height alone, never in opacity.
    static func tintOpacity(progress: Double, isDark: Bool) -> Double {
        let mini = isDark ? 0.82 : 0.85
        return mini + (1 - mini) * clamped(progress)
    }

    /// Corner radius tokens. The iOS dock is a full-width strip
    /// in BOTH states (it hosts in the safe-area inset, not as a
    /// floating card), so its radius token is 0 at both ends and
    /// the interpolation is constant — the decision's stated
    /// constant case ("linear in progress; constant if the
    /// tokens are equal"). The function still derives the
    /// radius from the one progress value, so a future token
    /// change animates with the same morph.
    static let miniCornerRadius: Double = 0
    static let expandedCornerRadius: Double = 0
    static func cornerRadius(progress: Double) -> Double {
        miniCornerRadius + (expandedCornerRadius - miniCornerRadius) * clamped(progress)
    }

    /// Height: linear in progress between the two MEASURED
    /// content heights (never hardcoded in the view).
    static func height(mini: Double, expanded: Double, progress: Double) -> Double {
        mini + (expanded - mini) * clamped(progress)
    }

    /// Expanded-only content opacity: windowed — ramps over
    /// progress 0.45 → 1.00 on expansion (and, by the same
    /// function in reverse, out over 1.00 → 0.55 of the collapse
    /// travel), so the closing card never displays compressed
    /// text and the lines are fully in before the settle.
    static func expandedContentOpacity(progress: Double) -> Double {
        clamped((clamped(progress) - 0.45) / 0.55)
    }

    /// Expanded-only content drift: ≤4 pt upward as it fades
    /// in (translate +4 → 0 across the opacity window).
    static func expandedContentOffset(progress: Double) -> Double {
        4 * (1 - expandedContentOpacity(progress: progress))
    }

    /// The chevron's rotation: ONE glyph rotating 180° with
    /// progress — continuous, no icon swap. Under Reduced
    /// Motion there is no rotation: the caller swaps the
    /// glyph's orientation at the crossfade midpoint, which
    /// this function expresses as the settled orientation for
    /// the target state (`settledFullExpression`).
    static func chevronRotationDegrees(
        progress: Double,
        reduceMotion: Bool,
        settledFullExpression: Bool
    ) -> Double {
        if reduceMotion { return settledFullExpression ? 180 : 0 }
        return 180 * clamped(progress)
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
    /// THE morph value (motion decision §2): 0 = MINI,
    /// 1 = EXPANDED/CUE. Every visual property of the bar is a
    /// pure function of this one value via `WearIveRunMorph`.
    @State private var expansionProgress: Double = 0
    /// Measured natural heights of the mini and full-expression
    /// contents (the morph's height endpoints — measured, never
    /// hardcoded, decision §2).
    @State private var miniHeight: Double = 0
    @State private var expandedHeight: Double = 0
    /// Generation of the in-flight morph: bumped on every new
    /// driver, so a superseded animation's completion can never
    /// fire a stale settle, latch release, or trash.
    @State private var morphGeneration = 0
    /// The morph generation whose completion owes the cue's
    /// pulse + soft haptic — they fire at the SETTLE, not at
    /// the driver (decision §2 / guardrail §7.8). Nil when no
    /// settle is owed; cancelled with the cue timer if the user
    /// touches the element first.
    @State private var pendingCueSettleGeneration: Int?
    /// Scroll-latch (§5.3): the generation of an in-flight
    /// scroll-driven transition. While it stands, the opposite
    /// scroll edge is QUEUED, not started — one move per
    /// threshold crossing, never a breathing card.
    @State private var scrollLatchGeneration: Int?
    @State private var queuedScrollEvent: WearIveRunElementEvent?
    /// Trash collapse (§1.2, unchanged): 1 → 0 over 250 ms,
    /// height shrinking while fading; the entry is deleted only
    /// when the collapse completes, so the dock's space is
    /// released in the same animation.
    @State private var trashScale: Double = 1
    /// Reduced Motion chevron: flipped at the crossfade
    /// midpoint instead of rotating (§6).
    @State private var reducedChevronFull = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if state != .preAdd {
            morphBar
                .scaleEffect(reduceMotion ? 1 : pulseScale)
                .shadow(
                    color: AppTheme.pinePayload.opacity(isPulsing && !reduceMotion ? 0.35 : 0),
                    radius: 12
                )
                .offset(y: reduceMotion ? 0 : riseOffset)
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
                .onChange(of: isDimmedBySheet) { _, dimmed in
                    // §5.4: a sheet presenting mid-transition wins —
                    // the morph finishes instantly to its target
                    // state underneath the dim.
                    if dimmed { finishMorphInstantly() }
                }
        }
    }

    // MARK: The morph bar (one view, one progress value)

    /// The dock's single rendered form. Two hidden measurers
    /// (the mini content and the full-expression content, at
    /// natural size) supply the height endpoints; the visible
    /// content is the union anatomy — the header (wordmark +
    /// narration + controls) is present in both states and
    /// never moves or fades, and the expanded-only block below
    /// it fades/drifts on the windowed ramp. The container's
    /// height, background tint opacity, and corner radius are
    /// all functions of `expansionProgress`.
    private var morphBar: some View {
        let targetHeight: Double? = (miniHeight > 0 && expandedHeight > 0)
            ? WearIveRunMorph.height(
                mini: miniHeight, expanded: expandedHeight,
                progress: expansionProgress
              ) * trashScale
            : nil
        let visible = machine.state != .absent || trashScale < 1
        return ZStack(alignment: .top) {
            // Measurers: natural-size copies, never painted,
            // never hit-tested. They exist so the morph's height
            // endpoints are the contents' own measured heights
            // at the user's current type size.
            morphContent(showsExpandedBlock: false, progress: 0)
                .fixedSize(horizontal: false, vertical: true)
                .opacity(0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .onGeometryChange(for: Double.self) { proxy in
                    proxy.size.height
                } action: { _, height in
                    if height > 0 { miniHeight = height }
                }
            morphContent(showsExpandedBlock: true, progress: 1)
                .fixedSize(horizontal: false, vertical: true)
                .opacity(0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .onGeometryChange(for: Double.self) { proxy in
                    proxy.size.height
                } action: { _, height in
                    if height > 0 { expandedHeight = height }
                }
            if visible {
                morphContent(showsExpandedBlock: true, progress: expansionProgress)
                    .fixedSize(horizontal: false, vertical: true)
                    .opacity(trashScale)
            }
        }
        .frame(height: visible ? targetHeight.map { CGFloat($0) } : 0)
        .background {
            // The tint layer over .regularMaterial: the morph
            // animates the TINT's opacity (progress-driven);
            // the material itself is never animated (decision
            // §4 — no blur animation).
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(AppTheme.sheet.opacity(
                    WearIveRunMorph.tintOpacity(
                        progress: expansionProgress,
                        isDark: colorScheme == .dark
                    )
                ))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: expansionProgress)))
    }

    /// The bar's content at a given morph progress. The mini
    /// measurer omits the expanded block from its LAYOUT (an
    /// opacity-0 block would still measure at full height and
    /// collapse the morph's travel to zero); the visible
    /// content always carries the block, clipped by the
    /// container's progress-driven height.
    @ViewBuilder
    private func morphContent(showsExpandedBlock: Bool, progress: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            headerRow
            if showsExpandedBlock {
                expandedBlock(progress: progress)
            }
        }
    }

    /// The continuity anchor (decision §2): wordmark, narration,
    /// and both controls — present in both states, never moving,
    /// never fading, never scaling. The narration is the SAME
    /// line in both states, so no mid-morph text swap is needed.
    private var headerRow: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                wordmarkView
                if let line = WearIveRunNarration.line(for: state) {
                    // GEN-5 (RW-29 FA-48) SUPERSEDES the frame-2
                    // one-line limit: a long simple place in the
                    // payload was end-truncated at one line on
                    // Sol's device. The narration may wrap to two
                    // lines — the payload is never amputated.
                    narrationText(line)
                        .font(.appFootnote)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
            barControls
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture { send(.tapMiniBarBody) }
    }

    /// The expanded-only block: the entry's thumbnail + its
    /// frame-3 line, and the explicit jump into the full
    /// collection (W-1: the expanded card must SHOW the way in —
    /// a labelled door with a chevron, not a bare card tap).
    /// It fades in on the windowed ramp (progress 0.45 → 1.00)
    /// with a ≤4 pt upward drift; on collapse it is gone before
    /// the card has visibly shrunk. The thumbnail renders ONLY
    /// once its image has decoded — no placeholder graphic and
    /// no broken-image frame ever paints (D-12).
    private func expandedBlock(progress: Double) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                // GEN-5 (RW-29 FA-45): the thumbnail renders
                // through HeroPhoto's top-anchored crop — build
                // 25's bare centre fill could cut the head here
                // as surely as on the poster.
                AsyncImage(url: entry.flatMap { URL(string: $0.heroImageReference) }) { phase in
                    if case .success(let image) = phase {
                        HeroPhoto(image: image, width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        Color.clear
                            .frame(width: 56, height: 56)
                    }
                }

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
                Spacer(minLength: 0)
            }
            Button(action: onOpenCollection) {
                HStack(spacing: 6) {
                    Text("Open the collection")
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(AppTheme.pinePayload)
                    Image(systemName: "chevron.right")
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(AppTheme.pinePayload)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open the full Wear I've Run collection")
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
        .opacity(WearIveRunMorph.expandedContentOpacity(progress: progress))
        .offset(y: WearIveRunMorph.expandedContentOffset(progress: progress))
        // GEN-5 (RW-29 FA-33): the expanded block's body is a
        // tap surface — tapping it settles the bar back to
        // mini (the machine's new transition). The "Open the
        // collection" button inside keeps its own tap.
        .contentShape(Rectangle())
        .onTapGesture { send(.tapMiniBarBody) }
    }

    /// The wordmark in the genuine Great Vibes (RW-26: the Snell
    /// Roundhand fallback call site is retired — the font is
    /// bundled and `Font.greatVibes` resolves it).
    private var wordmarkView: some View {
        Text(WearIveRunMiniBarModel.wordmark)
            .font(.greatVibes(size: 20))
            .foregroundColor(AppTheme.inkPrimary)
    }

    /// Two tiny grayed controls at the right end: the toggle and
    /// trash. The SAME two controls, with the same semantics, in
    /// MINI, EXPANDED, and CUE (D-10). The toggle is ONE chevron
    /// glyph that ROTATES 180° with the morph's progress
    /// (decision §2) — no icon swap, no pop; under Reduced
    /// Motion it swaps orientation at the crossfade midpoint.
    /// Trash is present in both states, constant, unanimated.
    private var barControls: some View {
        HStack(spacing: 4) {
            Button {
                send(.tapToggleControl)
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .rotationEffect(.degrees(WearIveRunMorph.chevronRotationDegrees(
                        progress: expansionProgress,
                        reduceMotion: reduceMotion,
                        settledFullExpression: reducedChevronFull
                    )))
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
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload)
        }
        if case .added(let place, let moment) = state {
            // The payload "{Place}, {Moment}" carries the pine.
            return Text("Added ").foregroundColor(AppTheme.inkSecondary)
                + Text("\(place), \(moment.rendered)").foregroundColor(AppTheme.pinePayload)
        }
        return Text(line).foregroundColor(AppTheme.inkSecondary)
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

    /// Which driver a transition runs under (decision §3's
    /// table), from the event and the state it lands in.
    private func morphDriver(
        for event: WearIveRunElementEvent,
        landingIn newState: WearIveRunElementState
    ) -> WearIveRunMorphDriver {
        if reduceMotion { return .reducedMotion }
        switch (event, newState) {
        case (.entryCreated, .cue), (.userCausedUpdate, .cue):
            return .cue
        case (.scrolledToEnd, .expanded):
            return .scroll
        case (.tapMiniBarBody, .expanded), (.tapToggleControl, .expanded):
            return .tap
        default:
            // cueTimedOut, the toggle/scroll collapses, rollover.
            return .collapse
        }
    }

    /// Feed one event to the machine and execute its effects
    /// exactly once. Presentation transitions run the morph:
    /// the machine decides the TARGET state; this function
    /// animates the one progress value toward it under the
    /// driver's spring.
    private func send(_ event: WearIveRunElementEvent) {
        // Scroll latch (§5.3): while a scroll-driven morph is
        // in flight, the opposite edge queues — it may start
        // only after the in-flight transition completes and
        // the threshold has been crossed (the queued event IS
        // that crossing).
        if (event == .scrolledToEnd || event == .scrolledAwayFromEnd),
           scrollLatchGeneration != nil {
            queuedScrollEvent = event
            return
        }
        let wasInCue = machine.state == .cue
        let previousState = machine.state
        let effects = machine.send(event)
        let enteringCue = machine.state == .cue && !wasInCue
        var deleteAfterCollapse = false
        for effect in effects {
            switch effect {
            case .fireCuePulseAndSoftHaptic:
                // NOT fired here: the pulse + soft haptic belong
                // to the morph's SETTLE (decision §2). They are
                // scheduled in `animateMorph` and fire when the
                // progress first reaches its target — or never,
                // if the user touches the element first (the
                // touch's send cancels the pending settle below).
                break
            case .startCueTimer:
                startCueTimer()
            case .cancelCueTimer:
                cueTimerGeneration += 1
                pendingCueSettleGeneration = nil
            case .deleteTodaysEntry:
                deleteAfterCollapse = true
            case .fireTrashHaptic:
                trashHapticNonce += 1
            }
        }
        if deleteAfterCollapse {
            performTrashCollapse()
            return
        }
        if machine.state != previousState {
            // Any non-cue transition supersedes a pending cue
            // settle (a touch during the cue cancels its pulse
            // and haptic with its timer, decision §2).
            if !enteringCue && machine.state != .cue {
                pendingCueSettleGeneration = nil
            }
            animateMorph(
                driver: morphDriver(for: event, landingIn: machine.state),
                enteringCue: enteringCue,
                scrollDriven: event == .scrolledToEnd || event == .scrolledAwayFromEnd
            )
        } else if enteringCue {
            // Already at full expression re-cueing (timer
            // restarted by the machine): the settle is now.
            fireCueSettle()
        }
    }

    /// Animate the ONE progress value to the machine state's
    /// target under the driver's animation. Interruptible by
    /// construction: a new driver bumps `morphGeneration` and
    /// retargets from the CURRENT progress; the superseded
    /// animation's completion is generation-guarded and does
    /// nothing.
    private func animateMorph(
        driver: WearIveRunMorphDriver,
        enteringCue: Bool,
        scrollDriven: Bool
    ) {
        morphGeneration += 1
        let generation = morphGeneration
        let target: Double = machine.isFullExpression ? 1 : 0
        if !scrollDriven {
            // A non-scroll driver supersedes an in-flight scroll
            // morph: its latch and any queued edge are stale —
            // the user's newest intent owns the element now.
            scrollLatchGeneration = nil
            queuedScrollEvent = nil
        }
        if scrollDriven { scrollLatchGeneration = generation }
        if enteringCue {
            pendingCueSettleGeneration = generation
            // The cue's 12 pt rise folds into the same gesture
            // (decision §2): the offset starts at 12 and the
            // same animation carries it to 0.
            if !reduceMotion { riseOffset = WearIveRunElementMachine.cueRisePoints }
        }
        if reduceMotion {
            // §6: the chevron swaps at the crossfade midpoint.
            let full = target == 1
            DispatchQueue.main.asyncAfter(
                deadline: .now() + WearIveRunMorph.reducedMotionDurationSeconds / 2
            ) {
                guard generation == morphGeneration else { return }
                reducedChevronFull = full
            }
        } else {
            reducedChevronFull = target == 1
        }
        let settle = {
            guard generation == self.morphGeneration else { return }
            if self.scrollLatchGeneration == generation {
                self.scrollLatchGeneration = nil
                if let queued = self.queuedScrollEvent {
                    self.queuedScrollEvent = nil
                    self.send(queued)
                }
            }
            if self.pendingCueSettleGeneration == generation {
                self.pendingCueSettleGeneration = nil
                self.fireCueSettle()
            }
        }
        if expansionProgress == target && riseOffset == 0 {
            // No travel (e.g. EXPANDED → CUE re-presentation at
            // full height): the settle is immediate.
            settle()
            return
        }
        withAnimation(WearIveRunMorph.animation(for: driver)) {
            expansionProgress = target
            riseOffset = 0
        } completion: {
            settle()
        }
    }

    /// A sheet presenting mid-transition wins (§5.4): the morph
    /// finishes instantly to its target underneath the dim —
    /// nothing animates visibly behind a sheet. A pending cue
    /// settle owed by the interrupted animation is honoured
    /// (the settle has, by this act, been reached).
    private func finishMorphInstantly() {
        let target: Double = machine.isFullExpression ? 1 : 0
        guard expansionProgress != target else { return }
        morphGeneration += 1
        scrollLatchGeneration = nil
        queuedScrollEvent = nil
        expansionProgress = target
        riseOffset = 0
        reducedChevronFull = target == 1
        if pendingCueSettleGeneration != nil {
            pendingCueSettleGeneration = nil
            fireCueSettle()
        }
    }

    /// The cue's settle moment: fire the ONE pulse and the ONE
    /// soft haptic (§1.7), together, exactly here — the first
    /// frame the morph reaches full expression.
    private func fireCueSettle() {
        cueHapticNonce += 1
        runCuePulse()
    }

    /// Trash (§1.2, unchanged by the motion decision): the
    /// element collapses its height while fading over 250 ms;
    /// the entry is deleted at the collapse's end, releasing
    /// the dock's space in the same animation.
    private func performTrashCollapse() {
        morphGeneration += 1
        let generation = morphGeneration
        pendingCueSettleGeneration = nil
        scrollLatchGeneration = nil
        queuedScrollEvent = nil
        withAnimation(.easeInOut(duration: WearIveRunElementMachine.trashCollapseSeconds)) {
            trashScale = 0
        } completion: {
            guard generation == self.morphGeneration else { return }
            self.onTrash()
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
}
