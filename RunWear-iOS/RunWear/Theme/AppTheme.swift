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
    // carry the same values as the dynamic tokens below. Every
    // user-facing surface now renders on the dynamic pair — weather
    // detail, the Make It Me flows, and Your Photos in the dual-mode
    // conversion; onboarding's preferences phase and Shop in the
    // follow-on — so the constants remain as the value record the
    // dynamic hexes mirror (and the watch target's reference).
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

    // RW-105 (Sol, Nov 6, 2026): the bracket BADGE's chip fills —
    // the data palette above deepened (RGB × 0.62) to full
    // strength for the white-text chip. The pre-fix chip filled
    // with the bright data colour at 0.8 opacity over the cream
    // ground, so the mild chip rendered pale mint with white
    // text (≈2:1) — the surface Sol circled as unreadable. The
    // data palette itself (chart bands, tints) is untouched;
    // only the chip gets the deepened set. White text on every
    // chip colour here measures ≈5:1 or better.
    static let tempChipFreezingHex = "563999"
    static let tempChipColdHex = "3D3F95"
    static let tempChipCoolHex = "255199"
    static let tempChipMildHex = "0A7350"
    static let tempChipWarmHex = "986207"
    static let tempChipHotHex = "9A470E"
    static let tempChipFreezing = Color(hex: tempChipFreezingHex)
    static let tempChipCold = Color(hex: tempChipColdHex)
    static let tempChipCool = Color(hex: tempChipCoolHex)
    static let tempChipMild = Color(hex: tempChipMildHex)
    static let tempChipWarm = Color(hex: tempChipWarmHex)
    static let tempChipHot = Color(hex: tempChipHotHex)

    // The bracket-typed temperature helpers (temperatureHex,
    // temperatureColor, temperatureTintOverlay) live in
    // AppThemeTemperature.swift — this file is membered into the
    // widget extension, which cannot see HeroTempBracket.

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

    // MARK: - Animation Durations
    /// The hero photo crossfade (rebuild program Part 1, §1.1:
    /// 300 ms; §1.0's standard crossfade band is 0.20–0.30 s).
    /// WS-1 corrected this from 0.5 s — the emphasized band,
    /// which §1.0 reserves for sheet travel and morphs, not for
    /// a photo crossfade. Its only consumer is HeroImageView.
    static let crossfadeDuration: Double = 0.3
    static let staggerDuration: Double = 0.4
    static let staggerDelay: Double = 0.05
    static let modalTransitionDuration: Double = 0.3

    // MARK: - Sizing
    /// RW-144 (first-screen redesign, approved Nov 8, 2026): the
    /// hero is ≈45% of the screen — the sheet's top edge sits at
    /// ≈43–45% — superseding the 0.75 photo-dominant seat. The
    /// v8 frames are the design of record.
    static let heroHeightRatio: CGFloat = 0.45
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
    /// RW-144 palette of record (Nov 8, 2026): the light ink is
    /// the deep warm brown #33291F — nothing on cream is true
    /// black — superseding the phase-1 near-black #17191C, and
    /// the light secondary is #685D4F, superseding #5F5B52.
    static let inkLightHex = "33291F"
    static let inkSecondaryLightHex = "685D4F"
    /// RW-144: description tone (light #807363 / dark #988E80)
    /// and the garment-tile fill (light #F3EEE5 / dark #25201A).
    static let descriptionLightHex = "807363"
    static let descriptionDarkHex = "988E80"
    static let tileLightHex = "F3EEE5"
    static let tileDarkHex = "25201A"
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
    /// RW-144: description text tone — #807363 on cream, #988E80
    /// on Warm Charcoal (the v8 palette audit table).
    static let descriptionInk = dynamicColor(lightHex: descriptionLightHex, darkHex: descriptionDarkHex)
    /// RW-144: garment-tile fill — #F3EEE5 light, #25201A dark.
    static let garmentTileFill = dynamicColor(lightHex: tileLightHex, darkHex: tileDarkHex)
    /// RW-144: the Make It Me pill / dock fill — the card cream
    /// #FDFBF6 in light mode, #2A251E in dark (cardDarkHex).
    static let firstScreenPillFill = dynamicColor(lightHex: sheetLightHex, darkHex: cardDarkHex)
    /// RW-144: the sheet hairline — warm ink at α26/255 in light
    /// mode, cream at α22/255 in dark.
    static let sheetHairline = dynamicColor(
        light: UIColor(hexRGB: inkLightHex, alpha: 26.0 / 255.0),
        dark: UIColor(hexRGB: inkDarkHex, alpha: 22.0 / 255.0)
    )
    /// The dark pine accent as a fixed color, for surfaces that are
    /// dark in BOTH modes (e.g. the refresh error banner's black
    /// fill): pine-as-text there must be the accent, never the seed
    /// (the seed computes to ≈2:1 on near-black — the D-25 class).
    static let pineAccentDark = Color(hex: pineAccentDarkHex)

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

    // MARK: - Semantic accents (dual-mode conversion of the forced-dark surfaces)
    //
    // WeatherDetailSheet, the Make It Me flows, and Your Photos
    // were the last white-on-dark surfaces: their palettes named
    // SwiftUI's system hues directly, which are tuned per
    // appearance by the OS only when the color is used as a
    // dynamic color — as fixed foregrounds on the old forced-dark
    // ground they read as the dark variants in BOTH modes. The
    // conversion gives every semantic hue an explicit pair: the
    // dark side keeps the exact system-color hex the surfaces
    // ship today (rendering on Warm Charcoal is unchanged); the
    // light side is deepened until it clears 4.5:1 on BOTH light
    // grounds (cream #F5F1E8 and sheet #FDFBF6), because the UV
    // band label is body text, not decoration — the same
    // legibility standard the WeatherPill fix pinned. The one
    // exception is dark indigo (#5E5CE6, 3.31:1 on the dark
    // sheet): it is a graphic-only icon hue, never text, and
    // keeping it preserves the shipped dark rendering exactly;
    // its light pair clears 4.5:1 like the rest. Ratios are
    // computed and pinned in RW26Phase2Tests.
    enum WeatherAccent: CaseIterable {
        case green, yellow, orange, red, purple, gray, blue, cyan, indigo
    }

    static func weatherAccentHex(_ accent: WeatherAccent, isDark: Bool) -> String {
        switch (accent, isDark) {
        case (.green, true): return "30D158"
        case (.green, false): return "1E7D32"
        case (.yellow, true): return "FFD60A"
        case (.yellow, false): return "806000"
        case (.orange, true): return "FF9F0A"
        case (.orange, false): return "A34A08"
        case (.red, true): return "FF453A"
        case (.red, false): return "B91C1C"
        case (.purple, true): return "BF5AF2"
        case (.purple, false): return "7C3AED"
        case (.gray, true): return "98989F"
        case (.gray, false): return inkSecondaryLightHex
        case (.blue, true): return "0A84FF"
        case (.blue, false): return "1D4ED8"
        case (.cyan, true): return "64D2FF"
        case (.cyan, false): return "0E7490"
        case (.indigo, true): return "5E5CE6"
        case (.indigo, false): return "4338CA"
        }
    }

    static func weatherAccentColor(_ accent: WeatherAccent, isDark: Bool) -> Color {
        Color(hex: weatherAccentHex(accent, isDark: isDark))
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
        light: UIColor(hexRGB: inkLightHex, alpha: 0.08),
        dark: UIColor(hexRGB: "FFFFFF", alpha: 0.10)
    )
    /// Bounded TOP gradient only (RW-25): chrome legibility at the
    /// hero's head. Replaces the 6-stop full-frame overlay on the
    /// home hero — the sheet owns all text now, so the photo's foot
    /// needs no darkening.
    ///
    /// GEN-3 DESIGN DELTA 1 (Sol's mockup review, Nov 4 — FIRM;
    /// strengthened-scrim treatment approved as mocked in round 2):
    /// the centered script wordmark got lost on light heroes. The
    /// treatment is the design system's EXISTING scrim language at
    /// the Morning Poster's top-scrim strength: the poster's stop
    /// alphas (0.62 / 0.31 — see MorningPosterView's area scrim) on
    /// this gradient's black, keeping this gradient's own seat
    /// (gone by 0.35 of the hero height, the mid stop at its
    /// midpoint). The wordmark's own shadow token is unchanged.
    /// Measured on the bundled set's lightest heroes (top-center
    /// wordmark zone, area-averaged warm-white contrast, before
    /// the shadow's glyph-local darkening): freezing_clear_male
    /// 1.84 → 3.64, cold_snow_male 1.88 → 3.72, cold_snow_female
    /// 2.00 → 3.93, freezing_cloudy_male 2.05 → 3.99,
    /// cool_rain_male 2.05 → 4.00.
    static let heroTopScrimAlphas: [Double] = [0.62, 0.31]
    static func heroTopGradient() -> LinearGradient {
        // RW-144: the top scrim's ink is the warm black (26,20,14)
        // of the v8 design, not pure black.
        LinearGradient(
            stops: [
                .init(color: Color(hex: "1A140E").opacity(heroTopScrimAlphas[0]), location: 0),
                .init(color: Color(hex: "1A140E").opacity(heroTopScrimAlphas[1]), location: 0.175),
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
        case .system: return AppStrings.settingsAppearanceSystem
        case .light: return AppStrings.settingsAppearanceLight
        case .dark: return AppStrings.settingsAppearanceDark
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
