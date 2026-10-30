import SwiftUI

@main
struct RunWearApp: App {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false

    var body: some Scene {
        WindowGroup {
            Group {
                if hasCompletedOnboarding {
                    ContentView()
                } else {
                    OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                }
            }
            // RW-6.11: the app is dark by design — declare it, so system
            // chrome (sheets, alerts, keyboards, Form surfaces) renders
            // dark everywhere instead of per-view overrides.
            .preferredColorScheme(.dark)
        }
    }
}
