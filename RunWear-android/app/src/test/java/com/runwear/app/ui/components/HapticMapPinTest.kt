package com.runwear.app.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-9 fix (P-2) — the §1.7 haptic map, pinned.
 *
 * §1.7's table is the COMPLETE list of haptic events in the app;
 * everything else is silent. The pins:
 *
 *  - the map's values: every tick class is the generic tick Compose
 *    exposes, and the ONE long-press class is LongPress
 *    ([HapticSpec]);
 *  - the call-site census: every `performHapticFeedback(` call in
 *    the app module's production sources names a [HapticSpec]
 *    constant (no raw effect anywhere), the GlassButton chrome
 *    (location pill / Settings) contains NO haptic call at all —
 *    the pre-fix LongPress-on-every-tap is gone — and the map's
 *    LongPress constant has exactly ONE call site (the recents
 *    contextual bar's long-press in the location sheet);
 *  - the specified selection surfaces carry their tick: the
 *    location sheet (place selected, recents long-press + removal),
 *    the day and hour sheets, the Settings preference selectors,
 *    onboarding's preference selection, the element (cue + trash),
 *    and the guided capture's check-turning-passing.
 */
class HapticMapPinTest {

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val relative = "app/src/main/java/com/runwear/app"
        val candidates = listOf(relative, "RunWear-android/$relative", "../$relative")
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isDirectory) return file
            }
            dir = dir.parentFile
        }
        fail("app source root not found (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun productionSources(): List<File> =
        sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun source(relativeToAppRoot: String): String =
        File(sourceRoot(), relativeToAppRoot).readText()

    @Test
    fun `the map values are the spec classes`() {
        assertEquals(HapticFeedbackType.TextHandleMove, HapticSpec.CUE_TICK)
        assertEquals(HapticFeedbackType.TextHandleMove, HapticSpec.LIGHT_TICK)
        assertEquals(HapticFeedbackType.TextHandleMove, HapticSpec.SELECTION)
        assertEquals(HapticFeedbackType.TextHandleMove, HapticSpec.CONFIRM)
        assertEquals(HapticFeedbackType.LongPress, HapticSpec.LONG_PRESS)
    }

    @Test
    fun `every haptic call site in the app names a HapticSpec constant`() {
        var calls = 0
        for (file in productionSources()) {
            val text = file.readText()
            var index = text.indexOf("performHapticFeedback(")
            while (index >= 0) {
                calls++
                val window = text.substring(
                    index, minOf(text.length, index + 220)
                )
                assertTrue(
                    "${file.name}: every performHapticFeedback call must " +
                        "name a HapticSpec constant (the §1.7 map), got: " +
                        window.take(120),
                    window.contains("HapticSpec.")
                )
                index = text.indexOf("performHapticFeedback(", index + 1)
            }
        }
        assertTrue("the census must find the map's call sites", calls >= 10)
    }

    @Test
    fun `the glass chrome fires no haptic on tap`() {
        val glass = source("ui/components/GlassButton.kt")
        assertFalse(
            "§1.7 assigns the location pill / Settings taps NO haptic; " +
                "the pre-fix LongPress-on-tap must stay gone",
            glass.contains("performHapticFeedback")
        )
    }

    @Test
    fun `long press exists at exactly one call site - the recents bar`() {
        val sites = productionSources().filter { file ->
            file.readText().contains("HapticSpec.LONG_PRESS")
        }
        assertEquals(
            "§1.7's only long-press is the recents contextual bar",
            listOf("BottomSheets.kt"),
            sites.map { it.name }
        )
        val bottomSheets = source("ui/screens/BottomSheets.kt")
        assertTrue(
            "the long-press must ride the recents row's onLongClick",
            bottomSheets.contains("onLongClick = {")
        )
    }

    @Test
    fun `the specified selection surfaces carry the selection tick`() {
        val bottomSheets = source("ui/screens/BottomSheets.kt")
        // Place selected + day/hour selected + Settings preference
        // selectors all live in BottomSheets.kt.
        val selections = bottomSheets.split("HapticSpec.SELECTION").size - 1
        assertTrue(
            "BottomSheets must carry the place/day/hour/Settings " +
                "selection ticks (found $selections)",
            selections >= 8
        )
        assertTrue(
            "onboarding preference selection keeps its tick",
            source("ui/screens/OnboardingScreen.kt")
                .contains("HapticSpec.SELECTION")
        )
        assertTrue(
            "a capture check turning passing fires the tick",
            source("makeitme/GuidedSelfieCapture.kt")
                .contains("HapticSpec.SELECTION")
        )
    }

    @Test
    fun `the element cue and trash and the first look carry their map entries`() {
        val miniBar = source("weariverun/WearIveRunMiniBar.kt")
        assertTrue(miniBar.contains("HapticSpec.CUE_TICK"))
        assertTrue(
            // GEN-2: the morph's single shared trash control serves
            // both states, so one call site carries the tick for both.
            "the element's shared trash control fires the light tick",
            miniBar.split("HapticSpec.LIGHT_TICK").size - 1 >= 1
        )
        assertTrue(
            "the first-look reveal fires the confirm-class effect",
            source("makeitme/MakeItMeScreens.kt").contains("HapticSpec.CONFIRM")
        )
    }
}
