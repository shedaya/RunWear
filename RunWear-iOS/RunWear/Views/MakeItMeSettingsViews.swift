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
            }

            // GEN-6 (RW-56): NO destructive group in this
            // composition. Both destructive rows (Delete my
            // photos, Delete my data) live in SettingsModal's ONE
            // quarantined "Danger zone" section, seated last in
            // the Settings inventory — gen-5's split across two
            // identically-headed sections is consolidated there.
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

    /// GEN-5 (RW-28 item 22): while a subscriber's bank is
    /// generating, the "Making your looks" band REPLACES the
    /// status/offer content in Settings (it does not sit below
    /// the Premium card alongside the status it replaces — the
    /// gen-4 B-12 defect).
    private var settingsConvergenceBand: ConvergenceBandContent? {
        // GEN-5 (RW-29 FA-4): the store's one band gate
        // (trial users never see the band).
        store.convergenceBand
    }

    private var statusSection: some View {
        Section {
            if store.mode == .subscribed, let band = settingsConvergenceBand {
                ConvergenceBandView(content: band)
            } else {
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
                        // GEN-5 (RW-28 item 26): the verbatim
                        // subscribed line (frame 37 / §13) — the
                        // gen-4 build drifted to an em-dash
                        // lowercase form on the sibling platform
                        // (B-17); iOS renders the settled form.
                        Text("Subscribed · Your photo is ready before you open the app.")
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

    /// GEN-6 (RW-57): the loader behind the row's selfie
    /// thumbnail — the same ReferencePhotosLoader seam the Your
    /// Photos screen uses, over the same API.
    @StateObject private var thumbnailLoader = ReferencePhotosLoader(fetcher: PersonalHeroAPI.shared)

    private var photosSection: some View {
        Section {
            // GEN-6 (RW-57): the row carries the user's own
            // selfie thumbnail (design-spec §13) in the free and
            // subscribed states alike — it was a text-only row.
            Button { presentation.present(.photos) } label: {
                HStack(spacing: 12) {
                    photosThumbnail
                    VStack(alignment: .leading, spacing: 2) {
                        Text(MakeItMeSettingsRow.yourPhotos.title)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("View or replace")
                            .font(.system(size: 13))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AppTheme.inkSecondary)
                }
            }
            .task {
                if thumbnailLoader.state == .idle {
                    await thumbnailLoader.load()
                }
            }
        }
    }

    /// The first reference photo as a small rounded thumbnail;
    /// a neutral photo glyph until (or unless) it loads — the
    /// row's door works regardless.
    @ViewBuilder
    private var photosThumbnail: some View {
        if case .loaded(let urls) = thumbnailLoader.state, let url = urls.first {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    Image(systemName: "person.crop.rectangle")
                        .foregroundColor(AppTheme.inkSecondary)
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            Image(systemName: "person.crop.rectangle")
                .font(.system(size: 20))
                .foregroundColor(AppTheme.inkSecondary)
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 10).fill(AppTheme.cardOnSheet))
        }
    }

    // MARK: Premium offer group (WS-D group 3)

    private var offerSection: some View {
        Section {
            if store.mode == .subscribed {
                // State-aware (WS-D): a subscriber sees management
                // only — GEN-6 (RW-57): the unlisted "Premium /
                // You're subscribed." block that sat in this seat
                // is REMOVED; the subscribed state is already
                // stated by the status block above, and this seat
                // carries the frame-37 management door only.
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
                // GEN-5: no second band here — while generating,
                // the band renders in place of the status block
                // above (settingsConvergenceBand).
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
            // GEN-5 (RW-28, C-5 + C-6): the monthly price joins
            // the Settings card as a MODEST tappable element — a
            // bordered row in the paywall row's family, clearly
            // subordinate to the annual headline and its filled
            // button; tapping it opens the same paywall, where
            // monthly is selectable.
            if let monthlyLabel = MakeItMePriceLabels.settingsMonthlyLabel(monthlyPriceString: billing.monthlyPriceString) {
                Button(action: onGetPremium) {
                    HStack(spacing: 8) {
                        Circle()
                            .stroke(AppTheme.inkSecondary, lineWidth: 1.5)
                            .frame(width: 16, height: 16)
                        Text(monthlyLabel)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 10).fill(AppTheme.cardFill))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppTheme.cardStroke, lineWidth: 1))
                    .contentShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Monthly plan, \(monthlyLabel)")
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
        // GEN-5 (cream map row 30): the offer card carries its
        // OWN quiet-card surface inside the sheet-toned section
        // row — the design's card-on-sheet treatment (fill,
        // padding, rounded corners, hairline) — where build 25
        // laid the card's contents bare on the row. (C-5's
        // ruling keeps the prices on the card; the design
        // note's no-price line applies to the store listing,
        // not this in-app card.)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.cardOnSheet))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(AppTheme.cardStroke, lineWidth: 1))
    }
}

// MARK: - Your photos (§3)

struct MakeItMePhotosView: View {
    let onReplace: () -> Void
    /// GEN-5 (RW-29 EX-10): the surface's own delete door —
    /// raises the SAME confirmation the Settings danger zone
    /// raises (one alert slot, one deletion path).
    var onDeletePhotos: () -> Void = {}
    @StateObject private var loader = ReferencePhotosLoader(fetcher: PersonalHeroAPI.shared)

    /// GEN-5 (RW-29 EX-10): the arrival stamp for the settled
    /// "Added {date}" line. iOS keeps no server-side arrival
    /// timestamp for the reference set, so the stamp is the
    /// device's own first-sight record: the day this exact set
    /// of reference photos was first seen on this phone. A new
    /// set (a replace) re-stamps.
    @State private var arrivalDate: Date?

    private static let arrivalFingerprintKey = "rw.gen5.referenceArrival.fingerprint"
    private static let arrivalDateKey = "rw.gen5.referenceArrival.date"

    private func noteLoadedSet(_ urls: [URL]) {
        guard !urls.isEmpty else { return }
        let fingerprint = urls.map(\.absoluteString).joined(separator: "|")
        let defaults = UserDefaults.standard
        if defaults.string(forKey: Self.arrivalFingerprintKey) == fingerprint,
           let stamp = defaults.object(forKey: Self.arrivalDateKey) as? Date {
            arrivalDate = stamp
        } else {
            let now = Date()
            defaults.set(fingerprint, forKey: Self.arrivalFingerprintKey)
            defaults.set(now, forKey: Self.arrivalDateKey)
            arrivalDate = now
        }
    }

    private var arrivalLine: String {
        // Frame 38/EX-10's form: "Added Nov 2 · Every look is
        // made from this one." Without a stamp (no set loaded
        // yet) the line keeps its settled second half alone.
        guard let arrivalDate else { return "Every look is made from this one." }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return "Added \(formatter.string(from: arrivalDate)) · Every look is made from this one."
    }

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // GEN-5 (RW-28 item 21): the settled Your
                    // Photos surface (frames 38–39) — the title
                    // zone carries the Make It Me script, the one
                    // current photo is labelled as such, the epoch
                    // rule has its own heading, and the two doors
                    // are distinct (the gen-4 build collapsed them
                    // into one generic "Replace photos" button).
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Make It Me")
                            .font(.greatVibes(size: 26))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("Your photos")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(AppTheme.inkPrimary)
                    }

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

                    // The one current photo, labelled (frame 38).
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your current photo")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text(arrivalLine)
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                    }

                    // Frame 38: the epoch rule under its own
                    // heading — a retake re-makes what's AHEAD and
                    // never rewrites the collection behind it.
                    VStack(alignment: .leading, spacing: 4) {
                        Text("A new photo starts a new set")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("Retake your selfie and new looks are made from the new photo. Looks already saved in Wear I've Run stay exactly as they are.")
                            .font(.system(size: 14))
                            .foregroundColor(AppTheme.inkSecondary)
                    }

                    // The two distinct doors (frame 38): the
                    // guided capture and the upload door. Both run
                    // the replace flow, whose source choice opens
                    // on exactly these two doors.
                    Button(action: onReplace) {
                        Text("Retake Selfie")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                    }
                    Button(action: onReplace) {
                        Text("Choose a Different Photo")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.cardFill))
                    }

                    // GEN-5 (RW-29 EX-10): the delete door ON
                    // the surface itself — frame 39 hangs the
                    // erasure off this screen, not only off the
                    // Settings list behind it. Same confirmation,
                    // same deletion path as the danger zone.
                    Button(role: .destructive, action: onDeletePhotos) {
                        Text("Delete my photos")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                }
                .padding(24)
            }
        }
        .task {
            await loader.load()
        }
        .onChange(of: loader.state) { _, newState in
            if case .loaded(let urls) = newState { noteLoadedSet(urls) }
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
                        // GEN-5 (RW-28 item 29): the per-run line
                        // DIRECTLY under the annual price (the
                        // gen-4 card omitted it; the 4¢ idea lived
                        // only in the fine print below).
                        if let perRun = MakeItMePriceLabels.perRunLine(annual: store.billing.annualPriceAmount) {
                            Text(perRun)
                                .font(.system(size: 13))
                                .foregroundColor(AppTheme.inkSecondary)
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

                    // GEN-3 (WS-F) as settled by GEN-6 (DA-20 +
                    // RW-58): the fine print under the card carries
                    // the per-run line's BASIS ONLY, in the settled
                    // wording, DERIVED from the displayed product's
                    // annual price — the per-run claim itself is
                    // stated exactly once, under the price above.
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
                            // GEN-5 (item 29): the row IS the settled
                            // label — "Monthly — $0.99/month".
                            Text(store.billing.monthlyRowLabel)
                                .font(.system(size: 15))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.sheet))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                // GEN-5 (C-6): visibly tappable even
                                // unselected — a modest bordered row,
                                // never a co-equal filled button.
                                .stroke(selectedPlan == .monthly ? AppTheme.primaryColor : AppTheme.cardStroke,
                                        lineWidth: 1.5)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 14))
                        .onTapGesture { selectedPlan = .monthly }
                        .accessibilityAddTraits(selectedPlan == .monthly ? [.isSelected] : [])
                        .accessibilityLabel("Monthly plan, \(store.billing.monthlyRowLabel)")
                    }

                    if store.billing.isSubscribed {
                        Text("Subscribed · Your photo is ready before you open the app.")
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
            // GEN-6 (DA-22): the feature's name as a title is the
            // Make It Me SCRIPT, like every other title instance
            // in the app — never a bold sans caps eyebrow.
            Text("Make It Me")
                .font(.greatVibes(size: 25))
                .foregroundColor(AppTheme.inkPrimary)
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

// MARK: - Your looks — the looks library (GEN-6 RW-61; frame 21 / §17)
//
// The designed library surface: every look the store knows
// about, as a titled cell. A gate-failed cell is labelled
// "…stock photo for now" and carries the "Why am I seeing a
// stock photo?" door, which speaks the policy's explanation
// verbatim (PersonalServePolicy.failedExplanation — wired here,
// not rewritten). Reached one tap away from the failed cell's
// home line ("See all your looks"); never badged on home.
struct YourLooksLibraryView: View {
    @EnvironmentObject var store: PersonalHeroStore
    @Environment(\.dismiss) private var dismiss
    @State private var showFailedExplanation = false

    private var cells: [LooksLibrary.Cell] {
        LooksLibrary.cells(from: store.lookStates)
    }

    var body: some View {
        ScrollView {
            if cells.isEmpty {
                Text("Your looks will appear here as they're made.")
                    .font(.system(size: 15))
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 48)
                    .padding(.horizontal, 32)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 16) {
                    ForEach(cells) { cell in
                        libraryCell(cell)
                    }
                }
                .padding(16)
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("Your looks")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
            }
        }
        .alert("Why am I seeing a stock photo?", isPresented: $showFailedExplanation) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(PersonalServePolicy.failedExplanation)
        }
    }

    private func libraryCell(_ cell: LooksLibrary.Cell) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            RoundedRectangle(cornerRadius: 12)
                .fill(AppTheme.cardOnSheet)
                .aspectRatio(0.8, contentMode: .fit)
                .overlay {
                    Image(systemName: cell.isFailed ? "photo" : "person.crop.rectangle")
                        .font(.system(size: 28))
                        .foregroundColor(AppTheme.inkSecondary)
                }
            Text(cell.titleLine)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(AppTheme.inkPrimary)
            Text(cell.statusLine)
                .font(.system(size: 13))
                .foregroundColor(AppTheme.inkSecondary)
            if cell.isFailed {
                Button("Why am I seeing a stock photo?") {
                    showFailedExplanation = true
                }
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppTheme.inkPrimary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

