import Foundation

// MARK: - Paywall price labels (RW-16.30 pricing experiment)

/// Pure label composition for the paywall rows — the iOS half of the
/// cross-platform rule (Android: MakeItMeStrings.monthlyRowLabel /
/// annualRowLabel): prices come only from the fetched offering, the
/// unresolved fallback is a price-free plan name, and the annual
/// row's "less than paying monthly" suffix is COMPUTED from the two
/// fetched amounts — it reproduces "about 16%" at the base prices
/// ($0.99/mo, $9.99/yr) and disappears at the $19.99/$29.99
/// experiment arms, where the claim would be false.
///
/// Foundation-only and dual-membered into the RunWearTests target
/// (the project's established pattern) so MakeItMePriceLabelTests
/// can pin the math without the RevenueCat module.
enum MakeItMePriceLabels {
    static func monthlyRowLabel(priceString: String?) -> String {
        priceString.map { "\($0) / month" } ?? "Monthly"
    }

    static func annualRowLabel(annualPriceString: String?, annual: Decimal?, monthly: Decimal?) -> String {
        guard let annualPriceString else { return "Annual" }
        if let pct = annualSavingsPercent(monthly: monthly, annual: annual) {
            return "\(annualPriceString) / year — about \(pct)% less than paying monthly"
        }
        return "\(annualPriceString) / year"
    }

    /// Whole-percent saving of annual vs 12 × monthly, or nil when
    /// annual is not cheaper (or either price is unknown).
    static func annualSavingsPercent(monthly: Decimal?, annual: Decimal?) -> Int? {
        guard let monthly, let annual, monthly > 0 else { return nil }
        let monthlyYear = monthly * 12
        guard annual < monthlyYear else { return nil }
        let ratio = (annual as NSDecimalNumber).doubleValue
            / (monthlyYear as NSDecimalNumber).doubleValue
        return Int(((1.0 - ratio) * 100.0).rounded())
    }
}
