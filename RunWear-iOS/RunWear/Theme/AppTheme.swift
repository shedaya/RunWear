import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum AppTheme {
    // MARK: - Primary Colors
    // RW-25: pine replaces the legacy teal.
    static let primaryColor = Color(hex: "175E4C")
    static let primaryLight = Color(hex: "2E7D64")
    static let primaryDark = Color(hex: "0E3D30")

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
    //
    // RW-25: the Android set, replacing the legacy hexes in place so
    // every consumer of temperatureColor(for:)/(fahrenheit:) — the
    // badge, the hero tint, the watch (which shares this file) —
    // inherits the unified set. The hex strings are the pins the
    // unit tests assert.
    static let tempFreezingHex = "8B5CF6"
    static let tempColdHex = "6366F1"
    static let tempCoolHex = "3B82F6"
    static let tempMildHex = "10B981"
    static let tempWarmHex = "F59E0B"
    static let tempHotHex = "F97316"
    static let tempFreezing = Color(hex: tempFreezingHex)
    static let tempCold = Color(hex: tempColdHex)
    static let tempCool = Color(hex: tempCoolHex)
    static let tempMild = Color(hex: tempMildHex)
    static let tempWarm = Color(hex: tempWarmHex)
    static let tempHot = Color(hex: tempHotHex)

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

    // MARK: - Content Card Tokens (RW-6.11)
    //
    // The single source of truth for flat content-card fills on the dark
    // design. Content cards are deliberately NOT Liquid Glass — glass is
    // reserved for floating/navigation surfaces (see GlassMorphismModifiers);
    // dense text stays on these flat fills for legibility.
    static let cardFillOnDark = Color.white.opacity(0.05)
    static let cardStrokeOnDark = Color.white.opacity(0.1)

    // MARK: - Dynamic Theme Pair (RW-25)
    //
    // Trait-dynamic tokens for the appearance pair. Light values are
    // the dormant light set, activated; dark values are the shipped
    // dark design. On watchOS (always dark, and UIColor dynamic
    // providers are unavailable) the tokens resolve to the dark side.
    static let background = dynamicColor(lightHex: "F5F5F5", darkHex: "0A0A0A")
    static let sheet = dynamicColor(lightHex: "FFFFFF", darkHex: "1A1A1A")
    static let cardOnSheet = dynamicColor(lightHex: "F5F5F5", darkHex: "262626")
    static let inkPrimary = dynamicColor(lightHex: "17191C", darkHex: "FFFFFF")
    static let inkSecondary = dynamicColor(
        light: UIColor(hexRGB: "5A6068", alpha: 1),
        dark: UIColor(hexRGB: "FFFFFF", alpha: 0.7)
    )
    static let inkMuted = dynamicColor(lightHex: "6B7280", darkHex: "9A9A9A")
    /// Dynamic successor to cardFillOnDark/cardStrokeOnDark for the
    /// home path: light = white fill with ink-8% stroke; dark =
    /// white@5% fill with white@10% stroke.
    static let cardFill = dynamicColor(
        light: UIColor(hexRGB: "FFFFFF", alpha: 1),
        dark: UIColor(hexRGB: "FFFFFF", alpha: 0.05)
    )
    static let cardStroke = dynamicColor(
        light: UIColor(hexRGB: "17191C", alpha: 0.08),
        dark: UIColor(hexRGB: "FFFFFF", alpha: 0.10)
    )
    /// Bounded TOP gradient only (RW-25): chrome legibility at the
    /// hero's head. Replaces the 6-stop full-frame overlay on the
    /// home hero — the sheet owns all text now, so the photo's foot
    /// needs no darkening.
    static func heroTopGradient() -> LinearGradient {
        LinearGradient(
            stops: [
                .init(color: .black.opacity(0.35), location: 0),
                .init(color: .clear, location: 0.35)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private static func dynamicColor(lightHex: String, darkHex: String) -> Color {
        dynamicColor(
            light: UIColor(hexRGB: lightHex, alpha: 1),
            dark: UIColor(hexRGB: darkHex, alpha: 1)
        )
    }

    private static func dynamicColor(light: UIColor, dark: UIColor) -> Color {
        #if os(watchOS)
        return Color(dark)
        #else
        return Color(UIColor { trait in
            trait.userInterfaceStyle == .dark ? dark : light
        })
        #endif
    }
}

// MARK: - Appearance Preference (RW-25)

/// The user's appearance choice, persisted under
/// "appearancePreference" (@AppStorage). `.system` resolves to a nil
/// color scheme — no override — so the app follows the device.
enum AppearancePreference: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }

    /// The preferredColorScheme value for this preference.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    /// Pure resolution mapping (pinned by unit tests).
    static func resolve(_ preference: AppearancePreference) -> ColorScheme? {
        preference.colorScheme
    }
}

extension UIColor {
    convenience init(hexRGB hex: String, alpha: Double) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&int)
        self.init(
            red: CGFloat((int >> 16) & 0xFF) / 255,
            green: CGFloat((int >> 8) & 0xFF) / 255,
            blue: CGFloat(int & 0xFF) / 255,
            alpha: CGFloat(alpha)
        )
    }
}

// MARK: - Typography Tokens (RW-6.3)
//
// Dynamic Type throughout: every text surface uses a semantic style from
// this scale so the whole app tracks the user's text-size setting. SF Pro
// is the system face — no custom font is bundled (the audit's typography
// verdict: do NOT import Bebas Neue). Numeric values use tabular digits
// via `.monospacedDigit()` at the call site.
extension Font {
    /// Sheet headers (was fixed 24pt bold).
    static var appSheetTitle: Font { .title2.weight(.bold) }
    /// Section headers on the home screen (was fixed 20pt bold).
    static var appSectionTitle: Font { .title3.weight(.bold) }
    /// Item names in cards and rows (was fixed 16pt semibold).
    static var appItemName: Font { .headline }
    /// Primary button labels (was fixed 17–18pt semibold).
    static var appButtonLabel: Font { .body.weight(.semibold) }
    /// Secondary body copy (was fixed 13–15pt regular).
    static var appSecondary: Font { .subheadline }
    /// Small descriptive copy under item names (was fixed 13pt).
    static var appFootnote: Font { .footnote }
    /// Category eyebrows / micro-labels (was fixed 11pt semibold).
    static var appEyebrow: Font { .caption.weight(.semibold) }
    /// Floating pill labels (was fixed 14–15pt medium).
    static var appPillLabel: Font { .subheadline.weight(.medium) }
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
