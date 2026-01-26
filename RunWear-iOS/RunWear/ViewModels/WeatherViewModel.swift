import Foundation
import CoreLocation
import SwiftUI

@MainActor
class WeatherViewModel: ObservableObject {
    @Published var weatherData: WeatherData?
    @Published var recommendation: OutfitRecommendation?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var locationName: String = "Loading..."
    @Published var genderPreference: GenderPreference {
        didSet {
            UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
        }
    }

    private let weatherService = WeatherService.shared
    private let outfitService = OutfitRecommendationService.shared
    private let geocoder = CLGeocoder()

    init() {
        // Load saved gender preference
        if let saved = UserDefaults.standard.string(forKey: "genderPreference"),
           let preference = GenderPreference(rawValue: saved) {
            self.genderPreference = preference
        } else {
            self.genderPreference = .unisex
        }
    }

    func fetchWeather(for location: CLLocation) async {
        isLoading = true
        errorMessage = nil

        do {
            let weather = try await weatherService.fetchWeather(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude
            )
            weatherData = weather
            recommendation = outfitService.getRecommendation(
                for: weather.currentWeather.temperatureFahrenheit,
                condition: weather.currentWeather.condition
            )
            await reverseGeocode(location: location)
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
