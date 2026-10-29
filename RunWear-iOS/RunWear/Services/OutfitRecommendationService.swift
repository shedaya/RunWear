import Foundation

/// Running-Specific Clothing Recommendation Engine (v4.3)
///
/// Brackets are mapped directly from real feels-like temperature to running-appropriate clothing.
/// No arithmetic offset — the thresholds themselves reflect that runners generate 5-15× resting
/// metabolic heat. The "Rule of 20" is baked into the brackets, not applied as math.
///
/// Sources: Nike, Jeff Galloway (US Olympian), Marathon Handbook, Tina Muir (2:36 marathoner),
/// Fleet Feet, Luke Humphrey (Hanson's Brooks Distance Project), NCBI exercise physiology.
///
/// Key insight: No credible running source recommends an insulated jacket at 20°F.
/// That threshold sits at 0-10°F for active runners.
///
/// v4.2 cool-band recalibration (field report, Oct 2026): 47°F actual / ~40°F feels-like in
/// 11 mph wind produced THREE torso layers — light long sleeve + quarter-zip + windbreaker.
/// Causes: (a) the mid-layer band started at 40°F, (b) the windbreaker was unconditional
/// across 10–39°F, and (c) the three torso selectors all keyed off the same feels-like with
/// no mutual exclusion, so a raw feels-like of 39.x (displayed as "40°") fired every layer at
/// once — crossing 40.0 → 39.9°F added FOUR items in a single step (2 → 6 total).
/// Fixes: mid-layers now start at 32°F, the shell is wind-gated through the whole cool band
/// (it only becomes standard issue below 25°F, where cold — not wind — dominates), and in
/// the 25–40°F band a wind shell and a mid-layer are alternatives, never a stack.
///
/// v4.3 sun-gear frame fix (owner decision, Oct 2026): sun protection is not layering.
/// The visor/cap sun gates in selectHead() read the comfort-ADJUSTED temperature, so a
/// warmer comfort profile could gain a running cap the colder profile didn't get at the
/// same weather (640 profile-ordering inversions in the Oct 17 sweep, inv7). The sun
/// gates now read the RAW temperature (before the comfort shift); warmth headgear stays
/// on the adjusted frame. Sunglasses/sunscreen were already UV-only.
///
/// This is a hand port of the Kotlin engine (GetOutfitRecommendationUseCase, :shared).
/// The port is pinned by OutfitRulesSweepTest (RunWearTests), a line port of the Kotlin
/// sweep harness: if the Kotlin tables change and this file doesn't, CI fails.
class OutfitRecommendationService {
    static let shared = OutfitRecommendationService()

    /// Engine version, surfaced in feedback diagnostic bundles so a report
    /// always names the exact rules that produced the outfit. Bump this
    /// whenever the selection tables below change. (Mirrors the Kotlin
    /// engine's ENGINE_VERSION.)
    static let engineVersion = "4.3"

    private init() {}

    /// Generate outfit recommendation from weather conditions.
    /// - Parameters:
    ///   - temperature: Feels-like temperature in Fahrenheit, AFTER the comfort
    ///     adjustment — the frame every warmth selector reads (the Kotlin
    ///     engine's `adjustedTemp`).
    ///   - rawTemperature: Feels-like temperature in Fahrenheit BEFORE the
    ///     comfort adjustment (the Kotlin engine's `baseTemp`). Read ONLY by
    ///     the visor/cap sun gates (v4.3); required so no caller can silently
    ///     keep the pre-v4.3 single-frame behavior.
    ///   - isRaining: Whether rain is actually falling, from MEASURED rain
    ///     (the Kotlin engine's `WeatherConditions.isRaining` = rain > 0),
    ///     derived by the caller from the hourly/current rain amount —
    ///     never from the weather-code class. Required, like rawTemperature,
    ///     so no caller can silently substitute the code class for the
    ///     measurement.
    ///   - windSpeed: Wind speed in mph (default 0) — the Kotlin gate's
    ///     frame; callers request mph from Open-Meteo.
    ///   - humidity: Humidity percentage (default 50)
    ///   - uvIndex: UV index (default 0)
    ///   - precipitation: Precipitation amount in mm (default 0)
    ///   - hour: Current hour (default 12)
    func getRecommendation(
        for temperature: Double,
        rawTemperature: Double,
        isRaining: Bool,
        windSpeed: Double = 0,
        humidity: Int = 50,
        uvIndex: Double = 0,
        precipitation: Double = 0,
        hour: Int = 12
    ) -> OutfitRecommendation {
        let temp = temperature
        let bracket = TemperatureBracket.from(temperature: temp)
        let isWindy = windSpeed > 10 // mph — the Kotlin gate, in the Kotlin frame

        let topMid = selectTopMid(temp)
        let topOuter = selectTopOuter(temp, isWindy: isWindy, isRaining: isRaining, precipitation: precipitation)
        // Cool-band rule (25–39.9°F): a wind shell and a mid-layer are alternatives,
        // never a stack. If the wind calls for the shell, the mid-layer comes off.
        // (Feels-like already prices in the wind chill; dressing for both double-counts it.)
        // Kotlin compares the outer layer by enum identity; Swift's ClothingItem
        // is a struct, so the comparison goes by its stable `key` — the Kotlin
        // enum case name every construction site sets (its `id` is a fresh UUID
        // per construction and its display name is presentation copy).
        let effectiveTopMid = (topOuter?.key == "WINDBREAKER" && topMid != nil && temp >= 25 && temp < 40)
            ? nil
            : topMid

        return OutfitRecommendation(
            temperatureBracket: bracket,
            topBase: selectTopBase(temp),
            topMid: effectiveTopMid,
            topOuter: topOuter,
            bottom: selectBottom(temp),
            head: selectHead(temp, rawTemp: rawTemperature, uvIndex: uvIndex, isRaining: isRaining),
            hands: selectHands(temp),
            accessories: selectAccessories(temp, uvIndex: uvIndex, isRaining: isRaining, hour: hour),
            tips: generateTips(temp, isWindy: isWindy, isRaining: isRaining, humidity: humidity, uvIndex: uvIndex)
        )
    }

    // MARK: - Selection Functions

    /// Runners wear short sleeves down to 50°F — body heat makes up the difference
    private func selectTopBase(_ temp: Double) -> ClothingItem {
        if temp >= 80 {
            return ClothingItem(key: "TANK_TOP", name: "Tank Top / Singlet", description: "Lightweight, breathable, maximum airflow", icon: "tshirt.fill", amazonSearchTerm: "running tank top moisture wicking")
        } else if temp >= 50 {
            return ClothingItem(key: "SHORT_SLEEVE", name: "Short Sleeve Shirt", description: "Moisture-wicking technical fabric", icon: "tshirt.fill", amazonSearchTerm: "running short sleeve shirt dri-fit")
        } else if temp >= 25 {
            return ClothingItem(key: "LONG_SLEEVE_LIGHT", name: "Light Long Sleeve", description: "Thin, breathable long sleeve", icon: "tshirt.fill", amazonSearchTerm: "running long sleeve lightweight")
        } else {
            return ClothingItem(key: "LONG_SLEEVE_THERMAL", name: "Thermal Long Sleeve", description: "Insulated base layer for cold", icon: "tshirt.fill", amazonSearchTerm: "running thermal base layer")
        }
    }

    /// No mid-layer above 32°F: at 32–49°F a single long-sleeve layer (plus an
    /// optional wind shell) is what mainstream dress charts call for. Below 32°F,
    /// progressively warmer mid-layers.
    private func selectTopMid(_ temp: Double) -> ClothingItem? {
        if temp >= 32 { return nil }
        if temp >= 25 {
            return ClothingItem(key: "QUARTER_ZIP", name: "Quarter-Zip Pullover", description: "Lightweight mid-layer with zip ventilation", icon: "tshirt.fill", amazonSearchTerm: "running quarter zip pullover")
        } else if temp >= 10 {
            return ClothingItem(key: "FLEECE_PULLOVER", name: "Fleece Pullover", description: "Warm brushed fleece for cold days", icon: "tshirt.fill", amazonSearchTerm: "running fleece pullover lightweight")
        } else {
            return ClothingItem(key: "HALF_ZIP_THERMAL", name: "Thermal Half-Zip", description: "Heavy-weight thermal mid-layer", icon: "tshirt.fill", amazonSearchTerm: "running thermal half zip")
        }
    }

    /// Rain always gets rain jacket. Wind shell beats heavy jacket for running.
    /// Insulated jacket ONLY below 10°F.
    /// The shell is wind-GATED through the whole cool band (25–49°F): feels-like
    /// already includes wind chill, so an automatic shell on top of it double-counts
    /// the wind. Below 25°F the shell becomes standard (cold dominates, and it seals
    /// the mid-layer). In the 25–40°F band, getRecommendation drops the mid-layer
    /// when the shell is on — the two are alternatives there, never a stack.
    private func selectTopOuter(_ temp: Double, isWindy: Bool, isRaining: Bool, precipitation: Double) -> ClothingItem? {
        // The Kotlin gate verbatim: the rain jacket needs measured rain
        // AND more than a trace of precipitation (> 0.1 mm). Drizzle
        // (isRaining, ≤ 0.1 mm) falls through to the temperature bands;
        // the rain cap and rain tip key off isRaining alone.
        if isRaining && precipitation > 0.1 {
            return ClothingItem(key: "RAIN_JACKET", name: "Rain Jacket", description: "Waterproof, breathable shell", icon: "cloud.rain.fill", amazonSearchTerm: "running rain jacket waterproof")
        }
        if temp >= 50 { return nil }
        if temp >= 25 {
            return isWindy ? ClothingItem(key: "WINDBREAKER", name: "Windbreaker", description: "Lightweight wind and light rain protection", icon: "wind", amazonSearchTerm: "running windbreaker jacket") : nil
        }
        if temp >= 10 {
            return ClothingItem(key: "WINDBREAKER", name: "Windbreaker", description: "Lightweight wind and light rain protection", icon: "wind", amazonSearchTerm: "running windbreaker jacket")
        }
        return ClothingItem(key: "INSULATED_JACKET", name: "Insulated Jacket", description: "Warm jacket for cold conditions", icon: "snow", amazonSearchTerm: "running winter jacket insulated")
    }

    /// Runners wear shorts down to ~45°F — legs generate plenty of heat
    private func selectBottom(_ temp: Double) -> ClothingItem {
        if temp >= 80 {
            return ClothingItem(key: "SHORT_SHORTS", name: "Short Shorts (3\")", description: "Maximum breathability", icon: "figure.run", amazonSearchTerm: "running shorts 3 inch")
        } else if temp >= 45 {
            return ClothingItem(key: "SHORTS", name: "Running Shorts (5-7\")", description: "Standard running shorts", icon: "figure.run", amazonSearchTerm: "running shorts 5 inch")
        } else if temp >= 30 {
            return ClothingItem(key: "LIGHT_TIGHTS", name: "Light Tights", description: "Full leg coverage, breathable", icon: "figure.run", amazonSearchTerm: "running tights lightweight")
        } else {
            return ClothingItem(key: "THERMAL_TIGHTS", name: "Thermal Tights", description: "Insulated for cold weather", icon: "figure.run", amazonSearchTerm: "running tights thermal winter")
        }
    }

    private func selectHead(_ temp: Double, rawTemp: Double, uvIndex: Double, isRaining: Bool) -> ClothingItem? {
        // Sun protection is NOT layering (v4.3): the visor/cap sun gates key off
        // the RAW temperature (rawTemp = feels-like before the comfort
        // adjustment), never the comfort-adjusted frame — "I run hot" must not
        // put a cap on your head, and "I run cold" must not take one off.
        if rawTemp >= 65 && uvIndex > 3 {
            return ClothingItem(key: "VISOR", name: "Visor", description: "Sun protection, max ventilation", icon: "sun.max.fill", amazonSearchTerm: "running visor")
        }
        if rawTemp >= 50 && uvIndex > 3 {
            return ClothingItem(key: "BASEBALL_CAP", name: "Running Cap", description: "Sun and light rain protection", icon: "baseball.cap.fill", amazonSearchTerm: "running cap lightweight")
        }
        // Same item, different job: in rain the cap is rain gear, keyed to rain
        // itself — not a temperature decision. Unchanged.
        if isRaining {
            return ClothingItem(key: "BASEBALL_CAP", name: "Running Cap", description: "Sun and light rain protection", icon: "baseball.cap.fill", amazonSearchTerm: "running cap lightweight")
        }
        // Warmth headgear below stays on the comfort-adjusted frame (temp).
        if temp >= 40 { return nil }
        if temp >= 30 {
            return ClothingItem(key: "HEADBAND", name: "Ear Warmer / Headband", description: "Keeps ears warm", icon: "ear.fill", amazonSearchTerm: "running ear warmer headband")
        }
        if temp >= 15 {
            return ClothingItem(key: "LIGHT_BEANIE", name: "Light Beanie", description: "Thin beanie for moderate cold", icon: "brain.head.profile", amazonSearchTerm: "running beanie lightweight")
        }
        if temp >= 5 {
            return ClothingItem(key: "THERMAL_BEANIE", name: "Thermal Beanie", description: "Warm hat for very cold weather", icon: "brain.head.profile", amazonSearchTerm: "running beanie thermal winter")
        }
        return ClothingItem(key: "BALACLAVA", name: "Balaclava / Face Mask", description: "Full face protection", icon: "face.dashed", amazonSearchTerm: "running balaclava face mask")
    }

    private func selectHands(_ temp: Double) -> ClothingItem? {
        if temp >= 40 { return nil }
        if temp >= 25 {
            return ClothingItem(key: "LIGHT_GLOVES", name: "Light Gloves", description: "Thin running gloves", icon: "hand.raised.fill", amazonSearchTerm: "running gloves lightweight touchscreen")
        }
        if temp >= 15 {
            return ClothingItem(key: "THERMAL_GLOVES", name: "Thermal Gloves", description: "Insulated gloves for cold", icon: "hand.raised.fill", amazonSearchTerm: "running gloves thermal winter")
        }
        return ClothingItem(key: "MITTENS", name: "Mittens", description: "Maximum warmth for extreme cold", icon: "hand.raised.fill", amazonSearchTerm: "running mittens warm")
    }

    private func selectAccessories(_ temp: Double, uvIndex: Double, isRaining: Bool, hour: Int) -> [ClothingItem] {
        var items: [ClothingItem] = []

        // Sun protection, UV-only: never read the temperature in any frame, so
        // the comfort preference cannot move them (classification audited v4.3).
        if uvIndex > 3 {
            items.append(ClothingItem(key: "SUNGLASSES", name: "Sunglasses", description: "Eye protection from sun and wind", icon: "sunglasses.fill", amazonSearchTerm: "running sunglasses sport"))
        }
        if uvIndex > 5 {
            items.append(ClothingItem(key: "SUNSCREEN", name: "Sunscreen (SPF 30+)", description: "Protect exposed skin", icon: "sun.max.fill", amazonSearchTerm: "sport sunscreen spf 50"))
        }
        if temp < 30 {
            items.append(ClothingItem(key: "NECK_GAITER", name: "Neck Gaiter / Buff", description: "Versatile neck and face protection", icon: "scribble.variable", amazonSearchTerm: "running neck gaiter buff"))
        }
        if hour < 7 || hour > 18 {
            items.append(ClothingItem(key: "REFLECTIVE_GEAR", name: "Reflective Gear", description: "Visibility for low-light", icon: "light.beacon.max.fill", amazonSearchTerm: "running reflective vest"))
        }

        return items
    }

    // MARK: - Tips

    private func generateTips(_ temp: Double, isWindy: Bool, isRaining: Bool, humidity: Int, uvIndex: Double) -> [String] {
        var tips: [String] = []

        if temp < 50 {
            tips.append("You should feel slightly cool stepping outside. If you're comfortable standing still, you're overdressed for running.")
        }
        // Layered-setup ventilation tip (2+ torso layers happen below 32°F)
        if temp < 32 && temp >= 10 {
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
