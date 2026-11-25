package com.runwear.app.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-5 (RW-28 item 27, design §1): the settled name-led welcome.
 * Each point's HEADLINE is "Name — headline phrase" rendered in
 * pine bold on its own line; the body line beneath is separate.
 * The body phrases are Sol's Nov 4 verbatim lines; the headline
 * phrases and the footer are the design's settled text (the gen-4
 * build ran the name straight into the body sentence and carried
 * a different footer — census A-1).
 */
class WelcomeNameLedTest {

    @Test
    fun `the three points carry the settled names, headlines and phrases verbatim, in order`() {
        val points = OnboardingCopy.WELCOME_POINTS
        assertEquals(3, points.size)

        assertEquals("RunWear", points[0].name)
        assertEquals("The right outfit for the weather", points[0].headline)
        assertEquals("RunWear — The right outfit for the weather", points[0].headlineLine)
        assertEquals(
            "One recommendation for the run you're about to take — feels-like, wind, and rain included.",
            points[0].phrase
        )

        assertEquals("Make It Me", points[1].name)
        assertEquals("See yourself in every outfit", points[1].headline)
        assertEquals("Make It Me — See yourself in every outfit", points[1].headlineLine)
        assertEquals(
            "Picture yourself at the start line with the perfect outfit ready to go.",
            points[1].phrase
        )

        assertEquals("Wear I've Run", points[2].name)
        assertEquals("Remember every run", points[2].headline)
        assertEquals("Wear I've Run — Remember every run", points[2].headlineLine)
        assertEquals(
            "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.",
            points[2].phrase
        )
    }

    @Test
    fun `no body phrase repeats its feature name`() {
        for (point in OnboardingCopy.WELCOME_POINTS) {
            assertFalse(
                "phrase repeats its name: ${point.name}",
                point.phrase.contains(point.name)
            )
        }
    }

    @Test
    fun `the footer is the settled line`() {
        assertEquals(
            "Free to start · Set up in under a minute",
            OnboardingCopy.PROMISE_FOOTER
        )
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
    fun `the promise row renders the headline line in pine bold with the body separate beneath`() {
        val screen = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/OnboardingScreen.kt"
        ).readText()
        val row = screen.substringAfter("private fun PromiseRow")
            .substringBefore("\n/**")
        assertTrue("headline must be pine", row.contains("RunWearColors.PineAccent"))
        assertTrue("headline must be bold", row.contains("FontWeight.Bold"))
        assertTrue("headline line leads", row.contains("point.headlineLine"))
        assertTrue("body line follows separately", row.contains("point.phrase"))
        // The settled screen structure is otherwise untouched: the
        // script logo and the headline still head the step.
        assertTrue(screen.contains("OnboardingCopy.HEADLINE"))
        assertTrue(screen.contains("GreatVibesFontFamily"))
    }
}
