import SwiftUI

enum AppTheme {
    // MARK: - Primary Colors
    static let primaryColor = Color(hex: "00796B")
    static let primaryLight = Color(hex: "48A999")
    static let primaryDark = Color(hex: "004C40")

    // MARK: - Background Colors
    static let backgroundColor = Color(hex: "F5F5F5")
    static let darkBackground = Color(hex: "0A0A0A")
    static let cardBackground = Color.white
    static let darkCardBackground = Color(hex: "1A1A1A")

    // MARK: - Text Colors
    static let textPrimary = Color(hex: "212121")
    static let textSecondary = Color(hex: "757575")
    static let textOnDark = Color.white
    static let textSecondaryOnDark = Color.white.opacity(0.7)

    // MARK: - Temperature Colors
    static let tempFreezing = Color(hex: "9C27B0")  // Purple
    static let tempCold = Color(hex: "3F51B5")      // Indigo
    static let tempCool = Color(hex: "2196F3")      // Blue
    static let tempMild = Color(hex: "4CAF50")      // Green
    static let tempWarm = Color(hex: "FF9800")      // Orange
    static let tempHot = Color(hex: "F44336")       // Red

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

    // MARK: - Glass Morphism
    static let glassBackground = Color.white.opacity(0.1)
    static let glassBorder = Color.white.opacity(0.15)
    static let glassBackgroundDark = Color.black.opacity(0.3)

    // MARK: - Overlay Gradients

    /// Creates a 6-stop gradient for hero image text readability
    static func heroOverlayGradient() -> LinearGradient {
        LinearGradient(
            stops: [
                .init(color: .black.opacity(0.4), location: 0),
                .init(color: .black.opacity(0.1), location: 0.3),
                .init(color: .clear, location: 0.5),
                .init(color: .clear, location: 0.6),
                .init(color: .black.opacity(0.2), location: 0.8),
                .init(color: .black.opacity(0.7), location: 1.0)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    /// Creates a temperature-tinted overlay
    static func temperatureTintOverlay(for bracket: HeroTempBracket, opacity: Double = 0.12) -> Color {
        temperatureColor(for: bracket).opacity(opacity)
    }

    // MARK: - Animation Durations
    static let crossfadeDuration: Double = 0.5
    static let staggerDuration: Double = 0.4
    static let staggerDelay: Double = 0.05
    static let modalTransitionDuration: Double = 0.3

    // MARK: - Sizing
    static let heroHeightRatio: CGFloat = 0.75
    static let pillCornerRadius: CGFloat = 16
    static let cardCornerRadius: CGFloat = 12
    static let modalCornerRadius: CGFloat = 24
    static let minTouchTarget: CGFloat = 44
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
