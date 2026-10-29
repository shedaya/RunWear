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

    enum CodingKeys: String, CodingKey {
        case id
        case combinationId = "combination_id"
        case imageUrl = "image_url"
        case thumbnailUrl = "thumbnail_url"
        case prompt
        case reviewStatus = "review_status"
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

/// Result of hero image lookup with fallback information
struct HeroImageResult {
    let imageUrl: String
    let thumbnailUrl: String?
    let combinationId: String?
    let isFromFallback: Bool
    let fallbackLevel: Int // 0 = exact match, 1-5 = cascade level, 6 = Unsplash

    /// Creates a result from an exact or cascade match
    static func fromMatch(
        image: GeneratedImage,
        fallbackLevel: Int
    ) -> HeroImageResult {
        HeroImageResult(
            imageUrl: image.imageUrl,
            thumbnailUrl: image.thumbnailUrl,
            combinationId: image.combinationId,
            isFromFallback: fallbackLevel > 0,
            fallbackLevel: fallbackLevel
        )
    }

    /// Creates a result from Unsplash fallback
    static func fromUnsplash(url: String) -> HeroImageResult {
        HeroImageResult(
            imageUrl: url,
            thumbnailUrl: nil,
            combinationId: nil,
            isFromFallback: true,
            fallbackLevel: 6
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
}
