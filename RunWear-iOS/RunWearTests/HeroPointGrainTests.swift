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

    // MARK: - RW-23 Juneau field failure (hands waiver)

    func testJuneauBareHandedRowMustNotMatchWhenGlovesPrescribed() {
        // RW-23 (2026-11-02): Juneau — raw feels-like 32°F, rain,
        // wind 11 mph, comfort +5 → adjusted 37°F. The prescription
        // includes a rain cap and light gloves. The four photos
        // served in the field were all bare-handed (hands=nil) and
        // passed because the RW-20 policy waived head and hands.
        // Sol ruled: no waiver — serving enforces the FULL
        // prescription including head and hands.
        let outfit = OutfitRecommendationService.shared.getRecommendation(
            for: 37, rawTemperature: 32, isRaining: true,
            windSpeed: 11, humidity: 80, uvIndex: 0, precipitation: 0.5, hour: 7
        )
        XCTAssertEqual(outfit.head?.key, "BASEBALL_CAP")
        XCTAssertEqual(outfit.hands?.key, "LIGHT_GLOVES")
        XCTAssertEqual(outfit.bottom.key, "LIGHT_TIGHTS")
        XCTAssertEqual(outfit.topOuter?.key, "RAIN_JACKET")

        // The Juneau served row: correct top/outer/bottom/head,
        // but bare-handed (hands=nil).
        let bareHandedRow = GeneratedImage(
            id: "juneau-bare-handed",
            combinationId: "MALE_RAIN_COOL_DAWN_vX",
            imageUrl: "https://example.test/juneau.webp",
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved",
            personCount: 1,
            garmentClasses: GarmentClasses(
                topBase: "long", outer: true, mid: false,
                bottom: "tights", head: "cap", hands: nil,
                sunglasses: false, gaiter: false
            ),
            garmentSource: "adjudicated"
        )
        XCTAssertFalse(
            HeroPointGrainPolicy.matches(image: bareHandedRow, outfit: outfit),
            "Bare-handed row must not match when LIGHT_GLOVES are prescribed (RW-23: hands are not waived)"
        )

        // The correct row — same garments, with gloves — matches.
        let glovedRow = GeneratedImage(
            id: "juneau-gloved",
            combinationId: "MALE_RAIN_COOL_DAWN_vX",
            imageUrl: "https://example.test/juneau-gloved.webp",
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved",
            personCount: 1,
            garmentClasses: GarmentClasses(
                topBase: "long", outer: true, mid: false,
                bottom: "tights", head: "cap", hands: "gloves",
                sunglasses: false, gaiter: false
            ),
            garmentSource: "adjudicated"
        )
        XCTAssertTrue(HeroPointGrainPolicy.matches(image: glovedRow, outfit: outfit))
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
        XCTAssertFalse(HeroPointGrainPolicy.matches(image: image(personCount: 2, bottom: "shorts"), outfit: outfit))
    }
}
