package com.runwear.app.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exit-reason formatter pins (1.1.4). The reason/importance values are
 * the platform's own constants, verified against the android-36 SDK
 * stub jar (javap -constants on ApplicationExitInfo and
 * ActivityManager.RunningAppProcessInfo) — the whole point of the
 * instrument is that the NAME next to each death is right.
 *
 * Provenance of the instrument: 1.1.3 field evidence (D1 feedback
 * id 20) — ~18 launches dying inside setContent{} with zero Java crash
 * records. Android's exit history is the only witness left.
 */
class ExitReasonFormatTest {

    @Test
    fun reasonName_coversEveryPlatformReason() {
        val expected = mapOf(
            0 to "REASON_UNKNOWN",
            1 to "REASON_EXIT_SELF",
            2 to "REASON_SIGNALED",
            3 to "REASON_LOW_MEMORY",
            4 to "REASON_CRASH",
            5 to "REASON_CRASH_NATIVE",
            6 to "REASON_ANR",
            7 to "REASON_INITIALIZATION_FAILURE",
            8 to "REASON_PERMISSION_CHANGE",
            9 to "REASON_EXCESSIVE_RESOURCE_USAGE",
            10 to "REASON_USER_REQUESTED",
            11 to "REASON_USER_STOPPED",
            12 to "REASON_DEPENDENCY_DIED",
            13 to "REASON_OTHER",
            14 to "REASON_FREEZER",
            15 to "REASON_PACKAGE_STATE_CHANGE",
            16 to "REASON_PACKAGE_UPDATED"
        )
        expected.forEach { (value, name) ->
            assertEquals(name, ExitReasonFormat.reasonName(value))
        }
        // A future platform reason renders as itself, never as a wrong name.
        assertEquals("REASON_99 (unrecognized)", ExitReasonFormat.reasonName(99))
    }

    @Test
    fun importanceName_coversThePlatformValues() {
        assertEquals("IMPORTANCE_FOREGROUND", ExitReasonFormat.importanceName(100))
        assertEquals("IMPORTANCE_FOREGROUND_SERVICE", ExitReasonFormat.importanceName(125))
        assertEquals("IMPORTANCE_VISIBLE", ExitReasonFormat.importanceName(200))
        assertEquals("IMPORTANCE_PERCEPTIBLE", ExitReasonFormat.importanceName(230))
        assertEquals("IMPORTANCE_SERVICE", ExitReasonFormat.importanceName(300))
        assertEquals("IMPORTANCE_CACHED", ExitReasonFormat.importanceName(400))
        assertEquals("IMPORTANCE_GONE", ExitReasonFormat.importanceName(1000))
        assertEquals("IMPORTANCE_777 (unrecognized)", ExitReasonFormat.importanceName(777))
    }

    private fun record(
        timestampMs: Long = 1_793_090_400_000L,
        pid: Int = 12_345,
        reason: Int = 5,
        subreason: Int? = null,
        status: Int = 11,
        importance: Int = 100,
        description: String? = null,
        traceExcerpt: String? = null
    ) = ProcessExitRecord(
        timestampMs, pid, reason, subreason, status, importance, description, traceExcerpt
    )

    @Test
    fun formatRecord_nativeCrashNamesTheSignal() {
        val line = ExitReasonFormat.formatRecord(record())
        assertTrue(line.contains("pid=12345"))
        assertTrue(line.contains("REASON_CRASH_NATIVE(5)"))
        // Status 11 on a native crash is SIGSEGV — the answer we built
        // this instrument to read.
        assertTrue(line.contains("status=11 (SIGSEGV)"))
        assertTrue(line.contains("importance=IMPORTANCE_FOREGROUND(100)"))
        // No subreason in the public API: nothing rendered, nothing invented.
        assertFalse(line.contains("subreason"))
        assertFalse(line.contains("desc="))
    }

    @Test
    fun formatRecord_signaledNamesTheSignalAndSubreasonWhenPresent() {
        val line = ExitReasonFormat.formatRecord(
            record(reason = 2, status = 9, subreason = 3, description = "killed by lmk")
        )
        assertTrue(line.contains("REASON_SIGNALED(2)"))
        assertTrue(line.contains("status=9 (SIGKILL)"))
        assertTrue(line.contains("subreason=3"))
        assertTrue(line.contains("desc=\"killed by lmk\""))
    }

    @Test
    fun formatRecord_javaCrashDoesNotMisreadStatusAsSignal() {
        // REASON_CRASH: status is not a signal number and must not be
        // dressed up as one.
        val line = ExitReasonFormat.formatRecord(record(reason = 4, status = 11))
        assertTrue(line.contains("REASON_CRASH(4)"))
        assertTrue(line.contains("status=11"))
        assertFalse(line.contains("SIGSEGV"))
    }

    @Test
    fun formatRecord_descriptionIsFlattenedAndCapped() {
        val long = "word \"quoted\"\n".repeat(50)
        val line = ExitReasonFormat.formatRecord(record(description = long))
        assertFalse(line.contains("\n"))
        val desc = line.substringAfter("desc=\"").substringBeforeLast("\"")
        assertEquals(ExitReasonFormat.MAX_DESCRIPTION_CHARS + 3, desc.length)
        assertTrue(desc.endsWith("..."))
    }

    @Test
    fun format_keepsTheMostRecentExitsAndNotesOmissions() {
        // Eight exits with distinctive pids, oldest first; the cap is
        // too small for all of them, so the oldest must fall off and
        // the newest — the death under investigation — must survive.
        val records = (1..8).map { i ->
            record(timestampMs = 1_793_090_400_000L + i * 60_000L, pid = 1_000 + i)
        }
        val out = ExitReasonFormat.format(records, maxChars = 400)
        assertTrue(out.length <= 400)
        assertTrue(out.contains("older exit(s) omitted"))
        assertTrue("newest exit dropped: $out", out.contains("pid=1008"))
        assertFalse("oldest exit kept: $out", out.contains("pid=1001"))
    }

    @Test
    fun formatRecord_traceExcerptIsRenderedIndented() {
        val block = ExitReasonFormat.formatRecord(
            record(traceExcerpt = "backtrace:\n    #00 pc 0x1234 libfoo.so")
        )
        assertTrue(block.contains("trace (system, excerpt):"))
        assertTrue(block.contains("  | backtrace:"))
        assertTrue(block.contains("  |     #00 pc 0x1234 libfoo.so"))
        assertFalse(block.contains("truncated"))
    }

    @Test
    fun formatRecord_traceExcerptIsCappedWithAnExplicitMarker() {
        // A native tombstone runs long; the rendering must stop at the
        // per-record cap and SAY it stopped — never balloon the item.
        // (The rendered block is the excerpt cap plus the "  | " line
        // indentation, the header line, and the truncation marker.)
        val long = "frame line\n".repeat(500)
        val block = ExitReasonFormat.formatRecord(record(traceExcerpt = long))
        assertTrue(block.contains("[trace excerpt truncated]"))
        assertTrue(
            "excerpt rendering not capped: ${block.length}",
            block.length <= ExitReasonFormat.MAX_TRACE_EXCERPT_CHARS + 700
        )
        assertTrue(
            "excerpt rendering not bounded vs the raw trace: ${block.length}",
            block.length < long.length / 3
        )
    }

    @Test
    fun formatRecord_absentTraceExcerptRendersNothing() {
        val line = ExitReasonFormat.formatRecord(record(traceExcerpt = null))
        assertFalse(line.contains("trace"))
        val blank = ExitReasonFormat.formatRecord(record(traceExcerpt = "  "))
        assertFalse(blank.contains("trace"))
    }

    @Test
    fun format_emptyHistorySaysSo() {
        assertEquals("no exit records", ExitReasonFormat.format(emptyList(), 4_000))
    }

    @Test
    fun format_underCapKeepsEverythingWithoutANote() {
        val records = listOf(record(pid = 111), record(pid = 222))
        val out = ExitReasonFormat.format(records, maxChars = 4_000)
        assertTrue(out.contains("pid=111"))
        assertTrue(out.contains("pid=222"))
        assertFalse(out.contains("omitted"))
    }
}
