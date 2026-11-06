package com.runwear.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.components.DayChartFormat
import com.runwear.app.ui.components.DayChartGeometry
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.temperatureBracketColor
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * GEN-4 Choose-a-Time draft semantics — pure, unit-tested.
 *
 * Opening the sheet SNAPSHOTS the current selection. Scrubbing,
 * tapping and stepping move a DRAFT hour only; home does not
 * re-solve behind the sheet. Confirm commits; Cancel restores the
 * pre-open selection exactly (the draft is sheet-local state, so
 * restoring is simply never committing it).
 *
 * NOW interpretation (pinned by test): confirming the current hour
 * while in the NOW state on the location's today, WITHOUT having
 * moved the handle off the NOW position at any point, KEEPS the NOW
 * state. Any other confirm — a different hour, or the NOW hour
 * reached after moving away and back — is an explicit pick and exits
 * NOW via the existing setDateTime semantics.
 */
data class TimePickerSession(
    val originalHour: Int,
    val originalIsNow: Boolean,
    val isToday: Boolean,
    val nowHour: Int?,
    val draftHour: Int,
    val hasMovedOffNow: Boolean,
    val selectableHours: Set<Int>
)

object TimePickerDraftPolicy {

    data class Confirm(val hour: Int, val keepNow: Boolean)

    fun open(
        selectedHour: Int,
        isNowSelected: Boolean,
        isToday: Boolean,
        nowHour: Int?,
        selectableHours: Set<Int>
    ): TimePickerSession {
        // GEN-6 WAVE C (RW-53(iii) / FA-32): on a day that is NOT
        // today the handle opens on that day's FIRST forecast hour
        // — the carried-over selected hour belonged to another day.
        val wanted = when {
            isNowSelected && isToday && nowHour != null -> nowHour
            !isToday -> selectableHours.minOrNull() ?: selectedHour
            else -> selectedHour
        }
        val draft = snap(wanted, selectableHours) ?: wanted
        return TimePickerSession(
            originalHour = selectedHour,
            originalIsNow = isNowSelected,
            isToday = isToday,
            nowHour = nowHour,
            draftHour = draft,
            hasMovedOffNow = false,
            selectableHours = selectableHours
        )
    }

    /** Move the draft only — the committed selection is untouched. */
    fun moveTo(session: TimePickerSession, hour: Int): TimePickerSession {
        val target = snap(hour, session.selectableHours) ?: return session
        if (target == session.draftHour) return session
        return session.copy(
            draftHour = target,
            hasMovedOffNow = session.hasMovedOffNow ||
                (session.nowHour != null && target != session.nowHour)
        )
    }

    /**
     * GEN-5 (RW-29 FA-39): stepping moves through the SELECTABLE
     * hours by index — the gen-4 step walked clock hours, so at
     * a forecast gap the plus/minus dead-ended (the adjacent
     * clock hour was unselectable and the snap pulled the draft
     * straight back), stranding the user mid-day. The ends of
     * the selectable list stop, as the ends of the day did.
     */
    fun step(session: TimePickerSession, delta: Int): TimePickerSession {
        val sorted = session.selectableHours.sorted()
        val index = sorted.indexOf(session.draftHour)
        if (index < 0) return session
        val next = sorted.getOrNull(index + delta) ?: return session
        return moveTo(session, next)
    }

    fun canStep(session: TimePickerSession, delta: Int): Boolean {
        val sorted = session.selectableHours.sorted()
        val index = sorted.indexOf(session.draftHour)
        return index >= 0 && (index + delta) in sorted.indices
    }

    fun confirm(session: TimePickerSession): Confirm {
        val keepNow = session.originalIsNow && session.isToday &&
            session.nowHour != null && session.draftHour == session.nowHour &&
            !session.hasMovedOffNow
        return Confirm(hour = session.draftHour, keepNow = keepNow)
    }

    /** Cancel restores the pre-open selection exactly. */
    fun cancel(session: TimePickerSession): Int = session.originalHour

    private fun snap(hour: Int, selectable: Set<Int>): Int? {
        if (selectable.isEmpty()) return null
        if (hour in selectable) return hour
        return selectable.minByOrNull { kotlin.math.abs(it - hour) }
    }
}

/**
 * GEN-4: the Choose-a-Time sheet — the chart replaces the HourGrid
 * (the tile grid is retired). Every existing entry opens this same
 * sheet: the DateTimePill's time segment, the glance band's Change
 * button, and the headline line's moment segment.
 *
 * Gesture safety: the chart claims HORIZONTAL drags only
 * ([detectHorizontalDragGestures] consumes the horizontal axis and
 * taps via [detectTapGestures]); the sheet's own dismiss/scroll
 * gestures are vertical, so the scrub cannot fight them — the two
 * gesture families own disjoint axes on the chart region.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    model: DayChartModel,
    selectedHour: Int,
    locationNow: LocalDateTime,
    isNowSelected: Boolean,
    temperatureUnit: TemperatureUnit,
    locationName: String,
    onConfirm: (TimePickerDraftPolicy.Confirm) -> Unit,
    onDismiss: () -> Unit,
    /**
     * GEN-6 WAVE C (RW-53, date-path fix): the day door — the
     * context line's day word opens the sibling Choose-a-Day
     * sheet (design-spec §6: the day picker "remains its
     * sibling"). Without it the date was unreachable from the
     * phone layout in every state.
     */
    onDayClick: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { DayChartDragHandle() }
    ) {
        TimePickerSheetContent(
            model = model,
            selectedHour = selectedHour,
            locationNow = locationNow,
            isNowSelected = isNowSelected,
            temperatureUnit = temperatureUnit,
            locationName = locationName,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onDayClick = onDayClick
        )
    }
}

/**
 * The Choose-a-Time sheet's content, extracted from the
 * ModalBottomSheet host (a ModalBottomSheet cannot be driven
 * to Expanded under Robolectric — the same extraction the
 * Choose-a-Day content uses in BottomSheets.kt) so the RW-73
 * detent-ruling render proofs can drive the production
 * content directly.
 */
@Composable
internal fun TimePickerSheetContent(
    model: DayChartModel,
    selectedHour: Int,
    locationNow: LocalDateTime,
    isNowSelected: Boolean,
    temperatureUnit: TemperatureUnit,
    locationName: String,
    onConfirm: (TimePickerDraftPolicy.Confirm) -> Unit,
    onDismiss: () -> Unit,
    onDayClick: () -> Unit = {},
    // Hoisted so render proofs can drive the ruled scroll
    // deterministically; the production host uses the default.
    scrollState: ScrollState = rememberScrollState()
) {
    val haptic = LocalHapticFeedback.current
    val isToday = model.date == locationNow.toLocalDate()
    var session by remember(model.date, selectedHour, isNowSelected) {
        mutableStateOf(
            TimePickerDraftPolicy.open(
                selectedHour = selectedHour,
                isNowSelected = isNowSelected,
                isToday = isToday,
                nowHour = if (isToday) locationNow.hour else null,
                selectableHours = model.selectableHours
            )
        )
    }

    // One haptic tick per hour crossed.
    var lastTickedHour by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(session.draftHour) {
        val last = lastTickedHour
        if (last != null && last != session.draftHour) {
            haptic.performHapticFeedback(HapticSpec.SELECTION)
        }
        lastTickedHour = session.draftHour
    }

    val draftPoint = model.point(session.draftHour)
    val readout = draftPoint?.let {
        DayChartFormat.readout(it.hour, it.feelsLikeF, temperatureUnit)
    } ?: DayChartFormat.hourLabel(session.draftHour)
    val low = model.low
    val high = model.high
    val showNowLine = isToday && low != null

    // RW-73 detent ruling: the one vertical scroll consumer,
    // present only at the largest font scale (see
    // PickerSheetScrollPolicy in BottomSheets.kt).
    val scrollable = PickerSheetScrollPolicy.scrollable(LocalDensity.current.fontScale)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Choose a Time",
                style = MaterialTheme.typography.titleLarge,
                color = RunWearColors.TextPrimary
            )
            // Context line — the day word is the Choose-a-Day
            // door (RW-53): "Today › · {Place} · Feels-like by hour".
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                androidx.compose.material3.Surface(
                    onClick = onDayClick,
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Text(
                        text = "${dayLabel(model.date, locationNow.toLocalDate())} ›",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunWearColors.PineAccent
                    )
                }
                Text(
                    text = " · ${com.runwear.app.ui.components.HeroSheetFormat.simplePlaceName(locationName)}" +
                        " · Feels-like by hour",
                    fontSize = 12.5.sp,
                    color = RunWearColors.TextSecondary
                )
            }
            // At most ONE short instruction line.
            Text(
                // RW-53(i): the settled instruction line
                // (design-spec §6; iOS carries it verbatim).
                text = "Every hour can be chosen — the handle snaps hour to hour.",
                fontSize = 13.sp,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Readout row: −/+ steppers (48dp) flanking the readout.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperButton(
                    label = "−",
                    contentDescription = "Previous hour",
                    enabled = TimePickerDraftPolicy.canStep(session, -1),
                    onClick = { session = TimePickerDraftPolicy.step(session, -1) }
                )
                Text(
                    text = readout,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunWearColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                StepperButton(
                    label = "+",
                    contentDescription = "Next hour",
                    enabled = TimePickerDraftPolicy.canStep(session, +1),
                    onClick = { session = TimePickerDraftPolicy.step(session, +1) }
                )
            }

            if (model.isEmpty || low == null || high == null) {
                Text(
                    text = "Beyond forecast — no weather is invented for this day.",
                    fontSize = 13.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                // The chart: full-height hit area, scrub + tap.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .semantics {
                            contentDescription = "Hour of day"
                            stateDescription = readout
                            val selectable = model.selectableHours
                            if (selectable.isNotEmpty()) {
                                progressBarRangeInfo = ProgressBarRangeInfo(
                                    current = session.draftHour.toFloat(),
                                    range = selectable.min().toFloat()..selectable.max().toFloat(),
                                    steps = 0
                                )
                            }
                            setProgress { target ->
                                session = TimePickerDraftPolicy.moveTo(session, target.toInt())
                                true
                            }
                        }
                        .pointerInput(model.selectableHours) {
                            detectTapGestures { offset ->
                                val fraction = offset.x / size.width.toFloat()
                                DayChartGeometry.hourFromXFraction(fraction, model.selectableHours)
                                    ?.let { session = TimePickerDraftPolicy.moveTo(session, it) }
                            }
                        }
                        .pointerInput(model.selectableHours) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val fraction = change.position.x / size.width.toFloat()
                                DayChartGeometry.hourFromXFraction(fraction, model.selectableHours)
                                    ?.let { session = TimePickerDraftPolicy.moveTo(session, it) }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // GEN-5 (item 15): night shading under
                        // everything — the settled fixed night
                        // window (20:00–05:59; the forecast carries
                        // no sunrise/sunset to shade by).
                        // RW-53(ii): sunrise–sunset shading;
                        // the fixed window only when the model
                        // carries no sun times.
                        for ((nightStartMinute, nightEndMinute) in
                            com.runwear.app.ui.components.DayChartBandPolicy.nightRanges(model)) {
                            val left = size.width * (nightStartMinute / (24f * 60f))
                            val right = size.width * (nightEndMinute / (24f * 60f))
                            drawRect(
                                color = RunWearColors.TextPrimary.copy(alpha = 0.07f),
                                topLeft = Offset(left, 0f),
                                size = Size(right - left, size.height)
                            )
                        }
                        // Bracket bands.
                        for (band in model.bands) {
                            val left = DayChartGeometry.cellLeftFraction(band.startHour) * size.width
                            val right = DayChartGeometry.cellLeftFraction(band.endHourExclusive) * size.width
                            drawRect(
                                color = temperatureBracketColor(band.bracket).copy(alpha = 0.13f),
                                topLeft = Offset(left, 0f),
                                size = Size(right - left, size.height)
                            )
                        }
                        // Beyond-the-forecast region: the curve ends
                        // at the horizon; past it is dimmed, never drawn.
                        val horizon = model.horizonLastHour
                        if (horizon != null && horizon < 23) {
                            val left = DayChartGeometry.cellLeftFraction(horizon + 1) * size.width
                            drawRect(
                                color = RunWearColors.TextSecondary.copy(alpha = 0.10f),
                                topLeft = Offset(left, 0f),
                                size = Size(size.width - left, size.height)
                            )
                        }
                        // Outfit-change ticks — BARE (a small tick at
                        // the chart's foot, no prose labels).
                        for (changeHour in model.changeHours) {
                            val x = DayChartGeometry.xFraction(changeHour) * size.width
                            drawLine(
                                color = RunWearColors.TextSecondary.copy(alpha = 0.6f),
                                start = Offset(x, size.height - 18f),
                                end = Offset(x, size.height - 4f),
                                strokeWidth = 3f
                            )
                        }
                        // NOW line (location's today only).
                        if (showNowLine) {
                            val x = DayChartGeometry.xFraction(locationNow.hour) * size.width
                            drawLine(
                                color = RunWearColors.PineAccent.copy(alpha = 0.55f),
                                start = Offset(x, 10f),
                                end = Offset(x, size.height - 10f),
                                strokeWidth = 2f
                            )
                        }
                        // The curve.
                        val path = Path()
                        model.hours.forEachIndexed { i, p ->
                            val x = DayChartGeometry.xFraction(p.hour) * size.width
                            val y = DayChartGeometry.yFraction(
                                p.feelsLikeF, low.feelsLikeF, high.feelsLikeF
                            ) * (size.height - 32f) + 16f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path = path, color = RunWearColors.Primary, style = Stroke(width = 5f))
                        // Handle: a line + dot at the drafted hour.
                        if (draftPoint != null) {
                            val hx = DayChartGeometry.xFraction(draftPoint.hour) * size.width
                            val hy = DayChartGeometry.yFraction(
                                draftPoint.feelsLikeF, low.feelsLikeF, high.feelsLikeF
                            ) * (size.height - 32f) + 16f
                            drawLine(
                                color = RunWearColors.TextSecondary.copy(alpha = 0.35f),
                                start = Offset(hx, 10f),
                                end = Offset(hx, size.height - 10f),
                                strokeWidth = 2f
                            )
                            drawCircle(color = RunWearColors.Surface, radius = 20f, center = Offset(hx, hy))
                            drawCircle(
                                color = RunWearColors.PineAccent,
                                radius = 17f,
                                center = Offset(hx, hy),
                                style = Stroke(width = 6f)
                            )
                            drawCircle(color = RunWearColors.PineAccent, radius = 6f, center = Offset(hx, hy))
                        }
                    }
                    if (showNowLine) {
                        Text(
                            text = "NOW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = RunWearColors.PineAccent,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = (
                                        DayChartGeometry.xFraction(locationNow.hour) * maxWidth.value - 10
                                        ).dp.coerceAtLeast(0.dp)
                                )
                        )
                    }
                    if (model.horizonLastHour != null && model.horizonLastHour < 23) {
                        Text(
                            text = "Beyond forecast",
                            fontSize = 10.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                    // GEN-5 (item 15): the per-hour condition
                    // glyphs along the chart's top — the hour's own
                    // weather code icon, at the hour's axis position.
                    for (point in model.hours) {
                        if (point.weatherCode !=
                            com.runwear.shared.domain.model.WeatherCode.UNKNOWN
                        ) {
                            Text(
                                text = point.weatherCode.icon,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = (
                                            DayChartGeometry.xFraction(point.hour) *
                                                maxWidth.value - 5
                                            ).dp.coerceAtLeast(0.dp),
                                        top = 1.dp
                                    )
                            )
                        }
                    }
                }

                // Axis labels.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach { label ->
                        Text(text = label, fontSize = 10.sp, color = RunWearColors.TextSecondary)
                    }
                }
                // Low / high endpoints.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${temperatureUnit.displayValue(low.feelsLikeF)}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunWearColors.TextPrimary
                    )
                    Text(
                        text = "${temperatureUnit.displayValue(high.feelsLikeF)}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunWearColors.TextPrimary
                    )
                }

                // The drafted hour's outfit preview line.
                if (draftPoint != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = draftPoint.bracket.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = temperatureBracketColor(draftPoint.bracket),
                            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                        )
                        Text(
                            text = draftPoint.outfitItems.joinToString(" · "),
                            fontSize = 13.5.sp,
                            color = RunWearColors.TextPrimary
                        )
                    }
                }
            }

            // Confirm / Cancel.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onConfirm(TimePickerDraftPolicy.confirm(session)) },
                    enabled = !model.isEmpty,
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "Confirm", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
}

/** The sheet's drag handle (BottomSheets' own handle is file-private). */
@Composable
private fun DayChartDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 5.dp)
                .background(
                    color = RunWearColors.TextSecondary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(3.dp)
                )
        )
    }
}

@Composable
private fun StepperButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.35f), CircleShape)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = label,
            fontSize = 21.sp,
            color = if (enabled) RunWearColors.TextPrimary else RunWearColors.TextSecondary.copy(alpha = 0.4f)
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.plusDays(1) -> "Tomorrow"
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
}
