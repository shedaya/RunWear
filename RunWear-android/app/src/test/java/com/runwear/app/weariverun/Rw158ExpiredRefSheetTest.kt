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

/** RW-158 + RW-152 §3: an old entry whose stored ref is an EXPIRED signed URL opens the sheet and renders its poster through the resolver — the exact path that failed on the owner's device. Own class — see Rw158Harness for why. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw158ExpiredRefSheetTest : Rw158Harness() {

    @Test
    fun `expired signed URL old entry renders its poster in the sheet via the resolver`() {
        val fresh = photoFile(0xFFC82020.toInt(), "resigned")
        val expiredRef = "https://mranecletgzsjjlqybhg.supabase.co/storage/v1/" +
            "object/sign/personal-heroes/u1/miami-hero.jpg" +
            "?token=STALE&expires_at=1762000000"
        val resignCalls = mutableListOf<String>()
        val old = entry(
            LocalDate.of(2026, 11, 2), "Miami, FL", "miami-expired",
            2_000, expiredRef
        )
        val viewed = mutableListOf<WearIveRunDayEntry>()
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = listOf(old),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                onViewPhoto = { viewed += it },
                heroResign = { path ->
                    resignCalls += path
                    fresh
                }
            )
        }
        val collection = openChapter(stage)
        clickCardAtText(stage, dialogRoot(collection), "Mon, Nov 2")
        val sheet = awaitNewDialog(stage, collection)
            ?: throw AssertionError("share studio sheet not shown")
        assertTrue(
            "the resolver was asked to re-sign the stored path",
            resignCalls.any { it.contains("miami-hero.jpg") }
        )
        assertTrue(
            "the poster preview renders from the re-signed photo",
            awaitText(stage, { dialogRoot(sheet) }, "View full photo", rounds = 120)
        )
        val texts = stage.texts(dialogRoot(sheet))
        assertFalse(
            "the expired ref must NOT surface as a photo failure",
            texts.any { it.contains("photo couldn't load") }
        )
        assertTrue("opening the sheet shares nothing", viewed.isEmpty())
        stage.controller.pause().stop().destroy()
    }
}
