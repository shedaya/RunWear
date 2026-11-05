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
    /// Internal (not private) so the RW-67 render fixtures can
    /// seat a measured weather value and render the production
    /// glance page — the WeatherViewModel precedent
    /// (updateSelectedWeatherSnapshot is internal for the same
    /// reason). Production writes it only in fetchWeather.
    var currentWeather: WeatherData?
    /// GEN-5 (RW-29 FA-56): the measured RAW feels-like (°F) from
    /// the last fetch. The glance does NOT render this value
    /// directly — GEN-6 (DA-14) it renders the comfort-adjusted
    /// figure derived from it (see `displayTemperature`).
    private(set) var currentFeelsLikeF: Double?

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
           let level = ComfortLevel.fromStoredRawValue(saved) {
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
            // GEN-5 (RW-29 FA-15 / EX-20): authored words.
            state = .error("Location isn't available.")
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

            // RW-23: the watch keys BOTH its recommendation and its
            // hero on the comfort-adjusted FEELS-LIKE — the same
            // frame the phone uses. Before RW-23 it keyed on the
            // comfort-adjusted ACTUAL temperature, a third frame, so
            // watch and phone could recommend/serve different
            // outfits for the same conditions. The feels-like comes
            // from the extended current block (°C → °F, the same
            // expression the phone's prepareHeroFrame fallback uses),
            // falling back to the actual temperature when absent.
            let rawFeelsLikeF = weather.currentDetail?.apparentTemperature.map { $0 * 9 / 5 + 32 }
                ?? weather.currentWeather.temperatureFahrenheit
            currentFeelsLikeF = rawFeelsLikeF
            let adjustedTemp = comfortLevel.adjustTemperature(rawFeelsLikeF)
            // The watch reads the current-conditions block only (no
            // hourly snapshot), so its rain signal stays the condition
            // class of the current code — the phone derives isRaining
            // from measured hourly rain. Wind now arrives in mph from
            // the shared WeatherService, so the engine's > 10 mph gate
            // reads correctly here too.
            let currentCondition = weather.currentWeather.condition
            let recommendation = outfitService.getRecommendation(
                for: adjustedTemp,
                rawTemperature: rawFeelsLikeF,
                isRaining: currentCondition == .rain || currentCondition == .drizzle || currentCondition == .thunderstorm,
                windSpeed: weather.currentWeather.windspeed,
                hour: Calendar.current.component(.hour, from: Date())
            )

            state = .loaded(weather, recommendation)
            await reverseGeocode(location: location)

            // Load hero image
            await loadHeroImage()
        } catch {
            // GEN-5 (RW-29 FA-15 / EX-20): authored words, never
            // raw system error text (design-spec §17).
            state = .error("Couldn't reach the weather service.")
        }
    }

    private func loadHeroImage() async {
        guard let weather = currentWeather else { return }

        // RW-23: same feels-like frame as fetchWeather (see its
        // note) — the hero is keyed on the recommendation's frame,
        // never on the actual temperature.
        let rawFeelsLikeF = weather.currentDetail?.apparentTemperature.map { $0 * 9 / 5 + 32 }
            ?? weather.currentWeather.temperatureFahrenheit
        let adjustedTemp = comfortLevel.adjustTemperature(rawFeelsLikeF)
        let weatherCode = weather.currentWeather.weathercode
        let hour = Calendar.current.component(.hour, from: Date())

        // Calculate temp bracket
        currentTempBracket = HeroTempBracket.from(feelsLikeTemperature: adjustedTemp)
        let weatherCondition = HeroWeatherCondition.from(weatherCode: weatherCode)

        // Set fallback immediately for zero-lag
        // WS-6 (D-20, watch): the pre-paint fallback is resolved WITH
        // the user's fit preference — the same deterministic mapping
        // the phone passes (GenderPreference.fallbackHeroGender);
        // unisex passes nil and keeps the legacy chain.
        fallbackImageUrl = fallbackProvider.getImageURL(
            temp: currentTempBracket,
            weather: weatherCondition,
            size: .watchOS,
            gender: genderPreference.fallbackHeroGender
        )

        // Fetch AI hero image — with the loaded recommendation, so the
        // RW-20 point-grain gate can judge pool photos against the
        // exact prescription (the phone passes its outfit the same
        // way; without one the service serves no pool row).
        let outfit: OutfitRecommendation?
        if case .loaded(_, let recommendation) = state {
            outfit = recommendation
        } else {
            outfit = nil
        }
        let result = await heroImageService.getHeroImage(
            gender: genderPreference,
            weatherCode: weatherCode,
            feelsLikeTemp: adjustedTemp,
            hour: hour,
            isWatch: true,
            outfit: outfit
        )

        // Only use AI image if not Unsplash fallback
        if result.fallbackLevel < 8 {
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

    /// GEN-6 (DA-14, design-spec §0.4/§17 one-frame rule): the
    /// glance number is the COMFORT-ADJUSTED feels-like — the
    /// number the outfit is priced off, because the
    /// recommendation in `fetchWeather` keys on
    /// `comfortLevel.adjustTemperature(rawFeelsLikeF)`. Rendering
    /// the raw feels-like here (the gen-5 code) made the watch
    /// numeral disagree with the phone's and with its own outfit
    /// whenever the comfort adjustment was non-zero. Pure and
    /// static so the gate pins the adjustment without a host.
    static func glanceTemperatureString(
        rawFeelsLikeF: Double,
        comfortLevel: ComfortLevel,
        unit: TemperatureUnit
    ) -> String {
        let adjustedF = comfortLevel.adjustTemperature(rawFeelsLikeF)
        let temp = unit == .fahrenheit
            ? adjustedF
            : (adjustedF - 32) * 5 / 9
        return "\(Int(round(temp)))°"
    }

    var displayTemperature: String {
        guard let weather = currentWeather else { return "--°" }
        // GEN-6 (DA-14): the glance renders the comfort-adjusted
        // feels-like (see glanceTemperatureString). Falls back to
        // the actual temperature as the raw surrogate only when
        // no feels-like was measured — the same surrogate the
        // fetch path uses, adjusted the same way.
        let rawFeelsLikeF = currentFeelsLikeF ?? weather.currentWeather.temperatureFahrenheit
        return Self.glanceTemperatureString(
            rawFeelsLikeF: rawFeelsLikeF,
            comfortLevel: comfortLevel,
            unit: temperatureUnit
        )
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
            // GEN-5 (RW-29 FA-15 / EX-20): authored words, never
            // raw system error text (design-spec §17).
            state = .error("Couldn't get your location.")
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            checkLocationAuthorization()
        }
    }
}
