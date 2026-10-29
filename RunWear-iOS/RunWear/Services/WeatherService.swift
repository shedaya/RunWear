import Foundation
import CoreLocation

class WeatherService {
    static let shared = WeatherService()
    private let baseURL = "https://api.open-meteo.com/v1/forecast"

    private init() {}

    func fetchWeather(latitude: Double, longitude: Double) async throws -> WeatherData {
        var components = URLComponents(string: baseURL)!
        // The weather contract mirrors Android's OpenMeteoApi: one fetch
        // carrying the legacy current block, the extended current block,
        // and the full hourly list (feels-like, humidity, UV, measured
        // precipitation/rain) in the LOCATION's timezone (timezone=auto).
        // Wind is requested in mph: the engine's windy gate (> 10) and
        // every wind label in the app are mph (Android requests mph
        // whenever Fahrenheit is the display unit; the iOS model layer
        // stores Celsius and converts for display, so temperature stays
        // celsius here — the frames the engine sees are unaffected).
        components.queryItems = [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "current_weather", value: "true"),
            URLQueryItem(name: "current", value: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,precipitation_probability,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"),
            URLQueryItem(name: "hourly", value: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"),
            URLQueryItem(name: "temperature_unit", value: "celsius"),
            URLQueryItem(name: "wind_speed_unit", value: "mph"),
            URLQueryItem(name: "timezone", value: "auto"),
            URLQueryItem(name: "forecast_days", value: "7")
        ]

        guard let url = components.url else {
            throw WeatherError.invalidURL
        }

        let (data, response) = try await URLSession.shared.data(from: url)

        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw WeatherError.invalidResponse
        }

        let decoder = JSONDecoder()
        return try decoder.decode(WeatherData.self, from: data)
    }
}

enum WeatherError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case decodingError
    case locationNotAvailable

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .invalidResponse:
            return "Invalid response from weather service"
        case .decodingError:
            return "Could not decode weather data"
        case .locationNotAvailable:
            return "Location not available"
        }
    }
}
