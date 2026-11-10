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
 * LOC-1 WP-R2 gate (DESIGN §7), isolated in its own class on
 * purpose: Robolectric resets the sandbox per test CLASS, and the
 * DataStore file persists across methods within a class — the
 * legacy-pairing case requires a store where the wind key is
 * ABSENT, which sibling tests that write a wind choice would
 * legitimately poison. A temperature-only legacy store keeps its
 * historical wind pairing (CELSIUS ⇒ km/h).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocaleImpliedWindPairingTest {

    private lateinit var original: Locale

    @Before
    fun saveLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun storedTemperatureChoiceImpliesItsLegacyWindPairing() = runBlocking {
        Locale.setDefault(Locale.US)
        val repo = PreferencesRepository(ApplicationProvider.getApplicationContext<Context>())
        repo.setTemperatureUnit(TemperatureUnit.CELSIUS)
        val prefs = repo.preferencesFlow.first()
        assertEquals(TemperatureUnit.CELSIUS, prefs.temperatureUnit)
        assertEquals(WindUnit.KMH, prefs.windUnit)
    }

}
