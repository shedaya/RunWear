import SwiftUI

/// RW-26 (frame 46): loading is the home screen's own skeleton —
/// the two-zone shape (photo block, sheet with header lines and
/// outfit rows) in the theme surfaces, never a spinner over blank.
struct LoadingView: View {
    var body: some View {
        ZStack {
            skeleton
            // GEN-6 (RW-60): the settled line (frame 46) rides the
            // HOME loading surface — it existed only in onboarding.
            // It sits OUTSIDE the redacted skeleton so it reads as
            // text, not as another placeholder bar.
            VStack {
                Spacer()
                Text("Getting your first recommendation…")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(AppTheme.inkSecondary)
                    .padding(.bottom, 40)
            }
        }
    }

    private var skeleton: some View {
        VStack(spacing: 0) {
            // Photo zone.
            RoundedRectangle(cornerRadius: 0)
                .fill(AppTheme.cardOnSheet)
                .frame(height: 300)
            // Sheet zone.
            VStack(alignment: .leading, spacing: 14) {
                skeletonLine(width: 90, height: 12)
                skeletonLine(width: 170, height: 18)
                skeletonLine(width: 120, height: 44)
                skeletonLine(width: 70, height: 14)
                HStack(spacing: 8) {
                    ForEach(0..<4, id: \.self) { _ in
                        Capsule().fill(AppTheme.cardOnSheet).frame(width: 74, height: 34)
                    }
                }
                skeletonLine(width: .infinity, height: 64)
                skeletonLine(width: .infinity, height: 64)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppTheme.sheet)
            Spacer()
        }
        .background(AppTheme.background)
        .redacted(reason: .placeholder)
        .accessibilityLabel("Loading your weather")
    }

    private func skeletonLine(width: CGFloat, height: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(AppTheme.cardOnSheet)
            .frame(width: width == .infinity ? nil : width, height: height)
            .frame(maxWidth: width == .infinity ? .infinity : nil, alignment: .leading)
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
            // WS-8: the gate icon takes the dynamic pine pair
            // (pinePayload) — seed pine alone is ≈2.19:1 on the
            // dark ground, below the 3:1 non-text floor; the pair
            // clears it in both modes. (The denied gate's icon
            // already renders in a token, inkMuted.)
            Image(systemName: "location.circle.fill")
                .font(.system(size: 80))
                .foregroundColor(AppTheme.pinePayload)

            // Headings on the dark gate background use textOnDark:
            // textPrimary (#212121) on darkBackground (#0A0A0A) is
            // ~1.3:1 — effectively invisible.
            Text("Location Access Needed")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.inkPrimary)

            Text("RunWear needs your location to provide accurate weather-based outfit recommendations for your area.")
                .font(.body)
                .foregroundColor(AppTheme.inkSecondary)
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
                // Token pair, not white: this button sits on the
                // dynamic home ground with no fill, and white on
                // the light cream ground is 1.13:1 — illegible.
                // inkPrimary clears 4.5:1 in both modes (pinned in
                // RW26Phase2Tests); cardStroke is the app's standard
                // card outline in both modes.
                Text("Set Location Manually")
                    .font(.headline)
                    .foregroundColor(AppTheme.inkPrimary)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(AppTheme.cardStroke, lineWidth: 1)
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
                .foregroundColor(AppTheme.inkMuted)

            Text("Location Access Denied")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.inkPrimary)

            Text("RunWear needs location access to show weather-based outfit recommendations. Please enable location access in Settings, or set your location manually.")
                .font(.body)
                .foregroundColor(AppTheme.inkSecondary)
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
                // Same token pair as LocationPermissionView's
                // button (see the note there): white on the light
                // cream ground is 1.13:1 with no fill behind it.
                Text("Set Location Manually")
                    .font(.headline)
                    .foregroundColor(AppTheme.inkPrimary)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(AppTheme.cardStroke, lineWidth: 1)
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
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 24) {
            // WS-8: the warning icon takes the theme's orange
            // accent pair — system .orange is ≈1.95:1 on the light
            // ground (below even the 3:1 non-text floor); the pair
            // is the same one the Make It Me flows' warningColor
            // already uses, pinned ≥4.5:1 in both modes.
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 60))
                .foregroundColor(AppTheme.weatherAccentColor(.orange, isDark: colorScheme == .dark))

            // RW-26 (frame 47; §8 Q3): the honest headline — the
            // "Oops!"-era tone retires. The message below names
            // what is safe / what happened.
            Text("Couldn't load your weather")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.inkPrimary)

            Text(message)
                .font(.body)
                .foregroundColor(AppTheme.inkSecondary)
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
