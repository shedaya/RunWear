package com.runwear.app.makeitme

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * RW-14 billing — RunWear's first (RevenueCat project 418e7aa5).
 *
 * Products (both stores): rw_makeitme_monthly / rw_makeitme_annual,
 * entitlement [ENTITLEMENT_ID] ("make_it_me"), offering "default" with
 * $rc_monthly / $rc_annual packages (Play product ids carry the base
 * plan suffix: rw_makeitme_monthly:monthly / rw_makeitme_annual:annual —
 * the SDK's offering packages abstract that away).
 *
 * ENTITLEMENT → BACKEND GAP (documented, not faked): the personal
 * backend's `entitled` flag is writable ONLY by the service role
 * (set_personal_entitlement), and the documented wiring is a RevenueCat
 * webhook → service-role call → start-personal-fill. No webhook
 * consumer exists yet, so a purchase here activates the entitlement in
 * RevenueCat (and this manager reports it, driving the subscribed UI
 * state) while the backend flag stays false until the webhook lands:
 * matrix pre-generation and serve-triggered replenishment (both
 * server-gated on the flag) begin only then. The client still calls
 * start-personal-fill on activation — the function itself refuses
 * non-entitled users, so the call is a harmless no-op until the flag
 * flips, and the correct trigger once it does. Nothing in the client
 * ever writes `entitled`.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _makeItMeActive = MutableStateFlow(false)
    /** RevenueCat-side truth for the make_it_me entitlement. */
    val makeItMeActive: StateFlow<Boolean> = _makeItMeActive.asStateFlow()

    @Volatile
    private var configured = false

    fun configure() {
        if (configured) return
        runCatching {
            Purchases.configure(
                PurchasesConfiguration.Builder(context, PUBLIC_SDK_KEY).build()
            )
            Purchases.sharedInstance.updatedCustomerInfoListener = { info ->
                applyCustomerInfo(info)
            }
            configured = true
            refreshCustomerInfoAsync()
        }.onFailure { Log.w(TAG, "RevenueCat configure failed", it) }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        _customerInfo.value = info
        _makeItMeActive.value = info.entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    private fun refreshCustomerInfoAsync() {
        if (!configured) return
        runCatching {
            Purchases.sharedInstance.getCustomerInfoWith(
                onError = { Log.w(TAG, "customer info fetch failed: ${it.message}") },
                onSuccess = { applyCustomerInfo(it) }
            )
        }
    }

    suspend fun refreshCustomerInfo(): CustomerInfo? {
        if (!configured) return null
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.getCustomerInfoWith(
                onError = { cont.resume(null) },
                onSuccess = { info ->
                    applyCustomerInfo(info)
                    cont.resume(info)
                }
            )
        }
    }

    /** The current offering ("default"), or null when unreachable. */
    suspend fun currentOffering(): Offering? {
        if (!configured) return null
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.getOfferingsWith(
                onError = { cont.resume(null) },
                onSuccess = { offerings -> cont.resume(offerings.current) }
            )
        }
    }

    sealed interface PurchaseOutcome {
        data object Success : PurchaseOutcome
        data object Cancelled : PurchaseOutcome
        data class Failed(val message: String) : PurchaseOutcome
    }

    suspend fun purchase(activity: Activity, packageToPurchase: Package): PurchaseOutcome {
        if (!configured) return PurchaseOutcome.Failed("Billing is unavailable.")
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchaseWith(
                com.revenuecat.purchases.PurchaseParams.Builder(activity, packageToPurchase).build(),
                onError = { error, userCancelled ->
                    cont.resume(
                        if (userCancelled || error.code == PurchasesErrorCode.UserCancelledError) {
                            PurchaseOutcome.Cancelled
                        } else {
                            PurchaseOutcome.Failed(error.message)
                        }
                    )
                },
                onSuccess = { _, info ->
                    applyCustomerInfo(info)
                    cont.resume(PurchaseOutcome.Success)
                }
            )
        }
    }

    companion object {
        private const val TAG = "BillingManager"
        const val ENTITLEMENT_ID = "make_it_me"

        // RevenueCat public SDK key for RunWear (Play Store) — public by
        // design (it ships in every client), issued with RC project
        // 418e7aa5's Play app entry (package com.runwear.wear).
        const val PUBLIC_SDK_KEY = "goog_LthAStLlEEDKjajSIfKzjuyOCUj"

        /** Play's subscription-management surface for this app (spec S9). */
        const val MANAGE_SUBSCRIPTIONS_URL =
            "https://play.google.com/store/account/subscriptions?package=com.runwear.wear"
    }
}
