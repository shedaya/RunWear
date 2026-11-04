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
        val pillStart = hero.indexOf("if (heroPill != null)")
        assertTrue("the pill render block exists", pillStart >= 0)
        val pillBlock = hero.substring(pillStart, pillStart + 500)
        assertTrue(pillBlock.contains("HeroPill("))
        assertFalse(
            "the pill must render over whatever photo is based — " +
                "gating it on the layers would hide the generating " +
                "state exactly while the fallback photo is up",
            pillBlock.contains("heroLayers")
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
        // serves — visibility hangs on the reveal + the URL, never
        // on the presentation state (a state gate here is what put
        // the iOS generating state on a blur).
        val photoBlock = moment.substring(
            moment.indexOf("AnimatedVisibility("),
            moment.indexOf("FirstLookState.MAKING")
        )
        assertTrue(photoBlock.contains("AsyncImage("))
        assertTrue(photoBlock.contains("visible = revealed && photoUrl != null"))
        assertFalse(photoBlock.contains("FirstLookState.READY"))
        // The making overlay: the shared hero pill, on the photo.
        assertTrue(
            moment.contains("com.runwear.app.ui.components.HeroPill(")
        )
        assertTrue(
            moment.contains("MakeItMeStrings.HERO_PILL_MAKING")
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
