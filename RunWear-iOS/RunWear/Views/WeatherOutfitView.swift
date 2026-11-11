import SwiftUI
import StoreKit // for the \.requestReview environment action (R-15)

struct WeatherOutfitView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // RW-25 sheet numeral: ~80pt, Dynamic-Type-scaled relative to
    // .largeTitle (the RW-6.3 pattern the hero numeral used), capped
    // by the text's dynamicTypeSize limit at the call site.
    /// RW-26 "Wear I've Run": the collection model — the current
    /// session's entry drives the mini bar dock (GEN-3 session
    /// rule); the store behind it is the collection itself.
    @StateObject private var collectionModel = WearIveRunCollectionModel()
    @State private var showCollection = false
    /// RW-158 (user ruling, Nov 10, 2026): the past entry whose
    /// tap on the expanded panel's entry row opened the Share
    /// Studio sheet directly.
    @State private var wearShareEntry: WearIveRunEntry?
    /// RW-161 (device verdict, Nov 10, 2026): ONE presentation
    /// owner for every Home sheet. The states above and below
    /// remain the sheets' backings, but no backing presents
    /// unless its route owns this token — every presentation
    /// funnels through claimSheet/releaseSheet (HomeSheetArbiter),
    /// a second request queues FIFO, and the toast/failure-line
    /// layer defers while a sheet owns the screen. Build 27's
    /// failure was ten independent states on two hosts cycling
    /// behind/over each other on a swipe.
    @State private var homeSheetOwner: HomeSheetRoute?
    @State private var homeSheetQueue: [HomeSheetRoute] = []
    /// Item-sheet payloads stashed while their route is queued.
    @State private var pendingWeatherDetail: WeatherDetailType?
    @State private var pendingWearShareEntry: WearIveRunEntry?
    /// RW-26 feedback door from the rating moment (frame 49).
    @State private var showFeedbackSheet = false
    /// RW-25 scroll-handoff progress (0 = hero full-frame at rest,
    /// 1 = settled as a card under the nav bar), driven by
    /// onScrollGeometryChange on the stacked scroll views.
    @State private var heroScrollProgress: CGFloat = 0
    @EnvironmentObject var personalStore: PersonalHeroStore
    @ObservedObject var viewModel: WeatherViewModel
    @ObservedObject var locationService: LocationService
    /// RW-79: the app's lifecycle phase — Home re-reads the
    /// current cell's look state when the app comes back to the
    /// foreground (see the onChange below).
    @Environment(\.scenePhase) private var scenePhase
    let weather: WeatherData
    let recommendation: OutfitRecommendation
    let onRefresh: () -> Void
    var onUseCurrentLocation: () -> Void = {}

    // Modal state
    @State private var showSettings = false
    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var showLocationPicker = false
    @State private var selectedWeatherDetail: WeatherDetailType?

    // Animation state
    @State private var hasAppeared = false

    // Hero-flag confirmation toast (the snackbar equivalent):
    // shown briefly after the user flags the hero photo.
    @State private var showFlagThanks = false
    /// GEN-3 (WS-A(2)): the hero pill's free-look state opens the
    /// Premium paywall from the hero.
    @State private var showHeroPillPaywall = false
    /// GEN-6 (RW-61): the looks library sheet, opened from the
    /// failed cell's "See all your looks" door.
    @State private var showLooksLibrary = false
    /// RW-152 §1 (Nov 9, 2026 — supersedes the RW-135 two-surface
    /// split this comment used to describe): the Wear I've Run
    /// presence on Home is ONE overlay surface with two states —
    /// the floating MINI dock and its EXPANDED panel state,
    /// morphed in place inside WearIveRunOverlayView (the
    /// WearIveRunMiniBar.swift residence). The in-flow footer
    /// section and ALL of its host bookkeeping (footer/scroll
    /// frames, footer height, glide targets, the §3 handoff
    /// fade) are REMOVED; the scroll carries only the RW-147
    /// scroll-end clearance below the page content, exactly the
    /// mini-dock reservation. What remains below is the exit
    /// orchestration state only.
    /// §4.7/§4.8 exit orchestration: while an exit runs, the
    /// overlay renders this FROZEN snapshot of the record; the
    /// store removal fires when the exit completes.
    @State private var wearExitEntry: WearIveRunEntry?
    @State private var wearExitState: WearIveRunState?
    @State private var wearExitInProgress = false
    @State private var wearExitRemovesData = false
    @State private var wearDockExitProgress: Double = 0
    /// The last presented session entry, kept so a session end
    /// (day rollover — the entry leaves the store without a
    /// trash tap) can still run the §4.8 exit over a snapshot.
    @State private var lastWearEntry: WearIveRunEntry?
    /// RW-135 §7.5: true while any home scroll gesture (or its
    /// deceleration) is live — fed by the layouts'
    /// onScrollPhaseChange; the dock defers a mid-gesture
    /// narration cross-fade until the frame after it ends.
    @State private var isScrollGestureActive = false
    /// Fix wave 2026-11-07 (RW-133): the full-screen hero viewer.
    @State private var showHeroViewer = false
    /// RW-144: the hero photo's measured zone luminances — the
    /// contrast guarantee's input (the scrims and chrome fills
    /// scale from these, never from a fixed constant).
    @StateObject private var heroLuminance = HeroLuminanceModel()

    // R-15 rating moment: this view exists only when a recommendation
    // is on screen, so appearing here IS the proven-value signal. The
    // tracker counts distinct view days; the ask comes after the 3rd,
    // and never twice a season.
    @State private var showRatingPrompt = false
    @Environment(\.requestReview) private var requestReview
    private let ratingTracker = RatingPromptTracker()

    var body: some View {
        // Home lives in a NavigationStack (design record §5.2):
        // the location is the navigation title, the date its
        // subtitle, and Settings / Shop are toolbar items — the
        // hero's floating glass pills are gone, and the hand-rolled
        // overlay "modals" are now real sheet presentations.
        NavigationStack {
            ZStack {
                // RW-25: dynamic theme background
                AppTheme.background
                    .ignoresSafeArea()

                layoutContent

                // RW-6.2: a failed refresh with content on screen is
                // a banner, not a takeover — the outfit stays readable
                // underneath, and .refreshable's own spinner is the
                // only progress affordance (the black scrim that used
                // to accompany every refresh is gone). This view only
                // exists when content does, so an errorMessage here is
                // by construction the with-content case.
                if let refreshError = viewModel.errorMessage {
                    VStack {
                        RefreshErrorBanner(
                            message: refreshError,
                            onRetry: onRefresh,
                            onDismiss: { viewModel.errorMessage = nil }
                        )
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                // Hero-flag confirmation toast, bottom-center of
                // the run screen (Android's snackbar placement).
                // RW-161: the toast layer DEFERS while a sheet
                // owns the screen — build 27 rendered a toast
                // sandwiched between stacked sheets; a transient
                // line never renders over or between sheets.
                if showFlagThanks && !anyHomeSheetPresented {
                    VStack {
                        Spacer()
                        Text("Thanks — we'll review this photo")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            // GEN-5 (cream map row 51): the
                            // transient capsules sit on the
                            // theme's Warm Charcoal, not pure
                            // black.
                            .background(Capsule().fill(AppTheme.darkBackground.opacity(0.92)))
                            .padding(.bottom, 32)
                    }
                    .ignoresSafeArea()
                    .transition(.opacity)
                }
            }
            // RW-152 §1: the Wear I've Run overlay is a true
            // OVERLAY on the root ZStack — alignment bottom,
            // inset 12 pt left/right, 14 pt above the bottom
            // edge — ONE surface whose two states (mini dock,
            // expanded panel) morph in place inside it. No
            // session entry → no surface, ever (§7.1): the
            // overlay renders nothing before the session's
            // first recorded entry. The GeometryReader exists
            // only to hand the overlay its viewport height (the
            // expanded panel's cap input, §2).
            .overlay(alignment: .bottom) {
                GeometryReader { proxy in
                    VStack {
                        Spacer()
                        miniBarDock(viewportHeight: proxy.size.height)
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 14)
                }
            }
            // §4.8 session end: the session entry leaving the
            // store without a trash tap (day rollover) runs the
            // same exit over the last presented snapshot, minus
            // any haptic. Trash taps run their own exit through
            // beginWearExit and set wearExitInProgress first, so
            // this hook stands down for them.
            .onChange(of: collectionModel.sessionEntry()?.id) { _, newID in
                if let entry = collectionModel.sessionEntry() {
                    lastWearEntry = entry
                } else if newID == nil, !wearExitInProgress,
                          let snapshot = lastWearEntry {
                    beginWearExit(snapshot: snapshot, removesData: false)
                }
            }
            .onChange(of: showFlagThanks) { _, visible in
                if visible {
                    Task {
                        try? await Task.sleep(nanoseconds: 2_500_000_000)
                        withAnimation { showFlagThanks = false }
                    }
                }
            }
            // RW-6.10's flag half of the haptics vocabulary: a light
            // impact when a hero-photo flag is confirmed — the merged
            // tree is the first with both the flag surface and the
            // vocabulary, so the hook lands here on the confirmation
            // state itself.
            .sensoryFeedback(.impact(weight: .light), trigger: showFlagThanks)
            // RW-144: the navigation bar is hidden on Home — the
            // v8 hero carries the chrome itself (place pill,
            // wordmark, gear circle over the photograph's top
            // scrim). The title stays set for accessibility and
            // state restoration; the location entry is the hero's
            // place pill (one tap straight to the picker — the
            // Y-16 rule, preserved at its new seat).
            .toolbar(.hidden, for: .navigationBar)
            // RW-146: the title names the DISPLAYED place — the place
            // the committed weather belongs to.
            .navigationTitle(viewModel.displayedPlaceName)
            // The design record's date-as-subtitle needs
            // navigationSubtitle, which the current iOS SDK marks
            // unavailable on iPhone (it is a newer-platform API
            // there) — CI 40394130506 is the evidence. The date
            // stays visible on the hero's date pill, the title
            // menu, and the weather detail's context until the
            // build SDK offers the subtitle on iOS.
            .navigationBarTitleDisplayMode(.inline)
            // RW-144 supersedes the opaque-bar composition: with
            // the bar hidden there is no chrome band for scrolled
            // content to ghost through (the 2026-10-31 finding's
            // mechanism is removed with the bar itself), and the
            // hero's own top scrim carries its chrome. The Shop
            // entry the toolbar carried moves to the outfit
            // header (see outfitSection) — no surface is dropped.
            .sheet(isPresented: ownedBinding(.settings, $showSettings)) {
                SettingsModal(
                    isPresented: ownedBinding(.settings, $showSettings),
                    temperatureUnit: $viewModel.temperatureUnit,
                    genderPreference: $viewModel.genderPreference,
                    comfortLevel: $viewModel.comfortLevel,
                    currentCombination: { viewModel.currentPersonalCombination }
                )
                // WS-5 (§1.3 Settings): full height only — no
                // detents, no partial rest; the Form inside is
                // the sheet's one scroll consumer.
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: ownedBinding(.datePicker, $showDatePicker)) {
                DatePickerSheet(
                    selectedDate: $viewModel.selectedDate,
                    isPresented: ownedBinding(.datePicker, $showDatePicker)
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .onChange(of: viewModel.selectedDate) { _, newDate in
                    viewModel.updateSelectedTime(date: newDate, hour: viewModel.selectedHour)
                }
            }
            .sheet(isPresented: ownedBinding(.timePicker, $showTimePicker)) {
                // GEN-4: the chart sheet works on a DRAFT — the
                // view model's selection is written only by
                // Confirm, through applyTimePickerCommit. (The
                // pre-GEN-4 form bound selectedHour live, and an
                // onChange re-solved home behind the sheet on
                // every tile tap; both are gone with the tiles.)
                TimePickerSheet(
                    chart: viewModel.dayChart,
                    dateLabel: navDateString,
                    placeName: viewModel.displayedPlaceName,
                    unit: viewModel.temperatureUnit,
                    initialHour: timePickerInitialHour,
                    openedInNowState: viewModel.isNowSelection,
                    nowHour: viewModel.dayChartNowHour,
                    isPresented: ownedBinding(.timePicker, $showTimePicker),
                    onConfirm: { viewModel.applyTimePickerCommit($0) },
                    // GEN-6 (RW-66): the day picker's door inside
                    // the sheet (its context line's date segment).
                    // Confirm writes the view model's selectedDate
                    // and dismisses this sheet too, landing home
                    // on the new day.
                    // RW-92: the re-solve is fired HERE, explicitly.
                    // The gen-6 form relied on the .onChange inside
                    // the HOME-hosted date sheet's content — a
                    // handler that exists only while that sheet is
                    // presented, so a date confirmed from THIS
                    // nested sheet wrote selectedDate (the header
                    // recomposed to the picked day) but never
                    // re-solved: home kept presenting Now's figures
                    // under the picked day's name, with no
                    // out-of-forecast notice (Sol's walkthrough,
                    // frame f_104). The nested confirm now runs the
                    // same updateSelectedTime the home path's
                    // handler runs — exactly once, keeping the
                    // selected hour.
                    dateSelection: $viewModel.selectedDate,
                    onDateConfirmed: {
                        dismissSheet(.timePicker)
                        viewModel.updateSelectedTime(
                            date: viewModel.selectedDate,
                            hour: viewModel.selectedHour
                        )
                    },
                    chartForDate: { viewModel.dayChart(for: $0) },
                    onSessionConfirm: { date, commit in
                        // Fix wave 2026-11-07 (RW-131, review
                        // §5.5): the unified session's ONE
                        // commit — (working date, hour) applied
                        // once through the view model's apply
                        // path. The day sheet's own confirm
                        // never reaches here: under the session
                        // it mutates the working day in place
                        // and the time sheet stays open until
                        // its own Confirm/Cancel. (The legacy
                        // onDateConfirmed above is now only the
                        // fallback for hosts without a chart
                        // provider — this host provides one.)
                        viewModel.applyPickerCommit(date: date, commit: commit)
                    },
                    initialDate: viewModel.selectedDate
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
            // Item-based, not isPresented + if-let: in the old
            // two-state form the sheet could evaluate its content
            // before the selection landed and present an empty
            // (white) sheet -- the blank UV overlay of 2026-10-29.
            // With .sheet(item:) the content exists exactly when
            // a detail does.
            .sheet(item: weatherDetailSheetItem) { detail in
                WeatherDetailSheet(
                    detailType: detail,
                    allDetails: allWeatherDetails,
                    contextLines: weatherContextLines,
                    isPresented: weatherDetailPresented
                )
                // WS-5 (§1.3): the detail content scrolls (its
                // own ScrollView), so the sheet rests at .large
                // only — never at a partial detent over scrolling
                // content.
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: ownedBinding(.locationPicker, $showLocationPicker)) {
                LocationPickerSheet(
                    isPresented: ownedBinding(.locationPicker, $showLocationPicker),
                    // RW-146: the DISPLAYED truth (see the view
                    // model's displayedPlaceName/displayedSourceIsGPS).
                    isUsingGPS: viewModel.displayedSourceIsGPS,
                    currentPlaceName: viewModel.displayedSourceIsGPS ? nil : viewModel.displayedPlaceName,
                    onUseCurrentLocation: {
                        onUseCurrentLocation()
                    },
                    onSelectLocation: { coordinate, name in
                        viewModel.setManualLocation(coordinate: coordinate, name: name)
                    }
                )
                // Y-16: the COMPLETE picker (search field, current
                // location, results) at first presentation — the
                // one-tap fix was removing the intermediate title
                // menu, so the first tap opens this sheet directly.
                // Detents follow the settled sheet discipline
                // (§1.3): Location is one of the three sheets that
                // keep a medium detent (SheetDisciplineTests).
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5).delay(0.2)) {
                hasAppeared = true
            }
            ratingTracker.recordRecommendationViewed()
            if ratingTracker.shouldPrompt() {
                showRatingPrompt = true
            }
            recordTodaysEntry()
            // RW-144: measure the served photo's zone luminances
            // for the hero scrims (the contrast guarantee).
            heroLuminance.update(imageURLString: viewModel.heroImageUrl ?? viewModel.fallbackImageUrl)
            // RW-79: returning to Home re-reads the current
            // cell's look state — a generation that finished (or
            // failed) while the user was anywhere else is the
            // server's word now, not whatever Home last rendered.
            viewModel.revalidatePersonalLookState()
            // Fix wave 2026-11-07 (review §5.4): landing runs
            // the store's reconciliation pass as a SET, so the
            // bank, counter, entitlement and library all hold
            // the same vintage as the hero.
            Task {
                await personalStore.reconcilePersonalLayer(
                    currentCombination: viewModel.currentPersonalCombination
                )
            }
        }
        // Fix wave 2026-11-07 (RW-125): the Now tick — a
        // standing Now view crosses the hour boundary as Now.
        // The tick itself never refetches; maintenance refetches
        // only when the location-frame hour/day actually rolled
        // (review §5.2 row 5, risk N-4's discipline).
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                if Task.isCancelled { break }
                viewModel.maintainNowIfNeeded()
            }
        }
        // RW-79: the foreground re-read. A generation running
        // when the app was backgrounded keeps being watched by
        // the store's poll only while its schedule lasts (the
        // schedule is consumed in real time, suspension
        // included); when the app comes back, one fresh read
        // folds the terminal state — or re-arms the poll when
        // the cell is still MAKING.
        // Fix wave 2026-11-07: the foreground event is now ONE
        // pass in the review's order (§5.2 row 4 + risk R-4):
        // Now re-anchor (at the refetch's commit) → personal
        // revalidation → the store's reconciliation as a set.
        // A picked selection is NOT re-anchored (Sol's ruling:
        // a return restores Now only if Now was active).
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                viewModel.handleForeground()
                viewModel.revalidatePersonalLookState()
                Task {
                    await personalStore.reconcilePersonalLayer(
                        currentCombination: viewModel.currentPersonalCombination
                    )
                }
            }
        }
        // A re-settled recommendation (refresh, hour change back to
        // now, outfit change) updates the session's entry in place
        // — the saved entry is the session's last settled version.
        .onChange(of: recommendation.allItems.map(\.id)) { _, _ in
            recordTodaysEntry()
        }
        // W-15 (the creation half): this view first appears
        // mid-fetch — weather + recommendation publish BEFORE
        // the reverse geocode lands — so the onAppear recording
        // is always offered while the place is still the
        // placeholder label, and the guard in recordTodaysEntry
        // rightly refuses it. The recording MUST be re-offered
        // when the real place resolves: this change is that
        // re-offer, the exact mirror of Android's recording
        // effect keyed on (outfit, weather, locationName,
        // heroImageUrl). The hero landing is likewise a silent
        // re-offer (the entry's thumbnail settles to the served
        // hero; the classifier keeps it cue-free).
        .onChange(of: viewModel.placeDisplayName) { _, _ in
            recordTodaysEntry()
        }
        .onChange(of: viewModel.fallbackImageUrl) { _, _ in
            heroLuminance.update(imageURLString: viewModel.heroImageUrl ?? viewModel.fallbackImageUrl)
        }
        .onChange(of: viewModel.heroImageUrl) { _, _ in
            heroLuminance.update(imageURLString: viewModel.heroImageUrl ?? viewModel.fallbackImageUrl)
            recordTodaysEntry()
        }
        // And when a fetch's weather settles (the snapshot's
        // feels-like is the entry's headline number): a manual
        // place switch names the new place BEFORE its weather
        // lands, so the place trigger alone could file the old
        // place's numbers under the new name. This trigger
        // re-offers the recording with the settled numbers —
        // a weather-only settle, which the collection model
        // records silently and never cues on.
        .onChange(of: viewModel.selectedWeatherSnapshot?.feelsLikeFahrenheit) { _, _ in
            recordTodaysEntry()
        }
        // GEN-6 (RW-42 / DA-09): the fetch-completion re-offer.
        // While a fetch is in flight the recording gate refuses
        // every trigger (a place can be named before its weather
        // lands — see recordTodaysEntry); when the fetch lands,
        // THIS trigger records the one solved selection — place,
        // weather, kit, and hero from the same fetch — so a
        // narrated update and the card it announces are a single
        // settle, never a stale intermediate.
        .onChange(of: viewModel.isLoading) { _, isLoading in
            if !isLoading {
                recordTodaysEntry()
            }
        }
        // RW-112 / RW-116 (successor wave): the personal-landing
        // re-offer. The entry's hero reference is read from the
        // photo on screen at settle time, and a personal look
        // commonly lands AFTER the settle that created the entry
        // (a generation the user waited on, a retry that
        // delivered) — the pre-fix trigger set re-offered the
        // recording on the hero URL's change, but a landing that
        // re-published an already-standing URL, or a settle whose
        // guards were momentarily closed, left the entry
        // thumbnailing the stock serve for the rest of the
        // session (the "Miami, FL · Today" row) and the
        // collection missing the session's latest look. A
        // personal application is the precise signal: the view
        // model's personal-hero id is set exactly when a personal
        // hero applies (resolution, swap-in, revalidation) and
        // cleared when it leaves, and the store bumps its landed
        // version on every poll landing — either one re-offers
        // the recording, whose own guards (Now, today, not
        // loading, an eligible place) keep it honest, and whose
        // classifier records a hero-only change silently.
        .onChange(of: viewModel.currentPersonalHeroId) { _, _ in
            recordTodaysEntry()
        }
        .onChange(of: personalStore.landedHeroVersion) { _, _ in
            recordTodaysEntry()
        }
        .sheet(isPresented: ownedBinding(.collection, $showCollection)) {
            NavigationStack {
                WearIveRunCollectionView(
                    model: collectionModel,
                    formatTemperature: { viewModel.temperatureUnit.formatFromFahrenheit($0) },
                    genderPreference: viewModel.genderPreference
                )
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        // RW-158: the Share Studio sheet for a past entry,
        // opened directly from the expanded panel's entry row.
        // Opening is viewing — the Studio's Share button is
        // the only path to the system share sheet.
        .sheet(item: wearShareSheetItem) { entry in
            WearIveRunShareStudioHost(
                entry: entry,
                formatTemperature: { viewModel.temperatureUnit.formatFromFahrenheit($0) },
                genderPreference: viewModel.genderPreference,
                onHeroRecovered: { collectionModel.healHeroReference(entryID: $0.id, durableReference: $1) }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: ownedBinding(.feedback, $showFeedbackSheet)) {
            // WS-5 (§1.3): named detent — the feedback Form
            // scrolls, so .large only.
            FeedbackSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: ownedBinding(.heroPaywall, $showHeroPillPaywall), onDismiss: {
            // RW-79: the paywall stood over Home while a
            // generation ran — dismissing it re-reads the cell's
            // look state, so the hero the user comes back to
            // carries the server's current word (landed hero
            // swapped in, or the settled failed line), not the
            // MAKING presentation the paywall covered.
            viewModel.revalidatePersonalLookState()
        }) {
            // GEN-3 (WS-A(2)): the hero pill's "Get unlimited" door
            // — the same Premium paywall the upsell card and the
            // first-look band open, at the same named detent.
            MakeItMeSubscriptionSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: ownedBinding(.looksLibrary, $showLooksLibrary)) {
            // GEN-6 (RW-61): the looks library, one tap from the
            // failed cell's home line.
            NavigationStack {
                YourLooksLibraryView()
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        // Fix wave 2026-11-07 (RW-133): the full-screen hero
        // viewer — the stored photograph fitted whole (never
        // cropped further), one quiet caption line naming the
        // outfit's pieces, tap-again or swipe-down dismisses,
        // pinch zooms.
        .fullScreenCover(isPresented: ownedBinding(.heroViewer, $showHeroViewer)) {
            HeroPhotoViewer(
                imageURL: viewModel.heroImageUrl ?? viewModel.fallbackImageUrl,
                caption: recommendation.allItems.map(\.name).joined(separator: " · ")
            )
        }
        // Neutral THREE-door rating moment (frame 49): feedback
        // first, then the rating door, then not-now. Any door
        // closes it for the season; the rating door hands off to
        // the system review sheet.
        .alert("Enjoying RunWear?", isPresented: $showRatingPrompt) {
            Button("Send feedback") {
                ratingTracker.recordPromptShown()
                requestSheet(.feedback)
            }
            Button("Rate RunWear") {
                ratingTracker.recordPromptShown()
                requestReview()
            }
            Button("Not now", role: .cancel) {
                ratingTracker.recordPromptShown()
            }
        } message: {
            Text("A quick rating helps other runners find their outfit.")
        }
    }

    // MARK: - Share (WS-6 / T-4 ruling)

    // There is NO home-level share. The RW-6.5 toolbar ShareLink
    // that sent the raw hero photo + text from here was removed by
    // the T-4 ruling (Sol, Nov 3, 2026 — register D-15's iOS half):
    // the design has no home share on either platform, and sharing
    // lives in the collection, whose Poster / Kit Card share is
    // the designed artifact path (WearIveRunCollectionView).
    // GEN-6 (DA-23): the view model's consumerless shareText
    // computation is deleted with this comment's predecessor.

    // MARK: - Layouts (design record §5.2, frame f5)

    /// Regular landscape splits across the division region: hero
    /// pane leading, outfit column trailing
    /// with the full-weather-detail entry pinned at its foot.
    /// Regular portrait is the banner + two-column outfit grid.
    /// Anything else is the original single column, width-capped
    /// at medium widths.
    @ViewBuilder private var layoutContent: some View {
        GeometryReader { geo in
            if horizontalSizeClass == .regular && geo.size.width > geo.size.height {
                DuoSplitArrangement {
                    ScrollView {
                        VStack(spacing: 0) {
                            // Ruling 2 (Nov 9, 2026): the Duo
                            // hero fills its pane — the
                            // photograph stands edge to edge in
                            // its dedicated column (it stood at
                            // 0.62 of the pane height in RW-144,
                            // over dead theme background). The
                            // Make It Me straddle state rides
                            // the pane's foot, fully on the pane
                            // (no boundary to straddle), seated
                            // by the same HeroVerticalLayout
                            // that seats the weather line above
                            // it (pillBottomOffset −12).
                            heroSection(heightMode: .fill, pillBottomOffset: -HeroVerticalLayout.duoPillBottomInset)
                                .frame(height: geo.size.height)
                                .overlay(alignment: .bottom) {
                                    if heroPillState != .hidden {
                                        HeroPillView(
                                            pillState: heroPillState,
                                            seat: .straddle,
                                            onPillGetUnlimited: { requestSheet(.heroPaywall) }
                                        )
                                        .padding(.bottom, HeroVerticalLayout.duoPillBottomInset)
                                    }
                                }
                        }
                    }
                    .onScrollPhaseChange { _, phase in
                        isScrollGestureActive = phase != .idle
                    }
                } secondary: {
                    VStack(spacing: 0) {
                        ScrollView {
                            VStack(spacing: 0) {
                                // Ruling 2 (Nov 9, 2026): the
                                // column carries the same order
                                // as the stacked sheet minus the
                                // elements that moved onto the
                                // hero pane — the weather line
                                // and the Make It Me pill ride
                                // the pane now (the pill at its
                                // foot), so the column opens
                                // with the band, then the outfit,
                                // the glance band, and the
                                // detailed weather block.
                                // RW-101 (Sol, Nov 6, 2026): the
                                // elevated band seat, Duo layout —
                                // directly under the hero, above
                                // the outfit, as in the stacked
                                // sheet.
                                homeBandSection
                                    .padding(.top, 20)
                                if viewModel.selectedTimeOutsideForecast {
                                    outsideForecastNotice
                                        .padding(.top, 16)
                                }
                                outfitSection
                                    .padding(.top, 24)
                                dayChartBand
                                    .padding(.top, 24)
                                detailedWeatherBlock
                                    .padding(.top, 24)
                                proTipSection
                                    .padding(.top, 24)
                                personalSection
                                    .padding(.top, 24)
                                // RW-152 §1: the expanded form
                                // no longer sits in this column's
                                // flow — it is the overlay dock's
                                // expanded state (miniBarDock).
                            }
                            // RW-147: the scroll-end clearance
                            // (DockResidence) — the column's
                            // content can rest fully clear of the
                            // dock's rest band.
                            .padding(.bottom, DockResidence.scrollEndClearance)
                        }
                        .onScrollPhaseChange { _, phase in
                            isScrollGestureActive = phase != .idle
                        }
                        .refreshable { onRefresh() }
                        fullWeatherEntry
                    }
                }
            } else if horizontalSizeClass == .regular {
                ScrollView {
                    VStack(spacing: 0) {
                        heroWithScrollHandoff(heroHeight: HeroHeightPolicy.stackedHeight(
                            viewportHeight: geo.size.height))
                        sheetContainer {
                            outfitGridSection
                                .padding(.top, 24)
                        }
                    }
                }
                .onScrollPhaseChange { _, phase in
                    isScrollGestureActive = phase != .idle
                }
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    max(0, geometry.contentOffset.y + geometry.contentInsets.top)
                } action: { _, offset in
                    updateHeroScrollProgress(offset: offset)
                }
                .refreshable { onRefresh() }
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        heroWithScrollHandoff(heroHeight: HeroHeightPolicy.stackedHeight(
                            viewportHeight: geo.size.height))
                        sheetContainer {
                            outfitSection
                                .padding(.top, 24)
                        }
                    }
                    .frame(maxWidth: geo.size.width >= 600 ? 640 : .infinity)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .onScrollPhaseChange { _, phase in
                    isScrollGestureActive = phase != .idle
                }
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    max(0, geometry.contentOffset.y + geometry.contentInsets.top)
                } action: { _, offset in
                    updateHeroScrollProgress(offset: offset)
                }
                .refreshable { onRefresh() }
            }
        }
    }

    // MARK: - RW-25 Sheet

    /// The hero with the native scroll handoff: as it scrolls up the
    /// photo scales toward 0.94, clips to 24pt rounded corners and
    /// gains a 16pt horizontal inset, settling as a card under the
    /// nav bar. Driven by onScrollGeometryChange state: the first
    /// attempt used .scrollTransition(.interactive), whose content
    /// is a VisualEffect in the current SDK — it has no clipShape /
    /// padding members (CI run 41527045612 is the evidence), so the
    /// spec's fallback drives the same end state from scroll
    /// geometry instead. Reduce Motion removes the transform
    /// entirely (the state still updates; nothing animates/scales).
    private func heroWithScrollHandoff(heroHeight: CGFloat) -> some View {
        let progress = reduceMotion ? 0 : heroScrollProgress
        return heroSection(
            chromeOpacity: HomeHandoffGeometry.chromeOpacity(progress: progress),
            chromeInteractive: HomeHandoffGeometry.chromeIsInteractive(progress: progress),
            explicitHeight: heroHeight
        )
            // WS-1 (§1.1): the photo-zone chrome fades 1 → 0
            // across the FIRST 40% of the handoff and is
            // non-interactive from the moment the fade begins —
            // it never slides, and it is fully present again at
            // rest. RW-144: the chrome that fades is now the
            // whole v8 hero chrome (top row, chip, feels-like
            // block), carried inside heroSection and driven by
            // the two parameters above.
            .scaleEffect(HomeHandoffGeometry.scale(progress: progress))
            .clipShape(RoundedRectangle(cornerRadius: HomeHandoffGeometry.cornerRadius(progress: progress)))
            .padding(.horizontal, HomeHandoffGeometry.horizontalInset(progress: progress))
            // Fix wave 2026-11-07 (RW-124): the handoff transform
            // is a DIRECT function of the scroll offset (§1.1) —
            // it must never be animated by an implicit
            // transaction. Diagnosis (against the RW-135
            // structure first, per the item): the doubling/
            // ghosting concentrated at the detached dock's
            // boundaries — the dock re-composed over scrolling
            // content on every scroll frame while its own
            // presentation animated — and the hero transform
            // could pick up the surrounding transaction's
            // animation, double-drawing mid-handoff. RW-135
            // Direction A restores the dock as a small inset
            // floating bar (its boundary is short and its morph
            // is transform/opacity only, §7.5); this pin stands
            // unchanged so the handoff itself draws once.
            .animation(nil, value: heroScrollProgress)
    }

    /// Scroll-offset -> handoff progress: full transition over the
    /// first 220pt of scroll (HomeHandoffGeometry.handoffRange).
    /// Scroll-coupled 1:1 per §1.1 — a direct function of the
    /// offset, no spring and no lag is applied to it.
    private func updateHeroScrollProgress(offset: CGFloat) {
        heroScrollProgress = HomeHandoffGeometry.progress(forScrollOffset: offset)
    }

    /// The sheet surface for the stacked layouts: overlaps the
    /// photo's foot by 28pt, top corners rounded 28, filled with the
    /// theme sheet color, above the hero — and continuous from the
    /// header down through the outfit and personal sections.
    private func sheetContainer<Content: View>(@ViewBuilder outfit: () -> Content) -> some View {
        VStack(spacing: 0) {
            // Ruling 2 (Nov 9, 2026) sheet order: the boxless
            // weather line — the RW-144 sheet's opening element
            // — moved onto the hero with the rest of the key
            // information, and its hairline went with it. The
            // sheet now opens with the straddle reserve, then
            // the band, then the outfit. The reserve is the
            // straddle pill's clearance zone: while a pill
            // renders, the sheet's first content starts below
            // the pill's dip with a 12 pt gap (the guarantee
            // HeroVerticalLayout composes); without a pill it
            // is the standard head space.
            Color.clear
                .frame(height: heroPillState != .hidden
                       ? HeroVerticalLayout.sheetTopReserveWithPill
                       : HeroVerticalLayout.sheetTopReserveNoPill)
            // RW-101 (Sol, Nov 6, 2026): the "Making your looks"
            // band — wording unchanged; it seats directly
            // under the hero now, the sheet's first band, so
            // a Premium subscriber knows what's going on.
            homeBandSection
                .padding(.top, 16)
            if viewModel.selectedTimeOutsideForecast {
                outsideForecastNotice
                    .padding(.top, 16)
            }
            outfit()
            // GEN-4: the glance band sits below the outfit list.
            dayChartBand
                .padding(.top, 24)
            // RW-144 (Nov 4, 2026 ruling carried into the
            // redesign): the COMPLETE detailed weather block
            // follows the glance band — every metric describing
            // the hour the chart marker is on.
            detailedWeatherBlock
                .padding(.top, 24)
            // GEN-5 (RW-28, C-3 settlement): the Pro Tip sits
            // BELOW the hourly band — the bottom of the normal
            // elements; only Wear I've Run (the pinned dock and
            // the personal section's content) sits below it.
            proTipSection
                .padding(.top, 24)
            personalSection
                .padding(.top, 24)
            // RW-152 §1: the EXPANDED form's seat is GONE from
            // the flow — the expanded state lives in the overlay
            // dock (miniBarDock), summoned in place. The page
            // now ends with the personal section plus the
            // scroll-end clearance below.
            // RW-147: the scroll-end clearance (DockResidence) —
            // present exactly while a session entry (and so the
            // dock) exists, so at scroll end the page's end rests
            // fully clear above the dock's rest band. The value
            // is unchanged: exactly the mini-dock reservation.
            if collectionModel.sessionEntry() != nil || wearExitInProgress {
                Color.clear
                    .frame(height: DockResidence.scrollEndClearance)
            }
        }
        .background {
            UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(
                topLeading: HomeHandoffGeometry.sheetCornerRadius,
                topTrailing: HomeHandoffGeometry.sheetCornerRadius
            ))
                .fill(AppTheme.sheet)
        }
        // WS-1 (§1.1): the −28 overlap is a constant of the
        // composition — layout geometry, not a moving part — so
        // it is the same at rest and at every handoff position.
        .padding(.top, -HomeHandoffGeometry.sheetOverlap)
        .zIndex(1)
        // RW-144: the Make It Me pill STRADDLES this container's
        // top edge (the hero/sheet boundary in the v8 frames) —
        // seated on the boundary per the frames (see
        // sheetStraddlePill). Only its seat changed; the state
        // is the same heroPillState and the rendering is
        // HeroPillView's settled table.
        .overlay(alignment: .top) {
            sheetStraddlePill
        }
    }

    /// The straddle-seat Make It Me pill: rendered only while
    /// the settled pill state is not hidden (a scaffold serve
    /// stays silent — HeroPill.resolve returns .hidden).
    ///
    /// Seat (Ruling 2, Nov 9, 2026 — replaces the RW-144
    /// frame-A seat): the pill's BOTTOM sits 14 pt below the
    /// hero's foot line (HeroVerticalLayout.straddleDip), in
    /// the sheet's reserved top space, whatever the pill
    /// state's height — the offset is a function of the
    /// state's planning height, computed by the same layout
    /// that seats the weather line above the pill on the hero.
    /// The RW-144 seat anchored the pill's TOP (−9 from the
    /// sheet's edge) at a fixed 44 pt height assumption, so
    /// the two-line generating pill grew DOWNWARD over the
    /// sheet's opening weather line — the build-33 device
    /// defect (wind value cut, UV figure half-covered). A
    /// bottom-anchored seat makes that class impossible: a
    /// taller pill grows UP, into the hero space the layout
    /// reserved for it.
    @ViewBuilder private var sheetStraddlePill: some View {
        if heroPillState != .hidden {
            HeroPillView(
                pillState: heroPillState,
                seat: .straddle,
                onPillGetUnlimited: { requestSheet(.heroPaywall) }
            )
            .offset(y: HeroVerticalLayout.pillOffsetFromSheetTop(
                pillHeight: currentPillHeight))
        }
    }

    /// RW-144: the complete detailed weather block below the
    /// glance band — the settled metric set for the hour the
    /// chart marker is on (allWeatherDetails), each row opening
    /// its existing detail sheet.
    private var detailedWeatherBlock: some View {
        DetailedWeatherBlockView(
            details: allWeatherDetails,
            onSelect: { requestWeatherDetail($0) }
        )
    }

    // RW-144: the former sheet header (feels-like label +
    // location line + numeral row + Actual line) is REMOVED —
    // its content moved to the hero (feels-like block, place
    // pill, date/time chip) and its weather figures became the
    // sheet's opening boxless line. Ruling 2 (Nov 9, 2026):
    // that boxless line has itself moved onto the hero (see
    // heroChrome) — the sheet now opens with the band/outfit.
    // HomeHeroNumeralRow remains in the tree as the RW-67
    // render-proven numeral composition; Home no longer
    // composes it.

    /// GEN-4: the hero's right-justified weather figures — the
    /// strict two-column grid beside the feels-like numeral
    /// (mock §1, right-justified variant): labels WIND / RAIN /
    /// HUMIDITY / UV in one fixed column, values flush right
    /// (mph / % / % / "N of 11"), even row spacing. The figures
    /// describe the selected moment, from the same snapshot the
    /// retired pills row read.
    /// The Choose-a-Time sheet's default draft hour: the
    /// currently selected hour — except in the NOW state on the
    /// location's today, where the draft starts on the
    /// location's current hour (the NOW marker's hour).
    private var timePickerInitialHour: Int {
        if viewModel.isNowSelection, let nowHour = viewModel.dayChartNowHour {
            return nowHour
        }
        return viewModel.selectedHour
    }

    /// GEN-4: the home glance band — the day's feels-like curve
    /// over its prescription-bracket bands, below the outfit
    /// list in every home layout. Absent when the selected day
    /// carries no forecast hours.
    @ViewBuilder private var dayChartBand: some View {
        if let chart = viewModel.dayChart {
            DayChartBandView(
                model: chart,
                selectedHour: viewModel.selectedHour,
                nowHour: viewModel.dayChartNowHour,
                unit: viewModel.temperatureUnit,
                onChange: { requestSheet(.timePicker) }
            )
            .padding(.horizontal, 16)
        }
    }

    /// "Now" when viewing now (sentence case — the location line
    /// is a sentence-case piece, notes §7.2); otherwise the same
    /// date + hour strings the hero's pills used.
    /// GEN-5 (RW-28 item 12): the changed-state moment in the
    /// bare simple form — "9:00 PM" on the viewed day, a simple
    /// day word + the hour beyond it; never the gen-4 build's
    /// "Today, 9:00 PM" stamped form.
    private var sheetMoment: String {
        if isViewingNow { return "Now" }
        let calendar = Calendar.current
        if calendar.isDateInToday(viewModel.selectedDate) {
            return heroHourString
        }
        if calendar.isDateInTomorrow(viewModel.selectedDate) {
            return "Tomorrow \(heroHourString)"
        }
        return "\(navDateString) \(heroHourString)"
    }

    /// GEN-5 (RW-28, C-7): the entry's moment as a simple human
    /// reading for the Wear I've Run narration — "Now" at now,
    /// a clock reading ("9 p.m.") for a picked hour. Never a
    /// stamp.
    ///
    /// GEN-5 (Addendum 8): the reading is CONSTRUCTED as the
    /// structural MomentReading.
    ///
    /// RW-150 (Nov 9, 2026): a picked hour now records the
    /// CLOCK reading on EVERY day, not just today. The pre-fix
    /// construction stamped future-day picks as dayparts
    /// ("Tomorrow afternoon" for a 2:00 PM pick) — the exact
    /// picked time never reached the entry, so no narration
    /// built from the entry (§7.0: narration is a pure function
    /// of the record) could ever name it, and RW-98's rule —
    /// a time/date change names the date and time in full —
    /// was unmeetable. The entry's dayStart carries the day;
    /// the reading now carries the time. Daypart readings
    /// survive only as legacy decodes (entries recorded by
    /// earlier builds), and every consumer still renders them.
    private var entryMomentReading: MomentReading {
        if isViewingNow { return .now }
        let hour = viewModel.selectedHour
        let display = hour % 12 == 0 ? 12 : hour % 12
        return .clock(hour12: display, isPM: hour >= 12)
    }

    private var heroHourString: String {
        let hour = viewModel.selectedHour
        let suffix = hour >= 12 ? "PM" : "AM"
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return "\(displayHour):00 \(suffix)"
    }

    /// GEN-4 RULING (Sol, Nov 4, 2026): "now" is the viewed
    /// location's live local time and date — the tracking lives
    /// on the view model in the location's clock (the headline's
    /// "↻ Now" return, the sheet moment's "Now", and the pill
    /// row's Now segment all read this one state).
    private var isViewingNow: Bool {
        viewModel.isViewingLocationNow
    }

    /// WS-3 (D-11): the sheet-open seam the element consumes for
    /// dimming. WS-5 owns the sheets themselves; home only reports
    /// whether any of its sheets is presented, and the element —
    /// which belongs to home's layer — dims with the backdrop.
    private var anyHomeSheetPresented: Bool {
        showSettings || showDatePicker || showTimePicker
            || showLocationPicker || selectedWeatherDetail != nil
            || showCollection || showFeedbackSheet || wearShareEntry != nil
            // GEN-6 (RW-61): the looks library is a home sheet too.
            || showLooksLibrary
    }

    /// RW-152 §1: the Wear I've Run overlay dock — ONE surface,
    /// two states (mini dock / expanded panel), morphed in place
    /// by WearIveRunOverlayView. The session's entry drives it,
    /// and it exists ONLY while the session holds an entry
    /// (§7.1: no entry → no surface, ever — no placeholder state;
    /// the RW-119 empty form that stood in this seat is
    /// superseded on Home by §7.1, and the collection's own
    /// empty state still carries that copy behind the doorway).
    /// The surface renders the entry record only (§7.0). During
    /// a §4.7/§4.8 exit it renders the host's frozen snapshot
    /// instead.
    @ViewBuilder private func miniBarDock(viewportHeight: CGFloat) -> some View {
        // GEN-3 (the presentation half, superseding W-15's
        // by-date key): the dock presents the CURRENT SESSION's
        // entry — the entry's own place is what the narration
        // names, so the element survives the user browsing
        // another place, and it can never announce a place the
        // session has moved past, because the session's one
        // entry always holds the last settled place.
        let liveEntry = collectionModel.sessionEntry()
        let todays = wearExitInProgress ? wearExitEntry : liveEntry
        // WS-4 + Y-5: the state derives EVERYTHING from the one
        // entry value above — the narration payload AND the
        // narration kind, which the entry carries on itself
        // (`wasUpdatedInSession`, committed in the same store
        // write as the content).
        // GEN-5 (RW-29 FA-3): the moment below is the ENTRY's
        // recorded momentReading — never the live sheetMoment.
        let state = wearExitInProgress
            ? (wearExitState ?? .preAdd)
            : WearIveRunMiniBarModel.state(
                todaysEntry: todays,
                moment: todays?.momentReading ?? .now,
                wasUpdatedThisSession: todays?.wasUpdatedInSession ?? false
            )
        if state != .preAdd, let todays {
            WearIveRunOverlayView(
                state: state,
                entry: todays,
                formattedFeelsLike: viewModel.temperatureUnit.formatFromFahrenheit(todays.feelsLikeF),
                cueCount: collectionModel.cueCount,
                narrationVersion: collectionModel.narrationVersion,
                exitProgress: wearDockExitProgress,
                isDimmedBySheet: anyHomeSheetPresented,
                // RW-135 §7.0/§7.5: narration derives from the
                // ENTRY'S OWN RECORD only (the RW-88 viewed-state
                // override is removed outright), and a mid-gesture
                // settle defers its cross-fade past the gesture.
                isScrollGestureActive: isScrollGestureActive,
                viewportHeight: viewportHeight,
                // RW-152 §3: the panel thumbnail resolves through
                // the one resolver (re-signing half).
                // RW-159: the RECOVERING resolution — the
                // session entry itself may be an empty-ref
                // settle; its panel thumbnail re-links from the
                // bank and the recovery persists on the entry.
                heroURLResolver: { entry in
                    await HeroRefRuntime.loadableURLString(
                        for: entry, gender: viewModel.genderPreference,
                        onRecovered: { recovered in
                            collectionModel.healHeroReference(
                                entryID: entry.id, durableReference: recovered)
                        })
                },
                onOpenCollection: { requestSheet(.collection) },
                // RW-158: a PAST entry's tap on the expanded
                // panel's entry row opens the Share Studio
                // sheet for that entry (the panel routes via
                // WearIveRunTapPolicy; today stays collection).
                onOpenShareEntry: { requestWearShare($0) },
                onTrash: {
                    // §4.7: the host orchestrates the exit over
                    // a frozen snapshot; the store removal fires
                    // when the exit completes.
                    if let entry = liveEntry {
                        beginWearExit(snapshot: entry, removesData: true)
                    }
                }
            )
        }
    }

    // MARK: - RW-152 §1 exit (§4.7/§4.8 carried into the one surface)

    /// Orchestrate the exit over a FROZEN snapshot of the
    /// record. RW-152 §1: there is ONE surface now, so the exit
    /// is the §4.7 choreography on that surface — the content
    /// fades and the height folds (the overlay container drives
    /// both from this one progress value) over ≈560 ms; the
    /// store removal fires when the exit completes. Deleting
    /// the live session's entry this way does NOT end the
    /// session — a later settle re-creates the entry, exactly
    /// as before. Session end (§4.8) runs the same choreography
    /// with removesData == false (the record has already passed
    /// to the collection) and no haptic — haptics live at the
    /// trash tap sites, never here.
    /// Reduce Motion (§5): a 200 ms fade, no fold travel.
    private func beginWearExit(snapshot: WearIveRunEntry, removesData: Bool) {
        guard !wearExitInProgress else { return }
        wearExitEntry = snapshot
        wearExitState = WearIveRunMiniBarModel.state(
            todaysEntry: snapshot,
            moment: snapshot.momentReading,
            wasUpdatedThisSession: snapshot.wasUpdatedInSession
        )
        wearExitRemovesData = removesData
        wearExitInProgress = true
        withAnimation(.timingCurve(
            0.65, 0, 0.35, 1, duration: reduceMotion ? 0.2 : 0.56
        )) {
            wearDockExitProgress = 1
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: reduceMotion ? 220_000_000 : 580_000_000)
            finishWearExit()
        }
    }

    /// The exit completed: the store removal fires (delete
    /// only — a session end's record already passed to the
    /// collection), and the exit state resets.
    private func finishWearExit() {
        if wearExitRemovesData {
            collectionModel.removeSessionEntry()
        }
        wearExitInProgress = false
        wearExitRemovesData = false
        wearExitEntry = nil
        wearExitState = nil
        lastWearEntry = nil
        wearDockExitProgress = 0
    }

    /// Automatic entry creation (checklist §C; GEN-3 session
    /// rule): the session's first generation settles its entry;
    /// every later settle in the session — a new place, new
    /// weather — REPLACES that entry in place (the saved entry
    /// is the session's LAST settled version; updates never
    /// accumulate). Fix wave 2026-11-07 (RW-132 ruling): the
    /// CONFIRMED VIEWED day records — Now, a picked hour, or a
    /// viewed future day (the pre-ruling Now-only sentence this
    /// replaces is superseded); the entry carries the current
    /// session's identity, which is what makes the replacement
    /// land on the same entry across place changes.
    /// GEN-6 (RW-31 Wave B): the selected day's actual-
    /// temperature range in Fahrenheit, from the hourly
    /// forecast's own rows for that day — the Swing caption's
    /// {low}/{high}. Nil when the forecast carries no rows for
    /// the day (the caption then falls back to the
    /// actual/feels-like pair, never a fabricated range).
    private var selectedDayActualRangeF: (low: Double, high: Double)? {
        guard let hourly = viewModel.weatherData?.hourly,
              let temperatures = hourly.temperature2m else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = Calendar.current
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.current.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        let day = formatter.string(from: viewModel.selectedDate)
        let values = zip(hourly.time, temperatures)
            .filter { $0.0.hasPrefix(day) }
            .map { $0.1 * 9 / 5 + 32 }
        guard let low = values.min(), let high = values.max() else { return nil }
        return (low, high)
    }

    private func recordTodaysEntry() {
        // Fix wave 2026-11-07 (RW-110/RW-132, Sol's Nov 7
        // future-days ruling; review §5.3): the settled entry
        // records the CONFIRMED VIEWED selection — Now, a picked
        // hour, or a viewed future day alike — under the VIEWED
        // day's date (the upsert below keys on `selectedDate`).
        // A place switch while viewing another day moves the
        // entry to the new place under that day's date; when the
        // day arrives, the ordinary session settle takes over.
        // The Now-only gate this replaces (GEN-5 FA-2) is
        // superseded by the ruling.
        // What the gate still refuses, unchanged in spirit:
        // GEN-6 (RW-42 / DA-09) — never record while a fetch is
        // in flight (a place switch names the new place
        // synchronously and fetches asynchronously; recording
        // mid-flight filed the OUTGOING place's solve under the
        // INCOMING place's name and announced it — the RW-132
        // split). The isLoading re-offer below records the
        // settle once the fetch lands, from the one solved
        // selection, so the announced state and the card are
        // the same entry. And an out-of-forecast PICK never
        // records from a fallback solve — while the Now state
        // itself solves from current conditions by ruling, so
        // its marked fallback is admitted.
        guard WearIveRunRecordingGate.admits(
            isLoading: viewModel.isLoading,
            hasCommittedSnapshot: viewModel.selectedWeatherSnapshot != nil,
            snapshotHour: viewModel.selectedWeatherSnapshot?.hour,
            selectedHour: viewModel.selectedHour,
            isNowSelection: viewModel.isNowSelection,
            selectedTimeOutsideForecast: viewModel.selectedTimeOutsideForecast
        ) else { return }
        // W-15: never record under a placeholder. Until the
        // reverse geocode (or a manual selection) derives a
        // SimplePlace, `placeDisplayName` is a non-place label
        // ("Loading..." on a GPS first load) — an entry filed
        // under it could never be found again. The place-change
        // trigger above re-offers the recording the moment the
        // real place lands, so refusing here loses nothing.
        guard let placeName = WearIveRunHomePresentation.eligiblePlace(viewModel.simplePlace) else { return }
        let snapshot = viewModel.selectedWeatherSnapshot
        let feelsLikeF = snapshot?.feelsLikeFahrenheit
            ?? (weather.currentWeather.temperatureFahrenheit)
        let actualF = snapshot?.temperatureFahrenheit
            ?? weather.currentWeather.temperatureFahrenheit
        let entry = WearIveRunEntry(
            dayStart: Calendar.current.startOfDay(for: viewModel.selectedDate),
            placeName: placeName,
            feelsLikeF: feelsLikeF,
            actualF: actualF,
            condition: viewModel.currentCondition.shortDescription,
            itemNames: recommendation.allItems.map(\.name),
            itemKeys: recommendation.allItems.map(\.key),
            // GEN-3 train merge (I-HERO x I-WIR, forced choice
            // documented in the train record): Y-10's served-hero
            // priority AND X-10's never-starve rule, in that order.
            // The entry records the hero ACTUALLY SERVED for this
            // place at settle time — the view model's flag target,
            // its own "photo on screen right now" (pool/personal
            // hero, else the curated fallback the fetch resolved) —
            // never the view's locally recomputed `defaultFallbackUrl`
            // (a fresh MILD/CLEAR resolution), which could stamp the
            // entry with a stock photo the place never showed (the
            // New Haven chapter carried exactly such a third image).
            // Only when NO hero has been served yet (flag target
            // nil) does the entry fall back to the SAME
            // always-available gated bundled resolution the hero
            // itself paints (WS-A(1) / X-10), so one unresolved
            // photo cannot starve the mini-bar thumbnail, the TODAY
            // card, the chapter row and the Morning Poster thumbnail
            // at once; that resolution is condition-matched to the
            // current frame, not a foreign stock photo. Y-10's
            // "honestly empty before first resolution" sub-rule is
            // the one element that yields in this merge.
            heroImageReference: viewModel.currentHeroFlagTarget?.imageUrl ?? viewModel.alwaysAvailableHeroReference() ?? "",
            sessionID: collectionModel.sessionID,
            momentReading: entryMomentReading,
            lastSavedAt: Date(),
            // GEN-6 (RW-31 Wave B): the share layer's day
            // record — the moment's wind (the poster's Tier 4
            // micro-line) and the day's actual-temperature
            // range (the Swing caption), from the same snapshot
            // and hourly forecast this settle already holds.
            windMph: snapshot?.windSpeed,
            dayLowF: selectedDayActualRangeF?.low,
            dayHighF: selectedDayActualRangeF?.high
        )
        collectionModel.recordSettled(entry)
    }

    /// The outfit column's pinned foot entry (f5): opens the full
    /// weather detail for the current conditions.
    private var fullWeatherEntry: some View {
        Button {
            requestWeatherDetail(.condition(viewModel.currentCondition))
        } label: {
            HStack {
                Text("Full Weather Detail")
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.appFootnote.weight(.semibold))
                    .foregroundColor(AppTheme.inkMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(AppTheme.background)
        }
        .buttonStyle(.plain)
    }

    /// Portrait-regular outfit: the same cards in a two-column grid.
    private var outfitGridSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Ruling (Nov 9, 2026 device pass): the outfit
            // header is the count line ALONE — the "Shop"
            // button the iOS RW-144 lane carried here (from
            // the retired navigation bar) is removed, matching
            // the v8 frame and the Android RW-144 build. Item
            // taps still open the item shop flow, unchanged.
            HStack {
                Text("\(recommendation.allItems.count) items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)

            // RW-144: the temperature bracket badge no longer
            // stands over the outfit — the band word rides the
            // hero's feels-like block now (in the band colour,
            // beside the numeral). RW-109's rule is preserved at
            // that seat: it names the DISPLAY bracket.

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(Array(recommendation.allItems.enumerated()), id: \.element.id) { index, item in
                    DarkClothingItemCard(item: item, gender: viewModel.genderPreference)
                        .staggeredAnimation(index: 1 + index, isVisible: hasAppeared)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    /// The settled metric details the detail sheet tabs between
    /// (GEN-6 RW-63; frames 40/41, §3.3): feels-like and actual
    /// first — carrying the home state's numbers unchanged, in
    /// the home state's own formatting — then condition, wind,
    /// humidity, UV, and precipitation where carried.
    private var allWeatherDetails: [WeatherDetailType] {
        let snapshot = viewModel.selectedWeatherSnapshot
        return [
            .feelsLike(value: viewModel.temperatureUnit.format(viewModel.currentFeelsLike)),
            .actual(value: viewModel.temperatureUnit.format(viewModel.currentTemperature)),
            .condition(viewModel.currentCondition),
            .wind(
                speed: snapshot?.windSpeed ?? weather.currentWeather.windspeed,
                direction: snapshot?.windDirectionCompass ?? "N",
                gusts: snapshot?.windGusts ?? weather.currentWeather.windspeed
            ),
            .humidity(percentage: snapshot?.humidity ?? 50),
            .uvIndex(value: snapshot?.uvIndex ?? 0),
            .precipitation(percentage: snapshot?.precipitationProbability ?? 0)
        ]
    }

    /// The detail sheet's isPresented face. Presentation itself is
    /// driven by the selection (sheet(item:)); the sheet's own
    /// dismiss writes false here, which clears the selection.
    /// RW-161: the face is owner-gated like every Home sheet.
    private var weatherDetailPresented: Binding<Bool> {
        Binding(
            get: { selectedWeatherDetail != nil
                && HomeSheetArbiter.mayPresent(owner: homeSheetOwner, route: .weatherDetail) },
            set: { presented in
                if !presented {
                    selectedWeatherDetail = nil
                    releaseSheet(.weatherDetail)
                }
            }
        )
    }

    // MARK: - RW-161: the one sheet owner (arbiter plumbing)

    /// Claim the presentation token for a route (HomeSheetArbiter):
    /// granted at once when free, queued FIFO while another
    /// sheet stands. Activation writes the route's backing.
    private func claimSheet(_ route: HomeSheetRoute) {
        let result = HomeSheetArbiter.claim(
            owner: homeSheetOwner, queue: homeSheetQueue, requested: route)
        homeSheetOwner = result.owner
        homeSheetQueue = result.queue
        if result.owner == route { activateSheet(route) }
    }

    /// Release a route's claim: the owner's release hands the
    /// token to the oldest queued route, which then activates.
    private func releaseSheet(_ route: HomeSheetRoute) {
        let previousOwner = homeSheetOwner
        let result = HomeSheetArbiter.release(
            owner: homeSheetOwner, queue: homeSheetQueue, released: route)
        homeSheetOwner = result.owner
        homeSheetQueue = result.queue
        if previousOwner == route, let next = result.owner, next != route {
            activateSheet(next)
        }
    }

    /// Write a route's backing state (the activation half of a
    /// granted claim). Item routes consume their stashed payload.
    private func activateSheet(_ route: HomeSheetRoute) {
        switch route {
        case .settings: showSettings = true
        case .datePicker: showDatePicker = true
        case .timePicker: showTimePicker = true
        case .locationPicker: showLocationPicker = true
        case .collection: showCollection = true
        case .feedback: showFeedbackSheet = true
        case .heroPaywall: showHeroPillPaywall = true
        case .looksLibrary: showLooksLibrary = true
        case .heroViewer: showHeroViewer = true
        case .weatherDetail:
            if let detail = pendingWeatherDetail {
                selectedWeatherDetail = detail
                pendingWeatherDetail = nil
            }
        case .wearShare:
            if let entry = pendingWearShareEntry {
                wearShareEntry = entry
                pendingWearShareEntry = nil
            }
        }
    }

    /// Dismiss a route: clear its backing and release its claim.
    private func dismissSheet(_ route: HomeSheetRoute) {
        switch route {
        case .settings: showSettings = false
        case .datePicker: showDatePicker = false
        case .timePicker: showTimePicker = false
        case .locationPicker: showLocationPicker = false
        case .collection: showCollection = false
        case .feedback: showFeedbackSheet = false
        case .heroPaywall: showHeroPillPaywall = false
        case .looksLibrary: showLooksLibrary = false
        case .heroViewer: showHeroViewer = false
        case .weatherDetail: selectedWeatherDetail = nil
        case .wearShare: wearShareEntry = nil
        }
        releaseSheet(route)
    }

    /// Request a boolean-backed sheet (the ONLY way user actions
    /// present one — a direct backing write would bypass the
    /// owner and either double-present or never present).
    private func requestSheet(_ route: HomeSheetRoute) {
        claimSheet(route)
    }

    /// Request the weather-detail sheet for a payload.
    private func requestWeatherDetail(_ detail: WeatherDetailType) {
        pendingWeatherDetail = detail
        claimSheet(.weatherDetail)
    }

    /// Request the RW-158 share sheet for an entry.
    private func requestWearShare(_ entry: WearIveRunEntry) {
        pendingWearShareEntry = entry
        claimSheet(.wearShare)
    }

    /// A boolean sheet's owner-gated binding: the sheet presents
    /// only while its route owns the token; writes funnel
    /// through the arbiter (true = claim, false = dismiss).
    private func ownedBinding(_ route: HomeSheetRoute, _ backing: Binding<Bool>) -> Binding<Bool> {
        Binding(
            get: { backing.wrappedValue
                && HomeSheetArbiter.mayPresent(owner: homeSheetOwner, route: route) },
            set: { newValue in
                if newValue {
                    claimSheet(route)
                } else {
                    backing.wrappedValue = false
                    releaseSheet(route)
                }
            }
        )
    }

    /// The weather-detail sheet's owner-gated item binding.
    private var weatherDetailSheetItem: Binding<WeatherDetailType?> {
        Binding(
            get: { HomeSheetArbiter.mayPresent(owner: homeSheetOwner, route: .weatherDetail)
                ? selectedWeatherDetail : nil },
            set: { newValue in
                if let detail = newValue {
                    requestWeatherDetail(detail)
                } else {
                    selectedWeatherDetail = nil
                    releaseSheet(.weatherDetail)
                }
            }
        )
    }

    /// The RW-158 share sheet's owner-gated item binding.
    private var wearShareSheetItem: Binding<WearIveRunEntry?> {
        Binding(
            get: { HomeSheetArbiter.mayPresent(owner: homeSheetOwner, route: .wearShare)
                ? wearShareEntry : nil },
            set: { newValue in
                if let entry = newValue {
                    requestWearShare(entry)
                } else {
                    wearShareEntry = nil
                    releaseSheet(.wearShare)
                }
            }
        )
    }

    /// Context lines for the weather detail's sidebar: where and
    /// when the numbers on show belong to.
    private var weatherContextLines: [String] {
        [
            // RW-146: the context names the displayed place, as
            // every place-bearing surface now does.
            viewModel.displayedPlaceName,
            navDateString,
            "\(viewModel.temperatureUnit.format(viewModel.currentTemperature)) · Feels like \(viewModel.temperatureUnit.format(viewModel.currentFeelsLike))",
            // RW-109: this line stands beside the same displayed
            // feels-like (the line above), so it names the
            // DISPLAY bracket, not the engine bracket.
            viewModel.displayTempBracket.description
        ]
    }

    /// RW-23: honest caption for an out-of-forecast selection. The
    /// screen keeps showing current conditions (the view model's
    /// fallback snapshot), and this notice — rendered above the
    /// outfit list in every layout, in the view's caption style —
    /// says so, with the selected date/time as context, instead of
    /// the pre-RW-23 silent substitution.
    private var outsideForecastNotice: some View {
        Text("Forecast isn't available for that time yet — showing current conditions. (\(navDateString), \(selectedHourString))")
            .font(.appFootnote)
            .foregroundColor(AppTheme.inkSecondary)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var selectedHourString: String {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: viewModel.selectedDate)
        components.hour = viewModel.selectedHour
        guard let date = Calendar.current.date(from: components) else {
            return "\(viewModel.selectedHour):00"
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "h a"
        return formatter.string(from: date)
    }

    private var navDateString: String {
        let calendar = Calendar.current
        let date = viewModel.selectedDate
        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInTomorrow(date) {
            return "Tomorrow"
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d"
        return formatter.string(from: date)
    }

    // MARK: - Hero Section

    /// RW-144: the hero = the photograph (heroPhotoSection) plus
    /// the v8 chrome overlay (top row, date/time chip,
    /// feels-like block on its luminance-driven scrim zone).
    /// The stacked layouts drive the chrome's fade/interactivity
    /// from the scroll handoff (WS-1 §1.1, above); the Duo pane
    /// takes it at rest.
    /// The current Make It Me pill state's planning height
    /// (HeroPillMetrics) — the input the hero's one vertical
    /// layout guarantees the whole stack against, in every
    /// pill state (Ruling 2 / the build-33 weather-line
    /// defect: the two-line generating pill is the tall one).
    private var currentPillHeight: CGFloat {
        HeroPillMetrics.height(for: heroPillState)
    }

    @ViewBuilder
    private func heroSection(
        heightMode: HeroImageView.HeightMode = .containerFraction,
        chromeOpacity: Double = 1,
        chromeInteractive: Bool = true,
        explicitHeight: CGFloat? = nil,
        pillBottomOffset: CGFloat = HeroVerticalLayout.straddleDip
    ) -> some View {
        heroPhotoSection(heightMode: heightMode, explicitHeight: explicitHeight)
            .overlay {
                heroChrome(pillBottomOffset: pillBottomOffset)
                    .opacity(chromeOpacity)
                    .allowsHitTesting(chromeInteractive)
            }
            .clipped()
    }

    /// The hero chrome, fed by the current selection and the
    /// photo's measured luminances. Ruling 2 (Nov 9, 2026):
    /// the boxless weather line rides over the hero too — fed
    /// from the same snapshot the sheet's opening line read
    /// in RW-144 (sheetWeatherLine's source expressions).
    private func heroChrome(pillBottomOffset: CGFloat = HeroVerticalLayout.straddleDip) -> some View {
        HeroChromeOverlayView(
            // RW-146: the place pill names the DISPLAYED place —
            // the place the conditions beside it belong to.
            placeName: HomeHeaderCopy.placeSegment(place: viewModel.displayedPlaceName),
            dateText: chipDateString,
            timeText: isViewingNow ? "Now" : heroHourString,
            isNow: isViewingNow,
            // RW-144 (parent ruling, Nov 8, 2026): the hero
            // numeral + Actual line are the bare degree ("58°" /
            // "Actual 60°"), per the approved v8 frames — the
            // unit letter belongs to every other surface only.
            formattedFeelsLike: viewModel.temperatureUnit.formatBareDegree(viewModel.currentFeelsLike),
            formattedActual: viewModel.temperatureUnit.formatBareDegree(viewModel.currentTemperature),
            feelsLikeF: viewModel.currentFeelsLike,
            topLuminance: heroLuminance.topLuminance,
            chipLuminance: heroLuminance.chipLuminance,
            footLuminance: heroLuminance.footLuminance,
            onPlaceTapped: { requestSheet(.locationPicker) },
            onSettingsTapped: { requestSheet(.settings) },
            onDateTapped: { requestSheet(.datePicker) },
            onTimeTapped: { requestSheet(.timePicker) },
            onNowTapped: { viewModel.resetToNow() },
            windMph: viewModel.selectedWeatherSnapshot?.windSpeed ?? weather.currentWeather.windspeed,
            rainPercent: viewModel.selectedWeatherSnapshot?.precipitationProbability ?? 0,
            humidityPercent: viewModel.selectedWeatherSnapshot?.humidity ?? 50,
            uvIndex: viewModel.selectedWeatherSnapshot?.uvIndex ?? 0,
            weatherLuminance: heroLuminance.weatherLuminance,
            pillHeight: currentPillHeight,
            pillBottomOffset: pillBottomOffset
        )
    }

    /// The chip's date segment: the selected date in the
    /// "Sun, Nov 8" form (the frames' form), read with the same
    /// calendar the sheet's moment line has always used.
    private var chipDateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d"
        return formatter.string(from: viewModel.selectedDate)
    }

    @ViewBuilder
    private func heroPhotoSection(heightMode: HeroImageView.HeightMode = .containerFraction, explicitHeight: CGFloat? = nil) -> some View {
        // GEN-3 (WS-A(1) + WS-B) SUPERSEDES the WS-1 §1.1 skeleton
        // gate below it in history: the bundled fallback is a FINAL
        // truthful serve, not a "for now" paint — the view model
        // commits the new cell's gated fallback in the same write
        // that drops the old cell's photo (the atomic commit), so
        // the fallback paints from the FIRST frame of the new cell,
        // while the pool cascade and any personal generation run.
        // A settled empty band is the X-3 defect, on every surface.
        // The skeleton survives for exactly two states: (1) the
        // resolved hero does not belong to the place on the label
        // (a place switch in flight — X-7: the previous place's
        // photo must never paint under the new name), and (2) the
        // very first resolution, before any fallback has been
        // committed for this place at all.
        if !viewModel.heroBelongsToDisplayedPlace {
            heroSkeleton(heightMode: heightMode, explicitHeight: explicitHeight)
        } else if viewModel.heroImageUrl == nil
            && viewModel.fallbackImageUrl == nil {
            heroSkeleton(heightMode: heightMode, explicitHeight: explicitHeight)
        } else {
            heroImageView(heightMode: heightMode, explicitHeight: explicitHeight)
                // Fix wave 2026-11-07 (RW-133): tapping the
                // hero opens the stored photograph fitted
                // full-screen (see HeroPhotoViewer).
                .onTapGesture { requestSheet(.heroViewer) }
        }
    }

    /// The hero zone's loading skeleton (frame 46's treatment at
    /// hero scale): a sheet-toned block in the hero's own frame —
    /// the same container fraction / fill mechanics as the photo,
    /// so the sheet composes against identical geometry and
    /// nothing moves when the photo lands.
    @ViewBuilder
    private func heroSkeleton(heightMode: HeroImageView.HeightMode, explicitHeight: CGFloat? = nil) -> some View {
        let block = Rectangle()
            .fill(AppTheme.cardOnSheet)
            .overlay {
                ProgressView()
                    .tint(AppTheme.inkSecondary)
            }
        if let explicitHeight {
            block.frame(height: explicitHeight)
        } else if heightMode == .fill {
            block.frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            block.containerRelativeFrame(.vertical) { length, _ in
                length * AppTheme.heroHeightRatio
            }
        }
    }

    private func heroImageView(heightMode: HeroImageView.HeightMode = .containerFraction, explicitHeight: CGFloat? = nil) -> some View {
        HeroImageView(
            locationName: viewModel.displayedPlaceName,
            // Hero contract (Android/PWA): headline = raw feels-like,
            // sub-line = raw actual. (Was: headline = actual,
            // sub-line = comfort-adjusted feels-like.)
            temperature: viewModel.currentFeelsLike,
            actualTemperature: viewModel.currentTemperature,
            temperatureUnit: viewModel.temperatureUnit,
            weatherCondition: viewModel.currentCondition,
            date: viewModel.selectedDate,
            hour: viewModel.selectedHour,
            tempBracket: viewModel.currentTempBracket,
            // GEN-3: an uncommitted fallback is an EMPTY url (the
            // designed gradient), never an unrelated bracket's
            // stock photo — the pre-GEN-3 MILD×CLEAR default could
            // paint a shorts photo over a COOL outfit.
            fallbackImageUrl: viewModel.fallbackImageUrl ?? "",
            aiImageUrl: viewModel.heroImageUrl,
            onSettingsTapped: { requestSheet(.settings) },
            // No onShareTapped: the hero's top controls are off
            // (showsTopControls below) — and since WS-6 (T-4)
            // there is no home share at all; sharing lives in
            // the collection (Poster / Kit Card).
            onLocationTapped: { requestSheet(.locationPicker) },
            onDateTapped: { requestSheet(.datePicker) },
            onTimeTapped: { requestSheet(.timePicker) },
            onNowTapped: { viewModel.resetToNow() },
            flagTarget: viewModel.currentHeroFlagTarget,
            onFlagHero: { reason in
                viewModel.flagCurrentHero(reason: reason)
                withAnimation { showFlagThanks = true }
            },
            personalFlagTarget: viewModel.currentPersonalFlagTarget,
            onFlagPersonalHero: { reason in
                viewModel.flagCurrentPersonalHero(reason: reason)
                withAnimation { showFlagThanks = true }
            },
            // RW-144: the hero pill no longer seats at the
            // hero's foot — it straddles the hero/sheet boundary
            // in the sheet container (see sheetStraddlePill,
            // fed by the same heroPillState). HeroImageView's
            // own pill seat is passed .hidden here.
            pillState: .hidden,
            onPillGetUnlimited: { requestSheet(.heroPaywall) },
            // RW-111: the failed look's line at the hero, with
            // its retry — the same retry the personal section's
            // button used to fire from the bottom of the page.
            // RW-161: the failed-look line ("…photos — tap to
            // try again") is a transient surface on Home's own
            // layer — it defers while any sheet owns the screen
            // rather than rendering between stacked sheets.
            lookFailed: currentCellLookIsFailed && !anyHomeSheetPresented,
            onRetryFailedLook: {
                if let cell = viewModel.currentPersonalCombination {
                    personalStore.retryLook(for: cell)
                    viewModel.refreshHero()
                }
            },
            heightMode: heightMode,
            heroHeightOverride: explicitHeight,
            // The navigation container carries the chrome now:
            // location is the title, Settings/Share are toolbar
            // items — no floating pills on the hero.
            showsTopControls: false
        )
    }

    /// GEN-3 (WS-A(2) / WS-B, census X-2): the hero pill's state,
    /// derived ONLY from the cell this screen is keyed on — the
    /// view model's current personal combination (the cell the
    /// last hero resolution was requested for, at the place on the
    /// label) and THAT cell's server look state. A generation in
    /// flight for any other cell cannot light this pill, and this
    /// cell's generation lights it here on the hero — not only on
    /// the first-look screen, and not in the scroll content under
    /// the photo (X-8). No current cell (not opted in / stock
    /// mode) hides the pill.
    private var heroPillState: HeroPillState {
        guard let cell = viewModel.currentPersonalCombination else { return .hidden }
        return HeroPill.resolve(
            optedIn: personalStore.isOptedIn,
            lookState: personalStore.lookStates[cell.combinationId] ?? .none,
            personalPhotoDisplayed: viewModel.currentPersonalHeroId != nil,
            isSubscribed: personalStore.mode == .subscribed,
            freeGenerationsUsed: personalStore.freeGenerationsUsed,
            freeAllowance: PersonalHeroStore.freeAllowance,
            // GEN-6 Wave A (RW-34): a scaffold serve is silent —
            // the pill policy, not this view, decides the state.
            isScaffoldServe: viewModel.currentPersonalServeIsScaffold,
            // RW-111 / RW-118: the cell the server refused with
            // the allowance exhausted carries the exhausted pill
            // at the hero even with no personal photo on screen.
            isExhaustedCell: personalStore.exhaustedCell == cell.combinationId
        )
    }

    /// RW-111: whether the current cell's look stands failed —
    /// the hero's failure-line input (the line itself renders in
    /// HeroImageView, in the pill's eye line over the stock hero).
    private var currentCellLookIsFailed: Bool {
        guard let cell = viewModel.currentPersonalCombination else { return false }
        return (personalStore.lookStates[cell.combinationId] ?? .none).isFailed
    }

    // MARK: - RW-14 Personal Section

    /// The "Make It Me" surfaces on the run screen, in spec order:
    /// (GEN-3: the making/scaffold states moved ONTO the hero as
    /// the one hero pill — see heroPillState.) RW-111 (successor
    /// wave): the failed line and the exhausted state moved onto
    /// the hero too — the failure line renders in HeroImageView's
    /// eye line over the stock hero (tappable to retry), and the
    /// exhausted state is the hero pill's own exhausted state
    /// (HeroPill.resolve's refused-cell branch). Both used to
    /// render HERE, at the bottom of the page — the failure block
    /// and the §6 U1 upsell card — far from the hero the user was
    /// looking at; the card's home rendering is retired with the
    /// move (the exhausted pill is its replacement surface, and
    /// rendering both would put the same message on one screen
    /// twice). What remains in this section is the failed cell's
    /// quiet library door (RW-61) — a door, not a status.
    /// Ready renders nothing: the personalized photo on screen
    /// is itself the state.
    @ViewBuilder private var personalSection: some View {
        if let cell = viewModel.currentPersonalCombination {
            switch personalStore.lookStates[cell.combinationId] ?? .none {
            case .making:
                // GEN-3 (WS-A(2)): the making state now lives ON
                // the photo as the ONE hero pill (HeroPill:
                // "Making this you…"); a scaffold serve under it
                // is silent (GEN-6 Wave A, RW-34 — the "Your
                // photo" label is removed). The pre-GEN-3 lines
                // here (the G1/G2 progress block and the scaffold
                // badge text) sat in the scroll content under the
                // hero — census X-8's wrong-place defect — and are
                // folded into the pill; nothing renders here, so
                // the state has exactly one surface. (G1/G2 copy
                // still renders on the first-look screen's photo
                // via the W-16 seam.)
                EmptyView()
            case .failed:
                // RW-111: the failed line + retry themselves now
                // render AT THE HERO (HeroImageView's failure
                // line, fed by currentCellLookIsFailed). The
                // library door stays here — one tap from where
                // the failed cell is encountered (frame 20's door
                // wording, RW-61).
                Button("See all your looks") {
                    requestSheet(.looksLibrary)
                }
                .font(.appSecondary.weight(.semibold))
                .foregroundColor(AppTheme.inkPrimary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal, 16)
            case .ready, .none:
                EmptyView()
            }
        }

    }

    /// GEN-5 (RW-28 item 22, band placement clarified Nov 5):
    /// the "Making your looks" band on home — DIRECTLY BELOW THE
    /// OUTFIT AREA, above the day-chart band, the Pro Tip, and
    /// the feedback row, inside the first screenful, so a user
    /// who has just subscribed sees it and understands what is
    /// happening. (It previously rendered at the foot of the
    /// personal section, below the chart — the position Sol
    /// flagged on Oct 31 and again on Nov 5.) Visible only while
    /// a subscribed user's bank is genuinely converging.
    @ViewBuilder private var homeBandSection: some View {
        // GEN-5 (RW-29 FA-4): the store's one band gate
        // (trial users never see the band).
        if let band = personalStore.convergenceBand {
            ConvergenceBandView(content: band)
        }
    }

    // MARK: - Outfit Section

    private var outfitSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Section header — the settled count line alone
            // ("N items for your run", Android's form), not the
            // generic "Recommended Outfit" (GEN-5, RW-29 FA-55)
            // and, since the Nov 9, 2026 ruling, no "Shop"
            // button beside it (see outfitGridSection).
            HStack {
                Text("\(recommendation.allItems.count) items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)

                Spacer()
            }
            .padding(.horizontal, 16)

            // RW-144: no bracket badge over the outfit — the
            // band word rides the hero's feels-like block (see
            // outfitGridSection's note; RW-109's display-bracket
            // rule is preserved at that seat).

            // All clothing items in layer order (v4.1), as the
            // v8 BOXLESS rows: garment tile, name, description —
            // no card (FirstScreenOutfitRow).
            ForEach(Array(recommendation.allItems.enumerated()), id: \.element.id) { index, item in
                FirstScreenOutfitRow(item: item, gender: viewModel.genderPreference)
                    .staggeredAnimation(index: 1 + index, isVisible: hasAppeared)
            }

        }
    }

    /// GEN-5 (RW-28, C-3): the Pro Tip block — its own section
    /// now, rendered below the day-chart band in every home
    /// layout (it lived at the outfit list's foot before the
    /// settlement, above the band).
    @ViewBuilder private var proTipSection: some View {
        if !recommendation.tips.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Pro Tip")
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkSecondary)
                    .padding(.horizontal, 16)

                ForEach(Array(recommendation.tips.enumerated()), id: \.offset) { _, tip in
                    Text(tip)
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                        .padding(.horizontal, 16)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

}

// MARK: - Refresh Error Banner (RW-6.2)

/// The non-destructive error surface for a failed refresh: a dark
/// glass banner in the app's card idiom, floating over the outfit
/// that stays on screen. Retry re-runs the refresh; the X clears
/// the error without one.
private struct RefreshErrorBanner: View {
    let message: String
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange)

            Text(message)
                .font(.system(size: 13))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button("Retry", action: onRetry)
                .font(.system(size: 13, weight: .semibold))
                // WS-8 (D-25 class): the banner's fill is near-black
                // in both modes, so its pine text is the DARK pine
                // accent (#5CC79F, ≈7.3:1 on the banner fill) — the
                // seed pine (primaryColor) computes to ≈2.0:1 there.
                .foregroundColor(AppTheme.pineAccentDark)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white.opacity(0.6))
            }
            .accessibilityLabel("Dismiss")
        }
        .padding(12)
        .background(
            // GEN-5 (cream map row 51): Warm Charcoal, not pure
            // black (the pine-on-fill contrast note above was
            // computed against this family and still holds).
            RoundedRectangle(cornerRadius: 12)
                .fill(AppTheme.darkBackground.opacity(0.94))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.15), lineWidth: 1)
        )
        .shadow(radius: 8)
    }
}

// MARK: - Dark Theme Cards

struct DarkClothingItemCard: View {
    let item: ClothingItem
    var category: String? = nil
    var gender: GenderPreference = .unisex

    @Environment(\.colorScheme) private var colorScheme

    private var thumbnailURL: URL? {
        // RW-144: the same garment-tile seam as the first
        // screen's rows — bundled Treatment L tiles in light
        // mode, the remote thumbnail in dark.
        if colorScheme == .light {
            return ClothingThumbnailService.shared.lightTileURL(for: item, gender: gender)
                ?? ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: gender)
        }
        return ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: gender)
    }

    var body: some View {
        // Direct link to Amazon affiliate (no modal) - PWA v3.12 pattern
        if let url = item.affiliateURL(for: gender) {
            Link(destination: url) {
                cardContent
            }
            .buttonStyle(PlainButtonStyle())
        } else {
            cardContent
        }
    }

    private var cardContent: some View {
        HStack(spacing: 16) {
            // Thumbnail container - 56x56, border-radius 12 per PWA v3.12
            ZStack {
                // Background — RW-144 garment-tile fill
                // (#F3EEE5 light / #25201A dark).
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppTheme.garmentTileFill)

                // Emoji icon (shown as fallback)
                if thumbnailURL == nil {
                    Image(systemName: item.icon)
                        .font(.title)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                // AI-generated thumbnail
                if let url = thumbnailURL {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .empty:
                            // Loading state - show icon
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(AppTheme.inkSecondary)
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .failure:
                            // Error state - show icon
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(AppTheme.inkSecondary)
                        @unknown default:
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                }
            }
            .frame(width: 56, height: 56)

            // Info
            VStack(alignment: .leading, spacing: 4) {
                if let category = category {
                    Text(category.uppercased())
                        .font(.appEyebrow)
                        .foregroundColor(AppTheme.inkMuted)
                }
                Text(item.name)
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Text(item.description)
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.descriptionInk)
                    // RW-114 (successor wave) SUPERSEDES the
                    // GEN-6 RW-54 one-line note for this text:
                    // the description renders WHOLE — it wraps as
                    // many lines as it needs at the current type
                    // size and never ellipsizes mid-word (Sol's
                    // build-28 still: "Eye protection from sun
                    // an…"). No line limit stands here.
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()

            // External link icon (matching PWA v3.12)
            Image(systemName: "arrow.up.right")
                .font(.appFootnote.weight(.semibold))
                .foregroundColor(AppTheme.inkMuted)
                .frame(width: 44, height: 44)
        }
        .padding(12)
        // Flat content card (AppTheme tokens) — deliberately NOT glass:
        // dense text stays off glass for legibility.
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppTheme.cardFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(AppTheme.cardStroke, lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }
}

// MARK: - Legacy Components (kept for compatibility)

struct TemperatureBracketBadge: View {
    let bracket: TemperatureBracket
    /// The display unit the range renders in — the same unit the
    /// hero numbers above it carry, so the screen tells one unit
    /// story (the rawValue it replaces is hard-coded °F).
    var unit: TemperatureUnit = .fahrenheit

    var body: some View {
        HStack(spacing: 8) {
            Text(bracket.description)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
            Text(bracket.rangeText(for: unit))
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.8))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            Capsule()
                // RW-105: the deepened chip colour at FULL
                // opacity — the pre-fix bright data colour at
                // 0.8 over the cream ground read as pale mint
                // under white text (Sol's circled surface).
                .fill(AppTheme.temperatureChipColor(fahrenheit: temperatureForBracket))
        )
    }

    private var temperatureForBracket: Double {
        switch bracket {
        case .hot: return 85
        case .warm: return 65
        case .mild: return 55
        case .cool: return 45
        case .cold: return 35
        case .veryCold: return 25
        case .frigid: return 15
        case .extreme: return 10
        }
    }
}

// WS-10 (A4 residue, R-1): the legacy GenderToggle / GenderOption
// pair that used to sit here is REMOVED. It had no call sites
// anywhere in the app (full-tree search: definitions only), and
// its GenderOption rendered GenderPreference.displayIcon — the
// 🚹/🚺/○ text glyphs no frame of the approved design carries.
// The live fit control is FitPreferenceInlinePicker (plain text
// labels, shared by Settings and Onboarding). Nothing rendered the pair,
// so nothing changes on screen.

#Preview {
    let viewModel = WeatherViewModel()
    let locationService = LocationService()

    WeatherOutfitView(
        viewModel: viewModel,
        locationService: locationService,
        weather: WeatherData(
            latitude: 40.7128,
            longitude: -74.0060,
            currentWeather: CurrentWeather(
                temperature: 15,
                windspeed: 10,
                weathercode: 0,
                isDay: 1
            ),
            hourly: nil,
            timezone: nil,
            currentDetail: nil
        ),
        recommendation: OutfitRecommendationService.shared.getRecommendation(
            for: 59,
            rawTemperature: 59,
            isRaining: false
        ),
        onRefresh: {}
    )
}

// MARK: - Flat Sheet Pill (RW-25)

extension View {
    /// Flat pill chrome for the sheet's date/time pills: sheet-tonal
    /// fill + ink text; the selected state (the Now pill) carries
    /// the pine tint. Not glass — these are content controls on the
    /// sheet, not floating chrome.
    func flatSheetPill(isSelected: Bool = false) -> some View {
        self
            .foregroundColor(AppTheme.inkPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule().fill(isSelected ? AppTheme.primaryColor.opacity(0.25) : AppTheme.cardOnSheet)
            )
    }
}
