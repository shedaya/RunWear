import Foundation
import Combine
import UIKit

// MARK: - RW-16.9 "Your photos" tile loading
//
// The layer the RW-16.1 fix never reached. ReferencePhotosLoader
// bounds the path list + signing and hands the view signed URLs;
// in build (12) each tile then rendered through a bare SwiftUI
// AsyncImage whose in-flight phase is the same faint box build
// (11) used for failure, with no timeout, no progress, and no
// label — so a stalled or trickling multi-MB transfer (Sol's
// registered references run 1.9–5.5 MB) parked the tile in that
// box indefinitely, and every sheet re-presentation (RW-16.7)
// recreated the AsyncImages and restarted the transfers from
// zero. Field-verified on 1.0 (12), two installs, one recording
// (rw16-9-build12-field-failure-2026-10-31.md).
//
// This file extracts the tile load behind the same testable-seam
// discipline as ReferencePhotosLoader: a per-tile loader fed by a
// ReferenceImageDownloading seam, whose contract is the one
// RW-16.1 stated for the whole surface — every load reaches a
// visible terminal state within a bounded time, and no state of
// the tile renders as the silent faint box.
//
// FIX (RW-16.9): the loader races every download against its
// bound and cancels the loser, so a stalled or trickling
// transfer reaches the failure phase inside the envelope
// instead of parking; decoded images are cached for the
// session (ReferencePhotoImageCache) so re-renders and sheet
// re-presentations render completed tiles instantly instead of
// restarting their transfers from zero. The reproduction
// commit above this one carried the shipping semantics
// (unbounded await, as AsyncImage awaits) and failed the
// stall-transport contract tests with the device signature.

protocol ReferenceImageDownloading {
    func downloadImageData(from url: URL) async throws -> Data
}

/// Production downloader: a plain URLSession data task. Any
/// total-time bound lives in the loader, not here — URLSession's
/// own request/resource timeouts are idle-gap based (the
/// RW-16.9 parking mechanism), so this type adds none.
struct URLSessionReferenceImageDownloader: ReferenceImageDownloading {
    let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func downloadImageData(from url: URL) async throws -> Data {
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw ReferenceImageDownloadError.badResponse
        }
        return data
    }
}

enum ReferenceImageDownloadError: Error {
    case badResponse
    case undecodable
}

/// Session cache of decoded reference photos (RW-16.9 fix
/// direction 2): a re-presented sheet — RW-16.7 churn recreates
/// the tile views — must re-render completed tiles instantly
/// instead of restarting multi-MB transfers from zero.
///
/// Keyed by the signed URL's PATH, not the full URL: the
/// backend re-mints the signature token on every sign, so the
/// full URL changes between loads while the path keeps naming
/// the same stored object. The mutation points that can change
/// what a path holds — opt-in commit, photo replacement,
/// delete-everything — clear the cache at commit success (see
/// SettingsModal), so a replaced photo never serves stale.
@MainActor
final class ReferencePhotoImageCache {
    static let shared = ReferencePhotoImageCache()

    private var images: [String: UIImage] = [:]

    static func key(for url: URL) -> String { url.path }

    func image(for url: URL) -> UIImage? {
        images[Self.key(for: url)]
    }

    func store(_ image: UIImage, for url: URL) {
        images[Self.key(for: url)] = image
    }

    func removeAll() {
        images.removeAll()
    }
}

@MainActor
final class ReferenceTileLoader: ObservableObject {
    enum Phase {
        case idle
        case loading
        case loaded(UIImage)
        case failed
    }

    @Published private(set) var phase: Phase = .idle

    let url: URL
    private let downloader: ReferenceImageDownloading
    private let cache: ReferencePhotoImageCache
    /// The bound a single tile load must reach a terminal state
    /// within — a TOTAL bound enforced by racing the download
    /// and cancelling the loser, not URLSession's idle-gap
    /// timeout (which a trickling transfer never trips).
    private let timeoutNanoseconds: UInt64
    private var loadTask: Task<Void, Never>?

    init(
        url: URL,
        downloader: ReferenceImageDownloading = URLSessionReferenceImageDownloader(),
        cache: ReferencePhotoImageCache = .shared,
        timeoutNanoseconds: UInt64 = 20_000_000_000
    ) {
        self.url = url
        self.downloader = downloader
        self.cache = cache
        self.timeoutNanoseconds = timeoutNanoseconds
    }

    private struct LoadTimeout: Error {}

    /// Start the load unless one is already in flight or the
    /// image is on screen. Safe to call from onAppear on every
    /// re-render: an in-flight load is never restarted, and a
    /// cached image renders without any network at all.
    func loadIfNeeded() {
        if case .loaded = phase { return }
        guard loadTask == nil else { return }
        if let cached = cache.image(for: url) {
            phase = .loaded(cached)
            return
        }
        phase = .loading
        let downloader = self.downloader
        let cache = self.cache
        let url = self.url
        let timeout = self.timeoutNanoseconds
        loadTask = Task { @MainActor in
            do {
                // Download + decode inside the raced work, off
                // the main actor — decoding a 5 MB JPEG is not
                // main-thread work.
                let image = try await Self.runWithTimeout(timeout) {
                    let data = try await downloader.downloadImageData(from: url)
                    guard let image = UIImage(data: data) else {
                        throw ReferenceImageDownloadError.undecodable
                    }
                    return image
                }
                cache.store(image, for: url)
                phase = .loaded(image)
            } catch {
                phase = .failed
            }
            loadTask = nil
        }
    }

    /// Retry a failed tile: starts a fresh load for this tile
    /// only (build (12) bumped a view-wide token that restarted
    /// every tile's AsyncImage).
    func retry() {
        guard case .failed = phase else { return }
        loadTask = nil
        loadIfNeeded()
    }

    /// Race the work against the loader's bound; whichever
    /// finishes first decides, and the loser is cancelled —
    /// cancelling the download task cancels the underlying
    /// URLSession transfer, so a stalled download does not keep
    /// running underneath the failure tile. (Twin of
    /// ReferencePhotosLoader's race, kept beside its caller.)
    nonisolated private static func runWithTimeout<T>(
        _ timeoutNanoseconds: UInt64,
        _ work: @escaping () async throws -> T
    ) async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await work() }
            group.addTask {
                try await Task.sleep(nanoseconds: timeoutNanoseconds)
                throw LoadTimeout()
            }
            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }
}
