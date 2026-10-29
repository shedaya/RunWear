package com.runwear.app.feedback

import android.content.Context
import android.os.Looper
import java.io.File

/**
 * Safe-boot diagnostic (1.1.3): the hang-versus-death discriminator.
 *
 * Armed from MainActivity's "setcontent-entry" breadcrumb. A plain
 * daemon thread sleeps [DELAY_MS]; if the process is still alive at
 * that point and no first frame has been marked ([noteFirstFrame],
 * called from MainActivity's first pre-draw), the main thread is
 * stuck somewhere that never lets a frame draw — so the watchdog
 * appends a "watchdog-still-alive-no-frame" line plus the main
 * thread's current stack to watchdog_main_stack.txt.
 *
 * Reading the result on the next launch:
 * - Watchdog file present → the process HUNG; the stack shows where
 *   the main thread is parked.
 * - No watchdog file and no further breadcrumbs → the process DIED
 *   before the delay elapsed (crash or native death); the crash
 *   handler's pending_crash.json is the evidence for that case.
 *
 * [RawCrashBootstrap] uploads the file on the next launch as its own
 * feedback item and clears it once the intake has accepted it. Plain
 * file I/O and fully guarded throughout, like [StartupTrace]: the
 * diagnostic must never become the crash.
 */
object StartupWatchdog {

    const val FILE_NAME = "watchdog_main_stack.txt"

    private const val DELAY_MS = 8_000L

    @Volatile
    private var firstFrameSeen = false

    @Volatile
    private var armed = false

    /** Called from the first-frame breadcrumb: disarms the report. */
    fun noteFirstFrame() {
        firstFrameSeen = true
    }

    /** Spawns the one-shot watchdog thread. Safe to call more than once. */
    fun arm(context: Context) {
        try {
            if (armed) return
            armed = true
            val appContext = context.applicationContext ?: context
            val worker = Thread({ checkAfterDelay(appContext) }, "rw-startup-watchdog")
            worker.isDaemon = true
            worker.start()
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    private fun checkAfterDelay(context: Context) {
        try {
            Thread.sleep(DELAY_MS)
            if (firstFrameSeen) return
            val mainThread = Looper.getMainLooper().thread
            val stack = Thread.getAllStackTraces()[mainThread]
            val report = buildString {
                append(System.currentTimeMillis())
                append(" watchdog-still-alive-no-frame\n")
                append("main thread '").append(mainThread.name)
                    .append("' state=").append(mainThread.state).append('\n')
                if (stack == null) {
                    append("(main thread stack unavailable)\n")
                } else {
                    stack.forEach { frame -> append("\tat ").append(frame).append('\n') }
                }
            }
            File(context.filesDir, FILE_NAME).appendText(report)
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    /** The accumulated watchdog reports, or null when there are none. */
    fun read(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) null else file.readText().ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    /** Drops the reports after a confirmed upload. */
    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
            // Best effort only.
        }
    }
}
