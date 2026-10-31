import SwiftUI

/// One hero photo layer, filling its frame edge-to-edge with a
/// subject-safe crop. The image scales to COVER the frame (never a
/// letterbox, never black bars — Android's ContentScale.Crop and the
/// PWA's object-fit: cover are the reference) and the crop is
/// anchored to the TOP of the image: vertical overflow comes off
/// the bottom, horizontal overflow is centered. The subject's head
/// sits at the top of every pool portrait, so the head is never the
/// part the crop takes — the bare center-anchored
/// `resizable().aspectRatio(.fill)` this replaces cut it off
/// whenever the frame ran wide (field finding 2026-10-31).
struct HeroPhoto: View {
    let image: Image
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        // The anchor acts on the OVERSIZED scaled image: the fixed
        // container proposes its size through an overlay;
        // scaledToFill answers with the covering size (taller than
        // the frame for a portrait), and the overlay's .top
        // alignment pins that overflow to the bottom edge, where
        // the clip takes it. (Why so explicit: the crop's anchor is
        // the whole point of this view — the pre-fix hero used a
        // bare center-anchored fill, and the head was the cost.)
        Color.clear
            .frame(width: width, height: height)
            .overlay(alignment: .top) {
                image
                    .resizable()
                    .scaledToFill()
            }
            .clipped()
    }

    /// The crop contract in image coordinates: the rect of the
    /// source image the top-anchored fill shows. It always starts
    /// at the image's top edge (y = 0 — the head band is always in
    /// frame) and the scaled image always covers the frame (the
    /// visible rect's aspect IS the frame's aspect — no letterbox).
    /// Pinned by FieldFindingsFixTests.
    static func visibleRect(imageSize: CGSize, frameSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0,
              frameSize.width > 0, frameSize.height > 0 else { return .zero }
        let scale = max(frameSize.width / imageSize.width,
                        frameSize.height / imageSize.height)
        let visibleWidth = frameSize.width / scale
        let visibleHeight = frameSize.height / scale
        return CGRect(
            x: (imageSize.width - visibleWidth) / 2,
            y: 0,
            width: visibleWidth,
            height: visibleHeight
        )
    }
}
