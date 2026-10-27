package com.runwear.app.diag

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.runwear.app.feedback.CrashReporter
import com.runwear.app.feedback.RawCrashBootstrap
import com.runwear.app.feedback.StartupTrace

/**
 * Safe-boot diagnostic (1.1.2, extended 1.1.3): the earliest code of ours
 * that can run. ContentProviders are installed before
 * Application.onCreate, and this one's high initOrder puts it ahead of
 * the other providers in the merged manifest (including androidx.startup's
 * InitializationProvider) — the phase where the 1.1.x crash loop dies.
 *
 * onCreate does three things, all guarded (inside their helpers or at
 * the call site): writes the "provider-entry" breadcrumb; installs the
 * crash handler
 * ([CrashReporter.install] — moved here in 1.1.3 so a crash anywhere
 * after the provider phase, Application.onCreate included, is captured;
 * the install call in RunWearApp.onCreate remains and is a guarded
 * no-op once this one has run, and the handler still chains to the
 * previous default handler exactly as before); and runs the raw
 * crash/trace upload ([RawCrashBootstrap]) so a stored crash report
 * leaves the device even if Application init, Hilt component creation,
 * or the Activity never happen. Serves no data; every data method is
 * a stub.
 */
class DiagBootProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: context
        if (appContext != null) {
            StartupTrace.mark(appContext, "provider-entry")
            // CrashReporter.install has no internal guard; wrap it so
            // the diagnostic can never become the crash it reports on.
            try {
                CrashReporter.install(appContext)
            } catch (_: Throwable) {
                // Best effort only.
            }
            RawCrashBootstrap.run(appContext)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
