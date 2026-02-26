package com.runwear.wear.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.shared.util.LocationProvider
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val RESOURCES_VERSION = "1"

@OptIn(ExperimentalHorologistApi::class)
class OutfitTileService : SuspendingTileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun weatherRepository(): WeatherRepository
        fun preferencesRepository(): PreferencesRepository
        fun locationProvider(): LocationProvider
        fun getOutfitRecommendation(): GetOutfitRecommendationUseCase
    }

    private val entryPoint: TileEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
    }

    private val weatherRepository: WeatherRepository get() = entryPoint.weatherRepository()
    private val preferencesRepository: PreferencesRepository get() = entryPoint.preferencesRepository()
    private val locationProvider: LocationProvider get() = entryPoint.locationProvider()
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase get() = entryPoint.getOutfitRecommendation()

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        val tileState = loadTileData()

        val layout = when (tileState) {
            is TileState.Loading -> createLoadingLayout()
            is TileState.Error -> createErrorLayout(tileState.message)
            is TileState.Success -> createOutfitLayout(tileState.weather, tileState.outfit, tileState.locationName)
            is TileState.NoPermission -> createNoPermissionLayout()
        }

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(layout)
                            .build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(singleTileTimeline)
            .setFreshnessIntervalMillis(1800000) // 30 minutes
            .build()
    }

    private suspend fun loadTileData(): TileState {
        return try {
            Log.w(TAG, "loadTileData: starting")

            if (!locationProvider.hasLocationPermission()) {
                Log.w(TAG, "loadTileData: no location permission")
                return TileState.NoPermission
            }

            val prefs = preferencesRepository.preferencesFlow.first()
            Log.w(TAG, "loadTileData: got prefs, unit=${prefs.temperatureUnit}")

            val result = withTimeoutOrNull(15_000L) {
                val location = locationProvider.getCachedLocation()
                    ?: locationProvider.getLastKnownLocation()
                    ?: locationProvider.getCurrentLocationWithTimeout().let { locResult ->
                        when (locResult) {
                            is com.runwear.shared.util.LocationFetchResult.Success -> locResult.location
                            is com.runwear.shared.util.LocationFetchResult.Error -> throw Exception("Location: ${locResult.reason}")
                        }
                    }
                Log.w(TAG, "loadTileData: got location ${location.latitude},${location.longitude}")

                val weather = weatherRepository.getCurrentWeather(
                    location.latitude,
                    location.longitude,
                    prefs.temperatureUnit
                ).getOrThrow()
                Log.w(TAG, "loadTileData: got weather ${weather.effectiveTemperature}°")

                val locationName = weatherRepository.reverseGeocode(
                    location.latitude,
                    location.longitude
                ).getOrElse { "Your Location" }

                val outfit = getOutfitRecommendation.execute(weather, prefs.comfortPreference)
                Log.w(TAG, "loadTileData: got outfit, topBase=${outfit.topBase}")

                TileState.Success(weather, outfit, locationName)
            }

            result ?: TileState.Error("Timed out loading weather")
        } catch (e: Exception) {
            Log.e(TAG, "loadTileData: error", e)
            TileState.Error(e.message ?: "Unable to load weather")
        }
    }

    companion object {
        private const val TAG = "OutfitTileService"
    }

    private fun createOutfitLayout(
        weather: WeatherConditions,
        outfit: OutfitRecommendation,
        locationName: String
    ): LayoutElementBuilders.LayoutElement {
        val tempColor = getTemperatureColor(weather.effectiveTemperature, weather.isCelsius)
        val tempText = if (weather.isCelsius) {
            "${weather.effectiveTemperature.toInt()}°C"
        } else {
            "${weather.effectiveTemperature.toInt()}°F"
        }
        val weatherEmoji = getWeatherEmoji(weather.weatherCode)

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("com.runwear.wear.presentation.MainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Spacer.Builder().setHeight(dp(8f)).build()
                    )
                    .addContent(
                        Row.Builder()
                            .setWidth(wrap())
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(
                                Text.Builder()
                                    .setText(weatherEmoji)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(28f))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                Spacer.Builder().setWidth(dp(8f)).build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(tempText)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(32f))
                                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                            .setColor(argb(tempColor))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Feels ${weather.feelsLike.toInt()}°")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0xFFB0B0B0.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(8f)).build()
                    )
                    .addContent(
                        createOutfitSummary(outfit)
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(6f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Tap for details")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(10f))
                                    .setColor(argb(0xFF808080.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun createOutfitSummary(outfit: OutfitRecommendation): LayoutElementBuilders.LayoutElement {
        val items = mutableListOf<Pair<String, String>>()

        items.add(outfit.topBase.icon to outfit.topBase.displayName.split(" ").take(2).joinToString(" "))
        outfit.topMid?.let {
            items.add(it.icon to it.displayName.split(" ").take(2).joinToString(" "))
        }
        outfit.topOuter?.let {
            items.add(it.icon to it.displayName.split(" ").take(2).joinToString(" "))
        }
        items.add(outfit.bottom.icon to outfit.bottom.displayName.split(" ").take(2).joinToString(" "))
        outfit.head?.let {
            items.add(it.icon to it.displayName.split(" ").take(2).joinToString(" "))
        }

        val displayItems = items.take(4)

        return Column.Builder()
            .setWidth(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .apply {
                displayItems.forEach { (icon, name) ->
                    addContent(
                        Row.Builder()
                            .setWidth(wrap())
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(
                                Text.Builder()
                                    .setText(icon)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(14f))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                Spacer.Builder().setWidth(dp(4f)).build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(name)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(13f))
                                            .setColor(argb(0xFFE0E0E0.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    addContent(
                        Spacer.Builder().setHeight(dp(2f)).build()
                    )
                }
            }
            .build()
    }

    private fun createLoadingLayout(): LayoutElementBuilders.LayoutElement {
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                Column.Builder()
                    .setWidth(wrap())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("🏃")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(32f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Loading...")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(0xFFB0B0B0.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun createErrorLayout(message: String): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("retry")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("com.runwear.wear.presentation.MainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(wrap())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("⚠️")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(24f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Tap to retry")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(0xFFFF6B35.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun createNoPermissionLayout(): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("request_permission")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("com.runwear.wear.presentation.MainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(wrap())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("📍")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(24f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Location needed")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(0xFFE0E0E0.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Tap to enable")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0xFFFF6B35.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
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

    private fun getTemperatureColor(temp: Double, isCelsius: Boolean): Int {
        val tempF = if (isCelsius) temp * 9 / 5 + 32 else temp
        return when {
            tempF >= 80 -> 0xFFFF5722.toInt() // Hot - Orange
            tempF >= 65 -> 0xFFFF9800.toInt() // Warm - Amber
            tempF >= 50 -> 0xFF4CAF50.toInt() // Mild - Green
            tempF >= 35 -> 0xFF2196F3.toInt() // Cool - Blue
            tempF >= 20 -> 0xFF3F51B5.toInt() // Cold - Indigo
            else -> 0xFF9C27B0.toInt() // Freezing - Purple
        }
    }

    private sealed class TileState {
        object Loading : TileState()
        object NoPermission : TileState()
        data class Error(val message: String) : TileState()
        data class Success(
            val weather: WeatherConditions,
            val outfit: OutfitRecommendation,
            val locationName: String
        ) : TileState()
    }
}
