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
//
// RW-135 (Direction A, owner's ruling Nov 7, 2026; research §7
// + the same-day refinement): this view is the floating MINI
// dock ONLY — inset, radius 16, translucent card material,
// restored to the design of record. The synthesis form (an
// always-present inline section + a floating jump chip) is
// removed. The EXPANDED form is INTERIM: a full-width section
// at the page footer in the page's section language
// (`WearIveRunFooterSectionView` below), rendered by the host
// while the machine is at full expression — pending the
// owner's design/animation package for the final choreography.
// Narration is a pure function of the recorded entry (§7.0);
// the creation cue is the only pulse (§7.1); a changed settle
// cross-fades the line quietly (§7.4).

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
    /// RW-135 §7.2 (Direction A, Nov 7, 2026): the session's
    /// entry is recorded under a FUTURE day — it is a plan, and
    /// the bar says so in plain words ("Planned for {place} ·
    /// {day}"), never the "Updated to" form, which describes
    /// today's settled mirror. Both fields derive from the
    /// entry's own record (its place, its day) — §7.0.
    case planned(place: String, dayLabel: String)
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
        wasUpdatedThisSession: Bool,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> WearIveRunState {
        guard let entry = todaysEntry else { return .preAdd }
        // RW-135 §7.2 (Direction A): a future-day entry is a
        // PLAN — the planned form wins over both the creation
        // and update forms, whatever the narration kind stamped
        // on the entry, because the day it is recorded under is
        // itself the fact the line must state.
        if calendar.startOfDay(for: entry.dayStart) > calendar.startOfDay(for: now) {
            return .planned(
                place: entry.placeDisplayName,
                dayLabel: WearIveRunNarration.plannedDayLabel(
                    for: entry, calendar: calendar, now: now))
        }
        if wasUpdatedThisSession {
            // RW-135 §7.2: the settled-today line names the
            // entry's recorded PLACE ("Updated to {place}", the
            // place in pine bold). This supersedes the GEN-5
            // moment-payload form ON THE BAR: a moment-only
            // change now reads as the place the entry holds —
            // the entry still stores its C-7 update payload for
            // the collection surfaces that render it.
            return .updated(payload: .place(entry.placeDisplayName))
        }
        return .added(place: entry.placeDisplayName, moment: moment)
    }
}

// MARK: - Narration formatter

enum WearIveRunNarration {
    /// - preAdd: nil (the bar does not render at all)
    /// - added: "Added {place}, {moment}" — the comma form
    /// - updated: "Updated to {payload}" (payload renders pine)
    ///   (RW-98: the settled verb form is "Updated to" — the
    ///   gen-5 "Update to" wording is retired.)
    static func line(for state: WearIveRunState) -> String? {
        switch state {
        case .preAdd:
            return nil
        case .added(let place, let moment):
            return "Added \(place), \(moment.rendered)"
        case .updated(let payload):
            return "Updated to \(payload.rendered)"
        case .planned(let place, let dayLabel):
            return "Planned for \(place) · \(dayLabel)"
        }
    }

    /// RW-135 §7.2: the planned form's day word — "Tomorrow" for
    /// the next day, the full weekday name beyond that (the same
    /// day-word family the entry's own moment readings use).
    /// Pure in (entry day, calendar, now): the word derives from
    /// the RECORDED day, never from the viewed day (§7.0).
    static func plannedDayLabel(
        for entry: WearIveRunEntry,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> String {
        // Compared against the INJECTED now, by day — never
        // Calendar.isDateInTomorrow, which anchors to the system
        // clock and would ignore the caller's frame.
        let tomorrow = calendar.date(
            byAdding: .day, value: 1,
            to: calendar.startOfDay(for: now)) ?? now
        if calendar.isDate(entry.dayStart, inSameDayAs: tomorrow) { return "Tomorrow" }
        let weekday = DateFormatter()
        weekday.calendar = calendar
        weekday.locale = Locale(identifier: "en_US_POSIX")
        weekday.dateFormat = "EEEE"
        return weekday.string(from: entry.dayStart)
    }

    /// RW-135 §7.2: the expanded entry line's day form —
    /// "Today" / "Tomorrow" / "{Weekday, Mon D}" — from the
    /// entry's recorded day. (The pre-ruling expanded line
    /// hardcoded "Today", so a future-day plan's entry row
    /// contradicted its own date — the RW-132 family.)
    static func expandedDayLabel(
        for entry: WearIveRunEntry,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> String {
        // Day comparisons against the INJECTED now (see
        // plannedDayLabel — the isDateIn* family would anchor
        // to the system clock instead).
        if calendar.isDate(entry.dayStart, inSameDayAs: now) { return "Today" }
        let tomorrow = calendar.date(
            byAdding: .day, value: 1,
            to: calendar.startOfDay(for: now)) ?? now
        if calendar.isDate(entry.dayStart, inSameDayAs: tomorrow) { return "Tomorrow" }
        let weekday = DateFormatter()
        weekday.calendar = calendar
        weekday.locale = Locale(identifier: "en_US_POSIX")
        weekday.dateFormat = "EEEE, MMM d"
        return weekday.string(from: entry.dayStart)
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
    static func expandedLine(
        for entry: WearIveRunEntry,
        formattedFeelsLike: String,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> String {
        // GEN-5 (RW-29 FA-34): the expanded anatomy carries the
        // OUTFIT LINE — the item names themselves — where build
        // 25 stopped at the count. The count form remains only
        // for an entry with no recorded items.
        // RW-135 §7.2: the day segment is the entry's OWN day
        // form (Today / Tomorrow / Weekday, Mon D), derived from
        // the recorded dayStart — never a hardcoded "Today".
        let day = expandedDayLabel(for: entry, calendar: calendar, now: now)
        if entry.itemNames.isEmpty {
            let items = entry.itemNames.count == 1 ? "1 item" : "\(entry.itemNames.count) items"
            return "\(entry.placeDisplayName) · \(day) — \(formattedFeelsLike) feels like · \(items)"
        }
        return "\(entry.placeDisplayName) · \(day) — \(formattedFeelsLike) feels like · \(entry.itemNames.joined(separator: " · "))"
    }
}

// MARK: - Viewed-state narration (RW-88)

/// The strip FOLLOWS THE VIEWED MOMENT (RW-88, Sol's Nov 6, 2026
/// walkthrough): when the place or moment on screen differs from
/// the session entry's settled place/moment, the status line
/// names the VIEWED state — "Updated to Miami, FL, Now" for a
/// place change, "Updated to November 12 7 p.m." for a picked
/// moment on another day, "Updated to 7 p.m." for a picked hour
/// today. Returning the view to the entry's own place at Now
/// restores the entry's own narration (this returns nil). The
/// entry itself is untouched — browsing a future moment never
/// re-settles the save; only the line follows the view.
///
/// Grammar (Sol's examples, Nov 6, 2026): the changed condition
/// is named in full — place, time, OR date. A place change
/// carries the moment with it ("{place}, {moment}"); a
/// moment-only change carries no place. Other-day moments render
/// "{Month} {D} {h} {a.m./p.m.}" — the date in full, never a
/// weekday daypart and never a numeric stamp.
enum WearIveRunViewedNarration {
    /// The payload that follows "Updated to " while the view
    /// differs from the entry; nil when the view matches the
    /// entry's settled place and moment.
    static func payload(
        entryPlaceDisplayName: String,
        entryMoment: MomentReading,
        viewedPlace: SimplePlace?,
        viewedDate: Date,
        viewedHour: Int,
        isViewingNow: Bool,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> String? {
        let placeChanged = viewedPlace.map {
            $0.displayName != entryPlaceDisplayName
        } ?? false
        let momentText = viewedMomentText(
            viewedDate: viewedDate,
            viewedHour: viewedHour,
            isViewingNow: isViewingNow,
            calendar: calendar,
            now: now
        )
        let momentChanged: Bool
        if isViewingNow {
            momentChanged = entryMoment != .now
        } else if calendar.isDate(viewedDate, inSameDayAs: now) {
            let display = viewedHour % 12 == 0 ? 12 : viewedHour % 12
            momentChanged = entryMoment != .clock(hour12: display, isPM: viewedHour >= 12)
        } else {
            // An entry reading cannot carry a calendar date (the
            // structural MomentReading has no such case), so a
            // viewed moment on another day always differs from
            // the entry's reading.
            momentChanged = true
        }
        guard placeChanged || momentChanged else { return nil }
        if placeChanged, let viewedPlace {
            return "\(viewedPlace.displayName), \(momentText)"
        }
        return momentText
    }

    /// The viewed moment in the narration's grammar: "Now" at
    /// now, the clock reading ("7 p.m.") for a picked hour
    /// today, and the full date + clock ("November 12 7 p.m.")
    /// for a picked moment on another day.
    static func viewedMomentText(
        viewedDate: Date,
        viewedHour: Int,
        isViewingNow: Bool,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> String {
        if isViewingNow { return "Now" }
        let display = viewedHour % 12 == 0 ? 12 : viewedHour % 12
        let suffix = viewedHour >= 12 ? "p.m." : "a.m."
        if calendar.isDate(viewedDate, inSameDayAs: now) {
            return "\(display) \(suffix)"
        }
        let month = DateFormatter()
        month.dateFormat = "MMMM"
        let day = calendar.component(.day, from: viewedDate)
        return "\(month.string(from: viewedDate)) \(day) \(display) \(suffix)"
    }
}

// MARK: - Narration cross-fade deferral (RW-135 §7.4/§7.5)

/// The gate between a changed recorded line and the bar's
/// shown line. At rest, a new recorded line is released
/// immediately (the view cross-fades it, opacity only, ~200
/// ms). While a scroll gesture — or its deceleration — is
/// live, the new line PARKS: the shown line does not move
/// mid-gesture, and no fade runs concurrently with a
/// scroll-handoff (§7.5). A second change while parked
/// replaces the parked line (only the latest recorded line
/// ever lands). When the gesture ends, the parked line is
/// released exactly once, for the frame after deceleration.
/// Pure value logic — the dock view drives it with its shown
/// state; the unit tests drive it directly.
struct WearIveRunNarrationDeferral: Equatable {
    /// The line the bar is currently showing.
    private(set) var shownLine: String?
    /// The recorded line parked behind a live gesture.
    private var pendingLine: String?

    /// Offer a newly derived recorded line. Returns the line to
    /// cross-fade to NOW, or nil when the offer parked (gesture
    /// live) or cleared the gate (the entry is gone — the line
    /// is nil, and with it the bar).
    mutating func offer(line: String?, gestureActive: Bool) -> String? {
        guard let line else {
            shownLine = nil
            pendingLine = nil
            return nil
        }
        if gestureActive {
            pendingLine = line
            return nil
        }
        pendingLine = nil
        shownLine = line
        return line
    }

    /// The gesture ended: release the parked line, if any,
    /// exactly once.
    mutating func gestureEnded() -> String? {
        guard let pendingLine else { return nil }
        self.pendingLine = nil
        shownLine = pendingLine
        return pendingLine
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
    /// Bumped ONLY on the session's CREATION (RW-135 §7.1,
    /// Direction A — supersedes the WS-3/GEN-3/GEN-5 cue-worthy
    /// set): the creation cue is the ONLY pulse in the system.
    /// A settle that moved the entry to a new place or changed
    /// its moment no longer bumps this counter — §7.4: a changed
    /// record cross-fades the narration line, with no pulse, no
    /// haptic, and no expansion. A settle that changed only the
    /// kit, weather numbers, or the hero image is an asynchronous
    /// landing — recorded, but it never bumps this counter either
    /// (D-07's abolished drivers, enforced at the source via
    /// `WearIveRunLandingClassifier`).
    @Published private(set) var cueCount = 0

    /// RW-135 §7.4: bumped when a settle changes the DERIVED
    /// NARRATION LINE (the line the bar renders from the recorded
    /// entry) — the cross-fade trigger. Distinct from `cueCount`:
    /// a line change is quiet (opacity only, ~200 ms, deferred
    /// past any scroll gesture per §7.5); it never pulses, never
    /// fires a haptic, and never expands the bar. A settle whose
    /// derived line is unchanged (a moment-only change under the
    /// §7.2 place form, a weather/kit/hero landing) does not bump
    /// it — there is nothing new for the line to say.
    @Published private(set) var narrationVersion = 0
    /// The last narration line this model derived, for the
    /// change comparison above. Derived at record time from the
    /// recorded entry alone (§7.0).
    private var lastNarrationLine: String?

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
        // GEN-6 (RW-42 / DA-09): RESTORE, not creation. This
        // session has settled nothing yet (no entry carries its
        // identity), but the store already holds an entry for
        // the SAME day at the SAME place, written by an earlier
        // session (or a legacy build). That is the relaunch
        // shape: the process died, the app relaunched, and home's
        // automatic re-settle — onAppear and the landing
        // triggers, no user action — offers the persisted place
        // again. Keyed on the session identity alone, the settle
        // classified .created, bumped the cue counter, and the
        // bar fired a second "Added {place}, Now" for an entry
        // that already existed (Sol's gen-5 recording: "Added
        // Miami, FL, Now" at 1:30 and again on relaunch at
        // 3:47). A restore is not a state change: the narration
        // kind and payload the prior entry already carried are
        // preserved untouched, and the cue counter does NOT
        // move — no announcement fires. The bar then composes
        // over the restored entry through the machine's
        // entryAlreadyPresent reconciliation: present at MINI,
        // silently. A first settle at a day+place with NO
        // pre-existing entry is a genuine creation and still
        // announces, exactly once.
        //
        // RW-91 (Sol's correction, Nov 6, 2026 — supersedes the
        // GEN-6 supersede rule that stood here): saves
        // ACCUMULATE in the collection — one save per session,
        // and an earlier session's save is never deleted by a
        // later session's settle, not even at the same day and
        // place. The restore branch therefore records the new
        // session's entry ALONGSIDE the prior session's entry
        // (the store upserts on the entry's own session-scoped
        // identity, so both persist, newest first in the
        // collection's ordering). The one removal that remains
        // is the pre-session LEGACY-twin dedupe (FA-36): a
        // sessionless row is a migration artifact, not a
        // session's save, and the session save that restores
        // over it replaces it — that rule is unchanged.
        if existing == nil,
           let prior = entries.first(where: {
               $0.id != entry.id
                   && $0.sessionID != entry.sessionID
                   && !$0.sessionID.isEmpty
                   && $0.placeKey == entry.placeKey
                   && Calendar.current.isDate($0.dayStart, inSameDayAs: entry.dayStart)
           }) ?? entries.first(where: {
               $0.id != entry.id
                   && $0.sessionID != entry.sessionID
                   && $0.placeKey == entry.placeKey
                   && Calendar.current.isDate($0.dayStart, inSameDayAs: entry.dayStart)
           }) {
            store.removeLegacyTwin(of: entry)
            let restored = entry.settingNarration(
                wasUpdated: prior.wasUpdatedInSession,
                payload: prior.updatePayload
            )
            store.record(restored)
            entries = store.entries
            narratedEntryID = entry.id
            // RW-135: a restore is silent — it re-baselines the
            // narration line WITHOUT bumping the version (no
            // cross-fade, no announcement; the bar simply renders
            // the restored record).
            lastNarrationLine = WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
                todaysEntry: restored,
                moment: restored.momentReading,
                wasUpdatedThisSession: restored.wasUpdatedInSession
            ))
            return
        }
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
        let recorded = entry.settingNarration(wasUpdated: narrationUpdated, payload: narrationPayload)
        store.record(recorded)
        entries = store.entries
        // RW-135 §7.1/§7.4 (Direction A): the cue counter moves
        // ONLY on creation — the creation cue is the only pulse
        // in the system. Place/moment updates move the NARRATION
        // VERSION when (and only when) the derived line actually
        // changes: the bar cross-fades to the new recorded line,
        // quietly. (The pre-ruling code bumped cueCount on place
        // and moment updates too, so every location switch fired
        // the pulse + haptic + expansion §7.4 now forbids.)
        if classification == .created {
            narratedEntryID = entry.id
            cueCount += 1
        }
        let newLine = WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
            todaysEntry: recorded,
            moment: recorded.momentReading,
            wasUpdatedThisSession: recorded.wasUpdatedInSession
        ))
        if newLine != lastNarrationLine {
            lastNarrationLine = newLine
            narrationVersion += 1
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
        lastNarrationLine = nil
    }

    /// Fix wave 2026-11-07 (RW-122, iOS half): delete ONE entry
    /// from the collection — any entry, not only the session's
    /// (the collection sheets' per-entry trash). Resolves
    /// through the store's own removal, so the collection,
    /// chapters and counts all re-derive from the same write.
    func removeEntry(id: String) {
        store.remove(id: id)
        entries = store.entries
        if sessionEntry() == nil { narratedEntryID = nil }
    }

    /// Fix wave 2026-11-07 (RW-122, iOS half): delete a whole
    /// by-location GROUP — every entry filed under one place
    /// (the chapter view's delete-all-for-this-location
    /// control).
    func removeEntries(placeName: String) {
        store.removePlace(placeName: placeName)
        entries = store.entries
        if sessionEntry() == nil { narratedEntryID = nil }
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

    /// RW-135 §7.4: the narration cross-fade's length — opacity
    /// only, ~200 ms, when a settle lands on a changed record.
    static let narrationCrossfadeSeconds: Double = 0.2

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

    /// Corner radius tokens. RW-135 §7.6 (Direction A, Nov 7,
    /// 2026) RESTORES the design of record (2026-11-02): the
    /// dock is a floating card, radius 16 in BOTH docked forms,
    /// and the radius never animates to 0 — the interpolation is
    /// constant at 16 by design. This supersedes the 2026-11-04
    /// geometry decision that zeroed both tokens (the full-width
    /// flat strip the owner's RW-135 complaint was about). The
    /// function still derives the radius from the one progress
    /// value, so a future token change animates with the same
    /// morph.
    static let miniCornerRadius: Double = 16
    static let expandedCornerRadius: Double = 16
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

/// RW-119 (successor wave): the dock's EMPTY form — the Wear
/// I've Run section's presence before any session entry exists.
/// The section is a persistent part of the home stack (the
/// settled design's empty state is a designed surface, not a
/// disappearance), but the pre-fix dock mounted the bar only
/// once a session entry stood, so a session whose settles never
/// recorded — a fresh account browsing non-Now cells, whose
/// recording guards rightly refuse — lost the section entirely
/// (Sol's build-29 end state: 23 delivered cells server-side,
/// no banner at all). The empty form carries the section's
/// wordmark and the designed empty state's own head line
/// (frame 10, verbatim — the collection's emptyState text);
/// tapping it opens the collection, whose full empty state
/// explains the rest. It borrows the MINI bar's chrome exactly
/// (material + sheet tint at the resting opacity, same insets),
/// so the section reads as one element in both forms.
struct WearIveRunEmptyDockView: View {
    let onOpenCollection: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    /// The designed empty state's head line, verbatim (frame 10;
    /// the same string the collection's emptyState carries).
    static let emptyLine = "Your scrapbook starts with today's run."

    var body: some View {
        Button(action: onOpenCollection) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(WearIveRunMiniBarModel.wordmark)
                        .font(.greatVibes(size: 20))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text(Self.emptyLine)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.appFootnote.weight(.semibold))
                    .foregroundColor(AppTheme.inkMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background {
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(AppTheme.sheet.opacity(
                    WearIveRunMorph.tintOpacity(
                        progress: 0,
                        isDark: colorScheme == .dark
                    )
                ))
            }
        }
        .accessibilityLabel("\(WearIveRunMiniBarModel.wordmark). \(Self.emptyLine)")
    }
}

struct WearIveRunMiniBarView: View {
    let state: WearIveRunState
    /// The session's entry, as recorded; nil only in preAdd.
    /// (The footer section renders the expanded anatomy from its
    /// own copy of this same record — one record, two seats.)
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
    /// RW-135 §7.5: true while the host page's scroll gesture (or
    /// its deceleration) is live. A settle that lands mid-gesture
    /// defers its narration cross-fade to the frame after the
    /// gesture ends — no morph or fade runs concurrently with a
    /// scroll-handoff.
    var isScrollGestureActive: Bool = false
    /// RW-135 (Direction A, interim expanded form): the machine's
    /// full-expression state, mirrored to the host — the EXPANDED
    /// form itself is the full-width footer section in the page
    /// flow (owner's Nov 7 refinement), not a taller dock. The
    /// dock reports; the host renders the footer.
    var onFullExpressionChange: (Bool) -> Void = { _ in }
    /// RW-135: a collapse requested by the footer section (its
    /// own tap-again / drag-down): the host bumps this nonce and
    /// the machine collapses through its one transition table.
    var externalCollapseNonce: Int = 0
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
    /// The rendered content's own measured height — used ONLY
    /// by the trash collapse (its height shrinks with the fade,
    /// §1.2). RW-86: this measurement never gates painting; the
    /// bar renders at its natural height in every state, and the
    /// value only scales a collapse already in flight.
    @State private var contentHeight: Double = 0
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
    /// RW-135 §7.4/§7.5: the narration state the bar is actually
    /// SHOWING. The rendered line derives from this, not directly
    /// from `state`: when a settle changes the recorded line, the
    /// shown state cross-fades (opacity only, ~200 ms) to the new
    /// one; a settle landing mid-scroll-gesture parks its new
    /// state in `pendingNarrationState` until the frame after
    /// deceleration ends. The shown state is still a pure
    /// function of a recorded entry (§7.0) — it is an EARLIER
    /// recorded entry's state, never a computed preview.
    @State private var displayedState: WearIveRunState?
    @State private var pendingNarrationState: WearIveRunState?
    /// The §7.5 deferral gate the shown line passes through.
    @State private var narrationDeferral = WearIveRunNarrationDeferral()
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
                .onAppear {
                    reconcileInitialState()
                    if displayedState == nil {
                        displayedState = state
                        _ = narrationDeferral.offer(
                            line: WearIveRunNarration.line(for: state),
                            gestureActive: false)
                    }
                }
                .onChange(of: cueCount) { oldValue, newValue in
                    guard newValue > oldValue else { return }
                    // RW-135 §7.1: the counter now moves ONLY on
                    // creation, and creation is the only cue — a
                    // landing here is fed as a creation, always.
                    // (Changed settles move `narrationVersion`,
                    // which this view deliberately does not
                    // consume: the line cross-fades via the
                    // `state` change path below, §7.4.)
                    send(.entryCreated)
                }
                .onChange(of: state) { _, newState in
                    // §7.4/§7.5: a changed recorded line passes
                    // the deferral gate — released at rest,
                    // parked while a scroll gesture (or its
                    // deceleration) is live.
                    let released = narrationDeferral.offer(
                        line: WearIveRunNarration.line(for: newState),
                        gestureActive: isScrollGestureActive)
                    if released != nil {
                        applyNarrationCrossfade(to: newState)
                    } else if isScrollGestureActive {
                        pendingNarrationState = newState
                    }
                }
                .onChange(of: isScrollGestureActive) { _, active in
                    // The frame after deceleration ends: a parked
                    // cross-fade lands (§7.5).
                    if !active, narrationDeferral.gestureEnded() != nil,
                       let pending = pendingNarrationState {
                        pendingNarrationState = nil
                        DispatchQueue.main.async {
                            applyNarrationCrossfade(to: pending)
                        }
                    }
                }
                .onChange(of: machine.isFullExpression) { _, full in
                    onFullExpressionChange(full)
                }
                .onChange(of: externalCollapseNonce) { _, _ in
                    // The footer section's collapse request (§7.2
                    // tap-again / drag-down, interim mapping):
                    // collapse through the one machine.
                    if machine.isFullExpression { send(.tapToggleControl) }
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

    /// The dock's single rendered form: ONE copy of the content,
    /// rendered at its natural height in every state. The header
    /// (wordmark + narration + controls) never moves or scales;
    /// the narration line cross-fades on a changed settle (§7.4,
    /// via the deferral gate). RW-135 (Direction A): the dock no
    /// longer carries an expanded block — the machine's
    /// `expansionProgress` still runs (it drives the toggle
    /// chevron's rotation and the cue's settle bookkeeping), but
    /// the EXPANDED content itself is the footer section in the
    /// page flow, mirrored through `onFullExpressionChange`.
    /// The background tint stands at the MINI opacity and the
    /// corner radius at 16 (§7.6); the height is the content's
    /// own.
    ///
    /// RW-86 (root cause, Nov 6, 2026): the pre-fix form measured
    /// two hidden phantom copies of the content through
    /// `onGeometryChange` and framed the bar at the interpolated
    /// measured height, painting the visible copy at EXPANDED
    /// natural height inside that frame, clipped. The MINI
    /// endpoint was the fragile one: on device the slim bar
    /// painted its background (material + sheet tint) with none
    /// of its content — Sol's walkthroughs across builds: at
    /// rest the slim bar is ALWAYS a solid white (light) / black
    /// (dark) bar, while the expanded states, whose height the
    /// content itself supplies, render fully. A surface whose
    /// resting state depends on a self-measurement cycle is also
    /// unrenderable off-device (the VQ harness could never
    /// produce the bar: "it measures its own heights through
    /// appear/layout cycles a static snapshot never runs").
    /// The fix removes the measurement from the paint path
    /// entirely: MINI is a plain header at natural height —
    /// there is no frame, clip, or measured endpoint between
    /// the content and the screen. The one remaining measurement
    /// (of the real content) serves only the trash collapse's
    /// height shrink, which never gates a resting state.
    private var morphBar: some View {
        let visible = machine.state != .absent || trashScale < 1
        return headerRow
        .opacity(visible ? trashScale : 0)
        .onGeometryChange(for: Double.self) { proxy in
            proxy.size.height
        } action: { _, height in
            if height > 0 { contentHeight = height }
        }
        .frame(height: visible
            ? (trashScale < 1 ? CGFloat(contentHeight * trashScale) : nil)
            : 0)
        .background {
            // RW-135 §7.6 (Direction A): the MINI material — the
            // translucent card tint over the system material blur.
            // The tint is the CARD hex pair (light #FDFBF6 at
            // 85% / dark #2A251E at 82%): in dark mode the tint is
            // the card hex, not the sheet hex (the pre-ruling bar
            // tinted with AppTheme.sheet, whose dark side is
            // #211D17). The dock is always MINI (§7.2's expanded
            // form is the footer section), so the tint stands at
            // the MINI opacity — the morph's tint ramp now belongs
            // to the footer relationship, not to this layer.
            // The material itself is never animated (decision
            // §4 — no blur animation).
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(
                    (colorScheme == .dark
                        ? Color(hex: AppTheme.cardDarkHex)
                        : Color(hex: AppTheme.sheetLightHex)
                    ).opacity(WearIveRunMorph.tintOpacity(
                        progress: 0,
                        isDark: colorScheme == .dark
                    ))
                )
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: expansionProgress)))
        // §7.6: the soft shadow (0 offset-x, 6 offset-y, black
        // 14%; SwiftUI's radius is half the CSS blur, so the
        // design's 24 pt blur is radius 12 here).
        .shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 6)
    }

    /// The continuity anchor (decision §2): wordmark, narration,
    /// and both controls — present in both states, never moving,
    /// never scaling. RW-135 §7.2/§7.6 (Direction A): the MINI
    /// bar is ONE row — the Great Vibes wordmark at 17 pt, the
    /// one narration line at 12 pt, and the expand + delete
    /// controls right-aligned, grayed until use (the frame-a
    /// anatomy; the pre-ruling two-line stacked header was the
    /// build-30 strip's anatomy). The narration renders from
    /// `displayedState` — the recorded state the bar is showing
    /// — so a changed settle cross-fades the line in place
    /// (§7.4): the text is keyed by its line inside an opacity
    /// transition, driven by the one 200 ms fade.
    private var headerRow: some View {
        HStack(spacing: 8) {
            wordmarkView
            if let shown = displayedState ?? state, let line = WearIveRunNarration.line(for: shown) {
                // GEN-5 (RW-29 FA-48): a long simple place in the
                // payload may wrap to two lines — the payload is
                // never amputated.
                narrationText(line, for: shown)
                    .font(.system(size: 12))
                    .lineLimit(2)
                    .id(line)
                    .transition(.opacity)
            }
            Spacer(minLength: 0)
            barControls
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture { send(.tapMiniBarBody) }
    }

    /// §7.4: apply a changed recorded line as an opacity-only
    /// cross-fade (~200 ms) — no pulse, no haptic, no expansion,
    /// and (via the parking in `onChange(of: state)`) never
    /// concurrent with a scroll gesture (§7.5).
    private func applyNarrationCrossfade(to newState: WearIveRunState) {
        guard displayedState != newState else { return }
        if reduceMotion {
            displayedState = newState
            return
        }
        withAnimation(.easeInOut(duration: WearIveRunMorph.narrationCrossfadeSeconds)) {
            displayedState = newState
        }
    }

    /// The wordmark in the genuine Great Vibes (RW-26: the Snell
    /// Roundhand fallback call site is retired — the font is
    /// bundled and `Font.greatVibes` resolves it).
    private var wordmarkView: some View {
        Text(WearIveRunMiniBarModel.wordmark)
            // RW-135 §7.6: the wordmark in the bar is 17 pt (the
            // in-flow section header size, 31 pt, is not used in
            // the bar; the pre-ruling bar used 20 pt).
            .font(.greatVibes(size: 17))
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

    /// The narration line's rich form: the verb in secondary
    /// ink, the payload in pine (RW-135 §7.6: the settled-today
    /// place in pine BOLD). Every case derives from the SHOWN
    /// recorded state and nothing else — §7.0: the RW-88
    /// viewed-state override branch that stood here is removed
    /// outright (its parameter is gone; the pure function and
    /// its tests remain in the tree, uncalled by the bar).
    private func narrationText(_ line: String, for shownState: WearIveRunState) -> Text {
        switch shownState {
        case .updated(let payload):
            return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .added(let place, let moment):
            // The payload "{Place}, {Moment}" carries the pine.
            return Text("Added ").foregroundColor(AppTheme.inkSecondary)
                + Text("\(place), \(moment.rendered)").foregroundColor(AppTheme.pinePayload)
        case .planned(let place, let dayLabel):
            // §7.2: "Planned for {place} · {day}" — the place in
            // pine bold, the day word in secondary ink.
            return Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(" · \(dayLabel)").foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
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

// MARK: - RW-135 expanded form (INTERIM): the footer section
//
// The owner's refinement (Nov 7, 2026): the EXPANDED state is a
// full-width section at the page footer — NOT a taller floating
// card, and NOT the build-30 flat hard-edged full-bleed slab:
// "full width" here is section width in the page's own language
// (the sheet container's inset, the card fill + stroke, a 24 pt
// card radius, section spacing), seated at the foot of the flow
// where the prior lane's inline section stood. The host renders
// this section only while the dock's machine is at full
// expression (end-of-scroll, tap on the bar, or the creation
// cue — §7.2), so it is the dock's expansion, not a separate
// always-present section. Its content is §7.2's anatomy, from
// the recorded entry only (§7.0): the narration row, then the
// full entry row (recorded thumbnail + the entry line), and
// tapping the entry row opens the collection sheet with the
// session entry in its location group (§7.3 — the doorway).
// Collapse (§7.2): the narration row is the tap-again surface,
// and a drag down on the section collapses it; both route
// through the host to the dock's one machine. The final
// MINI ↔ footer choreography is the design package's to
// specify; this interim form follows §7.5's principles (one
// form at a time in the footer seat, opacity-only appearance,
// no concurrent scroll-handoff animation).
struct WearIveRunFooterSectionView: View {
    /// The recorded narration state (the same derivation the
    /// dock renders — one record, one line).
    let state: WearIveRunState
    /// The session's recorded entry.
    let entry: WearIveRunEntry
    /// The entry's feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    let onOpenCollection: () -> Void
    let onCollapse: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Narration row (§7.2): wordmark + the recorded line
            // + the collapse control. Tapping the row is the
            // tap-again collapse.
            HStack(spacing: 8) {
                Text(WearIveRunMiniBarModel.wordmark)
                    .font(.greatVibes(size: 17))
                    .foregroundColor(AppTheme.inkPrimary)
                if let line = WearIveRunNarration.line(for: state) {
                    footerNarrationText(line)
                        .font(.system(size: 12))
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .contentShape(Rectangle())
            .onTapGesture { onCollapse() }

            // The entry row (§7.2 anatomy, §7.3 doorway): the
            // recorded thumbnail + the full entry line. Tapping
            // it opens the collection sheet.
            Button(action: onOpenCollection) {
                HStack(spacing: 12) {
                    // The thumbnail renders ONLY once its image
                    // has decoded — no placeholder graphic and no
                    // broken-image frame ever paints (D-12).
                    AsyncImage(url: URL(string: entry.heroImageReference)) { phase in
                        if case .success(let image) = phase {
                            HeroPhoto(image: image, width: 56, height: 56)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        } else {
                            Color.clear
                                .frame(width: 56, height: 56)
                        }
                    }
                    // No line limit, by design (WS-4): the entry
                    // line wraps whole rather than truncating.
                    Text(WearIveRunNarration.expandedLine(
                        for: entry,
                        formattedFeelsLike: formattedFeelsLike
                    ))
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open the full Wear I've Run collection")
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(AppTheme.cardFill)
                .overlay {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(AppTheme.cardStroke, lineWidth: 1)
                }
        }
        .padding(.horizontal, 16)
        // §7.2: drag down on the expanded form collapses it.
        .gesture(
            DragGesture(minimumDistance: 16)
                .onEnded { value in
                    if value.translation.height > 24 { onCollapse() }
                }
        )
        .accessibilityLabel("\(WearIveRunMiniBarModel.wordmark), expanded")
    }

    /// The footer's narration rich form — the same anatomy as
    /// the dock's (verb secondary, payload pine bold), rendered
    /// from the recorded state only (§7.0).
    private func footerNarrationText(_ line: String) -> Text {
        switch state {
        case .updated(let payload):
            return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .added(let place, let moment):
            return Text("Added ").foregroundColor(AppTheme.inkSecondary)
                + Text("\(place), \(moment.rendered)").foregroundColor(AppTheme.pinePayload)
        case .planned(let place, let dayLabel):
            return Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(" · \(dayLabel)").foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }
}
