import XCTest

/// Pins for RW-6.2 (2026-10-30 UX audit): ContentView's state
/// switch let the error branch outrank existing data — a failed
/// pull-to-refresh replaced the outfit on screen with a full-screen
/// "Something went wrong." The precedence now lives in
/// `HomePresentation.resolve`, and these tests are its contract:
///
/// - content + error → content, with the error demoted to a banner
///   (showRefreshError)
/// - content, no error → content, no banner
/// - no content + error → the full-screen error (nothing to show)
/// - no content, no error → loading
final class HomePresentationTests: XCTestCase {

    func testContentOutranksError_errorBecomesBanner() {
        XCTAssertEqual(
            HomePresentation.resolve(hasContent: true, errorMessage: "Network unreachable"),
            .content(showRefreshError: true)
        )
    }

    func testContentWithoutError_showsNoBanner() {
        XCTAssertEqual(
            HomePresentation.resolve(hasContent: true, errorMessage: nil),
            .content(showRefreshError: false)
        )
    }

    func testErrorOwnsTheScreenOnlyWithNoContent() {
        XCTAssertEqual(
            HomePresentation.resolve(hasContent: false, errorMessage: "Network unreachable"),
            .error("Network unreachable")
        )
    }

    func testNoContentNoError_isLoading() {
        XCTAssertEqual(
            HomePresentation.resolve(hasContent: false, errorMessage: nil),
            .loading
        )
    }
}
