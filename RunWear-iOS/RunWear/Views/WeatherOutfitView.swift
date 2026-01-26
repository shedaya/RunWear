import SwiftUI

struct WeatherOutfitView: View {
    let weather: WeatherData
    let recommendation: OutfitRecommendation
    let locationName: String
    let isRefreshing: Bool
    let genderPreference: GenderPreference
    let onRefresh: () -> Void
    let onGenderChange: (GenderPreference) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Weather Card
                WeatherCard(
                    weather: weather.currentWeather,
                    locationName: locationName
                )

                // Temperature Bracket
                TemperatureBracketBadge(bracket: recommendation.temperatureBracket)

                // Outfit Recommendations
                VStack(alignment: .leading, spacing: 16) {
                    // Section header with gender toggle
                    HStack {
                        Text("Recommended Outfit")
                            .font(.headline)
                            .foregroundColor(AppTheme.textPrimary)

                        Spacer()

                        GenderToggle(
                            selected: genderPreference,
                            onSelect: onGenderChange
                        )
                    }
                    .padding(.horizontal)

                    // Main clothing items
                    ClothingItemCard(item: recommendation.top, category: "Top", gender: genderPreference)
                    ClothingItemCard(item: recommendation.bottom, category: "Bottom", gender: genderPreference)

                    // Accessories
                    if !recommendation.accessories.isEmpty {
                        Text("Accessories")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(AppTheme.textSecondary)
                            .padding(.horizontal)
                            .padding(.top, 8)

                        ForEach(recommendation.accessories) { item in
                            ClothingItemCard(item: item, gender: genderPreference)
                        }
                    }

                    // Extras (rain gear, etc.)
                    if !recommendation.extras.isEmpty {
                        Text("Additional Gear")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(AppTheme.textSecondary)
                            .padding(.horizontal)
                            .padding(.top, 8)

                        ForEach(recommendation.extras) { item in
                            ClothingItemCard(item: item, gender: genderPreference)
                        }
                    }
                }

                // Tips
                TipsCard(bracket: recommendation.temperatureBracket, condition: weather.currentWeather.condition)

                Spacer(minLength: 20)
            }
            .padding(.vertical)
        }
        .refreshable {
            onRefresh()
        }
        .overlay {
            if isRefreshing {
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.1))
            }
        }
    }
}

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
        VStack(spacing: 4) {
            Text(bracket.description)
                .font(.headline)
                .foregroundColor(.white)
            Text(bracket.rawValue)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
        .background(AppTheme.primaryColor.opacity(bracket.colorOpacity))
        .background(AppTheme.primaryColor)
        .cornerRadius(25)
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
                Image(systemName: item.icon)
                    .font(.title2)
                    .foregroundColor(AppTheme.primaryColor)
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

// MARK: - Gender Toggle

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
    let weather = WeatherData(
        latitude: 40.7128,
        longitude: -74.0060,
        currentWeather: CurrentWeather(
            temperature: 15,
            windspeed: 10,
            weathercode: 0,
            isDay: 1
        )
    )
    let recommendation = OutfitRecommendationService.shared.getRecommendation(
        for: weather.currentWeather.temperatureFahrenheit,
        condition: weather.currentWeather.condition
    )

    return NavigationStack {
        WeatherOutfitView(
            weather: weather,
            recommendation: recommendation,
            locationName: "New York, NY",
            isRefreshing: false,
            genderPreference: .unisex,
            onRefresh: {},
            onGenderChange: { _ in }
        )
    }
}
