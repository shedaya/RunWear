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

        // Queue replenishment if needed (non-blocking)
        Task {
            await queueReplenishmentIfNeeded(
                combination: combination,
                result: result,
                outfit: outfit
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
        result: HeroImageResult,
        outfit: OutfitRecommendation? = nil
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
            await queueGeneration(for: combination, outfit: outfit)
        }
    }

    /// Queues a generation job
    private func queueGeneration(for combination: HeroCombinationId, outfit: OutfitRecommendation? = nil) async {
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
        let prompt = buildPrompt(for: fullCombination, outfit: outfit)

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
    /// v3.14: Uses actual outfit recommendation items instead of static descriptions.
    /// Includes random backgrounds for variety per HERO_IMAGE_SPEC.md
    private func buildPrompt(for combination: HeroCombinationId, outfit: OutfitRecommendation? = nil) -> String {
        let genderText = combination.gender == .MALE ? "male" : "female"
        let weatherText = combination.weather.rawValue.lowercased()
        let tempText = combination.temp.rawValue.lowercased().replacingOccurrences(of: "_", with: " ")

        let timeText: String
        switch combination.time {
        case .DAWN: timeText = "early morning golden hour"
        case .MIDDAY: timeText = "bright midday"
        case .DUSK: timeText = "evening golden hour"
        case .NIGHT: timeText = "night with street lights"
        case .none: timeText = "midday"
        }

        // Random backgrounds for variety
        let backgrounds = [
            "city street with buildings in background",
            "urban park with trees",
            "waterfront boardwalk",
            "scenic trail with nature",
            "downtown area with shops",
            "bridge with city skyline",
            "tree-lined avenue"
        ]
        let background = backgrounds.randomElement() ?? backgrounds[0]

        // Mood based on conditions
        let mood: String
        switch combination.weather {
        case .RAIN: mood = "determined, pushing through the rain"
        case .SNOW: mood = "resilient, winter warrior"
        default:
            switch combination.temp {
            case .HOT: mood = "energetic, summer vibes"
            case .FREEZING: mood = "tough, braving the cold"
            default: mood = "focused, confident stride"
            }
        }

        // v3.14: Build outfit description from actual recommendation items if available
        let outfitDesc: String
        if let outfit = outfit {
            // Filter out non-visible items (sunscreen, reflective gear)
            let visibleItems = outfit.allItems.filter { item in
                !item.name.contains("Sunscreen") && !item.name.contains("Reflective")
            }
            outfitDesc = visibleItems.map { $0.name.lowercased() }.joined(separator: ", ")
        } else {
            // Fallback to static descriptions if no outfit provided
            let outfitDescriptions: [HeroTempBracket: String] = [
                .HOT: "lightweight breathable tank top, very short split running shorts, sunglasses, visor",
                .WARM: "breathable short sleeve tech shirt, standard running shorts, light mesh running cap",
                .MILD: "short sleeve or lightweight long sleeve shirt, running shorts",
                .COOL: "lightweight long sleeve shirt, windbreaker shell if windy or below 40°F, running shorts or light tights, headband below 40°F, light gloves below 40°F",
                .COLD: "lightweight long sleeve base layer, charcoal quarter-zip pullover mid-layer visible at collar, black windbreaker shell partially unzipped showing layers, light running tights, light beanie, light gloves, neck gaiter",
                .FREEZING: "thermal long sleeve base layer visible at collar, teal fleece pullover or thermal half-zip mid-layer, windbreaker shell (10-19°F) or insulated jacket (below 10°F), thermal tights, thermal beanie, thermal gloves or mittens, neck gaiter, visible breath vapor"
            ]
            outfitDesc = outfitDescriptions[combination.temp] ?? outfitDescriptions[.MILD]!
        }

        return "A \(genderText) runner in their 30s running mid-stride along a \(background). They are wearing \(outfitDesc) appropriate for \(weatherText) \(tempText) weather. Time of day: \(timeText). Professional running photography, dynamic action shot, high quality, sharp focus. MOOD: \(mood)"
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
