import Foundation

/// Service for fetching and managing hero images with cascade fallback
actor HeroImageService {
    static let shared = HeroImageService()

    private let supabase = SupabaseClient.shared
    private let fallbackProvider = FallbackImageProvider.shared

    /// Minimum variants before triggering replenishment
    private let minVariantCount = 5

    /// Rate limiting: track last queue time per user/device
    private var lastQueueTime: Date?
    private let queueCooldown: TimeInterval = 5 * 60 // 5 minutes

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
    /// - Returns: HeroImageResult with image URL and fallback info
    func getHeroImage(
        gender: GenderPreference,
        weatherCode: Int,
        feelsLikeTemp: Double,
        hour: Int,
        isWatch: Bool = false
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

        // Queue replenishment if needed (non-blocking)
        Task {
            await queueReplenishmentIfNeeded(
                combination: combination,
                result: result
            )
        }

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

    // MARK: - Demand-Driven Replenishment

    /// Queues image generation if needed based on result
    private func queueReplenishmentIfNeeded(
        combination: HeroCombinationId,
        result: HeroImageResult
    ) async {
        // Check rate limit
        if let lastTime = lastQueueTime,
           Date().timeIntervalSince(lastTime) < queueCooldown {
            return
        }

        // Determine if we should queue
        let shouldQueue: Bool

        switch result.fallbackLevel {
        case 0, 1:
            // Exact match - check if we need more variants
            do {
                let count = try await supabase.countExistingVariants(
                    prefix: "\(combination.gender.rawValue)_\(combination.weather.rawValue)_\(combination.temp.rawValue)_\(combination.time?.rawValue ?? "")"
                )
                shouldQueue = count < minVariantCount
            } catch {
                shouldQueue = false
            }
        case 2...5:
            // Found via fallback - queue for exact match
            shouldQueue = true
        case 6:
            // Unsplash fallback - definitely queue
            shouldQueue = true
        default:
            shouldQueue = false
        }

        if shouldQueue {
            await queueGeneration(for: combination)
        }
    }

    /// Queues a generation job
    private func queueGeneration(for combination: HeroCombinationId) async {
        // Find next variant number
        let variantNumber: Int
        do {
            let existingCount = try await supabase.countExistingVariants(
                prefix: "\(combination.gender.rawValue)_\(combination.weather.rawValue)_\(combination.temp.rawValue)_\(combination.time?.rawValue ?? "")"
            )
            variantNumber = existingCount + 1
        } catch {
            variantNumber = 1
        }

        // Build combination ID with variant
        let fullCombination = HeroCombinationId(
            gender: combination.gender,
            weather: combination.weather,
            temp: combination.temp,
            time: combination.time,
            variant: variantNumber
        )

        // Build prompt
        let prompt = buildPrompt(for: fullCombination)

        do {
            try await supabase.insertGenerationJob(
                combinationId: fullCombination.fullId,
                prompt: prompt,
                status: "QUEUED"
            )
            lastQueueTime = Date()
            print("HeroImageService: Queued generation for \(fullCombination.fullId)")
        } catch {
            print("HeroImageService: Failed to queue generation: \(error)")
        }
    }

    /// Builds a generation prompt for the combination
    private func buildPrompt(for combination: HeroCombinationId) -> String {
        let genderText = combination.gender == .MALE ? "male" : "female"
        let weatherText: String
        switch combination.weather {
        case .CLEAR: weatherText = "clear sunny"
        case .CLOUDY: weatherText = "cloudy overcast"
        case .RAIN: weatherText = "rainy wet"
        case .SNOW: weatherText = "snowy winter"
        }

        let tempText: String
        switch combination.temp {
        case .FREEZING: tempText = "extremely cold freezing"
        case .COLD: tempText = "cold winter"
        case .COOL: tempText = "cool crisp"
        case .MILD: tempText = "mild pleasant"
        case .WARM: tempText = "warm comfortable"
        case .HOT: tempText = "hot summer"
        }

        let timeText: String
        switch combination.time {
        case .DAWN: timeText = "early morning golden hour sunrise"
        case .MIDDAY: timeText = "midday bright daylight"
        case .DUSK: timeText = "evening golden hour sunset"
        case .NIGHT: timeText = "night time city lights"
        case .none: timeText = "daylight"
        }

        return """
        Professional photo of a \(genderText) runner in \(weatherText) \(tempText) weather during \(timeText). \
        Athletic pose, running gear appropriate for conditions, urban or trail setting. \
        High quality, lifestyle photography style, inspiring and motivational.
        """
    }
}

// MARK: - Convenience Extension

extension HeroImageService {
    /// Gets hero image using HourlyWeatherSnapshot
    func getHeroImage(
        gender: GenderPreference,
        weather: HourlyWeatherSnapshot,
        isWatch: Bool = false
    ) async -> HeroImageResult {
        await getHeroImage(
            gender: gender,
            weatherCode: weather.weatherCode,
            feelsLikeTemp: weather.feelsLikeFahrenheit,
            hour: weather.hour,
            isWatch: isWatch
        )
    }
}
