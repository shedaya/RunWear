package com.runwear.app.feedback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.telemetryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "runwear_telemetry",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * All persisted state for the feedback + analytics system (spec §2–§3 and the
 * rating-moment addenda). Lives in its own DataStore file, separate from the
 * user's app preferences.
 *
 * Privacy posture (owner decisions, 2026-10-17):
 * - Analytics is OPT-IN. The toggle defaults OFF, and while it is off the
 *   analytics client emits nothing and opens no connections.
 * - The per-install random ID is created lazily — when the user opts in to
 *   analytics, or when they explicitly send a feedback report (the schema
 *   requires it) — never at first launch.
 * - Crash reports ride the same toggle: stored on the device at crash time,
 *   sent on a later launch only while the toggle is on.
 */
@Singleton
class FeedbackStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val SESSION_COUNT = intPreferencesKey("session_count")
        val OUTFIT_GENERATION_COUNT = intPreferencesKey("outfit_generation_count")
        val LAST_RATING_MOMENT_MS = longPreferencesKey("last_rating_moment_ms")
        val LAST_BAD_DAY = stringPreferencesKey("last_bad_day")
        val PENDING_EVENTS = stringPreferencesKey("pending_events")
        val PENDING_FEEDBACK = stringPreferencesKey("pending_feedback")
    }

    /** Set when the user submits a negative-category report this session. In-memory only. */
    @Volatile
    var negativeFeedbackThisSession: Boolean = false

    val analyticsOptInFlow: Flow<Boolean> = context.telemetryDataStore.data.map { prefs ->
        prefs[Keys.ANALYTICS_OPT_IN] ?: false
    }

    suspend fun isAnalyticsOptedIn(): Boolean = analyticsOptInFlow.first()

    suspend fun setAnalyticsOptIn(optedIn: Boolean) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.ANALYTICS_OPT_IN] = optedIn
            if (!optedIn) {
                // Spec §3.2: turning the toggle off stops all event emission
                // and flushes the local queue UNSENT.
                prefs.remove(Keys.PENDING_EVENTS)
            }
        }
    }

    /** The per-install random ID, created on first demand (opt-in or feedback send). */
    suspend fun installId(): String {
        val existing = context.telemetryDataStore.data.first()[Keys.INSTALL_ID]
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        context.telemetryDataStore.edit { prefs ->
            if (prefs[Keys.INSTALL_ID] == null) prefs[Keys.INSTALL_ID] = created
        }
        return context.telemetryDataStore.data.first()[Keys.INSTALL_ID] ?: created
    }

    suspend fun currentInstallIdOrNull(): String? =
        context.telemetryDataStore.data.first()[Keys.INSTALL_ID]

    /** One increment per process start (called from the Application class). Returns the new count. */
    suspend fun recordSessionStart(): Int {
        var count = 0
        context.telemetryDataStore.edit { prefs ->
            count = (prefs[Keys.SESSION_COUNT] ?: 0) + 1
            prefs[Keys.SESSION_COUNT] = count
        }
        return count
    }

    suspend fun sessionCount(): Int =
        context.telemetryDataStore.data.first()[Keys.SESSION_COUNT] ?: 0

    suspend fun recordOutfitGeneration(): Int {
        var count = 0
        context.telemetryDataStore.edit { prefs ->
            count = (prefs[Keys.OUTFIT_GENERATION_COUNT] ?: 0) + 1
            prefs[Keys.OUTFIT_GENERATION_COUNT] = count
        }
        return count
    }

    suspend fun outfitGenerationCount(): Int =
        context.telemetryDataStore.data.first()[Keys.OUTFIT_GENERATION_COUNT] ?: 0

    /**
     * Marks today as a "bad day" for rating-moment suppression: a crash, a
     * failed weather load, or a permission denial happened. The moment is
     * never shown on a bad day.
     */
    suspend fun markBadDay(date: LocalDate = LocalDate.now()) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.LAST_BAD_DAY] = date.toString()
        }
    }

    suspend fun markRatingMomentShown(nowMs: Long = System.currentTimeMillis()) {
        context.telemetryDataStore.edit { prefs ->
            prefs[Keys.LAST_RATING_MOMENT_MS] = nowMs
        }
    }

    /**
     * Rating-moment eligibility (addendum suppression rules): not in the
     * first 3 sessions, not on a bad day, not in a session where the user
     * submitted negative feedback, and at most once per 30 days.
     */
    suspend fun isRatingMomentEligible(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (negativeFeedbackThisSession) return false
        val prefs = context.telemetryDataStore.data.first()
        if ((prefs[Keys.SESSION_COUNT] ?: 0) <= FIRST_SESSIONS_SUPPRESSED) return false
        if (prefs[Keys.LAST_BAD_DAY] == LocalDate.now().toString()) return false
        val lastMoment = prefs[Keys.LAST_RATING_MOMENT_MS] ?: 0L
        if (nowMs - lastMoment < RATING_MOMENT_CAP_MS) return false
        return true
    }

    // ── Offline queues (JSON arrays persisted as strings) ──────────────────

    suspend fun enqueuePendingEvent(eventJson: String) {
        context.telemetryDataStore.edit { prefs ->
            val queue = JSONArray(prefs[Keys.PENDING_EVENTS] ?: "[]")
            queue.put(eventJson)
            while (queue.length() > MAX_QUEUED_EVENTS) queue.remove(0)
            prefs[Keys.PENDING_EVENTS] = queue.toString()
        }
    }

    /** Removes and returns all queued events. Used only after a confirmed successful send. */
    suspend fun peekPendingEvents(): List<String> {
        val raw = context.telemetryDataStore.data.first()[Keys.PENDING_EVENTS] ?: return emptyList()
        val queue = JSONArray(raw)
        return List(queue.length()) { queue.getString(it) }
    }

    suspend fun clearPendingEvents() {
        context.telemetryDataStore.edit { prefs -> prefs.remove(Keys.PENDING_EVENTS) }
    }

    suspend fun enqueuePendingFeedback(feedbackJson: String) {
        context.telemetryDataStore.edit { prefs ->
            val queue = JSONArray(prefs[Keys.PENDING_FEEDBACK] ?: "[]")
            queue.put(feedbackJson)
            while (queue.length() > MAX_QUEUED_FEEDBACK) queue.remove(0)
            prefs[Keys.PENDING_FEEDBACK] = queue.toString()
        }
    }

    suspend fun peekPendingFeedback(): List<String> {
        val raw = context.telemetryDataStore.data.first()[Keys.PENDING_FEEDBACK] ?: return emptyList()
        val queue = JSONArray(raw)
        return List(queue.length()) { queue.getString(it) }
    }

    suspend fun clearPendingFeedback() {
        context.telemetryDataStore.edit { prefs -> prefs.remove(Keys.PENDING_FEEDBACK) }
    }

    companion object {
        /** The moment is never shown during the first 3 sessions. */
        const val FIRST_SESSIONS_SUPPRESSED = 3
        /** App-side cap between rating moments: 30 days. */
        const val RATING_MOMENT_CAP_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_QUEUED_EVENTS = 200
        const val MAX_QUEUED_FEEDBACK = 20
    }
}
