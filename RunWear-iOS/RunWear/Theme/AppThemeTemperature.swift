import SwiftUI

/// The temperature-bracket surface of AppTheme, split out of
/// AppTheme.swift in GEN-5: AppTheme itself is membered into the
/// widget extension target, which does not link the shared
/// engine framework that HeroTempBracket's mapping lives in —
/// the bracket-typed helpers therefore live here, membered into
/// the app, watch, and test targets only.
extension AppTheme {
    /// The pinned hex for a bracket (test + diagnostics surface).
    static func temperatureHex(for bracket: HeroTempBracket) -> String {
        switch bracket {
        case .FREEZING: return tempFreezingHex
        case .COLD: return tempColdHex
        case .COOL: return tempCoolHex
        case .MILD: return tempMildHex
        case .WARM: return tempWarmHex
        case .HOT: return tempHotHex
        }
    }

    /// Returns the appropriate color for a temperature bracket
    static func temperatureColor(for bracket: HeroTempBracket) -> Color {
        switch bracket {
        case .FREEZING: return tempFreezing
        case .COLD: return tempCold
        case .COOL: return tempCool
        case .MILD: return tempMild
        case .WARM: return tempWarm
        case .HOT: return tempHot
        }
    }

    /// Returns the appropriate color for a temperature in Fahrenheit
    static func temperatureColor(fahrenheit: Double) -> Color {
        let bracket = HeroTempBracket.from(feelsLikeTemperature: fahrenheit)
        return temperatureColor(for: bracket)
    }

    /// Creates a temperature-tinted overlay
    static func temperatureTintOverlay(for bracket: HeroTempBracket, opacity: Double = 0.12) -> Color {
        temperatureColor(for: bracket).opacity(opacity)
    }
}
