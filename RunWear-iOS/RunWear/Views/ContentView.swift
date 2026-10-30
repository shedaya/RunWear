import SwiftUI
import CoreLocation

struct ContentView: View {
    @StateObject private var locationService = LocationService()
    @StateObject private var viewModel = WeatherViewModel()
    @State private var showManualLocationPicker = false

    var body: some View {
        ZStack {
            // Use dark background for the new design
            AppTheme.darkBackground
                .ignoresSafeArea()

            Group {
                switch locationService.authorizationStatus {
                case .notDetermined:
                    if hasManualWeather {
                        outfitView
                    } else {
                        LocationPermissionView(
                            onRequestPermission: {
                                locationService.requestPermission()
                            },
                            onSetLocationManually: {
                                showManualLocationPicker = true
                            }
                        )
                    }
                case .denied, .restricted:
                    if hasManualWeather {
                        outfitView
                    } else {
                        LocationDeniedView(onSetLocationManually: {
                            showManualLocationPicker = true
                        })
                    }
                case .authorizedWhenInUse, .authorizedAlways:
                    // RW-6.2 precedence (resolved by HomePresentation,
                    // pinned by HomePresentationTests): content
                    // outranks the error. A failed refresh keeps the
                    // outfit on screen — the error becomes a banner
                    // over it inside WeatherOutfitView — and the
                    // full-screen error is reserved for having
                    // nothing to show.
                    switch HomePresentation.resolve(
                        hasContent: viewModel.weatherData != nil && viewModel.recommendation != nil,
                        errorMessage: viewModel.errorMessage
                    ) {
                    case .loading:
                        LoadingView()
                    case .error(let message):
                        ErrorView(message: message) {
                            refreshAppropriate()
                        }
                    case .content:
                        outfitView
                    }
                @unknown default:
                    LoadingView()
                }
            }
        }
        .tint(AppTheme.primaryColor)
        // The manual-location path out of the permission gates: the
        // picker is reachable BEFORE authorization (it geocodes a
        // typed place; it never needs the device's location), and a
        // manual selection shows the outfit even while authorization
        // is denied/notDetermined — previously those states hard-walled
        // the app even with a valid manual location chosen in the
        // picker.
        .sheet(isPresented: $showManualLocationPicker) {
            LocationPickerSheet(
                isPresented: $showManualLocationPicker,
                isUsingGPS: false,
                onUseCurrentLocation: {
                    viewModel.switchToGPS()
                    locationService.requestPermission()
                },
                onSelectLocation: { coordinate, name in
                    viewModel.setManualLocation(coordinate: coordinate, name: name)
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .onChange(of: locationService.location) { _, newLocation in
            // A GPS fix governs only in GPS mode. In manual mode a
            // stray fix (e.g. one requested before the user picked a
            // place) must not overwrite the manual location's
            // weather — that overwrite was R-19's visible symptom.
            guard viewModel.isUsingGPS, let location = newLocation else { return }
            Task {
                await viewModel.fetchWeather(for: location)
            }
        }
        .onAppear {
            // RW-6.8: honor the location choice made in onboarding. A
            // user who picked "Set a City Instead" meets the picker
            // directly — the choice was already made, so the permission
            // gate is not a surprise second ask. One-shot: the flag is
            // consumed here, and a restored manual location (a returning
            // user) skips it entirely.
            if UserDefaults.standard.string(forKey: "onboardingLocationChoice") == "manual",
               viewModel.restoredManualLocation == nil {
                UserDefaults.standard.removeObject(forKey: "onboardingLocationChoice")
                showManualLocationPicker = true
            }
            if let manual = viewModel.restoredManualLocation {
                // Saved-location-first launch (Android's contract):
                // fetch the restored manual location's weather; do
                // not request a GPS fix at all.
                Task {
                    await viewModel.fetchWeather(for: manual)
                }
            } else if locationService.authorizationStatus == .authorizedWhenInUse ||
               locationService.authorizationStatus == .authorizedAlways {
                locationService.requestLocation()
            }
        }
    }

    /// Refresh / retry for whichever location source is active:
    /// the manual location in manual mode, the GPS fix otherwise.
    private func refreshAppropriate() {
        Task {
            if viewModel.isUsingGPS {
                await viewModel.refresh(location: locationService.location)
            } else {
                await viewModel.refreshCurrent()
            }
        }
    }

    /// A manually chosen location with loaded weather bypasses the
    /// permission gates: the user declined (or hasn't granted) GPS
    /// but still has a working app.
    private var hasManualWeather: Bool {
        !viewModel.isUsingGPS && viewModel.weatherData != nil && viewModel.recommendation != nil
    }

    @ViewBuilder
    private var outfitView: some View {
        if let weather = viewModel.weatherData,
           let recommendation = viewModel.recommendation {
            WeatherOutfitView(
                viewModel: viewModel,
                locationService: locationService,
                weather: weather,
                recommendation: recommendation,
                onRefresh: {
                    refreshAppropriate()
                },
                onUseCurrentLocation: {
                    viewModel.switchToGPS()
                    locationService.requestLocation()
                }
            )
        }
    }
}

#Preview {
    ContentView()
}
