import XCTest

/// RW-26 (2026-11-02): the bundled fallback matrix is keyed bracket ×
/// weather only, but its assets are photographs of specific people —
/// 15 of 21 subjects are women, and the COLD and MILD brackets hold no
/// male-subject asset at all. Before this fix, the cascade's level-6
/// bundled fallback ignored the combination's gender: a
/// male-preference user was served a woman's photo by every
/// COLD/MILD/COOL fallback cell (field instances 2026-11-02 on
/// Android: cool_clear and mild_clear; iOS shares the matrix and the
/// mechanism). Diagnosis:
/// release-prep/rw-gender-preference-diagnosis-2026-11-02.md.
/// Mirror of Android's HeroFallbackGenderTest. Assertions run on
/// resolved asset NAMES (FallbackImageProvider.resolvedAssetName):
/// this target is a logic-test host where Bundle.main is the test
/// runner, so bundle-URL resolution returns nil there by design —
/// the first CI round failed all six tests on empty URLs, including
/// the ungendered legacy-chain case, which is how that artifact was
/// identified.
final class HeroFallbackGenderTests: XCTestCase {

    private let provider = FallbackImageProvider.shared

    private func assetName(temp: HeroTempBracket, weather: HeroWeatherCondition, gender: HeroGender? = nil) -> String {
        provider.resolvedAssetName(temp: temp, weather: weather, gender: gender)
    }

    /// The RW-20 provenance census: subject gender per bundled asset.
    private let expectedSubjects: [String: HeroGender] = [
        "freezing_clear": .FEMALE, "freezing_cloudy": .FEMALE, "freezing_snow": .MALE,
        "cold_clear": .FEMALE, "cold_cloudy": .FEMALE, "cold_rain": .FEMALE, "cold_snow": .FEMALE,
        "cool_clear": .FEMALE, "cool_cloudy": .FEMALE, "cool_rain": .FEMALE, "cool_snow": .MALE,
        "mild_clear": .FEMALE, "mild_cloudy": .FEMALE, "mild_rain": .FEMALE, "mild_snow": .FEMALE,
        "warm_clear": .MALE, "warm_cloudy": .MALE, "warm_rain": .FEMALE,
        "hot_clear": .FEMALE, "hot_cloudy": .MALE, "hot_rain": .FEMALE
    ]

    func testFieldInstance2_maleMildClearResolvesToWarmClearNotMildClear() {
        // Sol's flagged serve: requested MALE_CLEAR_MILD_NIGHT, served
        // the MILD×CLEAR fallback (a woman). MILD holds no male-subject
        // asset; the nearest matching one is WARM×CLEAR.
        XCTAssertEqual("warm_clear", assetName(temp: .MILD, weather: .CLEAR, gender: .MALE))
    }

    func testFieldInstance1_maleCoolClearResolvesToCoolSnowNotCoolClear() {
        // The screenshot: Fit Preference Male, hero = cool_clear (a
        // woman in a pink long-sleeve). COOL's only male-subject asset
        // is its SNOW cell.
        XCTAssertEqual("cool_snow", assetName(temp: .COOL, weather: .CLEAR, gender: .MALE))
    }

    func testFemaleWarmClearResolvesToWarmRainNotWarmClear() {
        XCTAssertEqual("warm_rain", assetName(temp: .WARM, weather: .CLEAR, gender: .FEMALE))
    }

    func testMatchingExactCellUnchanged() {
        XCTAssertEqual("mild_clear", assetName(temp: .MILD, weather: .CLEAR, gender: .FEMALE))
        XCTAssertEqual("warm_clear", assetName(temp: .WARM, weather: .CLEAR, gender: .MALE))
    }

    func testEveryGenderedResolutionReturnsAMatchingSubject() {
        for bracket in HeroTempBracket.allCases {
            for condition in HeroWeatherCondition.allCases {
                for gender in [HeroGender.MALE, .FEMALE] {
                    let name = assetName(temp: bracket, weather: condition, gender: gender)
                    XCTAssertEqual(
                        gender,
                        expectedSubjects[name],
                        "wrong-gender subject for \(bracket)/\(condition)/\(gender): \(name)"
                    )
                }
            }
        }
    }

    func testUngenderedResolutionIsTheLegacyChain() {
        XCTAssertEqual("mild_clear", assetName(temp: .MILD, weather: .CLEAR))
        XCTAssertEqual("hot_clear", assetName(temp: .HOT, weather: .SNOW))
        XCTAssertEqual("cool_clear", assetName(temp: .COOL, weather: .CLEAR))
    }
}
