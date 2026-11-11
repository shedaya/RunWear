import XCTest

/// GEN-6 RW-78 + RW-79 — iOS lane pins.
///
/// RW-78 (the fork): Sol's device evidence (Nov 6, 2026) settled
/// against the GEN-6 RW-52 select-then-commit fork — tapping
/// "Use my photos" only selected the card and appeared to do
/// nothing. The doors act on tap again, gen-5's wiring; the
/// RW-52 frame-32 surface (script title, settled door headings,
/// honesty line) is unchanged.
///
/// RW-79 (look-state truth): a generation's terminal state must
/// resolve on Home within seconds of a lifecycle event, not
/// only while the store's poll happens to be running. The fix:
/// a store revalidation (one fresh read + a policy-gated poll
/// re-arm), a view-model hook that swaps a landed hero in or
/// re-attaches the swap-in awaiter, and three Home call sites
/// (foreground, paywall dismissal, Home appearing). Pure logic
/// is pinned on the dual-membered models; wiring is pinned by
/// reading the source of record — the suite's established
/// pattern, so a drift in the shipped source fails the gate.
final class Gen6RW78RW79Tests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func region(_ source: String, from start: String, to end: String) throws -> String {
        let s = try XCTUnwrap(source.range(of: start), "region start not found: \(start)")
        let e = try XCTUnwrap(source.range(of: end, range: s.upperBound..<source.endIndex),
                              "region end not found: \(end)")
        return String(source[s.lowerBound..<e.lowerBound])
    }

    private func occurrences(_ haystack: String, _ needle: String) -> Int {
        haystack.components(separatedBy: needle).count - 1
    }

    // MARK: - RW-78 · the fork's doors act on tap

    func testRW78ForkDoorsActOnTap_sourcePin() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        let fork = try region(flow,
                              from: "struct MakeItMeForkView",
                              to: "// MARK: - The opt-in flow container")
        // Gen-5's wiring, restored: each door's button IS the
        // navigation — the callbacks fire on the tap itself.
        XCTAssertTrue(fork.contains("Button(action: onCustomize)"),
                      "\"Use my photos\" navigates to the opt-in flow on tap")
        XCTAssertTrue(fork.contains("Button(action: onStock)"),
                      "\"Use standard photos\" completes onboarding on tap")
        // The select-then-commit apparatus is gone, whole: no
        // selection state, no selection enum, no card-level tap
        // gestures doing selection's work, no commit button.
        XCTAssertFalse(fork.contains("selectedDoor"),
                       "no selection state remains on the fork")
        XCTAssertFalse(fork.contains("ForkDoor"),
                       "the selection enum is deleted with the state")
        XCTAssertFalse(fork.contains("onTapGesture"),
                       "the cards carry no selection tap gestures")
        XCTAssertFalse(fork.contains("Text(\"Continue\")"),
                       "the fork has no separate commit button")
        // The RW-52 frame-32 surface the ruling did NOT touch:
        // script title, settled door headings, honesty line.
        XCTAssertTrue(fork.contains("Text(\"Make It Me\")"))
        XCTAssertTrue(fork.contains("Text(\"Make it me\")"))
        XCTAssertTrue(fork.contains("Text(\"Standard photos\")"))
        XCTAssertTrue(fork.contains("Either way, your recommendations don't change — only the photo does."))
    }

    // MARK: - RW-79 · the re-arm rule (pure behaviour)

    func testRW79RearmPolicy_OnlyMakingWithoutAPollRearms() {
        // The one re-arm case: still MAKING, no poll stored.
        XCTAssertTrue(LookRevalidationPolicy.shouldRearmPoll(state: .making, pollRunning: false))
        // A running poll needs no second one.
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .making, pollRunning: true))
        // Terminal states need no poll — the revalidation's own
        // fold is the resolution Home renders.
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .ready, pollRunning: false))
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .ready, pollRunning: true))
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .failed(reason: .error), pollRunning: false))
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .failed(reason: .qualityRefused), pollRunning: true))
        // NONE is honest quiet — nothing is known to be running.
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .none, pollRunning: false))
        XCTAssertFalse(LookRevalidationPolicy.shouldRearmPoll(state: .none, pollRunning: true))
    }

    // MARK: - RW-79 · the contract's default is the fresh read

    private final class FreshReadResolver: PersonalHeroResolving {
        var isOptedIn = true
        var resolvedURL: String?
        private(set) var resolveCalls = 0
        func resolveHero(for combination: PersonalCombination) async -> String? {
            resolveCalls += 1
            return resolvedURL
        }
        func awaitHero(for combination: PersonalCombination) async -> String? { nil }
    }

    func testRW79DefaultRevalidationIsTheFreshRead() async {
        let combo = PersonalCombination(
            genderSegment: "MALE", weather: .RAIN, temp: .WARM, time: .DAWN,
            outfitItems: ["Singlet"])
        let resolver = FreshReadResolver()
        resolver.resolvedURL = "https://example.invalid/hero.webp"
        let url = await resolver.revalidateLookState(for: combo)
        XCTAssertEqual(url, "https://example.invalid/hero.webp",
                       "the contract's default revalidation returns the fresh read's URL")
        XCTAssertEqual(resolver.resolveCalls, 1,
                       "the default revalidation is exactly one fresh read")
    }

    func testRW79ContractCarriesRevalidationWithDefault_sourcePin() throws {
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertEqual(occurrences(models, "func revalidateLookState(for combination: PersonalCombination) async -> String?"), 2,
                       "the contract carries the requirement and the extension's default — no more, no fewer")
        XCTAssertTrue(models.contains("await resolveHero(for: combination)"),
                      "the default revalidation is the fresh read itself")
    }

    // MARK: - RW-79 · the store's revalidation

    func testRW79StoreRevalidation_ReadsOnceAndRearmsThePoll_sourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        let body = try region(store,
                              from: "func revalidateLookState(for combination: PersonalCombination) async -> String?",
                              to: "private func lookupAndMaybeGenerate")
        // RE-PINNED for RW-134 (fix wave): the store's
        // revalidation deliberately does NOT route through
        // resolveHero — build 30 did, and its cache fresh-URL
        // fast path returned the morning's answer while the
        // exact cell banked behind it (the stranded
        // settle-bank-return sequence). The one fresh read is
        // the lookup itself, which folds the server's word.
        XCTAssertTrue(body.contains("await lookupAndMaybeGenerate(combination: combination, userInitiated: false)"),
                      "the revalidation performs the one fresh read through the lookup (which folds the server's word)")
        XCTAssertTrue(body.contains("LookRevalidationPolicy.shouldRearmPoll"),
                      "the poll re-arm is gated on the pure policy")
        XCTAssertTrue(body.contains("awaitHero(for: combination)"),
                      "a still-MAKING cell with no poll gets its poll re-armed")
        XCTAssertFalse(body.contains("requestGeneration"),
                       "the revalidation never requests generation itself — a failed cell is re-requested only by the user's Try again")
    }

    // MARK: - RW-79 · the view-model hook

    func testRW79ViewModelHook_SwapsLandingsAndReattachesTheAwaiter_sourcePin() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        let hook = try region(vm,
                              from: "func revalidatePersonalLookState()",
                              to: "func refreshHero()")
        XCTAssertTrue(hook.contains("await store.revalidateLookState(for: combo)"),
                      "the hook drives the store's revalidation for the current cell")
        XCTAssertTrue(hook.contains("self.heroImageUrl != url"),
                      "a landed hero swaps in only when it is not already the paint")
        XCTAssertTrue(hook.contains("store.lookState(for: combo) == .making"),
                      "a still-MAKING cell is detected off the folded state")
        XCTAssertTrue(hook.contains("attachPersonalAwaiter(for: combo, store: store)"),
                      "a still-MAKING cell gets the swap-in awaiter re-attached")
        // The awaiter is ONE factored body, shared by the
        // resolution path and the hook — not two copies to drift.
        // RE-PINNED for RW-130 (fix wave): the exact-watch
        // behind a non-exact serve is a THIRD call site, inside
        // applyPersonalHero next to its in-flight attach. The
        // discipline the count guards — ONE factored body,
        // never a copy to drift — is unchanged.
        XCTAssertEqual(occurrences(vm, "attachPersonalAwaiter(for"), 4,
                       "the awaiter factory: one definition, three call sites (applyPersonalHero ×2 incl. the RW-130 exact-watch + the RW-79 hook)")
        XCTAssertEqual(occurrences(vm, "await store.awaitHero"), 1,
                       "the store poll is awaited in exactly one place — the factored awaiter")
    }

    // MARK: - RW-79 · Home's lifecycle call sites

    func testRW79HomeRevalidatesOnForegroundPaywallDismissalAndAppear_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("@Environment(\\.scenePhase)"),
                      "Home observes the app's lifecycle phase")
        XCTAssertTrue(home.contains("if phase == .active"),
                      "returning to the foreground triggers the revalidation")
        // RW-161 RE-PIN (device verdict, Nov 10, 2026): the
        // paywall's sheet binding is the owner-gated form; its
        // onDismiss still runs the RW-79 revalidation (the
        // release precedes it in the same closure).
        XCTAssertTrue(home.contains(".sheet(isPresented: ownedBinding(.heroPaywall, $showHeroPillPaywall), onDismiss: {"),
                      "dismissing the hero paywall triggers the revalidation")
        XCTAssertEqual(occurrences(home, "viewModel.revalidatePersonalLookState()"), 3,
                       "exactly three call sites: foreground, paywall dismissal, Home appearing")
    }

    // MARK: - RW-79 · the settled failure surface is untouched

    func testRW79SettledFailedCopyUntouched_sourcePin() throws {
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        // Sol's verbatim wording (Nov 1, 2026) — the line the
        // revalidation exists to surface promptly; this lane
        // must not have drifted it by a character.
        XCTAssertTrue(models.contains("static let lookFailed = \"Your look couldn't be made from these photos — tap to try again.\""),
                      "the settled failed line stands verbatim")
        XCTAssertTrue(models.contains("static let lookRetry = \"Try again\""),
                      "the settled retry label stands verbatim")
    }
}
