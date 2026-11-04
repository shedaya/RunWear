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
        XCTAssertTrue(view.contains(".sensoryFeedback(.impact(flexibility: .soft), trigger: cueHapticNonce)"))
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

// MARK: - W-15 regression + morph pins (GEN-2 lane I1, 2026-11-04)
//
// W-15: "Wear I've Run" never surfaced on iOS. The mechanism
// these tests pin against regression, in plain language:
// home appears MID-FETCH — the view model publishes weather
// and a recommendation before its reverse geocode returns —
// so the only recording triggers the home view had (appear,
// item change) always fired while the place was still the
// placeholder label "Loading...". The day's entry was filed
// under that non-place (or, where an earlier session had left
// a real entry, under its own place), and the dock then
// looked today's entry up BY THE VIEWED PLACE — a lookup no
// placeholder-filed entry can ever match, and no entry at a
// different place can match either. Result: the state stayed
// .preAdd, the dock was never composed, and the element's
// state machine sat in ABSENT downstream of a lookup that
// could not succeed. Android records from an effect keyed on
// (outfit, weather, locationName, heroImageUrl) and presents
// today's entry BY DATE. These tests pin the iOS chain to
// the same shape: place-gated recording, re-offer on place
// resolution, date-only presentation, settled narration.
final class WearIveRunW15FixTests: XCTestCase {

    private func entry(
        place: String = "Kansas City, MO",
        day: Date = Date(timeIntervalSince1970: 1_762_560_000)
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: day,
            placeName: place,
            feelsLikeF: 45,
            actualF: 49,
            condition: "Cloudy",
            itemNames: ["Light Long Sleeve", "Light Tights"],
            itemKeys: ["light-long-sleeve", "light-tights"],
            heroImageReference: "hero-kc"
        )
    }

    // MARK: (a) Creation — the place gate

    func testRecordingRequiresAResolvedPlace() {
        // No SimplePlace yet (GPS first load, geocode in
        // flight): the recording must be refused — an entry
        // filed under "Loading..." is an entry lost.
        XCTAssertNil(WearIveRunHomePresentation.eligiblePlace(nil))
        // A resolved place admits exactly its simple form.
        XCTAssertEqual(
            WearIveRunHomePresentation.eligiblePlace(
                SimplePlace(city: "Kansas City", region: "Missouri")
            ),
            "Kansas City, MO"
        )
        XCTAssertEqual(
            WearIveRunHomePresentation.eligiblePlace(
                SimplePlace(city: "New York", region: "New York")
            ),
            "New York, NY"
        )
    }

    // MARK: (a)+(b) First resolution creates; the dock presents by date

    @MainActor
    func testFirstResolutionCreatesEntryAndElementPresentsIt() {
        let defaults = UserDefaults(suiteName: "w15-tests-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store)
        let e = entry()

        // Before the first resolution: nothing exists, no cue,
        // and the dock's state is .preAdd — the element absent.
        XCTAssertNil(model.todaysEntry(today: e.dayStart))
        XCTAssertEqual(model.cueCount, 0)
        XCTAssertEqual(
            WearIveRunMiniBarModel.state(
                todaysEntry: model.todaysEntry(today: e.dayStart),
                moment: "Now", wasUpdatedThisSession: false
            ),
            .preAdd
        )

        // The first generation settles under its resolved place.
        model.recordSettled(e)
        XCTAssertEqual(model.cueCount, 1, "creation is the cue-worthy landing")

        // The dock presents it BY DATE: it is found even though
        // the place the user is now viewing is a different one —
        // the pre-fix place-keyed lookup (kept on the store)
        // demonstrably cannot find it from the viewed place.
        XCTAssertEqual(model.todaysEntry(today: e.dayStart), e)
        XCTAssertNil(store.todaysEntry(placeName: "New York, NY", today: e.dayStart))

        // Element presence given the entry, with the settled
        // narration — Android's first-frame line for this flow.
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: model.todaysEntry(today: e.dayStart),
            moment: "Now", wasUpdatedThisSession: model.todaysEntryWasUpdated
        )
        XCTAssertEqual(state, .added(place: "Kansas City, MO", moment: "Now"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: state),
            "Added Kansas City, MO, Now"
        )
    }

    func testStorePresentsTodaysEntryByDateAlone() {
        let defaults = UserDefaults(suiteName: "w15-tests-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let today = Date(timeIntervalSince1970: 1_762_560_000)
        let yesterday = today.addingTimeInterval(-86_400)
        store.record(entry(place: "New York, NY", day: yesterday))
        XCTAssertNil(store.todaysEntry(today: today),
                     "yesterday's entry never presents as today's")
        store.record(entry(day: today))
        XCTAssertEqual(store.todaysEntry(today: today)?.placeName, "Kansas City, MO")
    }

    // MARK: Home wiring — source pins

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testHomeReoffersRecordingWhenThePlaceResolves_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // The re-offer triggers, mirroring Android's keyed effect.
        XCTAssertTrue(home.contains(".onChange(of: viewModel.placeDisplayName)"),
                      "the recording must be re-offered when the geocode lands")
        XCTAssertTrue(home.contains(".onChange(of: viewModel.heroImageUrl)"))
        XCTAssertTrue(home.contains(".onChange(of: viewModel.selectedWeatherSnapshot?.feelsLikeFahrenheit)"))
        // The placeholder gate, at the recording itself.
        XCTAssertTrue(home.contains("WearIveRunHomePresentation.eligiblePlace(viewModel.simplePlace)"),
                      "recording must refuse an unresolved (placeholder) place")
        // The dock is session-keyed (GEN-3, superseding this
        // lane's gen-2 date key), never viewed-place-keyed.
        XCTAssertTrue(home.contains("collectionModel.sessionEntry()"),
                      "the dock must present the current session's entry")
        XCTAssertFalse(home.contains("todaysEntry(placeName: viewModel.placeDisplayName)"),
                       "the viewed-place lookup was the W-15 presentation defect")
    }

    // MARK: Morph mathematics (motion decision, 2026-11-04)

    func testMorphTintOpacityEndpointsAndMidpoint() {
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: false), 0.85, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0, isDark: true), 0.82, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 1, isDark: false), 1.0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 1, isDark: true), 1.0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 0.5, isDark: false), 0.925, accuracy: 0.0001)
        // The cue's overshoot can never push opacity past solid.
        XCTAssertEqual(WearIveRunMorph.tintOpacity(progress: 1.03, isDark: false), 1.0, accuracy: 0.0001)
    }

    func testMorphHeightAndContentWindow() {
        XCTAssertEqual(WearIveRunMorph.height(mini: 56, expanded: 148, progress: 0), 56, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.height(mini: 56, expanded: 148, progress: 1), 148, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.height(mini: 56, expanded: 148, progress: 0.5), 102, accuracy: 0.0001)
        // Expanded-only content: out until 0.45, fully in at the
        // settle, half-way at the window's middle; drift +4 → 0.
        XCTAssertEqual(WearIveRunMorph.expandedContentOpacity(progress: 0), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedContentOpacity(progress: 0.45), 0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedContentOpacity(progress: 0.725), 0.5, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedContentOpacity(progress: 1), 1, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedContentOffset(progress: 0), 4, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.expandedContentOffset(progress: 1), 0, accuracy: 0.0001)
        // Radius tokens are equal in the iOS strip expression:
        // the interpolation is the decision's constant case.
        XCTAssertEqual(
            WearIveRunMorph.cornerRadius(progress: 0.5),
            WearIveRunMorph.miniCornerRadius, accuracy: 0.0001
        )
    }

    func testMorphChevronRotation() {
        // Continuous 180° rotation with progress (no swap).
        XCTAssertEqual(
            WearIveRunMorph.chevronRotationDegrees(progress: 0, reduceMotion: false, settledFullExpression: false),
            0, accuracy: 0.0001)
        XCTAssertEqual(
            WearIveRunMorph.chevronRotationDegrees(progress: 0.5, reduceMotion: false, settledFullExpression: false),
            90, accuracy: 0.0001)
        XCTAssertEqual(
            WearIveRunMorph.chevronRotationDegrees(progress: 1, reduceMotion: false, settledFullExpression: true),
            180, accuracy: 0.0001)
        // Reduced Motion: no rotation — the settled orientation
        // for the target state (swapped at the midpoint by the
        // view), whatever the progress value says.
        XCTAssertEqual(
            WearIveRunMorph.chevronRotationDegrees(progress: 0.5, reduceMotion: true, settledFullExpression: false),
            0, accuracy: 0.0001)
        XCTAssertEqual(
            WearIveRunMorph.chevronRotationDegrees(progress: 0.5, reduceMotion: true, settledFullExpression: true),
            180, accuracy: 0.0001)
    }

    func testMorphDriverSpringsAndGates() {
        // The decision's iOS spring table, exactly.
        XCTAssertEqual(WearIveRunMorph.springResponse(for: .cue), 0.46, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springDampingFraction(for: .cue), 0.80, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springResponse(for: .tap), 0.42, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springDampingFraction(for: .tap), 0.92, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springResponse(for: .scroll), 0.34, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springDampingFraction(for: .scroll), 1.0, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springResponse(for: .collapse), 0.34, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.springDampingFraction(for: .collapse), 1.0, accuracy: 0.0001)
        // Overshoot on the cue ONLY.
        XCTAssertTrue(WearIveRunMorph.permitsOvershoot(.cue))
        for driver in [WearIveRunMorphDriver.tap, .scroll, .collapse, .reducedMotion] {
            XCTAssertFalse(WearIveRunMorph.permitsOvershoot(driver), "\(driver)")
        }
        // Duration gates.
        XCTAssertEqual(WearIveRunMorph.durationGateSeconds(for: .cue), 0.50, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.durationGateSeconds(for: .tap), 0.45, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.durationGateSeconds(for: .scroll), 0.34, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.durationGateSeconds(for: .collapse), 0.32, accuracy: 0.0001)
        XCTAssertEqual(WearIveRunMorph.reducedMotionDurationSeconds, 0.15, accuracy: 0.0001)
    }

    func testMorphViewWiring_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(view.contains("expansionProgress"),
                      "the morph must be driven by the one progress value")
        XCTAssertTrue(view.contains("WearIveRunMorph.animation(for: driver)"),
                      "transitions must run the driver's pinned animation")
        XCTAssertTrue(view.contains("WearIveRunMorph.tintOpacity"),
                      "the tint layer's opacity — never the material's blur — is what animates")
        XCTAssertFalse(view.contains(".bouncy"), "the bouncy preset overshoots past the cap")
        XCTAssertFalse(view.contains(".snappy"), "the snappy preset is not in the decision's table")
        // The cue's pulse/haptic fire at the settle, not the driver.
        XCTAssertTrue(view.contains("pendingCueSettleGeneration"))
        XCTAssertTrue(view.contains("fireCueSettle()"))
        // The W-1 jump-in affordance is explicit on the expanded card.
        XCTAssertTrue(view.contains("Open the full Wear I've Run collection"))
    }
}

// MARK: - Session rule (GEN-3 lane I-WIR, 2026-11-04)
//
// Sol's correction (Nov 4, binding; supersedes once-per-day):
// one app session produces exactly ONE entry. Within the
// session the entry updates IN PLACE through every location
// and weather change — updates replace, never accumulate; the
// scrapbook keeps the session's last settled version; a new
// session starts a new entry. Entries are keyed by SESSION;
// the dock presents the CURRENT session's entry (the gen-2
// by-date dock key is the thing this rule replaces).
//
// Operational session definition (verbatim, as carried in the
// gate report): a session is one continuous period of app use
// beginning when the app process launches (cold start) and
// ending when that process terminates; backgrounding and
// returning to the same live process does not start a new
// session — only a new process does.
//
// The census evidence these tests kill (X-4 / X-5, recorded on
// Android; the rule is behavioral parity): three place
// resolutions minted three "Added" entries and ZERO "Update
// to…" narrations; the collection held three same-day chapters
// while the pinned TODAY card stayed on the first place and
// duplicated its chapter beneath it.
final class WearIveRunSessionRuleTests: XCTestCase {

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func day(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = 12
        return utcCalendar.startOfDay(for: utcCalendar.date(from: comps)!)
    }

    private func entry(
        session: String,
        place: String,
        feels: Double,
        items: [String],
        day: Date,
        hero: String = "hero"
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: day,
            placeName: place,
            feelsLikeF: feels,
            actualF: feels + 2,
            condition: "Clear",
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: hero,
            sessionID: session
        )
    }

    @MainActor
    private func state(of model: WearIveRunCollectionModel) -> WearIveRunState {
        // The production derivation (WeatherOutfitView's dock):
        // narration kind read off the presented entry itself —
        // the Y-5 atomicity shape.
        let presented = model.sessionEntry()
        return WearIveRunMiniBarModel.state(
            todaysEntry: presented,
            moment: "Now",
            wasUpdatedThisSession: presented?.wasUpdatedInSession ?? false
        )
    }

    // MARK: The session identity itself

    func testProcessSessionIDIsOneValueForTheProcess() {
        // One launch, one identity: every read in this process
        // returns the same value (a fresh process mints a new
        // one — that is the session boundary, by definition).
        XCTAssertFalse(WearIveRunSession.currentID.isEmpty)
        XCTAssertEqual(WearIveRunSession.currentID, WearIveRunSession.currentID)
    }

    func testSessionIdentityShape() {
        let d = day(2026, 11, 4)
        let a = entry(session: "s1", place: "Miami, FL", feels: 90, items: ["Tee"], day: d)
        let b = entry(session: "s2", place: "Miami, FL", feels: 90, items: ["Tee"], day: d)
        // Same day, same place, different sessions: two entries.
        XCTAssertNotEqual(a.id, b.id)
        XCTAssertEqual(a.id, "session|s1")
        // A legacy entry (no session) keeps its day+place identity.
        let legacy = WearIveRunEntry(
            dayStart: d, placeName: "Miami, FL", feelsLikeF: 90, actualF: 92,
            condition: "Clear", itemNames: ["Tee"], itemKeys: ["tee"],
            heroImageReference: "hero"
        )
        XCTAssertEqual(legacy.id, "\(Int(d.timeIntervalSince1970))|miami, fl")
    }

    // MARK: One entry per session — in-place updates (X-4/X-5a)

    @MainActor
    func testInSessionPlaceChangesKeepExactlyOneEntry_LastSettledWins() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s1")
        let d = day(2026, 11, 4)

        // The walkthrough's three place resolutions, one session.
        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"], day: d))
        XCTAssertEqual(model.entries.count, 1)

        model.recordSettled(entry(
            session: "s1", place: "New York, NY", feels: 72,
            items: ["Tee", "Shorts"], day: d))
        XCTAssertEqual(model.entries.count, 1, "a place change replaces, never accumulates")

        let miami = entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"], day: d)
        model.recordSettled(miami)
        XCTAssertEqual(model.entries.count, 1)
        // The scrapbook keeps the session's LAST settled version
        // — carrying its update narration kind on itself (Y-5:
        // the two in-session place moves were narrated updates).
        XCTAssertEqual(model.sessionEntry(), miami.settingWasUpdatedInSession(true))
        XCTAssertEqual(model.sessionEntry()?.placeDisplayName, "Miami, FL")
        XCTAssertEqual(model.sessionEntry()?.feelsLikeF, 90)
        // And it is what a fresh read of the store holds.
        XCTAssertEqual(
            WearIveRunStore(defaults: defaults).entries,
            [miami.settingWasUpdatedInSession(true)])
    }

    @MainActor
    func testUpdateNarrationsAfterTheFirstAdd_NeverAFreshAddedPerPlace() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: defaults), sessionID: "s1")
        let d = day(2026, 11, 4)

        // First add: the session's one "Added".
        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"], day: d))
        XCTAssertEqual(model.cueCount, 1)
        XCTAssertEqual(state(of: model), .added(place: "Overland Park, KS", moment: "Now"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: state(of: model)),
            "Added Overland Park, KS, Now")

        // Place + kit change: narration-from-diff — "Update to"
        // the new entry's payload, never a second "Added".
        model.recordSettled(entry(
            session: "s1", place: "New York, NY", feels: 72,
            items: ["Tee", "Shorts"], day: d))
        XCTAssertEqual(model.cueCount, 2)
        XCTAssertEqual(state(of: model), .updated(payload: "Tee"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: state(of: model)),
            "Update to Tee")

        // Place-only change (same kit): still an update — the
        // bar must never announce a place home has moved past,
        // and never mint a fresh "Added Miami".
        model.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tee", "Shorts"], day: d))
        XCTAssertEqual(model.cueCount, 3)
        XCTAssertEqual(state(of: model), .updated(payload: "Tee"))
        XCTAssertEqual(model.sessionEntry()?.placeDisplayName, "Miami, FL")
    }

    func testClassifierPlaceMoveIsANarratedUpdate_NotSilent() {
        let d = day(2026, 11, 4)
        let overlandPark = entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Tee", "Shorts"], day: d)
        let miamiSameKit = entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tee", "Shorts"], day: d)
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: overlandPark, incoming: miamiSameKit),
            .placeUpdated)
        // Weather-only at the same place stays silent (D-07).
        let miamiWarmer = entry(
            session: "s1", place: "Miami, FL", feels: 92,
            items: ["Tee", "Shorts"], day: d)
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: miamiSameKit, incoming: miamiWarmer),
            .silentUpdate)
    }

    @MainActor
    func testWeatherOnlySettleInSessionUpdatesInPlaceSilently() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: defaults), sessionID: "s1")
        let d = day(2026, 11, 4)
        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"], day: d, hero: "hero-1"))
        // Same place, same kit; numbers and hero settle again.
        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 50,
            items: ["Light Long Sleeve", "Windbreaker"], day: d, hero: "hero-2"))
        XCTAssertEqual(model.entries.count, 1)
        XCTAssertEqual(model.sessionEntry()?.feelsLikeF, 50)
        XCTAssertEqual(model.sessionEntry()?.heroImageReference, "hero-2")
        // Recorded, but no cue and no narration flip: an async
        // landing is not a driver.
        XCTAssertEqual(model.cueCount, 1)
        XCTAssertEqual(state(of: model), .added(place: "Overland Park, KS", moment: "Now"))
    }

    // MARK: Pinned == chapter identity (X-5b)

    func testPinnedEntryIsTheSessionsChapterEntry_AndLiftingItLeavesNoDuplicate() {
        let today = day(2026, 11, 4)
        let yesterday = day(2026, 11, 3)
        let olderMiami = entry(
            session: "s0", place: "Miami, FL", feels: 88,
            items: ["Tee"], day: yesterday)
        let sessionMiami = entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"], day: today)
        let newYork = entry(
            session: "s0", place: "New York, NY", feels: 72,
            items: ["Tee"], day: yesterday)
        let entries = [sessionMiami, olderMiami, newYork]

        // The pin IS the session's chapter entry: the Miami
        // chapter's cover is the very entry the pin presents.
        let pinned = WearIveRunCollection.sessionEntry(from: entries, sessionID: "s1")
        XCTAssertEqual(pinned, sessionMiami)
        let allChapters = WearIveRunCollection.chapters(
            from: entries, filter: .all, calendar: utcCalendar)
        let miamiChapter = allChapters.first { $0.placeName == "Miami, FL" }
        XCTAssertEqual(miamiChapter?.cover.id, pinned?.id)

        // Lifted out (what the collection view renders), the
        // entry appears in NO chapter — it is presented exactly
        // once, as the pin — while its chapter survives on its
        // remaining session.
        let unpinned = entries.filter { $0.id != pinned?.id }
        let chapters = WearIveRunCollection.chapters(
            from: unpinned, filter: .all, calendar: utcCalendar)
        XCTAssertFalse(chapters.flatMap(\.entries).contains { $0.id == pinned?.id })
        let miamiAfter = chapters.first { $0.placeName == "Miami, FL" }
        XCTAssertEqual(miamiAfter?.cover, olderMiami)
        XCTAssertEqual(chapters.count, 2, "chapters still group by location")
    }

    // MARK: A new session starts a new entry

    @MainActor
    func testNewSessionStartsANewEntry_PreviousSessionUntouched() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let d = day(2026, 11, 4)
        let first = WearIveRunCollectionModel(store: store, sessionID: "s1")
        let miami = entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"], day: d)
        first.recordSettled(miami)

        // A new process ≈ a new model over the same store.
        let second = WearIveRunCollectionModel(store: store, sessionID: "s2")
        XCTAssertNil(second.sessionEntry(), "the new session owns no entry yet")
        let overlandPark = entry(
            session: "s2", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve"], day: d)
        second.recordSettled(overlandPark)

        XCTAssertEqual(store.entries.count, 2)
        XCTAssertEqual(first.sessionEntry(), miami, "the prior session's entry is untouched")
        XCTAssertEqual(second.sessionEntry(), overlandPark)
        XCTAssertEqual(second.cueCount, 1, "the new session's first settle is a creation")
        XCTAssertEqual(state(of: second), .added(place: "Overland Park, KS", moment: "Now"))
    }

    // MARK: The dock presents the current session's entry

    @MainActor
    func testDockPresentsCurrentSessionEntryWhileADifferentPlaceIsViewed() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s1")
        let d = day(2026, 11, 4)
        // The W-15 walkthrough scenario, session-keyed: the
        // session's entry settled at Kansas City; the user is
        // now VIEWING New York.
        let kansasCity = entry(
            session: "s1", place: "Kansas City, MO", feels: 45,
            items: ["Light Long Sleeve", "Light Tights"], day: d)
        model.recordSettled(kansasCity)

        XCTAssertEqual(model.sessionEntry(), kansasCity)
        XCTAssertNil(
            store.todaysEntry(placeName: "New York, NY", today: d, calendar: utcCalendar),
            "the viewed place has no entry — the dock must not depend on it")
        // The narration names the SESSION ENTRY's place.
        XCTAssertEqual(state(of: model), .added(place: "Kansas City, MO", moment: "Now"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: state(of: model)),
            "Added Kansas City, MO, Now")
    }

    @MainActor
    func testTrashRemovesTheSessionEntry_AndALaterSettleRecreatesIt() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let model = WearIveRunCollectionModel(store: store, sessionID: "s1")
        let d = day(2026, 11, 4)
        model.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"], day: d))
        // Browsing has since moved the VIEW to New York; trash
        // still removes the presented (session) entry.
        model.removeSessionEntry()
        XCTAssertTrue(model.entries.isEmpty)
        XCTAssertEqual(state(of: model), .preAdd)
        // A later settle in the same session re-creates it —
        // a fresh "Added", as after any trash.
        model.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 91,
            items: ["Tank Top"], day: d))
        XCTAssertEqual(model.entries.count, 1)
        XCTAssertEqual(state(of: model), .added(place: "Miami, FL", moment: "Now"))
    }

    // MARK: Legacy rows — decoded, preserved, never adopted

    func testLegacyEntriesDecodeUntouched_AndAreNeverAdopted() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let d = day(2026, 11, 4)
        // A payload as the pre-session build wrote it: no
        // sessionID key at all (dates encode as seconds since
        // the reference date under the default strategy).
        let legacyJSON = """
        [{"dayStart":\(d.timeIntervalSinceReferenceDate),\
        "placeName":"Miami, FL","feelsLikeF":88,"actualF":90,\
        "condition":"Clear","itemNames":["Tee"],"itemKeys":["tee"],\
        "heroImageReference":"hero-legacy"}]
        """
        defaults.set(Data(legacyJSON.utf8), forKey: "wearIveRun.entries.v1")

        let store = WearIveRunStore(defaults: defaults)
        XCTAssertEqual(store.entries.count, 1, "legacy history must load, not wipe")
        let legacy = store.entries[0]
        XCTAssertEqual(legacy.sessionID, "")
        XCTAssertEqual(legacy.placeName, "Miami, FL")
        XCTAssertEqual(legacy.id, "\(Int(d.timeIntervalSince1970))|miami, fl")

        // A new session settling the same place on the same day
        // does NOT adopt or merge the legacy row: the session
        // mints its own entry and the legacy row stands.
        store.record(entry(
            session: "s-new", place: "Miami, FL", feels: 90,
            items: ["Tank Top"], day: d))
        XCTAssertEqual(store.entries.count, 2)
        XCTAssertTrue(store.entries.contains(legacy))
        // Both live in the one Miami chapter (grouped by
        // location); its day count counts DAYS, not sessions.
        let chapters = WearIveRunCollection.chapters(
            from: store.entries, filter: .all, calendar: utcCalendar)
        XCTAssertEqual(chapters.count, 1)
        XCTAssertEqual(chapters[0].entries.count, 2)
        XCTAssertEqual(chapters[0].dayCount, 1)
    }

    // MARK: Facet atomicity (census Y-5)

    @MainActor
    func testFacetsCommitAtomicallyAcrossAPlaceChange() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: defaults), sessionID: "s1")
        let d = day(2026, 11, 4)
        model.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve", "Windbreaker"], day: d, hero: "hero-overland"))
        model.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top", "Shorts"], day: d, hero: "hero-miami"))

        // Read the persisted value back ONCE, through the JSON
        // round trip: narration kind, place, weather, kit, and
        // photo are facets of this single decoded value — there
        // is no second read from which a stale facet could come.
        let persisted = WearIveRunStore(defaults: defaults).entries
        XCTAssertEqual(persisted.count, 1)
        let settled = persisted[0]
        XCTAssertEqual(settled.placeDisplayName, "Miami, FL")
        XCTAssertEqual(settled.feelsLikeF, 90)
        XCTAssertEqual(settled.itemNames, ["Tank Top", "Shorts"])
        XCTAssertEqual(settled.heroImageReference, "hero-miami")
        XCTAssertTrue(settled.wasUpdatedInSession)
        // The narration computed from THIS value names THIS
        // value's payload: "Update to Tank Top" over the Miami
        // entry — the build-21 shape (an update naming one
        // place's item over another place's entry) cannot be
        // produced, because both halves read the same value.
        let narration = WearIveRunMiniBarModel.state(
            todaysEntry: settled, moment: "Now",
            wasUpdatedThisSession: settled.wasUpdatedInSession)
        XCTAssertEqual(narration, .updated(payload: settled.itemNames[0]))
        XCTAssertEqual(
            WearIveRunNarration.line(for: narration), "Update to Tank Top")
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: settled, formattedFeelsLike: "90°"),
            "Miami, FL · Today — 90° feels like · 2 items")
    }

    @MainActor
    func testNarrationKindSurvivesModelRecreation_AndSilentSettlesPreserveIt() {
        let defaults = UserDefaults(suiteName: "gen3-iwir-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let d = day(2026, 11, 4)
        let first = WearIveRunCollectionModel(store: store, sessionID: "s1")
        first.recordSettled(entry(
            session: "s1", place: "Overland Park, KS", feels: 48,
            items: ["Light Long Sleeve"], day: d))
        first.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 90,
            items: ["Tank Top"], day: d))

        // Home's view (and with it the collection model) is
        // recreated mid-session: the narration kind is still the
        // update form, because it was never the model's to lose —
        // it is stored on the entry.
        let recreated = WearIveRunCollectionModel(store: store, sessionID: "s1")
        XCTAssertEqual(recreated.sessionEntry()?.wasUpdatedInSession, true)
        XCTAssertEqual(state(of: recreated), .updated(payload: "Tank Top"))

        // A weather-only settle updates the numbers and keeps
        // the narration kind it found.
        recreated.recordSettled(entry(
            session: "s1", place: "Miami, FL", feels: 92,
            items: ["Tank Top"], day: d))
        XCTAssertEqual(recreated.sessionEntry()?.feelsLikeF, 92)
        XCTAssertEqual(recreated.sessionEntry()?.wasUpdatedInSession, true)
        XCTAssertEqual(state(of: recreated), .updated(payload: "Tank Top"))
    }

    // MARK: Served-hero identity (census Y-10)

    func testChapterCoverIsTheRecordedServedHero_NeverReResolved() {
        // The chapter renders the reference the entry recorded
        // at settle time — the photo actually served for that
        // place in that session — verbatim.
        let d = day(2026, 11, 4)
        let newHaven = entry(
            session: "s1", place: "New Haven, MO", feels: 53,
            items: ["Tee", "Shorts"], day: d, hero: "served-teal-runner")
        let chapters = WearIveRunCollection.chapters(
            from: [newHaven], filter: .all, calendar: utcCalendar)
        XCTAssertEqual(chapters.first?.cover.heroImageReference, "served-teal-runner")
        XCTAssertEqual(chapters.first?.cover, newHaven)
    }

    // MARK: Source pins (view-layer wiring)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testSessionWiring_sourcePins() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // Recording stamps the model's session identity.
        XCTAssertTrue(home.contains("sessionID: collectionModel.sessionID"),
                      "every recording must carry the current session's identity")
        // The dock presents the session entry and trashes it by
        // session, not by viewed place or date.
        XCTAssertTrue(home.contains("collectionModel.sessionEntry()"))
        XCTAssertTrue(home.contains("collectionModel.removeSessionEntry()"))
        // The collection pins the session entry and lifts it out
        // of the chapters.
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("model.sessionEntry()"),
                      "the pin must be the current session's entry")
        XCTAssertTrue(collection.contains("$0.id != pin.id"),
                      "the pinned entry must be lifted out of the chapter arithmetic")
        XCTAssertFalse(collection.contains("todaysEntries"),
                       "the date-keyed pin was the X-5 defect's other half")
        // Y-10: the recorded hero is the SERVED hero (the view
        // model's flag target — the photo on screen), never the
        // view's locally recomputed default fallback.
        XCTAssertTrue(home.contains("heroImageReference: viewModel.currentHeroFlagTarget?.imageUrl ?? \"\""),
                      "the entry must stamp the served hero identity at settle time")
        // And the collection renders the recorded reference
        // verbatim — it re-resolves nothing at open time.
        XCTAssertTrue(collection.contains("entry.heroImageReference"))
        XCTAssertFalse(collection.contains("FallbackImageProvider"),
                       "the collection must never re-resolve a chapter's photo")
    }
}
