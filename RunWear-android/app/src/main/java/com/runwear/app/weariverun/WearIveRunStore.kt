package com.runwear.app.weariverun

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        // RW-142 (Nov 8, 2026): an ADMITTED write always completes.
        // The recording policy admits a settle only from the
        // selection's own committed solve; once admitted, the edit
        // runs under NonCancellable so a caller torn down in the
        // same instant (a composition leaving, a superseding state
        // arriving) cannot strand it — the failure mode behind the
        // confirmed-time-pick entry that never updated on device.
        withContext(NonCancellable) {
            context.wearIveRunDataStore.edit { prefs ->
                val current = decode(prefs[Keys.ENTRIES_JSON])
                prefs[Keys.ENTRIES_JSON] =
                    encode(WearIveRunCollectionLogic.upsert(current, entry))
            }
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

    /** RW-122: remove every entry collated under [place]. */
    suspend fun removePlace(place: String) {
        context.wearIveRunDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES_JSON])
            prefs[Keys.ENTRIES_JSON] =
                encode(WearIveRunCollectionLogic.removePlace(current, place))
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
                    e.momentLabel?.let { put("momentLabel", it) }
                    put("feelsLikeF", e.feelsLikeF)
                    put("condition", e.conditionLabel)
                    put("snowing", e.isSnowing)
                    put("raining", e.isRaining)
                    put("items", JSONArray(e.items))
                    e.heroRef?.let { put("heroRef", it) }
                    // GEN-6 Wave B (RW-31): the share fields —
                    // written only when present, so a legacy
                    // entry's stored shape is unchanged.
                    e.actualF?.let { put("actualF", it) }
                    e.windMph?.let { put("windMph", it) }
                    e.dayLowF?.let { put("dayLowF", it) }
                    e.dayHighF?.let { put("dayHighF", it) }
                    if (e.itemKeys.isNotEmpty()) {
                        put("itemKeys", JSONArray(e.itemKeys))
                    }
                    e.faceAnchor?.let { anchor ->
                        put("faceAnchor", JSONObject().apply {
                            put("cx", anchor.centerX)
                            put("cy", anchor.centerY)
                            put("w", anchor.width)
                            put("h", anchor.height)
                        })
                    }
                })
            }
            return array.toString()
        }

        fun decode(json: String?): List<WearIveRunDayEntry> {
            if (json.isNullOrBlank()) return emptyList()
            // GEN-5 (RW-29 FA-14): PER-RECORD tolerance — one
            // malformed or type-shifted record is skipped, never
            // allowed to empty the whole scrapbook. The gen-4
            // decoder threw inside a whole-list fallback, so a
            // single bad record silently discarded every entry.
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val o = array.getJSONObject(i)
                    val items = o.optJSONArray("items") ?: JSONArray()
                    WearIveRunItemKeyMigration.migrate(WearIveRunDayEntry(
                        date = LocalDate.parse(o.getString("date")),
                        // GEN-3: entries written before the session
                        // rule have no sessionId/settledAtMillis —
                        // they decode as legacy (blank / 0) and keep
                        // their (date, place) identity. No migration
                        // wipe: history decodes exactly as stored.
                        sessionId = o.optString("sessionId"),
                        settledAtMillis = o.optLong("settledAtMillis", 0L),
                        momentLabel = o.optString("momentLabel").takeIf { it.isNotBlank() },
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
                        heroRef = o.optString("heroRef").takeIf { it.isNotBlank() },
                        // GEN-6 Wave B (RW-31): the share fields
                        // decode null-safely — a legacy record
                        // (keys absent) decodes with nulls /
                        // empty, the rest of the record intact.
                        actualF = if (o.has("actualF") && !o.isNull("actualF")) {
                            o.optDouble("actualF")
                        } else {
                            null
                        },
                        windMph = if (o.has("windMph") && !o.isNull("windMph")) {
                            o.optDouble("windMph")
                        } else {
                            null
                        },
                        dayLowF = if (o.has("dayLowF") && !o.isNull("dayLowF")) {
                            o.optDouble("dayLowF")
                        } else {
                            null
                        },
                        dayHighF = if (o.has("dayHighF") && !o.isNull("dayHighF")) {
                            o.optDouble("dayHighF")
                        } else {
                            null
                        },
                        itemKeys = o.optJSONArray("itemKeys")?.let { keys ->
                            (0 until keys.length()).map { keys.optString(it) }
                        } ?: emptyList(),
                        faceAnchor = o.optJSONObject("faceAnchor")?.let { fa ->
                            ShareFaceAnchor(
                                centerX = fa.optDouble("cx"),
                                centerY = fa.optDouble("cy"),
                                width = fa.optDouble("w"),
                                height = fa.optDouble("h")
                            )
                        }
                    ))
                }.getOrNull()
            }
        }
    }
}
