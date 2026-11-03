package com.runwear.app.ui.screens

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * RW-26 Section E: RECENT LOCATIONS — a jump-back list, not
 * favorites. A place joins ONLY by being actually selected,
 * most-recent-first, capped at [RecentLocationsLogic.CAP]. There is
 * no "add favorite" step anywhere. Removal is one tap, no
 * confirmation, with Undo (the sheet's snackbar).
 */
data class RecentLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object RecentLocationsLogic {

    const val CAP = 5

    /** Record a selection: dedupe by name, most-recent-first, cap. */
    fun record(recents: List<RecentLocation>, selected: RecentLocation): List<RecentLocation> =
        (listOf(selected) + recents.filterNot { it.name == selected.name }).take(CAP)

    fun remove(recents: List<RecentLocation>, name: String): List<RecentLocation> =
        recents.filterNot { it.name == name }

    /** Undo of a removal restores the entry at its original index. */
    fun restore(
        recents: List<RecentLocation>,
        removed: RecentLocation,
        index: Int
    ): List<RecentLocation> {
        if (recents.any { it.name == removed.name }) return recents
        val at = index.coerceIn(0, recents.size)
        return (recents.take(at) + removed + recents.drop(at)).take(CAP + 1).take(CAP)
    }
}

private val Context.recentLocationsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_locations",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Persistence for [RecentLocation]s (own DataStore file, JSON array). */
class RecentLocationsStore(private val context: Context) {

    private object Keys {
        val RECENTS_JSON = stringPreferencesKey("recents_json")
    }

    val recentsFlow: Flow<List<RecentLocation>> =
        context.recentLocationsDataStore.data.map { prefs -> decode(prefs[Keys.RECENTS_JSON]) }

    suspend fun record(selected: RecentLocation) {
        context.recentLocationsDataStore.edit { prefs ->
            prefs[Keys.RECENTS_JSON] =
                encode(RecentLocationsLogic.record(decode(prefs[Keys.RECENTS_JSON]), selected))
        }
    }

    suspend fun setAll(recents: List<RecentLocation>) {
        context.recentLocationsDataStore.edit { prefs ->
            prefs[Keys.RECENTS_JSON] = encode(recents)
        }
    }

    companion object {
        fun encode(recents: List<RecentLocation>): String {
            val array = JSONArray()
            recents.forEach { r ->
                array.put(JSONObject().apply {
                    put("name", r.name)
                    put("lat", r.latitude)
                    put("lon", r.longitude)
                })
            }
            return array.toString()
        }

        fun decode(json: String?): List<RecentLocation> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    RecentLocation(
                        name = o.getString("name"),
                        latitude = o.getDouble("lat"),
                        longitude = o.getDouble("lon")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
