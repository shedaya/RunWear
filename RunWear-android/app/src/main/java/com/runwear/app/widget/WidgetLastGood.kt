package com.runwear.app.widget

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The widget's last successfully loaded data, persisted verbatim so a failed
 * refresh can keep showing it with an honest age instead of replacing it with
 * an error card (redesign spec §B.3.1 — the no-memory widget was the root
 * cause of the permanent "Couldn't load weather" resident).
 *
 * Serialization is a flat string map so it drops straight into DataStore
 * preferences, and so the round-trip is unit-testable without Android.
 * [ClothingItem] and [WeatherCode] are enums, so they persist by name/code.
 * Outfit `tips` are not rendered by the widget and are not persisted.
 */
data class WidgetLastGood(
    val weather: WeatherConditions,
    val outfit: OutfitRecommendation,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val laterTodayLine: String?,
    /** Wall-clock (device) moment the data was saved — drives "Updated …". */
    val savedAtLocal: LocalDateTime,
    /** Monotonic-ish epoch ms at save — drives age reckoning across clock/zone changes. */
    val savedAtEpochMs: Long
) {
    fun toEntries(): Map<String, String> {
        val w = weather
        val o = outfit
        val entries = mutableMapOf(
            "w_datetime" to w.dateTime.toJavaLocalDateTime().format(FMT),
            "w_temperature" to w.temperature.toString(),
            "w_feels_like" to w.feelsLike.toString(),
            "w_humidity" to w.humidity.toString(),
            "w_wind_speed" to w.windSpeed.toString(),
            "w_wind_gusts" to w.windGusts.toString(),
            "w_wind_direction" to w.windDirection.toString(),
            "w_precip_prob" to w.precipitationProbability.toString(),
            "w_precip" to w.precipitation.toString(),
            "w_is_raining" to w.isRaining.toString(),
            "w_is_snowing" to w.isSnowing.toString(),
            "w_cloud_cover" to w.cloudCover.toString(),
            "w_uv_index" to w.uvIndex.toString(),
            "w_code" to w.weatherCode.code.toString(),
            "w_is_celsius" to w.isCelsius.toString(),
            "o_top_base" to o.topBase.name,
            "o_bottom" to o.bottom.name,
            "location_name" to locationName,
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString(),
            "saved_at_local" to savedAtLocal.format(FMT),
            "saved_at_epoch_ms" to savedAtEpochMs.toString()
        )
        o.topMid?.let { entries["o_top_mid"] = it.name }
        o.topOuter?.let { entries["o_top_outer"] = it.name }
        o.head?.let { entries["o_head"] = it.name }
        o.hands?.let { entries["o_hands"] = it.name }
        if (o.accessories.isNotEmpty()) {
            entries["o_accessories"] = o.accessories.joinToString(",") { it.name }
        }
        laterTodayLine?.let { entries["later_today_line"] = it }
        return entries
    }

    companion object {
        private val FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /** Null on any missing/corrupt required field — a torn write must read as "no last-good", never crash. */
        fun fromEntries(entries: Map<String, String>): WidgetLastGood? {
            fun req(key: String): String? = entries[key]?.takeIf { it.isNotBlank() }
            fun item(key: String): ClothingItem? =
                req(key)?.let { name -> runCatching { ClothingItem.valueOf(name) }.getOrNull() }
            fun optItem(key: String): ClothingItem? = entries[key]?.let { name ->
                runCatching { ClothingItem.valueOf(name) }.getOrNull()
            }

            val weatherDateTime = req("w_datetime")?.let {
                runCatching { LocalDateTime.parse(it, FMT) }.getOrNull()?.toKotlinLocalDateTime()
            } ?: return null
            val weather = WeatherConditions(
                dateTime = weatherDateTime,
                temperature = req("w_temperature")?.toDoubleOrNull() ?: return null,
                feelsLike = req("w_feels_like")?.toDoubleOrNull() ?: return null,
                humidity = req("w_humidity")?.toIntOrNull() ?: return null,
                windSpeed = req("w_wind_speed")?.toDoubleOrNull() ?: return null,
                windGusts = req("w_wind_gusts")?.toDoubleOrNull() ?: return null,
                windDirection = req("w_wind_direction")?.toIntOrNull() ?: 0,
                precipitationProbability = req("w_precip_prob")?.toIntOrNull() ?: return null,
                precipitation = req("w_precip")?.toDoubleOrNull() ?: return null,
                isRaining = req("w_is_raining")?.toBooleanStrictOrNull() ?: return null,
                isSnowing = req("w_is_snowing")?.toBooleanStrictOrNull() ?: return null,
                cloudCover = req("w_cloud_cover")?.toIntOrNull() ?: return null,
                uvIndex = req("w_uv_index")?.toDoubleOrNull() ?: return null,
                weatherCode = WeatherCode.fromCode(req("w_code")?.toIntOrNull() ?: return null),
                isCelsius = req("w_is_celsius")?.toBooleanStrictOrNull() ?: return null
            )
            val topBase = item("o_top_base") ?: return null
            val bottom = item("o_bottom") ?: return null
            val accessories = entries["o_accessories"]
                ?.split(",")
                ?.mapNotNull { name -> runCatching { ClothingItem.valueOf(name) }.getOrNull() }
                ?: emptyList()
            val outfit = OutfitRecommendation(
                weather = weather,
                topBase = topBase,
                topMid = optItem("o_top_mid"),
                topOuter = optItem("o_top_outer"),
                bottom = bottom,
                head = optItem("o_head"),
                hands = optItem("o_hands"),
                accessories = accessories,
                tips = emptyList()
            )
            return WidgetLastGood(
                weather = weather,
                outfit = outfit,
                locationName = req("location_name") ?: return null,
                latitude = req("latitude")?.toDoubleOrNull() ?: return null,
                longitude = req("longitude")?.toDoubleOrNull() ?: return null,
                laterTodayLine = entries["later_today_line"],
                savedAtLocal = req("saved_at_local")?.let {
                    runCatching { LocalDateTime.parse(it, FMT) }.getOrNull()
                } ?: return null,
                savedAtEpochMs = req("saved_at_epoch_ms")?.toLongOrNull() ?: return null
            )
        }
    }
}
