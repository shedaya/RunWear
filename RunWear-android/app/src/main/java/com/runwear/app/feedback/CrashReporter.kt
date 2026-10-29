package com.runwear.app.feedback

import android.content.Context
import android.os.Build
import com.runwear.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Minimal self-hosted crash capture (spec §3.3 — chosen over a third-party
 * crash SDK so no vendor ever sees our stack traces).
 *
 * On an uncaught exception, the crash context is written to a small file in
 * internal storage and the previous handler still runs, so the system crash
 * behavior is unchanged. Nothing is sent at crash time: on a later launch
 * the app reads the file and posts it through the intake, and only while the
 * user's improvement toggle is on (owner decision: crash reports are
 * automatic with plain-language disclosure, covered by the same opt-out).
 *
 * The payload carries no free text and no user content: app version, OS
 * version, device model, the exception type, and the top stack frames.
 */
object CrashReporter {

    private const val CRASH_FILE = "pending_crash.json"
    private const val MAX_STACK_FRAMES = 40

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Exception) {
                // Never let crash reporting change the crash itself.
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val frames = throwable.stackTrace.take(MAX_STACK_FRAMES).joinToString("\n") {
            it.toString()
        }
        val crash = JSONObject().apply {
            put("occurredAt", System.currentTimeMillis())
            put("crashDate", LocalDate.now(ZoneId.systemDefault()).toString())
            put("thread", thread.name)
            put("exceptionClass", throwable.javaClass.name)
            put("message", throwable.message ?: "")
            put("stackTrace", frames)
            put("causeClass", throwable.cause?.javaClass?.name ?: JSONObject.NULL)
            put("osVersion", Build.VERSION.RELEASE ?: "")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("appVersionCode", BuildConfig.VERSION_CODE)
        }
        File(context.filesDir, CRASH_FILE).writeText(crash.toString())
    }

    /** The stored crash from a previous run, or null when there is none. */
    fun pendingCrash(context: Context): JSONObject? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun clearPendingCrash(context: Context) {
        try {
            File(context.filesDir, CRASH_FILE).delete()
        } catch (_: Exception) {
            // Best effort only.
        }
    }
}
