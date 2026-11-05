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
            // GEN-5 (RW-29 FA-46): the hero fills the display's
            // ACTUAL shape (frame 53) — build 25's circle clip
            // centre-cropped the photo into a disc, cutting the
            // runner's head and wasting the display's corners.
            heroImageBackground

            // Temperature tint overlay (30% opacity)
            Rectangle()
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
    }

    private var heroImageBackground: some View {
        // GEN-5 (RW-29 FA-46): full-bleed, TOP-anchored — the
        // photo fills the display's shape with the head kept,
        // the same crop discipline as the phone's hero surfaces.
        GeometryReader { proxy in
            ZStack {
                // Fallback image (immediate)
                if let fallbackUrl = viewModel.fallbackImageUrl {
                    AsyncImage(url: URL(string: fallbackUrl)) { phase in
                        switch phase {
                        case .success(let image):
                            HeroPhoto(image: image, width: proxy.size.width, height: proxy.size.height)
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
                            HeroPhoto(image: image, width: proxy.size.width, height: proxy.size.height)
                        case .failure, .empty:
                            EmptyView()
                        @unknown default:
                            EmptyView()
                        }
                    }
                }
            }
        }
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

                // Clothing Items (v4.1 — all items in layer order)
                ForEach(recommendation.allItems) { item in
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
                // GEN-5 (cream map row 60): the outfit page's
                // refresh tint is pine, not system blue.
                .tint(AppTheme.primaryColor)
                .padding(.top, 4)
            }
            .padding(.horizontal, 4)
        }
    }
}

// MARK: - Watch Clothing Row

/// The clothing row's glyph tile: the item's SF Symbol rendered as
/// an IMAGE — never the symbol's name as text. (The row opened with
/// `Text(item.icon)`, which printed clipped fragments of the symbol
/// name — "tshirt.f…" — on every row: the R-05 defect class, which
/// survived on the watch after the phone fix; RW-6.1.) A dedicated
/// view so the test target can pin the rendering contract on the
/// exact view the row composes.
struct WatchClothingGlyph: View {
    let item: ClothingItem

    var body: some View {
        Image(systemName: item.icon)
            .font(.system(size: 18))
            .foregroundColor(.white)
            .frame(width: 32, height: 32)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.white.opacity(0.1))
            )
    }
}

struct WatchClothingRow: View {
    let item: ClothingItem
    var category: String? = nil
    var gender: GenderPreference = .unisex

    /// The row's glyph — the pinned seam for RW-6.1: the body below
    /// composes exactly this view, and the test target asserts its
    /// rendered content is the symbol image, not the name string.
    var glyph: WatchClothingGlyph { WatchClothingGlyph(item: item) }

    var body: some View {
        HStack(spacing: 8) {
            // Icon — SF Symbol image (see WatchClothingGlyph)
            glyph

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

            // GEN-6 (DA-24): no shop link on the watch — there is
            // no designed watch shop surface, and the phone shop
            // sheet is the settled shop surface. The row's `gender`
            // input remains for the row's other consumers.
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        // GEN-5 (cream map row 60): the theme's own dark card
        // fill, not a raw white composite.
        .background(AppTheme.cardFillOnDark)
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
        hourly: nil,
        timezone: nil,
        currentDetail: nil
    )
    let recommendation = OutfitRecommendationService.shared.getRecommendation(
        for: weather.currentWeather.temperatureFahrenheit,
        rawTemperature: weather.currentWeather.temperatureFahrenheit,
        isRaining: false
    )

    WatchOutfitView(
        viewModel: viewModel,
        weather: weather,
        recommendation: recommendation
    )
}
