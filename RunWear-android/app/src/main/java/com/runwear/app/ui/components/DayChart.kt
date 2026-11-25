package com.runwear.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.temperatureBracketColor
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.toJavaLocalDateTime

/**
 * GEN-4 DAY CHART — the day's feels-like arc, drawn from the SAME
 * hourly series the retired HourGrid rendered (RW-23: the
 * comfort-adjusted feels-like is the one frame every temperature
 * lives in). Pure model + policies first; the composables below are
 * thin renderers over them.
 */

/** One hour of the chart: the adjusted feels-like, its prescription
 * bracket, and the outfit the engine prescribes for that hour. */
data class DayChartHour(
    val hour: Int,
    /** Comfort-adjusted feels-like, canonical °F — the plotted value. */
    val feelsLikeF: Double,
    /** Raw (unadjusted) feels-like, canonical °F. */
    val rawFeelsLikeF: Double,
    val bracket: TempBracket,
    /** The drafted hour's outfit preview items (display names). */
    val outfitItems: List<String>,
    /** GEN-5 (item 15): the hour's condition, for the picker
     * chart's per-hour condition glyphs. Defaults keep the older
     * fixtures compiling; the builder always passes the real code. */
    val weatherCode: com.runwear.shared.domain.model.WeatherCode =
        com.runwear.shared.domain.model.WeatherCode.UNKNOWN
) {
    /** Identity of the outfit at this hour, for change detection. */
    val outfitSignature: String get() = outfitItems.joinToString("|")
}

/** A contiguous stretch of hours sharing one prescription bracket. */
data class DayChartBand(
    val startHour: Int,
    /** Exclusive: the first hour NOT in this band (24 = day end). */
    val endHourExclusive: Int,
    val bracket: TempBracket
)

data class DayChartModel(
    val date: LocalDate,
    val hours: List<DayChartHour>,
    val low: DayChartHour?,
    val high: DayChartHour?,
    /** Hours where the outfit/bracket changes vs the previous hour. */
    val changeHours: List<Int>,
    /** Last hour with forecast data, or null when the day has none. */
    val horizonLastHour: Int?,
    /** RW-53(ii): this date's sunrise/sunset as minutes after local
     * midnight, from the forecast payload's daily block. Null when
     * the payload carried none — the night shading then falls back
     * to [DayChartBandPolicy.NIGHT_RANGES]. */
    val sunriseMinutes: Int? = null,
    val sunsetMinutes: Int? = null
) {
    val selectableHours: Set<Int> get() = hours.map { it.hour }.toSet()
    val isEmpty: Boolean get() = hours.isEmpty()

    fun point(hour: Int): DayChartHour? = hours.firstOrNull { it.hour == hour }

    /** Bracket bands in hour order, covering only the forecast span. */
    val bands: List<DayChartBand> get() {
        if (hours.isEmpty()) return emptyList()
        val out = mutableListOf<DayChartBand>()
        var start = hours.first()
        var prev = hours.first()
        for (h in hours.drop(1)) {
            if (h.bracket != prev.bracket) {
                out += DayChartBand(start.hour, prev.hour + 1, start.bracket)
                start = h
            }
            prev = h
        }
        out += DayChartBand(start.hour, prev.hour + 1, start.bracket)
        return out
    }
}

object DayChartBuilder {

    /**
     * Build the model for [date] from the full hourly forecast.
     * Hours absent from the forecast are absent from the model —
     * the horizon is never invented over. The outfit per hour comes
     * from the prescription engine itself ([DayChartOutfitPreview]),
     * in the RW-23 adjusted frame.
     */
    fun build(
        hourly: List<HourlyForecast>,
        date: LocalDate,
        comfort: ComfortPreference,
        sunTimes: List<com.runwear.shared.domain.model.DaySunTimes> = emptyList(),
        // RW-FIXWAVE (Sol's ruling, Nov 7, 2026; review row S6-27):
        // the chart's point for the CURRENT hour takes the live
        // current-conditions value, and the outfit band shading for
        // that hour derives from the same value — so at Now the
        // chart and the hero quote one temperature. Only the Now
        // hour of the location's today is substituted; every other
        // hour (and every picked hour's chart) stays forecast.
        // [nowFeelsLikeF] is in the chart's comfort-adjusted frame.
        nowDate: LocalDate? = null,
        nowHour: Int? = null,
        nowFeelsLikeF: Double? = null
    ): DayChartModel {
        val dayHours = hourly
            .map { it to it.dateTime.toJavaLocalDateTime() }
            .filter { (_, dt) -> dt.toLocalDate() == date }
            .sortedBy { (_, dt) -> dt.hour }
            .distinctBy { (_, dt) -> dt.hour }
            .map { (h, dt) ->
                val forecastAdjusted = h.feelsLike + comfort.tempAdjustment
                val adjusted = if (
                    nowFeelsLikeF != null && nowDate == date && nowHour == dt.hour
                ) {
                    nowFeelsLikeF
                } else {
                    forecastAdjusted
                }
                DayChartHour(
                    hour = dt.hour,
                    feelsLikeF = adjusted,
                    rawFeelsLikeF = if (adjusted == forecastAdjusted) {
                        h.feelsLike
                    } else {
                        adjusted - comfort.tempAdjustment
                    },
                    bracket = TempBracket.fromFahrenheit(adjusted),
                    outfitItems = DayChartOutfitPreview.itemsFor(h, comfort),
                    weatherCode = h.weatherCode
                )
            }
        val changes = mutableListOf<Int>()
        for (i in 1 until dayHours.size) {
            val prev = dayHours[i - 1]
            val cur = dayHours[i]
            if (cur.bracket != prev.bracket || cur.outfitSignature != prev.outfitSignature) {
                changes += cur.hour
            }
        }
        val daySun = sunTimes.firstOrNull {
            it.date == kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth)
        }
        return DayChartModel(
            date = date,
            hours = dayHours,
            low = dayHours.minByOrNull { it.feelsLikeF },
            high = dayHours.maxByOrNull { it.feelsLikeF },
            changeHours = changes,
            horizonLastHour = dayHours.lastOrNull()?.hour,
            sunriseMinutes = daySun?.sunriseMinutes,
            sunsetMinutes = daySun?.sunsetMinutes
        )
    }
}

/**
 * The outfit the prescription engine gives one forecast hour.
 *
 * NOTE (deviation surface, honest): [HourlyForecast] carries no
 * measured precipitation amount, gusts or cloud cover, so the rain
 * gate is derived from the hour's weather code (rain codes count as
 * measured rain, amount 1.0mm so the engine's >0.1mm jacket gate
 * behaves as it does for measured rain). The bracket and the item
 * set are the engine's own output in the adjusted frame; the FINAL
 * outfit after a Confirm re-solves from full conditions in the
 * ViewModel, which remains authoritative.
 */
object DayChartOutfitPreview {

    private val engine = GetOutfitRecommendationUseCase()

    fun itemsFor(hourly: HourlyForecast, comfort: ComfortPreference): List<String> {
        val isRaining = hourly.weatherCode.isRain
        val selection = engine.recommendFrames(
            adjustedFeelsLikeF = hourly.feelsLike + comfort.tempAdjustment,
            rawFeelsLikeF = hourly.feelsLike,
            isRaining = isRaining,
            precipitation = if (isRaining) 1.0 else 0.0,
            isWindy = hourly.windSpeed > 10,
            isHumid = hourly.humidity > 65,
            uvIndex = hourly.uvIndex,
            hourOfDay = hourly.hour
        )
        return selection.allItems.map { it.displayName }
    }
}

/** Pure geometry for both chart renderings (glance band + sheet). */
object DayChartGeometry {

    /** Center-of-hour x, as a fraction of the full 24h width. */
    fun xFraction(hour: Int): Float = (hour + 0.5f) / 24f

    /** Left edge of an hour's cell, as a fraction of the 24h width. */
    fun cellLeftFraction(hour: Int): Float = hour / 24f

    /**
     * The hour a chart x-position selects: the NEAREST hour, clamped
     * to the day, then snapped to the nearest SELECTABLE hour (hours
     * past the horizon are never selectable). Null when nothing is.
     */
    fun hourFromXFraction(fraction: Float, selectable: Set<Int>): Int? {
        if (selectable.isEmpty()) return null
        val raw = (fraction.coerceIn(0f, 1f) * 24f - 0.5f)
        val nearest = Math.round(raw).coerceIn(0, 23)
        if (nearest in selectable) return nearest
        return selectable.minByOrNull { kotlin.math.abs(it - nearest) }
    }

    /** Y fraction (0 = top) for a feels-like within [lowF, highF]. */
    fun yFraction(feelsF: Double, lowF: Double, highF: Double): Float {
        val range = highF - lowF
        if (range <= 0.0) return 0.5f
        val paddedLow = lowF - range * 0.12
        val paddedHigh = highF + range * 0.12
        return (1.0 - (feelsF - paddedLow) / (paddedHigh - paddedLow)).toFloat()
            .coerceIn(0f, 1f)
    }
}

object DayChartFormat {

    private val HOUR_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h a", Locale.US)
    private val AXIS_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** "6 PM" — the sheet readout's hour segment. */
    fun hourLabel(hour: Int): String = LocalTime.of(hour, 0).format(HOUR_FORMAT)

    /** "6:00 PM" — the glance band's axis row. */
    fun axisLabel(hour: Int): String = LocalTime.of(hour, 0).format(AXIS_FORMAT)

    /** The sheet readout: hour + feels-like ONLY, e.g. "6 PM · 47°". */
    fun readout(hour: Int, feelsLikeF: Double, unit: TemperatureUnit): String =
        "${hourLabel(hour)} · ${unit.displayValue(feelsLikeF)}°"
}

/** The glance band's state rules — pure, unit-tested. */
object DayChartBandPolicy {

    enum class ChangeAction { OPEN_TIME_SHEET }

    /** The NOW marker exists only when the selected day IS the
     * viewed location's today. */
    fun showNowMarker(selectedDate: LocalDate, locationToday: LocalDate): Boolean =
        selectedDate == locationToday

    /** The selected dot follows the current selection's hour. */
    fun selectedDotHour(selectedHour: Int): Int = selectedHour

    /**
     * RW-103 (Nov 6, 2026): the horizontal footprint the
     * "Change ›" control owns at the band's foot — its text
     * (~58 dp at 12 sp bold), its start/end padding (8 + 12 dp)
     * and an 8 dp breathing gap: 86 dp in all. The selected-hour
     * axis label's travel is clamped so the label's END never
     * enters this footprint: in the walkthrough build the clamp
     * reserved only 124 dp for label-start + label, so at a
     * late-day dot the label and the Change control collided at
     * the strip's right end and "Change ›" read as covered.
     */
    const val CHANGE_FOOTPRINT_DP: Float = 86f

    /** The axis label's width allowance ("12:00 PM" at 11 sp bold). */
    const val AXIS_LABEL_WIDTH_DP: Float = 56f

    /**
     * The axis label's start (dp from the band's left edge): the
     * dot's x pulled back 16 dp, clamped so the whole label
     * stays inside the band and clear of the Change footprint.
     */
    fun axisLabelStartDp(dotXDp: Float, bandWidthDp: Float): Float {
        val maxStart = maxOf(
            4f, bandWidthDp - CHANGE_FOOTPRINT_DP - AXIS_LABEL_WIDTH_DP
        )
        return (dotXDp - 16f).coerceIn(4f, maxStart)
    }

    /** Tapping Change opens the Choose-a-Time sheet — the band's
     * only action. */
    fun changeAction(): ChangeAction = ChangeAction.OPEN_TIME_SHEET

    /**
     * GEN-5 (item 15): night shading for the picker chart, from the
     * fixed fallback window 20:00–05:59, expressed as contiguous
     * (startHour, endHourExclusive) ranges. RW-53(ii): used only
     * when the forecast payload carried no sunrise/sunset for the
     * day — see [nightRanges].
     */
    val NIGHT_RANGES: List<Pair<Int, Int>> = listOf(0 to 6, 20 to 24)

    fun isNightHour(hour: Int): Boolean = NIGHT_RANGES.any { (start, end) -> hour in start until end }

    /**
     * RW-53(ii): night shading follows the day's actual sunrise and
     * sunset when the model carries them (minutes after midnight,
     * ranges in the same unit): night is [0, sunrise) ∪ [sunset,
     * 1440). Falls back to [NIGHT_RANGES] scaled to minutes when
     * either is missing.
     */
    fun nightRanges(model: DayChartModel): List<Pair<Int, Int>> {
        val rise = model.sunriseMinutes
        val set = model.sunsetMinutes
        if (rise == null || set == null || set <= rise) {
            return NIGHT_RANGES.map { (s, e) -> (s * 60) to (e * 60) }
        }
        return listOf(0 to rise, set to (24 * 60))
    }

    /** Is the given minute of the day inside a shaded night range? */
    fun isNightMinute(model: DayChartModel, minutes: Int): Boolean =
        nightRanges(model).any { (start, end) -> minutes in start until end }
}

/**
 * GEN-4: the home glance band — below the outfit list. The feels-like
 * curve over shaded prescription-bracket bands; day low/high labels
 * at the band's ends; a NOW marker on the location's today only; a
 * dot at the selected hour.
 *
 * GEN-5 (RW-28 item 14, B-8): the selected hour is named ON THE
 * BAND'S OWN AXIS — a label inside the band at the dot's position —
 * and "Change ›" sits at the band's own foot. The separate
 * hour/Change row that used to sit beneath the band is gone.
 */
@Composable
fun DayChartGlanceBand(
    model: DayChartModel,
    selectedHour: Int,
    locationToday: LocalDate,
    nowHour: Int,
    temperatureUnit: TemperatureUnit,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (model.isEmpty) return
    val low = model.low ?: return
    val high = model.high ?: return
    val showNow = DayChartBandPolicy.showNowMarker(model.date, locationToday)
    val dotHour = DayChartBandPolicy.selectedDotHour(selectedHour)
    val dotPoint = model.point(dotHour) ?: model.hours.lastOrNull { it.hour < dotHour }
        ?: model.hours.first()

    Column(modifier = modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RunWearColors.Background)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // GEN-5 (RW-29 EX-13): night shading on the
                // glance band too — the same night window and
                // wash the picker chart draws (item 15 landed
                // it on the picker only). RW-53(ii): the window
                // is the day's sunrise–sunset when the payload
                // carried them (ranges in minutes here).
                for ((startMin, endMin) in DayChartBandPolicy.nightRanges(model)) {
                    val left = startMin / (24f * 60f) * size.width
                    val right = endMin / (24f * 60f) * size.width
                    drawRect(
                        color = RunWearColors.TextPrimary.copy(alpha = 0.07f),
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height)
                    )
                }
                // Bracket bands.
                // RW-103 (Nov 6, 2026): the LAST band's tint runs
                // to the strip's right edge. The bands model only
                // covers the forecast span, so when the forecast
                // ends before midnight the tint stopped short and
                // the strip's foot — the "Change ›" control —
                // sat past the visible tinted edge, reading as
                // covered/clipped by the strip itself (the
                // owner's circled frame). The band DATA is
                // unchanged; only its paint extends.
                val bandList = model.bands
                for ((index, band) in bandList.withIndex()) {
                    val left = DayChartGeometry.cellLeftFraction(band.startHour) * size.width
                    val right = if (index == bandList.lastIndex) {
                        size.width
                    } else {
                        DayChartGeometry.cellLeftFraction(band.endHourExclusive) * size.width
                    }
                    drawRect(
                        color = temperatureBracketColor(band.bracket).copy(alpha = 0.13f),
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height)
                    )
                }
                // NOW marker.
                if (showNow) {
                    val x = DayChartGeometry.xFraction(nowHour) * size.width
                    drawLine(
                        color = RunWearColors.PineAccent.copy(alpha = 0.55f),
                        start = Offset(x, 4f),
                        end = Offset(x, size.height - 4f),
                        strokeWidth = 2f
                    )
                }
                // The curve.
                val path = Path()
                model.hours.forEachIndexed { i, p ->
                    val x = DayChartGeometry.xFraction(p.hour) * size.width
                    val y = DayChartGeometry.yFraction(p.feelsLikeF, low.feelsLikeF, high.feelsLikeF) *
                        (size.height - 16f) + 8f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = RunWearColors.Primary,
                    style = Stroke(width = 4f)
                )
                // Selected dot.
                val dx = DayChartGeometry.xFraction(dotPoint.hour) * size.width
                val dy = DayChartGeometry.yFraction(dotPoint.feelsLikeF, low.feelsLikeF, high.feelsLikeF) *
                    (size.height - 16f) + 8f
                drawCircle(color = RunWearColors.Surface, radius = 11f, center = Offset(dx, dy))
                drawCircle(color = RunWearColors.Primary, radius = 8f, center = Offset(dx, dy))
            }
            if (showNow) {
                Text(
                    text = "NOW",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunWearColors.PineAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = (
                                DayChartGeometry.xFraction(nowHour) * maxWidth.value - 10
                                ).dp.coerceAtLeast(0.dp),
                            top = 5.dp
                        )
                )
            }
            // GEN-5 (item 14): the selected hour named ON THE BAND'S
            // OWN AXIS — inside the band at the dot's x, clamped so
            // it never leaves the band at the day's edges.
            // GEN-5 (RW-29 FA-31): the label names the DOT'S hour
            // (dotPoint.hour) — when the selected hour has no
            // forecast point the dot falls back to the last hour
            // at or before it, and the gen-5 label kept printing
            // the raw selected hour, naming a moment the dot was
            // not on.
            Text(
                text = DayChartFormat.axisLabel(dotPoint.hour),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        // RW-103: the clamp is the policy's —
                        // the label's END stays clear of the
                        // Change control's true footprint.
                        start = DayChartBandPolicy.axisLabelStartDp(
                            dotXDp = DayChartGeometry.xFraction(dotPoint.hour) * maxWidth.value,
                            bandWidthDp = maxWidth.value
                        ).dp,
                        bottom = 4.dp
                    )
            )
            // "Change ›" at the band's own foot — the band's only
            // action, opening the Choose-a-Time sheet.
            Text(
                text = "Change ›",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.PineAccent,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clickable { onChangeClick() }
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            )
        }
        // WT-6 (parity with iOS): the day's low/high live BELOW
        // the plot, at its ends — inside the plot the rising
        // curve's stroke crossed the "high" label whenever the
        // day's peak sat near the right edge. Below the plot no
        // curve position can reach them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${temperatureUnit.displayValue(low.feelsLikeF)}° low",
                fontSize = 11.sp,
                color = RunWearColors.TextSecondary
            )
            Text(
                text = "${temperatureUnit.displayValue(high.feelsLikeF)}° high",
                fontSize = 11.sp,
                color = RunWearColors.TextSecondary
            )
        }
    }
}
