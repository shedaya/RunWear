import SwiftUI

// MARK: - Settings (RW-6.7)
//
// A native Form inside a NavigationStack — the one sheet grammar for
// task sheets (title in the toolbar, Done to dismiss). The Form buys
// Dynamic Type, VoiceOver structure, and system dismissal chrome that
// the hand-rolled ScrollView version had to fake. All three preference
// controls are the shared PreferenceControls — the same views Onboarding
// uses, so the surfaces cannot drift.
struct SettingsModal: View {
    @Binding var isPresented: Bool
    @Binding var temperatureUnit: TemperatureUnit
    @Binding var genderPreference: GenderPreference
    @Binding var comfortLevel: ComfortLevel
    /// RW-14: the personal cell currently on the main screen, read
    /// lazily so the section can regenerate it after a photo
    /// replacement. Defaults to none (previews, other call sites).
    var currentCombination: () -> PersonalCombination? = { nil }
    @EnvironmentObject private var personalStore: PersonalHeroStore

    @State private var showFeedback = false
    /// RW-16.7: the single owner of every "Make it me"
    /// presentation on this surface — sheets, alerts, and the
    /// opt-in commit driver. Owned HERE, at the level whose own
    /// structure never branches on store state, so no profile
    /// refresh can rebuild it mid-presentation (the build-(12)
    /// section owned four sheets + three alerts on a store.mode-
    /// keyed branch; see MakeItMePresentation.swift).
    @StateObject private var makeItMePresentation = MakeItMePresentationHost()

    /// App Store product id (App Store Connect) for the review door.
    static let reviewURL = URL(string: "https://apps.apple.com/app/id6827973343?action=write-review")!

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "\(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Temperature Unit") {
                    TemperatureUnitPicker(unit: $temperatureUnit)
                }

                Section("Fit Preference") {
                    FitPreferencePicker(preference: $genderPreference)
                }

                Section("Body Temperature") {
                    ComfortLevelControl(comfortLevel: $comfortLevel)
                }

                // RW-14 "Make it me" (spec §3, states S1–S14).
                MakeItMeSettingsSection(
                    presentation: makeItMePresentation,
                    genderPreference: genderPreference,
                    currentCombination: currentCombination
                )

                // R-15: the always-available feedback + rating doors.
                Section("Support") {
                    Button {
                        showFeedback = true
                    } label: {
                        Label("Send Feedback", systemImage: "envelope")
                    }

                    Link(destination: Self.reviewURL) {
                        Label("Rate RunWear", systemImage: "star")
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: appVersion)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { isPresented = false }
                }
            }
            .sheet(isPresented: $showFeedback) {
                FeedbackSheet()
            }
            // RW-16.7: the ONE sheet slot + ONE alert slot for
            // the whole "Make it me" surface, keyed by the
            // host's enums. Photos → Replace is a content swap
            // inside this single presentation, never a
            // dismiss-and-represent chain.
            .sheet(item: $makeItMePresentation.sheet) { sheet in
                makeItMeSheetContent(sheet)
            }
            .alert(
                makeItMeAlertTitle,
                isPresented: makeItMeAlertPresented,
                presenting: makeItMePresentation.alert
            ) { alert in
                makeItMeAlertButtons(alert)
            } message: { alert in
                Text(makeItMeAlertMessage(alert))
            }
        }
    }

    // MARK: - "Make it me" presentation content (RW-16.7)

    @ViewBuilder
    private func makeItMeSheetContent(_ sheet: MakeItMeSheet) -> some View {
        switch sheet {
        case .optIn:
            MakeItMeOptInFlow(
                mode: .optIn,
                onCommit: { photos in
                    try await personalStore.completeOptIn(
                        jpegPhotos: photos,
                        genderPreference: genderPreference
                    )
                    // The stored objects changed under the tile
                    // cache's paths — drop decoded tiles so Your
                    // Photos can never serve a previous set
                    // (RW-16.9 cache contract).
                    ReferencePhotoImageCache.shared.removeAll()
                },
                onFinish: { makeItMePresentation.dismissSheet() },
                onCancel: { makeItMePresentation.dismissSheet() },
                commitDriver: makeItMePresentation.commitDriver
            )
        case .replace:
            MakeItMeOptInFlow(
                mode: .replace,
                onCommit: { photos in
                    try await personalStore.replaceReferences(
                        jpegPhotos: photos,
                        currentCombination: currentCombination()
                    )
                    ReferencePhotoImageCache.shared.removeAll()
                },
                onFinish: { makeItMePresentation.dismissSheet() },
                onCancel: { makeItMePresentation.dismissSheet() },
                commitDriver: makeItMePresentation.commitDriver
            )
        case .photos:
            MakeItMePhotosView(onReplace: {
                makeItMePresentation.present(.replace)
            })
        case .subscription:
            MakeItMeSubscriptionSheet()
        }
    }

    private var makeItMeAlertPresented: Binding<Bool> {
        Binding(
            get: { makeItMePresentation.alert != nil },
            set: { if !$0 { makeItMePresentation.alert = nil } }
        )
    }

    private var makeItMeAlertTitle: LocalizedStringKey {
        switch makeItMePresentation.alert {
        case .confirmDelete: return "Delete your photos?"
        case .deletedConfirmation: return "Your photos and pictures are deleted."
        case .deleteFailed: return "Your photos could not be deleted. Please try again."
        case .commitFailed(let failure): return LocalizedStringKey(failure.line)
        case nil: return ""
        }
    }

    private func makeItMeAlertMessage(_ alert: MakeItMeAlert) -> String {
        switch alert {
        case .confirmDelete:
            return "Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make it me turns off. This can't be undone."
        case .commitFailed(let failure):
            // The orphaned-commit failure (RW-16.9): the line is
            // the alert's title; the server's detail, when it
            // sent one, is the message.
            return failure.detail ?? "Please try again."
        case .deletedConfirmation, .deleteFailed:
            return ""
        }
    }

    @ViewBuilder
    private func makeItMeAlertButtons(_ alert: MakeItMeAlert) -> some View {
        switch alert {
        case .confirmDelete:
            Button("Delete my photos", role: .destructive) {
                Task { await performDeleteMyPhotos() }
            }
            Button("Keep my photos", role: .cancel) {}
        case .deletedConfirmation, .deleteFailed, .commitFailed:
            Button("OK", role: .cancel) {}
        }
    }

    /// The §3 delete: the end-state copy may only claim what the
    /// system confirmed — on failure nothing was deleted and the
    /// section stays as it was.
    private func performDeleteMyPhotos() async {
        do {
            try await personalStore.deleteEverything()
            ReferencePhotoImageCache.shared.removeAll()
            makeItMePresentation.alert = .deletedConfirmation
        } catch {
            makeItMePresentation.alert = .deleteFailed
        }
    }
}

// MARK: - Feedback Sheet (R-15)
//
// Category + message + optional reply address, composed into a
// pre-addressed email to the studio support inbox (FeedbackComposer).
// Presented from Settings; dismisses itself after handing off to Mail.
struct FeedbackSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var category: FeedbackCategory = .bugReport
    @State private var message = ""
    @State private var replyEmail = ""

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }

    private var composedURL: URL? {
        FeedbackComposer.mailtoURL(
            category: category,
            message: message,
            replyEmail: replyEmail,
            appVersion: appVersion
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Category") {
                    Picker("Category", selection: $category) {
                        ForEach(FeedbackCategory.allCases) { category in
                            Text(category.rawValue).tag(category)
                        }
                    }
                    .labelsHidden()
                }

                Section("Your Feedback") {
                    TextEditor(text: $message)
                        .frame(minHeight: 120)
                        .accessibilityLabel("Your feedback")
                }

                Section {
                    TextField("Email (optional)", text: $replyEmail)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Reply To")
                } footer: {
                    Text("Only used to reply to your feedback.")
                }
            }
            .navigationTitle("Send Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") {
                        if let url = composedURL {
                            openURL(url)
                            dismiss()
                        }
                    }
                    .disabled(composedURL == nil)
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    SettingsModal(
        isPresented: .constant(true),
        temperatureUnit: .constant(.fahrenheit),
        genderPreference: .constant(.male),
        comfortLevel: .constant(.neutral)
    )
    .environmentObject(PersonalHeroStore())
}
