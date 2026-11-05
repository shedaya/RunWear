package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
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

    /** The non-NOW moment label. GEN-5 (RW-28 item 12, B-13):
     * for the location's TODAY the moment reads in the bare simple
     * form — "9:00 PM", no "Today," prefix. Other days keep their
     * day word, which carries information the time alone cannot. */
    fun momentLabel(selectedDateTime: LocalDateTime, locationNow: LocalDateTime): String {
        val timeText = selectedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        return when {
            selectedDateTime.toLocalDate() == locationNow.toLocalDate() -> timeText
            selectedDateTime.toLocalDate() == locationNow.toLocalDate().plusDays(1) -> "Tomorrow, $timeText"
            else -> "${selectedDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}, $timeText"
        }
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
    fun heroNumeral(weather: WeatherConditions, unit: TemperatureUnit): String =
        heroNumeral(weather, unit, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL)

    /**
     * GEN-5 (RW-28 item 11, B-3): the ONE-FRAME RULE. The numeral
     * reads the picked hour's feels-like in the RW-23
     * comfort-adjusted frame — the SAME frame the day chart, the
     * picker readout and the outfit engine all use. The gen-4
     * build read the raw feels-like here while the chart read the
     * adjusted one, so after a Confirm the sheet said 83° and the
     * numeral kept 78°: two feels-likes for one moment on one
     * screen. The comfort adjustment is applied ONCE, here at the
     * readout (and at the bracket chip), never to the stored
     * conditions — the outfit engine applies its own.
     */
    fun heroNumeral(
        weather: WeatherConditions,
        unit: TemperatureUnit,
        comfort: com.runwear.shared.domain.model.ComfortPreference
    ): String {
        return "${unit.displayValue(weather.effectiveTemperature + comfort.tempAdjustment)}°"
    }

    /** The bracket for the sheet's chip, in the same adjusted frame. */
    fun bracketFor(
        weather: WeatherConditions,
        comfort: com.runwear.shared.domain.model.ComfortPreference
    ): com.runwear.shared.domain.model.TempBracket =
        com.runwear.shared.domain.model.TempBracket.fromFahrenheit(
            weather.feelsLikeInFahrenheit + comfort.tempAdjustment
        )

    /** The wind pill's text, projected through [unit] like every other figure. */
    fun windText(weather: WeatherConditions, unit: TemperatureUnit): String {
        return unit.formatWind(weather.windSpeed)
    }

    /**
     * GEN-4: the hero's right-justified weather grid — the strict
     * two-column figure set beside the feels-like numeral. Labels in
     * one fixed column (WIND / RAIN / HUMIDITY / UV), values flush
     * right ("7 mph" / "0%" / "52%" / "0 of 11"). Rain is the
     * precipitation probability, shortened per the mock so the label
     * column stays narrow and even; UV is the index of 11.
     */
    fun weatherGridRows(weather: WeatherConditions, unit: TemperatureUnit): List<Pair<String, String>> =
        listOf(
            "WIND" to unit.formatWind(weather.windSpeed),
            "RAIN" to "${weather.precipitationProbability}%",
            "HUMIDITY" to "${weather.humidity}%",
            "UV" to "${weather.uvIndex.toInt()} of 11"
        )

    /** The headline line's place segment (non-interactive). */
    fun placeText(locationName: String): String = simplePlaceName(locationName)

    /** The headline line's moment segment text. */
    fun momentText(
        selectedDateTime: LocalDateTime,
        locationNow: LocalDateTime,
        isNowSelected: Boolean
    ): String = if (isNowSelected) "Now" else momentLabel(selectedDateTime, locationNow)
}

/**
 * GEN-4 headline-line entry (Concept 1) — pure, unit-tested.
 *
 * The moment segment of "{Place} · {Moment}" is tappable in BOTH
 * states (it opens the Choose-a-Time sheet). The compact pine
 * refresh-arrow + "Now" way-back segment is present ONLY in the
 * changed-time state, and tapping it performs exactly the existing
 * resetToNow(). The place segment stays non-interactive.
 */
object HeadlineLinePolicy {

    enum class Action { OPEN_TIME_SHEET, RESET_TO_NOW }

    data class State(
        val momentTappable: Boolean,
        val showNowReset: Boolean
    )

    fun state(isNowSelected: Boolean): State = State(
        momentTappable = true,
        showNowReset = !isNowSelected
    )

    fun momentAction(): Action = Action.OPEN_TIME_SHEET

    fun nowAction(state: State): Action? =
        if (state.showNowReset) Action.RESET_TO_NOW else null
}

/**
 * GEN-2 A2 (W-7): the hero numeral's atomicity rule. The numeral is a
 * readout (WS-2: no counter, no tween in the format layer), yet the
 * walkthrough caught it rendering through malformed partial glyphs
 * mid-transition after a location switch — the value changed hands
 * while the surface around it was still in flight. This policy makes
 * the swap atomic BY CONSTRUCTION: while a transition is in flight
 * the numeral on screen is the previously committed value, WHOLE;
 * when the transition commits, it becomes the new value, WHOLE. The
 * function's codomain is exactly {held, candidate} — no intermediate
 * or blended string can exist, which is the strongest form of the
 * rule a unit test can pin.
 */
object HeroNumeralPolicy {

    /**
     * The numeral to render. [held] is the last committed numeral,
     * [candidate] the numeral the current state would produce,
     * [transitionInFlight] whether a fetch/switch is still landing.
     */
    fun shown(held: String?, candidate: String?, transitionInFlight: Boolean): String? =
        if (transitionInFlight) held ?: candidate else candidate ?: held
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
     * GEN-2 A2 (W-7): true while a fetch/switch transition is in
     * flight — the hero numeral holds its last committed value whole
     * until the transition lands (see [HeroNumeralPolicy]).
     */
    holdNumeral: Boolean = false,
    /**
     * WS-2: the render-time unit projection. Every figure in this
     * block (numeral, caption, wind pill) converts from the canonical
     * store through this ONE preference value in the same composition
     * pass, so a unit change swaps the whole sheet atomically.
     */
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /**
     * GEN-5 (RW-28 item 11): the comfort frame for the numeral and
     * the bracket chip — the RW-23 adjusted frame, shared with the
     * day chart (see HeroSheetFormat.heroNumeral).
     */
    comfortPreference: com.runwear.shared.domain.model.ComfortPreference =
        com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
) {
    val shape = if (overlapsPhoto) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }

    val bracket = weather?.let { HeroSheetFormat.bracketFor(it, comfortPreference) }

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
        // GEN-4 headline-line entry: the MOMENT segment is tappable
        // (pine, bold, chevron — opens the Choose-a-Time sheet); in
        // the changed-time state a compact pine refresh + "Now"
        // segment follows it in the same dot-separated rhythm and
        // performs exactly resetToNow(). The place segment stays
        // non-interactive.
        val headlineState = HeadlineLinePolicy.state(isNowSelected)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = HeroSheetFormat.placeText(locationName),
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    lineHeight = 20.sp
                ),
                color = RunWearColors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = " · ",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    lineHeight = 20.sp
                ),
                color = RunWearColors.TextPrimary
            )
            // Surface onClick, not a tap modifier on the numeral's
            // file: the T-1 pin (HeroNumeralReadoutTest) keeps this
            // file free of tap modifiers — the numeral itself stays
            // a pure readout, and these segments are buttons.
            Surface(
                onClick = { onTimeClick() },
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Text(
                    text = HeroSheetFormat.momentText(selectedDateTime, locationNow, isNowSelected) + " ›",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = RunWearColors.PineAccent,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (headlineState.showNowReset) {
                Text(
                    text = " · ",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = RunWearColors.TextPrimary
                )
                Surface(
                    onClick = { onNowClick() },
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Jump to now",
                        tint = RunWearColors.PineAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Now",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        color = RunWearColors.PineAccent,
                        maxLines = 1
                    )
                }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // (b) Hero number row: the numeral in ink beside the bracket
        // chip. Only the chip's dot carries the temperature color.
        // WS-2 (T-1 ruling): the numeral is display-only — no tap
        // target, no haptic, no counter animation. The unit is
        // changed in Settings only.
        // GEN-2 A2 (W-7): the rendered string passes through
        // HeroNumeralPolicy — held whole during a transition, swapped
        // whole at the commit, never a partial or blended rendering.
        val candidateNumeral = weather?.let {
            HeroSheetFormat.heroNumeral(it, temperatureUnit, comfortPreference)
        }
        var heldNumeral by remember { mutableStateOf<String?>(null) }
        val shownNumeral = HeroNumeralPolicy.shown(
            held = heldNumeral,
            candidate = candidateNumeral,
            transitionInFlight = holdNumeral
        )
        LaunchedEffect(candidateNumeral, holdNumeral) {
            if (!holdNumeral && candidateNumeral != null) heldNumeral = candidateNumeral
        }
        // GEN-4: the weather figures live HERE, right-justified in
        // the hero beside the numeral — a strict two-column grid
        // (labels in one fixed column, values flush right), its top
        // aligned to the numeral's top, rows evenly spaced. Nothing
        // weather-related sits below the headline on home anymore:
        // the pills row that used to live under the pill is gone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = shownNumeral ?: "0°",
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
                Box(modifier = Modifier.padding(top = 14.dp)) {
                    TemperatureBracketChip(bracket = bracket)
                }
            }
            Spacer(Modifier.weight(1f))
            weather?.let { w ->
                Surface(
                    onClick = { onWeatherPillClick("condition") },
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
                ) {
                    HeroSheetFormat.weatherGridRows(w, temperatureUnit).forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(19.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = RunWearColors.TextSecondary,
                                modifier = Modifier.width(68.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = value,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = RunWearColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                }
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

        // GEN-5 (RW-28 item 12, A-22/B-13): the duplicate
        // DateTimePill "Today | Now" segmented row is GONE. The
        // moment lives in exactly one place — the headline line
        // above, whose moment segment opens Choose-a-Time and
        // whose compact refresh + "Now" way-back appears in the
        // same line in the changed state. (onDateClick is retained
        // in the signature for the split-pane header's legacy
        // wiring; nothing in this block renders a date row.)

        // GEN-4: no weather row below the headline — the figures
        // are in the right-justified grid beside the numeral above.
        // The weather DETAIL sheet (frames 40/41) is unchanged and
        // still opens from that grid.
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
