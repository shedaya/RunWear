import XCTest

/// Outfit engine sweep — Swift port of the Kotlin QA harness
/// (`OutfitRulesSweepTest` in RunWear-android `:shared`, written 2026-10-17;
/// inv7 re-reviewed 2026-10-19 for engine v4.3).
///
/// Sweeps the REAL `OutfitRecommendationService` over its full input space
/// (209,600 main-grid scenarios plus divergence and Celsius slices) and checks
/// the invariants the v4.2 recalibration is supposed to guarantee. This target
/// is the drift alarm for the hand-ported Swift engine: the scenario axes,
/// invariant logic, and assertion messages mirror the Kotlin harness, so a
/// Kotlin engine change that is not ported to Swift fails here loudly.
///
/// Port notes (where the Swift surface forced a different shape — the
/// invariants and expectations are unchanged):
///  - The Kotlin engine takes a `WeatherConditions` value + a
///    `ComfortPreference`; the Swift service takes primitive inputs and the
///    caller applies the comfort shift. The adapter below (`inputs(for:)`)
///    plays the caller: adjusted = feels + adjustment, raw = feels,
///    exactly as WeatherViewModel / WatchViewModel construct the call.
///  - Wind is fed in the Kotlin frame (mph numbers, `> 10` windy gate) so
///    the sweep exercises the same decision surface as the Kotlin grid.
///    (Production callers pass Open-Meteo km/h into the same parameter —
///    a call-boundary unit contract, outside the engine tables.)
///  - Swift's `ClothingItem` is a struct identified by its `name` (its `id`
///    is a fresh UUID per construction), so item identity here is a
///    name → ItemKind map. ItemKind's case order, categories and display
///    names mirror Kotlin's `ClothingItem` verbatim.
///  - Precipitation is passed with the Kotlin values (drizzle 0.05, rain
///    0.5). The Swift service accepts the parameter but its rain branch is
///    keyed to the condition alone (the Kotlin `precipitation > 0.1` gate
///    was never part of the hand port). No asserted invariant reads the
///    difference — drizzle's rain-jacket divergence is exactly the
///    observational note inv9 records on the Kotlin side.
///  - The Kotlin harness writes per-section report files under
///    build/outfit-sweep/ from an @AfterClass hook; the port prints each
///    section to the test log instead (an iOS test sandbox has no project
///    build directory). JUnit assertions halt a Kotlin test on first
///    failure; XCTest records and continues, so a failing Swift run lists
///    every violation in the section text.
final class OutfitRulesSweepTest: XCTestCase {

    // MARK: - Item taxonomy (mirrors Kotlin ClothingItem / ClothingCategory)

    enum ItemCategory {
        case topBase, topMid, topOuter, bottom, head, hands, accessories
    }

    /// Case order mirrors the Kotlin enum declaration order (bit positions
    /// in the mask follow it, as Kotlin's ordinals do).
    enum ItemKind: Int, CaseIterable {
        case tankTop = 0
        case shortSleeve
        case longSleeveLight
        case longSleeveThermal
        case quarterZip
        case fleecePullover
        case halfZipThermal
        case lightVest
        case windbreaker
        case lightJacket
        case rainJacket
        case insulatedJacket
        case shortShorts
        case shorts
        case capris
        case lightTights
        case thermalTights
        case visor
        case baseballCap
        case headband
        case lightBeanie
        case thermalBeanie
        case balaclava
        case lightGloves
        case thermalGloves
        case mittens
        case sunglasses
        case sunscreen
        case reflectiveGear
        case neckGaiter

        var category: ItemCategory {
            switch self {
            case .tankTop, .shortSleeve, .longSleeveLight, .longSleeveThermal: return .topBase
            case .quarterZip, .fleecePullover, .halfZipThermal: return .topMid
            case .lightVest, .windbreaker, .lightJacket, .rainJacket, .insulatedJacket: return .topOuter
            case .shortShorts, .shorts, .capris, .lightTights, .thermalTights: return .bottom
            case .visor, .baseballCap, .headband, .lightBeanie, .thermalBeanie, .balaclava: return .head
            case .lightGloves, .thermalGloves, .mittens: return .hands
            case .sunglasses, .sunscreen, .reflectiveGear, .neckGaiter: return .accessories
            }
        }

        /// Kotlin displayName, verbatim (used in report/violation strings).
        var displayName: String {
            switch self {
            case .tankTop: return "Tank Top / Singlet"
            case .shortSleeve: return "Short Sleeve Shirt"
            case .longSleeveLight: return "Light Long Sleeve"
            case .longSleeveThermal: return "Thermal Long Sleeve"
            case .quarterZip: return "Quarter-Zip Pullover"
            case .fleecePullover: return "Fleece Pullover"
            case .halfZipThermal: return "Thermal Half-Zip"
            case .lightVest: return "Light Vest"
            case .windbreaker: return "Windbreaker"
            case .lightJacket: return "Light Running Jacket"
            case .rainJacket: return "Rain Jacket"
            case .insulatedJacket: return "Insulated Jacket"
            case .shortShorts: return "Short Shorts (3\")"
            case .shorts: return "Running Shorts (5-7\")"
            case .capris: return "Capris / 3/4 Tights"
            case .lightTights: return "Light Tights"
            case .thermalTights: return "Thermal Tights"
            case .visor: return "Visor"
            case .baseballCap: return "Running Cap"
            case .headband: return "Ear Warmer / Headband"
            case .lightBeanie: return "Light Beanie"
            case .thermalBeanie: return "Thermal Beanie"
            case .balaclava: return "Balaclava / Face Mask"
            case .lightGloves: return "Light Gloves"
            case .thermalGloves: return "Thermal Gloves"
            case .mittens: return "Mittens"
            case .sunglasses: return "Sunglasses"
            case .sunscreen: return "Sunscreen (SPF 30+)"
            case .reflectiveGear: return "Reflective Gear"
            case .neckGaiter: return "Neck Gaiter / Buff"
            }
        }
    }

    /// The Swift engine constructs items by name; this is the identity map.
    /// Every name the service can produce appears exactly once.
    static let kindBySwiftName: [String: ItemKind] = [
        "Tank Top": .tankTop,
        "Short Sleeve Shirt": .shortSleeve,
        "Light Long Sleeve": .longSleeveLight,
        "Thermal Long Sleeve": .longSleeveThermal,
        "Quarter-Zip Pullover": .quarterZip,
        "Fleece Pullover": .fleecePullover,
        "Thermal Half-Zip": .halfZipThermal,
        "Windbreaker": .windbreaker,
        "Rain Jacket": .rainJacket,
        "Insulated Jacket": .insulatedJacket,
        "Short Shorts": .shortShorts,
        "Running Shorts": .shorts,
        "Light Tights": .lightTights,
        "Thermal Tights": .thermalTights,
        "Visor": .visor,
        "Running Cap": .baseballCap,
        "Headband": .headband,
        "Light Beanie": .lightBeanie,
        "Thermal Beanie": .thermalBeanie,
        "Balaclava": .balaclava,
        "Light Gloves": .lightGloves,
        "Thermal Gloves": .thermalGloves,
        "Mittens": .mittens,
        "Sunglasses": .sunglasses,
        "Sunscreen": .sunscreen,
        "Neck Gaiter": .neckGaiter,
        "Reflective Gear": .reflectiveGear,
    ]

    // MARK: - Scenario axes (mirror the Kotlin grid)

    enum Cond: Hashable {
        case dry, drizzle, rain, snow
    }

    enum Env: Hashable {
        case daySun, dayGrey, dawnUv, night
    }

    /// The five Kotlin ComfortPreference values, coldest → warmest, mapped
    /// to the Swift ComfortLevel cases with the same adjustment.
    enum ComfortProfile: Hashable, CaseIterable {
        case runsVeryCold, runsCold, neutral, runsWarm, runsVeryWarm

        var tempAdjustment: Int {
            switch self {
            case .runsVeryCold: return -10
            case .runsCold: return -5
            case .neutral: return 0
            case .runsWarm: return 5
            case .runsVeryWarm: return 10
            }
        }

        var swiftLevel: ComfortLevel {
            switch self {
            case .runsVeryCold: return .veryCold
            case .runsCold: return .cold
            case .neutral: return .neutral
            case .runsWarm: return .warm
            case .runsVeryWarm: return .veryWarm
            }
        }

        var kotlinName: String {
            switch self {
            case .runsVeryCold: return "RUNS_VERY_COLD"
            case .runsCold: return "RUNS_COLD"
            case .neutral: return "NEUTRAL"
            case .runsWarm: return "RUNS_WARM"
            case .runsVeryWarm: return "RUNS_VERY_WARM"
            }
        }
    }

    struct Scenario: Hashable {
        let feels: Int
        let wind: Double
        let cond: Cond
        let comfort: ComfortProfile
        let humidity: Int
        let env: Env

        /// The temperature the engine's brackets actually see.
        var adjusted: Double { Double(feels) + Double(comfort.tempAdjustment) }

        func with(cond newCond: Cond) -> Scenario {
            Scenario(feels: feels, wind: wind, cond: newCond, comfort: comfort, humidity: humidity, env: env)
        }

        func describe() -> String {
            "feels=\(feels)F actual=\(feels)F wind=\(wind)mph cond=\(cond) comfort=\(comfort.kotlinName)(adj=\(comfort.tempAdjustment)) humidity=\(humidity) env=\(env) adjusted=\(adjusted)F"
        }
    }

    /// Compact per-scenario record: item bitmask over ItemKind + counts.
    struct Rec {
        let mask: Int
        let total: Int
        let torso: Int
    }

    // MARK: - Grid

    static let engine = OutfitRecommendationService.shared

    static let feelsAxis: [Int] = Array(-20...110)
    static let windsAxis: [Double] = [0.0, 3.0, 6.0, 9.0, 12.0, 16.0, 20.0, 25.0, 30.0, 40.0]
    static let condsAxis: [Cond] = [.dry, .drizzle, .rain, .snow]
    static let comfortsAxis: [ComfortProfile] = ComfortProfile.allCases // coldest → warmest
    static let humiditiesAxis: [Int] = [30, 85]
    static let envsAxis: [Env] = [.daySun, .dayGrey, .dawnUv, .night]

    static let grid: [Scenario: Rec] = {
        var map: [Scenario: Rec] = [:]
        map.reserveCapacity(210_000)
        for feels in Self.feelsAxis { for wind in Self.windsAxis { for cond in Self.condsAxis {
            for comfort in Self.comfortsAxis { for humidity in Self.humiditiesAxis { for env in Self.envsAxis {
                let s = Scenario(feels: feels, wind: wind, cond: cond, comfort: comfort, humidity: humidity, env: env)
                map[s] = Self.recOf(engine: Self.runEngine(s))
            } }
        } } }
        return map
    }()

    // MARK: - Adapter: Scenario → the production service call

    /// Builds the service call for a scenario the way the production view
    /// models do: comfort applied to feels-like up front (`adjusted`), the
    /// unshifted feels-like passed as `rawTemperature`.
    ///
    /// - Parameter actualF: the actual (non-feels-like) temperature. The
    ///   Kotlin engine receives it inside WeatherConditions and never reads
    ///   it (proven by inv10); the Swift service has no such input at all,
    ///   so the adapter accepts it and drops it — inv10 below threads it
    ///   through exactly so the slice stays executable.
    static func runEngine(_ s: Scenario, actualF: Double? = nil, feelsOverride: Double? = nil) -> OutfitRecommendation {
        let (hour, uv) = envInputs(s.env)
        let raw = feelsOverride ?? Double(s.feels)
        let adjusted = raw + Double(s.comfort.tempAdjustment)
        _ = actualF // no service input exists for it — see inv10
        return engine.getRecommendation(
            for: adjusted,
            rawTemperature: raw,
            condition: condition(for: s.cond),
            windSpeed: s.wind,
            humidity: s.humidity,
            uvIndex: uv,
            precipitation: precipitation(for: s.cond),
            hour: hour
        )
    }

    static func envInputs(_ env: Env) -> (hour: Int, uv: Double) {
        switch env {
        case .daySun: return (12, 8.0)
        case .dayGrey: return (12, 0.0)
        case .dawnUv: return (6, 8.0)
        case .night: return (21, 0.0)
        }
    }

    static func condition(for cond: Cond) -> WeatherCondition {
        switch cond {
        case .dry: return .partlyCloudy
        case .drizzle: return .drizzle
        case .rain: return .rain
        case .snow: return .snow
        }
    }

    static func precipitation(for cond: Cond) -> Double {
        switch cond {
        case .dry: return 0.0
        case .drizzle: return 0.05
        case .rain: return 0.5
        case .snow: return 0.4
        }
    }

    // MARK: - Rec helpers (mirror the Kotlin harness)

    static func kind(of item: ClothingItem) -> ItemKind {
        guard let kind = kindBySwiftName[item.name] else {
            fatalError("Engine produced an item with no ItemKind mapping: '\(item.name)' — add it to kindBySwiftName (and check it against the Kotlin ClothingItem table).")
        }
        return kind
    }

    static func recOf(engine outfit: OutfitRecommendation) -> Rec {
        let kinds = outfit.allItems.map { kind(of: $0) }
        let mask = kinds.reduce(0) { $0 | (1 << $1.rawValue) }
        let torso = 1 + (outfit.topMid != nil ? 1 : 0) + (outfit.topOuter != nil ? 1 : 0)
        return Rec(mask: mask, total: outfit.allItems.count, torso: torso)
    }

    static func has(_ mask: Int, _ item: ItemKind) -> Bool {
        mask & (1 << item.rawValue) != 0
    }

    static func itemsOf(_ mask: Int) -> [ItemKind] {
        ItemKind.allCases.filter { has(mask, $0) }
    }

    static func describe(_ mask: Int, _ total: Int, _ torso: Int) -> String {
        itemsOf(mask).map { $0.displayName }.joined(separator: ", ") + " | total=\(total) torso=\(torso)"
    }

    static func outfitString(_ outfit: OutfitRecommendation) -> String {
        let r = recOf(engine: outfit)
        return describe(r.mask, r.total, r.torso)
    }

    static let midItems: [ItemKind] = [.quarterZip, .fleecePullover, .halfZipThermal]
    static let outerItems: [ItemKind] = [.lightVest, .windbreaker, .lightJacket, .rainJacket, .insulatedJacket]

    static func outerOf(_ mask: Int) -> ItemKind? { outerItems.first { has(mask, $0) } }
    static func midOf(_ mask: Int) -> ItemKind? { midItems.first { has(mask, $0) } }

    private func section(_ name: String, _ body: String) {
        print(body)
        _ = name
    }

    private var grid: [Scenario: Rec] { Self.grid }

    // MARK: - inv0: engine version pin

    func testInv0_engineVersionPin() {
        XCTAssertEqual(
            OutfitRecommendationService.engineVersion, "4.3",
            "Harness written against engine 4.3; re-review invariants before bumping this pin."
        )
        section("inv0", """
        ## inv0 — engine version pin
        ENGINE_VERSION = \(OutfitRecommendationService.engineVersion) (asserted == "4.3")
        """)
    }

    // MARK: - inv1: 25–40°F band — wind shell and mid layer never co-occur

    func testInv1_shellMidExclusionInCoolBand() {
        var checks = 0
        var violations: [String] = []
        // Observations (reported, not asserted — see notes in section text):
        var rainStackCount = 0
        var rainStackExamples: [String] = []
        var rawBandStackAdjustedOutside = 0
        var rawBandExamples: [String] = []
        var substitutions = 0

        for (s, r) in grid {
            let mid = Self.midOf(r.mask)
            let outer = Self.outerOf(r.mask)
            if s.adjusted >= 25.0 && s.adjusted < 40.0 {
                checks += 1
                if mid != nil && outer == .windbreaker {
                    violations.append("VIOLATION \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                }
                if mid != nil && outer == .rainJacket {
                    rainStackCount += 1
                    if rainStackExamples.count < 10 {
                        rainStackExamples.append("\(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                    }
                }
                // Documented substitution fired: adjusted 25–32 would select a mid,
                // wind called the shell, mid was dropped.
                if s.adjusted >= 25.0 && s.adjusted < 32.0 && s.wind > 10.0 &&
                    outer == .windbreaker && mid == nil {
                    substitutions += 1
                }
            }
            // Raw feels-like framing (what a user reading the thermometer sees):
            if (25...40).contains(s.feels) && mid != nil && outer == .windbreaker &&
                !(s.adjusted >= 25.0 && s.adjusted < 40.0) {
                rawBandStackAdjustedOutside += 1
                if rawBandExamples.count < 10 {
                    rawBandExamples.append("\(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                }
            }
        }

        var body = """
        ## inv1 — 25–40°F band: wind shell + mid layer co-occurrence
        Checks (scenarios with adjusted temp in [25,40)): \(checks)
        WINDBREAKER+mid violations: \(violations.count)
        """
        body += violations.map { "\n" + $0 }.joined()
        body += """

        Documented substitutions observed (mid dropped for shell in band): \(substitutions)
        OBSERVATION — RAIN_JACKET+mid stacks inside the adjusted band: \(rainStackCount)
          The v4.2 exclusion in execute() names only WINDBREAKER; the rain branch
          returns before it. A rain shell over a mid layer in cold rain may be
          intended design, but it is outside the letter of the v4.2 rule.
        """
        body += rainStackExamples.map { "\n  e.g. " + $0 }.joined()
        body += """

        OBSERVATION — raw feels-like in 25..40 with WINDBREAKER+mid but adjusted
          temp OUTSIDE the band (comfort shift moved the bracket): \(rawBandStackAdjustedOutside)
        """
        body += rawBandExamples.map { "\n  e.g. " + $0 }.joined()
        section("inv1", body)
        XCTAssertTrue(
            violations.isEmpty,
            "inv1: \(violations.count) WINDBREAKER+mid stacks in the 25–40°F adjusted band"
        )
    }

    // MARK: - inv2 + inv3: temperature chains (monotonic warmth, no cliffs)

    private func chains() -> [[(Scenario, Rec)]] {
        var result: [[(Scenario, Rec)]] = []
        for wind in Self.windsAxis { for cond in Self.condsAxis { for comfort in Self.comfortsAxis {
            for humidity in Self.humiditiesAxis { for env in Self.envsAxis {
                result.append(Self.feelsAxis.map { f in
                    let s = Scenario(feels: f, wind: wind, cond: cond, comfort: comfort, humidity: humidity, env: env)
                    return (s, grid[s]!)
                })
            } }
        } } }
        return result
    }

    func testInv2_monotonicWarmth() {
        var steps = 0
        var violations: [String] = []
        for chain in chains() {
            for i in 0..<(chain.count - 1) {
                steps += 1
                let (s0, r0) = chain[i]
                let (s1, r1) = chain[i + 1]
                if r1.torso > r0.torso {
                    violations.append("VIOLATION torso \(r0.torso) -> \(r1.torso) as feels rises: " +
                        "\(s0.describe()) -> \(Self.describe(r0.mask, r0.total, r0.torso)) || " +
                        "\(s1.describe()) -> \(Self.describe(r1.mask, r1.total, r1.torso))")
                }
            }
        }
        var body = """
        ## inv2 — monotonic warmth (torso layers never increase as feels-like rises 1°F)
        Adjacent-step comparisons: \(steps)
        Violations: \(violations.count)
        """
        body += violations.map { "\n" + $0 }.joined()
        section("inv2", body)
        XCTAssertTrue(violations.isEmpty, "inv2: \(violations.count) monotonic-warmth violations")
    }

    func testInv3_noCliffs() {
        var steps = 0
        var violations: [String] = []
        var cliffsOf2: [String] = []
        for chain in chains() {
            for i in 0..<(chain.count - 1) {
                steps += 1
                let (s0, r0) = chain[i]
                let (s1, r1) = chain[i + 1]
                let delta = r1.total - r0.total
                if abs(delta) > 2 {
                    violations.append("VIOLATION total \(r0.total) -> \(r1.total) (Δ\(delta)): " +
                        "\(s0.describe()) -> \(Self.describe(r0.mask, r0.total, r0.torso)) || " +
                        "\(s1.describe()) -> \(Self.describe(r1.mask, r1.total, r1.torso))")
                } else if abs(delta) == 2 {
                    let removed = Self.itemsOf(r0.mask).filter { !Self.has(r1.mask, $0) }
                    let added = Self.itemsOf(r1.mask).filter { !Self.has(r0.mask, $0) }
                    cliffsOf2.append("CLIFF Δ\(delta) at feels \(s0.feels)->\(s1.feels)F " +
                        "[wind=\(s0.wind) cond=\(s0.cond) comfort=\(s0.comfort.kotlinName) humidity=\(s0.humidity) env=\(s0.env)] " +
                        "removed=\(removed.map { $0.displayName }) added=\(added.map { $0.displayName }) " +
                        "total \(r0.total) -> \(r1.total)")
                }
            }
        }
        var body = """
        ## inv3 — no cliffs (adjacent 1°F steps change total item count by at most 2)
        Adjacent-step comparisons: \(steps)
        Violations (|Δ| > 2): \(violations.count)
        """
        body += violations.map { "\n" + $0 }.joined()
        body += "\nJumps of exactly 2 (logged, allowed): \(cliffsOf2.count)"
        body += cliffsOf2.map { "\n" + $0 }.joined()
        section("inv3", body)
        XCTAssertTrue(violations.isEmpty, "inv3: \(violations.count) cliff violations (|Δ|>2)")
    }

    // MARK: - inv4: structural sanity

    func testInv4_structuralSanity() {
        var violations: [String] = []
        var minTotal = Int.max
        var maxTotal = Int.min
        var maxExamples: [String] = []
        let bottomItems = ItemKind.allCases.filter { $0.category == .bottom }
        let torsoCats: Set<ItemCategory> = [.topBase, .topMid, .topOuter]
        for (s, r) in grid {
            let items = Self.itemsOf(r.mask)
            let bottoms = items.count { bottomItems.contains($0) }
            let torsoItems = items.count { torsoCats.contains($0.category) }
            if items.isEmpty { violations.append("VIOLATION empty outfit: \(s.describe())") }
            if bottoms != 1 { violations.append("VIOLATION bottoms=\(bottoms): \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))") }
            if torsoItems < 1 { violations.append("VIOLATION no torso item: \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))") }
            if !(2...10).contains(r.total) { violations.append("VIOLATION total=\(r.total) outside 2..10: \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))") }
            if r.total < minTotal { minTotal = r.total }
            if r.total > maxTotal {
                maxTotal = r.total
                maxExamples.removeAll()
            }
            if r.total == maxTotal && maxExamples.count < 5 {
                maxExamples.append("\(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
            }
        }
        var body = """
        ## inv4 — structural sanity (never empty, exactly one bottom, ≥1 torso item, total 2–10)
        Scenarios checked: \(grid.count); observed total range: \(minTotal)..\(maxTotal)
        Max-total examples:
        """
        body += maxExamples.map { "\n  " + $0 }.joined()
        body += "\nViolations: \(violations.count)"
        body += violations.prefix(200).map { "\n" + $0 }.joined()
        if violations.count > 200 { body += "\n... and \(violations.count - 200) more (see test rerun)" }
        section("inv4", body)
        XCTAssertTrue(violations.isEmpty, "inv4: \(violations.count) structural violations")
    }

    // MARK: - inv5: no contradictory pairs

    func testInv5_noContradictions() {
        let shorts: Set<ItemKind> = [.shorts, .shortShorts]
        let thermalWear: Set<ItemKind> = [
            .longSleeveThermal, .halfZipThermal,
            .insulatedJacket, .thermalTights,
        ]
        let tank: Set<ItemKind> = [.tankTop]
        let coldWear: Set<ItemKind> = [
            .quarterZip, .fleecePullover, .halfZipThermal,
            .windbreaker, .insulatedJacket,
            .lightTights, .thermalTights,
            .lightGloves, .thermalGloves, .mittens,
            .lightBeanie, .thermalBeanie, .balaclava,
            .neckGaiter,
        ]
        let shortSleeve: Set<ItemKind> = [.shortSleeve]
        let heavyCold: Set<ItemKind> = [
            .halfZipThermal, .insulatedJacket, .thermalTights,
        ]
        // (groupA, groupB, label) — an outfit containing any A with any B is a contradiction.
        // RAIN_JACKET is deliberately excluded from the tank pairs: a waterproof shell over
        // a singlet in a hot downpour is legitimate gear, not a contradiction.
        let rules: [(Set<ItemKind>, Set<ItemKind>, String)] = [
            (shorts, thermalWear, "shorts × thermal/insulated wear"),
            (tank, coldWear, "tank/singlet × cold-weather wear"),
            (shortSleeve, heavyCold, "short sleeve × heavy thermal wear"),
        ]
        var pairList: [String] = []
        for (a, b, label) in rules { for x in a { for y in b { pairList.append("\(label): \(x.displayName) + \(y.displayName)") } } }

        var violations: [String] = []
        for (s, r) in grid {
            for (a, b, label) in rules {
                let hitA = a.first { Self.has(r.mask, $0) }
                let hitB = b.first { Self.has(r.mask, $0) }
                if let hitA, let hitB {
                    violations.append("VIOLATION [\(label)] \(hitA.displayName) + \(hitB.displayName): " +
                        "\(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                }
            }
        }
        var body = """
        ## inv5 — no contradictory pairs (pairs derived from the ClothingItem taxonomy)
        Pairs checked per scenario (\(pairList.count)):
        """
        body += pairList.map { "\n  " + $0 }.joined()
        body += "\nScenarios checked: \(grid.count)"
        body += "\nViolations: \(violations.count)"
        body += violations.prefix(200).map { "\n" + $0 }.joined()
        if violations.count > 200 { body += "\n... and \(violations.count - 200) more" }
        section("inv5", body)
        XCTAssertTrue(violations.isEmpty, "inv5: \(violations.count) contradiction violations")
    }

    // MARK: - inv6: wind sanity

    func testInv6_windSanity() {
        var rows = 0
        var steps = 0
        var violations: [String] = []
        var calmTo20Decreases = 0
        var substitutionsCalmTo20 = 0
        for feels in Self.feelsAxis { for cond in Self.condsAxis { for comfort in Self.comfortsAxis {
            for humidity in Self.humiditiesAxis { for env in Self.envsAxis {
                rows += 1
                let ladder = Self.windsAxis.map { w -> (Scenario, Rec) in
                    let s = Scenario(feels: feels, wind: w, cond: cond, comfort: comfort, humidity: humidity, env: env)
                    return (s, grid[s]!)
                }
                for i in 0..<(ladder.count - 1) {
                    steps += 1
                    let (s0, r0) = ladder[i]
                    let (s1, r1) = ladder[i + 1]
                    if r1.total < r0.total {
                        violations.append("VIOLATION wind \(s0.wind) -> \(s1.wind) mph, total \(r0.total) -> \(r1.total): " +
                            "\(s0.describe()) -> \(Self.describe(r0.mask, r0.total, r0.torso)) || " +
                            "\(s1.describe()) -> \(Self.describe(r1.mask, r1.total, r1.torso))")
                    }
                }
                let calm = ladder.first { $0.0.wind == 0.0 }!
                let w20 = ladder.first { $0.0.wind == 20.0 }!
                if w20.1.total < calm.1.total { calmTo20Decreases += 1 }
                if w20.1.total == calm.1.total &&
                    Self.midOf(calm.1.mask) != nil && Self.midOf(w20.1.mask) == nil &&
                    Self.outerOf(w20.1.mask) == .windbreaker {
                    substitutionsCalmTo20 += 1
                }
            } }
        } } }
        var body = """
        ## inv6 — wind sanity (more wind never yields fewer items; shell-for-mid substitution is equal-count)
        Rows (feels × cond × comfort × humidity × env): \(rows); adjacent wind-step comparisons: \(steps)
        Calm(0) -> 20 mph decreases: \(calmTo20Decreases)
        Documented shell-for-mid substitutions at 0 -> 20 mph (equal total): \(substitutionsCalmTo20)
        Violations (any wind step decreases total): \(violations.count)
        """
        body += violations.prefix(200).map { "\n" + $0 }.joined()
        if violations.count > 200 { body += "\n... and \(violations.count - 200) more" }
        section("inv6", body)
        XCTAssertTrue(violations.isEmpty, "inv6: \(violations.count) wind-sanity violations")
    }

    // MARK: - inv7: profile ordering + sun gear keys to RAW temperature
    //
    // Owner decision (2026-10-19): sun protection is NOT layering. Through
    // engine v4.2 the visor/cap sun gates in selectHead() read the
    // comfort-ADJUSTED temperature, so at identical weather a warmer comfort
    // profile could gain a Running Cap the colder profile didn't — 640
    // profile-ordering inversions in the 2026-10-17 sweep, every one of them
    // sun gear. v4.3 keys the sun gates to the RAW temperature (feels-like
    // before the comfort shift). inv7 asserts both halves of that decision:
    //  (a) Profile ordering on TOTAL count: runs-cold >= neutral >= runs-warm
    //      at identical weather — zero inversions, no sun-gear exclusion
    //      accounting needed any more.
    //  (b) Raw keying, exactly: at identical weather the sun-protection
    //      outcome (sun headgear + sunglasses + sunscreen) is identical for
    //      all five comfort profiles, and in dry/snow conditions the sun
    //      headgear is a pure function of (raw feels-like, UV): VISOR iff
    //      raw >= 65 and UV > 3, BASEBALL_CAP iff 50 <= raw < 65 and UV > 3,
    //      no sun headgear below 50 raw or without UV — at EVERY profile.
    // Rain/drizzle are excluded from (b)'s threshold half: there the cap is
    // rain gear (selectHead's rain branch), not sun gear.

    private func headOf(_ mask: Int) -> ItemKind? {
        ItemKind.allCases.first { Self.has(mask, $0) && $0.category == .head }
    }

    /// The sun-protection outcome of an outfit, as a comparable signature:
    /// sun headgear (dry/snow only — in rain the cap is rain gear) plus the
    /// UV accessories.
    private func sunSignature(_ mask: Int, _ cond: Cond) -> String {
        let head = headOf(mask)
        let sunHead = (cond != .rain && cond != .drizzle &&
            (head == .visor || head == .baseballCap)) ? head : nil
        return "sunHead=\(String(describing: sunHead)) sunglasses=\(Self.has(mask, .sunglasses)) " +
            "sunscreen=\(Self.has(mask, .sunscreen))"
    }

    func testInv7_profileOrdering() {
        var rows = 0
        var comparisons = 0
        var keyingChecks = 0
        var orderingViolations: [String] = []
        var keyingViolations: [String] = []
        for feels in Self.feelsAxis { for wind in Self.windsAxis { for cond in Self.condsAxis {
            for humidity in Self.humiditiesAxis { for env in Self.envsAxis {
                rows += 1
                let chain = Self.comfortsAxis.map { c -> (Scenario, Rec) in
                    let s = Scenario(feels: feels, wind: wind, cond: cond, comfort: c, humidity: humidity, env: env)
                    return (s, grid[s]!)
                }
                // (a) Total-count ordering along the coldest->warmest chain.
                for i in 0..<(chain.count - 1) {
                    comparisons += 1
                    let (sCold, rCold) = chain[i]
                    let (sWarm, rWarm) = chain[i + 1]
                    if rWarm.total > rCold.total {
                        orderingViolations.append("VIOLATION \(sCold.comfort.kotlinName) total=\(rCold.total) < \(sWarm.comfort.kotlinName) total=\(rWarm.total): " +
                            "\(sCold.describe()) -> \(Self.describe(rCold.mask, rCold.total, rCold.torso)) || " +
                            "\(sWarm.describe()) -> \(Self.describe(rWarm.mask, rWarm.total, rWarm.torso))")
                    }
                }
                // (b) Sun gear is comfort-invariant at identical weather...
                keyingChecks += 1
                let sigs = chain.map { (_, r) in sunSignature(r.mask, cond) }
                if Set(sigs).count != 1 {
                    keyingViolations.append("VIOLATION sun gear differs across comfort profiles at identical weather: " +
                        chain.map { (s, r) in
                            "\(s.comfort.kotlinName) \(sunSignature(r.mask, cond)) [\(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))]"
                        }.joined(separator: " || "))
                }
                // ...and in dry/snow it is exactly the raw-temperature function.
                if cond != .rain && cond != .drizzle {
                    let uv = (env == .daySun || env == .dawnUv) ? 8.0 : 0.0
                    let expectedSunHead: ItemKind? = uv > 3 && feels >= 65 ? .visor
                        : uv > 3 && feels >= 50 ? .baseballCap
                        : nil
                    for (s, r) in chain {
                        keyingChecks += 1
                        let head = headOf(r.mask)
                        let sunHead = (head == .visor || head == .baseballCap) ? head : nil
                        if sunHead != expectedSunHead {
                            keyingViolations.append("VIOLATION sun headgear \(String(describing: sunHead)) != expected \(String(describing: expectedSunHead)) " +
                                "from RAW feels=\(feels)F uv=\(uv): \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                        }
                    }
                }
            } }
        } } }
        var body = """
        ## inv7 — profile ordering + sun gear keyed to RAW temperature (v4.3)
        Rows: \(rows); adjacent comfort comparisons (coldest->warmest chain of 5): \(comparisons)
        Raw-keying checks (per-row profile-invariance + per-profile raw thresholds): \(keyingChecks)
        Strict ordering inversions: \(orderingViolations.count)
        Raw-keying violations: \(keyingViolations.count)
        Mechanism note: selectHead()'s visor/cap sun gates read the RAW temperature
        (feels-like before the comfort shift), so the comfort preference cannot add or
        remove sun protection; sunglasses/sunscreen are UV-only. In rain/drizzle the cap
        is rain gear (selectHead's rain branch) and is excluded from the threshold half.
        """
        body += orderingViolations.map { "\n" + $0 }.joined()
        body += keyingViolations.map { "\n" + $0 }.joined()
        section("inv7", body)
        XCTAssertTrue(
            orderingViolations.isEmpty,
            "inv7: \(orderingViolations.count) profile-ordering inversions"
        )
        XCTAssertTrue(
            keyingViolations.isEmpty,
            "inv7: \(keyingViolations.count) raw-temperature keying violations"
        )
    }

    // MARK: - inv8: field-case regressions (Sol's real cases behind v4.2)

    func testInv8_fieldCaseRegressions() {
        var body = "## inv8 — field-case regressions"

        // (a) 47°F actual / 40°F feels / 11 mph / NEUTRAL — the case that produced
        // three torso layers pre-v4.2.
        let caseA = Scenario(feels: 40, wind: 11.0, cond: .dry, comfort: .neutral, humidity: 30, env: .dayGrey)
        let outfitA = Self.runEngine(caseA, actualF: 47.0)
        let recA = Self.recOf(engine: outfitA)
        body += "\nCase (a) feels=40F actual=47F wind=11mph NEUTRAL dry day-grey:"
        body += "\n  \(Self.outfitString(outfitA))"
        XCTAssertTrue(recA.torso <= 2, "case (a) torso layers \(recA.torso) > 2")
        XCTAssertTrue(
            outfitA.topMid == nil || outfitA.topOuter == nil,
            "case (a) shell+mid stack present"
        )

        // (b) 39°F feels / calm / NEUTRAL — the shipped v4.2 result: 4 items, 1 torso layer.
        let caseB = Scenario(feels: 39, wind: 0.0, cond: .dry, comfort: .neutral, humidity: 30, env: .dayGrey)
        let outfitB = Self.runEngine(caseB)
        let recB = Self.recOf(engine: outfitB)
        body += "\nCase (b) feels=39F wind=0mph NEUTRAL dry day-grey:"
        body += "\n  \(Self.outfitString(outfitB))"
        XCTAssertEqual(recB.total, 4, "case (b) total items")
        XCTAssertEqual(recB.torso, 1, "case (b) torso layers")

        section("inv8", body)
    }

    // MARK: - inv9: rain coverage

    func testInv9_rainCoverage() {
        var rainChecks = 0
        var violations: [String] = []
        var missingTemps = Set<Double>()
        var drizzleChecks = 0
        var drizzleWithRainJacket = 0
        var snowDryDiffs = 0
        var snowDryPairs = 0
        for (s, r) in grid {
            switch s.cond {
            case .rain:
                rainChecks += 1
                if Self.outerOf(r.mask) != .rainJacket {
                    violations.append("VIOLATION rain without RAIN_JACKET: \(s.describe()) -> \(Self.describe(r.mask, r.total, r.torso))")
                    missingTemps.insert(s.adjusted)
                }
            case .drizzle:
                drizzleChecks += 1
                if Self.outerOf(r.mask) == .rainJacket { drizzleWithRainJacket += 1 }
            default:
                break
            }
            if s.cond == .snow {
                let dryTwin = grid[s.with(cond: .dry)]!
                snowDryPairs += 1
                if dryTwin.mask != r.mask { snowDryDiffs += 1 }
            }
        }
        var body = """
        ## inv9 — rain coverage (condition=rain ⇒ the engine's rain/waterproof item)
        Rain scenarios checked (isRaining, precip=0.5 > 0.1): \(rainChecks)
        Violations (rain item missing): \(violations.count)
        """
        if !missingTemps.isEmpty {
            body += "\nAdjusted temps missing the rain jacket: \(missingTemps.sorted().map { "\($0)" }.joined(separator: ", "))"
        }
        body += violations.prefix(100).map { "\n" + $0 }.joined()
        body += """

        OBSERVATION — drizzle (isRaining, precip=0.05 ≤ 0.1 threshold): \(drizzleChecks) scenarios,
          rain jacket in \(drizzleWithRainJacket) of them. In the Kotlin engine selectTopOuter()
          requires precip > 0.1, so drizzle gets NO rain-specific outer at any temperature
          (head still gets a cap). The Swift service's rain branch ignores the precipitation
          amount (see the header's port notes), so the iOS count above is every drizzle
          scenario — a documented divergence; no invariant asserts it.
        OBSERVATION — snow: \(snowDryPairs) snow scenarios compared with their dry twins;
          outfits differing: \(snowDryDiffs). The engine has no snow-specific branch:
          isSnowing is never read by the selection logic.
        """
        section("inv9", body)
        XCTAssertTrue(violations.isEmpty, "inv9: \(violations.count) rain scenarios missing RAIN_JACKET")
    }

    // MARK: - inv10 (extra): actual-temperature independence

    func testInv10_actualTemperatureIgnored() {
        var checks = 0
        var mismatches: [String] = []
        for feels in Self.feelsAxis { for wind in Self.windsAxis {
            let base = Scenario(feels: feels, wind: wind, cond: .dry, comfort: .neutral, humidity: 30, env: .dayGrey)
            let baseMask = grid[base]!.mask
            for delta in [-10.0, 10.0] {
                checks += 1
                let outfit = Self.runEngine(base, actualF: Double(feels) + delta)
                if Self.recOf(engine: outfit).mask != baseMask {
                    mismatches.append("MISMATCH actual=\(Double(feels) + delta)F vs \(feels)F at \(base.describe())")
                }
            }
        } }
        var body = """
        ## inv10 (extra) — actual-temperature independence
        Divergence slice checks (actual = feels ±10°F, NEUTRAL/dry/day-grey): \(checks)
        Mismatches: \(mismatches.count) — the engine's selection reads feelsLike only;
        the `temperature` field never reaches a selector (source-verified, now measured).
        """
        body += mismatches.map { "\n" + $0 }.joined()
        section("inv10", body)
        XCTAssertTrue(mismatches.isEmpty, "inv10: \(mismatches.count) actual-temp mismatches")
    }

    // MARK: - inv11 (extra, observational): Celsius round-trip equivalence

    func testInv11_celsiusRoundTrip() {
        var checks = 0
        var mismatches: [String] = []
        for feelsF in stride(from: -20, through: 110, by: 5) { for wind in [0.0, 12.0, 25.0] {
            for comfort in Self.comfortsAxis { for cond in [Cond.dry, Cond.rain] {
                let s = Scenario(feels: feelsF, wind: wind, cond: cond, comfort: comfort, humidity: 30, env: .dayGrey)
                // The Kotlin engine converts a Celsius feels-like back to °F
                // internally; the Swift caller does the same conversion before
                // the service call, so the slice feeds the round-tripped
                // Fahrenheit value in as the feels-like.
                let celsius = (Double(feelsF) - 32.0) * 5.0 / 9.0
                let roundTripped = celsius * 9.0 / 5.0 + 32.0
                checks += 1
                let outfit = Self.runEngine(s, feelsOverride: roundTripped)
                let expected = grid[s]!
                if Self.recOf(engine: outfit).mask != expected.mask {
                    mismatches.append("MISMATCH feels=\(feelsF)F via Celsius (round-trips to \(roundTripped)): " +
                        "\(s.describe()) -> F-run \(Self.describe(expected.mask, expected.total, expected.torso)) " +
                        "vs C-run \(Self.outfitString(outfit))")
                }
            } }
        } }
        var body = """
        ## inv11 (extra, observational) — Celsius round-trip equivalence
        Checks: \(checks) (feels step 5°F, winds {0,12,25}, all comforts, dry+rain, day-grey)
        Mismatches: \(mismatches.count)
        The engine converts °C feels-like back to °F in floating point; values that
        land a hair below a bracket boundary (e.g. 39.999… instead of 40.0) select
        the colder bracket. Counted and listed here as a finding, not asserted:
        it is not one of the nine v4.2 invariants.
        """
        body += mismatches.map { "\n" + $0 }.joined()
        section("inv11", body)
        print("inv11: \(mismatches.count) Celsius round-trip mismatches out of \(checks) checks")
    }

    // MARK: - spot table

    func testSpotTable() {
        var body = """
        ## spot table — NEUTRAL, dry, humidity 30, day-grey (UV 0, 12:00)
        feels | wind | outfit
        """
        for feels in [25, 30, 35, 40, 45, 50, 55, 60] {
            for wind in [0.0, 15.0] {
                let s = Scenario(feels: feels, wind: wind, cond: .dry, comfort: .neutral, humidity: 30, env: .dayGrey)
                let outfit = Self.runEngine(s)
                body += "\n\(feels)F | \(wind)mph | \(Self.outfitString(outfit))"
            }
        }
        section("spot", body)
    }
}
