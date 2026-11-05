import XCTest
import UIKit

/// GEN-5 (RW-28) — the fidelity fix wave's pins, iOS half.
///
/// Every test here pins a wave item or an addendum item against
/// the settled design (rw-gen4-walkthrough-2026-11-05/census.md
/// §5 + rulings.md, as amended). Pure logic is pinned on the
/// dual-membered models; view-level settlements are pinned by
/// reading the source of record, the established pattern in this
/// suite (a source pin fails the gate the moment the shipped
/// source drifts from the settlement).
final class Gen5FidelityTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func entry(
        place: String, day: Date, feels: Double, items: [String] = ["Tank Top"],
        hero: String = "https://example.com/hero.webp", session: String = "",
        moment: MomentReading = .now, savedAt: Date? = nil
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: day, placeName: place, feelsLikeF: feels, actualF: feels - 5,
            condition: "Clear sky", itemNames: items, itemKeys: items.map { $0.lowercased() },
            heroImageReference: hero, sessionID: session,
            momentReading: moment, lastSavedAt: savedAt ?? day
        )
    }

    private var nov5: Date {
        var comps = DateComponents()
        comps.year = 2026; comps.month = 11; comps.day = 5; comps.hour = 6
        return Calendar.current.date(from: comps)!
    }
    private var nov4: Date {
        var comps = DateComponents()
        comps.year = 2026; comps.month = 11; comps.day = 4; comps.hour = 6
        return Calendar.current.date(from: comps)!
    }
    private var nov2: Date {
        var comps = DateComponents()
        comps.year = 2026; comps.month = 11; comps.day = 2; comps.hour = 6
        return Calendar.current.date(from: comps)!
    }

    // MARK: - Wave 2 / C-7: narration names what changed, never a garment

    func testNarrationPayload_PlaceChangeNamesThePlace() {
        let before = entry(place: "Kansas City, Missouri", day: nov5, feels: 36)
        let after = entry(place: "Miami, Florida", day: nov5, feels: 78)
        XCTAssertEqual(
            WearIveRunNarrationRules.updatePayload(existing: before, incoming: after),
            .place("Miami, FL"))
    }

    func testNarrationPayload_TimeChangeNamesTheReading() {
        let before = entry(place: "Miami, Florida", day: nov5, feels: 78, moment: .now)
        let after = entry(place: "Miami, Florida", day: nov5, feels: 83,
                          items: ["Light Long Sleeve"], moment: .clock(hour12: 9, isPM: true))
        XCTAssertEqual(
            WearIveRunNarrationRules.updatePayload(existing: before, incoming: after),
            .moment(.clock(hour12: 9, isPM: true)))
        // And the classifier treats a moment-only change as a
        // narrated, cued update — the Wear card re-solves and the
        // cue fires (items 8/9).
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: before, incoming: after),
            .momentUpdated)
    }

    func testNarrationPayload_NeverAGarment() {
        // Kit changes with place and moment unchanged: even the
        // payload RULE never names a garment — and at the model
        // level a kit-only settle is silent and preserves the
        // narration it found (pinned in
        // WearIveRunElementMachineTests.testCollectionModelCueSemantics).
        let before = entry(place: "Miami, Florida", day: nov5, feels: 78,
                           items: ["Tank Top"], moment: .clock(hour12: 9, isPM: true))
        let after = entry(place: "Miami, Florida", day: nov5, feels: 78,
                          items: ["Light Long Sleeve"], moment: .clock(hour12: 9, isPM: true))
        let payload = WearIveRunNarrationRules.updatePayload(existing: before, incoming: after)
        // GEN-5 (Addendum 8): the payload is a structural case —
        // a moment here — and its rendered form names the
        // reading, never the garment that changed.
        XCTAssertEqual(payload, .moment(.clock(hour12: 9, isPM: true)))
        XCTAssertEqual(payload.rendered, "9 p.m.")
        XCTAssertFalse(payload.rendered.contains("Sleeve"))
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: before, incoming: after),
            .kitUpdated)
    }

    func testEntryDecoding_LegacyPayloadWithoutGen5Fields() throws {
        // A pre-gen-5 stored entry (no moment/payload/lastSaved
        // keys) must decode, not wipe the collection.
        let legacy = """
        {"dayStart": 500000000, "placeName": "Miami, Florida",
         "feelsLikeF": 78, "actualF": 73, "condition": "Clear sky",
         "itemNames": ["Tank Top"], "itemKeys": ["tank"],
         "heroImageReference": "", "sessionID": "s1",
         "wasUpdatedInSession": true}
        """
        let decoded = try JSONDecoder().decode(WearIveRunEntry.self, from: Data(legacy.utf8))
        XCTAssertEqual(decoded.momentReading, .now)
        XCTAssertNil(decoded.updatePayload)
        XCTAssertEqual(decoded.narrationPayload, .place("Miami, FL"))
        XCTAssertEqual(decoded.lastSavedAt, decoded.dayStart)
    }

    // MARK: - Addendum 1+2: Sol's collection shape opens everywhere

    /// Sol's actual shape (Nov 5): a today-session entry for
    /// Kansas City; a collated Kansas City chapter (2 stays,
    /// 2 days, 36°); a collated Miami chapter (2 stays, 2 days,
    /// 78°) — including a Miami entry with NO thumbnail (Sol's
    /// own observation: "On Miami it looked like no thumbnail
    /// available. Probably that's why."). Every entry type must
    /// open: chapters compute, covers resolve, summaries render,
    /// stays enumerate — and the thumbnail-less entry is carried
    /// like any other (the iOS path never dereferences the photo:
    /// chapter/day rendering guards the reference through
    /// AsyncImage's default phase, pinned by source below).
    func testSolShapedCollection_EveryEntryOpens() {
        let todaySession = entry(place: "Kansas City, Missouri", day: nov5, feels: 36,
                                 session: "session-today", savedAt: nov5)
        let kcOlder = entry(place: "Kansas City, Missouri", day: nov2, feels: 41)
        let miamiRecent = entry(place: "Miami, Florida", day: nov5, feels: 78,
                                hero: "", session: "session-miami-a")
        // Sol's shape is 2 STAYS: the two Miami days are NOT
        // consecutive (Nov 2 + Nov 5), so the collation splits
        // them — a consecutive pair would read as one stay.
        let miamiOlder = entry(place: "Miami, Florida", day: nov2, feels: 80,
                               hero: "not a url", session: "session-miami-b")
        let all = [todaySession, kcOlder, miamiRecent, miamiOlder]

        // Both collated chapters compute from the full set.
        let chapters = WearIveRunCollection.chapters(from: all, filter: .all)
        XCTAssertEqual(Set(chapters.map(\.placeName)), ["Kansas City, MO", "Miami, FL"])
        // And with the today pin lifted out (the collection's
        // own presentation), both chapters still remain.
        XCTAssertEqual(
            Set(WearIveRunCollection.chapters(
                from: all.filter { $0.id != todaySession.id }, filter: .all
            ).map(\.placeName)),
            ["Kansas City, MO", "Miami, FL"])

        for chapter in chapters {
            // Cover, range, summary — the surfaces a chapter row
            // renders before any tap.
            _ = chapter.cover
            _ = chapter.feelsLikeRangeF
            let summary = chapter.summaryLine(formatTemperature: { "\(Int($0))°" })
            XCTAssertTrue(summary.contains("2 stays · 2 days · Last stay · Nov 5"),
                          "\(chapter.placeName): \(summary)")
            // Every stay and every day entry enumerates — the
            // exact traversal the chapter view performs on open.
            XCTAssertEqual(chapter.stays.count, 2)
            for stay in chapter.stays {
                XCTAssertFalse(stay.entries.isEmpty)
                _ = stay.firstDay; _ = stay.lastDay
                for day in stay.entries {
                    _ = day.placeDisplayName
                    _ = WearIveRunDateFormatting.dayLabel(day.dayStart)
                }
            }
            // The chapter lookup the detail view performs by name.
            XCTAssertNotNil(
                WearIveRunCollection.chapters(from: all, filter: .all)
                    .first { $0.placeName == chapter.placeName })
        }

        // Miami's summary names its warm temperature; the
        // thumbnail-less entry is IN the chapter (not dropped).
        let miami = chapters.first { $0.placeName == "Miami, FL" }!
        XCTAssertTrue(miami.summaryLine(formatTemperature: { "\(Int($0))°" }).contains("78°"))
        XCTAssertTrue(miami.entries.contains { $0.heroImageReference.isEmpty })

        // The pinned TODAY card's "Last saved" element (item 10).
        XCTAssertEqual(
            WearIveRunDateFormatting.lastSavedLine(savedAt: nov5, now: nov5.addingTimeInterval(30)),
            "Last saved just now")
        XCTAssertTrue(
            WearIveRunDateFormatting.lastSavedLine(savedAt: nov4, now: nov5)
                .hasPrefix("Last saved Nov 4"))
    }

    func testCollectionSource_MissingPhotoIsGuardedEverywhere() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        // The chapter/cover image seam renders its placeholder on
        // every non-success phase — a missing photo is a treatment,
        // not a trap.
        XCTAssertTrue(collection.contains("case .success(let image)"))
        XCTAssertTrue(collection.contains("HeroPhoto(image: image"))
        // The poster pipeline guards the reference before loading.
        XCTAssertTrue(collection.contains("guard let url = URL(string: entry.heroImageReference)"))
        // No force-unwrapped photo access anywhere in the file.
        XCTAssertFalse(collection.contains("heroImageReference!"))
        XCTAssertFalse(collection.contains("URL(string: entry.heroImageReference)!"))
    }

    // MARK: - Wave 1 + C-4 amended + Addendum 3: the pill state machine

    func testPill_FreeGeneratingCarriesTheInstantlyLine() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5),
            .makingWithUpsell)
        XCTAssertEqual(HeroPill.getInstantlyLabel, "Get it instantly next time")
    }

    func testPill_PremiumGeneratingStaysClean() {
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: false,
                             isSubscribed: true, freeGenerationsUsed: 0, freeAllowance: 5),
            .making)
    }

    func testPill_ExhaustedAtExactlyFive() {
        // 4 of 5: the ordinary free status.
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 4, freeAllowance: 5),
            .freeLook(used: 4, allowance: 5))
        // Exactly 5: the exhausted/upsell status takes over.
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .ready, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 5, freeAllowance: 5),
            .exhausted(used: 5, allowance: 5))
        XCTAssertEqual(HeroPill.exhaustedCountLabel(used: 5, allowance: 5), "5 of 5 looks used")
        // Settings agrees with the pill in the exhausted state
        // (the shared meter's own line).
        XCTAssertEqual(FreeLooksMeter.settingsLine(used: 5, allowance: 5), "All 5 free looks used")
        XCTAssertEqual(FreeLooksMeter.settingsLine(used: 4, allowance: 5), "4 of 5 free looks used")
    }

    // MARK: - RW-29 HIGH findings FA-1 / FA-2 / FA-3

    func testPill_GenerationOutranksTheScaffoldServe_FA1() {
        // A personal (scaffold) photo on screen while a look is
        // generating: the pill must show the GENERATING status,
        // not the scaffold status — generation in flight
        // outranks the scaffold serve (Android test-pins the
        // same precedence).
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: true,
                             isSubscribed: false, freeGenerationsUsed: 1, freeAllowance: 5),
            .makingWithUpsell)
        XCTAssertEqual(
            HeroPill.resolve(optedIn: true, lookState: .making, personalPhotoDisplayed: true,
                             isSubscribed: true, freeGenerationsUsed: 0, freeAllowance: 5),
            .making)
    }

    func testRecordTodaysEntry_NeverWritesOutsideNow_FA2() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        let start = try XCTUnwrap(view.range(of: "private func recordTodaysEntry()"))
        let rest = view[start.upperBound..<view.endIndex]
        let end = rest.range(of: "\n    private func ")?.lowerBound ?? view.endIndex
        let body = String(view[start.lowerBound..<end])
        XCTAssertTrue(body.contains("guard viewModel.isViewingLocationNow"),
                      "FA-2: the NOW-state guard stands at the recording gate")
        XCTAssertTrue(body.contains("isDateInToday"),
                      "the date guard remains as the second condition")
    }

    func testNarrationMomentComesFromTheEntry_FA3() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("moment: todays?.momentReading ?? \"Now\""),
                      "FA-3: the narration moment derives from the ENTRY, per C-7")
        XCTAssertFalse(view.contains("moment: viewModel.sheetMoment"),
                       "FA-3: the live home view's moment never feeds the narration")
    }

    func testPillView_EveryStatusLeadsWithTheScript() throws {
        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        XCTAssertTrue(hero.contains("case .makingWithUpsell"))
        XCTAssertTrue(hero.contains("case .exhausted"))
        XCTAssertTrue(hero.contains("HeroPill.getInstantlyLabel"))
        // The making case renders the script mark (it was the one
        // state that dropped it — A-13/B-14).
        let makingRange = try XCTUnwrap(hero.range(of: "case .making:"))
        let scaffoldRange = try XCTUnwrap(hero.range(of: "case .makingWithUpsell:"))
        XCTAssertTrue(hero[makingRange.lowerBound..<scaffoldRange.lowerBound].contains("pillScriptMark"))
    }

    // MARK: - Sharing fix: the poster crop keeps the face

    func testShareCrop_FaceStaysInFrame() {
        // A portrait hero (the headless-poster shape): 1000×1500,
        // face in the top band.
        let size = CGSize(width: 1000, height: 1500)
        let face = CGRect(x: 0.35, y: 0.04, width: 0.30, height: 0.16)
        let rect = ShareCardCrop.cropRect(imageSize: size, faceRectTopLeftNormalized: face)
        XCTAssertEqual(rect.width / rect.height, ShareCardCrop.posterAspect, accuracy: 0.01)
        let facePx = CGRect(x: face.minX * 1000, y: face.minY * 1500,
                            width: face.width * 1000, height: face.height * 1500)
        XCTAssertTrue(rect.contains(facePx), "crop \(rect) must contain the face \(facePx)")
    }

    func testShareCrop_NoFaceFallsBackToTheTopAnchor() {
        let rect = ShareCardCrop.cropRect(
            imageSize: CGSize(width: 1000, height: 1500), faceRectTopLeftNormalized: nil)
        XCTAssertEqual(rect.minY, 0)
        XCTAssertEqual(rect.width / rect.height, ShareCardCrop.posterAspect, accuracy: 0.01)
    }

    func testShareRender_ProducesAPosterWithTheFaceBand() {
        // Render-level proof: a synthetic hero whose top band is
        // a distinct "face" colour must keep that colour in the
        // cropped poster source — the center crop the gen-4 build
        // shipped would have taken the middle band instead.
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let hero = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 600), format: format).image { _ in
            UIColor.blue.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 400, height: 120))   // face band
            UIColor.green.setFill()
            UIRectFill(CGRect(x: 0, y: 120, width: 400, height: 480)) // body
        }
        let cropped = ShareCardCrop.facePreservingCropped(hero, faceRectTopLeftNormalized: nil)
        XCTAssertEqual(cropped.size.width / cropped.size.height,
                       ShareCardCrop.posterAspect, accuracy: 0.02)
        // The cropped window keeps the face band at one edge and
        // the body at the other (bitmap row order is
        // platform-defined, so read both edges): a center crop
        // would show the body colour at BOTH edges.
        let w = 40, h = 50
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        let context = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(cropped.cgImage!, in: CGRect(x: 0, y: 0, width: w, height: h))
        func pixelIsBlue(_ row: Int) -> Bool {
            let i = (row * w + w / 2) * 4
            return bytes[i + 2] > 150 && bytes[i + 1] < 120
        }
        func pixelIsGreen(_ row: Int) -> Bool {
            let i = (row * w + w / 2) * 4
            return bytes[i + 1] > 150 && bytes[i + 2] < 120
        }
        let edgeA = pixelIsBlue(2) && pixelIsGreen(h - 3)
        let edgeB = pixelIsBlue(h - 3) && pixelIsGreen(2)
        XCTAssertTrue(edgeA || edgeB,
                      "the face band must survive at the poster's edge — the headless center crop is gone")
    }

    // MARK: - C-2: the fork hero crop contract

    func testForkCrop_FaceBoxVisibleAtEveryTestedAspect() {
        // The asset lane's crop-test table (rw-gen5-assets):
        // phone bands 0.86–1.74 and the wide 2.32/2.56 bands.
        for (w, h) in [(360.0, 420.0), (800.0, 800.0), (390.0, 300.0), (390.0, 240.0),
                       (430.0, 260.0), (1080.0, 620.0), (834.0, 360.0), (1024.0, 400.0)] {
            let frame = CGSize(width: w, height: h)
            XCTAssertTrue(
                ForkHeroSpec.faceBoxFullyVisible(imageSize: ForkHeroSpec.imageSize, frameSize: frame),
                "face box must be fully in frame at \(w)×\(h)")
            // Shoulders stay in frame horizontally at phone aspects.
            let visible = ForkHeroSpec.visibleRect(imageSize: ForkHeroSpec.imageSize, frameSize: frame)
            if w / h <= 1.78 {
                XCTAssertLessThanOrEqual(visible.minX, ForkHeroSpec.shouldersXRange.lowerBound * 2048 + 1)
                XCTAssertGreaterThanOrEqual(visible.maxX, ForkHeroSpec.shouldersXRange.upperBound * 2048 - 1)
            }
        }
        XCTAssertEqual(ForkHeroSpec.faceBoxNormalized.minX, 0.400, accuracy: 0.0001)
        XCTAssertEqual(ForkHeroSpec.faceBoxNormalized.minY, 0.018, accuracy: 0.0001)
        XCTAssertEqual(ForkHeroSpec.faceBoxNormalized.width, 0.214, accuracy: 0.0001)
        XCTAssertEqual(ForkHeroSpec.faceBoxNormalized.height, 0.478, accuracy: 0.0001)
    }

    func testForkBand_SizedHeightsKeepTheFaceAtEveryShippedWidth() {
        // RW-29 FA-40: the band the view actually builds —
        // height from ForkHeroSpec.bandHeight — at every width
        // from a small phone to a tablet. The whole face box
        // (and therefore the head) stays in the visible window,
        // and the window always reaches at least the chest
        // (torso visible to y ≥ 0.65 of the image height).
        XCTAssertEqual(ForkHeroSpec.bandHeight(forWidth: 390), 180)
        XCTAssertEqual(ForkHeroSpec.bandHeight(forWidth: 834), 834 / 2.4, accuracy: 0.001)
        for width in stride(from: 320.0, through: 1180.0, by: 20.0) {
            let frame = CGSize(width: width, height: ForkHeroSpec.bandHeight(forWidth: width))
            XCTAssertTrue(
                ForkHeroSpec.faceBoxFullyVisible(imageSize: ForkHeroSpec.imageSize, frameSize: frame),
                "face box must be fully in frame at width \(width)")
            let visible = ForkHeroSpec.visibleRect(imageSize: ForkHeroSpec.imageSize, frameSize: frame)
            XCTAssertGreaterThanOrEqual(visible.maxY, 0.65 * 1152,
                                        "the window must reach the chest at width \(width)")
        }
    }

    func testForkView_UsesTheBundledAssetWithTheCodeDrawnBox() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("ForkHeroSpec.faceBoxRectInFrame"))
        XCTAssertTrue(flow.contains("dash: [10, 7]"))
        XCTAssertTrue(flow.contains("mim-fork-hero") || flow.contains("ForkHeroSpec.assetName"))
    }

    // MARK: - Wave 6 / C-5 / C-6: copy settlements

    func testPriceLabels_SettledForms() {
        XCTAssertEqual(MakeItMePriceLabels.monthlyRowLabel(priceString: "$0.99"), "Monthly — $0.99/month")
        XCTAssertEqual(MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: "$9.99"), "$9.99/yr")
        XCTAssertEqual(MakeItMePriceLabels.settingsMonthlyLabel(monthlyPriceString: "$0.99"), "Monthly — $0.99/month")
        XCTAssertEqual(MakeItMePriceLabels.perRunLine(annual: Decimal(string: "9.99")), "only about 4¢ per run")
        XCTAssertEqual(
            FreeLooksMeter.subscriptionLine(used: 2, allowance: 5),
            "2 of 5 used · Free looks never expire — and your first ones take about a minute to make.")
    }

    func testWelcome_SettledHeadlinesAndFooter() {
        XCTAssertEqual(OnboardingCopy.welcomePoints.map(\.phrase),
                       ["The right outfit for the weather", "See yourself in every outfit", "Remember every run"])
        XCTAssertEqual(OnboardingCopy.welcomePoints.map(\.name),
                       ["RunWear", "Make It Me", "Wear I've Run"])
        for point in OnboardingCopy.welcomePoints {
            XCTAssertFalse(point.body.contains(point.name), "body must not repeat the name: \(point.body)")
        }
        XCTAssertEqual(OnboardingCopy.welcomeFooter, "Free to start · Set up in under a minute")
    }

    func testHomeSource_OneMomentSystemAndSettledOrder() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains("sheetDateTimePills"), "the duplicate date/time pill row is removed (item 12)")
        // Final composed order inside the sheet container
        // (C-3 + addendum 4): outfit → convergence band (while
        // generating) → day chart → Pro Tip → personal section.
        let containerIdx = try XCTUnwrap(home.range(of: "private func sheetContainer")?.lowerBound)
        let bandIdx = try XCTUnwrap(home.range(of: "homeBandSection", range: containerIdx..<home.endIndex)?.lowerBound)
        let chartIdx = try XCTUnwrap(home.range(of: "dayChartBand", range: bandIdx..<home.endIndex)?.lowerBound)
        let tipIdx = try XCTUnwrap(home.range(of: "proTipSection", range: chartIdx..<home.endIndex)?.lowerBound)
        XCTAssertTrue(home.range(of: "personalSection", range: tipIdx..<home.endIndex) != nil)
        // The changed-state moment is the bare simple form.
        XCTAssertTrue(home.contains("if calendar.isDateInToday(viewModel.selectedDate)"))
        XCTAssertFalse(home.contains("\"Today, \\(heroHourString)\""))
    }

    func testIntakeSource_OnePhotoFaceOnly() throws {
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertFalse(flow.contains("1 to 3 photos"))
        XCTAssertFalse(flow.contains("body to land on"))
        // The settled passing copy (item 17): the check is a
        // drawn SF Symbol glyph + the words — the pair renders
        // as the design's "✓ That's exactly what we need" with
        // no emoji-class character in copy (WS-10 census).
        XCTAssertTrue(flow.contains("Image(systemName: \"checkmark\")"))
        XCTAssertTrue(flow.contains("Text(\"That's exactly what we need\")"))
        XCTAssertTrue(flow.contains("maxPhotos = 1"))
        let capture = try sourceFile("../RunWear/Views/GuidedSelfieCaptureView.swift")
        XCTAssertTrue(capture.contains("Image(systemName: \"checkmark\")"))
        XCTAssertTrue(capture.contains("Text(\"That's exactly what we need\")"))
    }

    func testSettingsSource_DangerZoneAndVerbatimLines() throws {
        let settings = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(settings.contains("Text(\"Danger zone\")"))
        XCTAssertTrue(settings.contains("Subscribed · Your photo is ready before you open the app."))
        XCTAssertTrue(settings.contains("settingsMonthlyLabel"))
        XCTAssertTrue(settings.contains("perRunLine"))
        XCTAssertTrue(settings.contains("Your current photo"))
        XCTAssertTrue(settings.contains("A new photo starts a new set"))
        XCTAssertTrue(settings.contains("Retake Selfie"))
        XCTAssertTrue(settings.contains("Choose a Different Photo"))
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(modal.contains("Text(\"Danger zone\")"))
    }

    func testLocationSource_SettledSearchField() throws {
        let picker = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(picker.contains("TextField(\"Search a place\""))
        XCTAssertTrue(picker.contains("Your recent places will wait here"))
    }

    func testBandSource_SelectedHourOnTheAxis() throws {
        let chart = try sourceFile("../RunWear/Views/Components/DayChartView.swift")
        XCTAssertTrue(chart.contains("showsSelectedHourOnAxis"))
        XCTAssertTrue(chart.contains("selectedHourAxisLabel"))
    }

    // MARK: - C-1 verification (rulings: already on iOS — pin it)

    func testComfortControl_HapticsAndTintShift() throws {
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertTrue(controls.contains(".sensoryFeedback(.selection, trigger: comfortLevel)"))
        XCTAssertTrue(controls.contains("case .veryCold, .cold: return .blue"))
        XCTAssertTrue(controls.contains("case .warm, .veryWarm: return .orange"))
        // The same shared control serves onboarding and Settings.
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(onboarding.contains("ComfortLevelControl(comfortLevel: $comfortLevel)"))
        XCTAssertTrue(modal.contains("ComfortLevelControl(comfortLevel: $comfortLevel)"))
    }

    // MARK: - Addendum 5.1: the design pass (cream system)

    func testDesignPass_SettingsFormIsOnSystem() throws {
        let modal = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        // The named instance: Settings' Form hides the system
        // grouped background, grounds on the approved cream /
        // warm charcoal, and seats every row on the sheet tone.
        XCTAssertTrue(modal.contains(".scrollContentBackground(.hidden)"))
        XCTAssertTrue(modal.contains(".listRowBackground(AppTheme.sheet)"))
        XCTAssertTrue(modal.contains("AppTheme.background.ignoresSafeArea()"))
    }

    func testDesignPass_BandReplacesSettingsStatusWhileGenerating() throws {
        // Addendum 5.2(b), confirmed from Sol's own iOS evidence:
        // while generating, the band REPLACES the status block —
        // and the readiness claim cannot render alongside it.
        let settings = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(settings.contains("if store.mode == .subscribed, let band = settingsConvergenceBand"))
        XCTAssertTrue(settings.contains("ConvergenceBandView(content: band)"))
    }

    // MARK: - Addendum 1.3: the hero failure line is the decided line

    func testFailureCopy_IsSolDecidedVerbatim() {
        // The line Android showed in Sol's recording is the same
        // string iOS renders — and it is Sol's decided wording
        // (RW-16.16, Nov 1, 2026), not a deviation from a settled
        // deck line (the design sets no other hero failure line).
        XCTAssertEqual(MakeItMeCopy.lookFailed,
                       "Your look couldn't be made from these photos — tap to try again.")
        XCTAssertEqual(MakeItMeCopy.lookRetry, "Try again")
    }

    // MARK: - Addendum 8.2: a garment payload is UNREPRESENTABLE

    func testMomentReading_RendersOnlySimpleForms() {
        XCTAssertEqual(MomentReading.now.rendered, "Now")
        XCTAssertEqual(MomentReading.clock(hour12: 9, isPM: true).rendered, "9 p.m.")
        XCTAssertEqual(MomentReading.clock(hour12: 12, isPM: false).rendered, "12 a.m.")
        XCTAssertEqual(
            MomentReading.daypart(dayWord: "Tomorrow", part: .evening).rendered,
            "Tomorrow evening")
        XCTAssertEqual(
            MomentReading.daypart(dayWord: "Friday", part: .morning).rendered,
            "Friday morning")
    }

    func testMomentReading_ParsingRejectsGarmentsAndStamps() {
        XCTAssertEqual(MomentReading(parsing: "Now"), .now)
        XCTAssertEqual(MomentReading(parsing: "9 p.m."), .clock(hour12: 9, isPM: true))
        XCTAssertEqual(MomentReading(parsing: "Tomorrow evening"),
                       .daypart(dayWord: "Tomorrow", part: .evening))
        // The Android breach string and the stamped form have
        // NO case:
        XCTAssertNil(MomentReading(parsing: "Ear Warmer / Headband"))
        XCTAssertNil(MomentReading(parsing: "Nov 7 9:00 PM"))
        XCTAssertNil(MomentReading(parsing: "Tank Top / Singlet"))
    }

    func testLegacyGarmentPayload_IsDroppedAtDecode() throws {
        // An entry persisted by the old defect, carrying a
        // garment update payload: decode must not carry it
        // forward — the payload drops and the entry narrates
        // from its place.
        let legacy = """
        {"dayStart": 500000000, "placeName": "Miami, Florida",
         "feelsLikeF": 78, "actualF": 73, "condition": "Clear sky",
         "itemNames": ["Tank Top"], "itemKeys": ["tank"],
         "heroImageReference": "", "sessionID": "s1",
         "wasUpdatedInSession": true, "momentReading": "Now",
         "updatePayload": "Ear Warmer / Headband"}
        """
        let decoded = try JSONDecoder().decode(WearIveRunEntry.self, from: Data(legacy.utf8))
        XCTAssertNil(decoded.updatePayload)
        XCTAssertEqual(decoded.narrationPayload, .place("Miami, FL"))
        XCTAssertEqual(decoded.narrationPayload.rendered, "Miami, FL")
    }

    func testChangedHeadwearSettle_ProducesNoNarration() {
        // The Addendum 8 state-sequence proof: two settles
        // differing ONLY in the headwear item. The classifier
        // reads the change as a kit change (silent), and the
        // recorded entry keeps the narration it already had —
        // no garment narration can be produced by this settle.
        let before = entry(place: "Kansas City, Missouri", day: nov5, feels: 36,
                           items: ["Ear Warmer", "Tights"], session: "s-head")
        let after = entry(place: "Kansas City, Missouri", day: nov5, feels: 36,
                          items: ["Headband", "Tights"], session: "s-head")
        XCTAssertEqual(
            WearIveRunLandingClassifier.classify(existing: before, incoming: after),
            .kitUpdated)
        let model = WearIveRunCollectionModel(
            store: WearIveRunStore(defaults: UserDefaults(suiteName: "gen5-headwear-\(UUID().uuidString)")!),
            sessionID: "s-head")
        model.recordSettled(before)
        let narratedBefore = model.sessionEntry()?.narrationPayload
        model.recordSettled(after)
        let settled = model.sessionEntry()
        XCTAssertEqual(settled?.itemNames.first, "Headband",
                       "the settle itself lands — only its narration is silent")
        XCTAssertEqual(settled?.narrationPayload, narratedBefore)
        XCTAssertEqual(settled?.wasUpdatedInSession, false)
        XCTAssertFalse(settled?.narrationPayload.rendered.contains("Headband") ?? true)
        XCTAssertFalse(settled?.narrationPayload.rendered.contains("Ear Warmer") ?? true)
    }

    // MARK: - Addendum 8.1: the band's progress is real counts; a stalled bank says so

    func testBandFraction_IsTheRealBankedFraction() {
        let content = ConvergenceBand.content(
            isSubscribed: true, coverage: BankCoverage(ready: 43, total: 48))
        XCTAssertEqual(content?.fraction, 43.0 / 48.0)
        XCTAssertEqual(content?.line, ConvergenceBand.line)
    }

    // MARK: Addendum 8 follow-up — the convergence diagnosis

    /// Defect 2, Sol's exact numbers: 8 banked cells against the
    /// 48-cell bank reads 8/48 — never the engaged-union 8/9 the
    /// gen-4 formula produced for his screenshot.
    func testCoverage_SolShapedBankReadsEightOfFortyEight() {
        let target = Set((0..<48).map { "cell-\($0)" })
        let banked = Set((0..<8).map { "cell-\($0)" })
        let coverage = deriveBankCoverage(heroCells: banked, targetCells: target)
        XCTAssertEqual(coverage?.ready, 8)
        XCTAssertEqual(coverage?.total, 48)
        let content = ConvergenceBand.content(isSubscribed: true, coverage: coverage)
        XCTAssertEqual(content?.fraction, 8.0 / 48.0)
    }

    /// Defect 1: the named forecast cells are one representative
    /// frame per (day, daypart) cluster — the cluster's first
    /// forecast hour — in forecast order, deduped by cell id
    /// (Android's ForecastFillCells selection, mirrored).
    func testForecastPriorityCells_ClusterRepresentativesInForecastOrder() {
        // Real engine outfits, as the view model's derivation
        // assembles them per forecast hour.
        let mild = OutfitRecommendationService.shared.getRecommendation(
            for: 62, rawTemperature: 62, isRaining: false, windSpeed: 5,
            humidity: 50, uvIndex: 3, precipitation: 0, hour: 6)
        let warm = OutfitRecommendationService.shared.getRecommendation(
            for: 78, rawTemperature: 78, isRaining: false, windSpeed: 5,
            humidity: 50, uvIndex: 5, precipitation: 0, hour: 12)
        let frames = [
            // Day 1 DAWN cluster: two hours, one representative.
            ForecastCellFrame(date: "2026-11-06", weatherCode: 0, feelsLikeFahrenheit: 62, hour: 6, outfit: mild),
            ForecastCellFrame(date: "2026-11-06", weatherCode: 0, feelsLikeFahrenheit: 62, hour: 7, outfit: mild),
            // Day 1 MIDDAY cluster.
            ForecastCellFrame(date: "2026-11-06", weatherCode: 0, feelsLikeFahrenheit: 78, hour: 12, outfit: warm),
            // Day 2 DAWN cluster prescribes day 1's dawn cell again
            // (cell ids carry no date) — named once.
            ForecastCellFrame(date: "2026-11-07", weatherCode: 0, feelsLikeFahrenheit: 62, hour: 6, outfit: mild),
        ]
        let ids = ForecastPriorityCells.ids(genderPreference: .male, frames: frames)
        XCTAssertEqual(ids.count, 2, "cluster representatives only, deduped by cell id")
        XCTAssertEqual(ids[0], PersonalCombination.make(
            genderPreference: .male, weatherCode: 0, feelsLikeFahrenheit: 62, hour: 6, outfit: mild
        ).combinationId, "forecast order: the first cluster's cell leads")
        XCTAssertEqual(ids[1], PersonalCombination.make(
            genderPreference: .male, weatherCode: 0, feelsLikeFahrenheit: 78, hour: 12, outfit: warm
        ).combinationId)
    }

    /// The corrected fill set (second follow-up): the core
    /// matrix is 20 cells with the settled segment structure —
    /// every bracket at CLEAR DAWN, CLEAR DUSK, RAIN DAWN, plus
    /// WARM/HOT at CLEAR MIDDAY and nothing else — and the
    /// derived ids carry the deployed canonical table's outfits
    /// at the representative frames (the drift-guard property:
    /// HOT × CLEAR × MIDDAY is the canonical
    /// VISOR/TANK_TOP/SHORT_SHORTS/SUNGLASSES cell).
    func testCoreMatrix_TwentySettledCells() {
        let ids = CoreMatrixCells.ids(genderPreference: .male)
        XCTAssertEqual(ids.count, 20)
        XCTAssertEqual(Set(ids).count, 20, "all twenty ids distinct")
        for bracket in ["FREEZING", "COLD", "COOL", "MILD", "WARM", "HOT"] {
            XCTAssertTrue(ids.contains { $0.hasPrefix("MALE_CLEAR_\(bracket)_DAWN_") }, "\(bracket) CLEAR DAWN named")
            XCTAssertTrue(ids.contains { $0.hasPrefix("MALE_CLEAR_\(bracket)_DUSK_") }, "\(bracket) CLEAR DUSK named")
            XCTAssertTrue(ids.contains { $0.hasPrefix("MALE_RAIN_\(bracket)_DAWN_") }, "\(bracket) RAIN DAWN named")
        }
        XCTAssertTrue(ids.contains { $0.hasPrefix("MALE_CLEAR_WARM_MIDDAY_") })
        XCTAssertTrue(ids.contains { $0.hasPrefix("MALE_CLEAR_HOT_MIDDAY_") })
        XCTAssertFalse(ids.contains { $0.hasPrefix("MALE_CLEAR_FREEZING_MIDDAY_") },
                       "no other bracket gets a MIDDAY core cell")
        XCTAssertFalse(ids.contains { $0.contains("_NIGHT_") }, "the matrix has no NIGHT cells")
        let canonicalHotMidday = PersonalCombination(
            genderSegment: "MALE", weather: .CLEAR, temp: .HOT, time: .MIDDAY,
            outfitItems: ["VISOR", "TANK_TOP", "SHORT_SHORTS", "SUNGLASSES"]).combinationId
        XCTAssertTrue(ids.contains(canonicalHotMidday),
                      "the derived HOT MIDDAY id is the deployed canonical cell")
    }

    /// The composed fill set: forecast cells first (queued
    /// ahead), core cells behind, no duplicates — the exact list
    /// a fresh subscription names without the user opening
    /// anything (the coordinator's gate check 3, client half).
    func testFillComposition_ForecastFirstThenCore() {
        let core = CoreMatrixCells.ids(genderPreference: .male)
        let forecast = ["MALE_CLEAR_COOL_DAWN_00000000", core[0], "MALE_RAIN_MILD_NIGHT_11111111"]
        let named = FillSetComposition.named(forecastCells: forecast, coreCells: core)
        XCTAssertEqual(Array(named.prefix(2)), [forecast[0], forecast[2]],
                       "forecast cells lead, in order, core overlap deduped")
        XCTAssertEqual(Set(named), Set(forecast).union(core))
        XCTAssertEqual(named.count, Set(named).count, "no cell is named twice")
        XCTAssertTrue(Set(core).isSubset(of: Set(named)),
                      "the whole substrate is named even when the forecast never touches it")
    }

    /// Gate check 1 (client half): a winter subscriber who has
    /// never generated a hot cell opens HOT × CLEAR × MIDDAY.
    /// Under the matrix that cell is banked, so the serve is
    /// EXACT: the lookup for the core cell resolves to SHOW and
    /// the presentation is .exact, not scaffold.
    func testGateCheck1_CoreHotMiddayServesExact() throws {
        let core = CoreMatrixCells.ids(genderPreference: .male)
        let hotMidday = try XCTUnwrap(core.first { $0.hasPrefix("MALE_CLEAR_HOT_MIDDAY_") })
        let json = """
        {"found": true, "combination_id": "\(hotMidday)",
         "hero_id": "hero-hot", "variant": 1,
         "image_url": "https://example.invalid/hot-midday.jpg",
         "version": 1, "replenishment_queued": 0,
         "look_state": "ready", "look_reason": null}
        """
        let lookup = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(json.utf8))
        var cache = PersonalCellCache()
        guard case .show = cache.resolve(lookup: lookup, now: Date()) else {
            return XCTFail("a banked core cell must resolve to SHOW")
        }
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: true,
                                             lookState: PersonalLookState.from(lookup: lookup)),
            .exact, "a banked matrix cell serves exact, never scaffold")
    }

    /// Gate check 2 (client half): the same subscriber opens an
    /// OFF-matrix cell — HOT × RAIN × DUSK is not one of the
    /// twenty. The serve is his OWN banked same-prescription
    /// photo as scaffold (the deployed serve answers found=true
    /// with look_state "making" and queues the exact job
    /// server-side) — never a stock photo: the resolution is
    /// SHOW of his own photo, classified scaffold.
    func testGateCheck2_OffMatrixCellServesOwnScaffold() throws {
        let core = Set(CoreMatrixCells.ids(genderPreference: .male))
        let outfit = OutfitRecommendationService.shared.getRecommendation(
            for: 85, rawTemperature: 85, isRaining: true, windSpeed: 2,
            humidity: 50, uvIndex: 2, precipitation: 1.0, hour: 18)
        let offMatrix = PersonalCombination.make(
            genderPreference: .male, weatherCode: 61,
            feelsLikeFahrenheit: 85, hour: 18, outfit: outfit)
        XCTAssertFalse(core.contains(offMatrix.combinationId),
                       "HOT RAIN DUSK is off-matrix by construction")
        let json = """
        {"found": true, "combination_id": "\(offMatrix.combinationId)",
         "hero_id": "hero-scaffold", "variant": 1,
         "image_url": "https://example.invalid/own-scaffold.jpg",
         "version": 2, "replenishment_queued": 0,
         "look_state": "making", "look_reason": null}
        """
        let lookup = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(json.utf8))
        var cache = PersonalCellCache()
        guard case .show(let url) = cache.resolve(lookup: lookup, now: Date()) else {
            return XCTFail("the scaffold payload must resolve to SHOW — never a stock fall-through")
        }
        XCTAssertEqual(url, "https://example.invalid/own-scaffold.jpg")
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: true,
                                             lookState: PersonalLookState.from(lookup: lookup)),
            .scaffold(badge: PersonalServePolicy.scaffoldBadge))
    }

    /// Defect 1's contract, end to end at the client level: the
    /// fill call NAMES the stored forecast cells (source pins on
    /// the API body and the store's kick), and the grant path and
    /// the forecast-refresh path both reach the kick.
    func testFillContract_NamesTheForecastCells() throws {
        let api = try sourceFile("../RunWear/Services/PersonalHeroAPI.swift")
        XCTAssertTrue(api.contains("\"priority_cells\": priorityCells"),
                      "start-personal-fill is called with the named priority cells")
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("api.startPersonalFill(priorityCells: cells)"),
                      "the fill kick sends the stored forecast cells")
        XCTAssertTrue(store.contains("func updateForecastPriorityCells"),
                      "the store receives forecast cells through the resolving contract")
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("publishForecastPriorityCells()"),
                      "every forecast load publishes its cells")
        XCTAssertTrue(vm.contains("ForecastPriorityCells.ids(genderPreference: genderPreference"),
                      "the derivation runs over the loaded 7-day forecast")
        XCTAssertTrue(vm.contains("CoreMatrixCells.ids(genderPreference: genderPreference"),
                      "the composed set includes the canonical core matrix")
        XCTAssertTrue(vm.contains("FillSetComposition.named("),
                      "forecast cells lead and the substrate fills behind them")
        XCTAssertTrue(store.contains("if mode == .subscribed { kickFillWithRetry() }"),
                      "selfie-epoch changes (opt-in and replace) re-name the fill")
    }

    /// Item 3, the exact state: an entitled user opens a cell he
    /// does not own while his own same-prescription hero is
    /// banked. The deployed serve answers found=true with the
    /// scaffold photo and look_state "making" (and queues the
    /// priority-15 convergence job server-side). The client half
    /// of that contract: the lookup decodes, the cache resolves
    /// it to SHOW (never a stock fall-through), the folded look
    /// state classifies the paint as the scaffold presentation,
    /// and the serve path reaches the lookup unconditionally —
    /// the personal layer runs on every hero load, and inside
    /// the store the lookup precedes any generation-request gate.
    func testItem3_ScaffoldServeBranch_ExactState() throws {
        let json = """
        {"found": true, "combination_id": "MALE_CLEAR_COOL_DAWN_ab12cd34",
         "hero_id": "hero-9", "variant": 1,
         "image_url": "https://example.invalid/scaffold.jpg",
         "version": 3, "replenishment_queued": 0,
         "look_state": "making", "look_reason": null}
        """
        let lookup = try JSONDecoder().decode(PersonalHeroLookup.self, from: Data(json.utf8))
        XCTAssertEqual(lookup.lookState, "making")
        var cache = PersonalCellCache()
        let resolution = cache.resolve(lookup: lookup, now: Date())
        guard case .show(let url) = resolution else {
            return XCTFail("a scaffold payload must resolve to SHOW, got \(resolution)")
        }
        XCTAssertEqual(url, "https://example.invalid/scaffold.jpg")
        let lookState = PersonalLookState.from(lookup: lookup)
        XCTAssertEqual(lookState, .making)
        XCTAssertEqual(
            PersonalServePolicy.presentation(hasPersonalPhoto: true, lookState: lookState),
            .scaffold(badge: PersonalServePolicy.scaffoldBadge),
            "an own photo under a making look state IS the scaffold presentation")
        // The serve path cannot be short-circuited by the pool:
        // the personal layer runs after the pool cascade on every
        // load, and the store's lookup precedes its request gate.
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        let poolIndex = try XCTUnwrap(vm.range(of: "heroImageService.getHeroImage(")?.lowerBound)
        let personalIndex = try XCTUnwrap(vm.range(of: "await applyPersonalHero(frame: frame)")?.lowerBound)
        XCTAssertLessThan(poolIndex, personalIndex,
                          "the personal layer runs on every hero load, after the pool paints")
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        let lookupIndex = try XCTUnwrap(store.range(of: "api.getPersonalHero(combinationId: cell)")?.lowerBound)
        let requestIndex = try XCTUnwrap(store.range(of: "await requestGenerationIfNeeded(")?.lowerBound)
        XCTAssertLessThan(lookupIndex, requestIndex,
                          "the serve lookup happens before any generation-request decision")
    }

    func testBandStalledLine_ReplacesTheInProgressLineOnly() {
        let coverage = BankCoverage(ready: 43, total: 48)
        let stalled = ConvergenceBand.content(isSubscribed: true, coverage: coverage, stalled: true)
        XCTAssertEqual(stalled?.line, ConvergenceBand.stalledLine)
        XCTAssertEqual(stalled?.fraction, 43.0 / 48.0,
                       "the fraction stays the real banked fraction in the stalled state")
        XCTAssertEqual(stalled?.heading, "Making your looks")
    }

    func testBankStallTracker_AdvancementResetsTheClock() {
        var tracker = BankStallTracker()
        let t0 = Date(timeIntervalSince1970: 1_700_000_000)
        let partial = BankCoverage(ready: 10, total: 48)
        tracker.observe(partial, now: t0)
        XCTAssertFalse(tracker.isStalled(partial, now: t0.addingTimeInterval(29 * 60)))
        XCTAssertTrue(tracker.isStalled(partial, now: t0.addingTimeInterval(31 * 60)),
                      "no advancement for the threshold reads as stalled")
        // Advancement restarts the clock:
        let advanced = BankCoverage(ready: 11, total: 48)
        tracker.observe(advanced, now: t0.addingTimeInterval(31 * 60))
        XCTAssertFalse(tracker.isStalled(advanced, now: t0.addingTimeInterval(59 * 60)))
        // A completed bank is never stalled:
        let complete = BankCoverage(ready: 48, total: 48)
        XCTAssertFalse(tracker.isStalled(complete, now: t0.addingTimeInterval(999 * 60)))
    }

    // MARK: - RW-29 batch pins

    func testTolerantDecoding_OneMalformedRecordDropsOnlyItself() throws {
        // FA-14: a whole-history JSON array with one corrupt
        // record between two valid ones — both neighbours load.
        let good1 = """
        {"dayStart": 500000000, "placeName": "Miami, Florida",
         "feelsLikeF": 78, "actualF": 73, "condition": "Clear",
         "itemNames": [], "itemKeys": [], "heroImageReference": "",
         "sessionID": "", "wasUpdatedInSession": false}
        """
        let good2 = """
        {"dayStart": 500100000, "placeName": "Kansas City, Missouri",
         "feelsLikeF": 36, "actualF": 31, "condition": "Clear",
         "itemNames": [], "itemKeys": [], "heroImageReference": "",
         "sessionID": "", "wasUpdatedInSession": false}
        """
        let data = Data("[\(good1), {\"dayStart\": \"not-a-date\"}, \(good2)]".utf8)
        let decoded = TolerantArrayDecoding.decode(WearIveRunEntry.self, from: data)
        XCTAssertEqual(decoded.count, 2)
        XCTAssertEqual(Set(decoded.map(\.placeDisplayName)), ["Miami, FL", "Kansas City, MO"])
    }

    func testLegacyFallbackAndSupersede_FA36() {
        // FA-36: a legacy (session-less) entry dated today is
        // the pin/dock's entry until the session settles; the
        // session's first settle at the same day+place
        // supersedes it — one entry for the day, not two. A
        // legacy entry at a DIFFERENT place is untouched.
        let legacyToday = entry(place: "Miami, Florida", day: nov5, feels: 78)
        let legacyElsewhere = entry(place: "Kansas City, Missouri", day: nov4, feels: 36)
        let found = WearIveRunCollection.sessionEntry(
            from: [legacyToday, legacyElsewhere], sessionID: "new-session", today: nov5)
        XCTAssertEqual(found?.id, legacyToday.id)

        let suite = UserDefaults(suiteName: "gen5-fa36-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: suite)
        store.record(legacyToday)
        store.record(legacyElsewhere)
        let model = WearIveRunCollectionModel(store: store, sessionID: "new-session")
        let sessionSettle = entry(place: "Miami, Florida", day: nov5, feels: 79, session: "new-session")
        model.recordSettled(sessionSettle)
        let miamiToday = model.entries.filter {
            $0.placeKey == sessionSettle.placeKey
                && Calendar.current.isDate($0.dayStart, inSameDayAs: nov5)
        }
        XCTAssertEqual(miamiToday.count, 1, "the day keeps ONE Miami entry — the session's")
        XCTAssertEqual(miamiToday.first?.sessionID, "new-session")
        XCTAssertTrue(model.entries.contains { $0.id == legacyElsewhere.id },
                      "the other place's legacy entry is history and stays")
    }

    func testDayChartGeometry_PartialDayUsesTheFixedDayScale_FA29() throws {
        // FA-29: a forecast covering only hours 14...23 must
        // place hour 14 at 14/23 of the width — the fixed day
        // scale — not at the model's own left edge.
        let points = (14...23).map { hour in
            DayChartHour(hour: hour, feelsLikeF: 50 + Double(hour), bracket: .mild, outfitItemNames: [])
        }
        let model = try XCTUnwrap(DayChartModel.build(points: points))
        let x = DayChartGeometry.x(forHour: 14, model: model, width: 230)
        XCTAssertEqual(Double(x), 230.0 * 14.0 / 23.0, accuracy: 0.001)
        XCTAssertEqual(Double(DayChartGeometry.x(forHour: 23, model: model, width: 230)), 230, accuracy: 0.001)
        XCTAssertEqual(Double(DayChartGeometry.x(forHour: 0, model: model, width: 230)), 0, accuracy: 0.001)
    }

    func testTimePickerDraft_SteppersDisableAtTheEnds_FA39() {
        var draft = TimePickerDraft(
            originalHour: 6, openedInNowState: true, nowHour: 6,
            selectableHours: Array(0...23))
        XCTAssertTrue(draft.canStep(by: 1))
        XCTAssertTrue(draft.canStep(by: -1))
        draft.move(to: 0)
        XCTAssertFalse(draft.canStep(by: -1), "the minus stepper disables at the first selectable hour")
        XCTAssertTrue(draft.canStep(by: 1))
        draft.move(to: 23)
        XCTAssertFalse(draft.canStep(by: 1), "the plus stepper disables at the last selectable hour")
    }

    func testPhotoCheckReport_ReportsTheFourChecksIndividually_FA54() {
        // A dark photo with a well-framed, upright face: Light
        // fails, the other three pass — reported individually,
        // from the same thresholds the verdict uses.
        let report = PhotoCheckReport.report(
            faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.05, faceRollDegrees: 0)
        XCTAssertTrue(report.passed(.faceFound))
        XCTAssertTrue(report.passed(.faceLargeEnough))
        XCTAssertTrue(report.passed(.faceFrontal))
        XCTAssertFalse(report.passed(.wellLit))
        let small = PhotoCheckReport.report(
            faceCount: 1, largestFaceAreaRatio: 0.005, meanLuminance: 0.5, faceRollDegrees: 0)
        XCTAssertFalse(small.passed(.faceLargeEnough))
        XCTAssertTrue(small.passed(.wellLit))
        let sideways = PhotoCheckReport.report(
            faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.5, faceRollDegrees: 80)
        XCTAssertFalse(sideways.passed(.faceFrontal))
        let nobody = PhotoCheckReport.report(
            faceCount: 0, largestFaceAreaRatio: 0, meanLuminance: 0.5, faceRollDegrees: 0)
        XCTAssertFalse(nobody.passed(.faceFound))
        XCTAssertFalse(nobody.passed(.faceLargeEnough))
    }

    func testFlagReasonLabels_AreTheSettledPresentation_FA18() throws {
        XCTAssertEqual(HeroFlagReason.outfitMismatch.label, "Wrong outfit for the weather")
        XCTAssertEqual(HeroFlagReason.looksBroken.label, "Unflattering photo")
        XCTAssertEqual(HeroFlagReason.inappropriate.label, "Other")
        XCTAssertEqual(HeroFlagReason.sheetOrder, [.outfitMismatch, .looksBroken, .inappropriate])
        XCTAssertEqual(PersonalHeroFlagReason.doesntLookLikeMe.label, "This doesn't look like me")
        XCTAssertEqual(PersonalHeroFlagReason.outfitMismatch.label, "Wrong outfit for the weather")
        XCTAssertEqual(PersonalHeroFlagReason.looksBroken.label, "Something else")
        XCTAssertEqual(PersonalHeroFlagReason.inappropriate.label, "Other")
        XCTAssertEqual(PersonalHeroFlagReason.sheetOrder,
                       [.doesntLookLikeMe, .outfitMismatch, .looksBroken],
                       "frame 45's personal sheet carries exactly three reasons")
        XCTAssertEqual(PersonalHeroFlagReason.consequenceLine,
                       "We'll stop showing this photo and make it again.")
        // The BACKEND codes are unchanged — presentation only.
        XCTAssertEqual(HeroFlagReason.outfitMismatch.code, "outfit_mismatch")
        XCTAssertEqual(PersonalHeroFlagReason.looksBroken.code, "looks_broken")
    }

    func testCollectionEmptyState_IsFrame10Verbatim_FA16() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("Your scrapbook starts with today's run."))
        XCTAssertTrue(collection.contains("Each day's outfit is saved here automatically — one card per day, in the places you've run. Nothing is backfilled. It begins the first time today's outfit is generated."))
        XCTAssertFalse(collection.contains("Nothing here yet"),
                       "the invented session-framed empty state is gone")
    }
}
