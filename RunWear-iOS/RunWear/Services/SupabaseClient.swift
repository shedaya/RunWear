import Foundation

/// Client for interacting with Supabase backend for hero images
actor SupabaseClient {
    static let shared = SupabaseClient()

    private let baseURL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

    private init() {}

    /// Fetches generated images matching a pattern using LIKE query
    /// - Parameter pattern: SQL LIKE pattern (e.g., "MALE_CLEAR_COLD_%")
    /// - Returns: Array of matching GeneratedImage objects
    func fetchGeneratedImages(pattern: String) async throws -> [GeneratedImage] {
        // URL-encode the pattern, ensuring % is encoded as %25
        let encodedPattern = pattern
            .addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? pattern

        let endpoint = "/rest/v1/generated_images"
        let query = "combination_id=like.\(encodedPattern)&select=id,combination_id,image_url,thumbnail_url,prompt,review_status"

        guard let url = URL(string: "\(baseURL)\(endpoint)?\(query)") else {
            throw SupabaseError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.addValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SupabaseError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            throw SupabaseError.httpError(httpResponse.statusCode)
        }

        let decoder = JSONDecoder()
        let images = try decoder.decode([GeneratedImage].self, from: data)
        // Review gate (mirrors Android's HeroPoolPolicy, 2026-10-27):
        // pending/rejected generations are never served.
        return images.filter { HeroPoolPolicy.isServable(reviewStatus: $0.reviewStatus) }
    }


    /// Inserts a user flag against a hero photo into `hero_flags`.
    /// The table's RLS grants the anon role INSERT only — flags are
    /// write-only from the client by design (review reads happen
    /// through service-role tooling, never from the app).
    /// - Parameter flag: The insert row built by `HeroFlagPayload`.
    func insertHeroFlag(_ flag: HeroFlagInsert) async throws {
        let endpoint = "/rest/v1/hero_flags"

        guard let url = URL(string: "\(baseURL)\(endpoint)") else {
            throw SupabaseError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("return=minimal", forHTTPHeaderField: "Prefer")

        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(flag)

        let (_, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SupabaseError.invalidResponse
        }

        guard httpResponse.statusCode == 201 || httpResponse.statusCode == 200 else {
            throw SupabaseError.httpError(httpResponse.statusCode)
        }
    }

}

/// Errors that can occur when interacting with Supabase
enum SupabaseError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(Int)
    case decodingError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid Supabase URL"
        case .invalidResponse:
            return "Invalid response from Supabase"
        case .httpError(let code):
            return "HTTP error: \(code)"
        case .decodingError(let error):
            return "Decoding error: \(error.localizedDescription)"
        }
    }
}
