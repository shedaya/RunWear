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
        // RW-131 re-pin: the session's working-day chart is
        // `activeChart` (it re-derives in place on a day change);
        // the caption renders from it exactly as before.
        XCTAssertTrue(sheet.contains("DayChartBeyondForecast.caption(for: activeChart)"),
                      "the time picker must render the beyond-forecast caption for a partial day")
        XCTAssertTrue(sheet.contains("Forecast isn't available for this day yet."),
                      "the wholly-empty day keeps its own naming")
    }

    // MARK: - RW-54 (iOS): one-line outfit notes; headline moment in pine bold

    func testRW54OutfitNoteIsOneLineAndMomentIsBold_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // RE-PINNED by the successor wave (build 30): RW-114 —
        // Sol's build-29 device check, later than RW-54's
        // one-line settlement — rules that outfit-item
        // descriptions render WHOLE at default type size (his
        // frame: "Eye protection from sun an…" cut mid-word).
        // The one-line cap this pin asserted is the defect
        // RW-114 names; the pin now asserts its absence. The
        // moment-bold half of RW-54 stands unchanged below.
        let cardStart = try XCTUnwrap(home.range(of: "struct DarkClothingItemCard"))
        let card = String(home[cardStart.lowerBound...])
        XCTAssertFalse(card.contains(".lineLimit(1)"), "RW-114: no one-line cap truncates the outfit description")
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
        XCTAssertTrue(time.contains("accessibilityLabel(\"Choose a day: \\(activeDateLabel)\")"))
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
//
// RW-135 RE-PIN (Nov 7, 2026, Direction A, §7.1/§7.2/§7.4):
// the cue counter now moves ONLY on creation, so an
// "announcement" in this suite is re-based on the NARRATION
// VERSION — a bump means the derived line changed, which is
// what the bar now does with a changed settle (a quiet
// cross-fade, not a cue). Two sequence expectations change
// with the ruling: a moment-only update no longer produces a
// new line (the §7.2 settled-today form names the entry's
// PLACE, so "Updated to 7 p.m." is superseded on the bar), and
// the restored entry's bar state is the place form for the
// same reason. The discipline these tests exist for — no
// duplicate "Added", no stale place, restore silence — is
// unchanged and still asserted.
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

    /// Record one settle and, if it ANNOUNCED (RW-135 re-pin:
    /// the NARRATION VERSION moved — the derived line changed),
    /// append the line the bar shows to the sequence.
    @MainActor
    private func settle(
        _ model: WearIveRunCollectionModel,
        _ entry: WearIveRunEntry,
        announcements: inout [String]
    ) {
        let versionBefore = model.narrationVersion
        model.recordSettled(entry)
        if model.narrationVersion > versionBefore, let line = announcedLine(of: model) {
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
        // then a moment update. RW-135 RE-PIN: the moment update
        // is recorded, but under §7.2 the bar's settled-today
        // line names the PLACE — the line does not change, so
        // there is no third announcement (the pre-ruling
        // "Updated to 7 p.m." form is superseded on the bar;
        // the entry still stores its moment payload).
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
        // RW-135 spec §9.1 RE-PIN: the creation announcement is
        // the "Updated to {place}" form — no "Added" line.
        XCTAssertEqual(announcements, [
            "Updated to Overland Park, KS",
            "Updated to Miami, FL"
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
        XCTAssertEqual(announcements.count, 2, "no duplicate Added on relaunch")
        XCTAssertEqual(relaunched.entries.count, 2,
                       "RW-91: saves accumulate — the prior session's save persists beside the restored session's (Sol's Nov 6, 2026 correction supersedes the gen-6 supersede rule)")
        XCTAssertEqual(relaunched.sessionEntry()?.sessionID, "s2")
        XCTAssertEqual(relaunched.sessionEntry()?.wasUpdatedInSession, true,
                       "the restored entry keeps the narration kind it already carried")
        // RW-135 RE-PIN (§7.2): the bar's state for the restored
        // entry is the PLACE form — the entry's stored moment
        // payload is preserved on the record (asserted in the
        // store-level pins), but the bar names the place.
        XCTAssertEqual(state(of: relaunched), .updated(payload: .place("Miami, FL")))

        // A weather-only re-settle after the restore is silent,
        // as it is in-session.
        settle(relaunched, entry(
            session: "s2", place: "Miami, FL", feels: 92,
            items: ["Tank Top", "Shorts"],
            moment: .clock(hour12: 7, isPM: true)), announcements: &announcements)
        XCTAssertEqual(announcements.count, 2)

        // A REAL change after the restore still announces —
        // once, and as an Update (the session already has an
        // entry), never as a fresh Added.
        settle(relaunched, entry(
            session: "s2", place: "New York, NY", feels: 72,
            items: ["Tee", "Shorts"]), announcements: &announcements)
        // RW-135 spec §9.1 RE-PIN (as above): creation reads
        // in the "Updated to {place}" form.
        XCTAssertEqual(announcements, [
            "Updated to Overland Park, KS",
            "Updated to Miami, FL",
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
        // RW-135 spec §9.1 RE-PIN: the creation line is the
        // "Updated to {place}" form.
        XCTAssertEqual(announcements, ["Updated to Miami, FL"])

        // Relaunch + automatic re-settle of the identical
        // state: silent restore; the bar's resting state is the
        // settled form the entry already carried (presented via
        // entryAlreadyPresent, at MINI — not re-announced).
        let relaunched = WearIveRunCollectionModel(store: store, sessionID: "s2")
        settle(relaunched, entry(
            session: "s2", place: "Miami, FL", feels: 90,
            items: ["Tank Top"]), announcements: &announcements)
        XCTAssertEqual(relaunched.cueCount, 0)
        // RW-135 spec §9.1 RE-PIN (as above).
        XCTAssertEqual(announcements, ["Updated to Miami, FL"])
        XCTAssertEqual(relaunched.entries.count, 2,
                       "RW-91: the restored session's save joins the prior session's — the collection accumulates")
        // RW-135 spec §9.1 RE-PIN: the restored creation entry's
        // bar state is the "Updated to {place}" form.
        XCTAssertEqual(state(of: relaunched), .updated(payload: .place("Miami, FL")))
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
        // RW-135 spec §9.1 RE-PIN: both creations read in the
        // "Updated to {place}" form.
        XCTAssertEqual(announcements, [
            "Updated to Miami, FL",
            "Updated to Overland Park, KS"
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
        // RW-135 RE-PIN (§7.1/§7.4): the update no longer cues —
        // the creation cue stands alone at 1; the update
        // announced exactly once through the NARRATION VERSION
        // (creation's line, then the updated line).
        XCTAssertEqual(model.cueCount, 1, "creation is the only cue")
        XCTAssertEqual(model.narrationVersion, 2, "the update announced exactly once")

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
            formattedFeelsLike: "\(Int(settled.feelsLikeF))°",
            now: settled.dayStart)
        XCTAssertEqual(cardLine, "Miami, FL · Today — 90° feels like · Tank Top · Shorts")
    }

    // MARK: The recording seam — no settle from a partial selection

    func testDA09RecordingSeamRefusesInFlightSettles_sourcePins() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // RE-PINNED to the fix wave's gate form: the in-flight
        // refusal moved out of the view into the one recording
        // gate (WearIveRunRecordingGate) — the view feeds the
        // fetch's loading state into the gate, and the gate
        // refuses on it. The DA-09 discipline is unchanged; the
        // pin now verifies it at the seam where it lives.
        XCTAssertTrue(home.contains("WearIveRunRecordingGate.admits"),
                      "DA-09: the recording stands on the one recording gate")
        XCTAssertTrue(home.contains("isLoading: viewModel.isLoading"),
                      "DA-09: the gate is fed the fetch's in-flight state")
        let gate = try sourceFile("../RunWear/Models/DayChartModel.swift")
        XCTAssertTrue(gate.contains("guard !isLoading, hasCommittedSnapshot"),
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
        // RE-PINNED for RW-135 spec §2.2/§3 (Direction A′,
        // approved Nov 7, 2026): the footer is ALWAYS in the
        // layout while a session entry exists — the interim
        // build's full-expression gating
        // (onFullExpressionChange / wearFooterPresented) is
        // removed, and the dock's visibility is the §3
        // position-derived handoff fade. RW-86's discipline
        // stands in the new shape: no second copy of the
        // expanded content exists in any paint path — the bar
        // is MINI-only and the footer is the single expanded
        // copy, mounted from the session entry alone.
        XCTAssertTrue(bar.contains("handoffFade"),
                      "spec §3: the dock consumes the position-derived handoff fade")
        XCTAssertFalse(bar.contains("onFullExpressionChange"),
                       "spec §2.2: the full-expression gating is removed")
        let homeView = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(homeView.contains("if let todays = wearExitInProgress ? wearExitEntry : liveEntry"),
                      "spec §2.2: the single expanded copy (the footer section) mounts whenever a session entry exists")
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

// MARK: - Successor wave (build 30) — RW-109…RW-119
//
// The build-29 device check's findings: client state-truthfulness
// defects (the server record exonerated the backend for the whole
// list — two traces reconciled the session job by job). Each test
// below pins one item's mechanism at the seam the fix lives in,
// following the suite's pattern: pure logic on the dual-membered
// models, view settlements by source pins, and the hosted-capture
// surfaces named UNPROVEN in the lane report rather than passed.
final class Gen6SuccessorWaveTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: RW-109 — the badge classifies the displayed feels-like

    /// The pure seam: at Sol's two frames, the ENGINE bracket of
    /// the comfort-adjusted value and the DISPLAY bracket of the
    /// printed value differ by exactly the filed one band — and
    /// the display bracket is the one whose range holds the
    /// printed number.
    func testRW109_DisplayBracketClassifiesThePrintedFeelsLike() {
        // New York frame: raw 64°F, runs-warm profile (+5) —
        // the engine solved at 69 (Warm); the hero printed 64.
        XCTAssertEqual(TemperatureBracket.from(temperature: 64 + 5), .warm,
                       "the engine frame at the adjusted value — unchanged by this fix")
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 64), .mild)
        // Miami frame: raw 79°F adjusted to 84 (Hot); printed 79.
        XCTAssertEqual(TemperatureBracket.from(temperature: 79 + 5), .hot)
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 79), .warm)
        // A runs-cold profile diverges the other way: raw 66
        // adjusted to 61 (Mild) — the display bracket stays Warm,
        // the bracket of the number on screen.
        XCTAssertEqual(TemperatureBracket.from(temperature: 66 - 5), .mild)
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 66), .warm)
        // Neutral comfort: the two frames agree, as they always
        // did — the defect needed a non-neutral profile.
        for raw in [9.0, 25.0, 45.0, 55.0, 70.0, 85.0] {
            XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: raw),
                           TemperatureBracket.from(temperature: raw))
        }
    }

    /// The rounding edge: the classifier reads the value AS
    /// PRINTED (Int(round)), so a raw 64.6 — which prints "65" —
    /// classifies Warm, never Mild-beside-65.
    func testRW109_DisplayBracketRoundsAsPrinted() {
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 64.6), .warm)
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 64.4), .mild)
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 79.5), .hot)
        XCTAssertEqual(TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: 49.5), .mild)
    }

    /// The invariant the badge owes the numeral, swept: for every
    /// raw feels-like, the display bracket's printed bounds
    /// contain the printed numeral.
    func testRW109_DisplayBracketRangeAlwaysContainsThePrintedNumeral() {
        var raw = -10.0
        while raw <= 110.0 {
            let printed = Int(round(raw))
            let bracket = TemperatureBracket.displayBracket(forDisplayedFeelsLikeF: raw)
            let bounds = bracket.fahrenheitBounds
            if let lower = bounds.lower {
                XCTAssertGreaterThanOrEqual(printed, lower,
                    "raw \(raw) prints \(printed) but \(bracket) starts at \(lower)")
            }
            if let upper = bounds.upper {
                if bracket == .extreme {
                    XCTAssertLessThan(printed, upper,
                        "raw \(raw) prints \(printed) but extreme is below \(upper)")
                } else {
                    XCTAssertLessThanOrEqual(printed, upper,
                        "raw \(raw) prints \(printed) but \(bracket) ends at \(upper)")
                }
            }
            raw += 0.25
        }
    }

    /// The view-model seam, end to end at Sol's two frames: the
    /// recommendation keeps its engine bracket (photo keying and
    /// the chart are untouched), while every display surface
    /// reads the display bracket.
    @MainActor
    func testRW109_ViewModelDisplayBracketAtSolFrames() {
        // 64°F frame: apparent 17.8°C ≈ 64.0°F, comfort warm.
        let vmNY = successorLoadedViewModel(apparentC: 17.8, comfort: .warm)
        XCTAssertEqual(vmNY.temperatureUnit.format(vmNY.currentFeelsLike), "64°F")
        XCTAssertEqual(vmNY.recommendation?.temperatureBracket, .warm,
                       "the recommendation's engine bracket is the adjusted frame — by design")
        XCTAssertEqual(vmNY.displayTempBracket, .mild,
                       "the badge's bracket is the printed number's bracket")
        XCTAssertEqual(vmNY.currentTempBracket, .MILD,
                       "the numeral chip classifies the raw value it stands beside")
        // 79°F frame: apparent 26.1°C ≈ 79.0°F.
        let vmMIA = successorLoadedViewModel(apparentC: 26.1, comfort: .warm)
        XCTAssertEqual(vmMIA.temperatureUnit.format(vmMIA.currentFeelsLike), "79°F")
        XCTAssertEqual(vmMIA.recommendation?.temperatureBracket, .hot)
        XCTAssertEqual(vmMIA.displayTempBracket, .warm)
        XCTAssertEqual(vmMIA.currentTempBracket, .WARM)
        // Neutral comfort at the same raw values: no divergence.
        let vmNeutral = successorLoadedViewModel(apparentC: 17.8, comfort: .neutral)
        XCTAssertEqual(vmNeutral.displayTempBracket, vmNeutral.recommendation?.temperatureBracket)
    }

    @MainActor
    private func successorLoadedViewModel(apparentC: Double, comfort: ComfortLevel) -> WeatherViewModel {
        UserDefaults.standard.set(TemperatureUnit.fahrenheit.rawValue, forKey: "temperatureUnit")
        let hourly = HourlyWeather(
            time: ["2026-11-06T07:00"],
            temperature2m: [20],
            apparentTemperature: [apparentC],
            relativeHumidity2m: [50],
            precipitationProbability: [0],
            precipitation: [0],
            rain: [0],
            uvIndex: [0],
            windSpeed10m: [8],
            windDirection10m: [0],
            windGusts10m: [8],
            weatherCode: [0]
        )
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.comfortLevel = comfort
        vm.weatherData = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 20, windspeed: 8, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: "America/New_York",
            currentDetail: nil
        )
        vm.selectedDate = Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 6))!
        vm.selectedHour = 7
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return vm
    }

    func testRW109_BadgeSurfacesReadTheDisplayBracket_sourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertEqual(view.components(separatedBy: "TemperatureBracketBadge(bracket: viewModel.displayTempBracket").count - 1, 2,
                       "both badge call sites (list + grid) read the display bracket")
        XCTAssertFalse(view.contains("TemperatureBracketBadge(bracket: recommendation.temperatureBracket"),
                       "no badge site reads the engine bracket any more")
        XCTAssertTrue(view.contains("viewModel.displayTempBracket.description"),
                      "the detail context line names the display bracket beside the displayed feels-like")
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("var displayTempBracket: TemperatureBracket"),
                      "the display bracket lives on the view model, from the headline's own selection")
        let chipRange = try XCTUnwrap(vm.range(of: "var currentTempBracket"))
        let chipBody = String(vm[chipRange.lowerBound...].prefix(900))
        XCTAssertFalse(chipBody.contains("adjustTemperature"),
                       "the numeral chip no longer classifies the comfort-adjusted value")
    }

    // MARK: RW-115 — a delivery clears the cell's failure

    private func successorLookup(
        cell: String, found: Bool = true, heroId: String? = "hero-1",
        version: Int? = 1, lookState: String? = nil
    ) -> PersonalHeroLookup {
        PersonalHeroLookup(
            found: found,
            combinationId: cell,
            heroId: heroId,
            variant: 1,
            imageUrl: found ? "https://signed.example/\(cell)-\(version ?? 0).jpg" : nil,
            version: version,
            replenishmentQueued: 0,
            lookState: lookState,
            lookReason: nil,
            scaffold: nil,
            scaffoldCombinationId: nil,
            serveTierRaw: nil,
            servedCombinationId: nil,
            converging: nil
        )
    }

    func testRW115_CacheDeliveryFact() {
        var cache = PersonalCellCache()
        let t0 = Date()
        // First resolve of a hero: a delivery.
        let first = cache.resolveWithDelivery(lookup: successorLookup(cell: "C1"), now: t0)
        XCTAssertEqual(first.resolution, .show("https://signed.example/C1-1.jpg"))
        XCTAssertTrue(first.deliveredHero, "storing a hero where none stood is a delivery")
        // Same version re-resolve: NOT a delivery (the cached
        // photo is the one already there).
        let again = cache.resolveWithDelivery(lookup: successorLookup(cell: "C1"), now: t0.addingTimeInterval(60))
        XCTAssertFalse(again.deliveredHero, "a same-version re-resolve delivers nothing new")
        // A version bump (a regeneration landed): a delivery.
        let bumped = cache.resolveWithDelivery(lookup: successorLookup(cell: "C1", heroId: "hero-2", version: 2), now: t0.addingTimeInterval(120))
        XCTAssertTrue(bumped.deliveredHero, "a new hero version is a delivery")
        // A miss delivers nothing.
        let miss = cache.resolveWithDelivery(lookup: successorLookup(cell: "C1", found: false, version: 3), now: t0.addingTimeInterval(180))
        XCTAssertEqual(miss.resolution, .none)
        XCTAssertFalse(miss.deliveredHero)
    }

    func testRW115_FailureClearsExactlyOnDelivery() {
        let failed = PersonalLookState.failed(reason: .qualityRefused)
        XCTAssertEqual(failed.reconciledWithDelivery(true), .ready,
                       "the Miami sequence: the retry's delivery clears the first attempt's failure")
        XCTAssertEqual(failed.reconciledWithDelivery(false), failed,
                       "no delivery, no clear — the keep-serving failure surface stands")
        // Non-failure states are untouched by the rule.
        XCTAssertEqual(PersonalLookState.making.reconciledWithDelivery(true), .making)
        XCTAssertEqual(PersonalLookState.ready.reconciledWithDelivery(false), .ready)
        XCTAssertEqual(PersonalLookState.none.reconciledWithDelivery(true), .none)
    }

    func testRW115_StoreFoldsThroughTheOneFold_sourcePins() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("private func foldLookup(_ lookup: PersonalHeroLookup) -> PersonalCellCache.Resolution"),
                      "one fold routes every lookup path")
        XCTAssertTrue(store.contains("resolveWithDelivery"),
                      "the fold carries the cache's delivery fact")
        XCTAssertTrue(store.contains("reconciledWithDelivery(deliveredHero)"),
                      "the fold applies the delivery-clear rule")
        XCTAssertFalse(store.contains("noteLookState"),
                       "the pre-fix per-path fold is gone — paths cannot drift again")
    }

    // MARK: RW-113 / RW-117(a) — the poll settles on the settled fold

    func testRW113_PollEndsOnlyOnASettledFold() {
        // The Juneau instant: a hero resolved while the fold
        // still reads MAKING (hero inserted, job completion
        // 0.54 s behind it) — the poll must NOT end there.
        XCTAssertFalse(HeroPollPolicy.shouldEndPoll(landed: true, foldedState: .making),
                       "a landing under a MAKING fold keeps polling — the pre-fix loop stranded the pill here")
        // The fold turns terminal: the poll ends and returns the
        // landed URL.
        XCTAssertTrue(HeroPollPolicy.shouldEndPoll(landed: true, foldedState: .ready))
        XCTAssertTrue(HeroPollPolicy.shouldEndPoll(landed: true, foldedState: .failed(reason: .error)))
        XCTAssertTrue(HeroPollPolicy.shouldEndPoll(landed: true, foldedState: .none))
        // No landing: the pre-existing terminal rule stands.
        XCTAssertTrue(HeroPollPolicy.shouldEndPoll(landed: false, foldedState: .failed(reason: .qualityRefused)))
        XCTAssertTrue(HeroPollPolicy.shouldEndPoll(landed: false, foldedState: .ready))
        XCTAssertFalse(HeroPollPolicy.shouldEndPoll(landed: false, foldedState: .making))
        XCTAssertFalse(HeroPollPolicy.shouldEndPoll(landed: false, foldedState: .none))
    }

    // MARK: RW-117 — scaffold truth comes from the payload

    func testRW117_ScaffoldMarkerDecodesFromThePayload() throws {
        let scaffoldJSON = """
        {"found": true, "scaffold": true, "scaffold_combination_id": "MALE_CLEAR_WARM_MIDDAY_ab12",
         "combination_id": "MALE_CLEAR_WARM_DAWN_ab12", "hero_id": "hero-scaffold", "variant": 1,
         "image_url": "https://signed.example/scaffold.jpg", "version": 3,
         "look_state": "ready", "look_reason": null}
        """
        let lookup = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(scaffoldJSON.utf8))
        XCTAssertEqual(lookup.scaffold, true)
        XCTAssertEqual(lookup.scaffoldCombinationId, "MALE_CLEAR_WARM_MIDDAY_ab12")
        XCTAssertEqual(PersonalLookState.from(lookup: lookup), .ready,
                       "a scaffold serve folds READY — which is why the look state could never classify it")
        // The Juneau shape: an exact serve in its completion
        // instant — no scaffold marker, look_state MAKING.
        let exactJSON = """
        {"found": true, "combination_id": "MALE_CLOUDY_COOL_MIDDAY_84c1", "hero_id": "hero-exact",
         "variant": 1, "image_url": "https://signed.example/exact.jpg", "version": 1,
         "look_state": "making", "look_reason": null}
        """
        let exact = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(exactJSON.utf8))
        XCTAssertNil(exact.scaffold)
        XCTAssertEqual(PersonalLookState.from(lookup: exact), .making,
                       "an exact hero can fold MAKING — the second half of why the inference failed both ways")
    }

    func testRW117_ClassificationReadsThePayloadFact_sourcePins() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertFalse(vm.contains("currentPersonalServeIsScaffold = lookState == .making"),
                       "the paint-time scaffold inference is gone")
        XCTAssertFalse(vm.contains("let isScaffold = lookState == .making"),
                       "the revalidation scaffold inference is gone")
        XCTAssertTrue(vm.contains("store.serveIsScaffold(for: combo)"),
                      "the classification reads the resolver's payload fact")
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        // RE-PINNED for the RW-130 contract v2 (fix wave): the
        // payload's fact is the serve TIER now — scaffold and
        // similar are the non-exact personal serves, folded from
        // the tier on every resolving lookup (the v1 boolean
        // marker `lookup.scaffold` is superseded by the tier).
        XCTAssertTrue(store.contains("scaffoldServes[cell] = lookup.serveTier.isNonExactPersonalServe"),
                      "the store folds the payload's marker on every resolving lookup")
    }

    // MARK: RW-111 / RW-118 — exhausted at the hero; no fall-through

    func testRW111_ExhaustedPillRendersForTheRefusedCell() {
        // The end-state cell: free account, allowance used, the
        // server refused THIS cell, no personal photo on screen.
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .none, personalPhotoDisplayed: false,
                isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5,
                isExhaustedCell: true),
            .exhausted(used: 5, allowance: 5),
            "the refused cell carries the exhausted pill at the hero — RW-118's fall-through is closed")
        // A cell that was NOT refused stays silent over stock.
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .none, personalPhotoDisplayed: false,
                isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5,
                isExhaustedCell: false),
            .hidden)
        // Below the allowance, a refused-marked cell is not
        // exhausted (the marker only stands with the count).
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .none, personalPhotoDisplayed: false,
                isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 5,
                isExhaustedCell: true),
            .hidden)
        // A subscribed account never resolves to exhausted.
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .none, personalPhotoDisplayed: false,
                isSubscribed: true, freeGenerationsUsed: 5, freeAllowance: 5,
                isExhaustedCell: true),
            .hidden)
        // Precedence preserved: MAKING outranks the exhausted
        // branch; a scaffold serve stays silent over it.
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5,
                isExhaustedCell: true),
            .makingWithUpsell)
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                isSubscribed: true, freeGenerationsUsed: 5, freeAllowance: 5,
                isScaffoldServe: true, isExhaustedCell: true),
            .hidden)
        // The in-photo exhausted branch is unchanged.
        XCTAssertEqual(
            HeroPill.resolve(
                optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5),
            .exhausted(used: 5, allowance: 5))
    }

    func testRW111_FailureAndExhaustedLiveAtTheHero_sourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertTrue(hero.contains("var lookFailed: Bool"),
                      "the hero carries the failed state as an input")
        XCTAssertTrue(hero.contains("Text(MakeItMeCopy.lookFailed)"),
                      "the hero renders the settled failure line verbatim")
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("lookFailed: currentCellLookIsFailed"),
                      "home feeds the hero the current cell's failed state")
        XCTAssertTrue(view.contains("isExhaustedCell: personalStore.exhaustedCell == cell.combinationId"),
                      "home feeds the pill the refused-cell fact")
        XCTAssertFalse(view.contains("MakeItMeUpsellCard(currentCell:"),
                       "the exhausted upsell card no longer renders at the bottom of home")
        XCTAssertFalse(view.contains("Text(MakeItMeCopy.lookFailed)"),
                       "the failure line no longer renders in the scroll content")
        XCTAssertTrue(view.contains("See all your looks"),
                      "the failed cell's library door stays (RW-61)")
    }

    // MARK: RW-110 — the banner follows a location switch (replay)

    private func successorEntry(
        session: String, place: String, items: [String], hero: String = "hero-1"
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_762_560_000),
            placeName: place,
            feelsLikeF: 64,
            actualF: 66,
            condition: "Clear",
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: hero,
            sessionID: session,
            momentReading: .now
        )
    }

    @MainActor
    private func successorDockLine(of model: WearIveRunCollectionModel) -> String? {
        let presented = model.sessionEntry()
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: presented,
            moment: presented?.momentReading ?? .now,
            wasUpdatedThisSession: presented?.wasUpdatedInSession ?? false
        )
        return WearIveRunNarration.line(for: state)
    }

    /// Sol's sequence, replayed through the production model:
    /// settle New York (the bar names it), switch to Miami (the
    /// settle moves the session's one entry), and the bar names
    /// Miami — via the entry itself once settled, and via the
    /// RW-88 viewed-place override in the interval before the
    /// settle lands.
    @MainActor
    func testRW110_BannerFollowsTheLocationSwitch() {
        let defaults = UserDefaults(suiteName: "gen6-successor-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s-rw110")
        model.recordSettled(successorEntry(session: "s-rw110", place: "New York, NY", items: ["Tee"]))
        // RW-135 spec §9.1 RE-PIN: no "Added" line — a creation
        // reads "Updated to {place}" from its first frame.
        XCTAssertEqual(successorDockLine(of: model), "Updated to New York, NY")
        // Before the Miami settle lands, the viewed-place
        // override already names Miami.
        XCTAssertEqual(
            WearIveRunViewedNarration.payload(
                entryPlaceDisplayName: "New York, NY", entryMoment: .now,
                viewedPlace: SimplePlace(city: "Miami", region: "FL"),
                viewedDate: Date(timeIntervalSince1970: 1_762_560_000), viewedHour: 12,
                isViewingNow: true,
                calendar: Calendar(identifier: .gregorian),
                now: Date(timeIntervalSince1970: 1_762_560_000)),
            "Miami, FL, Now")
        // The settle lands: the entry itself moves, and the
        // bar's own narration names Miami in full.
        model.recordSettled(successorEntry(session: "s-rw110", place: "Miami, FL", items: ["Tank"]))
        XCTAssertEqual(model.sessionEntry()?.placeDisplayName, "Miami, FL")
        XCTAssertEqual(successorDockLine(of: model), "Updated to Miami, FL")
    }

    // MARK: RW-112 / RW-116 — the settle records the landed look

    /// A hero-only settle (the personal landing re-offer) updates
    /// the session entry's hero reference in place, silently —
    /// the collection row then thumbnails the personal look, and
    /// the entry the landing belongs to is the one that stands.
    @MainActor
    func testRW112_HeroOnlySettleUpdatesTheEntrySilently() {
        let defaults = UserDefaults(suiteName: "gen6-successor-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s-rw112")
        model.recordSettled(successorEntry(session: "s-rw112", place: "Miami, FL", items: ["Tank"], hero: "stock-url"))
        let cuesAfterCreate = model.cueCount
        model.recordSettled(successorEntry(session: "s-rw112", place: "Miami, FL", items: ["Tank"], hero: "personal-url"))
        XCTAssertEqual(model.sessionEntry()?.heroImageReference, "personal-url",
                       "the entry thumbnails the personal look once it has landed and re-settled")
        XCTAssertEqual(model.cueCount, cuesAfterCreate,
                       "a hero-only landing is silent — recorded, never announced")
        XCTAssertEqual(model.entries.count, 1,
                       "the landing updates the session's one entry in place (GEN-3 session rule)")
    }

    func testRW112_LandingReOfferTriggersStand_sourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains(".onChange(of: viewModel.currentPersonalHeroId)"),
                      "a personal application re-offers the recording")
        XCTAssertTrue(view.contains(".onChange(of: personalStore.landedHeroVersion)"),
                      "a poll landing re-offers the recording")
    }

    // MARK: RW-119 — the section stands in its empty form

    func testRW119_DockStandsEmptyBeforeAnyEntry_sourcePins() throws {
        // RW-135 RE-PIN (Nov 7, 2026, Direction A, §7.1)
        // SUPERSEDES RW-119's Home presence: no session entry →
        // NO bar, ever — no placeholder state stands in the
        // dock's seat. (RW-119's defect — a session whose settles
        // never recorded losing the section entirely — is
        // answered under Direction A by the absence rule itself:
        // the mini-player precedent §7.1 cites shows nothing
        // when there is nothing to show.) The designed empty
        // copy still lives where it belongs: the COLLECTION's
        // empty state, behind the doorway.
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(view.contains("WearIveRunEmptyDockView"),
                       "§7.1: no empty/placeholder form stands in the dock's seat")
        XCTAssertTrue(view.contains("if state != .preAdd"),
                      "the dock renders only while a session entry stands")
        XCTAssertEqual(WearIveRunEmptyDockView.emptyLine, "Your scrapbook starts with today's run.",
                       "the designed empty state's head line, verbatim (collection surface)")
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("Your scrapbook starts with today's run."),
                      "the collection's empty state carries the settled copy")
        // The model state for no entry is still .preAdd — the
        // dock's branch reads it; the bar itself is unchanged.
        XCTAssertEqual(
            WearIveRunMiniBarModel.state(todaysEntry: nil, moment: .now, wasUpdatedThisSession: false),
            .preAdd)
    }

    // MARK: RW-114 — descriptions render whole

    func testRW114_DescriptionsCarryNoLineCap_sourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        let cardStart = try XCTUnwrap(view.range(of: "struct DarkClothingItemCard"))
        let card = String(view[cardStart.lowerBound...])
        XCTAssertTrue(card.contains("Text(item.description)"), "the card renders the description")
        let descriptionRange = try XCTUnwrap(card.range(of: "Text(item.description)"))
        let descriptionBlock = String(card[descriptionRange.lowerBound...].prefix(700))
        XCTAssertFalse(descriptionBlock.contains(".lineLimit("),
                       "no line cap stands on the outfit card's description (RW-114)")
        let shop = try sourceFile("../RunWear/Views/Modals/ShopModal.swift")
        XCTAssertFalse(shop.contains(".lineLimit(2)"),
                       "the shop row's two-line description cap is gone too (RW-114)")
    }
}

// MARK: - Fix wave 2026-11-07 (build 31): reconciliation, Now
// state, serve ladder, picker session, RW-122/132/133/135 seams
//
// The architecture review's 30-row regression matrix, executed
// for the rows this lane touches. Row mapping is named per
// test. Hosted-capture render rows (S6-29 and the visual half
// of RW-133/RW-135) are NOT executed here — the hosted runner
// returns black for hosted captures (build-30 gate record), so
// those surfaces are reported UNPROVEN in the lane report,
// never passed.

extension Gen6FixWaveTests {

    // MARK: Serve ladder (review §4; S6-18/19/20)

    func testFixWave_ServeTierWireMapping() {
        XCTAssertEqual(PersonalServeTier(wireValue: "exact"), .exact)
        XCTAssertEqual(PersonalServeTier(wireValue: "scaffold"), .scaffold)
        XCTAssertEqual(PersonalServeTier(wireValue: "similar"), .similar)
        XCTAssertEqual(PersonalServeTier(wireValue: "none"), .none)
        // An unrecognised tier is treated as none (§4.3).
        XCTAssertEqual(PersonalServeTier(wireValue: "surprise"), .none)
        // An absent tier (pre-v2 payload) is handled by the
        // lookup's decoding path: no wire value and nothing
        // found derives .none.
        let absentTier = PersonalHeroLookup(
            found: false, combinationId: "C1", heroId: nil, variant: nil,
            imageUrl: nil, version: nil, replenishmentQueued: 0,
            lookState: nil, lookReason: nil, scaffold: nil, scaffoldCombinationId: nil,
            serveTierRaw: nil, servedCombinationId: nil, converging: nil)
        XCTAssertEqual(absentTier.serveTier, .none)
        XCTAssertTrue(PersonalServeTier.scaffold.isNonExactPersonalServe)
        XCTAssertTrue(PersonalServeTier.similar.isNonExactPersonalServe)
        XCTAssertFalse(PersonalServeTier.exact.isNonExactPersonalServe)
        XCTAssertFalse(PersonalServeTier.none.isNonExactPersonalServe)
    }

    func testFixWave_LookupDecodesContractV2Fields() throws {
        let json = """
        {"found": true, "combination_id": "C1", "hero_id": "h9",
         "variant": 1, "image_url": "https://x/1.jpg", "version": 3,
         "replenishment_queued": 0, "look_state": null,
         "look_reason": null, "scaffold": null,
         "scaffold_combination_id": null,
         "serve_tier": "similar", "served_combination_id": "C0",
         "converging": true}
        """
        let lookup = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(json.utf8))
        XCTAssertEqual(lookup.serveTier, .similar)
        XCTAssertEqual(lookup.servedCombinationId, "C0")
        XCTAssertEqual(lookup.converging, true)
    }

    private func tieredLookup(cell: String, tier: String?, url: String, version: Int) -> PersonalHeroLookup {
        PersonalHeroLookup(
            found: true, combinationId: cell, heroId: "h-\(version)", variant: 1,
            imageUrl: url, version: version, replenishmentQueued: 0,
            lookState: nil, lookReason: nil, scaffold: nil, scaffoldCombinationId: nil,
            serveTierRaw: tier, servedCombinationId: cell, converging: nil
        )
    }

    /// S6-19 + RW-130/RW-134 at the cache seam: a non-exact
    /// serve paints but is NEVER a terminal hit; when the exact
    /// cell lands, the next resolve serves it (the swap the
    /// revalidation path performs on landing/return).
    func testFixWave_NonExactServePaintsButNeverSettles() {
        var cache = PersonalCellCache()
        let t0 = Date()
        let scaffold = cache.resolve(
            lookup: tieredLookup(cell: "C1", tier: "scaffold", url: "https://x/scaffold.jpg", version: 1),
            now: t0)
        XCTAssertEqual(scaffold, .show("https://x/scaffold.jpg"), "the fallback serve paints silently, now")
        XCTAssertEqual(cache.cachedServeTier(for: "C1"), .scaffold)
        XCTAssertFalse(cache.isTerminalCacheHit(for: "C1", now: t0),
                       "a scaffold serve is never a terminal cache hit — the cell is always re-asked")
        let exact = cache.resolve(
            lookup: tieredLookup(cell: "C1", tier: "exact", url: "https://x/exact.jpg", version: 2),
            now: t0.addingTimeInterval(600))
        XCTAssertEqual(exact, .show("https://x/exact.jpg"), "the landed exact cell replaces the fallback serve")
        XCTAssertTrue(cache.isTerminalCacheHit(for: "C1", now: t0.addingTimeInterval(600)))
    }

    // MARK: Band terminal fold (RW-126 client half; S6-08)

    func testFixWave_BandSettlesWhenGenerationProvenOver() {
        let coverage = BankCoverage(ready: 2, total: 5)
        XCTAssertNotNil(ConvergenceBand.content(isSubscribed: true, coverage: coverage, generationActive: nil),
                        "an unread job state keeps the settled (coverage-only) behaviour")
        XCTAssertNotNil(ConvergenceBand.content(isSubscribed: true, coverage: coverage, generationActive: true))
        XCTAssertNil(ConvergenceBand.content(isSubscribed: true, coverage: coverage, generationActive: false),
                     "a server read proving every job terminal settles the band — it cannot outlive generation")
    }

    // MARK: RW-128 aligned allowance

    func testFixWave_FreeAllowanceIsTen() throws {
        // The store is not compiled into the test target (the
        // suite's standing pattern), so its ONE allowance
        // constant is pinned against the source of record.
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("static let freeAllowance = 10"),
                      "RW-128: the iOS free allowance is 10, in the one constant")
        XCTAssertTrue(OnboardingCopy.freeLooksPromise.contains("10"),
                      "the onboarding promise follows the allowance")
    }

    // MARK: Recording gate (S6-13/14/15/16; RW-110/RW-132)

    func testFixWave_RecordingGate() {
        // Now settle admitted — including its marked
        // current-conditions fallback (the Now ruling).
        XCTAssertTrue(WearIveRunRecordingGate.admits(
            isLoading: false, hasCommittedSnapshot: true, snapshotHour: 7,
            selectedHour: 7, isNowSelection: true, selectedTimeOutsideForecast: true))
        // Picked settle on a viewed future day admitted (S6-14
        // as ruled Nov 7).
        XCTAssertTrue(WearIveRunRecordingGate.admits(
            isLoading: false, hasCommittedSnapshot: true, snapshotHour: 18,
            selectedHour: 18, isNowSelection: false, selectedTimeOutsideForecast: false))
        // S6-13: a mid-flight read never records.
        XCTAssertFalse(WearIveRunRecordingGate.admits(
            isLoading: true, hasCommittedSnapshot: true, snapshotHour: 7,
            selectedHour: 7, isNowSelection: true, selectedTimeOutsideForecast: false))
        // S6-16: an out-of-forecast PICK never records from the
        // fallback solve.
        XCTAssertFalse(WearIveRunRecordingGate.admits(
            isLoading: false, hasCommittedSnapshot: true, snapshotHour: 7,
            selectedHour: 7, isNowSelection: false, selectedTimeOutsideForecast: true))
        // A snapshot of a different hour than the selection is
        // not the same solve.
        XCTAssertFalse(WearIveRunRecordingGate.admits(
            isLoading: false, hasCommittedSnapshot: true, snapshotHour: 7,
            selectedHour: 9, isNowSelection: false, selectedTimeOutsideForecast: false))
        // No committed snapshot, no record.
        XCTAssertFalse(WearIveRunRecordingGate.admits(
            isLoading: false, hasCommittedSnapshot: false, snapshotHour: nil,
            selectedHour: 7, isNowSelection: true, selectedTimeOutsideForecast: false))
    }

    // MARK: Now state + solve (S6-01/02/03/25; RW-123/RW-125)

    @MainActor
    private func nowFixture() -> (vm: WeatherViewModel, day: Date, nowHour: Int) {
        let tz = TimeZone(identifier: "America/New_York")!
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = tz
        let now = Date()
        let nowHour = calendar.component(.hour, from: now)
        let day = calendar.startOfDay(for: now)
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = tz
        formatter.dateFormat = "yyyy-MM-dd"
        let dayString = formatter.string(from: day)
        let times = (0..<24).map { String(format: "%@T%02d:00", dayString, $0) }
        let hourly = HourlyWeather(
            time: times,
            temperature2m: (0..<24).map { Double($0) },
            apparentTemperature: (0..<24).map { Double($0) },
            relativeHumidity2m: times.map { _ in 50 },
            precipitationProbability: times.map { _ in 0 },
            precipitation: times.map { _ in 0.0 },
            rain: times.map { _ in 0.0 },
            uvIndex: nil,
            windSpeed10m: times.map { _ in 5 },
            windDirection10m: times.map { _ in 0 },
            windGusts10m: times.map { _ in 5 },
            weatherCode: times.map { _ in 0 }
        )
        let vm = WeatherViewModel()
        vm.weatherData = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 30, windspeed: 8, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: "America/New_York", currentDetail: nil
        )
        return (vm, day, nowHour)
    }

    /// S6-25 / RW-123: the Now solve reads the CURRENT block —
    /// 30°C current against a 0–23°C hourly ladder, the snapshot
    /// must carry the current value at the location's now hour.
    @MainActor
    func testFixWave_NowSolveUsesCurrentConditions() {
        let (vm, day, nowHour) = nowFixture()
        vm.selectedDate = day
        vm.selectedHour = nowHour
        XCTAssertTrue(vm.isNowSelection)
        vm.updateSelectedWeatherSnapshot()
        let snapshot = vm.selectedWeatherSnapshot
        XCTAssertEqual(snapshot?.hour, nowHour)
        XCTAssertEqual(snapshot?.temperatureFahrenheit ?? -1, 86, accuracy: 0.6,
                       "Now solves from current conditions (30°C), not the hourly row")
        XCTAssertFalse(vm.selectedTimeOutsideForecast)
    }

    /// S6-03: a manual pick solves from the HOURLY row at the
    /// picked wall-clock hour — never the current block.
    @MainActor
    func testFixWave_PickedSolveUsesHourlyRow() {
        let (vm, day, nowHour) = nowFixture()
        let picked = (nowHour + 5) % 24
        vm.updateSelectedTime(date: day, hour: picked)
        XCTAssertFalse(vm.isNowSelection)
        let snapshot = vm.selectedWeatherSnapshot
        XCTAssertEqual(snapshot?.hour, picked)
        XCTAssertEqual(snapshot?.temperatureFahrenheit ?? -1,
                       Double(picked) * 9 / 5 + 32, accuracy: 0.6,
                       "the pick reads its hourly row (\(picked)°C), not the 30°C current block")
    }

    /// Both switch sequences at the solve seam (S6-01/02): the
    /// weather frame is swapped for a second location; in the
    /// Now state the solve follows the NEW location's current
    /// block; in a picked state the wall-clock pick survives
    /// and solves from the new location's hourly row.
    @MainActor
    func testFixWave_SwitchSequencesAtTheSolveSeam() {
        let (vm, day, nowHour) = nowFixture()
        // Now → switch.
        vm.selectedDate = day
        vm.selectedHour = nowHour
        swapFrameToChicago(vm, day: day)
        vm.updateSelectedWeatherSnapshot()
        XCTAssertEqual(vm.selectedWeatherSnapshot?.temperatureFahrenheit ?? -1, 50, accuracy: 0.6,
                       "Now→switch: the solve is the new location's current conditions (10°C)")
        // Pick → switch.
        let picked = (nowHour + 5) % 24
        vm.updateSelectedTime(date: day, hour: picked)
        swapFrameToChicago(vm, day: day)
        vm.updateSelectedWeatherSnapshot()
        XCTAssertEqual(vm.selectedHour, picked, "pick→switch: the wall-clock pick survives")
        XCTAssertEqual(vm.selectedWeatherSnapshot?.temperatureFahrenheit ?? -1,
                       Double(picked + 40) * 9 / 5 + 32, accuracy: 0.6,
                       "pick→switch: the pick solves from the new location's hourly row")
    }

    @MainActor
    private func swapFrameToChicago(_ vm: WeatherViewModel, day: Date) {
        let tz = TimeZone(identifier: "America/Chicago")!
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = tz
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = tz
        formatter.dateFormat = "yyyy-MM-dd"
        // The rows are the VIEWED calendar day's wall-clock
        // rows in the new location — the frame the solve's
        // textual prefix keys on (the viewed day's date at the
        // picked wall-clock hour, as on Android). Formatting
        // the New York-midnight INSTANT in Chicago time would
        // date the whole block a day early (23:00 the day
        // before) — a frame the app never requests — so the
        // block is built on the viewed day's own calendar date,
        // re-based into the Chicago calendar.
        var viewedCalendar = Calendar(identifier: .gregorian)
        viewedCalendar.timeZone = TimeZone(identifier: "America/New_York")!
        let viewedDay = viewedCalendar.dateComponents([.year, .month, .day], from: day)
        let dayString = formatter.string(from: calendar.date(from: viewedDay) ?? day)
        let times = (0..<24).map { String(format: "%@T%02d:00", dayString, $0) }
        let hourly = HourlyWeather(
            time: times,
            temperature2m: (0..<24).map { Double($0 + 40) },
            apparentTemperature: (0..<24).map { Double($0 + 40) },
            relativeHumidity2m: times.map { _ in 50 },
            precipitationProbability: times.map { _ in 0 },
            precipitation: times.map { _ in 0.0 },
            rain: times.map { _ in 0.0 },
            uvIndex: nil,
            windSpeed10m: times.map { _ in 5 },
            windDirection10m: times.map { _ in 0 },
            windGusts10m: times.map { _ in 5 },
            weatherCode: times.map { _ in 0 }
        )
        vm.weatherData = WeatherData(
            latitude: 41.8781, longitude: -87.6298,
            currentWeather: CurrentWeather(temperature: 10, windspeed: 8, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: "America/Chicago", currentDetail: nil
        )
        // The swap stands in for the fetch COMMIT, so it must
        // also perform the commit's own step (RW-123/RW-125,
        // review §5.2 row 1): in the Now state the commit
        // re-anchors the projection from the fresh response's
        // own frame BEFORE any solve — a switch lands on the
        // new place's Now, never on the old place's hour number
        // reinterpreted in the new frame. A pick's wall-clock
        // numbers carry untouched, exactly as the commit
        // leaves them. (Run 42421837806's pass on the Now leg
        // was the fallback's luck: the old block was dated a
        // day early, the stale hour missed every row, and the
        // current-block fallback produced the expected value.)
        if vm.isNowSelection {
            vm.selectedDate = vm.locationNowDate()
            vm.selectedHour = vm.locationNowHour()
        }
    }

    /// S6-02 (return half) / RW-125: a picked selection survives
    /// a foreground return untouched; maintenance does nothing.
    @MainActor
    func testFixWave_PickSurvivesReturn() {
        let (vm, day, nowHour) = nowFixture()
        let picked = (nowHour + 5) % 24
        vm.updateSelectedTime(date: day, hour: picked)
        XCTAssertFalse(vm.maintainNowIfNeeded(), "no Now maintenance stands over a pick")
        XCTAssertEqual(vm.selectedHour, picked)
        XCTAssertFalse(vm.isNowSelection)
    }

    /// S6-01 (roll half): a standing Now whose projection has
    /// decayed off the location's now hour is re-anchored by
    /// maintenance.
    @MainActor
    func testFixWave_NowMaintenanceReanchorsDecayedProjection() {
        let (vm, day, nowHour) = nowFixture()
        vm.selectedDate = day
        vm.selectedHour = (nowHour + 23) % 24 // one hour stale
        XCTAssertTrue(vm.isNowSelection)
        XCTAssertTrue(vm.maintainNowIfNeeded(), "the decayed Now projection is repaired")
        XCTAssertEqual(vm.selectedHour, vm.locationNowHour())
    }

    /// Day-chart Now point (Sol's ruling with RW-125): today's
    /// chart point for the current hour carries the live
    /// current-conditions value; another day's chart is the
    /// forecast untouched.
    @MainActor
    func testFixWave_DayChartNowPointSubstitution() {
        let (vm, day, nowHour) = nowFixture()
        let todayChart = vm.dayChart(for: day)
        XCTAssertEqual(todayChart?.entry(forHour: nowHour)?.feelsLikeF ?? -1, 86, accuracy: 0.6,
                       "the chart's Now point is the current-conditions value")
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: day)!
        XCTAssertNil(vm.dayChart(for: tomorrow), "no forecast rows exist for tomorrow in this fixture")
        // The pure substitution itself, on a synthetic model:
        let points = (0..<24).map {
            DayChartHour(hour: $0, feelsLikeF: 50, bracket: .mild, outfitItemNames: ["Tee"])
        }
        let model = DayChartModel.build(points: points)!
        let substituted = DayChartModel.substitutingNowPoint(
            in: model, nowHour: 7,
            currentPoint: DayChartHour(hour: 7, feelsLikeF: 90, bracket: .hot, outfitItemNames: ["Shorts"]))
        XCTAssertEqual(substituted.entry(forHour: 7)?.feelsLikeF, 90)
        XCTAssertEqual(substituted.entry(forHour: 7)?.bracket, .hot,
                       "the band shading follows the substituted point's own bracket")
        XCTAssertEqual(substituted.entry(forHour: 8)?.feelsLikeF, 50, "other hours untouched")
    }

    // MARK: Picker session (S6-21/22/23/24; RW-131)

    func testFixWave_PickerSessionDayChange() {
        let day1 = Date(timeIntervalSince1970: 1_800_000_000)
        let day2 = day1.addingTimeInterval(86_400)
        var draft = TimePickerDraft(
            originalHour: 9, openedInNowState: true, nowHour: 9,
            selectableHours: Array(0..<24), originalDate: day1)
        XCTAssertEqual(draft.commit(), .keepNow, "an untouched Now session keeps Now")
        draft.changeDay(to: day2, selectableHours: [6, 7, 8])
        XCTAssertTrue(draft.dayChanged)
        XCTAssertEqual(draft.workingDate, day2)
        XCTAssertEqual(draft.draftHour, 8, "the hour re-derives to the new day's nearest offered hour")
        // S6-23: a day change alone breaks keep-Now.
        XCTAssertEqual(draft.commit(), .explicitPick(hour: 8))
        XCTAssertEqual(draft.commitDate, day2, "the one commit carries the working date")
        // Stickiness: returning to the original day does not
        // un-touch the session (risk P-2).
        draft.changeDay(to: day1, selectableHours: Array(0..<24))
        XCTAssertTrue(draft.dayChanged)
        XCTAssertEqual(draft.commit(), .explicitPick(hour: 8))
    }

    /// The host's one apply path: (date, commit) lands once.
    @MainActor
    func testFixWave_ApplyPickerCommitLandsDateAndHourOnce() {
        let (vm, day, _) = nowFixture()
        let target = Calendar.current.date(byAdding: .day, value: 1, to: day)!
        vm.applyPickerCommit(date: target, commit: .explicitPick(hour: 18))
        XCTAssertEqual(vm.selectedHour, 18)
        XCTAssertTrue(Calendar.current.isDate(vm.selectedDate, inSameDayAs: target))
        XCTAssertFalse(vm.isNowSelection)
        vm.applyPickerCommit(date: target, commit: .keepNow)
        XCTAssertTrue(vm.isNowSelection, "keep-Now through the session path restores the Now state")
    }

    // MARK: Library bank merge (S6-06) + collection delete (RW-122)

    func testFixWave_LibraryCellsFromBank() {
        // Combination ids carry the production format
        // ({GENDER}_{WEATHER}_{BRACKET}_{TIME}_{hash}) — the
        // library's title lines derive from the id, so a cell
        // whose id does not parse cannot stand in the library
        // at all; the fixture uses real-format ids (the first
        // is the field-recorded banked cell's shape).
        let bankedId = "MALE_CLOUDY_MILD_MIDDAY_491df101"
        let sessionOnlyId = "MALE_RAIN_WARM_DAWN_7bc20455"
        let bank = [BankedLook(combinationId: bankedId, heroId: "h1", imageURL: "https://x/1.jpg")]
        let cells = LooksLibrary.cells(
            fromBank: bank,
            lookStates: [bankedId: .failed(reason: .qualityRefused), sessionOnlyId: .making])
        XCTAssertEqual(cells.count, 2)
        let banked = cells.first { $0.combinationId == bankedId }
        XCTAssertEqual(banked?.state, .ready, "a banked look is ready — its stale session failure is discarded")
        XCTAssertEqual(banked?.imageURL, "https://x/1.jpg", "the banked cell carries its photograph")
        XCTAssertEqual(cells.first { $0.combinationId == sessionOnlyId }?.state, .making,
                       "session cells not yet in the bank still show")
    }

    func testFixWave_CollectionDeleteByEntryAndPlace() {
        func entry(_ day: Int, _ place: String) -> WearIveRunEntry {
            WearIveRunEntry(
                dayStart: Date(timeIntervalSince1970: TimeInterval(day * 86_400)),
                placeName: place, feelsLikeF: 50, actualF: 50, condition: "Clear",
                itemNames: [], itemKeys: [], heroImageReference: "")
        }
        let entries = [entry(10, "New York, NY"), entry(11, "New York, NY"), entry(10, "Miami, FL")]
        let afterPlace = WearIveRunCollection.removingPlace(entries, placeName: "New York, NY")
        XCTAssertEqual(afterPlace.count, 1)
        XCTAssertEqual(afterPlace.first?.placeName, "Miami, FL")
        let target = entries[0]
        let afterOne = WearIveRunCollection.removing(entries, id: target.id)
        XCTAssertEqual(afterOne.count, 2)
        XCTAssertFalse(afterOne.contains { $0.id == target.id })
    }

    // MARK: Source pins for the view-layer seams

    func testFixWave_ViewSeams_sourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(view.contains("viewedNarrationPayload"),
                       "RW-135 §7.0: no viewed-state narration override reaches the bar at all (Seam B, completed — the parameter itself is gone)")
        // RW-135 RE-PIN (Direction A, Nov 7, 2026): the
        // synthesis pins that stood here (inline section + jump
        // chip, dock retired) are superseded by the owner's
        // ruling — the floating dock is restored and the
        // expanded form is the footer section.
        XCTAssertTrue(view.contains("miniBarDock"),
                      "RW-135 Direction A: the floating dock is restored as the Home presence")
        XCTAssertTrue(view.contains("wearIveRunFooterSection"),
                      "RW-135 Direction A (interim): the expanded form is the footer section")
        XCTAssertFalse(view.contains("wearIveRunInlineSection"),
                       "RW-135: the synthesis inline section is removed")
        XCTAssertFalse(view.contains("wearIveRunJumpChip"),
                       "RW-135: the synthesis jump chip is removed — the bar itself is the jump (§7.3)")
        XCTAssertTrue(view.contains("reconcilePersonalLayer"),
                      "landing/foreground run the reconciliation pass")
        XCTAssertTrue(view.contains("HeroPhotoViewer("),
                      "RW-133: the hero opens the full-screen viewer")
        let sheet = try sourceFile("../RunWear/Views/Modals/TimePickerSheet.swift")
        XCTAssertTrue(sheet.contains("applyWorkingDate"),
                      "RW-131: the day sheet confirms into the session")
        XCTAssertTrue(sheet.contains("onSessionConfirm"),
                      "RW-131: Confirm is the session's one commit")
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("func applyPickerCommit"))
        XCTAssertTrue(vm.contains("func handleForeground"))
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("func reconcilePersonalLayer"))
        XCTAssertTrue(store.contains("func refreshBankLibrary"),
                      "the library's genuine server bank read exists")
    }

    // MARK: RW-135 Direction A (owner's ruling, Nov 7, 2026; research §7)
    //
    // Written for the gate to run — NOT run on this VM (no iOS
    // toolchain; see the lane report's headline caveat).

    private func rw135Entry(
        dayStart: Date, place: String, items: [String] = ["Tee", "Shorts"],
        wasUpdated: Bool = false, payload: NarrationPayload? = nil,
        sessionID: String = ""
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: place,
            feelsLikeF: 55, actualF: 58, condition: "Clear",
            itemNames: items, itemKeys: items.map { $0.lowercased() },
            heroImageReference: "hero-1", sessionID: sessionID,
            wasUpdatedInSession: wasUpdated, updatePayload: payload)
    }

    /// §7.8(5) + §7.1: no entry → .preAdd → no line, no bar.
    func testRW135_NoEntryNoBar() {
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: nil, moment: .now, wasUpdatedThisSession: false)
        XCTAssertEqual(state, .preAdd)
        XCTAssertNil(WearIveRunNarration.line(for: state))
    }

    /// §7.8(2) (RW-132 regression): the location-switch sequence
    /// at the derivation seam — before the Miami settle lands,
    /// the bar still names the RECORDED place (New York); the
    /// line names Miami only once the entry itself holds Miami.
    /// There is no frame at which the bar names a place the
    /// entry does not hold, because the line is a pure function
    /// of the entry.
    @MainActor
    func testRW135_LocationSwitch_NarrationNamesOnlyRecordedPlaces() {
        let defaults = UserDefaults(suiteName: "rw135-\(UUID().uuidString)")!
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: defaults), sessionID: "s-rw135")
        let today = Calendar.current.startOfDay(for: Date())
        // The entries carry the model's session identity —
        // the stamp the production recording site always
        // applies (recordTodaysEntry passes
        // `sessionID: collectionModel.sessionID`). An unstamped
        // entry takes the LEGACY day+place identity by design,
        // under which a place move is a second entry, not the
        // session's one entry updating in place (§7.2/GEN-3).
        model.recordSettled(rw135Entry(dayStart: today, place: "New York, NY", sessionID: "s-rw135"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
                todaysEntry: model.sessionEntry(), moment: .now,
                wasUpdatedThisSession: model.sessionEntry()?.wasUpdatedInSession ?? false)),
            "Updated to New York, NY")
        // (RW-135 spec §9.1 RE-PIN: the creation line above is
        // the "Updated to {place}" form — no "Added" line.)
        // The switch is being viewed; nothing has settled. The
        // recorded entry — and therefore the line — is unmoved.
        XCTAssertEqual(model.sessionEntry()?.placeDisplayName, "New York, NY")
        // The settle lands: the entry holds Miami, and only now
        // does the line name it (§7.2 settled-today form).
        model.recordSettled(rw135Entry(dayStart: today, place: "Miami, FL", items: ["Tank"], sessionID: "s-rw135"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
                todaysEntry: model.sessionEntry(), moment: .now,
                wasUpdatedThisSession: model.sessionEntry()?.wasUpdatedInSession ?? false)),
            "Updated to Miami, FL")
        // §7.1/§7.4: exactly one cue in the whole sequence (the
        // creation); the switch moved only the narration version.
        XCTAssertEqual(model.cueCount, 1)
        XCTAssertEqual(model.narrationVersion, 2)
    }

    /// §7.8(3): the future-day sequence — the bar reads the
    /// "Planned for" form while the entry is recorded under a
    /// future day, and switching back to today restores the
    /// "Updated to" form; both from the record.
    func testRW135_FutureDay_PlannedFormAndBack() {
        let calendar = Calendar(identifier: .gregorian)
        let now = utcDay(2026, 11, 7)
        let tomorrow = utcDay(2026, 11, 8)
        let planned = rw135Entry(dayStart: tomorrow, place: "Los Angeles, CA")
        let plannedState = WearIveRunMiniBarModel.state(
            todaysEntry: planned, moment: .now, wasUpdatedThisSession: false,
            now: now, calendar: calendar)
        XCTAssertEqual(plannedState, .planned(place: "Los Angeles, CA", dayLabel: "Tomorrow"))
        XCTAssertEqual(WearIveRunNarration.line(for: plannedState),
                       "Planned for Los Angeles, CA · Tomorrow")
        // A plan further out names its weekday (Nov 10, 2026 is
        // a Tuesday).
        let later = rw135Entry(dayStart: utcDay(2026, 11, 10), place: "Los Angeles, CA")
        XCTAssertEqual(
            WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
                todaysEntry: later, moment: .now, wasUpdatedThisSession: false,
                now: now, calendar: calendar)),
            "Planned for Los Angeles, CA · Tuesday")
        // Back to today: the entry re-settles under today as an
        // in-session update — the "Updated to" form returns.
        let backToday = rw135Entry(dayStart: now, place: "Los Angeles, CA", wasUpdated: true)
        XCTAssertEqual(
            WearIveRunNarration.line(for: WearIveRunMiniBarModel.state(
                todaysEntry: backToday, moment: .now, wasUpdatedThisSession: true,
                now: now, calendar: calendar)),
            "Updated to Los Angeles, CA")
    }

    /// §7.2: the settled-today form names the entry's PLACE even
    /// when the entry's stored update payload is a moment (the
    /// GEN-5 moment form is superseded on the bar).
    func testRW135_SettledTodayNamesThePlace_NotTheMomentPayload() {
        let entry = rw135Entry(
            dayStart: utcDay(2026, 11, 7), place: "Miami, FL",
            wasUpdated: true, payload: .moment(.clock(hour12: 7, isPM: true)))
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: entry, moment: entry.momentReading,
            wasUpdatedThisSession: true,
            now: utcDay(2026, 11, 7), calendar: Calendar(identifier: .gregorian))
        XCTAssertEqual(state, .updated(payload: .place("Miami, FL")))
        XCTAssertEqual(WearIveRunNarration.line(for: state), "Updated to Miami, FL")
    }

    /// §7.2 expanded anatomy: the entry line's day form comes
    /// from the recorded day — Today / Tomorrow / Weekday, Mon D
    /// (Nov 9, 2026 is a Monday) — and the items are the recorded
    /// names, joined.
    func testRW135_ExpandedLine_DayFormFromTheRecord() {
        let calendar = Calendar(identifier: .gregorian)
        let now = utcDay(2026, 11, 7)
        let today = rw135Entry(dayStart: now, place: "New York, NY")
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: today, formattedFeelsLike: "55°F", calendar: calendar, now: now),
            "New York, NY · Today — 55°F feels like · Tee · Shorts")
        let tomorrow = rw135Entry(dayStart: utcDay(2026, 11, 8), place: "New York, NY")
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: tomorrow, formattedFeelsLike: "55°F", calendar: calendar, now: now),
            "New York, NY · Tomorrow — 55°F feels like · Tee · Shorts")
        let monday = rw135Entry(dayStart: utcDay(2026, 11, 9), place: "New York, NY")
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: monday, formattedFeelsLike: "55°F", calendar: calendar, now: now),
            "New York, NY · Monday, Nov 9 — 55°F feels like · Tee · Shorts")
    }

    /// §7.6 geometry: radius 16 in both docked forms, at every
    /// morph progress — it never animates to 0.
    func testRW135_RadiusIsSixteenInBothForms() {
        XCTAssertEqual(WearIveRunMorph.miniCornerRadius, 16, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedCornerRadius, 16, accuracy: 0.0001)
        for progress in [0.0, 0.25, 0.5, 0.75, 1.0] {
            XCTAssertEqual(WearIveRunMorph.cornerRadius(progress: progress), 16, accuracy: 0.0001)
        }
        // RW-135 spec §4.3 RE-PIN: the narration crossfade is
        // ≈210 ms total (out 95 + in 115).
        XCTAssertEqual(WearIveRunMorph.narrationCrossfadeSeconds, 0.21, accuracy: 0.0001)
    }

    /// §7.4/§7.5 sequence: a changed settle's cross-fade parks
    /// while a scroll gesture is live and lands after it ends —
    /// the deferral the dock view applies to its shown line.
    func testRW135_CrossfadeDeferralAcrossAGesture() {
        var deferral = WearIveRunNarrationDeferral()
        // At rest, a new recorded line applies immediately.
        XCTAssertEqual(deferral.offer(line: "Added New York, NY, Now", gestureActive: false),
                       "Added New York, NY, Now")
        // Mid-gesture, a changed line parks — the shown line
        // does not move under the user's hand.
        XCTAssertNil(deferral.offer(line: "Updated to Miami, FL", gestureActive: true))
        XCTAssertEqual(deferral.shownLine, "Added New York, NY, Now")
        // A second change mid-gesture replaces the parked one —
        // only the latest recorded line ever lands.
        XCTAssertNil(deferral.offer(line: "Updated to Chicago, IL", gestureActive: true))
        // The frame after deceleration ends: the parked line is
        // released for the cross-fade.
        XCTAssertEqual(deferral.gestureEnded(), "Updated to Chicago, IL")
        XCTAssertEqual(deferral.shownLine, "Updated to Chicago, IL")
        XCTAssertNil(deferral.gestureEnded())
    }

    /// Direction A source pins: the dock's seat and geometry in
    /// the host, the footer's interim anatomy, and the machine's
    /// one-pulse rule.
    func testRW135_DirectionA_sourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // RW-135 spec §3 RE-PIN (Direction A′, approved Nov 7,
        // 2026): the dock is a true overlay on the root ZStack —
        // the interim safeAreaInset hosting is superseded.
        XCTAssertTrue(view.contains(".overlay(alignment: .bottom)"),
                      "spec §3: the dock hosts as an overlay on the root ZStack")
        XCTAssertFalse(view.contains(".safeAreaInset(edge: .bottom)"),
                       "spec §3: the safe-area inset hosting is removed")
        XCTAssertTrue(view.contains("miniBarDock"),
                      "the restored dock is what the overlay hosts")
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("struct WearIveRunFooterSectionView"),
                      "the interim expanded form is the footer section view")
        XCTAssertTrue(bar.contains("WearIveRunNarration.expandedLine"),
                      "the footer renders §7.2's entry line from the record")
        XCTAssertFalse(bar.contains("viewedNarrationPayload"),
                       "§7.0: the bar carries no live-view narration input")
        let machine = try sourceFile("../RunWear/Models/WearIveRunElementMachine.swift")
        XCTAssertFalse(machine.contains("(.mini, .userCausedUpdate)"),
                       "§7.4: a changed settle is not a cue driver in the machine")
    }

    /// RW-135 spec §3 (Direction A′): the handoff fade is a
    /// pure function of the footer's position — endpoints,
    /// midpoint, and the hit-testing rule (hit-testable only
    /// below fade 0.5).
    func testRW135_HandoffFade_EndpointsMidpointAndHitRule() {
        // Below the window (footer top < 83 pt above the
        // viewport's bottom edge): the dock stands fully.
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: -40), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 0), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 83), 0, accuracy: 0.0001)
        // Window midpoint (101.5 pt): smoothstep(0.5) == 0.5 —
        // the exact point hit-testing turns off (fade < 0.5).
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 101.5), 0.5, accuracy: 0.0001)
        XCTAssertTrue(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 100) < 0.5,
                      "just inside the window the dock still takes hits")
        XCTAssertFalse(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 103) < 0.5,
                       "past the midpoint taps pass through (§3 hit rule)")
        // At and past the window's end (120 pt): handed off.
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 120), 1, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 212), 1, accuracy: 0.0001)
        // Monotonic across the window — position is the only
        // input, so scrolling back up retraces the same frames.
        var previous = -1.0
        for a in stride(from: 83.0, through: 120.0, by: 3.7) {
            let fade = WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: a)
            XCTAssertGreaterThanOrEqual(fade, previous)
            previous = fade
        }
        // Reduce Motion (§5): a step at the window's midpoint.
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 101.4, reduceMotion: true), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.handoffFade(footerTopAboveViewportBottom: 101.5, reduceMotion: true), 1, accuracy: 0.0001)
        // Smoothstep's own endpoints.
        XCTAssertEqual(WearIveRunMorph.smoothstep(-1), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.smoothstep(0), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.smoothstep(0.5), 0.5, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.smoothstep(1), 1, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.smoothstep(2), 1, accuracy: 0.0001)
    }

    /// RW-135 spec §4.4: the glide duration rule —
    /// clamp(450 ms, 350 + 0.55 × distancePt, 950 ms).
    func testRW135_GlideDuration_ClampRule() {
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: 0), 0.45, accuracy: 0.0001,
                       "zero distance still lands on the 450 ms floor")
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: -120), 0.45, accuracy: 0.0001,
                       "negative distance clamps to the floor")
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: 200), 0.46, accuracy: 0.0001,
                       "350 + 0.55 × 200 = 460 ms")
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: 1000), 0.9, accuracy: 0.0001,
                       "350 + 0.55 × 1000 = 900 ms")
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: 1090.9), 0.95, accuracy: 0.0001,
                       "the linear rule reaches the cap at ≈1091 pt")
        XCTAssertEqual(WearIveRunMorph.glideDurationSeconds(distancePt: 5000), 0.95, accuracy: 0.0001,
                       "long pages cap at 950 ms")
    }

    /// RW-135 spec §9.2 glyphs + §4.1 cue peak, source pins:
    /// dock = corners-out (no rotation), footer = chevron-down,
    /// and the doorway element stands in the footer (§2.2).
    func testRW135_GlyphsAndDoorway_sourcePins() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("\"arrow.up.left.and.arrow.down.right\""),
                      "§9.2: the dock's expand control is the corners-out glyph")
        XCTAssertTrue(bar.contains("\"chevron.down\""),
                      "§9.2: the footer's inverse-jump control is chevron-down")
        XCTAssertFalse(bar.contains(".rotationEffect"),
                       "§9.2: no rotation is applied on the dock path (the morph's pure rotation function remains for its unit pins, unused by the views)")
        XCTAssertTrue(bar.contains("Open the collection ›"),
                      "§2.2: the footer carries the collection doorway element")
        XCTAssertTrue(bar.contains("greatVibes(size: 31)"),
                      "§2.2: the footer's wordmark is 31 pt")
        XCTAssertTrue(bar.contains("cornerRadius: 14"),
                      "§2.2: the entry thumbnail's radius is 14")
        // §4.1: the creation cue peak as pinned on the machine.
        XCTAssertEqual(WearIveRunElementMachine.cuePulseScale, 1.018, accuracy: 0.0001)
        // §4.3: the update bounce peak (≈1.5% overshoot).
        XCTAssertEqual(WearIveRunMorph.updateBounceScale, 1.015, accuracy: 0.0001)
    }
}
