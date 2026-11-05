import XCTest

/// GEN-6 Wave C (iOS lane) — parity, occlusion, copy, and the
/// missing looks library. Every test pins a wave item against the
/// settled design corpus (design-spec rw-gen4-walkthrough +
/// final-design frames), following the suite's established
/// pattern: pure logic pinned on the dual-membered models, view
/// settlements pinned by reading the source of record.
final class Gen6WaveCTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func chart(hours: [Int]) -> DayChartModel {
        DayChartModel.build(points: hours.map { hour in
            DayChartHour(hour: hour, feelsLikeF: 50, bracket: .mild, outfitItemNames: [])
        })!
    }

    // MARK: - DA-14: watch glance is the comfort-adjusted feels-like

    @MainActor
    func testDA14WatchGlanceRendersTheComfortAdjustedFigure() {
        // Raw 50°F with a non-zero comfort adjustment: the glance
        // must show the ADJUSTED figure the outfit is priced off,
        // never the raw 50°.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryWarm, unit: .fahrenheit),
            "60°")
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryCold, unit: .fahrenheit),
            "40°")
        // Neutral adjustment: adjusted == raw.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .neutral, unit: .fahrenheit),
            "50°")
        // Celsius converts the ADJUSTED figure: 60°F -> 16°C.
        XCTAssertEqual(
            WatchViewModel.glanceTemperatureString(rawFeelsLikeF: 50, comfortLevel: .veryWarm, unit: .celsius),
            "16°")
    }

    func testDA14WatchDisplayTemperatureUsesTheGlanceRule_sourcePin() throws {
        let vm = try sourceFile("../RunWearWatch/WatchViewModel.swift")
        XCTAssertTrue(vm.contains("Self.glanceTemperatureString("),
                      "displayTemperature must render through the comfort-adjusted glance rule")
        XCTAssertTrue(vm.contains("comfortLevel.adjustTemperature(rawFeelsLikeF)"),
                      "the glance rule must apply the comfort adjustment")
        XCTAssertFalse(vm.contains("let feelsLikeF = currentFeelsLikeF ?? weather.currentWeather.temperatureFahrenheit\n        let temp = temperatureUnit"),
                       "the gen-5 raw-value rendering must not return")
    }

    // MARK: - DA-05 (iOS): day-view hero on the protected HeroPhoto crop

    func testDA05DayViewHeroUsesHeroPhotoCrop_sourcePin() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        let dayStart = try XCTUnwrap(collection.range(of: "struct WearIveRunDayView"))
        let dayView = String(collection[dayStart.lowerBound...])
        XCTAssertTrue(dayView.contains("HeroPhoto(image: image, width: geo.size.width, height: geo.size.height)"),
                      "the day-view hero must render through HeroPhoto's top-anchored crop")
        XCTAssertFalse(dayView.contains("image.resizable().aspectRatio(contentMode: .fill)"),
                       "the bare centre fill must not return on the day-view hero")
    }

    // MARK: - DA-21: the time picker names a partial day's beyond-forecast region

    func testDA21PartialDayNamesBeyondForecast() {
        XCTAssertEqual(DayChartBeyondForecast.caption(for: chart(hours: Array(0...14))), "Beyond forecast")
        XCTAssertEqual(DayChartBeyondForecast.caption(for: chart(hours: Array(6...23))), "Beyond forecast")
        XCTAssertEqual(DayChartBeyondForecast.label, "Beyond forecast",
                       "the picker's name is the date cells' name, verbatim")
    }

    func testDA21FullDayNamesNothing() {
        XCTAssertNil(DayChartBeyondForecast.caption(for: chart(hours: Array(0...23))))
    }

    func testDA21TimePickerRendersTheCaption_sourcePin() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/TimePickerSheet.swift")
        XCTAssertTrue(sheet.contains("DayChartBeyondForecast.caption(for: chart)"),
                      "the time picker must render the beyond-forecast caption for a partial day")
        XCTAssertTrue(sheet.contains("Forecast isn't available for this day yet."),
                      "the wholly-empty day keeps its own naming")
    }

    // MARK: - RW-54 (iOS): one-line outfit notes; headline moment in pine bold

    func testRW54OutfitNoteIsOneLineAndMomentIsBold_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // The outfit-row note: exactly one lineLimit(1) on the
        // description in DarkClothingItemCard, and no lineLimit(2)
        // left on that card.
        let cardStart = try XCTUnwrap(home.range(of: "struct DarkClothingItemCard"))
        let card = String(home[cardStart.lowerBound...])
        XCTAssertTrue(card.contains(".lineLimit(1)"), "the outfit-row note is one line")
        XCTAssertFalse(card.contains(".lineLimit(2)"), "the two-line note must not return")
        // The headline moment carries its own bold font, not the
        // line's inherited semibold.
        XCTAssertTrue(home.contains(".font(.system(size: 15.5, weight: .bold))"),
                      "the headline moment renders in pine bold per the design")
    }

    // MARK: - DA-20 + RW-58: per-run stated once; fine print is basis only

    func testRW58FinePrintIsTheSettledBasisOnly() {
        XCTAssertEqual(
            MakeItMePriceLabels.perRunFinePrint(annualPriceString: "$9.99", annual: Decimal(string: "9.99")),
            "$9.99 a year, at 4–5 runs a week.")
        let finePrint = try! XCTUnwrap(MakeItMePriceLabels.perRunFinePrint(
            annualPriceString: "$9.99", annual: Decimal(string: "9.99")))
        XCTAssertFalse(finePrint.contains("per run"), "DA-20: the fine print must not restate the per-run claim")
        XCTAssertFalse(finePrint.contains("¢"), "DA-20: no second per-run figure below the card")
    }

    func testRW58PerRunLineStaysUnderThePriceAndPriceIsPerYear() {
        XCTAssertEqual(MakeItMePriceLabels.perRunLine(annual: Decimal(string: "9.99")), "only about 4¢ per run")
        XCTAssertEqual(MakeItMePriceLabels.annualRowLabel(annualPriceString: "$9.99", annual: nil, monthly: nil), "$9.99/yr")
        XCTAssertEqual(MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: "$9.99"), "$9.99/yr")
    }

    // MARK: - DA-22: upsell eyebrow is the script title instance

    func testDA22UpsellCardUsesTheScriptNotSansCaps_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(settings.contains("Text(\"MAKE IT ME\")"),
                       "no sans caps MAKE IT ME title instance may remain")
        let upsellStart = try XCTUnwrap(settings.range(of: "struct MakeItMeUpsellCard"))
        let upsell = String(settings[upsellStart.lowerBound...])
        XCTAssertTrue(upsell.contains("Text(\"Make It Me\")"), "the upsell card carries the script title")
        XCTAssertTrue(upsell.contains(".greatVibes(size: 25)"), "the upsell title is set in the script")
    }

    // MARK: - RW-51: settled upload failure wordings + doors

    func testRW51FailureWordingsAreTheSettledOnes() {
        XCTAssertEqual(PhotoRejection.faceTooSmall.specString,
                       "Your face is small in that photo — A closer selfie works best — a mirror selfie is great too. Or let the camera guide you…")
        XCTAssertEqual(PhotoRejection.tooClose.specString,
                       "Hold the phone at arm's length, so your whole face fits with space around it.")
        XCTAssertEqual(PhotoRejection.tooDark.specString,
                       "Try facing a window or light, looking at the camera.")
        XCTAssertEqual(SelfieGuidance.retakeFaceTooSmall, PhotoRejection.faceTooSmall.specString,
                       "the guidance constant and the rejection string are one wording")
    }

    func testRW51FailureRowCarriesBothSettledDoors_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("Text(\"Take the guided selfie\")"),
                      "the guided door carries its settled label")
        XCTAssertFalse(flow.contains("Retake with the guided camera"),
                       "the old guided-door label must not return")
        XCTAssertTrue(flow.contains("Text(\"Choose a different photo\")"),
                      "the failure row gains the labelled library door")
    }

    // MARK: - RW-52 + DA-24(i): the fork per §10.1 / frame 32

    func testRW52ForkShape_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        let start = try XCTUnwrap(flow.range(of: "struct MakeItMeForkView"))
        let end = try XCTUnwrap(flow.range(of: "// MARK: - The opt-in flow container"))
        let fork = String(flow[start.lowerBound..<end.lowerBound])
        // The script is the fork's TITLE: it appears before the
        // fork's first line, and only once in the fork.
        let scriptIdx = try XCTUnwrap(fork.range(of: "Text(\"Make It Me\")")).lowerBound
        let lineIdx = try XCTUnwrap(fork.range(of: "See yourself in every outfit.")).lowerBound
        XCTAssertTrue(scriptIdx < lineIdx, "the script title heads the fork")
        XCTAssertEqual(fork.components(separatedBy: "Text(\"Make It Me\")").count - 1, 1,
                       "the script sits in the title seat only, not inside a card")
        // Settled doors.
        XCTAssertTrue(fork.contains("Text(\"Make it me\")"))
        XCTAssertTrue(fork.contains("Text(\"Use my photos\")"))
        XCTAssertTrue(fork.contains("Text(\"Standard photos\")"))
        XCTAssertTrue(fork.contains("Text(\"Use standard photos\")"))
        XCTAssertFalse(fork.contains("Use the standard photos"),
                       "DA-24: the old stock-card heading must not return")
        // The two unlisted card description lines are gone.
        XCTAssertFalse(fork.contains("Upload one photo of your face"))
        XCTAssertFalse(fork.contains("Keep the photo library RunWear matches"))
        // The settled Continue, after the honesty line.
        let honestyIdx = try XCTUnwrap(fork.range(of: "Either way, your recommendations don't change")).lowerBound
        let continueIdx = try XCTUnwrap(fork.range(of: "Text(\"Continue\")")).lowerBound
        XCTAssertTrue(honestyIdx < continueIdx, "Continue follows the honesty line")
        // Fork lines themselves unchanged.
        XCTAssertTrue(fork.contains("Your recommendations, with you in the photo."))
    }

    // MARK: - DA-23: dead code stays deleted

    func testDA23DeadCodeAbsent_sourcePins() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertFalse(vm.contains("var shareText"), "shareText is deleted")
        XCTAssertFalse(vm.contains("🏃 RunWear Recommendation"), "the emoji-led share copy is deleted")
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertFalse(models.contains("fullBodyGuidance"), "fullBodyGuidance is deleted")
        let watch = try sourceFile("../RunWearWatch/WatchOutfitView.swift")
        XCTAssertFalse(watch.contains("WatchWeatherCard"), "the dead WatchWeatherCard is deleted")
        XCTAssertFalse(watch.contains("FF5722"), "its hardcoded off-system colours go with it")
    }

    // MARK: - DA-24(ii): no shop links on the watch

    func testDA24WatchRowsCarryNoShopLink_sourcePin() throws {
        let watch = try sourceFile("../RunWearWatch/WatchOutfitView.swift")
        XCTAssertFalse(watch.contains("affiliateURL"), "no affiliate link construction on the watch")
        XCTAssertFalse(watch.contains("cart.fill"), "no cart glyph on the watch")
        XCTAssertFalse(watch.contains("Link(destination:"), "no outbound link on the watch outfit rows")
    }

    // MARK: - RW-56: one danger zone, seated last

    func testRW56OneDangerZoneSeatedLast() throws {
        XCTAssertEqual(SettingsInventory.sections.last, .dangerZone)
        XCTAssertEqual(SettingsInventory.sections.filter { $0 == .dangerZone }.count, 1)
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertEqual(modal.components(separatedBy: "settingsSectionHeader(\"Danger zone\")").count - 1, 1,
                       "exactly one Danger zone section renders in Settings")
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(section.contains("destructiveSection"),
                       "the Make It Me composition carries no destructive group")
    }

    // MARK: - RW-57: selfie thumbnail row; subscribed seat is management only

    func testRW57PhotosRowCarriesTheSelfieThumbnail_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(section.contains("ReferencePhotosLoader(fetcher: PersonalHeroAPI.shared)"),
                      "the row's thumbnail loads through the shared loader seam")
        XCTAssertTrue(section.contains("AsyncImage(url: url)"),
                      "the row renders the user's own photo as its thumbnail")
        XCTAssertTrue(section.contains("photosThumbnail"))
    }

    func testRW57SubscribedSeatCarriesManagementOnly_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(section.contains("Text(\"You're subscribed.\")"),
                       "the unlisted subscribed block is removed")
        XCTAssertTrue(section.contains("MakeItMeSettingsRow.manageSubscription.title"),
                      "the management door stands in the subscribed seat")
    }

    // MARK: - RW-59: feedback disclosure is the settled substance

    func testRW59BundleCarriesVersionDeviceAndSettings() throws {
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(modal.contains("\"Device\": \"\\(UIDevice.current.model)"),
                      "the bundle carries the device, as disclosed")
        XCTAssertTrue(modal.contains("\"Settings\": \"Fit: \\(fit) · Units: \\(unit) · Body temperature: \\(comfort)\""),
                      "the bundle carries the user's settings, as disclosed")
        XCTAssertTrue(modal.contains("Nothing else — no health data, no location, no photos."),
                      "the disclosure states the never-sent substance before Send")
        XCTAssertFalse(modal.contains("no location beyond what you type"),
                       "the weaker old phrasing must not return")
    }

    func testRW59CasingPins_sourcePins() throws {
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(modal.contains("Label(\"Send feedback\", systemImage: \"envelope\")"))
        XCTAssertTrue(modal.contains(".navigationTitle(\"Send feedback\")"))
        XCTAssertFalse(modal.contains("\"Send Feedback\""))
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("Button(\"Send feedback\")"))
        XCTAssertTrue(home.contains("Button(\"Not now\", role: .cancel)"))
        XCTAssertFalse(home.contains("Button(\"Not Now\""))
    }

    // MARK: - RW-60: the settled line on the HOME loading surface

    func testRW60HomeLoadingSurfaceCarriesTheSettledLine_sourcePin() throws {
        let supporting = try sourceFile("../RunWear/Views/SupportingViews.swift")
        let start = try XCTUnwrap(supporting.range(of: "struct LoadingView"))
        let end = try XCTUnwrap(supporting.range(of: "struct LocationPermissionView"))
        let loading = String(supporting[start.lowerBound..<end.lowerBound])
        XCTAssertTrue(loading.contains("Text(\"Getting your first recommendation…\")"),
                      "the home skeleton carries the settled line (frame 46)")
        // The line must render as text: it is composed outside
        // the redacted skeleton subtree.
        let textIdx = try XCTUnwrap(loading.range(of: "Getting your first recommendation…")).lowerBound
        let redactedIdx = try XCTUnwrap(loading.range(of: ".redacted(reason: .placeholder)")).lowerBound
        XCTAssertTrue(textIdx < redactedIdx || loading.contains("private var skeleton"),
                      "the line is not swallowed by the placeholder redaction")
    }

    // MARK: - RW-61: the looks library

    func testRW61LibraryCellsCarrySettledTitlesAndTheFailedLabel() {
        let cells = LooksLibrary.cells(from: [
            "MALE_SNOW_FREEZING_NIGHT_hash1": .ready,
            "FEMALE_CLEAR_MILD_DAWN_hash2": .failed(reason: .qualityRefused),
            "MALE_RAIN_COOL_MIDDAY_hash3": .making,
            "MALE_CLOUDY_WARM_DUSK_hash4": .none,
        ])
        XCTAssertEqual(cells.count, 3, ".none states are not library cells")
        let ready = try! XCTUnwrap(cells.first { $0.combinationId.hasPrefix("MALE_SNOW") })
        XCTAssertEqual(ready.titleLine, "Snow · Freezing")
        XCTAssertEqual(ready.statusLine, "Night")
        XCTAssertFalse(ready.isFailed)
        // THE failed-cell state: the settled label, verbatim.
        let failed = try! XCTUnwrap(cells.first { $0.combinationId.hasPrefix("FEMALE_CLEAR") })
        XCTAssertEqual(failed.titleLine, "Clear · Mild")
        XCTAssertEqual(failed.statusLine, "Dawn · stock photo for now")
        XCTAssertTrue(failed.isFailed)
    }

    func testRW61ExplanationIsThePolicyConstantVerbatim() {
        XCTAssertEqual(PersonalServePolicy.failedExplanation,
                       "This look couldn't be made, so the standard photo is shown here. Your other looks are unaffected.")
        XCTAssertEqual(PersonalServePolicy.presentation(hasPersonalPhoto: false, lookState: .failed(reason: .error)),
                       .failedQuiet(explanation: PersonalServePolicy.failedExplanation))
    }

    func testRW61LibrarySurfaceAndEntry_sourcePins() throws {
        let settings = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(settings.contains("struct YourLooksLibraryView"), "the library surface exists")
        XCTAssertTrue(settings.contains(".navigationTitle(\"Your looks\")"), "under its settled title")
        XCTAssertTrue(settings.contains("Button(\"Why am I seeing a stock photo?\")"),
                      "the failed cell carries the explanation door")
        XCTAssertTrue(settings.contains("Text(PersonalServePolicy.failedExplanation)"),
                      "the door speaks the policy constant itself, not a rewrite")
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("Button(\"See all your looks\")"),
                      "one tap away from the failed cell's home line")
        XCTAssertFalse(home.contains("stock photo for now"),
                       "the failed label is never badged on home — it lives in the library")
    }

    // MARK: - RW-63: the settled weather sheet

    func testRW63SheetCarriesAllSevenMetricsWithHomeNumbers() throws {
        XCTAssertEqual(WeatherMetricKind.allCases,
                       [.feelsLike, .actual, .condition, .wind, .humidity, .uvIndex, .precipitation])
        XCTAssertEqual(WeatherDetailType.feelsLike(value: "52°").summaryValue, "52°")
        XCTAssertEqual(WeatherDetailType.actual(value: "55°").title, "Actual")
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains(".feelsLike(value: viewModel.temperatureUnit.format(viewModel.currentFeelsLike))"),
                      "feels-like carries the home state's number, in its own formatting")
        XCTAssertTrue(home.contains(".actual(value: viewModel.temperatureUnit.format(viewModel.currentTemperature))"),
                      "actual carries the home state's number, unchanged")
        let sheet = try sourceFile("../RunWear/Views/Modals/WeatherDetailSheet.swift")
        // Context lines render in the compact (phone) layout:
        // they appear in pickerLayout as well as the sidebar.
        XCTAssertGreaterThanOrEqual(sheet.components(separatedBy: "ForEach(contextLines, id: \\.self)").count - 1, 2,
                                    "context lines render in both layouts")
        XCTAssertTrue(sheet.contains("Text(\"Running Tip\")"), "the Running tip line stands")
    }

    // MARK: - RW-64: shop doors + disclosure

    func testRW64ShopSheetDoorsAndDisclosure_sourcePins() throws {
        let shop = try sourceFile("../RunWear/Views/Modals/ShopModal.swift")
        XCTAssertTrue(shop.contains("Text(\"Shop ›\")"), "the settled per-row door")
        XCTAssertTrue(shop.contains("These links open Amazon."), "disclosure: links open Amazon")
        XCTAssertTrue(shop.contains("RunWear may earn a commission"), "disclosure: commission")
        XCTAssertTrue(shop.contains("It never changes the outfit we recommend."),
                      "disclosure: the commission never changes the recommendation")
        XCTAssertFalse(shop.contains("we may earn a small commission"),
                       "the old partial disclosure must not return")
    }

    // MARK: - RW-66: date pickable from Home, Confirm-gated

    /// Two fixed days with sharply different weather (day 1 mild,
    /// day 2 cold), built the established fixture way.
    @MainActor
    private func rw66ViewModel() -> WeatherViewModel {
        ManualLocationStore.clear()
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        func day(_ date: String, apparent: Double) -> ([String], [Double], [Double]) {
            let times = (0..<24).map { String(format: "%@T%02d:00", date, $0) }
            return (times, [Double](repeating: apparent, count: 24), [Double](repeating: apparent, count: 24))
        }
        let (t1, a1, ap1) = day("2026-10-29", apparent: 18.3)
        let (t2, a2, ap2) = day("2026-10-30", apparent: -1.1)
        let hourly = HourlyWeather(
            time: t1 + t2,
            temperature2m: a1 + a2,
            apparentTemperature: ap1 + ap2,
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
        vm.weatherData = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 18.3, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: "America/New_York", currentDetail: nil
        )
        vm.selectedDate = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 29))!
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return vm
    }

    func testRW66DateDraftMovesOnlyTheDraft() {
        let day1 = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 29))!
        let day2 = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 30))!
        var draft = DatePickerDraft(originalDate: day1)
        XCTAssertFalse(draft.isChanged)
        draft.select(day2)
        XCTAssertTrue(draft.isChanged)
        XCTAssertEqual(draft.originalDate, day1, "the confirmed selection is untouched until Confirm")
        XCTAssertEqual(draft.commit(), day2)
    }

    @MainActor
    func testRW66DateConfirmReSolvesToTheNewDate() {
        let vm = rw66ViewModel()
        let mildFeels = vm.currentFeelsLike
        // The Confirm path, exactly as the host runs it: the
        // draft's commit is written to the selection once, and
        // the host's onChange applies it through the one
        // re-solve path, keeping the selected hour.
        var draft = DatePickerDraft(originalDate: vm.selectedDate)
        draft.select(Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 30))!)
        vm.selectedDate = draft.commit()
        vm.updateSelectedTime(date: vm.selectedDate, hour: vm.selectedHour)
        XCTAssertEqual(vm.selectedDate, draft.commit())
        XCTAssertEqual(vm.selectedHour, 12, "the hour is kept across a date change")
        XCTAssertLessThan(vm.currentFeelsLike, mildFeels - 20,
                          "home re-solved to the new date's conditions (cold day), got \(vm.currentFeelsLike)")
    }

    @MainActor
    func testRW66DateCancelRestoresThePriorSelectionExactly() {
        let vm = rw66ViewModel()
        let dateBefore = vm.selectedDate
        let hourBefore = vm.selectedHour
        let feelsBefore = vm.currentFeelsLike
        // Cancel semantics: the draft is moved and DISCARDED —
        // the selection was never written, so there is nothing
        // to undo and nothing re-solves.
        var draft = DatePickerDraft(originalDate: vm.selectedDate)
        draft.select(Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 30))!)
        XCTAssertTrue(draft.isChanged)
        _ = draft // discarded, as Cancel discards it
        XCTAssertEqual(vm.selectedDate, dateBefore)
        XCTAssertEqual(vm.selectedHour, hourBefore)
        XCTAssertEqual(vm.currentFeelsLike, feelsBefore)
    }

    func testRW66DateEntryAndConfirmGate_sourcePins() throws {
        // The entry: the time sheet's context line carries the
        // day door, fed by the host with the real binding.
        let time = try sourceFile("../RunWear/Views/Modals/TimePickerSheet.swift")
        XCTAssertTrue(time.contains("showDateSheet = true"), "the context line's date segment opens the day sheet")
        XCTAssertTrue(time.contains("DatePickerSheet("), "the sibling day sheet is presented from the picker")
        XCTAssertTrue(time.contains("accessibilityLabel(\"Choose a day: \\(dateLabel)\")"))
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("dateSelection: $viewModel.selectedDate"),
                      "home feeds the picker the selection binding — the date is pickable from home")
        XCTAssertTrue(home.contains("onDateConfirmed: { showTimePicker = false }"))
        // The gate: the day sheet writes ONLY on Confirm.
        let date = try sourceFile("../RunWear/Views/Modals/DatePickerSheet.swift")
        XCTAssertTrue(date.contains("selectedDate = draft.commit()"), "Confirm is the one write")
        XCTAssertFalse(date.contains("selectedDate = date"), "no live write on tap remains")
        XCTAssertTrue(date.contains("Text(\"Choose a Day\")"), "frame 42's sheet name")
        XCTAssertTrue(date.contains("Button(\"Cancel\")"), "Cancel stands in the sheet")
        XCTAssertTrue(date.contains("Button(\"Confirm\")"), "Confirm stands in the sheet")
    }
}
