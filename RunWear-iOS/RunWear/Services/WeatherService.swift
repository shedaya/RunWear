import Foundation
import CoreLocation

class WeatherService {
    static let shared = WeatherService()
    private static let baseURL = "https://api.open-meteo.com/v1/forecast"

    private init() {}

    /// Coordinates leave the device only at Coarse Location precision:
    /// 1 decimal place (~11 km), the AccliMate approach, adopted for
    /// RunWear by Sol's ruling (2026-10-30) so the privacy manifest can
    /// declare Coarse instead of Precise. The rounding lives here, at
    /// the request boundary — callers pass full precision, and stored,
    /// displayed, and persisted locations are never rounded.
    static func coarseCoordinate(_ value: Double) -> Double {
        (value * 10).rounded() / 10
    }

    /// The forecast request URL — the single place coordinates are
    /// serialized into an outbound query (both the app and watch
    /// targets fetch through here). Pinned by WeatherServicePrivacyTests.
    static func forecastURL(latitude: Double, longitude: Double) -> URL? {
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
            URLQueryItem(name: "latitude", value: String(coarseCoordinate(latitude))),
            URLQueryItem(name: "longitude", value: String(coarseCoordinate(longitude))),
            URLQueryItem(name: "current_weather", value: "true"),
            URLQueryItem(name: "current", value: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,precipitation_probability,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"),
            URLQueryItem(name: "hourly", value: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"),
            URLQueryItem(name: "temperature_unit", value: "celsius"),
            URLQueryItem(name: "wind_speed_unit", value: "mph"),
            URLQueryItem(name: "timezone", value: "auto"),
            URLQueryItem(name: "forecast_days", value: "7")
        ]

        return components.url
    }

    func fetchWeather(latitude: Double, longitude: Double) async throws -> WeatherData {
        guard let url = Self.forecastURL(latitude: latitude, longitude: longitude) else {
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
