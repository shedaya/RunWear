import SwiftUI

struct HeroImageView: View {
    let locationName: String
    let temperature: Double // Fahrenheit
    let feelsLike: Double // Fahrenheit
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

    // State for crossfade
    @State private var showAIImage = false
    @State private var aiImageLoaded = false

    var body: some View {
        GeometryReader { geometry in
            let heroHeight = geometry.size.height * AppTheme.heroHeightRatio

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
                    // Top controls
                    topControls
                        .padding(.top, geometry.safeAreaInsets.top + 8)

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
        .frame(height: UIScreen.main.bounds.height * AppTheme.heroHeightRatio)
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
                                withAnimation(.easeInOut(duration: AppTheme.crossfadeDuration)) {
                                    showAIImage = true
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

    private var topControls: some View {
        HStack {
            // Location button
            Button(action: onLocationTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 14))
                    Text(locationName)
                        .font(.system(size: 15, weight: .medium))
                }
                .foregroundColor(.white)
                .glassPill()
            }

            Spacer()

            HStack(spacing: 12) {
                // Share button
                Button(action: onShareTapped) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .glassMorphism(cornerRadius: 22)
                }

                // Settings button
                Button(action: onSettingsTapped) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 18, weight: .medium))
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
            // Large temperature
            Text("\(Int(round(temperature)))°")
                .font(.system(size: 96, weight: .thin))
                .foregroundColor(.white)

            // Feels like
            HStack(spacing: 4) {
                Text("Feels like")
                    .foregroundColor(.white.opacity(0.7))
                Text("\(Int(round(feelsLike)))°")
                    .foregroundColor(.white)
            }
            .font(.system(size: 17, weight: .medium))
        }
    }

    private var dateTimePills: some View {
        HStack(spacing: 12) {
            // Date pill
            Button(action: onDateTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "calendar")
                        .font(.system(size: 14))
                    Text(formattedDate)
                        .font(.system(size: 15, weight: .medium))
                }
                .foregroundColor(.white)
                .glassPill()
            }

            // Time pill
            Button(action: onTimeTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "clock")
                        .font(.system(size: 14))
                    Text(formattedTime)
                        .font(.system(size: 15, weight: .medium))
                }
                .foregroundColor(.white)
                .glassPill()
            }
        }
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
        feelsLike: 55,
        weatherCondition: .clear,
        date: Date(),
        hour: 14,
        tempBracket: .MILD,
        fallbackImageUrl: "https://images.unsplash.com/photo-1532274402911-5a369e4c4bb5?w=800&h=1200&fit=crop",
        aiImageUrl: nil
    )
    .background(Color.black)
}
