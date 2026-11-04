package com.runwear.app.weariverun

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
import java.time.LocalDate

private val Context.wearIveRunDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weariverun_collection",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * RW-26: persistence for the Wear I've Run collection. Entries live
 * in their own DataStore file as one JSON array; all arithmetic
 * (chapters, stays, filters) is computed from the entries by
 * [WearIveRunCollectionLogic], never stored. NO backfill: the store
 * starts empty and only [recordSettled] — called when a generation
 * settles — ever adds an entry.
 */
class WearIveRunStore(private val context: Context) {

    private object Keys {
        val ENTRIES_JSON = stringPreferencesKey("entries_json")
    }

    val entriesFlow: Flow<List<WearIveRunDayEntry>> =
        context.wearIveRunDataStore.data.map { prefs ->
            decode(prefs[Keys.ENTRIES_JSON])
        }

    /** Record the day's settled version (replaces any earlier one). */
    suspend fun recordSettled(entry: WearIveRunDayEntry) {
        context.wearIveRunDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES_JSON])
            prefs[Keys.ENTRIES_JSON] = encode(WearIveRunCollectionLogic.upsert(current, entry))
        }
    }

    /** Remove one day's entry (the mini bar's trash: one tap, no confirmation). */
    suspend fun remove(date: LocalDate, place: String) {
        context.wearIveRunDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES_JSON])
            prefs[Keys.ENTRIES_JSON] =
                encode(WearIveRunCollectionLogic.remove(current, date, place))
        }
    }

    /** GEN-3: remove an entry by identity — a session entry by its
     * sessionId (its place/date may have moved within the session),
     * a legacy entry by (date, place). */
    suspend fun removeByIdentity(entry: WearIveRunDayEntry) {
        context.wearIveRunDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES_JSON])
            prefs[Keys.ENTRIES_JSON] =
                encode(WearIveRunCollectionLogic.removeByIdentity(current, entry))
        }
    }

    companion object {
        fun encode(entries: List<WearIveRunDayEntry>): String {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(JSONObject().apply {
                    put("date", e.date.toString())
                    put("place", e.place)
                    put("sessionId", e.sessionId)
                    put("settledAtMillis", e.settledAtMillis)
                    put("feelsLikeF", e.feelsLikeF)
                    put("condition", e.conditionLabel)
                    put("snowing", e.isSnowing)
                    put("raining", e.isRaining)
                    put("items", JSONArray(e.items))
                    e.heroRef?.let { put("heroRef", it) }
                })
            }
            return array.toString()
        }

        fun decode(json: String?): List<WearIveRunDayEntry> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    val items = o.optJSONArray("items") ?: JSONArray()
                    WearIveRunDayEntry(
                        date = LocalDate.parse(o.getString("date")),
                        // GEN-3: entries written before the session
                        // rule have no sessionId/settledAtMillis —
                        // they decode as legacy (blank / 0) and keep
                        // their (date, place) identity. No migration
                        // wipe: history decodes exactly as stored.
                        sessionId = o.optString("sessionId"),
                        settledAtMillis = o.optLong("settledAtMillis", 0L),
                        // WS-4: entries recorded before the simple-place
                        // model carry raw geocode strings; normalize on
                        // read so every downstream surface (narration,
                        // chapter covers) renders the simple form.
                        place = com.runwear.shared.domain.model.SimplePlace
                            .fromRawDisplayString(o.getString("place")).display,
                        feelsLikeF = o.optDouble("feelsLikeF", 0.0),
                        conditionLabel = o.optString("condition"),
                        isSnowing = o.optBoolean("snowing"),
                        isRaining = o.optBoolean("raining"),
                        items = (0 until items.length()).map { items.optString(it) },
                        heroRef = o.optString("heroRef").takeIf { it.isNotBlank() }
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
