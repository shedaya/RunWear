package com.runwear.app.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-168 (Nov 11, 2026): the home share button beside the gear
 * and its menu of existing entries. Full-screen frames: the
 * button composes in the hero chrome; the menu opens with
 * exactly the three doors (frames in light AND dark); the
 * collection door navigates into the Wear I've Run collection;
 * the outfit door presents the RW-158 sheet (its Dialog window
 * is the observable). The menu card's own wiring is pinned
 * directly, and the direct save's render pipeline is pinned in
 * [Rw168SaveRenderPipelineTest].
 */
abstract class Rw168Base : Rw156Base() {

    /** Click the node whose merged semantics carry [label] as
     * text and an OnClick action (the harness idiom, modelled
     * on Rw156Base.clickDescription). */
    protected fun clickText(host: Host, label: String) {
        clickSemanticsText(rootNode(host.composeView), label)
        settleReveal(host)
    }

    /** Invoke the OnClick of the node carrying [label] as text —
     * the same node when semantics are merged, else the
     * clickable node whose bounds contain the text's centre. */
    protected fun clickSemanticsText(root: SemanticsNode, label: String) {
        val all = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            all += node
            node.children.forEach { collect(it) }
        }
        collect(root)
        val textNode = all.firstOrNull { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text]
                    .any { it.text == label }
        } ?: throw AssertionError("no text node '$label'")
        val direct = if (
            textNode.config.contains(SemanticsActions.OnClick)
        ) {
            textNode
        } else {
            val centre = textNode.boundsInRoot.center
            all.filter {
                it.config.contains(SemanticsActions.OnClick)
            }.minByOrNull {
                val b = it.boundsInRoot
                if (
                    centre.x >= b.left && centre.x <= b.right &&
                    centre.y >= b.top && centre.y <= b.bottom
                ) {
                    0f
                } else {
                    Float.MAX_VALUE
                }
            }
        }
        val node = direct
            ?: throw AssertionError("no clickable node for '$label'")
        node.config[SemanticsActions.OnClick].action?.invoke()
            ?: throw AssertionError("OnClick missing for '$label'")
    }

    protected fun openMenu(dark: Boolean): Host {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = dark) { Home(state, exhaustedMim()) }
        clickDescription(host, "Share")
        return host
    }

    protected fun menuLabels(host: Host): List<String> =
        texts(host).map { it.text }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168MenuLightRenderTest : Rw168Base() {

    @Test
    fun `rw168 menu opens with the three existing doors - light`() {
        val host = openMenu(dark = false)
        val labels = menuLabels(host)
        assertTrue("Share this outfit", "Share this outfit" in labels)
        assertTrue("Share from collection", "Share from collection" in labels)
        assertTrue("Save to photos", "Save to photos" in labels)
        capture(host, "rw168-menu-light.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168MenuDarkRenderTest : Rw168Base() {

    @Test
    fun `rw168 menu opens with the three existing doors - dark`() {
        val host = openMenu(dark = true)
        val labels = menuLabels(host)
        assertTrue("Share this outfit", "Share this outfit" in labels)
        assertTrue("Share from collection", "Share from collection" in labels)
        assertTrue("Save to photos", "Save to photos" in labels)
        capture(host, "rw168-menu-dark.png")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168CollectionDoorRenderTest : Rw168Base() {

    @Test
    fun `rw168 collection door navigates into the collection`() {
        val host = openMenu(dark = false)
        clickText(host, "Share from collection")
        val labels = menuLabels(host)
        assertTrue(
            "collection wordmark composed",
            labels.any { it.contains("Wear I've Run") }
        )
        assertTrue(
            "menu closed after the door",
            "Share this outfit" !in labels
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168OutfitDoorRenderTest : Rw168Base() {

    @Test
    fun `rw168 outfit door presents the share sheet dialog`() {
        val host = openMenu(dark = false)
        clickText(host, "Share this outfit")
        settleReveal(host)
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertTrue(
            "the RW-158 sheet's dialog is showing",
            dialog != null && dialog.isShowing
        )
        host.controller.pause().stop().destroy()
    }
}

/**
 * RW-168: the menu card's wiring, pinned directly — each row
 * fires exactly its own door, entry-bound rows rest disabled
 * without an entry, and every row dismisses first.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168MenuCardWiringTest : Rw168Base() {

    @Test
    fun `rw168 menu rows fire their own doors`() {
        val fired = mutableListOf<String>()
        var dismissed = 0
        val actions = com.runwear.app.ui.components.HeroShareActions(
            onShareOutfit = { fired += "outfit" },
            onShareFromCollection = { fired += "collection" },
            onSaveToPhotos = { fired += "save" },
            outfitEnabled = true
        )
        val host = compose(dark = false) {
            com.runwear.app.ui.components.HeroShareMenuCard(
                actions = actions,
                onDismiss = { dismissed++ }
            )
        }
        fun clickRow(label: String) {
            clickSemanticsText(rootNode(host.composeView), label)
            settleReveal(host)
        }
        clickRow("Share this outfit")
        clickRow("Share from collection")
        clickRow("Save to photos")
        assertEquals(listOf("outfit", "collection", "save"), fired)
        assertEquals(3, dismissed)
        host.controller.pause().stop().destroy()
    }
}

/**
 * RW-168: the direct save's render pipeline — a verified card
 * renders headlessly for both kinds from the sheet's own
 * primitives, and the Postcard's hero hold applies (a referenced
 * hero that cannot load yields no card rather than a
 * photo-less rectangle). The pipeline suspends onto the main
 * dispatcher (the production renderer does), so calls run on a
 * worker thread while the test thread pumps Robolectric's main
 * scheduler — the Gen6WaveBRenderTests harness pattern.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw168SaveRenderPipelineTest : Rw156Base() {

    private fun renderPumped(
        host: Host,
        entry: com.runwear.app.weariverun.WearIveRunDayEntry,
        kind: com.runwear.app.weariverun.ShareArtifactKind
    ): android.graphics.Bitmap? {
        var result: android.graphics.Bitmap? = null
        var failure: Throwable? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        val thread = Thread {
            try {
                result = kotlinx.coroutines.runBlocking {
                    com.runwear.app.weariverun.renderVerifiedShareCard(
                        context = host.activity,
                        entry = entry,
                        kind = kind,
                        format = com.runwear.app.weariverun
                            .ShareExportFormat.FEED,
                        gender = com.runwear.shared.domain.model
                            .GenderPreference.UNISEX,
                        temperatureUnit = com.runwear.shared.domain.model
                            .TemperatureUnit.FAHRENHEIT
                    )
                }
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 90_000
        while (!latch.await(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            org.robolectric.Robolectric.flushForegroundThreadScheduler()
            org.robolectric.Shadows.shadowOf(
                android.os.Looper.getMainLooper()
            ).idle()
            if (System.currentTimeMillis() > deadline) {
                org.junit.Assert.fail("direct render timed out ($kind)")
            }
        }
        failure?.let {
            throw AssertionError("direct render failed ($kind)", it)
        }
        return result
    }

    private fun hostWithEntry(): Pair<Host, com.runwear.app.weariverun.WearIveRunDayEntry> {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val outfit = state.outfit!!
        val weather = state.weather!!
        val entry = com.runwear.app.weariverun.WearIveRunDayEntry(
            date = state.selectedDateTime.toLocalDate(),
            place = "New York, NY",
            feelsLikeF = weather.feelsLike,
            conditionLabel = weather.weatherCode.description,
            isSnowing = false,
            isRaining = false,
            items = outfit.allItems.map { it.displayName },
            itemKeys = outfit.allItems.map { it.key },
            heroRef = photo
        )
        return host to entry
    }

    @Test
    fun `rw168 direct render produces verified cards for both kinds`() {
        val (host, entry) = hostWithEntry()
        val poster = renderPumped(
            host, entry,
            com.runwear.app.weariverun.ShareArtifactKind.MORNING_POSTER
        )
        assertNotNull("postcard renders + verifies headlessly", poster)
        assertEquals(
            com.runwear.app.weariverun.ShareExportFormat.FEED
                .exportPixelSize.width,
            poster!!.width
        )
        assertEquals(
            com.runwear.app.weariverun.ShareExportFormat.FEED
                .exportPixelSize.height,
            poster.height
        )
        val kit = renderPumped(
            host, entry,
            com.runwear.app.weariverun.ShareArtifactKind.KIT_CARD
        )
        assertNotNull("kit card renders + verifies headlessly", kit)
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `rw168 postcard hero hold - unloadable referenced hero yields no card`() {
        val (host, entry) = hostWithEntry()
        val broken = entry.copy(
            heroRef = "file:///nonexistent/definitely-missing-hero.png"
        )
        val poster = renderPumped(
            host, broken,
            com.runwear.app.weariverun.ShareArtifactKind.MORNING_POSTER
        )
        assertNull(
            "hero hold: no card when the referenced hero cannot load",
            poster
        )
        host.controller.pause().stop().destroy()
    }
}
