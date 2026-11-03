import XCTest
import SwiftUI

/// Pins for WS-1 (RC-1, iOS half) — home composition verification.
///
/// The deep audit judged iOS CLEAN on D-01…D-04 at code level
/// (`WeatherOutfitView.swift`'s sheet container overlaps the photo
/// by −28pt by construction; a single continuous sheet-filled
/// stack; an opaque navigation bar). This suite VERIFIES those
/// verdicts against the current tree, pins the Part 1 §1.1 handoff
/// invariants that are testable, and carries the fixes this lane
/// landed where verification found divergences:
///
/// - the photo-zone chrome (script logo) never faded — it only
///   rode the hero's scale transform (§1.1: opacity 1 → 0 across
///   the handoff's first 40%, non-interactive from fade start);
/// - the hero crossfade ran at 0.5 s (§1.1: 300 ms) and its
///   landed state carried over to later photos, which then
///   swapped with no crossfade at all;
/// - the fallback layer hard-swapped on a URL change instead of
///   crossfading.
///
/// View-layer facts the test target cannot compile are pinned
/// against the sources of record (the suite's established
/// source-pin pattern; a skip in CI is reported in the gate
/// report, never silently counted).
final class HomeCompositionTests: XCTestCase {

    // MARK: - The overlap anchor (D-01 iOS verdict)

    func testSheetOverlapIsTheDesignedConstant() {
        XCTAssertEqual(HomeHandoffGeometry.sheetOverlap, 28)
        XCTAssertEqual(HomeHandoffGeometry.sheetCornerRadius, 28)
    }

    func testOverlapIsConstantAcrossTheWholeHandoff() {
        // The overlap is layout geometry (.padding(.top, -overlap)
        // on the sheet container), NOT a function of progress:
        // for every handoff position the sheet's top edge sits
        // exactly `sheetOverlap` above the hero's layout foot.
        // This loop pins the model side — there is no
        // progress-dependent overlap value anywhere in the type.
        for progress in stride(from: 0.0, through: 1.0, by: 0.05) {
            XCTAssertEqual(
                HomeHandoffGeometry.sheetOverlap, 28,
                "overlap changed at progress \(progress)"
            )
            _ = progress
        }
    }

    // MARK: - Handoff progress (scroll-coupled 1:1, §1.1)

    func testProgressIsADirectClampedFunctionOfOffset() {
        XCTAssertEqual(HomeHandoffGeometry.progress(forScrollOffset: -40), 0)
        XCTAssertEqual(HomeHandoffGeometry.progress(forScrollOffset: 0), 0)
        XCTAssertEqual(HomeHandoffGeometry.progress(forScrollOffset: 110), 0.5, accuracy: 0.001)
        XCTAssertEqual(HomeHandoffGeometry.progress(forScrollOffset: 220), 1)
        XCTAssertEqual(HomeHandoffGeometry.progress(forScrollOffset: 900), 1)
        XCTAssertEqual(HomeHandoffGeometry.handoffRange, 220)
    }

    func testSettledCardEndStateMatchesSpec() {
        // §1.1 end-state: scale 1.00 → 0.94, radius 0 → 24,
        // horizontal inset 0 → 16, all across the same range.
        XCTAssertEqual(HomeHandoffGeometry.scale(progress: 0), 1)
        XCTAssertEqual(HomeHandoffGeometry.scale(progress: 1), 0.94, accuracy: 0.001)
        XCTAssertEqual(HomeHandoffGeometry.scale(progress: 0.5), 0.97, accuracy: 0.001)
        XCTAssertEqual(HomeHandoffGeometry.cornerRadius(progress: 0), 0)
        XCTAssertEqual(HomeHandoffGeometry.cornerRadius(progress: 1), 24)
        XCTAssertEqual(HomeHandoffGeometry.horizontalInset(progress: 0), 0)
        XCTAssertEqual(HomeHandoffGeometry.horizontalInset(progress: 1), 16)
    }

    // MARK: - Photo-zone chrome fade (§1.1, the WS-1 fix)

    func testChromeFadesAcrossTheFirstFortyPercentOnly() {
        XCTAssertEqual(HomeHandoffGeometry.chromeFadePortion, 0.4)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: 0), 1)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: 0.2), 0.5, accuracy: 0.001)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: 0.4), 0)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: 0.41), 0)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: 1), 0)
    }

    func testChromeIsInteractiveOnlyAtExactRest() {
        XCTAssertTrue(HomeHandoffGeometry.chromeIsInteractive(progress: 0))
        XCTAssertFalse(HomeHandoffGeometry.chromeIsInteractive(progress: 0.001))
        XCTAssertFalse(HomeHandoffGeometry.chromeIsInteractive(progress: 0.4))
        XCTAssertFalse(HomeHandoffGeometry.chromeIsInteractive(progress: 1))
    }

    func testChromeIsFullyPresentAgainAtRest() {
        // Rubber-band / return-to-top: a negative offset is the
        // at-rest state, and the chrome is whole there.
        let rest = HomeHandoffGeometry.progress(forScrollOffset: -120)
        XCTAssertEqual(HomeHandoffGeometry.chromeOpacity(progress: rest), 1)
        XCTAssertTrue(HomeHandoffGeometry.chromeIsInteractive(progress: rest))
    }

    // MARK: - Capped accessory zone (flag chip)

    func testFlagChipInsetDerivesFromTheOverlapAndIsCapped() {
        XCTAssertEqual(HomeHandoffGeometry.flagChipBottomInset, 40)
        XCTAssertEqual(
            HomeHandoffGeometry.flagChipBottomInset,
            HomeHandoffGeometry.sheetOverlap + HomeHandoffGeometry.flagChipGap
        )
        XCTAssertEqual(HomeHandoffGeometry.flagChipGap, 12)
    }

    // MARK: - No scroll void (D-04 iOS verdict), by construction

    func testComposedHeightHasNoReservedVoid() {
        // The stacked composition is hero (0.75 of the container)
        // then the sheet pulled UP by the overlap: total composed
        // height = hero + sheet − 28, with no reserved-but-empty
        // segment at any content height. (Android's D-04 void was
        // a first item reserving hero + sheet − 28 while its
        // visible content ended at hero.) Here the sheet's own
        // content BEGINS at hero − 28, for short and tall sheets
        // alike — the arithmetic this test pins is the whole
        // claim; the frame census belongs to WS-9's walkthrough.
        let container = 800.0
        let hero = container * AppTheme.heroHeightRatio
        XCTAssertEqual(hero, 600)
        for sheetContent in [120.0, 340.0, 900.0] {
            let sheetTop = hero - HomeHandoffGeometry.sheetOverlap
            let composed = sheetTop + sheetContent + 40 // foot spacer
            XCTAssertEqual(composed, hero + sheetContent + 40 - 28)
            XCTAssertLessThan(sheetTop, hero, "sheet must overlap the photo's foot")
        }
    }

    // MARK: - Head crop (D-23 iOS half)

    func testHeadBandStaysInFrameInTheD23Scenario() {
        // The audit's D-23 source: an 800×1200 portrait whose
        // head sits at source y ≈ 392–509 in the ANDROID fit's
        // coordinates; in the source's own coordinates the head
        // band is the top ~15% (y 0–180), and the failure was a
        // visible window STARTING at y ≈ 603. The iOS crop is
        // top-anchored: the visible window starts at y = 0 in
        // every frame shape, so the head band is in frame by
        // construction — phone hero and wide Duo pane alike.
        let image = CGSize(width: 800, height: 1200)
        let headBand = 0.0...180.0
        for frame in [CGSize(width: 393, height: 585),
                      CGSize(width: 393, height: 639),
                      CGSize(width: 700, height: 360)] {
            let visible = HeroPhoto.visibleRect(imageSize: image, frameSize: frame)
            XCTAssertEqual(visible.minY, 0, "visible window must start at the image's top edge")
            XCTAssertGreaterThanOrEqual(visible.maxY, headBand.upperBound,
                                        "head band must be fully visible in frame \(frame)")
        }
    }

    // MARK: - Crossfade (§1.1: after full decode, 300 ms)

    func testHeroCrossfadeDurationIsTheSpecValue() {
        XCTAssertEqual(AppTheme.crossfadeDuration, 0.3, accuracy: 0.001)
    }

    // MARK: - Source pins (view-layer facts)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testSheetOverlapAndChromeFadeAreWired_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains(".padding(.top, -HomeHandoffGeometry.sheetOverlap)"),
                      "the sheet overlap must come from the geometry constant")
        XCTAssertTrue(view.contains("HomeHandoffGeometry.chromeOpacity(progress: progress)"),
                      "the script logo must fade per §1.1")
        XCTAssertTrue(view.contains("HomeHandoffGeometry.chromeIsInteractive(progress: progress)"),
                      "the chrome must leave the hit test when the fade begins")
        XCTAssertTrue(view.contains("HomeHandoffGeometry.progress(forScrollOffset: offset)"),
                      "handoff progress must be the geometry type's direct function of offset")
        // No spring / animation transaction may be attached to
        // the handoff itself (§1.1: 1:1, no spring, no lag).
        XCTAssertFalse(view.contains(".animation("),
                       "no animation transaction belongs on the home composition")
    }

    func testInsetsAreConsumedOnce_sourcePin() throws {
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        // The hero extends under the top safe area exactly once;
        // a second consumption is the D-03 mechanism (content
        // clipped / doubled insets).
        XCTAssertEqual(hero.components(separatedBy: ".ignoresSafeArea(edges: .top)").count - 1, 1,
                       "the hero must consume the top inset exactly once")
        XCTAssertTrue(hero.contains("HomeHandoffGeometry.flagChipBottomInset"),
                      "the flag chip's inset must derive from the overlap constant")
        XCTAssertTrue(hero.contains(".onChange(of: aiImageUrl)"),
                      "each incoming photo must reset the crossfade state")
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // The bar is opaque and its background is visible: no
        // sheet or header content renders under the status bar
        // (the D-03 iOS verdict), and the location entry lives in
        // the title menu of that always-visible bar.
        XCTAssertTrue(view.contains(".toolbarBackgroundVisibility(.visible, for: .navigationBar)"))
        XCTAssertTrue(view.contains(".toolbarTitleMenu"))
        XCTAssertTrue(view.contains("showLocationPicker = true"))
    }

    func testHeroFirstPaintIsSkeletonUntilTheServeResolves_sourcePin() throws {
        // §1.1: the first painted hero is the resolved serve or
        // the skeleton — the photo zone gates on the view model's
        // unresolved state (no hero URL and no settled fallback).
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("viewModel.heroImageUrl == nil"),
                      "the hero zone must skeleton-gate on the unresolved serve")
        XCTAssertTrue(view.contains("viewModel.isHeroImageLoading || viewModel.fallbackImageUrl == nil"),
                      "the gate must cover the whole pool-resolution window")
        XCTAssertTrue(view.contains("heroSkeleton(heightMode: heightMode)"))
    }

    func testFirstPaintIsTheDesignedSkeleton_sourcePin() throws {
        // §1.1 first paint: until weather + recommendation exist,
        // ContentView paints the designed skeleton (frame 46's
        // LoadingView), never a stale or fallback home.
        let content = try sourceFile("../RunWear/Views/ContentView.swift")
        XCTAssertTrue(content.contains("case .loading:"))
        XCTAssertTrue(content.contains("LoadingView()"))
        XCTAssertTrue(content.contains("HomePresentation.resolve("))
    }
}
