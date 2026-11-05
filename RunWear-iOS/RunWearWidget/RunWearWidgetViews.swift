import WidgetKit
import SwiftUI

// MARK: - RunWear widget entry + entry view (GEN-6, RW-67)
//
// Split from RunWearWidget.swift for one reason: the widget
// definition file carries the extension's @main bundle, which
// cannot be dual-membered into the test target — so the entry
// view could never be rendered by the gate. The entry and its
// view live here, dual-membered into the widget extension AND
// RunWearTests, so the RW-67 render tests compose the exact
// production view. The compositions are unchanged from the
// gen-5 widget: per-family layouts, the feels-like hero, the
// freshness label derived at render time, the cream system.

struct RunWearWidgetEntry: TimelineEntry {
    let date: Date
    let record: WidgetLastGood?
    let errorCategory: WidgetErrorCategory?
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
