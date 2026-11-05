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
