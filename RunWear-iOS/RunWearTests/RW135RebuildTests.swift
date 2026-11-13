import XCTest
import SwiftUI
import UIKit
import CoreText
import CryptoKit

/// RW-135 REBUILD (Nov 12, 2026) — Phase 2, iOS leg. Governed
/// by the Phase 1 conformance map
/// (release-prep/rw135-rebuild-2026-11-12/CONFORMANCE-MAP.md
/// §§0–7) and the parent brief's RB rows + R-1 ruling:
///
/// - RB-01: the R-1 trigger — reaching the page end presents
///   the expanded form; scrolling away re-docks it to MINI;
///   a tap summon persists; a collapse at the footer
///   suppresses re-presentation until the scroll leaves.
///   The pure decision (WearIveRunExpansionPolicy) and the
///   footer zone (WearIveRunFooterZone) are pinned here; the
///   host wiring is source-pinned.
/// - RB-02: ONE toggle control — the icon control and the bar
///   body fire the SAME action. Proven at harness level: both
///   production call sites funnel through the one
///   `handleToggleTap` → `onToggle` path, and driving that
///   production path's decision function toggles the state
///   from either activation. DEVICE tap-through is UNPROVEN
///   (no device in this lane) and is not claimed here.
/// - RB-03: the morph — RE-PINNED for RW-170 (Nov 12, 2026):
///   ONE narration whose slot migrates (font 12 → 15 pt,
///   beside-wordmark → below-header) at opacity 1 — the
///   legibility invariant holds by construction; the
///   two-placement dissolve RB-03 briefly carried is retired
///   on the diagnosis's evidence. Expanded-only content over
///   [0.45, 1], the 380 ms cubic-bezier(0.32, 0.72, 0, 1)
///   morph, Reduced Motion ≤150 ms.
/// - RW-170: ONE SURFACE (the ruled unified token, alpha-only
///   morph), ONE BAND (clearance prices the presented form),
///   ONE SHADOW (spec §2.1 values, constant), ONE NARRATION
///   (above), NO RE-FLOW (host composition never gated on the
///   expanded state) — pinned in the RW170 section below, with
///   composited full-home probes.
/// - RB-04: MINI content-sized at the measured ≈49 pt
///   footprint; EXPANDED height == intrinsic content height
///   (bottom padding 12).
/// - RB-05: corner radius constant 16 pt at every progress.
/// - RB-09: the Wear I've Run surface never touches the Make
///   It Me pill's seat or visibility (source evidence).
/// - Audit sweep: the §4.7 exit split (MINI 300 ms fade+sink;
///   EXPANDED 160 ms fade then 400 ms fold).
///
/// Captures: the morph at p = 0, 0.25, 0.5, 0.75, 1 driven
/// through the REAL expansionProgress value, Kansas City
/// fixture, rendered with the RW-169 lane's ImageRenderer
/// machinery (settled two-pass) into `render-artefacts/`.
final class RW135RebuildTests: XCTestCase {

    // MARK: - Harness plumbing (the RW-169 lane's idiom)

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
    private func writeRenderSettled<Content: View>(
        _ content: Content, size: CGSize, name: String
    ) throws -> Data {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        _ = renderer.uiImage
        let until = Date().addingTimeInterval(0.6)
        while Date() < until {
            RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        let image = try XCTUnwrap(renderer.uiImage, "settled render failed for \(name)")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW135-CAPTURE wrote \(name)")
        return data
    }

    private func registerGreatVibes() throws {
        let url = try XCTUnwrap(
            Bundle(for: RW135RebuildTests.self)
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

    // MARK: - Kansas City fixture (the brief's capture fixture)

    private var kcItems: [String] {
        ["Ear Warmer / Headband", "Light Long Sleeve", "Light Tights",
         "Light Gloves", "Reflective Gear"]
    }

    private func kcEntry() -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Calendar.current.startOfDay(for: Date()),
            placeName: "Kansas City, MO",
            feelsLikeF: 37, actualF: 41, condition: "Clear sky",
            itemNames: kcItems,
            itemKeys: ["EAR_WARMER", "LIGHT_LONG_SLEEVE", "LIGHT_TIGHTS",
                       "LIGHT_GLOVES", "REFLECTIVE"],
            heroImageReference: "", sessionID: "s-rw135")
    }

    /// "Updated to Kansas City, MO, Now" — the settled-today
    /// form for the fixture entry.
    private var kcState: WearIveRunState {
        .updated(payload: .placeAtMoment("Kansas City, MO", .now))
    }

    // MARK: - RB-01 · the footer zone from real geometry values

    func testRB01_FooterZone_FromGeometryValues() {
        // distanceToEnd: content 2000, bottom inset 0, container
        // (viewport) 800. At offset 0 the end is 1200 away;
        // at offset 400 it is exactly one viewport away (the
        // zone's edge); at 1200 the scroll rests at the end.
        XCTAssertEqual(WearIveRunFooterZone.distanceToEnd(
            contentHeight: 2000, contentInsetsBottom: 0,
            containerHeight: 800, contentOffsetY: 0), 1200, accuracy: 0.001)
        XCTAssertEqual(WearIveRunFooterZone.distanceToEnd(
            contentHeight: 2000, contentInsetsBottom: 0,
            containerHeight: 800, contentOffsetY: 400), 800, accuracy: 0.001)
        XCTAssertEqual(WearIveRunFooterZone.distanceToEnd(
            contentHeight: 2000, contentInsetsBottom: 34,
            containerHeight: 800, contentOffsetY: 1234), 0, accuracy: 0.001)
        // The zone: within ~one viewport of the scroll bottom.
        XCTAssertFalse(WearIveRunFooterZone.isAtFooter(distanceToEnd: 1200, viewportHeight: 800))
        XCTAssertTrue(WearIveRunFooterZone.isAtFooter(distanceToEnd: 800, viewportHeight: 800),
                      "the content end exactly one viewport away is in the zone")
        XCTAssertTrue(WearIveRunFooterZone.isAtFooter(distanceToEnd: 0, viewportHeight: 800))
        XCTAssertTrue(WearIveRunFooterZone.isAtFooter(distanceToEnd: -40, viewportHeight: 800),
                      "resting past the end (rubber-band / shrunk content) is at the footer")
        // Content shorter than a viewport is at its end already.
        XCTAssertTrue(WearIveRunFooterZone.isAtFooter(
            distanceToEnd: WearIveRunFooterZone.distanceToEnd(
                contentHeight: 500, contentInsetsBottom: 0,
                containerHeight: 800, contentOffsetY: 0),
            viewportHeight: 800))
    }

    // MARK: - RB-01 · the R-1 decision, as a truth table in motion

    func testRB01_R1Policy_SummonFooterAndSuppression() {
        var policy = WearIveRunExpansionPolicy()
        XCTAssertFalse(policy.isExpanded, "a fresh surface stands at MINI")

        // Reaching the page end presents the expanded form.
        policy.scrollChanged(atFooter: true)
        XCTAssertTrue(policy.isExpanded, "at the footer the form presents")
        // Scrolling away re-docks it to MINI.
        policy.scrollChanged(atFooter: false)
        XCTAssertFalse(policy.isExpanded, "leaving the footer zone re-docks to MINI")

        // A tap summon persists through scroll travel — into
        // the footer zone and back out — until explicit collapse.
        policy.userToggled()
        XCTAssertTrue(policy.isExpanded, "the tap summons")
        policy.scrollChanged(atFooter: true)
        policy.scrollChanged(atFooter: false)
        XCTAssertTrue(policy.isExpanded,
                      "a tap-summoned expansion persists until an explicit collapse (R-1)")
        policy.userToggled()
        XCTAssertFalse(policy.isExpanded, "the second toggle is the explicit collapse")
        XCTAssertFalse(policy.footerSuppressed,
                       "a collapse away from the footer sets no suppression")

        // Collapse WHILE at the footer suppresses re-presentation
        // until the scroll leaves the zone.
        policy.scrollChanged(atFooter: true)
        XCTAssertTrue(policy.isExpanded, "the footer presents again on the next arrival")
        policy.userCollapsed()
        XCTAssertFalse(policy.isExpanded, "the chevron collapse at the footer collapses")
        XCTAssertTrue(policy.footerSuppressed)
        policy.scrollChanged(atFooter: true)
        XCTAssertFalse(policy.isExpanded,
                       "still standing in the zone, the suppressed footer does not re-present")
        policy.scrollChanged(atFooter: false)
        XCTAssertFalse(policy.footerSuppressed, "leaving the zone clears the suppression")
        policy.scrollChanged(atFooter: true)
        XCTAssertTrue(policy.isExpanded, "the next arrival presents again")

        // A tap while suppressed summons anyway; collapsing
        // that summon at the footer re-suppresses.
        policy.userCollapsed()
        XCTAssertFalse(policy.isExpanded)
        policy.userToggled()
        XCTAssertTrue(policy.isExpanded, "a tap summons even while the footer is suppressed")
        policy.userCollapsed()
        XCTAssertFalse(policy.isExpanded)
        XCTAssertTrue(policy.footerSuppressed)
    }

    func testRB01_HostWiring_SourcePins() throws {
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(host.contains("WearIveRunFooterZone.distanceToEnd"),
                      "the host derives the remaining distance from the scroll's real geometry")
        XCTAssertTrue(host.contains("WearIveRunFooterZone.isAtFooter"),
                      "the host judges the footer zone from that geometry")
        XCTAssertTrue(host.contains("isAtFooter: wearAtFooter"),
                      "the host feeds the verdict to the overlay surface")
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("expansionPolicy.userToggled()"),
                      "the overlay's toggle runs the R-1 policy")
        XCTAssertTrue(bar.contains("expansionPolicy.scrollChanged(atFooter: newValue)"),
                      "the overlay feeds footer-zone changes to the R-1 policy")
        XCTAssertTrue(bar.contains("expansionPolicy.userCollapsed()"),
                      "the glide-up dismissal runs the R-1 explicit collapse")
    }

    // MARK: - RB-02 · one toggle control, one action (harness level)

    func testRB02_ToggleControlAndBody_SameAction_HarnessLevel() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let unifiedStart = try XCTUnwrap(bar.range(of: "struct WearIveRunUnifiedSurfaceView"))
        let unifiedEnd = try XCTUnwrap(bar.range(
            of: "// MARK: - RW-152 §1 / RW-169 §7A", range: unifiedStart.upperBound..<bar.endIndex))
        let unified = bar[unifiedStart.lowerBound..<unifiedEnd.lowerBound]
        // Both production call sites — the body tap layer and
        // the toggle control — fire the ONE handler, and the
        // handler fires the toggle callback exactly once.
        // (Two `handleToggleTap()` call sites + its definition.)
        XCTAssertEqual(
            unified.components(separatedBy: "handleToggleTap()").count - 1, 3,
            "body layer and toggle control share the one handleToggleTap action")
        XCTAssertEqual(
            unified.components(separatedBy: "onToggle()").count - 1, 1,
            "handleToggleTap fires onToggle exactly once — no double-toggle path")
        // The stacked recognizer that made the icon read as
        // dead (a root onTapGesture over the Button) is gone
        // from the unified surface.
        XCTAssertFalse(unified.contains(".onTapGesture"),
                       "no parent tap gesture competes with the toggle Button")
        // The toggle control is ONE button in ONE 26 pt box
        // whose two glyphs cross-fade with the progress.
        XCTAssertTrue(unified.contains(".frame(width: 26, height: 26)"),
                      "the toggle glyph box is 26 pt (RB-02/RB-04)")
        XCTAssertTrue(unified.contains("Image(systemName: \"chevron.down\")"),
                      "the collapse glyph cross-fades inside the same box")
        XCTAssertTrue(unified.contains("Image(systemName: \"arrow.up.left.and.arrow.down.right\")"),
                      "the expand glyph cross-fades inside the same box")

        // Harness-level activation proof: the production
        // decision function both call sites reach
        // (OverlayView.toggle → policy.userToggled) toggles
        // the state on EVERY activation, from either source —
        // icon activation and body activation are the same
        // call, so they cannot diverge. (Device tap-through
        // remains UNPROVEN — no device in this lane.)
        var policy = WearIveRunExpansionPolicy()
        policy.userToggled() // the icon control's activation
        XCTAssertTrue(policy.isExpanded, "activating the icon control expands")
        policy.userToggled() // the body tap's activation
        XCTAssertFalse(policy.isExpanded, "activating the body collapses — the same action, toggled")
    }

    // MARK: - RB-03 · the morph — RW-170: ONE narration, moved not cross-faded

    func testRB03_MorphWindows_LegibilityInvariant() throws {
        // RW-170 law (supersedes the RB-03 two-placement
        // windows this test pinned before): the narration is
        // ONE view — its font lerps 12 → 15 pt, its slot
        // migrates beside-wordmark → below-header, and its
        // opacity is 1 at EVERY progress value. Exactly one
        // legible narration at every sample — by construction,
        // not by disjoint windows.
        XCTAssertEqual(WearIveRunMorph.narrationFontSize(progress: 0), 12, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.narrationFontSize(progress: 0.5), 13.5, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.narrationFontSize(progress: 1), 15, accuracy: 0.0001)
        for i in 0...100 {
            let p = Double(i) / 100
            XCTAssertEqual(WearIveRunMorph.narrationOpacity(progress: p), 1, accuracy: 0.0001,
                           "p=\(p): the one narration never dissolves — it moves")
            XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1,
                           "p=\(p): exactly one legible narration")
        }
        // Source level: the unified surface composes the
        // narration ONCE — the two placement views are gone.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let unifiedStart = try XCTUnwrap(bar.range(of: "struct WearIveRunUnifiedSurfaceView"))
        let unifiedEnd = try XCTUnwrap(bar.range(
            of: "// MARK: - RW-152 §1 / RW-169 §7A", range: unifiedStart.upperBound..<bar.endIndex))
        let unified = bar[unifiedStart.lowerBound..<unifiedEnd.lowerBound]
        XCTAssertEqual(unified.components(separatedBy: "narrationView(line)").count - 1, 1,
                       "exactly one narration composition in the unified surface")
        XCTAssertFalse(unified.contains("miniNarrationView"),
                       "the MINI placement is retired (RW-170)")
        XCTAssertFalse(unified.contains("expandedNarrationView"),
                       "the EXPANDED placement is retired (RW-170)")
        // Expanded-only content dissolves in over [0.45, 1],
        // monotonically.
        XCTAssertEqual(WearIveRunMorph.expandedOnlyOpacity(progress: 0.45), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedOnlyOpacity(progress: 1), 1, accuracy: 0.0001)
        var previous = -1.0
        for i in 0...100 {
            let alpha = WearIveRunMorph.expandedOnlyOpacity(progress: Double(i) / 100)
            XCTAssertGreaterThanOrEqual(alpha, previous, "the expanded-only dissolve never runs backwards")
            previous = alpha
        }
        // The motion token: 380 ms, cubic-bezier(0.32, 0.72, 0, 1);
        // Reduced Motion is an opacity crossfade ≤150 ms.
        XCTAssertEqual(WearIveRunMorph.morphDurationSeconds, 0.38, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.morphCurve.0, 0.32, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.morphCurve.1, 0.72, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.morphCurve.2, 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.morphCurve.3, 1, accuracy: 0.0001)
        XCTAssertLessThanOrEqual(WearIveRunMorph.reducedMotionDurationSeconds, 0.15)
    }

    // MARK: - RB-04 · the room — MINI ≈49 pt, EXPANDED == content

    private func miamiEntry() -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Calendar.current.startOfDay(for: Date()),
            placeName: "Miami, FL",
            feelsLikeF: 82, actualF: 85, condition: "Clear sky",
            itemNames: kcItems, itemKeys: kcItems.map { _ in "ITEM" },
            heroImageReference: "", sessionID: "s-rw135")
    }

    private var miamiState: WearIveRunState {
        .updated(payload: .placeAtMoment("Miami, FL", .now))
    }

    @MainActor
    func testRB04_MiniRestHeight_MeasuredFootprint() throws {
        try registerGreatVibes()
        // The formula level: 11 + 26 + 12 = 49, in the
        // brief's 47–52 band, and DockResidence carries it.
        XCTAssertEqual(
            WearIveRunUnifiedSurfaceView.miniTopPadding
                + WearIveRunUnifiedSurfaceView.miniRowHeight
                + WearIveRunUnifiedSurfaceView.miniBottomPadding,
            49, accuracy: 0.001)
        XCTAssertEqual(DockResidence.measuredDockHeight, 49, accuracy: 0.001)

        // The layout level (natural size, the RW-145 fitting
        // method): the retained MINI form with a one-line
        // narration ("Updated to Miami, FL") measures ≈49 pt
        // tall — the brief's 47–52 band at natural size.
        let mini = WearIveRunMiniBarView(
            state: miamiState,
            entry: miamiEntry(), formattedFeelsLike: "82°F",
            cueCount: 0, onOpenCollection: {}, onTrash: {})
        let renderer = ImageRenderer(content: mini)
        renderer.scale = 3
        renderer.proposedSize = ProposedViewSize(width: 369, height: nil)
        let miniHeight = try XCTUnwrap(renderer.uiImage?.size.height, "MINI fitting render failed")
        let probeDir = artefactsDirectory()
        try FileManager.default.createDirectory(at: probeDir, withIntermediateDirectories: true)
        try "RW135 RB-04 probe: miniBarFittingHeight=\(miniHeight)\n"
            .write(to: probeDir.appendingPathComponent("RW135-rb04-minibar-probe.txt"),
                   atomically: true, encoding: .utf8)
        XCTAssertGreaterThanOrEqual(miniHeight, 47,
                                    "MINI rest height \(miniHeight) is at the measured footprint's floor")
        XCTAssertLessThanOrEqual(miniHeight, 52,
                                 "MINI rest height \(miniHeight) ≈ 49 pt — no empty room (RB-04)")
    }

    @MainActor
    func testRB04_OverlayRestHeight_MeasuredFootprint() throws {
        try registerGreatVibes()
        // The container level, measured the way the RW-169
        // lane measures this surface: a SETTLED render on a
        // FIXED canvas in the production placement — NOT a
        // fitting-size render. (Run 3's probe proved why: the
        // fitting-size pass feeds the surface degenerate
        // widths during sizing, its geometry reads land
        // polluted, and the returned height was 94.67 pt for
        // a surface whose real rest height is 49 — the
        // harness limitation RW-169 documented; in
        // production the surface only ever renders at a real
        // width.) The painted dock's span is then measured
        // off the PNG's centre column. The backdrop is a
        // SOLID DARK ground: in production the dock always
        // stands over the hero photo, never over the plain
        // page — and over the cream page the light material
        // sits within a few code points of the ground in
        // every channel (run 4's probe read a 14.5 pt span
        // on a visually perfect 49 pt dock, because only the
        // text and shadow rows separate there). Over the
        // dark ground the light card differs by ~200.
        let overlay = WearIveRunOverlayView(
            state: miamiState,
            entry: miamiEntry(), formattedFeelsLike: "82°F",
            cueCount: 0, viewportHeight: 852,
            onOpenCollection: {}, onTrash: {})
        let data = try writeRenderSettled(
            ZStack {
                Color(hex: "2E2A24")
                VStack {
                    Spacer()
                    overlay.padding(.horizontal, 12)
                }
                .padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW135-rb04-overlay-probe.png")
        let image = try XCTUnwrap(UIImage(data: data)?.cgImage, "overlay probe PNG undecodable")
        let width = image.width, heightPx = image.height
        let cfData = try XCTUnwrap(image.dataProvider?.data, "overlay probe pixels unreadable")
        let bytes = cfData as Data
        let bytesPerRow = image.bytesPerRow
        func centrePixel(_ y: Int) -> (Int, Int, Int) {
            let x = width / 2
            let offset = y * bytesPerRow + x * 4
            return (Int(bytes[offset]), Int(bytes[offset + 1]), Int(bytes[offset + 2]))
        }
        // The backdrop ground, sampled well above the dock.
        let ground = centrePixel(100)
        func differsFromGround(_ y: Int) -> Bool {
            let px = centrePixel(y)
            return max(abs(px.0 - ground.0), abs(px.1 - ground.1), abs(px.2 - ground.2)) > 8
        }
        var cardRows = 0
        var y = heightPx - 1
        var trailingGround = 0
        while y >= 0 {
            if differsFromGround(y) { cardRows += 1; trailingGround = 0 }
            else {
                trailingGround += 1
                // Below the dock: the 14 pt bottom padding of
                // ground rows comes FIRST (scanning upward) —
                // only stop once card rows have been seen and
                // a sustained ground run follows above the
                // card's top edge.
                if cardRows > 0 && trailingGround > 12 { break }
            }
            y -= 1
        }
        // Scale 2 → points = rows / 2. The phase-3 gate
        // re-measured this render independently (Nov 12,
        // 2026): the painted span is exactly the card —
        // 49.0 pt at difference thresholds 8/20/40/80, the
        // top-most and bottom-most card rows identical at
        // every threshold, i.e. the soft shadow fringe
        // contributes 0 pt beyond the card on this render.
        // The band below is therefore set on the card
        // itself: floor 45 admits render noise; ceiling 55
        // EXCLUDES the old ≈60 pt card this probe exists
        // to catch (a 60 pt card paints a ≈60 pt span and
        // must fail). An earlier widening of the ceiling
        // to 62 on a shadow-fringe rationale was refuted
        // by that measurement and is reverted here.
        let spanPoints = Double(cardRows) / 2.0
        let probeDir = artefactsDirectory()
        try FileManager.default.createDirectory(at: probeDir, withIntermediateDirectories: true)
        try "RW135 RB-04 probe: overlayPaintedSpanPoints=\(spanPoints) cardRows=\(cardRows)\n"
            .write(to: probeDir.appendingPathComponent("RW135-rb04-overlay-probe.txt"),
                   atomically: true, encoding: .utf8)
        XCTAssertGreaterThanOrEqual(spanPoints, 45,
                                    "the overlay's painted MINI dock stands at the measured footprint (span \(spanPoints) pt)")
        XCTAssertLessThanOrEqual(spanPoints, 55,
                                 "the overlay's painted MINI dock is ≈49 pt of card, not the old ≈60 pt card (span \(spanPoints) pt)")
    }

    func testRB04_ExpandedHeight_EqualsContentFormula() {
        // The unified surface's expanded rest formula and the
        // overlay policy's content height agree on the
        // one-line anatomy (narration 20 pt at 15 pt type,
        // entry at the 56 pt thumbnail floor): the estimate
        // the container uses before the first expansion IS
        // the surface's own rest height — rendered height ==
        // measured content height at rest.
        let fromSurface = WearIveRunUnifiedSurfaceView.expandedTopPadding
            + WearIveRunUnifiedSurfaceView.expandedHeaderZone
            + 20 + 12 + 56 + 12 + 24
            + WearIveRunUnifiedSurfaceView.expandedBottomPadding
        let fromPolicy = WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 1, entryLineCount: 1, textScale: 1)
        XCTAssertEqual(fromSurface, fromPolicy, accuracy: 0.001,
                       "surface formula and policy formula are the same content height")
        XCTAssertEqual(fromPolicy, 212, accuracy: 0.001,
                       "top 20 + header 44 + spacing 12 + narration 20 + spacing 12 + entry 56 + spacing 12 + doorway 24 + bottom 12")
        // The expanded paddings of record.
        XCTAssertEqual(WearIveRunUnifiedSurfaceView.expandedTopPadding, 20, accuracy: 0.001)
        XCTAssertEqual(WearIveRunUnifiedSurfaceView.expandedBottomPadding, 12, accuracy: 0.001,
                       "the A′ seat's larger bottom padding does not carry into the overlay (RB-04)")
    }

    // MARK: - RB-05 · constant corner radius

    func testRB05_CornerRadius_NeverAnimates() {
        for p in [0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0] {
            XCTAssertEqual(WearIveRunMorph.cornerRadius(progress: p), 16, accuracy: 0.0001,
                           "p=\(p): the radius is a constant 16 pt at every morph progress")
        }
        XCTAssertEqual(WearIveRunMorph.miniCornerRadius,
                       WearIveRunMorph.expandedCornerRadius, accuracy: 0.0001,
                       "the two tokens are equal — there is nothing to interpolate")
    }

    // MARK: - RB-09 · the pill is not this surface's to touch

    func testRB09_PillSeatAndVisibility_UntouchedByWearSurface() throws {
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // The pill's state derivation: from the personal cell
        // presentation and HeroPill.resolve ONLY — no Wear
        // I've Run state, expansion, or scroll input.
        let pillStart = try XCTUnwrap(host.range(of: "private var heroPillState"))
        let pillEnd = try XCTUnwrap(host.range(
            of: "private var currentCellLookIsFailed", range: pillStart.upperBound..<host.endIndex))
        let pill = host[pillStart.lowerBound..<pillEnd.lowerBound]
        XCTAssertFalse(pill.lowercased().contains("wear"),
                       "heroPillState derives from the cell presentation alone — the Wear surface is not an input")
        XCTAssertFalse(pill.contains("expansion"),
                       "the pill's visibility never keys on the Wear surface's expansion")
        // The straddle pill renders exactly when that state is
        // not hidden — in MINI rest, mid-scroll, and EXPANDED
        // alike, because nothing in its composition reads the
        // Wear surface at all.
        XCTAssertTrue(host.contains("if heroPillState != .hidden"),
                      "the straddle pill's only gate is its own state")
        // And the Wear overlay's own composition carries no
        // pill reference: it cannot remove or bisect it.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let overlayStart = try XCTUnwrap(bar.range(of: "struct WearIveRunOverlayView"))
        let overlay = bar[overlayStart.lowerBound..<bar.endIndex]
        XCTAssertFalse(overlay.contains("heroPillState"),
                       "the Wear overlay never reads the pill's state")
    }

    // MARK: - Audit sweep · the §4.7 exit split

    func testAuditSweep_ExitPolicy_FormSplit() {
        // From MINI: 300 ms; opacity is the fade (1 − q), the
        // sink runs 0 → 26 pt, and there is NO height fold.
        XCTAssertEqual(WearIveRunExitPolicy.durationSeconds(
            fromExpanded: false, reduceMotion: false), 0.30, accuracy: 0.0001)
        for q in [0.0, 0.25, 0.5, 0.75, 1.0] {
            XCTAssertEqual(WearIveRunExitPolicy.opacity(
                progress: q, fromExpanded: false, reduceMotion: false), 1 - q, accuracy: 0.0001)
            XCTAssertEqual(WearIveRunExitPolicy.sink(
                progress: q, fromExpanded: false, reduceMotion: false), 26 * q, accuracy: 0.0001)
            XCTAssertEqual(WearIveRunExitPolicy.heightScale(
                progress: q, fromExpanded: false, reduceMotion: false), 1, accuracy: 0.0001,
                "from MINI the height is released at removal, never folded")
        }
        // From EXPANDED: 560 ms total; the fade completes
        // inside the first 160 ms (q = 160/560) while the
        // height still stands; only then does the fold run.
        XCTAssertEqual(WearIveRunExitPolicy.durationSeconds(
            fromExpanded: true, reduceMotion: false), 0.56, accuracy: 0.0001)
        let fadeEnd = 160.0 / 560.0
        XCTAssertEqual(WearIveRunExitPolicy.opacity(
            progress: fadeEnd, fromExpanded: true, reduceMotion: false), 0, accuracy: 0.0001,
            "the expanded content is fully faded at 160 ms")
        XCTAssertEqual(WearIveRunExitPolicy.heightScale(
            progress: fadeEnd, fromExpanded: true, reduceMotion: false), 1, accuracy: 0.0001,
            "the fold has not started while the fade runs")
        XCTAssertEqual(WearIveRunExitPolicy.heightScale(
            progress: (1 + fadeEnd) / 2, fromExpanded: true, reduceMotion: false), 0.5, accuracy: 0.0001,
            "the fold's midpoint halves the height")
        XCTAssertEqual(WearIveRunExitPolicy.heightScale(
            progress: 1, fromExpanded: true, reduceMotion: false), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunExitPolicy.sink(
            progress: 0.5, fromExpanded: true, reduceMotion: false), 0, accuracy: 0.0001,
            "no sink from the expanded form")
        // Reduced Motion: a 200 ms fade — no sink, no fold.
        XCTAssertEqual(WearIveRunExitPolicy.durationSeconds(
            fromExpanded: true, reduceMotion: true), 0.20, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunExitPolicy.sink(
            progress: 0.5, fromExpanded: false, reduceMotion: true), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunExitPolicy.heightScale(
            progress: 0.5, fromExpanded: true, reduceMotion: true), 1, accuracy: 0.0001)
    }

    // MARK: - Captures · the morph at five progress values (Kansas City)

    @MainActor
    func testRB135_Captures_MorphSamples_KansasCity() throws {
        try registerGreatVibes()
        let entry = kcEntry()
        let state = kcState
        let line = try XCTUnwrap(WearIveRunNarration.line(for: state))
        XCTAssertEqual(line, "Updated to Kansas City, MO, Now")
        let entryLine = WearIveRunNarration.expandedLine(for: entry, formattedFeelsLike: "37°F")
        XCTAssertEqual(entryLine,
                       "Kansas City, MO · Today — 37°F feels like · Ear Warmer / Headband · Light Long Sleeve · Light Tights · Light Gloves · Reflective Gear")

        // The two REST heights from the PRODUCTION formulas
        // over text heights measured at the fixture (the
        // RW-169 capture method): MINI from the measured
        // footprint; EXPANDED as the surface's own formula.
        let surfaceWidth: CGFloat = 369
        let wordmarkW = textWidth(
            WearIveRunMiniBarModel.wordmark,
            font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17)))
        let miniColumn = surfaceWidth - (16 + wordmarkW + 8) - 16 - 56 - 4
        let miniNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 12), width: miniColumn)
        let miniH = WearIveRunUnifiedSurfaceView.miniTopPadding
            + max(WearIveRunUnifiedSurfaceView.miniRowHeight, miniNarrH)
            + WearIveRunUnifiedSurfaceView.miniBottomPadding
        XCTAssertGreaterThanOrEqual(miniH, 47)
        XCTAssertLessThanOrEqual(miniH, 58,
                                    "the capture frames MINI at its measured rest height (\(miniH)) — 49 pt if the KC line fits one line, taller only by the wrapped line's own height (growth is content, §4.3)")
        let expNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 15), width: surfaceWidth - 32)
        let entryH = measuredTextHeight(entryLine, font: .systemFont(ofSize: 14.5), width: surfaceWidth - 32 - 56 - 12)
        let expandedH = WearIveRunUnifiedSurfaceView.expandedTopPadding
            + WearIveRunUnifiedSurfaceView.expandedHeaderZone
            + expNarrH + 12 + entryH + 12 + 24
            + WearIveRunUnifiedSurfaceView.expandedBottomPadding

        // Five renders driven through the REAL
        // expansionProgress value — not two rest states
        // relabelled. Each must differ from every other.
        var hashes: [String: String] = [:]
        for (p, label) in [(0.0, "p0"), (0.25, "p25"), (0.5, "p50"), (0.75, "p75"), (1.0, "p100")] {
            let h = miniH + (expandedH - miniH) * CGFloat(p)
            let data = try writeRenderSettled(
                ZStack {
                    AppTheme.background
                    VStack {
                        Spacer()
                        WearIveRunUnifiedSurfaceView(
                            state: state, entry: entry, formattedFeelsLike: "37°F",
                            cueCount: 0, viewportHeight: 852,
                            heroURLResolver: nil, expansionProgress: p,
                            onToggle: {}, onOpenCollection: {},
                            onOpenShareEntry: nil, onTrash: {})
                            .frame(width: surfaceWidth, height: h)
                    }
                    .padding(.horizontal, 12).padding(.bottom, 14)
                },
                size: CGSize(width: 393, height: 852),
                name: "RW135-ios-morph-\(label).png")
            hashes[label] = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        }
        XCTAssertEqual(Set(hashes.values).count, 5,
                       "all five morph samples are pairwise distinct renders: \(hashes)")
    }

    // MARK: - RW-170 · ONE SURFACE / ONE BAND / ONE SHADOW / ONE NARRATION / NO RE-FLOW
    //
    // The structural fix for Sol's Nov 12 device-pass failure
    // (DIAGNOSIS Q1–Q4), iOS leg. Probes are COMPOSITED (the
    // surface over a real page composition, light AND dark) —
    // never surface-only — per the diagnosis's rendered-
    // evidence standard. Each probe's discrimination is proven
    // on a scratch branch (see the lane report).

    // MARK: RW-170 · Move 1 — one token, alpha only

    func testRW170_SurfaceToken_OneTokenAlphaOnly() throws {
        // Sol's ruling: the unified token is the EXPANDED
        // state's darker warm tone in BOTH states — light
        // #F5F1E8 (the page-ground family), dark #2A251E (the
        // warm-charcoal family). The token function takes no
        // progress input: there is nothing to cross-fade.
        XCTAssertEqual(WearIveRunMorph.unifiedSurfaceHex(isDark: false), "F5F1E8")
        XCTAssertEqual(WearIveRunMorph.unifiedSurfaceHex(isDark: false), AppTheme.groundLightHex)
        XCTAssertEqual(WearIveRunMorph.unifiedSurfaceHex(isDark: true), "2A251E")
        XCTAssertEqual(WearIveRunMorph.unifiedSurfaceHex(isDark: true), AppTheme.cardDarkHex)
        // The only background animation is ALPHA: the mini
        // rest alpha (0.85 light / 0.82 dark) → 1.00 expanded.
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: false), 0.85, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: true), 0.82, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 1, isDark: false), 1.0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 1, isDark: true), 1.0, accuracy: 0.0001)
        // Source level: no surface in this file still paints
        // the superseded sheet token — every Wear I've Run
        // background resolves through the one token function.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertFalse(bar.contains("sheetLightHex"),
                       "the superseded MINI token (#FDFBF6) is retired from the Wear surfaces")
        XCTAssertGreaterThanOrEqual(
            bar.components(separatedBy: "unifiedSurfaceHex(").count - 1, 3,
            "mini form, expanded form and the unified surface all paint the ruled token")
    }

    // MARK: RW-170 · Move 2 — one band (verify-and-price the presented form)

    func testRW170_Band_PresentedFormPricing() throws {
        // At the one-line rest the reserve IS the presented
        // form: 49 pt card + 14 pt lift = 63 pt, exactly.
        XCTAssertEqual(DockResidence.measuredDockHeight, 49, accuracy: 0.001)
        XCTAssertEqual(DockResidence.bottomInset, 14, accuracy: 0.001)
        XCTAssertEqual(DockResidence.scrollEndClearance, 63, accuracy: 0.001,
                       "the reserve prices the presented form — no empty band above it")
        XCTAssertEqual(DockResidence.clearance(forPresentedMiniHeight: 49), 63, accuracy: 0.001)
        // A wrapped narration grows the presented height and
        // the reserve follows it, point for point.
        XCTAssertEqual(DockResidence.clearance(forPresentedMiniHeight: 65), 79, accuracy: 0.001)
        // The host's two clearance sites consume the SAME
        // presented-form quantity (the reported mini height),
        // and neither consults the old constant any more.
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertEqual(
            host.components(separatedBy: "forPresentedMiniHeight: wearPresentedMiniHeight").count - 1, 2,
            "stacked sheet and Duo column both reserve the presented-form clearance")
        XCTAssertFalse(host.contains("DockResidence.scrollEndClearance"),
                       "no host site reserves the constant band any more")
        // The overlay reports the PRESENTED (mini) height to
        // the host — never the expanded height.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("onPresentedMiniHeight?(mini)"),
                      "the clearance input is the mini rest height only")
    }

    // MARK: RW-170 · Move 3 — one shadow (verify-and-confirm)

    func testRW170_Shadow_SpecExpression_Verified() throws {
        // The iOS spec §2.1 expression, already of record and
        // confirmed by the RW-170 audit: black 14%, radius 12
        // (SwiftUI's radius is half the spec's 24 pt blur),
        // offset (0, +6), constant across progress — the SAME
        // expression in the unified surface as in the two
        // reference forms. No elevation model exists in
        // SwiftUI to mistake it for.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertEqual(
            bar.components(separatedBy: ".shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 6)").count - 1, 3,
            "mini form, expanded form and unified surface carry the one spec shadow")
        let unifiedStart = try XCTUnwrap(bar.range(of: "struct WearIveRunUnifiedSurfaceView"))
        let unifiedEnd = try XCTUnwrap(bar.range(
            of: "// MARK: - RW-152 §1 / RW-169 §7A", range: unifiedStart.upperBound..<bar.endIndex))
        let unified = bar[unifiedStart.lowerBound..<unifiedEnd.lowerBound]
        XCTAssertFalse(unified.contains("radius: 24"),
                       "no doubled-radius shadow crept into the unified surface")
    }

    // MARK: RW-170 · Move 5 — no re-flow

    func testRW170_NoReflow_HostCompositionNotGatedOnExpansion() throws {
        // The diagnosis's Android Q3 cause 3 (in-list items
        // evicted when the expanded state presents) has NO
        // iOS equivalent: the host's expanded-state record
        // (`wearSurfaceExpanded`) feeds ONLY the §4.7 exit
        // split, and no scroll-content composition in the
        // host is gated on it. Source-pinned here so the
        // equivalent can never arrive silently.
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(host.contains("if wearSurfaceExpanded"),
                       "no host composition is gated on the expanded state")
        XCTAssertFalse(host.contains("wearSurfaceExpanded ?"),
                       "no host composition branches on the expanded state")
        // The scroll content's sections stand unconditionally
        // in the sheet composition (their own inputs aside).
        for section in ["homeBandSection", "dayChartBand", "proTipSection", "personalSection"] {
            XCTAssertTrue(host.contains(section), "the sheet still composes \(section)")
        }
    }

    // MARK: RW-170 · composited probes — helpers

    /// The KC fixture's two REST heights from the production
    /// formulas over measured text (the captures method).
    @MainActor
    private func rw170RestHeights() throws -> (mini: CGFloat, expanded: CGFloat) {
        let entry = kcEntry()
        let line = try XCTUnwrap(WearIveRunNarration.line(for: kcState))
        let entryLine = WearIveRunNarration.expandedLine(for: entry, formattedFeelsLike: "37°F")
        let surfaceWidth: CGFloat = 369
        let wordmarkW = textWidth(
            WearIveRunMiniBarModel.wordmark,
            font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17)))
        let miniColumn = surfaceWidth - (16 + wordmarkW + 8) - 16 - 56 - 4
        let miniNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 12), width: miniColumn)
        let miniH = WearIveRunUnifiedSurfaceView.miniTopPadding
            + max(WearIveRunUnifiedSurfaceView.miniRowHeight, miniNarrH)
            + WearIveRunUnifiedSurfaceView.miniBottomPadding
        let expNarrH = measuredTextHeight(line, font: .systemFont(ofSize: 15), width: surfaceWidth - 32)
        let entryH = measuredTextHeight(entryLine, font: .systemFont(ofSize: 14.5), width: surfaceWidth - 32 - 56 - 12)
        let expandedH = WearIveRunUnifiedSurfaceView.expandedTopPadding
            + WearIveRunUnifiedSurfaceView.expandedHeaderZone
            + expNarrH + 12 + entryH + 12 + 24
            + WearIveRunUnifiedSurfaceView.expandedBottomPadding
        return (miniH, expandedH)
    }

    @MainActor
    private func rw170Surface(progress: Double, height: CGFloat) -> some View {
        WearIveRunUnifiedSurfaceView(
            state: kcState, entry: kcEntry(), formattedFeelsLike: "37°F",
            cueCount: 0, viewportHeight: 852,
            heroURLResolver: nil, expansionProgress: progress,
            onToggle: {}, onOpenCollection: {},
            onOpenShareEntry: nil, onTrash: {})
            .frame(width: 369, height: height)
    }

    private func rw170Card(dark: Bool, title: String, height: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 12)
                .fill(dark ? Color(hex: "211D17") : Color(hex: "FDFBF6"))
            Text(title)
                .font(.system(size: 13))
                .foregroundColor(dark ? Color(hex: "F5F1EA") : Color(hex: "33291F"))
                .padding(12)
        }
        .frame(height: height)
    }

    /// A pixel reader over a rendered PNG (scale 2 renders:
    /// pixel coordinates are points × 2, rows top-first).
    private func rw170Pixels(_ data: Data) throws -> (pixel: (Int, Int) -> (Int, Int, Int), width: Int, height: Int) {
        let image = try XCTUnwrap(UIImage(data: data)?.cgImage, "render PNG undecodable")
        let cfData = try XCTUnwrap(image.dataProvider?.data, "render pixels unreadable")
        let bytes = cfData as Data
        let bpr = image.bytesPerRow
        let pixel: (Int, Int) -> (Int, Int, Int) = { x, y in
            let o = y * bpr + x * 4
            return (Int(bytes[o]), Int(bytes[o + 1]), Int(bytes[o + 2]))
        }
        return (pixel, image.width, image.height)
    }

    private func rw170WriteProbe(_ text: String, name: String) throws {
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try text.write(to: dir.appendingPathComponent(name), atomically: true, encoding: .utf8)
        print(text)
    }

    // MARK: RW-170 · composited full-home captures + probes (b), (c), (d)

    @MainActor
    func testRW170_Captures_CompositedHome_LightDark() throws {
        try registerGreatVibes()
        let rests = try rw170RestHeights()
        var hashes: [String: String] = [:]
        // (scheme, label, progress, scroll shift of the page content)
        let states: [(Bool, String, Double, CGFloat)] = [
            (false, "mini-rest", 0, 0), (false, "mid-scroll", 0, 120),
            (false, "scroll-end", 0, 260), (false, "mid-morph", 0.5, 260),
            (false, "expanded", 1, 260),
            (true, "mini-rest", 0, 0), (true, "mid-scroll", 0, 120),
            (true, "scroll-end", 0, 260), (true, "mid-morph", 0.5, 260),
            (true, "expanded", 1, 260),
        ]
        for (dark, label, progress, shift) in states {
            let h = rests.mini + (rests.expanded - rests.mini) * CGFloat(progress)
            let data = try writeRenderSettled(
                ZStack {
                    dark ? Color(hex: "16130F") : Color(hex: "F5F1E8")
                    VStack(alignment: .leading, spacing: 16) {
                        rw170Card(dark: dark, title: "Outfit cards", height: 150)
                        rw170Card(dark: dark, title: "Day chart", height: 110)
                        rw170Card(dark: dark, title: "Pro Tip", height: 90)
                        rw170Card(dark: dark, title: "Personal", height: 80)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16).padding(.top, 140)
                    .offset(y: -shift)
                    VStack {
                        Spacer()
                        rw170Surface(progress: progress, height: h)
                    }
                    .padding(.horizontal, 12).padding(.bottom, 14)
                }
                .environment(\.colorScheme, dark ? .dark : .light),
                size: CGSize(width: 393, height: 852),
                name: "RW170-ios-home-\(dark ? "dark" : "light")-\(label).png")
            hashes["\(dark ? "dark" : "light")-\(label)"] =
                SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        }
        // (d) landmark stability: every composited state is a
        // distinct render of the ONE surface over the page —
        // no state is another state relabelled.
        XCTAssertEqual(Set(hashes.values).count, hashes.count,
                       "all ten composited states are pairwise distinct: \(hashes)")

        // (c) shadow spill, measured on the LIGHT mini-rest
        // composite (canvas 393×852 @2x; the card's painted
        // rect is x 12…381 pt, y 789…838 pt at the one-line
        // rest — bottom lift 14, height 49 by the RB-04 law;
        // the KC fixture's presented height is rests.mini,
        // so the rect is computed from it).
        let light = try rw170Pixels(try Data(contentsOf:
            artefactsDirectory().appendingPathComponent("RW170-ios-home-light-mini-rest.png")))
        let ground = (245, 241, 232)
        func darkening(_ px: (Int, Int, Int)) -> Double {
            Double((ground.0 - px.0) + (ground.1 - px.1) + (ground.2 - px.2)) / 3.0
        }
        let cardTop = 852 - 14 - Int(rests.mini)
        let cardBottom = 852 - 14
        // Bounded scan: a spill that reaches the canvas edge
        // stops AT the edge (an earlier unbounded form ran
        // off the pixel buffer and crashed the probe).
        func spill(from start: Int, step: Int, limit: Int, sample: (Int) -> (Int, Int, Int)) -> Int {
            var count = 0
            var v = start
            while count < 40 && (step > 0 ? v <= limit : v >= limit) {
                if darkening(sample(v)) > 6 { count += 1; v += step } else { break }
            }
            return count
        }
        // Centre column x=196 pt (px 392), scanning outward
        // (each step is 2 px = 1 pt, so the count IS points).
        let aboveSpill = spill(from: (cardTop - 1) * 2, step: -2, limit: 0) { y in light.pixel(392, y) }
        let belowSpill = spill(from: (cardBottom + 1) * 2, step: 2, limit: light.height - 1) { y in light.pixel(392, y) }
        let midY = (cardTop + cardBottom) / 2 * 2
        let leftSpill = spill(from: 11 * 2, step: -2, limit: 0) { x in light.pixel(x, midY) }
        let rightSpill = spill(from: 382 * 2, step: 2, limit: light.width - 1) { x in light.pixel(x, midY) }
        try rw170WriteProbe(
            "RW170 shadow spill (light, mini rest, pt): above=\(aboveSpill) below=\(belowSpill) left=\(leftSpill) right=\(rightSpill)\n",
            name: "RW170-probe-shadow-spill.txt")
        XCTAssertGreaterThan(belowSpill, aboveSpill,
                             "the §2.1 shadow pools BELOW the card (offset +6): below \(belowSpill) > above \(aboveSpill)")
        XCTAssertGreaterThanOrEqual(belowSpill, 4,
                                    "a real shadow pool exists below the card (\(belowSpill) pt)")
        XCTAssertLessThanOrEqual(aboveSpill, 26, "no halo above the card (\(aboveSpill) pt)")
        XCTAssertLessThanOrEqual(leftSpill, 26, "no halo left of the card (\(leftSpill) pt)")
        XCTAssertLessThanOrEqual(rightSpill, 26, "no halo right of the card (\(rightSpill) pt)")
    }

    // MARK: RW-170 · probe (a) painted bounds vs reserved clearance

    @MainActor
    func testRW170_Probe_PaintedVsReserved() throws {
        try registerGreatVibes()
        // The painted MINI span over a dark backdrop (the
        // RB-04 method), one-line Miami fixture.
        let overlay = WearIveRunOverlayView(
            state: miamiState,
            entry: miamiEntry(), formattedFeelsLike: "82°F",
            cueCount: 0, viewportHeight: 852,
            onOpenCollection: {}, onTrash: {})
        let data = try writeRenderSettled(
            ZStack {
                Color(hex: "2E2A24")
                VStack {
                    Spacer()
                    overlay.padding(.horizontal, 12)
                }
                .padding(.bottom, 14)
            },
            size: CGSize(width: 393, height: 852),
            name: "RW170-probe-painted.png")
        let reader = try rw170Pixels(data)
        let ground = reader.pixel(reader.width / 2, 100)
        func differs(_ y: Int) -> Bool {
            let px = reader.pixel(reader.width / 2, y)
            return max(abs(px.0 - ground.0), abs(px.1 - ground.1), abs(px.2 - ground.2)) > 8
        }
        var cardRows = 0
        var y = reader.height - 1
        var trailing = 0
        while y >= 0 {
            if differs(y) { cardRows += 1; trailing = 0 }
            else {
                trailing += 1
                if cardRows > 0 && trailing > 12 { break }
            }
            y -= 1
        }
        let painted = Double(cardRows) / 2.0
        let reserved = Double(DockResidence.scrollEndClearance)
        try rw170WriteProbe(
            "RW170 painted-vs-reserved: paintedSpan=\(painted) lift=\(DockResidence.bottomInset) reserved=\(reserved) excess=\(reserved - painted - Double(DockResidence.bottomInset))\n",
            name: "RW170-probe-painted-reserved.txt")
        XCTAssertGreaterThanOrEqual(painted, 45)
        XCTAssertLessThanOrEqual(painted, 55)
        XCTAssertEqual(reserved - painted - Double(DockResidence.bottomInset), 0, accuracy: 0.6,
                       "reserved − (painted + lift) = 0: the band prices the presented form exactly (was +17 pt of empty band at the 80 pt constant)")
        // Wrapped growth follows: the Los Angeles plan line
        // wraps in the mini column, the presented height
        // grows, and the clearance grows point for point.
        let wordmarkW = textWidth(
            WearIveRunMiniBarModel.wordmark,
            font: try XCTUnwrap(UIFont(name: "GreatVibes-Regular", size: 17)))
        let miniColumn = 369 - (16 + wordmarkW + 8) - 16 - 56 - 4
        let laLine = "Planned for Los Angeles, CA · Tomorrow"
        let laNarrH = measuredTextHeight(laLine, font: .systemFont(ofSize: 12), width: miniColumn)
        let wrappedMini = WearIveRunUnifiedSurfaceView.miniTopPadding
            + max(WearIveRunUnifiedSurfaceView.miniRowHeight, laNarrH)
            + WearIveRunUnifiedSurfaceView.miniBottomPadding
        XCTAssertGreaterThan(wrappedMini, 49,
                             "the plan line wraps and the presented form grows (\(wrappedMini) pt)")
        XCTAssertEqual(
            DockResidence.clearance(forPresentedMiniHeight: wrappedMini),
            wrappedMini + 14, accuracy: 0.001,
            "the reserve follows the wrapped presented height exactly")
    }

    // MARK: RW-170 · probe (b) cross-state interior RGB identity

    @MainActor
    func testRW170_Probe_CrossStateRGBIdentity() throws {
        try registerGreatVibes()
        let rests = try rw170RestHeights()
        var report = "RW170 cross-state interior RGB (sampled at the surface's bottom-centre interior, over the token-coloured ground):\n"
        for dark in [false, true] {
            let tokenHex = WearIveRunMorph.unifiedSurfaceHex(isDark: dark)
            var samples: [Double: (Int, Int, Int)] = [:]
            for progress in [0.0, 0.5, 1.0] {
                let h = rests.mini + (rests.expanded - rests.mini) * CGFloat(progress)
                let data = try writeRenderSettled(
                    ZStack {
                        Color(hex: tokenHex)
                        VStack {
                            Spacer()
                            rw170Surface(progress: progress, height: h)
                        }
                        .padding(.horizontal, 12).padding(.bottom, 14)
                    }
                    .environment(\.colorScheme, dark ? .dark : .light),
                    size: CGSize(width: 393, height: 852),
                    name: "RW170-probe-rgb-\(dark ? "dark" : "light")-p\(Int(progress * 100)).png")
                let reader = try rw170Pixels(data)
                // Bottom-centre interior: 5 pt above the
                // surface's bottom edge, centred — inside the
                // surface at every progress, clear of text.
                let px = reader.pixel(196 * 2, (852 - 14 - 5) * 2)
                samples[progress] = px
                report += "\(dark ? "dark" : "light") p=\(progress): (\(px.0), \(px.1), \(px.2))\n"
            }
            let s0 = try XCTUnwrap(samples[0.0])
            let s5 = try XCTUnwrap(samples[0.5])
            let s1 = try XCTUnwrap(samples[1.0])
            func maxDiff(_ a: (Int, Int, Int), _ b: (Int, Int, Int)) -> Int {
                max(abs(a.0 - b.0), abs(a.1 - b.1), abs(a.2 - b.2))
            }
            report += "\(dark ? "dark" : "light") maxDiff p0-p1=\(maxDiff(s0, s1)) p0-p0.5=\(maxDiff(s0, s5))\n"
            XCTAssertLessThanOrEqual(maxDiff(s0, s1), 18,
                                     "\(dark ? "dark" : "light"): ONE token across states — interior differs only by the alpha blend over the token's own ground (p0 \(s0) vs p1 \(s1))")
            XCTAssertLessThanOrEqual(maxDiff(s0, s5), 18,
                                     "\(dark ? "dark" : "light"): mid-morph interior is the same token (p0.5 \(s5))")
        }
        try rw170WriteProbe(report, name: "RW170-probe-rgb-identity.txt")
    }

    // MARK: RW-170 · Move 3 check — the shadow under the travel clip (finding probe)

    @MainActor
    func testRW170_Probe_ShadowTravelClip() throws {
        try registerGreatVibes()
        let rests = try rw170RestHeights()
        let h = rests.mini + (rests.expanded - rests.mini) * 0.5
        // Mid-morph surface seated at the bottom of a short
        // canvas over the light ground, rendered twice: as
        // the surface paints itself (its own clip + shadow),
        // and with the overlay container's travel clip applied
        // (the production clippedIfMorphing form during
        // travel: a rounded-rect clip at the container's
        // bounds). The finding is measured, not assumed.
        func render(clipped: Bool, name: String) throws -> Data {
            let surface = rw170Surface(progress: 0.5, height: h)
            return try writeRenderSettled(
                ZStack {
                    Color(hex: "F5F1E8")
                    VStack {
                        Spacer()
                        if clipped {
                            surface.clipShape(RoundedRectangle(cornerRadius: 16))
                        } else {
                            surface
                        }
                    }
                    .padding(.horizontal, 12).padding(.bottom, 14)
                }
                .environment(\.colorScheme, .light),
                size: CGSize(width: 393, height: 400),
                name: name)
        }
        func belowSpill(_ data: Data) throws -> Int {
            let reader = try rw170Pixels(data)
            let ground = (245, 241, 232)
            // The surface's bottom edge: canvas 400 − lift 14.
            var count = 0
            var yy = (400 - 14 + 1) * 2
            while count < 40 && yy <= reader.height - 1 {
                let px = reader.pixel(196 * 2, yy)
                let darkening = Double((ground.0 - px.0) + (ground.1 - px.1) + (ground.2 - px.2)) / 3.0
                if darkening > 6 { count += 1; yy += 2 } else { break }
            }
            return count // each step is 2 px = 1 pt
        }
        let unclipped = try belowSpill(render(clipped: false, name: "RW170-probe-shadow-travel-unclipped.png"))
        let clipped = try belowSpill(render(clipped: true, name: "RW170-probe-shadow-travel-clipped.png"))
        try rw170WriteProbe(
            "RW170 travel-clip finding: mid-morph below-spill unclipped=\(unclipped) pt, container-clipped=\(clipped) pt. At REST the container does not clip (clippedIfMorphing is travel-only) and the full §2.1 shadow stands; DURING travel the container's rounded-rect clip bounds the shadow's spill outside the surface's frame.\n",
            name: "RW170-probe-travel-clip.txt")
        XCTAssertGreaterThanOrEqual(unclipped, 4,
                                    "the surface's own shadow pools below it mid-morph (\(unclipped) pt)")
        XCTAssertLessThanOrEqual(clipped, unclipped,
                                 "the travel clip can only bound the spill, never add to it")
    }

    // MARK: RW-170 · probe (d) one-surface / one-narration node counts

    func testRW170_Probe_NodeCounts() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let overlayStart = try XCTUnwrap(bar.range(of: "struct WearIveRunOverlayView"))
        let overlay = bar[overlayStart.lowerBound..<bar.endIndex]
        XCTAssertEqual(
            overlay.components(separatedBy: "WearIveRunUnifiedSurfaceView(").count - 1, 1,
            "the container composes exactly ONE surface")
        XCTAssertFalse(overlay.contains("WearIveRunMiniBarView("),
                       "no second (mini) composition in the container")
        XCTAssertFalse(overlay.contains("WearIveRunExpandedPanelView("),
                       "no second (expanded) composition in the container")
        // One narration, one wordmark, across the whole morph.
        for i in 0...20 {
            let p = Double(i) / 20
            XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1,
                           "p=\(p): exactly one legible narration")
            XCTAssertEqual(WearIveRunMorph.narrationOpacity(progress: p), 1, accuracy: 0.0001)
        }
        try rw170WriteProbe(
            "RW170 node counts: surfacesInContainer=1 narrationCompositions=1 legibleNarrations=1 at every sampled progress (0…1 step 0.05)\n",
            name: "RW170-probe-nodes.txt")
    }

    // MARK: RW-173 — the pill seat vs the expanded title (code-level geometry)

    /// RW-173 (Nov 13, 2026): does the Make It Me straddle
    /// pill reach the expanded panel's script title at home
    /// rest on iOS? The capture harness does not render the
    /// pill, so the verdict is arithmetic from the real
    /// placement constants:
    ///   pill bottom (at rest) = HeroHeightPolicy.stackedHeight(V)
    ///       + HeroVerticalLayout.straddleDip (14 below hero foot)
    ///   panel bottom          = V − DockResidence.bottomInset (14)
    ///   panel height          = min(content, WearIveRunOverlayPolicy cap)
    ///   title top             = panel top + expandedTopPadding (20)
    /// with the §2 content anatomy (WearIveRunOverlayPolicy
    /// .contentHeight): narration 1 line; entry 3 lines =
    /// 219 pt, entry 4 lines = 240 pt (the Kansas City
    /// fixture's entry wraps four lines in the panel).
    ///
    /// VERDICT on the pre-fix constants: the title CAN be
    /// reached. Standard 852/844 pt clears both anatomies
    /// (by 7.6/5.2 pt at 240), but 812 pt is crossed by the
    /// four-line entry (pill 582.4 vs title 578.0) and the
    /// short class is crossed outright — 667 pt: pill 454.2
    /// vs title 454.0 (three-line entry) and 433.0
    /// (four-line). The panel therefore yields on iOS as on
    /// Android: WearIveRunOverlayPolicy.effectiveCap binds
    /// the expanded height at the pill-title cap while the
    /// pill is in its seat, and this test pins both the
    /// pre-fix reachability (the discriminator) and the
    /// post-fix clearance across the grid.
    func testRW173_PillGeometry_TitleReachability() throws {
        func pillBottom(viewport v: CGFloat, scrollOffset: CGFloat = 0) -> CGFloat {
            HeroHeightPolicy.stackedHeight(viewportHeight: v)
                + HeroVerticalLayout.straddleDip - scrollOffset
        }
        func titleTop(
            viewport v: CGFloat, content: CGFloat, cap: CGFloat
        ) -> CGFloat {
            let panel = min(content, cap)
            return v - DockResidence.bottomInset - panel
                + WearIveRunOverlayPolicy.expandedTopPadding
        }
        let content3 = WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 1, entryLineCount: 3, textScale: 1)
        let content4 = WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 1, entryLineCount: 4, textScale: 1)
        XCTAssertEqual(content3, 219, accuracy: 0.001)
        XCTAssertEqual(content4, 240, accuracy: 0.001)

        // — Pre-fix reachability (the §2 cap alone): the
        // standard tall class clears; 812 (four-line) and
        // the short class are crossed. These equalities are
        // the measured geometry of record; if a constant of
        // record changes, they must be re-derived, not edited
        // to fit.
        let chrome = { (v: CGFloat) in
            WearIveRunOverlayPolicy.expandedCap(viewportHeight: v)
        }
        XCTAssertGreaterThan(
            titleTop(viewport: 852, content: content4, cap: chrome(852)),
            pillBottom(viewport: 852),
            "852 pt, four-line entry: title clears the pill at rest")
        XCTAssertLessThan(
            titleTop(viewport: 812, content: content4, cap: chrome(812)),
            pillBottom(viewport: 812),
            "812 pt, four-line entry: the pill REACHES the title (582.4 vs 578.0)")
        XCTAssertLessThan(
            titleTop(viewport: 667, content: content3, cap: chrome(667)),
            pillBottom(viewport: 667),
            "667 pt, three-line entry: the pill REACHES the title (454.2 vs 454.0)")
        XCTAssertLessThan(
            titleTop(viewport: 667, content: content4, cap: chrome(667)),
            pillBottom(viewport: 667),
            "667 pt, four-line entry: the pill REACHES the title (454.2 vs 433.0)")

        // — Post-fix: with the pill-title cap in force, the
        // title clears the pill's bottom at every viewport
        /// class, both anatomies, and every scroll offset
        /// (scrolling only moves the pill UP, relaxing it).
        for v in [667, 736, 812, 844, 852, 932] as [CGFloat] {
            for content in [content3, content4] {
                for offset in [0, 25, 120, 400] as [CGFloat] {
                    let pb = pillBottom(viewport: v, scrollOffset: offset)
                    let cap = WearIveRunOverlayPolicy.effectiveCap(
                        viewportHeight: v, pillBottomY: pb)
                    let top = titleTop(viewport: v, content: content, cap: cap)
                    XCTAssertGreaterThanOrEqual(
                        top, pb - 0.001,
                        "V=\(v) content=\(content) offset=\(offset): " +
                            "title top \(top) must clear pill bottom \(pb)")
                }
            }
        }
        // No pill in the seat → the §2 cap stands untouched.
        XCTAssertEqual(
            WearIveRunOverlayPolicy.effectiveCap(viewportHeight: 852, pillBottomY: nil),
            WearIveRunOverlayPolicy.expandedCap(viewportHeight: 852),
            accuracy: 0.001)
        // A pill scrolled far above the panel relaxes the
        // cap back to the §2 cap exactly.
        XCTAssertEqual(
            WearIveRunOverlayPolicy.effectiveCap(
                viewportHeight: 852,
                pillBottomY: pillBottom(viewport: 852, scrollOffset: 10000)),
            WearIveRunOverlayPolicy.expandedCap(viewportHeight: 852),
            accuracy: 0.001)

        // — Source pins: the host places the pill's bottom
        // from the same constants and hands it to the dock;
        // the container caps through the one effective cap
        // and forwards it to the surface's entry budget.
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(host.contains("pillBottomY: pillBottomY"),
                      "the host hands the pill's bottom edge to the dock")
        XCTAssertTrue(host.contains("HeroVerticalLayout.straddleDip - homeScrollOffset"),
                      "the pill's bottom is placed from the stacked constants and the live scroll offset")
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("WearIveRunOverlayPolicy.effectiveCap("),
                      "the container caps the expanded height through the effective cap")
        XCTAssertTrue(bar.contains("expandedCapOverride: pillBottomY == nil ? nil : effectiveCap"),
                      "the surface's entry-region budget reads the same effective cap")
    }
}
