import SwiftUI

struct OnboardingView: View {
    @Binding var hasCompletedOnboarding: Bool

    @State private var currentStep = 0
    @State private var temperatureUnit: TemperatureUnit = .fahrenheit
    @State private var genderPreference: GenderPreference = .unisex
    @State private var comfortLevel: ComfortLevel = .neutral

    private let totalSteps = 3

    var body: some View {
        ZStack {
            // Dark background
            AppTheme.darkBackground
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Progress indicator
                progressIndicator
                    .padding(.top, 60)
                    .padding(.horizontal, 24)

                Spacer()

                // Content based on step
                Group {
                    switch currentStep {
                    case 0:
                        temperatureStep
                    case 1:
                        genderStep
                    case 2:
                        comfortStep
                    default:
                        EmptyView()
                    }
                }
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))

                Spacer()

                // Navigation buttons
                navigationButtons
                    .padding(.horizontal, 24)
                    .padding(.bottom, 40)
            }
        }
    }

    // MARK: - Progress Indicator

    private var progressIndicator: some View {
        HStack(spacing: 8) {
            ForEach(0..<totalSteps, id: \.self) { step in
                RoundedRectangle(cornerRadius: 2)
                    .fill(step <= currentStep ? Color.white : Color.white.opacity(0.3))
                    .frame(height: 4)
                    .animation(.easeInOut(duration: 0.3), value: currentStep)
            }
        }
    }

    // MARK: - Step 1: Temperature Unit

    private var temperatureStep: some View {
        VStack(spacing: 32) {
            // Icon
            Image(systemName: "thermometer.medium")
                .font(.system(size: 64))
                .foregroundColor(.white)

            // Title and description
            VStack(spacing: 12) {
                Text("Temperature Unit")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)

                Text("How do you prefer to see temperatures?")
                    .font(.system(size: 17))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
            }

            // Toggle buttons
            HStack(spacing: 16) {
                OnboardingToggleButton(
                    title: "Fahrenheit",
                    subtitle: "°F",
                    isSelected: temperatureUnit == .fahrenheit,
                    action: { temperatureUnit = .fahrenheit }
                )

                OnboardingToggleButton(
                    title: "Celsius",
                    subtitle: "°C",
                    isSelected: temperatureUnit == .celsius,
                    action: { temperatureUnit = .celsius }
                )
            }
            .padding(.horizontal, 24)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Step 2: Gender Preference

    private var genderStep: some View {
        VStack(spacing: 32) {
            // Icon
            Image(systemName: "figure.run")
                .font(.system(size: 64))
                .foregroundColor(.white)

            // Title and description
            VStack(spacing: 12) {
                Text("Fit Preference")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)

                Text("Select your preferred fit for clothing recommendations")
                    .font(.system(size: 17))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
            }

            // Gender selector
            VStack(spacing: 12) {
                Text("Tap to select, tap again to clear")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.5))

                ToggleableGenderSelector(selection: $genderPreference)
                    .padding(.horizontal, 24)

                if genderPreference == .unisex {
                    Text("No preference selected - showing unisex options")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.5))
                }
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Step 3: Comfort Level

    private var comfortStep: some View {
        VStack(spacing: 32) {
            // Icon
            Image(systemName: "person.fill.questionmark")
                .font(.system(size: 64))
                .foregroundColor(.white)

            // Title and description
            VStack(spacing: 12) {
                Text("Body Temperature")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)

                Text("Do you typically run warm or cold?")
                    .font(.system(size: 17))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
            }

            // Comfort selector
            VStack(spacing: 16) {
                Text(comfortLevel.shortLabel)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)

                OnboardingComfortSlider(comfortLevel: $comfortLevel)
                    .padding(.horizontal, 24)
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Navigation Buttons

    private var navigationButtons: some View {
        HStack(spacing: 16) {
            // Back button (except on first step)
            if currentStep > 0 {
                Button(action: previousStep) {
                    HStack {
                        Image(systemName: "chevron.left")
                        Text("Back")
                    }
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.white.opacity(0.1))
                    )
                }
            }

            // Next/Finish button
            Button(action: nextStep) {
                HStack {
                    Text(currentStep == totalSteps - 1 ? "Get Started" : "Next")
                    if currentStep < totalSteps - 1 {
                        Image(systemName: "chevron.right")
                    }
                }
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(AppTheme.primaryColor)
                )
            }
        }
    }

    // MARK: - Navigation Actions

    private func nextStep() {
        if currentStep < totalSteps - 1 {
            withAnimation(.easeInOut(duration: 0.3)) {
                currentStep += 1
            }
        } else {
            // Save preferences and complete onboarding
            savePreferences()
            withAnimation(.easeInOut(duration: 0.3)) {
                hasCompletedOnboarding = true
            }
        }
    }

    private func previousStep() {
        if currentStep > 0 {
            withAnimation(.easeInOut(duration: 0.3)) {
                currentStep -= 1
            }
        }
    }

    private func savePreferences() {
        UserDefaults.standard.set(temperatureUnit.rawValue, forKey: "temperatureUnit")
        UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
        UserDefaults.standard.set(comfortLevel.rawValue, forKey: "comfortLevel")
    }
}

// MARK: - Onboarding Toggle Button

private struct OnboardingToggleButton: View {
    let title: String
    let subtitle: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Text(subtitle)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.5))

                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.5))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(isSelected ? Color.white.opacity(0.2) : Color.white.opacity(0.05))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.white.opacity(0.4) : Color.white.opacity(0.1), lineWidth: 2)
            )
        }
    }
}

// MARK: - Onboarding Comfort Slider

private struct OnboardingComfortSlider: View {
    @Binding var comfortLevel: ComfortLevel

    private let levels = ComfortLevel.sortedCases

    var body: some View {
        VStack(spacing: 16) {
            // Slider track with segments
            GeometryReader { geometry in
                let segmentWidth = geometry.size.width / CGFloat(levels.count)
                let selectedIndex = levels.firstIndex(of: comfortLevel) ?? 3

                ZStack(alignment: .leading) {
                    // Track background
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.white.opacity(0.1))
                        .frame(height: 16)

                    // Selected indicator
                    RoundedRectangle(cornerRadius: 8)
                        .fill(comfortColor)
                        .frame(width: segmentWidth - 4, height: 16)
                        .offset(x: CGFloat(selectedIndex) * segmentWidth + 2)
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: selectedIndex)
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
            .frame(height: 40)

            // Labels
            HStack {
                Text("❄️ Runs Cold")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.6))
                Spacer()
                Text("Runs Warm 🔥")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.6))
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
    OnboardingView(hasCompletedOnboarding: .constant(false))
}
