import Foundation
import CoreLocation
import SwiftUI

enum WatchViewState {
    case loading
    case permissionNeeded
    case error(String)
    case loaded(WeatherData, OutfitRecommendation)
}

@MainActor
class WatchViewModel: NSObject, ObservableObject {
    @Published var state: WatchViewState = .loading
    @Published var locationName: String = "Loading..."

    // Hero image state
    @Published var heroImageUrl: String?
    @Published var fallbackImageUrl: String?
    @Published var currentTempBracket: HeroTempBracket = .MILD

    // Settings synced from iOS
    @Published var genderPreference: GenderPreference = .unisex {
        didSet {
            UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
            Task { await reloadHeroImage() }
        }
    }

    @Published var temperatureUnit: TemperatureUnit = .fahrenheit {
        didSet {
            UserDefaults.standard.set(temperatureUnit.rawValue, forKey: "temperatureUnit")
        }
    }

    @Published var comfortLevel: ComfortLevel = .neutral {
        didSet {
            UserDefaults.standard.set(comfortLevel.rawValue, forKey: "comfortLevel")
            Task { await reloadHeroImage() }
        }
    }

    private let locationManager = CLLocationManager()
    private let weatherService = WeatherService.shared
    private let outfitService = OutfitRecommendationService.shared
    private let heroImageService = HeroImageService.shared
    private let fallbackProvider = FallbackImageProvider.shared
    private let geocoder = CLGeocoder()

    private var currentLocation: CLLocation?
    private var currentWeather: WeatherData?

    override init() {
        super.init()
        loadSavedPreferences()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    private func loadSavedPreferences() {
        if let saved = UserDefaults.standard.string(forKey: "genderPreference"),
           let preference = GenderPreference(rawValue: saved) {
            genderPreference = preference
        }

        if let saved = UserDefaults.standard.string(forKey: "temperatureUnit"),
           let unit = TemperatureUnit(rawValue: saved) {
            temperatureUnit = unit
        }

        if let saved = UserDefaults.standard.object(forKey: "comfortLevel") as? Int,
           let level = ComfortLevel(rawValue: saved) {
            comfortLevel = level
        }
    }

    func onAppear() {
        checkLocationAuthorization()
    }

    func requestLocation() {
        locationManager.requestWhenInUseAuthorization()
    }

    func refresh() {
        if let location = currentLocation {
            Task {
                await fetchWeather(for: location)
            }
        } else {
            locationManager.requestLocation()
        }
    }

    func toggleUnit() {
        temperatureUnit = temperatureUnit == .fahrenheit ? .celsius : .fahrenheit
    }

    private func checkLocationAuthorization() {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            state = .permissionNeeded
        case .restricted, .denied:
            state = .error("Location access denied. Enable in Settings.")
        case .authorizedWhenInUse, .authorizedAlways:
            state = .loading
            locationManager.requestLocation()
        @unknown default:
            state = .error("Unknown location status")
        }
    }

    private func fetchWeather(for location: CLLocation) async {
        state = .loading

        do {
            let weather = try await weatherService.fetchWeather(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude
            )

            currentWeather = weather

            let adjustedTemp = comfortLevel.adjustTemperature(weather.currentWeather.temperatureFahrenheit)
            let recommendation = outfitService.getRecommendation(
                for: adjustedTemp,
                condition: weather.currentWeather.condition,
                windSpeed: weather.currentWeather.windspeed,
                hour: Calendar.current.component(.hour, from: Date())
            )

            state = .loaded(weather, recommendation)
            await reverseGeocode(location: location)

            // Load hero image
            await loadHeroImage()
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    private func loadHeroImage() async {
        guard let weather = currentWeather else { return }

        let adjustedTemp = comfortLevel.adjustTemperature(weather.currentWeather.temperatureFahrenheit)
        let weatherCode = weather.currentWeather.weathercode
        let hour = Calendar.current.component(.hour, from: Date())

        // Calculate temp bracket
        currentTempBracket = HeroTempBracket.from(feelsLikeTemperature: adjustedTemp)
        let weatherCondition = HeroWeatherCondition.from(weatherCode: weatherCode)

        // Set fallback immediately for zero-lag
        fallbackImageUrl = fallbackProvider.getImageURL(
            temp: currentTempBracket,
            weather: weatherCondition,
            size: .watchOS
        )

        // Fetch AI hero image
        let result = await heroImageService.getHeroImage(
            gender: genderPreference,
            weatherCode: weatherCode,
            feelsLikeTemp: adjustedTemp,
            hour: hour,
            isWatch: true
        )

        // Only use AI image if not Unsplash fallback
        if result.fallbackLevel < 6 {
            heroImageUrl = result.imageUrl
        } else {
            heroImageUrl = nil
        }
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

    var displayTemperature: String {
        guard let weather = currentWeather else { return "--°" }
        let temp = temperatureUnit == .fahrenheit
            ? weather.currentWeather.temperatureFahrenheit
            : weather.currentWeather.temperature
        return "\(Int(round(temp)))°"
    }
}

extension WatchViewModel: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.first else { return }
        Task { @MainActor in
            currentLocation = location
            await fetchWeather(for: location)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            state = .error("Could not get location: \(error.localizedDescription)")
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            checkLocationAuthorization()
        }
    }
}
