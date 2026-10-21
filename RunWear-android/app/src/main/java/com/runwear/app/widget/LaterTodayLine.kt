package com.runwear.app.widget

import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Large widget's "Later today" line (spec §B.3.4). The outfit the user
 * will want changes before the weather number does, so the earliest outfit
 * CHANGE at +3 h / +6 h wins ("By 6 PM: drop the Windbreaker · rain 40%").
 * Only when the outfit is stable does the line fall back to the next-hours
 * feels-like range + peak rain chance. Pure builders + one suspend compute.
 */
object LaterTodayLine {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("h a", Locale.US)
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
        val parts = mutableListOf("By ${targetTime.format(TIME_FORMAT)}: ${clauses.joinToString(" · ")}")
        if (laterWeather.precipitationProbability >= RAIN_MENTION_THRESHOLD) {
            parts += "rain ${laterWeather.precipitationProbability}%"
        }
        return parts.joinToString(" · ")
    }

    /** Stable-outfit fallback: next-hours feels-like range + peak rain chance. */
    fun stableLine(hours: List<HourlyForecast>): String? {
        if (hours.isEmpty()) return null
        val min = hours.minOf { it.feelsLike }
        val max = hours.maxOf { it.feelsLike }
        val peakRain = hours.maxOf { it.precipitationProbability }
        val unit = if (hours.first().isCelsius) "°C" else "°F"
        val range = if (min.toInt() == max.toInt()) {
            "feels like ${min.toInt()}$unit"
        } else {
            "feels like ${min.toInt()}–${max.toInt()}$unit"
        }
        val parts = mutableListOf("Later today: $range")
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
        unit: TemperatureUnit,
        current: WeatherConditions,
        currentOutfit: OutfitRecommendation,
        comfort: ComfortPreference,
        getOutfit: GetOutfitRecommendationUseCase,
        hourly: List<HourlyForecast>? = null
    ): String? {
        return runCatching {
            for (hoursAhead in listOf(3L, 6L)) {
                val target = current.dateTime.plusHours(hoursAhead)
                val laterWeather = weatherRepository
                    .getWeatherForDateTime(latitude, longitude, target, unit)
                    .getOrNull() ?: continue
                val laterOutfit = getOutfit.execute(laterWeather, comfort)
                deltaLine(target, currentOutfit, laterOutfit, laterWeather)?.let { return@runCatching it }
            }
            val forecast = hourly ?: weatherRepository
                .getHourlyForecast(latitude, longitude, unit)
                .getOrDefault(emptyList())
            val window = forecast.filter {
                it.dateTime.isAfter(current.dateTime) &&
                    !it.dateTime.isAfter(current.dateTime.plusHours(6))
            }
            stableLine(window)
        }.getOrNull()
    }
}
