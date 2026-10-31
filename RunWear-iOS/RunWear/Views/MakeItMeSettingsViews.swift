import SwiftUI

// MARK: - RW-14 "Make it me" — Settings section, subscription sheet,
// upsell card (spec §3 + §5; strings S1–S14, U1–U2 verbatim).

// MARK: - Settings section (§3)

struct MakeItMeSettingsSection: View {
    @EnvironmentObject var store: PersonalHeroStore
    /// The user's current fit preference, for the consent call.
    let genderPreference: GenderPreference
    /// The cell currently on the main screen, for photo-replacement
    /// regeneration; nil when no recommendation is on screen.
    let currentCombination: () -> PersonalCombination?

    @State private var showOptInFlow = false
    @State private var showReplaceFlow = false
    @State private var showPhotos = false
    @State private var showSubscriptionSheet = false
    @State private var confirmDelete = false
    @State private var showDeletedConfirmation = false
    @State private var deleteFailed = false

    var body: some View {
        // A native Form section — Settings is a Form (RW-6.7), so
        // this renders in the same grammar and adaptive colors as
        // the preference sections around it.
        Section {
            switch store.mode {
            case .stock:
                settingsRow(
                    title: "See yourself in every outfit",
                    subtitle: "Make your outfit photos from your own photos.",
                    action: { showOptInFlow = true }
                )
            case .free, .subscribed:
                VStack(alignment: .leading, spacing: 4) {
                    Text("You're in your photos.")
                        .font(.system(size: 16, weight: .semibold))
                    if store.mode == .subscribed {
                        Text("Subscribed — your photo is ready for every outfit, instantly.")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    } else if store.freeGenerationsUsed >= PersonalHeroStore.freeAllowance {
                        Text("All 5 free looks used")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    } else {
                        Text("\(store.freeGenerationsUsed) of 5 free looks used")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                }

                settingsRow(
                    title: "Your photos",
                    subtitle: "View or replace",
                    action: { showPhotos = true }
                )

                if store.mode == .subscribed {
                    settingsRow(
                        title: "Manage subscription",
                        subtitle: nil,
                        action: { Task { await store.billing.showManageSubscriptions() } }
                    )
                } else {
                    settingsRow(
                        title: "Every outfit, instantly",
                        subtitle: "Subscribe, and your photo is ready before you open the app. $0.99/month.",
                        action: { showSubscriptionSheet = true }
                    )
                }

                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Text("Delete my photos")
                        .font(.system(size: 16, weight: .medium))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        } header: {
            Text("Make it me")
        }
        .sheet(isPresented: $showOptInFlow) {
            MakeItMeOptInFlow(
                mode: .optIn,
                onCommit: { photos in
                    try await store.completeOptIn(jpegPhotos: photos, genderPreference: genderPreference)
                },
                onFinish: { showOptInFlow = false },
                onCancel: { showOptInFlow = false }
            )
        }
        .sheet(isPresented: $showReplaceFlow) {
            MakeItMeOptInFlow(
                mode: .replace,
                onCommit: { photos in
                    try await store.replaceReferences(jpegPhotos: photos, currentCombination: currentCombination())
                },
                onFinish: { showReplaceFlow = false },
                onCancel: { showReplaceFlow = false }
            )
        }
        .sheet(isPresented: $showPhotos) {
            MakeItMePhotosView(onReplace: {
                showPhotos = false
                showReplaceFlow = true
            })
        }
        .sheet(isPresented: $showSubscriptionSheet) {
            MakeItMeSubscriptionSheet()
        }
        .alert("Delete your photos?", isPresented: $confirmDelete) {
            Button("Delete my photos", role: .destructive) {
                Task {
                    do {
                        try await store.deleteEverything()
                        showDeletedConfirmation = true
                    } catch {
                        // The end-state copy may only claim what the
                        // system confirmed (§3): on failure nothing
                        // was deleted and the section stays as it was.
                        deleteFailed = true
                    }
                }
            }
            Button("Keep my photos", role: .cancel) {}
        } message: {
            Text("Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make it me turns off. This can't be undone.")
        }
        .alert("Your photos and pictures are deleted.", isPresented: $showDeletedConfirmation) {
            Button("OK", role: .cancel) {}
        }
        .alert("Your photos could not be deleted. Please try again.", isPresented: $deleteFailed) {
            Button("OK", role: .cancel) {}
        }
    }

    private func settingsRow(title: String, subtitle: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.primary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Your photos (§3)

struct MakeItMePhotosView: View {
    let onReplace: () -> Void
    @StateObject private var loader = ReferencePhotosLoader(fetcher: PersonalHeroAPI.shared)

    var body: some View {
        ZStack {
            AppTheme.darkBackground.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Your photos")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)

                    switch loader.state {
                    case .loaded(let imageURLs):
                        HStack(spacing: 12) {
                            ForEach(Array(imageURLs.enumerated()), id: \.offset) { _, url in
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().aspectRatio(contentMode: .fill)
                                    default:
                                        Color.white.opacity(0.08)
                                    }
                                }
                                .frame(width: 96, height: 96)
                                .clipped()
                                .cornerRadius(12)
                            }
                        }
                    case .empty:
                        Text("No photos on the server.")
                            .font(.system(size: 15))
                            .foregroundColor(.white.opacity(0.6))
                    case .idle, .loading:
                        Text("Loading…")
                            .font(.system(size: 15))
                            .foregroundColor(.white.opacity(0.6))
                    }

                    Button(action: onReplace) {
                        Text("Replace photos")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.1)))
                    }
                }
                .padding(24)
            }
        }
        .task {
            await loader.load()
        }
    }
}

// MARK: - Subscription sheet (§5.3, strings U2)

struct MakeItMeSubscriptionSheet: View {
    @EnvironmentObject var store: PersonalHeroStore
    @Environment(\.dismiss) private var dismiss
    @State private var purchasing = false
    @State private var unavailableLine: String?

    var body: some View {
        ZStack {
            AppTheme.darkBackground.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Every outfit, instantly.")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    priceRow(
                        label: "\(store.billing.monthlyPriceString ?? "$0.99") / month",
                        action: { purchase(annual: false) }
                    )
                    priceRow(
                        label: "\(store.billing.annualPriceString ?? "$9.99") / year — about 16% less than paying monthly",
                        action: { purchase(annual: true) }
                    )

                    if let unavailableLine {
                        Text(unavailableLine)
                            .font(.system(size: 14))
                            .foregroundColor(.orange)
                    }

                    Button {
                        Task {
                            await store.billing.restore()
                            if store.billing.isSubscribed { dismiss() }
                        }
                    } label: {
                        Text("Restore purchases")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                    }

                    // U2c — the honest edge, on the purchase surface
                    // itself, before money moves.
                    Text("Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about 10 seconds to make the first time — after that, it's ready instantly. Cancel any time in your App Store or Google Play subscriptions.")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.5))
                }
                .padding(24)
            }
        }
        .task { await store.billing.refresh() }
    }

    private func priceRow(label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.leading)
                Spacer()
                if purchasing { ProgressView().tint(.white) }
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.08)))
        }
        .buttonStyle(.plain)
        .disabled(purchasing)
    }

    private func purchase(annual: Bool) {
        purchasing = true
        unavailableLine = nil
        Task {
            let outcome = annual ? await store.billing.purchaseAnnual() : await store.billing.purchaseMonthly()
            purchasing = false
            switch outcome {
            case .subscribed:
                dismiss()
            case .cancelled:
                break
            case .unavailable:
                // DEVIATION-COPY (no deck entry): the products exist
                // in App Store Connect but may not resolve in the
                // store yet — say so instead of failing silently.
                unavailableLine = "Purchases aren't available right now. Please try again later."
            }
        }
    }
}

// MARK: - The 6th-cell upsell card (§5.1–5.3, strings U1)

struct MakeItMeUpsellCard: View {
    @EnvironmentObject var store: PersonalHeroStore
    let currentCell: String
    @State private var showSubscriptionSheet = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("MAKE IT ME")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white.opacity(0.5))
            Text("This outfit isn't you — yet.")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
            Text("You've seen yourself in 5 outfits. This is a new one, so it wears a standard photo. Subscribe, and RunWear makes your photo for every outfit in your weather ahead of time — you, on screen the moment you open the app.")
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
            Button {
                showSubscriptionSheet = true
            } label: {
                Text("See myself in every outfit — $0.99/month")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
            }
            Button {
                store.dismissUpsell(currentCell: currentCell)
            } label: {
                Text("Not now")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white.opacity(0.6))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.06)))
        .padding(.horizontal, 16)
        .sheet(isPresented: $showSubscriptionSheet) {
            MakeItMeSubscriptionSheet()
        }
    }
}
