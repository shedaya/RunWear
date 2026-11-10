import XCTest
import Foundation
import Security

/// RW-137 iOS client (DESIGN §7 S4 + S4c client half; design of
/// record: rw137-identity-exploration-2026-11-07/DESIGN.md):
///
/// - S4 adoption hook: reconcile's response gains an optional
///   `adoption_available` source uid; when RevenueCat proof
///   links the caller's entitled identity to a prior uid, the
///   client calls `request-identity-adoption` (the source uid
///   rides as a lookup hint — the server re-verifies, so a
///   stale/wrong hint is a silent no-op) and renders the
///   restored state through the EXISTING profile/bank surfaces.
///   Adoption is AUTOMATIC — a standing Premium property (Sol,
///   Nov 7, 2026): no opt-in toggle in UI or schema, no new
///   user-facing flow, no login, no settings screen.
/// - Sync points: bootstrap, entitlement change, and Restore
///   Purchases all run the store's one reconcileWithAdoption.
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
    private static let uidSource = "22222222-2222-4222-8222-222222222222"
    private static let uidFresh = "33333333-3333-4333-8333-333333333333"

    /// One mocked backend behind both clients' transport seams.
    /// The auth seam answers anonymous sign-ups with the staged
    /// uid; the API seam routes by function slug and records
    /// every adoption request (body hint + caller JWT).
    private final class MockBackend: @unchecked Sendable {
        private let lock = NSLock()
        private var _signupUid = ""
        private var _reconcileBody = Data("{}".utf8)
        private var _deleteBody = Data(#"{"account_deleted": true}"#.utf8)
        private var _adoptionCalls: [(sourceUserId: String?, authorization: String?)] = []

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
        var adoptionCalls: [(sourceUserId: String?, authorization: String?)] {
            lock.lock(); defer { lock.unlock() }; return _adoptionCalls
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
            if url.contains("request-identity-adoption") {
                var hint: String? = nil
                if let body = request.httpBody,
                   let json = try? JSONSerialization.jsonObject(with: body) as? [String: Any] {
                    hint = json["source_user_id"] as? String
                }
                lock.lock()
                _adoptionCalls.append((
                    sourceUserId: hint,
                    authorization: request.value(forHTTPHeaderField: "Authorization")))
                lock.unlock()
                return (Data("{}".utf8), response)
            }
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

    private func keychainItemExists() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: SupabaseAuthService.keychainService,
            kSecAttrAccount as String: SupabaseAuthService.keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        return SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess
    }

    // MARK: - S4: the reconcile contract (model layer)

    func testRW137_ReconcileResponse_AdoptionAvailableDecoding() {
        let withAdoption = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true, "adoption_available": "\(Self.uidSource)"}
        """.utf8))
        XCTAssertEqual(withAdoption?.adoptionAvailable, Self.uidSource)

        // Absent field = the pre-RW-137 response = no adoption.
        let absent = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "entitled": true}
        """.utf8))
        XCTAssertNotNil(absent)
        XCTAssertNil(absent?.adoptionAvailable)

        // Explicit null = no adoption.
        let null = ReconcileEntitlementResponse.decode(from: Data("""
        {"ok": true, "adoption_available": null}
        """.utf8))
        XCTAssertNil(null?.adoptionAvailable)

        // A malformed body decodes to nil — reconcile's failure
        // mode is doing nothing, never an error state.
        XCTAssertNil(ReconcileEntitlementResponse.decode(from: Data("not json".utf8)))
    }

    func testRW137_IdentityAdoptionPolicy_TriggerRules() {
        let reporting = ReconcileEntitlementResponse.decode(from: Data("""
        {"adoption_available": "\(Self.uidSource)"}
        """.utf8))
        XCTAssertEqual(
            IdentityAdoptionPolicy.sourceToAdopt(response: reporting, currentUserId: Self.uidCurrent),
            Self.uidSource,
            "a reported source distinct from the caller triggers adoption")

        // Self-adoption never fires (the server would refuse it;
        // the client does not even ask).
        let selfReport = ReconcileEntitlementResponse.decode(from: Data("""
        {"adoption_available": "\(Self.uidCurrent)"}
        """.utf8))
        XCTAssertNil(IdentityAdoptionPolicy.sourceToAdopt(
            response: selfReport, currentUserId: Self.uidCurrent))

        // Absent / empty / missing-response hints never fire.
        XCTAssertNil(IdentityAdoptionPolicy.sourceToAdopt(
            response: ReconcileEntitlementResponse.decode(from: Data("{}".utf8)),
            currentUserId: Self.uidCurrent))
        XCTAssertNil(IdentityAdoptionPolicy.sourceToAdopt(
            response: ReconcileEntitlementResponse.decode(from: Data(
                #"{"adoption_available": "  "}"#.utf8)),
            currentUserId: Self.uidCurrent))
        XCTAssertNil(IdentityAdoptionPolicy.sourceToAdopt(
            response: nil, currentUserId: Self.uidCurrent))
    }

    func testRW137_IdentityAdoptionBody_CarriesTheSourceHintOnly() {
        let body = PersonalHeroRequests.identityAdoptionBody(sourceUserId: Self.uidSource)
        XCTAssertEqual(body.count, 1, "the hint is the whole body — no assertion rides along")
        XCTAssertEqual(body["source_user_id"] as? String, Self.uidSource)
    }

    // MARK: - S4: the hook against the mocked backend

    func testRW137_ReconcileReportsAdoption_AdoptionRequestPostsTheHint() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.reconcileBody = Data("""
        {"ok": true, "entitled": true, "adoption_available": "\(Self.uidSource)"}
        """.utf8)
        await install(backend)

        // The store's exact sequence (its wiring is source-pinned
        // below): reconcile → policy → adoption request.
        let response = await PersonalHeroAPI.shared.reconcileEntitlement()
        XCTAssertEqual(response?.adoptionAvailable, Self.uidSource)
        let currentUserId = await PersonalHeroAPI.shared.currentUserId()
        XCTAssertEqual(currentUserId, Self.uidCurrent)
        let source = IdentityAdoptionPolicy.sourceToAdopt(
            response: response, currentUserId: currentUserId)
        XCTAssertEqual(source, Self.uidSource)
        await PersonalHeroAPI.shared.requestIdentityAdoption(sourceUserId: try XCTUnwrap(source))

        XCTAssertEqual(backend.adoptionCalls.count, 1)
        XCTAssertEqual(backend.adoptionCalls.first?.sourceUserId, Self.uidSource,
                       "the source uid rides as the lookup hint")
        XCTAssertEqual(backend.adoptionCalls.first?.authorization,
                       "Bearer token-\(Self.uidCurrent)",
                       "the request carries the CALLER's JWT — the server derives the destination from it")
    }

    func testRW137_ReconcileWithoutAdoption_StaysSilent() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.reconcileBody = Data(#"{"ok": true, "entitled": true}"#.utf8)
        await install(backend)

        let response = await PersonalHeroAPI.shared.reconcileEntitlement()
        XCTAssertNil(response?.adoptionAvailable)
        let source = IdentityAdoptionPolicy.sourceToAdopt(
            response: response,
            currentUserId: await PersonalHeroAPI.shared.currentUserId())
        XCTAssertNil(source, "no reported source → no adoption request is ever composed")
        XCTAssertTrue(backend.adoptionCalls.isEmpty)
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
        XCTAssertTrue(keychainItemExists(), "the session persists in the Keychain before deletion")

        // The mocked delete-my-data succeeds through the real
        // API client (receipt confirms the account deletion).
        try await PersonalHeroAPI.shared.deleteMyData()

        // The store's completion step — PersonalHeroStore
        // .deleteAllData, source-pinned below — discards the
        // local session material through the auth service's own
        // seam.
        await SupabaseAuthService.shared.clearLocalSession()
        XCTAssertFalse(keychainItemExists(),
                       "the deleted identity's Keychain item is removed, so it cannot silently re-authenticate")
        let afterDelete = try await SupabaseAuthService.shared.currentSession()
        XCTAssertNil(afterDelete, "no session survives the closure — cache or Keychain")

        // The next bootstrap mints a genuinely fresh anonymous
        // session: a NEW uid, persisted as the live session.
        backend.signupUid = Self.uidFresh
        let sessionB = try await SupabaseAuthService.shared.ensureSession()
        XCTAssertEqual(sessionB.userId, Self.uidFresh)
        XCTAssertNotEqual(sessionB.userId, sessionA.userId)
        XCTAssertTrue(keychainItemExists())
        let persisted = try await SupabaseAuthService.shared.currentSession()
        XCTAssertEqual(persisted?.userId, Self.uidFresh)
    }

    func testRW137_DeleteMyData_UnconfirmedReceiptThrows_SessionKept() async throws {
        let backend = MockBackend()
        backend.signupUid = Self.uidCurrent
        backend.deleteBody = Data(#"{"account_deleted": false}"#.utf8)
        await install(backend)

        _ = try await SupabaseAuthService.shared.ensureSession()
        XCTAssertTrue(keychainItemExists())

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
        XCTAssertTrue(keychainItemExists(), "a failed deletion discards nothing")
    }

    // MARK: - S4/S4c: store + surface wiring (source pins)

    func testRW137_StoreSyncPointsRunTheAdoptionHook_SourcePin() throws {
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("func reconcileWithAdoption()"),
                      "the store owns ONE reconcile + adoption pass")
        XCTAssertTrue(store.contains("IdentityAdoptionPolicy.sourceToAdopt("),
                      "the trigger is the model-layer policy")
        XCTAssertTrue(store.contains("api.requestIdentityAdoption(sourceUserId: source)"),
                      "a reported source is requested for adoption")
        // The hook runs at every sync point where reconcile
        // already ran: bootstrap, entitlement change, restore —
        // three call sites of the one pass.
        let callSites = store.components(separatedBy: "await reconcileWithAdoption()").count - 1
        XCTAssertEqual(callSites, 3,
                       "bootstrap + entitlementDidChange + restorePurchases all run the adoption hook")
        XCTAssertTrue(store.contains("func restorePurchases()"),
                      "Restore Purchases is a store-level sync, not a bare billing call")
        XCTAssertTrue(store.contains("await billing.restore()"),
                      "the store restore still runs the RevenueCat restore first")
    }

    func testRW137_RestoreButtonRoutesThroughTheStoreSync_SourcePin() throws {
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(views.contains("await store.restorePurchases()"),
                      "the Restore purchases button runs the store's restore + adoption sync")
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

    func testRW137_NoAdoptionSurfaceOrToggle_SourcePin() throws {
        // Sol's ruling (DESIGN §4.1): adoption is automatic —
        // no opt-in toggle in UI or schema, no new user-facing
        // flow. The mechanism's tokens live in the model/API/
        // store layers only; the view layer never references it.
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertFalse(modal.lowercased().contains("adoption"),
                       "Settings grows no adoption surface")
        let views = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(views.contains("requestIdentityAdoption"))
        XCTAssertFalse(views.contains("adoption_available"))
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
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
