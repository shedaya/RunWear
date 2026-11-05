import SwiftUI

/// GEN-4 day chart rendering (Sol's day-chart wave, Nov 4,
/// 2026): one comfort-adjusted feels-like curve over
/// prescription-bracket bands, the day's NOW marker, a dot at
/// the selected hour, and BARE ticks where the outfit changes.
/// Numbers are rationed: the caller renders the day's low/high
/// and the selected hour's readout as text; the chart itself
/// carries no per-hour numerals.
///
/// The same view serves both surfaces: the home glance band
/// (non-interactive) and the Choose-a-Time sheet (interactive —
/// scrub anywhere on the chart's full height, snap hour to
/// hour, tap jumps to the nearest hour). Gesture safety: the
/// sheet that hosts the interactive form has NO scroll consumer
/// (its content is fixed — pinned by SheetDisciplineTests), so
/// the horizontal scrub has no scroll gesture to fight; the
/// scrub is confined to this view's hit area, and the sheet's
/// vertical swipe-dismiss remains the system's — dismissing
/// that way is a Cancel, because the draft never writes home
/// state until Confirm.
struct DayChartView: View {
    let model: DayChartModel
    /// The hour the dot marks: the selection on home, the draft
    /// in the sheet. Snapped to the nearest forecast hour for
    /// placement when it falls outside the series.
    let selectedHour: Int
    /// The viewed location's current hour — only when the
    /// selected day is the location's today (DayChartNowMarker).
    let nowHour: Int?
    var interactive: Bool = false
    var showsAxisLabels: Bool = false
    /// GEN-5 (RW-28 item 14): name the selected hour ON the
    /// chart's own axis, at the dot — the glance band turns this
    /// on so the hour is never carried by a separate row under
    /// the band (the gen-4 census B-8 defect).
    var showsSelectedHourOnAxis: Bool = false
    var onSelectHour: ((Int) -> Void)? = nil

    var body: some View {
        VStack(spacing: 4) {
            chartArea
            if showsAxisLabels {
                axisLabels
            }
        }
    }

    private var placedSelectedHour: Int {
        model.nearestSelectableHour(toHour: Double(selectedHour))
    }

    private var chartArea: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            ZStack(alignment: .topLeading) {
                bands(width: width, height: height)
                curve(width: width, height: height)
                nowMarker(width: width, height: height)
                changeTicks(width: width, height: height)
                selectedDot(width: width, height: height)
                if showsSelectedHourOnAxis {
                    selectedHourAxisLabel(width: width, height: height)
                }
            }
            .contentShape(Rectangle())
            .modifier(ScrubModifier(
                interactive: interactive,
                onScrub: { x in
                    let fractional = DayChartGeometry.fractionalHour(
                        atX: x, model: model, width: width
                    )
                    let snapped = model.nearestSelectableHour(toHour: fractional)
                    if snapped != placedSelectedHour {
                        onSelectHour?(snapped)
                    }
                }
            ))
        }
        .modifier(ChartAccessibility(
            interactive: interactive,
            label: accessibilitySummary,
            value: accessibilityValue,
            onStep: { delta in
                onSelectHour?(model.steppedHour(from: placedSelectedHour, by: delta))
            }
        ))
    }

    // MARK: - Layers

    @ViewBuilder
    private func bands(width: CGFloat, height: CGFloat) -> some View {
        let span = max(1, model.lastHour - model.firstHour)
        let halfStep = width / CGFloat(span) / 2
        ForEach(Array(model.bands.enumerated()), id: \.offset) { _, band in
            let startX = max(0, DayChartGeometry.x(forHour: band.startHour, model: model, width: width) - halfStep)
            let endX = min(width, DayChartGeometry.x(forHour: band.endHour, model: model, width: width) + halfStep)
            Rectangle()
                .fill(AppTheme.temperatureColor(fahrenheit: representativeFeelsLike(of: band)).opacity(0.20))
                .frame(width: max(0, endX - startX), height: height)
                .offset(x: startX)
        }
    }

    private func representativeFeelsLike(of band: DayChartBand) -> Double {
        let entries = model.hours.filter { $0.hour >= band.startHour && $0.hour <= band.endHour }
        guard !entries.isEmpty else { return model.low.feelsLikeF }
        return entries.map(\.feelsLikeF).reduce(0, +) / Double(entries.count)
    }

    @ViewBuilder
    private func curve(width: CGFloat, height: CGFloat) -> some View {
        Path { path in
            for (index, entry) in model.hours.enumerated() {
                let point = CGPoint(
                    x: DayChartGeometry.x(forHour: entry.hour, model: model, width: width),
                    y: DayChartGeometry.y(forFeelsLike: entry.feelsLikeF, model: model, height: height)
                )
                if index == 0 {
                    path.move(to: point)
                } else {
                    path.addLine(to: point)
                }
            }
        }
        .stroke(AppTheme.inkPrimary, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    @ViewBuilder
    private func nowMarker(width: CGFloat, height: CGFloat) -> some View {
        if let nowHour {
            let x = DayChartGeometry.x(forHour: nowHour, model: model, width: width)
            Path { path in
                path.move(to: CGPoint(x: x, y: 10))
                path.addLine(to: CGPoint(x: x, y: height))
            }
            .stroke(AppTheme.primaryColor, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            Text("NOW")
                .font(.system(size: 8.5, weight: .bold))
                .tracking(0.5)
                .foregroundColor(AppTheme.primaryColor)
                .position(x: min(max(x, 14), width - 14), y: 5)
        }
    }

    @ViewBuilder
    private func changeTicks(width: CGFloat, height: CGFloat) -> some View {
        ForEach(model.outfitChangeHours, id: \.self) { hour in
            let x = DayChartGeometry.x(forHour: hour, model: model, width: width)
            Path { path in
                path.move(to: CGPoint(x: x, y: height))
                path.addLine(to: CGPoint(x: x - 4, y: height - 6))
                path.addLine(to: CGPoint(x: x + 4, y: height - 6))
                path.closeSubpath()
            }
            .fill(AppTheme.inkSecondary)
        }
    }

    @ViewBuilder
    private func selectedDot(width: CGFloat, height: CGFloat) -> some View {
        if let entry = model.entry(forHour: placedSelectedHour) {
            Circle()
                .fill(AppTheme.primaryColor)
                .frame(width: 11, height: 11)
                .overlay(Circle().stroke(Color.white, lineWidth: 2))
                .position(
                    x: DayChartGeometry.x(forHour: entry.hour, model: model, width: width),
                    y: DayChartGeometry.y(forFeelsLike: entry.feelsLikeF, model: model, height: height)
                )
        }
    }

    /// The selected hour's name on the axis itself: bold pine,
    /// centred on the dot's x (clamped into the chart), sitting
    /// in the axis zone at the chart's foot.
    @ViewBuilder
    private func selectedHourAxisLabel(width: CGFloat, height: CGFloat) -> some View {
        if let entry = model.entry(forHour: placedSelectedHour) {
            Text(DayChartFormat.hourLabel(entry.hour))
                .font(.system(size: 10.5, weight: .bold))
                .foregroundColor(AppTheme.pinePayload)
                .fixedSize()
                .position(
                    x: min(max(DayChartGeometry.x(forHour: entry.hour, model: model, width: width), 18), width - 18),
                    y: height - 7
                )
        }
    }

    private var axisLabels: some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack(alignment: .topLeading) {
                ForEach(axisLabelHours, id: \.self) { hour in
                    Text(DayChartFormat.hourLabel(hour))
                        .font(.system(size: 10.5))
                        .foregroundColor(AppTheme.inkSecondary)
                        .fixedSize()
                        .position(
                            x: min(max(DayChartGeometry.x(forHour: hour, model: model, width: width), 16), width - 16),
                            y: 6
                        )
                }
            }
        }
        .frame(height: 13)
    }

    /// Sparse axis anchors (the mock's set), restricted to the
    /// hours the forecast actually spans.
    private var axisLabelHours: [Int] {
        [0, 6, 12, 18, 23].filter { $0 >= model.firstHour && $0 <= model.lastHour }
    }

    // MARK: - Accessibility

    private var accessibilitySummary: String {
        "Day chart. Feels like from \(Int(model.low.feelsLikeF.rounded())) degrees "
            + "at \(DayChartFormat.hourLabel(model.low.hour)) to "
            + "\(Int(model.high.feelsLikeF.rounded())) degrees at "
            + "\(DayChartFormat.hourLabel(model.high.hour))."
    }

    private var accessibilityValue: String {
        guard let entry = model.entry(forHour: placedSelectedHour) else { return "" }
        return DayChartFormat.readout(
            hour: entry.hour,
            formattedFeelsLike: "\(Int(entry.feelsLikeF.rounded()))°"
        )
    }
}

/// The scrub gesture, attached only in the interactive form:
/// a zero-minimum-distance drag, so a tap (a drag that never
/// moves) jumps to the nearest hour and a drag scrubs — one
/// gesture, both mechanics from the governing demo.
private struct ScrubModifier: ViewModifier {
    let interactive: Bool
    let onScrub: (CGFloat) -> Void

    func body(content: Content) -> some View {
        if interactive {
            content.gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in onScrub(value.location.x) }
                    .onEnded { value in onScrub(value.location.x) }
            )
        } else {
            content
        }
    }
}

/// VoiceOver form: in the sheet the chart is ONE adjustable
/// control stepping by hour, and its value is the readout as
/// real text. On home (non-interactive) it is a plain labelled
/// element summarising the day.
private struct ChartAccessibility: ViewModifier {
    let interactive: Bool
    let label: String
    let value: String
    let onStep: (Int) -> Void

    func body(content: Content) -> some View {
        if interactive {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityValue(value)
                .accessibilityAdjustableAction { direction in
                    onStep(direction == .increment ? 1 : -1)
                }
        } else {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
        }
    }
}

/// The home glance band (GEN-4 §1): the chart below the outfit
/// list, the day's low/high at its ends, and beneath it the axis
/// row naming the selected hour with the "Change ›" button
/// beside it — there is no separate Change-time row.
struct DayChartBandView: View {
    let model: DayChartModel
    let selectedHour: Int
    let nowHour: Int?
    let unit: TemperatureUnit
    var onChange: () -> Void = {}

    var body: some View {
        VStack(spacing: 8) {
            DayChartView(
                model: model,
                selectedHour: selectedHour,
                nowHour: nowHour,
                interactive: false,
                showsSelectedHourOnAxis: true
            )
            .frame(height: 62)

            // GEN-5 (RW-28 item 14): ONE row under the chart —
            // the day's low/high at the ends and "Change ›"
            // beside them. The selected hour itself is named on
            // the chart's own axis above, not in this row.
            HStack(alignment: .center) {
                Text("\(unit.formatFromFahrenheit(model.low.feelsLikeF)) low")
                Spacer()
                Button(action: onChange) {
                    HStack(spacing: 3) {
                        Text("Change")
                        Text("›").fontWeight(.bold)
                    }
                    .font(.system(size: 15.5, weight: .bold))
                    .foregroundColor(AppTheme.pinePayload)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Change the hour")
                Spacer()
                Text("\(unit.formatFromFahrenheit(model.high.feelsLikeF)) high")
            }
            .font(.system(size: 12.5))
            .foregroundColor(AppTheme.inkSecondary)
        }
        .padding(13)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppTheme.cardFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(AppTheme.cardStroke, lineWidth: 1)
        )
    }
}
