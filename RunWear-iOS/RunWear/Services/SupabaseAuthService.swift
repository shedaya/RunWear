import Foundation
import Security

// MARK: - Supabase Auth session for RW-14
//
// RunWear has no accounts and no login; the app's Supabase
// identity is an ANONYMOUS Auth user (auth.users row), created
// on first use and persisted in the Keychain across launches.
// RW-137 posture (DESIGN §6): the identity is
// keyed to the install for free users; keyed to the subscription for Premium
// — a restored subscription's personal layer is
// re-keyed server-side onto the current install's uid on
// RevenueCat proof (DESIGN §4.4), so durability follows the
// subscription automatically, never a toggle. The RW-14 backend
// derives the user from the verified JWT on every call — no function
// accepts a user id from the client — so every personal-hero call
// needs this session's access token.
//
// KNOWN SERVER GAP (verified 2026-10-31 by direct probe): the
// project currently has anonymous sign-ins DISABLED
// (POST /auth/v1/signup -> 422 anonymous_provider_disabled). Until
// that toggle is enabled server-side, ensureSession() throws
// .anonymousSignInDisabled and the feature stays in its stock state —
// nothing is faked client-side. The day the toggle flips, this code
// path works unchanged.
actor SupabaseAuthService {
    static let shared = SupabaseAuthService()

    struct Session: Codable {
        let accessToken: String
        let refreshToken: String
        let userId: String
        let expiresAt: Date
    }

    enum AuthError: Error, Equatable {
        case anonymousSignInDisabled
        case noSession
        case server(String)
        case http(Int)
    }

    private let baseURL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

    /// The persisted session's Keychain coordinates (DESIGN
    /// §1.1). Published on the service — not buried in the store
    /// below — so the deletion-closure tests assert against the
    /// auth service's own seam.
    static let keychainService = "com.uptimeventures.runwear.supabase"
    static let keychainAccount = "personal-hero-session"

    /// RW-137 test seam: when set, replaces URLSession as the
    /// auth transport (returns body + HTTP status), so tests
    /// drive the real session code against mocked auth responses
    /// (fixtures only — no production contact). Production never
    /// sets it. Internal, not public: the seam exists for the
    /// test target's dual membership of this file.
    typealias Transport = @Sendable (URLRequest) async throws -> (Data, Int)
    private var transportOverride: Transport?

    func installTransportOverride(_ transport: Transport?) {
        transportOverride = transport
    }

    private var cached: Session?

    private init() {}

    /// The persisted session, refreshed if it is near expiry; nil if
    /// none exists yet.
    func currentSession() async throws -> Session? {
        if let cached, cached.expiresAt.timeIntervalSinceNow > 120 {
            return cached
        }
        guard let stored = KeychainStore.load() else {
            cached = nil
            return nil
        }
        if stored.expiresAt.timeIntervalSinceNow > 120 {
            cached = stored
            return stored
        }
        let refreshed = try await refresh(stored)
        cached = refreshed
        KeychainStore.save(refreshed)
        return refreshed
    }

    /// Whether a session is persisted in the Keychain right
    /// now, read through the store itself (no cache). The
    /// RW-137 deletion-closure tests assert on this seam: after
    /// clearLocalSession the persisted item must be gone.
    /// (In the CI test host this reads false unconditionally —
    /// the host has no Keychain entitlement and every SecItem
    /// call returns errSecMissingEntitlement, proven by
    /// RW137IdentityTests' characterized probe; on device it is
    /// the literal persisted truth.)
    func hasPersistedSession() -> Bool {
        KeychainStore.load() != nil
    }

    /// A valid session, creating the anonymous user on first use.
    func ensureSession() async throws -> Session {
        if let existing = try await currentSession() {
            return existing
        }
        let created = try await signInAnonymously()
        cached = created
        KeychainStore.save(created)
        return created
    }

    /// Forget the local session: the in-memory cache AND the
    /// Keychain item. Used when the server session is rejected,
    /// and as the client half of the RW-137 deletion closure
    /// (DESIGN §4.8.3): when delete-my-data completes, this
    /// discard is what stops the deleted identity from quietly
    /// re-authenticating — the next ensureSession() mints a
    /// genuinely fresh anonymous user.
    func clearLocalSession() {
        cached = nil
        KeychainStore.clear()
    }

    // MARK: - Auth endpoints

    private struct TokenResponse: Decodable {
        let accessToken: String
        let refreshToken: String
        let expiresIn: Int
        let user: UserBody

        struct UserBody: Decodable { let id: String }

        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token"
            case refreshToken = "refresh_token"
            case expiresIn = "expires_in"
            case user
        }
    }

    private func signInAnonymously() async throws -> Session {
        let (data, status) = try await authPost(path: "/auth/v1/signup", body: ["data": [:]] as [String: Any])
        if status == 422 || status == 400 {
            let text = String(data: data, encoding: .utf8) ?? ""
            if text.contains("anonymous_provider_disabled") || text.contains("Anonymous sign-ins are disabled") {
                throw AuthError.anonymousSignInDisabled
            }
            throw AuthError.server(text)
        }
        guard (200..<300).contains(status) else { throw AuthError.http(status) }
        return try Self.session(from: data)
    }

    private func refresh(_ session: Session) async throws -> Session {
        let (data, status) = try await authPost(
            path: "/auth/v1/token?grant_type=refresh_token",
            body: ["refresh_token": session.refreshToken]
        )
        guard (200..<300).contains(status) else { throw AuthError.http(status) }
        return try Self.session(from: data)
    }

    private static func session(from data: Data) throws -> Session {
        let decoded = try JSONDecoder().decode(TokenResponse.self, from: data)
        return Session(
            accessToken: decoded.accessToken,
            refreshToken: decoded.refreshToken,
            userId: decoded.user.id,
            expiresAt: Date().addingTimeInterval(TimeInterval(decoded.expiresIn))
        )
    }

    private func authPost(path: String, body: [String: Any]) async throws -> (Data, Int) {
        guard let url = URL(string: "\(baseURL)\(path)") else { throw AuthError.server("invalid URL") }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        if let transportOverride {
            return try await transportOverride(request)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AuthError.server("invalid response") }
        return (data, http.statusCode)
    }
}

// MARK: - Keychain persistence for the session

private enum KeychainStore {
    private static let service = SupabaseAuthService.keychainService
    private static let account = SupabaseAuthService.keychainAccount

    static func save(_ session: SupabaseAuthService.Session) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func load() -> SupabaseAuthService.Session? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(SupabaseAuthService.Session.self, from: data)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
