package com.runwear.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.runwear.app.MainActivity
import com.runwear.app.R
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
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
            .background(Color(0xFF1A1A1A))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        when (state) {
            is WidgetState.Loading -> LoadingContent()
            is WidgetState.NoPermission -> NoPermissionContent()
            is WidgetState.Error -> ErrorContent(state.message)
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
    val tempColor = getTemperatureColorProvider(state.weather.effectiveTemperature, state.weather.isCelsius)
    val tempText = formatTemperature(state.weather)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getWeatherEmoji(state.weather.weatherCode),
            style = TextStyle(fontSize = 28.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = tempText,
            style = TextStyle(
                color = tempColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = state.outfit.topBase.displayName.split(" ").take(2).joinToString(" "),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp
            )
        )
        Text(
            text = state.outfit.bottom.displayName.split(" ").take(2).joinToString(" "),
            style = TextStyle(
                color = ColorProvider(Color(0xFFB0B0B0)),
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun HorizontalWidgetContent(state: WidgetState.Success) {
    val tempColor = getTemperatureColorProvider(state.weather.effectiveTemperature, state.weather.isCelsius)
    val tempText = formatTemperature(state.weather)

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Weather section
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getWeatherEmoji(state.weather.weatherCode),
                style = TextStyle(fontSize = 24.sp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = tempText,
                style = TextStyle(
                    color = tempColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        // Divider
        Box(
            modifier = GlanceModifier
                .width(1.dp)
                .height(30.dp)
                .background(Color(0xFF404040))
        ) {}

        Spacer(modifier = GlanceModifier.width(12.dp))

        // Outfit summary
        Text(
            text = buildOutfitSummary(state.outfit),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 13.sp
            ),
            maxLines = 2
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Arrow indicator
        Text(
            text = "→",
            style = TextStyle(
                color = ColorProvider(Color(0xFF808080)),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
private fun LargeWidgetContent(state: WidgetState.Success) {
    val tempColor = getTemperatureColorProvider(state.weather.effectiveTemperature, state.weather.isCelsius)
    val tempText = formatTemperature(state.weather)
    val feelsLikeText = "Feels ${state.weather.feelsLike.toInt()}°"
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        // Header row with weather and location
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getWeatherEmoji(state.weather.weatherCode),
                style = TextStyle(fontSize = 28.sp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column {
                Text(
                    text = tempText,
                    style = TextStyle(
                        color = tempColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = feelsLikeText,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB0B0B0)),
                        fontSize = 12.sp
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = state.locationName.take(15),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB0B0B0)),
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = state.lastUpdated.format(timeFormatter),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF808080)),
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
                .background(Color(0xFF303030))
        ) {}

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Outfit items grid
        Column {
            // Primary items row
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                OutfitItemChip(state.outfit.topBase)
                state.outfit.topMid?.let {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    OutfitItemChip(it)
                }
                state.outfit.topOuter?.let {
                    Spacer(modifier = GlanceModifier.width(8.dp))
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
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                state.outfit.hands?.let {
                    OutfitItemChip(it)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                state.outfit.accessories.take(2).forEach { item ->
                    OutfitItemChip(item)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OutfitItemChip(item: ClothingItem) {
    Row(
        modifier = GlanceModifier
            .background(Color(0xFF2A2A2A))
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.icon,
            style = TextStyle(fontSize = 12.sp)
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = item.displayName.split(" ").take(2).joinToString(" "),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 11.sp
            )
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
            text = "🏃",
            style = TextStyle(fontSize = 32.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Loading...",
            style = TextStyle(
                color = ColorProvider(Color(0xFFB0B0B0)),
                fontSize = 14.sp
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
            text = "📍",
            style = TextStyle(fontSize = 24.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Location needed",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp
            )
        )
        Text(
            text = "Tap to enable",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFF6B35)),
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚠️",
            style = TextStyle(fontSize = 24.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to retry",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFF6B35)),
                fontSize = 12.sp
            )
        )
    }
}

private fun formatTemperature(weather: WeatherConditions): String {
    return if (weather.isCelsius) {
        "${weather.effectiveTemperature.toInt()}°C"
    } else {
        "${weather.effectiveTemperature.toInt()}°F"
    }
}

private fun buildOutfitSummary(outfit: OutfitRecommendation): String {
    val items = mutableListOf<String>()
    items.add(outfit.topBase.displayName.split(" ").take(2).joinToString(" "))
    outfit.topMid?.let { items.add(it.displayName.split(" ").take(1).joinToString(" ")) }
    outfit.topOuter?.let { items.add(it.displayName.split(" ").take(1).joinToString(" ")) }
    items.add(outfit.bottom.displayName.split(" ").take(2).joinToString(" "))
    outfit.head?.let { items.add(it.displayName.split(" ").take(1).joinToString(" ")) }
    return items.joinToString(" • ")
}

private fun getWeatherEmoji(code: WeatherCode): String = when (code) {
    WeatherCode.CLEAR_SKY -> "☀️"
    WeatherCode.MAINLY_CLEAR -> "🌤️"
    WeatherCode.PARTLY_CLOUDY -> "⛅"
    WeatherCode.OVERCAST -> "☁️"
    WeatherCode.FOG, WeatherCode.DEPOSITING_RIME_FOG -> "🌫️"
    WeatherCode.DRIZZLE_LIGHT, WeatherCode.DRIZZLE_MODERATE, WeatherCode.DRIZZLE_DENSE -> "🌧️"
    WeatherCode.FREEZING_DRIZZLE_LIGHT, WeatherCode.FREEZING_DRIZZLE_DENSE -> "🌨️"
    WeatherCode.RAIN_SLIGHT, WeatherCode.RAIN_MODERATE, WeatherCode.RAIN_HEAVY -> "🌧️"
    WeatherCode.FREEZING_RAIN_LIGHT, WeatherCode.FREEZING_RAIN_HEAVY -> "🌨️"
    WeatherCode.SNOW_SLIGHT, WeatherCode.SNOW_MODERATE, WeatherCode.SNOW_HEAVY -> "❄️"
    WeatherCode.SNOW_GRAINS -> "🌨️"
    WeatherCode.RAIN_SHOWERS_SLIGHT, WeatherCode.RAIN_SHOWERS_MODERATE, WeatherCode.RAIN_SHOWERS_VIOLENT -> "🌦️"
    WeatherCode.SNOW_SHOWERS_SLIGHT, WeatherCode.SNOW_SHOWERS_HEAVY -> "🌨️"
    WeatherCode.THUNDERSTORM, WeatherCode.THUNDERSTORM_HAIL_SLIGHT, WeatherCode.THUNDERSTORM_HAIL_HEAVY -> "⛈️"
    WeatherCode.UNKNOWN -> "🌡️"
}

private fun getTemperatureColorProvider(temp: Double, isCelsius: Boolean): ColorProvider {
    val tempF = if (isCelsius) temp * 9 / 5 + 32 else temp
    val color = when {
        tempF >= 80 -> Color(0xFFFF5722) // Hot - Orange
        tempF >= 65 -> Color(0xFFFF9800) // Warm - Amber
        tempF >= 50 -> Color(0xFF4CAF50) // Mild - Green
        tempF >= 35 -> Color(0xFF2196F3) // Cool - Blue
        tempF >= 20 -> Color(0xFF3F51B5) // Cold - Indigo
        else -> Color(0xFF9C27B0) // Freezing - Purple
    }
    return ColorProvider(color)
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
