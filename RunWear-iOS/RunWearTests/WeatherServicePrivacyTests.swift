import XCTest

/// Coarse Location contract (Sol's ruling, 2026-10-30).
///
/// RunWear originally sent full-precision coordinates to the weather
/// API (`String(latitude)` at full Double resolution), which forced
/// the privacy manifest to declare Precise Location. Sol ruled the
/// AccliMate posture instead: round the coordinates in the outbound
/// weather query to 1 decimal place (~11 km) and declare Coarse.
///
/// The rounding lives in exactly one place — the request boundary in
/// `WeatherService.forecastURL`, through which both the app and the
/// watch target fetch. These tests pin that boundary: the constructed
/// query carries 1-decimal coordinates, and a full-precision input
/// can never reach the URL. Stored, displayed, and persisted
/// locations keep full precision — nothing here touches them.
final class WeatherServicePrivacyTests: XCTestCase {

    private func queryValue(_ name: String, in url: URL?) -> String? {
        guard let url,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else { return nil }
        return components.queryItems?.first(where: { $0.name == name })?.value
    }

    /// NYC at GPS/manual-search precision, the manifest truth-pass's
    /// flow-1 example: full Double resolution in, 1 decimal out.
    func testForecastURLCarriesOneDecimalCoordinates() {
        let url = WeatherService.forecastURL(latitude: 40.7128, longitude: -73.9857)

        XCTAssertEqual(queryValue("latitude", in: url), "40.7")
        XCTAssertEqual(queryValue("longitude", in: url), "-74.0")
    }

    /// Regression: no full-precision coordinate string may appear
    /// anywhere in the constructed request URL.
    func testFullPrecisionInputNeverReachesTheURL() {
        let url = WeatherService.forecastURL(latitude: 40.7128, longitude: -73.9857)
        let absolute = url?.absoluteString ?? ""

        XCTAssertFalse(absolute.contains("40.7128"), "full-precision latitude leaked into the weather URL")
        XCTAssertFalse(absolute.contains("73.9857"), "full-precision longitude leaked into the weather URL")

        // Every coordinate value in the query has at most one
        // fractional digit (a bare integer or a trailing-zero form
        // like "-74.0" both satisfy the 1-decimal contract).
        for name in ["latitude", "longitude"] {
            let value = queryValue(name, in: url) ?? ""
            let fractional = value.split(separator: ".").dropFirst().first ?? ""
            XCTAssertLessThanOrEqual(fractional.count, 1, "\(name)=\(value) carries more than 1 decimal place")
        }
    }

    /// The rounding rule itself: nearest 1-decimal value, negatives
    /// included, and already-coarse inputs pass through unchanged.
    func testCoarseCoordinateRoundsToOneDecimal() {
        XCTAssertEqual(WeatherService.coarseCoordinate(40.7128), 40.7, accuracy: 1e-9)
        XCTAssertEqual(WeatherService.coarseCoordinate(-73.9857), -74.0, accuracy: 1e-9)
        XCTAssertEqual(WeatherService.coarseCoordinate(51.5074), 51.5, accuracy: 1e-9)
        XCTAssertEqual(WeatherService.coarseCoordinate(40.7), 40.7, accuracy: 1e-9)
        XCTAssertEqual(WeatherService.coarseCoordinate(0.0), 0.0, accuracy: 1e-9)
    }
}
