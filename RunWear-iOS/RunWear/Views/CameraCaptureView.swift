import SwiftUI
import UIKit

// MARK: - Camera source for the reference-photo intake (RW-16.14)
//
// PhotosUI's picker has no camera, so the camera source rides the
// platform's UIImagePickerController. What it hands back is plain
// image Data — the caller feeds it into exactly the intake seam
// library photos use (append → PhotoQualityChecker → JPEG for
// upload), so a captured photo is checked and committed like any
// other. A capture that can't be encoded falls through to the
// cancel path and the flow simply stays where it was; the intake
// is never a dead end.
struct CameraCaptureView: UIViewControllerRepresentable {
    /// The captured photo, as JPEG data (orientation baked in by
    /// the encoder, so the quality check measures the frame the
    /// user actually shot).
    let onCapture: (Data) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, onCancel: onCancel)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (Data) -> Void
        let onCancel: () -> Void

        init(onCapture: @escaping (Data) -> Void, onCancel: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onCancel = onCancel
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            guard let image = info[.originalImage] as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.95) else {
                onCancel()
                return
            }
            onCapture(data)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCancel()
        }
    }
}
