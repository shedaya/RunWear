import Foundation

/// Provides fallback hero images when Supabase pool images are unavailable.
///
/// Since 2026-10-30 the fallbacks are the SAME 22 approved AI pool photos
/// the Android app bundles, shipped inside the app bundle under
/// `hero_fallback/` — no more remote Unsplash stock. Cell-for-cell
/// identical to Android's WebP fallback table (and the PWA's).
struct FallbackImageProvider {
    static let shared = FallbackImageProvider()

    private init() {}

    /// Kept for call-site compatibility. The bundled photos are a single
    /// 800x1200 size serving both iOS and watchOS (the watch downscales),
    /// so the size no longer changes the returned URL.
    enum ImageSize {
        case iOS      // 800x1200
        case watchOS  // 400x600
    }

    /// Bundled asset file names organized by temperature and weather —
    /// an exact mirror of Android's FALLBACK_IMAGES (22 populated cells).
    /// HOT and WARM have no SNOW cell on any platform; the lookup below
    /// falls back to the bracket's CLEAR image, as Android's does.
    ///
    /// The previous iOS table pointed at Unsplash stock photos and had
    /// drifted from the pool (its HOT/CLOUDY cell was once a desk with
    /// code on monitors, served to Sol as a "Miami hero"). Gone.
    private let assetNames: [HeroTempBracket: [HeroWeatherCondition: String]] = [
        .FREEZING: [
            .CLEAR: "freezing_clear",   // MALE_CLEAR_FREEZING_DUSK_v1160
            .CLOUDY: "freezing_cloudy",  // FEMALE_CLOUDY_FREEZING_MIDDAY_v1177
            .RAIN: "freezing_rain", // FEMALE_RAINY_FREEZING_MIDDAY_v5
            .SNOW: "freezing_snow"  // MALE_SNOWY_FREEZING_NIGHT_v1
        ],
        .COLD: [
            .CLEAR: "cold_clear",   // UNISEX_CLEAR_COLD_DUSK_v4
            .CLOUDY: "cold_cloudy",  // FEMALE_CLOUDY_COLD_MIDDAY_v3
            .RAIN: "cold_rain",    // MALE_RAIN_COLD_MIDDAY_v2
            .SNOW: "cold_snow"     // FEMALE_SNOW_COLD_DUSK_v5
        ],
        .COOL: [
            .CLEAR: "cool_clear",   // MALE_CLEAR_COOL_DUSK_v2
            .CLOUDY: "cool_cloudy",  // FEMALE_CLOUDY_COOL_MIDDAY
            .RAIN: "cool_rain",    // FEMALE_RAIN_COOL_MIDDAY_v3
            .SNOW: "cool_snow"     // FEMALE_SNOW_COOL_MIDDAY_v3
        ],
        .MILD: [
            .CLEAR: "mild_clear",   // FEMALE_CLEAR_MILD_DUSK_v6
            .CLOUDY: "mild_cloudy",  // FEMALE_CLOUDY_MILD_MIDDAY_v3
            .RAIN: "mild_rain", // FEMALE_RAIN_MILD_DUSK_v4
            .SNOW: "mild_snow"  // FEMALE_SNOWY_MILD_MIDDAY_v3
        ],
        .WARM: [
            .CLEAR: "warm_clear",   // MALE_CLEAR_WARM_MIDDAY_v3
            .CLOUDY: "warm_cloudy",    // MALE_CLOUDY_WARM_DAWN_v5
            .RAIN: "warm_rain"     // MALE_RAINY_WARM_DUSK_v5
        ],
        .HOT: [
            .CLEAR: "hot_clear",   // MALE_CLEAR_HOT_MIDDAY_v1070
            .CLOUDY: "hot_cloudy",  // MALE_CLOUDY_HOT_DUSK_v2
            .RAIN: "hot_rain"     // FEMALE_RAINY_HOT_DAWN_v6
        ]
    ]

    /// Resolves a bundled fallback asset to a file URL string.
    private func bundledURLString(assetName: String) -> String? {
        Bundle.main.url(
            forResource: assetName,
            withExtension: "webp",
            subdirectory: "hero_fallback"
        )?.absoluteString
    }

    /// Returns a fallback image URL for the given conditions.
    /// Lookup mirrors Android's getWeatherAwareFallback: exact cell,
    /// then the bracket's CLEAR cell (e.g. HOT + SNOW), then
    /// MILD/CLEAR as the ultimate default.
    /// - Parameters:
    ///   - temp: Temperature bracket
    ///   - weather: Weather condition
    ///   - size: Image size (kept for compatibility; one bundled size)
    /// - Returns: file URL string of the bundled pool photo
    func getImageURL(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        size: ImageSize = .iOS
    ) -> String {
        let name = assetNames[temp]?[weather]
            ?? assetNames[temp]?[.CLEAR]
            ?? assetNames[.MILD]?[.CLEAR]
            ?? "mild_clear"
        return bundledURLString(assetName: name)
            ?? bundledURLString(assetName: "mild_clear")
            ?? ""
    }

    /// Returns a HeroImageResult for fallback
    /// - Parameters:
    ///   - temp: Temperature bracket
    ///   - weather: Weather condition
    ///   - isWatch: Whether this is for watchOS
    /// - Returns: HeroImageResult with the bundled fallback URL
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
