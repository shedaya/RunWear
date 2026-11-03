package com.runwear.app.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-1 (RC-1) — source pins for the structural guarantees the
 * rendered tests cannot see directly, following the repo's WS-2
 * precedent (HeroNumeralReadoutTest):
 *
 *  - D-01's mechanism cannot return: in HeroSheetContent AND
 *    HeroSectionContent the caller's modifier is applied to the
 *    AnimatedVisibility ROOT (the stacking Box's direct child) and
 *    is NOT forwarded to the inner block;
 *  - D-03: MainActivity passes the Scaffold inset to
 *    HeroMainScreen, which consumes it in exactly its three scroll
 *    containers' bottom content padding — one consumption pattern,
 *    no per-node re-application;
 *  - A3/D-02: the location pill lives in the photo-zone chrome,
 *    shows the SimplePlace form, and its click is wired in
 *    HeroMainScreen to open the Section E location picker;
 *  - Standing gate item (F-2): the sheets the home chrome opens
 *    (Settings, Location) are real ModalBottomSheets whose
 *    onDismissRequest clears the hosting state — system Back
 *    dismisses to home, never falls through to the activity.
 */
class HomeCompositionPinTest {

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

    private fun heroSection() =
        sourceFile("app/src/main/java/com/runwear/app/ui/components/HeroSection.kt").readText()

    /** The body of the named private composable, brace-counted.
     * The search starts at the parameter list's closing ") {" —
     * a naive first-'{' search would land inside a default value
     * like `= {}` in the parameter list itself. */
    private fun functionBody(source: String, name: String): String {
        val at = source.indexOf("fun $name(")
        assertTrue("function $name must exist", at >= 0)
        val openBrace = source.indexOf("\n) {", at) + 3
        var depth = 0
        for (i in openBrace until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBrace, i + 1)
                }
            }
        }
        fail("function $name body must close")
        throw AssertionError("unreachable")
    }

    @Test
    fun `the sheet content root carries the caller modifier - D-01 cannot return`() {
        val body = functionBody(heroMainScreen(), "HeroSheetContent")
        // The AnimatedVisibility (direct child of the stacking Box)
        // receives the modifier…
        val visibilityAt = body.indexOf("AnimatedVisibility(")
        val blockAt = body.indexOf("HeroSheetBlock(")
        assertTrue(visibilityAt in 0 until blockAt)
        val visibilityArgs = body.substring(visibilityAt, blockAt)
        assertTrue(
            "AnimatedVisibility must carry modifier = modifier",
            visibilityArgs.contains("modifier = modifier")
        )
        // …and the inner block receives a plain fill, never the
        // caller's alignment-bearing modifier.
        val blockArgs = body.substring(blockAt)
        assertTrue(
            "HeroSheetBlock must be sized with Modifier.fillMaxWidth()",
            blockArgs.contains("modifier = Modifier.fillMaxWidth()")
        )
        assertFalse(
            "the caller modifier must NOT be forwarded to HeroSheetBlock (that was D-01)",
            blockArgs.contains("modifier = modifier")
        )
    }

    @Test
    fun `the photo content root carries the caller modifier too`() {
        val body = functionBody(heroMainScreen(), "HeroSectionContent")
        val visibilityAt = body.indexOf("AnimatedVisibility(")
        val sectionAt = body.indexOf("HeroSection(")
        assertTrue(visibilityAt in 0 until sectionAt)
        assertTrue(
            "AnimatedVisibility must carry modifier = modifier",
            body.substring(visibilityAt, sectionAt).contains("modifier = modifier")
        )
        assertFalse(
            "the caller modifier must NOT be forwarded to HeroSection",
            body.substring(sectionAt).contains("modifier = modifier")
        )
    }

    @Test
    fun `the stacked unit anchors the sheet to the bottom and the photo to the top`() {
        val body = functionBody(heroMainScreen(), "HeroStackedUnit")
        assertTrue(body.contains(".align(Alignment.BottomStart)"))
        assertTrue(body.contains(".align(Alignment.TopStart)"))
        assertTrue(
            "the reservation comes from HeroStackedGeometry (hero + sheet - 28dp)",
            body.contains("HeroStackedGeometry.unitHeight")
        )
    }

    @Test
    fun `scaffold inset is passed by MainActivity and consumed once per scroll container`() {
        val activity = sourceFile("app/src/main/java/com/runwear/app/MainActivity.kt").readText()
        assertTrue(
            "MainActivity must pass the Scaffold inset to the main screen (D-03)",
            activity.contains("scaffoldContentPadding = innerPadding")
        )
        val host = heroMainScreen()
        // Exactly the three scroll containers (COMPACT list, MEDIUM
        // grid, EXPANDED grid) consume the bottom inset, each once.
        assertEquals(
            3,
            host.split("scaffoldContentPadding.calculateBottomPadding()").size - 1
        )
        // The root never pads itself with the inset (the photo zone
        // is full-bleed behind the status bar by design).
        assertFalse(host.contains(".padding(scaffoldContentPadding)"))
    }

    @Test
    fun `the location pill is in the photo chrome, simple-formed, and wired to the picker`() {
        val section = heroSection()
        // The pill exists in HeroSection's top-controls row…
        assertTrue(section.contains("GlassButton("))
        // …shows the SimplePlace form, normalized at the boundary…
        assertTrue(
            section.contains("SimplePlace") &&
                section.contains("fromRawDisplayString(locationName).display")
        )
        // …and HeroMainScreen wires its click to the Section E picker.
        val host = heroMainScreen()
        assertTrue(host.contains("onLocationClick = { showLocationPicker = true }"))
        assertTrue(host.contains("LocationPickerSheet("))
        // The hero content passes the state's simple place through.
        assertTrue(host.contains("uiState.simplePlace?.display ?: uiState.locationName"))
    }

    @Test
    fun `chrome fade disables the photo-zone controls at the end of the fade`() {
        val section = heroSection()
        assertTrue(section.contains("HeroScrollHandoff.chromeAlpha(handoff)"))
        assertTrue(section.contains("HeroScrollHandoff.chromeInteractive(handoff)"))
        assertTrue(section.contains("enabled = chromeInteractive"))
    }

    @Test
    fun `back from the sheets the chrome opens returns home - F-2 standing item`() {
        val host = heroMainScreen()
        // Settings: hosted by state, dismissed by clearing it — the
        // M3 ModalBottomSheet's onDismissRequest (system Back) lands
        // exactly there.
        assertTrue(host.contains("onDismiss = { showSettings = false }"))
        assertTrue(host.contains("onDismiss = { showLocationPicker = false }"))
        val sheets = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        ).readText()
        assertTrue(
            "SettingsSheet must be a ModalBottomSheet with onDismissRequest",
            sheets.contains("ModalBottomSheet(") && sheets.contains("onDismissRequest = onDismiss")
        )
    }

    @Test
    fun `the hero image paints only after decode - no placeholder or error painter`() {
        val section = heroSection()
        assertTrue(section.contains("AsyncImagePainter.State.Success"))
        assertTrue(section.contains("HERO_CROSSFADE_IN_MS"))
        assertFalse(
            "no error/placeholder painter may ever show a broken-image glyph (D-12)",
            section.contains("error =") && section.contains("Painter")
        )
        assertFalse(
            "the arbitrary 1.1x zoom that cropped the served frame is gone (D-23)",
            section.contains(".scale(1.1f)")
        )
    }
}
