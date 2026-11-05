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

    // MARK: - Convergence band · settled copy, bar-only progress (count HELD), spinner

    func testConvergenceBandCopyIsSolSettledWordingVerbatim() {
        XCTAssertEqual(ConvergenceBand.heading, "Making your looks")
        XCTAssertEqual(ConvergenceBand.line,
                       "We're generating your outfits across all running conditions, so it will be ready when you need it.")
    }

    /// HOLD (Sol, Nov 4): the numeric count is NOT built — the
    /// earlier "N of your M looks are ready" wording is
    /// superseded. No progress-text API, no count phrasing, and
    /// no hard-coded total anywhere in the band's model or view;
    /// progress is the bar, driven by the real banking fraction.
    func testConvergenceBandCarriesNoNumericCount_sourcePin() throws {
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        let bandStart = try XCTUnwrap(models.range(of: "enum ConvergenceBand")?.lowerBound)
        let bandSlice = String(models[bandStart...].prefix(1500))
        XCTAssertFalse(bandSlice.contains("progressText"),
                       "HOLD: no progress-text copy in the band model")
        XCTAssertFalse(bandSlice.contains("looks are ready"),
                       "HOLD: the superseded count sentence must not exist")
        XCTAssertFalse(bandSlice.contains("of your"),
                       "HOLD: no 'N of your M' count phrasing")
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(views.contains("progressText"),
                       "HOLD: the band view renders no numeric count text")
        XCTAssertTrue(views.contains("ProgressView(value: content.fraction)"),
                      "progress is the bar, from the real banking fraction")
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
        // Fixture numbers are internal banking state only (the
        // bar's fraction) — they are never rendered as a count.
        let partial = BankCoverage(ready: 2, total: 5)
        let content = ConvergenceBand.content(mode: .subscribed, coverage: partial)
        XCTAssertEqual(content?.heading, "Making your looks")
        XCTAssertEqual(content?.fraction ?? -1, 2.0 / 5.0, accuracy: 0.0001)
        // Complete bank → no band.
        XCTAssertNil(ConvergenceBand.content(mode: .subscribed, coverage: BankCoverage(ready: 5, total: 5)))
        // Unknown banking state → no band.
        XCTAssertNil(ConvergenceBand.content(mode: .subscribed, coverage: nil))
        // PREMIUM CONTEXT ONLY: the .free state IS the free /
        // in-trial 5-look allowance — no bank, no band. Stock
        // (never opted in) likewise.
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
                      "the band is seated in the personal section from the store's real banking state")
        XCTAssertTrue(home.contains("ConvergenceBandView(content: band)"))
    }

    // MARK: - Round 2 · Wear I've Run FULL MODEL verification

    private var wirUTCCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }

    private func wirDay(_ year: Int, _ month: Int, _ day: Int) -> Date {
        let comps = DateComponents(year: year, month: month, day: day)
        return wirUTCCalendar.startOfDay(for: wirUTCCalendar.date(from: comps)!)
    }

    private func wirEntry(session: String, place: String, day: Date, hero: String) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: day, placeName: place, feelsLikeF: 50, actualF: 52,
            condition: "Clear", itemNames: ["Tee"], itemKeys: ["tee"],
            heroImageReference: hero, sessionID: session)
    }

    /// The binding shape (Sol's example, scaled): the collection's
    /// TOP LEVEL collates BY LOCATION ACROSS ALL HISTORY — many
    /// sessions accumulate under one location across days; inside
    /// a location, one entry per session, each updated in place
    /// during its own session; past sessions preserved; the pinned
    /// TODAY entry and the current session's chapter entry are
    /// THE SAME entry, never duplicates.
    @MainActor
    func testCollectionCollatesByLocationAcrossAllHistory() {
        let defaults = UserDefaults(suiteName: "gen3-train-wir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)

        // Five New York sessions on five different days, two
        // Miami, one Overland Park — each its own model (session)
        // over the one store, as successive app launches would be.
        let nyDays = [wirDay(2026, 10, 28), wirDay(2026, 10, 30), wirDay(2026, 11, 1),
                      wirDay(2026, 11, 3), wirDay(2026, 11, 4)]
        for (i, day) in nyDays.enumerated() {
            let model = WearIveRunCollectionModel(store: store, sessionID: "ny-\(i)")
            model.recordSettled(wirEntry(session: "ny-\(i)", place: "New York, NY", day: day, hero: "hero-ny-\(i)"))
            if i == nyDays.count - 1 {
                // The current session settles AGAIN (a weather
                // change in-session): it updates in place — the
                // location must NOT gain a sixth entry.
                model.recordSettled(wirEntry(session: "ny-\(i)", place: "New York, NY", day: day, hero: "hero-ny-\(i)-v2"))
            }
        }
        for (i, day) in [wirDay(2026, 10, 29), wirDay(2026, 11, 2)].enumerated() {
            let model = WearIveRunCollectionModel(store: store, sessionID: "mia-\(i)")
            model.recordSettled(wirEntry(session: "mia-\(i)", place: "Miami, FL", day: day, hero: "hero-mia-\(i)"))
        }
        let opModel = WearIveRunCollectionModel(store: store, sessionID: "op-0")
        opModel.recordSettled(wirEntry(session: "op-0", place: "Overland Park, KS", day: wirDay(2026, 11, 3), hero: "hero-op"))

        XCTAssertEqual(store.entries.count, 8, "5 NY + 2 Miami + 1 OP sessions, the in-session update included")

        let chapters = WearIveRunCollection.chapters(from: store.entries, filter: .all, calendar: wirUTCCalendar)
        XCTAssertEqual(chapters.count, 3, "the top level is locations, not sessions")
        let ny = chapters.first { $0.placeName == "New York, NY" }
        XCTAssertEqual(ny?.entries.count, 5, "all five NY sessions accumulate under the one location")
        XCTAssertEqual(ny?.dayCount, 5, "five distinct NY days")
        XCTAssertEqual(ny?.entries.first?.heroImageReference, "hero-ny-4-v2",
                       "the current session's entry is its LAST settled version, newest first")
        XCTAssertEqual(ny?.entries.last?.heroImageReference, "hero-ny-0",
                       "the oldest NY session is preserved at the chapter's foot")
        XCTAssertEqual(chapters.first { $0.placeName == "Miami, FL" }?.entries.count, 2)
        XCTAssertEqual(chapters.first { $0.placeName == "Overland Park, KS" }?.entries.count, 1)
        // Nothing flattens sessions to the top level and nothing
        // caps a location at one entry.
        XCTAssertEqual(chapters.flatMap(\.entries).count, 8)

        // The pinned TODAY entry IS the current session's chapter
        // entry: same identity, and lifting it for the pin leaves
        // the chapter standing on its remaining four sessions —
        // presented exactly once in the collection, as the pin.
        let current = WearIveRunCollectionModel(store: store, sessionID: "ny-4")
        let pinned = current.sessionEntry()
        XCTAssertEqual(pinned?.id, ny?.cover.id, "pin and chapter cover are the same entry")
        let unpinned = store.entries.filter { $0.id != pinned?.id }
        let chaptersAfter = WearIveRunCollection.chapters(from: unpinned, filter: .all, calendar: wirUTCCalendar)
        XCTAssertFalse(chaptersAfter.flatMap(\.entries).contains { $0.id == pinned?.id },
                       "the pinned entry never duplicates into a chapter")
        XCTAssertEqual(chaptersAfter.first { $0.placeName == "New York, NY" }?.entries.count, 4)
    }

    // MARK: - Paywall monthly · Sol's resolution (displayed, secondary)

    func testPaywallDefaultsToAnnualAndMonthlyPriceIsParameterized() {
        XCTAssertEqual(MakeItMePriceLabels.defaultPlan, .annual,
                       "annual is the hero and the default selection")
        // The monthly row's price comes from the fetched product —
        // the existing label seam, never a hardcoded figure.
        XCTAssertEqual(MakeItMePriceLabels.monthlyRowLabel(priceString: "$0.99"), "$0.99 / month")
        XCTAssertEqual(MakeItMePriceLabels.monthlyRowLabel(priceString: nil), "Monthly",
                       "unresolved product → the price-free plan name, never a wrong price")
    }

    func testMonthlyRowIsOneQuietRowUnderTheAnnualHero_sourcePin() throws {
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        let sheetIdx = try XCTUnwrap(views.range(of: "struct MakeItMeSubscriptionSheet"))
        let sheet = views[sheetIdx.lowerBound...]
        let annualIdx = try XCTUnwrap(sheet.range(of: "store.billing.annualPriceString"))
        let finePrintIdx = try XCTUnwrap(sheet.range(of: "store.billing.perRunFinePrint"))
        let monthlyIdx = try XCTUnwrap(sheet.range(of: "store.billing.monthlyRowLabel"))
        XCTAssertTrue(annualIdx.lowerBound < finePrintIdx.lowerBound
                      && finePrintIdx.lowerBound < monthlyIdx.lowerBound,
                      "order: annual hero card → per-run fine print (unchanged seat) → the one monthly row")
        XCTAssertTrue(sheet.contains("@State private var selectedPlan: PaywallPlan = MakeItMePriceLabels.defaultPlan"),
                      "the selection state defaults through the label seam (annual)")
        XCTAssertTrue(sheet.contains("if store.billing.monthlyPriceString != nil"),
                      "the monthly row renders only once its fetched price resolves")
        XCTAssertTrue(sheet.contains("selectedPlan = .monthly"),
                      "tapping the row selects monthly")
        XCTAssertTrue(sheet.contains("await store.billing.purchaseMonthly()"),
                      "the CTA purchases the selected plan")
        XCTAssertTrue(sheet.contains("await store.billing.purchaseAnnual()"))
        // Exactly ONE monthly row in the sheet.
        XCTAssertEqual(sheet.components(separatedBy: "store.billing.monthlyRowLabel").count - 1, 2,
                       "the label is read twice (row + its accessibility label) — one row, not two")
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
