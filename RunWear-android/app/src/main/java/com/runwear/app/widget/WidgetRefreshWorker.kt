package com.runwear.app.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.EntryPointAccessors
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The `refreshing` Glance state flag behind the ↻ icon's in-progress
 * affordance. Stored as the epoch-ms moment the refresh started; readers
 * treat anything older than [WidgetFreshness.REFRESH_FLAG_TTL_MS] as dead
 * (the process was killed mid-refresh) and ignore it.
 */
object RefreshFlag {

    val KEY = longPreferencesKey("widget_refreshing_since")

    suspend fun set(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[KEY] = System.currentTimeMillis()
        }
    }

    suspend fun clear(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(KEY)
        }
    }

    suspend fun clearAll(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(OutfitWidget::class.java)
        ids.forEach { runCatching { clear(context, it) } }
    }
}

/**
 * WorkManager backstop (spec §B.3.3): the automatic-recovery path that makes
 * tap-to-retry unnecessary. Periodic, network-constrained; a failed load
 * returns [Result.retry] so WorkManager's exponential backoff drives the
 * next attempt instead of the user.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java
        )
        return when (val outcome = WidgetDataLoader.load(
            weatherRepository = entryPoint.weatherRepository(),
            preferencesRepository = entryPoint.preferencesRepository(),
            locationProvider = entryPoint.locationProvider(),
            getOutfitRecommendation = entryPoint.getOutfitRecommendation()
        )) {
            is WidgetDataLoader.Outcome.Success -> {
                WidgetPublisher.publish(applicationContext, outcome.lastGood)
                Result.success()
            }
            WidgetDataLoader.Outcome.NoPermission -> Result.success() // nothing a retry can fix
            is WidgetDataLoader.Outcome.Failed -> Result.retry()
        }
    }
}

object WidgetRefreshScheduler {

    private const val UNIQUE_NAME = "runwear_widget_refresh"

    /** Idempotent (KEEP): safe to call from Application, Activity and the widget receiver. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
