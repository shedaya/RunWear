import Foundation
import Combine

// MARK: - RW-16 "Your photos" loading
//
// The data path behind MakeItMePhotosView, extracted from the view
// so it can be unit-tested (the shipping implementation lived inline
// in the view's .task, unreachable by tests). The seam is the two
// PersonalHeroAPI calls the screen makes: the reference-path list
// (PostgREST) and one storage sign per path.
//
// RW-16 field signature (build 1.0 (11), two installs, three
// recordings): the screen either sat on "Loading…" with no terminal
// state, or rendered one faint box per photo forever — every failure
// mode (thrown fetch, failed sign, failed image load, stalled
// network) collapsed into the same silent, unbounded, unretryable
// display, while the Settings card one level up — reading the same
// profile through the same session — rendered the opted-in state
// correctly. The loader's contract is therefore: every load reaches
// a terminal state within a bounded time, a failure is reported as
// a failure (never as "no photos"), and the view can retry.

protocol ReferencePhotoFetching {
    func fetchReferencePaths() async throws -> [String]
    func signedReferenceURL(path: String) async throws -> URL?
}

@MainActor
final class ReferencePhotosLoader: ObservableObject {
    enum State: Equatable {
        case idle
        case loading
        case loaded([URL])
        case empty
    }

    @Published private(set) var state: State = .idle

    private let fetcher: ReferencePhotoFetching
    /// The bound a single load must reach a terminal state within.
    /// Accepted by the initializer; the extraction (v1) preserves
    /// the shipping behavior, which had NO bound — see load().
    private let timeoutNanoseconds: UInt64

    init(fetcher: ReferencePhotoFetching, timeoutNanoseconds: UInt64 = 20_000_000_000) {
        self.fetcher = fetcher
        self.timeoutNanoseconds = timeoutNanoseconds
    }

    /// v1 — the shipping semantics, verbatim from the view's .task:
    /// a thrown fetch is swallowed (and then reported as .empty,
    /// i.e. "No photos on the server"), a failed sign silently
    /// drops that photo, and nothing bounds the wait.
    func load() async {
        state = .loading
        var urls: [URL] = []
        if let paths = try? await fetcher.fetchReferencePaths() {
            for path in paths {
                if let url = try? await fetcher.signedReferenceURL(path: path) {
                    urls.append(url)
                }
            }
        }
        state = urls.isEmpty ? .empty : .loaded(urls)
    }
}
