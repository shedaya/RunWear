import Foundation
import RunWearEngine

/// Service for generating clothing thumbnail URLs.
///
/// Thumbnails are AI-generated product images stored in Supabase Storage.
/// 52 total images: 26 clothing items × 2 genders (male/female).
///
/// Style: Dark gray/charcoal clothing on pure black background,
/// professional product photography aesthetic.
///
/// Stage 2 (2026-10-29): the item→file-key map that used to be mirrored
/// in this file is DELETED. The one map lives in the shared Kotlin module
/// (ClothingThumbnailService in `:engine`), and this service delegates to
/// it by item key — so a key change on Android is a key change here, by
/// construction. The boundary fixtures assert every emittable key
/// resolves through this path.
///
/// History (why keys, never names): this map was once keyed by display
/// name and carried legacy names the v4.x engine no longer produces
/// ("Split Shorts", "Running Tights"), so Short Shorts and Light
/// Tights — two of the most common recommendations — resolved to no
/// thumbnail and rendered as bare glyphs forever.
class ClothingThumbnailService {
    static let shared = ClothingThumbnailService()

    private init() {}

    /// Get the thumbnail URL for a clothing item based on gender preference.
    ///
    /// - Parameters:
    ///   - item: The clothing item
    ///   - gender: The user's gender preference (.unisex defaults to male)
    /// - Returns: The full URL to the thumbnail image, or nil if no thumbnail exists
    func getThumbnailURL(for item: ClothingItem, gender: GenderPreference) -> URL? {
        let urlString = RunWearEngine.ClothingThumbnailService.shared.getThumbnailUrl(
            itemName: item.key,
            genderName: gender.rawValue.uppercased()
        )
        return urlString.flatMap { URL(string: $0) }
    }

    /// Check if a clothing item has a thumbnail available.
    func hasThumbnail(for item: ClothingItem) -> Bool {
        RunWearEngine.ClothingThumbnailService.shared.hasThumbnail(itemName: item.key)
    }

    // MARK: - RW-144 light-mode garment tiles (Treatment L)

    /// The bundled LIGHT-mode garment tile for an item, or nil.
    ///
    /// The seam is the app's existing garment-tile mechanism —
    /// the engine's one item→file-key map
    /// (`thumbnailKeyForName`, the same key `getThumbnailURL`
    /// resolves remotely) plus the bundle, exactly as
    /// `hero_fallback/` bundles the hero pool: the Treatment L
    /// PNGs live in the `garment_tiles_light/` folder reference
    /// under their final-L filenames (`<key>-<gender>.png`), so
    /// the icon top-up is a file-for-file swap in that folder
    /// with NO code change. Dark mode does not use this seam —
    /// it keeps the remote thumbnail treatment.
    func lightTileURL(for item: ClothingItem, gender: GenderPreference, bundle: Bundle = .main) -> URL? {
        guard let resource = lightTileResourceName(for: item, gender: gender) else {
            return nil
        }
        return bundle.url(
            forResource: resource,
            withExtension: "png",
            subdirectory: "garment_tiles_light"
        )
    }

    /// The bundled resource name (`<key>-<gender>`) a light tile
    /// resolves to, before bundle-URL resolution. Internal (not
    /// private) so unit tests can pin the resolution in a
    /// logic-test host, where Bundle.main is the test runner and
    /// no app resources resolve — the same split FallbackImage
    /// Provider uses for the hero pool.
    func lightTileResourceName(for item: ClothingItem, gender: GenderPreference) -> String? {
        guard let key = RunWearEngine.ClothingThumbnailService.shared.thumbnailKeyForName(itemName: item.key) else {
            return nil
        }
        let suffix = gender == .female ? "female" : "male"
        return "\(key)-\(suffix)"
    }
}
