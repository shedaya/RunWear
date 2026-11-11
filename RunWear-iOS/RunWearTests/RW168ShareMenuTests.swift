import XCTest
import SwiftUI
import UIKit

/// RW-168 (Nov 11, 2026): the home share menu — a share
/// control at the hero's top right, beside the gear, opening a
/// small menu of EXISTING doors only:
///   * "Share this outfit" → the RW-158 Share Studio sheet for
///     the current day/outfit;
///   * "Share from collection" → the Wear I've Run collection;
///   * "Save to photos" → today's card rendered by the Studio's
///     own pipeline and written DIRECTLY to the photo library.
///
/// Pins: the pure menu model (HomeShareMenuEntry /
/// HomeShareMenuPolicy, dual-membered via ShareStudioModels),
/// the direct-save seam (ShareCardDirectSave +
/// ShareCardPhotoSaving, dual-membered via
/// ShareCardRenderStore) against a recording mock, the home
/// wiring by source, and ImageRenderer frames of the production
/// top chrome + menu card in both themes (the offscreen route —
/// the hosted-capture route is the standing baseline failure
/// set and is not evidentiary here).
final class RW168ShareMenuTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
            .standardizedFileURL
        return try String(contentsOf: url, encoding: .utf8)
    }

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
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var distinct = Set<Int>()
        for i in stride(from: 0, to: bytes.count, by: 4) {
            distinct.insert(Int(bytes[i]) << 16 | Int(bytes[i + 1]) << 8 | Int(bytes[i + 2]))
        }
        return distinct.count > 2
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW168 wrote \(name)")
    }

    // MARK: - The menu's entries

    func testRW168_MenuEntries_OrderTitlesAndIcons() {
        XCTAssertEqual(HomeShareMenuEntry.allCases,
                       [.shareOutfit, .shareFromCollection, .saveToPhotos],
                       "the menu is exactly the three existing doors, in the commissioned order")
        XCTAssertEqual(HomeShareMenuEntry.shareOutfit.title, "Share this outfit")
        XCTAssertEqual(HomeShareMenuEntry.shareFromCollection.title, "Share from collection")
        XCTAssertEqual(HomeShareMenuEntry.saveToPhotos.title, "Save to photos")
        for entry in HomeShareMenuEntry.allCases {
            XCTAssertFalse(entry.systemImage.isEmpty, "\(entry) carries a glyph")
            XCTAssertFalse(entry.title.contains("Morning Poster"),
                           "the dead name never appears (the format pair is Postcard / Kit Card)")
        }
        XCTAssertEqual(HomeShareMenuPolicy.savedConfirmationText, "Saved to Photos")
    }

    // MARK: - Routing policy

    func testRW168_PolicyRouting_WithAndWithoutCurrentEntry() {
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .shareOutfit, hasCurrentEntry: true),
                       .shareSheet, "Share this outfit opens the RW-158 sheet for the current entry")
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .shareFromCollection, hasCurrentEntry: true),
                       .collection)
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .saveToPhotos, hasCurrentEntry: true),
                       .saveToPhotos)
        // No settled entry yet (home still landing): the
        // entry-dependent doors honestly do nothing; the
        // collection door is always live.
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .shareOutfit, hasCurrentEntry: false), .none)
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .saveToPhotos, hasCurrentEntry: false), .none)
        XCTAssertEqual(HomeShareMenuPolicy.destination(for: .shareFromCollection, hasCurrentEntry: false),
                       .collection)
    }

    func testRW168_DirectSaveDefaults_AreTheStudiosOwnDefaults() throws {
        XCTAssertEqual(HomeShareMenuPolicy.directSaveKind, .poster,
                       "the direct save saves the Postcard — the Studio's pre-selected card")
        XCTAssertEqual(HomeShareMenuPolicy.directSaveFormat, .feed,
                       "at the Studio's default export format")
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("@State private var shareFormat: ShareExportFormat = .feed"),
                      "the Studio host's initial format is .feed — the default the policy mirrors")
        XCTAssertTrue(collection.contains("@State private var selectedKind: ShareCardKind = .poster"),
                      "the Studio's pre-selected card is the Postcard — the default the policy mirrors")
    }

    // MARK: - The direct-save seam

    private final class RecordingSaver: ShareCardPhotoSaving {
        private(set) var savedImages: [UIImage] = []
        var result = true
        func saveToPhotoLibrary(_ image: UIImage) async -> Bool {
            savedImages.append(image)
            return result
        }
    }

    private func fixtureImage() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { ctx in
            UIColor(red: 0.4, green: 0.3, blue: 0.2, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
    }

    func testRW168_DirectSave_InvokesTheSaverExactlyOnce() async {
        let saver = RecordingSaver()
        let image = fixtureImage()
        let saved = await ShareCardDirectSave.save(image: image, saver: saver)
        XCTAssertTrue(saved)
        XCTAssertEqual(saver.savedImages.count, 1, "the rendered card goes to the library exactly once")
        XCTAssertTrue(saver.savedImages.first === image, "the very rendered instance is what is saved")
    }

    func testRW168_DirectSave_MissingRenderNeverTouchesTheLibrary() async {
        let saver = RecordingSaver()
        let saved = await ShareCardDirectSave.save(image: nil, saver: saver)
        XCTAssertFalse(saved)
        XCTAssertTrue(saver.savedImages.isEmpty,
                      "a failed render writes nothing and never prompts for the library")
    }

    func testRW168_DirectSave_SaverFailurePropagates() async {
        let saver = RecordingSaver()
        saver.result = false
        let saved = await ShareCardDirectSave.save(image: fixtureImage(), saver: saver)
        XCTAssertFalse(saved, "a refused library write surfaces as the failure toast, never as success")
        XCTAssertEqual(saver.savedImages.count, 1)
    }

    // MARK: - Wiring (source pins)

    func testRW168_HomeWiring_SourcePins() throws {
        let hero = try sourceFile("../RunWear/Views/FirstScreenHero.swift")
        // The share control sits BESIDE the gear in the top
        // chrome row — its glyph precedes the gear's, in the
        // same row view.
        let topStart = try XCTUnwrap(hero.range(of: "struct HeroTopChromeView"))
        let topEnd = try XCTUnwrap(hero.range(of: "enum HeroChipGeometry"))
        let top = hero[topStart.lowerBound..<topEnd.lowerBound]
        XCTAssertTrue(top.contains("Image(systemName: \"square.and.arrow.up\")"),
                      "the share control carries the share glyph")
        XCTAssertTrue(top.contains(".accessibilityLabel(\"Share\")"))
        let shareIndex = try XCTUnwrap(top.range(of: "square.and.arrow.up")).lowerBound
        let gearIndex = try XCTUnwrap(top.range(of: "gearshape.fill")).lowerBound
        XCTAssertLessThan(shareIndex, gearIndex,
                          "share sits beside the gear, gear outermost at the top right")
        XCTAssertTrue(top.contains(".accessibilityLabel(\"Settings\")"),
                      "the gear's own door is untouched")
        // The menu card: the RW-152 boundary/depth construction.
        XCTAssertTrue(hero.contains("struct HomeShareMenuView"))
        XCTAssertTrue(hero.contains("RoundedRectangle(cornerRadius: 16)"))
        XCTAssertTrue(hero.contains(".shadow(color: .black.opacity(0.14), radius: 12, x: 0, y: 6)"),
                      "the mini bar's soft shadow, outside the rounded shape")

        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("onShareTapped: { withAnimation { showShareMenu.toggle() } }"),
                      "the hero's share control toggles the menu")
        XCTAssertTrue(home.contains("HomeShareMenuView { entry in"),
                      "the overlay presents the production menu card")
        XCTAssertTrue(home.contains("HomeShareMenuPolicy.destination(for: entry, hasCurrentEntry: currentShareEntry != nil)"),
                      "selections route through the pinned policy")
        XCTAssertTrue(home.contains("requestWearShare(current)"),
                      "Share this outfit presents the existing RW-158 sheet for the current entry")
        XCTAssertTrue(home.contains("ShareCardRenderer("),
                      "the direct save renders through the Studio's own pipeline")
        XCTAssertTrue(home.contains("ShareRasterVerifier.verify(image: image, format: format"),
                      "the direct save verifies the raster at the Studio's own gate first")
        XCTAssertTrue(home.contains("ShareCardDirectSave.save(image: image, saver: photoSaver)"),
                      "the verified card is written through the injected photo seam")
        XCTAssertTrue(home.contains("PhotoLibraryShareCardSaver()"),
                      "the production seam is the Photos writer")

        // The renderer's extraction kept the Studio's ONE
        // poster composition.
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertTrue(collection.contains("struct ShareCardRenderer"))
        XCTAssertTrue(collection.contains("func renderImage(kind: ShareCardKind, format: ShareExportFormat)"))
        XCTAssertEqual(collection.components(separatedBy: "MorningPosterView(").count - 1, 1,
                       "exactly one poster composition exists — the renderer's")

        // The photo-library write carries its usage string.
        let plist = try sourceFile("../RunWear/Info.plist")
        XCTAssertTrue(plist.contains("NSPhotoLibraryAddUsageDescription"))
    }

    // MARK: - Frames (menu-open state, both themes)

    @MainActor
    func testRW168_ShareMenuRender_LightAndDark() throws {
        let light = try renderImage(menuScene(dark: false), size: CGSize(width: 393, height: 430),
                                    name: "RW168-ios-share-menu-light.png")
        let dark = try renderImage(menuScene(dark: true), size: CGSize(width: 393, height: 430),
                                   name: "RW168-ios-share-menu-dark.png")
        // The two frames must genuinely differ at the menu
        // card: the light card is the sheet cream (#FDFBF6,
        // luminance ≈ 250) and the dark card is Warm Charcoal
        // (#211D17, ≈ 28). Identical frames would mean the
        // appearance never applied — a failed render, not a
        // dark frame (the first RW-168 run produced exactly
        // that; this pin is its correction).
        let lightCard = cardLuminance(light)
        let darkCard = cardLuminance(dark)
        XCTAssertGreaterThan(lightCard, 200, "light menu card renders on the sheet cream (sampled \(lightCard))")
        XCTAssertLessThan(darkCard, 80, "dark menu card renders on Warm Charcoal (sampled \(darkCard))")
    }

    /// Mean luminance of the menu card's FILL, sampled from a
    /// native-resolution crop at image seat (0.88, 0.36) — the
    /// card's lower-right interior: inside the card, right of
    /// every row's text, clear of every edge and the shadow.
    /// (Two earlier sampler forms failed on geometry, not on
    /// the frames: a 48x48 squash made the patch straddle the
    /// card's right edge — sampled 191 against the true 251.
    /// The artifact PNGs sampled externally at this seat read
    /// a uniform 251.0 light / 29.5 dark.)
    private func cardLuminance(_ image: UIImage) -> Double {
        guard let cg = image.cgImage else { return -1 }
        let seatX = Int(0.88 * Double(cg.width)), seatY = Int(0.36 * Double(cg.height))
        let half = 10
        let rect = CGRect(x: seatX - half, y: seatY - half, width: half * 2, height: half * 2)
        guard let crop = cg.cropping(to: rect) else { return -1 }
        let w = crop.width, h = crop.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return -1 }
        ctx.draw(crop, in: CGRect(x: 0, y: 0, width: w, height: h))
        var sum = 0.0
        for i in stride(from: 0, to: bytes.count, by: 4) {
            sum += 0.299 * Double(bytes[i]) + 0.587 * Double(bytes[i + 1]) + 0.114 * Double(bytes[i + 2])
        }
        return sum / Double(w * h)
    }

    @MainActor
    private func renderImage<Content: View>(_ content: Content, size: CGSize, name: String) throws -> UIImage {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 2
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        XCTAssertTrue(imageHasContent(image), "\(name) rendered blank")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW168 wrote \(name)")
        return image
    }

    /// The production top chrome row (place pill, wordmark,
    /// share + gear) with the menu card open beneath it, over a
    /// dark photo stand-in (the chrome's own scrim family) —
    /// the composition the home hero seats these pieces in.
    private func menuScene(dark: Bool) -> some View {
        ZStack(alignment: .top) {
            LinearGradient(
                colors: [Color(hex: "3A2E22"), Color(hex: "171310")],
                startPoint: .top, endPoint: .bottom)
            VStack(spacing: 0) {
                HeroTopChromeView(
                    placeName: "New York", luminance: 0.5,
                    onPlaceTapped: {}, onSettingsTapped: {}, onShareTapped: {})
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                HStack {
                    Spacer(minLength: 0)
                    HomeShareMenuView(onSelect: { _ in })
                }
                .padding(.trailing, 16)
                .padding(.top, 12)
                Spacer(minLength: 0)
            }
        }
        // Both appearance channels: preferredColorScheme for
        // the SwiftUI environment, and the colorScheme
        // environment itself — the channel ImageRenderer's
        // trait resolution follows for the dynamic UIColor
        // tokens AppTheme is built from (the first RW-168 run
        // carried preferredColorScheme alone and produced a
        // light frame for the dark leg).
        .preferredColorScheme(dark ? .dark : .light)
        .environment(\.colorScheme, dark ? .dark : .light)
    }
}
