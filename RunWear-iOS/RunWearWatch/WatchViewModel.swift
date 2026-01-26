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

    private let locationManager = CLLocationManager()
    private let weatherService = WeatherService.shared
    private let outfitService = OutfitRecommendationService.shared
    private let geocoder = CLGeocoder()

    private var currentLocation: CLLocation?

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
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
        // Toggle between F and C - for now just refresh
        // Could store preference in UserDefaults
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

            let recommendation = outfitService.getRecommendation(
                for: weather.currentWeather.temperatureFahrenheit,
                condition: weather.currentWeather.condition
            )

            state = .loaded(weather, recommendation)
            await reverseGeocode(location: location)
        } catch {
            state = .error(error.localizedDescription)
        }
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
