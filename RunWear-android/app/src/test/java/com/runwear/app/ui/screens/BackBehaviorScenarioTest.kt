package com.runwear.app.ui.screens

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.feedback.FeedbackBundleFactory
import com.runwear.app.ui.theme.RunWearTheme
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WS-5 (F-2, standing gate item) — system Back exercised on an
 * overlay surface, at activity-scenario level: the production
 * [FeedbackScreen] (a boolean full-screen overlay hosted from
 * MainActivity, same class as the F-2 surfaces) is composed in a
 * real ComponentActivity, and the activity's
 * OnBackPressedDispatcher is fired. Before WS-5 the app had no
 * BackHandler anywhere, so this press fell through and finished
 * the activity; now the overlay's handler consumes it, the
 * overlay's dismiss fires, and the activity stays alive.
 *
 * The Make It Me upload flow's handler is the same mechanism with
 * the UploadBackPolicy step-back (pinned in SheetDisciplinePinTest
 * + SettingsInventoryTest's sibling policy test); Your Photos is a
 * Dialog, whose back handling is the window's own (pinned). A
 * physical Back press on a device remains WS-9's pass — stated in
 * the gate report, not simulated here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class BackBehaviorScenarioTest {

    @Test
    fun `system Back on the feedback overlay dismisses it and does not finish the activity`() {
        val dismissed = AtomicBoolean(false)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                FeedbackScreen(
                    bundle = FeedbackBundleFactory.Bundle(
                        displayFields = emptyList(),
                        json = JSONObject()
                    ),
                    preselectedCategory = null,
                    onSend = { _, _, _, _ -> },
                    onDismiss = { dismissed.set(true) }
                )
            }
        }
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(20) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertFalse("nothing dismissed before Back", dismissed.get())
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Back must fire the overlay's dismiss (return to the opening surface)",
            dismissed.get()
        )
        assertFalse(
            "Back must NOT finish the activity (that was the F-2 trap)",
            activity.isFinishing
        )
        controller.pause().stop().destroy()
    }
}
