import Foundation

/// One selectable fit-preference choice: the preference value and
/// the label the selection surfaces render for it (W-22).
struct FitPreferenceOption: Equatable {
    let value: GenderPreference
    let label: String
}

/// Gender Preference for clothing search personalization
///
/// Prepends "men's" or "women's" to search terms for more relevant results.
/// Unisex returns generic results without gender prefix.
enum GenderPreference: String, Codable, CaseIterable {
    case male = "male"
    case female = "female"
    case unisex = "unisex"

    var searchPrefix: String {
        switch self {
        case .male: return "men's "
        case .female: return "women's "
        case .unisex: return ""
        }
    }

    // WS-10 (A4 residue, R-1): the former `displayIcon` (🚹/🚺/○
    // text glyphs) is REMOVED. Its only consumer was the dead
    // GenderToggle/GenderOption pair (no call sites anywhere in
    // the app — verified by full-tree search), and no frame of
    // the approved design carries emoji or text-glyph picker
    // markers: the live fit picker (FitPreferencePicker) renders
    // plain text labels. Removal, not redesign — nothing rendered
    // it, so nothing changes on screen.

    /// Text display label (not icons)
    var displayLabel: String {
        switch self {
        case .male: return "Male"
        case .female: return "Female"
        case .unisex: return "Unisex"
        }
    }

    /// SF Symbol icon name for the preference
    var iconName: String {
        switch self {
        case .male: return "figure.run"
        case .female: return "figure.run"
        case .unisex: return "figure.2"
        }
    }

    /// Builds a search term with the appropriate gender prefix
    /// Example: "running tank top" -> "men's running tank top"
    func buildSearchTerm(_ baseTerm: String) -> String {
        return "\(searchPrefix)\(baseTerm)"
    }

    /// W-22 (GEN-2): the fit preference's three choices, in the
    /// order every selection surface presents them — the single
    /// list the onboarding step's inline picker
    /// (FitPreferenceInlinePicker) renders directly on screen.
    /// (A model value, not view state, so the test target can pin
    /// the choices without compiling the controls.)
    static let fitOptions: [FitPreferenceOption] = [
        FitPreferenceOption(value: .male, label: "Men's"),
        FitPreferenceOption(value: .female, label: "Women's"),
        FitPreferenceOption(value: .unisex, label: "No preference")
    ]

    /// WS-6 (D-20): the DETERMINISTIC hero gender the bundled
    /// fallback paints with. Unlike `forHeroImage()` — whose
    /// unisex case is a coin flip for pool variety — the fallback
    /// call sites must resolve the same asset every time for a
    /// given preference, and a unisex preference has no gendered
    /// asset to demand: it keeps the legacy ungendered chain,
    /// exactly the treatment Android gives UNISEX. Nil = legacy.
    var fallbackHeroGender: HeroGender? {
        switch self {
        case .male: return .MALE
        case .female: return .FEMALE
        case .unisex: return nil
        }
    }

    /// Returns a HeroGender for hero image selection
    /// For unisex preference, randomly returns MALE or FEMALE (50/50)
    /// This ensures variety in hero images when no preference is set
    func forHeroImage() -> HeroGender {
        switch self {
        case .male:
            return .MALE
        case .female:
            return .FEMALE
        case .unisex:
            return Bool.random() ? .MALE : .FEMALE
        }
    }
}
