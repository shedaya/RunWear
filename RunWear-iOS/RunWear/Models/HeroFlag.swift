import Foundation

/// Why a user flagged a hero photo. Mirror of Android's
/// `HeroFlagReason` (shared module, 2026-10-29): `code` is the stable
/// value stored in `public.hero_flags.reason` — the table's CHECK
/// constraint accepts exactly these codes — and `label` is the text
/// shown in the flag sheet.
///
/// Flags are the human quality signal for the hero pool: because new
/// generations auto-approve (Sol's 2026-10-27 auto-replenishment call),
/// a flag is how a bad photo gets reported for manual review and
/// retraction. Nothing acts on a flag automatically.
enum HeroFlagReason: String, CaseIterable {
    case looksBroken = "looks_broken"
    case inappropriate = "inappropriate"
    case outfitMismatch = "outfit_mismatch"

    /// The stored `hero_flags.reason` value.
    var code: String { rawValue }

    /// The label shown in the flag sheet.
    var label: String {
        switch self {
        case .looksBroken: return "Looks broken"
        case .inappropriate: return "Inappropriate"
        case .outfitMismatch: return "Doesn't match the outfit"
        }
    }
}

/// The photo on screen, as a flag must identify it: the served pool
/// row's identity when the hero came from the pool, or the curated
/// fallback's URL (with no row id) when it did not.
struct HeroFlagTarget: Equatable {
    let imageUrl: String
    let imageRowId: String?
    let servedCombinationId: String?
    let requestedCombinationId: String?
}

/// Insert model for `public.hero_flags` (migration 20261029190000,
/// already live in runwear-prod). Mirrors the table column-for-column
/// and Android's `HeroFlagInsert`. Nil optionals are omitted from the
/// encoded JSON, which PostgREST treats the same as an explicit null.
struct HeroFlagInsert: Codable, Equatable {
    let imageRowId: String?
    let imageUrl: String
    let combinationId: String?
    let imageSource: String
    let reason: String
    let platform: String
    let appVersion: String?
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case imageRowId = "image_row_id"
        case imageUrl = "image_url"
        case combinationId = "combination_id"
        case imageSource = "image_source"
        case reason
        case platform
        case appVersion = "app_version"
        case createdAt = "created_at"
    }
}
