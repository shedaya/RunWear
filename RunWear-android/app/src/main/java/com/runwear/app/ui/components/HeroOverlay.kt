package com.runwear.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.HeroChromeInk
import com.runwear.app.ui.theme.HeroScrimInk
import com.runwear.app.ui.theme.PhotoInk
import com.runwear.app.ui.theme.temperatureBracketColor
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RW-144 hero overlay text forms — pure, unit-tested. The chip's
 * date segment always names the day ("Sun, Nov 8", the v8 frame's
 * form — including for today: the Now state is carried by the TIME
 * segment reading "Now", per RW-143's Now wording ruling).
 */
object HeroChipFormat {

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun dateText(selectedDateTime: LocalDateTime): String =
        selectedDateTime.format(DATE_FORMAT)

    /** "Now" in the Now state; the picked clock time otherwise. */
    fun timeText(selectedDateTime: LocalDateTime, isNowSelected: Boolean): String =
        if (isNowSelected) "Now" else selectedDateTime.format(TIME_FORMAT)

    /** The return segment exists ONLY while the selection differs from Now. */
    fun showNowReturn(isNowSelected: Boolean): Boolean = !isNowSelected
}

/** RW-144 chip geometry, translated from the v7/v8 mock proportions
 * (mock canvas px ÷ 3 = pt ≈ dp; heights raised to the platform's
 * legible touch scale — see the lane report's deviation list). */
object HeroChipGeometry {
    val HEIGHT = 34.dp
    /** Clear space on EACH side of the divider (v7 rule: 48 mock-pt). */
    val DIVIDER_CLEAR_SPACE = 17.dp
    /** Divider inset from the pill's top/bottom (17 mock-pt ≈ 6dp). */
    val DIVIDER_INSET = 6.dp
    val FILL_ALPHA = 72f / 255f
    val OUTLINE_ALPHA = 26f / 255f
    val DIVIDER_ALPHA = 96f / 255f
}

/**
 * RW-144: the hero's ONE translucent date/time pill with TWO
 * targets (RW-143 two-target ruling, round-5 single-pill form,
 * round-7 widened divider separation):
 *  - date segment (clock glyph + date + chevron) → the DATE PICKER;
 *  - divider standing in open space (clear space each side);
 *  - time segment ("Now" or the picked time + chevron) → the
 *    HOURLY CHART (which itself links to the same date picker);
 *  - picked state only: a "↻ Now" return segment after the time.
 * All text/glyphs are the cream #F5F1EA; fill is the warm-black
 * chrome ink (24,20,16) at α72, outline cream at α26, divider
 * cream at α96 (v8 palette corrections applied).
 */
@Composable
fun HeroDateTimeChip(
    selectedDateTime: LocalDateTime,
    isNowSelected: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = HeroChromeInk.copy(alpha = HeroChipGeometry.FILL_ALPHA),
        border = BorderStroke(
            1.dp, PhotoInk.copy(alpha = HeroChipGeometry.OUTLINE_ALPHA)
        )
    ) {
        Row(
            modifier = Modifier.height(HeroChipGeometry.HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date segment — its own target (the date picker).
            Surface(
                onClick = onDateClick,
                color = Color.Transparent,
                shape = RoundedCornerShape(100.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Select date",
                        tint = PhotoInk,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = HeroChipFormat.dateText(selectedDateTime),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PhotoInk,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = " ›",
                        fontSize = 13.sp,
                        color = PhotoInk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            // Divider in open space — the v7 separation rule.
            Spacer(Modifier.width(HeroChipGeometry.DIVIDER_CLEAR_SPACE))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(HeroChipGeometry.HEIGHT - HeroChipGeometry.DIVIDER_INSET * 2)
                    .background(PhotoInk.copy(alpha = HeroChipGeometry.DIVIDER_ALPHA))
            )
            Spacer(Modifier.width(HeroChipGeometry.DIVIDER_CLEAR_SPACE))
            // Time segment — its own target (the hourly chart).
            Surface(
                onClick = onTimeClick,
                color = Color.Transparent,
                shape = RoundedCornerShape(100.dp)
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 4.dp,
                        end = if (HeroChipFormat.showNowReturn(isNowSelected)) 4.dp else 12.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = HeroChipFormat.timeText(selectedDateTime, isNowSelected),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PhotoInk,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = " ›",
                        fontSize = 13.sp,
                        color = PhotoInk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            // Return segment — picked state ONLY.
            if (HeroChipFormat.showNowReturn(isNowSelected)) {
                Surface(
                    onClick = onNowClick,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 2.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Return to now",
                            tint = PhotoInk,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PhotoInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * RW-144: the feels-like scrim zone's paint — ink (14,11,8) with
 * the mock's vertical profile (notes-v6): α0 at the zone top,
 * rising to [peakAlpha] by 28% of the zone height, holding, then
 * easing to 65% of peak at the bottom edge. [peakAlpha] is the
 * LUMINANCE-DRIVEN strength (HeroLuminancePolicy.scrimPeakAlpha),
 * never a constant — the mock's 0.73 is its floor.
 */
@Composable
fun HeroFeelsLikeScrim(
    peakAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to HeroScrimInk.copy(alpha = 0f),
                    0.28f to HeroScrimInk.copy(alpha = peakAlpha),
                    0.75f to HeroScrimInk.copy(alpha = peakAlpha),
                    1.00f to HeroScrimInk.copy(alpha = peakAlpha * 0.65f)
                )
            )
        )
    )
}

/**
 * RW-144: the feels-like block on the hero's foot — "FEELS LIKE"
 * label (letterspaced cream), the giant numeral in the DYNAMIC
 * BAND COLOUR with the PWA's soft shadow, the band dot + tracked
 * band word in band colour beside it, and the "Actual NN°" line
 * in cream (value bold). The numeral reads the same
 * comfort-adjusted frame as every other surface
 * (HeroSheetFormat.heroNumeral / bracketFor — one frame rule).
 */
@Composable
fun HeroFeelsLikeBlock(
    weather: WeatherConditions,
    temperatureUnit: TemperatureUnit,
    comfortPreference: com.runwear.shared.domain.model.ComfortPreference,
    modifier: Modifier = Modifier
) {
    val bracket = HeroSheetFormat.bracketFor(weather, comfortPreference)
    val bandColor = temperatureBracketColor(bracket)
    val numeral = HeroSheetFormat.heroNumeral(weather, temperatureUnit, comfortPreference)
    Column(modifier = modifier) {
        Text(
            text = "FEELS LIKE",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.2.sp,
            color = PhotoInk,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = numeral,
                style = TextStyle(
                    fontSize = 100.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 80f / 255f),
                        offset = Offset(0f, 5f),
                        blurRadius = 18f
                    )
                ),
                color = bandColor,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier.padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(bandColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = HeroBand.word(bracket),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    color = bandColor,
                    maxLines = 1
                )
            }
        }
        val actualText = HeroSheetFormat.caption(weather, temperatureUnit)
        val valueStart = actualText.indexOfFirst { it.isDigit() }
        Text(
            text = buildAnnotatedString {
                if (valueStart > 0) {
                    append(actualText.substring(0, valueStart))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(actualText.substring(valueStart))
                    }
                } else {
                    append(actualText)
                }
            },
            fontSize = 15.sp,
            color = PhotoInk,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1
        )
    }
}
