import Foundation

struct OutfitRecommendation {
    let temperatureBracket: TemperatureBracket
    let topBase: ClothingItem
    let topMid: ClothingItem?
    let topOuter: ClothingItem?
    let bottom: ClothingItem
    let head: ClothingItem?
    let hands: ClothingItem?
    let accessories: [ClothingItem]
    let tips: [String]

    var allItems: [ClothingItem] {
        [head, topBase, topMid, topOuter, bottom, hands].compactMap { $0 } + accessories
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

/// Temperature brackets for outfit recommendations (v4.1)
/// Thresholds reflect running-specific needs — runners generate 5-15× resting metabolic heat.
enum TemperatureBracket: String, CaseIterable {
    case hot = "80°F+"
    case warm = "65-79°F"
    case mild = "50-64°F"
    case cool = "40-49°F"
    case cold = "30-39°F"
    case veryCold = "20-29°F"
    case frigid = "10-19°F"
    case extreme = "<10°F"

    var description: String {
        switch self {
        case .hot: return "Hot Weather Running"
        case .warm: return "Warm Weather Running"
        case .mild: return "Mild Weather Running"
        case .cool: return "Cool Weather Running"
        case .cold: return "Cold Weather Running"
        case .veryCold: return "Very Cold Weather Running"
        case .frigid: return "Frigid Weather Running"
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
        case .frigid: return 0.85
        case .extreme: return 0.9
        }
    }

    static func from(temperature: Double) -> TemperatureBracket {
        switch temperature {
        case 80...: return .hot
        case 65..<80: return .warm
        case 50..<65: return .mild
        case 40..<50: return .cool
        case 30..<40: return .cold
        case 20..<30: return .veryCold
        case 10..<20: return .frigid
        default: return .extreme
        }
    }
}
