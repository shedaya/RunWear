import SwiftUI
import PhotosUI

// MARK: - RW-14 "Make it me" — fork + opt-in flow views
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
    let previewContextLine: String
    let previewOutfitLine: String
    let onCustomize: () -> Void
    let onStock: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // The first recommendation stays visible above the
                // choice (§2.2).
                VStack(spacing: 8) {
                    AsyncImage(url: previewHeroURL.flatMap(URL.init(string:))) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().aspectRatio(contentMode: .fill)
                        default:
                            Color.white.opacity(0.05)
                        }
                    }
                    .frame(height: 180)
                    .clipped()
                    .cornerRadius(16)

                    Text(previewContextLine)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                    Text(previewOutfitLine)
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.5))
                        .multilineTextAlignment(.center)
                }

                Text("See yourself in every outfit.")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Text("That's a model in your outfit photo. It could be you — in this morning's weather, on a road like yours, wearing exactly what RunWear recommends.")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)

                // Option A — customize
                VStack(alignment: .leading, spacing: 10) {
                    Text("Make it me")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text("Upload 1 to 3 photos of yourself, and RunWear makes your outfit photos from them — starting with the one you're looking at. Your first 5 looks are free.")
                        .font(.system(size: 15))
                        .foregroundColor(.white.opacity(0.7))
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
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.08)))

                // Option B — stock (a complete, respected choice)
                VStack(alignment: .leading, spacing: 10) {
                    Text("Use the standard photos")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text("Keep the photo library RunWear matches to your outfit and your weather. You can switch to Make it me any time in Settings.")
                        .font(.system(size: 15))
                        .foregroundColor(.white.opacity(0.7))
                    Button(action: onStock) {
                        Text("Use standard photos")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.12)))
                    }
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.04)))

                Text("Either way, your recommendations don't change — only the photo does.")
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.5))
                    .multilineTextAlignment(.center)
            }
            .padding(24)
        }
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
    @State private var committing = false
    @State private var errorLine: String?

    private enum Step { case consent, capture }

    var body: some View {
        ZStack {
            AppTheme.darkBackground.ignoresSafeArea()
            switch effectiveStep {
            case .consent:
                consentStep
            case .capture:
                MakeItMeCaptureView(
                    isReplace: mode == .replace,
                    attested: $attested,
                    committing: committing,
                    errorLine: errorLine,
                    onConfirm: { photos in commit(photos) },
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
                Text("Before you add photos")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)

                VStack(alignment: .leading, spacing: 14) {
                    Text("RunWear will upload your photos to our server. They're used for one thing only: making pictures of you wearing your recommended outfits.")
                    Text("Your photos are never used to train AI models. They're never shown to other users, never added to RunWear's standard photo library, and never shared.")
                    Text("Pictures made from your photos are kept only for you, so your outfits are ready when you open the app.")
                    Text("You can delete your photos and every picture made from them at any time: Settings → Make it me → Delete my photos. Deleting is permanent and takes effect right away.")
                }
                .font(.system(size: 16))
                .foregroundColor(.white.opacity(0.8))

                // Attestation (C1c) — no attestation, no picker.
                Button {
                    attested.toggle()
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: attested ? "checkmark.square.fill" : "square")
                            .font(.system(size: 22))
                            .foregroundColor(attested ? AppTheme.primaryColor : .white.opacity(0.6))
                        Text("These photos are of me, and I am 18 or older.")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.leading)
                    }
                }

                Button {
                    step = .capture
                } label: {
                    Text("Continue")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(RoundedRectangle(cornerRadius: 14)
                            .fill(attested ? AppTheme.primaryColor : Color.white.opacity(0.15)))
                }
                .disabled(!attested)

                Button(action: onCancel) {
                    Text("Not now")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
            }
            .padding(24)
        }
    }

    private func commit(_ photos: [Data]) {
        committing = true
        errorLine = nil
        Task {
            do {
                try await onCommit(photos)
                committing = false
                onFinish()
            } catch PersonalHeroAPIError.authUnavailable {
                committing = false
                // DEVIATION-COPY (no deck entry exists for a server
                // that cannot take uploads yet): honest, specific,
                // and claims nothing was sent — because nothing was.
                errorLine = "We can't reach RunWear's server right now, so your photos can't be uploaded yet. Nothing was sent. You can try again later — your recommendations are unchanged."
            } catch {
                committing = false
                // DEVIATION-COPY (generic upload failure).
                errorLine = "Something went wrong uploading your photos. No look was used. Please try again."
            }
        }
    }
}

// MARK: - Capture step (strings C2, C3, S7)

private struct PickedPhoto: Identifiable {
    let id = UUID()
    let originalData: Data
    var state: State = .checking
    var jpegData: Data?

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
    let errorLine: String?
    let onConfirm: ([Data]) -> Void
    let onCancel: () -> Void

    @State private var photos: [PickedPhoto] = []
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showPicker = false

    private var acceptedJPEGs: [Data] {
        photos.compactMap { $0.state == .accepted ? $0.jpegData : nil }
    }

    private var canConfirm: Bool {
        !acceptedJPEGs.isEmpty && !committing && (isReplace ? attested : true)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Add 1 to 3 photos of you")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)

                Text("Front-facing works best: head and shoulders or half body, good light, no sunglasses. The outfit needs a body to land on.")
                    .font(.system(size: 15))
                    .foregroundColor(.white.opacity(0.7))

                if isReplace {
                    Text("Replacing your photos remakes today's look right away. Your other looks update as they're made again.")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.6))

                    // Replace re-runs the attestation (§3).
                    Button {
                        attested.toggle()
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: attested ? "checkmark.square.fill" : "square")
                                .font(.system(size: 22))
                                .foregroundColor(attested ? AppTheme.primaryColor : .white.opacity(0.6))
                            Text("These photos are of me, and I am 18 or older.")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(.white)
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
                                    .foregroundColor(.white.opacity(0.6))
                            case .accepted:
                                Text("This one works.")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.white.opacity(0.8))
                            case .rejected(let rejection):
                                // C3: the specific reason, under the
                                // photo, in the flow — never
                                // uploaded-and-prayed-over.
                                Text(rejection.specString)
                                    .font(.system(size: 14))
                                    .foregroundColor(.orange)
                            }
                        }
                        Spacer()
                        Button {
                            photos.removeAll { $0.id == photo.id }
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.white.opacity(0.5))
                                .font(.system(size: 20))
                        }
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.05)))
                }

                if photos.count < 3 {
                    Button {
                        showPicker = true
                    } label: {
                        HStack {
                            Image(systemName: "plus.circle")
                            Text(photos.isEmpty ? "Add photos" : "Add another photo")
                        }
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.1)))
                    }
                }

                if let errorLine {
                    Text(errorLine)
                        .font(.system(size: 14))
                        .foregroundColor(.orange)
                }

                Button {
                    onConfirm(acceptedJPEGs)
                } label: {
                    HStack {
                        if committing { ProgressView().tint(.white) }
                        Text(isReplace ? "Save photos" : "Make my first look")
                    }
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(RoundedRectangle(cornerRadius: 14)
                        .fill(canConfirm ? AppTheme.primaryColor : Color.white.opacity(0.15)))
                }
                .disabled(!canConfirm)

                Button(action: onCancel) {
                    Text("Cancel")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .disabled(committing)
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
    }

    @ViewBuilder
    private func photoThumbnail(_ photo: PickedPhoto) -> some View {
        if let uiImage = UIImage(data: photo.originalData) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else {
            Color.white.opacity(0.1)
        }
    }

    private func loadPicked(_ items: [PhotosPickerItem]) async {
        for item in items {
            guard photos.count < 3 else { break }
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            let picked = PickedPhoto(originalData: data)
            photos.append(picked)
            let rejection = await PhotoQualityChecker.rejection(for: data)
            let jpeg = PhotoQualityChecker.jpegForUpload(data)
            if let index = photos.firstIndex(where: { $0.id == picked.id }) {
                if let rejection {
                    photos[index].state = .rejected(rejection)
                } else if let jpeg {
                    photos[index].state = .accepted
                    photos[index].jpegData = jpeg
                } else {
                    photos[index].state = .rejected(.faceUnclear)
                }
            }
        }
    }
}
