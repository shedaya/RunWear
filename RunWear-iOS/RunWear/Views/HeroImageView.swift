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
    }

    private var heroContent: some View {
        GeometryReader { geometry in
            let heroHeight = heightMode == .fill
                ? geometry.size.height
                : geometry.size.height * AppTheme.heroHeightRatio

            ZStack {
                // Background image with crossfade
                heroImageLayer
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
    private var heroImageLayer: some View {
        ZStack {
            // Always show fallback first (instant load)
            AsyncImage(url: URL(string: fallbackImageUrl)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
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
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
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

            // Large temperature (the raw measured feels-like)
            Text("\(Int(round(temperature)))°")
                .font(.system(size: heroNumeralSize, weight: .thin))
                .monospacedDigit()
                .foregroundColor(.white)
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)

            // Actual temperature sub-line ("Actual: X°" on the
            // reference surfaces)
            HStack(spacing: 4) {
                Text("Actual:")
                    .foregroundColor(.white.opacity(0.7))
                Text("\(Int(round(actualTemperature)))°")
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
        fallbackImageUrl: "https://images.unsplash.com/photo-1532274402911-5a369e4c4bb5?w=800&h=1200&fit=crop",
        aiImageUrl: nil
    )
    .background(Color.black)
}
