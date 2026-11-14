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
        // wraps (the one-line floor is 212 pt — RB-04: bottom padding 12, not 20).
        XCTAssertEqual(WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 1, entryLineCount: 1, textScale: 1.0), 212, accuracy: 0.5)
        let wrapped = WearIveRunOverlayPolicy.contentHeight(
            narrationLineCount: 2, entryLineCount: 3, textScale: 1.0)
        // RB-04: the wrapped anatomy is 239 pt with the 12 pt
        // bottom padding (was 247 with the retired 20).
        XCTAssertEqual(wrapped, 239, accuracy: 0.5)
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
        // The expanded frames of the first gate rounds caught
        // two greed traps in the panel: a bare ScrollView
        // inflating to the offered height, and a root
        // `frame(maxHeight:)` making the panel height-flexible
        // (expanding + centering its content, and feeding the
        // morph a false endpoint). The panel is content-sized
        // by construction: the chrome block and the row are
        // measured (both independent of the region's frame),
        // the region's height is COMPUTED from the policy, and
        // a ScrollView exists only in the cap-binding branch.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("entryRowHeight = height"),
                      "the row's height is measured on the row itself")
        XCTAssertTrue(bar.contains("chromeHeight = height"),
                      "the chrome block's height is measured on the block itself")
        XCTAssertTrue(bar.contains("WearIveRunOverlayPolicy.entryRegionHeight("),
                      "the region's height is the policy's computed budget")
        XCTAssertTrue(bar.contains("if entryRowHeight > regionBudget"),
                      "the ScrollView exists only when the cap binds")
        XCTAssertFalse(bar.contains(".frame(maxHeight: WearIveRunOverlayPolicy.expandedCap"),
                       "no root max-height frame makes the panel flexible")
    }

    func testRW152_EntryRegionHeightArithmetic() {
        // Row stands whole whenever it fits the cap.
        XCTAssertEqual(WearIveRunOverlayPolicy.entryRegionHeight(
            cap: 736, chromeHeight: 76, rowHeight: 56), 56, accuracy: 0.5)
        // A wrapped row at the short cap still stands whole.
        XCTAssertEqual(WearIveRunOverlayPolicy.entryRegionHeight(
            cap: 551, chromeHeight: 76, rowHeight: 120), 120, accuracy: 0.5)
        // The cap binds: budget = cap − chrome − doorway
        // allowance, and the region scrolls at that height.
        XCTAssertEqual(WearIveRunOverlayPolicy.entryRegionHeight(
            cap: 274, chromeHeight: 76, rowHeight: 120), 120, accuracy: 0.5)
        XCTAssertEqual(WearIveRunOverlayPolicy.entryRegionHeight(
            cap: 274, chromeHeight: 160, rowHeight: 120), 56, accuracy: 0.5,
            "extreme chrome at a landscape cap: the region floors at the thumbnail cell and scrolls")
        // Panel total under the binding case never exceeds
        // the cap: chrome + region + allowance == cap.
        let chrome: CGFloat = 160
        let region = WearIveRunOverlayPolicy.entryRegionHeight(
            cap: 274, chromeHeight: chrome, rowHeight: 120)
        XCTAssertLessThanOrEqual(
            chrome + region + WearIveRunOverlayPolicy.doorwayAllowance, 274 + 56,
            "the doorway stays inside the cap (the 56 floor can exceed it only when chrome alone nearly fills the cap)")
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

    // MARK: - RW-158: past-entry tap → share sheet (user ruling, Nov 10, 2026)

    func testRW158_TapRouting_PastShareTodayUnchanged() {
        // The ruling (user, Nov 10, 2026): "pressing on the
        // past run should simply open the share interface that
        // we've designed." The routing is a pure decision:
        // past entry → .shareSheet on BOTH entry surfaces;
        // today/future → the surface's unchanged route;
        // chapter card → .drillDown; sheet preview → .fullPhoto.
        let now = day(2026, 11, 10, hour: 12)
        let past = entry(place: "New York, NY", dayStart: day(2026, 11, 9, hour: 23),
                         hero: "", session: "s-158-1")
        let today = entry(place: "New York, NY", dayStart: day(2026, 11, 10, hour: 0),
                          hero: "", session: "s-158-2")
        let future = entry(place: "New York, NY", dayStart: day(2026, 11, 11),
                           hero: "", session: "s-158-3")
        XCTAssertTrue(WearIveRunTapPolicy.isPast(past, now: now, calendar: nyCalendar))
        XCTAssertFalse(WearIveRunTapPolicy.isPast(today, now: now, calendar: nyCalendar),
                       "an entry from the first minute of today is not past")
        XCTAssertFalse(WearIveRunTapPolicy.isPast(future, now: now, calendar: nyCalendar))
        // Chapter day/entry rows.
        XCTAssertEqual(WearIveRunTapPolicy.chapterEntryDestination(
            for: past, now: now, calendar: nyCalendar), .shareSheet)
        XCTAssertEqual(WearIveRunTapPolicy.chapterEntryDestination(
            for: today, now: now, calendar: nyCalendar), .dayView,
            "today's entry in the collection is unchanged (ruling boundary)")
        XCTAssertEqual(WearIveRunTapPolicy.chapterEntryDestination(
            for: future, now: now, calendar: nyCalendar), .dayView)
        // The expanded panel's entry row (footer affordance).
        XCTAssertEqual(WearIveRunTapPolicy.expandedPanelEntryDestination(
            for: past, now: now, calendar: nyCalendar), .shareSheet)
        XCTAssertEqual(WearIveRunTapPolicy.expandedPanelEntryDestination(
            for: today, now: now, calendar: nyCalendar), .openCollection,
            "the panel row's unchanged route is the collection")
        // The chapter card itself keeps its drill-down; the
        // sheet's poster preview keeps the full photo.
        XCTAssertEqual(WearIveRunTapPolicy.chapterCardDestination, .drillDown)
        XCTAssertEqual(WearIveRunTapPolicy.sharePreviewDestination, .fullPhoto)
    }

    func testRW158_OldEntryWithExpiredSignedURL_SheetRendersViaResolver() throws {
        // The RW-152 §3 durable-ref path, exercised for RW-158:
        // an OLD entry (Nov 9, viewed Nov 10) whose stored
        // display reference is an EXPIRED signed URL. Its tap
        // routes to the share sheet, and the sheet's poster
        // base can only load through the resolver — the stable
        // (synchronous) reference is nil, the durable storage
        // path is what re-signs.
        let now = day(2026, 11, 10, hour: 12)
        let expired = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/heroes/ny-nov9.jpg?token=expired"
        let old = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                        hero: expired, session: "s-158-old")
        XCTAssertEqual(WearIveRunTapPolicy.chapterEntryDestination(
            for: old, now: now, calendar: nyCalendar), .shareSheet)
        XCTAssertEqual(old.heroDurableReference, "u1/heroes/ny-nov9.jpg",
                       "the durable identity is the storage path, not the dead URL")
        XCTAssertEqual(HeroRefResolver.storagePath(for: old), "u1/heroes/ny-nov9.jpg")
        XCTAssertNil(HeroRefResolver.stableDisplayReference(for: old),
                     "an expired signed URL never renders synchronously — it re-signs")
        XCTAssertTrue(HeroRefResolver.hasLoadableReference(old),
                      "the entry is loadable through the resolver path")
        // The share host's poster pipeline loads through the
        // one resolver — pinned in the host's own section, so
        // the direct-entry sheet (not only the day view) is
        // covered.
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        let hostStart = try XCTUnwrap(collection.range(of: "struct WearIveRunShareStudioHost"))
        let hostEnd = try XCTUnwrap(collection.range(of: "// MARK: - Share export context"))
        let host = collection[hostStart.lowerBound..<hostEnd.lowerBound]
        // RW-160 RE-PIN (device verdict, Nov 10, 2026): the
        // host's load is the RECOVERING form of the same
        // resolver call (it carries the gender segment) — the
        // RW-152 §3 property (poster base via HeroRefRuntime)
        // stands, extended by the bank re-link.
        XCTAssertTrue(host.contains("HeroRefRuntime.loadableURLString("),
                      "the sheet's poster base resolves via HeroRefRuntime (RW-152 §3)")
        XCTAssertTrue(host.contains("for: entry, gender: genderPreference"),
                      "the sheet's poster base uses the recovering resolution (RW-160)")
        XCTAssertTrue(host.contains("ShareStudioView("),
                      "the host presents the designed Share Studio sheet")
    }

    func testRW158_RoutingWiring_SourcePins() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        // Chapter rows route through the policy and present
        // the host sheet by item; the day view stays for the
        // unchanged route, and the chapter card's NavigationLink
        // into the chapter (drill-down) stands.
        XCTAssertTrue(collection.contains("WearIveRunTapPolicy.chapterEntryDestination(for: entry) == .shareSheet"))
        XCTAssertTrue(collection.contains(".sheet(item: $shareEntry)"))
        XCTAssertTrue(collection.contains("WearIveRunDayView("),
                      "today/future rows keep the day view")
        XCTAssertTrue(collection.contains("WearIveRunChapterView("),
                      "the chapter card keeps its drill-down (RW-158 boundary)")
        // The Studio's poster preview opens the full photo.
        let studioStart = try XCTUnwrap(collection.range(of: "struct ShareStudioView"))
        let studio = collection[studioStart.lowerBound...]
        XCTAssertTrue(studio.contains("WearIveRunTapPolicy.sharePreviewDestination == .fullPhoto"))
        XCTAssertTrue(studio.contains("HeroPhotoViewer("),
                      "the full-screen photo stays reachable from the sheet's preview")
        // The expanded panel routes through the policy and the
        // host presents the sheet from WeatherOutfitView.
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        XCTAssertTrue(bar.contains("WearIveRunTapPolicy.expandedPanelEntryDestination(for: shownEntry) == .shareSheet"))
        XCTAssertTrue(bar.contains("var onOpenShareEntry: ((WearIveRunEntry) -> Void)?"))
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // RW-161 RE-PIN (device verdict, Nov 10, 2026): the
        // panel's share route now funnels through the ONE sheet
        // owner — the tap stashes the entry and claims the
        // token (requestWearShare), and the sheet presents
        // through the owner-gated item binding. The RW-158
        // routing behaviour (past entry → this sheet) is
        // unchanged; only the presentation plumbing moved.
        XCTAssertTrue(host.contains("onOpenShareEntry: { requestWearShare($0) }"))
        XCTAssertTrue(host.contains(".sheet(item: wearShareSheetItem)"))
        XCTAssertTrue(host.contains("WearIveRunShareStudioHost("))
    }

    // MARK: - Rename ruling (user, Nov 10, 2026): "Morning Poster" → "Postcard"

    func testPostcardRename_NoMorningPosterUserFacingStrings() throws {
        // The format pair of record is Postcard + Kit Card.
        // Every user-facing "Morning Poster" string is renamed;
        // code identifiers (MorningPosterView,
        // MorningPosterMetrics, ShareCardKind.poster) are NOT
        // strings and are intentionally left — this pin guards
        // the STRING sites only.
        XCTAssertEqual(ShareCardCopy.progressLabel(for: .poster), "Making your Postcard…")
        XCTAssertEqual(ShareCardCopy.failureLabel(for: .poster), "Your Postcard couldn't be made.")
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("name: \"Postcard\""),
                      "the Studio's format card is labeled Postcard")
        XCTAssertTrue(collection.contains("Text(\"Postcard or Kit Card, with the day's caption.\")"),
                      "the day view's share explainer names the pair of record")
        XCTAssertFalse(collection.contains("name: \"Morning Poster\""))
        XCTAssertFalse(collection.contains("Text(\"Morning Poster"))
        let store = try sourceFile("../RunWear/Services/ShareCardRenderStore.swift")
        XCTAssertFalse(store.contains("Morning Poster…"))
        XCTAssertFalse(store.contains("Morning Poster couldn't"))
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

    // MARK: - Build 28 fix lane (device verdict, Nov 10, 2026)

    /// RW-159 (F1): the REAL failing shape — not the RW-158
    /// fixture's expired signed URL, but the EMPTY reference
    /// build 27's New York entries actually carry. The chapter
    /// (every NY entry empty) falls to the placeholder; the
    /// bank re-link then recovers the newest entry's OWN hero
    /// from the bank, and once persisted the cover rule picks
    /// it — the chapter renders its photo.
    func testRW159_EmptyRefEntryRecoversFromBank_RealShape() {
        // New York, Nov 9: settled at 9 a.m. (DAWN), feels 58
        // (MILD), Clear — with NO hero reference at all.
        let nyNov9 = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                           hero: "", session: "s-ny-nov9", feels: 58)
        XCTAssertEqual(nyNov9.heroImageReference, "")
        XCTAssertEqual(nyNov9.heroDurableReference, "",
                       "an empty display reference derives an empty durable one — the RW-152 recovery had nothing to derive from")
        XCTAssertFalse(HeroRefResolver.hasLoadableReference(nyNov9))
        // The pre-recovery chapter: no loadable entry, so the
        // cover IS this entry and its placeholder (the device
        // screenshot's empty New York banner).
        let chapterBefore = WearIveRunCollection.chapters(
            from: [nyNov9], filter: .all, calendar: nyCalendar)[0]
        XCTAssertEqual(chapterBefore.cover.id, nyNov9.id)
        XCTAssertFalse(HeroRefResolver.hasLoadableReference(chapterBefore.cover))
        // His bank (read live in the lane, Nov 10, 2026): heroes
        // keyed by combination_id with storage paths of the form
        // {uid}/heroes/{combination_id}/v{n}.png. The entry's
        // candidate set leads with its exact reconstruction.
        let hash = OutfitSignatureHash.hash(itemKeys: nyNov9.itemKeys)
        let exactCell = "MALE_CLEAR_MILD_DAWN_\(hash)"
        let candidates = HeroRecovery.candidateCombinationIds(
            for: nyNov9, genderSegment: "MALE", calendar: nyCalendar)
        XCTAssertEqual(candidates.first, exactCell,
                       "the exact reconstruction (gender/weather/bracket/time/hash, all from persisted fields) leads the candidate set")
        let bankPath = "63e82e02-7ef5-4b3a-9bf1-16fa6f3e9f58/heroes/\(exactCell)/v1.png"
        let bank: [String: String] = [exactCell: bankPath]
        let recovered = HeroRecovery.recoveredDurableReference(
            for: nyNov9, genderSegment: "MALE", bankPaths: bank, calendar: nyCalendar)
        XCTAssertEqual(recovered, bankPath,
                       "the empty-ref entry re-links to its OWN banked hero")
        // Persisted (the model's heal write, exercised here at
        // the value layer): the entry becomes loadable through
        // its durable path, and the cover rule picks it.
        let healed = nyNov9.withHeroDurableReference(bankPath)
        XCTAssertTrue(HeroRefResolver.hasLoadableReference(healed))
        XCTAssertEqual(HeroRefResolver.storagePath(for: healed), bankPath)
        XCTAssertNil(HeroRefResolver.stableDisplayReference(for: healed),
                     "the recovered path re-signs at render, never renders raw")
        let chapterAfter = WearIveRunCollection.chapters(
            from: [healed], filter: .all, calendar: nyCalendar)[0]
        XCTAssertTrue(HeroRefResolver.hasLoadableReference(chapterAfter.cover))
        // The Philadelphia contrast, in the same terms: an entry
        // that settled WITH a signed URL was never broken — its
        // durable path derived at decode and the plain resolver
        // serves it; recovery never touches it.
        let signed = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/heroes/phl.jpg?token=abc"
        let phl = entry(place: "Philadelphia, PA", dayStart: day(2026, 11, 9),
                        hero: signed, session: "s-phl", feels: 58)
        XCTAssertEqual(phl.heroDurableReference, "u1/heroes/phl.jpg")
        XCTAssertNil(HeroRecovery.recoveredDurableReference(
            for: phl, genderSegment: "MALE", bankPaths: bank, calendar: nyCalendar),
            "an entry carrying its own reference is never re-linked")
    }

    /// RW-159: recovery is not a New-York special case and never
    /// fabricates: a bank missing the entry's own bracket/outfit
    /// yields nothing (the honest placeholder stands), a
    /// same-outfit hero at a DIFFERENT bracket is a different
    /// look and is not served, and an entry with no outfit keys
    /// has no signature to look up.
    func testRW159_RecoveryNeverFabricates() {
        let nyNov9 = entry(place: "New York, NY", dayStart: day(2026, 11, 9),
                           hero: "", session: "s-ny-nov9b", feels: 58)
        let hash = OutfitSignatureHash.hash(itemKeys: nyNov9.itemKeys)
        // Same outfit, WARM bracket (the entry is MILD): not its photo.
        let wrongBracket: [String: String] = [
            "MALE_CLEAR_WARM_DAWN_\(hash)": "u/heroes/warm.png"]
        XCTAssertNil(HeroRecovery.recoveredDurableReference(
            for: nyNov9, genderSegment: "MALE", bankPaths: wrongBracket, calendar: nyCalendar),
            "a different bracket's look is never substituted")
        // Empty bank: nothing recovers.
        XCTAssertNil(HeroRecovery.recoveredDurableReference(
            for: nyNov9, genderSegment: "MALE", bankPaths: [:], calendar: nyCalendar))
        // A weather-boundary hit still recovers: the label
        // "Clear" stands next to CLOUDY in the candidate order.
        let cloudyBank: [String: String] = [
            "MALE_CLOUDY_MILD_DAWN_\(hash)": "u/heroes/cloudy.png"]
        XCTAssertEqual(HeroRecovery.recoveredDurableReference(
            for: nyNov9, genderSegment: "MALE", bankPaths: cloudyBank, calendar: nyCalendar),
            "u/heroes/cloudy.png")
        // No outfit keys → no candidates → no recovery.
        let keyless = WearIveRunEntry(
            dayStart: day(2026, 11, 9), placeName: "New York, NY",
            feelsLikeF: 58, actualF: 60, condition: "Clear",
            itemNames: [], itemKeys: [], heroImageReference: "", sessionID: "s-keyless")
        XCTAssertTrue(HeroRecovery.candidateCombinationIds(
            for: keyless, genderSegment: "MALE", calendar: nyCalendar).isEmpty)
        XCTAssertNil(HeroRecovery.recoveredDurableReference(
            for: keyless, genderSegment: "MALE", bankPaths: cloudyBank, calendar: nyCalendar))
    }

    /// RW-160 (F2): the Postcard pipeline consumes the SAME
    /// recovering resolution as the cover — build 27's pipeline
    /// took the plain resolver, whose nil for an empty-ref
    /// entry failed the poster slot while the Kit Card (which
    /// never touches the hero) rendered fully.
    func testRW160_PosterPipelineRecoversEmptyRefEntry_SourcePins() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        let hostStart = try XCTUnwrap(collection.range(of: "struct WearIveRunShareStudioHost"))
        let hostEnd = try XCTUnwrap(collection.range(of: "// MARK: - Share export context"))
        let host = collection[hostStart.lowerBound..<hostEnd.lowerBound]
        XCTAssertTrue(host.contains("for: entry, gender: genderPreference"),
                      "the poster base loads through the RECOVERING resolver (RW-160)")
        XCTAssertTrue(host.contains("onHeroRecovered"),
                      "a recovery in the poster pipeline is persisted on the entry")
        // The runtime's recovering overload falls back to the
        // bank re-link only for entries with no loadable ref.
        let api = try sourceFile("../RunWear/Services/PersonalHeroAPI.swift")
        XCTAssertTrue(api.contains("HeroRecovery.recoveredDurableReference"))
        XCTAssertTrue(api.contains("fetchBankHeroPaths"))
    }

    /// RW-161 (F3): one presentation owner — a second sheet
    /// request queues while one stands, activates on release,
    /// and only the owner may present.
    func testRW161_SheetArbiter_SingleOwnerQueue() {
        var arbiter = HomeSheetArbiter.claim(owner: nil, queue: [], requested: .wearShare)
        XCTAssertEqual(arbiter.owner, .wearShare)
        XCTAssertTrue(HomeSheetArbiter.mayPresent(owner: arbiter.owner, route: .wearShare))
        // While the share sheet stands, Settings and Location
        // requests queue — neither may present (the build-27
        // swipe-cycling stack).
        arbiter = HomeSheetArbiter.claim(owner: arbiter.owner, queue: arbiter.queue, requested: .settings)
        arbiter = HomeSheetArbiter.claim(owner: arbiter.owner, queue: arbiter.queue, requested: .locationPicker)
        XCTAssertEqual(arbiter.owner, .wearShare)
        XCTAssertEqual(arbiter.queue, [.settings, .locationPicker])
        XCTAssertFalse(HomeSheetArbiter.mayPresent(owner: arbiter.owner, route: .settings))
        XCTAssertFalse(HomeSheetArbiter.mayPresent(owner: arbiter.owner, route: .locationPicker))
        // A duplicate request does not double-queue.
        arbiter = HomeSheetArbiter.claim(owner: arbiter.owner, queue: arbiter.queue, requested: .settings)
        XCTAssertEqual(arbiter.queue, [.settings, .locationPicker])
        // Release hands the token to the oldest queued route.
        arbiter = HomeSheetArbiter.release(owner: arbiter.owner, queue: arbiter.queue, released: .wearShare)
        XCTAssertEqual(arbiter.owner, .settings)
        XCTAssertEqual(arbiter.queue, [.locationPicker])
        // Releasing a queued (non-owner) route just dequeues it.
        arbiter = HomeSheetArbiter.release(owner: arbiter.owner, queue: arbiter.queue, released: .locationPicker)
        XCTAssertEqual(arbiter.owner, .settings)
        XCTAssertTrue(arbiter.queue.isEmpty)
        arbiter = HomeSheetArbiter.release(owner: arbiter.owner, queue: arbiter.queue, released: .settings)
        XCTAssertNil(arbiter.owner)
    }

    /// RW-161 (F3): the host's wiring — every Home sheet is
    /// owner-gated and the transient layer defers under a sheet.
    func testRW161_HostWiring_SourcePins() throws {
        let host = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(host.contains("ownedBinding(.settings, $showSettings)"))
        XCTAssertTrue(host.contains("ownedBinding(.locationPicker, $showLocationPicker)"))
        XCTAssertTrue(host.contains("ownedBinding(.collection, $showCollection)"))
        XCTAssertTrue(host.contains(".sheet(item: wearShareSheetItem)"),
                      "the RW-158 share sheet presents through the owner-gated item binding")
        XCTAssertTrue(host.contains("showFlagThanks && !anyHomeSheetPresented"),
                      "the flag toast defers while a sheet owns the screen (RW-161)")
        XCTAssertTrue(host.contains("currentCellLookIsFailed && !anyHomeSheetPresented"),
                      "the failed-look line defers while a sheet owns the screen (RW-161)")
    }

    /// RW-162 G1: the expand tap fires the expansion
    /// synchronously (never from an animation completion), and
    /// the overlay measures the panel before driving its frame.
    func testRW162G1_ExpandIsSynchronousAndMeasured_SourcePins() throws {
        let bar = try sourceFile("../RunWear/Views/WearIveRunMiniBar.swift")
        let tapStart = try XCTUnwrap(bar.range(of: "private func handleExpandTap()"))
        let tapEnd = try XCTUnwrap(bar.range(of: "// MARK: §4.1"))
        let tap = bar[tapStart.lowerBound..<tapEnd.lowerBound]
        let expandCall = try XCTUnwrap(tap.range(of: "onExpand()"))
        let pressAnim = try XCTUnwrap(tap.range(of: "withAnimation"))
        XCTAssertLessThan(expandCall.lowerBound, pressAnim.lowerBound,
                          "RW-162 G1: onExpand fires in the tap's own transaction, BEFORE any press animation — never in its completion")
        // RE-PINNED for RW-169 §7A (Nov 11, 2026; supersedes
        // the RW-162 G1 measurement-pass pin that stood here):
        // the during-morph measurement pass (startMorph) is
        // RETIRED — it was D3's mechanism (the expanded height
        // existed only while the morph ran). The morph's
        // endpoints are the two REST heights, so the tap runs
        // the spring immediately, with no measurement to wait
        // for. The travel-only clip pins below stand.
        XCTAssertFalse(bar.contains("private func startMorph()"),
                      "RW-169 §7A: no during-morph measurement pass remains")
        XCTAssertTrue(bar.contains("WearIveRunMorphProbe.containerHeight"),
                      "RW-169 §7A: the container height is the rest-height interpolation (see WearIveRunOverlayView)")
        // RW-174 (row 17) SUPERSEDES the RW-162 G5 travel-clip
        // pins that stood here: the container's travel clip
        // bound the surface's §2.1 shadow to 0 pt of spill for
        // the whole morph (RW-170 travel-clip probe), so the
        // clip is retired from the container — the unified
        // surface clips its own CONTENT layer to the morph's
        // rounded shape (radius 16, constant) and its shadow
        // is applied after that clip, unclipped at every
        // progress value.
        XCTAssertFalse(bar.contains("travelClipActive"),
                      "RW-174 row 17: the RW-162 G5 container travel clip is retired — it suppressed the §2.1 shadow mid-morph")
        XCTAssertFalse(bar.contains("clippedIfMorphing"),
                      "RW-174 row 17: no container clip wraps the unified surface")
        XCTAssertTrue(bar.contains(".clipShape(RoundedRectangle(cornerRadius: WearIveRunMorph.cornerRadius(progress: p)))"),
                      "RW-174 row 17: the clip lives INSIDE the surface, on its content layer, following the morph's rounded shape")
    }

    /// RW-162 G2: the fresh-fix rule and its wiring — an
    /// explicit Current Location tap never answers with a
    /// cached fix from before the tap.
    func testRW162G2_FreshFixPolicy() throws {
        let requestedAt = Date(timeIntervalSince1970: 1_700_000_000)
        XCTAssertTrue(FreshFixPolicy.isFresh(
            fixDate: requestedAt.addingTimeInterval(3), requestedAt: requestedAt),
            "a fix timestamped after the tap is fresh")
        XCTAssertTrue(FreshFixPolicy.isFresh(
            fixDate: requestedAt.addingTimeInterval(-1), requestedAt: requestedAt),
            "a fix inside the 2 s clock slack is fresh")
        XCTAssertFalse(FreshFixPolicy.isFresh(
            fixDate: requestedAt.addingTimeInterval(-3600), requestedAt: requestedAt),
            "the previous city's hour-old cached fix is NOT the tap's answer")
        XCTAssertFalse(FreshFixPolicy.windowClosed(
            requestedAt: requestedAt, now: requestedAt.addingTimeInterval(5)))
        XCTAssertTrue(FreshFixPolicy.windowClosed(
            requestedAt: requestedAt, now: requestedAt.addingTimeInterval(11)),
            "the bounded window closes and the best fix seen answers instead of a dead end")
        let service = try sourceFile("../RunWear/Services/LocationService.swift")
        XCTAssertTrue(service.contains("locations.last"),
                      "a delivery batch's LAST fix is the newest (the pre-fix code took the stalest, .first)")
        XCTAssertTrue(service.contains("func requestFreshLocation()"))
        let content = try sourceFile("../RunWear/Views/ContentView.swift")
        XCTAssertEqual(content.components(separatedBy: "locationService.requestFreshLocation()").count - 1, 2,
                       "both explicit Current Location taps (gate sheet + Home sheet) request a fresh fix")
    }

    /// RW-162 G5: the expanded panel OVER NON-UNIFORM content —
    /// the acceptance frame for the boundary/shadow fix. The
    /// harness's uniform grounds are exactly why this class
    /// kept passing the gate: a flat rectangle over a flat
    /// ground reads as composed. Over list rows the panel's
    /// hairline edge and shadow are the separation.
    @MainActor
    func testRW162Frame_PanelOverContent() throws {
        let entry = frameEntry(hero: "")
        let panel = WearIveRunExpandedPanelView(
            state: frameState(for: entry),
            entry: entry,
            formattedFeelsLike: "71°",
            viewportHeight: 852,
            onOpenCollection: {}, onCollapse: {}, onTrash: {})
        let backdrop = VStack(spacing: 0) {
            ForEach(0..<14, id: \.self) { row in
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(row.isMultiple(of: 2) ? AppTheme.pinePayload.opacity(0.55) : AppTheme.cardOnSheet)
                        .frame(width: 44, height: 44)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Running Shorts (5-7\")")
                            .font(.appItemName)
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("Row \(row + 1) of the outfit list under the panel")
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(row.isMultiple(of: 2) ? AppTheme.sheet : AppTheme.background)
            }
        }
        let composed = ZStack(alignment: .bottom) {
            backdrop
            panel
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, 14)
        }
        try writeRender(composed, size: CGSize(width: 393, height: 852),
                        name: "RW162-ios-panel-over-content.png")
    }
}
