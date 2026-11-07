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
                    // GEN-6 (RW-64): the settled disclosure
                    // substance (frame 44 / §5), all three elements:
                    // the links open Amazon; RunWear may earn a
                    // commission; it never changes the outfit
                    // recommended.
                    Text("Affiliate Disclosure: ")
                        .font(.caption.weight(.bold))
                        .foregroundColor(AppTheme.inkSecondary)
                    + Text("These links open Amazon. As an Amazon Associate, RunWear may earn a commission from qualifying purchases, at no extra cost to you. It never changes the outfit we recommend.")
                        .font(.caption)
                        .foregroundColor(AppTheme.inkSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(AppTheme.cardFill)
                )
                .padding(.horizontal, 24)

                Button(action: dismiss) {
                    Text("Close")
                        .font(.appSecondary.weight(.semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(AppTheme.cardStroke, lineWidth: 1)
                        )
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 16)
            }
            .background(AppTheme.background.ignoresSafeArea())
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
                        .fill(AppTheme.cardFill)

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
                                    .font(.largeTitle)
                                    .foregroundColor(AppTheme.inkSecondary)
                            }
                        }
                    } else {
                        Image(systemName: item.icon)
                            .font(.largeTitle)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
                .frame(width: 72, height: 72)
                Text(item.name)
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Text(item.description)
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkSecondary)
                Button(action: { openAmazon(for: item) }) {
                    HStack(spacing: 6) {
                        Image(systemName: "cart.fill")
                        Text("Shop")
                    }
                    .font(.appSecondary.weight(.semibold))
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
            .background(AppTheme.background.ignoresSafeArea())
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
                    .font(.appSheetTitle)
                    .foregroundColor(AppTheme.inkPrimary)

                Text("\(items.count) items recommended")
                    .font(.appSecondary)
                    .monospacedDigit()
                    .foregroundColor(AppTheme.inkSecondary)
            }

            Spacer()

            // Shop All button
            Button(action: openShopAll) {
                HStack(spacing: 4) {
                    Text("Shop All")
                        .font(.appSecondary.weight(.semibold))
                    Image(systemName: "arrow.right")
                        .font(.appFootnote.weight(.semibold))
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
        // State-driven dismissal (RW-6.12): the system animates the
        // sheet away; no hand-rolled transition wrapper.
        isPresented = false
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
                        .fill(AppTheme.cardOnSheet)

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
                                    .font(.title)
                                    .foregroundColor(AppTheme.inkSecondary)
                            }
                        }
                    } else {
                        Image(systemName: item.icon)
                            .font(.title)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
                .frame(width: 56, height: 56)

                // Info
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name)
                        .font(.appItemName)
                        .foregroundColor(AppTheme.inkPrimary)

                    Text(item.description)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                        // RW-114 (successor wave): item
                        // descriptions render whole — no line
                        // cap, so no mid-word ellipsis (the
                        // modal's other description render
                        // already wraps fully).
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer()

                // GEN-6 (RW-64): the settled per-row door —
                // "Shop ›" as labelled text, replacing the bare
                // cart glyph (the row itself still opens Amazon,
                // or selects for the inspector at regular width).
                Text("Shop ›")
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.primaryColor)
            }
            .padding(12)
            // Flat content card (AppTheme tokens) — not glass.
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppTheme.cardFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(AppTheme.cardStroke, lineWidth: 1)
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
