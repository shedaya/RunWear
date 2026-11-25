import Foundation

// MARK: - Widget last-good (GEN-5, Addendum 7 — RW-10)
//
// The iOS counterpart of Android's WidgetLastGoodStore pattern:
// the app persists its last successfully loaded NOW-frame
// recommendation (weather + outfit + place + when it was saved)
// into the shared App Group, and the WidgetKit extension renders
// THAT record — so a widget whose app has not run recently keeps
// showing the last real outfit with an honest age instead of an
// error or a blank. A failed load never overwrites the record;
// the freshness model below states the age rather than implying
// liveness (the settled glance rules: a genuine freshness label
// that actually renders; stale data presented as stale).
//
// This file is membered into THREE targets: the app (writer),
// the widget extension (reader), and the tests (pins). It must
// stay free of app-only dependencies (no engine, no SwiftUI).

/// The persisted record. All temperatures are Fahrenheit (the
/// app's canonical frame); the unit preference at save time is
/// carried so the widget formats exactly as the app would have.
struct WidgetLastGood: Codable, Equatable {
    var feelsLikeF: Double
    var temperatureF: Double
    var conditionText: String
    /// SF Symbol for the condition (WeatherCondition.icon).
    var conditionIcon: String
    var windMph: Double
    var humidityPercent: Int
    var uvIndex: Double
    var locationName: String
    /// The hero garment (top base layer) — bold at every size.
    var topBaseName: String
    /// Mid + outer layers, in wearing order.
    var layerNames: [String]
    var bottomName: String
    /// Head, hands, and accessories — the "+N more" family at
    /// small sizes, named at larger ones.
    var secondaryNames: [String]
    /// The settled later-today line (Android's LaterTodayLine
    /// forms), computed by the app at write time; nil when the
    /// outfit is stable and no line was composed.
    var laterTodayLine: String?
    var unitIsCelsius: Bool
    /// When the record was saved (device wall clock).
    var savedAt: Date

    /// The app's own temperature format (TemperatureUnit.format):
    /// rounded, with the unit symbol.
    func formatTemperature(fahrenheit: Double) -> String {
        let value = unitIsCelsius ? (fahrenheit - 32) * 5 / 9 : fahrenheit
        return "\(Int(round(value)))\(unitIsCelsius ? "°C" : "°F")"
    }

    /// The supporting line (settled widget rule): actual
    /// temperature, wind, and condition alongside the feels-like
    /// hero. Wind is mph, as everywhere in the app.
    var supportingLine: String {
        AppEnglish.widgetSupportingLine(formatTemperature(fahrenheit: temperatureF), Int(round(windMph)), conditionText)
    }

    /// Every garment name in wearing order (for the larger
    /// sizes' full stack).
    var allGarmentNames: [String] {
        [topBaseName] + layerNames + [bottomName] + secondaryNames
    }
}

/// Pure freshness reckoning — a direct port of Android's
/// WidgetFreshness (thresholds and label forms identical, so the
/// two platforms' widgets age their data the same way). Every
/// function takes `now` explicitly so it is unit-testable.
enum WidgetFreshness {
    /// Beyond the ~60-minute target cadence, data is stale.
    static let staleThreshold: TimeInterval = 60 * 60
    /// The Large brand strip switches from a clock time to an
    /// age form past this.
    static let largeAgeFormThreshold: TimeInterval = 90 * 60

    static func age(of record: WidgetLastGood, now: Date) -> TimeInterval {
        max(0, now.timeIntervalSince(record.savedAt))
    }

    static func isStale(_ record: WidgetLastGood, now: Date) -> Bool {
        age(of: record, now: now) > staleThreshold
    }

    /// Compact uppercase age: "JUST NOW", "25M AGO", "2H AGO",
    /// "3D AGO".
    static func ageShortUpper(_ age: TimeInterval) -> String {
        let minutes = Int(age) / 60
        switch minutes {
        case ..<1: return AppEnglish.widgetAgeJustNowUpper
        case ..<60: return AppEnglish.widgetAgeMinUpper(minutes)
        case ..<(60 * 24): return AppEnglish.widgetAgeHrUpper(minutes / 60)
        default: return AppEnglish.widgetAgeDayUpper(minutes / (60 * 24))
        }
    }

    /// Sentence-case age: "just now", "25 min ago", "3h ago",
    /// "2d ago".
    static func ageWords(_ age: TimeInterval) -> String {
        let minutes = Int(age) / 60
        switch minutes {
        case ..<1: return AppEnglish.widgetAgeJustNowLower
        case ..<60: return AppEnglish.widgetAgeMinLower(minutes)
        case ..<(60 * 24): return AppEnglish.widgetAgeHrLower(minutes / 60)
        default: return AppEnglish.widgetAgeDayLower(minutes / (60 * 24))
        }
    }

    private static func clockTime(of date: Date) -> String {
        LocaleFormats.timeHourMinute(date)
    }

    /// Small/Medium eyebrow: the data's save moment is ALWAYS
    /// stated — a clock time while fresh, the age once stale —
    /// so "FOR NOW" never quietly presents an old record as
    /// live (Android 1.1.8 rule).
    static func eyebrow(_ record: WidgetLastGood, now: Date) -> String {
        if isStale(record, now: now) {
            return AppEnglish.widgetForNowAge(ageShortUpper(age(of: record, now: now)))
        }
        return AppEnglish.widgetForNowAge(clockTime(of: record.savedAt))
    }

    /// Large brand-strip freshness: "Updated h:mm a" while
    /// recent, "Updated 3h ago" once old.
    static func updatedLabel(_ record: WidgetLastGood, now: Date) -> String {
        let age = age(of: record, now: now)
        if age < largeAgeFormThreshold {
            return AppEnglish.widgetUpdatedAge(clockTime(of: record.savedAt))
        }
        return AppEnglish.widgetUpdatedAge(ageWords(age))
    }
}

/// Plain-words failure categories for the never-succeeded state
/// (Android's WidgetErrorCategory, verbatim): the user reads
/// WHY, not an exception message. The app records the category
/// of its most recent failed load; the widget shows the error
/// card ONLY when no last-good record exists at all — with a
/// record, failures are invisible and the age speaks instead.
enum WidgetErrorCategory: String, Codable {
    case location
    case weather

    var headline: String { AppEnglish.widgetCouldntLoad }

    var reason: String {
        switch self {
        case .location: return AppEnglish.widgetLocationUnavailable
        case .weather: return AppEnglish.widgetCouldntReach
        }
    }

    /// The fresh-install form (the app has never run, so no
    /// category was ever recorded): same headline family, the
    /// honest reason.
    static let neverRunReason = AppEnglish.widgetOpenRunwear
}

/// The App Group store. Reads are total: a missing or corrupt
/// payload reads as "no record", never a crash (Android's
/// fromEntries rule).
enum WidgetLastGoodStore {
    static let appGroupIdentifier = "group.com.uptimeventures.runwear"
    static let recordKey = "widgetLastGood.v1"
    static let errorCategoryKey = "widgetErrorCategory.v1"

    static func defaults() -> UserDefaults? {
        UserDefaults(suiteName: appGroupIdentifier)
    }

    static func save(_ record: WidgetLastGood, to defaults: UserDefaults? = defaults()) {
        guard let defaults, let data = try? JSONEncoder().encode(record) else { return }
        defaults.set(data, forKey: recordKey)
    }

    static func load(from defaults: UserDefaults? = defaults()) -> WidgetLastGood? {
        guard let defaults, let data = defaults.data(forKey: recordKey) else { return nil }
        return try? JSONDecoder().decode(WidgetLastGood.self, from: data)
    }

    static func saveErrorCategory(_ category: WidgetErrorCategory, to defaults: UserDefaults? = defaults()) {
        defaults?.set(category.rawValue, forKey: errorCategoryKey)
    }

    static func loadErrorCategory(from defaults: UserDefaults? = defaults()) -> WidgetErrorCategory? {
        defaults?.string(forKey: errorCategoryKey).flatMap(WidgetErrorCategory.init(rawValue:))
    }

    static func clearErrorCategory(in defaults: UserDefaults? = defaults()) {
        defaults?.removeObject(forKey: errorCategoryKey)
    }
}
