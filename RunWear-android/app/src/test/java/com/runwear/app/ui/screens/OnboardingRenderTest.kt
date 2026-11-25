package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.theme.DarkPalette
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WS-10 rendered evidence (A5 discipline — BOTH modes): genuine
 * renders of the production [OnboardingScreen]'s FIRST step — the
 * frame-28 promise screen the T-2 ruling adopted into the Android
 * sequence (script logo, the A2 headline, the two promise rows,
 * "Get Started") — in light AND dark, following the
 * [HeroMainScreenRenderTest] harness pattern (Robolectric native
 * graphics; this VM has no emulator).
 *
 * Assertions: the frame renders (opaque-pixel census) and the pixel
 * at the top-left corner is exactly the palette's Background token —
 * the token ground is what the surfaces actually paint, in both
 * modes. PNGs land in build/render-out-ws10/ for the WS-9 /
 * design-conformance walkthrough. The setup and location steps
 * share the same tokens, scaffolding, and type (their copy and
 * order are pinned by OnboardingSequenceTest); the pre-WS-10 WS-8
 * renders of the old setup-first screen are superseded by these.
 */
abstract class OnboardingShotHarness {

    private val outDir: File =
        File(System.getProperty("ws10ShotsDir") ?: "build/render-out-ws10")

    protected fun shoot(name: String, darkTheme: Boolean) {
        // RunWearTheme publishes the palette to the RunWearColors
        // holder from a SideEffect, which lands AFTER the first
        // composition reads its tokens — in a two-class harness the
        // first frame would paint the previous class's palette.
        // Pre-publish the mode under test (exactly what the theme is
        // about to publish) so the render is deterministic.
        RunWearColors.setActivePalette(
            if (darkTheme) DarkPalette else LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = darkTheme) {
                OnboardingScreen(
                    onComplete = { _, _, _ -> },
                    onRequestLocationPermission = {}
                )
            }
        }

        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idle()

        val widthDp = 390
        val heightDp = 844
        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))

        val px = IntArray(widthPx * heightPx)
        bitmap.getPixels(px, 0, widthPx, 0, 0, widthPx, heightPx)
        var opaque = 0
        for (p in px) if (p ushr 24 == 0xFF) opaque++
        assertTrue(
            "only $opaque/${widthPx * heightPx} opaque pixels — nothing rendered?",
            opaque > widthPx * heightPx / 4
        )

        val expectedGround = (if (darkTheme) DarkPalette.Background else LightPalette.Background).toArgb()
        assertEquals(
            "top-left corner is not the palette Background token",
            expectedGround,
            px[0]
        )

        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.pause().stop().destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class OnboardingLightRenderTest : OnboardingShotHarness() {
    @Test
    fun onboardingPromiseLight() = shoot("onboarding-promise-light", darkTheme = false)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class OnboardingDarkRenderTest : OnboardingShotHarness() {
    @Test
    fun onboardingPromiseDark() = shoot("onboarding-promise-dark", darkTheme = true)
}
