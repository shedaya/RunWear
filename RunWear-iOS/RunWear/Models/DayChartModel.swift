import Foundation

/// GEN-4 day chart (Sol's day-chart wave, Nov 4, 2026): the pure,
/// unit-testable model behind the home glance band and the
/// Choose-a-Time sheet's chart. It carries exactly what the old
/// hour grid carried — one comfort-adjusted feels-like per hour
/// (the RW-23 frame the prescription itself reads), the
/// prescription bracket per hour, and the outfit the engine
/// prescribes at that hour — re-expressed as a curve over
/// bracket bands.
///
/// Horizon honesty: the model contains ONLY the hours the
/// forecast actually carries for the day. Hours beyond the
/// forecast horizon are absent — never invented, never padded —
/// so the curve simply ends at the last forecast hour, and
/// past-horizon hours are not selectable.
struct DayChartHour: Equatable {
    /// Location-local wall hour, 0...23.
    let hour: Int
    /// Comfort-adjusted feels-like in Fahrenheit (RW-23 frame).
    let feelsLikeF: Double
    /// The prescription bracket at this hour.
    let bracket: TemperatureBracket
    /// The prescribed outfit's item names at this hour, in layer
    /// order (the sheet's preview line renders these).
    let outfitItemNames: [String]
}

/// One contiguous stretch of hours sharing a prescription
/// bracket — the shaded bands the curve rides over.
struct DayChartBand: Equatable {
    let bracket: TemperatureBracket
    /// First hour of the stretch (present in the series).
    let startHour: Int
    /// Last hour of the stretch (present in the series,
    /// inclusive).
    let endHour: Int
}

struct DayChartModel: Equatable {
    /// The day's forecast hours, ascending. Never empty (the
    /// builder returns nil for an empty series).
    let hours: [DayChartHour]
    /// Bracket bands, in hour order.
    let bands: [DayChartBand]
    /// Hours at which the prescription changes versus the
    /// previous forecast hour — the chart's outfit-change ticks.
    /// A change is a bracket crossing OR a change in the
    /// prescribed item set (a rain shell or reflective gear can
    /// arrive inside one bracket). Ticks render BARE: no prose.
    let outfitChangeHours: [Int]
    /// The day's coldest forecast hour.
    let low: DayChartHour
    /// The day's warmest forecast hour.
    let high: DayChartHour

    var firstHour: Int { hours.first!.hour }
    var lastHour: Int { hours.last!.hour }
    var selectableHours: [Int] { hours.map(\.hour) }

    func entry(forHour hour: Int) -> DayChartHour? {
        hours.first { $0.hour == hour }
    }

    /// The selectable hour nearest to a (possibly fractional)
    /// hour value — the scrub/tap snap. Values outside the
    /// forecast range clamp to the first/last forecast hour, so
    /// a scrub can never land past the horizon.
    func nearestSelectableHour(toHour value: Double) -> Int {
        var best = hours[0]
        var bestDistance = Double.greatestFiniteMagnitude
        for entry in hours {
            let distance = abs(Double(entry.hour) - value)
            if distance < bestDistance {
                bestDistance = distance
                best = entry
            }
        }
        return best.hour
    }

    /// The selectable hour `delta` steps from `hour` among the
    /// forecast hours (the sheet's −/+ steppers), clamped at the
    /// ends of the forecast.
    func steppedHour(from hour: Int, by delta: Int) -> Int {
        let list = selectableHours
        guard let index = list.firstIndex(of: hour) else {
            return nearestSelectableHour(toHour: Double(hour))
        }
        let target = min(max(index + delta, 0), list.count - 1)
        return list[target]
    }

    /// Builds the model from raw per-hour points: sorted by hour,
    /// de-duplicated (the first point for an hour wins), nil when
    /// the day carries no forecast hours at all.
    static func build(points: [DayChartHour]) -> DayChartModel? {
        let sorted = points.sorted { $0.hour < $1.hour }
        var unique: [DayChartHour] = []
        for point in sorted where unique.last?.hour != point.hour {
            unique.append(point)
        }
        guard let first = unique.first else { return nil }

        var bands: [DayChartBand] = []
        var changes: [Int] = []
        var bandStart = first
        var previous = first
        for entry in unique.dropFirst() {
            if entry.bracket != previous.bracket
                || entry.outfitItemNames != previous.outfitItemNames {
                changes.append(entry.hour)
            }
            if entry.bracket != bandStart.bracket {
                bands.append(DayChartBand(
                    bracket: bandStart.bracket,
                    startHour: bandStart.hour,
                    endHour: previous.hour
                ))
                bandStart = entry
            }
            previous = entry
        }
        bands.append(DayChartBand(
            bracket: bandStart.bracket,
            startHour: bandStart.hour,
            endHour: previous.hour
        ))

        let low = unique.min(by: { $0.feelsLikeF < $1.feelsLikeF }) ?? first
        let high = unique.max(by: { $0.feelsLikeF < $1.feelsLikeF }) ?? first
        return DayChartModel(
            hours: unique,
            bands: bands,
            outfitChangeHours: changes,
            low: low,
            high: high
        )
    }
}

// MARK: - NOW marker rule

/// The band's NOW marker: it exists only when the selected day is
/// the viewed location's today AND the forecast carries the
/// location's current hour. Any other day — or a current hour
/// past the forecast horizon — gets no marker, never a borrowed
/// one.
enum DayChartNowMarker {
    static func hour(
        selectedDayIsLocationToday: Bool,
        locationNowHour: Int,
        model: DayChartModel
    ) -> Int? {
        guard selectedDayIsLocationToday else { return nil }
        guard model.entry(forHour: locationNowHour) != nil else { return nil }
        return locationNowHour
    }
}

// MARK: - Beyond-forecast naming (GEN-6, DA-21)

/// Design-spec §6 / frame 42: territory past the forecast is
/// NAMED as beyond the forecast, never left as silent empty
/// space. The date cells already carry the name ("Beyond
/// forecast", DatePickerSheet); a PARTIAL day in the time picker
/// — a day whose forecast covers only part of the 24-hour scale —
/// carries the same name for its truncated region. A full day
/// (forecast covering hours 0…23) has no beyond-forecast region
/// and names nothing.
enum DayChartBeyondForecast {
    static let label = "Beyond forecast"

    /// The caption for a chart, or nil when the day is fully
    /// covered by the forecast.
    static func caption(for model: DayChartModel) -> String? {
        let coversFullDay = model.firstHour <= DayChartGeometry.dayFirstHour
            && model.lastHour >= DayChartGeometry.dayLastHour
        return coversFullDay ? nil : label
    }
}

// MARK: - Chart geometry (pure, so the mapping is pinnable)

/// The chart's coordinate mapping, shared by the glance band and
/// the sheet chart: x is linear in hour across the forecast
/// range, y is linear in feels-like between the day's low and
/// high (padded, so the curve never touches the frame's edges).
enum DayChartGeometry {
    /// GEN-5 (RW-29 FA-29): the chart's horizontal scale is the
    /// fixed 24-hour day (hours 0…23 across the full width) on
    /// EVERY day — build 25 mapped across the forecast's own
    /// span, so on a partial day (a forecast that starts at
    /// 14:00) nine hours stretched across the full width and
    /// every marker sat at a different scale than a full day's.
    /// A partial day's curve now occupies its true slice of the
    /// day and ends at the forecast horizon; the region outside
    /// the forecast stays empty (the sheets name it "Beyond
    /// forecast"). Full-day geometry is unchanged.
    static let dayFirstHour = 0
    static let dayLastHour = 23

    static func x(forHour hour: Int, model: DayChartModel, width: CGFloat) -> CGFloat {
        let span = max(1, dayLastHour - dayFirstHour)
        let fraction = CGFloat(hour - dayFirstHour) / CGFloat(span)
        return min(max(fraction, 0), 1) * width
    }

    /// The fractional hour under a scrub position — the caller
    /// snaps it through `nearestSelectableHour`.
    static func fractionalHour(atX x: CGFloat, model: DayChartModel, width: CGFloat) -> Double {
        guard width > 0 else { return Double(dayFirstHour) }
        let span = Double(max(1, dayLastHour - dayFirstHour))
        let fraction = min(max(Double(x / width), 0), 1)
        return Double(dayFirstHour) + fraction * span
    }

    /// RW-85: the top annotation band reserved for the "NOW"
    /// label (the label occupies y 0–10; its dashed rule starts
    /// at y 10). The plot maps into the space BELOW this band, so
    /// the curve's warmest point — and the selected dot centred
    /// on it (11 pt + stroke) — can never strike the label. The
    /// pre-fix mapping put the warmest point at 9.7% of the
    /// height (≈11.6 pt at the accessibility height), inside the
    /// label's band whenever the selected hour, the now hour,
    /// and the warmest hour coincided.
    static let topAnnotationBand: CGFloat = 14

    static func y(forFeelsLike feelsLikeF: Double, model: DayChartModel, height: CGFloat) -> CGFloat {
        let low = model.low.feelsLikeF
        let high = model.high.feelsLikeF
        let range = high - low
        let plotHeight = max(0, height - topAnnotationBand)
        guard range > 0 else { return topAnnotationBand + plotHeight / 2 }
        // 12% headroom above the high and below the low.
        let paddedLow = low - range * 0.12
        let paddedHigh = high + range * 0.12
        let fraction = (feelsLikeF - paddedLow) / (paddedHigh - paddedLow)
        return topAnnotationBand + plotHeight * (1 - CGFloat(fraction))
    }
}

// MARK: - Hour formatting

/// The chart surfaces' hour strings, in the app's existing
/// on-the-hour format ("6 PM" for readouts, "6:00 PM" where the
/// home axis names the selected hour).
enum DayChartFormat {
    static func hourLabel(_ hour: Int) -> String {
        let suffix = hour >= 12 ? "PM" : "AM"
        let display = hour % 12 == 0 ? 12 : hour % 12
        return "\(display) \(suffix)"
    }

    static func hourMinuteLabel(_ hour: Int) -> String {
        let suffix = hour >= 12 ? "PM" : "AM"
        let display = hour % 12 == 0 ? 12 : hour % 12
        return "\(display):00 \(suffix)"
    }

    /// The sheet readout: hour + feels-like ONLY ("6 PM · 47°").
    /// The temperature arrives pre-formatted in the display unit.
    static func readout(hour: Int, formattedFeelsLike: String) -> String {
        "\(hourLabel(hour)) · \(formattedFeelsLike)"
    }
}

// MARK: - Choose-a-Time draft semantics

/// The Choose-a-Time sheet's draft state (GEN-4, governing
/// mechanics from the time-picker demo): opening the sheet
/// snapshots the current selection; scrubbing, tapping and the
/// steppers move ONLY this draft — home does not re-solve behind
/// the sheet. Confirm commits the draft (the same downstream
/// re-solve as a pick today); Cancel — including a swipe
/// dismissal, which never touches this value at all — restores
/// the pre-open selection exactly, because the selection itself
/// was never written.
///
/// The NOW interpretation (pinned by Gen4DayChartTests; the
/// Android lane implements the same rule): confirming while the
/// sheet was opened in the NOW state AND the handle never moved
/// off the NOW position KEEPS the NOW state. Any other confirm —
/// including scrubbing away and back onto the NOW hour — is an
/// explicit pick and exits NOW, matching the existing
/// explicit-pick semantics.
struct TimePickerDraft: Equatable {
    enum Commit: Equatable {
        case keepNow
        case explicitPick(hour: Int)
    }

    /// The selection as it was when the sheet opened.
    let originalHour: Int
    /// Whether home was in the NOW state when the sheet opened.
    let openedInNowState: Bool
    /// The viewed location's current hour when opened on its
    /// today in the NOW state; nil otherwise.
    let nowHour: Int?
    /// The day's selectable (forecast) hours.
    let selectableHours: [Int]

    private(set) var draftHour: Int
    /// Sticky: once the handle leaves the NOW position it has
    /// moved, even if it later returns to the same hour.
    private(set) var movedOffNow: Bool

    init(
        originalHour: Int,
        openedInNowState: Bool,
        nowHour: Int?,
        selectableHours: [Int]
    ) {
        self.originalHour = originalHour
        self.openedInNowState = openedInNowState
        self.nowHour = nowHour
        self.selectableHours = selectableHours
        if selectableHours.contains(originalHour) {
            self.draftHour = originalHour
        } else if let nearest = selectableHours.min(by: {
            abs($0 - originalHour) < abs($1 - originalHour)
        }) {
            self.draftHour = nearest
        } else {
            self.draftHour = originalHour
        }
        self.movedOffNow = false
    }

    /// Moves the draft to the selectable hour nearest `hour`
    /// (scrub snap / tap). Moving onto a non-NOW hour while
    /// opened in the NOW state marks the handle as moved.
    mutating func move(to hour: Int) {
        guard !selectableHours.isEmpty else { return }
        let snapped = selectableHours.min(by: {
            abs($0 - hour) < abs($1 - hour)
        }) ?? hour
        draftHour = snapped
        if openedInNowState, let nowHour, snapped != nowHour {
            movedOffNow = true
        }
    }

    /// The −/+ steppers: one selectable hour at a time, clamped
    /// at the forecast's ends.
    mutating func step(by delta: Int) {
        guard let index = selectableHours.firstIndex(of: draftHour) else { return }
        let target = min(max(index + delta, 0), selectableHours.count - 1)
        move(to: selectableHours[target])
    }

    /// GEN-5 (RW-29 FA-39): whether a step in `delta`'s
    /// direction would move at all — the sheet DISABLES the
    /// stepper at the first/last selectable hour instead of
    /// letting it sit live but inert (build 25 clamped the
    /// step silently, so the button looked tappable and did
    /// nothing).
    func canStep(by delta: Int) -> Bool {
        guard let index = selectableHours.firstIndex(of: draftHour) else { return false }
        let target = index + delta
        return target >= 0 && target < selectableHours.count
    }

    func commit() -> Commit {
        if openedInNowState, let nowHour, !movedOffNow, draftHour == nowHour {
            return .keepNow
        }
        return .explicitPick(hour: draftHour)
    }
}

// MARK: - Date-picker draft (GEN-6 RW-66)

/// The day picker's draft, the date-side mirror of
/// TimePickerDraft's semantics (design-spec §6 mechanics applied
/// to the frame-42 sibling sheet): opening snapshots the
/// selection; tapping days moves the DRAFT only — the confirmed
/// selection is written exactly once, by Confirm, through the
/// host's existing re-solve path. Cancel (or swipe-dismiss)
/// discards the draft, so the prior selection is restored
/// exactly because it was never written.
struct DatePickerDraft: Equatable {
    /// The selection as it was when the sheet opened.
    let originalDate: Date
    private(set) var draftDate: Date

    init(originalDate: Date) {
        self.originalDate = originalDate
        self.draftDate = originalDate
    }

    mutating func select(_ date: Date) {
        draftDate = date
    }

    /// True when the drafted day differs from the confirmed day.
    var isChanged: Bool {
        !Calendar.current.isDate(draftDate, inSameDayAs: originalDate)
    }

    /// The date Confirm writes to the selection.
    func commit() -> Date { draftDate }
}

// MARK: - Headline-line state

/// The headline line's way back to now (GEN-4 Concept 1): the
/// compact refresh + "Now" segment exists ONLY in the
/// changed-time state — exactly the state the date/time pill's
/// own Now segment already keys on — and tapping it performs the
/// existing reset-to-now. In the NOW state there is nothing to
/// come back from, so the segment is absent.
enum HeadlineNowReturn {
    static func isShown(isViewingNow: Bool) -> Bool {
        !isViewingNow
    }
}
