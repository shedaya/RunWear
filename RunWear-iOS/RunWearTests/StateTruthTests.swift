import XCTest

/// Pins for WS-2 (RC-2 / D-05) — state truth, iOS alignment.
///
/// The deep audit judged iOS CLEAN on D-05 at code level: the unit
/// preference loads synchronously in `WeatherViewModel.init()`, no
/// animated numeral exists in the iOS target, and a US-locale user
/// gets °F from the first frame. This suite VERIFIES that verdict
/// against the current tree and pins the §1.1 model as tests:
///
/// - Weather is fetched and stored in ONE canonical frame that the
///   display unit never influences: the request asks Open-Meteo for
///   celsius + mph unconditionally (WeatherService.forecastURL has
///   no unit input at all), the model layer holds those canonical
///   values, the engine / hero / entry frame derives °F from them
///   by a fixed conversion, and the user's unit is a pure
///   render-time projection (`TemperatureUnit.format*`).
/// - No unit value lives inside weather objects, persisted entry
///   payloads, cache keys, or generated-image requests.
/// - A unit change re-renders only: the view model's
///   `temperatureUnit` didSet writes UserDefaults and nothing else —
///   no refetch, no recommendation re-run, no hero reload — and
///   every rendered figure derives from the SAME current unit in
///   the same synchronous turn, so a mixed-unit frame is not
///   representable.
/// - The hero numeral is display-only (ruling T-1): no tap, no
///   animation, no content transition; the unit is changed only in
///   Settings (and Onboarding's preferences step), and the stored
///   value drives the first paint after relaunch.
/// - Day changes and late-landing data swap atomically: one
///   synchronous production call settles snapshot + recommendation
///   together, and the rendered set is wholly the new state's.
///
/// The flip test (the workstream gate): repeated programmatic unit
/// flips at every data state — loading / loaded / error / stale —
/// end on one consistent unit, checked after EVERY flip.
final class StateTruthTests: XCTestCase {

    // MARK: - Defaults hygiene
    //
    // WeatherViewModel reads and writes UserDefaults.standard, so
    // every test here saves the stored unit on entry and restores
    // it on exit, and clears any manual location (the view model's
    // init also restores one).

    private var savedUnitRaw: String?

    override func setUp() {
        super.setUp()
        savedUnitRaw = UserDefaults.standard.string(forKey: "temperatureUnit")
        ManualLocationStore.clear()
    }

    override func tearDown() {
        if let savedUnitRaw {
            UserDefaults.standard.set(savedUnitRaw, forKey: "temperatureUnit")
        } else {
            UserDefaults.standard.removeObject(forKey: "temperatureUnit")
        }
        ManualLocationStore.clear()
        super.tearDown()
    }

    // MARK: - Fixtures (canonical values: °C at the model layer, mph wind)
    //
    // Day 1: actual 10°C = 50°F, feels-like 5°C = 41°F.
    // Day 2: actual 22°C ≈ 71.6°F, feels-like 20°C = 68°F.
    // The two days' figures are deliberately far apart so a figure
    // from the wrong day (or the wrong unit) is unmistakable.

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day))!
    }

    private func twoDayWeather() -> WeatherData {
        let hourly = HourlyWeather(
            time: ["2026-10-29T07:00", "2026-10-30T07:00"],
            temperature2m: [10, 22],
            apparentTemperature: [5, 20],
            relativeHumidity2m: [50, 50],
            precipitationProbability: [0, 0],
            precipitation: [0, 0],
            rain: [0, 0],
            uvIndex: [0, 0],
            windSpeed10m: [8, 12],
            windDirection10m: [0, 90],
            windGusts10m: [8, 12],
            weatherCode: [0, 0]
        )
        return WeatherData(
            latitude: 40.7128,
            longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 10, windspeed: 8, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: "America/New_York",
            currentDetail: nil
        )
    }

    /// A view model in the loaded state for day 1 at 07:00, built
    /// through the production assembly (the same sequence
    /// `fetchWeather` performs once its fetch lands).
    @MainActor
    private func loadedViewModel(unit: TemperatureUnit) -> WeatherViewModel {
        UserDefaults.standard.set(unit.rawValue, forKey: "temperatureUnit")
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.comfortLevel = .neutral
        vm.weatherData = twoDayWeather()
        vm.selectedDate = date(2026, 10, 29)
        vm.selectedHour = 7
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return vm
    }

    // MARK: - The rendered figure set
    //
    // Every temperature figure the home sheet renders, produced by
    // the SAME production formatters WeatherOutfitView uses:
    // the hero numeral (`temperatureUnit.format(currentFeelsLike)`),
    // the "Actual NN°" line (HomeHeaderCopy.actualLine over
    // `format(currentTemperature)`), the bracket badge's range
    // (TemperatureBracket.rangeText(for:)), the share headline
    // (shareText), and the mini bar's formatted feels-like
    // (`formatFromFahrenheit(entry.feelsLikeF)`).

    private struct RenderedFigures {
        var numeral: String
        var actualLine: String
        var badgeRange: String
        var share: String?
        var miniBar: String

        /// The sheet figures proper — the one-unit contract binds
        /// these in full. The share payload is checked by its
        /// headline line only (see assertOneConsistentUnit): its
        /// tip line is engine copy, not a rendered figure.
        var all: [String] {
            [numeral, actualLine, badgeRange, miniBar]
        }
    }

    @MainActor
    private func renderedFigures(_ vm: WeatherViewModel) -> RenderedFigures {
        let entry = WearIveRunEntry(
            dayStart: date(2026, 10, 29),
            placeName: "New York, NY",
            feelsLikeF: 41,
            actualF: 50,
            condition: "Clear",
            itemNames: ["Tights"],
            itemKeys: ["LIGHT_TIGHTS"],
            heroImageReference: "x"
        )
        return RenderedFigures(
            numeral: vm.temperatureUnit.format(vm.currentFeelsLike),
            actualLine: HomeHeaderCopy.actualLine(
                formattedTemperature: vm.temperatureUnit.format(vm.currentTemperature)
            ),
            badgeRange: vm.recommendation!.temperatureBracket.rangeText(for: vm.temperatureUnit),
            share: vm.shareText,
            miniBar: vm.temperatureUnit.formatFromFahrenheit(entry.feelsLikeF)
        )
    }

    /// The one-unit contract: every figure that carries a degree
    /// symbol carries the CURRENT unit's symbol, none carries the
    /// other unit's, and the two headline figures are exactly the
    /// canonical values projected into that unit.
    @MainActor
    private func assertOneConsistentUnit(
        _ vm: WeatherViewModel,
        expectedNumeral: String,
        expectedActualLine: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let figures = renderedFigures(vm)
        let unit = vm.temperatureUnit
        let other: TemperatureUnit = unit == .fahrenheit ? .celsius : .fahrenheit
        for figure in figures.all {
            XCTAssertFalse(
                figure.contains(other.symbol),
                "mixed-unit figure '\(figure)' while unit is \(unit.rawValue)",
                file: file, line: line
            )
            if figure.contains("°") {
                XCTAssertTrue(
                    figure.contains(unit.symbol),
                    "figure '\(figure)' does not carry the current unit \(unit.symbol)",
                    file: file, line: line
                )
            }
        }
        // The share payload's headline line speaks the same unit.
        if let share = figures.share,
           let headline = share.components(separatedBy: "\n").first(where: { $0.contains("Feels like") }) {
            XCTAssertTrue(headline.contains(unit.symbol), "share headline '\(headline)' not in \(unit.symbol)", file: file, line: line)
            XCTAssertFalse(headline.contains(other.symbol), "share headline '\(headline)' carries \(other.symbol)", file: file, line: line)
        }
        XCTAssertEqual(figures.numeral, expectedNumeral, file: file, line: line)
        XCTAssertEqual(figures.actualLine, expectedActualLine, file: file, line: line)
        // The mini bar formats TODAY's entry (a fixed 41°F fixture
        // here) from the canonical frame in the current unit — it
        // follows the unit, not the browsed day.
        XCTAssertEqual(figures.miniBar, unit.formatFromFahrenheit(41), file: file, line: line)
    }

    /// Flips the unit `count` times through the production property
    /// (the Settings picker's write path), asserting the one-unit
    /// contract after EVERY flip, for the day-1 fixture figures.
    @MainActor
    private func flipCheckingConsistency(_ vm: WeatherViewModel, times: Int) {
        for _ in 0..<times {
            vm.temperatureUnit = vm.temperatureUnit == .fahrenheit ? .celsius : .fahrenheit
            if vm.temperatureUnit == .fahrenheit {
                assertOneConsistentUnit(vm, expectedNumeral: "41°F", expectedActualLine: "Actual 50°F")
            } else {
                assertOneConsistentUnit(vm, expectedNumeral: "5°C", expectedActualLine: "Actual 10°C")
            }
        }
    }

    // MARK: - Canonical fetch: the request carries no display unit

    func testForecastRequestIsCanonicalAndUnitFree() {
        let url = WeatherService.forecastURL(latitude: 40.7128, longitude: -73.9857)
        let components = URLComponents(url: url!, resolvingAgainstBaseURL: false)
        let items = components?.queryItems ?? []

        func value(_ name: String) -> String? { items.first(where: { $0.name == name })?.value }

        // The canonical frame is FIXED: celsius temperatures and mph
        // wind, whatever the user's display unit — forecastURL does
        // not even take a unit parameter, so no caller can stamp
        // one into the request.
        XCTAssertEqual(value("temperature_unit"), "celsius")
        XCTAssertEqual(value("wind_speed_unit"), "mph")
        XCTAssertFalse(
            (url?.absoluteString ?? "").lowercased().contains("fahrenheit"),
            "the display unit leaked into the weather request"
        )
    }

    // MARK: - No unit inside stored / cached / requested payloads

    func testWeatherObjectCarriesNoUnit() throws {
        let data = try JSONEncoder().encode(twoDayWeather())
        let json = String(decoding: data, as: UTF8.self).lowercased()
        XCTAssertFalse(json.contains("celsius"))
        XCTAssertFalse(json.contains("fahrenheit"))
        XCTAssertFalse(json.contains("temperatureunit"))
        XCTAssertFalse(json.contains("iscelsius"))
    }

    func testDecodedWeatherIsCanonicalAtTheModelLayer() throws {
        // An Open-Meteo-shaped payload: the API echoes its own unit
        // metadata in sibling keys the model does not decode; the
        // decoded values are the canonical ones, and the °F frame
        // is a derived projection of them, never a stored field.
        let payload = """
        {
          "latitude": 40.7, "longitude": -74.0, "timezone": "America/New_York",
          "current_weather_units": {"temperature": "°C"},
          "current_weather": {"temperature": 10.0, "windspeed": 8.0, "weathercode": 0, "is_day": 1},
          "hourly": {
            "time": ["2026-10-29T07:00"],
            "temperature_2m": [10.0], "apparent_temperature": [5.0],
            "relative_humidity_2m": [50], "precipitation_probability": [0],
            "precipitation": [0.0], "rain": [0.0], "uv_index": [0.0],
            "wind_speed_10m": [8.0], "wind_direction_10m": [0],
            "wind_gusts_10m": [8.0], "weather_code": [0]
          }
        }
        """
        let decoded = try JSONDecoder().decode(WeatherData.self, from: Data(payload.utf8))
        let snapshot = HourlyWeatherSnapshot.from(hourly: decoded.hourly!, at: 0)!
        XCTAssertEqual(snapshot.temperature, 10.0, accuracy: 1e-9) // canonical °C, as fetched
        XCTAssertEqual(snapshot.feelsLike, 5.0, accuracy: 1e-9)
        XCTAssertEqual(snapshot.windSpeed, 8.0, accuracy: 1e-9) // canonical mph, as fetched
        XCTAssertEqual(snapshot.temperatureFahrenheit, 50.0, accuracy: 1e-9) // derived, not stored
        XCTAssertEqual(snapshot.feelsLikeFahrenheit, 41.0, accuracy: 1e-9)
    }

    func testPersistedEntryPayloadCarriesNoUnit() throws {
        // "Wear I've Run" entries persist in the canonical °F frame
        // (feelsLikeF / actualF) with no unit field: the stored
        // bytes mean the same thing under either display unit, and
        // rendering converts at read time.
        let entry = WearIveRunEntry(
            dayStart: date(2026, 10, 29),
            placeName: "New York, NY",
            feelsLikeF: 41,
            actualF: 50,
            condition: "Clear",
            itemNames: ["Tights"],
            itemKeys: ["LIGHT_TIGHTS"],
            heroImageReference: "x"
        )
        let data = try JSONEncoder().encode(entry)
        let object = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        // The key set carries the GEN-3 session schema (the
        // owning session's identity + the entry-carried narration
        // kind) and the GEN-5 narration schema (the settled
        // moment reading + the last-saved stamp; the optional
        // update payload encodes only when present) alongside
        // the canonical-frame fields — and still NO unit field,
        // which is this pin's actual subject.
        XCTAssertEqual(
            Set(object.keys),
            ["dayStart", "placeName", "feelsLikeF", "actualF", "condition", "itemNames", "itemKeys", "heroImageReference", "sessionID", "wasUpdatedInSession", "momentReading", "lastSavedAt"]
        )
        let json = String(decoding: data, as: UTF8.self).lowercased()
        XCTAssertFalse(json.contains("fahrenheit") || json.contains("celsius") || json.contains("unit"))
    }

    @MainActor
    func testGeneratedImageKeysCarryNoUnit() {
        // Pool key: gender × weather × bracket × time — the bracket
        // derives from the canonical °F frame, so the same cell is
        // requested under either display unit.
        let poolKey = HeroCombinationId(
            gender: .MALE, weather: .CLEAR, temp: .MILD, time: .MIDDAY, variant: nil
        ).fullId
        XCTAssertEqual(poolKey, "MALE_CLEAR_MILD_MIDDAY")

        // Personal key: the same frame plus the outfit hash — this
        // string is both the server request's combination_id and
        // the PersonalCellCache key, so this also pins the cache.
        let vm = loadedViewModel(unit: .fahrenheit)
        let personalKey = PersonalCombination.make(
            genderPreference: .male,
            weatherCode: 0,
            feelsLikeFahrenheit: 41,
            hour: 7,
            outfit: vm.recommendation!
        ).combinationId
        for key in [poolKey, personalKey] {
            let lowered = key.lowercased()
            XCTAssertFalse(lowered.contains("fahrenheit") || lowered.contains("celsius"))
            XCTAssertFalse(key.contains("°"))
        }

        // Bundled fallback lookup takes bracket × weather only.
        let fallback = FallbackImageProvider.shared.getImageURL(temp: .MILD, weather: .CLEAR, size: .iOS)
        XCTAssertFalse(fallback.lowercased().contains("fahrenheit") || fallback.lowercased().contains("celsius"))
    }

    // MARK: - A unit change is a pure re-render

    /// Deterministic encoding for state comparison: JSONEncoder's
    /// default key order varies per encoder instance (two encodes
    /// of the identical struct differ in key order only — verified
    /// on the fixture), so byte comparison must sort the keys; the
    /// pinned quantity is the canonical CONTENT.
    private func canonicalJSON(_ weather: WeatherData) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(weather)
    }

    @MainActor
    func testUnitFlipIsPureReRender_canonicalStateUntouched() throws {
        let vm = loadedViewModel(unit: .fahrenheit)
        let weatherBefore = try canonicalJSON(vm.weatherData!)
        let snapshotBefore = vm.selectedWeatherSnapshot
        let itemsBefore = vm.recommendation!.allItems.map(\.key)
        let bracketBefore = vm.recommendation!.temperatureBracket
        let heroFrameBefore = vm.prepareHeroFrame()

        flipCheckingConsistency(vm, times: 10)

        // Ten flips later the canonical state is content-identical:
        // the unit write touched nothing but the preference itself.
        XCTAssertEqual(try canonicalJSON(vm.weatherData!), weatherBefore)
        XCTAssertEqual(vm.selectedWeatherSnapshot?.temperature, snapshotBefore?.temperature)
        XCTAssertEqual(vm.selectedWeatherSnapshot?.feelsLike, snapshotBefore?.feelsLike)
        XCTAssertEqual(vm.selectedWeatherSnapshot?.windSpeed, snapshotBefore?.windSpeed)
        XCTAssertEqual(vm.recommendation!.allItems.map(\.key), itemsBefore)
        XCTAssertEqual(vm.recommendation!.temperatureBracket, bracketBefore)
        XCTAssertEqual(vm.prepareHeroFrame()?.feelsLikeF, heroFrameBefore?.feelsLikeF)
        XCTAssertEqual(UserDefaults.standard.string(forKey: "temperatureUnit"), vm.temperatureUnit.rawValue)
    }

    @MainActor
    func testFlipIsSynchronousAndDriftFree() {
        // No animation, no counter, no async landing: the flip is
        // final in the same synchronous turn it is written — the
        // numeral read immediately after the write is already the
        // new unit's value — and because every render projects
        // from the canonical value, 100 flips accumulate no drift.
        let vm = loadedViewModel(unit: .fahrenheit)
        XCTAssertEqual(vm.temperatureUnit.format(vm.currentFeelsLike), "41°F")
        vm.temperatureUnit = .celsius
        XCTAssertEqual(vm.temperatureUnit.format(vm.currentFeelsLike), "5°C")
        for _ in 0..<100 {
            vm.temperatureUnit = vm.temperatureUnit == .fahrenheit ? .celsius : .fahrenheit
        }
        XCTAssertEqual(vm.temperatureUnit, .celsius)
        XCTAssertEqual(vm.temperatureUnit.format(vm.currentFeelsLike), "5°C")
        vm.temperatureUnit = .fahrenheit
        XCTAssertEqual(vm.temperatureUnit.format(vm.currentFeelsLike), "41°F")
    }

    @MainActor
    func testEngineAndHeroFramesAreUnitIndependent() {
        // The outfit, its bracket, the hero's keying frame and the
        // weather classifier all run on the canonical frame: the
        // display unit never reaches them.
        let fahrenheit = loadedViewModel(unit: .fahrenheit)
        let celsius = loadedViewModel(unit: .celsius)
        XCTAssertEqual(
            fahrenheit.recommendation!.allItems.map(\.key),
            celsius.recommendation!.allItems.map(\.key)
        )
        XCTAssertEqual(fahrenheit.recommendation!.temperatureBracket, celsius.recommendation!.temperatureBracket)
        XCTAssertEqual(fahrenheit.currentTempBracket, celsius.currentTempBracket)
        XCTAssertEqual(fahrenheit.prepareHeroFrame()?.feelsLikeF, celsius.prepareHeroFrame()?.feelsLikeF)
        XCTAssertEqual(fahrenheit.prepareHeroFrame()?.weatherCode, celsius.prepareHeroFrame()?.weatherCode)
    }

    // MARK: - THE FLIP TEST — every data state ends on one consistent unit

    @MainActor
    func testFlipTest_loadingState() {
        UserDefaults.standard.set(TemperatureUnit.fahrenheit.rawValue, forKey: "temperatureUnit")
        let vm = WeatherViewModel()
        vm.isLoading = true
        XCTAssertNil(vm.weatherData)

        for _ in 0..<7 {
            vm.temperatureUnit = vm.temperatureUnit == .fahrenheit ? .celsius : .fahrenheit
            // No figure exists to contradict itself, and the flips
            // manufacture none: still loading, still no data, and
            // the persisted unit is the one just written.
            XCTAssertNil(vm.weatherData)
            XCTAssertNil(vm.recommendation)
            XCTAssertTrue(vm.isLoading)
            XCTAssertEqual(
                HomePresentation.resolve(hasContent: vm.weatherData != nil && vm.recommendation != nil, errorMessage: vm.errorMessage),
                .loading
            )
            XCTAssertEqual(UserDefaults.standard.string(forKey: "temperatureUnit"), vm.temperatureUnit.rawValue)
        }
        XCTAssertEqual(vm.temperatureUnit, .celsius) // 7 flips from °F
    }

    @MainActor
    func testFlipTest_loadedState() {
        let vm = loadedViewModel(unit: .fahrenheit)
        flipCheckingConsistency(vm, times: 7)
        XCTAssertEqual(vm.temperatureUnit, .celsius)
        assertOneConsistentUnit(vm, expectedNumeral: "5°C", expectedActualLine: "Actual 10°C")
    }

    @MainActor
    func testFlipTest_errorState() {
        UserDefaults.standard.set(TemperatureUnit.fahrenheit.rawValue, forKey: "temperatureUnit")
        let vm = WeatherViewModel()
        vm.errorMessage = "Network unreachable"

        for _ in 0..<7 {
            vm.temperatureUnit = vm.temperatureUnit == .fahrenheit ? .celsius : .fahrenheit
            XCTAssertNil(vm.weatherData)
            XCTAssertEqual(vm.errorMessage, "Network unreachable")
            XCTAssertEqual(
                HomePresentation.resolve(hasContent: false, errorMessage: vm.errorMessage),
                .error("Network unreachable")
            )
            XCTAssertEqual(UserDefaults.standard.string(forKey: "temperatureUnit"), vm.temperatureUnit.rawValue)
        }
        XCTAssertEqual(vm.temperatureUnit, .celsius)
    }

    @MainActor
    func testFlipTest_staleState() {
        // Stale = content on screen with a refresh error over it:
        // the figures keep rendering (demoted error, per RW-6.2)
        // and they flip as one unit like any loaded state.
        let vm = loadedViewModel(unit: .fahrenheit)
        vm.errorMessage = "Network unreachable"
        XCTAssertEqual(
            HomePresentation.resolve(hasContent: true, errorMessage: vm.errorMessage),
            .content(showRefreshError: true)
        )
        flipCheckingConsistency(vm, times: 7)
        XCTAssertEqual(vm.errorMessage, "Network unreachable")
        XCTAssertEqual(vm.temperatureUnit, .celsius)
        assertOneConsistentUnit(vm, expectedNumeral: "5°C", expectedActualLine: "Actual 10°C")
    }

    // MARK: - Settings persistence & first paint

    @MainActor
    func testSettingsUnitPersistsAcrossRelaunchAndDrivesFirstPaint() {
        for unit in TemperatureUnit.allCases {
            // The stored Settings value is what a fresh view model
            // holds SYNCHRONOUSLY out of init — before any fetch or
            // view body — so the first painted frame is already in
            // the user's unit.
            UserDefaults.standard.set(unit.rawValue, forKey: "temperatureUnit")
            let firstPaint = WeatherViewModel()
            XCTAssertEqual(firstPaint.temperatureUnit, unit)

            // A Settings write persists through the property itself,
            // and the next launch (a new view model) reads it back.
            let other: TemperatureUnit = unit == .fahrenheit ? .celsius : .fahrenheit
            firstPaint.temperatureUnit = other
            XCTAssertEqual(UserDefaults.standard.string(forKey: "temperatureUnit"), other.rawValue)
            let relaunched = WeatherViewModel()
            XCTAssertEqual(relaunched.temperatureUnit, other)
        }
    }

    @MainActor
    func testUnitDefaultComesFromLocaleWhenNothingIsStored() {
        UserDefaults.standard.removeObject(forKey: "temperatureUnit")
        let vm = WeatherViewModel()
        XCTAssertEqual(vm.temperatureUnit, .defaultForLocale)
    }

    // MARK: - Day changes & late-landing data swap atomically

    @MainActor
    func testDayChangeSwapsAtomically_noFigureFromTheOtherDay() {
        let vm = loadedViewModel(unit: .fahrenheit)
        assertOneConsistentUnit(vm, expectedNumeral: "41°F", expectedActualLine: "Actual 50°F")

        // ONE synchronous production call settles the snapshot and
        // the recommendation together; the moment it returns,
        // every figure is day 2's — no day-1 figure survives
        // anywhere in the rendered set.
        vm.updateSelectedTime(date: date(2026, 10, 30), hour: 7)
        assertOneConsistentUnit(vm, expectedNumeral: "68°F", expectedActualLine: "Actual 72°F")
        let dayTwo = renderedFigures(vm)
        for figure in [dayTwo.numeral, dayTwo.actualLine, dayTwo.badgeRange] {
            XCTAssertFalse(figure.contains("41"), "day-1 figure survived the day change: \(figure)")
        }

        // The same atomicity under the other unit, changing back.
        vm.temperatureUnit = .celsius
        vm.updateSelectedTime(date: date(2026, 10, 29), hour: 7)
        assertOneConsistentUnit(vm, expectedNumeral: "5°C", expectedActualLine: "Actual 10°C")
    }

    @MainActor
    func testNewDataLandingAfterFlipRendersAsOneValue_orderIndependent() {
        // Order A: flip first, THEN new data lands (the production
        // landing sequence fetchWeather performs). The landed data
        // renders wholly in the already-chosen unit.
        let flipFirst = loadedViewModel(unit: .fahrenheit)
        flipFirst.temperatureUnit = .celsius
        flipFirst.weatherData = twoDayWeather()
        flipFirst.selectedDate = date(2026, 10, 30)
        flipFirst.selectedHour = 7
        flipFirst.updateSelectedWeatherSnapshot()
        flipFirst.makeRecommendation()
        assertOneConsistentUnit(flipFirst, expectedNumeral: "20°C", expectedActualLine: "Actual 22°C")

        // Order B: data lands first, flip after. Both orders
        // converge on the identical rendered set for the same
        // (data, unit) pair — there is no order in which a stale
        // unit or a stale day survives.
        let landFirst = loadedViewModel(unit: .fahrenheit)
        landFirst.weatherData = twoDayWeather()
        landFirst.selectedDate = date(2026, 10, 30)
        landFirst.selectedHour = 7
        landFirst.updateSelectedWeatherSnapshot()
        landFirst.makeRecommendation()
        landFirst.temperatureUnit = .celsius

        XCTAssertEqual(renderedFigures(flipFirst).numeral, renderedFigures(landFirst).numeral)
        XCTAssertEqual(renderedFigures(flipFirst).actualLine, renderedFigures(landFirst).actualLine)
        XCTAssertEqual(renderedFigures(flipFirst).badgeRange, renderedFigures(landFirst).badgeRange)
        XCTAssertEqual(renderedFigures(flipFirst).share, renderedFigures(landFirst).share)
    }

    // MARK: - Source pins (view layer is not compiled into this target)
    //
    // WeatherOutfitView / SettingsModal are app-target views the
    // test target does not compile (the suite's established
    // pattern), so the T-1 surface facts are pinned against the
    // source of record, resolved relative to this file. If the
    // source tree is unreachable from the test host the test skips
    // rather than guessing; the gate report carries the sweep.

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testHeroNumeralIsDisplayOnly_sourcePin() throws {
        let outfitView = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        let heroView = try sourceFile("../RunWear/Views/HeroImageView.swift")

        // The sheet numeral and its modifier chain: a plain Text —
        // no gesture, no animation, no content transition.
        let lines = outfitView.components(separatedBy: "\n")
        let numeralIndex = try XCTUnwrap(
            lines.firstIndex(where: { $0.contains("Text(viewModel.temperatureUnit.format(viewModel.currentFeelsLike))") }),
            "the sheet numeral was not found — if it moved, re-pin WS-2 against its new home"
        )
        let chain = lines[numeralIndex...min(numeralIndex + 5, lines.count - 1)].joined(separator: "\n")
        for forbidden in ["onTapGesture", "onLongPressGesture", "gesture", "contentTransition", ".animation", "withAnimation", "Button"] {
            XCTAssertFalse(chain.contains(forbidden), "hero numeral carries '\(forbidden)' — T-1 says display-only")
        }

        // No numeric content transition exists on either home view:
        // a Text(String) with no contentTransition swaps discretely
        // and can never display an intermediate (false) value.
        for source in [outfitView, heroView] {
            XCTAssertFalse(source.contains("contentTransition"))
            XCTAssertFalse(source.contains("numericText"))
        }

        // The unit is changed ONLY where the design puts it: the
        // shared TemperatureUnitPicker renders in Settings and in
        // Onboarding's preferences step — never on Home.
        XCTAssertFalse(outfitView.contains("TemperatureUnitPicker"))
        XCTAssertFalse(heroView.contains("TemperatureUnitPicker"))
        XCTAssertTrue(try sourceFile("../RunWear/Views/Modals/SettingsModal.swift").contains("TemperatureUnitPicker"))
        XCTAssertTrue(try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift").contains("TemperatureUnitPicker"))
    }

    func testTemperatureUnitWriteHasNoFetchSideEffects_sourcePin() throws {
        let viewModel = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        let lines = viewModel.components(separatedBy: "\n")
        let declaration = try XCTUnwrap(
            lines.firstIndex(where: { $0.contains("@Published var temperatureUnit") }),
            "temperatureUnit declaration not found"
        )
        let didSet = lines[declaration...min(declaration + 5, lines.count - 1)].joined(separator: "\n")
        XCTAssertTrue(didSet.contains("UserDefaults.standard.set"))
        for forbidden in ["fetchWeather", "loadHeroImage", "makeRecommendation", "Task"] {
            XCTAssertFalse(
                didSet.contains(forbidden),
                "a unit write triggers '\(forbidden)' — D-05's refetch mechanism must stay absent"
            )
        }
    }
}
