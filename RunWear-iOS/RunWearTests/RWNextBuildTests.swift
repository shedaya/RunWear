import XCTest
import SwiftUI
import UIKit

/// RW-152 + RW-153 + RW-151 client (next build, Nov 10, 2026) —
/// the iOS lane's pins and render frames:
///
/// - RW-152 §1/§2: the Wear I've Run presence is ONE overlay
///   surface (mini dock ⇄ expanded panel, in-place morph); the
///   panel is content-sized with the viewport-minus-top-chrome
///   cap (WearIveRunOverlayPolicy).
/// - RW-152 §3 + RW-153: durable hero references, the one
///   resolver (HeroRefResolver), and the cover rule — a
///   chapter's cover is the NEWEST ENTRY WITH A LOADABLE
///   REFERENCE (Sol's RW-153 screenshot: New York's newest
///   entry settled empty-ref while older entries carried
///   photos, and the card rendered an empty banner).
/// - RW-152 §4 (J1/J2): past-entry deletes confirm, naming
///   place + date, with the device-only/irreversible copy;
///   today's entry is one-tap; a cover trash deletes the
///   representative entry only and the chapter recomputes.
/// - RW-151 §5 client: accompanied forecast cells on the wire
///   ("id|ITEM,ITEM,..."), bare core cells, the fillable-set
///   coverage arithmetic with the failed-settle preserved, and
///   the reconcile rule (pinned in Gen5FidelityTests' re-pins).
/// - Remainder: the glance-band "Change ›" takes the settled
///   primary ink (contrast fix).
///
/// Render half: production components from the dual-target
/// residence (WearIveRunMiniBar.swift), rendered with the
/// tree's ImageRenderer route into `render-artefacts/`. (The
/// hosted UIWindow capture route renders blank frames in this
/// CI environment — the standing hosted-capture baseline
/// failure set fails the same way — so it is not evidentiary
/// here.) The collection list and the convergence band live
/// in app-only files and cannot be rendered from this
/// target — their behaviour is pinned at the model layer here
/// and in the re-pinned suites; the lane report carries them
/// as UNPROVEN frames, not silent passes. The panel's
/// resolver-loaded thumbnail is likewise not frame-provable:
/// AsyncImage cannot resolve inside the synchronous
/// renderer, so the resolver is proven at the model layer
/// (classification, persistence, legacy recovery) and the
/// frames carry the panel's composition.
final class RWNextBuildTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
            .standardizedFileURL
        return try String(contentsOf: url, encoding: .utf8)
    }

    private var nyCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/New_York")!
        return cal
    }

    private func day(_ year: Int, _ month: Int, _ day: Int, hour: Int = 9) -> Date {
        nyCalendar.date(from: DateComponents(
            year: year, month: month, day: day, hour: hour))!
    }

    private func entry(
        place: String, dayStart: Date, hero: String,
        session: String, feels: Double = 50
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: place,
            feelsLikeF: feels, actualF: feels + 4, condition: "Clear sky",
            itemNames: ["Short-sleeve shirt", "Running shorts"],
            itemKeys: ["SHORT_SLEEVE", "SHORTS"],
            heroImageReference: hero, sessionID: session)
    }

    // MARK: - RW-152 §2: panel height == content height

    func testRW152_PanelHeightEqualsContentHeightAcrossViewportsAndScales() {
        // Viewport classes: short (667), standard (852), tall
        // (932); accessibility text scales up to 2.0. Whenever
        // the cap does not bind, the panel stands at EXACTLY the
        // content height — no reserved slack, no truncation.
        for viewport in [667.0, 852.0, 932.0] {
            for scale in [1.0, 1.35, 2.0] {
                for (narration, entryLines) in [(1, 1), (2, 2), (3, 4)] {
                    let content = WearIveRunOverlayPolicy.contentHeight(
                        narrationLineCount: narration,
                        entryLineCount: entryLines,
                        textScale: scale)
                    let cap = WearIveRunOverlayPolicy.expandedCap(
                        viewportHeight: viewport)
                    if content <= cap {
                        XCTAssertEqual(
                            WearIveRunOverlayPolicy.panelHeight(
                                contentHeight: content, viewportHeight: viewport),
                            content, accuracy: 0.5,
                            "viewport \(viewport) scale \(scale): panel must equal content height \(content)")
                        XCTAssertFalse(WearIveRunOverlayPolicy.entryRegionScrolls(
                            contentHeight: content, viewportHeight: viewport))
                    } else {
                        XCTAssertEqual(
                            WearIveRunOverlayPolicy.panelHeight(
                                contentHeight: content, viewportHeight: viewport),
                            cap, accuracy: 0.5,
                            "viewport \(viewport) scale \(scale): the cap binds at \(cap)")
                        XCTAssertTrue(WearIveRunOverlayPolicy.entryRegionScrolls(
                            contentHeight: content, viewportHeight: viewport))
                    }
                }
            }
        }
        // The default-type anatomy lands in the design's
        // ≈240–300 pt band once narration or the entry line
        // wraps (the one-line floor is 220 pt).
        XCTAssertEqual(WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 1, entryLineCount: 1, textScale: 1.0), 220, accuracy: 0.5)
        let wrapped = WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 2, entryLineCount: 3, textScale: 1.0)
        XCTAssertGreaterThanOrEqual(wrapped, 240)
        XCTAssertLessThanOrEqual(wrapped, 300)
        // The cap is the viewport minus the top chrome zone,
        // exactly.
        XCTAssertEqual(WearIveRunOverlayPolicy.expandedCap(viewportHeight: 852),
                       852 - WearIveRunOverlayPolicy.topChromeZoneHeight, accuracy: 0.5)
        // When the cap binds, the entry region's budget plus the
        // fixed chrome reconstructs the cap — header, narration,
        // and doorway are never what scrolls.
        let cap667 = WearIveRunOverlayPolicy.expandedCap(viewportHeight: 667)
        XCTAssertEqual(
            WearIveRunOverlayPolicy.entryRegionMaxHeight(viewportHeight: 667)
                + WearIveRunOverlayPolicy.nonEntryChromeHeight,
            cap667, accuracy: 0.5)
    }

    // MARK: - RW-153: cover selection

    func testRW153_CoverIsNewestEntryWithLoadableReference() {
        // Sol's RW-153 fixture, reproduced: New York, three
        // entries over Nov 8–10; the NEWEST settled with an
        // EMPTY reference, the older two carry photos.
        let newest = entry(place: "New York, NY", dayStart: day(2026, 11, 10),
                           hero: "", session: "s-ny-3", feels: 54)
        let middle = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                           hero: "https://cdn.example.invalid/ny-middle.jpg",
                           session: "s-ny-2", feels: 48)
        let oldest = entry(place: "New York, NY", dayStart: day(2026, 11, 8),
                           hero: "https://cdn.example.invalid/ny-oldest.jpg",
                           session: "s-ny-1", feels: 42)
        let chapters = WearIveRunCollection.chapters(
            from: [oldest, newest, middle], filter: .all, calendar: nyCalendar)
        XCTAssertEqual(chapters.count, 1)
        let chapter = chapters[0]
        // The cover is the middle entry — the newest WITH a
        // loadable reference — not the empty newest.
        XCTAssertEqual(chapter.cover.id, middle.id,
                       "RW-153: the cover skips the empty-ref newest entry")
        XCTAssertEqual(chapter.cover.heroImageReference,
                       "https://cdn.example.invalid/ny-middle.jpg")
        // Counts and spans still recompute from the FULL set.
        XCTAssertEqual(chapter.dayCount, 3)
        XCTAssertEqual(chapter.entries.count, 3)
        XCTAssertEqual(chapter.feelsLikeRangeF.min, 42)
        XCTAssertEqual(chapter.feelsLikeRangeF.max, 54)
    }

    func testRW153_CoverFallbacks() {
        // Newest carries a reference: the cover is the newest
        // (the pre-fix behaviour, preserved where it was right).
        let newest = entry(place: "Philadelphia, PA", dayStart: day(2026, 11, 10),
                           hero: "https://cdn.example.invalid/phl-new.jpg",
                           session: "s-phl-2")
        let older = entry(place: "Philadelphia, PA", dayStart: day(2026, 11, 9),
                          hero: "https://cdn.example.invalid/phl-old.jpg",
                          session: "s-phl-1")
        let chapter = WearIveRunCollection.chapters(
            from: [older, newest], filter: .all, calendar: nyCalendar)[0]
        XCTAssertEqual(chapter.cover.id, newest.id)
        // NO entry carries a reference: the cover falls back to
        // the newest entry (its honest placeholder), never to an
        // older empty entry and never to a fabricated photo.
        let emptyNew = entry(place: "Boston, MA", dayStart: day(2026, 11, 10),
                             hero: "", session: "s-bos-2")
        let emptyOld = entry(place: "Boston, MA", dayStart: day(2026, 11, 9),
                             hero: "", session: "s-bos-1")
        let emptyChapter = WearIveRunCollection.chapters(
            from: [emptyOld, emptyNew], filter: .all, calendar: nyCalendar)[0]
        XCTAssertEqual(emptyChapter.cover.id, emptyNew.id)
        XCTAssertFalse(HeroRefResolver.hasLoadableReference(emptyChapter.cover))
        // An expired SIGNED reference is still loadable — its
        // durable path re-signs — so a newest entry carrying one
        // keeps the cover.
        let signed = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/heroes/ny.jpg?token=expired"
        let signedNew = entry(place: "Chicago, IL", dayStart: day(2026, 11, 10),
                              hero: signed, session: "s-chi-2")
        let stableOld = entry(place: "Chicago, IL", dayStart: day(2026, 11, 9),
                              hero: "https://cdn.example.invalid/chi.jpg",
                              session: "s-chi-1")
        let signedChapter = WearIveRunCollection.chapters(
            from: [stableOld, signedNew], filter: .all, calendar: nyCalendar)[0]
        XCTAssertEqual(signedChapter.cover.id, signedNew.id,
                       "a signed-ref newest entry is loadable through its durable path")
    }

    // MARK: - RW-152 §3: the resolver

    func testRW152_ResolverClassificationAndRecovery() {
        let signed = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/heroes/a.jpg?token=abc"
        XCTAssertEqual(HeroRefResolver.storagePath(from: signed), "u1/heroes/a.jpg",
                       "an expired signed URL recovers by path extraction, query stripped")
        XCTAssertEqual(HeroRefResolver.durableReference(forDisplayReference: signed),
                       "u1/heroes/a.jpg")
        let publicURL = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/personal-heroes/u1/heroes/a.jpg"
        XCTAssertEqual(HeroRefResolver.storagePath(from: publicURL), "u1/heroes/a.jpg")
        // A bare persisted path is already durable.
        XCTAssertEqual(HeroRefResolver.storagePath(from: "u1/heroes/a.jpg"), "u1/heroes/a.jpg")
        XCTAssertEqual(HeroRefResolver.storagePath(from: "personal-heroes/u1/heroes/a.jpg"),
                       "u1/heroes/a.jpg")
        // Stable URLs pass through untouched and are not paths.
        let stable = "https://cdn.example.invalid/pool/mild_clear_male.jpg"
        XCTAssertNil(HeroRefResolver.storagePath(from: stable))
        XCTAssertEqual(HeroRefResolver.durableReference(forDisplayReference: stable), stable)
        // Empty stays honestly empty.
        XCTAssertNil(HeroRefResolver.storagePath(from: ""))
        XCTAssertEqual(HeroRefResolver.durableReference(forDisplayReference: ""), "")

        // Entry level: a personal hero settled from a signed
        // display URL persists the PATH as its durable identity.
        let personal = entry(place: "Miami, FL", dayStart: day(2026, 11, 10),
                             hero: signed, session: "s-mia-1")
        XCTAssertEqual(personal.heroDurableReference, "u1/heroes/a.jpg")
        XCTAssertEqual(HeroRefResolver.storagePath(for: personal), "u1/heroes/a.jpg")
        XCTAssertNil(HeroRefResolver.stableDisplayReference(for: personal),
                     "a storage path never renders synchronously — it re-signs")
        XCTAssertTrue(HeroRefResolver.hasLoadableReference(personal))
        // A pool hero's stable URL is its own durable identity
        // and renders synchronously.
        let pool = entry(place: "Miami, FL", dayStart: day(2026, 11, 10),
                         hero: stable, session: "s-mia-2")
        XCTAssertEqual(pool.heroDurableReference, stable)
        XCTAssertEqual(HeroRefResolver.stableDisplayReference(for: pool), stable)
        XCTAssertNil(HeroRefResolver.storagePath(for: pool))
    }

    func testRW152_DurableReferencePersistenceAndLegacyRecovery() throws {
        let signed = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u9/heroes/z.jpg?token=old"
        let personal = entry(place: "Miami, FL", dayStart: day(2026, 11, 10),
                             hero: signed, session: "s-mia-9")
        // The encoded payload carries the durable key.
        let data = try JSONEncoder().encode(personal)
        let object = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(object["heroDurableReference"] as? String, "u9/heroes/z.jpg")
        // Round-trip: the decoded entry keeps both references.
        let decoded = try JSONDecoder().decode(WearIveRunEntry.self, from: data)
        XCTAssertEqual(decoded.heroDurableReference, "u9/heroes/z.jpg")
        XCTAssertEqual(decoded.heroImageReference, signed)
        // Legacy payload (no durable key, written by the shipped
        // build): decoding DERIVES the durable path from the
        // signed display URL — the relaunch-after-an-hour
        // recovery for every entry already on users' devices.
        let legacy = """
        {"dayStart": 700000000, "placeName": "Miami, Florida",
         "feelsLikeF": 78, "actualF": 73, "condition": "Clear",
         "itemNames": [], "itemKeys": [],
         "heroImageReference": "\(signed)",
         "sessionID": "", "wasUpdatedInSession": false}
        """
        let legacyEntry = try JSONDecoder().decode(
            WearIveRunEntry.self, from: Data(legacy.utf8))
        XCTAssertEqual(legacyEntry.heroDurableReference, "u9/heroes/z.jpg")
        XCTAssertEqual(HeroRefResolver.storagePath(for: legacyEntry), "u9/heroes/z.jpg")
    }

    // MARK: - RW-152 §4 (J1/J2): delete policy + recompute

    func testRW152_DeletePolicy_PastConfirmsTodayOneTap() {
        let now = day(2026, 11, 10, hour: 12)
        let past = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                         hero: "", session: "s-del-1")
        XCTAssertTrue(WearIveRunDeletePolicy.requiresConfirmation(
            for: past, now: now, calendar: nyCalendar),
            "J1: a PAST entry requires the confirmation")
        let message = WearIveRunDeletePolicy.confirmationMessage(
            for: past, calendar: nyCalendar)
        XCTAssertTrue(message.contains("New York, NY"),
                      "J1: the confirmation NAMES the place")
        XCTAssertTrue(message.contains("Nov 9, 2026"),
                      "J1: the confirmation NAMES the date")
        XCTAssertTrue(message.contains("only on this device"),
                      "J1: the copy states entries live only on this device")
        XCTAssertTrue(message.contains("cannot be undone"),
                      "J1: the copy states deletion cannot be undone")
        // Today: one tap, no confirmation. A future (planned)
        // entry is not past either.
        let today = entry(place: "New York, NY", dayStart: day(2026, 11, 10, hour: 7),
                          hero: "", session: "s-del-2")
        XCTAssertFalse(WearIveRunDeletePolicy.requiresConfirmation(
            for: today, now: now, calendar: nyCalendar))
        let future = entry(place: "New York, NY", dayStart: day(2026, 11, 11),
                           hero: "", session: "s-del-3")
        XCTAssertFalse(WearIveRunDeletePolicy.requiresConfirmation(
            for: future, now: now, calendar: nyCalendar))
    }

    func testRW152_DeleteRecomputesChaptersAndCover() {
        // J2: the cover's trash deletes the REPRESENTATIVE entry
        // only — the chapter survives on its remaining entries,
        // its cover recomputes, and deleting the last entry
        // removes the chapter.
        let nyNew = entry(place: "New York, NY", dayStart: day(2026, 11, 10),
                          hero: "https://cdn.example.invalid/ny-new.jpg", session: "s-j2-2")
        let nyOld = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                          hero: "https://cdn.example.invalid/ny-old.jpg", session: "s-j2-1")
        let phl = entry(place: "Philadelphia, PA", dayStart: day(2026, 11, 10),
                        hero: "https://cdn.example.invalid/phl.jpg", session: "s-j2-3")
        var entries = [nyOld, phl, nyNew]
        var chapters = WearIveRunCollection.chapters(
            from: entries, filter: .all, calendar: nyCalendar)
        XCTAssertEqual(chapters.count, 2)
        let nyChapter = chapters.first { $0.placeName == "New York, NY" }!
        XCTAssertEqual(nyChapter.cover.id, nyNew.id)
        // Delete the representative entry (the cover's own id):
        entries = WearIveRunCollection.removing(entries, id: nyChapter.cover.id)
        chapters = WearIveRunCollection.chapters(
            from: entries, filter: .all, calendar: nyCalendar)
        let survived = chapters.first { $0.placeName == "New York, NY" }!
        XCTAssertEqual(survived.entries.count, 1)
        XCTAssertEqual(survived.cover.id, nyOld.id,
                       "the cover recomputes to the remaining entry")
        XCTAssertEqual(chapters.count, 2, "the other place is untouched (J2)")
        // Delete the chapter's last entry: the chapter is gone.
        entries = WearIveRunCollection.removing(entries, id: nyOld.id)
        chapters = WearIveRunCollection.chapters(
            from: entries, filter: .all, calendar: nyCalendar)
        XCTAssertEqual(chapters.map(\.placeName), ["Philadelphia, PA"])
    }

    // MARK: - RW-151 §5: wire composition + coverage arithmetic

    func testRW151_WireComposition_AccompaniedForecastBareCore() {
        let forecast = [
            NamedFillCell(combinationId: "MALE_CLEAR_COOL_DAWN_ab12",
                          outfitItems: ["LONG_SLEEVE_LIGHT", "PANTS"]),
            NamedFillCell(combinationId: "MALE_RAIN_MILD_DAY_cd34",
                          outfitItems: ["RAIN_JACKET", "SHORTS"]),
        ]
        XCTAssertEqual(forecast[0].wireForm,
                       "MALE_CLEAR_COOL_DAWN_ab12|LONG_SLEEVE_LIGHT,PANTS")
        // Forecast first (accompanied), core behind (bare); a
        // core naming of an already-named forecast id is dropped
        // — the accompanied form wins.
        let wire = FillSetComposition.wireCells(
            forecastCells: forecast,
            coreCells: ["MALE_CLEAR_COOL_DAWN_ab12", "CORE_A", "CORE_B"])
        XCTAssertEqual(wire, [
            "MALE_CLEAR_COOL_DAWN_ab12|LONG_SLEEVE_LIGHT,PANTS",
            "MALE_RAIN_MILD_DAY_cd34|RAIN_JACKET,SHORTS",
            "CORE_A", "CORE_B",
        ])
        // The id a wire entry names (the send-dedupe key form).
        XCTAssertEqual(FillSetComposition.cellID(ofWireEntry: wire[0]),
                       "MALE_CLEAR_COOL_DAWN_ab12")
        XCTAssertEqual(FillSetComposition.cellID(ofWireEntry: "CORE_A"), "CORE_A")
        // The bare target composition is unchanged (the
        // denominator of record).
        XCTAssertEqual(
            FillSetComposition.named(forecastCells: forecast.map(\.combinationId),
                                     coreCells: ["CORE_A"]),
            ["MALE_CLEAR_COOL_DAWN_ab12", "MALE_RAIN_MILD_DAY_cd34", "CORE_A"])
    }

    func testRW151_ForecastCellsThreadTheFramesOwnOutfits() {
        // The items on the wire are the frame's ALREADY-COMPUTED
        // outfit — threaded through PersonalCombination.make,
        // never recomputed. Two frames in one (day, daypart)
        // cluster name ONE cell (the cluster representative).
        let mild = OutfitRecommendationService.shared.getRecommendation(
            for: 62, rawTemperature: 62, isRaining: false, windSpeed: 5,
            humidity: 50, uvIndex: 3, precipitation: 0, hour: 6)
        let frames = [
            ForecastCellFrame(date: "2026-11-11", weatherCode: 0,
                              feelsLikeFahrenheit: 62, hour: 6, outfit: mild),
            ForecastCellFrame(date: "2026-11-11", weatherCode: 0,
                              feelsLikeFahrenheit: 62, hour: 7, outfit: mild),
            ForecastCellFrame(date: "2026-11-11", weatherCode: 0,
                              feelsLikeFahrenheit: 62, hour: 13, outfit: mild),
        ]
        let cells = ForecastPriorityCells.cells(
            genderPreference: .male, frames: frames)
        XCTAssertEqual(cells.count, 2, "dawn cluster + day cluster")
        XCTAssertEqual(cells.map(\.combinationId),
                       ForecastPriorityCells.ids(genderPreference: .male, frames: frames),
                       "cells() and ids() are the same selection")
        let expected = PersonalCombination.make(
            genderPreference: .male, weatherCode: 0,
            feelsLikeFahrenheit: 62, hour: 6, outfit: mild)
        XCTAssertEqual(cells[0].combinationId, expected.combinationId)
        XCTAssertEqual(cells[0].outfitItems, expected.outfitItems,
                       "the accompanied items are the frame's own outfit items")
        XCTAssertFalse(cells[0].outfitItems.isEmpty)
    }

    func testRW151_CoverageFillableAndFailedArithmetic() {
        // total = |Target ∩ Fillable|: the unfillable id "d"
        // cannot hold the bank open.
        let coverage = deriveBankCoverage(
            heroCells: ["a"],
            targetCells: ["a", "b", "c", "d"],
            fillableCells: ["a", "b", "c"],
            failedCells: ["b", "x"])
        XCTAssertEqual(coverage?.total, 3)
        XCTAssertEqual(coverage?.ready, 1, "ready = |banked ∩ Target|")
        XCTAssertEqual(coverage?.failed, 1, "failed counts only in-target failures")
        XCTAssertEqual(coverage?.outstanding, 1, "outstanding = total − ready − failed∩Target")
        XCTAssertEqual(coverage?.isComplete, false)
        // Everything banked or terminally failed: complete.
        let done = deriveBankCoverage(
            heroCells: ["a", "c"],
            targetCells: ["a", "b", "c"],
            fillableCells: ["a", "b", "c"],
            failedCells: ["b"])
        XCTAssertEqual(done?.outstanding, 0)
        XCTAssertEqual(done?.isComplete, true)
        // The pre-RW-151 two-argument call is exact (fillable =
        // target, failed = ∅) — the gen-3/gen-5 pins stand.
        let legacy = deriveBankCoverage(heroCells: ["a", "b"], targetCells: ["a", "b", "c"])
        XCTAssertEqual(legacy?.total, 3)
        XCTAssertEqual(legacy?.outstanding, 1)
        // No fillable target: no honest fraction.
        XCTAssertNil(deriveBankCoverage(
            heroCells: [], targetCells: ["a"], fillableCells: []))
    }

    // MARK: - Remainder: "Change ›" settled ink

    func testRW152_ChangeButtonTakesSettledPrimaryInk_SourcePin() throws {
        let chart = try sourceFile("../RunWear/Views/Components/DayChartView.swift")
        let change = try XCTUnwrap(chart.range(of: "Text(\"Change\")"))
        let buttonBlock = chart[change.lowerBound...].prefix(600)
        XCTAssertTrue(buttonBlock.contains("AppTheme.inkPrimary"),
                      "the glance-band Change control reads in the settled primary ink")
        XCTAssertFalse(buttonBlock.contains("AppTheme.pinePayload"),
                       "the low-contrast pine treatment is retired on this control")
    }

    // MARK: - RW-152 §2: the entry region is not greedy

    func testRW152_EntryRegionStandsAtTheRowsOwnHeight_SourcePin() throws {
        // The expanded frame of the first gate round caught the
        // panel's ScrollView inflating to whatever height it
        // was offered (a bare ScrollView is greedy) — which in
        // production would also feed the morph a false expanded
        // endpoint. The region stands at the row's measured
        // height, capped by the policy budget.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("entryRowHeight = height"),
                      "the entry region's height is the row's own measured height")
        XCTAssertTrue(bar.contains("WearIveRunOverlayPolicy.entryRegionMaxHeight"),
                      "the region is capped by the §2 budget")
        XCTAssertFalse(bar.contains(".frame(maxHeight: WearIveRunOverlayPolicy.entryRegionMaxHeight"),
                       "the greedy max-height form is retired")
    }

    // MARK: - Render frames (ImageRenderer)

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    private func imageHasContent(_ image: UIImage) -> Bool {
        guard let cg = image.cgImage else { return false }
        let w = 8, h = 8
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return Set(bytes).count > 2
    }

    @MainActor
    private func writeRender<Content: View>(
        _ content: Content, size: CGSize, name: String
    ) throws {
        let renderer = ImageRenderer(
            content: content.frame(width: size.width, height: size.height))
        renderer.scale = 3
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(
            at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW-NEXT wrote \(name)")
    }

    private func frameEntry(hero: String) -> WearIveRunEntry {
        entry(place: "Miami, FL", dayStart: Date(), hero: hero,
              session: "frame-session", feels: 71)
    }

    private func frameState(for entry: WearIveRunEntry) -> WearIveRunState {
        WearIveRunMiniBarModel.state(
            todaysEntry: entry, moment: entry.momentReading,
            wasUpdatedThisSession: entry.wasUpdatedInSession)
    }

    /// The overlay at REST (mini state) — and, by construction,
    /// the DISMISSED state: dismissal returns the surface to
    /// exactly this composition and the page never moved.
    @MainActor
    func testRW152Frame_OverlayRest_StandardAndShort() throws {
        for (size, suffix) in [(CGSize(width: 393, height: 852), "standard"),
                               (CGSize(width: 375, height: 667), "short")] {
            let entry = frameEntry(hero: "")
            let overlay = WearIveRunOverlayView(
                state: frameState(for: entry),
                entry: entry,
                formattedFeelsLike: "71°",
                cueCount: 0,
                viewportHeight: size.height,
                onOpenCollection: {}, onTrash: {})
            let composed = ZStack {
                AppTheme.background
                VStack {
                    Spacer()
                    overlay
                        .padding(.horizontal, DockResidence.horizontalInset)
                        .padding(.bottom, DockResidence.bottomInset)
                }
            }
            try writeRender(
                composed, size: size,
                name: "RW152-ios-overlay-rest-\(suffix).png")
        }
    }

    /// The EXPANDED panel state (the morph's target state),
    /// standard + short portrait viewports.
    @MainActor
    func testRW152Frame_ExpandedPanel_StandardAndShort() throws {
        for (viewport, suffix) in [(852.0, "standard"), (667.0, "short")] {
            let entry = frameEntry(hero: "")
            let panel = WearIveRunExpandedPanelView(
                state: frameState(for: entry),
                entry: entry,
                formattedFeelsLike: "71°",
                viewportHeight: viewport,
                onOpenCollection: {}, onCollapse: {}, onTrash: {})
            let size = CGSize(width: viewport == 852 ? 393 : 375, height: 380)
            let composed = ZStack {
                AppTheme.background
                VStack {
                    panel
                        .padding(.horizontal, DockResidence.horizontalInset)
                        .padding(.top, 24)
                    Spacer()
                }
            }
            try writeRender(
                composed, size: size,
                name: "RW152-ios-overlay-expanded-\(suffix).png")
        }
    }
}
