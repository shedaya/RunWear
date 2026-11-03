import XCTest

/// "Make it me" posture pin. The store line carries the feature OFF
/// (Sol's ruling, 2026-11-02, "til it actually works"). This test
/// branch (test/rw-mim-flag-on-ios-2026-11-03) carries it ON for the
/// flag-on TestFlight build against the now-live pipeline (Sol,
/// 2026-11-03); the pin below asserts this branch's posture.
///
/// The gate is one constant (MakeItMeAvailability.isEnabled) and
/// every entry point consults it: the app never bootstraps the
/// personal store, ContentView never wires it into the view model,
/// Settings never renders the section, and onboarding never shows
/// the fork. This test pins the shipped posture so the flip back
/// on is a deliberate, visible change — a new ruling and a new
/// build — never an accident of some other edit.
///
/// (The store itself is not part of this target's compiled source
/// subset — the same reason PersonalHeroTests exercises the model
/// layer — so the pin lives on the gate every surface consults.)
final class MakeItMeAvailabilityTests: XCTestCase {

    func testFlagOnTestBuildHasMakeItMeOn() {
        XCTAssertTrue(
            MakeItMeAvailability.isEnabled,
            "This test branch carries \"Make it me\" ON (Sol's flag-on test "
                + "build, 2026-11-03) — the constant and this pin were flipped "
                + "together, deliberately, for the live-pipeline test build."
        )
    }
}
