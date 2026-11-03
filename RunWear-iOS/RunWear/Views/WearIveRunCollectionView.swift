import SwiftUI
import RunWearEngine

// MARK: - "Wear I've Run" Collection (RW-26, checklist §C; notes §8-K)
//
// Chapters BY LOCATION. Cover = most recent card + day count +
// date span + feels-like range, all computed. Multi-stay chapters
// count stays and name the last stay — never a false-contiguity
// span. Hometown-scale chapters group by year → month rows. The
// weather filter lives at the collection level AND inside
// chapters, and recomputes every summary it touches. Today is
// pinned. Sharing happens inside, card by card (Poster or Kit
// Card — no Range, no collection-level sharing).

struct WearIveRunCollectionView: View {
    @ObservedObject var model: WearIveRunCollectionModel
    /// Temperatures render in the user's unit.
    let formatTemperature: (Double) -> String
    let genderPreference: GenderPreference

    @State private var filter: WearIveRunWeatherFilter = .all

    private var chapters: [WearIveRunChapter] {
        WearIveRunCollection.chapters(from: model.entries, filter: filter)
    }

    private var todays: [WearIveRunEntry] {
        WearIveRunCollection.todaysEntries(from: model.entries, today: Date())
            .filter { WearIveRunWeatherClassifier.matches($0, filter: filter) }
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
                } else if chapters.isEmpty {
                    Text("No days match this filter yet.")
                        .font(.appSecondary)
                        .foregroundColor(AppTheme.inkSecondary)
                } else {
                    if let today = todays.first {
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
        .navigationTitle("Wear I've Run")
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
            Text("Nothing here yet")
                .font(.appSectionTitle)
                .foregroundColor(AppTheme.inkPrimary)
            Text("Your outfits are saved here as you run them — one entry a day, starting with today. Nothing is backfilled.")
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
                Text(entry.placeName)
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Text("\(formatTemperature(entry.feelsLikeF)) feels like · \(entry.condition) · \(entry.itemNames.count) items")
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
        AsyncImage(url: URL(string: entry.heroImageReference)) { phase in
            switch phase {
            case .success(let image):
                image.resizable().aspectRatio(contentMode: .fill)
            default:
                Rectangle().fill(AppTheme.cardOnSheet)
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

    @State private var shareKind: ShareKind?
    @State private var posterImage: Image?
    @State private var kitImage: Image?
    /// Garment renders prefetched as images — ImageRenderer does
    /// not wait for AsyncImage, so the Kit Card renders from these.
    @State private var kitThumbnails: [String: UIImage] = [:]

    private enum ShareKind: Identifiable {
        case poster, kit
        var id: String { self == .poster ? "poster" : "kit" }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AsyncImage(url: URL(string: entry.heroImageReference)) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Rectangle().fill(AppTheme.cardOnSheet)
                    }
                }
                .frame(height: 300)
                .clipped()
                .cornerRadius(16)

                Text("\(formatTemperature(entry.feelsLikeF)) feels like · \(entry.condition)")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Text("Actual \(formatTemperature(entry.actualF)) · \(entry.placeName)")
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
                HStack(spacing: 12) {
                    Button("Morning Poster") { shareKind = .poster }
                        .font(.appButtonLabel)
                        .foregroundColor(AppTheme.pinePayload)
                    Button("Kit Card") { shareKind = .kit }
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
            shareSheet(for: kind)
        }
        .task {
            kitThumbnails = await loadKitThumbnails()
            posterImage = await renderPoster()
            kitImage = await renderKitCard()
        }
    }

    /// The native share sheet with the caption pre-filled
    /// #RunAnyWear (frame 14).
    @ViewBuilder
    private func shareSheet(for kind: ShareKind) -> some View {
        let image = kind == .poster ? posterImage : kitImage
        if let image {
            ShareLink(
                item: image,
                subject: Text("RunWear — Wear I've Run"),
                message: Text("#RunAnyWear"),
                preview: SharePreview("Wear I've Run", image: image)
            ) {
                Label("Share", systemImage: "square.and.arrow.up")
                    .font(.appButtonLabel)
                    .padding(24)
            }
        } else {
            ProgressView().padding(24)
        }
    }

    @MainActor
    private func renderPoster() async -> Image? {
        let hero = await loadHeroUIImage()
        let view = MorningPosterView(entry: entry, hero: hero, formatTemperature: formatTemperature)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0) }
    }

    @MainActor
    private func renderKitCard() async -> Image? {
        let view = KitCardView(entry: entry, genderPreference: genderPreference, thumbnails: kitThumbnails)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0) }
    }

    private func loadKitThumbnails() async -> [String: UIImage] {
        var result: [String: UIImage] = [:]
        for key in entry.itemKeys {
            guard let urlString = RunWearEngine.ClothingThumbnailService.shared
                    .getThumbnailUrl(itemName: key, genderName: genderPreference.rawValue.uppercased()),
                  let url = URL(string: urlString),
                  let (data, _) = try? await URLSession.shared.data(from: url),
                  let image = UIImage(data: data) else { continue }
            result[key] = image
        }
        return result
    }

    private func loadHeroUIImage() async -> UIImage? {
        guard let url = URL(string: entry.heroImageReference),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data) else { return nil }
        return image
    }
}

// MARK: - Morning Poster (frame 12)

/// The poster artifact: hero + the script "RunWear" lockup with
/// #RunAnyWear directly under it, a top area scrim that is gone
/// before the runner's head, UI-sans body copy, and the "Wear I've
/// Run" script at the foot.
struct MorningPosterView: View {
    let entry: WearIveRunEntry
    let hero: UIImage?
    let formatTemperature: (Double) -> String

    var body: some View {
        ZStack(alignment: .top) {
            if let hero {
                Image(uiImage: hero)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
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
            .frame(height: 150)
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
                .padding(.top, 20)

                Spacer()

                // Body in the UI sans (Bebas retired, artifact too).
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(formatTemperature(entry.feelsLikeF)) · \(entry.condition)")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                    Text("\(entry.placeName) · \(WearIveRunDateFormatting.dayLabel(entry.dayStart))")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.85))
                    Text(entry.itemNames.prefix(4).joined(separator: " · "))
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.85))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.45)],
                        startPoint: .top, endPoint: .bottom
                    )
                )

                // Foot: the collection's signature.
                HStack(spacing: 8) {
                    Text("Wear I've Run")
                        .font(.greatVibes(size: 24))
                        .foregroundColor(.white)
                    Spacer()
                    Text("runwear.ai")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 18)
            }
        }
        .frame(width: 390, height: 520)
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
            Text("\(entry.placeName) · \(WearIveRunDateFormatting.dayLabel(entry.dayStart))")
                .font(.system(size: 13))
                .foregroundColor(AppTheme.textSecondary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(Array(zip(entry.itemNames, entry.itemKeys).enumerated()), id: \.offset) { _, pair in
                    VStack(spacing: 6) {
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
                        .frame(height: 90)
                        Text(pair.0)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(AppTheme.textPrimary)
                            .multilineTextAlignment(.center)
                    }
                }
            }

            Text("#RunAnyWear")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(AppTheme.primaryColor)
        }
        .padding(20)
        .frame(width: 390)
        .background(AppTheme.cardBackground)
    }
}
