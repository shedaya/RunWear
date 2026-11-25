package com.runwear.app.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WS-2 (T-1 ruling): the unit is changed in Settings only — and the
 * change persists.
 *
 * The Settings toggle writes through [PreferencesRepository] (the
 * same write MainViewModel.toggleUnit performs). This test proves
 * the durable half of the WS-2 contract on the real DataStore:
 * after the write, a FRESH repository — the read a relaunch makes —
 * emits the chosen unit as its FIRST value, so the first paint of a
 * new process is preference-determined rather than a default that
 * flips when something resolves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemperatureUnitPersistenceTest {

    @Test
    fun `a settings unit change persists and is the first value a fresh read emits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        PreferencesRepository(context).setTemperatureUnit(TemperatureUnit.CELSIUS)

        val firstAfterRelaunch = PreferencesRepository(context).preferencesFlow.first()
        assertEquals(
            "the first emission after relaunch must be the persisted °C — the first paint's unit",
            TemperatureUnit.CELSIUS,
            firstAfterRelaunch.temperatureUnit
        )

        PreferencesRepository(context).setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        val firstAfterSecondRelaunch = PreferencesRepository(context).preferencesFlow.first()
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            firstAfterSecondRelaunch.temperatureUnit
        )
    }
}
