import SwiftUI

// MARK: - RW-144 first-screen sheet (design of record: v8 frames)
//
// The sheet opens with the BOXLESS weather line (no card), a
// hairline, the outfit-count header in the "3 items for your
// run" form, and boxless outfit rows — garment tile (rounded
// square), item name, description line. Below the fold the
// day-chart glance band (DayChartBandView, unchanged) is
// followed by the COMPLETE detailed weather block for the hour
// the chart marker is on.
//
// Palette (notes-v8): icons pine #175E4C in light, accent
// #5CC79F in dark (AppTheme.pinePayload is exactly that pair);
// values in ink, labels in secondary, descriptions in the
// description tone; garment tiles #F3EEE5 / #25201A; silhouettes
// ink at α168 (light) / cream at α178 (dark).

// MARK: - Boxless weather line

/// The sheet's opening line: four stats — WIND (mph), RAIN (%),
/// HUMIDITY (%), UV ("0 of 11" form) — icon + value above the
/// small label, no card anywhere.
struct BoxlessWeatherLineView: View {
    let windMph: Double
    let rainPercent: Int
    let humidityPercent: Int
    let uvIndex: Double

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            stat(icon: "wind", value: "\(Int(windMph.rounded())) mph", label: "WIND")
            Spacer(minLength: 0)
            stat(icon: "cloud.rain", value: "\(rainPercent)%", label: "RAIN")
            Spacer(minLength: 0)
            stat(icon: "drop", value: "\(humidityPercent)%", label: "HUMIDITY")
            Spacer(minLength: 0)
            stat(icon: "sun.max", value: "\(Int(uvIndex.rounded())) of 11", label: "UV")
        }
    }

    private func stat(icon: String, value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AppTheme.pinePayload)
                Text(value)
                    .font(.system(size: 15, weight: .semibold))
                    .monospacedDigit()
                    .foregroundColor(AppTheme.inkPrimary)
                    .lineLimit(1)
            }
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .tracking(0.7)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }
}

// MARK: - Outfit row (boxless)

/// One outfit row in the v8 form: a rounded-square garment tile,
/// the item name, and its description line — no card fill, no
/// stroke; the row sits directly on the sheet. The tile loads
/// through the app's existing garment-tile mechanism: in LIGHT
/// mode the bundled Treatment L tile (ClothingThumbnailService's
/// light-tile seam — a file-for-file swap folder), in dark mode
/// the existing remote thumbnail. The affiliate tap behaviour
/// is DarkClothingItemCard's, unchanged.
struct FirstScreenOutfitRow: View {
    let item: ClothingItem
    var gender: GenderPreference = .unisex

    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }

    private var tileImageURL: URL? {
        if isDark {
            return ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: gender)
        }
        return ClothingThumbnailService.shared.lightTileURL(for: item, gender: gender)
            ?? ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: gender)
    }

    var body: some View {
        if let url = item.affiliateURL(for: gender) {
            Link(destination: url) {
                rowContent
            }
            .buttonStyle(PlainButtonStyle())
        } else {
            rowContent
        }
    }

    private var rowContent: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppTheme.garmentTileFill)

                if tileImageURL == nil {
                    Image(systemName: item.icon)
                        .font(.title)
                        .foregroundColor(silhouetteColor)
                }

                if let url = tileImageURL {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .empty, .failure:
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(silhouetteColor)
                        @unknown default:
                            Image(systemName: item.icon)
                                .font(.title)
                                .foregroundColor(silhouetteColor)
                        }
                    }
                }
            }
            .frame(width: 56, height: 56)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.name)
                    .font(.appItemName)
                    .foregroundColor(AppTheme.inkPrimary)
                Text(item.description)
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.descriptionInk)
                    // RW-114 stands: the description renders
                    // WHOLE — it wraps as many lines as it needs
                    // and never ellipsizes mid-word.
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()

            Image(systemName: "arrow.up.right")
                .font(.appFootnote.weight(.semibold))
                .foregroundColor(AppTheme.inkSecondary)
                .frame(width: 44, height: 44)
        }
        .padding(.horizontal, 16)
        .contentShape(Rectangle())
    }

    /// Garment silhouette tone: warm ink at α168/255 in light
    /// mode, cream at α178/255 in dark (notes-v8).
    private var silhouetteColor: Color {
        isDark
            ? Color(hex: AppTheme.inkDarkHex).opacity(178.0 / 255.0)
            : Color(hex: AppTheme.inkLightHex).opacity(168.0 / 255.0)
    }
}

// MARK: - Detailed weather block (below the fold)

/// The COMPLETE detailed weather block (Nov 4, 2026 ruling,
/// carried into RW-144): every metric of the settled set for
/// the hour the day-chart marker is on — the same
/// `WeatherDetailType` values the detail sheet tabs between
/// (WeatherOutfitView.allWeatherDetails), rendered as rows on
/// the sheet. Tapping a row opens that metric's existing
/// detail sheet, exactly as before.
struct DetailedWeatherBlockView: View {
    let details: [WeatherDetailType]
    let onSelect: (WeatherDetailType) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Weather details")
                .font(.appItemName)
                .foregroundColor(AppTheme.inkPrimary)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)

            ForEach(Array(details.enumerated()), id: \.element.id) { index, detail in
                Button {
                    onSelect(detail)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: detail.icon)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(AppTheme.pinePayload)
                            .frame(width: 24)
                        Text(detail.title)
                            .font(.appSecondary)
                            .foregroundColor(AppTheme.inkPrimary)
                        Spacer()
                        Text(detail.summaryValue)
                            .font(.appSecondary.weight(.semibold))
                            .monospacedDigit()
                            .foregroundColor(AppTheme.inkPrimary)
                        Image(systemName: "chevron.right")
                            .font(.appFootnote.weight(.semibold))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < details.count - 1 {
                    Rectangle()
                        .fill(AppTheme.sheetHairline)
                        .frame(height: 1)
                        .padding(.horizontal, 16)
                }
            }
        }
    }
}
