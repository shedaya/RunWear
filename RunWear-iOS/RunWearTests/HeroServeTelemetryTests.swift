import XCTest

/// WS-7 / RW-26(c) — serve-source telemetry (D-18's attribution fix).
///
/// Every hero serve emits exactly one record naming its source —
/// pool / bundled-fallback / personal / scaffold — with the cell,
/// gender, and served combination where applicable, and (for a
/// bundled serve) the asset file + manifest provenance. These tests
/// pin that each serve kind emits its record through
/// HeroServeTelemetry, the channel the view model paints through,
/// and that a bundled serve's remote filing (HeroServeDiagnostics)
/// carries the record under the automated `diagnostics` category.
/// Mirror of Android's HeroServeTelemetryTest +
/// HeroServeDiagnosticsTest.
final class HeroServeTelemetryTests: XCTestCase {

    private var captured: [HeroServeRecord] = []

    override func setUp() {
        super.setUp()
        HeroServeTelemetry.clear()
        captured = []
        HeroServeTelemetry.installSink { [weak self] record in
            self?.captured.append(record)
        }
    }

    override func tearDown() {
        HeroServeTelemetry.installSink(HeroServeTelemetry.defaultSink)
        HeroServeTelemetry.clear()
        super.tearDown()
    }

    private func emitPool(trigger: String = "pool-cascade") {
        let image = GeneratedImage(
            id: "row-1",
            combinationId: "MALE_CLEAR_COOL_MIDDAY_ffff0000",
            imageUrl: "https://cdn.example/pool/row-1.webp",
            thumbnailUrl: "https://cdn.example/pool/row-1.webp",
            prompt: nil,
            reviewStatus: "approved"
        )
        let result = HeroImageResult.fromMatch(image: image, fallbackLevel: 1)
        HeroServeTelemetry.emitForResult(
            result,
            requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
            bracket: .COOL,
            weather: .CLEAR,
            gender: .male,
            trigger: trigger
        )
    }

    func testAPoolServeEmitsAPoolRecordWithItsRowIdentity() {
        emitPool()
        XCTAssertEqual(1, captured.count)
        let record = captured[0]
        XCTAssertEqual(HeroServeSource.pool, record.source)
        XCTAssertEqual("pool", record.source.wire)
        XCTAssertEqual("row-1", record.imageRowId)
        XCTAssertEqual("MALE_CLEAR_COOL_MIDDAY_ffff0000", record.servedCombinationId)
        XCTAssertEqual(HeroTempBracket.COOL, record.tempBracket)
        XCTAssertEqual(HeroWeatherCondition.CLEAR, record.weatherCondition)
        XCTAssertEqual(GenderPreference.male, record.gender)
        XCTAssertEqual("pool-cascade", record.trigger)
        XCTAssertTrue(record.summary().contains("source=pool"))
    }

    func testABundledFallbackServeEmitsABundledRecordWithAssetAndProvenance() {
        let result = FallbackImageProvider.shared.getFallbackResult(temp: .COOL, weather: .CLEAR, gender: .MALE)
        // In this logic-test host the bundle URL cannot resolve; the
        // record path keys on the URL in production, so hand
        // emitForResult the same result carrying its production URL
        // shape (the provider result's asset/provenance ride along).
        let productionShaped = HeroImageResult(
            imageUrl: "file:///RunWear.app/hero_fallback/cool_clear_male.webp",
            thumbnailUrl: nil,
            combinationId: result.combinationId,
            imageRowId: result.imageRowId,
            isFromFallback: true,
            fallbackLevel: 8,
            serveSource: result.serveSource,
            fallbackProvenance: result.fallbackProvenance,
            fallbackAssetFile: result.fallbackAssetFile
        )
        HeroServeTelemetry.emitForResult(
            productionShaped,
            requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
            bracket: .COOL,
            weather: .CLEAR,
            gender: .male,
            trigger: "bundled-fallback"
        )
        XCTAssertEqual(1, captured.count)
        let record = captured[0]
        XCTAssertEqual(HeroServeSource.bundledFallback, record.source)
        XCTAssertEqual("bundled-fallback", record.source.wire)
        XCTAssertEqual("cool_clear_male", record.fallbackAssetFile)
        XCTAssertEqual(
            "f4b95431-0943-435b-95c7-304b95762e51",
            record.fallbackProvenance?.sourceRowId
        )
        XCTAssertEqual("MALE_CLEAR_COOL_MIDDAY_84c10525", record.fallbackProvenance?.combinationId)
        let summary = record.summary()
        XCTAssertTrue(summary.contains("source=bundled-fallback"))
        XCTAssertTrue(summary.contains("asset=cool_clear_male"))
        XCTAssertTrue(summary.contains("provenance_row=f4b95431-0943-435b-95c7-304b95762e51"))
        XCTAssertTrue(summary.contains("rung=T1"))
    }

    func testABundledURLIsRecordedAsBundledEvenWhenTheResultIsUnlabelled() {
        // The URL is the ground truth: a bundled paint arriving with
        // no serveSource label and no pool row id must still
        // attribute as bundled-fallback, with asset + provenance
        // derived from the URL.
        let result = HeroImageResult.fromUnsplash(
            url: "file:///RunWear.app/hero_fallback/cool_snow_male.webp"
        )
        HeroServeTelemetry.emitForResult(
            result,
            requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
            bracket: .COOL,
            weather: .CLEAR,
            gender: .male,
            trigger: "pool-cascade"
        )
        XCTAssertEqual(1, captured.count)
        let record = captured[0]
        XCTAssertEqual(HeroServeSource.bundledFallback, record.source)
        XCTAssertEqual("cool_snow_male", record.fallbackAssetFile)
        XCTAssertEqual(
            "f8e3791a-6fe2-472c-ad80-e058a00ed792",
            record.fallbackProvenance?.sourceRowId
        )
    }

    func testPersonalAndScaffoldServesEmitTheirRecords() {
        HeroServeTelemetry.emit(
            HeroServeRecord(
                source: .personal,
                requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
                gender: .male,
                trigger: "personal-overlay"
            )
        )
        HeroServeTelemetry.emit(
            HeroServeRecord(
                source: .scaffold,
                requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
                gender: .male,
                trigger: "personal-overlay"
            )
        )
        XCTAssertEqual(2, captured.count)
        XCTAssertEqual(HeroServeSource.personal, captured[0].source)
        XCTAssertEqual("personal", captured[0].source.wire)
        XCTAssertEqual(HeroServeSource.scaffold, captured[1].source)
        XCTAssertEqual("scaffold", captured[1].source.wire)
        // History retains both, in order — the after-the-fact channel.
        XCTAssertEqual(captured, HeroServeTelemetry.recent())
    }

    func testAResultWithNoImageEmitsNothing() {
        let result = HeroImageResult.fromBundledFallback(url: "", assetFile: "cool_clear_male", provenance: nil)
        HeroServeTelemetry.emitForResult(
            result,
            requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
            bracket: .COOL,
            weather: .CLEAR,
            gender: .male,
            trigger: "pool-cascade"
        )
        XCTAssertTrue(captured.isEmpty)
    }

    // MARK: - Remote filing (bundled serves only)

    private var bundledRecord: HeroServeRecord {
        HeroServeRecord(
            source: .bundledFallback,
            requestedCombinationId: "MALE_CLEAR_COOL_MIDDAY_abcd1234",
            tempBracket: .COOL,
            weatherCondition: .CLEAR,
            gender: .male,
            fallbackAssetFile: "cool_clear_male",
            fallbackProvenance: FallbackAssetProvenance(
                combinationId: "MALE_CLEAR_COOL_MIDDAY_84c10525",
                sourceRowId: "f4b95431-0943-435b-95c7-304b95762e51",
                rung: "T1"
            ),
            trigger: "bundled-fallback"
        )
    }

    private func payloadObject(_ record: HeroServeRecord) throws -> [String: Any] {
        let data = try XCTUnwrap(
            HeroServeDiagnostics.payloadData(record: record, appVersion: "1.0", installId: "install-1")
        )
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testBundledServePayloadFilesUnderDiagnosticsWithTheRecordAsItsText() throws {
        let payload = try payloadObject(bundledRecord)
        XCTAssertEqual("diagnostics", payload["category"] as? String)
        XCTAssertEqual("hero_serve", payload["entryPoint"] as? String)
        XCTAssertEqual(bundledRecord.summary(), payload["text"] as? String)
        XCTAssertTrue((payload["text"] as? String ?? "").contains("source=bundled-fallback"))
        XCTAssertTrue((payload["text"] as? String ?? "").contains("asset=cool_clear_male"))
        XCTAssertTrue((payload["text"] as? String ?? "").contains("trigger=bundled-fallback"))
        // Automated filing: never a human category, no reply email.
        XCTAssertNil(payload["replyEmail"])
        XCTAssertFalse(FeedbackRouting.isHumanCategory(payload["category"] as? String ?? ""))
    }

    func testPayloadBundleStaysInsideTheIntakeAllowlist_allServeFactsRideInTheText() throws {
        let payload = try payloadObject(bundledRecord)
        let bundle = try XCTUnwrap(payload["bundle"] as? [String: Any])
        // runwear-feedback/1 rejects unknown bundle keys with 422, so
        // the bundle is exactly the allowlisted engineVersion.
        XCTAssertEqual(Set(["engineVersion"]), Set(bundle.keys))
        let text = try XCTUnwrap(payload["text"] as? String)
        XCTAssertTrue(text.contains("bracket=COOL"))
        XCTAssertTrue(text.contains("weather=CLEAR"))
        XCTAssertTrue(text.contains("gender=MALE"))
        XCTAssertTrue(text.contains("requested=MALE_CLEAR_COOL_MIDDAY_abcd1234"))
        XCTAssertTrue(text.contains("provenance_combination=MALE_CLEAR_COOL_MIDDAY_84c10525"))
        XCTAssertTrue(text.contains("provenance_row=f4b95431-0943-435b-95c7-304b95762e51"))
        XCTAssertTrue(text.contains("rung=T1"))
    }

    func testPayloadCarriesTheStandardTelemetryEnvelope() throws {
        let payload = try payloadObject(bundledRecord)
        XCTAssertEqual("runwear-feedback/1", payload["schema"] as? String)
        XCTAssertEqual("runwear", payload["app"] as? String)
        XCTAssertEqual("ios", payload["platform"] as? String)
        XCTAssertEqual("1.0", payload["appVersion"] as? String)
        XCTAssertEqual("install-1", payload["installId"] as? String)
    }
}
