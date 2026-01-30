import Foundation

struct WeatherData: Codable {
    let latitude: Double
    let longitude: Double
    let currentWeather: CurrentWeather
    let hourly: HourlyWeather?

    enum CodingKeys: String, CodingKey {
        case latitude, longitude
        case currentWeather = "current_weather"
        case hourly
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        latitude = try container.decode(Double.self, forKey: .latitude)
        longitude = try container.decode(Double.self, forKey: .longitude)
        currentWeather = try container.decode(CurrentWeather.self, forKey: .currentWeather)
        hourly = try container.decodeIfPresent(HourlyWeather.self, forKey: .hourly)
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

/// Extended weather data from hourly forecast
struct HourlyWeather: Codable {
    let time: [String]
    let temperature2m: [Double]?
    let apparentTemperature: [Double]?
    let relativeHumidity2m: [Int]?
    let precipitationProbability: [Int]?
    let uvIndex: [Double]?
    let windSpeed10m: [Double]?
    let windDirection10m: [Int]?
    let windGusts10m: [Double]?
    let weatherCode: [Int]?

    enum CodingKeys: String, CodingKey {
        case time
        case temperature2m = "temperature_2m"
        case apparentTemperature = "apparent_temperature"
        case relativeHumidity2m = "relative_humidity_2m"
        case precipitationProbability = "precipitation_probability"
        case uvIndex = "uv_index"
        case windSpeed10m = "wind_speed_10m"
        case windDirection10m = "wind_direction_10m"
        case windGusts10m = "wind_gusts_10m"
        case weatherCode = "weather_code"
    }
}

/// Snapshot of weather at a specific hour
struct HourlyWeatherSnapshot {
    let hour: Int
    let temperature: Double // Celsius
    let feelsLike: Double // Celsius
    let humidity: Int // Percentage
    let precipitationProbability: Int // Percentage
    let uvIndex: Double
    let windSpeed: Double // km/h
    let windDirection: Int // Degrees
    let windGusts: Double // km/h
    let weatherCode: Int

    var temperatureFahrenheit: Double {
        temperature * 9 / 5 + 32
    }

    var feelsLikeFahrenheit: Double {
        feelsLike * 9 / 5 + 32
    }

    var condition: WeatherCondition {
        WeatherCondition.from(code: weatherCode)
    }

    var heroWeatherCondition: HeroWeatherCondition {
        HeroWeatherCondition.from(weatherCode: weatherCode)
    }

    var heroTempBracket: HeroTempBracket {
        HeroTempBracket.from(feelsLikeTemperature: feelsLikeFahrenheit)
    }

    var heroTimeOfDay: HeroTimeOfDay {
        HeroTimeOfDay.from(hour: hour)
    }

    /// Wind direction as compass direction
    var windDirectionCompass: String {
        let directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                         "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let index = Int((Double(windDirection) + 11.25) / 22.5) % 16
        return directions[index]
    }

    /// Creates a snapshot from hourly data at a specific index
    static func from(hourly: HourlyWeather, at index: Int) -> HourlyWeatherSnapshot? {
        guard index >= 0 && index < hourly.time.count else { return nil }

        // Extract hour from time string (format: "2024-01-15T14:00")
        let timeString = hourly.time[index]
        let hour = Int(timeString.suffix(5).prefix(2)) ?? 12

        return HourlyWeatherSnapshot(
            hour: hour,
            temperature: hourly.temperature2m?[safe: index] ?? 20,
            feelsLike: hourly.apparentTemperature?[safe: index] ?? hourly.temperature2m?[safe: index] ?? 20,
            humidity: hourly.relativeHumidity2m?[safe: index] ?? 50,
            precipitationProbability: hourly.precipitationProbability?[safe: index] ?? 0,
            uvIndex: hourly.uvIndex?[safe: index] ?? 0,
            windSpeed: hourly.windSpeed10m?[safe: index] ?? 0,
            windDirection: hourly.windDirection10m?[safe: index] ?? 0,
            windGusts: hourly.windGusts10m?[safe: index] ?? 0,
            weatherCode: hourly.weatherCode?[safe: index] ?? 0
        )
    }
}

/// Safe array subscript
extension Array {
    subscript(safe index: Int) -> Element? {
        guard index >= 0 && index < count else { return nil }
        return self[index]
    }
}

enum WeatherCondition: String, Codable {
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

    /// Short description for weather pills
    var shortDescription: String {
        switch self {
        case .clear: return "Clear"
        case .partlyCloudy: return "Partly Cloudy"
        case .cloudy: return "Cloudy"
        case .foggy: return "Foggy"
        case .drizzle: return "Light Rain"
        case .rain: return "Rainy"
        case .snow: return "Snowy"
        case .thunderstorm: return "Storms"
        case .unknown: return "Unknown"
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
