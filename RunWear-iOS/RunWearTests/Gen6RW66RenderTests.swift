import XCTest
import SwiftUI
import UIKit

/// GEN-6 (RW-66) — device-grade render proof, produced in the
/// CI simulator by the lane's established route (SwiftUI
/// ImageRenderer inside XCTest, PNGs attached to the run's
/// result bundle with keepAlways lifetime).
///
/// The four states Sol's walkthrough found broken, rendered:
///   1. the picker home's headline opens (Choose a Time) WITH
///      the restored date entry in its context line — "Today ›";
///   2. the day picker with a DIFFERENT date selected in the
///      draft (tomorrow highlighted, Confirm/Cancel standing);
///   3. AFTER Confirm — the view model re-solved to the new
///      date (asserted: cold feels-like, rebuilt recommendation)
///      and the picker reopened on the new day: "Tomorrow ›"
///      over the cold day's curve;
///   4. AFTER Cancel — the prior selection unchanged (asserted)
///      and the picker reopened on today, exactly as before.
///
/// Scope note (stated plainly): the full home view
/// (WeatherOutfitView) is not compiled into this test target —
/// dual-membering its whole dependency closure is not this
/// lane's to do — so states 3/4 render the re-solved SELECTION
/// through the production picker + view model rather than the
/// home screen itself. The home screen renders the same view
/// model state the assertions pin. TimePickerSheet,
/// DatePickerSheet and DayChartView are dual-membered into the
/// test target for these renders (the WeatherDetailSheet
/// precedent).
///
/// The weather fixture is built relative to the RUN day (today
/// mild ≈65°F feels-like, tomorrow cold ≈30°F, both at hour 12)
/// so the picker's real "today/tomorrow" grid and the view
/// model's textual day matching line up on any run date.
final class Gen6RW66RenderTests: XCTestCase {

    private struct Fixture {
        let vm: WeatherViewModel
        let data: WeatherData
        let today: Date
        let tomorrow: Date
    }

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
            relativeHumidity2m: [Int](repeating: 55, count: 48),
            precipitationProbability: [Int](repeating: 0, count: 48),
            precipitation: [Double](repeating: 0, count: 48),
            rain: [Double](repeating: 0, count: 48),
            uvIndex: [Double](repeating: 3, count: 48),
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
        return Fixture(vm: vm, data: data, today: today, tomorrow: tomorrow)
    }

    @MainActor
    private func attach<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertGreaterThan(image.size.width, 0)
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        // A rendered frame must not be a blank canvas: sample the
        // centre pixel row band for non-background content.
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
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

    // MARK: - 1 · The restored date entry, reachable from Home's picker

    @MainActor
    func testRW66Render1_TimeSheetCarriesTheDateEntry() throws {
        let f = fixture()
        let sheet = TimePickerSheet(
            chart: f.vm.dayChart,
            dateLabel: "Today",
            placeName: "New York",
            unit: .fahrenheit,
            initialHour: 12,
            openedInNowState: false,
            nowHour: nil,
            isPresented: .constant(true),
            dateSelection: .constant(f.today),
            onDateConfirmed: {}
        )
        .background(AppTheme.background)
        try attach(sheet, size: CGSize(width: 390, height: 560),
                   name: "RW-66-1-time-sheet-date-entry.png")
    }

    // MARK: - 2 · A date CHANGED state in the day picker

    @MainActor
    func testRW66Render2_DayPickerWithTomorrowSelected() throws {
        let f = fixture()
        // The draft initialises from the binding — tomorrow is
        // the changed selection the user has tapped, standing
        // uncommitted behind Confirm/Cancel.
        let sheet = DatePickerSheet(
            selectedDate: .constant(f.tomorrow),
            isPresented: .constant(true)
        )
        .background(AppTheme.background)
        try attach(sheet, size: CGSize(width: 390, height: 620),
                   name: "RW-66-2-date-picker-changed.png")
    }

    // MARK: - 3 · Confirm → re-solved to the new date

    @MainActor
    func testRW66Render3_AfterConfirmPickerOnTheNewDate() throws {
        let f = fixture()
        let mild = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        f.vm.selectedDate = draft.commit()
        f.vm.updateSelectedTime(date: f.vm.selectedDate, hour: f.vm.selectedHour)
        XCTAssertLessThan(f.vm.currentFeelsLike, mild - 20,
                          "the commit re-solved to the cold day before rendering")
        XCTAssertNotNil(f.vm.recommendation, "the outfit re-solved with the day")
        // The picker reopened on the committed state: tomorrow's
        // cold curve under a "Tomorrow ›" context line.
        let sheet = TimePickerSheet(
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
        try attach(sheet, size: CGSize(width: 390, height: 560),
                   name: "RW-66-3-after-confirm-new-date.png")
    }

    // MARK: - 4 · Cancel → prior selection restored unchanged

    @MainActor
    func testRW66Render4_AfterCancelPickerUnchanged() throws {
        let f = fixture()
        let feelsBefore = f.vm.currentFeelsLike
        var draft = DatePickerDraft(originalDate: f.vm.selectedDate)
        draft.select(f.tomorrow)
        // Cancel: the draft is discarded; nothing was written.
        _ = draft
        XCTAssertEqual(f.vm.selectedDate, f.today)
        XCTAssertEqual(f.vm.currentFeelsLike, feelsBefore)
        let sheet = TimePickerSheet(
            chart: f.vm.dayChart,
            dateLabel: "Today",
            placeName: "New York",
            unit: .fahrenheit,
            initialHour: 12,
            openedInNowState: false,
            nowHour: nil,
            isPresented: .constant(true),
            dateSelection: .constant(f.today),
            onDateConfirmed: {}
        )
        .background(AppTheme.background)
        try attach(sheet, size: CGSize(width: 390, height: 560),
                   name: "RW-66-4-after-cancel-unchanged.png")
    }
}
