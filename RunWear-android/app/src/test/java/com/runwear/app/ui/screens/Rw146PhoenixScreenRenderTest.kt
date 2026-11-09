package com.runwear.app.ui.screens

import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime

/**
 * RW-146 replay frame 0: the Phoenix manual state on the first
 * screen — pill, conditions and dock all say Phoenix (the state
 * the sheet in frame A must agree with).
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw146PhoenixScreenRenderTest : Rw145RenderTestBase() {

    @Test
    fun `rw146 phoenix manual - pill conditions and narration agree`() {
        val photo = heroPhotoUrl("mild_clear_male.webp")
        val w = WeatherConditions(
            dateTime = KotlinLocalDateTime(2026, 11, 9, 7, 0),
            temperature = 69.0,
            feelsLike = 58.0,
            humidity = 26,
            windSpeed = 10.0,
            windGusts = 11.9,
            windDirection = 30,
            precipitationProbability = 0,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 0,
            uvIndex = 0.0,
            weatherCode = WeatherCode.CLEAR_SKY
        )
        val state = nyState(photo).copy(
            weather = w,
            outfit = engine.execute(
                w, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
            ),
            locationName = "Phoenix, AZ",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("Phoenix, AZ"),
            locationSource = "manual"
        )
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        capture(host, "rw146-0-phoenix-manual-screen.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW146-0 texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        assertTrue("pill must read Phoenix", boxes.any { it.text == "Phoenix, AZ" })
        assertTrue(
            "feels-like must be Phoenix's 58",
            boxes.any { it.text == "58°" }
        )
        assertTrue(
            "no surface may still say New York",
            boxes.none { it.text.contains("New York") }
        )
        host.controller.pause().stop().destroy()
    }
}
