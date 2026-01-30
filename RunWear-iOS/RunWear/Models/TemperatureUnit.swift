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
        case .fahrenheit: return "Fahrenheit"
        case .celsius: return "Celsius"
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
}
