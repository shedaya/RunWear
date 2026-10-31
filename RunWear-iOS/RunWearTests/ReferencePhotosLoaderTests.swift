import XCTest

/// RW-16 — the "Your photos" screen's loading contract, pinned
/// against the field failure recorded on build 1.0 (11) (two
/// installs, three recordings, 2026-10-31):
///
///   * first open sat on "Loading…" with no terminal state;
///   * second open rendered one empty placeholder box per photo,
///     no error, no retry — indistinguishable from loading;
///   * the Settings card, reading the same profile through the
///     same session, showed the opted-in state correctly.
///
/// The loader must therefore: reach a terminal state within its
/// bound even when a call never answers; report a failed fetch or
/// failed signing as a FAILURE, never as "no photos on the
/// server"; and keep the genuine-empty and happy paths exact.
///
/// These tests were written against the extracted shipping
/// semantics first (thrown calls swallowed into .empty, no bound):
/// the three failure tests fail there with the recorded signatures
/// and pass once the loader honours its bound and its states.
final class ReferencePhotosLoaderTests: XCTestCase {

    /// Scriptable fetcher: each half of the seam is a closure, so a
    /// test can hang, throw, or answer per path.
    private final class FakeFetcher: ReferencePhotoFetching {
        var onFetch: () async throws -> [String]
        var onSign: (String) async throws -> URL?
        init(onFetch: @escaping () async throws -> [String],
             onSign: @escaping (String) async throws -> URL?) {
            self.onFetch = onFetch
            self.onSign = onSign
        }
        func fetchReferencePaths() async throws -> [String] { try await onFetch() }
        func signedReferenceURL(path: String) async throws -> URL? { try await onSign(path) }
    }

    private struct StubError: Error {}

    /// Poll (on the main actor) until the loader leaves .loading
    /// or the deadline passes; returns the state observed.
    @MainActor
    private func awaitTerminal(_ loader: ReferencePhotosLoader,
                               deadlineSeconds: Double) async -> ReferencePhotosLoader.State {
        let deadline = Date().addingTimeInterval(deadlineSeconds)
        while Date() < deadline {
            if loader.state != .loading { return loader.state }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        return loader.state
    }

    // A fetch that never answers must not leave the screen on
    // "Loading…" past the loader's bound (field signature #1).
    @MainActor
    func testHangingFetchReachesTerminalStateWithinBound() async {
        let fetcher = FakeFetcher(
            onFetch: {
                try await Task.sleep(nanoseconds: 30_000_000_000)
                return []
            },
            onSign: { _ in nil }
        )
        let loader = ReferencePhotosLoader(fetcher: fetcher, timeoutNanoseconds: 300_000_000)
        let task = Task { await loader.load() }
        let state = await awaitTerminal(loader, deadlineSeconds: 3)
        task.cancel()
        XCTAssertFalse(state == .loading,
                       "a hanging fetch left the loader in .loading past its bound")
        if case .loaded = state {
            XCTFail("a hanging fetch must not report loaded photos")
        }
    }

    // A thrown fetch is a failure, not an empty server (the
    // shipping code swallowed the throw into "No photos on the
    // server" while the server held three registered photos).
    @MainActor
    func testFetchErrorIsNotReportedAsEmptyServer() async {
        let fetcher = FakeFetcher(
            onFetch: { throw StubError() },
            onSign: { _ in nil }
        )
        let loader = ReferencePhotosLoader(fetcher: fetcher, timeoutNanoseconds: 300_000_000)
        await loader.load()
        let state = await awaitTerminal(loader, deadlineSeconds: 3)
        XCTAssertFalse(state == .empty,
                       "a failed fetch was reported as an empty server")
        XCTAssertFalse(state == .loading,
                       "a failed fetch never reached a terminal state")
        if case .loaded = state {
            XCTFail("a failed fetch must not report loaded photos")
        }
    }

    // Paths exist but every signing fails: also a failure, never
    // "no photos" (field signature #2's URL half).
    @MainActor
    func testAllSignsFailingIsNotReportedAsEmptyServer() async {
        let fetcher = FakeFetcher(
            onFetch: { ["u/refs/ref-1.jpg", "u/refs/ref-2.jpg", "u/refs/ref-3.jpg"] },
            onSign: { _ in throw StubError() }
        )
        let loader = ReferencePhotosLoader(fetcher: fetcher, timeoutNanoseconds: 300_000_000)
        await loader.load()
        let state = await awaitTerminal(loader, deadlineSeconds: 3)
        XCTAssertFalse(state == .empty,
                       "failed signing for existing photos was reported as an empty server")
        XCTAssertFalse(state == .loading,
                       "failed signing never reached a terminal state")
        if case .loaded = state {
            XCTFail("photos whose URLs could not be made must not report as loaded")
        }
    }

    // Control: the happy path loads every URL, in path order.
    @MainActor
    func testHappyPathLoadsEveryPhotoInOrder() async {
        let fetcher = FakeFetcher(
            onFetch: { ["u/refs/ref-1.jpg", "u/refs/ref-2.jpg", "u/refs/ref-3.jpg"] },
            onSign: { path in URL(string: "https://example.test/\(path)") }
        )
        let loader = ReferencePhotosLoader(fetcher: fetcher, timeoutNanoseconds: 300_000_000)
        await loader.load()
        guard case .loaded(let urls) = loader.state else {
            return XCTFail("expected .loaded, got \(loader.state)")
        }
        XCTAssertEqual(urls.map { $0.absoluteString }, [
            "https://example.test/u/refs/ref-1.jpg",
            "https://example.test/u/refs/ref-2.jpg",
            "https://example.test/u/refs/ref-3.jpg"
        ])
    }

    // Control: a genuinely empty server set is .empty — the state
    // the failure tests above must not be confused with.
    @MainActor
    func testGenuinelyEmptyServerSetReportsEmpty() async {
        let fetcher = FakeFetcher(onFetch: { [] }, onSign: { _ in nil })
        let loader = ReferencePhotosLoader(fetcher: fetcher, timeoutNanoseconds: 300_000_000)
        await loader.load()
        XCTAssertTrue(loader.state == .empty,
                      "an empty server set should report .empty, got \(loader.state)")
    }
}
