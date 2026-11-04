import Foundation

// MARK: - "Make it me" availability gate (Sol's ruling, 2026-11-02)
//
// The App Store resubmission ships with "Make it me" OFF — Sol's
// ruling for both store submissions: off "til it actually works."
// The Android production candidate carries the same posture. This
// constant is the ONE gate every entry point consults:
//
//   * RunWearApp — the personal store is never bootstrapped: no
//     anonymous session is minted, billing is never configured,
//     no entitlement reconcile or profile read happens at launch.
//   * ContentView — the store is never wired into
//     WeatherViewModel, which is the view model's own documented
//     "personal layer is off (stock behavior)" state: pool heroes
//     only, no personal section, no upsell card, no personal flag
//     surface.
//   * SettingsModal — the feature's FUNCTION is unreachable:
//     the personal store is never wired, so no opt-in commit,
//     generation, or billing can run. (WS-5/D-14: the Settings
//     INVENTORY is a different matter — the Make It Me section
//     still renders its plain not-started row with the flag OFF,
//     and Delete-my-data is reachable on its own row; the flag
//     gates what the rows DO, never whether the sections exist.)
//   * Onboarding — the first-look fork is not shown; the first
//     look completes onboarding through the stock door only.
//
// The feature code and the backend are untouched. Turning the
// feature back on is flipping this one constant — deliberately a
// code change in a new build, never a remote switch, so the
// shipped binary always matches what App Review was told it
// contains. MakeItMeAvailabilityTests pins the shipped posture.
enum MakeItMeAvailability {
    static let isEnabled = true
}
