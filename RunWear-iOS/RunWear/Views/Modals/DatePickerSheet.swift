import SwiftUI

struct DatePickerSheet: View {
    @Binding var selectedDate: Date
    @Binding var isPresented: Bool

    @State private var dragOffset: CGFloat = 0
    private let dismissThreshold: CGFloat = 100

    private let calendar = Calendar.current
    private var availableDates: [Date] {
        (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: calendar.startOfDay(for: Date())) }
    }

    var body: some View {
        ZStack {
            // Backdrop
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }

            // Sheet content
            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 20) {
                    // Drag handle
                    dragHandle

                    // Title
                    Text("Select Date")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    // Date grid
                    dateGrid

                    Spacer().frame(height: 16)
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.modalCornerRadius)
                        .fill(AppTheme.darkBackground)
                )
                .offset(y: dragOffset)
                .gesture(dragGesture)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .transition(.opacity)
    }

    // MARK: - Drag Handle

    private var dragHandle: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(Color.white.opacity(0.3))
            .frame(width: 40, height: 5)
            .padding(.top, 8)
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

    // MARK: - Gestures

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if value.translation.height > 0 {
                    dragOffset = value.translation.height
                }
            }
            .onEnded { value in
                if value.translation.height > dismissThreshold {
                    dismiss()
                } else {
                    withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
                        dragOffset = 0
                    }
                }
            }
    }

    private func dismiss() {
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
        }
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
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.6))

                // Date number
                Text("\(calendar.component(.day, from: date))")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.9))

                // Month abbreviation
                Text(monthLabel)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(isSelected ? .white.opacity(0.8) : .white.opacity(0.5))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.white.opacity(0.25) : Color.white.opacity(0.1))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.white.opacity(0.4) : Color.white.opacity(0.15), lineWidth: 1)
            )
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
