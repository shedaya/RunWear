import XCTest
import SwiftUI

/// FOLLOWING-BUILD ASSEMBLY (Nov 6, 2026) — RW-73 DETENT RULING
/// render proof, iOS half.
///
/// The ruling (Sol, Nov 6, 2026, delegated to Tank's suggestion;
/// BACKLOG RW-73): at the LARGEST accessibility text sizes the
/// date/time picker sheets may scroll internally; at default and
/// moderate sizes the settled no-scroll discipline stands. The
/// production form is one ScrollView gated on
/// `dynamicTypeSize.isAccessibilitySize` in each picker (pinned
/// by SheetDisciplineTests in the ruled form).
///
/// This lane proves the ruled behaviour renders:
///   * At .accessibility5 each sheet's CONTENT renders whole
///     on a fitting-height canvas, with the Confirm action
///     whole at the foot — the content the ScrollView carries
///     is complete and untruncated at the largest size.
///   * The wrapper half of the ruling (exactly one ScrollView,
///     gated on the accessibility size class, detents
///     unchanged) is pinned by SheetDisciplineTests.
///
/// Why the content and not the sheet: a SwiftUI ScrollView
/// photographs as a blank canvas under ImageRenderer (proven
/// on gate run 42282659617, whose AX-size sheet renders came
/// back uniformly blank at every frame), and hosted capture
/// in this CI is the standing black-frame lottery. The scroll
/// MOTION itself is therefore a device-walkthrough item on
/// the Visual QA sign-off sheet (VQ-2.4 post-ruling fixture);
/// these renders grade the half this vehicle can photograph.
///
/// Fixture mirrors Gen6RW66RenderTests (WeatherViewModel +
/// run-day-relative WeatherData), and DatePickerSheet /
/// TimePickerSheet are already dual-membered into the test
/// target (the RW-66 precedent).
final class Gen6DetentRenderTests: XCTestCase {

    @MainActor
    private func fixture() -> (vm: WeatherViewModel, today: Date) {
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
        return (vm, today)
    }

    @MainActor
    @discardableResult
    private func attach<Content: View>(_ content: Content, size: CGSize, name: String) throws -> UIImage {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertGreaterThan(image.size.width, 0)
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        return image
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

    /// Count pixels close to pine #175E4C (AppTheme.pinePayload,
    /// the Confirm label colour) in the bottom part of an image,
    /// starting at `fromFraction` of its height.
    private func pinePixels(_ image: UIImage, fromFraction: Double) -> Int {
        guard let cg = image.cgImage else { return 0 }
        let w = cg.width, h = cg.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return 0 }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var count = 0
        let startY = Int(Double(h) * fromFraction)
        for y in startY..<h {
            for x in 0..<w {
                let i = (y * w + x) * 4
                let r = Int(bytes[i]), g = Int(bytes[i + 1]), b = Int(bytes[i + 2])
                if abs(r - 0x17) <= 24 && abs(g - 0x5E) <= 24 && abs(b - 0x4C) <= 24 {
                    count += 1
                }
            }
        }
        return count
    }

    // MARK: - Date sheet content at the largest size

    @MainActor
    func testDetent_DateSheet_AX5_FittingHeight() throws {
        let (_, today) = fixture()
        let content = DatePickerSheet(
            selectedDate: .constant(today),
            isPresented: .constant(true)
        )
        .sheetContent(size: .accessibility5)
        .background(AppTheme.background)
        .environment(\.dynamicTypeSize, .accessibility5)

        // The full content on a fitting-height canvas: Confirm
        // whole at the foot of the content the ScrollView
        // carries. (Canvas heights here are deliberately taller
        // than the content; the foot lands in the canvas's lower
        // half under either anchoring. The Date canvas is 2800:
        // at AX5 the one-column grid makes the content the
        // taller of the two sheets, and gate 42291942279's
        // 1900pt canvas clipped the foot.)
        let fitting = try attach(content, size: CGSize(width: 390, height: 2800),
                                 name: "DETENT-date-AX5-fitting-height.png")
        XCTAssertGreaterThan(pinePixels(fitting, fromFraction: 0.5), 20,
                             "the Confirm action (pine label) must render in the content's lower half")
    }

    // MARK: - Time sheet content at the largest size

    @MainActor
    func testDetent_TimeSheet_AX5_FittingHeight() throws {
        let (vm, today) = fixture()
        let content = TimePickerSheet(
            chart: vm.dayChart,
            dateLabel: "Today",
            placeName: "New York",
            unit: .fahrenheit,
            initialHour: 12,
            openedInNowState: false,
            nowHour: nil,
            isPresented: .constant(true),
            dateSelection: .constant(today),
            onDateConfirmed: {}
        )
        .sheetContent(size: .accessibility5)
        .background(AppTheme.background)
        .environment(\.dynamicTypeSize, .accessibility5)

        let fitting = try attach(content, size: CGSize(width: 390, height: 1200),
                                 name: "DETENT-time-AX5-fitting-height.png")
        // Bottom region only: the chart's own pine axis labels
        // sit mid-sheet; the foot is the Confirm action row.
        XCTAssertGreaterThan(pinePixels(fitting, fromFraction: 0.7), 20,
                             "the Confirm action (pine label) must render at the content's foot")
    }
}
