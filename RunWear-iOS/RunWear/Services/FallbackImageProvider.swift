import Foundation

/// Provides Unsplash fallback images when Supabase images are unavailable
struct FallbackImageProvider {
    static let shared = FallbackImageProvider()

    private init() {}

    /// Base Unsplash URL format
    private let baseURL = "https://images.unsplash.com"

    /// Image size parameters
    enum ImageSize {
        case iOS      // 800x1200
        case watchOS  // 400x600

        var parameters: String {
            switch self {
            case .iOS: return "w=800&h=1200&fit=crop"
            case .watchOS: return "w=400&h=600&fit=crop"
            }
        }
    }

    /// Unsplash photo IDs organized by temperature and weather
    /// 24 unique images: 6 temps × 4 weather conditions
    private let photoIds: [HeroTempBracket: [HeroWeatherCondition: String]] = [
        .FREEZING: [
            .CLEAR: "photo-1483664852095-d6cc6870702d",   // Snowy clear mountain
            .CLOUDY: "photo-1491002052546-bf38f186af56",  // Overcast winter
            .RAIN: "photo-1519003722824-194d4455a60c",    // Winter rain
            .SNOW: "photo-1517299321609-52687d1bc55a"     // Heavy snow
        ],
        .COLD: [
            .CLEAR: "photo-1544829099-b9a0c07fad1a",      // Cold clear morning
            .CLOUDY: "photo-1485236715568-ddc5ee6ca227",  // Cold cloudy day
            .RAIN: "photo-1534274988757-a28bf1a57c17",    // Cold rain
            .SNOW: "photo-1418985991508-e47386d96a71"     // Light snow
        ],
        .COOL: [
            .CLEAR: "photo-1476400424721-e25994a9b1d3",   // Cool clear day
            .CLOUDY: "photo-1499346030926-9a72daac6c63",  // Cool overcast
            .RAIN: "photo-1515694346937-94d85e41e6f0",    // Light rain
            .SNOW: "photo-1491824989090-8cb88b5e00e7"     // Cool snow
        ],
        .MILD: [
            .CLEAR: "photo-1532274402911-5a369e4c4bb5",   // Perfect running weather
            .CLOUDY: "photo-1504701954957-2010ec3bcec1",  // Mild cloudy
            .RAIN: "photo-1534088568595-a066f410bcda",    // Mild rain
            .SNOW: "photo-1478827387698-1527781a4887"     // Rare mild snow
        ],
        .WARM: [
            .CLEAR: "photo-1523464862212-d6631e2ebc4a",   // Warm sunny day
            .CLOUDY: "photo-1504150558240-0b4fd8946624",  // Warm overcast
            .RAIN: "photo-1527766833261-b09c3163a791",    // Summer rain
            .SNOW: "photo-1518458028785-8fbcd101ebb9"     // Warm snow (rare)
        ],
        .HOT: [
            .CLEAR: "photo-1504714146340-959ca07e1f38",   // Hot sunny day
            .CLOUDY: "photo-1504639725590-34d0984388bd",  // Hot hazy
            .RAIN: "photo-1501691223387-dd0500403074",    // Hot rain/monsoon
            .SNOW: "photo-1504701954957-2010ec3bcec1"     // Hot snow (placeholder, very rare)
        ]
    ]

    /// Returns a fallback Unsplash URL for the given conditions
    /// - Parameters:
    ///   - temp: Temperature bracket
    ///   - weather: Weather condition
    ///   - size: Image size (iOS or watchOS)
    /// - Returns: Unsplash image URL
    func getImageURL(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        size: ImageSize = .iOS
    ) -> String {
        let photoId = photoIds[temp]?[weather] ?? photoIds[.MILD]?[.CLEAR] ?? "photo-1532274402911-5a369e4c4bb5"
        return "\(baseURL)/\(photoId)?\(size.parameters)"
    }

    /// Returns a HeroImageResult for fallback
    /// - Parameters:
    ///   - temp: Temperature bracket
    ///   - weather: Weather condition
    ///   - isWatch: Whether this is for watchOS
    /// - Returns: HeroImageResult with Unsplash URL
    func getFallbackResult(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        isWatch: Bool = false
    ) -> HeroImageResult {
        let size: ImageSize = isWatch ? .watchOS : .iOS
        let url = getImageURL(temp: temp, weather: weather, size: size)
        return HeroImageResult.fromUnsplash(url: url)
    }

    /// Returns a random fallback for the given temperature
    /// Used when specific weather doesn't matter
    func getRandomFallback(
        temp: HeroTempBracket,
        isWatch: Bool = false
    ) -> HeroImageResult {
        let weather = HeroWeatherCondition.allCases.randomElement() ?? .CLEAR
        return getFallbackResult(temp: temp, weather: weather, isWatch: isWatch)
    }
}
