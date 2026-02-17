import Foundation

/// Running-Specific Clothing Recommendation Engine (v4.1)
///
/// Brackets are mapped directly from real feels-like temperature to running-appropriate clothing.
/// No arithmetic offset — the thresholds themselves reflect that runners generate 5-15× resting
/// metabolic heat. The "Rule of 20" is baked into the brackets, not applied as math.
///
/// Sources: Nike, Jeff Galloway (US Olympian), Marathon Handbook, Tina Muir (2:36 marathoner),
/// Fleet Feet, Luke Humphrey (Hanson's Brooks Distance Project), NCBI exercise physiology.
class OutfitRecommendationService {
    static let shared = OutfitRecommendationService()

    private init() {}

    /// Generate outfit recommendation from weather conditions.
    /// - Parameters:
    ///   - temperature: Feels-like temperature in Fahrenheit (after comfort adjustment)
    ///   - condition: Current weather condition
    ///   - windSpeed: Wind speed in km/h (default 0)
    ///   - humidity: Humidity percentage (default 50)
    ///   - uvIndex: UV index (default 0)
    ///   - precipitation: Precipitation amount (default 0)
    ///   - hour: Current hour (default 12)
    func getRecommendation(
        for temperature: Double,
        condition: WeatherCondition,
        windSpeed: Double = 0,
        humidity: Int = 50,
        uvIndex: Double = 0,
        precipitation: Double = 0,
        hour: Int = 12
    ) -> OutfitRecommendation {
        let temp = temperature
        let bracket = TemperatureBracket.from(temperature: temp)
        let isWindy = windSpeed > 10 // km/h ~= 6 mph threshold adjusts for metric
        let isRaining = condition == .rain || condition == .drizzle || condition == .thunderstorm

        return OutfitRecommendation(
            temperatureBracket: bracket,
            topBase: selectTopBase(temp),
            topMid: selectTopMid(temp),
            topOuter: selectTopOuter(temp, isWindy: isWindy, isRaining: isRaining, precipitation: precipitation),
            bottom: selectBottom(temp),
            head: selectHead(temp, uvIndex: uvIndex, isRaining: isRaining),
            hands: selectHands(temp),
            accessories: selectAccessories(temp, uvIndex: uvIndex, isRaining: isRaining, hour: hour),
            tips: generateTips(temp, isWindy: isWindy, isRaining: isRaining, humidity: humidity, uvIndex: uvIndex)
        )
    }

    // MARK: - Selection Functions

    /// Runners wear short sleeves down to 50°F — body heat makes up the difference
    private func selectTopBase(_ temp: Double) -> ClothingItem {
        if temp >= 80 {
            return ClothingItem(name: "Tank Top", description: "Lightweight, breathable singlet", icon: "tshirt.fill", amazonSearchTerm: "running tank top moisture wicking")
        } else if temp >= 50 {
            return ClothingItem(name: "Short Sleeve Shirt", description: "Moisture-wicking technical tee", icon: "tshirt.fill", amazonSearchTerm: "running short sleeve shirt dri-fit")
        } else if temp >= 20 {
            return ClothingItem(name: "Light Long Sleeve", description: "Lightweight technical long sleeve", icon: "tshirt.fill", amazonSearchTerm: "running long sleeve lightweight")
        } else {
            return ClothingItem(name: "Thermal Long Sleeve", description: "Insulated base layer for extreme cold", icon: "tshirt.fill", amazonSearchTerm: "running thermal base layer")
        }
    }

    /// No mid-layer above 40°F. Below that, progressively warmer mid-layers.
    private func selectTopMid(_ temp: Double) -> ClothingItem? {
        if temp >= 40 { return nil }
        if temp >= 20 {
            return ClothingItem(name: "Quarter-Zip Pullover", description: "Lightweight mid-layer with zip ventilation", icon: "tshirt.fill", amazonSearchTerm: "running quarter zip pullover")
        } else if temp >= 10 {
            return ClothingItem(name: "Fleece Pullover", description: "Warm brushed fleece for cold days", icon: "tshirt.fill", amazonSearchTerm: "running fleece pullover lightweight")
        } else {
            return ClothingItem(name: "Thermal Half-Zip", description: "Heavy-weight thermal mid-layer", icon: "tshirt.fill", amazonSearchTerm: "running thermal half zip")
        }
    }

    /// Rain always gets rain jacket. Wind shell beats heavy jacket for running.
    /// Insulated jacket ONLY below 10°F.
    private func selectTopOuter(_ temp: Double, isWindy: Bool, isRaining: Bool, precipitation: Double) -> ClothingItem? {
        if isRaining {
            return ClothingItem(name: "Rain Jacket", description: "Lightweight waterproof running jacket", icon: "cloud.rain.fill", amazonSearchTerm: "waterproof running jacket")
        }
        if temp >= 50 { return nil }
        if temp >= 40 {
            return isWindy ? ClothingItem(name: "Windbreaker", description: "Lightweight wind protection", icon: "wind", amazonSearchTerm: "running windbreaker jacket") : nil
        }
        if temp >= 10 {
            return ClothingItem(name: "Windbreaker", description: "Lightweight wind shell", icon: "wind", amazonSearchTerm: "running windbreaker jacket")
        }
        return ClothingItem(name: "Insulated Jacket", description: "Warm jacket for extreme cold", icon: "snow", amazonSearchTerm: "running winter jacket insulated")
    }

    /// Runners wear shorts down to ~45°F — legs generate plenty of heat
    private func selectBottom(_ temp: Double) -> ClothingItem {
        if temp >= 80 {
            return ClothingItem(name: "Short Shorts", description: "Light 3-inch running shorts", icon: "figure.run", amazonSearchTerm: "running shorts 3 inch split")
        } else if temp >= 45 {
            return ClothingItem(name: "Running Shorts", description: "Standard 5-7 inch shorts", icon: "figure.run", amazonSearchTerm: "running shorts 5 inch")
        } else if temp >= 30 {
            return ClothingItem(name: "Light Tights", description: "Lightweight running tights", icon: "figure.run", amazonSearchTerm: "running tights lightweight")
        } else {
            return ClothingItem(name: "Thermal Tights", description: "Fleece-lined running tights", icon: "figure.run", amazonSearchTerm: "fleece lined running tights")
        }
    }

    private func selectHead(_ temp: Double, uvIndex: Double, isRaining: Bool) -> ClothingItem? {
        if temp >= 65 && uvIndex > 3 {
            return ClothingItem(name: "Visor", description: "Sun protection, max ventilation", icon: "sun.max.fill", amazonSearchTerm: "running visor")
        }
        if temp >= 50 && uvIndex > 3 {
            return ClothingItem(name: "Running Cap", description: "Breathable mesh cap for sun protection", icon: "baseball.cap.fill", amazonSearchTerm: "running cap breathable")
        }
        if isRaining {
            return ClothingItem(name: "Running Cap", description: "Keeps rain out of eyes", icon: "baseball.cap.fill", amazonSearchTerm: "running cap breathable")
        }
        if temp >= 50 { return nil }
        if temp >= 30 {
            return ClothingItem(name: "Headband", description: "Ear warmer headband", icon: "ear.fill", amazonSearchTerm: "running ear warmer headband")
        }
        if temp >= 10 {
            return ClothingItem(name: "Light Beanie", description: "Thin thermal beanie", icon: "brain.head.profile", amazonSearchTerm: "running beanie lightweight")
        }
        if temp >= 0 {
            return ClothingItem(name: "Thermal Beanie", description: "Warm insulated beanie", icon: "brain.head.profile", amazonSearchTerm: "running beanie thermal winter")
        }
        return ClothingItem(name: "Balaclava", description: "Full face and neck coverage", icon: "face.dashed", amazonSearchTerm: "running balaclava cold weather")
    }

    private func selectHands(_ temp: Double) -> ClothingItem? {
        if temp >= 40 { return nil }
        if temp >= 20 {
            return ClothingItem(name: "Light Gloves", description: "Thin touchscreen-compatible gloves", icon: "hand.raised.fill", amazonSearchTerm: "lightweight running gloves touchscreen")
        }
        if temp >= 10 {
            return ClothingItem(name: "Thermal Gloves", description: "Insulated running gloves", icon: "hand.raised.fill", amazonSearchTerm: "insulated running gloves winter")
        }
        return ClothingItem(name: "Mittens", description: "Maximum warmth for extreme cold", icon: "hand.raised.fill", amazonSearchTerm: "running mittens warm")
    }

    private func selectAccessories(_ temp: Double, uvIndex: Double, isRaining: Bool, hour: Int) -> [ClothingItem] {
        var items: [ClothingItem] = []

        if uvIndex > 3 {
            items.append(ClothingItem(name: "Sunglasses", description: "UV protection running sunglasses", icon: "sunglasses.fill", amazonSearchTerm: "running sunglasses"))
        }
        if uvIndex > 5 {
            items.append(ClothingItem(name: "Sunscreen", description: "Sport SPF 50 sunscreen", icon: "sun.max.fill", amazonSearchTerm: "sport sunscreen spf 50"))
        }
        if temp < 30 {
            items.append(ClothingItem(name: "Neck Gaiter", description: "Fleece neck warmer", icon: "scribble.variable", amazonSearchTerm: "running neck gaiter buff"))
        }
        if hour < 7 || hour > 18 {
            items.append(ClothingItem(name: "Reflective Gear", description: "High-visibility vest", icon: "light.beacon.max.fill", amazonSearchTerm: "running reflective vest"))
        }

        return items
    }

    // MARK: - Tips

    private func generateTips(_ temp: Double, isWindy: Bool, isRaining: Bool, humidity: Int, uvIndex: Double) -> [String] {
        var tips: [String] = []

        if temp < 50 {
            tips.append("You should feel slightly cool stepping outside. If you're comfortable standing still, you're overdressed for running.")
        }
        if temp < 40 && temp >= 10 {
            tips.append("Your layered setup lets you regulate heat — unzip your mid-layer or shell if you warm up after the first mile.")
        }
        if isWindy && temp < 50 {
            tips.append("A wind shell beats a heavy jacket for running. It blocks wind chill while letting you vent excess heat.")
        }
        if temp < 10 {
            tips.append("Cover all exposed skin — frostbite risk increases below 10°F with wind. Vaseline on cheeks and nose helps.")
        }
        if temp >= 80 {
            tips.append("High heat risk — consider running early morning or evening.")
            tips.append("Hydrate well before, during, and after your run.")
        } else if temp >= 70 {
            tips.append("Stay hydrated — consider carrying water.")
        }
        if isRaining {
            tips.append("Wear a cap under your hood to keep rain out of your eyes. Avoid cotton — it retains moisture.")
        }
        if humidity > 65 && temp > 60 {
            tips.append("High humidity — sweat won't evaporate easily. Wear looser, lighter fabrics and hydrate more.")
        }
        if uvIndex > 6 {
            tips.append("UV is high — reapply sunscreen every 60-90 minutes if running longer than an hour.")
        }
        if temp >= 40 && temp < 50 {
            tips.append("Many runners still wear shorts at this temperature. Your legs generate plenty of heat — try it before adding tights.")
        }

        return Array(tips.prefix(3))
    }
}
