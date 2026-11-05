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
import com.runwear.wear.R
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
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

// GEN-5: bumped for the design-pass palette change so installed
// tiles re-render in the Warm Charcoal pair. Addendum 10: bumped
// again for the safe-area padding + large-font layout changes so
// installed tiles re-render in the compliant layout.
private const val RESOURCES_VERSION = "4"

// Tile palette mirrors the phone app's kit — GEN-5 design pass:
// the Warm Charcoal pair (warm ink #F5F1EA, muted #B8AEA0) and the
// pine dark accent #5CC79F, replacing the retired cool greys and
// teal. Item rows use the app's category icons (baked in the phone
// dark category colors — ProtoLayout cannot tint).
private const val TILE_TEXT_PRIMARY = 0xFFF5F1EA.toInt()
private const val TILE_TEXT_SECONDARY = 0xFFB8AEA0.toInt()
private const val TILE_TEXT_MUTED = 0xFFB8AEA0.toInt()
private const val TILE_PRIMARY = 0xFF5CC79F.toInt()

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
            .addIdToImageMapping("header_icon", imageResource(R.drawable.ic_tile_outfit))
            .addIdToImageMapping("cat_top", imageResource(R.drawable.ic_tile_cat_top))
            .addIdToImageMapping("cat_bottom", imageResource(R.drawable.ic_tile_cat_bottom))
            .addIdToImageMapping("cat_head", imageResource(R.drawable.ic_tile_cat_head))
            .addIdToImageMapping("cat_hands", imageResource(R.drawable.ic_tile_cat_hands))
            .addIdToImageMapping("cat_accessories", imageResource(R.drawable.ic_tile_cat_accessories))
            .build()
    }

    private fun imageResource(resId: Int): ResourceBuilders.ImageResource {
        return ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        val tileState = loadTileData()

        val layout = when (tileState) {
            is TileState.Loading -> createLoadingLayout()
            is TileState.Error -> createErrorLayout(tileState.message)
            is TileState.Success -> createOutfitLayout(tileState.weather, tileState.outfit, tileState.locationName, tileState.temperatureUnit)
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
                    location.longitude
                ).getOrThrow()
                Log.w(TAG, "loadTileData: got weather ${weather.effectiveTemperature}°")

                val locationName = weatherRepository.reverseGeocode(
                    location.latitude,
                    location.longitude
                ).getOrElse { "Your Location" }

                val outfit = getOutfitRecommendation.execute(weather, prefs.comfortPreference)
                Log.w(TAG, "loadTileData: got outfit, topBase=${outfit.topBase}")

                TileState.Success(weather, outfit, locationName, prefs.temperatureUnit)
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
        locationName: String,
        /** WS-2: render-time projection of the canonical store. */
        temperatureUnit: com.runwear.shared.domain.model.TemperatureUnit
    ): LayoutElementBuilders.LayoutElement {
        val feelsText = temperatureUnit.formatTemperature(weather.feelsLike)
        val actualText = temperatureUnit.formatTemperature(weather.effectiveTemperature)

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
                    // Addendum 10 (WATCH SHAPES): safe-area padding
                    // on the root keeps every text row and control
                    // inside the round display's usable chord —
                    // full-bleed rows were clipped by the curved
                    // edge on round faces.
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(18f))
                            .setEnd(dp(18f))
                            .setTop(dp(10f))
                            .setBottom(dp(10f))
                            .build()
                    )
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
                                LayoutElementBuilders.Image.Builder()
                                    .setResourceId("header_icon")
                                    .setWidth(dp(24f))
                                    .setHeight(dp(24f))
                                    .build()
                            )
                            .addContent(
                                Spacer.Builder().setWidth(dp(6f)).build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(feelsText)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(30f))
                                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                            .setColor(argb(TILE_TEXT_PRIMARY))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("${weather.weatherCode.description} · $actualText actual")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(TILE_TEXT_SECONDARY))
                                    .build()
                            )
                            // Addendum 10 (LARGE FONT): wraps to
                            // two lines instead of ellipsising at
                            // the largest font scale.
                            .setMaxLines(2)
                            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
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
                                    .setColor(argb(TILE_TEXT_MUTED))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun createOutfitSummary(outfit: OutfitRecommendation): LayoutElementBuilders.LayoutElement {
        val items = mutableListOf<ClothingItem>()

        items.add(outfit.topBase)
        outfit.topMid?.let { items.add(it) }
        outfit.topOuter?.let { items.add(it) }
        items.add(outfit.bottom)
        outfit.head?.let { items.add(it) }

        // Addendum 10 (LARGE FONT): three rows instead of four.
        // The tile host does not report the system font scale to
        // providers (DeviceParameters in tiles 1.4 carries no font
        // scale), so the layout must be safe at the largest scale
        // by construction: with names now wrapping to two lines,
        // four rows could push the summary off the round display
        // at fontScale 1.30; three cannot. The full outfit remains
        // one tap away in the app, per the tile's own door.
        val displayItems = items.take(3)

        return Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .apply {
                displayItems.forEach { item ->
                    addContent(
                        Row.Builder()
                            .setWidth(expand())
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(
                                LayoutElementBuilders.Image.Builder()
                                    .setResourceId(tileCategoryIconId(item.category))
                                    .setWidth(dp(16f))
                                    .setHeight(dp(16f))
                                    .build()
                            )
                            .addContent(
                                Spacer.Builder().setWidth(dp(5f)).build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(item.displayName)
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(13f))
                                            .setColor(argb(TILE_TEXT_PRIMARY))
                                            .build()
                                    )
                                    // Addendum 10 (LARGE FONT):
                                    // item names wrap to two lines
                                    // inside the padded row instead
                                    // of truncating mid-name.
                                    .setMaxLines(2)
                                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
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

    /** Tile image-resource id for a category — mirrors the phone app's category set. */
    private fun tileCategoryIconId(category: ClothingCategory): String =
        when (category) {
            ClothingCategory.TOP_BASE,
            ClothingCategory.TOP_MID,
            ClothingCategory.TOP_OUTER -> "cat_top"
            ClothingCategory.BOTTOM -> "cat_bottom"
            ClothingCategory.HEAD -> "cat_head"
            ClothingCategory.HANDS -> "cat_hands"
            ClothingCategory.ACCESSORIES -> "cat_accessories"
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
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId("header_icon")
                            .setWidth(dp(28f))
                            .setHeight(dp(28f))
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(6f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Loading...")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(TILE_TEXT_SECONDARY))
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
                            .setText("Couldn't load weather")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(13f))
                                    .setColor(argb(TILE_TEXT_PRIMARY))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(4f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Tap to retry")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(TILE_PRIMARY))
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
                            .setText("Location needed")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(TILE_TEXT_PRIMARY))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(4f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText("Tap to enable")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(TILE_PRIMARY))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private sealed class TileState {
        object Loading : TileState()
        object NoPermission : TileState()
        data class Error(val message: String) : TileState()
        data class Success(
            val weather: WeatherConditions,
            val outfit: OutfitRecommendation,
            val locationName: String,
            val temperatureUnit: com.runwear.shared.domain.model.TemperatureUnit =
                com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        ) : TileState()
    }
}
