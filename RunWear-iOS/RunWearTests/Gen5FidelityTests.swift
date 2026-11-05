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
        moment: String = "Now", savedAt: Date? = nil
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
            "Miami, FL")
    }

    func testNarrationPayload_TimeChangeNamesTheReading() {
        let before = entry(place: "Miami, Florida", day: nov5, feels: 78, moment: "Now")
        let after = entry(place: "Miami, Florida", day: nov5, feels: 83,
                          items: ["Light Long Sleeve"], moment: "9 p.m.")
        XCTAssertEqual(
            WearIveRunNarrationRules.updatePayload(existing: before, incoming: after),
            "9 p.m.")
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
                           items: ["Tank Top"], moment: "9 p.m.")
        let after = entry(place: "Miami, Florida", day: nov5, feels: 78,
                          items: ["Light Long Sleeve"], moment: "9 p.m.")
        let payload = WearIveRunNarrationRules.updatePayload(existing: before, incoming: after)
        XCTAssertEqual(payload, "9 p.m.")
        XCTAssertFalse(payload.contains("Sleeve"))
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
        XCTAssertEqual(decoded.momentReading, "Now")
        XCTAssertNil(decoded.updatePayload)
        XCTAssertEqual(decoded.narrationPayload, "Miami, FL")
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
}
