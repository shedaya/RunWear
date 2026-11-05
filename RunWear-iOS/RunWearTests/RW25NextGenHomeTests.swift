import XCTest
import SwiftUI

/// Pins for RW-25 (next-gen home, iOS): the appearance resolution
/// mapping, the Android temperature set, the "Wear I've Run"
/// narration formatter, and the mini bar's background policy.
///
/// These types are compiled into the test target directly (the
/// target's dual-membership pattern), so no @testable import.
final class RW25NextGenHomeTests: XCTestCase {

    // MARK: - Appearance resolution

    func testAppearanceSystemResolvesToNoOverride() {
        XCTAssertNil(AppearancePreference.resolve(.system))
        XCTAssertNil(AppearancePreference.system.colorScheme)
    }

    func testAppearanceLightResolvesToLight() {
        XCTAssertEqual(AppearancePreference.resolve(.light), .light)
    }

    func testAppearanceDarkResolvesToDark() {
        XCTAssertEqual(AppearancePreference.resolve(.dark), .dark)
    }

    func testAppearanceRawValuesRoundTrip() {
        for preference in AppearancePreference.allCases {
            XCTAssertEqual(AppearancePreference(rawValue: preference.rawValue), preference)
        }
        XCTAssertEqual(AppearancePreference.allCases.count, 3)
    }

    // MARK: - Temperature hex pins (the Android set)

    func testTemperatureHexPinsMatchAndroidSet() {
        XCTAssertEqual(AppTheme.temperatureHex(for: .FREEZING), "8B5CF6")
        XCTAssertEqual(AppTheme.temperatureHex(for: .COLD), "6366F1")
        XCTAssertEqual(AppTheme.temperatureHex(for: .COOL), "3B82F6")
        XCTAssertEqual(AppTheme.temperatureHex(for: .MILD), "10B981")
        XCTAssertEqual(AppTheme.temperatureHex(for: .WARM), "F59E0B")
        XCTAssertEqual(AppTheme.temperatureHex(for: .HOT), "F97316")
    }

    func testTemperatureHexViaEngineBracketMapping() {
        // Bracket boundaries are the engine's (pinned by
        // EngineFixtureTests: 34.9 -> COLD, 80 -> HOT); the hex the
        // bracket resolves to must be the Android set.
        XCTAssertEqual(
            AppTheme.temperatureHex(for: HeroTempBracket.from(feelsLikeTemperature: 34.9)),
            "6366F1"
        )
        XCTAssertEqual(
            AppTheme.temperatureHex(for: HeroTempBracket.from(feelsLikeTemperature: 80)),
            "F97316"
        )
    }

    // MARK: - Mini bar narration formatter

    func testNarrationPreAddIsNil() {
        XCTAssertNil(WearIveRunNarration.line(for: .preAdd))
    }

    func testNarrationAddedUsesCommaForm() {
        XCTAssertEqual(
            WearIveRunNarration.line(for: .added(place: "New York", moment: "Now")),
            "Added New York, Now"
        )
    }

    func testNarrationAddedWithPlaceAndTime() {
        XCTAssertEqual(
            WearIveRunNarration.line(for: .added(place: "Central Park", moment: "7:00 AM")),
            "Added Central Park, 7:00 AM"
        )
    }

    func testNarrationUpdateToPayload() {
        XCTAssertEqual(
            WearIveRunNarration.line(for: .updated(payload: "Cool-weather kit")),
            "Update to Cool-weather kit"
        )
    }

    func testMiniBarStateDerivation() {
        // RW-26: the collection is built — the bar's state derives
        // from today's entry. No entry: preAdd, narration nil
        // (nothing renders). An entry: the "Added" narration.
        // (Phase 1 pinned a hard-coded preAdd; the pin now covers
        // the derivation instead.)
        XCTAssertEqual(WearIveRunMiniBarModel.wordmark, "Wear I've Run")
        let noEntry = WearIveRunMiniBarModel.state(
            todaysEntry: nil, moment: "Now",
            wasUpdatedThisSession: false
        )
        XCTAssertEqual(noEntry, .preAdd)
        XCTAssertNil(WearIveRunNarration.line(for: noEntry))
        let entry = WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_762_560_000),
            placeName: "New York", feelsLikeF: 41, actualF: 38,
            condition: "Cloudy", itemNames: ["Tee"], itemKeys: ["tee"],
            heroImageReference: "hero"
        )
        XCTAssertEqual(
            WearIveRunMiniBarModel.state(
                todaysEntry: entry, moment: "Now",
                wasUpdatedThisSession: false
            ),
            .added(place: "New York", moment: "Now")
        )
        // GEN-5 (RW-28, C-7) SUPERSEDES the WS-4 garment payload:
        // an entry carrying no stored update payload narrates
        // its PLACE — never its first kit item. (This pin
        // previously asserted the first kit item "Tee"; before
        // that, a caller-supplied "5 items · 41°F" summary —
        // both superseded in turn.)
        XCTAssertEqual(
            WearIveRunMiniBarModel.state(
                todaysEntry: entry, moment: "Now",
                wasUpdatedThisSession: true
            ),
            .updated(payload: "New York")
        )
    }

    // MARK: - Mini bar background policy (translucency amendment)

    func testMiniBackgroundIsTranslucentMaterial() {
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: true),
            .translucentMaterial(sheetOpacity: 0.82)
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: false),
            .translucentMaterial(sheetOpacity: 0.85)
        )
    }

    func testFullExpressionBackgroundIsOpaqueSheet() {
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .expanded, isDark: true),
            .opaqueSheet
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .expanded, isDark: false),
            .opaqueSheet
        )
        // WS-3: the cue is a state of the ONE machine now — the
        // retired `.endOfScrollCue` presentation case is `.cue`.
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .cue, isDark: true),
            .opaqueSheet
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .cue, isDark: false),
            .opaqueSheet
        )
    }
}
