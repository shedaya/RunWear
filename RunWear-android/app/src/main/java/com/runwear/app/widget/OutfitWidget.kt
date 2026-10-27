package com.runwear.app.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.runwear.app.MainActivity
import com.runwear.app.R
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.shared.util.LocationProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Widget palette mirrors the phone app's kit (ui/theme RunWearColors):
// background #0A0A0A, card #262626, text #FFFFFF / #B3B3B3 / #737373,
// primary teal #00796B, and the app's per-category colors for item icons.
// Glance cannot load the app's Bebas font, so display type is Bold system
// at display size (per the widgets/watch audit re-skin spec).
private val WidgetBackground = Color(0xFF0A0A0A)
private val WidgetCard = Color(0xFF262626)
private val WidgetTextPrimary = Color(0xFFFFFFFF)
private val WidgetTextSecondary = Color(0xFFB3B3B3)
private val WidgetTextMuted = Color(0xFF737373)
private val WidgetPrimary = Color(0xFF00796B)

/**
 * Home-screen outfit widget (redesign spec §B.3).
 *
 * The widget has a MEMORY now: [provideGlance] renders the persisted
 * last-good data immediately when it exists and only pays for a reload when
 * that data is older than [WidgetFreshness.FRESH_ENOUGH_MS]. A failed reload
 * keeps the last-good on screen with its age — the error card exists only
 * for a widget that has NEVER loaded (fresh install) — and a tiny ↻ in the
 * corner of every state re-runs the load in place via [RefreshWidgetAction].
 */
class OutfitWidget : GlanceAppWidget() {

    // SizeMode.Exact since 1.2.0 (field regression, Oct 27 2026): with
    // SizeMode.Responsive the HOST chooses which of the declared size
    // buckets' RemoteViews to display and may substitute the smallest
    // bucket for a placement the largest bucket doesn't exactly fit —
    // the composition then renders at the bucket's size, not the tile's,
    // and the launcher stretches/letterboxes it. Exact hands the
    // composition the size the launcher actually reports, and
    // WidgetLayout's dispatch + slot budgets — which are written for
    // arbitrary sizes, not just the canonical buckets — do the rest.
    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val store = WidgetLastGoodStore(context)
        val lastGood = store.read()
        val nowMs = System.currentTimeMillis()

        val state: WidgetState = when {
            lastGood != null &&
                WidgetFreshness.ageMs(lastGood.savedAtEpochMs, nowMs) < WidgetFreshness.FRESH_ENOUGH_MS ->
                lastGood.toWidgetState()

            else -> when (val outcome = WidgetDataLoader.load(
                weatherRepository = entryPoint.weatherRepository(),
                preferencesRepository = entryPoint.preferencesRepository(),
                locationProvider = entryPoint.locationProvider(),
                getOutfitRecommendation = entryPoint.getOutfitRecommendation()
            )) {
                is WidgetDataLoader.Outcome.Success -> {
                    // Just persist; this very render shows the fresh data and
                    // the publisher's updateAll belongs to out-of-band writes.
                    store.write(outcome.lastGood)
                    outcome.lastGood.toWidgetState()
                }
                WidgetDataLoader.Outcome.NoPermission ->
                    lastGood?.toWidgetState() ?: WidgetState.NoPermission
                is WidgetDataLoader.Outcome.Failed ->
                    lastGood?.toWidgetState()
                        ?: WidgetState.Error(outcome.category, outcome.detail)
            }
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val refreshing = WidgetFreshness.isRefreshActive(
                prefs[RefreshFlag.KEY],
                System.currentTimeMillis()
            )
            GlanceTheme {
                WidgetContent(state, refreshing)
            }
        }
    }
}

/** The ↻ corner icon: a real in-place reload, NOT an open-app shortcut (spec §B.3.3). */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.w("OutfitWidget", "loadWidgetData: manual refresh tapped")
        RefreshFlag.set(context, glanceId)
        OutfitWidget().update(context, glanceId)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            when (val outcome = WidgetDataLoader.load(
                weatherRepository = entryPoint.weatherRepository(),
                preferencesRepository = entryPoint.preferencesRepository(),
                locationProvider = entryPoint.locationProvider(),
                getOutfitRecommendation = entryPoint.getOutfitRecommendation()
            )) {
                is WidgetDataLoader.Outcome.Success ->
                    WidgetPublisher.publish(context, outcome.lastGood)
                else -> {
                    RefreshFlag.clear(context, glanceId)
                    OutfitWidget().update(context, glanceId)
                }
            }
        } catch (e: Exception) {
            Log.e("OutfitWidget", "loadWidgetData: manual refresh failed", e)
            runCatching {
                RefreshFlag.clear(context, glanceId)
                OutfitWidget().update(context, glanceId)
            }
        }
    }
}

private fun WidgetLastGood.toWidgetState(): WidgetState.Success = WidgetState.Success(
    weather = weather,
    outfit = outfit,
    locationName = locationName,
    lastUpdated = savedAtLocal,
    savedAtEpochMs = savedAtEpochMs,
    laterTodayLine = laterTodayLine
)

@Composable
private fun WidgetContent(state: WidgetState, refreshing: Boolean) {
    val size = LocalSize.current
    // 1.1.8 size system: dispatch and slot budgets come from WidgetLayout,
    // keyed on width AND height — never width alone (the pre-1.1.8 bug).
    val kind = WidgetLayout.layoutFor(size.width.value.toInt(), size.height.value.toInt())
    val (padH, padV) = WidgetLayout.paddingDp(kind)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val innerW = size.width.value - 2 * padH
    val innerH = size.height.value - 2 * padV

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetBackground)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = padH.dp, vertical = padV.dp)
        ) {
            when (state) {
                is WidgetState.Loading -> LoadingContent(kind)
                is WidgetState.NoPermission -> NoPermissionContent(kind)
                is WidgetState.Error -> ErrorContent(state, kind)
                is WidgetState.Success -> when (kind) {
                    WidgetLayout.Kind.SMALL -> SmallWidgetContent(state, fontScale, innerW, innerH)
                    WidgetLayout.Kind.MEDIUM_SQUARE -> MediumWidgetContent(state, fontScale, innerW, innerH)
                    WidgetLayout.Kind.HORIZONTAL -> HorizontalWidgetContent(state, fontScale, innerW, innerH)
                    WidgetLayout.Kind.LARGE -> LargeWidgetContent(state, fontScale, innerW, innerH)
                }
            }
        }

        // Standing manual refresh — every state, corner, quiet (spec §B.3.3).
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(6.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            RefreshAffordance(refreshing)
        }
    }
}

@Composable
private fun RefreshAffordance(refreshing: Boolean) {
    if (refreshing) {
        CircularProgressIndicator(
            modifier = GlanceModifier.size(15.dp),
            color = ColorProvider(WidgetTextMuted)
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "Refresh",
            modifier = GlanceModifier
                .size(15.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
            colorFilter = ColorFilter.tint(ColorProvider(WidgetTextMuted))
        )
    }
}

/** Footer segments for the packed footer lines; [full] adds actual + wind (Medium/Large). */
private fun footerSegments(state: WidgetState.Success, full: Boolean): List<String> {
    val w = state.weather
    val feels = "Feels like ${formatTemperature(w.feelsLike, w.isCelsius)}"
    if (!full) return listOf(feels, w.weatherCode.description)
    return listOf(
        feels,
        "Actual ${formatTemperature(w.temperature, w.isCelsius)}",
        "Wind ${formatWind(w)}",
        w.weatherCode.description
    )
}

private fun actualSegment(state: WidgetState.Success): String =
    "Actual ${formatTemperature(state.weather.temperature, state.weather.isCelsius)}"

/** One garment row: category icon + name. The outfit is the hero at every size. */
@Composable
private fun GarmentRow(item: ClothingItem, textSp: Float, iconSizeDp: Int, bold: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(getCategoryIconRes(item.category)),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSizeDp.dp),
            colorFilter = ColorFilter.tint(ColorProvider(getCategoryColor(item.category)))
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = item.displayName,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = textSp.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

/** The combined mid+outer layer line, with the outermost layer's icon. */
@Composable
private fun LayerLine(layers: List<ClothingItem>, textSp: Float, iconSizeDp: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(getCategoryIconRes(layers.last().category)),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSizeDp.dp),
            colorFilter = ColorFilter.tint(ColorProvider(getCategoryColor(layers.last().category)))
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = layers.joinToString(" + ") { it.displayName },
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = textSp.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SmallWidgetContent(
    state: WidgetState.Success,
    fontScale: Float,
    innerW: Float,
    innerH: Float
) {
    // 1.1.8: top-anchored outfit stack with the weather line anchored to
    // the bottom — never a centered block floating in a void. Layers are
    // always shown; secondary garments get the compact "+N more" line
    // whenever the height budget allows (see WidgetLayout.planSmall).
    val outfit = state.outfit
    val plan = WidgetLayout.plan(
        kind = WidgetLayout.Kind.SMALL,
        outfit = outfit,
        laterTodayLine = null,
        footerSegments = footerSegments(state, full = false),
        droppableFooterSegment = null,
        innerWidthDp = innerW,
        innerHeightDp = innerH,
        fontScale = fontScale
    )
    val layers = listOfNotNull(outfit.topMid, outfit.topOuter)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = WidgetFreshness.smallEyebrow(
                state.lastUpdated, state.savedAtEpochMs, System.currentTimeMillis()
            ),
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = plan.eyebrowSp.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        GarmentRow(outfit.topBase, plan.heroSp, iconSizeDp = 16, bold = true)
        if (layers.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            LayerLine(layers, plan.layerSp, iconSizeDp = 14)
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        GarmentRow(outfit.bottom, plan.bottomSp, iconSizeDp = 16, bold = false)
        if (plan.secondaryLine != null) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = plan.secondaryLine,
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = plan.microSp.sp
                ),
                maxLines = 1
            )
        }
        if (plan.footerLines.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = plan.footerLines.first(),
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = plan.footerSp.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediumWidgetContent(
    state: WidgetState.Success,
    fontScale: Float,
    innerW: Float,
    innerH: Float
) {
    // The size the field screenshot proved missing: a real square
    // composition. Header (FOR NOW · time, location + updated at the
    // right), the full garment stack with icons, budgeted secondary
    // chips, later-today when it fits, weather footer anchored bottom.
    val outfit = state.outfit
    val plan = WidgetLayout.plan(
        kind = WidgetLayout.Kind.MEDIUM_SQUARE,
        outfit = outfit,
        laterTodayLine = state.laterTodayLine,
        footerSegments = footerSegments(state, full = true),
        droppableFooterSegment = actualSegment(state),
        innerWidthDp = innerW,
        innerHeightDp = innerH,
        fontScale = fontScale
    )
    val layers = listOfNotNull(outfit.topMid, outfit.topOuter)
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "FOR NOW · ${state.weather.dateTime.format(timeFormatter)}",
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = WidgetLayout.Sizes.MEDIUM_EYEBROW.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.locationName,
                    style = TextStyle(
                        color = ColorProvider(WidgetTextSecondary),
                        fontSize = WidgetLayout.Sizes.MEDIUM_LOCATION.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = WidgetFreshness.updatedLabel(
                        state.lastUpdated, state.savedAtEpochMs, System.currentTimeMillis()
                    ),
                    style = TextStyle(
                        color = ColorProvider(WidgetTextMuted),
                        fontSize = plan.metaSp.sp
                    ),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        GarmentRow(outfit.topBase, plan.heroSp, iconSizeDp = 20, bold = true)
        if (layers.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            LayerLine(layers, plan.layerSp, iconSizeDp = 16)
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        GarmentRow(outfit.bottom, plan.heroSp, iconSizeDp = 20, bold = true)
        if (plan.secondaryMode != WidgetLayout.SecondaryMode.NONE) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            SecondaryZone(plan, WidgetLayout.Sizes.MEDIUM_CHIP)
        }
        if (plan.showLaterToday && state.laterTodayLine != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = state.laterTodayLine,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = WidgetLayout.Sizes.MEDIUM_LATER.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        plan.footerLines.forEach { line ->
            Text(
                text = line,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = plan.footerSp.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HorizontalWidgetContent(
    state: WidgetState.Success,
    fontScale: Float,
    innerW: Float,
    innerH: Float
) {
    // 1.1.8: the outfit summary is the largest text in the strip again
    // (pre-fix the temperature outranked it), garment names are no longer
    // truncated to two words — the plan keeps the items whose full names
    // fit the two-line budget — and the weather stack gains the condition,
    // with the data's age appended when stale.
    val plan = WidgetLayout.plan(
        kind = WidgetLayout.Kind.HORIZONTAL,
        outfit = state.outfit,
        laterTodayLine = null,
        footerSegments = emptyList(),
        droppableFooterSegment = null,
        innerWidthDp = innerW,
        innerHeightDp = innerH,
        fontScale = fontScale
    )
    val nowMs = System.currentTimeMillis()
    val conditionLine = state.weather.weatherCode.description + if (
        WidgetFreshness.isStale(state.savedAtEpochMs, nowMs)
    ) {
        " · ${WidgetFreshness.ageShortUpper(WidgetFreshness.ageMs(state.savedAtEpochMs, nowMs))}"
    } else {
        ""
    }

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = plan.summaryItems.joinToString(" • "),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = plan.heroSp.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        // Divider
        Box(
            modifier = GlanceModifier
                .width(1.dp)
                .height(30.dp)
                .background(WidgetCard)
        ) {}

        Spacer(modifier = GlanceModifier.width(12.dp))

        // Weather as a supporting stack: feels-like, condition, wind.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTemperature(state.weather.feelsLike, state.weather.isCelsius),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = WidgetLayout.Sizes.HORIZ_TEMP.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = conditionLine,
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = WidgetLayout.Sizes.HORIZ_SUB.sp
                ),
                maxLines = 1
            )
            if (plan.showWind) {
                Text(
                    text = "Wind ${formatWind(state.weather)}",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextMuted),
                        fontSize = WidgetLayout.Sizes.HORIZ_WIND.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LargeWidgetContent(
    state: WidgetState.Success,
    fontScale: Float,
    innerW: Float,
    innerH: Float
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val outfit = state.outfit
    // 1.1.8: every optional slot is budget-decided by the plan — chips are
    // width-budgeted (no more four chips in a non-wrapping row), the
    // later-today line renders only when the height budget holds it, and
    // the footer packs into as many lines as its width needs instead of
    // truncating four facts into one.
    val plan = WidgetLayout.plan(
        kind = WidgetLayout.Kind.LARGE,
        outfit = outfit,
        laterTodayLine = state.laterTodayLine,
        footerSegments = footerSegments(state, full = true),
        droppableFooterSegment = actualSegment(state),
        innerWidthDp = innerW,
        innerHeightDp = innerH,
        fontScale = fontScale
    )
    val layers = listOfNotNull(outfit.topMid, outfit.topOuter)

    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        // Brand strip: the RunWear mark, location and freshness at the right.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(WidgetPrimary)
                    .cornerRadius(2.dp)
            ) {}
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "RunWear",
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = WidgetLayout.Sizes.LARGE_BRAND.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = state.locationName,
                    style = TextStyle(
                        color = ColorProvider(WidgetTextSecondary),
                        fontSize = WidgetLayout.Sizes.LARGE_LOCATION.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = WidgetFreshness.updatedLabel(
                        state.lastUpdated,
                        state.savedAtEpochMs,
                        System.currentTimeMillis()
                    ),
                    style = TextStyle(
                        color = ColorProvider(WidgetTextMuted),
                        fontSize = plan.metaSp.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // The outfit is the hero: the moment it is for, then the main
        // pieces at display size with their garment icons.
        Text(
            text = "FOR NOW · ${state.weather.dateTime.format(timeFormatter)}",
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = WidgetLayout.Sizes.LARGE_EYEBROW.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        GarmentRow(outfit.topBase, plan.heroSp, iconSizeDp = 20, bold = true)
        if (layers.isNotEmpty()) {
            LayerLine(layers, plan.layerSp, iconSizeDp = 16)
        }
        GarmentRow(outfit.bottom, plan.heroSp, iconSizeDp = 20, bold = true)

        // Secondary pieces keep their icon chips under the hero — as many
        // as the width budget honestly fits, in priority order, with the
        // "+N" marker for the rest (or the compact line if height forced
        // the chips out entirely).
        if (plan.secondaryMode != WidgetLayout.SecondaryMode.NONE) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            SecondaryZone(plan, WidgetLayout.Sizes.LARGE_CHIP)
        }

        // The "Later today" line — only when the height budget holds it
        // without displacing the weather footer.
        if (plan.showLaterToday && state.laterTodayLine != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = state.laterTodayLine,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = WidgetLayout.Sizes.LARGE_LATER.sp
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Weather grounding: feels-like, actual, wind, condition — packed
        // by the plan into the lines its width actually needs.
        plan.footerLines.forEach { line ->
            Text(
                text = line,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = plan.footerSp.sp
                ),
                maxLines = 1
            )
        }
    }
}

/** The "+N" marker chip: secondary items the row's width budget couldn't fit stay represented. */
@Composable
private fun OverflowChip(hiddenCount: Int, chipSp: Float) {
    Box(
        modifier = GlanceModifier
            .background(WidgetCard)
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = "+$hiddenCount",
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = chipSp.sp
            ),
            maxLines = 1
        )
    }
}

/** The plan's secondary row: budgeted chips plus the overflow marker. */
@Composable
private fun SecondaryChipsRow(plan: WidgetLayout.SlotPlan, chipSp: Float) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        plan.chips.forEachIndexed { index, item ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
            OutfitItemChip(item)
        }
        if (plan.showOverflowMarker) {
            if (plan.chips.isNotEmpty()) Spacer(modifier = GlanceModifier.width(6.dp))
            OverflowChip(plan.chipsHiddenCount, chipSp)
        }
    }
}

/** Renders the plan's secondary zone exactly as the plan decided it. */
@Composable
private fun SecondaryZone(plan: WidgetLayout.SlotPlan, chipSp: Float) {
    when (plan.secondaryMode) {
        WidgetLayout.SecondaryMode.CHIPS -> SecondaryChipsRow(plan, chipSp)
        WidgetLayout.SecondaryMode.LINE -> Text(
            text = plan.secondaryLine ?: "",
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = WidgetLayout.Sizes.SECONDARY_LINE.sp
            ),
            maxLines = 1
        )
        WidgetLayout.SecondaryMode.NONE -> {}
    }
}

@Composable
private fun OutfitItemChip(item: ClothingItem) {
    Row(
        modifier = GlanceModifier
            .background(WidgetCard)
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(getCategoryIconRes(item.category)),
            contentDescription = null,
            modifier = GlanceModifier.size(14.dp),
            colorFilter = ColorFilter.tint(ColorProvider(getCategoryColor(item.category)))
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = item.displayName,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

/**
 * Non-success states are size-aware (1.1.8): at Small the compact centered
 * treatment is honest for 100×100; in a strip the state is a single row;
 * at Medium/Large the state uses the same top-anchored shell hierarchy as
 * Success instead of a small block floating in a big black rectangle.
 */
@Composable
private fun LoadingContent(kind: WidgetLayout.Kind) {
    when (kind) {
        WidgetLayout.Kind.SMALL -> CenteredStateText("RunWear", "Loading…")
        WidgetLayout.Kind.HORIZONTAL -> StripStateText("RunWear · Loading…")
        WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE ->
            AnchoredStateText("Loading your outfit…", null)
    }
}

@Composable
private fun NoPermissionContent(kind: WidgetLayout.Kind) {
    when (kind) {
        WidgetLayout.Kind.SMALL -> CenteredStateText("Location needed", "Tap to enable")
        WidgetLayout.Kind.HORIZONTAL -> StripStateText("Location needed · Tap to enable")
        WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE ->
            AnchoredStateText("Location needed", "Tap to enable location")
    }
}

/**
 * Never-succeeded-only (spec §B.3.1): a fresh install that has never loaded
 * gets the plain-words reason and a REAL retry — never two generic lines
 * that wait for the user to fix the widget.
 */
@Composable
private fun ErrorContent(state: WidgetState.Error, kind: WidgetLayout.Kind) {
    when (kind) {
        WidgetLayout.Kind.SMALL -> Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.category.headline,
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = state.category.reason,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            RetryRow()
        }
        WidgetLayout.Kind.HORIZONTAL -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.category.reason,
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            RetryRow()
        }
        WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE -> Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            StateBrandRow()
            Spacer(modifier = GlanceModifier.height(10.dp))
            Text(
                text = state.category.headline,
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = state.category.reason,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 12.sp
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            RetryRow()
        }
    }
}

@Composable
private fun CenteredStateText(headline: String, sub: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headline,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = sub,
            style = TextStyle(
                color = ColorProvider(WidgetPrimary),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun StripStateText(text: String) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

/** Medium/Large state shell: the Success brand row, then the message in the content zone. */
@Composable
private fun AnchoredStateText(headline: String, sub: String?) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StateBrandRow()
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = headline,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2
        )
        if (sub != null) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = sub,
                style = TextStyle(
                    color = ColorProvider(WidgetPrimary),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StateBrandRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(WidgetPrimary)
                .cornerRadius(2.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "RunWear",
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun RetryRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>())
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = null,
            modifier = GlanceModifier.size(13.dp),
            colorFilter = ColorFilter.tint(ColorProvider(WidgetPrimary))
        )
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(
            text = "Retry",
            style = TextStyle(
                color = ColorProvider(WidgetPrimary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun formatTemperature(value: Double, isCelsius: Boolean): String {
    return if (isCelsius) {
        "${value.toInt()}°C"
    } else {
        "${value.toInt()}°F"
    }
}

/** Wind in the app's own unit convention (WeatherConditionsRow): mph, or km/h when Celsius. */
private fun formatWind(weather: WeatherConditions): String {
    return "${weather.windSpeed.toInt()} ${if (weather.isCelsius) "km/h" else "mph"}"
}

/** Category accent color — mirrors the phone app's kit (ui/theme RunWearColors). */
private fun getCategoryColor(category: ClothingCategory): Color = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER ->
        Color(0xFF00796B)
    ClothingCategory.BOTTOM -> Color(0xFF1565C0)
    ClothingCategory.HEAD -> Color(0xFF2E7D32)
    ClothingCategory.HANDS -> Color(0xFF5E35B1)
    ClothingCategory.ACCESSORIES -> Color(0xFFE65100)
}

/** Category icon drawable — mirrors the phone app's OutfitCard mapping. */
private fun getCategoryIconRes(category: ClothingCategory): Int = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER ->
        R.drawable.ic_category_top
    ClothingCategory.BOTTOM -> R.drawable.ic_category_bottom
    ClothingCategory.HEAD -> R.drawable.ic_category_head
    ClothingCategory.HANDS -> R.drawable.ic_category_hands
    ClothingCategory.ACCESSORIES -> R.drawable.ic_category_accessories
}

sealed class WidgetState {
    object Loading : WidgetState()
    object NoPermission : WidgetState()
    data class Error(
        val category: WidgetErrorCategory,
        /** Raw diagnostic detail — logged, and kept on the state for debugging; the UI shows [category] copy. */
        val detail: String
    ) : WidgetState()
    data class Success(
        val weather: WeatherConditions,
        val outfit: OutfitRecommendation,
        val locationName: String,
        val lastUpdated: LocalDateTime,
        val savedAtEpochMs: Long,
        val laterTodayLine: String?
    ) : WidgetState()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun preferencesRepository(): PreferencesRepository
    fun locationProvider(): LocationProvider
    fun getOutfitRecommendation(): GetOutfitRecommendationUseCase
}

class OutfitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OutfitWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First placement: make sure the recovery backstop exists even if
        // the app itself hasn't been opened yet.
        WidgetRefreshScheduler.schedule(context)
    }
}
