import SwiftUI
import LinkPresentation
import RunWearEngine
import Vision

// MARK: - "Wear I've Run" Collection (RW-26, checklist §C; notes §8-K)
//
// Chapters BY LOCATION (within a location, the entries are
// sessions — GEN-3). Cover = the newest entry WITH a loadable
// hero reference (RW-153) + day count + date span + feels-like
// range, all computed. Multi-stay chapters
// count stays and name the last stay — never a false-contiguity
// span. Hometown-scale chapters group by year → month rows. The
// weather filter lives at the collection level AND inside
// chapters, and recomputes every summary it touches. The current
// session's entry is pinned above the chapters — and lifted out
// of them, so it never duplicates a chapter. Sharing happens
// inside, card by card (Poster or Kit Card — no Range, no
// collection-level sharing).

struct WearIveRunCollectionView: View {
    @ObservedObject var model: WearIveRunCollectionModel
    /// Temperatures render in the user's unit.
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    @State private var filter: WearIveRunWeatherFilter = .all
    /// RW-152 §4 (J1): the past entry awaiting its delete
    /// confirmation — the confirmation names its place and date
    /// (WearIveRunDeletePolicy). Today's entry never lands
    /// here: it deletes in one tap.
    @State private var pendingDelete: WearIveRunEntry?

    /// RW-152 §4 (J1): route a delete request — a PAST entry
    /// asks first (confirmation), today's entry deletes at once.
    private func requestDelete(_ entry: WearIveRunEntry) {
        if WearIveRunDeletePolicy.requiresConfirmation(for: entry) {
            pendingDelete = entry
        } else {
            model.removeEntry(id: entry.id)
        }
    }

    /// The pinned entry (GEN-3 session rule): the CURRENT
    /// session's entry — the same entry the home dock presents.
    /// It is pinned above the chapters and LIFTED OUT of the
    /// chapter arithmetic below, so it can never appear twice
    /// (census X-5: the pinned TODAY card duplicated the chapter
    /// directly beneath it, and stayed on the day's first place
    /// while the session moved on — under the session rule the
    /// pin IS the session's last settled version, by identity).
    /// A pinned entry the weather filter excludes is not pinned;
    /// it then stays in its chapter like any other entry.
    private var pinned: WearIveRunEntry? {
        model.sessionEntry().flatMap {
            WearIveRunWeatherClassifier.matches($0, filter: filter) ? $0 : nil
        }
    }

    private var chapters: [WearIveRunChapter] {
        let unpinned = pinned.map { pin in
            model.entries.filter { $0.id != pin.id }
        } ?? model.entries
        return WearIveRunCollection.chapters(from: unpinned, filter: filter)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // The collection's signature — script, ink on light
                // / warm-white on dark via the ink token.
                Text("Wear I've Run")
                    .font(.greatVibes(size: 34))
                    .foregroundColor(AppTheme.inkPrimary)

                filterRow

                if model.entries.isEmpty {
                    emptyState
                } else if chapters.isEmpty && pinned == nil {
                    Text("No days match this filter yet.")
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                } else {
                    if let today = pinned {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                // Fix wave 2026-11-07 (RW-132):
                                // the pinned card's header is the
                                // ENTRY'S OWN day — under the
                                // future-days ruling the session
                                // entry can be dated tomorrow or
                                // later, and a literal "Today"
                                // would announce a day the record
                                // does not hold.
                                Text(Calendar.current.isDateInToday(today.dayStart)
                                     ? "Today"
                                     : WearIveRunDateFormatting.dayLabel(today.dayStart))
                                    .font(.appEyebrow)
                                    .foregroundColor(AppTheme.inkMuted)
                                Spacer()
                                // RW-122 (iOS half): the pinned
                                // entry deletes from here too.
                                // RW-152 §4 (J1): routed through
                                // the delete policy — the pinned
                                // entry is the session's (today's,
                                // or a future plan), so it stays
                                // one-tap; the confirmation path
                                // stands if that ever changes.
                                Button {
                                    requestDelete(today)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.appFootnote.weight(.semibold))
                                        .foregroundColor(AppTheme.inkSecondary)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Delete this entry")
                            }
                            NavigationLink {
                                WearIveRunDayView(
                                    entry: today,
                                    formatTemperature: formatTemperature,
                                    genderPreference: genderPreference,
                                    onDelete: { model.removeEntry(id: today.id) },
                                    onHeroRecovered: { model.healHeroReference(entryID: $0.id, durableReference: $1) }
                                )
                            } label: {
                                entryCard(today)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    ForEach(chapters, id: \.placeName) { chapter in
                        // RW-152 §4: the chapter cover carries
                        // its own trash, bound to the cover's
                        // REPRESENTATIVE entry only (ruling J2 —
                        // never the whole place group; whole-place
                        // removal stays in the chapter view). It
                        // sits OUTSIDE the navigation link so the
                        // tap can never be read as navigation.
                        ZStack(alignment: .topTrailing) {
                            NavigationLink {
                                WearIveRunChapterView(
                                    model: model,
                                    placeName: chapter.placeName,
                                    formatTemperature: formatTemperature,
                                    genderPreference: genderPreference
                                )
                            } label: {
                                chapterCover(chapter)
                            }
                            .buttonStyle(.plain)
                            Button {
                                requestDelete(chapter.cover)
                            } label: {
                                Image(systemName: "trash")
                                    .font(.appFootnote.weight(.semibold))
                                    .foregroundColor(AppTheme.inkPrimary)
                                    .frame(width: 32, height: 32)
                                    .background(Circle().fill(AppTheme.sheet.opacity(0.92)))
                            }
                            .buttonStyle(.plain)
                            .padding(.top, 23)
                            .padding(.trailing, 23)
                            .accessibilityLabel("Delete the cover entry for \(chapter.placeName)")
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        // GEN-5 (RW-29 FA-19): no plain-text navigation title —
        // the script wordmark above is the collection's only
        // header (design-spec §8.2; Android's header is the
        // script alone). Build 25 printed the name twice.
        .navigationBarTitleDisplayMode(.inline)
        // RW-152 §4 (J1): the past-entry delete confirmation.
        .modifier(WearIveRunDeleteConfirmation(
            pending: $pendingDelete,
            onConfirm: { model.removeEntry(id: $0.id) }))
    }

    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(WearIveRunWeatherFilter.allCases, id: \.rawValue) { option in
                    Button(option.label) { filter = option }
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(filter == option ? AppTheme.warmWhite : AppTheme.inkPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            Capsule().fill(filter == option ? AppTheme.primaryColor : AppTheme.cardOnSheet)
                        )
                }
            }
        }
    }

    /// The honest empty state (frame 10): no fake rows, just what
    /// the collection is and that it starts with today's run.
    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 8) {
            // GEN-5 (RW-29 FA-16): frame 10's honest empty
            // state, verbatim — build 25's invented head and
            // session-framed body are replaced with the settled
            // day-language text.
            Text("Your scrapbook starts with today's run.")
                .font(.appSectionTitle)
                .foregroundColor(AppTheme.inkPrimary)
            Text("Each day's outfit is saved here automatically — one card per day, in the places you've run. Nothing is backfilled. It begins the first time today's outfit is generated.")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.sheet))
    }

    private func chapterCover(_ chapter: WearIveRunChapter) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            entryImage(chapter.cover)
                .frame(height: 170)
                .clipped()
                .cornerRadius(14)
            HStack {
                Text(chapter.placeName)
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.appFootnote.weight(.semibold))
                    .foregroundColor(AppTheme.inkMuted)
            }
            Text(chapter.summaryLine(formatTemperature: formatTemperature))
                .font(.appFootnote)
                .foregroundColor(AppTheme.inkSecondary)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18).fill(AppTheme.sheet))
    }

    private func entryCard(_ entry: WearIveRunEntry) -> some View {
        HStack(spacing: 12) {
            entryImage(entry)
                .frame(width: 64, height: 64)
                .clipped()
                .cornerRadius(10)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.placeDisplayName)
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Text("\(formatTemperature(entry.feelsLikeF)) feels like · \(entry.condition) · \(entry.itemNames.count) items")
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)
                // GEN-5 (RW-28 item 10): the TODAY card's settled
                // "Last saved …" element (frame 9 form).
                Text(WearIveRunDateFormatting.lastSavedLine(savedAt: entry.lastSavedAt))
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)
            }
            Spacer()
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.sheet))
    }

    @ViewBuilder
    private func entryImage(_ entry: WearIveRunEntry) -> some View {
        // RW-152 §3: every collection surface loads through the
        // ONE resolver (ResolvedHeroImage) — the chapter banner
        // and the entry cards alike.
        // RW-159 (device verdict, Nov 10, 2026): the resolver is
        // the RECOVERING one — an entry that settled with an
        // empty reference re-links from the bank, and the
        // recovery is persisted on the entry through the model.
        ResolvedHeroImage(
            entry: entry,
            genderPreference: genderPreference,
            onRecovered: { model.healHeroReference(entryID: $0.id, durableReference: $1) })
    }
}

/// RW-152 §3: an entry's hero, loaded through the one resolver.
/// A stable reference (pool/curated URL, asset reference) passes
/// through synchronously; a storage path (personal hero) is
/// re-signed through HeroRefRuntime — the client's existing
/// signing path — so the photo that settled shows again on a
/// relaunch past the signed URL's one-hour life. An entry with
/// no loadable reference renders the designed treatment — a
/// card-fill placeholder with the photo glyph (GEN-5, RW-28) —
/// never a substituted photo, never a crash; a resolved photo
/// renders through HeroPhoto's TOP-anchored crop, so a
/// thumbnail can never present the runner headless the way a
/// center fill did.
struct ResolvedHeroImage: View {
    let entry: WearIveRunEntry
    /// RW-159: the recovering resolution's inputs — the user's
    /// gender segment keys the bank lookup, and a recovery is
    /// reported for persistence on the entry. Defaulted so
    /// seats without a model keep the plain resolver.
    var genderPreference: GenderPreference = .unisex
    var onRecovered: ((WearIveRunEntry, String) -> Void)?
    @State private var resolvedURLString: String?

    private var urlString: String? {
        HeroRefResolver.stableDisplayReference(for: entry) ?? resolvedURLString
    }

    var body: some View {
        GeometryReader { geo in
            AsyncImage(url: urlString.flatMap { URL(string: $0) }) { phase in
                switch phase {
                case .success(let image):
                    HeroPhoto(image: image, width: geo.size.width, height: geo.size.height)
                default:
                    ZStack {
                        Rectangle().fill(AppTheme.cardOnSheet)
                        Image(systemName: "photo")
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
            }
        }
        .task(id: entry.id) {
            if HeroRefResolver.stableDisplayReference(for: entry) == nil {
                resolvedURLString = await HeroRefRuntime.loadableURLString(
                    for: entry, gender: genderPreference,
                    onRecovered: { recovered in
                        onRecovered?(entry, recovered)
                    })
            }
        }
    }
}

/// RW-152 §4 (J1): the past-entry delete confirmation, shared
/// by every collection surface. The title and message come
/// from WearIveRunDeletePolicy — the confirmation NAMES the
/// place and the date and states plainly that entries live
/// only on this device and deletion cannot be undone.
struct WearIveRunDeleteConfirmation: ViewModifier {
    @Binding var pending: WearIveRunEntry?
    let onConfirm: (WearIveRunEntry) -> Void

    func body(content: Content) -> some View {
        content.alert(
            pending.map { WearIveRunDeletePolicy.confirmationTitle(for: $0) }
                ?? "Delete this entry?",
            isPresented: Binding(
                get: { pending != nil },
                set: { if !$0 { pending = nil } }),
            presenting: pending
        ) { entry in
            Button("Delete", role: .destructive) {
                onConfirm(entry)
                pending = nil
            }
            Button("Cancel", role: .cancel) { pending = nil }
        } message: { entry in
            Text(WearIveRunDeletePolicy.confirmationMessage(for: entry))
        }
    }
}

// MARK: - Chapter

struct WearIveRunChapterView: View {
    @ObservedObject var model: WearIveRunCollectionModel
    let placeName: String
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    @State private var filter: WearIveRunWeatherFilter = .all
    /// RW-152 §4 (J1): the past entry awaiting its delete
    /// confirmation on this surface.
    @State private var pendingDelete: WearIveRunEntry?
    /// RW-158 (user ruling, Nov 10, 2026): the PAST entry whose
    /// tap opened the Share Studio sheet directly
    /// (WearIveRunTapPolicy.chapterEntryDestination).
    @State private var shareEntry: WearIveRunEntry?

    /// RW-152 §4 (J1): route a per-entry delete — a PAST entry
    /// asks first (the confirmation names place and date);
    /// today's entry deletes in one tap.
    private func requestDelete(_ entry: WearIveRunEntry) {
        if WearIveRunDeletePolicy.requiresConfirmation(for: entry) {
            pendingDelete = entry
        } else {
            model.removeEntry(id: entry.id)
        }
    }

    /// The chapter recomputed under THIS view's filter — summaries,
    /// stays, and groups all derive from the admitted entries.
    private var chapter: WearIveRunChapter? {
        WearIveRunCollection.chapters(from: model.entries, filter: filter)
            .first { $0.placeName == placeName }
    }

    /// Hometown scale: past a month of days the chapter groups by
    /// year → month instead of one long scroll (frame 51).
    private var isHometownScale: Bool {
        (chapter?.dayCount ?? 0) > 31
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Wear I've Run")
                    .font(.greatVibes(size: 28))
                    .foregroundColor(AppTheme.inkPrimary)

                filterRow

                if let chapter {
                    Text(chapter.summaryLine(formatTemperature: formatTemperature))
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)

                    if isHometownScale {
                        monthGroups(chapter)
                    } else if chapter.stays.count > 1 {
                        // Multi-stay: each stay is a cluster with
                        // its own honest span (frame 50).
                        ForEach(Array(chapter.stays.enumerated()), id: \.offset) { _, stay in
                            staySection(stay)
                        }
                    } else {
                        dayList(chapter.entries)
                    }

                    // Fix wave 2026-11-07 (RW-122, iOS half):
                    // delete the whole by-location group — every
                    // entry filed under this place, across all
                    // filters and stays.
                    Button(role: .destructive) {
                        model.removeEntries(placeName: placeName)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "trash")
                            Text("Delete all entries for this place")
                        }
                        .font(.appSecondary.weight(.semibold))
                    }
                    .padding(.top, 4)
                } else {
                    Text("No days match this filter yet.")
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle(placeName)
        .navigationBarTitleDisplayMode(.inline)
        // RW-152 §4 (J1): the past-entry delete confirmation.
        .modifier(WearIveRunDeleteConfirmation(
            pending: $pendingDelete,
            onConfirm: { model.removeEntry(id: $0.id) }))
        // RW-158: a past entry's tap presents the Share Studio
        // sheet for that entry directly. Opening is viewing —
        // nothing is shared until the Studio's Share button.
        .sheet(item: $shareEntry) { entry in
            WearIveRunShareStudioHost(
                entry: entry,
                formatTemperature: formatTemperature,
                genderPreference: genderPreference,
                onHeroRecovered: { model.healHeroReference(entryID: $0.id, durableReference: $1) }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }

    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(WearIveRunWeatherFilter.allCases, id: \.rawValue) { option in
                    Button(option.label) { filter = option }
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(filter == option ? AppTheme.warmWhite : AppTheme.inkPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            Capsule().fill(filter == option ? AppTheme.primaryColor : AppTheme.cardOnSheet)
                        )
                }
            }
        }
    }

    private func staySection(_ stay: WearIveRunStay) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(WearIveRunDateFormatting.span(from: stay.firstDay, to: stay.lastDay))
                .font(.appEyebrow)
                .foregroundColor(AppTheme.inkMuted)
            dayList(stay.entries)
        }
    }

    private func monthGroups(_ chapter: WearIveRunChapter) -> some View {
        let groups = WearIveRunCollection.monthGroups(for: chapter)
        return VStack(alignment: .leading, spacing: 20) {
            ForEach(groups, id: \.self) { group in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(WearIveRunDateFormatting.monthLabel(year: group.year, month: group.month))
                            .font(.appItemName)
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                        Text(monthSummary(group))
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    dayList(group.entries)
                }
            }
        }
    }

    private func monthSummary(_ group: WearIveRunMonthGroup) -> String {
        let range = group.feelsLikeRangeF
        let rangeText = range.min == range.max
            ? formatTemperature(range.min)
            : "\(formatTemperature(range.min))–\(formatTemperature(range.max))"
        let days = group.dayCount == 1 ? "1 day" : "\(group.dayCount) days"
        return "\(days) · \(rangeText)"
    }

    /// One day row's label — shared by the row's two tap
    /// routes (RW-158): the share-sheet button for a past
    /// entry and the day-view navigation link for
    /// today/future render the identical row.
    private func dayRowLabel(_ entry: WearIveRunEntry) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(WearIveRunDateFormatting.dayLabel(entry.dayStart))
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Text("\(formatTemperature(entry.feelsLikeF)) feels like · \(entry.condition) · \(entry.itemNames.count) items")
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.appFootnote.weight(.semibold))
                .foregroundColor(AppTheme.inkMuted)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.sheet))
    }

    private func dayList(_ entries: [WearIveRunEntry]) -> some View {
        VStack(spacing: 10) {
            ForEach(entries) { entry in
                HStack(spacing: 8) {
                    // RW-158 (user ruling, Nov 10, 2026): the
                    // tap route is the pure policy's decision —
                    // a PAST entry opens the Share Studio sheet
                    // directly; today's (or a future planned)
                    // entry keeps its day view, unchanged.
                    if WearIveRunTapPolicy.chapterEntryDestination(for: entry) == .shareSheet {
                        Button {
                            shareEntry = entry
                        } label: {
                            dayRowLabel(entry)
                        }
                        .buttonStyle(.plain)
                    } else {
                        NavigationLink {
                            WearIveRunDayView(
                                entry: entry,
                                formatTemperature: formatTemperature,
                                genderPreference: genderPreference,
                                onDelete: { model.removeEntry(id: entry.id) },
                                onHeroRecovered: { model.healHeroReference(entryID: $0.id, durableReference: $1) }
                            )
                        } label: {
                            dayRowLabel(entry)
                        }
                        .buttonStyle(.plain)
                    }
                    // Fix wave 2026-11-07 (RW-122, iOS half):
                    // per-entry delete in the collection — the
                    // row's own trash, beside (never inside) the
                    // navigation target. RW-152 §4 (J1): routed
                    // through the delete policy — past entries
                    // confirm first, today's deletes in one tap.
                    Button {
                        requestDelete(entry)
                    } label: {
                        Image(systemName: "trash")
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(AppTheme.inkSecondary)
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Delete \(WearIveRunDateFormatting.dayLabel(entry.dayStart)) in \(entry.placeDisplayName)")
                }
            }
        }
    }
}

// MARK: - Day card + sharing (frames 9, 11–14)

struct WearIveRunDayView: View {
    let entry: WearIveRunEntry
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference
    /// RW-152 §4: the day view's own delete affordance — the
    /// host (collection / chapter) binds it to
    /// `WearIveRunCollection.removing(id:)` for this entry.
    /// Nil in seats that only present the day (no delete).
    var onDelete: (() -> Void)? = nil
    /// RW-159/RW-160: a bank re-link recovery for this entry is
    /// persisted through here (the host's collection model).
    var onHeroRecovered: ((WearIveRunEntry, String) -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    /// RW-152 §4 (J1): this (past) entry awaiting its delete
    /// confirmation.
    @State private var pendingDelete: WearIveRunEntry?
    /// RW-152 §3: the entry's hero URL, resolved through the
    /// one resolver (a storage path re-signs on appearance).
    @State private var resolvedHeroURL: String?
    private var heroURLString: String? {
        resolvedHeroURL ?? HeroRefResolver.stableDisplayReference(for: entry)
    }

    /// GEN-6 (RW-31 Wave B): sharing opens ONE Share Studio —
    /// the chapter page's two plain text buttons and the
    /// per-card blank sheets are retired.
    @State private var showStudio = false
    /// Fix wave 2026-11-07 (RW-133): the entry's photograph,
    /// tapped, opens fitted full-screen.
    @State private var showPhotoViewer = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // GEN-6 (DA-05, iOS instance): the day-view hero
                // renders through the protected HeroPhoto crop
                // (top-anchored, face-preserving) like every other
                // hero surface — the bare centre fill here was the
                // one hero-class surface still centre-cropping.
                GeometryReader { geo in
                    AsyncImage(url: heroURLString.flatMap { URL(string: $0) }) { phase in
                        switch phase {
                        case .success(let image):
                            HeroPhoto(image: image, width: geo.size.width, height: geo.size.height)
                        default:
                            Rectangle().fill(AppTheme.cardOnSheet)
                        }
                    }
                }
                .frame(height: 300)
                .clipped()
                .cornerRadius(16)
                // RW-133: the entry's photograph opens fitted
                // full-screen, stored frame as-is.
                .onTapGesture { showPhotoViewer = true }
                .fullScreenCover(isPresented: $showPhotoViewer) {
                    HeroPhotoViewer(
                        imageURL: heroURLString,
                        caption: entry.itemNames.joined(separator: " · ")
                    )
                }

                Text("\(formatTemperature(entry.feelsLikeF)) feels like · \(entry.condition)")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Text("Actual \(formatTemperature(entry.actualF)) · \(entry.placeDisplayName)")
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkSecondary)

                VStack(alignment: .leading, spacing: 6) {
                    ForEach(entry.itemNames, id: \.self) { name in
                        Text(name)
                            .font(.appSecondary)
                            .foregroundColor(AppTheme.inkPrimary)
                    }
                }

                // GEN-6 (RW-31 §4.1): ONE full-width row opens
                // the Share Studio — card choice with live
                // previews, the size control, the caption
                // choice, and the one pine Share button.
                Button {
                    showStudio = true
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Share this day")
                                .font(.appItemName)
                                .foregroundColor(AppTheme.inkPrimary)
                            Text("Postcard or Kit Card, with the day's caption.")
                                .font(.appFootnote)
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        Spacer()
                        Image(systemName: "square.and.arrow.up")
                            .font(.appItemName)
                            .foregroundColor(AppTheme.pinePayload)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.sheet))
                }
                .buttonStyle(.plain)
                .padding(.top, 8)

                // RW-152 §4: the day view's own trash — every
                // entry card carries the affordance. J1: a PAST
                // entry confirms first (the confirmation names
                // the place and the date); today's entry deletes
                // in one tap. Either way the view dismisses —
                // the entry it presented is gone.
                if let onDelete {
                    Button(role: .destructive) {
                        if WearIveRunDeletePolicy.requiresConfirmation(for: entry) {
                            pendingDelete = entry
                        } else {
                            onDelete()
                            dismiss()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "trash")
                            Text("Delete this entry")
                        }
                        .font(.appSecondary.weight(.semibold))
                    }
                    .padding(.top, 4)
                }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle(WearIveRunDateFormatting.dayLabel(entry.dayStart))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showStudio) {
            // RW-158: the day view's Share row presents the
            // same Share Studio host the chapter rows and the
            // expanded panel present directly for past entries.
            WearIveRunShareStudioHost(
                entry: entry,
                formatTemperature: formatTemperature,
                genderPreference: genderPreference,
                onHeroRecovered: onHeroRecovered
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        // RW-152 §3: resolve the hero through the one resolver
        // (a personal hero's storage path re-signs here).
        // RW-159: the RECOVERING resolution — an empty-ref entry
        // re-links from the bank so the day view's hero (and its
        // full-photo tap) heal with the cover.
        .task {
            resolvedHeroURL = await HeroRefRuntime.loadableURLString(
                for: entry, gender: genderPreference,
                onRecovered: { recovered in
                    onHeroRecovered?(entry, recovered)
                })
        }
        // RW-152 §4 (J1): the past-entry delete confirmation —
        // confirming deletes and leaves the day view.
        .modifier(WearIveRunDeleteConfirmation(
            pending: $pendingDelete,
            onConfirm: { _ in
                onDelete?()
                dismiss()
            }))
    }

}

// MARK: - Share Studio host (GEN-6 machinery; RW-158 direct entry)
//
// The Share Studio's owning view: the two render slots, the
// export-format state, the caption input, and the two render
// pipelines (moved verbatim from WearIveRunDayView, where this
// machinery previously lived). RW-158 (user ruling, Nov 10,
// 2026): a PAST entry's tap opens this sheet DIRECTLY from the
// chapter's day rows and from the expanded panel's entry row —
// the day view's "Share this day" row presents this same host,
// so every door leads to the one designed share interface.
// Opening the sheet shares nothing: the Studio's own Share
// button is the only path to the system share sheet. The
// poster pipeline loads its base through the ONE resolver
// (HeroRefRuntime.loadableURLString — RW-152 §3), so an OLD
// entry whose stored display reference is an EXPIRED signed
// URL still renders its poster via its durable reference.

struct WearIveRunShareStudioHost: View {
    let entry: WearIveRunEntry
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference
    /// RW-160 (device verdict, Nov 10, 2026): a bank re-link
    /// recovery for this entry, reported by the poster pipeline
    /// for persistence on the entry (the host's model heals it,
    /// so the recovery happens once).
    var onHeroRecovered: ((WearIveRunEntry, String) -> Void)? = nil

    /// The Studio's chosen export format. The render slots'
    /// keys carry it, so each (card, format) pair renders at
    /// most once and is cached process-wide.
    @State private var shareFormat: ShareExportFormat = .feed
    /// Garments the user removed from the Kit Card in the
    /// Studio (the input-gating escape for a garment whose
    /// render cannot load).
    @State private var excludedKitKeys: Set<String> = []
    /// GEN-3 share-render fix (kept): each card renders through
    /// its own slot — independent pipelines, render-once per
    /// content key, process-wide image cache.
    @StateObject private var posterSlot = ShareCardSlot()
    @StateObject private var kitSlot = ShareCardSlot()
    /// Verify-gate context the render pipelines publish for the
    /// Studio: the poster's face-anchor canvas point and the
    /// Kit Card's missing garment renders.
    @StateObject private var exportContext = ShareExportContext()

    /// Content keys: a re-recorded entry (new hero, new kit) is
    /// a new card; the same entry is the same card forever. The
    /// keys carry a CONTENT FINGERPRINT (GEN-5 FA-24) and, from
    /// GEN-6, the export format (RW-31 §9: the render store's
    /// slots gain the format as part of their key).
    private var shareContentFingerprint: String {
        [entry.placeKey,
         String(entry.dayStart.timeIntervalSince1970),
         String(entry.feelsLikeF),
         entry.condition,
         entry.itemNames.joined(separator: ","),
         entry.heroImageReference].joined(separator: "|")
    }
    private func posterKey(format: ShareExportFormat) -> String {
        "\(entry.id)|poster|\(format.rawValue)|\(shareContentFingerprint)"
    }
    private func kitKey(format: ShareExportFormat) -> String {
        "\(entry.id)|kit|\(format.rawValue)|\(entry.itemKeys.joined(separator: ","))|\(genderPreference.rawValue)|\(excludedKitKeys.sorted().joined(separator: ","))|\(shareContentFingerprint)"
    }

    /// The day's moment in its caption and poster forms (§5.1).
    private var moments: (caption: String, poster: String, isPastChapter: Bool) {
        ShareMomentResolver.forms(
            momentReading: entry.momentReading,
            dayStart: entry.dayStart,
            lastSavedAt: entry.lastSavedAt,
            now: Date()
        )
    }

    /// The caption system's input, filled from the day record.
    /// Legacy entries without a recorded day range fall back to
    /// the actual/feels-like pair as the range — never a
    /// fabricated figure.
    private var captionInput: ShareCaptionInput {
        let lowF = entry.dayLowF ?? min(entry.actualF, entry.feelsLikeF)
        let highF = entry.dayHighF ?? max(entry.actualF, entry.feelsLikeF)
        return ShareCaptionInput(
            place: entry.placeDisplayName,
            captionMoment: moments.caption,
            isPastChapter: moments.isPastChapter,
            feels: ShareDisplayDegrees.number(fromFormatted: formatTemperature(entry.feelsLikeF)),
            actual: ShareDisplayDegrees.number(fromFormatted: formatTemperature(entry.actualF)),
            low: ShareDisplayDegrees.number(fromFormatted: formatTemperature(lowF)),
            high: ShareDisplayDegrees.number(fromFormatted: formatTemperature(highF)),
            swingF: highF - lowF,
            feelsF: entry.feelsLikeF,
            conditionLower: entry.condition.lowercased(),
            kitNames: entry.itemNames
        )
    }

    var body: some View {
        ShareStudioView(
            entry: entry,
            captionInput: captionInput,
            posterMoment: moments.poster,
            formatTemperature: formatTemperature,
            genderPreference: genderPreference,
            posterSlot: posterSlot,
            kitSlot: kitSlot,
            exportContext: exportContext,
            format: $shareFormat,
            excludedKitKeys: excludedKitKeys,
            onFormatChanged: {
                requestPoster()
                requestKit()
            },
            onRetry: { kind in
                if kind == .poster { requestPoster(force: true) } else { requestKit(force: true) }
            },
            onRemoveGarment: { key in
                excludedKitKeys.insert(key)
                requestKit(force: true)
            },
            onHeroRecovered: onHeroRecovered
        )
        // Pre-warm BOTH cards on appearance, as two independent
        // pipelines — neither waits for the other, and each slot
        // renders at most once per (entry, format) key.
        .task { await MainActor.run { requestPoster() } }
        .task { await MainActor.run { requestKit() } }
    }

    // MARK: Render requests (slot-deduped, keyed by format)

    @MainActor
    private func requestPoster(force: Bool = false) {
        let format = shareFormat
        if force {
            posterSlot.forceRequest(key: posterKey(format: format)) { await renderPosterImage(format: format) }
        } else {
            posterSlot.request(key: posterKey(format: format)) { await renderPosterImage(format: format) }
        }
    }

    @MainActor
    private func requestKit(force: Bool = false) {
        let format = shareFormat
        if force {
            kitSlot.forceRequest(key: kitKey(format: format)) { await renderKitImage(format: format) }
        } else {
            kitSlot.request(key: kitKey(format: format)) { await renderKitImage(format: format) }
        }
    }

    // MARK: Render pipelines

    /// Poster pipeline: hero download → poster-size downscale
    /// (off the main actor) → face-anchor resolution (stored →
    /// Vision → default) → anchor crop for THIS canvas → the
    /// one export render at exact pixels. A hero that cannot
    /// load FAILS the slot (GEN-5 FA-25) — never a photo-less
    /// dark rectangle — and a composition that would clip the
    /// face cannot be produced (§4.3.3).
    private func renderPosterImage(format: ShareExportFormat) async -> UIImage? {
        guard let base = await loadHeroBase() else { return nil }
        let anchor = base.anchor
        let hero = base.image
        let cropped = await Task.detached(priority: .userInitiated) {
            ShareAnchorCrop.anchoredCropped(hero, anchor: anchor, canvasAspect: format.aspect)
        }.value
        // Anchor arithmetic, checked before rasterising: the
        // face must sit inside the crop window, and its canvas
        // position is published for the verify gate's sample.
        guard let cg = hero.cgImage else { return nil }
        let imageSize = CGSize(width: cg.width, height: cg.height)
        let rect = ShareAnchorCrop.cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: format.aspect)
        let faceTop = CGFloat(anchor.topNormalized) * imageSize.height
        let faceBottom = faceTop + CGFloat(anchor.height) * imageSize.height
        guard faceTop >= rect.minY - 1, faceBottom <= rect.maxY + 1 else { return nil }
        let placement = ShareAnchorCrop.placement(cropRect: rect, anchor: anchor, imageSize: imageSize)
        let centerX = (CGFloat(anchor.centerX) * imageSize.width - rect.minX) / rect.width
        await MainActor.run {
            exportContext.posterAnchorCanvasPoint = CGPoint(x: centerX, y: placement.faceCenterYFraction)
            exportContext.posterPlacementValid = ShareAnchorCrop.placementIsValid(
                cropRect: rect, anchor: anchor, imageSize: imageSize)
        }
        let view = MorningPosterView(
            entry: entry, hero: cropped, format: format,
            posterMoment: moments.poster, formatTemperature: formatTemperature
        )
        return await ShareExportRenderer.render(view, format: format)
    }

    /// Kit pipeline: garment renders fetched CONCURRENTLY, then
    /// the one export render. A garment whose render cannot
    /// load is recorded on the export context and FAILS the
    /// slot — the Studio holds the share in its labeled error
    /// state until retry or removal (RW-31 §7.2).
    private func renderKitImage(format: ShareExportFormat) async -> UIImage? {
        let thumbnails = await loadKitThumbnails()
        let included = Array(ShareKitCardModel.orderedItems(names: entry.itemNames, keys: entry.itemKeys)
            .filter { !excludedKitKeys.contains($0.key) }
            .prefix(ShareKitCardModel.maxItems(for: format)))
        let missing = included.filter { thumbnails[$0.key] == nil }
        await MainActor.run {
            exportContext.missingKitGarments = missing.map { MissingKitGarment(name: $0.name, key: $0.key) }
        }
        guard missing.isEmpty else { return nil }
        let view = KitCardView(
            entry: entry, format: format, items: included, thumbnails: thumbnails,
            posterMoment: moments.poster, formatTemperature: formatTemperature,
            genderPreference: genderPreference
        )
        return await ShareExportRenderer.render(view, format: format)
    }

    private func loadKitThumbnails() async -> [String: UIImage] {
        await withTaskGroup(of: (String, UIImage?).self) { group in
            for key in entry.itemKeys {
                group.addTask { (key, await fetchThumbnail(forKey: key)) }
            }
            var result: [String: UIImage] = [:]
            for await (key, image) in group {
                if let image { result[key] = image }
            }
            return result
        }
    }

    private func fetchThumbnail(forKey key: String) async -> UIImage? {
        guard let urlString = RunWearEngine.ClothingThumbnailService.shared
                .getThumbnailUrl(itemName: key, genderName: genderPreference.rawValue.uppercased()),
              let url = URL(string: urlString),
              let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
        return UIImage(data: data)
    }

    /// GEN-5 (RW-29 FA-25): the hero fetch is BOUNDED — a
    /// dedicated session with a 20-second request timeout, so a
    /// stalled fetch reaches the failure surface instead of
    /// holding the slot in its working state indefinitely.
    private static let heroFetchSession: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration)
    }()

    /// Download + downscale the hero and resolve its face
    /// anchor: the day record's stored anchor when one exists,
    /// else on-device Vision face detection, else the default
    /// upper-middle composition point (never the centre).
    private func loadHeroBase() async -> (image: UIImage, anchor: ShareFaceAnchor)? {
        // RW-152 §3: the poster pipeline loads through the one
        // resolver too — the stored display URL's signed hour
        // may be long past; the durable reference re-signs.
        // RW-160 (device verdict, Nov 10, 2026): the RECOVERING
        // resolution — build 27's New York Nov 9 Postcard failed
        // ("Your Postcard couldn't be made") while its Kit Card
        // rendered, because the entry settled with an EMPTY
        // reference: the plain resolver had nothing to re-sign,
        // this load returned nil, and the poster slot failed.
        // The entry's bank hero exists; the re-link finds it.
        guard let urlString = await HeroRefRuntime.loadableURLString(
                for: entry, gender: genderPreference,
                onRecovered: { recovered in
                    onHeroRecovered?(entry, recovered)
                }),
              let url = URL(string: urlString),
              let (data, _) = try? await Self.heroFetchSession.data(from: url),
              let image = UIImage(data: data) else { return nil }
        let downscaled = await Task.detached(priority: .userInitiated) {
            ShareCardImagePrep.downscaledForPoster(image)
        }.value
        if let stored = entry.faceAnchor {
            return (downscaled, stored)
        }
        let detected = await Task.detached(priority: .userInitiated) {
            Self.detectedFaceRect(in: downscaled)
        }.value
        if let rect = detected {
            return (downscaled, ShareFaceAnchor.from(faceRectTopLeftNormalized: rect))
        }
        return (downscaled, .defaultUpperMiddle)
    }

    /// Vision face detection for the poster crop: the largest
    /// face's rect in top-left-normalised coordinates, or nil.
    nonisolated private static func detectedFaceRect(in image: UIImage) -> CGRect? {
        guard let cg = image.cgImage else { return nil }
        let request = VNDetectFaceRectanglesRequest()
        let handler = VNImageRequestHandler(cgImage: cg, options: [:])
        guard (try? handler.perform([request])) != nil,
              let face = request.results?.max(by: { $0.boundingBox.width < $1.boundingBox.width })
        else { return nil }
        let box = face.boundingBox // Vision space: y-up
        return CGRect(x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)
    }
}

// MARK: - Share export context (GEN-6, RW-31 §7)
//
// What the render pipelines publish for the Studio's verify
// gate: the poster's face-anchor position on the composed
// canvas (the raster is sampled exactly there) and the Kit
// Card's garments whose renders could not load (the share is
// held until retry or removal).

struct MissingKitGarment: Identifiable, Equatable {
    let name: String
    let key: String
    var id: String { key }
}

final class ShareExportContext: ObservableObject {
    @Published var posterAnchorCanvasPoint: CGPoint?
    @Published var posterPlacementValid = false
    @Published var missingKitGarments: [MissingKitGarment] = []
}

// MARK: - Share Studio (GEN-6, RW-31 §4.1 — DA-04)
//
// ONE sheet in the app's design language, in three movements:
// the card (Morning Poster and Kit Card as LIVE PREVIEW
// thumbnails of the actual artifacts, poster pre-selected,
// with the three-way size control), the words (the four filled
// captions as selectable cards, Edit opens the chosen one as
// editable text), and the door (one pine Share button →
// render-and-verify → the system activity sheet). The
// blank-white per-card sheet is retired.

struct ShareStudioView: View {
    let entry: WearIveRunEntry
    let captionInput: ShareCaptionInput
    let posterMoment: String
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference
    @ObservedObject var posterSlot: ShareCardSlot
    @ObservedObject var kitSlot: ShareCardSlot
    @ObservedObject var exportContext: ShareExportContext
    @Binding var format: ShareExportFormat
    let excludedKitKeys: Set<String>
    var onFormatChanged: () -> Void
    var onRetry: (ShareCardKind) -> Void
    var onRemoveGarment: (String) -> Void
    /// RW-160: forwarded from the host — the preview's photo
    /// resolution recovers (and persists) like the poster's.
    var onHeroRecovered: ((WearIveRunEntry, String) -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var selectedKind: ShareCardKind = .poster
    @State private var selectedTemplate: ShareCaptionTemplate?
    @State private var editedCaption: String?
    @State private var editorPresented = false
    @State private var editorDraft = ""
    @State private var verifyFailed = false
    @State private var activityPresented = false
    /// RW-158: the poster preview, tapped, opens the entry's
    /// full-screen photograph (RW-133) — the full-photo
    /// affordance stays reachable from inside the sheet
    /// (WearIveRunTapPolicy.sharePreviewDestination). The URL
    /// resolves through the ONE resolver (RW-152 §3), so an
    /// old entry's expired signed reference re-signs here too.
    @State private var showPreviewPhoto = false
    @State private var previewHeroURL: String?
    private var previewHeroURLString: String? {
        previewHeroURL ?? HeroRefResolver.stableDisplayReference(for: entry)
    }

    private var orderedCaptions: [(template: ShareCaptionTemplate, text: String)] {
        ShareCaptionComposer.orderedCaptions(for: captionInput)
    }

    private var activeTemplate: ShareCaptionTemplate {
        selectedTemplate ?? orderedCaptions.first?.template ?? .feelsLikeStory
    }

    private var activeCaption: String {
        editedCaption ?? ShareCaptionComposer.caption(for: activeTemplate, input: captionInput)
    }

    private var selectedSlot: ShareCardSlot {
        selectedKind == .poster ? posterSlot : kitSlot
    }

    /// The share is held while the chosen card is not ready —
    /// and, for the Kit Card, while any garment render is
    /// missing (the labeled error state of §7.2).
    private var shareHeld: Bool {
        if selectedSlot.state != .ready { return true }
        if selectedKind == .kit && !exportContext.missingKitGarments.isEmpty { return true }
        return false
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text("Share this day")
                        .font(.appSectionTitle)
                        .foregroundColor(AppTheme.inkPrimary)
                    Spacer()
                    Button("Close") { dismiss() }
                        .font(.appButtonLabel)
                        .foregroundColor(AppTheme.pinePayload)
                }

                formatChoice
                sizeControl
                selectedPreview
                if selectedKind == .kit { kitInputState }
                captionChoice

                if verifyFailed {
                    Text(ShareCardCopy.verifyFailureLabel)
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkPrimary)
                    Button("Try again") {
                        verifyFailed = false
                        onRetry(selectedKind)
                    }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
                }

                Button {
                    shareTapped()
                } label: {
                    Text("Share")
                        .font(.appButtonLabel)
                        .foregroundColor(AppTheme.warmWhite)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(shareHeld ? AppTheme.inkMuted : AppTheme.primaryColor)
                        )
                }
                .buttonStyle(.plain)
                .disabled(shareHeld)
            }
            .padding(20)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .onChange(of: format) { _ in
            verifyFailed = false
            onFormatChanged()
        }
        .sheet(isPresented: $editorPresented) {
            captionEditor
        }
        .sheet(isPresented: $activityPresented) {
            if let image = selectedSlot.image {
                ActivityViewController(items: [image, activityItemSource(image: image)]) {
                    activityPresented = false
                    dismiss()
                }
                .ignoresSafeArea()
            }
        }
        // RW-158: the poster preview's full-screen photo
        // (RW-133) — the stored frame fitted whole, one quiet
        // caption line, exactly as the day view presents it.
        .fullScreenCover(isPresented: $showPreviewPhoto) {
            HeroPhotoViewer(
                imageURL: previewHeroURLString,
                caption: entry.itemNames.joined(separator: " · ")
            )
        }
        // RW-158 / RW-152 §3: resolve the preview photo's URL
        // through the one resolver (a storage path re-signs).
        // RW-160: the recovering form, like the poster pipeline.
        .task {
            previewHeroURL = await HeroRefRuntime.loadableURLString(
                for: entry, gender: genderPreference,
                onRecovered: { recovered in
                    onHeroRecovered?(entry, recovered)
                })
        }
    }

    // MARK: First movement — the card

    private var formatChoice: some View {
        HStack(alignment: .top, spacing: 12) {
            formatCard(kind: .poster, name: "Postcard", slot: posterSlot)
            formatCard(kind: .kit, name: "Kit Card", slot: kitSlot)
        }
    }

    private func formatCard(kind: ShareCardKind, name: String, slot: ShareCardSlot) -> some View {
        Button {
            selectedKind = kind
            verifyFailed = false
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardOnSheet)
                    switch slot.state {
                    case .ready:
                        if let image = slot.image {
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    case .failed:
                        VStack(spacing: 6) {
                            Text(ShareCardCopy.failureLabel(for: kind))
                                .font(.appFootnote)
                                .foregroundColor(AppTheme.inkPrimary)
                                .multilineTextAlignment(.center)
                            Text("Try again")
                                .font(.appFootnote.weight(.semibold))
                                .foregroundColor(AppTheme.pinePayload)
                        }
                        .padding(8)
                    case .idle, .rendering:
                        VStack(spacing: 6) {
                            ProgressView()
                            Text(ShareCardCopy.progressLabel(for: kind))
                                .font(.appFootnote)
                                .foregroundColor(AppTheme.inkSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(8)
                    }
                }
                .frame(height: 170)
                HStack {
                    Text(name)
                        .font(.appFootnote.weight(.semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Spacer()
                    if selectedKind == kind {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(AppTheme.pinePayload)
                    }
                }
            }
            .padding(8)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(AppTheme.sheet)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(selectedKind == kind ? AppTheme.primaryColor : Color.clear, lineWidth: 2)
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private var sizeControl: some View {
        Picker("Size", selection: $format) {
            ForEach(ShareExportFormat.allCases) { option in
                Text(option.label).tag(option)
            }
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder
    private var selectedPreview: some View {
        if selectedSlot.state == .ready, let image = selectedSlot.image {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxHeight: 420)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
                // RW-158: the POSTER preview is tappable — it
                // opens the entry's photograph full-screen
                // (RW-133's hero rule), so routing past entries
                // straight to this sheet did not retire the
                // full-photo affordance. (The Kit Card preview
                // carries no photograph to open.)
                .onTapGesture {
                    if selectedKind == .poster,
                       WearIveRunTapPolicy.sharePreviewDestination == .fullPhoto {
                        showPreviewPhoto = true
                    }
                }
        }
    }

    /// Kit Card input gating (§7.2): a garment whose render
    /// cannot load shows its name under an empty tile in the
    /// card and holds the share here — retry, or remove the
    /// garment from the card. Per-tile Shop › rows ride under
    /// the preview: the raster carries the printed affordance,
    /// and these live rows open the garment's shop URL through
    /// the app's existing affiliate machinery.
    @ViewBuilder
    private var kitInputState: some View {
        if !exportContext.missingKitGarments.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(exportContext.missingKitGarments) { garment in
                    HStack {
                        Text("\(garment.name) couldn't load.")
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                        Button("Remove from card") { onRemoveGarment(garment.key) }
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(AppTheme.pinePayload)
                    }
                }
                Button("Try again") { onRetry(.kit) }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
            }
        }
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(ShareKitCardModel.orderedItems(names: entry.itemNames, keys: entry.itemKeys)
                .filter { !excludedKitKeys.contains($0.key) }.enumerated()), id: \.offset) { _, item in
                if let url = shopURL(forKey: item.key, name: item.name) {
                    HStack {
                        Text(item.name)
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                        Button("Shop ›") { openURL(url) }
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(AppTheme.pinePayload)
                    }
                }
            }
        }
    }

    /// The garment's affiliate URL via the app's existing
    /// machinery (ClothingItem.affiliateURL — the same link the
    /// outfit cards and Shop sheet open). No catalogue render
    /// for the key → no link → no Shop ›.
    private func shopURL(forKey key: String, name: String) -> URL? {
        guard RunWearEngine.ClothingThumbnailService.shared.hasThumbnail(itemName: key) else { return nil }
        let item = ClothingItem(
            key: key, name: name, description: "", icon: "", amazonSearchTerm: name)
        return item.affiliateURL(for: genderPreference, subtag: "ios")
    }

    // MARK: Second movement — the words

    private var captionChoice: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Caption")
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
                Button("Edit") {
                    editorDraft = activeCaption
                    editorPresented = true
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
            }
            ForEach(Array(orderedCaptions.enumerated()), id: \.offset) { _, option in
                Button {
                    selectedTemplate = option.template
                    editedCaption = nil
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(option.template.title.uppercased())
                            .font(.appEyebrow)
                            .foregroundColor(AppTheme.pinePayload)
                        Text(option.template == activeTemplate ? activeCaption : option.text)
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkPrimary)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(AppTheme.sheet)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(option.template == activeTemplate ? AppTheme.primaryColor : Color.clear, lineWidth: 2)
                            )
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var captionEditor: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Edit caption")
                .font(.appSectionTitle)
                .foregroundColor(AppTheme.inkPrimary)
            TextEditor(text: $editorDraft)
                .font(.appSecondary)
                .frame(minHeight: 180)
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.sheet))
            HStack {
                Button("Cancel") { editorPresented = false }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
                Spacer()
                Button("Use this caption") {
                    editedCaption = editorDraft
                    editorPresented = false
                }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
            }
        }
        .padding(20)
        .background(AppTheme.background.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }

    // MARK: Third movement — the door (render-and-verify, §7)

    /// Verify the finished raster, THEN open the system sheet.
    /// Any failure: the sheet never opens and the Studio shows
    /// the verify failure line with its retry.
    private func shareTapped() {
        guard let image = selectedSlot.image else { return }
        let points: [CGPoint]
        if selectedKind == .poster {
            points = [exportContext.posterAnchorCanvasPoint].compactMap { $0 }
        } else {
            let uncapped = ShareKitCardModel.orderedItems(names: entry.itemNames, keys: entry.itemKeys)
                .filter { !excludedKitKeys.contains($0.key) }.count
            let count = min(uncapped, ShareKitCardModel.maxItems(for: format))
            points = ShareKitCardModel.tileSamplePoints(format: format, itemCount: count)
        }
        let verdict = ShareRasterVerifier.verify(image: image, format: format, samplePoints: points)
        guard verdict.passed else {
            verifyFailed = true
            return
        }
        activityPresented = true
    }

    /// The payload (§8): the verified image plus an item source
    /// carrying the chosen caption as the message, the subject
    /// for Mail, and rich link metadata titled for the day with
    /// the poster itself as its icon/image provider.
    private func activityItemSource(image: UIImage) -> ShareActivityItemSource {
        ShareActivityItemSource(
            caption: activeCaption,
            subjectLine: ShareCaptionComposer.subject(for: captionInput),
            linkTitle: ShareCaptionComposer.linkTitle(for: captionInput),
            image: image
        )
    }
}

/// The caption/subject/metadata half of the share payload
/// (RW-31 §8): the image travels as its own activity item; this
/// source vends the caption text, the Mail subject, and
/// LPLinkMetadata whose title names the day and whose icon is
/// the card itself — so Messages and Mail preview the actual
/// card, not a generic attachment.
final class ShareActivityItemSource: NSObject, UIActivityItemSource {
    let caption: String
    let subjectLine: String
    let linkTitle: String
    let image: UIImage

    init(caption: String, subjectLine: String, linkTitle: String, image: UIImage) {
        self.caption = caption
        self.subjectLine = subjectLine
        self.linkTitle = linkTitle
        self.image = image
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        caption
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        itemForActivityType activityType: UIActivity.ActivityType?
    ) -> Any? {
        caption
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        subjectForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        subjectLine
    }

    func activityViewControllerLinkMetadata(_ activityViewController: UIActivityViewController) -> LPLinkMetadata? {
        let metadata = LPLinkMetadata()
        metadata.title = linkTitle
        let provider = NSItemProvider(object: image)
        metadata.iconProvider = provider
        metadata.imageProvider = provider
        return metadata
    }
}

/// The system share sheet, presented directly so its
/// completion (sent OR cancelled) is observable — the property
/// ShareLink cannot provide and FA-63 requires: the app's own
/// sheet dismisses when the system sheet finishes.
private struct ActivityViewController: UIViewControllerRepresentable {
    let items: [Any]
    let onFinish: () -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.completionWithItemsHandler = { _, _, _, _ in onFinish() }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

// MARK: - Morning Poster (GEN-6, RW-31 §4.2 — DA-06)
//
// ONE composition rendered onto the three canvases: the photo
// (already anchor-cropped for this canvas) fills the frame;
// the settled top lockup (RunWear script + #RunAnyWear beneath,
// warm white over the top scrim); the informative block in
// exactly four tiers — (1) the feels-like numeral with "feels
// like" small beside it, (2) place · moment, (3) the outfit in
// words, promoted as the clear second read, (4) the quiet
// conditions micro-line, which yields first under pressure;
// and the foot as ONE lockup row (Wear I've Run script +
// runwear.ai). The why-line never appears here — it lives in
// the captions. On Stories the lockup sits below the top safe
// zone and the footer folds into the block as its last line,
// the whole stack ending above the bottom safe line.

struct MorningPosterView: View {
    let entry: WearIveRunEntry
    let hero: UIImage?
    let format: ShareExportFormat
    let posterMoment: String
    let formatTemperature: (Double) -> String

    private var canvas: CGSize { format.canvasPointSize }
    private var feelsText: String {
        ShareDisplayDegrees.text(fromFormatted: formatTemperature(entry.feelsLikeF))
    }
    private var tier3: String {
        SharePosterModel.tier3Line(kitNames: entry.itemNames)
    }
    private var tier4: String? {
        guard SharePosterModel.showsMicroLine(format: format, tier3Text: tier3) else { return nil }
        let actual = ShareDisplayDegrees.number(fromFormatted: formatTemperature(entry.actualF))
        if let wind = entry.windMph {
            return SharePosterModel.tier4Line(
                condition: entry.condition, actualDegrees: actual, windMph: Int(wind.rounded()))
        }
        // Legacy record without a wind figure: the micro-line
        // carries what the record holds — never a fabrication.
        return "\(entry.condition) · Actual \(actual)°"
    }

    var body: some View {
        ZStack(alignment: .top) {
            if let hero {
                HeroPhoto(image: Image(uiImage: hero), width: canvas.width, height: canvas.height)
            } else {
                AppTheme.darkBackground
            }

            // The settled top scrim: charcoal gradient, 0.62 at
            // the top edge, gone before it reaches the runner.
            LinearGradient(
                stops: [
                    .init(color: Color(red: 35 / 255, green: 35 / 255, blue: 39 / 255).opacity(0.62), location: 0),
                    .init(color: Color(red: 35 / 255, green: 35 / 255, blue: 39 / 255).opacity(0.31), location: 0.5),
                    .init(color: .clear, location: 1)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: canvas.height * 0.31)
            .frame(maxWidth: .infinity)
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Lockup: script brand + hashtag directly under it.
                VStack(spacing: 2) {
                    Text("RunWear")
                        .font(.greatVibes(size: 37))
                        .foregroundColor(AppTheme.warmWhite)
                        .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
                    Text("#RunAnyWear")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AppTheme.warmWhite)
                }
                .padding(.top, SharePosterModel.lockupTopInset(format: format))

                Spacer()

                VStack(alignment: .leading, spacing: 10) {
                    // Tier 1 — the numeral.
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(feelsText)
                            .font(.system(size: 64, weight: .heavy))
                            .foregroundColor(.white)
                        Text("feels like")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white.opacity(0.9))
                    }
                    // Tier 2 — place · moment.
                    Text(SharePosterModel.tier2Line(place: entry.placeDisplayName, posterMoment: posterMoment))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                    // Tier 3 — the outfit in words, promoted.
                    Text(tier3)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    // Tier 4 — the quiet conditions micro-line.
                    if let tier4 {
                        Text(tier4)
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundColor(.white.opacity(0.72))
                    }
                    // Stories: the footer folds in as the
                    // block's last line (§6).
                    if SharePosterModel.footerFoldsIntoBlock(format: format) {
                        footerRow
                            .padding(.top, 4)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, SharePosterModel.footerFoldsIntoBlock(format: format)
                    ? SharePosterModel.blockBottomInset(format: format) : 6)
                .background(
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.45)],
                        startPoint: .top, endPoint: .bottom
                    )
                )

                if !SharePosterModel.footerFoldsIntoBlock(format: format) {
                    footerRow
                        .padding(.horizontal, 20)
                        .padding(.bottom, SharePosterModel.blockBottomInset(format: format))
                }
            }
        }
        .frame(width: canvas.width, height: canvas.height)
        .clipped()
    }

    /// The foot as ONE lockup row: the Wear I've Run script and
    /// runwear.ai composed together, centred — never two
    /// elements in opposite corners.
    private var footerRow: some View {
        HStack(spacing: 9) {
            Text("Wear I've Run")
                .font(.greatVibes(size: 21))
                .foregroundColor(.white)
            Text("·")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.55))
            Text("runwear.ai")
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

// MARK: - Kit Card (GEN-6, RW-31 §4.5 — DA-17)
//
// The outfit in the app's own card language, on its cream
// ground: header (Wear I've Run script small, place as title,
// the weather line), then a two-column grid of LARGE squares
// on the Warm Charcoal ground token in wearing order — the
// garment name under every square in every format, its
// one-line note under the name where the format has room, and
// a per-tile Shop › printed on the card. Foot: #RunAnyWear in
// pine, runwear.ai beside it, and the Associates disclosure in
// microcopy. No prices anywhere. Fixed-light by design (frame
// 13), so its colors are AppTheme's STATIC light constants.

struct KitCardView: View {
    let entry: WearIveRunEntry
    let format: ShareExportFormat
    /// The day's garments in wearing order (removed garments
    /// already filtered by the caller).
    let items: [(name: String, key: String)]
    /// Prefetched garment renders by item key.
    var thumbnails: [String: UIImage] = [:]
    let posterMoment: String
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    private var canvas: CGSize { format.canvasPointSize }
    private var layout: ShareKitCardModel.KitGridLayout {
        ShareKitCardModel.gridLayout(format: format, itemCount: items.count)
    }

    /// Effective note visibility — the design rule plus the
    /// layout's yield under canvas pressure.
    private var showsNotes: Bool { layout.showsNotes }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Wear I've Run")
                    .font(.greatVibes(size: 24))
                    .foregroundColor(AppTheme.textPrimary)
                Text(entry.placeDisplayName)
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(AppTheme.textPrimary)
                Text(ShareKitCardModel.weatherLine(
                    feelsDegrees: ShareDisplayDegrees.number(fromFormatted: formatTemperature(entry.feelsLikeF)),
                    condition: entry.condition,
                    place: entry.placeDisplayName,
                    posterMoment: posterMoment))
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.textSecondary)
            }

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: ShareKitCardModel.kitRowSpacing) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    VStack(spacing: 0) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(AppTheme.darkCardBackground)
                            if let thumbnail = thumbnails[item.key] {
                                Image(uiImage: thumbnail)
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .padding(8)
                            }
                        }
                        .frame(width: layout.tileSide, height: layout.tileSide)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        Text(item.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AppTheme.textPrimary)
                            .multilineTextAlignment(.center)
                            .padding(.top, 8)
                        if showsNotes, let note = EngineBridge.itemNote(forKey: item.key) {
                            Text(note)
                                .font(.system(size: 12))
                                .foregroundColor(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.top, 1)
                        }
                        if hasShopLink(forKey: item.key) {
                            Text("Shop ›")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(AppTheme.primaryColor)
                                .padding(.top, 4)
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            HStack(spacing: 8) {
                Text("#RunAnyWear")
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundColor(AppTheme.primaryColor)
                Text("runwear.ai")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)
            }
            Text(ShareKitCardModel.disclosure)
                .font(.system(size: 9.5))
                .foregroundColor(AppTheme.textSecondary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .frame(width: canvas.width, height: canvas.height, alignment: .topLeading)
        .background(AppTheme.cardBackground)
    }

    /// A tile carries Shop › only when the garment has a real
    /// shop link — the catalogue render exists for its key, so
    /// the affiliate URL the Studio's live rows open exists too.
    private func hasShopLink(forKey key: String) -> Bool {
        RunWearEngine.ClothingThumbnailService.shared.hasThumbnail(itemName: key)
    }
}
