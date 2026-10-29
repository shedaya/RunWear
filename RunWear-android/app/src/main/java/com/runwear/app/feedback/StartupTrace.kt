package com.runwear.app.feedback

import android.content.Context
import java.io.File

/**
 * Safe-boot diagnostic (1.1.2): append-only startup breadcrumbs in plain
 * file I/O — no DataStore, no coroutines, no singletons — so a mark can be
 * written from the earliest boot phases (ContentProvider, Application)
 * even when the rest of the app never comes up.
 *
 * One segment per launch, in order: provider-entry, app-oncreate-entry,
 * app-oncreate-done, setcontent-entry, mainactivity-created,
 * edge-to-edge-done, setcontent-called, composition-start,
 * navhost-entered, home-composable-entered, hero-card-entered,
 * first-frame, home-content-ready. (The 1.1.3 stages — setcontent-entry,
 * setcontent-called, navhost-entered, home-composable-entered,
 * hero-card-entered, first-frame — bisect the MainActivity →
 * composition gap the 1.1.2 trace exposed; the 1.1.4 stage,
 * edge-to-edge-done, splits enableEdgeToEdge() from the setContent{}
 * call after the 1.1.3 trace ended between them; real ordering of the
 * middle stages depends on which destination composes.) A launch that dies
 * mid-startup leaves a partial segment; [RawCrashBootstrap] uploads
 * the file on the next launch and clears it once the intake has
 * accepted it.
 *
 * Every call is fully guarded: a breadcrumb must never become the crash.
 */
object StartupTrace {

    const val FILE_NAME = "startup_trace.txt"

    fun mark(context: Context, stage: String) {
        try {
            File(context.filesDir, FILE_NAME)
                .appendText("${System.currentTimeMillis()} $stage\n")
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    /** The accumulated trace, or null when there is nothing to report. */
    fun read(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) null else file.readText().ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    /** Drops the trace after a confirmed upload, so it is never sent twice. */
    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
            // Best effort only.
        }
    }
}
