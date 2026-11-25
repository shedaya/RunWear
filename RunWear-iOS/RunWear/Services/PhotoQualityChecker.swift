import Foundation
import Vision
import CoreImage
import UIKit

// MARK: - In-flow reference-photo quality check (spec §2.3)
//
// Runs entirely on device, in the capture flow, BEFORE anything is
// uploaded: face count + largest-face framing from Vision, mean
// luminance from CoreImage. The classification of those measurements
// into the spec's rejection reasons (C3a–C3d) is the pure
// PhotoQualityVerdict in PersonalHeroModels.swift (unit-tested);
// this file only measures.
//
// WS-6 (F-1) — the orientation fix at the source. The captured
// artifact is made UPRIGHT here, before measurement and before
// encoding:
//
//  1. EXIF-at-decode: `UIImage(data:)` surfaces a captured file's
//     EXIF orientation as `imageOrientation` while its `cgImage`
//     stays in raw sensor pixels. The pre-fix checker measured the
//     raw CGImage and `jpegForUpload` re-encoded through
//     `jpegData` on the UNBAKED image whenever the photo was under
//     the size cap — an encode whose consumers that ignore EXIF
//     (the server's raw decode, exactly as Android's BitmapFactory
//     path) read as sideways: F-1's poisoned-artifact seam on iOS.
//     Now every photo is baked — drawn through a renderer, which
//     applies the orientation to the pixels — before anything
//     measures or encodes it, so what leaves here is upright
//     pixels, not upright-by-tag.
//  2. The subject face's roll (the Vision face observation's
//     `roll` — read here for the first time in the app) gates
//     intake via IntakeOrientation: within ±30° passes; a residual
//     clean quarter-turn (an EXIF-less sideways photo) is rotated
//     upright and re-measured once; anything else is rejected
//     .rotated.
//
// What `check` returns — the encoded JPEG — is therefore upright
// at the source; the server's ref-prep uprighting (RW-16.30)
// remains as the safety net behind it, not the fix.
enum PhotoQualityChecker {

    struct Measurement {
        let faceCount: Int
        let largestFaceAreaRatio: Double
        let meanLuminance: Double
        /// The subject (largest) face's in-plane roll, degrees —
        /// the F-1 gate's reading; 0 when no face was found.
        let faceRollDegrees: Double
    }

    /// The unified intake result (mirror of Android's CheckResult):
    /// a rejection, or the upright JPEG bytes to upload.
    struct CheckResult {
        let rejection: PhotoRejection?
        let uploadData: Data?
        /// GEN-5 (RW-29 FA-54): the per-check report the upload
        /// review renders — derived from the final measurement
        /// the verdict itself used.
        let report: PhotoCheckReport
    }

    /// Measure + classify one picked photo. Returns nil when the
    /// photo is acceptable, or the spec rejection it fails with.
    /// A photo that cannot be measured at all fails closed as
    /// .faceUnclear — an unchecked photo is never uploaded.
    static func rejection(for imageData: Data) async -> PhotoRejection? {
        await check(imageData: imageData).rejection
    }

    /// THE intake check (WS-6): bake EXIF → measure (with roll) →
    /// correct a clean quarter-turn and re-measure once → verdict.
    /// On acceptance the upload bytes are encoded from the final,
    /// upright bitmap — the correction reaches the artifact, which
    /// is why this replaces the old measure-then-encode-separately
    /// pair at the intake seam.
    static func check(imageData: Data) async -> CheckResult {
        guard let decoded = UIImage(data: imageData) else {
            return CheckResult(rejection: .faceUnclear, uploadData: nil, report: .unmeasurable)
        }
        // F-1: apply the file's EXIF orientation NOW, at decode —
        // every later stage (measurement, encode) works on upright
        // pixels.
        var image = bakedUpright(decoded)
        guard var measurement = await measure(image: image) else {
            return CheckResult(rejection: .faceUnclear, uploadData: nil, report: .unmeasurable)
        }
        // F-1 roll correction: an EXIF-less residual quarter-turn
        // is uprighted here and re-measured ONCE. The correction
        // direction is never trusted blindly — if the re-measured
        // roll is still a clean quarter-turn (the detector's sign
        // convention cut the other way), the opposite turn is
        // tried once; a roll that survives falls to the verdict,
        // which rejects it .rotated.
        if measurement.faceCount > 0,
           let correction = IntakeOrientation.quarterTurnCorrection(
               rollDegrees: measurement.faceRollDegrees
           ) {
            let rotated = rotatedClockwise(image, degrees: correction)
            if let remeasured = await measure(image: rotated) {
                if IntakeOrientation.rollWithinGate(remeasured.faceRollDegrees) {
                    image = rotated
                    measurement = remeasured
                } else if let secondCorrection = IntakeOrientation.quarterTurnCorrection(
                    rollDegrees: remeasured.faceRollDegrees
                ) {
                    let rotatedAgain = rotatedClockwise(rotated, degrees: secondCorrection)
                    if let final = await measure(image: rotatedAgain),
                       IntakeOrientation.rollWithinGate(final.faceRollDegrees) {
                        image = rotatedAgain
                        measurement = final
                    }
                }
            }
        }
        let rejection = PhotoQualityVerdict.rejection(
            faceCount: measurement.faceCount,
            largestFaceAreaRatio: measurement.largestFaceAreaRatio,
            meanLuminance: measurement.meanLuminance,
            faceRollDegrees: measurement.faceRollDegrees
        )
        let report = PhotoCheckReport.report(
            faceCount: measurement.faceCount,
            largestFaceAreaRatio: measurement.largestFaceAreaRatio,
            meanLuminance: measurement.meanLuminance,
            faceRollDegrees: measurement.faceRollDegrees
        )
        if let rejection {
            return CheckResult(rejection: rejection, uploadData: nil, report: report)
        }
        return CheckResult(rejection: nil, uploadData: jpegData(forUploadImage: image), report: report)
    }

    static func measure(imageData: Data) async -> Measurement? {
        guard let image = UIImage(data: imageData) else { return nil }
        return await measure(image: bakedUpright(image))
    }

    /// Measure one orientation-baked image: Vision faces (count,
    /// largest-face area, subject roll) + mean luminance.
    static func measure(image: UIImage) async -> Measurement? {
        guard let cgImage = image.cgImage else { return nil }

        let faces: [VNFaceObservation] = await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let request = VNDetectFaceRectanglesRequest()
                // The image is orientation-baked (.up) before it
                // gets here, so Vision reads the pixels as stored.
                let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
                try? handler.perform([request])
                continuation.resume(returning: (request.results as? [VNFaceObservation]) ?? [])
            }
        }

        let subject = faces.max {
            ($0.boundingBox.width * $0.boundingBox.height)
                < ($1.boundingBox.width * $1.boundingBox.height)
        }
        let largestFaceRatio = subject.map {
            Double($0.boundingBox.width * $0.boundingBox.height)
        } ?? 0
        // VNFaceObservation.roll is in radians; the gate speaks
        // degrees (IntakeOrientation).
        let rollDegrees = subject?.roll.map {
            Double(truncating: $0) * 180 / .pi
        } ?? 0

        return Measurement(
            faceCount: faces.count,
            largestFaceAreaRatio: largestFaceRatio,
            meanLuminance: meanLuminance(of: cgImage),
            faceRollDegrees: rollDegrees
        )
    }

    /// Average luminance via CIAreaAverage over the whole frame.
    private static func meanLuminance(of cgImage: CGImage) -> Double {
        let ciImage = CIImage(cgImage: cgImage)
        guard let filter = CIFilter(name: "CIAreaAverage") else { return 1 }
        filter.setValue(ciImage, forKey: kCIInputImageKey)
        filter.setValue(CIVector(cgRect: ciImage.extent), forKey: kCIInputExtentKey)
        guard let output = filter.outputImage else { return 1 }
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = CIContext()
        context.render(output, toBitmap: &pixel, rowBytes: 4,
                       bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
                       format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB())
        let r = Double(pixel[0]) / 255, g = Double(pixel[1]) / 255, b = Double(pixel[2]) / 255
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    // MARK: - Orientation baking + rotation (F-1)

    /// A 1:1 renderer (format scale 1, so a point is a pixel —
    /// the renderer's device-scale default would resample the
    /// artifact's dimensions, which the upload cap speaks in).
    private static func pixelRenderer(size: CGSize) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format)
    }

    /// Bake a UIImage's orientation into its pixels: drawing an
    /// image through a renderer applies its orientation, and the
    /// rendered result is orientation .up. `image.size` is the
    /// ORIENTED size, so the renderer's canvas is the displayed
    /// frame. An already-upright image is returned as-is.
    static func bakedUpright(_ image: UIImage) -> UIImage {
        guard image.imageOrientation != .up else { return image }
        return pixelRenderer(size: image.size).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    /// Rotate an image's pixels clockwise by a quarter-turn
    /// (90/180/270), expressed through UIImage orientation values —
    /// whose semantics ARE the rotation — and baked, so no
    /// transform-sign convention is ever guessed at: relabeling
    /// upright pixels with the orientation that names the turn and
    /// baking produces pixels turned by exactly that amount.
    static func rotatedClockwise(_ image: UIImage, degrees: Int) -> UIImage {
        let upright = bakedUpright(image)
        guard let cgImage = upright.cgImage else { return upright }
        let orientation: UIImage.Orientation
        switch ((degrees % 360) + 360) % 360 {
        case 90: orientation = .right
        case 180: orientation = .down
        case 270: orientation = .left
        default: return upright
        }
        let relabeled = UIImage(cgImage: cgImage, scale: upright.scale, orientation: orientation)
        return pixelRenderer(size: relabeled.size).image { _ in
            relabeled.draw(in: CGRect(origin: .zero, size: relabeled.size))
        }
    }

    // MARK: - Upload encoding

    /// Encode an orientation-baked image as the upload JPEG: the
    /// long edge capped at 1600 px (references only anchor
    /// identity; the server signs 1 h URLs and Kontext consumes
    /// them as-is). The input's pixels are already upright, and
    /// the renderer's output is .up, so the artifact's pixels —
    /// not merely its EXIF tag — are upright.
    private static func jpegData(forUploadImage image: UIImage, quality: CGFloat = 0.85) -> Data? {
        let maxEdge: CGFloat = 1600
        let longest = max(image.size.width, image.size.height)
        var uploadImage = image
        if longest > maxEdge {
            let scale = maxEdge / longest
            let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            uploadImage = pixelRenderer(size: newSize).image { _ in
                image.draw(in: CGRect(origin: .zero, size: newSize))
            }
        }
        return uploadImage.jpegData(compressionQuality: quality)
    }

    /// Normalize a picked photo for upload: re-encode as JPEG with
    /// the long edge capped at 1600 px. WS-6 (F-1): the EXIF
    /// orientation is baked into the pixels FIRST (and at every
    /// size — the pre-fix code skipped the render entirely under
    /// the cap, leaving the artifact upright only by tag).
    static func jpegForUpload(_ imageData: Data, quality: CGFloat = 0.85) -> Data? {
        guard let image = UIImage(data: imageData) else { return nil }
        return jpegData(forUploadImage: bakedUpright(image), quality: quality)
    }
}
