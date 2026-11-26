import XCTest
import Foundation
import Security

/// RW-137 iOS client (subscription-as-identity; Sol's ruling,
/// Nov 7, 2026 — the personal bank is keyed to the SUBSCRIPTION,
/// not the device uid):
///
/// - Bank resolution: reconcile-personal-entitlement resolves
///   the caller's bank ENTIRELY server-side and reports the
///   outcome on its response as `bank` — {state: restored |
///   own | new, looks, refs, collision}, or null when there is
///   no outcome (unentitled caller, or resolution unavailable).
///   Resolution REPLACES the retired hint-and-adopt flow: the
///   server no longer reports a source uid to adopt and the
///   client makes no follow-up request — there is no opt-in
///   toggle in UI or schema, no login, no settings screen.
/// - Sync points: bootstrap, entitlement change, and Restore
///   Purchases all run the store's one `reconcile()`. Only the
///   explicit Restore Purchases action surfaces the outcome, as
///   the BankOutcomePolicy line its store call returns;
///   background reconciles never pop UI.
/// - S4c client half (DESIGN §4.8.3): when delete-my-data
///   completes, the client discards its local session material
///   — the Keychain session item is deleted via the auth
///   service's clearLocalSession, and the store re-arms its
///   bootstrap one-shot — so the deleted identity cannot
///   silently re-authenticate and the next bootstrap mints a
///   genuinely fresh anonymous session.
///
/// Functional tests drive the REAL SupabaseAuthService and
/// PersonalHeroAPI (both dual-membered into this target for
/// RW-137) against mocked transports installed through their
/// RW-137 test seams — fixtures only, no production contact.
/// The store itself is app-only (RevenueCat), so its wiring is
/// pinned by source, the established pattern for app-only
/// orchestration in this suite.
///
/// KEYCHAIN IN THIS HOST: the CI test host has no Keychain
/// entitlement — every raw SecItem call returns
/// errSecMissingEntitlement (-34018), proven by run 279's probe
/// and characterized permanently by
/// testRW137_KeychainHostCapability_Characterized below. The
/// closure tests therefore assert the discard through the auth
/// service's own seams (its session read + its persisted-load
/// read), and the literal Keychain step (KeychainStore.clear →
/// SecItemDelete at the service's coordinates) is carried by
/// the source pins and the production-proven store code — the
/// literal in-CI Keychain assertion is UNPROVEN here, stated
/// plainly rather than silently skipped. If a future host
/// grants the entitlement, the characterized probe fails
/// loudly and the direct assertions should be restored.
final class RW137IdentityTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
            .standardizedFileURL
        return try String(contentsOf: url, encoding: .utf8)
    }

    override func setUp() async throws {
        await SupabaseAuthService.shared.installTransportOverride(nil)
        await PersonalHeroAPI.shared.installTransportOverride(nil)
        await SupabaseAuthService.shared.clearLocalSession()
    }

    override func tearDown() async throws {
        await SupabaseAuthService.shared.installTransportOverride(nil)
        await PersonalHeroAPI.shared.installTransportOverride(nil)
        await SupabaseAuthService.shared.clearLocalSession()
    }

    // MARK: - Fixtures

    private static let uidCurrent = "11111111-1111-4111-8111-111111111111"
    private static let uidFresh = "33333333-3333-4333-8333-333333333333"

    /// One mocked backend behind both clients' transport seams.
    /// The auth seam answers anonymous sign-ups with the staged
    /// uid; the API seam routes by function slug.
    private final class MockBackend: @unchecked Sendable {
        private let lock = NSLock()
        private var _signupUid = ""
        private var _reconcileBody = Data("{}".utf8)
        private var _deleteBody = Data(#"{"account_deleted": true}"#.utf8)

        var signupUid: String {
            get { lock.lock(); defer { lock.unlock() }; return _signupUid }
            set { lock.lock(); _signupUid = newValue; lock.unlock() }
        }
        var reconcileBody: Data {
            get { lock.lock(); defer { lock.unlock() }; return _reconcileBody }
            set { lock.lock(); _reconcileBody = newValue; lock.unlock() }
        }
        var deleteBody: Data {
            get { lock.lock(); defer { lock.unlock() }; return _deleteBody }
            set { lock.lock(); _deleteBody = newValue; lock.unlock() }
        }

        func authTransport(_ request: URLRequest) -> (Data, Int) {
            if request.url?.absoluteString.contains("/auth/v1/signup") == true {
                let uid = signupUid
                return (Data("""
                {"access_token":"token-\(uid)","refresh_token":"refresh-\(uid)",\
                "expires_in":3600,"user":{"id":"\(uid)"}}
                """.utf8), 200)
            }
            return (Data("{}".utf8), 200)
        }

        func apiTransport(_ request: URLRequest) -> (Data, URLResponse) {
            let url = request.url?.absoluteString ?? ""
            let response = HTTPURLResponse(
                url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "application/json"])!
            if url.contains("reconcile-personal-entitlement") {
                return (reconcileBody, response)
            }
            if url.contains("delete-my-data") {
                return (deleteBody, response)
            }
            return (Data("{}".utf8), response)
        }
    }

    private func install(_ backend: MockBackend) async {
        await SupabaseAuthService.shared.installTransportOverride { request in
            backend.authTransport(request)
        }
        await PersonalHeroAPI.shared.installTransportOverride { request in
            backend.apiTransport(request)
        }
    }

    // MARK: - Bank outcome: the reconcile contract (model layer)

    func testRW137_ReconcileResponse_BankOutcomeDecoding() {
        // Each state the server can report, with its counts.
        let restored = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true, "bank": {"state": "restored", "looks": 12, "refs": 3, "collision": false}}
        """.utf8))
        XCTAssertEqual(restored?.entitled, true)
        XCTAssertEqual(restored?.bank, BankOutcome(state: "restored", looks: 12, refs: 3, collision: false))

        let own = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true, "bank": {"state": "own", "looks": 4, "refs": 2, "collision": false}}
        """.utf8))
        XCTAssertEqual(own?.bank, BankOutcome(state: "own", looks: 4, refs: 2, collision: false))

        let new = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true, "bank": {"state": "new", "looks": 0, "refs": 0, "collision": true}}
        """.utf8))
        XCTAssertEqual(new?.bank, BankOutcome(state: "new", looks: 0, refs: 0, collision: true))

        // Explicit null = no outcome (unentitled, or resolution
        // unavailable) — never an error.
        let null = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": false, "bank": null}
        """.utf8))
        XCTAssertEqual(null?.entitled, false)
        XCTAssertNil(null?.bank)

        // A malformed body decodes to nil — reconcile's failure
        // mode is doing nothing, never an error state.
        XCTAssertNil(ReconcileEntitlementResponse.decode(from: Data("not json".utf8)))
    }

    func testRW137_ReconcileResponse_BankAbsent_OldServerDecoding() {
        // Absent field = the pre-RW-137 response = no bank
        // outcome; the entitlement word still reads.
        let absent = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true}
        """.utf8))
        XCTAssertNotNil(absent)
        XCTAssertEqual(absent?.entitled, true)
        XCTAssertNil(absent?.bank)
    }

    // MARK: - Bank outcome: the restore message (policy)

    func testRW137_BankOutcomePolicy_RestoreMessageMapping() {
        func bank(_ state: String, _ looks: Int) -> BankOutcome {
            BankOutcome(state: state, looks: looks, refs: 0, collision: false)
        }
        // Not entitled: the policy stays silent — the existing
        // not-entitled flow owns that case, bank or no bank.
        XCTAssertNil(BankOutcomePolicy.restoreMessage(entitled: false, bank: nil))
        XCTAssertNil(BankOutcomePolicy.restoreMessage(entitled: false, bank: bank("restored", 3)))

        // Entitled, no outcome reported (older server /
        // resolution unavailable): the plain active line.
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: nil),
                       "Your subscription is active.")

        // Restored: plural, singular, and empty.
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("restored", 12)),
                       "Restored — your subscription and your 12 saved looks are back.")
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("restored", 1)),
                       "Restored — your subscription and your 1 saved look is back.")
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("restored", 0)),
                       "Restored — your subscription is back. No saved looks were found for it.")

        // Own bank: plural, singular, and empty.
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("own", 4)),
                       "Your subscription is active — 4 saved looks on this account.")
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("own", 1)),
                       "Your subscription is active — 1 saved look on this account.")
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("own", 0)),
                       "Your subscription is active.")

        // New bank: nothing was there to find.
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("new", 0)),
                       "Your subscription is active. No saved looks were found for it.")

        // An unknown future state degrades to the plain line,
        // never to silence or a wrong claim.
        XCTAssertEqual(BankOutcomePolicy.restoreMessage(entitled: true, bank: bank("future", 9)),
                       "Your subscription is active.")
    }

    func testRW137_BankOutcomePolicy_HasBankReferences() {
        XCTAssertFalse(BankOutcomePolicy.hasBankReferences(nil))
        XCTAssertFalse(BankOutcomePolicy.hasBankReferences(
            BankOutcome(state: "own", looks: 2, refs: 0, collision: false)))
        XCTAssertTrue(BankOutcomePolicy.hasBankReferences(
            BankOutcome(state: "restored", looks: 2, refs: 3, collision: false)))
    }

    // MARK: - Bank outcome: the hook against the mocked backend

    func testRW137_ReconcileReportsBankOutcome() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.reconcileBody = Data("""
        {"ok": true, "entitled": true, "bank": {"state": "restored", "looks": 7, "refs": 3, "collision": false}}
        """.utf8)
        await install(backend)

        // The store's reconcile reads exactly this response and
        // folds `bank` into lastBankOutcome (source-pinned
        // below); the message the restore action would surface
        // composes from it.
        let response = await PersonalHeroAPI.shared.reconcileEntitlement()
        XCTAssertEqual(response?.entitled, true)
        XCTAssertEqual(response?.bank, BankOutcome(state: "restored", looks: 7, refs: 3, collision: false))
        XCTAssertEqual(
            BankOutcomePolicy.restoreMessage(entitled: response?.entitled ?? false, bank: response?.bank),
            "Restored — your subscription and your 7 saved looks are back.")
        XCTAssertTrue(BankOutcomePolicy.hasBankReferences(response?.bank))
    }

    func testRW137_ReconcileWithoutBank_StaysSilent() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.reconcileBody = Data(#"{"ok": true, "entitled": false, "bank": null}"#.utf8)
        await install(backend)

        let response = await PersonalHeroAPI.shared.reconcileEntitlement()
        XCTAssertEqual(response?.entitled, false)
        XCTAssertNil(response?.bank)
        XCTAssertNil(BankOutcomePolicy.restoreMessage(
            entitled: response?.entitled ?? false, bank: response?.bank),
            "an unentitled reconcile surfaces no restore line")
    }

    // MARK: - S4c: the deletion closure against the mocked backend

    func testRW137_DeleteClosure_KeychainDiscardedAndNextBootstrapMintsAFreshUid() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.deleteBody = Data(#"{"account_deleted": true}"#.utf8)
        await install(backend)

        // The session the deleted identity lived under.
        let sessionA = try await SupabaseAuthService.shared.ensureSession()
        XCTAssertEqual(sessionA.userId, Self.uidCurrent)
        let liveBefore = try await SupabaseAuthService.shared.currentSession()
        XCTAssertEqual(liveBefore?.userId, Self.uidCurrent,
                       "the deleted identity's session is the live one before deletion")

        // The mocked delete-my-data succeeds through the real
        // API client (receipt confirms the account deletion).
        try await PersonalHeroAPI.shared.deleteMyData()

        // The store's completion step — PersonalHeroStore
        // .deleteAllData, source-pinned below — discards the
        // local session material through the auth service's own
        // seam: cache cleared AND the Keychain item deleted
        // (KeychainStore.clear → SecItemDelete at the service's
        // coordinates, pinned by source; the literal Keychain
        // read is beyond this host — see the characterized
        // probe below).
        await SupabaseAuthService.shared.clearLocalSession()
        let afterDelete = try await SupabaseAuthService.shared.currentSession()
        XCTAssertNil(afterDelete, "no session survives the closure — cache or Keychain")
        let persistedAfter = await SupabaseAuthService.shared.hasPersistedSession()
        XCTAssertFalse(persistedAfter,
                       "the persisted-load seam finds no session item after the discard")

        // The next bootstrap mints a genuinely fresh anonymous
        // session: a NEW uid, live as the current session.
        backend.signupUid = Self.uidFresh
        let sessionB = try await SupabaseAuthService.shared.ensureSession()
        XCTAssertEqual(sessionB.userId, Self.uidFresh)
        XCTAssertNotEqual(sessionB.userId, sessionA.userId)
        let liveAfter = try await SupabaseAuthService.shared.currentSession()
        XCTAssertEqual(liveAfter?.userId, Self.uidFresh)
    }

    func testRW137_DeleteMyData_UnconfirmedReceiptThrows_SessionKept() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.deleteBody = Data(#"{"account_deleted": false}"#.utf8)
        await install(backend)

        let session = try await SupabaseAuthService.shared.ensureSession()
        XCTAssertEqual(session.userId, Self.uidCurrent)

        // The server reports the account deletion did NOT land:
        // the client throws (claims nothing), and because the
        // store clears the session only after a successful call
        // (source-pinned below), the session material survives
        // for the user's retry.
        do {
            try await PersonalHeroAPI.shared.deleteMyData()
            XCTFail("an unconfirmed account deletion must throw")
        } catch let error as PersonalHeroAPIError {
            guard case .server = error else {
                return XCTFail("expected the server error, got \(error)")
            }
        }
        let liveAfter = try await SupabaseAuthService.shared.currentSession()
        XCTAssertEqual(liveAfter?.userId, Self.uidCurrent,
                       "a failed deletion discards nothing — the session is still live")
    }

    // MARK: - Keychain host capability (characterized, RW-137)

    /// CI runs 278–279: the closure tests' direct SecItem query
    /// found no item after a (mock-)successful ensureSession,
    /// while every service-level assertion passed. The probe
    /// named the cause: this CI test host has NO Keychain
    /// entitlement — raw SecItemAdd / SecItemCopyMatching /
    /// SecItemDelete ALL return errSecMissingEntitlement
    /// (-34018). The Keychain itself is unreachable from this
    /// suite, in both directions; KeychainStore is the
    /// production-proven path and is not indicted by this.
    ///
    /// This test CHARACTERIZES the wall so the closure tests'
    /// seam-based assertions are read correctly. If a future
    /// host grants the entitlement, this test fails loudly —
    /// that is the signal to restore direct Keychain assertions
    /// in the closure tests (add → present, clear → absent).
    func testRW137_KeychainHostCapability_Characterized() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: SupabaseAuthService.keychainService,
            kSecAttrAccount as String: "rw137-probe"
        ]
        var attributes = query
        attributes[kSecValueData as String] = Data("probe".utf8)
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        XCTAssertEqual(SecItemAdd(attributes as CFDictionary, nil), errSecMissingEntitlement,
                       "this host cannot add Keychain items — see the characterization above")
        var read = query
        read[kSecReturnData as String] = true
        read[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        XCTAssertEqual(SecItemCopyMatching(read as CFDictionary, &item), errSecMissingEntitlement,
                       "this host cannot read Keychain items — see the characterization above")
        XCTAssertEqual(SecItemDelete(query as CFDictionary), errSecMissingEntitlement,
                       "this host cannot delete Keychain items — see the characterization above")
    }

    // MARK: - Bank resolution + S4c: store + surface wiring (source pins)

    func testRW137_StoreSyncPointsRunTheOneReconcile_SourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("func reconcile() async -> Bool"),
                      "the store owns ONE reconcile pass")
        XCTAssertTrue(store.contains("lastBankOutcome = response.bank"),
                      "the reconcile folds the response's bank outcome into the published property")
        XCTAssertTrue(store.contains("@Published private(set) var lastBankOutcome: BankOutcome?"),
                      "the bank outcome is published state the surfaces read")
        XCTAssertTrue(store.contains("BankOutcomePolicy.hasBankReferences(lastBankOutcome)"),
                      "the server-references check consults the resolved bank's references")
        // The pass runs at every sync point where reconcile
        // already ran: bootstrap, entitlement change, restore —
        // three call sites of the one pass.
        let callSites = store.components(separatedBy: "await reconcile()").count - 1
        XCTAssertEqual(callSites, 3,
                       "bootstrap + entitlementDidChange + restorePurchases all run the one reconcile")
        XCTAssertTrue(store.contains("func restorePurchases() async -> String?"),
                      "Restore Purchases is a store-level sync that returns its surface line")
        XCTAssertTrue(store.contains("await billing.restore()"),
                      "the store restore still runs the RevenueCat restore first")
        XCTAssertTrue(store.contains("BankOutcomePolicy.restoreMessage(entitled: entitled, bank: lastBankOutcome)"),
                      "the restore line composes from the folded outcome through the policy")
    }

    func testRW137_RestoreButtonSurfacesTheOutcome_SourcePin() throws {
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(views.contains("await store.restorePurchases()"),
                      "the Restore purchases button runs the store's restore + reconcile sync")
        XCTAssertTrue(views.contains("restoreMessage = message"),
                      "a non-nil outcome line is surfaced by the restore action")
        XCTAssertTrue(views.contains("if let restoreMessage"),
                      "the surfaced line renders inline in the sheet")
        XCTAssertFalse(views.contains("await store.billing.restore()"),
                       "no surface calls the bare billing restore anymore")
    }

    func testRW137_DeleteAllDataClosure_SourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        let deleteCall = try XCTUnwrap(store.range(of: "try await api.deleteMyData()")?.lowerBound)
        let clearCall = try XCTUnwrap(store.range(of: "SupabaseAuthService.shared.clearLocalSession()")?.lowerBound)
        let rearmCall = try XCTUnwrap(store.range(of: "\n        bootstrapped = false")?.lowerBound)
        XCTAssertLessThan(deleteCall, clearCall,
                          "the Keychain discard happens only AFTER the server deletion succeeds")
        XCTAssertLessThan(clearCall, rearmCall,
                          "the bootstrap one-shot re-arms as part of the same closure")
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(modal.contains("try await personalStore.deleteAllData()"),
                      "the Danger zone's Delete my data routes to the closing flow (no new deletion UI)")
    }

    func testRW137_NoAdoptionMechanismRemains_SourcePin() throws {
        // Resolution replaced the retired hint-and-adopt flow:
        // no client file may still reference it. (The word
        // "adoption" survives only in unrelated hero-serving
        // senses elsewhere — bundled-photo adoption in the view
        // model — never in the identity mechanism's files.)
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertFalse(models.contains("adoption_available"))
        XCTAssertFalse(models.contains("IdentityAdoptionPolicy"),
                       "the adoption policy is replaced by BankOutcomePolicy")
        XCTAssertFalse(models.contains("identityAdoptionBody"),
                       "no adoption request body is composed anymore")
        let api = try sourceFile("../RunWear/Services/PersonalHeroAPI.swift")
        XCTAssertFalse(api.contains("requestIdentityAdoption"))
        XCTAssertFalse(api.contains("request-identity-adoption"),
                       "the client never calls the retired endpoint")
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertFalse(store.contains("reconcileWithAdoption"),
                       "the two-step pass is replaced by the one reconcile")
        // Sol's ruling (DESIGN §4.1): resolution is automatic —
        // no opt-in toggle in UI or schema, no new user-facing
        // flow. The view layer never references the mechanism.
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertFalse(modal.lowercased().contains("adoption"),
                       "Settings grows no adoption surface")
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(views.contains("requestIdentityAdoption"))
        XCTAssertFalse(models.contains("adoptionEnabled"))
        XCTAssertFalse(models.contains("adoptionOptIn"),
                       "no per-user adoption flag exists in the schema-facing model layer")
    }

    // MARK: - S4b verification: the iOS Keychain posture (DESIGN §4.9)

    func testRW137_KeychainPosture_ThisDeviceOnly_NothingToChange() throws {
        // The session item is kSecAttrAccessibleAfterFirstUnlock-
        // ThisDeviceOnly: ThisDeviceOnly items do not migrate
        // (no iCloud Keychain sync, excluded from device
        // transfers), so iOS needs NO S4b exclusion work — the
        // posture is verified in source and asserted by value.
        XCTAssertEqual(SupabaseAuthService.keychainService, "com.uptimeventures.runwear.supabase")
        XCTAssertEqual(SupabaseAuthService.keychainAccount, "personal-hero-session")
        let auth = try sourceFile("../RunWear/Services/SupabaseAuthService.swift")
        XCTAssertTrue(auth.contains("kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly"),
                      "the session item is ThisDeviceOnly — it cannot migrate to a new device")
        XCTAssertFalse(auth.contains("kSecAttrSynchronizable"),
                       "the item is never marked synchronizable")
        XCTAssertTrue(auth.contains("keyed to the install for free users; keyed to the subscription for Premium"),
                      "DESIGN §6: the posture comment states the RW-137 rule")
    }
}
