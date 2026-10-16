package com.runwear.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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
import kotlinx.coroutines.flow.first
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

class OutfitWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 60.dp)
        private val LARGE_RECTANGLE = DpSize(250.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL_SQUARE, HORIZONTAL_RECTANGLE, LARGE_RECTANGLE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        val weatherRepository = entryPoint.weatherRepository()
        val preferencesRepository = entryPoint.preferencesRepository()
        val locationProvider = entryPoint.locationProvider()
        val getOutfitRecommendation = entryPoint.getOutfitRecommendation()

        val widgetState = loadWidgetData(
            weatherRepository,
            preferencesRepository,
            locationProvider,
            getOutfitRecommendation
        )

        provideContent {
            GlanceTheme {
                WidgetContent(widgetState)
            }
        }
    }

    private suspend fun loadWidgetData(
        weatherRepository: WeatherRepository,
        preferencesRepository: PreferencesRepository,
        locationProvider: LocationProvider,
        getOutfitRecommendation: GetOutfitRecommendationUseCase
    ): WidgetState {
        if (!locationProvider.hasLocationPermission()) {
            return WidgetState.NoPermission
        }

        return try {
            val prefs = preferencesRepository.preferencesFlow.first()
            val location = locationProvider.getCurrentLocation().getOrThrow()
            val weather = weatherRepository.getCurrentWeather(
                location.latitude,
                location.longitude,
                prefs.temperatureUnit
            ).getOrThrow()

            val locationName = weatherRepository.reverseGeocode(
                location.latitude,
                location.longitude
            ).getOrElse { "Your Location" }

            val outfit = getOutfitRecommendation.execute(weather, prefs.comfortPreference)
            val lastUpdated = LocalDateTime.now()

            WidgetState.Success(weather, outfit, locationName, lastUpdated)
        } catch (e: Exception) {
            WidgetState.Error(e.message ?: "Unable to load weather")
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val context = LocalContext.current

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
            is WidgetState.Error -> ErrorContent()
            is WidgetState.Success -> {
                when {
                    size.width >= 250.dp && size.height >= 150.dp -> LargeWidgetContent(state)
                    size.width >= 250.dp -> HorizontalWidgetContent(state)
                    else -> SmallWidgetContent(state)
                }
            }
        }
    }
}

@Composable
private fun SmallWidgetContent(state: WidgetState.Success) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.weather.weatherCode.description,
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = formatTemperature(state.weather.feelsLike, state.weather.isCelsius),
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = state.outfit.topBase.displayName.split(" ").take(2).joinToString(" "),
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 12.sp
            )
        )
        Text(
            text = state.outfit.bottom.displayName.split(" ").take(2).joinToString(" "),
            style = TextStyle(
                color = ColorProvider(WidgetTextSecondary),
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun HorizontalWidgetContent(state: WidgetState.Success) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Weather section: feels-like hero in white + condition label
        Column {
            Text(
                text = formatTemperature(state.weather.feelsLike, state.weather.isCelsius),
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = state.weather.weatherCode.description,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 10.sp
                ),
                maxLines = 1
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

        // Outfit summary
        Text(
            text = buildOutfitSummary(state.outfit),
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp
            ),
            maxLines = 2
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Arrow indicator
        Text(
            text = "→",
            style = TextStyle(
                color = ColorProvider(WidgetTextMuted),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
private fun LargeWidgetContent(state: WidgetState.Success) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        // Header row with weather and location
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatTemperature(state.weather.feelsLike, state.weather.isCelsius),
                    style = TextStyle(
                        color = ColorProvider(WidgetTextPrimary),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${state.weather.weatherCode.description} · " +
                        formatTemperature(state.weather.effectiveTemperature, state.weather.isCelsius) + " actual",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextSecondary),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
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
                    text = "Updated ${state.lastUpdated.format(timeFormatter)}",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextMuted),
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Divider
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetCard)
        ) {}

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Outfit items grid — full item names at this size
        Column {
            // Primary items row
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                OutfitItemChip(state.outfit.topBase)
                state.outfit.topMid?.let {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    OutfitItemChip(it)
                }
                state.outfit.topOuter?.let {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    OutfitItemChip(it)
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Bottom row
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                OutfitItemChip(state.outfit.bottom)
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Accessories row
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                state.outfit.head?.let {
                    OutfitItemChip(it)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
                state.outfit.hands?.let {
                    OutfitItemChip(it)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
                state.outfit.accessories.take(2).forEach { item ->
                    OutfitItemChip(item)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
            }
        }
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

@Composable
private fun ErrorContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Couldn't load weather",
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 13.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to retry",
            style = TextStyle(
                color = ColorProvider(WidgetPrimary),
                fontSize = 12.sp
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
    data class Error(val message: String) : WidgetState()
    data class Success(
        val weather: WeatherConditions,
        val outfit: OutfitRecommendation,
        val locationName: String,
        val lastUpdated: LocalDateTime
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
}
