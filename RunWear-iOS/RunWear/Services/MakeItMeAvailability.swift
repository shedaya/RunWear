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
//   * SettingsModal — the "Make it me" section is not rendered,
//     so its sheets (opt-in, my photos, subscription) and alerts
//     have no presenter and cannot be reached.
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
