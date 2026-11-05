import Foundation

/// WS-7 / RW-26(c) — serve-source telemetry (D-18's attribution fix).
///
/// Every hero serve emits exactly one `HeroServeRecord` here, at
/// serve time, naming its source (pool / bundled-fallback / personal
/// / scaffold), the cell and gender where applicable, the combination
/// served, and — for a bundled serve — the asset file and its
/// manifest provenance (combination_id + source row + rung, D-22).
/// Before WS-7 the app recorded no serve source at all: Sol's
/// walkthrough hero was the bundled snow asset on a clear morning
/// and no trigger could be attributed after the fact. That failure
/// is what this closes. Mirror of Android's HeroServeTelemetry.
///
/// Transport (the app's existing diagnostics/feedback channel
/// patterns):
/// 1. The record's single-line `summary()` is written to the console
///    log — the same unstructured log channel the hero cascade
///    already narrates itself on (`HeroImageService: Failed to
///    fetch pattern …`) — so a serve is attributable in any captured
///    device log.
/// 2. Records are retained in a bounded in-memory history
///    (`recent()`), which tests assert against and a diagnostics
///    capture can read.
/// 3. The app layer additionally files every BUNDLED-FALLBACK serve
///    remotely under the automated `diagnostics` category (RW-18's
///    client half: automated filings file under `diagnostics`, never
///    a human category) — see `WeatherViewModel.recordHeroServe` and
///    `HeroServeDiagnostics`. Pool and personal serves are already
///    attributable server-side by row id / hero id, so they are not
///    re-filed; the bundled serve is the one that previously left no
///    trace anywhere, and it is the one that now files.
///
/// The sink is replaceable (`installSink`) so tests capture records
/// without touching the log; production uses `defaultSink`.
enum HeroServeTelemetry {
    /// Receives one serve record. Must never throw into the serve path.
    typealias Sink = (HeroServeRecord) -> Void

    /// The production sink: the hero cascade's console log channel.
    static let defaultSink: Sink = { record in
        print("[HeroServe] \(record.summary())")
    }

    private static let lock = NSLock()
    private static var sink: Sink = defaultSink
    private static var history: [HeroServeRecord] = []
    private static let maxHistory = 64

    /// Install a sink (tests). Pass `defaultSink` to restore.
    static func installSink(_ newSink: @escaping Sink) {
        lock.lock()
        sink = newSink
        lock.unlock()
    }

    /// Emit one serve record: retained in history, then handed to the sink.
    static func emit(_ record: HeroServeRecord) {
        let currentSink: Sink
        lock.lock()
        history.append(record)
        while history.count > maxHistory { history.removeFirst() }
        currentSink = sink
        lock.unlock()
        currentSink(record)
    }

    /// The retained serve records, oldest first (bounded, see `emit`).
    static func recent() -> [HeroServeRecord] {
        lock.lock()
        defer { lock.unlock() }
        return history
    }

    /// Clear retained history (tests).
    static func clear() {
        lock.lock()
        history.removeAll()
        lock.unlock()
    }

    /// Emit the record for a serving-layer `HeroImageResult` — the
    /// pool cascade's serves and the bundled results the provider
    /// returns. `trigger` names the path that produced the result
    /// (e.g. "pool-cascade", "bundled-fallback", "personal-overlay").
    /// A result whose image is a bundled asset is a bundled serve
    /// whatever the caller labelled it — the URL is the ground truth.
    static func emitForResult(
        _ result: HeroImageResult,
        requestedCombinationId: String?,
        bracket: HeroTempBracket?,
        weather: HeroWeatherCondition?,
        gender: GenderPreference?,
        trigger: String
    ) {
        let url = result.imageUrl
        guard !url.isEmpty else { return }
        let isBundled = FallbackImageProvider.isBundledURL(url)
        let source: HeroServeSource
        // GEN-4: a scaffold result keeps the scaffold source even
        // when its photo is a bundled asset — the bundled-URL
        // ground truth below must not relabel a scaffold serve as
        // an (exact-claiming) bundled-fallback serve.
        if result.fallbackTier == .scaffold {
            source = .scaffold
        } else if isBundled {
            source = .bundledFallback
        } else if let labelled = result.serveSource {
            source = labelled
        } else {
            source = .pool
        }
        emit(
            HeroServeRecord(
                source: source,
                requestedCombinationId: requestedCombinationId,
                servedCombinationId: result.combinationId,
                imageRowId: result.imageRowId,
                tempBracket: bracket,
                weatherCondition: weather,
                gender: gender,
                fallbackAssetFile: isBundled
                    ? (result.fallbackAssetFile ?? FallbackImageProvider.assetFile(fromURLString: url))
                    : result.fallbackAssetFile,
                fallbackProvenance: isBundled
                    ? (result.fallbackProvenance ?? FallbackImageProvider.provenance(forURLString: url))
                    : result.fallbackProvenance,
                trigger: trigger,
                fallbackTier: result.fallbackTier
            )
        )
    }
}

/// WS-7 / RW-26(c): the remote half of serve-source telemetry.
///
/// A bundled-fallback serve is the one hero serve that leaves no
/// trace anywhere else — no pool row, no server hero record — and it
/// is the serve D-18 could not attribute after the fact (Sol's
/// walkthrough hero was the bundled snow asset on a clear morning,
/// trigger unknown). So when the final paint is a bundled serve, the
/// app files its `HeroServeRecord` as an AUTOMATED report under the
/// `diagnostics` category — RW-18's routing for automated filings,
/// never a human feedback category — through the same
/// `FeedbackPayloads` shape and feedback-endpoint door as every
/// other automated filing on Android (`TelemetryPayloads.feedback` +
/// `sendFeedbackNow`). Pool / personal / scaffold serves are NOT
/// filed: they are attributable server-side by row id / hero id, and
/// their serve records ride the on-device HeroServe channel
/// (`HeroServeTelemetry`) like every serve.
///
/// The filing is best-effort and fire-and-forget: a failed send is
/// dropped, never queued — a stale bundled-serve report retried days
/// later would misattribute, which is worse than a missing one.
/// Mirror of Android's HeroServeDiagnostics.
enum HeroServeDiagnostics {
    /// The diagnostics payload for one bundled serve record.
    static func payloadData(record: HeroServeRecord, appVersion: String, installId: String) -> Data? {
        // The bundle rides the intake's strict allowlist
        // (runwear-feedback/1: unknown bundle keys are rejected with
        // 422 — the same constraint Android's diagnostics filings
        // observe), so the bundle carries only the allowlisted
        // engineVersion and EVERY serve fact rides in the text: the
        // record's summary names source, cell, gender, combinations,
        // asset, provenance combination + row, rung, and trigger.
        FeedbackPayloads.feedbackData(
            appVersion: appVersion,
            installId: installId,
            categoryId: FeedbackRouting.diagnosticsCategory,
            text: record.summary(),
            replyEmail: nil,
            entryPoint: "hero_serve",
            bundle: ["engineVersion": OutfitRecommendationService.engineVersion]
        )
    }

    /// File one bundled serve's record remotely. Fire-and-forget:
    /// the send runs on a detached task and every failure is dropped.
    static func file(record: HeroServeRecord) {
        guard record.source == .bundledFallback,
              let data = payloadData(
                  record: record,
                  appVersion: HeroFlagService.appVersion ?? "unknown",
                  installId: FeedbackSubmissionService.installId()
              ),
              let url = URL(string: FeedbackRouting.Destination.feedback.urlString) else {
            return
        }
        Task {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = data
            _ = try? await URLSession.shared.data(for: request)
        }
    }
}
