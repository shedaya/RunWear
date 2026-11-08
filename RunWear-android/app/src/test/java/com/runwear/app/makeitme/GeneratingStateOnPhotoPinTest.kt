package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * GEN-3 (Y-8 parity, iOS census 2026-11-04): the generating state
 * sits on the ACTUAL standard photo for the cell — the WS-A(1)
 * bundled fallback — not on a blur or a gradient, wherever the
 * generating state renders. On iOS the first-look generating state
 * sat on a featureless grey blur for ~11 s while its own copy
 * claimed a standard photo "stays up"; no photo was ever visible.
 *
 * The Android mechanism, pinned at its seams:
 *  1. the home hero's pill (the generating state) renders whenever
 *     its content exists — it is NOT gated on the photo layers, so
 *     it appears over the fallback photo the layer policy bases the
 *     scene on, from the scene's first frame;
 *  2. the first-look moment's photo area paints the serving photo
 *     in EVERY presentation state (the AsyncImage is not gated on
 *     the state), and the MAKING state overlays the same hero pill
 *     on that photo;
 *  3. both surfaces are fed by MainUiState.effectiveHeroUrl /
 *     heroFallbackUrl — the fallback is in the photo path, not a
 *     placeholder beside it (the fork/first-look feed itself is
 *     pinned in ForkBandNonEmptyTest).
 */
class GeneratingStateOnPhotoPinTest {

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

    @Test
    fun `the home hero pill is not gated on the photo layers`() {
        val hero = sourceFile(
            "app/src/main/java/com/runwear/app/ui/components/HeroSection.kt"
        ).readText()
        // RW-144 (Nov 8, 2026): the pill's seat moved OUT of
        // HeroSection to the hero/sheet boundary in HeroMainScreen's
        // stacked unit (the v8 frames' straddle). The settled rule
        // this pin carries is unchanged: the pill renders from the
        // pill policy alone, never gated on the photo layers, so
        // the generating state shows over whatever photo is based.
        val host = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        ).readText()
        val unitStart = host.indexOf("private fun HeroStackedUnit")
        assertTrue("the stacked unit exists", unitStart >= 0)
        val unit = host.substring(
            unitStart, host.indexOf("\n}\n", unitStart) + 3
        )
        assertTrue("the pill renders in the stacked unit", unit.contains("HeroPill("))
        assertFalse(
            "the pill must render over whatever photo is based — " +
                "gating it on the layers would hide the generating " +
                "state exactly while the fallback photo is up",
            unit.contains("heroLayers")
        )
        // And the scene base IS the fallback: the layer policy's
        // scene-change entry takes the fallback as its base input.
        assertTrue(
            hero.contains("HeroLayerPolicy.onSceneChange(heroImageUrl, heroFallbackUrl)")
        )
    }

    @Test
    fun `the first-look photo paints in every state and the making pill overlays it`() {
        val screens = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
        val momentStart = screens.indexOf("fun FirstLookMoment")
        val momentEnd = screens.indexOf("fun MakeItMeDeleteDataDialog")
        assertTrue(momentStart in 0 until momentEnd)
        val moment = screens.substring(momentStart, momentEnd)
        // The photo itself: painted from photoUrl whenever one
        // serves. GEN-6 RW-97 (Sol's Nov 6, 2026 walkthrough —
        // the sheet's photo area was blank in MAKING and DONE):
        // the old reveal gate deadlocked, because the load state
        // it waited on was set only by the gated image's own
        // callbacks, so a photo that was already resolved when
        // the sheet opened never painted. The layers now compose
        // the image UNGATED (the last loaded photo holds beneath
        // a loading one; the reveal survives only as the fade +
        // the success sequence's trigger, never as a composition
        // gate). This pin follows the fix.
        assertTrue(moment.contains("FirstLookPhotoLayers("))
        val layersStart = screens.indexOf("private fun FirstLookPhotoLayers")
        assertTrue(layersStart >= 0)
        val layers = screens.substring(
            layersStart,
            screens.indexOf("\n}\n", layersStart) + 3
        )
        assertTrue(layers.contains("AsyncImage("))
        assertFalse(
            "the photo layers must never visibility-gate the image (RW-97)",
            layers.contains("AnimatedVisibility")
        )
        assertFalse(
            "the reveal flag must not gate composition (RW-97)",
            moment.contains("visible = revealed")
        )
        // The making overlay: the shared hero pill, on the photo.
        assertTrue(
            moment.contains("com.runwear.app.ui.components.HeroPill(")
        )
        // GEN-6 (DA-12): the making pill is resolved through the
        // ONE HeroPillPolicy (the MAKING copy now lives behind the
        // policy, not hand-built in this surface) — the pin follows
        // the seam, and the hand-built content must not return.
        assertTrue(
            moment.contains("HeroPillPolicy.resolve")
        )
        assertFalse(
            moment.contains("HeroPillContent(")
        )
    }

    @Test
    fun `every home layout receives the pill content`() {
        val home = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        ).readText()
        val passes = home.split("heroPill = heroPill").size - 1
        assertTrue(
            "COMPACT, MEDIUM and EXPANDED heroes must all carry the pill (found $passes)",
            passes >= 3
        )
    }
}
