package com.runwear.app.weariverun

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-3 (RC-3) — source pins for the structural guarantees the
 * rendered tests cannot see directly (the repo's WS-1/WS-2 pin
 * precedent):
 *
 * - D-06: the element is NOT a root-Box overlay — no bottom-aligned,
 *   88 dp-padded dock exists in HeroMainScreen; the element is
 *   composed inside the content branch's Column, below the pull-to-
 *   refresh scroll area (the sticky bottom dock in the layout flow);
 * - D-07: exactly ONE cue timer exists (keyed on the cue sequence),
 *   the old cue effect (a haptic + setExpanded pair driven by a cue
 *   counter) is gone, expand/collapse/trash all dispatch machine
 *   events, and the mini bar's expand callback can no longer open
 *   the collection;
 * - D-11 + F-2: the dim seam consumes the host's sheet booleans and
 *   passes them to the element; the sheets themselves are untouched
 *   by this lane — their dismiss paths (system Back → the hosting
 *   state clears → home) are exactly the base's;
 * - §1.7: the element surface contains exactly one haptic call —
 *   the cue pulse's soft tick — and HeroMainScreen contains none
 *   for this element.
 */
class WearIveRunElementPinTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun heroMainScreen() =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt").readText()

    private fun miniBar() =
        sourceFile("app/src/main/java/com/runwear/app/weariverun/WearIveRunMiniBar.kt").readText()

    @Test
    fun `the element is a dock in the layout flow - not a root overlay - D-06`() {
        val host = heroMainScreen()
        // The old floating dock's fingerprints are gone.
        assertFalse(
            "no 88dp bottom-padded overlay dock (that was D-06)",
            host.contains(".padding(bottom = 88.dp)")
        )
        // The dock is composed after the PullToRefreshBox, inside the
        // content branch — layout order is the pin: find both and
        // require dock-after-scroll-area.
        val pullAt = host.indexOf("PullToRefreshBox(")
        val dockAt = host.indexOf("WearIveRunMiniBar(", pullAt)
        assertTrue("PullToRefreshBox must exist", pullAt >= 0)
        assertTrue("the dock must be composed after the scroll area", dockAt > pullAt)
        val dockCall = host.substring(dockAt, host.indexOf("navigationBarsPadding", dockAt))
        assertTrue(
            "the dock fills the flow width (a citizen, not an overlay)",
            dockCall.contains(".fillMaxWidth()")
        )
        assertFalse(
            "the dock must not be aligned to the root Box's bottom",
            dockCall.contains(".align(Alignment.BottomCenter)")
        )
    }

    @Test
    fun `one cue timer, keyed on the cue sequence - the old cue effect is gone`() {
        val host = heroMainScreen()
        assertFalse(
            "the old holder.setExpanded driver API is retired everywhere in the host",
            host.contains("setExpanded")
        )
        assertFalse(
            "the old cue counter flow is retired",
            host.contains("cueCount")
        )
        // Exactly one delay drives the cue timeout, inside the
        // (isCue, cueSequence)-keyed effect.
        assertEquals(
            1,
            host.split("WearIveRunElementMachine.CUE_TIMEOUT_MS").size - 1
        )
        assertTrue(host.contains("LaunchedEffect(wearIsCue, wearCueSequence)"))
        assertTrue(
            host.contains("wearIveRunHolder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)")
        )
    }

    @Test
    fun `every control dispatches a machine event - expand no longer opens the collection`() {
        val host = heroMainScreen()
        assertTrue(host.contains("WearIveRunElementEvent.TAP_BODY"))
        assertTrue(host.contains("WearIveRunElementEvent.COLLAPSE_CONTROL"))
        assertTrue(host.contains("WearIveRunElementEvent.TRASH"))
        assertTrue(host.contains("WearIveRunElementEvent.SCROLL_ARRIVED_AT_END"))
        assertTrue(host.contains("WearIveRunElementEvent.SCROLL_DEPARTED_END"))
        // The collection opens ONLY through the expanded card's
        // doorway callback.
        assertTrue(host.contains("onOpenCollection = { showWearIveRunCollection = true }"))
        // Day rollover is dispatched as its own driver.
        assertTrue(host.contains("wearIveRunHolder.onDayRollover(wearToday)"))
    }

    @Test
    fun `the recording effect records content - it is not a state driver`() {
        val host = heroMainScreen()
        // The recording still exists (entries must be recorded), and
        // the holder's seam — not the effect — decides what cues.
        assertTrue(host.contains("wearIveRunStore.recordSettled("))
        assertTrue(host.contains("wearIveRunHolder.onEntriesChanged("))
    }

    @Test
    fun `the element dims with the backdrop whenever any sheet is up - D-11`() {
        val host = heroMainScreen()
        assertTrue(
            "the dim seam is computed from the host's sheet booleans",
            host.contains("val wearAnySheetUp = showDatePicker || showTimePicker || showSettings")
        )
        assertTrue(host.contains("dimmed = wearAnySheetUp"))
        val bar = miniBar()
        assertTrue(
            "the element applies the backdrop alpha as a whole",
            bar.contains("WearIveRunMiniBarPolicy.surfaceAlpha(dimmed)")
        )
    }

    @Test
    fun `back from the sheets returns home - F-2 standing item, sheets untouched by this lane`() {
        val host = heroMainScreen()
        // The dim seam only READS the sheet booleans; every dismiss
        // path is the base's: the M3 sheets' onDismissRequest (system
        // Back) clears the hosting state and returns to home.
        assertTrue(host.contains("onDismiss = { showSettings = false }"))
        assertTrue(host.contains("onDismiss = { showLocationPicker = false }"))
        assertTrue(host.contains("onDismiss = { showDatePicker = false }"))
        // RW-131 re-pin (Nov 7, 2026): the time sheet's dismiss
        // also clears the picker session's working day — the
        // return-home behaviour this pin protects is unchanged.
        assertTrue(host.contains("pickerWorkingDate = null"))
        assertTrue(host.contains("showTimePicker = false"))
        // This lane adds no BackHandler and no back interception of
        // its own anywhere in the element.
        assertFalse(miniBar().contains("BackHandler"))
        // The element's trash path never touches sheet state.
        val trashAt = host.indexOf("WearIveRunElementEvent.TRASH")
        val trashBlock = host.substring(trashAt, host.indexOf("onOpenCollection", trashAt))
        assertFalse(trashBlock.contains("showSettings"))
        assertFalse(trashBlock.contains("showLocationPicker"))
    }

    @Test
    fun `the element surface's haptics are exactly the S1_7 map entries - cue tick and trash tick`() {
        // WS-9 fix (P-2): §1.7's table names TWO haptics for this
        // surface — the cue's light confirm-class tick and the
        // trash's light tick. GEN-2's morph unifies the two state
        // renderings into ONE composable with ONE shared control set,
        // so each map entry now has exactly one call site (cue tick
        // at the settle + the shared trash button's light tick) —
        // the map itself is unchanged.
        val bar = miniBar()
        assertEquals(
            2,
            bar.split("performHapticFeedback").size - 1
        )
        assertTrue(
            "the cue haptic is the map's cue tick, not the old LongPress",
            bar.contains("HapticSpec.CUE_TICK")
        )
        assertTrue(
            "the trash buttons fire the map's light tick",
            bar.contains("HapticSpec.LIGHT_TICK")
        )
        assertFalse(bar.contains("HapticSpec.LONG_PRESS"))
        // The host fires no haptic for this element at all anymore.
        assertFalse(heroMainScreen().contains("performHapticFeedback"))
        // The pulse is a single non-looping pair of 450 ms halves and
        // is keyed only on the cue sequence (fires exactly once per
        // landing).
        assertTrue(bar.contains("LaunchedEffect(cueSequence)"))
        assertFalse(bar.contains("repeat("))
        assertFalse(bar.contains("infiniteRepeatable"))
    }

    @Test
    fun `the expanded state has no Remove text button and no placeholder glyph - D-10 D-12`() {
        val bar = miniBar()
        assertFalse(bar.contains("TextButton"))
        assertFalse(bar.contains("text = \"Remove\""))
        // GEN-2: the morph's ONE shared trash control serves both
        // states (identical by construction), so its content
        // description appears exactly once in the unified surface.
        assertEquals(
            1,
            bar.split("\"Remove from Wear I've Run\"").size - 1
        )
        // No broken-image placeholder icon in the thumbnail slot; the
        // photo paints on decode success only.
        assertFalse(bar.contains("Icons.Outlined.Image"))
        assertTrue(bar.contains("AsyncImagePainter.State.Success"))
    }
}
