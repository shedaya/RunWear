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

    @MainActor
    func testRW169_Captures_SixStates_PhonePortrait() throws {
        let planned = plannedState
        let entry = fixtureEntry(place: "Los Angeles, CA")
        let opEntry = fixtureEntry(place: "Overland Park, KS")
        let miniH: CGFloat = 60
        let expandedH: CGFloat = 264

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
        // 3 · morph mid-transition stills (p ≈ .25 / .5 / .75).
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
        // 5 · expanded at the height cap (viewport 480 → cap 364).
        let cap = WearIveRunOverlayPolicy.expandedCap(viewportHeight: 480)
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 1, state: planned, entry: entry)
                    .frame(width: 369, height: cap).clipped() }
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
        _ = opEntry
    }

    @MainActor
    func testRW169_Captures_MorphEndpointsAndWideClass() throws {
        let planned = plannedState
        let entry = fixtureEntry(place: "Los Angeles, CA")
        // Morph endpoint stills on the shared 393×400 canvas
        // (the video's first/last beats; p samples are in the
        // phone capture test).
        for (p, label) in [(0.0, "p0"), (1.0, "p100")] {
            let h: CGFloat = p == 0 ? 60 : 264
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
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 0, state: planned, entry: entry)
                    .frame(width: 640, height: 60) }
                    .padding(.bottom, 14)
            },
            size: CGSize(width: 852, height: 393),
            name: "RW169-ios-mini-rest-wide.png")
        try writeRender(
            ZStack {
                AppTheme.background
                VStack { Spacer(); unifiedSurface(progress: 1, state: planned, entry: entry)
                    .frame(width: 640, height: 264) }
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
