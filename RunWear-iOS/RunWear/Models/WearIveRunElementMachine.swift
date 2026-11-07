import Foundation

// MARK: - WS-3 (RC-3, iOS) — the Wear I've Run element: ONE state machine
//
// The element has FOUR states — ABSENT, MINI, EXPANDED, CUE — where
// CUE is a timed *presentation* of EXPANDED, not a fifth content
// state (rebuild program Part 1, §1.2). This type is the ONLY
// transition source: the view holds one machine value, feeds it
// the enumerated events below, and renders the resulting state.
// It is the SAME machine Android's WS-3 lane builds — identical
// states, drivers, and timing constants, expressed in Swift.
//
// The pre-WS-3 iOS view held three competing presentations
// (mini / expanded / a separate end-of-scroll case) driven by a
// cue counter, a tap, and an end-of-scroll signal with NO collapse
// counterpart — after leaving the end of the scroll the bar stayed
// expanded (the audit's D-07 iOS latch). The latch is retired
// here: arrival at the end expands, departure collapses, in both
// directions, from the one table in `send(_:)`.
//
// Abolished drivers (D-07, specified out in §1.2): asynchronous
// landings — weather refresh, hero URL resolution, outfit/weather
// re-landing, unit re-render, app foregrounding — never create,
// update, expand, collapse, or narrate. They exist in the event
// vocabulary ONLY as the explicit no-op `asyncLanding`, so a call
// site that is tempted to move the element for a landing has to
// say so, and the machine's answer is always: no movement, no
// effects, timer untouched.

/// The element's four states.
enum WearIveRunElementState: Equatable {
    /// No entry for today — the element does not render.
    case absent
    /// Docked, collapsed, translucent (85% light / 82% dark over
    /// `.regularMaterial`, content at full opacity).
    case mini
    /// The full expression, opaque, at rest until a driver moves it.
    case expanded
    /// The timed presentation of EXPANDED after a CREATION
    /// (RW-135 §7.1/§7.4: the creation cue is the only pulse in
    /// the system — a user-caused update no longer reaches this
    /// state): one pulse + one soft haptic, then the 3.0 s timer
    /// collapses it to MINI.
    case cue
}

/// Something that is NOT a driver, named so the no-op is explicit:
/// an asynchronous landing resolving on its own.
enum WearIveRunAsyncLanding: Equatable {
    case outfit
    case weather
    case location
    case heroImage
    case unitRerender
    case foregrounding
}

/// The complete driver list (§1.2's table, plus the two
/// reconciliations a docked element needs: an entry already on
/// disk at cold start, and a sheet's dim — the dim is NOT a state
/// transition and is handled by `WearIveRunElementDimming`).
enum WearIveRunElementEvent: Equatable {
    /// Today's entry is created — the first generation of the day
    /// settles (ABSENT → CUE).
    case entryCreated
    /// A user-caused update settles — the user changed location,
    /// day, or time, or added the entry manually. RW-135 §7.4
    /// (Nov 7, 2026 ruling, Direction A) SUPERSEDES the pre-ruling
    /// cue semantics this event used to carry: a settle landing on
    /// a CHANGED record is NOT a driver — no pulse, no haptic, no
    /// expansion, in every state. The bar's narration line simply
    /// cross-fades to the new recorded line (view layer). The
    /// creation cue is the only pulse in the system (§7.1), so
    /// this event is retained in the vocabulary only so a call
    /// site tempted to cue an update has to say so — and the
    /// machine's answer is always: no movement, no effects.
    case userCausedUpdate
    /// The entry was already present when the element composed
    /// (cold start / re-entry): ABSENT → MINI, silently. This is
    /// a reconciliation, never a cue — nothing landed just now.
    case entryAlreadyPresent
    /// The cue's 3.0 s elapsed with no interaction (CUE → MINI).
    case cueTimedOut
    /// The user tapped the mini bar's body (MINI → EXPANDED; in
    /// CUE the touch cancels the timer and the bar obeys it —
    /// CUE → EXPANDED).
    case tapMiniBarBody
    /// The user tapped the expand/collapse control. The SAME
    /// control, with the SAME semantics, in both resting states:
    /// MINI → EXPANDED, EXPANDED → MINI; in CUE it collapses
    /// (CUE → MINI, timer cancelled).
    case tapToggleControl
    /// The user tapped trash, in either resting state or mid-cue
    /// (any → ABSENT; the entry is deleted with it).
    case tapTrash
    /// The list scroll arrived at its end (MINI → EXPANDED;
    /// EXPANDED stays EXPANDED).
    case scrolledToEnd
    /// The list scroll left its end (EXPANDED → MINI). The
    /// symmetric counterpart the pre-WS-3 latch never had.
    case scrolledAwayFromEnd
    /// The day rolled over (any → ABSENT, until the new day's
    /// first generation creates its entry).
    case dayRollover
    /// An asynchronous landing resolved. NEVER a driver: every
    /// state is unchanged, no effect fires, the cue timer is
    /// neither started nor restarted.
    case asyncLanding(WearIveRunAsyncLanding)
}

/// Side effects a transition asks the view to perform. The machine
/// is pure; the view executes these exactly once per `send`.
enum WearIveRunElementEffect: Equatable {
    /// Fire the ONE cue pulse (900 ms ease-in-out, 1.00 → 1.03 →
    /// 1.00, pine glow at low alpha) and the ONE soft haptic
    /// (§1.7: impact `.soft`) at the pulse's start.
    case fireCuePulseAndSoftHaptic
    /// (Re)start the 3.0 s cue timer.
    case startCueTimer
    /// Cancel a pending cue timer (the user touched the element,
    /// trashed it, or the day rolled over).
    case cancelCueTimer
    /// Delete today's entry (the trash driver's data half; the
    /// view's trash handler performs the store removal).
    case deleteTodaysEntry
    /// The trash haptic (§1.7: impact `.light`).
    case fireTrashHaptic
}

struct WearIveRunElementMachine: Equatable {
    // MARK: Timing & motion constants (§1.2 / §1.7)

    /// The cue collapses to MINI after exactly this long.
    static let cueDurationSeconds: Double = 3.0
    /// The cue pulse's total length: 900 ms, ease-in-out, once.
    static let cuePulseDurationSeconds: Double = 0.9
    /// The pulse's peak scale (1.00 → 1.03 → 1.00).
    static let cuePulseScale: Double = 1.03
    /// The cue's rise into place, in points.
    static let cueRisePoints: Double = 12
    /// The trash collapse's length, in seconds (§1.2: the element
    /// collapses its height while fading; the dock's space is
    /// released in the same animation).
    static let trashCollapseSeconds: Double = 0.25

    private(set) var state: WearIveRunElementState

    init(state: WearIveRunElementState = .absent) {
        self.state = state
    }

    /// True while the element renders at all.
    var isPresent: Bool { state != .absent }
    /// True for the full expression (EXPANDED and its timed
    /// presentation, CUE).
    var isFullExpression: Bool { state == .expanded || state == .cue }
    /// The toggle control's glyph direction for the CURRENT
    /// state: it points "expand" only from MINI, "collapse" from
    /// EXPANDED and CUE. One control, both states (D-10).
    var toggleIsExpand: Bool { state == .mini }

    /// The ONE transition function. Every (state, event) pair not
    /// listed as moving is an illegal/no-op transition: the state
    /// is unchanged and no effect fires — including every
    /// `asyncLanding`, in every state.
    @discardableResult
    mutating func send(_ event: WearIveRunElementEvent) -> [WearIveRunElementEffect] {
        switch (state, event) {
        // Creation: the day's first generation settles.
        case (.absent, .entryCreated):
            state = .cue
            return [.fireCuePulseAndSoftHaptic, .startCueTimer]
        // An entry already on disk at compose time: present, at
        // rest, silently — no landing happened.
        case (.absent, .entryAlreadyPresent):
            state = .mini
            return []
        // RW-135 §7.4 (Direction A): a user-caused update is NOT
        // a driver — it falls through to the default no-op below
        // in every state (MINI, EXPANDED, and CUE alike; the cue
        // timer is neither started nor restarted by an update).
        // The pre-ruling transitions (update → CUE with pulse +
        // haptic) are superseded: the creation cue is the only
        // pulse in the system.
        // The cue expires untouched.
        case (.cue, .cueTimedOut):
            state = .mini
            return []
        // Taps.
        case (.mini, .tapMiniBarBody):
            state = .expanded
            return []
        case (.cue, .tapMiniBarBody):
            state = .expanded
            return [.cancelCueTimer]
        // GEN-5 (RW-29 FA-33): a body tap while EXPANDED
        // settles the bar back to mini — build 25's machine
        // let it fall to the default no-op, so the expanded
        // block's body was a dead surface (only the chevron
        // collapsed it).
        case (.expanded, .tapMiniBarBody):
            state = .mini
            return []
        case (.mini, .tapToggleControl):
            state = .expanded
            return []
        case (.expanded, .tapToggleControl):
            state = .mini
            return []
        case (.cue, .tapToggleControl):
            state = .mini
            return [.cancelCueTimer]
        // End-of-scroll, symmetric in both directions.
        case (.mini, .scrolledToEnd):
            state = .expanded
            return []
        case (.expanded, .scrolledToEnd):
            return [] // already there — arrival while expanded stays
        case (.expanded, .scrolledAwayFromEnd):
            state = .mini
            return []
        // Trash, from any present state (mid-cue included).
        case (.mini, .tapTrash), (.expanded, .tapTrash):
            state = .absent
            return [.deleteTodaysEntry, .fireTrashHaptic]
        case (.cue, .tapTrash):
            state = .absent
            return [.cancelCueTimer, .deleteTodaysEntry, .fireTrashHaptic]
        // Day rollover: absent until the new day's first
        // generation, whatever the element was doing.
        case (.mini, .dayRollover), (.expanded, .dayRollover):
            state = .absent
            return []
        case (.cue, .dayRollover):
            state = .absent
            return [.cancelCueTimer]
        // Everything else — every async landing in every state,
        // a creation arriving when an entry already exists, a
        // timeout outside the cue, scroll/tap events in ABSENT,
        // scrolling away in MINI/CUE — is not a driver: no
        // movement, no effects.
        default:
            return []
        }
    }
}

// MARK: - Landing classification (the collection model's half)
//
// The element's cue may only be fed by a landing that is the
// user's business: the session's first generation, a settle
// that moved the session's entry to a new PLACE (GEN-3 session
// rule), or — GEN-5 (RW-28, C-7) — a settle that changed its
// MOMENT. A settle that changed only the kit, weather numbers,
// or the hero image is an asynchronous landing wearing the
// entry's clothes — the store still records it (the saved
// entry is the session's last settled version), but the
// element never hears about it (D-07's abolished drivers, at
// the source). GEN-5 supersedes the WS-4 kit cue: the
// narration names place and time only, never a garment, so a
// kit-only change has nothing to narrate and does not cue.

enum WearIveRunLandingClassification: Equatable {
    /// No entry with this identity existed: creation.
    case created
    /// The entry existed and its kit (names or keys) changed —
    /// classified distinctly so the record can say so, but
    /// GEN-5 treats it as a silent landing (C-7: garments are
    /// never narration subjects).
    case kitUpdated
    /// GEN-5 (RW-28, C-7): the entry existed and its settled
    /// MOMENT changed (a confirmed hour change re-solves the
    /// entry) with place and kit otherwise as they were — a
    /// user-caused settle that narrates "Update to [reading]"
    /// and cues.
    case momentUpdated
    /// The entry existed and MOVED to a different place (GEN-3
    /// session rule): within a session the one entry updates in
    /// place through every location change, and the move is a
    /// user-caused settle — it narrates from the diff ("Update
    /// to…") and cues, exactly like a kit change. Before the
    /// session rule a place change minted a NEW entry (a fresh
    /// "Added …" per place — census X-4); a place change with an
    /// unchanged kit must therefore never fall through to the
    /// silent case, or the bar would keep announcing a place the
    /// session has moved past.
    case placeUpdated
    /// The entry existed and changed, but neither its kit nor
    /// its place — weather or hero only. Recorded silently;
    /// never a cue.
    case silentUpdate
    /// Byte-identical settle: nothing happened at all.
    case identical
}

enum WearIveRunLandingClassifier {
    static func classify(
        existing: WearIveRunEntry?,
        incoming: WearIveRunEntry
    ) -> WearIveRunLandingClassification {
        guard let existing else { return .created }
        // GEN-5: identity of CONTENT, ignoring the volatile
        // bookkeeping fields (lastSavedAt moves on every settle;
        // updatePayload is derived at record time) — otherwise no
        // settle could ever classify identical again and every
        // weather tick would rewrite the store.
        let sameContent = existing.dayStart == incoming.dayStart
            && existing.placeKey == incoming.placeKey
            && existing.feelsLikeF == incoming.feelsLikeF
            && existing.actualF == incoming.actualF
            && existing.condition == incoming.condition
            && existing.itemNames == incoming.itemNames
            && existing.itemKeys == incoming.itemKeys
            && existing.heroImageReference == incoming.heroImageReference
            && existing.momentReading == incoming.momentReading
        if sameContent { return .identical }
        if existing.placeKey != incoming.placeKey {
            return .placeUpdated
        }
        if existing.momentReading != incoming.momentReading {
            return .momentUpdated
        }
        if existing.itemNames != incoming.itemNames
            || existing.itemKeys != incoming.itemKeys {
            return .kitUpdated
        }
        return .silentUpdate
    }
}

// MARK: - Backdrop dimming (D-11)

/// The element belongs to HOME's layer: when a sheet presents
/// over home, the element dims WITH the home backdrop — it never
/// stays bright above the scrim (the Android D-11 frame; iOS's
/// construction was judged clean in the audit, and WS-3 makes the
/// membership explicit and pinnable rather than incidental).
/// WS-5 owns the sheets themselves; the element only consumes
/// their presented state through this seam.
enum WearIveRunElementDimming {
    /// Content opacity while a sheet is up: the element takes
    /// the backdrop's dim, it does not keep its own brightness.
    static let sheetPresentedOpacity: Double = 0.4
    static let atRestOpacity: Double = 1.0

    static func opacity(sheetPresented: Bool) -> Double {
        sheetPresented ? sheetPresentedOpacity : atRestOpacity
    }

    /// A dimmed element is not interactive: its controls must not
    /// answer touches through the sheet's backdrop.
    static func isInteractive(sheetPresented: Bool) -> Bool {
        !sheetPresented
    }
}
