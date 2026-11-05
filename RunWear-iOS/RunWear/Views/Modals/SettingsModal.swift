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
    /// RW-26 (notes §8 Q1): the analytics opt-in — Android has
    /// carried it alone until now; parity requires it on iOS with
    /// identical copy and the identical default: OFF.
    @AppStorage("analyticsOptIn") private var analyticsOptIn = false
    /// RW-25: appearance preference — the same AppStorage key the
    /// app root resolves, so this picker and RunWearApp never drift.
    @AppStorage("appearancePreference") private var appearancePreference: AppearancePreference = .system
    /// RW-16.7: the single owner of every "Make It Me"
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

    /// WS-5 (D-14): the Form composes the SettingsInventory
    /// registry — one section per entry, in registry order, in
    /// EVERY configuration. The registry's `sectionsFor` is the
    /// invariant: no section is gated on the feature flag or the
    /// store's mode, so no shipped configuration can make a
    /// section emit nothing (the pre-WS-5 form gated the whole
    /// Make It Me section on MakeItMeAvailability, and the
    /// Delete-my-data row lived inside that section — with the
    /// flag OFF both silently vanished, the D-14 mechanism).
    private var inventorySections: [SettingsSectionID] {
        SettingsInventory.sectionsFor(
            makeItMeEnabled: MakeItMeAvailability.isEnabled,
            makeItMeMode: makeItMeMode
        )
    }

    private var makeItMeMode: SettingsMakeItMeMode {
        switch personalStore.mode {
        case .stock: return .stock
        case .free: return .free
        case .subscribed: return .subscribed
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                ForEach(inventorySections) { section in
                    inventorySection(section)
                        // GEN-5 design pass (RW-29 workstream, Sol
                        // Nov 5): every Settings row sits on the
                        // system's sheet tone (#FDFBF6 light /
                        // #211D17 dark) — the screen's ground is
                        // the approved cream (below), so no surface
                        // of this screen keeps the system
                        // white/grey Form styling.
                        .listRowBackground(AppTheme.sheet)
                }
            }
            // GEN-5 design pass: the Form's own grouped
            // background is hidden and the approved ground shows
            // through — cream #F5F1E8 light, warm charcoal
            // #16130F dark (design of record, tokens §0.4).
            .listRowBackground(AppTheme.sheet)
            .scrollContentBackground(.hidden)
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { isPresented = false }
                }
            }
            // WS-5 (§1.3): every sheet on this surface rests at a
            // NAMED detent. Both sheets below carry scrolling
            // content (a Form; the Make It Me flows/photos/paywall
            // ScrollViews), so both are .large only — no partial
            // rest with a second scroll consumer.
            .sheet(isPresented: $showFeedback) {
                FeedbackSheet()
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
            // RW-16.7: the ONE sheet slot + ONE alert slot for
            // the whole "Make It Me" surface, keyed by the
            // host's enums. Photos → Replace is a content swap
            // inside this single presentation, never a
            // dismiss-and-represent chain.
            .sheet(item: $makeItMePresentation.sheet) { sheet in
                makeItMeSheetContent(sheet)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
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

    // MARK: - Inventory sections (WS-5)

    /// One Form section per registry entry. Content is each
    /// section's existing body, moved verbatim; the switch exists
    /// so the Form's composition IS the registry — a section
    /// cannot be dropped from the sheet without dropping it from
    /// SettingsInventory, which the gate tests pin.
    @ViewBuilder
    private func inventorySection(_ section: SettingsSectionID) -> some View {
        switch section {
        case .appearance:
            // RW-25: native segmented appearance picker
            // (System / Light / Dark) — Form idioms only.
            Section("Appearance") {
                Picker("Appearance", selection: $appearancePreference) {
                    ForEach(AppearancePreference.allCases) { preference in
                        Text(preference.displayName).tag(preference)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

        case .temperatureUnit:
            Section("Temperature Unit") {
                TemperatureUnitPicker(unit: $temperatureUnit)
            }

        case .fitPreference:
            // GEN-3 (Y-13): Settings presents the three fit
            // choices DIRECTLY — the same inline rows onboarding
            // uses (W-22) and Android Settings' direct toggle —
            // not the collapsed "Men's ⌄" menu row the gen-2 iOS
            // census caught here. One control, both surfaces.
            Section("Fit Preference") {
                FitPreferenceInlinePicker(preference: $genderPreference)
            }

        case .bodyTemperature:
            Section("Body Temperature") {
                ComfortLevelControl(comfortLevel: $comfortLevel)
            }

        case .helpImprove:
            // RW-26: "Help improve the app" — copy and default
            // IDENTICAL to Android's settings sheet (§8 Q1).
            // WS-8 (D-25 iOS residue): this section's helper
            // text renders in the design's secondary token pair
            // (AppTheme.inkSecondary), NOT system .secondary —
            // the system color composites to ≈3.3–3.4:1 on
            // light grounds, below the 4.5:1 text floor; the
            // token pair clears it in both modes (pinned in
            // RW26Phase2Tests).
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Toggle(isOn: $analyticsOptIn) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Help improve the app")
                                .font(.appSecondary.weight(.medium))
                            Text("Count how the app is used")
                                .font(.appFootnote)
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                    }
                    Text("We count how the app is used — which features get used and where recommendations miss — and nothing else. No account, no advertising ID, and your location never leaves the phone more precisely than the city name. Turn this off and nothing is counted or sent.")
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                    if !analyticsOptIn {
                        Text("Off. Nothing is being counted.")
                            .font(.appFootnote.weight(.medium))
                    }
                    Text("If the app crashes, it can send us the technical details so we can fix it. This happens only after a crash, and only with your permission here.")
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }
                .padding(.vertical, 4)
            }

        case .makeItMe:
            // RW-14 "Make It Me" (spec §3, states S1–S14),
            // composed UNCONDITIONALLY (WS-5): with the flag OFF
            // the section renders the plain not-started row
            // (frame 36); the flag gates the feature's function
            // behind the row, not the section's existence.
            MakeItMeSettingsSection(
                presentation: makeItMePresentation,
                featureEnabled: MakeItMeAvailability.isEnabled,
                genderPreference: genderPreference,
                currentCombination: currentCombination
            )

        case .deleteMyData:
            // WS-5 (D-14): Delete my data is its OWN section
            // (RW-16.30 decision 5a: the account-level erasure,
            // offered in every mode and every flag state — a
            // stock-mode user still has an account). It used to
            // be a row inside the Make It Me section, so it
            // vanished whenever that section emitted nothing;
            // now Settings hosts it directly, and its
            // confirmation rides the same single alert slot.
            // GEN-5 (RW-28 item 25): the account delete is
            // quarantined under its own "Danger zone" heading.
            Section {
                Button(role: .destructive) {
                    makeItMePresentation.alert = .confirmDeleteData
                } label: {
                    Text("Delete my data")
                        .font(.system(size: 16, weight: .medium))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } header: {
                Text("Danger zone")
            }

        case .support:
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

        case .about:
            Section("About") {
                LabeledContent("Version", value: appVersion)
            }
        }
    }

    // MARK: - "Make It Me" presentation content (RW-16.7)

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
        case .confirmDeleteData: return "Delete all your data?"
        case .dataDeletedConfirmation: return "Your data and account are deleted."
        case .deleteDataFailed: return "Your data could not be deleted. Please try again."
        case .commitFailed(let failure): return LocalizedStringKey(failure.line)
        case nil: return ""
        }
    }

    private func makeItMeAlertMessage(_ alert: MakeItMeAlert) -> String {
        switch alert {
        case .confirmDelete:
            return "Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make It Me turns off. This can't be undone."
        case .confirmDeleteData:
            // RW-16.30 decision 5a: S12's scope discipline, extended
            // to the account itself, plus the one money fact the
            // photos copy doesn't carry — deleting data does not
            // cancel a store subscription.
            return "Everything RunWear holds for you will be deleted from our server and this phone: your photos, every picture made from them, and your account itself. This can't be undone. Deleting your data does not cancel a subscription — you can manage that from your store account."
        case .commitFailed(let failure):
            // The orphaned-commit failure (RW-16.9): the line is
            // the alert's title; the server's detail, when it
            // sent one, is the message.
            return failure.detail ?? "Please try again."
        case .deletedConfirmation, .deleteFailed, .dataDeletedConfirmation, .deleteDataFailed:
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
        case .confirmDeleteData:
            Button("Delete my data", role: .destructive) {
                Task { await performDeleteMyData() }
            }
            Button("Keep my data", role: .cancel) {}
        case .deletedConfirmation, .deleteFailed, .dataDeletedConfirmation, .deleteDataFailed, .commitFailed:
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

    /// Delete my data (RW-16.30 decision 5a): the same confirmation
    /// discipline as the photos delete — the end-state copy shows
    /// only when the server's receipt confirmed the account itself
    /// is deleted (deleteMyData throws otherwise).
    private func performDeleteMyData() async {
        do {
            try await personalStore.deleteAllData()
            ReferencePhotoImageCache.shared.removeAll()
            makeItMePresentation.alert = .dataDeletedConfirmation
        } catch {
            makeItMePresentation.alert = .deleteDataFailed
        }
    }
}

// MARK: - Feedback Sheet (R-15; RW-26 channel wiring)
//
// Category + message + optional reply address. RW-26: the Send
// action SUBMITS to the first-party intake — the same channel
// Android uses (FeedbackSubmissionService) — under a human
// category, with the what's-sent disclosure (frame 48) readable
// BEFORE sending. If a submission fails, the sheet says so and
// offers the old mailto composer as a fallback; it never claims
// a send that did not land.
struct FeedbackSheet: View {
    // GEN-5 design pass: FeedbackSheet's Form is composed with
    // the same cream/sheet treatment as Settings (see body).
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var category: FeedbackCategory = .somethingWrong
    @State private var message = ""
    @State private var replyEmail = ""
    @State private var sending = false
    @State private var sendFailed = false
    @State private var sent = false

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }

    /// The derived bundle that rides with the report — exactly
    /// what the disclosure section lists.
    private var bundle: [String: String] {
        ["App version": appVersion]
    }

    private var fallbackURL: URL? {
        FeedbackComposer.mailtoURL(
            category: category,
            message: message,
            replyEmail: replyEmail,
            appVersion: appVersion
        )
    }

    private var canSend: Bool {
        !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !sending
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

                // Frame 48: the what's-sent disclosure, before Send.
                // WS-8: helper/disclosure text in the token pair
                // (AppTheme.inkSecondary), not system .secondary —
                // same D-25 residue class as the analytics section
                // above.
                Section("What We're Sending With This") {
                    ForEach(FeedbackPayloads.disclosureLines(bundle: bundle), id: \.self) { line in
                        Text(line)
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                    Text("Plus your message, exactly as written, under the category you picked. Nothing else — no location beyond what you type, no photos.")
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                if sent {
                    Section {
                        Label("Thanks — your feedback is in.", systemImage: "checkmark.circle")
                            .foregroundColor(AppTheme.pinePayload)
                    }
                }

                if sendFailed {
                    Section {
                        Text("That didn't send. Check your connection and try again — or email it instead.")
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                        if let fallbackURL {
                            Button("Email it instead") {
                                openURL(fallbackURL)
                                dismiss()
                            }
                        }
                    }
                }
            }
            // GEN-5 design pass: same cream ground + sheet rows
            // as Settings — no system-grey Form surface remains
            // in the settings family.
            .listRowBackground(AppTheme.sheet)
            .scrollContentBackground(.hidden)
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Send Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(sending ? "Sending…" : "Send") {
                        Task { await submit() }
                    }
                    .disabled(!canSend)
                }
            }
        }
    }

    private func submit() async {
        sending = true
        sendFailed = false
        let ok = await FeedbackSubmissionService.submit(
            category: category,
            message: message,
            replyEmail: replyEmail,
            appVersion: appVersion,
            bundle: bundle
        )
        sending = false
        if ok {
            sent = true
            dismiss()
        } else {
            sendFailed = true
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
