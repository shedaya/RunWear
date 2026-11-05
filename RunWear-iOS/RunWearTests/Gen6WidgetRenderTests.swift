import XCTest
import SwiftUI
import UIKit
import WidgetKit

/// GEN-6 WAVE C — EVIDENCE CLOSURE 1 (gen-5 gate gap #3): the
/// iOS "Running Outfit" widget, RENDERED.
///
/// The gen-5 gate proved the widget by test only (families,
/// record store, freshness forms) and named the missing
/// artefact plainly: "No render of the widget exists in any
/// size, populated or stale." This suite renders the REAL
/// widget entry view (`RunWearWidgetEntryView`, dual-membered
/// into this target from RunWearWidget.swift — the same view
/// the WidgetKit extension hosts) through SwiftUI ImageRenderer
/// in the CI simulator, at the exact per-family point sizes, in
/// the RW-66 render-proof style. Every render is attached to
/// the run's result bundle (keepAlways) AND written as a PNG
/// into `RunWear-iOS/render-artefacts/` in the checkout, which
/// the workflow uploads as the `runwear-ios-renders` artefact.
///
/// Sizes are the WidgetKit iPhone family sizes in points:
/// Small 170×170, Medium 364×170, Large 364×382.
///
/// The record is representative and realistic: New York, NY,
/// a cold clear morning — 36°F feels-like (the hero figure),
/// actual 39°F, wind 9 mph, the outfit the engine prices off
/// that frame (long sleeve + light jacket, tights, cap and
/// light gloves), and a later-today line. Freshness states are
/// driven by the entry date against the record's savedAt —
/// exactly the seam WidgetKit itself uses — fresh (25 minutes
/// old), stale (2h 05m old, past the 60-minute threshold), and
/// the authored error card (no record at all).
final class Gen6WidgetRenderTests: XCTestCase {

    private let savedAt = Date(timeIntervalSince1970: 1_798_000_000)

    private func record() -> WidgetLastGood {
        WidgetLastGood(
            feelsLikeF: 36,
            temperatureF: 39,
            conditionText: "Clear",
            conditionIcon: "sun.max.fill",
            windMph: 9,
            humidityPercent: 48,
            uvIndex: 2,
            locationName: "New York, NY",
            topBaseName: "Long Sleeve Shirt",
            layerNames: ["Light Jacket"],
            bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Light Jacket · rain 20%",
            unitIsCelsius: false,
            savedAt: savedAt
        )
    }

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func render<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertEqual(image.size.width, size.width, "\(name): rendered width must equal the family point width")
        XCTAssertEqual(image.size.height, size.height, "\(name): rendered height must equal the family point height")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")

        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
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

    // MARK: - Fresh populated renders, one per family

    @MainActor
    func testWidgetRenderSmallFresh() throws {
        let entry = RunWearWidgetEntry(
            date: savedAt.addingTimeInterval(25 * 60), record: record(), errorCategory: nil)
        XCTAssertTrue(
            WidgetFreshness.eyebrow(record(), now: entry.date).hasPrefix("FOR NOW · "),
            "a fresh record's eyebrow states its save clock time")
        XCTAssertFalse(WidgetFreshness.isStale(record(), now: entry.date))
        let view = RunWearWidgetEntryView(entry: entry)
            .previewContext(WidgetPreviewContext(family: .systemSmall))
        try render(view, size: CGSize(width: 170, height: 170), name: "widget-small.png")
    }

    @MainActor
    func testWidgetRenderMediumFresh() throws {
        let entry = RunWearWidgetEntry(
            date: savedAt.addingTimeInterval(25 * 60), record: record(), errorCategory: nil)
        let view = RunWearWidgetEntryView(entry: entry)
            .previewContext(WidgetPreviewContext(family: .systemMedium))
        try render(view, size: CGSize(width: 364, height: 170), name: "widget-medium.png")
    }

    @MainActor
    func testWidgetRenderLargeFresh() throws {
        let entry = RunWearWidgetEntry(
            date: savedAt.addingTimeInterval(25 * 60), record: record(), errorCategory: nil)
        XCTAssertTrue(
            WidgetFreshness.updatedLabel(record(), now: entry.date).hasPrefix("Updated "),
            "the Large brand strip renders its Updated label in clock form while recent")
        let view = RunWearWidgetEntryView(entry: entry)
            .previewContext(WidgetPreviewContext(family: .systemLarge))
        try render(view, size: CGSize(width: 364, height: 382), name: "widget-large.png")
    }

    // MARK: - Stale state (same record, aged past the threshold)

    @MainActor
    func testWidgetRenderSmallStale() throws {
        let staleDate = savedAt.addingTimeInterval(2 * 3600 + 5 * 60)
        XCTAssertTrue(WidgetFreshness.isStale(record(), now: staleDate),
                      "2h 05m is past the 60-minute stale threshold")
        XCTAssertEqual(WidgetFreshness.eyebrow(record(), now: staleDate), "FOR NOW · 2H AGO",
                       "the stale eyebrow speaks in age form, never a bare clock time")
        let entry = RunWearWidgetEntry(date: staleDate, record: record(), errorCategory: nil)
        let view = RunWearWidgetEntryView(entry: entry)
            .previewContext(WidgetPreviewContext(family: .systemSmall))
        try render(view, size: CGSize(width: 170, height: 170), name: "widget-small-stale.png")
    }

    // MARK: - Authored error card (no record at all)

    @MainActor
    func testWidgetRenderMediumErrorCard() throws {
        XCTAssertEqual(WidgetErrorCategory.weather.headline, "Couldn't load weather")
        XCTAssertEqual(WidgetErrorCategory.weather.reason, "Couldn't reach the weather service")
        let entry = RunWearWidgetEntry(date: savedAt, record: nil, errorCategory: .weather)
        let view = RunWearWidgetEntryView(entry: entry)
            .previewContext(WidgetPreviewContext(family: .systemMedium))
        try render(view, size: CGSize(width: 364, height: 170), name: "widget-medium-error.png")
    }
}
