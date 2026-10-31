import Foundation
import RevenueCat
import UIKit

// MARK: - RW-14 billing (RevenueCat)
//
// RunWear's first billing, in its own RevenueCat project (418e7aa5),
// deliberately separate from JetGuide's. Entitlement identifier:
// `make_it_me`. Offering: `default` (current), packages $rc_monthly
// (rw_makeitme_monthly, $0.99) and $rc_annual (rw_makeitme_annual,
// $9.99) — rw14-billing-setup-2026-10-31.md.
//
// What this class owns: configuring the SDK against the Supabase
// user's id (so the billing webhook can map entitlement events back
// to the RW-14 profile), surfacing the entitlement state, and the
// purchase/restore/manage flows. What it NEVER does: write the
// backend's `entitled` flag — that is the webhook's service-role job
// (backend §4.5). The client only READS its entitlement here and
// asks the server to fill the matrix (start-personal-fill), which
// the server itself gates on the webhook-set flag.
@MainActor
final class MakeItMeBilling: ObservableObject {
    static let entitlementId = "make_it_me"
    private static let apiKey = "appl_UEECNwJOFHXpDkMQTPYFUkAoBTY"

    @Published private(set) var isSubscribed = false
    @Published private(set) var monthlyPriceString: String?
    @Published private(set) var annualPriceString: String?

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
        let active = customerInfo.entitlements[Self.entitlementId]?.isActive == true
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
