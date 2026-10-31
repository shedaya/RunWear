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

    // MARK: - RW-15 (1): request payload contract
    //
    // Build (9) sent register_references' paths under
    // `storage_paths`; the deployed function reads `paths` and
    // refused every registration with 400. These pins hold the
    // client to the DEPLOYED function's field names.

    private func jsonObject(_ body: [String: Any]) -> [String: Any] {
        let data = try! JSONSerialization.data(withJSONObject: body)
        return try! JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    func testRegisterReferencesBodyUsesPathsField() {
        let json = jsonObject(PersonalHeroRequests.registerReferencesBody(
            paths: ["u1/refs/ref-1.jpg", "u1/refs/ref-2.jpg", "u1/refs/ref-3.jpg"]
        ))
        XCTAssertEqual(json["action"] as? String, "register_references")
        XCTAssertEqual(json["paths"] as? [String],
                       ["u1/refs/ref-1.jpg", "u1/refs/ref-2.jpg", "u1/refs/ref-3.jpg"])
        // The RW-15 field name must never come back.
        XCTAssertNil(json["storage_paths"])
    }

    func testFlagHeroBodyShape() {
        let json = jsonObject(PersonalHeroRequests.flagHeroBody(
            heroId: "9fbeee01-6582-4616-8c79-b4088a443e5b",
            reason: PersonalHeroFlagReason.doesntLookLikeMe.code
        ))
        XCTAssertEqual(json["action"] as? String, "flag_hero")
        XCTAssertEqual(json["hero_id"] as? String, "9fbeee01-6582-4616-8c79-b4088a443e5b")
        XCTAssertEqual(json["reason"] as? String, "doesnt_look_like_me")
    }

    // MARK: - RW-15 (2): personal flag vocabulary + cache retirement

    func testPersonalFlagReasonCodesAndLabels() {
        // The headline control is spec §4 verbatim; the codes are
        // the backend flag_hero vocabulary (backend (c) §4).
        XCTAssertEqual(PersonalHeroFlagReason.doesntLookLikeMe.code, "doesnt_look_like_me")
        XCTAssertEqual(PersonalHeroFlagReason.doesntLookLikeMe.label, "This doesn't look like me")
        // The shared reasons mirror the pool menu's labels and the
        // pool vocabulary's codes (HeroFlagReason).
        XCTAssertEqual(PersonalHeroFlagReason.looksBroken.code, HeroFlagReason.looksBroken.code)
        XCTAssertEqual(PersonalHeroFlagReason.looksBroken.label, HeroFlagReason.looksBroken.label)
        XCTAssertEqual(PersonalHeroFlagReason.inappropriate.code, HeroFlagReason.inappropriate.code)
        XCTAssertEqual(PersonalHeroFlagReason.inappropriate.label, HeroFlagReason.inappropriate.label)
        XCTAssertEqual(PersonalHeroFlagReason.outfitMismatch.code, HeroFlagReason.outfitMismatch.code)
        XCTAssertEqual(PersonalHeroFlagReason.outfitMismatch.label, HeroFlagReason.outfitMismatch.label)
        // The personal reason heads the menu.
        XCTAssertEqual(PersonalHeroFlagReason.allCases.first, .doesntLookLikeMe)
    }

    func testCacheTracksServedHeroId() {
        var cache = PersonalCellCache()
        let now = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A"), now: now)
        XCTAssertEqual(cache.heroId(for: "CELL_A"), "hero-1")
        XCTAssertNil(cache.heroId(for: "CELL_B"))
    }

    func testCacheEvictsWhenNotFoundVersionMoved() {
        // A flag retires the hero server-side and bumps the cell
        // version; the next lookup answers found:false AT THE NEW
        // version. The cached entry must go, or freshURL would
        // keep serving the flagged photo until its URL aged out.
        var cache = PersonalCellCache()
        let t0 = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/flagged", version: 1), now: t0)
        let resolution = cache.resolve(
            lookup: lookup(cell: "CELL_A", found: false, url: nil, version: 2),
            now: t0.addingTimeInterval(60)
        )
        XCTAssertEqual(resolution, .none)
        XCTAssertNil(cache.freshURL(for: "CELL_A", now: t0.addingTimeInterval(61)))
        XCTAssertNil(cache.heroId(for: "CELL_A"))
    }

    func testCacheKeepsEntryWhenNotFoundVersionUnchanged() {
        // A not-found at the SAME version is not a retirement —
        // the entry survives (no spurious eviction mid-generation).
        var cache = PersonalCellCache()
        let t0 = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A", url: "https://signed.example/hero", version: 1), now: t0)
        let resolution = cache.resolve(
            lookup: lookup(cell: "CELL_A", found: false, url: nil, version: 1),
            now: t0.addingTimeInterval(60)
        )
        XCTAssertEqual(resolution, .none)
        XCTAssertEqual(cache.freshURL(for: "CELL_A", now: t0.addingTimeInterval(61)), "https://signed.example/hero")
    }

    func testCacheExplicitEvict() {
        // The store's post-flag refresh: eviction is unconditional.
        var cache = PersonalCellCache()
        let now = Date()
        _ = cache.resolve(lookup: lookup(cell: "CELL_A"), now: now)
        cache.evict(combinationId: "CELL_A")
        XCTAssertNil(cache.freshURL(for: "CELL_A", now: now))
        XCTAssertNil(cache.heroId(for: "CELL_A"))
    }

    // MARK: - RW-15 (3): commit failure surfacing

    func testErrorMappingKeepsServerMessageFrom400Body() {
        // The exact RW-15 refusal body: an unrecognized error name
        // survives verbatim as .server(message:).
        let body = Data(#"{"error":"paths must be an array of 1 to 3 storage paths"}"#.utf8)
        let mapped = PersonalHeroAPIError.mapped(data: body, status: 400)
        XCTAssertEqual(mapped, .server(message: "paths must be an array of 1 to 3 storage paths"))
    }

    func testCommitFailureShowsServerMessageBeneathGenericLine() {
        // A 400 with an error body renders that body text in the
        // failure state: the generic deck line stays primary, the
        // server's own words ride beneath it.
        let body = Data(#"{"error":"paths must be an array of 1 to 3 storage paths"}"#.utf8)
        let error = PersonalHeroAPIError.mapped(data: body, status: 400)
        let presentation = CommitFailurePresentation.forError(error)
        XCTAssertEqual(presentation.line, "Something went wrong uploading your photos. No look was used. Please try again.")
        XCTAssertEqual(presentation.detail, "paths must be an array of 1 to 3 storage paths")
    }

    func testCommitFailureAuthUnavailableKeepsItsOwnLine() {
        let presentation = CommitFailurePresentation.forError(PersonalHeroAPIError.authUnavailable)
        XCTAssertEqual(presentation.line, "We can't reach RunWear's server right now, so your photos can't be uploaded yet. Nothing was sent. You can try again later — your recommendations are unchanged.")
        XCTAssertNil(presentation.detail)
    }

    func testCommitFailureNamedErrorsAndTransportHaveNoDetail() {
        // Named backend conditions and non-API errors carry no
        // server prose: line only, exactly as build (9) behaved.
        XCTAssertNil(CommitFailurePresentation.forError(
            PersonalHeroAPIError.allowanceExhausted(freeGenerationsUsed: 5)).detail)
        XCTAssertNil(CommitFailurePresentation.forError(
            PersonalHeroAPIError.http(500)).detail)
        XCTAssertNil(CommitFailurePresentation.forError(
            URLError(.notConnectedToInternet)).detail)
        XCTAssertEqual(CommitFailurePresentation.forError(
            PersonalHeroAPIError.http(500)).line,
            "Something went wrong uploading your photos. No look was used. Please try again.")
    }

    // MARK: - RW-15 (4): retry commits the full picked set

    private func picked(_ states: [(Bool, String?)]) -> [PickedReferencePhoto] {
        states.map { PickedReferencePhoto(isAccepted: $0.0, jpegData: $0.1?.data(using: .utf8)) }
    }

    func testCommitSetSendsEveryAcceptedPhotoInOrder() {
        let set = picked([(true, "one"), (true, "two"), (true, "three")])
        let jpegs = ReferenceCommitSet.jpegPhotos(from: set)
        XCTAssertEqual(jpegs.map { String(data: $0, encoding: .utf8) }, ["one", "two", "three"])
    }

    func testCommitSetExcludesCheckingAndRejectedPhotos() {
        // A photo still checking, or rejected by the quality gate,
        // never uploads — only accepted photos with JPEG data go.
        let set = picked([(true, "one"), (false, nil), (false, "rejected-bytes"), (true, nil)])
        let jpegs = ReferenceCommitSet.jpegPhotos(from: set)
        XCTAssertEqual(jpegs.map { String(data: $0, encoding: .utf8) }, ["one"])
    }

    func testCommitSetRetryFromSamePickedSetIsIdentical() {
        // The retry guarantee: the derivation is a pure function of
        // the picked set, which a failed commit never mutates — a
        // second derivation (the retry) sends exactly what the
        // first attempt sent.
        let set = picked([(true, "one"), (true, "two"), (true, "three")])
        let first = ReferenceCommitSet.jpegPhotos(from: set)
        let retry = ReferenceCommitSet.jpegPhotos(from: set)
        XCTAssertEqual(first, retry)
        XCTAssertEqual(retry.count, 3)
    }

    func testCommitPositions() {
        XCTAssertEqual(ReferenceCommitSet.positions(photoCount: 3), [1, 2, 3])
        XCTAssertEqual(ReferenceCommitSet.positions(photoCount: 1), [1])
        XCTAssertEqual(ReferenceCommitSet.positions(photoCount: 0), [])
    }
}
