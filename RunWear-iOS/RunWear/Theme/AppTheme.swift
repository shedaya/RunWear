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
    // RW-26 (consolidated design, notes §1): the cream + Warm Charcoal
    // pair replaces the phase-1 neutral pair. These legacy constants
    // carry the same values as the dynamic tokens below so forced-dark
    // surfaces (onboarding, location picker, photos) warm up too.
    static let backgroundColor = Color(hex: "F5F1E8")
    static let darkBackground = Color(hex: "16130F")
    static let cardBackground = Color(hex: "FDFBF6")
    static let darkCardBackground = Color(hex: "211D17")

    // MARK: - Text Colors
    static let textPrimary = Color(hex: "17191C")
    static let textSecondary = Color(hex: "5F5B52")
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

    // MARK: - Dynamic Theme Pair (RW-25; token pass RW-26)
    //
    // Trait-dynamic tokens for the appearance pair. RW-26 replaces
    // the phase-1 pair (light #F5F5F5/#FFFFFF, dark
    // #0A0A0A/#1A1A1A/#262626) with the consolidated cream + Warm
    // Charcoal pair (final design notes §1). Warmth lives in the
    // neutrals only: pine, the temperature hexes, and data ink are
    // unchanged. The hex constants are the pins the unit tests
    // assert. On watchOS (always dark, and UIColor dynamic
    // providers are unavailable) the tokens resolve to the dark side.
    static let groundLightHex = "F5F1E8"
    static let sheetLightHex = "FDFBF6"
    static let inkLightHex = "17191C"
    static let inkSecondaryLightHex = "5F5B52"
    static let groundDarkHex = "16130F"
    static let sheetDarkHex = "211D17"
    static let cardDarkHex = "2A251E"
    static let inkDarkHex = "F5F1EA"
    static let mutedDarkHex = "B8AEA0"
    /// Dark-mode pine accent (notes §1: dark accent #5CC79F); the
    /// light-mode payload pine stays the seed #175E4C.
    static let pineAccentDarkHex = "5CC79F"
    /// Warm white for the script logo over photography (notes §7.3).
    static let warmWhiteHex = "F5F1EA"

    static let background = dynamicColor(lightHex: groundLightHex, darkHex: groundDarkHex)
    static let sheet = dynamicColor(lightHex: sheetLightHex, darkHex: sheetDarkHex)
    /// Card on the sheet: light cards share the sheet hex (notes §1:
    /// "sheet/cards #FDFBF6"), so on the home sheet a card separates
    /// by sitting on the ground-toned chip fill below; dark cards are
    /// the Warm Charcoal card hex.
    static let cardOnSheet = dynamicColor(lightHex: groundLightHex, darkHex: cardDarkHex)
    static let inkPrimary = dynamicColor(lightHex: inkLightHex, darkHex: inkDarkHex)
    static let inkSecondary = dynamicColor(
        lightHex: inkSecondaryLightHex,
        darkHex: mutedDarkHex
    )
    /// The design defines exactly two neutral text tones per mode
    /// (ink + secondary/muted), so the muted token resolves to the
    /// same pair as inkSecondary.
    static let inkMuted = dynamicColor(
        lightHex: inkSecondaryLightHex,
        darkHex: mutedDarkHex
    )
    /// Pine for payload text (mini-bar narration, accents): the seed
    /// in light mode, the brightened accent on Warm Charcoal.
    static let pinePayload = dynamicColor(lightHex: "175E4C", darkHex: pineAccentDarkHex)

    // MARK: - Weather pill legibility (RW-26, phase-1 flag closure)
    //
    // The weather pills keep Liquid Glass (chrome class) in both
    // modes, but the phase-1 white label cannot survive the cream
    // sheet: clear glass over #FDFBF6 renders ≈that hex, and white
    // on #FDFBF6 is ≈1.03:1 — below even the 3:1 large-text floor,
    // let alone 4.5:1 for the ~15pt pill labels. The fix lives in
    // the shared WeatherPill: content (label + icon) is ink
    // #17191C in light mode (17.03:1 on the sheet, per the design's
    // own contrast table) and stays white/hued in dark mode (white
    // on #211D17 ≈ 16.7:1). Glass itself is unchanged.
    static func weatherPillContentHex(isDark: Bool) -> String {
        isDark ? "FFFFFF" : inkLightHex
    }
    /// The script logo's warm white over photography (both modes).
    static let warmWhite = Color(hex: warmWhiteHex)
    /// Dynamic successor to cardFillOnDark/cardStrokeOnDark for the
    /// home path: light = white fill with ink-8% stroke; dark =
    /// white@5% fill with white@10% stroke.
    static let cardFill = dynamicColor(
        light: UIColor(hexRGB: "FDFBF6", alpha: 1),
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
// is the system face for text — no custom text font is bundled (the
// audit's typography verdict: do NOT import Bebas Neue). The one bundled
// face is Great Vibes, for the script wordmarks only (see
// `Font.greatVibes` below). Numeric values use tabular digits via
// `.monospacedDigit()` at the call site.
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

    /// Great Vibes script — the wordmark face (the RunWear logo,
    /// "Wear I've Run", "Make It Me"). Brand lockups only, never body
    /// text. Genuine bundled font (Google Fonts, SIL Open Font
    /// License 1.1), registered via UIAppFonts in Info.plist; resolves
    /// by its PostScript name "GreatVibes-Regular". Bundled 2026-11-02
    /// (RW-25): provenance + verification in
    /// release-prep/rw-greatvibes-bundling-2026-11-02.md.
    static func greatVibes(size: CGFloat) -> Font {
        .custom("GreatVibes-Regular", size: size)
    }
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
