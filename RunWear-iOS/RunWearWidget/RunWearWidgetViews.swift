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

    /// GEN-6 (RW-67) test seam: the widget family is an
    /// environment value the system sets in a real widget, but
    /// a render harness cannot inject it (the preview-context
    /// modifier does not reach ImageRenderer — the first RW-67
    /// capture's "small"/"large" frames silently rendered the
    /// renderer's default MEDIUM composition). Render tests
    /// pass the family explicitly; production constructs the
    /// view without it and reads the environment, unchanged.
    var familyOverride: WidgetFamily? = nil

    private var resolvedFamily: WidgetFamily { familyOverride ?? family }

    var body: some View {
        Group {
            if let record = entry.record {
                switch resolvedFamily {
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
    //
    // GEN-6 (RW-67): as shipped this stack did not fit its
    // 158 pt canvas — measured fitting height 177 pt at
    // DEFAULT type with a provider-shaped record (one layer,
    // two secondary garments), 439.5 pt at accessibility5: the
    // bottom lines were cut on device. The composition fix
    // keeps every element and every garment name: the outfit
    // runs as TWO lines instead of four — base + layers on
    // one, bottom + the honest "+N more" count on the other,
    // per-run styling preserved (base semibold, layers
    // regular, the count caption-sized and secondary) — the
    // supporting line shrinks to fit one line rather than
    // wrapping to two or three, and the stack's type growth
    // is capped at accessibility1 (as on medium and the home
    // hero cluster). The temperature is the fixed 32 pt hero,
    // never scaled, never truncated.
    private func smallContent(_ record: WidgetLastGood) -> some View {
        VStack(alignment: .leading, spacing: 3) {
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

            smallOutfitLineOne(record)
                .font(.subheadline)
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)
            smallOutfitLineTwo(record)
                .font(.subheadline)
                .foregroundColor(AppTheme.inkPrimary)
                .lineLimit(1)

            Spacer(minLength: 2)

            Text(record.supportingLine)
                .font(.caption2)
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    /// Small outfit line one: the base (semibold run) with the
    /// layers appended as a regular run — the same joined
    /// idiom the layers already used among themselves.
    private func smallOutfitLineOne(_ record: WidgetLastGood) -> Text {
        var line = Text(record.topBaseName).fontWeight(.semibold)
        if !record.layerNames.isEmpty {
            line = line + Text(" · " + record.layerNames.joined(separator: " · "))
        }
        return line
    }

    /// Small outfit line two: the bottom with the secondary
    /// count as a caption-sized secondary run — counted
    /// honestly, exactly as the standalone line counted it.
    private func smallOutfitLineTwo(_ record: WidgetLastGood) -> Text {
        var line = Text(record.bottomName)
        if !record.secondaryNames.isEmpty {
            line = line + Text(" · +\(record.secondaryNames.count) more")
                .font(.caption)
                .foregroundColor(AppTheme.inkSecondary)
        }
        return line
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
        // GEN-6 (RW-67): at accessibility Dynamic Type sizes
        // the medium composition's semantic text (five outfit
        // lines, place, supporting line) grows until the stack
        // needs ~307 pt in a 170 pt canvas — the bottom lines
        // are cut and the outfit names ellipsis-truncate
        // (before-render: RW-67-widget-medium-115F-largest).
        // The composition is unchanged; its type growth is
        // capped at accessibility1, the same cap the home hero
        // cluster carries. The temperature itself is a fixed
        // 38 pt and is never scaled or truncated.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    // LARGE — the brand strip with the rendered "Updated …"
    // label, the place, the hero, the full supporting line,
    // every garment on its own line, the later-today line, and
    // the humidity/UV truth the smaller sizes have no room to
    // carry truthfully.
    private func largeContent(_ record: WidgetLastGood) -> some View {
        VStack(alignment: .leading, spacing: 2) {
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

            VStack(alignment: .leading, spacing: 2) {
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
        // GEN-6 (RW-67): at accessibility Dynamic Type sizes
        // the large composition measured a 948.5 pt fitting
        // height in its 382 pt canvas (the .body outfit lines
        // carry no line limit, so at accessibility sizes each
        // name wraps and the stack runs away). An accessibility1
        // cap brought that to 457 pt — still over: the large
        // composition carries the most semantic text of the
        // three sizes, so its cap sits one step tighter, at
        // xLarge, with the stack spacings tightened to match.
        // Composition and the fixed 46 pt temperature are
        // unchanged.
        .dynamicTypeSize(...DynamicTypeSize.xLarge)
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
