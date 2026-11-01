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
        case failed(String)
    }

    @Published private(set) var state: State = .idle

    private let fetcher: ReferencePhotoFetching
    /// The bound a single load must reach a terminal state within.
    private let timeoutNanoseconds: UInt64

    init(fetcher: ReferencePhotoFetching, timeoutNanoseconds: UInt64 = 20_000_000_000) {
        self.fetcher = fetcher
        self.timeoutNanoseconds = timeoutNanoseconds
    }

    private struct LoadTimeout: Error {}
    private struct AllSignsFailed: Error {}

    /// RW-16 fix: the load is bounded by `timeoutNanoseconds`,
    /// a thrown fetch is reported as a failure (never as an empty
    /// server), paths whose signing fails are dropped only when at
    /// least one other photo signs (all failing is a failure), and
    /// the caller can retry by calling load() again.
    func load() async {
        state = .loading
        do {
            let urls = try await runWithTimeout { try await self.collectURLs() }
            state = urls.isEmpty ? .empty : .loaded(urls)
        } catch is LoadTimeout {
            state = .failed("Loading your photos took too long. Check your connection and try again.")
        } catch {
            state = .failed("Couldn't load your photos. Please try again.")
        }
    }

    /// Fetch the path list, then sign every path in parallel,
    /// preserving path order. A sign that throws or returns nil
    /// drops that one photo; if paths exist but none sign, the
    /// whole load fails — the server demonstrably has photos, so
    /// reporting "no photos" would be a lie.
    nonisolated private func collectURLs() async throws -> [URL] {
        let paths = try await fetcher.fetchReferencePaths()
        if paths.isEmpty { return [] }
        let fetcher = self.fetcher
        let signed: [URL?] = await withTaskGroup(of: (Int, URL?).self) { group in
            for (index, path) in paths.enumerated() {
                group.addTask {
                    (index, (try? await fetcher.signedReferenceURL(path: path)) ?? nil)
                }
            }
            var out = [URL?](repeating: nil, count: paths.count)
            for await (index, url) in group {
                out[index] = url
            }
            return out
        }
        let urls = signed.compactMap { $0 }
        if urls.isEmpty { throw AllSignsFailed() }
        return urls
    }

    /// Race the work against the loader's bound; whichever finishes
    /// first decides, and the loser is cancelled.
    nonisolated private func runWithTimeout<T>(
        _ work: @escaping () async throws -> T
    ) async throws -> T {
        let timeout = self.timeoutNanoseconds
        return try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await work() }
            group.addTask {
                try await Task.sleep(nanoseconds: timeout)
                throw LoadTimeout()
            }
            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }
}
