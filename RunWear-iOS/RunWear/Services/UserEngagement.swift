import Foundation

// MARK: - Rating Prompt Tracker (R-15)
//
// The rating ask lives at a proven-value moment: a recommendation was
// actually on screen. Eligibility: the user has viewed a recommendation
// on 3 distinct days. After a prompt is shown, the ask goes quiet for a
// season (~90 days) and the day count starts over — never nag twice.
struct RatingPromptTracker {
    /// Distinct recommendation-view days required before the first ask.
    static let requiredViewDays = 3
    /// Quiet period after a prompt, in days.
    static let seasonDays = 90

    private let defaults: UserDefaults
    private let calendar: Calendar

    private let viewedDaysKey = "ratingPrompt.viewedDayStamps"
    private let lastPromptedKey = "ratingPrompt.lastPromptedAt"

    init(defaults: UserDefaults = .standard, calendar: Calendar = .current) {
        self.defaults = defaults
        self.calendar = calendar
    }

    /// Records that a recommendation was successfully viewed on `date`'s
    /// day. Multiple views on the same day count once.
    func recordRecommendationViewed(on date: Date = Date()) {
        let stamp = calendar.startOfDay(for: date).timeIntervalSince1970
        var stamps = defaults.array(forKey: viewedDaysKey) as? [Double] ?? []
        guard !stamps.contains(stamp) else { return }
        stamps.append(stamp)
        defaults.set(stamps, forKey: viewedDaysKey)
    }

    /// Number of distinct days a recommendation has been viewed since the
    /// last prompt.
    var viewedDayCount: Int {
        (defaults.array(forKey: viewedDaysKey) as? [Double] ?? []).count
    }

    /// True when the proven-value threshold is met and the season quiet
    /// period since the last prompt has elapsed.
    func shouldPrompt(now: Date = Date()) -> Bool {
        guard viewedDayCount >= Self.requiredViewDays else { return false }
        if let last = defaults.object(forKey: lastPromptedKey) as? Date {
            let season = TimeInterval(Self.seasonDays) * 24 * 60 * 60
            guard now.timeIntervalSince(last) >= season else { return false }
        }
        return true
    }

    /// Marks the prompt as shown: starts the season quiet period and
    /// resets the day count so the next ask needs fresh proven value.
    func recordPromptShown(at date: Date = Date()) {
        defaults.set(date, forKey: lastPromptedKey)
        defaults.removeObject(forKey: viewedDaysKey)
    }
}

// MARK: - Feedback (R-15; RW-26 channel wiring)
//
// RW-26 closes the RW-18/RW-1 residual: the iOS form previously
// only composed a mailto: — it contributed ZERO items to the
// feedback channel. The form now submits through the SAME intake
// Android uses (FeedbackRouting: POST /v1/feedback/runwear,
// schema runwear-feedback/1), under the five HUMAN categories
// (Android's ids verbatim). Machine traffic never files here:
// diagnostics and crash reports carry their own category names
// and destinations (FeedbackRouting.diagnosticsCategory /
// crashReportCategory). The mailto composer survives only as the
// fallback the form offers when a submission fails.
enum FeedbackCategory: String, CaseIterable, Identifiable {
    case somethingWrong = "Something's wrong"
    case recommendationOff = "The recommendation seems off"
    case confusing = "Confusing"
    case idea = "Idea"
    case other = "Other"

    /// The wire id — Android's FeedbackCategory ids, verbatim.
    var wireId: String {
        switch self {
        case .somethingWrong: return "something_wrong"
        case .recommendationOff: return "recommendation_off"
        case .confusing: return "confusing"
        case .idea: return "idea"
        case .other: return "other"
        }
    }

    var id: String { wireId }
}

/// Submits feedback to the first-party intake. The per-install id
/// is created lazily at the first send (Android's rule — never at
/// first launch).
enum FeedbackSubmissionService {
    private static let installIdKey = "feedback.installId"

    static func installId(defaults: UserDefaults = .standard) -> String {
        if let existing = defaults.string(forKey: installIdKey) {
            return existing
        }
        let created = UUID().uuidString
        defaults.set(created, forKey: installIdKey)
        return created
    }

    /// POST one human feedback report. True only on a 2xx — the
    /// form claims "sent" solely when the intake accepted it.
    static func submit(
        category: FeedbackCategory,
        message: String,
        replyEmail: String,
        appVersion: String,
        bundle: [String: String]
    ) async -> Bool {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              FeedbackRouting.isHumanCategory(category.wireId),
              let data = FeedbackPayloads.feedbackData(
                  appVersion: appVersion,
                  installId: installId(),
                  categoryId: category.wireId,
                  text: trimmed,
                  replyEmail: replyEmail,
                  entryPoint: "settings",
                  bundle: bundle
              ),
              let url = URL(string: FeedbackRouting.Destination.feedback.urlString) else {
            return false
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        guard let (_, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse else {
            return false
        }
        return (200..<300).contains(http.statusCode)
    }
}

enum FeedbackComposer {
    static let supportEmail = "support@uptimeventuresllc.com"

    /// Builds the pre-addressed feedback email URL, or nil when there is
    /// no message to send (the form's Send action stays disabled).
    static func mailtoURL(
        category: FeedbackCategory,
        message: String,
        replyEmail: String,
        appVersion: String
    ) -> URL? {
        let trimmedMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedMessage.isEmpty else { return nil }

        var bodyLines = [trimmedMessage, "", "—", "RunWear \(appVersion)"]
        let trimmedReply = replyEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedReply.isEmpty {
            bodyLines.append("Reply to: \(trimmedReply)")
        }

        var components = URLComponents()
        components.scheme = "mailto"
        components.path = supportEmail
        components.queryItems = [
            URLQueryItem(name: "subject", value: "RunWear Feedback — \(category.rawValue)"),
            URLQueryItem(name: "body", value: bodyLines.joined(separator: "\n"))
        ]
        return components.url
    }
}
