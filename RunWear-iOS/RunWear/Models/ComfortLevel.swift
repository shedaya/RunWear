import Foundation

/// User's body temperature comfort preference
/// Negative values = runs cold, positive values = runs warm
enum ComfortLevel: Int, Codable, CaseIterable {
    case veryCold = -10
    case cold = -5
    case slightlyCold = -3
    case neutral = 0
    case slightlyWarm = 3
    case warm = 5
    case veryWarm = 10

    /// Display label for the comfort level
    var label: String {
        switch self {
        case .veryCold: return "Very Cold"
        case .cold: return "Cold"
        case .slightlyCold: return "Slightly Cold"
        case .neutral: return "Neutral"
        case .slightlyWarm: return "Slightly Warm"
        case .warm: return "Warm"
        case .veryWarm: return "Very Warm"
        }
    }

    /// Short description for UI
    var shortLabel: String {
        switch self {
        case .veryCold: return "I run very cold"
        case .cold: return "I run cold"
        case .slightlyCold: return "I run slightly cold"
        case .neutral: return "I'm neutral"
        case .slightlyWarm: return "I run slightly warm"
        case .warm: return "I run warm"
        case .veryWarm: return "I run very warm"
        }
    }

    /// Temperature adjustment in Fahrenheit
    /// Positive = feels warmer than actual, negative = feels colder
    var temperatureAdjustment: Double {
        Double(self.rawValue)
    }

    /// Adjusts the "feels like" temperature based on comfort level
    /// - Parameter feelsLike: Original feels like temperature in Fahrenheit
    /// - Returns: Adjusted temperature accounting for personal comfort
    func adjustTemperature(_ feelsLike: Double) -> Double {
        // If user runs warm (+10), they feel 10° warmer than actual
        // If user runs cold (-10), they feel 10° colder than actual
        return feelsLike + temperatureAdjustment
    }

    /// Creates a ComfortLevel from a slider value (-10 to +10)
    /// - Parameter value: Slider value
    /// - Returns: Nearest ComfortLevel
    static func from(sliderValue value: Int) -> ComfortLevel {
        let clamped = max(-10, min(10, value))

        // Find the closest matching case
        return allCases.min(by: { abs($0.rawValue - clamped) < abs($1.rawValue - clamped) }) ?? .neutral
    }

    /// Returns all cases sorted by raw value for slider display
    static var sortedCases: [ComfortLevel] {
        allCases.sorted { $0.rawValue < $1.rawValue }
    }

    /// Index in sorted cases (for slider)
    var sliderIndex: Int {
        ComfortLevel.sortedCases.firstIndex(of: self) ?? 3
    }

    /// Creates from slider index
    static func from(sliderIndex index: Int) -> ComfortLevel {
        let cases = sortedCases
        guard index >= 0 && index < cases.count else { return .neutral }
        return cases[index]
    }
}
