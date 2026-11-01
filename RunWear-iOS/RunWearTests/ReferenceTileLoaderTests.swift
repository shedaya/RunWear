import XCTest
import UIKit

/// RW-16.9 — the "Your photos" TILE loading contract, pinned
/// against the field failure recorded on build 1.0 (12)
/// (rw16-9-build12-field-failure-2026-10-31.md): the RW-16.1
/// loader bounded the path list + signing, but each tile then
/// rendered through a bare AsyncImage whose in-flight phase is
/// the identical faint box build (11) used for failure — no
/// timeout, no progress, no label — so stalled or trickling
/// multi-MB transfers parked the grid on blank boxes for the
/// whole screen visit, on two installs.
///
/// The RW-16.1 lesson is binding here: that fix's timeout race
/// was "proven" by a contract test whose stub could not actually
/// hang, so it passed against the unfixed code too. These tests
/// therefore run against a REAL stalling transport — a
/// URLProtocol that drip-feeds a multi-megabyte JPEG slower than
/// the loader's bound, and one that delivers a first chunk and
/// then nothing — through a real URLSession, never a fake clock
/// or a stubbed-away download. The fixture image is generated
/// in-test and asserted to exceed 2 MB: Sol's registered
/// references run 1.9–5.5 MB, and transfer size is part of the
/// failure signature.
///
/// Written against the extracted shipping semantics first (the
/// download awaited unbounded, as AsyncImage awaits it): the
/// three stall-contract tests fail there — the loader is still
/// parked when the deadline passes — and the mechanism test
/// documents WHY (a raw unbounded session load does not
/// complete against the drip). After the fix the same tests
/// pass: the loader reaches its terminal failure inside its
/// bound and cancels the stalled transfer.
final class ReferenceTileLoaderTests: XCTestCase {

    // MARK: - Stalling transport

    private enum StallMode { case fast, drip, silent }

    private final class StallState {
        private let lock = NSLock()
        private var _bytesSent = 0
        private var _stopLoadingCalled = false
        private var _finished = false
        var bytesSent: Int { lock.lock(); defer { lock.unlock() }; return _bytesSent }
        var stopLoadingCalled: Bool { lock.lock(); defer { lock.unlock() }; return _stopLoadingCalled }
        var finished: Bool { lock.lock(); defer { lock.unlock() }; return _finished }
        func addBytes(_ n: Int) { lock.lock(); _bytesSent += n; lock.unlock() }
        func markStopped() { lock.lock(); _stopLoadingCalled = true; lock.unlock() }
        func markFinished() { lock.lock(); _finished = true; lock.unlock() }
        var isStopped: Bool { lock.lock(); defer { lock.unlock() }; return _stopLoadingCalled }
    }

    private final class StallFixture {
        static let lock = NSLock()
        static var fixtures: [String: (data: Data, mode: StallMode)] = [:]
        static var states: [String: StallState] = [:]

        static func register(path: String, data: Data, mode: StallMode) {
            lock.lock()
            fixtures[path] = (data, mode)
            states[path] = StallState()
            lock.unlock()
        }
        static func fixture(for path: String) -> (data: Data, mode: StallMode)? {
            lock.lock(); defer { lock.unlock() }; return fixtures[path]
        }
        static func state(for path: String) -> StallState? {
            lock.lock(); defer { lock.unlock() }; return states[path]
        }
        static func reset() {
            lock.lock(); fixtures = [:]; states = [:]; lock.unlock()
        }
    }

    private final class StallingURLProtocol: URLProtocol {
        private let queue = DispatchQueue(label: "rw16.stall-protocol")
        private var offset = 0
        private static let chunkSize = 32_768
        private static let chunkInterval: TimeInterval = 0.2

        override class func canInit(with request: URLRequest) -> Bool {
            request.url?.host == "stall.test"
        }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

        override func startLoading() {
            guard let url = request.url,
                  let fixture = StallFixture.fixture(for: url.path),
                  let state = StallFixture.state(for: url.path) else {
                client?.urlProtocol(self, didFailWithError: URLError(.fileDoesNotExist))
                return
            }
            let response = HTTPURLResponse(
                url: url, statusCode: 200, httpVersion: "HTTP/1.1",
                headerFields: [
                    "Content-Type": "image/jpeg",
                    "Content-Length": "\(fixture.data.count)"
                ]
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            switch fixture.mode {
            case .fast:
                state.addBytes(fixture.data.count)
                client?.urlProtocol(self, didLoad: fixture.data)
                state.markFinished()
                client?.urlProtocolDidFinishLoading(self)
            case .drip, .silent:
                deliverNext(fixture: fixture, state: state)
            }
        }

        private func deliverNext(fixture: (data: Data, mode: StallMode), state: StallState) {
            let isFirst = offset == 0
            queue.asyncAfter(deadline: .now() + (isFirst ? 0 : Self.chunkInterval)) { [weak self] in
                guard let self, !state.isStopped else { return }
                let end = min(self.offset + Self.chunkSize, fixture.data.count)
                let chunk = fixture.data.subdata(in: self.offset..<end)
                self.client?.urlProtocol(self, didLoad: chunk)
                state.addBytes(chunk.count)
                self.offset = end
                // .silent: the first chunk is the whole story —
                // headers and some bytes arrived, then nothing,
                // forever. The connection stays open.
                if fixture.mode == .silent { return }
                if self.offset < fixture.data.count {
                    self.deliverNext(fixture: fixture, state: state)
                } else {
                    state.markFinished()
                    self.client?.urlProtocolDidFinishLoading(self)
                }
            }
        }

        override func stopLoading() {
            guard let url = request.url else { return }
            StallFixture.state(for: url.path)?.markStopped()
        }
    }

    private func stallingSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StallingURLProtocol.self]
        return URLSession(configuration: config)
    }

    // MARK: - Fixture image (multi-MB, like Sol's references)

    /// A noise-filled JPEG: visually random blocks do not
    /// compress, so the encoded size stays in the multi-MB
    /// range of the real registered references (1.9–5.5 MB).
    private static let largeJPEG: Data = {
        let size = CGSize(width: 2000, height: 2400)
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            var rng = SystemRandomNumberGenerator()
            let block = 4
            for y in stride(from: 0, to: Int(size.height), by: block) {
                for x in stride(from: 0, to: Int(size.width), by: block) {
                    UIColor(
                        hue: CGFloat.random(in: 0...1, using: &rng),
                        saturation: 0.85,
                        brightness: 0.9,
                        alpha: 1
                    ).setFill()
                    context.fill(CGRect(x: x, y: y, width: block, height: block))
                }
            }
        }
        return image.jpegData(compressionQuality: 0.95) ?? Data()
    }()

    private static let smallJPEG: Data = {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
        let image = renderer.image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 64, height: 64))
        }
        return image.jpegData(compressionQuality: 0.9) ?? Data()
    }()

    override func setUp() {
        super.setUp()
        StallFixture.reset()
        XCTAssertGreaterThan(
            Self.largeJPEG.count, 2_000_000,
            "fixture must be multi-MB like the real references; got \(Self.largeJPEG.count) bytes"
        )
    }

    // MARK: - Phase helpers

    @MainActor
    private func phaseTag(_ loader: ReferenceTileLoader) -> String {
        switch loader.phase {
        case .idle: return "idle"
        case .loading: return "loading"
        case .loaded: return "loaded"
        case .failed: return "failed"
        }
    }

    @MainActor
    private func awaitTerminalTag(
        _ loader: ReferenceTileLoader,
        deadlineSeconds: Double
    ) async -> String {
        let deadline = Date().addingTimeInterval(deadlineSeconds)
        while Date() < deadline {
            let tag = phaseTag(loader)
            if tag == "loaded" || tag == "failed" { return tag }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        return phaseTag(loader)
    }

    @MainActor
    private func makeLoader(url: URL, session: URLSession) -> ReferenceTileLoader {
        ReferenceTileLoader(
            url: url,
            downloader: URLSessionReferenceImageDownloader(session: session),
            timeoutNanoseconds: 1_000_000_000 // 1 s bound for tests
        )
    }

    // MARK: - The stall contract (fails on shipping semantics)

    /// A transfer that delivers a multi-MB photo slower than the
    /// bound must reach the terminal FAILURE state inside the
    /// bound's envelope — on build (12) this exact shape parked
    /// the tile on the silent faint box for the entire visit.
    @MainActor
    func testDripFeedSlowerThanBoundReachesTerminalFailure() async {
        let path = "/drip.jpg"
        StallFixture.register(path: path, data: Self.largeJPEG, mode: .drip)
        let session = stallingSession()
        defer { session.invalidateAndCancel() }
        let loader = makeLoader(url: URL(string: "https://stall.test\(path)")!, session: session)

        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 5)

        let state = StallFixture.state(for: path)
        XCTAssertGreaterThan(state?.bytesSent ?? 0, 0,
                             "harness check: the stall transport delivered no bytes — protocol not honoured?")
        XCTAssertLessThan(state?.bytesSent ?? .max, Self.largeJPEG.count,
                          "harness check: the drip should still be mid-transfer when the loader terminates")
        XCTAssertEqual(tag, "failed",
                       "a drip-fed multi-MB transfer left the tile parked in .loading past its bound (the (12) field signature)")
    }

    /// A transfer that answers and then goes silent must also
    /// reach terminal failure inside the envelope — URLSession's
    /// idle-gap timeout (60 s) is not a bound the user can feel.
    @MainActor
    func testSilentTransferReachesTerminalFailure() async {
        let path = "/silent.jpg"
        StallFixture.register(path: path, data: Self.largeJPEG, mode: .silent)
        let session = stallingSession()
        defer { session.invalidateAndCancel() }
        let loader = makeLoader(url: URL(string: "https://stall.test\(path)")!, session: session)

        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 5)

        let state = StallFixture.state(for: path)
        XCTAssertGreaterThan(state?.bytesSent ?? 0, 0,
                             "harness check: the stall transport delivered no bytes — protocol not honoured?")
        XCTAssertFalse(state?.finished ?? true,
                       "harness check: the silent transfer should never finish")
        XCTAssertEqual(tag, "failed",
                       "a silent transfer left the tile parked in .loading past its bound (the (12) field signature)")
    }

    /// When the bound fires, the stalled transfer itself must be
    /// cancelled — not left running underneath a failure tile.
    @MainActor
    func testBoundFiringCancelsTheStalledTransfer() async {
        let path = "/drip-cancel.jpg"
        StallFixture.register(path: path, data: Self.largeJPEG, mode: .drip)
        let session = stallingSession()
        defer { session.invalidateAndCancel() }
        let loader = makeLoader(url: URL(string: "https://stall.test\(path)")!, session: session)

        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 5)
        XCTAssertEqual(tag, "failed", "precondition: the bound must fire first")

        let deadline = Date().addingTimeInterval(2)
        var stopped = false
        while Date() < deadline {
            if StallFixture.state(for: path)?.stopLoadingCalled == true { stopped = true; break }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        XCTAssertTrue(stopped,
                      "the loader reached failure but the stalled URLSession transfer was never cancelled")
    }

    // MARK: - Mechanism documentation (passes before and after)

    /// The primitive AsyncImage is built on — a bare URLSession
    /// load with no external bound — does not complete against
    /// the drip within several times the loader's bound: the
    /// idle-gap timeout never trips because bytes keep arriving.
    /// This is why the (12) tiles parked, stated as a test so the
    /// harness itself is verified honest: if the stall transport
    /// ever stops stalling, THIS test fails first.
    func testUnboundedSessionLoadParksAgainstDripFeed() async {
        let path = "/drip-raw.jpg"
        StallFixture.register(path: path, data: Self.largeJPEG, mode: .drip)
        let session = stallingSession()
        defer { session.invalidateAndCancel() }
        let url = URL(string: "https://stall.test\(path)")!

        final class Flag: @unchecked Sendable {
            private let lock = NSLock()
            private var _done = false
            var done: Bool { lock.lock(); defer { lock.unlock() }; return _done }
            func set() { lock.lock(); _done = true; lock.unlock() }
        }
        let flag = Flag()
        let task = Task {
            _ = try? await session.data(from: url)
            flag.set()
        }
        try? await Task.sleep(nanoseconds: 3_000_000_000)
        XCTAssertFalse(flag.done,
                       "the unbounded load completed against the drip — the stall harness is not stalling")
        let state = StallFixture.state(for: path)
        XCTAssertGreaterThan(state?.bytesSent ?? 0, 0,
                             "harness check: no bytes delivered — protocol not honoured?")
        XCTAssertLessThan(state?.bytesSent ?? .max, Self.largeJPEG.count,
                          "harness check: drip should still be mid-transfer")
        task.cancel()
    }

    /// Control: the same transport delivering at full speed
    /// loads the multi-MB image fine — the stall, not the size
    /// or the session, is what breaks the shipping semantics.
    @MainActor
    func testFastFullDeliveryLoadsTheMultiMBImage() async {
        let path = "/fast.jpg"
        StallFixture.register(path: path, data: Self.largeJPEG, mode: .fast)
        let session = stallingSession()
        defer { session.invalidateAndCancel() }
        let loader = makeLoader(url: URL(string: "https://stall.test\(path)")!, session: session)

        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 5)
        XCTAssertEqual(tag, "loaded", "a healthy full-speed multi-MB delivery should load")
        if case .loaded(let image) = loader.phase {
            XCTAssertGreaterThan(image.cgImage?.width ?? 0, 1000,
                                 "the loaded image should be the full-size fixture")
        }
    }

    // MARK: - Downloader-seam controls (fake, deterministic)

    private final class FakeDownloader: ReferenceImageDownloading {
        var handler: (URL) async throws -> Data
        private(set) var callCount = 0
        init(handler: @escaping (URL) async throws -> Data) { self.handler = handler }
        func downloadImageData(from url: URL) async throws -> Data {
            callCount += 1
            return try await handler(url)
        }
    }

    private struct StubError: Error {}

    @MainActor
    func testThrowingDownloaderReachesFailure() async {
        let loader = ReferenceTileLoader(
            url: URL(string: "https://example.test/a.jpg")!,
            downloader: FakeDownloader { _ in throw StubError() },
            timeoutNanoseconds: 1_000_000_000
        )
        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 3)
        XCTAssertEqual(tag, "failed")
    }

    @MainActor
    func testUndecodableDataReachesFailure() async {
        let loader = ReferenceTileLoader(
            url: URL(string: "https://example.test/b.jpg")!,
            downloader: FakeDownloader { _ in Data([0x00, 0x01, 0x02, 0x03]) },
            timeoutNanoseconds: 1_000_000_000
        )
        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 3)
        XCTAssertEqual(tag, "failed", "bytes that are not an image must fail, never render as loaded")
    }

    @MainActor
    func testRetryAfterFailureLoadsOnSecondAttempt() async {
        let fake = FakeDownloader { _ in throw StubError() }
        let loader = ReferenceTileLoader(
            url: URL(string: "https://example.test/c.jpg")!,
            downloader: fake,
            timeoutNanoseconds: 1_000_000_000
        )
        loader.loadIfNeeded()
        var tag = await awaitTerminalTag(loader, deadlineSeconds: 3)
        XCTAssertEqual(tag, "failed")
        fake.handler = { _ in Self.smallJPEG }
        loader.retry()
        tag = await awaitTerminalTag(loader, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded", "tapping retry on a failed tile must start a fresh load for that tile")
        XCTAssertEqual(fake.callCount, 2)
    }

    // MARK: - Session cache (RW-16.9 fix: no restart from zero)

    /// A tile whose photo already downloaded in this session
    /// renders from the cache with NO new download — even under
    /// a freshly re-minted signed URL (the backend re-signs on
    /// every Photos load, changing the token; the path is the
    /// photo's stable identity). This is what lets a
    /// re-presented sheet (RW-16.7 churn) show completed tiles
    /// instantly instead of restarting their transfers.
    @MainActor
    func testSecondLoaderForSamePathServesFromCacheWithoutDownloading() async {
        let cache = ReferencePhotoImageCache()
        let fake = FakeDownloader { _ in Self.smallJPEG }
        let first = ReferenceTileLoader(
            url: URL(string: "https://example.test/storage/v1/object/sign/personal-heroes/u/refs/ref-1.jpg?token=AAA")!,
            downloader: fake, cache: cache,
            timeoutNanoseconds: 1_000_000_000
        )
        first.loadIfNeeded()
        var tag = await awaitTerminalTag(first, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded")
        XCTAssertEqual(fake.callCount, 1)

        // A new loader (new view, re-presented sheet) for the
        // same photo under a DIFFERENT signed URL.
        let second = ReferenceTileLoader(
            url: URL(string: "https://example.test/storage/v1/object/sign/personal-heroes/u/refs/ref-1.jpg?token=BBB")!,
            downloader: fake, cache: cache,
            timeoutNanoseconds: 1_000_000_000
        )
        second.loadIfNeeded()
        tag = await awaitTerminalTag(second, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded")
        XCTAssertEqual(fake.callCount, 1,
                       "the second presentation must come from the session cache, not a fresh download")
    }

    /// The cache's invalidation contract: after removeAll (what
    /// the reference-mutation commits call), the same path
    /// downloads again — a replaced photo never serves stale.
    @MainActor
    func testCacheClearForcesAFreshDownload() async {
        let cache = ReferencePhotoImageCache()
        let fake = FakeDownloader { _ in Self.smallJPEG }
        let url = URL(string: "https://example.test/storage/v1/object/sign/personal-heroes/u/refs/ref-1.jpg?token=AAA")!
        let first = ReferenceTileLoader(
            url: url, downloader: fake, cache: cache,
            timeoutNanoseconds: 1_000_000_000
        )
        first.loadIfNeeded()
        var tag = await awaitTerminalTag(first, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded")

        cache.removeAll()
        let second = ReferenceTileLoader(
            url: url, downloader: fake, cache: cache,
            timeoutNanoseconds: 1_000_000_000
        )
        second.loadIfNeeded()
        tag = await awaitTerminalTag(second, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded")
        XCTAssertEqual(fake.callCount, 2,
                       "after the cache is cleared the tile must download again")
    }

    /// An in-flight load is joined, never duplicated or
    /// restarted, when loadIfNeeded is called again mid-flight
    /// (view re-renders call it on every appear).
    @MainActor
    func testLoadIfNeededDuringFlightStartsNoSecondDownload() async {
        let fake = FakeDownloader { _ in
            try await Task.sleep(nanoseconds: 600_000_000)
            return Self.smallJPEG
        }
        let loader = ReferenceTileLoader(
            url: URL(string: "https://example.test/d.jpg")!,
            downloader: fake, cache: ReferencePhotoImageCache(),
            timeoutNanoseconds: 5_000_000_000
        )
        loader.loadIfNeeded()
        loader.loadIfNeeded()
        loader.loadIfNeeded()
        let tag = await awaitTerminalTag(loader, deadlineSeconds: 3)
        XCTAssertEqual(tag, "loaded")
        XCTAssertEqual(fake.callCount, 1,
                       "re-renders during a load must not restart or duplicate it")
    }
}
