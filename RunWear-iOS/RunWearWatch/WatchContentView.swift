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
                .foregroundColor(.secondary)
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
                    .foregroundColor(.secondary)

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
                    .foregroundColor(.orange)

                Text("Error")
                    .font(.headline)

                Text(message)
                    .font(.caption2)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)

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
