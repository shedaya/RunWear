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
enum PhotoQualityChecker {

    struct Measurement {
        let faceCount: Int
        let largestFaceAreaRatio: Double
        let meanLuminance: Double
    }

    /// Measure + classify one picked photo. Returns nil when the
    /// photo is acceptable, or the spec rejection it fails with.
    /// A photo that cannot be measured at all fails closed as
    /// .faceUnclear — an unchecked photo is never uploaded.
    static func rejection(for imageData: Data) async -> PhotoRejection? {
        guard let measurement = await measure(imageData: imageData) else {
            return .faceUnclear
        }
        return PhotoQualityVerdict.rejection(
            faceCount: measurement.faceCount,
            largestFaceAreaRatio: measurement.largestFaceAreaRatio,
            meanLuminance: measurement.meanLuminance
        )
    }

    static func measure(imageData: Data) async -> Measurement? {
        guard let image = UIImage(data: imageData),
              let cgImage = image.cgImage else { return nil }

        let faces: [VNFaceObservation] = await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let request = VNDetectFaceRectanglesRequest()
                let handler = VNImageRequestHandler(cgImage: cgImage, orientation: cgOrientation(from: image.imageOrientation), options: [:])
                try? handler.perform([request])
                continuation.resume(returning: (request.results as? [VNFaceObservation]) ?? [])
            }
        }

        let largestFaceRatio = faces
            .map { Double($0.boundingBox.width * $0.boundingBox.height) }
            .max() ?? 0

        return Measurement(
            faceCount: faces.count,
            largestFaceAreaRatio: largestFaceRatio,
            meanLuminance: meanLuminance(of: cgImage)
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

    private static func cgOrientation(from orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }

    /// Normalize a picked photo for upload: re-encode as JPEG with the
    /// long edge capped at 1600 px (references only anchor identity;
    /// the server signs 1 h URLs and Kontext consumes them as-is).
    static func jpegForUpload(_ imageData: Data, quality: CGFloat = 0.85) -> Data? {
        guard let image = UIImage(data: imageData) else { return nil }
        let maxEdge: CGFloat = 1600
        let longest = max(image.size.width, image.size.height)
        var uploadImage = image
        if longest > maxEdge {
            let scale = maxEdge / longest
            let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let renderer = UIGraphicsImageRenderer(size: newSize)
            uploadImage = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: newSize))
            }
        }
        return uploadImage.jpegData(compressionQuality: quality)
    }
}
