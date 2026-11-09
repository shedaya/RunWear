import XCTest
import SwiftUI
import UIKit

/// RW-145 / RW-146 / RW-147 (fix pass, Nov 9, 2026) — the iOS
/// parity lane for Sol's Android device-pass findings.
///
/// - RW-145: HeroVerticalLayout — the chip↔feels-like-block gap
///   guarantee at every hero height.
/// - RW-146: the displayed location truth — sheet marking and
///   the place pill derive from the COMMITTED place/source, and
///   the commit flips place + conditions together.
/// - RW-147: DockResidence + HeroHeightPolicy — no readable
///   content under the dock at rest (first open + scroll end),
///   in the stacked layouts and the Duo column alike.
///
/// Render half: production components composed as production
/// composes them, over a REAL runner photograph (the bundled
/// hero pool's mild_clear_male — a local image file, not the
/// RW-144 synthetic wash), written into `render-artefacts/`.
final class RW145147FixTests: XCTestCase {

    // MARK: - RW-145: the gap guarantee

    func testRW145GapGuaranteeAcrossHeroHeights() {
        // Stacked heroes (44 pt foot pad) from the policy floor
        // to a tall Duo-class pane, and Duo panes (12 pt pad).
        for heroHeight in [242.0, 260, 300, 365, 383, 430, 517] {
            for pad in [44.0, 12.0] {
                let layout = HeroVerticalLayout.layout(
                    heroHeight: heroHeight, requestedNumeralSize: 72, bottomPadding: pad)
                XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5,
                    "hero \(heroHeight) pad \(pad): gap \(layout.gap) must hold the 12 pt guarantee")
                // Face zone: the chip's VISUAL top clears the
                // zone's foot by 2 pt.
                let visualTop = layout.chipHitTop + HeroVerticalLayout.chipVisualTopInset
                XCTAssertGreaterThanOrEqual(visualTop, heroHeight * 0.30 + 2 - 0.5,
                    "hero \(heroHeight) pad \(pad): chip visual top \(visualTop) enters the face zone")
                XCTAssertGreaterThanOrEqual(layout.chipHitTop, HeroVerticalLayout.minimumChipHitTop - 0.5)
                XCTAssertLessThanOrEqual(layout.numeralSize, 72)
                XCTAssertGreaterThanOrEqual(layout.numeralSize, HeroVerticalLayout.minimumNumeralSize)
            }
        }
    }

    func testRW145StandardHeroKeepsTheFullNumeral() {
        // The 852 pt fixture's hero (383 pt): the numeral stays
        // 72 and the chip moves UP from the RW-144 seat (0.46 of
        // the hero = 176) to the feasible seat that buys the
        // guaranteed gap — the RW-144 seat's own gap was ≈3 pt.
        let layout = HeroVerticalLayout.layout(
            heroHeight: 383, requestedNumeralSize: 72, bottomPadding: 44)
        XCTAssertEqual(layout.numeralSize, 72, accuracy: 0.5)
        XCTAssertGreaterThanOrEqual(layout.gap, 12 - 0.5)
        XCTAssertLessThan(layout.chipHitTop, 383 * HeroChipPlacement.defaultTopFraction)
        // The pre-fix geometry, for the record: block top at
        // 383 − 44 − blockHeight(72); the old chip's visual
        // bottom at 0.46×383 + 36 — a ≈3 pt gap (hit frames
        // already overlapping).
        let oldGap = (383 - 44 - HeroVerticalLayout.blockHeight(numeralSize: 72))
            - (383 * HeroChipPlacement.defaultTopFraction + HeroVerticalLayout.chipVisualBottomFromHitTop)
        XCTAssertLessThan(oldGap, HeroVerticalLayout.minimumGap,
                          "the RW-144 seat must fail the guarantee this item sets (it measured ≈3 pt)")
    }

    func testRW145ShortHeroShrinksTheNumeralInsteadOfColliding() {
        // iPhone SE class: hero 249 (the RW-147 policy height at
        // a 667 pt viewport) — the layout shrinks the numeral
        // rather than letting the chip ride into the block.
        let layout = HeroVerticalLayout.layout(
            heroHeight: 249, requestedNumeralSize: 72, bottomPadding: 44)
        XCTAssertLessThan(layout.numeralSize, 72)
        XCTAssertGreaterThanOrEqual(layout.gap, 12 - 0.5)
        // Duo phone-landscape pane (242 pt, 12 pt pad).
        let duo = HeroVerticalLayout.layout(
            heroHeight: 242, requestedNumeralSize: 72, bottomPadding: 12)
        XCTAssertGreaterThanOrEqual(duo.gap, 12 - 0.5)
    }

    func testRW145BlockHeightIsMonotonicInNumeralSize() {
        var last = -1.0
        for size in stride(from: 36.0, through: 96.0, by: 12) {
            let h = HeroVerticalLayout.blockHeight(numeralSize: size)
            XCTAssertGreaterThan(h, last)
            last = h
        }
    }

    // MARK: - RW-147: hero height policy + dock residence

    func testRW147PolicyHeights() {
        // Standard height, glance present, no band: the hero
        // gives up exactly what the glance band needs to rest
        // clear of the dock band (852 − 54 − (364 + 138) = 296).
        XCTAssertEqual(HeroHeightPolicy.height(viewportHeight: 852, bandPresent: false, glancePresent: true),
                       296, accuracy: 0.5)
        // Standard height without the glance band: untouched.
        XCTAssertEqual(HeroHeightPolicy.height(viewportHeight: 852, bandPresent: false, glancePresent: false),
                       852 * AppTheme.heroHeightRatio, accuracy: 0.5)
        // SE height: the outfit stack clears (hero 249); the
        // glance band is carried by the scroll clearance there.
        XCTAssertEqual(HeroHeightPolicy.height(viewportHeight: 667, bandPresent: false, glancePresent: true),
                       249, accuracy: 0.5)
        // Short landscape viewport: the hero never drops below
        // the RW-145 composition floor.
        XCTAssertEqual(HeroHeightPolicy.height(viewportHeight: 390, bandPresent: false, glancePresent: true),
                       HeroHeightPolicy.minimumHeroHeight, accuracy: 0.5)
        // The outfit-clearance claim at the policy's own heights:
        // row 3's bottom (hero − overlap + outfit stack) rests at
        // or above dockTop − restClearance.
        for (viewport, band) in [(852.0, false), (736.0, false), (667.0, false), (852.0, true)] {
            let hero = HeroHeightPolicy.height(viewportHeight: viewport, bandPresent: band, glancePresent: true)
            let row3Bottom = hero - HomeHandoffGeometry.sheetOverlap
                + HeroHeightPolicy.outfitBottomFromSheetTop(bandPresent: band)
            let dockTop = DockResidence.dockTop(viewportHeight: viewport, dockHeight: DockResidence.measuredDockHeight)
            XCTAssertLessThanOrEqual(row3Bottom, dockTop - HeroHeightPolicy.restClearance + 0.5,
                "viewport \(viewport) band \(band): row 3 bottom \(row3Bottom) vs dock top \(dockTop)")
        }
    }

    func testRW147DockResidenceArithmetic() {
        XCTAssertEqual(DockResidence.dockTop(viewportHeight: 852, dockHeight: 56), 782, accuracy: 0.5)
        XCTAssertEqual(DockResidence.footerTopContentY(contentHeight: 2000, footerHeight: 200),
                       2000 - DockResidence.scrollEndClearance - 200, accuracy: 0.5)
        XCTAssertEqual(DockResidence.scrollEndClearance, 80, accuracy: 0.5)
    }

    // MARK: - RW-146: displayed truth

    override func setUp() {
        super.setUp()
        ManualLocationStore.clear()
    }

    override func tearDown() {
        ManualLocationStore.clear()
        super.tearDown()
    }

    @MainActor
    func testRW146DisplayedTruthStartsAsTheLaunchState() {
        let gpsVM = WeatherViewModel()
        XCTAssertTrue(gpsVM.displayedSourceIsGPS)
        XCTAssertEqual(gpsVM.displayedPlaceName, gpsVM.placeDisplayName)

        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let manualVM = WeatherViewModel()
        XCTAssertFalse(manualVM.displayedSourceIsGPS)
        XCTAssertEqual(manualVM.displayedPlaceName, "Phoenix, AZ")
    }

    @MainActor
    func testRW146PendingSwitchDoesNotMoveTheDisplayedTruth() {
        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let vm = WeatherViewModel()
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")

        // The tap: the SELECTION flips immediately…
        vm.switchToGPS()
        XCTAssertTrue(vm.isUsingGPS)
        // …but the DISPLAYED truth — what the sheet marks and
        // the pill names — still states the place on screen
        // until a GPS fetch commits. (The Android RW-146 (a)
        // defect was the marking following the tap-time flag.)
        XCTAssertFalse(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")

        // The commit flips place + source together.
        vm.commitDisplayedPlace(
            sourceIsGPS: true,
            geocodedPlace: SimplePlace(city: "New York", region: "New York"),
            pendingManualPlace: nil)
        XCTAssertTrue(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "New York, NY")
        XCTAssertEqual(vm.displayedPlaceName, vm.placeDisplayName,
                       "after the commit the displayed and pending names coincide")
    }

    @MainActor
    func testRW146ManualCommitKeepsThePickedPlaceWhenTheGeocodeFails() {
        let vm = WeatherViewModel()
        vm.commitDisplayedPlace(
            sourceIsGPS: false,
            geocodedPlace: nil,
            pendingManualPlace: SimplePlace(city: "Phoenix", region: "Arizona"))
        XCTAssertFalse(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")
        // A GPS commit with no geocode leaves the non-place label.
        vm.commitDisplayedPlace(sourceIsGPS: true, geocodedPlace: nil, pendingManualPlace: nil)
        XCTAssertTrue(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Current Location")
    }

    func testRW146SheetMarkingHasExactlyOneTruth() {
        // GPS displayed: the GPS row is marked, no recent is.
        XCTAssertTrue(LocationSheetMarking.gpsRowIsCurrent(displayedSourceIsGPS: true))
        XCTAssertFalse(LocationSheetMarking.recentIsCurrent(
            recentName: "Phoenix, AZ", displayedSourceIsGPS: true, displayedPlaceName: nil))
        // Manual Phoenix displayed: the GPS row is unmarked and
        // exactly the Phoenix recent is marked.
        XCTAssertFalse(LocationSheetMarking.gpsRowIsCurrent(displayedSourceIsGPS: false))
        XCTAssertTrue(LocationSheetMarking.recentIsCurrent(
            recentName: "Phoenix, AZ", displayedSourceIsGPS: false, displayedPlaceName: "Phoenix, AZ"))
        XCTAssertFalse(LocationSheetMarking.recentIsCurrent(
            recentName: "Seattle, WA", displayedSourceIsGPS: false, displayedPlaceName: "Phoenix, AZ"))
    }

    // MARK: - Render harness

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 3
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW145147 wrote \(name)")
    }

    @MainActor
    private func fittingHeight<Content: View>(_ content: Content, width: CGFloat) -> CGFloat {
        let renderer = ImageRenderer(content: content)
        renderer.scale = 3
        renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        return renderer.uiImage?.size.height ?? 0
    }

    /// The REAL runner photograph: the bundled hero pool's
    /// mild_clear_male (800×1200 WebP), loaded from its local
    /// file — the workaround for the harness's remote-thumbnail
    /// limitation, and a visible runner subject for the chip's
    /// face-zone seat (the RW-144 synthetic wash had neither).
    private func realRunnerPhoto() throws -> UIImage {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../RunWear/hero_fallback/mild_clear_male.webp")
            .standardizedFileURL
        let image = try XCTUnwrap(UIImage(contentsOfFile: url.path),
                                  "the bundled real runner photo must decode in the harness")
        XCTAssertGreaterThan(image.size.width, 100)
        return image
    }

    private func fixtureItems() -> [ClothingItem] {
        [
            ClothingItem(key: "SHORT_SLEEVE", name: "Short Sleeve Shirt",
                         description: "Moisture-wicking technical fabric",
                         icon: "tshirt", amazonSearchTerm: "short sleeve running shirt"),
            ClothingItem(key: "SHORTS", name: "Running Shorts (5-7\u{2033})",
                         description: "Standard running shorts",
                         icon: "figure.run", amazonSearchTerm: "running shorts"),
            ClothingItem(key: "REFLECTIVE_GEAR", name: "Reflective Gear",
                         description: "Visibility for low-light",
                         icon: "exclamationmark.triangle", amazonSearchTerm: "reflective running gear"),
        ]
    }

    private func fixtureEntry() -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_798_000_000),
            placeName: "Phoenix, AZ", feelsLikeF: 58, actualF: 60, condition: "Clear sky",
            itemNames: ["Short Sleeve Shirt", "Running Shorts (5-7\u{2033})", "Reflective Gear"],
            itemKeys: ["SHORT_SLEEVE", "SHORTS", "REFLECTIVE_GEAR"],
            heroImageReference: "", windMph: 0, dayLowF: 55, dayHighF: 63,
            faceAnchor: nil)
    }

    private func heroWithRealPhoto(photo: UIImage, width: CGFloat, height: CGFloat, footPadding: CGFloat) -> some View {
        let top = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.topRegion)
        let chip = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.chipRegion)
        let foot = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.footRegion)
        print("RW145147 real-photo luminance top=\(String(format: "%.3f", top)) chip=\(String(format: "%.3f", chip)) foot=\(String(format: "%.3f", foot))")
        return ZStack {
            Image(uiImage: photo)
                .resizable()
                .scaledToFill()
                .frame(width: width, height: height)
                .clipped()
            HeroChromeOverlayView(
                placeName: "Phoenix, AZ",
                dateText: "Sun, Nov 8",
                timeText: "Now",
                isNow: true,
                formattedFeelsLike: "58°",
                formattedActual: "60°",
                feelsLikeF: 58,
                topLuminance: top, chipLuminance: chip, footLuminance: foot,
                onPlaceTapped: {}, onSettingsTapped: {},
                onDateTapped: {}, onTimeTapped: {}, onNowTapped: {},
                feelsBlockBottomPadding: footPadding
            )
        }
        .frame(width: width, height: height)
    }

    private func sheetStack() -> some View {
        VStack(spacing: 0) {
            BoxlessWeatherLineView(windMph: 0, rainPercent: 0, humidityPercent: 60, uvIndex: 0)
                .padding(.horizontal, 16)
                .padding(.top, 36)
            Rectangle()
                .fill(AppTheme.sheetHairline)
                .frame(height: 1)
                .padding(.horizontal, 16)
                .padding(.top, 16)
            HStack {
                Text("3 items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
            VStack(spacing: 16) {
                ForEach(fixtureItems()) { item in
                    FirstScreenOutfitRow(item: item, gender: .male)
                }
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }
        .background {
            UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(topLeading: 28, topTrailing: 28))
                .fill(AppTheme.sheet)
        }
        .overlay(alignment: .top) {
            HeroPillView(pillState: .exhausted(used: 10, allowance: 10), seat: .straddle, onPillGetUnlimited: {})
                .offset(y: -9)
        }
    }

    private func dock() -> WearIveRunMiniBarView {
        WearIveRunMiniBarView(
            state: .updated(payload: .place("Phoenix, AZ")),
            entry: fixtureEntry(),
            formattedFeelsLike: "58°F", cueCount: 0,
            onOpenCollection: {}, onTrash: {})
    }

    // MARK: - Frames

    /// (1) Short portrait (iPhone SE class, 375×667), Now,
    /// 3-item outfit at rest, real photo: the chip↔block gap
    /// and the outfit↔dock clearance both asserted from the
    /// production layout/policy values the frame is built with.
    @MainActor
    func testRW145147ShortPortraitFrame() throws {
        let viewport = CGSize(width: 375, height: 667)
        let heroHeight = HeroHeightPolicy.height(
            viewportHeight: viewport.height, bandPresent: false, glancePresent: true)
        let layout = HeroVerticalLayout.layout(
            heroHeight: heroHeight, requestedNumeralSize: 72, bottomPadding: 44)
        XCTAssertGreaterThanOrEqual(layout.gap, 12 - 0.5,
            "SE hero \(heroHeight): chip↔block gap \(layout.gap) (numeral \(layout.numeralSize))")

        let photo = try realRunnerPhoto()
        let dockHeight = fittingHeight(dock(), width: viewport.width - 24)
        let dockTop = DockResidence.dockTop(viewportHeight: viewport.height, dockHeight: dockHeight)
        let row3Bottom = heroHeight - HomeHandoffGeometry.sheetOverlap
            + HeroHeightPolicy.outfitBottomFromSheetTop(bandPresent: false)
        XCTAssertLessThanOrEqual(row3Bottom, dockTop - HeroHeightPolicy.restClearance + 0.5,
            "SE: row 3 bottom \(row3Bottom) must clear the dock top \(dockTop)")

        let screen = ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                heroWithRealPhoto(photo: photo, width: viewport.width, height: heroHeight, footPadding: 44)
                sheetStack()
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
                    .frame(maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-1-short-portrait-rest-light.png")
        print("RW147 short portrait: hero=\(heroHeight) numeral=\(layout.numeralSize) gap=\(layout.gap) dockTop=\(dockTop) row3Bottom=\(row3Bottom) clearance=\(dockTop - row3Bottom)")
    }

    /// (2) Standard phone height (393×852), Now, 3-item outfit
    /// + glance band at rest: the glance band's bottom — the
    /// RW-144 §10 flag — now rests clear of the dock too.
    @MainActor
    func testRW145147StandardPortraitFrame() throws {
        let viewport = CGSize(width: 393, height: 852)
        let heroHeight = HeroHeightPolicy.height(
            viewportHeight: viewport.height, bandPresent: false, glancePresent: true)
        let photo = try realRunnerPhoto()
        let dockHeight = fittingHeight(dock(), width: viewport.width - 24)
        let dockTop = DockResidence.dockTop(viewportHeight: viewport.height, dockHeight: dockHeight)
        let glanceBottom = heroHeight - HomeHandoffGeometry.sheetOverlap
            + HeroHeightPolicy.outfitBottomFromSheetTop(bandPresent: false)
            + HeroHeightPolicy.glanceStack
        XCTAssertLessThanOrEqual(glanceBottom, dockTop - HeroHeightPolicy.restClearance + 0.5,
            "852: glance bottom \(glanceBottom) must clear the dock top \(dockTop)")

        let screen = ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                heroWithRealPhoto(photo: photo, width: viewport.width, height: heroHeight, footPadding: 44)
                sheetStack()
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
                    .frame(maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-2-standard-portrait-rest-light.png")
        print("RW147 standard: hero=\(heroHeight) dockTop=\(dockTop) glanceBottom=\(glanceBottom) clearance=\(dockTop - glanceBottom)")
    }

    /// (3) Duo / expanded layout (1194×834): the Make It Me
    /// pill stands in-flow at the secondary column's head, the
    /// dock spans the full width at rest — and overlays only
    /// clear sheet/background in both panes.
    @MainActor
    func testRW145147DuoFrame() throws {
        let viewport = CGSize(width: 1194, height: 834)
        let heroHeight = viewport.height * 0.62
        let layout = HeroVerticalLayout.layout(
            heroHeight: heroHeight, requestedNumeralSize: 72, bottomPadding: 12)
        XCTAssertGreaterThanOrEqual(layout.gap, 12 - 0.5)

        let photo = try realRunnerPhoto()
        let dockHeight = fittingHeight(dock(), width: viewport.width - 24)
        let dockTop = DockResidence.dockTop(viewportHeight: viewport.height, dockHeight: dockHeight)

        let secondary = VStack(spacing: 0) {
            HeroPillView(pillState: .exhausted(used: 10, allowance: 10), seat: .straddle, onPillGetUnlimited: {})
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, 20)
            BoxlessWeatherLineView(windMph: 0, rainPercent: 0, humidityPercent: 60, uvIndex: 0)
                .padding(.horizontal, 16)
                .padding(.top, 20)
            Rectangle()
                .fill(AppTheme.sheetHairline)
                .frame(height: 1)
                .padding(.horizontal, 16)
                .padding(.top, 16)
            HStack {
                Text("3 items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
            VStack(spacing: 16) {
                ForEach(fixtureItems()) { item in
                    FirstScreenOutfitRow(item: item, gender: .male)
                }
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }

        let screen = ZStack(alignment: .bottom) {
            HStack(alignment: .top, spacing: 24) {
                heroWithRealPhoto(photo: photo, width: 550, height: heroHeight, footPadding: 12)
                secondary
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-3-duo-rest-light.png")
        print("RW147 Duo: hero=\(heroHeight) gap=\(layout.gap) dockTop=\(dockTop) (secondary stack ends at the outfit, ≈470 pt — clear sheet below)")
    }

    /// (4) RW-146 sheet sequence: the location sheet in the
    /// three states of a GPS switch — GPS displayed; manual
    /// Phoenix displayed; GPS requested but not yet committed
    /// (the displayed truth, and so the sheet, still states
    /// Phoenix). Frames (b) and (c) carry identical inputs by
    /// design: that identity IS the fix.
    @MainActor
    func testRW146SheetSequenceFrames() throws {
        // Seed the recents the sheet lists (Phoenix + Seattle).
        let store = RecentLocationsStore()
        store.record(RecentLocation(name: "Seattle, WA", latitude: 47.6062, longitude: -122.3321))
        store.record(RecentLocation(name: "Phoenix, AZ", latitude: 33.4484, longitude: -112.0740))
        defer {
            UserDefaults.standard.removeObject(forKey: "location.recents.v1")
        }

        // (a) GPS displayed.
        let gpsSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: true,
            currentPlaceName: nil,
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(gpsSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-a-sheet-gps-displayed.png")

        // (b) Manual Phoenix displayed.
        let manualSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: false,
            currentPlaceName: "Phoenix, AZ",
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(manualSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-b-sheet-manual-displayed.png")

        // (c) The switch tapped, not yet committed: drive the
        // sheet from a real view model's DISPLAYED truth after
        // switchToGPS() — the inputs are (b)'s, proven in
        // testRW146PendingSwitchDoesNotMoveTheDisplayedTruth.
        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let vm = WeatherViewModel()
        vm.switchToGPS()
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")
        let pendingSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: vm.displayedSourceIsGPS,
            currentPlaceName: vm.displayedSourceIsGPS ? nil : vm.displayedPlaceName,
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(pendingSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-c-sheet-switch-pending.png")
    }
}
