import Foundation

// MARK: - WS-5 (D-14) — the Settings inventory of record
//
// The design's one inventory (frames 35–39; notes §8H: Appearance
// first, the three preferences, Make It Me, Support, About) is
// declared ONCE, here, in the order the iOS Form renders it.
// SettingsModal composes exactly this list — one Form section per
// entry, in registry order — and `sectionsFor` is the invariant
// the gate tests:
//
// **Every registered section renders in EVERY shipped
// configuration.** A section composed conditionally may not emit
// nothing: not when the Make It Me feature flag is OFF (its
// section still carries the plain not-started row, frame 36), and
// not in any of the feature's three coded modes. The
// Delete-my-data row is its own registered section — reachable on
// its own, never through the Make It Me section's emission (that
// coupling is what made it unreachable in the D-14 build, where
// the whole section was gated on the availability flag and the
// delete row lived inside it).
//
// Content parity with the Android registry (SettingsInventory.kt):
// the same nine section families — Appearance, Temperature Unit,
// Fit Preference, Body Temperature, Help Improve, Make It Me,
// Delete My Data, Support (Android's Send Feedback section; iOS's
// Support section also carries the Rate door), About. Order is
// each platform's own Form/sheet order within the §8H grouping
// (Appearance → preferences → Make It Me → Delete → Support →
// About); iOS has always carried Help Improve with the preference
// block, ahead of Make It Me.

/// The feature's three coded modes, mirrored for this file so the
/// inventory layer stays free of the store (SettingsModal maps
/// `PersonalHeroStore.Mode` onto this at the composition site).
enum SettingsMakeItMeMode: String, CaseIterable {
    case stock
    case free
    case subscribed
}

enum SettingsSectionID: String, CaseIterable, Identifiable {
    case appearance
    case temperatureUnit
    case fitPreference
    case bodyTemperature
    case helpImprove
    case makeItMe
    case deleteMyData
    case support
    case about

    var id: String { rawValue }
}

enum SettingsInventory {

    /// The full designed inventory, in designed (rendered) order.
    static let sections: [SettingsSectionID] = [
        .appearance,
        .temperatureUnit,
        .fitPreference,
        .bodyTemperature,
        .helpImprove,
        .makeItMe,
        .deleteMyData,
        .support,
        .about,
    ]

    /// The sections a Settings surface renders for one
    /// configuration. The parameters exist so the invariant is
    /// stated — and tested — over the whole configuration space;
    /// the answer is the full registry in every case. Any future
    /// configuration-dependent section must change this function
    /// AND its gate test together.
    static func sectionsFor(
        makeItMeEnabled: Bool,
        makeItMeMode: SettingsMakeItMeMode
    ) -> [SettingsSectionID] {
        sections
    }
}

// MARK: - The Make It Me section's rows (frames 35–37)

/// One tappable/destructive row of the Make It Me settings
/// section. The section view renders its rows under exactly these
/// titles, so "the section emits something in every
/// configuration" is a tested fact about the same strings the
/// user sees, not a parallel paraphrase.
enum MakeItMeSettingsRow: String, CaseIterable {
    /// Frame 36's plain not-started row.
    case notStarted
    /// Frame 38's door: Your photos (view or replace).
    case yourPhotos
    /// Frame 35's quiet door in the free state.
    case premium
    /// Frame 37's management door in the subscribed state.
    case manageSubscription
    /// The photos-only delete (frame 39's sibling row). The
    /// account-level Delete-my-data is NOT this row — it is the
    /// inventory's own section (see SettingsSectionID.deleteMyData).
    case deletePhotos

    var title: String {
        switch self {
        case .notStarted: return "See yourself in every outfit"
        case .yourPhotos: return "Your photos"
        case .premium: return "Premium"
        case .manageSubscription: return "Manage subscription"
        case .deletePhotos: return "Delete my photos"
        }
    }
}

enum MakeItMeSettingsPresentation {

    /// The rows the Make It Me section presents in one
    /// configuration, in order. With the flag OFF the section
    /// still presents exactly the plain not-started row (frame 36)
    /// in every mode — what the flag keeps shut is the feature's
    /// function (the opt-in flow behind the row), not the
    /// section's existence.
    static func rows(
        makeItMeEnabled: Bool,
        mode: SettingsMakeItMeMode
    ) -> [MakeItMeSettingsRow] {
        guard makeItMeEnabled else { return [.notStarted] }
        switch mode {
        case .stock:
            return [.notStarted]
        case .free:
            return [.yourPhotos, .premium, .deletePhotos]
        case .subscribed:
            return [.yourPhotos, .manageSubscription, .deletePhotos]
        }
    }
}
