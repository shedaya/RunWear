import Foundation

// MARK: - Paywall price labels (RW-16.30 pricing experiment)
//
// GEN-3 (WS-F): the annual row's old "about 16% less than paying
// monthly" comparison is REMOVED — Sol's ruling replaces the
// monthly comparison with a per-run framing of the annual price.
// The fine print DERIVES its cents figure from the displayed
// product's annual price (parameterized: $9.99 control / $19.99 /
// $29.99 experiment arms), with its basis stated in the line
// itself — at four to five runs a week (234 runs a year at the
// 4.5 midpoint), $9.99/year is only about 4¢ per run. No cents
// figure is ever hardcoded, so the line stays honest at every arm.

/// Pure label composition for the paywall rows — the iOS half of the
/// cross-platform rule (Android: MakeItMeStrings.monthlyRowLabel /
/// annualRowLabel): prices come only from the fetched offering, the
/// unresolved fallback is a price-free plan name, and the annual
/// row carries NO comparison suffix (WS-F) — the per-run fine print
/// below is the annual price's only framing.
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
        return "\(annualPriceString) / year"
    }

    /// The Settings Premium offer card's price line (GEN-3, WS-D):
    /// the store product's annual price with its term, or nil while
    /// the offering is unresolved — the card then carries no price
    /// rather than a wrong one. Never a hardcoded string.
    static func settingsOfferPriceLine(annualPriceString: String?) -> String? {
        annualPriceString.map { "\($0) / year" }
    }

    /// Runs per year at the stated basis: four to five runs a week,
    /// taken at the 4.5 midpoint × 52 weeks.
    static var runsPerYearBasis: Double { 4.5 * 52 }

    /// The annual price in cents per run at the stated basis,
    /// rounded to the nearest cent — or nil when the annual price
    /// is unknown (the fine print then does not render at all).
    static func perRunCents(annual: Decimal?) -> Int? {
        guard let annual, annual > 0 else { return nil }
        let cents = (annual as NSDecimalNumber).doubleValue * 100.0 / runsPerYearBasis
        return Int(cents.rounded())
    }

    /// The paywall fine print (WS-F): the annual price framed per
    /// run, basis stated in the line. Composed only from the
    /// fetched product's price string + amount; nil when either is
    /// unresolved, so no price framing renders without a price.
    static func perRunFinePrint(annualPriceString: String?, annual: Decimal?) -> String? {
        guard let annualPriceString, let cents = perRunCents(annual: annual) else { return nil }
        return "At four to five runs a week, \(annualPriceString)/year is only about \(cents)¢ per run."
    }
}
