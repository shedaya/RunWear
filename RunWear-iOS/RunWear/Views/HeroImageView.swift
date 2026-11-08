import SwiftUI
import UIKit
import CryptoKit

struct HeroImageView: View {
    let locationName: String
    /// The hero headline number: the RAW measured feels-like, in the
    /// user's selected unit — the contract Android and the PWA ship
    /// (their 96pt number is feelsLike / effectiveTemperature, under
    /// a "FEELS LIKE" caption). This view previously received the
    /// ACTUAL temperature here and the sub-line below showed a
    /// comfort-adjusted "feels like" that existed on neither
    /// reference surface.
    let temperature: Double
    /// The raw ACTUAL temperature for the "Actual:" sub-line (the
    /// reference surfaces' second line), same unit as `temperature`.
    let actualTemperature: Double
    /// The unit `temperature`/`actualTemperature` are expressed in —
    /// rendered WITH the numbers. The hero previously printed a bare
    /// "12°" with no unit anywhere while the outfit band below
    /// carried a hard-coded "°F" range: read in the user's Celsius,
    /// the two told different stories on one screen (field finding
    /// 2026-10-31). Android's hero carries the symbol on both lines.
    var temperatureUnit: TemperatureUnit = .fahrenheit
    let weatherCondition: WeatherCondition
    let date: Date
    let hour: Int
    let tempBracket: HeroTempBracket

    // Image URLs
    let fallbackImageUrl: String
    let aiImageUrl: String?

    // Callbacks
    var onSettingsTapped: () -> Void = {}
    var onShareTapped: () -> Void = {}
    var onLocationTapped: () -> Void = {}
    var onDateTapped: () -> Void = {}
    var onTimeTapped: () -> Void = {}
    var onNowTapped: () -> Void = {}

    // Hero-photo flag (2026-10-29, parity with Android): the exact
    // photo on screen and the callback for when the user picks a
    // reason. A nil target renders no chip — there is no resolved
    // photo to flag yet.
    var flagTarget: HeroFlagTarget? = nil
    var onFlagHero: (HeroFlagReason) -> Void = { _ in }

    // Personal-hero flag (RW-14 acceptance (19), spec §4): when
    // the photo on screen is a PERSONALIZED hero, the same chip
    // offers the personal menu — headed by "This doesn't look
    // like me" — and a pick routes to the backend's flag_hero
    // action instead of the pool insert. A non-nil personal
    // target takes precedence over flagTarget: both describe the
    // URL on screen then, but only the personal target names the
    // photo that's actually up.
    var personalFlagTarget: PersonalHeroFlagTarget? = nil
    var onFlagPersonalHero: (PersonalHeroFlagReason) -> Void = { _ in }

    // GEN-3 (WS-A(2)): THE hero pill — one component, one position
    // (bottom of the hero image), state-driven by HeroPill from the
    // requested cell's facts. .hidden renders nothing. The freeLook
    // state's tap opens the Premium paywall via the callback.
    var pillState: HeroPillState = .hidden
    var onPillGetUnlimited: () -> Void = {}

    // RW-111 (successor wave): the failed look's surface AT THE
    // HERO — the settled failure line in the pill's eye line over
    // the stock hero, the whole line tappable to retry (the copy
    // itself says "tap to try again"; Android renders the same
    // line over the hero). Before this, the line lived in the
    // scroll content at the bottom of the page, far from the
    // hero the user was looking at. Rendered only while the pill
    // itself is hidden — the failed state resolves no pill, and
    // on the one overlap (a refused retry on the exhausted cell)
    // the exhausted pill is the surface and this line yields.
    var lookFailed: Bool = false
    var onRetryFailedLook: () -> Void = {}

    /// How the hero's height is derived (design record §5.2: no
    /// UIScreen.main anywhere — the container decides).
    enum HeightMode {
        /// Fraction of the container's height (the original
        /// full-screen layout, where the container is the screen).
        case containerFraction
        /// Fill the height the parent proposes (pane layouts size
        /// the hero from their own geometry).
        case fill
    }
    var heightMode: HeightMode = .containerFraction

    /// The floating glass pills (location / share / settings) are
    /// chrome, and chrome belongs to the container: when Home sits
    /// in a NavigationStack the location is the nav title and
    /// Settings/Share are toolbar items, so the hero hides its
    /// pills. The date/time pills stay — they are content.
    var showsTopControls: Bool = true

    // (The remote layer's crossfade state moved into
    // HeroRemoteImageView with RW-106 — the fade is keyed to the
    // photo's identity there, not to this view's URL string.)

    // State for the flag chip: the reason sheet's presentation, and
    // the URL of the photo already flagged in this view (the chip
    // goes inert for that photo; a different photo re-arms it).
    @State private var showFlagSheet = false
    @State private var flaggedImageUrl: String?

    // RW-6.6: Reduce Motion gates the crossfade and the "Now" pill's
    // scale transition — content swaps instantly instead.
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // RW-25: the hero is photo-only — the feels-like numeral, its
    // eyebrow and the date/time pills moved to the sheet
    // (WeatherOutfitView's sheet header). The data params above are
    // kept: other call sites pass them and the flag/tint logic reads
    // the bracket; this view simply renders no text on the photo.

    var body: some View {
        Group {
            if heightMode == .fill {
                // Pane layouts frame the hero themselves; the
                // GeometryReader fills what the parent proposes.
                heroContent
            } else {
                // Full-screen layouts: the hero is a fraction of
                // its container (the scroll view), never of
                // UIScreen.main.
                heroContent
                    .containerRelativeFrame(.vertical) { length, _ in
                        length * AppTheme.heroHeightRatio
                    }
            }
        }
        // Flag chip over the photo, bottom-trailing (see flagChip).
        // Attached as an overlay on the hero frame itself so its
        // hit area is only the chip — a full-size clear layer here
        // would swallow taps meant for the hero's own controls.
        // RW-25 inset: 40pt = the sheet's 28pt overlap onto the
        // hero's foot + a 12pt gap, so the chip clears the sheet's
        // rounded top edge in the stacked layouts. WS-1: the value
        // is HomeHandoffGeometry.flagChipBottomInset — derived
        // from the overlap constant, so the accessory zone is
        // capped by construction and cannot drift from the sheet.
        // GEN-3 (WS-A(2)): the hero pill, bottom-leading — the one
        // pill on the photo, in the same accessory zone the flag
        // chip caps (HomeHandoffGeometry's derived inset), so it
        // clears the sheet's rounded top edge identically.
        .overlay(alignment: .bottomLeading) {
            if pillState != .hidden {
                heroPill
                    .padding(.leading, 14)
                    .padding(.bottom, HomeHandoffGeometry.flagChipBottomInset)
                    .padding(.trailing, 60) // clear the flag chip's disc
            } else if lookFailed {
                // RW-111: the failure line at the hero (see the
                // input's note) — same accessory zone, same
                // clearance from the sheet's top edge and the
                // flag chip as the pill it stands in for.
                heroFailureLine
                    .padding(.leading, 14)
                    .padding(.bottom, HomeHandoffGeometry.flagChipBottomInset)
                    .padding(.trailing, 60) // clear the flag chip's disc
            }
        }
        .overlay(alignment: .bottomTrailing) {
            if let personalFlagTarget {
                personalFlagChip(for: personalFlagTarget)
                    .padding(.trailing, 14)
                    .padding(.bottom, HomeHandoffGeometry.flagChipBottomInset)
            } else if let flagTarget {
                flagChip(for: flagTarget)
                    .padding(.trailing, 14)
                    .padding(.bottom, HomeHandoffGeometry.flagChipBottomInset)
            }
        }
        // WS-1 (§1.1) + RW-106: the crossfade/reset bookkeeping
        // lives in the remote layer itself (HeroRemoteImageView),
        // keyed to the photo's IDENTITY. There is deliberately no
        // URL-change reset here anymore: a re-resolution mints a
        // fresh signed URL for the SAME photo, and resetting on
        // that change is what hid the photo and restarted its
        // download on every settle (the RW-106 defect). A truly
        // different photo is a different identity, and the layer
        // still starts it hidden and fades it in on its own load
        // — the WS-1 property, preserved per identity.
    }

    private var heroContent: some View {
        GeometryReader { geometry in
            // In containerFraction mode the containerRelativeFrame in
            // `body` has ALREADY applied heroHeightRatio to this
            // view's height — geometry.size.height IS the hero
            // height. Multiplying by the ratio again (as this did)
            // shrank the hero to 0.75 × 0.75 of the container — a
            // near-square frame the full-frame photo then had to be
            // cropped into (field finding 2026-10-31: "the image on
            // iOS is a square"). Android applies its 0.75 once
            // (HeroSection: screenHeightDp * 0.75).
            let heroHeight = geometry.size.height

            ZStack {
                // Background image with crossfade
                heroImageLayer(width: geometry.size.width, height: heroHeight)
                    .frame(width: geometry.size.width, height: heroHeight)

                // Temperature tint overlay (12% opacity)
                AppTheme.temperatureTintOverlay(for: tempBracket, opacity: 0.12)
                    .frame(width: geometry.size.width, height: heroHeight)

                // RW-25: bounded TOP gradient only, for chrome
                // legibility at the hero's head (the nav bar is
                // opaque in this app, so this stays minimal). The
                // 6-stop full-frame overlay is gone — no text lives
                // on the photo's foot anymore.
                AppTheme.heroTopGradient()
                    .frame(width: geometry.size.width, height: heroHeight)

                // Chrome overlay: top controls only, and only when
                // a container has not taken the chrome over (home
                // passes showsTopControls: false).
                VStack(spacing: 0) {
                    if showsTopControls {
                        topControls(width: geometry.size.width)
                            .padding(.top, geometry.safeAreaInsets.top + 8)
                    }
                    Spacer()
                }
                .frame(width: geometry.size.width, height: heroHeight)
            }
            .frame(width: geometry.size.width, height: heroHeight)
            .clipped()
        }
        .ignoresSafeArea(edges: .top)
    }

    // MARK: - Hero Image Layer

    // MARK: - Hero Pill (GEN-3 WS-A(2); RW-144 seat)

    /// RW-144: the pill rendering lives in HeroPillView (below)
    /// so Home can seat the SAME pill — same settled table, same
    /// copy — straddling the hero/sheet boundary. HeroImageView's
    /// own seat (bottom of the hero) remains the default for its
    /// other callers.
    @ViewBuilder
    private var heroPill: some View {
        HeroPillView(
            pillState: pillState,
            onPillGetUnlimited: onPillGetUnlimited
        )
    }

    /// RW-111: the failed look's line at the hero — the settled
    /// copy verbatim (MakeItMeCopy.lookFailed), in the pill's
    /// glass chrome, the whole surface the retry (the copy names
    /// the act: "tap to try again"). It wraps as many lines as it
    /// needs inside the pill's granted width; nothing truncates.
    private var heroFailureLine: some View {
        HeroPillView(
            pillState: .hidden,
            lookFailed: true,
            onRetryFailedLook: onRetryFailedLook
        )
    }

    @ViewBuilder
    private func heroImageLayer(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            // Always show fallback first (instant load). WS-1
            // (§1.1): a fallback URL change (a new day / time /
            // location settling on the fallback) crossfades like
            // every other hero change — the AsyncImage transaction
            // carries the §1.1 crossfade instead of a hard swap.
            // GEN-3 (WS-A(1)): an EMPTY fallback URL is a resolution,
            // not a loading state — the point-grain gate found no
            // truthful bundled photo for this prescription, so the
            // layer paints the bracket's temperature tint (the
            // designed gradient terminus) until the pool serve
            // lands over it.
            if fallbackImageUrl.isEmpty {
                AppTheme.temperatureTintOverlay(for: tempBracket, opacity: 0.45)
            } else {
                AsyncImage(
                    url: URL(string: fallbackImageUrl),
                    transaction: Transaction(animation: .easeInOut(duration: reduceMotion ? 0 : AppTheme.crossfadeDuration))
                ) { phase in
                    switch phase {
                    case .success(let image):
                        HeroPhoto(image: image, width: width, height: height)
                    case .failure:
                        Color.black
                    case .empty:
                        Color.black
                    @unknown default:
                        Color.black
                    }
                }
            }

            // Remote image on top (RW-106): loaded through the
            // identity-keyed, retrying loader below. The
            // AsyncImage this replaces hid on every URL change,
            // restarted the ~1 MB download from zero on every
            // re-signed URL (defeating the URL cache), and
            // rendered EmptyView on failure with no retry — so on
            // a weak connection the personal photo never painted,
            // the bundled fallback below stayed on screen, and
            // the pill (rendered from view-model state) counted a
            // look the user never saw. Fallback-first loading is
            // unchanged (the RW-4 contract): the fallback paints
            // instantly underneath, the remote fades in over it
            // when ITS load lands.
            if let aiUrl = aiImageUrl {
                HeroRemoteImageView(urlString: aiUrl, width: width, height: height)
            }
        }
    }

    // MARK: - Flag Chip

    /// The flag affordance: a 32pt translucent disc with a flag
    /// glyph — deliberately quieter than the 44pt glass share and
    /// settings buttons. Tapping it presents the reason sheet
    /// ("Flag this photo"); picking a reason fires the flag and
    /// leaves the chip dimmed and inert for the rest of this
    /// photo's view (one flag per photo view, as on Android).
    private func flagChip(for target: HeroFlagTarget) -> some View {
        let alreadyFlagged = flaggedImageUrl == target.imageUrl
        return Button {
            showFlagSheet = true
        } label: {
            flagChipDisc(alreadyFlagged: alreadyFlagged)
        }
        .buttonStyle(.plain)
        .disabled(alreadyFlagged)
        .accessibilityLabel("Flag this photo")
        .confirmationDialog(
            "Flag this photo",
            isPresented: $showFlagSheet,
            titleVisibility: .visible
        ) {
            // GEN-5 (RW-29 FA-18): the settled sheet order —
            // outfit reason, quality reason, residual.
            ForEach(HeroFlagReason.sheetOrder, id: \.self) { reason in
                Button(reason.label) {
                    flaggedImageUrl = target.imageUrl
                    onFlagHero(reason)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// The personal-photo variant of the flag chip: same disc,
    /// same placement, same one-flag-per-photo-view inert rule —
    /// but the menu is the personal one (spec §4: the existing
    /// control gains "This doesn't look like me"), and every pick
    /// routes to the backend flag_hero action, which retires THIS
    /// hero and queues its regeneration. The pool reasons are here
    /// in their personal form for the same reason: flagging a
    /// personal photo through the pool insert would record it as a
    /// fallback photo and retire nothing.
    private func personalFlagChip(for target: PersonalHeroFlagTarget) -> some View {
        let alreadyFlagged = flaggedImageUrl == target.imageUrl
        return Button {
            showFlagSheet = true
        } label: {
            flagChipDisc(alreadyFlagged: alreadyFlagged)
        }
        .buttonStyle(.plain)
        .disabled(alreadyFlagged)
        .accessibilityLabel("Flag this photo")
        .confirmationDialog(
            "Flag this photo",
            isPresented: $showFlagSheet,
            titleVisibility: .visible
        ) {
            // GEN-5 (RW-29 FA-18): frame 45's personal sheet —
            // the lead reason, the outfit reason, the residual.
            ForEach(PersonalHeroFlagReason.sheetOrder, id: \.self) { reason in
                Button(reason.label) {
                    flaggedImageUrl = target.imageUrl
                    onFlagPersonalHero(reason)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            // The consequence, stated before the user commits
            // (frame 45 carries it under the lead reason; the
            // dialog's message seat is its iOS expression).
            Text(PersonalHeroFlagReason.consequenceLine)
        }
    }

    /// The chip's disc, shared by both flag variants.
    private func flagChipDisc(alreadyFlagged: Bool) -> some View {
        Image(systemName: "flag")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(.white.opacity(alreadyFlagged ? 0.45 : 1.0))
            .frame(width: 32, height: 32)
            .background(
                Circle().fill(Color.black.opacity(alreadyFlagged ? 0.22 : 0.35))
            )
            .overlay(
                Circle().stroke(Color.white.opacity(0.18), lineWidth: 1)
            )
    }

    // MARK: - Top Controls

    private func topControls(width: CGFloat) -> some View {
        HStack(spacing: 8) {
            // Location button - constrained to prevent overflow with long names
            Button(action: onLocationTapped) {
                HStack(spacing: 6) {
                    Image(systemName: "location.fill")
                        .font(.appPillLabel)
                    Text(locationName)
                        .font(.appPillLabel)
                        .lineLimit(1)
                }
                .foregroundColor(.white)
                .glassPill()
            }
            .frame(maxWidth: max(120, width - 140), alignment: .leading) // Leave room for buttons

            Spacer(minLength: 8)

            HStack(spacing: 12) {
                // Share button
                Button(action: onShareTapped) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.body.weight(.medium))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .glassMorphism(cornerRadius: 22)
                }

                // Settings button
                Button(action: onSettingsTapped) {
                    Image(systemName: "gearshape.fill")
                        .font(.body.weight(.medium))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .glassMorphism(cornerRadius: 22)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // Check if viewing current date and hour
    private var isViewingNow: Bool {
        let calendar = Calendar.current
        let now = Date()
        return calendar.isDateInToday(date) && hour == calendar.component(.hour, from: now)
    }

    // MARK: - Formatters

    private var formattedDate: String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInTomorrow(date) {
            return "Tomorrow"
        } else {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, MMM d"
            return formatter.string(from: date)
        }
    }

    private var formattedTime: String {
        let suffix = hour >= 12 ? "PM" : "AM"
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return "\(displayHour):00 \(suffix)"
    }
}

// MARK: - Preview

#Preview {
    HeroImageView(
        locationName: "San Francisco, CA",
        temperature: 58,
        actualTemperature: 61,
        weatherCondition: .clear,
        date: Date(),
        hour: 14,
        tempBracket: .MILD,
        fallbackImageUrl: FallbackImageProvider.shared.getImageURL(temp: .MILD, weather: .CLEAR, gender: .FEMALE),
        aiImageUrl: nil
    )
    .background(Color.black)
}

// MARK: - Hero Pill View (GEN-3 WS-A(2); RW-144 seats)

/// THE Make It Me pill — one component, state-driven by
/// HeroPill from the requested cell's facts, rendering ONLY the
/// settled pill table: generating (script + spinner + "Making
/// this you…"), free generating (+ the one green upsell line),
/// free serve (script + "— N of 10 looks" count form + "Get
/// unlimited"), exhausted, Premium (script + "Premium" + lock).
/// A scaffold serve resolves .hidden and renders nothing.
///
/// Two seats, one pill:
/// - `.hero`: the GEN-3 seat at the bottom of the hero image —
///   native glass chrome, cream content over the photograph.
/// - `.straddle` (RW-144): the v8 seat straddling the hero/sheet
///   boundary — the theme pill fill (#FDFBF6 light / #2A251E
///   dark) with a warm-black soft shadow, content in the theme
///   ink (warm ink on cream; cream on Warm Charcoal), the CTA
///   in the pine payload. Only the seat and its surface change;
///   every state, string, and behaviour is the settled table's.
struct HeroPillView: View {
    enum Seat {
        case hero
        case straddle
    }

    let pillState: HeroPillState
    var seat: Seat = .hero
    var lookFailed: Bool = false
    var onPillGetUnlimited: () -> Void = {}
    var onRetryFailedLook: () -> Void = {}

    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }

    /// Primary content ink for the seat: cream over the photo
    /// (and on the dark straddle fill); warm ink on the cream
    /// straddle fill. Never pure white (RW-144 palette rule).
    private var ink: Color {
        switch seat {
        case .hero: return HeroChrome.cream
        case .straddle: return isDark ? HeroChrome.cream : AppTheme.inkPrimary
        }
    }

    private var secondaryInk: Color {
        switch seat {
        case .hero: return HeroChrome.cream.opacity(0.7)
        case .straddle: return isDark ? HeroChrome.cream.opacity(0.7) : AppTheme.inkSecondary
        }
    }

    private var pillScriptMark: some View {
        Text(HeroPill.scriptMarkText)
            .font(.greatVibes(size: 15))
            .foregroundColor(ink)
            .lineLimit(1)
    }

    /// The seat's surface: glass on the hero; the theme pill
    /// fill + the mock's warm-black shadow (20,14,8)@α64 at the
    /// straddle.
    @ViewBuilder
    private func seated<Content: View>(_ content: Content) -> some View {
        switch seat {
        case .hero:
            content.glassPill()
        case .straddle:
            content
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Capsule().fill(AppTheme.firstScreenPillFill))
                .shadow(color: Color(hex: "140E08").opacity(64.0 / 255.0), radius: 12, y: 5)
        }
    }

    var body: some View {
        if lookFailed {
            // RW-111: the failed look's line — the settled copy
            // verbatim, the whole surface the retry.
            seated(
                Button(action: onRetryFailedLook) {
                    Text(MakeItMeCopy.lookFailed)
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(ink)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(.plain)
            )
            .accessibilityLabel(MakeItMeCopy.lookFailed)
        } else {
            pillBody
        }
    }

    @ViewBuilder
    private var pillBody: some View {
        switch pillState {
        case .hidden:
            EmptyView()
        case .making:
            // GEN-5 (pill restatement): EVERY status leads with
            // the Make It Me script — the generating state was the
            // one state that dropped it (census A-13/B-14).
            seated(
                HStack(spacing: 6) {
                    pillScriptMark
                    ProgressView()
                        .controlSize(.mini)
                        .tint(ink)
                    Text(HeroPill.makingLabel)
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(ink)
                        .lineLimit(1)
                }
            )
            .accessibilityLabel("\(HeroPill.scriptMarkText). \(HeroPill.makingLabel)")
        case .makingWithUpsell:
            // GEN-5 (C-4 AMENDED) + GEN-6 VQ FIX (RW-71): the
            // FREE generating pill — the clean generating row
            // plus ONE small green upsell line on its own line
            // beneath (the one-row form ellipsised both texts at
            // the hero composition's width). Every settled
            // string renders whole.
            seated(
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        pillScriptMark
                        ProgressView()
                            .controlSize(.mini)
                            .tint(ink)
                        Text(HeroPill.makingLabel)
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(ink)
                            .lineLimit(1)
                    }
                    Button(action: onPillGetUnlimited) {
                        Text(HeroPill.getInstantlyLabel)
                            .font(.appFootnote.weight(.bold))
                            // RW-105: the dynamic pine payload.
                            .foregroundColor(AppTheme.pinePayload)
                            .lineLimit(1)
                    }
                    .buttonStyle(.plain)
                }
            )
            .accessibilityLabel("\(HeroPill.scriptMarkText). \(HeroPill.makingLabel). \(HeroPill.getInstantlyLabel)")
        case .exhausted(let used, let allowance):
            // GEN-5 (OUT OF FREE LOOKS) + GEN-6 VQ FIX (RW-72):
            // script + the whole honest count on the first line,
            // "Get unlimited" — the pill's main message in the
            // green accent — on its own line beneath; the whole
            // pill taps through to the Premium offer.
            Button(action: onPillGetUnlimited) {
                seated(
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            pillScriptMark
                            Text(HeroPill.exhaustedCountLabel(used: used, allowance: allowance))
                                .font(.appFootnote.weight(.semibold))
                                .foregroundColor(ink)
                                .lineLimit(1)
                        }
                        Text(HeroPill.getUnlimitedLabel)
                            .font(.appFootnote.weight(.bold))
                            .foregroundColor(AppTheme.pinePayload)
                            .lineLimit(1)
                    }
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(HeroPill.scriptMarkText). \(HeroPill.exhaustedCountLabel(used: used, allowance: allowance)). \(HeroPill.getUnlimitedLabel)")
        case .freeLook(let used, let allowance):
            Button(action: onPillGetUnlimited) {
                seated(
                    HStack(spacing: 6) {
                        pillScriptMark
                        Text(HeroPill.freeLookCountLabel(used: used, allowance: allowance))
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(ink)
                            .lineLimit(1)
                        Text("·")
                            .font(.appFootnote)
                            .foregroundColor(secondaryInk)
                        // Approved pill table: "Get unlimited" is
                        // the GREEN element (pine payload, RW-105).
                        Text(HeroPill.getUnlimitedLabel)
                            .font(.appFootnote.weight(.bold))
                            .foregroundColor(AppTheme.pinePayload)
                            .lineLimit(1)
                    }
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(HeroPill.scriptMarkText). \(HeroPill.freeLookCountLabel(used: used, allowance: allowance)). \(HeroPill.getUnlimitedLabel)")
        case .premium:
            seated(
                HStack(spacing: 6) {
                    pillScriptMark
                    Text(HeroPill.premiumLabel)
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(ink)
                        .lineLimit(1)
                    // GEN-3 DESIGN DELTA 2: the lock glyph.
                    Image(systemName: "lock.fill")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(ink)
                }
            )
            .accessibilityLabel("\(HeroPill.scriptMarkText). \(HeroPill.premiumLabel)")
        }
    }
}

// MARK: - RW-106 · Remote hero image loading
//
// Diagnosis of record: release-prep/rw106-evidence/diagnosis.md.
// Generation, counting, delivery, and the server's serve answer
// were all correct on Sol's Nov 6 session — five looks counted,
// five heroes of him delivered — but the photo on screen in every
// count-pill frame was the bundled fallback, because this layer
// (a) hid the remote image on every URL change while every
// re-resolution mints a FRESH signed URL for the same image, so
// the ~1 MB PNG restarted from zero and the changed URL defeated
// the URL cache, and (b) rendered EmptyView on load failure with
// no retry, ever, while the pill — rendered from view-model
// state — asserted a personal serve. On WiFi-with-no-cellular
// ("SOS" in every frame) the megabyte loads never landed.
//
// The loader below is the fix, in the render layer only:
//  - Loads are keyed to the photo's IDENTITY — the URL with its
//    signature (query) stripped — never to the signed URL string.
//  - Memory + disk caches are keyed by identity, so
//    re-resolutions and relaunches reuse downloaded bytes.
//  - A re-resolution carrying the same identity never blanks a
//    painted photo and never restarts a load; the freshest
//    signed URL is remembered so a retry spends the new
//    signature, not the stale one.
//  - A failed or interrupted load retries with bounded backoff
//    (1s, 2s, 4s, 8s — five attempts), and a later drive (a
//    foreground revalidation, a cell revisit) re-arms it.
//  - A DIFFERENT identity still starts unpainted (the fallback
//    shows) and fades in on its own load — the WS-1 crossfade
//    property, preserved per identity.

enum HeroRemoteLoadError: Error {
    case undecodable
}

@MainActor
final class HeroRemoteImageLoader: ObservableObject {

    typealias Fetcher = @Sendable (URL) async throws -> Data

    /// The photo currently painted, and the identity it belongs
    /// to. Both are published together; a view paints the image
    /// only when `paintedIdentity` equals its current identity.
    @Published private(set) var image: UIImage?
    @Published private(set) var paintedIdentity: String?

    nonisolated static let defaultRetryDelays: [TimeInterval] = [1, 2, 4, 8]

    private let fetch: Fetcher
    private let retryDelays: [TimeInterval]
    private var currentIdentity: String?
    private var freshestURL: URL?
    private var loadTask: Task<Void, Never>?
    private var generation = 0

    init(fetch: @escaping Fetcher = HeroRemoteImageLoader.urlSessionFetch,
         retryDelays: [TimeInterval] = HeroRemoteImageLoader.defaultRetryDelays) {
        self.fetch = fetch
        self.retryDelays = retryDelays
    }

    nonisolated static func urlSessionFetch(_ url: URL) async throws -> Data {
        let (data, _) = try await URLSession.shared.data(from: url)
        return data
    }

    /// The stable identity of a remote hero object: its URL with
    /// the signature (query) and fragment stripped. Two signed
    /// URLs for the same storage object share an identity; two
    /// different objects never do.
    nonisolated static func identity(for urlString: String) -> String {
        guard var components = URLComponents(string: urlString) else { return urlString }
        components.query = nil
        components.fragment = nil
        return components.url?.absoluteString ?? urlString
    }

    /// Drive the loader at a (possibly re-signed) URL. Safe to
    /// call on every appearance and every URL change.
    func load(urlString: String?) {
        guard let urlString, let url = URL(string: urlString) else {
            generation += 1
            loadTask?.cancel()
            loadTask = nil
            currentIdentity = nil
            freshestURL = nil
            image = nil
            paintedIdentity = nil
            return
        }
        let identity = Self.identity(for: urlString)
        // Always track the freshest signed URL: a retry after a
        // re-resolution spends the new signature, not a stale one.
        freshestURL = url
        if identity == currentIdentity {
            // The same underlying photo. Painted: nothing to do —
            // a re-resolution must never blank or restart it. In
            // flight: the running sequence picks the fresh URL up
            // on its next attempt. Exhausted: re-arm a fresh
            // bounded sequence.
            if image != nil || loadTask != nil { return }
            startLoad(identity: identity)
            return
        }
        // A different photo: drop the old paint (the fallback
        // underneath shows, as designed) and load the new one.
        currentIdentity = identity
        generation += 1
        loadTask?.cancel()
        loadTask = nil
        image = nil
        paintedIdentity = nil
        if let cached = Self.memoryImage(for: identity) {
            image = cached
            paintedIdentity = identity
            return
        }
        startLoad(identity: identity)
    }

    private func startLoad(identity: String) {
        let gen = generation
        let fetch = self.fetch
        let delays = retryDelays
        loadTask = Task { [weak self] in
            // Disk first: bytes downloaded in an earlier session
            // paint without touching the network.
            if let disk = await Task.detached(priority: .userInitiated, operation: {
                Self.diskImage(for: identity)
            }).value {
                await self?.publish(disk, identity: identity, generation: gen)
                return
            }
            var attempt = 0
            while true {
                guard let self else { return }
                guard await self.isCurrent(identity: identity, generation: gen),
                      let url = await self.currentFreshestURL() else { return }
                do {
                    let data = try await fetch(url)
                    guard let decoded = UIImage(data: data) else {
                        throw HeroRemoteLoadError.undecodable
                    }
                    Self.storeToDisk(decoded, identity: identity)
                    await self.publish(decoded, identity: identity, generation: gen)
                    return
                } catch {
                    if attempt >= delays.count {
                        await self.finishExhausted(generation: gen)
                        return
                    }
                    let delay = delays[attempt]
                    attempt += 1
                    try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                }
            }
        }
    }

    private func isCurrent(identity: String, generation gen: Int) -> Bool {
        !Task.isCancelled && generation == gen && currentIdentity == identity
    }

    private func currentFreshestURL() -> URL? { freshestURL }

    private func publish(_ decoded: UIImage, identity: String, generation gen: Int) {
        guard isCurrent(identity: identity, generation: gen) else { return }
        Self.storeInMemory(decoded, for: identity)
        image = decoded
        paintedIdentity = identity
        loadTask = nil
    }

    private func finishExhausted(generation gen: Int) {
        if generation == gen { loadTask = nil }
    }

    // MARK: Caches (identity-keyed; thread-safe, nonisolated)

    nonisolated private static let memoryCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 24
        cache.totalCostLimit = 48 * 1024 * 1024
        return cache
    }()

    nonisolated private static func memoryImage(for identity: String) -> UIImage? {
        memoryCache.object(forKey: identity as NSString)
    }

    nonisolated private static func storeInMemory(_ image: UIImage, for identity: String) {
        let cost = Int(image.size.width * image.size.height * 4)
        memoryCache.setObject(image, forKey: identity as NSString, cost: cost)
    }

    nonisolated private static var diskCacheDirectory: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("HeroRemoteImages", isDirectory: true)
    }

    nonisolated private static func diskFileURL(for identity: String) -> URL? {
        let digest = SHA256.hash(data: Data(identity.utf8))
        let name = digest.map { String(format: "%02x", $0) }.joined() + ".png"
        return diskCacheDirectory?.appendingPathComponent(name)
    }

    nonisolated private static func diskImage(for identity: String) -> UIImage? {
        guard let url = diskFileURL(for: identity),
              let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    nonisolated private static func storeToDisk(_ image: UIImage, identity: String) {
        guard let dir = diskCacheDirectory, let file = diskFileURL(for: identity),
              let data = image.pngData() else { return }
        Task.detached(priority: .utility) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
            pruneDiskCache(in: dir)
        }
    }

    nonisolated private static func modificationDate(of url: URL) -> Date {
        let values = try? url.resourceValues(forKeys: [.contentModificationDateKey])
        return values?.contentModificationDate ?? .distantPast
    }

    nonisolated private static func pruneDiskCache(in dir: URL) {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        let newestFirst = files.sorted { modificationDate(of: $0) > modificationDate(of: $1) }
        for stale in newestFirst.dropFirst(40) {
            try? FileManager.default.removeItem(at: stale)
        }
    }
}

/// The remote hero layer: paints the loader's image for the
/// CURRENT identity over whatever sits beneath (the bundled
/// fallback), fading in when the load lands. Renders nothing
/// while the current identity has no painted image — the
/// fallback shows, exactly as designed — but unlike the
/// AsyncImage it replaces, a failure is a retrying state, not a
/// terminal one.
struct HeroRemoteImageView: View {
    let urlString: String?
    let width: CGFloat
    let height: CGFloat

    @StateObject private var loader: HeroRemoteImageLoader
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(urlString: String?, width: CGFloat, height: CGFloat,
         fetch: HeroRemoteImageLoader.Fetcher? = nil,
         retryDelays: [TimeInterval]? = nil) {
        self.urlString = urlString
        self.width = width
        self.height = height
        _loader = StateObject(wrappedValue: HeroRemoteImageLoader(
            fetch: fetch ?? HeroRemoteImageLoader.urlSessionFetch,
            retryDelays: retryDelays ?? HeroRemoteImageLoader.defaultRetryDelays))
    }

    private var currentIdentity: String? {
        urlString.map { HeroRemoteImageLoader.identity(for: $0) }
    }

    var body: some View {
        ZStack {
            if let image = loader.image, loader.paintedIdentity == currentIdentity {
                HeroPhoto(image: Image(uiImage: image), width: width, height: height)
                    .opacity(shown ? 1 : 0)
            }
        }
        .onAppear {
            loader.load(urlString: urlString)
            if loader.image != nil, loader.paintedIdentity == currentIdentity {
                reveal()
            }
        }
        .onChange(of: urlString) { oldValue, newValue in
            // A different photo starts hidden (WS-1); a re-signed
            // URL for the SAME photo changes nothing on screen.
            if HeroRemoteImageLoader.identity(for: oldValue ?? "")
                != HeroRemoteImageLoader.identity(for: newValue ?? "") {
                shown = false
            }
            loader.load(urlString: newValue)
        }
        .onChange(of: loader.paintedIdentity) { _, _ in
            if loader.image != nil, loader.paintedIdentity == currentIdentity {
                reveal()
            }
        }
    }

    private func reveal() {
        // Fallback-first loading is unchanged (the RW-4
        // contract); only the fade is gated on Reduce Motion.
        if reduceMotion {
            shown = true
        } else if !shown {
            withAnimation(.easeInOut(duration: AppTheme.crossfadeDuration)) {
                shown = true
            }
        }
    }
}

// MARK: - Full-screen hero viewer (RW-133, fix wave 2026-11-07)

/// The full-screen photograph viewer (Sol's RW-133 ruling):
/// tapping the hero — or a Your Looks tile, or a Wear I've Run
/// entry's photograph — opens the STORED photograph fitted
/// whole on black, so the whole outfit is visible. The stored
/// frame is fitted AS STORED (`.scaledToFit`): if a stored hero
/// is itself a cropped frame, the viewer shows that frame whole
/// and never crops further. At most ONE quiet caption line
/// names the outfit's pieces. Dismiss by tapping again or
/// swiping down; pinch zooms (and a double-tap toggles zoom).
struct HeroPhotoViewer: View {
    let imageURL: String?
    /// The one quiet caption line (outfit piece names); nil or
    /// empty renders no caption at all.
    var caption: String? = nil

    @Environment(\.dismiss) private var dismiss
    @State private var zoomScale: CGFloat = 1
    @State private var lastZoomScale: CGFloat = 1

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let imageURL, let url = URL(string: imageURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                            .scaleEffect(zoomScale)
                            .gesture(
                                MagnificationGesture()
                                    .onChanged { value in
                                        zoomScale = min(4, max(1, lastZoomScale * value))
                                    }
                                    .onEnded { _ in
                                        lastZoomScale = zoomScale
                                    }
                            )
                    case .failure:
                        Image(systemName: "photo")
                            .font(.largeTitle)
                            .foregroundColor(.white.opacity(0.5))
                    default:
                        ProgressView().tint(.white)
                    }
                }
            }
            if let caption, !caption.isEmpty {
                VStack {
                    Spacer()
                    Text(caption)
                        .font(.appFootnote)
                        .foregroundColor(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 28)
                }
                .allowsHitTesting(false)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            withAnimation {
                zoomScale = zoomScale > 1 ? 1 : 2
                lastZoomScale = zoomScale
            }
        }
        .onTapGesture(count: 1) {
            dismiss()
        }
        .gesture(
            DragGesture(minimumDistance: 24)
                .onEnded { value in
                    if value.translation.height > 80 { dismiss() }
                }
        )
    }
}
