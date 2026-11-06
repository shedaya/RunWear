import XCTest
import SwiftUI
import UIKit

/// GEN-6 Wave B — RW-31 unified share implementation pins
/// (DA-04 / DA-05 / DA-06 / DA-16 / DA-17):
///
///   * the caption composer: exact template strings, the
///     why-line library's deterministic selection, the picker
///     ordering, and the honesty rule — personal-day and
///     stock-day fixtures produce IDENTICAL caption sets,
///     because no template describes or claims the photograph;
///   * the moment forms (§5.1): same-day lower-case, stamped
///     past chapters, planned-hour;
///   * the face-anchor crop math (§4.3): face in the upper
///     third, ≥ 8% headroom, per canvas, including an
///     off-centre face;
///   * export rendering + the raster verify gate (DA-16):
///     rendered exports at all three sizes land on exact
///     export pixels and pass inspection; a blanked input
///     FAILS and can never be sent;
///   * the Kit Card structure (§4.5): wearing order, per-tile
///     Shop ›, the disclosure, no prices;
///   * the day record's null-safe share fields.
final class Gen6WaveBShareTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - Caption fixtures
    //
    // The Kansas City day of the design mockups. The composer
    // input carries NO photograph information at all — that is
    // the §4.4 honesty mechanism — so the "personal" and
    // "stock" fixtures below are the same day record, and their
    // caption sets must be identical.

    private var kansasCityInput: ShareCaptionInput {
        ShareCaptionInput(
            place: "Kansas City, MO",
            captionMoment: "this morning",
            isPastChapter: false,
            feels: 57, actual: 61, low: 55, high: 63,
            swingF: 8, feelsF: 57,
            conditionLower: "clear sky",
            kitNames: ["Short-sleeve shirt", "Running shorts", "Sunglasses"]
        )
    }

    private let kansasCityKit = "• Short-sleeve shirt\n• Running shorts\n• Sunglasses"

    func testCaptions_KansasCityFixture_allFourTemplatesExact() {
        let input = kansasCityInput
        XCTAssertEqual(
            ShareCaptionComposer.caption(for: .feelsLikeStory, input: input),
            "Kansas City, MO, this morning: 61° on the thermometer, 57° on the run. RunWear's call:\n\(kansasCityKit)\nDressed for the feels-like, not the thermometer. #RunAnyWear · runwear.ai")
        XCTAssertEqual(
            ShareCaptionComposer.caption(for: .swing, input: input),
            "Kansas City, MO today: 55° to 63°. Dressed for this morning's 57°:\n\(kansasCityKit)\nOne outfit, no mid-run regret. #RunAnyWear · runwear.ai")
        XCTAssertEqual(
            ShareCaptionComposer.caption(for: .scrapbook, input: input),
            "One for the scrapbook: Kansas City, MO, this morning, 57° feels like, clear sky. The kit:\n\(kansasCityKit)\nEvery run, remembered — run, weather or not. #RunAnyWear · @runwear.ai")
        XCTAssertEqual(
            ShareCaptionComposer.caption(for: .shortCall, input: input),
            "Kansas City, MO, this morning: 57° feels like, clear sky. The call:\n\(kansasCityKit)\n#RunAnyWear · @runwear.ai")
    }

    func testCaptions_personalAndStockDays_produceIdenticalSets() {
        // Two day records with identical day data — one a
        // personal (Make It Me) day, one a stock day. The
        // composer has no input that could distinguish them, so
        // the sets are identical BY CONSTRUCTION; this pin
        // fails if a photograph-describing field ever enters
        // the input.
        let personalDay = kansasCityInput
        let stockDay = kansasCityInput
        for template in ShareCaptionTemplate.allCases {
            XCTAssertEqual(
                ShareCaptionComposer.caption(for: template, input: personalDay),
                ShareCaptionComposer.caption(for: template, input: stockDay),
                "\(template) must not change between personal and stock days")
        }
        XCTAssertEqual(
            ShareCaptionComposer.pickerOrder(for: personalDay),
            ShareCaptionComposer.pickerOrder(for: stockDay))
    }

    func testCaptions_neverClaimThePhotograph_andObeyVoiceRules() {
        for template in ShareCaptionTemplate.allCases {
            let text = ShareCaptionComposer.caption(for: template, input: kansasCityInput)
            let lowered = text.lowercased()
            for claim in ["my photo", "your photo", "photo of me", "i wore", "picture of"] {
                XCTAssertFalse(lowered.contains(claim),
                               "\(template) must never describe or claim the photograph: \(text)")
            }
            XCTAssertFalse(text.contains("?"), "\(template): no interrogative hooks (§5.3)")
            XCTAssertFalse(text.contains("!"), "\(template): no exclamation marks (§5.3)")
            XCTAssertEqual(text.components(separatedBy: "#RunAnyWear").count - 1, 1,
                           "\(template): the hashtag travels exactly once, as text")
        }
    }

    func testCaptions_kitIsBulletsAndCappedAtFour() {
        var input = kansasCityInput
        input = ShareCaptionInput(
            place: input.place, captionMoment: input.captionMoment,
            isPastChapter: input.isPastChapter, feels: input.feels,
            actual: input.actual, low: input.low, high: input.high,
            swingF: input.swingF, feelsF: input.feelsF,
            conditionLower: input.conditionLower,
            kitNames: ["A", "B", "C", "D", "E", "F"])
        let text = ShareCaptionComposer.caption(for: .shortCall, input: input)
        XCTAssertTrue(text.contains("• A\n• B\n• C\n• D"), text)
        XCTAssertFalse(text.contains("• E"), "the headline kit caps at four garments")
    }

    // MARK: - Why-line library (deterministic, both platforms)

    func testWhyLine_swingWinsAtFifteenOrMore() {
        var input = kansasCityInput
        input = ShareCaptionInput(
            place: input.place, captionMoment: input.captionMoment,
            isPastChapter: false, feels: 60, actual: 66, low: 50, high: 70,
            swingF: 20, feelsF: 60, conditionLower: "cloudy", kitNames: input.kitNames)
        XCTAssertEqual(ShareCaptionComposer.whyLine(for: input), "One outfit for a 20-degree swing.")
    }

    func testWhyLine_swingBeatsCold() {
        let input = ShareCaptionInput(
            place: "Chicago, IL", captionMoment: "this morning",
            isPastChapter: false, feels: 30, actual: 36, low: 28, high: 48,
            swingF: 20, feelsF: 30, conditionLower: "snow", kitNames: ["Thermal tights"])
        XCTAssertEqual(ShareCaptionComposer.whyLine(for: input), "One outfit for a 20-degree swing.")
    }

    func testWhyLine_coldAtFortyOrBelow() {
        let input = ShareCaptionInput(
            place: "Chicago, IL", captionMoment: "this morning",
            isPastChapter: false, feels: 34, actual: 38, low: 32, high: 40,
            swingF: 8, feelsF: 34, conditionLower: "snow", kitNames: ["Thermal tights"])
        XCTAssertEqual(ShareCaptionComposer.whyLine(for: input), "No guesswork at 34°.")
    }

    func testWhyLine_defaultIsTheAnchorLine() {
        XCTAssertEqual(ShareCaptionComposer.whyLine(for: kansasCityInput),
                       "Dressed for the feels-like, not the thermometer.")
    }

    // MARK: - Picker ordering

    func testPickerOrder_defaultLeadsFeelsLikeStory_shortCallLast() {
        XCTAssertEqual(ShareCaptionComposer.pickerOrder(for: kansasCityInput),
                       [.feelsLikeStory, .swing, .scrapbook, .shortCall])
    }

    func testPickerOrder_swingDayLeadsSwing() {
        let input = ShareCaptionInput(
            place: "Denver, CO", captionMoment: "this morning",
            isPastChapter: false, feels: 60, actual: 66, low: 50, high: 70,
            swingF: 20, feelsF: 60, conditionLower: "sunny", kitNames: ["Shorts"])
        XCTAssertEqual(ShareCaptionComposer.pickerOrder(for: input),
                       [.swing, .feelsLikeStory, .scrapbook, .shortCall])
    }

    func testPickerOrder_pastChapterLeadsScrapbook_evenOnASwingDay() {
        let input = ShareCaptionInput(
            place: "Miami, FL", captionMoment: "Thursday, November 5",
            isPastChapter: true, feels: 88, actual: 91, low: 70, high: 92,
            swingF: 22, feelsF: 88, conditionLower: "sunny", kitNames: ["Tank top"])
        XCTAssertEqual(ShareCaptionComposer.pickerOrder(for: input),
                       [.scrapbook, .feelsLikeStory, .swing, .shortCall])
    }

    func testSubjectAndLinkTitle_exactForms() {
        XCTAssertEqual(ShareCaptionComposer.subject(for: kansasCityInput),
                       "RunWear — Kansas City, MO, 57° feels like")
        XCTAssertEqual(ShareCaptionComposer.linkTitle(for: kansasCityInput),
                       "RunWear — Kansas City, MO · 57° feels like")
    }

    // MARK: - Moment forms (§5.1)

    private func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    private func date(_ calendar: Calendar, _ year: Int, _ month: Int, _ day: Int, _ hour: Int = 12) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    func testMoment_sameDayNow_morningAndTonight() {
        let calendar = utcCalendar()
        let day = date(calendar, 2026, 11, 5)
        let morning = ShareMomentResolver.forms(
            momentReading: .now, dayStart: day,
            lastSavedAt: date(calendar, 2026, 11, 5, 7), now: date(calendar, 2026, 11, 5, 9),
            calendar: calendar)
        XCTAssertEqual(morning.caption, "this morning")
        XCTAssertEqual(morning.poster, "This morning")
        XCTAssertFalse(morning.isPastChapter)

        let evening = ShareMomentResolver.forms(
            momentReading: .now, dayStart: day,
            lastSavedAt: date(calendar, 2026, 11, 5, 19), now: date(calendar, 2026, 11, 5, 20),
            calendar: calendar)
        XCTAssertEqual(evening.caption, "tonight")
        XCTAssertEqual(evening.poster, "Tonight")
    }

    func testMoment_plannedHourToday_usesTodayClockForm() {
        let calendar = utcCalendar()
        let forms = ShareMomentResolver.forms(
            momentReading: .clock(hour12: 3, isPM: true),
            dayStart: date(calendar, 2026, 11, 5),
            lastSavedAt: date(calendar, 2026, 11, 5, 9),
            now: date(calendar, 2026, 11, 5, 9),
            calendar: calendar)
        XCTAssertEqual(forms.caption, "today, 3:00 PM")
        XCTAssertEqual(forms.poster, "Today, 3:00 PM")
        XCTAssertFalse(forms.isPastChapter)
    }

    func testMoment_pastChapter_usesTheStampedForm() {
        let calendar = utcCalendar()
        // Thursday, November 5, 2026, read back on November 7.
        let forms = ShareMomentResolver.forms(
            momentReading: .now,
            dayStart: date(calendar, 2026, 11, 5),
            lastSavedAt: date(calendar, 2026, 11, 5, 7),
            now: date(calendar, 2026, 11, 7),
            calendar: calendar)
        XCTAssertEqual(forms.caption, "Thursday, November 5")
        XCTAssertEqual(forms.poster, "Thursday, November 5")
        XCTAssertTrue(forms.isPastChapter)
    }

    // MARK: - Face anchor + crop math (§4.3)

    func testAnchor_defaultIsUpperMiddle_neverTheCentre() {
        XCTAssertEqual(ShareFaceAnchor.defaultUpperMiddle.centerX, 0.5)
        XCTAssertLessThan(ShareFaceAnchor.defaultUpperMiddle.centerY, 0.25,
                          "the default anchor is the upper-middle composition point")
        XCTAssertNotEqual(ShareFaceAnchor.defaultUpperMiddle.centerY, 0.5,
                          "never the geometric centre")
    }

    func testAnchorCrop_centredFace_upperThirdWithHeadroom_onEveryCanvas() {
        let imageSize = CGSize(width: 1000, height: 1500)
        let anchor = ShareFaceAnchor(centerX: 0.5, centerY: 0.16, width: 0.30, height: 0.12)
        for format in ShareExportFormat.allCases {
            let rect = ShareAnchorCrop.cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: format.aspect)
            XCTAssertEqual(rect.width / rect.height, format.aspect, accuracy: 0.01,
                           "\(format): the window carries the canvas aspect")
            let placed = ShareAnchorCrop.placement(cropRect: rect, anchor: anchor, imageSize: imageSize)
            XCTAssertLessThanOrEqual(placed.faceCenterYFraction, 1.0 / 3.0 + 0.001,
                                     "\(format): the face sits in the upper third")
            XCTAssertGreaterThanOrEqual(placed.headroomFraction, 0.08 - 0.001,
                                        "\(format): at least 8% headroom above the head")
            XCTAssertTrue(ShareAnchorCrop.placementIsValid(cropRect: rect, anchor: anchor, imageSize: imageSize),
                          "\(format): the placement honours the rule")
        }
    }

    func testAnchorCrop_offCentreFace_stillUpperThirdWithHeadroom() {
        // The gen-5 failure shape: a face well off centre —
        // right side, lower than the pool's usual band.
        let imageSize = CGSize(width: 1200, height: 1600)
        let anchor = ShareFaceAnchor(centerX: 0.78, centerY: 0.30, width: 0.24, height: 0.15)
        for format in ShareExportFormat.allCases {
            let rect = ShareAnchorCrop.cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: format.aspect)
            XCTAssertEqual(rect.width / rect.height, format.aspect, accuracy: 0.01, "\(format)")
            // The face is fully inside the window — never clipped.
            let faceTop = CGFloat(anchor.topNormalized) * imageSize.height
            let faceBottom = faceTop + CGFloat(anchor.height) * imageSize.height
            XCTAssertGreaterThanOrEqual(faceTop, rect.minY - 1, "\(format): the head is not clipped")
            XCTAssertLessThanOrEqual(faceBottom, rect.maxY + 1, "\(format): the chin is not clipped")
            let placed = ShareAnchorCrop.placement(cropRect: rect, anchor: anchor, imageSize: imageSize)
            XCTAssertLessThanOrEqual(placed.faceCenterYFraction, 1.0 / 3.0 + 0.001,
                                     "\(format): off-centre face still lands in the upper third")
            XCTAssertGreaterThanOrEqual(placed.headroomFraction, 0.08 - 0.001,
                                        "\(format): off-centre face keeps its headroom")
            // The window follows the face sideways, too.
            let faceCenterCanvasX = (CGFloat(anchor.centerX) * imageSize.width - rect.minX) / rect.width
            XCTAssertGreaterThan(faceCenterCanvasX, 0.5, "\(format): the window tracks the face horizontally")
        }
    }

    func testAnchorCrop_storiesExtraHeightGoesBelowTheRunner() {
        // Same image, same anchor: the Stories window must not
        // sit HIGHER than the Feed window — extra height goes
        // to the scene below, never to sky above the head.
        let imageSize = CGSize(width: 1000, height: 2000)
        let anchor = ShareFaceAnchor(centerX: 0.5, centerY: 0.14, width: 0.26, height: 0.12)
        let feed = ShareAnchorCrop.cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: ShareExportFormat.feed.aspect)
        let stories = ShareAnchorCrop.cropRect(imageSize: imageSize, anchor: anchor, canvasAspect: ShareExportFormat.stories.aspect)
        XCTAssertLessThanOrEqual(stories.minY, feed.minY + 1)
        XCTAssertGreaterThanOrEqual(
            ShareAnchorCrop.placement(cropRect: stories, anchor: anchor, imageSize: imageSize).headroomFraction,
            0.08 - 0.001)
    }

    func testAnchorCroppedImage_carriesTheCanvasAspect() {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let hero = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 600), format: format).image { context in
            UIColor.green.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 400, height: 600))
            UIColor.blue.setFill()
            context.fill(CGRect(x: 280, y: 60, width: 80, height: 90)) // off-centre "face"
        }
        let anchor = ShareFaceAnchor(centerX: 0.8, centerY: 0.175, width: 0.2, height: 0.15)
        let cropped = ShareAnchorCrop.anchoredCropped(hero, anchor: anchor, canvasAspect: ShareExportFormat.feed.aspect)
        XCTAssertEqual(cropped.size.width / cropped.size.height,
                       ShareExportFormat.feed.aspect, accuracy: 0.02)
    }

    // MARK: - Export rendering + verify gate (DA-16)

    /// A synthetic card with real image structure: a diagonal
    /// gradient, garment-like blocks, and text — enough variance
    /// in every region that a healthy raster passes inspection.
    private struct SyntheticCard: View {
        let format: ShareExportFormat
        var body: some View {
            ZStack {
                LinearGradient(
                    colors: [Color(red: 0.16, green: 0.14, blue: 0.12),
                             Color(red: 0.85, green: 0.80, blue: 0.70)],
                    startPoint: .topLeading, endPoint: .bottomTrailing)
                VStack(spacing: 24) {
                    Circle().fill(Color(red: 0.9, green: 0.75, blue: 0.6))
                        .frame(width: 90, height: 90)
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color(red: 0.13, green: 0.11, blue: 0.09))
                        .frame(width: 220, height: 160)
                        .overlay(Text("Garment").foregroundColor(.white))
                    Text("57° feels like")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(30)
            }
            .frame(width: format.canvasPointSize.width, height: format.canvasPointSize.height)
        }
    }

    private func uniformImage(size: CGSize, color: UIColor) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            color.setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
    }

    @MainActor
    func testExportRender_allThreeSizes_exactPixels_andVerifyPasses() {
        for format in ShareExportFormat.allCases {
            let image = ShareExportRenderer.render(SyntheticCard(format: format), format: format)
            let rendered = try! XCTUnwrap(image, "\(format): the export renders")
            let cg = try! XCTUnwrap(rendered.cgImage)
            XCTAssertEqual(cg.width, Int(format.exportPixelSize.width),
                           "\(format): exact export width")
            XCTAssertEqual(cg.height, Int(format.exportPixelSize.height),
                           "\(format): exact export height")
            let verdict = ShareRasterVerifier.verify(
                image: rendered, format: format,
                samplePoints: [CGPoint(x: 0.5, y: 0.24)])
            XCTAssertTrue(verdict.passed,
                          "\(format): a healthy raster passes — \(String(describing: verdict.failure))")
            XCTAssertGreaterThan(verdict.standardDeviation, ShareRasterVerifier.minimumGlobalDeviation)
        }
    }

    func testVerify_blankedInputs_fail() {
        for format in ShareExportFormat.allCases {
            let size = format.exportPixelSize
            let white = ShareRasterVerifier.verify(image: uniformImage(size: size, color: .white), format: format)
            XCTAssertFalse(white.passed, "\(format): a blank white raster can never be sent")
            let black = ShareRasterVerifier.verify(image: uniformImage(size: size, color: .black), format: format)
            XCTAssertFalse(black.passed, "\(format): a blank black raster can never be sent")
            let flat = ShareRasterVerifier.verify(image: uniformImage(size: size, color: .gray), format: format)
            XCTAssertFalse(flat.passed, "\(format): a flat raster can never be sent")
        }
        let missing = ShareRasterVerifier.verify(image: nil, format: .feed)
        XCTAssertEqual(missing.failure, .missingImage)
    }

    func testVerify_wrongSize_fails() {
        let image = uniformImage(size: CGSize(width: 200, height: 250), color: .red)
        let verdict = ShareRasterVerifier.verify(image: image, format: .feed)
        XCTAssertFalse(verdict.passed)
        guard case .wrongSize = verdict.failure else {
            return XCTFail("expected wrongSize, got \(String(describing: verdict.failure))")
        }
    }

    func testVerify_blackSquareRegion_fails_atGridAndAtTileSample() {
        // The Sol-recording signature: a healthy card carrying
        // one uniform black square where a garment render
        // should be.
        let size = ShareExportFormat.feed.exportPixelSize
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            // Diagonal gradient ground (healthy variance).
            let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [UIColor(red: 0.2, green: 0.18, blue: 0.16, alpha: 1).cgColor,
                         UIColor(red: 0.9, green: 0.86, blue: 0.78, alpha: 1).cgColor] as CFArray,
                locations: [0, 1])!
            context.cgContext.drawLinearGradient(
                gradient, start: .zero, end: CGPoint(x: size.width, y: size.height), options: [])
            // The black square, centred on the first tile sample.
            UIColor.black.setFill()
            let tilePoint = ShareKitCardModel.tileSamplePoints(format: .feed, itemCount: 2)[0]
            let centre = CGPoint(x: tilePoint.x * size.width, y: tilePoint.y * size.height)
            context.fill(CGRect(x: centre.x - 260, y: centre.y - 260, width: 520, height: 520))
        }
        let tilePoints = ShareKitCardModel.tileSamplePoints(format: .feed, itemCount: 2)
        let verdict = ShareRasterVerifier.verify(image: image, format: .feed, samplePoints: tilePoints)
        XCTAssertFalse(verdict.passed, "a uniform black square in the raster must fail verification")
        guard case .uniformRegion = verdict.failure else {
            return XCTFail("expected uniformRegion, got \(String(describing: verdict.failure))")
        }
    }

    func testVerify_creamKitGround_isNotABlankFailure() {
        // The Kit Card's designed ground is cream: a cream
        // raster with real tile content must not be failed as
        // "blank" by the grid scan (its degenerate whole-raster
        // form still fails — see the blanked-input pin).
        let size = ShareExportFormat.feed.exportPixelSize
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor(red: 0.992, green: 0.984, blue: 0.965, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))
            // Two charcoal tiles with lighter garment shapes.
            for point in ShareKitCardModel.tileSamplePoints(format: .feed, itemCount: 2) {
                let centre = CGPoint(x: point.x * size.width, y: point.y * size.height)
                UIColor(red: 0.13, green: 0.11, blue: 0.09, alpha: 1).setFill()
                context.fill(CGRect(x: centre.x - 200, y: centre.y - 200, width: 400, height: 400))
                UIColor(red: 0.75, green: 0.7, blue: 0.6, alpha: 1).setFill()
                context.fill(CGRect(x: centre.x - 90, y: centre.y - 120, width: 180, height: 240))
            }
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 60, y: 40, width: 500, height: 60))
        }
        let verdict = ShareRasterVerifier.verify(
            image: image, format: .feed,
            samplePoints: ShareKitCardModel.tileSamplePoints(format: .feed, itemCount: 2))
        XCTAssertTrue(verdict.passed, "the cream kit ground with real tiles passes — \(String(describing: verdict.failure))")
    }

    // MARK: - Poster composition (§4.2)

    func testPosterTierLines_exactForms() {
        XCTAssertEqual(
            SharePosterModel.tier2Line(place: "Miami, FL", posterMoment: "This morning"),
            "Miami, FL · This morning.")
        XCTAssertEqual(
            SharePosterModel.tier3Line(kitNames: ["Tank top", "Short shorts", "Visor", "Reflective gear", "Extra"]),
            "Tank top · Short shorts · Visor · Reflective gear")
        XCTAssertEqual(
            SharePosterModel.tier4Line(condition: "Clear sky", actualDegrees: 61, windMph: 5),
            "Clear sky · Actual 61° · Wind 5 mph")
    }

    func testPoster_microLineYieldsFirst_neverTheOutfit() {
        let longOutfit = SharePosterModel.tier3Line(
            kitNames: ["Merino wool long sleeve base layer", "Insulated waterproof windbreaker jacket", "Thermal tights", "Trail shoes"])
        XCTAssertFalse(SharePosterModel.showsMicroLine(format: .square, tier3Text: longOutfit),
                       "on the Square canvas under pressure the micro-line yields")
        XCTAssertTrue(SharePosterModel.showsMicroLine(format: .feed, tier3Text: longOutfit),
                      "the Feed canvas holds all four tiers")
        XCTAssertTrue(SharePosterModel.showsMicroLine(format: .square, tier3Text: "Tank · Shorts"),
                      "a short outfit keeps the micro-line even on Square")
    }

    func testPoster_storiesSafeZones_andFoldedFooter() {
        XCTAssertEqual(SharePosterModel.lockupTopInset(format: .stories) * ShareExportFormat.stories.renderScale,
                       250, accuracy: 0.5, "the lockup begins below the top 250 px at export scale")
        XCTAssertEqual(SharePosterModel.blockBottomInset(format: .stories) * ShareExportFormat.stories.renderScale,
                       340, accuracy: 0.5, "the block ends above the bottom 340 px at export scale")
        XCTAssertTrue(SharePosterModel.footerFoldsIntoBlock(format: .stories))
        XCTAssertFalse(SharePosterModel.footerFoldsIntoBlock(format: .feed))
        XCTAssertFalse(SharePosterModel.footerFoldsIntoBlock(format: .square))
    }

    // MARK: - Kit Card structure (§4.5)

    func testKitCard_wearingOrderIsTheEntrysOrder() {
        let items = ShareKitCardModel.orderedItems(
            names: ["Visor", "Tank top", "Short shorts", "Sunglasses"],
            keys: ["VISOR", "TANK_TOP", "SHORT_SHORTS", "SUNGLASSES"])
        XCTAssertEqual(items.map(\.name), ["Visor", "Tank top", "Short shorts", "Sunglasses"],
                       "the grid preserves the existing outfit ordering verbatim — nothing re-sorts")
        XCTAssertEqual(items.map(\.key), ["VISOR", "TANK_TOP", "SHORT_SHORTS", "SUNGLASSES"])
    }

    func testKitCard_notesRideFeedAndSquare_yieldOnStoriesUnderPressure() {
        XCTAssertTrue(ShareKitCardModel.showsNotes(format: .feed, itemCount: 8))
        XCTAssertTrue(ShareKitCardModel.showsNotes(format: .square, itemCount: 8))
        XCTAssertTrue(ShareKitCardModel.showsNotes(format: .stories, itemCount: 4))
        XCTAssertFalse(ShareKitCardModel.showsNotes(format: .stories, itemCount: 5),
                       "on Stories a tall kit drops its notes first — names stand alone")
    }

    func testKitCard_gridLayout_fitsEveryCanvas_andYieldsNotesUnderPressure() {
        // The effective layout: notes survive where geometry
        // allows (Feed at 3 items; Square at 2), and yield on
        // the Square canvas at 3 items, where two rows of
        // width-class tiles plus full label blocks physically
        // exceed 360 pt of height. Tiles stay inside the
        // canvas on every format and count.
        XCTAssertTrue(ShareKitCardModel.gridLayout(format: .feed, itemCount: 3).showsNotes)
        XCTAssertTrue(ShareKitCardModel.gridLayout(format: .square, itemCount: 2).showsNotes)
        XCTAssertFalse(ShareKitCardModel.gridLayout(format: .square, itemCount: 3).showsNotes,
                       "Square yields notes at 3 items — the design's yield rule under real geometry")
        for format in ShareExportFormat.allCases {
            for count in 1...ShareKitCardModel.maxItems(for: format) {
                let layout = ShareKitCardModel.gridLayout(format: format, itemCount: count)
                let rows = CGFloat((count + 1) / 2)
                let totalHeight = ShareKitCardModel.kitVerticalPadding
                    + ShareKitCardModel.kitHeaderHeight + ShareKitCardModel.kitFooterHeight
                    + rows * layout.tileSide + rows * layout.labelHeight
                    + (rows - 1) * ShareKitCardModel.kitRowSpacing
                XCTAssertLessThanOrEqual(totalHeight, format.canvasPointSize.height + 6,
                                         "\(format) with \(count) items must fit its canvas")
                XCTAssertGreaterThanOrEqual(layout.tileSide, 44)
            }
        }
    }

    func testKitCard_weatherLineAndDisclosure_exact() {
        XCTAssertEqual(
            ShareKitCardModel.weatherLine(feelsDegrees: 88, condition: "Sunny", place: "Miami, FL", posterMoment: "This morning"),
            "88° feels like · Sunny · Miami, FL · This morning.")
        XCTAssertEqual(ShareKitCardModel.disclosure,
                       "As an Amazon Associate, RunWear earns from qualifying purchases.")
    }

    func testKitCard_tileSamplePoints_coverEveryTileInsideTheCanvas() {
        for format in ShareExportFormat.allCases {
            let count = min(5, ShareKitCardModel.maxItems(for: format))
            let points = ShareKitCardModel.tileSamplePoints(format: format, itemCount: count)
            XCTAssertEqual(points.count, count, "\(format)")
            for point in points {
                XCTAssertTrue(point.x > 0 && point.x < 1 && point.y > 0 && point.y < 1,
                              "\(format): sample \(point) lies inside the canvas")
            }
        }
    }

    func testKitCard_sourcePins_perTileShop_disclosure_noPrices_noCardLevelCTA() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("Text(\"Shop ›\")"),
                      "Shop › attaches per tile, printed on the raster")
        XCTAssertFalse(collection.contains("Shop this kit"),
                       "the gen-5 card-level CTA is retired — links are per tile")
        XCTAssertTrue(collection.contains("ShareKitCardModel.disclosure"),
                      "the foot carries the Associates disclosure")
        XCTAssertTrue(collection.contains("AppTheme.darkCardBackground"),
                      "the tiles seat on the Warm Charcoal ground token")
        // No prices anywhere in the share surfaces.
        let kitStart = try XCTUnwrap(collection.range(of: "struct KitCardView"))
        let kitSection = collection[kitStart.lowerBound...]
        XCTAssertFalse(kitSection.contains("$"), "no prices on the Kit Card")
    }

    // MARK: - Studio + payload wiring (source pins)

    func testStudioAndPayload_sourcePins() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("Text(\"Share this day\")"),
                      "the chapter page's one full-width Studio row")
        XCTAssertTrue(collection.contains("ShareStudioView("),
                      "the Studio is the share door")
        XCTAssertFalse(collection.contains("Button(\"Morning Poster\")"),
                       "the two plain text buttons are retired")
        XCTAssertFalse(collection.contains("Button(\"Kit Card\")"),
                       "the two plain text buttons are retired")
        XCTAssertTrue(collection.contains("ShareRasterVerifier.verify"),
                      "the Share button runs render-and-verify before the system sheet")
        XCTAssertTrue(collection.contains("ShareCardCopy.verifyFailureLabel"),
                      "a failed verification shows the settled line, never the sheet")
        XCTAssertTrue(collection.contains("ShareActivityItemSource"),
                      "the payload carries caption + subject + link metadata via an item source")
        XCTAssertTrue(collection.contains("LPLinkMetadata"),
                      "Messages/Mail preview the actual card")
        XCTAssertFalse(collection.contains("[image, \"#RunAnyWear\"]"),
                       "the bare-hashtag payload is retired")
        // The footer is ONE lockup row: the script and
        // runwear.ai are composed together, not at opposite
        // corners with a Spacer between them.
        XCTAssertTrue(collection.contains("private var footerRow"),
                      "the poster foot is one composed lockup row")
    }

    func testDayViewHero_isNotTheShareCrop_surfaceSeparationPin() throws {
        // Wave B's scope boundary: the day-view hero's own
        // centre fill (WearIveRunCollectionView DayView, the
        // 300 pt frame) is Wave C's item — the share path must
        // not have quietly rebuilt it, and the poster must not
        // render from it.
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains(".frame(height: 300)"),
                      "the day-view hero frame is untouched by Wave B")
        XCTAssertTrue(collection.contains("ShareAnchorCrop.anchoredCropped"),
                      "the SHARE path crops from the anchor, independently of the day hero")
    }

    // MARK: - Day record share fields (null-safe)

    func testEntry_legacyPayloadWithoutShareFields_decodesWithNils() throws {
        let entry = WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_762_300_000),
            placeName: "Kansas City, MO",
            feelsLikeF: 57, actualF: 61, condition: "Clear sky",
            itemNames: ["Shorts"], itemKeys: ["SHORTS"],
            heroImageReference: "https://example.com/hero.jpg",
            windMph: 6, dayLowF: 55, dayHighF: 63,
            faceAnchor: ShareFaceAnchor(centerX: 0.5, centerY: 0.15, width: 0.24, height: 0.14))
        // Round-trip with the fields present.
        let data = try JSONEncoder().encode(entry)
        let roundTripped = try JSONDecoder().decode(WearIveRunEntry.self, from: data)
        XCTAssertEqual(roundTripped.windMph, 6)
        XCTAssertEqual(roundTripped.dayLowF, 55)
        XCTAssertEqual(roundTripped.dayHighF, 63)
        XCTAssertEqual(roundTripped.faceAnchor?.centerY, 0.15)

        // A legacy payload (the keys absent) decodes null-safely.
        var object = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any])
        for key in ["windMph", "dayLowF", "dayHighF", "faceAnchor"] { object[key] = nil }
        let legacyData = try JSONSerialization.data(withJSONObject: object)
        let legacy = try JSONDecoder().decode(WearIveRunEntry.self, from: legacyData)
        XCTAssertNil(legacy.windMph)
        XCTAssertNil(legacy.dayLowF)
        XCTAssertNil(legacy.dayHighF)
        XCTAssertNil(legacy.faceAnchor)
        XCTAssertEqual(legacy.placeName, "Kansas City, MO", "the rest of the record survives")
    }

    func testDisplayDegrees_stripsTheUnitKeepsTheDegree() {
        XCTAssertEqual(ShareDisplayDegrees.number(fromFormatted: "57°F"), 57)
        XCTAssertEqual(ShareDisplayDegrees.number(fromFormatted: "14°C"), 14)
        XCTAssertEqual(ShareDisplayDegrees.number(fromFormatted: "-4°F"), -4)
        XCTAssertEqual(ShareDisplayDegrees.text(fromFormatted: "57°F"), "57°")
    }
}
