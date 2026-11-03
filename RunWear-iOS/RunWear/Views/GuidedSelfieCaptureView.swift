import SwiftUI
import AVFoundation
import Vision
import CoreImage

// MARK: - Guided selfie capture (RW-26, MIM §8.4; frames 15–16)
//
// THE primary Make It Me intake path: the app dictates the
// capture. Camera-first, with a face oval coaching framing (face
// large, with margin), frontality, and light — LIVE check chips
// evaluated from Vision face observations on the preview stream
// (SelfieGuidance, the pure half). The shutter opens when every
// check passes. The captured photo then enters the SAME intake
// seam a library photo uses (MakeItMeCaptureView.addPhoto →
// PhotoQualityChecker), so both doors are judged by identical
// checks. There is NO full-body slot and NO turn-your-head step.

struct GuidedSelfieCaptureView: View {
    /// The captured photo as JPEG data (orientation baked in).
    let onCapture: (Data) -> Void
    let onCancel: () -> Void

    @StateObject private var model = GuidedSelfieCameraModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if model.permissionDenied {
                deniedState
            } else {
                cameraStack
            }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
    }

    private var cameraStack: some View {
        ZStack {
            GuidedCameraPreview(session: model.session)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar: close + the feature's script title is
                // deliberately absent — full-screen camera chrome
                // (frame 15 carries no "Make It Me" title).
                HStack {
                    Button(action: onCancel) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(Circle().fill(Color.black.opacity(0.45)))
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // The face oval: framing with margin.
                Ellipse()
                    .stroke(
                        model.evaluation.allPassed ? AppTheme.primaryLight : Color.white.opacity(0.85),
                        lineWidth: 3
                    )
                    .frame(width: 250, height: 330)
                    .padding(.top, 26)

                // Live check chips.
                HStack(spacing: 8) {
                    ForEach(SelfieCheck.allCases, id: \.rawValue) { check in
                        chip(for: check)
                    }
                }
                .padding(.top, 18)

                // ONE coaching line — the first failing check's.
                Text(model.evaluation.coachingLine ?? "That's it — hold still.")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .padding(.top, 12)
                    .shadow(color: .black.opacity(0.6), radius: 4)

                Spacer()

                // Shutter: opens only when every check passes —
                // the passing moment (frame 16).
                Button {
                    model.capturePhoto { data in
                        if let data { onCapture(data) } else { onCancel() }
                    }
                } label: {
                    Circle()
                        .stroke(Color.white, lineWidth: 4)
                        .frame(width: 74, height: 74)
                        .overlay(
                            Circle()
                                .fill(model.evaluation.allPassed ? Color.white : Color.white.opacity(0.3))
                                .frame(width: 62, height: 62)
                        )
                }
                .disabled(!model.evaluation.allPassed)
                .padding(.bottom, 34)
            }
        }
    }

    private func chip(for check: SelfieCheck) -> some View {
        let passed = model.evaluation.results[check] == true
        return HStack(spacing: 4) {
            Image(systemName: passed ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 11))
            Text(check.passedLabel)
                .font(.system(size: 11, weight: .medium))
                .lineLimit(1)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(
            Capsule().fill(passed ? AppTheme.primaryColor.opacity(0.85) : Color.black.opacity(0.5))
        )
    }

    private var deniedState: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.slash")
                .font(.system(size: 40))
                .foregroundColor(.white.opacity(0.8))
            Text("Camera access is off. You can turn it on in Settings — or pick a photo from your library instead.")
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.85))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("Not now") { onCancel() }
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
        }
    }
}

// MARK: - Preview layer

private struct GuidedCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer // swiftlint:disable:this force_cast
        }
    }
}

// MARK: - Camera model
//
// Threading: the session runs on its own queue, analysis on
// another; @Published state is only ever written on the main
// queue (the delegate hops explicitly).

final class GuidedSelfieCameraModel: NSObject, ObservableObject {
    @Published private(set) var evaluation = SelfieGuidance.evaluate(
        faceCount: 0, largestFaceAreaRatio: 0, yawDegrees: 90, meanLuminance: 0
    )
    @Published private(set) var permissionDenied = false

    let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "guided-selfie.session")
    private let analysisQueue = DispatchQueue(label: "guided-selfie.analysis")
    private var lastAnalysis = Date.distantPast
    private var photoCompletion: ((Data?) -> Void)?
    private var configured = false

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureIfNeeded()
            sessionQueue.async { [weak self] in self?.session.startRunning() }
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self else { return }
                if granted {
                    self.configureIfNeeded()
                    self.sessionQueue.async { [weak self] in self?.session.startRunning() }
                } else {
                    DispatchQueue.main.async { self.permissionDenied = true }
                }
            }
        default:
            permissionDenied = true
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    private func configureIfNeeded() {
        guard !configured else { return }
        configured = true
        session.beginConfiguration()
        session.sessionPreset = .photo
        if let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
           let input = try? AVCaptureDeviceInput(device: camera),
           session.canAddInput(input) {
            session.addInput(input)
        }
        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ]
        videoOutput.setSampleBufferDelegate(self, queue: analysisQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true
        if session.canAddOutput(videoOutput) { session.addOutput(videoOutput) }
        if session.canAddOutput(photoOutput) { session.addOutput(photoOutput) }
        if let connection = videoOutput.connection(with: .video) {
            connection.videoRotationAngle = 90
        }
        session.commitConfiguration()
    }

    func capturePhoto(completion: @escaping (Data?) -> Void) {
        photoCompletion = completion
        let settings = AVCapturePhotoSettings()
        sessionQueue.async { [weak self] in
            self?.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }
}

// MARK: - Frame analysis (Vision, throttled)

extension GuidedSelfieCameraModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = Date()
        guard now.timeIntervalSince(lastAnalysis) > 0.2 else { return }
        lastAnalysis = now
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let request = VNDetectFaceLandmarksRequest()
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .leftMirrored, options: [:])
        try? handler.perform([request])
        let faces = (request.results as? [VNFaceObservation]) ?? []
        let largest = faces
            .map { Double($0.boundingBox.width * $0.boundingBox.height) }
            .max() ?? 0
        let yaw = faces.first.map { Double(truncating: $0.yaw ?? 0) * 180 / .pi } ?? 90
        let luminance = SelfieGuidanceLuminance.mean(of: pixelBuffer)

        let evaluation = SelfieGuidance.evaluate(
            faceCount: faces.count,
            largestFaceAreaRatio: largest,
            yawDegrees: yaw,
            meanLuminance: luminance
        )
        DispatchQueue.main.async { [weak self] in
            self?.evaluation = evaluation
        }
    }
}

// MARK: - Photo capture delegate

extension GuidedSelfieCameraModel: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        let data = error == nil ? photo.fileDataRepresentation() : nil
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let completion = self.photoCompletion
            self.photoCompletion = nil
            completion?(data)
        }
    }
}

// MARK: - Luminance

/// Mean luminance from a pixel buffer via CIAreaAverage — the
/// same measurement PhotoQualityChecker makes on stills.
enum SelfieGuidanceLuminance {
    private static let context = CIContext()

    static func mean(of pixelBuffer: CVPixelBuffer) -> Double {
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let filter = CIFilter(name: "CIAreaAverage") else { return 1 }
        filter.setValue(ciImage, forKey: kCIInputImageKey)
        filter.setValue(CIVector(cgRect: ciImage.extent), forKey: kCIInputExtentKey)
        guard let output = filter.outputImage else { return 1 }
        var pixel = [UInt8](repeating: 0, count: 4)
        context.render(output, toBitmap: &pixel, rowBytes: 4,
                       bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
                       format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB())
        let r = Double(pixel[0]) / 255, g = Double(pixel[1]) / 255, b = Double(pixel[2]) / 255
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
