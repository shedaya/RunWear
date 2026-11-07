import XCTest
import SwiftUI
import UIKit
import WidgetKit

/// GEN-6 VQ harness (iOS): visual-QA executor renders for the
/// gen-6 assembled candidate (release/gen6-ios). Every render is
/// a PRODUCTION view composed as production composes it, rendered
/// with ImageRenderer in the CI simulator and written as a PNG
/// into `RunWear-iOS/render-artefacts/` (uploaded by ios-tests.yml
/// as `runwear-ios-renders`). No re-implemented surfaces.
///
/// File names follow TEST-PLAN §0:
/// VQ-<item>-ios-<fixture>-<typescale>.png
final class Gen6VisualQAHarnessTests: XCTestCase {

    // MARK: - Harness plumbing

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("VQ-HARNESS wrote \(name) (\(Int(image.size.width))x\(Int(image.size.height))pt)")
    }

    private func writeText(_ text: String, name: String) throws {
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try text.write(to: dir.appendingPathComponent(name), atomically: true, encoding: .utf8)
    }

    private func imageHasContent(_ image: UIImage) -> Bool {
        guard let cg = image.cgImage else { return false }
        let w = 8, h = 8
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var distinct = Set<Int>()
        for i in stride(from: 0, to: bytes.count, by: 4) {
            distinct.insert(Int(bytes[i]) << 16 | Int(bytes[i + 1]) << 8 | Int(bytes[i + 2]))
        }
        return distinct.count > 2
    }

    // MARK: - VQ-2.1 · Hero numeral at the plan fixtures (108 / -4)

    @MainActor
    private func renderHeroNumeral(feelsLikeF: Double, type: DynamicTypeSize, name: String) throws {
        let row = HomeHeroNumeralRow(
            formattedTemperature: TemperatureUnit.fahrenheit.formatFromFahrenheit(feelsLikeF),
            bracket: HeroTempBracket.from(feelsLikeTemperature: feelsLikeF),
            windMph: 11, rainPercent: 40, humidityPercent: 54, uvIndex: 3
        )
        .environment(\.dynamicTypeSize, type)
        .background(AppTheme.background)
        // Height the composition needs at the sheet's real width
        // (358 pt = 390 pt phone minus the sheet's 16 pt insets) —
        // the RW-67 fitting method, so the canvas never clips by
        // harness choice; any clipping in the PNG is the build's.
        let probe = ImageRenderer(content: row)
        probe.scale = 2
        probe.proposedSize = ProposedViewSize(width: 358, height: nil)
        let needed = probe.uiImage?.size.height ?? 120
        try writeRender(row, size: CGSize(width: 358, height: max(needed, 60)), name: name)
        print("VQ-HARNESS hero \(name): fitting height at 358pt = \(needed)")
    }

    @MainActor
    func testVQ21HeroNumeral_PlanFixtures() throws {
        try renderHeroNumeral(feelsLikeF: 108, type: .large,
                              name: "VQ-2.1-ios-F-HOT108-default.png")
        try renderHeroNumeral(feelsLikeF: 108, type: .accessibility5,
                              name: "VQ-2.1-ios-F-HOT108-max.png")
        try renderHeroNumeral(feelsLikeF: -4, type: .large,
                              name: "VQ-2.1-ios-F-COLDNEG-default.png")
        try renderHeroNumeral(feelsLikeF: -4, type: .accessibility5,
                              name: "VQ-2.1-ios-F-COLDNEG-max.png")
    }

    // MARK: - §1 · Hero pill states (production HeroImageView)

    @MainActor
    private func renderPill(state: HeroPillState, dark: Bool = false, name: String) throws {
        let hero = HeroImageView(
            locationName: "New York, NY",
            temperature: 72, actualTemperature: 75,
            weatherCondition: .clear,
            date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
            tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
            fallbackImageUrl: "", aiImageUrl: nil,
            pillState: state,
            heightMode: .fill,
            showsTopControls: false
        )
        .preferredColorScheme(dark ? .dark : .light)
        try writeRender(hero, size: CGSize(width: 390, height: 520), name: name)
    }

    @MainActor
    func testVQ1PillStates_Light() throws {
        // The policy resolves the scaffold serve to silence —
        // pinned here so the silent render below is the policy's
        // own output, not a hand-picked state.
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5,
                             isScaffoldServe: true),
            .hidden, "scaffold serve resolves silent per the operative pill table")
        try renderPill(state: .makingWithUpsell, name: "VQ-1.1-ios-F-FREE-0-default.png")
        try renderPill(state: .making, name: "VQ-1.2-ios-F-PREM-GEN-default.png")
        try renderPill(state: .freeLook(used: 3, allowance: 5), name: "VQ-1.3-ios-F-FREE-3-default.png")
        try renderPill(state: .exhausted(used: 5, allowance: 5), name: "VQ-1.4-ios-F-FREE-5-default.png")
        try renderPill(state: .premium, name: "VQ-1.5-ios-F-PREM-BANK-default.png")
        try renderPill(state: .hidden, name: "VQ-1.6-ios-F-SCAFFOLD-default.png")
    }

    @MainActor
    func testVQ110PillStates_Dark() throws {
        try renderPill(state: .makingWithUpsell, dark: true, name: "VQ-1.10-ios-F-FREE-0-dark.png")
        try renderPill(state: .freeLook(used: 3, allowance: 5), dark: true, name: "VQ-1.10-ios-F-FREE-3-dark.png")
        try renderPill(state: .exhausted(used: 5, allowance: 5), dark: true, name: "VQ-1.10-ios-F-FREE-5-dark.png")
    }

    // MARK: - §2.9 / §11.5 · Widget at plan fixtures + missing states

    private func widgetRecord(feelsLikeF: Double, place: String, savedAt: Date) -> WidgetLastGood {
        WidgetLastGood(
            feelsLikeF: feelsLikeF,
            temperatureF: feelsLikeF - 3,
            conditionText: feelsLikeF >= 100 ? "Sunny" : "Snow",
            conditionIcon: feelsLikeF >= 100 ? "sun.max.fill" : "snowflake",
            windMph: 8, humidityPercent: 40, uvIndex: feelsLikeF >= 100 ? 9 : 1,
            locationName: place,
            topBaseName: feelsLikeF >= 100 ? "Singlet" : "Thermal Top",
            layerNames: feelsLikeF >= 100 ? [] : ["Insulated Jacket"],
            bottomName: feelsLikeF >= 100 ? "Split Shorts" : "Thermal Tights",
            secondaryNames: feelsLikeF >= 100 ? ["Sunglasses"] : ["Balaclava", "Heavy Gloves"],
            laterTodayLine: "By 6 PM: 91° · clear",
            unitIsCelsius: false,
            savedAt: savedAt
        )
    }

    private func widgetCanvas(_ family: WidgetFamily) -> CGSize {
        switch family {
        case .systemMedium: return CGSize(width: 364, height: 170)
        case .systemLarge: return CGSize(width: 364, height: 382)
        default: return CGSize(width: 170, height: 170)
        }
    }

    @MainActor
    private func renderWidget(entry: RunWearWidgetEntry, family: WidgetFamily,
                              type: DynamicTypeSize = .large, name: String) throws {
        let view = ZStack {
            AppTheme.background
            RunWearWidgetEntryView(entry: entry, familyOverride: family)
                .environment(\.dynamicTypeSize, type)
        }
        try writeRender(view, size: widgetCanvas(family), name: name)
    }

    @MainActor
    func testVQ29Widget_PlanFixtures_LargestType() throws {
        let now = Date(timeIntervalSince1970: 1_798_000_000)
        let hot = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                     record: widgetRecord(feelsLikeF: 108, place: "Phoenix, AZ", savedAt: now),
                                     errorCategory: nil)
        let cold = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                      record: widgetRecord(feelsLikeF: -4, place: "Duluth, MN", savedAt: now),
                                      errorCategory: nil)
        try renderWidget(entry: hot, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-HOT108-max.png")
        try renderWidget(entry: hot, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-HOT108-max.png")
        try renderWidget(entry: hot, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-HOT108-max.png")
        try renderWidget(entry: cold, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-COLDNEG-max.png")
        try renderWidget(entry: cold, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-COLDNEG-max.png")
        try renderWidget(entry: cold, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-COLDNEG-max.png")
    }

    @MainActor
    func testVQ115Widget_RemainingStates() throws {
        let savedAt = Date(timeIntervalSince1970: 1_798_000_000)
        let record = WidgetLastGood(
            feelsLikeF: 36, temperatureF: 39, conditionText: "Clear",
            conditionIcon: "sun.max.fill", windMph: 9, humidityPercent: 48, uvIndex: 2,
            locationName: "New York, NY", topBaseName: "Long Sleeve Shirt",
            layerNames: ["Light Jacket"], bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Light Jacket · rain 20%",
            unitIsCelsius: false, savedAt: savedAt)
        let staleDate = savedAt.addingTimeInterval(2 * 3600 + 5 * 60)
        try renderWidget(entry: RunWearWidgetEntry(date: staleDate, record: record, errorCategory: nil),
                         family: .systemMedium, name: "VQ-11.5-ios-medium-stale-default.png")
        try renderWidget(entry: RunWearWidgetEntry(date: staleDate, record: record, errorCategory: nil),
                         family: .systemLarge, name: "VQ-11.5-ios-large-stale-default.png")
        try renderWidget(entry: RunWearWidgetEntry(date: savedAt, record: nil, errorCategory: .weather),
                         family: .systemSmall, name: "VQ-11.5-ios-small-error-default.png")
        try renderWidget(entry: RunWearWidgetEntry(date: savedAt, record: nil, errorCategory: .weather),
                         family: .systemLarge, name: "VQ-11.5-ios-large-error-default.png")
    }

    // MARK: - §2.8 / §11.3 · watchOS glance at plan fixtures

    @MainActor
    private func renderWatch(feelsLikeF: Double, type: DynamicTypeSize, name: String) throws {
        let celsius = (feelsLikeF - 32) * 5 / 9
        let weather = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: celsius, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: nil, timezone: nil, currentDetail: nil)
        let vm = WatchViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.currentWeather = weather
        XCTAssertEqual(vm.displayTemperature, "\(Int(feelsLikeF.rounded()))°",
                       "glance renders the fixture value")
        let page = WatchGlancePage(viewModel: vm, weather: weather)
            .environment(\.dynamicTypeSize, type)
            .background(Color.black)
        try writeRender(page, size: CGSize(width: 198, height: 242), name: name)
        print("VQ-HARNESS watch \(name): displayTemperature = \(vm.displayTemperature)")
    }

    @MainActor
    func testVQ28WatchGlance_PlanFixtures() throws {
        try renderWatch(feelsLikeF: 108, type: .accessibility5, name: "VQ-2.8-ios-watch-F-HOT108-max.png")
        try renderWatch(feelsLikeF: -4, type: .accessibility5, name: "VQ-2.8-ios-watch-F-COLDNEG-max.png")
        try renderWatch(feelsLikeF: 108, type: .large, name: "VQ-11.3-ios-watch-F-HOT108-default.png")
        try renderWatch(feelsLikeF: -4, type: .large, name: "VQ-11.3-ios-watch-F-COLDNEG-default.png")
    }

    // MARK: - §5.1–5.3 / §2.4 · Picker date axis, to files (RW-66 states)

    private struct PickerFixture {
        let vm: WeatherViewModel
        let today: Date
        let tomorrow: Date
    }

    @MainActor
    private func pickerFixture() -> PickerFixture {
        ManualLocationStore.clear()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today)!
        func stamp(_ date: Date, _ hour: Int) -> String {
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            return String(format: "%04d-%02d-%02dT%02d:00", c.year!, c.month!, c.day!, hour)
        }
        var times: [String] = []
        var temps: [Double] = []
        var apparent: [Double] = []
        for (date, value) in [(today, 18.3), (tomorrow, -1.1)] {
            for hour in 0..<24 {
                times.append(stamp(date, hour))
                temps.append(value)
                apparent.append(value)
            }
        }
        let hourly = HourlyWeather(
            time: times, temperature2m: temps, apparentTemperature: apparent,
            relativeHumidity2m: [Int](repeating: 55, count: 48),
            precipitationProbability: [Int](repeating: 0, count: 48),
            precipitation: [Double](repeating: 0, count: 48),
            rain: [Double](repeating: 0, count: 48),
            uvIndex: [Double](repeating: 3, count: 48),
            windSpeed10m: [Double](repeating: 6, count: 48),
            windDirection10m: [Int](repeating: 90, count: 48),
            windGusts10m: [Double](repeating: 9, count: 48),
            weatherCode: [Int](repeating: 0, count: 48))
        let data = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 18.3, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: TimeZone.current.identifier, currentDetail: nil)
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.weatherData = data
        vm.selectedDate = today
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return PickerFixture(vm: vm, today: today, tomorrow: tomorrow)
    }

    @MainActor
    private func timeSheet(vm: WeatherViewModel, label: String, date: Date,
                           type: DynamicTypeSize = .large) -> some View {
        TimePickerSheet(
            chart: vm.dayChart, dateLabel: label, placeName: "New York",
            unit: .fahrenheit, initialHour: 12, openedInNowState: false, nowHour: nil,
            isPresented: .constant(true), dateSelection: .constant(date), onDateConfirmed: {})
            .environment(\.dynamicTypeSize, type)
            .background(AppTheme.background)
    }

    // RW-73 DETENT RULING amendment (Nov 6, 2026): at
    // accessibility sizes the picker sheets' bodies are a
    // ScrollView (the ruling's gated form), and a SwiftUI
    // ScrollView photographs as a blank canvas under this
    // harness's ImageRenderer vehicles (evidence: gate run
    // 42282659617's VQ-2.4 max renders). The accessibility-size
    // legs of this harness therefore render the sheet's CONTENT
    // through the sheetContent seam — content wholeness is what
    // VQ-2.4 grades; the gated wrapper is pinned by
    // SheetDisciplineTests, and the scroll motion itself is a
    // device-walkthrough item on the VQ sign-off sheet.
    @MainActor
    private func timeSheetContent(vm: WeatherViewModel, label: String, date: Date,
                                  type: DynamicTypeSize = .large) -> some View {
        TimePickerSheet(
            chart: vm.dayChart, dateLabel: label, placeName: "New York",
            unit: .fahrenheit, initialHour: 12, openedInNowState: false, nowHour: nil,
            isPresented: .constant(true), dateSelection: .constant(date), onDateConfirmed: {})
            .sheetContent(size: type)
            .environment(\.dynamicTypeSize, type)
            .background(AppTheme.background)
    }

    @MainActor
    func testVQ51Picker_DateAxisSeries() throws {
        let f = pickerFixture()
        // Before: the time sheet carries the date entry ("Today ›").
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.1-ios-picker-before-default.png")
        try writeFittingRender(timeSheetContent(vm: f.vm, label: "Today", date: f.today, type: .accessibility5),
                               width: 390, minimumHeight: 700,
                               name: "VQ-2.4-ios-timesheet-F-PARTDAY-max.png")
        // DATE-CHANGED state: tomorrow selected in the draft,
        // standing behind Confirm/Cancel (the RW-66 state).
        let changed = DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
            .background(AppTheme.background)
        try writeRender(changed, size: CGSize(width: 390, height: 620),
                        name: "VQ-5.1-ios-datechanged-F-TOMORROW-default.png")
        // AX5 leg: the sheet's content through the seam (see
        // the timeSheetContent note above).
        let changedMax = DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
            .sheetContent(size: .accessibility5)
            .environment(\.dynamicTypeSize, .accessibility5)
            .background(AppTheme.background)
        try writeFittingRender(changedMax, width: 390, minimumHeight: 760,
                               name: "VQ-2.4-ios-datepicker-F-TOMORROW-max.png")
        // After Confirm: the view model re-solves to the cold day;
        // the picker reopened on it reads "Tomorrow ›".
        let mild = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        f.vm.selectedDate = draft.commit()
        f.vm.updateSelectedTime(date: f.vm.selectedDate, hour: f.vm.selectedHour)
        XCTAssertLessThan(f.vm.currentFeelsLike, mild - 20, "confirm re-solved to the cold day")
        XCTAssertNotNil(f.vm.recommendation)
        print("VQ-HARNESS picker after-confirm: feelsLike \(mild) -> \(f.vm.currentFeelsLike)")
        try writeRender(timeSheet(vm: f.vm, label: "Tomorrow", date: f.tomorrow),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.1-ios-picker-afterconfirm-default.png")
    }

    @MainActor
    func testVQ53Picker_CancelRestores() throws {
        let f = pickerFixture()
        let beforeFeels = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        // Cancel: the draft is discarded; the committed selection
        // is never touched (the RW-66 cancel construction).
        XCTAssertEqual(f.vm.selectedDate, f.today, "cancel leaves the committed date untouched")
        XCTAssertEqual(f.vm.currentFeelsLike, beforeFeels, "cancel leaves the solve untouched")
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.3-ios-picker-aftercancel-default.png")
    }

    // MARK: - §6.3 · Day-chart band at collision-prone hours

    @MainActor
    func testVQ63DayChartBand() throws {
        ManualLocationStore.clear()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today)!
        func stamp(_ date: Date, _ hour: Int) -> String {
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            return String(format: "%04d-%02d-%02dT%02d:00", c.year!, c.month!, c.day!, hour)
        }
        var times: [String] = []
        var temps: [Double] = []
        var apparent: [Double] = []
        for hour in 0..<24 {  // ramp day: cold dawn -> warm afternoon
            let v = -2.0 + Double(hour) * 0.9
            times.append(stamp(today, hour)); temps.append(v); apparent.append(v)
        }
        for hour in 0..<24 {
            times.append(stamp(tomorrow, hour)); temps.append(10); apparent.append(10)
        }
        let hourly = HourlyWeather(
            time: times, temperature2m: temps, apparentTemperature: apparent,
            relativeHumidity2m: [Int](repeating: 55, count: 48),
            precipitationProbability: [Int](repeating: 0, count: 48),
            precipitation: [Double](repeating: 0, count: 48),
            rain: [Double](repeating: 0, count: 48),
            uvIndex: [Double](repeating: 3, count: 48),
            windSpeed10m: [Double](repeating: 6, count: 48),
            windDirection10m: [Int](repeating: 90, count: 48),
            windGusts10m: [Double](repeating: 9, count: 48),
            weatherCode: [Int](repeating: 0, count: 48))
        let data = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 8, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: TimeZone.current.identifier, currentDetail: nil)
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.weatherData = data
        vm.selectedDate = today
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        let chart = try XCTUnwrap(vm.dayChart, "ramp fixture produces a day chart")
        let bandEnd = DayChartBandView(model: chart, selectedHour: 23, nowHour: 12, unit: .fahrenheit)
            .background(AppTheme.background)
        try writeRender(bandEnd, size: CGSize(width: 390, height: 150),
                        name: "VQ-6.3-ios-band-lasthour-default.png")
        let bandMid = DayChartBandView(model: chart, selectedHour: 15, nowHour: 12, unit: .fahrenheit)
            .background(AppTheme.background)
        try writeRender(bandMid, size: CGSize(width: 390, height: 150),
                        name: "VQ-6.3-ios-band-changetick-default.png")
    }

    // MARK: - §1.7 / §2.10 · Mini-bar narration states

    private func collectionEntry(place: String) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_798_000_000),
            placeName: place, feelsLikeF: 57, actualF: 61, condition: "Clear sky",
            itemNames: ["Short-sleeve shirt", "Running shorts"],
            itemKeys: ["SHORT_SLEEVE", "SHORTS"],
            heroImageReference: "", windMph: 6, dayLowF: 55, dayHighF: 63,
            faceAnchor: nil)
    }

    @MainActor
    func testVQ17MiniBarNarration() throws {
        let miami = WearIveRunMiniBarView(
            state: .added(place: "Miami, FL", moment: .now),
            entry: collectionEntry(place: "Miami, FL"),
            formattedFeelsLike: "82°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        try writeRender(miami, size: CGSize(width: 390, height: 110),
                        name: "VQ-1.7-ios-minibar-added-default.png")
        let longPlace = WearIveRunMiniBarView(
            state: .updated(payload: .place("Truth or Consequences, New Mexico")),
            entry: collectionEntry(place: "Truth or Consequences, New Mexico"),
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .environment(\.dynamicTypeSize, .accessibility5)
            .background(AppTheme.background)
        try writeRender(longPlace, size: CGSize(width: 390, height: 150),
                        name: "VQ-2.10-ios-minibar-F-LONGPLACE-max.png")
    }

    // MARK: - §7.4 · Unified weather detail sheet

    @MainActor
    func testVQ74WeatherDetailSheet() throws {
        let all: [WeatherDetailType] = [
            .feelsLike(value: "72°"), .actual(value: "75°"), .condition(.clear),
            .wind(speed: 11, direction: "NW", gusts: 18),
            .humidity(percentage: 54), .precipitation(percentage: 40), .uvIndex(value: 3),
        ]
        let sheet = WeatherDetailSheet(
            detailType: .feelsLike(value: "72°"), allDetails: all,
            contextLines: ["New York, NY", "Today", "Feels like 72°"],
            isPresented: .constant(true))
            .background(AppTheme.background)
        try writeRender(sheet, size: CGSize(width: 390, height: 700),
                        name: "VQ-7.4-ios-weatherdetail-default.png")
    }

    // MARK: - Hosted-window snapshots (glass pill + self-measuring bar)
    //
    // The offscreen ImageRenderer drops the hero pill entirely:
    // the pill's iOS 26 glass chrome (glassPill) does not
    // composite without a window, and every pill state rendered
    // byte-identical empty gradients (run 42147399785). The mini
    // bar likewise measures its own heights through appear/layout
    // cycles a static snapshot never runs, and rendered blank.
    // Both surfaces are therefore re-rendered hosted in a real
    // window with layout + a run-loop settle, then captured via
    // drawHierarchy — the device compositing path.

    @MainActor
    private func writeHostedRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let host = UIHostingController(rootView: content.frame(width: size.width, height: size.height))
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.frame = CGRect(origin: .zero, size: size)
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        let deadline = Date().addingTimeInterval(0.8)
        while Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        }
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 2
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank (hosted)")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("VQ-HARNESS wrote \(name) (hosted)")
        window.isHidden = true
    }

    @MainActor
    func testVQ1PillStates_Hosted() throws {
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .makingWithUpsell, heightMode: .fill, showsTopControls: false),
            size: CGSize(width: 390, height: 520), name: "VQ-1.1-ios-F-FREE-0-default.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .making, heightMode: .fill, showsTopControls: false),
            size: CGSize(width: 390, height: 520), name: "VQ-1.2-ios-F-PREM-GEN-default.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .freeLook(used: 3, allowance: 5), heightMode: .fill, showsTopControls: false),
            size: CGSize(width: 390, height: 520), name: "VQ-1.3-ios-F-FREE-3-default.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .exhausted(used: 5, allowance: 5), heightMode: .fill, showsTopControls: false),
            size: CGSize(width: 390, height: 520), name: "VQ-1.4-ios-F-FREE-5-default.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .premium, heightMode: .fill, showsTopControls: false),
            size: CGSize(width: 390, height: 520), name: "VQ-1.5-ios-F-PREM-BANK-default.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .makingWithUpsell, heightMode: .fill, showsTopControls: false)
                .preferredColorScheme(.dark),
            size: CGSize(width: 390, height: 520), name: "VQ-1.10-ios-F-FREE-0-dark.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .freeLook(used: 3, allowance: 5), heightMode: .fill, showsTopControls: false)
                .preferredColorScheme(.dark),
            size: CGSize(width: 390, height: 520), name: "VQ-1.10-ios-F-FREE-3-dark.png")
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .exhausted(used: 5, allowance: 5), heightMode: .fill, showsTopControls: false)
                .preferredColorScheme(.dark),
            size: CGSize(width: 390, height: 520), name: "VQ-1.10-ios-F-FREE-5-dark.png")
    }

    @MainActor
    func testVQ17MiniBarNarration_Hosted() throws {
        let miami = WearIveRunMiniBarView(
            state: .added(place: "Miami, FL", moment: .now),
            entry: collectionEntry(place: "Miami, FL"),
            formattedFeelsLike: "82°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        try writeHostedRender(miami, size: CGSize(width: 390, height: 110),
                              name: "VQ-1.7-ios-minibar-added-default.png")
        let longPlace = WearIveRunMiniBarView(
            state: .updated(payload: .place("Truth or Consequences, New Mexico")),
            entry: collectionEntry(place: "Truth or Consequences, New Mexico"),
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .environment(\.dynamicTypeSize, .accessibility5)
            .background(AppTheme.background)
        try writeHostedRender(longPlace, size: CGSize(width: 390, height: 150),
                              name: "VQ-2.10-ios-minibar-F-LONGPLACE-max.png")
    }

    @MainActor
    func testVQ74WeatherDetailSheet_Hosted() throws {
        // The sheet's split-view composition does not rasterise
        // under the offscreen ImageRenderer (run 42147399785: a
        // placeholder bar on a cream field) — re-render hosted.
        let all: [WeatherDetailType] = [
            .feelsLike(value: "72°"), .actual(value: "75°"), .condition(.clear),
            .wind(speed: 11, direction: "NW", gusts: 18),
            .humidity(percentage: 54), .precipitation(percentage: 40), .uvIndex(value: 3),
        ]
        let sheet = WeatherDetailSheet(
            detailType: .feelsLike(value: "72°"), allDetails: all,
            contextLines: ["New York, NY", "Today", "Feels like 72°"],
            isPresented: .constant(true))
            .background(AppTheme.background)
        try writeHostedRender(sheet, size: CGSize(width: 390, height: 700),
                              name: "VQ-7.4-ios-weatherdetail-default.png")
    }

    // MARK: - GEN-6 VQ FIX WAVE · RW-71/72/73/74 after-renders
    //
    // The fix lane's proof renders for the four iOS FAILs of the
    // gen-6 VQ run (VQ-1.1, VQ-1.4, VQ-2.4, VQ-2.9). Same
    // fixtures, same production views, same vehicles as the
    // executor's renders above; file names carry the `-fixed`
    // suffix. The picker sheets at accessibility sizes render
    // at the composition's FITTING height (the VQ-2.1 hero
    // numeral method in this same file: the canvas never clips
    // by harness choice) — at those sizes the sheets' fixed,
    // scroll-free content (SheetDisciplineTests pins no scroll
    // consumer in either picker) is legitimately taller than
    // the executor's 700/760 pt frames, and width — the device
    // width, 390 pt — is the invariant that grades.

    /// Render at the height the composition needs at `width`
    /// (probed with an unspecified-height proposal), never
    /// below `minimumHeight`.
    @MainActor
    private func writeFittingRender<Content: View>(
        _ content: Content, width: CGFloat, minimumHeight: CGFloat, name: String
    ) throws {
        let probe = ImageRenderer(content: content)
        probe.scale = 2
        probe.proposedSize = ProposedViewSize(width: width, height: nil)
        let needed = probe.uiImage?.size.height ?? minimumHeight
        let height = max(ceil(needed), minimumHeight)
        try writeRender(content, size: CGSize(width: width, height: height), name: name)
        print("VQ-HARNESS fitting render \(name): height \(height) (probed \(needed))")
    }

    @MainActor
    func testVQFix_PillStates_Hosted() throws {
        // The -fixed counterparts of testVQ1PillStates_Hosted,
        // through the same writeHostedRender vehicle. Hosted
        // capture in the parallel CI clones is a lottery (a
        // window that has not composited snapshots pure black;
        // the same failure hits testVQ1PillStates_Hosted itself
        // on the unmodified base branch), so this test rides
        // that lottery exactly like the harness's own test —
        // an earlier retry variant (fresh window per attempt)
        // made no difference across two runs and was removed.
        func pill(_ state: HeroPillState, _ name: String) throws {
            try writeHostedRender(
                HeroImageView(
                    locationName: "New York, NY", temperature: 72, actualTemperature: 75,
                    weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                    tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
                    fallbackImageUrl: "", aiImageUrl: nil,
                    pillState: state, heightMode: .fill, showsTopControls: false),
                size: CGSize(width: 390, height: 520), name: name)
        }
        try pill(.makingWithUpsell, "VQ-1.1-ios-F-FREE-0-default-fixed.png")
        try pill(.making, "VQ-1.2-ios-F-PREM-GEN-default-fixed.png")
        try pill(.freeLook(used: 3, allowance: 5), "VQ-1.3-ios-F-FREE-3-default-fixed.png")
        try pill(.exhausted(used: 5, allowance: 5), "VQ-1.4-ios-F-FREE-5-default-fixed.png")
        try pill(.premium, "VQ-1.5-ios-F-PREM-BANK-default-fixed.png")
    }

    @MainActor
    func testVQFix_Widget_PlanFixtures_LargestType() throws {
        let now = Date(timeIntervalSince1970: 1_798_000_000)
        let hot = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                     record: widgetRecord(feelsLikeF: 108, place: "Phoenix, AZ", savedAt: now),
                                     errorCategory: nil)
        let cold = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                      record: widgetRecord(feelsLikeF: -4, place: "Duluth, MN", savedAt: now),
                                      errorCategory: nil)
        try renderWidget(entry: hot, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-HOT108-max-fixed.png")
        try renderWidget(entry: hot, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-HOT108-max-fixed.png")
        try renderWidget(entry: hot, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-HOT108-max-fixed.png")
        try renderWidget(entry: cold, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-COLDNEG-max-fixed.png")
        try renderWidget(entry: cold, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-COLDNEG-max-fixed.png")
        try renderWidget(entry: cold, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-COLDNEG-max-fixed.png")
    }

    @MainActor
    func testVQFix_PickerSheets_TypeScales() throws {
        let f = pickerFixture()
        // Time sheet: default renders on the executor's own
        // canvas (390×560, the VQ-5.1 before frame); the
        // accessibility sizes render at fitting height.
        // RW-73 DETENT RULING amendment (Nov 6, 2026): the
        // accessibility legs render the sheet's CONTENT through
        // the sheetContent seam — at those sizes the body is
        // the ruling's gated ScrollView, which photographs
        // blank under ImageRenderer (see the timeSheetContent
        // note). Content wholeness at each size remains the
        // assertion; the wrapper is pin-proven and the scroll
        // motion is a device item.
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-2.4-ios-timesheet-F-PARTDAY-default-fixed.png")
        try writeFittingRender(timeSheetContent(vm: f.vm, label: "Today", date: f.today, type: .accessibility2),
                               width: 390, minimumHeight: 700,
                               name: "VQ-2.4-ios-timesheet-F-PARTDAY-mid-fixed.png")
        try writeFittingRender(timeSheetContent(vm: f.vm, label: "Today", date: f.today, type: .accessibility5),
                               width: 390, minimumHeight: 700,
                               name: "VQ-2.4-ios-timesheet-F-PARTDAY-max-fixed.png")
        // Date sheet (tomorrow selected in the draft, the
        // VQ-2.4 fixture state): default on the executor's
        // canvas (390×620, the VQ-5.1 date-changed frame);
        // accessibility sizes at fitting height, content
        // through the seam per the amendment above.
        func dateSheet(_ type: DynamicTypeSize) -> some View {
            DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
                .environment(\.dynamicTypeSize, type)
                .background(AppTheme.background)
        }
        func dateSheetContent(_ type: DynamicTypeSize) -> some View {
            DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
                .sheetContent(size: type)
                .environment(\.dynamicTypeSize, type)
                .background(AppTheme.background)
        }
        try writeRender(dateSheet(.large),
                        size: CGSize(width: 390, height: 620),
                        name: "VQ-2.4-ios-datepicker-F-TOMORROW-default-fixed.png")
        try writeFittingRender(dateSheetContent(.accessibility2),
                               width: 390, minimumHeight: 760,
                               name: "VQ-2.4-ios-datepicker-F-TOMORROW-mid-fixed.png")
        try writeFittingRender(dateSheetContent(.accessibility5),
                               width: 390, minimumHeight: 760,
                               name: "VQ-2.4-ios-datepicker-F-TOMORROW-max-fixed.png")
    }

    // MARK: - RW-81 · Fit control in its three states (Nov 6, 2026)
    //
    // Owner ruling RW-81: the fit control carries exactly two
    // rows (Men's / Women's); neutral is the stored .unisex
    // state and renders as NOTHING selected. The renders below
    // are the PRODUCTION shared control — the one view both
    // onboarding (342 pt content width: the 390 pt phone minus
    // its 24 pt insets) and Settings present — seated in each of
    // its three states at default Dynamic Type, through this
    // harness's ImageRenderer vehicle (not the hosted-capture
    // lottery). The watch surface carries no render here: its
    // fit buttons are pinned by source + the model transition
    // tests (SettingsInventoryTests / Gen2I3OnboardingTests).

    /// Stateful seat for the production fit control — the
    /// control takes a binding, so the fixture owns the value
    /// exactly the way onboarding and Settings each own theirs.
    private struct FitControlFixture: View {
        @State private var preference: GenderPreference

        init(_ preference: GenderPreference) {
            _preference = State(initialValue: preference)
        }

        var body: some View {
            FitPreferenceInlinePicker(preference: $preference)
                .environment(\.dynamicTypeSize, .large)
                .background(AppTheme.background)
        }
    }

    @MainActor
    func testRW81FitControl_ThreeStates() throws {
        try writeFittingRender(FitControlFixture(.male), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-mens-default.png")
        try writeFittingRender(FitControlFixture(.female), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-womens-default.png")
        try writeFittingRender(FitControlFixture(.unisex), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-neutral-default.png")
    }

    // MARK: - §10.4 · Caption system, transcribed from the production composer

    func testVQ104CaptionsTranscription() throws {
        let input = ShareCaptionInput(
            place: "Kansas City, MO", captionMoment: "this morning", isPastChapter: false,
            feels: 57, actual: 61, low: 55, high: 63, swingF: 8, feelsF: 57,
            conditionLower: "clear sky",
            kitNames: ["Short-sleeve shirt", "Running shorts", "Sunglasses"])
        var out = "VQ-10.4 caption transcription — production ShareCaptionComposer, Kansas City fixture\n"
        out += "Picker order: \(ShareCaptionComposer.pickerOrder(for: input).map(\.rawValue).joined(separator: ", "))\n"
        for template in ShareCaptionTemplate.allCases {
            out += "\n--- \(template.rawValue) ---\n"
            out += ShareCaptionComposer.caption(for: template, input: input) + "\n"
        }
        try writeText(out, name: "VQ-10.4-ios-captions.txt")
        print(out)
    }
}

// MARK: - GEN-6 FIX WAVE (Nov 6, 2026) · RW-85…RW-105 after-renders
//
// The fix lane's proof renders: same vehicles and fitting method
// as the executor's renders above; file names carry the FIX-RW<nn>
// prefix. Before-evidence for these items is Sol's device
// walkthrough frames (rw-build28-walkthrough-frames) and his two
// annotated screenshots — the renders below are the AFTER state
// of the exact surfaces on the candidate build.

extension Gen6VisualQAHarnessTests {

    // RW-86 + RW-88: the slim (MINI) bar, hosted — the state Sol
    // reported as a solid blank bar on device. Post-fix the bar
    // is a plain header at natural height (no self-measurement
    // in the paint path), so the hosted vehicle composes it like
    // any other content: wordmark, narration, controls.
    @MainActor
    func testFixWave_MinibarMini_Hosted() throws {
        let entry = collectionEntry(place: "New York, NY")
        let mini = WearIveRunMiniBarView(
            state: .added(place: "New York, NY", moment: .now),
            entry: entry,
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        try writeHostedRender(mini, size: CGSize(width: 390, height: 110),
                              name: "FIX-RW86-ios-minibar-mini-light.png")
        let miniDark = WearIveRunMiniBarView(
            state: .added(place: "New York, NY", moment: .now),
            entry: entry,
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .preferredColorScheme(.dark)
            .background(AppTheme.background)
        try writeHostedRender(miniDark, size: CGSize(width: 390, height: 110),
                              name: "FIX-RW86-ios-minibar-mini-dark.png")
        // RW-88: the viewed-state line in the slim bar — a place
        // change and a picked future moment.
        let viewedPlace = WearIveRunMiniBarView(
            state: .added(place: "New York, NY", moment: .now),
            entry: entry,
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            viewedNarrationPayload: "Miami, FL, Now",
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        try writeHostedRender(viewedPlace, size: CGSize(width: 390, height: 110),
                              name: "FIX-RW88-ios-minibar-updated-place.png")
        let viewedMoment = WearIveRunMiniBarView(
            state: .added(place: "New York, NY", moment: .now),
            entry: entry,
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            viewedNarrationPayload: "November 12 7 p.m.",
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        try writeHostedRender(viewedMoment, size: CGSize(width: 390, height: 110),
                              name: "FIX-RW88-ios-minibar-updated-moment.png")
    }

    // RW-87: the time sheet's content height is IDENTICAL for a
    // one-line and a two-line outfit preview (the reserve), at
    // the default type size; both states also render at AX5
    // (where the sheet's own scroll absorbs any growth past the
    // reserve — RW-73's ruling).
    @MainActor
    func testFixWave_TimeSheetReserve() throws {
        func sheet(hour: Int) -> TimePickerSheet {
            let chart = DayChartModel.build(points: (0..<24).map { h in
                DayChartHour(
                    hour: h,
                    feelsLikeF: 55 + Double(h),
                    bracket: .mild,
                    outfitItemNames: h == 19
                        ? ["Long-Sleeve Merino Wool Running Shirt", "Insulated Running Tights", "Windproof Running Jacket", "Running Gloves"]
                        : ["Tee"])
            })!
            return TimePickerSheet(
                chart: chart, dateLabel: "Today", placeName: "New York, NY",
                unit: .fahrenheit, initialHour: hour, openedInNowState: false,
                nowHour: nil, isPresented: .constant(true))
        }
        func fittingHeight<Content: View>(_ content: Content) -> CGFloat {
            let probe = ImageRenderer(content: content)
            probe.scale = 2
            probe.proposedSize = ProposedViewSize(width: 390, height: nil)
            return probe.uiImage?.size.height ?? -1
        }
        let oneLine = sheet(hour: 7).sheetContent(size: .large)
        let twoLine = sheet(hour: 19).sheetContent(size: .large)
        XCTAssertEqual(fittingHeight(oneLine), fittingHeight(twoLine),
                       "RW-87: the sheet must not jump between one-line and two-line outfit previews")
        try writeRender(oneLine, size: CGSize(width: 390, height: fittingHeight(oneLine)),
                        name: "FIX-RW87-ios-timesheet-oneline-default.png")
        try writeRender(twoLine, size: CGSize(width: 390, height: fittingHeight(twoLine)),
                        name: "FIX-RW87-ios-timesheet-twoline-default.png")
        try writeRender(sheet(hour: 19).sheetContent(size: .accessibility5),
                        size: CGSize(width: 390, height: fittingHeight(sheet(hour: 19).sheetContent(size: .accessibility5))),
                        name: "FIX-RW87-ios-timesheet-twoline-max.png")
    }

    // RW-85: the collision fixture — selected hour == now hour
    // == the day's warmest hour, at the sheet height and the
    // accessibility height. The dot must sit clear below the
    // NOW label (geometry pinned in Gen6FixWaveTests).
    @MainActor
    func testFixWave_DayChartNowCollision() throws {
        let chart = DayChartModel.build(points: (0..<24).map { h in
            DayChartHour(
                hour: h,
                feelsLikeF: h == 14 ? 80 : 50 + Double(abs(h - 6)),
                bracket: .mild,
                outfitItemNames: ["Tee"])
        })!
        for (height, suffix) in [(168, "sheet"), (120, "ax")] {
            let view = DayChartView(
                model: chart, selectedHour: 14, nowHour: 14,
                interactive: false, showsAxisLabels: true)
                .frame(width: 358, height: CGFloat(height))
                .background(AppTheme.background)
            try writeRender(view, size: CGSize(width: 358, height: height),
                            name: "FIX-RW85-ios-daychart-nowcollision-\(suffix).png")
        }
    }

    // RW-105 after-renders: the bracket badge (deepened chip,
    // full opacity) in both themes, and the free-look pill's
    // "Get unlimited" in the dynamic pine (hosted, light mode —
    // the mode Sol circled).
    @MainActor
    func testFixWave_PillGreen() throws {
        try writeHostedRender(
            HeroImageView(
                locationName: "New York, NY", temperature: 64, actualTemperature: 62,
                weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
                tempBracket: HeroTempBracket.from(feelsLikeTemperature: 64),
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .freeLook(used: 1, allowance: 5), heightMode: .fill, showsTopControls: false)
                .preferredColorScheme(.light),
            size: CGSize(width: 390, height: 520), name: "FIX-RW105-ios-pill-freelook-light.png")
    }

}
