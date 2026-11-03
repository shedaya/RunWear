package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.temperatureBracketColor
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RW-25 sheet text composition — pure, no composition, unit-tested.
 *
 * The string literals follow the immediate home files' existing
 * pattern (the home surface is literal-based today; see the RW-25
 * report for the localization-standard tension this inherits rather
 * than resolves).
 */
object HeroSheetFormat {

    /** RW-26 settled header stack, step (i): the small label. */
    const val FEELS_LIKE_LABEL = "Feels like"

    /**
     * RW-26 settled header stack, step (ii): the location line
     * "{Place} · {Moment}" in sentence case (15.5sp semibold in the
     * composable). MOMENT is "Now" while the now state is selected,
     * otherwise the selected moment in DateTimePill's human format.
     */
    fun locationLine(
        locationName: String,
        selectedDateTime: LocalDateTime,
        locationNow: LocalDateTime,
        isNowSelected: Boolean
    ): String {
        val moment = if (isNowSelected) "Now" else momentLabel(selectedDateTime, locationNow)
        return "${simplePlaceName(locationName)} · $moment"
    }

    /** Legacy single-line eyebrow, retained for the split-pane header. */
    fun eyebrow(
        locationName: String,
        selectedDateTime: LocalDateTime,
        locationNow: LocalDateTime,
        isNowSelected: Boolean
    ): String {
        val moment = if (isNowSelected) "NOW" else momentLabel(selectedDateTime, locationNow)
        return "FEELS LIKE · ${simplePlaceName(locationName)} · $moment".uppercase(Locale.US)
    }

    /**
     * WS-4 (RC-4): the header's place is the SIMPLE form. State should
     * already carry a [com.runwear.shared.domain.model.SimplePlace]
     * display (derived once at geocode time); this render-boundary
     * normalization guarantees a raw geocode string ("New York,
     * New York") can never reach the line even from a legacy saved
     * value. Idempotent for simple forms and sentinels alike.
     */
    fun simplePlaceName(locationName: String): String =
        com.runwear.shared.domain.model.SimplePlace.fromRawDisplayString(locationName).display

    /** The non-NOW moment label, in DateTimePill's human formats. */
    fun momentLabel(selectedDateTime: LocalDateTime, locationNow: LocalDateTime): String {
        val dateText = when {
            selectedDateTime.toLocalDate() == locationNow.toLocalDate() -> "Today"
            selectedDateTime.toLocalDate() == locationNow.toLocalDate().plusDays(1) -> "Tomorrow"
            else -> selectedDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
        val timeText = selectedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        return "$dateText, $timeText"
    }

    /**
     * RW-26 (design §7 amendment 1): the line under the hero number
     * carries the ACTUAL temperature only — "Actual NN°" and nothing
     * else. Condition/wind/humidity live in the weather strip below;
     * duplicating them here was the contradiction class §2 fixed.
     *
     * WS-2: [unit] is the render-time projection of the canonical
     * store — the number is the stored °F value converted by the
     * preference, in the same frame transaction as every other
     * figure on the sheet.
     */
    fun caption(weather: WeatherConditions, unit: TemperatureUnit): String {
        return "Actual ${unit.displayValue(weather.temperature)}°"
    }

    /**
     * WS-2 (RC-2, T-1 ruling): the hero numeral is a READOUT of the
     * canonical feels-like, projected through [unit]. It is computed
     * directly from state on every frame — there is no counter, no
     * tween, and no tap target: the value on screen is always the
     * current state, never an intermediate count toward it.
     */
    fun heroNumeral(weather: WeatherConditions, unit: TemperatureUnit): String {
        return "${unit.displayValue(weather.effectiveTemperature)}°"
    }

    /** The wind pill's text, projected through [unit] like every other figure. */
    fun windText(weather: WeatherConditions, unit: TemperatureUnit): String {
        return unit.formatWind(weather.windSpeed)
    }
}

/**
 * RW-25: the home SHEET block — the lower zone of the two-zone home.
 *
 * In the stacked layouts this block overlaps the photo's foot by
 * [HeroSheetOverlap] (HeroMainScreen sizes the overlap); in the
 * EXPANDED split it tops the right pane with no overlap. It owns, in
 * order: the eyebrow, the hero number row (tabular sans numeral in
 * ink — never temperature-colored — beside the bracket chip), the
 * caption line, the flat DateTimePill, and the flat WeatherPillsRow.
 * The outfit content follows it in the same scroll on the same sheet
 * surface.
 *
 * @param overlapsPhoto stacked layouts: top corners rounded 28dp over
 * the photo. Split layout: the block is a pane-top card, all corners
 * rounded.
 */
@Composable
fun HeroSheetBlock(
    weather: WeatherConditions?,
    locationName: String,
    selectedDateTime: LocalDateTime,
    locationNow: LocalDateTime,
    isNowSelected: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit = {},
    overlapsPhoto: Boolean = true,
    modifier: Modifier = Modifier,
    /**
     * WS-2: the render-time unit projection. Every figure in this
     * block (numeral, caption, wind pill) converts from the canonical
     * store through this ONE preference value in the same composition
     * pass, so a unit change swaps the whole sheet atomically.
     */
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    val shape = if (overlapsPhoto) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }

    val bracket = weather?.let { TempBracket.fromFahrenheit(it.feelsLikeInFahrenheit) }

    Column(
        modifier = modifier
            .clip(shape)
            .background(RunWearColors.Surface)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 12.dp)
    ) {
        // (a) RW-26 settled header stack: (i) "Feels like" small
        // uppercase label, (ii) the location line "{Place} · {Moment}"
        // at 15.5sp semibold sentence case — distinct pieces, never
        // one undifferentiated eyebrow block (design §7 amendment 2).
        Text(
            text = HeroSheetFormat.FEELS_LIKE_LABEL.uppercase(Locale.US),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = RunWearColors.TextSecondary,
            maxLines = 1
        )
        Text(
            text = HeroSheetFormat.locationLine(
                locationName = locationName,
                selectedDateTime = selectedDateTime,
                locationNow = locationNow,
                isNowSelected = isNowSelected
            ),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                lineHeight = 20.sp
            ),
            color = RunWearColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(8.dp))

        // (b) Hero number row: the numeral in ink beside the bracket
        // chip. Only the chip's dot carries the temperature color.
        // WS-2 (T-1 ruling): the numeral is display-only — no tap
        // target, no haptic, no counter animation. The unit is
        // changed in Settings only.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = weather?.let { HeroSheetFormat.heroNumeral(it, temperatureUnit) } ?: "0°",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = RunWearColors.TextPrimary
            )
            if (bracket != null) {
                Spacer(Modifier.size(14.dp))
                TemperatureBracketChip(bracket = bracket)
            }
        }

        // (c) Caption line
        weather?.let {
            Text(
                text = HeroSheetFormat.caption(it, temperatureUnit),
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // (d) Date/Time pill, flat on the sheet
        DateTimePill(
            dateTime = selectedDateTime,
            referenceNow = locationNow,
            isNowSelected = isNowSelected,
            onDateClick = onDateClick,
            onTimeClick = onTimeClick,
            onNowClick = onNowClick,
            onSheet = true
        )

        Spacer(Modifier.height(12.dp))

        // (e) Weather pills, flat on the sheet (behavior unchanged)
        WeatherPillsRow(
            weather = weather,
            onPillClick = onWeatherPillClick,
            onSheet = true,
            temperatureUnit = temperatureUnit
        )
    }
}

/**
 * The temperature bracket chip beside the hero number: a filled dot
 * in the bracket's temperature color + the bracket label in ink, on a
 * tonal pill. The numeral itself is never temperature-colored.
 */
@Composable
private fun TemperatureBracketChip(bracket: TempBracket) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(temperatureBracketColor(bracket))
            )
            Spacer(Modifier.size(7.dp))
            Text(
                text = bracket.name,
                style = MaterialTheme.typography.labelLarge,
                color = RunWearColors.TextPrimary
            )
        }
    }
}
