package com.runwear.app.makeitme

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three Settings/fork modes from spec §3. */
enum class MakeItMeMode { STOCK, FREE, SUBSCRIBED }

data class MakeItMeUiState(
    val mode: MakeItMeMode = MakeItMeMode.STOCK,
    val personalizationOn: Boolean = false,
    val freeGenerationsUsed: Int = 0,
    /** Gen-3 X-9: any look currently generating (the meter seam's in-flight input). */
    val anyLookMaking: Boolean = false,
    /** GEN-5 (RW-29 FA-10): the real in-flight count behind [anyLookMaking]. */
    val looksMakingCount: Int = 0,
    val referenceCount: Int = 0,
    val forkDecided: Boolean = false,
    /** RevenueCat-side entitlement (drives the subscribed UI state). */
    val rcEntitled: Boolean = false,
    /** Server-side entitlement flag (webhook + reconciliation driven; gates fill/replenishment). */
    val serverEntitled: Boolean = false,
    val gender: String = "UNISEX",
    /** GEN-3 (convergence band): real bank coverage (ready/total), or null. */
    val bankCoverage: PersonalHeroService.BankCoverage? = null,
    /** GEN-3 (band HOLD): the entitlement is in its free-trial period. */
    val inTrial: Boolean = false
)

/**
 * RW-14 "Make It Me" UI logic: merges the personal backend state
 * ([PersonalHeroRepository]) with the RevenueCat entitlement
 * ([BillingManager]) into the spec's three modes, and runs the flow
 * actions (consent, upload, first look, replace, delete, purchase).
 */
@HiltViewModel
class MakeItMeViewModel @Inject constructor(
    private val repository: PersonalHeroRepository,
    private val service: PersonalHeroService,
    private val billing: BillingManager
) : ViewModel() {

    val uiState: StateFlow<MakeItMeUiState> = combine(
        repository.state,
        billing.makeItMeActive,
        billing.makeItMeInTrial
    ) { personal, rcActive, inTrial ->
        val entitled = rcActive || personal.entitled
        MakeItMeUiState(
            mode = when {
                !personal.personalizationOn -> MakeItMeMode.STOCK
                entitled -> MakeItMeMode.SUBSCRIBED
                else -> MakeItMeMode.FREE
            },
            personalizationOn = personal.personalizationOn,
            freeGenerationsUsed = personal.freeGenerationsUsed,
            anyLookMaking = personal.anyLookMaking,
            looksMakingCount = personal.looksMakingCount,
            referenceCount = personal.referenceCount,
            forkDecided = personal.forkDecided,
            rcEntitled = rcActive,
            serverEntitled = personal.entitled,
            gender = personal.gender,
            bankCoverage = personal.bankCoverage,
            inTrial = inTrial
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MakeItMeUiState())

    /** True while a purchase or fill kick is in flight (sheet spinner). */
    val purchaseInFlight = MutableStateFlow(false)

    init {
        billing.configure()
        // GEN-5 (EX-15): the band watch runs for the ViewModel's
        // life; it only spends a refresh while the band shows.
        startBandWatch()
        viewModelScope.launch {
            repository.initialize()
            billing.refreshCustomerInfo()
            repository.refreshFromServer()
        }
        // Entitlement changes (purchase in another flow, renewal,
        // lapse detected on refresh) re-run the identity + reconcile
        // sync, so the server flag follows RevenueCat even when the
        // change didn't happen on this screen. Bootstrap is covered by
        // initialize(); this collector only fires on actual changes.
        viewModelScope.launch {
            var previous = billing.makeItMeActive.value
            billing.makeItMeActive.collect { active ->
                if (active != previous) {
                    previous = active
                    repository.syncBillingIdentity()
                }
            }
        }
    }

    // ── Fork ───────────────────────────────────────────────────────────

    fun decideForkAsStock() {
        viewModelScope.launch { repository.markForkDecided() }
    }

    // ── Upload flow ────────────────────────────────────────────────────

    /** Step 1–2 of the sub-flow: consent + attestation recorded server-side. */
    fun recordConsent(gender: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.recordConsent(gender)) }
    }

    /**
     * Steps 3–4: upload + register the accepted photos, then — for the
     * onboarding first look — request generation of today's cell.
     * In replace mode, today's cell is invalidated and re-requested so
     * the look remakes from the new photos (spec §3/S7); replacement
     * requests clear the requested-cell set because new references make
     * previously gate-failed cells worth one new attempt.
     *
     * RW-16.16: the request's outcome is no longer discarded. An
     * accepted request is noted into the repository's look-status
     * path, and the home overlay re-reads the server's look state
     * when the flow finishes — so returning home shows the cell
     * MAKING (and, later, its delivery or its durable FAILED line)
     * instead of the old fire-and-forget silence.
     *
     * @param todaysCell the current recommendation's personal
     *   combination id + outfit item keys, or null to skip generation.
     */
    fun submitPhotos(
        photos: List<ByteArray>,
        replace: Boolean,
        todaysCell: Pair<String, List<String>>?,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val uploaded = repository.uploadAndRegisterReferences(photos)
            if (!uploaded) {
                onResult(false)
                return@launch
            }
            repository.markForkDecided()
            if (replace) {
                repository.clearRequestedCells()
            }
            if (todaysCell != null) {
                val (combinationId, items) = todaysCell
                if (replace) repository.invalidateCell(combinationId)
                when (repository.requestGeneration(combinationId, items)) {
                    is PersonalHeroService.RequestOutcome.Queued,
                    is PersonalHeroService.RequestOutcome.AlreadyQueued ->
                        repository.noteLookStatus(combinationId, PersonalLookStatus.Making)
                    is PersonalHeroService.RequestOutcome.AlreadyAvailable ->
                        repository.noteLookStatus(combinationId, PersonalLookStatus.Ready)
                    else -> Unit // refused: the overlay's server read tells the truth
                }
            }
            onResult(true)
        }
    }

    /** Signed display URLs for the registered reference photos (Settings "Your photos"). */
    fun loadReferenceUrls(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val paths = service.fetchReferencePaths()
            val urls = paths.mapNotNull { service.signedUrlFor(it) }
            onResult(urls)
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────

    /** "Delete my photos" — completes only on server confirmation. */
    fun deletePhotos(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.deleteEverything()) }
    }

    /** "Delete my data" — completes only when the server's receipt
     * confirms the account itself is deleted. */
    fun deleteData(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.deleteAllData()) }
    }

    // ── Subscription ───────────────────────────────────────────────────

    fun purchase(activity: Activity, packageToPurchase: Package, onResult: (BillingManager.PurchaseOutcome) -> Unit) {
        // Make It Me OFF in this build (MakeItMeAvailability): no
        // purchase can start — RevenueCat's purchase call is never
        // reached, whatever surface asks.
        if (!MakeItMeAvailability.purchaseAllowed()) {
            onResult(BillingManager.PurchaseOutcome.Failed("Make It Me is unavailable in this build."))
            return
        }
        purchaseInFlight.value = true
        viewModelScope.launch {
            val outcome = billing.purchase(activity, packageToPurchase)
            purchaseInFlight.value = false
            if (outcome == BillingManager.PurchaseOutcome.Success) {
                onSubscriptionActivated()
            }
            onResult(outcome)
        }
    }

    /**
     * After a successful purchase: sync identity + entitlement first —
     * the reconcile backstop verifies the purchase against RevenueCat
     * server-side and flips the flag. GEN-5 (RW-28 Addendum 8,
     * Defect 1): there is deliberately NO direct fill call here any
     * longer — under the deployed convergence contract a fill call
     * without forecast cells enqueues nothing (that empty call was
     * the defect), and this view model holds no forecast. The fill
     * trigger of record is the main view model's forecast path: it
     * names the 7-day forecast cells on the entitlement transition
     * this sync produces and on every forecast refresh while
     * entitled.
     */
    private fun onSubscriptionActivated() {
        viewModelScope.launch {
            repository.syncBillingIdentity()
            repository.refreshFromServer()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            billing.refreshCustomerInfo()
            repository.refreshFromServer()
        }
    }

    /**
     * GEN-5 (RW-29 EX-15): while the convergence band is showing
     * (subscribed, bank not yet fully ready), coverage is
     * re-read on a slow loop so a bank that completes mid-session
     * retires the band without the user leaving and returning.
     * The gen-4 read rode only unrelated profile refreshes, so a
     * completed bank could leave the band up for the whole
     * session. The loop idles cheaply when no band is showing.
     */
    fun startBandWatch() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val state = uiState.value
                val coverage = state.bankCoverage
                val bandShowing = state.mode == MakeItMeMode.SUBSCRIBED &&
                    (coverage == null || coverage.ready < coverage.total)
                if (bandShowing) refresh()
            }
        }
    }

    /** The current RevenueCat offering ("default"), or null when unreachable. */
    suspend fun offering(): com.revenuecat.purchases.Offering? = billing.currentOffering()

    /**
     * GEN-5 (RW-29 FA-7): the paywall's Restore door. A restore
     * that lands the entitlement activates the subscription state
     * exactly like a purchase does.
     */
    fun restorePurchases(onResult: (BillingManager.PurchaseOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = billing.restorePurchases()
            if (outcome == BillingManager.PurchaseOutcome.Success) {
                onSubscriptionActivated()
            }
            onResult(outcome)
        }
    }
}
