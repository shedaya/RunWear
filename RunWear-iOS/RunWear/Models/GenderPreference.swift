import Foundation

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

    var displayIcon: String {
        switch self {
        case .male: return "🚹"
        case .female: return "🚺"
        case .unisex: return "○"
        }
    }

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
