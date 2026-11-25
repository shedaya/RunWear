import Foundation
import RevenueCat
import UIKit

// MARK: - RW-14 billing (RevenueCat)
//
// RunWear's first billing, in its own RevenueCat project (418e7aa5),
// deliberately separate from JetGuide's. Entitlement identifier:
// `make_it_me`. Offering: `default` (current), packages $rc_monthly
// (rw_makeitme_monthly) and $rc_annual (rw_makeitme_annual)
// — rw14-billing-setup-2026-10-31.md.
//
// Prices are NEVER hardcoded in this file or in the views
// (RW-16.30 pricing experiment, Nov 2 2026): every price renders
// from the fetched current offering, so a user assigned a variant
// offering ($19.99/$29.99 annual experiment arms) sees that
// offering's prices and never the control price.
//
// What this class owns: configuring the SDK against the Supabase
// user's id (so the billing webhook can map entitlement events back
// to the RW-14 profile), surfacing the entitlement state, and the
// purchase/restore/manage flows. What it NEVER does: write the
// backend's `entitled` flag — that is a service-role job with two
// server paths (backend §4.5): the revenuecat-webhook consumer, and
// the reconcile-personal-entitlement backstop the store invokes at
// bootstrap and on every entitlement change observed here (RW-16.12
// Lane B / RW-16.14) — the server re-verifies against RevenueCat
// itself, so this class only READS its entitlement and asks. The
// store also asks the server to fill the matrix
// (start-personal-fill), which the server itself gates on its flag.
@MainActor
final class MakeItMeBilling: ObservableObject {
    static let entitlementId = "make_it_me"
    private static let apiKey = "appl_UEECNwJOFHXpDkMQTPYFUkAoBTY"

    @Published private(set) var isSubscribed = false
    /// GEN-5 (RW-29 FA-4): the active entitlement is in its
    /// trial period (RevenueCat periodType) — read by the
    /// convergence band's gate.
    @Published private(set) var isInTrial = false
    @Published private(set) var monthlyPriceString: String?
    @Published private(set) var annualPriceString: String?
    @Published private(set) var monthlyPriceAmount: Decimal?
    @Published private(set) var annualPriceAmount: Decimal?

    /// Paywall row labels, composed from the fetched offering only.
    var monthlyRowLabel: String {
        MakeItMePriceLabels.monthlyRowLabel(priceString: monthlyPriceString)
    }
    var annualRowLabel: String {
        MakeItMePriceLabels.annualRowLabel(
            annualPriceString: annualPriceString,
            annual: annualPriceAmount,
            monthly: monthlyPriceAmount
        )
    }

    /// GEN-3 (WS-F): the paywall's per-run fine print, derived from
    /// the fetched offering's annual price — nil until it resolves.
    var perRunFinePrint: String? {
        MakeItMePriceLabels.perRunFinePrint(
            annualPriceString: annualPriceString,
            annual: annualPriceAmount
        )
    }

    /// Set by PersonalHeroStore: fires on every entitlement change so
    /// the store can kick the server-side matrix fill.
    var onEntitlementChange: ((Bool) -> Void)?

    private var configured = false
    private var monthlyPackage: Package?
    private var annualPackage: Package?
    private var streamTask: Task<Void, Never>?

    /// Configure once, keyed to the Supabase user id. Until the app
    /// has a Supabase session there is no stable identity to bill
    /// against, so configuration waits for bootstrap to produce one.
    func configure(appUserID: String) {
        guard !configured else { return }
        configured = true
        Purchases.configure(withAPIKey: Self.apiKey, appUserID: appUserID)
        streamTask = Task { [weak self] in
            for await info in Purchases.shared.customerInfoStream {
                guard let self else { return }
                self.apply(customerInfo: info)
            }
        }
        Task { [weak self] in
            await self?.refresh()
        }
    }

    func refresh() async {
        guard configured else { return }
        if let info = try? await Purchases.shared.customerInfo() {
            apply(customerInfo: info)
        }
        await loadOfferings()
    }

    private func apply(customerInfo: CustomerInfo) {
        let entitlement = customerInfo.entitlements[Self.entitlementId]
        let active = entitlement?.isActive == true
        // GEN-5 (RW-29 FA-4): the trial distinction IS available
        // client-side — RevenueCat reports the entitlement's
        // period type. Android suppresses the convergence band
        // for trial users (no bank is built during a trial);
        // iOS now reads the same signal for the same gate.
        isInTrial = active && entitlement?.periodType == .trial
        if active != isSubscribed {
            isSubscribed = active
            onEntitlementChange?(active)
        }
    }

    private func loadOfferings() async {
        guard let offerings = try? await Purchases.shared.offerings(),
              let current = offerings.current else { return }
        monthlyPackage = current.monthly
        annualPackage = current.annual
        monthlyPriceString = current.monthly?.storeProduct.localizedPriceString
        annualPriceString = current.annual?.storeProduct.localizedPriceString
        monthlyPriceAmount = current.monthly?.storeProduct.price
        annualPriceAmount = current.annual?.storeProduct.price
    }

    enum PurchaseOutcome {
        case subscribed
        case cancelled
        case unavailable // no package (products not yet resolvable in the store)
    }

    func purchaseMonthly() async -> PurchaseOutcome {
        await purchase(package: monthlyPackage)
    }

    func purchaseAnnual() async -> PurchaseOutcome {
        await purchase(package: annualPackage)
    }

    private func purchase(package: Package?) async -> PurchaseOutcome {
        // GEN-6 Wave A (DA-10 class): recognise an EXISTING
        // entitlement before initiating any purchase. The sheet
        // gates its purchase button on `isSubscribed` at render
        // time, but a stale model (entitlement active, refresh
        // not yet landed) would otherwise send an already-
        // subscribed user into a store purchase that can only
        // dead-end in a store error. Re-read the entitlement
        // first; a live one routes to the subscribed state.
        await refresh()
        guard !isSubscribed else { return .subscribed }
        guard configured, let package else {
            await loadOfferings()
            return .unavailable
        }
        do {
            let result = try await Purchases.shared.purchase(package: package)
            if result.userCancelled { return .cancelled }
            apply(customerInfo: result.customerInfo)
            return isSubscribed ? .subscribed : .cancelled
        } catch {
            return .unavailable
        }
    }

    func restore() async {
        guard configured else { return }
        if let info = try? await Purchases.shared.restorePurchases() {
            apply(customerInfo: info)
        }
    }

    /// The store's own subscription management (spec §3, S9) — never
    /// a home-grown billing screen.
    func showManageSubscriptions() async {
        if configured, (try? await Purchases.shared.showManageSubscriptions()) != nil {
            return
        }
        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
            await UIApplication.shared.open(url)
        }
    }
}

// Paywall price labels live in MakeItMePriceLabels.swift
// (dual-membered into the test target, like MakeItMeAvailability).
