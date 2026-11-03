package com.runwear.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.temperatureBracketColor
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
        return "$locationName · $moment"
    }

    /** Legacy single-line eyebrow, retained for the split-pane header. */
    fun eyebrow(
        locationName: String,
        selectedDateTime: LocalDateTime,
        locationNow: LocalDateTime,
        isNowSelected: Boolean
    ): String {
        val moment = if (isNowSelected) "NOW" else momentLabel(selectedDateTime, locationNow)
        return "FEELS LIKE · $locationName · $moment".uppercase(Locale.US)
    }

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
     */
    fun caption(weather: WeatherConditions): String {
        return "Actual ${weather.temperature.toInt()}°"
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
    onTempClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit = {},
    overlapsPhoto: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = if (overlapsPhoto) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }

    // Animated temperature counter — the count-up the on-photo number
    // always had, moved with the number to the sheet.
    val actualTemp = weather?.effectiveTemperature?.roundToInt() ?: 0
    val startTemp = maxOf(-10, actualTemp - 10)
    var targetTemp by remember { mutableStateOf(startTemp) }
    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "temperatureCounter"
    )
    LaunchedEffect(actualTemp) {
        targetTemp = startTemp
        kotlinx.coroutines.delay(50)
        targetTemp = actualTemp
    }

    val hapticFeedback = LocalHapticFeedback.current
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$animatedTemp°",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = RunWearColors.TextPrimary,
                modifier = Modifier.clickable {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTempClick()
                }
            )
            if (bracket != null) {
                Spacer(Modifier.size(14.dp))
                TemperatureBracketChip(bracket = bracket)
            }
        }

        // (c) Caption line
        weather?.let {
            Text(
                text = HeroSheetFormat.caption(it),
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
            onSheet = true
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
