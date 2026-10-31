import Foundation
import RunWearEngine

/// Bridge from the shared Kotlin engine — the `RunWearEngine` framework,
/// built from RunWear-android's `:engine` KMP module — to this app's Swift
/// models. Stage 2 of the iOS parity plan (Route C, 2026-10-29): the
/// decision tables live ONLY in the Kotlin module now; this file is the
/// entire seam between them and Swift.
///
/// What crosses the seam:
/// - Decisions: `OutfitSelection` (item slots + tips) from
///   `GetOutfitRecommendationUseCase.recommendFrames`, the frame-form entry
///   point whose inputs are the Stage 1 boundary contract.
/// - Item identity: the Kotlin enum case name (`key`), mapped here to the
///   Swift `ClothingItem` struct. Name/description/search term come from
///   the Kotlin item (single-sourced); the icon stays Swift-side because
///   presentation differs — Kotlin's `icon` is an emoji for Android's UI,
///   Swift renders SF Symbols.
/// - Tips: the engine's tips carry a leading emoji (Android renders them
///   as-is); Swift tips never did, so the bridge strips it.
///
/// The framework is a CI-built artifact (see ios-tests.yml); it is not
/// committed. Every target that compiles this file also links and embeds
/// RunWearEngine.xcframework.
enum EngineBridge {

    /// The one engine instance. The Kotlin use case is stateless.
    static let engine = GetOutfitRecommendationUseCase()

    /// SF Symbol per engine item key — the values the deleted Swift port
    /// assigned per item. Keys the engine never emits (LIGHT_VEST,
    /// LIGHT_JACKET, CAPRIS) are included so the map is total over the
    /// enum; they fall back to the generic shirt symbol the port used
    /// for base layers.
    private static let iconByKey: [String: String] = [
        "TANK_TOP": "tshirt.fill",
        "SHORT_SLEEVE": "tshirt.fill",
        "LONG_SLEEVE_LIGHT": "tshirt.fill",
        "LONG_SLEEVE_THERMAL": "tshirt.fill",
        "QUARTER_ZIP": "tshirt.fill",
        "FLEECE_PULLOVER": "tshirt.fill",
        "HALF_ZIP_THERMAL": "tshirt.fill",
        "LIGHT_VEST": "tshirt.fill",
        "WINDBREAKER": "wind",
        "LIGHT_JACKET": "tshirt.fill",
        "RAIN_JACKET": "cloud.rain.fill",
        "INSULATED_JACKET": "snow",
        "SHORT_SHORTS": "figure.run",
        "SHORTS": "figure.run",
        "CAPRIS": "figure.run",
        "LIGHT_TIGHTS": "figure.run",
        "THERMAL_TIGHTS": "figure.run",
        "VISOR": "sun.max.fill",
        "BASEBALL_CAP": "baseball.cap.fill",
        "HEADBAND": "ear.fill",
        "LIGHT_BEANIE": "brain.head.profile",
        "THERMAL_BEANIE": "brain.head.profile",
        "BALACLAVA": "face.dashed",
        "LIGHT_GLOVES": "hand.raised.fill",
        "THERMAL_GLOVES": "hand.raised.fill",
        "MITTENS": "hand.raised.fill",
        "SUNGLASSES": "sunglasses.fill",
        "SUNSCREEN": "sun.max.fill",
        "REFLECTIVE_GEAR": "light.beacon.max.fill",
        "NECK_GAITER": "scribble.variable",
    ]

    /// Map one engine item to the Swift presentation struct.
    static func item(from engineItem: RunWearEngine.ClothingItem) -> ClothingItem {
        ClothingItem(
            key: engineItem.key,
            name: engineItem.displayName,
            // NOT engineItem.description: that name is NSObject's on
            // this side of the seam and yields the enum case name
            // ("LONG_SLEEVE_LIGHT") — the raw-identifier subtitles of
            // the 2026-10-31 field finding. The engine exposes the
            // human string as descriptionText for this bridge.
            description: engineItem.descriptionText,
            icon: iconByKey[engineItem.key] ?? "tshirt.fill",
            amazonSearchTerm: engineItem.amazonSearchTerm
        )
    }

    /// Strip the engine tip's leading emoji grapheme + following space,
    /// when present ("🏃 You should feel…" → "You should feel…"). A tip
    /// that starts with a letter or digit is returned untouched.
    static func stripTipEmoji(_ tip: String) -> String {
        guard let first = tip.first, !first.isLetter, !first.isNumber else { return tip }
        var rest = tip.dropFirst()
        while rest.first == " " { rest = rest.dropFirst() }
        return String(rest)
    }
}
