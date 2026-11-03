import XCTest
import SwiftUI

/// Pins for RW-26 Phase 2 (iOS): the consolidated tokens, the
/// WeatherPill legibility decision, the "Wear I've Run" collection
/// arithmetic (the cross-platform contract the Android lane pins
/// identically in Kotlin), location recents, the home header
/// composition, the scaffold policy, feedback routing (RW-18), the
/// F7 check order, and the guided-selfie evaluation.
///
/// Dual-membered pure types are compiled into the test target
/// directly, so no @testable import.
final class RW26Phase2Tests: XCTestCase {

    // MARK: - Fixtures

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func day(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = 12
        return utcCalendar.date(from: comps)!
    }

    private func entry(
        _ date: Date,
        place: String,
        feels: Double,
        condition: String = "Clear",
        items: [String] = ["Tee", "Shorts"]
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: utcCalendar.startOfDay(for: date),
            placeName: place,
            feelsLikeF: feels,
            actualF: feels + 2,
            condition: condition,
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: "hero-\(place)-\(Int(feels))"
        )
    }

    private func formatF(_ fahrenheit: Double) -> String {
        "\(Int(round(fahrenheit)))°"
    }

    // MARK: - Tokens (notes §1)

    func testConsolidatedTokenHexes() {
        XCTAssertEqual(AppTheme.groundLightHex, "F5F1E8")
        XCTAssertEqual(AppTheme.sheetLightHex, "FDFBF6")
        XCTAssertEqual(AppTheme.inkLightHex, "17191C")
        XCTAssertEqual(AppTheme.inkSecondaryLightHex, "5F5B52")
        XCTAssertEqual(AppTheme.groundDarkHex, "16130F")
        XCTAssertEqual(AppTheme.sheetDarkHex, "211D17")
        XCTAssertEqual(AppTheme.cardDarkHex, "2A251E")
        XCTAssertEqual(AppTheme.inkDarkHex, "F5F1EA")
        XCTAssertEqual(AppTheme.mutedDarkHex, "B8AEA0")
        XCTAssertEqual(AppTheme.pineAccentDarkHex, "5CC79F")
        XCTAssertEqual(AppTheme.warmWhiteHex, "F5F1EA")
    }

    func testWarmthOnlyInNeutrals() {
        // Pine seed and the temperature set are unchanged by the
        // token pass.
        XCTAssertEqual(AppTheme.temperatureHex(for: .MILD), "10B981")
        XCTAssertEqual(AppTheme.temperatureHex(for: .FREEZING), "8B5CF6")
    }

    // MARK: - WeatherPill legibility

    func testWeatherPillContentIsInkInLightWhiteInDark() {
        // White on the cream sheet measures ~1.03:1 (fails every
        // floor); ink on the sheet is 17.03:1 per the design's own
        // contrast table. Dark mode keeps white (~16.7:1 on the
        // Warm Charcoal sheet).
        XCTAssertEqual(AppTheme.weatherPillContentHex(isDark: false), "17191C")
        XCTAssertEqual(AppTheme.weatherPillContentHex(isDark: true), "FFFFFF")
    }

    // MARK: - Home header composition (notes §7.1–7.2)

    func testHomeHeaderCopy() {
        XCTAssertEqual(HomeHeaderCopy.feelsLikeLabel, "Feels like")
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(place: "New York", moment: "Now"),
            "New York · Now"
        )
        // The line carries the actual temperature ONLY.
        XCTAssertEqual(HomeHeaderCopy.actualLine(formattedTemperature: "38°"), "Actual 38°")
        XCTAssertEqual(HomeHeaderCopy.actualLine(formattedTemperature: "63°"), "Actual 63°")
    }

    func testMiniBarTranslucencyMatchesSettledTints() {
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: false),
            .translucentMaterial(sheetOpacity: 0.85)
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: true),
            .translucentMaterial(sheetOpacity: 0.82)
        )
    }

    func testExpandedNarrationLine() {
        let e = entry(day(2026, 11, 2), place: "New York", feels: 41, items: ["A", "B", "C", "D", "E"])
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: e, formattedFeelsLike: "41°"),
            "New York · Today — 41° feels like · 5 items"
        )
    }

    // MARK: - Collection: one entry per day per place

    func testUpsertLastSettledVersionWins() {
        let d = day(2026, 11, 2)
        var entries = WearIveRunCollection.upserting([], entry(d, place: "New York", feels: 41))
        entries = WearIveRunCollection.upserting(entries, entry(d, place: "New York", feels: 43))
        XCTAssertEqual(entries.count, 1)
        XCTAssertEqual(entries.first?.feelsLikeF, 43)
        // A different place the same day is a different entry.
        entries = WearIveRunCollection.upserting(entries, entry(d, place: "Boston", feels: 50))
        XCTAssertEqual(entries.count, 2)
        // Place identity folds case and whitespace.
        entries = WearIveRunCollection.upserting(entries, entry(d, place: " new york ", feels: 44))
        XCTAssertEqual(entries.count, 2)
        XCTAssertEqual(entries.first { $0.placeKey == "new york" }?.feelsLikeF, 44)
    }

    func testStoreRoundTripAndTrash() {
        let defaults = UserDefaults(suiteName: "rw26-tests-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let d = day(2026, 11, 2)
        store.record(entry(d, place: "New York", feels: 41))
        store.record(entry(d, place: "New York", feels: 45))
        XCTAssertEqual(store.entries.count, 1)
        XCTAssertEqual(store.todaysEntry(placeName: "New York", today: d, calendar: utcCalendar)?.feelsLikeF, 45)
        // Trash: today's entry goes in one call, no confirmation.
        store.remove(dayStart: utcCalendar.startOfDay(for: d), placeName: "New York")
        XCTAssertTrue(store.entries.isEmpty)
    }

    // MARK: - Collection: chapters, stays, summaries

    func testSingleStayChapterSummary() {
        // New York, Oct 25 – Nov 2 2026, nine consecutive days,
        // feels-like 24°–58° (the §2 range-fix case).
        var entries: [WearIveRunEntry] = []
        for offset in 0..<9 {
            let date = utcCalendar.date(byAdding: .day, value: offset, to: day(2026, 10, 25))!
            let feels = offset == 0 ? 24.0 : (offset == 8 ? 58.0 : 40.0)
            entries.append(entry(date, place: "New York", feels: feels))
        }
        let chapters = WearIveRunCollection.chapters(from: entries, filter: .all, calendar: utcCalendar)
        XCTAssertEqual(chapters.count, 1)
        let chapter = chapters[0]
        XCTAssertEqual(chapter.dayCount, 9)
        XCTAssertEqual(chapter.stays.count, 1)
        XCTAssertEqual(chapter.cover.feelsLikeF, 58) // most recent card is the cover
        XCTAssertEqual(
            chapter.summaryLine(formatTemperature: formatF, calendar: utcCalendar),
            "9 days · Oct 25 – Nov 2 · 24°–58°"
        )
        // Days newest-first.
        XCTAssertEqual(chapter.entries.first?.feelsLikeF, 58)
        XCTAssertEqual(chapter.entries.last?.feelsLikeF, 24)
    }

    func testMultiStayChapterSummaryNeverImpliesContiguity() {
        // Frame 50's shape: 3 stays · 10 days · Last stay · Mar 11–13.
        var entries: [WearIveRunEntry] = []
        for d in [1, 2, 3] { entries.append(entry(day(2026, 3, d), place: "Denver", feels: 30)) }
        for d in [6, 7, 8, 9] { entries.append(entry(day(2026, 3, d), place: "Denver", feels: 45)) }
        for d in [11, 12, 13] { entries.append(entry(day(2026, 3, d), place: "Denver", feels: 52)) }
        let chapters = WearIveRunCollection.chapters(from: entries, filter: .all, calendar: utcCalendar)
        let chapter = chapters[0]
        XCTAssertEqual(chapter.stays.count, 3)
        XCTAssertEqual(chapter.dayCount, 10)
        // Stays newest-first; the last stay is Mar 11–13.
        XCTAssertEqual(chapter.stays.first?.firstDay, utcCalendar.startOfDay(for: day(2026, 3, 11)))
        let summary = chapter.summaryLine(formatTemperature: formatF, calendar: utcCalendar)
        XCTAssertTrue(summary.hasPrefix("3 stays · 10 days · Last stay · Mar 11–13"), summary)
        XCTAssertFalse(summary.contains("Mar 1 – Mar 13"))
    }

    func testSameMonthSpan() {
        XCTAssertEqual(
            WearIveRunDateFormatting.span(from: day(2026, 3, 11), to: day(2026, 3, 13), calendar: utcCalendar),
            "Mar 11–13"
        )
        XCTAssertEqual(
            WearIveRunDateFormatting.span(from: day(2026, 3, 11), to: day(2026, 3, 11), calendar: utcCalendar),
            "Mar 11"
        )
    }

    func testWeatherFilterRecomputesSummaries() {
        var entries: [WearIveRunEntry] = []
        entries.append(entry(day(2026, 1, 5), place: "Chicago", feels: 20, condition: "Snow"))
        entries.append(entry(day(2026, 1, 6), place: "Chicago", feels: 50, condition: "Rain"))
        entries.append(entry(day(2026, 1, 7), place: "Chicago", feels: 90, condition: "Clear"))
        let snow = WearIveRunCollection.chapters(from: entries, filter: .snow, calendar: utcCalendar)
        XCTAssertEqual(snow.count, 1)
        XCTAssertEqual(snow[0].dayCount, 1)
        XCTAssertEqual(
            snow[0].summaryLine(formatTemperature: formatF, calendar: utcCalendar),
            "1 day · Jan 5 · 20°"
        )
        // A snowy 20° day is Cold AND Snow; the 90° day is Heat only.
        let cold = WearIveRunCollection.chapters(from: entries, filter: .cold, calendar: utcCalendar)
        XCTAssertEqual(cold[0].dayCount, 1)
        let heat = WearIveRunCollection.chapters(from: entries, filter: .heat, calendar: utcCalendar)
        XCTAssertEqual(heat[0].dayCount, 1)
        let rain = WearIveRunCollection.chapters(from: entries, filter: .rain, calendar: utcCalendar)
        XCTAssertEqual(rain[0].dayCount, 1)
        // Classifier edges: cold < 40, heat >= 85.
        XCTAssertTrue(WearIveRunWeatherClassifier.categories(feelsLikeF: 39.9, condition: "Clear").contains(.cold))
        XCTAssertFalse(WearIveRunWeatherClassifier.categories(feelsLikeF: 40, condition: "Clear").contains(.cold))
        XCTAssertTrue(WearIveRunWeatherClassifier.categories(feelsLikeF: 85, condition: "Clear").contains(.heat))
    }

    func testHometownMonthGroups() {
        // A hometown-scale chapter groups year → month with
        // computed counts (frame 51's arithmetic shape).
        var entries: [WearIveRunEntry] = []
        for d in 1...23 {
            entries.append(entry(day(2026, 2, d), place: "New York", feels: Double(20 + d)))
        }
        for d in 1...10 {
            entries.append(entry(day(2027, 1, d), place: "New York", feels: 30))
        }
        let chapter = WearIveRunCollection.chapters(from: entries, filter: .all, calendar: utcCalendar)[0]
        let groups = WearIveRunCollection.monthGroups(for: chapter, calendar: utcCalendar)
        XCTAssertEqual(groups.count, 2)
        XCTAssertEqual(groups[0].year, 2027) // newest first
        XCTAssertEqual(groups[0].dayCount, 10)
        XCTAssertEqual(groups[1].year, 2026)
        XCTAssertEqual(groups[1].month, 2)
        XCTAssertEqual(groups[1].dayCount, 23)
        XCTAssertEqual(groups[1].feelsLikeRangeF.min, 21)
        XCTAssertEqual(groups[1].feelsLikeRangeF.max, 43)
        XCTAssertEqual(
            WearIveRunDateFormatting.monthLabel(year: 2026, month: 2, calendar: utcCalendar),
            "February 2026"
        )
    }

    func testTodayPinning() {
        let today = day(2026, 11, 2)
        let entries = [
            entry(day(2026, 11, 1), place: "New York", feels: 40),
            entry(today, place: "New York", feels: 41),
            entry(today, place: "Boston", feels: 55)
        ]
        let todays = WearIveRunCollection.todaysEntries(from: entries, today: today, calendar: utcCalendar)
        XCTAssertEqual(todays.count, 2)
        XCTAssertTrue(todays.allSatisfy { $0.placeName == "New York" || $0.placeName == "Boston" })
    }

    // MARK: - Location recents (Section E)

    func testRecentsCapOrderAndDedupe() {
        var list: [RecentLocation] = []
        for i in 1...6 {
            list = RecentLocations.recording(
                list,
                selection: RecentLocation(name: "City \(i)", latitude: Double(i), longitude: Double(i))
            )
        }
        XCTAssertEqual(list.count, RecentLocations.cap) // 5
        XCTAssertEqual(RecentLocations.cap, 5)
        XCTAssertEqual(list.first?.name, "City 6") // most recent first
        XCTAssertEqual(list.last?.name, "City 2") // City 1 fell off
        // Re-selecting a place moves it to the front, no duplicate.
        list = RecentLocations.recording(
            list,
            selection: RecentLocation(name: "City 3", latitude: 3, longitude: 3)
        )
        XCTAssertEqual(list.count, 5)
        XCTAssertEqual(list.first?.name, "City 3")
        XCTAssertEqual(list.filter { $0.name == "City 3" }.count, 1)
        // Removal (the swipe).
        list = RecentLocations.removing(list, id: list[0].id)
        XCTAssertEqual(list.first?.name, "City 6")
        XCTAssertEqual(list.count, 4)
    }

    func testRecentsStorePersistence() {
        let defaults = UserDefaults(suiteName: "rw26-recents-\(UUID().uuidString)")!
        let store = RecentLocationsStore(defaults: defaults)
        XCTAssertTrue(store.recents.isEmpty) // first run: section absent
        store.record(RecentLocation(name: "Kansas City", latitude: 39.1, longitude: -94.6))
        XCTAssertEqual(RecentLocationsStore(defaults: defaults).recents.first?.name, "Kansas City")
    }

    // MARK: - Scaffold / converging policy (§9 silence)

    func testScaffoldPolicyMatrix() {
        // Exact look ready + on screen: nothing extra.
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: true, lookState: .ready),
            .exact
        )
        // Personal photo on screen while the cell makes: that is
        // the user's own banked scaffold — badge, no spinner.
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: true, lookState: .making),
            .scaffold(badge: PersonalServePolicy.scaffoldBadge)
        )
        // Nothing on screen yet: the honest wait.
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: false, lookState: .making),
            .making
        )
        // Failed: stock quietly; explanation lives in the library.
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: false, lookState: .failed(reason: .error)),
            .failedQuiet(explanation: PersonalServePolicy.failedExplanation)
        )
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: false, lookState: .none),
            .none
        )
        // Silence rule: neither scaffold nor failure copy carries
        // an upsell or a meter.
        XCTAssertFalse(PersonalServePolicy.scaffoldBadge.lowercased().contains("premium"))
        XCTAssertFalse(PersonalServePolicy.failedExplanation.lowercased().contains("premium"))
    }

    // MARK: - Feedback routing (RW-18)

    func testFeedbackRoutingSeparation() {
        // Human categories are Android's five ids, verbatim.
        XCTAssertEqual(
            FeedbackRouting.humanCategories.map(\.id),
            ["something_wrong", "recommendation_off", "confusing", "idea", "other"]
        )
        XCTAssertTrue(FeedbackRouting.isHumanCategory("something_wrong"))
        // Machine traffic has its OWN names and destinations —
        // never something_wrong, never the human destination.
        XCTAssertEqual(FeedbackRouting.diagnosticsCategory, "diagnostics")
        XCTAssertEqual(FeedbackRouting.crashReportCategory, "crash_report")
        XCTAssertFalse(FeedbackRouting.isHumanCategory("diagnostics"))
        XCTAssertFalse(FeedbackRouting.isHumanCategory("crash_report"))
        XCTAssertEqual(
            FeedbackRouting.destination(forMachineCategory: "diagnostics"),
            .events
        )
        XCTAssertEqual(
            FeedbackRouting.destination(forMachineCategory: "crash_report"),
            .crash
        )
        XCTAssertNil(FeedbackRouting.destination(forMachineCategory: "something_wrong"))
        XCTAssertEqual(
            FeedbackRouting.Destination.feedback.urlString,
            "https://api.uptimeventuresllc.com/v1/feedback/runwear"
        )
        XCTAssertEqual(FeedbackRouting.Destination.feedback.schema, "runwear-feedback/1")
        XCTAssertEqual(FeedbackRouting.Destination.crash.schema, "runwear-crash/1")
        // The picker's cases resolve to the same wire ids.
        XCTAssertEqual(FeedbackCategory.somethingWrong.wireId, "something_wrong")
        XCTAssertEqual(FeedbackCategory.allCases.count, 5)
    }

    func testFeedbackPayloadShape() throws {
        let data = try XCTUnwrap(FeedbackPayloads.feedbackData(
            appVersion: "1.0 (18)",
            installId: "install-1",
            categoryId: "idea",
            text: "More trail routes, please.",
            replyEmail: " runner@example.com ",
            entryPoint: "settings",
            bundle: ["App version": "1.0 (18)"]
        ))
        let json = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )
        XCTAssertEqual(json["schema"] as? String, "runwear-feedback/1")
        XCTAssertEqual(json["app"] as? String, "runwear")
        XCTAssertEqual(json["platform"] as? String, "ios")
        XCTAssertEqual(json["category"] as? String, "idea")
        XCTAssertEqual(json["text"] as? String, "More trail routes, please.")
        XCTAssertEqual(json["replyEmail"] as? String, "runner@example.com")
        XCTAssertEqual((json["bundle"] as? [String: String])?["App version"], "1.0 (18)")
        // Blank reply email is omitted, never sent empty.
        let data2 = try XCTUnwrap(FeedbackPayloads.feedbackData(
            appVersion: "1.0 (18)", installId: "install-1", categoryId: "other",
            text: "Hi", replyEmail: "  ", entryPoint: nil, bundle: [:]
        ))
        let json2 = try XCTUnwrap(JSONSerialization.jsonObject(with: data2) as? [String: Any])
        XCTAssertNil(json2["replyEmail"])
        XCTAssertNil(json2["entryPoint"])
    }

    func testFeedbackDisclosureLines() {
        XCTAssertEqual(
            FeedbackPayloads.disclosureLines(bundle: ["App version": "1.0 (18)"]),
            ["App version: 1.0 (18)"]
        )
    }

    // MARK: - F7 check order (harmonized: count → darkness → framing)

    func testF7FaceCountBeforeDarkness() {
        // A dark photo with NO face reads as the face problem,
        // not the darkness — the harmonized order.
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 0, largestFaceAreaRatio: 0, meanLuminance: 0.05),
            .faceUnclear
        )
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 2, largestFaceAreaRatio: 0.2, meanLuminance: 0.05),
            .multiplePeople
        )
        // One face, dark: darkness is next.
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.05),
            .tooDark
        )
        // Framing last: too small (C3E), then too close.
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.01, meanLuminance: 0.5),
            .faceTooSmall
        )
        XCTAssertEqual(PhotoRejection.faceTooSmall.specString, SelfieGuidance.retakeFaceTooSmall)
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.6, meanLuminance: 0.5),
            .tooClose
        )
    }

    // MARK: - Guided-selfie evaluation

    func testSelfieGuidanceMatrix() {
        let passing = SelfieGuidance.evaluate(
            faceCount: 1, largestFaceAreaRatio: 0.25, yawDegrees: 3, meanLuminance: 0.5
        )
        XCTAssertTrue(passing.allPassed)
        XCTAssertNil(passing.coachingLine)

        let noFace = SelfieGuidance.evaluate(
            faceCount: 0, largestFaceAreaRatio: 0, yawDegrees: 0, meanLuminance: 0.5
        )
        XCTAssertEqual(noFace.firstFailure, .faceFound)
        XCTAssertEqual(noFace.coachingLine, "Center your face in the oval.")

        let small = SelfieGuidance.evaluate(
            faceCount: 1, largestFaceAreaRatio: 0.03, yawDegrees: 0, meanLuminance: 0.5
        )
        XCTAssertEqual(small.firstFailure, .faceLargeEnough)

        let turned = SelfieGuidance.evaluate(
            faceCount: 1, largestFaceAreaRatio: 0.25, yawDegrees: 40, meanLuminance: 0.5
        )
        XCTAssertEqual(turned.firstFailure, .faceFrontal)

        let dark = SelfieGuidance.evaluate(
            faceCount: 1, largestFaceAreaRatio: 0.25, yawDegrees: 0, meanLuminance: 0.1
        )
        XCTAssertEqual(dark.firstFailure, .wellLit)
        XCTAssertEqual(dark.coachingLine, "Face a window or a brighter light.")
    }
}
