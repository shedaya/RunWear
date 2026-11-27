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

/** RW-158: the chapter cover keeps its drill-down into the chapter's days (the ruling moved the DAY rows, not the cover). Own class — see Rw158Harness for why. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw158ChapterDrillDownTest : Rw158Harness() {

    @Test
    fun `chapter cover tap still drills into the chapter days`() {
        val viewed = mutableListOf<WearIveRunDayEntry>()
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = miamiPastEntries(),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                onViewPhoto = { viewed += it },
                heroResign = { null }
            )
        }
        val collection = awaitCollectionDialog(stage)
        clickCardAtText(stage, dialogRoot(collection), "Miami, FL")
        assertTrue(
            "the chapter detail shows its days",
            awaitText(stage, { dialogRoot(collection) }, "Sun, Nov 8")
        )
        val texts = stage.texts(dialogRoot(collection))
        assertTrue(texts.any { it.contains("Mon, Nov 2") })
        assertFalse(
            "drill-down must not open the sheet",
            texts.any { it.contains("Choose the card for") }
        )
        assertTrue("drill-down must not open the viewer", viewed.isEmpty())
        stage.controller.pause().stop().destroy()
    }
}
