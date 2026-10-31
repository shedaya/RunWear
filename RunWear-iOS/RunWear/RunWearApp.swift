import SwiftUI

@main
struct RunWearApp: App {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
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
                await personalHeroStore.bootstrap()
            }
            // RW-6.11: the app is dark by design — declare it, so system
            // chrome (sheets, alerts, keyboards, Form surfaces) renders
            // dark everywhere instead of per-view overrides.
            .preferredColorScheme(.dark)
        }
    }
}
