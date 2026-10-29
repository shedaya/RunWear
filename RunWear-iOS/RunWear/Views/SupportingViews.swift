import SwiftUI

struct LoadingView: View {
    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(AppTheme.primaryColor)

            Text("Getting weather data...")
                .font(.headline)
                .foregroundColor(AppTheme.textSecondary)
        }
    }
}

struct LocationPermissionView: View {
    let onRequestPermission: () -> Void
    /// Manual-location escape hatch: Android reaches its location
    /// picker without GPS permission; the iOS gate previously offered
    /// no path but granting permission.
    var onSetLocationManually: () -> Void = {}

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 80))
                .foregroundColor(AppTheme.primaryColor)

            // Headings on the dark gate background use textOnDark:
            // textPrimary (#212121) on darkBackground (#0A0A0A) is
            // ~1.3:1 — effectively invisible.
            Text("Location Access Needed")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.textOnDark)

            Text("RunWear needs your location to provide accurate weather-based outfit recommendations for your area.")
                .font(.body)
                .foregroundColor(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: onRequestPermission) {
                Text("Enable Location")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.primaryColor)
                    .cornerRadius(12)
            }
            .padding(.horizontal, 32)

            Button(action: onSetLocationManually) {
                Text("Set Location Manually")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.4), lineWidth: 1)
                    )
            }
            .padding(.horizontal, 32)
        }
        .padding()
    }
}

struct LocationDeniedView: View {
    /// Manual-location escape hatch (see LocationPermissionView).
    var onSetLocationManually: () -> Void = {}

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "location.slash.fill")
                .font(.system(size: 80))
                .foregroundColor(AppTheme.textSecondary)

            Text("Location Access Denied")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.textOnDark)

            Text("RunWear needs location access to show weather-based outfit recommendations. Please enable location access in Settings, or set your location manually.")
                .font(.body)
                .foregroundColor(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: openSettings) {
                Text("Open Settings")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.primaryColor)
                    .cornerRadius(12)
            }
            .padding(.horizontal, 32)

            Button(action: onSetLocationManually) {
                Text("Set Location Manually")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.4), lineWidth: 1)
                    )
            }
            .padding(.horizontal, 32)
        }
        .padding()
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

struct ErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 60))
                .foregroundColor(.orange)

            Text("Something went wrong")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.textOnDark)

            Text(message)
                .font(.body)
                .foregroundColor(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: onRetry) {
                HStack {
                    Image(systemName: "arrow.clockwise")
                    Text("Try Again")
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(AppTheme.primaryColor)
                .cornerRadius(12)
            }
            .padding(.horizontal, 32)
        }
        .padding()
    }
}

#Preview("Loading") {
    LoadingView()
}

#Preview("Permission") {
    LocationPermissionView {}
}

#Preview("Denied") {
    LocationDeniedView()
}

#Preview("Error") {
    ErrorView(message: "Could not fetch weather data") {}
}
