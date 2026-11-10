import Foundation

/// WP-3 DESIGN §7 (iOS half): unit defaults derive from the locale;
/// a stored user choice always wins — the preference stores read
/// UserDefaults first and fall back to these defaults only when no
/// choice has ever been stored. (WP-R3: the watch app previously
/// hard-coded .fahrenheit as its no-choice default.)
enum LocaleUnits {
    /// Temperature: °F in imperial measurement locales (US, LR, MM),
    /// °C elsewhere — the rule `TemperatureUnit.defaultForLocale`
    /// encodes via `Locale.measurementSystem`.
    static var defaultTemperatureUnit: TemperatureUnit {
        TemperatureUnit.defaultForLocale
    }

    /// Whether the locale measures in imperial units (the §7 default
    /// rule for wind — mph — and precipitation — inches).
    static func usesImperialMeasurements(for locale: Locale = .current) -> Bool {
        locale.measurementSystem == .us
    }
}
