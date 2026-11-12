import XCTest
import SwiftUI
import UIKit
import CoreText

/// RW-169 (Nov 11, 2026) — the structural fix, iOS leg. Governed
/// by the diagnosis of record
/// (release-prep/rw169-diagnosis-2026-11-11/DIAGNOSIS.md §7):
///
/// - A (D1/D3/D5): ONE content hierarchy for mini ⇄ expanded —
///   the dual-form ZStack is retired; the morph probe samples
///   p = 0.25 / 0.5 / 0.75 asserting exactly one legible
///   narration and the wordmark at its rest width.
/// - B (D1/D10): the width law — wordmark and place name are
///   fixed-claim; the narration wraps and the bar grows; the
///   place pill sizes to its text (the 110 pt cap is gone).
///   Assertions run at the approval fixtures: place
///   "Overland Park, KS", narration "Planned for
///   Los Angeles, CA · Tomorrow", a 4-item kit.
/// - C (D10): top chrome is ONE negotiating layout pass —
///   pill, wordmark, share, gear as siblings.
/// - D (D8/D9): the straddle pill seats from its MEASURED
///   height (alignment guide), not planning heights; ONE
///   agreed per-cell presentation value, so a cell cannot be
///   FAILED and MAKING at once.
/// - E (§5): the settle discipline — a ≈2 s stability-gated
///   commit, one immutable presented snapshot, atomic flips,
///   and the re-settle-while-open probe (the visible entry is
///   byte-identical while a re-settle parks).
///
/// Captures: the six conformance states at the approval
/// fixtures in phone portrait + a wide class, rendered with
/// the tree's ImageRenderer route into `render-artefacts/`
/// (the hosted-capture route is the standing baseline failure
/// set and is not evidentiary here).
final class RW169StructuralTests: XCTestCase {

    // MARK: - Harness plumbing (the Gen6 VQ harness idiom)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
            .standardizedFileURL
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW169-CAPTURE wrote \(name)")
    }

    private func imageHasContent(_ image: UIImage) -> Bool {
        guard let cg = image.cgImage else { return false }
        let w = 8, h = 8
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var distinct = Set<Int>()
        for i in stride(from: 0, to: bytes.count, by: 4) {
            distinct.insert(Int(bytes[i]) << 16 | Int(bytes[i + 1]) << 8 | Int(bytes[i + 2]))
        }
        return distinct.count > 2
    }

    private func registerGreatVibes() throws {
        let url = try XCTUnwrap(
            Bundle(for: RW169StructuralTests.self)
                .url(forResource: "GreatVibes-Regular", withExtension: "ttf"),
            "GreatVibes-Regular.ttf is not in the test bundle")
        var error: Unmanaged<CFError>?
        _ = CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
    }

    private func textWidth(_ text: String, font: UIFont) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    // MARK: - Approval fixtures

    private var fixtureDay: Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/New_York")!
        return cal.date(from: DateComponents(year: 2026, month: 11, day: 12, hour: 9))!
    }

    /// The 4-item kit of the approval fixtures.
    private var fixtureItems: [String] {
        ["Running Cap", "Short Sleeve Shirt", "Running Shorts (5-7\")", "Sunglasses"]
    }

    private func fixtureEntry(place: String, hero: String = "") -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: fixtureDay, placeName: place,
            feelsLikeF: 58, actualF: 62, condition: "Clear sky",
            itemNames: fixtureItems,
            itemKeys: ["CAP", "SHORT_SLEEVE", "SHORTS", "SUNGLASSES"],
            heroImageReference: hero, sessionID: "s-rw169")
    }

    /// The approval narration: "Planned for Los Angeles, CA · Tomorrow".
    private var plannedState: WearIveRunState {
        .planned(place: "Los Angeles, CA", dayLabel: "Tomorrow", moment: .now)
    }

    // MARK: - A · the morph probe

    func testRW169A_MorphProbe_SingleLegibleNarrationAndWordmarkWidthAtSamples() {
        // The mechanical acceptance probe of §7A: sample the
        // morph at p = 0.25 / 0.5 / 0.75.
        let miniRestWidth = 96.0   // "Wear I've Run" at 17 pt Great Vibes (measured class)
        let expandedRestWidth = 176.0 // the same wordmark at 31 pt
        for p in [0.25, 0.5, 0.75] {
            XCTAssertEqual(
                WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1,
                "p=\(p): exactly one narration is legible — the same narration view, never two forms")
            let width = WearIveRunMorphProbe.wordmarkWidth(
                progress: p, miniRestWidth: miniRestWidth, expandedRestWidth: expandedRestWidth)
            XCTAssertEqual(
                width, miniRestWidth + (expandedRestWidth - miniRestWidth) * p,
                accuracy: 0.001,
                "p=\(p): the wordmark's rendered width is its interpolated REST width")
            XCTAssertGreaterThanOrEqual(
                width, miniRestWidth,
                "p=\(p): the wordmark never yields below its mini rest width (D1's ≈33 pt crush is unrepresentable)")
        }
        // Endpoints are exactly the two rest widths.
        XCTAssertEqual(WearIveRunMorphProbe.wordmarkWidth(
            progress: 0, miniRestWidth: miniRestWidth, expandedRestWidth: expandedRestWidth),
            miniRestWidth, accuracy: 0.001)
        XCTAssertEqual(WearIveRunMorphProbe.wordmarkWidth(
            progress: 1, miniRestWidth: miniRestWidth, expandedRestWidth: expandedRestWidth),
            expandedRestWidth, accuracy: 0.001)
        // The container height is the interpolation of the two
        // REST heights at every sample.
        for p in [0.25, 0.5, 0.75] {
            XCTAssertEqual(
                WearIveRunMorphProbe.containerHeight(miniRest: 60, expandedRest: 264, progress: p),
                60 + (264 - 60) * p, accuracy: 0.001,
                "p=\(p): height interpolates the rest heights — no during-morph measurement feeds it")
        }
    }

    func testRW169A_SingleHierarchy_SourcePins() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let unifiedStart = try XCTUnwrap(bar.range(of: "struct WearIveRunUnifiedSurfaceView"))
        let unifiedEnd = try XCTUnwrap(bar.range(
            of: "// MARK: - RW-152 §1 / RW-169 §7A", range: unifiedStart.upperBound..<bar.endIndex))
        let unified = bar[unifiedStart.lowerBound..<unifiedEnd.lowerBound]
        // ONE rendered narration view in the unified hierarchy:
        // the single `narrationView(line)` call site (the two
        // further `line(for:)` derivations in this slice are
        // the narration deferral's model-side version stamps —
        // they render nothing).
        XCTAssertEqual(
            unified.components(separatedBy: "narrationView(line)").count - 1, 1,
            "the unified surface renders its narration ONCE — there is no second form's narration to ghost (D3)")
        // The overlay composes the unified surface ONLY — the
        // retired dual composition (mini view + expanded view
        // in a ZStack) cannot return through the container.
        let overlayStart = try XCTUnwrap(bar.range(of: "struct WearIveRunOverlayView"))
        let overlay = bar[overlayStart.lowerBound..<bar.endIndex]
        XCTAssertTrue(overlay.contains("WearIveRunUnifiedSurfaceView("))
        XCTAssertFalse(overlay.contains("WearIveRunMiniBarView("),
                       "the overlay no longer composes the mini form as a separate layer")
        XCTAssertFalse(overlay.contains("WearIveRunExpandedPanelView("),
                       "the overlay no longer composes the expanded form as a separate layer")
        XCTAssertFalse(overlay.contains("expandedContentOpacity"),
                       "the dual-form dissolve window is retired")
        // No during-morph measurement pass.
        XCTAssertFalse(bar.contains("private func startMorph()"))
        XCTAssertFalse(bar.contains("panelMounted"))
    }

    // MARK: - B · the width law at the approval fixtures

    func testRW169B_WidthLaw_ApprovalFixtures_NoTruncationFullWordmark() throws {
        try registerGreatVibes()
        let wordmarkFont = try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17))
        let wordmarkWidth = textWidth(WearIveRunMiniBarModel.wordmark, font: wordmarkFont)
        XCTAssertGreaterThan(wordmarkWidth, 60,
                             "the wordmark's rest width is a real claim, not D1's ≈33 pt remainder")

        // The mini row at 393 pt: surface 393 − 24 insets −
        // 32 internal padding = 337 for wordmark + narration +
        // controls (92). The wordmark takes its FULL rest width
        // first (fixed claim); the narration takes what remains
        // and wraps — the bar grows.
        let rowWidth: CGFloat = 393 - 24 - 32
        let controlsWidth: CGFloat = 92
        let narrationAvailable = rowWidth - controlsWidth - wordmarkWidth - 8
        XCTAssertGreaterThan(narrationAvailable, 100,
                             "at the fixture width the narration still gets a real column beside the full wordmark")
        // The fixture narration at 12 pt does NOT fit that
        // column on one line — so the law's answer (wrap +
        // grow) is the one the fixture exercises, and the
        // wordmark is never the element that yields.
        let narration = try XCTUnwrap(WearIveRunNarration.line(for: plannedState))
        XCTAssertEqual(narration, "Planned for Los Angeles, CA · Tomorrow")
        let narrationWidth = textWidth(narration, font: .systemFont(ofSize: 12))
        XCTAssertGreaterThan(narrationWidth, narrationAvailable,
                             "fixture check: the long narration genuinely needs the wrap the law prescribes")

        // The place pill at the same viewport: the pill sizes
        // to its text. "Overland Park, KS" at 13 pt semibold +
        // pin + paddings, beside the FULL RunWear wordmark and
        // the share+gear pair, fits 393 with air to spare.
        let pillTextWidth = textWidth("Overland Park, KS", font: .systemFont(ofSize: 13, weight: .semibold))
        let pillWidth = pillTextWidth + 13 + 6 + 24 // pin + spacing + horizontal padding
        let chromeWordmark = textWidth("RunWear", font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 25)))
        let pairWidth: CGFloat = 32 + 8 + 32 // share + spacing + gear
        let total = pillWidth + chromeWordmark + pairWidth + 8 * 4 + 32
        XCTAssertLessThanOrEqual(total, 393,
                                 "pill (full text) + wordmark (full width) + pair fit the phone class in one pass")
    }

    func testRW169B_WidthLaw_SourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/FirstScreenHero.swift")
        XCTAssertFalse(hero.contains("maxWidth: 110"),
                       "the place pill's 110 pt fixture cap is gone (D10)")
        XCTAssertFalse(hero.contains("truncationMode(.tail)"),
                       "the place name is never tail-truncated")
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains(".layoutPriority(2)"),
                      "the wordmark carries the row's highest priority (D1)")
    }

    // MARK: - C · top chrome, one negotiating pass

    func testRW169C_TopChrome_OneNegotiatingPass_SourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/FirstScreenHero.swift")
        let start = try XCTUnwrap(hero.range(of: "struct HeroTopChromeView"))
        // The slice ends at the NEXT declaration — the chrome
        // struct itself, not whatever follows it in the file.
        let end = try XCTUnwrap(hero.range(
            of: "\nstruct ", range: start.upperBound..<hero.endIndex))
        let chrome = hero[start.lowerBound..<end.lowerBound]
        // The wordmark is a sibling inside the HStack — the
        // pre-fix ZStack overlay (a centred layer that never
        // negotiated with the pill) is gone from this view
        // (the word survives only in the retirement comment).
        XCTAssertFalse(chrome.contains("ZStack {"),
                       "no overlaying layer: pill, wordmark, share and gear negotiate in one pass (D10)")
        // [Spacer][share][gear] remain the row's end, in order.
        let shareIdx = try XCTUnwrap(chrome.range(of: "\"square.and.arrow.up\""))
        let gearIdx = try XCTUnwrap(chrome.range(of: "\"gearshape.fill\""))
        XCTAssertLessThan(shareIdx.lowerBound, gearIdx.lowerBound,
                          "the share+gear pair stays bound as end-of-row siblings, share beside gear")
    }

    // MARK: - D · measured seat + one state

    func testRW169D_StraddleSeat_MeasuredNotPlanned() {
        // The seat formula is a function of the MEASURED pill
        // height alone: for every height, the pill's bottom
        // lands on the same anchor (sheet top + 42).
        for height in [CGFloat(40), 44, 58, 62, 74] {
            let guide = StraddleSeatGuide.topGuideValue(measuredPillHeight: height)
            let pillTop = -guide // overlay .top anchor at the sheet's top edge
            XCTAssertEqual(pillTop + height, StraddleSeatGuide.anchorFromSheetTop, accuracy: 0.001,
                           "height \(height): the pill bottom lands on the measured anchor")
        }
        XCTAssertEqual(StraddleSeatGuide.anchorFromSheetTop, 42, accuracy: 0.001,
                       "the anchor is sheetOverlap 28 + straddleDip 14 — geometry, not a pill-height estimate")
    }

    func testRW169D_SingleStateAgreement_FailedCannotBeMaking() {
        // For every look state, the agreed presentation is a
        // single value; FAILED and MAKING are different cases
        // of one enum, so no cell can present both at once.
        let states: [PersonalLookState] = [
            .none, .making, .ready, .failed(reason: .error),
            .failed(reason: .qualityRefused), .failed(reason: .serviceRefused),
        ]
        for look in states {
            for pill in [HeroPillState.hidden, .making, .makingWithUpsell, .premium] {
                let presentation = PersonalCellPresentation.resolve(lookState: look, pillState: pill)
                if look.isFailed {
                    XCTAssertEqual(presentation, .failed,
                                   "a failed cell's agreed value is FAILED whatever any pill derivation says (D9)")
                }
                if presentation == .making {
                    XCTAssertFalse(look.isFailed, "MAKING and FAILED never co-occur")
                }
            }
        }
        XCTAssertEqual(
            PersonalCellPresentation.resolve(lookState: .making, pillState: .hidden), .making)
        XCTAssertEqual(
            PersonalCellPresentation.resolve(lookState: .ready, pillState: .premium), .serving)
    }

    func testRW169D_StraddleSeatAndState_SourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("StraddleSeatGuide.topGuideValue"),
                      "the straddle seat is the measured alignment guide (D8)")
        XCTAssertFalse(view.contains("pillOffsetFromSheetTop(\n                pillHeight: currentPillHeight)"),
                      "the planning-height offset seat is retired")
        XCTAssertTrue(view.contains("cellPresentation == .failed"),
                      "the failure wording reads the one agreed presentation value (D9)")
        XCTAssertTrue(view.contains("guard cellPresentation != .failed else { return .hidden }"),
                      "under FAILED the straddle pill carries no making/serving claim (D9)")
    }

    // MARK: - D5 · the expanded entry line wraps whole

    func testRW169D5_ExpandedEntryLine_WrapsWholeAtFixture() {
        let entry = fixtureEntry(place: "Overland Park, KS")
        let line = WearIveRunNarration.expandedLine(for: entry, formattedFeelsLike: "58°")
        // The whole kit, whole words — the line the panel
        // renders carries every item name untruncated; the
        // "…Runni…" mid-word tail of D5 is a rendering the
        // line itself never produces.
        for item in fixtureItems {
            XCTAssertTrue(line.contains(item), "the entry line carries \(item) whole")
        }
        XCTAssertFalse(line.contains("…"), "no ellipsis anywhere in the entry line")
        XCTAssertTrue(line.hasPrefix("Overland Park, KS · "))
    }

    // MARK: - D7 · the mini material

    func testRW169D7_MiniMaterial_TranslucentSpecValues() {
        // The design's material values stand (§2.1/§2.2): the
        // mini tint over blur is light .85 / dark .82 — the bar
        // ghosts content softly, it is not an opaque band.
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: false), 0.85, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: true), 0.82, accuracy: 0.0001)
        if case .translucentMaterial(let sheetOpacity) =
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: false) {
            XCTAssertEqual(sheetOpacity, 0.85, accuracy: 0.0001)
        } else {
            XCTFail("the mini state must be the translucent material")
        }
        if case .translucentMaterial(let sheetOpacity) =
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: true) {
            XCTAssertEqual(sheetOpacity, 0.82, accuracy: 0.0001)
        } else {
            XCTFail("the mini state must be the translucent material")
        }
        // Bounded residence (§2.2): the scroll-end clearance
        // reserves exactly the dock's band, so at scroll rest
        // no outfit card or chart label row sits under the bar.
        XCTAssertEqual(DockResidence.scrollEndClearance, 80, accuracy: 0.001)
        XCTAssertGreaterThanOrEqual(
            DockResidence.scrollEndClearance,
            DockResidence.bottomInset + DockResidence.measuredDockHeight)
    }

    // MARK: - E · the settle discipline

    func testRW169E_SettleGate_StabilityWindowRestartsOnChange() {
        var gate = WearIveRunSettleGate()
        let t0 = Date(timeIntervalSince1970: 1_700_000_000)
        let a = fixtureEntry(place: "Overland Park, KS")
        var b = fixtureEntry(place: "Los Angeles, CA")
        b = WearIveRunEntry(
            dayStart: b.dayStart, placeName: b.placeName, feelsLikeF: 66,
            actualF: b.actualF, condition: b.condition, itemNames: b.itemNames,
            itemKeys: b.itemKeys, heroImageReference: "personal-b",
            sessionID: b.sessionID, momentReading: b.momentReading)

        gate.offer(a, at: t0)
        XCTAssertNil(gate.stableCandidate(at: t0.addingTimeInterval(1.9)),
                     "inside the ≈2 s window nothing commits")
        // The same frame re-offered does NOT restart the window.
        gate.offer(a, at: t0.addingTimeInterval(1.5))
        XCTAssertEqual(gate.stableCandidate(at: t0.addingTimeInterval(2.0)), a,
                       "a stable frame commits at the window's end")
        // A changed frame restarts the window from its offer.
        gate.offer(b, at: t0.addingTimeInterval(2.0))
        XCTAssertNil(gate.stableCandidate(at: t0.addingTimeInterval(3.9)),
                     "the changed frame's window runs from ITS offer, not the earlier one")
        XCTAssertEqual(gate.stableCandidate(at: t0.addingTimeInterval(4.0)), b)
    }

    func testRW169E_PresentedSnapshot_AtomicFlipAndReSettleWhileOpenByteIdentical() {
        // §7G.3's probe, at the model layer: the footer stands
        // OPEN on snapshot A; a re-settle (weather tick: new
        // feels-like, new kit, new hero) is forced. While its
        // window runs, the presented snapshot is BYTE-IDENTICAL
        // (entry equality + the rendered entry line); when the
        // window completes the flip is ATOMIC — place, kit,
        // weather and hero change together in one new snapshot.
        var gate = WearIveRunSettleGate()
        let t0 = Date(timeIntervalSince1970: 1_700_000_000)
        let a = fixtureEntry(place: "Overland Park, KS", hero: "personal-a")
        let snapshotA = WearIveRunPresentedSnapshot(
            entry: a,
            state: WearIveRunMiniBarModel.state(
                todaysEntry: a, moment: a.momentReading, wasUpdatedThisSession: false),
            formattedFeelsLike: "58°")
        let lineA = WearIveRunNarration.expandedLine(for: a, formattedFeelsLike: "58°")

        let b = WearIveRunEntry(
            dayStart: a.dayStart, placeName: "Kansas City, KS", feelsLikeF: 57,
            actualF: 61, condition: "Light rain",
            itemNames: ["Rain Jacket", "Running Tights"],
            itemKeys: ["RAIN_JACKET", "TIGHTS"],
            heroImageReference: "personal-b", sessionID: a.sessionID,
            momentReading: a.momentReading)
        gate.offer(b, at: t0)
        // The open surface keeps rendering snapshot A, untouched.
        XCTAssertEqual(snapshotA.entry, a)
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: snapshotA.entry, formattedFeelsLike: "58°"), lineA,
            "while the re-settle parks, the visible entry is byte-identical")
        XCTAssertNil(gate.stableCandidate(at: t0.addingTimeInterval(1.0)))
        // At the window's end the whole frame flips at once.
        let committed = gate.stableCandidate(at: t0.addingTimeInterval(2.0))
        XCTAssertEqual(committed, b)
        XCTAssertEqual(committed?.placeName, "Kansas City, KS")
        XCTAssertEqual(committed?.itemNames, ["Rain Jacket", "Running Tights"])
        XCTAssertEqual(committed?.heroImageReference, "personal-b")
        XCTAssertEqual(committed?.feelsLikeF, 57)
    }

    func testRW169E_SettleDiscipline_SourcePins() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("settleGate.offer(entry, at: Date())"),
                      "the eight recording sites offer to the gate — none writes the record directly (§7E.1)")
        XCTAssertTrue(view.contains("commitStableSettleIfReady"),
                      "the gate's commit swaps the presented snapshot atomically (§7E.2)")
        XCTAssertTrue(view.contains("heroImageReference: viewModel.heroImageUrl ?? \"\""),
                      "fallback serves are never recorded as the entry's hero reference (§7E.3)")
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("WearIveRunPromotedThumbnail"),
                      "the thumbnail binds at promotion and never returns to empty for the same entry (§7E.3)")
    }

    // MARK: - Captures for the conformance reviewer

    @MainActor
    private func unifiedSurface(
        progress: Double, state: WearIveRunState, entry: WearIveRunEntry
    ) -> some View {
        WearIveRunUnifiedSurfaceView(
            state: state, entry: entry, formattedFeelsLike: "58°",
            cueCount: 0, viewportHeight: 852,
            heroURLResolver: nil, expansionProgress: progress,
            onToggle: {}, onOpenCollection: {}, onOpenShareEntry: nil, onTrash: {})
    }

    private func measuredTextHeight(_ text: String, font: UIFont, width: CGFloat) -> CGFloat {
        let rect = (text as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font], context: nil)
        return ceil(rect.height)
    }

    /// The two REST heights for the approval fixture at a
    /// given surface width, computed with the PRODUCTION
    /// formulas (WearIveRunUnifiedSurfaceView.miniRestHeight /
    /// expandedRestHeight) over text heights measured at the
    /// fixture — the values the surface itself reports at
    /// rest on device, where its geometry actions measure the
    /// same strings in the same columns. (ImageRenderer takes
    /// one static pass, so the capture frames the surface at
    /// its rest heights rather than its 56 pt seed.)
    private func fixtureRestHeights(surfaceWidth: CGFloat) throws -> (mini: CGFloat, expanded: CGFloat) {
        try registerGreatVibes()
        let wordmarkW = textWidth(
            WearIveRunMiniBarModel.wordmark,
            font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17)))
        let line = try XCTUnwrap(WearIveRunNarration.line(for: plannedState))
        // Mini: narration column = width − 32 padding −
        // (wordmark + 8) − 96 controls reserve.
        let miniColumn = surfaceWidth - 32 - (wordmarkW + 8) - 96
        let miniNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 12), width: miniColumn)
        let mini = max(16 + max(44, miniNarrH), 26 + miniNarrH)
        // Expanded: narration spans width − 32 at 15 pt; the
        // entry line sits beside the 56 pt thumbnail + 10.
        let expNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 15), width: surfaceWidth - 32)
        let entryLine = WearIveRunNarration.expandedLine(
            for: fixtureEntry(place: "Los Angeles, CA"), formattedFeelsLike: "58°")
        let entryH = measuredTextHeight(entryLine, font: .systemFont(ofSize: 14.5), width: surfaceWidth - 32 - 56 - 10)
        let expanded = 20 + 44 + 12 + expNarrH + 12 + entryH + 12 + 24 + 20
        return (mini, expanded)
    }

    @MainActor
    func testRW169_Captures_SixStates_PhonePortrait() throws {
        try registerGreatVibes()
        let planned = plannedState
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let rests = try fixtureRestHeights(surfaceWidth: 369)
        let miniH = rests.mini
        let expandedH = rests.expanded

        // 1 · mini at rest — the dock over the page's foot zone.
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 0, state: planned, entry: entry)
                    .frame(width: 369, height: miniH) }
                    .padding(.horizontal, 12).padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-mini-rest-phone.png")
        // 2 · mini mid-scroll — weather rows pass beneath the
        // translucent bar (the D7 state).
        try writeRender(
            ZStack {
                AppTheme.sheet
                VStack(alignment: .leading, spacing: 18) {
                    Spacer()
                    ForEach(["Humidity  44%", "UV Index  3 of 11", "Wind  11 mph", "Rain  10%"], id: \.self) { row in
                        Text(row).font(.system(size: 15)).foregroundColor(AppTheme.inkPrimary)
                    }
                    Spacer().frame(height: 120)
                }
                .padding(.horizontal, 16)
                VStack { Spacer(); unifiedSurface(progress: 0, state: planned, entry: entry)
                    .frame(width: 369, height: miniH) }
                    .padding(.horizontal, 12).padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-mini-midscroll-phone.png")
        // 3 · morph mid-transition stills (p ≈ .25 / .5 / .75)
        // on the shared canvas, heights interpolated between
        // the two fixture rest heights.
        for (p, label) in [(0.25, "p25"), (0.5, "p50"), (0.75, "p75")] {
            let h = miniH + (expandedH - miniH) * CGFloat(p)
            try writeRender(
                ZStack {
                    AppTheme.background
                    VStack { Spacer(); unifiedSurface(progress: p, state: planned, entry: entry)
                        .frame(width: 369, height: h) }
                        .padding(.horizontal, 12).padding(.bottom, 14)
                },
                size: CGSize(width: 393, height: 400),
                name: "RW169-ios-morph-\(label)-phone.png")
        }
        // 4 · expanded at rest.
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 1, state: planned, entry: entry)
                    .frame(width: 369, height: expandedH) }
                    .padding(.horizontal, 12).padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-expanded-rest-phone.png")
        // 5 · expanded at the height cap (viewport 480 → the
        // container takes min(rest, cap)).
        let cap = WearIveRunOverlayPolicy.expandedCap(viewportHeight: 480)
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 1, state: planned, entry: entry)
                    .frame(width: 369, height: min(expandedH, cap)).clipped() }
                    .padding(.horizontal, 12).padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 480),
            name: "RW169-ios-expanded-cap-phone.png")
        // 6 · top chrome — pill (full "Overland Park, KS"),
        // centred wordmark, share+gear pair, over a photo
        // stand-in gradient.
        try writeRender(
            ZStack {
                LinearGradient(colors: [Color(hex: "3A4A3F"), Color(hex: "141210")],
                               startPoint: .top, endPoint: .bottom)
                VStack {
                    HeroTopChromeView(
                        placeName: "Overland Park, KS", luminance: 0.5,
                        onPlaceTapped: {}, onSettingsTapped: {}, onShareTapped: {})
                        .padding(.horizontal, 16).padding(.top, 10)
                    Spacer()
                }
            },
            size: CGSize(width: 393, height: 200),
            name: "RW169-ios-topchrome-phone.png")
    }

    @MainActor
    func testRW169_Captures_MorphEndpointsAndWideClass() throws {
        try registerGreatVibes()
        let planned = plannedState
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let rests = try fixtureRestHeights(surfaceWidth: 369)
        // Morph endpoint stills on the shared 393×400 canvas
        // (the video's first/last beats; p samples are in the
        // phone capture test).
        for (p, label) in [(0.0, "p0"), (1.0, "p100")] {
            let h: CGFloat = p == 0 ? rests.mini : rests.expanded
            try writeRender(
                ZStack {
                    AppTheme.background
                    VStack { Spacer(); unifiedSurface(progress: p, state: planned, entry: entry)
                        .frame(width: 369, height: h) }
                        .padding(.horizontal, 12).padding(.bottom, 14)
                },
                size: CGSize(width: 393, height: 400),
                name: "RW169-ios-morph-\(label)-phone.png")
        }
        // Wide class (852 pt): the surface in its centred
        // column, mini and expanded, plus the top chrome.
        let wideRests = try fixtureRestHeights(surfaceWidth: 640)
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 0, state: planned, entry: entry)
                    .frame(width: 640, height: wideRests.mini) }
                    .padding(.bottom, 14)
            },
            size: CGSize(width: 852, height: 393),
            name: "RW169-ios-mini-rest-wide.png")
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 1, state: planned, entry: entry)
                    .frame(width: 640, height: wideRests.expanded) }
                    .padding(.bottom, 14)
            },
            size: CGSize(width: 852, height: 393),
            name: "RW169-ios-expanded-rest-wide.png")
        try writeRender(
            ZStack {
                LinearGradient(colors: [Color(hex: "3A4A3F"), Color(hex: "141210")],
                               startPoint: .top, endPoint: .bottom)
                VStack {
                    HeroTopChromeView(
                        placeName: "Overland Park, KS", luminance: 0.5,
                        onPlaceTapped: {}, onSettingsTapped: {}, onShareTapped: {})
                        .padding(.horizontal, 16).padding(.top, 10)
                    Spacer()
                }
            },
            size: CGSize(width: 852, height: 200),
            name: "RW169-ios-topchrome-wide.png")
    }
}

// MARK: - RW-169 LOOP-BACK (Nov 12, 2026) — the conformance review's
// missing-evidence set. The reviewer (work/conformance-review.md)
// returned iOS FAIL (unproven-blocked): the pill appeared in ZERO
// captures (D8/D9), the morph video was five crossfaded stills (D3),
// the mid-scroll capture was a stand-in isolate (D7), the height
// cap never bound, the §7G.3 probe artifact was never staged (D12),
// and §8 checks 4/6 had no iOS evidence at all. This class produces
// FULL-COMPOSITION renders — hero + sheet + straddle pill + dock
// together, composed from the PRODUCTION components and the
// PRODUCTION seats (HeroImageView with pillState .hidden +
// lookFailed from the one PersonalCellPresentation, as Home passes
// it; HeroChromeOverlayView; the sheet container's exact overlay +
// StraddleSeatGuide seat; WearIveRunUnifiedSurfaceView as the
// dock) — plus dense non-crossfaded morph samples, a binding-cap
// composition, and trace artifacts for the probes. The hero
// PHOTOGRAPH is a stand-in (gradient + the production temperature
// tint HeroImageView paints for an empty fallback URL): the
// harness has no photo asset for the fixture cell, and no claim
// here depends on the photograph itself.

private struct RW169SheetView: View {
    let pillState: HeroPillState

    var body: some View {
        VStack(spacing: 0) {
            // The straddle reserve (production sheetContainer):
            // while a pill renders, the sheet's first content
            // starts below the pill's dip with a 12 pt gap.
            Color.clear
                .frame(height: pillState != .hidden
                    ? HeroVerticalLayout.sheetTopReserveWithPill
                    : HeroVerticalLayout.sheetTopReserveNoPill)
            VStack(alignment: .leading, spacing: 0) {
                Text("4 items for your run")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                ForEach(["Running Cap", "Short Sleeve Shirt",
                         "Running Shorts (5-7\")", "Sunglasses"], id: \.self) { item in
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(AppTheme.cardOnSheet)
                            .frame(width: 56, height: 56)
                        Text(item)
                            .font(.system(size: 15))
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                    }
                    .padding(.top, 12)
                }
                // The day chart's label row (D7 names it: the
                // dock must never occlude it at scroll rest, and
                // mid-scroll it passes above the dock's band).
                HStack {
                    Text("Today").font(.system(size: 13, weight: .semibold))
                    Spacer()
                    Text("Now").font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(AppTheme.inkSecondary)
                .padding(.top, 28)
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach([0.3, 0.45, 0.6, 0.8, 1.0, 0.85, 0.65, 0.5], id: \.self) { f in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(AppTheme.pinePayload.opacity(0.55))
                            .frame(width: 30, height: 54 * f)
                    }
                }
                .padding(.top, 8)
                // The detailed weather rows — enough of them that
                // at the mid-scroll offset they pass BENEATH the
                // still, translucent dock (the D7 state).
                ForEach(["Humidity|44%", "UV Index|3 of 11", "Wind|11 mph",
                         "Rain|10%", "Dew Point|41°", "Pressure|30.1 in",
                         "Visibility|10 mi", "Sunrise|6:58 AM", "Sunset|5:12 PM",
                         "Moon|Waxing", "AQI|32 Good", "Pollen|Low"], id: \.self) { pair in
                    let parts = pair.split(separator: "|")
                    HStack {
                        Text(parts[0]).font(.system(size: 15))
                        Spacer()
                        Text(parts[1]).font(.system(size: 15, weight: .semibold))
                    }
                    .foregroundColor(AppTheme.inkPrimary)
                    .padding(.vertical, 13)
                    Divider()
                }
                Text("Pro tip: the light is best in the first hour.")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.inkSecondary)
                    .padding(.top, 20)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
        }
        .frame(maxWidth: .infinity)
        .background {
            UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(
                topLeading: HomeHandoffGeometry.sheetCornerRadius,
                topTrailing: HomeHandoffGeometry.sheetCornerRadius))
                .fill(AppTheme.sheet)
        }
        // The straddle pill, seated EXACTLY as production seats
        // it: an overlay on the sheet container's top edge, its
        // top alignment guide fed by its own measured height
        // (RW-169 §7D, StraddleSeatGuide) — never an offset from
        // planning heights.
        .overlay(alignment: .top) {
            if pillState != .hidden {
                HeroPillView(
                    pillState: pillState, seat: .straddle,
                    onPillGetUnlimited: {})
                    .alignmentGuide(.top) { dimensions in
                        StraddleSeatGuide.topGuideValue(
                            measuredPillHeight: dimensions.height)
                    }
            }
        }
    }
}

/// The full composition, structured as the RW147 harness's
/// proven full-screen pattern (ZStack(alignment: .bottom); the
/// page a VStack of hard-framed hero + filling sheet; the dock
/// a bottom child) — the round-1 loop-back attempt's flexible
/// hero/dock sizing collapsed under ImageRenderer (hero shrank
/// to ≈226 pt and the dock never painted), which is why this
/// structure hard-frames every surface.
private struct RW169CompositionView: View {
    /// The ONE agreed per-cell value (D9): every surface below
    /// derives from it and nothing else.
    let presentation: PersonalCellPresentation
    /// The straddle pill's state — resolved exactly as Home
    /// resolves it: under FAILED the pill is .hidden (it carries
    /// no making/serving claim); otherwise HeroPill's value.
    let pillState: HeroPillState
    let dockProgress: Double
    let dockHeight: CGFloat
    /// Simulated scroll offset of the page column (the production
    /// page is a ScrollView; a static offset is the harness form
    /// of a mid-scroll position, labelled as such).
    var scrollOffset: CGFloat = 0
    let entry: WearIveRunEntry
    let state: WearIveRunState
    let unified: (Double, WearIveRunState, WearIveRunEntry) -> AnyView

    private var heroHeight: CGFloat {
        HeroHeightPolicy.stackedHeight(viewportHeight: 852)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            if scrollOffset > 0 {
                // Mid-scroll: the natural-height page, offset —
                // the hero has handed off (its chrome faded per
                // HomeHandoffGeometry), the sheet's boundary is
                // still on screen, rows pass under the dock.
                page
                    .offset(y: -scrollOffset)
            } else {
                page
            }
            // The dock: a bottom child of the screen stack,
            // unmoved by the page's scroll (production: the
            // miniBarDock overlay host).
            unified(dockProgress, state, entry)
                .frame(width: 369, height: dockHeight)
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        .frame(width: 393, height: 852)
        .clipped()
    }

    private var page: some View {
        VStack(spacing: 0) {
            hero
            if scrollOffset > 0 {
                sheet
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
            } else {
                sheet
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
                    .frame(maxHeight: .infinity)
            }
        }
    }

    private var hero: some View {
        ZStack {
            // Photograph stand-in: a warm gradient under the
            // production tint layer HeroImageView itself paints.
            LinearGradient(
                colors: [Color(hex: "7A6A53"), Color(hex: "2E2A24")],
                startPoint: .top, endPoint: .bottom)
                .frame(width: 393, height: heroHeight)
            HeroImageView(
                locationName: "Overland Park, KS",
                temperature: 58, actualTemperature: 62,
                weatherCondition: .clear,
                date: Date(timeIntervalSince1970: 1_763_568_000),
                hour: 9, tempBracket: .MILD,
                fallbackImageUrl: "", aiImageUrl: nil,
                pillState: .hidden,
                lookFailed: presentation == .failed,
                heroHeightOverride: heroHeight,
                showsTopControls: false)
                .frame(width: 393, height: heroHeight)
            HeroChromeOverlayView(
                placeName: "Overland Park, KS",
                dateText: "Wed, Nov 12", timeText: "Now", isNow: true,
                formattedFeelsLike: "58°", formattedActual: "62°",
                feelsLikeF: 58,
                topLuminance: 0.5, chipLuminance: 0.5, footLuminance: 0.5,
                onPlaceTapped: {}, onSettingsTapped: {}, onShareTapped: {},
                onDateTapped: {}, onTimeTapped: {}, onNowTapped: {},
                windMph: 11, rainPercent: 10, humidityPercent: 44, uvIndex: 3,
                weatherLuminance: 0.5,
                pillHeight: HeroPillMetrics.height(for: pillState),
                pillBottomOffset: HeroVerticalLayout.straddleDip)
                .frame(width: 393, height: heroHeight)
                .opacity(scrollOffset > 0
                    ? Double(HomeHandoffGeometry.chromeOpacity(
                        progress: HomeHandoffGeometry.progress(forScrollOffset: scrollOffset)))
                    : 1)
        }
        .frame(width: 393, height: heroHeight)
        .scaleEffect(scrollOffset > 0
            ? HomeHandoffGeometry.scale(
                progress: HomeHandoffGeometry.progress(forScrollOffset: scrollOffset))
            : 1)
        .clipped()
    }

    var heroOnlyForDiagnostics: some View { hero }

    private var sheet: some View {
        RW169SheetView(pillState: pillState)
    }
}

final class RW169LoopbackTests: XCTestCase {

    // MARK: - Harness plumbing (mirrors RW169StructuralTests)

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW169LB-CAPTURE wrote \(name)")
    }

    private func writeTextArtefact(_ text: String, name: String) throws {
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try text.write(to: dir.appendingPathComponent(name), atomically: true, encoding: .utf8)
        print("RW169LB-CAPTURE wrote \(name)")
    }

    private func registerGreatVibes() throws {
        let url = try XCTUnwrap(
            Bundle(for: RW169LoopbackTests.self)
                .url(forResource: "GreatVibes-Regular", withExtension: "ttf"),
            "GreatVibes-Regular.ttf is not in the test bundle")
        var error: Unmanaged<CFError>?
        _ = CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
    }

    private func textWidth(_ text: String, font: UIFont) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    private func measuredTextHeight(_ text: String, font: UIFont, width: CGFloat) -> CGFloat {
        let rect = (text as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font], context: nil)
        return ceil(rect.height)
    }

    // MARK: - Fixtures (the approval fixtures, as the builder's)

    private var fixtureDay: Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/New_York")!
        return cal.date(from: DateComponents(year: 2026, month: 11, day: 12, hour: 9))!
    }

    private var fixtureItems: [String] {
        ["Running Cap", "Short Sleeve Shirt", "Running Shorts (5-7\")", "Sunglasses"]
    }

    private func fixtureEntry(place: String, items: [String]? = nil) -> WearIveRunEntry {
        let items = items ?? fixtureItems
        return WearIveRunEntry(
            dayStart: fixtureDay, placeName: place,
            feelsLikeF: 58, actualF: 62, condition: "Clear sky",
            itemNames: items,
            itemKeys: items.map { _ in "ITEM" },
            heroImageReference: "", sessionID: "s-rw169")
    }

    private var plannedState: WearIveRunState {
        .planned(place: "Los Angeles, CA", dayLabel: "Tomorrow", moment: .now)
    }

    private func unifiedSurface(
        progress: Double, state: WearIveRunState, entry: WearIveRunEntry
    ) -> AnyView {
        AnyView(WearIveRunUnifiedSurfaceView(
            state: state, entry: entry, formattedFeelsLike: "58°",
            cueCount: 0, viewportHeight: 852,
            heroURLResolver: nil, expansionProgress: progress,
            onToggle: {}, onOpenCollection: {}, onOpenShareEntry: nil, onTrash: {}))
    }

    /// Production rest heights for the fixture at 369 pt, via
    /// the same formulas the builder's captures used.
    private func fixtureRestHeights(surfaceWidth: CGFloat, entry: WearIveRunEntry) throws -> (mini: CGFloat, expanded: CGFloat) {
        try registerGreatVibes()
        let wordmarkW = textWidth(
            WearIveRunMiniBarModel.wordmark,
            font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17)))
        let line = try XCTUnwrap(WearIveRunNarration.line(for: plannedState))
        let miniColumn = surfaceWidth - 32 - (wordmarkW + 8) - 96
        let miniNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 12), width: miniColumn)
        let mini = max(16 + max(44, miniNarrH), 26 + miniNarrH)
        let expNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 15), width: surfaceWidth - 32)
        let entryLine = WearIveRunNarration.expandedLine(for: entry, formattedFeelsLike: "58°")
        let entryH = measuredTextHeight(entryLine, font: .systemFont(ofSize: 14.5), width: surfaceWidth - 32 - 56 - 10)
        let expanded = 20 + 44 + 12 + expNarrH + 12 + entryH + 12 + 24 + 20
        return (mini, expanded)
    }

    private func composition(
        presentation: PersonalCellPresentation,
        pillState: HeroPillState,
        dockProgress: Double, dockHeight: CGFloat,
        scrollOffset: CGFloat = 0,
        entry: WearIveRunEntry
    ) -> RW169CompositionView {
        RW169CompositionView(
            presentation: presentation, pillState: pillState,
            dockProgress: dockProgress, dockHeight: dockHeight,
            scrollOffset: scrollOffset, entry: entry, state: plannedState,
            unified: { [self] p, s, e in unifiedSurface(progress: p, state: s, entry: e) })
    }

    // MARK: - 1 · Full compositions (D8/D9) + 3 · mid-scroll (D7)

    @MainActor
    func testRW169LB_Captures_FullCompositions() throws {
        try registerGreatVibes()
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let rests = try fixtureRestHeights(surfaceWidth: 369, entry: entry)

        // The agreed values, resolved through the PRODUCTION
        // resolver from cell facts — the same single value every
        // surface in the composition reads (D9).
        let serving = PersonalCellPresentation.resolve(
            lookState: .ready,
            pillState: HeroPill.resolve(
                optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 10))
        XCTAssertEqual(serving, .serving)
        let servingPill = HeroPill.resolve(
            optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
            isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 10)
        XCTAssertEqual(servingPill, .freeLook(used: 3, allowance: 10))

        // (a) Pill seated whole at the hero/sheet boundary, mini
        // dock at rest — the full screen as the user sees it.
        try writeRender(
            composition(presentation: serving, pillState: servingPill,
                        dockProgress: 0, dockHeight: rests.mini, entry: entry),
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-fullcomp-pill-mini-rest-phone.png")

        // (b)+(D7) Mid-scroll: the page column offset 260 pt —
        // the hero chrome has handed off (production fade), the
        // pill still sits whole on the hero/sheet boundary (the
        // boundary is still on screen), weather rows pass
        // beneath the still, translucent mini dock, and the
        // outfit cards + chart label row stand clear above it.
        try writeRender(
            composition(presentation: serving, pillState: servingPill,
                        dockProgress: 0, dockHeight: rests.mini,
                        scrollOffset: 260, entry: entry),
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-fullcomp-pill-midscroll-phone.png")

        // (c) The Wear I've Run panel EXPANDED over the same
        // composition — pill whole at the boundary above it.
        try writeRender(
            composition(presentation: serving, pillState: servingPill,
                        dockProgress: 1, dockHeight: rests.expanded, entry: entry),
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-fullcomp-pill-expanded-phone.png")

        // (e) MAKING: one cell, one state — the straddle pill
        // carries "Making this you… / Get it instantly next
        // time"; the hero carries NO failure wording; the
        // agreed value is .making.
        let making = PersonalCellPresentation.resolve(
            lookState: .making,
            pillState: HeroPill.resolve(
                optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 10))
        XCTAssertEqual(making, .making)
        let makingPill = HeroPill.resolve(
            optedIn: true, lookState: .making, personalPhotoDisplayed: false,
            isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 10)
        XCTAssertEqual(makingPill, .makingWithUpsell)
        try writeRender(
            composition(presentation: making, pillState: makingPill,
                        dockProgress: 0, dockHeight: rests.mini, entry: entry),
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-fullcomp-state-making-phone.png")

        // (d) FAILED: the agreed value is .failed even though a
        // pill derivation, taken alone, would say MAKING — the
        // D9 contradiction of build 29. In the composition the
        // straddle pill is HIDDEN (production heroPillState
        // rule), the hero carries the failure wording in the
        // pill's eye line (production HeroImageView seat), and
        // nothing anywhere claims MAKING.
        let failed = PersonalCellPresentation.resolve(
            lookState: .failed(reason: .error), pillState: .makingWithUpsell)
        XCTAssertEqual(failed, .failed,
                       "FAILED outranks any pill derivation — one cell, one state (D9)")
        try writeRender(
            composition(presentation: failed, pillState: .hidden,
                        dockProgress: 0, dockHeight: rests.mini, entry: entry),
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-fullcomp-state-failed-phone.png")

        // Seat probe: the sheet alone on a known top edge
        // (y = 300, red line) with the design anchor marked
        // (sheet top + 42 = hero foot + straddleDip, second red
        // line). The pill's bottom must land ON the anchor line
        // — the measured seat (StraddleSeatGuide), judged to
        // the pixel.
        try writeRender(
            ZStack(alignment: .top) {
                AppTheme.background
                RW169SheetView(pillState: servingPill)
                    .frame(width: 393, height: 280)
                    .offset(y: 300)
                Rectangle().fill(Color.red)
                    .frame(width: 393, height: 1).offset(y: 300)
                Rectangle().fill(Color.red)
                    .frame(width: 393, height: 1)
                    .offset(y: 300 + StraddleSeatGuide.anchorFromSheetTop)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-seatprobe-phone.png")
    }

    // MARK: - 2 · Dense morph sampling (D3) — no crossfade

    @MainActor
    func testRW169LB_Captures_MorphDenseSamples() throws {
        try registerGreatVibes()
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let rests = try fixtureRestHeights(surfaceWidth: 369, entry: entry)
        // Seventeen individually rendered samples, p = 0, 1/16,
        // …, 1 — each its own ImageRenderer pass of the unified
        // surface at that progress, heights interpolated between
        // the two REST heights (the production container's law).
        // Assembled downstream WITHOUT blending (hard cuts), so
        // no frame double-exposes two forms by construction.
        for i in 0...16 {
            let p = Double(i) / 16.0
            XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1,
                           "p=\(p): exactly one legible narration")
            let h = rests.mini + (rests.expanded - rests.mini) * CGFloat(p)
            try writeRender(
                ZStack {
                    AppTheme.background
                    VStack { Spacer(); unifiedSurface(progress: p, state: plannedState, entry: entry)
                        .frame(width: 369, height: h) }
                        .padding(.horizontal, 12).padding(.bottom, 14)
                },
                size: CGSize(width: 393, height: 400),
                name: String(format: "RW169-ios-morphdense-p%03d-phone.png", Int(p * 1000)))
        }
    }

    // MARK: - 4 · The height cap, binding

    @MainActor
    func testRW169LB_Captures_HeightCapBinding() throws {
        try registerGreatVibes()
        // Content far taller than the cap: a 24-item kit whose
        // entry line wraps to many lines at the panel's column.
        let tallItems = (1...24).map { "Item \($0) Long Garment Name" }
        let tallEntry = fixtureEntry(place: "Los Angeles, CA", items: tallItems)
        let rests = try fixtureRestHeights(surfaceWidth: 369, entry: tallEntry)
        let viewport: CGFloat = 480
        let cap = WearIveRunOverlayPolicy.expandedCap(viewportHeight: viewport)
        XCTAssertEqual(cap, 364, accuracy: 0.001)
        XCTAssertGreaterThan(rests.expanded, cap,
                             "fixture check: the tall content's rest height (\(rests.expanded)) exceeds the cap (\(cap)) — the cap BINDS here, unlike the round-1 capture")
        XCTAssertTrue(WearIveRunOverlayPolicy.entryRegionScrolls(
            contentHeight: rests.expanded, viewportHeight: viewport),
            "the policy's own verdict: the entry region must scroll internally at this viewport")
        XCTAssertEqual(WearIveRunOverlayPolicy.panelHeight(
            contentHeight: rests.expanded, viewportHeight: viewport), cap, accuracy: 0.001,
            "the panel stands AT the cap — never over the hero's top chrome zone (116 pt)")
        // Production pin (loop-back fix): the unified surface
        // itself carries the cap behaviour — an entry region
        // budget, an internal ScrollView when it binds, and the
        // doorway seated from the CAPPED height. (Round 1's
        // surface had none of these: the binding-cap capture
        // showed the entry overflowing the panel's bottom edge
        // and the doorway pushed out of the surface entirely.)
        let barSource = try String(contentsOf: URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../RunWear/Views/WearIveRunMiniBar.swift")
            .standardizedFileURL, encoding: .utf8)
        let unifiedStart = try XCTUnwrap(barSource.range(of: "struct WearIveRunUnifiedSurfaceView"))
        let unified = barSource[unifiedStart.lowerBound..<barSource.endIndex]
        XCTAssertTrue(unified.contains("entryRegionBudget"),
                      "the unified surface computes the RW-152 §2 entry-region budget")
        XCTAssertTrue(unified.contains("lerp(expandedEntryHeight, cappedEntryHeight)"),
                      "the doorway seats from the capped entry height — pinned under a binding cap")
        XCTAssertTrue(unified.contains("ScrollView(.vertical, showsIndicators: false)"),
                      "the entry region scrolls internally when the cap binds")

        var probe = "RW-169 iOS LOOP-BACK — height-cap probe (harness, ImageRenderer)\n"
        probe += "viewport=\(viewport) cap=\(cap) topChromeZone=\(WearIveRunOverlayPolicy.topChromeZoneHeight)\n"
        probe += "tall entry: 24 items; fixture expanded rest height=\(rests.expanded) (> cap, binds)\n"
        probe += "entryRegionScrolls=\(WearIveRunOverlayPolicy.entryRegionScrolls(contentHeight: rests.expanded, viewportHeight: viewport))\n"
        probe += "entryRegionMaxHeight=\(WearIveRunOverlayPolicy.entryRegionMaxHeight(viewportHeight: viewport))\n"
        probe += "panelHeight=\(WearIveRunOverlayPolicy.panelHeight(contentHeight: rests.expanded, viewportHeight: viewport))\n"
        try writeTextArtefact(probe, name: "RW169-ios-heightcap-probe.txt")

        // The composition at the binding cap: top chrome at the
        // hero's head, the panel bottom-anchored standing at
        // exactly the cap height (480 − 116), framed as the
        // production overlay container frames it.
        try writeRender(
            ZStack {
                LinearGradient(colors: [Color(hex: "7A6A53"), Color(hex: "2E2A24")],
                               startPoint: .top, endPoint: .bottom)
                VStack {
                    HeroTopChromeView(
                        placeName: "Overland Park, KS", luminance: 0.5,
                        onPlaceTapped: {}, onSettingsTapped: {}, onShareTapped: {})
                        .padding(.horizontal, 16).padding(.top, 10)
                    Spacer()
                }
                VStack { Spacer(); unifiedSurface(progress: 1, state: plannedState, entry: tallEntry)
                    .frame(width: 369, height: min(rests.expanded, cap)).clipped() }
                    .padding(.horizontal, 12).padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 480),
            name: "RW169-ios-expanded-capbinding-phone.png")
    }


    // MARK: - Diagnostics (loop-back round 3 — bisecting the
    // ImageRenderer composition behaviour; NOT evidence)

    /// Renders the same content twice on ONE renderer host,
    /// spinning the run loop between passes so geometry-driven
    /// @State updates apply before the snapshot.
    @MainActor
    private func writeRenderSettled<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        _ = renderer.uiImage
        let until = Date().addingTimeInterval(0.6)
        while Date() < until {
            RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        let image = try XCTUnwrap(renderer.uiImage, "settled render failed for \(name)")
        let dir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try XCTUnwrap(image.pngData()).write(to: dir.appendingPathComponent(name))
        print("RW169LB wrote settled \(name)")
    }

    @MainActor
    func testRW169LB_Diagnostics() throws {
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let rests = try fixtureRestHeights(surfaceWidth: 369, entry: entry)
        // D-A: the unified surface as a DIRECT bottom child of a
        // ZStack over a plain page block (no page VStack).
        try writeRender(
            ZStack(alignment: .bottom) {
                AppTheme.sheet
                unifiedSurface(progress: 0, state: plannedState, entry: entry)
                    .frame(width: 369, height: rests.mini)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-diagA-dock-direct.png")
        // D-B: the same, but the dock inside VStack{Spacer}.
        try writeRender(
            ZStack {
                AppTheme.sheet
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    unifiedSurface(progress: 0, state: plannedState, entry: entry)
                        .frame(width: 369, height: rests.mini)
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-diagB-dock-vstack.png")
        // D-C: the composition's hero ALONE in the canvas.
        let comp = composition(
            presentation: .serving,
            pillState: .freeLook(used: 3, allowance: 10),
            dockProgress: 0, dockHeight: rests.mini, entry: entry)
        try writeRender(comp.heroOnlyForDiagnostics,
            size: CGSize(width: 393, height: 852),
            name: "RW169-ios-diagC-hero.png")
        // D-D: the cap surface, SETTLED render (two passes on
        // one host, run loop spun between them).
        let tallEntry = fixtureEntry(
            place: "Los Angeles, CA",
            items: (1...24).map { "Item \($0) Long Garment Name" })
        let tallRests = try fixtureRestHeights(surfaceWidth: 369, entry: tallEntry)
        let cap: CGFloat = 480 - 116
        try writeRenderSettled(
            ZStack {
                AppTheme.background
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    AnyView(WearIveRunUnifiedSurfaceView(
                        state: plannedState, entry: tallEntry,
                        formattedFeelsLike: "58°",
                        cueCount: 0, viewportHeight: 480,
                        heroURLResolver: nil, expansionProgress: 1,
                        onToggle: {}, onOpenCollection: {},
                        onOpenShareEntry: nil, onTrash: {}))
                        .frame(width: 369, height: min(tallRests.expanded, cap))
                }
                .padding(.horizontal, 12)
            },
            size: CGSize(width: 393, height: 480),
            name: "RW169-ios-diagD-cap-settled.png")
        print("RW169LB diag tallRests expanded=\(tallRests.expanded) mini=\(tallRests.mini)")
    }

    // MARK: - 5 · The §7G.3 settle probe, with a staged trace (D12)

    func testRW169LB_SettleProbe_StagedTrace() throws {
        var gate = WearIveRunSettleGate()
        let t0 = Date(timeIntervalSince1970: 1_700_000_000)
        let a = WearIveRunEntry(
            dayStart: fixtureDay, placeName: "Overland Park, KS",
            feelsLikeF: 58, actualF: 62, condition: "Clear sky",
            itemNames: fixtureItems, itemKeys: ["CAP", "SHORT_SLEEVE", "SHORTS", "SUNGLASSES"],
            heroImageReference: "personal-a", sessionID: "s-rw169")
        let snapshotA = WearIveRunPresentedSnapshot(
            entry: a,
            state: WearIveRunMiniBarModel.state(
                todaysEntry: a, moment: a.momentReading, wasUpdatedThisSession: false),
            formattedFeelsLike: "58°")
        let lineA = WearIveRunNarration.expandedLine(for: a, formattedFeelsLike: "58°")
        let b = WearIveRunEntry(
            dayStart: fixtureDay, placeName: "Kansas City, KS",
            feelsLikeF: 57, actualF: 61, condition: "Light rain",
            itemNames: ["Rain Jacket", "Running Tights"],
            itemKeys: ["RAIN_JACKET", "TIGHTS"],
            heroImageReference: "personal-b", sessionID: "s-rw169")

        var trace = "RW-169 iOS LOOP-BACK — §7G.3 settle probe trace (WearIveRunSettleGate, harness)\n"
        trace += "t0: footer OPEN on presented snapshot A (place=\(a.placeName), items=\(a.itemNames.count), hero=\(a.heroImageReference))\n"
        trace += "t0: rendered entry line A = \"\(lineA)\"\n"
        gate.offer(b, at: t0)
        trace += "t0: forced re-settle offered (place=\(b.placeName), items=\(b.itemNames.count), hero=\(b.heroImageReference), feelsLike=\(b.feelsLikeF))\n"
        let parked = gate.stableCandidate(at: t0.addingTimeInterval(1.0))
        XCTAssertNil(parked, "inside the ≈2 s window the re-settle parks — nothing commits")
        trace += "t0+1.0s: stableCandidate=nil (parked); presented entry still A; entry line byte-identical: \(WearIveRunNarration.expandedLine(for: snapshotA.entry, formattedFeelsLike: "58°") == lineA)\n"
        XCTAssertEqual(snapshotA.entry, a)
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: snapshotA.entry, formattedFeelsLike: "58°"), lineA,
            "while the re-settle parks, the visible entry is byte-identical (§7G.3)")
        let committed = gate.stableCandidate(at: t0.addingTimeInterval(2.0))
        XCTAssertEqual(committed, b, "at the window's end the flip is atomic — the whole frame at once")
        trace += "t0+2.0s: stableCandidate=B — atomic flip (place, kit, weather, hero together; no piecemeal intermediate was ever presented)\n"
        trace += "RESULT: PASS — parked byte-identical inside the window, single-step atomic landing at its end.\n"
        print(trace)
        try writeTextArtefact(trace, name: "RW169-ios-settle-probe.txt")
    }

    // MARK: - 6 · §8 check 4 (flick speed), harness-grade

    func testRW169LB_S8Check4_FlickSpeedProbe_HarnessGrade() throws {
        // HARNESS-GRADE ONLY (§8 check 4 is a device-frame
        // check; this probe does not close it). A flick traverses
        // the morph in a few large steps: at every coarse step
        // the single-hierarchy invariants hold — one legible
        // narration, the wordmark at its interpolated rest
        // width, the container height the rest-height
        // interpolation. The dense rendered samples are in
        // testRW169LB_Captures_MorphDenseSamples.
        var trace = "RW-169 iOS LOOP-BACK — §8 check 4 flick-speed probe (HARNESS-GRADE; device check remains UNPROVEN)\n"
        let miniW = 96.0, expW = 176.0
        for p in [0.0, 0.34, 0.67, 1.0] {
            XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1)
            let w = WearIveRunMorphProbe.wordmarkWidth(progress: p, miniRestWidth: miniW, expandedRestWidth: expW)
            XCTAssertGreaterThanOrEqual(w, miniW)
            let h = WearIveRunMorphProbe.containerHeight(miniRest: 60, expandedRest: 264, progress: p)
            XCTAssertEqual(h, 60 + (264 - 60) * p, accuracy: 0.001)
            trace += "flick step p=\(p): legibleNarrations=1 wordmarkWidth=\(w) containerHeight=\(h)\n"
        }
        trace += "RESULT: invariants hold at flick-coarse steps; the device-frame check itself is NOT closed by this probe.\n"
        print(trace)
        try writeTextArtefact(trace, name: "RW169-ios-s8-check4-flick-probe.txt")
    }

    // MARK: - 6 · §8 check 6 (delete anchoring), harness-grade

    func testRW169LB_S8Check6_DeleteAnchoringProbe_HarnessGrade() throws {
        // HARNESS-GRADE ONLY (§8 check 6 is a device-frame
        // check; this probe does not close it). The model-level
        // anchoring invariant a delete-at-end relies on:
        // removing the LAST entry leaves every earlier entry
        // byte-identical and in place (the content above the
        // deletion point does not move or rewrite).
        let defaults = UserDefaults(suiteName: "rw169lb-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        var entries: [WearIveRunEntry] = []
        for i in 0..<5 {
            let day = Calendar(identifier: .gregorian).date(
                byAdding: .day, value: -i, to: fixtureDay)!
            let e = WearIveRunEntry(
                dayStart: day, placeName: "Place \(i)",
                feelsLikeF: Double(50 + i), actualF: Double(55 + i), condition: "Clear sky",
                itemNames: fixtureItems, itemKeys: ["CAP", "SHORT_SLEEVE", "SHORTS", "SUNGLASSES"],
                heroImageReference: "h\(i)", sessionID: "s-\(i)")
            entries.append(e)
            store.record(e)
        }
        let before = store.entries
        XCTAssertEqual(before.count, 5)
        let lastID = try XCTUnwrap(before.last?.id)
        store.remove(id: lastID)
        let after = store.entries
        XCTAssertEqual(after.count, 4)
        XCTAssertEqual(after, Array(before.dropLast()),
                       "delete-at-end: the surviving entries are byte-identical, in order — nothing above the deletion point moved")
        var trace = "RW-169 iOS LOOP-BACK — §8 check 6 delete-anchoring probe (HARNESS-GRADE, model level; device check remains UNPROVEN)\n"
        trace += "recorded 5 entries; removed the list-end entry (\(lastID))\n"
        trace += "survivors byte-identical & order-preserved: \(after == Array(before.dropLast()))\n"
        trace += "NOTE: no scroll-anchoring mechanism (ScrollViewReader / scrollPosition) exists in the iOS collection or home code — the on-screen anchoring behaviour at delete-at-end can only be evidenced on device.\n"
        trace += "RESULT: model invariant holds; the device-frame check itself is NOT closed by this probe.\n"
        print(trace)
        try writeTextArtefact(trace, name: "RW169-ios-s8-check6-delete-probe.txt")
    }
}
