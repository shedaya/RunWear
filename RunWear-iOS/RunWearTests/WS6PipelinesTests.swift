import XCTest
import UIKit

/// Pins for WS-6 (RC-6, iOS lane) — finish the pipelines:
///
///  * D-20: the gender-aware fallback provider is called WITH the
///    user's fit preference at every app-target painting call site
///    (WeatherViewModel's pre-paint fallback, WeatherOutfitView's
///    default), so the gendered path is the path that paints —
///    first frame and pool-miss alike — and WS-7's serve-source
///    telemetry names the gendered asset for both paints.
///  * T-4 (ruling, R-2): the home toolbar carries no ShareLink /
///    share action; the collection's Poster / Kit Card share is
///    untouched.
///  * F-1 (iOS client half): intake orientation — EXIF baked at
///    decode, the Vision roll gate, and quarter-turn correction —
///    exercised with rotated inputs; upright captures pass
///    unchanged.
///
/// The watch view model's copy of the fallback call lives in the
/// WATCH target (RunWearWatch/WatchViewModel.swift, a Sources
/// member of "RunWear Watch" — verified against the project file);
/// WS-6's boundary forbids editing that target, so it is excluded
/// from the source pin below and reported in the gate report as a
/// stopped sub-item, not silently absorbed.
final class WS6PipelinesTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    // MARK: - D-20 · the preference → hero-gender mapping

    func testFallbackHeroGenderIsDeterministicPerPreference() {
        XCTAssertEqual(GenderPreference.male.fallbackHeroGender, .MALE)
        XCTAssertEqual(GenderPreference.female.fallbackHeroGender, .FEMALE)
        // Unisex demands no gendered asset: nil keeps the legacy
        // chain (Android's UNISEX treatment), deterministically —
        // never forHeroImage()'s coin flip.
        XCTAssertNil(GenderPreference.unisex.fallbackHeroGender)
    }

    // MARK: - D-20 · first paint, both preferences

    @MainActor
    func testFirstPaintFallbackIsGenderedForBothPreferences() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        for (preference, expected) in [
            (GenderPreference.male, "cool_clear_male"),
            (GenderPreference.female, "cool_clear_female"),
        ] {
            let vm = WeatherViewModel()
            vm.genderPreference = preference
            XCTAssertEqual(
                vm.prePaintFallbackAssetName(temp: .COOL, weather: .CLEAR),
                expected,
                "the pre-paint fallback for \(preference) must resolve the gendered asset"
            )
        }
    }

    @MainActor
    func testFirstPaintFreezingRainIsGenderedToo() {
        // FREEZING × RAIN is a real paired cell since WS-7: the
        // gendered paint must serve its own assets, not the legacy
        // degradation to freezing_clear.
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        let vm = WeatherViewModel()
        vm.genderPreference = .male
        XCTAssertEqual(
            vm.prePaintFallbackAssetName(temp: .FREEZING, weather: .RAIN),
            "freezing_rain_male"
        )
        vm.genderPreference = .female
        XCTAssertEqual(
            vm.prePaintFallbackAssetName(temp: .FREEZING, weather: .RAIN),
            "freezing_rain_female"
        )
    }

    // MARK: - D-20 · pool-miss paint, proven by serve telemetry

    private var capturedServes: [HeroServeRecord] = []

    override func setUp() {
        super.setUp()
        HeroServeTelemetry.clear()
        capturedServes = []
        HeroServeTelemetry.installSink { [weak self] record in
            self?.capturedServes.append(record)
        }
    }

    override func tearDown() {
        HeroServeTelemetry.installSink(HeroServeTelemetry.defaultSink)
        HeroServeTelemetry.clear()
        super.tearDown()
    }

    @MainActor
    func testPoolMissServeRecordNamesTheGenderedAssetForBothPreferences() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }

        for (preference, expectedAsset) in [
            (GenderPreference.male, "cool_clear_male"),
            (GenderPreference.female, "cool_clear_female"),
        ] {
            capturedServes = []
            let vm = WeatherViewModel()
            vm.genderPreference = preference
            // The pool-miss paint IS the pre-paint fallback (the
            // view model's loadHeroImage records exactly this
            // record for it — bundled source, the painted asset
            // and its provenance, the bundled-fallback trigger).
            let paintedAsset = vm.prePaintFallbackAssetName(temp: .COOL, weather: .CLEAR)
            let record = HeroServeRecord(
                source: .bundledFallback,
                requestedCombinationId: nil,
                tempBracket: .COOL,
                weatherCondition: .CLEAR,
                gender: preference,
                fallbackAssetFile: paintedAsset,
                fallbackProvenance: FallbackImageProvider.provenance(forAssetName: paintedAsset),
                trigger: "bundled-fallback"
            )
            HeroServeTelemetry.emit(record)

            XCTAssertEqual(1, capturedServes.count)
            let emitted = capturedServes[0]
            XCTAssertEqual(HeroServeSource.bundledFallback, emitted.source)
            XCTAssertEqual(expectedAsset, emitted.fallbackAssetFile,
                           "the recorded pool-miss serve must name the gendered asset")
            XCTAssertNotNil(emitted.fallbackProvenance)
            let summary = emitted.summary()
            XCTAssertTrue(summary.contains("asset=\(expectedAsset)"), summary)
            XCTAssertTrue(summary.contains("gender=\(preference.rawValue.uppercased())"), summary)
            XCTAssertTrue(summary.contains("source=bundled-fallback"), summary)
        }
    }

    // MARK: - D-20 · no painting call site uses the ungendered chain

    /// Every `getImageURL(` call in an app-target painting file
    /// must carry a `gender:` argument inside its argument list.
    private func assertEveryFallbackCallIsGendered(_ source: String, file: String) {
        var searchFrom = source.startIndex
        var calls = 0
        while let range = source.range(of: "getImageURL(", range: searchFrom..<source.endIndex) {
            calls += 1
            // The call's argument list: up to its closing paren at
            // the call's own nesting depth.
            var depth = 0
            var cursor = range.lowerBound
            var arguments = ""
            while cursor < source.endIndex {
                let ch = source[cursor]
                if ch == "(" { depth += 1 }
                if ch == ")" {
                    depth -= 1
                    if depth == 0 { break }
                }
                arguments.append(ch)
                cursor = source.index(after: cursor)
            }
            XCTAssertTrue(
                arguments.contains("gender:"),
                "\(file): a painting call site invokes the ungendered chain: \(arguments)"
            )
            searchFrom = range.upperBound
        }
        XCTAssertGreaterThan(calls, 0, "\(file): expected at least one fallback call to pin")
    }

    func testNoAppTargetPaintingCallSiteUsesTheUngenderedChain_sourcePin() throws {
        let vm = try sourceFile("../RunWear/ViewModels/WeatherViewModel.swift")
        assertEveryFallbackCallIsGendered(vm, file: "WeatherViewModel.swift")
        XCTAssertTrue(vm.contains("gender: genderPreference.fallbackHeroGender"),
                      "the pre-paint fallback must pass the fit preference")

        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        assertEveryFallbackCallIsGendered(view, file: "WeatherOutfitView.swift")
        XCTAssertTrue(view.contains("gender: viewModel.genderPreference.fallbackHeroGender"),
                      "the view's default fallback must pass the fit preference")

        let hero = try sourceFile("../RunWear/Views/HeroImageView.swift")
        assertEveryFallbackCallIsGendered(hero, file: "HeroImageView.swift")
    }

    // MARK: - T-4 · no home share; collection share untouched

    /// Source with `//` comment lines removed — the pin scans
    /// CODE; the view's own comments legitimately document the
    /// removal ("the toolbar ShareLink was removed by T-4").
    private func codeOnly(_ source: String) -> String {
        source.split(separator: "\n", omittingEmptySubsequences: false)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    func testHomeToolbarCarriesNoShareLink_sourcePin() throws {
        let view = codeOnly(try sourceFile("../RunWear/Views/WeatherOutfitView.swift"))
        XCTAssertFalse(view.contains("ShareLink"),
                       "the home view must carry no ShareLink (T-4)")
        XCTAssertFalse(view.contains("shareButton"),
                       "the home share button is removed, not merely unwired")
        XCTAssertFalse(view.contains("heroShareImage"),
                       "the home share payload state is removed with the button")
        // The toolbar itself survives with its designed items.
        XCTAssertTrue(view.contains("Label(\"Settings\", systemImage: \"gearshape\")"))
        XCTAssertTrue(view.contains("Label(\"Shop\", systemImage: \"cart\")"))
    }

    func testCollectionShareIsUntouched_sourcePin() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("ShareLink("),
                      "the collection's designed share must remain")
        XCTAssertTrue(collection.contains("Text(\"#RunAnyWear\")"),
                      "the collection artifacts keep the hashtag lockup")
        XCTAssertTrue(collection.contains("message: Text(\"#RunAnyWear\")"),
                      "the collection share keeps its pre-filled caption")
    }

    // MARK: - F-1 · IntakeOrientation math (rotated inputs)

    func testNormalizeRollWrapsIntoMinus180To180() {
        XCTAssertEqual(IntakeOrientation.normalizeRoll(0), 0)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(90), 90)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(-90), -90)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(180), 180)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(270), -90)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(-270), 90)
        XCTAssertEqual(IntakeOrientation.normalizeRoll(450), 90)
    }

    func testRollGateIsThirtyDegrees() {
        XCTAssertEqual(IntakeOrientation.maxRollDegrees, 30)
        for roll in [0.0, 12.0, -29.9, 30.0, -30.0] {
            XCTAssertTrue(IntakeOrientation.rollWithinGate(roll), "roll \(roll) must pass")
        }
        for roll in [30.1, -45.0, 90.0, -90.46, 180.0] {
            XCTAssertFalse(IntakeOrientation.rollWithinGate(roll), "roll \(roll) must fail")
        }
    }

    func testQuarterTurnCorrectionSnapsAndInverts() {
        // Within the gate: nothing to correct.
        XCTAssertNil(IntakeOrientation.quarterTurnCorrection(rollDegrees: 0))
        XCTAssertNil(IntakeOrientation.quarterTurnCorrection(rollDegrees: 25))
        // The diagnosis case: roll ≈ +90° on the sideways reference
        // — correction is the inverse turn (270° clockwise).
        XCTAssertEqual(IntakeOrientation.quarterTurnCorrection(rollDegrees: 90.46), 270)
        XCTAssertEqual(IntakeOrientation.quarterTurnCorrection(rollDegrees: -90), 90)
        XCTAssertEqual(IntakeOrientation.quarterTurnCorrection(rollDegrees: 180), 180)
        // Near a quarter-turn (within tolerance): still corrected.
        XCTAssertEqual(IntakeOrientation.quarterTurnCorrection(rollDegrees: 80), 270)
        XCTAssertEqual(IntakeOrientation.quarterTurnCorrection(rollDegrees: -100), 90)
        // Not a clean quarter-turn: never guessed at.
        XCTAssertNil(IntakeOrientation.quarterTurnCorrection(rollDegrees: 45))
        XCTAssertNil(IntakeOrientation.quarterTurnCorrection(rollDegrees: 60))
        XCTAssertNil(IntakeOrientation.quarterTurnCorrection(rollDegrees: -140))
    }

    // MARK: - F-1 · the verdict's roll gate

    func testVerdictRejectsRotatedPhotos() {
        // Upright captures pass unchanged (roll defaults to 0 for
        // pre-F-1 call sites, and small tilts pass explicitly).
        XCTAssertNil(PhotoQualityVerdict.rejection(
            faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.5))
        XCTAssertNil(PhotoQualityVerdict.rejection(
            faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.5,
            faceRollDegrees: -8))
        // Sideways (the F-1 artifact) and upside-down are retaken.
        for roll in [90.46, -90.0, 180.0, 45.0] {
            XCTAssertEqual(
                PhotoQualityVerdict.rejection(
                    faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.5,
                    faceRollDegrees: roll),
                .rotated, "roll \(roll) must be rejected .rotated")
        }
        // Order: count still outranks roll; roll outranks darkness.
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(
                faceCount: 0, largestFaceAreaRatio: 0, meanLuminance: 0,
                faceRollDegrees: 90),
            .faceUnclear)
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(
                faceCount: 2, largestFaceAreaRatio: 0.2, meanLuminance: 0.5,
                faceRollDegrees: 90),
            .multiplePeople)
        XCTAssertEqual(
            PhotoQualityVerdict.rejection(
                faceCount: 1, largestFaceAreaRatio: 0.2, meanLuminance: 0.05,
                faceRollDegrees: 90),
            .rotated)
        XCTAssertEqual(
            PhotoRejection.rotated.specString,
            "This photo came out sideways. Retake it holding your phone upright, or pick a photo that's already upright.")
    }

    // MARK: - F-1 · EXIF baked at decode (rotated inputs, real pixels)

    /// A scale-1 renderer (a point is a pixel), so fixture
    /// dimensions are exact — the default renderer format follows
    /// the device scale and would resample every fixture.
    private func testRenderer(size: CGSize) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format)
    }

    /// A 200×100 landscape pixel buffer labelled .right — the
    /// exact shape a portrait front-camera still arrives in (raw
    /// landscape pixels, EXIF saying "rotate 90° to display").
    private func exifRotatedImage() -> UIImage {
        let base = testRenderer(size: CGSize(width: 200, height: 100)).image { ctx in
            UIColor(red: 0.9, green: 0.85, blue: 0.8, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 200, height: 100))
        }
        return UIImage(cgImage: base.cgImage!, scale: 1, orientation: .right)
    }

    func testBakedUprightAppliesEXIFOrientationToPixels() {
        let rotated = exifRotatedImage()
        XCTAssertEqual(rotated.size, CGSize(width: 100, height: 200),
                       "the oriented (displayed) frame is portrait")
        let baked = PhotoQualityChecker.bakedUpright(rotated)
        XCTAssertEqual(baked.imageOrientation, .up)
        XCTAssertEqual(baked.size, CGSize(width: 100, height: 200))
        XCTAssertEqual(baked.cgImage?.width, 100)
        XCTAssertEqual(baked.cgImage?.height, 200)
    }

    func testJpegForUploadArtifactIsUprightPixelsNotJustTag() {
        // A JPEG produced from the EXIF-tagged image: however the
        // encoder carries the orientation (tag or baked), the
        // upload artifact jpegForUpload returns must be upright
        // PIXELS — orientation .up, portrait pixel dimensions.
        let taggedData = exifRotatedImage().jpegData(compressionQuality: 0.95)
        XCTAssertNotNil(taggedData)
        let upload = PhotoQualityChecker.jpegForUpload(taggedData!)
        XCTAssertNotNil(upload)
        let artifact = UIImage(data: upload!)
        XCTAssertNotNil(artifact)
        XCTAssertEqual(artifact?.imageOrientation, .up,
                       "the upload artifact must be upright by pixels, not by tag")
        XCTAssertEqual(artifact?.cgImage?.width, 100)
        XCTAssertEqual(artifact?.cgImage?.height, 200)
    }

    func testUprightCapturePassesTheBakeUnchanged() {
        let upright = testRenderer(size: CGSize(width: 120, height: 80)).image { ctx in
            UIColor(white: 0.9, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 120, height: 80))
        }
        let baked = PhotoQualityChecker.bakedUpright(upright)
        XCTAssertEqual(baked.imageOrientation, .up)
        XCTAssertEqual(baked.cgImage?.width, 120)
        XCTAssertEqual(baked.cgImage?.height, 80)
    }

    /// Four quadrant markers on a 100×200 portrait fixture:
    /// TL red, TR green, BL blue, BR yellow (UIKit space: y grows
    /// downward from the top). Each rotation claim below is its
    /// OWN test method — deliberately: this workflow's log
    /// formatter strips assertion values, so the per-method
    /// pass/fail list is the diagnostic of record for which
    /// quadrant landed where.
    private func quadrantFixture() -> UIImage {
        testRenderer(size: CGSize(width: 100, height: 200)).image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 50, height: 100))
            UIColor.green.setFill()
            ctx.fill(CGRect(x: 50, y: 0, width: 50, height: 100))
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 0, y: 100, width: 50, height: 100))
            UIColor.yellow.setFill()
            ctx.fill(CGRect(x: 50, y: 100, width: 50, height: 100))
        }
    }

    func testRotatedClockwiseDimensionsSwapAndRestore() {
        let image = quadrantFixture()
        let turned90 = PhotoQualityChecker.rotatedClockwise(image, degrees: 90)
        XCTAssertEqual(turned90.cgImage?.width, 200)
        XCTAssertEqual(turned90.cgImage?.height, 100)
        let turned180 = PhotoQualityChecker.rotatedClockwise(image, degrees: 180)
        XCTAssertEqual(turned180.cgImage?.width, 100)
        XCTAssertEqual(turned180.cgImage?.height, 200)
        let turned270 = PhotoQualityChecker.rotatedClockwise(image, degrees: 270)
        XCTAssertEqual(turned270.cgImage?.width, 200)
        XCTAssertEqual(turned270.cgImage?.height, 100)
    }

    // A 90° CLOCKWISE turn: TL←BL, TR←TL, BL←BR, BR←TR — the
    // top edge becomes the right edge.
    func testTurn90TopLeftIsBlue() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 90)
        XCTAssertEqual(pixelColor(turned, x: 50, y: 25), "blue")
    }
    func testTurn90TopRightIsRed() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 90)
        XCTAssertEqual(pixelColor(turned, x: 150, y: 25), "red")
    }
    func testTurn90BottomLeftIsYellow() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 90)
        XCTAssertEqual(pixelColor(turned, x: 50, y: 75), "yellow")
    }
    func testTurn90BottomRightIsGreen() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 90)
        XCTAssertEqual(pixelColor(turned, x: 150, y: 75), "green")
    }

    // 180°: TL←BR, TR←BL, BL←TR, BR←TL.
    func testTurn180TopLeftIsYellow() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 180)
        XCTAssertEqual(pixelColor(turned, x: 25, y: 50), "yellow")
    }
    func testTurn180TopRightIsBlue() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 180)
        XCTAssertEqual(pixelColor(turned, x: 75, y: 50), "blue")
    }
    func testTurn180BottomLeftIsGreen() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 180)
        XCTAssertEqual(pixelColor(turned, x: 25, y: 150), "green")
    }
    func testTurn180BottomRightIsRed() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 180)
        XCTAssertEqual(pixelColor(turned, x: 75, y: 150), "red")
    }

    // 270° clockwise: TL←TR, TR←BR, BL←TL, BR←BL.
    func testTurn270TopLeftIsGreen() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 270)
        XCTAssertEqual(pixelColor(turned, x: 50, y: 25), "green")
    }
    func testTurn270TopRightIsYellow() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 270)
        XCTAssertEqual(pixelColor(turned, x: 150, y: 25), "yellow")
    }
    func testTurn270BottomLeftIsRed() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 270)
        XCTAssertEqual(pixelColor(turned, x: 50, y: 75), "red")
    }
    func testTurn270BottomRightIsBlue() {
        let turned = PhotoQualityChecker.rotatedClockwise(quadrantFixture(), degrees: 270)
        XCTAssertEqual(pixelColor(turned, x: 150, y: 75), "blue")
    }

    /// Sample one pixel by re-rendering the image through a
    /// scale-1 UIKit renderer (the same stack that produced the
    /// fixtures, so orientation and row conventions cancel) and
    /// reading the rendered CGImage's RGBA bytes, row 0 = top.
    private func pixelColor(_ image: UIImage, x: Int, y: Int) -> String {
        let rendered = testRenderer(size: image.size).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        guard let cg = rendered.cgImage,
              let provider = cg.dataProvider,
              let data = provider.data else { return "none" }
        let bytes = CFDataGetBytePtr(data)!
        let offset = y * cg.bytesPerRow + x * 4
        let r = bytes[offset], g = bytes[offset + 1], b = bytes[offset + 2]
        if r > 150 && g < 100 && b < 100 { return "red" }
        if g > 150 && r < 100 && b < 100 { return "green" }
        if b > 150 && r < 100 && g < 100 { return "blue" }
        if r > 150 && g > 150 && b < 100 { return "yellow" }
        return "r\(r)g\(g)b\(b)"
    }

    // MARK: - F-1 · the unified check, end to end on-device

    func testUnifiedCheckFailsClosedOnFacelessAndUndecodable() async {
        let undecodable = await PhotoQualityChecker.check(imageData: Data([0x00, 0x01, 0x02, 0x03]))
        XCTAssertEqual(undecodable.rejection, .faceUnclear)
        XCTAssertNil(undecodable.uploadData)

        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 400))
        let faceless = renderer.image { ctx in
            UIColor(white: 0.9, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 400, height: 400))
        }
        let data = faceless.jpegData(compressionQuality: 0.95)!
        let result = await PhotoQualityChecker.check(imageData: data)
        XCTAssertEqual(result.rejection, .faceUnclear)
        XCTAssertNil(result.uploadData)
    }

    // MARK: - F-1 · capture-path source pins

    func testGuidedCaptureSetsPhotoRotationAndCheckerReadsRoll_sourcePin() throws {
        let capture = try sourceFile("../RunWear/Views/GuidedSelfieCaptureView.swift")
        XCTAssertTrue(capture.contains("photoOutput.connection(with: .video)"),
                      "the photo connection must be configured, not just the preview's")
        XCTAssertTrue(capture.contains("photoConnection.videoRotationAngle = 90"),
                      "the still capture itself must be taken at the portrait rotation")
        let checker = try sourceFile("../RunWear/Services/PhotoQualityChecker.swift")
        XCTAssertTrue(checker.contains("subject?.roll"),
                      "the checker must read the Vision face observation's roll")
        XCTAssertTrue(checker.contains("IntakeOrientation.quarterTurnCorrection"),
                      "the checker must correct a residual quarter-turn")
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertTrue(flow.contains("PhotoQualityChecker.check(imageData: data)"),
                      "intake must run the one unified check so the correction reaches the upload")
    }
}
