import Foundation
import WatchConnectivity

/// Manages Watch Connectivity for syncing preferences between iOS and watchOS
class WatchConnectivityManager: NSObject, ObservableObject {
    static let shared = WatchConnectivityManager()

    @Published var genderPreference: GenderPreference = .unisex
    @Published var temperatureUnit: TemperatureUnit = .fahrenheit
    @Published var comfortLevel: ComfortLevel = .neutral

    private override init() {
        super.init()
        loadLocalPreferences()

        if WCSession.isSupported() {
            let session = WCSession.default
            session.delegate = self
            session.activate()
        }
    }

    // MARK: - Local Persistence

    private func loadLocalPreferences() {
        if let saved = UserDefaults.standard.string(forKey: "genderPreference"),
           let preference = GenderPreference(rawValue: saved) {
            genderPreference = preference
        }

        if let saved = UserDefaults.standard.string(forKey: "temperatureUnit"),
           let unit = TemperatureUnit(rawValue: saved) {
            temperatureUnit = unit
        }

        if let saved = UserDefaults.standard.object(forKey: "comfortLevel") as? Int,
           let level = ComfortLevel(rawValue: saved) {
            comfortLevel = level
        }
    }

    private func saveLocalPreferences() {
        UserDefaults.standard.set(genderPreference.rawValue, forKey: "genderPreference")
        UserDefaults.standard.set(temperatureUnit.rawValue, forKey: "temperatureUnit")
        UserDefaults.standard.set(comfortLevel.rawValue, forKey: "comfortLevel")
    }

    // MARK: - Sync to Watch

    func syncPreferencesToWatch() {
        guard WCSession.default.activationState == .activated else { return }

        let context: [String: Any] = [
            "genderPreference": genderPreference.rawValue,
            "temperatureUnit": temperatureUnit.rawValue,
            "comfortLevel": comfortLevel.rawValue
        ]

        do {
            try WCSession.default.updateApplicationContext(context)
        } catch {
            print("WatchConnectivity: Failed to sync to watch: \(error)")
        }
    }

    func updateGenderPreference(_ preference: GenderPreference) {
        genderPreference = preference
        saveLocalPreferences()
        syncPreferencesToWatch()
    }

    func updateTemperatureUnit(_ unit: TemperatureUnit) {
        temperatureUnit = unit
        saveLocalPreferences()
        syncPreferencesToWatch()
    }

    func updateComfortLevel(_ level: ComfortLevel) {
        comfortLevel = level
        saveLocalPreferences()
        syncPreferencesToWatch()
    }
}

// MARK: - WCSessionDelegate

extension WatchConnectivityManager: WCSessionDelegate {
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated {
            syncPreferencesToWatch()
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {
        // Required for iOS
    }

    func sessionDidDeactivate(_ session: WCSession) {
        // Required for iOS
        WCSession.default.activate()
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        DispatchQueue.main.async { [weak self] in
            self?.processReceivedContext(applicationContext)
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        DispatchQueue.main.async { [weak self] in
            self?.processReceivedContext(message)
        }
    }

    private func processReceivedContext(_ context: [String: Any]) {
        if let genderRaw = context["genderPreference"] as? String,
           let gender = GenderPreference(rawValue: genderRaw) {
            genderPreference = gender
        }

        if let unitRaw = context["temperatureUnit"] as? String,
           let unit = TemperatureUnit(rawValue: unitRaw) {
            temperatureUnit = unit
        }

        if let levelRaw = context["comfortLevel"] as? Int,
           let level = ComfortLevel(rawValue: levelRaw) {
            comfortLevel = level
        }

        saveLocalPreferences()
    }
}
