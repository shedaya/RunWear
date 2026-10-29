package com.runwear.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runwear.shared.data.repository.AffiliateRepository
import com.runwear.shared.data.repository.HeroImageRepository
import com.runwear.shared.data.repository.LocationResult
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.AffiliatePartner
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.shared.util.LocationErrorReason
import com.runwear.shared.util.LocationFetchResult
import com.runwear.shared.util.LocationProvider
import com.runwear.app.feedback.FeedbackBundleFactory
import com.runwear.app.feedback.FeedbackCategory
import com.runwear.app.feedback.FeedbackStore
import com.runwear.app.feedback.RunWearEvents
import com.runwear.app.feedback.TelemetryClient
import com.runwear.app.widget.LaterTodayLine
import com.runwear.app.widget.WidgetLastGood
import com.runwear.app.widget.WidgetLastGoodStore
import com.runwear.app.widget.WidgetPublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val outfit: OutfitRecommendation? = null,
    val weather: WeatherConditions? = null,
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val locationName: String = "Getting location...",
    /**
     * The selected moment, ALWAYS expressed as a naive wall-clock time in the
     * SELECTED LOCATION's frame — never the phone's. When [isNowSelected] is
     * true this is kept equal to [locationNow] and the weather comes from the
     * API's current-conditions block.
     */
    val selectedDateTime: LocalDateTime = LocalDateTime.now(),
    /** First-class NOW selection state. Any explicit date/time pick clears it. */
    val isNowSelected: Boolean = true,
    /** "Now" in the selected location's timezone; drives Today/Tomorrow labels. */
    val locationNow: LocalDateTime = LocalDateTime.now(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val comfortPreference: ComfortPreference = ComfortPreference.NEUTRAL,
    val genderPreference: GenderPreference = GenderPreference.UNISEX,
    val hasLocationPermission: Boolean = false,
    val hasCompletedOnboarding: Boolean = true, // Assume true, check in init
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showSettings: Boolean = false,
    val affiliatePartner: AffiliatePartner = AffiliatePartner.AMAZON,
    // Location fallback state
    val showLocationSetup: Boolean = false,
    val locationSource: String = "gps", // "gps" or "manual"
    val locationErrorReason: LocationErrorReason? = null,
    // Hero image from AI service
    val heroImageUrl: String? = null,
    /**
     * Identity of the hero photo on screen, for flag reports: the
     * Supabase pool row id when [heroImageUrl] came from the pool
     * (null for fallback/curated photos) and the combination context
     * to record with a flag (the served row's own cell when known,
     * otherwise the requested combination).
     */
    val heroImageRowId: String? = null,
    val heroCombinationId: String? = null
)

data class LocationSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<LocationResult> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val preferencesRepository: PreferencesRepository,
    private val affiliateRepository: AffiliateRepository,
    private val heroImageRepository: HeroImageRepository,
    private val locationProvider: LocationProvider,
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase,
    private val feedbackStore: FeedbackStore,
    private val telemetryClient: TelemetryClient,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _locationSearch = MutableStateFlow(LocationSearchState())
    val locationSearch: StateFlow<LocationSearchState> = _locationSearch.asStateFlow()

    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var searchJob: Job? = null

    /**
     * The in-flight weather fetch. A new [fetchWeather] call CANCELS the
     * previous one instead of stacking: duplicate triggers (init, preferences
     * collector, foregrounding, the NOW tick) used to race and land in
     * completion order, so a slow stale fetch could overwrite a newer one.
     */
    private var fetchJob: Job? = null

    /** The in-flight hero-image resolution (see [resolveHeroImage]). */
    private var heroResolveJob: Job? = null

    /** The widget's last-good store — the main screen seeds from it at startup. */
    private val lastGoodStore by lazy { WidgetLastGoodStore(appContext) }

    // ── Feedback + analytics (spec) ─────────────────────────────────────────
    // Everything here is inert unless the user opts in to analytics; feedback
    // sends only on the user's own tap. See the feedback package.

    /** The Settings opt-in toggle state (default OFF — owner's decision). */
    val analyticsOptIn: StateFlow<Boolean> = feedbackStore.analyticsOptInFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _ratingMomentDue = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a rating moment should be shown (triggers + suppression checked). */
    val ratingMomentDue: SharedFlow<Unit> = _ratingMomentDue.asSharedFlow()

    /** Last weather the engine dressed for — outfit diffs only count under identical conditions. */
    private var lastWeather: WeatherConditions? = null
    private var lastOutfitSlots: Map<String, ClothingItem?>? = null
    private var lastAccessories: Set<ClothingItem> = emptySet()

    /** Human-readable per-slot changes this session; shown in the feedback bundle display. */
    private val sessionItemChanges = mutableListOf<String>()

    /** Structured changes for the bundle's allowlisted swappedItems / removedItems arrays. */
    private val sessionSwappedItems = mutableListOf<String>()
    private val sessionRemovedItems = mutableListOf<String>()

    /** Frame state for the now_vs_explicit event; starts in NOW (the default). */
    private var lastFrameMode: String = "now"

    /** Set by refresh(): the outfit on screen just before a conditions refresh. */
    private var preRefreshItems: List<ClothingItem>? = null
    private var pendingRefreshCheck: Boolean = false

    /**
     * Timezone of the selected location (from the weather API). All selection
     * state is framed in this zone. Falls back to the phone's zone only until
     * the first lookup resolves — or if the lookup fails, which degrades to
     * the pre-fix behavior for the GPS case (where the frames coincide).
     */
    private var locationZone: ZoneId = ZoneId.systemDefault()

    /** Current wall-clock time at the selected location. */
    private fun locationNow(): LocalDateTime = LocalDateTime.now(locationZone)

    /**
     * Re-anchor the selection in the selected location's frame. In NOW mode
     * the selected moment becomes the location's now; an explicit selection
     * keeps its wall-clock numbers (they are location-local by definition)
     * and is reinterpreted in the new frame.
     *
     * This performs NO network call: the zone itself is derived from the
     * weather response in [fetchWeather] (`WeatherRepository.recordZone`).
     * A dedicated pre-fetch timezone round-trip used to gate every startup
     * behind a third serial network call before any weather could load.
     */
    private fun resolveLocationZone() {
        val now = locationNow()
        _uiState.update {
            it.copy(
                locationNow = now,
                selectedDateTime = if (it.isNowSelected) now else it.selectedDateTime
            )
        }
    }

    /**
     * Seed the first frame from the widget's last-good snapshot when it was
     * saved for (approximately) this location, so a warm-data open paints
     * real content instantly instead of the skeleton; the fetch that follows
     * refreshes it in place. No-op once content exists or when the snapshot
     * belongs to a different place.
     */
    private suspend fun seedFromLastGood(lat: Double, lon: Double) {
        if (_uiState.value.outfit != null) return
        val lastGood = runCatching { lastGoodStore.read() }.getOrNull() ?: return
        val dLat = lastGood.latitude - lat
        val dLon = lastGood.longitude - lon
        if (dLat * dLat + dLon * dLon > SEED_COORD_TOLERANCE_DEG * SEED_COORD_TOLERANCE_DEG) return
        _uiState.update { state ->
            if (state.outfit != null) state else {
                val seededHero = com.runwear.shared.domain.model.HeroImageSelector.getFallbackImage(
                    lastGood.weather, lastGood.outfit, state.genderPreference
                )
                state.copy(
                    isLoading = false,
                    weather = lastGood.weather,
                    outfit = lastGood.outfit,
                    locationName = lastGood.locationName,
                    heroImageUrl = seededHero.imageUrl,
                    heroImageRowId = null,
                    heroCombinationId = seededHero.combinationId
                )
            }
        }
    }

    // Stale-location guard (F1): a saved GPS fix must not be reused forever.
    // Re-poll GPS on foreground; only adopt the new fix if the user moved
    // significantly, so we never cause a weather-refetch storm.
    private var lastGpsRefreshMs: Long = 0L

    init {
        // Retry anything the feedback system owes the intake (a report that
        // failed to send earlier, queued events). No-ops where not applicable:
        // event flush respects the opt-in toggle inside the client.
        viewModelScope.launch {
            telemetryClient.flushPendingFeedback()
            telemetryClient.flushEvents()
        }

        // Load affiliate partner
        viewModelScope.launch {
            val partner = affiliateRepository.getUserPartner()
            _uiState.update { it.copy(affiliatePartner = partner) }
        }

        // Check for saved manual location first
        viewModelScope.launch {
            val savedLocation = preferencesRepository.getSavedLocation()
            if (savedLocation != null) {
                // Use saved location - skip GPS entirely for this launch.
                // NOTE: the source is preserved ("manual" vs "gps") so a saved
                // GPS fix can be re-validated on foreground (see onForegrounded).
                currentLat = savedLocation.latitude
                currentLon = savedLocation.longitude
                _uiState.update {
                    it.copy(
                        locationName = savedLocation.name,
                        locationSource = savedLocation.source,
                        hasLocationPermission = true, // Bypass permission check
                        isLoading = true
                    )
                }
                seedFromLastGood(savedLocation.latitude, savedLocation.longitude)
                resolveLocationZone()
                fetchWeather()
            }
            // If no saved location, MainActivity will call checkLocationPermission()
        }

        // Observe preferences
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        temperatureUnit = prefs.temperatureUnit,
                        comfortPreference = prefs.comfortPreference,
                        genderPreference = prefs.genderPreference,
                        hasCompletedOnboarding = prefs.hasCompletedOnboarding
                    )
                }
                if (currentLat != null) fetchWeather()
            }
        }

        // NOW tracking: while NOW is selected, keep the displayed moment glued
        // to the location's real time. Refetch only when the hour (or day)
        // rolls over, so the weather never silently goes stale inside NOW.
        viewModelScope.launch {
            while (true) {
                delay(NOW_TICK_MS)
                val now = locationNow()
                val displayed = _uiState.value.selectedDateTime
                _uiState.update {
                    it.copy(
                        locationNow = now,
                        selectedDateTime = if (it.isNowSelected) now else it.selectedDateTime
                    )
                }
                if (_uiState.value.isNowSelected && currentLat != null &&
                    (now.hour != displayed.hour || now.toLocalDate() != displayed.toLocalDate())
                ) {
                    fetchWeather()
                }
            }
        }
    }
    
    fun checkLocationPermission() {
        // Skip if we already have a location (manual or saved GPS)
        if (currentLat != null && currentLon != null) {
            return
        }

        val hasPermission = locationProvider.hasLocationPermission()
        _uiState.update { it.copy(hasLocationPermission = hasPermission) }

        if (hasPermission) {
            fetchCurrentLocation()
        } else {
            // Permission not granted - show location setup screen
            _uiState.update {
                it.copy(
                    isLoading = false,
                    showLocationSetup = true,
                    locationErrorReason = LocationErrorReason.PERMISSION_DENIED
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (!granted) {
            // A permission denial makes today a bad day for the rating moment.
            viewModelScope.launch { feedbackStore.markBadDay() }
        }
        if (granted) {
            _uiState.update { it.copy(showLocationSetup = false) }
            fetchCurrentLocation()
        } else {
            // Permission denied - show location setup screen
            _uiState.update {
                it.copy(
                    isLoading = false,
                    showLocationSetup = true,
                    locationErrorReason = LocationErrorReason.PERMISSION_DENIED
                )
            }
        }
    }

    /**
     * Called from MainActivity.onResume(). Re-validates a saved GPS location
     * against a fresh fix so the app never shows another city's weather
     * forever (F1). A user-chosen MANUAL location is never overwritten.
     * Debounced and distance-gated so this can't cause a refetch storm.
     *
     * @param force adopt the fresh fix regardless of distance (used when the
     * user explicitly switches back to GPS).
     */
    fun onForegrounded(force: Boolean = false) {
        // NOW re-anchor (all location sources): if the hour rolled over while
        // the app was backgrounded, NOW must show the location's new hour.
        if (_uiState.value.isNowSelected) {
            val now = locationNow()
            val displayed = _uiState.value.selectedDateTime
            _uiState.update { it.copy(locationNow = now, selectedDateTime = now) }
            if (currentLat != null &&
                (now.hour != displayed.hour || now.toLocalDate() != displayed.toLocalDate())
            ) {
                fetchWeather()
            }
        }
        // Never override an explicit manual location choice.
        if (_uiState.value.locationSource == "manual") return
        // Debounce: at most one background re-poll per 15 minutes.
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastGpsRefreshMs < GPS_REFRESH_DEBOUNCE_MS) return
        // Nothing to re-validate yet (first launch is handled by checkLocationPermission).
        if (currentLat == null || currentLon == null) return
        if (!locationProvider.hasLocationPermission()) return

        lastGpsRefreshMs = nowMs
        viewModelScope.launch {
            when (val result = locationProvider.getCurrentLocationWithTimeout()) {
                is LocationFetchResult.Success -> {
                    val newLat = result.location.latitude
                    val newLon = result.location.longitude
                    val movedKm = distanceKm(currentLat!!, currentLon!!, newLat, newLon)
                    if (force || movedKm >= STALE_LOCATION_KM) {
                        // User moved significantly: adopt the fresh fix.
                        currentLat = newLat
                        currentLon = newLon
                        weatherRepository.reverseGeocode(newLat, newLon).fold(
                            onSuccess = { name ->
                                _uiState.update { it.copy(locationName = name, locationSource = "gps") }
                                preferencesRepository.saveGPSLocation(newLat, newLon, name)
                            },
                            onFailure = {
                                _uiState.update { it.copy(locationName = "Your Location", locationSource = "gps") }
                                preferencesRepository.saveGPSLocation(newLat, newLon, "Your Location")
                            }
                        )
                        resolveLocationZone()
                        fetchWeather()
                    }
                    // Within STALE_LOCATION_KM: keep the saved fix, no refetch.
                }
                is LocationFetchResult.Error -> {
                    // Keep the saved location silently; a failed background
                    // re-poll must never surface an error or setup screen.
                }
            }
        }
    }

    /** Haversine distance in kilometres. */
    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    companion object {
        /** Adopt a fresh GPS fix only if the user moved at least this far. */
        private const val STALE_LOCATION_KM = 25.0
        /** Minimum interval between background GPS re-polls. */
        private const val GPS_REFRESH_DEBOUNCE_MS = 15 * 60 * 1000L
        /** How often the NOW selection re-checks the location's clock. */
        private const val NOW_TICK_MS = 30_000L
        /** Rating moment fires at the fifth outfit generation (spec addendum). */
        private const val RATING_TRIGGER_GENERATION = 5
        /** Last-good seeding accepts a snapshot within ~0.05° (~5 km) of the fetch target. */
        private const val SEED_COORD_TOLERANCE_DEG = 0.05
        /** Time box for the AI hero lookup; past it the session keeps the default hero. */
        private const val HERO_RESOLVE_TIMEOUT_MS = 2_000L
    }
    
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showLocationSetup = false) }

            // Fast path: Fused's last-known fix resolves instantly when one
            // exists. Painting from it beats blocking the first screen on a
            // fresh GPS fix (seconds on a cold radio, up to the 15 s
            // timeout); onForegrounded's re-validation refines it later.
            val lastKnown = runCatching { locationProvider.getLastKnownLocation() }.getOrNull()
            if (lastKnown != null) {
                adoptGpsFix(lastKnown.latitude, lastKnown.longitude)
                return@launch
            }

            when (val result = locationProvider.getCurrentLocationWithTimeout()) {
                is LocationFetchResult.Success -> {
                    adoptGpsFix(result.location.latitude, result.location.longitude)
                }
                is LocationFetchResult.Error -> {
                    // GPS failed - try to use last saved location as fallback
                    val savedLocation = preferencesRepository.getSavedLocation()
                    if (savedLocation != null) {
                        // Use saved location silently instead of showing setup screen
                        currentLat = savedLocation.latitude
                        currentLon = savedLocation.longitude
                        _uiState.update {
                            it.copy(
                                locationName = savedLocation.name,
                                locationSource = savedLocation.source,
                                isLoading = true
                            )
                        }
                        seedFromLastGood(savedLocation.latitude, savedLocation.longitude)
                        resolveLocationZone()
                        fetchWeather()
                    } else {
                        // No fallback - show location setup screen
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showLocationSetup = true,
                                locationErrorReason = result.reason
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Adopt a GPS fix (last-known or fresh) and load weather for it. The
     * friendly name is cosmetic, so reverse geocoding runs OFF the critical
     * path: it used to be awaited between the fix and the forecast, putting
     * a Nominatim round trip (seconds on a cold connection) in front of the
     * first content on every cold start. The name — and the saved-location
     * record, which carries it — lands whenever the geocode returns.
     */
    private suspend fun adoptGpsFix(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon
        _uiState.update { it.copy(locationSource = "gps") }

        viewModelScope.launch {
            weatherRepository.reverseGeocode(lat, lon).fold(
                onSuccess = { name ->
                    _uiState.update { it.copy(locationName = name, locationSource = "gps") }
                    // Save GPS location for next app launch
                    preferencesRepository.saveGPSLocation(lat, lon, name)
                },
                onFailure = {
                    _uiState.update { it.copy(locationName = "Your Location", locationSource = "gps") }
                    preferencesRepository.saveGPSLocation(lat, lon, "Your Location")
                }
            )
        }

        seedFromLastGood(lat, lon)
        resolveLocationZone()
        fetchWeather()
    }

    private fun fetchWeather() {
        val lat = currentLat ?: return
        val lon = currentLon ?: return

        // Dedup: a new fetch supersedes the in-flight one. Triggers stack up
        // (init, preferences collector, foregrounding, NOW tick, user picks)
        // and used to land in completion order — a slow stale fetch could
        // overwrite fresher state.
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Re-anchor NOW in the location frame on every fetch: if the hour
            // rolled over since the last render, NOW follows the location clock.
            val now = locationNow()
            _uiState.update {
                it.copy(
                    locationNow = now,
                    selectedDateTime = if (it.isNowSelected) now else it.selectedDateTime
                )
            }

            val dt = _uiState.value.selectedDateTime
            val unit = _uiState.value.temperatureUnit
            val nowSelected = _uiState.value.isNowSelected

            // ONE forecast fetch per refresh: current conditions, the
            // selected hour and the hourly list all parse from the same
            // response (this used to be two identical serial requests, plus
            // a timezone round-trip ahead of them).
            val forecastResult = weatherRepository.getForecast(lat, lon, unit)

            // The repository's runCatching also wraps CancellationException:
            // a superseded fetch must land nowhere, not even as an error.
            if (!isActive) return@launch

            forecastResult.fold(
                onSuccess = { response ->
                    // The frame comes FROM this response — no separate
                    // timezone call gates the weather anymore.
                    locationZone = weatherRepository.recordZone(lat, lon, response)

                    val weather = runCatching {
                        // NOW uses the API's current-conditions block
                        // (location's real now); an explicit selection is a
                        // location-local wall time matched against the
                        // location-local hourly forecast.
                        if (nowSelected) {
                            weatherRepository.parseCurrentWeather(response, unit)
                        } else {
                            weatherRepository.parseWeatherForDateTime(response, dt.toKotlinLocalDateTime(), unit)
                        }
                    }.getOrElse { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message ?: "Weather error")
                        }
                        viewModelScope.launch { feedbackStore.markBadDay() }
                        return@fold
                    }
                    val outfit = getOutfitRecommendation.execute(weather, _uiState.value.comfortPreference)
                    val forecast = runCatching {
                        weatherRepository.parseHourlyForecast(response, unit)
                    }.getOrDefault(emptyList())

                    // Feedback + analytics bookkeeping (all inert unless the
                    // user opted in): outfit diff under identical conditions,
                    // the outfit_generated event, and the rating-moment check.
                    val keptThroughRefresh = pendingRefreshCheck &&
                        preRefreshItems != null && preRefreshItems == outfit.allItems
                    pendingRefreshCheck = false
                    preRefreshItems = null
                    onOutfitProduced(weather, outfit, countGeneration = true)
                    evaluateRatingMoment(keptThroughRefresh)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weather = weather,
                            outfit = outfit,
                            hourlyForecast = forecast,
                            // NOW re-anchors on the API's own location-local
                            // current time from this response.
                            locationNow = if (nowSelected) weather.dateTime.toJavaLocalDateTime() else it.locationNow,
                            selectedDateTime = if (nowSelected) weather.dateTime.toJavaLocalDateTime() else it.selectedDateTime
                        )
                    }

                    // Widget write-through (redesign spec §B.3.3): the app
                    // just loaded in the foreground, where location always
                    // succeeds — the home-screen widget inherits the result
                    // via its last-good store instead of re-loading (and
                    // historically, failing) on its own. NOW loads only:
                    // the widget is a "for now" surface.
                    if (_uiState.value.isNowSelected) {
                        viewModelScope.launch {
                            runCatching {
                                val laterLine = LaterTodayLine.compute(
                                    weatherRepository = weatherRepository,
                                    latitude = lat,
                                    longitude = lon,
                                    unit = unit,
                                    current = weather,
                                    currentOutfit = outfit,
                                    comfort = _uiState.value.comfortPreference,
                                    getOutfit = getOutfitRecommendation,
                                    hourly = forecast
                                )
                                WidgetPublisher.publish(
                                    appContext,
                                    WidgetLastGood(
                                        weather = weather,
                                        outfit = outfit,
                                        locationName = _uiState.value.locationName,
                                        latitude = lat,
                                        longitude = lon,
                                        laterTodayLine = laterLine,
                                        savedAtLocal = LocalDateTime.now(),
                                        savedAtEpochMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }

                    // Resolve the hero image for this load — exactly one
                    // URL is chosen and set (see resolveHeroImage).
                    resolveHeroImage(weather, outfit)
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Weather error") }
                    // A failed day suppresses the rating moment (addendum rules).
                    viewModelScope.launch { feedbackStore.markBadDay() }
                }
            )
        }
    }
    
    /**
     * Resolve the hero image for one load — the final URL is chosen BEFORE
     * it is set, so the hero loads exactly once per load. The previous
     * design set the default image instantly and then swapped to the
     * Supabase AI image when it arrived: two large downloads and two
     * crossfades for one hero. Now the AI lookup runs first (time-boxed);
     * whichever URL wins — AI image or the weather-aware default — is the
     * only one the UI ever loads for this weather/outfit. While resolution
     * runs, the previous hero (or the seeded last-good hero) stays on
     * screen; on a true first-ever load the gradient shows briefly.
     */
    private fun resolveHeroImage(weather: WeatherConditions, outfit: OutfitRecommendation) {
        val gender = _uiState.value.genderPreference
        heroResolveJob?.cancel()
        heroResolveJob = viewModelScope.launch {
            val result = runCatching {
                withTimeoutOrNull(HERO_RESOLVE_TIMEOUT_MS) {
                    heroImageRepository.getHeroImage(
                        weather = weather,
                        outfit = outfit,
                        gender = gender
                    )
                }
            }.getOrNull()
            if (!isActive) return@launch
            // Keep the winning result whole: its row id / served cell are
            // the identity a hero-flag report needs. A pool result with
            // no URL (shouldn't happen) degrades to the curated fallback.
            val hero = result?.takeIf { it.imageUrl != null }
                ?: com.runwear.shared.domain.model.HeroImageSelector.getFallbackImage(
                    weather, outfit, gender
                )
            _uiState.update {
                it.copy(
                    heroImageUrl = hero.imageUrl,
                    heroImageRowId = hero.imageRowId,
                    heroCombinationId = hero.servedCombinationId ?: hero.combinationId
                )
            }
        }
    }

    /**
     * Send a hero-flag report for the photo currently on screen.
     * Fire-and-forget: the repository swallows all failures, so this
     * never disturbs the run screen; the UI confirms optimistically.
     */
    fun flagCurrentHero(reason: com.runwear.shared.domain.model.HeroFlagReason) {
        val state = _uiState.value
        val url = state.heroImageUrl ?: return
        viewModelScope.launch {
            heroImageRepository.flagHeroImage(
                imageUrl = url,
                imageRowId = state.heroImageRowId,
                servedCombinationId = state.heroCombinationId,
                requestedCombinationId = state.heroCombinationId,
                reason = reason,
                appVersion = com.runwear.app.BuildConfig.VERSION_NAME
            )
        }
    }
    
    /** Any explicit date/time pick exits NOW into an explicit location-local time. */
    fun setDateTime(dt: LocalDateTime) {
        _uiState.update { it.copy(selectedDateTime = dt, isNowSelected = false) }
        if (lastFrameMode != "explicit") {
            lastFrameMode = "explicit"
            telemetryClient.recordEvent(
                RunWearEvents.NOW_VS_EXPLICIT,
                RunWearEvents.nowVsExplicitProps("explicit")
            )
        }
        fetchWeather()
    }
    
    fun selectHour(hour: Int) {
        val newDateTime = _uiState.value.selectedDateTime.withHour(hour).withMinute(0)
        setDateTime(newDateTime)
        _uiState.update { it.copy(showTimePicker = false) }
    }
    
    fun selectDate(date: LocalDate) {
        val newDateTime = LocalDateTime.of(date, _uiState.value.selectedDateTime.toLocalTime())
        setDateTime(newDateTime)
        _uiState.update { it.copy(showDatePicker = false) }
    }
    
    fun goToNextDay() {
        val newDateTime = _uiState.value.selectedDateTime.plusDays(1)
        // Forecast horizon is 7 days starting at the LOCATION's today.
        if (newDateTime.toLocalDate() <= locationNow().toLocalDate().plusDays(6)) {
            setDateTime(newDateTime)
        }
    }

    fun goToPreviousDay() {
        val prev = _uiState.value.selectedDateTime.minusDays(1)
        // The forecast starts at the location's today 00:00 — stepping before
        // it lands back on NOW instead of a time with no data.
        if (prev.toLocalDate().isBefore(locationNow().toLocalDate())) {
            resetToNow()
        } else {
            setDateTime(prev)
        }
    }

    /** Re-enter NOW: the current instant in the selected location's frame. */
    fun resetToNow() {
        val now = locationNow()
        _uiState.update { it.copy(isNowSelected = true, selectedDateTime = now, locationNow = now) }
        if (lastFrameMode != "now") {
            lastFrameMode = "now"
            telemetryClient.recordEvent(
                RunWearEvents.NOW_VS_EXPLICIT,
                RunWearEvents.nowVsExplicitProps("now")
            )
        }
        fetchWeather()
    }
    
    fun toggleUnit() {
        viewModelScope.launch {
            val newUnit = if (_uiState.value.temperatureUnit == TemperatureUnit.FAHRENHEIT) {
                TemperatureUnit.CELSIUS
            } else {
                TemperatureUnit.FAHRENHEIT
            }
            preferencesRepository.setTemperatureUnit(newUnit)
        }
    }
    
    fun setComfortPreference(preference: ComfortPreference) {
        viewModelScope.launch {
            preferencesRepository.setComfortPreference(preference)
            // Recalculate outfit with new comfort and reload hero image
            val weather = _uiState.value.weather ?: return@launch
            val outfit = getOutfitRecommendation.execute(weather, preference)
            _uiState.update { it.copy(outfit = outfit) }
            // Same conditions, new outfit, user's own action: this is the one
            // true "swap" the app can observe, so the diff is recorded here.
            onOutfitProduced(weather, outfit, countGeneration = false)
            // Hero image depends on outfit hash - must reload
            resolveHeroImage(weather, outfit)
        }
    }
    
    fun showDatePicker() { _uiState.update { it.copy(showDatePicker = true) } }
    fun hideDatePicker() { _uiState.update { it.copy(showDatePicker = false) } }
    fun showTimePicker() { _uiState.update { it.copy(showTimePicker = true) } }
    fun hideTimePicker() { _uiState.update { it.copy(showTimePicker = false) } }
    fun showSettings() { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }
    
    fun refresh() {
        // Snapshot the outfit on screen so a refresh that leaves it unchanged
        // counts as "kept" — one of the two rating-moment triggers.
        preRefreshItems = _uiState.value.outfit?.allItems
        pendingRefreshCheck = preRefreshItems != null
        fetchWeather()
    }
    
    fun searchLocation(query: String) {
        _locationSearch.update { it.copy(query = query) }

        // Cancel previous search job
        searchJob?.cancel()

        if (query.length < 2) {
            _locationSearch.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        // Debounce: wait 300ms before searching (like PWA)
        searchJob = viewModelScope.launch {
            _locationSearch.update { it.copy(isSearching = true) }
            delay(300)
            weatherRepository.searchLocation(query).fold(
                onSuccess = { results -> _locationSearch.update { it.copy(isSearching = false, results = results) } },
                onFailure = { _locationSearch.update { it.copy(isSearching = false, results = emptyList()) } }
            )
        }
    }
    
    fun selectLocation(location: LocationResult) {
        viewModelScope.launch {
            currentLat = location.latitude
            currentLon = location.longitude

            // Persist manual location to DataStore
            preferencesRepository.saveManualLocation(
                location.latitude,
                location.longitude,
                location.displayName
            )

            _uiState.update {
                it.copy(
                    locationName = location.displayName,
                    locationSource = "manual",
                    showLocationSetup = false,
                    hasLocationPermission = true // Bypass permission screen for manual location
                )
            }
            _locationSearch.update { LocationSearchState() }
            // New location = new frame: NOW (if active) is recomputed there.
            resolveLocationZone()
            fetchWeather()
        }
    }

    fun selectManualLocation(lat: Double, lon: Double, name: String) {
        viewModelScope.launch {
            currentLat = lat
            currentLon = lon

            // Persist manual location to DataStore
            preferencesRepository.saveManualLocation(lat, lon, name)

            _uiState.update {
                it.copy(
                    locationName = name,
                    locationSource = "manual",
                    showLocationSetup = false,
                    hasLocationPermission = true // Bypass permission screen for manual location
                )
            }
            _locationSearch.update { LocationSearchState() }
            // New location = new frame: NOW (if active) is recomputed there.
            resolveLocationZone()
            fetchWeather()
        }
    }

    /**
     * Called when user taps "Use Current Location" from setup screen.
     * If permission was previously denied, MainActivity will re-request permission.
     * If it was a timeout, we just retry GPS.
     */
    fun retryGPS() {
        _uiState.update {
            it.copy(
                showLocationSetup = false,
                locationSource = "gps",
                isLoading = true
            )
        }
        // Note: If permission denied, MainActivity handles re-requesting permission
        // If permission was granted but timed out, we can retry directly
        if (locationProvider.hasLocationPermission()) {
            fetchCurrentLocation()
        }
        // Otherwise MainActivity will request permission and call onPermissionResult
    }

    /**
     * Called from Settings when user wants to switch from manual location to GPS.
     */
    fun switchToGPS() {
        viewModelScope.launch {
            preferencesRepository.clearManualLocation()
            _uiState.update { it.copy(locationSource = "gps") }
            // Immediately re-poll GPS and adopt the fix unconditionally.
            lastGpsRefreshMs = 0L
            onForegrounded(force = true)
        }
    }

    /**
     * Check if location permission is currently granted.
     * Used by MainActivity to determine whether to request permission or just retry GPS.
     */
    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    // Onboarding
    fun completeOnboarding(
        tempUnit: TemperatureUnit,
        gender: GenderPreference,
        comfort: ComfortPreference
    ) {
        viewModelScope.launch {
            preferencesRepository.setTemperatureUnit(tempUnit)
            preferencesRepository.setGenderPreference(gender)
            preferencesRepository.setComfortPreference(comfort)
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun setGenderPreference(preference: GenderPreference) {
        viewModelScope.launch {
            preferencesRepository.setGenderPreference(preference)
            _uiState.update { it.copy(genderPreference = preference) }
            // Hero image depends on gender - reload with new combination ID
            val weather = _uiState.value.weather ?: return@launch
            val outfit = _uiState.value.outfit ?: return@launch
            resolveHeroImage(weather, outfit)
        }
    }

    fun getAffiliateLinkForItem(item: ClothingItem): String {
        val partner = _uiState.value.affiliatePartner
        val gender = _uiState.value.genderPreference
        return affiliateRepository.buildAffiliateLink(partner, item, gender)
    }

    // ── Feedback + analytics plumbing ───────────────────────────────────────

    fun setAnalyticsOptIn(enabled: Boolean) {
        viewModelScope.launch {
            feedbackStore.setAnalyticsOptIn(enabled)
            if (enabled) {
                // Opting in starts the flow: anything queued goes out now.
                telemetryClient.flushPendingFeedback()
                telemetryClient.flushEvents()
            }
        }
    }

    /** The bundle the feedback screen shows the user before they send. */
    fun buildFeedbackBundle(): FeedbackBundleFactory.Bundle =
        FeedbackBundleFactory.build(
            _uiState.value,
            sessionItemChanges.toList(),
            sessionSwappedItems.toList(),
            sessionRemovedItems.toList()
        )

    /**
     * Sends a feedback report on the user's tap. On failure the report is
     * queued on the device and retried later; [onResult] receives true when
     * the intake accepted it now, false when it was saved for retry.
     */
    fun submitFeedback(
        category: FeedbackCategory,
        message: String,
        replyEmail: String?,
        entryPoint: String?,
        onResult: (sentNow: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            if (category.isNegative) {
                // Suppresses the rating moment for the rest of this session.
                feedbackStore.negativeFeedbackThisSession = true
            }
            val bundle = buildFeedbackBundle()
            val payload = telemetryClient.buildFeedbackPayload(
                category = category,
                text = message.trim(),
                replyEmail = replyEmail,
                entryPoint = entryPoint,
                bundle = bundle.json
            )
            val sent = telemetryClient.sendFeedbackNow(payload)
            if (!sent) {
                feedbackStore.enqueuePendingFeedback(payload.toString())
            }
            onResult(sent)
        }
    }

    /** Category only — never the product, never the price (spec §3.1). */
    fun onAffiliateItemTapped(item: ClothingItem) {
        telemetryClient.recordEvent(
            RunWearEvents.AFFILIATE_TAPPED,
            RunWearEvents.affiliateTappedProps(item.category.name.lowercase())
        )
    }

    /** Called when the rating-moment card actually appears on screen. */
    fun onRatingMomentShown() {
        viewModelScope.launch { feedbackStore.markRatingMomentShown() }
        // No analytics event: the intake's runwear-events/1 allowlist
        // has no rating-moment event name, and one unlisted event
        // would reject the whole queued batch (422). The moment is
        // stamped in the store only.
    }

    private fun slotMap(outfit: OutfitRecommendation): Map<String, ClothingItem?> = linkedMapOf(
        "base" to outfit.topBase,
        "mid" to outfit.topMid,
        "outer" to outfit.topOuter,
        "bottom" to outfit.bottom,
        "head" to outfit.head,
        "hands" to outfit.hands
    )

    private fun slotLabel(slot: String): String = when (slot) {
        "base" -> "Base layer"
        "mid" -> "Mid layer"
        "outer" -> "Outer layer"
        "bottom" -> "Bottom"
        "head" -> "Head"
        "hands" -> "Hands"
        else -> slot
    }

    /**
     * Bookkeeping for every outfit the engine produces: the swap/removal diff
     * (only meaningful under identical conditions — a comfort change, or a
     * refetch that returned the same weather) and the outfit_generated event.
     */
    private fun onOutfitProduced(weather: WeatherConditions, outfit: OutfitRecommendation, countGeneration: Boolean) {
        if (lastWeather == weather && lastOutfitSlots != null) {
            val previous = lastOutfitSlots!!
            val current = slotMap(outfit)
            for ((slot, oldItem) in previous) {
                val newItem = current[slot]
                when {
                    oldItem != null && newItem == null -> {
                        sessionItemChanges += "${slotLabel(slot)} removed: ${oldItem.displayName}"
                        sessionRemovedItems += oldItem.displayName
                        telemetryClient.recordEvent(
                            RunWearEvents.ITEM_REMOVED,
                            RunWearEvents.itemRemovedProps(slot, oldItem.displayName)
                        )
                    }
                    oldItem != null && newItem != null && oldItem != newItem -> {
                        sessionItemChanges +=
                            "${slotLabel(slot)} changed: ${oldItem.displayName} to ${newItem.displayName}"
                        sessionSwappedItems += newItem.displayName
                        telemetryClient.recordEvent(
                            RunWearEvents.ITEM_SWAPPED,
                            RunWearEvents.itemSwappedProps(
                                slot,
                                oldItem.displayName,
                                newItem.displayName
                            )
                        )
                    }
                }
            }
            val newAccessories = outfit.accessories.toSet()
            for (removed in lastAccessories - newAccessories) {
                sessionItemChanges += "Accessory removed: ${removed.displayName}"
                sessionRemovedItems += removed.displayName
                telemetryClient.recordEvent(
                    RunWearEvents.ITEM_REMOVED,
                    RunWearEvents.itemRemovedProps("accessories", removed.displayName)
                )
            }
            lastAccessories = newAccessories
        } else if (lastWeather != weather) {
            lastAccessories = outfit.accessories.toSet()
        }
        lastWeather = weather
        lastOutfitSlots = slotMap(outfit)

        telemetryClient.recordEvent(
            RunWearEvents.OUTFIT_GENERATED,
            RunWearEvents.outfitGeneratedProps(
                engineVersion = GetOutfitRecommendationUseCase.ENGINE_VERSION,
                tempBand = tempBand(weather.feelsLikeInFahrenheit),
                comfortClass = _uiState.value.comfortPreference.shortLabel,
                itemCount = outfit.allItems.size
            )
        )
    }

    /**
     * The rating moment's two triggers (spec addendum): the fifth outfit
     * generation, or an outfit kept unchanged through a conditions refresh.
     * All suppression rules are checked inside the store.
     */
    private fun evaluateRatingMoment(keptThroughRefresh: Boolean) {
        viewModelScope.launch {
            val generation = feedbackStore.recordOutfitGeneration()
            val triggered = generation == RATING_TRIGGER_GENERATION || keptThroughRefresh
            if (triggered && feedbackStore.isRatingMomentEligible()) {
                _ratingMomentDue.tryEmit(Unit)
            }
        }
    }

    /** Feels-like temperature bands for the outfit_generated event. */
    private fun tempBand(feelsLikeF: Double): String = when {
        feelsLikeF < 20 -> "under_20"
        feelsLikeF < 32 -> "20_32"
        feelsLikeF < 45 -> "32_45"
        feelsLikeF < 55 -> "45_55"
        feelsLikeF < 65 -> "55_65"
        feelsLikeF < 75 -> "65_75"
        feelsLikeF < 85 -> "75_85"
        else -> "85_plus"
    }
}
