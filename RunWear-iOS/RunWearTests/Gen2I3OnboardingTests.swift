import XCTest
import SwiftUI

/// Pins for the GEN-2 lane I3 fixes (walkthrough 2026-11-04, iOS
/// census): W-17 (fork half), W-19, W-20, W-21, W-22, W-23.
///
///  * W-17: the fork's photo card renders through HeroPhoto (the
///    home hero's top-anchored cover crop) — not the bare
///    center-anchored .fill that cropped the runner at the neck.
///  * W-23: the fork's location caption exists only when the
///    place is known (ForkContextLine), and the photo area has a
///    designed placeholder while its image loads — no blank slab,
///    no raw "Loading…" caption.
///  * W-19: "Use My Location" requests permission and STAYS on
///    the styled location step (spinner + copy inside it); the
///    flow advances only when the authorization resolves, and
///    the first-look host's fallback branch is styled, not bare.
///  * W-20: the guided-capture chips lay out two per row
///    (SelfieCheck.captureChipRows) at full label width.
///  * W-21: the welcome footer is the decided line, verbatim.
///  * W-22: the Fit Preference step renders its three choices
///    inline (FitPreferenceInlinePicker), not a collapsed menu.
final class Gen2I3OnboardingTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func occurrences(_ haystack: String, _ needle: String) -> Int {
        haystack.components(separatedBy: needle).count - 1
    }

    // MARK: - W-17 · fork photo card is top-anchored (HeroPhoto)

    func testForkPhotoCardUsesHeroPhotoNotCenterAnchoredFill_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        let start = try XCTUnwrap(flow.range(of: "struct MakeItMeForkView"))
        let end = try XCTUnwrap(flow.range(of: "// MARK: - The opt-in flow container"))
        let fork = String(flow[start.lowerBound..<end.lowerBound])

        XCTAssertTrue(fork.contains("HeroPhoto(image: image, width: geometry.size.width, height: 180)"),
                      "the fork card must render through the top-anchored HeroPhoto (W-17)")
        XCTAssertFalse(fork.contains("image.resizable().aspectRatio(contentMode: .fill)"),
                       "the bare center-anchored fill that cropped the head is gone from the fork card")
        // The crop contract the fork now inherits: the visible
        // rect always starts at the image's top edge (the head).
        let wide = HeroPhoto.visibleRect(
            imageSize: CGSize(width: 800, height: 1200),
            frameSize: CGSize(width: 345, height: 180)
        )
        XCTAssertEqual(wide.minY, 0)
        XCTAssertGreaterThan(wide.height, 180,
                               "the head band (top 15% of the portrait) is inside the visible rect")
    }

    // MARK: - W-23 · no loading caption, designed placeholder

    func testForkContextLineExistsOnlyWhenThePlaceIsKnown() {
        XCTAssertNil(ForkContextLine.make(
            placeDisplayName: "Loading...", isPlaceKnown: false, feelsLike: "45°F"),
            "no known place → no caption at all (never the placeholder string)")
        XCTAssertNil(ForkContextLine.make(
            placeDisplayName: "Kansas City, MO", isPlaceKnown: false, feelsLike: "45°F"))
        XCTAssertEqual(ForkContextLine.make(
            placeDisplayName: "Kansas City, MO", isPlaceKnown: true, feelsLike: "45°F"),
            "Kansas City, MO · 45°F")
    }

    func testForkCaptionIsForkContextLineAndPhotoHasPlaceholder_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("previewContextLine: forkContextLine"),
                      "the fork receives the place-gated caption, not the raw context line")
        XCTAssertTrue(onboarding.contains("isPlaceKnown: previewVM.simplePlace != nil"),
                      "known means a SimplePlace has actually been derived")

        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("forkPhotoPlaceholder"),
                      "the fork photo area has a designed loading placeholder (W-23)")
        XCTAssertTrue(flow.contains("if let previewContextLine"),
                      "the caption slot renders only when a caption exists")
        XCTAssertFalse(flow.contains("Loading… ·"),
                       "no raw loading caption may be composed in the fork file")
    }

    // MARK: - W-19 · location step stays intact through the ask

    func testUseMyLocationDoesNotAdvanceInTheSameTap_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("private func useMyLocationTapped()"),
                      "the tap is a named step, not an inline advance")
        let tapStart = try XCTUnwrap(onboarding.range(of: "private func useMyLocationTapped()"))
        let tap = String(onboarding[tapStart.lowerBound...].prefix(1200))
        XCTAssertTrue(tap.contains("locationPermissionPending = true"),
                      "an undecided permission marks the step pending and stays")
        XCTAssertTrue(tap.contains("locationService.requestPermission()"))
        XCTAssertTrue(tap.contains("case .authorizedWhenInUse, .authorizedAlways:"),
                      "an already-granted permission advances at once")

        // The old defect, pinned out: no button action in the
        // location step may pair the request with an immediate
        // advance. (The handler itself is the only place the two
        // calls may coexist, separated by the status switch.)
        XCTAssertFalse(onboarding.contains("locationService.requestPermission()\n                    beginFirstLook()"),
                       "request + immediate advance in one action is the W-19 defect")
    }

    func testLocationStepKeepsItsLayoutWithAnInStepLoadingRow_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        let start = try XCTUnwrap(onboarding.range(of: "private var locationScreen"))
        let end = try XCTUnwrap(onboarding.range(of: "private func useMyLocationTapped()"))
        let step = String(onboarding[start.lowerBound..<end.lowerBound])

        XCTAssertTrue(step.contains("if locationPermissionPending"),
                      "the pending state renders inside the step")
        XCTAssertTrue(step.contains("ProgressView()"),
                      "the pending state carries a spinner")
        XCTAssertTrue(step.contains("\"Finding your location…\""),
                      "the pending state carries copy")
        XCTAssertTrue(step.contains(".disabled(locationPermissionPending)"),
                      "the doors stay rendered (disabled) while the ask resolves — chrome intact")
        XCTAssertTrue(step.contains(".onChange(of: locationService.authorizationStatus)"),
                      "the flow advances on the authorization change, over the intact step")
        XCTAssertTrue(step.contains("locationPermissionDenied = true"),
                      "a denial resolves the pending state on the step, with the city door working")
    }

    func testFirstLookFallbackBranchIsStyledNotBare_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        // The notDetermined fallback in the first-look host: its
        // doors are styled buttons now — the bare plain-text
        // rendering is the W-19 stripped state.
        XCTAssertFalse(onboarding.contains("Button(\"Use My Location\") {"),
                       "the bare text-button form of the fallback door is gone")
        XCTAssertTrue(onboarding.contains("Text(\"Finding your location…\")"),
                      "the fallback carries the same spinner copy treatment")
    }

    func testLocationServiceSeedsAuthorizationFromTheManager_sourcePin() throws {
        let service = try sourceFile("../RunWear/Services/LocationService.swift")
        XCTAssertTrue(service.contains("authorizationStatus = locationManager.authorizationStatus"),
                      "an already-granted permission must not read as notDetermined at launch (W-19)")
    }

    // MARK: - W-20 · capture chips: two rows, full labels

    func testCaptureChipRowsAreTwoPerRowCoveringEveryCheck() {
        let rows = SelfieCheck.captureChipRows
        XCTAssertEqual(rows.count, 2)
        XCTAssertTrue(rows.allSatisfy { $0.count == 2 },
                      "two chips per row — no chip is squeezed into a quarter width")
        XCTAssertEqual(rows.flatMap { $0 }, SelfieCheck.allCases,
                       "every check appears exactly once, in coaching order")
    }

    func testCaptureChipLabelsAreCompleteSentences() {
        // The four labels as the user must be able to read them —
        // the truncated forms on camera were these strings cut
        // mid-word, so pin them whole.
        XCTAssertEqual(SelfieCheck.faceFound.passedLabel, "In frame")
        XCTAssertEqual(SelfieCheck.faceLargeEnough.passedLabel, "Face size")
        XCTAssertEqual(SelfieCheck.faceFrontal.passedLabel, "Straight on")
        XCTAssertEqual(SelfieCheck.wellLit.passedLabel, "Light")
    }

    func testCaptureViewRendersChipsByRowsAtFullWidth_sourcePin() throws {
        let capture = try sourceFile("../RunWear/Views/GuidedSelfieCaptureView.swift")
        XCTAssertTrue(capture.contains("SelfieCheck.captureChipRows"),
                      "the chips render from the two-row layout (W-20)")
        XCTAssertTrue(capture.contains(".fixedSize(horizontal: true, vertical: false)"),
                      "each chip sizes to its full label instead of truncating it")
        XCTAssertFalse(capture.contains("ForEach(SelfieCheck.allCases, id: \\.rawValue) { check in\n                        chip(for: check)"),
                       "the single-row-of-four layout is retired")
    }

    // MARK: - W-21 · welcome footer, verbatim

    func testWelcomeFooterIsTheDecidedLineVerbatim() {
        XCTAssertEqual(OnboardingCopy.welcomeFooter, "Free forever · Premium is optional")
    }

    func testWelcomeScreenRendersTheFooterConstant_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("Text(OnboardingCopy.welcomeFooter)"))
        XCTAssertFalse(onboarding.contains("\"Free to start · Set up in under a minute\""),
                       "the struck line survives nowhere in the onboarding view")
    }

    // MARK: - W-22 · fit choices shown directly on the step

    func testInlineFitOptionsAreTheThreeChoicesInOrder() {
        XCTAssertEqual(GenderPreference.fitOptions.map { $0.value },
                       [.male, .female, .unisex])
        XCTAssertEqual(GenderPreference.fitOptions.map { $0.label },
                       ["Men's", "Women's", "No preference"])
    }

    func testFitStepUsesTheInlinePicker_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertEqual(occurrences(onboarding, "FitPreferenceInlinePicker(preference:"), 1,
                       "the fit step renders the inline selection, once, on its own screen")
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertTrue(controls.contains("struct FitPreferenceInlinePicker"),
                      "the inline picker is a shared control beside the menu picker")
        XCTAssertTrue(controls.contains("Image(systemName: \"checkmark\")"),
                      "the current choice is checkmarked in place — no dropdown to open")
    }
}
