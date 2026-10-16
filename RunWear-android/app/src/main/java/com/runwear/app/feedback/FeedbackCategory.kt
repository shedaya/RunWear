package com.runwear.app.feedback

/**
 * The five feedback categories (spec §1.2). The first two are the "negative"
 * categories: submitting one suppresses the rating moment for the rest of the
 * session (addendum suppression rules) — a user who just told us something is
 * wrong is never asked to rate in the same visit.
 */
enum class FeedbackCategory(val label: String, val id: String, val isNegative: Boolean) {
    SOMETHING_WRONG("Something's wrong", "something_wrong", true),
    RECOMMENDATION_OFF("The recommendation seems off", "recommendation_off", true),
    CONFUSING("Confusing", "confusing", false),
    IDEA("Idea", "idea", false),
    OTHER("Other", "other", false)
}
