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

class Rw144CHeroPickedRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - c - hero picked state shows return segment`() {
        val state = uiState(picked = true)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, mimState()) }
        capture(host, "c-hero-picked-light.png")
        val boxes = texts(host)
        assertTrue("picked date", boxes.any { it.text == "Tue, Nov 10" })
        assertTrue("picked time", boxes.any { it.text == "3:00 PM" })
        assertTrue(
            "return-to-Now segment present in picked state",
            boxes.any { it.text == "Now" }
        )
        host.controller.pause().stop().destroy()
    }

}
