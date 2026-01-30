import SwiftUI

struct WatchOutfitView: View {
    @ObservedObject var viewModel: WatchViewModel
    let weather: WeatherData
    let recommendation: OutfitRecommendation

    @State private var selectedPage = 0

    var body: some View {
        TabView(selection: $selectedPage) {
            // Page 1: Hero Weather
            heroWeatherPage
                .tag(0)

            // Page 2: Outfit Items
            outfitPage
                .tag(1)

            // Page 3: Settings
            WatchSettingsView(viewModel: viewModel)
                .tag(2)
        }
        .tabViewStyle(.page(indexDisplayMode: .automatic))
    }

    // MARK: - Page 1: Hero Weather

    private var heroWeatherPage: some View {
        ZStack {
            // Hero image background (circular crop for watch)
            heroImageBackground

            // Temperature tint overlay (30% opacity)
            Circle()
                .fill(temperatureColor.opacity(0.3))

            // Content
            VStack(spacing: 4) {
                // Location
                HStack(spacing: 2) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 8))
                    Text(viewModel.locationName)
                        .font(.system(size: 10))
                        .lineLimit(1)
                }
                .foregroundColor(.white.opacity(0.8))

                Spacer()

                // Weather icon
                Image(systemName: weather.currentWeather.condition.icon)
                    .font(.system(size: 28))
                    .foregroundColor(.white)

                // Temperature
                Button(action: { viewModel.toggleUnit() }) {
                    Text(viewModel.displayTemperature)
                        .font(.system(size: 44, weight: .bold))
                        .foregroundColor(.white)
                }
                .buttonStyle(.plain)

                // Condition
                Text(weather.currentWeather.condition.shortDescription)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.8))

                Spacer()

                // Swipe hint
                HStack(spacing: 4) {
                    Text("Outfit")
                        .font(.system(size: 9))
                    Image(systemName: "chevron.right")
                        .font(.system(size: 8))
                }
                .foregroundColor(.white.opacity(0.5))
            }
            .padding(12)
        }
        .clipShape(Circle())
    }

    private var heroImageBackground: some View {
        ZStack {
            // Fallback image (immediate)
            if let fallbackUrl = viewModel.fallbackImageUrl {
                AsyncImage(url: URL(string: fallbackUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure, .empty:
                        Color.black
                    @unknown default:
                        Color.black
                    }
                }
            }

            // AI image on top with crossfade
            if let heroUrl = viewModel.heroImageUrl {
                AsyncImage(url: URL(string: heroUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure, .empty:
                        EmptyView()
                    @unknown default:
                        EmptyView()
                    }
                }
            }
        }
        .clipShape(Circle())
    }

    private var temperatureColor: Color {
        AppTheme.temperatureColor(for: viewModel.currentTempBracket)
    }

    // MARK: - Page 2: Outfit

    private var outfitPage: some View {
        ScrollView {
            VStack(spacing: 6) {
                // Temperature Bracket
                Text(recommendation.temperatureBracket.description)
                    .font(.caption2)
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(temperatureColor.opacity(0.8))
                    .cornerRadius(8)

                // Clothing Items
                WatchClothingRow(
                    item: recommendation.top,
                    category: "Top",
                    gender: viewModel.genderPreference
                )

                WatchClothingRow(
                    item: recommendation.bottom,
                    category: "Bottom",
                    gender: viewModel.genderPreference
                )

                ForEach(recommendation.accessories) { item in
                    WatchClothingRow(item: item, gender: viewModel.genderPreference)
                }

                ForEach(recommendation.extras) { item in
                    WatchClothingRow(item: item, gender: viewModel.genderPreference)
                }

                // Refresh Button
                Button(action: { viewModel.refresh() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 10))
                        Text("Refresh")
                            .font(.caption2)
                    }
                }
                .buttonStyle(.bordered)
                .tint(.blue)
                .padding(.top, 4)
            }
            .padding(.horizontal, 4)
        }
    }
}

// MARK: - Watch Weather Card (Legacy)

struct WatchWeatherCard: View {
    let weather: CurrentWeather
    let locationName: String
    let onToggleUnit: () -> Void

    var temperatureColor: Color {
        let temp = weather.temperatureFahrenheit
        switch temp {
        case 80...: return Color(hex: "FF5722")
        case 65..<80: return Color(hex: "FF9800")
        case 50..<65: return Color(hex: "4CAF50")
        case 35..<50: return Color(hex: "2196F3")
        case 20..<35: return Color(hex: "3F51B5")
        default: return Color(hex: "9C27B0")
        }
    }

    var body: some View {
        VStack(spacing: 4) {
            // Location
            HStack(spacing: 2) {
                Image(systemName: "location.fill")
                    .font(.system(size: 8))
                Text(locationName)
                    .font(.system(size: 10))
            }
            .foregroundColor(.secondary)

            // Weather Icon + Temp
            HStack(spacing: 8) {
                Image(systemName: weather.condition.icon)
                    .font(.title2)
                    .foregroundColor(AppTheme.primaryColor)

                Button(action: onToggleUnit) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("\(Int(weather.temperatureFahrenheit))°")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(temperatureColor)
                        Text("Feels like")
                            .font(.system(size: 8))
                            .foregroundColor(.secondary)
                    }
                }
                .buttonStyle(.plain)
            }

            // Condition + Wind
            HStack(spacing: 8) {
                Text(weather.condition.rawValue)
                    .font(.caption2)

                HStack(spacing: 2) {
                    Image(systemName: "wind")
                        .font(.system(size: 8))
                    Text("\(Int(weather.windspeed))")
                        .font(.system(size: 10))
                }
            }
            .foregroundColor(.secondary)
        }
        .padding(8)
        .background(Color.black.opacity(0.3))
        .cornerRadius(12)
    }
}

// MARK: - Watch Clothing Row

struct WatchClothingRow: View {
    let item: ClothingItem
    var category: String? = nil
    var gender: GenderPreference = .unisex

    var body: some View {
        HStack(spacing: 8) {
            // Icon
            Text(item.icon)
                .font(.system(size: 18))
                .frame(width: 32, height: 32)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.white.opacity(0.1))
                )

            VStack(alignment: .leading, spacing: 0) {
                if let category = category {
                    Text(category.uppercased())
                        .font(.system(size: 8))
                        .foregroundColor(.secondary)
                }
                Text(item.name)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .lineLimit(1)
                    .foregroundColor(.white)
            }

            Spacer()

            // Shop icon - links to Amazon with watchos subtag
            if let url = item.affiliateURL(for: gender, subtag: "watchos") {
                Link(destination: url) {
                    Image(systemName: "cart.fill")
                        .font(.system(size: 10))
                        .foregroundColor(AppTheme.primaryColor)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .frame(minHeight: 44) // Minimum touch target
    }
}

#Preview {
    let viewModel = WatchViewModel()
    let weather = WeatherData(
        latitude: 40.7128,
        longitude: -74.0060,
        currentWeather: CurrentWeather(
            temperature: 10,
            windspeed: 15,
            weathercode: 3,
            isDay: 1
        ),
        hourly: nil
    )
    let recommendation = OutfitRecommendationService.shared.getRecommendation(
        for: weather.currentWeather.temperatureFahrenheit,
        condition: weather.currentWeather.condition
    )

    return WatchOutfitView(
        viewModel: viewModel,
        weather: weather,
        recommendation: recommendation
    )
}
