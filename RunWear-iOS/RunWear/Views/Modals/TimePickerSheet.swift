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
    /// GEN-6 (RW-66): the day picker's binding + confirm hook.
    /// The date segment of the context line is the door into the
    /// sibling "Choose a Day" sheet (frame 42) — this is the
    /// designed date entry: home's Concept 1 headline opens THIS
    /// sheet, and the day is pickable inside the same picker
    /// family. (Before RW-66 the day picker's only home wiring
    /// was the hero's onDateTapped, which never renders with the
    /// hero's top controls off — the date was unreachable from
    /// home, as Sol's build-26 walkthrough found.) When the date
    /// is confirmed, the host dismisses this sheet too: the new
    /// day is committed and home has re-solved to it.
    var dateSelection: Binding<Date>? = nil
    var onDateConfirmed: () -> Void = {}

    @State private var draft: TimePickerDraft
    @State private var showDateSheet = false

    /// GEN-6 VQ FIX (RW-73): the sheet's composition adapts to
    /// the Dynamic Type size in force — at accessibility sizes
    /// the chart yields some height and the action row stacks,
    /// so every settled string stays whole and Confirm stays
    /// fully visible and tappable at EVERY size (the gen-6 VQ
    /// render at the largest size ellipsised the title, the
    /// context line, the outfit line, the instruction, and
    /// Confirm itself).
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    init(
        chart: DayChartModel?,
        dateLabel: String,
        placeName: String,
        unit: TemperatureUnit,
        initialHour: Int,
        openedInNowState: Bool,
        nowHour: Int?,
        isPresented: Binding<Bool>,
        onConfirm: @escaping (TimePickerDraft.Commit) -> Void = { _ in },
        dateSelection: Binding<Date>? = nil,
        onDateConfirmed: @escaping () -> Void = {}
    ) {
        self.chart = chart
        self.dateLabel = dateLabel
        self.placeName = placeName
        self.unit = unit
        self._isPresented = isPresented
        self.onConfirm = onConfirm
        self.dateSelection = dateSelection
        self.onDateConfirmed = onDateConfirmed
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

    // RW-73 DETENT RULING (Sol, Nov 6, 2026 — delegated to Tank's
    // suggestion; BACKLOG RW-73): with the RW-73 composition fixes
    // the untruncated content at the largest accessibility text
    // sizes is taller than a phone screen. At those sizes — and
    // only those — this sheet's content may scroll internally, so
    // Confirm stays reachable and whole; at default and moderate
    // sizes the settled no-scroll sheet discipline stands and the
    // content is the fixed stack it always was. The detents are
    // unchanged ([.medium, .large] at both hosts, and at the
    // nested day sheet below).
    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                ScrollView {
                    sheetContent
                }
            } else {
                sheetContent
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        // One selection tick per hour crossed: the trigger is the
        // SNAPPED draft hour, so it fires exactly once per hour
        // the handle crosses, never per drag event.
        .sensoryFeedback(.selection, trigger: draft.draftHour)
        // GEN-6 (RW-66): the sibling day sheet, presented over
        // this one from the context line's date door.
        .sheet(isPresented: $showDateSheet) {
            if let dateSelection {
                DatePickerSheet(
                    selectedDate: dateSelection,
                    isPresented: $showDateSheet,
                    onConfirm: onDateConfirmed
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
        }
    }

    private var sheetContent: some View {
        VStack(spacing: dynamicTypeSize.isAccessibilitySize ? 12 : 16) {
            Text("Choose a Time")
                .font(.appSheetTitle)
                .foregroundColor(AppTheme.inkPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            contextLine

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
                // GEN-6 VQ FIX (RW-73): at accessibility sizes
                // the chart yields height to the type (the
                // readout carries the hour's data as text; the
                // chart remains the scrub surface).
                .frame(height: dynamicTypeSize.isAccessibilitySize ? 120 : 168)

                // GEN-6 (DA-21): a PARTIAL day's truncated curve
                // names its beyond-forecast region with the same
                // words the date cells use — the wholly-empty day
                // is named by the branch below, and a full day
                // names nothing.
                if let beyondCaption = DayChartBeyondForecast.caption(for: chart) {
                    Text(beyondCaption)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                // GEN-5 (RW-29 FA-57): the settled instruction
                // sentence from the day-chart research mock —
                // build 25's paraphrase omitted both facts it
                // carries (every hour available; the handle
                // snaps hour to hour).
                Text("Every hour can be chosen — the handle snaps hour to hour.")
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                actionRow
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
    }

    /// The context line — "{Date} · {Place} · Feels-like by
    /// hour" — with the date segment as the day picker's door
    /// (GEN-6 RW-66): pine, bold, chevron, the headline moment's
    /// own treatment (Concept 1), because it is the same kind of
    /// entry. Without a date binding (previews) the line is the
    /// plain settled text it always was.
    @ViewBuilder
    private var contextLine: some View {
        if dateSelection != nil {
            // GEN-6 VQ FIX (RW-73): the one-line form is kept
            // wherever it fits whole; where the type size makes
            // it exceed the sheet's width, the line re-flows —
            // the date door takes its own line and the remainder
            // wraps beneath it by whole words, every character
            // of the settled line preserved (the gen-6 VQ render
            // ellipsised the one-line form to "Today › · New…"
            // at the largest size, and the old `.lineLimit(1)`
            // guaranteed that failure at any size where the line
            // outgrew the sheet).
            ViewThatFits(in: .horizontal) {
                contextLineRow
                contextLineStacked
            }
        } else {
            Text("\(dateLabel) · \(placeName) · Feels-like by hour")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The settled one-line context: the date door (pine, bold,
    /// chevron) followed by the place / feels-like tail.
    private var contextLineRow: some View {
        HStack(spacing: 0) {
            dateDoor
            Text(" · \(placeName) · Feels-like by hour")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }

    /// The re-flowed context for large type: the date door on
    /// its own line, the tail (leading separator kept, exactly
    /// as the one-line form reads across its wrap point)
    /// wrapping by whole words beneath it.
    private var contextLineStacked: some View {
        VStack(spacing: 2) {
            dateDoor
            Text("· \(placeName) · Feels-like by hour")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The context line's date segment — the day picker's door
    /// (GEN-6 RW-66): pine, bold, chevron, the headline moment's
    /// own treatment (Concept 1), because it is the same kind
    /// of entry.
    private var dateDoor: some View {
        Button { showDateSheet = true } label: {
            HStack(spacing: 3) {
                Text(dateLabel)
                Text("›")
            }
            .font(.appSecondary.weight(.bold))
            .foregroundColor(AppTheme.pinePayload)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Choose a day: \(dateLabel)")
    }

    /// The Cancel / Confirm row. GEN-6 VQ FIX (RW-73): kept in
    /// its settled one-row form wherever both actions fit
    /// whole; where the type size outgrows the sheet's width
    /// the row stacks — Confirm first, each action full-width
    /// with a 44 pt target — so Confirm is never the ellipsised
    /// "Confir…" the gen-6 VQ render showed at the largest size.
    private var actionRow: some View {
        ViewThatFits(in: .horizontal) {
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
            VStack(spacing: 10) {
                Button("Confirm") {
                    onConfirm(draft.commit())
                    dismiss()
                }
                .font(.appSecondary.weight(.bold))
                .foregroundColor(AppTheme.pinePayload)
                .frame(maxWidth: .infinity, minHeight: 44)
                Button("Cancel") { dismiss() }
                    .font(.appSecondary.weight(.semibold))
                    .foregroundColor(AppTheme.inkSecondary)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
    }

    // MARK: - Readout + steppers

    /// The live readout flanked by the −/+ steppers (≥44pt):
    /// hour + feels-like ONLY, with the drafted hour's outfit
    /// preview line beneath it. The readout is real text, so
    /// VoiceOver hears exactly what the eye sees.
    private func readoutRow(chart: DayChartModel) -> some View {
        let entry = chart.entry(forHour: draft.draftHour)
        // GEN-6 VQ FIX (RW-73): the outfit preview line moves
        // OUT of the stepper row's centre column to the sheet's
        // full width beneath the row, wrapping by whole words
        // with no line cap. In the centre column (~234 pt
        // between the two 44 pt steppers) the line ellipsised
        // at every larger type size — and at the largest it
        // collapsed to "Short Slee…" — because two lines in
        // that column cannot hold the day's kit once the type
        // grows. The readout keeps its settled form and seat
        // between the steppers ("12 PM · 65°F", hour +
        // feels-like only); the steppers keep their full round
        // 44 pt targets.
        return VStack(spacing: 6) {
            HStack(spacing: 10) {
                // GEN-5 (RW-29 FA-39): steppers disable at the
                // selectable range's ends.
                stepper(systemImage: "minus", label: "Previous hour", isEnabled: draft.canStep(by: -1)) {
                    draft.step(by: -1)
                }

                Text(entry.map {
                    DayChartFormat.readout(
                        hour: $0.hour,
                        formattedFeelsLike: unit.formatFromFahrenheit($0.feelsLikeF)
                    )
                } ?? "")
                .font(.system(size: 15.5, weight: .bold))
                .foregroundColor(AppTheme.inkPrimary)
                .monospacedDigit()
                .frame(maxWidth: .infinity)

                stepper(systemImage: "plus", label: "Next hour", isEnabled: draft.canStep(by: 1)) {
                    draft.step(by: 1)
                }
            }

            if let entry, !entry.outfitItemNames.isEmpty {
                Text(entry.outfitItemNames.joined(separator: " · "))
                    .font(.appFootnote)
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
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
