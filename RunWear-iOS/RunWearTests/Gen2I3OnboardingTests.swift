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

        // GEN-5 (RW-28 item 19, ruling C-2) SUPERSEDES the W-17
        // HeroPhoto seat: the fork's photo is now the bundled
        // purpose-designed image, rendered through the fork band
        // with the asset lane's anchoring rule (ForkHeroSpec) and
        // the code-drawn dashed face box — a stronger form of the
        // same never-crop-the-head contract.
        XCTAssertTrue(fork.contains("forkHeroBand(width: contentWidth, height: bandHeight)"),
                      "the fork card renders the bundled fork image through its spec-anchored band (C-2)")
        XCTAssertTrue(fork.contains("ForkHeroSpec.bandHeight(forWidth: contentWidth)"),
                      "the band's height scales with its width (RW-29 FA-40) — a fixed 180 pt band slices the chin on wide form factors")
        XCTAssertTrue(fork.contains("ForkHeroSpec"),
                      "the band's crop comes from the asset lane's spec of record")
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
        XCTAssertTrue(step.contains("AppStrings.onbLocationFinding"),
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
        XCTAssertTrue(onboarding.contains("Text(AppStrings.onbLocationFinding)"),
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
        // GEN-5 (RW-28 item 27, census A-1) RESTORES the settled
        // footer: the W-21 line ("Free forever · Premium is
        // optional") was itself the deviation the Nov 5
        // walkthrough flagged — the settled line is the one the
        // design carried all along.
        XCTAssertEqual(OnboardingCopy.welcomeFooter, "Free to start · Set up in under a minute")
    }

    func testWelcomeScreenRendersTheFooterConstant_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("Text(OnboardingCopy.welcomeFooter)"))
        XCTAssertFalse(onboarding.contains("\"Free forever · Premium is optional\""),
                       "the W-21 deviation line survives nowhere in the onboarding view (GEN-5, census A-1)")
    }

    // MARK: - W-22 · fit choices shown directly on the step

    /// RW-81 RE-PIN (owner ruling, Sol, Nov 6, 2026 — supersedes
    /// the Nov 4 three-option pin this test used to carry): the
    /// fit choices are exactly TWO, Men's and Women's, and no
    /// fitOptions entry carries the neutral value — neutral is a
    /// state (a stored .unisex), never a button.
    func testInlineFitOptionsAreTheTwoChoicesInOrder_RW81() {
        XCTAssertEqual(GenderPreference.fitOptions.map { $0.value },
                       [.male, .female])
        XCTAssertEqual(GenderPreference.fitOptions.map { $0.label },
                       ["Men's", "Women's"])
        XCTAssertFalse(GenderPreference.fitOptions.contains { $0.value == .unisex },
                       "RW-81: no fit row carries the neutral value — nothing renders as a third choice")
    }

    /// RW-81: the tap-again toggle's full transition table —
    /// tapping an unselected choice selects it; tapping the
    /// selected choice again deselects to neutral (.unisex);
    /// from neutral, a tap selects.
    func testFitTapAgainTogglesToNeutral_RW81() {
        XCTAssertEqual(GenderPreference.male.fitSelection(afterTapping: .male), .unisex,
                       "tapping the selected Men's returns to neutral")
        XCTAssertEqual(GenderPreference.female.fitSelection(afterTapping: .female), .unisex,
                       "tapping the selected Women's returns to neutral")
        XCTAssertEqual(GenderPreference.male.fitSelection(afterTapping: .female), .female,
                       "tapping the other choice switches to it")
        XCTAssertEqual(GenderPreference.female.fitSelection(afterTapping: .male), .male,
                       "tapping the other choice switches to it")
        XCTAssertEqual(GenderPreference.unisex.fitSelection(afterTapping: .male), .male,
                       "from neutral, tapping Men's selects it")
        XCTAssertEqual(GenderPreference.unisex.fitSelection(afterTapping: .female), .female,
                       "from neutral, tapping Women's selects it")
        XCTAssertEqual(
            GenderPreference.male
                .fitSelection(afterTapping: .male)
                .fitSelection(afterTapping: .male),
            .male,
            "the toggle round-trips: select, un-tap to neutral, tap to select again")
    }

    /// RW-81: a stored .unisex (neutral — every pre-RW-81 "No
    /// preference" selection is stored as this same value)
    /// matches NO fit row, so every surface renders it as
    /// nothing selected; stored men/women values are untouched.
    func testStoredUnisexRendersAsNothingSelected_RW81() {
        for option in GenderPreference.fitOptions {
            XCTAssertNotEqual(option.value, GenderPreference.unisex,
                              "a stored neutral value must match no row's checkmark seat")
        }
        XCTAssertEqual(GenderPreference(rawValue: "unisex"), .unisex,
                       "the stored neutral value is the pre-existing .unisex — the data model does not change")
        XCTAssertEqual(GenderPreference(rawValue: "male"), .male)
        XCTAssertEqual(GenderPreference(rawValue: "female"), .female)
    }

    /// RW-81: neutral is a complete, proceedable state — the
    /// onboarding screen's fit state starts neutral (.unisex),
    /// and its Continue is never disabled and never gated on the
    /// fit value: advanceToNextStep reads only the step stack.
    func testOnboardingStartsNeutralAndContinueIsUngated_RW81_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("@State private var genderPreference: GenderPreference = .unisex"),
                      "RW-81: a fresh install starts neutral — neither fit row selected")
        let start = try XCTUnwrap(onboarding.range(of: "private var preferencesScreen")?.lowerBound)
        let end = try XCTUnwrap(onboarding.range(of: "private func preferenceGroup")?.lowerBound)
        let screen = String(onboarding[start..<end])
        XCTAssertTrue(screen.contains("advanceToNextStep()"),
                      "the combined screen's Continue advances the flow")
        XCTAssertFalse(screen.contains(".disabled"),
                       "RW-81: Continue is never disabled — neutral proceeds exactly like a selection")
        let advanceStart = try XCTUnwrap(onboarding.range(of: "private func advanceToNextStep()")?.lowerBound)
        let advanceEnd = try XCTUnwrap(onboarding.range(of: "path.append(next)", range: advanceStart..<onboarding.endIndex)?.upperBound)
        let advance = String(onboarding[advanceStart..<advanceEnd])
        XCTAssertFalse(advance.contains("genderPreference"),
                       "RW-81: advancing reads the step stack only — no fit-selection requirement")
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertTrue(controls.contains("preference = preference.fitSelection(afterTapping: option.value)"),
                      "RW-81: the shared control's rows toggle through the model's transition")
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
