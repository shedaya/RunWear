package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.GenderPreference
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
 * GEN-3 share-render scope (Sol's iOS gen-2 walkthrough verdict —
 * "sharing cards fails": a blank sheet + lone spinner for ~23 s,
 * the card flickering in and out; the Android family history is
 * W-9's ~15 s of nothing). These tests pin the Android artifacts'
 * render contract, which the share sheet's progress state is
 * driven by:
 *
 *  - SETTLES: `onRendered` fires for every artifact, whatever its
 *    photo does — decoded, absent, OR FAILED. The pre-fix poster
 *    and kit card only ever fired on image SUCCESS, so any decode
 *    error left the sheet's "Rendering…" state up forever: a
 *    spinner as the settled state.
 *  - EXACTLY ONCE: the callback fires one time per entry, however
 *    often the painter re-delivers state across recompositions.
 *    The pre-fix poster had no once-guard (the kit card had one),
 *    so every painter delivery wrote the sheet's ready-state anew.
 *  - PROMPTLY: a photo-less artifact settles within the first
 *    composition passes (no work is pending at all); a local-file
 *    photo settles as soon as its decode lands.
 *
 * Elapsed settle times are printed (visible in the JUnit XML's
 * system-out in the CI artifact) as the before/after timing
 * evidence the harness can produce.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ShareRenderStabilityTest {

    private fun entry(heroRef: String?, items: List<String>) = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 4),
        place = "Overland Park, KS",
        feelsLikeF = 48.0,
        conditionLabel = "Overcast",
        isSnowing = false,
        isRaining = false,
        items = items,
        heroRef = heroRef
    )

    private class Probe {
        val count = AtomicInteger(0)
        val latch = CountDownLatch(1)
        @Volatile var settledAtMs: Long = -1
        val startedAtMs = System.currentTimeMillis()
        val callback: () -> Unit = {
            count.incrementAndGet()
            settledAtMs = System.currentTimeMillis() - startedAtMs
            latch.countDown()
        }
    }

    /**
     * Compose [content], then pump the harness (main-looper idles +
     * real-time latch waits, since Coil decodes on real IO threads)
     * until [probe] settles or [timeoutMs] elapses. Returns true if
     * it settled. Extra passes afterwards prove exactly-once.
     */
    private fun composeAndSettle(probe: Probe, timeoutMs: Long, content: @Composable () -> Unit): Boolean {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp)) { content() }
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        fun passes(n: Int) {
            repeat(n) {
                // Full scheduler flush (not just a timed advance):
                // effect coroutines (LaunchedEffect) are posted to
                // the foreground scheduler behind choreographer
                // frames, and a partial advance left them unrun in
                // run 41919186433 — the painter-driven settles fired
                // while the effect-driven ones (photo-less poster,
                // name-only kit card) never did. Flush + idle drains
                // every posted frame and task.
                Robolectric.flushForegroundThreadScheduler()
                shadowOf(Looper.getMainLooper()).idle()
                composeView.measure(wSpec, hSpec)
                composeView.layout(0, 0, w, h)
                composeView.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
                Robolectric.flushForegroundThreadScheduler()
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        var settled = false
        while (System.currentTimeMillis() < deadline) {
            passes(2)
            if (probe.latch.await(25, TimeUnit.MILLISECONDS)) {
                settled = true
                break
            }
        }
        if (settled) {
            // Exactly-once is a settle-then-stay property: more
            // composition/draw passes must not re-fire the callback.
            passes(10)
        }
        return settled
    }

    @Test
    fun `poster without a photo settles promptly and exactly once`() {
        val probe = Probe()
        val settled = composeAndSettle(probe, timeoutMs = 15_000) {
            MorningPoster(entry = entry(null, listOf("Light Long Sleeve")), onRendered = probe.callback)
        }
        println("poster/no-photo settled=${settled} at ${probe.settledAtMs}ms count=${probe.count.get()}")
        assertTrue("photo-less poster must settle (nothing is pending)", settled)
        assertEquals("onRendered must fire exactly once", 1, probe.count.get())
    }

    @Test
    fun `poster with an undecodable photo still settles - no spinner as the settled state`() {
        val probe = Probe()
        val settled = composeAndSettle(probe, timeoutMs = 30_000) {
            MorningPoster(
                entry = entry("file:///nonexistent/runwear/hero-that-is-not-there.jpg", listOf("Light Long Sleeve")),
                onRendered = probe.callback
            )
        }
        println("poster/broken-photo settled=${settled} at ${probe.settledAtMs}ms count=${probe.count.get()}")
        assertTrue(
            "a failed photo decode must still settle the poster; the sheet's rendering state may not hang on it",
            settled
        )
        assertEquals("onRendered must fire exactly once", 1, probe.count.get())
    }

    @Test
    fun `poster with a real local photo settles exactly once`() {
        // A genuine decode: write a real PNG and hand its path over.
        val photoFile = File.createTempFile("runwear-hero", ".png")
        val photo = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        photoFile.outputStream().use { out -> photo.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val probe = Probe()
        val settled = composeAndSettle(probe, timeoutMs = 30_000) {
            MorningPoster(
                entry = entry(photoFile.absolutePath, listOf("Light Long Sleeve")),
                onRendered = probe.callback
            )
        }
        println("poster/real-photo settled=${settled} at ${probe.settledAtMs}ms count=${probe.count.get()}")
        assertTrue("a decodable local photo must settle the poster", settled)
        assertEquals("onRendered must fire exactly once however often the painter re-delivers", 1, probe.count.get())
    }

    @Test
    fun `kit card with name-only items settles promptly and exactly once`() {
        val probe = Probe()
        val settled = composeAndSettle(probe, timeoutMs = 15_000) {
            KitCard(
                entry = entry(null, listOf("Mystery Garment", "Unknown Layer")),
                gender = GenderPreference.UNISEX,
                onRendered = probe.callback
            )
        }
        println("kit/name-only settled=${settled} at ${probe.settledAtMs}ms count=${probe.count.get()}")
        assertTrue("a kit card with no renders must settle immediately", settled)
        assertEquals(1, probe.count.get())
    }

    @Test
    fun `kit card with real garment renders settles exactly once`() {
        val probe = Probe()
        val settled = composeAndSettle(probe, timeoutMs = 30_000) {
            KitCard(
                entry = entry(null, listOf("Light Long Sleeve", "Windbreaker")),
                gender = GenderPreference.MALE,
                onRendered = probe.callback
            )
        }
        println("kit/real-renders settled=${settled} at ${probe.settledAtMs}ms count=${probe.count.get()}")
        assertTrue("the kit card must settle once its first render lands OR fails", settled)
        assertEquals(1, probe.count.get())
    }
}
