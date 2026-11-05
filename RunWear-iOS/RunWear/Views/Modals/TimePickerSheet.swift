import SwiftUI

/// GEN-4: "Choose a Time" — the day chart replaces the hour
/// grid (the tiles are retired; the grid's per-hour feels-like
/// values survive as the curve's data and the readout's text).
/// Same sheet, same entry points (the pill's time segment, the
/// glance band's Change button, the headline moment).
///
/// DRAFT semantics (governing mechanics, time-picker demo):
/// opening snapshots the selection into a TimePickerDraft;
/// scrub / tap / steppers move the draft ONLY — home does not
/// re-solve behind the sheet. Confirm commits (the same
/// downstream re-solve as a pick today) and dismisses; Cancel
/// restores the pre-open selection exactly and dismisses —
/// exactly, because the selection was never written, so Cancel
/// and swipe-dismissal alike have nothing to undo.
///
/// Gesture safety: this sheet's content is fixed (no scroll
/// consumer — the discipline SheetDisciplineTests pins for the
/// medium-detent pickers), so the chart's horizontal scrub has
/// no scroll gesture to fight; the scrub lives entirely inside
/// the chart's full-height hit area.
struct TimePickerSheet: View {
    /// The selected day's chart; nil when the day carries no
    /// forecast hours (beyond the horizon).
    let chart: DayChartModel?
    /// "{Date} · {Place} · Feels-like by hour" context line.
    let dateLabel: String
    let placeName: String
    let unit: TemperatureUnit
    @Binding var isPresented: Bool
    /// Confirm's commit — the host applies it through the view
    /// model's one apply path.
    var onConfirm: (TimePickerDraft.Commit) -> Void = { _ in }

    @State private var draft: TimePickerDraft

    init(
        chart: DayChartModel?,
        dateLabel: String,
        placeName: String,
        unit: TemperatureUnit,
        initialHour: Int,
        openedInNowState: Bool,
        nowHour: Int?,
        isPresented: Binding<Bool>,
        onConfirm: @escaping (TimePickerDraft.Commit) -> Void = { _ in }
    ) {
        self.chart = chart
        self.dateLabel = dateLabel
        self.placeName = placeName
        self.unit = unit
        self._isPresented = isPresented
        self.onConfirm = onConfirm
        // Default draft: the currently selected hour; in the NOW
        // state on the location's today the host passes the
        // location's current hour as the initial hour, so the
        // handle starts on the NOW marker.
        _draft = State(initialValue: TimePickerDraft(
            originalHour: initialHour,
            openedInNowState: openedInNowState,
            nowHour: nowHour,
            selectableHours: chart?.selectableHours ?? []
        ))
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("Choose a Time")
                .font(.appSheetTitle)
                .foregroundColor(AppTheme.inkPrimary)

            Text("\(dateLabel) · \(placeName) · Feels-like by hour")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .lineLimit(1)

            if let chart {
                readoutRow(chart: chart)
                DayChartView(
                    model: chart,
                    selectedHour: draft.draftHour,
                    nowHour: draft.nowHour,
                    interactive: true,
                    showsAxisLabels: true,
                    onSelectHour: { draft.move(to: $0) }
                )
                .frame(height: 168)

                // GEN-6 (DA-21): a PARTIAL day's truncated curve
                // names its beyond-forecast region with the same
                // words the date cells use — the wholly-empty day
                // is named by the branch below, and a full day
                // names nothing.
                if let beyondCaption = DayChartBeyondForecast.caption(for: chart) {
                    Text(beyondCaption)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                // GEN-5 (RW-29 FA-57): the settled instruction
                // sentence from the day-chart research mock —
                // build 25's paraphrase omitted both facts it
                // carries (every hour available; the handle
                // snaps hour to hour).
                Text("Every hour can be chosen — the handle snaps hour to hour.")
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)

                HStack {
                    Button("Cancel") { dismiss() }
                        .font(.appSecondary.weight(.semibold))
                        .foregroundColor(AppTheme.inkSecondary)
                    Spacer()
                    Button("Confirm") {
                        onConfirm(draft.commit())
                        dismiss()
                    }
                    .font(.appSecondary.weight(.bold))
                    .foregroundColor(AppTheme.pinePayload)
                }
            } else {
                // Horizon honesty: a day the forecast does not
                // reach has no hours to offer — named, never
                // dressed over with invented hours.
                Text("Forecast isn't available for this day yet.")
                    .font(.appSecondary)
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)

                Button("Cancel") { dismiss() }
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.inkSecondary)
            }

            Spacer().frame(height: 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppTheme.background.ignoresSafeArea())
        // One selection tick per hour crossed: the trigger is the
        // SNAPPED draft hour, so it fires exactly once per hour
        // the handle crosses, never per drag event.
        .sensoryFeedback(.selection, trigger: draft.draftHour)
    }

    // MARK: - Readout + steppers

    /// The live readout flanked by the −/+ steppers (≥44pt):
    /// hour + feels-like ONLY, with the drafted hour's outfit
    /// preview line beneath it. The readout is real text, so
    /// VoiceOver hears exactly what the eye sees.
    private func readoutRow(chart: DayChartModel) -> some View {
        let entry = chart.entry(forHour: draft.draftHour)
        return HStack(spacing: 10) {
            // GEN-5 (RW-29 FA-39): steppers disable at the
            // selectable range's ends.
            stepper(systemImage: "minus", label: "Previous hour", isEnabled: draft.canStep(by: -1)) {
                draft.step(by: -1)
            }

            VStack(spacing: 3) {
                Text(entry.map {
                    DayChartFormat.readout(
                        hour: $0.hour,
                        formattedFeelsLike: unit.formatFromFahrenheit($0.feelsLikeF)
                    )
                } ?? "")
                .font(.system(size: 15.5, weight: .bold))
                .foregroundColor(AppTheme.inkPrimary)
                .monospacedDigit()

                if let entry, !entry.outfitItemNames.isEmpty {
                    Text(entry.outfitItemNames.joined(separator: " · "))
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)

            stepper(systemImage: "plus", label: "Next hour", isEnabled: draft.canStep(by: 1)) {
                draft.step(by: 1)
            }
        }
    }

    private func stepper(systemImage: String, label: String, isEnabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(isEnabled ? AppTheme.inkPrimary : AppTheme.inkMuted)
                .frame(width: 44, height: 44)
                .background(Circle().stroke(AppTheme.cardStroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(label)
    }

    private func dismiss() {
        // State-driven dismissal (RW-6.12): the system animates
        // the sheet away; no hand-rolled transition wrapper.
        isPresented = false
    }
}

// MARK: - Preview

#Preview {
    TimePickerSheet(
        chart: DayChartModel.build(points: (0..<24).map { hour in
            DayChartHour(
                hour: hour,
                feelsLikeF: 42 + 11 * sin(Double(hour - 6) / 24 * 2 * Double.pi) + Double(hour) / 4,
                bracket: .cool,
                outfitItemNames: ["Short-Sleeve Running Shirt", "5-Inch Running Shorts", "Cap"]
            )
        }),
        dateLabel: "Today",
        placeName: "Kansas City",
        unit: .fahrenheit,
        initialHour: 16,
        openedInNowState: true,
        nowHour: 16,
        isPresented: .constant(true)
    )
}
