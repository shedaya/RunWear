import Combine
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
            guard let self, self.key == key else { return }
            if let rendered {
                ShareCardImageCache.shared.store(rendered, for: key)
                self.image = rendered
                self.state = .ready
            } else {
                self.state = .failed
            }
        }
    }

    /// Await the in-flight render, if any (tests; the UI reads
    /// `state` instead).
    func waitUntilSettled() async {
        await task?.value
    }
}

// MARK: - Hero preparation for the poster

enum ShareCardImagePrep {
    /// The poster's longest rendered side: 520 pt at the
    /// renderer's 3x scale. A hero larger than this is downscaled
    /// BEFORE the render, off the main actor — rasterizing the
    /// full-resolution download inside ImageRenderer was part of
    /// the main-thread cost of the old path.
    static let posterMaxDimension: CGFloat = 1560

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
