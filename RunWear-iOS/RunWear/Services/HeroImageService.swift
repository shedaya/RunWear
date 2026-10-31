import Foundation

/// Service for fetching and managing hero images with cascade fallback
actor HeroImageService {
    static let shared = HeroImageService()

    private let supabase = SupabaseClient.shared
    private let fallbackProvider = FallbackImageProvider.shared

    private init() {}

    // MARK: - Main API

    /// Gets a hero image for the given conditions
    /// Uses 5-level cascade fallback, ultimately falling back to Unsplash
    /// - Parameters:
    ///   - gender: User's gender preference
    ///   - weather: Current weather code
    ///   - feelsLikeTemp: Feels-like temperature in Fahrenheit
    ///   - hour: Hour of day (0-23)
    ///   - isWatch: Whether this is for watchOS (smaller image)
    ///   - outfit: Optional outfit recommendation for dynamic prompt generation
    /// - Returns: HeroImageResult with image URL and fallback info
    func getHeroImage(
        gender: GenderPreference,
        weatherCode: Int,
        feelsLikeTemp: Double,
        hour: Int,
        isWatch: Bool = false,
        outfit: OutfitRecommendation? = nil
    ) async -> HeroImageResult {
        // Convert to hero-specific types
        let heroGender = gender.forHeroImage()
        let heroWeather = HeroWeatherCondition.from(weatherCode: weatherCode)
        let heroTemp = HeroTempBracket.from(feelsLikeTemperature: feelsLikeTemp)
        let heroTime = HeroTimeOfDay.from(hour: hour)

        // Build combination ID helper
        let combination = HeroCombinationId(
            gender: heroGender,
            weather: heroWeather,
            temp: heroTemp,
            time: heroTime,
            variant: nil
        )

        // Try cascade fallback
        let result = await fetchWithCascade(
            combination: combination,
            isWatch: isWatch
        )

        // RW-14 (BACKLOG acceptance (11)): the legacy generic
        // queue-on-low path is REMOVED. The generic pool no longer
        // grows from client browse behavior — pool curation is a
        // server-side studio process, and personalized growth lives
        // in the per-user pipeline (PersonalHeroStore), which queues
        // its own gated replenishment per personalized serve.

        return result
    }

    // MARK: - Cascade Fallback

    /// Attempts to fetch image with 5-level cascade
    private func fetchWithCascade(
        combination: HeroCombinationId,
        isWatch: Bool
    ) async -> HeroImageResult {
        // Level 1: Exact match (gender + weather + temp + time)
        if let result = await tryFetch(pattern: combination.exactPattern, level: 1) {
            return result
        }

        // Level 2: Any time of day
        if let result = await tryFetch(pattern: combination.anyTimePattern, level: 2) {
            return result
        }

        // Level 3: Clear weather fallback (if not already CLEAR)
        if combination.weather != .CLEAR {
            if let result = await tryFetch(pattern: combination.clearWeatherPattern, level: 3) {
                return result
            }
        }

        // Level 4: Opposite gender, same weather
        if let result = await tryFetch(pattern: combination.oppositeGenderPattern, level: 4) {
            return result
        }

        // Level 5: Opposite gender, clear weather
        if combination.weather != .CLEAR {
            if let result = await tryFetch(pattern: combination.oppositeGenderClearPattern, level: 5) {
                return result
            }
        }

        // Level 6: Unsplash fallback
        return fallbackProvider.getFallbackResult(
            temp: combination.temp,
            weather: combination.weather,
            isWatch: isWatch
        )
    }

    /// Tries to fetch images matching a pattern
    private func tryFetch(pattern: String, level: Int) async -> HeroImageResult? {
        do {
            let images = try await supabase.fetchGeneratedImages(pattern: pattern)
            guard let image = images.randomElement() else { return nil }
            return HeroImageResult.fromMatch(image: image, fallbackLevel: level)
        } catch {
            print("HeroImageService: Failed to fetch pattern '\(pattern)': \(error)")
            return nil
        }
    }



}

// MARK: - Convenience Extension

extension HeroImageService {
    /// Gets hero image using HourlyWeatherSnapshot
    func getHeroImage(
        gender: GenderPreference,
        weather: HourlyWeatherSnapshot,
        isWatch: Bool = false,
        outfit: OutfitRecommendation? = nil
    ) async -> HeroImageResult {
        await getHeroImage(
            gender: gender,
            weatherCode: weather.weatherCode,
            feelsLikeTemp: weather.feelsLikeFahrenheit,
            hour: weather.hour,
            isWatch: isWatch,
            outfit: outfit
        )
    }
}
