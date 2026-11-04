import XCTest
import UIKit

/// GEN-3 share-render fix — pins for the mechanism that replaced
/// build 21's share path (blank sheet, ~23 s lone spinner, card
/// flickering in/out ≥3×). The pre-fix day view ran ONE serial
/// task — all Kit thumbnail downloads, then the poster render,
/// then the Kit render — into @State values the sheet branched
/// over. The replacement (ShareCardRenderStore, dual-membered so
/// these pins compile the production logic):
///
///   * one slot per card; the pipelines are independent;
///   * a slot renders AT MOST ONCE per content key — requests
///     while rendering or ready are no-ops, and the image is
///     assigned exactly once (the anti-flicker guarantee);
///   * a process-wide cache serves repeat visits with no render;
///   * failure lands in a retryable .failed, never a dead spinner;
///   * the poster hero is downscaled to rendered size off the
///     main actor before ImageRenderer ever sees it.
final class Gen3ShareRenderTests: XCTestCase {

    override func setUp() async throws {
        await MainActor.run { ShareCardImageCache.shared.removeAll() }
    }

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func tinyImage() -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8), format: format).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
    }

    /// Thread-safe render counter (render closures run off the
    /// test's actor).
    private final class RenderCounter: @unchecked Sendable {
        private let lock = NSLock()
        private var value = 0
        var count: Int { lock.lock(); defer { lock.unlock() }; return value }
        func increment() { lock.lock(); value += 1; lock.unlock() }
    }

    private func uniqueKey(_ base: String) -> String { "\(base)-\(UUID().uuidString)" }

    // MARK: - Render-once per key (the anti-flicker guarantee)

    @MainActor
    func testSlotRendersExactlyOncePerKey_despiteRepeatedRequests() async {
        let slot = ShareCardSlot()
        let counter = RenderCounter()
        let key = uniqueKey("poster")
        let render: () async -> UIImage? = {
            counter.increment()
            try? await Task.sleep(nanoseconds: 300_000_000)
            return self.tinyImage()
        }
        // Appearance pre-warm, the share tap, and a second tap
        // while the render is in flight: ONE render.
        slot.request(key: key, render: render)
        slot.request(key: key, render: render)
        slot.request(key: key, render: render)
        await slot.waitUntilSettled()
        XCTAssertEqual(counter.count, 1, "in-flight requests dedupe to a single render")
        XCTAssertEqual(slot.state, .ready)
        let settledImage = slot.image
        XCTAssertNotNil(settledImage)

        // After ready, further requests change nothing — the
        // published image instance is stable, so a sheet built
        // from it has nothing to flicker between.
        slot.request(key: key, render: render)
        XCTAssertEqual(counter.count, 1, "a ready slot never re-renders the same key")
        XCTAssertTrue(slot.image === settledImage, "the image instance is the one single assignment")
    }

    @MainActor
    func testSlotRendersAgainForANewKey() async {
        let slot = ShareCardSlot()
        let counter = RenderCounter()
        let render: () async -> UIImage? = { counter.increment(); return self.tinyImage() }
        slot.request(key: uniqueKey("a"), render: render)
        await slot.waitUntilSettled()
        slot.request(key: uniqueKey("b"), render: render)
        await slot.waitUntilSettled()
        XCTAssertEqual(counter.count, 2, "a new content key is a new card and renders")
        XCTAssertEqual(slot.state, .ready)
    }

    // MARK: - Independence + concurrency (the anti-23-seconds pins)

    @MainActor
    func testPosterSettlesWhileKitStillRenders_noCrossCardGating() async {
        let poster = ShareCardSlot()
        let kit = ShareCardSlot()
        kit.request(key: uniqueKey("kit")) {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            return self.tinyImage()
        }
        poster.request(key: uniqueKey("poster")) { self.tinyImage() }
        await poster.waitUntilSettled()
        XCTAssertEqual(poster.state, .ready,
                       "the poster completes on its own pipeline")
        XCTAssertEqual(kit.state, .rendering,
                       "…while the Kit Card is still rendering — the pre-fix serial chain made the poster wait for exactly this")
    }

    @MainActor
    func testBothCardsRenderConcurrently_notSerially() async {
        let poster = ShareCardSlot()
        let kit = ShareCardSlot()
        let slow: () async -> UIImage? = {
            try? await Task.sleep(nanoseconds: 500_000_000)
            return self.tinyImage()
        }
        let start = Date()
        poster.request(key: uniqueKey("poster"), render: slow)
        kit.request(key: uniqueKey("kit"), render: slow)
        await poster.waitUntilSettled()
        await kit.waitUntilSettled()
        let elapsed = Date().timeIntervalSince(start)
        XCTAssertEqual(poster.state, .ready)
        XCTAssertEqual(kit.state, .ready)
        XCTAssertLessThan(elapsed, 0.85,
                          "two 0.5 s renders overlapped (\(elapsed)s); the serial pre-fix chain would take ≥1.0 s before either card existed")
    }

    // MARK: - Cache

    @MainActor
    func testCacheServesANewSlotWithoutRendering() async {
        let key = uniqueKey("poster")
        let counter = RenderCounter()
        let first = ShareCardSlot()
        first.request(key: key) { counter.increment(); return self.tinyImage() }
        await first.waitUntilSettled()
        XCTAssertEqual(counter.count, 1)

        // A fresh day view (a new slot) for the same entry asks
        // again — the cache answers; nothing renders.
        let second = ShareCardSlot()
        second.request(key: key) { counter.increment(); return self.tinyImage() }
        XCTAssertEqual(second.state, .ready, "cache hit settles synchronously")
        XCTAssertEqual(counter.count, 1, "no second render for a cached card")
        XCTAssertTrue(second.image === first.image, "the cached instance itself is served")
    }

    // MARK: - Failure is a state, not a dead spinner

    @MainActor
    func testFailedRenderLandsInFailed_andRetryRerenders() async {
        let slot = ShareCardSlot()
        let counter = RenderCounter()
        let key = uniqueKey("kit")
        slot.request(key: key) { counter.increment(); return nil }
        await slot.waitUntilSettled()
        XCTAssertEqual(slot.state, .failed, "a failed render is a state the sheet can show — not a spinner forever")
        XCTAssertNil(slot.image)

        slot.request(key: key) { counter.increment(); return self.tinyImage() }
        await slot.waitUntilSettled()
        XCTAssertEqual(counter.count, 2, "retry after failure renders again")
        XCTAssertEqual(slot.state, .ready)
    }

    // MARK: - Hero preparation

    func testDownscaleCapsThePosterDimension_andKeepsAspect() {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let big = UIGraphicsImageRenderer(size: CGSize(width: 1000, height: 1700), format: format).image { _ in }
        let out = ShareCardImagePrep.downscaledForPoster(big)
        XCTAssertEqual(max(out.size.width, out.size.height),
                       ShareCardImagePrep.posterMaxDimension, accuracy: 2,
                       "the hero handed to ImageRenderer never exceeds the poster's rendered size")
        XCTAssertEqual(out.size.width / out.size.height, 1000.0 / 1700.0, accuracy: 0.01,
                       "aspect is preserved")

        let small = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 300), format: format).image { _ in }
        let untouched = ShareCardImagePrep.downscaledForPoster(small)
        XCTAssertEqual(untouched.size, small.size, "an already-small hero passes through")
    }

    // MARK: - Sheet copy (labeled states, never a bare spinner)

    func testShareSheetCopyIsLabeledPerCardAndState() {
        XCTAssertEqual(ShareCardCopy.progressLabel(for: .poster), "Making your Morning Poster…")
        XCTAssertEqual(ShareCardCopy.progressLabel(for: .kit), "Making your Kit Card…")
        XCTAssertEqual(ShareCardCopy.failureLabel(for: .poster), "Your Morning Poster couldn't be made.")
        XCTAssertEqual(ShareCardCopy.failureLabel(for: .kit), "Your Kit Card couldn't be made.")
    }

    // MARK: - Wiring (source pins on the day view)

    func testDayViewShareWiring_sourcePins() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertFalse(collection.contains("posterImage = await renderPoster()"),
                       "the serial one-task chain (thumbnails → poster → kit) is gone")
        XCTAssertFalse(collection.contains("ProgressView().padding(24)"),
                       "the bare unlabeled spinner that was the old sheet's waiting state is gone")
        XCTAssertTrue(collection.contains(".task { await MainActor.run { requestPoster() } }")
                      && collection.contains(".task { await MainActor.run { requestKit() } }"),
                      "both cards pre-warm as independent pipelines on appearance")
        XCTAssertTrue(collection.contains("withTaskGroup"),
                      "kit thumbnails fetch concurrently, not in a sequential await loop")
        XCTAssertTrue(collection.contains("ShareCardSheetView("),
                      "the sheet is the per-card slot-observing view")
        XCTAssertTrue(collection.contains("ShareCardCopy.progressLabel"),
                      "the sheet's working state is the labeled copy")
        XCTAssertTrue(collection.contains("ShareCardImagePrep.downscaledForPoster"),
                      "the poster hero is downscaled before the render")
    }
}
