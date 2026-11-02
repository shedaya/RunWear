import XCTest

/// RW-20 point-grain serving filter (2026-11-02), iOS side.
///
/// The pool is stocked and adjudicated at temperature-BRACKET grain,
/// but the engine prescribes at POINT grain — inside COOL, shorts only
/// at 45°F+, tights below; a shell only when wind or rain calls for
/// one. Sol's 2026-11-02 field failure (39°F, Light Tights prescribed,
/// the rotation dealt three shorts photos, a jacket photo, and a
/// two-runner photo) is the anchor scenario. The filter itself lives
/// in the shared Kotlin engine (HeroPointGrainPolicy) — the same code
/// Android serves through — and these tests pin the Swift seam: the
/// wire decode of the backfilled adjudication columns, and the
/// key-based bridge verdicts against REAL engine prescriptions built
/// by OutfitRecommendationService.
final class HeroPointGrainTests: XCTestCase {

    private func image(
        personCount: Int? = 1,
        topBase: String? = "long",
        outer: Bool? = false,
        bottom: String? = "tights"
    ) -> GeneratedImage {
        GeneratedImage(
            id: "row",
            combinationId: "MALE_CLEAR_COOL_DAWN_vX",
            imageUrl: "https://example.test/hero.webp",
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved",
            personCount: personCount,
            garmentClasses: GarmentClasses(
                topBase: topBase, outer: outer, mid: false,
                bottom: bottom, head: nil, hands: nil,
                sunglasses: false, gaiter: false
            ),
            garmentSource: "adjudicated"
        )
    }

    // MARK: - Wire decode

    func testPersonCountAndGarmentClassesDecodeFromTheWire() throws {
        let json = """
        {"id":"72d6f96c","combination_id":"MALE_CLEAR_COOL_DAWN_v10",
         "image_url":"https://example.test/v10.webp","review_status":"approved",
         "person_count":1,
         "garment_classes":{"topBase":"long","outer":false,"mid":false,
           "bottom":"shorts","head":null,"hands":null,
           "sunglasses":false,"gaiter":false},
         "garment_source":"adjudicated"}
        """
        let decoded = try JSONDecoder().decode(GeneratedImage.self, from: Data(json.utf8))
        XCTAssertEqual(decoded.personCount, 1)
        XCTAssertEqual(decoded.garmentClasses?.bottom, "shorts")
        XCTAssertEqual(decoded.garmentSource, "adjudicated")

        // A row from before the backfill decodes nils.
        let legacy = """
        {"id":"a2","combination_id":"MALE_CLEAR_COOL_DUSK_v2",
         "image_url":"https://example.test/dusk_v2.webp"}
        """
        let legacyDecoded = try JSONDecoder().decode(GeneratedImage.self, from: Data(legacy.utf8))
        XCTAssertNil(legacyDecoded.personCount)
        XCTAssertNil(legacyDecoded.garmentClasses)
    }

    // MARK: - The field-failure scenario (39F COOL, calm)

    func testAt39FShortsJacketAndMultiPersonNeverMatch() {
        // Sol's exact conditions: feels-like 39F, actual 42F, wind 2 mph.
        let outfit = OutfitRecommendationService.shared.getRecommendation(
            for: 39, rawTemperature: 42, isRaining: false,
            windSpeed: 2, humidity: 77, uvIndex: 0, hour: 6
        )
        XCTAssertEqual(outfit.bottom.key, "LIGHT_TIGHTS")
        XCTAssertNil(outfit.topOuter)

        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(bottom: "shorts"), outfit: outfit))
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(bottom: "short_shorts"), outfit: outfit))
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(outer: true), outfit: outfit))
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(personCount: 2), outfit: outfit))
        XCTAssertTrue(HeroPointGrainPolicy.matches(image: image(), outfit: outfit))
    }

    // MARK: - The warm side of the breakpoint (47F COOL)

    func testAt47FShortsMayMatchAndTightsMustNot() {
        let outfit = OutfitRecommendationService.shared.getRecommendation(
            for: 47, rawTemperature: 47, isRaining: false,
            windSpeed: 2, humidity: 50, uvIndex: 0, hour: 6
        )
        XCTAssertEqual(outfit.bottom.key, "SHORTS")

        XCTAssertTrue(HeroPointGrainPolicy.matches(image: image(bottom: "shorts"), outfit: outfit))
        XCTAssertTrue(HeroPointGrainPolicy.matches(image: image(bottom: "short_shorts"), outfit: outfit))
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(bottom: "tights"), outfit: outfit))
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(bottom: "shorts", personCount: 2), outfit: outfit))
    }
}
