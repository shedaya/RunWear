package com.runwear.app.ui.screens

import com.runwear.app.ui.components.HeroSheetFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * GEN-6 WAVE C (RW-53 — the date-path break, device-confirmed on
 * gen-5 vc36: "can change the time but not the date").
 *
 * Diagnosis: the break was at ENTRY, not in the ViewModel.
 * MainViewModel.selectDate re-solves correctly, and
 * DatePickerSheet existed — but gen-5 (RW-28 item 12) removed
 * the duplicate DateTimePill row from the sheet headline and
 * left the headline's moment segment wired to the TIME sheet
 * alone; onDateClick dangled unrendered in HeroSheetBlock, and
 * the Choose-a-Time sheet named its day as inert text. On the
 * phone layout no rendered control could open the date sheet.
 *
 * These pins hold the fix: the headline's date segment, the
 * time sheet's day door, and the date sheet's Confirm-gated
 * draft mechanics (design-spec §6 C1, applied to the sibling
 * day picker per frame 42).
 */
class DatePickerDraftTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    @Test
    fun `draft opens on the committed selection`() {
        assertEquals(today, DatePickerDraftPolicy.open(today))
        assertEquals(
            today.plusDays(3),
            DatePickerDraftPolicy.open(today.plusDays(3))
        )
    }

    @Test
    fun `tapping a day moves the draft only - confirm commits it`() {
        val draft = DatePickerDraftPolicy.select(today, today.plusDays(2), today)
        assertEquals(today.plusDays(2), draft)
        assertEquals(today.plusDays(2), DatePickerDraftPolicy.confirm(draft))
    }

    @Test
    fun `cancel restores the pre-open selection exactly`() {
        val original = today.plusDays(1)
        val draft = DatePickerDraftPolicy.select(original, today.plusDays(4), today)
        assertEquals(today.plusDays(4), draft)
        assertEquals(original, DatePickerDraftPolicy.cancel(original))
    }

    @Test
    fun `beyond-horizon days refuse the draft`() {
        // Horizon is 7 days (offsets 0..6 selectable); offset 7+
        // is "Beyond forecast" and can never enter the draft.
        val draft = DatePickerDraftPolicy.select(today, today.plusDays(9), today)
        assertEquals(today, draft)
        val edge = DatePickerDraftPolicy.select(today, today.plusDays(6), today)
        assertEquals(today.plusDays(6), edge)
    }

    @Test
    fun `headline segments reproduce the settled moment label`() {
        val now = LocalDateTime.of(2026, 11, 5, 6, 30)
        val tomorrow = LocalDateTime.of(2026, 11, 6, 19, 0)
        assertEquals(
            HeroSheetFormat.momentLabel(tomorrow, now),
            HeroSheetFormat.dateSegmentText(tomorrow, now) + ", " +
                HeroSheetFormat.timeSegmentText(tomorrow)
        )
        assertEquals("Tomorrow", HeroSheetFormat.dateSegmentText(tomorrow, now))
        assertEquals("7:00 PM", HeroSheetFormat.timeSegmentText(tomorrow))
        val later = LocalDateTime.of(2026, 11, 9, 7, 0)
        assertEquals("Mon, Nov 9", HeroSheetFormat.dateSegmentText(later, now))
        assertEquals(
            HeroSheetFormat.momentLabel(later, now),
            HeroSheetFormat.dateSegmentText(later, now) + ", " +
                HeroSheetFormat.timeSegmentText(later)
        )
    }

    @Test
    fun `headline renders a date segment wired to onDateClick - the entry exists`() {
        val source = File(
            "src/main/java/com/runwear/app/ui/components/HeroSheet.kt"
        ).takeIf { it.exists() } ?: File(
            "app/src/main/java/com/runwear/app/ui/components/HeroSheet.kt"
        )
        val block = source.readText().substringAfter("fun HeroSheetBlock")
        assertTrue(block.contains("onClick = { onDateClick() }"))
        assertTrue(block.contains("dateSegmentText"))
    }

    @Test
    fun `time sheet context line carries the day door`() {
        val source = File(
            "src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt"
        ).takeIf { it.exists() } ?: File(
            "app/src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt"
        )
        val text = source.readText()
        assertTrue(text.contains("onDayClick"))
        assertTrue(text.contains("onClick = onDayClick"))
    }

    @Test
    fun `date sheet is confirm-gated - taps move a draft, confirm commits`() {
        val source = File(
            "src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        ).takeIf { it.exists() } ?: File(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        )
        val sheet = source.readText()
            .substringAfter("fun DatePickerSheet")
            .substringBefore("TIME PICKER SHEET")
        assertTrue(sheet.contains("draftDate"))
        assertTrue(sheet.contains("DatePickerDraftPolicy.confirm(draftDate)"))
        assertFalse(
            "a day tap must not commit directly",
            sheet.contains("onSelectDate(date)")
        )
    }
}
