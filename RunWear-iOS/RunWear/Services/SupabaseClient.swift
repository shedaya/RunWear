import Foundation

/// Client for interacting with Supabase backend for hero images
actor SupabaseClient {
    static let shared = SupabaseClient()

    private let baseURL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI3NTIzMDYsImV4cCI6MjA1ODMyODMwNn0.gmEyMkZ27wNqIzAFLPvNH5kPDl3T6_uBfLWmXFSfEqY"

    private init() {}

    /// Fetches generated images matching a pattern using LIKE query
    /// - Parameter pattern: SQL LIKE pattern (e.g., "MALE_CLEAR_COLD_%")
    /// - Returns: Array of matching GeneratedImage objects
    func fetchGeneratedImages(pattern: String) async throws -> [GeneratedImage] {
        // URL-encode the pattern, ensuring % is encoded as %25
        let encodedPattern = pattern
            .addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? pattern

        let endpoint = "/rest/v1/generated_images"
        let query = "combination_id=like.\(encodedPattern)&select=id,combination_id,image_url,thumbnail_url,prompt"

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
        return images
    }

    /// Inserts a generation job for demand-driven replenishment
    /// - Parameters:
    ///   - combinationId: The combination ID for the image to generate
    ///   - prompt: The generation prompt
    ///   - status: Initial status (default: "QUEUED")
    func insertGenerationJob(
        combinationId: String,
        prompt: String,
        status: String = "QUEUED"
    ) async throws {
        let endpoint = "/rest/v1/generation_jobs"

        guard let url = URL(string: "\(baseURL)\(endpoint)") else {
            throw SupabaseError.invalidURL
        }

        let job = GenerationJob(
            combinationId: combinationId,
            prompt: prompt,
            status: status
        )

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.addValue(anonKey, forHTTPHeaderField: "apikey")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("return=minimal", forHTTPHeaderField: "Prefer")

        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(job)

        let (_, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SupabaseError.invalidResponse
        }

        guard httpResponse.statusCode == 201 || httpResponse.statusCode == 200 else {
            throw SupabaseError.httpError(httpResponse.statusCode)
        }
    }

    /// Counts existing variants for a combination prefix
    /// - Parameter prefix: Combination ID prefix (without variant number)
    /// - Returns: Count of existing variants
    func countExistingVariants(prefix: String) async throws -> Int {
        let pattern = "\(prefix)%"
        let images = try await fetchGeneratedImages(pattern: pattern)
        return images.count
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
