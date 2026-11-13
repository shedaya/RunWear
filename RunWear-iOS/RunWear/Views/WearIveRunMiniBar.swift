import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25 scaffolding; RW-26 full behavior)
//
// RW-152 §1 (Nov 9, 2026) SUPERSEDES the two-surface split
// this header used to describe: the Wear I've Run presence is
// now ONE overlay surface with two states —
// - the floating MINI dock (WearIveRunMiniBarView): a true
//   overlay, inset 12 / bottom 14, radius 16, translucent card
//   material; at rest while a session entry exists, absent
//   before one does (§1.3/§2.1);
// - the EXPANDED panel (WearIveRunExpandedPanelView below):
//   the SAME surface's expanded state, summoned in place by
//   the dock's expand control and dismissed by its chevron —
//   never the rest state, never a scroll-flow section.
// WearIveRunOverlayView (below) owns the one
// `expansionProgress` the RW-135 §3 dissolve mathematics drive:
// height interpolates between the two measured content heights
// and the expanded content cross-fades in over the morph's
// window — the continuous in-place morph is also the RW-154
// fix (dock and expanded form now read as one element because
// they ARE one element). The in-flow footer section
// (WearIveRunFooterSectionView) and the scroll-position
// handoff/glide system are removed. Narration remains a pure
// function of the recorded entry (§1.2); there is no "Added"
// line (§9.1); the creation cue (§4.1) and the update bounce
// (§4.3, Sol's Nov 7 ruling) are the only pulses.

// MARK: - RW-147 dock residence (one system: rest band + scroll clearance)
//
// RW-147 (owner device pass, Nov 9, 2026 — the dock over
// readable content at rest, on Android in every viewport): the
// iOS audit found the same family in the new geometry. The dock
// is a bottom-anchored overlay (inset 12, bottom 14, §2.1) and
// the scroll system carried NO bottom clearance at all — the
// stacked layouts' content ended flush at the footer (bottom
// padding 0) and the Duo column's at 16 pt — so (a) at first
// open, whatever sheet content happened to fall in the dock's
// rest band (viewport bottom − 70 pt for the 56 pt dock) was
// ghosted behind it: the outfit stack's third row at short
// heights and whenever the convergence band stands, the glance
// band's label row at the standard height (the RW-144 lane's
// own §10 flag); and (b) content could never be scrolled fully
// clear of the band except through the footer's handoff. The
// residence and the scroll-end padding are therefore specified
// together, here:
// - the hero height policy (HeroHeightPolicy, FirstScreenHero)
//   keeps the first-open stack's required bottom clear of the
//   rest band wherever the viewport's geometry allows it;
// - every Home scroll view carries `scrollEndClearance` of
//   bottom padding BELOW the page content, so at scroll end
//   the whole page rests clear above the band, and every
//   element can be scrolled fully out from under the dock;
// - RW-152 §1: the value is EXACTLY the mini-dock reservation
//   (RW-147 invariant) — the expanded panel is an overlay state
//   and reserves nothing in the scroll; the former footer-top
//   glide bookkeeping that derived from this constant is gone.
enum DockResidence {
    /// The dock's bottom inset (§2.1: 14 pt above the bottom edge).
    static let bottomInset: CGFloat = 14
    /// The dock's side insets (§2.1: 12 pt).
    static let horizontalInset: CGFloat = 12
    /// The single-line dock's measured height. RB-04
    /// (RW-135 rebuild, Nov 12, 2026): the prototype's own
    /// footprint — padding 11 top + one 26 pt content row +
    /// padding 12 bottom = 49 pt. (The 56 pt figure that
    /// stood here was the 44 pt-control formula's output —
    /// the "room" Sol's recording showed around the content.)
    static let measuredDockHeight: CGFloat = 49
    /// RW-170 (Nov 12, 2026) — ONE BAND: the host's reserved
    /// clearance prices the PRESENTED (mini) form only: the
    /// presented height + its 14 pt lift. At the one-line rest
    /// that is 49 + 14 = 63 pt. A wrapped narration grows the
    /// presented height and the reserve follows it through
    /// `clearance(forPresentedMiniHeight:)` — the host's two
    /// clearance sites consume THAT quantity, never a constant
    /// sized for a state that is not on screen. The expanded
    /// form's height is NEVER reserved: it grows over the sheet
    /// from this same residence. (The 80 pt constant that stood
    /// here priced 17 pt of empty band above the presented card
    /// at every scroll position — DIAGNOSIS Q1/Q4.)
    static let scrollEndClearance: CGFloat = bottomInset + measuredDockHeight

    /// The clearance for a presented mini form of the given
    /// height: exactly its height plus the bottom lift.
    static func clearance(forPresentedMiniHeight height: CGFloat) -> CGFloat {
        bottomInset + height
    }

    /// The dock band's top edge in a viewport of `viewportHeight`
    /// for a dock of `dockHeight`.
    static func dockTop(viewportHeight: CGFloat, dockHeight: CGFloat) -> CGFloat {
        viewportHeight - bottomInset - dockHeight
    }
}

enum WearIveRunState: Equatable {
    /// No entry for today yet — the bar does not render.
    case preAdd
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
    /// RW-150 (Nov 9, 2026): the entry's recorded MOMENT rides
    /// along — a future-day entry settled at a picked time must
    /// name that time (RW-98's full-naming rule governs the
    /// content; §7.2's form governs the rendering). Before this
    /// the state carried no moment at all, so a 2:00 PM pick
    /// narrated as "Planned for {place} · Tomorrow" with the
    /// time nowhere in the line.
    case planned(place: String, dayLabel: String, moment: MomentReading)
}

enum WearIveRunMiniBarModel {
    static let wordmark = "Wear I've Run"

    /// The bar's state from the session's entry: absent without
    /// one; a future-day entry is the planned form; anything
    /// else is "Updated to {place}" — a creation reads exactly
    /// like a later settle (spec §9.1: no "Added" line; the
    /// creation moment is the §4.1 cue's, not a wording's).
    ///
    /// WS-4: the payload derives from the ENTRY, never from a
    /// caller-assembled string — the entry's place in its
    /// simple form (city + abbreviated region). (The pre-WS-4
    /// iOS caller fed the line a "{n} items · {temp}" summary
    /// the entry never said; the Android holder fed it the raw
    /// place + "Today" — D-08 on both sides, different
    /// strings.)
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
                    for: entry, calendar: calendar, now: now),
                moment: entry.momentReading)
        }
        // RW-135 spec §9.1 (approved Nov 7, 2026): there is NO
        // "Added" line on these surfaces. A creation
        // (wasUpdatedThisSession == false) reads exactly like a
        // later settle — "Updated to {place}" — and the creation
        // moment is marked by the motion cue (§4.1), not by a
        // wording. RW-138/WT-7 (Nov 7, 2026): the former
        // `.added` state case and its formatter branches are
        // REMOVED — the superseded line is now unrepresentable
        // rather than merely unproduced, so §9.1 conformance no
        // longer rests on caller discipline. (Stored-data
        // decode never involved this state type: the entry's
        // own `wasUpdatedInSession` flag in RW26CollectionModels
        // is a separate stored field and is untouched.) §7.2's
        // settled-today place form stands for both branches: a
        // moment-only change reads as the place the entry
        // holds — the entry still stores its C-7 update payload
        // for the collection surfaces that render it.
        _ = wasUpdatedThisSession
        _ = moment
        // RW-150 (Nov 9, 2026) — the settled-today grammar,
        // reconciled: RW-98 (Sol, Nov 6) governs CONTENT — the
        // line catches whichever condition changed and names
        // the new state IN FULL; RW-135 §1.4/§7.2 governs FORM
        // (the Updated/Planned distinction, the wrap, the pure
        // function of the recorded entry). The pre-fix builder
        // discarded both the entry's recorded moment and its
        // C-7 update payload and always rendered the bare
        // place — so the line read "Updated to New York, NY"
        // at Now (the ", Now" segment missing) and never
        // named a picked time at all (Sol's recording, build
        // 33). The composition, from the entry alone:
        //   · a TIME-only settle (the record-time payload is a
        //     clock moment) names the moment, per Sol's own
        //     example: "Updated to 7 p.m."
        //   · every other settle (creation, a place change, a
        //     return to Now) names the full state — place AND
        //     moment: "Updated to Miami, FL, Now" (Sol's
        //     example), "Updated to Miami, FL, 7 p.m." for a
        //     place settled at a picked hour.
        // §1.4's "settled-today names the place, not the
        // moment" is superseded on the moment segment by
        // RW-98/RW-150; its forms stand. Flagged in the lane
        // report as the one genuine spec tension in this item.
        if let payload = entry.updatePayload,
           case .moment(let reading) = payload,
           case .clock = reading {
            return .updated(payload: .moment(reading))
        }
        return .updated(payload: .placeAtMoment(
            entry.placeDisplayName, entry.momentReading))
    }
}

// MARK: - Narration formatter

enum WearIveRunNarration {
    /// - preAdd: nil (the bar does not render at all)
    /// - updated: "Updated to {payload}" (payload renders pine)
    ///   (RW-98: the settled verb form is "Updated to" — the
    ///   gen-5 "Update to" wording is retired.)
    /// - planned: "Planned for {place} · {day}"
    /// There is no "Added" form (spec §9.1; the state case was
    /// removed in the RW-138 pass — see the derivation note).
    static func line(for state: WearIveRunState) -> String? {
        switch state {
        case .preAdd:
            return nil
        case .updated(let payload):
            return "Updated to \(payload.rendered)"
        case .planned(let place, let dayLabel, let moment):
            return "Planned for \(place)\(plannedSuffix(dayLabel: dayLabel, moment: moment))"
        }
    }

    /// RW-150: the planned line's suffix after "Planned for
    /// {place}" — the day segment and, when the entry was
    /// settled at a picked time, the time segment (RW-98: the
    /// picked time is named in full). A clock reading appends
    /// " · {time}" to the day word ("· Tomorrow · 2 p.m."); a
    /// legacy daypart reading already carries its own day word,
    /// so it stands alone ("· Tomorrow afternoon") rather than
    /// naming the day twice; a Now reading on a future day has
    /// no time to name and the day word stands alone. The rich
    /// text builders compose from this same suffix, so the
    /// plain line and the styled line can never diverge.
    static func plannedSuffix(dayLabel: String, moment: MomentReading) -> String {
        switch moment {
        case .clock:
            return " · \(dayLabel) · \(moment.rendered)"
        case .daypart:
            return " · \(moment.rendered)"
        case .now:
            return " · \(dayLabel)"
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
    /// (a fresh creation, narrated "Updated to {place}" per
    /// §9.1, as after any trash).
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

    /// RW-159/RW-160 (device verdict, Nov 10, 2026): persist a
    /// bank-recovered durable reference onto the entry that
    /// settled without one (HeroRecovery / HeroRefRuntime's
    /// recovering resolution report it here), so the recovery
    /// happens ONCE — later renders resolve through the entry's
    /// own durable identity like any signed-ref entry. A plain
    /// store write of the one entry: no narration, no cue, no
    /// settle semantics — the entry's content does not change,
    /// only its hero identity is restored. Refused for an entry
    /// that already carries a loadable reference (its own
    /// identity always wins).
    func healHeroReference(entryID: String, durableReference: String) {
        guard let entry = entries.first(where: { $0.id == entryID }),
              !HeroRefResolver.hasLoadableReference(entry),
              !durableReference.isEmpty else { return }
        store.record(entry.withHeroDurableReference(durableReference))
        entries = store.entries
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

    /// RW-135 §7.4 / spec §4.3: the narration cross-fade's
    /// length — opacity only, out 95 ms + in 115 ms ≈ 210 ms
    /// total, when a settle lands on a changed record.
    static let narrationCrossfadeSeconds: Double = 0.21

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

    /// RW-170 (Nov 12, 2026) — ONE SURFACE, Sol's ruling: the
    /// unified surface paints ONE token in BOTH states — the
    /// EXPANDED state's darker warm tone: light the page-ground
    /// family #F5F1E8 (`AppTheme.groundLightHex`, the §2.2
    /// footer ground), dark the warm-charcoal #2A251E
    /// (`AppTheme.cardDarkHex`, spec §6). The MINI state's old
    /// token (#FDFBF6 sheet) is superseded for this surface.
    /// The morph animates ALPHA ONLY (tintOpacity above: the
    /// mini rest alpha 0.85 light / 0.82 dark → 1.00 expanded);
    /// there is no colour cross-fade anywhere in the surface.
    static func unifiedSurfaceHex(isDark: Bool) -> String {
        isDark ? AppTheme.cardDarkHex : AppTheme.groundLightHex
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

    // MARK: RW-170 (Nov 12, 2026) — ONE narration, moved not cross-faded
    //
    // SUPERSEDES the RB-03 two-placement dissolve: the
    // narration is ONE view whose slot migrates with progress
    // (font 12 → 15 pt, beside-wordmark → below-header, width
    // flexing between the two slots' widths), at full opacity
    // throughout. Exactly one narration exists in the view and
    // semantics tree at every progress value, so the RW-152
    // §1.3 legibility invariant holds BY CONSTRUCTION — there
    // is never a second copy to ghost (DIAGNOSIS Q3, cause 2:
    // the two placements cross-fading through a dead zone read
    // as a swap, not a transform). Expanded-only content
    // (entry row + doorway) still dissolves in over p in
    // [0.45, 1] (the standing window above), laid out at its
    // expanded geometry inside the growing surface.

    /// The morph's length and curve (spec §4 tokens): 380 ms,
    /// cubic-bezier(0.32, 0.72, 0, 1).
    static let morphDurationSeconds: Double = 0.38
    static let morphCurve: (Double, Double, Double, Double) = (0.32, 0.72, 0, 1)

    /// The morph animation itself — one timing curve of the
    /// one progress value. Reduced Motion: an opacity
    /// crossfade ≤150 ms, no travel (the caller still runs
    /// the same progress; the surface suppresses its offsets).
    static func morphAnimation(reduceMotion: Bool) -> Animation {
        if reduceMotion {
            return .easeInOut(duration: reducedMotionDurationSeconds)
        }
        return .timingCurve(
            morphCurve.0, morphCurve.1, morphCurve.2, morphCurve.3,
            duration: morphDurationSeconds)
    }

    /// Smoothstep over an arbitrary window.
    static func smoothstep(_ progress: Double, _ start: Double, _ end: Double) -> Double {
        let t = min(1, max(0, (clamped(progress) - start) / (end - start)))
        return t * t * (3 - 2 * t)
    }

    /// RW-170: the ONE narration's font size — 12 pt at the
    /// mini slot, 15 pt at the expanded slot, linear in
    /// progress (the slot's position and width lerp on the
    /// same progress in the surface's layout).
    static func narrationFontSize(progress: Double) -> Double {
        12 + 3 * clamped(progress)
    }

    /// RW-170: the ONE narration's opacity — 1 at every
    /// progress value. It is never dissolved; it MOVES.
    static func narrationOpacity(progress: Double) -> Double {
        _ = clamped(progress)
        return 1
    }

    /// The expanded-only content's alpha over p in [0.45, 1]
    /// (the standing expanded-content window, under the name
    /// the unified surface uses).
    static func expandedOnlyOpacity(progress: Double) -> Double {
        expandedContentOpacity(progress: progress)
    }

    // RW-152 §1: the RW-135 §3 handoff mathematics and the
    // §4.4 glide mathematics that stood here are REMOVED with
    // the two-surface split they served — there is no footer
    // position to hand off to and no page glide; the one
    // expansionProgress in WearIveRunOverlayView drives the
    // height/opacity functions above, in place.

    /// Update-bounce peak (spec §4.3, Sol's Nov 7, 2026
    /// ruling): ≈1.5% overshoot when the deferral gate
    /// releases a changed line.
    static let updateBounceScale: Double = 1.015

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
    /// (The expanded panel renders the expanded anatomy from
    /// its own copy of this same record — one record, two
    /// states of the one overlay surface.)
    let entry: WearIveRunEntry?
    /// The feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    /// Cue-worthy landing counter (creation only) — a change is
    /// fed to the entrance logic as a creation event.
    let cueCount: Int
    /// RW-135 spec §4.3: the collection model's narration
    /// version — bumped when the derived line changes. The
    /// cross-fade itself is driven by `state`; this value is
    /// bookkeeping for the surfaces' shared cue discipline.
    var narrationVersion: Int = 0
    /// RW-152 §1 (carrying §4.7): host-orchestrated exit
    /// progress (0 = at rest, 1 = fully exited: faded + sunk
    /// 26 pt; the overlay container folds the height).
    var exitProgress: Double = 0
    /// True while any sheet is presented over home (WS-5 owns the
    /// sheets; this view only consumes their presented state):
    /// the element dims WITH the home backdrop (D-11).
    var isDimmedBySheet: Bool = false
    /// RW-135 §7.5: true while the host page's scroll gesture (or
    /// its deceleration) is live. A settle that lands mid-gesture
    /// defers its narration cross-fade to the frame after the
    /// gesture ends.
    var isScrollGestureActive: Bool = false
    /// RW-152 §1: tapping the dock body or its expand control
    /// EXPANDS the one overlay surface in place (the overlay
    /// container morphs this bar into its expanded panel
    /// state). There is no page glide anymore — the RW-135
    /// §4.4 glide to an in-flow footer is superseded.
    var onExpand: () -> Void = {}
    let onOpenCollection: () -> Void
    let onTrash: () -> Void

    /// Sensory-feedback triggers: incremented ONLY by the cue /
    /// bounce / glide / trash paths, so each haptic fires
    /// exactly once per cause (§1.7).
    @State private var cueHapticNonce = 0
    @State private var trashHapticNonce = 0
    @State private var glideHapticNonce = 0
    /// The cue pulse's scale (1.00 → 1.018 → 1.00 over 900 ms).
    @State private var pulseScale: CGFloat = 1
    @State private var isPulsing = false
    /// The creation cue's pine ring (§4.1): blooms and decays
    /// over 900 ms alongside the pulse scale.
    @State private var ringScale: CGFloat = 1
    @State private var ringOpacity: Double = 0
    /// The update bounce's scale (§4.3): 1.00 → 1.015 → 1.00.
    @State private var bounceScale: CGFloat = 1
    /// The glide tap's press scale (§4.4): 0.97 over 120 ms.
    @State private var pressScale: CGFloat = 1
    /// Creation entrance (§4.1): 0 = risen-from position
    /// (+160% of the dock's height, faded), 1 = at rest.
    @State private var entranceProgress: Double = 1
    @State private var didRunEntrance = false
    /// The rendered content's own measured height — used ONLY
    /// by the creation entrance's rise distance (RW-86: this
    /// measurement never gates painting; the bar renders at its
    /// natural height in every state).
    @State private var dockHeight: Double = 0
    /// RW-135 §7.4/§7.5: the narration state the bar is actually
    /// SHOWING. The rendered line derives from this, not directly
    /// from `state`: when a settle changes the recorded line, the
    /// shown state cross-fades (opacity only, ≈210 ms) to the new
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
            dockBar
                // Opacity/sink/scale are pure functions of the
                // entrance, exit, and pulse state — RW-152 §1
                // removed the scroll-position handoff term the
                // RW-135 §3 dissolve used to contribute.
                .opacity(dockOpacity)
                .offset(y: dockOffsetY)
                .scaleEffect(dockScale)
                .allowsHitTesting(
                    exitProgress == 0
                        && WearIveRunElementDimming.isInteractive(sheetPresented: isDimmedBySheet))
                // §1.7: the cue's soft haptic, the update
                // bounce's soft haptic (same trigger), the
                // glide's light haptic and the trash's light
                // haptic are the ONLY sounds this surface makes.
                .sensoryFeedback(.impact(flexibility: .soft), trigger: cueHapticNonce)
                .sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)
                .sensoryFeedback(.impact(weight: .light), trigger: glideHapticNonce)
                .onAppear {
                    if displayedState == nil {
                        displayedState = state
                        _ = narrationDeferral.offer(
                            line: WearIveRunNarration.line(for: state),
                            gestureActive: false)
                    }
                    runEntranceIfNeeded()
                }
                .onChange(of: cueCount) { oldValue, newValue in
                    // A NEW creation while the dock stands (a
                    // settle re-creating the entry after a
                    // collection-side removal): replay the §4.1
                    // entrance + cue. The ordinary creation is
                    // handled at appear (the dock mounts with
                    // the entry).
                    guard newValue > oldValue, didRunEntrance else { return }
                    didRunEntrance = false
                    runEntranceIfNeeded()
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
        }
    }

    /// Opacity composition: backdrop dim (D-11) × exit
    /// (1−progress) × entrance fade.
    private var dockOpacity: Double {
        WearIveRunElementDimming.opacity(sheetPresented: isDimmedBySheet)
            * (1 - exitProgress) * entranceProgress
    }

    /// Vertical offset: the creation rise (+160% of height →
    /// 0) and the exit sink (+26). Reduce Motion removes the
    /// rise and the exit sink (§5).
    private var dockOffsetY: CGFloat {
        let rise = reduceMotion ? 0 : CGFloat(1 - entranceProgress) * CGFloat(dockHeight) * 1.6
        let exitSink = reduceMotion ? 0 : CGFloat(26 * exitProgress)
        return rise + exitSink
    }

    /// Scale composition: cue pulse × update bounce × press.
    private var dockScale: CGFloat {
        pulseScale * bounceScale * pressScale
    }

    // MARK: The dock bar (one row, natural height)

    /// The dock's single rendered form: ONE copy of the content,
    /// rendered at its natural height in every state (RW-86:
    /// no self-measurement in the paint path — the one
    /// measurement below serves only the entrance's rise
    /// distance). RW-152 §1: this bar is the overlay surface's
    /// MINI state. (Retained standalone form: the production
    /// host composes the ONE unified surface —
    /// WearIveRunUnifiedSurfaceView — whose single composition
    /// carries both states; this view and
    /// WearIveRunExpandedPanelView remain as the states'
    /// reference forms and test fixtures, and paint the same
    /// ruled token so the file carries one surface law.)
    private var dockBar: some View {
        headerRow
            .onGeometryChange(for: Double.self) { proxy in
                proxy.size.height
            } action: { _, height in
                if height > 0 { dockHeight = height }
            }
            .background {
                // §7.6 / spec §2.1: the MINI material — the
                // translucent tint over the system material
                // blur, on the RW-170 ruled unified token
                // (light #F5F1E8 at 85% / dark #2A251E at 82% —
                // ONE token in both states, alpha only). The
                // material itself is never animated (decision
                // §4 — no blur animation).
                ZStack {
                    Rectangle().fill(.regularMaterial)
                    Rectangle().fill(
                        Color(hex: WearIveRunMorph.unifiedSurfaceHex(
                            isDark: colorScheme == .dark)).opacity(WearIveRunMorph.tintOpacity(
                            progress: 0,
                            isDark: colorScheme == .dark
                        ))
                    )
                }
            }
            .overlay {
                // §4.1: the creation cue's pine ring — a stroked
                // rounded rect that blooms slightly and decays
                // over 900 ms.
                if isPulsing {
                    RoundedRectangle(cornerRadius: WearIveRunMorph.miniCornerRadius)
                        .stroke(AppTheme.pinePayload, lineWidth: 2)
                        .scaleEffect(ringScale)
                        .opacity(ringOpacity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: 0)))
            // RW-162 G4(a) (device verdict, Nov 10, 2026): the
            // boundary hairline — the dock's material is 85%/82%
            // translucent, so over list content its edge washed
            // out and the pill read as a straight-edged block
            // slicing the row beneath. A half-point hairline in
            // the scheme's ink gives the floating surface a real
            // edge against ANY content under it, in the same
            // language as the expanded panel's (G5).
            .overlay {
                RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: 0))
                    .strokeBorder(
                        colorScheme == .dark
                            ? Color.white.opacity(0.14)
                            : Color.black.opacity(0.08),
                        lineWidth: 0.5)
            }
            // §7.6: the soft shadow (0 offset-x, 6 offset-y, black
            // 14%; SwiftUI's radius is half the CSS blur, so the
            // design's 24 pt blur is radius 12 here).
            .shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 6)
    }

    /// The continuity anchor: wordmark, narration, and both
    /// controls — present in every state, never moving. RW-135
    /// §7.2/§7.6 (Direction A): the MINI bar is ONE row — the
    /// Great Vibes wordmark at 17 pt, the one narration line at
    /// 12 pt, and the expand + delete controls right-aligned.
    /// The narration renders from `displayedState` — the
    /// recorded state the bar is showing — so a changed settle
    /// cross-fades the line in place (§7.4): the text is keyed
    /// by its line inside an opacity transition.
    private var headerRow: some View {
        HStack(spacing: 8) {
            wordmarkView
                .allowsHitTesting(false)
            let shown = displayedState ?? state
            if let line = WearIveRunNarration.line(for: shown) {
                // RW-138 (RW-135 spec §4.3): the narration WRAPS
                // and the bar's height follows — there is NO
                // line cap, so the payload can never be
                // ellipsized at any type size. (The two-line cap
                // that stood here cut the plan form's day —
                // "Tomorr…" — and long place names mid-word.)
                dockNarration(line, for: shown)
                    // RW-162 G4(a): the narration takes layout
                    // priority over the wordmark and the fixed
                    // controls, so the wrap the RW-138 ruling
                    // allows composes inside the pill instead of
                    // cramming against its controls.
                    .layoutPriority(1)
                    .id(line)
                    .transition(.opacity)
                    .allowsHitTesting(false)
            }
            Spacer(minLength: 0)
            barControls
        }
        .padding(.horizontal, 16)
        // RB-04: the prototype's own vertical paddings —
        // 11 top / 12 bottom around the one 26 pt row.
        .padding(.top, 11)
        .padding(.bottom, 12)
        .contentShape(Rectangle())
        // RB-02: the body tap is a background tap layer BELOW
        // the controls, not an onTapGesture stacked over them
        // (the stacked recognizers could double-fire or
        // swallow the toggle Button's action).
        .background {
            Button {
                handleExpandTap()
            } label: {
                Color.clear.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHidden(true)
        }
    }

    /// §4.3: apply a changed recorded line as an opacity-only
    /// cross-fade (≈210 ms total) — and, per Sol's Nov 7, 2026
    /// ruling (§11.1 resolved), the surface ALSO gives the
    /// update bounce when the deferral gate releases the line.
    /// Never concurrent with a scroll gesture (the parking in
    /// `onChange(of: state)`), and never doubled with the
    /// creation cue: this path runs only for a line released
    /// AFTER the dock mounted with its first line. (RW-152 §1:
    /// the bounce always plays here now — there is no second
    /// surface to hand the cue to.)
    private func applyNarrationCrossfade(to newState: WearIveRunState) {
        guard displayedState != newState else { return }
        if exitProgress == 0 {
            runUpdateBounce()
        }
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
            // RW-135 §7.6: the wordmark in the bar is 17 pt.
            .font(.greatVibes(size: 17))
            .foregroundColor(AppTheme.inkPrimary)
            // RW-169 §7B (width law, D1): the wordmark is a
            // FIXED-CLAIM element — it never yields. It carries
            // the row's highest layout priority and a fixed
            // horizontal size, so its rendered width is its rest
            // width at every row width; when space runs out the
            // NARRATION wraps and the bar grows (spec §4.3).
            // (RW-162 G4(a) gave the narration priority 1 and the
            // wordmark no floor at all — at 393 pt the wordmark
            // was left ≈33 pt and wrapped character-wise.)
            .fixedSize(horizontal: true, vertical: false)
            .layoutPriority(2)
    }

    /// Two tiny grayed controls at the right end (spec §9.2):
    /// the expand control is the corners-out glyph
    /// ("arrow.up.left.and.arrow.down.right"), NO rotation —
    /// it performs the same in-place expansion as tapping the
    /// bar body (RW-152 §1); trash routes to the host's §4.7
    /// exit orchestration. The SAME two controls in every
    /// state.
    private var barControls: some View {
        HStack(spacing: 4) {
            Button {
                handleExpandTap()
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    // RB-04: the 26 pt glyph box of the
                    // prototype — the old 44 pt frames were
                    // what stood the bar at ≈60 pt.
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Expand")
            Button {
                // §4.7: light trash haptic at tap (existing);
                // the host orchestrates the exit and fires the
                // store removal when the longest exit completes
                // — the dock does NOT hide itself first.
                trashHapticNonce += 1
                onTrash()
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove today's entry")
        }
    }

    /// RW-138: the dock's narration in its wrap-and-grow form
    /// (RW-135 spec §4.3). The settled form is a single text
    /// with NO line limit: it wraps at word boundaries and the
    /// bar grows — the payload is never truncated, at default
    /// type or AX5. The planned form carries §4.3's break
    /// rule — the wrap breaks before "· {day}", never inside
    /// the place name — expressed structurally: a one-line
    /// candidate (taken whenever the whole line fits the
    /// offered width) and a stacked candidate in which the
    /// head (verb + place, itself wrapping uncapped at word
    /// boundaries) stands over the day segment on its own
    /// line. `ViewThatFits` picks the first candidate that
    /// fits; neither candidate can ellipsize the payload.
    @ViewBuilder
    private func dockNarration(_ line: String, for shownState: WearIveRunState) -> some View {
        switch shownState {
        case .planned(let place, let dayLabel, let moment):
            ViewThatFits(in: .horizontal) {
                narrationText(line, for: shownState)
                    .font(.system(size: 12))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                VStack(alignment: .leading, spacing: 0) {
                    plannedNarrationHead(place: place)
                        .font(.system(size: 12))
                    // RW-150: the stacked candidate's second
                    // line carries the same suffix as the
                    // one-line form (day + picked time) — the
                    // shared plannedSuffix, minus its leading
                    // space (it opens its own line here).
                    Text(String(WearIveRunNarration.plannedSuffix(
                        dayLabel: dayLabel, moment: moment).dropFirst()))
                        .font(.system(size: 12))
                        .foregroundColor(AppTheme.inkSecondary)
                }
            }
        default:
            narrationText(line, for: shownState)
                .font(.system(size: 12))
        }
    }

    /// The planned narration's head segment: "Planned for
    /// {place}" — the verb in secondary ink, the place in pine
    /// semibold (the same spans the one-line form renders).
    private func plannedNarrationHead(place: String) -> Text {
        Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
            + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
    }

    /// The narration line's rich form: the verb in secondary
    /// ink, the payload in pine (RW-135 §7.6: the settled-today
    /// place in pine BOLD). Every case derives from the SHOWN
    /// recorded state and nothing else — §7.0: the RW-88
    /// viewed-state override branch is removed outright.
    private func narrationText(_ line: String, for shownState: WearIveRunState) -> Text {
        switch shownState {
        case .updated(let payload):
            // RW-150: the full-state payload styles like the
            // settled-today form always did — the PLACE in pine
            // bold; its moment segment rides in secondary ink
            // (the moment is context, the place is the payload).
            if case .placeAtMoment(let place, let moment) = payload {
                return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                    + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                    + Text(", \(moment.rendered)").foregroundColor(AppTheme.inkSecondary)
            }
            return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .planned(let place, let dayLabel, let moment):
            // §7.2: "Planned for {place} · {day}" — the place in
            // pine bold, the day word in secondary ink. RW-150:
            // the suffix (day + any picked time) comes from the
            // one shared composition the plain line uses.
            return Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(WearIveRunNarration.plannedSuffix(dayLabel: dayLabel, moment: moment)).foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }

    // MARK: RW-152 §1 — the expand tap

    /// Press (scale 0.97 over 120 ms) + one light haptic, and
    /// the overlay container morphs the surface to its
    /// expanded state in place — the page never moves.
    ///
    /// RW-162 G1 (device verdict, Nov 10, 2026 — "the Wear I've
    /// Run never expands into the expanded view"): the pre-fix
    /// form fired `onExpand()` from the press animation's
    /// COMPLETION closure. The dock's scale is a composition
    /// (cue pulse × update bounce × press) re-animated by
    /// settles and narration crossfades landing constantly —
    /// any transaction superseding the press animation could
    /// swallow the completion, and with it the expansion: the
    /// tap visibly pressed and nothing happened, in exactly the
    /// busy scroll/settle states the user exercised. A control's
    /// action never rides an animation completion: the
    /// expansion fires SYNCHRONOUSLY in the tap's own
    /// transaction, and the press scale is decoration that
    /// follows it.
    private func handleExpandTap() {
        guard exitProgress == 0 else { return }
        glideHapticNonce += 1
        onExpand()
        if reduceMotion { return }
        withAnimation(.easeInOut(duration: 0.12)) {
            pressScale = 0.97
        } completion: {
            withAnimation(.easeOut(duration: 0.15)) { pressScale = 1 }
        }
    }

    // MARK: §4.1 — creation entrance + cue

    /// The entrance runs ONLY on a genuine creation (cueCount
    /// bumped by the machine's entryCreated path): a cold start
    /// over an already-present entry (entryAlreadyPresent,
    /// cueCount == 0) appears silently at rest.
    private func runEntranceIfNeeded() {
        guard !didRunEntrance else { return }
        didRunEntrance = true
        guard cueCount > 0 else {
            entranceProgress = 1
            return
        }
        if reduceMotion {
            // §5: fade in 220 ms, no pulse; haptic kept.
            entranceProgress = 0
            withAnimation(.easeInOut(duration: 0.22)) {
                entranceProgress = 1
            } completion: {
                cueHapticNonce += 1
            }
            return
        }
        entranceProgress = 0
        withAnimation(.spring(response: 0.5, dampingFraction: 0.82)) {
            entranceProgress = 1
        } completion: {
            fireCueSettle()
        }
    }

    /// The cue's settle moment: fire the ONE pulse and the ONE
    /// soft haptic (§1.7), together, exactly here — the first
    /// frame the entrance completes.
    private func fireCueSettle() {
        cueHapticNonce += 1
        runCuePulse()
    }

    /// The ONE cue pulse: scale 1.00 → 1.018 → 1.00 with the
    /// pine ring blooming and decaying, 900 ms total, exactly
    /// once — never a loop (§4.1). Disabled under Reduce
    /// Motion (§5); the haptic still happens.
    private func runCuePulse() {
        guard !reduceMotion else { return }
        isPulsing = true
        ringScale = 1
        ringOpacity = 0.9
        withAnimation(.easeOut(duration: WearIveRunElementMachine.cuePulseDurationSeconds)) {
            ringScale = 1.08
            ringOpacity = 0
        }
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

    // MARK: §4.3 — the update bounce

    /// The update bounce: scale to ≈1.015 overshoot and back,
    /// with ONE soft haptic at the settle. Reduce Motion
    /// removes the bounce; the haptic is KEPT (§5).
    private func runUpdateBounce() {
        if reduceMotion {
            cueHapticNonce += 1
            return
        }
        withAnimation(.easeOut(duration: 0.16)) {
            bounceScale = CGFloat(WearIveRunMorph.updateBounceScale)
        } completion: {
            withAnimation(.easeInOut(duration: 0.22)) {
                bounceScale = 1
            } completion: {
                cueHapticNonce += 1
            }
        }
    }
}

// MARK: - RW-152 §2 — the expanded panel's sizing policy

/// RW-152 §2: the expanded panel is CONTENT-SIZED — its height
/// is the intrinsic height of (header + narration + one entry
/// row + doorway + settled paddings), ≈240–300 pt at default
/// type. The one cap: viewport height minus the TOP CHROME
/// ZONE (status area + wordmark/place pill zone), so the panel
/// can never slide under the top chrome. Only the ENTRY REGION
/// scrolls internally, and only when the cap binds; the header,
/// narration, and doorway are always fully visible. Pure, so
/// the panel-height == content-height property is pinned per
/// viewport class and accessibility scale without a window.
enum WearIveRunOverlayPolicy {
    /// Height of the top chrome zone the panel must never
    /// enter (status area, wordmark, place pill, status line).
    static let topChromeZoneHeight: CGFloat = 116
    /// Everything in the panel except the entry region:
    /// top padding 20 + header 44 + spacing 12 + narration
    /// allowance 44 + spacing 12 + doorway 24 + bottom
    /// padding 12 (RB-04: the settled overlay bottom padding
    /// is 12 — the A′ in-flow seat's larger bottom padding
    /// does not carry into the overlay).
    static let nonEntryChromeHeight: CGFloat = 168

    /// The cap for a viewport: viewport − top chrome zone.
    static func expandedCap(viewportHeight: CGFloat) -> CGFloat {
        max(0, viewportHeight - topChromeZoneHeight)
    }

    /// The expanded form's top padding (RB-04): the script
    /// title seats this far below the panel's top edge. The
    /// unified surface's own `expandedTopPadding` constant
    /// references this value — one anatomy, one number.
    static let expandedTopPadding: CGFloat = 20

    /// RW-173 (Nov 13, 2026): the panel-height cap that keeps
    /// the expanded title clear of the Make It Me straddle
    /// pill at home rest. Code-level geometry from the real
    /// placement constants (see RW135RebuildTests
    /// testRW173_PillGeometry_TitleReachability): the pill's
    /// bottom sits at HeroHeightPolicy.stackedHeight +
    /// HeroVerticalLayout.straddleDip − scrollOffset; the
    /// panel's bottom sits at viewportHeight −
    /// DockResidence.bottomInset; the title seats
    /// expandedTopPadding below the panel's top. In the
    /// short viewport class (and marginally at 812 pt with a
    /// four-line entry) the uncapped panel's title crosses
    /// the pill's bottom edge — the Android F1 defect's
    /// geometry exists on iOS too, so the panel yields here
    /// as well: its height is capped at the value whose top
    /// edge puts the title exactly at the pill's bottom.
    /// [pillBottomY] is the pill bottom's current screen Y
    /// (nil when no pill renders or the seat is not the
    /// stacked straddle seat — e.g. the Duo pane seat, whose
    /// pill rides inside the hero pane and is not this
    /// surface's neighbour).
    static func pillTitleCap(
        viewportHeight: CGFloat, pillBottomY: CGFloat
    ) -> CGFloat {
        max(0, (viewportHeight - DockResidence.bottomInset)
            - pillBottomY + expandedTopPadding)
    }

    /// The cap in force: the top-chrome cap, further bound
    /// by the pill-title cap while the pill is in its
    /// straddle seat near the panel's expansion zone.
    static func effectiveCap(
        viewportHeight: CGFloat, pillBottomY: CGFloat?
    ) -> CGFloat {
        let base = expandedCap(viewportHeight: viewportHeight)
        guard let pillBottomY else { return base }
        return min(base, pillTitleCap(
            viewportHeight: viewportHeight, pillBottomY: pillBottomY))
    }

    /// The height the panel stands at: the content's own
    /// height, or the cap when the cap binds — panel height
    /// EQUALS content height in every other case.
    static func panelHeight(contentHeight: CGFloat, viewportHeight: CGFloat) -> CGFloat {
        min(contentHeight, expandedCap(viewportHeight: viewportHeight))
    }

    /// True exactly when the cap binds and the entry region
    /// must scroll internally.
    static func entryRegionScrolls(contentHeight: CGFloat, viewportHeight: CGFloat) -> Bool {
        contentHeight > expandedCap(viewportHeight: viewportHeight)
    }

    /// The entry region's height budget inside a capped panel.
    static func entryRegionMaxHeight(viewportHeight: CGFloat) -> CGFloat {
        max(56, expandedCap(viewportHeight: viewportHeight) - nonEntryChromeHeight)
    }

    /// The fixed allowance below the chrome block: spacing
    /// 12 (chrome → region) + spacing 12 (region → doorway) +
    /// doorway 24 + bottom padding 12 (RB-04).
    static let doorwayAllowance: CGFloat = 60

    /// The entry region's standing height in the real panel,
    /// where the chrome block (header + narration) is MEASURED:
    /// the row's own height, unless the cap binds — then the
    /// cap minus the measured chrome and the doorway
    /// allowance, floored at the 56 pt thumbnail cell. This is
    /// what makes the cap a hard bound with the doorway
    /// always visible (§2), at any text scale.
    static func entryRegionHeight(
        cap: CGFloat, chromeHeight: CGFloat, rowHeight: CGFloat
    ) -> CGFloat {
        min(rowHeight, max(56, cap - chromeHeight - doorwayAllowance))
    }

    /// The panel's intrinsic content height from its anatomy:
    /// header 44 + narration lines (20 pt at 15 pt type, scaled)
    /// + the entry row (56 pt thumbnail, or wrapped entry text
    /// at 21 pt/line, scaled) + doorway 24 + two 12 pt spacings
    /// + 20 top / 12 bottom paddings (RB-04).
    static func contentHeight(
        narrationLineCount: Int,
        entryLineCount: Int,
        textScale: CGFloat
    ) -> CGFloat {
        let header: CGFloat = 44
        let narration = CGFloat(max(1, narrationLineCount)) * 20 * textScale
        let entry = max(56, CGFloat(max(1, entryLineCount)) * 21 * textScale)
        let doorway: CGFloat = 24
        return header + narration + entry + doorway + 36 + 32
    }
}

// MARK: - RW-152 §1/§2 — the EXPANDED state of the one overlay surface
//
// The former in-flow footer section's anatomy, carried into
// the overlay: Great Vibes 31 pt header + chevron-down (the
// dismissal) + delete controls; narration 15 pt; entry row
// (thumbnail 56 pt, radius 14, entry line 14.5 pt); and the
// "Open the collection ›" doorway — as a floating card on the
// dock's own material (BackgroundPolicy: the EXPANDED form is
// the opaque sheet tint), radius 16 on every corner, the
// dock's soft shadow. The panel carries the same §4.3/§4.5
// discipline as the dock: its line passes through the
// deferral gate and cross-fades, and its fields cross-fade in
// place. The footer's host bookkeeping (naturalHeight, grow
// and exit height fractions, cue seats) is gone: the overlay
// container owns the morph, and the host owns the exit.

struct WearIveRunExpandedPanelView: View {
    /// The recorded narration state (the same derivation the
    /// dock renders — one record, one line).
    let state: WearIveRunState
    /// The session's recorded entry.
    let entry: WearIveRunEntry
    /// The entry's feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    /// §4.3 bookkeeping: the model's narration version.
    var narrationVersion: Int = 0
    /// §7.5: true while the host page's scroll gesture is live
    /// — the panel's line defers its cross-fade identically.
    var isScrollGestureActive: Bool = false
    /// The host viewport's height — the §2 cap input.
    let viewportHeight: CGFloat
    /// §4.7 carried into the one surface: host-orchestrated
    /// exit opacity (the container folds the height).
    var exitContentOpacity: Double = 1
    /// RW-152 §3: the resolver's signing half, injected by the
    /// host (this file is dual-target; the API client is
    /// app-only). Stable references render without it.
    var heroURLResolver: ((WearIveRunEntry) async -> String?)?
    let onOpenCollection: () -> Void
    /// RW-158 (user ruling, Nov 10, 2026): the entry row's tap
    /// for a PAST entry opens the Share Studio sheet — the
    /// host (app target) presents it, since the Studio lives
    /// in an app-only file and this file is dual-target. Nil
    /// in seats with no share presentation (tests, watch):
    /// the row then keeps its collection route for every entry.
    var onOpenShareEntry: ((WearIveRunEntry) -> Void)?
    /// RW-152 §1: the chevron (or a vertical glide on the
    /// header) collapses the surface back to the mini dock —
    /// dismissal restores the page exactly, because the page
    /// never moved.
    var onCollapse: () -> Void = {}
    /// §4.7: trash — the host orchestrates the exit.
    var onTrash: () -> Void = {}

    @State private var displayedState: WearIveRunState?
    @State private var pendingNarrationState: WearIveRunState?
    @State private var narrationDeferral = WearIveRunNarrationDeferral()
    /// §4.5: the entry the panel is SHOWING — narration and
    /// entry line flip together at the cross-fade's midpoint;
    /// the thumbnail cross-fades when the hero image changes.
    @State private var displayedEntry: WearIveRunEntry?
    @State private var trashHapticNonce = 0
    /// RW-152 §2: the entry row's measured height — the entry
    /// region's standing height (before the cap). Seeded at
    /// the row's floor (the 56 pt thumbnail cell).
    @State private var entryRowHeight: CGFloat = 56
    /// RW-152 §2: the chrome block's measured height (header +
    /// narration at the live text scale) — the entry region's
    /// budget is computed against it, so the cap binds
    /// honestly at any scale. Seeded at the default-type
    /// anatomy (44 header + 12 spacing + 20 narration).
    @State private var chromeHeight: CGFloat = 76
    /// RW-152 §3: the entry's resolved hero URL when its
    /// reference is a storage path (re-signed by the injected
    /// resolver). Stable references render directly.
    @State private var resolvedHeroURL: String?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var shownEntry: WearIveRunEntry { displayedEntry ?? entry }
    private var shownState: WearIveRunState { displayedState ?? state }

    /// RW-152 §2: the entry region's budget — the cap minus
    /// the measured chrome and the doorway allowance, against
    /// the row's own height (the policy's arithmetic).
    private var regionBudget: CGFloat {
        WearIveRunOverlayPolicy.entryRegionHeight(
            cap: WearIveRunOverlayPolicy.expandedCap(
                viewportHeight: viewportHeight),
            chromeHeight: chromeHeight,
            rowHeight: entryRowHeight)
    }

    /// The entry region: the row at its natural height, or —
    /// only when the cap binds — the row inside a ScrollView
    /// standing at exactly the budget. (In the binding case
    /// the harness renderer cannot paint the scroll content;
    /// the standing case is the portrait norm.)
    @ViewBuilder private var entryRegion: some View {
        if entryRowHeight > regionBudget {
            ScrollView(.vertical, showsIndicators: false) {
                measuredEntryRow
            }
            .frame(height: regionBudget)
        } else {
            measuredEntryRow
        }
    }

    /// The entry row with its height capture (the height is a
    /// property of the row's content at the panel's width —
    /// independent of the region's frame, so the capture
    /// cannot feed back into the thing it measures).
    private var measuredEntryRow: some View {
        entryRow
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { _, height in
                if height > 0 { entryRowHeight = height }
            }
    }

    /// The thumbnail's loadable reference: a stable reference
    /// passes through the resolver synchronously; a storage
    /// path renders once the injected resolver has re-signed
    /// it (the task below) — empty stays honestly empty.
    private var thumbURLString: String? {
        HeroRefResolver.stableDisplayReference(for: shownEntry) ?? resolvedHeroURL
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // The chrome block (header + narration), measured:
            // its real height at the live text scale is what
            // the entry region's budget is computed against.
            VStack(alignment: .leading, spacing: 12) {
                headerRow
                // Narration (§2.2 anatomy): 15 pt secondary, payload
                // pine semibold — cross-fades in place via the
                // deferral gate, exactly like the dock's (§4.3/§4.5).
                if let line = WearIveRunNarration.line(for: shownState) {
                    // RW-138 (spec §4.3): no line cap here either —
                    // the panel narration wraps whole and the
                    // panel's height follows. Truncating the payload
                    // is never acceptable on either state.
                    panelNarrationText(line)
                        .font(.system(size: 15))
                        .id(line)
                        .transition(.opacity)
                }
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { _, height in
                if height > 0 { chromeHeight = height }
            }
            // RW-152 §2: the entry region — the panel's only
            // flexible element. It stands at the row's own
            // height; only when the cap binds (region budget
            // below the row's height) does it scroll internally.
            // A bare ScrollView here is greedy and would
            // inflate the panel to whatever height it is
            // offered, feeding the morph a false endpoint —
            // the region's height is computed, never proposed.
            entryRegion
            // §4.6: the doorway, visible in the expanded form
            // (Sol's Nov 4 requirement, W-1).
            Button(action: onOpenCollection) {
                Text("Open the collection ›")
                    .font(.system(size: 15.5, weight: .semibold))
                    .foregroundColor(AppTheme.pinePayload)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open the full Wear I've Run collection")
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
        .padding(.bottom, 20)
        .opacity(exitContentOpacity)
        // NO root height frame: the panel is content-sized by
        // construction — the cap binds through the entry
        // region's computed budget alone. (A root
        // `frame(maxHeight:)` made the panel height-flexible:
        // it expanded to any offered height and centered its
        // content — caught by this lane's expanded frames.)
        .background {
            // The dock's own material, at the EXPANDED state's
            // settled opacity (BackgroundPolicy: the opaque
            // RW-170 ruled unified token over .regularMaterial)
            // — one surface, one material, two states.
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(
                    Color(hex: WearIveRunMorph.unifiedSurfaceHex(
                        isDark: colorScheme == .dark)).opacity(WearIveRunMorph.tintOpacity(
                        progress: 1,
                        isDark: colorScheme == .dark
                    ))
                )
            }
        }
        .clipShape(RoundedRectangle(
            cornerRadius: WearIveRunMorph.cornerRadius(progress: 1)))
        // RW-162 G5 (device verdict, Nov 10, 2026): the boundary
        // hairline, in the mini dock's own language (G4(a)) —
        // the expanded state's edge definition against the
        // content beneath it. On device the panel read as a
        // flat full-width rectangle with no boundary at all:
        // its shadow was being clipped away by the overlay
        // container for the whole expanded rest state (fixed in
        // WearIveRunOverlayView's travel clip), and a shadow
        // alone over busy content is not an edge — the hairline
        // is what separates the surface from what it covers.
        .overlay {
            RoundedRectangle(
                cornerRadius: WearIveRunMorph.cornerRadius(progress: 1))
                .strokeBorder(
                    colorScheme == .dark
                        ? Color.white.opacity(0.14)
                        : Color.black.opacity(0.08),
                    lineWidth: 0.5)
        }
        // The dock's soft shadow (§7.6): 0 offset-x, 6 offset-y,
        // black 14%, radius 12.
        .shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 6)
        // §4.5: a re-wrap height change follows over the
        // 380 ms morph; Reduce Motion snaps it (§5).
        .animation(
            reduceMotion ? nil : .timingCurve(0.32, 0.72, 0, 1, duration: 0.38),
            value: WearIveRunNarration.expandedLine(
                for: shownEntry, formattedFeelsLike: formattedFeelsLike))
        .sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)
        .accessibilityLabel("\(WearIveRunMiniBarModel.wordmark), expanded")
        .task(id: shownEntry.id) {
            // RW-152 §3: re-sign the shown entry's storage path
            // through the injected resolver. Stable references
            // never reach here (they render synchronously).
            if HeroRefResolver.stableDisplayReference(for: shownEntry) == nil {
                resolvedHeroURL = await heroURLResolver?(shownEntry)
            }
        }
        .onAppear {
            if displayedState == nil {
                displayedState = state
                displayedEntry = entry
                _ = narrationDeferral.offer(
                    line: WearIveRunNarration.line(for: state),
                    gestureActive: false)
            }
        }
        .onChange(of: state) { _, newState in
            let released = narrationDeferral.offer(
                line: WearIveRunNarration.line(for: newState),
                gestureActive: isScrollGestureActive)
            if released != nil {
                applyFieldCrossfade(to: newState)
            } else if isScrollGestureActive {
                pendingNarrationState = newState
            }
        }
        .onChange(of: entry) { _, newEntry in
            // §4.5: a record change whose LINE is unchanged
            // (hero/kit landing) still flips the entry row's
            // fields in the same transaction.
            if displayedState == state, displayedEntry != newEntry {
                applyFieldCrossfade(to: state)
            }
        }
        .onChange(of: isScrollGestureActive) { _, active in
            if !active, narrationDeferral.gestureEnded() != nil,
               let pending = pendingNarrationState {
                pendingNarrationState = nil
                DispatchQueue.main.async {
                    applyFieldCrossfade(to: pending)
                }
            }
        }
    }

    /// Header row (the §2.2 anatomy): Great Vibes 31 pt +
    /// chevron-down (the dismissal) and delete controls, ink,
    /// right-aligned. A vertical glide on the header dismisses
    /// too (RW-152 §1: chevron/glide dismissal).
    private var headerRow: some View {
        HStack(spacing: 8) {
            Text(WearIveRunMiniBarModel.wordmark)
                .font(.greatVibes(size: 31))
                .foregroundColor(AppTheme.inkPrimary)
            Spacer(minLength: 0)
            Button {
                onCollapse()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkPrimary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Collapse")
            Button {
                // §4.7: light trash haptic at tap; the host
                // orchestrates the exit over a frozen snapshot.
                trashHapticNonce += 1
                onTrash()
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkPrimary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove today's entry")
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 12)
                .onEnded { value in
                    if abs(value.translation.height) > 32 { onCollapse() }
                }
        )
    }

    /// The entry row (§2.2/§7.3 doorway): the recorded
    /// thumbnail (56 pt, radius 14) + the full entry line at
    /// 14.5 pt. Tapping it opens the collection sheet — EXCEPT
    /// a PAST entry, whose tap opens the Share Studio sheet
    /// directly (RW-158, user ruling Nov 10, 2026; the pure
    /// decision is WearIveRunTapPolicy's). The thumbnail loads
    /// through the RW-152 §3 resolver and cross-fades when the
    /// record's hero image changes (§4.5).
    /// RW-158: the entry row's tap route — the policy's pure
    /// decision for the SHOWN entry: past → the share sheet
    /// (via the host's presenter); today/future → the
    /// collection, unchanged. With no presenter injected the
    /// row keeps the collection route.
    private func entryRowTapped() {
        if let onOpenShareEntry,
           WearIveRunTapPolicy.expandedPanelEntryDestination(for: shownEntry) == .shareSheet {
            onOpenShareEntry(shownEntry)
        } else {
            onOpenCollection()
        }
    }

    private var entryRow: some View {
        Button(action: entryRowTapped) {
            HStack(spacing: 12) {
                // RW-169 §7E.3 (thumbnail binds at promotion):
                // a surface that has shown a thumbnail never
                // returns to an empty slot for the same entry —
                // the current decoded image stays while a
                // successor reference resolves. (The pre-fix
                // AsyncImage was keyed by the reference string,
                // so a re-settle's reference change reset it to
                // the empty phase — D-12's wash-out.)
                WearIveRunPromotedThumbnail(urlString: thumbURLString)
                // No line limit, by design (WS-4): the entry
                // line wraps whole rather than truncating.
                // RW-169 §7B/D5: the line is also fixed-claim
                // vertically — it takes layout priority over the
                // trailing spacer and sizes to its full wrapped
                // height, so it can never be tail-truncated
                // mid-word ("…Runni…") inside this row.
                Text(WearIveRunNarration.expandedLine(
                    for: shownEntry,
                    formattedFeelsLike: formattedFeelsLike
                ))
                .font(.system(size: 14.5))
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .layoutPriority(1)
                .id(WearIveRunNarration.expandedLine(
                    for: shownEntry, formattedFeelsLike: formattedFeelsLike))
                .transition(.opacity)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open the full Wear I've Run collection")
    }

    /// §4.3/§4.5: a released line flips the panel's fields —
    /// narration and entry line together, thumbnail keyed by
    /// hero reference — as an opacity cross-fade (≈210 ms).
    private func applyFieldCrossfade(to newState: WearIveRunState) {
        if reduceMotion {
            displayedState = newState
            displayedEntry = entry
            return
        }
        withAnimation(.easeInOut(duration: WearIveRunMorph.narrationCrossfadeSeconds)) {
            displayedState = newState
            displayedEntry = entry
        }
    }

    /// The panel's narration rich form — the same anatomy as
    /// the dock's (verb secondary, payload pine bold), rendered
    /// from the recorded state only (§7.0).
    private func panelNarrationText(_ line: String) -> Text {
        switch shownState {
        case .updated(let payload):
            // RW-150: same anatomy as the dock's — the place in
            // pine bold, its moment segment in secondary ink —
            // composed from the same recorded state (§7.0), so
            // the two states of the surface agree in every
            // change class.
            if case .placeAtMoment(let place, let moment) = payload {
                return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                    + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                    + Text(", \(moment.rendered)").foregroundColor(AppTheme.inkSecondary)
            }
            return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .planned(let place, let dayLabel, let moment):
            return Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(WearIveRunNarration.plannedSuffix(dayLabel: dayLabel, moment: moment)).foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }
}

// MARK: - RW-169 §7E — PresentedEntry: the settle discipline, implemented once
//
// Diagnosis §5/§7E (RW-169): recordTodaysEntry() was called from
// eight sites gated by a classifier that disciplined cue/narration
// version, not content — every re-settle of the live view was a
// candidate write and surfaces re-rendered piecemeal. The fix:
// a change reaches the record only after the viewed frame is
// unchanged for the spec's ≈2 s stability window; on commit the
// entry flips ATOMICALLY as one immutable presented snapshot;
// every surface (mini, expanded, collection TODAY card) renders
// only the snapshot.

/// One immutable presented snapshot of the settled entry: the
/// entry, its derived narration state, and its formatted
/// feels-like, committed together and never mutated in place.
/// A new settle produces a NEW snapshot; surfaces swap whole.
struct WearIveRunPresentedSnapshot: Equatable {
    let entry: WearIveRunEntry
    let state: WearIveRunState
    let formattedFeelsLike: String
}

/// The stability gate between a viewed frame and the record.
/// Pure value logic with injected time, so the ≈2 s window is
/// pinned without a clock: offering a DIFFERENT frame restarts
/// the window; offering the same frame again does not.
/// The frame key is (place, day, feels-like, kit, hero
/// reference, moment) — the viewed frame of spec §7E.1;
/// `lastSavedAt` and narration stamps are not frame content.
struct WearIveRunSettleGate {
    static let stabilityWindow: TimeInterval = 2.0

    private(set) var pendingEntry: WearIveRunEntry?
    private var pendingSince: Date?
    private var pendingKey: String?

    static func frameKey(for entry: WearIveRunEntry) -> String {
        [
            entry.placeName,
            String(entry.dayStart.timeIntervalSince1970),
            String(entry.feelsLikeF),
            entry.itemNames.joined(separator: "·"),
            entry.heroImageReference,
            entry.momentReading.rendered,
        ].joined(separator: "|")
    }

    /// Offer a viewed frame. A frame whose key differs from the
    /// pending frame replaces it and restarts the window; the
    /// same frame offered again keeps the original offer (and
    /// its original timestamp).
    mutating func offer(_ entry: WearIveRunEntry, at now: Date) {
        let key = Self.frameKey(for: entry)
        guard key != pendingKey else { return }
        pendingEntry = entry
        pendingKey = key
        pendingSince = now
    }

    /// The candidate that has been stable for the whole window
    /// at `now`, or nil while any part of the window remains.
    func stableCandidate(at now: Date) -> WearIveRunEntry? {
        guard let pendingEntry, let pendingSince,
              now.timeIntervalSince(pendingSince) >= Self.stabilityWindow
        else { return nil }
        return pendingEntry
    }

    /// True when a candidate is parked inside its window.
    var hasPending: Bool { pendingEntry != nil }

    mutating func clearPending() {
        pendingEntry = nil
        pendingKey = nil
        pendingSince = nil
    }
}

// MARK: - RW-169 §7A — the morph probe (mechanical acceptance)
//
// The acceptance probe of §7A, as pure functions the tests pin.
// RE-PINNED for RW-170 (Nov 12, 2026): the narration is ONE
// view again (the RW-169 single-view form), but on the RW-170
// law — its slot MIGRATES with progress (font 12 → 15 pt,
// beside-wordmark → below-header) at opacity 1 throughout, so
// the legibility invariant is exactly-one-legible at every
// progress value, by construction. (The RB-03 two-placement
// dissolve this briefly carried — mini out [0, 0.35],
// expanded in [0.55, 1], dead zone between — is retired: the
// diagnosis showed the cross-fade through the dead zone is
// what made the morph read as a swap.) The wordmark's
// rendered width remains the interpolation of its two REST
// widths — never below the mini rest width, never the ≈33 pt
// crush D1 shipped.
enum WearIveRunMorphProbe {
    /// How many narrations are legible at `progress`: the ONE
    /// narration view, at opacity 1 — exactly one at every
    /// progress value.
    static func legibleNarrationCount(progress: Double) -> Int {
        WearIveRunMorph.narrationOpacity(progress: progress) > 0.5 ? 1 : 0
    }

    /// The wordmark's rendered width at `progress`: the linear
    /// interpolation of its mini and expanded REST widths (its
    /// font size interpolates 17 → 31 pt across the same travel).
    /// At p = 0 it equals the mini rest width exactly; at p = 1
    /// the expanded rest width; between, it is never less than
    /// the mini rest width.
    static func wordmarkWidth(
        progress: Double, miniRestWidth: Double, expandedRestWidth: Double
    ) -> Double {
        let p = min(1, max(0, progress))
        return miniRestWidth + (expandedRestWidth - miniRestWidth) * p
    }

    /// The container height at `progress`: the interpolation of
    /// the two REST heights (mini measured at mini rest,
    /// expanded measured at expanded rest / derived from the
    /// rest content before the first expansion) — never a
    /// height measured during the morph itself.
    static func containerHeight(
        miniRest: Double, expandedRest: Double, progress: Double
    ) -> Double {
        WearIveRunMorph.height(
            mini: miniRest, expanded: expandedRest, progress: progress)
    }
}

// MARK: - RW-169 §7D — the straddle pill's measured seat
//
// The pill's seat is computed by the layout pass itself from the
// pill's OWN measured size (an alignment guide), not from
// HeroPillMetrics planning heights: the guide value below is
// the pill's measured height minus the anchor, so the pill's
// bottom lands on the anchor line (hero foot + straddle dip)
// at every pill height, on the first frame — no wrong first
// seat, no snap.
enum StraddleSeatGuide {
    /// The pill bottom's seat relative to the SHEET's top edge:
    /// the sheet's top sits `sheetOverlap` above the hero foot,
    /// and the pill bottom sits `straddleDip` below the foot.
    static let anchorFromSheetTop: CGFloat =
        HomeHandoffGeometry.sheetOverlap + HeroVerticalLayout.straddleDip

    /// The `.top` alignment-guide value for a pill of the given
    /// MEASURED height: guide = height − anchor, so the layout
    /// places the pill's top at (sheet top + anchor − height)
    /// and its bottom exactly on the anchor.
    static func topGuideValue(measuredPillHeight: CGFloat) -> CGFloat {
        measuredPillHeight - anchorFromSheetTop
    }
}

// MARK: - RW-169 §7E.3 — the promoted thumbnail
//
// Binds at promotion: once an image has decoded for the entry's
// thumbnail slot it stays on screen while a successor reference
// resolves; the slot returns to empty only when there is no
// successor reference at all. (The pre-fix AsyncImage was keyed
// by the reference string, so any reference change reset the
// slot to the empty phase until the network re-signed and
// re-decoded — the §5 wash-out.)
struct WearIveRunPromotedThumbnail: View {
    let urlString: String?
    @State private var displayedImage: Image?
    @State private var displayedURLString: String?

    var body: some View {
        ZStack {
            if let displayedImage {
                HeroPhoto(image: displayedImage, width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                Color.clear.frame(width: 56, height: 56)
            }
        }
        .frame(width: 56, height: 56)
        .task(id: urlString) {
            guard let urlString, !urlString.isEmpty,
                  urlString != displayedURLString,
                  let url = URL(string: urlString) else { return }
            let data: Data?
            if url.isFileURL {
                data = try? Data(contentsOf: url)
            } else {
                data = try? await URLSession.shared.data(from: url).0
            }
            guard let data, let decoded = UIImage(data: data) else { return }
            displayedImage = Image(uiImage: decoded)
            displayedURLString = urlString
        }
    }
}

// MARK: - RW-169 §7A — ONE element, two geometries (the unified surface)
//
// RETIRES the dual-form ZStack (D3): the overlay no longer
// composes a complete mini form and a complete expanded form
// simultaneously and cross-fades between them over a height
// measured during the morph. There is ONE content hierarchy —
// the same wordmark view, the same entry row and the same
// doorway throughout the travel — whose positions, sizes and
// opacities are pure functions of the one expansionProgress.
// The container height is the interpolation of the two REST
// heights: the mini rest height is measured at mini rest, the
// expanded rest height is measured at expanded rest (before
// the first expansion it is derived from the rest content by
// WearIveRunOverlayPolicy.contentHeight — a rest computation,
// never a during-morph measurement).
//
// RW-170 (Nov 12, 2026) settles the narration's part in that
// hierarchy: ONE narration view whose slot migrates with
// progress — font 12 → 15 pt, beside the wordmark → below
// the header, width flexing between the slots — at opacity 1
// throughout. (The RB-03 two-placement dissolve that stood
// here briefly — two copies cross-fading through a dead zone
// — is retired on the diagnosis's evidence: it read as a
// swap, not a transform. With one view there is never a
// second copy to ghost, so the legibility invariant holds
// by construction.)

struct WearIveRunUnifiedSurfaceView: View {
    let state: WearIveRunState
    let entry: WearIveRunEntry
    let formattedFeelsLike: String
    let cueCount: Int
    var narrationVersion: Int = 0
    var exitProgress: Double = 0
    /// Audit sweep (§4.7 exit split): the form the host's exit
    /// starts from — the exit policy's fade/sink/fold split
    /// keys on it. The overlay container sets it from its
    /// expansion state at the trash tap.
    var exitFromExpanded: Bool = false
    var isDimmedBySheet: Bool = false
    var isScrollGestureActive: Bool = false
    let viewportHeight: CGFloat
    /// RW-173: the pill-title cap in force, when the host's
    /// straddle pill is in the panel's expansion zone (the
    /// overlay container computes it through
    /// WearIveRunOverlayPolicy.effectiveCap). The entry
    /// region's budget reads the same cap as the container's
    /// height, so a binding pill cap scrolls the entry region
    /// internally — header, narration and doorway pinned,
    /// exactly as under the §2 top-chrome cap.
    var expandedCapOverride: CGFloat? = nil
    var heroURLResolver: ((WearIveRunEntry) async -> String?)?
    /// The morph's one progress value (0 = mini, 1 = expanded),
    /// owned by the overlay container.
    let expansionProgress: Double
    /// Rest-height reporting to the container: called with the
    /// surface's rest anatomy heights when a rest-state geometry
    /// read lands (and on appear) — never during the travel.
    var onRestHeights: ((CGFloat, CGFloat) -> Void)? = nil
    let onToggle: () -> Void
    /// RB-01/R-1: the explicit collapse (glide-up) — distinct
    /// from the toggle so the container's R-1 policy records
    /// a dismissal, not a state flip.
    var onCollapse: () -> Void = {}
    let onOpenCollection: () -> Void
    var onOpenShareEntry: ((WearIveRunEntry) -> Void)?
    let onTrash: () -> Void

    @State private var displayedState: WearIveRunState?
    @State private var pendingNarrationState: WearIveRunState?
    @State private var narrationDeferral = WearIveRunNarrationDeferral()
    @State private var displayedEntry: WearIveRunEntry?
    @State private var resolvedHeroURL: String?
    @State private var wordmarkWidth: CGFloat = 96
    @State private var miniNarrationHeight: CGFloat = 17
    @State private var expandedNarrationHeight: CGFloat = 20
    @State private var expandedEntryHeight: CGFloat = 56
    @State private var cueHapticNonce = 0
    @State private var trashHapticNonce = 0
    @State private var glideHapticNonce = 0
    @State private var pulseScale: CGFloat = 1
    @State private var isPulsing = false
    @State private var ringScale: CGFloat = 1
    @State private var ringOpacity: Double = 0
    @State private var bounceScale: CGFloat = 1
    @State private var pressScale: CGFloat = 1
    @State private var entranceProgress: Double = 1
    @State private var didRunEntrance = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var p: Double { min(1, max(0, expansionProgress)) }
    private func lerp(_ a: CGFloat, _ b: CGFloat) -> CGFloat {
        a + (b - a) * CGFloat(p)
    }
    private var shownState: WearIveRunState { displayedState ?? state }
    private var shownEntry: WearIveRunEntry { displayedEntry ?? entry }

    // MARK: RB-04 (RW-135 rebuild) — the measured footprint
    //
    // MINI is content-sized at the prototype's own footprint:
    // padding 11 top + ONE 26 pt content row + padding 12
    // bottom = 49 pt with a one-line narration. The 26 pt row
    // is the tallest element (wordmark 17 pt, narration 12 pt,
    // two 26 pt glyph controls); hit areas may extend by
    // padding but never add visible height. A wrapped
    // narration grows the row (and the bar) by its own height
    // — growth is content, not room.
    static let miniTopPadding: CGFloat = 11
    static let miniBottomPadding: CGFloat = 12
    static let miniRowHeight: CGFloat = 26
    /// EXPANDED paddings (RB-04): top 20 / sides 16 / bottom
    /// 12 — the A′ in-flow seat's 32 pt bottom padding (and
    /// the interim 20) do not carry into the overlay. The top
    /// padding's value is the overlay policy's (RW-173: the
    /// pill-title cap's anatomy reads the same number).
    static let expandedTopPadding: CGFloat =
        WearIveRunOverlayPolicy.expandedTopPadding
    static let expandedBottomPadding: CGFloat = 12
    /// The expanded header zone: header row 44 + the 12 pt
    /// spacing above the expanded narration slot, measured
    /// from the expanded top padding.
    static let expandedHeaderZone: CGFloat = 56

    var miniRestHeight: CGFloat {
        Self.miniTopPadding
            + max(Self.miniRowHeight, miniNarrationHeight)
            + Self.miniBottomPadding
    }
    /// Expanded rest height from the measured rest anatomy:
    /// top 20 + header zone 56 (row 44 + spacing 12) +
    /// narration + 12 + entry + 12 + doorway 24 + bottom 12.
    /// Rendered height == this content height at rest.
    var expandedRestHeight: CGFloat {
        Self.expandedTopPadding + Self.expandedHeaderZone
            + expandedNarrationHeight + 12
            + expandedEntryHeight + 12 + 24
            + Self.expandedBottomPadding
    }

    /// RW-152 §2 height cap, inside the one surface (RW-169
    /// loop-back, Nov 12, 2026): when the expanded rest height
    /// exceeds the viewport cap, the ENTRY REGION is the one
    /// flexible element — it stands at this budget and scrolls
    /// internally, while the header, narration and doorway stay
    /// pinned and fully visible. The budget is the cap minus
    /// everything that is not the entry region (top pad 20 +
    /// header zone 56 + narration + spacing 12 + spacing 12 +
    /// doorway 24 + bottom pad 12 = 136 + narration, RB-04),
    /// floored at the 56 pt thumbnail cell — the same floor
    /// the overlay policy uses. (The unified surface
    /// originally carried NO cap behaviour at all: at a binding
    /// cap the entry text simply overflowed the panel's bottom
    /// edge and the doorway was pushed out of the surface —
    /// the loop-back's binding-cap capture is the evidence.)
    var entryRegionBudget: CGFloat {
        let cap = min(
            WearIveRunOverlayPolicy.expandedCap(
                viewportHeight: viewportHeight),
            expandedCapOverride ?? .greatestFiniteMagnitude)
        return max(56, cap - (136 + expandedNarrationHeight))
    }

    /// The entry region's standing height at expanded rest:
    /// its full measured height, or the budget when the cap
    /// binds.
    var cappedEntryHeight: CGFloat {
        min(expandedEntryHeight, entryRegionBudget)
    }

    /// True exactly when the cap binds at expanded rest and
    /// the entry region must scroll internally.
    var capBinds: Bool {
        expandedEntryHeight > entryRegionBudget + 0.5
    }

    private var thumbURLString: String? {
        HeroRefResolver.stableDisplayReference(for: shownEntry) ?? resolvedHeroURL
    }

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let topPad = lerp(Self.miniTopPadding, Self.expandedTopPadding)
            // RW-170: the ONE narration's slot geometry — the
            // mini slot (beside the wordmark) and the expanded
            // slot (below the header, full width) are the two
            // ENDS of one migration: x, y and width all lerp on
            // the same progress, and the font lerps 12 → 15 pt
            // inside the view. There is no second placement.
            let miniNarrationX = 16 + wordmarkWidth + 8
            let miniNarrationY = Self.miniTopPadding
                + max(0, (Self.miniRowHeight - miniNarrationHeight) / 2)
            let miniNarrationWidth = max(
                40, width - miniNarrationX - 16 - 56 - 4)
            let expandedNarrationY = Self.expandedTopPadding + Self.expandedHeaderZone
            let narrationX = lerp(miniNarrationX, 16)
            let narrationY = lerp(miniNarrationY, expandedNarrationY)
            let narrationWidth = lerp(miniNarrationWidth, width - 32)
            // Expanded-only content stands at its EXPANDED
            // geometry from the first frame (RB-03): the
            // entry region below the expanded narration slot,
            // the doorway below the entry region — revealed
            // by the growing, clipped surface and its own
            // dissolve window, never slid up from the mini
            // form's space.
            let entryY = expandedNarrationY + expandedNarrationHeight + 12
            // RW-152 §2 (RW-169 loop-back): the doorway seats
            // below the entry REGION's standing height — the
            // full entry height interpolated toward the capped
            // region height across the same travel, so at
            // expanded rest under a binding cap the doorway is
            // pinned inside the surface, never pushed past
            // its bottom edge by the overflowing entry.
            let doorwayY = entryY + lerp(expandedEntryHeight, cappedEntryHeight) + 12
            ZStack(alignment: .topLeading) {
                // RB-02: the body tap layer — ONE tap target
                // for the whole surface body, seated BELOW
                // every control. Taps on the toggle, trash,
                // entry row, or doorway land on those controls
                // (they sit above this layer); taps anywhere
                // else land here. There is no parent
                // `onTapGesture` competing with the buttons —
                // the pre-rebuild hit path stacked a root
                // onTapGesture over the toggle Button, so a tap
                // on the icon could fire the toggle twice (or
                // be swallowed between the two recognizers)
                // and read as a dead control.
                Button {
                    handleToggleTap()
                } label: {
                    Color.clear
                        .frame(width: width, height: geo.size.height)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHidden(true)
                // The wordmark — ONE instance, anchored
                // top-left throughout; its size lerps
                // 17 pt → 31 pt (fixed-claim at every sample).
                wordmark
                    .offset(x: 16, y: topPad + lerp(3, 0))
                    .allowsHitTesting(false)
                // The controls — one toggle + trash, top-right
                // throughout the travel, each in its 26 pt box
                // (RB-02/RB-04), centred on the mini row and on
                // the expanded header row respectively.
                controls
                    .offset(x: width - 16 - 56, y: topPad + lerp(0, 9))
                // The narration — ONE view (RW-170), migrating
                // between its two slots on the one progress:
                // exactly one narration in the view and
                // semantics tree at every progress value, at
                // opacity 1 throughout, so the legibility
                // invariant holds by construction.
                if let line = WearIveRunNarration.line(for: shownState) {
                    narrationView(line)
                        .frame(width: narrationWidth, alignment: .leading)
                        .offset(x: narrationX, y: narrationY)
                        .allowsHitTesting(false)
                }
                // The entry region — the same row throughout,
                // at its expanded geometry; it dissolves in
                // over p in [0.45, 1]. At expanded rest under
                // a binding cap the region stands at its
                // budget inside a ScrollView — the row scrolls
                // internally (RW-152 §2).
                entryRegion
                    .frame(width: width - 32, alignment: .leading)
                    .offset(x: 16, y: entryY)
                    .opacity(WearIveRunMorph.expandedOnlyOpacity(progress: p))
                    .allowsHitTesting(p > 0.5 && exitProgress == 0)
                // The doorway — always present in the expanded
                // form (§2.4), dissolving in with the same
                // window at its expanded geometry.
                doorway
                    .offset(x: 16, y: doorwayY)
                    .opacity(WearIveRunMorph.expandedOnlyOpacity(progress: p))
                    .allowsHitTesting(p > 0.5 && exitProgress == 0)
            }
            .frame(width: width, alignment: .topLeading)
        }
        .background {
            // RW-170 — ONE SURFACE: one token throughout, the
            // ruled unified token (light #F5F1E8 / dark
            // #2A251E — the expanded state's warm tone, per
            // Sol's ruling), over the system blur. The ONLY
            // background animation is the tint layer's ALPHA
            // (mini rest .85 light / .82 dark → 1.00 expanded);
            // the token never cross-fades and the material's
            // blur never animates.
            ZStack {
                Rectangle().fill(.regularMaterial)
                Rectangle().fill(
                    Color(hex: WearIveRunMorph.unifiedSurfaceHex(
                        isDark: colorScheme == .dark)
                    ).opacity(WearIveRunMorph.tintOpacity(
                        progress: p,
                        isDark: colorScheme == .dark
                    ))
                )
            }
        }
        .overlay {
            if isPulsing {
                RoundedRectangle(cornerRadius: WearIveRunMorph.miniCornerRadius)
                    .stroke(AppTheme.pinePayload, lineWidth: 2)
                    .scaleEffect(ringScale)
                    .opacity(ringOpacity)
            }
        }
        // RB-05: the corner radius is a CONSTANT 16 pt at
        // every progress value — cornerRadius(progress:) is
        // 16 at both tokens by design, and neither the shape
        // nor the hairline nor the travel clip interpolates it.
        .clipShape(RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: p)))
        .overlay {
            RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: p))
                .strokeBorder(
                    colorScheme == .dark
                        ? Color.white.opacity(0.14)
                        : Color.black.opacity(0.08),
                    lineWidth: 0.5)
        }
        // Spec §2.1 shadow, VERIFIED at the RW-170 audit: the
        // settled iOS expression — black 14%, radius 12 (half
        // the spec's 24 pt blur in SwiftUI terms), offset
        // (0, +6), constant across progress. No elevation
        // model, nothing animated. (RW-170 Move 3 is
        // verify-and-confirm on iOS; the travel-clip finding
        // is recorded in the lane report.)
        .shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 6)
        // §4.7 exit split (WearIveRunExitPolicy): opacity and
        // sink are the policy's functions of the host's one
        // exit progress and the form the exit started from.
        .opacity(WearIveRunElementDimming.opacity(sheetPresented: isDimmedBySheet)
            * WearIveRunExitPolicy.opacity(
                progress: exitProgress, fromExpanded: exitFromExpanded,
                reduceMotion: reduceMotion)
            * entranceProgress)
        .offset(y: (reduceMotion ? 0 : CGFloat(1 - entranceProgress) * miniRestHeight * 1.6)
            + CGFloat(WearIveRunExitPolicy.sink(
                progress: exitProgress, fromExpanded: exitFromExpanded,
                reduceMotion: reduceMotion)))
        .scaleEffect(pulseScale * bounceScale * pressScale)
        // R-1 dismissal set (RW-152 §1.4, iOS): a glide UP on
        // the expanded surface collapses it — a simultaneous
        // gesture, so it never competes with the controls'
        // taps (RB-02's one-action discipline).
        .simultaneousGesture(
            DragGesture(minimumDistance: 12)
                .onEnded { value in
                    if value.translation.height < -32, p > 0.5 {
                        onCollapse()
                    }
                }
        )
        .sensoryFeedback(.impact(flexibility: .soft), trigger: cueHapticNonce)
        .sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)
        .sensoryFeedback(.impact(weight: .light), trigger: glideHapticNonce)
        .accessibilityLabel("\(WearIveRunMiniBarModel.wordmark)\(p > 0.5 ? ", expanded" : "")")
        .task(id: shownEntry.id) {
            if HeroRefResolver.stableDisplayReference(for: shownEntry) == nil {
                resolvedHeroURL = await heroURLResolver?(shownEntry)
            }
        }
        .onAppear {
            if displayedState == nil {
                displayedState = state
                displayedEntry = entry
                _ = narrationDeferral.offer(
                    line: WearIveRunNarration.line(for: state),
                    gestureActive: false)
            }
            onRestHeights?(miniRestHeight, expandedRestHeight)
            runEntranceIfNeeded()
        }
        .onChange(of: cueCount) { oldValue, newValue in
            guard newValue > oldValue, didRunEntrance else { return }
            didRunEntrance = false
            runEntranceIfNeeded()
        }
        .onChange(of: state) { _, newState in
            let released = narrationDeferral.offer(
                line: WearIveRunNarration.line(for: newState),
                gestureActive: isScrollGestureActive)
            if released != nil {
                applyNarrationCrossfade(to: newState)
            } else if isScrollGestureActive {
                pendingNarrationState = newState
            }
        }
        .onChange(of: entry) { _, newEntry in
            if displayedState == state, displayedEntry != newEntry {
                applyNarrationCrossfade(to: state)
            }
        }
        .onChange(of: isScrollGestureActive) { _, active in
            if !active, narrationDeferral.gestureEnded() != nil,
               let pending = pendingNarrationState {
                pendingNarrationState = nil
                DispatchQueue.main.async {
                    applyNarrationCrossfade(to: pending)
                }
            }
        }
    }

    // MARK: The one hierarchy's elements

    private var wordmark: some View {
        Text(WearIveRunMiniBarModel.wordmark)
            .font(.greatVibes(size: lerp(17, 31)))
            .foregroundColor(AppTheme.inkPrimary)
            // §7B width law: fixed-claim at every progress
            // value — the wordmark's width is its interpolated
            // rest width, never a negotiated remainder.
            .fixedSize(horizontal: true, vertical: false)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.width
            } action: { _, width in
                if width > 0 { wordmarkWidth = width }
            }
    }

    /// RB-02: ONE toggle control — a single Button whose
    /// glyph cross-fades (corners-out ↔ chevron-down) inside
    /// one 26 pt box; its action is the same `handleToggleTap`
    /// the body tap layer fires. The trash is its own control.
    /// Both boxes are 26 pt (RB-04): the visible row height is
    /// the content row, never the controls' old 44 pt frames.
    private var controls: some View {
        HStack(spacing: 4) {
            Button {
                handleToggleTap()
            } label: {
                ZStack {
                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                        .opacity(1 - p)
                    Image(systemName: "chevron.down")
                        .opacity(p)
                }
                .font(.system(size: 14))
                .foregroundColor(AppTheme.inkMuted)
                .frame(width: 26, height: 26)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(p > 0.5 ? "Collapse" : "Expand")
            Button {
                trashHapticNonce += 1
                onTrash()
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove today's entry")
        }
    }

    /// RW-170: THE narration — one view, font lerping
    /// 12 → 15 pt as its slot migrates (the slot geometry is
    /// computed in the body). Its measured height feeds the
    /// rest height of whichever rest state the surface stands
    /// in — mini at p == 0, expanded at p == 1 (the RW-169
    /// §7A discipline, kept: rest captures are confined to
    /// their rest states, so a mid-travel height never feeds
    /// an endpoint).
    @ViewBuilder
    private func narrationView(_ line: String) -> some View {
        narrationText(line, for: shownState)
            .font(.system(size: CGFloat(WearIveRunMorph.narrationFontSize(progress: p))))
            .id(line)
            .transition(.opacity)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { _, height in
                if p == 0 { miniNarrationHeight = height }
                if p == 1 { expandedNarrationHeight = height }
                if height > 0 {
                    onRestHeights?(miniRestHeight, expandedRestHeight)
                }
            }
    }

    /// The entry region (RW-152 §2, RW-169 loop-back): the
    /// entry row at its natural height — or, only at expanded
    /// rest when the height cap binds, the same row inside a
    /// vertical ScrollView standing at exactly the region
    /// budget, so the header, narration and doorway stay
    /// pinned while the entry scrolls internally. The row's
    /// own geometry read (inside `entryRow`) still measures
    /// its FULL content height inside the ScrollView, so the
    /// budget comparison never feeds on the clipped height.
    @ViewBuilder private var entryRegion: some View {
        if p >= 1, capBinds {
            ScrollView(.vertical, showsIndicators: false) {
                entryRow
            }
            .frame(height: entryRegionBudget)
        } else {
            entryRow
        }
    }

    private var entryRow: some View {
        Button(action: entryRowTapped) {
            HStack(spacing: 12) {
                WearIveRunPromotedThumbnail(urlString: thumbURLString)
                // §2.4 anatomy + RW-169 D5: the entry line
                // wraps whole over as many lines as it needs —
                // no line limit, no tail truncation, fixed
                // vertical claim.
                Text(WearIveRunNarration.expandedLine(
                    for: shownEntry,
                    formattedFeelsLike: formattedFeelsLike
                ))
                .font(.system(size: 14.5))
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .layoutPriority(1)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open the full Wear I've Run collection")
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { _, height in
            // RB-03: the row stands at its expanded geometry
            // for the whole travel, so its height — a property
            // of the content at the panel's width, never of
            // the progress value — is measured whenever the
            // read lands and feeds the expanded rest height.
            if height > 0 {
                expandedEntryHeight = height
                onRestHeights?(miniRestHeight, expandedRestHeight)
            }
        }
    }

    private var doorway: some View {
        Button(action: onOpenCollection) {
            Text("Open the collection ›")
                .font(.system(size: 15.5, weight: .semibold))
                .foregroundColor(AppTheme.pinePayload)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open the full Wear I've Run collection")
    }

    private func entryRowTapped() {
        if let onOpenShareEntry,
           WearIveRunTapPolicy.expandedPanelEntryDestination(for: shownEntry) == .shareSheet {
            onOpenShareEntry(shownEntry)
        } else {
            onOpenCollection()
        }
    }

    /// The narration's rich form — verb secondary, payload pine
    /// semibold — composed from the SHOWN recorded state only
    /// (§7.0), identical in both geometries because it is the
    /// same view.
    private func narrationText(_ line: String, for shownState: WearIveRunState) -> Text {
        switch shownState {
        case .updated(let payload):
            if case .placeAtMoment(let place, let moment) = payload {
                return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                    + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                    + Text(", \(moment.rendered)").foregroundColor(AppTheme.inkSecondary)
            }
            return Text("Updated to ").foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .planned(let place, let dayLabel, let moment):
            return Text("Planned for ").foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(WearIveRunNarration.plannedSuffix(dayLabel: dayLabel, moment: moment)).foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }

    // MARK: Toggle, cross-fade, entrance, cue (the mini bar's discipline, carried)

    /// The toggle fires SYNCHRONOUSLY in the tap's own
    /// transaction (RW-162 G1's rule, carried): the press scale
    /// is decoration that follows it, never its carrier.
    private func handleToggleTap() {
        guard exitProgress == 0 else { return }
        glideHapticNonce += 1
        onToggle()
        if reduceMotion { return }
        withAnimation(.easeInOut(duration: 0.12)) {
            pressScale = 0.97
        } completion: {
            withAnimation(.easeOut(duration: 0.15)) { pressScale = 1 }
        }
    }

    /// §4.3/§7E.4: a released line flips the surface's fields —
    /// narration and entry together, from the one snapshot —
    /// as an opacity-only cross-fade (≈210 ms) + the update
    /// bounce, deferred to scroll rest by the gate above.
    private func applyNarrationCrossfade(to newState: WearIveRunState) {
        guard displayedState != newState || displayedEntry != entry else { return }
        if exitProgress == 0 { runUpdateBounce() }
        if reduceMotion {
            displayedState = newState
            displayedEntry = entry
            return
        }
        withAnimation(.easeInOut(duration: WearIveRunMorph.narrationCrossfadeSeconds)) {
            displayedState = newState
            displayedEntry = entry
        }
    }

    private func runEntranceIfNeeded() {
        guard !didRunEntrance else { return }
        didRunEntrance = true
        guard cueCount > 0 else {
            entranceProgress = 1
            return
        }
        if reduceMotion {
            entranceProgress = 0
            withAnimation(.easeInOut(duration: 0.22)) {
                entranceProgress = 1
            } completion: {
                cueHapticNonce += 1
            }
            return
        }
        entranceProgress = 0
        withAnimation(.spring(response: 0.5, dampingFraction: 0.82)) {
            entranceProgress = 1
        } completion: {
            fireCueSettle()
        }
    }

    /// The cue's settle moment: fire the ONE pulse and the ONE
    /// soft haptic (§4.1), together, exactly here.
    private func fireCueSettle() {
        cueHapticNonce += 1
        runCuePulse()
    }

    private func runCuePulse() {
        guard !reduceMotion else { return }
        isPulsing = true
        ringScale = 1
        ringOpacity = 0.9
        withAnimation(.easeOut(duration: WearIveRunElementMachine.cuePulseDurationSeconds)) {
            ringScale = 1.08
            ringOpacity = 0
        }
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

    private func runUpdateBounce() {
        if reduceMotion {
            cueHapticNonce += 1
            return
        }
        withAnimation(.easeOut(duration: 0.16)) {
            bounceScale = CGFloat(WearIveRunMorph.updateBounceScale)
        } completion: {
            withAnimation(.easeInOut(duration: 0.22)) {
                bounceScale = 1
            } completion: {
                cueHapticNonce += 1
            }
        }
    }
}

// MARK: - RW-152 §1 / RW-169 §7A — the one overlay surface (two states, one morph)
//
// The container the host mounts. It owns the ONE
// `expansionProgress` (0 = MINI, 1 = EXPANDED) and drives the
// ONE unified surface (WearIveRunUnifiedSurfaceView) with it:
// height interpolates between the two REST heights through
// WearIveRunMorph.height — the mini rest height measured at
// mini rest, the expanded rest height measured at expanded
// rest (before the first expansion, derived from the rest
// content by WearIveRunOverlayPolicy.contentHeight). No height
// is ever measured during the morph (RW-169 §7A retires RW-162
// G1's during-morph measurement pass and the dual-form ZStack
// it fed). Expansion follows the R-1 decision
// (WearIveRunExpansionPolicy: tap-summoned, or presented on
// reaching the page end); dismissal restores
// the page exactly because the page never moved. The host's
// exit progress folds this container's height while the
// surface fades — the §4.7 content-fade + height-fold.

struct WearIveRunOverlayView: View {
    let state: WearIveRunState
    /// The session's recorded entry.
    let entry: WearIveRunEntry
    /// The entry's feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    /// Cue-worthy landing counter (creation only), forwarded
    /// to the surface's entrance logic.
    let cueCount: Int
    /// The collection model's narration version (bookkeeping,
    /// forwarded to the surface).
    var narrationVersion: Int = 0
    /// Host-orchestrated exit progress (0 = at rest, 1 = fully
    /// exited), driven by the host's §4.7/§4.8 choreography.
    var exitProgress: Double = 0
    /// True while any sheet is presented over home — forwarded
    /// to the surface's backdrop dim.
    var isDimmedBySheet: Bool = false
    /// True while the host page's scroll gesture is live —
    /// forwarded to the surface's narration deferral gate.
    var isScrollGestureActive: Bool = false
    /// RB-01/R-1: the host scroll's footer-zone verdict, from
    /// WeatherOutfitView's real scroll geometry
    /// (WearIveRunFooterZone). Reaching the page end presents
    /// the expanded form; leaving the zone re-docks it —
    /// unless a tap summoned it (the R-1 policy below).
    var isAtFooter: Bool = false
    /// Audit sweep (§4.7 exit split): the form the host's exit
    /// starts from, forwarded to the surface and used by the
    /// container's height fold.
    var exitFromExpanded: Bool = false
    /// The host viewport's height — the expanded state's §2
    /// cap input.
    let viewportHeight: CGFloat
    /// RW-173: the Make It Me straddle pill's bottom edge in
    /// screen coordinates at the host's current scroll
    /// position, when the pill renders in the stacked seat
    /// (the host computes it from HeroHeightPolicy +
    /// HeroVerticalLayout.straddleDip − scroll offset). Nil
    /// when no pill renders or the layout is not the stacked
    /// seat. While it is near the panel's expansion zone the
    /// expanded height is capped so the title clears it
    /// (WearIveRunOverlayPolicy.effectiveCap).
    var pillBottomY: CGFloat? = nil
    /// RW-152 §3: the resolver's signing half, forwarded to the
    /// surface (the collection surfaces resolve through
    /// HeroRefRuntime directly).
    var heroURLResolver: ((WearIveRunEntry) async -> String?)?
    let onOpenCollection: () -> Void
    /// RW-158: forwarded to the surface's entry row (a past
    /// entry's tap opens the Share Studio sheet).
    var onOpenShareEntry: ((WearIveRunEntry) -> Void)?
    let onTrash: () -> Void
    /// The host learns the surface's expanded state (the
    /// §4.7 exit split keys its choreography on the form the
    /// exit starts from).
    var onExpansionChange: ((Bool) -> Void)? = nil
    /// RW-170 — ONE BAND: the host learns the surface's
    /// PRESENTED (mini) rest height, so its scroll clearance
    /// prices the presented form (DockResidence.clearance) —
    /// a wrapped narration grows the presented height and the
    /// reserve follows; the expanded height is never reported
    /// for clearance.
    var onPresentedMiniHeight: ((CGFloat) -> Void)? = nil

    /// RB-01/R-1: the expansion decision — the ONLY source of
    /// the expanded state. Tap summons persist until an
    /// explicit collapse; the footer presents on arrival and
    /// re-docks on departure; a collapse at the footer
    /// suppresses re-presentation until the scroll leaves.
    @State private var expansionPolicy = WearIveRunExpansionPolicy()
    @State private var expansionProgress: Double = 0
    /// The surface's measured mini rest height (captured from
    /// the unified surface's own rest anatomy at progress 0).
    /// Seeded with the settled single-line dock height.
    @State private var miniRestHeight: Double = Double(DockResidence.measuredDockHeight)
    /// The surface's measured expanded rest height, captured
    /// at expanded rest after the first expansion; 0 until
    /// then (the pre-expansion endpoint is the policy's
    /// rest-content derivation below).
    @State private var expandedRestHeight: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The expanded endpoint before its first rest measurement:
    /// the §2 policy's content height for this entry's rest
    /// content (narration and entry lines counted from the
    /// recorded strings at the panel's width class).
    private var expandedRestEstimate: Double {
        let narration = WearIveRunNarration.line(for: state) ?? ""
        let entryLine = WearIveRunNarration.expandedLine(
            for: entry, formattedFeelsLike: formattedFeelsLike)
        let narrationLines = max(1, Int(ceil(Double(narration.count) / 44.0)))
        let entryLines = max(1, Int(ceil(Double(entryLine.count) / 38.0)))
        return Double(WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: narrationLines,
            entryLineCount: entryLines,
            textScale: 1))
    }

    /// The container's height: the morph interpolation of the
    /// two rest heights, capped per §2, folded by the exit —
    /// the fold being the §4.7 exit policy's height scale
    /// (from MINI there is no fold; from EXPANDED the fold
    /// runs only after the content fade's segment).
    /// RW-173: the cap in force for this seat — §2's
    /// top-chrome cap, bound further by the pill-title cap
    /// while the straddle pill is in the expansion zone.
    private var effectiveCap: CGFloat {
        WearIveRunOverlayPolicy.effectiveCap(
            viewportHeight: viewportHeight, pillBottomY: pillBottomY)
    }

    private var containerHeight: Double {
        let expanded = min(
            expandedRestHeight > 0 ? expandedRestHeight : expandedRestEstimate,
            Double(effectiveCap))
        let base = WearIveRunMorphProbe.containerHeight(
            miniRest: miniRestHeight, expandedRest: expanded, progress: expansionProgress)
        return base * WearIveRunExitPolicy.heightScale(
            progress: exitProgress, fromExpanded: exitFromExpanded,
            reduceMotion: reduceMotion)
    }

    /// RW-162 G5, carried: the travel clip applies ONLY while
    /// the morph is actually travelling (0 < progress < 1) or
    /// an exit is folding — at either rest state the surface's
    /// own rounded shape and shadow stand unclipped.
    private var travelClipActive: Bool {
        (expansionProgress > 0 && expansionProgress < 1) || exitProgress > 0
    }

    var body: some View {
        WearIveRunUnifiedSurfaceView(
            state: state,
            entry: entry,
            formattedFeelsLike: formattedFeelsLike,
            cueCount: cueCount,
            narrationVersion: narrationVersion,
            exitProgress: exitProgress,
            exitFromExpanded: exitFromExpanded,
            isDimmedBySheet: isDimmedBySheet,
            isScrollGestureActive: isScrollGestureActive,
            viewportHeight: viewportHeight,
            expandedCapOverride: pillBottomY == nil ? nil : effectiveCap,
            heroURLResolver: heroURLResolver,
            expansionProgress: expansionProgress,
            onRestHeights: { mini, expanded in
                // Rest captures only (the surface calls this
                // from its rest-state geometry reads): these
                // become the morph's two endpoints.
                if mini > 0 { miniRestHeight = Double(mini) }
                if expanded > 0 { expandedRestHeight = Double(expanded) }
                // RW-170: the host's clearance follows the
                // PRESENTED (mini) height only.
                if mini > 0 { onPresentedMiniHeight?(mini) }
            },
            onToggle: { toggle() },
            onCollapse: { collapseExplicit() },
            onOpenCollection: onOpenCollection,
            onOpenShareEntry: onOpenShareEntry,
            onTrash: onTrash
        )
        .frame(height: containerHeight)
        .clippedIfMorphing(travelClipActive, radius: WearIveRunMorph.cornerRadius(progress: expansionProgress))
        .onAppear {
            expansionPolicy.scrollChanged(atFooter: isAtFooter)
            syncProgress(animated: false)
        }
        .onChange(of: isAtFooter) { _, newValue in
            // RB-01/R-1: the scroll reached (or left) the page
            // end — the policy decides what that means for the
            // presentation; the morph follows its verdict.
            expansionPolicy.scrollChanged(atFooter: newValue)
            syncProgress(animated: true)
        }
    }

    /// The toggle action — the SAME action from the body tap
    /// and the toggle control (RB-02): the R-1 policy records
    /// a summon or an explicit collapse, and the morph runs
    /// to the policy's verdict.
    private func toggle() {
        guard exitProgress == 0 else { return }
        expansionPolicy.userToggled()
        syncProgress(animated: true)
    }

    /// The explicit collapse (glide-up on the surface): an
    /// R-1 dismissal — at the footer it suppresses the
    /// footer's re-presentation until the scroll leaves.
    private func collapseExplicit() {
        guard exitProgress == 0 else { return }
        expansionPolicy.userCollapsed()
        syncProgress(animated: true)
    }

    /// Run the one progress value to the R-1 policy's current
    /// verdict, with the morph's 380 ms cubic-bezier(0.32,
    /// 0.72, 0, 1) (RB-03; Reduced Motion: the ≤150 ms
    /// crossfade). The host learns the settled expanded state
    /// for its §4.7 exit choreography.
    private func syncProgress(animated: Bool) {
        let target: Double = expansionPolicy.isExpanded ? 1 : 0
        onExpansionChange?(expansionPolicy.isExpanded)
        guard target != expansionProgress else { return }
        if animated {
            withAnimation(WearIveRunMorph.morphAnimation(reduceMotion: reduceMotion)) {
                expansionProgress = target
            }
        } else {
            expansionProgress = target
        }
    }
}

private extension View {
    /// Clip only while a morph or exit is TRAVELLING — at
    /// either rest state the container must not clip: the
    /// dock's and the panel's soft shadows live outside their
    /// bounds, and the expanded state's boundary IS its
    /// rounded shape. RW-162 G5: the clip follows the morph's
    /// own rounded shape (never a plain rectangle), so even
    /// mid-travel the surface keeps its corners.
    @ViewBuilder func clippedIfMorphing(_ condition: Bool, radius: Double) -> some View {
        if condition {
            self.clipShape(RoundedRectangle(cornerRadius: radius))
        } else {
            self
        }
    }
}
