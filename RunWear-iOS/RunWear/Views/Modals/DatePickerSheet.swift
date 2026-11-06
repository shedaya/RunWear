import SwiftUI

struct DatePickerSheet: View {
    @Binding var selectedDate: Date
    @Binding var isPresented: Bool
    /// GEN-6 (RW-66): fired after Confirm writes the selection —
    /// a host presenting this sheet inside the time picker uses
    /// it to dismiss the parent sheet, since the moment (day +
    /// kept hour) has been committed and home has re-solved.
    var onConfirm: (() -> Void)? = nil

    /// GEN-6 (RW-66): the sheet works on a DRAFT, exactly like
    /// its sibling the time picker (§6 mechanics): tapping a day
    /// moves the draft only; Confirm writes the selection once
    /// (the host's onChange re-solves home exactly once); Cancel
    /// and swipe-dismiss write nothing, so the prior selection
    /// stands restored exactly. (The pre-GEN-6 form bound the
    /// selection live and auto-dismissed on tap — a date change
    /// re-solved instantly with no way back.)
    @State private var draft: DatePickerDraft

    /// GEN-6 VQ FIX (RW-73): the grid's column count adapts to
    /// the Dynamic Type size in force, so a whole weekday word
    /// always fits its tile — at the largest sizes the settled
    /// four-column grid squeezed "Tomorrow" into a mid-word
    /// wrap ("To-mor-row" over three lines) and pushed "Nov"
    /// past its tile's edge (the gen-6 VQ render). Two columns
    /// at accessibility1–3 and one full-width column at
    /// accessibility4–5; four wherever the words fit, which
    /// includes every non-accessibility size. (Measured from
    /// this branch's own renders: "Tomorrow" in the settled
    /// eyebrow face demands ~113 pt at accessibility2 and more
    /// than the ~167 pt two-across tile at accessibility5, so
    /// the largest sizes give each tile the full sheet width.)
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// The grid's column count for a given size — taken as a
    /// parameter (not read from the environment here) so the
    /// render harness can compose the content at an explicit
    /// size through the sheetContent seam below; production
    /// passes its own environment value, so behaviour is
    /// unchanged.
    private func gridColumnCount(for size: DynamicTypeSize) -> Int {
        switch size {
        case .accessibility4, .accessibility5:
            return 1
        case .accessibility1, .accessibility2, .accessibility3:
            return 2
        default:
            return 4
        }
    }

    init(selectedDate: Binding<Date>, isPresented: Binding<Bool>, onConfirm: (() -> Void)? = nil) {
        self._selectedDate = selectedDate
        self._isPresented = isPresented
        self.onConfirm = onConfirm
        _draft = State(initialValue: DatePickerDraft(originalDate: selectedDate.wrappedValue))
    }

    private let calendar = Calendar.current
    /// The forecast horizon is 7 days (WeatherService forecast_days).
    /// Browsing extends 3 days past it; those days are NAMED as
    /// beyond the forecast in the grid (frame 42) and land on the
    /// RW-23 out-of-forecast notice when picked — never dressed on
    /// invented weather.
    static let forecastHorizonDays = 7
    static let browseDays = 10
    private var availableDates: [Date] {
        (0..<Self.browseDays).compactMap { calendar.date(byAdding: .day, value: $0, to: calendar.startOfDay(for: Date())) }
    }

    static func isBeyondForecast(dayIndex: Int) -> Bool {
        dayIndex >= forecastHorizonDays
    }

    // Presented as a real .sheet (design record §5.2): the system
    // owns the backdrop, drag handle, and dismissal.
    //
    // RW-73 DETENT RULING (Sol, Nov 6, 2026 — delegated to Tank's
    // suggestion; BACKLOG RW-73): with the RW-73 composition fixes
    // the untruncated content at the largest accessibility text
    // sizes is taller than a phone screen. At those sizes — and
    // only those — this sheet's content may scroll internally, so
    // Confirm stays reachable and whole; at default and moderate
    // sizes the settled no-scroll sheet discipline stands and the
    // content is the fixed stack it always was. The detents are
    // unchanged ([.medium, .large] at both hosts).
    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                ScrollView {
                    sheetContent(size: dynamicTypeSize)
                }
            } else {
                sheetContent(size: dynamicTypeSize)
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        // RW-6.10: one selection tick per change — nothing more.
        .sensoryFeedback(.selection, trigger: draft.draftDate)
    }

    // Internal (not private) as the render-harness seam: a
    // SwiftUI ScrollView photographs as a blank canvas under
    // ImageRenderer, so the harness grades the AX-size CONTENT
    // through this entry point (wholeness, Confirm at the
    // foot), passing the size explicitly, while
    // SheetDisciplineTests pins the gated ScrollView wrapper
    // itself. The scroll motion is a device item.
    func sheetContent(size: DynamicTypeSize) -> some View {
        VStack(spacing: 20) {
            // Title — frame 42's own name for the sheet, the
            // sibling of the time picker's "Choose a Time".
            Text("Choose a Day")
                .font(.appSheetTitle)
                .foregroundColor(AppTheme.inkPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            // Date grid (draft selection)
            dateGrid

            // GEN-6 (RW-66): the settled Confirm gate, in the
            // time picker's own button treatment — nothing
            // re-solves until Confirm; Cancel restores exactly.
            // GEN-6 VQ FIX (RW-73): the row stacks at the type
            // sizes where the two actions no longer fit whole
            // side by side (Confirm first, full-width 44 pt
            // targets), exactly as the time picker's row does.
            ViewThatFits(in: .horizontal) {
                HStack {
                    Button("Cancel") { dismiss() }
                        .font(.appSecondary.weight(.semibold))
                        .foregroundColor(AppTheme.inkSecondary)
                    Spacer()
                    Button("Confirm") {
                        selectedDate = draft.commit()
                        onConfirm?()
                        dismiss()
                    }
                    .font(.appSecondary.weight(.bold))
                    .foregroundColor(AppTheme.pinePayload)
                }
                VStack(spacing: 10) {
                    Button("Confirm") {
                        selectedDate = draft.commit()
                        onConfirm?()
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

            Spacer().frame(height: 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
    }


    // MARK: - Date Grid

    private var dateGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: gridColumnCount(for: size)), spacing: 12) {
            ForEach(Array(availableDates.enumerated()), id: \.offset) { index, date in
                DateButton(
                    date: date,
                    isBeyondForecast: Self.isBeyondForecast(dayIndex: index),
                    isSelected: calendar.isDate(date, inSameDayAs: draft.draftDate),
                    action: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            draft.select(date)
                        }
                    }
                )
            }
        }
    }


    private func dismiss() {
        // State-driven dismissal (RW-6.12): the system animates the
        // sheet away; no hand-rolled transition wrapper.
        isPresented = false
    }
}

// MARK: - Date Button

private struct DateButton: View {
    let date: Date
    var isBeyondForecast: Bool = false
    let isSelected: Bool
    let action: () -> Void

    private let calendar = Calendar.current

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                // Day label (Today, Tomorrow, or day name)
                Text(dayLabel)
                    .font(.appEyebrow)
                    .foregroundColor(isSelected ? .white : AppTheme.inkMuted)

                // Date number
                Text("\(calendar.component(.day, from: date))")
                    .font(.title2.weight(.semibold))
                    .monospacedDigit()
                    .foregroundColor(isSelected ? .white : AppTheme.inkPrimary)

                // Month abbreviation
                Text(monthLabel)
                    .font(.appEyebrow)
                    .foregroundColor(isSelected ? .white.opacity(0.8) : AppTheme.inkSecondary)

                // Frame 42: beyond-forecast days are named as such.
                if isBeyondForecast {
                    Text("Beyond forecast")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(isSelected ? .white.opacity(0.8) : AppTheme.inkMuted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            // GEN-5 design pass (cream map row 21): the day
            // cells are CONTENT, not chrome — the design
            // reserves glass for chrome — so the cells take the
            // flat card treatment every other content surface
            // uses (cardFill + cardStroke), with selection as
            // the pine fill. The Liquid Glass button modifier
            // leaves this grid.
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? AppTheme.primaryColor : AppTheme.cardFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.clear : AppTheme.cardStroke, lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var dayLabel: String {
        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInTomorrow(date) {
            return "Tomorrow"
        } else {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE"
            return formatter.string(from: date)
        }
    }

    private var monthLabel: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM"
        return formatter.string(from: date)
    }
}

// MARK: - Preview

#Preview {
    DatePickerSheet(
        selectedDate: .constant(Date()),
        isPresented: .constant(true)
    )
}
