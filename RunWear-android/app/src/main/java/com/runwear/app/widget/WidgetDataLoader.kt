package com.runwear.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.UserPreferences
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.shared.util.LocationFetchResult
import com.runwear.shared.util.LocationProvider
import com.runwear.shared.util.UserLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * The widget's load chain, shared by the Glance provider, the ↻ refresh
 * callback and the WorkManager backstop (redesign spec §B.3).
 *
 * Location strategy — SAVED LOCATION FIRST (§B.3.2): the durable saved
 * location in [PreferencesRepository] is the widget's home base. A manually
 * chosen location always wins. A GPS-saved location may be refreshed by the
 * live chain, but only opportunistically — a live fix is never a gate, so
 * the background-starvation failure that parked the widget on an error card
 * (1-hour fix cache vs a travel day, "while using the app" permission) can
 * no longer recur. No background-location permission is requested, ever.
 *
 * Stage logging mirrors OutfitTileService (`Log.w` per stage, `Log.e` with
 * the exception) so a field failure is diagnosable from logcat.
 */
object WidgetDataLoader {

    private const val TAG = "OutfitWidget"
    private const val COORD_EPSILON = 0.005 // ~500 m — below this a "new" fix changes nothing

    sealed class Outcome {
        data class Success(val lastGood: WidgetLastGood) : Outcome()
        object NoPermission : Outcome()
        data class Failed(val category: WidgetErrorCategory, val detail: String) : Outcome()
    }

    suspend fun load(
        weatherRepository: WeatherRepository,
        preferencesRepository: PreferencesRepository,
        locationProvider: LocationProvider,
        getOutfitRecommendation: GetOutfitRecommendationUseCase
    ): Outcome {
        Log.w(TAG, "loadWidgetData: starting")
        return try {
            val prefs = preferencesRepository.preferencesFlow.first()
            Log.w(TAG, "loadWidgetData: got prefs, unit=${prefs.temperatureUnit}")

            val saved = preferencesRepository.getSavedLocation()
            val coords: UserLocation
            var locationName: String

            if (saved != null) {
                Log.w(TAG, "loadWidgetData: saved location (${saved.source}) ${saved.latitude},${saved.longitude}")
                coords = UserLocation(saved.latitude, saved.longitude)
                locationName = saved.name

                // Opportunistic refresh of a GPS-saved location only. A manual
                // location is the user's explicit choice and is never overridden.
                if (saved.source == "gps" && locationProvider.hasLocationPermission()) {
                    val live = runCatching { liveChain(locationProvider) }.getOrNull()
                    if (live != null &&
                        (kotlin.math.abs(live.latitude - saved.latitude) > COORD_EPSILON ||
                            kotlin.math.abs(live.longitude - saved.longitude) > COORD_EPSILON)
                    ) {
                        Log.w(TAG, "loadWidgetData: live fix moved, refreshing saved GPS location")
                        val name = weatherRepository.reverseGeocode(live.latitude, live.longitude)
                            .getOrElse { saved.name }
                        preferencesRepository.saveGPSLocation(live.latitude, live.longitude, name)
                        locationName = name
                        return loadForCoords(
                            live, name, weatherRepository, prefs, getOutfitRecommendation
                        )
                    }
                }
                loadForCoords(coords, locationName, weatherRepository, prefs, getOutfitRecommendation)
            } else {
                // No durable location yet: permission + live chain is the only path,
                // and a success becomes durable via saveGPSLocation below.
                if (!locationProvider.hasLocationPermission()) {
                    Log.w(TAG, "loadWidgetData: no saved location, no permission")
                    return Outcome.NoPermission
                }
                val live = liveChain(locationProvider)
                    ?: return Outcome.Failed(
                        WidgetErrorCategory.LOCATION,
                        "Location: no source yielded a fix"
                    ).also { Log.e(TAG, "loadWidgetData: error ${it.detail}") }
                val name = weatherRepository.reverseGeocode(live.latitude, live.longitude)
                    .getOrElse { "Your Location" }
                preferencesRepository.saveGPSLocation(live.latitude, live.longitude, name)
                Log.w(TAG, "loadWidgetData: saved first GPS location ${live.latitude},${live.longitude}")
                loadForCoords(live, name, weatherRepository, prefs, getOutfitRecommendation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadWidgetData: error", e)
            Outcome.Failed(WidgetErrorCategory.WEATHER, e.message ?: "Unknown error")
        }
    }

    /** Cached fix → fused last-known → fresh fix with timeout (the pre-existing resilient chain). */
    private suspend fun liveChain(locationProvider: LocationProvider): UserLocation? {
        locationProvider.getCachedLocation()?.let {
            Log.w(TAG, "loadWidgetData: location from shared-prefs cache")
            return it
        }
        locationProvider.getLastKnownLocation()?.let {
            Log.w(TAG, "loadWidgetData: location from fused last-known")
            return it
        }
        return when (val fresh = locationProvider.getCurrentLocationWithTimeout()) {
            is LocationFetchResult.Success -> {
                Log.w(TAG, "loadWidgetData: location from fresh fix")
                fresh.location
            }
            is LocationFetchResult.Error -> {
                Log.w(TAG, "loadWidgetData: fresh fix failed: ${fresh.reason}")
                null
            }
        }
    }

    private suspend fun loadForCoords(
        coords: UserLocation,
        locationName: String,
        weatherRepository: WeatherRepository,
        prefs: UserPreferences,
        getOutfitRecommendation: GetOutfitRecommendationUseCase
    ): Outcome {
        Log.w(TAG, "loadWidgetData: got location ${coords.latitude},${coords.longitude}")

        // One retry on the weather fetch, so a transient network failure
        // does not blank the widget until the next refresh cycle.
        var weatherResult = weatherRepository.getCurrentWeather(
            coords.latitude, coords.longitude, prefs.temperatureUnit
        )
        if (weatherResult.isFailure) {
            delay(1000)
            weatherResult = weatherRepository.getCurrentWeather(
                coords.latitude, coords.longitude, prefs.temperatureUnit
            )
        }
        val weather: WeatherConditions = weatherResult.getOrElse { e ->
            Log.e(TAG, "loadWidgetData: weather fetch failed", e)
            return Outcome.Failed(
                WidgetErrorCategory.WEATHER,
                e.message ?: "Weather fetch failed"
            )
        }
        Log.w(TAG, "loadWidgetData: got weather ${weather.effectiveTemperature}°")

        // A saved location's stored name can be the geocoder fallback; upgrade
        // it when the real geocoder answers. Failure-tolerant by design.
        var name = locationName
        if (name.isBlank() || name == "Your Location") {
            weatherRepository.reverseGeocode(coords.latitude, coords.longitude)
                .onSuccess { name = it }
        }

        val outfit = getOutfitRecommendation.execute(weather, prefs.comfortPreference)
        Log.w(TAG, "loadWidgetData: got outfit, topBase=${outfit.topBase}")

        val laterTodayLine = LaterTodayLine.compute(
            weatherRepository = weatherRepository,
            latitude = coords.latitude,
            longitude = coords.longitude,
            unit = prefs.temperatureUnit,
            current = weather,
            currentOutfit = outfit,
            comfort = prefs.comfortPreference,
            getOutfit = getOutfitRecommendation
        )

        val now = LocalDateTime.now()
        return Outcome.Success(
            WidgetLastGood(
                weather = weather,
                outfit = outfit,
                locationName = name,
                latitude = coords.latitude,
                longitude = coords.longitude,
                laterTodayLine = laterTodayLine,
                savedAtLocal = now,
                savedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
}

/**
 * Single write path for new widget data (spec §B.3.3 write-through): persist
 * the last-good record, drop any stale `refreshing` flags, and re-render every
 * placed widget. Used by the widget load, the ↻ callback, the WorkManager
 * backstop and MainViewModel's successful in-app load.
 */
object WidgetPublisher {

    suspend fun publish(context: Context, lastGood: WidgetLastGood) {
        WidgetLastGoodStore(context).write(lastGood)
        RefreshFlag.clearAll(context)
        OutfitWidget().updateAll(context)
    }
}
