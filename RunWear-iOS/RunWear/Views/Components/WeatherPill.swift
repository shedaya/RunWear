import SwiftUI

/// Type of weather information displayed in a pill
enum WeatherPillType {
    case condition(WeatherCondition)
    case wind(speed: Double, direction: String)
    case humidity(percentage: Int)
    case precipitation(percentage: Int)
    case uvIndex(value: Double)

    var icon: String {
        switch self {
        case .condition(let condition):
            return condition.icon
        case .wind:
            return "wind"
        case .humidity:
            return "drop.fill"
        case .precipitation:
            return "cloud.rain"
        case .uvIndex:
            return "sun.max.fill"
        }
    }

    var label: String {
        switch self {
        case .condition(let condition):
            return condition.shortDescription
        case .wind(let speed, _):
            return "\(Int(round(speed))) mph"
        case .humidity(let percentage):
            return "\(percentage)%"
        case .precipitation(let percentage):
            return "\(percentage)%"
        case .uvIndex(let value):
            return String(format: "%.1f", value)
        }
    }

    var accessibilityLabel: String {
        switch self {
        case .condition(let condition):
            return "Weather: \(condition.rawValue)"
        case .wind(let speed, let direction):
            return "Wind: \(Int(round(speed))) miles per hour from \(direction)"
        case .humidity(let percentage):
            return "Humidity: \(percentage) percent"
        case .precipitation(let percentage):
            return "Precipitation chance: \(percentage) percent"
        case .uvIndex(let value):
            return "UV Index: \(value)"
        }
    }
}

struct WeatherPill: View {
    let type: WeatherPillType
    var onTap: () -> Void = {}
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Image(systemName: type.icon)
                    .font(.appPillLabel)
                    .foregroundColor(iconColor)

                Text(type.label)
                    .font(.appPillLabel)
                    .monospacedDigit()
                    .foregroundColor(contentColor)
            }
            // The one shared glass pill chrome (native Liquid Glass) —
            // no per-view material recipes.
            .glassPill()
        }
        .accessibilityLabel(type.accessibilityLabel)
    }

    /// RW-26 legibility fix (see AppTheme.weatherPillContentHex):
    /// pill content is ink in light mode — white on clear glass over
    /// the cream sheet measured ≈1.03:1. Dark mode is unchanged.
    private var contentColor: Color {
        Color(hex: AppTheme.weatherPillContentHex(isDark: colorScheme == .dark))
    }

    private var iconColor: Color {
        // Light mode: the whole pill goes monochrome ink so every
        // glyph clears the bar (the yellow/cyan hues measure
        // 1.3–2.3:1 on cream). Dark mode keeps the hue coding.
        guard colorScheme == .dark else { return contentColor }
        switch type {
        case .condition(let condition):
            return conditionColor(condition)
        case .wind:
            return .cyan
        case .humidity:
            return .blue
        case .precipitation:
            return .indigo
        case .uvIndex(let value):
            return uvColor(value)
        }
    }

    private func conditionColor(_ condition: WeatherCondition) -> Color {
        switch condition {
        case .clear: return .yellow
        case .partlyCloudy: return .orange
        case .cloudy: return .gray
        case .foggy: return .gray
        case .drizzle: return .blue
        case .rain: return .blue
        case .snow: return .cyan
        case .thunderstorm: return .purple
        case .unknown: return .white
        }
    }

    private func uvColor(_ value: Double) -> Color {
        switch value {
        case ..<3: return .green
        case 3..<6: return .yellow
        case 6..<8: return .orange
        case 8..<11: return .red
        default: return .purple
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 16) {
        WeatherPill(type: .condition(.clear))
        WeatherPill(type: .wind(speed: 12, direction: "NW"))
        WeatherPill(type: .humidity(percentage: 65))
        WeatherPill(type: .precipitation(percentage: 20))
        WeatherPill(type: .uvIndex(value: 7.5))
    }
    .padding()
    .background(Color.black)
}
