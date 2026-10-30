import SwiftUI

// MARK: - Onboarding (RW-6.8 / R-17)
//
// One scrolling screen built from the same native PreferenceControls as
// Settings — no pager, no progress bars, no step transitions. The
// temperature unit starts from the device locale
// (Locale.measurementSystem) instead of a hard-coded Fahrenheit.
// Location is a choice made here, in context: "Use My Location" fires
// the system permission request from the tap; "Set a City Instead" is
// remembered and ContentView opens the city picker on first launch.
struct OnboardingView: View {
    @Binding var hasCompletedOnboarding: Bool

    @State private var temperatureUnit: TemperatureUnit = TemperatureUnit.defaultForLocale
    @State private var genderPreference: GenderPreference = .unisex
    @State private var comfortLevel: ComfortLevel = .neutral
    @State private var locationChoice: LocationChoice = .gps

    @StateObject private var locationService = LocationService()

    private enum LocationChoice {
        case gps
        case manual
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {
                header

                temperatureSection

                fitSection

                comfortSection

                locationSection
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .padding(.bottom, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(AppTheme.darkBackground.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            // The single completion action, pinned and always reachable.
            Button(action: complete) {
                Text("Get Started")
                    .font(.appButtonLabel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.primaryColor)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(AppTheme.darkBackground)
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "figure.run")
                .font(.system(size: 48))
                .foregroundStyle(.white)

            Text("Welcome to RunWear")
                .font(.title.weight(.bold))
                .foregroundStyle(.white)

            Text("Outfit recommendations tuned to your weather. Set your preferences once — you can change them anytime in Settings.")
                .font(.appSecondary)
                .foregroundStyle(.white.opacity(0.7))
        }
    }

    // MARK: - Sections

    private func sectionHeader(icon: String, title: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: icon)
                .font(.appSectionTitle)
                .foregroundStyle(.white)

            Text(description)
                .font(.appSecondary)
                .foregroundStyle(.white.opacity(0.7))
        }
    }

    private var temperatureSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                icon: "thermometer.medium",
                title: "Temperature Unit",
                description: "How do you prefer to see temperatures?"
            )

            TemperatureUnitPicker(unit: $temperatureUnit)
        }
    }

    private var fitSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                icon: "tshirt",
                title: "Fit Preference",
                description: "Select your preferred fit for clothing recommendations"
            )

            FitPreferencePicker(preference: $genderPreference)

            if genderPreference == .unisex {
                Text("No preference selected - showing unisex options")
                    .font(.appFootnote)
                    .foregroundStyle(.white.opacity(0.5))
            }
        }
    }

    private var comfortSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                icon: "person.fill.questionmark",
                title: "Body Temperature",
                description: "Do you typically run warm or cold?"
            )

            ComfortLevelControl(comfortLevel: $comfortLevel)
        }
    }

    private var locationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                icon: "location",
                title: "Location",
                description: "RunWear uses your location for local weather — or set a city instead."
            )

            Button {
                locationChoice = .gps
                locationService.requestPermission()
            } label: {
                HStack {
                    Text("Use My Location")
                        .font(.appButtonLabel)
                    Spacer()
                    if locationChoice == .gps {
                        Image(systemName: "checkmark")
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .padding(.horizontal, 16)
            }
            .buttonStyle(.bordered)
            .tint(.white)

            Button {
                locationChoice = .manual
            } label: {
                HStack {
                    Text("Set a City Instead")
                        .font(.appButtonLabel)
                    Spacer()
                    if locationChoice == .manual {
                        Image(systemName: "checkmark")
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .padding(.horizontal, 16)
            }
            .buttonStyle(.bordered)
            .tint(.white)
        }
    }

    // MARK: - Completion

    private func complete() {
        savePreferences()
        hasCompletedOnboarding = true
    }

    private func savePreferences() {
        UserDefaults.standard.set(temperatureUnit.rawValue, forKey: "temperatureUnit")
        UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
        UserDefaults.standard.set(comfortLevel.rawValue, forKey: "comfortLevel")
        UserDefaults.standard.set(
            locationChoice == .manual ? "manual" : "gps",
            forKey: "onboardingLocationChoice"
        )
    }
}

// MARK: - Preview

#Preview {
    OnboardingView(hasCompletedOnboarding: .constant(false))
}
