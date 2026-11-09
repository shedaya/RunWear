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

class Rw144DSheetTopRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - d - sheet top`() {
        val state = uiState()
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, mimState()) }
        val density = host.composeView.resources.displayMetrics.density
        captureCrop(host, "d-sheet-top-light.png", (330 * density).toInt(), (844 * density).toInt())
        val boxes = texts(host)
        assertTrue(boxes.any { it.text == "WIND" })
        assertTrue(boxes.any { it.text == "RAIN" })
        assertTrue(boxes.any { it.text == "HUMIDITY" })
        assertTrue(boxes.any { it.text == "UV" })
        assertTrue(boxes.any { it.text == "0 of 11" })
        assertTrue(boxes.any { it.text.contains("items for your run") })
        host.controller.pause().stop().destroy()
    }

}
