package com.runwear.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.ui.components.HeroPill
import com.runwear.app.ui.components.HeroPillPolicy
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunMorphSurface
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-174 (Nov 14, 2026) — discriminating probes for the
 * consolidated fix pass, Android items, written against head
 * 5a9e0b0 (the RW-173 fix) where every probe FAILS:
 *
 * ITEM 1 (audit rows 39+40, spec §4.7) — the delete exit runs
 * BOTH choreographies at once: the host layer's fade + 26dp
 * sink over 300ms AND the morph's internal content fade 160ms
 * + height fold 400ms, in both forms. From MINI the surface
 * folds for ~560ms instead of leaving at 300ms with its height
 * RELEASED; from EXPANDED it additionally sinks and
 * container-fades. The probes sample the surface's painted
 * semantics bounds in 50ms steps through the exit:
 *  - MINI: the height must NEVER drop while the surface is
 *    present (released, not folded) and the surface must be
 *    gone inside the 300ms exit's envelope;
 *  - EXPANDED: the top must NEVER move (no sink) while the
 *    height DOES fold to gone.
 *
 * ITEM 3 (audit row 34, spec §4.1) — the live morph surface
 * has NO pine ring (the only PineRing lives in the retired
 * WearIveRunDock seat Home does not compose). The probe arms
 * the creation cue on the morph directly and scans the
 * surface's top-edge band, centre span, for pine pixels
 * (green-dominant) blooming there during the pulse.
 *
 * ITEM 2 (audit row 53, RW-171) — the free-tier generating
 * pill stacks "Get it instantly next time" as a second line.
 * The probe renders the production HeroPill from the real
 * policy resolution and asserts the pill's texts occupy ONE
 * line band and the upsell text is absent.
 */
abstract class Rw174Base : Rw173Base() {

    protected val fix3Dir = File(
        "/home/hatch/workspace/release-prep/" +
            "rw135-rebuild-2026-11-12/work/fix3-captures-android"
    )

    protected fun captureFix3(host: Host, name: String): Bitmap {
        val bitmap = capture(host, name)
        saveFix3(bitmap, name)
        return bitmap
    }

    protected fun saveFix3(bitmap: Bitmap, name: String) {
        fix3Dir.mkdirs()
        File(fix3Dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    protected fun probe174(lines: List<String>) {
        fix3Dir.mkdirs()
        File(fix3Dir, "rw174-probe.txt").appendText(
            lines.joinToString("\n") + "\n"
        )
        lines.forEach { println("RW174-PROBE $it") }
    }

    protected fun surfaceBoundsOrNull(
        host: Host
    ): androidx.compose.ui.geometry.Rect? =
        try {
            surfaceBounds(host)
        } catch (t: Throwable) {
            null
        }

    /** One drawn frame without a file write (the frame clock
     * only advances when frames are drawn in this harness). */
    protected fun drawFrame(host: Host): Bitmap {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    protected data class ExitTrajectory(
        val tops: List<Float>,
        val heights: List<Float>,
        val goneAtBatch: Int,
        val batches: Int
    )

    /** Sample the exiting surface's semantics bounds batch by
     * batch (the Rw173 mid-morph idiom: runOneTask batches +
     * a drawn frame per batch — scheduler advanceBy overshoots
     * whole animation phases in this harness, which is how the
     * first version of these probes read "gone at step 1").
     * [onPresentFrame] fires with each drawn frame while the
     * surface is present (frame ownership passes to it). */
    protected fun sampleExitTrajectory(
        host: Host,
        onPresentFrame: (Bitmap, Float, Float) -> Unit = { b, _, _ ->
            b.recycle()
        }
    ): ExitTrajectory {
        val looper = org.robolectric.Shadows.shadowOf(
            android.os.Looper.getMainLooper()
        )
        val tops = mutableListOf<Float>()
        val heights = mutableListOf<Float>()
        var goneAt = -1
        var batches = 0
        while (batches < 1500) {
            repeat(5) { looper.runOneTask() }
            batches++
            val frame = drawFrame(host)
            val b = surfaceBoundsOrNull(host)
            if (b == null) {
                goneAt = batches
                frame.recycle()
                break
            }
            tops += b.top
            heights += b.bottom - b.top
            onPresentFrame(frame, b.top, b.bottom - b.top)
        }
        return ExitTrajectory(tops, heights, goneAt, batches)
    }

    protected fun summarize(values: List<Float>): String =
        if (values.isEmpty()) {
            "[]"
        } else {
            "n=${values.size} first=${values.first()} " +
                "min=${values.min()} max=${values.max()} " +
                "last=${values.last()}"
        }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw174DeleteFromMiniTest : Rw174Base() {

    @Test
    fun `rw174 item1 - delete from MINI releases the height, gone at the 300ms exit`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        captureFix3(host, "rw174-home-mini-rest-light.png")
        val b0 = surfaceBounds(host)
        val h0 = b0.bottom - b0.top

        clickDescriptionNoSettle(host, "Remove from Wear I've Run")
        var presentFrames = 0
        val traj = sampleExitTrajectory(host) { frame, _, _ ->
            presentFrames++
            if (presentFrames == 5) {
                saveFix3(frame, "rw174-delete-mini-midexit-light.png")
            }
            frame.recycle()
        }
        probe174(
            listOf(
                "[delete-mini] restHeightPx=$h0 " +
                    "heights=${summarize(traj.heights)} " +
                    "goneAtBatch=${traj.goneAtBatch}"
            )
        )
        assertTrue(
            "[delete-mini] exit samples were taken " +
                "(heights ${summarize(traj.heights)})",
            traj.heights.size >= 3
        )
        assertTrue(
            "[delete-mini] the surface leaves (exit completes, " +
                "data removal fires) — goneAtBatch=${traj.goneAtBatch}",
            traj.goneAtBatch > 0
        )
        assertTrue(
            "[delete-mini] height RELEASED, never folded: every " +
                "sampled height stays at the rest height " +
                "(rest $h0, heights ${summarize(traj.heights)}; the " +
                "baseline's internal fold drives it to 0 over " +
                "~400ms after a 160ms content fade)",
            traj.heights.all { it >= h0 - 3f }
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw174DeleteFromExpandedTest : Rw174Base() {

    @Test
    fun `rw174 item1 - delete from EXPANDED folds with no sink and no host fade`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        captureFix3(host, "rw174-home-expanded-light.png")
        val b1 = surfaceBounds(host)
        val h1 = b1.bottom - b1.top
        val t1 = b1.top

        clickDescriptionNoSettle(host, "Remove from Wear I've Run")
        var savedMidFold = false
        val traj = sampleExitTrajectory(host) { frame, _, h ->
            if (!savedMidFold && h < h1 * 0.85f) {
                savedMidFold = true
                saveFix3(frame, "rw174-delete-expanded-midfold-light.png")
            }
            frame.recycle()
        }
        // The sink discriminator is the surface's BOTTOM
        // edge, not its top: the surface is bottom-anchored
        // in its overlay seat, so a pure height FOLD leaves
        // the bottom exactly where it was (the top descends
        // as the height shrinks — geometry, not sink), while
        // the baseline host layer's 26dp sink translates the
        // WHOLE container, bottom included. Window: samples
        // while the height is still >= 90% of rest (the
        // content-fade segment + the fold's start, when the
        // baseline sink runs). Baseline read from the
        // recorded trajectory: the window's last top was
        // 1340.6 at height >= 364.5, i.e. a bottom >= 1705
        // against the rest bottom 1660 — a drift of >= +45px.
        val restBottom = t1 + h1
        val sinkWindowBottoms = traj.tops.zip(traj.heights)
            .filter { (_, h) -> h >= h1 * 0.9f }
            .map { (t, h) -> t + h }
        val pairs = traj.tops.zip(traj.heights).take(60)
            .joinToString(" ") { (t, h) -> "($t,$h)" }
        probe174(
            listOf(
                "[delete-expanded] restTopPx=$t1 restHeightPx=$h1 " +
                    "restBottomPx=$restBottom",
                "[delete-expanded] tops=${summarize(traj.tops)} " +
                    "heights=${summarize(traj.heights)} " +
                    "goneAtBatch=${traj.goneAtBatch}",
                "[delete-expanded] sinkWindowBottoms=" +
                    "${summarize(sinkWindowBottoms)}",
                "[delete-expanded] firstPairs(top,height)=$pairs"
            )
        )
        assertTrue(
            "[delete-expanded] exit samples were taken " +
                "(heights ${summarize(traj.heights)})",
            traj.heights.size >= 3
        )
        assertTrue(
            "[delete-expanded] the surface leaves (exit completes)",
            traj.goneAtBatch > 0
        )
        assertTrue(
            "[delete-expanded] sink-window samples exist " +
                "(was ${sinkWindowBottoms.size})",
            sinkWindowBottoms.size >= 3
        )
        assertTrue(
            "[delete-expanded] NO SINK: the bottom edge never " +
                "moves while the exit runs (rest bottom " +
                "$restBottom, sink-window bottoms " +
                "${summarize(sinkWindowBottoms)}; the baseline " +
                "host layer's sink drifts it >= +45px — the " +
                "8px tolerance covers measure noise)",
            sinkWindowBottoms.all {
                kotlin.math.abs(it - restBottom) <= 8f
            }
        )
        assertTrue(
            "[delete-expanded] the height FOLDS before the surface " +
                "leaves (rest $h1, heights ${summarize(traj.heights)})",
            traj.heights.any { it < h1 * 0.6f }
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw174PineRingMorphTest : Rw174Base() {

    @Composable
    private fun MorphCue(cueKey: Int, cueArmed: Boolean, photo: String) {
        val day = fixtureDayEntry(photo)
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, nov11),
            entry = fixtureBarEntry(photo),
            entryLine = WearIveRunDockNarration.entryLine(
                day, nov11, TemperatureUnit.FAHRENHEIT
            ),
            expanded = false,
            onExpand = {},
            onCollapse = {},
            onDelete = {},
            onOpenCollection = {},
            cueKey = cueKey,
            cueArmed = cueArmed,
            maxExpandedHeightDp = 2000f
        )
    }

    @Test
    fun `rw174 item3 - creation cue blooms a pine ring on the live morph surface`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        var cue by mutableStateOf(0)
        var armed by mutableStateOf(false)
        val host = compose(dark = false) { MorphCue(cue, armed, photo) }
        val bounds = surfaceBounds(host)
        val density = host.composeView.resources.displayMetrics.density

        // Arm the creation cue AFTER the rest composition has
        // settled (the Rw169 morph-sequence idiom): the pulse
        // and its ring run 900ms from here, sampled in 50ms
        // steps so the bloom is caught mid-flight.
        cue = 1
        armed = true
        var bestPine = 0
        val counts = mutableListOf<Int>()
        val looper = org.robolectric.Shadows.shadowOf(
            android.os.Looper.getMainLooper()
        )
        for (step in 1..240) {
            repeat(5) { looper.runOneTask() }
            val bitmap = drawFrame(host)
            // The ring band: the surface's top edge, centre
            // span — clear of the wordmark (left), the
            // controls (right) and the narration (mid-row).
            val yFrom = (bounds.top - 2f).toInt().coerceAtLeast(0)
            val yTo = (bounds.top + 4f * density).toInt()
                .coerceAtMost(bitmap.height)
            val xFrom = (bounds.left + (bounds.right - bounds.left) * 0.35f).toInt()
            val xTo = (bounds.left + (bounds.right - bounds.left) * 0.65f).toInt()
            var pine = 0
            for (y in yFrom until yTo) {
                for (x in xFrom until xTo) {
                    val px = bitmap.getPixel(x, y)
                    val r = Color.red(px)
                    val g = Color.green(px)
                    if (g - r >= 10) pine++
                }
            }
            counts += pine
            if (pine > bestPine) {
                bestPine = pine
                saveFix3(bitmap, "rw174-pine-ring-midpulse-light.png")
            }
            bitmap.recycle()
            // The pulse is 900ms; once a strong bloom has been
            // seen and has decayed back to zero, stop early.
            if (bestPine >= 30 && pine == 0 && step > 40) break
        }
        probe174(
            listOf(
                "[pine-ring] bounds top=${bounds.top} left=${bounds.left} " +
                    "right=${bounds.right} density=$density",
                "[pine-ring] pinePixelsPerStep=$counts best=$bestPine"
            )
        )
        assertTrue(
            "[pine-ring] a pine ring blooms around the live surface " +
                "during the creation pulse (best pine-pixel count in " +
                "the top-edge band $bestPine, per-step $counts; the " +
                "baseline surface draws no ring at all)",
            bestPine >= 30
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw174PillOneLineTest : Rw174Base() {

    @Test
    fun `rw174 item2 rw171 - free generating pill is ONE line, no upsell line`() {
        val content = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 0
        )
        assertTrue("generating resolves a pill", content != null)
        val host = compose(dark = false, pumpSettle = true) {
            HeroPill(content = content!!, onAction = {})
        }
        captureFix3(host, "rw174-pill-making-free-oneline-light.png")
        val density = host.composeView.resources.displayMetrics.density
        val pillTexts = unmergedNodeBoxes(host).filter {
            it.text == "Make It Me" ||
                it.text.contains("Making this you") ||
                it.text.contains("Get it instantly")
        }
        val span = if (pillTexts.isEmpty()) {
            -1f
        } else {
            pillTexts.maxOf { it.bottom } - pillTexts.minOf { it.top }
        }
        probe174(
            listOf(
                "[pill-oneline] texts=${pillTexts.map { it.text }}",
                "[pill-oneline] verticalSpanPx=$span density=$density"
            )
        )
        assertTrue(
            "[pill-oneline] the script mark and the making line render",
            pillTexts.any { it.text.contains("Making this you") }
        )
        assertTrue(
            "[pill-oneline] NO upsell second line in the pill " +
                "(RW-171: the copy leaves the pill entirely)",
            pillTexts.none { it.text.contains("Get it instantly") }
        )
        assertTrue(
            "[pill-oneline] every pill text sits in ONE line band " +
                "(span ${span}px <= 26dp; the baseline stacks a " +
                "second line beneath the generating row)",
            span > 0f && span <= 26f * density
        )
        host.controller.pause().stop().destroy()
    }
}
