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

/** RW-158: the eye lives on inside the sheet — the Postcard preview tap opens the entry's full photo (RW-133). Own class — see Rw158Harness for why. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw158PosterPreviewTapTest : Rw158Harness() {

    @Test
    fun `poster preview tap inside the sheet opens the full photo`() {
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
        val collection = openChapter(stage)
        clickCardAtText(stage, dialogRoot(collection), "Sun, Nov 8")
        val sheet = awaitNewDialog(stage, collection)
            ?: throw AssertionError("share studio sheet not shown")
        // The preview exists only once the poster render lands.
        assertTrue(
            "the poster preview renders in the sheet",
            awaitText(stage, { dialogRoot(sheet) }, "View full photo", rounds = 120)
        )
        clickByDescription(stage, dialogRoot(sheet), "View full photo")
        stage.settle()
        assertEquals(
            "the preview tap opens the tapped day's full photo",
            listOf("miami-nov8"), viewed.map { it.sessionId }
        )
        stage.controller.pause().stop().destroy()
    }
}
