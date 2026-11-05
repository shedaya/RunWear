import Foundation

/// Provides fallback hero images when Supabase pool images are unavailable.
///
/// Since 2026-10-30 the fallbacks are the SAME approved AI pool photos
/// the Android app bundles, shipped inside the app bundle under
/// `hero_fallback/` — no more remote Unsplash stock. Cell-for-cell
/// identical to Android's WebP fallback table (and the PWA's).
///
/// WS-7 (2026-11-03): the bundle is now the staged RW-26 gender-paired
/// matrix — 22 cells × MALE/FEMALE = 44 assets
/// (`{bracket}_{weather}_{gender}.webp`, RW-20 render standard:
/// center-crop 2:3 → 800×1200 → WebP q82), per
/// release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md (23 NEW +
/// 12 RE-RENDER + 9 KEEP against the RW-20 set it replaces). The
/// RW-20-era 21-asset set is retired wholesale — including the 5
/// sources the legacy re-adjudication rejected after RW-20 derived
/// them (cool_clear, cool_cloudy, cold_rain, mild_rain, hot_clear —
/// D-21). FREEZING × RAIN is a real cell again (Sol's Nov 3 ruling):
/// the designed degradation (FREEZING RAIN → freezing_clear) is
/// RETIRED for gendered serves — the cell serves its own paired
/// assets. HOT × SNOW and WARM × SNOW remain non-cells (physically
/// unreachable, no stock). The manifest's two light-vs-tier
/// alternates were taken at their PRIMARY picks: FREEZING CLEAR · F
/// is the T1 NIGHT row (not the T3 MIDDAY alternate) and MILD
/// RAIN · M is the T1 NIGHT row.
///
/// Resolver bound (D-19): with every cell paired, a gendered request
/// STARTS AND NORMALLY ENDS at the requested cell's own gendered
/// asset — subject gender never outranks the scene at a paired cell.
/// The RW-26 walk that resolved MALE + COOL + CLEAR to the snow
/// asset (the only male subject in the old COOL bracket) now
/// resolves to cool_clear_male. The scene-first walk survives only
/// for non-cells (HOT × SNOW, WARM × SNOW), where it takes the
/// requested gender's paired asset at the nearest scene. An
/// ungendered request keeps the legacy chain byte-for-byte over the
/// legacy view (see `legacyAssetNames`): FREEZING × RAIN still
/// degrades to freezing_clear there, exactly as before WS-7 — the
/// same treatment Android gives UNISEX. WS-6 wired the gender
/// argument at the app-target painting call sites (D-20); this
/// provider honors it correctly whenever it is supplied.
///
/// Point-grain standing (D-22): every bundled asset carries its
/// manifest provenance — the pool source combination it was rendered
/// from, that row's id, and its rung verdict (T1 strict rung 1 /
/// T3 recalibrated rung 1) — in `provenanceByAsset`, and every
/// bundled serve result carries it, so a bundled serve is auditable
/// exactly like a pool serve.
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

    // MARK: - The paired matrix (WS-7)

    /// The gender-paired fallback matrix: bracket → weather → gender
    /// → asset base name (`{bracket}_{weather}_{gender}`). 22 cells
    /// × 2 genders = 44 assets. Mirror of Android's
    /// FALLBACK_IMAGES_PAIRED.
    static let pairedAssets: [HeroTempBracket: [HeroWeatherCondition: [HeroGender: String]]] = [
        .FREEZING: [
            .CLEAR: [.FEMALE: "freezing_clear_female", .MALE: "freezing_clear_male"],
            .CLOUDY: [.FEMALE: "freezing_cloudy_female", .MALE: "freezing_cloudy_male"],
            .RAIN: [.FEMALE: "freezing_rain_female", .MALE: "freezing_rain_male"],
            .SNOW: [.FEMALE: "freezing_snow_female", .MALE: "freezing_snow_male"]
        ],
        .COLD: [
            .CLEAR: [.FEMALE: "cold_clear_female", .MALE: "cold_clear_male"],
            .CLOUDY: [.FEMALE: "cold_cloudy_female", .MALE: "cold_cloudy_male"],
            .RAIN: [.FEMALE: "cold_rain_female", .MALE: "cold_rain_male"],
            .SNOW: [.FEMALE: "cold_snow_female", .MALE: "cold_snow_male"]
        ],
        .COOL: [
            .CLEAR: [.FEMALE: "cool_clear_female", .MALE: "cool_clear_male"],
            .CLOUDY: [.FEMALE: "cool_cloudy_female", .MALE: "cool_cloudy_male"],
            .RAIN: [.FEMALE: "cool_rain_female", .MALE: "cool_rain_male"],
            .SNOW: [.FEMALE: "cool_snow_female", .MALE: "cool_snow_male"]
        ],
        .MILD: [
            .CLEAR: [.FEMALE: "mild_clear_female", .MALE: "mild_clear_male"],
            .CLOUDY: [.FEMALE: "mild_cloudy_female", .MALE: "mild_cloudy_male"],
            .RAIN: [.FEMALE: "mild_rain_female", .MALE: "mild_rain_male"],
            .SNOW: [.FEMALE: "mild_snow_female", .MALE: "mild_snow_male"]
        ],
        .WARM: [
            .CLEAR: [.FEMALE: "warm_clear_female", .MALE: "warm_clear_male"],
            .CLOUDY: [.FEMALE: "warm_cloudy_female", .MALE: "warm_cloudy_male"],
            .RAIN: [.FEMALE: "warm_rain_female", .MALE: "warm_rain_male"]
        ],
        .HOT: [
            .CLEAR: [.FEMALE: "hot_clear_female", .MALE: "hot_clear_male"],
            .CLOUDY: [.FEMALE: "hot_cloudy_female", .MALE: "hot_cloudy_male"],
            .RAIN: [.FEMALE: "hot_rain_female", .MALE: "hot_rain_male"]
        ]
    ]

    /// WS-7 / D-22: per-asset provenance for every bundled file, from
    /// the staged bundle manifest — the slot's pool source
    /// combination, source row id, and point-grain rung verdict
    /// (T1/T3). Keyed by asset file base name (no extension).
    static let provenanceByAsset: [String: FallbackAssetProvenance] = [
        "freezing_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_FREEZING_NIGHT_v4", sourceRowId: "dc5b7be8-947c-462d-9cf5-d800e867363b", rung: "T1"),
        "freezing_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_FREEZING_MIDDAY_v1", sourceRowId: "616cdf4f-6dfb-4ca0-bc51-b045f1c32d57", rung: "T3"),
        "freezing_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_FREEZING_MIDDAY_v1177", sourceRowId: "6fcb0994-ade3-4490-b8ca-60704b349490", rung: "T3"),
        "freezing_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_FREEZING_MIDDAY_459e0be6", sourceRowId: "69ab00a0-e806-4cf4-a848-3014a4e2029d", rung: "T3"),
        "freezing_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_FREEZING_MIDDAY_b7e866a8", sourceRowId: "0b891b30-75ed-4337-939c-6219d9f1309a", rung: "T3"),
        "freezing_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAIN_FREEZING_DAWN_b7e866a8", sourceRowId: "ad138c05-da7e-4cc3-ac63-f73bcf3bc21b", rung: "T1"),
        "freezing_snow_female": FallbackAssetProvenance(combinationId: "FEMALE_SNOW_FREEZING_MIDDAY_v2", sourceRowId: "36a47e4d-685a-4d12-98ff-653a88e8d866", rung: "T1"),
        "freezing_snow_male": FallbackAssetProvenance(combinationId: "MALE_SNOWY_FREEZING_DAWN_v6", sourceRowId: "5c9047fc-3958-4991-9ada-a66ac359e295", rung: "T1"),
        "cold_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_COLD_NIGHT_v2", sourceRowId: "4a46575d-3b63-4e94-84e0-f07f9558e50a", rung: "T1"),
        "cold_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_COLD_MIDDAY_27f65a1f", sourceRowId: "dd83ab86-0030-4b27-90cb-3e60221d9ce0", rung: "T1"),
        "cold_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_COLD_MIDDAY_v3", sourceRowId: "579a244b-3160-4350-b8a4-0c69b00bd353", rung: "T3"),
        "cold_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_COLD_MIDDAY_v5", sourceRowId: "0d69b957-8755-43f7-ba8b-957b6b144eb8", rung: "T1"),
        "cold_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_COLD_MIDDAY_v18", sourceRowId: "9ec95942-fb00-4d38-a8bb-845bcd8c149a", rung: "T3"),
        "cold_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAIN_COLD_MIDDAY_v12", sourceRowId: "ef52a837-8354-40fd-a0cd-362696d6ee23", rung: "T3"),
        "cold_snow_female": FallbackAssetProvenance(combinationId: "FEMALE_SNOW_COLD_DAWN_v4", sourceRowId: "a3f54b7c-81e5-4bd9-8362-aa77f3e4b5d3", rung: "T1"),
        "cold_snow_male": FallbackAssetProvenance(combinationId: "MALE_SNOW_COLD_DAWN_v2", sourceRowId: "c0b3f186-89d5-412d-9398-4065af6c39bd", rung: "T1"),
        "cool_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_COOL_MIDDAY_84c10525", sourceRowId: "6e8a4250-d42e-415f-b7a1-ffc5723ac453", rung: "T1"),
        "cool_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_COOL_MIDDAY_84c10525", sourceRowId: "f4b95431-0943-435b-95c7-304b95762e51", rung: "T1"),
        "cool_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_COOL_MIDDAY_84c10525", sourceRowId: "ecaa6fe9-701f-4034-b253-e20ff57409ca", rung: "T1"),
        "cool_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_COOL_MIDDAY_84c10525", sourceRowId: "3e6ad6b9-992a-4af9-aacb-bfb96325575b", rung: "T1"),
        "cool_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_COOL_DUSK_f213805f", sourceRowId: "6a256bd2-29a4-46c6-a832-b604c2299016", rung: "T1"),
        "cool_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAIN_COOL_MIDDAY_f213805f", sourceRowId: "c345ae20-76d8-4b81-8bd7-8643e9770df5", rung: "T3"),
        "cool_snow_female": FallbackAssetProvenance(combinationId: "FEMALE_SNOW_COOL_MIDDAY_84c10525", sourceRowId: "7ad70bbc-f5ac-4b68-90de-b260404c1f3d", rung: "T1"),
        "cool_snow_male": FallbackAssetProvenance(combinationId: "MALE_SNOW_COOL_MIDDAY_v26", sourceRowId: "f8e3791a-6fe2-472c-ad80-e058a00ed792", rung: "T1"),
        "mild_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_MILD_DUSK_v4", sourceRowId: "d42b35c7-c78d-49c3-a918-b3cf5adf755a", rung: "T1"),
        "mild_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_MILD_MIDDAY_89c065aa", sourceRowId: "e6970789-4adb-41a6-8923-22fd40e17a66", rung: "T1"),
        "mild_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_MILD_DUSK_v3", sourceRowId: "207ff224-58ee-4869-8cff-5cf3db231f4e", rung: "T1"),
        "mild_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_MILD_MIDDAY_491df101", sourceRowId: "72f96eea-edb5-4d0c-97a0-a20530cfe88f", rung: "T1"),
        "mild_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_MILD_DAWN_v15", sourceRowId: "f11a3e51-c7ee-4d1e-9d32-e8b7206654f6", rung: "T1"),
        "mild_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAINY_MILD_NIGHT_v3", sourceRowId: "a9c80dda-fe17-4ad5-88c9-fdf353def761", rung: "T1"),
        "mild_snow_female": FallbackAssetProvenance(combinationId: "FEMALE_SNOW_MILD_DAWN_v16", sourceRowId: "f0f85719-0185-4c4c-8036-b03ef2757738", rung: "T1"),
        "mild_snow_male": FallbackAssetProvenance(combinationId: "MALE_SNOW_MILD_MIDDAY_v11", sourceRowId: "b756edc2-9f19-4ec1-942c-b071ba1e3709", rung: "T1"),
        "warm_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_WARM_MIDDAY_89c065aa", sourceRowId: "9886f191-13dd-4ae2-bcce-9f1094fcc9ca", rung: "T1"),
        "warm_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_WARM_MIDDAY_v3", sourceRowId: "9e4d573a-24cf-4d25-b454-888d9c121a71", rung: "T1"),
        "warm_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_WARM_MIDDAY_v2", sourceRowId: "7d3d5933-1dd7-4cd1-89ac-d7c06be8a836", rung: "T1"),
        "warm_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_WARM_DAWN_v5", sourceRowId: "121c9ee8-87b3-4e81-9090-e982ea35e62e", rung: "T1"),
        "warm_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_WARM_DAWN_v867", sourceRowId: "952172e9-748b-44d7-a68a-3652496d2625", rung: "T1"),
        "warm_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAINY_WARM_MIDDAY_v4", sourceRowId: "c368c886-a602-448b-93d7-4b7f9f1871a2", rung: "T1"),
        "hot_clear_female": FallbackAssetProvenance(combinationId: "FEMALE_CLEAR_HOT_MIDDAY_v1067", sourceRowId: "08c2e0ac-0631-46ad-9a76-36eda0a0bc30", rung: "T1"),
        "hot_clear_male": FallbackAssetProvenance(combinationId: "MALE_CLEAR_HOT_MIDDAY_v1", sourceRowId: "01cae610-4e85-404a-ad6b-79ce037c1d88", rung: "T1"),
        "hot_cloudy_female": FallbackAssetProvenance(combinationId: "FEMALE_CLOUDY_HOT_MIDDAY_v1", sourceRowId: "52097737-5b23-4419-bb63-917d84beaf7d", rung: "T1"),
        "hot_cloudy_male": FallbackAssetProvenance(combinationId: "MALE_CLOUDY_HOT_DUSK_v2", sourceRowId: "c6b19c8f-7e31-45a9-9a83-ba30d1cf9363", rung: "T1"),
        "hot_rain_female": FallbackAssetProvenance(combinationId: "FEMALE_RAIN_HOT_MIDDAY_49cd4d15", sourceRowId: "6ef05e97-3003-428a-9bce-0e61b11b0bee", rung: "T1"),
        "hot_rain_male": FallbackAssetProvenance(combinationId: "MALE_RAIN_HOT_DAWN_49cd4d15", sourceRowId: "66c5acd7-d0a6-4eba-b765-f12ec29e2eca", rung: "T1")
    ]

    /// RW-26, reframed by WS-7: the photographed subject's gender of
    /// the RW-20-era bundled asset for each of the 21 legacy cells,
    /// from the RW-20-verified provenance of its source pool row.
    /// Since WS-7 every cell carries BOTH genders' paired assets, so
    /// this census no longer decides gendered serves — the paired
    /// table does. Its remaining job is the ungendered legacy view
    /// (`legacyAssetNames`): it names which paired file the legacy
    /// chain lands on for each legacy cell, preserving the subject
    /// an ungendered request has always been served. FREEZING × RAIN
    /// has no entry: it was not a legacy cell, and the legacy chain
    /// still degrades it to freezing_clear.
    static let legacySubjects: [HeroTempBracket: [HeroWeatherCondition: HeroGender]] = [
        .FREEZING: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .SNOW: .MALE],
        .COLD: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .FEMALE],
        .COOL: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .MALE],
        .MILD: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .FEMALE],
        .WARM: [.CLEAR: .MALE, .CLOUDY: .MALE, .RAIN: .FEMALE],
        .HOT: [.CLEAR: .FEMALE, .CLOUDY: .MALE, .RAIN: .FEMALE]
    ]

    /// The ungendered view of the matrix: the legacy chain's table.
    /// Each of the 21 legacy cells maps to the paired file whose
    /// subject is the gender the RW-20 asset for that cell carried
    /// (the `legacySubjects` census), so an ungendered request keeps
    /// the legacy chain byte-for-byte — exact cell → bracket CLEAR →
    /// MILD × CLEAR, landing on the same subject it always landed
    /// on. FREEZING × RAIN is deliberately absent: under the legacy
    /// chain it still degrades to freezing_clear, exactly as before
    /// WS-7. No ungendered-specific asset exists or is needed. Since
    /// WS-6 (D-20) the legacy view serves only a unisex preference
    /// (and the watch — see the WS-6 gate report): every app-target
    /// painting call site passes the user's fit preference.
    static let legacyAssetNames: [HeroTempBracket: [HeroWeatherCondition: String]] = {
        var view: [HeroTempBracket: [HeroWeatherCondition: String]] = [:]
        for (bracket, conditions) in legacySubjects {
            var row: [HeroWeatherCondition: String] = [:]
            for (condition, subject) in conditions {
                row[condition] = pairedAssets[bracket]?[condition]?[subject]
            }
            view[bracket] = row
        }
        return view
    }()

    // MARK: - Accessors (tests, telemetry, mirrors)

    /// The gender-paired bundled asset base name for an exact cell,
    /// or nil when the cell is a non-cell (HOT × SNOW, WARM × SNOW).
    static func pairedAssetName(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        gender: HeroGender
    ) -> String? {
        pairedAssets[temp]?[weather]?[gender]
    }

    /// WS-7 / D-22: the manifest provenance of a bundled asset base
    /// name, or nil when the name is not a bundled fallback asset.
    static func provenance(forAssetName name: String) -> FallbackAssetProvenance? {
        provenanceByAsset[name]
    }

    /// The bundled asset file base name for a fallback URL string
    /// (last path component, `.webp` stripped) — mirror of Android's
    /// HeroImageSelector.fallbackAssetFile.
    static func assetFile(fromURLString url: String) -> String {
        let last = url.split(separator: "/").last.map(String.init) ?? url
        return last.hasSuffix(".webp") ? String(last.dropLast(5)) : last
    }

    /// WS-7 / D-22: the manifest provenance of a bundled asset URL,
    /// or nil when the URL does not name a bundled fallback asset.
    static func provenance(forURLString url: String) -> FallbackAssetProvenance? {
        provenanceByAsset[assetFile(fromURLString: url)]
    }

    /// Whether a URL string names a bundled fallback asset (the
    /// ground truth a serve record keys on — mirror of Android's
    /// bundled-asset URL check in HeroServeTelemetry.emitForResult).
    static func isBundledURL(_ url: String) -> Bool {
        url.contains("/hero_fallback/") && provenanceByAsset[assetFile(fromURLString: url)] != nil
    }

    // MARK: - Resolution

    /// Warmth order (coldest first) for walking neighbouring brackets.
    private let bracketWarmthOrder: [HeroTempBracket] = [.FREEZING, .COLD, .COOL, .MILD, .WARM, .HOT]

    /// Candidate cells for a fallback request, most-preferred first:
    /// the requested cell, then the rest of its bracket (CLEAR, CLOUDY,
    /// RAIN, SNOW), then neighbouring brackets by warmth distance
    /// (warmer first on a tie), each contributing its requested-weather
    /// cell first, then CLEAR, CLOUDY, RAIN, SNOW. Only cells that hold
    /// a paired asset are returned. Mirror of Android's
    /// orderedFallbackCells over the paired table (22 cells).
    private func orderedFallbackCells(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition
    ) -> [(HeroTempBracket, HeroWeatherCondition)] {
        var seen = Set<String>()
        var cells: [(HeroTempBracket, HeroWeatherCondition)] = []
        func add(bracket: HeroTempBracket, condition: HeroWeatherCondition) {
            let key = "\(bracket)|\(condition)"
            guard seen.insert(key).inserted, Self.pairedAssets[bracket]?[condition] != nil else { return }
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

    /// The bundled asset NAME a fallback request resolves to, before
    /// bundle-URL resolution. Internal (not private) so unit tests can
    /// pin the resolution in a logic-test host, where Bundle.main is
    /// the test runner and no app resources resolve.
    ///
    /// WS-7 bound (D-19): with a gender, the resolution starts and
    /// normally ends at the requested cell's own paired asset; the
    /// scene-first walk below it is reachable only from a non-cell
    /// (HOT × SNOW, WARM × SNOW) and takes the requested gender's
    /// paired asset at each stop, so gender is satisfied inside the
    /// nearest scene — it never crosses a weather class at a paired
    /// cell. Without a gender, the legacy chain over the legacy view
    /// is unchanged.
    func resolvedAssetName(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        gender: HeroGender? = nil
    ) -> String {
        if let gender {
            if let exact = Self.pairedAssets[temp]?[weather]?[gender] {
                return exact
            }
            for (bracket, condition) in orderedFallbackCells(temp: temp, weather: weather) {
                if let name = Self.pairedAssets[bracket]?[condition]?[gender] {
                    return name
                }
            }
        }
        return Self.legacyAssetNames[temp]?[weather]
            ?? Self.legacyAssetNames[temp]?[.CLEAR]
            ?? Self.legacyAssetNames[.MILD]?[.CLEAR]
            ?? "mild_clear_female"
    }

    /// The bundle URL string for a KNOWN bundled asset name (the
    /// gated resolution's output), or "" where bundle URLs cannot
    /// resolve (a logic-test host).
    func imageURL(forAssetName name: String) -> String {
        bundledURLString(assetName: name) ?? ""
    }

    func getImageURL(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        size: ImageSize = .iOS,
        gender: HeroGender? = nil
    ) -> String {
        let name = resolvedAssetName(temp: temp, weather: weather, gender: gender)
        return bundledURLString(assetName: name)
            ?? bundledURLString(assetName: "mild_clear_female")
            ?? ""
    }

    /// Returns a HeroImageResult for fallback. WS-7: the result is a
    /// bundled serve — it carries its serve source, the asset file
    /// served, and that asset's manifest provenance (D-22), derived
    /// from the resolved NAME so the record is complete even where
    /// bundle-URL resolution cannot run (a logic-test host).
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
        let name = resolvedAssetName(temp: temp, weather: weather, gender: gender)
        let url = getImageURL(temp: temp, weather: weather, size: size, gender: gender)
        return HeroImageResult.fromBundledFallback(
            url: url,
            assetFile: name,
            provenance: Self.provenanceByAsset[name]
        )
    }

    // MARK: - GEN-3 point-grain gate (WS-A(1))

    /// GEN-3 (WS-A(1), 2026-11-04): the ADJUDICATED garment classes
    /// of one bundled asset — what the photo actually wears, in the
    /// RW-20 vocabulary the shared point-grain policy judges. Mirror
    /// of Android GEN-2 A2's FallbackGarments census (same source:
    /// the RW-26 derivation record — every asset's source pool row
    /// was adjudicated person_count = 1, garment_source =
    /// 'adjudicated', qualified at its cell's cold-end prescription).
    /// `topBase` is nil where the base is unperceived beneath a
    /// present shell, which the policy accepts exactly as it does
    /// for a pool row. An asset with no census entry fails closed.
    struct BundledGarments: Equatable {
        let topBase: String?
        let outer: Bool
        let bottom: String
        let head: String?
        let hands: String?
    }

    /// The garment census of the 44 bundled assets, keyed by asset
    /// file base name — byte-for-byte the Android A2 table.
    static let garmentsByAsset: [String: BundledGarments] = [
        "freezing_clear_female": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "freezing_clear_male": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "freezing_cloudy_female": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "freezing_cloudy_male": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "freezing_rain_female": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "freezing_rain_male": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "freezing_snow_female": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "freezing_snow_male": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_clear_female": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_clear_male": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_cloudy_female": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_cloudy_male": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_rain_female": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "cold_rain_male": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "cold_snow_female": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cold_snow_male": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "beanie", hands: "gloves"),
        "cool_clear_female": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "cool_clear_male": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "cool_cloudy_female": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "cool_cloudy_male": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "cool_rain_female": BundledGarments(topBase: "long", outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "cool_rain_male": BundledGarments(topBase: nil, outer: true, bottom: "tights", head: "cap", hands: "gloves"),
        "cool_snow_female": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "cool_snow_male": BundledGarments(topBase: "long", outer: false, bottom: "tights", head: "headband", hands: "gloves"),
        "mild_clear_female": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "mild_clear_male": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "mild_cloudy_female": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "mild_cloudy_male": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "mild_rain_female": BundledGarments(topBase: "short", outer: true, bottom: "shorts", head: "cap", hands: nil),
        "mild_rain_male": BundledGarments(topBase: "short", outer: true, bottom: "shorts", head: "cap", hands: nil),
        "mild_snow_female": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "mild_snow_male": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "warm_clear_female": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "warm_clear_male": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "warm_cloudy_female": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "warm_cloudy_male": BundledGarments(topBase: "short", outer: false, bottom: "shorts", head: nil, hands: nil),
        "warm_rain_female": BundledGarments(topBase: "short", outer: true, bottom: "shorts", head: "cap", hands: nil),
        "warm_rain_male": BundledGarments(topBase: "short", outer: true, bottom: "shorts", head: "cap", hands: nil),
        "hot_clear_female": BundledGarments(topBase: "sleeveless", outer: false, bottom: "shorts", head: nil, hands: nil),
        "hot_clear_male": BundledGarments(topBase: "sleeveless", outer: false, bottom: "shorts", head: nil, hands: nil),
        "hot_cloudy_female": BundledGarments(topBase: "sleeveless", outer: false, bottom: "short_shorts", head: nil, hands: nil),
        "hot_cloudy_male": BundledGarments(topBase: "sleeveless", outer: false, bottom: "shorts", head: nil, hands: nil),
        "hot_rain_female": BundledGarments(topBase: "sleeveless", outer: true, bottom: "shorts", head: "cap", hands: nil),
        "hot_rain_male": BundledGarments(topBase: "sleeveless", outer: true, bottom: "shorts", head: "cap", hands: nil)
    ]

    /// May this bundled asset serve under `outfit`'s exact point
    /// prescription? The SAME shared point-grain gate the pool obeys
    /// (HeroPointGrainPolicy via its key bridge), fed the asset's
    /// adjudicated classes as a synthetic pool row (person count 1
    /// for every asset, per the derivation record). Uncensused
    /// assets fail closed. Mirror of Android's
    /// HeroImageSelector.fallbackMatchesPrescription.
    static func bundledAssetMatches(assetName: String, outfit: OutfitRecommendation) -> Bool {
        guard let garments = garmentsByAsset[assetName] else { return false }
        let row = GeneratedImage(
            id: provenanceByAsset[assetName]?.sourceRowId ?? assetName,
            combinationId: provenanceByAsset[assetName]?.combinationId ?? assetName,
            imageUrl: assetName,
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved",
            personCount: 1,
            garmentClasses: GarmentClasses(
                topBase: garments.topBase, outer: garments.outer, mid: false,
                bottom: garments.bottom, head: garments.head, hands: garments.hands,
                sunglasses: false, gaiter: false
            ),
            garmentSource: "adjudicated"
        )
        return HeroPointGrainPolicy.matches(image: row, outfit: outfit)
    }

    /// GEN-3 (WS-A(1)): the garment-gated bundled resolution — the
    /// fallback terminus under the RW-20 invariant (a serve that
    /// cannot match the prescription never serves). The walk visits
    /// cells in the established order (orderedFallbackCells:
    /// requested cell, its bracket's weathers, neighbouring
    /// brackets by warmth) and considers assets in gender order
    /// mirroring the pool cascade: the requested gender first across
    /// the whole walk, then the opposite gender; an ungendered
    /// request considers the legacy-view asset per cell first, then
    /// either paired asset. The first asset whose adjudicated
    /// garments MATCH the point prescription wins — garment truth
    /// outranks scene proximity (a nearer photo wearing
    /// unprescribed garments is the W-4 defect). Returns nil when
    /// no bundled asset matches: there is no truthful bundled photo
    /// for that prescription, and the caller falls to the pool
    /// serve (or, when the pool cannot serve either, reports the
    /// cell — the GEN-3 servability census carries those cells
    /// with row-level evidence). Mirror of Android's
    /// HeroImageSelector.gatedFallbackUrl, returning the asset NAME.
    func gatedAssetName(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        gender: HeroGender? = nil,
        outfit: OutfitRecommendation
    ) -> String? {
        let cells = orderedFallbackCells(temp: temp, weather: weather)
        let passes: [[String?]]
        if let gender {
            passes = [
                cells.map { Self.pairedAssets[$0.0]?[$0.1]?[gender] },
                cells.map { Self.pairedAssets[$0.0]?[$0.1]?[gender.opposite] }
            ]
        } else {
            passes = [
                cells.map { Self.legacyAssetNames[$0.0]?[$0.1] },
                cells.flatMap { cell -> [String?] in
                    [
                        Self.pairedAssets[cell.0]?[cell.1]?[.FEMALE],
                        Self.pairedAssets[cell.0]?[cell.1]?[.MALE]
                    ]
                }
            ]
        }
        for pass in passes {
            for name in pass {
                if let name, Self.bundledAssetMatches(assetName: name, outfit: outfit) {
                    return name
                }
            }
        }
        return nil
    }

    /// The gated bundled serve as a result: nil when no bundled
    /// asset matches the prescription (see gatedAssetName).
    func getGatedFallbackResult(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        isWatch: Bool = false,
        gender: HeroGender? = nil,
        outfit: OutfitRecommendation
    ) -> HeroImageResult? {
        guard let name = gatedAssetName(temp: temp, weather: weather, gender: gender, outfit: outfit) else {
            return nil
        }
        let url = bundledURLString(assetName: name) ?? ""
        return HeroImageResult.fromBundledFallback(
            url: url,
            assetFile: name,
            provenance: Self.provenanceByAsset[name]
        )
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

// MARK: - GEN-4 fallback chain (hero-fallback lane, 2026-11-04)
//
// Sol's ruling (2026-11-04, ~20:08 CST): the stock hero must
// NEVER settle on the bare gradient. The GEN-3 point-grain gate
// is a CLAIM rule — it governs what a photo may claim to be —
// and under it 26 of the 50 gendered gate signatures in the
// GEN-3 servability census resolve to no photo at all (mittens
// vocabulary = 0 pool rows; long-sleeve + shorts with bare
// head/hands; warm-point tights combinations; male/sleeveless
// visor), so those prescriptions painted the gradient. The
// chain below is the serving rule that sits UNDER the claim
// rule, at the same single resolution point GEN-3 built
// (HeroImageService's cascade terminus + WeatherViewModel's
// atomic commit, whose resolution feeds the hero, the Wear
// I've Run records, the collection/poster thumbnails and the
// onboarding fork band through alwaysAvailableHeroReference):
//
//   1. EXACT — a candidate whose adjudicated garments PASS the
//      shared point-grain gate for the prescription's signature
//      (pool rows first, then bundled assets; today's behaviour,
//      via HeroPointGrainPolicy / bundledAssetMatches).
//   2. SCAFFOLD — no exact candidate exists and a live pool
//      consultation happened: the NEAREST covered photo under
//      the metric below, pool candidates and bundled assets in
//      one candidate set (pool wins a tie). Recorded/presented
//      as a scaffold (HeroServeSource.scaffold + tier), never
//      as the exact photo, on any surface.
//   3. BUNDLED FLOOR — no live pool consultation was available
//      at this layer (the pool fetch failed, or the synchronous
//      pre-paint commit, which must paint before any pool
//      answer exists): the nearest BUNDLED asset under the same
//      metric. The bundled matrix is paired for every gender ×
//      bracket family, so the floor exists for EVERY
//      prescription group — the gradient is no longer a
//      reachable stock-hero state while a prescription exists.
//
// CONVERGENCE: the resolution is computed from the LIVE
// candidate set at serve time — there is no per-signature
// scaffold table. When refreshed exact stock lands in the pool
// (the WS-A distribution path), the same prescription's next
// resolution finds it at tier 1 through the unchanged gate and
// the scaffold/floor steps aside automatically.
//
// THE METRIC (deterministic nearest neighbour over the
// signature/condition space; the Android GEN-4 lane's semantics
// under the same constraints):
//   * HARD FILTER — gender: a gendered request only ever
//     considers candidates of that gender. NEVER crossed, at
//     any tier (the GEN-3 gated bundled walk's opposite-gender
//     pass is not used by this chain).
//   * Distance is a lexicographic tuple, lower wins:
//       (a) bracket distance — |index(candidate bracket) −
//           index(requested bracket)| in FREEZING…HOT order.
//           Temperature/bracket distance DOMINATES garment
//           detail: any same-bracket photo outranks any
//           other-bracket photo, however close its garments.
//       (b) weather distance — 0 exact; 1 within the dry pair
//           (CLEAR/CLOUDY) or within the precipitation pair
//           (RAIN/SNOW); 2 across the pairs; 2 when the
//           candidate's weather is unreadable.
//       (c) garment score — weighted signature distance:
//           bottom family mismatch 40 (shorts family
//           {shorts, short_shorts} vs tights family
//           {tights, capris} — the gate's families); top-base
//           ordinal distance × 15 (sleeveless < short < long;
//           an unreadable base under a shell counts 7 — the
//           policy likewise does not treat it as a visible
//           contradiction); outer present/absent mismatch 20;
//           head: exact 0, both-worn-but-different 5,
//           worn-vs-bare 10; hands: exact 0,
//           gloves-vs-mittens 4 (hand coverage present, class
//           wrong — the mittens family's nearest truth),
//           covered-vs-bare 8.
//   * Tie-breaks, in order: pool before bundled; then the
//     candidate's deterministic key (combination id / asset
//     name) ascending. Same inputs → same photo, every time.
//
// A pool row is a chain candidate only when it is servable
// (approved), single-person (person_count < 2 when recorded),
// carries adjudicated garment classes a signature can be read
// from, and its combination id parses to a gender / weather /
// bracket the metric can rank. A row that cannot be ranked
// cannot scaffold — the bundled floor still covers the serve.

/// The point-grain gate signature of a prescription or a photo:
/// (top-base class, outer present, bottom family, head class,
/// hands class) — the signature space the GEN-3 servability
/// census enumerated (50 gendered signatures).
struct HeroGarmentSignature: Equatable {
    /// "sleeveless" / "short" / "long"; nil = base unreadable
    /// (beneath a present shell, per the census convention).
    let topBase: String?
    let outer: Bool
    /// "shorts" (shorts + short_shorts) or "tights" (tights +
    /// capris) — the point-grain policy's bottom families.
    let bottomFamily: String
    /// "none" when no head gear is worn/prescribed.
    let head: String
    /// "none" when hands are bare.
    let hands: String

    static func bottomFamily(forBottomClass bottom: String?) -> String? {
        switch bottom {
        case "shorts", "short_shorts": return "shorts"
        case "tights", "capris": return "tights"
        default: return nil
        }
    }

    /// The signature a prescription asks for. The key → class
    /// mapping is the shared point-grain policy's own
    /// (HeroPoolPolicy.kt): TANK_TOP→sleeveless,
    /// SHORT_SLEEVE→short, else long; VISOR→visor,
    /// BASEBALL_CAP→cap, HEADBAND→headband, beanies→beanie,
    /// BALACLAVA→balaclava; LIGHT/THERMAL_GLOVES→gloves,
    /// MITTENS→mittens.
    static func prescribed(from outfit: OutfitRecommendation) -> HeroGarmentSignature {
        let top: String
        switch outfit.topBase.key {
        case "TANK_TOP": top = "sleeveless"
        case "SHORT_SLEEVE": top = "short"
        default: top = "long"
        }
        let head: String
        switch outfit.head?.key {
        case "VISOR": head = "visor"
        case "BASEBALL_CAP": head = "cap"
        case "HEADBAND": head = "headband"
        case "LIGHT_BEANIE", "THERMAL_BEANIE": head = "beanie"
        case "BALACLAVA": head = "balaclava"
        default: head = "none"
        }
        let hands: String
        switch outfit.hands?.key {
        case "LIGHT_GLOVES", "THERMAL_GLOVES": hands = "gloves"
        case "MITTENS": hands = "mittens"
        default: hands = "none"
        }
        let bottom = (outfit.bottom.key == "SHORTS" || outfit.bottom.key == "SHORT_SHORTS") ? "shorts" : "tights"
        return HeroGarmentSignature(
            topBase: top,
            outer: outfit.topOuter != nil,
            bottomFamily: bottom,
            head: head,
            hands: hands
        )
    }

    /// The signature a pool row's adjudicated classes record, or
    /// nil when no signature can be read (no classes, or an
    /// unreadable bottom — the bottom is the most visible
    /// garment; a photo whose bottom cannot be read cannot be
    /// ranked honestly).
    static func from(garmentClasses classes: GarmentClasses) -> HeroGarmentSignature? {
        guard let family = bottomFamily(forBottomClass: classes.bottom) else { return nil }
        return HeroGarmentSignature(
            topBase: classes.topBase,
            outer: classes.outer ?? false,
            bottomFamily: family,
            head: classes.head ?? "none",
            hands: classes.hands ?? "none"
        )
    }

    /// The signature a bundled asset's census entry records.
    static func from(bundledGarments garments: FallbackImageProvider.BundledGarments) -> HeroGarmentSignature {
        HeroGarmentSignature(
            topBase: garments.topBase,
            outer: garments.outer,
            bottomFamily: bottomFamily(forBottomClass: garments.bottom) ?? "shorts",
            head: garments.head ?? "none",
            hands: garments.hands ?? "none"
        )
    }
}

/// One ranked candidate in the GEN-4 chain: a live approved
/// pool row, or a bundled asset.
struct HeroFallbackCandidate {
    enum Source: Equatable {
        case pool
        case bundled
    }

    let source: Source
    let gender: HeroGender?
    let bracket: HeroTempBracket?
    let weather: HeroWeatherCondition?
    let signature: HeroGarmentSignature
    /// The pool row, when source == .pool.
    let poolImage: GeneratedImage?
    /// The bundled asset base name, when source == .bundled.
    let bundledAssetName: String?

    /// Deterministic tie-break key: combination id for a pool
    /// row, asset name for a bundled asset.
    var sortKey: String {
        poolImage?.combinationId ?? bundledAssetName ?? ""
    }
}

/// The chain's distance: a lexicographic tuple (bracket,
/// weather, garment) — see the metric definition above.
struct HeroFallbackDistance: Equatable, Comparable {
    let bracket: Int
    let weather: Int
    let garment: Int

    static func < (lhs: HeroFallbackDistance, rhs: HeroFallbackDistance) -> Bool {
        if lhs.bracket != rhs.bracket { return lhs.bracket < rhs.bracket }
        if lhs.weather != rhs.weather { return lhs.weather < rhs.weather }
        return lhs.garment < rhs.garment
    }
}

/// The GEN-4 chain's resolution: the tier it landed at, the
/// candidate that serves, and how far that candidate sits from
/// the prescription under the metric (zero garment distance for
/// an exact serve is NOT required — exactness is the gate's
/// verdict, not the metric's; the distance is reported for the
/// scaffold/floor tiers).
struct HeroFallbackResolution {
    let tier: HeroFallbackTier
    let candidate: HeroFallbackCandidate
    let distance: HeroFallbackDistance

    var isScaffold: Bool { tier == .scaffold }
    var bundledAssetName: String? { candidate.bundledAssetName }
}

/// The GEN-4 fallback chain: pure resolution over a candidate
/// set supplied at serve time (convergence — see the header).
enum HeroFallbackChain {
    /// Warmth order (coldest first) — the bracket axis.
    static let bracketOrder: [HeroTempBracket] = [.FREEZING, .COLD, .COOL, .MILD, .WARM, .HOT]

    static func bracketIndex(_ bracket: HeroTempBracket) -> Int {
        bracketOrder.firstIndex(of: bracket) ?? 2
    }

    static func weatherDistance(
        requested: HeroWeatherCondition,
        candidate: HeroWeatherCondition?
    ) -> Int {
        guard let candidate else { return 2 }
        if candidate == requested { return 0 }
        let dry: Set<HeroWeatherCondition> = [.CLEAR, .CLOUDY]
        if dry.contains(candidate) == dry.contains(requested) { return 1 }
        return 2
    }

    /// Weighted garment-signature distance (metric step (c)).
    static func garmentScore(
        requested: HeroGarmentSignature,
        candidate: HeroGarmentSignature
    ) -> Int {
        var score = 0
        if candidate.bottomFamily != requested.bottomFamily { score += 40 }
        let topOrder = ["sleeveless": 0, "short": 1, "long": 2]
        if let candidateTop = candidate.topBase, let requestedTop = requested.topBase,
           let ci = topOrder[candidateTop], let ri = topOrder[requestedTop] {
            score += abs(ci - ri) * 15
        } else if candidate.topBase == nil {
            score += 7
        }
        if candidate.outer != requested.outer { score += 20 }
        if candidate.head != requested.head {
            score += (candidate.head != "none" && requested.head != "none") ? 5 : 10
        }
        if candidate.hands != requested.hands {
            score += (candidate.hands != "none" && requested.hands != "none") ? 4 : 8
        }
        return score
    }

    static func distance(
        requestedSignature: HeroGarmentSignature,
        requestedBracket: HeroTempBracket,
        requestedWeather: HeroWeatherCondition,
        candidate: HeroFallbackCandidate
    ) -> HeroFallbackDistance {
        HeroFallbackDistance(
            bracket: candidate.bracket.map { abs(bracketIndex($0) - bracketIndex(requestedBracket)) } ?? 99,
            weather: weatherDistance(requested: requestedWeather, candidate: candidate.weather),
            garment: garmentScore(requested: requestedSignature, candidate: candidate.signature)
        )
    }

    /// Parse a pool row's combination id
    /// ({GENDER}_{WEATHER}_{BRACKET}_…, RAINY/SNOWY are the
    /// legacy weather spellings) into the metric's axes. Nil
    /// when any axis is unreadable — an unrankable row.
    static func combinationAxes(
        _ combinationId: String
    ) -> (gender: HeroGender, weather: HeroWeatherCondition, bracket: HeroTempBracket)? {
        let tokens = combinationId.split(separator: "_").map(String.init)
        guard tokens.count >= 3,
              let gender = HeroGender(rawValue: tokens[0]),
              let bracket = HeroTempBracket(rawValue: tokens[2]) else { return nil }
        let weather: HeroWeatherCondition?
        switch tokens[1] {
        case "RAINY": weather = .RAIN
        case "SNOWY": weather = .SNOW
        default: weather = HeroWeatherCondition(rawValue: tokens[1])
        }
        guard let weather else { return nil }
        return (gender, weather, bracket)
    }

    /// A pool row as a chain candidate, or nil when the row is
    /// not rankable (unservable, multi-person, no readable
    /// signature, unparseable combination).
    static func poolCandidate(from image: GeneratedImage) -> HeroFallbackCandidate? {
        guard HeroPoolPolicy.isServable(reviewStatus: image.reviewStatus),
              (image.personCount ?? 1) < 2,
              let classes = image.garmentClasses,
              let signature = HeroGarmentSignature.from(garmentClasses: classes),
              let axes = combinationAxes(image.combinationId) else { return nil }
        return HeroFallbackCandidate(
            source: .pool,
            gender: axes.gender,
            bracket: axes.bracket,
            weather: axes.weather,
            signature: signature,
            poolImage: image,
            bundledAssetName: nil
        )
    }

    /// Resolve the chain for one prescription.
    /// - Parameters:
    ///   - poolCandidates: the live approved pool rows the app's
    ///     read path returned for this serve (any cell — the
    ///     metric ranks them), or empty.
    ///   - poolReachable: whether a live pool consultation
    ///     happened at this layer at all. False selects the
    ///     bundled floor; true with no exact candidate selects
    ///     a scaffold (pool and bundled candidates together).
    ///   - bundledCandidates: the bundled candidate set
    ///     (FallbackImageProvider.bundledCandidates).
    static func resolve(
        requestedBracket: HeroTempBracket,
        requestedWeather: HeroWeatherCondition,
        gender: HeroGender?,
        outfit: OutfitRecommendation,
        poolCandidates: [GeneratedImage],
        poolReachable: Bool,
        bundledCandidates: [HeroFallbackCandidate]
    ) -> HeroFallbackResolution? {
        let requestedSignature = HeroGarmentSignature.prescribed(from: outfit)
        func genderOK(_ candidateGender: HeroGender?) -> Bool {
            guard let gender else { return true }
            return candidateGender == gender
        }
        let pool = poolCandidates
            .compactMap(poolCandidate(from:))
            .filter { genderOK($0.gender) }
            .sorted { $0.sortKey < $1.sortKey }
        let bundled = bundledCandidates
            .filter { genderOK($0.gender) }
            .sorted { $0.sortKey < $1.sortKey }

        func ranked(_ candidate: HeroFallbackCandidate) -> HeroFallbackDistance {
            distance(
                requestedSignature: requestedSignature,
                requestedBracket: requestedBracket,
                requestedWeather: requestedWeather,
                candidate: candidate
            )
        }

        // Tier 1 — EXACT: the gate's verdict, pool first, then
        // bundled (today's behaviour).
        for candidate in pool {
            if let image = candidate.poolImage,
               HeroPointGrainPolicy.matches(image: image, outfit: outfit) {
                return HeroFallbackResolution(tier: .exact, candidate: candidate, distance: ranked(candidate))
            }
        }
        for candidate in bundled {
            if let name = candidate.bundledAssetName,
               FallbackImageProvider.bundledAssetMatches(assetName: name, outfit: outfit) {
                return HeroFallbackResolution(tier: .exact, candidate: candidate, distance: ranked(candidate))
            }
        }

        // Tier 2 — SCAFFOLD (a live pool consultation happened):
        // nearest covered photo across pool + bundled, pool
        // winning a tie.
        if poolReachable {
            let all = pool + bundled
            guard let best = all.min(by: { a, b in
                let da = ranked(a), db = ranked(b)
                if da != db { return da < db }
                if a.source != b.source { return a.source == .pool }
                return a.sortKey < b.sortKey
            }) else { return nil }
            return HeroFallbackResolution(tier: .scaffold, candidate: best, distance: ranked(best))
        }

        // Tier 3 — BUNDLED FLOOR: nearest bundled asset.
        guard let best = bundled.min(by: { a, b in
            let da = ranked(a), db = ranked(b)
            if da != db { return da < db }
            return a.sortKey < b.sortKey
        }) else { return nil }
        return HeroFallbackResolution(tier: .bundledFloor, candidate: best, distance: ranked(best))
    }
}

extension FallbackImageProvider {
    /// The bundled candidate set for the GEN-4 chain: every
    /// paired asset with a garment-census entry, keyed by its
    /// cell (gender × bracket × weather). Completeness audit:
    /// the paired matrix covers every gender × bracket family
    /// (22 cells × 2 genders — HOT×SNOW and WARM×SNOW are
    /// non-cells with no bracket-family gap), so under the
    /// metric every prescription group has a bundled floor
    /// candidate of its own gender; Gen4HeroFallbackTests pins
    /// all 50 census signatures against this set.
    static var bundledCandidates: [HeroFallbackCandidate] {
        var candidates: [HeroFallbackCandidate] = []
        for (bracket, conditions) in pairedAssets {
            for (weather, genders) in conditions {
                for (gender, name) in genders {
                    guard let garments = garmentsByAsset[name] else { continue }
                    candidates.append(HeroFallbackCandidate(
                        source: .bundled,
                        gender: gender,
                        bracket: bracket,
                        weather: weather,
                        signature: HeroGarmentSignature.from(bundledGarments: garments),
                        poolImage: nil,
                        bundledAssetName: name
                    ))
                }
            }
        }
        return candidates.sorted { $0.sortKey < $1.sortKey }
    }

    /// Resolve the GEN-4 chain through this provider's bundled
    /// candidate set. See HeroFallbackChain.resolve.
    func chainResolution(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition,
        gender: HeroGender?,
        outfit: OutfitRecommendation,
        poolCandidates: [GeneratedImage] = [],
        poolReachable: Bool = false
    ) -> HeroFallbackResolution? {
        HeroFallbackChain.resolve(
            requestedBracket: temp,
            requestedWeather: weather,
            gender: gender,
            outfit: outfit,
            poolCandidates: poolCandidates,
            poolReachable: poolReachable,
            bundledCandidates: Self.bundledCandidates
        )
    }

    /// The chain resolution as a HeroImageResult: a pool exact
    /// or scaffold carries the row's own identity; a bundled
    /// exact/scaffold/floor carries the asset, its provenance
    /// and the tier (fromBundledFallback maps the tier onto the
    /// serve source — scaffold results are labelled scaffold,
    /// never bundled-fallback/exact).
    func result(for resolution: HeroFallbackResolution) -> HeroImageResult? {
        switch resolution.candidate.source {
        case .pool:
            guard let image = resolution.candidate.poolImage else { return nil }
            if resolution.tier == .exact {
                return HeroImageResult.fromMatch(image: image, fallbackLevel: 2)
            }
            return HeroImageResult.fromScaffoldPool(image: image)
        case .bundled:
            guard let name = resolution.candidate.bundledAssetName else { return nil }
            let url = bundledURLString(assetName: name) ?? ""
            return HeroImageResult.fromBundledFallback(
                url: url,
                assetFile: name,
                provenance: Self.provenanceByAsset[name],
                tier: resolution.tier
            )
        }
    }
}
