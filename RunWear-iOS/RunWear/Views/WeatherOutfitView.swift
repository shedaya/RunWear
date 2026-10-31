import SwiftUI
import StoreKit // for the \.requestReview environment action (R-15)

struct WeatherOutfitView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
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
                // Dark background
                AppTheme.darkBackground
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
            .navigationTitle(viewModel.locationName)
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
            .toolbarBackground(AppTheme.darkBackground, for: .navigationBar)
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
        }
        // Neutral two-door ask: either door closes it for the season;
        // the positive door hands off to the system review sheet.
        .alert("Enjoying RunWear?", isPresented: $showRatingPrompt) {
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
                                .frame(height: geo.size.height * 0.62)
                        }
                        .padding(.bottom, 24)
                    }
                } secondary: {
                    VStack(spacing: 0) {
                        ScrollView {
                            VStack(spacing: 0) {
                                weatherPillsSection
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
                        heroSection()
                        weatherPillsSection
                            .padding(.top, -30)
                            .zIndex(1)
                        outfitGridSection
                            .padding(.top, 24)
                        personalSection
                            .padding(.top, 24)
                        Spacer(minLength: 40)
                    }
                }
                .refreshable { onRefresh() }
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        heroSection()
                        weatherPillsSection
                            .padding(.top, -30)
                            .zIndex(1)
                        outfitSection
                            .padding(.top, 24)
                        personalSection
                            .padding(.top, 24)
                        Spacer(minLength: 40)
                    }
                    .frame(maxWidth: geo.size.width >= 600 ? 640 : .infinity)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .refreshable { onRefresh() }
            }
        }
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
                    .foregroundColor(.white)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.appFootnote.weight(.semibold))
                    .foregroundColor(.white.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(AppTheme.darkBackground)
        }
        .buttonStyle(.plain)
    }

    /// Portrait-regular outfit: the same cards in a two-column grid.
    private var outfitGridSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Recommended Outfit")
                .font(.appSectionTitle)
                .foregroundColor(.white)
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
            viewModel.locationName,
            navDateString,
            "\(viewModel.temperatureUnit.format(viewModel.currentTemperature)) · Feels like \(viewModel.temperatureUnit.format(viewModel.currentFeelsLike))",
            recommendation.temperatureBracket.description
        ]
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
            locationName: viewModel.locationName,
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
        .background(
            LinearGradient(
                colors: [AppTheme.darkBackground.opacity(0.8), AppTheme.darkBackground],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    // MARK: - RW-14 Personal Section

    /// The "Make it me" surfaces on the run screen, in spec order:
    /// the first-look generation progress line while a
    /// user-initiated generation for THIS cell is in flight (spec
    /// §5 G1/G2 — quiet text under the hero, never a spinner over
    /// the photo), and the free-tier upsell card when this cell is
    /// the one that exhausted the allowance (spec §6 U1). Nothing
    /// renders in any other state — notably not for gate-rejected
    /// cells, which sit on the pool photo indefinitely in silence.
    @ViewBuilder private var personalSection: some View {
        if let cell = viewModel.currentPersonalCombination {
            if personalStore.userInitiatedPending.contains(cell.combinationId) {
                VStack(spacing: 4) {
                    HStack(spacing: 8) {
                        ProgressView()
                            .tint(.white.opacity(0.7))
                        // Spec §7 G1, verbatim.
                        Text("Making this outfit yours…")
                            .font(.appSecondary.weight(.semibold))
                            .foregroundColor(.white)
                    }
                    // Spec §7 G2, verbatim.
                    Text("About 10 seconds. Your recommendation is ready either way — the standard photo stays up until yours is done.")
                        .font(.appFootnote)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal, 16)
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
                    .foregroundColor(.white)

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
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .staggeredAnimation(index: 1 + recommendation.allItems.count, isVisible: hasAppeared)

                ForEach(Array(recommendation.tips.enumerated()), id: \.offset) { index, tip in
                    Text(tip)
                        .font(.appSecondary)
                        .foregroundColor(.white.opacity(0.8))
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
                .foregroundColor(AppTheme.primaryColor)

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
                    .fill(Color.white.opacity(0.1))

                // Emoji icon (shown as fallback)
                if thumbnailURL == nil {
                    Image(systemName: item.icon)
                        .font(.title)
                        .foregroundColor(.white)
                }

                // AI-generated thumbnail
                if let url = thumbnailURL {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .empty:
                            // Loading state - show icon
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(.white)
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .failure:
                            // Error state - show icon
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(.white)
                        @unknown default:
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(.white)
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
                        .foregroundColor(.white.opacity(0.5))
                }
                Text(item.name)
                    .font(.appItemName)
                    .foregroundColor(.white)
                Text(item.description)
                    .font(.appFootnote)
                    .foregroundColor(.white.opacity(0.6))
                    .lineLimit(2)
            }

            Spacer()

            // External link icon (matching PWA v3.12)
            Image(systemName: "arrow.up.right")
                .font(.appFootnote.weight(.semibold))
                .foregroundColor(.white.opacity(0.5))
                .frame(width: 44, height: 44)
        }
        .padding(12)
        // Flat content card (AppTheme tokens) — deliberately NOT glass:
        // dense text stays off glass for legibility.
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppTheme.cardFillOnDark)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(AppTheme.cardStrokeOnDark, lineWidth: 1)
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
        .background(Color(UIColor.secondarySystemBackground))
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
