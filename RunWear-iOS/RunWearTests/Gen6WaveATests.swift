import XCTest

/// GEN-6 Wave A (first-photo truth) — iOS lane pins.
///
/// Every test pins a wave item against its governing source:
/// the deep-audit register (DA-02/03/07/08/10/11), the
/// conformance register (RW-47/RW-48), and the approved pill
/// state table (rulings C-4 as amended + the exhausted-pill and
/// scaffold-silence rulings, Nov 5, 2026). Pure logic is pinned
/// on the dual-membered models; view/store wiring is pinned by
/// reading the source of record — the suite's established
/// pattern, so a drift in the shipped source fails the gate.
final class Gen6WaveATests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - Item 1 · Poll horizon (DA-02)

    func testPollPolicy_CapIsGenerousAndBacksOff() {
        XCTAssertGreaterThanOrEqual(HeroPollPolicy.maxDurationSeconds, 15 * 60,
                                    "the cap must be at least 15 minutes (DA-02)")
        XCTAssertGreaterThanOrEqual(HeroPollPolicy.totalDurationSeconds, 15 * 60,
                                    "the schedule must actually poll across the cap window")
        // Backoff: early polls are tight, later polls are wider.
        XCTAssertEqual(HeroPollPolicy.delay(forAttempt: 0), 15)
        XCTAssertLessThan(HeroPollPolicy.delay(forAttempt: 0), HeroPollPolicy.delay(forAttempt: 10))
        XCTAssertLessThan(HeroPollPolicy.delay(forAttempt: 10), HeroPollPolicy.delay(forAttempt: 30))
        // The gen-5 budget is gone as a schedule: 18 polls of
        // 15 s would total 270 s; this schedule is far beyond it.
        XCTAssertGreaterThan(HeroPollPolicy.delays.count, 18)
        XCTAssertGreaterThan(HeroPollPolicy.totalDurationSeconds, 270)
    }

    func testPollPolicy_TerminalStatesAreReadyAndFailedOnly() {
        XCTAssertTrue(HeroPollPolicy.isTerminal(.ready))
        XCTAssertTrue(HeroPollPolicy.isTerminal(.failed(reason: .error)))
        XCTAssertFalse(HeroPollPolicy.isTerminal(.making),
                       "MAKING is the state the poll exists to outlast")
        XCTAssertFalse(HeroPollPolicy.isTerminal(.none))
    }

    func testPollPolicy_CapExpiryNeverStrandsMaking() {
        XCTAssertEqual(HeroPollPolicy.stateAfterCapExpiry(.making), .none,
                       "cap expiry resolves MAKING to the quiet pool serve — never a stranded generating state")
        XCTAssertEqual(HeroPollPolicy.stateAfterCapExpiry(.ready), .ready)
        XCTAssertEqual(HeroPollPolicy.stateAfterCapExpiry(.failed(reason: .error)), .failed(reason: .error))
        XCTAssertEqual(HeroPollPolicy.stateAfterCapExpiry(.none), .none)
    }

    func testAwaitHero_UsesThePolicyScheduleAndResolvesCapExpiry_sourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("for delay in HeroPollPolicy.delays"),
                      "awaitHero polls on the policy schedule, not a fixed 18 × 15 s loop")
        XCTAssertFalse(store.contains("for _ in 0..<18"),
                       "the gen-5 4.5-minute poll budget is removed")
        XCTAssertTrue(store.contains("HeroPollPolicy.stateAfterCapExpiry"),
                      "cap expiry leaves the policy's honest state")
        XCTAssertTrue(store.contains("await self.refreshProfile()"),
                      "W-18 stands: a landing still re-reads the one counter")
    }

    // MARK: - Item 6 · Pill policy — the full approved state table (RW-34)

    func testPillTable_Generating_FreeCarriesGreenLine_PremiumStaysClean() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 0, freeAllowance: 5),
            .makingWithUpsell, "free generating: script + spinner + making + green instantly line")
        XCTAssertEqual(HeroPill.getInstantlyLabel, "Get it instantly next time")
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: true, freeGenerationsUsed: 0, freeAllowance: 5),
            .making, "Premium generating carries NO upsell")
    }

    func testPillTable_GenerationWindowNeverHasNoPill() {
        // Every opted-in making combination — photo or no photo,
        // either tier, any counter — resolves to a generating
        // pill, never .hidden.
        for displayed in [false, true] {
            for subscribed in [false, true] {
                for used in [0, 3, 5] {
                    let state = HeroPill.resolve(
                        optedIn: true, lookState: .making, personalPhotoDisplayed: displayed,
                        isSubscribed: subscribed, freeGenerationsUsed: used, freeAllowance: 5,
                        isScaffoldServe: displayed)
                    XCTAssertTrue(state == .making || state == .makingWithUpsell,
                                  "making + displayed=\(displayed) subscribed=\(subscribed) used=\(used) must carry the generating pill, got \(state)")
                }
            }
        }
    }

    func testPillTable_FreeExactServe_CountAndGetUnlimited() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 3, freeAllowance: 5),
            .freeLook(used: 3, allowance: 5))
        XCTAssertEqual(HeroPill.freeLookCountLabel(used: 3, allowance: 5), "3 of 5 looks",
                       "the design mock's form: '— 3 of 5 looks · Get unlimited'")
        XCTAssertEqual(HeroPill.getUnlimitedLabel, "Get unlimited")
    }

    func testPillTable_Exhausted_UpsellIsTheMainMessage() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5),
            .exhausted(used: 5, allowance: 5), "at the allowance the exhausted status takes over")
        XCTAssertEqual(HeroPill.exhaustedCountLabel(used: 5, allowance: 5), "5 of 5 looks used")
    }

    func testPillTable_PremiumExactServe_ScriptPremiumLockOnly() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: true, freeGenerationsUsed: 5, freeAllowance: 5),
            .premium, "Premium exact serve is Premium + lock even at the free allowance — no count, no extra line")
        XCTAssertEqual(HeroPill.premiumLabel, "Premium")
    }

    func testPillTable_ScaffoldServeIsSilent() {
        // A banked photo standing in (not making — the making
        // case is the generating pill, above): NO pill, on any
        // tier, at any counter — and never the exact labels.
        for subscribed in [false, true] {
            for used in [0, 1, 5] {
                XCTAssertEqual(
                    HeroPill.resolve(optedIn: true, lookState: .none, personalPhotoDisplayed: true,
                                     isSubscribed: subscribed, freeGenerationsUsed: used, freeAllowance: 5,
                                     isScaffoldServe: true),
                    .hidden, "scaffold serve must be silent (subscribed=\(subscribed), used=\(used))")
                XCTAssertEqual(
                    HeroPill.resolve(optedIn: true, lookState: .failed(reason: .error), personalPhotoDisplayed: true,
                                     isSubscribed: subscribed, freeGenerationsUsed: used, freeAllowance: 5,
                                     isScaffoldServe: true),
                    .hidden, "a scaffold photo left up by a failed exact look still carries no claim")
            }
        }
    }

    func testPillPolicy_NoScaffoldLabelAnywhere_sourcePin() throws {
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertFalse(models.contains("scaffoldLabel"),
                       "the scaffold label symbol is removed, not merely unrendered")
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertFalse(hero.contains(".scaffold"), "the hero view has no scaffold pill case")
        XCTAssertFalse(hero.contains("\"Your photo\""), "the ruled-out label renders nowhere on the hero")
        // The free pill's "Get unlimited" is the green element.
        let freeStart = try XCTUnwrap(hero.range(of: "case .freeLook")?.lowerBound)
        let premiumStart = try XCTUnwrap(hero.range(of: "case .premium:")?.lowerBound)
        let freeSlice = String(hero[freeStart..<premiumStart])
        XCTAssertTrue(freeSlice.contains("AppTheme.pineAccentDark"),
                      "free exact-serve pill: 'Get unlimited' renders in the green accent")
        // The exhausted pill is one whole-pill button to Premium.
        XCTAssertTrue(hero.contains("case .exhausted"))
        let outfit = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(outfit.contains("isScaffoldServe: viewModel.currentPersonalServeIsScaffold"),
                      "home feeds the pill policy the serve's scaffold fact")
        XCTAssertTrue(outfit.contains("onPillGetUnlimited: { showHeroPillPaywall = true }"),
                      "the pill's Premium door opens the paywall from home")
    }

    // MARK: - Item 7 · RW-47 first-look honesty line + RW-48 consent closing

    func testRW47_SettledHonestyLineRestoredVerbatim() {
        XCTAssertEqual(
            MakeItMeCopy.progressSubline,
            "This usually takes a few minutes — the standard photo stays up until yours is done.")
        XCTAssertFalse(MakeItMeCopy.progressSubline.contains("ready either way"),
                       "the unruled gen-5 substitution is gone")
    }

    func testRW48_ConsentBodyClosesWithTheSettledSentence_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        let consentStart = try XCTUnwrap(flow.range(of: "private var consentStep")?.lowerBound)
        let consent = String(flow[consentStart...])
        let closing = try XCTUnwrap(consent.range(of: "Nothing else is done with them.")?.lowerBound)
        // The closing sentence comes AFTER the delete-anytime
        // paragraph and BEFORE the attestation — it closes the
        // BODY, it is not a stray line elsewhere in the flow.
        let deleteLine = try XCTUnwrap(consent.range(of: "You can delete your photos")?.lowerBound)
        let attestation = try XCTUnwrap(consent.range(of: "These photos are of me")?.lowerBound)
        XCTAssertTrue(deleteLine < closing && closing < attestation,
                      "the closing sentence is the consent body's last line")
    }

    func testStallLine_IsUntouchedNamedParityException_sourcePin() throws {
        // The 30-minute bank stall line (PersonalHeroModels.swift's
        // ConvergenceBand) is a named parity exception awaiting
        // the owner's ruling — this wave must NOT remove or
        // retime it while aligning the poll budget.
        XCTAssertEqual(ConvergenceBand.stallThreshold, 30 * 60)
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertTrue(models.contains("static let stallThreshold: TimeInterval = 30 * 60"))
    }
}
