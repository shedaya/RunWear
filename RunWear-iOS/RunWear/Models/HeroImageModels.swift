import Foundation
import RunWearEngine

// MARK: - Hero Image Enums
//
// Stage 2 (2026-10-29): the classification mappings below (WMO code →
// condition, feels-like → bracket, hour → time of day) are DELEGATED to
// the shared Kotlin module (HeroFrameMapping in `:engine`) — the tables
// live only there now, shared with Android's OutfitCombination. The
// Swift enums remain as this platform's presentation types; their case
// names are the Kotlin enum names, so a mapping result crosses the seam
// as a name. The fallbacks mirror the Kotlin tables' own fallbacks
// (UNKNOWN code → CLEAR, out-of-range temp → MILD, unmatched hour →
// MIDDAY) and exist only so an impossible mismatch degrades the way
// Android degrades instead of trapping.

/// Weather condition categories for hero image selection
enum HeroWeatherCondition: String, Codable, CaseIterable {
    case CLEAR
    case CLOUDY
    case RAIN
    case SNOW

    /// Maps WMO weather codes to hero weather conditions (shared module)
    /// - Parameter code: WMO weather code (0-99)
    /// - Returns: Corresponding HeroWeatherCondition
    static func from(weatherCode code: Int) -> HeroWeatherCondition {
        let name = HeroFrameMapping.shared.weatherConditionName(weatherCode: Int32(code))
        return HeroWeatherCondition(rawValue: name) ?? .CLEAR
    }
}

/// Temperature brackets for hero image selection
enum HeroTempBracket: String, Codable, CaseIterable {
    case FREEZING
    case COLD
    case COOL
    case MILD
    case WARM
    case HOT

    /// Maps "feels like" temperature (Fahrenheit) to bracket (shared module)
    /// - Parameter feelsLike: Feels like temperature in Fahrenheit
    /// - Returns: Corresponding HeroTempBracket
    static func from(feelsLikeTemperature feelsLike: Double) -> HeroTempBracket {
        let name = HeroFrameMapping.shared.tempBracketName(feelsLikeF: feelsLike)
        return HeroTempBracket(rawValue: name) ?? .MILD
    }
}

/// Time of day categories for hero image selection
enum HeroTimeOfDay: String, Codable, CaseIterable {
    case DAWN
    case MIDDAY
    case DUSK
    case NIGHT

    /// Maps hour of day to time category (shared module)
    /// - Parameter hour: Hour in 24-hour format (0-23)
    /// - Returns: Corresponding HeroTimeOfDay
    static func from(hour: Int) -> HeroTimeOfDay {
        let name = HeroFrameMapping.shared.timeOfDayName(hour: Int32(hour))
        return HeroTimeOfDay(rawValue: name) ?? .MIDDAY
    }
}

/// Gender for hero image selection (distinct from user preference)
enum HeroGender: String, Codable, CaseIterable {
    case MALE
    case FEMALE

    /// Returns the opposite gender for fallback cascade
    var opposite: HeroGender {
        switch self {
        case .MALE: return .FEMALE
        case .FEMALE: return .MALE
        }
    }
}

// MARK: - Hero Image Data Structures

/// The adjudicated garment classes of a pool photo (RW-20 backfill,
/// 2026-11-02): what the photo actually shows, per the RW-16.17 audit /
/// regen adjudication, stored on the row as garment_classes. Keys are
/// the audit vocabulary (camelCase in the JSON): bottom ∈ {shorts,
/// short_shorts, capris, tights}, topBase ∈ {sleeveless, short, long},
/// head ∈ {balaclava, beanie, cap, headband, visor}, hands ∈ {gloves,
/// mittens}; nil = not readable in the photo.
struct GarmentClasses: Codable {
    let topBase: String?
    let outer: Bool?
    let mid: Bool?
    let bottom: String?
    let head: String?
    let hands: String?
    let sunglasses: Bool?
    let gaiter: Bool?
}

/// Represents a generated AI image from Supabase
struct GeneratedImage: Codable, Identifiable {
    let id: String
    let combinationId: String
    let imageUrl: String
    let thumbnailUrl: String?
    let prompt: String?
    /// Review-gate status from the 2026-10-27 migration
    /// ('approved' | 'pending' | 'rejected'); nil on legacy rows.
    let reviewStatus: String?
    /// RW-20 backfill: adjudicated person count; nil when unrecorded.
    let personCount: Int?
    /// RW-20 backfill: adjudicated garment classes (see GarmentClasses).
    let garmentClasses: GarmentClasses?
    /// 'adjudicated' (photo-derived) or 'prompt' (stored-prompt-derived).
    let garmentSource: String?

    /// Explicit initializer (the memberwise one would do, but the
    /// RW-20 columns need defaults so pre-backfill construction sites
    /// keep compiling unchanged; synthesized Codable decoding does not
    /// rely on these defaults — absent keys decode as nil).
    init(
        id: String,
        combinationId: String,
        imageUrl: String,
        thumbnailUrl: String?,
        prompt: String?,
        reviewStatus: String?,
        personCount: Int? = nil,
        garmentClasses: GarmentClasses? = nil,
        garmentSource: String? = nil
    ) {
        self.id = id
        self.combinationId = combinationId
        self.imageUrl = imageUrl
        self.thumbnailUrl = thumbnailUrl
        self.prompt = prompt
        self.reviewStatus = reviewStatus
        self.personCount = personCount
        self.garmentClasses = garmentClasses
        self.garmentSource = garmentSource
    }

    enum CodingKeys: String, CodingKey {
        case id
        case combinationId = "combination_id"
        case imageUrl = "image_url"
        case thumbnailUrl = "thumbnail_url"
        case prompt
        case reviewStatus = "review_status"
        case personCount = "person_count"
        case garmentClasses = "garment_classes"
        case garmentSource = "garment_source"
    }
}

/// Delegates to the shared module's HeroPoolPolicy: only approved
/// images may be served; a null status is a pre-migration legacy row
/// and counts as approved.
enum HeroPoolPolicy {
    static func isServable(reviewStatus: String?) -> Bool {
        RunWearEngine.HeroPoolPolicy.shared.isServable(reviewStatus: reviewStatus)
    }
}

/// RW-20 point-grain serving filter — delegates to the shared module's
/// HeroPointGrainPolicy (the same code Android serves through), via its
/// key-based bridge entry point: Swift holds prescribed items as
/// enum-case keys (ClothingItem.key) and an unrecorded person count
/// crosses as a negative number. A pool photo may be served only when
/// its adjudicated garments and person count agree with the exact
/// prescription for the current conditions (inside COOL: shorts only
/// at 45°F+, tights below; a shell only when prescribed).
/// RW-23 (2026-11-02): head and hands are enforced too — no waiver.
/// A prescribed head/hands item requires its exact recorded class
/// (BASEBALL_CAP→"cap", LIGHT_GLOVES→"gloves", …), and when none is
/// prescribed the row must record none; the Juneau field failure
/// (four bare-handed photos served under a LIGHT_GLOVES prescription)
/// is what the waiver let through.
enum HeroPointGrainPolicy {
    static func matches(image: GeneratedImage, outfit: OutfitRecommendation) -> Bool {
        RunWearEngine.HeroPointGrainPolicy.shared.matchesByKey(
            personCount: Int32(image.personCount ?? -1),
            topBaseClass: image.garmentClasses?.topBase,
            outerClass: image.garmentClasses?.outer.map { $0 ? "true" : "false" },
            bottomClass: image.garmentClasses?.bottom,
            prescribedTopBaseKey: outfit.topBase.key,
            prescribedTopOuterKey: outfit.topOuter?.key,
            prescribedBottomKey: outfit.bottom.key,
            headClass: image.garmentClasses?.head,
            handsClass: image.garmentClasses?.hands,
            prescribedHeadKey: outfit.head?.key,
            prescribedHandsKey: outfit.hands?.key
        )
    }
}

// MARK: - Serve-source taxonomy (WS-7 / RW-26(c))

/// WS-7 / RW-26(c): the layer that served a hero paint. The wire
/// names are the serve-source vocabulary of record — identical to
/// Android's HeroServeSource: pool / bundled-fallback / personal /
/// scaffold.
enum HeroServeSource: String, Equatable {
    case pool = "pool"
    case bundledFallback = "bundled-fallback"
    case personal = "personal"
    case scaffold = "scaffold"

    var wire: String { rawValue }
}

/// GEN-4 (hero-fallback lane, 2026-11-04): WHERE a stock hero
/// resolution landed in the fallback chain — the chain's tiers,
/// in serving order (see HeroFallbackChain in
/// FallbackImageProvider.swift for the full definition):
///  - exact: a candidate (pool row or bundled asset) whose
///    adjudicated garments PASS the point-grain gate for the
///    prescription's signature — the photo may claim the outfit.
///  - scaffold: no exact candidate exists; the nearest covered
///    same-gender photo serves as a scaffold while the exact
///    photo is generated/replenished. A scaffold is recorded
///    with serve source `scaffold` (the design's existing
///    non-exact convention — the HeroServeSource.scaffold wire
///    value, the same source the personal bank's scaffold serves
///    already record) and is NEVER recorded or presented as the
///    exact photo. There is NO stock-scaffold UI copy in the
///    design: the personal scaffold's words ("Your photo" /
///    "This look was made for similar weather…") describe the
///    user's OWN banked photo and are not reused for stock.
///  - bundledFloor: no live pool consultation was available
///    (pool unreachable/empty at this layer, or the synchronous
///    pre-paint commit, which paints before any pool answer
///    exists); the nearest BUNDLED asset under the same metric
///    serves. Recorded as a bundled-fallback serve, exactly as
///    bundled serves have always been recorded.
enum HeroFallbackTier: String, Equatable {
    case exact = "exact"
    case scaffold = "scaffold"
    case bundledFloor = "bundled-floor"

    var wire: String { rawValue }
}

/// WS-7 / D-22: point-grain standing for one bundled fallback asset —
/// the precomputed prescription verdict the pool obeys, recorded per
/// slot in the staged RW-26 bundle manifest
/// (release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md). `rung`
/// is the manifest's rung: T1 = strict rung 1 (every garment class
/// legible and exact at the cell's cold-end point prescription);
/// T3 = recalibrated rung 1 (every legible class exact incl. head and
/// hands; base unperceived under a present shell — Sol's Nov 2 gate
/// rulings 2 + 5). A bundled serve carries this record, so it is
/// auditable exactly like a pool serve and never exempt from the
/// outfit rules by construction. Mirror of Android's
/// FallbackAssetProvenance.
struct FallbackAssetProvenance: Equatable {
    let combinationId: String
    let sourceRowId: String
    let rung: String
}

/// WS-7 / RW-26(c): one hero serve-source record — the attributable
/// record emitted at serve time for every hero paint. `summary()` is
/// the single-line form the diagnostics channel carries, in the same
/// key order and spelling as Android's HeroServeRecord.summary().
struct HeroServeRecord: Equatable {
    let source: HeroServeSource
    var requestedCombinationId: String? = nil
    var servedCombinationId: String? = nil
    var imageRowId: String? = nil
    var tempBracket: HeroTempBracket? = nil
    var weatherCondition: HeroWeatherCondition? = nil
    var gender: GenderPreference? = nil
    var fallbackAssetFile: String? = nil
    var fallbackProvenance: FallbackAssetProvenance? = nil
    var trigger: String? = nil
    /// GEN-4: the fallback-chain tier of this serve, when the
    /// GEN-4 chain produced it — the record-level honest label:
    /// a scaffold serve's summary says `tier=scaffold`, so it can
    /// never be read back as an exact serve.
    var fallbackTier: HeroFallbackTier? = nil

    func summary() -> String {
        var parts: [String] = ["hero_serve source=\(source.wire)"]
        if let fallbackTier { parts.append("tier=\(fallbackTier.wire)") }
        if let tempBracket { parts.append("bracket=\(tempBracket.rawValue)") }
        if let weatherCondition { parts.append("weather=\(weatherCondition.rawValue)") }
        if let gender { parts.append("gender=\(gender.rawValue.uppercased())") }
        if let requestedCombinationId { parts.append("requested=\(requestedCombinationId)") }
        if let servedCombinationId { parts.append("served=\(servedCombinationId)") }
        if let imageRowId { parts.append("row=\(imageRowId)") }
        if let fallbackAssetFile { parts.append("asset=\(fallbackAssetFile)") }
        if let fallbackProvenance {
            parts.append("provenance_combination=\(fallbackProvenance.combinationId)")
            parts.append("provenance_row=\(fallbackProvenance.sourceRowId)")
            parts.append("rung=\(fallbackProvenance.rung)")
        }
        if let trigger { parts.append("trigger=\(trigger)") }
        return parts.joined(separator: " ")
    }
}

/// Result of hero image lookup with fallback information
struct HeroImageResult {
    let imageUrl: String
    let thumbnailUrl: String?
    /// The served row's own combination_id (pool matches only; can
    /// differ from the requested combination when the cascade fell
    /// back to a neighbouring cell).
    let combinationId: String?
    /// The served row's `generated_images.id` (pool matches only).
    /// Carried so a hero-photo flag can name the exact photo —
    /// before 2026-10-29 the cascade discarded it and only a URL
    /// reached the UI (the same gap Android's flag work closed).
    let imageRowId: String?
    let isFromFallback: Bool
    let fallbackLevel: Int // 0 = exact match, 1-7 = cascade level, 8 = Unsplash
    /// WS-7 / RW-26(c): which layer served this hero — recorded on
    /// the result so every paint can emit its serve-source record
    /// (D-18's attribution fix). Nil only for synthetic results that
    /// never reached a serving layer.
    var serveSource: HeroServeSource? = nil
    /// WS-7 / D-22: for a bundled-fallback serve, the manifest
    /// provenance of the exact asset served (its pool source
    /// combination + row and its point-grain rung verdict). Nil for
    /// pool and personal serves, whose identity rides in `imageRowId`
    /// / `combinationId`.
    var fallbackProvenance: FallbackAssetProvenance? = nil
    /// WS-7: the bundled asset file base name served (no extension),
    /// for a bundled serve.
    var fallbackAssetFile: String? = nil
    /// GEN-4: the fallback-chain tier this result resolved at
    /// (HeroFallbackTier), when the GEN-4 chain produced it. Nil
    /// for results from the legacy paths (the no-prescription
    /// bracket resolution, personal serves).
    var fallbackTier: HeroFallbackTier? = nil

    /// GEN-4: true iff this result is a scaffold serve — the
    /// honest-labelling seam every consumer can read instead of
    /// re-deriving tier from the serve source.
    var isScaffold: Bool { fallbackTier == .scaffold }

    /// Creates a result from an exact or cascade match
    static func fromMatch(
        image: GeneratedImage,
        fallbackLevel: Int
    ) -> HeroImageResult {
        HeroImageResult(
            imageUrl: image.imageUrl,
            thumbnailUrl: image.thumbnailUrl,
            combinationId: image.combinationId,
            imageRowId: image.id,
            isFromFallback: fallbackLevel > 0,
            fallbackLevel: fallbackLevel,
            serveSource: .pool
        )
    }

    /// Creates a result from Unsplash fallback
    static func fromUnsplash(url: String) -> HeroImageResult {
        HeroImageResult(
            imageUrl: url,
            thumbnailUrl: nil,
            combinationId: nil,
            imageRowId: nil,
            isFromFallback: true,
            fallbackLevel: 8
        )
    }

    /// WS-7: creates a result for a bundled-fallback serve (cascade
    /// level 8's successor — the bundled matrix, not Unsplash). The
    /// result carries its serve source, the asset file served, and
    /// that asset's manifest provenance (D-22), so the paint layer
    /// can emit the serve record without re-deriving any of it.
    /// GEN-4: `tier` records where the fallback chain resolved —
    /// a scaffold tier flips the serve source to .scaffold (the
    /// existing non-exact recording convention); exact and floor
    /// tiers keep the bundled-fallback source bundled serves have
    /// always carried.
    static func fromBundledFallback(
        url: String,
        assetFile: String,
        provenance: FallbackAssetProvenance?,
        tier: HeroFallbackTier? = nil
    ) -> HeroImageResult {
        HeroImageResult(
            imageUrl: url,
            thumbnailUrl: nil,
            combinationId: nil,
            imageRowId: nil,
            isFromFallback: true,
            fallbackLevel: 8,
            serveSource: tier == .scaffold ? .scaffold : .bundledFallback,
            fallbackProvenance: provenance,
            fallbackAssetFile: assetFile,
            fallbackTier: tier
        )
    }

    /// GEN-4: creates a result for a SCAFFOLD pool serve — a live
    /// approved pool row that did NOT pass the point-grain gate
    /// for the prescription but is the nearest covered photo
    /// under the chain metric. It carries the row's OWN identity
    /// (its combination and row id — the photo it actually is,
    /// never the requested cell's), serve source .scaffold, and
    /// fallback level 9: beyond the exact cascade (1–7) and the
    /// bundled terminus (8), so every existing `fallbackLevel < 8`
    /// exact-pool check stays false for it and consumers must
    /// opt into the scaffold explicitly.
    static func fromScaffoldPool(image: GeneratedImage) -> HeroImageResult {
        HeroImageResult(
            imageUrl: image.imageUrl,
            thumbnailUrl: image.thumbnailUrl,
            combinationId: image.combinationId,
            imageRowId: image.id,
            isFromFallback: true,
            fallbackLevel: 9,
            serveSource: .scaffold,
            fallbackTier: .scaffold
        )
    }
}

/// Job status for image generation queue
enum GenerationJobStatus: String, Codable {
    case QUEUED
    case PROCESSING
    case COMPLETED
    case FAILED
}

/// Represents a generation job in the queue
struct GenerationJob: Codable {
    let combinationId: String
    let prompt: String
    let status: String

    enum CodingKeys: String, CodingKey {
        case combinationId = "combination_id"
        case prompt
        case status
    }
}

// MARK: - Combination ID Builder

/// Builds combination IDs for hero image queries
struct HeroCombinationId {
    let gender: HeroGender
    let weather: HeroWeatherCondition
    let temp: HeroTempBracket
    let time: HeroTimeOfDay?
    let variant: Int?

    /// Full combination ID string
    var fullId: String {
        var id = "\(gender.rawValue)_\(weather.rawValue)_\(temp.rawValue)"
        if let time = time {
            id += "_\(time.rawValue)"
        }
        if let variant = variant {
            id += "_v\(variant)"
        }
        return id
    }

    /// Pattern for LIKE query (exact match with any variant)
    var exactPattern: String {
        "\(gender.rawValue)_\(weather.rawValue)_\(temp.rawValue)_\(time?.rawValue ?? "")_%"
    }

    /// Pattern for any time of day
    var anyTimePattern: String {
        "\(gender.rawValue)_\(weather.rawValue)_\(temp.rawValue)_%"
    }

    /// Pattern with CLEAR weather fallback
    var clearWeatherPattern: String {
        "\(gender.rawValue)_CLEAR_\(temp.rawValue)_%"
    }

    /// Pattern with opposite gender
    var oppositeGenderPattern: String {
        "\(gender.opposite.rawValue)_\(weather.rawValue)_\(temp.rawValue)_%"
    }

    /// Pattern with opposite gender and CLEAR weather
    var oppositeGenderClearPattern: String {
        "\(gender.opposite.rawValue)_CLEAR_\(temp.rawValue)_%"
    }

    /// RW-23: the legacy combination-token name for this weather, if
    /// any. 523 pool rows carry RAINY/SNOWY tokens written before the
    /// canonical RAIN/SNOW names settled; no canonical cascade pattern
    /// matches them, stranding that stock. Canonical conditions with
    /// no legacy spelling return nil and skip the legacy levels.
    var legacyWeatherName: String? {
        switch weather {
        case .RAIN: return "RAINY"
        case .SNOW: return "SNOWY"
        case .CLEAR, .CLOUDY: return nil
        }
    }

    /// Legacy-weather pattern, any time of day (like levels 2/4)
    var legacyWeatherPattern: String? {
        guard let legacyWeatherName else { return nil }
        return "\(gender.rawValue)_\(legacyWeatherName)_\(temp.rawValue)_%"
    }

    /// Legacy-weather pattern with opposite gender, any time of day
    var legacyOppositeGenderPattern: String? {
        guard let legacyWeatherName else { return nil }
        return "\(gender.opposite.rawValue)_\(legacyWeatherName)_\(temp.rawValue)_%"
    }
}
