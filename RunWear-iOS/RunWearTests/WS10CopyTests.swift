import XCTest

/// Pins for WS-10 (addendum & copy, iOS lane) — A1, A2, A4 residue:
///
///  * A1: onboarding's sequence is the design's — promise (frame
///    28) → setup questions, one per screen with native push
///    (frames 29–31) → choice (32) → consent (33) → first look
///    (34). The free-looks promise appears ONLY on frame 28's own
///    screen; it is gone from the setup form (which no longer has
///    a header at all) and from the fork.
///  * A2: the welcome headline is Sol's pick, character for
///    character: "Run, weather or not."
///  * T-2 (R-2 ruling): frame 28 stands in the sequence as a
///    standalone screen, shaped as designed (script logo, the
///    headline, two promise rows, Get Started, the quiet footer).
///  * Consent is reachable in ONE gesture from the fork (the
///    customize door presents the opt-in flow, whose first step
///    is consent), and consent acceptance is SILENT (§1.7).
///  * Frame 34's reveal: photo crossfade (FirstLookReveal's
///    numbers) → band +120 ms after the photo lands → ONE success
///    haptic.
///  * A4 residue (R-1's corrected test): EVERY emoji occurrence in
///    app-target code is censused — present-in-design = keep,
///    absent-from-design = defect. The 🚹/🚺 displayIcon and its
///    dead GenderToggle consumer chain are removed; the only
///    remaining code emoji are WeatherViewModel.shareText's four
///    lines — a computation with NO UI consumer since WS-6 (T-4),
///    kept because its composition is pinned by
///    ShareTextCompositionTests (status recorded in the gate
///    report; not deleted without evidence about its tests).
final class WS10CopyTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func occurrences(_ haystack: String, _ needle: String) -> Int {
        haystack.components(separatedBy: needle).count - 1
    }

    /// Every app-target Swift file, as (name, code lines with
    /// comment-only lines removed) — the emoji census scans CODE,
    /// not the comments that document removals and quotations.
    private func appCodeLines() throws -> [(file: String, line: Int, text: String)] {
        let testsDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        let appDir = testsDir.deletingLastPathComponent().appendingPathComponent("RunWear")
        guard let enumerator = FileManager.default.enumerator(
            at: appDir, includingPropertiesForKeys: nil
        ) else { throw XCTSkip("app source tree unreachable") }
        var out: [(String, Int, String)] = []
        for case let url as URL in enumerator where url.pathExtension == "swift" {
            guard let text = try? String(contentsOf: url, encoding: .utf8) else { continue }
            for (idx, raw) in text.components(separatedBy: "\n").enumerated() {
                if raw.trimmingCharacters(in: .whitespaces).hasPrefix("//") { continue }
                out.append((url.lastPathComponent, idx + 1, raw))
            }
        }
        return out
    }

    private func isEmojiScalar(_ scalar: Unicode.Scalar) -> Bool {
        (0x1F300...0x1FAFF).contains(scalar.value)
            || (0x2600...0x26FF).contains(scalar.value)
            || (0x2700...0x27BF).contains(scalar.value)
    }

    // MARK: - A1 · step order (the design's sequence)

    func testStepOrderIsTheDesignSequence() {
        XCTAssertEqual(
            OnboardingStep.fullSequence,
            [.welcome, .temperature, .fit, .comfort, .location, .firstLook],
            "promise (28) → setup (29–31) → choice/consent/first look (32–34)"
        )
        XCTAssertEqual(OnboardingStep.setupQuestions, [.temperature, .fit, .comfort])
    }

    func testStepChainAdvancesInOrderAndEndsAtFirstLook() {
        XCTAssertEqual(OnboardingStep.welcome.next, .temperature)
        XCTAssertEqual(OnboardingStep.temperature.next, .fit)
        XCTAssertEqual(OnboardingStep.fit.next, .comfort)
        XCTAssertEqual(OnboardingStep.comfort.next, .location)
        XCTAssertEqual(OnboardingStep.location.next, .firstLook)
        XCTAssertNil(OnboardingStep.firstLook.next)
    }

    func testSetupIsOneQuestionPerScreenWithNativePush_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("NavigationStack(path: $path)"),
                      "the setup advances through a native NavigationStack (§1.4 push)")
        XCTAssertTrue(onboarding.contains(".navigationDestination(for: OnboardingStep.self)"),
                      "each step is a pushed destination — Back is the exact reverse")
        // One screen per question: each shared control is composed
        // exactly once in onboarding (its own pushed screen), and
        // the old single scrolling form (and its header) is gone.
        XCTAssertEqual(occurrences(onboarding, "TemperatureUnitPicker(unit:"), 1)
        XCTAssertEqual(occurrences(onboarding, "FitPreferencePicker(preference:"), 1)
        XCTAssertEqual(occurrences(onboarding, "ComfortLevelControl(comfortLevel:"), 1)
        XCTAssertFalse(onboarding.contains("preferencesBody"),
                       "the one-screen preferences form is retired")
        XCTAssertFalse(onboarding.contains("Welcome to RunWear"),
                       "the old setup-form header (title + promise) is retired with it")
    }

    // MARK: - A1/T-2 · frame 28 stands, and the promise lives ONLY there

    func testFrame28WelcomeScreenShape_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("private var welcomeScreen"),
                      "frame 28 is a standalone screen (the stack's root), per the T-2 ruling")
        XCTAssertTrue(onboarding.contains(".font(.greatVibes(size: 64))"),
                      "the script logo at frame 28's specimen size")
        XCTAssertTrue(onboarding.contains("Text(OnboardingCopy.welcomeHeadline)"),
                      "the headline slot renders the decided line")
        XCTAssertTrue(onboarding.contains("\"The right outfit for the weather\""),
                      "promise row 1, as designed")
        XCTAssertTrue(onboarding.contains("\"See yourself in every outfit\""),
                      "promise row 2, as designed")
        XCTAssertTrue(onboarding.contains("\"Free to start · Set up in under a minute\""),
                      "frame 28's quiet footer")
        XCTAssertTrue(onboarding.contains("path.append(.temperature)"),
                      "Get Started pushes the first setup question")
    }

    func testFreeLooksPromiseAppearsOnlyOnFrame28() throws {
        // The promise STRING exists exactly once in app code: its
        // definition in OnboardingCopy. The welcome screen renders
        // it through the constant; nothing else may spell it out.
        let lines = try appCodeLines()
        let hits = lines.filter { $0.text.contains(OnboardingCopy.freeLooksPromise) }
        XCTAssertEqual(hits.count, 1,
                       "the promise must be spelled out exactly once — found: \(hits)")
        XCTAssertEqual(hits.first?.file, "OnboardingSequence.swift")

        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertEqual(occurrences(onboarding, "OnboardingCopy.freeLooksPromise"), 1,
                       "the welcome screen references the promise exactly once")
        // …and that reference sits INSIDE the welcome screen (after
        // its declaration, before the question screens begin).
        let welcomeStart = try XCTUnwrap(onboarding.range(of: "private var welcomeScreen"))
        let promiseUse = try XCTUnwrap(onboarding.range(of: "OnboardingCopy.freeLooksPromise"))
        let questionsStart = try XCTUnwrap(onboarding.range(of: "private func questionScreen"))
        XCTAssertTrue(welcomeStart.lowerBound < promiseUse.lowerBound
                      && promiseUse.lowerBound < questionsStart.lowerBound,
                      "the promise renders on frame 28's screen and nowhere later")

        let fork = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertFalse(fork.contains(OnboardingCopy.freeLooksPromise),
                       "the fork (frame 32) carries no promise line — the choice is its copy")
    }

    func testNoPriceStringsInOnboarding() throws {
        // §9/§8G: no price appears anywhere in onboarding. A price
        // STRING is a string literal opening with a $ and a digit
        // ("$9…") — Swift's $0 closure shorthand is not a price.
        for file in ["../RunWear/Views/Onboarding/OnboardingView.swift",
                     "../RunWear/Models/OnboardingSequence.swift",
                     "../RunWear/Views/MakeItMeFlowViews.swift"] {
            let source = try sourceFile(file)
            XCTAssertNil(source.range(of: "\"\\$[0-9]", options: .regularExpression),
                         "\(file) must carry no price string")
        }
    }

    // MARK: - A2 · the headline, character for character

    func testWelcomeHeadlineIsSolPickCharacterForCharacter() {
        XCTAssertEqual(OnboardingCopy.welcomeHeadline, "Run, weather or not.")
        XCTAssertNotEqual(OnboardingCopy.welcomeHeadline, "Run, Weather or Not.",
                          "the tagline's casing is NOT the decided headline — the pick is installed exactly")
    }

    func testOldHeadlineCopyIsGoneFromTheApp() throws {
        let lines = try appCodeLines()
        XCTAssertFalse(lines.contains { $0.text.contains("Let's prepare your perfect run") },
                       "the replaced headline survives nowhere in app code")
        XCTAssertFalse(lines.contains { $0.text.contains("prepare your perfect run") })
    }

    // MARK: - Consent · one gesture from the fork, silent on accept

    func testConsentIsOneGestureFromTheFork_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("onCustomize: { showOptIn = true }"),
                      "the fork's customize door — one tap — presents the opt-in flow")
        XCTAssertEqual(occurrences(onboarding, ".fullScreenCover(isPresented: $showOptIn)"), 1,
                       "the flow rides its own full-screen cover from the fork (WS-5's §1.4 reading)")
        XCTAssertTrue(onboarding.contains("MakeItMeOptInFlow("),
                      "the presented flow is the opt-in flow")
    }

    func testConsentIsTheOptInFlowsFirstStep_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("@State private var step: Step = .consent"),
                      "the opt-in flow OPENS on consent (frame 33) — capture follows it")
        XCTAssertTrue(flow.contains("case .consent:"),
                      "consent is a step of the flow in its own right")
        XCTAssertTrue(flow.contains("These photos are of me, and I am 18 or older."),
                      "the frame-33 attestation, verbatim")
        XCTAssertTrue(flow.contains("step = .capture"),
                      "accepting consent advances to capture — nothing else gates it")
    }

    func testConsentAcceptanceIsSilent_sourcePin() throws {
        // §1.7's haptic table is the complete list; consent is not
        // in it. The whole opt-in flow file carries no sensory
        // feedback at all — consent accept (and the flow around
        // it) fires nothing.
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertEqual(occurrences(flow, ".sensoryFeedback"), 0,
                       "no haptic may ride the consent flow")
        XCTAssertEqual(occurrences(flow, "UINotificationFeedbackGenerator"), 0)
        XCTAssertEqual(occurrences(flow, "UIImpactFeedbackGenerator"), 0)
    }

    // MARK: - Frame 34 · the reveal (§1.4 numbers, one haptic)

    func testFirstLookRevealTiming() {
        XCTAssertEqual(FirstLookReveal.photoCrossfadeSeconds, 0.4, accuracy: 0.0001)
        XCTAssertEqual(FirstLookReveal.bandDelayAfterPhotoSeconds, 0.12, accuracy: 0.0001)
        XCTAssertEqual(FirstLookReveal.bandStartSeconds, 0.52, accuracy: 0.0001,
                       "the band starts 120 ms AFTER the photo lands, not with it")
        XCTAssertEqual(FirstLookReveal.successHapticCount, 1,
                       "exactly one success haptic per reveal (§1.7)")
    }

    func testFirstLookRevealWiring_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains(".sensoryFeedback(.success, trigger: firstLookHapticNonce)"),
                      "the reveal's success haptic is the notification .success (§1.7)")
        XCTAssertEqual(occurrences(onboarding, ".sensoryFeedback("), 1,
                       "the first-look moment carries exactly ONE haptic wiring")
        XCTAssertTrue(onboarding.contains("withAnimation(.easeInOut(duration: FirstLookReveal.photoCrossfadeSeconds))"),
                      "the photo crossfades on the shared timing value")
        XCTAssertTrue(onboarding.contains("FirstLookReveal.bandStartSeconds"),
                      "the band's delay is computed from the same numbers the tests pin")
        XCTAssertTrue(onboarding.contains("guard !firstLookPhotoRevealed else { return }"),
                      "a re-appearance never replays the reveal (or its haptic)")
    }

    // MARK: - A4 residue · the emoji census (R-1's test)

    func testEmojiCensus_onlyTheConsumerlessShareTextRemains() throws {
        let lines = try appCodeLines()
        var hits: [(file: String, line: Int, text: String)] = []
        for entry in lines where entry.text.unicodeScalars.contains(where: isEmojiScalar) {
            hits.append(entry)
        }
        // The complete census of app-target CODE: exactly four
        // lines, all inside WeatherViewModel.shareText. That
        // computation has had no UI consumer since WS-6 removed
        // the home ShareLink (T-4); it is kept — and its emoji
        // with it — because ShareTextCompositionTests pins its
        // composition (see the gate report's census table). Every
        // other occurrence the audit found is gone.
        XCTAssertEqual(hits.count, 4,
                       "emoji census drifted — found: \(hits.map { "\($0.file):\($0.line)" })")
        XCTAssertTrue(hits.allSatisfy { $0.file == "WeatherViewModel.swift" },
                      "no emoji may live outside the shareText computation: \(hits)")
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        let shareStart = try XCTUnwrap(vm.range(of: "var shareText: String?"))
        for hit in hits {
            XCTAssertTrue(hit.text.contains("RunWear Recommendation")
                          || hit.text.contains("placeDisplayName")
                          || hit.text.contains("Feels like")
                          || hit.text.contains("Tip:"),
                          "census hit outside shareText's known lines: \(hit)")
        }
        XCTAssertTrue(vm[shareStart.lowerBound...].contains("🏃 RunWear Recommendation"))
    }

    func testDisplayIconAndItsDeadToggleChainAreRemoved() throws {
        let gender = try sourceFile("../RunWear/Models/GenderPreference.swift")
        XCTAssertFalse(gender.contains("var displayIcon"),
                       "the 🚹/🚺 displayIcon is removed, not left as dead code")
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains("struct GenderToggle"),
                       "GenderToggle had no call sites — the dead chain goes with its glyph")
        XCTAssertFalse(home.contains("struct GenderOption"))
        // The LIVE fit picker renders plain text labels — the
        // design's picker expression on both onboarding and
        // Settings.
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertTrue(controls.contains("Text(\"Men's\")"))
        XCTAssertTrue(controls.contains("Text(\"Women's\")"))
        XCTAssertTrue(controls.contains("Text(\"No preference\")"))
    }

    func testShareTextStatusRecorded_consumerlessButKept() throws {
        // WS-6's flag, recorded as a pin: shareText remains DEFINED
        // (its composition tests still run against it) and no app
        // view consumes it in code — the home toolbar's ShareLink
        // was its last consumer (T-4).
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("var shareText: String?"))
        let lines = try appCodeLines()
        let consumers = lines.filter { $0.file != "WeatherViewModel.swift" && $0.text.contains("shareText") }
        XCTAssertTrue(consumers.isEmpty,
                      "shareText must stay consumerless in code (comments aside): \(consumers)")
    }

    // MARK: - WS-9 fix · frame 34 completeness, fork body, fit-step line

    func testFirstLookBandHeaderIsTheDesignedString() {
        XCTAssertEqual(MakeItMeCopy.firstLookBandHeader, "With Premium",
                       "frame 34's band header, verbatim (Android: FIRST_LOOK_BAND_HEADER)")
    }

    func testFirstLookHeadingAndWeatherLineComposition() {
        // The heading: city only, "Today" when no place is known —
        // Android's firstLookHeading pattern, character for
        // character (design frame 34: "Your first look · Today,
        // New York").
        XCTAssertEqual(MakeItMeCopy.firstLookHeading(city: "New York"),
                       "Your first look · Today, New York")
        XCTAssertEqual(MakeItMeCopy.firstLookHeading(city: nil), "Your first look · Today")
        XCTAssertEqual(MakeItMeCopy.firstLookHeading(city: ""), "Your first look · Today")
        // The weather line: feels-like + condition, items counted
        // only when the outfit has any (design frame 34: "41°
        // feels like · Cloudy · 5 items").
        XCTAssertEqual(
            MakeItMeCopy.firstLookWeatherLine(feelsLike: "41°F", condition: "Cloudy", itemCount: 5),
            "41°F feels like · Cloudy · 5 items")
        XCTAssertEqual(
            MakeItMeCopy.firstLookWeatherLine(feelsLike: "41°F", condition: "Cloudy", itemCount: 0),
            "41°F feels like · Cloudy")
    }

    func testFrame34MomentCarriesTheDesignedElements_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        // Scope every assertion to the frame-34 surface itself.
        let start = try XCTUnwrap(onboarding.range(of: "private var firstLookMomentView"))
        let end = try XCTUnwrap(onboarding.range(of: "private func startFirstLookReveal"))
        let moment = String(onboarding[start.lowerBound..<end.lowerBound])

        XCTAssertTrue(moment.contains("Text(\"Make It Me\")"),
                      "the moment carries its designed script header (WS-9: it was missing)")
        XCTAssertTrue(moment.contains(".greatVibes(size: 34)"),
                      "the script header at Android's 34 pt Great Vibes")
        XCTAssertTrue(moment.contains("MakeItMeCopy.firstLookHeading(city: previewVM.simplePlace?.city)"),
                      "the heading composes from the simple place's city")
        XCTAssertTrue(moment.contains("MakeItMeCopy.firstLookWeatherLine("),
                      "the weather line is the frame's second context line")
        XCTAssertFalse(moment.contains("Text(contextLine)"),
                       "the RW-14 single computed line is retired on frame 34 (it remains on the fork preview and the flag-off ready view)")
        XCTAssertTrue(moment.contains("MakeItMeCopy.firstLookBandHeader"),
                      "the band carries its designed header (WS-9: it was missing)")

        // Placement, mirroring Android's FirstLookMoment: script
        // header → heading → weather line → photo → band header →
        // primary door.
        func position(of needle: String) throws -> String.Index {
            try XCTUnwrap(moment.range(of: needle), "\(needle) missing from the moment").lowerBound
        }
        let script = try position(of: "Text(\"Make It Me\")")
        let heading = try position(of: "MakeItMeCopy.firstLookHeading(")
        let weather = try position(of: "MakeItMeCopy.firstLookWeatherLine(")
        // GEN-2 I2 (W-16/W-17): the photo slot is now the moment's
        // own stateful card (firstLookPhotoCard) — the photo area
        // carries the phase's treatment and copy, so the bare
        // AsyncImage that used to sit in this slot is gone. The
        // ORDER pin's intent is unchanged: header stack → photo →
        // band → primary door.
        let photo = try position(of: "firstLookPhotoCard")
        let bandHeader = try position(of: "MakeItMeCopy.firstLookBandHeader")
        let door = try position(of: "Text(\"See My Outfit\")")
        XCTAssertTrue(script < heading && heading < weather && weather < photo
                      && photo < bandHeader && bandHeader < door,
                      "frame 34's element order must match the design/Android composition")
    }

    func testForkBodyIsFrame32sSubHeadline_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("Text(\"Your recommendations, with you in the photo.\")"),
                      "the fork's body paragraph is frame 32's sub-headline, verbatim")
        XCTAssertFalse(flow.contains("That's a model in your outfit photo"),
                       "the RW-14 deck lead is retired from the fork (WS-9)")
        XCTAssertFalse(flow.contains("on a road like yours"),
                       "no fragment of the retired deck lead survives")
    }

    func testFitStepCarriesNoUnintroducedReassuranceLine_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertFalse(onboarding.contains("No preference selected"),
                       "the removed fit-step line stays removed (WS-9)")
        XCTAssertFalse(onboarding.contains("showing unisex options"),
                       "vocabulary no frame introduces appears nowhere in onboarding")
    }

    func testLocationPickerUsesDesignedIconographyNotTextGlyphs() throws {
        // A4's picker item, iOS side: the location picker's
        // selection/current markers are SF Symbol iconography —
        // no "✓" text glyph serves as a check anywhere in it.
        let picker = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertFalse(picker.contains("Text(\"✓\")"))
        XCTAssertFalse(picker.contains("\"✓\""))
        XCTAssertTrue(picker.contains("Image(systemName: \"location.fill\")"),
                      "the Current location row is marked with designed iconography")
    }
}

/// Pins for GEN-2 I2 (2026-11-04) — W-16 (first-look honesty),
/// W-17 (frame-34 crop), W-18 (the free-looks counter split):
///
///  * While the first look is generating, frame 34's presentation
///    claims NOTHING: no "You're in the photo now.", no meter, no
///    Premium band — the working copy rides ON the photo instead.
///  * The debit exists only in the ready phase, composed from the
///    ONE counter (the server profile's free_generations_used as
///    the store holds it) — the same value Settings' line is
///    composed from, so after a delivered first look both
///    surfaces agree by construction.
///  * The regression that shipped (W-18): frame 34's hardcoded
///    "look 1" line rendered at presentation time while Settings
///    read a counter nothing re-read after delivery. The server
///    charges only on delivery (personal-hero-worker), so the
///    completed-look simulation below feeds the post-delivery
///    profile value (1) into BOTH surfaces' compositions.
final class FirstLookHonestyTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - W-16 · the making phase claims nothing

    func testMakingPhaseHasNoCaptionNoMeterNoBand() {
        for state in [PersonalLookState.making, .none] {
            let p = FirstLookPresentation.resolve(
                lookState: state,
                personalPhotoDisplayed: false,
                freeGenerationsUsed: 0,
                freeAllowance: 5,
                isSubscribed: false
            )
            XCTAssertEqual(p.phase, .making)
            XCTAssertNil(p.caption, "the stock photo must never be captioned as the user")
            XCTAssertNil(p.meterLine, "no debit may be presented before delivery")
            XCTAssertNil(p.failureLine)
            XCTAssertFalse(p.showsPremiumBand, "the soft ask follows the look — it is not presented before the look exists")
            // The working state lives ON the photo: the §7 G1/G2
            // processing copy is the overlay's, not a footer line.
            XCTAssertEqual(p.photoOverlayTitle, MakeItMeCopy.progressTitle)
            XCTAssertEqual(p.photoOverlayDetail, MakeItMeCopy.progressSubline)
        }
    }

    func testFailedPhaseChargesNothingAndSaysSo() {
        let p = FirstLookPresentation.resolve(
            lookState: .failed(reason: .error),
            personalPhotoDisplayed: false,
            freeGenerationsUsed: 0,
            freeAllowance: 5,
            isSubscribed: false
        )
        XCTAssertEqual(p.phase, .failed)
        XCTAssertNil(p.caption)
        XCTAssertNil(p.meterLine, "a failed look is never charged, so no meter may render")
        XCTAssertEqual(p.failureLine, MakeItMeCopy.firstLookFailed)
        XCTAssertTrue(p.failureLine?.contains("No free look was used") == true,
                      "the failure line states the accounting truth")
        XCTAssertFalse(p.showsPremiumBand)
    }

    // MARK: - W-18 · the completed first look: both surfaces agree

    func testCompletedFirstLook_frame34AndSettingsAgree() {
        // Simulate the completed first look exactly as the system
        // records it: the worker delivers the verified hero and
        // increments the profile counter to 1; the store re-reads
        // the profile on landing (awaitHero) and holds 1. Both
        // surfaces compose from that one held value.
        let profile = PersonalProfileRow(
            personalizationOn: true,
            freeGenerationsUsed: 1,
            entitled: false,
            gender: "MALE"
        )
        let used = profile.freeGenerationsUsed

        // Settings' surface (frame 37's meter).
        XCTAssertEqual(
            FreeLooksMeter.settingsLine(used: used, allowance: PersonalHeroStoreAllowance.value),
            "1 of 5 free looks used",
            "after a delivered first look Settings must count it (W-18's shipped defect read 0)"
        )

        // Frame 34's surface, ready phase (the generated photo is
        // the photo on screen).
        let p = FirstLookPresentation.resolve(
            lookState: .ready,
            personalPhotoDisplayed: true,
            freeGenerationsUsed: used,
            freeAllowance: PersonalHeroStoreAllowance.value,
            isSubscribed: false
        )
        XCTAssertEqual(p.phase, .ready)
        XCTAssertEqual(p.caption, "You're in the photo now.")
        XCTAssertEqual(
            p.meterLine,
            "That was look 1 of your 5 free looks — 4 remain, whenever you want them.",
            "frame 34's meter is the SAME count Settings shows: look 1 spent, 4 remain"
        )
        XCTAssertTrue(p.showsPremiumBand, "the soft ask may show once the look exists")
        XCTAssertNil(p.photoOverlayTitle, "no working state remains on a delivered photo")
    }

    func testReadyPhaseBeforeCounterConfirm_showsNoMeterRatherThanAWrongOne() {
        // The photo has landed but the store's profile re-read has
        // not landed yet (counter still 0): the caption is true
        // (he IS in the photo), but no number is printed until the
        // shared counter confirms it — never a "look 0" line that
        // would contradict the debit, and never the old hardcoded
        // "look 1" ahead of the server's count.
        let p = FirstLookPresentation.resolve(
            lookState: .ready,
            personalPhotoDisplayed: true,
            freeGenerationsUsed: 0,
            freeAllowance: 5,
            isSubscribed: false
        )
        XCTAssertEqual(p.phase, .ready)
        XCTAssertEqual(p.caption, "You're in the photo now.")
        XCTAssertNil(p.meterLine)
    }

    func testReadyPhaseMeterCountsDownFromTheSharedCounter() {
        let last = FirstLookPresentation.resolve(
            lookState: .ready, personalPhotoDisplayed: true,
            freeGenerationsUsed: 5, freeAllowance: 5, isSubscribed: false)
        XCTAssertEqual(last.meterLine, "That was look 5 of your 5 free looks.")
        XCTAssertEqual(FreeLooksMeter.settingsLine(used: 5, allowance: 5), "All 5 free looks used")

        let subscribed = FirstLookPresentation.resolve(
            lookState: .ready, personalPhotoDisplayed: true,
            freeGenerationsUsed: 3, freeAllowance: 5, isSubscribed: true)
        XCTAssertNil(subscribed.meterLine, "a subscribed user has no free-look accounting to state")
    }

    func testDisplayedPersonalPhotoWinsOverAnInFlightRegenState() {
        // A personal photo on screen IS the ready state even if
        // the cell also reports a regeneration in flight.
        let p = FirstLookPresentation.resolve(
            lookState: .making,
            personalPhotoDisplayed: true,
            freeGenerationsUsed: 2,
            freeAllowance: 5,
            isSubscribed: false
        )
        XCTAssertEqual(p.phase, .ready)
        XCTAssertEqual(p.meterLine, "That was look 2 of your 5 free looks — 3 remain, whenever you want them.")
    }

    // MARK: - W-16/W-17/W-18 · wiring pins

    func testFrame34HasNoHardcodedDebitLine_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertFalse(onboarding.contains("That was look 1 of your 5 free looks"),
                       "the hardcoded debit line is the W-18 defect — frame 34 composes its meter from the shared counter")
        XCTAssertTrue(onboarding.contains("FirstLookPresentation.resolve("),
                      "frame 34 resolves its state through the presentation model")
        XCTAssertTrue(onboarding.contains("freeGenerationsUsed: store.freeGenerationsUsed"),
                      "frame 34 reads the store's ONE counter — the value Settings reads")
        XCTAssertTrue(onboarding.contains("store.lookState(for:"),
                      "frame 34's phase comes from the server's folded look state for its cell")
    }

    func testFrame34PhotoCardIsTopAnchoredAndCarriesItsState_sourcePin() throws {
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        let start = try XCTUnwrap(onboarding.range(of: "private var firstLookPhotoCard"))
        let end = try XCTUnwrap(onboarding.range(of: "private func startFirstLookReveal"))
        let card = String(onboarding[start.lowerBound..<end.lowerBound])
        XCTAssertTrue(card.contains("HeroPhoto(image: image"),
                      "W-17: the card crops through HeroPhoto — the home hero's top-anchored crop — so the full head is in frame")
        XCTAssertFalse(card.contains("aspectRatio(contentMode: .fill)"),
                       "the bare center-anchored fill that cropped the head is gone from this card")
        XCTAssertTrue(card.contains("photoOverlayTitle"),
                      "W-16: the processing copy renders ON the photo card")
        XCTAssertTrue(card.contains(".blur(radius: 8)"),
                      "W-16: while making, the placeholder photo is visibly treated — never presented as the user")
    }

    func testSettingsMeterComesThroughTheSharedMeter_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(section.contains("FreeLooksMeter.settingsLine(used: store.freeGenerationsUsed"),
                      "Settings composes its meter from the shared counter through the one seam")
        XCTAssertFalse(section.contains("of 5 free looks used\""),
                       "Settings no longer spells its own meter arithmetic inline")
        XCTAssertTrue(section.contains("await store.refreshProfile()"),
                      "opening Settings re-reads the server profile, so a delivered look is counted in-session")
    }

    func testStoreReReadsTheCounterWhenALookLands_sourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("await self.refreshProfile()"),
                      "W-18: awaitHero re-reads the profile when a look lands — the server's charge moment")
    }
}

/// The shipped free allowance, named once for these tests (the
/// store itself is not compiled into the test target; its value
/// is pinned against the server limit by the backend contract).
private enum PersonalHeroStoreAllowance {
    static let value = 5
}
