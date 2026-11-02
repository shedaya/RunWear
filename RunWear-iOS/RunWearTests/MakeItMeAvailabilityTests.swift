import XCTest

/// "Make it me" OFF posture (Sol's ruling, 2026-11-02) — the App
/// Store resubmission build carries the feature switched off, "til
/// it actually works," matching the Android production candidate.
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

    func testShippedBuildHasMakeItMeOff() {
        XCTAssertFalse(
            MakeItMeAvailability.isEnabled,
            "This build line ships with \"Make it me\" OFF (Sol, 2026-11-02). "
                + "Re-enabling takes a new ruling and a new build — flip the "
                + "constant and this pin together, deliberately."
        )
    }
}
