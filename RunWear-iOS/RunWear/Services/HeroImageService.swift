import Foundation

/// Service for fetching and managing hero images with cascade fallback
actor HeroImageService {
    static let shared = HeroImageService()

    private let supabase = SupabaseClient.shared
    private let fallbackProvider = FallbackImageProvider.shared

    private init() {}

    // MARK: - Main API

    /// Gets a hero image for the given conditions
    /// Uses cascade fallback (canonical levels 1-5, RW-23 legacy-token
    /// levels 6-7), ultimately falling back to Unsplash (level 8)
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
            isWatch: isWatch,
            outfit: outfit
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

    /// Attempts to fetch image with the cascade (levels 1-7)
    private func fetchWithCascade(
        combination: HeroCombinationId,
        isWatch: Bool,
        outfit: OutfitRecommendation?
    ) async -> HeroImageResult {
        // Level 1: Exact match (gender + weather + temp + time)
        if let result = await tryFetch(pattern: combination.exactPattern, level: 1, outfit: outfit) {
            return result
        }

        // Level 2: Any time of day
        if let result = await tryFetch(pattern: combination.anyTimePattern, level: 2, outfit: outfit) {
            return result
        }

        // Level 3: Clear weather fallback (if not already CLEAR)
        if combination.weather != .CLEAR {
            if let result = await tryFetch(pattern: combination.clearWeatherPattern, level: 3, outfit: outfit) {
                return result
            }
        }

        // Level 4: Opposite gender, same weather
        if let result = await tryFetch(pattern: combination.oppositeGenderPattern, level: 4, outfit: outfit) {
            return result
        }

        // Level 5: Opposite gender, clear weather
        if combination.weather != .CLEAR {
            if let result = await tryFetch(pattern: combination.oppositeGenderClearPattern, level: 5, outfit: outfit) {
                return result
            }
        }

        // RW-23 levels 6-7: legacy combination tokens (RAINY/SNOWY).
        // 523 pool rows carry the legacy spellings and no canonical
        // pattern above matches them; these levels rescue that stock
        // AFTER the canonical five, filtered by the same point-grain
        // policy (tryFetch applies it unchanged).
        if let legacyPattern = combination.legacyWeatherPattern {
            // Level 6: Legacy weather token, same gender, any time
            if let result = await tryFetch(pattern: legacyPattern, level: 6, outfit: outfit) {
                return result
            }
        }
        if let legacyOppositePattern = combination.legacyOppositeGenderPattern {
            // Level 7: Legacy weather token, opposite gender, any time
            if let result = await tryFetch(pattern: legacyOppositePattern, level: 7, outfit: outfit) {
                return result
            }
        }

        // Level 8: Bundled fallback — GEN-3 (WS-A(1)): with an
        // outfit, the terminus is the POINT-GRAIN GATED bundled
        // resolution (FallbackImageProvider.gatedAssetName — the
        // same gate the pool obeys, over the adjudicated garment
        // census of the 44 bundled assets). When no bundled asset
        // matches the prescription there is NO truthful bundled
        // photo for it: the result carries an empty URL and the
        // painting layer resolves its own gated fallback (the
        // phone view model paints the gated asset it resolved at
        // commit; the designed gradient is the terminus, never a
        // garment-mismatched photo — the W-4 defect). Without an
        // outfit there is no prescription to judge against, so the
        // legacy bracket resolution stands (RW-26: gender-aware).
        if let outfit {
            if let gated = fallbackProvider.getGatedFallbackResult(
                temp: combination.temp,
                weather: combination.weather,
                isWatch: isWatch,
                gender: combination.gender,
                outfit: outfit
            ) {
                return gated
            }
            return HeroImageResult.fromBundledFallback(
                url: "",
                assetFile: "",
                provenance: nil
            )
        }
        return fallbackProvider.getFallbackResult(
            temp: combination.temp,
            weather: combination.weather,
            isWatch: isWatch,
            gender: combination.gender
        )
    }

    /// Tries to fetch images matching a pattern
    ///
    /// RW-20 point-grain gate (mirrors Android's HeroPointGrainPolicy
    /// serving filter): of the review-servable rows at this level, only
    /// those whose adjudicated garments and person count agree with the
    /// exact prescription may be served; a level with no agreeing row
    /// returns nil so the cascade continues. Without an outfit there is
    /// no prescription to judge against, so no pool row is served —
    /// the same contract as Android's getHeroImage, which returns the
    /// bundled fallback when the outfit is missing.
    private func tryFetch(
        pattern: String,
        level: Int,
        outfit: OutfitRecommendation?
    ) async -> HeroImageResult? {
        guard let outfit else { return nil }
        do {
            let images = try await supabase.fetchGeneratedImages(pattern: pattern)
            let agreeing = images.filter {
                HeroPointGrainPolicy.matches(image: $0, outfit: outfit)
            }
            guard let image = agreeing.randomElement() else { return nil }
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
