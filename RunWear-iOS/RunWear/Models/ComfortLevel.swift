import Foundation

/// User's body temperature comfort preference
/// Negative values = runs cold, positive values = runs warm.
///
/// Five levels, mirroring Android's ComfortPreference exactly
/// (adjustments -10/-5/0/+5/+10). The iOS model previously had
/// seven, with intermediate ±3 stops Android and the PWA never
/// offered — the same user picked different effective adjustments
/// per platform. Persisted ±3 values migrate to the nearest
/// surviving level (see fromStoredRawValue).
enum ComfortLevel: Int, Codable, CaseIterable {
    case veryCold = -10
    case cold = -5
    case neutral = 0
    case warm = 5
    case veryWarm = 10

    /// Short display label (Android ComfortPreference.shortLabel)
    var label: String {
        switch self {
        case .veryCold: return "Run Cold"
        case .cold: return "Slightly Cold"
        case .neutral: return "Neutral"
        case .warm: return "Slightly Hot"
        case .veryWarm: return "Run Hot"
        }
    }

    /// Full description for UI (Android ComfortPreference.label)
    var shortLabel: String {
        switch self {
        case .veryCold: return "I get cold very easily"
        case .cold: return "I tend to get cold"
        case .neutral: return "Neither"
        case .warm: return "I tend to overheat"
        case .veryWarm: return "I overheat very easily"
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

    /// Decodes a persisted raw value, migrating the retired 7-level
    /// model's intermediate stops: a stored -3 becomes .cold (-5)
    /// and +3 becomes .warm (+5) — the nearest surviving levels.
    /// Without the migration those users' raw values no longer
    /// decode and they would silently reset to neutral.
    static func fromStoredRawValue(_ raw: Int) -> ComfortLevel? {
        switch raw {
        case -3: return .cold
        case 3: return .warm
        default: return ComfortLevel(rawValue: raw)
        }
    }

    /// Returns all cases sorted by raw value for slider display
    static var sortedCases: [ComfortLevel] {
        allCases.sorted { $0.rawValue < $1.rawValue }
    }

    /// Index in sorted cases (for slider)
    var sliderIndex: Int {
        ComfortLevel.sortedCases.firstIndex(of: self) ?? 2
    }

    /// Creates from slider index
    static func from(sliderIndex index: Int) -> ComfortLevel {
        let cases = sortedCases
        guard index >= 0 && index < cases.count else { return .neutral }
        return cases[index]
    }
}
