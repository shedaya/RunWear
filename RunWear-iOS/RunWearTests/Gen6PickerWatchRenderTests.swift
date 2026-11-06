import XCTest
import SwiftUI
import UIKit

/// GEN-6 WAVE C — EVIDENCE CLOSURE 2 (gen-5 gate gap #2, iOS
/// instance + RW-66): picker → watch re-solve, DATE-CHANGED
/// state, as a PAIRED artefact.
///
/// The selection is the iOS lane's exact RW-66 selection —
/// place New York, NY; date TOMORROW relative to the run day;
/// time hour 12 — committed through the real seam:
/// `DatePickerDraft.commit()` writes `WeatherViewModel.selectedDate`
/// (the host binding write in DatePickerSheet), and the host's
/// onChange (WeatherOutfitView) calls
/// `WeatherViewModel.updateSelectedTime(date:hour:)`, which
/// re-solves the snapshot and the recommendation.
///
/// Phone side: the date-changed picker and the post-Confirm
/// re-solved picker are rendered fresh in this run (the RW-66
/// renders of the same states, from the gen-6 gate run, are
/// cited alongside in the proof note).
///
/// Watch side — stated plainly, because the artefact must not
/// claim more than the code does: in this codebase the watch
/// does NOT receive the phone's selection at all.
/// WatchConnectivity (both directions) syncs exactly three
/// preference values — genderPreference, temperatureUnit,
/// comfortLevel — and nothing else; the phone's WeatherViewModel
/// and WeatherOutfitView hold no WatchConnectivity reference,
/// and WatchViewModel solves independently from its own GPS
/// fetch at the CURRENT hour. There is therefore no transport
/// seam to drive a committed selection through — that absence
/// is itself pinned by the source test below, and it is a
/// product finding, reported, not fixed, by this lane.
///
/// What CAN be proven, and is: the watch's solve machinery —
/// the same shared-engine `OutfitRecommendationService` call
/// WatchViewModel.fetchWeather makes, with its exact argument
/// construction — solves the COMMITTED selection (tomorrow,
/// hour 12, New York fixture) to the IDENTICAL outfit the phone
/// solved, and the watch's glance-temperature seam
/// (`WatchViewModel.glanceTemperatureString`, DA-14) renders the
/// same feels-like figure the phone solved. The watch outfit
/// surface is rendered from that watch-side solve at watch size
/// using the production `WatchClothingRow` views.
///
/// What this does NOT prove: live WatchConnectivity transport
/// of a selection between two running processes (no such
/// transport exists for selections), and the watch hero page's
/// numeral rendered from fetched state — WatchViewModel's
/// weather state is private and is set only by its live
/// GPS + network fetch, which a CI test host cannot drive.
/// Both limits are named in the proof note.
final class Gen6PickerWatchRenderTests: XCTestCase {

    private struct Fixture {
        let vm: WeatherViewModel
        let today: Date
        let tomorrow: Date
    }

    /// The RW-66 fixture shape (New York coordinates; today mild
    /// ≈65°F feels-like, tomorrow cold ≈30°F, both at hour 12),
    /// with humidity 50 and UV 0 so the phone's full-input
    /// engine call and the watch's narrower construction
    /// (humidity/UV/precipitation defaults) receive identical
    /// engine inputs — the comparison below is then exact, not
    /// approximate.
    @MainActor
    private func fixture() -> Fixture {
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
            time: times,
            temperature2m: temps,
            apparentTemperature: apparent,
            relativeHumidity2m: [Int](repeating: 50, count: 48),
            precipitationProbability: [Int](repeating: 0, count: 48),
            precipitation: [Double](repeating: 0, count: 48),
            rain: [Double](repeating: 0, count: 48),
            uvIndex: [Double](repeating: 0, count: 48),
            windSpeed10m: [Double](repeating: 6, count: 48),
            windDirection10m: [Int](repeating: 90, count: 48),
            windGusts10m: [Double](repeating: 9, count: 48),
            weatherCode: [Int](repeating: 0, count: 48)
        )
        let data = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 18.3, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: TimeZone.current.identifier, currentDetail: nil
        )
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.weatherData = data
        vm.selectedDate = today
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return Fixture(vm: vm, today: today, tomorrow: tomorrow)
    }

    /// The committed-selection seam, exactly as the host drives
    /// it: draft → commit() → binding write → onChange re-solve.
    @MainActor
    private func commitTomorrowHour12(_ f: Fixture) {
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        XCTAssertTrue(draft.isChanged, "tomorrow is a changed date against today's selection")
        f.vm.selectedDate = draft.commit()
        f.vm.updateSelectedTime(date: f.vm.selectedDate, hour: 12)
    }

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func render<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertEqual(image.size.width, size.width, "\(name): rendered width mismatch")
        XCTAssertEqual(image.size.height, size.height, "\(name): rendered height mismatch")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
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

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - Phone pair: date-changed picker, then post-Confirm solve

    @MainActor
    func testPhonePair_DateChangedThenConfirmResolves() throws {
        let f = fixture()
        let mild = f.vm.currentFeelsLike

        // (1) The DATE-CHANGED state: tomorrow selected in the
        // draft, standing uncommitted behind Confirm/Cancel.
        let picker = DatePickerSheet(
            selectedDate: .constant(f.tomorrow),
            isPresented: .constant(true)
        )
        .background(AppTheme.background)
        try render(picker, size: CGSize(width: 390, height: 620),
                   name: "picker-watch-phone-1-date-changed.png")

        // (2) Commit through the real seam and render the
        // re-solved selection (tomorrow's cold curve, hour 12).
        commitTomorrowHour12(f)
        XCTAssertTrue(Calendar.current.isDate(f.vm.selectedDate, inSameDayAs: f.tomorrow),
                      "the committed selection is tomorrow")
        XCTAssertEqual(f.vm.selectedHour, 12, "the committed hour is 12")
        XCTAssertLessThan(f.vm.currentFeelsLike, mild - 20,
                          "the commit re-solved from mild today to cold tomorrow")
        XCTAssertNotNil(f.vm.recommendation, "the phone outfit re-solved with the day")
        XCTAssertEqual(f.vm.selectedWeatherSnapshot?.hour, 12,
                       "the solved snapshot is hour 12 of the committed day")

        let solved = TimePickerSheet(
            chart: f.vm.dayChart,
            dateLabel: "Tomorrow",
            placeName: "New York",
            unit: .fahrenheit,
            initialHour: 12,
            openedInNowState: false,
            nowHour: nil,
            isPresented: .constant(true),
            dateSelection: .constant(f.tomorrow),
            onDateConfirmed: {}
        )
        .background(AppTheme.background)
        try render(solved, size: CGSize(width: 390, height: 560),
                   name: "picker-watch-phone-2-after-confirm.png")
    }

    // MARK: - Watch side: the same committed selection, solved by the watch's machinery

    @MainActor
    func testWatchSolvesTheCommittedSelection() throws {
        let f = fixture()
        commitTomorrowHour12(f)
        let phoneRecommendation = try XCTUnwrap(f.vm.recommendation)
        let snapshot = try XCTUnwrap(f.vm.selectedWeatherSnapshot)

        // WatchViewModel.fetchWeather's exact construction,
        // fed the committed selection instead of the watch's
        // own current-hour GPS frame: comfort-adjusted
        // feels-like keyed through the shared engine, rain from
        // the condition class, wind from the current block.
        let rawFeelsLikeF = snapshot.feelsLikeFahrenheit
        let adjusted = f.vm.comfortLevel.adjustTemperature(rawFeelsLikeF)
        let condition = snapshot.condition
        let watchRecommendation = OutfitRecommendationService.shared.getRecommendation(
            for: adjusted,
            rawTemperature: rawFeelsLikeF,
            isRaining: condition == .rain || condition == .drizzle || condition == .thunderstorm,
            windSpeed: f.vm.weatherData?.currentWeather.windspeed ?? 0,
            hour: 12
        )

        XCTAssertEqual(
            watchRecommendation.allItems.map(\.name),
            phoneRecommendation.allItems.map(\.name),
            "the watch's solve of the committed selection is the phone's outfit, item for item")
        XCTAssertEqual(
            String(describing: watchRecommendation.temperatureBracket),
            String(describing: phoneRecommendation.temperatureBracket),
            "the watch's solve lands in the phone's temperature bracket")

        // The glance numeral seam (DA-14): the watch renders the
        // comfort-adjusted feels-like — the same figure the
        // phone solved for the committed selection.
        let glance = WatchViewModel.glanceTemperatureString(
            rawFeelsLikeF: rawFeelsLikeF, comfortLevel: f.vm.comfortLevel, unit: .fahrenheit)
        XCTAssertEqual(glance, "\(Int(round(f.vm.currentFeelsLike)))°",
                       "the watch glance for the committed selection equals the phone's solved feels-like")

        // The watch outfit surface, rendered at watch size
        // (45mm: 198×242 pt) from the watch-side solve: the
        // production WatchClothingRow for every item, in
        // wearing order, on the watch's dark ground.
        let watchVM = WatchViewModel()
        watchVM.locationName = "New York, NY"
        let surface = VStack(alignment: .leading, spacing: 6) {
            Text(watchRecommendation.temperatureBracket.description)
                .font(.caption2)
                .foregroundColor(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(AppTheme.temperatureColor(for: snapshot.heroTempBracket).opacity(0.8))
                .cornerRadius(8)
            ForEach(watchRecommendation.allItems) { item in
                WatchClothingRow(item: item, gender: watchVM.genderPreference)
            }
        }
        .padding(8)
        .background(Color.black)
        // The production outfit page is a ScrollView; the static
        // render shows its first screenful, top-anchored (the
        // full item list is asserted above, item for item).
        .frame(width: 198, height: 242, alignment: .topLeading)
        try render(surface, size: CGSize(width: 198, height: 242),
                   name: "picker-watch-watch-1-same-selection.png")
    }

    // MARK: - The transport truth, pinned

    func testNoSelectionTransportExistsBetweenPhoneAndWatch() throws {
        let phoneManager = try sourceFile("../RunWear/Services/WatchConnectivityManager.swift")
        let watchManager = try sourceFile("../RunWearWatch/WatchConnectivityManager.swift")
        for (label, source) in [("phone", phoneManager), ("watch", watchManager)] {
            XCTAssertTrue(source.contains("\"genderPreference\""), "\(label) manager syncs genderPreference")
            XCTAssertTrue(source.contains("\"temperatureUnit\""), "\(label) manager syncs temperatureUnit")
            XCTAssertTrue(source.contains("\"comfortLevel\""), "\(label) manager syncs comfortLevel")
            XCTAssertFalse(source.contains("selectedDate"),
                           "\(label) manager carries NO selectedDate — selections are not transported")
            XCTAssertFalse(source.contains("selectedHour"),
                           "\(label) manager carries NO selectedHour — selections are not transported")
            XCTAssertFalse(source.contains("locationName"),
                           "\(label) manager carries NO location — selections are not transported")
        }
        let viewModel = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertFalse(viewModel.contains("WatchConnectivity"),
                       "the phone's WeatherViewModel holds no WatchConnectivity reference: a committed pick is never sent")
        let watchVM = try sourceFile("../RunWearWatch/WatchViewModel.swift")
        XCTAssertFalse(watchVM.contains("selectedDate"),
                       "the watch view model has no selection input: it solves its own current-hour GPS frame")
    }
}
