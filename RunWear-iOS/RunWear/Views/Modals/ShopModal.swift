import SwiftUI

struct ShopModal: View {
    let items: [ClothingItem]
    let genderPreference: GenderPreference
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
                Text(item.icon)
                    .font(.system(size: 44))
                    .frame(width: 72, height: 72)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.1))
                    )
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
        let searchTerm = genderPreference.buildSearchTerm(item.amazonSearchTerm)
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.amazonSearchTerm

        let urlString = "https://www.amazon.com/s?k=\(searchTerm)&tag=\(affiliateTag)&subtag=\(subtag)"

        if let url = URL(string: urlString) {
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
        let searchTerm = genderPreference.buildSearchTerm("running gear")
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "running+gear"

        let urlString = "https://www.amazon.com/s?k=\(searchTerm)&tag=\(affiliateTag)&subtag=\(subtag)"

        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
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
                // Icon
                Text(item.icon)
                    .font(.system(size: 32))
                    .frame(width: 56, height: 56)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.1))
                    )

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
        let searchTerm = genderPreference.buildSearchTerm(item.amazonSearchTerm)
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.amazonSearchTerm

        let urlString = "https://www.amazon.com/s?k=\(searchTerm)&tag=\(affiliateTag)&subtag=\(subtag)"

        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Preview

#Preview {
    ShopModal(
        items: [
            ClothingItem(name: "Running Shorts", description: "Lightweight breathable shorts", icon: "🩳", amazonSearchTerm: "running shorts"),
            ClothingItem(name: "Tech Shirt", description: "Moisture-wicking performance top", icon: "👕", amazonSearchTerm: "running shirt"),
            ClothingItem(name: "Running Cap", description: "Sun protection for your run", icon: "🧢", amazonSearchTerm: "running cap")
        ],
        genderPreference: .male,
        isPresented: .constant(true)
    )
}
