package com.runwear.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.wear.presentation.viewmodel.WearUiState
import com.runwear.wear.presentation.screens.HeroWearMainScreen
import com.runwear.wear.presentation.theme.RunWearWatchTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 WAVE C (DA-13) render proof: the Wear hero at 384×384 and
 * 456×456 with a weather fixture whose ACTUAL (52°) differs from
 * its FEELS-LIKE (44°) — the gen-5 defect shape. The headline
 * numeral must be the feels-like figure and the subordinate line
 * must read "Actual 52°"; before the fix both lines carried 52°.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class WearTempFrameRenderTest {

    private val outDir: File =
        File(System.getProperty("da13ShotsDir") ?: "build/render-out-da13")

    private fun shoot(name: String, sizePx: Int): List<String> {
        val weather = WeatherConditions(
            dateTime = LocalDateTime(2026, 11, 5, 7, 0),
            temperature = 52.0, feelsLike = 44.0, humidity = 55,
            windSpeed = 8.0, windGusts = 12.0, precipitationProbability = 10,
            precipitation = 0.0, isRaining = false, isSnowing = false,
            cloudCover = 30, uvIndex = 2.0, weatherCode = WeatherCode.PARTLY_CLOUDY
        )
        val outfit = GetOutfitRecommendationUseCase()
            .execute(weather, ComfortPreference.NEUTRAL)
        val state = WearUiState(
            isLoading = false, error = null, outfit = outfit,
            weather = weather, heroImageUrl = null,
            locationName = "Kansas City", hasLocationPermission = true
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearWatchTheme {
                HeroWearMainScreen(
                    uiState = state,
                    onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                    onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
                )
            }
        }
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(60) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        composeView.measure(spec, spec)
        composeView.layout(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                texts += node.config[SemanticsProperties.Text].map { it.text }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                texts += node.config[SemanticsProperties.ContentDescription]
            }
            node.children.forEach { walk(it) }
        }
        var root: SemanticsNode? = null
        fun collect(v: View) {
            if (root == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                root = v.semanticsOwner.unmergedRootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (root == null) collect(v.getChildAt(i))
            }
        }
        collect(composeView.rootView)
        root?.let { walk(it) }
        controller.pause().stop().destroy()
        return texts
    }

    @Test
    fun `hero at 384 shows feels-like headline and actual subordinate`() {
        val texts = shoot("DA-13-wear-hero-384.png", 384)
        assertTrue("headline must be the feels-like 44°F: $texts", texts.any { it == "44°F" })
        assertTrue("subordinate must read Actual 52°F: $texts", texts.any { it == "Actual 52°F" })
    }

    @Test
    fun `hero at 456 shows feels-like headline and actual subordinate`() {
        val texts = shoot("DA-13-wear-hero-456.png", 456)
        assertTrue(texts.any { it == "44°F" })
        assertTrue(texts.any { it == "Actual 52°F" })
    }
}
