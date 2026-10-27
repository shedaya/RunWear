package com.runwear.app.feedback

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Safe-boot diagnostic (1.1.4): one process exit from Android's exit-reason
 * history ([android.app.ActivityManager.getHistoricalProcessExitReasons]),
 * reduced to plain data so the rendering below is a pure function — no
 * Android types — and unit tests can pin the reason-name mapping and the
 * truncation rules on the JVM.
 *
 * [subreason] exists in the model because the brief asks for it "if
 * available": it is NOT available. Verified against the android-35 and
 * android-36 SDK stub jars, the public [android.app.ApplicationExitInfo]
 * exposes reason/status/importance/timestamp/pid/description but no
 * getSubreason() and no SUBREASON_* constants (those live only in the
 * internal system-server copy of the class). The collector therefore
 * always passes null today; the field renders only when non-null, so if
 * a future API exposes it the wire format already carries it.
 */
data class ProcessExitRecord(
    val timestampMs: Long,
    val pid: Int,
    val reason: Int,
    val subreason: Int?,
    val status: Int,
    val importance: Int,
    val description: String?,
    /**
     * A bounded excerpt of the record's system trace stream
     * (ApplicationExitInfo.getTraceInputStream) — the tombstone for a
     * native death, the ANR trace for a hang. The collector caps the
     * read at the stream (4 KB, never unbounded); the formatter caps
     * the rendering again (see [ExitReasonFormat.MAX_TRACE_EXCERPT_CHARS]).
     * Null when the system kept no trace for this exit.
     */
    val traceExcerpt: String? = null
)

/**
 * Renders [ProcessExitRecord]s as compact text lines for the intake.
 *
 * Why this instrument matters (what each outcome tells us about the
 * 1.1.x startup death, which leaves no Java crash record):
 * - REASON_CRASH_NATIVE — the process died on a native signal (the
 *   status carries it, e.g. 11 = SIGSEGV): the death is below the Java
 *   layer (Compose/skia/Choreographer native code), which is exactly
 *   why no uncaught-exception handler ever ran.
 * - REASON_SIGNALED — killed by a signal sent from outside (status =
 *   signal, e.g. 9 = SIGKILL): something external terminated us.
 * - REASON_CRASH — an ordinary Java crash the system recorded; combined
 *   with a missing handler marker it would mean our handler's writes
 *   themselves are failing.
 * - REASON_EXIT_SELF — the process called exit() / finished itself:
 *   some library is terminating the process deliberately.
 * - REASON_ANR / REASON_LOW_MEMORY / REASON_EXCESSIVE_RESOURCE_USAGE —
 *   a system policy kill (hang, memory pressure, watchdog), each with
 *   its own fix; the description field names the system's reason.
 * - REASON_USER_STOPPED / REASON_PACKAGE_UPDATED /
 *   REASON_PACKAGE_STATE_CHANGE / REASON_PERMISSION_CHANGE — benign
 *   administrative exits (force-stop, an update landing mid-test).
 */
object ExitReasonFormat {

    /** Per-field cap for the system-supplied description string. */
    const val MAX_DESCRIPTION_CHARS = 160

    /** Per-record cap for the rendered trace excerpt. */
    const val MAX_TRACE_EXCERPT_CHARS = 1_000

    // android.app.ApplicationExitInfo.REASON_* — values verified against
    // the android-36 SDK stub jar (javap -constants).
    fun reasonName(reason: Int): String = when (reason) {
        0 -> "REASON_UNKNOWN"
        1 -> "REASON_EXIT_SELF"
        2 -> "REASON_SIGNALED"
        3 -> "REASON_LOW_MEMORY"
        4 -> "REASON_CRASH"
        5 -> "REASON_CRASH_NATIVE"
        6 -> "REASON_ANR"
        7 -> "REASON_INITIALIZATION_FAILURE"
        8 -> "REASON_PERMISSION_CHANGE"
        9 -> "REASON_EXCESSIVE_RESOURCE_USAGE"
        10 -> "REASON_USER_REQUESTED"
        11 -> "REASON_USER_STOPPED"
        12 -> "REASON_DEPENDENCY_DIED"
        13 -> "REASON_OTHER"
        14 -> "REASON_FREEZER"
        15 -> "REASON_PACKAGE_STATE_CHANGE"
        16 -> "REASON_PACKAGE_UPDATED"
        else -> "REASON_$reason (unrecognized)"
    }

    // android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_* —
    // values verified against the android-36 SDK stub jar.
    fun importanceName(importance: Int): String = when (importance) {
        100 -> "IMPORTANCE_FOREGROUND"
        125 -> "IMPORTANCE_FOREGROUND_SERVICE"
        130 -> "IMPORTANCE_PERCEPTIBLE_PRE_26"
        150 -> "IMPORTANCE_TOP_SLEEPING_PRE_28"
        200 -> "IMPORTANCE_VISIBLE"
        230 -> "IMPORTANCE_PERCEPTIBLE"
        300 -> "IMPORTANCE_SERVICE"
        325 -> "IMPORTANCE_TOP_SLEEPING"
        350 -> "IMPORTANCE_CANT_SAVE_STATE"
        400 -> "IMPORTANCE_CACHED"
        500 -> "IMPORTANCE_EMPTY"
        1000 -> "IMPORTANCE_GONE"
        else -> "IMPORTANCE_$importance (unrecognized)"
    }

    /**
     * The exit status of a SIGNALED or CRASH_NATIVE record is the signal
     * number that killed the process. POSIX names for the usual suspects;
     * null when we have no name for it (render the bare number then).
     */
    fun signalName(status: Int): String? = when (status) {
        4 -> "SIGILL"
        5 -> "SIGTRAP"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        15 -> "SIGTERM"
        else -> null
    }

    /** One exit, one line. [record] fields are already plain data. */
    fun formatRecord(record: ProcessExitRecord): String {
        val timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(record.timestampMs),
            ZoneId.systemDefault()
        ).toString()
        return buildString {
            append(timestamp)
            append(" pid=").append(record.pid)
            append(' ').append(reasonName(record.reason))
                .append('(').append(record.reason).append(')')
            if (record.subreason != null) {
                append(" subreason=").append(record.subreason)
            }
            append(" status=").append(record.status)
            // The status reads as a signal exactly when the process died
            // by one (signaled from outside, or a native crash).
            if (record.reason == 2 || record.reason == 5) {
                signalName(record.status)?.let { append(" (").append(it).append(')') }
            }
            append(" importance=").append(importanceName(record.importance))
                .append('(').append(record.importance).append(')')
            val description = record.description?.trim().orEmpty()
            if (description.isNotEmpty()) {
                append(" desc=\"").append(truncate(description)).append('"')
            }
            val excerpt = record.traceExcerpt?.trim().orEmpty()
            if (excerpt.isNotEmpty()) {
                append("\n  trace (system, excerpt):")
                val capped = if (excerpt.length > MAX_TRACE_EXCERPT_CHARS) {
                    excerpt.substring(0, MAX_TRACE_EXCERPT_CHARS) +
                        "\n...[trace excerpt truncated]"
                } else {
                    excerpt
                }
                capped.lineSequence().forEach { line ->
                    append("\n  | ").append(line)
                }
            }
        }
    }

    private fun truncate(value: String): String {
        val oneLine = value.replace('\n', ' ').replace('"', '\'')
        return if (oneLine.length > MAX_DESCRIPTION_CHARS) {
            oneLine.substring(0, MAX_DESCRIPTION_CHARS) + "..."
        } else {
            oneLine
        }
    }

    /**
     * The whole history for one upload. [records] must be chronological
     * (oldest first — the collector sorts). When the rendered text would
     * exceed [maxChars], the OLDEST lines are dropped (the most recent
     * exits — the deaths under investigation — always survive) and a
     * note line says how many were omitted.
     */
    fun format(records: List<ProcessExitRecord>, maxChars: Int): String {
        if (records.isEmpty()) return "no exit records"
        val lines = records.map { formatRecord(it) }
        var start = 0
        while (start < lines.lastIndex) {
            val omitted = start
            val note = if (omitted > 0) noteLine(omitted) else null
            val total = (note?.length?.plus(1) ?: 0) +
                lines.subList(start, lines.size).sumOf { it.length + 1 } - 1
            if (total <= maxChars) break
            start++
        }
        val body = lines.drop(start).joinToString("\n")
        return if (start > 0) noteLine(start) + "\n" + body else body
    }

    private fun noteLine(omitted: Int) = "[$omitted older exit(s) omitted to fit the upload cap]"
}
