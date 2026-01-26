import Foundation
import CoreLocation

class WeatherService {
    static let shared = WeatherService()
    private let baseURL = "https://api.open-meteo.com/v1/forecast"

    private init() {}

    func fetchWeather(latitude: Double, longitude: Double) async throws -> WeatherData {
        var components = URLComponents(string: baseURL)!
        components.queryItems = [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "current_weather", value: "true"),
            URLQueryItem(name: "temperature_unit", value: "celsius")
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
