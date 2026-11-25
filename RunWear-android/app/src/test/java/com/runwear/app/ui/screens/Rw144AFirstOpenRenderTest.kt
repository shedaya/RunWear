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

class Rw144AFirstOpenRenderTest : Rw144RenderTestBase() {

    @Test
    fun `rw144 - a - first open at rest - dock overlays clear space only`() {
        val state = uiState()
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, mimState()) }
        capture(host, "a-first-open-rest-light.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = texts(host)
        val dockWordmark = boxes.firstOrNull { it.text.contains("Wear I've Run") }
        println(
            "RW144-A texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        assertTrue("first screen must show the chip date", boxes.any { it.text == "Sun, Nov 8" })
        assertTrue("first screen must show Now", boxes.any { it.text == "Now" })
        assertTrue(
            "free pill must show the settled count form",
            boxes.any { it.text == "6 of 10 looks" }
        )
        if (dockWordmark != null) {
            val dockTop = dockWordmark.top - 14 * density // dock's own top padding zone
            // The RW-144 acceptance (task item 8, closing RW-140 Audit
            // item B): the dock's band vs the bottom of the lowest
            // readable SHEET element it could overlap — the sheet
            // being the weather line, the outfit-count header and the
            // outfit rows (task item 6). Below-the-fold elements (the
            // day-chart glance band and the detailed weather block,
            // task item 7) are measured and printed for the report
            // but are not sheet elements.
            val sheetTexts = setOf(
                "0 mph", "WIND", "0%", "RAIN", "60%", "HUMIDITY", "0 of 11", "UV",
                "2 items for your run",
                "Short Sleeve Shirt", "Moisture-wicking technical fabric",
                "Running Shorts (5-7\")", "Standard running shorts"
            )
            val sheetBoxes = boxes.filter { it.text in sheetTexts }
            val lowestSheetBottom = sheetBoxes.maxOfOrNull { it.bottom } ?: 0f
            val offenders = sheetBoxes.filter { it.bottom > dockTop + 2 }
            val bandGraze = boxes.filter { box ->
                box.text !in sheetTexts &&
                    box.bottom > dockTop + 2 &&
                    box.top < dockTop + 56 * density
            }
            println(
                "RW144-A dockTopDp=${dockTop / density} " +
                    "lowestSheetBottomDp=${lowestSheetBottom / density} " +
                    "offenders=$offenders bandGraze=$bandGraze"
            )
            assertTrue(
                "first screen must render the sheet elements",
                sheetBoxes.size >= 10
            )
            assertTrue(
                "dock overlays readable sheet content at rest: $offenders",
                offenders.isEmpty()
            )
        } else {
            throw AssertionError(
                "the seeded today's entry must surface the dock at rest — " +
                    "without it the clear-space acceptance measures nothing"
            )
        }
        host.controller.pause().stop().destroy()
    }

}
