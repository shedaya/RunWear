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

class Rw144GStockServeRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - g - stock serve hero carries no pill`() {
        val state = uiState(stock = true)
        seedExactEntry(state)
        val host = compose(dark = false) {
            Home(state, mimState(stock = true))
        }
        capture(host, "g-stock-serve-light.png")
        val boxes = texts(host)
        assertFalse(boxes.any { it.text.contains("of 10 looks") })
        assertFalse(boxes.any { it.text == "Making this you…" })
        assertTrue(boxes.any { it.text == "58°" })
        host.controller.pause().stop().destroy()
    }

}
