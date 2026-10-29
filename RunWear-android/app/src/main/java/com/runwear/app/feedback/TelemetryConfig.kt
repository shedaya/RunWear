package com.runwear.app.feedback

/**
 * The single home for the first-party intake contract (feedback + analytics
 * spec, §2). The server intake is a Cloudflare Worker on our own zone; if its
 * base URL ever changes, it changes here and nowhere else.
 *
 * Payloads POST to:
 *   POST {BASE_URL}/feedback/runwear   schema "runwear-feedback/1"
 *   POST {BASE_URL}/events/runwear     schema "runwear-events/1"
 *   POST {BASE_URL}/crash/runwear      schema "runwear-crash/1"
 *
 * The Worker validates strictly: unknown top-level fields, unknown
 * bundle keys, and unknown event names or prop keys are rejected with
 * 422, so the payload builders in this package emit exactly the
 * allowlisted shapes (see feedback-intake/src/schemas.js).
 */
object TelemetryConfig {
    const val BASE_URL = "https://api.uptimeventuresllc.com/v1"

    const val APP_ID = "runwear"
    const val PLATFORM = "android"

    const val FEEDBACK_ENDPOINT = "$BASE_URL/feedback/$APP_ID"
    const val EVENTS_ENDPOINT = "$BASE_URL/events/$APP_ID"
    const val CRASH_ENDPOINT = "$BASE_URL/crash/$APP_ID"

    const val FEEDBACK_SCHEMA = "runwear-feedback/1"
    const val EVENTS_SCHEMA = "runwear-events/1"
    const val CRASH_SCHEMA = "runwear-crash/1"
}
