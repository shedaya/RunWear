import SwiftUI

/// Types of weather details that can be shown
enum WeatherDetailType {
    case condition(WeatherCondition)
    case wind(speed: Double, direction: String, gusts: Double)
    case humidity(percentage: Int)
    case precipitation(percentage: Int)
    case uvIndex(value: Double)

    var title: String {
        switch self {
        case .condition: return "Weather Condition"
        case .wind: return "Wind"
        case .humidity: return "Humidity"
        case .precipitation: return "Precipitation"
        case .uvIndex: return "UV Index"
        }
    }

    var icon: String {
        switch self {
        case .condition(let condition): return condition.icon
        case .wind: return "wind"
        case .humidity: return "drop.fill"
        case .precipitation: return "cloud.rain"
        case .uvIndex: return "sun.max.fill"
        }
    }
}

struct WeatherDetailSheet: View {
    let detailType: WeatherDetailType
    @Binding var isPresented: Bool

    @State private var dragOffset: CGFloat = 0
    private let dismissThreshold: CGFloat = 100

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

                    // Icon and title
                    headerSection

                    // Content based on type
                    detailContent

                    // Running tips
                    tipsSection

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

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 12) {
            Image(systemName: detailType.icon)
                .font(.system(size: 48))
                .foregroundColor(iconColor)

            Text(detailType.title)
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
        }
    }

    // MARK: - Detail Content

    @ViewBuilder
    private var detailContent: some View {
        switch detailType {
        case .condition(let condition):
            conditionContent(condition)
        case .wind(let speed, let direction, let gusts):
            windContent(speed: speed, direction: direction, gusts: gusts)
        case .humidity(let percentage):
            humidityContent(percentage)
        case .precipitation(let percentage):
            precipitationContent(percentage)
        case .uvIndex(let value):
            uvIndexContent(value)
        }
    }

    private func conditionContent(_ condition: WeatherCondition) -> some View {
        VStack(spacing: 8) {
            Text(condition.rawValue)
                .font(.system(size: 36, weight: .semibold))
                .foregroundColor(.white)

            Text(conditionDescription(condition))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func windContent(speed: Double, direction: String, gusts: Double) -> some View {
        VStack(spacing: 16) {
            HStack(spacing: 24) {
                VStack(spacing: 4) {
                    Text("\(Int(round(speed)))")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                    Text("mph")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }

                VStack(spacing: 4) {
                    Text(direction)
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                    Text("direction")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }

                if gusts > speed {
                    VStack(spacing: 4) {
                        Text("\(Int(round(gusts)))")
                            .font(.system(size: 36, weight: .semibold))
                            .foregroundColor(.white)
                        Text("gusts")
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
            }

            Text(windDescription(speed: speed))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func humidityContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text("\(percentage)%")
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text(humidityDescription(percentage))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func precipitationContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text("\(percentage)%")
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text("Chance of precipitation")
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
        }
    }

    private func uvIndexContent(_ value: Double) -> some View {
        VStack(spacing: 8) {
            Text(String(format: "%.1f", value))
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text(uvDescription(value))
                .font(.system(size: 17, weight: .medium))
                .foregroundColor(uvColor(value))
        }
    }

    // MARK: - Tips Section

    private var tipsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Running Tip")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))

            Text(runningTip)
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.9))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
        )
    }

    // MARK: - Helper Properties

    private var iconColor: Color {
        switch detailType {
        case .condition(let condition):
            switch condition {
            case .clear: return .yellow
            case .partlyCloudy, .cloudy: return .gray
            case .foggy: return .gray
            case .drizzle, .rain: return .blue
            case .snow: return .cyan
            case .thunderstorm: return .purple
            case .unknown: return .white
            }
        case .wind: return .cyan
        case .humidity: return .blue
        case .precipitation: return .indigo
        case .uvIndex(let value): return uvColor(value)
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

    private func uvDescription(_ value: Double) -> String {
        switch value {
        case ..<3: return "Low"
        case 3..<6: return "Moderate"
        case 6..<8: return "High"
        case 8..<11: return "Very High"
        default: return "Extreme"
        }
    }

    private func conditionDescription(_ condition: WeatherCondition) -> String {
        switch condition {
        case .clear: return "Perfect conditions for running"
        case .partlyCloudy: return "Some cloud cover, comfortable running"
        case .cloudy: return "Overcast skies, cooler temperatures"
        case .foggy: return "Limited visibility, stay alert"
        case .drizzle: return "Light rain, consider a waterproof layer"
        case .rain: return "Wet conditions, waterproof gear recommended"
        case .snow: return "Snowy conditions, watch your footing"
        case .thunderstorm: return "Dangerous conditions, consider staying indoors"
        case .unknown: return "Check local conditions"
        }
    }

    private func windDescription(speed: Double) -> String {
        switch speed {
        case ..<5: return "Calm conditions"
        case 5..<15: return "Light breeze"
        case 15..<25: return "Moderate wind, may affect pace"
        case 25..<40: return "Strong wind, challenging running"
        default: return "Very strong wind, use caution"
        }
    }

    private func humidityDescription(_ percentage: Int) -> String {
        switch percentage {
        case ..<30: return "Low humidity, stay hydrated"
        case 30..<60: return "Comfortable humidity level"
        case 60..<80: return "Moderate humidity, pace yourself"
        default: return "High humidity, take it easy"
        }
    }

    private var runningTip: String {
        switch detailType {
        case .condition(let condition):
            switch condition {
            case .clear: return "Great day for a run! Don't forget sunscreen if it's sunny."
            case .partlyCloudy: return "Enjoy the cloud cover - it can help keep you cool."
            case .cloudy: return "Overcast days can be great for longer runs."
            case .foggy: return "Wear bright, reflective clothing for visibility."
            case .drizzle: return "Light rain can be refreshing. A cap helps keep rain off your face."
            case .rain: return "Wear moisture-wicking fabrics and protect your phone."
            case .snow: return "Choose shoes with good traction and shorten your stride."
            case .thunderstorm: return "Safety first - consider an indoor workout."
            case .unknown: return "Check conditions before heading out."
            }
        case .wind(let speed, _, _):
            if speed > 20 {
                return "Start your run heading into the wind so you have it at your back when tired."
            } else {
                return "Light wind can help cool you down during your run."
            }
        case .humidity(let percentage):
            if percentage > 70 {
                return "High humidity makes it harder to cool down. Slow your pace and hydrate frequently."
            } else if percentage < 30 {
                return "Low humidity can be deceptively dehydrating. Drink before you feel thirsty."
            } else {
                return "Comfortable humidity for running. Stay hydrated as usual."
            }
        case .precipitation(let percentage):
            if percentage > 50 {
                return "Consider waterproof gear and protect electronics in a ziplock bag."
            } else {
                return "Low chance of rain, but a light layer won't hurt."
            }
        case .uvIndex(let value):
            if value >= 6 {
                return "High UV - wear sunscreen SPF 30+, sunglasses, and a hat."
            } else if value >= 3 {
                return "Moderate UV - sunscreen recommended for longer runs."
            } else {
                return "Low UV exposure - enjoy your run!"
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

// MARK: - Preview

#Preview {
    VStack {
        WeatherDetailSheet(
            detailType: .uvIndex(value: 7.5),
            isPresented: .constant(true)
        )
    }
}
