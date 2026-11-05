import XCTest

/// GEN-4 train ruling fix (Sol, Nov 4, 2026): "now" is the VIEWED
/// LOCATION's live local time and date on EVERY surface — the
/// reset path (resetToNow and the NOW-state tracking it feeds,
/// incl. the headline "↻ Now" and the pill Now segment, which
/// call the same reset) is framed in the viewed location's
/// timezone, never the device's. Android parity reference:
/// MainViewModel.resetToNow uses locationNow (LocalDateTime in
/// the viewed location's zone).
///
/// The selection's framing is the app's existing one: the
/// selected date's DEVICE-calendar components are the wall date
/// the hourly matching reads, so a correct reset lands the date
/// whose device-calendar components are the location's current
/// year/month/day, at the location's current hour.
final class Gen4LocationNowTests: XCTestCase {

    // MARK: - Fixtures

    private func instant(_ iso: String) -> Date {
        ISO8601DateFormatter().date(from: iso)!
    }

    /// Hourly forecast covering Nov 3–5, 2026 (all 24 hours each),
    /// flat 10°C apparent, in the given location timezone.
    private func threeDayWeather(timezone: String) -> WeatherData {
        var times: [String] = []
        for day in 3...5 {
            for hour in 0...23 {
                times.append(String(format: "2026-11-%02dT%02d:00", day, hour))
            }
        }
        let hourly = HourlyWeather(
            time: times,
            temperature2m: times.map { _ in 10 },
            apparentTemperature: times.map { _ in 10 },
            relativeHumidity2m: times.map { _ in 50 },
            precipitationProbability: times.map { _ in 0 },
            precipitation: times.map { _ in 0 },
            rain: times.map { _ in 0 },
            uvIndex: times.map { _ in 0 },
            windSpeed10m: times.map { _ in 5 },
            windDirection10m: times.map { _ in 0 },
            windGusts10m: times.map { _ in 5 },
            weatherCode: times.map { _ in 0 }
        )
        return WeatherData(
            latitude: 39.0997,
            longitude: -94.5786,
            currentWeather: CurrentWeather(temperature: 10, windspeed: 5, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: timezone,
            currentDetail: nil
        )
    }

    @MainActor
    private func loadedViewModel(timezone: String) -> WeatherViewModel {
        let vm = WeatherViewModel()
        vm.comfortLevel = .neutral
        vm.weatherData = threeDayWeather(timezone: timezone)
        return vm
    }

    private func selectedComponents(_ vm: WeatherViewModel) -> (Int, Int, Int) {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: vm.selectedDate)
        return (c.year!, c.month!, c.day!)
    }

    // MARK: - Reset lands on the location's date and hour

    @MainActor
    func testResetLandsOnLocationDateAndHour_locationAheadOfUTC() {
        // 2026-11-04T12:00Z is 2026-11-05 01:00 in Auckland
        // (NZDT, UTC+13): the location is already TOMORROW at
        // 1 AM while UTC — and every US device zone — is still
        // on Nov 4. A device-framed reset would land Nov 4 at
        // the device's hour; the ruling reset lands Nov 5, 01.
        let vm = loadedViewModel(timezone: "Pacific/Auckland")
        vm.updateSelectedTime(date: vm.selectedDate, hour: 18)
        XCTAssertFalse(vm.isNowSelection)

        vm.resetToNow(at: instant("2026-11-04T12:00:00Z"))

        XCTAssertTrue(vm.isNowSelection, "reset enters NOW")
        XCTAssertEqual(vm.selectedHour, 1, "the location's hour, not the device's")
        let (year, month, day) = selectedComponents(vm)
        XCTAssertEqual([year, month, day], [2026, 11, 5], "the location's date, not the device's")
        // The snapshot resolves on the location's wall time:
        // the hourly prefix 2026-11-05T01 exists, so this is a
        // real forecast snapshot, not the outside-window fallback.
        XCTAssertEqual(vm.selectedWeatherSnapshot?.hour, 1)
        XCTAssertFalse(vm.selectedTimeOutsideForecast)
    }

    @MainActor
    func testResetLandsOnLocationDateAndHour_locationBehindUTC() {
        // 2026-11-04T02:30Z is 2026-11-03 21:30 in New York
        // (EST, UTC-5): the location is still YESTERDAY evening
        // while UTC is already Nov 4.
        let vm = loadedViewModel(timezone: "America/New_York")

        vm.resetToNow(at: instant("2026-11-04T02:30:00Z"))

        XCTAssertTrue(vm.isNowSelection)
        XCTAssertEqual(vm.selectedHour, 21)
        let (year, month, day) = selectedComponents(vm)
        XCTAssertEqual([year, month, day], [2026, 11, 3])
        XCTAssertEqual(vm.selectedWeatherSnapshot?.hour, 21)
        XCTAssertFalse(vm.selectedTimeOutsideForecast)
    }

    @MainActor
    func testLocationNowDateFramesTheLocationsDay() {
        let vm = loadedViewModel(timezone: "Pacific/Auckland")
        let framed = vm.locationNowDate(at: instant("2026-11-04T12:00:00Z"))
        let c = Calendar.current.dateComponents([.year, .month, .day], from: framed)
        XCTAssertEqual([c.year!, c.month!, c.day!], [2026, 11, 5])
        XCTAssertEqual(vm.locationNowHour(at: instant("2026-11-04T12:00:00Z")), 1)
    }

    // MARK: - NOW-state tracking follows the location clock

    @MainActor
    func testNowTrackingTracksTheLocationClock() {
        // Live-instant form (deterministic at any wall time):
        // after a live reset, the selection IS the location's
        // now — its date components are the location calendar's
        // components of now, its hour is the location's hour,
        // and the NOW tracking the headline/pill read is true.
        let vm = loadedViewModel(timezone: "Pacific/Auckland")
        vm.resetToNow()

        XCTAssertTrue(vm.isNowSelection)
        XCTAssertTrue(vm.selectedDayIsLocationToday)
        XCTAssertEqual(vm.selectedHour, vm.locationNowHour())
        XCTAssertTrue(vm.isViewingLocationNow, "NOW tracking reads the location clock")

        let locationToday = vm.locationCalendar.dateComponents([.year, .month, .day], from: Date())
        let (year, month, day) = selectedComponents(vm)
        XCTAssertEqual(
            [year, month, day],
            [locationToday.year!, locationToday.month!, locationToday.day!]
        )

        // An explicit pick at another hour exits the tracking
        // even though the day is still the location's today.
        let otherHour = (vm.locationNowHour() + 3) % 24
        vm.updateSelectedTime(date: vm.selectedDate, hour: otherHour)
        XCTAssertFalse(vm.isNowSelection)
        XCTAssertFalse(vm.isViewingLocationNow)
    }

    @MainActor
    func testHeadlineAndPillResetPathsLandIdentically() {
        // Both surfaces call the one reset; the sheet's keepNow
        // commit reaches it too. All three land on the same
        // location-framed now state.
        let vm = loadedViewModel(timezone: "America/New_York")

        vm.updateSelectedTime(date: vm.selectedDate, hour: 3)
        vm.resetToNow()
        let headlineLanding = (vm.selectedDate, vm.selectedHour, vm.isNowSelection)

        vm.updateSelectedTime(date: vm.selectedDate, hour: 3)
        vm.applyTimePickerCommit(.keepNow)

        XCTAssertEqual(vm.selectedHour, headlineLanding.1, "keepNow lands on the same location hour as reset")
        XCTAssertEqual(vm.selectedDate, headlineLanding.0, "keepNow lands on the same location date as reset")
        XCTAssertEqual(vm.isNowSelection, headlineLanding.2)
        XCTAssertTrue(vm.isViewingLocationNow)
    }

    // MARK: - Source pins (view-layer facts the test target
    // cannot compile, the suite's established pattern)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testNowSurfacesShareTheLocationFramedReset_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // Headline "↻ Now" + pill Now segment: the same reset.
        XCTAssertEqual(view.components(separatedBy: "viewModel.resetToNow()").count - 1, 3,
                       "headline Now, pill Now, and the hero's onNowTapped all call the one reset")
        // The view's NOW tracking is the view model's
        // location-framed state, not a device-calendar check.
        XCTAssertTrue(view.contains("viewModel.isViewingLocationNow"))
        XCTAssertFalse(view.contains("calendar.isDateInToday(viewModel.selectedDate)\n            && viewModel.selectedHour"),
                       "the device-framed isViewingNow derivation is retired")
    }
}
