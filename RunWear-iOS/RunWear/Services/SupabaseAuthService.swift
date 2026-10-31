import Foundation
import Security

// MARK: - Supabase Auth session for RW-14
//
// RunWear has no accounts; the app's Supabase identity is an
// ANONYMOUS Auth user (auth.users row), created on first use and
// persisted in the Keychain across launches. The RW-14 backend
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

    /// Forget the local session (used when the server session is
    /// rejected). The server-side data lifecycle is delete-my-photos'
    /// job, not this one's.
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
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AuthError.server("invalid response") }
        return (data, http.statusCode)
    }
}

// MARK: - Keychain persistence for the session

private enum KeychainStore {
    private static let service = "com.uptimeventures.runwear.supabase"
    private static let account = "personal-hero-session"

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
