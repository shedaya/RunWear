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

    /// RW-14 (§2.1): onboarding ends with the fork — the first REAL
    /// recommendation, then "See yourself in every outfit" vs "Use
    /// the standard photos". Preferences come first; the fork is a
    /// first-class final phase, not an afterthought sheet.
    @State private var phase: Phase = .preferences

    private enum LocationChoice {
        case gps
        case manual
    }

    private enum Phase {
        case preferences
        case firstLook
    }

    var body: some View {
        if phase == .firstLook {
            FirstLookView(
                locationService: locationService,
                manualChoice: locationChoice == .manual,
                onComplete: { hasCompletedOnboarding = true }
            )
        } else {
            preferencesBody
        }
    }

    private var preferencesBody: some View {
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
            // The single advance action, pinned and always reachable.
            // RW-14: it no longer completes onboarding — it moves to
            // the first-look fork (the recommendation, then the
            // customize-vs-stock choice).
            Button(action: beginFirstLook) {
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

    /// Preferences are saved first (the first-look view model reads
    /// them back from UserDefaults, exactly like the home screen
    /// will), then the fork phase begins. Onboarding completes only
    /// from the fork — either door (spec §2.2: dismissing the fork
    /// by choosing stock IS the stock choice, fully respected).
    private func beginFirstLook() {
        savePreferences()
        phase = .firstLook
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

// MARK: - First Look (RW-14 fork host)
//
// Loads the user's first REAL recommendation (their location, their
// preferences — a dedicated view model running the same pipeline
// the home screen runs) and presents the fork over it: the
// recommendation's photo, context and outfit stay visible above
// the choice. Either door completes onboarding; "customize" runs
// the opt-in flow first, and finishing it kicks the first
// personalized generation for exactly the cell on screen.
private struct FirstLookView: View {
    @ObservedObject var locationService: LocationService
    let manualChoice: Bool
    let onComplete: () -> Void

    @EnvironmentObject private var store: PersonalHeroStore
    @StateObject private var previewVM = WeatherViewModel()
    @State private var showLocationPicker = false
    @State private var showOptIn = false
    @State private var started = false

    private var hasRecommendation: Bool {
        previewVM.weatherData != nil && previewVM.recommendation != nil
    }

    var body: some View {
        ZStack {
            AppTheme.darkBackground.ignoresSafeArea()

            if hasRecommendation {
                MakeItMeForkView(
                    previewHeroURL: previewVM.heroImageUrl ?? previewVM.fallbackImageUrl,
                    previewContextLine: contextLine,
                    previewOutfitLine: outfitLine,
                    onCustomize: { showOptIn = true },
                    onStock: onComplete
                )
            } else {
                loadingState
            }
        }
        .onAppear(perform: start)
        .onChange(of: locationService.location) { _, newLocation in
            guard previewVM.isUsingGPS, let location = newLocation else { return }
            Task { await previewVM.fetchWeather(for: location) }
        }
        .sheet(isPresented: $showLocationPicker) {
            LocationPickerSheet(
                isPresented: $showLocationPicker,
                isUsingGPS: false,
                onUseCurrentLocation: {
                    previewVM.switchToGPS()
                    locationService.requestPermission()
                },
                onSelectLocation: { coordinate, name in
                    previewVM.setManualLocation(coordinate: coordinate, name: name)
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .fullScreenCover(isPresented: $showOptIn) {
            MakeItMeOptInFlow(
                mode: .optIn,
                onCommit: { photos in
                    try await store.completeOptIn(
                        jpegPhotos: photos,
                        genderPreference: previewVM.genderPreference
                    )
                },
                onFinish: {
                    showOptIn = false
                    Task {
                        // Kick the first generation for the exact
                        // cell the user just saw, then finish
                        // onboarding — the home screen's progress
                        // line (G1/G2) carries it from here.
                        previewVM.personalStore = store
                        if let combo = await previewVM.reloadHeroForPersonal() {
                            store.startUserInitiatedGeneration(for: combo)
                        }
                        onComplete()
                    }
                },
                onCancel: { showOptIn = false }
            )
        }
    }

    private var contextLine: String {
        "\(previewVM.locationName) · \(previewVM.temperatureUnit.format(previewVM.currentFeelsLike))"
    }

    private var outfitLine: String {
        previewVM.recommendation?.allItems.map(\.name).joined(separator: " · ") ?? ""
    }

    /// Routes the first load by the choice made on the preferences
    /// screen: a restored/manual city fetches directly, a manual
    /// choice opens the picker, GPS waits for (or uses) the fix.
    private func start() {
        guard !started else { return }
        started = true
        previewVM.personalStore = store
        if let manual = previewVM.restoredManualLocation {
            Task { await previewVM.fetchWeather(for: manual) }
        } else if manualChoice {
            showLocationPicker = true
        } else if locationService.authorizationStatus == .authorizedWhenInUse ||
                  locationService.authorizationStatus == .authorizedAlways {
            locationService.requestLocation()
        }
        // notDetermined: the loading state below carries the
        // permission ask inline.
    }

    @ViewBuilder private var loadingState: some View {
        VStack(spacing: 20) {
            Spacer()
            if let error = previewVM.errorMessage {
                Text(error)
                    .font(.appSecondary)
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                Button("Try Again") {
                    started = false
                    start()
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.primaryColor)
            } else if locationService.authorizationStatus == .notDetermined && !manualChoice {
                // The user reached the fork without ever tapping a
                // location door on the preferences screen — ask here,
                // in context, with the same two doors.
                Text("One more thing — where are you running?")
                    .font(.appSectionTitle)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                Button("Use My Location") {
                    locationService.requestPermission()
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.primaryColor)
                Button("Set a City Instead") {
                    showLocationPicker = true
                }
                .font(.appButtonLabel)
                .foregroundColor(.white.opacity(0.8))
            } else if locationService.authorizationStatus == .denied ||
                      locationService.authorizationStatus == .restricted {
                Text("Location is off — set a city to see your first recommendation.")
                    .font(.appSecondary)
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                Button("Set a City Instead") {
                    showLocationPicker = true
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.primaryColor)
            } else {
                ProgressView()
                    .tint(.white)
                // DEVIATION-COPY (no deck entry): the wait line for
                // the first recommendation load.
                Text("Getting your first recommendation…")
                    .font(.appSecondary)
                    .foregroundColor(.white.opacity(0.7))
            }
            Spacer()
        }
        .padding(.horizontal, 24)
    }
}

// MARK: - Preview

#Preview {
    OnboardingView(hasCompletedOnboarding: .constant(false))
        .environmentObject(PersonalHeroStore())
}
