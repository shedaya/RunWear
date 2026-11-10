package com.runwear.app.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WindUnit
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOC-1 WP-R2 gate (DESIGN §7): with NO stored choice, the unit
 * defaults derive from the device locale — and en-US must resolve
 * to EXACTLY the shipped pair (Fahrenheit + mph), i.e. en-US
 * behaviour is unchanged. A stored choice always wins, and the
 * wind frame resolves independently of the temperature frame
 * (temperature-only legacy stores keep their legacy wind pairing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocaleDefaultUnitsTest {

    private lateinit var original: Locale

    @Before
    fun saveLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun freshStoreUnderEnUsIsTheShippedPair() = runBlocking {
        Locale.setDefault(Locale.US)
        val prefs = PreferencesRepository(context()).preferencesFlow.first()
        assertEquals(TemperatureUnit.FAHRENHEIT, prefs.temperatureUnit)
        assertEquals(WindUnit.MPH, prefs.windUnit)
    }

    @Test
    fun freshStoreUnderGermanLocaleIsMetric() = runBlocking {
        Locale.setDefault(Locale.GERMANY)
        val prefs = PreferencesRepository(context()).preferencesFlow.first()
        assertEquals(TemperatureUnit.CELSIUS, prefs.temperatureUnit)
        assertEquals(WindUnit.KMH, prefs.windUnit)
    }

    @Test
    fun storedTemperatureChoiceImpliesItsLegacyWindPairing() = runBlocking {
        Locale.setDefault(Locale.US)
        val repo = PreferencesRepository(context())
        repo.setTemperatureUnit(TemperatureUnit.CELSIUS)
        val prefs = repo.preferencesFlow.first()
        assertEquals(TemperatureUnit.CELSIUS, prefs.temperatureUnit)
        assertEquals(WindUnit.KMH, prefs.windUnit)
    }

    @Test
    fun storedWindChoiceWinsIndependentlyOfTemperature() = runBlocking {
        Locale.setDefault(Locale.US)
        val repo = PreferencesRepository(context())
        repo.setTemperatureUnit(TemperatureUnit.CELSIUS)
        repo.setWindUnit(WindUnit.MPH)
        val prefs = repo.preferencesFlow.first()
        assertEquals(TemperatureUnit.CELSIUS, prefs.temperatureUnit)
        assertEquals(WindUnit.MPH, prefs.windUnit)
    }

    @Test
    fun storedChoiceBeatsLocaleDefault() = runBlocking {
        Locale.setDefault(Locale.GERMANY)
        val repo = PreferencesRepository(context())
        repo.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        val prefs = repo.preferencesFlow.first()
        assertEquals(TemperatureUnit.FAHRENHEIT, prefs.temperatureUnit)
        assertEquals(WindUnit.MPH, prefs.windUnit)
    }
}
