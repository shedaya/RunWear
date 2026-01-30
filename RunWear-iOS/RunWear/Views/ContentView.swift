import SwiftUI
import CoreLocation

struct ContentView: View {
    @StateObject private var locationService = LocationService()
    @StateObject private var viewModel = WeatherViewModel()

    var body: some View {
        ZStack {
            // Use dark background for the new design
            AppTheme.darkBackground
                .ignoresSafeArea()

            Group {
                switch locationService.authorizationStatus {
                case .notDetermined:
                    LocationPermissionView(onRequestPermission: {
                        locationService.requestPermission()
                    })
                case .denied, .restricted:
                    LocationDeniedView()
                case .authorizedWhenInUse, .authorizedAlways:
                    if viewModel.isLoading && viewModel.weatherData == nil {
                        LoadingView()
                    } else if let error = viewModel.errorMessage {
                        ErrorView(message: error) {
                            Task {
                                await viewModel.refresh(location: locationService.location)
                            }
                        }
                    } else if let weather = viewModel.weatherData,
                              let recommendation = viewModel.recommendation {
                        WeatherOutfitView(
                            viewModel: viewModel,
                            weather: weather,
                            recommendation: recommendation,
                            isRefreshing: viewModel.isLoading,
                            onRefresh: {
                                Task {
                                    await viewModel.refresh(location: locationService.location)
                                }
                            }
                        )
                    } else {
                        LoadingView()
                    }
                @unknown default:
                    LoadingView()
                }
            }
        }
        .tint(AppTheme.primaryColor)
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
}

#Preview {
    ContentView()
}
