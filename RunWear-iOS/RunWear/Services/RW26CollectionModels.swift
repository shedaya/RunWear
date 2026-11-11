import Foundation

// MARK: - RW-26 Phase 2 — pure models (dual-membered into RunWearTests)
//
// Foundation-only, like MakeItMePriceLabels, so the test target can
// pin the arithmetic directly. Everything in this file is a pure
// function or a thin UserDefaults persistence wrapper over one:
// the "Wear I've Run" collection, the location recents, the home
// header composition, the scaffold-serve policy, the feedback
// routing (RW-18), and the guided-selfie checks. The collection
// arithmetic is the cross-platform contract: the Android lane
// implements the same rules in Kotlin and both sides pin them.

// MARK: - App session (GEN-3, I-WIR: once per session)
//
// OPERATIONAL SESSION DEFINITION (binding, Sol Nov 4, 2026):
// a session is one continuous period of app use beginning when
// the app process launches (cold start — `RunWearApp` comes up)
// and ending when that process terminates (the user swipes the
// app away, or the system kills it). Backgrounding the app and
// returning to the SAME live process does NOT start a new
// session; only a new process does. Within a session there is
// exactly ONE "Wear I've Run" entry: every settled generation
// updates that entry IN PLACE (place and weather included), the
// scrapbook keeps the session's last settled version, and a new
// session starts a new entry. This supersedes the gen-2
// once-per-day keying (and its dock lookup BY DATE): entries are
// keyed by SESSION.

enum WearIveRunSession {
    /// The current process's session identity: minted once, the
    /// first time anything asks, and stable for the rest of the
    /// process's life (a `static let` — one value per launch).
    /// Deliberately NOT persisted: persisting it would let a
    /// later launch resurrect a dead session and merge two
    /// sessions' entries into one.
    static let currentID: String = UUID().uuidString
}

// MARK: - "Wear I've Run" entry

/// One session's settled outfit. The saved entry is the
/// session's LAST settled version — within the session, every
/// location and weather change replaces it in place, so a
/// session that visited three places holds ONE entry, at the
/// last place. Entries are created automatically at the
/// session's first generation. There is NO backfill — the store
/// only ever holds sessions the app actually generated in.
///
/// LEGACY: entries minted before the session rule carry no
/// session identity (`sessionID == ""`, decoded from payloads
/// written before the field existed). They keep their original
/// day+place identity, stay in the collection untouched, and
/// are never adopted or merged into a later session's entry —
/// each legacy row reads as the single entry of a session that
/// predates the keying.
// MARK: - Narration payload types (GEN-5 Addendum 8 — C-7 made structural)
//
// Sol's device proved the breach on Android: the mini bar read
// "Update to Ear Warmer / Headband" — a GARMENT in the narration
// payload. The iOS defence is not a display filter: the payload
// is a TYPE whose cases cannot hold a garment at all.
//
// - A moment reading is one of: now, a clock reading (an hour
//   number and an AM/PM flag), or a daypart (a day word —
//   "Tomorrow" or a weekday name — plus one of four parts of
//   day). There is no free-text case.
// - An update payload is one of: a place (the entry's simple
//   place name) or a moment reading. There is no third case.
//
// Both types persist as their rendered string, so stores written
// by earlier builds keep loading: decoding parses the string
// back into cases, and a legacy string that fits NO case (a
// garment payload written by the gen-3/gen-4 defect) is dropped
// at decode — the entry falls back to its place — rather than
// carried forward as data the model can no longer produce.

enum MomentReading: Equatable, Hashable {
    case now
    case clock(hour12: Int, isPM: Bool)
    case daypart(dayWord: String, part: DayPart)

    enum DayPart: String, Equatable, Hashable {
        case morning, afternoon, evening, night

        /// The part of day for a 24-hour clock hour (the
        /// production site's settled bands).
        static func forHour(_ hour: Int) -> DayPart {
            switch hour {
            case 5..<12: return .morning
            case 12..<17: return .afternoon
            case 17..<22: return .evening
            default: return .night
            }
        }
    }

    var rendered: String {
        switch self {
        case .now:
            return "Now"
        case .clock(let hour12, let isPM):
            return "\(hour12) \(isPM ? "p.m." : "a.m.")"
        case .daypart(let dayWord, let part):
            return "\(dayWord) \(part.rawValue)"
        }
    }

    /// Parse a persisted rendered reading back into its case.
    /// Returns nil for a string no case can render — stamped
    /// forms and garment names alike.
    init?(parsing string: String) {
        let text = string.trimmingCharacters(in: .whitespacesAndNewlines)
        if text == "Now" {
            self = .now
            return
        }
        // Clock form: "9 p.m." / "12 a.m."
        let clockParts = text.split(separator: " ")
        if clockParts.count == 2,
           let hour = Int(clockParts[0]), (1...12).contains(hour),
           clockParts[1] == "p.m." || clockParts[1] == "a.m." {
            self = .clock(hour12: hour, isPM: clockParts[1] == "p.m.")
            return
        }
        // Daypart form: "{Tomorrow|Weekday} {morning|afternoon|evening|night}"
        if clockParts.count == 2,
           let part = DayPart(rawValue: String(clockParts[1])) {
            let dayWord = String(clockParts[0])
            let weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            if dayWord == "Tomorrow" || weekdays.contains(dayWord) {
                self = .daypart(dayWord: dayWord, part: part)
                return
            }
        }
        return nil
    }
}

enum NarrationPayload: Equatable, Hashable {
    case place(String)
    case moment(MomentReading)
    /// RW-150 (Nov 9, 2026): the FULL settled state — the place
    /// AND its moment, rendered "{place}, {moment}" ("New York,
    /// NY, Now" / "Miami, FL, 7 p.m."). RW-98's grammar names
    /// the new state in full; the gen-5 place-only payload could
    /// not express the moment half, which is how the settled
    /// line came to drop ", Now" and every picked time. This
    /// case is SYNTHESIZED at render time by the narration
    /// state builder from the recorded entry — it is never the
    /// stored update payload (the record-time diff payload
    /// stays .place / .moment, exactly as C-7 defined it), so
    /// the persisted string forms are unchanged.
    case placeAtMoment(String, MomentReading)

    var rendered: String {
        switch self {
        case .place(let name): return name
        case .moment(let reading): return reading.rendered
        case .placeAtMoment(let name, let reading): return "\(name), \(reading.rendered)"
        }
    }
}

extension MomentReading: Codable {
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let string = try container.decode(String.self)
        guard let parsed = MomentReading(parsing: string) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unrepresentable moment reading: \(string)"
            )
        }
        self = parsed
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rendered)
    }
}

extension NarrationPayload: Codable {
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let string = try container.decode(String.self)
        if let reading = MomentReading(parsing: string) {
            self = .moment(reading)
        } else {
            self = .place(string)
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rendered)
    }
}

struct WearIveRunEntry: Codable, Equatable, Hashable, Identifiable {
    /// Start-of-day (local) for the day the entry last settled.
    let dayStart: Date
    /// The place's display name, as shown when the entry was made.
    let placeName: String
    /// Comfort-adjusted feels-like, Fahrenheit (the one frame —
    /// RW-23: the same value the home hero carries).
    let feelsLikeF: Double
    /// The day's actual temperature, Fahrenheit.
    let actualF: Double
    /// Condition label (e.g. "Cloudy").
    let condition: String
    /// The outfit's item names, in outfit order.
    let itemNames: [String]
    /// The outfit's item keys, parallel to itemNames — the Kit
    /// Card resolves the app's real garment renders by key.
    let itemKeys: [String]
    /// Reference to the day's hero image (remote URL or asset name).
    let heroImageReference: String
    /// RW-152 §3: the hero's DURABLE identity, persisted alongside
    /// the display URL — the storage-object path for personal
    /// heroes (whose display URL is a signed URL with a ONE-HOUR
    /// TTL, so a relaunch after the hour finds the display URL
    /// dead), the stable URL itself for pool/curated heroes.
    /// Legacy payloads carry no key: decoding derives it from
    /// the display reference (a signed URL reduces to its path),
    /// which is the expired-signed-URL recovery.
    let heroDurableReference: String
    /// The owning app session's identity (GEN-3 session rule).
    /// Empty ONLY for legacy entries decoded from pre-session
    /// payloads; every entry recorded since carries the session
    /// that settled it.
    let sessionID: String
    /// Whether this entry's narration is the UPDATE form (GEN-3,
    /// census Y-5): true once any in-session settle after the
    /// creation changed the entry's kit or place. The flag lives
    /// ON THE ENTRY — it is written in the same store write as
    /// the place, kit, weather, and hero it describes — so the
    /// narration kind can never be read from a different settle
    /// than the payload it narrates. (Build 21 kept the flag in
    /// separate view-model state: after the first place switch
    /// the narration read the newest settle while the payload
    /// read an older entry, and the two never agreed again.)
    /// The collection model stamps it at record time; callers
    /// constructing a settle pass the default.
    let wasUpdatedInSession: Bool
    /// GEN-5 (RW-28, C-7): the entry's settled moment as a simple
    /// human reading ("Now", "9 p.m.", "Tomorrow evening") — the
    /// payload an in-session TIME change narrates. Never a stamped
    /// date, never a garment.
    ///
    /// GEN-5 (Addendum 8): the reading is a STRUCTURAL type, not
    /// a string — see MomentReading below. A garment name has no
    /// case to live in, so a garment payload is unrepresentable
    /// in the model, not merely filtered at display time (the
    /// Android device breach: "Update to Ear Warmer / Headband").
    let momentReading: MomentReading
    /// GEN-5 (RW-28, C-7): the update narration's payload, computed
    /// at record time from the DIFF (the place that changed, or the
    /// moment reading that changed) and stored on the entry with
    /// the content it describes. Nil for a creation.
    ///
    /// GEN-5 (Addendum 8): likewise structural — NarrationPayload
    /// below. Its only cases are a place and a moment reading.
    let updatePayload: NarrationPayload?
    /// GEN-5 (RW-28 item 10): when this entry last settled — the
    /// TODAY card's "Last saved …" element. Legacy payloads decode
    /// it as the entry's day.
    let lastSavedAt: Date
    /// GEN-6 (RW-31 Wave B): the day's wind at settle time (mph),
    /// for the poster's Tier 4 micro-line. Optional and decoded
    /// null-safely — entries recorded before Wave B carry nil and
    /// the poster composes without a fabricated wind figure.
    let windMph: Double?
    /// GEN-6 (RW-31 Wave B): the day's actual-temperature low /
    /// high (Fahrenheit), for the Swing caption. Optional,
    /// null-safe like `windMph`.
    let dayLowF: Double?
    let dayHighF: Double?
    /// GEN-6 (RW-31 §4.3): the hero photograph's stored face
    /// anchor, when generation/pool metadata exposes one.
    /// Optional and null-safe; nil anchors resolve at render
    /// time (Vision detection, then the default composition
    /// point) — see ShareStudioModels.
    let faceAnchor: ShareFaceAnchor?

    init(
        dayStart: Date,
        placeName: String,
        feelsLikeF: Double,
        actualF: Double,
        condition: String,
        itemNames: [String],
        itemKeys: [String],
        heroImageReference: String,
        heroDurableReference: String? = nil,
        sessionID: String = "",
        wasUpdatedInSession: Bool = false,
        momentReading: MomentReading = .now,
        updatePayload: NarrationPayload? = nil,
        lastSavedAt: Date? = nil,
        windMph: Double? = nil,
        dayLowF: Double? = nil,
        dayHighF: Double? = nil,
        faceAnchor: ShareFaceAnchor? = nil
    ) {
        self.dayStart = dayStart
        self.placeName = placeName
        self.feelsLikeF = feelsLikeF
        self.actualF = actualF
        self.condition = condition
        self.itemNames = itemNames
        self.itemKeys = itemKeys
        self.heroImageReference = heroImageReference
        // RW-152 §3: an explicitly stamped durable identity wins;
        // otherwise it derives from the display reference at the
        // same moment (signed URL → path, stable URL → itself).
        self.heroDurableReference = heroDurableReference
            ?? HeroRefResolver.durableReference(forDisplayReference: heroImageReference)
        self.sessionID = sessionID
        self.wasUpdatedInSession = wasUpdatedInSession
        self.momentReading = momentReading
        self.updatePayload = updatePayload
        self.lastSavedAt = lastSavedAt ?? dayStart
        self.windMph = windMph
        self.dayLowF = dayLowF
        self.dayHighF = dayHighF
        self.faceAnchor = faceAnchor
    }

    /// A copy of this entry carrying the given narration flag —
    /// how the collection model commits the narration kind WITH
    /// the entry it describes, as one value (Y-5 atomicity).
    func settingWasUpdatedInSession(_ value: Bool) -> WearIveRunEntry {
        settingNarration(wasUpdated: value, payload: updatePayload)
    }

    /// GEN-5: a copy carrying the narration kind AND its payload,
    /// committed in the same store write as the content (Y-5
    /// atomicity, extended to the C-7 payload).
    func settingNarration(wasUpdated: Bool, payload: NarrationPayload?) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: placeName,
            feelsLikeF: feelsLikeF, actualF: actualF, condition: condition,
            itemNames: itemNames, itemKeys: itemKeys,
            heroImageReference: heroImageReference,
            heroDurableReference: heroDurableReference,
            sessionID: sessionID, wasUpdatedInSession: wasUpdated,
            momentReading: momentReading, updatePayload: payload,
            lastSavedAt: lastSavedAt,
            windMph: windMph, dayLowF: dayLowF, dayHighF: dayHighF,
            faceAnchor: faceAnchor
        )
    }

    /// RW-159/RW-160: a copy carrying a RECOVERED durable
    /// reference — the bank re-link (HeroRecovery) found this
    /// entry's personal hero in the server bank after the entry
    /// settled with an empty display reference. Only the
    /// durable identity changes; the display reference stays
    /// what settle wrote, and every other field is the entry's
    /// own. The resolver re-signs the durable path at render.
    func withHeroDurableReference(_ durable: String) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: placeName,
            feelsLikeF: feelsLikeF, actualF: actualF, condition: condition,
            itemNames: itemNames, itemKeys: itemKeys,
            heroImageReference: heroImageReference,
            heroDurableReference: durable,
            sessionID: sessionID, wasUpdatedInSession: wasUpdatedInSession,
            momentReading: momentReading, updatePayload: updatePayload,
            lastSavedAt: lastSavedAt,
            windMph: windMph, dayLowF: dayLowF, dayHighF: dayHighF,
            faceAnchor: faceAnchor
        )
    }

    private enum CodingKeys: String, CodingKey {
        case dayStart, placeName, feelsLikeF, actualF, condition
        case itemNames, itemKeys, heroImageReference, heroDurableReference, sessionID
        case wasUpdatedInSession
        case momentReading, updatePayload, lastSavedAt
        case windMph, dayLowF, dayHighF, faceAnchor
    }

    /// Legacy-tolerant decoding: a payload written before the
    /// session rule has no `sessionID` key — it decodes as the
    /// empty (legacy) identity instead of failing the whole
    /// store load and wiping the user's history. Payloads from
    /// before the Y-5 fix likewise have no narration flag —
    /// they decode as the "Added" form.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        dayStart = try container.decode(Date.self, forKey: .dayStart)
        placeName = try container.decode(String.self, forKey: .placeName)
        feelsLikeF = try container.decode(Double.self, forKey: .feelsLikeF)
        actualF = try container.decode(Double.self, forKey: .actualF)
        condition = try container.decode(String.self, forKey: .condition)
        itemNames = try container.decode([String].self, forKey: .itemNames)
        itemKeys = try container.decode([String].self, forKey: .itemKeys)
        heroImageReference = try container.decode(String.self, forKey: .heroImageReference)
        // RW-152 §3: payloads written before the durable field
        // derive it from the display reference — a legacy signed
        // URL reduces to its storage path, which is exactly the
        // recovery a relaunch past the signed hour needs.
        heroDurableReference = try container.decodeIfPresent(String.self, forKey: .heroDurableReference)
            ?? HeroRefResolver.durableReference(forDisplayReference: heroImageReference)
        sessionID = try container.decodeIfPresent(String.self, forKey: .sessionID) ?? ""
        wasUpdatedInSession = try container.decodeIfPresent(Bool.self, forKey: .wasUpdatedInSession) ?? false
        // GEN-5 (Addendum 8): persisted readings are strings;
        // they decode back into the structural cases. A legacy
        // string no case can render (a stamped form) decodes as
        // .now rather than failing the load.
        if let rawMoment = try container.decodeIfPresent(String.self, forKey: .momentReading) {
            momentReading = MomentReading(parsing: rawMoment) ?? .now
        } else {
            momentReading = .now
        }
        // The update payload, likewise: the entry's own place
        // string decodes as .place, a moment form as .moment,
        // and anything else — a garment payload written by the
        // old defect — is DROPPED (nil), so the entry narrates
        // from its place until its next settle stamps a payload
        // the current model can produce.
        if let rawPayload = try container.decodeIfPresent(String.self, forKey: .updatePayload) {
            let simpleName = SimplePlace(rawDisplayName: placeName)?.displayName
                ?? placeName.trimmingCharacters(in: .whitespacesAndNewlines)
            if rawPayload == simpleName {
                updatePayload = .place(simpleName)
            } else if let reading = MomentReading(parsing: rawPayload) {
                updatePayload = .moment(reading)
            } else {
                updatePayload = nil
            }
        } else {
            updatePayload = nil
        }
        lastSavedAt = try container.decodeIfPresent(Date.self, forKey: .lastSavedAt) ?? dayStart
        // GEN-6 (RW-31 Wave B): the share fields are null-safe —
        // a payload written before they existed decodes them as
        // nil rather than failing the load.
        windMph = try container.decodeIfPresent(Double.self, forKey: .windMph)
        dayLowF = try container.decodeIfPresent(Double.self, forKey: .dayLowF)
        dayHighF = try container.decodeIfPresent(Double.self, forKey: .dayHighF)
        faceAnchor = try container.decodeIfPresent(ShareFaceAnchor.self, forKey: .faceAnchor)
    }

    /// The entry's place as a SimplePlace (WS-4 / RC-4): the
    /// stored string is parsed through the model, so an entry
    /// recorded before the model existed (raw geocode string)
    /// and one recorded after it (simple form) are the same
    /// place with the same rendered name.
    var simplePlace: SimplePlace? {
        SimplePlace(rawDisplayName: placeName)
    }

    /// The ONLY form a rendered line may show for this entry's
    /// place: city + abbreviated region. Falls back to the
    /// trimmed stored string only when it carries no name at
    /// all (unreachable for a recorded entry).
    var placeDisplayName: String {
        simplePlace?.displayName
            ?? placeName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The narration's payload for an update (GEN-5, RW-28,
    /// C-7 — supersedes the WS-4 garment payload): what CHANGED,
    /// computed at record time and stored on the entry — the new
    /// place when the place changed, the new moment reading when
    /// the time changed. NEVER a garment: the payload is the
    /// structural NarrationPayload (Addendum 8), whose only
    /// cases are a place and a moment reading. Falls back to
    /// the entry's place for entries recorded before the
    /// payload was stored.
    var narrationPayload: NarrationPayload {
        updatePayload ?? .place(placeDisplayName)
    }

    /// Place identity: the SimplePlace key — trimmed, case-folded,
    /// and canonicalized, so "New York, New York" (legacy raw)
    /// and "New York, NY" (simple) share one identity.
    var placeKey: String {
        simplePlace?.key
            ?? placeName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// Identity (GEN-3 session rule): an entry recorded by a
    /// session IS that session — one identity for the session's
    /// whole life, across every place and weather change, so an
    /// in-session settle REPLACES the entry instead of minting a
    /// sibling. Legacy entries (no session identity) keep their
    /// original identity: one entry per day per place.
    var id: String {
        sessionID.isEmpty
            ? "\(Int(dayStart.timeIntervalSince1970))|\(placeKey)"
            : "session|\(sessionID)"
    }
}

// MARK: - Weather filter

/// The collection's weather filter (frames 7–8, 52). An entry's
/// categories are computed from the same one-frame values the home
/// hero carries; an entry can belong to several categories (a 30°
/// snow day is both Cold and Snow) and matches a filter when the
/// filter is in its set.
/// GEN-5 (RW-28, C-7): the update payload from the settle diff —
/// the original Nov 2 concept: name whatever changed. Place beats
/// moment (a move is the bigger change); a moment change names the
/// new simple reading (a clock reading like "9 p.m." is in-spec
/// per Sol's Nov 5 nuance ruling). Called only for place/moment
/// updates — a kit-only settle never reaches here (it is silent
/// and preserves the payload it found). Garments are never named.
enum WearIveRunNarrationRules {
    /// GEN-5 (Addendum 8): the payload is constructed ONLY as
    /// a place case (from the entry's simple place name) or a
    /// moment case (from the entry's structural moment
    /// reading). No other value can be stamped.
    static func updatePayload(
        existing: WearIveRunEntry,
        incoming: WearIveRunEntry
    ) -> NarrationPayload {
        if existing.placeKey != incoming.placeKey {
            return .place(incoming.placeDisplayName)
        }
        return .moment(incoming.momentReading)
    }
}

enum WearIveRunWeatherFilter: String, CaseIterable, Equatable {
    case all
    case cold
    case snow
    case rain
    case heat

    var label: String {
        switch self {
        case .all: return "All"
        case .cold: return "Cold"
        case .snow: return "Snow"
        case .rain: return "Rain"
        case .heat: return "Heat"
        }
    }
}

enum WearIveRunWeatherClassifier {
    /// Cold below 40°F feels-like; Heat at 85°F and above.
    static let coldThresholdF: Double = 40
    static let heatThresholdF: Double = 85

    static func categories(feelsLikeF: Double, condition: String) -> Set<WearIveRunWeatherFilter> {
        var result: Set<WearIveRunWeatherFilter> = []
        let lowered = condition.lowercased()
        if lowered.contains("snow") || lowered.contains("flurr") || lowered.contains("blizzard") {
            result.insert(.snow)
        }
        if lowered.contains("rain") || lowered.contains("drizzle")
            || lowered.contains("thunder") || lowered.contains("shower") {
            result.insert(.rain)
        }
        if feelsLikeF < coldThresholdF { result.insert(.cold) }
        if feelsLikeF >= heatThresholdF { result.insert(.heat) }
        return result
    }

    static func matches(_ entry: WearIveRunEntry, filter: WearIveRunWeatherFilter) -> Bool {
        filter == .all || categories(feelsLikeF: entry.feelsLikeF, condition: entry.condition).contains(filter)
    }
}

// MARK: - Collection arithmetic

/// A run of consecutive calendar days at one place.
struct WearIveRunStay: Equatable {
    /// Entries in the stay, newest-first. Under the session rule
    /// a stay can hold several entries for one calendar day (one
    /// per session that day), so the DAY count below counts
    /// distinct days, not entries.
    let entries: [WearIveRunEntry]
    var dayCount: Int { Set(entries.map(\.dayStart)).count }
    /// Earliest day in the stay.
    var firstDay: Date { entries.last!.dayStart }
    /// Latest day in the stay.
    var lastDay: Date { entries.first!.dayStart }
}

/// One place's chapter: its entries, stays, cover, and the computed
/// summary language. Every number here is computed from the entries
/// the current filter admits — filtering recomputes the chapter.
struct WearIveRunChapter: Equatable {
    let placeName: String
    /// All admitted entries, newest-first.
    let entries: [WearIveRunEntry]
    /// Stays newest-first; days within a stay newest-first.
    let stays: [WearIveRunStay]

    /// RW-153 (Nov 9, 2026): the cover is the NEWEST ENTRY WITH A
    /// LOADABLE REFERENCE — not `entries.first` unconditionally.
    /// New York's newest entry settled with an EMPTY reference
    /// while older entries carried good photos, and the place
    /// card rendered an empty banner (Sol's RW-153 screenshot).
    /// The newest entry (honest placeholder) is the fallback
    /// only when NO entry in the chapter has a reference. Counts,
    /// spans, and ranges still recompute from the full set.
    var cover: WearIveRunEntry {
        entries.first(where: { HeroRefResolver.hasLoadableReference($0) }) ?? entries.first!
    }
    /// Distinct calendar days in the chapter (session rule: a
    /// chapter's entries are SESSIONS, and two sessions can share
    /// a day — the summary's "days" counts days, not sessions).
    /// Identical to the entry count for legacy data, which holds
    /// at most one entry per day per place.
    var dayCount: Int { Set(entries.map(\.dayStart)).count }
    var feelsLikeRangeF: (min: Double, max: Double) {
        let values = entries.map(\.feelsLikeF)
        return (values.min()!, values.max()!)
    }

    /// The computed summary line (frames 7, 50):
    /// - multi-stay: "{n} stays · {total} days · Last stay · {span}"
    ///   — never a single span implying continuity;
    /// - single-stay: "{total} days · {span}" (or "1 day · {span}").
    /// The temperature unit formatter is injected so the range
    /// renders in the user's unit; degrees are whole numbers.
    func summaryLine(formatTemperature: (Double) -> String, calendar: Calendar = .current) -> String {
        let range = feelsLikeRangeF
        let rangeText = range.min == range.max
            ? formatTemperature(range.min)
            : "\(formatTemperature(range.min))–\(formatTemperature(range.max))"
        if stays.count > 1, let lastStay = stays.first {
            let span = WearIveRunDateFormatting.span(
                from: lastStay.firstDay, to: lastStay.lastDay, calendar: calendar
            )
            return "\(stays.count) stays · \(dayCount) days · Last stay · \(span) · \(rangeText)"
        }
        let span = WearIveRunDateFormatting.span(
            from: entries.last!.dayStart, to: entries.first!.dayStart, calendar: calendar
        )
        let days = dayCount == 1 ? "1 day" : "\(dayCount) days"
        return "\(days) · \(span) · \(rangeText)"
    }
}

/// One month inside a hometown chapter's year grouping (frame 51).
struct WearIveRunMonthGroup: Equatable, Hashable {
    let year: Int
    let month: Int
    /// Days in the month, newest-first.
    let entries: [WearIveRunEntry]
    /// Distinct calendar days (see the chapter's note).
    var dayCount: Int { Set(entries.map(\.dayStart)).count }
    var feelsLikeRangeF: (min: Double, max: Double) {
        let values = entries.map(\.feelsLikeF)
        return (values.min()!, values.max()!)
    }
}

/// Newest-first order for entry lists: by the day the entry
/// last settled, with the entry identity as a deterministic
/// tiebreak (two sessions can settle on the same day; their
/// relative order is by identity, never by sort accident).
private func wearIveRunNewestFirst(_ lhs: WearIveRunEntry, _ rhs: WearIveRunEntry) -> Bool {
    if lhs.dayStart != rhs.dayStart { return lhs.dayStart > rhs.dayStart }
    return lhs.id > rhs.id
}

enum WearIveRunCollection {
    /// Upsert by identity: for a session entry the identity is
    /// the SESSION, so every in-session settle replaces the
    /// session's one entry in place (the last settled version
    /// wins) and a new session appends a new entry; legacy
    /// entries keep their day+place identity.
    static func upserting(_ entries: [WearIveRunEntry], _ entry: WearIveRunEntry) -> [WearIveRunEntry] {
        var result = entries.filter { $0.id != entry.id }
        result.append(entry)
        return result.sorted(by: wearIveRunNewestFirst)
    }

    /// Remove one day's entry at a place (the mini bar's trash:
    /// today's entry, one tap, no confirmation).
    static func removing(_ entries: [WearIveRunEntry], dayStart: Date, placeName: String) -> [WearIveRunEntry] {
        let probe = WearIveRunEntry(
            dayStart: dayStart, placeName: placeName, feelsLikeF: 0, actualF: 0,
            condition: "", itemNames: [], itemKeys: [], heroImageReference: ""
        )
        return entries.filter { $0.id != probe.id }
    }

    /// Remove the entry with this identity (the mini bar's
    /// trash under the session rule: the PRESENTED entry — the
    /// current session's — goes away, whatever place it settled
    /// at and whatever place is being viewed).
    static func removing(_ entries: [WearIveRunEntry], id: String) -> [WearIveRunEntry] {
        entries.filter { $0.id != id }
    }

    /// Fix wave 2026-11-07 (RW-122): remove a whole by-location
    /// GROUP — every entry whose place key matches the named
    /// place's key (chapters collate by place key, so the group
    /// IS the key). Canonicalisation runs through the same
    /// probe-entry path as the day removal above.
    static func removingPlace(_ entries: [WearIveRunEntry], placeName: String) -> [WearIveRunEntry] {
        let probe = WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 0), placeName: placeName,
            feelsLikeF: 0, actualF: 0, condition: "", itemNames: [], itemKeys: [],
            heroImageReference: ""
        )
        return entries.filter { $0.placeKey != probe.placeKey }
    }

    /// The entry a session owns, if it has settled one yet —
    /// the dock's and the collection pin's lookup (GEN-3): by
    /// SESSION, never by date and never by the viewed place.
    ///
    /// GEN-5 (RW-29 FA-36): the LEGACY fallback. An entry
    /// written by a pre-session build carries no session
    /// identity; on the first launch after upgrade the session
    /// owns nothing yet, and build 25's pure session key left
    /// today's legacy entry invisible to both the pin and the
    /// dock until a new settle created a SECOND entry for the
    /// same day. The lookup now falls back to a legacy entry
    /// dated today; the session's first settle at the same
    /// day+place supersedes it (see the collection model's
    /// recordSettled).
    static func sessionEntry(
        from entries: [WearIveRunEntry],
        sessionID: String,
        today: Date = Date(),
        calendar: Calendar = .current
    ) -> WearIveRunEntry? {
        if !sessionID.isEmpty, let owned = entries.first(where: { $0.sessionID == sessionID }) {
            return owned
        }
        return entries.first {
            $0.sessionID.isEmpty && calendar.isDate($0.dayStart, inSameDayAs: today)
        }
    }

    /// Chapters for a filter: entries are filtered FIRST, then
    /// grouped — covers, counts, spans, and ranges all recompute
    /// from the admitted entries. Chapters order by their NEWEST
    /// ENTRY's recency (RW-153: no longer the cover's — the cover
    /// may now be an older entry carrying the loadable photo, and
    /// chapter order must still follow the chapter's latest
    /// activity); empty chapters never appear.
    static func chapters(
        from entries: [WearIveRunEntry],
        filter: WearIveRunWeatherFilter,
        calendar: Calendar = .current
    ) -> [WearIveRunChapter] {
        let admitted = entries.filter { WearIveRunWeatherClassifier.matches($0, filter: filter) }
        let grouped = Dictionary(grouping: admitted, by: \.placeKey)
        var chapters: [WearIveRunChapter] = grouped.values.compactMap { placeEntries in
            let sorted = placeEntries.sorted(by: wearIveRunNewestFirst)
            guard let first = sorted.first else { return nil }
            return WearIveRunChapter(
                // Chapter covers name the place in its simple
                // form (WS-4) — never the cover entry's raw
                // stored string.
                placeName: first.placeDisplayName,
                entries: sorted,
                stays: stays(from: sorted, calendar: calendar)
            )
        }
        chapters.sort { $0.entries.first!.dayStart > $1.entries.first!.dayStart }
        return chapters
    }

    /// Consecutive-day runs within one place's newest-first
    /// entries. Two entries a calendar day apart continue a stay;
    /// anything wider starts a new one. Under the session rule a
    /// gap of ZERO also continues the stay: two sessions on the
    /// same calendar day at the same place are one day of it.
    static func stays(from newestFirst: [WearIveRunEntry], calendar: Calendar = .current) -> [WearIveRunStay] {
        var result: [WearIveRunStay] = []
        var current: [WearIveRunEntry] = []
        for entry in newestFirst {
            if let previous = current.last,
               let gap = calendar.dateComponents(
                   [.day],
                   from: calendar.startOfDay(for: entry.dayStart),
                   to: calendar.startOfDay(for: previous.dayStart)
               ).day, gap == 0 || gap == 1 {
                current.append(entry)
            } else {
                if !current.isEmpty { result.append(WearIveRunStay(entries: current)) }
                current = [entry]
            }
        }
        if !current.isEmpty { result.append(WearIveRunStay(entries: current)) }
        return result
    }

    /// Hometown presentation (frame 51): group a chapter's entries
    /// by year, then month rows — years and months newest-first,
    /// days newest-first inside the month.
    static func monthGroups(for chapter: WearIveRunChapter, calendar: Calendar = .current) -> [WearIveRunMonthGroup] {
        let grouped = Dictionary(grouping: chapter.entries) { entry -> String in
            let comps = calendar.dateComponents([.year, .month], from: entry.dayStart)
            return "\(comps.year ?? 0)-\(comps.month ?? 0)"
        }
        var groups: [WearIveRunMonthGroup] = grouped.values.compactMap { monthEntries in
            guard let first = monthEntries.first else { return nil }
            let comps = calendar.dateComponents([.year, .month], from: first.dayStart)
            return WearIveRunMonthGroup(
                year: comps.year ?? 0,
                month: comps.month ?? 0,
                entries: monthEntries.sorted(by: wearIveRunNewestFirst)
            )
        }
        groups.sort { ($0.year, $0.month) > ($1.year, $1.month) }
        return groups
    }

    /// Today's entries (any place), newest first. Retained for
    /// the legacy date-keyed arithmetic (and its pins); the
    /// collection's PIN no longer comes from here — under the
    /// session rule the pin is the current session's entry
    /// (`sessionEntry(from:sessionID:)`), and the chapters are
    /// computed from the entries that remain after it is lifted
    /// out, so the pinned entry never duplicates a chapter.
    static func todaysEntries(from entries: [WearIveRunEntry], today: Date, calendar: Calendar = .current) -> [WearIveRunEntry] {
        entries
            .filter { calendar.isDate($0.dayStart, inSameDayAs: today) }
            .sorted(by: wearIveRunNewestFirst)
    }
}

enum WearIveRunDateFormatting {
    private static func formatter(_ format: String, calendar: Calendar) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = format
        return formatter
    }

    /// "Nov 2" for one day; "Mar 11–13" inside a month;
    /// "Oct 25 – Nov 2" across months (frame 50's honest spans).
    static func span(from start: Date, to end: Date, calendar: Calendar = .current) -> String {
        if calendar.isDate(start, inSameDayAs: end) {
            return formatter("MMM d", calendar: calendar).string(from: start)
        }
        let sameMonth = calendar.component(.month, from: start) == calendar.component(.month, from: end)
            && calendar.component(.year, from: start) == calendar.component(.year, from: end)
        if sameMonth {
            let month = formatter("MMM", calendar: calendar).string(from: start)
            let first = calendar.component(.day, from: start)
            let last = calendar.component(.day, from: end)
            return "\(month) \(first)–\(last)"
        }
        let fromText = formatter("MMM d", calendar: calendar).string(from: start)
        let toText = formatter("MMM d", calendar: calendar).string(from: end)
        return "\(fromText) – \(toText)"
    }

    /// GEN-5 (RW-28 item 10): the TODAY card's "Last saved …"
    /// element — "just now" inside the minute, a clock reading
    /// today, a day label before that. Never a stamped date-time.
    static func lastSavedLine(savedAt: Date, now: Date = Date(), calendar: Calendar = .current) -> String {
        if now.timeIntervalSince(savedAt) < 60 {
            return "Last saved just now"
        }
        if calendar.isDate(savedAt, inSameDayAs: now) {
            return "Last saved \(formatter("h:mm a", calendar: calendar).string(from: savedAt))"
        }
        return "Last saved \(dayLabel(savedAt, calendar: calendar))"
    }

    /// "Mar 11" style single-day label.
    static func dayLabel(_ date: Date, calendar: Calendar = .current) -> String {
        formatter("MMM d", calendar: calendar).string(from: date)
    }

    /// "Nov 9, 2026" — the full date the RW-152 §4 (J1) past-entry
    /// delete confirmation NAMES alongside the place.
    static func fullDayLabel(_ date: Date, calendar: Calendar = .current) -> String {
        formatter("MMM d, yyyy", calendar: calendar).string(from: date)
    }

    static func monthLabel(year: Int, month: Int, calendar: Calendar = .current) -> String {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        guard let date = calendar.date(from: comps) else { return "\(month)/\(year)" }
        return formatter("MMMM yyyy", calendar: calendar).string(from: date)
    }
}

// MARK: - RW-152 §3 / RW-153: durable hero references + the one resolver

/// RW-152 §3 (parity with the Android design): an entry persists
/// the hero's DURABLE identity alongside the display URL — the
/// storage path for personal heroes (whose display URL is a
/// signed URL with a ONE-HOUR TTL), the stable URL itself for
/// pool/curated heroes. ONE resolver turns a stored reference
/// into a loadable URL at render:
///
///   * stable URLs pass through unchanged;
///   * storage paths re-sign through the client's existing
///     signing path (the async half lives with the API client —
///     this enum is the pure classification/extraction half);
///   * expired signed URLs recover by path extraction + re-sign;
///   * empty stays honestly empty (the placeholder, never a
///     substituted photo).
enum HeroRefResolver {

    /// The Supabase storage bucket personal heroes live in; the
    /// signing path addresses objects inside it by bare path.
    static let personalHeroesBucket = "personal-heroes"

    /// True when the entry carries any reference at all. This is
    /// the RW-153 "loadable reference" test for cover selection:
    /// a settled entry whose reference is empty cannot be the
    /// cover while an older entry carries one.
    static func hasLoadableReference(_ entry: WearIveRunEntry) -> Bool {
        !entry.heroDurableReference.trimmingCharacters(in: .whitespaces).isEmpty
            || !entry.heroImageReference.trimmingCharacters(in: .whitespaces).isEmpty
    }

    /// The durable identity for a display reference: a signed
    /// storage URL reduces to its object path; anything else
    /// (stable URL, file URL, asset reference) IS its own
    /// durable identity.
    static func durableReference(forDisplayReference display: String) -> String {
        if let path = storagePath(from: display) { return path }
        return display
    }

    /// The storage-object path inside the personal-heroes bucket
    /// for a reference, when the reference denotes one: a signed
    /// or public storage URL (path extracted, query stripped —
    /// this is the expired-signed-URL recovery), or a bare path
    /// persisted by a newer client. Nil for stable remote URLs,
    /// file URLs, and non-path references.
    static func storagePath(from reference: String) -> String? {
        let trimmed = reference.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        for marker in ["/object/sign/\(personalHeroesBucket)/",
                       "/object/public/\(personalHeroesBucket)/"] {
            if let range = trimmed.range(of: marker) {
                let tail = trimmed[range.upperBound...]
                let path = tail.split(separator: "?").first.map(String.init) ?? ""
                return path.isEmpty ? nil : (path.removingPercentEncoding ?? path)
            }
        }
        // A bare persisted path: no scheme, path-shaped.
        if !trimmed.contains("://"), trimmed.contains("/"),
           !trimmed.contains(" ") {
            return trimmed.hasPrefix("\(personalHeroesBucket)/")
                ? String(trimmed.dropFirst(personalHeroesBucket.count + 1))
                : trimmed
        }
        return nil
    }

    /// The entry's storage path, when it has one — the durable
    /// reference first, the display URL as the recovery source.
    static func storagePath(for entry: WearIveRunEntry) -> String? {
        storagePath(from: entry.heroDurableReference)
            ?? storagePath(from: entry.heroImageReference)
    }

    /// The reference a surface may load SYNCHRONOUSLY: a stable
    /// URL passes through (durable first, then the display URL);
    /// a storage path returns nil here — it must be re-signed
    /// through the async half. Empty stays nil (honest empty).
    static func stableDisplayReference(for entry: WearIveRunEntry) -> String? {
        for candidate in [entry.heroDurableReference, entry.heroImageReference] {
            let trimmed = candidate.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { continue }
            if trimmed.contains("://"), storagePath(from: trimmed) == nil {
                return trimmed
            }
            if !trimmed.contains("://"), storagePath(from: trimmed) == nil,
               !trimmed.contains("/") {
                return trimmed
            }
        }
        return nil
    }
}

// MARK: - RW-159/RW-160 (device verdict, Nov 10, 2026): bank re-link recovery
//
// THE GAP the RW-152 §3 recovery could not cross: it derives an
// entry's durable reference FROM its display reference — a
// signed URL reduces to its path. An entry that settled with NO
// hero on screen persists an EMPTY display reference
// (`recordTodaysEntry`: flag target nil + the always-available
// fallback nil → ""), and an empty string derives an empty
// durable reference. There is no path to re-sign, the cover
// rule's loadable test fails for every such entry, and the
// chapter falls to the placeholder — while the user's personal
// hero for that very outfit sits in his server bank
// (personal_heroes rows carry combination_id + storage_path).
//
// THE RECOVERY: an entry persists enough identity to find its
// bank hero — itemKeys (the outfit signature the combination
// id's hash segment signs), feelsLikeF (the settle frame's
// comfort-adjusted feels-like — the same value the cell was
// keyed from, so its temp bracket is EXACT), the condition
// label (the weather, lossily: the label collapses neighbouring
// WMO codes, so weather is a CANDIDATE SET, never a guess), and
// the moment reading / last-settled time (the time of day,
// likewise a candidate set). Candidates are tried in a fixed
// priority — exact reconstruction first — against the bank the
// client already reads (fetchBankHeroes' table). A hit is the
// entry's OWN banked hero for its OWN outfit at its OWN
// bracket; nothing is ever substituted across outfits or
// brackets. No hit → the entry stays honestly empty (a look
// settled against a stock/scaffold serve has no personal hero
// to recover).
enum HeroRecovery {

    /// The temp-bracket token for a feels-like value — the
    /// shared engine's table (HeroFrameMapping / TempBracket:
    /// FREEZING <20, COLD 20–34, COOL 35–49, MILD 50–64,
    /// WARM 65–79, HOT ≥80, on the Int-truncated value),
    /// restated here so this Foundation-only file stays
    /// self-contained for the test target.
    static func tempBracketToken(feelsLikeF: Double) -> String {
        switch Int(feelsLikeF) {
        case ..<20: return "FREEZING"
        case 20...34: return "COLD"
        case 35...49: return "COOL"
        case 50...64: return "MILD"
        case 65...79: return "WARM"
        default: return "HOT"
        }
    }

    /// Weather tokens for the entry's persisted condition
    /// label, most likely first. The label is the WeatherCondition
    /// short description ("Clear", "Partly Cloudy", "Cloudy",
    /// "Foggy", "Light Rain", "Rainy", "Snowy", "Storms"); the
    /// engine maps WMO codes 0–1 → CLEAR and 2–3 + fog →
    /// CLOUDY, so a label standing at that boundary carries
    /// both candidates. Unrecognised labels carry all four.
    static func weatherTokens(conditionLabel: String) -> [String] {
        let label = conditionLabel.lowercased()
        let primary: String?
        if label.contains("snow") { primary = "SNOW" }
        else if label.contains("rain") || label.contains("drizzle") || label.contains("storm") { primary = "RAIN" }
        else if label.contains("cloud") || label.contains("fog") || label.contains("overcast") { primary = "CLOUDY" }
        else if label.contains("clear") || label.contains("sunny") || label.contains("sky") { primary = "CLEAR" }
        else { primary = nil }
        var tokens: [String] = []
        if let primary { tokens.append(primary) }
        // Boundary neighbour: code 1 (Mainly clear → CLEAR) and
        // code 2 (Partly cloudy → CLOUDY) wear adjacent labels.
        if primary == "CLEAR" { tokens.append("CLOUDY") }
        if primary == "CLOUDY" { tokens.append("CLEAR") }
        for token in ["CLEAR", "CLOUDY", "RAIN", "SNOW"] where !tokens.contains(token) {
            tokens.append(token)
        }
        return tokens
    }

    /// The time-of-day token for a 24-hour clock hour — the
    /// engine's table (DAWN 5–9, MIDDAY 10–16, DUSK 17–19,
    /// NIGHT 20–4).
    static func timeToken(hour: Int) -> String {
        switch ((hour % 24) + 24) % 24 {
        case 5...9: return "DAWN"
        case 10...16: return "MIDDAY"
        case 17...19: return "DUSK"
        default: return "NIGHT"
        }
    }

    /// Time-of-day tokens for the entry, most likely first:
    /// the recorded moment reading when it carries one (a
    /// clock reading is the settle's own hour; a daypart maps
    /// to its band), else the hour the entry last settled.
    static func timeTokens(for entry: WearIveRunEntry, calendar: Calendar = .current) -> [String] {
        let primary: String
        switch entry.momentReading {
        case .clock(let hour12, let isPM):
            primary = timeToken(hour: (hour12 % 12) + (isPM ? 12 : 0))
        case .daypart(_, let part):
            switch part {
            case .morning: primary = "DAWN"
            case .afternoon: primary = "MIDDAY"
            case .evening: primary = "DUSK"
            case .night: primary = "NIGHT"
            }
        case .now:
            primary = timeToken(hour: calendar.component(.hour, from: entry.lastSavedAt))
        }
        var tokens = [primary]
        for token in ["DAWN", "MIDDAY", "DUSK", "NIGHT"] where token != primary {
            tokens.append(token)
        }
        return tokens
    }

    /// Candidate combination ids for the entry, in priority
    /// order — the exact reconstruction first, then the same
    /// weather at other times of day, then the boundary/other
    /// weathers. The bracket and the outfit hash never vary:
    /// both derive exactly from persisted values, and a hero
    /// banked under a different bracket or outfit is a
    /// DIFFERENT look, never this entry's photo.
    static func candidateCombinationIds(
        for entry: WearIveRunEntry,
        genderSegment: String,
        calendar: Calendar = .current
    ) -> [String] {
        guard !entry.itemKeys.isEmpty else { return [] }
        let hash = OutfitSignatureHash.hash(itemKeys: entry.itemKeys)
        let bracket = tempBracketToken(feelsLikeF: entry.feelsLikeF)
        var ids: [String] = []
        for weather in weatherTokens(conditionLabel: entry.condition) {
            for time in timeTokens(for: entry, calendar: calendar) {
                ids.append("\(genderSegment)_\(weather)_\(bracket)_\(time)_\(hash)")
            }
        }
        return ids
    }

    /// The entry's recovered durable reference (a bank storage
    /// path), when recovery applies: the entry carries NO
    /// loadable reference (an entry with one is never
    /// re-linked — its own identity wins) and one of its
    /// candidate cells is banked. `bankPaths` is the bank as
    /// the client reads it: combination_id → storage_path.
    static func recoveredDurableReference(
        for entry: WearIveRunEntry,
        genderSegment: String,
        bankPaths: [String: String],
        calendar: Calendar = .current
    ) -> String? {
        guard !HeroRefResolver.hasLoadableReference(entry) else { return nil }
        for id in candidateCombinationIds(for: entry, genderSegment: genderSegment, calendar: calendar) {
            if let path = bankPaths[id], !path.isEmpty { return path }
        }
        return nil
    }
}

// MARK: - RW-161 (device verdict, Nov 10, 2026): one sheet owner on Home
//
// Build 27's "Share this day" sheet stood open while horizontal
// swipes cycled the Settings / Set Location / Share sheets
// behind and over each other: Home carried TEN independent
// sheet states (nine booleans + two item bindings, split across
// TWO hosts — the NavigationStack's content and WeatherOutfitView
// itself), and SwiftUI presents whichever are non-nil, in
// whichever order their hosts evaluate. There was no single
// fact of "which sheet is up", so nothing could refuse a second
// presentation while one stood.
//
// THE DISCIPLINE: one owner token. A route may present only
// while it OWNS the token; a request made while another route
// owns it is QUEUED (FIFO) and presented when the owner
// releases. This enum is the pure arbitration the host's
// bindings all funnel through, pinned here.
enum HomeSheetRoute: String, Equatable, CaseIterable {
    case settings, datePicker, timePicker, weatherDetail, locationPicker
    case collection, wearShare, feedback, heroPaywall, looksLibrary, heroViewer
}

enum HomeSheetArbiter {

    /// Claim the token for `requested`. Granted when the token
    /// is free (or already the requester's); otherwise the
    /// request queues behind the owner, once.
    static func claim(
        owner: HomeSheetRoute?,
        queue: [HomeSheetRoute],
        requested: HomeSheetRoute
    ) -> (owner: HomeSheetRoute?, queue: [HomeSheetRoute]) {
        if owner == nil { return (requested, queue) }
        if owner == requested || queue.contains(requested) { return (owner, queue) }
        return (owner, queue + [requested])
    }

    /// Release `released`. Releasing the owner hands the token
    /// to the oldest queued route; releasing a queued route
    /// just dequeues it; releasing anything else is a no-op.
    static func release(
        owner: HomeSheetRoute?,
        queue: [HomeSheetRoute],
        released: HomeSheetRoute
    ) -> (owner: HomeSheetRoute?, queue: [HomeSheetRoute]) {
        if owner == released {
            var rest = queue
            let next = rest.isEmpty ? nil : rest.removeFirst()
            return (next, rest)
        }
        return (owner, queue.filter { $0 != released })
    }

    /// Whether `route` may be presented right now: it owns the
    /// token. Every Home sheet binding's getter is this test.
    static func mayPresent(owner: HomeSheetRoute?, route: HomeSheetRoute) -> Bool {
        owner == route
    }
}

// MARK: - RW-162 G2 (device verdict, Nov 10, 2026): the fresh-fix rule
//
// "When I click on current location, it doesn't really work or
// at least not consistently." An explicit Current Location
// tap's one-shot request could be satisfied by the location
// manager's CACHED fix — with kilometre accuracy desired, a
// fix from the previous city qualifies — so the tap could
// "work" (a fix delivered, weather fetched) while changing
// nothing the user could see, or work properly when a fresh
// fix happened to arrive. The rule the tap is judged by: a
// fix counts as the tap's answer only when its own timestamp
// is at or after the request (2 s of clock slack); the wait
// is bounded, and the best fix seen answers when the window
// closes, so the tap never dead-ends. LocationService
// executes this; the rule is pure so it is pinned here.
enum FreshFixPolicy {
    /// How long an explicit request waits for a fresh fix.
    static let windowSeconds: TimeInterval = 10
    /// Clock slack applied to a fix's timestamp.
    static let slackSeconds: TimeInterval = 2

    static func isFresh(fixDate: Date, requestedAt: Date) -> Bool {
        fixDate >= requestedAt.addingTimeInterval(-slackSeconds)
    }

    static func windowClosed(requestedAt: Date, now: Date) -> Bool {
        now.timeIntervalSince(requestedAt) >= windowSeconds
    }
}

// MARK: - RW-152 §4 (J1): the past-entry delete confirmation rule

/// RW-152 §4 + owner ruling J1 (Nov 9, 2026): deleting a PAST
/// Wear I've Run entry requires a confirmation NAMING the place
/// and the date, with copy stating plainly that entries live
/// only on this device and deletion cannot be undone; TODAY's
/// entry stays one-tap (settling and correcting today's look is
/// the session's own flow).
enum WearIveRunDeletePolicy {

    static func requiresConfirmation(for entry: WearIveRunEntry,
                                     now: Date = Date(),
                                     calendar: Calendar = .current) -> Bool {
        calendar.startOfDay(for: entry.dayStart) < calendar.startOfDay(for: now)
    }

    static func confirmationTitle(for entry: WearIveRunEntry) -> String {
        "Delete this entry?"
    }

    static func confirmationMessage(for entry: WearIveRunEntry,
                                    calendar: Calendar = .current) -> String {
        "Delete the \(entry.placeDisplayName) entry from "
            + "\(WearIveRunDateFormatting.fullDayLabel(entry.dayStart, calendar: calendar))? "
            + "Entries live only on this device, and deletion cannot be undone."
    }
}

// MARK: - RW-158: entry tap routing (user ruling, Nov 10, 2026)
//
// "Pressing on the past run should simply open the share
// interface that we've designed." Tapping a PAST Wear I've Run
// entry opens the Share Studio sheet ("Share this day") for
// that entry DIRECTLY — opening is viewing: the sheet does not
// invoke the system share sheet or share anything; the user
// still chooses the format and taps Share inside. The routing
// is a pure decision, pinned at this layer:
//
// - a past entry (its day is strictly before today) →
//   `.shareSheet`, from every entry surface (the chapter's
//   day/entry rows and the expanded panel's entry row alike);
// - today's (or a future planned) entry → UNCHANGED: the
//   chapter row keeps its day view (`.dayView`), the expanded
//   panel's row keeps opening the collection
//   (`.openCollection`), the collection's pinned entry and the
//   home hero are untouched by this item;
// - the place-group / chapter card itself → `.drillDown`
//   (into the chapter's days — unchanged);
// - the poster preview inside the share sheet → `.fullPhoto`
//   (the RW-133 full-screen photo stays reachable from inside
//   the sheet; the eye/full-screen affordance does not
//   disappear with the routing change).
enum WearIveRunTapDestination: Equatable {
    case shareSheet
    case dayView
    case drillDown
    case fullPhoto
    case openCollection
}

enum WearIveRunTapPolicy {

    /// A PAST entry: its recorded day is strictly before
    /// today (the same day boundary the RW-152 §4 delete
    /// policy uses).
    static func isPast(_ entry: WearIveRunEntry,
                       now: Date = Date(),
                       calendar: Calendar = .current) -> Bool {
        calendar.startOfDay(for: entry.dayStart) < calendar.startOfDay(for: now)
    }

    /// A day/entry row inside a place chapter (RW-158): a past
    /// entry opens the share sheet; today/future keeps the
    /// day view it has always opened.
    static func chapterEntryDestination(
        for entry: WearIveRunEntry,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> WearIveRunTapDestination {
        isPast(entry, now: now, calendar: calendar) ? .shareSheet : .dayView
    }

    /// The expanded panel's entry row (RW-158): a past entry
    /// opens the share sheet; today/future keeps opening the
    /// collection, as it does today.
    static func expandedPanelEntryDestination(
        for entry: WearIveRunEntry,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> WearIveRunTapDestination {
        isPast(entry, now: now, calendar: calendar) ? .shareSheet : .openCollection
    }

    /// The place-group / chapter card: drill-down into the
    /// chapter's days — unchanged by RW-158.
    static let chapterCardDestination: WearIveRunTapDestination = .drillDown

    /// The poster preview inside the share sheet: the
    /// full-screen photo (RW-133) — unchanged reachability.
    static let sharePreviewDestination: WearIveRunTapDestination = .fullPhoto
}

// MARK: - Tolerant array decoding (GEN-5, RW-29 FA-14)

/// Record-by-record decoding for the UserDefaults-backed
/// stores: the payload is split into its elements and each is
/// decoded on its own, so one malformed record drops itself
/// and only itself. A payload that is not a JSON array at all
/// still reads as empty.
enum TolerantArrayDecoding {
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) -> [T] {
        guard let raw = try? JSONSerialization.jsonObject(with: data),
              let array = raw as? [Any] else { return [] }
        return array.compactMap { element in
            guard let elementData = try? JSONSerialization.data(withJSONObject: element) else {
                return nil
            }
            return try? JSONDecoder().decode(T.self, from: elementData)
        }
    }
}

// MARK: - Collection persistence

/// UserDefaults-backed store for the collection. The entry list is
/// the only state; every read goes through the pure functions
/// above, so what the tests pin is what the UI renders.
final class WearIveRunStore {
    private let defaults: UserDefaults
    private let key = "wearIveRun.entries.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var entries: [WearIveRunEntry] {
        guard let data = defaults.data(forKey: key) else { return [] }
        // GEN-5 (RW-29 FA-14): decode RECORD BY RECORD — build
        // 25 decoded the whole array atomically, so one
        // malformed record discarded the user's entire run
        // history silently. A malformed record now drops only
        // itself; its neighbours survive.
        return TolerantArrayDecoding.decode(WearIveRunEntry.self, from: data)
    }

    /// Record a settled entry (automatic at the session's first
    /// generation; later settles in the same session replace it
    /// in place — the store upserts by the entry's identity,
    /// which is the session for session entries).
    func record(_ entry: WearIveRunEntry) {
        save(WearIveRunCollection.upserting(entries, entry))
    }

    /// GEN-5 (RW-29 FA-36): remove the LEGACY twin of a
    /// session's first settle — a pre-session entry for the
    /// same day at the same place. A legacy entry at a
    /// DIFFERENT place is the user's history and is never
    /// touched by a settle elsewhere.
    func removeLegacyTwin(of entry: WearIveRunEntry, calendar: Calendar = .current) {
        let survivors = entries.filter { candidate in
            !(candidate.sessionID.isEmpty
                && candidate.id != entry.id
                && candidate.placeKey == entry.placeKey
                && calendar.isDate(candidate.dayStart, inSameDayAs: entry.dayStart))
        }
        if survivors.count != entries.count { save(survivors) }
    }

    /// The mini bar's trash: remove today's entry at a place.
    func remove(dayStart: Date, placeName: String) {
        save(WearIveRunCollection.removing(entries, dayStart: dayStart, placeName: placeName))
    }

    /// The mini bar's trash under the session rule: remove the
    /// entry with this identity (the presented session entry).
    func remove(id: String) {
        save(WearIveRunCollection.removing(entries, id: id))
    }

    /// Fix wave 2026-11-07 (RW-122): remove every entry filed
    /// under one place (the chapter's delete-all control).
    func removePlace(placeName: String) {
        save(WearIveRunCollection.removingPlace(entries, placeName: placeName))
    }

    /// The entry the given session owns — what the home dock
    /// presents and the collection pins (GEN-3, superseding the
    /// W-15 date lookup below). The dock's entry is the CURRENT
    /// SESSION's entry, not an entry at the place currently
    /// being VIEWED and not merely any entry dated today: its
    /// narration keeps naming the place the session's entry
    /// last settled at after the user browses elsewhere.
    func sessionEntry(sessionID: String) -> WearIveRunEntry? {
        WearIveRunHomePresentation.sessionEntry(from: entries, sessionID: sessionID)
    }

    /// Today's entry, whichever place it was recorded at — the
    /// W-15 (gen-2) dock lookup, BY DATE. Retained for the
    /// legacy arithmetic and its pins; the dock no longer
    /// presents through it (the session rule replaced its key).
    func todaysEntry(today: Date, calendar: Calendar = .current) -> WearIveRunEntry? {
        WearIveRunHomePresentation.todaysEntry(from: entries, today: today, calendar: calendar)
    }

    /// Today's entry at the current place, if one exists — what
    /// the mini bar narrates.
    func todaysEntry(placeName: String, today: Date, calendar: Calendar = .current) -> WearIveRunEntry? {
        // Both sides canonicalize through SimplePlace (WS-4), so
        // a caller holding the simple name matches an entry stored
        // under the legacy raw string, and vice versa.
        let probeKey = SimplePlace(rawDisplayName: placeName)?.key
            ?? placeName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return entries.first {
            $0.placeKey == probeKey
                && calendar.isDate($0.dayStart, inSameDayAs: today)
        }
    }

    private func save(_ entries: [WearIveRunEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: key)
    }
}

// MARK: - Home recording & presentation seam (W-15)
//
// The W-15 mechanism, pinned as pure functions so the home
// view's wiring is a thin call into tested code:
//
// (a) CREATION. Home appears mid-fetch: `WeatherViewModel`
// publishes weather + recommendation BEFORE its reverse
// geocode lands, so the home view's first appearance always
// precedes the real place on a GPS first load. A recording
// made at that moment is filed under the placeholder label
// ("Loading...", or "Current Location" when geocoding fails)
// — a non-place. `eligiblePlace` therefore admits ONLY a
// resolved `SimplePlace`: no resolved place, no entry. The
// home view re-offers the recording when the place resolves
// (its `placeDisplayName` change), exactly as Android's
// recording effect is keyed on the location name.
//
// (b) PRESENTATION. The dock presents the CURRENT SESSION's
// entry (GEN-3 session rule — this key REPLACES the gen-2
// by-date key, which was itself the W-15 fix for the original
// defect: a lookup by the currently VIEWED place, which no
// placeholder-filed entry could ever match and no entry at a
// different place could match either). By session, the entry
// the dock presents is the one this session has been updating
// in place, whichever place it last settled at and whichever
// place is on screen now.
enum WearIveRunHomePresentation {
    /// The place an entry may be recorded under: the resolved
    /// simple place's display name, or nil while the place is
    /// still a placeholder label (no `SimplePlace` derived yet).
    static func eligiblePlace(_ simplePlace: SimplePlace?) -> String? {
        simplePlace?.displayName
    }

    /// The current session's entry (see the store's note): the
    /// entry recorded under this session's identity, whichever
    /// place it names and whichever day it last settled on.
    static func sessionEntry(
        from entries: [WearIveRunEntry],
        sessionID: String
    ) -> WearIveRunEntry? {
        WearIveRunCollection.sessionEntry(from: entries, sessionID: sessionID)
    }

    /// Today's entry by DATE alone — the gen-2 presentation
    /// key, retained for the legacy arithmetic and its pins;
    /// the dock no longer presents through it.
    static func todaysEntry(
        from entries: [WearIveRunEntry],
        today: Date,
        calendar: Calendar = .current
    ) -> WearIveRunEntry? {
        entries.first { calendar.isDate($0.dayStart, inSameDayAs: today) }
    }
}

// MARK: - Location recents (Section E, frames 23–25)

/// A place the user has actually selected before. Recents join by
/// being chosen — there is no "add favorite" step.
struct RecentLocation: Codable, Equatable, Identifiable {
    let name: String
    let latitude: Double
    let longitude: Double

    var id: String { "\(name.lowercased())|\(latitude)|\(longitude)" }
}

enum RecentLocations {
    /// The list's cap (frame 23).
    static let cap = 5

    /// Record a selection: most-recent-first, de-duplicated by
    /// identity, capped. Selecting a place already in the list
    /// moves it to the front.
    static func recording(_ list: [RecentLocation], selection: RecentLocation) -> [RecentLocation] {
        // A recent's name is a rendered place line (WS-4): it is
        // stored in the simple form, so a selection carrying a
        // raw geocode string is normalized as it joins the list.
        let normalized = RecentLocation(
            name: SimplePlace(rawDisplayName: selection.name)?.displayName ?? selection.name,
            latitude: selection.latitude,
            longitude: selection.longitude
        )
        var result = list.filter { $0.id != normalized.id }
        result.insert(normalized, at: 0)
        return Array(result.prefix(cap))
    }

    static func removing(_ list: [RecentLocation], id: String) -> [RecentLocation] {
        list.filter { $0.id != id }
    }
}

final class RecentLocationsStore {
    private let defaults: UserDefaults
    private let key = "location.recents.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var recents: [RecentLocation] {
        guard let data = defaults.data(forKey: key) else { return [] }
        // GEN-5 (RW-29 FA-14): per-record tolerant decoding, as
        // in the collection store above.
        return TolerantArrayDecoding.decode(RecentLocation.self, from: data)
    }

    func record(_ selection: RecentLocation) {
        save(RecentLocations.recording(recents, selection: selection))
    }

    func remove(id: String) {
        save(RecentLocations.removing(recents, id: id))
    }

    private func save(_ list: [RecentLocation]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        defaults.set(data, forKey: key)
    }
}

// MARK: - Home header composition (notes §7.1–7.2)

/// The settled home header stack, as pure copy: (i) the small
/// uppercase label; (ii) the location line "{Place} · {Moment}" in
/// sentence case; (iv) the actual-temperature line carrying the
/// temperature ONLY. The hero numeral and the weather strip are
/// rendered from the view model directly.
enum HomeHeaderCopy {
    static let feelsLikeLabel = "Feels like"

    /// The location line's place is the SimplePlace (WS-4): the
    /// string overload routes through the model, so a raw geocode
    /// string handed to this formatter still cannot reach the
    /// rendered line.
    static func locationLine(place: String, moment: String) -> String {
        "\(placeSegment(place: place)) · \(moment)"
    }

    /// The location line's place segment alone, resolved through
    /// the same model as the full line (GEN-4: the headline line
    /// renders its segments separately — the moment is the
    /// tappable entry — so the place resolution lives in exactly
    /// one place). GEN-3 X-13: the segment never renders empty;
    /// an empty/whitespace place falls back to the app's existing
    /// non-place label, the same one the view model uses when no
    /// place has resolved.
    static func placeSegment(place: String) -> String {
        let resolved = SimplePlace(rawDisplayName: place)?.displayName ?? place
        return resolved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Current Location"
            : resolved
    }

    static func locationLine(place: SimplePlace, moment: String) -> String {
        "\(place.displayName) · \(moment)"
    }

    /// "Actual 38°" — the formatted temperature (unit included) and
    /// nothing else.
    static func actualLine(formattedTemperature: String) -> String {
        "Actual \(formattedTemperature)"
    }
}

// MARK: - Location search candidates (GEN-3 / X-12)

/// One location-search result BEFORE the picker's row type is
/// built: the pure form of a geocoder placemark, so result
/// construction — including the dedupe below — is pinnable
/// without CoreLocation in the test target.
///
/// GEN-3 X-12 diagnosis (iOS): the picker searches ONE source
/// (CLGeocoder), so Android's dual-source merge does not exist
/// here — but result construction applied NO dedupe at all:
/// every placemark became a row under a fresh UUID, so a place
/// the geocoder returns twice renders as two identical rows.
/// The dedupe now happens HERE, at result construction, keyed
/// on place identity — never on the name string alone, so
/// distinct same-name places survive.
struct LocationSearchCandidate: Equatable {
    let name: String
    let subtitle: String
    /// The SimplePlace display form ("New York, NY").
    let displayName: String
    let latitude: Double
    let longitude: Double

    /// Place identity: the SimplePlace key (canonicalized, so a
    /// raw "New York, New York" and the simple "New York, NY"
    /// are the same place) PLUS the coordinate rounded to
    /// ~100 m (3 decimal places). Two rows are the same place
    /// only when BOTH agree: same-name places in different
    /// regions differ in the key; same-name places in the same
    /// region differ in the coordinate; a duplicated geocoder
    /// hit agrees on both and collapses.
    var identity: String {
        let placeKey = SimplePlace(rawDisplayName: displayName)?.key
            ?? displayName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let lat = Int((latitude * 1000).rounded())
        let lon = Int((longitude * 1000).rounded())
        return "\(placeKey)|\(lat)|\(lon)"
    }
}

enum LocationSearchResults {
    /// First occurrence wins; the geocoder's ordering (best
    /// match first) is preserved.
    static func deduplicated(_ candidates: [LocationSearchCandidate]) -> [LocationSearchCandidate] {
        var seen = Set<String>()
        return candidates.filter { seen.insert($0.identity).inserted }
    }
}

/// Which geocode completion may replace the results list
/// (GEN-3 / Y-15). Diagnosis, verified in the pre-fix picker:
/// a search fired on EVERY keystroke with no debounce and no
/// staleness check, and every completion replaced the whole
/// result set — whose rows carried fresh UUID identities, so
/// each replacement rebuilt every row view. A trailing response
/// (to "junea", say) landing between the user's touch-down and
/// touch-up on the "Juneau" row destroyed the row mid-touch and
/// the tap never fired: intermittent dead taps, worst right
/// after typing and under rapid repeats — Sol tapped Juneau's
/// row across four searches without a selection, while slower
/// searches (Miami, New Haven) eventually landed in a quiet
/// window and worked.
///
/// The gate is the data-layer half of the fix: it vends a
/// monotonic token per issued query, and a completion may apply
/// ONLY while it is the latest token for the current query.
/// Stale completions are dropped without touching the list, so
/// the rows on screen — and any touch in flight on them — are
/// never replaced by an older query's answer. (The view-layer
/// halves: a 250 ms debounce collapses the keystroke storm to
/// one request, and row identity is the place identity, so
/// even a legitimate refresh keeps the same row views.)
final class LocationSearchGate {
    private(set) var latestToken = 0
    private(set) var currentQuery = ""

    /// Register a newly issued query; returns its token. Every
    /// earlier token is stale from this moment.
    func begin(query: String) -> Int {
        currentQuery = query
        latestToken += 1
        return latestToken
    }

    /// The query was cleared (or the sheet reset): nothing in
    /// flight may apply anymore.
    func invalidate() {
        currentQuery = ""
        latestToken += 1
    }

    /// May this completion replace the results? Only the latest
    /// issued query's own completion, and only while that query
    /// is still the current one.
    func shouldApply(token: Int, query: String) -> Bool {
        token == latestToken && query == currentQuery && !query.isEmpty
    }
}

// MARK: - Morning Poster layout metrics (GEN-3 / X-11)

/// The Morning Poster's layout constants, consumed BY the view
/// itself (WearIveRunCollectionView's MorningPosterView), so the
/// geometry pinned here is the geometry that renders.
///
/// GEN-3 X-11 diagnosis (iOS): the poster exists at exactly ONE
/// size. It is composed at 390×520 and rendered once, full-size,
/// through ImageRenderer; the share sheet's preview is a raster
/// scale-down of that render. There is no thumbnail-size
/// re-layout of the view anywhere on iOS — the Android collision
/// mechanism (a live poster view recomposed at thumbnail size)
/// has no counterpart here. Within the composition, the lockup
/// band (top) and the body+foot band (bottom) are sequential
/// VStack bands separated by a Spacer, so they can meet only if
/// their heights sum past the canvas; the metrics below bound
/// that sum, at full size and — because every value scales
/// linearly — at thumbnail scale alike.
enum MorningPosterMetrics {
    static let canvasWidth: Double = 390
    /// GEN-5 (RW-29 FA-21): the settled canvas ratio is 4:5
    /// (frame 12) — build 25 rendered 390×520 (3:4). 390 × 488
    /// keeps the width and lands on 4:5.
    static let canvasHeight: Double = 488
    /// The top area scrim's height (gone before the runner's head).
    static let scrimHeight: Double = 150

    static let lockupTopPadding: Double = 20
    static let lockupSpacing: Double = 2
    static let brandFontSize: Double = 37
    static let hashtagFontSize: Double = 13

    static let bodySpacing: Double = 6
    static let bodyPadding: Double = 20
    static let feelsFontSize: Double = 20
    static let placeFontSize: Double = 14
    static let itemsFontSize: Double = 13

    static let footFontSize: Double = 24
    static let footHorizontalPadding: Double = 20
    static let footBottomPadding: Double = 18

    /// A deliberately generous line height (1.4× the point size)
    /// — it overestimates both the UI sans and the script face,
    /// so a no-collision verdict here holds on device.
    static func lineHeight(_ fontSize: Double) -> Double { fontSize * 1.4 }

    /// The top band: top padding + brand line + spacing + hashtag.
    static var lockupBandHeight: Double {
        lockupTopPadding
            + lineHeight(brandFontSize)
            + lockupSpacing
            + lineHeight(hashtagFontSize)
    }

    /// Wrapped-line estimate for one body line at `fontSize`,
    /// using a deliberately wide average glyph (0.58× the point
    /// size) so the estimate over-counts lines, never under.
    static func lineCount(forText text: String, fontSize: Double) -> Int {
        let contentWidth = canvasWidth - 2 * bodyPadding
        let glyphWidth = fontSize * 0.58
        let charsPerLine = max(1, Int(contentWidth / glyphWidth))
        return max(1, Int(ceil(Double(text.count) / Double(charsPerLine))))
    }

    /// The bottom band: the body block (padding, three text lines
    /// with their wrapping, padding) plus the foot row (its taller
    /// text line + bottom padding).
    static func bodyBandHeight(feelsText: String, placeText: String, itemsText: String) -> Double {
        let body = bodyPadding
            + lineHeight(feelsFontSize) * Double(lineCount(forText: feelsText, fontSize: feelsFontSize))
            + bodySpacing
            + lineHeight(placeFontSize) * Double(lineCount(forText: placeText, fontSize: placeFontSize))
            + bodySpacing
            + lineHeight(itemsFontSize) * Double(lineCount(forText: itemsText, fontSize: itemsFontSize))
            + bodyPadding
        let foot = lineHeight(footFontSize) + footBottomPadding
        return body + foot
    }

    /// True iff the lockup band and the body+foot band would meet
    /// on a canvas rendered at `scale` (1 = full size; 0.25 ≈ the
    /// share preview's thumbnail). Every term scales linearly, so
    /// the verdict is scale-invariant — which is exactly why the
    /// raster thumbnail cannot collide when the full size does not.
    static func collides(feelsText: String, placeText: String, itemsText: String, scale: Double = 1) -> Bool {
        (lockupBandHeight + bodyBandHeight(feelsText: feelsText, placeText: placeText, itemsText: itemsText)) * scale
            > canvasHeight * scale
    }
}

// MARK: - Scaffold / converging serve policy (MIM §8.4)

/// What the hero area shows for a personal cell, decided from the
/// two facts the client actually holds: whether a personal photo
/// is on screen, and the server's per-cell look state. Per §9 these
/// surfaces are SILENT — no upsell copy, no meter; the policy
/// returns presentation only.
enum PersonalServePresentation: Equatable {
    /// The exact look is on screen. Nothing extra renders.
    case exact
    /// A personal photo is on screen while the exact cell is still
    /// making: by the convergence architecture that photo is the
    /// user's OWN banked scaffold (exact prescription, nearest
    /// conditions). It shows immediately, with the honest badge.
    case scaffold(badge: String)
    /// No personal photo yet and the exact look is making: the
    /// honest wait (the existing G1/G2 progress copy).
    case making
    /// The cell failed: stock serves quietly on home; the
    /// explanation lives one tap away in the looks library.
    case failedQuiet(explanation: String)
    /// No personal layer for this cell.
    case none
}

enum PersonalServePolicy {
    /// The scaffold badge: factual, no promise of a time.
    static let scaffoldBadge = "This look was made for similar weather — your exact one is being made."

    /// The failed cell's library explanation (frame 21).
    static let failedExplanation = "This look couldn't be made, so the standard photo is shown here. Your other looks are unaffected."

    static func presentation(
        hasPersonalPhoto: Bool,
        lookState: PersonalLookState
    ) -> PersonalServePresentation {
        switch lookState {
        case .ready:
            return hasPersonalPhoto ? .exact : .none
        case .making:
            return hasPersonalPhoto ? .scaffold(badge: scaffoldBadge) : .making
        case .failed:
            return .failedQuiet(explanation: failedExplanation)
        case .none:
            return .none
        }
    }
}

// MARK: - The looks library (GEN-6 RW-61; frame 21 / §17)
//
// The designed "Your looks" library did not exist on iOS: the
// store's per-cell look states had no surface of their own, and
// the failed cell's explanation (PersonalServePolicy
// .failedExplanation) was computed but never rendered anywhere.
// This model is the library's content: one cell per known look
// state, titled from the cell's own key ({weather} · {bracket},
// then the time of day), with the gate-failed cell carrying the
// settled "stock photo for now" suffix on its time line.
/// One banked look as the SERVER holds it (fix wave
/// 2026-11-07, review §2 row 5): a delivered hero row read
/// fresh from the bank, independent of which cells this
/// process happened to resolve in-session.
struct BankedLook: Equatable, Identifiable {
    let combinationId: String
    let heroId: String?
    /// Signed URL for the stored photograph, when signing
    /// succeeded — the library tile and the RW-133 viewer
    /// render the stored frame itself, never a placeholder.
    let imageURL: String?

    var id: String { combinationId }
}

enum LooksLibrary {

    struct Cell: Equatable, Identifiable {
        let combinationId: String
        /// "{Weather} · {Bracket}" — frame 21's title line.
        let titleLine: String
        /// The time-of-day line; a gate-failed cell appends the
        /// settled suffix: "Night · stock photo for now".
        let statusLine: String
        var state: PersonalLookState
        /// The stored photograph's signed URL for a banked
        /// cell (fix wave: the library renders real photos for
        /// banked looks, and tapping one opens the RW-133
        /// full-screen viewer). Nil for session-only cells.
        var imageURL: String? = nil

        var id: String { combinationId }
        var isFailed: Bool { state.isFailed }
    }

    /// The settled failure suffix (frame 21 / design-spec §17).
    static let failedSuffix = "stock photo for now"

    static func cells(from lookStates: [String: PersonalLookState]) -> [Cell] {
        lookStates
            .compactMap { (combinationId, state) in
                guard state != .none else { return nil }
                // combinationId = {GENDER}_{WEATHER}_{BRACKET}_{TIME}_{hash}
                let parts = combinationId.split(separator: "_")
                guard parts.count >= 5,
                      let weather = HeroWeatherCondition(rawValue: String(parts[1])),
                      let bracket = HeroTempBracket(rawValue: String(parts[2])),
                      let time = HeroTimeOfDay(rawValue: String(parts[3]))
                else { return nil }
                let timeName = time.rawValue.capitalized
                return Cell(
                    combinationId: combinationId,
                    titleLine: "\(weather.rawValue.capitalized) · \(bracket.rawValue.capitalized)",
                    statusLine: state.isFailed ? "\(timeName) · \(failedSuffix)" : timeName,
                    state: state
                )
            }
            .sorted { $0.combinationId < $1.combinationId }
    }

    /// Fix wave 2026-11-07 (review §2 row 5): the library's
    /// content is the BANK AS THE SERVER HOLDS IT, re-read on
    /// open — not only the cells this session resolved. Banked
    /// looks render as ready cells carrying their stored
    /// photograph; session-folded making/failed states for
    /// cells the bank does not (yet) hold join them, so an
    /// in-flight or failed cell is still explained in place.
    static func cells(fromBank bank: [BankedLook], lookStates: [String: PersonalLookState]) -> [Cell] {
        var byId: [String: Cell] = [:]
        for cell in cells(from: lookStates) {
            byId[cell.combinationId] = cell
        }
        for look in bank {
            // A banked look is READY by the server's own record;
            // its cell is rebuilt from the ready state so a
            // session-folded failure for the same cell cannot
            // keep its "stock photo for now" suffix standing
            // over a delivered photograph.
            guard var resolved = cells(from: [look.combinationId: .ready]).first
            else { continue }
            resolved.imageURL = look.imageURL
            byId[look.combinationId] = resolved
        }
        return byId.values.sorted { $0.combinationId < $1.combinationId }
    }
}

// MARK: - Feedback routing (RW-18 + RW-1 residual)

/// The feedback channel contract, identical to Android's
/// (TelemetryConfig + TelemetryPayloads): human feedback POSTs to
/// the feedback endpoint under the five HUMAN categories; machine
/// traffic never files as human feedback — diagnostics and crash
/// reports carry their OWN category names and destinations.
enum FeedbackRouting {
    static let baseURL = "https://api.uptimeventuresllc.com/v1"
    static let appID = "runwear"
    static let platform = "ios"

    enum Destination: Equatable {
        case feedback
        case events
        case crash

        var urlString: String {
            switch self {
            case .feedback: return "\(FeedbackRouting.baseURL)/feedback/\(FeedbackRouting.appID)"
            case .events: return "\(FeedbackRouting.baseURL)/events/\(FeedbackRouting.appID)"
            case .crash: return "\(FeedbackRouting.baseURL)/crash/\(FeedbackRouting.appID)"
            }
        }

        var schema: String {
            switch self {
            case .feedback: return "runwear-feedback/1"
            case .events: return "runwear-events/1"
            case .crash: return "runwear-crash/1"
            }
        }
    }

    /// Human categories — Android's five ids, verbatim.
    static let humanCategories: [(id: String, label: String)] = [
        ("something_wrong", "Something's wrong"),
        ("recommendation_off", "The recommendation seems off"),
        ("confusing", "Confusing"),
        ("idea", "Idea"),
        ("other", "Other")
    ]

    /// Machine-traffic category names (RW-18 separation).
    static let diagnosticsCategory = "diagnostics"
    static let crashReportCategory = "crash_report"

    static func destination(forMachineCategory category: String) -> Destination? {
        switch category {
        case diagnosticsCategory: return .events
        case crashReportCategory: return .crash
        default: return nil
        }
    }

    static func isHumanCategory(_ id: String) -> Bool {
        humanCategories.contains { $0.id == id }
    }
}

/// Pure payload builders mirroring Android's TelemetryPayloads
/// shapes (the Worker rejects unknown fields, so the shapes are
/// exact): envelope + category + text + replyEmail? + entryPoint?
/// + bundle of derived display values.
enum FeedbackPayloads {
    static func feedbackData(
        appVersion: String,
        installId: String,
        categoryId: String,
        text: String,
        replyEmail: String?,
        entryPoint: String?,
        bundle: [String: String]
    ) -> Data? {
        var payload: [String: Any] = [
            "schema": FeedbackRouting.Destination.feedback.schema,
            "app": FeedbackRouting.appID,
            "platform": FeedbackRouting.platform,
            "appVersion": appVersion,
            "installId": installId,
            "category": categoryId,
            "text": text,
            "bundle": bundle
        ]
        if let replyEmail, !replyEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["replyEmail"] = replyEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let entryPoint {
            payload["entryPoint"] = entryPoint
        }
        return try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
    }

    /// The what's-sent disclosure (frame 48): the derived fields
    /// that ride with a report, as labeled lines the user reads
    /// BEFORE sending. No raw coordinates — the city label is the
    /// finest location included (Android's bundle rule).
    static func disclosureLines(bundle: [String: String]) -> [String] {
        bundle
            .sorted { $0.key < $1.key }
            .map { "\($0.key): \($0.value)" }
    }
}

// MARK: - Guided-selfie checks (MIM §8.4)

/// The live checks the guided capture coaches against, evaluated
/// from Vision measurements. The SAME evaluation judges an uploaded
/// photo after selection (identical checks, both doors).
enum SelfieCheck: String, CaseIterable, Equatable {
    case faceFound
    case faceLargeEnough
    case faceFrontal
    case wellLit

    var passedLabel: String {
        switch self {
        case .faceFound: return "In frame"
        case .faceLargeEnough: return "Face size"
        case .faceFrontal: return "Straight on"
        case .wellLit: return "Light"
        }
    }

    /// W-20 (GEN-2): the capture chips' row layout — two rows of
    /// two. A single row of four forced every chip into a quarter
    /// of the screen width, and three of the four labels truncated
    /// mid-word at default type ("Face fills…", "Facing th…",
    /// "Light loo…"). Two per row, each chip sizes to its full
    /// label. Order is the checks' coaching order, row-major.
    static var captureChipRows: [[SelfieCheck]] {
        [[.faceFound, .faceLargeEnough], [.faceFrontal, .wellLit]]
    }

    /// The one coaching line for this check's failure.
    var coachingLine: String {
        switch self {
        case .faceFound: return "Center your face in the oval."
        case .faceLargeEnough: return "Move a little closer — your face should fill the oval."
        case .faceFrontal: return "Look straight at the camera."
        case .wellLit: return "Face a window or a brighter light."
        }
    }
}

struct SelfieEvaluation: Equatable {
    let results: [SelfieCheck: Bool]
    var allPassed: Bool { SelfieCheck.allCases.allSatisfy { results[$0] == true } }
    /// The first failing check in coaching order — its line is the
    /// one coaching line the capture shows.
    var firstFailure: SelfieCheck? {
        SelfieCheck.allCases.first { results[$0] != true }
    }
    var coachingLine: String? { firstFailure?.coachingLine }
}

enum SelfieGuidance {
    /// Face area share the guide asks for: large enough to read,
    /// with margin around it (and never past the intake's
    /// too-close line, which PhotoQualityVerdict also enforces).
    static let minimumFaceAreaRatio: Double = 0.08
    /// |yaw| beyond this is not "frontal".
    static let maximumYawDegrees: Double = 15
    /// The intake's own darkness floor (PhotoQualityVerdict).
    static let minimumLuminance: Double = 0.18

    static func evaluate(
        faceCount: Int,
        largestFaceAreaRatio: Double,
        yawDegrees: Double,
        meanLuminance: Double
    ) -> SelfieEvaluation {
        SelfieEvaluation(results: [
            .faceFound: faceCount == 1,
            .faceLargeEnough: faceCount == 1 && largestFaceAreaRatio >= minimumFaceAreaRatio,
            .faceFrontal: faceCount == 1 && abs(yawDegrees) <= maximumYawDegrees,
            .wellLit: meanLuminance >= minimumLuminance
        ])
    }

    // MARK: Retake strings (C3 pattern)
    //
    // The MIM record names C3E/C3F retake strings for the upload
    // door; no coded counterpart exists in either repo (checked
    // 2026-11-02), so these are authored to the C3 pattern:
    // failure-specific, and the camera is offered first.
    /// C3E — the uploaded photo's face is too small. GEN-6
    /// (RW-51): the settled §10.4 wording, identical to
    /// PhotoRejection.faceTooSmall.specString.
    static let retakeFaceTooSmall = "Your face is small in that photo — A closer selfie works best — a mirror selfie is great too. Or let the camera guide you…"
    /// C3F — the uploaded photo is not frontal.
    static let retakeNotFrontal = "This photo isn't facing the camera. Take a selfie with the camera instead — face it straight on."
}
