import SwiftUI

// MARK: - "Wear I've Run" Mini Bar (RW-25 scaffolding; RW-26 full behavior)
//
// RW-135 spec (Direction A′, APPROVED by Sol Hedaya Nov 7,
// 2026 — DESIGN_AND_ANIMATION_SPEC.md is the document of
// record): the Wear I've Run presence is TWO surfaces over
// one recorded entry —
// - the floating MINI dock (this file's WearIveRunMiniBarView):
//   a true overlay, inset 12 / bottom 14, radius 16,
//   translucent card material; at rest while a session entry
//   exists, absent before one does (§1.3/§2.1);
// - the EXPANDED footer section (WearIveRunFooterSectionView
//   below): full-bleed ground surface at the page foot, ALWAYS
//   in the layout while an entry exists (§2.2).
// The scroll mediates between them: dock visibility is a pure
// function of the footer's position (§3 handoff, computed in
// WearIveRunMorph.handoffFade — no timers, no state machine
// for the handoff). Tapping the dock glides the page to the
// footer (§4.4); the footer's chevron-down glides back up.
// Narration is a pure function of the recorded entry (§1.2);
// there is no "Added" line (§9.1); the creation cue (§4.1)
// and the update bounce (§4.3, Sol's Nov 7 ruling) are the
// only pulses. The interim build's in-place expansion morph
// and machine-gated footer are superseded by this spec; the
// WearIveRunElementMachine type remains for its pure
// transition tests and timing constants.

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
//   bottom padding BELOW the footer, so at scroll end the whole
//   page — footer included — rests clear above the band, and
//   every element can be scrolled fully out from under the dock;
// - the RW-135 §3 handoff is untouched: the fade stays a pure
//   function of the footer's measured position, and the host's
//   footer-top bookkeeping derives from this one constant.
enum DockResidence {
    /// The dock's bottom inset (§2.1: 14 pt above the bottom edge).
    static let bottomInset: CGFloat = 14
    /// The dock's side insets (§2.1: 12 pt).
    static let horizontalInset: CGFloat = 12
    /// The single-line dock's measured height (RW-144 gate).
    static let measuredDockHeight: CGFloat = 56
    /// Bottom clearance carried below the footer in every Home
    /// scroll view: the dock's height + its bottom inset + 10 pt
    /// of margin, sized up for the wrapped-narration dock forms
    /// (RW-138's wrap-and-grow can stand taller than 56 pt).
    static let scrollEndClearance: CGFloat = 80

    /// The dock band's top edge in a viewport of `viewportHeight`
    /// for a dock of `dockHeight`.
    static func dockTop(viewportHeight: CGFloat, dockHeight: CGFloat) -> CGFloat {
        viewportHeight - bottomInset - dockHeight
    }

    /// The footer's top edge in content coordinates (the host's
    /// §4.4 glide bookkeeping): content height minus the scroll-
    /// end clearance minus the footer's measured height — one
    /// formula for the stacked and Duo layouts alike.
    static func footerTopContentY(contentHeight: Double, footerHeight: Double) -> Double {
        contentHeight - Double(scrollEndClearance) - footerHeight
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
    static let wordmark = AppStrings.wirWordmark

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
            return AppEnglish.wirUpdatedLine(payload.rendered)
        case .planned(let place, let dayLabel, let moment):
            return AppEnglish.wirPlannedLine(place, plannedSuffix(dayLabel: dayLabel, moment: moment))
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
            return AppEnglish.wirPlanSuffixDayMoment(dayLabel, moment.rendered)
        case .daypart:
            return AppEnglish.wirPlanSuffixMoment(moment.rendered)
        case .now:
            return AppEnglish.wirPlanSuffixDay(dayLabel)
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
        if calendar.isDate(entry.dayStart, inSameDayAs: tomorrow) { return AppEnglish.wirDayTomorrow }
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
        if calendar.isDate(entry.dayStart, inSameDayAs: now) { return AppEnglish.wirDayToday }
        let tomorrow = calendar.date(
            byAdding: .day, value: 1,
            to: calendar.startOfDay(for: now)) ?? now
        if calendar.isDate(entry.dayStart, inSameDayAs: tomorrow) { return AppEnglish.wirDayTomorrow }
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
            let items = AppEnglish.wirItemsCount(entry.itemNames.count)
            return AppEnglish.wirEntryLineCount(entry.placeDisplayName, day, formattedFeelsLike, items)
        }
        return AppEnglish.wirEntryLineCount(entry.placeDisplayName, day, formattedFeelsLike, entry.itemNames.joined(separator: " · "))
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
            return AppEnglish.wirPlaceMoment(viewedPlace.displayName, momentText)
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
        if isViewingNow { return AppEnglish.wirNow }
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

    // MARK: RW-135 spec §3 / §4.4 — handoff + glide mathematics
    //
    // The handoff is a PURE function of the footer's position:
    // no timers, no state machine. `a` is how far (pt) the
    // footer's top edge sits ABOVE the scroll viewport's bottom
    // edge (negative while the footer is still below the fold).

    /// Smoothstep easing (0 → 1, zero slope at both ends).
    static func smoothstep(_ x: Double) -> Double {
        let t = min(1, max(0, x))
        return t * t * (3 - 2 * t)
    }

    /// The dissolve window (spec §3, kept exactly per §11.2):
    /// the dock dissolves across the 37 pt in which the
    /// footer's top edge travels from 83 pt to 120 pt above
    /// the viewport's bottom edge.
    static let handoffWindowStart: Double = 83
    static let handoffWindowWidth: Double = 37

    /// Dock fade (0 = dock fully present, 1 = fully handed
    /// off) from the footer's position. Under Reduce Motion
    /// (§5) the dissolve becomes a step at the window's
    /// midpoint (101.5 pt) with a single 160 ms crossfade,
    /// which the dock view applies as an animation on this
    /// value.
    static func handoffFade(
        footerTopAboveViewportBottom a: Double,
        reduceMotion: Bool = false
    ) -> Double {
        if reduceMotion {
            return a >= handoffWindowStart + handoffWindowWidth / 2 ? 1 : 0
        }
        return smoothstep((a - handoffWindowStart) / handoffWindowWidth)
    }

    /// Spec §4.4 glide duration: clamp(450 ms, 350 + 0.55 ×
    /// distancePt, 950 ms) — critically damped feel, no
    /// overshoot at the end.
    static func glideDurationSeconds(distancePt: Double) -> Double {
        min(0.95, max(0.45, 0.35 + 0.00055 * max(0, distancePt)))
    }

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
    static let emptyLine = AppEnglish.wirEmptyLine

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
        .accessibilityLabel(AppEnglish.wirWordmarkLine(WearIveRunMiniBarModel.wordmark, Self.emptyLine))
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
    /// Cue-worthy landing counter (creation only) — a change is
    /// fed to the entrance logic as a creation event.
    let cueCount: Int
    /// RW-135 spec §4.3: the collection model's narration
    /// version — bumped when the derived line changes. The
    /// cross-fade itself is driven by `state`; this value is
    /// bookkeeping for the surfaces' shared cue discipline.
    var narrationVersion: Int = 0
    /// RW-135 spec §3: the handoff fade (0 = dock fully
    /// present, 1 = fully handed off to the footer), computed
    /// by the host as a pure function of the footer's position
    /// (`WearIveRunMorph.handoffFade`). Default 0 before the
    /// host's first measurement.
    var handoffFade: Double = 0
    /// RW-135 spec §4.7: host-orchestrated exit progress
    /// (0 = at rest, 1 = fully exited: faded + sunk 26 pt).
    var exitProgress: Double = 0
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
    /// RW-135 spec §4.4: tapping the dock body or its expand
    /// control is the JUMP — the host glides the page to its
    /// end. There is no in-place expansion anymore (Direction
    /// A′: the one expanded form is the footer section).
    var onGlideToFooter: () -> Void = {}
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
                // §3 handoff: opacity 1−fade, sink 12·fade,
                // scale 1−0.02·fade — pure functions of the
                // host-supplied fade. Under Reduce Motion the
                // fade is a step and the view crossfades it
                // once over 160 ms (§5).
                .opacity(dockOpacity)
                .offset(y: dockOffsetY)
                .scaleEffect(dockScale)
                .allowsHitTesting(
                    handoffFade < 0.5 && exitProgress == 0
                        && WearIveRunElementDimming.isInteractive(sheetPresented: isDimmedBySheet))
                .accessibilityHidden(handoffFade >= 1)
                .animation(
                    reduceMotion ? .easeInOut(duration: 0.16) : nil,
                    value: handoffFade)
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

    /// Opacity composition: backdrop dim (D-11) × handoff
    /// (1−fade) × exit (1−progress) × entrance fade.
    private var dockOpacity: Double {
        WearIveRunElementDimming.opacity(sheetPresented: isDimmedBySheet)
            * (1 - handoffFade) * (1 - exitProgress) * entranceProgress
    }

    /// Vertical offset: the creation rise (+160% of height →
    /// 0), the handoff sink (+12·fade), the exit sink (+26).
    /// Reduce Motion removes the rise and the exit sink (§5).
    private var dockOffsetY: CGFloat {
        let rise = reduceMotion ? 0 : CGFloat(1 - entranceProgress) * CGFloat(dockHeight) * 1.6
        let exitSink = reduceMotion ? 0 : CGFloat(26 * exitProgress)
        return rise + CGFloat(12 * handoffFade) + exitSink
    }

    /// Scale composition: cue pulse × update bounce × press ×
    /// handoff (1 − 0.02·fade).
    private var dockScale: CGFloat {
        pulseScale * bounceScale * pressScale * CGFloat(1 - 0.02 * handoffFade)
    }

    // MARK: The dock bar (one row, natural height)

    /// The dock's single rendered form: ONE copy of the content,
    /// rendered at its natural height in every state (RW-86:
    /// no self-measurement in the paint path — the one
    /// measurement below serves only the entrance's rise
    /// distance). RW-135 Direction A′: the dock carries no
    /// expanded block; the expanded form is the footer section.
    private var dockBar: some View {
        headerRow
            .onGeometryChange(for: Double.self) { proxy in
                proxy.size.height
            } action: { _, height in
                if height > 0 { dockHeight = height }
            }
            .background {
                // §7.6 / spec §2.1: the MINI material — the
                // translucent card tint over the system
                // material blur (light #FDFBF6 at 85% / dark
                // #2A251E at 82%). The material itself is never
                // animated (decision §4 — no blur animation).
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
            let shown = displayedState ?? state
            if let line = WearIveRunNarration.line(for: shown) {
                // RW-138 (RW-135 spec §4.3): the narration WRAPS
                // and the bar's height follows — there is NO
                // line cap, so the payload can never be
                // ellipsized at any type size. (The two-line cap
                // that stood here cut the plan form's day —
                // "Tomorr…" — and long place names mid-word.)
                dockNarration(line, for: shown)
                    .id(line)
                    .transition(.opacity)
            }
            Spacer(minLength: 0)
            barControls
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture { handleGlideTap() }
    }

    /// §4.3: apply a changed recorded line as an opacity-only
    /// cross-fade (≈210 ms total) — and, per Sol's Nov 7, 2026
    /// ruling (§11.1 resolved), the surface ALSO gives the
    /// update bounce when the deferral gate releases the line.
    /// The bounce plays here when the dock is not handed off
    /// (fade < 1); handed off, the footer's header zone bounces
    /// instead. Never concurrent with a scroll gesture (the
    /// parking in `onChange(of: state)`), and never doubled
    /// with the creation cue: this path runs only for a line
    /// released AFTER the dock mounted with its first line.
    private func applyNarrationCrossfade(to newState: WearIveRunState) {
        guard displayedState != newState else { return }
        if handoffFade < 1, exitProgress == 0 {
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
    }

    /// Two tiny grayed controls at the right end (spec §9.2):
    /// the expand control is the corners-out glyph
    /// ("arrow.up.left.and.arrow.down.right"), NO rotation —
    /// it performs the same glide as tapping the bar body
    /// (§4.4); trash routes to the host's §4.7 exit
    /// orchestration. The SAME two controls in every state.
    private var barControls: some View {
        HStack(spacing: 4) {
            Button {
                handleGlideTap()
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.wirExpand)
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
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppEnglish.wirRemoveToday)
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
        Text(AppEnglish.wirPlannedFor).foregroundColor(AppTheme.inkSecondary)
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
                return Text(AppEnglish.wirUpdatedTo).foregroundColor(AppTheme.inkSecondary)
                    + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                    + Text(", \(moment.rendered)").foregroundColor(AppTheme.inkSecondary)
            }
            return Text(AppEnglish.wirUpdatedTo).foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .planned(let place, let dayLabel, let moment):
            // §7.2: "Planned for {place} · {day}" — the place in
            // pine bold, the day word in secondary ink. RW-150:
            // the suffix (day + any picked time) comes from the
            // one shared composition the plain line uses.
            return Text(AppEnglish.wirPlannedFor).foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(WearIveRunNarration.plannedSuffix(dayLabel: dayLabel, moment: moment)).foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }

    // MARK: §4.4 — the glide tap

    /// Press (scale 0.97 over 120 ms) + one light haptic on
    /// release, then the host glides the page to its end. The
    /// handoff runs off the glide's own scroll position (§3) —
    /// there is no separate dock animation.
    private func handleGlideTap() {
        guard handoffFade < 0.5, exitProgress == 0 else { return }
        if reduceMotion {
            glideHapticNonce += 1
            onGlideToFooter()
            return
        }
        withAnimation(.easeInOut(duration: 0.12)) {
            pressScale = 0.97
        } completion: {
            glideHapticNonce += 1
            withAnimation(.easeOut(duration: 0.15)) { pressScale = 1 }
            onGlideToFooter()
        }
    }

    // MARK: §4.1 — creation entrance + cue

    /// The entrance runs ONLY on a genuine creation (cueCount
    /// bumped by the machine's entryCreated path): a cold start
    /// over an already-present entry (entryAlreadyPresent,
    /// cueCount == 0) appears silently at rest. When the dock
    /// is handed off at creation (fade ≥ 1), the pulse plays
    /// on the footer's header zone instead — the dock simply
    /// stands at rest, invisible.
    private func runEntranceIfNeeded() {
        guard !didRunEntrance else { return }
        didRunEntrance = true
        guard cueCount > 0 else {
            entranceProgress = 1
            return
        }
        guard handoffFade < 1 else {
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

// MARK: - RW-135 spec §2.2 — the footer section (Direction A′)
//
// The EXPANDED form: a full-bleed section IN THE FLOW, the last
// thing on the page, present in the layout whenever a session
// entry exists (§2.2 — its space is reserved even while the
// user is at the top). Surface = the page ground (light
// #F5F1E8 / dark #2A251E — AppTheme.cardOnSheet), top corners
// 24 pt only (bottom square), a soft top shadow + 1 pt top
// hairline, internal padding 20 / 16 / 32. Anatomy: Great
// Vibes 31 pt header + chevron-down (the inverse glide, §4.4)
// + delete controls; narration 15 pt; entry row (thumbnail
// 56 pt, radius 14, entry line 14.5 pt); and the "Open the
// collection ›" doorway (§4.6), tappable — as is the entry row.
// The footer carries the same §4.3 discipline as the dock:
// its line passes through the deferral gate and cross-fades,
// its fields cross-fade in place (§4.5), and when the dock is
// handed off the creation pulse / update bounce play on its
// header zone (§4.1/§4.3).
struct WearIveRunFooterSectionView: View {
    /// The recorded narration state (the same derivation the
    /// dock renders — one record, one line).
    let state: WearIveRunState
    /// The session's recorded entry.
    let entry: WearIveRunEntry
    /// The entry's feels-like, formatted in the user's unit.
    let formattedFeelsLike: String
    /// §4.3 bookkeeping: the model's narration version.
    var narrationVersion: Int = 0
    /// Creation counter — the §4.1 grow/pulse gate (a genuine
    /// creation only; cold start stands silent).
    var cueCount: Int = 0
    /// True while the dock is handed off (handoff fade ≥ 1):
    /// the creation pulse and the update bounce play on THIS
    /// surface's header zone instead of the dock's.
    var presentsCues: Bool = false
    /// §7.5: true while the host page's scroll gesture is live
    /// — the footer's line defers its cross-fade identically.
    var isScrollGestureActive: Bool = false
    /// §4.1: the host's judgement that the footer is in or
    /// near the viewport at creation — the 480 ms grow plays
    /// only then; far off-screen it inserts silently.
    var growOnAppear: Bool = false
    /// §4.7 host-orchestrated exit: content opacity (fades
    /// 160 ms first) and height fraction (then folds to 0
    /// over 400 ms), driven by the host over a FROZEN snapshot
    /// of this record.
    var exitContentOpacity: Double = 1
    var exitHeightFraction: Double = 1
    let onOpenCollection: () -> Void
    /// §4.4: the chevron-down inverse glide, hosted.
    var onGlideUp: () -> Void = {}
    /// §4.7: trash — the host orchestrates the exit.
    var onTrash: () -> Void = {}

    @State private var displayedState: WearIveRunState?
    @State private var pendingNarrationState: WearIveRunState?
    @State private var narrationDeferral = WearIveRunNarrationDeferral()
    /// §4.5: the entry the footer is SHOWING — narration and
    /// entry line flip together at the cross-fade's midpoint;
    /// the thumbnail cross-fades when the hero image changes.
    @State private var displayedEntry: WearIveRunEntry?
    /// Header-zone cue/bounce (§4.1/§4.3 when handed off).
    @State private var headerScale: CGFloat = 1
    @State private var isPulsing = false
    @State private var ringScale: CGFloat = 1
    @State private var ringOpacity: Double = 0
    @State private var cueHapticNonce = 0
    @State private var trashHapticNonce = 0
    /// §4.1 creation grow: 0 → 1 over 480 ms fold when in view.
    @State private var growFraction: Double = 1
    @State private var growContentOpacity: Double = 1
    /// The footer's natural height, measured for the host's
    /// scroll bookkeeping and this view's grow/exit height
    /// folds — never in the dock's paint path (RW-86).
    @State private var naturalHeight: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var shownEntry: WearIveRunEntry { displayedEntry ?? entry }
    private var shownState: WearIveRunState { displayedState ?? state }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            headerRow
            // Narration (§2.2): 15 pt secondary, payload pine
            // semibold — cross-fades in place via the deferral
            // gate, exactly like the dock's (§4.3/§4.5).
            if let line = WearIveRunNarration.line(for: shownState) {
                // RW-138 (spec §4.3): no line cap here either —
                // the footer narration wraps whole and the
                // section's height follows, exactly like the
                // entry line below it. Truncating the payload
                // is never acceptable on either surface.
                footerNarrationText(line)
                    .font(.system(size: 15))
                    .id(line)
                    .transition(.opacity)
            }
            entryRow
            // §4.6 / §2.2: the doorway, visible in the expanded
            // form (Sol's Nov 4 requirement, W-1).
            Button(action: onOpenCollection) {
                Text(AppEnglish.wirOpenCollection)
                    .font(.system(size: 15.5, weight: .semibold))
                    .foregroundColor(AppTheme.pinePayload)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppEnglish.wirOpenCollectionFull)
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
        .padding(.bottom, 32)
        .opacity(exitContentOpacity * growContentOpacity)
        .onGeometryChange(for: Double.self) { proxy in
            proxy.size.height
        } action: { _, height in
            if height > 0, exitHeightFraction >= 1, growFraction >= 1 {
                naturalHeight = height
            }
        }
        .frame(height: heightOverride)
        .background {
            // §2.2: full-bleed ground surface, top corners
            // 24 pt, bottom square.
            UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(
                topLeading: 24, topTrailing: 24
            ))
            .fill(AppTheme.cardOnSheet)
        }
        .overlay(alignment: .top) {
            // §2.2: 1 pt top hairline at ink 4%.
            Rectangle()
                .fill(AppTheme.inkPrimary.opacity(0.04))
                .frame(height: 1)
                .padding(.horizontal, 24)
        }
        .clipShape(UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(
            topLeading: 24, topTrailing: 24
        )))
        // §2.2: the soft top shadow (0 / −10, 28 pt blur →
        // radius 14, black 7%).
        .shadow(color: Color.black.opacity(0.07), radius: 14, x: 0, y: -10)
        // §4.5: a re-wrap height change follows over the
        // 380 ms morph; Reduce Motion snaps it (§5).
        .animation(
            reduceMotion ? nil : .timingCurve(0.32, 0.72, 0, 1, duration: 0.38),
            value: WearIveRunNarration.expandedLine(
                for: shownEntry, formattedFeelsLike: formattedFeelsLike))
        .sensoryFeedback(.impact(flexibility: .soft), trigger: cueHapticNonce)
        .sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)
        .accessibilityLabel(AppStrings.wirWordmarkExpanded(WearIveRunMiniBarModel.wordmark))
        .onAppear {
            if displayedState == nil {
                displayedState = state
                displayedEntry = entry
                _ = narrationDeferral.offer(
                    line: WearIveRunNarration.line(for: state),
                    gestureActive: false)
            }
            runCreationGrowIfNeeded()
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

    /// Grow/exit height: natural height × fractions while a
    /// fold is in flight; unconstrained at rest.
    private var heightOverride: CGFloat? {
        guard naturalHeight > 0 else { return nil }
        let fraction = growFraction * exitHeightFraction
        return fraction < 1 ? CGFloat(naturalHeight * fraction) : nil
    }

    /// Header row (§2.2): Great Vibes 31 pt + chevron-down and
    /// delete controls, ink, right-aligned. The header zone
    /// carries the creation pulse / update bounce when the
    /// dock is handed off (§4.1/§4.3).
    private var headerRow: some View {
        HStack(spacing: 8) {
            Text(WearIveRunMiniBarModel.wordmark)
                .font(.greatVibes(size: 31))
                .foregroundColor(AppTheme.inkPrimary)
            Spacer(minLength: 0)
            Button {
                onGlideUp()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkPrimary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.wirCollapse)
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
            .accessibilityLabel(AppEnglish.wirRemoveToday)
        }
        .scaleEffect(headerScale)
        .overlay {
            if isPulsing {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(AppTheme.pinePayload, lineWidth: 2)
                    .scaleEffect(ringScale)
                    .opacity(ringOpacity)
            }
        }
    }

    /// The entry row (§2.2/§7.3 doorway): the recorded
    /// thumbnail (56 pt, radius 14) + the full entry line at
    /// 14.5 pt. Tapping it opens the collection sheet. The
    /// thumbnail cross-fades when the record's hero image
    /// changes (§4.5).
    private var entryRow: some View {
        Button(action: onOpenCollection) {
            HStack(spacing: 12) {
                // The thumbnail renders ONLY once its image
                // has decoded — no placeholder graphic and no
                // broken-image frame ever paints (D-12).
                AsyncImage(url: URL(string: shownEntry.heroImageReference)) { phase in
                    if case .success(let image) = phase {
                        HeroPhoto(image: image, width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    } else {
                        Color.clear
                            .frame(width: 56, height: 56)
                    }
                }
                .id(shownEntry.heroImageReference)
                .transition(.opacity)
                // No line limit, by design (WS-4): the entry
                // line wraps whole rather than truncating.
                Text(WearIveRunNarration.expandedLine(
                    for: shownEntry,
                    formattedFeelsLike: formattedFeelsLike
                ))
                .font(.system(size: 14.5))
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.leading)
                .id(WearIveRunNarration.expandedLine(
                    for: shownEntry, formattedFeelsLike: formattedFeelsLike))
                .transition(.opacity)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppEnglish.wirOpenCollectionFull)
    }

    /// §4.3/§4.5: a released line flips the footer's fields —
    /// narration and entry line together, thumbnail keyed by
    /// hero reference — as an opacity cross-fade (≈210 ms),
    /// plus the update bounce on the header zone when this
    /// surface presents the cues (dock handed off).
    private func applyFieldCrossfade(to newState: WearIveRunState) {
        if presentsCues, exitHeightFraction >= 1 {
            runHeaderBounce()
        }
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

    /// §4.1 creation: if in/near the viewport, the footer
    /// grows 0 → natural height over 480 ms fold with content
    /// fading in across the second half, and — when the dock
    /// is handed off — the pulse plays on the header zone at
    /// the settle. Far off-screen (or a cold start), it simply
    /// stands. Reduce Motion (§5): crossfade 220 ms, height
    /// snaps, no pulse, haptic kept.
    private func runCreationGrowIfNeeded() {
        guard cueCount > 0, growOnAppear else { return }
        if reduceMotion {
            growContentOpacity = 0
            withAnimation(.easeInOut(duration: 0.22)) {
                growContentOpacity = 1
            } completion: {
                if presentsCues { cueHapticNonce += 1 }
            }
            return
        }
        growFraction = 0
        growContentOpacity = 0
        withAnimation(.timingCurve(0.65, 0, 0.35, 1, duration: 0.48)) {
            growFraction = 1
        }
        withAnimation(.easeIn(duration: 0.24).delay(0.24)) {
            growContentOpacity = 1
        } completion: {
            if presentsCues { fireHeaderCueSettle() }
        }
    }

    /// The header-zone creation pulse (§4.1, handed-off
    /// placement): scale 1.018 + inset pine ring, 900 ms, one
    /// soft haptic at the settle.
    private func fireHeaderCueSettle() {
        cueHapticNonce += 1
        guard !reduceMotion else { return }
        isPulsing = true
        ringScale = 1
        ringOpacity = 0.9
        withAnimation(.easeOut(duration: WearIveRunElementMachine.cuePulseDurationSeconds)) {
            ringScale = 1.06
            ringOpacity = 0
        }
        let half = WearIveRunElementMachine.cuePulseDurationSeconds / 2
        withAnimation(.easeInOut(duration: half)) {
            headerScale = WearIveRunElementMachine.cuePulseScale
        } completion: {
            withAnimation(.easeInOut(duration: half)) {
                headerScale = 1
            } completion: {
                isPulsing = false
            }
        }
    }

    /// The header-zone update bounce (§4.3): ≈1.015 overshoot
    /// + one soft haptic at the settle. Reduce Motion removes
    /// the bounce; the haptic is kept (§5).
    private func runHeaderBounce() {
        if reduceMotion {
            cueHapticNonce += 1
            return
        }
        withAnimation(.easeOut(duration: 0.16)) {
            headerScale = CGFloat(WearIveRunMorph.updateBounceScale)
        } completion: {
            withAnimation(.easeInOut(duration: 0.22)) {
                headerScale = 1
            } completion: {
                cueHapticNonce += 1
            }
        }
    }

    /// The footer's narration rich form — the same anatomy as
    /// the dock's (verb secondary, payload pine bold), rendered
    /// from the recorded state only (§7.0).
    private func footerNarrationText(_ line: String) -> Text {
        switch shownState {
        case .updated(let payload):
            // RW-150: same anatomy as the dock's — the place in
            // pine bold, its moment segment in secondary ink —
            // composed from the same recorded state (§7.0), so
            // dock and footer agree in every change class.
            if case .placeAtMoment(let place, let moment) = payload {
                return Text(AppEnglish.wirUpdatedTo).foregroundColor(AppTheme.inkSecondary)
                    + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                    + Text(", \(moment.rendered)").foregroundColor(AppTheme.inkSecondary)
            }
            return Text(AppEnglish.wirUpdatedTo).foregroundColor(AppTheme.inkSecondary)
                + Text(payload.rendered).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
        case .planned(let place, let dayLabel, let moment):
            return Text(AppEnglish.wirPlannedFor).foregroundColor(AppTheme.inkSecondary)
                + Text(place).foregroundColor(AppTheme.pinePayload).fontWeight(.semibold)
                + Text(WearIveRunNarration.plannedSuffix(dayLabel: dayLabel, moment: moment)).foregroundColor(AppTheme.inkSecondary)
        case .preAdd:
            return Text(line).foregroundColor(AppTheme.inkSecondary)
        }
    }
}
