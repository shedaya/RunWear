import SwiftUI

struct HeroImageView: View {
    let locationName: String
    /// The hero headline number: the RAW measured feels-like, in the
    /// user's selected unit — the contract Android and the PWA ship
    /// (their 96pt number is feelsLike / effectiveTemperature, under
    /// a "FEELS LIKE" caption). This view previously received the
    /// ACTUAL temperature here and the sub-line below showed a
    /// comfort-adjusted "feels like" that existed on neither
    /// reference surface.
    let temperature: Double
    /// The raw ACTUAL temperature for the "Actual:" sub-line (the
    /// reference surfaces' second line), same unit as `temperature`.
    let actualTemperature: Double
    /// The unit `temperature`/`actualTemperature` are expressed in —
    /// rendered WITH the numbers. The hero previously printed a bare
    /// "12°" with no unit anywhere while the outfit band below
    /// carried a hard-coded "°F" range: read in the user's Celsius,
    /// the two told different stories on one screen (field finding
    /// 2026-10-31). Android's hero carries the symbol on both lines.
    var temperatureUnit: TemperatureUnit = .fahrenheit
    let weatherCondition: WeatherCondition
    let date: Date
    let hour: Int
    let tempBracket: HeroTempBracket

    // Image URLs
    let fallbackImageUrl: String
    let aiImageUrl: String?

    // Callbacks
    var onSettingsTapped: () -> Void = {}
    var onShareTapped: () -> Void = {}
    var onLocationTapped: () -> Void = {}
    var onDateTapped: () -> Void = {}
    var onTimeTapped: () -> Void = {}
    var onNowTapped: () -> Void = {}

    // Hero-photo flag (2026-10-29, parity with Android): the exact
    // photo on screen and the callback for when the user picks a
    // reason. A nil target renders no chip — there is no resolved
    // photo to flag yet.
    var flagTarget: HeroFlagTarget? = nil
    var onFlagHero: (HeroFlagReason) -> Void = { _ in }

    // Personal-hero flag (RW-14 acceptance (19), spec §4): when
    // the photo on screen is a PERSONALIZED hero, the same chip
    // offers the personal menu — headed by "This doesn't look
    // like me" — and a pick routes to the backend's flag_hero
    // action instead of the pool insert. A non-nil personal
    // target takes precedence over flagTarget: both describe the
    // URL on screen then, but only the personal target names the
    // photo that's actually up.
    var personalFlagTarget: PersonalHeroFlagTarget? = nil
    var onFlagPersonalHero: (PersonalHeroFlagReason) -> Void = { _ in }

    /// How the hero's height is derived (design record §5.2: no
    /// UIScreen.main anywhere — the container decides).
    enum HeightMode {
        /// Fraction of the container's height (the original
        /// full-screen layout, where the container is the screen).
        case containerFraction
        /// Fill the height the parent proposes (pane layouts size
        /// the hero from their own geometry).
        case fill
    }
    var heightMode: HeightMode = .containerFraction

    /// The floating glass pills (location / share / settings) are
    /// chrome, and chrome belongs to the container: when Home sits
    /// in a NavigationStack the location is the nav title and
    /// Settings/Share are toolbar items, so the hero hides its
    /// pills. The date/time pills stay — they are content.
    var showsTopControls: Bool = true

    // State for crossfade
    @State private var showAIImage = false
    @State private var aiImageLoaded = false

    // State for the flag chip: the reason sheet's presentation, and
    // the URL of the photo already flagged in this view (the chip
    // goes inert for that photo; a different photo re-arms it).
    @State private var showFlagSheet = false
    @State private var flaggedImageUrl: String?

    // RW-6.6: Reduce Motion gates the crossfade and the "Now" pill's
    // scale transition — content swaps instantly instead.
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // RW-6.3 display-numeral exception: the hero number scales with
    // Dynamic Type relative to .largeTitle, capped so the layout holds
    // at accessibility sizes.
    @ScaledMetric(relativeTo: .largeTitle) private var heroNumeralSize: CGFloat = 96

    var body: some View {
        Group {
            if heightMode == .fill {
                // Pane layouts frame the hero themselves; the
                // GeometryReader fills what the parent proposes.
                heroContent
            } else {
                // Full-screen layouts: the hero is a fraction of
                // its container (the scroll view), never of
                // UIScreen.main.
                heroContent
                    .containerRelativeFrame(.vertical) { length, _ in
                        length * AppTheme.heroHeightRatio
                    }
            }
        }
        // Flag chip over the photo, bottom-trailing (see flagChip).
        // Attached as an overlay on the hero frame itself so its
        // hit area is only the chip — a full-size clear layer here
        // would swallow taps meant for the hero's own controls.
        // The 64pt bottom inset matches Android's: it keeps the
        // chip clear of the hero's bottom content and of the
        // weather-pills row the parent overlaps onto the hero's
        // lower edge in the single-column layouts.
        .overlay(alignment: .bottomTrailing) {
            if let personalFlagTarget {
                personalFlagChip(for: personalFlagTarget)
                    .padding(.trailing, 14)
                    .padding(.bottom, 64)
            } else if let flagTarget {
                flagChip(for: flagTarget)
                    .padding(.trailing, 14)
                    .padding(.bottom, 64)
            }
        }
    }

    private var heroContent: some View {
        GeometryReader { geometry in
            // In containerFraction mode the containerRelativeFrame in
            // `body` has ALREADY applied heroHeightRatio to this
            // view's height — geometry.size.height IS the hero
            // height. Multiplying by the ratio again (as this did)
            // shrank the hero to 0.75 × 0.75 of the container — a
            // near-square frame the full-frame photo then had to be
            // cropped into (field finding 2026-10-31: "the image on
            // iOS is a square"). Android applies its 0.75 once
            // (HeroSection: screenHeightDp * 0.75).
            let heroHeight = geometry.size.height

            ZStack {
                // Background image with crossfade
                heroImageLayer(width: geometry.size.width, height: heroHeight)
                    .frame(width: geometry.size.width, height: heroHeight)

                // Temperature tint overlay (12% opacity)
                AppTheme.temperatureTintOverlay(for: tempBracket, opacity: 0.12)
                    .frame(width: geometry.size.width, height: heroHeight)

                // 6-stop gradient overlay for text readability
                AppTheme.heroOverlayGradient()
                    .frame(width: geometry.size.width, height: heroHeight)

                // Content overlay
                VStack(spacing: 0) {
                    // Top controls (hidden when the navigation
                    // container carries the chrome instead)
                    if showsTopControls {
                        topControls(width: geometry.size.width)
                            .padding(.top, geometry.safeAreaInsets.top + 8)
                    }

                    Spacer()

                    // Bottom content
                    bottomContent
                        .padding(.bottom, 24)
                }
                .frame(width: geometry.size.width, height: heroHeight)
            }
            .frame(width: geometry.size.width, height: heroHeight)
            .clipped()
        }
        .ignoresSafeArea(edges: .top)
    }

    // MARK: - Hero Image Layer

    @ViewBuilder
    private func heroImageLayer(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            // Always show fallback first (instant load)
            AsyncImage(url: URL(string: fallbackImageUrl)) { phase in
                switch phase {
                case .success(let image):
                    HeroPhoto(image: image, width: width, height: height)
                case .failure:
                    Color.black
                case .empty:
                    Color.black
                @unknown default:
                    Color.black
                }
            }

            // AI image on top with crossfade
            if let aiUrl = aiImageUrl {
                AsyncImage(url: URL(string: aiUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        HeroPhoto(image: image, width: width, height: height)
                            .opacity(showAIImage ? 1 : 0)
                            .onAppear {
                                aiImageLoaded = true
                                // Fallback-first loading is unchanged
                                // (RW-4 contract); only the fade is
                                // gated on Reduce Motion.
                                if reduceMotion {
                                    showAIImage = true
                                } else {
                                    withAnimation(.easeInOut(duration: AppTheme.crossfadeDuration)) {
                                        showAIImage = true
                                    }
                                }
                            }
                    case .failure, .empty:
                        EmptyView()
                    @unknown default:
                        EmptyView()
                    }
                }
            }
        }
    }

    // MARK: - Flag Chip

    /// The flag affordance: a 32pt translucent disc with a flag
    /// glyph — deliberately quieter than the 44pt glass share and
    /// settings buttons. Tapping it presents the reason sheet
    /// ("Flag this photo"); picking a reason fires the flag and
    /// leaves the chip dimmed and inert for the rest of this
    /// photo's view (one flag per photo view, as on Android).
    private func flagChip(for target: HeroFlagTarget) -> some View {
        let alreadyFlagged = flaggedImageUrl == target.imageUrl
        return Button {
            showFlagSheet = true
        } label: {
            flagChipDisc(alreadyFlagged: alreadyFlagged)
        }
        .buttonStyle(.plain)
        .disabled(alreadyFlagged)
        .accessibilityLabel("Flag this photo")
        .confirmationDialog(
            "Flag this photo",
            isPresented: $showFlagSheet,
            titleVisibility: .visible
        ) {
            ForEach(HeroFlagReason.allCases, id: \.self) { reason in
                Button(reason.label) {
                    flaggedImageUrl = target.imageUrl
                    onFlagHero(reason)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// The personal-photo variant of the flag chip: same disc,
    /// same placement, same one-flag-per-photo-view inert rule —
    /// but the menu is the personal one (spec §4: the existing
    /// control gains "This doesn't look like me"), and every pick
    /// routes to the backend flag_hero action, which retires THIS
    /// hero and queues its regeneration. The pool reasons are here
    /// in their personal form for the same reason: flagging a
    /// personal photo through the pool insert would record it as a
    /// fallback photo and retire nothing.
    private func personalFlagChip(for target: PersonalHeroFlagTarget) -> some View {
        let alreadyFlagged = flaggedImageUrl == target.imageUrl
        return Button {
            showFlagSheet = true
        } label: {
            flagChipDisc(alreadyFlagged: alreadyFlagged)
        }
        .buttonStyle(.plain)
        .disabled(alreadyFlagged)
        .accessibilityLabel("Flag this photo")
        .confirmationDialog(
            "Flag this photo",
            isPresented: $showFlagSheet,
            titleVisibility: .visible
        ) {
            ForEach(PersonalHeroFlagReason.allCases, id: \.self) { reason in
                Button(reason.label) {
                    flaggedImageUrl = target.imageUrl
                    onFlagPersonalHero(reason)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// The chip's disc, shared by both flag variants.
    private func flagChipDisc(alreadyFlagged: Bool) -> some View {
        Image(systemName: "flag")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(.white.opacity(alreadyFlagged ? 0.45 : 1.0))
            .frame(width: 32, height: 32)
            .background(
                Circle().fill(Color.black.opacity(alreadyFlagged ? 0.22 : 0.35))
            )
            .overlay(
                Circle().stroke(Color.white.opacity(0.18), lineWidth: 1)
            )
    }

    // MARK: - Top Controls

    private func topControls(width: CGFloat) -> some View {
        HStack(spacing: 8) {
            // Location button - constrained to prevent overflow with long names
            Button(action: onLocationTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "location.fill")
                        .font(.appPillLabel)
                    Text(locationName)
                        .font(.appPillLabel)
                        .lineLimit(1)
                }
                .foregroundColor(.white)
                .glassPill()
            }
            .frame(maxWidth: max(120, width - 140), alignment: .leading) // Leave room for buttons

            Spacer(minLength: 8)

            HStack(spacing: 12) {
                // Share button
                Button(action: onShareTapped) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.body.weight(.medium))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .glassMorphism(cornerRadius: 22)
                }

                // Settings button
                Button(action: onSettingsTapped) {
                    Image(systemName: "gearshape.fill")
                        .font(.body.weight(.medium))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .glassMorphism(cornerRadius: 22)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Bottom Content

    private var bottomContent: some View {
        VStack(spacing: 16) {
            // Temperature display
            temperatureDisplay

            // Date and time pills
            dateTimePills
        }
        .padding(.horizontal, 16)
    }

    private var temperatureDisplay: some View {
        VStack(spacing: 4) {
            // "FEELS LIKE" caption — the headline number's identity,
            // carried verbatim from the Android/PWA hero.
            Text("FEELS LIKE")
                .font(.appEyebrow)
                .tracking(0.5)
                .foregroundColor(.white.opacity(0.7))

            // Large temperature (the raw measured feels-like), unit
            // attached — the reference surfaces carry the symbol
            // (Android: "Actual: X°F"; the headline is the same
            // story), and an unlabeled number next to a labeled
            // band was the 2026-10-31 incoherence.
            Text(temperatureUnit.format(temperature))
                .font(.system(size: heroNumeralSize, weight: .thin))
                .monospacedDigit()
                .foregroundColor(.white)
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)

            // Actual temperature sub-line ("Actual: X°F" on the
            // reference surfaces)
            HStack(spacing: 4) {
                Text("Actual:")
                    .foregroundColor(.white.opacity(0.7))
                Text(temperatureUnit.format(actualTemperature))
                    .monospacedDigit()
                    .foregroundColor(.white)
            }
            .font(.body.weight(.medium))
        }
    }

    private var dateTimePills: some View {
        HStack(spacing: 12) {
            // Date pill
            Button(action: onDateTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "calendar")
                        .font(.appPillLabel)
                    Text(formattedDate)
                        .font(.appPillLabel)
                }
                .foregroundColor(.white)
                .glassPill()
            }

            // Time pill
            Button(action: onTimeTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "clock")
                        .font(.appPillLabel)
                    Text(formattedTime)
                        .font(.appPillLabel)
                        .monospacedDigit()
                }
                .foregroundColor(.white)
                .glassPill()
            }

            // "Now" shortcut - only visible when viewing different time.
            // The selected (teal-tinted) state of the shared glass pill —
            // the same chrome as its siblings, tinted for the active state.
            if !isViewingNow {
                Button(action: onNowTapped) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.appPillLabel)
                        Text("Now")
                            .font(.appPillLabel)
                    }
                    .foregroundColor(.white)
                    .glassPill(isSelected: true)
                }
                .transition(reduceMotion ? .opacity : .scale.combined(with: .opacity))
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: isViewingNow)
    }

    // Check if viewing current date and hour
    private var isViewingNow: Bool {
        let calendar = Calendar.current
        let now = Date()
        return calendar.isDateInToday(date) && hour == calendar.component(.hour, from: now)
    }

    // MARK: - Formatters

    private var formattedDate: String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInTomorrow(date) {
            return "Tomorrow"
        } else {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d"
            return formatter.string(from: date)
        }
    }

    private var formattedTime: String {
        let suffix = hour >= 12 ? "PM" : "AM"
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return "\(displayHour):00 \(suffix)"
    }
}

// MARK: - Preview

#Preview {
    HeroImageView(
        locationName: "San Francisco, CA",
        temperature: 58,
        actualTemperature: 61,
        weatherCondition: .clear,
        date: Date(),
        hour: 14,
        tempBracket: .MILD,
        fallbackImageUrl: FallbackImageProvider.shared.getImageURL(temp: .MILD, weather: .CLEAR),
        aiImageUrl: nil
    )
    .background(Color.black)
}
