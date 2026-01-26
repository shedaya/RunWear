import SwiftUI

struct WatchOutfitView: View {
    let weather: WeatherData
    let recommendation: OutfitRecommendation
    let locationName: String
    let onRefresh: () -> Void
    let onToggleUnit: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                // Weather Hero Card
                WatchWeatherCard(
                    weather: weather.currentWeather,
                    locationName: locationName,
                    onToggleUnit: onToggleUnit
                )

                // Temperature Bracket
                Text(recommendation.temperatureBracket.description)
                    .font(.caption2)
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppTheme.primaryColor)
                    .cornerRadius(8)

                // Clothing Items
                VStack(spacing: 6) {
                    WatchClothingRow(item: recommendation.top, category: "Top")
                    WatchClothingRow(item: recommendation.bottom, category: "Bottom")

                    ForEach(recommendation.accessories) { item in
                        WatchClothingRow(item: item)
                    }

                    ForEach(recommendation.extras) { item in
                        WatchClothingRow(item: item)
                    }
                }

                // Refresh Button
                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .tint(AppTheme.primaryColor)
                .padding(.top, 4)
            }
            .padding(.horizontal, 4)
        }
    }
}

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

struct WatchClothingRow: View {
    let item: ClothingItem
    var category: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(AppTheme.primaryColor.opacity(0.2))
                    .frame(width: 28, height: 28)
                Image(systemName: item.icon)
                    .font(.system(size: 12))
                    .foregroundColor(AppTheme.primaryColor)
            }

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
            }

            Spacer()

            // Shop icon - links to Amazon
            if item.affiliateURL != nil {
                Image(systemName: "cart.fill")
                    .font(.system(size: 10))
                    .foregroundColor(AppTheme.primaryColor)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
    }
}

#Preview {
    let weather = WeatherData(
        latitude: 40.7128,
        longitude: -74.0060,
        currentWeather: CurrentWeather(
            temperature: 10,
            windspeed: 15,
            weathercode: 3,
            isDay: 1
        )
    )
    let recommendation = OutfitRecommendationService.shared.getRecommendation(
        for: weather.currentWeather.temperatureFahrenheit,
        condition: weather.currentWeather.condition
    )

    return WatchOutfitView(
        weather: weather,
        recommendation: recommendation,
        locationName: "Brooklyn, NY",
        onRefresh: {},
        onToggleUnit: {}
    )
}
