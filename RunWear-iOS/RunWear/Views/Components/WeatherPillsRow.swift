import SwiftUI

struct WeatherPillsRow: View {
    let condition: WeatherCondition
    let windSpeed: Double
    let windDirection: String
    let humidity: Int
    let precipitationProbability: Int
    let uvIndex: Double

    var onConditionTapped: () -> Void = {}
    var onWindTapped: () -> Void = {}
    var onHumidityTapped: () -> Void = {}
    var onPrecipitationTapped: () -> Void = {}
    var onUVTapped: () -> Void = {}

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Condition pill (always shown)
                WeatherPill(
                    type: .condition(condition),
                    onTap: onConditionTapped
                )

                // Wind pill (always shown)
                WeatherPill(
                    type: .wind(speed: windSpeed, direction: windDirection),
                    onTap: onWindTapped
                )

                // Humidity pill (always shown)
                WeatherPill(
                    type: .humidity(percentage: humidity),
                    onTap: onHumidityTapped
                )

                // Precipitation pill (only if > 0%)
                if precipitationProbability > 0 {
                    WeatherPill(
                        type: .precipitation(percentage: precipitationProbability),
                        onTap: onPrecipitationTapped
                    )
                }

                // UV Index pill (only if > 0)
                if uvIndex > 0 {
                    WeatherPill(
                        type: .uvIndex(value: uvIndex),
                        onTap: onUVTapped
                    )
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Convenience initializer with HourlyWeatherSnapshot

extension WeatherPillsRow {
    init(
        snapshot: HourlyWeatherSnapshot,
        onConditionTapped: @escaping () -> Void = {},
        onWindTapped: @escaping () -> Void = {},
        onHumidityTapped: @escaping () -> Void = {},
        onPrecipitationTapped: @escaping () -> Void = {},
        onUVTapped: @escaping () -> Void = {}
    ) {
        self.condition = snapshot.condition
        self.windSpeed = snapshot.windSpeed * 0.621371 // km/h to mph
        self.windDirection = snapshot.windDirectionCompass
        self.humidity = snapshot.humidity
        self.precipitationProbability = snapshot.precipitationProbability
        self.uvIndex = snapshot.uvIndex
        self.onConditionTapped = onConditionTapped
        self.onWindTapped = onWindTapped
        self.onHumidityTapped = onHumidityTapped
        self.onPrecipitationTapped = onPrecipitationTapped
        self.onUVTapped = onUVTapped
    }
}

// MARK: - Preview

#Preview {
    VStack {
        WeatherPillsRow(
            condition: .partlyCloudy,
            windSpeed: 8,
            windDirection: "NW",
            humidity: 55,
            precipitationProbability: 20,
            uvIndex: 6.5
        )

        // Without precipitation or UV
        WeatherPillsRow(
            condition: .cloudy,
            windSpeed: 15,
            windDirection: "S",
            humidity: 80,
            precipitationProbability: 0,
            uvIndex: 0
        )
    }
    .padding(.vertical)
    .background(Color.black)
}
