import XCTest

/// Hero-photo flag payload fixtures (2026-10-29, iOS parity with
/// Android's HeroFlagPayloadTest in the shared module).
///
/// The flag is the pool's human quality signal; a flag that names
/// the wrong photo — or silently serializes to columns the table
/// rejects — is worse than no flag, so the mapping is pinned here:
/// pool identity and served-cell precedence, the fallback form,
/// the exact reason codes the `hero_flags` CHECK constraint
/// accepts, the snake_case insert contract, and the
/// fire-and-forget submit policy (send once, retry once, then
/// swallow).
final class HeroFlagPayloadTests: XCTestCase {

    private let createdAt = "2026-10-29T22:45:00.000Z"

    private func build(
        imageUrl: String? = "https://example.supabase.co/storage/v1/object/public/hero-images/MALE_CLEAR_MILD_DUSK_v5/abc",
        imageRowId: String? = "7367eebd-47ff-4338-a7a0-0a5a64e3d9b2",
        servedCombinationId: String? = "MALE_CLEAR_MILD_DUSK_v5",
        requestedCombinationId: String? = "MALE_CLEAR_MILD_DUSK",
        reason: HeroFlagReason = .looksBroken,
        appVersion: String? = "1.0"
    ) -> HeroFlagInsert? {
        HeroFlagPayload.build(
            imageUrl: imageUrl,
            imageRowId: imageRowId,
            servedCombinationId: servedCombinationId,
            requestedCombinationId: requestedCombinationId,
            reason: reason,
            appVersion: appVersion,
            createdAtIso: createdAt
        )
    }

    // MARK: - Payload mapping

    func testPoolPhotoCarriesRowIdAndServedCombination() {
        let payload = build()
        XCTAssertNotNil(payload)
        XCTAssertEqual(payload?.imageRowId, "7367eebd-47ff-4338-a7a0-0a5a64e3d9b2")
        // The served row's own cell wins over the requested one —
        // the cascade may have fallen back to a neighbouring cell,
        // and the flag must name the photo actually shown.
        XCTAssertEqual(payload?.combinationId, "MALE_CLEAR_MILD_DUSK_v5")
        XCTAssertEqual(payload?.imageSource, "pool")
        XCTAssertEqual(payload?.platform, "iOS")
        XCTAssertEqual(payload?.appVersion, "1.0")
        XCTAssertEqual(payload?.createdAt, createdAt)
    }

    func testFallbackPhotoHasNoRowIdAndIsMarkedNonPool() {
        let payload = build(
            imageUrl: "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop",
            imageRowId: nil,
            servedCombinationId: nil
        )
        XCTAssertNotNil(payload)
        XCTAssertNil(payload?.imageRowId)
        XCTAssertEqual(payload?.imageSource, "fallback")
        // No served row: the requested combination is the context.
        XCTAssertEqual(payload?.combinationId, "MALE_CLEAR_MILD_DUSK")
    }

    func testBlankRowIdIsTreatedAsNonPoolAndBlankAppVersionIsNil() {
        let payload = build(imageRowId: "  ", appVersion: "")
        XCTAssertNotNil(payload)
        XCTAssertNil(payload?.imageRowId)
        XCTAssertEqual(payload?.imageSource, "fallback")
        XCTAssertNil(payload?.appVersion)
    }

    func testNoPhotoOnScreenBuildsNoFlag() {
        XCTAssertNil(build(imageUrl: nil))
        XCTAssertNil(build(imageUrl: ""))
        XCTAssertNil(build(imageUrl: "   "))
    }

    func testReasonCodesMatchDatabaseCheckConstraint() {
        XCTAssertEqual(
            Set(HeroFlagReason.allCases.map(\.code)),
            ["looks_broken", "inappropriate", "outfit_mismatch"]
        )
    }

    func testInsertSerializesToSnakeCaseColumns() throws {
        let payload = try XCTUnwrap(build(reason: .inappropriate))
        let data = try JSONEncoder().encode(payload)
        let json = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )
        XCTAssertEqual(json["image_row_id"] as? String, "7367eebd-47ff-4338-a7a0-0a5a64e3d9b2")
        XCTAssertEqual(json["image_url"] as? String, payload.imageUrl)
        XCTAssertEqual(json["combination_id"] as? String, "MALE_CLEAR_MILD_DUSK_v5")
        XCTAssertEqual(json["image_source"] as? String, "pool")
        XCTAssertEqual(json["reason"] as? String, "inappropriate")
        XCTAssertEqual(json["platform"] as? String, "iOS")
        XCTAssertEqual(json["app_version"] as? String, "1.0")
        XCTAssertEqual(json["created_at"] as? String, createdAt)

        // A fallback flag omits the row id (lands as NULL) rather
        // than sending a placeholder the FK would reject.
        let fallback = try XCTUnwrap(build(imageRowId: nil, servedCombinationId: nil))
        let fallbackData = try JSONEncoder().encode(fallback)
        let fallbackJson = try XCTUnwrap(
            JSONSerialization.jsonObject(with: fallbackData) as? [String: Any]
        )
        XCTAssertNil(fallbackJson["image_row_id"])
        XCTAssertEqual(fallbackJson["image_source"] as? String, "fallback")
    }

    // MARK: - Identity threading

    func testHeroResultCarriesServedRowIdentity() {
        let image = GeneratedImage(
            id: "7367eebd-47ff-4338-a7a0-0a5a64e3d9b2",
            combinationId: "MALE_CLEAR_MILD_DUSK_v5",
            imageUrl: "https://example.supabase.co/hero.png",
            thumbnailUrl: nil,
            prompt: nil,
            reviewStatus: "approved"
        )
        let match = HeroImageResult.fromMatch(image: image, fallbackLevel: 2)
        XCTAssertEqual(match.imageRowId, image.id)
        XCTAssertEqual(match.combinationId, image.combinationId)

        let unsplash = HeroImageResult.fromUnsplash(url: "https://images.unsplash.com/photo-x")
        XCTAssertNil(unsplash.imageRowId)
        XCTAssertNil(unsplash.combinationId)
    }

    // MARK: - Submit policy

    private final class CallCounter {
        var count = 0
    }

    private struct SendFailure: Error {}

    func testSubmitSendsOnceWhenFirstAttemptSucceeds() async {
        let counter = CallCounter()
        let payload = build()!
        await HeroFlagPayload.submit(payload) { _ in
            counter.count += 1
        }
        XCTAssertEqual(counter.count, 1)
    }

    func testSubmitRetriesOnceAfterFailureThenSucceeds() async {
        let counter = CallCounter()
        let payload = build()!
        await HeroFlagPayload.submit(payload) { _ in
            counter.count += 1
            if counter.count == 1 { throw SendFailure() }
        }
        XCTAssertEqual(counter.count, 2)
    }

    func testSubmitSwallowsPersistentFailureAfterRetryBudget() async {
        let counter = CallCounter()
        let payload = build()!
        // Must return normally — a flag send can never throw into
        // the UI — after exactly MAX_SEND_ATTEMPTS calls.
        await HeroFlagPayload.submit(payload) { _ in
            counter.count += 1
            throw SendFailure()
        }
        XCTAssertEqual(counter.count, HeroFlagPayload.maxSendAttempts)
    }
}
