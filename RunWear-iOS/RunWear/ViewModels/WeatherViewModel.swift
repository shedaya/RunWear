import Foundation
import CoreLocation
import SwiftUI
import WidgetKit

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
        // The persisted name is a rendered place line (WS-4): it
        // is stored in the simple form, so a raw geocode string
        // never survives a relaunch into the header.
        defaults.set(
            SimplePlace(rawDisplayName: saved.name)?.displayName ?? saved.name,
            forKey: nameKey
        )
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

/// GEN-3 (WS-B, census X-7): may the hero URLs currently resolved
/// paint under the place the UI is labelling? The hero's resolved
/// URLs belong to the place whose fetch committed them
/// (`heroOwnerPlaceKey`); on a place switch the label moves first
/// (the selection writes it at tap time), so until the new place's
/// own fetch commits its hero resolution, the gate is CLOSED and
/// the hero zone shows its skeleton — the previous place's photo
/// never renders under the new place's name. Pure so the binding
/// is pinned without a view host.
enum HeroDisplayGate {
    static func mayPaint(ownerPlaceKey: String?, displayedPlaceKey: String) -> Bool {
        guard let ownerPlaceKey else { return false }
        return ownerPlaceKey == displayedPlaceKey
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

    /// The current place as the app's ONE place value (WS-4 /
    /// RC-4): derived once at geocode time (or at manual
    /// selection) from the placemark's city + region, and the
    /// source every place-bearing surface renders from via
    /// `placeDisplayName`. Nil before the first geocode lands
    /// and in the non-place states ("Loading...", "Current
    /// Location"), which are labels, not places.
    @Published private(set) var simplePlace: SimplePlace?

    /// The rendered form of the current place: the SimplePlace's
    /// display name when one is derived; otherwise the stored
    /// string routed through the model (a direct assignment in a
    /// test or a legacy restore still cannot put a raw geocode
    /// string on a rendered line); otherwise the string itself
    /// (the non-place labels above).
    var placeDisplayName: String {
        let resolved: String
        if let simplePlace {
            resolved = simplePlace.displayName
        } else if let parsed = SimplePlace(rawDisplayName: locationName) {
            resolved = parsed.displayName
        } else {
            resolved = locationName
        }
        // GEN-3 X-13: every surface that names the place in use
        // (the home header location line, the navigation title,
        // the hero) renders this value, and it NEVER renders
        // empty. Through the app's own assignment paths it cannot
        // be empty today (the initial label, the "Current
        // Location" fallback, and SimplePlace display names are
        // all non-empty) — but the guarantee was incidental: an
        // empty/whitespace `locationName` assignment would have
        // passed straight through. It now falls back to the
        // app's existing non-place label.
        return resolved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Current Location"
            : resolved
    }

    // Hero image state
    @Published var heroImageUrl: String?
    @Published var fallbackImageUrl: String?
    @Published var isHeroImageLoading = false

    /// GEN-3 (WS-B / X-7): the place key the currently resolved
    /// hero URLs belong to — written in the SAME state write that
    /// commits a fetch's fallback (loadHeroImage's atomic commit),
    /// and read by the view through `heroBelongsToDisplayedPlace`.
    /// Nil until the first hero resolution commits.
    @Published private(set) var heroOwnerPlaceKey: String?

    /// GEN-4: the fallback-chain tier the currently committed
    /// fallback resolution landed at (exact / scaffold /
    /// bundled-floor), and the bundled asset it named, when the
    /// paint is a bundled one. Written in the same atomic commit
    /// as fallbackImageUrl; a scaffold paint is labelled by this
    /// tier on every surface that reads the resolution — it is
    /// never presented as the exact photo.
    @Published private(set) var heroFallbackTier: HeroFallbackTier?
    @Published private(set) var heroFallbackAssetName: String?

    /// Whether the resolved hero may paint under the place the UI
    /// currently labels (HeroDisplayGate). False during a place
    /// switch's in-flight window — the hero zone skeletons rather
    /// than showing the previous place's photo (X-7).
    var heroBelongsToDisplayedPlace: Bool {
        HeroDisplayGate.mayPaint(
            ownerPlaceKey: heroOwnerPlaceKey,
            displayedPlaceKey: placeDisplayName
        )
    }

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

    /// GEN-4: the first-class NOW state, made explicit on iOS.
    /// It was previously only derivable (selected date is today
    /// and selected hour is the current hour), which cannot
    /// distinguish "tracking now" from "explicitly picked the
    /// current hour" — the Choose-a-Time draft rule needs the
    /// distinction (confirming an unmoved draft opened in NOW
    /// keeps NOW; any other confirm is an explicit pick). True
    /// at launch and after resetToNow; every explicit date/time
    /// pick clears it. (Android's MainViewModel carries the same
    /// state as isNowSelected.)
    @Published private(set) var isNowSelection = true

    // Current weather snapshot for selected date/time
    @Published var selectedWeatherSnapshot: HourlyWeatherSnapshot?

    // RW-23: true when the selected date/hour lies outside the
    // hourly forecast window, so selectedWeatherSnapshot is the
    // current-conditions fallback rather than that time's forecast.
    // Before RW-23 the substitution was silent — the screen showed
    // current conditions labelled with the selected hour and nothing
    // said so. The view surfaces a notice whenever this is true.
    @Published var selectedTimeOutsideForecast = false

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

    // MARK: - RW-14 personal hero layer

    /// The "Make It Me" store, wired by the hosting view (ContentView
    /// owns both objects), typed as the PersonalHeroResolving
    /// contract so this file compiles in the test target without the
    /// store's dependency stack. Weak: the store lives in the view
    /// layer and outlives this view model by construction; nil
    /// simply means the personal layer is off (stock behavior).
    weak var personalStore: (any PersonalHeroResolving)?

    /// The personal cell the current screen is keyed on, when the
    /// user has opted in — Settings reads it for the photo-replace
    /// regeneration, and the upsell/progress surfaces key on it.
    @Published private(set) var currentPersonalCombination: PersonalCombination?

    /// The backend id of the personalized hero currently ON SCREEN,
    /// when the hero slot is showing one — nil whenever the pool or
    /// fallback photo is up. Drives the personal flag surface
    /// ("This doesn't look like me", RW-14 acceptance (19)).
    @Published private(set) var currentPersonalHeroId: String?

    /// GEN-6 Wave A (RW-34): whether the personal photo currently
    /// ON SCREEN is a SCAFFOLD serve — the user's own banked photo
    /// for nearest conditions standing in while this cell's exact
    /// look is still making (the PersonalServePolicy distinction,
    /// recorded at paint time in applyPersonalHero). The hero pill
    /// reads it: a scaffold serve is silent and never receives an
    /// exact-serve label. False whenever the exact photo (or no
    /// personal photo) is on screen.
    @Published private(set) var currentPersonalServeIsScaffold = false

    /// The in-flight wait for a personalized hero to land for the
    /// current cell; cancelled whenever the cell changes.
    private var personalAwaitTask: Task<Void, Never>?
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
            // A pre-WS-4 persisted name may be a raw geocode
            // string: it re-derives through the model at restore.
            self.simplePlace = SimplePlace(rawDisplayName: saved.name)
            self.locationName = self.simplePlace?.displayName ?? saved.name
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
            // GEN-5 (RW-29 FA-15): the error surface speaks in
            // authored words (design-spec §17 / frame 47: name
            // the failure and what is safe) — never the raw
            // system error text the build-25 code surfaced.
            errorMessage = "The weather service couldn't be reached. Your places, settings, and photos stay on this phone — nothing is lost."
            // The widget's never-succeeded card reads its
            // failure category from the same fact (Addendum 7).
            WidgetLastGoodStore.saveErrorCategory(.weather)
        }

        isLoading = false
    }

    func refresh(location: CLLocation?) async {
        guard let location = location else {
            // GEN-5 (RW-29 FA-15): authored form (frame 47's
            // pattern — the failure named, and what is safe).
            errorMessage = "Location isn't available. Your places, settings, and photos stay on this phone — nothing is lost."
            WidgetLastGoodStore.saveErrorCategory(.location)
            return
        }
        await fetchWeather(for: location)
    }

    func setManualLocation(coordinate: CLLocationCoordinate2D, name: String) {
        isUsingGPS = false
        // The manual selection is a geocode result too (WS-4):
        // the name is derived through the model once, here, and
        // only the simple form is kept or persisted.
        simplePlace = SimplePlace(rawDisplayName: name)
        locationName = simplePlace?.displayName ?? name
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        // Persist the choice (R-19): it survives relaunch and is
        // what the next launch restores.
        ManualLocationStore.save(
            ManualLocationStore.Saved(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                name: locationName
            )
        )
        restoredManualLocation = location
        Task {
            await fetchWeather(for: location)
        }
    }

    func switchToGPS() {
        isUsingGPS = true
        // The manual place no longer governs; the next GPS fix
        // re-derives the SimplePlace at geocode time.
        simplePlace = nil
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
            // GEN-5 (RW-29 FA-15): authored form, as above.
            errorMessage = "Location isn't available. Your places, settings, and photos stay on this phone — nothing is lost."
            WidgetLastGoodStore.saveErrorCategory(.location)
            return
        }
        await fetchWeather(for: currentLocation)
    }

    /// An explicit date/time pick: exits the NOW state (the
    /// long-standing setDateTime semantics the study records).
    func updateSelectedTime(date: Date, hour: Int) {
        applySelectedTime(date: date, hour: hour, nowState: false)
    }

    private func applySelectedTime(date: Date, hour: Int, nowState: Bool) {
        isNowSelection = nowState
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

    /// GEN-4: the Choose-a-Time sheet's Confirm lands here — the
    /// ONE apply path for a chart pick, so the sheet and the
    /// tests drive identical downstream behaviour. Keeping NOW
    /// re-enters the native reset (the selection re-snapshots to
    /// now through the same code as the pill's Now segment); an
    /// explicit pick re-solves the selected day at the drafted
    /// hour. Cancel never reaches this function at all.
    func applyTimePickerCommit(_ commit: TimePickerDraft.Commit) {
        switch commit {
        case .keepNow:
            resetToNow()
        case .explicitPick(let hour):
            updateSelectedTime(date: selectedDate, hour: hour)
        }
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

        // GEN-5 (Addendum 7 — the iOS widget): every successful
        // NOW-frame recommendation is the widget's last-good
        // record. Browsed hours never write it (the guard is
        // the NOW state itself), and a failed load never reaches
        // here, so the record only ever moves forward.
        persistWidgetLastGoodIfNow()

        // GEN-5 (Addendum 8 follow-up, Defect 1): keep the
        // personal store's named fill set current with the
        // loaded forecast, so a subscription fill names real
        // cells. Fingerprinted — the derivation re-runs only
        // when the forecast (or the keying inputs) changes.
        publishForecastPriorityCells()
    }

    /// The fingerprint of the last published priority-cell set:
    /// forecast identity + the keying inputs (fit preference,
    /// comfort) that shape the cell ids.
    private var priorityCellsFingerprint: String?

    private func publishForecastPriorityCells() {
        guard let hourly = weatherData?.hourly, !hourly.time.isEmpty else { return }
        let fingerprint = "\(hourly.time.first ?? "")|\(hourly.time.count)|\(genderPreference.rawValue)|\(comfortLevel.rawValue)"
        guard fingerprint != priorityCellsFingerprint else { return }
        priorityCellsFingerprint = fingerprint
        // Frames from the current hour forward (the forecast's
        // own location-local frame), one per (day, daypart)
        // cluster — only cluster-first frames need an engine
        // outfit; ForecastPriorityCells applies the same
        // selection formally.
        let nowPrefix = {
            let c = locationCalendar.dateComponents([.year, .month, .day, .hour], from: Date())
            return String(format: "%04d-%02d-%02dT%02d",
                          c.year ?? 0, c.month ?? 0, c.day ?? 0, c.hour ?? 0)
        }()
        var frames: [ForecastCellFrame] = []
        var lastCluster: String? = nil
        for index in hourly.time.indices {
            let stamp = hourly.time[index]
            if stamp < nowPrefix { continue }
            guard let snap = HourlyWeatherSnapshot.from(hourly: hourly, at: index) else { continue }
            let cluster = "\(String(stamp.prefix(10)))|\(HeroTimeOfDay.from(hour: snap.hour).rawValue)"
            if cluster == lastCluster { continue }
            lastCluster = cluster
            let outfit = outfitService.getRecommendation(
                for: comfortLevel.adjustTemperature(snap.feelsLikeFahrenheit),
                rawTemperature: snap.feelsLikeFahrenheit,
                isRaining: snap.rain > 0,
                windSpeed: snap.windSpeed,
                humidity: snap.humidity,
                uvIndex: snap.uvIndex,
                precipitation: snap.precipitation,
                hour: snap.hour
            )
            frames.append(ForecastCellFrame(
                date: String(stamp.prefix(10)),
                weatherCode: snap.weatherCode,
                feelsLikeFahrenheit: comfortLevel.adjustTemperature(snap.feelsLikeFahrenheit),
                hour: snap.hour,
                outfit: outfit
            ))
        }
        let forecastCells = ForecastPriorityCells.ids(genderPreference: genderPreference, frames: frames)
        // The composed fill set: forecast first (queued ahead),
        // then the canonical core matrix — the scaffold
        // substrate — behind it.
        let named = FillSetComposition.named(
            forecastCells: forecastCells,
            coreCells: CoreMatrixCells.ids(genderPreference: genderPreference)
        )
        personalStore?.updateForecastPriorityCells(named)
    }

    // MARK: - Widget last-good (GEN-5, Addendum 7)

    /// Persist the current NOW recommendation for the widget
    /// and ask WidgetKit to re-render. No-op in every other
    /// state (browsing, loading, failed).
    private func persistWidgetLastGoodIfNow() {
        guard isViewingLocationNow,
              let snapshot = selectedWeatherSnapshot,
              let recommendation else { return }
        let condition = WeatherCondition.from(code: snapshot.weatherCode)
        let record = WidgetLastGood(
            feelsLikeF: snapshot.feelsLikeFahrenheit,
            temperatureF: snapshot.temperatureFahrenheit,
            conditionText: condition.rawValue,
            conditionIcon: condition.icon,
            windMph: snapshot.windSpeed,
            humidityPercent: snapshot.humidity,
            uvIndex: snapshot.uvIndex,
            locationName: locationName,
            topBaseName: recommendation.topBase.name,
            layerNames: [recommendation.topMid, recommendation.topOuter].compactMap { $0?.name },
            bottomName: recommendation.bottom.name,
            secondaryNames: [recommendation.head, recommendation.hands].compactMap { $0?.name }
                + recommendation.accessories.map { $0.name },
            laterTodayLine: widgetLaterTodayLine(current: recommendation),
            unitIsCelsius: temperatureUnit == .celsius,
            savedAt: Date()
        )
        WidgetLastGoodStore.save(record)
        WidgetLastGoodStore.clearErrorCategory()
        WidgetCenter.shared.reloadTimelines(ofKind: "RunWearWidget")
    }

    /// The hourly snapshot for a location-local day/hour, by the
    /// same textual prefix match updateSelectedWeatherSnapshot
    /// uses. Nil outside the forecast.
    private func hourlySnapshot(on date: Date, hour: Int) -> HourlyWeatherSnapshot? {
        guard let hourly = weatherData?.hourly else { return nil }
        let components = locationCalendar.dateComponents([.year, .month, .day], from: date)
        let prefix = String(
            format: "%04d-%02d-%02dT%02d",
            components.year ?? 0, components.month ?? 0, components.day ?? 0, hour
        )
        guard let index = hourly.time.firstIndex(where: { $0.hasPrefix(prefix) }) else { return nil }
        return HourlyWeatherSnapshot.from(hourly: hourly, at: index)
    }

    /// A port of Android's LaterTodayLine (the settled Large
    /// widget line): the earliest outfit CHANGE at +3 h / +6 h
    /// wins; only when the outfit is stable does the line fall
    /// back to the next-hours feels-like range + peak rain.
    private func widgetLaterTodayLine(current: OutfitRecommendation) -> String? {
        let today = locationNowDate()
        let nowHour = locationNowHour()
        for delta in [3, 6] {
            let targetHour = nowHour + delta
            guard targetHour <= 23,
                  let later = hourlySnapshot(on: today, hour: targetHour) else { continue }
            let laterRecommendation = outfitService.getRecommendation(
                for: comfortLevel.adjustTemperature(later.feelsLikeFahrenheit),
                rawTemperature: later.feelsLikeFahrenheit,
                isRaining: later.rain > 0,
                windSpeed: later.windSpeed,
                humidity: later.humidity,
                uvIndex: later.uvIndex,
                precipitation: later.precipitation,
                hour: later.hour
            )
            let clauses = widgetOutfitDeltaClauses(current: current, later: laterRecommendation)
            if !clauses.isEmpty {
                var line = "By \(widgetHourLabel(hour: targetHour, on: today)): \(clauses.joined(separator: " · "))"
                if later.precipitationProbability >= 30 {
                    line += " · rain \(later.precipitationProbability)%"
                }
                return line
            }
        }
        var feels: [Double] = []
        var peakRain = 0
        for delta in 1...6 {
            let hour = nowHour + delta
            guard hour <= 23, let snapshot = hourlySnapshot(on: today, hour: hour) else { continue }
            feels.append(snapshot.feelsLikeFahrenheit)
            peakRain = max(peakRain, snapshot.precipitationProbability)
        }
        guard let minF = feels.min(), let maxF = feels.max() else { return nil }
        let symbol = temperatureUnit.symbol
        let range = Int(round(temperatureUnit.convert(fromFahrenheit: minF))) == Int(round(temperatureUnit.convert(fromFahrenheit: maxF)))
            ? "feels like \(Int(round(temperatureUnit.convert(fromFahrenheit: minF))))\(symbol)"
            : "feels like \(Int(round(temperatureUnit.convert(fromFahrenheit: minF))))–\(Int(round(temperatureUnit.convert(fromFahrenheit: maxF))))\(symbol)"
        var line = "Later today: \(range)"
        if peakRain >= 30 { line += " · rain up to \(peakRain)%" }
        return line
    }

    /// Android's outfitDeltaClauses, verbatim logic: slots in
    /// significance order (outer, mid, base, bottom, head,
    /// hands), "drop the X" / "add the X", capped at two.
    private func widgetOutfitDeltaClauses(
        current: OutfitRecommendation,
        later: OutfitRecommendation
    ) -> [String] {
        let slots: [(ClothingItem?, ClothingItem?)] = [
            (current.topOuter, later.topOuter),
            (current.topMid, later.topMid),
            (current.topBase, later.topBase),
            (current.bottom, later.bottom),
            (current.head, later.head),
            (current.hands, later.hands)
        ]
        var clauses: [String] = []
        for (nowItem, thenItem) in slots {
            if nowItem?.key == thenItem?.key { continue }
            if let nowItem, thenItem == nil {
                clauses.append("drop the \(nowItem.name)")
            } else if nowItem == nil, let thenItem {
                clauses.append("add the \(thenItem.name)")
            } else if let nowItem, let thenItem {
                clauses.append("drop the \(nowItem.name)")
                clauses.append("add the \(thenItem.name)")
            }
            if clauses.count >= 2 { break }
        }
        return Array(clauses.prefix(2))
    }

    private func widgetHourLabel(hour: Int, on date: Date) -> String {
        var components = locationCalendar.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = 0
        guard let target = locationCalendar.date(from: components) else { return "\(hour)" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h a"
        formatter.timeZone = locationCalendar.timeZone
        return formatter.string(from: target)
    }

    /// Reset to current date and time — re-enters the NOW state.
    ///
    /// GEN-4 RULING (Sol, Nov 4, 2026): "now" is the VIEWED
    /// LOCATION's live local time and date, on every surface —
    /// not the device's clock. Android's MainViewModel.resetToNow
    /// does the same via its locationNow (LocalDateTime in the
    /// viewed location's zone). The pre-fix iOS reset read
    /// Calendar.current, so viewing a place in another timezone
    /// reset to the device's date and hour while the rest of the
    /// app (snapshot matching, the day chart) frames the
    /// selection as the location's wall time.
    ///
    /// The selection's framing is the app's existing one: the
    /// selected date's DEVICE-calendar components are the wall
    /// date the hourly matching reads textually. The reset
    /// therefore lands the date whose device-calendar components
    /// are the location's current year/month/day (see
    /// locationNowDate) and the location's current hour.
    func resetToNow(at instant: Date = Date()) {
        applySelectedTime(
            date: locationNowDate(at: instant),
            hour: locationNowHour(at: instant),
            nowState: true
        )
    }

    /// The viewed location's current wall date, framed for the
    /// selection: a Date whose device-calendar year/month/day
    /// equal the location's current year/month/day at `instant`.
    /// With no forecast timezone yet (no weather loaded),
    /// locationCalendar IS the device calendar and this is the
    /// instant's own day — the pre-ruling behaviour.
    func locationNowDate(at instant: Date = Date()) -> Date {
        let components = locationCalendar.dateComponents(
            [.year, .month, .day], from: instant
        )
        return Calendar.current.date(from: components) ?? instant
    }

    /// Whether the current selection IS the viewed location's
    /// now: the location's today, at the location's current
    /// hour. This is the NOW-state tracking the reset feeds —
    /// the headline's "↻ Now" return and the pill row's Now
    /// segment show exactly when this is false, and the sheet
    /// moment reads "Now" exactly when it is true — so it is
    /// framed in the location's clock, never the device's.
    var isViewingLocationNow: Bool {
        selectedDayIsLocationToday && selectedHour == locationNowHour()
    }

    // MARK: - GEN-4 day chart

    /// A calendar in the VIEWED LOCATION's timezone (the hourly
    /// forecast's own zone, resolved by the weather service from
    /// the coordinates). "Now" for the chart surfaces — the
    /// band's NOW marker and the sheet's default draft — is the
    /// location's live local time, not the device's.
    var locationCalendar: Calendar {
        var calendar = Calendar.current
        if let identifier = weatherData?.timezone,
           let zone = TimeZone(identifier: identifier) {
            calendar.timeZone = zone
        }
        return calendar
    }

    /// The viewed location's current hour, in its own clock.
    func locationNowHour(at instant: Date = Date()) -> Int {
        locationCalendar.component(.hour, from: instant)
    }

    /// Whether the selected day is the viewed location's today:
    /// the selected day read in the app's existing framing (the
    /// device-calendar components the hourly matching uses),
    /// compared against the location's own current day.
    var selectedDayIsLocationToday: Bool {
        let selected = Calendar.current.dateComponents([.year, .month, .day], from: selectedDate)
        let locationNow = locationCalendar.dateComponents([.year, .month, .day], from: Date())
        return selected.year == locationNow.year
            && selected.month == locationNow.month
            && selected.day == locationNow.day
    }

    /// The selected day's day-chart model, built from the day's
    /// hourly forecast — the same per-hour snapshots the selected
    /// moment resolves from, each run through the ONE engine
    /// assembly's inputs (comfort-adjusted feels-like for the
    /// warmth frame, raw for the sun gates, measured rain, mph
    /// wind). Hours the forecast does not carry for the day are
    /// absent from the model (horizon honesty). Nil when the day
    /// has no forecast hours at all.
    var dayChart: DayChartModel? {
        guard let hourly = weatherData?.hourly else { return nil }
        let components = Calendar.current.dateComponents([.year, .month, .day], from: selectedDate)
        guard let year = components.year, let month = components.month, let day = components.day
        else { return nil }
        let dayPrefix = String(format: "%04d-%02d-%02d", year, month, day)
        var points: [DayChartHour] = []
        for (index, time) in hourly.time.enumerated() where time.hasPrefix(dayPrefix) {
            guard let snapshot = HourlyWeatherSnapshot.from(hourly: hourly, at: index)
            else { continue }
            let rawFeelsLike = snapshot.feelsLikeFahrenheit
            let adjustedFeelsLike = comfortLevel.adjustTemperature(rawFeelsLike)
            let hourRecommendation = outfitService.getRecommendation(
                for: adjustedFeelsLike,
                rawTemperature: rawFeelsLike,
                isRaining: snapshot.rain > 0,
                windSpeed: snapshot.windSpeed,
                humidity: snapshot.humidity,
                uvIndex: snapshot.uvIndex,
                precipitation: snapshot.precipitation,
                hour: snapshot.hour
            )
            points.append(DayChartHour(
                hour: snapshot.hour,
                feelsLikeF: adjustedFeelsLike,
                bracket: hourRecommendation.temperatureBracket,
                outfitItemNames: hourRecommendation.allItems.map(\.name)
            ))
        }
        return DayChartModel.build(points: points)
    }

    /// The chart's NOW marker hour for the current selection —
    /// non-nil only on the viewed location's today (see
    /// DayChartNowMarker for the rule).
    var dayChartNowHour: Int? {
        guard let dayChart else { return nil }
        return DayChartNowMarker.hour(
            selectedDayIsLocationToday: selectedDayIsLocationToday,
            locationNowHour: locationNowHour(),
            model: dayChart
        )
    }

    // MARK: - Private Methods

    /// Selects the hourly snapshot for the chosen date/hour. Internal
    /// (not private) so the boundary fixtures exercise the production
    /// selection, not a copy of it.
    func updateSelectedWeatherSnapshot() {
        guard let weatherData else {
            selectedWeatherSnapshot = nil
            selectedTimeOutsideForecast = false
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
                selectedTimeOutsideForecast = false
                return
            }
        }

        // Selected hour outside the forecast window: fall back to the
        // current-conditions snapshot (real measured values from the
        // extended current block — see HourlyWeatherSnapshot.init).
        // RW-23: flag it — the view shows a notice that the forecast
        // isn't available for that time yet, instead of the pre-RW-23
        // silent substitution.
        selectedTimeOutsideForecast = true
        selectedWeatherSnapshot = HourlyWeatherSnapshot(
            hour: selectedHour,
            current: weatherData.currentWeather,
            detail: weatherData.currentDetail
        )
    }

    /// The hero's keying frame, resolved synchronously: the
    /// feels-like (Fahrenheit) the photo is chosen by, the weather
    /// code, and the hour. The feels-like is the SAME
    /// comfort-adjusted frame the recommendation was built in
    /// (makeRecommendation's engine input) — the contract
    /// WatchViewModel already implements (its hero takes
    /// adjustedTemp). The phone previously keyed the photo on the
    /// RAW feels-like while the outfit and its band were keyed on
    /// the adjusted frame, so a comfort offset crossing a bracket
    /// boundary served a photo of a different outfit than the one
    /// recommended (field 2026-10-31: raw 53.6°F is a MILD cell —
    /// t-shirt and shorts — over a COOL outfit at adjusted 43.6°F).
    /// Internal (not private) so the field-findings fixtures drive
    /// this exact production construction.
    func prepareHeroFrame() -> (feelsLikeF: Double, weatherCode: Int, hour: Int)? {
        let rawFeelsLike: Double
        let weatherCode: Int
        let hour: Int

        if let snapshot = selectedWeatherSnapshot {
            rawFeelsLike = snapshot.feelsLikeFahrenheit
            weatherCode = snapshot.weatherCode
            hour = snapshot.hour
        } else if let current = weatherData?.currentWeather {
            rawFeelsLike = weatherData?.currentDetail?.apparentTemperature.map { $0 * 9 / 5 + 32 }
                ?? current.temperatureFahrenheit
            weatherCode = current.weathercode
            hour = Calendar.current.component(.hour, from: Date())
        } else {
            return nil
        }

        return (
            feelsLikeF: comfortLevel.adjustTemperature(rawFeelsLike),
            weatherCode: weatherCode,
            hour: hour
        )
    }

    /// WS-6 (D-20): the bundled asset name the pre-paint fallback
    /// resolves to for the current fit preference — the exact
    /// resolution `loadHeroImage` paints from (its URL form is the
    /// same call through `getImageURL`). Internal (not private) so
    /// the WS-6 gate tests drive the production resolution in a
    /// logic-test host, where bundle URLs cannot resolve and the
    /// asset NAME is the honest testable surface (the same surface
    /// WS-7's provider tests pin).
    func prePaintFallbackAssetName(
        temp: HeroTempBracket,
        weather: HeroWeatherCondition
    ) -> String {
        fallbackProvider.resolvedAssetName(
            temp: temp,
            weather: weather,
            gender: genderPreference.fallbackHeroGender
        )
    }

    private func loadHeroImage() async {
        isHeroImageLoading = true

        // Determine conditions for hero image — the recommendation
        // frame, via prepareHeroFrame (see its note).
        guard let frame = prepareHeroFrame() else {
            isHeroImageLoading = false
            return
        }
        let feelsLike = frame.feelsLikeF
        let weatherCode = frame.weatherCode
        let hour = frame.hour

        // The combination this screen asks the pool for, built from
        // the same frame inputs the service call below uses (the
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
        let previousRequestedCombinationId = heroRequestedCombinationId
        heroRequestedCombinationId = requestedCombination.fullId

        // Set fallback image immediately (zero-lag)
        let tempBracket = HeroTempBracket.from(feelsLikeTemperature: feelsLike)
        let weatherCondition = HeroWeatherCondition.from(weatherCode: weatherCode)

        // GEN-3 (WS-A(1) + WS-B / X-7): THE ATOMIC COMMIT. When the
        // requested cell changes (a place switch, a time change, a
        // comfort/gender change), the previous cell's pool/personal
        // photo is dropped in the SAME write that commits this
        // cell's fallback — the old photo can never paint under the
        // new place's label or the new cell's outfit (X-7), and the
        // new cell's truthful fallback serves from this instant
        // while its pool/personal photo loads. The hero's place
        // ownership moves in the same write (HeroDisplayGate).
        if previousRequestedCombinationId != requestedCombination.fullId
            || heroOwnerPlaceKey != placeDisplayName {
            heroImageUrl = nil
            heroImageRowId = nil
            heroServedCombinationId = nil
            currentPersonalHeroId = nil
            currentPersonalServeIsScaffold = false
        }
        heroOwnerPlaceKey = placeDisplayName

        // GEN-3 (WS-A(1)): the fallback that paints is resolved at
        // POINT GRAIN — the gated walk over the bundled assets'
        // adjudicated garment census against the live prescription
        // (the same gate the pool obeys; Android GEN-2 A2 parity).
        // WS-6 (D-20) stands: the resolution carries the user's fit
        // preference (a unisex preference passes nil and walks the
        // legacy view first).
        //
        // GEN-4 (hero-fallback lane, 2026-11-04): the GEN-3 commit
        // resolved EMPTY when no bundled asset passed the gate —
        // the hero painted its gradient for the census's 26
        // unservable signatures. Sol ruled that out: the commit
        // now resolves through the GEN-4 fallback chain
        // (HeroFallbackChain) at its BUNDLED FLOOR layer — no
        // live pool consultation exists at this synchronous
        // instant, so the chain returns the exact bundled asset
        // when the gate passes one, and otherwise the nearest
        // bundled asset under the chain metric, labelled by its
        // tier (heroFallbackTier). The fallback is empty only
        // when there is no prescription at all and the legacy
        // resolution cannot run — with a recommendation, a photo
        // ALWAYS commits in this write. The pool cascade below
        // then re-resolves through the full chain (exact →
        // scaffold → floor) with the live pool set. Without a
        // recommendation there is no prescription to judge, so
        // the legacy bracket resolution paints, exactly as before.
        let chainResolution: HeroFallbackResolution?
        if let recommendation {
            chainResolution = fallbackProvider.chainResolution(
                temp: tempBracket,
                weather: weatherCondition,
                gender: genderPreference.fallbackHeroGender,
                outfit: recommendation,
                poolCandidates: [],
                poolReachable: false
            )
            heroFallbackTier = chainResolution?.tier
            heroFallbackAssetName = chainResolution?.bundledAssetName
            fallbackImageUrl = chainResolution?.bundledAssetName.map {
                fallbackProvider.imageURL(forAssetName: $0)
            } ?? ""
        } else {
            chainResolution = nil
            heroFallbackTier = nil
            heroFallbackAssetName = nil
            fallbackImageUrl = fallbackProvider.getImageURL(
                temp: tempBracket,
                weather: weatherCondition,
                size: .iOS,
                gender: genderPreference.fallbackHeroGender
            )
        }
        let gatedAssetName = chainResolution?.bundledAssetName

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

        // Only set hero image if it's from Supabase (not Unsplash fallback).
        // GEN-3 (census-ios Y-1, rendering defect 2): a reload for
        // the SAME cell while a personal photo is applied must not
        // republish the pool URL into the shared slot — the
        // census's middle step was exactly that write, a stock
        // photo fading in under the finished claim for ~0.6 s
        // before the personal write cut back. No stock photo may
        // appear under a finished claim, even transiently. The
        // personal layer below re-resolves either way; the pool
        // result still feeds the chapter records through the
        // serve telemetry.
        let personalAppliedForThisCell = currentPersonalHeroId != nil
            && previousRequestedCombinationId == requestedCombination.fullId
        // GEN-4: a scaffold POOL serve (fallback level 9) paints
        // through the same slot as an exact pool serve — but it
        // keeps its scaffold tier (heroFallbackTier and the serve
        // record below), and its identity is the row's OWN
        // combination, never the requested cell's.
        let isScaffoldPoolPaint = result.fallbackLevel == 9
        // GEN-6 Wave A (DA-07 class, cold-start serve order): the
        // pool resolution is NOT published yet. Gen-5 published
        // the pool photo here and let the personal layer overwrite
        // it a network round-trip later, so a relaunching user
        // with a banked own photo watched the bundled fallback, a
        // POOL STOCK STRANGER, and only then their own photo —
        // the exact serve order DA-07 censused on the sibling
        // platform. Now the pool paint (and the bundled-result
        // adoption) is computed into deferred values, the
        // personal layer resolves FIRST (below), and the deferred
        // pool paint publishes only when no personal photo
        // served. The bundled fallback painted at the atomic
        // commit remains the always-available floor throughout
        // (WS-A(1)).
        var deferredPoolPaint: (url: String, rowId: String?, combinationId: String?)?
        var deferredBundledAdoption: (tier: HeroFallbackTier?, asset: String, url: String)?
        if result.fallbackLevel < 8 || isScaffoldPoolPaint {
            if !personalAppliedForThisCell {
                deferredPoolPaint = (result.imageUrl, result.imageRowId, result.combinationId)
            }
            if isScaffoldPoolPaint {
                heroFallbackTier = .scaffold
                heroFallbackAssetName = nil
            }
        } else {
            // GEN-4: a bundled result from the service's full
            // chain (exact / scaffold / floor over the live pool
            // set) names its asset and tier — adopt them so the
            // paint and its record carry the chain's labelling,
            // not just the commit's floor labelling. (The metric
            // over the static bundled set makes this the same
            // asset the commit painted; the adoption keeps the
            // record honest if the sets ever diverge.) The
            // adoption is deferred with the pool paint (GEN-6
            // Wave A): the committed floor asset already paints,
            // and the adoption names that same asset in practice.
            if let asset = result.fallbackAssetFile, !asset.isEmpty {
                deferredBundledAdoption = (
                    tier: result.fallbackTier ?? heroFallbackTier,
                    asset: asset,
                    url: fallbackProvider.imageURL(forAssetName: asset)
                )
            }
        }

        // WS-7 / RW-26(c): the bundled asset the screen will paint
        // if the pool missed — the fallback resolved at the
        // atomic commit above is the paint in that case (the
        // cascade's own level-8 result is not what renders), so the
        // serve record names THIS asset. GEN-3: the name is the
        // gated resolution itself — complete in every host, even
        // where bundle URLs cannot resolve (a logic-test host's
        // empty URL string). GEN-4: the name is the chain
        // resolution's asset (the service's adopted asset when it
        // named one, else the commit's), and its tier rides the
        // record. The no-prescription path keeps the legacy
        // derivation from the painted URL.
        let paintedBundledAsset: String?
        if result.fallbackLevel < 8 || isScaffoldPoolPaint {
            paintedBundledAsset = nil
        } else if recommendation != nil {
            paintedBundledAsset = result.fallbackAssetFile.flatMap { $0.isEmpty ? nil : $0 } ?? gatedAssetName
        } else {
            paintedBundledAsset = fallbackImageUrl.map { url in
                url.isEmpty
                    ? fallbackProvider.resolvedAssetName(
                        temp: tempBracket,
                        weather: weatherCondition,
                        gender: genderPreference.fallbackHeroGender
                    )
                    : FallbackImageProvider.assetFile(fromURLString: url)
            }
        }

        // RW-14: the personal layer runs on the same keying
        // frame (prepareHeroFrame), so the personalized photo is
        // keyed to the outfit on screen. GEN-6 Wave A: it now
        // runs BEFORE the pool paint publishes (see the deferred
        // pool paint above) — serve order on every load, cold
        // start included, is: bundled floor → the user's own
        // banked photo when one serves → the pool photo only when
        // no personal photo serves. Never the pool stranger
        // flashing in ahead of the user's own photo.
        let personalServed = await applyPersonalHero(frame: frame)

        if !personalServed {
            // Publish the deferred pool resolution now that the
            // personal layer has had its first claim on the slot.
            if let paint = deferredPoolPaint {
                heroImageUrl = paint.url
                heroImageRowId = paint.rowId
                heroServedCombinationId = paint.combinationId
            } else if result.fallbackLevel >= 8 && !isScaffoldPoolPaint {
                heroImageUrl = nil // Will use fallback
                heroImageRowId = nil
                heroServedCombinationId = nil
                if let adoption = deferredBundledAdoption {
                    heroFallbackTier = adoption.tier
                    heroFallbackAssetName = adoption.asset
                    if !adoption.url.isEmpty { fallbackImageUrl = adoption.url }
                }
            }
        }

        // WS-7 / RW-26(c): record the serve that is actually on
        // screen (D-18's attribution fix). A personal serve records
        // itself inside applyPersonalHero; every other resolution
        // records exactly one record here — pool, or the bundled
        // fallback that paints on a pool miss.
        if !personalServed {
            if result.fallbackLevel < 8 {
                HeroServeTelemetry.emitForResult(
                    result,
                    requestedCombinationId: heroRequestedCombinationId,
                    bracket: tempBracket,
                    weather: weatherCondition,
                    gender: genderPreference,
                    trigger: "pool-cascade"
                )
            } else if isScaffoldPoolPaint {
                // GEN-4: the scaffold pool serve records as a
                // scaffold — its own row identity, tier labelled —
                // never as the exact pool serve it is not.
                recordHeroServe(
                    HeroServeRecord(
                        source: .scaffold,
                        requestedCombinationId: heroRequestedCombinationId,
                        servedCombinationId: result.combinationId,
                        imageRowId: result.imageRowId,
                        tempBracket: tempBracket,
                        weatherCondition: weatherCondition,
                        gender: genderPreference,
                        trigger: "hero-fallback-chain",
                        fallbackTier: .scaffold
                    )
                )
            } else if let paintedBundledAsset {
                // GEN-4: the bundled paint's source follows its
                // chain tier — scaffold when the chain labelled
                // it scaffold, bundled-fallback otherwise (the
                // exact and floor tiers, and the legacy
                // no-prescription path whose tier is nil).
                let isScaffoldTier = heroFallbackTier == .scaffold
                recordHeroServe(
                    HeroServeRecord(
                        source: isScaffoldTier ? .scaffold : .bundledFallback,
                        requestedCombinationId: heroRequestedCombinationId,
                        tempBracket: tempBracket,
                        weatherCondition: weatherCondition,
                        gender: genderPreference,
                        fallbackAssetFile: paintedBundledAsset,
                        fallbackProvenance: FallbackImageProvider.provenance(forAssetName: paintedBundledAsset),
                        trigger: isScaffoldTier ? "hero-fallback-chain" : "bundled-fallback",
                        fallbackTier: heroFallbackTier
                    )
                )
            }
        }

        isHeroImageLoading = false
    }

    /// WS-7 / RW-26(c): record one hero serve. Every record rides
    /// the HeroServe channel (HeroServeTelemetry); a
    /// BUNDLED-FALLBACK serve additionally files remotely under the
    /// automated `diagnostics` category (HeroServeDiagnostics) —
    /// the bundled serve is the one serve with no server-side
    /// trace, and D-18 is the failure that closes. Filing is
    /// fire-and-forget; failures are dropped, never queued (a stale
    /// serve report would misattribute). Mirror of Android's
    /// MainViewModel.recordHeroServe.
    private func recordHeroServe(_ record: HeroServeRecord) {
        HeroServeTelemetry.emit(record)
        if record.source == .bundledFallback {
            HeroServeDiagnostics.file(record: record)
        }
    }

    /// RW-14: consults the personal serving path for the current
    /// cell. A verified personalized hero replaces the pool photo —
    /// immediately when one exists, or by swap-in when it lands
    /// (the pool photo stays up meanwhile). Since RW-16.16 the
    /// store folds the server's per-cell look state on every
    /// lookup, and the view renders it at the hero — making,
    /// failed (with retry) — so a cell the gate never delivers
    /// keeps the pool photo AND says so. Not opted in (or no store
    /// wired): no-op beyond clearing the cell marker.
    ///
    /// WS-7: returns whether a personal photo is the paint NOW —
    /// the caller records the pool/bundled serve only when it is
    /// not, so each paint carries exactly one serve record. A
    /// personal paint records itself here (personal, or scaffold
    /// when the server's look state for the cell is still making —
    /// the PersonalServePolicy distinction); a swap-in that lands
    /// later records its own personal serve when it paints.
    @discardableResult
    private func applyPersonalHero(frame: (feelsLikeF: Double, weatherCode: Int, hour: Int)) async -> Bool {
        guard let store = personalStore, store.isOptedIn,
              let recommendation else {
            currentPersonalCombination = nil
            currentPersonalHeroId = nil
            currentPersonalServeIsScaffold = false
            personalAwaitTask?.cancel()
            personalAwaitTask = nil
            return false
        }
        let combo = PersonalCombination.make(
            genderPreference: genderPreference,
            weatherCode: frame.weatherCode,
            feelsLikeFahrenheit: frame.feelsLikeF,
            hour: frame.hour,
            outfit: recommendation
        )
        currentPersonalCombination = combo
        // The pool photo painted by the caller is what's on screen
        // until a personal hero actually applies below.
        currentPersonalHeroId = nil
        currentPersonalServeIsScaffold = false
        personalAwaitTask?.cancel()
        if let personalURL = await store.resolveHero(for: combo) {
            heroImageUrl = personalURL
            heroImageRowId = nil
            heroServedCombinationId = combo.combinationId
            currentPersonalHeroId = store.personalHeroId(for: combo)
            // WS-7: this paint is a personal serve — or a scaffold
            // serve when the exact look is still making and the
            // photo on screen is the user's own banked photo for
            // nearest conditions (PersonalServePolicy's rule, read
            // off the store's folded look state through the
            // resolving contract — the concrete store is not part
            // of every target this view model compiles into).
            let lookState = store.lookState(for: combo)
            // GEN-6 Wave A (RW-34): record the scaffold fact at
            // paint time — the pill's silent-scaffold gate reads
            // exactly this distinction.
            currentPersonalServeIsScaffold = lookState == .making
            recordHeroServe(
                HeroServeRecord(
                    source: lookState == .making ? .scaffold : .personal,
                    requestedCombinationId: heroRequestedCombinationId,
                    servedCombinationId: combo.combinationId,
                    tempBracket: HeroTempBracket.from(feelsLikeTemperature: frame.feelsLikeF),
                    weatherCondition: HeroWeatherCondition.from(weatherCode: frame.weatherCode),
                    gender: genderPreference,
                    trigger: "personal-overlay"
                )
            )
            return true
        } else {
            personalAwaitTask = Task { [weak self] in
                guard let landedURL = await store.awaitHero(for: combo) else { return }
                guard let self,
                      self.currentPersonalCombination?.combinationId == combo.combinationId
                else { return }
                self.heroImageUrl = landedURL
                self.heroImageRowId = nil
                self.heroServedCombinationId = combo.combinationId
                self.currentPersonalHeroId = self.personalStore?.personalHeroId(for: combo)
                // The landed look is the exact photo by
                // definition — no longer a scaffold serve.
                self.currentPersonalServeIsScaffold = false
                // WS-7: the swap-in is a new paint — a personal
                // serve (the landed look is ready by definition).
                self.recordHeroServe(
                    HeroServeRecord(
                        source: .personal,
                        requestedCombinationId: self.heroRequestedCombinationId,
                        servedCombinationId: combo.combinationId,
                        gender: self.genderPreference,
                        trigger: "personal-overlay"
                    )
                )
            }
            return false
        }
    }

    /// Re-runs the hero resolution for the current conditions —
    /// called when the personal layer's state changes underneath a
    /// screen that is already showing (opt-in completed, photos
    /// replaced, entitlement changed, photos deleted).
    func refreshHero() {
        Task { await loadHeroImage() }
    }

    /// Awaits a hero reload and returns the personal cell it keyed
    /// on — the onboarding fork uses it to kick the user's first
    /// generation for exactly the cell they just saw.
    func reloadHeroForPersonal() async -> PersonalCombination? {
        await loadHeroImage()
        return currentPersonalCombination
    }

    /// GEN-3 (WS-A(1) / census X-10): THE always-available photo
    /// reference — the one resolution every photo-consuming
    /// surface is fed by (hero, Wear I've Run entry thumbnails,
    /// the TODAY card, chapter rows, the Morning Poster
    /// thumbnail): the personal/pool photo when one is displayed,
    /// otherwise the committed fallback. When the entry is
    /// recorded before the hero load commits (a settle racing the
    /// cascade), the fallback for the CURRENT frame is derived
    /// on the spot — the same resolution loadHeroImage commits —
    /// so a recorded entry never falls back to an unrelated
    /// bracket's stock photo.
    /// GEN-4: the on-the-spot derivation is the chain's bundled
    /// floor (exact bundled when the gate passes, otherwise the
    /// nearest bundled asset under the metric), so a recorded
    /// entry exists for EVERY prescription — nil only when no
    /// frame exists yet (or bundle URLs cannot resolve, as in a
    /// logic-test host).
    func alwaysAvailableHeroReference() -> String? {
        if let heroImageUrl { return heroImageUrl }
        if let fallbackImageUrl, !fallbackImageUrl.isEmpty { return fallbackImageUrl }
        guard let frame = prepareHeroFrame(), let recommendation else { return nil }
        let tempBracket = HeroTempBracket.from(feelsLikeTemperature: frame.feelsLikeF)
        let weatherCondition = HeroWeatherCondition.from(weatherCode: frame.weatherCode)
        guard let name = fallbackProvider.chainResolution(
            temp: tempBracket,
            weather: weatherCondition,
            gender: genderPreference.fallbackHeroGender,
            outfit: recommendation,
            poolCandidates: [],
            poolReachable: false
        )?.bundledAssetName else { return nil }
        let url = fallbackProvider.imageURL(forAssetName: name)
        return url.isEmpty ? nil : url
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

    /// The PERSONAL flag target: non-nil only while a personalized
    /// hero is the photo on screen. A personal photo is never
    /// reported through the pool flag path — that insert would
    /// misidentify it as a fallback photo and retire nothing.
    var currentPersonalFlagTarget: PersonalHeroFlagTarget? {
        guard let heroId = currentPersonalHeroId,
              let combo = currentPersonalCombination,
              let url = heroImageUrl else { return nil }
        return PersonalHeroFlagTarget(
            heroId: heroId,
            combinationId: combo.combinationId,
            imageUrl: url
        )
    }

    /// "This doesn't look like me" (spec §4): flag the personalized
    /// hero on screen with the backend. When the flag lands, the
    /// hero is refreshed — the pool photo serves in its place while
    /// the server-side regeneration the flag queued is pending.
    /// When it doesn't land, nothing on screen changes.
    func flagCurrentPersonalHero(reason: PersonalHeroFlagReason) {
        guard let store = personalStore,
              let combo = currentPersonalCombination else { return }
        Task {
            if await store.flagPersonalHero(combination: combo, reason: reason) {
                refreshHero()
            }
        }
    }

    private func reloadHeroImage() async {
        await loadHeroImage()
    }

    private func reverseGeocode(location: CLLocation) async {
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            if let placemark = placemarks.first {
                // WS-4: the SimplePlace is derived ONCE, here, at
                // geocode time — city + abbreviated region from
                // the placemark's components. No surface ever
                // sees the placemark, and no raw concatenation of
                // its fields reaches a rendered line. (The
                // pre-WS-4 code joined locality + administrativeArea
                // verbatim, which is how "New York, New York"
                // reached the header on Android; iOS happened to
                // receive abbreviated regions from CLGeocoder for
                // US places, which is luck, not a model.)
                if let place = SimplePlace(
                    city: placemark.locality,
                    region: placemark.administrativeArea
                ) {
                    simplePlace = place
                    locationName = place.displayName
                } else {
                    simplePlace = nil
                    locationName = "Current Location"
                }
            }
        } catch {
            simplePlace = nil
            locationName = "Current Location"
        }
        // GEN-5 (Addendum 7): the place name has now resolved —
        // re-persist the widget record so it never carries the
        // loading placeholder (the first persist happens inside
        // makeRecommendation, before the geocode lands).
        persistWidgetLastGoodIfNow()
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
            "📍 \(placeDisplayName)",
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
