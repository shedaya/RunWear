import Combine
import SwiftUI
import UIKit

// MARK: - Share-card rendering (GEN-3 — the share render fix)
//
// Build 21's failure, as observed in Sol's gen-2 walkthrough:
// tapping Morning Poster opened a blank white sheet with a lone
// spinner for ~23 s, the rendered card flickering in and out at
// least three times before it stabilized. Diagnosis, verified in
// the pre-fix WearIveRunDayView:
//
//   1. ONE serial `.task` chain rendered everything in sequence:
//      ALL Kit Card thumbnail downloads → the poster's hero
//      download + render → the Kit Card render. The poster — the
//      card the user asked for — waited behind the other card's
//      entire pipeline.
//   2. The thumbnail downloads themselves were sequential (a
//      for-loop of awaits): N round-trips summed.
//   3. The sheet's waiting state was a bare, unlabeled
//      ProgressView — the blank white sheet.
//   4. The sheet's content branched over three @State values on
//      the presenting view; each pipeline phase landing replaced
//      the sheet's content tree and recreated the ShareLink and
//      its preview from a freshly wrapped Image — the flicker.
//   5. Nothing was cached: every appearance of the day view
//      re-downloaded and re-rendered both cards from zero.
//   6. Both renders ran ImageRenderer at 3x on the main actor
//      over the hero at its full downloaded resolution.
//   7. The two collapses back to the entry detail (census Y-3:
//      three spinner stretches ≈22.4 s + 3.0 s + 5.7 s): the
//      collection model republishes its @Published entries on
//      EVERY non-identical settle — hero-only and weather-only
//      landings included (WearIveRunCollectionModel.
//      recordSettled) — and the home view feeds it from four
//      onChange triggers while the collection is open. Each
//      republish rebuilt the day view, cancelling the render
//      .task and tearing the sheet down; the rebuilt view
//      re-presented the sheet and restarted the serial chain
//      from zero (later stretches shorter as URLCache warmed).
//      The fix's render lives in the slot's own unstructured
//      task and writes the shared cache even if the slot dies,
//      so presenter churn can neither cancel it nor lose it.
//
// The fix is structural, and it lives here so the test target
// (which dual-members this file) pins the mechanism itself:
//
//   * Each card has its own SLOT: an independent render pipeline
//     with a state machine (idle → rendering → ready / failed).
//     The two slots never wait on each other.
//   * A slot renders AT MOST ONCE per content key. A request
//     while rendering is a no-op; a request while ready is a
//     no-op — the published image is assigned exactly once, so
//     the sheet built from it has nothing to flicker between.
//   * A process-wide cache keyed by the same content key serves
//     repeat visits instantly, with no download and no render.
//   * A failed render lands in .failed (retryable), never in a
//     silent spinner forever.

enum ShareCardKind: String, Identifiable {
    case poster
    case kit
    var id: String { rawValue }
}

/// The share sheet's words, by card and state.
enum ShareCardCopy {
    /// The labeled working state — the hero-pill rule applied to
    /// the sheet: a working state is labeled and state-driven,
    /// never a bare spinner on a blank sheet.
    static func progressLabel(for kind: ShareCardKind) -> String {
        switch kind {
        case .poster: return "Making your Morning Poster…"
        case .kit: return "Making your Kit Card…"
        }
    }

    static func failureLabel(for kind: ShareCardKind) -> String {
        switch kind {
        case .poster: return "Your Morning Poster couldn't be made."
        case .kit: return "Your Kit Card couldn't be made."
        }
    }

    /// GEN-6 (RW-31 §7): the verify gate's failure line — the
    /// rendered raster failed inspection, so the system sheet
    /// never opens and the Studio offers the retry instead.
    static let verifyFailureLabel = "This card didn't render cleanly — try again"
}

enum ShareCardState: Equatable {
    case idle
    case rendering
    case ready
    case failed
}

/// Rendered share cards by content key, shared across day-view
/// instances: leaving a day and coming back re-renders nothing.
@MainActor
final class ShareCardImageCache {
    static let shared = ShareCardImageCache()

    private var images: [String: UIImage] = [:]

    func image(for key: String) -> UIImage? { images[key] }

    func store(_ image: UIImage, for key: String) { images[key] = image }

    /// GEN-6 (RW-31 §7): eviction for the verify gate's retry —
    /// a raster that failed inspection is never served again.
    func evict(_ key: String) { images[key] = nil }

    /// Test hygiene: the cache is process-wide.
    func removeAll() { images.removeAll() }
}

/// One card's render pipeline. Owns the card's state and its ONE
/// rendered image; the share sheet observes exactly this object,
/// so the other card's progress can never touch this card's sheet.
@MainActor
final class ShareCardSlot: ObservableObject {
    @Published private(set) var state: ShareCardState = .idle
    /// The rendered card. Assigned EXACTLY ONCE per content key —
    /// that single assignment is the anti-flicker guarantee: the
    /// sheet's ShareLink is built from this instance and the
    /// instance never changes under it.
    private(set) var image: UIImage?
    /// The content key the current state/image belongs to.
    private(set) var key: String?

    private var task: Task<Void, Never>?

    /// Ask for the card for `key`, rendering via `render` only if
    /// this slot does not already hold it (or a render of it is
    /// not already in flight). Safe to call from appearance,
    /// from the share button, and from a retry — the state
    /// machine dedupes all of them.
    func request(key: String, render: @escaping () async -> UIImage?) {
        if self.key == key {
            switch state {
            case .ready, .rendering:
                return // held or in flight — never render twice
            case .idle, .failed:
                break // a retry (failed) or a fresh start (idle)
            }
        }
        self.key = key

        if let cached = ShareCardImageCache.shared.image(for: key) {
            image = cached
            state = .ready
            return
        }

        state = .rendering
        task = Task { [weak self] in
            let rendered = await render()
            // The cache write does NOT wait on the slot living.
            // Census Y-3's collapses are the presenter being torn
            // down and rebuilt by collection-model republishes
            // mid-render (WearIveRunCollectionModel.recordSettled
            // reassigns its @Published entries on every
            // non-identical settle, hero-only and weather-only
            // included, and the home view feeds it from four
            // onChange triggers under the open collection). If
            // that churn kills this slot mid-render, the finished
            // card STILL lands in the cache, and the re-presented
            // sheet is served instantly — the repeated spinner
            // stretches cannot recur. The render task is the
            // slot's own unstructured Task, never the day view's
            // .task, so view teardown cannot cancel it either.
            if let rendered {
                ShareCardImageCache.shared.store(rendered, for: key)
            }
            guard let self, self.key == key else { return }
            if let rendered {
                self.image = rendered
                self.state = .ready
            } else {
                self.state = .failed
            }
        }
    }

    /// GEN-6 (RW-31 §7): force a fresh render for `key`,
    /// evicting the cached raster first. The verify gate's retry
    /// path — a raster that failed inspection must not be
    /// re-served from the cache or the slot's ready state.
    func forceRequest(key: String, render: @escaping () async -> UIImage?) {
        ShareCardImageCache.shared.evict(key)
        if self.key == key, state == .ready {
            image = nil
            state = .idle
        }
        request(key: key, render: render)
    }

    /// Await the in-flight render, if any (tests; the UI reads
    /// `state` instead).
    func waitUntilSettled() async {
        await task?.value
    }
}

// MARK: - Face-preserving poster crop (GEN-5, RW-28 sharing fix)

/// Sol's Nov 5 sharing test: the Morning Poster center-cropped
/// the photo and cut the user's face off entirely — the runner
/// rendered headless. The crop contract that replaces the bare
/// center fill: the poster's 4:5 window must CONTAIN the face
/// (subject-aware when a face rect is known — Vision on the render
/// path; otherwise the head band at the top of the frame, where
/// every RunWear hero portrait keeps its head). Pure geometry, so
/// the gate pins the contract itself.
enum ShareCardCrop {
    /// The poster's aspect (width / height) — MorningPosterMetrics'
    /// canvas is 4:5.
    static let posterAspect: CGFloat = 4.0 / 5.0

    /// The crop rect (image coordinates) of aspect `posterAspect`
    /// that keeps `faceRectNormalized` (y-up Vision space, or a
    /// top-left-normalised rect converted by the caller through
    /// `flipY`) in frame with headroom. Without a face, the window
    /// anchors to the TOP of the image.
    static func cropRect(
        imageSize: CGSize,
        faceRectTopLeftNormalized: CGRect?
    ) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0 else { return .zero }
        // Window size: the largest rect of the poster aspect that
        // fits the image.
        var windowWidth = imageSize.width
        var windowHeight = windowWidth / posterAspect
        if windowHeight > imageSize.height {
            windowHeight = imageSize.height
            windowWidth = windowHeight * posterAspect
        }
        // Start top-anchored, horizontally centred on the face
        // centre (or the image centre).
        var originX = (imageSize.width - windowWidth) / 2
        var originY: CGFloat = 0
        if let face = faceRectTopLeftNormalized {
            let faceRect = CGRect(
                x: face.minX * imageSize.width,
                y: face.minY * imageSize.height,
                width: face.width * imageSize.width,
                height: face.height * imageSize.height
            )
            // Centre the window on the face, then clamp — and if
            // clamping would push the face out, pin the window so
            // the face's top keeps 4% headroom instead.
            originX = min(max(faceRect.midX - windowWidth / 2, 0), imageSize.width - windowWidth)
            originY = min(max(faceRect.midY - windowHeight / 2, 0), imageSize.height - windowHeight)
            if faceRect.minY < originY {
                originY = max(faceRect.minY - imageSize.height * 0.04, 0)
            }
            if faceRect.maxY > originY + windowHeight {
                originY = min(faceRect.maxY - windowHeight, imageSize.height - windowHeight)
            }
        }
        return CGRect(x: originX, y: originY, width: windowWidth, height: windowHeight)
    }

    /// Crop a UIImage to the poster window. Returns the input when
    /// cropping is impossible (the poster then renders the photo
    /// through HeroPhoto's top anchor — never a center crop).
    static func facePreservingCropped(
        _ image: UIImage,
        faceRectTopLeftNormalized: CGRect?
    ) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let imageSize = CGSize(width: cg.width, height: cg.height)
        let rect = cropRect(imageSize: imageSize, faceRectTopLeftNormalized: faceRectTopLeftNormalized)
        guard rect.width > 0, rect.height > 0,
              let cropped = cg.cropping(to: rect.integral) else { return image }
        return UIImage(cgImage: cropped, scale: image.scale, orientation: image.imageOrientation)
    }
}

// MARK: - Hero preparation for the poster

enum ShareCardImagePrep {
    /// The poster's longest rendered side. GEN-6 (RW-31 §6):
    /// the tallest export canvas is Stories at 1920 px, so the
    /// cap rises from the gen-3 1560 to 2048 — a hero is still
    /// downscaled BEFORE the render, off the main actor (the
    /// gen-3 main-thread lesson), but never below export size.
    static let posterMaxDimension: CGFloat = 2048

    static func downscaledForPoster(_ image: UIImage) -> UIImage {
        let size = image.size
        let longest = max(size.width, size.height)
        guard longest > posterMaxDimension else { return image }
        let factor = posterMaxDimension / longest
        let target = CGSize(width: size.width * factor, height: size.height * factor)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
    }
}

// MARK: - Anchor-based hero crop (GEN-6, RW-31 §4.3 — share path)
//
// The export path's crop: the window is computed from the face
// anchor by ShareAnchorCrop (ShareStudioModels) for the CHOSEN
// canvas's aspect — Feed, Stories, and Square each recompute
// it. This is the share/export path only; the day-view hero's
// own crop is a separate surface (Wave C's item) and is not
// touched here.

extension ShareAnchorCrop {
    /// Crop a UIImage to the anchor-computed window for
    /// `canvasAspect`. Returns the input when cropping is
    /// impossible (the poster then renders through HeroPhoto's
    /// top anchor — never a centre crop).
    static func anchoredCropped(
        _ image: UIImage,
        anchor: ShareFaceAnchor,
        canvasAspect: CGFloat
    ) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let imageSize = CGSize(width: cg.width, height: cg.height)
        let rect = cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: canvasAspect)
        guard rect.width > 0, rect.height > 0,
              let cropped = cg.cropping(to: rect.integral) else { return image }
        return UIImage(cgImage: cropped, scale: image.scale, orientation: image.imageOrientation)
    }
}

// MARK: - Export rendering (GEN-6, RW-31 §6/§7.1 — DA-16)
//
// The export is RENDERED at exact export pixels from the same
// composition the Studio previews — never a capture of an
// on-screen preview at whatever size the sheet happened to be.

enum ShareExportRenderer {
    /// Render `content` (composed at the format's canvas point
    /// size) at the format's render scale, landing exactly on
    /// the export pixel size.
    @MainActor
    static func render<Content: View>(_ content: Content, format: ShareExportFormat) -> UIImage? {
        let renderer = ImageRenderer(content: content)
        renderer.scale = format.renderScale
        return renderer.uiImage
    }
}

// MARK: - Raster verification (GEN-6, RW-31 §7.3 — DA-16)
//
// The finished raster is inspected BEFORE the system sheet
// opens: it must exist and decode, carry real image data at
// exactly the export size, sit inside sane brightness/variance
// bounds, and contain no uniform black or blank region —
// checked in a grid over the canvas and specifically at the
// caller's sample points (the face anchor's canvas position,
// each Kit Card garment tile). A uniform region is the exact
// signature of the black squares in Sol's gen-5 recording and
// of a photo that never drew. Any failure means the activity
// sheet NEVER opens.

enum ShareRasterVerifier {
    enum Failure: Equatable {
        case missingImage
        case wrongSize(expectedWidth: Int, expectedHeight: Int, actualWidth: Int, actualHeight: Int)
        case degenerateRaster(mean: Double, standardDeviation: Double)
        case uniformRegion(String)
    }

    struct Verdict: Equatable {
        let passed: Bool
        let failure: Failure?
        let meanLuminance: Double
        let standardDeviation: Double
    }

    /// Sane-bounds constants (0–255 luminance frame).
    static let minimumGlobalDeviation: Double = 3
    static let uniformMeanCeiling: Double = 10
    static let uniformMeanFloor: Double = 245
    static let uniformDeviationCeiling: Double = 2

    static func verify(
        image: UIImage?,
        format: ShareExportFormat,
        samplePoints: [CGPoint] = []
    ) -> Verdict {
        guard let image, let cg = image.cgImage else {
            return Verdict(passed: false, failure: .missingImage, meanLuminance: 0, standardDeviation: 0)
        }
        let expected = format.exportPixelSize
        guard cg.width == Int(expected.width), cg.height == Int(expected.height) else {
            return Verdict(
                passed: false,
                failure: .wrongSize(
                    expectedWidth: Int(expected.width), expectedHeight: Int(expected.height),
                    actualWidth: cg.width, actualHeight: cg.height),
                meanLuminance: 0, standardDeviation: 0)
        }
        // Decode check: the raster must round-trip through an
        // encoded form — a CGImage that cannot encode is not a
        // finished image.
        guard image.pngData() != nil else {
            return Verdict(passed: false, failure: .missingImage, meanLuminance: 0, standardDeviation: 0)
        }

        // Sample the raster down to a working grid and read its
        // luminance statistics.
        let gridWidth = 48
        let gridHeight = max(1, Int((Double(gridWidth) * Double(cg.height) / Double(cg.width)).rounded()))
        var pixels = [UInt8](repeating: 0, count: gridWidth * gridHeight * 4)
        let context = CGContext(
            data: &pixels, width: gridWidth, height: gridHeight,
            bitsPerComponent: 8, bytesPerRow: gridWidth * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        guard let context else {
            return Verdict(passed: false, failure: .missingImage, meanLuminance: 0, standardDeviation: 0)
        }
        context.draw(cg, in: CGRect(x: 0, y: 0, width: gridWidth, height: gridHeight))
        var luminance = [Double](repeating: 0, count: gridWidth * gridHeight)
        for index in 0..<(gridWidth * gridHeight) {
            let offset = index * 4
            luminance[index] = 0.299 * Double(pixels[offset])
                + 0.587 * Double(pixels[offset + 1])
                + 0.114 * Double(pixels[offset + 2])
        }
        let mean = luminance.reduce(0, +) / Double(luminance.count)
        let variance = luminance.reduce(0) { $0 + ($1 - mean) * ($1 - mean) } / Double(luminance.count)
        let deviation = variance.squareRoot()
        guard deviation >= minimumGlobalDeviation, mean > 3, mean < 252 else {
            return Verdict(passed: false, failure: .degenerateRaster(mean: mean, standardDeviation: deviation),
                           meanLuminance: mean, standardDeviation: deviation)
        }

        func regionStats(centerX: Int, centerY: Int, radius: Int) -> (mean: Double, deviation: Double) {
            var values: [Double] = []
            for y in max(0, centerY - radius)...min(gridHeight - 1, centerY + radius) {
                for x in max(0, centerX - radius)...min(gridWidth - 1, centerX + radius) {
                    values.append(luminance[y * gridWidth + x])
                }
            }
            guard !values.isEmpty else { return (mean, deviation) }
            let regionMean = values.reduce(0, +) / Double(values.count)
            let regionVariance = values.reduce(0) { $0 + ($1 - regionMean) * ($1 - regionMean) } / Double(values.count)
            return (regionMean, regionVariance.squareRoot())
        }
        func isUniformDead(_ stats: (mean: Double, deviation: Double)) -> Bool {
            stats.deviation < uniformDeviationCeiling
                && (stats.mean < uniformMeanCeiling || stats.mean > uniformMeanFloor)
        }

        // Grid scan: 6 columns × proportional rows of cells.
        let cellColumns = 6
        let cellRows = max(1, gridHeight * cellColumns / gridWidth)
        for row in 0..<cellRows {
            for column in 0..<cellColumns {
                let centerX = (column * 2 + 1) * gridWidth / (cellColumns * 2)
                let centerY = (row * 2 + 1) * gridHeight / (cellRows * 2)
                let radius = max(1, gridWidth / (cellColumns * 4))
                if isUniformDead(regionStats(centerX: centerX, centerY: centerY, radius: radius)) {
                    return Verdict(passed: false, failure: .uniformRegion("grid \(column),\(row)"),
                                   meanLuminance: mean, standardDeviation: deviation)
                }
            }
        }
        // Specific samples: the face anchor and each garment
        // tile must hold real image data.
        for point in samplePoints {
            let centerX = min(gridWidth - 1, max(0, Int(point.x * CGFloat(gridWidth))))
            let centerY = min(gridHeight - 1, max(0, Int(point.y * CGFloat(gridHeight))))
            if isUniformDead(regionStats(centerX: centerX, centerY: centerY, radius: 2)) {
                return Verdict(passed: false, failure: .uniformRegion("sample \(point)"),
                               meanLuminance: mean, standardDeviation: deviation)
            }
        }
        return Verdict(passed: true, failure: nil, meanLuminance: mean, standardDeviation: deviation)
    }
}
