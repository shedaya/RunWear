import Foundation

/// Temperature unit preference for display
enum TemperatureUnit: String, Codable, CaseIterable {
    case fahrenheit
    case celsius

    /// Display symbol for the unit
    var symbol: String {
        switch self {
        case .fahrenheit: return "°F"
        case .celsius: return "°C"
        }
    }

    /// Short display label
    var label: String {
        switch self {
        case .fahrenheit: return AppStrings.unitFahrenheitLabel
        case .celsius: return AppStrings.unitCelsiusLabel
        }
    }

    /// Short label for compact display
    var shortLabel: String {
        switch self {
        case .fahrenheit: return "°F"
        case .celsius: return "°C"
        }
    }

    /// Converts temperature to this unit from Fahrenheit
    /// - Parameter fahrenheit: Temperature in Fahrenheit
    /// - Returns: Temperature in this unit
    func convert(fromFahrenheit fahrenheit: Double) -> Double {
        switch self {
        case .fahrenheit:
            return fahrenheit
        case .celsius:
            return (fahrenheit - 32) * 5 / 9
        }
    }

    /// Converts temperature to this unit from Celsius
    /// - Parameter celsius: Temperature in Celsius
    /// - Returns: Temperature in this unit
    func convert(fromCelsius celsius: Double) -> Double {
        switch self {
        case .fahrenheit:
            return celsius * 9 / 5 + 32
        case .celsius:
            return celsius
        }
    }

    /// Formats temperature with unit symbol
    /// - Parameter value: Temperature value in this unit
    /// - Returns: Formatted string (e.g., "72°F")
    func format(_ value: Double) -> String {
        "\(Int(round(value)))\(symbol)"
    }

    /// RW-144 (parent ruling, Nov 8, 2026): the FIRST SCREEN's
    /// hero feels-like numeral and "Actual" line carry the BARE
    /// degree — "58°" / "Actual 60°", no unit letter — exactly as
    /// the approved v8 design frames show and as Android's
    /// settled HeroSheetFormat renders. The value still converts
    /// to the user's unit; only the letter is dropped, and ONLY
    /// for those two first-screen readings — every other surface
    /// keeps `format`'s one-unit story.
    func formatBareDegree(_ value: Double) -> String {
        "\(Int(round(value)))°"
    }

    /// `formatBareDegree` fed from a Fahrenheit reading.
    func formatBareDegreeFromFahrenheit(_ fahrenheit: Double) -> String {
        formatBareDegree(convert(fromFahrenheit: fahrenheit))
    }

    /// Formats temperature from Fahrenheit with unit symbol
    /// - Parameter fahrenheit: Temperature in Fahrenheit
    /// - Returns: Formatted string in this unit
    func formatFromFahrenheit(_ fahrenheit: Double) -> String {
        format(convert(fromFahrenheit: fahrenheit))
    }

    /// Formats temperature from Celsius with unit symbol
    /// - Parameter celsius: Temperature in Celsius
    /// - Returns: Formatted string in this unit
    func formatFromCelsius(_ celsius: Double) -> String {
        format(convert(fromCelsius: celsius))
    }

    // MARK: - Locale Default (RW-6.8)

    /// The unit a user in the given measurement system expects: metric
    /// locales default to Celsius, everyone else to Fahrenheit. Onboarding
    /// and first-launch defaults come from here — never a hard-coded unit.
    static func defaultUnit(for measurementSystem: Locale.MeasurementSystem) -> TemperatureUnit {
        measurementSystem == .metric ? .celsius : .fahrenheit
    }

    /// The locale default for the current device locale.
    static var defaultForLocale: TemperatureUnit {
        defaultUnit(for: Locale.current.measurementSystem)
    }
}
