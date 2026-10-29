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

    /// Unsplash photo IDs organized by temperature and weather —
    /// an exact mirror of Android's FALLBACK_IMAGES_2D
    /// (HeroImageRepository): 22 populated cells. HOT and WARM have
    /// no SNOW cell on either platform; the lookup below falls back
    /// to the bracket's CLEAR image, as Android's does.
    ///
    /// The previous iOS table was a different, uncurated set: its
    /// HOT/CLOUDY cell was a desk with code on monitors (the photo
    /// Sol was served as a "Miami hero") and MILD/CLEAR was a lake at
    /// sunset. Both are gone.
    private let photoIds: [HeroTempBracket: [HeroWeatherCondition: String]] = [
        .FREEZING: [
            .CLEAR: "photo-1544899489-a083461b088c",   // Winter gear sunny
            .CLOUDY: "photo-1544899489-a083461b088c",  // Freezing overcast
            .RAIN: "photo-1519692933481-e162a57d6721", // Freezing rain/sleet
            .SNOW: "photo-1418985991508-e47386d96a71"  // Heavy snow
        ],
        .COLD: [
            .CLEAR: "photo-1517649763962-0c623066013b",   // Jacket sunny
            .CLOUDY: "photo-1517649763962-0c623066013b",  // Cold overcast
            .RAIN: "photo-1519692933481-e162a57d6721",    // Cold rain/sleet
            .SNOW: "photo-1483921020237-2ff51e8e4b22"     // Snowy run
        ],
        .COOL: [
            .CLEAR: "photo-1476480862126-209bfaa8edc8",   // Long sleeves sunny
            .CLOUDY: "photo-1571019613454-1cb2f99b2d8b",  // Cool overcast
            .RAIN: "photo-1515191107209-c28698631303",    // Cool rainy
            .SNOW: "photo-1491002052546-bf38f186af56"     // Cool snow
        ],
        .MILD: [
            .CLEAR: "photo-1552674605-db6ffd4facb5",   // Light layers sunny
            .CLOUDY: "photo-1558017487-06bf9f82613a",  // Mild overcast
            .RAIN: "photo-1515191107209-c28698631303", // Spring/fall rain
            .SNOW: "photo-1491002052546-bf38f186af56"  // Light snow
        ],
        .WARM: [
            .CLEAR: "photo-1486218119243-13883505764c",   // T-shirt sunny
            .CLOUDY: "photo-1558017487-06bf9f82613a",    // Warm overcast
            .RAIN: "photo-1534258936925-c58bed479fcb"     // Warm rain
        ],
        .HOT: [
            .CLEAR: "photo-1571008887538-b36bb32f4571",   // Tank top, bright sunny
            .CLOUDY: "photo-1571019613454-1cb2f99b2d8b",  // Summer overcast
            .RAIN: "photo-1534258936925-c58bed479fcb"     // Summer rain
        ]
    ]

    /// Returns a fallback Unsplash URL for the given conditions.
    /// Lookup mirrors Android's getWeatherAwareFallback: exact cell,
    /// then the bracket's CLEAR cell (e.g. HOT + SNOW), then
    /// MILD/CLEAR as the ultimate default.
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
        let photoId = photoIds[temp]?[weather]
            ?? photoIds[temp]?[.CLEAR]
            ?? photoIds[.MILD]?[.CLEAR]
            ?? "photo-1571008887538-b36bb32f4571"
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
