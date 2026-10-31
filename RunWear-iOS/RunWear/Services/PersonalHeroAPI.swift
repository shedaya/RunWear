import Foundation

// MARK: - RW-14 backend client
//
// Thin REST client over the live RW-14 backend (Supabase project
// ebicqznlcjbqcukjfzcf): the four Edge Functions of
// rw14-backend-build-2026-10-31.md §2, the private `personal-heroes`
// storage bucket, and the user's own personal_profiles /
// personal_reference_photos rows via PostgREST (owner-SELECT RLS).
// Every call carries the Supabase Auth session JWT — the server
// derives the user from the token and accepts no user id from us.
actor PersonalHeroAPI {
    static let shared = PersonalHeroAPI()

    private let baseURL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"
    private let bucket = "personal-heroes"

    private let auth = SupabaseAuthService.shared

    private init() {}

    /// The current session's user id, if a session exists.
    func currentUserId() async -> String? {
        (try? await auth.currentSession())??.userId
    }

    // MARK: - Consent + references (request-personal-generation actions)

    func recordConsent(genderSegment: String) async throws {
        let body: [String: Any] = [
            "action": "record_consent",
            "consent_version": PersonalConsent.version,
            "attested_own_likeness": true,
            "attested_adult": true,
            "gender": genderSegment
        ]
        _ = try await functionPost("request-personal-generation", body: body)
    }

    /// Upload one reference photo to {uid}/refs/ref-{position}.jpg.
    /// Storage RLS permits owner writes under the caller's own prefix.
    func uploadReference(jpegData: Data, position: Int) async throws {
        let session = try await requireSession()
        let path = "\(session.userId)/refs/ref-\(position).jpg"
        guard let url = URL(string: "\(baseURL)/storage/v1/object/\(bucket)/\(path)") else {
            throw PersonalHeroAPIError.server(message: "invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        request.addValue("true", forHTTPHeaderField: "x-upsert")
        request.httpBody = jpegData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw PersonalHeroAPIError.server(message: "invalid response")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw PersonalHeroAPIError.server(
                message: "reference upload failed (\(http.statusCode)): \(String(data: data, encoding: .utf8) ?? "")"
            )
        }
    }

    func registerReferences(positions: [Int]) async throws {
        let session = try await requireSession()
        let paths = positions.map { "\(session.userId)/refs/ref-\($0).jpg" }
        // The deployed function reads `paths` — build (9) sent
        // `storage_paths` (a name taken from a backend report, not
        // the function) and every registration was refused with
        // 400 (RW-15). The body comes from the pinned builder in
        // PersonalHeroRequests; do not inline a variant of it.
        let body = PersonalHeroRequests.registerReferencesBody(paths: paths)
        _ = try await functionPost("request-personal-generation", body: body)
    }

    /// Flag one of the caller's own personalized heroes
    /// ("This doesn't look like me", RW-14 acceptance (19);
    /// backend (c) contract: rw14-backend-bc-2026-10-31.md §4).
    /// On success the hero stops serving immediately server-side
    /// and a regeneration is queued.
    func flagPersonalHero(heroId: String, reason: String) async throws {
        let body = PersonalHeroRequests.flagHeroBody(heroId: heroId, reason: reason)
        _ = try await functionPost("request-personal-generation", body: body)
    }

    // MARK: - Generation + serving

    func requestGeneration(combination: PersonalCombination) async throws -> PersonalGenerationResponse {
        let body: [String: Any] = [
            "combination_id": combination.combinationId,
            "outfit_items": combination.outfitItems
        ]
        let data = try await functionPost("request-personal-generation", body: body)
        return try JSONDecoder().decode(PersonalGenerationResponse.self, from: data)
    }

    func getPersonalHero(combinationId: String) async throws -> PersonalHeroLookup {
        let data = try await functionPost("get-personal-hero", body: ["combination_id": combinationId])
        return try JSONDecoder().decode(PersonalHeroLookup.self, from: data)
    }

    /// Subscriber matrix fill. The server gates this on its OWN
    /// entitled flag (set by the billing webhook, service-role only):
    /// a 403 not_entitled here means the webhook has not landed yet,
    /// and the caller retries — the client never writes entitled.
    func startPersonalFill() async throws -> PersonalFillResponse {
        let data = try await functionPost("start-personal-fill", body: [:])
        return try JSONDecoder().decode(PersonalFillResponse.self, from: data)
    }

    func deleteMyPhotos() async throws {
        _ = try await functionPost("delete-my-photos", body: [:])
    }

    // MARK: - Profile + reference reads (PostgREST, owner-SELECT)

    func fetchProfile() async throws -> PersonalProfileRow? {
        let session = try await requireSession()
        let path = "/rest/v1/personal_profiles?select=personalization_on,free_generations_used,entitled,gender&user_id=eq.\(session.userId)"
        let data = try await authedGet(path: path, session: session)
        let rows = try JSONDecoder().decode([PersonalProfileRow].self, from: data)
        return rows.first
    }

    func fetchReferencePaths() async throws -> [String] {
        let session = try await requireSession()
        let path = "/rest/v1/personal_reference_photos?select=storage_path&user_id=eq.\(session.userId)&order=position.asc"
        let data = try await authedGet(path: path, session: session)
        struct Row: Decodable { let storagePath: String
            enum CodingKeys: String, CodingKey { case storagePath = "storage_path" } }
        return try JSONDecoder().decode([Row].self, from: data).map { $0.storagePath }
    }

    /// A short-lived signed URL for one stored reference photo (for
    /// the "Your photos" screen).
    func signedReferenceURL(path: String) async throws -> URL? {
        let session = try await requireSession()
        guard let url = URL(string: "\(baseURL)/storage/v1/object/sign/\(bucket)/\(path)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["expiresIn": 3600])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
        struct SignResponse: Decodable { let signedURL: String }
        guard let decoded = try? JSONDecoder().decode(SignResponse.self, from: data) else { return nil }
        return URL(string: "\(baseURL)/storage/v1\(decoded.signedURL)")
    }

    // MARK: - Plumbing

    private func requireSession() async throws -> SupabaseAuthService.Session {
        do {
            return try await auth.ensureSession()
        } catch SupabaseAuthService.AuthError.anonymousSignInDisabled {
            throw PersonalHeroAPIError.authUnavailable
        } catch SupabaseAuthService.AuthError.noSession {
            throw PersonalHeroAPIError.unauthorized
        }
    }

    private func functionPost(_ slug: String, body: [String: Any]) async throws -> Data {
        let session = try await requireSession()
        guard let url = URL(string: "\(baseURL)/functions/v1/\(slug)") else {
            throw PersonalHeroAPIError.server(message: "invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw PersonalHeroAPIError.server(message: "invalid response")
        }
        if http.statusCode == 401 {
            await auth.clearLocalSession()
            throw PersonalHeroAPIError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            throw Self.mapError(data: data, status: http.statusCode)
        }
        return data
    }

    private func authedGet(path: String, session: SupabaseAuthService.Session) async throws -> Data {
        guard let url = URL(string: "\(baseURL)\(path)") else {
            throw PersonalHeroAPIError.server(message: "invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.addValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw PersonalHeroAPIError.server(message: "invalid response")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw Self.mapError(data: data, status: http.statusCode)
        }
        return data
    }

    /// Map the backend's {"error": "<name>", ...} bodies onto the
    /// typed error enum (backend §2.2/§2.3). The implementation
    /// lives in the model layer (PersonalHeroAPIError.mapped) so
    /// it is unit-tested; this is the delegation seam.
    private static func mapError(data: Data, status: Int) -> PersonalHeroAPIError {
        PersonalHeroAPIError.mapped(data: data, status: status)
    }
}
