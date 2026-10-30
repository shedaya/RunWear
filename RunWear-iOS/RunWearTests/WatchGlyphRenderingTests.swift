import SwiftUI
import XCTest

/// Pin for RW-6.1 (2026-10-30 UX audit): the watch outfit row's
/// glyph tile rendered `Text(item.icon)` — the SF Symbol NAME as
/// literal text, clipped ("tshirt.f…") — the R-05 defect class,
/// which survived on the watch after the phone fix. The row now
/// composes `WatchClothingGlyph`, whose body is the symbol as an
/// Image. This test walks the modifier chain of the exact view the
/// row composes (`row.glyph`) down to its leaf and asserts the leaf
/// is an Image, never a Text.
final class WatchGlyphRenderingTests: XCTestCase {

    private func item(icon: String) -> ClothingItem {
        ClothingItem(
            key: "TEST_ITEM",
            name: "Test Item",
            description: "Test description",
            icon: icon,
            amazonSearchTerm: "test item"
        )
    }

    /// Unwrap ModifiedContent's `content` chain to the leaf view and
    /// report its type name ("Image", "Text", …).
    private func leafViewTypeName(of view: some View) -> String {
        var current: Any = view
        for _ in 0..<16 {
            let mirror = Mirror(reflecting: current)
            guard let next = mirror.children.first(where: { $0.label == "content" })?.value else {
                return String(describing: type(of: current))
            }
            current = next
        }
        return String(describing: type(of: current))
    }

    func testWatchRowGlyphRendersTheSymbolImageNotItsName() {
        for icon in ["tshirt.fill", "figure.run"] {
            let row = WatchClothingRow(item: item(icon: icon))
            XCTAssertEqual(row.glyph.item.icon, icon)
            XCTAssertEqual(
                leafViewTypeName(of: row.glyph.body),
                "Image",
                "Watch glyph for \(icon) must render Image(systemName:) — a Text leaf here is the R-05 regression (the symbol name printed as text)"
            )
        }
    }
}
