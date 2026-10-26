package com.runwear.app

import android.app.Application
import com.runwear.app.feedback.CrashReporter
import com.runwear.app.feedback.FeedbackStore
import com.runwear.app.feedback.TelemetryClient
import com.runwear.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class RunWearApp : Application() {

    @Inject
    lateinit var feedbackStore: FeedbackStore

    @Inject
    lateinit var telemetryClient: TelemetryClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Crash capture first, so even a crash later in this startup is held.
        // The report is only stored here; sending happens below, and only
        // while the user's improvement toggle is on.
        CrashReporter.install(this)

        // Widget recovery backstop (spec §B.3.3): periodic refresh with
        // backoff so a failed widget load heals itself. KEEP = idempotent.
        // Scheduling the backstop must never take the app down with it:
        // WorkManager.getInstance throws if initialization was disturbed.
        runCatching { WidgetRefreshScheduler.schedule(this) }

        appScope.launch {
            // One session per process start (rating-moment suppression needs it).
            feedbackStore.recordSessionStart()

            // A crash from a previous run: today counts as a bad day for the
            // rating moment, and the report goes out if the toggle allows it.
            val crash = CrashReporter.pendingCrash(this@RunWearApp)
            if (crash != null) {
                val crashDate = crash.optString("crashDate")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                feedbackStore.markBadDay(crashDate ?: LocalDate.now())
                if (telemetryClient.sendCrashReport(crash)) {
                    CrashReporter.clearPendingCrash(this@RunWearApp)
                }
            }

            telemetryClient.flushPendingFeedback()
            telemetryClient.flushEvents()
        }
    }
}
