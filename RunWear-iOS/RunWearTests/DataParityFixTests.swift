import XCTest

/// Pins for the 2026-10-30 iOS data-parity audit fixes (the items
/// the Oct 28 register's Stage 1/2 passes had NOT reached):
///
/// - R-19: a manually chosen location persists across relaunch and
///   governs launch/refresh until the user switches back to GPS.
///   (Android's PreferencesRepository.SavedLocation is read first;
///   iOS kept the choice in memory only, so a relaunch silently
///   returned to GPS and the next GPS fix overwrote the manual
///   location's weather.)
/// - Hero temperature contract: the hero headline is the RAW
///   measured feels-like and the second line is the RAW actual
///   temperature (Android: effectiveTemperature == feelsLike, plus
///   an "Actual:" line; PWA: .temp-main is feelsLike). iOS showed
///   the actual temperature as the headline and a comfort-adjusted
///   value as "Feels like" — a displayed measurement that existed
///   on neither reference surface.
final class DataParityFixTests: XCTestCase {

    // MARK: - ManualLocationStore round-trip (isolated defaults)

    private func isolatedDefaults() -> UserDefaults {
        UserDefaults(suiteName: "DataParityFixTests.\(UUID().uuidString)")!
    }

    func testManualLocationStoreRoundTrip() {
        let defaults = isolatedDefaults()
        XCTAssertNil(ManualLocationStore.load(from: defaults))

        let saved = ManualLocationStore.Saved(latitude: 25.7617, longitude: -80.1918, name: "Miami, FL")
        ManualLocationStore.save(saved, to: defaults)
        XCTAssertEqual(ManualLocationStore.load(from: defaults), saved)

        ManualLocationStore.clear(from: defaults)
        XCTAssertNil(ManualLocationStore.load(from: defaults))
    }

    func testManualLocationStoreLoadsZeroCoordinates() {
        // A saved point on the equator / prime meridian is 0.0 in
        // both fields: load must not mistake it for "never saved".
        let defaults = isolatedDefaults()
        let saved = ManualLocationStore.Saved(latitude: 0, longitude: 0, name: "Null Island")
        ManualLocationStore.save(saved, to: defaults)
        XCTAssertEqual(ManualLocationStore.load(from: defaults), saved)
    }

    // MARK: - View model restore / clear (R-19 behavior)

    @MainActor
    func testViewModelRestoresSavedManualLocation_andSwitchToGPSClearsIt() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 40.7128, longitude: -74.0060, name: "New York, NY")
        )
        let vm = WeatherViewModel()
        XCTAssertFalse(vm.isUsingGPS)
        XCTAssertEqual(vm.locationName, "New York, NY")
        XCTAssertNotNil(vm.restoredManualLocation)
        XCTAssertEqual(vm.restoredManualLocation?.coordinate.latitude ?? 0, 40.7128, accuracy: 0.0001)
        XCTAssertEqual(vm.restoredManualLocation?.coordinate.longitude ?? 0, -74.0060, accuracy: 0.0001)

        vm.switchToGPS()
        XCTAssertTrue(vm.isUsingGPS)
        XCTAssertNil(vm.restoredManualLocation)
        XCTAssertNil(ManualLocationStore.load())
    }

    @MainActor
    func testViewModelDefaultsToGPSWhenNothingSaved() {
        ManualLocationStore.clear()
        let vm = WeatherViewModel()
        XCTAssertTrue(vm.isUsingGPS)
        XCTAssertNil(vm.restoredManualLocation)
    }

    // MARK: - Hero temperature contract

    @MainActor
    func testDisplayedTemperaturesAreRawMeasuredValues() {
        // 78°F actual / 85°F feels-like, comfort at the strongest
        // warm detent (+10). The engine's warmth frame uses the
        // comfort adjustment; the DISPLAYED numbers must not: the
        // headline property is the raw feels-like, the actual
        // property is the raw actual. Before this fix,
        // currentFeelsLike returned 95 here.
        let vm = WeatherViewModel()
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .veryWarm
        let hourly = HourlyWeather(
            time: ["2026-10-29T12:00"],
            temperature2m: [25.6],          // ≈ 78.1°F
            apparentTemperature: [29.4],    // ≈ 84.9°F
            relativeHumidity2m: [60],
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
            currentWeather: CurrentWeather(temperature: 25.6, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: "America/New_York",
            currentDetail: nil
        )
        vm.selectedDate = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 29))!
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()

        XCTAssertEqual(vm.currentTemperature, 78.1, accuracy: 0.2)  // actual, raw
        XCTAssertEqual(vm.currentFeelsLike, 84.9, accuracy: 0.2)    // feels-like, raw — NOT +10
    }
}
