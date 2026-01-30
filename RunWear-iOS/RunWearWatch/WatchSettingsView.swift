import SwiftUI

struct WatchSettingsView: View {
    @ObservedObject var viewModel: WatchViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Title
                Text("Settings")
                    .font(.headline)
                    .foregroundColor(.white)

                // Temperature Unit
                VStack(alignment: .leading, spacing: 8) {
                    Text("Temperature")
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    HStack(spacing: 8) {
                        WatchSettingsButton(
                            label: "°F",
                            isSelected: viewModel.temperatureUnit == .fahrenheit,
                            action: { viewModel.temperatureUnit = .fahrenheit }
                        )

                        WatchSettingsButton(
                            label: "°C",
                            isSelected: viewModel.temperatureUnit == .celsius,
                            action: { viewModel.temperatureUnit = .celsius }
                        )
                    }
                }

                // Gender Preference
                VStack(alignment: .leading, spacing: 8) {
                    Text("Fit Preference")
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    HStack(spacing: 8) {
                        WatchGenderButton(
                            label: "M",
                            isSelected: viewModel.genderPreference == .male,
                            action: {
                                viewModel.genderPreference = viewModel.genderPreference == .male ? .unisex : .male
                            }
                        )

                        WatchGenderButton(
                            label: "F",
                            isSelected: viewModel.genderPreference == .female,
                            action: {
                                viewModel.genderPreference = viewModel.genderPreference == .female ? .unisex : .female
                            }
                        )
                    }

                    if viewModel.genderPreference == .unisex {
                        Text("None selected")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                }

                // Comfort Level
                VStack(alignment: .leading, spacing: 8) {
                    Text("Body Temp")
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    Text(viewModel.comfortLevel.shortLabel)
                        .font(.caption2)
                        .foregroundColor(.white)

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
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(isSelected ? .white : .gray)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(isSelected ? Color.green.opacity(0.6) : Color.white.opacity(0.1))
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Gender Button

private struct WatchGenderButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(isSelected ? .white : .gray)
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(isSelected ? Color.blue.opacity(0.6) : Color.white.opacity(0.1))
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

    private func comfortColor(_ level: ComfortLevel) -> Color {
        switch level {
        case .veryCold, .cold, .slightlyCold:
            return .blue
        case .neutral:
            return .green
        case .slightlyWarm, .warm, .veryWarm:
            return .orange
        }
    }
}

#Preview {
    WatchSettingsView(viewModel: WatchViewModel())
}
