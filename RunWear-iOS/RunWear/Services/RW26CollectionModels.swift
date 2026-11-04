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

// MARK: - "Wear I've Run" entry

/// One day's settled outfit at one place. The saved entry is the
/// day's LAST settled version; entries are created automatically at
/// the day's first generation. There is NO backfill — the store
/// only ever holds days the app actually generated on.
struct WearIveRunEntry: Codable, Equatable, Hashable, Identifiable {
    /// Start-of-day (local) for the entry's day.
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

    /// The narration's real payload for an update (WS-4): the
    /// entry's first kit item — the design's pattern, and the
    /// field Android's holder computed and never used (D-08).
    /// Falls back to the condition when the entry carries no
    /// items (a recorded entry always carries its kit).
    var narrationPayload: String {
        itemNames.first ?? condition
    }

    /// Place identity: the SimplePlace key — trimmed, case-folded,
    /// and canonicalized, so "New York, New York" (legacy raw)
    /// and "New York, NY" (simple) share one identity.
    var placeKey: String {
        simplePlace?.key
            ?? placeName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// One entry per day per place: the identity is day + place.
    var id: String { "\(Int(dayStart.timeIntervalSince1970))|\(placeKey)" }
}

// MARK: - Weather filter

/// The collection's weather filter (frames 7–8, 52). An entry's
/// categories are computed from the same one-frame values the home
/// hero carries; an entry can belong to several categories (a 30°
/// snow day is both Cold and Snow) and matches a filter when the
/// filter is in its set.
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
    /// Days in the stay, newest-first.
    let entries: [WearIveRunEntry]
    var dayCount: Int { entries.count }
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

    /// The cover is the most recent card.
    var cover: WearIveRunEntry { entries.first! }
    var dayCount: Int { entries.count }
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
    var dayCount: Int { entries.count }
    var feelsLikeRangeF: (min: Double, max: Double) {
        let values = entries.map(\.feelsLikeF)
        return (values.min()!, values.max()!)
    }
}

enum WearIveRunCollection {
    /// Upsert by (day, place): the day's last settled version wins.
    static func upserting(_ entries: [WearIveRunEntry], _ entry: WearIveRunEntry) -> [WearIveRunEntry] {
        var result = entries.filter { $0.id != entry.id }
        result.append(entry)
        return result.sorted { $0.dayStart > $1.dayStart }
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

    /// Chapters for a filter: entries are filtered FIRST, then
    /// grouped — covers, counts, spans, and ranges all recompute
    /// from the admitted entries. Chapters order by their cover's
    /// recency (newest cover first); empty chapters never appear.
    static func chapters(
        from entries: [WearIveRunEntry],
        filter: WearIveRunWeatherFilter,
        calendar: Calendar = .current
    ) -> [WearIveRunChapter] {
        let admitted = entries.filter { WearIveRunWeatherClassifier.matches($0, filter: filter) }
        let grouped = Dictionary(grouping: admitted, by: \.placeKey)
        var chapters: [WearIveRunChapter] = grouped.values.compactMap { placeEntries in
            let sorted = placeEntries.sorted { $0.dayStart > $1.dayStart }
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
        chapters.sort { $0.cover.dayStart > $1.cover.dayStart }
        return chapters
    }

    /// Consecutive-day runs within one place's newest-first
    /// entries. Two entries a calendar day apart continue a stay;
    /// anything wider starts a new one.
    static func stays(from newestFirst: [WearIveRunEntry], calendar: Calendar = .current) -> [WearIveRunStay] {
        var result: [WearIveRunStay] = []
        var current: [WearIveRunEntry] = []
        for entry in newestFirst {
            if let previous = current.last,
               let gap = calendar.dateComponents(
                   [.day],
                   from: calendar.startOfDay(for: entry.dayStart),
                   to: calendar.startOfDay(for: previous.dayStart)
               ).day, gap == 1 {
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
                entries: monthEntries.sorted { $0.dayStart > $1.dayStart }
            )
        }
        groups.sort { ($0.year, $0.month) > ($1.year, $1.month) }
        return groups
    }

    /// Today's entries (any place), newest first — the collection
    /// pins today above the chapters.
    static func todaysEntries(from entries: [WearIveRunEntry], today: Date, calendar: Calendar = .current) -> [WearIveRunEntry] {
        entries
            .filter { calendar.isDate($0.dayStart, inSameDayAs: today) }
            .sorted { $0.dayStart > $1.dayStart }
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

    /// "Mar 11" style single-day label.
    static func dayLabel(_ date: Date, calendar: Calendar = .current) -> String {
        formatter("MMM d", calendar: calendar).string(from: date)
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
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([WearIveRunEntry].self, from: data) else {
            return []
        }
        return decoded
    }

    /// Record a settled entry (automatic at the day's first
    /// generation; later settles the same day replace it).
    func record(_ entry: WearIveRunEntry) {
        save(WearIveRunCollection.upserting(entries, entry))
    }

    /// The mini bar's trash: remove today's entry at a place.
    func remove(dayStart: Date, placeName: String) {
        save(WearIveRunCollection.removing(entries, dayStart: dayStart, placeName: placeName))
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
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([RecentLocation].self, from: data) else {
            return []
        }
        return decoded
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
        "\(SimplePlace(rawDisplayName: place)?.displayName ?? place) · \(moment)"
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
        case .faceFound: return "Face found"
        case .faceLargeEnough: return "Face fills the guide"
        case .faceFrontal: return "Facing the camera"
        case .wellLit: return "Light looks good"
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
    /// C3E — the uploaded photo's face is too small.
    static let retakeFaceTooSmall = "Your face is too small in this photo to use. Take a selfie with the camera instead — the guide helps you frame it."
    /// C3F — the uploaded photo is not frontal.
    static let retakeNotFrontal = "This photo isn't facing the camera. Take a selfie with the camera instead — face it straight on."
}
