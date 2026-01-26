import Foundation

struct WeatherData: Codable {
    let latitude: Double
    let longitude: Double
    let currentWeather: CurrentWeather

    enum CodingKeys: String, CodingKey {
        case latitude, longitude
        case currentWeather = "current_weather"
    }
}

struct CurrentWeather: Codable {
    let temperature: Double
    let windspeed: Double
    let weathercode: Int
    let isDay: Int

    enum CodingKeys: String, CodingKey {
        case temperature, windspeed, weathercode
        case isDay = "is_day"
    }

    var temperatureFahrenheit: Double {
        temperature * 9 / 5 + 32
    }

    var condition: WeatherCondition {
        WeatherCondition.from(code: weathercode)
    }
}

enum WeatherCondition: String {
    case clear = "Clear"
    case partlyCloudy = "Partly Cloudy"
    case cloudy = "Cloudy"
    case foggy = "Foggy"
    case drizzle = "Drizzle"
    case rain = "Rain"
    case snow = "Snow"
    case thunderstorm = "Thunderstorm"
    case unknown = "Unknown"

    var icon: String {
        switch self {
        case .clear: return "sun.max.fill"
        case .partlyCloudy: return "cloud.sun.fill"
        case .cloudy: return "cloud.fill"
        case .foggy: return "cloud.fog.fill"
        case .drizzle: return "cloud.drizzle.fill"
        case .rain: return "cloud.rain.fill"
        case .snow: return "cloud.snow.fill"
        case .thunderstorm: return "cloud.bolt.rain.fill"
        case .unknown: return "questionmark.circle.fill"
        }
    }

    static func from(code: Int) -> WeatherCondition {
        switch code {
        case 0: return .clear
        case 1, 2: return .partlyCloudy
        case 3: return .cloudy
        case 45, 48: return .foggy
        case 51, 53, 55: return .drizzle
        case 61, 63, 65, 80, 81, 82: return .rain
        case 71, 73, 75, 77, 85, 86: return .snow
        case 95, 96, 99: return .thunderstorm
        default: return .unknown
        }
    }
}
