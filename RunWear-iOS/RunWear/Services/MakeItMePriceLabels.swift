import Foundation

// MARK: - Paywall price labels (RW-16.30 pricing experiment)
//
// GEN-3 (WS-F): the annual row's old "about 16% less than paying
// monthly" comparison is REMOVED — Sol's ruling replaces the
// monthly comparison with a per-run framing of the annual price.
// The per-run line under the price DERIVES its cents figure from
// the displayed product's annual price (parameterized: $9.99
// control / $19.99 / $29.99 experiment arms) — at four to five
// runs a week (234 runs a year at the 4.5 midpoint), $9.99/year
// is only about 4¢ per run. No cents figure is ever hardcoded, so
// the line stays honest at every arm. GEN-6 (DA-20/RW-58): the
// fine print below the card carries the basis only, in the
// settled wording — it does not restate the per-run figure.

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
/// The paywall's two plans (GEN-3, Sol's Nov 4 resolution: the
/// monthly plan IS displayed, as one quiet secondary row under
/// the annual hero card). Annual is the hero and the DEFAULT
/// selection; monthly is the secondary alternative.
enum PaywallPlan: Equatable {
    case annual
    case monthly
}

enum MakeItMePriceLabels {
    /// The selection the paywall opens with: annual, always.
    static let defaultPlan: PaywallPlan = .annual

    /// GEN-5 (RW-28 item 29): the settled monthly row form —
    /// "Monthly — $0.99/month" (the gen-4 build's "$0.99 / month"
    /// with no label deviated on both platforms).
    static func monthlyRowLabel(priceString: String?) -> String {
        priceString.map { "Monthly — \($0)/month" } ?? "Monthly"
    }

    static func annualRowLabel(annualPriceString: String?, annual: Decimal?, monthly: Decimal?) -> String {
        guard let annualPriceString else { return "Annual" }
        return "\(annualPriceString)/yr"
    }

    /// The Settings Premium offer card's price line (GEN-3, WS-D):
    /// the store product's annual price with its term, or nil while
    /// the offering is unresolved — the card then carries no price
    /// rather than a wrong one. Never a hardcoded string.
    /// GEN-5 (RW-28 item 26): the settled compact form "$9.99/yr"
    /// (the gen-4 "$9.99 / year" deviated).
    static func settingsOfferPriceLine(annualPriceString: String?) -> String? {
        annualPriceString.map { "\($0)/yr" }
    }

    /// GEN-5 (RW-28 items 26/29 + C-5): the Settings card's modest
    /// monthly element label — the same settled row form as the
    /// paywall's, so the price reads identically in both places.
    static func settingsMonthlyLabel(monthlyPriceString: String?) -> String? {
        monthlyPriceString.map { "Monthly — \($0)/month" }
    }

    /// GEN-5 (RW-28 item 29): the per-run line that sits DIRECTLY
    /// under the annual price on the paywall's hero card —
    /// "only about 4¢ per run", derived from the displayed price
    /// at the stated basis (never hardcoded, never a second price).
    static func perRunLine(annual: Decimal?) -> String? {
        guard let cents = perRunCents(annual: annual) else { return nil }
        return "only about \(cents)¢ per run"
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

    /// The paywall fine print (GEN-6, DA-20 + RW-58): the settled
    /// BASIS ONLY — "$9.99 a year, at 4–5 runs a week." The
    /// per-run claim itself is stated exactly once on the paywall,
    /// in the per-run line directly under the annual price
    /// (`perRunLine`); the gen-5 fine print restated that claim in
    /// different words below the card. Composed only from the
    /// fetched product's price string + amount; nil when either is
    /// unresolved, so no price framing renders without a price.
    static func perRunFinePrint(annualPriceString: String?, annual: Decimal?) -> String? {
        guard let annualPriceString, annual != nil else { return nil }
        return "\(annualPriceString) a year, at 4–5 runs a week."
    }
}
