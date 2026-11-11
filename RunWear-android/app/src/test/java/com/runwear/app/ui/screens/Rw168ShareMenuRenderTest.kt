package com.runwear.app.ui.screens

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.weariverun.WearIveRunNarrationFormatter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * RW-168 (Nov 11, 2026): the home share button beside the gear
 * and its menu of existing entries — the button composes in the
 * hero chrome, the menu opens with exactly the three doors, the
 * collection door navigates into the Wear I've Run collection,
 * and the outfit door opens the RW-158 "Share this day" sheet
 * for the current entry. Menu-open frames are written in light
 * and dark for the report.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168ShareMenuRenderTest : Rw156DockBase() {

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outDir, name)
        FileOutputStream(file).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("RW168 frame ${file.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    private fun openMenu(dark: Boolean) {
        composeRule.setContent {
            RunWearTheme(darkTheme = dark) {
                HeroMainScreen(
                    initialState = composeHome(),
                    onOpenFeedback = {}
                )
            }
        }
        composeRule.waitForIdle()
        settleFrames(10)
        composeRule.onNodeWithContentDescription("Share")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        settleFrames(6)
    }

    @Test
    fun `rw168 menu opens with the three existing doors - light`() {
        openMenu(dark = false)
        composeRule.onNodeWithText("Share this outfit").assertIsDisplayed()
        composeRule.onNodeWithText("Share from collection").assertIsDisplayed()
        composeRule.onNodeWithText("Save to photos").assertIsDisplayed()
        shoot("rw168-menu-light.png")
    }

    @Test
    fun `rw168 menu opens with the three existing doors - dark`() {
        openMenu(dark = true)
        composeRule.onNodeWithText("Share this outfit").assertIsDisplayed()
        composeRule.onNodeWithText("Share from collection").assertIsDisplayed()
        composeRule.onNodeWithText("Save to photos").assertIsDisplayed()
        shoot("rw168-menu-dark.png")
    }

    @Test
    fun `rw168 collection door navigates into the collection`() {
        openMenu(dark = false)
        composeRule.onNodeWithText("Share from collection").performClick()
        composeRule.waitForIdle()
        settleFrames(8)
        composeRule.onNodeWithText(WearIveRunNarrationFormatter.WORDMARK)
            .assertIsDisplayed()
    }

    @Test
    fun `rw168 outfit door opens the share studio for the current entry`() {
        openMenu(dark = false)
        composeRule.onNodeWithText("Share this outfit").performClick()
        composeRule.waitForIdle()
        settleFrames(10)
        composeRule.onNodeWithText("Share this day").assertIsDisplayed()
        composeRule.onNodeWithText("Postcard").assertIsDisplayed()
        composeRule.onNodeWithText("Kit Card").assertIsDisplayed()
    }
}

/**
 * RW-168: the direct save's render pipeline — a verified card
 * renders headlessly for both kinds from the sheet's own
 * primitives, and the Postcard's hero hold applies (a referenced
 * hero that cannot load yields no card rather than a
 * photo-less rectangle). The pipeline suspends onto the main
 * dispatcher (the production renderer does), so calls run on a
 * worker thread while the test thread pumps Robolectric's main
 * scheduler — the Gen6WaveBRenderTests harness pattern.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168SaveRenderPipelineTest : Rw156DockBase() {

    private fun renderPumped(
        entry: com.runwear.app.weariverun.WearIveRunDayEntry,
        kind: com.runwear.app.weariverun.ShareArtifactKind
    ): android.graphics.Bitmap? {
        var result: android.graphics.Bitmap? = null
        var failure: Throwable? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = Thread {
            try {
                result = runBlocking {
                    com.runwear.app.weariverun.renderVerifiedShareCard(
                        context = composeRule.activity,
                        entry = entry,
                        kind = kind,
                        format = com.runwear.app.weariverun
                            .ShareExportFormat.FEED,
                        gender = com.runwear.shared.domain.model
                            .GenderPreference.UNISEX,
                        temperatureUnit = com.runwear.shared.domain.model
                            .TemperatureUnit.FAHRENHEIT
                    )
                }
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 90_000
        while (!latch.await(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            org.robolectric.Robolectric.flushForegroundThreadScheduler()
            org.robolectric.Shadows.shadowOf(
                android.os.Looper.getMainLooper()
            ).idle()
            if (System.currentTimeMillis() > deadline) {
                org.junit.Assert.fail("direct render timed out ($kind)")
            }
        }
        failure?.let {
            throw AssertionError("direct render failed ($kind)", it)
        }
        return result
    }

    @Test
    fun `rw168 direct render produces verified cards for both kinds`() {
        composeRule.setContent {
            RunWearTheme { HeroMainScreen(initialState = composeHome(), onOpenFeedback = {}) }
        }
        composeRule.waitForIdle()
        settleFrames(6)
        val entry = seededEntry()
        val poster = renderPumped(
            entry,
            com.runwear.app.weariverun.ShareArtifactKind.MORNING_POSTER
        )
        assertNotNull("postcard renders + verifies headlessly", poster)
        assertEquals(
            com.runwear.app.weariverun.ShareExportFormat.FEED.exportPixelSize.width,
            poster!!.width
        )
        assertEquals(
            com.runwear.app.weariverun.ShareExportFormat.FEED.exportPixelSize.height,
            poster.height
        )
        val kit = renderPumped(
            entry,
            com.runwear.app.weariverun.ShareArtifactKind.KIT_CARD
        )
        assertNotNull("kit card renders + verifies headlessly", kit)
    }

    @Test
    fun `rw168 postcard hero hold - unloadable referenced hero yields no card`() {
        composeRule.setContent {
            RunWearTheme { HeroMainScreen(initialState = composeHome(), onOpenFeedback = {}) }
        }
        composeRule.waitForIdle()
        settleFrames(6)
        val broken = seededEntry().copy(
            heroRef = "file:///nonexistent/definitely-missing-hero.png"
        )
        val poster = renderPumped(
            broken,
            com.runwear.app.weariverun.ShareArtifactKind.MORNING_POSTER
        )
        assertNull("hero hold: no card when the referenced hero cannot load", poster)
    }
}
