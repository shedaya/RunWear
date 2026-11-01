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
    val referenceCount: Int = 0,
    val forkDecided: Boolean = false,
    /** RevenueCat-side entitlement (drives the subscribed UI state). */
    val rcEntitled: Boolean = false,
    /** Server-side entitlement flag (webhook + reconciliation driven; gates fill/replenishment). */
    val serverEntitled: Boolean = false,
    val gender: String = "UNISEX"
)

/**
 * RW-14 "Make it me" UI logic: merges the personal backend state
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
        billing.makeItMeActive
    ) { personal, rcActive ->
        val entitled = rcActive || personal.entitled
        MakeItMeUiState(
            mode = when {
                !personal.personalizationOn -> MakeItMeMode.STOCK
                entitled -> MakeItMeMode.SUBSCRIBED
                else -> MakeItMeMode.FREE
            },
            personalizationOn = personal.personalizationOn,
            freeGenerationsUsed = personal.freeGenerationsUsed,
            referenceCount = personal.referenceCount,
            forkDecided = personal.forkDecided,
            rcEntitled = rcActive,
            serverEntitled = personal.entitled,
            gender = personal.gender
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MakeItMeUiState())

    /** True while a purchase or fill kick is in flight (sheet spinner). */
    val purchaseInFlight = MutableStateFlow(false)

    init {
        billing.configure()
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
                repository.requestGeneration(combinationId, items)
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

    // ── Subscription ───────────────────────────────────────────────────

    fun purchase(activity: Activity, packageToPurchase: Package, onResult: (BillingManager.PurchaseOutcome) -> Unit) {
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
     * server-side, flips the flag, and kicks the matrix fill itself on
     * a grant. The direct fill call stays as the second trigger (it is
     * also the correct one when the webhook landed the flag first);
     * the function refuses non-entitled users, so it is a harmless
     * no-op in every other state.
     */
    private fun onSubscriptionActivated() {
        viewModelScope.launch {
            repository.syncBillingIdentity()
            runCatching { repository.startPersonalFill() }
            repository.refreshFromServer()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            billing.refreshCustomerInfo()
            repository.refreshFromServer()
        }
    }

    /** The current RevenueCat offering ("default"), or null when unreachable. */
    suspend fun offering(): com.revenuecat.purchases.Offering? = billing.currentOffering()
}
