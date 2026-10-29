package com.runwear.app.feedback

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.runwear.app.BuildConfig
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Safe-boot diagnostic (1.1.2; watchdog item added 1.1.3; handler-marker
 * and process-exit-history items added 1.1.4): the raw, dependency-free
 * crash upload that runs BEFORE the app proper — from
 * [com.runwear.app.diag.DiagBootProvider]
 * (provider phase, before Application.onCreate) and again, deduped, at
 * the top of Application.onCreate.
 *
 * Why it exists: the normal crash path ([TelemetryClient.sendCrashReport])
 * is opt-in-gated and only fires from a coroutine after Hilt injection on
 * a LATER successful launch. A crash loop never reaches it, so a stored
 * [CrashReporter] report can sit in files/pending_crash.json forever,
 * unseen. This bootstrap moves the same payload out with nothing but
 * java.net.HttpURLConnection and org.json: zero Hilt, zero Supabase, zero
 * coroutines, zero DataStore. Payload shapes come from [TelemetryPayloads]
 * — the contract of record — so the intake sees exactly what the normal
 * path would have sent, and the crash's stack trace carries a marker line
 * identifying this diagnostic path.
 *
 * The upload runs on a plain worker thread joined with a hard cap, so the
 * boot that carries it can be delayed by at most [JOIN_MS] and never by a
 * dead network. On a confirmed crash send the pending file is deleted via
 * [CrashReporter.clearPendingCrash] — the reporter's own convention. The
 * startup trace ([StartupTrace]) rides as a second feedback item (the
 * crash contract has no attachment field) and is cleared on its own
 * confirmed send. Since 1.1.3 the startup watchdog's report
 * ([StartupWatchdog]) rides as a third feedback item the same way.
 *
 * Since 1.1.4 two more items ride along, aimed at the 1.1.3 field result
 * (~18 launches, every one dying inside the setContent{} call with NO
 * Java crash record at all):
 * - The crash handler's invocation marker ([CrashReporter.handlerMarker])
 *   as its own item — proof the uncaught-exception handler ran, naming
 *   the throwable it saw.
 * - Android's own process exit history
 *   ([ActivityManager.getHistoricalProcessExitReasons]) — the system's
 *   record of how previous processes of this package ended (native
 *   crash, signal, exit-self, ANR, low-memory, …), which exists even
 *   when the death bypassed every Java hook we own. Unlike the other
 *   artifacts this history cannot be cleared from the app side, so it
 *   is deduped by a token file: an upload happens only when the newest
 *   recorded exit (or the read-failure state) differs from the last
 *   one confirmed sent. The item always carries a one-line self-check
 *   stating whether the handler marker existed at this pass, so marker
 *   absence is stated, never just silent.
 *
 * 1.1.5 — fixing the silenced diagnostic. The 1.1.4 field result was
 * total silence: not one item of any kind reached the intake, on any
 * launch, even though the 1.1.3 pass had phoned home from the same
 * code path on every launch. The 1.1.4 pass wrapped ALL items in a
 * single try/catch, and two of the read helpers it newly depended on
 * before the first post ([CrashReporter.handlerMarker], read at the
 * top of the pass, and [CrashReporter.pendingCrash]) guarded their
 * file reads with catch-Exception only — any [Error] escaping them
 * (an OutOfMemoryError or a linkage/verification error surfacing at
 * first touch) aborted the whole pass before a single post, on every
 * launch, deterministically. The pass is therefore now executed as
 * isolated steps in the pinned [BootstrapPlan.STEP_ORDER]: each step
 * has its own catch-Throwable, the crash and trace uploads lead
 * exactly as they did in 1.1.3 before anything new runs, the marker
 * snapshot moved back behind them, and the exit-history read — now
 * also collecting a bounded excerpt of each record's system trace
 * stream — runs last and ships as its own post, so its failure can
 * never take the crash or trace path down with it.
 */
object RawCrashBootstrap {

    private const val JOIN_MS = 5_000L
    private const val TIMEOUT_MS = 2_000
    private const val DEDUPE_WINDOW_MS = 30_000L
    private const val MARKER_FILE = "raw_bootstrap_done.txt"
    private const val DIAG_INSTALL_ID_FILE = "diag_install_id.txt"
    private const val EXIT_TOKEN_FILE = "exit_history_token.txt"
    private const val TRACE_TEXT_CAP = 3_500
    private const val WATCHDOG_TEXT_CAP = 3_500

    // The exit-history body stays inside the same ~4 KB envelope every
    // bootstrap item has always used against the intake's feedback
    // text field. The system trace STREAM behind a record is a
    // different matter: a native tombstone can run to hundreds of KB,
    // so the collector reads at most TRACE_EXCERPT_BYTES of it into
    // memory — never the whole stream — and the formatter renders an
    // even smaller slice (see ExitReasonFormat.MAX_TRACE_EXCERPT_CHARS).
    private const val EXIT_BODY_CAP = 3_900
    private const val TRACE_EXCERPT_BYTES = 4_096
    private const val MAX_EXIT_RECORDS = 8
    private const val MARKER_LINE =
        "[safe-boot raw upload: 1.1.9 diagnostic build, provider-phase path, no opt-in gate]"

    private val UUID_PATTERN =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    @Volatile
    private var ranThisProcess = false

    /**
     * Runs the upload pass at most once per boot. Safe to call from both
     * the provider phase and Application.onCreate: the first caller does
     * the work, later callers in the same boot return immediately
     * (in-process flag, backed by a marker file with the pass timestamp).
     */
    fun run(context: Context) {
        try {
            if (ranThisProcess) return
            val appContext = context.applicationContext ?: context
            if (recentPass(appContext)) {
                ranThisProcess = true
                return
            }
            ranThisProcess = true
            val worker = Thread({ uploadAll(appContext) }, "rw-raw-bootstrap")
            worker.isDaemon = true
            worker.start()
            worker.join(JOIN_MS)
            recordPass(appContext)
        } catch (_: Throwable) {
            // The diagnostic must never become the crash.
        }
    }

    /**
     * One upload pass: the steps of [BootstrapPlan.STEP_ORDER], each
     * executed in isolation. Since 1.1.5 a step that throws — an
     * [Error] included — fails only itself: whatever it could not send
     * stays on disk for the next launch, and the remaining steps still
     * run. (1.1.4 ran the items inside a single try, so one escaping
     * Error silenced the entire pass; see the class doc.)
     */
    private fun uploadAll(context: Context) {
        val installId = resolveInstallId(context)

        // The handler-invocation marker of a previous death, snapshotted
        // by the "handlerMarker" step (the first step that needs it) and
        // read by the "exitHistory" step for its self-check line.
        var handlerMarker: String? = null

        val steps: List<Pair<String, () -> Unit>> = listOf(

            // 1. The stored crash from a previous launch, if any.
            "crash" to {
                val crash = CrashReporter.pendingCrash(context)
                if (crash != null) {
                    crash.put(
                        "stackTrace",
                        crash.optString("stackTrace") + "\n" + MARKER_LINE
                    )
                    val payload = TelemetryPayloads.crash(
                        BuildConfig.VERSION_NAME, installId, crash
                    )
                    if (postJson(TelemetryConfig.CRASH_ENDPOINT, payload)) {
                        CrashReporter.clearPendingCrash(context)
                    }
                }
            },

            // 2. The startup trace: how far the previous launch got.
            "trace" to {
                val trace = StartupTrace.read(context)
                if (trace != null) {
                    val tail = if (trace.length > TRACE_TEXT_CAP) {
                        trace.substring(trace.length - TRACE_TEXT_CAP)
                    } else {
                        trace
                    }
                    val text = "[SAFE-BOOT DIAGNOSTIC 1.1.9] Startup trace uploaded by " +
                        "the raw bootstrap (the previous launch's breadcrumbs; " +
                        "epoch-ms timestamps):\n" + tail
                    if (postFeedback(installId, text)) {
                        StartupTrace.clear(context)
                    }
                }
            },

            // 3. The startup watchdog report: a launch whose process was
            // still alive 8 s after setContent entry but never drew a
            // first frame — the hang case. Its own feedback item, cleared
            // on its own confirmed send, like the trace.
            "watchdog" to {
                val watchdog = StartupWatchdog.read(context)
                if (watchdog != null) {
                    val tail = if (watchdog.length > WATCHDOG_TEXT_CAP) {
                        watchdog.substring(watchdog.length - WATCHDOG_TEXT_CAP)
                    } else {
                        watchdog
                    }
                    val text = "[SAFE-BOOT DIAGNOSTIC 1.1.9] Startup watchdog report " +
                        "uploaded by the raw bootstrap (main-thread stack captured " +
                        "8s after setcontent-entry when no first frame had drawn):\n" + tail
                    if (postFeedback(installId, text)) {
                        StartupWatchdog.clear(context)
                    }
                }
            },

            // 4. The crash handler's invocation marker (1.1.4): one line
            // the uncaught-exception handler writes after the full crash
            // write. Its presence proves the handler ran and names what
            // it saw; its absence (stated in item 5) proves the death
            // never reached any Java handler. The snapshot is taken
            // here — behind the crash and trace uploads, not ahead of
            // them as 1.1.4 had it — and shared with item 5.
            "handlerMarker" to {
                handlerMarker = CrashReporter.handlerMarker(context)
                val marker = handlerMarker
                if (marker != null) {
                    val text = "[SAFE-BOOT DIAGNOSTIC 1.1.9] Crash-handler invocation " +
                        "marker uploaded by the raw bootstrap (written by the " +
                        "uncaught-exception handler after the full crash write; " +
                        "format: <epoch-ms> <throwable class>: <message>):\n" +
                        marker
                    if (postFeedback(installId, text)) {
                        CrashReporter.clearHandlerMarker(context)
                    }
                }
            },

            // 5. Android's process exit history (1.1.4): how the system
            // says previous processes of this package ended. Deduped by
            // token (the history cannot be cleared app-side); a changed
            // token — a newer exit, or a new read-failure state — is
            // what triggers an upload. The read NEVER stays silent: an
            // unavailable API, a throw, or an empty history all produce
            // an explicit body line. Its own step and its own post, last
            // in the pass: nothing it does can touch items 1–4.
            "exitHistory" to {
                val exitHistory = readExitHistory(context)
                if (exitHistory.token != exitTokenSeen(context)) {
                    val markerLine = if (handlerMarker != null) {
                        "handler_marker_check: handler_invoked.txt EXISTED at this " +
                            "pass (contents uploaded as their own item above) — the " +
                            "uncaught-exception handler DID run for a previous death."
                    } else {
                        "handler_marker_check: handler_invoked.txt ABSENT at this " +
                            "pass — the uncaught-exception handler never ran (or " +
                            "its marker write failed) for every previous death."
                    }
                    val text = "[SAFE-BOOT DIAGNOSTIC 1.1.9] Process exit history " +
                        "uploaded by the raw bootstrap (Android's own record of " +
                        "how previous processes of this package ended; most " +
                        "recent last):\n" + markerLine + "\n" + exitHistory.body
                    if (postFeedback(installId, text)) {
                        recordExitToken(context, exitHistory.token)
                    }
                }
            }
        )

        // Execute in the pinned plan order — not merely the order the
        // steps happen to be declared above — each in its own
        // catch-Throwable, so one step's failure (Error included)
        // neither aborts the pass nor is mistaken for another step's
        // silence.
        val byId = steps.toMap()
        for (stepId in BootstrapPlan.STEP_ORDER) {
            val step = byId[stepId] ?: continue
            try {
                step()
            } catch (_: Throwable) {
                // Best effort: whatever failed stays on disk for the
                // next launch, and the remaining steps still run.
            }
        }
    }

    /** One feedback item on the existing contract. True only on a confirmed send. */
    private fun postFeedback(installId: String, text: String): Boolean {
        val bundle = JSONObject().apply {
            put("engineVersion", GetOutfitRecommendationUseCase.ENGINE_VERSION)
        }
        val payload = TelemetryPayloads.feedback(
            appVersion = BuildConfig.VERSION_NAME,
            installId = installId,
            category = FeedbackCategory.SOMETHING_WRONG,
            text = text,
            replyEmail = null,
            entryPoint = null,
            bundle = bundle
        )
        return postJson(TelemetryConfig.FEEDBACK_ENDPOINT, payload)
    }

    /** The dedupe token + rendered body for one exit-history read. */
    private class ExitHistoryResult(val token: String, val body: String)

    /**
     * Reads the system's exit-reason history for this package. The
     * token identifies the state read — "ts:<newest exit>" when records
     * exist, a stable "err:"/"empty" marker otherwise — so the same
     * state is never uploaded twice, while any NEW death (a newer exit
     * timestamp) or a changed failure mode uploads again.
     */
    private fun readExitHistory(context: Context): ExitHistoryResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ExitHistoryResult(
                "err:sdk",
                "exit history unavailable: SDK ${Build.VERSION.SDK_INT} is below " +
                    "API 30, getHistoricalProcessExitReasons does not exist on this OS."
            )
        }
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return ExitHistoryResult(
                        "err:no-activity-manager",
                        "exit history unavailable: no ActivityManager service."
                    )
            val infos = activityManager.getHistoricalProcessExitReasons(
                context.packageName, 0, MAX_EXIT_RECORDS
            )
            if (infos.isNullOrEmpty()) {
                ExitHistoryResult(
                    "empty",
                    "exit history: the API returned no exit records for this package."
                )
            } else {
                // subreason is null by construction: the public SDK has
                // no getSubreason() (see ProcessExitRecord's doc).
                val records = infos.map { info ->
                    ProcessExitRecord(
                        timestampMs = info.timestamp,
                        pid = info.pid,
                        reason = info.reason,
                        subreason = null,
                        status = info.status,
                        importance = info.importance,
                        description = info.description,
                        traceExcerpt = readTraceExcerpt(info)
                    )
                }.sortedBy { it.timestampMs }
                ExitHistoryResult(
                    "ts:${records.last().timestampMs}:${records.size}",
                    ExitReasonFormat.format(records, EXIT_BODY_CAP)
                )
            }
        } catch (t: Throwable) {
            ExitHistoryResult(
                "err:${t.javaClass.name}",
                "exit history read failed: ${t.javaClass.name}: ${t.message ?: ""}"
            )
        }
    }

    /**
     * The first [TRACE_EXCERPT_BYTES] of a record's system trace
     * stream — the tombstone for a native death, the ANR trace for a
     * hang. The stream is NEVER read unbounded: a native tombstone can
     * run to hundreds of KB, so the read stops at the byte cap whatever
     * the stream still holds, and any failure (no stream for this
     * record, a stream that errors or blocks past the worker's join
     * window) yields null rather than an exception — the record ships
     * without its excerpt, never not at all.
     */
    private fun readTraceExcerpt(info: ApplicationExitInfo): String? {
        return try {
            val stream = info.traceInputStream ?: return null
            stream.use { input ->
                val buffer = ByteArray(TRACE_EXCERPT_BYTES)
                var filled = 0
                while (filled < buffer.size) {
                    val read = input.read(buffer, filled, buffer.size - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled == 0) {
                    null
                } else {
                    String(buffer, 0, filled, Charsets.UTF_8)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun exitTokenSeen(context: Context): String? {
        return try {
            val file = File(context.filesDir, EXIT_TOKEN_FILE)
            if (file.exists()) file.readText().trim().ifBlank { null } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun recordExitToken(context: Context, token: String) {
        try {
            File(context.filesDir, EXIT_TOKEN_FILE).writeText(token)
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    /**
     * The per-install ID without touching DataStore: the telemetry
     * preferences file is a protobuf whose string values sit in plain
     * UTF-8, and the install ID is the only UUID it ever holds, so a byte
     * scan finds it. If the user never opted in or sent feedback there is
     * no ID yet; fall back to a diagnostic ID persisted in a plain file so
     * raw uploads stay attributable to this install across launches.
     */
    private fun resolveInstallId(context: Context): String {
        try {
            val storeFile = File(
                File(context.filesDir, "datastore"),
                "runwear_telemetry.preferences_pb"
            )
            if (storeFile.exists()) {
                val asText = String(storeFile.readBytes(), Charsets.ISO_8859_1)
                UUID_PATTERN.find(asText)?.let { return it.value }
            }
        } catch (_: Throwable) {
            // Fall through to the diagnostic ID.
        }
        try {
            val diagFile = File(context.filesDir, DIAG_INSTALL_ID_FILE)
            if (diagFile.exists()) {
                val saved = diagFile.readText().trim()
                if (UUID_PATTERN.matches(saved)) return saved
            }
            val created = UUID.randomUUID().toString()
            diagFile.writeText(created)
            return created
        } catch (_: Throwable) {
            return UUID.randomUUID().toString()
        }
    }

    private fun recentPass(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, MARKER_FILE)
            if (!file.exists()) false
            else System.currentTimeMillis() - file.lastModified() < DEDUPE_WINDOW_MS
        } catch (_: Throwable) {
            false
        }
    }

    private fun recordPass(context: Context) {
        try {
            File(context.filesDir, MARKER_FILE).writeText(
                System.currentTimeMillis().toString()
            )
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    private fun postJson(url: String, payload: JSONObject): Boolean {
        var connection: HttpURLConnection? = null
        return try {
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
        } catch (_: Throwable) {
            false
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {
                // Nothing to salvage.
            }
        }
    }
}
