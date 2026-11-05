package com.runwear.app.makeitme

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.purchaseWith
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
 * ENTITLEMENT → BACKEND WIRING: the personal backend's `entitled` flag
 * is writable ONLY by the service role (set_personal_entitlement). Two
 * server paths flip it, and the client never writes it directly:
 *  1. the revenuecat-webhook consumer (primary), and
 *  2. the reconcile-personal-entitlement backstop, which the client
 *     invokes on bootstrap, on entitlement change, and after a
 *     purchase (see PersonalHeroRepository.syncBillingIdentity) — the
 *     server re-verifies the caller's state against RevenueCat and
 *     grants + kicks start-personal-fill itself when warranted.
 * Both paths key the user by the SUPABASE uid, which is why the
 * identity link (acceptance 17, [logIn]) matters: until RevenueCat
 * runs under the Supabase uid, an Android purchase sits under the
 * SDK's anonymous id and neither path can find it. This manager
 * reports the RevenueCat-side entitlement for the subscribed UI state;
 * server-gated benefits (matrix pre-generation, replenishment) follow
 * the backend flag once either path lands it.
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

    private val _makeItMeInTrial = MutableStateFlow(false)
    /**
     * GEN-3 (convergence band, Sol Nov 4): true while the
     * entitlement's current period is a free TRIAL (RevenueCat
     * periodType). Banking is Premium-only behaviour — a trial
     * user gets on-demand looks, no bank — so surfaces that
     * represent the bank must be able to tell the two apart.
     */
    val makeItMeInTrial: StateFlow<Boolean> = _makeItMeInTrial.asStateFlow()

    @Volatile
    private var configured = false

    fun configure() {
        if (configured) return
        runCatching {
            Purchases.configure(
                PurchasesConfiguration.Builder(context, PUBLIC_SDK_KEY).build()
            )
            Purchases.sharedInstance.updatedCustomerInfoListener =
                object : UpdatedCustomerInfoListener {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        applyCustomerInfo(customerInfo)
                    }
                }
            configured = true
            refreshCustomerInfoAsync()
        }.onFailure { Log.w(TAG, "RevenueCat configure failed", it) }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        _customerInfo.value = info
        _makeItMeActive.value = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        _makeItMeInTrial.value =
            info.entitlements[ENTITLEMENT_ID]?.periodType ==
                com.revenuecat.purchases.PeriodType.TRIAL
    }

    /** The app_user_id RevenueCat currently runs under, if configured. */
    fun currentAppUserId(): String? {
        if (!configured) return null
        return runCatching { Purchases.sharedInstance.appUserID }.getOrNull()
    }

    /**
     * Acceptance 17: link RevenueCat to the Supabase user id, so this
     * device's purchases are recorded under the uid the backend keys
     * on. Idempotent per [BillingIdentityPolicy]: a no-op (returns
     * true) when the SDK already runs under [appUserId]. Failures are
     * logged and reported as false — callers treat them as "retry at
     * the next sync point", never as a user-facing error.
     */
    suspend fun logIn(appUserId: String): Boolean {
        if (!configured) return false
        if (!BillingIdentityPolicy.shouldLogIn(currentAppUserId(), appUserId)) return true
        return suspendCancellableCoroutine { cont ->
            runCatching {
                Purchases.sharedInstance.logInWith(
                    appUserId,
                    onError = { error ->
                        Log.w(TAG, "RevenueCat logIn failed: ${error.message}")
                        if (cont.isActive) cont.resume(false)
                    },
                    onSuccess = { info, _ ->
                        applyCustomerInfo(info)
                        if (cont.isActive) cont.resume(true)
                    }
                )
            }.onFailure {
                Log.w(TAG, "RevenueCat logIn threw", it)
                if (cont.isActive) cont.resume(false)
            }
        }
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

        /**
         * GEN-6 WAVE A (DA-10): the entitlement was ALREADY active
         * when purchase was tapped — the flow was never launched.
         * The caller routes the user to the subscribed state
         * instead of dead-ending in the store's "You're already
         * subscribed…" error (the owner's recording, 2:51–2:53).
         */
        data object AlreadySubscribed : PurchaseOutcome
    }

    /**
     * GEN-5 (RW-29 FA-7): restore purchases — the gen-4 build had
     * NO restore path anywhere, so a subscriber who reinstalled
     * or switched devices met a paywall with no way back to the
     * subscription they already own. Restores through RevenueCat
     * and reports whether the entitlement came back active.
     */
    suspend fun restorePurchases(): PurchaseOutcome {
        if (!configured) return PurchaseOutcome.Failed("Billing is unavailable.")
        // SDK 9.x: the coroutine form is Purchases.awaitRestore()
        // (there is no restorePurchasesWith in this version).
        val info = runCatching {
            Purchases.sharedInstance.awaitRestore()
        }.getOrElse { error ->
            return PurchaseOutcome.Failed(error.message ?: "Restore failed.")
        }
        applyCustomerInfo(info)
        return if (info.entitlements[ENTITLEMENT_ID]?.isActive == true) {
            PurchaseOutcome.Success
        } else {
            PurchaseOutcome.Failed(
                "No active subscription found for this account."
            )
        }
    }

    suspend fun purchase(activity: Activity, packageToPurchase: Package): PurchaseOutcome {
        if (!configured) return PurchaseOutcome.Failed("Billing is unavailable.")
        // GEN-6 WAVE A (DA-10): recognise the entitlement BEFORE
        // launching the purchase flow. A fresh customer-info read
        // (not just the cached flow — the recording's user had a
        // live subscription the app had not yet surfaced) decides;
        // already-entitled users never reach the store sheet that
        // errors out on them.
        val fresh = runCatching { refreshCustomerInfo() }.getOrNull()
        val alreadyActive = makeItMeActive.value ||
            fresh?.entitlements?.get(ENTITLEMENT_ID)?.isActive == true
        if (alreadyActive) return PurchaseOutcome.AlreadySubscribed
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchaseWith(
                com.revenuecat.purchases.PurchaseParams.Builder(activity, packageToPurchase).build(),
                onError = { error, userCancelled ->
                    cont.resume(
                        if (userCancelled) {
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
