package com.runwear.app.makeitme

import androidx.datastore.preferences.core.stringPreferencesKey
import com.runwear.shared.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The RW-14 personal layer's local state + serving logic, shared by the
 * main screen (hero overlay, upsell) and the Make-it-me surfaces
 * (onboarding fork, upload flow, Settings section).
 *
 * The SERVER is the truth for the free counter and the entitlement flag
 * (personal_profiles); this repository mirrors them into a StateFlow and
 * refreshes the mirror at launch and after events that move them (a
 * verified delivery increments the counter server-side; a delete wipes
 * the profile). Local persistence covers what the server doesn't hold:
 * the fork decision, the per-cell versioned hero cache (acceptance 16),
 * the requested-cell set (so a gate-failed cell is never auto-requested
 * in a loop), and the upsell dismissal record (spec §5.2).
 */
@Singleton
class PersonalHeroRepository @Inject constructor(
    private val service: PersonalHeroService,
    private val preferencesRepository: PreferencesRepository,
    private val billing: BillingManager
) {
    data class PersonalState(
        val personalizationOn: Boolean = false,
        val freeGenerationsUsed: Int = 0,
        /** The server-side entitlement flag (webhook + reconciliation driven). */
        val entitled: Boolean = false,
        val gender: String = "UNISEX",
        val referenceCount: Int = 0,
        val forkDecided: Boolean = false,
        /** True once the profile has been read from (or written to) the server. */
        val synced: Boolean = false,
        /**
         * Gen-3 X-9: true while ANY cell's look is in flight, from
         * the same per-cell status truth the hero renders
         * ([lastKnownStatus]). This is the input the gen-2 meter
         * lacked: the delivered counter alone can never describe
         * an in-flight look. Updated by [noteLookStatus] and
         * [cellLookFor] — every path that learns a look's status.
         */
        val anyLookMaking: Boolean = false,
        /**
         * GEN-5 (RW-29 FA-10): the REAL in-flight count, from the
         * same per-cell status map — the Settings meter line
         * counts the looks actually being made, not a Boolean.
         */
        val looksMakingCount: Int = 0,
        /**
         * GEN-3 (convergence band): the caller's real bank coverage
         * (ready/total), read with the profile refresh. Null until a
         * read succeeds — the band shows only real counts.
         */
        val bankCoverage: PersonalHeroService.BankCoverage? = null
    )

    private val _state = MutableStateFlow(PersonalState())
    val state: StateFlow<PersonalState> = _state.asStateFlow()

    /**
     * GEN-5 (RW-28 Addendum 8, second follow-up): the selfie
     * epoch — bumped whenever the registered reference-photo set
     * CHANGES (a replacement set, not the first upload and not a
     * deletion). A new epoch retires the bank server-side, so the
     * owed fill set must be re-named against the fresh bank; the
     * main view model collects this and re-sends.
     */
    private val _selfieEpoch = MutableStateFlow(0)
    val selfieEpoch: StateFlow<Int> = _selfieEpoch.asStateFlow()
    private var lastReferencePaths: List<String>? = null

    /** Load persisted local state, then refresh the server mirror. */
    suspend fun initialize() {
        val forkDecided = preferencesRepository.getString(KEY_FORK_DECIDED) == "1"
        _state.value = _state.value.copy(forkDecided = forkDecided)
        refreshFromServer()
        syncBillingIdentity()
    }

    /**
     * The identity + entitlement sync (acceptance 17 + the RW-16.12
     * backstop), run at bootstrap, on entitlement change, and after a
     * purchase:
     *  1. link RevenueCat to the Supabase uid ([BillingManager.logIn]) —
     *     until this happens, this device's purchases sit under the
     *     SDK's anonymous id and the backend cannot find them;
     *  2. invoke reconcile-personal-entitlement — the server verifies
     *     the caller's state against RevenueCat and flips the
     *     `entitled` flag (kicking the matrix fill on a grant) without
     *     trusting anything the client claims;
     *  3. RW-137 (DESIGN §4.4 trigger 2): if the reconcile response
     *     reports `adoption_available` — RevenueCat proof links this
     *     restored, entitled identity to a prior uid — ask the
     *     server to execute the adoption (request-identity-adoption
     *     re-verifies the proof server-side; the reported uid is a
     *     lookup hint only, and a stale/wrong hint degrades to a
     *     silent no-op). Adoption is AUTOMATIC: a standing Premium
     *     property of the restored subscription, no opt-in, no new
     *     user-facing flow — the refreshed profile + bank below ARE
     *     the restored state, rendered by the existing surfaces;
     *  4. if the reconcile call completed, re-read the profile so the
     *     local mirror reflects any transition (and any adoption).
     * Silent and idempotent: no session, a failed link, or a failed
     * reconcile leaves local state exactly as it was.
     */
    suspend fun syncBillingIdentity() {
        if (!service.ensureSession()) return
        val uid = service.currentUserId() ?: return
        runCatching { billing.logIn(uid) }
        val outcome = service.reconcileEntitlement()
        if (!outcome.completed) return
        val source = IdentityAdoptionPolicy.sourceToAdopt(
            outcome.adoptionAvailable, uid
        )
        if (source != null) {
            // The result is deliberately not surfaced: a confirmed
            // adoption and a server-side no-op both end at the same
            // refresh, which renders whatever identity state the
            // server now holds for this uid.
            runCatching { service.requestIdentityAdoption(source) }
        }
        refreshFromServer()
    }

    /** Re-read the profile + reference count from the server. */
    suspend fun refreshFromServer() {
        val profile = service.fetchProfile() ?: return
        val refs = service.fetchReferencePaths()
        // GEN-5 (selfie epoch): a changed, non-empty reference set
        // is a new epoch — the bank refills against the new face.
        val previousRefs = lastReferencePaths
        if (previousRefs != null && previousRefs.isNotEmpty() &&
            refs.isNotEmpty() && previousRefs != refs
        ) {
            _selfieEpoch.value += 1
        }
        lastReferencePaths = refs
        // RW-FIXWAVE (review §2 row 19): keep the target set
        // durable. A non-empty in-memory set is persisted; an
        // empty one (fresh process) is restored from the persisted
        // set BEFORE the coverage read, so the read never runs
        // against the post-process-death empty denominator.
        if (fillTargetCells.isNotEmpty()) {
            preferencesRepository.setString(
                KEY_FILL_TARGET_CELLS, fillTargetCells.joinToString(",")
            )
        } else {
            val persisted = preferencesRepository.getString(KEY_FILL_TARGET_CELLS)
            if (!persisted.isNullOrBlank()) {
                fillTargetCells = persisted.split(",").filter { it.isNotBlank() }.toSet()
            }
        }
        // GEN-3 (convergence band): the coverage read rides the same
        // refresh; a failed read leaves the previous value in place.
        val coverage = if (profile.personalizationOn) {
            service.fetchBankCoverage(fillTargetCells, fillableCells)
                ?: _state.value.bankCoverage
        } else {
            _state.value.bankCoverage
        }
        // RW-151 client (Nov 10, 2026; DESIGN.md §5 reconcile rule):
        // after this coverage read, if fillable work is outstanding
        // and NOTHING is in flight — no non-terminal job, no fill
        // request outstanding — re-issue the fill ONCE for the
        // fillable remainder. The once-guard is the wire cell set
        // itself: a set already reconciled is not re-issued until a
        // new forecast derivation names a different set, so no loop
        // can form within a derivation. A re-issued fill that still
        // leaves outstanding work with no jobs is closed by RW-126
        // (terminally failed cells count as settled), never by an
        // indefinite "making".
        if (profile.entitled && profile.personalizationOn &&
            coverage != null && fillWireCells.isNotEmpty() &&
            fillWireCells != lastReconciledWireCells &&
            FillReconcilePolicy.shouldReissue(
                outstanding = coverage.outstanding,
                hasNonTerminalJob = coverage.hasNonTerminalJob,
                fillOutstanding = false
            )
        ) {
            if (service.startPersonalFill(fillWireCells)) {
                lastReconciledWireCells = fillWireCells
            }
        }
        _state.value = _state.value.copy(
            personalizationOn = profile.personalizationOn,
            freeGenerationsUsed = profile.freeGenerationsUsed,
            entitled = profile.entitled,
            gender = profile.gender,
            referenceCount = refs.size,
            synced = true,
            bankCoverage = coverage
        )
    }

    suspend fun markForkDecided() {
        preferencesRepository.setString(KEY_FORK_DECIDED, "1")
        _state.value = _state.value.copy(forkDecided = true)
    }

    // ── Opt-in ─────────────────────────────────────────────────────────

    /** Record consent (both attestations) for [gender]. */
    suspend fun recordConsent(gender: String): Boolean {
        val ok = service.recordConsent(gender)
        if (ok) {
            _state.value = _state.value.copy(
                personalizationOn = true,
                gender = gender,
                synced = true
            )
        }
        return ok
    }

    /**
     * Upload reference photos (JPEG bytes, 1–3) and register them.
     * Paths follow the backend layout: {uid}/refs/ref-N.jpg.
     */
    suspend fun uploadAndRegisterReferences(photos: List<ByteArray>): Boolean {
        if (!service.ensureSession()) return false
        val uid = service.currentUserId() ?: return false
        val paths = photos.mapIndexed { index, _ -> "$uid/refs/ref-${index + 1}.jpg" }
        for ((index, bytes) in photos.withIndex()) {
            if (!service.uploadReferencePhoto(paths[index], bytes)) return false
        }
        val registered = service.registerReferences(paths)
        if (registered) {
            _state.value = _state.value.copy(referenceCount = paths.size)
        }
        return registered
    }

    // ── Serving ────────────────────────────────────────────────────────

    data class PersonalHeroHit(
        val imageUrl: String,
        val version: Int,
        /** The backend hero row id — what a personal flag addresses. */
        val heroId: String? = null,
        /** RW-26: exact vs scaffold serve (see [ScaffoldRenderPolicy]). */
        val serveKind: PersonalServeKind = PersonalServeKind.EXACT
    )

    /**
     * RW-16.16: one cell's look as the server reports it — the
     * verified hero (if any) AND the look status, from the same
     * get-personal-hero call. The status is what the hero renders
     * (making / failed lines); it is server-driven so it survives
     * navigation and process death, and it is remembered per cell
     * so a transport failure mid-poll degrades to the last known
     * truth instead of erasing a durable state.
     */
    data class CellLook(val hit: PersonalHeroHit?, val status: PersonalLookStatus)

    private val lastKnownStatus = mutableMapOf<String, PersonalLookStatus>()

    /** Record a status learned outside a serve call (e.g. the
     *  upload flow's accepted generation request). */
    fun noteLookStatus(combinationId: String, status: PersonalLookStatus) {
        lastKnownStatus[combinationId] = status
        _state.value = _state.value.copy(
            anyLookMaking = lastKnownStatus.values.any { it == PersonalLookStatus.Making },
            looksMakingCount = lastKnownStatus.values.count { it == PersonalLookStatus.Making }
        )
    }

    suspend fun cellLookFor(combinationId: String): CellLook {
        // Make It Me OFF in this build (MakeItMeAvailability): no
        // personal hero is ever served and no look status is ever
        // reported — the pool hero serves and the hero renders no
        // personal lines. The backend is not consulted at all.
        if (!MakeItMeAvailability.ENABLED) {
            return CellLook(null, PersonalLookStatus.None)
        }
        val lookup = service.getPersonalHero(combinationId)
            ?: return CellLook(null, lastKnownStatus[combinationId] ?: PersonalLookStatus.None)
        val status = PersonalLookStatusPolicy.fromServer(
            found = lookup.found,
            lookState = lookup.lookState,
            lookReason = lookup.lookReason
        )
        noteLookStatus(combinationId, status)
        val hit = resolveHit(combinationId, lookup)
        return CellLook(hit, status)
    }

    /**
     * The verified personal hero for one cell, honoring the versioned
     * cache ([PersonalHeroCachePolicy]): a bumped version yields the new
     * signed URL (the on-screen image changes); an unchanged version
     * inside the freshness window reuses the cached URL.
     */
    suspend fun personalHeroFor(combinationId: String): PersonalHeroHit? =
        cellLookFor(combinationId).hit

    private suspend fun resolveHit(
        combinationId: String,
        lookup: PersonalHeroService.HeroLookup
    ): PersonalHeroHit? {
        // RW-130 / review §4.5 (cache honesty): a NON-EXACT serve
        // (scaffold / similar) must never be cached under the
        // requested cell, and a cached entry must never substitute
        // for the server's current non-exact answer — either would
        // strand the requested cell on a stand-in photo exactly as
        // RW-134 stranded it on stock. Non-exact serves paint from
        // the fresh payload and leave the cache untouched, so the
        // next resolution re-asks and the exact photo swaps in.
        val serveKind = ScaffoldRenderPolicy.kindFor(
            status = PersonalLookStatusPolicy.fromServer(
                found = lookup.found,
                lookState = lookup.lookState,
                lookReason = lookup.lookReason
            ),
            serveKindRaw = lookup.serveKind,
            found = lookup.found
        )
        val nonExact = ScaffoldRenderPolicy.isNonExactServe(serveKind)
        val cached = if (nonExact) null else readCachedEntry(combinationId)
        val decision = PersonalHeroCachePolicy.decide(
            cached = cached,
            found = lookup.found,
            serverVersion = lookup.version,
            serverUrl = lookup.imageUrl,
            nowMs = System.currentTimeMillis(),
            serverHeroId = lookup.heroId
        )
        return when (decision) {
            is PersonalHeroCachePolicy.Decision.Serve -> {
                if (!nonExact && (cached == null || cached.version != decision.version ||
                    cached.imageUrl != decision.imageUrl ||
                    cached.heroId != decision.heroId)
                ) {
                    writeCachedEntry(
                        combinationId,
                        PersonalHeroCachePolicy.CachedEntry(
                            version = decision.version,
                            imageUrl = decision.imageUrl,
                            fetchedAtMs = System.currentTimeMillis(),
                            heroId = decision.heroId
                        )
                    )
                }
                PersonalHeroHit(
                    decision.imageUrl, decision.version, decision.heroId,
                    serveKind = serveKind
                )
            }
            PersonalHeroCachePolicy.Decision.NoPersonalHero -> null
        }
    }

    private suspend fun readCachedEntry(combinationId: String): PersonalHeroCachePolicy.CachedEntry? {
        val raw = preferencesRepository.getString(cellCacheKey(combinationId)) ?: return null
        // RW-16.27 format: version|fetchedAt|heroId|imageUrl. Entries
        // persisted before RW-16.27 have three parts and no hero id —
        // they parse with heroId null and re-derive it on the next
        // fresh serve (a flag needs the id, so a stale entry can
        // delay — never misdirect — a flag).
        val parts = raw.split("|", limit = 4)
        val version = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val fetchedAt = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return when (parts.size) {
            4 -> PersonalHeroCachePolicy.CachedEntry(
                version, parts[3], fetchedAt, heroId = parts[2].takeIf { it.isNotBlank() }
            )
            3 -> PersonalHeroCachePolicy.CachedEntry(version, parts[2], fetchedAt)
            else -> null
        }
    }

    private suspend fun writeCachedEntry(
        combinationId: String,
        entry: PersonalHeroCachePolicy.CachedEntry
    ) {
        preferencesRepository.setString(
            cellCacheKey(combinationId),
            "${entry.version}|${entry.fetchedAtMs}|${entry.heroId ?: ""}|${entry.imageUrl}"
        )
    }

    /** Drop one cell's cached entry (e.g. after deletion or photo replace). */
    suspend fun invalidateCell(combinationId: String) {
        preferencesRepository.remove(cellCacheKey(combinationId))
    }

    private fun cellCacheKey(combinationId: String) =
        stringPreferencesKey("mim_cell_$combinationId")

    // ── Generation ─────────────────────────────────────────────────────

    /**
     * Cells for which a generation request has been ACCEPTED (queued or
     * already queued). Persisted so a gate-failed cell is never
     * AUTOMATICALLY re-requested on every launch (the anti-loop rule;
     * a photo replacement clears the set — new references = new
     * attempts — and the user can always retry explicitly from the
     * hero's failed line, spec §7 G6). Since RW-16.16 this set gates
     * ONLY the automatic request: what the user SEES for the cell is
     * the server's look state ([cellLookFor]), never this set — the
     * set's old side effect of also suppressing the progress line is
     * the silence defect RW-16.16 removed.
     */
    suspend fun wasCellRequested(combinationId: String): Boolean {
        val raw = preferencesRepository.getString(KEY_REQUESTED_CELLS) ?: return false
        return raw.split(",").any { it == combinationId }
    }

    private suspend fun markCellRequested(combinationId: String) {
        val raw = preferencesRepository.getString(KEY_REQUESTED_CELLS)
        val cells = (raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()).toMutableSet()
        cells.add(combinationId)
        preferencesRepository.setString(KEY_REQUESTED_CELLS, cells.joinToString(","))
    }

    private suspend fun unmarkCellRequested(combinationId: String) {
        val raw = preferencesRepository.getString(KEY_REQUESTED_CELLS) ?: return
        val cells = raw.split(",").filter { it.isNotBlank() && it != combinationId }
        preferencesRepository.setString(KEY_REQUESTED_CELLS, cells.joinToString(","))
    }

    /**
     * RW-16.27: "This doesn't look like me" (spec §4) — flag the
     * cell's personalized hero with the backend, mirroring iOS's
     * store. On success the server has already retired the hero and
     * queued a regeneration; locally the cell's cache entry is
     * evicted and its automatic-request marker cleared, so the very
     * next resolution re-looks-up, finds nothing serving, and the
     * pool photo serves while the regeneration is pending (the
     * server's look state reports MAKING and the poll picks the new
     * hero up when it lands). Returns whether the flag landed; a
     * failed flag changes nothing, silently — the flag must never
     * disturb the screen it was sent from.
     */
    suspend fun flagPersonalHero(
        combinationId: String,
        heroId: String,
        reason: PersonalHeroFlagReason
    ): Boolean {
        if (!service.flagPersonalHero(heroId, reason)) return false
        invalidateCell(combinationId)
        unmarkCellRequested(combinationId)
        return true
    }

    suspend fun clearRequestedCells() {
        preferencesRepository.remove(KEY_REQUESTED_CELLS)
    }

    suspend fun requestGeneration(
        combinationId: String,
        outfitItems: List<String>
    ): PersonalHeroService.RequestOutcome {
        // Make It Me OFF in this build (MakeItMeAvailability): a
        // generation request never reaches the backend, from any
        // caller. Callers already treat a Failed outcome as
        // "refused — re-read the server's truth", and the gated
        // cellLookFor above answers that re-read with None.
        if (!MakeItMeAvailability.generationRequestAllowed()) {
            return PersonalHeroService.RequestOutcome.Failed("make-it-me-unavailable")
        }
        val outcome = service.requestGeneration(combinationId, outfitItems)
        when (outcome) {
            is PersonalHeroService.RequestOutcome.Queued,
            is PersonalHeroService.RequestOutcome.AlreadyQueued -> {
                markCellRequested(combinationId)
                // Gen-3 X-9: an accepted request IS an in-flight
                // look, from this moment — every caller (intake,
                // home auto-request, retry) now feeds the meter's
                // in-flight input, not just the intake flow.
                noteLookStatus(combinationId, PersonalLookStatus.Making)
            }
            is PersonalHeroService.RequestOutcome.AlreadyAvailable ->
                markCellRequested(combinationId)
            else -> Unit
        }
        return outcome
    }

    /**
     * GEN-5 (RW-28 Addendum 8, Defects 1 + 2): the bank's target
     * set — the forecast cells the fill names. Noted by the main
     * view model on every forecast derivation (entitled or not),
     * consumed by the coverage read as its denominator and by the
     * fill call as its request. In-memory: it is re-derived from
     * the live forecast on the first weather load of every
     * session, which is also when the band first needs it.
     */
    @Volatile
    var fillTargetCells: Set<String> = emptySet()
        private set

    /**
     * RW-151 client: the FILLABLE subset of the target (DESIGN.md
     * §5) — accompanied forecast cells plus canonical core cells.
     * The coverage denominator is |Target ∩ Fillable|.
     */
    @Volatile
    var fillableCells: Set<String> = emptySet()
        private set

    /** RW-151 client: the composed WIRE entries (accompanied
     * forecast cells, bare core cells) — what a reconcile re-issue
     * re-sends, and its once-per-derivation guard. */
    @Volatile
    var fillWireCells: List<String> = emptyList()
        private set

    private var lastReconciledWireCells: List<String> = emptyList()

    fun noteFillTarget(cells: List<String>) {
        fillTargetCells = cells.toSet()
    }

    /**
     * RW-151 client: note one derivation's full fill picture — the
     * wire entries actually sent, the target ids they name, and
     * the fillable subset of that target.
     */
    fun noteFillDerivation(
        wireCells: List<String>,
        targetIds: Set<String>,
        fillableIds: Set<String>
    ) {
        fillWireCells = wireCells
        fillTargetCells = targetIds
        fillableCells = fillableIds
    }

    /** Subscriber forecast fill (server refuses non-entitled users). */
    suspend fun startPersonalFill(priorityCells: List<String>): Boolean =
        service.startPersonalFill(priorityCells)

    /**
     * GEN-5 (RW-28 Addendum 8, Item 3): the serve-path engagement
     * guarantee. The deployed get-personal-hero queues the
     * priority-15 convergence job for an entitled user's unowned
     * cell as a side effect of being CALLED — but the hero
     * resolution runs that call inside a cancellable job, behind
     * the pool fetch, so a superseded resolution can drop the
     * engagement for a cell without a trace (the 07:43 CST serve
     * class). This entry point performs the same lookup from the
     * caller's own scope, once per cell per process, so the
     * engagement does not depend on the hero job's lifetime. The
     * lookup's result is discarded here — the resolution's own
     * lookup still owns what renders.
     */
    private val engagementEnsured = mutableSetOf<String>()

    suspend fun ensureEngagement(combinationId: String) {
        if (!engagementEnsured.add(combinationId)) return
        runCatching { cellLookFor(combinationId) }
    }

    // ── Delete ─────────────────────────────────────────────────────────

    /**
     * Delete everything (acceptance 5): server erasure via
     * delete-my-photos, then local state, caches, and the session are
     * cleared. Returns true only when the SERVER confirmed the erasure —
     * the Settings confirmation copy (S14) claims only what the system
     * has confirmed (spec §3).
     */
    suspend fun deleteEverything(): Boolean {
        service.deleteMyPhotos() ?: return false
        clearLocalPersonalState()
        return true
    }

    /**
     * Delete my data (RW-16.30 decision 5a): the full account
     * deletion — server erasure via delete-my-data (photos, event
     * logs, and the account itself), then the same local cleanup as
     * the photos erasure. Returns true only when the SERVER's
     * receipt confirms the account is deleted; a receipt without
     * that confirmation (account_deleted = false) maps to false so
     * the UI offers a retry instead of claiming the deletion.
     */
    suspend fun deleteAllData(): Boolean {
        service.deleteMyData() ?: return false
        clearLocalPersonalState()
        return true
    }

    /**
     * Local mirrors after a confirmed server erasure: the profile is
     * gone server-side; clear all personal residue on this phone
     * (spec S12: "from our server and this phone").
     */
    private suspend fun clearLocalPersonalState() {
        val raw = preferencesRepository.getString(KEY_REQUESTED_CELLS)
        raw?.split(",")?.filter { it.isNotBlank() }?.forEach { invalidateCell(it) }
        clearRequestedCells()
        lastKnownStatus.clear()
        // Cell caches for cells never requested this install can still
        // exist via serving hits; the profile wipe makes them
        // unreachable (personalization is off) and they hold only
        // expired signed URLs. The requested-set sweep above covers the
        // generated ones.
        // RW-137 S4c (DESIGN §4.8 closure item 3): the deleted
        // identity must not silently re-authenticate OR quietly
        // re-derive its state on this phone. The session keys go
        // (access / refresh / user id — clearSession), so the next
        // bootstrap mints a genuinely fresh anonymous session; and
        // the identity-scoped fill state goes with it — the
        // persisted coverage target would otherwise be restored by
        // the next refresh and re-applied to the NEW uid, and the
        // in-memory fill picture (target / fillable / wire /
        // reconciled sets, reference paths, engagement set, selfie
        // epoch inputs) all described the deleted identity.
        preferencesRepository.remove(KEY_FILL_TARGET_CELLS)
        fillTargetCells = emptySet()
        fillableCells = emptySet()
        fillWireCells = emptyList()
        lastReconciledWireCells = emptyList()
        lastReferencePaths = null
        engagementEnsured.clear()
        service.clearSession()
        _state.value = PersonalState(forkDecided = _state.value.forkDecided, synced = true)
    }

    // ── Upsell dismissal (spec §5.2) ───────────────────────────────────

    data class UpsellDismissal(val atMs: Long, val cell: String?)

    suspend fun upsellDismissal(): UpsellDismissal {
        val at = preferencesRepository.getString(KEY_UPSELL_DISMISSED_AT)?.toLongOrNull() ?: 0L
        val cell = preferencesRepository.getString(KEY_UPSELL_DISMISSED_CELL)
        return UpsellDismissal(at, cell)
    }

    suspend fun recordUpsellDismissal(combinationId: String) {
        preferencesRepository.setString(KEY_UPSELL_DISMISSED_AT, System.currentTimeMillis().toString())
        preferencesRepository.setString(KEY_UPSELL_DISMISSED_CELL, combinationId)
    }

    companion object {
        private val KEY_FORK_DECIDED = stringPreferencesKey("mim_fork_decided")
        private val KEY_REQUESTED_CELLS = stringPreferencesKey("mim_requested_cells")
        // RW-FIXWAVE (review §2 row 19): the coverage target set,
        // persisted so a cold session after process death restores
        // the real denominator instead of deriving coverage
        // against an empty in-memory set.
        private val KEY_FILL_TARGET_CELLS = stringPreferencesKey("mim_fill_target_cells")
        private val KEY_UPSELL_DISMISSED_AT = stringPreferencesKey("mim_upsell_dismissed_at")
        private val KEY_UPSELL_DISMISSED_CELL = stringPreferencesKey("mim_upsell_dismissed_cell")
    }
}
