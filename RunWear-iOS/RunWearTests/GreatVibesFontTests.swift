import XCTest
import UIKit
import CoreText

/// RW-25: the bundled Great Vibes font resolves by name and is the
/// genuine article.
///
/// The font ships in the app bundle (GreatVibes-Regular.ttf,
/// registered via UIAppFonts in Info.plist) for the script wordmarks —
/// the RunWear logo, "Wear I've Run", "Make It Me". These tests run
/// hosted in the app, so the app's font registration applies:
///
///  - The file must be present in the app bundle.
///  - It must resolve by its PostScript name, "GreatVibes-Regular",
///    in the "Great Vibes" family — a missing registration or a
///    wrong file fails here instead of silently falling back.
///  - Its character set must cover every character of the three
///    wordmarks, including the lowercase "a" the old design-file
///    subset was missing. A redrawn or lookalike substitute with
///    partial coverage fails here.
///
/// Provenance: genuine Great Vibes Regular from Google Fonts
/// (SIL Open Font License 1.1), verified against the design files'
/// embedded font before bundling — see
/// release-prep/rw-greatvibes-bundling-2026-11-02.md.
final class GreatVibesFontTests: XCTestCase {

    func testFontFileIsInAppBundle() {
        let url = Bundle.main.url(forResource: "GreatVibes-Regular", withExtension: "ttf")
        XCTAssertNotNil(url, "GreatVibes-Regular.ttf is not in the app bundle")
    }

    func testFontResolvesByPostScriptName() {
        let font = UIFont(name: "GreatVibes-Regular", size: 20)
        XCTAssertNotNil(font, "UIFont(name: \"GreatVibes-Regular\") did not resolve — UIAppFonts registration or bundle membership is broken")
        XCTAssertEqual(font?.familyName, "Great Vibes")
        XCTAssertTrue(UIFont.fontNames(forFamilyName: "Great Vibes").contains("GreatVibes-Regular"))
    }

    func testFontCoversEveryWordmarkCharacter() {
        let ctFont = CTFontCreateWithName("GreatVibes-Regular" as CFString, 20, nil)
        XCTAssertEqual(CTFontCopyPostScriptName(ctFont) as String, "GreatVibes-Regular")
        let characterSet = CTFontCopyCharacterSet(ctFont) as CharacterSet
        let wordmarks = "RunWear" + "Wear I've Run" + "Make It Me"
        for scalar in wordmarks.unicodeScalars {
            XCTAssertTrue(
                characterSet.contains(scalar),
                "Great Vibes has no glyph for '\(scalar)' (U+\(String(scalar.value, radix: 16, uppercase: true)))"
            )
        }
        // The old design subset's specific failure, pinned by name.
        XCTAssertTrue(characterSet.contains("a"), "lowercase 'a' must have a glyph")
    }
}
