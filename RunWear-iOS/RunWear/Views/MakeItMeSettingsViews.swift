import SwiftUI

// MARK: - RW-14 "Make It Me" — Settings section, subscription sheet,
// upsell card (spec §3 + §5; strings S1–S14, U1–U2 verbatim).

// MARK: - Settings section (§3)

struct MakeItMeSettingsSection: View {
    @EnvironmentObject var store: PersonalHeroStore
    /// The presentation host owned by SettingsModal (RW-16.7).
    /// This section is CONTENT ONLY: it requests presentations
    /// through the host and owns no sheet/alert bindings of its
    /// own. Build (12) carried four .sheet + three .alert
    /// modifiers here, on a view whose branch is keyed to
    /// store.mode — a profile refresh could restructure the
    /// presentation owner mid-flight, doubling and ghosting
    /// sheets and killing the opt-in commit (RW-16.9 §4/§6).
    @ObservedObject var presentation: MakeItMePresentationHost
    /// WS-5 (D-14): the availability flag, consulted for FUNCTION
    /// only. This section renders in every configuration — with
    /// the flag OFF it carries the plain not-started row (frame
    /// 36) as a static row; the opt-in door behind it is what the
    /// flag keeps shut. (Pre-WS-5 the flag gated the section's
    /// existence at the SettingsModal call site, and the whole
    /// section — with the Delete-my-data row then hosted inside
    /// it — emitted nothing in the shipped configuration.)
    let featureEnabled: Bool
    /// The user's current fit preference, for the consent call.
    let genderPreference: GenderPreference
    /// The cell currently on the main screen, for photo-replacement
    /// regeneration; nil when no recommendation is on screen.
    let currentCombination: () -> PersonalCombination?

    var body: some View {
        // GEN-3 (WS-D): the redesign composes the feature as a
        // GROUP of native Form sections, in spec order — the
        // status group under the "Make It Me" heading, Your photos
        // as its own group, the Premium offer as a card in its own
        // group, and the destructive group last and visually
        // separated — replacing gen-2's one flat stack of
        // same-weight rows (study P2/P5). Settings is a Form
        // (RW-6.7), so the groups render in the same grammar and
        // adaptive colors as the preference sections around them.
        // (Wrapped in a Group so the .task below binds to the
        // whole composition, unambiguously past the Sections'
        // trailing closures.)
        Group {
            statusSection
            if store.mode != .stock {
                photosSection
                offerSection
                destructiveSection
            }

            // WS-5 (D-14): the account-level data delete is NOT a
            // row of this composition — SettingsModal hosts it as
            // the inventory's own section, directly after this
            // one, reachable whether or not this composition's
            // mode content renders. The destructive zone therefore
            // ends the Make It Me block as two adjacent, visually
            // separated groups: the photos delete closing this
            // composition, the data delete in its own group after.
        }
        // W-18: opening Settings re-reads the server profile, so
        // the meter this section renders is the server's current
        // count — a look delivered since the last read (frame 34's
        // included) is counted here, in the same session.
        // GEN-3 (WS-D): the billing offering re-reads with it, so
        // the offer card's price is the store product's current
        // price, never a stale or hardcoded one.
        .task {
            await store.refreshProfile()
            await store.billing.refresh()
        }
    }

    // MARK: Status group (WS-D group 1, under the heading)

    private var statusSection: some View {
        Section {
            switch store.mode {
            case .stock:
                // Frame 36's plain not-started row — present in
                // every flag state (WS-5). With the feature ON it
                // is the opt-in door; with it OFF the row renders
                // statically (no chevron, no dead control): the
                // inventory is visible, the function stays shut.
                if featureEnabled {
                    settingsRow(
                        title: MakeItMeSettingsRow.notStarted.title,
                        subtitle: "Make your outfit photos from your own photos.",
                        action: { presentation.present(.optIn) }
                    )
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(MakeItMeSettingsRow.notStarted.title)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("Make your outfit photos from your own photos.")
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
            case .free, .subscribed:
                // ONE status block (WS-D group 1): the state line
                // and the free-look meter composed together from
                // the shared seam — one source of truth, so the
                // block can never contradict itself.
                VStack(alignment: .leading, spacing: 4) {
                    Text("You're in your photos.")
                        .font(.system(size: 16, weight: .semibold))
                    // WS-8 (D-25 residue class): the state/meter
                    // lines render in the design's secondary token
                    // pair (AppTheme.inkSecondary), not system
                    // .secondary (≈3.3–3.4:1 on light grounds).
                    if store.mode == .subscribed {
                        // Frame 37: management only — this line is
                        // a state fact (§9), not a pitch.
                        Text("Your photo is ready before you open the app.")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                    } else {
                        // W-18: composed through FreeLooksMeter
                        // from the store's ONE counter — the same
                        // value frame 34's meter is composed from,
                        // so the two surfaces cannot keep separate
                        // books again.
                        Text(FreeLooksMeter.settingsLine(used: store.freeGenerationsUsed, allowance: PersonalHeroStore.freeAllowance))
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                        // GEN-3 (X-9): while a look is in flight,
                        // the block carries it as PENDING under the
                        // delivered count — the meter stays the
                        // delivered truth (the server charges on
                        // delivery), and the block as a whole never
                        // reads as settled while the flow is
                        // visibly making a look.
                        if let pending = FreeLooksMeter.settingsPendingLine(inFlight: store.looksInFlight) {
                            Text(pending)
                                .font(.system(size: 14))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                }
            }
        } header: {
            // RW-26 (notes §7.5): "Make It Me" is a script
            // wordmark wherever it appears as a title.
            Text("Make It Me")
                .font(.greatVibes(size: 20))
                .textCase(nil)
                .foregroundColor(AppTheme.inkPrimary)
        }
    }

    // MARK: Your photos group (WS-D group 2)

    private var photosSection: some View {
        Section {
            settingsRow(
                title: MakeItMeSettingsRow.yourPhotos.title,
                subtitle: "View or replace",
                action: { presentation.present(.photos) }
            )
        }
    }

    // MARK: Premium offer group (WS-D group 3)

    private var offerSection: some View {
        Section {
            if store.mode == .subscribed {
                // State-aware (WS-D): a subscriber sees the
                // subscribed state instead of the offer, with the
                // frame-37 management door under it.
                VStack(alignment: .leading, spacing: 4) {
                    Text(MakeItMeSettingsRow.premium.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text("You're subscribed.")
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.inkSecondary)
                }
                // GEN-3 (WS-D addition, Sol Nov 4): while the
                // bank is still generating, the subscribed state
                // carries the SAME "Making your looks" element as
                // home — the shared ConvergenceBandView, driven by
                // the shared ConvergenceBand.content rule over the
                // store's banking state (this section's .task
                // refreshes it via refreshProfile). One element,
                // two seats: any treatment Sol rules for the band
                // applies in both places at once. Subscribed +
                // generating → renders; subscribed + complete →
                // the rule returns nil; free users never reach
                // this branch at all.
                if let band = ConvergenceBand.content(mode: store.mode, coverage: store.bankCoverage) {
                    ConvergenceBandView(content: band)
                }
                settingsRow(
                    title: MakeItMeSettingsRow.manageSubscription.title,
                    subtitle: nil,
                    action: { Task { await store.billing.showManageSubscriptions() } }
                )
            } else {
                // Frame 35's card, made legible as an offer
                // (WS-D): the name "Premium", the price with term
                // from the store product, the settled two-benefit
                // copy, and a real button opening the paywall.
                MakeItMePremiumOfferCard(billing: store.billing) {
                    presentation.present(.subscription)
                }
            }
        }
    }

    // MARK: Destructive group (WS-D group 4 — last, separated)

    private var destructiveSection: some View {
        Section {
            Button(role: .destructive) {
                presentation.alert = .confirmDelete
            } label: {
                Text(MakeItMeSettingsRow.deletePhotos.title)
                    .font(.system(size: 16, weight: .medium))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func settingsRow(title: String, subtitle: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    // WS-8: the row's title/subtitle/chevron take
                    // the design's two text tiers (inkPrimary /
                    // inkSecondary) — the system .primary/.secondary
                    // pair is an off-table third structure, and its
                    // secondary side fails the light-mode floor.
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(AppTheme.inkPrimary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AppTheme.inkSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - The Premium offer card (GEN-3 · WS-D)
//
// Frame 35's quiet card was quiet to the point of illegibility
// (study P1): no Premium name as an offer, no price, no button —
// a user could not tell it was an offer, let alone act on it.
// This card keeps the settled two-benefit copy and the state
// awareness, and carries what an offer must: the name "Premium",
// the price with term composed from the fetched store product
// (parameterized — $9.99/year control, never hardcoded; no price
// line at all while the offering is unresolved), and a real
// "Get Premium" button that opens the paywall. It observes the
// billing object directly so the price lands the moment the
// offering resolves.
private struct MakeItMePremiumOfferCard: View {
    @ObservedObject var billing: MakeItMeBilling
    let onGetPremium: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(MakeItMeSettingsRow.premium.title)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(AppTheme.inkPrimary)
            // The settled two-benefit copy (§9 line, verbatim):
            // looks made ahead, without limit.
            Text("Made ahead, without limit — every outfit is ready when you open the app.")
                .font(.system(size: 14))
                .foregroundColor(AppTheme.inkSecondary)
            if let priceLine = MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: billing.annualPriceString) {
                Text(priceLine)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(AppTheme.inkPrimary)
            }
            Button(action: onGetPremium) {
                Text("Get Premium")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.primaryColor))
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(.vertical, 6)
    }
}

// MARK: - Your photos (§3)

struct MakeItMePhotosView: View {
    let onReplace: () -> Void
    @StateObject private var loader = ReferencePhotosLoader(fetcher: PersonalHeroAPI.shared)

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Your photos")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.inkPrimary)

                    switch loader.state {
                    case .loaded(let imageURLs):
                        HStack(spacing: 12) {
                            ForEach(imageURLs, id: \.absoluteString) { url in
                                ReferencePhotoTileView(url: url)
                                    .frame(width: 96, height: 96)
                                    .clipped()
                                    .cornerRadius(12)
                            }
                        }
                    case .empty:
                        Text("No photos on the server.")
                            .font(.system(size: 15))
                            .foregroundColor(AppTheme.inkSecondary)
                    case .failed(let message):
                        VStack(alignment: .leading, spacing: 12) {
                            Text(message)
                                .font(.system(size: 15))
                                .foregroundColor(AppTheme.inkPrimary)
                            Button {
                                Task { await loader.load() }
                            } label: {
                                Text("Try again")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(AppTheme.inkPrimary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(AppTheme.cardFill))
                            }
                        }
                    case .idle, .loading:
                        Text("Loading…")
                            .font(.system(size: 15))
                            .foregroundColor(AppTheme.inkSecondary)
                    }

                    // Frame 38: the epoch rule, in plain words —
                    // a retake re-makes what's AHEAD and never
                    // rewrites the collection behind it.
                    Text("This is the photo your looks are made from. If you retake it, the looks ahead are re-made from the new photo — the days already in your collection stay as they are.")
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.inkSecondary)

                    Button(action: onReplace) {
                        Text("Replace photos")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.cardFill))
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

/// One photo tile, driven by its own ReferenceTileLoader
/// (RW-16.9). Every phase is visibly itself: the image, a
/// labelled progress skeleton while loading, or the failure
/// tile with its per-tile retry. No phase renders as the
/// silent faint box that made build (12)'s stalls invisible.
private struct ReferencePhotoTileView: View {
    @StateObject private var tileLoader: ReferenceTileLoader

    init(url: URL) {
        _tileLoader = StateObject(wrappedValue: ReferenceTileLoader(url: url))
    }

    var body: some View {
        Group {
            switch tileLoader.phase {
            case .loaded(let image):
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            case .failed:
                Button {
                    tileLoader.retry()
                } label: {
                    ZStack {
                        AppTheme.cardFill
                        VStack(spacing: 4) {
                            Image(systemName: "exclamationmark.triangle")
                                .foregroundColor(AppTheme.inkSecondary)
                            Text("Tap to retry")
                                .font(.system(size: 10))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                }
            case .idle, .loading:
                ZStack {
                    AppTheme.cardFill
                    VStack(spacing: 4) {
                        ProgressView()
                            .tint(AppTheme.inkSecondary)
                        Text("Loading…")
                            .font(.system(size: 10))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
            }
        }
        .onAppear { tileLoader.loadIfNeeded() }
    }
}

// MARK: - Subscription sheet — the paywall (frame 22; notes §§5–6)
//
// ONE design at every price: the price is a parameterized slot
// rendered ONLY from the fetched RevenueCat Offering (the
// experiment's variable — $9.99 control / $19.99 / $29.99 arms);
// when the Offering has not resolved the slot is price-free.
// Structure: free bar + real meter, "How Premium works", ONE
// highlighted card, the CTA. No Founding / Limited-time /
// struck-through elements anywhere (the §5 contamination fix).

struct MakeItMeSubscriptionSheet: View {
    @EnvironmentObject var store: PersonalHeroStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @State private var purchasing = false
    @State private var unavailableLine: String?
    /// GEN-3 (Sol's Nov 4 resolution): the plan selection —
    /// annual is the hero and the default; the monthly row below
    /// it is the one quiet secondary alternative.
    @State private var selectedPlan: PaywallPlan = MakeItMePriceLabels.defaultPlan

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // The feature's script title (§7.5: 30pt instance).
                    Text("Make It Me")
                        .font(.greatVibes(size: 30))
                        .foregroundColor(AppTheme.inkPrimary)

                    // Free bar + the real meter (§1 wording pass).
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Free plan")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(AppTheme.inkPrimary)
                            Spacer()
                            Text("5 looks included")
                                .font(.system(size: 14))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        Text(FreeLooksMeter.subscriptionLine(used: store.freeGenerationsUsed, allowance: PersonalHeroStore.freeAllowance))
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.sheet))

                    // How Premium works — the convergence copy,
                    // verbatim (§6 audit table, row 3).
                    VStack(alignment: .leading, spacing: 4) {
                        Text("How Premium works")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("Looks of you for every scenario. This week's weather is made first; the rest are made as you need them, and each one is banked for good once it's made.")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                    }

                    // The hero card — the annual plan, and the
                    // default selection. Its price slot is the
                    // Offering's annual price and nothing else.
                    // Tapping it re-selects annual.
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Premium")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("All of Make It Me.")
                            .font(.system(size: 15))
                            .foregroundColor(AppTheme.inkSecondary)
                        if let price = store.billing.annualPriceString {
                            Text("\(price)/yr")
                                .font(.system(size: 19, weight: .bold))
                                .foregroundColor(AppTheme.inkPrimary)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.sheet))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(selectedPlan == .annual ? AppTheme.primaryColor : AppTheme.cardStroke,
                                    lineWidth: selectedPlan == .annual ? 2 : 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 16))
                    .onTapGesture { selectedPlan = .annual }
                    .accessibilityAddTraits(selectedPlan == .annual ? [.isSelected] : [])
                    .accessibilityLabel("Annual plan")

                    // GEN-3 (WS-F): the fine print under the card —
                    // the annual price framed per run with its
                    // basis stated, DERIVED from the displayed
                    // product's annual price (never a hardcoded
                    // cents figure). This replaces the retired
                    // monthly-comparison line; nothing else on the
                    // paywall changes.
                    if let finePrint = store.billing.perRunFinePrint {
                        Text(finePrint)
                            .font(.system(size: 12))
                            .foregroundColor(AppTheme.inkSecondary)
                    }

                    // GEN-3 (Sol's Nov 4 resolution — the monthly
                    // plan IS displayed): ONE quiet secondary
                    // monthly row UNDER the annual hero card. Its
                    // price is parameterized from the fetched
                    // monthly product, exactly like the annual's
                    // (never hardcoded); the row renders only once
                    // that price has resolved. Tapping it selects
                    // monthly; the CTA then purchases monthly.
                    if store.billing.monthlyPriceString != nil {
                        HStack {
                            Text("Monthly")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(AppTheme.inkPrimary)
                            Spacer()
                            Text(store.billing.monthlyRowLabel)
                                .font(.system(size: 15))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.sheet))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(selectedPlan == .monthly ? AppTheme.primaryColor : Color.clear,
                                        lineWidth: 1.5)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 14))
                        .onTapGesture { selectedPlan = .monthly }
                        .accessibilityAddTraits(selectedPlan == .monthly ? [.isSelected] : [])
                        .accessibilityLabel("Monthly plan, \(store.billing.monthlyRowLabel)")
                    }

                    if store.billing.isSubscribed {
                        Text("You're subscribed — your photo is ready before you open the app.")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                    } else {
                        // The CTA carries NO price (§6, row 7).
                        Button {
                            purchase()
                        } label: {
                            HStack {
                                Text("Keep going with Premium")
                                    .font(.system(size: 17, weight: .semibold))
                                if purchasing { ProgressView() }
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                        }
                        .buttonStyle(.plain)
                        .disabled(purchasing)
                    }

                    if let unavailableLine {
                        Text(unavailableLine)
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.weatherAccentColor(.orange, isDark: colorScheme == .dark))
                    }

                    Button {
                        Task {
                            await store.billing.restore()
                            if store.billing.isSubscribed { dismiss() }
                        }
                    } label: {
                        Text("Restore purchases")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
                .padding(24)
            }
        }
        .task { await store.billing.refresh() }
    }

    private func purchase() {
        purchasing = true
        unavailableLine = nil
        Task {
            // The CTA purchases the SELECTED plan — annual by
            // default (and whenever the hero card is tapped),
            // monthly only when its quiet row was selected.
            let outcome = selectedPlan == .annual
                ? await store.billing.purchaseAnnual()
                : await store.billing.purchaseMonthly()
            purchasing = false
            switch outcome {
            case .subscribed:
                dismiss()
            case .cancelled:
                break
            case .unavailable:
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
                .foregroundColor(AppTheme.inkSecondary)
            Text("This outfit isn't you — yet.")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(AppTheme.inkPrimary)
            Text("You've seen yourself in 5 outfits. This is a new one, so it wears a standard photo. Subscribe, and RunWear makes your photo for every outfit in your weather ahead of time — you, on screen the moment you open the app.")
                .font(.system(size: 15))
                .foregroundColor(AppTheme.inkSecondary)
            Button {
                showSubscriptionSheet = true
            } label: {
                Text("See myself in every outfit")
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
                    .foregroundColor(AppTheme.inkSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.cardOnSheet))
        .padding(.horizontal, 16)
        // WS-5 (§1.3): named detent — the paywall's content
        // scrolls, so it rests at .large only.
        .sheet(isPresented: $showSubscriptionSheet) {
            MakeItMeSubscriptionSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }
}

// MARK: - Convergence band (GEN-3, Sol's settled wording Nov 4)

/// The home-sheet band a SUBSCRIBED user sees while the bank of
/// looks is still converging — seated in the personal section,
/// below the outfit items, in the upsell card's sheet language.
/// Copy is Sol's settled wording, word for word (ConvergenceBand
/// carries the strings + the visibility rule). GEN-3 ROUND 2:
/// the band carries the SAME animated spinner as the generating
/// hero pill — the band represents active generation and visibly
/// says so.
struct ConvergenceBandView: View {
    let content: ConvergenceBandContent

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                // The generating hero pill's spinner (ProgressView,
                // mini), in the sheet's pine — same device, sheet
                // seat.
                ProgressView()
                    .controlSize(.mini)
                    .tint(AppTheme.pinePayload)
                Text(content.heading)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(AppTheme.inkPrimary)
            }
            Text(content.line)
                .font(.system(size: 13))
                .foregroundColor(AppTheme.inkSecondary)
            // Progress BAR only — the numeric count is HELD
            // (Sol, Nov 4): no total is rendered or hard-coded;
            // the fraction comes from the real banking state.
            ProgressView(value: content.fraction)
                .progressViewStyle(.linear)
                .tint(AppTheme.pinePayload)
                .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.cardOnSheet))
        .padding(.horizontal, 16)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(content.heading). \(content.line)")
    }
}
