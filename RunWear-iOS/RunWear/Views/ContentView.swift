import SwiftUI
import CoreLocation

struct ContentView: View {
    @StateObject private var locationService = LocationService()
    @StateObject private var viewModel = WeatherViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.backgroundColor
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
                                weather: weather,
                                recommendation: recommendation,
                                locationName: viewModel.locationName,
                                isRefreshing: viewModel.isLoading,
                                genderPreference: viewModel.genderPreference,
                                onRefresh: {
                                    Task {
                                        await viewModel.refresh(location: locationService.location)
                                    }
                                },
                                onGenderChange: { newGender in
                                    viewModel.genderPreference = newGender
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
            .navigationTitle("RunWear")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(AppTheme.primaryColor, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
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
