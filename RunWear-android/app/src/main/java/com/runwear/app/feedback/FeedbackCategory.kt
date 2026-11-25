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

/**
 * RW-18 (RW-26 client half): categories for AUTOMATED submissions —
 * safe-boot diagnostics and crash capture. These are NOT human
 * feedback categories and never appear in the human feedback picker;
 * they exist so automated filings stop landing in the human
 * `something_wrong` stream. Category ids are IDENTICAL across
 * platforms (the iOS lane uses the same two ids).
 */
enum class AutomatedReportCategory(val id: String) {
    DIAGNOSTICS("diagnostics"),
    CRASH_REPORT("crash_report")
}

/** Routing rule (RW-18): which category id a submission files under. */
object FeedbackRouting {
    fun humanCategoryId(category: FeedbackCategory): String = category.id
    fun automatedCategoryId(category: AutomatedReportCategory): String = category.id
}
