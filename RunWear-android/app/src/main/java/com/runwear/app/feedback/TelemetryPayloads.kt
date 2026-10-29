package com.runwear.app.feedback

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure payload builders for the intake contract — the one place the
 * wire shapes are made. Kept free of Android state (callers pass the
 * app version and install ID in) so unit tests can pin the exact
 * shapes against the Worker's allowlists
 * (feedback-intake/src/schemas.js, the contract of record).
 *
 * The shapes, exactly:
 * - Envelope: schema, app, platform, appVersion, installId. Nothing
 *   else — the Worker rejects unknown top-level fields with 422.
 * - Feedback: envelope + category, text, replyEmail?, entryPoint?,
 *   bundle. The user's words ride in "text" (never "message").
 * - Events: each event is {name, ts, props?} — the timestamp field
 *   is "ts" (epoch milliseconds), never "at".
 * - Crash: envelope + the flat CRASH_FIELDS (osVersion, deviceModel,
 *   stackTrace, recurring, crashTimestamp) at top level — never
 *   wrapped in a nested object.
 */
object TelemetryPayloads {

    /** Common envelope fields for every payload (spec §2.2). */
    fun envelope(schema: String, appVersion: String, installId: String): JSONObject =
        JSONObject().apply {
            put("schema", schema)
            put("app", TelemetryConfig.APP_ID)
            put("platform", TelemetryConfig.PLATFORM)
            put("appVersion", appVersion)
            put("installId", installId)
        }

    /** The full feedback payload: envelope + the report fields. */
    fun feedback(
        appVersion: String,
        installId: String,
        category: FeedbackCategory,
        text: String,
        replyEmail: String?,
        entryPoint: String?,
        bundle: JSONObject
    ): JSONObject = envelope(TelemetryConfig.FEEDBACK_SCHEMA, appVersion, installId).apply {
        put("category", category.id)
        put("text", text)
        if (!replyEmail.isNullOrBlank()) put("replyEmail", replyEmail.trim())
        if (entryPoint != null) put("entryPoint", entryPoint)
        put("bundle", bundle)
    }

    /** One analytics event. Null prop values are dropped, never sent. */
    fun event(name: String, tsMs: Long, props: Map<String, Any?> = emptyMap()): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("ts", tsMs)
            val clean = props.filterValues { it != null }
            if (clean.isNotEmpty()) {
                put("props", JSONObject().apply {
                    clean.forEach { (key, value) -> put(key, value!!) }
                })
            }
        }

    /** A batch payload: envelope + the queued event objects (1–50). */
    fun eventsBatch(appVersion: String, installId: String, queuedEvents: List<String>): JSONObject =
        envelope(TelemetryConfig.EVENTS_SCHEMA, appVersion, installId).apply {
            put("events", JSONArray().apply { queuedEvents.forEach { put(JSONObject(it)) } })
        }

    /**
     * The flat crash payload (spec §3.3). [stored] is the on-device
     * crash file [CrashReporter] wrote at crash time; only the
     * allowlisted fields are carried over, capped at the schema's
     * lengths. The stored file's local bookkeeping fields
     * (occurredAt, sdkInt, causeClass, appVersionCode, …) never
     * reach the wire.
     */
    fun crash(appVersion: String, installId: String, stored: JSONObject): JSONObject =
        envelope(TelemetryConfig.CRASH_SCHEMA, appVersion, installId).apply {
            put("osVersion", osVersionOf(stored).take(32))
            put("deviceModel", stored.optString("deviceModel").ifBlank { "Android device" }.take(64))
            put("stackTrace", stackTraceOf(stored).take(16_000))
            put("recurring", false)
            crashTimestampOf(stored)?.let { put("crashTimestamp", it.take(32)) }
        }

    /** The stored file holds the bare release ("15"); the contract shape is "Android 15". */
    private fun osVersionOf(stored: JSONObject): String {
        val raw = stored.optString("osVersion").trim()
        return when {
            raw.isEmpty() -> "Android"
            raw.startsWith("Android") -> raw
            else -> "Android $raw"
        }
    }

    /** Header line (exception class, message, thread) followed by the stored frames. */
    private fun stackTraceOf(stored: JSONObject): String {
        val frames = stored.optString("stackTrace")
        val header = buildString {
            append(stored.optString("exceptionClass").ifBlank { "Unknown exception" })
            stored.optString("message").takeIf { it.isNotBlank() }
                ?.let { append(": ").append(it) }
            stored.optString("thread").takeIf { it.isNotBlank() }
                ?.let { append(" (thread: ").append(it).append(")") }
        }
        return if (frames.isBlank()) header else "$header\n$frames"
    }

    /** ISO-8601 local timestamp of the crash, from the stored epoch millis. */
    private fun crashTimestampOf(stored: JSONObject): String? {
        val occurredAt = stored.optLong("occurredAt", 0L)
        if (occurredAt > 0L) {
            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(occurredAt),
                ZoneId.systemDefault()
            ).toString()
        }
        return stored.optString("crashDate").takeIf { it.isNotBlank() }
    }
}
