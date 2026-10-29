import Foundation
import RunWearEngine

/// Running-Specific Clothing Recommendation Engine (v4.3) — Swift facade.
///
/// Stage 2 of the iOS parity plan (Route C, 2026-10-29): the decision
/// tables that used to be hand-ported in this file are DELETED. The one
/// implementation now lives in the shared Kotlin module
/// (RunWear-android `:engine`, GetOutfitRecommendationUseCase) and this
/// service delegates to it through the RunWearEngine framework via
/// EngineBridge. The platforms cannot silently drift again: there is
/// nothing left here to drift.
///
/// What remains Swift-side, deliberately:
/// - This facade's signature — the Stage 1 boundary contract (see below).
/// - `TemperatureBracket`, the iOS-only 8-band presentation bracket
///   (R-18) computed from the adjusted temperature for UI copy/color.
/// - Item presentation (SF Symbol icons), in EngineBridge.
///
/// The frames this facade forwards are the v4.3 semantics the Kotlin
/// tables implement:
/// - Warmth selectors read the comfort-ADJUSTED feels-like temperature.
/// - The visor/cap sun gates read the RAW feels-like temperature (v4.3:
///   sun protection is not layering — a comfort profile must not put a
///   cap on, or take one off).
/// - The rain jacket gate is measured rain AND precipitation > 0.1 mm;
///   the rain cap and rain tip key off measured rain alone.
/// - The wind gate is mph (callers request mph from Open-Meteo);
///   humidity is the real percent (the humidity tip fires above 65%).
class OutfitRecommendationService {
    static let shared = OutfitRecommendationService()

    /// Engine version, forwarded from the shared module — the value the
    /// feedback bundle names is the value the Kotlin engine reports
    /// (RunWearEngineInfo.ENGINE_VERSION == the use case's constant).
    static let engineVersion: String = RunWearEngineInfo.shared.ENGINE_VERSION

    private init() {}

    /// Generate outfit recommendation from weather conditions.
    /// - Parameters:
    ///   - temperature: Feels-like temperature in Fahrenheit, AFTER the comfort
    ///     adjustment — the frame every warmth selector reads (the Kotlin
    ///     engine's `adjustedTemp`).
    ///   - rawTemperature: Feels-like temperature in Fahrenheit BEFORE the
    ///     comfort adjustment (the Kotlin engine's `baseTemp`). Read ONLY by
    ///     the visor/cap sun gates (v4.3); required so no caller can silently
    ///     keep the pre-v4.3 single-frame behavior.
    ///   - isRaining: Whether rain is actually falling, from MEASURED rain
    ///     (the Kotlin engine's `WeatherConditions.isRaining` = rain > 0),
    ///     derived by the caller from the hourly/current rain amount —
    ///     never from the weather-code class. Required, like rawTemperature,
    ///     so no caller can silently substitute the code class for the
    ///     measurement.
    ///   - windSpeed: Wind speed in mph (default 0) — the Kotlin gate's
    ///     frame; callers request mph from Open-Meteo.
    ///   - humidity: Humidity percentage (default 50)
    ///   - uvIndex: UV index (default 0)
    ///   - precipitation: Precipitation amount in mm (default 0)
    ///   - hour: Current hour (default 12)
    func getRecommendation(
        for temperature: Double,
        rawTemperature: Double,
        isRaining: Bool,
        windSpeed: Double = 0,
        humidity: Int = 50,
        uvIndex: Double = 0,
        precipitation: Double = 0,
        hour: Int = 12
    ) -> OutfitRecommendation {
        let selection = EngineBridge.engine.recommendFrames(
            adjustedFeelsLikeF: temperature,
            rawFeelsLikeF: rawTemperature,
            isRaining: isRaining,
            precipitation: precipitation,
            isWindy: windSpeed > 10, // mph — the Kotlin gate (WeatherConditions.isWindy), in the Kotlin frame
            isHumid: humidity > 65, // the Kotlin gate (WeatherConditions.isHumid)
            uvIndex: uvIndex,
            hourOfDay: Int32(hour)
        )

        return OutfitRecommendation(
            temperatureBracket: TemperatureBracket.from(temperature: temperature),
            topBase: EngineBridge.item(from: selection.topBase),
            topMid: selection.topMid.map(EngineBridge.item(from:)),
            topOuter: selection.topOuter.map(EngineBridge.item(from:)),
            bottom: EngineBridge.item(from: selection.bottom),
            head: selection.head.map(EngineBridge.item(from:)),
            hands: selection.hands.map(EngineBridge.item(from:)),
            accessories: selection.accessories.map(EngineBridge.item(from:)),
            tips: selection.tips.map(EngineBridge.stripTipEmoji)
        )
    }
}
