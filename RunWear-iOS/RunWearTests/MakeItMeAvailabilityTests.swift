import XCTest

/// "Make it me" OFF posture (Sol's ruling, 2026-11-02) — the App
/// Store resubmission build carries the feature switched off, "til
/// it actually works," matching the Android production candidate.
///
/// The gate is one constant (MakeItMeAvailability.isEnabled) and
/// every entry point consults it: the app never bootstraps the
/// personal store, ContentView never wires it into the view model,
/// Settings never renders the section, and onboarding never shows
/// the fork. These tests pin the shipped posture so the flip back
/// on is a deliberate, visible change — a new ruling and a new
/// build — never an accident of some other edit.
///
/// The store-level half of the posture: with no bootstrap and no
/// wiring, a PersonalHeroStore stays in its initial stock mode,
/// which is what every personal surface keys off (isOptedIn is
/// false, the view model's personal layer stays off, heroes
/// resolve pool-only).
@MainActor
final class MakeItMeAvailabilityTests: XCTestCase {

    func testShippedBuildHasMakeItMeOff() {
        XCTAssertFalse(
            MakeItMeAvailability.isEnabled,
            "This build line ships with \"Make it me\" OFF (Sol, 2026-11-02). "
                + "Re-enabling takes a new ruling and a new build — flip the "
                + "constant and this pin together, deliberately."
        )
    }

    func testPersonalStoreStartsInStockMode() {
        let store = PersonalHeroStore()
        XCTAssertEqual(store.mode, .stock)
        XCTAssertFalse(store.isOptedIn)
        XCTAssertEqual(store.freeGenerationsUsed, 0)
    }
}
