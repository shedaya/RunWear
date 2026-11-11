package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** RW-158 scenarios whose assertions need no post-write
 * recomposition (plain-lambda recordings, source pins) — they
 * share one class; see Rw158Harness for the split's reason. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw158StaticRoutingTest : Rw158Harness() {

    @Test
    fun `todays pinned card tap still opens the full photo - unchanged`() {
        val viewed = mutableListOf<WearIveRunDayEntry>()
        val todayEntry = entry(
            today, "New York, NY", "ny-today", 5_000,
            photoFile(0xFF2050C8.toInt(), "today")
        )
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = listOf(todayEntry),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                onViewPhoto = { viewed += it },
                heroResign = { null }
            )
        }
        val collection = awaitCollectionDialog(stage)
        clickCardAtText(stage, dialogRoot(collection), "Today · Tue, Nov 10")
        stage.settle()
        assertEquals(
            "today's card keeps the RW-133 full-photo tap",
            listOf("ny-today"), viewed.map { it.sessionId }
        )
        val texts = stage.texts(dialogRoot(collection))
        assertFalse(
            "today's tap must not open the sheet",
            texts.any { it.contains("Choose the card for") }
        )
        stage.controller.pause().stop().destroy()
    }

    // ── The ruling, surface (b): the footer's entry row ───────

    @Test
    fun `footer entry row tap routes to the past-entry tap when wired`() {
        val counters = intArrayOf(0, 0)
        val stage = footerStage(onEntryTap = {}, counters = counters)
        val root = activityRoot(stage)
        clickCardAtText(stage, root, "Mon, Nov 2 · 78°")
        stage.settle()
        assertEquals("the past-entry tap fires", 1, counters[1])
        assertEquals("the collection doorway is not the row's tap", 0, counters[0])
        stage.controller.pause().stop().destroy()
    }

    @Test
    fun `footer entry row tap keeps the collection doorway when unwired`() {
        val counters = intArrayOf(0, 0)
        val stage = footerStage(onEntryTap = null, counters = counters)
        val root = activityRoot(stage)
        clickCardAtText(stage, root, "Mon, Nov 2 · 78°")
        stage.settle()
        assertEquals("today's row keeps the collection doorway", 1, counters[0])
        assertEquals("no entry tap is wired", 0, counters[1])
        stage.controller.pause().stop().destroy()
    }

    // ── Host wiring (source pin — the full HeroMainScreen
    // composition is beyond this harness; the pin follows the
    // repo's WearIveRunElementPinTest pattern) ─────────────────

    @Test
    fun `host wires the footers past-entry row to the share studio`() {
        fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                for (candidate in listOf(
                    relative,
                    "RunWear-android/$relative",
                    "../$relative"
                )) {
                    val file = File(dir, candidate)
                    if (file.isFile) return file.readText()
                }
                dir = dir.parentFile
            }
            throw AssertionError("source not found: $relative")
        }
        val host = source(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        )
        assertTrue(
            "the footer row tap is gated on the entry being past",
            host.contains("dayEntry.date < uiState.locationNow.toLocalDate()")
        )
        assertTrue(
            "the footer opens the Share Studio sheet for the entry",
            host.contains("wirFooterShareEntry") &&
                host.contains("ShareStudioSheet(")
        )
        val dock = source(
            "app/src/main/java/com/runwear/app/weariverun/WearIveRunDock.kt"
        )
        assertTrue(
            "the footer section carries the entry-tap seam",
            dock.contains("onEntryTap")
        )
        val studio = source(
            "app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt"
        )
        assertTrue(
            "the poster preview names itself as the photo door",
            studio.contains("\"View full photo\"")
        )
    }

    // ── Frame: the sheet open on an old entry ─────────────────
}
