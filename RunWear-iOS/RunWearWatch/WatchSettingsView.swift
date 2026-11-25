import SwiftUI

/// Watch settings (frame 53's direction: the phone's three
/// preference rows, in the app's tokens).
///
/// GEN-5 (RW-29 EX-5 + cream map row 61): build 25 abbreviated
/// every label ("M" / "F" / "Body Temp" / "°F" / "°C") and made
/// every selection in system green/blue on the default black.
/// The labels are now spelled out exactly as the phone's —
/// Men's / Women's, Body temperature, Fahrenheit / Celsius —
/// and the selections are pine, on the Warm Charcoal ground
/// the app root paints.
/// RW-81 (owner ruling, Sol, Nov 6, 2026): Fit Preference here
/// carries the same TWO buttons as the phone — no third choice.
/// Tapping the selected fit button again returns to neutral
/// (.unisex, nothing highlighted), through the same model
/// transition the phone's control uses
/// (GenderPreference.fitSelection(afterTapping:)). The
/// temperature buttons are untouched: a unit always has a
/// value, so they stay direct-set with no toggle.
struct WatchSettingsView: View {
    @ObservedObject var viewModel: WatchViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Title
                Text(AppStrings.wearSettingsTitle)
                    .font(.headline)
                    .foregroundColor(AppTheme.inkPrimary)

                // Temperature Unit
                VStack(alignment: .leading, spacing: 8) {
                    Text(AppStrings.wearTemperatureUnit)
                        .font(.caption2)
                        .foregroundColor(AppTheme.inkSecondary)

                    VStack(spacing: 8) {
                        WatchSettingsButton(
                            label: AppStrings.unitFahrenheitLabel,
                            isSelected: viewModel.temperatureUnit == .fahrenheit,
                            action: { viewModel.temperatureUnit = .fahrenheit }
                        )

                        WatchSettingsButton(
                            label: AppStrings.unitCelsiusLabel,
                            isSelected: viewModel.temperatureUnit == .celsius,
                            action: { viewModel.temperatureUnit = .celsius }
                        )
                    }
                }

                // Fit Preference — the phone's TWO choices,
                // spelled out, one row each (RW-81: no third
                // button; tapping the selected one again clears
                // back to neutral, nothing highlighted).
                VStack(alignment: .leading, spacing: 8) {
                    Text(AppStrings.wearFitPreference)
                        .font(.caption2)
                        .foregroundColor(AppTheme.inkSecondary)

                    VStack(spacing: 8) {
                        WatchSettingsButton(
                            label: AppStrings.fitOptionMens,
                            isSelected: viewModel.genderPreference == .male,
                            action: {
                                viewModel.genderPreference = viewModel.genderPreference
                                    .fitSelection(afterTapping: .male)
                            }
                        )

                        WatchSettingsButton(
                            label: AppStrings.fitOptionWomens,
                            isSelected: viewModel.genderPreference == .female,
                            action: {
                                viewModel.genderPreference = viewModel.genderPreference
                                    .fitSelection(afterTapping: .female)
                            }
                        )
                    }
                }

                // Body Temperature
                VStack(alignment: .leading, spacing: 8) {
                    Text(AppStrings.wearBodyTemperature)
                        .font(.caption2)
                        .foregroundColor(AppTheme.inkSecondary)

                    Text(viewModel.comfortLevel.shortLabel)
                        .font(.caption2)
                        .foregroundColor(AppTheme.inkPrimary)

                    WatchComfortSlider(comfortLevel: $viewModel.comfortLevel)
                }

                Spacer(minLength: 16)
            }
            .padding(.horizontal, 8)
        }
    }
}

// MARK: - Settings Button

private struct WatchSettingsButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(isSelected ? .white : AppTheme.inkSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(isSelected ? AppTheme.primaryColor : AppTheme.cardFillOnDark)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Comfort Slider

private struct WatchComfortSlider: View {
    @Binding var comfortLevel: ComfortLevel

    private let levels = ComfortLevel.sortedCases

    var body: some View {
        HStack(spacing: 4) {
            ForEach(levels, id: \.rawValue) { level in
                Button(action: { comfortLevel = level }) {
                    Circle()
                        .fill(level == comfortLevel ? comfortColor(level) : Color.white.opacity(0.2))
                        .frame(width: 16, height: 16)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// The phone slider's settled family (C-1): blue at the
    /// cold end, pine at neutral, orange at the warm end.
    private func comfortColor(_ level: ComfortLevel) -> Color {
        switch level {
        case .veryCold, .cold:
            return .blue
        case .neutral:
            return AppTheme.primaryColor
        case .warm, .veryWarm:
            return .orange
        }
    }
}

#Preview {
    WatchSettingsView(viewModel: WatchViewModel())
}
