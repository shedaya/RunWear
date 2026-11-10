package com.runwear.app.widget

import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime

/**
 * The Large widget's "Later today" line (spec §B.3.4). The outfit the user
 * will want changes before the weather number does, so the earliest outfit
 * CHANGE at +3 h / +6 h wins ("By 6 PM: drop the Windbreaker · rain 40%").
 * Only when the outfit is stable does the line fall back to the next-hours
 * feels-like range + peak rain chance. Pure builders + one suspend compute.
 */
object LaterTodayLine {

    // LOC-1 WP-R2: the later-today clock renders via LocaleFormats.timeHour (device locale).
    private const val RAIN_MENTION_THRESHOLD = 30

    /**
     * Diff two outfits into plain-words clauses, most significant garment
     * first, capped at two clauses. Accessories are deliberately excluded —
     * a sunglasses flip is not a "later today" event. Null when the garment
     * set is identical.
     */
    fun outfitDeltaClauses(
        current: OutfitRecommendation,
        later: OutfitRecommendation
    ): List<String> {
        data class Slot(val now: ClothingItem?, val then: ClothingItem?)
        val slots = listOf(
            Slot(current.topOuter, later.topOuter),
            Slot(current.topMid, later.topMid),
            Slot(current.topBase, later.topBase),
            Slot(current.bottom, later.bottom),
            Slot(current.head, later.head),
            Slot(current.hands, later.hands)
        )
        val clauses = mutableListOf<String>()
        for (slot in slots) {
            if (slot.now == slot.then) continue
            when {
                slot.now != null && slot.then == null ->
                    clauses += "drop the ${slot.now.displayName}"
                slot.now == null && slot.then != null ->
                    clauses += "add the ${slot.then.displayName}"
                slot.now != null && slot.then != null -> {
                    clauses += "drop the ${slot.now.displayName}"
                    clauses += "add the ${slot.then.displayName}"
                }
            }
            if (clauses.size >= 2) break
        }
        return clauses.take(2)
    }

    /** One target's line, or null when the outfit at that hour is unchanged. */
    fun deltaLine(
        targetTime: LocalDateTime,
        current: OutfitRecommendation,
        later: OutfitRecommendation,
        laterWeather: WeatherConditions
    ): String? {
        val clauses = outfitDeltaClauses(current, later)
        if (clauses.isEmpty()) return null
        val parts = mutableListOf(com.runwear.app.i18n.AppEnglish.WIDGET_BY_LINE.format(com.runwear.shared.i18n.LocaleFormats.timeHour(targetTime), clauses.joinToString(" · ")))
        if (laterWeather.precipitationProbability >= RAIN_MENTION_THRESHOLD) {
            parts += "rain ${laterWeather.precipitationProbability}%"
        }
        return parts.joinToString(" · ")
    }

    /**
     * Stable-outfit fallback: next-hours feels-like range + peak rain
     * chance. WS-2: the stored hours are canonical °F; [displayUnit]
     * is the render-time projection for the range text only.
     */
    fun stableLine(hours: List<HourlyForecast>, displayUnit: TemperatureUnit): String? {
        if (hours.isEmpty()) return null
        val min = hours.minOf { it.feelsLike }
        val max = hours.maxOf { it.feelsLike }
        val peakRain = hours.maxOf { it.precipitationProbability }
        val range = if (displayUnit.displayValue(min) == displayUnit.displayValue(max)) {
            "feels like ${displayUnit.formatTemperature(min)}"
        } else {
            "feels like ${displayUnit.displayValue(min)}–${displayUnit.formatTemperature(max)}"
        }
        val parts = mutableListOf(com.runwear.app.i18n.AppEnglish.WIDGET_LATER_TODAY_RANGE.format(range))
        if (peakRain >= RAIN_MENTION_THRESHOLD) parts += "rain up to $peakRain%"
        return parts.joinToString(" · ")
    }

    /**
     * Full computation. [hourly] may be supplied by a caller that already
     * fetched it (the app's write-through); otherwise it is fetched here.
     * Best-effort: any failure yields null, never an exception — this line is
     * an enhancement, not a load gate.
     */
    suspend fun compute(
        weatherRepository: WeatherRepository,
        latitude: Double,
        longitude: Double,
        displayUnit: TemperatureUnit,
        current: WeatherConditions,
        currentOutfit: OutfitRecommendation,
        comfort: ComfortPreference,
        getOutfit: GetOutfitRecommendationUseCase,
        hourly: List<HourlyForecast>? = null
    ): String? {
        return runCatching {
            // The models carry kotlinx LocalDateTime (shared :engine module);
            // this line's arithmetic stays in java.time, converted at the edge.
            val currentJava = current.dateTime.toJavaLocalDateTime()
            for (hoursAhead in listOf(3L, 6L)) {
                val target = currentJava.plusHours(hoursAhead)
                val laterWeather = weatherRepository
                    .getWeatherForDateTime(latitude, longitude, target.toKotlinLocalDateTime())
                    .getOrNull() ?: continue
                val laterOutfit = getOutfit.execute(laterWeather, comfort)
                deltaLine(target, currentOutfit, laterOutfit, laterWeather)?.let { return@runCatching it }
            }
            val forecast = hourly ?: weatherRepository
                .getHourlyForecast(latitude, longitude)
                .getOrDefault(emptyList())
            val window = forecast.filter {
                val forecastJava = it.dateTime.toJavaLocalDateTime()
                forecastJava.isAfter(currentJava) &&
                    !forecastJava.isAfter(currentJava.plusHours(6))
            }
            stableLine(window, displayUnit)
        }.getOrNull()
    }
}
