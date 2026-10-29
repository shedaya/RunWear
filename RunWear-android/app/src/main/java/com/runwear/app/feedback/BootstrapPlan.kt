package com.runwear.app.feedback

/**
 * Safe-boot diagnostic: the raw bootstrap's pass plan (1.1.5).
 *
 * The pass steps [RawCrashBootstrap] executes on every launch, in
 * execution order. Kept as plain data in its own file — no Android
 * types — so a unit test can pin the order on the JVM:
 *
 * 1. "crash" — the stored crash report from a previous launch.
 * 2. "trace" — the startup breadcrumb trace.
 *
 * Those two lead, exactly as they did in the last field-proven build
 * (1.1.3): nothing added later may run before them, and each step is
 * isolated (its own catch-Throwable) so a failure in any step — the
 * new ones included — can never starve the two that carry the crash
 * evidence. The 1.1.4 additions ride behind them, in the order they
 * were introduced: the startup watchdog report (1.1.3), the crash
 * handler's invocation marker (1.1.4), and the process exit history
 * (1.1.4) last — it is the only step that talks to a system service
 * and reads system trace streams, so it is the one step whose cost
 * and failure modes are least predictable.
 */
object BootstrapPlan {

    val STEP_ORDER: List<String> = listOf(
        "crash",
        "trace",
        "watchdog",
        "handlerMarker",
        "exitHistory"
    )
}
