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
// REPRODUCTION STATE (this commit): the loader below carries the
// SHIPPING semantics — the download is awaited unbounded, exactly
// as AsyncImage awaits it (URLSession's only timeout is the
// idle-gap timeout, which a trickling transfer never trips).
// The timeout parameter exists at the seam but is NOT honoured
// yet. ReferenceTileLoaderTests pins the contract against a
// genuinely stalling transport and fails here with the recorded
// device signature; the fix commit makes the loader honour its
// bound and adds the session image cache.

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
    /// The bound a single tile load must reach a terminal state
    /// within. Accepted at the seam; the shipping semantics in
    /// this commit do not yet honour it (see the file header).
    private let timeoutNanoseconds: UInt64
    private var loadTask: Task<Void, Never>?

    init(
        url: URL,
        downloader: ReferenceImageDownloading = URLSessionReferenceImageDownloader(),
        timeoutNanoseconds: UInt64 = 20_000_000_000
    ) {
        self.url = url
        self.downloader = downloader
        self.timeoutNanoseconds = timeoutNanoseconds
    }

    /// Start the load unless one is already in flight or the
    /// image is on screen. Safe to call from onAppear on every
    /// re-render: an in-flight load is never restarted.
    func loadIfNeeded() {
        if case .loaded = phase { return }
        guard loadTask == nil else { return }
        phase = .loading
        let downloader = self.downloader
        let url = self.url
        loadTask = Task { @MainActor in
            do {
                let data = try await downloader.downloadImageData(from: url)
                if let image = UIImage(data: data) {
                    phase = .loaded(image)
                } else {
                    phase = .failed
                }
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
}
