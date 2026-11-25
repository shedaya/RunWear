import XCTest
import SwiftUI
import UIKit
import WidgetKit

/// GEN-6 (RW-67) — iOS temperature overflow, render-proven.
///
/// The owner's build-26 walkthrough (Nov 5, 2026): the big
/// number on the Home screen — the hero feels-like numeral —
/// overflows "in some versions." The deep audit's iOS lane had
/// set the same class aside in the widget as "system-clipped by
/// design" from layout-constraint reading alone; this suite
/// renders instead. Order per the owner's narrowing: HERO
/// FIRST, then the widget in all three sizes, then the watch
/// glance (DA-14's surface), as secondary checks.
///
/// Method (the RW-66 harness style): every surface is composed
/// EXACTLY as production composes it — the hero row is
/// HomeHeroNumeralRow (extracted from WeatherOutfitView's sheet
/// header, composition unchanged), the widget is
/// RunWearWidgetEntryView (split out of the @main widget file
/// so it can be dual-membered here), the glance is
/// WatchGlancePage (extracted from WatchOutfitView) — rendered
/// with ImageRenderer at the surface's real canvas, attached
/// keepAlways, and MEASURED: a second render at an unspecified
/// (or width-only) proposed size yields the composition's
/// fitting size; a fitting width beyond the canvas, or a
/// fitting height at the canvas's width beyond the canvas's
/// height, IS overflow (the content cannot be fully inside the
/// frame — it is clipped, pushed out, or truncated there).
/// Benign controls (72°F at default type) validate the method:
/// a sound method passes the controls.
///
/// Fixtures: three-digit (115°F) and negative (-13°F)
/// temperatures, at default (.large) and largest
/// (.accessibility5) Dynamic Type.
final class Gen6RW67RenderTests: XCTestCase {

    // MARK: - Harness

    @MainActor
    private func attach<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertGreaterThan(image.size.width, 0)
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
    }

    /// The composition's fitting size: an unspecified proposal
    /// (natural size), then a width-only proposal (the height
    /// the content needs at the canvas's real width).
    @MainActor
    private func fittingSize<Content: View>(
        _ content: Content, atWidth width: CGFloat? = nil
    ) -> CGSize {
        let renderer = ImageRenderer(content: content)
        renderer.scale = 2
        if let width {
            renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        }
        guard let image = renderer.uiImage else { return .zero }
        return image.size
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

    private func assertFits(
        fitting: CGSize, canvas: CGSize, _ label: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        XCTAssertLessThanOrEqual(
            fitting.width, canvas.width + 0.5,
            "\(label): fitting width \(fitting.width) exceeds canvas width \(canvas.width) — horizontal overflow",
            file: file, line: line)
        XCTAssertLessThanOrEqual(
            fitting.height, canvas.height + 0.5,
            "\(label): fitting height \(fitting.height) at canvas width exceeds canvas height \(canvas.height) — vertical overflow",
            file: file, line: line)
    }

    // MARK: - 1 · HERO (the owner's surface)
    //
    // The row sits in the sheet header at .padding(.horizontal,
    // 16) on a 390 pt phone: canvas width 358. Its height is
    // content-driven in production (the header grows), so the
    // binding constraint is WIDTH: past it, the numeral pushes
    // the figures out of the sheet or is itself cut.

    private let heroCanvasWidth: CGFloat = 358

    private func heroRow(feelsLikeF: Double, unit: TemperatureUnit = .fahrenheit) -> HomeHeroNumeralRow {
        HomeHeroNumeralRow(
            formattedTemperature: unit.formatFromFahrenheit(feelsLikeF),
            bracket: HeroTempBracket.from(feelsLikeTemperature: feelsLikeF),
            windMph: 11, rainPercent: 40, humidityPercent: 54, uvIndex: 3
        )
    }

    /// The hero fit rule. BEFORE the fix (run 42125431543),
    /// the row's fixed content exceeded the canvas at every
    /// fixture — natural fitting widths 415/453/471 pt at
    /// default type, 634.5/676.5/763 pt at accessibility5
    /// against the 358 pt canvas — and SwiftUI took the excess
    /// out of the numeral Text, which WRAPPED mid-value while
    /// the figures ran off the edge; the height the row needed
    /// AT the canvas width ballooned to 2×–5× its natural
    /// height (191 vs 95.5; 814.5 vs 163).
    ///
    /// AFTER the fix the numeral shrinks to fit its slot
    /// (minimumScaleFactor), so a width-constrained render must
    /// need NO more height than the natural render — nothing
    /// wraps, nothing balloons — and the attached canvas render
    /// shows the whole value on one line. The natural width is
    /// still printed for the record (a shrink-to-fit element's
    /// ideal width legitimately exceeds its slot).
    @MainActor
    private func renderHero(
        feelsLikeF: Double, type: DynamicTypeSize, name: String
    ) throws -> (natural: CGSize, atWidth: CGSize) {
        let row = heroRow(feelsLikeF: feelsLikeF)
            .environment(\.dynamicTypeSize, type)
            .background(AppTheme.background)
        let natural = fittingSize(row)
        let atWidth = fittingSize(row, atWidth: heroCanvasWidth)
        print("RW-67 HERO \(name): natural fitting \(natural), at canvas width \(atWidth)")
        try attach(row, size: CGSize(width: heroCanvasWidth, height: max(atWidth.height, 60)),
                   name: name)
        return (natural, atWidth)
    }

    private func assertHeroFits(
        _ fit: (natural: CGSize, atWidth: CGSize), _ label: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        XCTAssertLessThanOrEqual(
            fit.atWidth.height, fit.natural.height * 1.05 + 2,
            "\(label): at the canvas width the row needs height \(fit.atWidth.height) vs natural \(fit.natural.height) — content is wrapping/squeezing (the shipped defect: the numeral wrapped mid-value and the figures ran off the edge)",
            file: file, line: line)
    }

    @MainActor
    func testRW67Hero_Control72_DefaultType() throws {
        let fit = try renderHero(feelsLikeF: 72, type: .large,
                                 name: "RW-67-hero-72F-default-after.png")
        assertHeroFits(fit, "hero 72°F default (control)")
    }

    @MainActor
    func testRW67Hero_ThreeDigit_DefaultType() throws {
        let fit = try renderHero(feelsLikeF: 115, type: .large,
                                 name: "RW-67-hero-115F-default-after.png")
        assertHeroFits(fit, "hero 115°F default")
    }

    @MainActor
    func testRW67Hero_Negative_DefaultType() throws {
        let fit = try renderHero(feelsLikeF: -13, type: .large,
                                 name: "RW-67-hero-neg13F-default-after.png")
        assertHeroFits(fit, "hero -13°F default")
    }

    @MainActor
    func testRW67Hero_Control72_LargestType() throws {
        let fit = try renderHero(feelsLikeF: 72, type: .accessibility5,
                                 name: "RW-67-hero-72F-largest-after.png")
        assertHeroFits(fit, "hero 72°F largest type (control)")
    }

    @MainActor
    func testRW67Hero_ThreeDigit_LargestType() throws {
        let fit = try renderHero(feelsLikeF: 115, type: .accessibility5,
                                 name: "RW-67-hero-115F-largest-after.png")
        assertHeroFits(fit, "hero 115°F largest type")
    }

    @MainActor
    func testRW67Hero_Negative_LargestType() throws {
        let fit = try renderHero(feelsLikeF: -13, type: .accessibility5,
                                 name: "RW-67-hero-neg13F-largest-after.png")
        assertHeroFits(fit, "hero -13°F largest type")
    }

    /// The fix's construction, pinned: the numeral's metric
    /// lives in a child the row caps, and the numeral shrinks
    /// to fit — it must never go back to a bare fixed Text
    /// whose only "cap" sits on the Text itself.
    func testRW67HeroFixConstruction_sourcePin() throws {
        let row = try sourceFile("../RunWear/Views/HomeHeroNumeralRow.swift")
        XCTAssertTrue(row.contains("struct HeroNumeralText"),
                      "the numeral is its own child view, so its @ScaledMetric can be capped by its parent")
        XCTAssertTrue(row.contains("HeroNumeralText(text: formattedTemperature)"),
                      "the row composes the numeral child")
        XCTAssertTrue(row.contains(".minimumScaleFactor(0.2)"),
                      "the numeral shrinks to fit its slot — never wraps, never truncates")
        XCTAssertTrue(row.contains(".lineLimit(1)"),
                      "the numeral is one line by construction")
        // The cap is applied to the cluster from outside the
        // child (after the chip's capsule background, on the
        // cluster HStack) — count the occurrences: one on the
        // cluster; the numeral Text itself must NOT carry one
        // (that was the shipped no-op).
        XCTAssertTrue(row.contains("dynamicTypeSize(...DynamicTypeSize.accessibility1)"),
                      "the accessibility1 cap is present")
    }

    private func sourceFile(_ relative: String) throws -> String {
        let here = URL(fileURLWithPath: #filePath)
        let url = here.deletingLastPathComponent().appendingPathComponent(relative)
        return try String(contentsOf: url, encoding: .utf8)
    }

    // MARK: - 2 · WIDGET (secondary check, all three families)
    //
    // Nominal canvases for a 393 pt phone: Small 158×158,
    // Medium 364×170, Large 364×382. The record fixture is the
    // provider's sample shape with the temperature driven to
    // the fixture values and realistic garment names.

    private func widgetRecord(feelsLikeF: Double) -> WidgetLastGood {
        WidgetLastGood(
            feelsLikeF: feelsLikeF,
            temperatureF: feelsLikeF - 3,
            conditionText: "Clear",
            conditionIcon: "sun.max.fill",
            windMph: 8,
            humidityPercent: 54,
            uvIndex: 1,
            locationName: "Kansas City, MO",
            topBaseName: "Long Sleeve Shirt",
            layerNames: ["Windbreaker"],
            bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Windbreaker · rain 40%",
            unitIsCelsius: false,
            savedAt: Date()
        )
    }

    private func widgetCanvas(_ family: WidgetFamily) -> CGSize {
        switch family {
        case .systemMedium: return CGSize(width: 364, height: 170)
        case .systemLarge: return CGSize(width: 364, height: 382)
        default: return CGSize(width: 158, height: 158)
        }
    }

    @MainActor
    private func renderWidget(
        family: WidgetFamily, feelsLikeF: Double, type: DynamicTypeSize, name: String
    ) throws -> CGSize {
        let entry = RunWearWidgetEntry(
            date: Date(), record: widgetRecord(feelsLikeF: feelsLikeF), errorCategory: nil)
        // The family goes through the entry view's explicit
        // seam: the preview-context modifier does not reach
        // ImageRenderer (the first capture's small/large
        // frames silently rendered the default medium
        // composition — caught by reading the renders).
        let view = RunWearWidgetEntryView(entry: entry, familyOverride: family)
            .environment(\.dynamicTypeSize, type)
            .background(AppTheme.background)
        let canvas = widgetCanvas(family)
        let fit = fittingSize(view, atWidth: canvas.width)
        print("RW-67 WIDGET \(name): fitting at canvas width \(fit), canvas \(canvas)")
        try attach(view, size: canvas, name: name)
        return fit
    }

    @MainActor
    func testRW67WidgetSmall_ThreeDigit_LargestType() throws {
        let fit = try renderWidget(family: .systemSmall, feelsLikeF: 115, type: .accessibility5,
                                   name: "RW-67-widget-small-115F-largest-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemSmall), "widget small 115°F largest")
    }

    @MainActor
    func testRW67WidgetSmall_Negative_LargestType() throws {
        let fit = try renderWidget(family: .systemSmall, feelsLikeF: -13, type: .accessibility5,
                                   name: "RW-67-widget-small-neg13F-largest-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemSmall), "widget small -13°F largest")
    }

    @MainActor
    func testRW67WidgetSmall_Control72_DefaultType() throws {
        let fit = try renderWidget(family: .systemSmall, feelsLikeF: 72, type: .large,
                                   name: "RW-67-widget-small-72F-default-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemSmall), "widget small 72°F default (control)")
    }

    @MainActor
    func testRW67WidgetMedium_ThreeDigit_LargestType() throws {
        let fit = try renderWidget(family: .systemMedium, feelsLikeF: 115, type: .accessibility5,
                                   name: "RW-67-widget-medium-115F-largest-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemMedium), "widget medium 115°F largest")
    }

    @MainActor
    func testRW67WidgetMedium_Negative_DefaultType() throws {
        let fit = try renderWidget(family: .systemMedium, feelsLikeF: -13, type: .large,
                                   name: "RW-67-widget-medium-neg13F-default-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemMedium), "widget medium -13°F default")
    }

    @MainActor
    func testRW67WidgetLarge_ThreeDigit_LargestType() throws {
        let fit = try renderWidget(family: .systemLarge, feelsLikeF: 115, type: .accessibility5,
                                   name: "RW-67-widget-large-115F-largest-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemLarge), "widget large 115°F largest")
    }

    @MainActor
    func testRW67WidgetLarge_Negative_LargestType() throws {
        let fit = try renderWidget(family: .systemLarge, feelsLikeF: -13, type: .accessibility5,
                                   name: "RW-67-widget-large-neg13F-largest-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemLarge), "widget large -13°F largest")
    }

    @MainActor
    func testRW67WidgetLarge_Control72_DefaultType() throws {
        let fit = try renderWidget(family: .systemLarge, feelsLikeF: 72, type: .large,
                                   name: "RW-67-widget-large-72F-default-after.png")
        assertFits(fitting: fit, canvas: widgetCanvas(.systemLarge), "widget large 72°F default (control)")
    }

    // MARK: - 3 · WATCH GLANCE (secondary check, DA-14's surface)
    //
    // The glance page rendered at watch canvases (45 mm
    // 198×242, 41 mm 176×215) in the phone test host — the
    // dual-membered production page (WatchGlancePage), with the
    // view model seated on a measured weather value so
    // displayTemperature renders the comfort-adjusted fixture
    // (neutral comfort: adjusted == raw). The page's content is
    // spacer-driven, so the binding check is the temperature
    // cluster's own fit: the glance string at the page's fixed
    // 44 pt bold must fit the canvas width net of the page's
    // 12 pt padding — plus the full-page renders as evidence.

    @MainActor
    private func watchFixture(feelsLikeF: Double) -> (WatchViewModel, WeatherData) {
        let celsius = (feelsLikeF - 32) * 5 / 9
        let weather = WeatherData(
            latitude: 40.7128, longitude: -74.0060,
            currentWeather: CurrentWeather(
                temperature: celsius, windspeed: 6, weathercode: 0, isDay: 1),
            hourly: nil, timezone: nil, currentDetail: nil
        )
        let vm = WatchViewModel()
        vm.locationName = "New York, NY"
        vm.temperatureUnit = .fahrenheit
        vm.comfortLevel = .neutral
        vm.currentWeather = weather
        return (vm, weather)
    }

    @MainActor
    private func renderWatch(
        feelsLikeF: Double, type: DynamicTypeSize, canvas: CGSize, name: String
    ) throws {
        let (vm, weather) = watchFixture(feelsLikeF: feelsLikeF)
        XCTAssertEqual(vm.displayTemperature,
                       "\(Int(round(feelsLikeF)))°",
                       "the glance must be rendering the fixture value")
        let page = WatchGlancePage(viewModel: vm, weather: weather)
            .environment(\.dynamicTypeSize, type)
            .background(Color.black)
        try attach(page, size: canvas, name: name)
        // The temperature cluster's fit, at the page's own
        // font: fitting width must fit inside the page padding.
        let cluster = Text(vm.displayTemperature)
            .font(.system(size: 44, weight: .bold))
        let fit = fittingSize(cluster)
        print("RW-67 WATCH \(name): temperature fitting \(fit), canvas \(canvas)")
        XCTAssertLessThanOrEqual(
            fit.width, canvas.width - 24 + 0.5,
            "\(name): the glance temperature's fitting width \(fit.width) exceeds the padded canvas \(canvas.width - 24)")
    }

    @MainActor
    func testRW67Watch_ThreeDigit_LargestType_45mm() throws {
        try renderWatch(feelsLikeF: 115, type: .accessibility5,
                        canvas: CGSize(width: 198, height: 242),
                        name: "RW-67-watch-115F-largest-45mm-before.png")
    }

    @MainActor
    func testRW67Watch_Negative_LargestType_45mm() throws {
        try renderWatch(feelsLikeF: -13, type: .accessibility5,
                        canvas: CGSize(width: 198, height: 242),
                        name: "RW-67-watch-neg13F-largest-45mm-before.png")
    }

    @MainActor
    func testRW67Watch_ThreeDigit_LargestType_41mm() throws {
        try renderWatch(feelsLikeF: 115, type: .accessibility5,
                        canvas: CGSize(width: 176, height: 215),
                        name: "RW-67-watch-115F-largest-41mm-before.png")
    }

    @MainActor
    func testRW67Watch_Control72_DefaultType_45mm() throws {
        try renderWatch(feelsLikeF: 72, type: .large,
                        canvas: CGSize(width: 198, height: 242),
                        name: "RW-67-watch-72F-default-45mm-before.png")
    }
}
