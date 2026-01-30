import SwiftUI

struct ShopModal: View {
    let items: [ClothingItem]
    let genderPreference: GenderPreference
    @Binding var isPresented: Bool

    @State private var dragOffset: CGFloat = 0
    private let dismissThreshold: CGFloat = 100

    // Affiliate configuration
    private let affiliateTag = "runwear-20"
    private let subtag = "ios"

    var body: some View {
        ZStack {
            // Backdrop
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }

            // Sheet content
            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 0) {
                    // Drag handle
                    dragHandle
                        .padding(.top, 12)

                    // Header with Shop All
                    headerSection
                        .padding(.horizontal, 24)
                        .padding(.top, 16)
                        .padding(.bottom, 8)

                    // Items list
                    ScrollView {
                        VStack(spacing: 12) {
                            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                                ShopItemRow(
                                    item: item,
                                    genderPreference: genderPreference,
                                    affiliateTag: affiliateTag,
                                    subtag: subtag
                                )
                                .staggeredAnimation(index: index)
                            }
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 16)
                    }
                    .frame(maxHeight: UIScreen.main.bounds.height * 0.5)
                }
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.modalCornerRadius)
                        .fill(AppTheme.darkBackground)
                )
                .offset(y: dragOffset)
                .gesture(dragGesture)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .transition(.opacity)
    }

    // MARK: - Drag Handle

    private var dragHandle: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(Color.white.opacity(0.3))
            .frame(width: 40, height: 5)
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

    // MARK: - Gestures

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if value.translation.height > 0 {
                    dragOffset = value.translation.height
                }
            }
            .onEnded { value in
                if value.translation.height > dismissThreshold {
                    dismiss()
                } else {
                    withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
                        dragOffset = 0
                    }
                }
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

    var body: some View {
        Button(action: openAmazon) {
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
