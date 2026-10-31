import Foundation
import Combine

// MARK: - RW-14 "Make it me" — pure model layer
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
}

extension PersonalHeroResolving {
    /// Defaults so lightweight conformers (test doubles) carry no
    /// flag surface; PersonalHeroStore provides the real ones.
    func personalHeroId(for combination: PersonalCombination) -> String? { nil }
    func flagPersonalHero(combination: PersonalCombination, reason: PersonalHeroFlagReason) async -> Bool { false }
}

// MARK: - Backend DTOs

/// get-personal-hero response (backend §2.1).
struct PersonalHeroLookup: Decodable {
    let found: Bool
    let combinationId: String
    let heroId: String?
    let variant: Int?
    let imageUrl: String?
    let version: Int?
    let replenishmentQueued: Int?

    enum CodingKeys: String, CodingKey {
        case found
        case combinationId = "combination_id"
        case heroId = "hero_id"
        case variant
        case imageUrl = "image_url"
        case version
        case replenishmentQueued = "replenishment_queued"
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

    /// The label shown in the flag menu.
    var label: String {
        switch self {
        case .doesntLookLikeMe: return "This doesn't look like me"
        case .looksBroken: return "Looks broken"
        case .inappropriate: return "Inappropriate"
        case .outfitMismatch: return "Doesn't match the outfit"
        }
    }
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

    /// Run the commit: onCommit first; on success the committing
    /// flag clears and THEN onFinish is invoked — synchronously,
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
        return Task { @MainActor in
            do {
                try await onCommit(photos)
            } catch {
                isCommitting = false
                failure = CommitFailurePresentation.forError(error)
                return
            }
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

    var specString: String {
        switch self {
        case .faceUnclear:
            return "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
        case .multiplePeople:
            return "This photo has more than one person in it. Pick one that's just you."
        case .tooDark:
            return "This one is too dark to use. Try a photo in brighter light."
        case .tooClose:
            return "This one is cropped too close. A head-and-shoulders or half-body photo works best."
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

    static func rejection(
        faceCount: Int,
        largestFaceAreaRatio: Double,
        meanLuminance: Double
    ) -> PhotoRejection? {
        if faceCount == 0 { return .faceUnclear }
        if faceCount > 1 { return .multiplePeople }
        if meanLuminance < darkThreshold { return .tooDark }
        if largestFaceAreaRatio > tooCloseThreshold { return .tooClose }
        return nil
    }
}
