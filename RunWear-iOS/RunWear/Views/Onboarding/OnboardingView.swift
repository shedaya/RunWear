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
        .background(AppTheme.background.ignoresSafeArea())
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
            .background(AppTheme.background)
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "figure.run")
                .font(.system(size: 48))
                .foregroundStyle(AppTheme.inkPrimary)

            Text("Welcome to RunWear")
                .font(.title.weight(.bold))
                .foregroundStyle(AppTheme.inkPrimary)

            Text("Outfit recommendations tuned to your weather. Set your preferences once — you can change them anytime in Settings.")
                .font(.appSecondary)
                .foregroundStyle(AppTheme.inkSecondary)

            // Frame 28: product truth only — answers "will this
            // cost me?" early and honestly. It is information,
            // not an ask (§9 placement table).
            Text("Your first 5 looks are free")
                .font(.appSecondary.weight(.semibold))
                .foregroundStyle(AppTheme.inkPrimary)
        }
    }

    // MARK: - Sections

    private func sectionHeader(icon: String, title: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: icon)
                .font(.appSectionTitle)
                .foregroundStyle(AppTheme.inkPrimary)

            Text(description)
                .font(.appSecondary)
                .foregroundStyle(AppTheme.inkSecondary)
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
                    .foregroundStyle(AppTheme.inkSecondary)
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

            // Frame 30: the coded honest privacy copy, promoted
            // into the step itself (§8-G) — and true of the code:
            // forecast lookups round coordinates to a rough area
            // at the service (WeatherService), the precise fix
            // never leaves the phone.
            Text("Your location stays on this phone. Forecast lookups are rounded to a rough area — never your exact spot — and your location is never sold or used for advertising.")
                .font(.appFootnote)
                .foregroundStyle(AppTheme.inkSecondary)

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
            .tint(AppTheme.inkPrimary)

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
            .tint(AppTheme.inkPrimary)
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
    /// RW-16.7: the opt-in commit driver is owned HERE — the
    /// stable host of the flow — not by the flow view, so the
    /// flow's re-creation can never orphan a commit mid-flight
    /// (the Settings-side instance of this defect is RW-16.9 §4).
    @StateObject private var commitDriver = OptInCommitDriver()
    @State private var showLocationPicker = false
    @State private var showOptIn = false
    @State private var started = false
    /// RW-26 frame 34: the first-look moment — shown ONCE, at
    /// onboarding's natural end on the fork's success path.
    @State private var showFirstLookMoment = false
    @State private var showPremiumSheet = false

    private var hasRecommendation: Bool {
        previewVM.weatherData != nil && previewVM.recommendation != nil
    }

    var body: some View {
        ZStack {
            // Dual-mode conversion: the first-look surface hosts
            // the Make It Me fork, so it follows the app's
            // appearance with the rest of the flow. (The
            // preferences phase above follows the appearance
            // too — the follow-on conversion.)
            AppTheme.background.ignoresSafeArea()

            if showFirstLookMoment {
                firstLookMomentView
            } else if hasRecommendation {
                if MakeItMeAvailability.isEnabled {
                    MakeItMeForkView(
                        previewHeroURL: previewVM.heroImageUrl ?? previewVM.fallbackImageUrl,
                        previewContextLine: contextLine,
                        previewOutfitLine: outfitLine,
                        onCustomize: { showOptIn = true },
                        onStock: onComplete
                    )
                } else {
                    // "Make it me" OFF (see MakeItMeAvailability):
                    // no fork, no opt-in — the first look is shown
                    // as itself and the only door completes
                    // onboarding, exactly the fork's stock door.
                    firstLookReadyView
                }
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
                    // RW-15 follow-up: dismissal happens
                    // synchronously here (the commit driver invokes
                    // this on the main actor); ONLY the
                    // first-generation kick is deferred.
                    // RW-26: completion now routes through the
                    // frame-34 first-look moment — the ONE soft
                    // ask, shown once at onboarding's end; its
                    // primary door completes onboarding.
                    OptInFinish.perform(
                        dismiss: { showOptIn = false },
                        complete: { showFirstLookMoment = true },
                        kick: {
                            // Kick the first generation for the
                            // exact cell the user just saw — the
                            // home screen's progress line (G1/G2)
                            // carries it from here.
                            previewVM.personalStore = store
                            if let combo = await previewVM.reloadHeroForPersonal() {
                                store.startUserInitiatedGeneration(for: combo)
                            }
                        }
                    )
                },
                onCancel: { showOptIn = false },
                commitDriver: commitDriver
            )
        }
    }

    /// The first look with "Make it me" off: the same preview the
    /// fork shows (the recommendation's photo, context and outfit),
    /// with no pitch and a single Continue door into the app.
    private var firstLookReadyView: some View {
        ScrollView {
            VStack(spacing: 20) {
                VStack(spacing: 8) {
                    AsyncImage(url: (previewVM.heroImageUrl ?? previewVM.fallbackImageUrl).flatMap(URL.init(string:))) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().aspectRatio(contentMode: .fill)
                        default:
                            AppTheme.cardOnSheet
                        }
                    }
                    .frame(height: 180)
                    .clipped()
                    .cornerRadius(16)

                    Text(contextLine)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(AppTheme.inkSecondary)
                    Text(outfitLine)
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.inkSecondary)
                        .multilineTextAlignment(.center)
                }

                Button(action: onComplete) {
                    Text("Continue")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
        }
    }

    /// Frame 34 — the first-look moment. The user has just seen
    /// themselves in a kit photo; the primary door is the run's
    /// clothing answer ("See My Outfit"), and the ONE soft ask is
    /// the quiet second door ("See Premium") — once, never a modal
    /// again. The band carries the two benefit concepts in the §9
    /// recommended pair, verbatim. The onboarding look counts as
    /// look 1 of 5 (§8 Q5), and the meter says so.
    private var firstLookMomentView: some View {
        ScrollView {
            VStack(spacing: 20) {
                AsyncImage(url: (previewVM.heroImageUrl ?? previewVM.fallbackImageUrl).flatMap(URL.init(string:))) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        AppTheme.cardOnSheet
                    }
                }
                .frame(height: 220)
                .clipped()
                .cornerRadius(16)

                Text(contextLine)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppTheme.inkSecondary)

                Text("That's look 1 of your 5 free looks.")
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.inkSecondary)

                // The §9 band — the recommended pair, verbatim.
                VStack(alignment: .leading, spacing: 8) {
                    Text("Ready the moment you open the app — made ahead, never while you wait.")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text("Every outfit, every day — no number to run out of.")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.cardFill))

                Button(action: onComplete) {
                    Text("See My Outfit")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                }

                // The quiet second door — dismissing the ask IS
                // continuing; this screen never returns as a modal.
                Button {
                    showPremiumSheet = true
                } label: {
                    Text("See Premium")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(AppTheme.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
        }
        // WS-5 (§1.3): named detent — the paywall's content
        // scrolls, so .large only. Dismissal (any path: swipe,
        // purchase, restore) still completes onboarding via
        // onDismiss, exactly as before.
        .sheet(isPresented: $showPremiumSheet, onDismiss: onComplete) {
            MakeItMeSubscriptionSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    private var contextLine: String {
        "\(previewVM.placeDisplayName) · \(previewVM.temperatureUnit.format(previewVM.currentFeelsLike))"
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
        // "Make it me" OFF (see MakeItMeAvailability): the preview
        // view model is never wired to the personal store either.
        if MakeItMeAvailability.isEnabled {
            previewVM.personalStore = store
        }
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
                    .foregroundColor(AppTheme.inkPrimary)
                    .multilineTextAlignment(.center)
                Button("Try Again") {
                    started = false
                    start()
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
            } else if locationService.authorizationStatus == .notDetermined && !manualChoice {
                // The user reached the fork without ever tapping a
                // location door on the preferences screen — ask here,
                // in context, with the same two doors.
                Text("One more thing — where are you running?")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                    .multilineTextAlignment(.center)
                Button("Use My Location") {
                    locationService.requestPermission()
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
                Button("Set a City Instead") {
                    showLocationPicker = true
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.inkPrimary)
            } else if locationService.authorizationStatus == .denied ||
                      locationService.authorizationStatus == .restricted {
                Text("Location is off — set a city to see your first recommendation.")
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkPrimary)
                    .multilineTextAlignment(.center)
                Button("Set a City Instead") {
                    showLocationPicker = true
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
            } else {
                ProgressView()
                    .tint(AppTheme.inkPrimary)
                // DEVIATION-COPY (no deck entry): the wait line for
                // the first recommendation load.
                Text("Getting your first recommendation…")
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkSecondary)
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
