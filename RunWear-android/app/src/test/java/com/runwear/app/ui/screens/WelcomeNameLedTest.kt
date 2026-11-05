package com.runwear.app.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 ROUND 2 (Sol, Nov 4): the name-led welcome — Sol CHOSE the
 * name-led proposal, superseding the A-SETTINGS title+body welcome
 * and the earlier "no name-led variant" instruction. Each point's
 * headline leads with its feature name in PINE BOLD, followed by
 * the plain phrase in regular weight; no phrase repeats its name.
 * These pins hold the composition and the exact strings.
 */
class WelcomeNameLedTest {

    @Test
    fun `the three points carry the settled names and phrases verbatim, in order`() {
        val points = OnboardingCopy.WELCOME_POINTS
        assertEquals(3, points.size)

        assertEquals("RunWear", points[0].name)
        assertEquals(
            "One recommendation for the run you're about to take — feels-like, wind, and rain included.",
            points[0].phrase
        )

        assertEquals("Make It Me", points[1].name)
        assertEquals("Put yourself in the photos.", points[1].phrase)

        assertEquals("Wear I've Run", points[2].name)
        assertEquals(
            "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.",
            points[2].phrase
        )
    }

    @Test
    fun `no phrase repeats its feature name`() {
        for (point in OnboardingCopy.WELCOME_POINTS) {
            assertFalse(
                "phrase repeats its name: ${point.name}",
                point.phrase.contains(point.name)
            )
        }
    }

    @Test
    fun `the superseded WS-E composition is gone`() {
        // "Remember every run" was the WS-E third-point headline;
        // the name-led composition replaces it entirely.
        val phrasesAndNames = OnboardingCopy.WELCOME_POINTS
            .flatMap { listOf(it.name, it.phrase) }
        assertFalse(phrasesAndNames.any { it.contains("Remember every run") })
        assertFalse(phrasesAndNames.any { it.contains("See yourself in every outfit") })
        assertFalse(phrasesAndNames.any { it.contains("The right outfit for the weather") })
    }

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

    @Test
    fun `the promise row renders the name in pine bold leading the phrase`() {
        val screen = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/OnboardingScreen.kt"
        ).readText()
        val row = screen.substringAfter("private fun PromiseRow")
            .substringBefore("\n/**")
        assertTrue("name span must be pine", row.contains("RunWearColors.PineAccent"))
        assertTrue("name span must be bold", row.contains("FontWeight.Bold"))
        assertTrue("name leads", row.contains("append(point.name)"))
        assertTrue("phrase follows", row.contains("append(point.phrase)"))
        // The settled screen structure is otherwise untouched: the
        // script logo and the headline still head the step.
        assertTrue(screen.contains("OnboardingCopy.HEADLINE"))
        assertTrue(screen.contains("GreatVibesFontFamily"))
    }
}
