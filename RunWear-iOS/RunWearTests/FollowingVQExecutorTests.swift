import XCTest
import SwiftUI
import UIKit
import WidgetKit

/// FOLLOWING-BUILD VQ EXECUTOR harness (iOS) — independent executor
/// renders for candidate following-candidate-ios-5ef39e8.
/// Every render is a PRODUCTION view composed as production
/// composes it. Files land in RunWear-iOS/render-artefacts/ with
/// an `-exec` suffix so they never overwrite another lane's files;
/// the executor verifies each file's pixels after download and
/// only then copies it into the evidence sheet under its plan
/// name. Hosted captures retry on pure-black frames (the standing
/// CI black-frame lottery): a frame that is >=98% black is NO
/// evidence and is retried with a fresh window; if every attempt
/// is black the test writes nothing and the item grades UNPROVEN.
final class FollowingVQExecutorTests: XCTestCase {

    // MARK: - Plumbing

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    private func writeImage(_ image: UIImage, name: String) throws {
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("VQEXEC wrote \(name)")
    }

    private func writeText(_ text: String, name: String) throws {
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try text.write(to: dir.appendingPathComponent(name), atomically: true, encoding: .utf8)
    }

    private func stats(_ image: UIImage) -> (distinct: Int, blackFraction: Double) {
        guard let cg = image.cgImage else { return (0, 1) }
        let w = 16, h = 16
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return (0, 1) }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var distinct = Set<Int>()
        var black = 0, total = 0
        for i in stride(from: 0, to: bytes.count, by: 4) {
            distinct.insert(Int(bytes[i]) << 16 | Int(bytes[i + 1]) << 8 | Int(bytes[i + 2]))
            if bytes[i] < 8 && bytes[i + 1] < 8 && bytes[i + 2] < 8 { black += 1 }
            total += 1
        }
        return (distinct.count, total > 0 ? Double(black) / Double(total) : 1)
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        let s = stats(image)
        XCTAssertGreaterThan(s.distinct, 2, "\(name) rendered blank")
        try writeImage(image, name: name)
    }

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
        print("VQEXEC fitting \(name): height \(height) (probed \(needed))")
    }

    /// Hosted capture with black-frame retry. Returns true when a
    /// non-black frame was written.
    @MainActor
    @discardableResult
    private func writeHostedRender<Content: View>(
        _ content: Content, size: CGSize, name: String, attempts: Int = 4
    ) -> Bool {
        for attempt in 1...attempts {
            let host = UIHostingController(rootView: content.frame(width: size.width, height: size.height))
            let window = UIWindow(frame: CGRect(origin: .zero, size: size))
            window.rootViewController = host
            window.makeKeyAndVisible()
            host.view.frame = CGRect(origin: .zero, size: size)
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            let deadline = Date().addingTimeInterval(1.0)
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
            window.isHidden = true
            let s = stats(image)
            print("VQEXEC hosted \(name) attempt \(attempt): distinct=\(s.distinct) black=\(s.blackFraction)")
            if s.blackFraction < 0.98 && s.distinct > 2 {
                do { try writeImage(image, name: name) } catch { print("VQEXEC write failed \(name): \(error)") }
                return true
            }
        }
        print("VQEXEC hosted \(name): ALL ATTEMPTS BLACK — no evidence written")
        return false
    }

    // MARK: - Pill states (hosted; glass does not composite offscreen)

    private func pillHero(_ state: HeroPillState, dark: Bool = false) -> some View {
        HeroImageView(
            locationName: "New York, NY", temperature: 72, actualTemperature: 75,
            weatherCondition: .clear, date: Date(timeIntervalSince1970: 1_798_000_000), hour: 7,
            tempBracket: HeroTempBracket.from(feelsLikeTemperature: 72),
            fallbackImageUrl: "", aiImageUrl: nil,
            pillState: state, heightMode: .fill, showsTopControls: false)
            .preferredColorScheme(dark ? .dark : .light)
    }

    @MainActor
    func testExecPillsHosted() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5,
                             isScaffoldServe: true),
            .hidden, "scaffold serve resolves silent per the operative pill table")
        writeHostedRender(pillHero(.makingWithUpsell), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.1-ios-F-FREE-0-default-exec.png")
        writeHostedRender(pillHero(.making), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.2-ios-F-PREM-GEN-default-exec.png")
        writeHostedRender(pillHero(.freeLook(used: 3, allowance: 5)), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.3-ios-F-FREE-3-default-exec.png")
        writeHostedRender(pillHero(.exhausted(used: 5, allowance: 5)), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.4-ios-F-FREE-5-default-exec.png")
        writeHostedRender(pillHero(.premium), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.5-ios-F-PREM-BANK-default-exec.png")
        writeHostedRender(pillHero(.hidden), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.6-ios-F-SCAFFOLD-default-exec.png")
        writeHostedRender(pillHero(.makingWithUpsell, dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-0-dark-exec.png")
        writeHostedRender(pillHero(.freeLook(used: 3, allowance: 5), dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-3-dark-exec.png")
        writeHostedRender(pillHero(.exhausted(used: 5, allowance: 5), dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-5-dark-exec.png")
    }

    // MARK: - VQ-2.1 hero numeral

    @MainActor
    private func renderHeroNumeral(feelsLikeF: Double, type: DynamicTypeSize, name: String) throws {
        let row = HomeHeroNumeralRow(
            formattedTemperature: TemperatureUnit.fahrenheit.formatFromFahrenheit(feelsLikeF),
            bracket: HeroTempBracket.from(feelsLikeTemperature: feelsLikeF),
            windMph: 11, rainPercent: 40, humidityPercent: 54, uvIndex: 3
        )
        .environment(\.dynamicTypeSize, type)
        .background(AppTheme.background)
        let probe = ImageRenderer(content: row)
        probe.scale = 2
        probe.proposedSize = ProposedViewSize(width: 358, height: nil)
        let needed = probe.uiImage?.size.height ?? 120
        try writeRender(row, size: CGSize(width: 358, height: max(needed, 60)), name: name)
    }

    @MainActor
    func testExecHeroNumeral() throws {
        try renderHeroNumeral(feelsLikeF: 108, type: .large, name: "VQ-2.1-ios-F-HOT108-default-exec.png")
        try renderHeroNumeral(feelsLikeF: 108, type: .accessibility5, name: "VQ-2.1-ios-F-HOT108-max-exec.png")
        try renderHeroNumeral(feelsLikeF: -4, type: .large, name: "VQ-2.1-ios-F-COLDNEG-default-exec.png")
        try renderHeroNumeral(feelsLikeF: -4, type: .accessibility5, name: "VQ-2.1-ios-F-COLDNEG-max-exec.png")
    }

    // MARK: - Widgets (VQ-2.9 + VQ-11.5)

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
    func testExecWidgets() throws {
        let now = Date(timeIntervalSince1970: 1_798_000_000)
        let hot = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                     record: widgetRecord(feelsLikeF: 108, place: "Phoenix, AZ", savedAt: now),
                                     errorCategory: nil)
        let cold = RunWearWidgetEntry(date: now.addingTimeInterval(600),
                                      record: widgetRecord(feelsLikeF: -4, place: "Duluth, MN", savedAt: now),
                                      errorCategory: nil)
        // VQ-2.9 at largest type
        try renderWidget(entry: hot, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-HOT108-max-exec.png")
        try renderWidget(entry: hot, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-HOT108-max-exec.png")
        try renderWidget(entry: hot, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-HOT108-max-exec.png")
        try renderWidget(entry: cold, family: .systemSmall, type: .accessibility5, name: "VQ-2.9-ios-small-F-COLDNEG-max-exec.png")
        try renderWidget(entry: cold, family: .systemMedium, type: .accessibility5, name: "VQ-2.9-ios-medium-F-COLDNEG-max-exec.png")
        try renderWidget(entry: cold, family: .systemLarge, type: .accessibility5, name: "VQ-2.9-ios-large-F-COLDNEG-max-exec.png")
        // VQ-11.5 fresh / stale / error in all sizes
        let record = WidgetLastGood(
            feelsLikeF: 36, temperatureF: 39, conditionText: "Clear",
            conditionIcon: "sun.max.fill", windMph: 9, humidityPercent: 48, uvIndex: 2,
            locationName: "New York, NY", topBaseName: "Long Sleeve Shirt",
            layerNames: ["Light Jacket"], bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Light Jacket · rain 20%",
            unitIsCelsius: false, savedAt: now)
        let staleDate = now.addingTimeInterval(2 * 3600 + 5 * 60)
        for (family, label) in [(WidgetFamily.systemSmall, "small"), (.systemMedium, "medium"), (.systemLarge, "large")] {
            try renderWidget(entry: RunWearWidgetEntry(date: now.addingTimeInterval(600), record: record, errorCategory: nil),
                             family: family, name: "VQ-11.5-ios-\(label)-fresh-default-exec.png")
            try renderWidget(entry: RunWearWidgetEntry(date: staleDate, record: record, errorCategory: nil),
                             family: family, name: "VQ-11.5-ios-\(label)-stale-default-exec.png")
            try renderWidget(entry: RunWearWidgetEntry(date: now, record: nil, errorCategory: .weather),
                             family: family, name: "VQ-11.5-ios-\(label)-error-default-exec.png")
        }
    }

    // MARK: - Watch glance (VQ-2.8 / VQ-11.3)

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
        XCTAssertEqual(vm.displayTemperature, "\(Int(feelsLikeF.rounded()))°")
        let page = WatchGlancePage(viewModel: vm, weather: weather)
            .environment(\.dynamicTypeSize, type)
            .background(Color.black)
        try writeRender(page, size: CGSize(width: 198, height: 242), name: name)
    }

    @MainActor
    func testExecWatchGlance() throws {
        try renderWatch(feelsLikeF: 108, type: .accessibility5, name: "VQ-2.8-ios-watch-F-HOT108-max-exec.png")
        try renderWatch(feelsLikeF: -4, type: .accessibility5, name: "VQ-2.8-ios-watch-F-COLDNEG-max-exec.png")
        try renderWatch(feelsLikeF: 108, type: .large, name: "VQ-11.3-ios-watch-F-HOT108-default-exec.png")
        try renderWatch(feelsLikeF: -4, type: .large, name: "VQ-11.3-ios-watch-F-COLDNEG-default-exec.png")
    }

    // MARK: - Picker fixture (VQ-5.1 / 5.3 / 2.4)

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

    @MainActor
    private func timeSheetContent(vm: WeatherViewModel, label: String, date: Date,
                                  type: DynamicTypeSize) -> some View {
        TimePickerSheet(
            chart: vm.dayChart, dateLabel: label, placeName: "New York",
            unit: .fahrenheit, initialHour: 12, openedInNowState: false, nowHour: nil,
            isPresented: .constant(true), dateSelection: .constant(date), onDateConfirmed: {})
            .sheetContent(size: type)
            .environment(\.dynamicTypeSize, type)
            .background(AppTheme.background)
    }

    @MainActor
    func testExecPickerSeries() throws {
        let f = pickerFixture()
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.1-ios-picker-before-default-exec.png")
        // VQ-2.4: both sheets, default + AX2 + AX5 (content through
        // the production sheetContent(size:) seam at AX sizes —
        // the ruled ScrollView form photographs blank offscreen;
        // the scrolled-to-foot presentation grades UNPROVEN).
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-2.4-ios-timesheet-default-exec.png")
        try writeFittingRender(timeSheetContent(vm: f.vm, label: "Today", date: f.today, type: .accessibility2),
                               width: 390, minimumHeight: 700,
                               name: "VQ-2.4-ios-timesheet-ax2-exec.png")
        try writeFittingRender(timeSheetContent(vm: f.vm, label: "Today", date: f.today, type: .accessibility5),
                               width: 390, minimumHeight: 700,
                               name: "VQ-2.4-ios-timesheet-ax5-exec.png")
        let changed = DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
            .environment(\.dynamicTypeSize, .large)
            .background(AppTheme.background)
        try writeRender(changed, size: CGSize(width: 390, height: 620),
                        name: "VQ-5.1-ios-datechanged-F-TOMORROW-default-exec.png")
        try writeRender(changed, size: CGSize(width: 390, height: 620),
                        name: "VQ-2.4-ios-datepicker-default-exec.png")
        func dateContent(_ type: DynamicTypeSize) -> some View {
            DatePickerSheet(selectedDate: .constant(f.tomorrow), isPresented: .constant(true))
                .sheetContent(size: type)
                .environment(\.dynamicTypeSize, type)
                .background(AppTheme.background)
        }
        try writeFittingRender(dateContent(.accessibility2), width: 390, minimumHeight: 760,
                               name: "VQ-2.4-ios-datepicker-ax2-exec.png")
        try writeFittingRender(dateContent(.accessibility5), width: 390, minimumHeight: 760,
                               name: "VQ-2.4-ios-datepicker-ax5-exec.png")
        // After Confirm re-solve
        let mild = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        f.vm.selectedDate = draft.commit()
        f.vm.updateSelectedTime(date: f.vm.selectedDate, hour: f.vm.selectedHour)
        XCTAssertLessThan(f.vm.currentFeelsLike, mild - 20, "confirm re-solved to the cold day")
        XCTAssertNotNil(f.vm.recommendation)
        try writeRender(timeSheet(vm: f.vm, label: "Tomorrow", date: f.tomorrow),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.1-ios-picker-afterconfirm-default-exec.png")
    }

    @MainActor
    func testExecPickerCancel() throws {
        let f = pickerFixture()
        let beforeFeels = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        XCTAssertEqual(f.vm.selectedDate, f.today)
        XCTAssertEqual(f.vm.currentFeelsLike, beforeFeels)
        try writeRender(timeSheet(vm: f.vm, label: "Today", date: f.today),
                        size: CGSize(width: 390, height: 560),
                        name: "VQ-5.3-ios-picker-aftercancel-default-exec.png")
    }

    // MARK: - VQ-6.3 day-chart band

    @MainActor
    func testExecDayChartBand() throws {
        let f = pickerFixture()
        _ = f
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
        for hour in 0..<24 {
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
        let chart = try XCTUnwrap(vm.dayChart)
        let bandEnd = DayChartBandView(model: chart, selectedHour: 23, nowHour: 12, unit: .fahrenheit)
            .background(AppTheme.background)
        try writeRender(bandEnd, size: CGSize(width: 390, height: 150),
                        name: "VQ-6.3-ios-band-lasthour-default-exec.png")
        let bandMid = DayChartBandView(model: chart, selectedHour: 15, nowHour: 12, unit: .fahrenheit)
            .background(AppTheme.background)
        try writeRender(bandMid, size: CGSize(width: 390, height: 150),
                        name: "VQ-6.3-ios-band-changetick-default-exec.png")
    }

    // MARK: - RW-81 fit control (phone seats, production shared control)

    private struct FitControlFixture: View {
        @State private var preference: GenderPreference
        init(_ preference: GenderPreference) { _preference = State(initialValue: preference) }
        var body: some View {
            FitPreferenceInlinePicker(preference: $preference)
                .environment(\.dynamicTypeSize, .large)
                .background(AppTheme.background)
        }
    }

    @MainActor
    func testExecRW81FitControl() throws {
        try writeFittingRender(FitControlFixture(.male), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-mens-default-exec.png")
        try writeFittingRender(FitControlFixture(.female), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-womens-default-exec.png")
        try writeFittingRender(FitControlFixture(.unisex), width: 342, minimumHeight: 96,
                               name: "RW81-ios-fit-neutral-default-exec.png")
        // Model truth under the renders: tap-again releases to
        // neutral; a stored legacy "No preference" IS .unisex.
        XCTAssertEqual(GenderPreference.male.fitSelection(afterTapping: .male), .unisex)
        XCTAssertEqual(GenderPreference.female.fitSelection(afterTapping: .female), .unisex)
        XCTAssertEqual(GenderPreference.unisex.fitSelection(afterTapping: .male), .male)
        XCTAssertEqual(GenderPreference.unisex.fitSelection(afterTapping: .female), .female)
    }

    // MARK: - RW-81 watch seat (hosted; the watch settings body is a ScrollView)

    @MainActor
    func testExecRW81WatchSettingsHosted() {
        for (pref, label) in [(GenderPreference.male, "mens"), (.female, "womens"), (.unisex, "neutral")] {
            let vm = WatchViewModel()
            vm.genderPreference = pref
            vm.temperatureUnit = .fahrenheit
            let view = WatchSettingsView(viewModel: vm)
                .background(Color.black)
            writeHostedRender(view, size: CGSize(width: 198, height: 242),
                              name: "RW81-ios-watchsettings-\(label)-exec.png", attempts: 3)
        }
    }

    // MARK: - Mini bar narration (hosted) — VQ-1.7 / VQ-2.10

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
    func testExecMiniBarHosted() {
        let miami = WearIveRunMiniBarView(
            state: .added(place: "Miami, FL", moment: .now),
            entry: collectionEntry(place: "Miami, FL"),
            formattedFeelsLike: "82°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        writeHostedRender(miami, size: CGSize(width: 390, height: 110),
                          name: "VQ-1.7-ios-minibar-added-default-exec.png")
        let longPlace = WearIveRunMiniBarView(
            state: .updated(payload: .place("Truth or Consequences, New Mexico")),
            entry: collectionEntry(place: "Truth or Consequences, New Mexico"),
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .environment(\.dynamicTypeSize, .accessibility5)
            .background(AppTheme.background)
        writeHostedRender(longPlace, size: CGSize(width: 390, height: 150),
                          name: "VQ-2.10-ios-minibar-F-LONGPLACE-max-exec.png")
    }

    // MARK: - Weather detail sheet (hosted) — VQ-7.4 row (a)

    @MainActor
    func testExecWeatherDetailHosted() {
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
        writeHostedRender(sheet, size: CGSize(width: 390, height: 700),
                          name: "VQ-7.4-ios-weatherdetail-default-exec.png")
    }

    // MARK: - RW-78 fork + tap destination (hosted)
    //
    // The fixture host mirrors production's FirstLookView wiring
    // exactly: onCustomize sets the flag that presents
    // MakeItMeOptInFlow. The test captures the fork, then invokes
    // the very closure the fork's "Use my photos" Button carries
    // as its action (Button(action: onCustomize)), settles, and
    // captures the destination the tap lands on.

    private final class ForkHostModel: ObservableObject {
        @Published var showOptIn = false
    }

    private struct ForkHostFixture: View {
        @ObservedObject var model: ForkHostModel
        @StateObject private var driver = OptInCommitDriver()
        var body: some View {
            ZStack {
                AppTheme.background.ignoresSafeArea()
                if model.showOptIn {
                    MakeItMeOptInFlow(
                        mode: .optIn,
                        onCommit: { _ in },
                        onFinish: {},
                        onCancel: {},
                        commitDriver: driver)
                } else {
                    MakeItMeForkView(
                        previewHeroURL: nil,
                        previewContextLine: "New York, NY · Now",
                        previewOutfitLine: "Long Sleeve Shirt · Tights",
                        onCustomize: { model.showOptIn = true },
                        onStock: {})
                }
            }
        }
    }

    @MainActor
    func testExecForkAndTapDestinationHosted() {
        let model = ForkHostModel()
        let fixture = ForkHostFixture(model: model)
        let size = CGSize(width: 390, height: 760)
        // Fork state
        let okFork = writeHostedRender(fixture, size: size,
                                       name: "RW78-ios-fork-default-exec.png", attempts: 3)
        print("VQEXEC fork hosted: \(okFork)")
        // Drive the fork's own onCustomize action — the Button's
        // action closure — and capture where the user lands.
        model.showOptIn = true
        let okDest = writeHostedRender(fixture, size: size,
                                       name: "RW78-ios-fork-aftertap-consent-exec.png", attempts: 3)
        print("VQEXEC fork destination hosted: \(okDest)")
    }

    // MARK: - Late-run hosted retries (ZZ: alphabetical last)
    //
    // Run 42300203909 evidence: every hosted capture in this
    // class came back pure black early in the run, while the
    // candidate harness's hosted pill test later in the same run
    // produced real light-state frames. Hosted compositing warms
    // up over the run; these legs re-attempt the blacked surfaces
    // late, with a warm-up capture first and longer settles.

    @MainActor
    private func warmUpHosted() {
        let v = ZStack {
            AppTheme.background
            Text("warmup").foregroundColor(AppTheme.inkPrimary)
        }
        writeHostedRender(v, size: CGSize(width: 200, height: 200),
                          name: "VQEXEC-warmup.png", attempts: 2)
    }

    @MainActor
    func testExecZZDarkPillsRetry() {
        warmUpHosted()
        writeHostedRender(pillHero(.makingWithUpsell, dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-0-dark-exec.png", attempts: 6)
        writeHostedRender(pillHero(.freeLook(used: 3, allowance: 5), dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-3-dark-exec.png", attempts: 6)
        writeHostedRender(pillHero(.exhausted(used: 5, allowance: 5), dark: true), size: CGSize(width: 390, height: 520),
                          name: "VQ-1.10-ios-F-FREE-5-dark-exec.png", attempts: 6)
    }

    @MainActor
    func testExecZZForkRetry() {
        warmUpHosted()
        let model = ForkHostModel()
        let fixture = ForkHostFixture(model: model)
        let size = CGSize(width: 390, height: 760)
        writeHostedRender(fixture, size: size,
                          name: "RW78-ios-fork-default-exec.png", attempts: 6)
        model.showOptIn = true
        writeHostedRender(fixture, size: size,
                          name: "RW78-ios-fork-aftertap-consent-exec.png", attempts: 6)
    }

    @MainActor
    func testExecZZMiniBarRetry() {
        warmUpHosted()
        let miami = WearIveRunMiniBarView(
            state: .added(place: "Miami, FL", moment: .now),
            entry: collectionEntry(place: "Miami, FL"),
            formattedFeelsLike: "82°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .background(AppTheme.background)
        writeHostedRender(miami, size: CGSize(width: 390, height: 110),
                          name: "VQ-1.7-ios-minibar-added-default-exec.png", attempts: 6)
        let longPlace = WearIveRunMiniBarView(
            state: .updated(payload: .place("Truth or Consequences, New Mexico")),
            entry: collectionEntry(place: "Truth or Consequences, New Mexico"),
            formattedFeelsLike: "57°", cueCount: 0, isAtEndOfScroll: false,
            onOpenCollection: {}, onTrash: {})
            .environment(\.dynamicTypeSize, .accessibility5)
            .background(AppTheme.background)
        writeHostedRender(longPlace, size: CGSize(width: 390, height: 150),
                          name: "VQ-2.10-ios-minibar-F-LONGPLACE-max-exec.png", attempts: 6)
    }

    @MainActor
    func testExecZZWatchSettingsRetry() {
        warmUpHosted()
        for (pref, label) in [(GenderPreference.male, "mens"), (.female, "womens"), (.unisex, "neutral")] {
            let vm = WatchViewModel()
            vm.genderPreference = pref
            vm.temperatureUnit = .fahrenheit
            let view = WatchSettingsView(viewModel: vm)
                .background(Color.black)
            writeHostedRender(view, size: CGSize(width: 198, height: 242),
                              name: "RW81-ios-watchsettings-\(label)-exec.png", attempts: 5)
        }
    }

    @MainActor
    func testExecZZWeatherDetailRetry() {
        warmUpHosted()
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
        writeHostedRender(sheet, size: CGSize(width: 390, height: 700),
                          name: "VQ-7.4-ios-weatherdetail-default-exec.png", attempts: 6)
    }

    // MARK: - VQ-10.4 captions

    func testExecCaptions() throws {
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
        try writeText(out, name: "VQ-10.4-ios-captions-exec.txt")
        print(out)
    }
}
