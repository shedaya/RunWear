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
 * - RW-135 (Nov 7, 2026) re-pinned three host pins below: the
 *   ruled floating dock reverses D-06, the dim seam is retired,
 *   and expansion is the glide (scroll drivers gone).
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
    fun `rw135 - the mini dock is a bottom overlay and the expanded form is the footer`() {
        // RW-135 (Direction A, owner-ruled Nov 7, 2026) REVERSES
        // the D-06 in-flow dock: the MINI bar is a floating overlay
        // above the bottom navigation, and the expanded form is
        // the footer section inside the scroll content.
        val host = heroMainScreen()
        val pullAt = host.indexOf("PullToRefreshBox(")
        val dockAt = host.indexOf("WearIveRunDock(", pullAt)
        assertTrue("PullToRefreshBox must exist", pullAt >= 0)
        assertTrue("the dock is composed after the scroll area", dockAt > pullAt)
        assertTrue(
            "the dock is bottom-aligned in the content Box (the ruled overlay)",
            // Re-pointed (RW-135 footer alignment, Nov 7, 2026):
            // the interim binary dockVisible is superseded by the
            // approved spec section 3 — the overlay is driven
            // continuously by the position-derived fade.
            // Re-pointed (RW-140, Nov 8, 2026): the fade derivation
            // moved into WearIveRunDockPolicy.fadeFromLayoutPx —
            // the single code path the host derives the fade
            // through (the literal it wraps is fade() itself).
            host.contains("WearIveRunDockPolicy.fadeFromLayoutPx(") &&
                host.contains(".align(Alignment.BottomCenter)")
        )
        assertTrue(
            "the dock takes the ruled 12dp inset seam",
            host.contains("WearIveRunDockPolicy.DOCK_INSET_DP")
        )
        assertFalse(
            "the in-flow MiniBar is retired as the Home presence",
            host.contains("WearIveRunMiniBar(")
        )
        assertTrue(
            "the expanded form is the footer section in the flow",
            host.contains("WearIveRunFooterSection(") &&
                host.contains("\"wir-footer\"")
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
    fun `rw135 - delete dispatches TRASH, expansion is the glide, the doorway opens the collection`() {
        val host = heroMainScreen()
        // Delete: one tap through the shared trash wiring.
        assertTrue(host.contains("WearIveRunElementEvent.TRASH"))
        // Expansion is the glide to the footer — the host no
        // longer dispatches tap/scroll machine events (the
        // end-zone scroll drivers are retired with the in-flow
        // dock; section 7.5's no-morph-with-scroll-handoff rule
        // is structural now).
        assertTrue(host.contains("wirGlideToFooter"))
        assertFalse(host.contains("SCROLL_ARRIVED_AT_END"))
        assertFalse(host.contains("SCROLL_DEPARTED_END"))
        // The collection opens ONLY through the footer's doorway.
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
    fun `rw135 - the dim seam is retired - sheets overlay the dock`() {
        // RW-135: the dock carries no dim seam; the sheets are
        // dialogs hosted after the content and scrim over the
        // whole screen, the dock included.
        val host = heroMainScreen()
        assertFalse(
            "no sheet-boolean dim seam feeds the presence",
            host.contains("dimmed = wearAnySheetUp")
        )
        assertFalse(
            "the seam itself is gone from the host",
            host.contains("val wearAnySheetUp")
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
        // WP-R2 re-pin: the description is the wir_remove
        // resource, resolved through its accessor at the site.
        assertEquals(
            1,
            bar.split("AppStrings.removeWir()").size - 1
        )
        // No broken-image placeholder icon in the thumbnail slot; the
        // photo paints on decode success only.
        assertFalse(bar.contains("Icons.Outlined.Image"))
        assertTrue(bar.contains("AsyncImagePainter.State.Success"))
    }
}
