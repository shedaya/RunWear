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

/// RW-162 G2 (device verdict, Nov 10, 2026): the freshness
/// rule an explicit Current Location tap is judged by lives
/// with the pure models (FreshFixPolicy in
/// RW26CollectionModels.swift) so the test target pins it
/// directly; this service is its executor (requestFreshLocation
/// below).
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

    // MARK: RW-162 G2: the explicit Current Location request

    /// When an explicit fresh-fix request started, if one is
    /// in flight; fixes arriving meanwhile are judged by
    /// FreshFixPolicy and a stale cached fix is never published
    /// as the tap's answer.
    private var freshFixRequestedAt: Date?
    /// The newest fix seen during the window (the fallback if
    /// no fresh fix lands before it closes).
    private var bestFixInWindow: CLLocation?
    private var freshFixTimer: Task<Void, Never>?

    /// The Current Location tap's request (ContentView wires
    /// both explicit taps here): continuous updates until a
    /// FRESH fix lands or the bounded window closes.
    func requestFreshLocation() {
        locationError = nil
        freshFixRequestedAt = Date()
        bestFixInWindow = nil
        locationManager.requestLocation()
        locationManager.startUpdatingLocation()
        freshFixTimer?.cancel()
        freshFixTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(FreshFixPolicy.windowSeconds * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.closeFreshFixWindow()
        }
    }

    /// A fix arrived (the delegate's seam): while a fresh-fix
    /// request is in flight, a fresh fix publishes and closes
    /// the window at once; a stale one is held as the fallback
    /// and NOT published (publishing it is the build-27 bug —
    /// the tap answered with the previous city). Outside a
    /// request, every fix publishes as before.
    fileprivate func receiveFix(_ fix: CLLocation) {
        guard let requestedAt = freshFixRequestedAt else {
            location = fix
            return
        }
        if bestFixInWindow == nil
            || fix.timestamp >= bestFixInWindow!.timestamp {
            bestFixInWindow = fix
        }
        if FreshFixPolicy.isFresh(fixDate: fix.timestamp, requestedAt: requestedAt) {
            location = fix
            endFreshFixWindow()
        }
    }

    /// The timer's close: the window ended without a fresh
    /// fix — publish the best fix seen (the tap still answers)
    /// and stop the updates the request started.
    private func closeFreshFixWindow() {
        if freshFixRequestedAt != nil, let best = bestFixInWindow {
            location = best
        }
        endFreshFixWindow()
    }

    private func endFreshFixWindow() {
        freshFixRequestedAt = nil
        bestFixInWindow = nil
        freshFixTimer?.cancel()
        freshFixTimer = nil
        locationManager.stopUpdatingLocation()
    }

    /// A location error arrived: during a fresh-fix window a
    /// transient failure (kCLErrorLocationUnknown is routine
    /// for one-shot requests) must not kill the request — the
    /// window's own deadline decides. Outside a window the
    /// error publishes as before.
    fileprivate func receiveError(_ error: Error) {
        if freshFixRequestedAt != nil { return }
        locationError = error
    }
}

extension LocationService: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            // RW-162 G2: a delivery batch is chronological —
            // its LAST fix is the newest. The pre-fix code took
            // `locations.first`, the stalest fix in the batch.
            if let fix = locations.last {
                receiveFix(fix)
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            receiveError(error)
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
