import XCTest
import UIKit

/// RW-16.14 — the reference-photo intake grows a second source
/// (the camera, beside the library). (The full-body guidance line
/// this lane once carried was removed from the intake in gen-5
/// and its dead constant deleted in GEN-6 / DA-23.) These tests
/// pin the contracts the lane is built on:
///
///   * the source-routing matrix is total and honest — the camera
///     when it can be used, the library otherwise, WITH the reason
///     shown; no state routes to a dead end;
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

    // MARK: - Guidance copy

    func testFullBodyGuidanceIsDeleted_gen6DA23() throws {
        // GEN-6 (DA-23): the fullBodyGuidance constant (Sol's Oct 31
        // 2026 ruling, RW-16.14) is DELETED as dead code — gen-5
        // removed it from the intake rendering when the intake
        // settled as one face photo (§10); it had zero call sites.
        // The multiple-people limit it carried is still stated
        // where it bites: the intake step's own line and the
        // rejection wording (PersonalHeroTests pins both).
        let dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        let modelsURL = dir.deletingLastPathComponent()
            .appendingPathComponent("RunWear/Models/PersonalHeroModels.swift")
        guard let models = try? String(contentsOf: modelsURL, encoding: .utf8) else {
            throw XCTSkip("app source tree unreachable from this host")
        }
        XCTAssertFalse(models.contains("fullBodyGuidance"),
                       "the dead guidance constant must not return")
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

    // MARK: - Save confirmation (RW-16.15)

    /// The field defect: a successful save dismissed the flow in
    /// silence, and the user concluded nothing had saved. The
    /// contract now: didSave is set BEFORE onFinish runs (the
    /// confirmation is what the user sees as the flow leaves), and
    /// the confirmation copy is the deck's C2j string.
    func testSuccessfulCommitConfirmsTheSaveBeforeFinishing() async {
        let driver = await MainActor.run { OptInCommitDriver() }
        var finishSawSaved: Bool?
        let task = await MainActor.run {
            driver.commit(
                photos: [],
                onCommit: { _ in },
                onFinish: { finishSawSaved = driver.didSave }
            )
        }
        await task?.value
        XCTAssertEqual(finishSawSaved, true,
                       "onFinish must run only after didSave is set — the confirmation precedes dismissal")
        let didSave = await MainActor.run { driver.didSave }
        XCTAssertTrue(didSave)
        XCTAssertEqual(MakeItMeCopy.saveConfirmed, "Your photos are saved.")
    }

    /// A failed commit never claims a save: didSave stays false
    /// and onFinish is never invoked.
    func testFailedCommitNeverConfirmsASave() async {
        struct StubError: Error {}
        let driver = await MainActor.run { OptInCommitDriver() }
        var finishCalled = false
        let task = await MainActor.run {
            driver.commit(
                photos: [],
                onCommit: { _ in throw StubError() },
                onFinish: { finishCalled = true }
            )
        }
        await task?.value
        let didSave = await MainActor.run { driver.didSave }
        let failure = await MainActor.run { driver.failure }
        XCTAssertFalse(didSave, "a failed commit must never present as saved")
        XCTAssertFalse(finishCalled)
        XCTAssertNotNil(failure)
    }
}
