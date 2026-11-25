package com.runwear.app.ui.components

import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.io.File
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-2 (RC-2, D-05; T-1 RULING) — the hero numeral is a readout.
 *
 * Pins, at the source level where the defect lived:
 *  - no counter tween anywhere in the hero sheet (the 800 ms
 *    `animateIntAsState` that counted through the unit flip and
 *    displayed false temperatures is gone, not merely unused);
 *  - no tap behavior on the numeral — `onTempClick` exists nowhere
 *    in the sheet or its host screen (the unit is changed in
 *    Settings only);
 *  - the preferences collector in MainViewModel applies preferences
 *    to state and does NOT refetch weather (the D-05 repaint
 *    mechanism);
 *  - the weather repository's request carries no unit at all.
 *
 * Source pins follow the repo's existing precedent for structural
 * guarantees (cf. the fixture/source tests under shared/): the
 * working directory is the app module dir; the walk goes up until
 * the repo root answers.
 */
class HeroNumeralReadoutTest {

    /** [androidRootRelative] is relative to the RunWear-android root. */
    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun heroSheet() =
        sourceFile("app/src/main/java/com/runwear/app/ui/components/HeroSheet.kt").readText()

    @Test
    fun `the counter tween is absent from the hero sheet`() {
        val source = heroSheet()
        assertFalse(
            "animateIntAsState (the 800 ms counter) must not exist in the hero sheet",
            source.contains("animateIntAsState")
        )
        assertFalse(
            "no animation spec may drive the numeral",
            source.contains("temperatureCounter")
        )
    }

    @Test
    fun `the numeral has no tap behavior anywhere in the sheet or its host`() {
        val sheet = heroSheet()
        assertFalse("onTempClick must not exist in HeroSheet.kt", sheet.contains("onTempClick"))
        assertFalse(
            "the sheet's numeral carries no clickable modifier",
            sheet.contains("clickable")
        )
        val host = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        ).readText()
        assertFalse(
            "onTempClick must not be wired by HeroMainScreen.kt (T-1: Settings only)",
            host.contains("onTempClick")
        )
    }

    @Test
    fun `the preferences collector applies state and never refetches`() {
        val vm = sourceFile(
            "app/src/main/java/com/runwear/app/ui/viewmodel/MainViewModel.kt"
        ).readText()
        val collectAt = vm.indexOf("preferencesFlow.collect")
        assertTrue("the preferences collector must exist", collectAt >= 0)
        // Slice the collector's lambda body by brace counting.
        val openBrace = vm.indexOf('{', collectAt)
        var depth = 0
        var end = -1
        for (i in openBrace until vm.length) {
            when (vm[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        assertTrue("collector body must close", end > openBrace)
        val body = vm.substring(openBrace, end)
        assertFalse(
            "a preference write must NEVER refetch weather (D-05) — the unit is a projection",
            body.contains("fetchWeather")
        )
        assertTrue(
            "the collector applies the unit to state",
            body.contains("temperatureUnit = prefs.temperatureUnit")
        )
    }

    @Test
    fun `the weather request carries no unit - canonical fahrenheit and mph only`() {
        val repo = sourceFile(
            "shared/src/main/java/com/runwear/shared/data/repository/WeatherRepository.kt"
        ).readText()
        assertFalse(
            "WeatherRepository must not reference TemperatureUnit at all",
            repo.contains("TemperatureUnit")
        )
        assertTrue(
            "the forecast request is explicitly canonical",
            repo.contains("temperatureUnit = \"fahrenheit\"") &&
                repo.contains("windSpeedUnit = \"mph\"")
        )
    }

    @Test
    fun `the numeral is the projected state value, computed directly`() {
        // There is no intermediate value to display: the function the
        // sheet calls IS the numeral, for both units, from the same
        // canonical weather.
        val weather = WeatherConditions(
            dateTime = KotlinLocalDateTime(2026, 11, 3, 7, 0),
            temperature = 45.0,
            feelsLike = 41.0,
            humidity = 40,
            windSpeed = 10.0,
            windGusts = 15.0,
            precipitationProbability = 5,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 1.0,
            weatherCode = WeatherCode.CLEAR_SKY
        )
        assertEquals("41°", HeroSheetFormat.heroNumeral(weather, TemperatureUnit.FAHRENHEIT))
        assertEquals("5°", HeroSheetFormat.heroNumeral(weather, TemperatureUnit.CELSIUS))
    }
}
