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
import com.runwear.app.ui.components.DayChartBuilder
import com.runwear.app.ui.components.DayChartGlanceBand
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Rw144EBelowFoldRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - e - below the fold - glance band and detailed weather`() {
        val w = phoenixWeather()
        val model = DayChartBuilder.build(
            hourly = hourly(today),
            date = today,
            comfort = ComfortPreference.NEUTRAL,
            nowDate = today,
            nowHour = 7,
            nowFeelsLikeF = 58.0
        )
        val host = compose(dark = false) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Surface)
                    .padding(top = 16.dp)
            ) {
                DayChartGlanceBand(
                    model = model,
                    selectedHour = 7,
                    locationToday = today,
                    nowHour = 7,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    onChangeClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(RunWearColors.Hairline)
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    UnifiedWeatherDetail(
                        weather = w,
                        unit = TemperatureUnit.FAHRENHEIT,
                        locationName = "Phoenix, AZ",
                        momentLabel = "Now",
                        runningTip = null,
                        comfortPreference = ComfortPreference.NEUTRAL
                    )
                }
            }
        }
        capture(host, "e-below-fold-light.png")
        val boxes = texts(host)
        assertTrue("Change row", boxes.any { it.text == "Change ›" })
        assertTrue("detail rows", boxes.any { it.text == "Feels like" })
        assertTrue("detail rows", boxes.any { it.text == "Precipitation" })
        host.controller.pause().stop().destroy()
    }

}
