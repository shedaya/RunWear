import XCTest
import UIKit

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
        // RW-78 (Nov 6, 2026) SUPERSEDES the RW-52 commit model
        // this pin originally asserted (a settled "Continue"
        // below the honesty line committing a selected door):
        // Sol's device evidence settled against select-then-
        // commit — the doors act on tap, gen-5's wiring, and no
        // Continue exists on the fork. The honesty line stands,
        // now as the fork's closing line. (The full RW-78 wiring
        // pins live in Gen6RW78RW79Tests.)
        XCTAssertTrue(fork.contains("Either way, your recommendations don't change"))
        XCTAssertFalse(fork.contains("Text(\"Continue\")"),
                       "RW-78: the select-then-commit Continue must not return")
        XCTAssertFalse(fork.contains("selectedDoor"),
                       "RW-78: the fork carries no selection state")
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
        let file = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        // GEN-6 ASSEMBLY reconciliation: this pin guards the SETTINGS
        // seat (MakeItMeSettingsSection). The paywall sheet
        // (MakeItMeSubscriptionSheet, later in the same file)
        // legitimately carries a plain "You're subscribed." fallback
        // in its subscribed branch — Wave A's DA-11 coverage gating —
        // so the absence assertion is scoped to the settings section,
        // the seat RW-57 governs. The block returning to THIS seat
        // fails the pin exactly as before.
        let sectionStart = try XCTUnwrap(file.range(of: "struct MakeItMeSettingsSection"))
        let sectionEnd = try XCTUnwrap(file.range(of: "private struct MakeItMePremiumOfferCard"))
        let section = String(file[sectionStart.lowerBound..<sectionEnd.lowerBound])
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
        // RW-92 (Nov 6, 2026): the confirm hook now ALSO re-solves
        // home (updateSelectedTime) — the nested date picker's
        // write otherwise never reached the recommendation. The
        // dismissal this pin guards still stands as the hook's
        // first act; the re-solve is pinned by
        // Gen6FixWaveTests.testRW92_NestedDateConfirmReSolves.
        XCTAssertTrue(home.contains("onDateConfirmed: {"))
        XCTAssertTrue(home.contains("showTimePicker = false"))
        // The gate: the day sheet writes ONLY on Confirm.
        let date = try sourceFile("../RunWear/Views/Modals/DatePickerSheet.swift")
        XCTAssertTrue(date.contains("selectedDate = draft.commit()"), "Confirm is the one write")
        XCTAssertFalse(date.contains("selectedDate = date"), "no live write on tap remains")
        XCTAssertTrue(date.contains("Text(\"Choose a Day\")"), "frame 42's sheet name")
        XCTAssertTrue(date.contains("Button(\"Cancel\")"), "Cancel stands in the sheet")
        XCTAssertTrue(date.contains("Button(\"Confirm\")"), "Confirm stands in the sheet")
    }
}

// MARK: - GEN-6 Wave C scope addition — DA-09 / RW-42 narration discipline (iOS)
//
// The device evidence is Android (Sol's gen-5 recording: six
// announcements in one session, including a SECOND "Added
// Miami, FL, Now" fired on relaunch for an entry added earlier
// in the same session, and a bar announcing "Update to Miami,
// FL, Now" over a stale card matching neither adjacent
// recommendation). The owner directed the same discipline on
// iOS under the wave's parity rules.
//
// The iOS mechanism, diagnosed in this lane: Wear I've Run
// entries are keyed by SESSION, and a relaunch mints a new
// session identity — so home's automatic re-settle after
// relaunch (onAppear / landing triggers, no user action) found
// no entry under the new identity, classified the settle
// .created, bumped the cue counter, and the bar fired "Added"
// again for a day+place entry the store already held. These
// tests script that sequence at the model level: an
// "announcement" is a cue-counter bump, and the announced line
// is the narration derived from the presented entry at that
// moment — the same derivation the dock renders.
final class Gen6WaveCDA09Tests: XCTestCase {

    private let day = Date(timeIntervalSince1970: 1_762_560_000)

    private func entry(
        session: String,
        place: String,
        feels: Double,
        items: [String],
        moment: MomentReading = .now,
        hero: String = "hero-1"
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: day,
            placeName: place,
            feelsLikeF: feels,
            actualF: feels + 2,
            condition: "Clear",
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: hero,
            sessionID: session,
            momentReading: moment
        )
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

    /// The production derivation (WeatherOutfitView's dock):
    /// state, kind, and moment all read off the ONE presented
    /// entry — the Y-5 shape the atomicity test below relies on.
    @MainActor
    private func state(of model: WearIveRunCollectionModel) -> WearIveRunState {
        let presented = model.sessionEntry()
        return WearIveRunMiniBarModel.state(
            todaysEntry: presented,
            moment: presented?.momentReading ?? .now,
            wasUpdatedThisSession: presented?.wasUpdatedInSession ?? false
        )
    }

    @MainActor
    private func announcedLine(of model: WearIveRunCollectionModel) -> String? {
        WearIveRunNarration.line(for: state(of: model))
    }

    /// Record one settle and, if it ANNOUNCED (the cue counter
    /// moved — the only path by which the element cues), append
    /// the line the bar announced to the sequence.
    @MainActor
    private func settle(
        _ model: WearIveRunCollectionModel,
        _ entry: WearIveRunEntry,
        announcements: inout [String]
    ) {
        let cuesBefore = model.cueCount
        model.recordSettled(entry)
        if model.cueCount > cuesBefore, let line = announcedLine(of: model) {
            announcements.append(line)
        }
    }

    // MARK: The recorded scenario, scripted end to end

    @MainActor
    func testDA09RecordedSequence_RelaunchRestoreFiresNoDuplicateAdded() {
        let defaults = UserDefaults(suiteName: "gen6-da09-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        var announcements: [String] = []

        // Session 1, live flow: add place A, move to place B,
        // then a moment update — three real changes, three
        // announcements (the recording's conforming forms).
        let first = WearIveRunCollectionModel(store: store, sessionID: "s1")
        settle(first, entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"]), announcements: &announcements)
        settle(first, entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"]), announcements: &announcements)
        settle(first, entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"],
            moment: .clock(hour12: 7, isPM: true)), announcements: &announcements)
        XCTAssertEqual(announcements, [
            "Added Overland Park, KS, Now",
            "Updated to Miami, FL",
            "Updated to 7 p.m."
        ])

        // RELAUNCH: a new process over the same persisted
        // store — a new session identity, a fresh model.
        // Rehydration alone fires nothing.
        let relaunched = WearIveRunCollectionModel(store: store, sessionID: "s2")
        XCTAssertEqual(relaunched.cueCount, 0, "rehydration must not announce")

        // Home's automatic re-settle of the SAME persisted
        // state (onAppear / landing triggers — no user action)
        // is a RESTORE, not a creation: no cue, no second
        // "Added Miami". RW-91: the new session's save JOINS
        // the prior session's in the collection (saves
        // accumulate, one per session), carrying the narration
        // kind and payload the entry already had.
        settle(relaunched, entry(
            session: "s2", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"],
            moment: .clock(hour12: 7, isPM: true)), announcements: &announcements)
        XCTAssertEqual(relaunched.cueCount, 0, "a restore must not cue")
        XCTAssertEqual(announcements.count, 3, "no duplicate Added on relaunch")
        XCTAssertEqual(relaunched.entries.count, 2,
                       "RW-91: saves accumulate — the prior session's save persists beside the restored session's (Sol's Nov 6, 2026 correction supersedes the gen-6 supersede rule)")
        XCTAssertEqual(relaunched.sessionEntry()?.sessionID, "s2")
        XCTAssertEqual(relaunched.sessionEntry()?.wasUpdatedInSession, true,
                       "the restored entry keeps the narration kind it already carried")
        XCTAssertEqual(state(of: relaunched), .updated(payload: .moment(.clock(hour12: 7, isPM: true))))

        // A weather-only re-settle after the restore is silent,
        // as it is in-session.
        settle(relaunched, entry(
            session: "s2", place: "Miami, FL", feels: 92,
            items: ["Tank Top", "Shorts"],
            moment: .clock(hour12: 7, isPM: true)), announcements: &announcements)
        XCTAssertEqual(announcements.count, 3)

        // A REAL change after the restore still announces —
        // once, and as an Update (the session already has an
        // entry), never as a fresh Added.
        settle(relaunched, entry(
            session: "s2", place: "New York, NY", feels: 72,
            items: ["Tee", "Shorts"]), announcements: &announcements)
        XCTAssertEqual(announcements, [
            "Added Overland Park, KS, Now",
            "Updated to Miami, FL",
            "Updated to 7 p.m.",
            "Updated to New York, NY"
        ])
        XCTAssertEqual(relaunched.entries.count, 2,
                       "RW-91: s1's save + s2's one in-place entry — two saves, one per session")
    }

    @MainActor
    func testDA09RestoreOfACreationEntry_KeepsItsAddedFormSilently() {
        let defaults = UserDefaults(suiteName: "gen6-da09-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        var announcements: [String] = []

        let first = WearIveRunCollectionModel(store: store, sessionID: "s1")
        settle(first, entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"]), announcements: &announcements)
        XCTAssertEqual(announcements, ["Added Miami, FL, Now"])

        // Relaunch + automatic re-settle of the identical
        // state: silent restore; the bar's resting state is the
        // Added form the entry already carried (presented via
        // entryAlreadyPresent, at MINI — not re-announced).
        let relaunched = WearIveRunCollectionModel(store: store, sessionID: "s2")
        settle(relaunched, entry(
            session: "s2", place: "Miami, FL", feels: 90,
            items: ["Tank Top"]), announcements: &announcements)
        XCTAssertEqual(relaunched.cueCount, 0)
        XCTAssertEqual(announcements, ["Added Miami, FL, Now"])
        XCTAssertEqual(relaunched.entries.count, 2,
                       "RW-91: the restored session's save joins the prior session's — the collection accumulates")
        XCTAssertEqual(state(of: relaunched), .added(place: "Miami, FL", moment: .now))
    }

    @MainActor
    func testDA09RestoreIsScopedToTheSameDayAndPlace() {
        // A relaunched session whose first settle is at a place
        // with NO entry for today is a genuine creation — it
        // announces Added, once, and the prior session's entry
        // at the other place stands as history. Restore silence
        // is not a blanket first-settle silence.
        let defaults = UserDefaults(suiteName: "gen6-da09-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        var announcements: [String] = []

        let first = WearIveRunCollectionModel(store: store, sessionID: "s1")
        settle(first, entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"]), announcements: &announcements)

        let relaunched = WearIveRunCollectionModel(store: store, sessionID: "s2")
        settle(relaunched, entry(
            session: "s2", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve"]), announcements: &announcements)
        XCTAssertEqual(relaunched.cueCount, 1)
        XCTAssertEqual(announcements, [
            "Added Miami, FL, Now",
            "Added Overland Park, KS, Now"
        ])
        XCTAssertEqual(store.entries.count, 2, "the prior session's Miami entry is untouched history")
    }

    @MainActor
    func testDA09LegacyEntryRestoresSilentlyOnFirstSessionSettle() {
        // The legacy (pre-session) form of the same shape —
        // FA-36's supersede, now also silent: build 25's legacy
        // entry for today + this place is restored, not
        // re-announced as a fresh Added.
        let defaults = UserDefaults(suiteName: "gen6-da09-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        store.record(entry(session: "", place: "Miami, FL", feels: 90, items: ["Tank Top"]))

        let model = WearIveRunCollectionModel(store: store, sessionID: "s-new")
        var announcements: [String] = []
        settle(model, entry(
            session: "s-new", place: "Miami, FL", feels: 90,
            items: ["Tank Top"]), announcements: &announcements)
        XCTAssertEqual(model.cueCount, 0)
        XCTAssertTrue(announcements.isEmpty)
        XCTAssertEqual(model.entries.count, 1, "the day keeps ONE Miami entry — the session's")
        XCTAssertEqual(model.sessionEntry()?.sessionID, "s-new")
    }

    // MARK: Atomicity — the announced selection IS the rendered card

    @MainActor
    func testDA09BarAndCardAreTheSameSolvedSelectionAfterAnUpdate() {
        let defaults = UserDefaults(suiteName: "gen6-da09-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s1")

        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"], hero: "hero-overland"))
        // The update lands as ONE solved settle (the recording
        // seam now refuses partial, in-flight assemblies — see
        // the source pins below).
        let miami = entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"], hero: "hero-miami")
        model.recordSettled(miami)
        XCTAssertEqual(model.cueCount, 2, "the update announced exactly once")

        // Read the persisted value back ONCE: the bar's state
        // and the card's line both derive from this single
        // value, so neither can carry a facet of the outgoing
        // settle (the DA-09 stale card: an 85° / 3-item outfit
        // under a Miami announcement).
        guard let settled = model.sessionEntry() else {
            XCTFail("the session's entry must be presented after its update settle")
            return
        }
        XCTAssertEqual(
            WearIveRunStore(defaults: defaults).entries, [settled],
            "one persisted value — no second read for a stale facet to come from")
        XCTAssertEqual(settled.placeDisplayName, miami.placeDisplayName)
        XCTAssertEqual(settled.feelsLikeF, miami.feelsLikeF)
        XCTAssertEqual(settled.itemNames, miami.itemNames)
        XCTAssertEqual(settled.heroImageReference, miami.heroImageReference)

        // The bar announces THIS selection…
        let barState = state(of: model)
        XCTAssertEqual(barState, .updated(payload: .place("Miami, FL")))
        XCTAssertEqual(WearIveRunNarration.line(for: barState), "Updated to Miami, FL")

        // …and the expanded card renders THIS selection — same
        // place, same feels-like, same items, from the same
        // entry value the bar derived from.
        let cardLine = WearIveRunNarration.expandedLine(
            for: settled,
            formattedFeelsLike: "\(Int(settled.feelsLikeF))°")
        XCTAssertEqual(cardLine, "Miami, FL · Today — 90° feels like · Tank Top · Shorts")
    }

    // MARK: The recording seam — no settle from a partial selection

    func testDA09RecordingSeamRefusesInFlightSettles_sourcePins() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("guard !viewModel.isLoading else { return }"),
                      "DA-09: no entry is recorded from an in-flight fetch — the place can be named before its weather lands")
        XCTAssertTrue(home.contains(".onChange(of: viewModel.isLoading)"),
                      "DA-09: the fetch-completion re-offer records the one solved selection")
        // The manual switch marks its fetch in flight in the
        // same instant it names the place — no unmarked window.
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        let start = try XCTUnwrap(vm.range(of: "func setManualLocation"))
        let rest = vm[start.upperBound..<vm.endIndex]
        let end = rest.range(of: "\n    func ")?.lowerBound ?? vm.endIndex
        let body = String(vm[start.lowerBound..<end])
        let loading = try XCTUnwrap(body.range(of: "isLoading = true"))
        let fetch = try XCTUnwrap(body.range(of: "await fetchWeather"))
        XCTAssertTrue(loading.lowerBound < fetch.lowerBound,
                      "DA-09: isLoading is set synchronously, before the fetch task")
        // The restore rule itself stands in the collection model.
        let miniBar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(miniBar.contains("RESTORE, not creation"),
                      "DA-09: the model's restore branch is the firing-discipline seam")
    }
}

// MARK: - GEN-6 FIX WAVE (Sol's build-28 walkthrough, Nov 6, 2026)
//
// The iOS fix wave's pins: RW-83 … RW-105. Behavioural pins for
// the pure seams; source pins where the defect lives in view
// composition (the studio rule: the pin names the mechanism, so
// a regression has to remove the mechanism to pass).

final class Gen6FixWaveTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func utcDay(_ year: Int, _ month: Int, _ day: Int) -> Date {
        utcCalendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    // MARK: RW-88 — the strip follows the viewed place/moment

    func testRW88_ViewedNarrationNamesTheChangedConditionInFull() throws {
        let now = utcDay(2026, 11, 6)
        let miami = try XCTUnwrap(SimplePlace(city: "Miami", region: "FL"))
        let newYork = try XCTUnwrap(SimplePlace(city: "New York", region: "NY"))

        // Place change at Now: "Miami, FL, Now" (Sol's example).
        XCTAssertEqual(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: miami, viewedDate: now, viewedHour: 12,
                isViewingNow: true, calendar: utcCalendar, now: now),
            "Miami, FL, Now")
        // Moment change to another day: "November 12 7 p.m."
        // (Sol's example — the date in full, no place).
        XCTAssertEqual(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: newYork, viewedDate: utcDay(2026, 11, 12), viewedHour: 19,
                isViewingNow: false, calendar: utcCalendar, now: now),
            "November 12 7 p.m.")
        // Moment change within today: the clock reading only.
        XCTAssertEqual(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: newYork, viewedDate: now, viewedHour: 19,
                isViewingNow: false, calendar: utcCalendar, now: now),
            "7 p.m.")
        // Both changed: place and the full moment together.
        XCTAssertEqual(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: miami, viewedDate: utcDay(2026, 11, 12), viewedHour: 19,
                isViewingNow: false, calendar: utcCalendar, now: now),
            "Miami, FL, November 12 7 p.m.")
        // Back at the entry's own place at Now: no override —
        // the entry's own narration stands (the Now line restored).
        XCTAssertNil(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: newYork, viewedDate: now, viewedHour: 12,
                isViewingNow: true, calendar: utcCalendar, now: now))
        // A picked hour the entry already carries: no override.
        XCTAssertNil(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY",
                entryMoment: .clock(hour12: 7, isPM: true),
                viewedPlace: newYork, viewedDate: now, viewedHour: 19,
                isViewingNow: false, calendar: utcCalendar, now: now))
    }

    // MARK: RW-93 — the launch gate waits for a resolved status
    //
    // Source pins, not a truth table: LocationService.swift is a
    // member of the app target only (the test target compiles a
    // curated source subset), so the seam cannot be called from
    // here. The pins name the exact mechanism so a regression has
    // to remove it to pass.

    func testRW93_PermissionGateRequiresAResolvedStatus_sourcePins() throws {
        let service = try sourceFile("../RunWear/Services/LocationService.swift")
        XCTAssertTrue(service.contains("@Published private(set) var hasResolvedAuthorization"),
                      "the service publishes whether the authorization reading has resolved")
        XCTAssertTrue(service.contains("enum LaunchGateResolution"),
                      "the gate decision lives in the named pure seam")
        XCTAssertTrue(service.contains("status == .notDetermined && hasResolvedAuthorization && !hasManualWeather"),
                      "the rule: gate only for a RESOLVED .notDetermined with no manual weather")
        let content = try sourceFile("../RunWear/Views/ContentView.swift")
        XCTAssertTrue(content.contains("LaunchGateResolution.showsPermissionGate("),
                      "ContentView consults the seam before raising the gate")
    }

    // MARK: RW-102 — the date picker offers no beyond-forecast day

    func testRW102_DatePickerStopsAtTheForecastHorizon() {
        XCTAssertEqual(DatePickerSheet.browseDays, DatePickerSheet.forecastHorizonDays,
                       "Sol's directive: the picker must not offer dates it cannot project for")
        for index in 0..<DatePickerSheet.browseDays {
            XCTAssertFalse(DatePickerSheet.isBeyondForecast(dayIndex: index),
                           "no offered day may be a beyond-forecast day")
        }
    }

    // MARK: RW-85 — the curve and dot clear the NOW label band

    func testRW85_WarmestPointClearsTheNowLabel() {
        let points = (0..<24).map { hour in
            DayChartHour(
                hour: hour,
                feelsLikeF: hour == 14 ? 80 : 50,
                bracket: .mild,
                outfitItemNames: ["Tee"]
            )
        }
        let model = DayChartModel.build(points: points)!
        for height: CGFloat in [120, 168] {
            let y = DayChartGeometry.y(forFeelsLike: 80, model: model, height: height)
            XCTAssertGreaterThanOrEqual(y, DayChartGeometry.topAnnotationBand)
            // The selected dot (11 pt + 2 pt stroke ≈ 7.5 pt
            // radius) centred on the warmest point must clear
            // the label band (y 0–10).
            XCTAssertGreaterThan(y - 7.5, 10,
                                 "the dot's top edge stays below the NOW label at height \(height)")
        }
    }

    // MARK: RW-100 — the confirm button names plan + price

    func testRW100_ConfirmLabelNamesTheSelectedPlanAndPrice() {
        XCTAssertEqual(
            MakeItMePriceLabels.confirmLabel(
                plan: .annual, annualPriceString: "$9.99", monthlyPriceString: "$0.99"),
            "Get Premium — Annual, $9.99/yr")
        XCTAssertEqual(
            MakeItMePriceLabels.confirmLabel(
                plan: .monthly, annualPriceString: "$9.99", monthlyPriceString: "$0.99"),
            "Get Premium — Monthly, $0.99/month")
        XCTAssertEqual(
            MakeItMePriceLabels.confirmLabel(
                plan: .annual, annualPriceString: nil, monthlyPriceString: nil),
            "Get Premium — Annual",
            "an unresolved price degrades to the plan name, never a wrong price")
    }

    // MARK: RW-105 — the chip palette reads at full strength

    private func whiteContrast(onHex hex: String) -> Double {
        let value = UInt64(hex, radix: 16)!
        let channels = [Double((value >> 16) & 0xFF), Double((value >> 8) & 0xFF), Double(value & 0xFF)]
            .map { $0 / 255 }
            .map { $0 <= 0.03928 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        let luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        return 1.05 / (luminance + 0.05)
    }

    func testRW105_ChipPaletteCarriesWhiteTextAtFullStrength() {
        let chips = [
            AppTheme.tempChipFreezingHex, AppTheme.tempChipColdHex,
            AppTheme.tempChipCoolHex, AppTheme.tempChipMildHex,
            AppTheme.tempChipWarmHex, AppTheme.tempChipHotHex,
        ]
        for hex in chips {
            XCTAssertGreaterThanOrEqual(whiteContrast(onHex: hex), 4.5,
                                        "white text on chip #\(hex) must meet the 4.5:1 floor")
        }
        XCTAssertEqual(AppTheme.tempChipMildHex, "0A7350",
                       "the mild chip is the deepened variant of the pinned mild data colour")
        // The DATA palette is untouched (chart bands/tints keep
        // the pinned bright set).
        XCTAssertEqual(AppTheme.tempMildHex, "10B981")
    }

    func testRW105_SurfacesUseTheStrongGreen_sourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertFalse(hero.contains("AppTheme.pineAccentDark"),
                       "the hero pill carries no fixed-accent green text (RW-105)")
        let outfit = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(outfit.contains("AppTheme.temperatureChipColor(fahrenheit: temperatureForBracket)"),
                      "the bracket badge fills with the deepened chip colour at full opacity")
    }

    // MARK: RW-83 / RW-84 — the Settings inventory is whole

    func testRW83_TemperatureConsequenceLineStandsInSettings_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("Used for today's outfit, the hourly chart, and your watch."),
                      "the settled DS §6/§13 consequence line renders under the Temperature Unit row")
    }

    func testRW84_NumberOfPhotosRowIsInTheInventory() throws {
        XCTAssertEqual(MakeItMeSettingsRow.numberOfPhotos.title, "Number of photos")
        XCTAssertTrue(MakeItMeSettingsRow.allCases.contains(.numberOfPhotos))
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(section.contains("MakeItMeSettingsRow.numberOfPhotos.title"),
                      "the photos section renders the DS §13 row")
    }

    // MARK: RW-86 — the slim bar's paint path carries no measurers

    func testRW86_MiniBarHasNoSelfMeasurementInItsPaintPath_sourcePins() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertFalse(bar.contains("miniHeight"),
                       "the measured mini endpoint (the blank-bar mechanism) is gone")
        XCTAssertFalse(bar.contains("morphContent(showsExpandedBlock: false"),
                       "no phantom mini measurer copy exists")
        XCTAssertTrue(bar.contains("machine.isFullExpression || expansionProgress > 0"),
                      "the single content copy mounts the expanded block only around the full expression")
    }

    // MARK: RW-89 / RW-92 / RW-95 / RW-101 — composition pins

    func testRW89_ResultsSitUnderTheFieldWhileSearching_sourcePins() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(sheet.contains("if searchText.count >= 2 {\n                    resultsSection"),
                      "search-active renders the results directly under the search field")
        XCTAssertTrue(sheet.contains("No places found"),
                      "a completed empty search says so in the results position")
    }

    func testRW92_NestedDateConfirmReSolves_sourcePin() throws {
        let outfit = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        let start = try XCTUnwrap(outfit.range(of: "onDateConfirmed: {"))
        let rest = outfit[start.lowerBound...]
        let end = try XCTUnwrap(rest.range(of: "\n                    }"))
        let closure = String(rest[..<end.lowerBound])
        XCTAssertTrue(closure.contains("viewModel.updateSelectedTime("),
                      "the time sheet's nested date confirm re-solves home (RW-92)")
    }

    func testRW95_StockStabilityMechanismStands_sourcePins() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("displayedStockServe"),
                      "the on-screen stock serve is tracked per cell")
        XCTAssertTrue(vm.contains("stockServeMemory"),
                      "resolved stock serves are remembered per cell for the next view")
        XCTAssertTrue(vm.contains("poolPaintSuppressedForStability"),
                      "a second stock resolution for a viewed cell is suppressed, not painted")
    }

    func testRW101_BandSitsDirectlyUnderTheHero_sourcePins() throws {
        let outfit = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertEqual(outfit.components(separatedBy: "RW-101 (Sol, Nov 6, 2026)").count - 1, 2,
                       "both home layouts carry the elevated band seat")
        let containerStart = try XCTUnwrap(outfit.range(of: "private func sheetContainer"))
        let slice = String(outfit[containerStart.lowerBound...].prefix(1600))
        let band = try XCTUnwrap(slice.range(of: "homeBandSection"))
        let outfitCall = try XCTUnwrap(slice.range(of: "outfit()"))
        XCTAssertTrue(band.lowerBound < outfitCall.lowerBound,
                      "in the stacked sheet the band precedes the outfit — directly under the hero")
    }

    // MARK: RW-94 — the pixel proof is keyed to identity, not URL

    func testRW94_LoadProofKeyedToHeroIdentity_sourcePins() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("firstLookLoadedHeroId == previewVM.currentPersonalHeroId"),
                      "the load proof compares hero identities — a refreshed signed URL cannot un-prove a loaded photo")
        XCTAssertFalse(onboarding.contains("firstLookLoadedImageURL"),
                       "the URL-string proof (the RW-94 stall mechanism) is removed")
        // The Y-1 invariant itself stands: the claim still waits
        // for loaded pixels in the resolver.
        let sequence = try sourceFile("../RunWear/Models/OnboardingSequence.swift")
        XCTAssertTrue(sequence.contains("personalPhotoDisplayed && personalImageLoaded"),
                      "Y-1 preserved: no finished claim before the photo is on screen")
    }
}

// MARK: - RW-106 — the remote hero loader (identity-keyed, retrying)
//
// The diagnosis (rw106-evidence/diagnosis.md): five counted
// looks, five delivered heroes, and the bundled fallback on
// screen in every count-pill frame — the AsyncImage layer hid on
// every re-signed URL, restarted the ~1 MB download each time,
// and never retried a failure. These pins cover the loader that
// replaced it. Fetchers are scripted; identities are unique per
// test so the shared caches cannot leak between tests.

private final class RW106FetchScript: @unchecked Sendable {
    private let lock = NSLock()
    private(set) var attempts = 0
    var handler: (Int, URL) async -> Result<Data, Error>
    init(handler: @escaping (Int, URL) async -> Result<Data, Error>) {
        self.handler = handler
    }
    func fetch(_ url: URL) async throws -> Data {
        lock.lock()
        attempts += 1
        let n = attempts
        lock.unlock()
        switch await handler(n, url) {
        case .success(let data): return data
        case .failure(let error): throw error
        }
    }
}

extension Gen6FixWaveTests {

    private func rw106FixturePNG(seed: CGFloat) -> Data {
        let size = CGSize(width: 12, height: 18)
        let image = UIGraphicsImageRenderer(size: size).image { ctx in
            UIColor(red: seed, green: 0.25, blue: 0.6, alpha: 1).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }
        return image.pngData()!
    }

    private func rw106SignedURL(token: String) -> String {
        "https://store.example/personal-heroes/user/hero-\(UUID().uuidString).png?token=\(token)"
    }

    @MainActor
    private func rw106WaitForPaint(_ loader: HeroRemoteImageLoader, timeout: TimeInterval = 3) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if loader.image != nil { return true }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        return loader.image != nil
    }

    func testRW106_IdentityStripsTheSignature() {
        let base = "https://store.example/personal-heroes/user/hero-1.png"
        XCTAssertEqual(
            HeroRemoteImageLoader.identity(for: "\(base)?token=AAA"),
            HeroRemoteImageLoader.identity(for: "\(base)?token=BBB"),
            "re-signed URLs for the same object share an identity")
        XCTAssertEqual(HeroRemoteImageLoader.identity(for: "\(base)?token=AAA"), base)
        XCTAssertNotEqual(
            HeroRemoteImageLoader.identity(for: "\(base)?token=AAA"),
            HeroRemoteImageLoader.identity(for: "https://store.example/personal-heroes/user/hero-2.png?token=AAA"),
            "different objects never share an identity")
    }

    @MainActor
    func testRW106_FailedFirstAttemptRetriesAndPaints() async {
        let png = rw106FixturePNG(seed: 0.9)
        let script = RW106FetchScript { n, _ in
            n == 1 ? .failure(HeroRemoteLoadError.undecodable) : .success(png)
        }
        let loader = HeroRemoteImageLoader(fetch: script.fetch, retryDelays: [0.02, 0.02, 0.02])
        let url = rw106SignedURL(token: "FIRST")
        loader.load(urlString: url)
        let painted = await rw106WaitForPaint(loader)
        XCTAssertTrue(painted, "a failed first attempt must retry and paint — never settle on the fallback in silence")
        XCTAssertEqual(script.attempts, 2)
        XCTAssertEqual(loader.paintedIdentity, HeroRemoteImageLoader.identity(for: url))
    }

    @MainActor
    func testRW106_ReResolutionSameIdentityNeverBlanksOrRefetches() async {
        let png = rw106FixturePNG(seed: 0.7)
        let script = RW106FetchScript { _, _ in .success(png) }
        let loader = HeroRemoteImageLoader(fetch: script.fetch, retryDelays: [0.02])
        let path = "https://store.example/personal-heroes/user/hero-\(UUID().uuidString).png"
        loader.load(urlString: "\(path)?token=AAA")
        let painted = await rw106WaitForPaint(loader)
        XCTAssertTrue(painted)
        let paintedImage = loader.image
        let attemptsAfterPaint = script.attempts
        // A re-resolution mints a fresh signature for the SAME photo.
        loader.load(urlString: "\(path)?token=BBB")
        XCTAssertEqual(script.attempts, attemptsAfterPaint,
                       "the same identity must not refetch — the bytes are already here")
        XCTAssertTrue(loader.image === paintedImage,
                      "the painted photo must not blank or swap on a re-resolution")
        XCTAssertEqual(loader.paintedIdentity, HeroRemoteImageLoader.identity(for: path))
    }

    @MainActor
    func testRW106_SecondLoaderPaintsFromCacheWithoutFetching() async {
        let png = rw106FixturePNG(seed: 0.5)
        let url = rw106SignedURL(token: "CACHED")
        let first = RW106FetchScript { _, _ in .success(png) }
        let loaderA = HeroRemoteImageLoader(fetch: first.fetch, retryDelays: [0.02])
        loaderA.load(urlString: url)
        let painted = await rw106WaitForPaint(loaderA)
        XCTAssertTrue(painted)
        // A fresh loader (a relaunch, a new view) for the same
        // identity paints from the identity-keyed cache — its
        // fetcher (which always fails) is never even called.
        let second = RW106FetchScript { _, _ in .failure(HeroRemoteLoadError.undecodable) }
        let loaderB = HeroRemoteImageLoader(fetch: second.fetch, retryDelays: [0.02])
        loaderB.load(urlString: url)
        XCTAssertNotNil(loaderB.image, "cached bytes paint synchronously on load")
        XCTAssertEqual(second.attempts, 0)
        XCTAssertEqual(loaderB.paintedIdentity, HeroRemoteImageLoader.identity(for: url))
    }

    @MainActor
    func testRW106_IdentityChangeDropsTheOldPaintUntilTheNewLands() async {
        let pngA = rw106FixturePNG(seed: 0.3)
        let pngB = rw106FixturePNG(seed: 0.95)
        let script = RW106FetchScript { _, url in
            if url.absoluteString.contains("slow-b") {
                try? await Task.sleep(nanoseconds: 300_000_000)
                return .success(pngB)
            }
            return .success(pngA)
        }
        let loader = HeroRemoteImageLoader(fetch: script.fetch, retryDelays: [0.02])
        let urlA = "https://store.example/personal-heroes/user/fast-a-\(UUID().uuidString).png?token=A"
        loader.load(urlString: urlA)
        let paintedA = await rw106WaitForPaint(loader)
        XCTAssertTrue(paintedA)
        let urlB = "https://store.example/personal-heroes/user/slow-b-\(UUID().uuidString).png?token=B"
        loader.load(urlString: urlB)
        XCTAssertNil(loader.image,
                     "a different photo never inherits the previous photo's paint (WS-1, per identity)")
        let paintedB = await rw106WaitForPaint(loader)
        XCTAssertTrue(paintedB)
        XCTAssertEqual(loader.paintedIdentity, HeroRemoteImageLoader.identity(for: urlB))
    }
}

extension Gen6FixWaveTests {
    func testRW106_HeroLayerLoadsThroughTheIdentityKeyedLoader_sourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertTrue(hero.contains("HeroRemoteImageView(urlString: aiUrl"),
                      "the remote layer paints through the identity-keyed loader view")
        XCTAssertFalse(hero.contains("AsyncImage(url: URL(string: aiUrl))"),
                       "the bare AsyncImage remote layer (hide-on-change, no retry) is gone")
        XCTAssertTrue(hero.contains("HeroRemoteImageLoader"),
                      "the loader lives with the layer it serves")
    }
}
