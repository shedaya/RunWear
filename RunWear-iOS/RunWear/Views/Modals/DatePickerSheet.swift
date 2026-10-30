import SwiftUI

struct DatePickerSheet: View {
    @Binding var selectedDate: Date
    @Binding var isPresented: Bool


    private let calendar = Calendar.current
    private var availableDates: [Date] {
        (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: calendar.startOfDay(for: Date())) }
    }

    // Presented as a real .sheet (design record §5.2): the system
    // owns the backdrop, drag handle, and dismissal.
    var body: some View {
        VStack(spacing: 20) {
            // Title
            Text("Select Date")
                .font(.appSheetTitle)
                .foregroundColor(.white)

            // Date grid
            dateGrid

            Spacer().frame(height: 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppTheme.darkBackground.ignoresSafeArea())
        // RW-6.10: one selection tick per change — nothing more.
        .sensoryFeedback(.selection, trigger: selectedDate)
    }


    // MARK: - Date Grid

    private var dateGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 12) {
            ForEach(availableDates, id: \.self) { date in
                DateButton(
                    date: date,
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
    let isSelected: Bool
    let action: () -> Void

    private let calendar = Calendar.current

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                // Day label (Today, Tomorrow, or day name)
                Text(dayLabel)
                    .font(.appEyebrow)
                    .foregroundColor(isSelected ? .white : .white.opacity(0.6))

                // Date number
                Text("\(calendar.component(.day, from: date))")
                    .font(.title2.weight(.semibold))
                    .monospacedDigit()
                    .foregroundColor(isSelected ? .white : .white.opacity(0.9))

                // Month abbreviation
                Text(monthLabel)
                    .font(.appEyebrow)
                    .foregroundColor(isSelected ? .white.opacity(0.8) : .white.opacity(0.5))
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
