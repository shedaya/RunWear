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
            // A comfort change re-runs the engine through the same
            // assembly as every other path (Android re-runs its use
            // case on a comfort change too) — the outfit must not stay
            // stale until the next fetch.
            makeRecommendation()
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

            // Select the snapshot for the chosen date/hour, then run the
            // ONE engine-call assembly (see makeRecommendation).
            updateSelectedWeatherSnapshot()
            makeRecommendation()

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

        // A time change re-runs the FULL input set through the same
        // assembly as the initial load — before Stage 1 this path
        // passed only temperature + condition, so wind, humidity, UV
        // and precipitation silently fell back to the engine defaults
        // (0 / 50 / 0 / 0) on every re-run.
        makeRecommendation()

        // Reload hero image for new conditions
        Task { await loadHeroImage() }
    }

    /// THE engine-call assembly — the single construction every path
    /// (initial load, time change, comfort change) runs, mirroring
    /// Android's MainViewModel → GetOutfitRecommendationUseCase
    /// boundary: feels-like in both frames (comfort-adjusted for the
    /// warmth selectors, raw for the sun gates), measured rain as the
    /// rain signal (rain > 0, never the weather-code class), wind in
    /// mph, precipitation in mm, and the selected hour. Internal (not
    /// private) so the boundary fixtures in RunWearTests drive this
    /// exact production construction.
    func makeRecommendation() {
        guard let snapshot = selectedWeatherSnapshot else {
            recommendation = nil
            return
        }

        let feelsLike = snapshot.feelsLikeFahrenheit
        recommendation = outfitService.getRecommendation(
            for: comfortLevel.adjustTemperature(feelsLike),
            rawTemperature: feelsLike,
            isRaining: snapshot.rain > 0,
            windSpeed: snapshot.windSpeed,
            humidity: snapshot.humidity,
            uvIndex: snapshot.uvIndex,
            precipitation: snapshot.precipitation,
            hour: snapshot.hour
        )
    }

    /// Reset to current date and time
    func resetToNow() {
        let now = Date()
        let currentHour = Calendar.current.component(.hour, from: now)
        updateSelectedTime(date: now, hour: currentHour)
    }

    // MARK: - Private Methods

    /// Selects the hourly snapshot for the chosen date/hour. Internal
    /// (not private) so the boundary fixtures exercise the production
    /// selection, not a copy of it.
    func updateSelectedWeatherSnapshot() {
        guard let weatherData else {
            selectedWeatherSnapshot = nil
            return
        }

        if let hourly = weatherData.hourly {
            // Hourly timestamps are LOCATION-local wall times
            // (timezone=auto). Android matches the "yyyy-MM-dd'T'HH"
            // prefix textually; do the same. The pre-Stage-1 code
            // parsed the strings as GMT and compared device-local
            // calendar components, which shifted the selected hour by
            // the viewer's UTC offset whenever the location's zone
            // differed from the device's.
            let components = Calendar.current.dateComponents([.year, .month, .day], from: selectedDate)
            let prefix = String(
                format: "%04d-%02d-%02dT%02d",
                components.year ?? 0, components.month ?? 0, components.day ?? 0, selectedHour
            )
            if let index = hourly.time.firstIndex(where: { $0.hasPrefix(prefix) }),
               let snapshot = HourlyWeatherSnapshot.from(hourly: hourly, at: index) {
                selectedWeatherSnapshot = snapshot
                return
            }
        }

        // Selected hour outside the forecast window: fall back to the
        // current-conditions snapshot (real measured values from the
        // extended current block — see HourlyWeatherSnapshot.init).
        selectedWeatherSnapshot = HourlyWeatherSnapshot(
            hour: selectedHour,
            current: weatherData.currentWeather,
            detail: weatherData.currentDetail
        )
    }

    private func loadHeroImage() async {
        isHeroImageLoading = true

        // Determine conditions for hero image. The hero combination is
        // built from the RAW feels-like (Android's HeroImageRepository
        // takes the unadjusted frame): the comfort preference must not
        // move the hero's temperature bracket the way it moves the
        // outfit's warmth selectors.
        let feelsLike: Double
        let weatherCode: Int
        let hour: Int

        if let snapshot = selectedWeatherSnapshot {
            feelsLike = snapshot.feelsLikeFahrenheit
            weatherCode = snapshot.weatherCode
            hour = snapshot.hour
        } else if let current = weatherData?.currentWeather {
            feelsLike = weatherData?.currentDetail?.apparentTemperature.map { $0 * 9 / 5 + 32 }
                ?? current.temperatureFahrenheit
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

        // Fetch AI hero image — with the actual outfit, so a
        // replenishment prompt describes what the engine recommended
        // (Android passes its outfit the same way); without it the
        // service falls back to static per-bracket descriptions.
        let result = await heroImageService.getHeroImage(
            gender: genderPreference,
            weatherCode: weatherCode,
            feelsLikeTemp: feelsLike,
            hour: hour,
            isWatch: false,
            outfit: recommendation
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
