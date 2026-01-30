import SwiftUI

struct TimePickerSheet: View {
    @Binding var selectedHour: Int
    @Binding var isPresented: Bool

    @State private var dragOffset: CGFloat = 0
    private let dismissThreshold: CGFloat = 100

    // Available hours: 5 AM to 9 PM
    private let availableHours = Array(5...21)

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
                    Text("Select Time")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    // Time grid
                    timeGrid

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
