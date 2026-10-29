package com.runwear.app.feedback

import android.content.Context
import android.os.Build
import com.runwear.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
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
 * version, device model, the exception type, and the stack trace.
 *
 * Storage history: through 1.1.2 the file held only the TOP exception's
 * frames (field "stackTrace", ≤40 frames) plus the immediate cause's class
 * name in the "causeClass" bookkeeping field — the actual root cause of a
 * wrapped crash (e.g. "Unable to start activity", whose cause is the real
 * failure) never reached the wire, because [TelemetryPayloads] sends only
 * the header + those frames. Since 1.1.3 the file ALSO holds
 * "fullStackTrace": the complete [Throwable.printStackTrace] output with
 * every "Caused by:" level, plus the immediate cause's message in
 * "causeMessage". [TelemetryPayloads] prefers the full chain for the wire
 * payload. The frames-only "stackTrace" field is kept unchanged so the
 * file contract is otherwise identical.
 *
 * The handler also writes a tiny invocation marker
 * ([HANDLER_MARKER_FILE]): one line of plain file I/O — epoch ms,
 * throwable class, message. Field evidence from 1.1.3: the startup
 * death inside setContent{} produced NO crash file at all, and the
 * evidence could not distinguish "the handler never ran" (native
 * death, external kill, exit() call — none of which route through an
 * uncaught-exception handler) from "the handler ran but its write
 * failed". The marker separates the two: absent alongside an absent
 * crash file, the handler provably never ran; present without a crash
 * file, the full write is what failed. Write ORDER: 1.1.4 wrote the
 * marker first; 1.1.5 restores the crash report as the handler's
 * first act and writes the marker second — the full report is the
 * artifact that matters, and no write, however small, stands in
 * front of it. (The diagnostic value is unchanged: the marker is
 * written in the same handler invocation, chained before the
 * previous handler runs.)
 */
object CrashReporter {

    private const val CRASH_FILE = "pending_crash.json"
    private const val HANDLER_MARKER_FILE = "handler_invoked.txt"
    private const val MAX_STACK_FRAMES = 40
    private const val MAX_FULL_TRACE_CHARS = 32_000
    private const val MAX_MARKER_MESSAGE_CHARS = 300

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // The full crash report FIRST (the 1.1.5 order; 1.1.4
                // wrote the invocation marker first, and no write —
                // however small — stands in front of the report).
                writeCrash(appContext, thread, throwable)
            } catch (_: Exception) {
                // Never let crash reporting change the crash itself.
            } finally {
                // The invocation marker SECOND, still inside the same
                // handler invocation and still before the previous
                // handler takes over: its presence/absence on the next
                // launch separates "handler never ran" from "handler
                // ran, write failed". Maximally guarded, like
                // everything in the diagnostic path.
                try {
                    writeHandlerMarker(appContext, throwable)
                } catch (_: Throwable) {
                    // Never let crash reporting change the crash itself.
                }
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * The one-line invocation marker: "<epoch-ms> <throwable class>:
     * <message>". Deliberately the simplest possible write — a single
     * small file, no JSON, no formatting beyond flattening newlines —
     * so almost nothing in it can fail. Written after [writeCrash] in
     * the handler (see the class doc for the order's history).
     */
    private fun writeHandlerMarker(context: Context, throwable: Throwable) {
        val message = (throwable.message ?: "")
            .replace('\n', ' ')
            .take(MAX_MARKER_MESSAGE_CHARS)
        val line = "${System.currentTimeMillis()} ${throwable.javaClass.name}: $message"
        File(context.filesDir, HANDLER_MARKER_FILE).writeText(line)
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val frames = throwable.stackTrace.take(MAX_STACK_FRAMES).joinToString("\n") {
            it.toString()
        }
        // The complete chain, exactly as printStackTrace renders it —
        // wrapper header, frames, and every "Caused by:" level down to
        // the root failure. This is the field that answers "what threw"
        // when the top exception is a framework wrapper.
        val fullTrace = try {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            writer.toString().take(MAX_FULL_TRACE_CHARS)
        } catch (_: Exception) {
            ""
        }
        val crash = JSONObject().apply {
            put("occurredAt", System.currentTimeMillis())
            put("crashDate", LocalDate.now(ZoneId.systemDefault()).toString())
            put("thread", thread.name)
            put("exceptionClass", throwable.javaClass.name)
            put("message", throwable.message ?: "")
            put("stackTrace", frames)
            put("fullStackTrace", fullTrace)
            put("causeClass", throwable.cause?.javaClass?.name ?: JSONObject.NULL)
            put("causeMessage", throwable.cause?.message ?: JSONObject.NULL)
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
        } catch (e: Throwable) {
            // Throwable, not Exception (1.1.5): this read sits at the
            // head of the bootstrap pass, and an Error escaping it
            // (OOM on a corrupt oversized file, a linkage error) must
            // degrade to "no crash stored", never abort the pass.
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

    /** The handler-invocation marker from a previous run, or null when the handler never wrote one. */
    fun handlerMarker(context: Context): String? {
        val file = File(context.filesDir, HANDLER_MARKER_FILE)
        if (!file.exists()) return null
        return try {
            file.readText().ifBlank { null }
        } catch (_: Throwable) {
            // Throwable, not Exception (1.1.5): see pendingCrash.
            null
        }
    }

    /** Drops the marker after a confirmed upload, so it is never sent twice. */
    fun clearHandlerMarker(context: Context) {
        try {
            File(context.filesDir, HANDLER_MARKER_FILE).delete()
        } catch (_: Exception) {
            // Best effort only.
        }
    }
}
