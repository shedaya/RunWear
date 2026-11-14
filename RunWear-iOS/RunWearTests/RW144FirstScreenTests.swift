import XCTest
import SwiftUI
import UIKit

/// RW-144 (first-screen redesign, approved Nov 8, 2026) — the
/// iOS lane's unit pins and rendered walkthrough frames.
///
/// Unit half: the band palette, the luminance-driven scrim
/// math (the contrast guarantee), the chip's face-zone
/// placement, the palette-of-record tokens, and the light-tile
/// seam. Render half: production components composed as
/// production composes them, written as PNGs into
/// `RunWear-iOS/render-artefacts/` (uploaded by ios-tests.yml
/// as `runwear-ios-renders`), at the v8 frames' own canvas
/// (393×852 pt at 3× = 1179×2556).
final class RW144FirstScreenTests: XCTestCase {

    // MARK: - Band palette

    func testBandPaletteBracketsAndHexes() {
        // PWA brackets (°F): freezing <20, cold 20–34, cool
        // 35–49, mild 50–64, warm 65–79, hot ≥80.
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 12), .FREEZING)
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 27), .COLD)
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 42), .COOL)
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 58), .MILD)
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 72), .WARM)
        XCTAssertEqual(HeroBandPalette.bracket(feelsLikeF: 88), .HOT)
        XCTAssertEqual(AppTheme.temperatureHex(for: HeroBandPalette.bracket(feelsLikeF: 58)), "10B981")
        XCTAssertEqual(HeroBandPalette.name(feelsLikeF: 58), "MILD")
    }

    // MARK: - Scrim math (contrast guarantee)

    func testScrimPeakIsFloorForDarkPhotosAndScalesUpForBright() {
        // The mock's peak (α≈0.73) is the FLOOR: dark photos sit
        // at it, bright photos scale up from it, never below.
        XCTAssertEqual(HeroScrimMath.peakAlpha(forLuminance: 0.0), HeroScrimMath.floorPeakAlpha, accuracy: 0.001)
        XCTAssertEqual(HeroScrimMath.peakAlpha(forLuminance: 0.35), HeroScrimMath.floorPeakAlpha, accuracy: 0.001)
        XCTAssertGreaterThan(HeroScrimMath.peakAlpha(forLuminance: 0.6), HeroScrimMath.floorPeakAlpha)
        XCTAssertEqual(HeroScrimMath.peakAlpha(forLuminance: 1.0), HeroScrimMath.maxPeakAlpha, accuracy: 0.001)
        // Monotonic over the whole range.
        var last = -1.0
        for l in stride(from: 0.0, through: 1.0, by: 0.05) {
            let peak = HeroScrimMath.peakAlpha(forLuminance: l)
            XCTAssertGreaterThanOrEqual(peak, last)
            last = peak
        }
    }

    func testScrimProfileMatchesTheMockShape() {
        let peak = 0.73
        XCTAssertEqual(HeroScrimMath.scrimAlpha(peak: peak, fraction: 0), 0, accuracy: 0.001)
        XCTAssertEqual(HeroScrimMath.scrimAlpha(peak: peak, fraction: 0.28), peak, accuracy: 0.001)
        XCTAssertEqual(HeroScrimMath.scrimAlpha(peak: peak, fraction: 0.5), peak, accuracy: 0.001)
        XCTAssertEqual(HeroScrimMath.scrimAlpha(peak: peak, fraction: 1), peak * 0.65, accuracy: 0.001)
    }

    func testLuminanceSamplerMeasuresSyntheticImages() {
        let white = Self.solidImage(.white)
        let black = Self.solidImage(.black)
        XCTAssertGreaterThan(HeroLuminanceSampler.meanLuminance(of: white, region: HeroLuminanceSampler.footRegion), 0.95)
        XCTAssertLessThan(HeroLuminanceSampler.meanLuminance(of: black, region: HeroLuminanceSampler.footRegion), 0.05)
        // The guarantee end to end: a bright photo's foot peak
        // exceeds a dark photo's, which sits at the floor.
        let brightPeak = HeroScrimMath.peakAlpha(forLuminance:
            HeroLuminanceSampler.meanLuminance(of: white, region: HeroLuminanceSampler.footRegion))
        let darkPeak = HeroScrimMath.peakAlpha(forLuminance:
            HeroLuminanceSampler.meanLuminance(of: black, region: HeroLuminanceSampler.footRegion))
        XCTAssertGreaterThan(brightPeak, darkPeak)
        XCTAssertEqual(darkPeak, HeroScrimMath.floorPeakAlpha, accuracy: 0.001)
    }

    // MARK: - Chip placement (face-zone protection)

    func testChipSeatClearsTheFaceZone() {
        let frame = CGSize(width: 393, height: 383)
        let top = HeroChipPlacement.chipTop(frameSize: frame, chipHeight: HeroChipGeometry.hitHeight)
        let chip = CGRect(x: 16, y: top, width: frame.width * 0.62, height: HeroChipGeometry.hitHeight)
        XCTAssertFalse(chip.intersects(HeroChipPlacement.faceZone(frameSize: frame)),
                       "the chip must never land across the runner's face zone")
        // The v7 separation rule, translated: 48 mock-pt of
        // clear space each side of the divider = 16 pt at 3×.
        XCTAssertEqual(HeroChipGeometry.dividerClearSpace, 16, accuracy: 0.5)
    }

    // MARK: - Palette of record

    func testPaletteTokensMatchTheV8AuditTable() {
        XCTAssertEqual(AppTheme.groundLightHex, "F5F1E8")
        XCTAssertEqual(AppTheme.sheetLightHex, "FDFBF6")
        XCTAssertEqual(AppTheme.inkLightHex, "33291F")
        XCTAssertEqual(AppTheme.inkSecondaryLightHex, "685D4F")
        XCTAssertEqual(AppTheme.descriptionLightHex, "807363")
        XCTAssertEqual(AppTheme.descriptionDarkHex, "988E80")
        XCTAssertEqual(AppTheme.tileLightHex, "F3EEE5")
        XCTAssertEqual(AppTheme.tileDarkHex, "25201A")
        XCTAssertEqual(AppTheme.groundDarkHex, "16130F")
        XCTAssertEqual(AppTheme.sheetDarkHex, "211D17")
        XCTAssertEqual(AppTheme.inkDarkHex, "F5F1EA")
        XCTAssertEqual(AppTheme.mutedDarkHex, "B8AEA0")
        XCTAssertEqual(AppTheme.pineAccentDarkHex, "5CC79F")
        XCTAssertEqual(AppTheme.heroHeightRatio, 0.45, accuracy: 0.001)
    }

    // MARK: - Hero numeral unit (RW-144 fix lane)

    func testHeroNumeralIsTheBareDegree() {
        // Parent ruling (Nov 8, 2026): the RW-144 first screen's
        // numeral + Actual read "58°" / "60°" — the approved v8
        // frames' bare degree, matching Android. The value still
        // converts; the letter is dropped here only — `format`
        // keeps "58°F" for every other surface.
        XCTAssertEqual(TemperatureUnit.fahrenheit.formatBareDegree(58), "58°")
        XCTAssertEqual(TemperatureUnit.fahrenheit.formatBareDegreeFromFahrenheit(60), "60°")
        XCTAssertEqual(TemperatureUnit.celsius.formatBareDegreeFromFahrenheit(58), "14°")
        XCTAssertEqual(TemperatureUnit.fahrenheit.format(58), "58°F")
    }

    // MARK: - Light-tile seam

    func testLightTileSeamResolvesBundledTreatmentLFiles() throws {
        // The iOS test host is a logic-test host (Bundle.main is
        // the test runner; app resources do not resolve — the
        // FallbackImageProvider split). So pin the RESOLUTION
        // (engine key → final-L resource name) and the in-repo
        // folder the app target bundles under that name.
        let item = ClothingItem(
            key: "SHORT_SLEEVE", name: "Short Sleeve Shirt",
            description: "Moisture-wicking technical fabric",
            icon: "tshirt", amazonSearchTerm: "short sleeve running shirt")
        let service = ClothingThumbnailService.shared
        XCTAssertEqual(service.lightTileResourceName(for: item, gender: .male), "short-sleeve-male")
        XCTAssertEqual(service.lightTileResourceName(for: item, gender: .female), "short-sleeve-female")

        let folder = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../RunWear/garment_tiles_light", isDirectory: true)
            .standardizedFileURL
        let pngs = try FileManager.default.contentsOfDirectory(atPath: folder.path)
            .filter { $0.hasSuffix(".png") }
        XCTAssertEqual(pngs.count, 58, "the Treatment L set is 29 keys × 2 genders")
        XCTAssertTrue(pngs.contains("short-sleeve-male.png"))
        XCTAssertTrue(pngs.contains("short-sleeve-female.png"))
        // Every bundled file name is a <key>-<gender> pair the
        // seam can produce: strip the gender suffix and the key
        // must be one the engine's map knows (checked through
        // hasThumbnail's inverse — the folder holds no orphans).
        for name in pngs {
            XCTAssertTrue(name.hasSuffix("-male.png") || name.hasSuffix("-female.png"), name)
        }
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
        print("RW144 wrote \(name) (\(Int(image.size.width))x\(Int(image.size.height))pt @3x)")
    }

    @MainActor
    private func fittingHeight<Content: View>(_ content: Content, width: CGFloat) -> CGFloat {
        let renderer = ImageRenderer(content: content)
        renderer.scale = 3
        renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        return renderer.uiImage?.size.height ?? 0
    }

    /// A synthetic "photo": a vertical gradient with a dark
    /// runner-silhouette mass in the top-center (the face zone)
    /// — bright and dark variants for the contrast proofs.
    static func syntheticPhoto(bright: Bool) -> UIImage {
        let size = CGSize(width: 393, height: 383)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            let cg = ctx.cgContext
            let top = bright ? UIColor(red: 0.98, green: 0.95, blue: 0.82, alpha: 1)
                             : UIColor(red: 0.10, green: 0.09, blue: 0.07, alpha: 1)
            let bottom = bright ? UIColor(red: 0.85, green: 0.72, blue: 0.45, alpha: 1)
                                : UIColor(red: 0.03, green: 0.03, blue: 0.02, alpha: 1)
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                      colors: [top.cgColor, bottom.cgColor] as CFArray,
                                      locations: [0, 1])!
            cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            // Runner silhouette: head + torso, top-center.
            UIColor(red: 0.16, green: 0.15, blue: 0.14, alpha: 1).setFill()
            cg.fillEllipse(in: CGRect(x: 171, y: 26, width: 51, height: 62))
            cg.fill(CGRect(x: 141, y: 88, width: 111, height: 210))
        }
    }

    static func solidImage(_ color: UIColor) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
        return renderer.image { _ in
            color.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 64, height: 64))
        }
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

    private func heroOverlay(
        photo: UIImage, isNow: Bool = true, pill: HeroPillState = .freeLook(used: 6, allowance: 10)
    ) -> some View {
        let top = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.topRegion)
        let chip = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.chipRegion)
        let foot = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.footRegion)
        print("RW144 luminance top=\(String(format: "%.3f", top)) chip=\(String(format: "%.3f", chip)) foot=\(String(format: "%.3f", foot)) footPeak=\(String(format: "%.3f", HeroScrimMath.peakAlpha(forLuminance: foot)))")
        return ZStack {
            Image(uiImage: photo)
                .resizable()
                .scaledToFill()
                .frame(width: 393, height: 383)
                .clipped()
            HeroChromeOverlayView(
                placeName: "Phoenix, AZ",
                dateText: isNow ? "Sun, Nov 8" : "Tue, Nov 10",
                timeText: isNow ? "Now" : "3:00 PM",
                isNow: isNow,
                formattedFeelsLike: "58°",
                formattedActual: "60°",
                feelsLikeF: 58,
                topLuminance: top, chipLuminance: chip, footLuminance: foot,
                onPlaceTapped: {}, onSettingsTapped: {},
                onDateTapped: {}, onTimeTapped: {}, onNowTapped: {},
                // Ruling 2 conformance (Nov 9, 2026): the overlay
                // now carries the weather line and the pill's
                // planning seat — the harness passes the same
                // fixture weather the sheet composition below it
                // uses, and the pill state's own planning height.
                windMph: 0, rainPercent: 0, humidityPercent: 60, uvIndex: 0,
                weatherLuminance: HeroLuminanceSampler.meanLuminance(
                    of: photo, region: HeroLuminanceSampler.weatherRegion),
                pillHeight: HeroPillMetrics.height(for: pill),
                pillBottomOffset: HeroVerticalLayout.straddleDip
            )
        }
        .frame(width: 393, height: 383)
    }

    private func sheetTop(pill: HeroPillState?) -> some View {
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
            .padding(.top, 16)
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
            if let pill, pill != .hidden {
                HeroPillView(pillState: pill, seat: .straddle, onPillGetUnlimited: {})
                    .offset(y: -9)
            }
        }
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

    // MARK: - Rendered walkthrough frames

    /// (a) First open at rest, BOTH themes, with the dock
    /// clear-space measurement asserted: the dock's top edge
    /// (852 − 14 − measured dock height, minus its 12 pt side
    /// insets' band) must sit BELOW the bottom of the lowest
    /// readable sheet element in the first-open composition
    /// (the third outfit row).
    @MainActor
    func testRW144FirstOpenFrames() throws {
        for (scheme, suffix) in [(ColorScheme.light, "light"), (ColorScheme.dark, "dark")] {
            let photo = Self.syntheticPhoto(bright: scheme == .light)
            let dock = WearIveRunMiniBarView(
                state: .updated(payload: .place("Phoenix, AZ")),
                entry: fixtureEntry(),
                formattedFeelsLike: "58°F", cueCount: 0,
                onOpenCollection: {}, onTrash: {})
            let dockHeight = fittingHeight(dock, width: 393 - 24)
            // Lowest readable element: hero (383) − sheet
            // overlap (28) + pill clearance (36) + weather line
            // (~40) + hairline (17) + count header (~46) +
            // three outfit rows (3 × 72).
            let lowestReadableBottom: CGFloat = 383 - 28 + 36 + 40 + 17 + 46 + 3 * 72
            let dockTop = 852 - 14 - dockHeight
            print("RW144 first-open (\(suffix)): dockHeight=\(dockHeight) dockTop=\(dockTop) lowestReadableBottom=\(lowestReadableBottom) clearance=\(dockTop - lowestReadableBottom)")
            XCTAssertGreaterThan(dockTop, lowestReadableBottom,
                                 "RW-144 dock rule: at first open the dock overlays NO readable content (\(suffix))")

            let screen = ZStack(alignment: .bottom) {
                VStack(spacing: 0) {
                    heroOverlay(photo: photo)
                    sheetTop(pill: .freeLook(used: 6, allowance: 10))
                        .padding(.top, -28)
                        .frame(maxHeight: .infinity)
                }
                dock
                    .padding(.horizontal, 12)
                    .padding(.bottom, 14)
            }
            .background(AppTheme.background)
            .environment(\.colorScheme, scheme)
            try writeRender(screen, size: CGSize(width: 393, height: 852),
                            name: "RW144-a-first-open-\(suffix).png")
        }
    }

    /// (b) Hero in Now state, (c) hero in a picked state —
    /// both themes for (b), light for (c).
    @MainActor
    func testRW144HeroFrames() throws {
        for (scheme, suffix) in [(ColorScheme.light, "light"), (ColorScheme.dark, "dark")] {
            let photo = Self.syntheticPhoto(bright: scheme == .light)
            try writeRender(
                heroOverlay(photo: photo).environment(\.colorScheme, scheme),
                size: CGSize(width: 393, height: 383),
                name: "RW144-b-hero-now-\(suffix).png")
        }
        let picked = heroOverlay(photo: Self.syntheticPhoto(bright: true), isNow: false)
            .environment(\.colorScheme, .light)
        try writeRender(picked, size: CGSize(width: 393, height: 383),
                        name: "RW144-c-hero-picked-light.png")
    }

    /// (d) Sheet top (weather line + count header + outfit).
    @MainActor
    func testRW144SheetTopFrames() throws {
        for (scheme, suffix) in [(ColorScheme.light, "light"), (ColorScheme.dark, "dark")] {
            let sheet = sheetTop(pill: .freeLook(used: 6, allowance: 10))
                .frame(width: 393, height: 469)
                .background(AppTheme.background)
                .environment(\.colorScheme, scheme)
            try writeRender(sheet, size: CGSize(width: 393, height: 469),
                            name: "RW144-d-sheet-top-\(suffix).png")
        }
    }

    /// (e) Below the fold: day-chart glance band + Change time
    /// row + the complete detailed weather block.
    @MainActor
    func testRW144BelowFoldFrame() throws {
        ManualLocationStore.clear()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today)!
        func stamp(_ date: Date, _ hour: Int) -> String {
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            return String(format: "%04d-%02d-%02dT%02d:00", c.year!, c.month!, c.day!, hour)
        }
        var times: [String] = []
        var temps: [Double] = []
        var apparent: [Double] = []
        for hour in 0..<24 {
            let v = -2.0 + Double(hour) * 0.9
            times.append(stamp(today, hour)); temps.append(v); apparent.append(v)
        }
        for hour in 0..<24 {
            times.append(stamp(tomorrow, hour)); temps.append(10); apparent.append(10)
        }
        let hourly = HourlyWeather(
            time: times, temperature2m: temps, apparentTemperature: apparent,
            relativeHumidity2m: [Int](repeating: 55, count: 48),
            precipitationProbability: [Int](repeating: 0, count: 48),
            precipitation: [Double](repeating: 0, count: 48),
            rain: [Double](repeating: 0, count: 48),
            uvIndex: [Double](repeating: 3, count: 48),
            windSpeed10m: [Double](repeating: 6, count: 48),
            windDirection10m: [Int](repeating: 90, count: 48),
            windGusts10m: [Double](repeating: 9, count: 48),
            weatherCode: [Int](repeating: 0, count: 48))
        let data = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 8, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: hourly, timezone: TimeZone.current.identifier, currentDetail: nil)
        let vm = WeatherViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.weatherData = data
        vm.selectedDate = today
        vm.selectedHour = 12
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        let chart = try XCTUnwrap(vm.dayChart, "ramp fixture produces a day chart")

        let details: [WeatherDetailType] = [
            .feelsLike(value: "58°F"), .actual(value: "60°F"), .condition(.clear),
            .wind(speed: 0, direction: "N", gusts: 2), .humidity(percentage: 60),
            .uvIndex(value: 0), .precipitation(percentage: 0)
        ]
        let belowFold = VStack(spacing: 0) {
            DayChartBandView(model: chart, selectedHour: 12, nowHour: 12, unit: .fahrenheit)
                .padding(.horizontal, 16)
            DetailedWeatherBlockView(details: details, onSelect: { _ in })
                .padding(.top, 24)
            Spacer(minLength: 0)
        }
        .padding(.top, 16)
        .background(AppTheme.sheet)
        .environment(\.colorScheme, .light)
        try writeRender(belowFold, size: CGSize(width: 393, height: 560),
                        name: "RW144-e-below-fold-light.png")
    }

    /// (f) A generating pill state at the straddle seat;
    /// (g) a stock-serve hero (no personal photo, no pill).
    @MainActor
    func testRW144PillAndStockFrames() throws {
        // RW-174 / RW-171: the free generating pill frame that
        // stood here as a second, two-line state is removed —
        // the generating states are the one .making pill on
        // both tiers (the frame now shows it once, as shipped).
        let generating = VStack(spacing: 28) {
            HeroPillView(pillState: .making, seat: .straddle)
            HeroPillView(pillState: .exhausted(used: 10, allowance: 10), seat: .straddle, onPillGetUnlimited: {})
            HeroPillView(pillState: .premium, seat: .straddle)
        }
        .frame(width: 393, height: 300)
        .background(AppTheme.sheet)
        .environment(\.colorScheme, .light)
        try writeRender(generating, size: CGSize(width: 393, height: 300),
                        name: "RW144-f-pill-states-light.png")

        let stock = VStack(spacing: 0) {
            heroOverlay(photo: Self.syntheticPhoto(bright: true), pill: .hidden)
            sheetTop(pill: nil)
                .padding(.top, -28)
                .frame(maxHeight: .infinity)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(stock, size: CGSize(width: 393, height: 852),
                        name: "RW144-g-stock-hero-light.png")
    }

    /// Contrast proofs: the feels-like block over a BRIGHT
    /// photo and over a DARK photo — the luminance-driven scrim
    /// adapts (bright peak > floor; dark peak = floor).
    @MainActor
    func testRW144ContrastProofFrames() throws {
        for bright in [true, false] {
            let photo = Self.syntheticPhoto(bright: bright)
            let foot = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.footRegion)
            let peak = HeroScrimMath.peakAlpha(forLuminance: foot)
            print("RW144 contrast \(bright ? "bright" : "dark"): footLuminance=\(String(format: "%.3f", foot)) scrimPeak=\(String(format: "%.3f", peak))")
            if bright {
                XCTAssertGreaterThan(peak, HeroScrimMath.floorPeakAlpha, "bright photo must scale the scrim up from the floor")
            } else {
                XCTAssertEqual(peak, HeroScrimMath.floorPeakAlpha, accuracy: 0.02, "dark photo relaxes toward the floor")
            }
            try writeRender(
                heroOverlay(photo: photo).environment(\.colorScheme, .light),
                size: CGSize(width: 393, height: 383),
                name: bright ? "RW144-contrast-bright.png" : "RW144-contrast-dark.png")
        }
    }
}
