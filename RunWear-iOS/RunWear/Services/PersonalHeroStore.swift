import Foundation
import Combine

// MARK: - RW-14 "Make It Me" — feature store
//
// The one state machine for personalization: stock / free /
// subscribed, the free-allowance count, the versioned cell cache,
// generation polling, and the upsell trigger. Views read this store;
// WeatherViewModel asks it to resolve the hero for the current cell.
//
// Doctrine (spec §1): the recommendation is never held hostage. Every
// failure in here degrades to the pool hero, and (server-side) the
// free allowance is not charged for a look that never delivers.
// Since RW-16.16 the degradation is no longer silent: the server
// reports each cell's look state (making / ready / failed + reason
// class) and the hero renders it durably (spec §7 G5/G6; G4's
// silence is superseded). What did NOT change: the pool photo stays
// up in every non-ready state, and a failed look is retried only by
// the user's tap — never automatically.
@MainActor
final class PersonalHeroStore: ObservableObject, PersonalHeroResolving {
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
    /// first look, a Settings-initiated opt-in). Retained as
    /// bookkeeping; since RW-16.16 the hero's progress/failed
    /// surfaces are driven by `lookStates`, not by this set.
    @Published private(set) var userInitiatedPending: Set<String> = []
    /// RW-16.16: the per-cell look state as the SERVER last reported
    /// it (get-personal-hero v5's look_state / look_reason), folded
    /// on EVERY lookup — resolution, revalidation, and every poll.
    /// This is what the hero renders: making shows the progress
    /// line whoever requested the generation (the save flow
    /// included), failed shows the G5 line with its retry, and the
    /// state survives navigation because it is re-derived from the
    /// server on every display, never held only in session memory.
    @Published private(set) var lookStates: [String: PersonalLookState] = [:]
    /// The cell the server refused with allowance_exhausted — the
    /// upsell trigger (spec §5).
    @Published private(set) var exhaustedCell: String?
    /// Bumped whenever a new personalized hero lands, so views can
    /// react (the view model does the actual swap via awaitHero).
    @Published private(set) var landedHeroVersion = 0
    /// GEN-3 (convergence band): the caller's real bank coverage
    /// (ready/total), read with the profile refresh. Nil until a
    /// read succeeds — the band shows only the real banking state.
    @Published private(set) var bankCoverage: BankCoverage?

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

    /// GEN-3 (X-9): looks in flight right now, from the same state
    /// the hero renders (the server's folded look states, plus
    /// user-initiated pending requests). The Settings status block
    /// reads this alongside the delivered counter so it can carry
    /// an in-flight look as pending instead of printing a settled
    /// meter the flow contradicts.
    var looksInFlight: Int {
        FreeLooksMeter.inFlightCount(
            lookStates: lookStates,
            userInitiatedPending: userInitiatedPending
        )
    }

    /// RW-16.16: fold one get-personal-hero payload into the
    /// per-cell look state the hero renders. Called on every lookup
    /// path so the rendered status is always the server's latest
    /// word for the cell.
    private func noteLookState(_ lookup: PersonalHeroLookup) {
        lookStates[lookup.combinationId] = PersonalLookState.from(lookup: lookup)
    }

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
            // RW-16.14 (the hook Lane B owed): reconcile BEFORE the
            // profile read, so a purchase the webhook never
            // delivered is already reflected in the state this
            // bootstrap renders. The server verifies; we only ask.
            await api.reconcileEntitlement()
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
            // GEN-3 (convergence band): the coverage read rides
            // the same refresh; a failed read leaves the previous
            // value in place.
            if let coverage = await api.fetchBankCoverage() {
                bankCoverage = coverage
                stallTracker.observe(coverage, now: Date())
            }
        } else if mode != .stock {
            mode = .stock
        }
        syncCoveragePolling()
    }

    /// GEN-5 (RW-29 FA-4): the ONE convergence-band gate both
    /// surfaces (home band, Settings band) read — subscribed
    /// AND not in a trial period. Trial users get on-demand
    /// looks and no bank (the model's premium-context rule), so
    /// the band never renders for them; Android suppresses it
    /// for trial users the same way.
    var convergenceBand: ConvergenceBandContent? {
        // GEN-5 (Addendum 8): a bank whose real ready count has
        // not advanced for the stall threshold renders the
        // band's stalled line — never ordinary in-progress
        // forever.
        ConvergenceBand.content(
            isSubscribed: mode == .subscribed && !billing.isInTrial,
            coverage: bankCoverage,
            stalled: bankCoverage.map { stallTracker.isStalled($0, now: Date()) } ?? false
        )
    }

    /// GEN-5 (Addendum 8): the bank stall tracker (pure rule in
    /// the model layer; the store feeds it every coverage read).
    private var stallTracker = BankStallTracker()

    // MARK: - Bank coverage polling (GEN-5, RW-29 EX-15)
    //
    // Build 25 re-read coverage ONLY as a passenger on profile
    // refreshes, so a bank that finished filling server-side
    // mid-session left the convergence band standing on stale
    // counts until some unrelated refresh happened by. While
    // the band's own condition holds (subscribed, coverage
    // engaged, not yet complete), the store now re-reads
    // coverage on a slow poll until the bank completes — the
    // band retires on the bank's actual completion, in-session.

    private var coveragePollTask: Task<Void, Never>?

    private var bandConditionHolds: Bool {
        // Exactly the band's own visibility (FA-4 gate
        // included): the poll lives while the band shows.
        convergenceBand != nil
    }

    private func syncCoveragePolling() {
        if bandConditionHolds {
            guard coveragePollTask == nil else { return }
            coveragePollTask = Task { [weak self] in
                guard let self else { return }
                // ~20 minutes at 30-second intervals — far past
                // any honest fill; the poll is a freshness
                // mechanism, not a daemon.
                for _ in 0..<40 {
                    try? await Task.sleep(nanoseconds: 30_000_000_000)
                    if Task.isCancelled || !self.bandConditionHolds { break }
                    if let coverage = await self.api.fetchBankCoverage() {
                        self.bankCoverage = coverage
                        self.stallTracker.observe(coverage, now: Date())
                    }
                }
                self.coveragePollTask = nil
            }
        } else {
            coveragePollTask?.cancel()
            coveragePollTask = nil
        }
    }

    private func entitlementDidChange(active: Bool) {
        if isOptedIn {
            mode = active ? .subscribed : .free
        }
        // RW-16.14: every RevenueCat-observed change also asks the
        // server to reconcile (Lane B backstop) and re-reads the
        // profile it may have just moved — this is the path a fresh
        // purchase takes when the webhook hasn't landed.
        Task {
            await api.reconcileEntitlement()
            await refreshProfile()
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
            // A cached verified hero is serving: that IS the ready
            // state (RW-16.16) — unless the server has already told
            // us something newer for this cell (a failed regen on a
            // keep-serving cell must not be painted over here; the
            // background revalidation below folds the server's word
            // either way).
            if lookStates[cell] == nil { lookStates[cell] = .ready }
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
        // RW-16.16: the server's look state folds in BEFORE any
        // request decision, so the hero renders the truth for this
        // cell even when the request guard below suppresses a new
        // automatic request.
        noteLookState(lookup)
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
    /// URL when the hero lands, nil when the poll resolves FAILED or
    /// the budget runs out. Every poll folds the server's look state
    /// (RW-16.16): a terminal failure ends the poll with the failed
    /// state standing on the hero — the pool photo stays up, the
    /// failed line stays up, and nothing about the state is erased
    /// when the poll gives up (the pre-RW-16.16 G4 behavior).
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
                self.noteLookState(lookup)
                if case .show(let url) = self.cache.resolve(lookup: lookup, now: Date()) {
                    landed = url
                    break
                }
                // A terminal failure resolves the poll: the failed
                // state folded above is the durable surface now.
                if self.lookStates[cell]?.isFailed == true { break }
            }
            self.userInitiatedPending.remove(cell)
            self.pollingTasks[cell] = nil
            if landed != nil {
                self.landedHeroVersion += 1
                // GEN-2 I2 (W-18): a landing IS the server's
                // charge moment — the worker increments
                // free_generations_used as it delivers the
                // verified hero. Re-read the profile here so the
                // ONE counter every surface binds to (this store's
                // freeGenerationsUsed) reflects the delivery in
                // this session. Before this, nothing re-read it
                // after opt-in, so Settings stood at its pre-
                // generation value ("0 of 5") while frame 34
                // announced the debit from a hardcoded line.
                await self.refreshProfile()
            }
            return landed
        }
        pollingTasks[cell] = task
        return await task.value
    }

    /// RW-16.16 (spec §7 G6): the failed line's "Try again" — the
    /// ONLY re-request path for a failed cell. Clears the cell's
    /// once-per-session marker so the request below actually fires
    /// (the marker's anti-loop role for AUTOMATIC requests is
    /// untouched), requests through the same path as any first
    /// request — the server dedupes against an active job, a failed
    /// look never charged the free allowance, and a retry that
    /// delivers charges exactly as a first delivery does — then
    /// polls; each poll folds the server state, so the hero moves
    /// failed → making → ready (or back to failed) on the server's
    /// word alone.
    func retryLook(for combination: PersonalCombination) {
        let cell = combination.combinationId
        requestedCells.remove(cell)
        Task {
            await requestGenerationIfNeeded(combination: combination, userInitiated: false)
            _ = await awaitHero(for: combination)
        }
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
                if let lookup = try? await api.getPersonalHero(combinationId: cell) {
                    noteLookState(lookup)
                    if case .show = cache.resolve(lookup: lookup, now: Date()) {
                        landedHeroVersion += 1
                        // W-18: the hero this response names was
                        // delivered (and, if chargeable, charged)
                        // before this request — re-read the one
                        // counter so every surface agrees.
                        await refreshProfile()
                    }
                }
                userInitiatedPending.remove(cell)
            default:
                // queued / already_queued — RW-16.16: the hero shows
                // the making state from the moment the request lands,
                // whoever sent it (the save flow included — its
                // outcome is no longer discarded); the polls fold
                // the server's own word from here.
                lookStates[cell] = .making
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
        try await api.registerReferences(positions: ReferenceCommitSet.positions(photoCount: jpegPhotos.count))
        referenceCount = jpegPhotos.count
        mode = billing.isSubscribed ? .subscribed : .free
        requestedCells.removeAll()
        exhaustedCell = nil
        await refreshProfile()
    }

    /// §3 photo replacement: same attestation + quality check in the
    /// view; consent persists server-side. Today's look is asked to
    /// regenerate right away at no allowance cost (J4): since the
    /// backend (b) addition, register_references detects the changed
    /// set, retires the verified heroes made from the old references
    /// (their cells' versions bump, so cached images stop serving),
    /// and the re-request below enqueues a regeneration job.
    func replaceReferences(jpegPhotos: [Data], currentCombination: PersonalCombination?) async throws {
        for (index, data) in jpegPhotos.enumerated() {
            try await api.uploadReference(jpegData: data, position: index + 1)
        }
        try await api.registerReferences(positions: ReferenceCommitSet.positions(photoCount: jpegPhotos.count))
        referenceCount = jpegPhotos.count
        if let currentCombination {
            requestedCells.remove(currentCombination.combinationId)
            startUserInitiatedGeneration(for: currentCombination)
        }
    }

    // MARK: - Personal-hero flag (RW-14 acceptance (19))

    /// The backend id of the verified hero this cell currently
    /// serves from cache, if one is held.
    func personalHeroId(for combination: PersonalCombination) -> String? {
        cache.heroId(for: combination.combinationId)
    }

    /// WS-7: the server's folded look state for this cell (the
    /// PersonalHeroResolving surface the paint layer classifies a
    /// personal serve from — see WeatherViewModel's serve records).
    func lookState(for combination: PersonalCombination) -> PersonalLookState {
        lookStates[combination.combinationId] ?? .none
    }

    /// "This doesn't look like me" (spec §4): flag the cell's
    /// personalized hero with the backend. On success the server
    /// has already retired the hero (its cell version bumped) and
    /// queued a regeneration; locally the cell's cache entry is
    /// evicted and its once-per-session request marker cleared,
    /// so the very next resolution re-looks-up, finds nothing,
    /// and the pool photo serves while the regeneration is
    /// pending (the re-request answers already_queued and the
    /// poll picks the new hero up when it lands). Returns whether
    /// the flag landed; a failed flag changes nothing, silently —
    /// the flag must never disturb the screen it was sent from.
    func flagPersonalHero(combination: PersonalCombination, reason: PersonalHeroFlagReason) async -> Bool {
        let cell = combination.combinationId
        guard let heroId = cache.heroId(for: cell) else { return false }
        do {
            try await api.flagPersonalHero(heroId: heroId, reason: reason.code)
        } catch {
            return false
        }
        cache.evict(combinationId: cell)
        requestedCells.remove(cell)
        return true
    }

    /// Delete my photos (spec §3): server-verified erasure first,
    /// then the local state returns to stock — heroes revert because
    /// resolveHero finds nothing for a stock user.
    func deleteEverything() async throws {
        try await api.deleteMyPhotos()
        resetToStock()
    }

    /// Delete my data (RW-16.30 decision 5a): the full account
    /// deletion. Same local reset as the photos erasure, plus the
    /// local session is forgotten — the account it belonged to no
    /// longer exists server-side, so the next session is a fresh
    /// anonymous account, exactly like a first launch.
    func deleteAllData() async throws {
        try await api.deleteMyData()
        resetToStock()
        await SupabaseAuthService.shared.clearLocalSession()
    }

    private func resetToStock() {
        cache.removeAll()
        lookStates.removeAll()
        requestedCells.removeAll()
        pollingTasks.values.forEach { $0.cancel() }
        pollingTasks.removeAll()
        userInitiatedPending.removeAll()
        exhaustedCell = nil
        freeGenerationsUsed = 0
        referenceCount = 0
        bankCoverage = nil
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
                    // GEN-5 (RW-29 EX-15): the fill starting is
                    // the moment the band appears — re-read
                    // coverage for it directly, not as a
                    // passenger on some later refresh.
                    await self.refreshProfile()
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
