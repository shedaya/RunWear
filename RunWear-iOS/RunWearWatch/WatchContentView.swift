import SwiftUI

struct WatchContentView: View {
    @StateObject private var viewModel = WatchViewModel()

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                WatchLoadingView()
            case .permissionNeeded:
                WatchPermissionView {
                    viewModel.requestLocation()
                }
            case .error(let message):
                WatchErrorView(message: message) {
                    viewModel.refresh()
                }
            case .loaded(let weather, let recommendation):
                WatchOutfitView(
                    viewModel: viewModel,
                    weather: weather,
                    recommendation: recommendation
                )
            }
        }
        // GEN-5 design pass: the watch app's ground is the
        // system's warm charcoal (#16130F family via the shared
        // dark token), not the watchOS system black.
        .background(AppTheme.darkBackground.ignoresSafeArea())
        .onAppear {
            viewModel.onAppear()
        }
    }
}

struct WatchLoadingView: View {
    var body: some View {
        VStack(spacing: 8) {
            ProgressView()
                .tint(AppTheme.primaryColor)
            Text("Loading...")
                .font(.caption)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }
}

struct WatchPermissionView: View {
    let onRequest: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: "location.circle.fill")
                    .font(.system(size: 40))
                    .foregroundColor(AppTheme.primaryColor)

                Text("Location Needed")
                    .font(.headline)

                Text("RunWear needs your location for weather-based outfit recommendations.")
                    .font(.caption2)
                    .multilineTextAlignment(.center)
                    .foregroundColor(AppTheme.inkSecondary)

                Button(action: onRequest) {
                    Text("Enable")
                        .font(.caption)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.primaryColor)
            }
            .padding()
        }
    }
}

struct WatchErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 32))
                    // GEN-5 (cream map row 62): the paired
                    // dark-mode warning accent, not system orange.
                    .foregroundColor(AppTheme.weatherAccentColor(.orange, isDark: true))

                Text("Error")
                    .font(.headline)

                Text(message)
                    .font(.caption2)
                    .multilineTextAlignment(.center)
                    .foregroundColor(AppTheme.inkSecondary)

                Button(action: onRetry) {
                    Label("Retry", systemImage: "arrow.clockwise")
                        .font(.caption)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.primaryColor)
            }
            .padding()
        }
    }
}

#Preview {
    WatchContentView()
}
