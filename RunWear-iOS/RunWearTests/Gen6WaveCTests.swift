import XCTest

/// GEN-6 Wave C (iOS lane) — parity, occlusion, copy, and the
/// missing looks library. Every test pins a wave item against the
/// settled design corpus (design-spec rw-gen4-walkthrough +
/// final-design frames), following the suite's established
/// pattern: pure logic pinned on the dual-membered models, view
/// settlements pinned by reading the source of record.
final class Gen6WaveCTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func chart(hours: [Int]) -> DayChartModel {
        DayChartModel.build(points: hours.map { hour in
            DayChartHour(hour: hour, feelsLikeF: 50, bracket: .mild, outfitItemNames: [])
        })!
    }

    // MARK: - DA-14: watch glance is the comfort-adjusted feels-like

    func testDA14WatchGlanceRendersTheComfortAdjustedFigure() {
        // Raw 50°F with a non-zero comfort adjustment: the glance
        // must show the ADJUSTED figure the outfit is priced off,
        // never the raw 50°.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryWarm, unit: .fahrenheit),
            "60°")
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryCold, unit: .fahrenheit),
            "40°")
        // Neutral adjustment: adjusted == raw.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .neutral, unit: .fahrenheit),
            "50°")
        // Celsius converts the ADJUSTED figure: 60°F -> 16°C.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryWarm, unit: .celsius),
            "16°")
    }

    func testDA14WatchDisplayTemperatureUsesTheGlanceRule_sourcePin() throws {
        let vm = try sourceFile("../RunWearWatch/WatchViewModel.swift")
        XCTAssertTrue(vm.contains("Self.glanceTemperatureString("),
                      "displayTemperature must render through the comfort-adjusted glance rule")
        XCTAssertTrue(vm.contains("comfortLevel.adjustTemperature(rawFeelsLikeF)"),
                      "the glance rule must apply the comfort adjustment")
        XCTAssertFalse(vm.contains("let feelsLikeF = currentFeelsLikeF ?? weather.currentWeather.temperatureFahrenheit\n        let temp = temperatureUnit"),
                       "the gen-5 raw-value rendering must not return")
    }

    // MARK: - DA-05 (iOS): day-view hero on the protected HeroPhoto crop

    func testDA05DayViewHeroUsesHeroPhotoCrop_sourcePin() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        let dayStart = try XCTUnwrap(collection.range(of: "struct WearIveRunDayView"))
        let dayView = String(collection[dayStart.lowerBound...])
        XCTAssertTrue(dayView.contains("HeroPhoto(image: image, width: geo.size.width, height: geo.size.height)"),
                      "the day-view hero must render through HeroPhoto's top-anchored crop")
        XCTAssertFalse(dayView.contains("image.resizable().aspectRatio(contentMode: .fill)"),
                       "the bare centre fill must not return on the day-view hero")
    }

    // MARK: - DA-21: the time picker names a partial day's beyond-forecast region

    func testDA21PartialDayNamesBeyondForecast() {
        XCTAssertEqual(DayChartBeyondForecast.caption(for: chart(hours: Array(0...14))), "Beyond forecast")
        XCTAssertEqual(DayChartBeyondForecast.caption(for: chart(hours: Array(6...23))), "Beyond forecast")
        XCTAssertEqual(DayChartBeyondForecast.label, "Beyond forecast",
                       "the picker's name is the date cells' name, verbatim")
    }

    func testDA21FullDayNamesNothing() {
        XCTAssertNil(DayChartBeyondForecast.caption(for: chart(hours: Array(0...23))))
    }

    func testDA21TimePickerRendersTheCaption_sourcePin() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/TimePickerSheet.swift")
        XCTAssertTrue(sheet.contains("DayChartBeyondForecast.caption(for: chart)"),
                      "the time picker must render the beyond-forecast caption for a partial day")
        XCTAssertTrue(sheet.contains("Forecast isn't available for this day yet."),
                      "the wholly-empty day keeps its own naming")
    }

    // MARK: - RW-54 (iOS): one-line outfit notes; headline moment in pine bold

    func testRW54OutfitNoteIsOneLineAndMomentIsBold_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // The outfit-row note: exactly one lineLimit(1) on the
        // description in DarkClothingItemCard, and no lineLimit(2)
        // left on that card.
        let cardStart = try XCTUnwrap(home.range(of: "struct DarkClothingItemCard"))
        let card = String(home[cardStart.lowerBound...])
        XCTAssertTrue(card.contains(".lineLimit(1)"), "the outfit-row note is one line")
        XCTAssertFalse(card.contains(".lineLimit(2)"), "the two-line note must not return")
        // The headline moment carries its own bold font, not the
        // line's inherited semibold.
        XCTAssertTrue(home.contains(".font(.system(size: 15.5, weight: .bold))"),
                      "the headline moment renders in pine bold per the design")
    }
}
