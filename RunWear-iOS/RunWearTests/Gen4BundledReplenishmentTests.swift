import XCTest
import Foundation

/// GEN-4 train integration (2026-11-04): the pool replenishment's
/// 20 bundled point-grain assets (RWPOOL-T07…T26) — the offline
/// floor for 20 of the 26 GEN-3 census signatures the 44-asset
/// matrix cannot serve. Each asset was rendered from the
/// replenishment's own newly approved pool row and
/// pixel-verified against it (bundled-pg/manifest.json is the
/// record of source). These pins prove, per asset:
///
///  * the file is in the bundle's hero_fallback folder;
///  * its census tuple is the signature its name encodes, and
///    its provenance is the manifest's source combination + row
///    (rung "PG");
///  * it PASSES the iOS point-grain gate
///    (FallbackImageProvider.bundledAssetMatches, the
///    HeroPointGrainPolicy seam) for its target signature's
///    prescription — it may claim that outfit;
///  * the GEN-4 chain serves it EXACT (offline, pool empty and
///    unreachable) for that prescription, and the gated walk
///    nominates it — so the signature exact-serves offline.
///
/// The 6 mittens signatures (RWPOOL-T01…T06) are deliberately
/// absent: no truthful mittens photo exists, and nothing here
/// fabricates one. Under Sol's handwear substitution ruling
/// (2026-11-04 ~22:23 CST) mittens prescriptions exact-serve
/// matching GLOVES candidates through the shared gate where the
/// other slots align, and scaffold/floor via the chain where
/// they do not (pinned in Gen4HeroFallbackTests).
final class Gen4BundledReplenishmentTests: XCTestCase {

    private let provider = FallbackImageProvider.shared

    private struct Row {
        let target: String
        let asset: String
        let gender: HeroGender
        let top: String
        let outer: Bool
        let bottom: String
        let head: String
        let hands: String
        let combination: String
        let sourceRow: String
    }

    /// The replenishment manifest, in target order T07…T26.
    private let rows: [Row] = [
        Row(target: "RWPOOL-T07", asset: "pg_female_long_o0_shorts_cap_none", gender: .FEMALE, top: "long", outer: false, bottom: "shorts", head: "cap", hands: "none", combination: "FEMALE_CLEAR_COOL_MIDDAY_01285eec", sourceRow: "72f29634-32a3-4382-9dbe-8705a1e1e9bc"),
        Row(target: "RWPOOL-T08", asset: "pg_female_long_o0_shorts_none_none", gender: .FEMALE, top: "long", outer: false, bottom: "shorts", head: "none", hands: "none", combination: "FEMALE_CLEAR_COOL_DAWN_726beba2", sourceRow: "92c54e8c-1e58-4d40-acd8-ace41fc00678"),
        Row(target: "RWPOOL-T09", asset: "pg_female_long_o1_shorts_none_none", gender: .FEMALE, top: "long", outer: true, bottom: "shorts", head: "none", hands: "none", combination: "FEMALE_CLEAR_COOL_DAWN_f48f0719", sourceRow: "801b8ec7-b176-461d-ac58-2f952237a521"),
        Row(target: "RWPOOL-T10", asset: "pg_male_long_o0_shorts_cap_none", gender: .MALE, top: "long", outer: false, bottom: "shorts", head: "cap", hands: "none", combination: "MALE_CLEAR_COOL_MIDDAY_01285eec", sourceRow: "c9d4e9ca-f4e8-4f23-a4bd-92a1db0ab828"),
        Row(target: "RWPOOL-T11", asset: "pg_male_long_o0_shorts_none_none", gender: .MALE, top: "long", outer: false, bottom: "shorts", head: "none", hands: "none", combination: "MALE_CLEAR_COOL_DAWN_726beba2", sourceRow: "8fe47ab5-ee32-4613-a416-4a631dc1d691"),
        Row(target: "RWPOOL-T12", asset: "pg_male_long_o1_shorts_none_none", gender: .MALE, top: "long", outer: true, bottom: "shorts", head: "none", hands: "none", combination: "MALE_CLEAR_COOL_DAWN_f48f0719", sourceRow: "b949a5cc-d3a3-4e4d-a787-3aca222bced7"),
        Row(target: "RWPOOL-T13", asset: "pg_female_long_o0_tights_beanie_gloves", gender: .FEMALE, top: "long", outer: false, bottom: "tights", head: "beanie", hands: "gloves", combination: "FEMALE_CLEAR_COLD_DAWN_86a1b05a", sourceRow: "a3ac61b5-8186-44c1-a35b-149517500cc0"),
        Row(target: "RWPOOL-T14", asset: "pg_female_long_o0_tights_cap_none", gender: .FEMALE, top: "long", outer: false, bottom: "tights", head: "cap", hands: "none", combination: "FEMALE_CLEAR_COOL_MIDDAY_2afe0e3d", sourceRow: "9b7b4c79-b4bf-460b-b89e-191884d8c66b"),
        Row(target: "RWPOOL-T15", asset: "pg_female_long_o0_tights_none_none", gender: .FEMALE, top: "long", outer: false, bottom: "tights", head: "none", hands: "none", combination: "FEMALE_CLEAR_COOL_DAWN_e216ba73", sourceRow: "767527d5-c0e9-4ec6-860b-2879aea3aa2e"),
        Row(target: "RWPOOL-T16", asset: "pg_female_long_o1_tights_cap_none", gender: .FEMALE, top: "long", outer: true, bottom: "tights", head: "cap", hands: "none", combination: "FEMALE_CLEAR_COOL_MIDDAY_32531934", sourceRow: "b605a141-652a-4520-b26e-6e113843bfe4"),
        Row(target: "RWPOOL-T17", asset: "pg_female_long_o1_tights_none_none", gender: .FEMALE, top: "long", outer: true, bottom: "tights", head: "none", hands: "none", combination: "FEMALE_CLEAR_COOL_DAWN_5d6d246a", sourceRow: "46c26b43-70d8-47bd-9332-b6eccf36b584"),
        Row(target: "RWPOOL-T18", asset: "pg_male_long_o0_tights_beanie_gloves", gender: .MALE, top: "long", outer: false, bottom: "tights", head: "beanie", hands: "gloves", combination: "MALE_CLEAR_COLD_DAWN_86a1b05a", sourceRow: "eba55b64-1020-4b0b-9415-422403101247"),
        Row(target: "RWPOOL-T19", asset: "pg_male_long_o0_tights_cap_none", gender: .MALE, top: "long", outer: false, bottom: "tights", head: "cap", hands: "none", combination: "MALE_CLEAR_COOL_MIDDAY_2afe0e3d", sourceRow: "c1c08b14-5285-4c8c-b6f8-e824557de115"),
        Row(target: "RWPOOL-T20", asset: "pg_male_long_o0_tights_none_none", gender: .MALE, top: "long", outer: false, bottom: "tights", head: "none", hands: "none", combination: "MALE_CLEAR_COOL_DAWN_e216ba73", sourceRow: "d18cbbc9-fcb3-463e-a046-8b873750cd46"),
        Row(target: "RWPOOL-T21", asset: "pg_male_long_o1_tights_none_none", gender: .MALE, top: "long", outer: true, bottom: "tights", head: "none", hands: "none", combination: "MALE_CLEAR_COOL_DAWN_5d6d246a", sourceRow: "bb301f8a-240d-4aec-9e1b-0cd54d2a5cc0"),
        Row(target: "RWPOOL-T22", asset: "pg_female_sleeveless_o0_shorts_visor_none", gender: .FEMALE, top: "sleeveless", outer: false, bottom: "shorts", head: "visor", hands: "none", combination: "FEMALE_CLEAR_HOT_MIDDAY_c9b28fa9", sourceRow: "751d99e8-d36c-46ba-b029-f5cc8607077d"),
        Row(target: "RWPOOL-T23", asset: "pg_male_short_o0_shorts_visor_none", gender: .MALE, top: "short", outer: false, bottom: "shorts", head: "visor", hands: "none", combination: "MALE_CLEAR_WARM_MIDDAY_83b3d617", sourceRow: "c682b3cc-fd5c-415b-8ca2-c69762de383f"),
        Row(target: "RWPOOL-T24", asset: "pg_male_short_o1_shorts_visor_none", gender: .MALE, top: "short", outer: true, bottom: "shorts", head: "visor", hands: "none", combination: "MALE_RAIN_WARM_MIDDAY_f8d85d23", sourceRow: "f7dd3cc1-4dd0-4767-8cab-ac1599d2abe1"),
        Row(target: "RWPOOL-T25", asset: "pg_male_sleeveless_o0_shorts_visor_none", gender: .MALE, top: "sleeveless", outer: false, bottom: "shorts", head: "visor", hands: "none", combination: "MALE_CLEAR_HOT_MIDDAY_c9b28fa9", sourceRow: "9d4b71bb-28ef-4eb1-83e8-6f2761bdbbbd"),
        Row(target: "RWPOOL-T26", asset: "pg_male_sleeveless_o1_shorts_visor_none", gender: .MALE, top: "sleeveless", outer: true, bottom: "shorts", head: "visor", hands: "none", combination: "MALE_RAIN_HOT_MIDDAY_87d183b5", sourceRow: "3643f7ec-59a2-4904-8aec-d1297e426857"),
    ]

    // MARK: - Builders (the Gen4HeroFallbackTests seam)

    private func item(_ key: String) -> ClothingItem {
        ClothingItem(key: key, name: key, description: "", icon: "", amazonSearchTerm: "")
    }

    private func outfit(for row: Row) -> OutfitRecommendation {
        let topKey: String
        switch row.top {
        case "sleeveless": topKey = "TANK_TOP"
        case "short": topKey = "SHORT_SLEEVE"
        default: topKey = "LONG_SLEEVE_LIGHT"
        }
        let headKey: String?
        switch row.head {
        case "cap": headKey = "BASEBALL_CAP"
        case "headband": headKey = "HEADBAND"
        case "beanie": headKey = "LIGHT_BEANIE"
        case "balaclava": headKey = "BALACLAVA"
        case "visor": headKey = "VISOR"
        default: headKey = nil
        }
        let handsKey: String?
        switch row.hands {
        case "gloves": handsKey = "LIGHT_GLOVES"
        case "mittens": handsKey = "MITTENS"
        default: handsKey = nil
        }
        return OutfitRecommendation(
            temperatureBracket: .cool,
            topBase: item(topKey),
            topMid: nil,
            topOuter: row.outer ? item("WINDBREAKER") : nil,
            bottom: item(row.bottom == "shorts" ? "SHORTS" : "LIGHT_TIGHTS"),
            head: headKey.map(item),
            hands: handsKey.map(item),
            accessories: [],
            tips: []
        )
    }

    // MARK: - Registration

    func testAll20TargetsAreRegisteredWithManifestProvenance() {
        XCTAssertEqual(20, rows.count)
        XCTAssertEqual(20, FallbackImageProvider.pointGrainGarmentsByAsset.count)
        XCTAssertEqual(20, FallbackImageProvider.pointGrainProvenanceByAsset.count)
        XCTAssertEqual(20, FallbackImageProvider.pointGrainAssetsBySignature.count)
        for row in rows {
            let garments = FallbackImageProvider.pointGrainGarmentsByAsset[row.asset]
            XCTAssertEqual(
                garments,
                FallbackImageProvider.BundledGarments(
                    topBase: row.top, outer: row.outer, bottom: row.bottom,
                    head: row.head == "none" ? nil : row.head,
                    hands: row.hands == "none" ? nil : row.hands
                ),
                "\(row.target) census tuple must be the signature its name encodes"
            )
            let prov = FallbackImageProvider.pointGrainProvenanceByAsset[row.asset]
            XCTAssertEqual(prov?.combinationId, row.combination, row.target)
            XCTAssertEqual(prov?.sourceRowId, row.sourceRow, row.target)
            XCTAssertEqual(prov?.rung, "PG", row.target)
            // The union accessors resolve the pg asset too.
            XCTAssertEqual(FallbackImageProvider.bundledProvenance(forAssetName: row.asset), prov)
            XCTAssertEqual(FallbackImageProvider.provenance(forAssetName: row.asset), prov)
            // The signature nomination names this asset.
            let key = "\(row.gender.rawValue)|\(row.top)|\(row.outer)|\(row.bottom)|\(row.head)|\(row.hands)"
            XCTAssertEqual(FallbackImageProvider.pointGrainAssetsBySignature[key], row.asset, row.target)
            // And the chain's candidate set carries it, gendered.
            XCTAssertTrue(
                FallbackImageProvider.bundledCandidates.contains {
                    $0.bundledAssetName == row.asset && $0.gender == row.gender && $0.source == .bundled
                },
                "\(row.target) missing from the chain's bundled candidate set"
            )
        }
        XCTAssertEqual(64, FallbackImageProvider.bundledCandidates.count,
                       "44 matrix + 20 point-grain candidates")
    }

    func testNoMittensAssetIsRegistered() {
        // RWPOOL-T01…T06: no asset, no census entry, no
        // nomination — nothing fabricated.
        for (name, garments) in FallbackImageProvider.pointGrainGarmentsByAsset {
            XCTAssertNotEqual(garments.hands, "mittens", name)
        }
        for key in FallbackImageProvider.pointGrainAssetsBySignature.keys {
            XCTAssertFalse(key.hasSuffix("|mittens"), key)
        }
    }

    // MARK: - The gate: each asset passes for its target signature

    func testEachAssetPassesTheGateForItsTargetSignature() {
        for row in rows {
            XCTAssertTrue(
                FallbackImageProvider.bundledAssetMatches(assetName: row.asset, outfit: outfit(for: row)),
                "\(row.target) \(row.asset) must pass the point-grain gate for its target signature"
            )
        }
    }

    // MARK: - Serving: chain-exact offline + gated-walk nomination

    func testEachSignatureExactServesOfflineThroughTheChain() {
        for row in rows {
            let axes = HeroFallbackChain.combinationAxes(row.combination)!
            let res = provider.chainResolution(
                temp: axes.bracket, weather: axes.weather, gender: row.gender,
                outfit: outfit(for: row), poolCandidates: [], poolReachable: false
            )
            XCTAssertEqual(res?.tier, .exact, row.target)
            XCTAssertEqual(res?.bundledAssetName, row.asset, row.target)
            XCTAssertEqual(res?.candidate.gender, row.gender, row.target)
            // The serve result carries the manifest provenance.
            let result = res.flatMap { provider.result(for: $0) }
            XCTAssertEqual(result?.fallbackProvenance?.sourceRowId, row.sourceRow, row.target)
            XCTAssertEqual(result?.fallbackProvenance?.combinationId, row.combination, row.target)
        }
    }

    func testGatedWalkNominatesEachAssetForItsSignature() {
        for row in rows {
            let axes = HeroFallbackChain.combinationAxes(row.combination)!
            XCTAssertEqual(
                provider.gatedAssetName(temp: axes.bracket, weather: axes.weather, gender: row.gender, outfit: outfit(for: row)),
                row.asset,
                row.target
            )
        }
    }

    // MARK: - The files themselves (source-tree pin)

    func testAssetFilesAreInTheBundleFolder() throws {
        let dir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../RunWear/hero_fallback")
        for row in rows {
            let file = dir.appendingPathComponent("\(row.asset).webp")
            var isDir: ObjCBool = false
            XCTAssertTrue(FileManager.default.fileExists(atPath: file.path, isDirectory: &isDir),
                          "\(row.target) file missing at \(file.path)")
            let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? Int) ?? 0
            XCTAssertGreaterThan(size ?? 0, 10_000, "\(row.target) file suspiciously small")
        }
    }
}
