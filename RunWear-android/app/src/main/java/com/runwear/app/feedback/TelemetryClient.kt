package com.runwear.app.feedback

import com.runwear.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one network door for feedback, analytics events, and crash reports
 * (spec §2.1). Plain HTTPS POSTs of small JSON payloads to our own intake —
 * no third-party SDK, no advertising ID, no identity beyond the random
 * per-install ID.
 *
 * Analytics discipline: events are recorded ONLY while the user has opted in.
 * When the toggle is off, [recordEvent] is a no-op and nothing in this class
 * opens a connection for analytics or crash reports. Feedback is different:
 * it is the user deliberately talking to us, so it sends on their tap
 * regardless of the analytics toggle.
 */
@Singleton
class TelemetryClient @Inject constructor(
    private val store: FeedbackStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var analyticsOptedIn: Boolean = false

    init {
        scope.launch {
            store.analyticsOptInFlow.collectLatest { analyticsOptedIn = it }
        }
    }

    /** Common envelope fields for every payload (spec §2.2). */
    private suspend fun envelope(schema: String): JSONObject = JSONObject().apply {
        put("schema", schema)
        put("app", TelemetryConfig.APP_ID)
        put("platform", TelemetryConfig.PLATFORM)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("appVersionCode", BuildConfig.VERSION_CODE)
        put("installId", store.installId())
    }

    // ── Analytics events ────────────────────────────────────────────────────

    /**
     * Records one analytics event. No-op unless the user has opted in: no
     * queueing, no network, nothing counted. Events batch in a local queue
     * and flush on app start and whenever the queue grows past a few items.
     */
    fun recordEvent(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!analyticsOptedIn) return
        scope.launch {
            // Re-check inside the coroutine: the toggle may have flipped.
            if (!store.isAnalyticsOptedIn()) return@launch
            val event = JSONObject().apply {
                put("name", name)
                put("at", System.currentTimeMillis())
                put("props", JSONObject().apply {
                    props.forEach { (key, value) -> if (value != null) put(key, value) }
                })
            }
            store.enqueuePendingEvent(event.toString())
            if (store.peekPendingEvents().size >= FLUSH_THRESHOLD) {
                flushEvents()
            }
        }
    }

    /** Sends the queued events as one batch. Keeps the queue on any failure. */
    suspend fun flushEvents() {
        if (!store.isAnalyticsOptedIn()) return
        val queued = store.peekPendingEvents()
        if (queued.isEmpty()) return
        val payload = envelope(TelemetryConfig.EVENTS_SCHEMA).apply {
            put("events", JSONArray().apply { queued.forEach { put(JSONObject(it)) } })
        }
        if (postJson(TelemetryConfig.EVENTS_ENDPOINT, payload)) {
            store.clearPendingEvents()
        }
    }

    // ── Feedback ────────────────────────────────────────────────────────────

    /**
     * Sends one feedback report now. Returns true when the intake accepted
     * it; on false the caller queues the payload for a later retry.
     */
    suspend fun sendFeedbackNow(report: JSONObject): Boolean =
        postJson(TelemetryConfig.FEEDBACK_ENDPOINT, report)

    /** Builds the full feedback payload (spec §2.2 envelope + report fields). */
    suspend fun buildFeedbackPayload(
        category: FeedbackCategory,
        message: String,
        replyEmail: String?,
        bundle: JSONObject
    ): JSONObject = envelope(TelemetryConfig.FEEDBACK_SCHEMA).apply {
        put("category", category.id)
        put("message", message)
        if (!replyEmail.isNullOrBlank()) put("replyEmail", replyEmail.trim())
        put("bundle", bundle)
        put("sentAt", System.currentTimeMillis())
    }

    /** Retries any feedback that failed to send earlier. */
    suspend fun flushPendingFeedback() {
        val queued = store.peekPendingFeedback()
        if (queued.isEmpty()) return
        var allSent = true
        for (item in queued) {
            if (!postJson(TelemetryConfig.FEEDBACK_ENDPOINT, JSONObject(item))) {
                allSent = false
                break
            }
        }
        if (allSent) store.clearPendingFeedback()
    }

    // ── Crash reports ───────────────────────────────────────────────────────

    /**
     * Sends a stored crash report. Gated on the same opt-in toggle as
     * analytics (owner decision: crash reports are automatic with plain
     * disclosure, covered by the same opt-out). Returns false — and sends
     * nothing — while the toggle is off.
     */
    suspend fun sendCrashReport(crash: JSONObject): Boolean {
        if (!store.isAnalyticsOptedIn()) return false
        val payload = envelope(TelemetryConfig.CRASH_SCHEMA).apply {
            put("crash", crash)
        }
        return postJson(TelemetryConfig.CRASH_ENDPOINT, payload)
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private suspend fun postJson(url: String, payload: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { out ->
                    out.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                connection.responseCode in 200..299
            } catch (e: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val FLUSH_THRESHOLD = 8
    }
}
