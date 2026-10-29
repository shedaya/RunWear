import SwiftUI

struct ShopModal: View {
    let items: [ClothingItem]
    let genderPreference: GenderPreference
    /// WMO weather code for the Shop All search term (Android's
    /// buildShopAllUrl is weather-aware; the iOS sheet previously
    /// searched a generic "running gear" for every condition).
    let weatherCode: Int
    @Binding var isPresented: Bool

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    /// At regular width a tapped item opens in an inspector beside
    /// the list instead of leaving for Amazon immediately (design
    /// record §5.2); compact keeps the direct-open behavior.
    @State private var selectedItem: ClothingItem?

    // Affiliate configuration
    private let affiliateTag = "runwear-20"
    private let subtag = "ios"

    // Presented as a real .sheet (design record §5.2): the system
    // owns the backdrop, drag handle, and dismissal. The content
    // sits in a NavigationStack so the item inspector has a
    // container to live in.
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Header with Shop All
                headerSection
                    .padding(.horizontal, 24)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                // Items list — sized by the sheet's detent, never
                // by UIScreen.main.
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                            ShopItemRow(
                                item: item,
                                genderPreference: genderPreference,
                                affiliateTag: affiliateTag,
                                subtag: subtag,
                                onSelect: horizontalSizeClass == .regular
                                    ? { selectedItem = item }
                                    : nil
                            )
                            .staggeredAnimation(index: index)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }

                // FTC disclosure + explicit Close (Android's shop
                // sheet carries both; the iOS sheet had neither).
                VStack(spacing: 12) {
                    Text("Affiliate Disclosure: ")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))
                    + Text("As an Amazon Associate, RunWear earns from qualifying purchases. When you click these links, we may earn a small commission at no extra cost to you.")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.05))
                )
                .padding(.horizontal, 24)

                Button(action: dismiss) {
                    Text("Close")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.white.opacity(0.3), lineWidth: 1)
                        )
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 16)
            }
            .background(AppTheme.darkBackground.ignoresSafeArea())
            .inspector(isPresented: itemInspectorPresented) {
                itemInspector
            }
        }
    }

    private var itemInspectorPresented: Binding<Bool> {
        Binding(
            get: { selectedItem != nil },
            set: { presented in
                if !presented { selectedItem = nil }
            }
        )
    }

    /// The selected item's detail: identity, description, and the
    /// Amazon action the row itself fires on compact.
    @ViewBuilder private var itemInspector: some View {
        if let item = selectedItem {
            VStack(alignment: .leading, spacing: 16) {
                // Product thumbnail over the glyph fallback — the same
                // pattern as DarkClothingItemCard. (This tile rendered
                // Text(item.icon), i.e. the SF Symbol NAME as text.)
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))

                    if let url = ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: genderPreference) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            default:
                                Image(systemName: item.icon)
                                    .font(.system(size: 36))
                                    .foregroundColor(.white)
                            }
                        }
                    } else {
                        Image(systemName: item.icon)
                            .font(.system(size: 36))
                            .foregroundColor(.white)
                    }
                }
                .frame(width: 72, height: 72)
                Text(item.name)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                Text(item.description)
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.7))
                Button(action: { openAmazon(for: item) }) {
                    HStack(spacing: 6) {
                        Image(systemName: "cart.fill")
                        Text("Shop")
                    }
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(
                        Capsule().fill(AppTheme.primaryColor)
                    )
                }
                Spacer()
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppTheme.darkBackground.ignoresSafeArea())
            .navigationTitle(item.name)
            .inspectorColumnWidth(min: 240, ideal: 300, max: 360)
        }
    }

    private func openAmazon(for item: ClothingItem) {
        // ClothingItem.affiliateURL builds the same link the outfit
        // cards use — including the "premium" prefix Android's link
        // builder applies (the sheet's hand-rolled URL omitted it).
        if let url = item.affiliateURL(for: genderPreference, subtag: subtag) {
            UIApplication.shared.open(url)
        }
    }


    // MARK: - Header

    private var headerSection: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Shop Gear")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)

                Text("\(items.count) items recommended")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.6))
            }

            Spacer()

            // Shop All button
            Button(action: openShopAll) {
                HStack(spacing: 4) {
                    Text("Shop All")
                        .font(.system(size: 15, weight: .semibold))
                    Image(systemName: "arrow.right")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    Capsule()
                        .fill(AppTheme.primaryColor)
                )
            }
        }
    }

    // MARK: - Actions

    private func openShopAll() {
        // Android's buildShopAllUrl, verbatim semantics:
        // "premium {men's |women's |}{weather description} weather
        // running gear" — weather-aware, not a generic "running gear".
        let fullTerm = "premium \(genderPreference.searchPrefix)\(Self.weatherDescription(for: weatherCode)) weather running gear"
            .trimmingCharacters(in: .whitespaces)
        let searchTerm = fullTerm.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? fullTerm

        let urlString = "https://www.amazon.com/s?k=\(searchTerm)&tag=\(affiliateTag)&subtag=\(subtag)"

        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }

    /// Weather description for search terms — a port of Android's
    /// getWeatherDescription (BottomSheets.kt), code for code.
    static func weatherDescription(for code: Int) -> String {
        switch code {
        case 0, 1: return "sunny"
        case 2, 3: return "cloudy"
        case 45...48: return "foggy"
        case 51...67: return "rainy"
        case 71...77: return "cold weather"
        case 80...82: return "rainy"
        case 85, 86: return "snowy"
        case 95...: return "stormy"
        default: return "all weather"
        }
    }


    private func dismiss() {
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
        }
    }
}

// MARK: - Shop Item Row

private struct ShopItemRow: View {
    let item: ClothingItem
    let genderPreference: GenderPreference
    let affiliateTag: String
    let subtag: String
    /// When set (regular width), tapping selects the item for the
    /// inspector instead of opening Amazon directly.
    var onSelect: (() -> Void)? = nil

    var body: some View {
        Button(action: { if let onSelect { onSelect() } else { openAmazon() } }) {
            HStack(spacing: 16) {
                // Product thumbnail over the category glyph — the
                // DarkClothingItemCard pattern. This tile previously
                // rendered Text(item.icon): the SF Symbol NAME
                // ("tshirt.fill", "figure.run", ...) drawn as literal
                // text, clipped to fragments like "ts…"/"fig…"/"lig…".
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))

                    if let url = ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: genderPreference) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            default:
                                Image(systemName: item.icon)
                                    .font(.system(size: 28))
                                    .foregroundColor(.white)
                            }
                        }
                    } else {
                        Image(systemName: item.icon)
                            .font(.system(size: 28))
                            .foregroundColor(.white)
                    }
                }
                .frame(width: 56, height: 56)

                // Info
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)

                    Text(item.description)
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(2)
                }

                Spacer()

                // Shop arrow
                Image(systemName: "cart.fill")
                    .font(.system(size: 18))
                    .foregroundColor(AppTheme.primaryColor)
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.05))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            )
        }
    }

    private func openAmazon() {
        // Same link the outfit cards open (see ShopModal.openAmazon).
        if let url = item.affiliateURL(for: genderPreference, subtag: subtag) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Preview

#Preview {
    ShopModal(
        items: [
            ClothingItem(key: "SHORTS", name: "Running Shorts (5-7\")", description: "Standard running shorts", icon: "figure.run", amazonSearchTerm: "running shorts 5 inch"),
            ClothingItem(key: "SHORT_SLEEVE", name: "Short Sleeve Shirt", description: "Moisture-wicking technical fabric", icon: "tshirt.fill", amazonSearchTerm: "running short sleeve shirt dri-fit"),
            ClothingItem(key: "BASEBALL_CAP", name: "Running Cap", description: "Sun and light rain protection", icon: "baseball.cap.fill", amazonSearchTerm: "running cap lightweight")
        ],
        genderPreference: .male,
        weatherCode: 0,
        isPresented: .constant(true)
    )
}
