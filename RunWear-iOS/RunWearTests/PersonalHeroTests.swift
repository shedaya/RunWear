import XCTest

/// RW-14 "Make it me" — pins for the pure personal-hero model layer
/// (PersonalHeroModels.swift, compiled into this target directly,
/// the established pattern). The combination-id vectors are the
/// backend contract's own worked examples
/// (rw14-backend-build-2026-10-31.md §2): if the outfit signature
/// hash or the cell assembly drifts from the server by one
/// character, personalized heroes silently never match — these
/// tests are the tripwire.
final class PersonalHeroTests: XCTestCase {

    // MARK: - Outfit signature hash (backend worked examples)

    func testHashVectorMildClear() {
        XCTAssertEqual(
            OutfitSignatureHash.hash(itemKeys: ["LONG_SLEEVE_LIGHT", "SHORTS"]),
            "726beba2"
        )
    }

    func testHashVectorRain() {
        XCTAssertEqual(
            OutfitSignatureHash.hash(itemKeys: ["LONG_SLEEVE_LIGHT", "RAIN_JACKET", "SHORTS", "BASEBALL_CAP"]),
            "9236f3e0"
        )
    }

    func testHashVectorWarm() {
        XCTAssertEqual(
            OutfitSignatureHash.hash(itemKeys: ["SHORT_SLEEVE", "SHORTS", "BASEBALL_CAP"]),
            "3c451777"
        )
    }

    func testHashIsOrderInsensitive() {
        // The signature sorts the item keys before hashing — the
        // same outfit listed in a different order is the same cell.
        XCTAssertEqual(
            OutfitSignatureHash.hash(itemKeys: ["SHORTS", "LONG_SLEEVE_LIGHT"]),
            OutfitSignatureHash.hash(itemKeys: ["LONG_SLEEVE_LIGHT", "SHORTS"])
        )
    }

    // MARK: - Combination id assembly (backend worked examples)

    func testCombinationIdMildClearDawn() {
        let combo = PersonalCombination(
            genderSegment: "MALE",
            weather: .CLEAR,
            temp: .MILD,
            time: .DAWN,
            outfitItems: ["LONG_SLEEVE_LIGHT", "SHORTS"]
        )
        XCTAssertEqual(combo.combinationId, "MALE_CLEAR_MILD_DAWN_726beba2")
    }

    func testCombinationIdRainMildDawn() {
        let combo = PersonalCombination(
            genderSegment: "MALE",
            weather: .RAIN,
            temp: .MILD,
            time: .DAWN,
            outfitItems: ["LONG_SLEEVE_LIGHT", "RAIN_JACKET", "SHORTS", "BASEBALL_CAP"]
        )
        XCTAssertEqual(combo.combinationId, "MALE_RAIN_MILD_DAWN_9236f3e0")
    }

    func testCombinationIdWarmClearDawn() {
        let combo = PersonalCombination(
            genderSegment: "MALE",
            weather: .CLEAR,
            temp: .WARM,
            time: .DAWN,
            outfitItems: ["SHORT_SLEEVE", "SHORTS", "BASEBALL_CAP"]
        )
        XCTAssertEqual(combo.combinationId, "MALE_CLEAR_WARM_DAWN_3c451777")
    }

    // MARK: - Gender segment (stated preference, never randomized)

    func testGenderSegmentMapping() {
        XCTAssertEqual(PersonalCombination.genderSegment(for: .male), "MALE")
        XCTAssertEqual(PersonalCombination.genderSegment(for: .female), "FEMALE")
        XCTAssertEqual(PersonalCombination.genderSegment(for: .unisex), "UNISEX")
    }

    // MARK: - Versioned cell cache (acceptance (16))

    private func lookup(
        cell: String,
        found: Bool = true,
        url: String? = "https://signed.example/hero.jpg?token=1",
        version: Int? = 1
    ) -> PersonalHeroLookup {
        PersonalHeroLookup(
            found: found,
            combinationId: cell,
            heroId: "hero-1",
            variant: 1,
            imageUrl: url,
            version: version,
            replenishmentQueued: 0
        )
    }

    func testCacheFirstResolveShowsFetchedURL() {
        var cache = PersonalCellCache()
        let now = Date()
        let resolution = cache.resolve(lookup: lookup(cell: "CELL_A"), now: now)
        XCTAssertEqual(resolution, .show("https://signed.example/hero.jpg?token=1"))
        XCTAssertEqual(cache.freshURL(for: "CELL_A", now: now), "https://signed.example/hero.jpg?token=1")
    }

    func testCacheSameVersionKeepsCachedURL() {
        // Same version, fresh entry: a re-signed URL for identical
        // content must NOT churn the on-screen image.
        var cache = PersonalCellCache()
        let t0 = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/v1-a"), now: t0)
        let resolution = cache.resolve(
            lookup: lookup(cell: "CELL_A", url: "https://signed.example/v1-b", version: 1),
            now: t0.addingTimeInterval(60)
        )
        XCTAssertEqual(resolution, .show("https://signed.example/v1-a"))
    }

    func testCacheVersionBumpSwapsImage() {
        // THE acceptance-(16) pin: when the server replaces a cell's
        // hero (version bump), the new image actually replaces the
        // old one on screen — a cache that keys on the cell alone
        // would serve the replaced photo forever.
        var cache = PersonalCellCache()
        let t0 = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/old", version: 1), now: t0)
        let resolution = cache.resolve(
            lookup: lookup(cell: "CELL_A", url: "https://signed.example/new", version: 2),
            now: t0.addingTimeInterval(60)
        )
        XCTAssertEqual(resolution, .show("https://signed.example/new"))
        XCTAssertEqual(cache.freshURL(for: "CELL_A", now: t0.addingTimeInterval(61)), "https://signed.example/new")
    }

    func testCacheExpiredURLRefreshesAtSameVersion() {
        // Signed URLs die after ~1 h server-side; past the client's
        // 50-minute lifetime the same version re-signs instead of
        // handing the view a dead URL.
        var cache = PersonalCellCache()
        let t0 = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/first", version: 1), now: t0)
        let later = t0.addingTimeInterval(PersonalCellCache.urlLifetime + 60)
        XCTAssertNil(cache.freshURL(for: "CELL_A", now: later))
        let resolution = cache.resolve(
            lookup: lookup(cell: "CELL_A", url: "https://signed.example/resigned", version: 1),
            now: later
        )
        XCTAssertEqual(resolution, .show("https://signed.example/resigned"))
    }

    func testCacheNotFoundAndCellIndependence() {
        var cache = PersonalCellCache()
        let now = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/a"), now: now)
        // A gate-rejected cell resolves to .none and leaves other
        // cells' entries untouched.
        let resolution = cache.resolve(lookup: lookup(cell: "CELL_B", found: false, url: nil, version: nil), now: now)
        XCTAssertEqual(resolution, .none)
        XCTAssertNil(cache.freshURL(for: "CELL_B", now: now))
        XCTAssertEqual(cache.freshURL(for: "CELL_A", now: now), "https://signed.example/a")
    }

    // MARK: - Upsell eligibility (spec §5)

    private func upsell(
        isSubscribed: Bool = false,
        used: Int = 5,
        allowance: Int = 5,
        exhaustedCell: String? = "CELL_NEW",
        currentCell: String = "CELL_NEW",
        lastDismissedAt: Date? = nil,
        dismissedCells: Set<String> = [],
        now: Date = Date(timeIntervalSince1970: 1_800_000_000)
    ) -> Bool {
        UpsellEligibility.shouldShow(
            isSubscribed: isSubscribed,
            freeGenerationsUsed: used,
            freeAllowance: allowance,
            exhaustedCell: exhaustedCell,
            currentCell: currentCell,
            lastDismissedAt: lastDismissedAt,
            dismissedCells: dismissedCells,
            now: now
        )
    }

    func testUpsellShowsAtSixthNewCell() {
        XCTAssertTrue(upsell())
    }

    func testUpsellHiddenForSubscribers() {
        XCTAssertFalse(upsell(isSubscribed: true))
    }

    func testUpsellHiddenWhileAllowanceRemains() {
        XCTAssertFalse(upsell(used: 4))
    }

    func testUpsellHiddenWhenCurrentCellAlreadyHasHero() {
        // The refusal names the exhausted cell; a different cell on
        // screen (one already holding a hero) never shows the card.
        XCTAssertFalse(upsell(currentCell: "CELL_OLD"))
        XCTAssertFalse(upsell(exhaustedCell: nil))
    }

    func testUpsellNeverTwiceForSameCell() {
        XCTAssertFalse(upsell(dismissedCells: ["CELL_NEW"]))
    }

    func testUpsellWeeklyRedisplayInterval() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let threeDaysAgo = now.addingTimeInterval(-3 * 24 * 3600)
        let eightDaysAgo = now.addingTimeInterval(-8 * 24 * 3600)
        // Dismissed (on another cell) 3 days ago: too soon anywhere.
        XCTAssertFalse(upsell(lastDismissedAt: threeDaysAgo, dismissedCells: ["CELL_OTHER"], now: now))
        // 8 days ago: the once-a-week window has passed.
        XCTAssertTrue(upsell(lastDismissedAt: eightDaysAgo, dismissedCells: ["CELL_OTHER"], now: now))
    }

    // MARK: - Photo quality verdict (spec §2.3, strings C3)

    func testPhotoVerdictCleanPhotoPasses() {
        XCTAssertNil(PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.5))
    }

    func testPhotoVerdictNoFace() {
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 0, largestFaceAreaRatio: 0, meanLuminance: 0.5),
            .faceUnclear
        )
    }

    func testPhotoVerdictMultiplePeopleWinsOverDark() {
        // Priority: count problems first — a group photo is wrong
        // however it is lit.
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 3, largestFaceAreaRatio: 0.2, meanLuminance: 0.05),
            .multiplePeople
        )
    }

    func testPhotoVerdictDarkBeforeTooClose() {
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.6, meanLuminance: 0.1),
            .tooDark
        )
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(faceCount: 1, largestFaceAreaRatio: 0.6, meanLuminance: 0.5),
            .tooClose
        )
    }

    func testPhotoRejectionStringsAreTheSpecDeck() {
        // Spec §7 C3a–d, verbatim — pinned so a copy edit in code
        // without a spec change fails loudly.
        XCTAssertEqual(PhotoRejection.faceUnclear.specString, "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow.")
        XCTAssertEqual(PhotoRejection.multiplePeople.specString, "This photo has more than one person in it. Pick one that's just you.")
        XCTAssertEqual(PhotoRejection.tooDark.specString, "This one is too dark to use. Try a photo in brighter light.")
        XCTAssertEqual(PhotoRejection.tooClose.specString, "This one is cropped too close. A head-and-shoulders or half-body photo works best.")
    }
}
