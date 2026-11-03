import SwiftUI
import StoreKit // for the \.requestReview environment action (R-15)

struct WeatherOutfitView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // RW-25 sheet numeral: ~80pt, Dynamic-Type-scaled relative to
    // .largeTitle (the RW-6.3 pattern the hero numeral used), capped
    // by the text's dynamicTypeSize limit at the call site.
    @ScaledMetric(relativeTo: .largeTitle) private var sheetNumeralSize: CGFloat = 80
    /// RW-26 "Wear I've Run": the collection model — today's entry
    /// drives the mini bar dock; the store behind it is the
    /// collection itself.
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

    // The hero photo the share payload carries (RW-6.5): the AI
    // photo when there is one, else the fallback — the image the
    // hero itself shows. Loaded as an Image because ShareLink
    // vends an Image, not a URL; nil until it arrives (the share
    // button falls back to text-only meanwhile, never blocked).
    @State private var heroShareImage: Image?

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
                            .background(Capsule().fill(Color.black.opacity(0.75)))
                            .padding(.bottom, 32)
                    }
                    .ignoresSafeArea()
                    .transition(.opacity)
                }
            }
            // RW-25: the "Wear I've Run" mini bar dock — attached
            // to the home scroll's container; renders only when the
            // state is not .preAdd (never, in this build).
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
            .toolbarTitleMenu {
                Button {
                    showLocationPicker = true
                } label: {
                    Label("Set Location", systemImage: "location")
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    shareButton
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
                .presentationDetents([.medium, .large])
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
                TimePickerSheet(
                    selectedHour: $viewModel.selectedHour,
                    isPresented: $showTimePicker
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .onChange(of: viewModel.selectedHour) { _, newHour in
                    viewModel.updateSelectedTime(date: viewModel.selectedDate, hour: newHour)
                }
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
                .presentationDetents([.medium, .large])
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
        }
        // A re-settled recommendation (refresh, hour change back to
        // now, outfit change) updates today's entry — the saved
        // entry is the day's last settled version.
        .onChange(of: recommendation.allItems.map(\.id)) { _, _ in
            recordTodaysEntry()
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
            FeedbackSheet()
        }
        // Neutral THREE-door rating moment (frame 49): feedback
        // first, then the rating door, then not-now. Any door
        // closes it for the season; the rating door hands off to
        // the system review sheet.
        .alert("Enjoying RunWear?", isPresented: $showRatingPrompt) {
            Button("Send Feedback") {
                ratingTracker.recordPromptShown()
                showFeedbackSheet = true
            }
            Button("Rate RunWear") {
                ratingTracker.recordPromptShown()
                requestReview()
            }
            Button("Not Now", role: .cancel) {
                ratingTracker.recordPromptShown()
            }
        } message: {
            Text("A quick rating helps other runners find their outfit.")
        }
        .task(id: heroSharePhotoURL) {
            heroShareImage = await Self.loadShareImage(from: heroSharePhotoURL)
        }
    }

    // MARK: - Share (RW-6.5)

    /// Native ShareLink — it anchors its own popover on iPad and
    /// stacks over presented sheets, the two failure modes of the
    /// hand-rolled UIActivityViewController it replaces (which
    /// presented from the root view controller with no anchor — a
    /// documented iPad crash risk on this iPad-shipping target).
    /// Payload: the hero photo (once loaded) + the aligned share
    /// text; text-only until the photo arrives.
    @ViewBuilder private var shareButton: some View {
        if let text = viewModel.shareText {
            if let heroShareImage {
                ShareLink(
                    item: heroShareImage,
                    subject: Text("RunWear Outfit"),
                    message: Text(text),
                    preview: SharePreview("RunWear Outfit", image: heroShareImage)
                ) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
            } else {
                ShareLink(item: text) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
            }
        }
    }

    /// The hero photo's URL in the hero's own priority: the AI
    /// photo, else the bundled fallback — the image on screen.
    private var heroSharePhotoURL: URL? {
        let string = viewModel.heroImageUrl ?? viewModel.fallbackImageUrl ?? defaultFallbackUrl
        return URL(string: string)
    }

    private static func loadShareImage(from url: URL?) async -> Image? {
        guard let url,
              let (data, _) = try? await URLSession.shared.data(from: url),
              let uiImage = UIImage(data: data) else {
            return nil
        }
        return Image(uiImage: uiImage)
    }

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

    /// The sheet header block, in order (notes §7.2): (i) "Feels
    /// like" label, (ii) location line, (iii) hero number +
    /// temperature chip, (iv) "Actual NN°" line, (v) weather
    /// strip — then the flat date/time pills. Used by the stacked
    /// sheet and, flat, by the Duo trailing column.
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
                Text(HomeHeaderCopy.locationLine(
                    place: viewModel.placeDisplayName,
                    moment: sheetMoment
                ))
                .font(.system(size: 15.5, weight: .semibold))
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)
            }

            // (b) Hero number (ink, never temperature-colored) +
            // temperature chip (filled dot + bracket label).
            HStack(alignment: .center, spacing: 14) {
                Text(viewModel.temperatureUnit.format(viewModel.currentFeelsLike))
                    .font(.system(size: sheetNumeralSize, weight: .semibold))
                    .monospacedDigit()
                    .foregroundColor(AppTheme.inkPrimary)
                    .dynamicTypeSize(...DynamicTypeSize.accessibility1)

                HStack(spacing: 6) {
                    Circle()
                        .fill(AppTheme.temperatureColor(for: viewModel.currentTempBracket))
                        .frame(width: 8, height: 8)
                    Text(viewModel.currentTempBracket.rawValue)
                        .font(.appEyebrow)
                        .foregroundColor(AppTheme.inkPrimary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(AppTheme.cardOnSheet))

                Spacer(minLength: 0)
            }

            // (c) RW-26 (notes §7.1): the line carries the actual
            // temperature ONLY — condition/wind/humidity live in
            // the weather strip beneath it, nowhere else.
            Text(HomeHeaderCopy.actualLine(
                formattedTemperature: viewModel.temperatureUnit.format(viewModel.currentTemperature)
            ))
            .font(.appSecondary)
            .foregroundColor(AppTheme.inkSecondary)

            // (d) Date/time pills, flat on the sheet.
            sheetDateTimePills

            // (e) Weather pills row (behavior unchanged). The row
            // carries its own 16pt content inset, so it cancels the
            // header's inset and spans the sheet edge-to-edge like
            // it did above the outfit before RW-25.
            weatherPillsSection
                .padding(.horizontal, -16)
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
    }

    /// "Now" when viewing now (sentence case — the location line
    /// is a sentence-case piece, notes §7.2); otherwise the same
    /// date + hour strings the hero's pills used.
    private var sheetMoment: String {
        isViewingNow ? "Now" : "\(navDateString) \(heroHourString)"
    }

    private var heroHourString: String {
        let hour = viewModel.selectedHour
        let suffix = hour >= 12 ? "PM" : "AM"
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return "\(displayHour):00 \(suffix)"
    }

    private var isViewingNow: Bool {
        let calendar = Calendar.current
        let now = Date()
        return calendar.isDateInToday(viewModel.selectedDate)
            && viewModel.selectedHour == calendar.component(.hour, from: now)
    }

    /// The hero's date/time pills, restyled flat on the sheet:
    /// sheet-tonal fill + ink text; the Now pill's selected state
    /// uses the pine tint. Same actions as the hero pills had.
    private var sheetDateTimePills: some View {
        HStack(spacing: 12) {
            Button { showDatePicker = true } label: {
                HStack(spacing: 6) {
                    Image(systemName: "calendar").font(.appPillLabel)
                    Text(navDateString).font(.appPillLabel)
                }
                .flatSheetPill()
            }
            Button { showTimePicker = true } label: {
                HStack(spacing: 6) {
                    Image(systemName: "clock").font(.appPillLabel)
                    Text(heroHourString).font(.appPillLabel).monospacedDigit()
                }
                .flatSheetPill()
            }
            if !isViewingNow {
                Button { viewModel.resetToNow() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.counterclockwise").font(.appPillLabel)
                        Text("Now").font(.appPillLabel)
                    }
                    .flatSheetPill(isSelected: true)
                }
            }
        }
    }

    /// The mini bar dock: today's entry drives the bar — absent
    /// pre-entry, narrating once the day's first generation lands.
    @ViewBuilder private var miniBarDock: some View {
        let todays = collectionModel.todaysEntry(placeName: viewModel.placeDisplayName)
        // WS-4: the state derives both narration payloads from
        // the entry itself (simple place for "Added", first kit
        // item for "Update to") — the view supplies no payload
        // string of its own.
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: todays,
            moment: sheetMoment,
            wasUpdatedThisSession: collectionModel.todaysEntryWasUpdated
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
                onOpenCollection: { showCollection = true },
                onTrash: {
                    collectionModel.removeToday(placeName: viewModel.placeDisplayName)
                }
            )
        }
    }

    /// Automatic entry creation (checklist §C): the day's first
    /// generation settles an entry; later settles the same day
    /// replace it (the saved entry is the day's LAST settled
    /// version). Only the viewed-now day at the current place
    /// records — browsing another day or hour never writes.
    private func recordTodaysEntry() {
        guard Calendar.current.isDateInToday(viewModel.selectedDate) else { return }
        let snapshot = viewModel.selectedWeatherSnapshot
        let feelsLikeF = snapshot?.feelsLikeFahrenheit
            ?? (weather.currentWeather.temperatureFahrenheit)
        let actualF = snapshot?.temperatureFahrenheit
            ?? weather.currentWeather.temperatureFahrenheit
        let entry = WearIveRunEntry(
            dayStart: Calendar.current.startOfDay(for: viewModel.selectedDate),
            placeName: viewModel.placeDisplayName,
            feelsLikeF: feelsLikeF,
            actualF: actualF,
            condition: viewModel.currentCondition.shortDescription,
            itemNames: recommendation.allItems.map(\.name),
            itemKeys: recommendation.allItems.map(\.key),
            heroImageReference: viewModel.heroImageUrl
                ?? viewModel.fallbackImageUrl
                ?? defaultFallbackUrl
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
            Text("Recommended Outfit")
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

    /// The five metric details the detail sheet tabs between —
    /// the same values the pills below present.
    private var allWeatherDetails: [WeatherDetailType] {
        let snapshot = viewModel.selectedWeatherSnapshot
        return [
            .condition(viewModel.currentCondition),
            .wind(
                speed: snapshot?.windSpeed ?? weather.currentWeather.windspeed,
                direction: snapshot?.windDirectionCompass ?? "N",
                gusts: snapshot?.windGusts ?? weather.currentWeather.windspeed
            ),
            .humidity(percentage: snapshot?.humidity ?? 50),
            .precipitation(percentage: snapshot?.precipitationProbability ?? 0),
            .uvIndex(value: snapshot?.uvIndex ?? 0)
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

    private func heroSection(heightMode: HeroImageView.HeightMode = .containerFraction) -> some View {
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
            fallbackImageUrl: viewModel.fallbackImageUrl ?? defaultFallbackUrl,
            aiImageUrl: viewModel.heroImageUrl,
            onSettingsTapped: { showSettings = true },
            // No onShareTapped: the hero's top controls are off
            // (showsTopControls below) — sharing lives in the
            // toolbar as a ShareLink (RW-6.5).
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
            heightMode: heightMode,
            // The navigation container carries the chrome now:
            // location is the title, Settings/Share are toolbar
            // items — no floating pills on the hero.
            showsTopControls: false
        )
    }

    private var defaultFallbackUrl: String {
        FallbackImageProvider.shared.getImageURL(
            temp: .MILD,
            weather: .CLEAR,
            size: .iOS
        )
    }

    // MARK: - Weather Pills Section

    private var weatherPillsSection: some View {
        WeatherPillsRow(
            condition: viewModel.currentCondition,
            windSpeed: viewModel.selectedWeatherSnapshot?.windSpeed ?? weather.currentWeather.windspeed,
            windDirection: viewModel.selectedWeatherSnapshot?.windDirectionCompass ?? "N",
            humidity: viewModel.selectedWeatherSnapshot?.humidity ?? 50,
            precipitationProbability: viewModel.selectedWeatherSnapshot?.precipitationProbability ?? 0,
            uvIndex: viewModel.selectedWeatherSnapshot?.uvIndex ?? 0,
            onConditionTapped: {
                selectedWeatherDetail = .condition(viewModel.currentCondition)
            },
            onWindTapped: {
                let snapshot = viewModel.selectedWeatherSnapshot
                selectedWeatherDetail = .wind(
                    speed: snapshot?.windSpeed ?? weather.currentWeather.windspeed,
                    direction: snapshot?.windDirectionCompass ?? "N",
                    gusts: snapshot?.windGusts ?? weather.currentWeather.windspeed
                )
            },
            onHumidityTapped: {
                selectedWeatherDetail = .humidity(percentage: viewModel.selectedWeatherSnapshot?.humidity ?? 50)
            },
            onPrecipitationTapped: {
                selectedWeatherDetail = .precipitation(percentage: viewModel.selectedWeatherSnapshot?.precipitationProbability ?? 0)
            },
            onUVTapped: {
                selectedWeatherDetail = .uvIndex(value: viewModel.selectedWeatherSnapshot?.uvIndex ?? 0)
            }
        )
        .padding(.vertical, 16)
        // RW-25: no background — the row sits on the sheet now; the
        // gradient existed only to blend the row into the hero.
    }

    // MARK: - RW-14 Personal Section

    /// The "Make it me" surfaces on the run screen, in spec order:
    /// the generation progress line while the SERVER reports this
    /// cell's look as making (spec §5 G1/G2 — quiet text under the
    /// hero, never a spinner over the photo), the failed line with
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
                // RW-26 scaffold policy (§9 silence — informational
                // only, no upsell, no meter): when a personal photo
                // is already on screen while this cell makes, that
                // photo is the user's own banked scaffold, and it
                // carries the honest badge instead of a spinner.
                if PersonalServePolicy.presentation(
                    hasPersonalPhoto: viewModel.currentPersonalHeroId != nil,
                    lookState: .making
                ) == .scaffold(badge: PersonalServePolicy.scaffoldBadge) {
                    Text(PersonalServePolicy.scaffoldBadge)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.horizontal, 16)
                } else {
                    VStack(spacing: 4) {
                        HStack(spacing: 8) {
                            ProgressView()
                                .tint(AppTheme.inkSecondary)
                            // Spec §7 G1, verbatim.
                            Text(MakeItMeCopy.progressTitle)
                                .font(.appSecondary.weight(.semibold))
                                .foregroundColor(AppTheme.inkPrimary)
                        }
                        // Spec §7 G2, verbatim (as amended Nov 1, 2026).
                        Text(MakeItMeCopy.progressSubline)
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.horizontal, 16)
                }
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

    // MARK: - Outfit Section

    private var outfitSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Section header — Shop lives in the toolbar now
            // (design record §5.2); the teal pill is gone.
            HStack {
                Text("Recommended Outfit")
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

            // Tips (v4.1)
            if !recommendation.tips.isEmpty {
                Text("Tips")
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkSecondary)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .staggeredAnimation(index: 1 + recommendation.allItems.count, isVisible: hasAppeared)

                ForEach(Array(recommendation.tips.enumerated()), id: \.offset) { index, tip in
                    Text(tip)
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                        .staggeredAnimation(index: 2 + recommendation.allItems.count + index, isVisible: hasAppeared)
                }
            }
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
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.85))
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
                    .lineLimit(2)
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

struct GenderToggle: View {
    let selected: GenderPreference
    let onSelect: (GenderPreference) -> Void

    var body: some View {
        HStack(spacing: 2) {
            ForEach(GenderPreference.allCases, id: \.self) { preference in
                GenderOption(
                    preference: preference,
                    isSelected: selected == preference,
                    onTap: { onSelect(preference) }
                )
            }
        }
        .padding(4)
        // WS-8: the token card fill for this (currently call-site-
        // less, legacy) segmented container — the system
        // secondarySystemBackground is an off-table ground.
        .background(AppTheme.cardOnSheet)
        .cornerRadius(20)
    }
}

struct GenderOption: View {
    let preference: GenderPreference
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(preference.displayIcon)
                .font(.system(size: preference == .unisex ? 12 : 16))
                .frame(width: 36, height: 32)
                .background(isSelected ? AppTheme.primaryColor : Color.clear)
                .cornerRadius(16)
                .opacity(isSelected ? 1.0 : 0.45)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

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
