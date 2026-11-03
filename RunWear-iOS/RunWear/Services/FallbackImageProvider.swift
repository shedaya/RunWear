import Foundation

/// Provides fallback hero images when Supabase pool images are unavailable.
///
/// Since 2026-10-30 the fallbacks are the SAME approved AI pool photos
/// the Android app bundles, shipped inside the app bundle under
/// `hero_fallback/` — no more remote Unsplash stock. Cell-for-cell
/// identical to Android's WebP fallback table (and the PWA's).
///
/// RW-20 (2026-11-02): all 22 assets' provenance was re-derived by
/// pixel comparison against the pool. 9 verified against
/// adjudicated-MATCH, single-person, point-grain-correct source rows;
/// 12 were replaced from such rows in the same cell (their sources
/// were retired, garment-mismatched, multi-person, or merely
/// UNCERTAIN); FREEZING×RAIN was removed — its pool holds no
/// verified-good photo at all (all 77 FREEZING rain/rainy rows are
/// rejected), so the lookup falls back to freezing_clear there.
/// 21 cells remain. Provenance record:
/// release-prep/rw20-fix-execution-2026-11-02.md.
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
    /// an exact mirror of Android's FALLBACK_IMAGES (21 populated cells
    /// after RW-20). HOT and WARM have no SNOW cell and FREEZING no
    /// longer has a RAIN cell on any platform; the lookup below falls
    /// back to the bracket's CLEAR image, as Android's does.
    ///
    /// Source combinations below are the RW-20-verified provenance
    /// (2026-11-02); entries marked RW-20 replaced a retired,
    /// mismatched, multi-person, or UNCERTAIN original.
    ///
    /// The previous iOS table pointed at Unsplash stock photos and had
    /// drifted from the pool (its HOT/CLOUDY cell was once a desk with
    /// code on monitors, served to Sol as a "Miami hero"). Gone.
    private let assetNames: [HeroTempBracket: [HeroWeatherCondition: String]] = [
        .FREEZING: [
            .CLEAR: "freezing_clear",   // RW-20: FEMALE_CLEAR_FREEZING_DAWN_v100
            .CLOUDY: "freezing_cloudy",  // FEMALE_CLOUDY_FREEZING_MIDDAY_v1177
            .SNOW: "freezing_snow"  // MALE_SNOWY_FREEZING_NIGHT_v1
        ],
        .COLD: [
            .CLEAR: "cold_clear",   // RW-20: FEMALE_CLEAR_COLD_DAWN_v2
            .CLOUDY: "cold_cloudy",  // FEMALE_CLOUDY_COLD_MIDDAY_v3
            .RAIN: "cold_rain",    // RW-20: FEMALE_RAIN_COLD_DAWN_v12
            .SNOW: "cold_snow"     // FEMALE_SNOW_COLD_DUSK_v5
        ],
        .COOL: [
            .CLEAR: "cool_clear",   // RW-20: FEMALE_CLEAR_COOL_DAWN_v15
            .CLOUDY: "cool_cloudy",  // RW-20: FEMALE_CLOUDY_COOL_DAWN_v12
            .RAIN: "cool_rain",    // RW-20: FEMALE_RAIN_COOL_NIGHT_v693
            .SNOW: "cool_snow"     // RW-20: MALE_SNOW_COOL_MIDDAY_v26
        ],
        .MILD: [
            .CLEAR: "mild_clear",   // RW-20: FEMALE_CLEAR_MILD_DUSK_v4
            .CLOUDY: "mild_cloudy",  // RW-20: FEMALE_CLOUDY_MILD_DUSK_v3
            .RAIN: "mild_rain", // FEMALE_RAIN_MILD_DUSK_v4
            .SNOW: "mild_snow"  // RW-20: FEMALE_SNOW_MILD_DAWN_v16
        ],
        .WARM: [
            .CLEAR: "warm_clear",   // MALE_CLEAR_WARM_MIDDAY_v3
            .CLOUDY: "warm_cloudy",    // MALE_CLOUDY_WARM_DAWN_v5
            .RAIN: "warm_rain"     // RW-20: FEMALE_RAIN_WARM_DAWN_v858
        ],
        .HOT: [
            .CLEAR: "hot_clear",   // RW-20: FEMALE_CLEAR_HOT_DAWN_v1
            .CLOUDY: "hot_cloudy",  // MALE_CLOUDY_HOT_DUSK_v2
            .RAIN: "hot_rain"     // FEMALE_RAINY_HOT_DAWN_v6
        ]
    ]

    /// RW-26 (2026-11-02): the photographed subject's gender for every
    /// bundled fallback asset, from the RW-20 provenance above. The
    /// matrix is keyed bracket × weather only — 15 of its 21 subjects
    /// are women and COLD/MILD hold no male-subject asset at all — so
    /// before this fix a fallback serve ignored the user's fit
    /// preference entirely (field instances 2026-11-02: cool_clear and
    /// mild_clear served to a Male-preference user). Diagnosis:
    /// release-prep/rw-gender-preference-diagnosis-2026-11-02.md.
    /// Mirror of the Android engine's subject table.
    private let assetSubjects: [String: HeroGender] = [
        "freezing_clear": .FEMALE, "freezing_cloudy": .FEMALE, "freezing_snow": .MALE,
        "cold_clear": .FEMALE, "cold_cloudy": .FEMALE, "cold_rain": .FEMALE, "cold_snow": .FEMALE,
        "cool_clear": .FEMALE, "cool_cloudy": .FEMALE, "cool_rain": .FEMALE, "cool_snow": .MALE,
        "mild_clear": .FEMALE, "mild_cloudy": .FEMALE, "mild_rain": .FEMALE, "mild_snow": .FEMALE,
        "warm_clear": .MALE, "warm_cloudy": .MALE, "warm_rain": .FEMALE,
        "hot_clear": .FEMALE, "hot_cloudy": .MALE, "hot_rain": .FEMALE
    ]

    /// Warmth order (coldest first) for walking neighbouring brackets.
    private let bracketWarmthOrder: [HeroTempBracket] = [.FREEZING, .COLD, .COOL, .MILD, .WARM, .HOT]

    /// Candidate cells for a fallback request, most-preferred first:
    /// the requested cell, then the rest of its bracket (CLEAR, CLOUDY,
    /// RAIN, SNOW), then neighbouring brackets by warmth distance
    /// (warmer first on a tie), each contributing its requested-weather
    /// cell first, then CLEAR, CLOUDY, RAIN, SNOW. Only cells that hold
    /// an asset are returned. Mirror of the Android orderedFallbackCells.
    private func orderedFallbackCells(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition
    ) -> [(HeroTempBracket, HeroWeatherCondition)] {
        var seen = Set<String>()
        var cells: [(HeroTempBracket, HeroWeatherCondition)] = []
        func add(bracket: HeroTempBracket, condition: HeroWeatherCondition) {
            let key = "\(bracket)|\(condition)"
            guard seen.insert(key).inserted, assetNames[bracket]?[condition] != nil else { return }
            cells.append((bracket, condition))
        }
        func addBracket(_ bracket: HeroTempBracket) {
            add(bracket: bracket, condition: weather)
            for w in [HeroWeatherCondition.CLEAR, .CLOUDY, .RAIN, .SNOW] {
                add(bracket: bracket, condition: w)
            }
        }
        addBracket(temp)
        guard let idx = bracketWarmthOrder.firstIndex(of: temp) else { return cells }
        let neighbours = bracketWarmthOrder
            .filter { $0 != temp }
            .sorted { a, b in
                let ia = bracketWarmthOrder.firstIndex(of: a)!
                let ib = bracketWarmthOrder.firstIndex(of: b)!
                if abs(ia - idx) != abs(ib - idx) { return abs(ia - idx) < abs(ib - idx) }
                return ia > ib
            }
        for b in neighbours { addBracket(b) }
        return cells
    }

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
    /// The bundled asset NAME a fallback request resolves to, before
    /// bundle-URL resolution. Internal (not private) so unit tests can
    /// pin the resolution in a logic-test host, where Bundle.main is
    /// the test runner and no app resources resolve.
    func resolvedAssetName(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        gender: HeroGender? = nil
    ) -> String {
        // RW-26: with a (resolved) gender, never resolve to an asset
        // whose photographed subject is the other gender while any
        // bundled asset matches — walk the ordered candidate cells and
        // take the first whose subject matches. Without a gender, the
        // legacy chain below is unchanged.
        if let gender {
            for (bracket, condition) in orderedFallbackCells(temp: temp, weather: weather) {
                if let name = assetNames[bracket]?[condition], assetSubjects[name] == gender {
                    return name
                }
            }
        }
        return assetNames[temp]?[weather]
            ?? assetNames[temp]?[.CLEAR]
            ?? assetNames[.MILD]?[.CLEAR]
            ?? "mild_clear"
    }

    func getImageURL(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        size: ImageSize = .iOS,
        gender: HeroGender? = nil
    ) -> String {
        let name = resolvedAssetName(temp: temp, weather: weather, gender: gender)
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
        isWatch: Bool = false,
        gender: HeroGender? = nil
    ) -> HeroImageResult {
        let size: ImageSize = isWatch ? .watchOS : .iOS
        let url = getImageURL(temp: temp, weather: weather, size: size, gender: gender)
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
