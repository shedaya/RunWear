import XCTest

/// WS-7 (2026-11-03): the bundled fallback matrix is now the staged
/// RW-26 gender-paired matrix — 22 cells × MALE/FEMALE = 44 assets —
/// replacing the RW-26-era 21-asset matrix whose gender-aware walk
/// ranked subject gender above weather truth (D-19: for MALE + COOL
/// + CLEAR it deterministically served the SNOW photo, the only
/// male subject in the old COOL bracket). With every cell paired,
/// the resolver bound is: a gendered request starts and normally
/// ends at the requested cell's own gendered asset; gender never
/// outranks the scene at a paired cell. FREEZING × RAIN serves its
/// own paired assets (the designed degradation is retired for
/// gendered serves); ungendered requests keep the legacy chain
/// byte-for-byte over the legacy view. Mirror of Android's
/// HeroFallbackGenderTest. Assertions run on resolved asset NAMES
/// (FallbackImageProvider.resolvedAssetName): this target is a
/// logic-test host where Bundle.main is the test runner, so
/// bundle-URL resolution returns nil there by design.
final class HeroFallbackGenderTests: XCTestCase {

    private let provider = FallbackImageProvider.shared

    private func assetName(temp: HeroTempBracket, weather: HeroWeatherCondition, gender: HeroGender? = nil) -> String {
        provider.resolvedAssetName(temp: temp, weather: weather, gender: gender)
    }

    private func expectedName(_ temp: HeroTempBracket, _ weather: HeroWeatherCondition, _ gender: HeroGender) -> String {
        "\(temp.rawValue.lowercased())_\(weather.rawValue.lowercased())_\(gender.rawValue.lowercased())"
    }

    /// The 22 paired cells: every bracket × weather except the two
    /// non-cells (WARM × SNOW, HOT × SNOW).
    private func isPairedCell(_ temp: HeroTempBracket, _ weather: HeroWeatherCondition) -> Bool {
        FallbackImageProvider.pairedAssets[temp]?[weather] != nil
    }

    // MARK: - The paired matrix

    func testPairedTableCoverage_22CellsTimes2Genders() {
        let paired = FallbackImageProvider.pairedAssets
        XCTAssertEqual(22, paired.values.reduce(0) { $0 + $1.count })
        XCTAssertEqual(44, paired.values.reduce(0) { $0 + $1.values.reduce(0) { $0 + $1.count } })
        for (bracket, conditions) in paired {
            for (condition, genders) in conditions {
                XCTAssertEqual(
                    Set([HeroGender.FEMALE, HeroGender.MALE]), Set(genders.keys),
                    "\(bracket)/\(condition) must carry both genders"
                )
                for (gender, name) in genders {
                    XCTAssertEqual(expectedName(bracket, condition, gender), name)
                }
            }
        }
        XCTAssertNotNil(FallbackImageProvider.pairedAssets[.FREEZING]?[.RAIN])
        XCTAssertNil(FallbackImageProvider.pairedAssets[.HOT]?[.SNOW])
        XCTAssertNil(FallbackImageProvider.pairedAssets[.WARM]?[.SNOW])
    }

    func testEveryPairedCellResolvesToItsOwnGenderPairedAssetForBothGenders() {
        for bracket in HeroTempBracket.allCases {
            for condition in HeroWeatherCondition.allCases where isPairedCell(bracket, condition) {
                for gender in [HeroGender.MALE, .FEMALE] {
                    XCTAssertEqual(
                        expectedName(bracket, condition, gender),
                        assetName(temp: bracket, weather: condition, gender: gender),
                        "paired cell \(bracket)/\(condition)/\(gender) must serve its own asset"
                    )
                    XCTAssertEqual(
                        expectedName(bracket, condition, gender),
                        FallbackImageProvider.pairedAssetName(temp: bracket, weather: condition, gender: gender)
                    )
                }
            }
        }
    }

    // MARK: - The D-19 bound

    func testD19ConstructionCase_maleCoolClearResolvesToPairedClearNeverSnow() {
        // The RW-27 walkthrough serve: MALE + COOL + CLEAR on a
        // clear 45°F morning served cool_snow by construction. It
        // now resolves to the cell's own male asset.
        let name = assetName(temp: .COOL, weather: .CLEAR, gender: .MALE)
        XCTAssertEqual("cool_clear_male", name)
        XCTAssertNotEqual("cool_snow_male", name)
        XCTAssertNotEqual("cool_snow", name)
        XCTAssertEqual("cool_clear_female", assetName(temp: .COOL, weather: .CLEAR, gender: .FEMALE))
    }

    func testGenderNeverOutranksScene_noGenderedResolutionCrossesWeatherAtAPairedCell() {
        for bracket in HeroTempBracket.allCases {
            for condition in HeroWeatherCondition.allCases where isPairedCell(bracket, condition) {
                for gender in [HeroGender.MALE, .FEMALE] {
                    let name = assetName(temp: bracket, weather: condition, gender: gender)
                    XCTAssertTrue(
                        name.hasPrefix("\(bracket.rawValue.lowercased())_\(condition.rawValue.lowercased())_"),
                        "\(bracket)/\(condition)/\(gender) crossed the scene: \(name)"
                    )
                    XCTAssertTrue(name.hasSuffix("_\(gender.rawValue.lowercased())"))
                }
            }
        }
    }

    func testFreezingRainServesItsOwnPairedAssetsForBothGenders_degradationRetired() {
        XCTAssertEqual("freezing_rain_female", assetName(temp: .FREEZING, weather: .RAIN, gender: .FEMALE))
        XCTAssertEqual("freezing_rain_male", assetName(temp: .FREEZING, weather: .RAIN, gender: .MALE))
    }

    func testNonCellsResolveSceneFirstInsideTheBracketWithTheRequestedGender() {
        // HOT × SNOW / WARM × SNOW hold no paired asset: the walk
        // stays inside the bracket and takes the requested gender's
        // asset at the nearest scene (the bracket's CLEAR cell).
        XCTAssertEqual("hot_clear_male", assetName(temp: .HOT, weather: .SNOW, gender: .MALE))
        XCTAssertEqual("hot_clear_female", assetName(temp: .HOT, weather: .SNOW, gender: .FEMALE))
        XCTAssertEqual("warm_clear_male", assetName(temp: .WARM, weather: .SNOW, gender: .MALE))
        XCTAssertEqual("warm_clear_female", assetName(temp: .WARM, weather: .SNOW, gender: .FEMALE))
    }

    // MARK: - The ungendered legacy chain (unchanged)

    /// The RW-20 provenance census: subject gender per legacy cell.
    private let expectedSubjects: [HeroTempBracket: [HeroWeatherCondition: HeroGender]] = [
        .FREEZING: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .SNOW: .MALE],
        .COLD: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .FEMALE],
        .COOL: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .MALE],
        .MILD: [.CLEAR: .FEMALE, .CLOUDY: .FEMALE, .RAIN: .FEMALE, .SNOW: .FEMALE],
        .WARM: [.CLEAR: .MALE, .CLOUDY: .MALE, .RAIN: .FEMALE],
        .HOT: [.CLEAR: .FEMALE, .CLOUDY: .MALE, .RAIN: .FEMALE]
    ]

    func testLegacyCensusCoversExactlyThe21LegacyCells() {
        XCTAssertEqual(expectedSubjects, FallbackImageProvider.legacySubjects)
        XCTAssertEqual(21, FallbackImageProvider.legacyAssetNames.values.reduce(0) { $0 + $1.count })
        XCTAssertNil(FallbackImageProvider.legacyAssetNames[.FREEZING]?[.RAIN])
        // Each legacy cell maps to the census subject's paired file.
        for (bracket, conditions) in expectedSubjects {
            for (condition, subject) in conditions {
                XCTAssertEqual(
                    expectedName(bracket, condition, subject),
                    FallbackImageProvider.legacyAssetNames[bracket]?[condition],
                    "legacy view for \(bracket)/\(condition)"
                )
            }
        }
    }

    func testUngenderedResolutionIsTheLegacyChain() {
        XCTAssertEqual("mild_clear_female", assetName(temp: .MILD, weather: .CLEAR))
        XCTAssertEqual("cool_clear_female", assetName(temp: .COOL, weather: .CLEAR))
        XCTAssertEqual("warm_clear_male", assetName(temp: .WARM, weather: .CLEAR))
        XCTAssertEqual("freezing_snow_male", assetName(temp: .FREEZING, weather: .SNOW))
        // Missing legacy cells fall back to the bracket's CLEAR cell.
        XCTAssertEqual("hot_clear_female", assetName(temp: .HOT, weather: .SNOW))
        // FREEZING × RAIN was not a legacy cell: the legacy chain
        // still degrades it to freezing_clear (the FEMALE paired
        // file, the census subject of FREEZING × CLEAR).
        XCTAssertEqual("freezing_clear_female", assetName(temp: .FREEZING, weather: .RAIN))
    }
}
