import SwiftUI

// MARK: - Shared Preference Controls (RW-6.4 / RW-6.7 / RW-6.8)
//
// ONE set of native controls for the three user preferences, used by both
// Settings and Onboarding so the two surfaces can never drift apart.
// These replace the two hand-rolled DragGesture sliders and the custom
// toggle-button selectors: native Slider/Picker give VoiceOver
// adjustability, Dynamic Type, and standard haptics behavior for free.
// A change of value fires one restrained `.selection` haptic (RW-6.10).

// MARK: Comfort

/// The single comfort control: a native stepped Slider over the five
/// ComfortLevel stops. VoiceOver users adjust it like any system slider;
/// the current level's description is the accessibility value.
struct ComfortLevelControl: View {
    @Binding var comfortLevel: ComfortLevel

    private var indexBinding: Binding<Double> {
        Binding(
            get: { Double(comfortLevel.sliderIndex) },
            set: { comfortLevel = ComfortLevel.from(sliderIndex: Int($0.rounded())) }
        )
    }

    private var tint: Color {
        switch comfortLevel {
        case .veryCold, .cold: return .blue
        case .neutral: return AppTheme.primaryColor
        case .warm, .veryWarm: return .orange
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // WS-8 (D-25 iOS residue): the comfort micro-labels —
            // the current level's label here and the Runs Cold /
            // Runs Warm end labels below — render in the design's
            // secondary token pair (AppTheme.inkSecondary:
            // #5F5B52 light / #B8AEA0 dark), NOT system .secondary.
            // System .secondary composites to ≈3.3–3.4:1 on the
            // light grounds, below the 4.5:1 text floor; the token
            // pair clears it in both modes (6.00–6.54:1 light,
            // 6.95–7.66:1 dark — pinned in RW26Phase2Tests).
            Text(comfortLevel.shortLabel)
                .font(.appSecondary)
                .foregroundStyle(AppTheme.inkSecondary)

            Slider(value: indexBinding, in: 0...4, step: 1) {
                Text("Body Temperature")
            }
            .tint(tint)
            .accessibilityLabel("Body Temperature")
            .accessibilityValue(comfortLevel.shortLabel)

            HStack {
                Text("Runs Cold")
                Spacer()
                Text("Runs Warm")
            }
            .font(.appFootnote)
            .foregroundStyle(AppTheme.inkSecondary)
        }
        .sensoryFeedback(.selection, trigger: comfortLevel)
    }
}

// MARK: Temperature Unit

/// Segmented native Picker for the temperature unit.
struct TemperatureUnitPicker: View {
    @Binding var unit: TemperatureUnit

    var body: some View {
        Picker("Temperature Unit", selection: $unit) {
            ForEach(TemperatureUnit.allCases, id: \.self) { unit in
                Text(unit.label).tag(unit)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .sensoryFeedback(.selection, trigger: unit)
    }
}

// MARK: Fit Preference

/// Native Picker for the fit preference. Menu style: the third option's
/// full label ("No preference") truncates in a segmented control at
/// large Dynamic Type sizes, and the menu is the native Form pattern.
struct FitPreferencePicker: View {
    @Binding var preference: GenderPreference

    var body: some View {
        Picker("Fit Preference", selection: $preference) {
            Text("Men's").tag(GenderPreference.male)
            Text("Women's").tag(GenderPreference.female)
            Text("No preference").tag(GenderPreference.unisex)
        }
        .labelsHidden()
        .sensoryFeedback(.selection, trigger: preference)
    }
}
