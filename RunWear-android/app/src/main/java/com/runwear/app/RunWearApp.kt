package com.runwear.app

import android.app.Application
import androidx.work.Configuration
import com.runwear.app.feedback.CrashReporter
import com.runwear.app.feedback.FeedbackStore
import com.runwear.app.feedback.RawCrashBootstrap
import com.runwear.app.feedback.StartupTrace
import com.runwear.app.feedback.TelemetryClient
import com.runwear.app.widget.WidgetRefreshScheduler
import com.runwear.app.widget.WidgetUpgrade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class RunWearApp : Application(), Configuration.Provider {

    @Inject
    lateinit var feedbackStore: FeedbackStore

    @Inject
    lateinit var telemetryClient: TelemetryClient

    @Inject
    lateinit var billingManager: com.runwear.app.makeitme.BillingManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * WorkManager on-demand initialization (1.1.2 diagnostic): the
     * androidx.startup WorkManagerInitializer is removed from the
     * manifest, so WorkManager takes its configuration from here on first
     * use. Default configuration — the same one the initializer built.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        // Safe-boot diagnostic (1.1.2): the raw crash upload normally
        // already ran in the provider phase (DiagBootProvider); this call
        // is deduped there and only does the work if the provider phase
        // somehow never ran. Then the first Application breadcrumb —
        // both before super.onCreate(), i.e. before Hilt component init.
        RawCrashBootstrap.run(this)
        StartupTrace.mark(this, "app-oncreate-entry")

        super.onCreate()

        // Crash capture: since 1.1.3 the handler is already installed by
        // DiagBootProvider in the provider phase, so even a crash inside
        // super.onCreate() above is held; this call is the install of
        // record on any path that skipped the provider phase, and a
        // guarded no-op otherwise (CrashReporter.install is idempotent
        // and still chains to the previous default handler). The report
        // is only stored here; sending happens below, and only while
        // the user's improvement toggle is on.
        CrashReporter.install(this)

        // RW-14 billing: configure RevenueCat at startup so entitlement
        // state is current before any surface reads it. Idempotent and
        // crash-safe; the Make it me ViewModel also configures it.
        runCatching { billingManager.configure() }

        // Widget recovery backstop (spec §B.3.3): periodic refresh with
        // backoff so a failed widget load heals itself. KEEP = idempotent.
        // Scheduling the backstop must never take the app down with it:
        // WorkManager.getInstance throws if initialization was disturbed.
        runCatching { WidgetRefreshScheduler.schedule(this) }

        // Upgrade freshness (1.2.0): a widget render restored across an
        // app upgrade belongs to the OLD build until something forces a
        // recompose — do that on the first run of every newer version.
        runCatching { WidgetUpgrade.forceRefreshOnUpgrade(this, appScope) }

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

        // Safe-boot diagnostic (1.1.2): Application init completed.
        StartupTrace.mark(this, "app-oncreate-done")
    }
}
