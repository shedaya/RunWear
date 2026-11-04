package com.runwear.app.weariverun

import java.util.UUID

/**
 * GEN-3 (WS-C, Sol's Nov 4 correction — binding, supersedes the
 * once-per-day / one-entry-per-day-per-place framing): Wear I've Run
 * records ONE entry PER SESSION. Within a session the entry updates
 * IN PLACE through every location and weather change — updates
 * replace, never accumulate. The scrapbook keeps the session's last
 * settled version. A new session starts a new entry.
 *
 * OPERATIONAL DEFINITION (verbatim, for the gate report):
 * "A Wear I've Run session is one continuous period of app use
 * beginning when MainActivity is created for a fresh task
 * (savedInstanceState == null) — which includes every cold process
 * start — and ending when that task is finished or the app process
 * dies. Backgrounding, foregrounding, configuration-change
 * recreation, and navigation within the task do not start a new
 * session. The session id lives only in process memory and is never
 * persisted, so process death always mints a new session on the
 * next launch."
 *
 * Implementation: [currentId] lazily mints one id per process;
 * MainActivity calls [beginNewSession] on a fresh-task creation
 * (savedInstanceState == null), which covers the case where the
 * task was finished while the process survived in the cache.
 */
object WearIveRunSession {

    const val OPERATIONAL_DEFINITION: String =
        "A Wear I've Run session is one continuous period of app use " +
            "beginning when MainActivity is created for a fresh task " +
            "(savedInstanceState == null) — which includes every cold process " +
            "start — and ending when that task is finished or the app process " +
            "dies. Backgrounding, foregrounding, configuration-change " +
            "recreation, and navigation within the task do not start a new " +
            "session. The session id lives only in process memory and is never " +
            "persisted, so process death always mints a new session on the " +
            "next launch."

    @Volatile
    private var current: String? = null

    /** The id of the session in progress in THIS process. */
    fun currentId(): String {
        current?.let { return it }
        return synchronized(this) {
            current ?: UUID.randomUUID().toString().also { current = it }
        }
    }

    /** Mint a fresh session id (fresh-task creation). */
    fun beginNewSession(): String = synchronized(this) {
        UUID.randomUUID().toString().also { current = it }
    }
}
