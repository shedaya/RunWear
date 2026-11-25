package com.runwear.shared.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.runwear.shared.domain.model.AppearancePreference
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.LocaleUnitsPolicy
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WindUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "runwear_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /**
     * LOC-1 WP-R2 (DESIGN §7): the wind frame, decoupled from the
     * temperature unit. Its default pairs with the legacy
     * temperature pairing (FAHRENHEIT ⇒ MPH) so an en-US user who
     * never touches units sees exactly the shipped behaviour; the
     * repository resolves the real default from the device locale
     * until the user stores a wind choice.
     */
    val windUnit: WindUnit = WindUnit.MPH,
    val comfortPreference: ComfortPreference = ComfortPreference.NEUTRAL,
    val genderPreference: GenderPreference = GenderPreference.UNISEX,
    val hasCompletedOnboarding: Boolean = false,
    /** RW-25: appearance (theme) preference; SYSTEM until the user pins one. */
    val appearancePreference: AppearancePreference = AppearancePreference.SYSTEM
)

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val source: String // "manual" or "gps"
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USE_CELSIUS = booleanPreferencesKey("use_celsius")
        // LOC-1 WP-R2: the wind frame's own stored choice (0 = mph,
        // 1 = km/h). ABSENCE of the key = no choice made — the
        // locale default applies; presence always wins.
        val WIND_UNIT = intPreferencesKey("wind_unit")
        val COMFORT_PREFERENCE = intPreferencesKey("comfort_preference")
        val GENDER_PREFERENCE = intPreferencesKey("gender_preference")
        val APPEARANCE_PREFERENCE = intPreferencesKey("appearance_preference")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        // Manual location keys
        val MANUAL_LAT = stringPreferencesKey("manual_lat")
        val MANUAL_LON = stringPreferencesKey("manual_lon")
        val MANUAL_LOCATION_NAME = stringPreferencesKey("manual_location_name")
        val LOCATION_SOURCE = stringPreferencesKey("location_source") // "manual" or "gps"
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        // LOC-1 WP-R2 (DESIGN §7): unit defaults derive from the
        // device locale ONLY until the user stores a choice — the
        // stored key's PRESENCE is the choice, and it always wins.
        // en-US resolves to FAHRENHEIT + MPH, the shipped defaults,
        // so en-US behaviour is unchanged (pinned by
        // LocaleDefaultUnitsTest under an en-US locale).
        val deviceLocale = java.util.Locale.getDefault()
        val storedCelsius = prefs[Keys.USE_CELSIUS]
        val temperatureUnit = when {
            storedCelsius != null ->
                if (storedCelsius) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT
            else -> LocaleUnitsPolicy.defaultTemperatureUnit(deviceLocale)
        }
        val storedWind = prefs[Keys.WIND_UNIT]
        val windUnit = when {
            storedWind != null -> WindUnit.entries.getOrNull(storedWind) ?: WindUnit.MPH
            storedCelsius != null -> WindUnit.impliedBy(temperatureUnit)
            else -> LocaleUnitsPolicy.defaultWindUnit(deviceLocale)
        }
        UserPreferences(
            temperatureUnit = temperatureUnit,
            windUnit = windUnit,
            comfortPreference = ComfortPreference.entries.getOrNull(prefs[Keys.COMFORT_PREFERENCE] ?: 2)
                ?: ComfortPreference.NEUTRAL,
            genderPreference = GenderPreference.entries.getOrNull(prefs[Keys.GENDER_PREFERENCE] ?: 2)
                ?: GenderPreference.UNISEX,
            hasCompletedOnboarding = prefs[Keys.COMPLETED_ONBOARDING] ?: false,
            appearancePreference = AppearancePreference.entries.getOrNull(prefs[Keys.APPEARANCE_PREFERENCE] ?: 0)
                ?: AppearancePreference.SYSTEM
        )
    }
    
    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_CELSIUS] = unit == TemperatureUnit.CELSIUS
        }
    }

    /** LOC-1 WP-R2: store the wind frame choice (its presence in
     * the store is what makes it win over the locale default). */
    suspend fun setWindUnit(unit: WindUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIND_UNIT] = unit.ordinal
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

    suspend fun setAppearancePreference(preference: AppearancePreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APPEARANCE_PREFERENCE] = preference.ordinal
        }
    }

    suspend fun setGenderPreference(preference: GenderPreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GENDER_PREFERENCE] = preference.ordinal
        }
    }

    // Generic string storage for affiliate cohort etc.
    suspend fun getString(key: Preferences.Key<String>): String? {
        return context.dataStore.data.first()[key]
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    suspend fun remove(key: Preferences.Key<String>) {
        context.dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    // Location persistence (both manual search and GPS)
    suspend fun saveLocation(lat: Double, lon: Double, name: String, source: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MANUAL_LAT] = lat.toString()
            prefs[Keys.MANUAL_LON] = lon.toString()
            prefs[Keys.MANUAL_LOCATION_NAME] = name
            prefs[Keys.LOCATION_SOURCE] = source
        }
    }

    // Convenience method for manual location saves
    suspend fun saveManualLocation(lat: Double, lon: Double, name: String) {
        saveLocation(lat, lon, name, "manual")
    }

    // Convenience method for GPS location saves
    suspend fun saveGPSLocation(lat: Double, lon: Double, name: String) {
        saveLocation(lat, lon, name, "gps")
    }

    suspend fun clearSavedLocation() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.MANUAL_LAT)
            prefs.remove(Keys.MANUAL_LON)
            prefs.remove(Keys.MANUAL_LOCATION_NAME)
            prefs.remove(Keys.LOCATION_SOURCE)
        }
    }

    // For backwards compatibility
    suspend fun clearManualLocation() = clearSavedLocation()

    suspend fun getSavedLocation(): SavedLocation? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[Keys.MANUAL_LAT]?.toDoubleOrNull() ?: return null
        val lon = prefs[Keys.MANUAL_LON]?.toDoubleOrNull() ?: return null
        val name = prefs[Keys.MANUAL_LOCATION_NAME] ?: "Your Location"
        val source = prefs[Keys.LOCATION_SOURCE] ?: "gps"
        return SavedLocation(lat, lon, name, source)
    }

    fun getLocationSourceFlow(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCATION_SOURCE] ?: "gps"
    }
}
