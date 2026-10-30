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
                .font(.appSheetTitle)
                .foregroundColor(.white)

            // Time grid
            timeGrid

            Spacer().frame(height: 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(AppTheme.darkBackground.ignoresSafeArea())
        // RW-6.10: one selection tick per change — nothing more.
        .sensoryFeedback(.selection, trigger: selectedHour)
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
        // State-driven dismissal (RW-6.12): the system animates the
        // sheet away; no hand-rolled transition wrapper.
        isPresented = false
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
                    .font(.appItemName)
                    .monospacedDigit()
                    .foregroundColor(isSelected ? .white : .white.opacity(0.9))

                Text(period)
                    .font(.appEyebrow)
                    .foregroundColor(isSelected ? .white.opacity(0.8) : .white.opacity(0.5))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            // Interactive Liquid Glass; selection is the teal tint of
            // the same glass (shared GlassButtonModifier).
            .glassButton(isSelected: isSelected, cornerRadius: 12)
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
