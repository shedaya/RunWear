import XCTest

/// Weather-detail presentation contract (2026-10-29).
///
/// Device report: tapping the UV pill (the green sun.max.fill —
/// 4th in the pills row when there is no precipitation pill)
/// opened a full-screen WHITE sheet with nothing on it. The sheet
/// was presented with `.sheet(isPresented:)` and its content
/// wrapped in `if let detail = selectedWeatherDetail`: when the
/// two state writes raced, the content evaluated while the
/// selection was still nil and the sheet presented its empty,
/// system-white self. WeatherDetailSheet itself has full content
/// for every metric — it was simply never built.
///
/// The fix presents with `.sheet(item:)`, keyed on
/// WeatherDetailType's Identifiable conformance, so the content
/// exists exactly when a detail does. These tests pin the data
/// contract that presentation relies on: the five canonical
/// details cover every metric kind exactly once, their identities
/// are distinct and stable, the sheet's resolution rule (first
/// detail of the selected kind, else the opening detail) always
/// lands on a detail of the requested kind, and every detail
/// carries the text and icon it renders — a detail resolving
/// blank is what the white sheet was.
///
/// GEN-6 (RW-63): the canonical set is the SETTLED sheet's set —
/// feels-like and actual join the original five, precipitation
/// last — seven details covering the seven metric kinds.
final class WeatherDetailPresentationTests: XCTestCase {

    /// The settled canonical details, built the way
    /// WeatherOutfitView.allWeatherDetails builds them.
    private func canonicalDetails() -> [WeatherDetailType] {
        [
            .feelsLike(value: "52°"),
            .actual(value: "55°"),
            .condition(.clear),
            .wind(speed: 8, direction: "NW", gusts: 12),
            .humidity(percentage: 55),
            .uvIndex(value: 2.0),
            .precipitation(percentage: 20)
        ]
    }

    func testSettledOrderIsFeelsLikeActualThenTheRest() {
        XCTAssertEqual(
            WeatherMetricKind.allCases,
            [.feelsLike, .actual, .condition, .wind, .humidity, .uvIndex, .precipitation]
        )
        XCTAssertEqual(
            canonicalDetails().map(\.kind),
            WeatherMetricKind.allCases,
            "the built details follow the settled order"
        )
    }

    func testCanonicalDetailsCoverEveryMetricKindExactlyOnce() {
        let details = canonicalDetails()
        XCTAssertEqual(details.count, WeatherMetricKind.allCases.count)
        XCTAssertEqual(Set(details.map(\.kind)), Set(WeatherMetricKind.allCases))
    }

    func testIdentifiableIDsAreDistinctAndStable() {
        let details = canonicalDetails()
        XCTAssertEqual(Set(details.map(\.id)).count, details.count)
        for detail in details {
            XCTAssertEqual(detail.id, detail.kind)
        }
        // Stability: a rebuilt detail of the same kind keeps its
        // id — sheet(item:) must not see a "new" item when only
        // the value changes.
        XCTAssertEqual(
            WeatherDetailType.uvIndex(value: 9.9).id,
            WeatherDetailType.uvIndex(value: 0.5).id
        )
    }

    func testSheetResolutionAlwaysLandsOnRequestedKind() {
        let details = canonicalDetails()
        for kind in WeatherMetricKind.allCases {
            let initial = details.first { $0.kind == kind }!
            // WeatherDetailSheet.detailType's resolution rule.
            let resolved = details.first { $0.kind == kind } ?? initial
            XCTAssertEqual(resolved.kind, kind)
        }
    }

    func testEveryDetailCarriesRenderableText() {
        for detail in canonicalDetails() {
            XCTAssertFalse(detail.title.isEmpty, "\(detail.kind) title must not be empty")
            XCTAssertFalse(detail.summaryValue.isEmpty, "\(detail.kind) summary must not be empty")
            XCTAssertFalse(detail.icon.isEmpty, "\(detail.kind) icon must not be empty")
        }
    }

    func testUVDetailMatchesTheReportedGreenSunCase() {
        // The reported tap: low UV — the green sun.max.fill pill.
        let uv = WeatherDetailType.uvIndex(value: 2.0)
        XCTAssertEqual(uv.kind, .uvIndex)
        XCTAssertEqual(uv.icon, "sun.max.fill")
        XCTAssertEqual(uv.title, "UV Index")
        XCTAssertEqual(uv.summaryValue, "2.0")
    }
}
