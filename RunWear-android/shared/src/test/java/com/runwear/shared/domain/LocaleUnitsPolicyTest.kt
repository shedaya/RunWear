package com.runwear.shared.domain

import com.runwear.shared.domain.model.LocaleUnitsPolicy
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WindUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOC-1 WP-R2 (DESIGN §7): the locale-derived unit defaults.
 * en-US must resolve to the shipped pair (Fahrenheit + mph);
 * metric locales to Celsius + km/h; the wind frame is its own
 * decision, decoupled from the temperature frame.
 */
class LocaleUnitsPolicyTest {

    @Test
    fun enUsDefaultsAreTheShippedPair() {
        assertEquals(TemperatureUnit.FAHRENHEIT, LocaleUnitsPolicy.defaultTemperatureUnit(Locale.US))
        assertEquals(WindUnit.MPH, LocaleUnitsPolicy.defaultWindUnit(Locale.US))
        assertTrue(LocaleUnitsPolicy.usesImperial(Locale.US))
    }

    @Test
    fun metricLocalesDefaultToCelsiusAndKmh() {
        for (locale in listOf(Locale.GERMANY, Locale.FRANCE, Locale.JAPAN, Locale.UK, Locale.CANADA)) {
            assertEquals("temp for $locale", TemperatureUnit.CELSIUS, LocaleUnitsPolicy.defaultTemperatureUnit(locale))
            assertEquals("wind for $locale", WindUnit.KMH, LocaleUnitsPolicy.defaultWindUnit(locale))
            assertFalse(LocaleUnitsPolicy.usesImperial(locale))
        }
    }

    @Test
    fun windUnitFormatsAndConvertsIndependently() {
        assertEquals("10 mph", WindUnit.MPH.format(10.0))
        assertEquals("16 km/h", WindUnit.KMH.format(10.0))
        assertEquals(WindUnit.MPH, WindUnit.impliedBy(TemperatureUnit.FAHRENHEIT))
        assertEquals(WindUnit.KMH, WindUnit.impliedBy(TemperatureUnit.CELSIUS))
        // The legacy temperature pairing still renders identically.
        assertEquals("10 mph", TemperatureUnit.FAHRENHEIT.formatWind(10.0))
        assertEquals("16 km/h", TemperatureUnit.CELSIUS.formatWind(10.0))
    }
}
