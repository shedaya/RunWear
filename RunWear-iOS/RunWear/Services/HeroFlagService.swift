import Foundation

/// Builds `hero_flags` insert rows from the hero currently on screen.
///
/// Mirror of Android's `HeroFlagPayload` (shared module, 2026-10-29) —
/// same mapping, same source markers, same retry policy; only the
/// platform value differs. The flag must identify the EXACT photo the
/// user saw:
/// - Pool photo → the `generated_images` row id is carried through,
///   and the combination recorded is the served row's own cell (which
///   can differ from the requested combination when the cascade fell
///   back).
/// - Fallback/curated photo (Unsplash) → no row id exists; the URL is
///   the identifier and `fallback` marks it as non-pool.
enum HeroFlagPayload {
    static let platformIOS = "iOS"
    static let sourcePool = "pool"
    static let sourceFallback = "fallback"

    /// Total send attempts per flag (initial try + one retry).
    static let maxSendAttempts = 2

    /// - Parameters:
    ///   - imageUrl: the hero URL on screen; nil/blank → no flag.
    ///   - imageRowId: pool row id when `imageUrl` came from the pool.
    ///   - servedCombinationId: the pool row's own combination_id,
    ///     when known.
    ///   - requestedCombinationId: the combination the app asked for;
    ///     used when there is no served row (fallback) or its cell
    ///     is unknown.
    ///   - createdAtIso: client timestamp, ISO-8601 (stored as
    ///     timestamptz).
    /// - Returns: the insert row, or nil when there is no photo to
    ///   flag.
    static func build(
        imageUrl: String?,
        imageRowId: String?,
        servedCombinationId: String?,
        requestedCombinationId: String?,
        reason: HeroFlagReason,
        appVersion: String?,
        createdAtIso: String
    ) -> HeroFlagInsert? {
        guard let url = nonBlank(imageUrl) else { return nil }
        let rowId = nonBlank(imageRowId)
        let fromPool = rowId != nil
        return HeroFlagInsert(
            imageRowId: rowId,
            imageUrl: url,
            combinationId: servedCombinationId ?? requestedCombinationId,
            imageSource: fromPool ? sourcePool : sourceFallback,
            reason: reason.code,
            platform: platformIOS,
            appVersion: nonBlank(appVersion),
            createdAt: createdAtIso
        )
    }

    /// Fire-and-forget submission: tries `sender` up to
    /// `maxSendAttempts` times and swallows every failure. A flag is
    /// best-effort user feedback — it must never surface an error or
    /// disturb the screen it was sent from.
    static func submit(
        _ payload: HeroFlagInsert,
        sender: (HeroFlagInsert) async throws -> Void
    ) async {
        for _ in 0..<maxSendAttempts {
            do {
                try await sender(payload)
                return
            } catch is CancellationError {
                return
            } catch {
                // Swallowed by design — see doc.
            }
        }
    }

    /// Returns the string unchanged when it has non-whitespace
    /// content, nil otherwise (Kotlin's `takeIf { it.isNotBlank() }`).
    private static func nonBlank(_ value: String?) -> String? {
        guard let value,
              !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return nil }
        return value
    }
}

/// Sends hero-photo flags to Supabase. The send runs on a detached
/// task; callers never await a result and failures never surface.
enum HeroFlagService {
    /// Flags the photo described by `target` with `reason`.
    static func flag(target: HeroFlagTarget, reason: HeroFlagReason) {
        guard let payload = HeroFlagPayload.build(
            imageUrl: target.imageUrl,
            imageRowId: target.imageRowId,
            servedCombinationId: target.servedCombinationId,
            requestedCombinationId: target.requestedCombinationId,
            reason: reason,
            appVersion: appVersion,
            createdAtIso: isoTimestamp(Date())
        ) else { return }

        Task {
            await HeroFlagPayload.submit(payload) { insert in
                try await SupabaseClient.shared.insertHeroFlag(insert)
            }
        }
    }

    static var appVersion: String? {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
    }

    static func isoTimestamp(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }
}
