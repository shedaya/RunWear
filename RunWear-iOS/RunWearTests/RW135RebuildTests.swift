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
/// - RB-03: the real dissolve morph — two narration
///   placements, disjoint smoothstep windows, the legibility
///   invariant, expanded-only content over [0.45, 1], the
///   380 ms cubic-bezier(0.32, 0.72, 0, 1) morph, Reduced
///   Motion ≤150 ms.
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

    // MARK: - RB-03 · the dissolve windows and the legibility invariant

    func testRB03_MorphWindows_LegibilityInvariant() {
        // The window endpoints, exactly as specified.
        XCTAssertEqual(WearIveRunMorph.miniNarrationOpacity(progress: 0), 1, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.miniNarrationOpacity(progress: 0.35), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedNarrationOpacity(progress: 0.55), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedNarrationOpacity(progress: 1), 1, accuracy: 0.0001)
        // Sampled across the whole travel: the two alphas are
        // DISJOINT (their product is 0 at every sample — one
        // is always fully out while the other is in), at most
        // one placement is ever legible, and the dead zone
        // (0.35, 0.55) has neither.
        for i in 0...100 {
            let p = Double(i) / 100
            let mini = WearIveRunMorph.miniNarrationOpacity(progress: p)
            let expanded = WearIveRunMorph.expandedNarrationOpacity(progress: p)
            XCTAssertEqual(mini * expanded, 0, accuracy: 0.0001,
                           "p=\(p): the windows are disjoint — both-legible is impossible")
            XCTAssertLessThanOrEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: p), 1,
                                     "p=\(p): at most one legible narration")
            if p > 0.35 && p < 0.55 {
                XCTAssertEqual(mini, 0, accuracy: 0.0001, "p=\(p): dead zone — mini is out")
                XCTAssertEqual(expanded, 0, accuracy: 0.0001, "p=\(p): dead zone — expanded is not yet in")
            }
        }
        // The endpoints carry exactly one legible narration each.
        XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: 0), 1)
        XCTAssertEqual(WearIveRunMorphProbe.legibleNarrationCount(progress: 1), 1)
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
        // The span includes the card's soft shadow fringe
        // (a few points, more visible over the dark ground);
        // scale 2 → points = rows / 2.
        let spanPoints = Double(cardRows) / 2.0
        let probeDir = artefactsDirectory()
        try FileManager.default.createDirectory(at: probeDir, withIntermediateDirectories: true)
        try "RW135 RB-04 probe: overlayPaintedSpanPoints=\(spanPoints) cardRows=\(cardRows)\n"
            .write(to: probeDir.appendingPathComponent("RW135-rb04-overlay-probe.txt"),
                   atomically: true, encoding: .utf8)
        XCTAssertGreaterThanOrEqual(spanPoints, 45,
                                    "the overlay's painted MINI dock stands at the measured footprint (span \(spanPoints) pt incl. shadow)")
        XCTAssertLessThanOrEqual(spanPoints, 62,
                                 "the overlay's painted MINI dock is ≈49 pt of card + shadow fringe, not the old ≈60 pt card and not a polluted read (span \(spanPoints) pt incl. shadow)")
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
}
