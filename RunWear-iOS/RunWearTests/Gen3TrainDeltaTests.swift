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

    // MARK: - Round 2 · the name-led welcome (Sol's chosen composition)

    func testWelcomePointsAreNameLedWithSolFinalWording() {
        let points = OnboardingCopy.welcomePoints
        XCTAssertEqual(points.count, 3)
        // Point 1: name + its existing plain phrase, unchanged.
        XCTAssertEqual(points[0].name, "RunWear")
        XCTAssertEqual(points[0].phrase,
                       "One recommendation for the run you're about to take — feels-like, wind, and rain included.")
        // Point 2: Sol's FINAL wording (last ruling, Nov 4).
        XCTAssertEqual(points[1].name, "Make It Me")
        XCTAssertEqual(points[1].phrase,
                       "Picture yourself at the start line with the perfect outfit ready to go.")
        // Point 3: the round-2 phrase (the name-led form of the
        // WS-E line — the name no longer repeats inside it).
        XCTAssertEqual(points[2].name, "Wear I've Run")
        XCTAssertEqual(points[2].phrase,
                       "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.")
        // No phrase repeats its feature name.
        for point in points {
            XCTAssertFalse(point.phrase.contains(point.name),
                           "the phrase must not repeat its feature name: \(point.name)")
        }
    }

    func testWelcomeRowRendersNameInPineBold_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("welcomePointRow(icon: \"cloud\", point: OnboardingCopy.welcomePoints[0])"))
        XCTAssertTrue(onboarding.contains("welcomePointRow(icon: \"person\", point: OnboardingCopy.welcomePoints[1])"))
        XCTAssertTrue(onboarding.contains("welcomePointRow(icon: \"rectangle.stack\", point: OnboardingCopy.welcomePoints[2])"),
                      "the settled layout and icons stand, in order")
        XCTAssertTrue(onboarding.contains(".fontWeight(.bold)"),
                      "the feature name leads in bold")
        XCTAssertTrue(onboarding.contains(".foregroundColor(AppTheme.pinePayload)"),
                      "the feature name leads in pine")
        XCTAssertFalse(onboarding.contains("Remember every run"),
                       "the superseded WS-E headline is gone")
    }

    // MARK: - First-look rulings · the generating screen is never a gate

    func testFirstLookWayForwardIsUnconditional_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        let momentIdx = try XCTUnwrap(onboarding.range(of: "private var firstLookMomentView"))
        let moment = onboarding[momentIdx.lowerBound...]
        let doorIdx = try XCTUnwrap(moment.range(of: "Text(\"See My Outfit\")"))
        // The door's button opens directly on onComplete and is
        // not wrapped in any phase condition: the characters
        // immediately before it are the ruling's own comment and
        // the button declaration — no `if`, no `.disabled`.
        let before = moment[moment.startIndex..<doorIdx.lowerBound].suffix(500)
        XCTAssertTrue(before.hasSuffix("Button(action: onComplete) {\n                    "),
                      "the way forward is a plain onComplete button, present in every phase")
        XCTAssertFalse(before.contains(".disabled"),
                       "the way forward is never disabled")
        // …and the premium door (which IS phase-gated) is a
        // different button, after it.
        let premiumIdx = try XCTUnwrap(moment.range(of: "Text(\"Upgrade to Premium\")"))
        XCTAssertTrue(doorIdx.lowerBound < premiumIdx.lowerBound)
    }

    func testLeavingMidGenerationLandsOnTheGeneratingPill() {
        // The home landing state after leaving frame 34
        // mid-generation: the shared store still reports the
        // requested cell as making, no personal photo is on
        // screen yet, and the hero pill resolves to .making —
        // the generating pill over the fallback photo.
        XCTAssertEqual(
            .making,
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5),
            "leaving mid-generation must land on home with the generating pill active")
    }

    // MARK: - Convergence band · settled copy, real counts, spinner

    func testConvergenceBandCopyIsSolSettledWordingVerbatim() {
        XCTAssertEqual(ConvergenceBand.heading, "Making your looks")
        XCTAssertEqual(ConvergenceBand.line,
                       "We're generating your outfits across all running conditions, so it will be ready when you need it.")
        // Plain-words progress with the real counts substituted —
        // the pattern from Sol's settled wording, never a bare
        // fraction and never "N of M ready".
        XCTAssertEqual(ConvergenceBand.progressText(ready: 14, total: 24),
                       "14 of your 24 looks are ready")
        XCTAssertEqual(ConvergenceBand.progressText(ready: 0, total: 48),
                       "0 of your 48 looks are ready")
    }

    func testConvergenceCoverageDerivation() {
        // ready = banked set; total = banked ∪ ever-enqueued.
        let cov = deriveBankCoverage(heroCells: ["a", "b"], jobCells: ["b", "c", "d"])
        XCTAssertEqual(cov.ready, 2)
        XCTAssertEqual(cov.total, 4)
        // Before any fill: the union is just the banked set.
        let preFill = deriveBankCoverage(heroCells: ["a"], jobCells: [])
        XCTAssertEqual(preFill.ready, preFill.total)
    }

    func testConvergenceBandVisibilityRule() {
        let partial = BankCoverage(ready: 14, total: 24)
        let content = ConvergenceBand.content(mode: .subscribed, coverage: partial)
        XCTAssertEqual(content?.heading, "Making your looks")
        XCTAssertEqual(content?.progressText, "14 of your 24 looks are ready")
        XCTAssertEqual(content?.fraction ?? -1, 14.0 / 24.0, accuracy: 0.0001)
        // Complete bank → no band.
        XCTAssertNil(ConvergenceBand.content(mode: .subscribed, coverage: BankCoverage(ready: 24, total: 24)))
        // Unknown counts → no band (never an invented number).
        XCTAssertNil(ConvergenceBand.content(mode: .subscribed, coverage: nil))
        // Not subscribed → no band.
        XCTAssertNil(ConvergenceBand.content(mode: .free, coverage: partial))
        XCTAssertNil(ConvergenceBand.content(mode: .stock, coverage: partial))
        // Empty engagement → no band.
        XCTAssertNil(ConvergenceBand.content(mode: .subscribed, coverage: BankCoverage(ready: 0, total: 0)))
    }

    func testConvergenceBandRendersWithThePillSpinner_sourcePin() throws {
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(views.contains("struct ConvergenceBandView"),
                      "the band's view exists")
        XCTAssertTrue(views.contains("ProgressView()\n                    .controlSize(.mini)"),
                      "ROUND 2: the band carries the generating pill's spinner device (ProgressView, mini)")
        XCTAssertTrue(views.contains("ProgressView(value: content.fraction)"),
                      "the band carries the simple progress bar")
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("ConvergenceBand.content(mode: personalStore.mode, coverage: personalStore.bankCoverage)"),
                      "the band is seated in the personal section from the store's real counts")
        XCTAssertTrue(home.contains("ConvergenceBandView(content: band)"))
    }

    // MARK: - First-look rulings · finished CTA wording

    func testFinishedCTAReadsUpgradeToPremium_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("Text(\"Upgrade to Premium\")"),
                      "the first-look finished surface's CTA is 'Upgrade to Premium'")
        XCTAssertFalse(onboarding.contains("Text(\"See Premium\")"),
                       "'See Premium' is gone from the first-look surface")
        // Scope: Settings' approved wording does not move.
        let settings = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(settings.contains("Text(\"Get Premium\")"),
                      "Settings' 'Get Premium' is approved as mocked and stays")
    }
}
