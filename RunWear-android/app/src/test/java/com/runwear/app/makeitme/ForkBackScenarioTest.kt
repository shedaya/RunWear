package com.runwear.app.makeitme

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.theme.RunWearTheme
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
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
 * WS-6 (BACKLOG open item from WS-5's Android lane; standing gate
 * item from F-2) — system Back exercised on the Make It Me
 * onboarding FORK screen, at activity-scenario level.
 *
 * The fork was the last boolean overlay with no Back handler: it is
 * the onboarding flow's root screen, hosted from MainActivity as a
 * plain overlay, so a Back press fell through to the activity and
 * closed the app (the F-2 trap, on onboarding's final step). The
 * policy — [ForkBackPolicy], mirroring the upload flow's
 * [UploadBackPolicy] — is: Back steps back within a flow and exits
 * only at its root; the fork IS the root, so Back exits the flow
 * (as stock, the upload flow's root-exit end-state), returns to
 * home, and never finishes the activity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class ForkBackScenarioTest {

    @Test
    fun `the fork back policy exits only at the flow root`() {
        assertEquals(ForkBackAction.EXIT_FLOW, ForkBackPolicy.actionFor())
        // The mirror holds: the upload flow also exits only at ITS
        // root (CONSENT) and steps back / stays everywhere else.
        assertEquals(UploadBackAction.EXIT_FLOW, UploadBackPolicy.actionFor(UploadStep.CONSENT))
        assertEquals(
            UploadBackAction.STEP_BACK_TO_CONSENT,
            UploadBackPolicy.actionFor(UploadStep.PHOTOS)
        )
    }

    @Test
    fun `system Back on the fork exits the flow and does not finish the activity`() {
        val backedOut = AtomicBoolean(false)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                MakeItMeForkScreen(
                    heroImageUrl = null,
                    onUseMyPhotos = {},
                    onUseStandardPhotos = {},
                    onBack = { backedOut.set(true) }
                )
            }
        }
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(20) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertFalse("no exit before Back", backedOut.get())
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Back at the fork (the flow's root) must fire the flow exit",
            backedOut.get()
        )
        assertFalse(
            "Back must NOT finish the activity (the F-2 trap)",
            activity.isFinishing
        )
    }
}
