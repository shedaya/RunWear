import Foundation

// MARK: - Hero Image Enums

/// Weather condition categories for hero image selection
enum HeroWeatherCondition: String, Codable, CaseIterable {
    case CLEAR
    case CLOUDY
    case RAIN
    case SNOW

    /// Maps WMO weather codes to hero weather conditions
    /// - Parameter code: WMO weather code (0-99)
    /// - Returns: Corresponding HeroWeatherCondition
    static func from(weatherCode code: Int) -> HeroWeatherCondition {
        switch code {
        case 0, 1:
            return .CLEAR
        case 2, 3, 45, 48:
            return .CLOUDY
        case 51...67, 80...82, 95...99:
            return .RAIN
        case 71...77, 85, 86:
            return .SNOW
        default:
            return .CLEAR
        }
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

    /// Maps "feels like" temperature (Fahrenheit) to bracket
    /// - Parameter feelsLike: Feels like temperature in Fahrenheit
    /// - Returns: Corresponding HeroTempBracket
    static func from(feelsLikeTemperature feelsLike: Double) -> HeroTempBracket {
        switch feelsLike {
        case ..<20:
            return .FREEZING
        case 20..<35:
            return .COLD
        case 35..<50:
            return .COOL
        case 50..<65:
            return .MILD
        case 65..<80:
            return .WARM
        default:
            return .HOT
        }
    }
}

/// Time of day categories for hero image selection
enum HeroTimeOfDay: String, Codable, CaseIterable {
    case DAWN
    case MIDDAY
    case DUSK
    case NIGHT

    /// Maps hour of day to time category
    /// - Parameter hour: Hour in 24-hour format (0-23)
    /// - Returns: Corresponding HeroTimeOfDay
    static func from(hour: Int) -> HeroTimeOfDay {
        switch hour {
        case 5...9:
            return .DAWN
        case 10...16:
            return .MIDDAY
        case 17...19:
            return .DUSK
        default:
            return .NIGHT
        }
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

    enum CodingKeys: String, CodingKey {
        case id
        case combinationId = "combination_id"
        case imageUrl = "image_url"
        case thumbnailUrl = "thumbnail_url"
        case prompt
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
