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

    // MARK: - Item 4 · Counter truth — the counter-sequence test (DA-08)

    /// THE COUNTER-SEQUENCE TEST the gen-5 gate lacked: drive
    /// REAL successive completions in sequence — request →
    /// in-flight → complete, repeatedly to exhaustion — asserting
    /// the used count and the in-flight count at EACH step and
    /// that their sum never exceeds the allowance of 5. The
    /// harness mirrors the shipped semantics exactly: a request
    /// puts its cell into MAKING + the user-initiated pending set
    /// (startUserInitiatedGeneration); a completion charges the
    /// delivered count on landing (W-18, "charged on delivery")
    /// and clears the cell's making state (noteLookState). The
    /// meter read at every step is the one Settings renders:
    /// settingsLine(used) + settingsPendingLine(cappedInFlight).
    func testCounterSequence_SuccessiveCompletionsToExhaustion_NeverExceedsAllowance() {
        let allowance = 5
        var used = 0
        var lookStates: [String: PersonalLookState] = [:]
        var pending: Set<String> = []

        func assertMeter(step: String, file: StaticString = #filePath, line: UInt = #line) {
            let making = FreeLooksMeter.cappedInFlightCount(
                used: used, allowance: allowance,
                lookStates: lookStates, userInitiatedPending: pending)
            XCTAssertLessThanOrEqual(used + making, allowance,
                                     "\(step): used \(used) + making \(making) must never exceed \(allowance)", file: file, line: line)
            // The composed Settings block itself.
            let line1 = FreeLooksMeter.settingsLine(used: used, allowance: allowance)
            XCTAssertTrue(line1.contains("\(used) of \(allowance)"),
                          "\(step): meter line reads the delivered truth: \(line1)", file: file, line: line)
            if making > 0 {
                XCTAssertEqual(FreeLooksMeter.settingsPendingLine(inFlight: making),
                               making == 1 ? "Making your look…" : "Making \(making) looks…",
                               "\(step): pending line carries exactly the capped count", file: file, line: line)
            } else {
                XCTAssertNil(FreeLooksMeter.settingsPendingLine(inFlight: making),
                             "\(step): no pending line when nothing is in flight", file: file, line: line)
            }
        }

        assertMeter(step: "initial")
        XCTAssertEqual(used, 0)

        // Five successive request → in-flight → complete cycles.
        for i in 1...allowance {
            let cell = "cell-\(i)"
            // Request: the cell enters MAKING + pending.
            lookStates[cell] = .making
            pending.insert(cell)
            assertMeter(step: "request \(i) in flight")
            XCTAssertEqual(
                FreeLooksMeter.cappedInFlightCount(used: used, allowance: allowance,
                                                   lookStates: lookStates, userInitiatedPending: pending),
                1, "request \(i): exactly one look in flight")
            // Complete: charged on delivery, making clears.
            used += 1
            lookStates[cell] = .ready
            pending.remove(cell)
            assertMeter(step: "completion \(i)")
            XCTAssertEqual(used, i)
        }

        // Exhaustion: the meter reads the settled exhausted line,
        // and further in-flight work — including a delivered cell
        // REGENERATING (the DA-08 double-count: counted in used
        // AND raw-making at once) — can never push the composed
        // block past the allowance.
        XCTAssertEqual(FreeLooksMeter.settingsLine(used: used, allowance: allowance),
                       "All 5 free looks used")
        lookStates["cell-1"] = .making          // delivered cell regenerating
        lookStates["cell-6"] = .making          // fresh cell making past the allowance
        pending.insert("cell-6")
        let rawMaking = FreeLooksMeter.inFlightCount(lookStates: lookStates, userInitiatedPending: pending)
        XCTAssertEqual(rawMaking, 2, "the RAW union does double-count here — this is the DA-08 mechanism")
        XCTAssertGreaterThan(used + rawMaking, allowance,
                             "raw composition would read 5 used + 2 making = 7 > 5: the impossibility, unguarded")
        assertMeter(step: "exhausted with regenerating + extra making cells")
        XCTAssertEqual(
            FreeLooksMeter.cappedInFlightCount(used: used, allowance: allowance,
                                               lookStates: lookStates, userInitiatedPending: pending),
            0, "at exhaustion the meter shows no making room — the sum stays at the allowance")
    }

    /// The mid-sequence double-count: used = 2, four cells making
    /// (one of them a delivered cell regenerating) — raw reads
    /// 2 + 4 = 6 > 5 (the register's exact example shape); the
    /// capped meter reads 2 + 3.
    func testCounterCap_MidSequenceDoubleCountIsCapped() {
        let states: [String: PersonalLookState] = [
            "delivered-a": .making,   // already counted in used
            "fresh-b": .making,
            "fresh-c": .making,
            "fresh-d": .making,
        ]
        XCTAssertEqual(FreeLooksMeter.inFlightCount(lookStates: states, userInitiatedPending: []), 4)
        XCTAssertEqual(
            FreeLooksMeter.cappedInFlightCount(used: 2, allowance: 5,
                                               lookStates: states, userInitiatedPending: []),
            3, "used 2 + making capped 3 = 5 — never the impossible 6")
        XCTAssertEqual(
            FreeLooksMeter.cappedInFlightCount(used: 0, allowance: 5,
                                               lookStates: states, userInitiatedPending: []),
            4, "with the full allowance free, the raw count passes through uncapped")
    }

    func testCounterCap_StoreSurfaceUsesTheCappedForm_sourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("FreeLooksMeter.cappedInFlightCount("),
                      "the store's meter-facing in-flight count is the capped form")
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
