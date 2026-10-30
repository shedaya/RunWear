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

// MARK: - Feedback Composer (R-15)
//
// The Support section's "Send Feedback" form composes a pre-addressed
// email to the studio support inbox. (No feedback HTTP endpoint exists
// for RunWear; the sibling apps' api.uptimeventuresllc.com route is not
// provisioned for this app, so the composer uses the provably live
// support address instead.)
enum FeedbackCategory: String, CaseIterable, Identifiable {
    case bugReport = "Bug Report"
    case featureRequest = "Feature Request"
    case outfitFeedback = "Outfit Feedback"
    case other = "Something Else"

    var id: String { rawValue }
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
