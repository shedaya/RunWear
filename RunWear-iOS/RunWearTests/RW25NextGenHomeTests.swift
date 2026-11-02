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

    func testMiniBarShipsInPreAddState() {
        // The collection feature is not built in this build, so the
        // model's current state — the dock's only source — is preAdd
        // and the narration is nil (nothing renders).
        XCTAssertEqual(WearIveRunMiniBarModel.currentState, .preAdd)
        XCTAssertNil(WearIveRunNarration.line(for: WearIveRunMiniBarModel.currentState))
        XCTAssertEqual(WearIveRunMiniBarModel.wordmark, "Wear I've Run")
    }

    // MARK: - Mini bar background policy (translucency amendment)

    func testMiniBackgroundIsTranslucentMaterial() {
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: true),
            .translucentMaterial(sheetOpacity: 0.78)
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
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .endOfScrollCue, isDark: true),
            .opaqueSheet
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .endOfScrollCue, isDark: false),
            .opaqueSheet
        )
    }
}
