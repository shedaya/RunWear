import Foundation
import CoreLocation
import SwiftUI

/// Persistence for the user's manually chosen location — the iOS
/// counterpart of Android's PreferencesRepository.SavedLocation,
/// which the Android app (and its widget) read first on launch.
/// iOS kept the choice only in memory: a relaunch silently returned
/// to GPS, and the next GPS fix overwrote the manual location's
/// weather (parity register R-19).
enum ManualLocationStore {
    struct Saved: Equatable {
        let latitude: Double
        let longitude: Double
        let name: String
    }

    private static let latitudeKey = "manualLocation.latitude"
    private static let longitudeKey = "manualLocation.longitude"
    private static let nameKey = "manualLocation.name"

    static func save(_ saved: Saved, to defaults: UserDefaults = .standard) {
        defaults.set(saved.latitude, forKey: latitudeKey)
        defaults.set(saved.longitude, forKey: longitudeKey)
        defaults.set(saved.name, forKey: nameKey)
    }

    static func load(from defaults: UserDefaults = .standard) -> Saved? {
        // object(forKey:) distinguishes "never saved" from a saved
        // coordinate on the equator / prime meridian (0.0).
        guard defaults.object(forKey: latitudeKey) != nil,
              defaults.object(forKey: longitudeKey) != nil else {
            return nil
        }
        return Saved(
            latitude: defaults.double(forKey: latitudeKey),
            longitude: defaults.double(forKey: longitudeKey),
            name: defaults.string(forKey: nameKey) ?? ""
        )
    }

    static func clear(from defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: latitudeKey)
        defaults.removeObject(forKey: longitudeKey)
        defaults.removeObject(forKey: nameKey)
    }
}

/// What the authorized home screen shows, resolved in one place so
/// the precedence is pinnable (RW-6.2): CONTENT outranks the error —
/// a failed pull-to-refresh must never replace a good outfit with a
/// full-screen error. The error owns the screen only when there is
/// nothing to show; an error arriving WITH content is demoted to a
/// non-destructive banner over the outfit (WeatherOutfitView reads
/// the same flag). Loading is what remains: no content, no error.
enum HomePresentation: Equatable {
    case loading
    case content(showRefreshError: Bool)
    case error(String)

    static func resolve(hasContent: Bool, errorMessage: String?) -> HomePresentation {
        if hasContent {
            return .content(showRefreshError: errorMessage != nil)
        }
        if let errorMessage {
            return .error(errorMessage)
        }
        return .loading
    }
}

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

    // Identity of the hero currently resolved, for the flag action
    // (mirrors Android's MainUiState hero identity, 2026-10-29): the
    // served pool row's id and its own combination, kept alongside
    // the URL so a flag names the exact photo on screen.
    @Published var heroImageRowId: String?
    @Published var heroServedCombinationId: String?
    private var heroRequestedCombinationId: String?

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

    /// The manual location restored from persistence at launch, if
    /// any. ContentView fetches weather for it on appear instead of
    /// requesting a GPS fix (Android's saved-location-first launch).
    /// Cleared when the user switches back to GPS.
    private(set) var restoredManualLocation: CLLocation?

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
            self.temperatureUnit = .defaultForLocale
        }

        if let saved = UserDefaults.standard.object(forKey: "comfortLevel") as? Int,
           let level = ComfortLevel.fromStoredRawValue(saved) {
            self.comfortLevel = level
        } else {
            self.comfortLevel = .neutral
        }

        // Restore a saved manual location (R-19): the app launches
        // on the user's chosen place, not on GPS, until they switch
        // back — the Android saved-location contract.
        if let saved = ManualLocationStore.load() {
            self.isUsingGPS = false
            self.locationName = saved.name
            let location = CLLocation(latitude: saved.latitude, longitude: saved.longitude)
            self.currentLocation = location
            self.restoredManualLocation = location
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
        // Persist the choice (R-19): it survives relaunch and is
        // what the next launch restores.
        ManualLocationStore.save(
            ManualLocationStore.Saved(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                name: name
            )
        )
        restoredManualLocation = location
        Task {
            await fetchWeather(for: location)
        }
    }

    func switchToGPS() {
        isUsingGPS = true
        // Back to GPS: the saved manual location no longer governs
        // launch or refresh.
        ManualLocationStore.clear()
        restoredManualLocation = nil
    }

    /// Re-fetch weather for the location currently in use (the
    /// manual location in manual mode). Refresh and error-retry in
    /// manual mode must not silently substitute a GPS fix.
    func refreshCurrent() async {
        guard let currentLocation else {
            errorMessage = "Location not available"
            return
        }
        await fetchWeather(for: currentLocation)
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

        // The combination this screen asks the pool for, built from
        // the same raw inputs the service call below uses (the
        // service rebuilds it internally from identical inputs).
        // Recorded so a hero-photo flag carries the requested cell
        // as context when the served photo has no cell of its own
        // (the curated fallback).
        let requestedCombination = HeroCombinationId(
            gender: genderPreference.forHeroImage(),
            weather: HeroWeatherCondition.from(weatherCode: weatherCode),
            temp: HeroTempBracket.from(feelsLikeTemperature: feelsLike),
            time: HeroTimeOfDay.from(hour: hour),
            variant: nil
        )
        heroRequestedCombinationId = requestedCombination.fullId

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
            heroImageRowId = result.imageRowId
            heroServedCombinationId = result.combinationId
        } else {
            heroImageUrl = nil // Will use fallback
            heroImageRowId = nil
            heroServedCombinationId = nil
        }

        isHeroImageLoading = false
    }

    /// The photo a flag would report right now: the pool photo while
    /// one is displayed (carrying its served row identity), otherwise
    /// the curated fallback, which is identified by URL alone. Nil
    /// before the first hero resolution — the views show no flag
    /// chip then.
    var currentHeroFlagTarget: HeroFlagTarget? {
        if let url = heroImageUrl {
            return HeroFlagTarget(
                imageUrl: url,
                imageRowId: heroImageRowId,
                servedCombinationId: heroServedCombinationId,
                requestedCombinationId: heroRequestedCombinationId
            )
        }
        if let url = fallbackImageUrl {
            return HeroFlagTarget(
                imageUrl: url,
                imageRowId: nil,
                servedCombinationId: nil,
                requestedCombinationId: heroRequestedCombinationId
            )
        }
        return nil
    }

    /// Reports the displayed hero photo with the given reason.
    /// Fire-and-forget: the send is best-effort, retried once, and
    /// never surfaces an error or disturbs the run screen.
    func flagCurrentHero(reason: HeroFlagReason) {
        guard let target = currentHeroFlagTarget else { return }
        HeroFlagService.flag(target: target, reason: reason)
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

    /// The measured feels-like for display — RAW, in the selected
    /// unit. The hero headline on Android and the PWA is the raw
    /// feels-like (Kotlin: effectiveTemperature == feelsLike), and
    /// their "Actual:" line is the raw actual temperature. This
    /// property previously added the comfort adjustment on top of
    /// the measured value, so the iOS "Feels like" line showed a
    /// number that existed on neither reference surface (and the
    /// hero headline showed the actual temperature instead — see
    /// WeatherOutfitView's HeroImageView call). The comfort
    /// adjustment belongs to the engine's warmth frame
    /// (makeRecommendation), never to a displayed measurement.
    var currentFeelsLike: Double {
        if let snapshot = selectedWeatherSnapshot {
            return temperatureUnit == .fahrenheit ? snapshot.feelsLikeFahrenheit : snapshot.feelsLike
        } else if let current = weatherData?.currentWeather {
            let feelsC = weatherData?.currentDetail?.apparentTemperature ?? current.temperature
            return temperatureUnit == .fahrenheit ? feelsC * 9 / 5 + 32 : feelsC
        }
        return 0
    }

    var currentCondition: WeatherCondition {
        selectedWeatherSnapshot?.condition ?? weatherData?.currentWeather.condition ?? .unknown
    }

    /// The RAW measured feels-like in °F, regardless of the display
    /// unit — the frame the share text's bracket is taken from
    /// (Android composes its share bracket from the raw feels-like,
    /// not from the comfort-adjusted engine frame).
    private var rawFeelsLikeFahrenheit: Double {
        if let snapshot = selectedWeatherSnapshot {
            return snapshot.feelsLikeFahrenheit
        }
        if let current = weatherData?.currentWeather {
            let feelsC = weatherData?.currentDetail?.apparentTemperature ?? current.temperature
            return feelsC * 9 / 5 + 32
        }
        return 0
    }

    /// The share payload's text (RW-6.5) — the aligned composition
    /// Android and the PWA share verbatim in layout: a feels-like
    /// headline (the RAW measured feels-like the hero shows — never
    /// the actual temperature, never comfort-adjusted), the
    /// condition, the outfit itemized, the first tip, and the
    /// runwear.ai link. (The hand-rolled share it replaces quoted
    /// the ACTUAL temperature and linked runwear.app.) A view-model
    /// computation so the test target pins the composition without
    /// a view host. Nil until a recommendation exists.
    var shareText: String? {
        guard let recommendation else { return nil }
        let unit = temperatureUnit == .fahrenheit ? "F" : "C"
        var lines: [String] = [
            "🏃 RunWear Recommendation",
            "",
            "📍 \(locationName)",
            "🌡️ Feels like \(Int(currentFeelsLike))°\(unit) (\(TemperatureBracket.from(temperature: rawFeelsLikeFahrenheit).description))",
            currentCondition.shortDescription,
            "",
            "What to wear:"
        ]
        lines += recommendation.allItems.map { "• \($0.name)" }
        lines.append("")
        if let tip = recommendation.tips.first {
            lines.append("💡 Tip: \(tip)")
            lines.append("")
        }
        lines.append("Get your personalized running outfit at runwear.ai")
        return lines.joined(separator: "\n")
    }

    var currentTempBracket: HeroTempBracket {
        let feelsLike = selectedWeatherSnapshot?.feelsLikeFahrenheit ?? weatherData?.currentWeather.temperatureFahrenheit ?? 60
        return HeroTempBracket.from(feelsLikeTemperature: comfortLevel.adjustTemperature(feelsLike))
    }
}
