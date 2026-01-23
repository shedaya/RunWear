package com.runwear.shared.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "runwear_preferences")

data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val comfortPreference: ComfortPreference = ComfortPreference.NEUTRAL,
    val hasCompletedOnboarding: Boolean = false
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USE_CELSIUS = booleanPreferencesKey("use_celsius")
        val COMFORT_PREFERENCE = intPreferencesKey("comfort_preference")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
    }
    
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            temperatureUnit = if (prefs[Keys.USE_CELSIUS] == true) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
            comfortPreference = ComfortPreference.entries.getOrNull(prefs[Keys.COMFORT_PREFERENCE] ?: 2) 
                ?: ComfortPreference.NEUTRAL,
            hasCompletedOnboarding = prefs[Keys.COMPLETED_ONBOARDING] ?: false
        )
    }
    
    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_CELSIUS] = unit == TemperatureUnit.CELSIUS
        }
    }
    
    suspend fun setComfortPreference(preference: ComfortPreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMFORT_PREFERENCE] = preference.ordinal
        }
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMPLETED_ONBOARDING] = completed
        }
    }
}
