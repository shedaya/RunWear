import Foundation
import Combine

// MARK: - RW-16.7 — "Make it me" presentation ownership
//
// The defect (field-verified on 1.0 (12), recording 16:38–16:41
// EDT): MakeItMeSettingsSection carried four .sheet modifiers
// and three .alerts on a single Form Section, inside the
// Settings sheet, while its content branch was keyed to
// store.mode. A profile refresh could therefore restructure the
// very view that owned live presentation bindings mid-flight —
// sheets doubled, ghosted, and re-presented with fresh state,
// and the opt-in flow's commit driver (a @StateObject of the
// flow view) was recreated with the view: picked photos lost,
// in-flight commit cancelled into a dead driver, no error ever
// surfaced (four record_consent calls and three upload rounds
// in 70 s on Sol's device; see the RW-16.9 report §4).
//
// The fix is ownership, stated as structure:
//   * ONE host owns every Make-it-me presentation for the
//     Settings surface. It lives at SettingsModal level, whose
//     own structure never branches on store state.
//   * The section is content only — it REQUESTS presentations
//     through the host and owns no bindings, so a store.mode
//     swap in the section can no longer tear a presentation
//     down.
//   * One sheet slot, keyed by an enum: moving from Photos to
//     Replace is a content swap inside a single presentation,
//     never a dismiss-and-represent chain across two booleans
//     on a restructuring view.
//   * ONE commit driver, owned by the host — not by the flow
//     view — so re-presentation can never orphan a commit. A
//     commit in flight completes and delivers its finish, or
//     its failure lands on the driver and is surfaced: inline
//     when the flow is on screen, as a host alert when the
//     sheet was dismissed mid-flight. Silent abandonment is
//     the defect; both surfaces are wired so it cannot recur.

enum MakeItMeSheet: String, Identifiable, Equatable {
    case optIn
    case replace
    case photos
    case subscription

    var id: String { rawValue }
}

enum MakeItMeAlert: Identifiable, Equatable {
    case confirmDelete
    case deletedConfirmation
    case deleteFailed
    /// A commit that failed after its sheet had gone away —
    /// the failure the old structure swallowed whole.
    case commitFailed(CommitFailurePresentation)

    var id: String {
        switch self {
        case .confirmDelete: return "confirmDelete"
        case .deletedConfirmation: return "deletedConfirmation"
        case .deleteFailed: return "deleteFailed"
        case .commitFailed: return "commitFailed"
        }
    }
}

@MainActor
final class MakeItMePresentationHost: ObservableObject {
    @Published var sheet: MakeItMeSheet?
    @Published var alert: MakeItMeAlert?

    /// The one commit driver for every flow this host presents
    /// (opt-in and replace share the type; each presentation
    /// runs one commit at a time and the driver refuses a
    /// second while one is in flight).
    let commitDriver = OptInCommitDriver()

    private var cancellables = Set<AnyCancellable>()

    init() {
        // A failure that lands while no flow is on screen has
        // exactly one place left to go: an alert on the host
        // surface. While a flow IS on screen the failure stays
        // inline in the flow (the capture view reads it from
        // the driver) — never both.
        commitDriver.$failure
            .compactMap { $0 }
            .sink { [weak self] failure in
                guard let self, self.sheet == nil else { return }
                self.alert = .commitFailed(failure)
            }
            .store(in: &cancellables)
    }

    /// Present a sheet. Opening a flow sheet with no commit in
    /// flight clears any stale failure, so a fresh flow never
    /// opens wearing a previous attempt's error line.
    func present(_ newSheet: MakeItMeSheet) {
        if newSheet == .optIn || newSheet == .replace, !commitDriver.isCommitting {
            commitDriver.clearFailure()
        }
        sheet = newSheet
    }

    func dismissSheet() {
        sheet = nil
    }
}
