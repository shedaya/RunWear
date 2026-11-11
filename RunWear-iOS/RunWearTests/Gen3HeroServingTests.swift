import XCTest

/// GEN-3 lane I-HERO (WS-A + WS-B, 2026-11-04): the always-available
/// hero — point-grain bundled fallback, the one hero pill, the
/// generating state's binding to the requested cell, and the
/// atomic place switch.
///
/// The serving defects (Android walkthrough census, shared design):
/// X-3 the hero settled EMPTY while a personal photo was being
/// made — no fallback or scaffold ever served; X-6 the same empty
/// band, shorter, on a place switch; X-7 the previous place's
/// photo painted under the new place's label; X-2/X-8 the
/// generating state was invisible for the look actually requested
/// and sat in the scroll content when it did appear; X-10 one
/// unresolved photo starved every photo-consuming surface.
///
/// The fix, pinned here at the pure seams:
///  * the bundled fallback resolves at POINT GRAIN — the gated
///    walk over the 44 assets' adjudicated garment census against
///    the live engine prescription (the same shared point-grain
///    policy the pool obeys; Android GEN-2 A2 parity);
///  * the pill state derives ONLY from the requested cell's facts;
///  * the display gate never lets one place's hero paint under
///    another place's label.
final class Gen3HeroServingTests: XCTestCase {

    private let provider = FallbackImageProvider.shared
    private let engine = OutfitRecommendationService.shared

    /// A real engine prescription in the hero's frame (adjusted ==
    /// raw, neutral comfort), built exactly as the production
    /// boundary builds it.
    private func outfit(
        adjustedF: Double,
        raining: Bool = false,
        windMph: Double = 2,
        uv: Double = 0,
        precipitation: Double = 0
    ) -> OutfitRecommendation {
        engine.getRecommendation(
            for: adjustedF,
            rawTemperature: adjustedF,
            isRaining: raining,
            windSpeed: windMph,
            humidity: 50,
            uvIndex: uv,
            precipitation: precipitation,
            hour: 12
        )
    }

    private func assetName(_ temp: HeroTempBracket, _ weather: HeroWeatherCondition, _ gender: HeroGender) -> String {
        "\(temp.rawValue.lowercased())_\(weather.rawValue.lowercased())_\(gender.rawValue.lowercased())"
    }

    // MARK: - The garment census is complete

    func testGarmentCensusCoversExactlyThe44BundledAssets() {
        var pairedNames = Set<String>()
        for (_, conditions) in FallbackImageProvider.pairedAssets {
            for (_, genders) in conditions {
                for (_, name) in genders { pairedNames.insert(name) }
            }
        }
        XCTAssertEqual(44, pairedNames.count)
        XCTAssertEqual(pairedNames, Set(FallbackImageProvider.garmentsByAsset.keys),
                       "every bundled asset must carry an adjudicated garment census entry — an uncensused asset fails closed and can never serve")
        XCTAssertEqual(pairedNames, Set(FallbackImageProvider.provenanceByAsset.keys),
                       "census and provenance must cover the same 44 assets")
    }

    // MARK: - Servability: every reachable cell serves its own asset at its cold-end prescription

    /// The bundled matrix was derived at each cell's cold-end point
    /// prescription, so at that prescription the gated walk must
    /// land on the cell's OWN paired asset, for both genders —
    /// the fallback is servable for every reachable cell class.
    /// (Warmer points inside a bracket are the pool cascade's
    /// coverage; the GEN-3 servability census in the lane report
    /// carries the full point sweep with row-level evidence.)
    func testColdEndPrescriptionServesOwnAssetForEveryReachableCell() {
        // (bracket, weather, adjustedF, raining, windMph, precipitation)
        let coldEnds: [(HeroTempBracket, HeroWeatherCondition, Double, Bool, Double, Double)] = [
            (.FREEZING, .CLEAR, 17, false, 2, 0),
            (.FREEZING, .CLOUDY, 17, false, 2, 0),
            (.FREEZING, .RAIN, 17, true, 2, 0.5),
            (.FREEZING, .SNOW, 17, false, 2, 0),
            (.COLD, .CLEAR, 22, false, 2, 0),
            (.COLD, .CLOUDY, 22, false, 2, 0),
            (.COLD, .RAIN, 22, true, 2, 0.5),
            (.COLD, .SNOW, 22, false, 2, 0),
            (.COOL, .CLEAR, 37, false, 2, 0),
            (.COOL, .CLOUDY, 37, false, 2, 0),
            (.COOL, .RAIN, 37, true, 11, 0.5),
            (.COOL, .SNOW, 37, false, 2, 0),
            (.MILD, .CLEAR, 55, false, 2, 0),
            (.MILD, .CLOUDY, 55, false, 2, 0),
            (.MILD, .RAIN, 55, true, 2, 0.5),
            (.MILD, .SNOW, 55, false, 2, 0),
            (.WARM, .CLEAR, 70, false, 2, 0),
            (.WARM, .CLOUDY, 70, false, 2, 0),
            (.WARM, .RAIN, 70, true, 2, 0.5),
            (.HOT, .CLEAR, 85, false, 2, 0),
            (.HOT, .CLOUDY, 85, false, 2, 0),
            (.HOT, .RAIN, 85, true, 2, 0.5)
        ]
        XCTAssertEqual(22, coldEnds.count)
        for (bracket, weather, temp, raining, wind, precip) in coldEnds {
            let prescription = outfit(adjustedF: temp, raining: raining, windMph: wind, precipitation: precip)
            for gender in [HeroGender.FEMALE, .MALE] {
                XCTAssertEqual(
                    assetName(bracket, weather, gender),
                    provider.gatedAssetName(temp: bracket, weather: weather, gender: gender, outfit: prescription),
                    "\(bracket)×\(weather)×\(gender) at its cold-end prescription must serve its own bundled asset"
                )
            }
            // Ungendered (unisex): some truthful bundled asset serves.
            XCTAssertNotNil(
                provider.gatedAssetName(temp: bracket, weather: weather, gender: nil, outfit: prescription),
                "\(bracket)×\(weather) ungendered at its cold-end prescription must serve a bundled asset"
            )
        }
    }

    // MARK: - The walkthrough prescriptions (W-4 / X-3 family)

    func testKansasCity44F_TwoItemPrescriptionHasNoBundledServe() {
        // Census X-3's cell class: COOL, feels-like 44°F, calm —
        // the engine prescribes exactly two items (long sleeve +
        // light tights; head and hands drop out at 40°F). Every
        // bundled COOL MATRIX asset wears the cold-end headband +
        // gloves, so no matrix asset may serve — serving one was
        // W-4, and that half of the pin stands.
        //
        // GEN-4 TRAIN UPDATE (replenishment, 2026-11-04): this
        // signature — (long, no-outer, tights, bare, bare) — is
        // RWPOOL-T15/T20, one of the 20 the replenishment
        // stocked with an exact point-grain asset rendered from
        // a gate-passing pool row. The gated walk's point-grain
        // pass now serves THAT asset: it wears exactly the two
        // prescribed items, so the serve is truthful, and the
        // pre-replenishment nil premise is superseded for this
        // signature (the full per-asset pins live in
        // Gen4BundledReplenishmentTests).
        let prescription = outfit(adjustedF: 44)
        XCTAssertEqual(prescription.bottom.key, "LIGHT_TIGHTS")
        XCTAssertNil(prescription.topOuter)
        XCTAssertNil(prescription.head)
        XCTAssertNil(prescription.hands)
        for gender in [HeroGender.FEMALE, .MALE] {
            let suffix = gender == .FEMALE ? "female" : "male"
            XCTAssertEqual(
                "pg_\(suffix)_long_o0_tights_none_none",
                provider.gatedAssetName(temp: .COOL, weather: .CLEAR, gender: gender, outfit: prescription)
            )
            XCTAssertEqual(
                "pg_\(suffix)_long_o0_tights_none_none",
                provider.gatedAssetName(temp: .COOL, weather: .CLOUDY, gender: gender, outfit: prescription)
            )
        }
        XCTAssertEqual(
            "pg_female_long_o0_tights_none_none",
            provider.gatedAssetName(temp: .COOL, weather: .CLEAR, gender: nil, outfit: prescription)
        )
        XCTAssertFalse(FallbackImageProvider.bundledAssetMatches(assetName: "cool_clear_female", outfit: prescription))
    }

    func testResolutionIsAFunctionOfThePrescription_W5() {
        // Same cell (COOL × CLEAR, female), three prescriptions,
        // three outcomes — a within-bracket prescription change
        // re-resolves (the pre-gate fallback key had no
        // prescription component, so the photo could never move).
        let coldEnd = outfit(adjustedF: 37)
        let twoItem = outfit(adjustedF: 44)
        let shorts = outfit(adjustedF: 47)
        XCTAssertEqual("cool_clear_female",
                       provider.gatedAssetName(temp: .COOL, weather: .CLEAR, gender: .FEMALE, outfit: coldEnd))
        // GEN-4 TRAIN UPDATE (replenishment): the two warmer
        // points are stocked point-grain signatures now — the
        // 44°F two-item point is RWPOOL-T15 and the 47°F shorts
        // point is RWPOOL-T08 — so each re-resolves to its own
        // exact pg asset. Three prescriptions, three DIFFERENT
        // truthful serves: the W-5 property itself is unchanged.
        XCTAssertEqual("pg_female_long_o0_tights_none_none",
                       provider.gatedAssetName(temp: .COOL, weather: .CLEAR, gender: .FEMALE, outfit: twoItem))
        XCTAssertEqual("pg_female_long_o0_shorts_none_none",
                       provider.gatedAssetName(temp: .COOL, weather: .CLEAR, gender: .FEMALE, outfit: shorts))
    }

    func testJuneauRainPrescriptionServesTheRainAsset() {
        // RW-23's Juneau frame: adjusted 37°F, rain, wind 11 mph —
        // rain jacket + cap + light gloves + tights.
        let prescription = outfit(adjustedF: 37, raining: true, windMph: 11, precipitation: 0.5)
        XCTAssertEqual(prescription.topOuter?.key, "RAIN_JACKET")
        XCTAssertEqual(prescription.head?.key, "BASEBALL_CAP")
        XCTAssertEqual(prescription.hands?.key, "LIGHT_GLOVES")
        for gender in [HeroGender.FEMALE, .MALE] {
            XCTAssertEqual(assetName(.COOL, .RAIN, gender),
                           provider.gatedAssetName(temp: .COOL, weather: .RAIN, gender: gender, outfit: prescription))
        }
    }

    func testMittensPrescriptionServesGlovesAssetUnderSubstitutionRuling() {
        // Below 15°F the engine prescribes MITTENS and NO bundled
        // asset records "mittens" (every winter asset wears
        // gloves) — the original GEN-3 pin asserted NO bundled
        // serve for this prescription.
        //
        // GEN-4 HANDWEAR AMENDMENT (Sol's ruling, 2026-11-04
        // ~22:23 CST): a gloves-for-mittens substitution is
        // acceptable where mittens are prescribed; handwear form
        // alone is not a failure. The shared gate now passes a
        // gloves candidate on the hands slot under a MITTENS
        // prescription (one direction only), so the gated walk
        // serves the cell's own gloves asset — freezing_clear —
        // truthfully under the ruling, for both genders.
        let prescription = outfit(adjustedF: 10)
        XCTAssertEqual(prescription.hands?.key, "MITTENS")
        for gender in [HeroGender.FEMALE, .MALE] {
            XCTAssertEqual(
                assetName(.FREEZING, .CLEAR, gender),
                provider.gatedAssetName(temp: .FREEZING, weather: .CLEAR, gender: gender, outfit: prescription),
                "under the substitution ruling the mittens prescription serves the gloves asset"
            )
            XCTAssertTrue(FallbackImageProvider.bundledAssetMatches(
                assetName: assetName(.FREEZING, .CLEAR, gender), outfit: prescription))
        }
    }

    // MARK: - The hero pill: four states, derived from the requested cell only

    func testPillMakingState_BindsToTheRequestedCell() {
        // (a) personal photo generating for THIS cell, none on
        // screen yet → the pill rides the fallback photo.
        // GEN-5 (C-4 AMENDED): a FREE user's generating pill is
        // its own state — it carries the green instantly line. A
        // subscriber's generating pill stays .making (pinned in
        // the gen-5 suite).
        XCTAssertEqual(
            .makingWithUpsell,
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5)
        )
        // The same making state for a DIFFERENT cell is not an
        // input to this seam at all: the caller passes only the
        // requested cell's look state (heroPillState reads
        // lookStates[currentPersonalCombination]). A ready/none
        // requested cell shows no pill while anything else makes.
        XCTAssertEqual(
            .hidden,
            HeroPill.resolve(optedIn: true, lookState: .none, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5)
        )
        XCTAssertEqual(
            .hidden,
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5)
        )
    }

    func testPillScaffoldState() {
        // (b) a personal photo IS on screen while this cell's exact
        // look still makes. GEN-5 (RW-29 FA-1) SUPERSEDES the
        // gen-3 iOS pin that resolved this combination to the
        // scaffold state: the settled precedence — Android's
        // HeroPillPolicy is test-pinned to it — is that a
        // generation in flight OUTRANKS the scaffold serve, so
        // the pill reports the making state (with the C-4
        // amendment's free-tier gating), never "Your photo",
        // while the look is still being made.
        XCTAssertEqual(
            .makingWithUpsell,
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5)
        )
        XCTAssertEqual(
            .making,
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: true,
                             isSubscribed: true, freeGenerationsUsed: 1, freeAllowance: 5)
        )
        // GEN-6 Wave A (RW-34) SUPERSEDES the GEN-3 label pin:
        // the scaffold serve is SILENT — the "Your photo" label
        // is removed (Sol ruled it is not an approved pill) and
        // a scaffold serve with no generation in flight resolves
        // to no pill at all (pinned in Gen6WaveATests).
        XCTAssertEqual("Make It Me", HeroPill.scriptMarkText)
        XCTAssertEqual("Making this you…", HeroPill.makingLabel)
    }

    func testPillFreeLookState_ShowsTheSharedCountOnlyOnceConfirmed() {
        // (c) exact personal photo displayed, free tier → count +
        // "Get unlimited" (the tap opens the paywall in the view).
        XCTAssertEqual(
            .freeLook(used: 1, allowance: 5),
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5)
        )
        // GEN-3 DESIGN DELTA 2: the mock's wording — "N of 5 looks".
        XCTAssertEqual("1 of 5 looks", HeroPill.freeLookCountLabel(used: 1, allowance: 5))
        XCTAssertEqual("5 of 5 looks", HeroPill.freeLookCountLabel(used: 7, allowance: 5),
                       "the count clamps to the allowance, like the W-18 meter")
        XCTAssertEqual("Get unlimited", HeroPill.getUnlimitedLabel)
        // W-18 discipline: before the shared counter confirms the
        // delivery (still 0), the pill prints no number.
        XCTAssertEqual(
            .hidden,
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5)
        )
    }

    func testPillPremiumState() {
        // (d) exact personal photo displayed, subscribed → Premium.
        XCTAssertEqual(
            .premium,
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: true, freeGenerationsUsed: 3, freeAllowance: 5)
        )
        XCTAssertEqual("Premium", HeroPill.premiumLabel)
    }

    func testPillHiddenStates() {
        // Not opted in: no pill in any state.
        XCTAssertEqual(
            .hidden,
            HeroPill.resolve(optedIn: false, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5)
        )
        // Failed look: the standard photo serves quietly; the
        // failure line lives in the personal section, not the pill.
        XCTAssertEqual(
            .hidden,
            HeroPill.resolve(optedIn: true, lookState: .failed(reason: .error), personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5)
        )
    }

    // MARK: - The atomic switch (X-7)

    func testDisplayGateNeverPaintsOnePlaceUnderAnotherPlacesLabel() {
        XCTAssertFalse(HeroDisplayGate.mayPaint(ownerPlaceKey: nil, displayedPlaceKey: "New York, NY"),
                       "no committed hero yet → nothing paints")
        XCTAssertTrue(HeroDisplayGate.mayPaint(ownerPlaceKey: "New York, NY", displayedPlaceKey: "New York, NY"))
        XCTAssertFalse(HeroDisplayGate.mayPaint(ownerPlaceKey: "Kansas City, MO", displayedPlaceKey: "Miami, FL"),
                       "the previous place's photo must never paint under the new place's label")
    }

    // MARK: - Wiring pins

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testHeroPillIsWiredOntoTheHero_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("pillState: heroPillState"),
                      "home must pass the pill state into the hero")
        XCTAssertTrue(view.contains("lookStates[cell.combinationId]"),
                      "the pill must read the REQUESTED cell's look state")
        XCTAssertTrue(view.contains("showHeroPillPaywall"),
                      "the pill's Get unlimited must open the Premium paywall from home")
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertTrue(hero.contains("HeroPill.makingLabel"))
        // GEN-6 Wave A (RW-34): NO scaffold label anywhere in
        // the hero — the serve is silent.
        XCTAssertFalse(hero.contains("HeroPill.scaffoldLabel"))
        XCTAssertFalse(hero.contains("\"Your photo\""))
        XCTAssertTrue(hero.contains("HeroPill.getUnlimitedLabel"))
        XCTAssertTrue(hero.contains("HeroPill.premiumLabel"))
        // GEN-3 DESIGN DELTA 2: the script mark renders in the
        // pill (states (b)(c)(d) share the one pillScriptMark
        // view), and the premium state carries the lock glyph —
        // the crown is out.
        XCTAssertTrue(hero.contains("pillScriptMark"))
        XCTAssertTrue(hero.contains("Text(HeroPill.scriptMarkText)"))
        XCTAssertTrue(hero.contains("\"lock.fill\""),
                      "the premium pill carries the mock's lock glyph")
        XCTAssertFalse(hero.contains("crown"),
                       "the crown I-HERO shipped is superseded by the lock")
    }

    func testAllPhotoSurfacesShareTheAlwaysAvailableResolution_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // RE-PINNED for RW-169 §7E.3 (Nov 11, 2026; narrows the
        // X-10 pin that stood here): a FALLBACK serve is never
        // recorded as the entry's hero reference — the bundled
        // always-available resolution no longer feeds the
        // recording. Only a remote serve actually on screen
        // (viewModel.heroImageUrl) is recorded; the
        // always-available resolution still feeds the HERO's
        // own paint (the VM pin below stands).
        XCTAssertTrue(view.contains("heroImageReference: viewModel.heroImageUrl ?? \"\""),
                      "RW-169 §7E.3: the entry records the remote serve on screen, never a fallback serve")
        XCTAssertFalse(view.contains("heroImageReference: viewModel.currentHeroFlagTarget?.imageUrl ?? viewModel.alwaysAvailableHeroReference()"),
                      "RW-169 §7E.3: the fallback-inclusive recording expression is retired")
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("func alwaysAvailableHeroReference()"))
        XCTAssertTrue(vm.contains("heroOwnerPlaceKey = placeDisplayName"),
                      "the atomic commit must move hero place ownership in the same write as the fallback")
    }
    // MARK: - Y-1 (narrowed by Sol's ruling) · rendering order

    func testFirstLookClaimWaitsForThePhotosPixels() {
        // Defect 1: the claim and the counter rendered over an
        // EMPTY band ~0.7 s before any photo appeared — the seam
        // declared ready from the photo's URL presence alone.
        // Ready now requires the displayed photo's pixels.
        let notLoaded = FirstLookPresentation.resolve(
            lookState: .ready, personalPhotoDisplayed: true, personalImageLoaded: false,
            freeGenerationsUsed: 1, freeAllowance: 5, isSubscribed: false)
        XCTAssertEqual(notLoaded.phase, .making,
                       "no finished claim before the photo it describes is on screen")
        XCTAssertNil(notLoaded.caption)
        XCTAssertNil(notLoaded.meterLine, "the counter waits for the photo too")
        XCTAssertFalse(notLoaded.showsPremiumBand)
        // Pixels loaded → the claim is earned, with the meter
        // from the shared counter.
        let loaded = FirstLookPresentation.resolve(
            lookState: .ready, personalPhotoDisplayed: true, personalImageLoaded: true,
            freeGenerationsUsed: 1, freeAllowance: 5, isSubscribed: false)
        XCTAssertEqual(loaded.phase, .ready)
        XCTAssertEqual(loaded.caption, "You're in the photo now.")
        XCTAssertEqual(loaded.meterLine, "That was look 1 of your 5 free looks — 4 remain, whenever you want them.")
        // Sol's ruling (2026-11-04): a served personal photo —
        // including a banked photo generated from references the
        // user uploaded in an earlier version — IS the user's
        // look, even while a regeneration for the cell is in
        // flight. No epoch policing in this seam.
        let banked = FirstLookPresentation.resolve(
            lookState: .making, personalPhotoDisplayed: true, personalImageLoaded: true,
            freeGenerationsUsed: 3, freeAllowance: 5, isSubscribed: false)
        XCTAssertEqual(banked.phase, .ready)
        XCTAssertEqual(banked.meterLine, "That was look 3 of your 5 free looks — 2 remain, whenever you want them.")
    }

    func testNoStockPublishOverAnAppliedPersonalPhoto_sourcePin() throws {
        // Defect 2: a stock photo faded in under the finished
        // claim for ~0.6 s — a same-cell reload republishing the
        // pool URL into the shared hero slot. The view model must
        // keep the guard that suppresses that write.
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("personalAppliedForThisCell"),
                      "a same-cell reload must not republish the pool URL over an applied personal photo")
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        // RW-94 (Nov 6, 2026) supersedes the URL-string form of
        // this pin: the load proof is keyed to the hero's
        // IDENTITY, because the URL is a signed address a
        // re-resolution can refresh for the SAME photo — the
        // string-equality proof silently un-proved loaded photos
        // and the finished claim never fired. The property this
        // pin guards (the surface tracks the photo's ACTUAL load;
        // the presentation is fed the load state) is unchanged.
        XCTAssertTrue(onboarding.contains("firstLookLoadedHeroId"),
                      "the first-look surface must track the personal photo's actual load")
        XCTAssertTrue(onboarding.contains("personalImageLoaded: firstLookLoadedHeroId != nil"),
                      "the presentation must be fed the load state, not just the URL's presence")
    }
}
