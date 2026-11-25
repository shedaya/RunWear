import XCTest
import UIKit
import CoreText

/// RW-25: the bundled Great Vibes font is the genuine article and
/// resolves by name.
///
/// The font ships in the app bundle (GreatVibes-Regular.ttf in the
/// app target's Resources, registered via UIAppFonts in Info.plist)
/// for the script wordmarks — the RunWear logo, "Wear I've Run",
/// "Make It Me". This repo's unit tests are NOT app-hosted (no
/// TEST_HOST; app types reach the suite by dual membership and
/// resources via the test bundle — the EngineFixtureTests pattern),
/// so the font file is also a test-target resource, and these tests
/// register it in-process exactly as the app registers it at launch:
///
///  - The bundled file must parse as a font whose PostScript name is
///    "GreatVibes-Regular" — a wrong or substitute file fails here.
///  - After registration it must resolve by that name through UIKit,
///    in the "Great Vibes" family.
///  - Its character set must cover every character of the three
///    wordmarks, including the lowercase "a" the old design-file
///    subset was missing.
///
/// Provenance: genuine Great Vibes Regular from Google Fonts
/// (SIL Open Font License 1.1), verified against the design files'
/// embedded font before bundling — see
/// release-prep/rw-greatvibes-bundling-2026-11-02.md.
final class GreatVibesFontTests: XCTestCase {

    private var fontURL: URL {
        get throws {
            try XCTUnwrap(
                Bundle(for: GreatVibesFontTests.self)
                    .url(forResource: "GreatVibes-Regular", withExtension: "ttf"),
                "GreatVibes-Regular.ttf is not in the test bundle — the file reference or Resources membership is broken"
            )
        }
    }

    private func registerFont() throws {
        var error: Unmanaged<CFError>?
        let ok = CTFontManagerRegisterFontsForURL(try fontURL as CFURL, .process, &error)
        if !ok {
            // Tolerate "already registered" (a second registration in
            // one process): if the font resolves now, registration is
            // in effect either way. Any other failure leaves it
            // unresolvable and is real.
            XCTAssertNotNil(
                UIFont(name: "GreatVibes-Regular", size: 20),
                "font registration failed: \(String(describing: error?.takeRetainedValue()))"
            )
        }
    }

    func testBundledFileParsesAsGreatVibesRegular() throws {
        let descriptors = try XCTUnwrap(
            CTFontManagerCreateFontDescriptorsFromURL(try fontURL as CFURL) as? [CTFontDescriptor],
            "the bundled file did not parse as a font"
        )
        XCTAssertEqual(descriptors.count, 1)
        let ctFont = CTFontCreateWithFontDescriptor(descriptors[0], 20, nil)
        XCTAssertEqual(CTFontCopyPostScriptName(ctFont) as String, "GreatVibes-Regular")
        XCTAssertEqual(CTFontCopyFamilyName(ctFont) as String, "Great Vibes")
        XCTAssertEqual(CTFontCopyFullName(ctFont) as String, "Great Vibes Regular")
    }

    func testFontResolvesByPostScriptNameAfterRegistration() throws {
        try registerFont()
        let font = UIFont(name: "GreatVibes-Regular", size: 20)
        XCTAssertNotNil(font, "UIFont(name: \"GreatVibes-Regular\") did not resolve after registration")
        XCTAssertEqual(font?.familyName, "Great Vibes")
        XCTAssertTrue(UIFont.fontNames(forFamilyName: "Great Vibes").contains("GreatVibes-Regular"))
    }

    func testFontCoversEveryWordmarkCharacter() throws {
        let descriptors = try XCTUnwrap(
            CTFontManagerCreateFontDescriptorsFromURL(try fontURL as CFURL) as? [CTFontDescriptor]
        )
        let ctFont = CTFontCreateWithFontDescriptor(descriptors[0], 20, nil)
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
