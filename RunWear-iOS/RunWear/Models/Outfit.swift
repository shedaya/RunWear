import Foundation

struct OutfitRecommendation {
    let temperatureBracket: TemperatureBracket
    let top: ClothingItem
    let bottom: ClothingItem
    let accessories: [ClothingItem]
    let extras: [ClothingItem]

    var allItems: [ClothingItem] {
        [top, bottom] + accessories + extras
    }
}

struct ClothingItem: Identifiable {
    let id = UUID()
    let name: String
    let description: String
    let icon: String
    let amazonSearchTerm: String

    /// Affiliate tag for Amazon links
    static let affiliateTag = "runwear-20"

    /// Build affiliate URL with gender preference and platform subtag
    /// - Parameters:
    ///   - gender: Gender preference for search term
    ///   - subtag: Platform identifier (ios, watchos)
    func affiliateURL(for gender: GenderPreference, subtag: String = "ios") -> URL? {
        let genderedTerm = "premium \(gender.buildSearchTerm(amazonSearchTerm))"
        let searchQuery = genderedTerm.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://www.amazon.com/s?k=\(searchQuery)&tag=\(Self.affiliateTag)&subtag=\(subtag)")
    }

    /// Build affiliate URL with gender preference (defaults to iOS subtag)
    func affiliateURL(for gender: GenderPreference) -> URL? {
        affiliateURL(for: gender, subtag: "ios")
    }

    /// Convenience for unisex/legacy usage
    var affiliateURL: URL? {
        affiliateURL(for: .unisex)
    }
}

enum TemperatureBracket: String, CaseIterable {
    case hot = "70°F+"
    case warm = "60-70°F"
    case mild = "50-60°F"
    case cool = "40-50°F"
    case cold = "30-40°F"
    case veryCold = "20-30°F"
    case extreme = "<20°F"

    var description: String {
        switch self {
        case .hot: return "Hot Weather Running"
        case .warm: return "Warm Weather Running"
        case .mild: return "Mild Weather Running"
        case .cool: return "Cool Weather Running"
        case .cold: return "Cold Weather Running"
        case .veryCold: return "Very Cold Weather Running"
        case .extreme: return "Extreme Cold Running"
        }
    }

    var colorOpacity: Double {
        switch self {
        case .hot: return 0.3
        case .warm: return 0.4
        case .mild: return 0.5
        case .cool: return 0.6
        case .cold: return 0.7
        case .veryCold: return 0.8
        case .extreme: return 0.9
        }
    }

    static func from(temperature: Double) -> TemperatureBracket {
        switch temperature {
        case 70...: return .hot
        case 60..<70: return .warm
        case 50..<60: return .mild
        case 40..<50: return .cool
        case 30..<40: return .cold
        case 20..<30: return .veryCold
        default: return .extreme
        }
    }
}
