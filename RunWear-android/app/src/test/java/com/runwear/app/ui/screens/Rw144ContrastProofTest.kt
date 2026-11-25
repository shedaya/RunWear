package com.runwear.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.runwear.app.ui.components.DayChartBuilder
import com.runwear.app.ui.components.HeroFeelsLikeBlock
import com.runwear.app.ui.components.HeroFeelsLikeScrim
import com.runwear.app.ui.components.HeroLuminancePolicy
import com.runwear.app.ui.components.DayChartGlanceBand
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Rw144ContrastProofTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - contrast - luminance drives the feels-like scrim`() {
        val bright = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        bright.eraseColor(0xFFF2EFE6.toInt())
        val dark = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        dark.eraseColor(0xFF14100C.toInt())
        val w = phoenixWeather()

        fun frame(bitmap: Bitmap, name: String): Pair<Float, Float> {
            val footLum = HeroLuminancePolicy.sampleRegionLuminance(
                bitmap, 0f, 0.52f, 1f, 1f
            )
            val peak = HeroLuminancePolicy.scrimPeakAlpha(footLum)
            val host = compose(dark = false) {
                Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(196.dp)
                    ) {
                        HeroFeelsLikeScrim(peakAlpha = peak, modifier = Modifier.fillMaxSize())
                        HeroFeelsLikeBlock(
                            weather = w,
                            temperatureUnit = TemperatureUnit.FAHRENHEIT,
                            comfortPreference = ComfortPreference.NEUTRAL,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
            capture(host, name)
            host.controller.pause().stop().destroy()
            return footLum to peak
        }

        val (brightLum, brightPeak) = frame(bright, "h-contrast-bright-photo.png")
        val (darkLum, darkPeak) = frame(dark, "i-contrast-dark-photo.png")
        println(
            "RW144-CONTRAST bright: luminance=$brightLum scrimPeak=$brightPeak | " +
                "dark: luminance=$darkLum scrimPeak=$darkPeak | " +
                "floor=${HeroLuminancePolicy.SCRIM_PEAK_FLOOR}"
        )
        assertTrue("bright photo must measure bright", brightLum > 0.8f)
        assertTrue("dark photo must measure dark", darkLum < 0.05f)
        assertEquals(
            "dark photo relaxes to the mock floor",
            HeroLuminancePolicy.SCRIM_PEAK_FLOOR, darkPeak, 0.01f
        )
        assertTrue(
            "bright photo scales the scrim well above the floor",
            brightPeak > HeroLuminancePolicy.SCRIM_PEAK_FLOOR + 0.10f
        )
        assertTrue(brightPeak > darkPeak)
    }

}
