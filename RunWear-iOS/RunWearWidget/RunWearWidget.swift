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

// MARK: - Entry view
//
// The entry type and its per-family entry view live in
// RunWearWidgetViews.swift (GEN-6, RW-67) — split out so the
// view can be dual-membered into RunWearTests and rendered by
// the gate; this file's @main bundle cannot be.

// MARK: - Widget definition

struct RunWearWidget: Widget {
    static let kind = "RunWearWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: RunWearWidgetProvider()) { entry in
            RunWearWidgetEntryView(entry: entry)
        }
        .configurationDisplayName(AppStrings.widgetName)
        .description(AppStrings.widgetDescription)
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct RunWearWidgetBundle: WidgetBundle {
    var body: some Widget {
        RunWearWidget()
    }
}
