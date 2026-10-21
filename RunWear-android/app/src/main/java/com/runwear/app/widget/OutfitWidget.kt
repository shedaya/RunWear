package com.runwear.app.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 60.dp)
        private val LARGE_RECTANGLE = DpSize(250.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL_SQUARE, HORIZONTAL_RECTANGLE, LARGE_RECTANGLE)
    )

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

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetBackground)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            when (state) {
                is WidgetState.Loading -> LoadingContent()
                is WidgetState.NoPermission -> NoPermissionContent()
                is WidgetState.Error -> ErrorContent(state)
                is WidgetState.Success -> {
                    when {
                        size.width >= 250.dp && size.height >= 150.dp -> LargeWidgetContent(state)
                        size.width >= 250.dp -> HorizontalWidgetContent(state)
                        else -> SmallWidgetContent(state)
                    }
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

@Composable
private fun SmallWidgetContent(state: WidgetState.Success) {
    // The outfit is the product — it leads even at this size. The weather
    // is demoted to one small grounding line at the bottom.
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = WidgetFreshness.forNowEyebrow(state.savedAtEpochMs, System.currentTimeMillis()),
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = state.outfit.topBase.displayName,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 2
        )
        Text(
            text = state.outfit.bottom.displayName,
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Feels like ${formatTemperature(state.weather.feelsLike, state.weather.isCelsius)}" +
                " · ${state.weather.weatherCode.description}",
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun HorizontalWidgetContent(state: WidgetState.Success) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Outfit summary leads — largest text in the strip.
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = WidgetFreshness.forNowEyebrow(state.savedAtEpochMs, System.currentTimeMillis()),
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = buildOutfitSummary(state.outfit),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 14.sp,
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

        // Weather compressed to a supporting stack: feels-like + wind.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTemperature(state.weather.feelsLike, state.weather.isCelsius),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Wind ${formatWind(state.weather)}",
                style = TextStyle(
                    color = ColorProvider(WidgetTextMuted),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LargeWidgetContent(state: WidgetState.Success) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val outfit = state.outfit

    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        // Brand strip: the RunWear mark, location and freshness at the right.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = state.locationName.take(15),
                    style = TextStyle(
                        color = ColorProvider(WidgetTextSecondary),
                        fontSize = 11.sp
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
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // The outfit is the hero: the moment it is for, then the main
        // pieces at display size with their garment icons.
        Text(
            text = "FOR NOW · ${state.weather.dateTime.format(timeFormatter)}",
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        HeroItemRow(outfit.topBase)
        val layers = listOfNotNull(outfit.topMid, outfit.topOuter)
        if (layers.isNotEmpty()) {
            Text(
                text = layers.joinToString(" + ") { it.displayName },
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
        }
        HeroItemRow(outfit.bottom)

        // Secondary pieces keep their icon chips under the hero.
        val secondary = listOfNotNull(outfit.head, outfit.hands) + outfit.accessories.take(2)
        if (secondary.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                secondary.forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                    OutfitItemChip(item)
                }
            }
        }

        // The "Later today" line fills the old defaultWeight void with the
        // one thing that changes before the weather does: the outfit.
        if (state.laterTodayLine != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = state.laterTodayLine,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }

        // Weather demoted to one grounding line: feels-like, actual, wind.
        Text(
            text = "Feels like ${formatTemperature(state.weather.feelsLike, state.weather.isCelsius)}" +
                " · Actual ${formatTemperature(state.weather.temperature, state.weather.isCelsius)}" +
                " · Wind ${formatWind(state.weather)}" +
                " · ${state.weather.weatherCode.description}",
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

/** One hero outfit line: the garment icon at display size + the full item name. */
@Composable
private fun HeroItemRow(item: ClothingItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(getCategoryIconRes(item.category)),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(ColorProvider(getCategoryColor(item.category)))
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = item.displayName,
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun OutfitItemChip(item: ClothingItem) {
    Row(
        modifier = GlanceModifier
            .background(WidgetCard)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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

@Composable
private fun LoadingContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RunWear",
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Loading…",
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun NoPermissionContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Location needed",
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to enable",
            style = TextStyle(
                color = ColorProvider(WidgetPrimary),
                fontSize = 11.sp
            )
        )
    }
}

/**
 * Never-succeeded-only (spec §B.3.1): a fresh install that has never loaded
 * gets the plain-words reason and a REAL retry — never two generic lines
 * that wait for the user to fix the widget.
 */
@Composable
private fun ErrorContent(state: WidgetState.Error) {
    Column(
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

private fun buildOutfitSummary(outfit: OutfitRecommendation): String {
    val items = mutableListOf<String>()
    items.add(outfit.topBase.displayName.split(" ").take(2).joinToString(" "))
    outfit.topMid?.let { items.add(it.displayName.split(" ").take(2).joinToString(" ")) }
    outfit.topOuter?.let { items.add(it.displayName.split(" ").take(2).joinToString(" ")) }
    items.add(outfit.bottom.displayName.split(" ").take(2).joinToString(" "))
    outfit.head?.let { items.add(it.displayName.split(" ").take(2).joinToString(" ")) }
    return items.joinToString(" • ")
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
