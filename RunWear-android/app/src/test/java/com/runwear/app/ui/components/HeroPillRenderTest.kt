package com.runwear.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 WAVE A, ITEM 7 (RW-34) — state-sequence RENDERS of every
 * approved pill state, through the ONE HeroPillPolicy and the
 * production HeroPill composable (Robolectric native graphics, the
 * repo's established render pipeline). PNGs land in
 * build/render-out-gen6-pill/ and are copied to the wave evidence
 * directory. The approved table (owner, Nov 5):
 *
 *  - Generating: script + spinner + "Making this you…" + the green
 *    "Get it instantly next time" on FREE only.
 *  - Free exact: script + "— N of 5 looks · Get unlimited".
 *  - Exhausted: script + used-up count + "Get unlimited" as the
 *    main message, whole pill tappable.
 *  - Premium exact: script + "Premium" + lock, nothing else.
 *  - Scaffold: SILENT — the policy resolves null; nothing renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h300dp-xhdpi")
class HeroPillRenderTest {

    private val outDir: File =
        File(System.getProperty("gen6PillShotsDir") ?: "build/render-out-gen6-pill")

    private fun renderPill(name: String, content: HeroPillContent): List<String> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                // A photo-like dark ground, as on the hero.
                Box(
                    modifier = Modifier
                        .background(Color(0xFF33413A))
                        .padding(16.dp)
                ) {
                    HeroPill(content = content, onAction = {})
                }
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return texts(mergedRoot(composeView))
    }

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (found == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun texts(root: SemanticsNode): List<String> =
        nodes(root).flatMap { node ->
            val out = mutableListOf<String>()
            if (node.config.contains(SemanticsProperties.Text)) {
                out += node.config[SemanticsProperties.Text].map { it.text }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                out += node.config[SemanticsProperties.ContentDescription]
            }
            out
        }

    private fun resolve(
        lookStatus: PersonalLookStatus = PersonalLookStatus.Ready,
        serveKind: PersonalServeKind = PersonalServeKind.EXACT,
        heroIsPersonal: Boolean = true,
        mode: MakeItMeMode = MakeItMeMode.FREE,
        used: Int = 0
    ): HeroPillContent? = HeroPillPolicy.resolve(
        lookStatus = lookStatus,
        serveKind = serveKind,
        heroIsPersonal = heroIsPersonal,
        mode = mode,
        freeGenerationsUsed = used
    )

    @Test
    fun `render - generating on free carries the script, the making line and the green C-4 line`() {
        val pill = resolve(lookStatus = PersonalLookStatus.Making, heroIsPersonal = false)
        assertEquals(HeroPillKind.MAKING, pill?.kind)
        val texts = renderPill("pill-making-free", pill!!)
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_SCRIPT_MARK) })
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_MAKING) })
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_MAKING_UPSELL) })
    }

    @Test
    fun `render - generating on premium carries NO upsell`() {
        val pill = resolve(
            lookStatus = PersonalLookStatus.Making,
            heroIsPersonal = false,
            mode = MakeItMeMode.SUBSCRIBED
        )
        assertEquals(HeroPillKind.MAKING, pill?.kind)
        val texts = renderPill("pill-making-premium", pill!!)
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_MAKING) })
        assertTrue(texts.none { it.contains("Get it instantly") })
        assertTrue(texts.none { it.contains("Get unlimited") })
    }

    @Test
    fun `render - free exact serve shows the look count and Get unlimited`() {
        val pill = resolve(used = 3)
        assertEquals(HeroPillKind.EXACT_FREE, pill?.kind)
        val texts = renderPill("pill-exact-free-3-of-5", pill!!)
        assertTrue(texts.any { it.contains("3 of 5 looks") })
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_GET_UNLIMITED) })
    }

    @Test
    fun `render - exhausted shows the used-up count with Get unlimited as the main message`() {
        val pill = resolve(used = 5)
        assertEquals(HeroPillKind.EXHAUSTED, pill?.kind)
        assertTrue(pill?.wholePillAction == true)
        val texts = renderPill("pill-exhausted", pill!!)
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT) })
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_GET_UNLIMITED) })
    }

    @Test
    fun `render - premium exact serve is script plus Premium plus lock and nothing else`() {
        val pill = resolve(mode = MakeItMeMode.SUBSCRIBED, used = 0)
        assertEquals(HeroPillKind.PREMIUM, pill?.kind)
        assertTrue(pill?.showLock == true)
        val texts = renderPill("pill-premium", pill!!)
        assertTrue(texts.any { it.contains(MakeItMeStrings.HERO_PILL_SCRIPT_MARK) })
        assertTrue(texts.any { it == MakeItMeStrings.HERO_PILL_PREMIUM })
        assertTrue(texts.none { it.contains("of 5 looks") })
        assertTrue(texts.none { it.contains("ready before you open") })
        assertTrue(texts.none { it.contains("Get unlimited") })
    }

    @Test
    fun `scaffold serve resolves to silence - no pill renders`() {
        // The removed "Your photo" label: the policy returns null
        // for both tiers, so there is nothing to render.
        assertNull(resolve(serveKind = PersonalServeKind.SCAFFOLD, used = 1))
        assertNull(
            resolve(
                serveKind = PersonalServeKind.SCAFFOLD,
                mode = MakeItMeMode.SUBSCRIBED
            )
        )
    }

    @Test
    fun `no hero state is left pill-less during a generation window`() {
        // The approved table's closing rule: while a look is
        // generating for the cell in view, SOME pill always
        // resolves — over stock, scaffold, or exact serves alike.
        for (kind in PersonalServeKind.values()) {
            for (mode in MakeItMeMode.values()) {
                val pill = resolve(
                    lookStatus = PersonalLookStatus.Making,
                    serveKind = kind,
                    heroIsPersonal = kind != PersonalServeKind.STOCK_QUIET,
                    mode = mode,
                    used = 2
                )
                assertTrue(
                    "MAKING must resolve a pill for $kind/$mode",
                    pill != null && pill.kind == HeroPillKind.MAKING
                )
            }
        }
    }
}
