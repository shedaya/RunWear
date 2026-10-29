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
                    if viewModel.isLoading && viewModel.weatherData == nil {
                        LoadingView()
                    } else if let error = viewModel.errorMessage {
                        ErrorView(message: error) {
                            Task {
                                await viewModel.refresh(location: locationService.location)
                            }
                        }
                    } else if viewModel.weatherData != nil && viewModel.recommendation != nil {
                        outfitView
                    } else {
                        LoadingView()
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
            if let location = newLocation {
                Task {
                    await viewModel.fetchWeather(for: location)
                }
            }
        }
        .onAppear {
            if locationService.authorizationStatus == .authorizedWhenInUse ||
               locationService.authorizationStatus == .authorizedAlways {
                locationService.requestLocation()
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
                isRefreshing: viewModel.isLoading,
                onRefresh: {
                    Task {
                        await viewModel.refresh(location: locationService.location)
                    }
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
