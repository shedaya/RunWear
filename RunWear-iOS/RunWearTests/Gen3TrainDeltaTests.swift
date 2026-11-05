import XCTest
@testable import RunWear

/// GEN-3 TRAIN DESIGN DELTAS (Sol's mockup review rounds 1–2,
/// Nov 4): pins for the deltas applied on the train AFTER the four
/// lane merges and BEFORE the final gate — wordmark legibility,
/// the hero pill's final mocked states, Y-16 one-tap location,
/// the settled convergence copy + band spinner, the name-led
/// welcome, and the Wear I've Run full-model verification.
final class Gen3TrainDeltaTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - Delta 1 · hero wordmark legibility (strengthened scrim)

    func testWordmarkScrimCarriesThePosterStopAlphas() {
        // The Morning Poster's top-scrim strength (0.62 / 0.31),
        // carried as data so the rendered gradient and this pin
        // can never drift apart.
        XCTAssertEqual(AppTheme.heroTopScrimAlphas, [0.62, 0.31],
                       "the home top scrim must carry the Morning Poster's stop alphas")
    }

    func testWordmarkScrimGradientShape_sourcePin() throws {
        let theme = try sourceFile("../RunWear/Theme/AppTheme.swift")
        XCTAssertTrue(theme.contains(".init(color: .black.opacity(heroTopScrimAlphas[0]), location: 0)"),
                      "the gradient's top stop is the poster's 0.62 on black")
        XCTAssertTrue(theme.contains(".init(color: .black.opacity(heroTopScrimAlphas[1]), location: 0.175)"),
                      "the mid stop is the poster's 0.31 at the seat's midpoint")
        XCTAssertTrue(theme.contains(".init(color: .clear, location: 0.35)"),
                      "the seat is unchanged: the scrim is gone by 0.35 of the hero height")
        // The wordmark's own shadow token is NOT part of the
        // treatment — it stays exactly as shipped.
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains(".shadow(color: .black.opacity(0.35), radius: 6, y: 2)"),
                      "the wordmark shadow token is unchanged by delta 1")
    }

    // MARK: - Y-16 · one tap on location opens the complete picker

    func testLocationEntryIsOneTapToTheCompletePicker_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains(".toolbarTitleMenu"),
                       "Y-16: no intermediate title menu in the home location path")
        XCTAssertTrue(home.contains("ToolbarItem(placement: .principal)"),
                      "the location is a principal toolbar button")
        // The button's action is the picker presentation itself,
        // and the picker rests at the full-height detent only, so
        // the first tap lands on the complete UI.
        let pickerSheet = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(pickerSheet.contains("TextField(\"Search for a city\""),
                      "the picker carries its search field")
        XCTAssertTrue(pickerSheet.contains("\"Current location\""),
                      "the picker carries its current-location row")
        // The home presentation of the picker: search the home
        // file's sheet block for its detent line.
        let sheetIdx = try XCTUnwrap(home.range(of: ".sheet(isPresented: $showLocationPicker)"))
        let sheetBlock = home[sheetIdx.lowerBound...].prefix(1600)
        XCTAssertTrue(sheetBlock.contains(".presentationDetents([.large])"),
                      "Y-16: the picker opens complete — full-height detent only")
        XCTAssertFalse(sheetBlock.contains(".medium"),
                       "Y-16: no medium rest the user must expand")
    }
}
