package com.runwear.app.feedback

/**
 * The single home for the first-party intake contract (feedback + analytics
 * spec, §2). The server intake is a Cloudflare Worker on our own zone; if its
 * base URL ever changes, it changes here and nowhere else.
 *
 * Payloads POST to:
 *   POST {BASE_URL}/feedback   schema "runwear-feedback/1"
 *   POST {BASE_URL}/events     schema "runwear-events/1"
 *   POST {BASE_URL}/crash      schema "runwear-crash/1"
 */
object TelemetryConfig {
    const val BASE_URL = "https://api.uptimeventuresllc.com/v1"

    const val FEEDBACK_ENDPOINT = "$BASE_URL/feedback"
    const val EVENTS_ENDPOINT = "$BASE_URL/events"
    const val CRASH_ENDPOINT = "$BASE_URL/crash"

    const val APP_ID = "runwear"
    const val PLATFORM = "android"

    const val FEEDBACK_SCHEMA = "runwear-feedback/1"
    const val EVENTS_SCHEMA = "runwear-events/1"
    const val CRASH_SCHEMA = "runwear-crash/1"
}
