import Foundation

// MARK: - RW-14 "Make it me" — pure model layer
//
// Foundation-only on purpose: this file is compiled into BOTH the app
// target and the RunWearTests target (the test target's established
// pattern of compiling app files directly), so the combination-id,
// cache-version, upsell-eligibility and photo-verdict logic is unit
// tested against the backend contract's own worked examples.
//
// Backend contract: rw14-backend-build-2026-10-31.md §2.
// combination_id = {GENDER}_{WEATHER}_{TEMP_BRACKET}_{TIME_OF_DAY}_{OUTFIT_HASH}
// where OUTFIT_HASH is the Kotlin/Java String.hashCode() (31-based over
// UTF-16 code units) of the sorted ClothingItem keys joined with "-",
// rendered unsigned lowercase hex, last 8 chars zero-padded — identical
// to OutfitCombination.fromConditions in the shared engine.

// MARK: - Outfit signature hash

enum OutfitSignatureHash {
    /// Java/Kotlin String.hashCode(): h = 31*h + unit over UTF-16 code
    /// units, Int32 wraparound. Rendered as unsigned hex, last 8 chars,
    /// zero-padded — the engine's `toUInt().toString(16).takeLast(8)
    /// .padStart(8, '0')`.
    static func hash(itemKeys: [String]) -> String {
        let joined = itemKeys.sorted().joined(separator: "-")
        var h: Int32 = 0
        for unit in joined.utf16 {
            h = h &* 31 &+ Int32(unit)
        }
        let hex = String(UInt32(bitPattern: h), radix: 16)
        let padded = String(repeating: "0", count: max(0, 8 - hex.count)) + hex
        return String(padded.suffix(8))
    }
}

// MARK: - Personal combination

/// A personalized-hero cell: the pool's condition frame plus the outfit
/// signature, keyed stably per user.
struct PersonalCombination: Equatable, Hashable {
    let genderSegment: String
    let weather: HeroWeatherCondition
    let temp: HeroTempBracket
    let time: HeroTimeOfDay
    let outfitItems: [String]

    var outfitHash: String { OutfitSignatureHash.hash(itemKeys: outfitItems) }

    var combinationId: String {
        "\(genderSegment)_\(weather.rawValue)_\(temp.rawValue)_\(time.rawValue)_\(outfitHash)"
    }

    /// The gender segment for personal keying comes from the user's
    /// stated preference — MALE / FEMALE / UNISEX, the engine's own
    /// GenderPreference names. Deliberately NOT GenderPreference
    /// .forHeroImage(), whose unisex case randomizes per call: a
    /// personalized cell must key identically on every lookup.
    static func genderSegment(for preference: GenderPreference) -> String {
        switch preference {
        case .male: return "MALE"
        case .female: return "FEMALE"
        case .unisex: return "UNISEX"
        }
    }

    /// Build the cell for the currently displayed recommendation,
    /// from the SAME keying frame the pool hero and the
    /// recommendation use — `WeatherViewModel.prepareHeroFrame()`
    /// (comfort-adjusted feels-like, weather code, hour) — so a
    /// personalized photo is keyed to the exact outfit on screen.
    /// (The backend's plain `fromConditions` buckets the raw
    /// feels-like; this client follows the app's hero contract
    /// because the cell means "this recommendation". The choice is
    /// named in the RW-14 build report.)
    static func make(
        genderPreference: GenderPreference,
        weatherCode: Int,
        feelsLikeFahrenheit: Double,
        hour: Int,
        outfit: OutfitRecommendation
    ) -> PersonalCombination {
        PersonalCombination(
            genderSegment: genderSegment(for: genderPreference),
            weather: HeroWeatherCondition.from(weatherCode: weatherCode),
            temp: HeroTempBracket.from(feelsLikeTemperature: feelsLikeFahrenheit),
            time: HeroTimeOfDay.from(hour: hour),
            outfitItems: outfit.allItems.map { $0.key }
        )
    }
}

// MARK: - Backend DTOs

/// get-personal-hero response (backend §2.1).
struct PersonalHeroLookup: Decodable {
    let found: Bool
    let combinationId: String
    let heroId: String?
    let variant: Int?
    let imageUrl: String?
    let version: Int?
    let replenishmentQueued: Int?

    enum CodingKeys: String, CodingKey {
        case found
        case combinationId = "combination_id"
        case heroId = "hero_id"
        case variant
        case imageUrl = "image_url"
        case version
        case replenishmentQueued = "replenishment_queued"
    }
}

/// request-personal-generation success response (backend §2.2).
struct PersonalGenerationResponse: Decodable {
    let status: String // "already_available" | "already_queued" | "queued"
    let heroId: String?
    let jobId: String?
    let entitled: Bool?
    let freeGenerationsUsed: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case heroId = "hero_id"
        case jobId = "job_id"
        case entitled
        case freeGenerationsUsed = "free_generations_used"
    }
}

/// start-personal-fill response (backend §2.3).
struct PersonalFillResponse: Decodable {
    let ok: Bool
    let matrixCells: Int?
    let enqueued: Int?

    enum CodingKeys: String, CodingKey {
        case ok
        case matrixCells = "matrix_cells"
        case enqueued
    }
}

/// The user's own personal_profiles row, read via PostgREST under the
/// owner-SELECT policy (backend §1.1).
struct PersonalProfileRow: Decodable {
    let personalizationOn: Bool
    let freeGenerationsUsed: Int
    let entitled: Bool
    let gender: String?

    enum CodingKeys: String, CodingKey {
        case personalizationOn = "personalization_on"
        case freeGenerationsUsed = "free_generations_used"
        case entitled
        case gender
    }
}

/// Errors the backend returns as {"error": "<name>", ...}.
enum PersonalHeroAPIError: Error, Equatable {
    case allowanceExhausted(freeGenerationsUsed: Int)
    case consentRequired
    case referencesRequired
    case attestationsRequired
    case outfitSignatureMismatch
    case notEntitled
    /// The Supabase project has anonymous sign-ins disabled, so no
    /// user JWT can be minted (verified server gap, 2026-10-31).
    case authUnavailable
    case server(message: String)
    case http(Int)
    case unauthorized
}

/// The production consent text's version string, recorded with every
/// consent (backend §2.2 / §4.1). Bump when the consent copy changes.
enum PersonalConsent {
    static let version = "rw14-v1"
}

// MARK: - Versioned cell cache (acceptance RW-14 (16): the named trap)
//
// The server bumps a per-cell `version` whenever the cell's verified
// hero set changes. The client caches (version, signed URL) per cell.
// A re-lookup whose version EQUALS the cached version keeps the cached
// image — no visual churn, no re-download. A DIFFERENT version swaps
// the new image in. Signed URLs expire after 1 h server-side, so a
// cached entry older than `urlLifetime` is refreshed even at the same
// version (the content is identical; only the URL is re-signed).
struct PersonalCellCache {
    struct Entry: Equatable {
        let version: Int
        let imageURL: String
        let fetchedAt: Date
    }

    enum Resolution: Equatable {
        /// Show this URL — either freshly fetched or the cached one.
        case show(String)
        /// Nothing verified for this cell right now.
        case none
    }

    /// Signed URLs live 1 h on the server; refresh with margin.
    static let urlLifetime: TimeInterval = 50 * 60

    private(set) var entries: [String: Entry] = [:]

    /// Fold one get-personal-hero result into the cache and decide
    /// what the hero slot should show for this cell.
    mutating func resolve(lookup: PersonalHeroLookup, now: Date) -> Resolution {
        let cell = lookup.combinationId
        guard lookup.found, let url = lookup.imageUrl, let version = lookup.version else {
            return .none
        }
        if let existing = entries[cell],
           existing.version == version,
           now.timeIntervalSince(existing.fetchedAt) < Self.urlLifetime {
            return .show(existing.imageURL)
        }
        entries[cell] = Entry(version: version, imageURL: url, fetchedAt: now)
        return .show(url)
    }

    /// The cached URL for a cell, if still fresh — lets a re-display
    /// paint instantly while a revalidation lookup runs.
    func freshURL(for combinationId: String, now: Date) -> String? {
        guard let entry = entries[combinationId],
              now.timeIntervalSince(entry.fetchedAt) < Self.urlLifetime else { return nil }
        return entry.imageURL
    }

    mutating func removeAll() {
        entries.removeAll()
    }
}

// MARK: - Upsell eligibility (spec §5)
//
// The 6th-cell card shows only when ALL of these hold: the user is on
// the free tier; the allowance is fully spent; the CURRENT cell was
// refused with allowance_exhausted (i.e. it is a genuinely new cell,
// not one already holding a hero); and the frequency rules allow it —
// never twice for the same cell, and after any dismissal at most once
// a week.
enum UpsellEligibility {
    static let redisplayInterval: TimeInterval = 7 * 24 * 60 * 60

    static func shouldShow(
        isSubscribed: Bool,
        freeGenerationsUsed: Int,
        freeAllowance: Int,
        exhaustedCell: String?,
        currentCell: String,
        lastDismissedAt: Date?,
        dismissedCells: Set<String>,
        now: Date
    ) -> Bool {
        guard !isSubscribed else { return false }
        guard freeGenerationsUsed >= freeAllowance else { return false }
        guard exhaustedCell == currentCell else { return false }
        guard !dismissedCells.contains(currentCell) else { return false }
        if let last = lastDismissedAt,
           now.timeIntervalSince(last) < redisplayInterval {
            return false
        }
        return true
    }
}

// MARK: - Reference-photo quality verdict (spec §2.3 / strings C3)
//
// The Vision measurements are taken on device; the CLASSIFICATION of
// those measurements into the spec's rejection reasons is pure and
// tested here. Priority: the count problems first (a photo of two
// people is wrong however bright it is), then darkness, then framing.
enum PhotoRejection: Equatable {
    case faceUnclear      // C3a
    case multiplePeople   // C3b
    case tooDark          // C3c
    case tooClose         // C3d

    var specString: String {
        switch self {
        case .faceUnclear:
            return "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
        case .multiplePeople:
            return "This photo has more than one person in it. Pick one that's just you."
        case .tooDark:
            return "This one is too dark to use. Try a photo in brighter light."
        case .tooClose:
            return "This one is cropped too close. A head-and-shoulders or half-body photo works best."
        }
    }
}

enum PhotoQualityVerdict {
    /// Mean-luminance floor (0...1) below which a photo is "too dark".
    static let darkThreshold: Double = 0.18
    /// Largest-face area share above which the photo is "cropped too
    /// close" (a head-and-shoulders frame keeps the face well under
    /// half the frame).
    static let tooCloseThreshold: Double = 0.45

    static func rejection(
        faceCount: Int,
        largestFaceAreaRatio: Double,
        meanLuminance: Double
    ) -> PhotoRejection? {
        if faceCount == 0 { return .faceUnclear }
        if faceCount > 1 { return .multiplePeople }
        if meanLuminance < darkThreshold { return .tooDark }
        if largestFaceAreaRatio > tooCloseThreshold { return .tooClose }
        return nil
    }
}
