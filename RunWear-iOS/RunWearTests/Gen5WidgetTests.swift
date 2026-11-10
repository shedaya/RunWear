import XCTest

// NOTE: no `@testable import RunWear` — the suite's standing
// convention (see Gen3TrainDeltaTests): the test target compiles
// the model-layer sources directly. (This file shipped with the
// import in the first gen-5 CI run, 42036497425, and failed the
// module scan with exactly the documented error.)

/// GEN-5 (Addendum 7 — RW-10): the iOS home-screen widget.
///
/// The widget renders the persisted last-good recommendation
/// (Android's WidgetLastGoodStore pattern). These tests pin:
/// the record's round-trip and corruption behaviour, the
/// freshness model's label forms and thresholds (ported from
/// Android's WidgetFreshness so both platforms age data the
/// same way), the authored error categories, and — by source —
/// the per-family compositions and the freshness label's seat
/// in each, because a label that exists in code but never
/// renders is this studio's known widget failure.
final class Gen5WidgetTests: XCTestCase {

    private func record(savedAt: Date) -> WidgetLastGood {
        WidgetLastGood(
            feelsLikeF: 41,
            temperatureF: 38,
            conditionText: "Clear",
            conditionIcon: "sun.max.fill",
            windMph: 8,
            humidityPercent: 54,
            uvIndex: 1,
            locationName: "Kansas City, MO",
            topBaseName: "Long Sleeve Shirt",
            layerNames: ["Windbreaker"],
            bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Windbreaker · rain 40%",
            unitIsCelsius: false,
            savedAt: savedAt
        )
    }

    // MARK: Store round-trip + corruption

    func testRecordRoundTripsThroughTheStore() {
        let defaults = UserDefaults(suiteName: "Gen5WidgetTests")!
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
        let saved = record(savedAt: Date(timeIntervalSince1970: 1_762_000_000))
        WidgetLastGoodStore.save(saved, to: defaults)
        XCTAssertEqual(WidgetLastGoodStore.load(from: defaults), saved)
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
    }

    func testCorruptPayloadReadsAsNoRecord() {
        let defaults = UserDefaults(suiteName: "Gen5WidgetTests")!
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
        defaults.set(Data([0x00, 0x01, 0x02]), forKey: WidgetLastGoodStore.recordKey)
        XCTAssertNil(WidgetLastGoodStore.load(from: defaults),
                     "a torn write reads as no last-good, never a crash (Android's rule)")
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
    }

    func testErrorCategoryRoundTrips() {
        let defaults = UserDefaults(suiteName: "Gen5WidgetTests")!
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
        XCTAssertNil(WidgetLastGoodStore.loadErrorCategory(from: defaults))
        WidgetLastGoodStore.saveErrorCategory(.location, to: defaults)
        XCTAssertEqual(WidgetLastGoodStore.loadErrorCategory(from: defaults), .location)
        WidgetLastGoodStore.clearErrorCategory(in: defaults)
        XCTAssertNil(WidgetLastGoodStore.loadErrorCategory(from: defaults))
        defaults.removePersistentDomain(forName: "Gen5WidgetTests")
    }

    // MARK: Freshness (Android's WidgetFreshness forms)

    func testEyebrowStatesTheSaveTimeWhileFresh() {
        let saved = Date(timeIntervalSince1970: 1_762_000_000)
        let eyebrow = WidgetFreshness.eyebrow(record(savedAt: saved), now: saved.addingTimeInterval(20 * 60))
        XCTAssertTrue(eyebrow.hasPrefix("FOR NOW · "),
                      "the save moment is ALWAYS stated (Android 1.1.8 rule)")
        XCTAssertFalse(eyebrow.contains("AGO"), "a fresh record states its clock time, not an age")
    }

    func testEyebrowSwitchesToAgeFormOnceStale() {
        let saved = Date(timeIntervalSince1970: 1_762_000_000)
        let record = record(savedAt: saved)
        XCTAssertFalse(WidgetFreshness.isStale(record, now: saved.addingTimeInterval(59 * 60)))
        XCTAssertTrue(WidgetFreshness.isStale(record, now: saved.addingTimeInterval(61 * 60)),
                      "the stale threshold is Android's 60 minutes")
        XCTAssertEqual(
            WidgetFreshness.eyebrow(record, now: saved.addingTimeInterval(2 * 3600 + 5 * 60)),
            "FOR NOW · 2H AGO")
    }

    func testUpdatedLabelClockThenAge() {
        let saved = Date(timeIntervalSince1970: 1_762_000_000)
        let record = record(savedAt: saved)
        XCTAssertTrue(WidgetFreshness.updatedLabel(record, now: saved.addingTimeInterval(30 * 60)).hasPrefix("Updated "))
        XCTAssertEqual(
            WidgetFreshness.updatedLabel(record, now: saved.addingTimeInterval(3 * 3600)),
            "Updated 3h ago",
            "past 90 minutes the Large strip speaks in age form")
    }

    func testAgeForms() {
        XCTAssertEqual(WidgetFreshness.ageShortUpper(30), "JUST NOW")
        XCTAssertEqual(WidgetFreshness.ageShortUpper(25 * 60), "25M AGO")
        XCTAssertEqual(WidgetFreshness.ageShortUpper(26 * 3600), "1D AGO")
        XCTAssertEqual(WidgetFreshness.ageWords(45 * 60), "45 min ago")
        XCTAssertEqual(WidgetFreshness.ageWords(2 * 24 * 3600), "2d ago")
    }

    // MARK: Error categories (authored, verbatim Android)

    func testErrorCategoriesAreAuthoredVerbatim() {
        XCTAssertEqual(WidgetErrorCategory.location.headline, "Couldn't load weather")
        XCTAssertEqual(WidgetErrorCategory.location.reason, "Location isn't available to the widget")
        XCTAssertEqual(WidgetErrorCategory.weather.headline, "Couldn't load weather")
        XCTAssertEqual(WidgetErrorCategory.weather.reason, "Couldn't reach the weather service")
        XCTAssertEqual(WidgetErrorCategory.neverRunReason, "Open RunWear to see your outfit")
    }

    // MARK: Content composition

    func testSupportingLineCarriesActualWindCondition() {
        let record = record(savedAt: Date())
        XCTAssertEqual(record.supportingLine, "Actual 38°F · Wind 8 mph · Clear",
                       "the settled supporting line: actual, wind, condition")
        XCTAssertEqual(record.allGarmentNames,
                       ["Long Sleeve Shirt", "Windbreaker", "Tights", "Running Cap", "Light Gloves"])
    }

    func testCelsiusFormattingFollowsTheSavedUnit() {
        var record = record(savedAt: Date())
        record.unitIsCelsius = true
        XCTAssertEqual(record.formatTemperature(fahrenheit: 41), "5°C")
    }

    // MARK: Source pins — widget definition + per-family seats

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func widgetSource() throws -> String {
        // GEN-6 (RW-67): the entry view moved to
        // RunWearWidgetViews.swift (dual-membered for render
        // tests; the definition file's @main cannot be). The
        // pins below read the widget's full source of record —
        // both files, as one text.
        try sourceFile("../RunWearWidget/RunWearWidget.swift")
            + sourceFile("../RunWearWidget/RunWearWidgetViews.swift")
    }

    func testWidgetOffersExactlyTheCommissionedFamilies() throws {
        let source = try widgetSource()
        XCTAssertTrue(source.contains(".supportedFamilies([.systemSmall, .systemMedium, .systemLarge])"),
                      "Small, Medium, and Large — each composed for its size")
        XCTAssertTrue(source.contains(".configurationDisplayName(AppStrings.widgetName)"),
                      "the widget carries Android's name")
        XCTAssertTrue(source.contains("containerBackground(AppTheme.background, for: .widget)"),
                      "the cream ground is the widget's ground in both appearances")
        let catalog = try sourceFile("../RunWearWidget/Localizable.xcstrings")
        XCTAssertTrue(catalog.contains("\"widget_name\"") && catalog.contains("Running Outfit"),
                      "WP-R3: the name itself lives in the widget catalog, byte-identical")
    }

    func testEveryFamilyRendersTheFreshnessLabel() throws {
        let source = try widgetSource()
        XCTAssertTrue(source.contains("WidgetFreshness.eyebrow(record, now: entry.date)"),
                      "Small and Medium render the eyebrow freshness label from the record at render time")
        XCTAssertTrue(source.contains("WidgetFreshness.updatedLabel(record, now: entry.date)"),
                      "Large renders the brand-strip Updated label")
        XCTAssertTrue(source.contains("record.supportingLine"),
                      "the supporting line (actual, wind, condition) is composed from the record")
        XCTAssertTrue(source.contains("laterTodayLine"),
                      "Large carries the later-today line")
    }

    func testWriterHookLivesInTheViewModel() throws {
        let viewModel = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(viewModel.contains("persistWidgetLastGoodIfNow()"),
                      "the app writes the last-good record on NOW-frame success")
        XCTAssertTrue(viewModel.contains("guard isViewingLocationNow"),
                      "browsed hours never write the widget record")
        XCTAssertTrue(viewModel.contains("reloadTimelines(ofKind: \"RunWearWidget\")"),
                      "a write asks WidgetKit to re-render")
        XCTAssertTrue(viewModel.contains("WidgetLastGoodStore.saveErrorCategory(.weather)"))
        XCTAssertTrue(viewModel.contains("WidgetLastGoodStore.saveErrorCategory(.location)"))
    }
}
