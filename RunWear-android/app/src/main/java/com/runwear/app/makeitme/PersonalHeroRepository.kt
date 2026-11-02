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
        val synced: Boolean = false
    )

    private val _state = MutableStateFlow(PersonalState())
    val state: StateFlow<PersonalState> = _state.asStateFlow()

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
     *  3. if the reconcile call completed, re-read the profile so the
     *     local mirror reflects any transition.
     * Silent and idempotent: no session, a failed link, or a failed
     * reconcile leaves local state exactly as it was.
     */
    suspend fun syncBillingIdentity() {
        if (!service.ensureSession()) return
        val uid = service.currentUserId() ?: return
        runCatching { billing.logIn(uid) }
        if (service.reconcileEntitlement()) refreshFromServer()
    }

    /** Re-read the profile + reference count from the server. */
    suspend fun refreshFromServer() {
        val profile = service.fetchProfile() ?: return
        val refs = service.fetchReferencePaths()
        _state.value = _state.value.copy(
            personalizationOn = profile.personalizationOn,
            freeGenerationsUsed = profile.freeGenerationsUsed,
            entitled = profile.entitled,
            gender = profile.gender,
            referenceCount = refs.size,
            synced = true
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
        val heroId: String? = null
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
        lastKnownStatus[combinationId] = status
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
        val cached = readCachedEntry(combinationId)
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
                if (cached == null || cached.version != decision.version ||
                    cached.imageUrl != decision.imageUrl ||
                    cached.heroId != decision.heroId
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
                PersonalHeroHit(decision.imageUrl, decision.version, decision.heroId)
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
            is PersonalHeroService.RequestOutcome.AlreadyQueued,
            is PersonalHeroService.RequestOutcome.AlreadyAvailable ->
                markCellRequested(combinationId)
            else -> Unit
        }
        return outcome
    }

    /** Subscriber matrix fill (server refuses non-entitled users). */
    suspend fun startPersonalFill(): Boolean = service.startPersonalFill()

    // ── Delete ─────────────────────────────────────────────────────────

    /**
     * Delete everything (acceptance 5): server erasure via
     * delete-my-photos, then local state, caches, and the session are
     * cleared. Returns true only when the SERVER confirmed the erasure —
     * the Settings confirmation copy (S14) claims only what the system
     * has confirmed (spec §3).
     */
    suspend fun deleteEverything(): Boolean {
        val outcome = service.deleteMyPhotos() ?: return false
        // Local mirrors: the profile is gone server-side; clear all
        // personal residue on this phone (spec S12: "from our server
        // and this phone").
        val raw = preferencesRepository.getString(KEY_REQUESTED_CELLS)
        raw?.split(",")?.filter { it.isNotBlank() }?.forEach { invalidateCell(it) }
        clearRequestedCells()
        lastKnownStatus.clear()
        // Cell caches for cells never requested this install can still
        // exist via serving hits; the profile wipe makes them
        // unreachable (personalization is off) and they hold only
        // expired signed URLs. The requested-set sweep above covers the
        // generated ones.
        service.clearSession()
        _state.value = PersonalState(forkDecided = _state.value.forkDecided, synced = true)
        return true
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
        private val KEY_UPSELL_DISMISSED_AT = stringPreferencesKey("mim_upsell_dismissed_at")
        private val KEY_UPSELL_DISMISSED_CELL = stringPreferencesKey("mim_upsell_dismissed_cell")
    }
}
