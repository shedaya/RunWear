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

    /// W-19 (GEN-2): "Use My Location" no longer advances the flow
    /// in the same tap that requests permission (see the location
    /// step). While the system prompt is outstanding the step
    /// stays on screen in its designed layout with this pending
    /// state driving its loading row; the flow advances only when
    /// the authorization actually resolves.
    @State private var locationPermissionPending = false
    /// Set when the permission resolves (or already stands) denied
    /// or restricted: the step stays put and says so, and "Set a
    /// City Instead" remains the working door.
    @State private var locationPermissionDenied = false

    /// The first-look phase (fork → consent → first look) begins
    /// only after the setup's last step saves the preference
    /// profile — preferences first, always.
    @State private var phase: Phase = .setup
    /// The setup stack's pushed steps (the Welcome screen is its
    /// root). Native push; the three preference questions share
    /// ONE combined screen (GEN-3 ruling, Sol Nov 4).
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
                    case .preferences:
                        preferencesScreen
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
    /// yet. No price appears anywhere in onboarding. (A1's
    /// free-looks promise placement on this screen was superseded
    /// by Sol's GEN-3 round-2 final point-2 copy — see
    /// OnboardingCopy.freeLooksPromise.)
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

            // GEN-3 ROUND 2: the name-led composition — each
            // point's headline leads with its feature name in
            // pine bold, the plain phrase in regular weight after
            // it (OnboardingCopy.welcomePoints carries the exact
            // strings). The settled layout and icons stand.
            VStack(alignment: .leading, spacing: 24) {
                welcomePointRow(icon: "cloud", point: OnboardingCopy.welcomePoints[0])
                welcomePointRow(icon: "person", point: OnboardingCopy.welcomePoints[1])
                welcomePointRow(icon: "rectangle.stack", point: OnboardingCopy.welcomePoints[2])
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 52)

            Spacer()

            Button {
                path.append(.preferences)
            } label: {
                Text("Get Started")
                    .font(.appButtonLabel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.primaryColor)

            Text(OnboardingCopy.welcomeFooter)
                .font(.appFootnote)
                .foregroundStyle(AppTheme.inkSecondary)
                .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.background.ignoresSafeArea())
    }

    /// One frame-28 welcome point (GEN-3 ROUND 2): the name-led
    /// headline — the feature name in PINE BOLD leading, the
    /// plain phrase in regular weight after it, one composed
    /// line over the settled icon seat.
    private func welcomePointRow(icon: String, point: OnboardingCopy.WelcomePoint) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(AppTheme.pinePayload)
                .frame(width: 28)

            (Text(point.name)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.pinePayload)
             + Text(" ")
             + Text(point.phrase))
                .font(.system(size: 17))
                .foregroundStyle(AppTheme.inkPrimary)
                .lineSpacing(6)
        }
    }

    // MARK: - Frame 29 · The combined preferences screen

    /// GEN-3 (Sol, Nov 4 — RULED): the three preference questions
    /// on ONE screen — "Make it yours". Each question heads the
    /// SAME native PreferenceControls control Settings uses (so
    /// the two surfaces can never drift apart, and each selection
    /// settles with the control's own selection haptic), with the
    /// shipped wording and options: Fahrenheit / Celsius in full
    /// words; Men's / Women's / No preference as direct-choice
    /// rows (W-22/Y-13 — all three, including "No preference");
    /// the 5-stop body-temperature slider with its Runs Cold /
    /// Runs Warm end labels. One Continue pushes the location
    /// step. (Supersedes the one-question-per-screen structure
    /// for these three questions only.)
    private var preferencesScreen: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(OnboardingCopy.preferencesHeadline)
                        .font(.title.weight(.bold))
                        .foregroundStyle(AppTheme.inkPrimary)

                    Text(OnboardingCopy.preferencesLede)
                        .font(.appSecondary)
                        .foregroundStyle(AppTheme.inkSecondary)
                }

                preferenceGroup(question: OnboardingCopy.preferencesQuestions[0]) {
                    TemperatureUnitPicker(unit: $temperatureUnit)
                }

                preferenceGroup(question: OnboardingCopy.preferencesQuestions[1]) {
                    // W-22: the fit choices show directly
                    // (inline grouped list), not behind a
                    // collapsed menu — see
                    // FitPreferenceInlinePicker.
                    FitPreferenceInlinePicker(preference: $genderPreference)
                }

                preferenceGroup(question: OnboardingCopy.preferencesQuestions[2]) {
                    ComfortLevelControl(comfortLevel: $comfortLevel)
                }
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

    /// One question + its control, on the combined screen.
    private func preferenceGroup<Content: View>(
        question: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(question)
                .font(.appSecondary)
                .foregroundStyle(AppTheme.inkSecondary)

            content()
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
                    useMyLocationTapped()
                } label: {
                    Text("Use My Location")
                        .font(.appButtonLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.primaryColor)
                .disabled(locationPermissionPending)

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
                .disabled(locationPermissionPending)

                // W-19: the wait lives INSIDE the designed step —
                // spinner + copy under the intact title, privacy
                // copy and button chrome. The step is never
                // replaced by a bare fallback while the permission
                // (and then the first fix) resolves.
                if locationPermissionPending {
                    HStack(spacing: 10) {
                        ProgressView()
                            .tint(AppTheme.inkPrimary)
                        Text("Finding your location…")
                            .font(.appSecondary)
                            .foregroundStyle(AppTheme.inkSecondary)
                    }
                    .padding(.top, 4)
                }

                if locationPermissionDenied {
                    Text("Location is off — set a city instead, or turn location on for RunWear in Settings.")
                        .font(.appSecondary)
                        .foregroundStyle(AppTheme.inkSecondary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(AppTheme.background.ignoresSafeArea())
        // If the user backed out while a request was outstanding
        // and returns after it resolved elsewhere, settle the
        // pending state on re-entry instead of stranding the step
        // with its doors disabled.
        .onAppear {
            guard locationPermissionPending else { return }
            switch locationService.authorizationStatus {
            case .authorizedWhenInUse, .authorizedAlways:
                locationPermissionPending = false
                beginFirstLook()
            case .denied, .restricted:
                locationPermissionPending = false
                locationPermissionDenied = true
            default:
                break
            }
        }
        // W-19: the flow advances only when the authorization
        // RESOLVES. The system prompt presents over this intact
        // step; a grant hands off to the first-look phase (whose
        // own spinner carries the fetch), a denial keeps the user
        // here with the city door working.
        .onChange(of: locationService.authorizationStatus) { _, status in
            guard locationPermissionPending else { return }
            switch status {
            case .authorizedWhenInUse, .authorizedAlways:
                locationPermissionPending = false
                beginFirstLook()
            case .denied, .restricted:
                locationPermissionPending = false
                locationPermissionDenied = true
            default:
                break
            }
        }
    }

    /// "Use My Location" (W-19): request the permission and STAY
    /// on the step. The pre-fix tap called beginFirstLook() in the
    /// same action as requestPermission(), tearing the
    /// NavigationStack (and this step's styled layout) down before
    /// the system prompt could present over it — the first-look
    /// host's notDetermined branch then rendered the step's title
    /// and two plain-text buttons with no chrome, no privacy copy
    /// and no spinner for ~3.5 s. Now: an already-granted
    /// permission advances at once; a denied one says so here;
    /// an undecided one raises the prompt over this step and the
    /// authorization change (above) advances the flow.
    private func useMyLocationTapped() {
        locationChoice = .gps
        locationPermissionDenied = false
        switch locationService.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            beginFirstLook()
        case .denied, .restricted:
            locationPermissionDenied = true
        default:
            locationPermissionPending = true
            locationService.requestPermission()
        }
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
    /// GEN-2 I2 (W-16): the reveal now belongs to the GENERATED
    /// photo's landing — startFirstLookReveal runs when the
    /// moment's presentation reaches its ready phase, not when
    /// the screen appears (the screen previously "revealed" the
    /// fork's stock photo and captioned it as the user).
    @State private var firstLookPhotoRevealed = false
    @State private var firstLookBandRevealed = false
    @State private var firstLookHapticNonce = 0
    /// GEN-2 I2 (W-16/W-18): the cell the first generation was
    /// kicked for, captured from the kick itself — frame 34 reads
    /// the server's look state for exactly this cell.
    @State private var firstLookCombo: PersonalCombination?
    /// GEN-3 (census-ios Y-1, rendering defect 1): the personal
    /// image URL whose pixels have actually loaded in the
    /// first-look card. The finished claim requires the photo to
    /// be ON SCREEN — a set URL whose image is still in flight
    /// is not on screen (the census's claim-over-empty-band
    /// step, ~0.7 s of claim and counter over an empty card).
    @State private var firstLookLoadedImageURL: String?

    /// The cell frame 34 is about: the kick's own cell, falling
    /// back to the preview's current personal cell.
    private var firstLookCell: PersonalCombination? {
        firstLookCombo ?? previewVM.currentPersonalCombination
    }

    /// Frame 34's state, resolved from the server's look state
    /// for the cell, whether the generated photo is the photo on
    /// screen, and the ONE free-looks counter Settings also reads
    /// (FirstLookPresentation / FreeLooksMeter — W-16/W-18).
    private var firstLookPresentation: FirstLookPresentation {
        FirstLookPresentation.resolve(
            lookState: firstLookCell.map { store.lookState(for: $0) } ?? .none,
            personalPhotoDisplayed: previewVM.currentPersonalHeroId != nil && previewVM.heroImageUrl != nil,
            personalImageLoaded: firstLookLoadedImageURL != nil
                && firstLookLoadedImageURL == previewVM.heroImageUrl,
            freeGenerationsUsed: store.freeGenerationsUsed,
            freeAllowance: PersonalHeroStore.freeAllowance,
            isSubscribed: store.mode == .subscribed
        )
    }

    /// Records that the card's loaded image IS the delivered
    /// personal photo (called from the card's success branches —
    /// a success phase means those pixels are on screen).
    private func recordFirstLookImageLoad() {
        guard previewVM.currentPersonalHeroId != nil,
              let url = previewVM.heroImageUrl else { return }
        firstLookLoadedImageURL = url
    }

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
                        previewContextLine: forkContextLine,
                        previewOutfitLine: outfitLine,
                        onCustomize: { showOptIn = true },
                        onStock: onComplete
                    )
                } else {
                    // "Make It Me" OFF (see MakeItMeAvailability):
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
                // GEN-3 X-13: the Current location row's CURRENT
                // badge reads the ACTUAL location state — this
                // sheet hardcoded `false` while the preview view
                // model is in GPS mode by default (its GPS
                // pipeline guards on `previewVM.isUsingGPS`), so
                // the row misreported the active selection.
                isUsingGPS: previewVM.isUsingGPS,
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
                                // W-16: frame 34 tracks THIS
                                // cell's server look state.
                                await MainActor.run { firstLookCombo = combo }
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

    /// The first look with "Make It Me" off: the same preview the
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

    /// Frame 34 — the first-look moment. The primary door is the
    /// run's clothing answer ("See My Outfit"), and the ONE soft
    /// ask is the quiet second door ("Upgrade to Premium" —
    /// GEN-3 first-look ruling, Nov 4: replaces "See Premium" on
    /// THIS surface only) — once, never a modal again. The band
    /// carries the two benefit concepts in the §9 recommended
    /// pair, verbatim.
    ///
    /// GEN-2 I2 (W-16/W-18): the moment is STATEFUL, and every
    /// line it shows is gated on FirstLookPresentation. While the
    /// personal look is generating, the working state lives ON
    /// the photo (the pool photo visibly treated, the §7 G1/G2
    /// processing copy on the photo itself — Sol's correction:
    /// not at the screen's footer), the caption does NOT claim
    /// the user is in the photo, no free-look debit is presented,
    /// and the Premium band waits. When the generated photo
    /// actually lands it crossfades in (§1.4 reveal), the caption
    /// claims it, and the meter renders from the store's ONE
    /// counter — the onboarding look counts as look 1 of 5
    /// (§8 Q5) exactly when the server has counted it, which is
    /// the same value Settings shows. A failed look shows the
    /// failure line and charges nothing. Leaving early is always
    /// possible ("See My Outfit" never waits on generation); the
    /// home screen's progress/failed surfaces carry the state
    /// from there, and no debit is ever claimed for a look that
    /// has not been delivered.
    private var firstLookMomentView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Frame 34's header stack (WS-9 fix — design frame
                // 34 and Android's FirstLookMoment carry all three):
                // the "Make It Me" script wordmark heads the moment,
                // then the heading and its weather line — the
                // frame's two-line context composition, replacing
                // the RW-14 single computed line ("{place} ·
                // {feels-like}") this surface used to carry under
                // the photo. That computed line remains only where
                // it belongs: the fork preview and the flag-off
                // ready view.
                VStack(spacing: 6) {
                    Text("Make It Me")
                        .font(.greatVibes(size: 34))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text(MakeItMeCopy.firstLookHeading(city: previewVM.simplePlace?.city))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text(MakeItMeCopy.firstLookWeatherLine(
                        feelsLike: previewVM.temperatureUnit.format(previewVM.currentFeelsLike),
                        condition: previewVM.currentCondition.rawValue,
                        itemCount: previewVM.recommendation?.allItems.count ?? 0
                    ))
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.inkSecondary)
                }

                // The photo card — state lives ON the photo
                // (firstLookPhotoCard): while the look is being
                // made the pool photo is visibly treated with the
                // processing copy over it; the generated photo
                // crossfades in when it lands.
                firstLookPhotoCard

                // Frame 34's own lines, gated on the presentation:
                // the claim and the meter exist ONLY in the ready
                // phase (the meter composed from the shared
                // counter); the failed phase states its failure
                // and charges nothing; the making phase states
                // nothing below the photo — its copy is on it.
                if let caption = firstLookPresentation.caption {
                    Text(caption)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                }

                if let meterLine = firstLookPresentation.meterLine {
                    Text(meterLine)
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.inkSecondary)
                }

                if let failureLine = firstLookPresentation.failureLine {
                    Text(failureLine)
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.inkSecondary)
                }

                // The §9 band — the recommended pair, verbatim.
                // It slides up 120 ms after the photo lands
                // (FirstLookReveal.bandStartSeconds from reveal
                // start), never with or ahead of the photo — and
                // (W-16) it renders only once the first look
                // exists: the ask follows the look.
                if firstLookPresentation.showsPremiumBand {
                VStack(alignment: .leading, spacing: 8) {
                    // WS-9 fix: the band's designed header (frame
                    // 34; Android's FIRST_LOOK_BAND_HEADER) — the
                    // eyebrow that attributes the pair below to
                    // Premium, at Android's size, tracking and pine.
                    Text(MakeItMeCopy.firstLookBandHeader)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.2)
                        .foregroundColor(AppTheme.pinePayload)
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
                }

                // GEN-3 FIRST-LOOK RULING 1 (Sol, Nov 4): the
                // generating screen is NEVER a gate. This door is
                // deliberately UNCONDITIONAL — no phase check, no
                // disabled state — so it is visible and tappable
                // at ALL times, including the whole making phase.
                // Leaving mid-generation completes onboarding;
                // generation continues in the background (the
                // store's polling owns it, not this screen), and
                // home's hero carries the state via the generating
                // pill over the fallback photo until the look
                // lands and swaps in. Waiting is the user's
                // choice, never a requirement.
                Button(action: onComplete) {
                    Text("See My Outfit")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                }

                // The quiet second door — part of the soft ask,
                // so it too waits for the ready phase (W-16).
                // Dismissing the ask IS continuing; this screen
                // never returns as a modal.
                // GEN-3 FIRST-LOOK RULING 2 (Sol, Nov 4): the
                // finished surface's CTA reads "Upgrade to
                // Premium" (replacing "See Premium") — this
                // surface only; no other surface's wording moves.
                if firstLookPresentation.showsPremiumBand {
                    Button {
                        showPremiumSheet = true
                    } label: {
                        Text("Upgrade to Premium")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(AppTheme.inkSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
        }
        // WS-10 (§1.4/§1.7) as re-aimed by W-16: the reveal runs
        // when the GENERATED photo lands (the presentation's
        // ready phase) — on appear only if the look is already
        // ready, and again from the ready photo's own load (see
        // firstLookPhotoCard). The success haptic is the moment's
        // only haptic and fires exactly once, as the photo lands.
        .onAppear {
            if firstLookPresentation.phase == .ready { startFirstLookReveal() }
        }
        .onChange(of: firstLookPresentation.phase) { _, phase in
            if phase == .ready { startFirstLookReveal() }
        }
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

    /// Frame 34's photo card (GEN-2 I2 · W-16/W-17).
    ///
    /// W-17 (this card's half): every phase renders through
    /// HeroPhoto — the home hero's top-anchored crop — so the
    /// full head is in frame. The bare center-anchored fill this
    /// card used before cropped the stock photo mid-face and the
    /// generated photo at the neck.
    ///
    /// W-16: the state lives ON the photo. While the look is
    /// making, the pool photo underneath is visibly a placeholder
    /// being worked on (blurred, dimmed, under a scrim) and the
    /// §7 G1/G2 processing copy sits on the photo itself — the
    /// photo is never presented as the user. When the generated
    /// photo lands, it replaces the treated placeholder through
    /// the §1.4 crossfade (its opacity is the reveal's), never a
    /// silent hard cut. On failure the standard photo stands
    /// untreated — it is the honest standard photo again, and the
    /// failure line below the card says what happened.
    private var firstLookPhotoCard: some View {
        let presentation = firstLookPresentation
        return GeometryReader { geo in
            ZStack {
                AsyncImage(url: (previewVM.heroImageUrl ?? previewVM.fallbackImageUrl).flatMap(URL.init(string:))) { phase in
                    switch phase {
                    case .success(let image):
                        switch presentation.phase {
                        case .ready:
                            HeroPhoto(image: image, width: geo.size.width, height: 220)
                                // §1.4 reveal: the generated
                                // photo crossfades in as it lands
                                // (it never pops, and the treated
                                // placeholder is never silently
                                // replaced underneath it).
                                .opacity(firstLookPhotoRevealed ? 1 : 0)
                                .onAppear { startFirstLookReveal() }
                        case .making:
                            HeroPhoto(image: image, width: geo.size.width, height: 220)
                                .blur(radius: 8)
                                .opacity(0.55)
                                // GEN-3 (Y-1 defect 1): if this
                                // loaded image is the personal
                                // photo, record it — the finished
                                // claim waits for exactly this.
                                .onAppear { recordFirstLookImageLoad() }
                        case .failed:
                            HeroPhoto(image: image, width: geo.size.width, height: 220)
                        }
                    default:
                        AppTheme.cardOnSheet
                    }
                }

                if presentation.phase == .making {
                    // The working state, ON the photo: a scrim
                    // over the treated placeholder, the spinner,
                    // and the processing copy where the user is
                    // looking — not at the screen's footer.
                    Color.black.opacity(0.35)
                    VStack(spacing: 10) {
                        ProgressView()
                            .tint(.white)
                            .scaleEffect(1.2)
                        if let title = presentation.photoOverlayTitle {
                            Text(title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                        }
                        if let detail = presentation.photoOverlayDetail {
                            Text(detail)
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.9))
                                .multilineTextAlignment(.center)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
        .frame(height: 220)
        .clipped()
        .cornerRadius(16)
    }

    /// The frame-34 reveal (§1.4), driven off FirstLookReveal's
    /// numbers: photo crossfade → (photo lands) ONE success
    /// haptic → +120 ms, the summary band slides up. Guarded so
    /// a re-appearance never replays it — the haptic count for
    /// the moment is FirstLookReveal.successHapticCount (1).
    /// GEN-2 I2 (W-16): callers fire it only for the ready phase
    /// — the photo being revealed is the generated one.
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

    /// W-23: the fork's caption — the context line ONLY once the
    /// place is actually known (a SimplePlace has been derived).
    /// Before the geocode lands, placeDisplayName is the view
    /// model's in-flight placeholder ("Loading..."), and the fork
    /// flashed exactly that in the caption slot; now the slot
    /// stays empty and the fork photo area's placeholder carries
    /// the wait.
    private var forkContextLine: String? {
        ForkContextLine.make(
            placeDisplayName: previewVM.placeDisplayName,
            isPlaceKnown: previewVM.simplePlace != nil,
            feelsLike: previewVM.temperatureUnit.format(previewVM.currentFeelsLike)
        )
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
        // "Make It Me" OFF (see MakeItMeAvailability): the preview
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
                // W-19: this branch used to render the step's
                // title plus two PLAIN-TEXT buttons — no chrome,
                // no privacy copy, no spinner — and it is what the
                // user actually saw for ~3.5 s, because the
                // location step advanced here in the same tap that
                // requested permission. The location step now
                // stays intact until the authorization resolves,
                // so this branch is a defensive fallback only; it
                // renders the same designed treatment (styled
                // doors, spinner + copy while a request is in
                // flight) rather than the stripped layout.
                Text("One more thing — where are you running?")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                    .multilineTextAlignment(.center)
                Button {
                    locationService.requestPermission()
                } label: {
                    Text("Use My Location")
                        .font(.appButtonLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.primaryColor)
                Button {
                    showLocationPicker = true
                } label: {
                    Text("Set a City Instead")
                        .font(.appButtonLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                .tint(AppTheme.inkPrimary)
                HStack(spacing: 10) {
                    ProgressView()
                        .tint(AppTheme.inkPrimary)
                    Text("Finding your location…")
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                }
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
