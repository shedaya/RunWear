import XCTest

/// Pins for RW-6.5 (2026-10-30 UX audit): the share payload's text
/// is the aligned composition (Android/PWA layout) — a RAW
/// feels-like headline, the condition, the outfit itemized, the
/// first tip, and the runwear.ai link. The hand-rolled share it
/// replaces quoted the ACTUAL temperature as its headline and
/// linked runwear.app; both regressions are pinned shut here, along
/// with the comfort frame: the displayed feels-like (and the share
/// bracket) never carry the comfort adjustment, which belongs to
/// the engine's warmth frame only.
final class ShareTextCompositionTests: XCTestCase {

    @MainActor
    func testShareTextIsNilUntilARecommendationExists() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        let vm = WeatherViewModel()
        XCTAssertNil(vm.recommendation)
        XCTAssertNil(vm.shareText)
    }

    @MainActor
    func testShareTextComposition_headlineIsRawFeelsLike_linkIsRunwearAi() throws {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        // Strongest warm detent (+10): if the comfort adjustment
        // leaked into the share headline, 71.9°F would print as 81.
        vm.comfortLevel = .veryWarm
        let hourly = HourlyWeather(
            time: ["2026-10-29T12:00"],
            temperature2m: [18.3],          // ≈ 64.9°F actual
            apparentTemperature: [22.2],    // ≈ 71.9°F feels-like
            relativeHumidity2m: [55],
            precipitationProbability: [0],
            precipitation: [0],
            rain: [0],
            uvIndex: [3],
            windSpeed10m: [6],
            windDirection10m: [90],
            windGusts10m: [9],
            weatherCode: [0]
        )
        vm.weatherData = WeatherData(
            latitude: 40.7128,
            longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 18.3, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: "America/New_York",
            currentDetail: nil
        )
        vm.selectedDate = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 29))!
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()

        let text = try XCTUnwrap(vm.shareText)
        let lines = text.components(separatedBy: "\n")

        XCTAssertEqual(lines.first, "🏃 RunWear Recommendation")
        XCTAssertTrue(lines.contains("📍 New York, NY"), text)
        // Headline: RAW feels-like (71.9 truncates to 71, Android's
        // toInt), bracket from the same raw frame — NOT the actual
        // (64) and NOT the comfort-adjusted value (81).
        XCTAssertTrue(lines.contains("🌡️ Feels like 71°F (Warm Weather Running)"), text)
        XCTAssertFalse(text.contains("Feels like 64"), text)
        XCTAssertFalse(text.contains("Feels like 81"), text)
        XCTAssertTrue(lines.contains("Clear"), text)
        XCTAssertTrue(lines.contains("What to wear:"), text)
        for item in vm.recommendation!.allItems {
            XCTAssertTrue(lines.contains("• \(item.name)"), "share text missing outfit item \(item.name):\n\(text)")
        }
        XCTAssertEqual(lines.last, "Get your personalized running outfit at runwear.ai")
        XCTAssertFalse(text.contains("runwear.app"), text)
    }
}
