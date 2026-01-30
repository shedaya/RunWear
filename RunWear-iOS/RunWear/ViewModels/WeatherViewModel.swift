import Foundation
import CoreLocation
import SwiftUI

@MainActor
class WeatherViewModel: ObservableObject {
    // MARK: - Published Properties

    @Published var weatherData: WeatherData?
    @Published var recommendation: OutfitRecommendation?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var locationName: String = "Loading..."

    // Hero image state
    @Published var heroImageUrl: String?
    @Published var fallbackImageUrl: String?
    @Published var isHeroImageLoading = false

    // Date/time selection
    @Published var selectedDate: Date = Date()
    @Published var selectedHour: Int = Calendar.current.component(.hour, from: Date())

    // Current weather snapshot for selected date/time
    @Published var selectedWeatherSnapshot: HourlyWeatherSnapshot?

    // Location source tracking
    @Published var isUsingGPS: Bool = true

    // Settings with persistence
    @Published var genderPreference: GenderPreference {
        didSet {
            UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
            Task { await reloadHeroImage() }
        }
    }

    @Published var temperatureUnit: TemperatureUnit {
        didSet {
            UserDefaults.standard.set(temperatureUnit.rawValue, forKey: "temperatureUnit")
        }
    }

    @Published var comfortLevel: ComfortLevel {
        didSet {
            UserDefaults.standard.set(comfortLevel.rawValue, forKey: "comfortLevel")
            Task { await reloadHeroImage() }
        }
    }

    // MARK: - Services

    private let weatherService = WeatherService.shared
    private let outfitService = OutfitRecommendationService.shared
    private let heroImageService = HeroImageService.shared
    private let fallbackProvider = FallbackImageProvider.shared
    private let geocoder = CLGeocoder()

    // MARK: - Private State

    private var currentLocation: CLLocation?

    // MARK: - Initialization

    init() {
        // Load saved preferences
        if let saved = UserDefaults.standard.string(forKey: "genderPreference"),
           let preference = GenderPreference(rawValue: saved) {
            self.genderPreference = preference
        } else {
            self.genderPreference = .unisex
        }

        if let saved = UserDefaults.standard.string(forKey: "temperatureUnit"),
           let unit = TemperatureUnit(rawValue: saved) {
            self.temperatureUnit = unit
        } else {
            self.temperatureUnit = .fahrenheit
        }

        if let saved = UserDefaults.standard.object(forKey: "comfortLevel") as? Int,
           let level = ComfortLevel(rawValue: saved) {
            self.comfortLevel = level
        } else {
            self.comfortLevel = .neutral
        }
    }

    // MARK: - Public Methods

    func fetchWeather(for location: CLLocation) async {
        isLoading = true
        errorMessage = nil
        currentLocation = location

        do {
            let weather = try await weatherService.fetchWeather(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude
            )
            weatherData = weather

            // Get current hour snapshot or use current weather
            updateSelectedWeatherSnapshot()

            // Generate recommendation based on selected weather
            let temp = selectedWeatherSnapshot?.temperatureFahrenheit ?? weather.currentWeather.temperatureFahrenheit
            let condition = selectedWeatherSnapshot?.condition ?? weather.currentWeather.condition

            recommendation = outfitService.getRecommendation(
                for: comfortLevel.adjustTemperature(temp),
                condition: condition
            )

            await reverseGeocode(location: location)

            // Load hero image
            await loadHeroImage()

        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func refresh(location: CLLocation?) async {
        guard let location = location else {
            errorMessage = "Location not available"
            return
        }
        await fetchWeather(for: location)
    }

    func setManualLocation(coordinate: CLLocationCoordinate2D, name: String) {
        isUsingGPS = false
        locationName = name
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        Task {
            await fetchWeather(for: location)
        }
    }

    func switchToGPS() {
        isUsingGPS = true
    }

    func updateSelectedTime(date: Date, hour: Int) {
        selectedDate = date
        selectedHour = hour
        updateSelectedWeatherSnapshot()

        // Update recommendation for new time
        if let snapshot = selectedWeatherSnapshot {
            let adjustedTemp = comfortLevel.adjustTemperature(snapshot.temperatureFahrenheit)
            recommendation = outfitService.getRecommendation(
                for: adjustedTemp,
                condition: snapshot.condition
            )
        }

        // Reload hero image for new conditions
        Task { await loadHeroImage() }
    }

    // MARK: - Private Methods

    private func updateSelectedWeatherSnapshot() {
        guard let hourly = weatherData?.hourly else {
            selectedWeatherSnapshot = nil
            return
        }

        // Find the index for selected date/hour
        let calendar = Calendar.current
        let targetComponents = calendar.dateComponents([.year, .month, .day], from: selectedDate)

        for (index, timeString) in hourly.time.enumerated() {
            // Parse time string (format: "2024-01-15T14:00")
            if let date = ISO8601DateFormatter().date(from: timeString + ":00+00:00") {
                let components = calendar.dateComponents([.year, .month, .day, .hour], from: date)
                if components.year == targetComponents.year &&
                    components.month == targetComponents.month &&
                    components.day == targetComponents.day &&
                    components.hour == selectedHour {
                    selectedWeatherSnapshot = HourlyWeatherSnapshot.from(hourly: hourly, at: index)
                    return
                }
            }
        }

        // Fallback to current weather if specific hour not found
        if let current = weatherData?.currentWeather {
            selectedWeatherSnapshot = HourlyWeatherSnapshot(
                hour: selectedHour,
                temperature: current.temperature,
                feelsLike: current.temperature, // Approximation
                humidity: 50,
                precipitationProbability: 0,
                uvIndex: 0,
                windSpeed: current.windspeed,
                windDirection: 0,
                windGusts: current.windspeed,
                weatherCode: current.weathercode
            )
        }
    }

    private func loadHeroImage() async {
        isHeroImageLoading = true

        // Determine conditions for hero image
        let feelsLike: Double
        let weatherCode: Int
        let hour: Int

        if let snapshot = selectedWeatherSnapshot {
            feelsLike = comfortLevel.adjustTemperature(snapshot.feelsLikeFahrenheit)
            weatherCode = snapshot.weatherCode
            hour = snapshot.hour
        } else if let current = weatherData?.currentWeather {
            feelsLike = comfortLevel.adjustTemperature(current.temperatureFahrenheit)
            weatherCode = current.weathercode
            hour = Calendar.current.component(.hour, from: Date())
        } else {
            isHeroImageLoading = false
            return
        }

        // Set fallback image immediately (zero-lag)
        let tempBracket = HeroTempBracket.from(feelsLikeTemperature: feelsLike)
        let weatherCondition = HeroWeatherCondition.from(weatherCode: weatherCode)
        fallbackImageUrl = fallbackProvider.getImageURL(temp: tempBracket, weather: weatherCondition, size: .iOS)

        // Fetch AI hero image
        let result = await heroImageService.getHeroImage(
            gender: genderPreference,
            weatherCode: weatherCode,
            feelsLikeTemp: feelsLike,
            hour: hour,
            isWatch: false
        )

        // Only set hero image if it's from Supabase (not Unsplash fallback)
        if result.fallbackLevel < 6 {
            heroImageUrl = result.imageUrl
        } else {
            heroImageUrl = nil // Will use fallback
        }

        isHeroImageLoading = false
    }

    private func reloadHeroImage() async {
        await loadHeroImage()
    }

    private func reverseGeocode(location: CLLocation) async {
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            if let placemark = placemarks.first {
                let city = placemark.locality ?? ""
                let state = placemark.administrativeArea ?? ""
                locationName = [city, state].filter { !$0.isEmpty }.joined(separator: ", ")
                if locationName.isEmpty {
                    locationName = "Current Location"
                }
            }
        } catch {
            locationName = "Current Location"
        }
    }

    // MARK: - Computed Properties

    var currentTemperature: Double {
        if let snapshot = selectedWeatherSnapshot {
            return temperatureUnit == .fahrenheit ? snapshot.temperatureFahrenheit : snapshot.temperature
        } else if let current = weatherData?.currentWeather {
            return temperatureUnit == .fahrenheit ? current.temperatureFahrenheit : current.temperature
        }
        return 0
    }

    var currentFeelsLike: Double {
        if let snapshot = selectedWeatherSnapshot {
            let base = temperatureUnit == .fahrenheit ? snapshot.feelsLikeFahrenheit : snapshot.feelsLike
            return base + (temperatureUnit == .fahrenheit ? comfortLevel.temperatureAdjustment : comfortLevel.temperatureAdjustment * 5 / 9)
        } else if let current = weatherData?.currentWeather {
            let base = temperatureUnit == .fahrenheit ? current.temperatureFahrenheit : current.temperature
            return base + (temperatureUnit == .fahrenheit ? comfortLevel.temperatureAdjustment : comfortLevel.temperatureAdjustment * 5 / 9)
        }
        return 0
    }

    var currentCondition: WeatherCondition {
        selectedWeatherSnapshot?.condition ?? weatherData?.currentWeather.condition ?? .unknown
    }

    var currentTempBracket: HeroTempBracket {
        let feelsLike = selectedWeatherSnapshot?.feelsLikeFahrenheit ?? weatherData?.currentWeather.temperatureFahrenheit ?? 60
        return HeroTempBracket.from(feelsLikeTemperature: comfortLevel.adjustTemperature(feelsLike))
    }
}
