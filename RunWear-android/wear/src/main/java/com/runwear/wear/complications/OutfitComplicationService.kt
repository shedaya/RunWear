package com.runwear.wear.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.shared.util.LocationProvider
import com.runwear.wear.R
import com.runwear.wear.presentation.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class OutfitComplicationService : SuspendingComplicationDataSourceService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ComplicationEntryPoint {
        fun weatherRepository(): WeatherRepository
        fun preferencesRepository(): PreferencesRepository
        fun locationProvider(): LocationProvider
        fun getOutfitRecommendation(): GetOutfitRecommendationUseCase
    }

    private val entryPoint: ComplicationEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ComplicationEntryPoint::class.java)
    }

    private val weatherRepository: WeatherRepository get() = entryPoint.weatherRepository()
    private val preferencesRepository: PreferencesRepository get() = entryPoint.preferencesRepository()
    private val locationProvider: LocationProvider get() = entryPoint.locationProvider()
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase get() = entryPoint.getOutfitRecommendation()

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        Log.w(TAG, "getPreviewData: type=$type")
        return when (type) {
            ComplicationType.SHORT_TEXT -> createPreviewShortText()
            ComplicationType.LONG_TEXT -> createPreviewLongText()
            ComplicationType.RANGED_VALUE -> createPreviewRangedValue()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.w(TAG, "onComplicationRequest: type=${request.complicationType}")
        return try {
            val result = loadWeatherData()
            val weather = result.getOrNull()
            if (weather == null) {
                val reason = result.exceptionOrNull()?.message ?: "unknown"
                Log.w(TAG, "onComplicationRequest: no weather, reason=$reason")
                // Failure states render the plain no-data complication. The
                // raw reason is for the log only — internal error strings
                // must never appear on the user's watch face.
                return createNoDataComplication(request.complicationType)
            }

            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> createShortTextComplication(weather)
                ComplicationType.LONG_TEXT -> createLongTextComplication(weather)
                ComplicationType.RANGED_VALUE -> createRangedValueComplication(weather)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "onComplicationRequest: error", e)
            createNoDataComplication(request.complicationType)
        }
    }

    private suspend fun loadWeatherData(): Result<WeatherConditions> {
        return try {
            Log.w(TAG, "loadWeatherData: starting")

            if (!locationProvider.hasLocationPermission()) {
                Log.w(TAG, "loadWeatherData: no location permission")
                return Result.failure(Exception("no_perm"))
            }

            val result = withTimeoutOrNull(15_000L) {
                val prefs = preferencesRepository.preferencesFlow.first()
                Log.w(TAG, "loadWeatherData: got prefs")

                // Try cached location first (instant), then lastLocation, then fresh fix
                val location = locationProvider.getCachedLocation()
                    ?: locationProvider.getLastKnownLocation()
                    ?: locationProvider.getCurrentLocationWithTimeout().let { locResult ->
                        when (locResult) {
                            is com.runwear.shared.util.LocationFetchResult.Success -> locResult.location
                            is com.runwear.shared.util.LocationFetchResult.Error -> {
                                Log.e(TAG, "loadWeatherData: location error ${locResult.reason}")
                                return@withTimeoutOrNull Result.failure<WeatherConditions>(
                                    Exception("loc_${locResult.reason}")
                                )
                            }
                        }
                    }
                Log.w(TAG, "loadWeatherData: got location ${location.latitude},${location.longitude}")

                val weather = weatherRepository.getCurrentWeather(
                    location.latitude,
                    location.longitude,
                    prefs.temperatureUnit
                ).getOrNull()
                Log.w(TAG, "loadWeatherData: weather=${weather?.effectiveTemperature}")
                if (weather != null) Result.success(weather)
                else Result.failure(Exception("no_weather"))
            }
            result ?: Result.failure(Exception("timeout"))
        } catch (e: Exception) {
            Log.e(TAG, "loadWeatherData: error", e)
            Result.failure(Exception("exc:${e.message?.take(20)}"))
        }
    }

    companion object {
        private const val TAG = "OutfitComplication"
    }

    /** The thermometer monochromatic icon (ic_complication) used in place of emoji titles. */
    private fun complicationIcon(): MonochromaticImage {
        return MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_complication)
        ).build()
    }

    private fun createTapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun createShortTextComplication(weather: WeatherConditions): ShortTextComplicationData {
        val tempText = "${weather.effectiveTemperature.toInt()}°"

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(tempText).build(),
            contentDescription = PlainComplicationText.Builder("Temperature: $tempText").build()
        )
            .setMonochromaticImage(complicationIcon())
            .setTapAction(createTapAction())
            .build()
    }

    private suspend fun createLongTextComplication(weather: WeatherConditions): LongTextComplicationData {
        val prefs = preferencesRepository.preferencesFlow.first()
        val outfit = getOutfitRecommendation.execute(weather, prefs.comfortPreference)

        val tempText = if (weather.isCelsius) {
            "${weather.effectiveTemperature.toInt()}°C"
        } else {
            "${weather.effectiveTemperature.toInt()}°F"
        }

        val outfitItems = listOfNotNull(
            outfit.topBase.displayName.split(" ").take(2).joinToString(" "),
            outfit.topMid?.displayName?.split(" ")?.take(2)?.joinToString(" "),
            outfit.bottom.displayName.split(" ").take(2).joinToString(" "),
            outfit.head?.displayName?.split(" ")?.take(1)?.joinToString(" ")
        ).joinToString(", ")

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(outfitItems).build(),
            contentDescription = PlainComplicationText.Builder("Outfit recommendation: $outfitItems").build()
        )
            .setTitle(PlainComplicationText.Builder(tempText).build())
            .setTapAction(createTapAction())
            .build()
    }

    private fun createRangedValueComplication(weather: WeatherConditions): RangedValueComplicationData {
        val tempF = if (weather.isCelsius) {
            weather.effectiveTemperature * 9 / 5 + 32
        } else {
            weather.effectiveTemperature
        }

        val minTemp = 10f
        val maxTemp = 100f
        val clampedTemp = tempF.toFloat().coerceIn(minTemp, maxTemp)

        val tempText = if (weather.isCelsius) {
            "${weather.effectiveTemperature.toInt()}°C"
        } else {
            "${weather.effectiveTemperature.toInt()}°F"
        }

        return RangedValueComplicationData.Builder(
            value = clampedTemp,
            min = minTemp,
            max = maxTemp,
            contentDescription = PlainComplicationText.Builder("Temperature: $tempText").build()
        )
            .setText(PlainComplicationText.Builder(tempText).build())
            .setTitle(PlainComplicationText.Builder(weather.weatherCode.description).build())
            .setTapAction(createTapAction())
            .build()
    }

    private fun createNoDataComplication(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("--°").build(),
                contentDescription = PlainComplicationText.Builder("No data").build()
            )
                .setMonochromaticImage(complicationIcon())
                .setTapAction(createTapAction())
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Tap to load").build(),
                contentDescription = PlainComplicationText.Builder("No data available").build()
            )
                .setTitle(PlainComplicationText.Builder("RunWear").build())
                .setTapAction(createTapAction())
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 50f,
                min = 10f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder("No data").build()
            )
                .setText(PlainComplicationText.Builder("--°").build())
                .setTapAction(createTapAction())
                .build()

            else -> null
        }
    }

    private fun createPreviewShortText(): ShortTextComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("72°").build(),
            contentDescription = PlainComplicationText.Builder("Temperature").build()
        )
            .setMonochromaticImage(complicationIcon())
            .build()
    }

    private fun createPreviewLongText(): LongTextComplicationData {
        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder("Shorts, Tank Top, Cap").build(),
            contentDescription = PlainComplicationText.Builder("Outfit preview").build()
        )
            .setTitle(PlainComplicationText.Builder("72°F").build())
            .build()
    }

    private fun createPreviewRangedValue(): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = 72f,
            min = 10f,
            max = 100f,
            contentDescription = PlainComplicationText.Builder("Temperature preview").build()
        )
            .setText(PlainComplicationText.Builder("72°F").build())
            .setTitle(PlainComplicationText.Builder("Clear sky").build())
            .build()
    }
}
