import SwiftUI
import RunWearEngine
import Vision

// MARK: - "Wear I've Run" Collection (RW-26, checklist §C; notes §8-K)
//
// Chapters BY LOCATION (within a location, the entries are
// sessions — GEN-3). Cover = most recent card + day count +
// date span + feels-like range, all computed. Multi-stay chapters
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
                            Text("Today")
                                .font(.appEyebrow)
                                .foregroundColor(AppTheme.inkMuted)
                            NavigationLink {
                                WearIveRunDayView(
                                    entry: today,
                                    formatTemperature: formatTemperature,
                                    genderPreference: genderPreference
                                )
                            } label: {
                                entryCard(today)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    ForEach(chapters, id: \.placeName) { chapter in
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
        // GEN-5 (RW-28 sharing fix + addendum 2): a missing or
        // unresolved reference renders the designed treatment — a
        // card-fill placeholder with the photo glyph — never a
        // blank, never a crash; a resolved photo renders through
        // HeroPhoto's TOP-anchored crop, so a thumbnail can never
        // present the runner headless the way a center fill did.
        GeometryReader { geo in
            AsyncImage(url: URL(string: entry.heroImageReference)) { phase in
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
    }
}

// MARK: - Chapter

struct WearIveRunChapterView: View {
    @ObservedObject var model: WearIveRunCollectionModel
    let placeName: String
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    @State private var filter: WearIveRunWeatherFilter = .all

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

    private func dayList(_ entries: [WearIveRunEntry]) -> some View {
        VStack(spacing: 10) {
            ForEach(entries) { entry in
                NavigationLink {
                    WearIveRunDayView(
                        entry: entry,
                        formatTemperature: formatTemperature,
                        genderPreference: genderPreference
                    )
                } label: {
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
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Day card + sharing (frames 9, 11–14)

struct WearIveRunDayView: View {
    let entry: WearIveRunEntry
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    @State private var shareKind: ShareCardKind?
    /// GEN-3 share-render fix: each card renders through its own
    /// slot (ShareCardRenderStore) — independent pipelines,
    /// render-once per content key, process-wide image cache.
    /// The pre-fix view ran ONE serial task (every Kit thumbnail
    /// download, then the poster, then the Kit Card) into three
    /// @State values the share sheet branched over, so the sheet
    /// waited behind the other card's downloads and its content
    /// was replaced at every phase landing — the ~23 s blank
    /// spinner and the flickering card Sol recorded on build 21.
    @StateObject private var posterSlot = ShareCardSlot()
    @StateObject private var kitSlot = ShareCardSlot()

    /// Content keys: a re-recorded entry (new hero, new kit) is a
    /// new card; the same entry is the same card forever.
    /// GEN-5 (RW-29 FA-24): the content keys carry a CONTENT
    /// FINGERPRINT, not just identity — an in-session settle can
    /// change the entry's place, feels-like, condition or items
    /// in place (same id), and a key that cannot see the change
    /// serves the stale card for the rest of the session.
    private var shareContentFingerprint: String {
        [entry.placeKey,
         String(entry.dayStart.timeIntervalSince1970),
         String(entry.feelsLikeF),
         entry.condition,
         entry.itemNames.joined(separator: ","),
         entry.heroImageReference].joined(separator: "|")
    }
    private var posterKey: String { "\(entry.id)|poster|\(shareContentFingerprint)" }
    private var kitKey: String { "\(entry.id)|kit|\(entry.itemKeys.joined(separator: ","))|\(genderPreference.rawValue)|\(shareContentFingerprint)" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // GEN-6 (DA-05, iOS instance): the day-view hero
                // renders through the protected HeroPhoto crop
                // (top-anchored, face-preserving) like every other
                // hero surface — the bare centre fill here was the
                // one hero-class surface still centre-cropping.
                GeometryReader { geo in
                    AsyncImage(url: URL(string: entry.heroImageReference)) { phase in
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

                // Sharing lives inside, card by card: the user
                // chooses Poster or Kit Card (frame 11 — no Range).
                // Tapping also requests the render (the appearance
                // pre-warm usually beats the tap; the slot dedupes).
                HStack(spacing: 12) {
                    Button("Morning Poster") {
                        requestPoster()
                        shareKind = .poster
                    }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
                    Button("Kit Card") {
                        requestKit()
                        shareKind = .kit
                    }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
                }
                .padding(.top, 8)
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle(WearIveRunDateFormatting.dayLabel(entry.dayStart))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $shareKind) { kind in
            // WS-5 (§1.3): named detent — this choice sheet was
            // the one unnamed presentation on the surface; it
            // now rests at .large like the other scrolling-free
            // share door (its content does not scroll; .large
            // names the detent it already defaulted to).
            ShareCardSheetView(
                slot: kind == .poster ? posterSlot : kitSlot,
                kind: kind,
                onRetry: { kind == .poster ? requestPoster() : requestKit() }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        // Pre-warm BOTH cards on appearance, as two independent
        // pipelines — neither waits for the other, and each slot
        // renders at most once per entry (the cache serves any
        // later visit outright). The pre-fix single serial task
        // is the 23-second spinner's mechanism; it is gone.
        .task { await MainActor.run { requestPoster() } }
        .task { await MainActor.run { requestKit() } }
    }

    // MARK: Render requests (slot-deduped)

    @MainActor
    private func requestPoster() {
        posterSlot.request(key: posterKey) { await renderPosterImage() }
    }

    @MainActor
    private func requestKit() {
        kitSlot.request(key: kitKey) { await renderKitImage() }
    }

    // MARK: Render pipelines

    /// Poster pipeline: hero download → poster-size downscale
    /// (off the main actor) → the one full-size render.
    /// GEN-5 (RW-29 FA-25): a poster whose hero could not be
    /// loaded FAILS the slot (the labeled failure surface with
    /// Try again) — it never lands as a "ready" photo-less dark
    /// rectangle, which is what the build-25 pipeline produced
    /// and what Sol's walkthrough received as a black payload.
    private func renderPosterImage() async -> UIImage? {
        guard let hero = await loadHeroUIImage() else { return nil }
        let view = MorningPosterView(entry: entry, hero: hero, formatTemperature: formatTemperature)
        return await MainActor.run {
            let renderer = ImageRenderer(content: view)
            renderer.scale = 3
            return renderer.uiImage
        }
    }

    /// Kit pipeline: garment renders fetched CONCURRENTLY (the
    /// pre-fix loop awaited them one at a time), then the one
    /// full-size render. ImageRenderer does not wait for
    /// AsyncImage, so the card renders from the fetched images.
    private func renderKitImage() async -> UIImage? {
        let thumbnails = await loadKitThumbnails()
        let view = KitCardView(entry: entry, genderPreference: genderPreference, thumbnails: thumbnails)
        return await MainActor.run {
            let renderer = ImageRenderer(content: view)
            renderer.scale = 3
            return renderer.uiImage
        }
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

    private func loadHeroUIImage() async -> UIImage? {
        guard let url = URL(string: entry.heroImageReference),
              let (data, _) = try? await Self.heroFetchSession.data(from: url),
              let image = UIImage(data: data) else { return nil }
        // Downscale to the poster's rendered size OFF the main
        // actor, so the render never pays full-resolution decode.
        let downscaled = await Task.detached(priority: .userInitiated) {
            ShareCardImagePrep.downscaledForPoster(image)
        }.value
        // GEN-5 (RW-28 sharing fix): crop to the poster's 4:5
        // window FACE-PRESERVING (Vision face rect when one is
        // found; the top-anchored head band otherwise) — the
        // gen-4 poster center-cropped the face off entirely.
        return await Task.detached(priority: .userInitiated) {
            ShareCardCrop.facePreservingCropped(
                downscaled,
                faceRectTopLeftNormalized: Self.detectedFaceRect(in: downscaled)
            )
        }.value
    }

    /// Vision face detection for the poster crop: the largest
    /// face's rect in top-left-normalised coordinates, or nil —
    /// the crop then falls back to its top anchor (the head band
    /// every RunWear hero portrait keeps at the top).
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

// MARK: - Share sheet (per card)

/// The share door for ONE card, observing ONLY that card's slot:
/// the other card's pipeline cannot republish this sheet. States:
/// ready → the rendered CARD ITSELF, drawn on the sheet, with a
/// Share affordance and a Close; rendering/idle → the LABELED
/// working state (the hero-pill rule: labeled, state-driven —
/// never a bare spinner on a blank sheet); failed → the failure
/// line with a retry. EVERY state carries an explicit Close —
/// the census path trapped the user in the sheet for ~31 s
/// with no cancel affordance at all.
///
/// GEN-5 (RW-29 FA-63 / FA-65): build 25's ready state was one
/// "Share" label on a full-height white sheet — the card was
/// never drawn, there was no exit control, and because the
/// door was a ShareLink (which reports no completion), this
/// sheet stayed open behind the system sheet after either
/// outcome. The door is now a UIActivityViewController whose
/// completion handler dismisses this sheet on BOTH outcomes,
/// and its payload is exactly the card image plus the settled
/// caption "#RunAnyWear" — no subject field exists in this
/// path, so nothing concatenates onto the caption (FA-65;
/// Android's caption is the same hashtag alone). The sheet
/// itself sits on the app's cream ground (cream map: the app's
/// own share sheet was an Absent row).
struct ShareCardSheetView: View {
    @ObservedObject var slot: ShareCardSlot
    let kind: ShareCardKind
    var onRetry: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var activityPresented = false

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            content
        }
        .sheet(isPresented: $activityPresented) {
            if let image = slot.image {
                ActivityViewController(items: [image, "#RunAnyWear"]) {
                    activityPresented = false
                    dismiss()
                }
                .ignoresSafeArea()
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch slot.state {
        case .ready:
            if let image = slot.image {
                VStack(spacing: 16) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
                    Button {
                        activityPresented = true
                    } label: {
                        Label("Share", systemImage: "square.and.arrow.up")
                            .font(.appButtonLabel)
                            .foregroundColor(AppTheme.pinePayload)
                    }
                    Button("Close") { dismiss() }
                        .font(.appButtonLabel)
                        .foregroundColor(AppTheme.pinePayload)
                }
                .padding(24)
            } else {
                progress
            }
        case .failed:
            VStack(spacing: 12) {
                Text(ShareCardCopy.failureLabel(for: kind))
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkPrimary)
                Button("Try again") { onRetry() }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
                Button("Close") { dismiss() }
                    .font(.appButtonLabel)
                    .foregroundColor(AppTheme.pinePayload)
            }
            .padding(24)
        case .idle, .rendering:
            progress
        }
    }

    private var progress: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(ShareCardCopy.progressLabel(for: kind))
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
            Button("Close") { dismiss() }
                .font(.appButtonLabel)
                .foregroundColor(AppTheme.pinePayload)
        }
        .padding(24)
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

// MARK: - Morning Poster (frame 12)

/// The poster artifact: hero + the script "RunWear" lockup with
/// #RunAnyWear directly under it, a top area scrim that is gone
/// before the runner's head, UI-sans body copy, and the "Wear I've
/// Run" script at the foot.
///
/// GEN-3 X-11: this view is composed at ONE size (the
/// MorningPosterMetrics canvas) and rendered once, full-size, by
/// `renderPoster()`; the share preview is a raster scale-down of
/// that render — there is no thumbnail-size composition of this
/// view anywhere on iOS, so the Android thumbnail text collision
/// has no mechanism here. The lockup band and the body+foot band
/// are sequential VStack bands across a Spacer; their bounded
/// heights are pinned in MorningPosterMetrics.
struct MorningPosterView: View {
    let entry: WearIveRunEntry
    let hero: UIImage?
    let formatTemperature: (Double) -> String

    var body: some View {
        ZStack(alignment: .top) {
            if let hero {
                // GEN-5: the hero arrives already face-cropped to
                // the poster aspect (loadHeroUIImage); HeroPhoto's
                // top anchor is the second line of defence — a
                // bare center fill never renders this poster.
                HeroPhoto(
                    image: Image(uiImage: hero),
                    width: MorningPosterMetrics.canvasWidth,
                    height: MorningPosterMetrics.canvasHeight
                )
            } else {
                AppTheme.darkBackground
            }

            // The area scrim (notes §7.4): gray gradient
            // rgba(35,35,39,.62) at the top edge, gone by ~150pt —
            // its own layer behind the lockup only.
            LinearGradient(
                stops: [
                    .init(color: Color(red: 35 / 255, green: 35 / 255, blue: 39 / 255).opacity(0.62), location: 0),
                    .init(color: Color(red: 35 / 255, green: 35 / 255, blue: 39 / 255).opacity(0.31), location: 0.5),
                    .init(color: .clear, location: 1)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: MorningPosterMetrics.scrimHeight)
            .frame(maxWidth: .infinity)
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Lockup: script brand + hashtag directly under it.
                // GEN-3 X-11: every layout value comes from
                // MorningPosterMetrics (the dual-membered pure
                // enum), so the band geometry the tests pin is
                // the geometry that renders.
                VStack(spacing: MorningPosterMetrics.lockupSpacing) {
                    Text("RunWear")
                        .font(.greatVibes(size: MorningPosterMetrics.brandFontSize))
                        .foregroundColor(AppTheme.warmWhite)
                        .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
                    Text("#RunAnyWear")
                        .font(.system(size: MorningPosterMetrics.hashtagFontSize, weight: .semibold))
                        .foregroundColor(AppTheme.warmWhite)
                }
                .padding(.top, MorningPosterMetrics.lockupTopPadding)

                Spacer()

                // Body in the UI sans (Bebas retired, artifact too).
                VStack(alignment: .leading, spacing: MorningPosterMetrics.bodySpacing) {
                    Text("\(formatTemperature(entry.feelsLikeF)) · \(entry.condition)")
                        .font(.system(size: MorningPosterMetrics.feelsFontSize, weight: .semibold))
                        .foregroundColor(.white)
                    // GEN-5 (RW-29 FA-21): the second line
                    // carries the run's human MOMENT (the
                    // entry's recorded moment reading), never a
                    // stamped "Nov 5" date form (frame 12).
                    Text("\(entry.placeDisplayName) · \(entry.momentReading.rendered)")
                        .font(.system(size: MorningPosterMetrics.placeFontSize))
                        .foregroundColor(.white.opacity(0.85))
                    Text(entry.itemNames.prefix(4).joined(separator: " · "))
                        .font(.system(size: MorningPosterMetrics.itemsFontSize))
                        .foregroundColor(.white.opacity(0.85))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(MorningPosterMetrics.bodyPadding)
                .background(
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.45)],
                        startPoint: .top, endPoint: .bottom
                    )
                )

                // Foot: the collection's signature.
                HStack(spacing: 8) {
                    Text("Wear I've Run")
                        .font(.greatVibes(size: MorningPosterMetrics.footFontSize))
                        .foregroundColor(.white)
                    Spacer()
                    Text("runwear.ai")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding(.horizontal, MorningPosterMetrics.footHorizontalPadding)
                .padding(.bottom, MorningPosterMetrics.footBottomPadding)
            }
        }
        .frame(width: MorningPosterMetrics.canvasWidth, height: MorningPosterMetrics.canvasHeight)
        .clipped()
    }
}

// MARK: - Kit Card (frame 13)

/// The kit artifact: the day's garments as the app's real renders
/// (the engine's thumbnail map, by item key), on the sheet surface.
/// WS-8: the artifact is fixed-light by design (frame 13), so its
/// colors are AppTheme's STATIC light constants (textPrimary /
/// textSecondary / backgroundColor / cardBackground / primaryColor)
/// — never the dynamic pair, which would flip the rendered share
/// image with the app's appearance.
struct KitCardView: View {
    let entry: WearIveRunEntry
    let genderPreference: GenderPreference
    /// Prefetched garment renders by item key (the live view
    /// passes these; previews may omit them).
    var thumbnails: [String: UIImage] = [:]

    private func thumbnailURL(forKey key: String) -> URL? {
        RunWearEngine.ClothingThumbnailService.shared
            .getThumbnailUrl(itemName: key, genderName: genderPreference.rawValue.uppercased())
            .flatMap { URL(string: $0) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Wear I've Run")
                .font(.greatVibes(size: 26))
                .foregroundColor(AppTheme.textPrimary)
            Text("\(entry.placeDisplayName) · \(WearIveRunDateFormatting.dayLabel(entry.dayStart))")
                .font(.system(size: 13))
                .foregroundColor(AppTheme.textSecondary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(Array(zip(entry.itemNames, entry.itemKeys).enumerated()), id: \.offset) { _, pair in
                    VStack(spacing: 6) {
                        // GEN-5 (RW-29 FA-64): the garment
                        // renders are charcoal garments on pure
                        // black source photographs (opaque — the
                        // black is baked into the asset, verified
                        // against the stored bytes). Build 25
                        // floated each as a hard-cornered black
                        // square inside a taller cream cell,
                        // which reads as a broken image and
                        // collapses to a black void at share
                        // scale. The tile is now a designed
                        // object: a square tile, rounded corners,
                        // a hairline edge — the dark product tile
                        // the photography was made as.
                        Group {
                            if let thumbnail = thumbnails[pair.1] {
                                Image(uiImage: thumbnail)
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                            } else {
                                AsyncImage(url: thumbnailURL(forKey: pair.1)) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().aspectRatio(contentMode: .fit)
                                    default:
                                        Rectangle()
                                            .fill(AppTheme.backgroundColor)
                                            .overlay(
                                                Image(systemName: "tshirt")
                                                    .foregroundColor(AppTheme.textSecondary)
                                            )
                                    }
                                }
                            }
                        }
                        .frame(width: 90, height: 90)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(AppTheme.textSecondary.opacity(0.35), lineWidth: 1)
                        )
                        Text(pair.0)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(AppTheme.textPrimary)
                            .multilineTextAlignment(.center)
                        // GEN-5 (RW-29 FA-17): frame 13 carries
                        // the catalogue's one-line note under
                        // each garment's name, resolved from
                        // the engine catalogue by item key.
                        if let note = EngineBridge.itemNote(forKey: pair.1) {
                            Text(note)
                                .font(.system(size: 11))
                                .foregroundColor(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                    }
                }
            }

            Text("#RunAnyWear")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(AppTheme.primaryColor)

            // GEN-5 (RW-29 FA-17): frame 13's single call to
            // action and the Amazon Associates disclosure —
            // both verbatim, exactly one CTA on the card.
            Text("Shop this kit →")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppTheme.primaryColor)
            Text("As an Amazon Associate, RunWear earns from qualifying purchases.")
                .font(.system(size: 10))
                .foregroundColor(AppTheme.textSecondary)
        }
        .padding(20)
        .frame(width: 390)
        .background(AppTheme.cardBackground)
    }
}
