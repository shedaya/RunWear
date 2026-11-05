import XCTest

/// GEN-4 day-chart wave (Nov 4, 2026) — pins for the day-chart
/// model, the Choose-a-Time draft semantics, the headline-line
/// state, and the glance band's NOW-marker rule.
///
/// The NOW-confirm interpretation pinned here (the Android lane
/// implements the same rule): confirming the current hour while
/// in the NOW state, with the handle never moved off the NOW
/// position, KEEPS the NOW state; any other confirm is an
/// explicit pick and exits NOW.
final class Gen4DayChartTests: XCTestCase {

    // MARK: - Fixtures

    private func point(
        _ hour: Int,
        _ feelsLikeF: Double,
        bracket: TemperatureBracket,
        items: [String] = ["Top", "Bottom"]
    ) -> DayChartHour {
        DayChartHour(hour: hour, feelsLikeF: feelsLikeF, bracket: bracket, outfitItemNames: items)
    }

    /// A synthetic day: cold dawn, mild middle, cool evening.
    private func sampleDay() -> DayChartModel {
        var points: [DayChartHour] = []
        for hour in 0...23 {
            let feels: Double
            let bracket: TemperatureBracket
            switch hour {
            case 0..<6: feels = 38; bracket = .cold
            case 6..<12: feels = 45; bracket = .cool
            case 12..<18: feels = 55; bracket = .mild
            default: feels = 47; bracket = .cool
            }
            points.append(point(hour, feels, bracket: bracket))
        }
        return DayChartModel.build(points: points)!
    }

    // MARK: - Chart model: bands, ticks, low/high

    func testBandsAreContiguousBracketStretches() {
        let model = sampleDay()
        XCTAssertEqual(model.bands, [
            DayChartBand(bracket: .cold, startHour: 0, endHour: 5),
            DayChartBand(bracket: .cool, startHour: 6, endHour: 11),
            DayChartBand(bracket: .mild, startHour: 12, endHour: 17),
            DayChartBand(bracket: .cool, startHour: 18, endHour: 23),
        ])
    }

    func testOutfitChangeHoursMarkBracketCrossings() {
        let model = sampleDay()
        XCTAssertEqual(model.outfitChangeHours, [6, 12, 18])
    }

    func testOutfitChangeFiresOnItemChangeInsideOneBracket() {
        // A rain shell arriving inside one bracket is an outfit
        // change even though the band does not break.
        let model = DayChartModel.build(points: [
            point(9, 55, bracket: .mild, items: ["Tee", "Shorts"]),
            point(10, 56, bracket: .mild, items: ["Tee", "Shorts", "Rain Shell"]),
            point(11, 57, bracket: .mild, items: ["Tee", "Shorts", "Rain Shell"]),
        ])!
        XCTAssertEqual(model.outfitChangeHours, [10])
        XCTAssertEqual(model.bands.count, 1)
    }

    func testLowAndHighCarryTheirHours() {
        let model = DayChartModel.build(points: [
            point(6, 41, bracket: .cool),
            point(8, 43, bracket: .cool),
            point(14, 58, bracket: .mild),
            point(22, 44, bracket: .cool),
        ])!
        XCTAssertEqual(model.low.hour, 6)
        XCTAssertEqual(model.low.feelsLikeF, 41)
        XCTAssertEqual(model.high.hour, 14)
        XCTAssertEqual(model.high.feelsLikeF, 58)
    }

    func testBuildSortsAndDedupes() {
        let model = DayChartModel.build(points: [
            point(12, 55, bracket: .mild),
            point(6, 41, bracket: .cool),
            point(12, 99, bracket: .hot),
        ])!
        XCTAssertEqual(model.selectableHours, [6, 12])
        XCTAssertEqual(model.entry(forHour: 12)?.feelsLikeF, 55, "first point for an hour wins")
    }

    func testEmptySeriesBuildsNoModel() {
        XCTAssertNil(DayChartModel.build(points: []))
    }

    // MARK: - Horizon honesty

    func testCurveEndsAtTheForecastHorizon() {
        // The forecast carries only hours 0...13 for this day:
        // hour 18 does not exist in the model and cannot be
        // selected — the snap clamps to the last forecast hour.
        let model = DayChartModel.build(points: (0...13).map {
            point($0, 50, bracket: .mild)
        })!
        XCTAssertEqual(model.lastHour, 13)
        XCTAssertNil(model.entry(forHour: 18))
        XCTAssertEqual(model.nearestSelectableHour(toHour: 20), 13)
        XCTAssertEqual(model.nearestSelectableHour(toHour: -3), 0)
        XCTAssertEqual(model.steppedHour(from: 13, by: 1), 13, "steppers clamp at the horizon")
    }

    func testNearestSelectableHourSnaps() {
        let model = sampleDay()
        XCTAssertEqual(model.nearestSelectableHour(toHour: 6.4), 6)
        XCTAssertEqual(model.nearestSelectableHour(toHour: 6.6), 7)
        XCTAssertEqual(model.steppedHour(from: 6, by: -1), 5)
        XCTAssertEqual(model.steppedHour(from: 0, by: -1), 0)
        XCTAssertEqual(model.steppedHour(from: 23, by: 1), 23)
    }

    func testGeometryMapsEndsAndMidpoints() {
        let model = sampleDay()
        XCTAssertEqual(DayChartGeometry.x(forHour: 0, model: model, width: 230), 0)
        XCTAssertEqual(DayChartGeometry.x(forHour: 23, model: model, width: 230), 230)
        XCTAssertEqual(
            DayChartGeometry.fractionalHour(atX: 115, model: model, width: 230),
            11.5, accuracy: 0.001
        )
        // A flat day (low == high) renders mid-height, never NaN.
        let flat = DayChartModel.build(points: [point(6, 50, bracket: .mild), point(7, 50, bracket: .mild)])!
        XCTAssertEqual(DayChartGeometry.y(forFeelsLike: 50, model: flat, height: 60), 30)
    }

    // MARK: - Draft semantics

    private func draft(
        original: Int = 16,
        inNow: Bool = true,
        nowHour: Int? = 16,
        hours: [Int] = Array(0...23)
    ) -> TimePickerDraft {
        TimePickerDraft(
            originalHour: original,
            openedInNowState: inNow,
            nowHour: nowHour,
            selectableHours: hours
        )
    }

    func testDefaultDraftIsTheSelectedHour() {
        XCTAssertEqual(draft(original: 9, inNow: false, nowHour: nil).draftHour, 9)
        // In NOW state on today the host passes the location's
        // current hour as the original — the draft starts there.
        XCTAssertEqual(draft(original: 16, inNow: true, nowHour: 16).draftHour, 16)
    }

    func testDefaultDraftSnapsIntoTheForecast() {
        // Selected hour past the horizon: the draft starts on the
        // nearest forecast hour rather than an unselectable one.
        let d = draft(original: 22, inNow: false, nowHour: nil, hours: Array(0...13))
        XCTAssertEqual(d.draftHour, 13)
    }

    func testConfirmUnmovedInNowKeepsNow() {
        let d = draft()
        XCTAssertEqual(d.commit(), .keepNow)
    }

    func testConfirmAfterMovingIsAnExplicitPick() {
        var d = draft()
        d.move(to: 18)
        XCTAssertEqual(d.draftHour, 18)
        XCTAssertEqual(d.commit(), .explicitPick(hour: 18))
    }

    func testMovingAwayAndBackStillCountsAsMoved() {
        // The interpretation, pinned: "never moved off the NOW
        // position" is literal — scrubbing away and back onto the
        // NOW hour is still an explicit pick.
        var d = draft()
        d.move(to: 19)
        d.move(to: 16)
        XCTAssertEqual(d.draftHour, 16)
        XCTAssertTrue(d.movedOffNow)
        XCTAssertEqual(d.commit(), .explicitPick(hour: 16))
    }

    func testConfirmOutsideNowStateIsAlwaysExplicit() {
        let d = draft(original: 9, inNow: false, nowHour: nil)
        XCTAssertEqual(d.commit(), .explicitPick(hour: 9))
    }

    func testDraftMovesSnapAndClampToSelectableHours() {
        var d = draft(original: 10, inNow: false, nowHour: nil, hours: Array(0...13))
        d.move(to: 20)
        XCTAssertEqual(d.draftHour, 13, "past-horizon hours are not selectable")
        d.step(by: 1)
        XCTAssertEqual(d.draftHour, 13)
        d.step(by: -1)
        XCTAssertEqual(d.draftHour, 12)
    }

    func testCancelRestoresExactly_becauseTheSelectionIsNeverWritten() {
        // Cancel semantics are structural: the draft is a value
        // the sheet owns; the pre-open selection lives outside
        // it. Mutating and discarding a draft cannot move the
        // selection — this pins the draft half of that contract
        // (the view-model half is pinned below: only Confirm's
        // apply path writes the selection).
        let selectionBeforeOpen = 16
        var d = draft(original: selectionBeforeOpen)
        d.move(to: 21)
        d.step(by: -3)
        XCTAssertEqual(d.originalHour, selectionBeforeOpen)
        XCTAssertNotEqual(d.draftHour, selectionBeforeOpen)
        // `d` is discarded here, as Cancel discards it.
        XCTAssertEqual(selectionBeforeOpen, 16)
    }

    // MARK: - Headline state

    func testHeadlineNowReturnShowsOnlyInTheChangedState() {
        XCTAssertFalse(HeadlineNowReturn.isShown(isViewingNow: true))
        XCTAssertTrue(HeadlineNowReturn.isShown(isViewingNow: false))
    }

    // MARK: - Band state: the NOW marker

    func testNowMarkerOnlyOnTheLocationsToday() {
        let model = sampleDay()
        XCTAssertEqual(
            DayChartNowMarker.hour(selectedDayIsLocationToday: true, locationNowHour: 16, model: model),
            16
        )
        XCTAssertNil(
            DayChartNowMarker.hour(selectedDayIsLocationToday: false, locationNowHour: 16, model: model),
            "another day gets no marker"
        )
        XCTAssertNil(
            DayChartNowMarker.hour(selectedDayIsLocationToday: true, locationNowHour: 16, model:
                DayChartModel.build(points: (0...13).map { point($0, 50, bracket: .mild) })!),
            "a current hour past the horizon gets no marker"
        )
    }

    func testBandDotFollowsTheSelection() {
        // The band's dot placement is the model's snap of the
        // selected hour — in-range selections place exactly.
        let model = sampleDay()
        for hour in [0, 6, 16, 23] {
            XCTAssertEqual(model.nearestSelectableHour(toHour: Double(hour)), hour)
        }
    }

    // MARK: - Readout format

    func testReadoutIsHourAndFeelsLikeOnly() {
        XCTAssertEqual(DayChartFormat.readout(hour: 18, formattedFeelsLike: "47°F"), "6 PM · 47°F")
        XCTAssertEqual(DayChartFormat.hourMinuteLabel(16), "4:00 PM")
        XCTAssertEqual(DayChartFormat.hourLabel(0), "12 AM")
        XCTAssertEqual(DayChartFormat.hourLabel(12), "12 PM")
    }

    // MARK: - View-model integration

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day))!
    }

    /// One full forecast day (2026-11-04): apparent temperature
    /// in °C at the model layer — 10°C (50°F) flat, a 0°C (32°F)
    /// dawn low at hour 6, a 20°C (68°F) peak at hour 14.
    private func fullDayWeather(timezone: String, hours: ClosedRange<Int> = 0...23) -> WeatherData {
        let times = hours.map { String(format: "2026-11-04T%02d:00", $0) }
        let apparent: [Double] = hours.map { hour in
            switch hour {
            case 6: return 0
            case 14: return 20
            default: return 10
            }
        }
        let hourly = HourlyWeather(
            time: times,
            temperature2m: apparent,
            apparentTemperature: apparent,
            relativeHumidity2m: hours.map { _ in 50 },
            precipitationProbability: hours.map { _ in 0 },
            precipitation: hours.map { _ in 0 },
            rain: hours.map { _ in 0 },
            uvIndex: hours.map { _ in 0 },
            windSpeed10m: hours.map { _ in 5 },
            windDirection10m: hours.map { _ in 0 },
            windGusts10m: hours.map { _ in 5 },
            weatherCode: hours.map { _ in 0 }
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
    private func loadedViewModel(timezone: String = "America/Chicago") -> WeatherViewModel {
        let vm = WeatherViewModel()
        vm.comfortLevel = .neutral
        vm.weatherData = fullDayWeather(timezone: timezone)
        vm.selectedDate = date(2026, 11, 4)
        vm.selectedHour = 7
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return vm
    }

    @MainActor
    func testViewModelBuildsTheDayChartFromTheHourlySeries() {
        let vm = loadedViewModel()
        let chart = try! XCTUnwrap(vm.dayChart)
        XCTAssertEqual(chart.hours.count, 24)
        XCTAssertEqual(chart.low.hour, 6)
        XCTAssertEqual(chart.low.feelsLikeF, 32, accuracy: 0.001)
        XCTAssertEqual(chart.high.hour, 14)
        XCTAssertEqual(chart.high.feelsLikeF, 68, accuracy: 0.001)
        // Bracket bands follow the prescription frame: cold at
        // the dawn low, warm at the peak.
        XCTAssertEqual(chart.entry(forHour: 6)?.bracket, .cold)
        XCTAssertEqual(chart.entry(forHour: 14)?.bracket, .warm)
        XCTAssertEqual(chart.entry(forHour: 7)?.bracket, .mild)
        XCTAssertFalse(chart.entry(forHour: 7)?.outfitItemNames.isEmpty ?? true)
    }

    @MainActor
    func testViewModelChartEndsAtTheHorizon() {
        let vm = WeatherViewModel()
        vm.comfortLevel = .neutral
        vm.weatherData = fullDayWeather(timezone: "America/Chicago", hours: 0...13)
        vm.selectedDate = date(2026, 11, 4)
        vm.selectedHour = 7
        vm.updateSelectedWeatherSnapshot()
        let chart = try! XCTUnwrap(vm.dayChart)
        XCTAssertEqual(chart.lastHour, 13)
        XCTAssertNil(chart.entry(forHour: 18))
    }

    @MainActor
    func testNowStateTransitions() {
        let vm = loadedViewModel()
        vm.resetToNow()
        XCTAssertTrue(vm.isNowSelection, "reset enters NOW")
        vm.updateSelectedTime(date: vm.selectedDate, hour: 18)
        XCTAssertFalse(vm.isNowSelection, "an explicit pick exits NOW")
        XCTAssertEqual(vm.selectedHour, 18)
        vm.resetToNow()
        XCTAssertTrue(vm.isNowSelection)
    }

    @MainActor
    func testApplyTimePickerCommit_keepNowAndExplicitPick() {
        let vm = loadedViewModel()
        vm.resetToNow()
        vm.applyTimePickerCommit(.keepNow)
        XCTAssertTrue(vm.isNowSelection, "keepNow keeps the NOW state")

        vm.applyTimePickerCommit(.explicitPick(hour: 18))
        XCTAssertFalse(vm.isNowSelection)
        XCTAssertEqual(vm.selectedHour, 18)
    }

    @MainActor
    func testDraftCommitDrivesTheViewModel() {
        // The full loop the sheet runs: draft opened in NOW at
        // the current hour, confirmed unmoved -> keepNow; the
        // same draft moved -> explicit pick at the drafted hour.
        let vm = loadedViewModel()
        vm.resetToNow()
        let nowHour = vm.selectedHour
        let selectable = vm.dayChart?.selectableHours ?? Array(0...23)

        let unmoved = TimePickerDraft(
            originalHour: nowHour, openedInNowState: vm.isNowSelection,
            nowHour: nowHour, selectableHours: selectable
        )
        vm.applyTimePickerCommit(unmoved.commit())
        XCTAssertTrue(vm.isNowSelection)

        var moved = TimePickerDraft(
            originalHour: nowHour, openedInNowState: true,
            nowHour: nowHour, selectableHours: selectable
        )
        let target = nowHour == 18 ? 9 : 18
        moved.move(to: target)
        vm.applyTimePickerCommit(moved.commit())
        XCTAssertFalse(vm.isNowSelection)
        XCTAssertEqual(vm.selectedHour, target)
    }

    @MainActor
    func testLocationTodayAndNowMarkerUseTheLocationsClock() {
        // The location's zone IS the device zone here, so the
        // location's today is the device's today and the marker
        // is the device's current hour — deterministic at any
        // wall time.
        let zone = TimeZone.current.identifier
        let vm = WeatherViewModel()
        vm.comfortLevel = .neutral
        // A chart for TODAY's date (device framing), all 24 hours.
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let todayPrefix = formatter.string(from: Date())
        let times = (0...23).map { String(format: "%@T%02d:00", todayPrefix, $0) }
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
        vm.weatherData = WeatherData(
            latitude: 39.0997, longitude: -94.5786,
            currentWeather: CurrentWeather(temperature: 10, windspeed: 5, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: zone, currentDetail: nil
        )
        vm.selectedDate = Date()
        XCTAssertTrue(vm.selectedDayIsLocationToday)
        XCTAssertEqual(vm.dayChartNowHour, Calendar.current.component(.hour, from: Date()))

        vm.selectedDate = Calendar.current.date(byAdding: .day, value: 1, to: Date())!
        XCTAssertFalse(vm.selectedDayIsLocationToday)
        XCTAssertNil(vm.dayChartNowHour, "no NOW marker on a day that is not the location's today")
    }

    // MARK: - Source pins (the suite's established pattern for
    // view-layer facts the test target cannot compile)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testTimeSheetIsTheChartWithDraftControls_sourcePin() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/TimePickerSheet.swift")
        XCTAssertTrue(sheet.contains("Choose a Time"))
        XCTAssertTrue(sheet.contains("DayChartView("), "the chart replaces the grid")
        XCTAssertTrue(sheet.contains("Drag to pick an hour."), "the one instruction line")
        XCTAssertTrue(sheet.contains("\"Confirm\""))
        XCTAssertTrue(sheet.contains("\"Cancel\""))
        XCTAssertTrue(sheet.contains("TimePickerDraft"), "the draft owns the selection until Confirm")
        XCTAssertFalse(sheet.contains("LazyVGrid"), "the hour tiles are retired")
        XCTAssertFalse(sheet.contains("TimeButton"), "the hour tiles are retired")
    }

    func testHomeSurfaces_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("headlineLocationLine"), "the headline moment is the second entry")
        XCTAssertTrue(view.contains("heroWeatherFigures"), "the right-justified figures beside the numeral")
        XCTAssertTrue(view.contains("DayChartBandView("), "the glance band below the outfit")
        XCTAssertTrue(view.contains("applyTimePickerCommit"), "Confirm lands through the one apply path")
        XCTAssertFalse(view.contains("WeatherPillsRow("), "the pills row is retired from home")
        // GEN-5 (RW-28 item 12): the sheet's duplicate date/time
        // pill row is RETIRED — it restated the hero's focal time
        // in a second wording for the same instant (census A-6).
        // The date stays reachable through the hero's date seam;
        // one moment system only.
        XCTAssertFalse(view.contains("sheetDateTimePills"),
                       "the duplicate sheet pill row is retired (GEN-5 item 12)")
    }
}
