package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * GEN-3 A-HERO (WS-B): the onboarding fork's photo band must never
 * render empty. Two halves, both pinned at the seams that decide
 * them:
 *
 *  1. FEED — the fork is fed by the scene's ONE photo resolution
 *     (MainUiState.effectiveHeroUrl: the resolved hero, or the
 *     scene's bundled fallback), not by the async hero alone — a
 *     first frame whose pool resolution has not landed yet still
 *     carries the fallback photo into the band.
 *  2. RENDER — when no truthful photo is servable at all (the
 *     census's defect signatures), the band renders the designed
 *     placeholder (gradient + glyph), never the bare flat box that
 *     read as a broken photo in the gen-2 stills.
 */
class ForkBandNonEmptyTest {

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
    fun `the fork is fed by the effective photo resolution`() {
        val mainActivity = sourceFile(
            "app/src/main/java/com/runwear/app/MainActivity.kt"
        ).readText()
        val forkCall = mainActivity.indexOf("MakeItMeForkScreen(")
        assertTrue("fork call site exists", forkCall >= 0)
        val forkArgs = mainActivity.substring(forkCall, forkCall + 600)
        assertTrue(
            "the fork's photo must be uiState.effectiveHeroUrl " +
                "(resolved hero or the scene's bundled fallback)",
            forkArgs.contains("heroImageUrl = uiState.effectiveHeroUrl")
        )
    }

    @Test
    fun `the fork band renders the bundled hard-coded image - it can never be blank`() {
        // GEN-5 (C-2): the band no longer renders the (possibly
        // unservable) hero photo at all — it renders the bundled
        // purpose-designed asset, synchronously, over the designed
        // cream ground. The gen-3 null-photo placeholder branch is
        // retired with the serve it guarded (A-5).
        val screens = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
        val forkStart = screens.indexOf("fun MakeItMeForkScreen")
        val forkEnd = screens.indexOf("private fun ForkOptionCard")
        assertTrue(forkStart in 0 until forkEnd)
        val forkBody = screens.substring(forkStart, forkEnd)
        assertTrue(
            "the band must paint the bundled fork asset",
            forkBody.contains("R.drawable.mim_fork_hero")
        )
        assertTrue(
            "the band's ground must be the designed cream, never white",
            forkBody.contains(".background(RunWearColors.Background)")
        )
        assertTrue(
            "no async photo load may gate the fork band",
            !forkBody.contains("AsyncImage")
        )
    }

    @Test
    fun `the first-look moment is fed by the same resolution and resolved state`() {
        val mainActivity = sourceFile(
            "app/src/main/java/com/runwear/app/MainActivity.kt"
        ).readText()
        val momentCall = mainActivity.indexOf("FirstLookMoment(")
        assertTrue("first-look call site exists", momentCall >= 0)
        val momentArgs = mainActivity.substring(momentCall, momentCall + 1600)
        assertTrue(
            "the moment's photo must be uiState.effectiveHeroUrl",
            momentArgs.contains("photoUrl = uiState.effectiveHeroUrl")
        )
        assertTrue(
            "the moment's claims must resolve through FirstLookPresentation",
            momentArgs.contains("FirstLookPresentation.resolve(")
        )
        assertTrue(
            "personal-displayed must be judged against the photo on screen",
            momentArgs.contains(
                "uiState.heroImageUrl == uiState.personalHeroImageUrl"
            )
        )
    }
}
