import SwiftUI

struct TimePickerSheet: View {
    @Binding var selectedHour: Int
    @Binding var isPresented: Bool


    // Available hours: 5 AM to 9 PM
    private let availableHours = Array(5...21)

    // Presented as a real .sheet (design record §5.2): the system
    // owns the backdrop, drag handle, and dismissal.
    var body: some View {
        VStack(spacing: 20) {
            // Title
            Text("Select Time")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)

            // Time grid
            timeGrid

            Spacer().frame(height: 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppTheme.darkBackground.ignoresSafeArea())
    }


    // MARK: - Time Grid

    private var timeGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 12) {
            ForEach(availableHours, id: \.self) { hour in
                TimeButton(
                    hour: hour,
                    isSelected: hour == selectedHour,
                    action: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedHour = hour
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
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
        }
    }
}

// MARK: - Time Button

private struct TimeButton: View {
    let hour: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(formattedHour)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.9))

                Text(period)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(isSelected ? .white.opacity(0.8) : .white.opacity(0.5))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
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

    private var formattedHour: String {
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return "\(displayHour):00"
    }

    private var period: String {
        hour >= 12 ? "PM" : "AM"
    }
}

// MARK: - Preview

#Preview {
    TimePickerSheet(
        selectedHour: .constant(14),
        isPresented: .constant(true)
    )
}
