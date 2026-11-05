import WidgetKit
import SwiftUI

// MARK: - RunWear widget (GEN-5, Addendum 7 — RW-10)
//
// The iOS counterpart of Android's "Running Outfit" widget. It
// renders the PERSISTED last-good recommendation (the app's
// WidgetLastGoodStore writes it on every successful NOW-frame
// load, Android's WidgetLastGoodStore pattern) — the extension
// itself fetches nothing, so what it shows is always a real
// recommendation the app actually made, with its age stated by
// the shared freshness model. With no record at all, it shows
// the authored error card (Android's category model), never a
// blank and never raw system text.
//
// Settled glance rules, applied per family: a widget is a
// glance — the feels-like temperature gets hero treatment
// alongside the outfit picks (the outfit is priced off the
// temperature), with actual temperature, wind, and condition
// as the supporting line; every size is composed for its size
// (Small, Medium, and Large each carry a different amount of
// truth, laid out for that canvas — no stretched compositions);
// the freshness label is part of every composition and is
// derived from the record at render time, so it ACTUALLY
// renders and ages honestly; the approved cream / Warm
// Charcoal / pine system applies in full (AppTheme tokens).

struct RunWearWidgetEntry: TimelineEntry {
    let date: Date
    let record: WidgetLastGood?
    let errorCategory: WidgetErrorCategory?
}

struct RunWearWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> RunWearWidgetEntry {
        RunWearWidgetEntry(
            date: Date(),
            record: Self.sampleRecord,
            errorCategory: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (RunWearWidgetEntry) -> Void) {
        completion(currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RunWearWidgetEntry>) -> Void) {
        let now = Date()
        var entries = [currentEntry(at: now)]
        // A second entry at the stale boundary, so the eyebrow
        // flips to its age form on time even if the app is not
        // opened again; the refresh policy below keeps the
        // timeline moving hourly after that.
        if let record = entries.first?.record {
            let staleAt = record.savedAt.addingTimeInterval(WidgetFreshness.staleThreshold + 1)
            if staleAt > now {
                entries.append(RunWearWidgetEntry(
                    date: staleAt,
                    record: record,
                    errorCategory: nil
                ))
            }
        }
        let nextRefresh = now.addingTimeInterval(60 * 60)
        completion(Timeline(entries: entries, policy: .after(nextRefresh)))
    }

    private func currentEntry(at date: Date = Date()) -> RunWearWidgetEntry {
        RunWearWidgetEntry(
            date: date,
            record: WidgetLastGoodStore.load(),
            errorCategory: WidgetLastGoodStore.loadErrorCategory()
        )
    }

    /// The widget gallery's placeholder record (design-time
    /// content only — never written to the store).
    static var sampleRecord: WidgetLastGood {
        WidgetLastGood(
            feelsLikeF: 41,
            temperatureF: 38,
            conditionText: "Clear",
            conditionIcon: "sun.max.fill",
            windMph: 8,
            humidityPercent: 54,
            uvIndex: 1,
            locationName: "Kansas City, MO",
            topBaseName: "Long Sleeve Shirt",
            layerNames: ["Windbreaker"],
            bottomName: "Tights",
            secondaryNames: ["Running Cap", "Light Gloves"],
            laterTodayLine: "By 6 PM: drop the Windbreaker · rain 40%",
            unitIsCelsius: false,
            savedAt: Date()
        )
    }
}

// MARK: - Entry view (per-family compositions)

struct RunWearWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: RunWearWidgetEntry

    var body: some View {
        Group {
            if let record = entry.record {
                switch family {
                case .systemLarge:
                    largeContent(record)
                case .systemMedium:
                    mediumContent(record)
                default:
                    smallContent(record)
                }
            } else {
                errorContent
            }
        }
        .containerBackground(AppTheme.background, for: .widget)
    }

    // SMALL — eyebrow, the feels-like hero with its condition,
    // the outfit stack, the supporting line. The outfit is the
    // hero's partner: base layer bold, layers on one line, the
    // bottom, and the secondary garments counted honestly.
    private func smallContent(_ record: WidgetLastGood) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(WidgetFreshness.eyebrow(record, now: entry.date))
                .font(.caption2.weight(.bold))
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(1)

            HStack(spacing: 6) {
                Text(record.formatTemperature(fahrenheit: record.feelsLikeF))
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(AppTheme.inkPrimary)
                Image(systemName: record.conditionIcon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(AppTheme.pinePayload)
            }

            Text(record.topBaseName)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)
            if !record.layerNames.isEmpty {
                Text(record.layerNames.joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundColor(AppTheme.inkPrimary)
                    .lineLimit(1)
            }
            Text(record.bottomName)
                .font(.subheadline)
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)
            if !record.secondaryNames.isEmpty {
                Text("+\(record.secondaryNames.count) more")
                    .font(.caption)
                    .foregroundColor(AppTheme.inkSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 2)

            Text(record.supportingLine)
                .font(.caption2)
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    // MEDIUM — the wide canvas splits: weather on the left
    // (eyebrow, place, the hero numeral, the supporting line),
    // the outfit on the right (the full stack, secondary
    // garments named rather than counted).
    private func mediumContent(_ record: WidgetLastGood) -> some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(WidgetFreshness.eyebrow(record, now: entry.date))
                    .font(.caption2.weight(.bold))
                    .foregroundColor(AppTheme.inkSecondary)
                    .lineLimit(1)
                Text(record.locationName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    Text(record.formatTemperature(fahrenheit: record.feelsLikeF))
                        .font(.system(size: 38, weight: .bold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Image(systemName: record.conditionIcon)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(AppTheme.pinePayload)
                }
                Text(record.supportingLine)
                    .font(.caption2)
                    .foregroundColor(AppTheme.inkSecondary)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .leading, spacing: 3) {
                Text(record.topBaseName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                    .lineLimit(1)
                ForEach(record.layerNames, id: \.self) { name in
                    Text(name)
                        .font(.subheadline)
                        .foregroundColor(AppTheme.inkPrimary)
                        .lineLimit(1)
                }
                Text(record.bottomName)
                    .font(.subheadline)
                    .foregroundColor(AppTheme.inkPrimary)
                    .lineLimit(1)
                ForEach(record.secondaryNames, id: \.self) { name in
                    Text(name)
                        .font(.subheadline)
                        .foregroundColor(AppTheme.inkSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    // LARGE — the brand strip with the rendered "Updated …"
    // label, the place, the hero, the full supporting line,
    // every garment on its own line, the later-today line, and
    // the humidity/UV truth the smaller sizes have no room to
    // carry truthfully.
    private func largeContent(_ record: WidgetLastGood) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("RunWear")
                    .font(.greatVibes(size: 26))
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
                Text(WidgetFreshness.updatedLabel(record, now: entry.date))
                    .font(.caption.weight(.medium))
                    .foregroundColor(AppTheme.inkSecondary)
            }

            Text(record.locationName)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)

            HStack(spacing: 8) {
                Text(record.formatTemperature(fahrenheit: record.feelsLikeF))
                    .font(.system(size: 46, weight: .bold))
                    .foregroundColor(AppTheme.inkPrimary)
                Image(systemName: record.conditionIcon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundColor(AppTheme.pinePayload)
                Text(record.conditionText)
                    .font(.headline)
                    .foregroundColor(AppTheme.inkPrimary)
            }

            Text(record.supportingLine)
                .font(.caption)
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(1)

            Divider()

            VStack(alignment: .leading, spacing: 3) {
                Text(record.topBaseName)
                    .font(.body.weight(.semibold))
                    .foregroundColor(AppTheme.inkPrimary)
                ForEach(record.layerNames, id: \.self) { name in
                    Text(name)
                        .font(.body)
                        .foregroundColor(AppTheme.inkPrimary)
                }
                Text(record.bottomName)
                    .font(.body)
                    .foregroundColor(AppTheme.inkPrimary)
                ForEach(record.secondaryNames, id: \.self) { name in
                    Text(name)
                        .font(.body)
                        .foregroundColor(AppTheme.inkSecondary)
                }
            }

            if let laterTodayLine = record.laterTodayLine {
                Text(laterTodayLine)
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(AppTheme.pinePayload)
                    .lineLimit(2)
            }

            Spacer(minLength: 2)

            Text("Humidity \(record.humidityPercent)% · UV \(Int(round(record.uvIndex)))")
                .font(.caption2)
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    // The never-succeeded card (authored categories, Android's
    // model): shown ONLY when no last-good record exists.
    private var errorContent: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("RunWear")
                .font(.greatVibes(size: 26))
                .foregroundColor(AppTheme.inkPrimary)
            Text(entry.errorCategory?.headline ?? WidgetErrorCategory.weather.headline)
                .font(.headline)
                .foregroundColor(AppTheme.inkPrimary)
            Text(entry.errorCategory?.reason ?? WidgetErrorCategory.neverRunReason)
                .font(.subheadline)
                .foregroundColor(AppTheme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

// MARK: - Widget definition

struct RunWearWidget: Widget {
    static let kind = "RunWearWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: RunWearWidgetProvider()) { entry in
            RunWearWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Running Outfit")
        .description("Your outfit for the weather right now, from your last RunWear recommendation.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct RunWearWidgetBundle: WidgetBundle {
    var body: some Widget {
        RunWearWidget()
    }
}
