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
    /// End-of-scroll signal for the mini bar's full expression,
    /// driven by the same onScrollGeometryChange feed as the hero
    /// handoff.
    @State private var isAtEndOfScroll = false
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
    @State private var showShop = false
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
                if showFlagThanks {
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
            // RW-25: the "Wear I've Run" mini bar dock — attached
            // to the home scroll's container; renders only when the
            // state is not .preAdd (the session's entry exists).
            .safeAreaInset(edge: .bottom) {
                miniBarDock
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
            .navigationTitle(viewModel.placeDisplayName)
            // The design record's date-as-subtitle needs
            // navigationSubtitle, which the current iOS SDK marks
            // unavailable on iPhone (it is a newer-platform API
            // there) — CI 40394130506 is the evidence. The date
            // stays visible on the hero's date pill, the title
            // menu, and the weather detail's context until the
            // build SDK offers the subtitle on iOS.
            .navigationBarTitleDisplayMode(.inline)
            // The chrome band is OPAQUE. With no toolbar background
            // the iOS 26 bar is translucent glass over a hero that
            // ignores the top safe area, so scrolled content — the
            // weather pills row above all — slid underneath the
            // location title and the glass action capsule, ghosting
            // through both and clipping pill values behind the
            // capsule (field finding 2026-10-31, both scroll states
            // photographed). The title row and the action group now
            // own their band exclusively; content scrolls beneath a
            // solid bar, as it does under Android's app bar.
            .toolbarBackground(AppTheme.background, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            // GEN-3 Y-16 (FIRM): ONE tap on the location opens the
            // COMPLETE change-location UI immediately. The pre-fix
            // entry was a toolbarTitleMenu — tapping the title
            // opened a one-item menu ("Set Location") and only the
            // second tap reached the picker. The title is now a
            // principal toolbar BUTTON bound straight to the
            // picker; there is no intermediate menu anywhere in
            // the home location path.
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Button {
                        showLocationPicker = true
                    } label: {
                        HStack(spacing: 4) {
                            Text(viewModel.placeDisplayName)
                                .font(.headline)
                                .foregroundColor(AppTheme.inkPrimary)
                                .lineLimit(1)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Location: \(viewModel.placeDisplayName). Change location")
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Label("Settings", systemImage: "gearshape")
                    }
                    Button {
                        showShop = true
                    } label: {
                        Label("Shop", systemImage: "cart")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsModal(
                    isPresented: $showSettings,
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
            .sheet(isPresented: $showDatePicker) {
                DatePickerSheet(
                    selectedDate: $viewModel.selectedDate,
                    isPresented: $showDatePicker
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .onChange(of: viewModel.selectedDate) { _, newDate in
                    viewModel.updateSelectedTime(date: newDate, hour: viewModel.selectedHour)
                }
            }
            .sheet(isPresented: $showTimePicker) {
                // GEN-4: the chart sheet works on a DRAFT — the
                // view model's selection is written only by
                // Confirm, through applyTimePickerCommit. (The
                // pre-GEN-4 form bound selectedHour live, and an
                // onChange re-solved home behind the sheet on
                // every tile tap; both are gone with the tiles.)
                TimePickerSheet(
                    chart: viewModel.dayChart,
                    dateLabel: navDateString,
                    placeName: viewModel.placeDisplayName,
                    unit: viewModel.temperatureUnit,
                    initialHour: timePickerInitialHour,
                    openedInNowState: viewModel.isNowSelection,
                    nowHour: viewModel.dayChartNowHour,
                    isPresented: $showTimePicker,
                    onConfirm: { viewModel.applyTimePickerCommit($0) },
                    // GEN-6 (RW-66): the day picker's door inside
                    // the sheet (its context line's date segment).
                    // Confirm writes the view model's selectedDate
                    // — the .onChange on the date-sheet modifier
                    // above re-solves home exactly once, keeping
                    // the selected hour — and dismisses this sheet
                    // too, landing home on the new day.
                    dateSelection: $viewModel.selectedDate,
                    onDateConfirmed: { showTimePicker = false }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showShop) {
                ShopModal(
                    items: recommendation.allItems,
                    genderPreference: viewModel.genderPreference,
                    weatherCode: viewModel.selectedWeatherSnapshot?.weatherCode ?? weather.currentWeather.weathercode,
                    isPresented: $showShop
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            // Item-based, not isPresented + if-let: in the old
            // two-state form the sheet could evaluate its content
            // before the selection landed and present an empty
            // (white) sheet -- the blank UV overlay of 2026-10-29.
            // With .sheet(item:) the content exists exactly when
            // a detail does.
            .sheet(item: $selectedWeatherDetail) { detail in
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
            .sheet(isPresented: $showLocationPicker) {
                LocationPickerSheet(
                    isPresented: $showLocationPicker,
                    isUsingGPS: viewModel.isUsingGPS,
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
            // RW-79: returning to Home re-reads the current
            // cell's look state — a generation that finished (or
            // failed) while the user was anywhere else is the
            // server's word now, not whatever Home last rendered.
            viewModel.revalidatePersonalLookState()
        }
        // RW-79: the foreground re-read. A generation running
        // when the app was backgrounded keeps being watched by
        // the store's poll only while its schedule lasts (the
        // schedule is consumed in real time, suspension
        // included); when the app comes back, one fresh read
        // folds the terminal state — or re-arms the poll when
        // the cell is still MAKING.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                viewModel.revalidatePersonalLookState()
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
        .onChange(of: viewModel.heroImageUrl) { _, _ in
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
        .sheet(isPresented: $showCollection) {
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
        .sheet(isPresented: $showFeedbackSheet) {
            // WS-5 (§1.3): named detent — the feedback Form
            // scrolls, so .large only.
            FeedbackSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showHeroPillPaywall, onDismiss: {
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
        .sheet(isPresented: $showLooksLibrary) {
            // GEN-6 (RW-61): the looks library, one tap from the
            // failed cell's home line.
            NavigationStack {
                YourLooksLibraryView()
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        // Neutral THREE-door rating moment (frame 49): feedback
        // first, then the rating door, then not-now. Any door
        // closes it for the season; the rating door hands off to
        // the system review sheet.
        .alert("Enjoying RunWear?", isPresented: $showRatingPrompt) {
            Button("Send feedback") {
                ratingTracker.recordPromptShown()
                showFeedbackSheet = true
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
                        VStack(spacing: 16) {
                            heroSection(heightMode: .fill)
                                .overlay(alignment: .top) { scriptLogo }
                                .frame(height: geo.size.height * 0.62)
                        }
                        .padding(.bottom, 24)
                    }
                } secondary: {
                    VStack(spacing: 0) {
                        ScrollView {
                            VStack(spacing: 0) {
                                // RW-25 Duo: the sheet content block
                                // (a)-(e) as a FLAT header on the theme
                                // background — no overlap in the split
                                // layout (permanent legibility state).
                                sheetHeader
                                if viewModel.selectedTimeOutsideForecast {
                                    outsideForecastNotice
                                        .padding(.top, 16)
                                }
                                outfitSection
                                    .padding(.top, 24)
                                homeBandSection
                                    .padding(.top, 24)
                                dayChartBand
                                    .padding(.top, 24)
                                proTipSection
                                    .padding(.top, 24)
                                personalSection
                                    .padding(.top, 24)
                            }
                            .padding(.bottom, 16)
                        }
                        .refreshable { onRefresh() }
                        fullWeatherEntry
                    }
                }
            } else if horizontalSizeClass == .regular {
                ScrollView {
                    VStack(spacing: 0) {
                        heroWithScrollHandoff
                        sheetContainer {
                            outfitGridSection
                                .padding(.top, 24)
                        }
                    }
                }
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    max(0, geometry.contentOffset.y + geometry.contentInsets.top)
                } action: { _, offset in
                    updateHeroScrollProgress(offset: offset)
                }
                .onScrollGeometryChange(for: Bool.self) { geometry in
                    geometry.visibleRect.maxY >= geometry.contentSize.height - 60
                } action: { _, atEnd in
                    isAtEndOfScroll = atEnd
                }
                .refreshable { onRefresh() }
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        heroWithScrollHandoff
                        sheetContainer {
                            outfitSection
                                .padding(.top, 24)
                        }
                    }
                    .frame(maxWidth: geo.size.width >= 600 ? 640 : .infinity)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    max(0, geometry.contentOffset.y + geometry.contentInsets.top)
                } action: { _, offset in
                    updateHeroScrollProgress(offset: offset)
                }
                .onScrollGeometryChange(for: Bool.self) { geometry in
                    geometry.visibleRect.maxY >= geometry.contentSize.height - 60
                } action: { _, atEnd in
                    isAtEndOfScroll = atEnd
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
    private var heroWithScrollHandoff: some View {
        let progress = reduceMotion ? 0 : heroScrollProgress
        return heroSection()
            // WS-1 (§1.1): the photo-zone chrome fades 1 → 0
            // across the FIRST 40% of the handoff and is
            // non-interactive from the moment the fade begins —
            // it never slides, and it is fully present again at
            // rest. (Before this fix the logo only rode the
            // hero's scale transform and never faded at all.)
            .overlay(alignment: .top) {
                scriptLogo
                    .opacity(HomeHandoffGeometry.chromeOpacity(progress: progress))
                    .allowsHitTesting(HomeHandoffGeometry.chromeIsInteractive(progress: progress))
            }
            .scaleEffect(HomeHandoffGeometry.scale(progress: progress))
            .clipShape(RoundedRectangle(cornerRadius: HomeHandoffGeometry.cornerRadius(progress: progress)))
            .padding(.horizontal, HomeHandoffGeometry.horizontalInset(progress: progress))
    }

    /// RW-26 (notes §7.3): the script logo at the top of the main
    /// screen — genuine Great Vibes "RunWear", warm-white #F5F1EA,
    /// 29pt, centered, as photo-zone chrome (a brand mark, not
    /// data). The family's 24pt floor is respected.
    private var scriptLogo: some View {
        Text("RunWear")
            .font(.greatVibes(size: 29))
            .foregroundColor(AppTheme.warmWhite)
            .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
            .padding(.top, 10)
            .accessibilityHidden(true)
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
            sheetHeader
            if viewModel.selectedTimeOutsideForecast {
                outsideForecastNotice
                    .padding(.top, 16)
            }
            outfit()
            // GEN-5: the convergence band directly below the
            // outfit, above the glance band (see homeBandSection).
            homeBandSection
                .padding(.top, 24)
            // GEN-4: the glance band sits below the outfit list.
            dayChartBand
                .padding(.top, 24)
            // GEN-5 (RW-28, C-3 settlement): the Pro Tip sits
            // BELOW the hourly band — the bottom of the normal
            // elements; only Wear I've Run (the pinned dock and
            // the personal section's content) sits below it.
            proTipSection
                .padding(.top, 24)
            personalSection
                .padding(.top, 24)
            Spacer(minLength: 40)
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
    }

    /// The sheet header block, in order (notes §7.2, GEN-4
    /// amended): (i) "Feels like" label, (ii) location line —
    /// its moment segment is the second time-picking entry,
    /// (iii) hero number + temperature chip with the
    /// right-justified weather figures beside the numeral,
    /// (iv) "Actual NN°" line — then the flat date/time pills.
    /// Used by the stacked sheet and, flat, by the Duo trailing
    /// column.
    private var sheetHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            // (a)+(b) RW-26 settled header stack (notes §7.2): the
            // "Feels like" label stands alone as the small uppercase
            // label, and the location line sits beneath it as its
            // own piece — never one undifferentiated eyebrow block.
            VStack(alignment: .leading, spacing: 4) {
                Text(HomeHeaderCopy.feelsLikeLabel)
                    .font(.appEyebrow)
                    .textCase(.uppercase)
                    .tracking(0.5)
                    .foregroundColor(AppTheme.inkMuted)
                headlineLocationLine
            }

            // (b) Hero number (ink, never temperature-colored) +
            // temperature chip (filled dot + bracket label), with
            // GEN-4's right-justified weather figures beside the
            // numeral: WIND / RAIN / HUMIDITY / UV in a strict
            // two-column grid — labels in one fixed column, values
            // flush right — its block top aligned to the
            // numeral's top. Nothing weather-related sits below
            // the headline on home anymore (the weather pills
            // row that used to render under the date/time pills
            // is retired from home; the weather detail sheet
            // itself is unchanged).
            HomeHeroNumeralRow(
                formattedTemperature: viewModel.temperatureUnit.format(viewModel.currentFeelsLike),
                bracket: viewModel.currentTempBracket,
                windMph: viewModel.selectedWeatherSnapshot?.windSpeed ?? weather.currentWeather.windspeed,
                rainPercent: viewModel.selectedWeatherSnapshot?.precipitationProbability ?? 0,
                humidityPercent: viewModel.selectedWeatherSnapshot?.humidity ?? 50,
                uvIndex: viewModel.selectedWeatherSnapshot?.uvIndex ?? 0
            )

            // (c) RW-26 (notes §7.1): the line carries the actual
            // temperature ONLY.
            Text(HomeHeaderCopy.actualLine(
                formattedTemperature: viewModel.temperatureUnit.format(viewModel.currentTemperature)
            ))
            .font(.appSecondary)
            .foregroundColor(AppTheme.inkSecondary)

            // GEN-5 (RW-28 item 12): the duplicate date/time pill
            // row that sat here is REMOVED — one moment system
            // only (the headline line above is the entry; the
            // glance band's Change is the other).
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
    }

    /// GEN-4 (day-chart wave, Concept 1): the headline location
    /// line with its second entry into time-picking. The line
    /// still reads "{Place} · {Moment}" in the line's own
    /// typography, but the MOMENT segment is now tappable —
    /// pine, bold, chevron — and opens the Choose-a-Time sheet
    /// (the same sheet the pill's time segment and the glance
    /// band's Change button open). The place segment stays
    /// non-interactive. In the changed-time state only, the way
    /// back rides in the same line, immediately after the
    /// moment in its dot-separated rhythm: a compact pine
    /// refresh + "Now" (~13pt against the line's ~15.5pt, same
    /// baseline) performing exactly the existing reset-to-now.
    /// It is an ADDITION — the pill row below keeps its own Now
    /// segment and behaviour.
    private var headlineLocationLine: some View {
        HStack(spacing: 0) {
            // GEN-5 (RW-29 FA-47): layout priority belongs to
            // the interactive segments — the MOMENT and the
            // "↻ Now" way-back keep their full width at every
            // place-name length, and the PLACE truncates first.
            // Build 25 let a long place name squeeze the moment
            // segment (the row's actual control) off the line.
            Text("\(HomeHeaderCopy.placeSegment(place: viewModel.placeDisplayName)) · ")
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)
                .layoutPriority(0)

            Button { showTimePicker = true } label: {
                HStack(spacing: 3) {
                    Text(sheetMoment)
                    Text("›")
                }
                // GEN-6 (RW-54, iOS half): the design specifies
                // the moment in pine BOLD — the line's inherited
                // semibold rendered it a weight light.
                .font(.system(size: 15.5, weight: .bold))
                .foregroundColor(AppTheme.pinePayload)
            }
            .buttonStyle(.plain)
            .layoutPriority(1)
            .accessibilityLabel("Choose a time: \(sheetMoment)")

            if HeadlineNowReturn.isShown(isViewingNow: isViewingNow) {
                Text(" · ")
                    .foregroundColor(AppTheme.inkPrimary)
                    .layoutPriority(1)
                Button { viewModel.resetToNow() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.counterclockwise")
                        Text("Now")
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(AppTheme.pinePayload)
                }
                .layoutPriority(1)
                .buttonStyle(.plain)
                .accessibilityLabel("Back to now")
            }
        }
        .font(.system(size: 15.5, weight: .semibold))
        .lineLimit(1)
    }

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
                onChange: { showTimePicker = true }
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
    /// a clock reading ("9 p.m.") for a picked hour today,
    /// "Tomorrow evening"-family readings beyond. Never a stamp.
    ///
    /// GEN-5 (Addendum 8): the reading is CONSTRUCTED as the
    /// structural MomentReading — and the beyond-tomorrow case
    /// now renders as a weekday daypart ("Friday evening")
    /// where the gen-5 first pass fell back to the sheet's
    /// stamped form ("Nov 7 9:00 PM"), which C-7 forbids in
    /// narrations.
    private var entryMomentReading: MomentReading {
        if isViewingNow { return .now }
        let calendar = Calendar.current
        let hour = viewModel.selectedHour
        if calendar.isDateInToday(viewModel.selectedDate) {
            let display = hour % 12 == 0 ? 12 : hour % 12
            return .clock(hour12: display, isPM: hour >= 12)
        }
        let part = MomentReading.DayPart.forHour(hour)
        if calendar.isDateInTomorrow(viewModel.selectedDate) {
            return .daypart(dayWord: "Tomorrow", part: part)
        }
        let weekday = DateFormatter()
        weekday.dateFormat = "EEEE"
        return .daypart(dayWord: weekday.string(from: viewModel.selectedDate), part: part)
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
        showSettings || showDatePicker || showTimePicker || showShop
            || showLocationPicker || selectedWeatherDetail != nil
            || showCollection || showFeedbackSheet
            // GEN-6 (RW-61): the looks library is a home sheet too.
            || showLooksLibrary
    }

    /// The mini bar dock: the session's entry drives the bar —
    /// absent pre-entry, narrating once the session's first
    /// generation lands.
    @ViewBuilder private var miniBarDock: some View {
        // GEN-3 (the presentation half, superseding W-15's
        // by-date key): the dock presents the CURRENT SESSION's
        // entry — the entry's own place is what the narration
        // names, so the element survives the user browsing
        // another place, and it can never announce a place the
        // session has moved past, because the session's one
        // entry always holds the last settled place. (W-15's
        // history: the original viewed-place lookup returned nil
        // for the placeholder-filed entry forever, and for any
        // entry whose place differed from the view; gen-2 keyed
        // the lookup by date instead; the session rule keys it
        // by session.)
        let todays = collectionModel.sessionEntry()
        // WS-4 + Y-5: the state derives EVERYTHING from the one
        // entry value above — both narration payloads (simple
        // place for "Added"; per C-7 the entry's own update
        // payload — place or moment reading, never garments) AND
        // the narration kind, which the entry carries on itself
        // (`wasUpdatedInSession`, committed in the same store
        // write as the content). There is no second state holder
        // for the flag to lag behind in: build 21 read the kind
        // from separate model state and the payload from an
        // older entry, and the card disagreed with itself for
        // the rest of the session.
        // GEN-5 (RW-29 FA-3): the moment below is the ENTRY's
        // recorded momentReading — never the live sheetMoment,
        // which browsing would rewrite into stamped forms.
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: todays,
            moment: todays?.momentReading ?? .now,
            wasUpdatedThisSession: todays?.wasUpdatedInSession ?? false
        )
        if state != .preAdd {
            WearIveRunMiniBarView(
                state: state,
                entry: todays,
                formattedFeelsLike: todays.map {
                    viewModel.temperatureUnit.formatFromFahrenheit($0.feelsLikeF)
                } ?? "",
                cueCount: collectionModel.cueCount,
                isAtEndOfScroll: isAtEndOfScroll,
                isDimmedBySheet: anyHomeSheetPresented,
                onOpenCollection: { showCollection = true },
                onTrash: {
                    // Trash deletes the PRESENTED entry — the
                    // session's own entry — not an entry at the
                    // viewed place; the two differ once the user
                    // has browsed elsewhere.
                    if todays != nil {
                        collectionModel.removeSessionEntry()
                    }
                }
            )
        }
    }

    /// Automatic entry creation (checklist §C; GEN-3 session
    /// rule): the session's first generation settles its entry;
    /// every later settle in the session — a new place, new
    /// weather — REPLACES that entry in place (the saved entry
    /// is the session's LAST settled version; updates never
    /// accumulate). Only the viewed-now day records — browsing
    /// another day never writes; the entry carries the current
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
        // GEN-5 (RW-29 FA-2, design-spec §8.2): the settled entry
        // is the NOW entry — browsing or confirming another hour
        // never moves it. Build 25 guarded only the date, so a
        // confirmed 9 p.m. overwrote today's entry with the
        // future hour's kit, feels-like, and hero and narrated
        // it as an update. The recording happens only in the
        // location's Now state (Android's recording effect has
        // the same guard); a session that only ever settles at
        // changed hours records nothing, which is the settled
        // session model's other side, not an accident.
        guard viewModel.isViewingLocationNow else { return }
        guard Calendar.current.isDateInToday(viewModel.selectedDate) else { return }
        // GEN-6 (RW-42 / DA-09): never record while a fetch is
        // in flight. A manual place switch names the new place
        // synchronously (setManualLocation) and fetches its
        // weather asynchronously — so the place trigger used to
        // file the OUTGOING place's feels-like, kit, and hero
        // under the INCOMING place's name, classify it a place
        // update, and ANNOUNCE it ("Update to Miami, FL, Now")
        // while the expanded card still showed that stale
        // outfit — matching neither adjacent recommendation,
        // exactly the recording's bar/card split. The
        // isLoading re-offer below records the settle once the
        // fetch has landed, from the one solved selection, so
        // the announced state and the card are the same entry.
        guard !viewModel.isLoading else { return }
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
            selectedWeatherDetail = .condition(viewModel.currentCondition)
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
            Text("\(recommendation.allItems.count) items for your run")
                .font(.appSectionTitle)
                .foregroundColor(AppTheme.inkPrimary)
                .padding(.horizontal, 16)

            TemperatureBracketBadge(bracket: recommendation.temperatureBracket, unit: viewModel.temperatureUnit)
                .padding(.horizontal, 16)

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
    private var weatherDetailPresented: Binding<Bool> {
        Binding(
            get: { selectedWeatherDetail != nil },
            set: { presented in
                if !presented { selectedWeatherDetail = nil }
            }
        )
    }

    /// Context lines for the weather detail's sidebar: where and
    /// when the numbers on show belong to.
    private var weatherContextLines: [String] {
        [
            viewModel.placeDisplayName,
            navDateString,
            "\(viewModel.temperatureUnit.format(viewModel.currentTemperature)) · Feels like \(viewModel.temperatureUnit.format(viewModel.currentFeelsLike))",
            recommendation.temperatureBracket.description
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

    @ViewBuilder
    private func heroSection(heightMode: HeroImageView.HeightMode = .containerFraction) -> some View {
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
            heroSkeleton(heightMode: heightMode)
        } else if viewModel.heroImageUrl == nil
            && viewModel.fallbackImageUrl == nil {
            heroSkeleton(heightMode: heightMode)
        } else {
            heroImageView(heightMode: heightMode)
        }
    }

    /// The hero zone's loading skeleton (frame 46's treatment at
    /// hero scale): a sheet-toned block in the hero's own frame —
    /// the same container fraction / fill mechanics as the photo,
    /// so the sheet composes against identical geometry and
    /// nothing moves when the photo lands.
    @ViewBuilder
    private func heroSkeleton(heightMode: HeroImageView.HeightMode) -> some View {
        let block = Rectangle()
            .fill(AppTheme.cardOnSheet)
            .overlay {
                ProgressView()
                    .tint(AppTheme.inkSecondary)
            }
        if heightMode == .fill {
            block.frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            block.containerRelativeFrame(.vertical) { length, _ in
                length * AppTheme.heroHeightRatio
            }
        }
    }

    private func heroImageView(heightMode: HeroImageView.HeightMode = .containerFraction) -> some View {
        HeroImageView(
            locationName: viewModel.placeDisplayName,
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
            onSettingsTapped: { showSettings = true },
            // No onShareTapped: the hero's top controls are off
            // (showsTopControls below) — and since WS-6 (T-4)
            // there is no home share at all; sharing lives in
            // the collection (Poster / Kit Card).
            onLocationTapped: { showLocationPicker = true },
            onDateTapped: { showDatePicker = true },
            onTimeTapped: { showTimePicker = true },
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
            // GEN-3 (WS-A(2)): the hero pill, derived from the
            // REQUESTED cell's facts only (see heroPillState).
            pillState: heroPillState,
            onPillGetUnlimited: { showHeroPillPaywall = true },
            heightMode: heightMode,
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
            isScaffoldServe: viewModel.currentPersonalServeIsScaffold
        )
    }

    // MARK: - RW-14 Personal Section

    /// The "Make It Me" surfaces on the run screen, in spec order:
    /// (GEN-3: the making/scaffold states moved ONTO the hero as
    /// the one hero pill — see heroPillState; this section keeps)
    /// the failed line with
    /// its retry when the server reports the look as failed (spec
    /// §7 G5/G6, RW-16.16), and the free-tier upsell card when this
    /// cell is the one that exhausted the allowance (spec §6 U1).
    /// Both status lines are driven by the server's per-cell look
    /// state (PersonalHeroStore.lookStates), so they render whoever
    /// requested the generation — the save flow included — and they
    /// survive navigation away and back. Ready renders nothing: the
    /// personalized photo on screen is itself the state.
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
                // Spec §7 G5/G6 (RW-16.16): the durable failed line
                // and its user-initiated retry. The retry goes
                // through the store's request path; the hero reload
                // re-attaches the view model's awaiter so a landed
                // replacement swaps in.
                VStack(spacing: 4) {
                    Text(MakeItMeCopy.lookFailed)
                        .font(.appSecondary.weight(.semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                        .multilineTextAlignment(.center)
                    Button(MakeItMeCopy.lookRetry) {
                        personalStore.retryLook(for: cell)
                        viewModel.refreshHero()
                    }
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                    // GEN-6 (RW-61): the library door, one tap from
                    // where the failed cell is encountered (frame
                    // 20's door wording). The failure itself is
                    // never badged on home — this quiet door is the
                    // whole home footprint.
                    Button("See all your looks") {
                        showLooksLibrary = true
                    }
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal, 16)
            case .ready, .none:
                EmptyView()
            }

            if personalStore.shouldShowUpsell(currentCell: cell.combinationId) {
                // The card carries its own horizontal padding.
                MakeItMeUpsellCard(currentCell: cell.combinationId)
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
            // Section header — Shop lives in the toolbar now
            // (design record §5.2); the teal pill is gone.
            // GEN-5 (RW-29 FA-55): the header is the settled
            // count line ("N items for your run", Android's
            // form), not the generic "Recommended Outfit" —
            // both layouts carry it.
            HStack {
                Text("\(recommendation.allItems.count) items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)

                Spacer()
            }
            .padding(.horizontal, 16)

            // Temperature bracket badge
            TemperatureBracketBadge(bracket: recommendation.temperatureBracket, unit: viewModel.temperatureUnit)
                .padding(.horizontal, 16)
                .staggeredAnimation(index: 0, isVisible: hasAppeared)

            // All clothing items in layer order (v4.1)
            ForEach(Array(recommendation.allItems.enumerated()), id: \.element.id) { index, item in
                DarkClothingItemCard(item: item, gender: viewModel.genderPreference)
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

    private var thumbnailURL: URL? {
        ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: gender)
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
                // Background
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppTheme.inkPrimary.opacity(0.08))

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
                    .foregroundColor(AppTheme.inkSecondary)
                    // GEN-6 (RW-54, iOS half): the design settles
                    // a ONE-line outfit-row note.
                    .lineLimit(1)
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
                .fill(AppTheme.temperatureColor(fahrenheit: temperatureForBracket).opacity(0.8))
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
