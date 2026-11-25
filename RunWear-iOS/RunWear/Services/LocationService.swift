import Foundation
import CoreLocation

// MARK: - RW-93: the launch-gate decision
//
// On a cold relaunch, CLLocationManager can read .notDetermined
// until its first authorization callback lands — even when the
// user granted permission long ago and used it minutes earlier
// (Sol's build-28 walkthrough: the full-screen "Location Access
// Needed" gate stood for ~2 s on relaunch, over a granted
// permission). A gate may render only on a RESOLVED status: the
// seeded value is trusted when it is already determined (a
// determined status never reads stale in that direction), and
// the first delegate callback resolves every other case.
enum LaunchGateResolution {
    /// Whether the full-screen permission gate may render now.
    /// An unresolved .notDetermined shows the loading skeleton
    /// instead — never the gate.
    static func showsPermissionGate(
        status: CLAuthorizationStatus,
        hasResolvedAuthorization: Bool,
        hasManualWeather: Bool
    ) -> Bool {
        status == .notDetermined && hasResolvedAuthorization && !hasManualWeather
    }
}

@MainActor
class LocationService: NSObject, ObservableObject {
    @Published var location: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var locationError: Error?
    /// RW-93: true once the authorization status is known for
    /// THIS launch — the seeded status was already determined,
    /// or the manager's first authorization callback has landed.
    /// Until then a .notDetermined reading is unproven and no
    /// permission gate may render from it.
    @Published private(set) var hasResolvedAuthorization = false

    private let locationManager = CLLocationManager()

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        // W-19 (GEN-2): seed the published status from the manager
        // itself. The default (.notDetermined) otherwise misreports
        // an already-granted permission until the first delegate
        // callback lands, and onboarding's location step branched
        // on that stale value.
        authorizationStatus = locationManager.authorizationStatus
        // RW-93: a seeded DETERMINED status is a resolution; a
        // seeded .notDetermined is not (it may be the cold-launch
        // transient) — the delegate callback below resolves it.
        hasResolvedAuthorization = authorizationStatus != .notDetermined
    }

    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    func requestLocation() {
        locationError = nil
        locationManager.requestLocation()
    }
}

extension LocationService: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            location = locations.first
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            locationError = error
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            authorizationStatus = manager.authorizationStatus
            // RW-93: the first callback resolves this launch's
            // authorization, whatever it reports.
            hasResolvedAuthorization = true
            if manager.authorizationStatus == .authorizedWhenInUse ||
               manager.authorizationStatus == .authorizedAlways {
                requestLocation()
            }
        }
    }
}
