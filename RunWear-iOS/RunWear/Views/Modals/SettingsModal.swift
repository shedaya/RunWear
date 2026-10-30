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

    @State private var showFeedback = false

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
}
