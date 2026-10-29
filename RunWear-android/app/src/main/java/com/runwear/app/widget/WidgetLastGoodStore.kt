package com.runwear.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore backing for [WidgetLastGood]. Its own file
 * (`runwear_widget_last_good`) so it never entangles with the user's
 * preferences store in the shared module. One delegate instance per process —
 * the widget, the refresh callback, the WorkManager backstop and the app's
 * write-through all run in the app process and share it.
 */
private val Context.widgetLastGoodDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "runwear_widget_last_good",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class WidgetLastGoodStore(context: Context) {

    private val dataStore = context.applicationContext.widgetLastGoodDataStore

    suspend fun read(): WidgetLastGood? {
        val prefs = dataStore.data.first()
        if (prefs.asMap().isEmpty()) return null
        val entries = prefs.asMap().entries.associate { (key, value) ->
            key.name to value.toString()
        }
        return WidgetLastGood.fromEntries(entries)
    }

    suspend fun write(lastGood: WidgetLastGood) {
        val entries = lastGood.toEntries()
        dataStore.edit { prefs ->
            prefs.clear()
            entries.forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
