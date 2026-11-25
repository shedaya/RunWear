package com.runwear.app.makeitme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * GEN-2 A1 (W-2, P0) — the Make It Me consent step, exercised end
 * to end on the PRODUCTION upload flow (the seam-carrying core
 * overload of [MakeItMeUploadFlow]; the view-model overload used
 * in the app delegates to it unchanged).
 *
 * The field defect (Sol's Nov 4 recording, both modes): the
 * consent step's title rendered ON TOP of its first paragraph,
 * the attestation was buried under the overlap, and NO forward
 * control existed — Make It Me was unreachable. Mechanism: the
 * step branches inside the flow's AnimatedContent emitted their
 * layout as top-level siblings, and AnimatedContent's transition
 * layout places every root child at the same origin. The fix
 * gives each branch exactly one root (a Column).
 *
 * These tests assert, on the rendered semantics geometry (not
 * source): title, body, attestation, Continue and Not now stack
 * in order with no overlap, in light AND dark; Continue is
 * disabled until the attestation is ticked; Continue then leads
 * into the photo-upload step (the iOS flow's shape: consent →
 * capture); Not now exits; and (F-2) system Back inside the
 * upload flow steps back to consent, and Back at consent exits
 * the flow — neither press finishes the activity.
 *
 * PNGs land in build/render-out-gen2/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class ConsentSheetScenarioTest {

    private val outDir: File =
        File(System.getProperty("gen2ShotsDir") ?: "build/render-out-gen2")

    private class Host(
        val activity: ComponentActivity,
        val composeView: ComposeView,
        val exited: AtomicBoolean
    )

    private fun composeFlow(
        dark: Boolean,
        recordConsent: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(true) }
    ): Host {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            if (dark) com.runwear.app.ui.theme.DarkPalette
            else com.runwear.app.ui.theme.LightPalette
        )
        val exited = AtomicBoolean(false)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = dark) {
                MakeItMeUploadFlow(
                    recordConsent = recordConsent,
                    submitPhotos = { _, _, _, _ -> },
                    gender = "UNISEX",
                    replace = false,
                    todaysCell = null,
                    onFinished = {},
                    onExitAsStock = { exited.set(true) }
                )
            }
        }
        settle(composeView)
        return Host(activity, composeView, exited)
    }

    private fun settle(composeView: ComposeView) {
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
    }

    private fun rootOf(view: View, unmerged: Boolean): SemanticsNode {
        var found: ViewRootForTest? = null
        fun collect(v: View) {
            if (found == null && v is ViewRootForTest) found = v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (found == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(view.rootView)
        val owner = (found ?: throw IllegalStateException("no ViewRootForTest"))
            .semanticsOwner
        return if (unmerged) owner.unmergedRootSemanticsNode else owner.rootSemanticsNode
    }

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun texts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        if (node.config.contains(SemanticsProperties.Text)) {
            out += node.config[SemanticsProperties.Text].map { it.text }
        }
        return out
    }

    private fun textNode(root: SemanticsNode, text: String): SemanticsNode? =
        nodes(root).firstOrNull { texts(it).any { t -> t == text } }

    /** The nearest node at or above [node] carrying a click action. */
    private fun clickableFrom(node: SemanticsNode): SemanticsNode? {
        var current: SemanticsNode? = node
        while (current != null) {
            if (current.config.contains(SemanticsActions.OnClick)) return current
            current = current.parent
        }
        return null
    }

    private fun clickText(root: SemanticsNode, text: String): Boolean {
        val node = textNode(root, text) ?: return false
        val clickable = clickableFrom(node) ?: return false
        return clickable.config[SemanticsActions.OnClick].action?.invoke() == true
    }

    private fun capturePng(composeView: ComposeView, name: String) {
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * The W-2 geometry claim: the consent step's elements stack in
     * reading order, pairwise non-overlapping, all inside the
     * viewport — the exact negation of the field frame (title over
     * paragraph, attestation buried, no Continue anywhere).
     */
    private fun assertConsentLayout(host: Host, label: String) {
        val root = rootOf(host.composeView, unmerged = true)
        val title = textNode(root, MakeItMeStrings.C1A_CONSENT_TITLE)
        val body = textNode(root, MakeItMeStrings.C1B_CONSENT_BODY)
        val attestation = textNode(root, MakeItMeStrings.C1C_ATTESTATION)
        val continueText = textNode(root, MakeItMeStrings.C1D_CONTINUE)
        val notNow = textNode(root, MakeItMeStrings.C1E_NOT_NOW)
        assertNotNull("[$label] consent title must be composed", title)
        assertNotNull("[$label] consent body must be composed", body)
        assertNotNull("[$label] attestation must be composed", attestation)
        assertNotNull("[$label] Continue must be composed (it was unreachable in the field)", continueText)
        assertNotNull("[$label] Not now must be composed", notNow)
        val continueButton = clickableFrom(continueText!!)
        assertNotNull("[$label] Continue must sit inside a clickable control", continueButton)

        val viewportBottom = root.boundsInRoot.bottom
        fun stacked(upper: SemanticsNode, lower: SemanticsNode, what: String) {
            assertTrue(
                "[$label] $what must not overlap: upper bottom ${upper.boundsInRoot.bottom} " +
                    "vs lower top ${lower.boundsInRoot.top}",
                upper.boundsInRoot.bottom <= lower.boundsInRoot.top + 1f
            )
        }
        val titleN = title!!
        val bodyN = body!!
        val attestationN = attestation!!
        val continueN = continueButton!!
        stacked(titleN, bodyN, "title over body (the W-2 overlap)")
        stacked(bodyN, attestationN, "body over attestation")
        stacked(attestationN, continueN, "attestation over Continue")
        assertTrue(
            "[$label] Continue must be fully inside the viewport " +
                "(bottom ${continueN.boundsInRoot.bottom} of $viewportBottom)",
            continueN.boundsInRoot.bottom <= viewportBottom + 1f
        )
        assertTrue(
            "[$label] Not now must be fully inside the viewport " +
                "(bottom ${notNow!!.boundsInRoot.bottom} of $viewportBottom)",
            notNow.boundsInRoot.bottom <= viewportBottom + 1f
        )
    }

    @Test
    fun `consent step renders stacked without overlap - light`() {
        val host = composeFlow(dark = false)
        assertConsentLayout(host, "light")
        capturePng(host.composeView, "consent-light")
    }

    @Test
    fun `consent step renders stacked without overlap - dark`() {
        val host = composeFlow(dark = true)
        assertConsentLayout(host, "dark")
        capturePng(host.composeView, "consent-dark")
    }

    @Test
    fun `Continue is gated by the attestation, then leads into the upload step - and Back walks the flow (F-2)`() {
        val host = composeFlow(dark = false)
        val activity = host.activity

        // Gate: Continue starts disabled (merged tree carries the
        // Disabled property on the button node).
        fun continueDisabled(): Boolean {
            val merged = rootOf(host.composeView, unmerged = false)
            val button = nodes(merged).firstOrNull {
                it.config.contains(SemanticsActions.OnClick) &&
                    texts(it).any { t -> t == MakeItMeStrings.C1D_CONTINUE }
            }
            assertNotNull("Continue button must exist in the merged tree", button)
            return button!!.config.contains(SemanticsProperties.Disabled)
        }
        assertTrue("Continue must be disabled before the attestation", continueDisabled())

        // Tick the attestation through its row's real click action.
        val root = rootOf(host.composeView, unmerged = true)
        assertTrue(
            "the attestation row must be clickable",
            clickText(root, MakeItMeStrings.C1C_ATTESTATION)
        )
        settle(host.composeView)
        assertFalse("Continue must enable once the attestation is ticked", continueDisabled())

        // Continue -> the upload (capture) step, exactly as iOS:
        // consent's Continue is the only door into photo UI.
        val root2 = rootOf(host.composeView, unmerged = true)
        assertTrue(
            "Continue must be clickable once enabled",
            clickText(root2, MakeItMeStrings.C1D_CONTINUE)
        )
        settle(host.composeView)
        val afterContinue = rootOf(host.composeView, unmerged = true)
        assertNotNull(
            "Continue must land on the upload step",
            textNode(afterContinue, MakeItMeStrings.C2A_CAPTURE_TITLE)
        )
        assertNull(
            "the consent step must be gone once the upload step is up",
            textNode(afterContinue, MakeItMeStrings.C1A_CONSENT_TITLE)
        )
        // The upload step's own layout is stacked too (same fix):
        // its title sits above its guidance, not on top of it.
        val captureTitle = textNode(afterContinue, MakeItMeStrings.C2A_CAPTURE_TITLE)!!
        val captureGuidance = textNode(afterContinue, MakeItMeStrings.C2B_CAPTURE_GUIDANCE)
        assertNotNull("upload guidance must be composed", captureGuidance)
        assertTrue(
            "upload title must not overlap its guidance",
            captureTitle.boundsInRoot.bottom <= captureGuidance!!.boundsInRoot.top + 1f
        )

        // F-2: system Back inside the upload flow steps back to
        // consent — it does NOT close the app.
        activity.onBackPressedDispatcher.onBackPressed()
        settle(host.composeView)
        val afterBack = rootOf(host.composeView, unmerged = true)
        assertNotNull(
            "Back from the upload step must return to consent",
            textNode(afterBack, MakeItMeStrings.C1A_CONSENT_TITLE)
        )
        assertFalse("Back inside the flow must not exit it yet", host.exited.get())
        assertFalse(
            "Back inside the flow must NOT finish the activity (the F-2 trap)",
            activity.isFinishing
        )

        // Back at consent (the flow's root) exits the flow through
        // its own callback — still never finishing the activity.
        activity.onBackPressedDispatcher.onBackPressed()
        settle(host.composeView)
        assertTrue("Back at consent must exit the flow", host.exited.get())
        assertFalse(
            "Back at consent must NOT finish the activity",
            activity.isFinishing
        )
    }

    @Test
    fun `Not now dismisses the consent step - the quiet exit, no dead end`() {
        val host = composeFlow(dark = false)
        val root = rootOf(host.composeView, unmerged = true)
        assertTrue(
            "Not now must be clickable",
            clickText(root, MakeItMeStrings.C1E_NOT_NOW)
        )
        settle(host.composeView)
        assertTrue("Not now must exit the flow", host.exited.get())
        assertFalse(
            "Not now must NOT finish the activity",
            host.activity.isFinishing
        )
    }
}
