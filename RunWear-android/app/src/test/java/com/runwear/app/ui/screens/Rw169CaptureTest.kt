package com.runwear.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunMorphSurface
import com.runwear.shared.domain.model.TemperatureUnit
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-169 §7G.1 (Nov 11, 2026): the conformance captures — six
 * states at approval-fixture data in BOTH viewport classes
 * (folded portrait w390dp-h740dp; wide w1000dp-h800dp), rendered
 * by the production build in the Robolectric native-graphics
 * harness. Full-screen frames use the Overland Park fixture
 * state (place "Overland Park, KS", bundled photo, the screen's
 * own settled entry); the morph stills use the exact §7B
 * fixtures via the morph surface directly (narration "Planned
 * for Los Angeles, CA · Tomorrow", 4-item kit). The morph's
 * motion beats are the still sequence p≈0 / 0.25 / 0.5 / 0.75 / 1
 * — the harness cannot record video; the sequence is assembled
 * into an animated GIF by the lane for comparison against
 * prototype.mp4's beats.
 *
 * Harness rule (RW-144/152 lanes): ONE full-screen composition
 * per class — the first full-screen composition in a sandbox is
 * the one that reveals, so every Home state gets its own class.
 */
abstract class Rw169CaptureBase : Rw169HomeBase() {

    protected fun scrollMid(host: Host) {
        val node = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height } ?: return
        node.config[SemanticsActions.ScrollBy].action?.invoke(0f, 900f)
        advanceFrames(host, 3)
    }

    protected fun homeHost(): Host {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = overlandState(photo)
        seedExactEntry(state)
        return compose(dark = false) { Home(state, exhaustedMim()) }
    }

    @Composable
    protected fun MorphCapture(expanded: Boolean, photo: String) {
        val day = fixtureDayEntry(photo)
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, nov11),
            entry = fixtureBarEntry(photo),
            entryLine = WearIveRunDockNarration.entryLine(
                day, nov11, TemperatureUnit.FAHRENHEIT
            ),
            expanded = expanded,
            onExpand = {},
            onCollapse = {},
            onDelete = {},
            onOpenCollection = {},
            maxExpandedHeightDp = 2000f
        )
    }

    protected fun morphSequence(prefix: String) {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        var expanded by mutableStateOf(false)
        val host = compose(dark = false) { MorphCapture(expanded, photo) }
        capture(host, "$prefix-morph-p000.png")
        expanded = true
        advanceFrames(host, 1)
        capture(host, "$prefix-morph-p025.png")
        advanceFrames(host, 1)
        capture(host, "$prefix-morph-p050.png")
        advanceFrames(host, 1)
        capture(host, "$prefix-morph-p075.png")
        advanceFrames(host, 3)
        capture(host, "$prefix-expanded-rest.png")
        host.controller.pause().stop().destroy()
    }
}

// ---------------- Folded portrait (w390dp-h740dp) ----------------

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169CapFoldedMiniRest : Rw169CaptureBase() {
    @Test
    fun `capture folded mini at rest plus chrome crop`() {
        val host = homeHost()
        capture(host, "rw169-folded-mini-rest.png")
        val h = host.composeView.resources.displayMetrics.heightPixels
        captureCrop(host, "rw169-folded-chrome.png", 0, (h * 0.30f).toInt())
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169CapFoldedMiniScroll : Rw169CaptureBase() {
    @Test
    fun `capture folded mini mid-scroll`() {
        val host = homeHost()
        scrollMid(host)
        capture(host, "rw169-folded-mini-midscroll.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169CapFoldedMorph : Rw169CaptureBase() {
    @Test
    fun `capture folded morph sequence`() = morphSequence("rw169-folded")
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169CapFoldedExpanded : Rw169CaptureBase() {
    @Test
    fun `capture folded expanded at rest`() {
        val host = homeHost()
        clickDescription(host, "Expand Wear I've Run")
        capture(host, "rw169-folded-expanded-home.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h400dp-xhdpi")
class Rw169CapFoldedExpandedCap : Rw169CaptureBase() {
    @Test
    fun `capture folded expanded at the height cap`() {
        val host = homeHost()
        clickDescription(host, "Expand Wear I've Run")
        capture(host, "rw169-folded-expanded-cap.png")
        host.controller.pause().stop().destroy()
    }
}

// ---------------- Wide / unfolded (w1000dp-h800dp) ----------------

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169CapWideMiniRest : Rw169CaptureBase() {
    @Test
    fun `capture wide mini at rest plus chrome crop`() {
        val host = homeHost()
        capture(host, "rw169-wide-mini-rest.png")
        val h = host.composeView.resources.displayMetrics.heightPixels
        captureCrop(host, "rw169-wide-chrome.png", 0, (h * 0.30f).toInt())
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169CapWideMiniScroll : Rw169CaptureBase() {
    @Test
    fun `capture wide mini mid-scroll`() {
        val host = homeHost()
        scrollMid(host)
        capture(host, "rw169-wide-mini-midscroll.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169CapWideMorph : Rw169CaptureBase() {
    @Test
    fun `capture wide morph sequence`() = morphSequence("rw169-wide")
}

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169CapWideExpanded : Rw169CaptureBase() {
    @Test
    fun `capture wide expanded at rest`() {
        val host = homeHost()
        clickDescription(host, "Expand Wear I've Run")
        capture(host, "rw169-wide-expanded-home.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w1000dp-h400dp-xhdpi")
class Rw169CapWideExpandedCap : Rw169CaptureBase() {
    @Test
    fun `capture wide expanded at the height cap`() {
        val host = homeHost()
        clickDescription(host, "Expand Wear I've Run")
        capture(host, "rw169-wide-expanded-cap.png")
        host.controller.pause().stop().destroy()
    }
}
