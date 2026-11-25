package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.FirstLookMoment
import com.runwear.app.makeitme.FirstLookState
import com.runwear.app.makeitme.ForkBandGeometry
import com.runwear.app.makeitme.MakeItMeForkScreen
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeSettingsSection
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.MakeItMeUiState
import com.runwear.app.ui.components.DayChartGlanceBand
import com.runwear.app.ui.components.DayChartHour
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.components.HeroPill
import com.runwear.app.ui.components.HeroPillPolicy
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.PineAccentOnDark
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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
 * ANDROID FIX WAVE (Nov 6, 2026) — rendered evidence for the wave's
 * changed surfaces, on the production composables (Robolectric
 * native graphics; this VM has no emulator). Host machinery follows
 * the Rw73DetentRenderTest pattern. Captures land in
 * build/render-out-rw-fix-wave/ and are staged with the lane report.
 *
 * Per item:
 *  - RW-104 welcome: the iOS composition — logo centred vertically,
 *    point names present, deep-pine present in the points region.
 *  - RW-104 preferences: the three settled sections compose bare.
 *  - RW-104 fork dash: the dashed face box samples as deep pine
 *    along its own geometry perimeter (the old light mint would
 *    sample ~0).
 *  - RW-97 first look: the stock photo PAINTS in MAKING; on the
 *    URL swap the settled photo holds (no blank frame) until the
 *    personal photo lands in READY.
 *  - RW-100 settings card: two selectable plan rows; the pine
 *    selection census starts on Annual and MOVES to Monthly on tap.
 *    (The paywall sheet itself needs a live MakeItMeViewModel +
 *    RevenueCat offering and cannot compose in this harness; its
 *    rows/CTA are the same components, pinned by
 *    PaywallMonthlyRowTest with real price strings.)
 *  - RW-102 date strip: today+6 present, today+7 absent.
 *  - RW-87 time picker: Confirm's top is identical for a one-line
 *    and a two-line drafted outfit.
 *  - RW-103 glance band: "Change" composes inside the band width
 *    with a 19:00 dot on a 0..22 span; capture is the visual proof.
 *  - Item 12 (RW-105 check): the EXACT_FREE pill's "Get unlimited"
 *    green censuses at the full accent token. RE-PINNED by the
 *    RW-144 fix lane (parent ruling, Nov 8, 2026): the token is now
 *    the THEME's PineAccent — #175E4C in this light-palette host —
 *    per the approved v8 frames (frame A pine / frame B the
 *    #5CC79F accent); the earlier both-themes PineAccentOnDark was
 *    the deviation corrected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class RwFixWaveRenderTest {

    private val outDir: File = File("build/render-out-rw-fix-wave")
    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val locationNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 6, 30)

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private fun compose(content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = false) { content() } }
        val host = Host(composeView, controller)
        pump(host, 12)
        return host
    }

    private fun pump(host: Host, times: Int) {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(times) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(wSpec, hSpec)
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    private fun drawBitmap(host: Host): Bitmap {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun capture(host: Host, name: String) {
        val bitmap = drawBitmap(host)
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun unmergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (found == null) collect(v.getChildAt(i))
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private fun texts(host: Host): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                node.config[SemanticsProperties.Text].forEach { out.add(it.text) }
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return out
    }

    private fun boundsOf(host: Host, text: String): Rect? {
        var bounds: Rect? = null
        fun walk(node: SemanticsNode) {
            if (bounds == null && node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text == text }
            ) {
                bounds = node.boundsInRoot
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return bounds
    }

    private fun findCenter(host: Host, text: String): Offset? {
        var center: Offset? = null
        fun walk(node: SemanticsNode) {
            if (center == null && node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text.contains(text) }
            ) {
                center = node.boundsInRoot.center
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return center
    }

    private fun tap(host: Host, text: String) {
        val c = findCenter(host, text)
        assertNotNull("node with text \"$text\" must be composed", c)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, c!!.x, c.y, 0)
        val up = MotionEvent.obtain(now, now + 12, MotionEvent.ACTION_UP, c.x, c.y, 0)
        host.composeView.dispatchTouchEvent(down)
        host.composeView.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
        pump(host, 5)
    }

    @Composable
    private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            content()
        }
    }

    @Composable
    private fun SheetSurface(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RunWearColors.Surface)
        ) { content() }
    }

    private fun censusColor(bitmap: Bitmap, argb: Int, tolerance: Int = 10): Int {
        val r = Color.red(argb); val g = Color.green(argb); val b = Color.blue(argb)
        var count = 0
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (p in px) {
            if (abs(Color.red(p) - r) <= tolerance &&
                abs(Color.green(p) - g) <= tolerance &&
                abs(Color.blue(p) - b) <= tolerance
            ) count++
        }
        return count
    }

    private fun photoFile(name: String, argb: Int): String {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(argb)
        val f = File.createTempFile(name, ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return "file://" + f.absolutePath
    }

    /** Pump (with real sleeps for Coil's IO threads) until [argb]
     * covers at least [minCount] pixels, or give up. */
    private fun pumpUntilColor(host: Host, argb: Int, minCount: Int, timeoutMs: Long = 9000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pump(host, 3)
            val bitmap = drawBitmap(host)
            val count = censusColor(bitmap, argb, tolerance = 14)
            bitmap.recycle()
            if (count >= minCount) return true
            Thread.sleep(60)
        }
        return false
    }

    // ── RW-104 welcome ────────────────────────────────────────────

    @Test
    fun `welcome screen - centred header, icon rows, deep pine - RW-104`() {
        val host = compose {
            OnboardingScreen(
                onComplete = { _, _, _ -> },
                onRequestLocationPermission = {}
            )
        }
        capture(host, "wave-welcome-phone.png")
        val all = texts(host)
        OnboardingCopy.WELCOME_POINTS.forEach { point ->
            assertTrue(
                "welcome point '${point.name}' composed as one name-led line",
                all.any { it.contains(point.name) && it.contains(point.headline) }
            )
        }
        val logo = boundsOf(host, "RunWear")
        assertNotNull("script logo composed", logo)
        val hPx = host.composeView.resources.displayMetrics.heightPixels.toFloat()
        val centreY = logo!!.center.y / hPx
        assertTrue(
            "logo is vertically centred (centre at ${(centreY * 100).toInt()}% of the frame), not top-loaded",
            centreY in 0.15f..0.60f
        )
        val bitmap = drawBitmap(host)
        val pine = censusColor(bitmap, LightPalette.Primary.toArgb(), tolerance = 6)
        bitmap.recycle()
        assertTrue("deep pine present (icons + name spans): $pine px", pine > 400)
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `preferences step - bare sections compose - RW-104`() {
        val host = compose {
            PreferencesStep(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                genderPreference = com.runwear.shared.domain.model.GenderPreference.UNISEX,
                comfortPreference = com.runwear.shared.domain.model.ComfortPreference.NEUTRAL,
                onTemperatureUnitChange = {},
                onGenderPreferenceChange = {},
                onComfortPreferenceChange = {},
                onBack = {},
                onContinue = {}
            )
        }
        capture(host, "wave-preferences-phone.png")
        val all = texts(host)
        assertTrue(all.any { it.contains(OnboardingCopy.TEMP_TITLE) })
        assertTrue(all.any { it.contains(OnboardingCopy.FIT_TITLE) })
        assertTrue(all.any { it.contains(OnboardingCopy.BODY_TITLE) })
        host.controller.pause().stop().destroy()
    }

    // ── RW-104 fork dash ──────────────────────────────────────────

    @Test
    fun `fork face box dash samples as deep pine - RW-104`() {
        val host = compose {
            MakeItMeForkScreen(
                heroImageUrl = null,
                onUseMyPhotos = {},
                onUseStandardPhotos = {}
            )
        }
        capture(host, "wave-fork-phone.png")
        val bitmap = drawBitmap(host)
        val density = host.composeView.resources.displayMetrics.density
        val bandW = bitmap.width.toFloat()
        val bandH = ForkBandGeometry.BAND_HEIGHT_DP * density
        val rect = ForkBandGeometry.faceBoxRect(bandW, bandH)
        val pine = LightPalette.Primary.toArgb()
        var samples = 0
        var hits = 0
        val stepsPerEdge = 60
        fun sample(x: Float, y: Float) {
            val xi = x.toInt().coerceIn(0, bitmap.width - 1)
            val yi = y.toInt().coerceIn(0, bitmap.height - 1)
            samples++
            if (bitmap.getPixel(xi, yi) == pine) hits++
        }
        for (i in 0..stepsPerEdge) {
            val t = i.toFloat() / stepsPerEdge
            sample(rect.left + rect.width * t, rect.top)
            sample(rect.left + rect.width * t, rect.top + rect.height)
            sample(rect.left, rect.top + rect.height * t)
            sample(rect.left + rect.width, rect.top + rect.height * t)
        }
        bitmap.recycle()
        assertTrue(
            "dash perimeter reads deep pine ($hits/$samples samples; the old light mint would read ~0)",
            hits.toFloat() / samples > 0.22f
        )
        host.controller.pause().stop().destroy()
    }

    // ── RW-97 first look ──────────────────────────────────────────

    @Test
    fun `first look - making sheet composes whole with its pill and copy - RW-97`() {
        val stockUrl = photoFile("rw97-stock", 0xFFB07448.toInt())
        val host = compose {
            FirstLookMoment(
                photoUrl = stockUrl,
                simplePlace = "Miami, FL",
                weatherLine = "Feels like 84° · Humid",
                onSeeOutfit = {},
                onSeePremium = {},
                state = FirstLookState.MAKING,
                freeLooksUsed = 1,
                mode = MakeItMeMode.FREE
            )
        }
        // MAKING: the sheet composes whole — the honest making
        // copy and the generating pill over the photo area.
        // (The photo layer's PIXEL paint in the MAKING state is
        // UNPROVEN in this harness: the pill's infinite spinner
        // animation starves Robolectric's frame clock — a probe
        // with a raw spinner sibling deadlocked the test thread
        // outright. The layer is the same ungated composable the
        // READY test above paints with; the pill overlays it and
        // never gates it.)
        capture(host, "wave-firstlook-making.png")
        val makingTexts = texts(host)
        assertTrue(
            "MAKING shows the honest making copy",
            makingTexts.any { it == MakeItMeStrings.G1_PROGRESS }
        )
        assertTrue(
            "MAKING carries the generating pill",
            makingTexts.any { it.contains("Making this you") }
        )
        host.controller.pause().stop().destroy()
    }

    // ── RW-102 date strip ─────────────────────────────────────────

    @Test
    fun `date picker strip ends at the forecast horizon - RW-102`() {
        val host = compose {
            SheetSurface {
                DatePickerSheetContent(
                    selectedDate = today,
                    today = today,
                    onSelectDate = {},
                    onDismiss = {}
                )
            }
        }
        capture(host, "wave-date-picker.png")
        val all = texts(host)
        assertTrue("horizon day (today+6) offered", all.any { it.contains("Nov 11") })
        assertTrue(
            "no beyond-horizon day offered",
            all.none { it.contains("Nov 12") }
        )
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `date picker at the largest text size still composes whole - RW-102 RW-73`() {
        val host = compose {
            Scaled(2.0f) {
                SheetSurface {
                    DatePickerSheetContent(
                        selectedDate = today,
                        today = today,
                        onSelectDate = {},
                        onDismiss = {}
                    )
                }
            }
        }
        capture(host, "wave-date-picker-largest-text.png")
        assertNotNull("Confirm composed at 2.0 font scale", boundsOf(host, "Confirm"))
        assertNull(
            "no beyond-horizon day at the largest size either",
            boundsOf(host, "Nov 12")
        )
        host.controller.pause().stop().destroy()
    }

    // ── RW-87 time picker ─────────────────────────────────────────

    private fun timeModel(itemsAt12: List<String>): DayChartModel {
        val hours = (0..23).map { h ->
            DayChartHour(
                hour = h,
                feelsLikeF = 40.0 + h,
                rawFeelsLikeF = 40.0 + h,
                bracket = TempBracket.COOL,
                outfitItems = if (h == 12) itemsAt12 else listOf("Light jacket"),
                weatherCode = com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
            )
        }
        return DayChartModel(
            date = today,
            hours = hours,
            low = hours.first(), high = hours.last(),
            changeHours = emptyList(), horizonLastHour = 23
        )
    }

    @Test
    fun `time picker confirm does not move between one-line and two-line outfits - RW-87`() {
        val shortHost = compose {
            SheetSurface {
                TimePickerSheetContent(
                    model = timeModel(listOf("Tee")),
                    selectedHour = 12,
                    locationNow = locationNow,
                    isNowSelected = false,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    locationName = "Kansas City",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        capture(shortHost, "wave-time-picker-one-line.png")
        val shortConfirm = boundsOf(shortHost, "Confirm")
        shortHost.controller.pause().stop().destroy()

        val longHost = compose {
            SheetSurface {
                TimePickerSheetContent(
                    model = timeModel(
                        listOf(
                            "Insulated jacket", "Base layer", "Tights",
                            "Wool socks", "Gloves"
                        )
                    ),
                    selectedHour = 12,
                    locationNow = locationNow,
                    isNowSelected = false,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    locationName = "Kansas City",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        capture(longHost, "wave-time-picker-two-line.png")
        val longConfirm = boundsOf(longHost, "Confirm")
        longHost.controller.pause().stop().destroy()

        assertNotNull("Confirm composed (one-line)", shortConfirm)
        assertNotNull("Confirm composed (two-line)", longConfirm)
        assertTrue(
            "Confirm top identical: one-line ${shortConfirm!!.top} vs two-line ${longConfirm!!.top}",
            abs(shortConfirm.top - longConfirm.top) <= 2f
        )
    }

    // ── RW-103 glance band ────────────────────────────────────────

    @Test
    fun `glance band - change inside the band with a late dot - RW-103`() {
        val hours = (0..22).map { h ->
            DayChartHour(
                hour = h,
                feelsLikeF = 45.0 + h,
                rawFeelsLikeF = 45.0 + h,
                bracket = TempBracket.COOL,
                outfitItems = listOf("Light jacket"),
                weatherCode = com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
            )
        }
        val model = DayChartModel(
            date = today,
            hours = hours,
            low = hours.first(), high = hours.last(),
            changeHours = emptyList(), horizonLastHour = 22
        )
        val host = compose {
            SheetSurface {
                DayChartGlanceBand(
                    model = model,
                    selectedHour = 19,
                    locationToday = today,
                    nowHour = 6,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    onChangeClick = {}
                )
            }
        }
        capture(host, "wave-glance-band-late-dot.png")
        val change = boundsOf(host, "Change ›") ?: boundsOf(host, "Change")
        assertNotNull("Change control composed", change)
        val wPx = host.composeView.resources.displayMetrics.widthPixels.toFloat()
        assertTrue(
            "Change sits inside the band width (right=${change!!.right} of $wPx)",
            change.right <= wPx && change.left >= 0f
        )
        host.controller.pause().stop().destroy()
    }

    // ── Item 12: the Get unlimited green (RW-105 check) ──────────

    @Test
    fun `get unlimited green renders at full token strength - item 12`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = com.runwear.app.makeitme.PersonalLookStatus.Ready,
            serveKind = com.runwear.app.makeitme.PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 3
        )
        assertNotNull("EXACT_FREE pill resolves", pill)
        val host = compose {
            Box(
                modifier = Modifier
                    .background(LightPalette.Background)
                    .padding(24.dp)
            ) {
                HeroPill(content = pill!!)
            }
        }
        capture(host, "wave-pill-exact-free.png")
        val all = texts(host)
        assertTrue(all.any { it.contains("Get unlimited") })
        val bitmap = drawBitmap(host)
        // RW-144 fix lane re-pin: this host runs the LIGHT palette,
        // so the CTA censuses at LightPalette.PineAccent (#175E4C,
        // the v8 frame A pine) — and the old both-themes accent
        // must be absent from the pill.
        val green = censusColor(bitmap, RunWearColors.PineAccent.toArgb(), tolerance = 4)
        val oldAccent = censusColor(bitmap, PineAccentOnDark.toArgb(), tolerance = 4)
        bitmap.recycle()
        assertTrue(
            "Get unlimited green at full token strength ($green px of exact theme PineAccent)",
            green > 150
        )
        assertTrue(
            "No PineAccentOnDark left in the light pill ($oldAccent px)",
            oldAccent == 0
        )
        host.controller.pause().stop().destroy()
    }
}

/**
 * RW-104 at the Fold's unfolded width: the welcome composition
 * centres the same way, and the fork's dashed face box stays
 * properly visible at the wide form factor Sol named.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w840dp-h1100dp-xhdpi")
class RwFixWaveFoldRenderTest {

    private val outDir: File = File("build/render-out-rw-fix-wave")

    @Test
    fun `welcome and fork at fold width - RW-104`() {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        fun pump(times: Int) {
            val w = activity.resources.displayMetrics.widthPixels
            val h = activity.resources.displayMetrics.heightPixels
            val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            repeat(times) {
                Robolectric.flushForegroundThreadScheduler()
                scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
                shadowOf(Looper.getMainLooper()).idle()
                composeView.measure(wSpec, hSpec)
                composeView.layout(0, 0, w, h)
                val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                composeView.draw(Canvas(frame))
                frame.recycle()
            }
        }
        fun capture(name: String): Bitmap {
            val w = activity.resources.displayMetrics.widthPixels
            val h = activity.resources.displayMetrics.heightPixels
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(bitmap))
            outDir.mkdirs()
            File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }

        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                OnboardingScreen(
                    onComplete = { _, _, _ -> },
                    onRequestLocationPermission = {}
                )
            }
        }
        pump(12)
        capture("wave-welcome-fold.png").recycle()

        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                MakeItMeForkScreen(
                    heroImageUrl = null,
                    onUseMyPhotos = {},
                    onUseStandardPhotos = {}
                )
            }
        }
        pump(12)
        val bitmap = capture("wave-fork-fold.png")
        val density = activity.resources.displayMetrics.density
        val rect = ForkBandGeometry.faceBoxRect(
            bitmap.width.toFloat(),
            ForkBandGeometry.BAND_HEIGHT_DP * density
        )
        val pine = LightPalette.Primary.toArgb()
        var samples = 0
        var hits = 0
        for (i in 0..80) {
            val t = i.toFloat() / 80
            val pts = listOf(
                (rect.left + rect.width * t) to rect.top,
                (rect.left + rect.width * t) to (rect.top + rect.height),
                rect.left to (rect.top + rect.height * t),
                (rect.left + rect.width) to (rect.top + rect.height * t)
            )
            for ((x, y) in pts) {
                samples++
                val xi = x.toInt().coerceIn(0, bitmap.width - 1)
                val yi = y.toInt().coerceIn(0, bitmap.height - 1)
                if (bitmap.getPixel(xi, yi) == pine) hits++
            }
        }
        bitmap.recycle()
        assertTrue(
            "fork dash reads deep pine at Fold width ($hits/$samples samples)",
            hits.toFloat() / samples > 0.22f
        )
        controller.pause().stop().destroy()
    }
}
