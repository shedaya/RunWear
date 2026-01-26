import Foundation

/// Gender Preference for clothing search personalization
///
/// Prepends "men's" or "women's" to search terms for more relevant results.
/// Unisex returns generic results without gender prefix.
enum GenderPreference: String, CaseIterable {
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

    /// Builds a search term with the appropriate gender prefix
    /// Example: "running tank top" -> "men's running tank top"
    func buildSearchTerm(_ baseTerm: String) -> String {
        return "\(searchPrefix)\(baseTerm)"
    }
}
