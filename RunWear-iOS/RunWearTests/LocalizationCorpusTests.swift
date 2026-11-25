import XCTest

/// WP-R3 narration twin corpus: the RW-150 grammar class table from
/// R2's Android suite (Rw150NarrationGrammarTest.kt @ d5b0a93),
/// executed against the iOS production builders
/// (WearIveRunMiniBarModel.state -> WearIveRunNarration.line for the
/// dock, WearIveRunNarration.expandedLine for the footer,
/// WearIveRunViewedNarration for the change narration). The corpus
/// proves the extraction changed no narration output: every line is
/// composed through the AppEnglish templates the extraction
/// introduced, and renders byte-identically to the pre-extraction
/// literals.
///
/// DESIGNED DELTAS from the Android table (documented residuals,
/// never defects — see the WP-R3 report):
///  D-08: the iOS expanded/footer line carries NO moment segment;
///        Android's footer names the picked moment a second time.
///  The iOS planned day label is weekday-only ("Thursday") where
///        Android's class-5 dock carries "Thursday, Nov 12"; the
///        iOS footer's day segment DOES carry "Thursday, Nov 12".
///  Boundary: Android's dock builder branches on the entry's date
///        and keeps a place-only short form for past entries. The
///        iOS state builder has no past branch (production only
///        ever feeds it today's or a future entry — the bar binds
///        the current day) and renders the full form; the row below
///        pins the iOS behavior so the divergence is explicit.
final class LocalizationCorpusTests: XCTestCase {

    private func corpusCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func corpusDate(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0) -> Date {
        var components = DateComponents()
        components.year = year; components.month = month; components.day = day
        components.hour = hour
        return corpusCalendar().date(from: components)!
    }

    /// R2's entry defaults: today = Monday 2026-11-09, place
    /// "New York, New York", items [Long Sleeve, Tights], feels 41.
    private func corpusEntry(
        dayStart: Date,
        place: String = "New York, New York",
        moment: MomentReading = .now,
        payload: NarrationPayload? = nil
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: place,
            feelsLikeF: 41, actualF: 44, condition: "Cool",
            itemNames: ["Long Sleeve", "Tights"],
            itemKeys: ["longSleeve", "tights"],
            heroImageReference: "", sessionID: "s-corpus",
            wasUpdatedInSession: payload != nil,
            momentReading: moment, updatePayload: payload)
    }

    private var now: Date { corpusDate(2026, 11, 9, 12) }
    private var today: Date { corpusDate(2026, 11, 9) }

    private func dock(_ entry: WearIveRunEntry) -> String {
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: entry, moment: entry.momentReading,
            wasUpdatedThisSession: entry.wasUpdatedInSession,
            now: now, calendar: corpusCalendar())
        return WearIveRunNarration.line(for: state)!
    }

    private func footer(_ entry: WearIveRunEntry) -> String {
        WearIveRunNarration.expandedLine(
            for: entry, formattedFeelsLike: "41°F",
            calendar: corpusCalendar(), now: now)
    }

    private func updateNarration(
        entryPlaceDisplayName: String,
        entryMoment: MomentReading,
        viewedPlace: SimplePlace? = nil,
        viewedDate: Date,
        viewedHour: Int = 0,
        isViewingNow: Bool = false
    ) -> String? {
        guard let payload = WearIveRunViewedNarration.payload(
            entryPlaceDisplayName: entryPlaceDisplayName,
            entryMoment: entryMoment,
            viewedPlace: viewedPlace,
            viewedDate: viewedDate,
            viewedHour: viewedHour,
            isViewingNow: isViewingNow,
            calendar: corpusCalendar(), now: now)
        else { return nil }
        return AppEnglish.wirUpdatedLine(payload)
    }

    // MARK: - The class table

    func testClass1_PlaceAtNow() {
        let e = corpusEntry(dayStart: today, place: "Miami, Florida")
        XCTAssertEqual(dock(e), "Updated to Miami, FL, Now")
        XCTAssertEqual(footer(e),
                       "Miami, FL · Today — 41°F feels like · Long Sleeve · Tights")
    }

    func testClass2_PlaceAtPickedTime() {
        let e = corpusEntry(dayStart: today, place: "Phoenix, Arizona",
                            moment: .clock(hour12: 2, isPM: true))
        XCTAssertEqual(dock(e), "Updated to Phoenix, AZ, 2 p.m.")
        // D-08 delta: Android's footer repeats the moment
        // ("· 2 p.m. —"); the iOS expanded line never carries it.
        XCTAssertEqual(footer(e),
                       "Phoenix, AZ · Today — 41°F feels like · Long Sleeve · Tights")
    }

    func testClass3_TimeOnlyChange() {
        let before = corpusEntry(dayStart: today)
        let after = corpusEntry(dayStart: today,
                                moment: .clock(hour12: 3, isPM: true))
        XCTAssertEqual(dock(before), "Updated to New York, NY, Now")
        XCTAssertEqual(dock(after), "Updated to New York, NY, 3 p.m.")
        XCTAssertNotEqual(dock(before), dock(after))
        // The change narration agrees: a moment payload.
        XCTAssertEqual(
            updateNarration(entryPlaceDisplayName: "New York, NY",
                            entryMoment: .now,
                            viewedDate: today, viewedHour: 15),
            "Updated to 3 p.m.")
        // iOS's second production path to the same line: an entry
        // whose stored update payload IS the moment.
        let stored = corpusEntry(dayStart: today,
                                 moment: .clock(hour12: 3, isPM: true),
                                 payload: .moment(.clock(hour12: 3, isPM: true)))
        XCTAssertEqual(dock(stored), "Updated to 3 p.m.")
    }

    func testClass4_DateChangeDayOnly() {
        let e = corpusEntry(dayStart: corpusDate(2026, 11, 10))
        XCTAssertEqual(dock(e), "Planned for New York, NY · Tomorrow")
        XCTAssertEqual(footer(e),
                       "New York, NY · Tomorrow — 41°F feels like · Long Sleeve · Tights")
    }

    func testClass5_DateAndTimeChange() {
        let e = corpusEntry(dayStart: corpusDate(2026, 11, 12),
                            moment: .clock(hour12: 7, isPM: true))
        // Day-label delta: iOS planned label is weekday-only;
        // Android's dock reads "Thursday, Nov 12".
        XCTAssertEqual(dock(e), "Planned for New York, NY · Thursday · 7 p.m.")
        // Footer delta (D-08): no "· 7 p.m." segment; the day
        // segment carries the full "Thursday, Nov 12" form.
        XCTAssertEqual(footer(e),
                       "New York, NY · Thursday, Nov 12 — 41°F feels like · Long Sleeve · Tights")
        // RW-98's future-moment example, through the change
        // narration — identical on both platforms.
        XCTAssertEqual(
            updateNarration(entryPlaceDisplayName: "New York, NY",
                            entryMoment: .now,
                            viewedDate: corpusDate(2026, 11, 12), viewedHour: 19),
            "Updated to November 12 7 p.m.")
    }

    func testClass6_ReturnToNow() {
        let nowEntry = corpusEntry(dayStart: today)
        XCTAssertEqual(dock(nowEntry), "Updated to New York, NY, Now")
        XCTAssertEqual(
            updateNarration(entryPlaceDisplayName: "New York, NY",
                            entryMoment: .clock(hour12: 2, isPM: true),
                            viewedDate: today, isViewingNow: true),
            "Updated to Now")
    }

    func testClass7_PlaceChangeAtPickedTime() {
        XCTAssertEqual(
            updateNarration(entryPlaceDisplayName: "Boston, MA",
                            entryMoment: .now,
                            viewedPlace: SimplePlace(city: "Phoenix", region: "AZ"),
                            viewedDate: today, viewedHour: 14),
            "Updated to Phoenix, AZ, 2 p.m.")
        // A place change at Now is RW-98's verbatim example.
        XCTAssertEqual(
            updateNarration(entryPlaceDisplayName: "Boston, MA",
                            entryMoment: .now,
                            viewedPlace: SimplePlace(city: "Miami", region: "FL"),
                            viewedDate: today, isViewingNow: true),
            "Updated to Miami, FL, Now")
    }

    func testBoundary_PastEntry() {
        // Android keeps the place-only short form for a past rest
        // entry. The iOS builder renders the full form (see the
        // header note): pinned here so the divergence is explicit,
        // never silently unified.
        let e = corpusEntry(dayStart: corpusDate(2026, 11, 7))
        XCTAssertEqual(dock(e), "Updated to New York, NY, Now")
    }

    func testDockAndFooterMomentAgreement() {
        // Picked moments: the dock names the moment; the iOS
        // footer deliberately does not (D-08) — the inverse of
        // Android's agreement rule, pinned so neither side drifts.
        let picked = corpusEntry(dayStart: today,
                                 moment: .clock(hour12: 2, isPM: true))
        XCTAssertTrue(dock(picked).contains("2 p.m."))
        XCTAssertFalse(footer(picked).contains("2 p.m."))
        let planned = corpusEntry(dayStart: corpusDate(2026, 11, 12),
                                  moment: .clock(hour12: 7, isPM: true))
        XCTAssertTrue(dock(planned).contains("7 p.m."))
        XCTAssertFalse(footer(planned).contains("7 p.m."))
        // At Now today, the dock says ", Now".
        XCTAssertTrue(dock(corpusEntry(dayStart: today)).hasSuffix(", Now"))
    }
}

/// Catalog <-> facade parity: for a representative sentinel set
/// spanning every surface in the app catalog (plain, formatted,
/// plural, narration templates, share captions, widget ages), the
/// runtime-localized facade (AppStrings, resolved through the host
/// app's compiled Localizable catalog) renders exactly what the
/// pinned-English facade (AppEnglish) renders under en. A missing
/// catalog key would surface here as the raw key string.
final class LocalizationCatalogParityTests: XCTestCase {

    func testPlainAccessors() {
        XCTAssertEqual(AppStrings.wirWordmark, AppEnglish.wirWordmark)
        XCTAssertEqual(AppStrings.heroNow, "Now")
        XCTAssertEqual(AppStrings.settingsTitleText, AppEnglish.settingsTitleText)
        XCTAssertEqual(AppStrings.widgetAgeJustNowUpper, "JUST NOW")
        XCTAssertEqual(AppStrings.widgetAgeJustNowLower, "just now")
        XCTAssertEqual(AppStrings.weatherBracketHotRange, AppEnglish.weatherBracketHotRange)
        XCTAssertEqual(AppStrings.feedbackIdea, AppEnglish.feedbackIdea)
        XCTAssertEqual(AppStrings.mimScriptMark, "Make It Me")
    }

    func testFormattedAccessors() {
        XCTAssertEqual(AppStrings.detailPercent(37), "37%")
        XCTAssertEqual(AppStrings.detailPercent(37), AppEnglish.detailPercent(37))
        XCTAssertEqual(AppStrings.a11yHumidity(41), AppEnglish.a11yHumidity(41))
        XCTAssertEqual(AppStrings.windValue(9), AppEnglish.windValue(9))
        XCTAssertEqual(AppStrings.chartReadout("3 PM", "41°"),
                       AppEnglish.chartReadout("3 PM", "41°"))
        XCTAssertEqual(
            AppStrings.shareCaptionShort("New York, NY", "this morning", 41, "the call", "Tee"),
            AppEnglish.shareCaptionShort("New York, NY", "this morning", 41, "the call", "Tee"))
        XCTAssertEqual(AppStrings.wirUpdatedLine("Miami, FL, Now"),
                       "Updated to Miami, FL, Now")
        XCTAssertEqual(AppStrings.wirPlannedLine("New York, NY", " · Tomorrow"),
                       AppEnglish.wirPlannedLine("New York, NY", " · Tomorrow"))
        XCTAssertEqual(AppStrings.widgetStatsLine(41, 3),
                       AppEnglish.widgetStatsLine(41, 3))
    }

    func testPluralAccessors() {
        XCTAssertEqual(AppStrings.wirItemsCount(1), "1 item")
        XCTAssertEqual(AppStrings.wirItemsCount(5), "5 items")
        XCTAssertEqual(AppStrings.wirDaysCount(1), AppEnglish.wirDaysCount(1))
        XCTAssertEqual(AppStrings.wirStaysCount(3), AppEnglish.wirStaysCount(3))
        XCTAssertEqual(AppStrings.outfitOutfits(1), "outfit")
        XCTAssertEqual(AppStrings.outfitOutfits(2), "outfits")
        XCTAssertEqual(AppStrings.wirLastSavedMinutes(1), AppEnglish.wirLastSavedMinutes(1))
        XCTAssertEqual(AppStrings.wirLastSavedHours(2), AppEnglish.wirLastSavedHours(2))
        XCTAssertEqual(AppStrings.outfitItemsForRun(1), AppEnglish.outfitItemsForRun(1))
    }
}
