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

class Rw144BHeroNowRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - b - hero now state dark`() {
        val state = uiState()
        seedExactEntry(state)
        val host = compose(dark = true) { Home(state, mimState()) }
        capture(host, "b-hero-now-dark.png")
        val boxes = texts(host)
        assertTrue(boxes.any { it.text == "Sun, Nov 8" })
        assertTrue(boxes.any { it.text == "Now" })
        assertTrue(boxes.any { it.text == "58°" })
        assertTrue(boxes.any { it.text == "FEELS LIKE" })
        host.controller.pause().stop().destroy()
    }

}
