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

/// RW-16.30 pricing experiment readiness (Nov 2, 2026) — the iOS half
/// of the cross-platform paywall-price rule (Android:
/// MakeItMePriceLabelTest). Every price in the paywall path renders
/// from the fetched current RevenueCat offering: unresolved rows
/// fall back to price-free plan names.
///
/// GEN-3 (WS-F) supersedes the gen-2 savings-suffix rule: the
/// annual row carries NO "less than paying monthly" comparison at
/// any price. In its place the paywall's fine print frames the
/// annual price per run, with the basis stated in the line and the
/// cents figure DERIVED from the displayed product's annual price
/// — about 4¢ at the $9.99 control, and the honest derived figure
/// at the $19.99/$29.99 arms, never a hardcoded cents value.
final class MakeItMePriceLabelTests: XCTestCase {

    func testUnresolvedRowsFallBackToPriceFreePlanNames() {
        XCTAssertEqual("Monthly", MakeItMePriceLabels.monthlyRowLabel(priceString: nil))
        XCTAssertEqual(
            "Annual",
            MakeItMePriceLabels.annualRowLabel(annualPriceString: nil, annual: nil, monthly: nil)
        )
    }

    func testMonthlyLabelRendersTheOfferingPrice() {
        XCTAssertEqual("$19.99 / month", MakeItMePriceLabels.monthlyRowLabel(priceString: "$19.99"))
        XCTAssertEqual("$0.99 / month", MakeItMePriceLabels.monthlyRowLabel(priceString: "$0.99"))
    }

    // MARK: - WS-F · the monthly comparison is gone at every price

    func testAnnualRowCarriesNoMonthlyComparisonAtAnyArm() {
        for (priceString, amount) in [("$9.99", "9.99"), ("$19.99", "19.99"), ("$29.99", "29.99")] {
            let label = MakeItMePriceLabels.annualRowLabel(
                annualPriceString: priceString,
                annual: Decimal(string: amount),
                monthly: Decimal(string: "0.99")
            )
            XCTAssertEqual("\(priceString) / year", label)
            XCTAssertFalse(label.contains("less than paying monthly"),
                           "WS-F: the monthly comparison is removed, not reworded")
            XCTAssertFalse(label.contains("%"),
                           "WS-F: no percentage claim survives on the annual row")
        }
        // Even with the monthly price unknown, the row is the same
        // plain price + term.
        XCTAssertEqual(
            "$9.99 / year",
            MakeItMePriceLabels.annualRowLabel(annualPriceString: "$9.99",
                                               annual: Decimal(string: "9.99"),
                                               monthly: nil)
        )
    }

    // MARK: - WS-F · the per-run fine print derives from the price

    func testPerRunCentsDeriveFromTheAnnualPriceAtTheStatedBasis() {
        // Basis: four to five runs a week → 4.5 × 52 = 234 runs/yr.
        // $9.99 → 999/234 = 4.27¢ → about 4¢ (the control framing).
        XCTAssertEqual(4, MakeItMePriceLabels.perRunCents(annual: Decimal(string: "9.99")))
        // $19.99 → 1999/234 = 8.54¢ → about 9¢.
        XCTAssertEqual(9, MakeItMePriceLabels.perRunCents(annual: Decimal(string: "19.99")))
        // $29.99 → 2999/234 = 12.82¢ → about 13¢.
        XCTAssertEqual(13, MakeItMePriceLabels.perRunCents(annual: Decimal(string: "29.99")))
        XCTAssertNil(MakeItMePriceLabels.perRunCents(annual: nil))
    }

    func testPerRunFinePrintStatesItsBasisAtAllThreeArms() {
        XCTAssertEqual(
            "At four to five runs a week, $9.99/year is only about 4¢ per run.",
            MakeItMePriceLabels.perRunFinePrint(annualPriceString: "$9.99",
                                                annual: Decimal(string: "9.99"))
        )
        XCTAssertEqual(
            "At four to five runs a week, $19.99/year is only about 9¢ per run.",
            MakeItMePriceLabels.perRunFinePrint(annualPriceString: "$19.99",
                                                annual: Decimal(string: "19.99"))
        )
        XCTAssertEqual(
            "At four to five runs a week, $29.99/year is only about 13¢ per run.",
            MakeItMePriceLabels.perRunFinePrint(annualPriceString: "$29.99",
                                                annual: Decimal(string: "29.99"))
        )
    }

    func testPerRunFinePrintWaitsForTheOffering() {
        XCTAssertNil(MakeItMePriceLabels.perRunFinePrint(annualPriceString: nil, annual: nil))
        XCTAssertNil(MakeItMePriceLabels.perRunFinePrint(annualPriceString: "$9.99", annual: nil))
        XCTAssertNil(MakeItMePriceLabels.perRunFinePrint(annualPriceString: nil,
                                                         annual: Decimal(string: "9.99")))
    }

    // MARK: - WS-D · the Settings offer card's price line

    func testSettingsOfferPriceLineIsTheProductPriceWithTerm() {
        XCTAssertEqual("$9.99 / year",
                       MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: "$9.99"))
        XCTAssertEqual("$19.99 / year",
                       MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: "$19.99"))
        XCTAssertNil(MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: nil),
                     "unresolved offering → the card carries no price, never a hardcoded one")
    }
}

/// RW-16.30 (privacy decision 5a) — the Delete my data alerts are
/// distinct presentation states from the photos-only delete: the
/// account-level confirmation, its confirmed end state, and its
/// failure must never alias the photos flow's alerts (the end-state
/// copy claims different things — account deleted, not just photos).
final class DeleteMyDataAlertTests: XCTestCase {

    func testDeleteDataAlertsHaveDistinctStableIds() {
        XCTAssertEqual(MakeItMeAlert.confirmDeleteData.id, "confirmDeleteData")
        XCTAssertEqual(MakeItMeAlert.dataDeletedConfirmation.id, "dataDeletedConfirmation")
        XCTAssertEqual(MakeItMeAlert.deleteDataFailed.id, "deleteDataFailed")
    }

    func testDeleteDataAlertsAreNotThePhotosAlerts() {
        XCTAssertNotEqual(MakeItMeAlert.confirmDeleteData, .confirmDelete)
        XCTAssertNotEqual(MakeItMeAlert.dataDeletedConfirmation, .deletedConfirmation)
        XCTAssertNotEqual(MakeItMeAlert.deleteDataFailed, .deleteFailed)
    }
}
