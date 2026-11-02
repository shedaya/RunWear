import SwiftUI

@main
struct RunWearApp: App {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    /// RW-25: appearance preference (System / Light / Dark), shared
    /// with the Settings picker via the same AppStorage key.
    @AppStorage("appearancePreference") private var appearancePreference: AppearancePreference = .system
    /// RW-14 "Make it me": the personalization store lives at the app
    /// root so onboarding, the main screen and Settings share one
    /// state machine.
    @StateObject private var personalHeroStore = PersonalHeroStore()

    var body: some Scene {
        WindowGroup {
            Group {
                if hasCompletedOnboarding {
                    ContentView()
                } else {
                    OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                }
            }
            .environmentObject(personalHeroStore)
            .task {
                // "Make it me" OFF (see MakeItMeAvailability): the
                // store is never bootstrapped, so it stays in its
                // initial stock mode for the whole session — no
                // session, no billing, no personal reads at launch.
                if MakeItMeAvailability.isEnabled {
                    await personalHeroStore.bootstrap()
                }
            }
            // RW-25: the appearance pair — the preference resolves
            // here (system -> nil, no override; light/dark -> the
            // explicit scheme). The RW-6.11 forced-dark override is
            // removed: system chrome follows the resolved scheme.
            .preferredColorScheme(appearancePreference.colorScheme)
        }
    }
}
