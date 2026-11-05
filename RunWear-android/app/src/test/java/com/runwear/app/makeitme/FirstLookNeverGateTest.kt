package com.runwear.app.makeitme

import com.runwear.app.ui.components.HeroPillKind
import com.runwear.app.ui.components.HeroPillPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 FIRST-LOOK RULINGS (Sol, Nov 4):
 *
 * 1. THE GENERATING SCREEN IS NEVER A GATE — while the first look
 *    generates (the moment's MAKING state), a visible way forward
 *    exists at ALL times: the primary "See My Outfit" door is
 *    rendered unconditionally (never state-gated), and Back takes
 *    the same door. Dismissing only un-renders the moment (the
 *    host's onSeeOutfit flips a visibility flag — it cancels
 *    nothing): generation is a server job whose status keeps
 *    mirroring into home's uiState, where the hero's generating
 *    pill carries the state over the fallback photo until the
 *    look lands. Waiting is the user's choice, never a
 *    requirement.
 * 2. The FINISHED surface's second-door CTA reads
 *    "Upgrade to Premium" (was "See Premium") — this surface only.
 */
class FirstLookNeverGateTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "RunWear-android/", "app/")) {
                val file = File(dir, prefix + androidRootRelative)
                if (file.exists()) return file
            }
            dir = dir.parentFile
        }
        error("source file not found: $androidRootRelative")
    }

    private fun screens() = sourceFile(
        "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
    ).readText()

    private fun mainActivity() = sourceFile(
        "app/src/main/java/com/runwear/app/MainActivity.kt"
    ).readText()

    private fun momentRegion(): String {
        val src = screens()
        return src.substringAfter("fun FirstLookMoment(")
            .substringBefore("\n// ===========================================================================\n// Delete-my-data")
    }

    @Test
    fun `MAKING is the state while the look generates - and the primary door is not state-gated`() {
        assertEquals(
            FirstLookState.MAKING,
            FirstLookPresentation.resolve(PersonalLookStatus.Making, false)
        )
        assertEquals(
            FirstLookState.MAKING,
            FirstLookPresentation.resolve(PersonalLookStatus.Ready, false)
        )

        val moment = momentRegion()
        // The primary door: a real Button on onSeeOutfit carrying
        // FIRST_LOOK_PRIMARY, present in the composition as-is.
        assertTrue(moment.contains("onClick = onSeeOutfit"))
        assertTrue(moment.contains("MakeItMeStrings.FIRST_LOOK_PRIMARY"))
        // It is NOT inside the Premium-band gate (that gate wraps
        // only the second door): the gate's region holds
        // FIRST_LOOK_SECOND_DOOR and never FIRST_LOOK_PRIMARY.
        val gateRegion = moment.substringAfter("if (FirstLookPresentation.showsPremiumBand(state))")
        assertTrue(gateRegion.contains("MakeItMeStrings.FIRST_LOOK_SECOND_DOOR"))
        assertFalse(gateRegion.contains("MakeItMeStrings.FIRST_LOOK_PRIMARY"))
        // Nor is it inside the band's settle animation (the button
        // never waits for the reveal): the Button sits after the
        // AnimatedVisibility block, at column level.
        val buttonAt = moment.indexOf("MakeItMeStrings.FIRST_LOOK_PRIMARY")
        val bandAt = moment.indexOf("AnimatedVisibility(")
        assertTrue(bandAt in 0 until buttonAt)
        // Back takes the same door — never a trap.
        assertTrue(moment.contains("BackHandler { onSeeOutfit() }"))
    }

    @Test
    fun `exiting during MAKING cancels nothing - home's pill carries the in-flight state`() {
        val host = mainActivity()
        val momentHost = host.substringAfter("if (showFirstLookMoment)")
            .substringBefore("if (showFirstLookPaywall)")
        // Dismissal is visibility only.
        assertTrue(momentHost.contains("onSeeOutfit = { showFirstLookMoment = false }"))
        assertFalse(momentHost.contains("cancel", ignoreCase = true))
        // The moment resolves from the SAME server-mirrored status
        // home renders: uiState.personalLookStatus.
        assertTrue(momentHost.contains("lookStatus = uiState.personalLookStatus"))

        // On home, that same Making status resolves to the
        // generating hero pill (over the fallback photo) until the
        // look lands — the state the user exited into.
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 0
        )
        assertEquals(HeroPillKind.MAKING, pill?.kind)
        assertTrue(pill?.showSpinner == true)
    }

    @Test
    fun `the finished surface's CTA reads Upgrade to Premium - this surface only`() {
        assertEquals("Upgrade to Premium", MakeItMeStrings.FIRST_LOOK_SECOND_DOOR)
        // The string's only consumer is the first-look moment's
        // second door, and it shows only in the READY state
        // (showsPremiumBand) — the finished surface.
        val moment = momentRegion()
        // Exactly one consumer in the screens file: this door.
        assertEquals(1, screens().split("FIRST_LOOK_SECOND_DOOR").size - 1)
        assertTrue(FirstLookPresentation.showsPremiumBand(FirstLookState.READY))
        assertFalse(FirstLookPresentation.showsPremiumBand(FirstLookState.MAKING))
        assertFalse(FirstLookPresentation.showsPremiumBand(FirstLookState.FAILED))
        assertTrue(moment.contains("MakeItMeStrings.FIRST_LOOK_SECOND_DOOR"))
    }
}
