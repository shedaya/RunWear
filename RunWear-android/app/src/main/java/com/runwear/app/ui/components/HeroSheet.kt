package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    const val FEELS_LIKE_LABEL: String = com.runwear.app.i18n.AppEnglish.HERO_FEELS_LIKE_LABEL

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
    /**
     * GEN-6 WAVE C (RW-53, date-path fix): the headline moment's
     * DATE segment text — "Today" / "Tomorrow" / "EEE, MMM d".
     * The changed-state headline renders this as its OWN tappable
     * segment opening the date sheet, beside the time segment;
     * concatenated with ", " + [timeSegmentText] it reproduces
     * [momentLabel] exactly, so the line reads as it always did.
     */
    fun dateSegmentText(selectedDateTime: LocalDateTime, locationNow: LocalDateTime): String =
        when {
            selectedDateTime.toLocalDate() == locationNow.toLocalDate() -> com.runwear.app.i18n.AppStringsPure.dayToday()
            selectedDateTime.toLocalDate() == locationNow.toLocalDate().plusDays(1) -> com.runwear.app.i18n.AppStringsPure.dayTomorrow()
            else -> com.runwear.app.i18n.LocaleFormats.dateWeekdayMonthDay(selectedDateTime)
        }

    /** The headline moment's TIME segment text ("7:00 AM"). */
    fun timeSegmentText(selectedDateTime: LocalDateTime): String =
        com.runwear.app.i18n.LocaleFormats.timeShort(selectedDateTime)

    fun momentLabel(selectedDateTime: LocalDateTime, locationNow: LocalDateTime): String {
        val timeText = com.runwear.app.i18n.LocaleFormats.timeShort(selectedDateTime)
        return when {
            selectedDateTime.toLocalDate() == locationNow.toLocalDate() -> timeText
            selectedDateTime.toLocalDate() == locationNow.toLocalDate().plusDays(1) -> "${com.runwear.app.i18n.AppStringsPure.dayTomorrow()}, $timeText"
            else -> "${com.runwear.app.i18n.LocaleFormats.dateWeekdayMonthDay(selectedDateTime)}, $timeText"
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
 * RW-144: the sheet's opening weather line — BOXLESS (no card):
 * four stats spread across the sheet's width, each an icon + value
 * above its small tracked label: WIND (mph), RAIN (%),
 * HUMIDITY (%), UV ("0 of 11" form). Icons take the theme's pine
 * accent (pine #175E4C in light, #5CC79F in dark — the PineAccent
 * token). Values are the engine state's own figures, projected
 * through the render-time unit via [HeroSheetFormat].
 */
@Composable
fun WeatherLine(
    weather: WeatherConditions?,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * HERO REDESIGN (Nov 9, 2026): the line's seat moved onto
     * the hero's foot (it opened the sheet through RW-144).
     * Same content, order and tap behaviour; over a photograph
     * the ink switches to the hero's PhotoInk language (cream
     * values/icons, softened labels, glyph shadows) — legibility
     * is carried by the hero foot scrim beneath it, whose
     * luminance drive covers this zone (HeroSection samples the
     * foot region for it).
     */
    onHero: Boolean = false
) {
    if (weather == null) return
    val rows = HeroSheetFormat.weatherGridRows(weather, temperatureUnit)
    val icons = listOf(
        Icons.Outlined.Air,
        Icons.Outlined.WaterDrop,
        Icons.Outlined.Opacity,
        Icons.Outlined.WbSunny
    )
    val valueColor = if (onHero) {
        com.runwear.app.ui.theme.PhotoInk
    } else {
        RunWearColors.TextPrimary
    }
    val labelColor = if (onHero) {
        com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.72f)
    } else {
        RunWearColors.TextSecondary
    }
    val iconTint = if (onHero) {
        com.runwear.app.ui.theme.PhotoInk
    } else {
        RunWearColors.PineAccent
    }
    val inkShadow = if (onHero) {
        androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
            blurRadius = 6f
        )
    } else {
        null
    }
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = value,
                            style = TextStyle(
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                fontFeatureSettings = "tnum",
                                shadow = inkShadow
                            ),
                            color = valueColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            shadow = inkShadow
                        ),
                        color = labelColor,
                        modifier = Modifier.padding(top = 3.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * RW-144: the home SHEET's opening block — the boxless weather
 * line over a hairline, on the sheet surface. Everything the
 * pre-RW-144 block carried (feels-like label, location line, the
 * giant numeral, the bracket chip, the figures grid) moved to the
 * hero in the redesign (HeroSection / HeroOverlay); the sheet now
 * opens with weather, then the outfit (the host's next items).
 *
 * @param topPadding clearance above the weather line — the stacked
 * host passes room for the Make It Me pill straddling the
 * hero/sheet boundary; the split pane passes its plain inset.
 */
@Composable
fun HeroSheetBlock(
    weather: WeatherConditions?,
    onWeatherPillClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    topPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    Column(
        modifier = modifier
            .background(RunWearColors.Surface)
            .padding(horizontal = 20.dp)
            .padding(top = topPadding, bottom = 14.dp)
    ) {
        WeatherLine(
            weather = weather,
            temperatureUnit = temperatureUnit,
            onClick = { onWeatherPillClick("condition") }
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RunWearColors.Hairline)
        )
    }
}
