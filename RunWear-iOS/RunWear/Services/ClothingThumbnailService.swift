import Foundation

/// Service for generating clothing thumbnail URLs.
///
/// Thumbnails are AI-generated product images stored in Supabase Storage.
/// 52 total images: 26 clothing items × 2 genders (male/female).
///
/// Style: Dark gray/charcoal clothing on pure black background,
/// professional product photography aesthetic.
class ClothingThumbnailService {
    static let shared = ClothingThumbnailService()

    private let baseURL = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/clothing-thumbnails"

    private init() {}

    /// Map clothing item names to thumbnail file keys.
    /// Keys use lowercase with hyphens (e.g., "Tank Top" → "tank-top").
    private let thumbnailKeys: [String: String] = [
        // Tops - Base Layer
        "Tank Top": "tank-top",
        "Short Sleeve Shirt": "short-sleeve",
        "Long Sleeve Shirt": "long-sleeve-light",
        "Thermal Long Sleeve": "thermal-long-sleeve",
        "Base Layer + Jacket": "thermal-long-sleeve",
        "Double Layer Top": "insulated-jacket",
        "Full Thermal System": "insulated-jacket",

        // Tops - Outer Layer
        "Light Vest": "light-vest",
        "Windbreaker": "windbreaker",
        "Light Jacket": "light-jacket",
        "Rain Jacket": "rain-jacket",
        "Insulated Jacket": "insulated-jacket",

        // Bottoms
        "Split Shorts": "short-shorts",
        "Running Shorts": "running-shorts",
        "Running Tights": "light-tights",
        "Thermal Tights": "thermal-tights",
        "Insulated Tights": "thermal-tights",
        "Wind-Proof Pants": "thermal-tights",

        // Head
        "Visor": "visor",
        "Running Cap": "running-cap",
        "Headband": "headband",
        "Ear Warmer": "headband",
        "Light Beanie": "light-beanie",
        "Running Beanie": "light-beanie",
        "Thermal Beanie": "thermal-beanie",
        "Balaclava": "balaclava",
        "Full Balaclava": "balaclava",

        // Hands
        "Light Gloves": "light-gloves",
        "Running Gloves": "thermal-gloves",
        "Thermal Gloves": "thermal-gloves",
        "Heavy Gloves": "thermal-gloves",
        "Mittens": "mittens",
        "Insulated Mittens": "mittens",

        // Accessories
        "Sunglasses": "sunglasses",
        "Sunscreen": "sunscreen",
        "Reflective Gear": "reflective-gear",
        "Neck Gaiter": "neck-gaiter",
        "Arm Sleeves": "light-gloves", // Use gloves thumbnail as proxy
        "Hand Warmers": "thermal-gloves" // Use gloves thumbnail as proxy
    ]

    /// Get the thumbnail URL for a clothing item based on gender preference.
    ///
    /// - Parameters:
    ///   - item: The clothing item
    ///   - gender: The user's gender preference (.unisex defaults to male)
    /// - Returns: The full URL to the thumbnail image, or nil if no thumbnail exists
    func getThumbnailURL(for item: ClothingItem, gender: GenderPreference) -> URL? {
        guard let thumbnailKey = thumbnailKeys[item.name] else { return nil }

        let genderSuffix: String
        switch gender {
        case .female:
            genderSuffix = "female"
        case .male, .unisex:
            genderSuffix = "male"
        }

        return URL(string: "\(baseURL)/\(thumbnailKey)-\(genderSuffix).webp")
    }

    /// Check if a clothing item has a thumbnail available.
    func hasThumbnail(for item: ClothingItem) -> Bool {
        return thumbnailKeys[item.name] != nil
    }
}
