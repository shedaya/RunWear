import XCTest
import UIKit

/// RW-16.14 — the reference-photo intake grows a second source
/// (the camera, beside the library) and a guidance line (one
/// full-body photo in plain athletic wear). These tests pin the
/// three contracts the lane is built on:
///
///   * the source-routing matrix is total and honest — the camera
///     when it can be used, the library otherwise, WITH the reason
///     shown; no state routes to a dead end;
///   * the guidance copy carries Sol's ruled elements verbatim —
///     the positive lead, the full-body ask, and the solo-frame
///     note that keeps users out of the multiple-people rejection;
///   * a captured photo enters the IDENTICAL quality check a
///     library photo does — the same PhotoQualityChecker seam —
///     and an unmeasurable or faceless frame fails closed, never
///     waved through.
final class PhotoIntakeTests: XCTestCase {

    // MARK: - Source routing

    func testLibraryRequestAlwaysRoutesToLibrary() {
        for access: CameraAccess in [.unavailable, .notDetermined, .authorized, .denied, .restricted] {
            XCTAssertEqual(
                PhotoIntakeRouting.route(requested: .library, cameraAccess: access),
                .library,
                "a library request must not be diverted (access: \(access))"
            )
        }
    }

    func testCameraRequestRoutesToCameraWhenUsableOrUnasked() {
        XCTAssertEqual(
            PhotoIntakeRouting.route(requested: .camera, cameraAccess: .authorized),
            .camera
        )
        // Not-yet-asked still routes to the camera: presenting it
        // is what asks, in context.
        XCTAssertEqual(
            PhotoIntakeRouting.route(requested: .camera, cameraAccess: .notDetermined),
            .camera
        )
    }

    func testCameraUnavailableFallsBackToLibraryWithNote() {
        XCTAssertEqual(
            PhotoIntakeRouting.route(requested: .camera, cameraAccess: .unavailable),
            .libraryWithNote("No camera is available here — choose from your library instead.")
        )
    }

    func testCameraDeniedOrRestrictedFallsBackToLibraryWithNote() {
        let expected = PhotoIntakeRoute.libraryWithNote(
            "Camera access is off for RunWear. You can turn it on in Settings, or choose from your library."
        )
        XCTAssertEqual(PhotoIntakeRouting.route(requested: .camera, cameraAccess: .denied), expected)
        XCTAssertEqual(PhotoIntakeRouting.route(requested: .camera, cameraAccess: .restricted), expected)
    }

    // MARK: - Guidance copy (Sol's ruling, Oct 31 2026)

    func testFullBodyGuidanceCarriesTheRuledElements() {
        let guidance = MakeItMeCopy.fullBodyGuidance
        XCTAssertTrue(guidance.hasPrefix("Photos of you running work best."),
                      "the guidance leads with Sol's positive line")
        XCTAssertTrue(guidance.contains("one full-body photo in plain athletic wear"),
                      "the structural ask: one full-body photo in plain athletic wear")
        XCTAssertTrue(guidance.contains("just you in the frame"),
                      "the solo-frame note that keeps race photos out of the C3b rejection")
        XCTAssertTrue(guidance.contains("tights and jackets"),
                      "the reason names the full-length outfits it rescues")
        XCTAssertTrue(guidance.contains("Photos with other people in them can't be used."),
                      "the multiple-people limit is stated before it can reject anyone")
    }

    // MARK: - Captured photos run the identical quality check

    /// JPEG data produced exactly the way CameraCaptureView
    /// produces it (UIImage → jpegData) must be measured by the
    /// same checker the library path uses — and a frame with no
    /// face in it is rejected, not accepted.
    func testCameraShapedDataEntersTheSameQualityCheck() async {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 400))
        let image = renderer.image { ctx in
            UIColor(white: 0.9, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 400, height: 400))
        }
        let data = image.jpegData(compressionQuality: 0.95)
        XCTAssertNotNil(data)
        let rejection = await PhotoQualityChecker.rejection(for: data!)
        XCTAssertEqual(rejection, .faceUnclear,
                       "a faceless capture is rejected by the shared check, never waved through")
    }

    /// Undecodable data — the capture path's worst case — fails
    /// closed as .faceUnclear, the same verdict the library path
    /// gives it.
    func testUndecodableDataFailsClosed() async {
        let rejection = await PhotoQualityChecker.rejection(for: Data([0x00, 0x01, 0x02, 0x03]))
        XCTAssertEqual(rejection, .faceUnclear)
    }
}
