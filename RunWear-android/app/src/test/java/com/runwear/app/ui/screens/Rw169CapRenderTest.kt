package com.runwear.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunMorphSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-169 loop-back (Nov 11, 2026): the height-cap state, redone
 * validly. The first lane's cap captures shrank the viewport to
 * 400dp — the reviewer ruled them invalid: no capture showed
 * over-cap content, internal entry scroll, or pinned
 * header/doorway at a real viewport.
 *
 * Here the panel is composed at the REAL viewport classes
 * (folded 390x740dp, wide 1000x800dp) with content tall enough
 * for the RW-152 section 2 cap to actually bind: an
 * accessibility font scale (2.4) plus a long kit — content
 * height is what binds the cap, however produced (diagnosis
 * section 2.4's gate names accessibility scale as an axis).
 * Assertions: rendered panel height == the cap; header,
 * narration and doorway composed and PINNED (bounds unmoved
 * by an internal entry scroll); the entry region scrolls
 * internally; the panel never exceeds the cap.
 */
abstract class Rw169CapBase : Rw169Base() {

    protected fun longLine(items: Int): String {
        val names = listOf(
            "Running Cap", "Short Sleeve Shirt",
            "Running Shorts (5-7\")", "Sunglasses",
            "Light Long Sleeve", "Light Tights",
            "Reflective Gear", "Wool Socks",
            "Thin Gloves", "Neck Gaiter",
            "Arm Warmers", "Compression Calf Sleeves",
            "Visor", "Hydration Vest"
        ).take(items)
        return "Los Angeles, CA · Tomorrow — 66°F feels like · " +
            names.joinToString(" · ")
    }

    protected fun capEntry(photo: String, items: Int): WearIveRunEntry {
        val base = fixtureBarEntry(photo)
        return WearIveRunEntry(
            place = base.place,
            timeLabel = base.timeLabel,
            thumbnailUrl = base.thumbnailUrl,
            feelsLikeF = base.feelsLikeF,
            itemCount = items,
            itemNames = base.itemNames,
            sourceEntry = base.sourceEntry
        )
    }

    @androidx.compose.runtime.Composable
    protected fun CapFixture(
        photo: String,
        items: Int,
        capDp: Float
    ) {
        val day = fixtureDayEntry(photo)
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = 2f, fontScale = 2.4f
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                WearIveRunMorphSurface(
                    narration = WearIveRunDockNarration.line(day, nov11),
                    entry = capEntry(photo, items),
                    entryLine = longLine(items),
                    expanded = true,
                    onExpand = {},
                    onCollapse = {},
                    onDelete = {},
                    onOpenCollection = {},
                    maxExpandedHeightDp = capDp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                )
            }
        }
    }

    protected fun surfaceBounds(host: Host) =
        allNodes(host).firstOrNull {
            it.config.contains(SemanticsProperties.TestTag) &&
                it.config[SemanticsProperties.TestTag] ==
                "rw169MorphSurface"
        }?.boundsInRoot

    protected fun doorwayBox(host: Host) =
        nodeBoxes(host).firstOrNull {
            it.text.contains("Open the collection")
        }

    protected fun wordmarkBox(host: Host) =
        wordmarkNodes(host).minByOrNull {
            (it.right - it.left) * (it.bottom - it.top)
        }

    protected fun assertCapBindsAndPins(
        host: Host,
        capDp: Float,
        label: String
    ) {
        advanceFrames(host, 12)
        settleReveal(host)
        val surface = surfaceBounds(host)
        assertTrue("$label: surface composed", surface != null)
        val capPx = capDp * 2f
        assertTrue(
            "$label: rendered panel height == the cap " +
                "(${surface!!.height} vs ${capPx})",
            kotlin.math.abs(surface.height - capPx) <= 2f
        )
        val wordmark = wordmarkBox(host)
        val doorway = doorwayBox(host)
        assertTrue("$label: header wordmark composed", wordmark != null)
        assertTrue("$label: doorway composed at the foot", doorway != null)
        assertTrue(
            "$label: doorway inside the panel",
            doorway!!.bottom <= surface.bottom + 1f
        )
        // Internal scroll: the entry region's own scroll node
        // takes the scroll; header + doorway + panel height do
        // not move.
        val scroll = allNodes(host).firstOrNull {
            it.config.contains(
                SemanticsProperties.VerticalScrollAxisRange
            ) && it.config.contains(SemanticsActions.ScrollBy)
        }
        assertTrue("$label: entry scroll region present", scroll != null)
        scroll!!.config[SemanticsActions.ScrollBy].action
            ?.invoke(0f, 600f)
        advanceFrames(host, 4)
        settleReveal(host)
        val range = scroll.config[
            SemanticsProperties.VerticalScrollAxisRange
        ]
        assertTrue(
            "$label: the entry region scrolled internally " +
                "(offset ${range.value()})",
            range.value() > 0f
        )
        val wordmarkAfter = wordmarkBox(host)
        val doorwayAfter = doorwayBox(host)
        val surfaceAfter = surfaceBounds(host)
        assertTrue(
            "$label: header pinned during internal scroll",
            wordmarkAfter != null &&
                kotlin.math.abs(wordmarkAfter.top - wordmark!!.top) <= 1f
        )
        assertTrue(
            "$label: doorway pinned during internal scroll",
            doorwayAfter != null &&
                kotlin.math.abs(doorwayAfter.top - doorway!!.top) <= 1f &&
                kotlin.math.abs(doorwayAfter.bottom - doorway.bottom) <= 1f
        )
        assertTrue(
            "$label: panel height still the cap after scroll",
            surfaceAfter != null &&
                kotlin.math.abs(surfaceAfter.height - capPx) <= 2f
        )
    }
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169CapFoldedRenderTest : Rw169CapBase() {

    @Test
    fun `rw169 loop-back - folded cap binds at the real viewport, entry scrolls internally, header and doorway pinned`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val cap = com.runwear.app.ui.screens.WirOverlayPanelPolicy
            .maxPanelHeightDp(740f)
        val host = compose(dark = false) {
            CapFixture(photo, items = 8, capDp = cap)
        }
        assertCapBindsAndPins(host, cap, "folded cap")
        capture(host, "rw169-folded-expanded-cap.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169CapWideRenderTest : Rw169CapBase() {

    @Test
    fun `rw169 loop-back - wide cap binds at the real viewport, entry scrolls internally, header and doorway pinned`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val cap = com.runwear.app.ui.screens.WirOverlayPanelPolicy
            .maxPanelHeightDp(800f)
        val host = compose(dark = false) {
            CapFixture(photo, items = 14, capDp = cap)
        }
        assertCapBindsAndPins(host, cap, "wide cap")
        capture(host, "rw169-wide-expanded-cap.png")
        host.controller.pause().stop().destroy()
    }
}
