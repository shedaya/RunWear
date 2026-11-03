import XCTest

/// WS-7 / D-22 — point-grain standing for bundled serves.
///
/// Pool serves obey the point-grain prescription gate; before WS-7 a
/// bundled fallback serve was exempt by construction (the photo
/// could wear gloves the app did not prescribe, with no record of
/// why that photo was entitled to serve the cell). The bundle's
/// answer is the precomputed verdict: every bundled asset carries
/// its manifest provenance — the pool source combination it was
/// rendered from, that row's id, and its rung (T1 strict rung 1 /
/// T3 recalibrated rung 1, the verdict basis of Sol's Nov 2 gate
/// rulings 2 + 5) — recorded in the bundle wiring, so a bundled
/// serve is auditable exactly like a pool serve. Mirror of
/// Android's HeroFallbackProvenanceTest. The manifest of record:
/// release-prep/rw26-fallback-bundle-2026-11-03/MANIFEST.md.
final class HeroFallbackProvenanceTests: XCTestCase {

    private let provider = FallbackImageProvider.shared

    func testEveryPairedAssetCarriesProvenance() {
        let table = FallbackImageProvider.provenanceByAsset
        XCTAssertEqual(44, table.count)
        var assetNames = Set<String>()
        for (_, conditions) in FallbackImageProvider.pairedAssets {
            for (_, genders) in conditions {
                for (_, name) in genders {
                    assetNames.insert(name)
                    let prov = table[name]
                    XCTAssertNotNil(prov, "no provenance for \(name)")
                    guard let prov else { continue }
                    XCTAssertEqual(prov, FallbackImageProvider.provenance(forAssetName: name))
                    XCTAssertTrue(
                        prov.rung == "T1" || prov.rung == "T3",
                        "\(name) rung must be a manifest rung, got \(prov.rung)"
                    )
                    XCTAssertTrue(
                        prov.sourceRowId.range(
                            of: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                            options: .regularExpression
                        ) != nil,
                        "\(name) source row id must be a pool row id, got \(prov.sourceRowId)"
                    )
                }
            }
        }
        XCTAssertEqual(assetNames, Set(table.keys))
    }

    func testProvenanceCombinationGenderAndCellMatchTheAssetSlot() {
        for (file, prov) in FallbackImageProvider.provenanceByAsset {
            // File: {bracket}_{weather}_{gender}; combination:
            // {GENDER}_{WEATHER}_{BRACKET}_… (RAINY/SNOWY are the
            // legacy weather spellings some combinations carry).
            let fileParts = file.split(separator: "_").map(String.init)
            XCTAssertEqual(3, fileParts.count, file)
            let comboParts = prov.combinationId.split(separator: "_").map(String.init)
            XCTAssertTrue(comboParts.count >= 3, prov.combinationId)
            XCTAssertEqual(
                fileParts[2].uppercased(), comboParts[0],
                "\(file) provenance combination \(prov.combinationId) gender mismatch"
            )
            XCTAssertEqual(
                fileParts[0].uppercased(), comboParts[2],
                "\(file) provenance combination \(prov.combinationId) bracket mismatch"
            )
            let weatherToken = fileParts[1].uppercased()
            let legacyToken = weatherToken == "RAIN" ? "RAINY" : (weatherToken == "SNOW" ? "SNOWY" : weatherToken)
            XCTAssertTrue(
                comboParts[1] == weatherToken || comboParts[1] == legacyToken,
                "\(file) provenance combination \(prov.combinationId) weather mismatch"
            )
        }
    }

    func testManifestPins_theD19CellAndTheRestoredFreezingRainCell() {
        let table = FallbackImageProvider.provenanceByAsset
        // MALE + COOL + CLEAR: the asset D-19's construction case now serves.
        XCTAssertEqual("MALE_CLEAR_COOL_MIDDAY_84c10525", table["cool_clear_male"]?.combinationId)
        XCTAssertEqual("f4b95431-0943-435b-95c7-304b95762e51", table["cool_clear_male"]?.sourceRowId)
        XCTAssertEqual("T1", table["cool_clear_male"]?.rung)
        // FREEZING × RAIN, restored cell.
        XCTAssertEqual("0b891b30-75ed-4337-939c-6219d9f1309a", table["freezing_rain_female"]?.sourceRowId)
        XCTAssertEqual("T3", table["freezing_rain_female"]?.rung)
        XCTAssertEqual("ad138c05-da7e-4cc3-ac63-f73bcf3bc21b", table["freezing_rain_male"]?.sourceRowId)
        XCTAssertEqual("T1", table["freezing_rain_male"]?.rung)
        // The manifest's two alternates were taken at their PRIMARY
        // picks: FREEZING CLEAR · F is the T1 NIGHT row (not the T3
        // MIDDAY alternate), MILD RAIN · M is the T1 NIGHT row.
        XCTAssertEqual("FEMALE_CLEAR_FREEZING_NIGHT_v4", table["freezing_clear_female"]?.combinationId)
        XCTAssertEqual("MALE_RAINY_MILD_NIGHT_v3", table["mild_rain_male"]?.combinationId)
    }

    func testThe5ReAdjudicationRejectedRW20SourcesAreNotInTheBundleWiring() {
        // D-21: these source combinations were retired by the legacy
        // re-adjudication (derivation record §4); none may back a
        // bundled asset anymore.
        let combinations = Set(FallbackImageProvider.provenanceByAsset.values.map { $0.combinationId })
        for retired in [
            "FEMALE_CLEAR_COOL_DAWN_v15",
            "FEMALE_CLOUDY_COOL_DAWN_v12",
            "FEMALE_RAIN_COLD_DAWN_v12",
            "FEMALE_RAIN_MILD_DUSK_v4",
            "FEMALE_CLEAR_HOT_DAWN_v1"
        ] {
            XCTAssertFalse(combinations.contains(retired), "\(retired) must not back any bundled asset")
        }
        // Their slots re-derived at T1 from approved stock (the
        // cold_rain replacement is T3 by the manifest — the
        // recalibrated verdict — and is pinned in the manifest test
        // above via its own slot family).
        let table = FallbackImageProvider.provenanceByAsset
        XCTAssertEqual("T1", table["cool_clear_female"]?.rung)
        XCTAssertEqual("T1", table["cool_cloudy_female"]?.rung)
        XCTAssertEqual("T1", table["mild_rain_female"]?.rung)
        XCTAssertEqual("T1", table["hot_clear_female"]?.rung)
    }

    func testABundledServeResultCarriesItsProvenance() {
        // The provider's bundled result (cascade level 8) must carry
        // source + provenance + asset file for the serve record.
        let result = provider.getFallbackResult(temp: .COOL, weather: .CLEAR, gender: .MALE)
        XCTAssertEqual(HeroServeSource.bundledFallback, result.serveSource)
        XCTAssertEqual("cool_clear_male", result.fallbackAssetFile)
        XCTAssertEqual(
            "f4b95431-0943-435b-95c7-304b95762e51",
            result.fallbackProvenance?.sourceRowId
        )
        XCTAssertEqual("T1", result.fallbackProvenance?.rung)
    }

    func testBundledURLGroundTruth_assetAndProvenanceDeriveFromTheURL() {
        let url = "file:///RunWear.app/hero_fallback/cool_snow_male.webp"
        XCTAssertTrue(FallbackImageProvider.isBundledURL(url))
        XCTAssertEqual("cool_snow_male", FallbackImageProvider.assetFile(fromURLString: url))
        XCTAssertEqual(
            "f8e3791a-6fe2-472c-ad80-e058a00ed792",
            FallbackImageProvider.provenance(forURLString: url)?.sourceRowId
        )
        XCTAssertFalse(FallbackImageProvider.isBundledURL("https://cdn.example/pool/row-1.webp"))
    }
}
