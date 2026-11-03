import XCTest

/// Pins for WS-3 (RC-3, iOS) — the Wear I've Run element's ONE
/// state machine (`WearIveRunElementMachine`).
///
/// The machine is the only transition source for the element
/// (ABSENT / MINI / EXPANDED / CUE, where CUE is a timed
/// presentation of EXPANDED). This suite pins:
/// - the complete (state × event) transition matrix, including
///   every illegal transition as an explicit no-op;
/// - the effects of the moving transitions (the cue fires its
///   pulse + soft haptic exactly once per landing; a stale timer
///   can never fire into a state that has moved on);
/// - interruptions: trash mid-cue, an async location landing
///   mid-cue (no movement, timer untouched), touch-during-cue;
/// - the symmetric end-of-scroll rule that retires the pre-WS-3
///   iOS latch (arrival expands, departure collapses);
/// - the landing classifier that keeps asynchronous landings
///   (hero-only / weather-only settles) from ever reaching the
///   element (D-07's abolished drivers);
/// - control semantics in both resting states (one toggle whose
///   glyph flips, one trash — D-10), and the sheet-dimming seam
///   (D-11).
///
/// The pure types are dual-membered into the test target, so no
/// @testable import. Rendered facts the unit target cannot
/// compose are pinned against the sources of record and stated
/// as gaps in the gate report — there is no screenshot-test
/// target in this repo.
final class WearIveRunElementMachineTests: XCTestCase {

    // MARK: - Fixtures

    private func entry(
        items: [String] = ["Tee", "Shorts"],
        hero: String = "hero-1",
        feels: Double = 41
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_762_560_000),
            placeName: "New York",
            feelsLikeF: feels,
            actualF: feels - 3,
            condition: "Cloudy",
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: hero
        )
    }

    private let allEvents: [WearIveRunElementEvent] = [
        .entryCreated, .userCausedUpdate, .entryAlreadyPresent,
        .cueTimedOut, .tapMiniBarBody, .tapToggleControl, .tapTrash,
        .scrolledToEnd, .scrolledAwayFromEnd, .dayRollover,
        .asyncLanding(.outfit), .asyncLanding(.weather),
        .asyncLanding(.location), .asyncLanding(.heroImage),
        .asyncLanding(.unitRerender), .asyncLanding(.foregrounding)
    ]

    // MARK: - The complete transition matrix

    /// The expected next state for every (state, event) pair.
    /// Anything not listed as moving in §1.2's driver table is a
    /// no-op: the state below is the state the pair STARTS in.
    private func expectedState(
        from state: WearIveRunElementState,
        after event: WearIveRunElementEvent
    ) -> WearIveRunElementState {
        switch (state, event) {
        case (.absent, .entryCreated): return .cue
        case (.absent, .entryAlreadyPresent): return .mini
        case (.mini, .userCausedUpdate), (.expanded, .userCausedUpdate): return .cue
        case (.cue, .cueTimedOut): return .mini
        case (.mini, .tapMiniBarBody), (.cue, .tapMiniBarBody): return .expanded
        case (.mini, .tapToggleControl): return .expanded
        case (.expanded, .tapToggleControl), (.cue, .tapToggleControl): return .mini
        case (.mini, .scrolledToEnd): return .expanded
        case (.expanded, .scrolledAwayFromEnd): return .mini
        case (.mini, .tapTrash), (.expanded, .tapTrash), (.cue, .tapTrash): return .absent
        case (.mini, .dayRollover), (.expanded, .dayRollover), (.cue, .dayRollover): return .absent
        default: return state
        }
    }

    func testCompleteTransitionMatrix() {
        let states: [WearIveRunElementState] = [.absent, .mini, .expanded, .cue]
        for state in states {
            for event in allEvents {
                var machine = WearIveRunElementMachine(state: state)
                machine.send(event)
                XCTAssertEqual(
                    machine.state,
                    expectedState(from: state, after: event),
                    "\(state) + \(event)"
                )
            }
        }
    }

    /// The moving transitions' effects — and every other pair in
    /// the matrix fires NO effect at all (illegal transitions are
    /// silent, and async landings are silent in every state).
    func testEffectsMatrix() {
        let effectful: [String: [WearIveRunElementEffect]] = [
            "absent|entryCreated": [.fireCuePulseAndSoftHaptic, .startCueTimer],
            "mini|userCausedUpdate": [.fireCuePulseAndSoftHaptic, .startCueTimer],
            "expanded|userCausedUpdate": [.fireCuePulseAndSoftHaptic, .startCueTimer],
            "cue|userCausedUpdate": [.startCueTimer],
            "cue|tapMiniBarBody": [.cancelCueTimer],
            "cue|tapToggleControl": [.cancelCueTimer],
            "mini|tapTrash": [.deleteTodaysEntry, .fireTrashHaptic],
            "expanded|tapTrash": [.deleteTodaysEntry, .fireTrashHaptic],
            "cue|tapTrash": [.cancelCueTimer, .deleteTodaysEntry, .fireTrashHaptic],
            "cue|dayRollover": [.cancelCueTimer]
        ]
        func key(_ state: WearIveRunElementState, _ event: WearIveRunElementEvent) -> String {
            switch event {
            case .entryCreated: return "\(state)|entryCreated"
            case .userCausedUpdate: return "\(state)|userCausedUpdate"
            case .tapMiniBarBody: return "\(state)|tapMiniBarBody"
            case .tapToggleControl: return "\(state)|tapToggleControl"
            case .tapTrash: return "\(state)|tapTrash"
            case .dayRollover: return "\(state)|dayRollover"
            default: return "\(state)|other"
            }
        }
        let states: [WearIveRunElementState] = [.absent, .mini, .expanded, .cue]
        for state in states {
            for event in allEvents {
                var machine = WearIveRunElementMachine(state: state)
                let effects = machine.send(event)
                XCTAssertEqual(
                    effects,
                    effectful[key(state, event)] ?? [],
                    "\(state) + \(event) effects"
                )
            }
        }
    }

    // MARK: - Timing & motion constants (§1.2)

    func testCueConstantsAreTheSpecValues() {
        XCTAssertEqual(WearIveRunElementMachine.cueDurationSeconds, 3.0)
        XCTAssertEqual(WearIveRunElementMachine.cuePulseDurationSeconds, 0.9)
        XCTAssertEqual(WearIveRunElementMachine.cuePulseScale, 1.03)
        XCTAssertEqual(WearIveRunElementMachine.cueRisePoints, 12)
        XCTAssertEqual(WearIveRunElementMachine.trashCollapseSeconds, 0.25)
    }

    // MARK: - The cue fires exactly once per landing

    func testCueFiresExactlyOncePerLanding() {
        var machine = WearIveRunElementMachine()
        var pulseCount = 0
        func count(_ effects: [WearIveRunElementEffect]) {
            pulseCount += effects.filter { $0 == .fireCuePulseAndSoftHaptic }.count
        }
        // Landing 1: creation.
        count(machine.send(.entryCreated))
        XCTAssertEqual(pulseCount, 1)
        XCTAssertEqual(machine.state, .cue)
        // The same landing's async echoes are not new landings:
        // no movement, no second pulse.
        for kind in [WearIveRunAsyncLanding.outfit, .weather, .location, .heroImage] {
            count(machine.send(.asyncLanding(kind)))
            XCTAssertEqual(machine.state, .cue)
        }
        XCTAssertEqual(pulseCount, 1)
        // A user-caused update DURING the cue restarts the timer
        // only — the presentation does not pulse again.
        let restart = machine.send(.userCausedUpdate)
        XCTAssertEqual(restart, [.startCueTimer])
        XCTAssertEqual(pulseCount, 1)
        // Timeout → MINI. Landing 2 (from MINI) pulses once more.
        count(machine.send(.cueTimedOut))
        XCTAssertEqual(machine.state, .mini)
        count(machine.send(.userCausedUpdate))
        XCTAssertEqual(pulseCount, 2)
        XCTAssertEqual(machine.state, .cue)
    }

    // MARK: - Interruptions

    func testTrashMidCue() {
        var machine = WearIveRunElementMachine()
        machine.send(.entryCreated)
        XCTAssertEqual(machine.state, .cue)
        let effects = machine.send(.tapTrash)
        XCTAssertEqual(machine.state, .absent)
        XCTAssertTrue(effects.contains(.cancelCueTimer))
        XCTAssertTrue(effects.contains(.deleteTodaysEntry))
        XCTAssertTrue(effects.contains(.fireTrashHaptic))
        // The cue timer that was pending when the trash landed
        // can never fire the element back into existence.
        XCTAssertEqual(machine.send(.cueTimedOut), [])
        XCTAssertEqual(machine.state, .absent)
        // And a later generation re-creates the entry and cues
        // again — trash is not a mute.
        XCTAssertEqual(
            machine.send(.entryCreated),
            [.fireCuePulseAndSoftHaptic, .startCueTimer]
        )
        XCTAssertEqual(machine.state, .cue)
    }

    func testLocationLandingMidCueDoesNotMoveOrRestart() {
        var machine = WearIveRunElementMachine()
        machine.send(.entryCreated)
        // An async location landing resolving mid-cue: no state
        // change, no effects — in particular NO timer restart
        // (a restart would extend the cue past its 3.0 s).
        XCTAssertEqual(machine.send(.asyncLanding(.location)), [])
        XCTAssertEqual(machine.state, .cue)
        // The original timer still owns the collapse.
        machine.send(.cueTimedOut)
        XCTAssertEqual(machine.state, .mini)
    }

    func testAsyncLandingsNeverMoveAnyState() {
        for state in [WearIveRunElementState.absent, .mini, .expanded, .cue] {
            for kind in [WearIveRunAsyncLanding.outfit, .weather, .location,
                         .heroImage, .unitRerender, .foregrounding] {
                var machine = WearIveRunElementMachine(state: state)
                XCTAssertEqual(machine.send(.asyncLanding(kind)), [], "\(state)/\(kind)")
                XCTAssertEqual(machine.state, state, "\(state)/\(kind)")
            }
        }
    }

    func testTouchDuringCueCancelsTimerAndObeysTouch() {
        var machine = WearIveRunElementMachine()
        machine.send(.entryCreated)
        // Body tap during the cue: expand, timer cancelled.
        XCTAssertEqual(machine.send(.tapMiniBarBody), [.cancelCueTimer])
        XCTAssertEqual(machine.state, .expanded)
        // The cancelled timer's late fire is an illegal
        // transition in EXPANDED: a no-op, not a collapse.
        XCTAssertEqual(machine.send(.cueTimedOut), [])
        XCTAssertEqual(machine.state, .expanded)
    }

    // MARK: - Symmetric end-of-scroll (the retired latch)

    func testEndOfScrollExpandsAndDepartureCollapses() {
        var machine = WearIveRunElementMachine(state: .mini)
        machine.send(.scrolledToEnd)
        XCTAssertEqual(machine.state, .expanded)
        // Arrival while already expanded: stays (no churn).
        XCTAssertEqual(machine.send(.scrolledToEnd), [])
        XCTAssertEqual(machine.state, .expanded)
        // Departure collapses — the counterpart the latch lacked.
        machine.send(.scrolledAwayFromEnd)
        XCTAssertEqual(machine.state, .mini)
        // Departure in MINI is a no-op; repeated cycles are
        // stable, with no oscillation residue.
        XCTAssertEqual(machine.send(.scrolledAwayFromEnd), [])
        for _ in 0..<3 {
            machine.send(.scrolledToEnd)
            XCTAssertEqual(machine.state, .expanded)
            machine.send(.scrolledAwayFromEnd)
            XCTAssertEqual(machine.state, .mini)
        }
    }

    func testScrollDoesNotDisturbCue() {
        var machine = WearIveRunElementMachine()
        machine.send(.entryCreated)
        XCTAssertEqual(machine.send(.scrolledToEnd), [])
        XCTAssertEqual(machine.state, .cue)
        XCTAssertEqual(machine.send(.scrolledAwayFromEnd), [])
        XCTAssertEqual(machine.state, .cue)
    }

    // MARK: - Controls in both states (D-10)

    func testToggleControlInBothStates() {
        var machine = WearIveRunElementMachine(state: .mini)
        XCTAssertTrue(machine.toggleIsExpand, "MINI shows the expand glyph")
        machine.send(.tapToggleControl)
        XCTAssertEqual(machine.state, .expanded)
        XCTAssertFalse(machine.toggleIsExpand, "EXPANDED shows the collapse glyph")
        machine.send(.tapToggleControl)
        XCTAssertEqual(machine.state, .mini)
        XCTAssertTrue(machine.toggleIsExpand)
    }

    func testBodyTapExpandsMiniOnly() {
        var machine = WearIveRunElementMachine(state: .mini)
        machine.send(.tapMiniBarBody)
        XCTAssertEqual(machine.state, .expanded)
        // A body tap in EXPANDED is not a collapse driver.
        XCTAssertEqual(machine.send(.tapMiniBarBody), [])
        XCTAssertEqual(machine.state, .expanded)
    }

    func testTrashInBothRestingStates() {
        for state in [WearIveRunElementState.mini, .expanded] {
            var machine = WearIveRunElementMachine(state: state)
            let effects = machine.send(.tapTrash)
            XCTAssertEqual(machine.state, .absent, "\(state)")
            XCTAssertEqual(effects, [.deleteTodaysEntry, .fireTrashHaptic], "\(state)")
            XCTAssertFalse(machine.isPresent)
        }
    }

    func testExpandCollapseAndScrollAreSilent() {
        // §1.7: nothing outside the cue and trash makes a sound
        // on this surface — expand, collapse, and scrolling carry
        // no haptic effect in any state.
        let silentEvents: [WearIveRunElementEvent] = [
            .tapMiniBarBody, .tapToggleControl, .scrolledToEnd, .scrolledAwayFromEnd
        ]
        for state in [WearIveRunElementState.mini, .expanded, .cue] {
            for event in silentEvents {
                var machine = WearIveRunElementMachine(state: state)
                let effects = machine.send(event)
                XCTAssertFalse(effects.contains(.fireCuePulseAndSoftHaptic), "\(state)/\(event)")
                XCTAssertFalse(effects.contains(.fireTrashHaptic), "\(state)/\(event)")
            }
        }
    }

    // MARK: - Day rollover

    func testDayRolloverAbsentsUntilFirstGeneration() {
        for state in [WearIveRunElementState.mini, .expanded, .cue] {
            var machine = WearIveRunElementMachine(state: state)
            machine.send(.dayRollover)
            XCTAssertEqual(machine.state, .absent, "\(state)")
            // Nothing but a creation (or the on-disk
            // reconciliation) may present the element again.
            XCTAssertEqual(machine.send(.scrolledToEnd), [])
            XCTAssertEqual(machine.state, .absent)
            machine.send(.entryCreated)
            XCTAssertEqual(machine.state, .cue)
        }
    }

    func testEntryAlreadyPresentReconcilesToMiniSilently() {
        var machine = WearIveRunElementMachine()
        XCTAssertEqual(machine.send(.entryAlreadyPresent), [])
        XCTAssertEqual(machine.state, .mini)
        // It is ABSENT-only: it never demotes a live presentation.
        var expanded = WearIveRunElementMachine(state: .expanded)
        XCTAssertEqual(expanded.send(.entryAlreadyPresent), [])
        XCTAssertEqual(expanded.state, .expanded)
    }

    // MARK: - Landing classification (abolished drivers, at the source)

    func testLandingClassifier() {
        let base = entry()
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: nil, incoming: base),
            .created
        )
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: base, incoming: base),
            .identical
        )
        // Hero-only settle: an async landing wearing the entry's
        // clothes — recorded by the store, never a cue.
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(
                existing: base, incoming: entry(hero: "hero-2")),
            .silentUpdate
        )
        // Weather-only settle (same kit): likewise silent.
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(
                existing: base, incoming: entry(feels: 55)),
            .silentUpdate
        )
        // A kit change IS the update the narration speaks.
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(
                existing: base, incoming: entry(items: ["Long Sleeve", "Tights"])),
            .kitUpdated
        )
    }

    /// The collection model's cue counter follows the classifier:
    /// creation bumps it, identical and hero/weather-only settles
    /// do not, a kit change does — and a silent settle does NOT
    /// flip the narration to "Update to" (WS-4 content carried
    /// as-is: the payload is the entry's first kit item).
    @MainActor
    func testCollectionModelCueSemantics() {
        let defaults = UserDefaults(suiteName: "ws3-tests-\(UUID().uuidString)")!
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: defaults)
        )
        let base = entry()
        model.recordSettled(base)
        XCTAssertEqual(model.cueCount, 1)
        XCTAssertFalse(model.todaysEntryWasUpdated)
        // Identical settle: nothing.
        model.recordSettled(base)
        XCTAssertEqual(model.cueCount, 1)
        // Hero-only settle: recorded, but no cue, no narration flip.
        let heroChanged = entry(hero: "hero-2")
        model.recordSettled(heroChanged)
        XCTAssertEqual(model.cueCount, 1)
        XCTAssertFalse(model.todaysEntryWasUpdated)
        XCTAssertEqual(model.entries.first?.heroImageReference, "hero-2")
        // Kit change: cue + the "Update to" narration state.
        model.recordSettled(entry(items: ["Long Sleeve", "Tights"], hero: "hero-2"))
        XCTAssertEqual(model.cueCount, 2)
        XCTAssertTrue(model.todaysEntryWasUpdated)
        XCTAssertEqual(
            WearIveRunMiniBarModel.state(
                todaysEntry: model.entries.first, moment: "Now",
                wasUpdatedThisSession: model.todaysEntryWasUpdated
            ),
            .updated(payload: "Long Sleeve"),
            "WS-4 narration content, carried as-is"
        )
        // Trash deletes the entry the machine absented.
        model.removeToday(placeName: "New York", today: base.dayStart)
        XCTAssertTrue(model.entries.isEmpty)
    }

    // MARK: - Dimming seam (D-11)

    func testDimmingWithTheBackdrop() {
        XCTAssertEqual(WearIveRunElementDimming.opacity(sheetPresented: false), 1.0)
        XCTAssertLessThan(WearIveRunElementDimming.opacity(sheetPresented: true), 1.0)
        XCTAssertEqual(
            WearIveRunElementDimming.opacity(sheetPresented: true),
            WearIveRunElementDimming.sheetPresentedOpacity
        )
        XCTAssertTrue(WearIveRunElementDimming.isInteractive(sheetPresented: false))
        XCTAssertFalse(WearIveRunElementDimming.isInteractive(sheetPresented: true))
    }

    // MARK: - Background policy over machine states

    func testBackgroundPolicyOverMachineStates() {
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: false),
            .translucentMaterial(sheetOpacity: 0.85)
        )
        XCTAssertEqual(
            WearIveRunBackgroundPolicy.style(for: .mini, isDark: true),
            .translucentMaterial(sheetOpacity: 0.82)
        )
        for state in [WearIveRunElementState.expanded, .cue] {
            XCTAssertEqual(
                WearIveRunBackgroundPolicy.style(for: state, isDark: false),
                .opaqueSheet, "\(state)"
            )
            XCTAssertEqual(
                WearIveRunBackgroundPolicy.style(for: state, isDark: true),
                .opaqueSheet, "\(state)"
            )
        }
    }

    // MARK: - Source pins (view-layer wiring)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testViewIsDrivenByTheMachine_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(view.contains("WearIveRunElementMachine()"),
                      "the view must hold the ONE machine")
        XCTAssertTrue(view.contains("machine.send(event)"),
                      "transitions must go through the machine's send")
        // Both scroll edges are fed — the latch's one-way feed is gone.
        XCTAssertTrue(view.contains(".scrolledToEnd : .scrolledAwayFromEnd"))
        XCTAssertFalse(view.contains("endOfScrollCue"),
                       "the retired latch presentation must not reappear")
        // The cue haptic is the §1.7 SOFT impact on the machine's
        // nonce — not the old light impact on the raw cue counter;
        // the trash keeps its own light impact.
        XCTAssertTrue(view.contains(".sensoryFeedback(.impact(weight: .soft), trigger: cueHapticNonce)"))
        XCTAssertTrue(view.contains(".sensoryFeedback(.impact(weight: .light), trigger: trashHapticNonce)"))
        XCTAssertFalse(view.contains("trigger: cueCount)"),
                       "haptics must fire from machine effects, not the raw counter")
        // The cue timer's length comes from the machine's constant.
        XCTAssertTrue(view.contains("WearIveRunElementMachine.cueDurationSeconds"))
        // D-11 dimming is consumed in the view.
        XCTAssertTrue(view.contains("WearIveRunElementDimming.opacity(sheetPresented: isDimmedBySheet)"))
    }

    func testExpandedThumbnailHasNoPlaceholder_sourcePin() throws {
        // D-12: the expanded card's thumbnail renders only once
        // decoded — the pre-WS-3 placeholder rectangle is gone
        // from this file (the mini bar's material rectangles are
        // fills of .regularMaterial / sheet, not cardOnSheet).
        let view = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertFalse(view.contains("AppTheme.cardOnSheet"),
                       "no placeholder tile may paint in the expanded thumbnail slot")
        XCTAssertTrue(view.contains("if case .success(let image) = phase"),
                      "the thumbnail must be decode-gated")
    }

    func testHomeFeedsTheDimmingSeam_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("anyHomeSheetPresented"),
                      "home must compute the sheet-open seam")
        XCTAssertTrue(home.contains("isDimmedBySheet: anyHomeSheetPresented"),
                      "the dock must consume the seam for dimming (WS-5 owns the sheets)")
    }
}
