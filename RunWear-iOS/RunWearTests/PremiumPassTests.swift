import XCTest

/// Pins for the 2026-10-30 iOS premium pass (UX audit RW-6 cluster):
///
/// - RW-6.8: the first-run temperature unit comes from the device
///   locale's measurement system, never a hard-coded Fahrenheit.
/// - RW-6.6: the stagger cascade is capped — items past the cap reuse
///   the last delay instead of drifting later down a long list.
/// - RW-6.4: the shared comfort control binds through ComfortLevel's
///   slider index — the round trip must be lossless for all 5 stops.
/// - R-15: the rating ask waits for proven value (a recommendation
///   viewed on 3 distinct days), fires once, then goes quiet for a
///   season (~90 days) with the day count reset.
/// - R-15: the feedback composer refuses an empty message and builds
///   a pre-addressed email carrying category, message, version, and
///   the optional reply address.
final class PremiumPassTests: XCTestCase {

    private func isolatedDefaults() -> UserDefaults {
        UserDefaults(suiteName: "PremiumPassTests.\(UUID().uuidString)")!
    }

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func day(_ year: Int, _ month: Int, _ day: Int) -> Date {
        utcCalendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    // MARK: - RW-6.8 locale unit default

    func testDefaultUnitFollowsMeasurementSystem() {
        XCTAssertEqual(TemperatureUnit.defaultUnit(for: .metric), .celsius)
        XCTAssertEqual(TemperatureUnit.defaultUnit(for: .us), .fahrenheit)
        XCTAssertEqual(TemperatureUnit.defaultUnit(for: .uk), .fahrenheit)
    }

    // MARK: - RW-6.6 stagger cap

    func testStaggerDelayCapsCascade() {
        let base = 0.1
        let step = 0.05
        XCTAssertEqual(StaggerTiming.effectiveDelay(index: 0, baseDelay: base, staggerDelay: step), base, accuracy: 0.0001)
        XCTAssertEqual(StaggerTiming.effectiveDelay(index: 4, baseDelay: base, staggerDelay: step), base + 4 * step, accuracy: 0.0001)
        // Past the cap the delay stops growing.
        XCTAssertEqual(StaggerTiming.effectiveDelay(index: 5, baseDelay: base, staggerDelay: step), base + 4 * step, accuracy: 0.0001)
        XCTAssertEqual(StaggerTiming.effectiveDelay(index: 12, baseDelay: base, staggerDelay: step), base + 4 * step, accuracy: 0.0001)
        // A negative index never produces a negative delay contribution.
        XCTAssertEqual(StaggerTiming.effectiveDelay(index: -3, baseDelay: base, staggerDelay: step), base, accuracy: 0.0001)
    }

    // MARK: - RW-6.4 comfort slider binding

    func testComfortLevelSliderIndexRoundTrip() {
        for level in ComfortLevel.allCases {
            XCTAssertEqual(ComfortLevel.from(sliderIndex: level.sliderIndex), level)
        }
        XCTAssertEqual(ComfortLevel.sortedCases.map(\.sliderIndex), [0, 1, 2, 3, 4])
        // Out-of-range indices clamp to neutral rather than trapping.
        XCTAssertEqual(ComfortLevel.from(sliderIndex: 99), .neutral)
        XCTAssertEqual(ComfortLevel.from(sliderIndex: -1), .neutral)
    }

    // MARK: - R-15 rating tracker

    func testRatingPromptNeedsThreeDistinctDays() {
        let tracker = RatingPromptTracker(defaults: isolatedDefaults(), calendar: utcCalendar)

        tracker.recordRecommendationViewed(on: day(2026, 10, 1))
        tracker.recordRecommendationViewed(on: day(2026, 10, 2))
        XCTAssertFalse(tracker.shouldPrompt(now: day(2026, 10, 2)))

        tracker.recordRecommendationViewed(on: day(2026, 10, 3))
        XCTAssertTrue(tracker.shouldPrompt(now: day(2026, 10, 3)))
    }

    func testRatingPromptSameDayViewsCountOnce() {
        let tracker = RatingPromptTracker(defaults: isolatedDefaults(), calendar: utcCalendar)

        tracker.recordRecommendationViewed(on: day(2026, 10, 1))
        tracker.recordRecommendationViewed(on: day(2026, 10, 1))
        tracker.recordRecommendationViewed(on: day(2026, 10, 1))
        XCTAssertEqual(tracker.viewedDayCount, 1)
        XCTAssertFalse(tracker.shouldPrompt(now: day(2026, 10, 1)))
    }

    func testRatingPromptSeasonQuietAfterShown() {
        let tracker = RatingPromptTracker(defaults: isolatedDefaults(), calendar: utcCalendar)

        tracker.recordRecommendationViewed(on: day(2026, 10, 1))
        tracker.recordRecommendationViewed(on: day(2026, 10, 2))
        tracker.recordRecommendationViewed(on: day(2026, 10, 3))
        XCTAssertTrue(tracker.shouldPrompt(now: day(2026, 10, 3)))

        tracker.recordPromptShown(at: day(2026, 10, 3))

        // Fresh proven value inside the season still does not re-ask.
        tracker.recordRecommendationViewed(on: day(2026, 11, 1))
        tracker.recordRecommendationViewed(on: day(2026, 11, 2))
        tracker.recordRecommendationViewed(on: day(2026, 11, 3))
        XCTAssertFalse(tracker.shouldPrompt(now: day(2026, 11, 3)))

        // After the season, the ask may return.
        XCTAssertTrue(tracker.shouldPrompt(now: day(2027, 1, 15)))
    }

    // MARK: - R-15 feedback composer

    func testFeedbackComposerRejectsEmptyMessage() {
        XCTAssertNil(FeedbackComposer.mailtoURL(category: .bugReport, message: "", replyEmail: "", appVersion: "1.0 (7)"))
        XCTAssertNil(FeedbackComposer.mailtoURL(category: .bugReport, message: "  \n ", replyEmail: "", appVersion: "1.0 (7)"))
    }

    func testFeedbackComposerBuildsAddressedEmail() throws {
        let url = try XCTUnwrap(FeedbackComposer.mailtoURL(
            category: .bugReport,
            message: "The UV pill shows the wrong value.",
            replyEmail: "runner@example.com",
            appVersion: "1.0 (7)"
        ))
        XCTAssertEqual(url.scheme, "mailto")

        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.path, FeedbackComposer.supportEmail)
        let query = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(query["subject"], "RunWear Feedback — Bug Report")
        let body = try XCTUnwrap(query["body"])
        XCTAssertTrue(body.contains("The UV pill shows the wrong value."))
        XCTAssertTrue(body.contains("RunWear 1.0 (7)"))
        XCTAssertTrue(body.contains("Reply to: runner@example.com"))
    }

    func testFeedbackComposerOmitsEmptyReplyAddress() throws {
        let url = try XCTUnwrap(FeedbackComposer.mailtoURL(
            category: .other,
            message: "Love the app.",
            replyEmail: " ",
            appVersion: "1.0 (7)"
        ))
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        let body = try XCTUnwrap(components.queryItems?.first { $0.name == "body" }?.value)
        XCTAssertFalse(body.contains("Reply to:"))
    }
}
