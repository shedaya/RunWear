import XCTest

/// GEN-4 hero-fallback lane (2026-11-04): the stock hero must
/// NEVER settle on the bare gradient (Sol's ruling, ~20:08 CST).
///
/// The GEN-3 servability census
/// (release-prep/rw-gen3-ahero-2026-11-04/servability-census.md)
/// enumerated 50 gendered gate signatures / 180 point
/// prescriptions: 24 signatures servable exactly, 26 resolving
/// to NO photo under the point-grain gate — the gate is a CLAIM
/// rule (what a photo may claim to be), and the gradient was
/// its terminus. The GEN-4 chain (HeroFallbackChain) is the
/// serving rule under the claim rule:
///
///   EXACT (gate pass, pool then bundled) → SCAFFOLD (nearest
///   covered same-gender photo from the live candidate set,
///   honestly labelled tier/serve-source `scaffold`) → BUNDLED
///   FLOOR (no live pool consultation: nearest bundled asset
///   under the same metric, for every prescription group).
///
/// The metric: gender is a HARD filter (never crossed, any
/// tier); distance is lexicographic (bracket, weather, garment)
/// so temperature/bracket distance dominates garment detail;
/// ties break pool-before-bundled, then by deterministic key.
final class Gen4HeroFallbackTests: XCTestCase {

    private let provider = FallbackImageProvider.shared

    // MARK: - Builders

    private func item(_ key: String) -> ClothingItem {
        ClothingItem(key: key, name: key, description: "", icon: "", amazonSearchTerm: "")
    }

    /// A prescription built directly from engine item keys —
    /// the signature seam reads keys only.
    private func outfit(
        top: String,
        outer: String?,
        bottom: String,
        head: String?,
        hands: String?
    ) -> OutfitRecommendation {
        OutfitRecommendation(
            temperatureBracket: .cool,
            topBase: item(top),
            topMid: nil,
            topOuter: outer.map(item),
            bottom: item(bottom),
            head: head.map(item),
            hands: hands.map(item),
            accessories: [],
            tips: []
        )
    }

    /// A prescription from a census gate signature:
    /// (topBase class, outer, bottom family, head class, hands class).
    private func outfit(signature sig: (String, Bool, String, String, String)) -> OutfitRecommendation {
        let topKey: String
        switch sig.0 {
        case "sleeveless": topKey = "TANK_TOP"
        case "short": topKey = "SHORT_SLEEVE"
        default: topKey = "LONG_SLEEVE_LIGHT"
        }
        let headKey: String?
        switch sig.3 {
        case "cap": headKey = "BASEBALL_CAP"
        case "headband": headKey = "HEADBAND"
        case "beanie": headKey = "LIGHT_BEANIE"
        case "balaclava": headKey = "BALACLAVA"
        case "visor": headKey = "VISOR"
        default: headKey = nil
        }
        let handsKey: String?
        switch sig.4 {
        case "gloves": handsKey = "LIGHT_GLOVES"
        case "mittens": handsKey = "MITTENS"
        default: handsKey = nil
        }
        return outfit(
            top: topKey,
            outer: sig.1 ? "WINDBREAKER" : nil,
            bottom: sig.2 == "shorts" ? "SHORTS" : "LIGHT_TIGHTS",
            head: headKey,
            hands: handsKey
        )
    }

    private func poolRow(
        id: String,
        combinationId: String,
        topBase: String?,
        outer: Bool,
        bottom: String,
        head: String?,
        hands: String?,
        personCount: Int? = 1
    ) -> GeneratedImage {
        GeneratedImage(
            id: id,
            combinationId: combinationId,
            imageUrl: "https://example.test/\(id).webp",
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved",
            personCount: personCount,
            garmentClasses: GarmentClasses(
                topBase: topBase, outer: outer, mid: false,
                bottom: bottom, head: head, hands: hands,
                sunglasses: false, gaiter: false
            ),
            garmentSource: "adjudicated"
        )
    }

    private func resolve(
        _ outfit: OutfitRecommendation,
        bracket: HeroTempBracket,
        weather: HeroWeatherCondition = .CLEAR,
        gender: HeroGender?,
        pool: [GeneratedImage] = [],
        reachable: Bool
    ) -> HeroFallbackResolution? {
        provider.chainResolution(
            temp: bracket, weather: weather, gender: gender,
            outfit: outfit, poolCandidates: pool, poolReachable: reachable
        )
    }

    /// The census's 25 gender-neutral gate signatures
    /// (topBase, outer, bottom, head, hands) — × 2 genders = the
    /// 50 gendered signatures of record.
    private let censusSignatures: [(String, Bool, String, String, String)] = [
        ("long", false, "shorts", "cap", "none"),
        ("long", false, "shorts", "none", "none"),
        ("long", false, "tights", "beanie", "gloves"),
        ("long", false, "tights", "cap", "none"),
        ("long", false, "tights", "headband", "gloves"),
        ("long", false, "tights", "none", "none"),
        ("long", true, "shorts", "cap", "none"),
        ("long", true, "shorts", "none", "none"),
        ("long", true, "tights", "balaclava", "mittens"),
        ("long", true, "tights", "beanie", "gloves"),
        ("long", true, "tights", "beanie", "mittens"),
        ("long", true, "tights", "cap", "gloves"),
        ("long", true, "tights", "cap", "mittens"),
        ("long", true, "tights", "cap", "none"),
        ("long", true, "tights", "headband", "gloves"),
        ("long", true, "tights", "none", "none"),
        ("short", false, "shorts", "cap", "none"),
        ("short", false, "shorts", "none", "none"),
        ("short", false, "shorts", "visor", "none"),
        ("short", true, "shorts", "cap", "none"),
        ("short", true, "shorts", "visor", "none"),
        ("sleeveless", false, "shorts", "none", "none"),
        ("sleeveless", false, "shorts", "visor", "none"),
        ("sleeveless", true, "shorts", "cap", "none"),
        ("sleeveless", true, "shorts", "visor", "none")
    ]

    // MARK: - Chain order: exact > scaffold > floor

    func testExactPoolBeatsScaffoldAndExactBundledBeatsScaffold() {
        // An uncovered-at-bracket prescription (long + tights,
        // bare head/hands) with an exact pool row in the live
        // set: the row serves EXACT, not as a scaffold.
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: nil, hands: nil)
        let exactRow = poolRow(
            id: "exact-1", combinationId: "FEMALE_CLEAR_COOL_DAWN_v9",
            topBase: "long", outer: false, bottom: "tights", head: nil, hands: nil
        )
        let res = resolve(prescription, bracket: .COOL, gender: .FEMALE, pool: [exactRow], reachable: true)
        XCTAssertEqual(res?.tier, .exact)
        XCTAssertEqual(res?.candidate.source, .pool)
        XCTAssertEqual(res?.candidate.poolImage?.id, "exact-1")

        // The COOL cold-end prescription passes the bundled gate:
        // exact bundled, even with a live pool consultation —
        // exact outranks scaffold.
        let coldEnd = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: "HEADBAND", hands: "LIGHT_GLOVES")
        let bundledRes = resolve(coldEnd, bracket: .COOL, gender: .FEMALE, pool: [], reachable: true)
        XCTAssertEqual(bundledRes?.tier, .exact)
        XCTAssertEqual(bundledRes?.bundledAssetName, "cool_clear_female")
    }

    func testScaffoldWhenPoolReachableAndFloorWhenItIsNot() {
        // The Kansas City 44°F shape: no exact candidate exists
        // for long + tights + bare/bare in the bundled set.
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: nil, hands: nil)
        let scaffold = resolve(prescription, bracket: .COOL, gender: .MALE, pool: [], reachable: true)
        XCTAssertEqual(scaffold?.tier, .scaffold)
        XCTAssertNotNil(scaffold?.bundledAssetName)
        // The SAME prescription with no live pool consultation
        // lands at the bundled floor — same asset (the metric
        // over the bundled set is one), floor tier.
        let floor = resolve(prescription, bracket: .COOL, gender: .MALE, pool: [], reachable: false)
        XCTAssertEqual(floor?.tier, .bundledFloor)
        XCTAssertEqual(floor?.bundledAssetName, scaffold?.bundledAssetName)
        XCTAssertEqual(floor?.candidate.source, .bundled)
    }

    func testConvergence_refreshedExactStockWinsAutomatically() {
        // No per-signature scaffold table: the resolution is a
        // function of the live candidate set, so when an exact
        // row lands in the pool the same prescription upgrades
        // from scaffold to exact with no code change.
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: nil, hands: nil)
        let before = resolve(prescription, bracket: .COOL, gender: .FEMALE, pool: [], reachable: true)
        XCTAssertEqual(before?.tier, .scaffold)
        let newRow = poolRow(
            id: "new-exact", combinationId: "FEMALE_CLEAR_COOL_MIDDAY_v42",
            topBase: "long", outer: false, bottom: "tights", head: nil, hands: nil
        )
        let after = resolve(prescription, bracket: .COOL, gender: .FEMALE, pool: [newRow], reachable: true)
        XCTAssertEqual(after?.tier, .exact)
        XCTAssertEqual(after?.candidate.poolImage?.id, "new-exact")
    }

    // MARK: - The metric

    func testMetric_bracketDistanceDominatesGarmentDetail() {
        let requested = HeroGarmentSignature.prescribed(
            from: outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: "LIGHT_BEANIE", hands: "LIGHT_GLOVES")
        )
        // Same bracket (COLD), garment score 20 (an unprescribed
        // shell over the otherwise-exact kit).
        let sameBracket = HeroFallbackCandidate(
            source: .bundled, gender: .FEMALE, bracket: .COLD, weather: .CLEAR,
            signature: HeroGarmentSignature(topBase: "long", outer: true, bottomFamily: "tights", head: "beanie", hands: "gloves"),
            poolImage: nil, bundledAssetName: "cold_clear_female"
        )
        // Neighbouring bracket (COOL), garment score 5.
        let neighbour = HeroFallbackCandidate(
            source: .bundled, gender: .FEMALE, bracket: .COOL, weather: .CLEAR,
            signature: HeroGarmentSignature(topBase: "long", outer: false, bottomFamily: "tights", head: "headband", hands: "gloves"),
            poolImage: nil, bundledAssetName: "cool_clear_female"
        )
        let dSame = HeroFallbackChain.distance(requestedSignature: requested, requestedBracket: .COLD, requestedWeather: .CLEAR, candidate: sameBracket)
        let dNeighbour = HeroFallbackChain.distance(requestedSignature: requested, requestedBracket: .COLD, requestedWeather: .CLEAR, candidate: neighbour)
        XCTAssertLessThan(dSame.garment, 25)
        XCTAssertGreaterThan(dNeighbour.bracket, dSame.bracket)
        XCTAssertTrue(dSame < dNeighbour, "bracket distance must dominate garment detail")
    }

    func testMetric_garmentWeights_mittensAreNearestToGloves() {
        let mittens = HeroGarmentSignature(topBase: "long", outer: true, bottomFamily: "tights", head: "beanie", hands: "mittens")
        let gloves = HeroGarmentSignature(topBase: "long", outer: true, bottomFamily: "tights", head: "beanie", hands: "gloves")
        let bare = HeroGarmentSignature(topBase: "long", outer: true, bottomFamily: "tights", head: "beanie", hands: "none")
        XCTAssertEqual(4, HeroFallbackChain.garmentScore(requested: mittens, candidate: gloves))
        XCTAssertEqual(8, HeroFallbackChain.garmentScore(requested: mittens, candidate: bare))
    }

    // MARK: - Defect family 1: mittens → nearest same-gender gloves scaffold

    func testMittensPrescriptionScaffoldsToSameGenderGloves() {
        for gender in [HeroGender.FEMALE, .MALE] {
            let suffix = gender == .FEMALE ? "female" : "male"
            // Sub-15°F: thermal + shell + thermal tights + beanie + MITTENS.
            let mittens = outfit(top: "LONG_SLEEVE_THERMAL", outer: "INSULATED_JACKET", bottom: "THERMAL_TIGHTS", head: "THERMAL_BEANIE", hands: "MITTENS")
            let res = resolve(mittens, bracket: .FREEZING, gender: gender, reachable: true)
            XCTAssertEqual(res?.tier, .scaffold)
            XCTAssertEqual(res?.candidate.gender, gender)
            XCTAssertEqual(res?.candidate.signature.hands, "gloves",
                           "the mittens family's nearest truth is a gloved photo of the same gender")
            XCTAssertEqual(res?.bundledAssetName, "freezing_clear_\(suffix)")
            // Balaclava + mittens (sub-5°F): same nearest family.
            let balaclava = outfit(top: "LONG_SLEEVE_THERMAL", outer: "INSULATED_JACKET", bottom: "THERMAL_TIGHTS", head: "BALACLAVA", hands: "MITTENS")
            let res2 = resolve(balaclava, bracket: .FREEZING, gender: gender, reachable: true)
            XCTAssertEqual(res2?.tier, .scaffold)
            XCTAssertEqual(res2?.candidate.signature.hands, "gloves")
            XCTAssertEqual(res2?.candidate.gender, gender)
        }
    }

    // MARK: - Defect family 2: shell-over-shorts, bare head/hands

    func testShellOverShortsBareBareScaffoldsSameBracket() {
        // The recorded Overland Park shape: long sleeve +
        // WINDBREAKER + shorts at COOL, bare head, bare hands —
        // zero pool rows, zero bundled exact.
        for gender in [HeroGender.FEMALE, .MALE] {
            let suffix = gender == .FEMALE ? "female" : "male"
            let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: "WINDBREAKER", bottom: "SHORTS", head: nil, hands: nil)
            let res = resolve(prescription, bracket: .COOL, gender: gender, reachable: true)
            XCTAssertEqual(res?.tier, .scaffold)
            XCTAssertEqual(res?.candidate.gender, gender)
            XCTAssertEqual(res?.candidate.bracket, .COOL,
                           "bracket dominates: the scaffold stays in COOL")
            XCTAssertEqual(res?.bundledAssetName, "cool_clear_\(suffix)")
        }
    }

    func testShellOverShortsPrefersANearPoolRowAsScaffold() {
        // A live pool row wearing shell + shorts + cap (gate
        // fails on the unprescribed cap) is nearer than every
        // bundled candidate: it scaffolds, with its OWN identity.
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: "WINDBREAKER", bottom: "SHORTS", head: nil, hands: nil)
        let nearRow = poolRow(
            id: "near-1", combinationId: "MALE_CLEAR_COOL_DAWN_v9",
            topBase: "long", outer: true, bottom: "shorts", head: "cap", hands: nil
        )
        let res = resolve(prescription, bracket: .COOL, gender: .MALE, pool: [nearRow], reachable: true)
        XCTAssertEqual(res?.tier, .scaffold)
        XCTAssertEqual(res?.candidate.source, .pool)
        XCTAssertEqual(res?.candidate.poolImage?.id, "near-1")
        // The result built from it is labelled scaffold and
        // carries the row's own combination — never the
        // requested cell's.
        let result = res.flatMap { provider.result(for: $0) }
        XCTAssertEqual(result?.fallbackTier, .scaffold)
        XCTAssertEqual(result?.serveSource, .scaffold)
        XCTAssertEqual(result?.fallbackLevel, 9)
        XCTAssertEqual(result?.imageRowId, "near-1")
        XCTAssertEqual(result?.combinationId, "MALE_CLEAR_COOL_DAWN_v9")
        XCTAssertTrue(result?.isScaffold ?? false)
    }

    // MARK: - Defect family 3: warm-point tights combinations

    func testWarmPointTights_noOuterBeanieGlovesScaffoldsWithinCold() {
        // The COLD 20–24°F calm prescription (long, no-outer,
        // tights, beanie, gloves): every beanie/gloves/tights
        // bundled asset wears a shell — garment score 20 — but
        // bracket dominance keeps the scaffold in COLD.
        for gender in [HeroGender.FEMALE, .MALE] {
            let suffix = gender == .FEMALE ? "female" : "male"
            let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "THERMAL_TIGHTS", head: "LIGHT_BEANIE", hands: "LIGHT_GLOVES")
            let res = resolve(prescription, bracket: .COLD, gender: gender, reachable: true)
            XCTAssertEqual(res?.tier, .scaffold)
            XCTAssertEqual(res?.bundledAssetName, "cold_clear_\(suffix)")
        }
    }

    func testWarmPointTights_capWithBareHandsScaffolds() {
        // (long, outer, tights, cap, none) — female-only defect
        // in the census (the male counterpart had one pool row).
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: "WINDBREAKER", bottom: "LIGHT_TIGHTS", head: "BASEBALL_CAP", hands: nil)
        let res = resolve(prescription, bracket: .COOL, gender: .FEMALE, reachable: true)
        XCTAssertEqual(res?.tier, .scaffold)
        XCTAssertEqual(res?.candidate.gender, .FEMALE)
        XCTAssertEqual(res?.candidate.bracket, .COOL)
        XCTAssertNotNil(res?.bundledAssetName)
    }

    // MARK: - Defect family 4: visor gaps

    func testMaleVisorScaffoldsToNearestMaleNeverFemaleVisor() {
        // Every visor row in the pool is FEMALE; there is no
        // male visor photo. The male visor prescription must
        // scaffold to the nearest MALE photo — never cross
        // gender to the female visor stock.
        let prescription = outfit(top: "SHORT_SLEEVE", outer: nil, bottom: "SHORTS", head: "VISOR", hands: nil)
        let res = resolve(prescription, bracket: .MILD, gender: .MALE, reachable: true)
        XCTAssertEqual(res?.tier, .scaffold)
        XCTAssertEqual(res?.candidate.gender, .MALE)
        XCTAssertEqual(res?.bundledAssetName, "mild_clear_male")
        // Male sleeveless + shell + visor (HOT RAIN).
        let hotVisor = outfit(top: "TANK_TOP", outer: "RAIN_JACKET", bottom: "SHORT_SHORTS", head: "VISOR", hands: nil)
        let hotRes = resolve(hotVisor, bracket: .HOT, weather: .RAIN, gender: .MALE, reachable: true)
        XCTAssertEqual(hotRes?.tier, .scaffold)
        XCTAssertEqual(hotRes?.bundledAssetName, "hot_rain_male")
        // Female sleeveless visor, no outer (HOT CLEAR).
        let femaleVisor = outfit(top: "TANK_TOP", outer: nil, bottom: "SHORT_SHORTS", head: "VISOR", hands: nil)
        let femaleRes = resolve(femaleVisor, bracket: .HOT, gender: .FEMALE, reachable: true)
        XCTAssertEqual(femaleRes?.tier, .scaffold)
        XCTAssertEqual(femaleRes?.bundledAssetName, "hot_clear_female")
    }

    // MARK: - Gender is never crossed

    func testGenderNeverCrossed_atAnyTier() {
        // Every census signature, both genders, scaffold tier:
        // the candidate's gender is always the requested one.
        for sig in censusSignatures {
            for gender in [HeroGender.FEMALE, .MALE] {
                let res = resolve(outfit(signature: sig), bracket: .COOL, gender: gender, reachable: true)
                XCTAssertEqual(res?.candidate.gender, gender,
                               "signature \(sig) crossed gender for \(gender)")
            }
        }
        // With ONLY opposite-gender candidates in the universe,
        // the chain resolves nil rather than crossing — at both
        // the scaffold and floor layers.
        let prescription = outfit(top: "LONG_SLEEVE_LIGHT", outer: nil, bottom: "LIGHT_TIGHTS", head: nil, hands: nil)
        let femaleOnly = FallbackImageProvider.bundledCandidates.filter { $0.gender == .FEMALE }
        XCTAssertNil(HeroFallbackChain.resolve(
            requestedBracket: .COOL, requestedWeather: .CLEAR, gender: .MALE,
            outfit: prescription, poolCandidates: [], poolReachable: true,
            bundledCandidates: femaleOnly
        ))
        XCTAssertNil(HeroFallbackChain.resolve(
            requestedBracket: .COOL, requestedWeather: .CLEAR, gender: .MALE,
            outfit: prescription, poolCandidates: [], poolReachable: false,
            bundledCandidates: femaleOnly
        ))
    }

    // MARK: - Floor completeness: all 50 signatures, pool empty

    func testBundledFloorCompleteness_all50SignaturesPoolEmpty() {
        XCTAssertEqual(25, censusSignatures.count)
        // The bundled candidate set itself: paired for every
        // gender × bracket family.
        let bundled = FallbackImageProvider.bundledCandidates
        XCTAssertEqual(44, bundled.count)
        for gender in [HeroGender.FEMALE, .MALE] {
            for bracket in HeroTempBracket.allCases {
                XCTAssertTrue(
                    bundled.contains { $0.gender == gender && $0.bracket == bracket },
                    "no bundled floor candidate for \(gender) × \(bracket)"
                )
            }
        }
        // Every census signature × gender × bracket, pool
        // unreachable/empty: a bundled resolution exists, of
        // the requested gender, at the exact or floor tier.
        for sig in censusSignatures {
            for gender in [HeroGender.FEMALE, .MALE] {
                for bracket in HeroTempBracket.allCases {
                    let res = resolve(
                        outfit(signature: sig), bracket: bracket,
                        gender: gender, pool: [], reachable: false
                    )
                    XCTAssertNotNil(res, "no floor for \(sig) × \(gender) × \(bracket)")
                    XCTAssertEqual(res?.candidate.source, .bundled)
                    XCTAssertEqual(res?.candidate.gender, gender)
                    XCTAssertTrue(res?.tier == .bundledFloor || res?.tier == .exact,
                                  "pool-unreachable resolution must be floor or exact bundled, got \(String(describing: res?.tier))")
                    XCTAssertNotNil(res?.bundledAssetName)
                }
            }
        }
    }

    // MARK: - Scaffold labelling iff tier == scaffold

    func testScaffoldLabelling_iffTierIsScaffold() {
        let provider = self.provider
        func bundledResult(tier: HeroFallbackTier) -> HeroImageResult? {
            let candidate = FallbackImageProvider.bundledCandidates.first { $0.bundledAssetName == "cool_clear_female" }!
            return provider.result(for: HeroFallbackResolution(
                tier: tier, candidate: candidate,
                distance: HeroFallbackDistance(bracket: 0, weather: 0, garment: 0)
            ))
        }
        let exact = bundledResult(tier: .exact)
        XCTAssertEqual(exact?.fallbackTier, .exact)
        XCTAssertEqual(exact?.serveSource, .bundledFallback)
        XCTAssertFalse(exact?.isScaffold ?? true)

        let scaffold = bundledResult(tier: .scaffold)
        XCTAssertEqual(scaffold?.fallbackTier, .scaffold)
        XCTAssertEqual(scaffold?.serveSource, .scaffold)
        XCTAssertTrue(scaffold?.isScaffold ?? false)

        let floor = bundledResult(tier: .bundledFloor)
        XCTAssertEqual(floor?.fallbackTier, .bundledFloor)
        XCTAssertEqual(floor?.serveSource, .bundledFallback)
        XCTAssertFalse(floor?.isScaffold ?? true)

        // The serve RECORD carries the label: a scaffold
        // record's summary says tier=scaffold and its source is
        // scaffold; a floor record's does not claim scaffold.
        let scaffoldRecord = HeroServeRecord(
            source: .scaffold, fallbackAssetFile: "cool_clear_female", fallbackTier: .scaffold
        )
        XCTAssertTrue(scaffoldRecord.summary().contains("source=scaffold"))
        XCTAssertTrue(scaffoldRecord.summary().contains("tier=scaffold"))
        let floorRecord = HeroServeRecord(
            source: .bundledFallback, fallbackAssetFile: "cool_clear_female", fallbackTier: .bundledFloor
        )
        XCTAssertTrue(floorRecord.summary().contains("source=bundled-fallback"))
        XCTAssertFalse(floorRecord.summary().contains("tier=scaffold"))
    }

    func testScaffoldBundledResultEmitsAScaffoldRecord_telemetry() {
        var captured: [HeroServeRecord] = []
        HeroServeTelemetry.installSink { captured.append($0) }
        defer { HeroServeTelemetry.installSink(HeroServeTelemetry.defaultSink) }
        // A bundled scaffold result (real bundled URL form) must
        // emit source=scaffold — the bundled-URL ground truth in
        // emitForResult must not relabel it bundled-fallback.
        let result = HeroImageResult.fromBundledFallback(
            url: "file:///RunWear.app/hero_fallback/cool_clear_female.webp",
            assetFile: "cool_clear_female",
            provenance: FallbackImageProvider.provenance(forAssetName: "cool_clear_female"),
            tier: .scaffold
        )
        HeroServeTelemetry.emitForResult(
            result,
            requestedCombinationId: "FEMALE_CLEAR_COOL_DAWN_v1",
            bracket: .COOL, weather: .CLEAR, gender: .female,
            trigger: "hero-fallback-chain"
        )
        XCTAssertEqual(1, captured.count)
        XCTAssertEqual(captured.first?.source, .scaffold)
        XCTAssertEqual(captured.first?.fallbackTier, .scaffold)
    }

    // MARK: - Wiring pins

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testChainIsWiredAtTheSingleResolutionPoint_sourcePin() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("chainResolution("),
                      "the view model's atomic commit must resolve through the GEN-4 chain")
        XCTAssertTrue(vm.contains("heroFallbackTier"),
                      "the committed resolution must carry its chain tier")
        let service = try sourceFile("../RunWear/Services/HeroImageService.swift")
        XCTAssertTrue(service.contains("chainResolution("),
                      "the cascade terminus must resolve through the GEN-4 chain")
        XCTAssertTrue(service.contains("fetchGeneratedImages"),
                      "the scaffold tier's pool candidates must come through the existing read path")
    }
}
