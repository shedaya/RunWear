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

    /// Map clothing items to thumbnail file keys, keyed by the item's
    /// stable `key` (the Kotlin `ClothingItem` enum case name) — an
    /// exact mirror of Android's ClothingThumbnailService map,
    /// including the entries the engine never emits (LIGHT_VEST,
    /// LIGHT_JACKET, CAPRIS). File keys use lowercase with hyphens
    /// (e.g. TANK_TOP → "tank-top").
    ///
    /// History: this map was keyed by display name and carried legacy
    /// names the v4.x engine no longer produces ("Split Shorts",
    /// "Running Tights"), so Short Shorts and Light Tights — two of
    /// the most common recommendations — resolved to no thumbnail and
    /// rendered as bare glyphs forever. Keying by the enum identity
    /// makes a missing mapping a compile-time-visible hole instead:
    /// every engine construction must set a key, and the boundary
    /// fixtures assert every emittable key resolves here.
    private let thumbnailKeys: [String: String] = [
        // Tops - Base Layer
        "TANK_TOP": "tank-top",
        "SHORT_SLEEVE": "short-sleeve",
        "LONG_SLEEVE_LIGHT": "long-sleeve-light",
        "LONG_SLEEVE_THERMAL": "thermal-long-sleeve",

        // Tops - Mid Layer
        "QUARTER_ZIP": "quarter-zip",
        "FLEECE_PULLOVER": "fleece-pullover",
        "HALF_ZIP_THERMAL": "half-zip-thermal",

        // Tops - Outer Layer
        "LIGHT_VEST": "light-vest",
        "WINDBREAKER": "windbreaker",
        "LIGHT_JACKET": "light-jacket",
        "RAIN_JACKET": "rain-jacket",
        "INSULATED_JACKET": "insulated-jacket",

        // Bottoms
        "SHORT_SHORTS": "short-shorts",
        "SHORTS": "running-shorts",
        "CAPRIS": "light-tights", // Capris use light-tights thumbnail (Android parity)
        "LIGHT_TIGHTS": "light-tights",
        "THERMAL_TIGHTS": "thermal-tights",

        // Head
        "VISOR": "visor",
        "BASEBALL_CAP": "running-cap",
        "HEADBAND": "headband",
        "LIGHT_BEANIE": "light-beanie",
        "THERMAL_BEANIE": "thermal-beanie",
        "BALACLAVA": "balaclava",

        // Hands
        "LIGHT_GLOVES": "light-gloves",
        "THERMAL_GLOVES": "thermal-gloves",
        "MITTENS": "mittens",

        // Accessories
        "SUNGLASSES": "sunglasses",
        "SUNSCREEN": "sunscreen",
        "REFLECTIVE_GEAR": "reflective-gear",
        "NECK_GAITER": "neck-gaiter"
    ]

    /// Get the thumbnail URL for a clothing item based on gender preference.
    ///
    /// - Parameters:
    ///   - item: The clothing item
    ///   - gender: The user's gender preference (.unisex defaults to male)
    /// - Returns: The full URL to the thumbnail image, or nil if no thumbnail exists
    func getThumbnailURL(for item: ClothingItem, gender: GenderPreference) -> URL? {
        guard let thumbnailKey = thumbnailKeys[item.key] else { return nil }

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
        return thumbnailKeys[item.key] != nil
    }
}
