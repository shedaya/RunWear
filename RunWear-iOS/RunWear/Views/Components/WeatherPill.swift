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

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Image(systemName: type.icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(iconColor)

                Text(type.label)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(Color.white.opacity(0.1))
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.15), lineWidth: 1)
            )
        }
        .accessibilityLabel(type.accessibilityLabel)
    }

    private var iconColor: Color {
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
