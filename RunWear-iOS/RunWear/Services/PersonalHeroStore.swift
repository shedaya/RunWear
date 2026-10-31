import Foundation
import Combine

// MARK: - RW-14 "Make it me" — feature store
//
// The one state machine for personalization: stock / free /
// subscribed, the free-allowance count, the versioned cell cache,
// generation polling, and the upsell trigger. Views read this store;
// WeatherViewModel asks it to resolve the hero for the current cell.
//
// Doctrine (spec §1): the recommendation is never held hostage. Every
// failure in here degrades to the pool hero, silently. A cell whose
// generation fails the backend gate simply never resolves — the pool
// serves, no error state exists, and (server-side) the free
// allowance is not charged.
@MainActor
final class PersonalHeroStore: ObservableObject {
    enum Mode: Equatable {
        case stock       // never opted in, or deleted
        case free        // opted in, allowance rules
        case subscribed  // opted in + make_it_me entitlement active
    }

    static let freeAllowance = 5

    @Published private(set) var mode: Mode = .stock
    @Published private(set) var freeGenerationsUsed = 0
    @Published private(set) var referenceCount = 0
    /// True when the server cannot mint a session (anonymous sign-ins
    /// disabled project-side). The feature stays in stock; the opt-in
    /// flow surfaces one honest line instead of pretending.
    @Published private(set) var authUnavailable = false
    /// Cells with a user-initiated generation in flight (onboarding's
    /// first look, a Settings-initiated opt-in) — drives the G1/G2
    /// progress line. Browsing-triggered generations swap silently.
    @Published private(set) var userInitiatedPending: Set<String> = []
    /// The cell the server refused with allowance_exhausted — the
    /// upsell trigger (spec §5).
    @Published private(set) var exhaustedCell: String?
    /// Bumped whenever a new personalized hero lands, so views can
    /// react (the view model does the actual swap via awaitHero).
    @Published private(set) var landedHeroVersion = 0

    let billing = MakeItMeBilling()

    private let api = PersonalHeroAPI.shared
    private var cache = PersonalCellCache()
    private var requestedCells: Set<String> = []
    private var pollingTasks: [String: Task<String?, Never>] = [:]
    private var fillTask: Task<Void, Never>?
    private var bootstrapped = false
    private var cancellables = Set<AnyCancellable>()

    init() {
        billing.onEntitlementChange = { [weak self] active in
            Task { @MainActor in
                self?.entitlementDidChange(active: active)
            }
        }
        billing.$isSubscribed
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    var isOptedIn: Bool { mode != .stock }
    var freeRemaining: Int { max(0, Self.freeAllowance - freeGenerationsUsed) }

    // MARK: - Bootstrap

    /// Silent at launch: establish (or restore) the Supabase session,
    /// read the profile, configure billing against the user id. Any
    /// failure leaves the app exactly as it was — stock heroes.
    func bootstrap() async {
        guard !bootstrapped else { return }
        bootstrapped = true
        do {
            let session = try await SupabaseAuthService.shared.ensureSession()
            authUnavailable = false
            billing.configure(appUserID: session.userId)
            await refreshProfile()
            if billing.isSubscribed {
                kickFillWithRetry()
            }
        } catch SupabaseAuthService.AuthError.anonymousSignInDisabled {
            authUnavailable = true
        } catch {
            // Offline / transient: stay stock; opt-in retries on tap.
        }
    }

    func refreshProfile() async {
        guard let profile = try? await api.fetchProfile() else { return }
        freeGenerationsUsed = profile.freeGenerationsUsed
        if profile.personalizationOn {
            if mode == .stock { mode = billing.isSubscribed ? .subscribed : .free }
            referenceCount = (try? await api.fetchReferencePaths())?.count ?? referenceCount
        } else if mode != .stock {
            mode = .stock
        }
    }

    private func entitlementDidChange(active: Bool) {
        if isOptedIn {
            mode = active ? .subscribed : .free
        }
        if active {
            kickFillWithRetry()
        }
    }

    // MARK: - Hero resolution (called by WeatherViewModel)

    /// Resolve the personalized hero URL for a cell, if one exists
    /// now. On a miss for an opted-in user, an on-demand generation
    /// is requested (once per cell per session) and polling starts —
    /// awaitHero(_:) delivers the URL when it lands.
    func resolveHero(for combination: PersonalCombination) async -> String? {
        let cell = combination.combinationId
        if let fresh = cache.freshURL(for: cell, now: Date()) {
            // Still revalidate in the background: a version bump
            // (replenishment rotation, regeneration) must replace
            // the on-screen image on the next display.
            Task { await self.revalidate(combination: combination) }
            return fresh
        }
        return await lookupAndMaybeGenerate(combination: combination, userInitiated: false)
    }

    private func revalidate(combination: PersonalCombination) async {
        _ = await lookupAndMaybeGenerate(combination: combination, userInitiated: false)
    }

    private func lookupAndMaybeGenerate(combination: PersonalCombination, userInitiated: Bool) async -> String? {
        let cell = combination.combinationId
        guard let lookup = try? await api.getPersonalHero(combinationId: cell) else {
            return nil
        }
        switch cache.resolve(lookup: lookup, now: Date()) {
        case .show(let url):
            return url
        case .none:
            break
        }
        guard isOptedIn else { return nil }
        await requestGenerationIfNeeded(combination: combination, userInitiated: userInitiated)
        return nil
    }

    /// Wait for a pending generation to land (polls the serving
    /// endpoint; generation takes ~2–4 min server-side). Returns the
    /// URL when the hero lands, nil when the poll budget runs out —
    /// a gate-failed cell simply never lands, and the pool keeps
    /// serving with nothing said (spec G4).
    func awaitHero(for combination: PersonalCombination) async -> String? {
        let cell = combination.combinationId
        if let existing = pollingTasks[cell] {
            return await existing.value
        }
        let task = Task<String?, Never> { [weak self] in
            guard let self else { return nil }
            var landed: String?
            for _ in 0..<18 {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                if Task.isCancelled { break }
                guard let lookup = try? await self.api.getPersonalHero(combinationId: cell) else { continue }
                if case .show(let url) = self.cache.resolve(lookup: lookup, now: Date()) {
                    landed = url
                    break
                }
            }
            self.userInitiatedPending.remove(cell)
            self.pollingTasks[cell] = nil
            if landed != nil { self.landedHeroVersion += 1 }
            return landed
        }
        pollingTasks[cell] = task
        return await task.value
    }

    /// Kick generation for the current cell as a USER-initiated
    /// action (onboarding's "Make my first look", a Settings opt-in):
    /// the progress line shows for these cells.
    func startUserInitiatedGeneration(for combination: PersonalCombination) {
        userInitiatedPending.insert(combination.combinationId)
        Task {
            _ = await lookupAndMaybeGenerate(combination: combination, userInitiated: true)
            _ = await awaitHero(for: combination)
        }
    }

    private func requestGenerationIfNeeded(combination: PersonalCombination, userInitiated: Bool) async {
        let cell = combination.combinationId
        guard !requestedCells.contains(cell) else { return }
        requestedCells.insert(cell)
        if userInitiated { userInitiatedPending.insert(cell) }
        do {
            let response = try await api.requestGeneration(combination: combination)
            if let used = response.freeGenerationsUsed {
                freeGenerationsUsed = used
            }
            switch response.status {
            case "already_available":
                // A hero exists but the lookup raced it; re-lookup.
                if let lookup = try? await api.getPersonalHero(combinationId: cell),
                   case .show = cache.resolve(lookup: lookup, now: Date()) {
                    landedHeroVersion += 1
                }
                userInitiatedPending.remove(cell)
            default:
                break // queued / already_queued — polling delivers it
            }
        } catch PersonalHeroAPIError.allowanceExhausted(let used) {
            freeGenerationsUsed = used
            if mode == .free {
                exhaustedCell = cell
            }
            userInitiatedPending.remove(cell)
        } catch {
            // consent/references missing or transient: stay silent,
            // pool serves. Allow a later display to retry.
            requestedCells.remove(cell)
            userInitiatedPending.remove(cell)
        }
    }

    // MARK: - Opt-in / replace / delete

    /// The §2.3 sub-flow's commit point: consent first, then uploads,
    /// then registration — in that order, because the server enforces
    /// it (requests before consent are refused).
    func completeOptIn(jpegPhotos: [Data], genderPreference: GenderPreference) async throws {
        _ = try await SupabaseAuthService.shared.ensureSession()
        authUnavailable = false
        try await api.recordConsent(genderSegment: PersonalCombination.genderSegment(for: genderPreference))
        for (index, data) in jpegPhotos.enumerated() {
            try await api.uploadReference(jpegData: data, position: index + 1)
        }
        try await api.registerReferences(positions: Array(1...jpegPhotos.count))
        referenceCount = jpegPhotos.count
        mode = billing.isSubscribed ? .subscribed : .free
        requestedCells.removeAll()
        exhaustedCell = nil
        await refreshProfile()
    }

    /// §3 photo replacement: same attestation + quality check in the
    /// view; consent persists server-side. Today's look is asked to
    /// regenerate right away at no allowance cost (J4) — note the
    /// shipped backend answers already_available for a cell that
    /// already holds a hero (no force-regenerate path exists in the
    /// contract), so an existing hero keeps serving until a new
    /// generation path produces a replacement; new cells generate
    /// from the new references immediately.
    func replaceReferences(jpegPhotos: [Data], currentCombination: PersonalCombination?) async throws {
        for (index, data) in jpegPhotos.enumerated() {
            try await api.uploadReference(jpegData: data, position: index + 1)
        }
        try await api.registerReferences(positions: Array(1...jpegPhotos.count))
        referenceCount = jpegPhotos.count
        if let currentCombination {
            requestedCells.remove(currentCombination.combinationId)
            startUserInitiatedGeneration(for: currentCombination)
        }
    }

    /// Delete my photos (spec §3): server-verified erasure first,
    /// then the local state returns to stock — heroes revert because
    /// resolveHero finds nothing for a stock user.
    func deleteEverything() async throws {
        try await api.deleteMyPhotos()
        cache.removeAll()
        requestedCells.removeAll()
        pollingTasks.values.forEach { $0.cancel() }
        pollingTasks.removeAll()
        userInitiatedPending.removeAll()
        exhaustedCell = nil
        freeGenerationsUsed = 0
        referenceCount = 0
        mode = .stock
    }

    // MARK: - Subscription fill (server-gated)

    /// On entitlement, ask the server to pre-generate the matrix.
    /// The server's entitled flag is set by the billing webhook and
    /// may lag the on-device entitlement by seconds-to-minutes; a
    /// not_entitled answer retries with patience instead of failing.
    private func kickFillWithRetry() {
        fillTask?.cancel()
        fillTask = Task { [weak self] in
            guard let self else { return }
            for attempt in 0..<6 {
                do {
                    _ = try await self.api.startPersonalFill()
                    return
                } catch PersonalHeroAPIError.notEntitled {
                    if attempt == 5 { return }
                    try? await Task.sleep(nanoseconds: 30_000_000_000)
                } catch {
                    return
                }
            }
        }
    }

    // MARK: - Upsell (spec §5)

    private static let upsellLastDismissedKey = "rw14.upsell.lastDismissedAt"
    private static let upsellDismissedCellsKey = "rw14.upsell.dismissedCells"

    func shouldShowUpsell(currentCell: String) -> Bool {
        let defaults = UserDefaults.standard
        let last = defaults.object(forKey: Self.upsellLastDismissedKey) as? Date
        let dismissed = Set(defaults.stringArray(forKey: Self.upsellDismissedCellsKey) ?? [])
        return UpsellEligibility.shouldShow(
            isSubscribed: mode == .subscribed,
            freeGenerationsUsed: freeGenerationsUsed,
            freeAllowance: Self.freeAllowance,
            exhaustedCell: exhaustedCell,
            currentCell: currentCell,
            lastDismissedAt: last,
            dismissedCells: dismissed,
            now: Date()
        )
    }

    func dismissUpsell(currentCell: String) {
        let defaults = UserDefaults.standard
        defaults.set(Date(), forKey: Self.upsellLastDismissedKey)
        var dismissed = Set(defaults.stringArray(forKey: Self.upsellDismissedCellsKey) ?? [])
        dismissed.insert(currentCell)
        defaults.set(Array(dismissed), forKey: Self.upsellDismissedCellsKey)
    }
}
