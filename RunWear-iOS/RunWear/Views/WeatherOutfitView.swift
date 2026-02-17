import SwiftUI

struct WeatherOutfitView: View {
    @ObservedObject var viewModel: WeatherViewModel
    @ObservedObject var locationService: LocationService
    let weather: WeatherData
    let recommendation: OutfitRecommendation
    let isRefreshing: Bool
    let onRefresh: () -> Void
    var onUseCurrentLocation: () -> Void = {}

    // Modal state
    @State private var showSettings = false
    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var showShop = false
    @State private var showWeatherDetail = false
    @State private var showLocationPicker = false
    @State private var selectedWeatherDetail: WeatherDetailType?

    // Animation state
    @State private var hasAppeared = false

    var body: some View {
        ZStack {
            // Dark background
            AppTheme.darkBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    // Hero Image Section
                    heroSection

                    // Weather Pills
                    weatherPillsSection
                        .padding(.top, -30)
                        .zIndex(1)

                    // Outfit Recommendations
                    outfitSection
                        .padding(.top, 24)

                    // Tips
                    tipsSection
                        .padding(.top, 16)

                    Spacer(minLength: 40)
                }
            }
            .refreshable {
                onRefresh()
            }

            // Loading overlay
            if isRefreshing {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)
            }

            // Modals
            if showSettings {
                SettingsModal(
                    isPresented: $showSettings,
                    temperatureUnit: $viewModel.temperatureUnit,
                    genderPreference: $viewModel.genderPreference,
                    comfortLevel: $viewModel.comfortLevel
                )
            }

            if showDatePicker {
                DatePickerSheet(
                    selectedDate: $viewModel.selectedDate,
                    isPresented: $showDatePicker
                )
                .onChange(of: viewModel.selectedDate) { _, newDate in
                    viewModel.updateSelectedTime(date: newDate, hour: viewModel.selectedHour)
                }
            }

            if showTimePicker {
                TimePickerSheet(
                    selectedHour: $viewModel.selectedHour,
                    isPresented: $showTimePicker
                )
                .onChange(of: viewModel.selectedHour) { _, newHour in
                    viewModel.updateSelectedTime(date: viewModel.selectedDate, hour: newHour)
                }
            }

            if showShop {
                ShopModal(
                    items: recommendation.allItems,
                    genderPreference: viewModel.genderPreference,
                    isPresented: $showShop
                )
            }

            if showWeatherDetail, let detail = selectedWeatherDetail {
                WeatherDetailSheet(
                    detailType: detail,
                    isPresented: $showWeatherDetail
                )
            }

            if showLocationPicker {
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
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5).delay(0.2)) {
                hasAppeared = true
            }
        }
    }

    // MARK: - Hero Section

    private var heroSection: some View {
        HeroImageView(
            locationName: viewModel.locationName,
            temperature: viewModel.currentTemperature,
            feelsLike: viewModel.currentFeelsLike,
            weatherCondition: viewModel.currentCondition,
            date: viewModel.selectedDate,
            hour: viewModel.selectedHour,
            tempBracket: viewModel.currentTempBracket,
            fallbackImageUrl: viewModel.fallbackImageUrl ?? defaultFallbackUrl,
            aiImageUrl: viewModel.heroImageUrl,
            onSettingsTapped: { withAnimation { showSettings = true } },
            onShareTapped: shareOutfit,
            onLocationTapped: { withAnimation { showLocationPicker = true } },
            onDateTapped: { withAnimation { showDatePicker = true } },
            onTimeTapped: { withAnimation { showTimePicker = true } },
            onNowTapped: { viewModel.resetToNow() }
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
                withAnimation { showWeatherDetail = true }
            },
            onWindTapped: {
                let snapshot = viewModel.selectedWeatherSnapshot
                selectedWeatherDetail = .wind(
                    speed: (snapshot?.windSpeed ?? weather.currentWeather.windspeed) * 0.621371,
                    direction: snapshot?.windDirectionCompass ?? "N",
                    gusts: (snapshot?.windGusts ?? weather.currentWeather.windspeed) * 0.621371
                )
                withAnimation { showWeatherDetail = true }
            },
            onHumidityTapped: {
                selectedWeatherDetail = .humidity(percentage: viewModel.selectedWeatherSnapshot?.humidity ?? 50)
                withAnimation { showWeatherDetail = true }
            },
            onPrecipitationTapped: {
                selectedWeatherDetail = .precipitation(percentage: viewModel.selectedWeatherSnapshot?.precipitationProbability ?? 0)
                withAnimation { showWeatherDetail = true }
            },
            onUVTapped: {
                selectedWeatherDetail = .uvIndex(value: viewModel.selectedWeatherSnapshot?.uvIndex ?? 0)
                withAnimation { showWeatherDetail = true }
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

    // MARK: - Outfit Section

    private var outfitSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Section header
            HStack {
                Text("Recommended Outfit")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)

                Spacer()

                Button(action: { withAnimation { showShop = true } }) {
                    HStack(spacing: 4) {
                        Image(systemName: "cart.fill")
                        Text("Shop")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(AppTheme.primaryColor)
                    .cornerRadius(20)
                }
            }
            .padding(.horizontal, 16)

            // Temperature bracket badge
            TemperatureBracketBadge(bracket: recommendation.temperatureBracket)
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
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .staggeredAnimation(index: 1 + recommendation.allItems.count, isVisible: hasAppeared)

                ForEach(Array(recommendation.tips.enumerated()), id: \.offset) { index, tip in
                    Text(tip)
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                        .staggeredAnimation(index: 2 + recommendation.allItems.count + index, isVisible: hasAppeared)
                }
            }
        }
    }

    // MARK: - Tips Section

    private var tipsSection: some View {
        DarkTipsCard(
            bracket: recommendation.temperatureBracket,
            condition: viewModel.currentCondition
        )
        .padding(.horizontal, 16)
        .staggeredAnimation(
            index: 5 + recommendation.allItems.count + recommendation.tips.count,
            isVisible: hasAppeared
        )
    }

    // MARK: - Actions

    private func shareOutfit() {
        // Build share content
        let items: [Any] = [
            "🏃 RunWear Outfit for \(viewModel.locationName)",
            "Temperature: \(Int(viewModel.currentTemperature))°\(viewModel.temperatureUnit == .fahrenheit ? "F" : "C")",
            "Condition: \(viewModel.currentCondition.rawValue)",
            "",
            "Outfit: \(recommendation.allItems.map { $0.name }.joined(separator: ", "))",
            "",
            "Get your personalized running outfit at runwear.app"
        ].filter { !($0 as? String ?? "").isEmpty }

        let text = (items as? [String])?.joined(separator: "\n") ?? ""

        let activityVC = UIActivityViewController(
            activityItems: [text],
            applicationActivities: nil
        )

        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first,
           let rootVC = window.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
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
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                }

                // AI-generated thumbnail
                if let url = thumbnailURL {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .empty:
                            // Loading state - show icon
                            Image(systemName: item.icon)
                                .font(.system(size: 28))
                                .foregroundColor(.white)
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .failure:
                            // Error state - show icon
                            Image(systemName: item.icon)
                                .font(.system(size: 28))
                                .foregroundColor(.white)
                        @unknown default:
                            Image(systemName: item.icon)
                                .font(.system(size: 28))
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
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white.opacity(0.5))
                }
                Text(item.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Text(item.description)
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.6))
                    .lineLimit(2)
            }

            Spacer()

            // External link icon (matching PWA v3.12)
            Image(systemName: "arrow.up.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))
                .frame(width: 44, height: 44)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }
}

struct DarkTipsCard: View {
    let bracket: TemperatureBracket
    let condition: WeatherCondition

    var tips: [String] {
        var result: [String] = []

        switch bracket {
        case .hot:
            result.append("Stay hydrated - drink water before, during, and after your run")
            result.append("Consider running early morning or evening to avoid peak heat")
        case .warm:
            result.append("Good conditions for running - enjoy your workout!")
            result.append("Stay hydrated even if you don't feel thirsty")
        case .mild:
            result.append("Perfect running weather for many runners")
            result.append("You may warm up quickly - dress lighter than expected")
        case .cool:
            result.append("Warm up thoroughly before picking up the pace")
            result.append("Consider bringing a light layer to tie around your waist")
        case .cold:
            result.append("Extend your warm-up to prevent injury")
            result.append("Breathe through a gaiter to warm the air")
        case .veryCold:
            result.append("Keep your core warm - the body prioritizes vital organs")
            result.append("Shorten your run or consider indoor alternatives")
        case .extreme:
            result.append("Consider running indoors - frostbite risk is real")
            result.append("If running outside, tell someone your route")
        }

        if condition == .rain || condition == .drizzle {
            result.append("Wear a brimmed cap to keep rain off your face")
        }

        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "lightbulb.fill")
                    .foregroundColor(.yellow)
                Text("Running Tips")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
            }

            ForEach(tips, id: \.self) { tip in
                HStack(alignment: .top, spacing: 8) {
                    Text("•")
                        .foregroundColor(AppTheme.primaryColor)
                    Text(tip)
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
    }
}

// MARK: - Legacy Components (kept for compatibility)

struct WeatherCard: View {
    let weather: CurrentWeather
    let locationName: String

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "location.fill")
                    .foregroundColor(AppTheme.primaryColor)
                Text(locationName)
                    .font(.subheadline)
                    .foregroundColor(AppTheme.textSecondary)
            }

            HStack(alignment: .top, spacing: 20) {
                VStack {
                    Image(systemName: weather.condition.icon)
                        .font(.system(size: 50))
                        .foregroundColor(AppTheme.primaryColor)
                    Text(weather.condition.rawValue)
                        .font(.caption)
                        .foregroundColor(AppTheme.textSecondary)
                }

                VStack(alignment: .leading) {
                    Text("\(Int(weather.temperatureFahrenheit))°F")
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(AppTheme.textPrimary)
                    Text("\(Int(weather.temperature))°C")
                        .font(.title3)
                        .foregroundColor(AppTheme.textSecondary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    HStack {
                        Image(systemName: "wind")
                            .foregroundColor(AppTheme.textSecondary)
                        Text("\(Int(weather.windspeed)) km/h")
                            .font(.subheadline)
                            .foregroundColor(AppTheme.textSecondary)
                    }
                }
            }
        }
        .padding()
        .background(AppTheme.cardBackground)
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 4)
        .padding(.horizontal)
    }
}

struct TemperatureBracketBadge: View {
    let bracket: TemperatureBracket

    var body: some View {
        HStack(spacing: 8) {
            Text(bracket.description)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
            Text(bracket.rawValue)
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
        case .extreme: return 10
        }
    }
}

struct ClothingItemCard: View {
    let item: ClothingItem
    var category: String? = nil
    var gender: GenderPreference = .unisex

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(AppTheme.primaryColor.opacity(0.1))
                    .frame(width: 50, height: 50)
                Text(item.icon)
                    .font(.title2)
            }

            VStack(alignment: .leading, spacing: 4) {
                if let category = category {
                    Text(category)
                        .font(.caption)
                        .foregroundColor(AppTheme.textSecondary)
                        .textCase(.uppercase)
                }
                Text(item.name)
                    .font(.headline)
                    .foregroundColor(AppTheme.textPrimary)
                Text(item.description)
                    .font(.subheadline)
                    .foregroundColor(AppTheme.textSecondary)
                    .lineLimit(2)
            }

            Spacer()

            if let url = item.affiliateURL(for: gender) {
                Link(destination: url) {
                    Image(systemName: "cart.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                        .padding(10)
                        .background(AppTheme.primaryColor)
                        .cornerRadius(10)
                }
            }
        }
        .padding()
        .background(AppTheme.cardBackground)
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.05), radius: 4, x: 0, y: 2)
        .padding(.horizontal)
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

struct TipsCard: View {
    let bracket: TemperatureBracket
    let condition: WeatherCondition

    var tips: [String] {
        var result: [String] = []

        switch bracket {
        case .hot:
            result.append("Stay hydrated - drink water before, during, and after your run")
            result.append("Consider running early morning or evening to avoid peak heat")
            result.append("Wear light-colored clothing to reflect sunlight")
        case .warm:
            result.append("Good conditions for running - enjoy your workout!")
            result.append("Stay hydrated even if you don't feel thirsty")
        case .mild:
            result.append("Perfect running weather for many runners")
            result.append("You may warm up quickly - dress lighter than expected")
        case .cool:
            result.append("Warm up thoroughly before picking up the pace")
            result.append("Consider bringing a light layer to tie around your waist")
        case .cold:
            result.append("Extend your warm-up to prevent injury")
            result.append("Breathe through a gaiter to warm the air")
        case .veryCold:
            result.append("Keep your core warm - the body prioritizes vital organs")
            result.append("Shorten your run or consider indoor alternatives")
        case .extreme:
            result.append("Consider running indoors - frostbite risk is real")
            result.append("If running outside, tell someone your route and expected return time")
        }

        if condition == .rain || condition == .drizzle {
            result.append("Wear a brimmed cap to keep rain off your face")
        }

        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "lightbulb.fill")
                    .foregroundColor(AppTheme.primaryColor)
                Text("Running Tips")
                    .font(.headline)
                    .foregroundColor(AppTheme.textPrimary)
            }

            ForEach(tips, id: \.self) { tip in
                HStack(alignment: .top, spacing: 8) {
                    Text("•")
                        .foregroundColor(AppTheme.primaryColor)
                    Text(tip)
                        .font(.subheadline)
                        .foregroundColor(AppTheme.textSecondary)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.primaryColor.opacity(0.05))
        .cornerRadius(12)
        .padding(.horizontal)
    }
}

#Preview {
    let viewModel = WeatherViewModel()
    let locationService = LocationService()

    return WeatherOutfitView(
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
            hourly: nil
        ),
        recommendation: OutfitRecommendationService.shared.getRecommendation(
            for: 59,
            condition: .clear
        ),
        isRefreshing: false,
        onRefresh: {}
    )
}
