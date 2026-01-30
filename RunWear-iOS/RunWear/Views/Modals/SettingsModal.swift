import SwiftUI

struct SettingsModal: View {
    @Binding var isPresented: Bool
    @Binding var temperatureUnit: TemperatureUnit
    @Binding var genderPreference: GenderPreference
    @Binding var comfortLevel: ComfortLevel

    @State private var dragOffset: CGFloat = 0
    private let dismissThreshold: CGFloat = 100

    var body: some View {
        ZStack {
            // Backdrop
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }

            // Modal content
            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 24) {
                    // Drag handle
                    dragHandle

                    // Title
                    Text("Settings")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    // Temperature Unit
                    temperatureUnitSection

                    // Gender Preference
                    genderPreferenceSection

                    // Comfort Level
                    comfortLevelSection

                    Spacer().frame(height: 16)
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.modalCornerRadius)
                        .fill(AppTheme.darkBackground)
                )
                .offset(y: dragOffset)
                .gesture(dragGesture)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .transition(.opacity)
    }

    // MARK: - Drag Handle

    private var dragHandle: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(Color.white.opacity(0.3))
            .frame(width: 40, height: 5)
            .padding(.top, 8)
    }

    // MARK: - Temperature Unit Section

    private var temperatureUnitSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Temperature Unit")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.7))

            HStack(spacing: 12) {
                SettingsToggleButton(
                    label: "°F",
                    isSelected: temperatureUnit == .fahrenheit,
                    action: { temperatureUnit = .fahrenheit }
                )

                SettingsToggleButton(
                    label: "°C",
                    isSelected: temperatureUnit == .celsius,
                    action: { temperatureUnit = .celsius }
                )
            }
        }
    }

    // MARK: - Gender Preference Section

    private var genderPreferenceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Fit Preference")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.7))

            Text("Tap to select, tap again to deselect")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.5))

            ToggleableGenderSelector(selection: $genderPreference)
        }
    }

    // MARK: - Comfort Level Section

    private var comfortLevelSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Body Temperature")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.7))

            Text(comfortLevel.shortLabel)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white)

            ComfortSlider(comfortLevel: $comfortLevel)
        }
    }

    // MARK: - Drag Gesture

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if value.translation.height > 0 {
                    dragOffset = value.translation.height
                }
            }
            .onEnded { value in
                if value.translation.height > dismissThreshold {
                    dismiss()
                } else {
                    withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
                        dragOffset = 0
                    }
                }
            }
    }

    private func dismiss() {
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
        }
    }
}

// MARK: - Settings Toggle Button

private struct SettingsToggleButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(isSelected ? .white : .white.opacity(0.5))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(isSelected ? Color.white.opacity(0.25) : Color.white.opacity(0.1))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isSelected ? Color.white.opacity(0.4) : Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }
}

// MARK: - Comfort Slider

private struct ComfortSlider: View {
    @Binding var comfortLevel: ComfortLevel

    private let levels = ComfortLevel.sortedCases

    var body: some View {
        VStack(spacing: 8) {
            // Slider
            GeometryReader { geometry in
                let segmentWidth = geometry.size.width / CGFloat(levels.count)
                let selectedIndex = levels.firstIndex(of: comfortLevel) ?? 3

                ZStack(alignment: .leading) {
                    // Track
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.white.opacity(0.1))
                        .frame(height: 8)

                    // Selected segment indicator
                    RoundedRectangle(cornerRadius: 4)
                        .fill(comfortColor)
                        .frame(width: segmentWidth, height: 8)
                        .offset(x: CGFloat(selectedIndex) * segmentWidth)
                        .animation(.easeInOut(duration: 0.2), value: selectedIndex)
                }
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let index = Int(value.location.x / segmentWidth)
                            let clampedIndex = max(0, min(levels.count - 1, index))
                            comfortLevel = levels[clampedIndex]
                        }
                )
            }
            .frame(height: 32)

            // Labels
            HStack {
                Text("Runs Cold")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.5))
                Spacer()
                Text("Runs Warm")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
    }

    private var comfortColor: Color {
        switch comfortLevel {
        case .veryCold, .cold, .slightlyCold:
            return .blue
        case .neutral:
            return .green
        case .slightlyWarm, .warm, .veryWarm:
            return .orange
        }
    }
}

// MARK: - Preview

#Preview {
    SettingsModal(
        isPresented: .constant(true),
        temperatureUnit: .constant(.fahrenheit),
        genderPreference: .constant(.male),
        comfortLevel: .constant(.neutral)
    )
}
