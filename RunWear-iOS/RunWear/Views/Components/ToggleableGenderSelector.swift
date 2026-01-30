import SwiftUI

/// A toggleable gender selector where tapping the selected option deselects it (returns to unisex)
struct ToggleableGenderSelector: View {
    @Binding var selection: GenderPreference

    var body: some View {
        HStack(spacing: 12) {
            GenderButton(
                label: "Male",
                iconName: "figure.run",
                isSelected: selection == .male,
                action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        if selection == .male {
                            selection = .unisex
                        } else {
                            selection = .male
                        }
                    }
                }
            )

            GenderButton(
                label: "Female",
                iconName: "figure.run",
                isSelected: selection == .female,
                action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        if selection == .female {
                            selection = .unisex
                        } else {
                            selection = .female
                        }
                    }
                }
            )
        }
    }
}

// MARK: - Gender Button

private struct GenderButton: View {
    let label: String
    let iconName: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: iconName)
                    .font(.system(size: 20, weight: .medium))
                Text(label)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundColor(isSelected ? .white : .white.opacity(0.6))
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
        .accessibilityLabel("\(label) fit preference")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

// MARK: - Compact Version for Watch

struct CompactGenderSelector: View {
    @Binding var selection: GenderPreference

    var body: some View {
        HStack(spacing: 8) {
            CompactGenderButton(
                label: "M",
                isSelected: selection == .male,
                action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selection = selection == .male ? .unisex : .male
                    }
                }
            )

            CompactGenderButton(
                label: "F",
                isSelected: selection == .female,
                action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selection = selection == .female ? .unisex : .female
                    }
                }
            )
        }
    }
}

private struct CompactGenderButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(isSelected ? .white : .white.opacity(0.5))
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(isSelected ? Color.white.opacity(0.3) : Color.white.opacity(0.1))
                )
                .overlay(
                    Circle()
                        .stroke(isSelected ? Color.white.opacity(0.4) : Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 40) {
        VStack(alignment: .leading, spacing: 8) {
            Text("Fit Preference")
                .font(.headline)
                .foregroundColor(.white)
            ToggleableGenderSelector(selection: .constant(.male))
        }

        VStack(alignment: .leading, spacing: 8) {
            Text("Unisex (none selected)")
                .font(.headline)
                .foregroundColor(.white)
            ToggleableGenderSelector(selection: .constant(.unisex))
        }

        VStack(alignment: .leading, spacing: 8) {
            Text("Compact (Watch)")
                .font(.headline)
                .foregroundColor(.white)
            CompactGenderSelector(selection: .constant(.female))
        }
    }
    .padding()
    .background(Color.black)
}
