import Foundation
import Combine

// MARK: - RW-14 "Make It Me" — pure model layer
//
// Foundation-only on purpose: this file is compiled into BOTH the app
// target and the RunWearTests target (the test target's established
// pattern of compiling app files directly), so the combination-id,
// cache-version, upsell-eligibility and photo-verdict logic is unit
// tested against the backend contract's own worked examples.
//
// Backend contract: rw14-backend-build-2026-10-31.md §2.
// combination_id = {GENDER}_{WEATHER}_{TEMP_BRACKET}_{TIME_OF_DAY}_{OUTFIT_HASH}
// where OUTFIT_HASH is the Kotlin/Java String.hashCode() (31-based over
// UTF-16 code units) of the sorted ClothingItem keys joined with "-",
// rendered unsigned lowercase hex, last 8 chars zero-padded — identical
// to OutfitCombination.fromConditions in the shared engine.

// MARK: - Outfit signature hash

enum OutfitSignatureHash {
    /// Java/Kotlin String.hashCode(): h = 31*h + unit over UTF-16 code
    /// units, Int32 wraparound. Rendered as unsigned hex, last 8 chars,
    /// zero-padded — the engine's `toUInt().toString(16).takeLast(8)
    /// .padStart(8, '0')`.
    static func hash(itemKeys: [String]) -> String {
        let joined = itemKeys.sorted().joined(separator: "-")
        var h: Int32 = 0
        for unit in joined.utf16 {
            h = h &* 31 &+ Int32(unit)
        }
        let hex = String(UInt32(bitPattern: h), radix: 16)
        let padded = String(repeating: "0", count: max(0, 8 - hex.count)) + hex
        return String(padded.suffix(8))
    }
}

// MARK: - Personal combination

/// A personalized-hero cell: the pool's condition frame plus the outfit
/// signature, keyed stably per user.
struct PersonalCombination: Equatable, Hashable {
    let genderSegment: String
    let weather: HeroWeatherCondition
    let temp: HeroTempBracket
    let time: HeroTimeOfDay
    let outfitItems: [String]

    var outfitHash: String { OutfitSignatureHash.hash(itemKeys: outfitItems) }

    var combinationId: String {
        "\(genderSegment)_\(weather.rawValue)_\(temp.rawValue)_\(time.rawValue)_\(outfitHash)"
    }

    /// The gender segment for personal keying comes from the user's
    /// stated preference — MALE / FEMALE / UNISEX, the engine's own
    /// GenderPreference names. Deliberately NOT GenderPreference
    /// .forHeroImage(), whose unisex case randomizes per call: a
    /// personalized cell must key identically on every lookup.
    static func genderSegment(for preference: GenderPreference) -> String {
        switch preference {
        case .male: return "MALE"
        case .female: return "FEMALE"
        case .unisex: return "UNISEX"
        }
    }

    /// Build the cell for the currently displayed recommendation,
    /// from the SAME keying frame the pool hero and the
    /// recommendation use — `WeatherViewModel.prepareHeroFrame()`
    /// (comfort-adjusted feels-like, weather code, hour) — so a
    /// personalized photo is keyed to the exact outfit on screen.
    /// (The backend's plain `fromConditions` buckets the raw
    /// feels-like; this client follows the app's hero contract
    /// because the cell means "this recommendation". The choice is
    /// named in the RW-14 build report.)
    static func make(
        genderPreference: GenderPreference,
        weatherCode: Int,
        feelsLikeFahrenheit: Double,
        hour: Int,
        outfit: OutfitRecommendation
    ) -> PersonalCombination {
        PersonalCombination(
            genderSegment: genderSegment(for: genderPreference),
            weather: HeroWeatherCondition.from(weatherCode: weatherCode),
            temp: HeroTempBracket.from(feelsLikeTemperature: feelsLikeFahrenheit),
            time: HeroTimeOfDay.from(hour: hour),
            outfitItems: outfit.allItems.map { $0.key }
        )
    }
}

// MARK: - Serving contract (view-model facing)

/// The personal-hero surface the view model consults. A protocol,
/// declared here in the Foundation-only layer, so WeatherViewModel
/// — which the test target compiles WITHOUT the store's
/// RevenueCat/SwiftUI stack — depends on the contract, never the
/// concrete store. `PersonalHeroStore` is the production
/// conformance.
protocol PersonalHeroResolving: AnyObject {
    var isOptedIn: Bool { get }
    /// The verified personalized hero URL for this cell, if one
    /// exists (or lands within the call's own short wait) — nil
    /// means "keep the pool photo".
    func resolveHero(for combination: PersonalCombination) async -> String?
    /// Polls for this cell's hero to land; nil on timeout — the
    /// caller simply keeps the pool photo (no error state).
    func awaitHero(for combination: PersonalCombination) async -> String?
    /// The id of the verified personalized hero currently held for
    /// this cell, if any — what a "This doesn't look like me" flag
    /// would report (RW-14 acceptance (19)).
    func personalHeroId(for combination: PersonalCombination) -> String?
    /// Flag the cell's personalized hero with the backend
    /// (flag_hero): on success the hero stops serving and a
    /// regeneration is queued server-side; returns whether the
    /// flag landed. Failure is silent — the screen is never
    /// disturbed by a flag that didn't land.
    func flagPersonalHero(combination: PersonalCombination, reason: PersonalHeroFlagReason) async -> Bool
    /// WS-7: the server's folded look state for this cell, when the
    /// resolver tracks one — the paint layer reads it to classify a
    /// personal serve for its serve-source record (scaffold while
    /// the exact look is still making, personal when it is ready).
    func lookState(for combination: PersonalCombination) -> PersonalLookState
    /// GEN-5 (Addendum 8 follow-up, Defect 1): hand the resolver
    /// the composed fill set — the forecast's cluster cells
    /// first, then the canonical core matrix — so a subscription
    /// fill can name it. Called on every forecast load; the
    /// resolver re-sends the fill when the named set changes
    /// under a live subscription.
    func updateForecastPriorityCells(_ cells: [String])
}

extension PersonalHeroResolving {
    /// Defaults so lightweight conformers (test doubles) carry no
    /// flag surface; PersonalHeroStore provides the real ones.
    func personalHeroId(for combination: PersonalCombination) -> String? { nil }
    func flagPersonalHero(combination: PersonalCombination, reason: PersonalHeroFlagReason) async -> Bool { false }
    func lookState(for combination: PersonalCombination) -> PersonalLookState { .none }
    func updateForecastPriorityCells(_ cells: [String]) {}
}

// MARK: - Backend DTOs

/// get-personal-hero response (backend §2.1; RW-16.16 added the
/// look-state fields in get-personal-hero v5 — additive, absent
/// from pre-v5 payloads).
struct PersonalHeroLookup: Decodable {
    let found: Bool
    let combinationId: String
    let heroId: String?
    let variant: Int?
    let imageUrl: String?
    let version: Int?
    let replenishmentQueued: Int?
    /// RW-16.16: the server's per-cell look state for this caller —
    /// "none" | "making" | "ready" | "failed"; nil on a pre-v5
    /// payload. NO default value here on purpose: a `let` with an
    /// initial value is excluded from Decodable synthesis entirely
    /// (the compiler warns "will not be decoded"), which would
    /// silently nil these on every payload — the exact failure
    /// LookStatusTests.testLookupDecodesLookStateAndReason caught.
    let lookState: String?
    /// RW-16.16: for a failed look, the coarse reason CLASS —
    /// "quality_refused" | "service_refused" | "error". The raw
    /// gate/moderation strings never cross the wire.
    let lookReason: String?

    enum CodingKeys: String, CodingKey {
        case found
        case combinationId = "combination_id"
        case heroId = "hero_id"
        case variant
        case imageUrl = "image_url"
        case version
        case replenishmentQueued = "replenishment_queued"
        case lookState = "look_state"
        case lookReason = "look_reason"
    }
}

// MARK: - Personal-look state (RW-16.16)
//
// The status the hero renders for one cell, as the SERVER reports
// it in get-personal-hero's look_state / look_reason (v5+). This —
// not any local bookkeeping — decides what the user sees: the
// once-per-session requested set once suppressed both re-requests
// AND the progress surface for a cell, so a generation the save
// flow had requested arrived (or failed) in total silence (the G4
// defect; spec §7 G4 superseded, G5/G6 added Nov 1, 2026).
//
// The server owns the truth: a look job in flight is MAKING; a
// verified hero serving with nothing in flight is READY (the hero
// itself is the state — no label); a terminal failure is FAILED
// with a coarse reason class; anything else is NONE. Mirrors
// Android's PersonalLookStatusPolicy exactly.

/// Why a look failed, at the coarse class the server exposes.
enum PersonalLookFailureReason: Equatable {
    /// The verification gate refused the generated photo.
    case qualityRefused
    /// The generation service refused (moderation class).
    case serviceRefused
    /// Anything else — provider errors, timeouts, the unknown.
    case error

    /// Map the wire class; an unknown or missing class degrades to
    /// `.error`, never to silence.
    static func fromServer(_ lookReason: String?) -> PersonalLookFailureReason {
        switch lookReason {
        case "quality_refused": return .qualityRefused
        case "service_refused": return .serviceRefused
        default: return .error
        }
    }
}

enum PersonalLookState: Equatable {
    case none
    case making
    case ready
    case failed(reason: PersonalLookFailureReason)

    var isMaking: Bool { self == .making }
    var isFailed: Bool { if case .failed = self { return true }; return false }

    /// Map one get-personal-hero payload onto the state. A payload
    /// with no state field at all (a pre-v5 server) falls back to
    /// the one inference that was always safe: found means READY, a
    /// miss means NONE. An unrecognized state string falls back the
    /// same way.
    static func fromServer(found: Bool, lookState: String?, lookReason: String?) -> PersonalLookState {
        switch lookState {
        case "making": return .making
        case "ready": return .ready
        case "failed": return .failed(reason: PersonalLookFailureReason.fromServer(lookReason))
        case "none": return .none
        default: return found ? .ready : .none
        }
    }

    /// The same mapping, from a decoded lookup.
    static func from(lookup: PersonalHeroLookup) -> PersonalLookState {
        fromServer(found: lookup.found, lookState: lookup.lookState, lookReason: lookup.lookReason)
    }
}

/// request-personal-generation success response (backend §2.2).
struct PersonalGenerationResponse: Decodable {
    let status: String // "already_available" | "already_queued" | "queued"
    let heroId: String?
    let jobId: String?
    let entitled: Bool?
    let freeGenerationsUsed: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case heroId = "hero_id"
        case jobId = "job_id"
        case entitled
        case freeGenerationsUsed = "free_generations_used"
    }
}

/// start-personal-fill response (backend §2.3).
struct PersonalFillResponse: Decodable {
    let ok: Bool
    let matrixCells: Int?
    let enqueued: Int?

    enum CodingKeys: String, CodingKey {
        case ok
        case matrixCells = "matrix_cells"
        case enqueued
    }
}

/// The user's own personal_profiles row, read via PostgREST under the
/// owner-SELECT policy (backend §1.1).
struct PersonalProfileRow: Decodable {
    let personalizationOn: Bool
    let freeGenerationsUsed: Int
    let entitled: Bool
    let gender: String?

    enum CodingKeys: String, CodingKey {
        case personalizationOn = "personalization_on"
        case freeGenerationsUsed = "free_generations_used"
        case entitled
        case gender
    }
}

// MARK: - Request bodies (the wire contract, pinned by test)
//
// These builders are the single source of the request-personal-
// generation payloads the client sends. They live in this
// test-compiled layer because RW-15 happened here: the build-(9)
// client sent register_references' paths under `storage_paths`
// (a field name taken from a backend REPORT, not the deployed
// function), the server read `paths`, and every registration —
// every user's, every time — was refused with 400. The deployed
// function's field names are the contract; these builders and
// their tests are the tripwire against a second drift.
enum PersonalHeroRequests {
    /// register_references: the field is `paths` — the deployed
    /// function validates exactly this name (RW-15 root cause).
    static func registerReferencesBody(paths: [String]) -> [String: Any] {
        [
            "action": "register_references",
            "paths": paths
        ]
    }

    /// flag_hero (backend (c), rw14-backend-bc-2026-10-31.md §4):
    /// flags one of the caller's own personalized heroes; it stops
    /// serving immediately and a regeneration is queued.
    static func flagHeroBody(heroId: String, reason: String) -> [String: Any] {
        [
            "action": "flag_hero",
            "hero_id": heroId,
            "reason": reason
        ]
    }
}

// MARK: - Personal-hero flag (RW-14 acceptance (19), spec §4)

/// Why a user flagged one of their OWN personalized heroes. `code`
/// is the value the backend's flag_hero action validates (the pool
/// flag vocabulary plus the personal one); `label` is the menu
/// text. The first case is the spec §4 control, verbatim; the
/// other three mirror the pool menu's labels (HeroFlagReason) —
/// for a personal photo they route to flag_hero too, because the
/// pool insert would misidentify the photo and retire nothing.
enum PersonalHeroFlagReason: String, CaseIterable {
    case doesntLookLikeMe = "doesnt_look_like_me"
    case looksBroken = "looks_broken"
    case inappropriate = "inappropriate"
    case outfitMismatch = "outfit_mismatch"

    /// The stored/validated `reason` value.
    var code: String { rawValue }

    /// The label shown in the flag menu. GEN-5 (RW-29 FA-18):
    /// frame 45's personal sheet, verbatim — the lead reason,
    /// the outfit reason, and the residual; the codes above are
    /// the server contract and do not change.
    var label: String {
        switch self {
        case .doesntLookLikeMe: return "This doesn't look like me"
        case .outfitMismatch: return "Wrong outfit for the weather"
        case .looksBroken: return "Something else"
        case .inappropriate: return "Other"
        }
    }

    /// The personal sheet's presentation (frame 45): exactly
    /// three reasons — the lead, the outfit reason, and the
    /// residual, which files under the generic defect code.
    static var sheetOrder: [PersonalHeroFlagReason] {
        [.doesntLookLikeMe, .outfitMismatch, .looksBroken]
    }

    /// What flagging a personal photo does, stated before the
    /// user commits (frame 45, under the lead reason).
    static let consequenceLine = "We'll stop showing this photo and make it again."
}

/// The personalized photo on screen, as a personal flag must
/// identify it: the backend hero id (what flag_hero addresses),
/// the cell it serves, and the URL displayed (the flag chip's
/// already-flagged tracking keys on it, as the pool chip does).
struct PersonalHeroFlagTarget: Equatable {
    let heroId: String
    let combinationId: String
    let imageUrl: String
}

/// Errors the backend returns as {"error": "<name>", ...}.
enum PersonalHeroAPIError: Error, Equatable {
    case allowanceExhausted(freeGenerationsUsed: Int)
    case consentRequired
    case referencesRequired
    case attestationsRequired
    case outfitSignatureMismatch
    case notEntitled
    /// The Supabase project has anonymous sign-ins disabled, so no
    /// user JWT can be minted (verified server gap, 2026-10-31).
    case authUnavailable
    case server(message: String)
    case http(Int)
    case unauthorized
}

/// The production consent text's version string, recorded with every
/// consent (backend §2.2 / §4.1). Bump when the consent copy changes.
enum PersonalConsent {
    static let version = "rw14-v1"
}

extension PersonalHeroAPIError {
    /// Map the backend's {"error": "<name>", ...} bodies onto the
    /// typed error enum (backend §2.2/§2.3). Lives in this layer so
    /// the mapping — in particular that an unrecognized error name
    /// survives verbatim as `.server(message:)` — is unit-tested;
    /// PersonalHeroAPI delegates here.
    static func mapped(data: Data, status: Int) -> PersonalHeroAPIError {
        struct ErrorBody: Decodable {
            let error: String?
            let freeGenerationsUsed: Int?
            enum CodingKeys: String, CodingKey {
                case error
                case freeGenerationsUsed = "free_generations_used"
            }
        }
        if let body = try? JSONDecoder().decode(ErrorBody.self, from: data),
           let name = body.error {
            switch name {
            case "allowance_exhausted":
                return .allowanceExhausted(freeGenerationsUsed: body.freeGenerationsUsed ?? 5)
            case "consent_required": return .consentRequired
            case "references_required": return .referencesRequired
            case "attestations_required": return .attestationsRequired
            case "outfit_signature_mismatch": return .outfitSignatureMismatch
            case "not_entitled": return .notEntitled
            case "Unauthorized": return .unauthorized
            default: return .server(message: name)
            }
        }
        return .http(status)
    }

    /// The server's own words for this failure, when it sent any:
    /// the message carried by `.server`. Everything else the
    /// backend can refuse with is a named condition the UI already
    /// understands, and carries no prose worth surfacing.
    var serverMessage: String? {
        if case .server(let message) = self { return message }
        return nil
    }
}

// MARK: - Opt-in commit failure presentation (RW-15 (3))
//
// What the opt-in flow shows when a commit throws. The two lines
// are the shipped DEVIATION-COPY strings (no §7 deck entry exists
// for either; they are named as deviations in the RW-14 build
// report). RW-15 adds the DETAIL: when the server returned an
// error body, its actual message renders beneath the generic
// line — build (9) computed that message inside the error
// mapping and then threw it away, leaving Sol a failure he
// could not describe and we could not diagnose from his report.
struct CommitFailurePresentation: Equatable {
    /// The primary line (the deck line for this failure class).
    let line: String
    /// The server's actual message, shown beneath the line in
    /// smaller secondary styling; nil when the server sent none.
    let detail: String?

    /// DEVIATION-COPY: honest, specific, and claims nothing was
    /// sent — because nothing was (anonymous sign-in unavailable
    /// server-side, so no session can be minted).
    static let serverUnavailableLine = "We can't reach RunWear's server right now, so your photos can't be uploaded yet. Nothing was sent. You can try again later — your recommendations are unchanged."

    /// DEVIATION-COPY: the generic upload-failure line.
    static let uploadFailureLine = "Something went wrong uploading your photos. No look was used. Please try again."

    static func forError(_ error: Error) -> CommitFailurePresentation {
        if let apiError = error as? PersonalHeroAPIError {
            if apiError == .authUnavailable {
                return CommitFailurePresentation(line: serverUnavailableLine, detail: nil)
            }
            return CommitFailurePresentation(line: uploadFailureLine, detail: apiError.serverMessage)
        }
        return CommitFailurePresentation(line: uploadFailureLine, detail: nil)
    }
}

// MARK: - Opt-in commit driver + finish sequence (RW-15 follow-up)
//
// The finish hand-off defect (rw15-retry-trace-2026-10-31.md):
// with registration fixed server-side, Sol's commits succeeded
// end to end — consent, uploads, register all 200, a generation
// job queued — yet the opt-in sheet never dismissed and
// onboarding never completed, across four taps. The exclusion
// chain in the trace report localizes it to commit(_:)'s success
// exit, and the concurrency shape is the defect:
//
// - The shipped commit ran its post-await statements in a bare,
//   non-isolated Task. After `try await onCommit` the continuation
//   resumes OFF the main actor (reproduced in a standalone
//   harness: every post-await statement, including the onFinish()
//   invocation, executes with Thread.isMainThread == false).
//   The dismissal and completion writes live in ANCESTOR views
//   (the host's @State, the app's @AppStorage) and are performed
//   through view structs captured in closures — SwiftUI state
//   mutation off the main actor is undefined, and those are the
//   writes that vanished. The flow's OWN state (committing)
//   happened to land, which is why the button cycled.
// - The host's finish handler compounded it: onboarding
//   completion was parked behind `await reloadHeroForPersonal()`
//   — a full weather/hero network reload — inside a nested
//   unstructured Task, so a degraded connection could stall
//   completion indefinitely even had the handler run cleanly.
//
// The fix removes both shapes. The commit runs end to end on the
// main actor (this driver), so the hand-off statements execute
// synchronously, in order, on the main actor. And the host's
// finish sequence (OptInFinish) performs dismissal and
// completion synchronously, deferring only the generation kick.

/// Drives one opt-in/replace commit for MakeItMeOptInFlow: owns
/// the committing flag and the failure presentation so the view
/// only renders state. All state mutates on the main actor.
@MainActor
final class OptInCommitDriver: ObservableObject {
    @Published private(set) var isCommitting = false
    @Published private(set) var failure: CommitFailurePresentation?
    /// RW-16.15: the save confirmation. Set on a successful commit,
    /// BEFORE onFinish dismisses the flow — the capture view renders
    /// "Your photos are saved." from it during the confirmation
    /// beat. Neither platform confirmed a save before this (the
    /// flow just vanished), which read to the user as "my photos
    /// didn't save"; Android's flow carries the identical contract.
    @Published private(set) var didSave = false

    /// How long the save confirmation stays on screen before the
    /// finish hand-off dismisses the flow.
    static let saveConfirmationBeat: UInt64 = 1_500_000_000

    /// Clear a recorded failure without starting a commit
    /// (RW-16.7): the presentation host calls this when a fresh
    /// flow opens, so the new flow never wears a previous
    /// attempt's error line. A no-op while a commit is in
    /// flight — that attempt owns its failure slot.
    func clearFailure() {
        guard !isCommitting else { return }
        failure = nil
        didSave = false
    }

    /// Run the commit: onCommit first; on success didSave is set
    /// (the flow renders the save confirmation), the confirmation
    /// beat elapses with the committing flag still up, and THEN
    /// the flag clears and onFinish is invoked — synchronously,
    /// in that order, on the main actor. On failure the flag
    /// clears and the failure presentation is set; onFinish is
    /// never invoked. A call while a commit is already in flight
    /// is a no-op (returns nil).
    @discardableResult
    func commit(
        photos: [Data],
        onCommit: @escaping ([Data]) async throws -> Void,
        onFinish: @escaping () -> Void
    ) -> Task<Void, Never>? {
        guard !isCommitting else { return nil }
        isCommitting = true
        failure = nil
        didSave = false
        return Task { @MainActor in
            do {
                try await onCommit(photos)
            } catch {
                isCommitting = false
                failure = CommitFailurePresentation.forError(error)
                return
            }
            didSave = true
            try? await Task.sleep(nanoseconds: Self.saveConfirmationBeat)
            isCommitting = false
            onFinish()
        }
    }
}

/// The onboarding host's post-opt-in finish sequence. Dismissal
/// and onboarding completion happen synchronously, in that
/// order, on the caller's (main) actor; the first-generation
/// kick — which needs a hero reload to key its cell — is the
/// only deferred effect, and it must never gate completion
/// (build (9) parked completion behind exactly that reload).
enum OptInFinish {
    static func perform(
        dismiss: () -> Void,
        complete: () -> Void,
        kick: @escaping () async -> Void
    ) {
        dismiss()
        complete()
        Task { await kick() }
    }
}

// MARK: - Reference commit set (RW-15 (4))
//
// What a photo commit sends, derived from the picked set. The
// derivation is pure and stateless on purpose: the capture view
// holds the picked photos across a failed commit (nothing in the
// failure path mutates them), and a retry re-derives the commit
// set from that same picked set — so a retry sends the same full
// set the first attempt did. These functions are the seam the
// view and the store both use, pinned by test.
struct PickedReferencePhoto: Equatable {
    /// Whether the on-device quality check accepted this photo.
    var isAccepted: Bool
    /// The normalized JPEG for upload, when accepted and encoded.
    var jpegData: Data?
}

enum ReferenceCommitSet {
    /// Every accepted photo's JPEG, in picked order — and nothing
    /// else (checking/rejected photos never upload).
    static func jpegPhotos(from picked: [PickedReferencePhoto]) -> [Data] {
        picked.compactMap { $0.isAccepted ? $0.jpegData : nil }
    }

    /// The 1-based storage positions for a commit of this many
    /// photos (ref-1 … ref-N); empty for an empty commit.
    static func positions(photoCount: Int) -> [Int] {
        photoCount > 0 ? Array(1...photoCount) : []
    }
}

// MARK: - Versioned cell cache (acceptance RW-14 (16): the named trap)
//
// The server bumps a per-cell `version` whenever the cell's verified
// hero set changes. The client caches (version, signed URL) per cell.
// A re-lookup whose version EQUALS the cached version keeps the cached
// image — no visual churn, no re-download. A DIFFERENT version swaps
// the new image in. Signed URLs expire after 1 h server-side, so a
// cached entry older than `urlLifetime` is refreshed even at the same
// version (the content is identical; only the URL is re-signed).
struct PersonalCellCache {
    struct Entry: Equatable {
        let version: Int
        /// The backend id of the hero this entry serves — what a
        /// personal flag addresses (RW-14 acceptance (19)).
        let heroId: String?
        let imageURL: String
        let fetchedAt: Date
    }

    enum Resolution: Equatable {
        /// Show this URL — either freshly fetched or the cached one.
        case show(String)
        /// Nothing verified for this cell right now.
        case none
    }

    /// Signed URLs live 1 h on the server; refresh with margin.
    static let urlLifetime: TimeInterval = 50 * 60

    private(set) var entries: [String: Entry] = [:]

    /// Fold one get-personal-hero result into the cache and decide
    /// what the hero slot should show for this cell.
    mutating func resolve(lookup: PersonalHeroLookup, now: Date) -> Resolution {
        let cell = lookup.combinationId
        guard lookup.found, let url = lookup.imageUrl, let version = lookup.version else {
            // Not found. If the lookup's version has MOVED past the
            // cached entry's, the cell's hero set changed under us
            // (a flag retired the hero, a regeneration is pending):
            // drop the stale entry, or freshURL would keep serving
            // the retired photo until its URL aged out (RW-15 (2)).
            // A not-found at the SAME version changes nothing — the
            // cell simply has no verified hero yet.
            if let version = lookup.version,
               let existing = entries[cell],
               existing.version != version {
                entries[cell] = nil
            }
            return .none
        }
        if let existing = entries[cell],
           existing.version == version,
           now.timeIntervalSince(existing.fetchedAt) < Self.urlLifetime {
            return .show(existing.imageURL)
        }
        entries[cell] = Entry(version: version, heroId: lookup.heroId, imageURL: url, fetchedAt: now)
        return .show(url)
    }

    /// The cached URL for a cell, if still fresh — lets a re-display
    /// paint instantly while a revalidation lookup runs.
    func freshURL(for combinationId: String, now: Date) -> String? {
        guard let entry = entries[combinationId],
              now.timeIntervalSince(entry.fetchedAt) < Self.urlLifetime else { return nil }
        return entry.imageURL
    }

    /// The backend hero id the cell's cached entry serves, if any.
    func heroId(for combinationId: String) -> String? {
        entries[combinationId]?.heroId
    }

    /// Drop a cell's entry outright — the explicit refresh after a
    /// successful personal flag: the flagged hero must stop being
    /// served from cache on the very next resolution.
    mutating func evict(combinationId: String) {
        entries[combinationId] = nil
    }

    mutating func removeAll() {
        entries.removeAll()
    }
}

// MARK: - Free-looks meter (GEN-2 I2 · W-18: ONE source of truth)
//
// The diagnosis (2026-11-04 walkthrough, W-18): frame 34 announced
// its debit from a HARDCODED line ("That was look 1 of your 5 free
// looks — 4 remain…") rendered the moment the screen appeared,
// while Settings rendered the store's `freeGenerationsUsed` — the
// server profile counter, which the server only increments when a
// look is DELIVERED (personal-hero-worker: the charge lands with
// the verified hero insert, for a free_on_demand job on a cell
// never counted before). The two surfaces kept separate books:
// frame 34 assumed the debit at presentation time; the store's
// counter was last read at opt-in (before generation was even
// queued) and nothing re-read it when the look landed, so Settings
// stood at 0 for the rest of the session.
//
// The fix is structural: there is exactly ONE counter — the
// store's `freeGenerationsUsed`, bound to the server's
// `personal_profiles.free_generations_used` — and every surface
// composes its line from that value through this one seam. The
// store re-reads the profile when a look lands (awaitHero), which
// is the moment the server's charge exists to be read.
enum FreeLooksMeter {
    /// Settings' state line (frame 37's meter), composed from the
    /// shared counter. `allowance` renders as the bare number the
    /// deck uses; at the shipped allowance of 5 these are exactly
    /// the strings Settings has always carried.
    static func settingsLine(used: Int, allowance: Int) -> String {
        if used >= allowance { return "All \(allowance) free looks used" }
        return "\(used) of \(allowance) free looks used"
    }

    /// The paywall's free bar line, composed from the same counter
    /// (it previously interpolated the store value inline — same
    /// number, second spelling site).
    static func subscriptionLine(used: Int, allowance: Int) -> String {
        "\(used) of \(allowance) used · Free looks never expire — and your first ones take about a minute to make."
    }

    // MARK: In-flight truthfulness (GEN-3 · X-9)
    //
    // The gen-2 seam counts only DELIVERED looks — correct, because
    // the server charges on delivery — but Settings composed its
    // block from that counter ALONE. While a look was generating
    // (requested, visibly "making" on the hero), Settings printed
    // the settled line ("0 of 5 free looks used" before the first
    // delivery) with nothing marking the in-flight look, so the
    // block contradicted the flow at the same moment (census X-9:
    // the Android observation, noted there as "exactly as on iOS
    // this morning"; frame 34 already refuses to print a meter at
    // all while making — Settings had no equivalent discipline).
    // The fix keeps the delivered count as the meter AND carries
    // the in-flight look in the same status block as a pending
    // line, so the block as a whole can never read as settled
    // while a generation is running.

    /// How many looks are in flight right now: cells the server
    /// last reported as MAKING, unioned with cells holding a
    /// user-initiated pending request (a cell in both counts once).
    static func inFlightCount(
        lookStates: [String: PersonalLookState],
        userInitiatedPending: Set<String>
    ) -> Int {
        var cells = Set(lookStates.filter { $0.value.isMaking }.keys)
        cells.formUnion(userInitiatedPending)
        return cells.count
    }

    /// The status block's pending line, or nil when nothing is in
    /// flight (the block then carries the settled meter alone,
    /// which is true). One look reads as the singular making line
    /// the flow itself shows; more read as a count.
    static func settingsPendingLine(inFlight: Int) -> String? {
        switch inFlight {
        case ...0: return nil
        case 1: return "Making your look…"
        default: return "Making \(inFlight) looks…"
        }
    }
}

// MARK: - The hero pill (GEN-3 WS-A(2) / WS-B, 2026-11-04)
//
// ONE pill at the bottom of the hero image, state-driven — the
// home expression of the same truth the first-look screen's W-16
// seam resolves (FirstLookPresentation): the working state lives
// ON the photo, and a delivered-look claim appears only when the
// generated photo is actually displayed. The walkthrough defects
// this closes: the generating state sat in the scroll content
// under the hero (census X-8) and appeared for a later place's
// cell while the requested place showed nothing (X-2); the
// scaffold treatment was a separate text line under the hero.
//
// The state is derived ONLY from the REQUESTED cell's facts: the
// caller passes the look state of the cell the screen is keyed on
// (WeatherViewModel.currentPersonalCombination — the cell
// loadHeroImage resolved for the place on the label), never a
// global or another cell's state. The four visible states:
//  (a) making — a personal photo is being generated for this
//      cell and none is on screen yet: "Making this you…" over
//      the fallback photo (which stays up — WS-A(1)).
//  (b) scaffold — the user's own banked photo for close-by
//      weather is on screen while the exact look makes: the
//      Make It Me script mark + "Your photo" — NOTHING MORE
//      (GEN-3 DESIGN DELTA 2, Sol's Nov 4 mockup review: the
//      "made for close-by weather" line is OUT, found confusing).
//      This folds the PersonalServePolicy scaffold treatment
//      into the pill.
//  (c) freeLook — the exact personal photo IS displayed, free
//      tier: the script mark + the look count from the ONE
//      shared counter (FreeLooksMeter's source), in the mock's
//      "N of 5 looks" form, + "Get unlimited", which opens the
//      Premium paywall. Like the W-16/W-18 meter, the count
//      waits for the counter to confirm the delivery (used > 0)
//      rather than printing an unverified number.
//  (d) premium — the exact personal photo is displayed and the
//      user is subscribed: the script mark + "Premium" + a lock
//      glyph (the mock's device — NOT the crown I-HERO shipped).
// States (b)(c)(d) carry the Make It Me script mark; state (a)
// is unchanged (approved as excellent).
// A failed look renders NO pill: the standard photo serves
// quietly and the failure line + retry live in the personal
// section, as before. Not opted in renders no pill.
enum HeroPillState: Equatable {
    case hidden
    /// Generating, subscribed (or otherwise not sellable): the
    /// clean pill — script + spinner + "Making this you…".
    case making
    /// GEN-5 (RW-28, C-4 AMENDED — Sol, Nov 5, 2026): generating
    /// on the FREE tier, where the user is actually waiting on
    /// demand — the same clean pill PLUS one small green upsell
    /// line, "Get it instantly next time" (Premium looks are
    /// banked ahead, so next time the look is already there).
    /// Gated to the free generating state ONLY.
    case makingWithUpsell
    case scaffold
    case freeLook(used: Int, allowance: Int)
    /// GEN-5 (RW-28, Sol Nov 5, 2026): OUT OF FREE LOOKS — the
    /// free tier's exhausted status: script lead, the used-up
    /// count stated honestly, and "Get unlimited" as the pill's
    /// MAIN message; the whole pill taps to the Premium offer.
    case exhausted(used: Int, allowance: Int)
    case premium
}

enum HeroPill {
    static let makingLabel = "Making this you…"
    /// GEN-3 DESIGN DELTA 2: "Your photo" — nothing more.
    static let scaffoldLabel = "Your photo"
    static let premiumLabel = "Premium"
    static let getUnlimitedLabel = "Get unlimited"
    /// GEN-5 (C-4 AMENDED): the free generating pill's green
    /// upsell line — verbatim, unembellished.
    static let getInstantlyLabel = "Get it instantly next time"

    /// The exhausted pill's honest count, in the free pill's
    /// count family: "5 of 5 looks used".
    static func exhaustedCountLabel(used: Int, allowance: Int) -> String {
        "\(min(used, allowance)) of \(allowance) looks used"
    }
    /// The Make It Me script mark the pill carries in states
    /// (b)(c)(d) — rendered in Great Vibes wherever the pill is
    /// drawn (GEN-3 DESIGN DELTA 2).
    static let scriptMarkText = "Make It Me"

    /// The count half of the free-look pill, in the approved
    /// mock's "1 of 5 looks" form, clamped to the allowance like
    /// the W-18 meter.
    static func freeLookCountLabel(used: Int, allowance: Int) -> String {
        "\(min(used, allowance)) of \(allowance) looks"
    }

    static func resolve(
        optedIn: Bool,
        lookState: PersonalLookState,
        personalPhotoDisplayed: Bool,
        isSubscribed: Bool,
        freeGenerationsUsed: Int,
        freeAllowance: Int
    ) -> HeroPillState {
        guard optedIn else { return .hidden }
        // GEN-5 (RW-29 FA-1, parity settlement): a generation in
        // flight OUTRANKS the scaffold serve — when this cell's
        // look is making, the pill reports the generation, not
        // the photo underneath it (Android's HeroPillPolicy is
        // test-pinned to exactly this precedence; iOS build 25
        // inverted it, resolving to the scaffold state whenever a
        // personal photo showed during a generation). The C-4
        // amendment's gating applies here too: a free user's
        // generating pill carries the green instantly line, a
        // subscriber's stays clean.
        if lookState == .making {
            return isSubscribed ? .making : .makingWithUpsell
        }
        if personalPhotoDisplayed {
            if isSubscribed { return .premium }
            // GEN-5: the exhausted status takes over the free pill
            // the moment the shared counter reaches the allowance —
            // exactly at the allowance, driven by the same one
            // meter (no second bookkeeping).
            if freeGenerationsUsed >= freeAllowance {
                return .exhausted(used: freeGenerationsUsed, allowance: freeAllowance)
            }
            // The exact photo is displayed: the count may show
            // only once the shared counter confirms the delivery
            // (W-18) — until then the pill waits, hidden.
            guard freeGenerationsUsed > 0 else { return .hidden }
            return .freeLook(used: freeGenerationsUsed, allowance: freeAllowance)
        }
        // No personal photo on screen and nothing making (the
        // making case is settled at the top, FA-1): the pill is
        // silent over a fallback serve.
        return .hidden
    }
}

// MARK: - Fork hero asset (GEN-5, RW-28, ruling C-2)

/// The purpose-designed, hard-coded fork image
/// (`mim-fork-hero`, 2048 × 1152, bundled — asset lane spec:
/// rw-gen5-assets/README.md) and its code-drawn dashed face box.
/// Anchoring rule (binding): bands narrower than the image show
/// the FULL image height with the horizontal window centred on
/// the face centre (x = 0.507), clamped to the edges; bands wider
/// than the image show the full width anchored to the TOP. Plain
/// centre-crop is ruled out — the head has only 3.2% headroom.
enum ForkHeroSpec {
    static let assetName = "mim-fork-hero"
    static let imageSize = CGSize(width: 2048, height: 1152)
    /// Face centre, fraction of image width.
    static let faceCentreX: CGFloat = 0.507
    /// Measured head: x 0.414–0.600, y 0.032–0.482.
    static let headRectNormalized = CGRect(x: 0.414, y: 0.032, width: 0.186, height: 0.450)
    /// Draw box (measured head + padding), image-normalised.
    static let faceBoxNormalized = CGRect(x: 0.400, y: 0.018, width: 0.214, height: 0.478)
    /// Shoulders: x 0.306–0.714 — must stay in frame with the face.
    static let shouldersXRange: ClosedRange<CGFloat> = 0.306...0.714

    /// The visible source rect (image coordinates) for a band of
    /// `frameSize`, per the anchoring rule.
    static func visibleRect(imageSize: CGSize, frameSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0,
              frameSize.width > 0, frameSize.height > 0 else { return .zero }
        let frameAspect = frameSize.width / frameSize.height
        let imageAspect = imageSize.width / imageSize.height
        if frameAspect <= imageAspect {
            // Narrower band: full height; window centred on the
            // face centre, clamped.
            let width = imageSize.height * frameAspect
            let centre = faceCentreX * imageSize.width
            let x = min(max(centre - width / 2, 0), imageSize.width - width)
            return CGRect(x: x, y: 0, width: width, height: imageSize.height)
        }
        // Wider band: full width, anchored to the top.
        let height = imageSize.width / frameAspect
        return CGRect(x: 0, y: 0, width: imageSize.width, height: height)
    }

    /// The face box in frame points, transformed with the SAME
    /// crop the image receives — registered to the face at every
    /// form factor.
    static func faceBoxRectInFrame(imageSize: CGSize, frameSize: CGSize) -> CGRect {
        let visible = visibleRect(imageSize: imageSize, frameSize: frameSize)
        guard visible.width > 0, visible.height > 0 else { return .zero }
        let scaleX = frameSize.width / visible.width
        let scaleY = frameSize.height / visible.height
        return CGRect(
            x: (faceBoxNormalized.minX * imageSize.width - visible.minX) * scaleX,
            y: (faceBoxNormalized.minY * imageSize.height - visible.minY) * scaleY,
            width: faceBoxNormalized.width * imageSize.width * scaleX,
            height: faceBoxNormalized.height * imageSize.height * scaleY
        )
    }

    /// The band's height for a given width (RW-29 FA-40): the
    /// band is never wider than 2.4:1, so at every shipped width
    /// the visible window keeps the face, shoulders, and chest
    /// composed (the asset lane's crop table: at 2.32:1 the
    /// torso is visible to y = 0.767, at 2.56:1 to 0.694). A
    /// fixed 180 pt band satisfies this on phones (≤ 432 pt
    /// wide) and grows on wider form factors instead of letting
    /// the window slide down the torso and through the chin.
    static func bandHeight(forWidth width: CGFloat) -> CGFloat {
        max(180, width / 2.4)
    }

    /// True when the whole draw box (and therefore the head) is
    /// inside the visible rect — the C-2 form-factor guarantee.
    static func faceBoxFullyVisible(imageSize: CGSize, frameSize: CGSize) -> Bool {
        let visible = visibleRect(imageSize: imageSize, frameSize: frameSize)
        let box = CGRect(
            x: faceBoxNormalized.minX * imageSize.width,
            y: faceBoxNormalized.minY * imageSize.height,
            width: faceBoxNormalized.width * imageSize.width,
            height: faceBoxNormalized.height * imageSize.height
        )
        return visible.contains(box)
    }
}

// MARK: - Convergence band (GEN-3, Sol's settled wording Nov 4)

/// The user's real bank coverage: `ready` = distinct cells with
/// a verified banked hero (personal_heroes); `total` = distinct
/// cells the server has engaged — banked heroes ∪ generation
/// jobs of any status (personal_generation_jobs). The fill
/// enqueues the whole missing matrix at once, so once a fill has
/// run the union IS the fill set; before any fill it is just the
/// banked set (ready == total, band hidden).
struct BankCoverage: Equatable {
    let ready: Int
    let total: Int
}

/// Coverage from the two owner-read cell sets — ready is the
/// GEN-5 (Addendum 8 follow-up, Defect 2 — the convergence
/// diagnosis of Nov 5, 2026): coverage is measured against the
/// bank's TARGET SET — the forecast cells the client named to
/// start-personal-fill — never against the set the server has
/// happened to engage. The gen-4 formula (ready = banked, total =
/// banked ∪ ever-enqueued) divided 8 banked cells by 9 engaged
/// cells and displayed ~89% at 17% true coverage of the 48-cell
/// bank; it could also reach 100% while target cells were still
/// missing. Here ready counts only banked cells INSIDE the
/// target, total IS the target, so the fraction can reach 1 only
/// when every named cell is banked. Nil when no target has been
/// named yet — without a named set there is no honest fraction
/// and the band stays hidden. Pure, so the band's counts are
/// unit-pinned without a network.
func deriveBankCoverage(heroCells: Set<String>, targetCells: Set<String>) -> BankCoverage? {
    guard !targetCells.isEmpty else { return nil }
    return BankCoverage(
        ready: heroCells.intersection(targetCells).count,
        total: targetCells.count
    )
}

/// GEN-5 (Addendum 8 follow-up, Defect 1; corrected by the
/// second follow-up, Nov 5, 2026): one frame of the 7-day
/// forecast for fill naming. The named forecast cells are ONE
/// REPRESENTATIVE FRAME PER (DAY, DAYPART) CLUSTER — the first
/// forecast hour of each cluster, in forecast order (Android's
/// ForecastFillCells selection, mirrored for parity) — each
/// keyed through the SAME PersonalCombination.make the displayed
/// cell uses (comfort-adjusted feels-like frame), so a named
/// cell is exactly the cell its frame will ask for.
struct ForecastCellFrame {
    /// The forecast day, yyyy-MM-dd in the forecast's own
    /// (location-local) frame — half of the cluster key.
    let date: String
    let weatherCode: Int
    let feelsLikeFahrenheit: Double
    let hour: Int
    let outfit: OutfitRecommendation
}

enum ForecastPriorityCells {
    static func ids(genderPreference: GenderPreference, frames: [ForecastCellFrame]) -> [String] {
        var seen = Set<String>()
        var ids: [String] = []
        var lastCluster: String? = nil
        for frame in frames {
            // The night daypart forms two clusters per calendar
            // day (00:00–04:59 and 20:00–23:59); the consecutive
            // key change is what separates them — both are named.
            let cluster = "\(frame.date)|\(HeroTimeOfDay.from(hour: frame.hour).rawValue)"
            if cluster == lastCluster { continue }
            lastCluster = cluster
            let combination = PersonalCombination.make(
                genderPreference: genderPreference,
                weatherCode: frame.weatherCode,
                feelsLikeFahrenheit: frame.feelsLikeFahrenheit,
                hour: frame.hour,
                outfit: frame.outfit
            )
            if seen.insert(combination.combinationId).inserted {
                ids.append(combination.combinationId)
            }
        }
        return ids
    }
}

/// The fill set a start-personal-fill call names, composed:
/// the forecast cells FIRST (they are queued ahead — imminent
/// conditions land first), then the canonical core matrix cells
/// the forecast did not already name (the scaffold substrate
/// fills behind them). Pure; the composition the grant call,
/// the forecast-refresh re-kick, and the epoch re-kick all send.
enum FillSetComposition {
    static func named(forecastCells: [String], coreCells: [String]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for id in forecastCells + coreCells where seen.insert(id).inserted {
            out.append(id)
        }
        return out
    }
}

struct ConvergenceBandContent: Equatable {
    let heading: String
    let line: String
    let fraction: Double
}

/// The convergence band's copy and visibility rule. The heading
/// and line are Sol's settled copy (Nov 4), word for word — do
/// not paraphrase.
///
/// NUMERIC COUNT: HELD by Sol (Nov 4, later ruling). An earlier
/// instruction's "N of your M looks are ready" line is NOT
/// built: the example total had no basis in the generation
/// model, and no total (24, 48, or any other) is hard-coded or
/// rendered anywhere. Progress shows as the BAR only; the bar's
/// fraction comes from the real banking state (BankCoverage).
/// If Sol rules a replacement treatment, it lands here.
///
/// PREMIUM CONTEXT ONLY: banking is premium-only behaviour —
/// free / in-trial users (the .free 5-look allowance state) get
/// on-demand looks and no bank, so the band never renders for
/// them. The band exists only while convergence is genuinely
/// in progress: subscribed mode, a successful coverage read,
/// and ready < total. When the bank is complete (or the state
/// is unknown) there is no band.
enum ConvergenceBand {
    static let heading = "Making your looks"
    static let line = "We're generating your outfits across all running conditions, so it will be ready when you need it."

    /// GEN-5 (Addendum 8, bank non-convergence): the STALLED
    /// line. Sol's device sat at ~90% for three hours after
    /// subscribing while the band presented as ordinary
    /// in-progress — a bank that has stopped advancing must say
    /// so, in the band itself. The fraction stays the REAL
    /// banked fraction (ready ÷ total from the server's cell
    /// sets — audited: there is no time-based or estimated
    /// progress anywhere in this rule); only the line changes.
    /// Lane-authored copy (no settled stalled line exists in
    /// the design record), written to the honesty rules: it
    /// names the delay, and it claims only what is true — the
    /// looks already banked ARE the ones being served.
    static let stalledLine = "This is taking longer than usual. Your ready looks are already in your outfits — the rest are still being made."

    /// How long coverage may fail to advance before the bank
    /// reads as stalled (the store's tracker applies it).
    static let stallThreshold: TimeInterval = 30 * 60

    /// The premium-context input is a plain Bool, deliberately:
    /// this model file is compiled into BOTH the app and the
    /// unit-test targets, while PersonalHeroStore is app-only —
    /// the seats map their mode at the call site
    /// (`mode == .subscribed`), so the rule itself stays
    /// testable in the model layer like everything else here.
    static func content(isSubscribed: Bool, coverage: BankCoverage?, stalled: Bool = false) -> ConvergenceBandContent? {
        guard isSubscribed, let coverage else { return nil }
        guard coverage.total > 0, coverage.ready < coverage.total else { return nil }
        return ConvergenceBandContent(
            heading: heading,
            line: stalled ? stalledLine : line,
            fraction: Double(coverage.ready) / Double(coverage.total)
        )
    }
}

/// GEN-5 (Addendum 8): the stall tracker — pure, so the rule is
/// pinned in the model layer. It watches the REAL ready count:
/// every observation that ADVANCES it resets the clock; when
/// the count has not advanced for `ConvergenceBand.stallThreshold`
/// while the bank is still incomplete, the bank reads as
/// stalled. A completed bank is never stalled, and the first
/// observation of a bank starts its clock (a bank is not born
/// stalled).
struct BankStallTracker: Equatable {
    private var lastReady: Int?
    private var lastAdvanceAt: Date?

    mutating func observe(_ coverage: BankCoverage, now: Date) {
        if let lastReady, coverage.ready > lastReady {
            self.lastReady = coverage.ready
            lastAdvanceAt = now
        } else if lastReady == nil {
            lastReady = coverage.ready
            lastAdvanceAt = now
        }
    }

    func isStalled(_ coverage: BankCoverage, now: Date) -> Bool {
        guard coverage.total > 0, coverage.ready < coverage.total,
              let lastAdvanceAt else { return false }
        return now.timeIntervalSince(lastAdvanceAt) >= ConvergenceBand.stallThreshold
    }
}

// MARK: - Upsell eligibility (spec §5)
//
// The 6th-cell card shows only when ALL of these hold: the user is on
// the free tier; the allowance is fully spent; the CURRENT cell was
// refused with allowance_exhausted (i.e. it is a genuinely new cell,
// not one already holding a hero); and the frequency rules allow it —
// never twice for the same cell, and after any dismissal at most once
// a week.
enum UpsellEligibility {
    static let redisplayInterval: TimeInterval = 7 * 24 * 60 * 60

    static func shouldShow(
        isSubscribed: Bool,
        freeGenerationsUsed: Int,
        freeAllowance: Int,
        exhaustedCell: String?,
        currentCell: String,
        lastDismissedAt: Date?,
        dismissedCells: Set<String>,
        now: Date
    ) -> Bool {
        guard !isSubscribed else { return false }
        guard freeGenerationsUsed >= freeAllowance else { return false }
        guard exhaustedCell == currentCell else { return false }
        guard !dismissedCells.contains(currentCell) else { return false }
        if let last = lastDismissedAt,
           now.timeIntervalSince(last) < redisplayInterval {
            return false
        }
        return true
    }
}

// MARK: - Reference-photo quality verdict (spec §2.3 / strings C3)
//
// The Vision measurements are taken on device; the CLASSIFICATION of
// those measurements into the spec's rejection reasons is pure and
// tested here. Priority: the count problems first (a photo of two
// people is wrong however bright it is), then darkness, then framing.
enum PhotoRejection: Equatable {
    case faceUnclear      // C3a
    case multiplePeople   // C3b
    case tooDark          // C3c
    case tooClose         // C3d
    case faceTooSmall     // C3E (RW-26: frame 18's failed check)
    case rotated          // C3G (WS-6 / F-1: the roll gate)

    var specString: String {
        switch self {
        case .faceUnclear:
            return "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
        case .multiplePeople:
            return "This photo has more than one person in it. Pick one that's just you."
        case .tooDark:
            // GEN-6 (RW-51): the settled dim/turned wording
            // (design-spec §10.4, frame 18), verbatim in substance.
            return "Try facing a window or light, looking at the camera."
        case .tooClose:
            // GEN-6 (RW-51): the settled too-close wording
            // (design-spec §10.4, frame 18), verbatim.
            return "Hold the phone at arm's length, so your whole face fits with space around it."
        case .faceTooSmall:
            // GEN-6 (RW-51): the settled face-too-small wording
            // (design-spec §10.4, frame 18), verbatim — failure-
            // specific, and the camera is offered first (the
            // guided capture frames the face for you).
            return "Your face is small in that photo — A closer selfie works best — a mirror selfie is great too. Or let the camera guide you…"
        case .rotated:
            // C3G: verbatim with Android's MakeItMeStrings
            // C3G_REJECT_ROTATED (both platforms, one string).
            return "This photo came out sideways. Retake it holding your phone upright, or pick a photo that's already upright."
        }
    }
}

// MARK: - Intake orientation (WS-6 / F-1)
//
// The intake orientation rules — pure, no Vision/UIKit types, so
// the gate exercises every seam with rotated inputs. Mirror of
// Android's IntakeOrientation (same constants, same math).
//
// F-1's root cause (field diagnosis, Nov 3 2026): the guided-selfie
// capture's artifact arrived SIDEWAYS and no stage read in-plane
// rotation — the identity screen scored a 90°-rotated face at
// 0.19–0.25 against the 0.40 line, so no draw could ever deliver.
// The client fix has three parts, all decided here or in
// PhotoQualityChecker:
//
//  1. EXIF-at-decode: the checker bakes the photo's EXIF
//     orientation into the pixels before anything measures or
//     encodes it, so the captured artifact is upright at the
//     source (PhotoQualityChecker's job — UIImage orientation is
//     iOS's EXIF face).
//  2. Roll gate: `rollWithinGate` — the subject face's roll (the
//     Vision face observation's `roll`, read by the checker for
//     the first time in the app) must sit within
//     ±`maxRollDegrees` of upright (the same 30° line the
//     server's ref-prep uses).
//  3. Quarter-turn correction: a residual roll that is a near
//     quarter-turn (an EXIF-less sideways photo — a screenshot, an
//     edited export) is corrected by `quarterTurnCorrection` and
//     re-measured; a roll that is neither within the gate nor a
//     clean quarter-turn is NOT guessed at — the photo is
//     rejected .rotated and retaken (the server safety net
//     behaves the same).
enum IntakeOrientation {

    /// Roll gate half-width in degrees (mirrors ref-prep's 30° line).
    static let maxRollDegrees: Double = 30
    /// How close a residual roll must sit to a quarter-turn to be corrected.
    static let quarterTurnToleranceDegrees: Double = 15

    /// Normalize any roll reading into (-180, 180].
    static func normalizeRoll(_ rollDegrees: Double) -> Double {
        var r = rollDegrees.truncatingRemainder(dividingBy: 360)
        if r > 180 { r -= 360 }
        if r <= -180 { r += 360 }
        return r
    }

    /// The roll gate: the subject face reads upright.
    static func rollWithinGate(_ rollDegrees: Double) -> Bool {
        abs(normalizeRoll(rollDegrees)) <= maxRollDegrees
    }

    /// The compensating clockwise rotation (90, 180, or 270) that
    /// uprights a bitmap whose subject face reads `rollDegrees` —
    /// or nil when the roll is within the gate (nothing to correct)
    /// or is not a clean quarter-turn (not correctable by rotation
    /// alone; the gate rejects it instead). The correction is the
    /// INVERSE of the measured roll, snapped to the nearest
    /// quarter-turn (the diagnosis measured roll ≈ +90° on the
    /// sideways reference, and uprighting took the inverse turn).
    /// The checker never trusts the sign blindly: a corrected
    /// photo is re-measured, and only a re-measurement inside the
    /// gate is accepted (see PhotoQualityChecker).
    static func quarterTurnCorrection(rollDegrees: Double) -> Int? {
        let roll = normalizeRoll(rollDegrees)
        if abs(roll) <= maxRollDegrees { return nil }
        let snapped = (roll / 90).rounded() * 90
        if abs(roll - snapped) > quarterTurnToleranceDegrees { return nil }
        let correction = ((-snapped).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360)
        switch correction {
        case 90, 180, 270: return Int(correction)
        default: return nil
        }
    }
}

enum PhotoQualityVerdict {
    /// Mean-luminance floor (0...1) below which a photo is "too dark".
    static let darkThreshold: Double = 0.18
    /// Largest-face area share above which the photo is "cropped too
    /// close" (a head-and-shoulders frame keeps the face well under
    /// half the frame).
    static let tooCloseThreshold: Double = 0.45
    /// Largest-face area share below which the face is too small to
    /// read (RW-26, frame 18's failed check). Deliberately far
    /// below the guided capture's coaching target — this rejects
    /// only faces no pipeline could use.
    static let tooSmallThreshold: Double = 0.02

    /// THE check order (F7 harmonization, RW-26): face COUNT first
    /// (no face, then multiple faces), then DARKNESS, then framing
    /// (too small, too close). Both platforms now apply this one
    /// order — Android's PhotoQualityPolicy previously judged
    /// darkness first; the Android lane applies count-first from
    /// this train. (PhotoIntakePolicy governs source routing, not
    /// check order, on both platforms.)
    ///
    /// WS-6 (F-1) inserts the ROLL gate directly after the count
    /// checks — the same seat Android's policy gives it: a subject
    /// face whose roll is beyond IntakeOrientation's gate (and was
    /// not a correctable quarter-turn — the checker corrects those
    /// before this verdict runs) is rejected .rotated for retake,
    /// never waved through sideways. `faceRollDegrees` defaults to
    /// 0 (upright) so pre-F-1 call sites keep their verdicts.
    static func rejection(
        faceCount: Int,
        largestFaceAreaRatio: Double,
        meanLuminance: Double,
        faceRollDegrees: Double = 0
    ) -> PhotoRejection? {
        if faceCount == 0 { return .faceUnclear }
        if faceCount > 1 { return .multiplePeople }
        if !IntakeOrientation.rollWithinGate(faceRollDegrees) { return .rotated }
        if meanLuminance < darkThreshold { return .tooDark }
        if largestFaceAreaRatio < tooSmallThreshold { return .faceTooSmall }
        if largestFaceAreaRatio > tooCloseThreshold { return .tooClose }
        return nil
    }
}

/// GEN-5 (RW-29 FA-54): the per-check report behind the upload
/// review. Build 25's review collapsed the still check to ONE
/// verdict line (plus, on acceptance, the four labels joined in
/// a single string) — the design's review reports the four
/// settled checks INDIVIDUALLY, the same four the guided capture
/// coaches live. Derived from the same measurements and the
/// same thresholds the verdict uses, so the report can never
/// disagree with the verdict it accompanies.
struct PhotoCheckReport: Equatable {
    let inFrame: Bool
    let faceSize: Bool
    let straightOn: Bool
    let light: Bool

    static func report(
        faceCount: Int,
        largestFaceAreaRatio: Double,
        meanLuminance: Double,
        faceRollDegrees: Double
    ) -> PhotoCheckReport {
        PhotoCheckReport(
            inFrame: faceCount >= 1,
            faceSize: faceCount >= 1
                && largestFaceAreaRatio >= PhotoQualityVerdict.tooSmallThreshold
                && largestFaceAreaRatio <= PhotoQualityVerdict.tooCloseThreshold,
            straightOn: IntakeOrientation.rollWithinGate(faceRollDegrees),
            light: meanLuminance >= PhotoQualityVerdict.darkThreshold
        )
    }

    /// The all-failed report for a photo that could not be
    /// measured at all (the checker's fail-closed case).
    static let unmeasurable = PhotoCheckReport(
        inFrame: false, faceSize: false, straightOn: false, light: false
    )

    func passed(_ check: SelfieCheck) -> Bool {
        switch check {
        case .faceFound: return inFrame
        case .faceLargeEnough: return faceSize
        case .faceFrontal: return straightOn
        case .wellLit: return light
        }
    }
}

// MARK: - Intake copy (RW-16.14)
//
// GEN-6 (DA-23): the dead full-body guidance constant is
// DELETED — guidance for an intake settled as face-only
// (design-spec §10), with zero call sites since gen-5 removed it
// from the intake rendering.
enum MakeItMeCopy {
    /// Frame 34 (WS-9 fix): the first-look band's header, verbatim
    /// from the frozen design package — Android ships the identical
    /// string (MakeItMeStrings.FIRST_LOOK_BAND_HEADER). The band
    /// header names what the §9 pair below it belongs to; without
    /// it the two benefit lines float unattributed.
    static let firstLookBandHeader = "With Premium"

    /// Frame 34's heading (design frame 34 + Android's
    /// firstLookHeading, verbatim pattern): "Your first look ·
    /// Today, {city}" — the CITY only, never the region; with no
    /// known place the line ends at "Today".
    static func firstLookHeading(city: String?) -> String {
        guard let city, !city.isEmpty else { return "Your first look · Today" }
        return "Your first look · Today, \(city)"
    }

    /// Frame 34's weather line (design frame 34 + Android's
    /// weatherLine composition): "{feels-like} feels like ·
    /// {condition}", plus " · {n} items" when the outfit has items.
    /// Together with the heading this is the frame's two-line
    /// context composition — the RW-14 single computed line
    /// ("{place} · {feels-like}") is not frame 34's treatment.
    static func firstLookWeatherLine(feelsLike: String, condition: String, itemCount: Int) -> String {
        var line = "\(feelsLike) feels like · \(condition)"
        if itemCount > 0 { line += " · \(itemCount) items" }
        return line
    }

    /// RW-16.15 (spec C2j, both platforms verbatim): the save
    /// confirmation the flow shows after a successful commit,
    /// before it dismisses — the explicit "it saved" the field
    /// report found missing on both platforms.
    static let saveConfirmed = "Your photos are saved."
    /// Spec §7 G1, verbatim: the progress line's title while the
    /// server reports the current cell's look as making.
    static let progressTitle = "Making this outfit yours…"
    /// Spec §7 G2, verbatim as AMENDED Nov 1, 2026 (RW-16.16): the
    /// original "About 10 seconds" figure was false against a
    /// measured 72-second job and a 2–4 minute backend norm; the
    /// amended line promises no number the system misses.
    // GEN-5 (RW-29 FA-5): the standard-photo claim clause is
    // removed — Android's gen-3 WS-B copy audit removed the same
    // claim after it proved false in the Overland Park frames;
    // both platforms now state the identical honesty line.
    static let progressSubline = "This usually takes a few minutes. Your recommendation is ready either way."
    /// Spec §7 G5 (added Nov 1, 2026 — RW-16.16): the durable
    /// failed-look line at the hero, shown with the G6 action
    /// whenever the server reports the current cell's look as
    /// failed, held until the state changes. One string for every
    /// failure class in this build. DECIDED Nov 1, 2026
    /// (RW-16.16): Sol picked the final wording from the options
    /// put to him — a one-string swap.
    static let lookFailed = "Your look couldn't be made from these photos — tap to try again."
    /// Spec §7 G6 (added Nov 1, 2026 — RW-16.16): the failed
    /// line's retry action. User-initiated only; automatic
    /// re-request stays off.
    static let lookRetry = "Try again"
    /// GEN-2 I2 (W-16): frame 34's failed-look line. DEVIATION-COPY
    /// (no deck entry): the G5 line's "tap to try again" names an
    /// action frame 34 does not carry (the retry lives on the
    /// outfit screen's hero, G6), so this line states the failure,
    /// the accounting truth (a failed look is never charged —
    /// server doctrine, §1), and where the retry lives.
    static let firstLookFailed = "Your look couldn't be made from these photos. No free look was used — your outfit is ready either way, and you can try again from there."
    /// GEN-2 I2 (W-16): frame 34's claim line, shown ONLY once the
    /// generated photo is the photo on screen. Before this fix the
    /// line was a hardcoded literal rendered over the fork's stock
    /// photo; it is now the ready phase's caption, composed by
    /// FirstLookPresentation.
    static let firstLookReadyCaption = "You're in the photo now."
}

// MARK: - Photo intake source routing (RW-16.14)
//
// The intake offers two sources for the same picked-photo seam:
// the library (PhotosUI) and the camera (Sol, Oct 31: "Both apps
// should allow camera not just photo roll"). This routing is the
// pure decision the capture view renders: given what the user
// asked for and what the camera can do right now, where do we go?
// The camera outcomes are mirrored from AVFoundation's states
// without importing it here, so the matrix is unit-tested.
enum PhotoIntakeSource: Equatable {
    case camera
    case library
}

enum CameraAccess: Equatable {
    /// No usable camera on this device (or the picker source is
    /// unavailable, e.g. the Simulator).
    case unavailable
    case notDetermined
    case authorized
    case denied
    case restricted
}

enum PhotoIntakeRoute: Equatable {
    case camera
    case library
    /// The camera was asked for but can't be used: open the
    /// library anyway and show this line — never a dead end.
    case libraryWithNote(String)
}

enum PhotoIntakeRouting {
    static let cameraUnavailableNote = "No camera is available here — choose from your library instead."
    static let cameraDeniedNote = "Camera access is off for RunWear. You can turn it on in Settings, or choose from your library."

    static func route(requested: PhotoIntakeSource, cameraAccess: CameraAccess) -> PhotoIntakeRoute {
        switch requested {
        case .library:
            return .library
        case .camera:
            switch cameraAccess {
            case .authorized, .notDetermined:
                // Not-yet-asked still routes to the camera: the
                // system asks for permission in context.
                return .camera
            case .unavailable:
                return .libraryWithNote(cameraUnavailableNote)
            case .denied, .restricted:
                return .libraryWithNote(cameraDeniedNote)
            }
        }
    }
}

// MARK: - The canonical core matrix (GEN-5, Addendum 8 second follow-up)

/// The 20-cell canonical core matrix — the scaffold substrate
/// every entitled user's bank must hold: every temperature
/// bracket (FREEZING, COLD, COOL, MILD, WARM, HOT) at CLEAR ×
/// DAWN, CLEAR × DUSK, and RAIN × DAWN, plus WARM and HOT at
/// CLEAR × MIDDAY, each at the bracket's canonical outfit. This
/// is the RW-16.30 production fill's original matrix
/// (start-personal-fill v9), restored to the caller-named
/// contract: a forecast-only fill in winter would bank no
/// hot-weather photo at all, leaving nothing to scaffold from
/// when the user travels somewhere hot.
///
/// The ids are DERIVED, never hand-mirrored: each cell replays
/// its representative frame through the same shared engine the
/// serve path uses (the deployed fill's canonical table was
/// itself generated from engine outputs at these frames —
/// comfort NEUTRAL so adjusted == raw; calm wind; RAIN measured
/// rain at 1.0 mm; UV 2 at DAWN/DUSK dry, 1 in RAIN, 4 at
/// MIDDAY; hours 8 / 13 / 18; representative adjusted temps
/// FREEZING 12, COLD 27, COOL 42, MILD 57, WARM 72, HOT 85 °F),
/// so a derived id is by construction a launch-set cell id the
/// server will intersect, and engine drift moves both together.
enum CoreMatrixCells {
    private static let brackets: [(bracket: HeroTempBracket, feelsLikeF: Double)] = [
        (.FREEZING, 12), (.COLD, 27), (.COOL, 42),
        (.MILD, 57), (.WARM, 72), (.HOT, 85),
    ]

    static func ids(genderPreference: GenderPreference) -> [String] {
        brackets.flatMap { entry in
            var cells = [
                cell(genderPreference: genderPreference, weatherCode: 0,
                     feelsLikeF: entry.feelsLikeF, hour: 8, uvIndex: 2, precipitation: 0),
                cell(genderPreference: genderPreference, weatherCode: 0,
                     feelsLikeF: entry.feelsLikeF, hour: 18, uvIndex: 2, precipitation: 0),
                cell(genderPreference: genderPreference, weatherCode: 61,
                     feelsLikeF: entry.feelsLikeF, hour: 8, uvIndex: 1, precipitation: 1.0),
            ]
            if entry.bracket == .WARM || entry.bracket == .HOT {
                cells.append(cell(genderPreference: genderPreference, weatherCode: 0,
                                  feelsLikeF: entry.feelsLikeF, hour: 13, uvIndex: 4, precipitation: 0))
            }
            return cells
        }
    }

    private static func cell(genderPreference: GenderPreference, weatherCode: Int,
                             feelsLikeF: Double, hour: Int, uvIndex: Double,
                             precipitation: Double) -> String {
        let outfit = OutfitRecommendationService.shared.getRecommendation(
            for: feelsLikeF, rawTemperature: feelsLikeF,
            isRaining: precipitation > 0, windSpeed: 2, humidity: 50,
            uvIndex: uvIndex, precipitation: precipitation, hour: hour
        )
        return PersonalCombination.make(
            genderPreference: genderPreference,
            weatherCode: weatherCode,
            feelsLikeFahrenheit: feelsLikeF,
            hour: hour,
            outfit: outfit
        ).combinationId
    }
}
