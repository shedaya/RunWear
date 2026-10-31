import XCTest

/// RW-16.7 / RW-16.9 §4 — the presentation-ownership contract.
///
/// The build-(12) defect: the opt-in commit driver was a
/// @StateObject of the flow view, and the flow's sheet bindings
/// lived on a view whose content branch store.mode could swap
/// mid-presentation. Sheet churn recreated the flow view, which
/// recreated the driver — picked photos lost, in-flight commit
/// cancelled into a dead driver, and its failure had nowhere to
/// surface. Sol's server record shows the fingerprint: four
/// record_consent calls and three upload rounds in 70 seconds,
/// and attempts that abandoned back to the empty Add state with
/// no success and no error.
///
/// The fix moves ownership to MakeItMePresentationHost, which
/// outlives any flow view. These tests pin what that ownership
/// must guarantee:
///   * a commit runs to its finish exactly once even when the
///     sheet is dismissed mid-flight;
///   * a failure with the flow on screen stays on the driver
///     (the flow renders it inline — no double surface);
///   * a failure landing after the sheet went away surfaces as
///     a host alert — the failure is never swallowed;
///   * a fresh flow presentation never opens wearing a previous
///     attempt's error line.
@MainActor
final class MakeItMePresentationTests: XCTestCase {

    private struct StubError: Error {}

    private func awaitIdle(_ driver: OptInCommitDriver, deadlineSeconds: Double = 3) async {
        let deadline = Date().addingTimeInterval(deadlineSeconds)
        while Date() < deadline {
            if !driver.isCommitting { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
    }

    func testCommitSurvivesSheetDismissalMidFlight() async {
        let host = MakeItMePresentationHost()
        host.present(.optIn)
        let commitStarted = expectation(description: "commit in flight")
        var finishCount = 0
        host.commitDriver.commit(
            photos: [],
            onCommit: { _ in
                commitStarted.fulfill()
                try await Task.sleep(nanoseconds: 300_000_000)
            },
            onFinish: { finishCount += 1 }
        )
        await fulfillment(of: [commitStarted], timeout: 2)

        // The RW-16.9 churn: the sheet goes away mid-commit.
        host.dismissSheet()

        await awaitIdle(host.commitDriver)
        XCTAssertEqual(finishCount, 1,
                       "a commit in flight must still deliver its finish exactly once")
        XCTAssertNil(host.commitDriver.failure)
        XCTAssertNil(host.alert,
                     "a successful commit needs no alert — the store state is its surface")
    }

    func testCommitFailureWhileFlowPresentedStaysOnTheDriver() async {
        let host = MakeItMePresentationHost()
        host.present(.optIn)
        host.commitDriver.commit(
            photos: [],
            onCommit: { _ in throw StubError() },
            onFinish: {}
        )
        await awaitIdle(host.commitDriver)
        XCTAssertNotNil(host.commitDriver.failure,
                        "the flow renders the failure from the driver while it is on screen")
        XCTAssertNil(host.alert,
                     "while the flow is presented the failure must not ALSO surface as an alert")
        XCTAssertEqual(host.sheet, .optIn, "the flow stays open to show its error")
    }

    func testCommitFailureAfterSheetDismissalSurfacesHostAlert() async {
        let host = MakeItMePresentationHost()
        host.present(.optIn)
        let commitStarted = expectation(description: "commit in flight")
        host.commitDriver.commit(
            photos: [],
            onCommit: { _ in
                commitStarted.fulfill()
                try await Task.sleep(nanoseconds: 200_000_000)
                throw StubError()
            },
            onFinish: {}
        )
        await fulfillment(of: [commitStarted], timeout: 2)

        host.dismissSheet()

        await awaitIdle(host.commitDriver)
        XCTAssertNotNil(host.commitDriver.failure)
        guard case .commitFailed = host.alert else {
            return XCTFail(
                "a failure landing after dismissal must surface as a host alert, got \(String(describing: host.alert))"
            )
        }
    }

    func testPresentingAFlowClearsThePreviousAttemptsFailure() async {
        let host = MakeItMePresentationHost()
        host.commitDriver.commit(
            photos: [],
            onCommit: { _ in throw StubError() },
            onFinish: {}
        )
        await awaitIdle(host.commitDriver)
        XCTAssertNotNil(host.commitDriver.failure)
        host.alert = nil // the user read and dismissed the alert

        host.present(.optIn)
        XCTAssertNil(host.commitDriver.failure,
                     "a fresh flow must not open wearing the previous attempt's error line")
    }
}
