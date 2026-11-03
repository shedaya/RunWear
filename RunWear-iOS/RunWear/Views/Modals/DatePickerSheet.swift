import SwiftUI

struct DatePickerSheet: View {
    @Binding var selectedDate: Date
    @Binding var isPresented: Bool


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
    var body: some View {
        VStack(spacing: 20) {
            // Title
            Text("Select Date")
                .font(.appSheetTitle)
                .foregroundColor(AppTheme.inkPrimary)

            // Date grid
            dateGrid

            Spacer().frame(height: 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppTheme.background.ignoresSafeArea())
        // RW-6.10: one selection tick per change — nothing more.
        .sensoryFeedback(.selection, trigger: selectedDate)
    }


    // MARK: - Date Grid

    private var dateGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 12) {
            ForEach(Array(availableDates.enumerated()), id: \.offset) { index, date in
                DateButton(
                    date: date,
                    isBeyondForecast: Self.isBeyondForecast(dayIndex: index),
                    isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                    action: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedDate = date
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            dismiss()
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
            // Interactive Liquid Glass; selection is the teal tint of
            // the same glass (shared GlassButtonModifier).
            .glassButton(isSelected: isSelected, cornerRadius: 12)
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
