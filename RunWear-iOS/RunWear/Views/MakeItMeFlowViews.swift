import SwiftUI
import PhotosUI
import AVFoundation

// MARK: - RW-14 "Make It Me" — fork + opt-in flow views
//
// Copy is the spec's §7 string deck, verbatim (see
// runwear-rw14-ux-identity-spec-2026-10-31.md). Strings that had to
// be written because the deck has no entry (server-unavailable and
// upload-failure lines) are marked DEVIATION-COPY and named in the
// build report.

// MARK: - The fork (spec §2.2, strings F1–F9)

struct MakeItMeForkView: View {
    /// The first recommendation the user just saw — the photo in it
    /// is the subject of the fork's sentence.
    let previewHeroURL: String?
    /// The location caption — nil while the place is not known yet
    /// (W-23): the slot renders nothing instead of a placeholder.
    let previewContextLine: String?
    let previewOutfitLine: String
    let onCustomize: () -> Void
    let onStock: () -> Void

    var body: some View {
        // GEN-5 (RW-29 FA-40 + EX-21): the photo band is a FIXED
        // header, not the first element of the scrolling column —
        // as a scrolling first element it scrolled away under the
        // top of the screen and the runner was progressively cut
        // by the viewport edge at every scroll position. Only the
        // choices scroll; the invitation surface stays composed.
        // The band's height comes from ForkHeroSpec.bandHeight —
        // never wider than 2.4:1, so the spec window keeps the
        // face, shoulders, and chest composed at every width,
        // phones through tablets.
        GeometryReader { proxy in
            let contentWidth = max(proxy.size.width - 48, 0)
            let bandHeight = ForkHeroSpec.bandHeight(forWidth: contentWidth)
            VStack(spacing: 20) {
                // The first recommendation stays visible above
                // the choice (§2.2).
                VStack(spacing: 8) {
                    // GEN-5 (RW-28 item 19, ruling C-2): the
                    // fork's photo is the purpose-designed
                    // HARD-CODED bundled image (ForkHeroSpec),
                    // rendered through the asset lane's anchoring
                    // window — never the recommendation's pool
                    // photo, whose shipped crop was half blank
                    // sky with the runner sheet-cut (A-3). The
                    // dashed face box is a code overlay in image
                    // space, transformed with the same window, so
                    // it stays registered to the face at every
                    // form factor. The image is bundled, so the
                    // band never renders blank on arrival (A-5).
                    forkHeroBand(width: contentWidth, height: bandHeight)
                        .clipped()
                        .cornerRadius(16)

                    if let previewContextLine {
                        Text(previewContextLine)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    Text(previewOutfitLine)
                        .font(.system(size: 13))
                        .foregroundColor(AppTheme.inkSecondary)
                        .multilineTextAlignment(.center)
                }

                ScrollView {
                    VStack(spacing: 20) {
                Text("See yourself in every outfit.")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(AppTheme.inkPrimary)
                    .multilineTextAlignment(.center)

                // WS-9 fix: frame 32's sub-headline, verbatim from
                // the frozen design package. The RW-14 deck lead
                // that used to sit in this slot is not frame 32's
                // copy and is retired; the frame's fork speaks in
                // the headline, this line, the two choices and the
                // closing line — nothing else. (Frame 32 carries no
                // free-looks promise sentence, and neither does
                // this fork — see the Option A note below.)
                Text("Your recommendations, with you in the photo.")
                    .font(.system(size: 16))
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)

                // Option A — customize
                VStack(alignment: .leading, spacing: 10) {
                    // RW-26 (notes §7.5): the fork introduces the
                    // feature under its script wordmark.
                    Text("Make It Me")
                        .font(.greatVibes(size: 25))
                        .foregroundColor(AppTheme.inkPrimary)
                    // WS-10 (A1): the free-looks promise lives
                    // ONLY on frame 28's Welcome screen — the fork
                    // (frame 32) carries no promise line; its copy
                    // is the choice itself.
                    Text("Upload one photo of your face, and RunWear makes your outfit photos from it — starting with the one you're looking at.")
                        .font(.system(size: 15))
                        .foregroundColor(AppTheme.inkSecondary)
                    Button(action: onCustomize) {
                        Text("Use my photos")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.primaryColor))
                    }
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.cardFill))

                // Option B — stock (a complete, respected choice)
                VStack(alignment: .leading, spacing: 10) {
                    Text("Use the standard photos")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text("Keep the photo library RunWear matches to your outfit and your weather. You can switch to Make It Me any time in Settings.")
                        .font(.system(size: 15))
                        .foregroundColor(AppTheme.inkSecondary)
                    Button(action: onStock) {
                        Text("Use standard photos")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.cardOnSheet))
                    }
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(AppTheme.cardFill))

                Text("Either way, your recommendations don't change — only the photo does.")
                    .font(.system(size: 13))
                    .foregroundColor(AppTheme.inkSecondary)
                    .multilineTextAlignment(.center)
                    }
                    .padding(.bottom, 24)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
        }
    }

    /// GEN-5 (C-2 + RW-29 FA-40): the bundled fork hero with its
    /// dashed face box, rendered through the asset lane's
    /// anchoring WINDOW (ForkHeroSpec.visibleRect) — the full
    /// image is scaled so the spec window maps exactly onto the
    /// band and offset by the window's origin, then clipped.
    /// (Build 25 rendered the pool portrait through a generic
    /// top-anchored cover in a fixed 180 pt band; the generic
    /// cover's image-centre window is not the spec's face-centre
    /// window, and the fixed height let wide form factors slice
    /// through the chin.) The image is local, so the band never
    /// renders blank on arrival (A-5); the cream placeholder
    /// sits behind it for the first-frame paint.
    private func forkHeroBand(width: CGFloat, height: CGFloat) -> some View {
        let frameSize = CGSize(width: width, height: height)
        let visible = ForkHeroSpec.visibleRect(
            imageSize: ForkHeroSpec.imageSize, frameSize: frameSize)
        let scale = visible.width > 0 ? width / visible.width : 1
        return ZStack(alignment: .topLeading) {
            AppTheme.background
            if let forkImage = Self.bundledForkImage {
                Image(uiImage: forkImage)
                    .resizable()
                    .frame(
                        width: ForkHeroSpec.imageSize.width * scale,
                        height: ForkHeroSpec.imageSize.height * scale)
                    .offset(x: -visible.minX * scale, y: -visible.minY * scale)
                let box = ForkHeroSpec.faceBoxRectInFrame(
                    imageSize: ForkHeroSpec.imageSize,
                    frameSize: frameSize
                )
                RoundedRectangle(cornerRadius: box.height * 0.12)
                    .strokeBorder(
                        AppTheme.pinePayload,
                        style: StrokeStyle(lineWidth: 2.5, dash: [10, 7])
                    )
                    .frame(width: box.width, height: box.height)
                    .offset(x: box.minX, y: box.minY)
            }
        }
        .frame(width: width, height: height)
        .clipped()
    }

    /// The bundled fork asset (hero_fallback folder reference —
    /// the same channel as every bundled hero). Loaded once.
    private static let bundledForkImage: UIImage? = {
        let bundle = Bundle.main
        let url = bundle.url(forResource: ForkHeroSpec.assetName, withExtension: "webp", subdirectory: "hero_fallback")
            ?? bundle.url(forResource: ForkHeroSpec.assetName, withExtension: "webp")
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }()

    /// W-23: the fork photo area while its image is in flight —
    /// the card's own fill, a photo glyph and a spinner, at the
    /// card's full height. A designed loading treatment, not the
    /// blank cream slab the bare AsyncImage fallback painted.
    private var forkPhotoPlaceholder: some View {
        ZStack {
            AppTheme.cardOnSheet
            VStack(spacing: 10) {
                Image(systemName: "photo")
                    .font(.system(size: 28))
                    .foregroundColor(AppTheme.inkSecondary)
                ProgressView()
                    .tint(AppTheme.inkSecondary)
            }
        }
        .frame(height: 180)
    }
}

// MARK: - The opt-in flow container (§2.3)
//
// One flow, three entrances: onboarding's fork and Settings' State A
// run mode .optIn (consent → attestation → capture); Settings'
// replace runs mode .replace (consent persists; attestation +
// quality check run again, per §3).

struct MakeItMeOptInFlow: View {
    enum Mode {
        case optIn
        case replace
    }

    let mode: Mode
    /// Commit the accepted photos (JPEG data). Throwing leaves the
    /// flow open with an honest error line; nothing is half-applied —
    /// the store commits consent → uploads → registration in order.
    let onCommit: ([Data]) async throws -> Void
    let onFinish: () -> Void
    let onCancel: () -> Void

    @State private var step: Step = .consent
    @State private var attested = false
    /// RW-15 follow-up: the commit sequencing lives in
    /// OptInCommitDriver (main-actor end to end) — see the
    /// driver's note in PersonalHeroModels.swift for the finish
    /// hand-off defect this replaces. The view only renders the
    /// driver's state.
    /// RW-16.7: the driver is OWNED BY THE PRESENTER (the
    /// Settings presentation host, or onboarding's FirstLookView)
    /// and passed in — never a @StateObject of this view. When
    /// the flow view was recreated by sheet re-presentation, its
    /// @StateObject driver died with it: the in-flight commit
    /// was cancelled into a dead driver and its failure had
    /// nowhere to surface (RW-16.9 §4). Owned by the presenter,
    /// the driver — and any commit it is running — survives the
    /// flow view entirely.
    @ObservedObject var commitDriver: OptInCommitDriver

    private enum Step { case consent, capture }

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            switch effectiveStep {
            case .consent:
                consentStep
            case .capture:
                MakeItMeCaptureView(
                    isReplace: mode == .replace,
                    attested: $attested,
                    committing: commitDriver.isCommitting,
                    saved: commitDriver.didSave,
                    errorLine: commitDriver.failure?.line,
                    errorDetail: commitDriver.failure?.detail,
                    onConfirm: { photos in
                        commitDriver.commit(photos: photos, onCommit: onCommit, onFinish: onFinish)
                    },
                    onCancel: onCancel
                )
            }
        }
    }

    private var effectiveStep: Step {
        // Replace skips the consent screen (consent persists, §3) and
        // lands on capture, where the attestation runs again.
        mode == .replace ? .capture : step
    }

    // MARK: Consent step (strings C1)

    private var consentStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // GEN-5 (RW-28 item 20): the consent header
                // carries the Make It Me script (frame 33).
                Text("Make It Me")
                    .font(.greatVibes(size: 28))
                    .foregroundColor(AppTheme.inkPrimary)

                Text("Before you add photos")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(AppTheme.inkPrimary)

                VStack(alignment: .leading, spacing: 14) {
                    Text("RunWear will upload your photos to our server. They're used for one thing only: making pictures of you wearing your recommended outfits.")
                    Text("Your photos are never used to train AI models. They're never shown to other users, never added to RunWear's standard photo library, and never shared.")
                    Text("Pictures made from your photos are kept only for you, so your outfits are ready when you open the app.")
                    Text("You can delete your photos and every picture made from them at any time: Settings → Make It Me → Delete my photos. Deleting is permanent and takes effect right away.")
                    // GEN-6 Wave A (RW-48, design-spec §10.2): the
                    // consent body's closing sentence — the
                    // explicit no-other-use assurance the settled
                    // body ends on — restored as the body's last
                    // line.
                    Text("Nothing else is done with them.")
                }
                .font(.system(size: 16))
                .foregroundColor(AppTheme.inkPrimary)

                // Attestation (C1c) — no attestation, no picker.
                Button {
                    attested.toggle()
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: attested ? "checkmark.square.fill" : "square")
                            .font(.system(size: 22))
                            .foregroundColor(attested ? AppTheme.primaryColor : AppTheme.inkSecondary)
                        Text("These photos are of me, and I am 18 or older.")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(AppTheme.inkPrimary)
                            .multilineTextAlignment(.leading)
                    }
                }

                Button {
                    step = .capture
                } label: {
                    Text("Continue")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(attested ? .white : AppTheme.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(RoundedRectangle(cornerRadius: 14)
                            .fill(attested ? AppTheme.primaryColor : AppTheme.cardFill))
                }
                .disabled(!attested)

                Button(action: onCancel) {
                    Text("Not now")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(AppTheme.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
            }
            .padding(24)
        }
    }

}

// MARK: - Capture step (strings C2, C3, S7)

private struct PickedPhoto: Identifiable {
    let id = UUID()
    let originalData: Data
    var state: State = .checking
    var jpegData: Data?
    /// GEN-5 (RW-29 FA-54): the per-check report the review
    /// renders individually.
    var report: PhotoCheckReport?

    enum State: Equatable {
        case checking
        case accepted
        case rejected(PhotoRejection)
    }
}

struct MakeItMeCaptureView: View {
    let isReplace: Bool
    @Binding var attested: Bool
    let committing: Bool
    /// RW-16.15: the driver's save confirmation — while true, the
    /// action area renders "Your photos are saved." instead of the
    /// confirm/cancel buttons, until the driver's beat ends and
    /// the flow dismisses.
    let saved: Bool
    let errorLine: String?
    let errorDetail: String?
    let onConfirm: ([Data]) -> Void
    let onCancel: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    @State private var photos: [PickedPhoto] = []
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showPicker = false
    // RW-16.14: the intake offers the camera beside the library.
    @State private var showSourceChoice = false
    @State private var showCamera = false
    /// GEN-5 (RW-29 FA-53): the one-time auto-presentation of
    /// the guided capture when intake opens empty.
    @State private var guidedAutoPresented = false
    /// The honest fallback line when the camera was asked for but
    /// can't be used (PhotoIntakeRouting's notes) — the flow opens
    /// the library instead and says why; never a dead end.
    @State private var intakeNote: String?

    /// The photos a commit sends, derived from the picked set via
    /// the shared seam (ReferenceCommitSet). The picked set itself
    /// is never mutated by a failed commit — nothing in the commit
    /// or failure path touches `photos` — so a retry derives and
    /// sends the same full set (RW-15 (4)).
    private var acceptedJPEGs: [Data] {
        ReferenceCommitSet.jpegPhotos(from: photos.map {
            PickedReferencePhoto(isAccepted: $0.state == .accepted, jpegData: $0.jpegData)
        })
    }

    private var canConfirm: Bool {
        !acceptedJPEGs.isEmpty && !committing && (isReplace ? attested : true)
    }

    /// Rejection / failure lines: the system orange this view
    /// shipped with is a dark-tuned hue (≈2.3:1 on the cream
    /// ground) — text resolves through the theme's accent pair.
    private var warningColor: Color {
        AppTheme.weatherAccentColor(.orange, isDark: colorScheme == .dark)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Add your photo")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(AppTheme.inkPrimary)

                Text("One front-facing photo of your face works best: good light, no sunglasses. The generator reads only your face.")
                    .font(.system(size: 15))
                    .foregroundColor(AppTheme.inkSecondary)

                // GEN-5 (RW-28 item 18): the full-body guidance
                // line no longer renders in intake — the settled
                // intake is ONE photo, face only (design-spec
                // §10: the generator reads only the face; the
                // full-body slot is out). The copy constant stays
                // defined for the historical Oct 31 ruling; the
                // flow does not present it.
                if isReplace {
                    Text("Replacing your photos remakes today's look right away. Your other looks update as they're made again.")
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.inkSecondary)

                    // Replace re-runs the attestation (§3).
                    Button {
                        attested.toggle()
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: attested ? "checkmark.square.fill" : "square")
                                .font(.system(size: 22))
                                .foregroundColor(attested ? AppTheme.primaryColor : AppTheme.inkSecondary)
                            Text("These photos are of me, and I am 18 or older.")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(AppTheme.inkPrimary)
                                .multilineTextAlignment(.leading)
                        }
                    }
                }

                // Picked photos with their quality verdicts
                ForEach(photos) { photo in
                    HStack(alignment: .top, spacing: 12) {
                        photoThumbnail(photo)
                            .frame(width: 72, height: 72)
                            .clipped()
                            .cornerRadius(10)

                        VStack(alignment: .leading, spacing: 4) {
                            switch photo.state {
                            case .checking:
                                Text("Checking this photo…")
                                    .font(.system(size: 14))
                                    .foregroundColor(AppTheme.inkSecondary)
                            case .accepted:
                                // GEN-5 (RW-28 item 17): the review
                                // speaks the SAME four checks as the
                                // guided capture, with the settled
                                // passing copy — acceptance means
                                // all four passed. The check is a
                                // drawn glyph (SF Symbol), not an
                                // emoji-class character (WS-10).
                                HStack(spacing: 5) {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 13, weight: .bold))
                                    Text("That's exactly what we need")
                                }
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(AppTheme.inkPrimary)
                                // GEN-5 (RW-29 FA-54): the four
                                // checks are reported INDIVIDUALLY
                                // — build 25 joined the labels into
                                // one string under a single verdict.
                                if let report = photo.report {
                                    checkReportList(report)
                                }
                            case .rejected(let rejection):
                                // C3: the specific reason, under the
                                // photo, in the flow — never
                                // uploaded-and-prayed-over.
                                Text(rejection.specString)
                                    .font(.system(size: 14))
                                    .foregroundColor(warningColor)
                                // GEN-5 (RW-29 FA-54): the four
                                // checks individually here too, and
                                // the failure ROUTES to the guided
                                // camera — the surface that frames
                                // the face for you — rather than
                                // leaving the user to guess again
                                // in the library.
                                if let report = photo.report {
                                    checkReportList(report)
                                }
                                Button {
                                    photos.removeAll { $0.id == photo.id }
                                    showCamera = true
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "camera.fill")
                                        Text("Retake with the guided camera")
                                    }
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(AppTheme.pinePayload)
                                }
                                .padding(.top, 2)
                            }
                        }
                        Spacer()
                        Button {
                            photos.removeAll { $0.id == photo.id }
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(AppTheme.inkSecondary)
                                .font(.system(size: 20))
                        }
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(AppTheme.cardFill))
                }

                if photos.count < Self.maxPhotos {
                    Button {
                        showSourceChoice = true
                    } label: {
                        HStack {
                            Image(systemName: "plus.circle")
                            Text("Add your photo")
                        }
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.cardFill))
                    }
                }

                if let intakeNote {
                    Text(intakeNote)
                        .font(.system(size: 14))
                        .foregroundColor(AppTheme.inkSecondary)
                }

                if let errorLine {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(errorLine)
                            .font(.system(size: 14))
                            .foregroundColor(warningColor)
                        // RW-15: the server's actual message, when
                        // it sent one — smaller and secondary under
                        // the deck line, so a refusal can be quoted
                        // back to us instead of paraphrased.
                        if let errorDetail {
                            Text(errorDetail)
                                .font(.system(size: 13))
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                }

                if saved {
                    // RW-16.15 save confirmation (spec C2j): the
                    // explicit "it saved" — shown for the driver's
                    // confirmation beat before the flow dismisses.
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 22))
                            // WS-8: dynamic pine pair, not the seed
                            // alone (≈2.19:1 on the dark ground).
                            .foregroundColor(AppTheme.pinePayload)
                        Text(MakeItMeCopy.saveConfirmed)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(AppTheme.inkPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                } else {
                    Button {
                        onConfirm(acceptedJPEGs)
                    } label: {
                        HStack {
                            if committing { ProgressView().tint(.white) }
                            Text(isReplace ? "Save photos" : "Make my first look")
                        }
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(canConfirm ? .white : AppTheme.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(RoundedRectangle(cornerRadius: 14)
                            .fill(canConfirm ? AppTheme.primaryColor : AppTheme.cardFill))
                    }
                    .disabled(!canConfirm)

                    Button(action: onCancel) {
                        Text("Cancel")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(AppTheme.inkSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                    .disabled(committing)
                }
            }
            .padding(24)
        }
        .photosPicker(isPresented: $showPicker, selection: $pickerItems,
                      maxSelectionCount: max(1, 3 - photos.count), matching: .images)
        .onChange(of: pickerItems) { _, items in
            guard !items.isEmpty else { return }
            pickerItems = []
            Task { await loadPicked(items) }
        }
        // RW-16.14: the source choice — camera beside library. The
        // routing decision itself is the pure PhotoIntakeRouting;
        // this dialog only renders its outcome.
        .confirmationDialog("Add a photo", isPresented: $showSourceChoice, titleVisibility: .visible) {
            Button("Take Photo") { routeToSource(.camera) }
            Button("Choose from Library") { routeToSource(.library) }
            Button("Cancel", role: .cancel) {}
        }
        .fullScreenCover(isPresented: $showCamera) {
            // RW-26: the camera door IS the guided capture —
            // camera-first with live coaching (frames 15–16). The
            // captured photo enters the identical seam a library
            // photo uses (addPhoto → PhotoQualityChecker), so both
            // doors are judged by the same checks.
            GuidedSelfieCaptureView(
                onCapture: { data in
                    showCamera = false
                    Task { await addPhoto(originalData: data) }
                },
                onCancel: { showCamera = false },
                // GEN-5 (RW-29 FA-53): the library door inside
                // the guided surface.
                onUsePhotoInstead: {
                    showCamera = false
                    routeToSource(.library)
                }
            )
            .ignoresSafeArea()
        }
        // GEN-5 (RW-29 FA-53): the guided capture is the PRIMARY
        // surface on open — build 25 opened on the review page
        // behind a source-choice dialog, with the designed
        // surface one tap away. Opening intake with no photo yet
        // presents the guided capture directly, once; the page
        // underneath remains the review/commit surface the
        // capture (or the library door inside it) returns to.
        .onAppear {
            if photos.isEmpty && !guidedAutoPresented {
                guidedAutoPresented = true
                showCamera = true
            }
        }
    }

    /// Where the "Add photos" choice goes, per PhotoIntakeRouting:
    /// the camera when it can be used, the library otherwise — with
    /// the routing's honest note shown when the camera was asked
    /// for and couldn't be.
    private func routeToSource(_ source: PhotoIntakeSource) {
        switch PhotoIntakeRouting.route(requested: source, cameraAccess: cameraAccess) {
        case .camera:
            showCamera = true
        case .library:
            intakeNote = nil
            showPicker = true
        case .libraryWithNote(let note):
            intakeNote = note
            showPicker = true
        }
    }

    /// This device's camera state, in the model layer's terms.
    private var cameraAccess: CameraAccess {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { return .unavailable }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return .authorized
        case .notDetermined: return .notDetermined
        case .denied: return .denied
        case .restricted: return .restricted
        @unknown default: return .denied
        }
    }

    /// GEN-5 (RW-29 FA-54): the four settled checks, one row
    /// each — a drawn pass glyph or a plain marker for the
    /// failed check, with the check's settled label. Shared by
    /// the accepted and rejected review rows.
    private func checkReportList(_ report: PhotoCheckReport) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(SelfieCheck.allCases, id: \.rawValue) { check in
                HStack(spacing: 6) {
                    Image(systemName: report.passed(check) ? "checkmark.circle.fill" : "xmark.circle")
                        .font(.system(size: 12))
                        .foregroundColor(report.passed(check) ? AppTheme.pinePayload : warningColor)
                    Text(check.passedLabel)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(AppTheme.inkPrimary)
                }
            }
        }
        .padding(.top, 2)
    }

    @ViewBuilder
    private func photoThumbnail(_ photo: PickedPhoto) -> some View {
        if let uiImage = UIImage(data: photo.originalData) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else {
            AppTheme.cardOnSheet
        }
    }

    /// GEN-5 (RW-28 item 18): the settled intake is ONE photo —
    /// the upload door takes the first picked photo only (the
    /// server accepts the one-photo set; the epoch is one photo).
    static let maxPhotos = 1

    private func loadPicked(_ items: [PhotosPickerItem]) async {
        for item in items {
            guard photos.count < Self.maxPhotos else { break }
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            await addPhoto(originalData: data)
        }
    }

    /// The one intake seam (RW-16.14): every photo — from the
    /// library picker or the camera — enters here as Data and runs
    /// the identical path: appended in the checking state, measured
    /// by PhotoQualityChecker, accepted with its upload JPEG or
    /// rejected with the checker's specific reason. No source gets
    /// its own quality path.
    ///
    /// WS-6 (F-1): the seam runs the checker's ONE unified check —
    /// the accepted JPEG is the artifact the check itself produced
    /// (EXIF baked at decode, a residual quarter-turn corrected),
    /// never a separate re-encode of the original bytes, which is
    /// how a corrected measurement could still upload sideways.
    private func addPhoto(originalData data: Data) async {
        guard photos.count < Self.maxPhotos else { return }
        let picked = PickedPhoto(originalData: data)
        photos.append(picked)
        let result = await PhotoQualityChecker.check(imageData: data)
        if let index = photos.firstIndex(where: { $0.id == picked.id }) {
            photos[index].report = result.report
            if let rejection = result.rejection {
                photos[index].state = .rejected(rejection)
            } else if let jpeg = result.uploadData {
                photos[index].state = .accepted
                photos[index].jpegData = jpeg
            } else {
                photos[index].state = .rejected(.faceUnclear)
            }
        }
    }
}
