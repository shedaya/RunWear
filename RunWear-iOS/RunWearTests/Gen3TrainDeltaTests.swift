import XCTest

// NOTE: no `@testable import RunWear` here — deliberately, like
// every other file in this suite. The test target compiles the
// model-layer sources directly; an app-module import makes the
// build's module scan depend on the app target's emission and
// failed the GEN-3 gate twice (41940827083, 41942526225:
// "unable to resolve module dependency: 'RunWear'"). Do not
// re-add it.

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
        // The button's action is the picker presentation itself:
        // the first tap opens the sheet directly — no menu, no
        // intermediate step — and the sheet's content is the
        // complete picker (search field, current location,
        // results) from the first moment.
        let pickerSheet = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        // GEN-5 (RW-28 item 31): the field's prompt is the
        // settled "Search a place".
        XCTAssertTrue(pickerSheet.contains("TextField(\"Search a place\""),
                      "the picker carries its search field")
        XCTAssertTrue(pickerSheet.contains("\"Current location\""),
                      "the picker carries its current-location row")
        // The home presentation of the picker: its detents are
        // the settled sheet discipline (§1.3 — Location keeps a
        // medium detent, per SheetDisciplineTests); Y-16's fix
        // was the removed menu, not the detent. (An earlier
        // version of this pin demanded [.large] only and
        // contradicted the discipline pin — the gate caught it,
        // run 41944552163.)
        let sheetIdx = try XCTUnwrap(home.range(of: ".sheet(isPresented: $showLocationPicker)"))
        let sheetBlock = home[sheetIdx.lowerBound...].prefix(1600)
        XCTAssertTrue(sheetBlock.contains(".presentationDetents([.medium, .large])"),
                      "Y-16: the directly-opened picker rests at the settled detents")
    }

    // MARK: - Round 2 · the name-led welcome (Sol's chosen composition)

    func testWelcomePointsAreNameLedWithSolFinalWording() {
        let points = OnboardingCopy.welcomePoints
        XCTAssertEqual(points.count, 3)
        // GEN-5 (RW-28 item 27, census A-1) restructures the
        // point: the name leads with the settled SHORT headline
        // phrase ("Name — phrase", pine bold), and Sol's Nov 4
        // final wording survives verbatim as the separate BODY
        // line. The headline phrases are pinned in
        // Gen5FidelityTests; the bodies are pinned here.
        // Point 1: name + its existing body sentence, unchanged.
        XCTAssertEqual(points[0].name, "RunWear")
        XCTAssertEqual(points[0].body,
                       "One recommendation for the run you're about to take — feels-like, wind, and rain included.")
        // Point 2: Sol's FINAL wording (last ruling, Nov 4).
        XCTAssertEqual(points[1].name, "Make It Me")
        XCTAssertEqual(points[1].body,
                       "Picture yourself at the start line with the perfect outfit ready to go.")
        // Point 3: the round-2 body (the name-led form of the
        // WS-E line — the name no longer repeats inside it).
        XCTAssertEqual(points[2].name, "Wear I've Run")
        XCTAssertEqual(points[2].body,
                       "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.")
        // Neither the headline phrase nor the body repeats its
        // feature name.
        for point in points {
            XCTAssertFalse(point.phrase.contains(point.name),
                           "the phrase must not repeat its feature name: \(point.name)")
            XCTAssertFalse(point.body.contains(point.name),
                           "the body must not repeat its feature name: \(point.name)")
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
        // screen yet, and the hero pill resolves to the
        // generating pill over the fallback photo. GEN-5
        // (C-4 AMENDED, Nov 5): for this FREE user the
        // generating state is .makingWithUpsell — script +
        // spinner + "Making this you…" + the one small green
        // "Get it instantly next time" line; a subscriber's
        // generating pill stays the clean .making.
        XCTAssertEqual(
            .makingWithUpsell,
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5),
            "leaving mid-generation must land on home with the generating pill active")
    }

    // MARK: - Onboarding · the three preference questions COMBINED (Sol RULED, Nov 4)

    /// The combined screen's copy, verbatim: headline, lede, and
    /// the three questions in their shipped wording and order.
    func testCombinedPreferencesCopyVerbatim() {
        XCTAssertEqual(OnboardingCopy.preferencesHeadline, "Make it yours")
        XCTAssertEqual(OnboardingCopy.preferencesLede,
                       "Three quick calls. You can change any of them later in Settings.")
        XCTAssertEqual(OnboardingCopy.preferencesQuestions, [
            "How do you measure the weather?",
            "Whose fit should the outfits follow?",
            "Do you get cold easily, or overheat quickly?",
        ])
    }

    /// Option fidelity is binding (shipped iOS wording/options):
    /// full-word Fahrenheit / Celsius; Men's / Women's / No
    /// preference — ALL THREE (the design frame dropping "No
    /// preference" is NOT followed); five slider stops.
    func testCombinedPreferencesOptionFidelity() {
        XCTAssertEqual(TemperatureUnit.allCases.map(\.label), ["Fahrenheit", "Celsius"],
                       "temperature unit options are the full words, in order")
        XCTAssertEqual(GenderPreference.fitOptions.map(\.label), ["Men's", "Women's", "No preference"],
                       "fit options are all three shipped choices, in order")
        XCTAssertEqual(ComfortLevel.allCases.count, 5,
                       "body temperature keeps its five stops")
    }

    /// The combined screen's composition: one pushed destination
    /// carrying all three shared controls under the ruled
    /// headline/lede, each question heading its control in order;
    /// the substantive steps stay separate screens.
    func testCombinedPreferencesScreenComposition_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("private var preferencesScreen"),
                      "the combined screen exists as its own view")
        let start = try XCTUnwrap(onboarding.range(of: "private var preferencesScreen")?.lowerBound)
        let end = try XCTUnwrap(onboarding.range(of: "private func preferenceGroup")?.lowerBound)
        let screen = String(onboarding[start..<end])
        XCTAssertTrue(screen.contains("Text(OnboardingCopy.preferencesHeadline)"),
                      "the headline renders from the ruled copy")
        XCTAssertTrue(screen.contains("Text(OnboardingCopy.preferencesLede)"),
                      "the lede renders from the ruled copy")
        // Each question heads its control, in the ruled order.
        let q0 = try XCTUnwrap(screen.range(of: "preferencesQuestions[0]")?.lowerBound)
        let c0 = try XCTUnwrap(screen.range(of: "TemperatureUnitPicker(unit: $temperatureUnit)")?.lowerBound)
        let q1 = try XCTUnwrap(screen.range(of: "preferencesQuestions[1]")?.lowerBound)
        let c1 = try XCTUnwrap(screen.range(of: "FitPreferenceInlinePicker(preference: $genderPreference)")?.lowerBound)
        let q2 = try XCTUnwrap(screen.range(of: "preferencesQuestions[2]")?.lowerBound)
        let c2 = try XCTUnwrap(screen.range(of: "ComfortLevelControl(comfortLevel: $comfortLevel)")?.lowerBound)
        XCTAssertTrue(q0 < c0 && c0 < q1 && q1 < c1 && c1 < q2 && q2 < c2,
                      "question → control, three times, in order, on the one screen")
        // The slider control itself carries the shipped end
        // labels over five stops.
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertTrue(controls.contains("Text(\"Runs Cold\")") && controls.contains("Text(\"Runs Warm\")"),
                      "the slider's end labels are the shipped Runs Cold / Runs Warm")
        XCTAssertTrue(controls.contains("in: 0...4, step: 1"),
                      "the slider is five stops")
        // The substantive steps remain their own screens.
        XCTAssertTrue(onboarding.contains("case .location:"),
                      "location stays a separate step/screen")
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
        // The rule's premium input is the seats' subscribed flag
        // (both seats pass `mode == .subscribed` — pinned at the
        // seats below): subscribed is the one true case; every
        // non-subscribed mode (free / stock) maps to false.
        let partial = BankCoverage(ready: 2, total: 5)
        let content = ConvergenceBand.content(isSubscribed: true, coverage: partial)
        XCTAssertEqual(content?.heading, "Making your looks")
        XCTAssertEqual(content?.fraction ?? -1, 2.0 / 5.0, accuracy: 0.0001)
        // Complete bank → no band.
        XCTAssertNil(ConvergenceBand.content(isSubscribed: true, coverage: BankCoverage(ready: 5, total: 5)))
        // Unknown banking state → no band.
        XCTAssertNil(ConvergenceBand.content(isSubscribed: true, coverage: nil))
        // PREMIUM CONTEXT ONLY: not subscribed — the free /
        // in-trial 5-look allowance state and the stock state
        // alike — no bank, no band.
        XCTAssertNil(ConvergenceBand.content(isSubscribed: false, coverage: partial))
        // Empty engagement → no band.
        XCTAssertNil(ConvergenceBand.content(isSubscribed: true, coverage: BankCoverage(ready: 0, total: 0)))
    }

    /// WS-D ADDITION (Sol, Nov 4): the Settings Make It Me
    /// section's subscribed state renders the SAME shared band
    /// element while the bank is generating — one element, two
    /// seats, driven by the one content rule (the behavioural
    /// half of the rule — subscribed+generating renders,
    /// subscribed+complete doesn't, free never — is pinned by
    /// testConvergenceBandVisibilityRule above, which both seats
    /// share).
    func testConvergenceBandRendersInSettingsSubscribedState_sourcePin() throws {
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        let sectionStart = try XCTUnwrap(views.range(of: "struct MakeItMeSettingsSection")?.lowerBound)
        let sectionEnd = try XCTUnwrap(views.range(of: "struct MakeItMePhotosView")?.lowerBound)
        let section = String(views[sectionStart..<sectionEnd])
        XCTAssertTrue(section.contains("ConvergenceBand.content("),
                      "the Settings seat is driven by the shared content rule over the store's banking state")
        XCTAssertTrue(section.contains("ConvergenceBandView(content: band)"),
                      "Settings renders the SHARED ConvergenceBandView — not a re-implementation")
        // GEN-5 (RW-28 item 22 + addendum 5.2b) RELOCATES the
        // seat: while generating, the band REPLACES the status
        // block inside statusSection — it no longer sits in the
        // offer group below a status card that simultaneously
        // claims readiness (the gen-4 B-11/B-12 defect: band
        // nested under "You're subscribed." at ~half coverage).
        let statusStart = try XCTUnwrap(section.range(of: "private var statusSection")?.lowerBound)
        let statusSlice = String(section[statusStart...])
        let gate = try XCTUnwrap(statusSlice.range(of: "if store.mode == .subscribed, let band = settingsConvergenceBand")?.lowerBound)
        let seat = try XCTUnwrap(statusSlice.range(of: "ConvergenceBandView(content: band)")?.lowerBound)
        let readiness = try XCTUnwrap(statusSlice.range(of: "Subscribed · Your photo is ready before you open the app.")?.lowerBound)
        XCTAssertTrue(gate < seat && seat < readiness,
                      "the band renders in place of the status block; the readiness line lives in the else branch and cannot co-render while generating")
        // And the offer group no longer carries a second seat.
        let offerStart = try XCTUnwrap(section.range(of: "private var offerSection")?.lowerBound)
        let offerSlice = String(section[offerStart...])
        XCTAssertFalse(offerSlice.contains("ConvergenceBand"),
                       "the duplicate offer-group band seat is gone (GEN-5 item 22)")
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
        XCTAssertTrue(home.contains("ConvergenceBand.content(isSubscribed: personalStore.mode == .subscribed, coverage: personalStore.bankCoverage)"),
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
        XCTAssertEqual(MakeItMePriceLabels.monthlyRowLabel(priceString: "$0.99"), "Monthly — $0.99/month")
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
