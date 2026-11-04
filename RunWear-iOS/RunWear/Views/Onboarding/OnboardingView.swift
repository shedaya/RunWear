import SwiftUI

// MARK: - Onboarding (WS-10 · A1/A2 — the design's sequence, §9)
//
// The sequence is the design's (OnboardingSequence): promise
// (frame 28) → setup (frames 29–31) → choice (32) → consent (33)
// → first look (34). iOS expression (§1.4): the Welcome/promise
// screen is the root of a NavigationStack and the setup questions
// advance ONE question per screen with native push transitions —
// Back is the exact reverse. Preferences are built FIRST, before
// Make It Me is ever introduced: the free-looks promise lives only
// on frame 28's own screen (A1), the fork (frame 32) introduces
// the feature, consent (frame 33) follows the fork's customize
// door, and the first personal look (frame 34) ends the flow.
//
// The temperature unit starts from the device locale
// (Locale.measurementSystem) instead of a hard-coded Fahrenheit.
// Location is a choice made at the location step, in context:
// "Use My Location" fires the system permission request from the
// tap; "Set a City Instead" is remembered and the first-look
// phase opens the city picker.
struct OnboardingView: View {
    @Binding var hasCompletedOnboarding: Bool

    @State private var temperatureUnit: TemperatureUnit = TemperatureUnit.defaultForLocale
    @State private var genderPreference: GenderPreference = .unisex
    @State private var comfortLevel: ComfortLevel = .neutral
    @State private var locationChoice: LocationChoice = .gps

    @StateObject private var locationService = LocationService()

    /// The first-look phase (fork → consent → first look) begins
    /// only after the setup's last step saves the preference
    /// profile — preferences first, always.
    @State private var phase: Phase = .setup
    /// The setup stack's pushed steps (the Welcome screen is its
    /// root). Native push, one question per screen (§1.4).
    @State private var path: [OnboardingStep] = []

    private enum LocationChoice {
        case gps
        case manual
    }

    private enum Phase {
        case setup
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
            setupStack
        }
    }

    private var setupStack: some View {
        NavigationStack(path: $path) {
            welcomeScreen
                .navigationDestination(for: OnboardingStep.self) { step in
                    switch step {
                    case .temperature:
                        questionScreen(
                            title: "Temperature",
                            question: "How do you measure the weather?"
                        ) {
                            TemperatureUnitPicker(unit: $temperatureUnit)
                        }
                    case .fit:
                        questionScreen(
                            title: "Fit Preference",
                            question: "Whose fit should the outfits follow?"
                        ) {
                            FitPreferencePicker(preference: $genderPreference)
                            // WS-9 fix: a debug-style reassurance
                            // line that used to sit under this picker
                            // is removed — no design frame carries
                            // it, Android has no equivalent, and its
                            // vocabulary is introduced by no frame.
                            // The picker's own "No preference" label
                            // states the selection; the step needs
                            // no gloss. (The removed string is kept
                            // out of this comment on purpose: a
                            // source pin asserts its absence.)
                        }
                    case .comfort:
                        questionScreen(
                            title: "Body Temperature",
                            question: "Do you get cold easily, or overheat quickly?"
                        ) {
                            ComfortLevelControl(comfortLevel: $comfortLevel)
                        }
                    case .location:
                        locationScreen
                    case .welcome, .firstLook:
                        // Neither is a pushed setup destination:
                        // welcome is the stack's root, and the
                        // first-look phase replaces the stack.
                        EmptyView()
                    }
                }
        }
    }

    // MARK: - Frame 28 · Welcome (the promise screen)

    /// Frame 28 as designed: the script logo at specimen size, the
    /// headline (A2 — Sol's pick, character for character, from
    /// OnboardingCopy), one promise per row, nothing to decide
    /// yet. This screen is the ONLY place in onboarding where the
    /// free-looks promise may appear (A1) — it is stated here,
    /// inside the Make It Me promise row, as one of the screen's
    /// product truths; no price appears anywhere in onboarding.
    private var welcomeScreen: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 10) {
                Text("RunWear")
                    .font(.greatVibes(size: 64))
                    .foregroundStyle(AppTheme.inkPrimary)

                Text(OnboardingCopy.welcomeHeadline)
                    .font(.system(size: 15))
                    .foregroundStyle(AppTheme.inkSecondary)
            }

            VStack(alignment: .leading, spacing: 24) {
                promiseRow(
                    icon: "cloud",
                    title: "The right outfit for the weather",
                    detail: "One recommendation for the run you're about to take — feels-like, wind, and rain included."
                )
                promiseRow(
                    icon: "person",
                    title: "See yourself in every outfit",
                    detail: "Make It Me puts you in the photos. \(OnboardingCopy.freeLooksPromise)"
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 52)

            Spacer()

            Button {
                path.append(.temperature)
            } label: {
                Text("Get Started")
                    .font(.appButtonLabel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.primaryColor)

            Text("Free to start · Set up in under a minute")
                .font(.appFootnote)
                .foregroundStyle(AppTheme.inkSecondary)
                .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.background.ignoresSafeArea())
    }

    private func promiseRow(icon: String, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(AppTheme.pinePayload)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.appSectionTitle)
                    .foregroundStyle(AppTheme.inkPrimary)
                Text(detail)
                    .font(.appSecondary)
                    .foregroundStyle(AppTheme.inkSecondary)
            }
        }
    }

    // MARK: - Frames 29–30 · One question per screen

    /// One setup question on its own pushed screen: the question,
    /// the same native PreferenceControls Settings uses (so the
    /// two surfaces can never drift apart, and each selection
    /// settles with the control's own selection haptic, §1.4),
    /// and one Continue that pushes the next step.
    private func questionScreen<Content: View>(
        title: String,
        question: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(title)
                    .font(.title.weight(.bold))
                    .foregroundStyle(AppTheme.inkPrimary)

                Text(question)
                    .font(.appSecondary)
                    .foregroundStyle(AppTheme.inkSecondary)

                content()
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            Button {
                advanceToNextStep()
            } label: {
                Text("Continue")
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

    /// Push the step after the one on top of the stack. The
    /// location step is the setup's last: its own doors finish
    /// the setup (see below), so Continue never pushes past it.
    private func advanceToNextStep() {
        guard let current = path.last, let next = current.next,
              next != .firstLook else { return }
        path.append(next)
    }

    /// Frame 30 — the location step. The honest privacy copy sits
    /// in the step itself (promoted from the permission screen):
    /// location kept on the phone, forecast lookups rounded to a
    /// rough area, never sold, never advertising. Both doors
    /// finish the setup: "Use My Location" fires the system
    /// permission prompt from the tap (frame 31 is that prompt's
    /// standalone context); "Set a City Instead" is a full door,
    /// and the first-look phase opens the place search for it.
    private var locationScreen: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("One more thing — where are you running?")
                    .font(.title.weight(.bold))
                    .foregroundStyle(AppTheme.inkPrimary)

                Text("RunWear saves your most recent location on your phone so your outfit is ready when you open the app. For a forecast, it sends coordinates rounded to a rough area — never your exact spot — and your location is never sold or used for advertising.")
                    .font(.appSecondary)
                    .foregroundStyle(AppTheme.inkSecondary)

                Button {
                    locationChoice = .gps
                    locationService.requestPermission()
                    beginFirstLook()
                } label: {
                    Text("Use My Location")
                        .font(.appButtonLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.primaryColor)

                Button {
                    locationChoice = .manual
                    beginFirstLook()
                } label: {
                    Text("Set a City Instead")
                        .font(.appButtonLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                .tint(AppTheme.inkPrimary)
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(AppTheme.background.ignoresSafeArea())
    }

    // MARK: - Completion of setup

    /// Preferences are saved first (the first-look view model reads
    /// them back from UserDefaults, exactly like the home screen
    /// will), then the first-look phase begins. Onboarding completes
    /// only from that phase — either door (spec §2.2: dismissing the
    /// fork by choosing stock IS the stock choice, fully respected).
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
    /// WS-10 (§1.4): the frame-34 reveal's state — the photo
    /// crossfades in, ONE success haptic fires as it lands, and
    /// the §9 summary band slides up 120 ms after it lands
    /// (FirstLookReveal carries the numbers).
    @State private var firstLookPhotoRevealed = false
    @State private var firstLookBandRevealed = false
    @State private var firstLookHapticNonce = 0

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
                // §1.4 reveal: the photo crossfades in (it never
                // pops, and it is never replaced mid-view).
                .opacity(firstLookPhotoRevealed ? 1 : 0)

                Text(contextLine)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppTheme.inkSecondary)

                // Frame 34's own lines, as designed: the moment
                // stated, then the honest meter (look 1 of 5,
                // §8 Q5 — 4 remain, whenever the user wants them).
                Text("You're in the photo now.")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AppTheme.inkPrimary)

                Text("That was look 1 of your 5 free looks — 4 remain, whenever you want them.")
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.inkSecondary)

                // The §9 band — the recommended pair, verbatim.
                // It slides up 120 ms after the photo lands
                // (FirstLookReveal.bandStartSeconds from reveal
                // start), never with or ahead of the photo.
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
                .opacity(firstLookBandRevealed ? 1 : 0)
                .offset(y: firstLookBandRevealed ? 0 : 12)

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
        // WS-10 (§1.4/§1.7): the reveal runs once when the moment
        // first appears; the success haptic is the moment's only
        // haptic and fires exactly once, as the photo lands.
        .onAppear(perform: startFirstLookReveal)
        .sensoryFeedback(.success, trigger: firstLookHapticNonce)
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

    /// The frame-34 reveal (§1.4), driven off FirstLookReveal's
    /// numbers: photo crossfade → (photo lands) ONE success
    /// haptic → +120 ms, the summary band slides up. Guarded so
    /// a re-appearance never replays it — the haptic count for
    /// the moment is FirstLookReveal.successHapticCount (1).
    private func startFirstLookReveal() {
        guard !firstLookPhotoRevealed else { return }
        withAnimation(.easeInOut(duration: FirstLookReveal.photoCrossfadeSeconds)) {
            firstLookPhotoRevealed = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + FirstLookReveal.photoCrossfadeSeconds) {
            firstLookHapticNonce += FirstLookReveal.successHapticCount
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + FirstLookReveal.bandStartSeconds) {
            withAnimation(.smooth(duration: 0.35)) {
                firstLookBandRevealed = true
            }
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
