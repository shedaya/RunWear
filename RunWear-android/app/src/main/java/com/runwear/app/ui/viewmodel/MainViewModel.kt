package com.runwear.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runwear.shared.data.repository.AffiliateRepository
import com.runwear.shared.data.repository.HeroImageRepository
import com.runwear.shared.data.repository.HeroServeTelemetry
import com.runwear.shared.data.repository.LocationResult
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.AffiliatePartner
import com.runwear.shared.domain.model.AppearancePreference
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
import com.runwear.app.BuildConfig
import com.runwear.app.feedback.FeedbackBundleFactory
import com.runwear.app.feedback.HeroServeDiagnostics
import com.runwear.app.feedback.FeedbackCategory
import com.runwear.app.feedback.FeedbackStore
import com.runwear.app.feedback.RunWearEvents
import com.runwear.app.feedback.TelemetryClient
import com.runwear.app.makeitme.MakeItMeUpsellRules
import com.runwear.app.makeitme.PersonalHeroRepository
import com.runwear.app.makeitme.PersonalHeroService
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
     * WS-4 (RC-4): the current place as a simple-place value, derived
     * ONCE at geocode/selection time. [locationName] is always this
     * value's display string ("New York, NY") once a place is known —
     * never a raw geocode string. Null only before any place resolves
     * (or for the "Your Location" fallback, which carries no region).
     */
    val simplePlace: com.runwear.shared.domain.model.SimplePlace? = null,
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
    /** RW-25: appearance preference driving the resolved app theme. */
    val appearancePreference: AppearancePreference = AppearancePreference.SYSTEM,
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
    val heroCombinationId: String? = null,
    /**
     * Every approved image for the current hero cell, for the refresh
     * control (cycle without flagging). Empty when the hero did not
     * come from a pool resolution.
     */
    val heroCandidates: List<com.runwear.shared.domain.model.HeroCandidate> = emptyList(),
    /**
     * The REQUESTED combination the current hero resolution belongs
     * to — the cell key under which the day's refresh choice persists.
     */
    val heroRequestedCombinationId: String? = null,
    // ── RW-14 "Make It Me" ─────────────────────────────────────────────
    /** True when the hero on screen is the user's verified personal hero. */
    val heroIsPersonal: Boolean = false,
    /**
     * RW-16.27: the backend row id of the personal hero on screen —
     * what a personal flag (flag_hero) addresses. Null unless a
     * personal hero is displayed (or its id is not yet known).
     */
    val personalHeroId: String? = null,
    /**
     * RW-16.27: the current cell's verified personal hero URL, held
     * separately from [heroImageUrl] so the refresh rotation can
     * include the personal photo alongside the pool candidates (the
     * pre-RW-16.27 overlay discarded the candidate list when it
     * swapped the personal photo in, which killed the refresh
     * control in exactly the state where the user most needs a way
     * off a bad photo). Null when no personal hero serves this cell.
     */
    val personalHeroImageUrl: String? = null,
    /**
     * RW-16.16: the current cell's personal-look status, as the
     * SERVER reports it (spec §7 G1/G2 progress line while MAKING;
     * G5/G6 failed line + retry while FAILED). Server-driven, so it
     * survives navigation and process death — the pre-RW-16.16
     * session flag (with G4 silence on failure) is gone.
     */
    val personalLookStatus: com.runwear.app.makeitme.PersonalLookStatus =
        com.runwear.app.makeitme.PersonalLookStatus.None,
    /** The 6th-new-cell subscription card should show (spec §5). */
    val showMakeItMeUpsell: Boolean = false,
    /** RW-26: how the personal hero was served (exact vs scaffold). */
    val heroServeKind: com.runwear.app.makeitme.PersonalServeKind =
        com.runwear.app.makeitme.PersonalServeKind.EXACT
)

data class LocationSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<LocationResult> = emptyList()
)

/**
 * RW-16.27: one entry of the hero refresh rotation — the cell's
 * personal hero (when one serves) or one pool candidate
 * ([candidateIndex] indexes the pool list; -1 for the personal
 * entry). Pure value type so the rotation arithmetic is unit-tested
 * without the ViewModel.
 */
internal data class HeroRotationEntry(
    val imageUrl: String,
    val isPersonal: Boolean,
    val candidateIndex: Int
)

/**
 * The refresh rotation for one cell: the personal hero leads (it is
 * the overlay's winner), then the pool candidates in order. With no
 * personal hero this is exactly the pre-RW-16.27 pool-only rotation.
 */
internal fun heroRotation(
    candidates: List<com.runwear.shared.domain.model.HeroCandidate>,
    personalImageUrl: String?
): List<HeroRotationEntry> = buildList {
    if (personalImageUrl != null) {
        add(HeroRotationEntry(personalImageUrl, isPersonal = true, candidateIndex = -1))
    }
    candidates.forEachIndexed { index, candidate ->
        add(HeroRotationEntry(candidate.imageUrl, isPersonal = false, candidateIndex = index))
    }
}

/**
 * The rotation entry after the one showing [currentImageUrl], wrapping
 * at the end; a current photo outside the rotation starts at the first
 * entry. Null when the rotation has fewer than two entries (nothing to
 * cycle to — the control stays hidden).
 */
internal fun nextHeroInRotation(
    rotation: List<HeroRotationEntry>,
    currentImageUrl: String?
): HeroRotationEntry? {
    if (rotation.size <= 1) return null
    val currentIdx = rotation.indexOfFirst { it.imageUrl == currentImageUrl }
    return rotation[(currentIdx + 1) % rotation.size]
}

/**
 * GEN-2 A2 (W-6): the ONE state commit that lands a fetch's results.
 * The fetched weather, outfit and forecast, the location-frame anchor,
 * and — when a place switch is in flight — the pending place's name
 * all swap in this single copy, so no rendered frame can pair the new
 * place with the old place's weather (the walkthrough's "New York,
 * NY" over Kansas City's 44° frame). With no place pending, the place
 * fields carry over untouched and only the conditions move. Pure, so
 * the pairing invariant is unit-tested without the ViewModel: the
 * returned state is the only state a switch ever produces mid-flight —
 * there is no intermediate state with the new name and old weather.
 *
 * NOW re-anchors on the API's own location-local current time from
 * this response; an explicit selection keeps its wall-clock moment
 * and re-anchors only [MainUiState.locationNow] to [locationNowAnchor].
 */
internal fun MainUiState.withFetchedConditions(
    weather: WeatherConditions,
    outfit: OutfitRecommendation,
    forecast: List<HourlyForecast>,
    pendingPlace: com.runwear.shared.domain.model.SimplePlace?,
    locationNowAnchor: LocalDateTime,
    nowSelected: Boolean
): MainUiState {
    val weatherNow = weather.dateTime.toJavaLocalDateTime()
    return copy(
        isLoading = false,
        weather = weather,
        outfit = outfit,
        hourlyForecast = forecast,
        locationName = pendingPlace?.display ?: locationName,
        simplePlace = pendingPlace ?: simplePlace,
        locationNow = if (nowSelected) weatherNow else locationNowAnchor,
        selectedDateTime = if (nowSelected) weatherNow else selectedDateTime
    )
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val preferencesRepository: PreferencesRepository,
    private val affiliateRepository: AffiliateRepository,
    private val heroImageRepository: HeroImageRepository,
    private val personalHeroRepository: PersonalHeroRepository,
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

    /**
     * GEN-2 A2 (W-6): a place the user has switched to whose weather
     * has not landed yet. selectLocation / selectManualLocation record
     * it here instead of writing the name into state; fetchWeather's
     * success commit (withFetchedConditions) moves the name, the
     * weather and the frame anchor in ONE state write. Until then the
     * screen keeps showing the previous place whole — name, weather
     * and photo in agreement.
     */
    @Volatile
    private var pendingPlace: com.runwear.shared.domain.model.SimplePlace? = null

    /** The widget's last-good store — the main screen seeds from it at startup. */
    private val lastGoodStore by lazy { WidgetLastGoodStore(appContext) }

    /**
     * WS-2 (RC-2, D-05): completes when the FIRST preferences emission
     * has been applied to [_uiState]. The last-good seed awaits it, so
     * the first paint is determined by the user's actual preferences
     * (their unit included) — never by a default that later flips
     * when the collector lands.
     */
    private val preferencesApplied = kotlinx.coroutines.CompletableDeferred<Unit>()

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
        // WS-2: never paint the seed before the user's preferences
        // (the unit above all) are the state the paint projects from.
        preferencesApplied.await()
        if (_uiState.value.outfit != null) return
        val lastGood = runCatching { lastGoodStore.read() }.getOrNull() ?: return
        val dLat = lastGood.latitude - lat
        val dLon = lastGood.longitude - lon
        if (dLat * dLat + dLon * dLon > SEED_COORD_TOLERANCE_DEG * SEED_COORD_TOLERANCE_DEG) return
        _uiState.update { state ->
            if (state.outfit != null) state else {
                val seededHero = com.runwear.shared.domain.model.HeroImageSelector.getFallbackImage(
                    lastGood.weather, lastGood.outfit, state.genderPreference,
                    frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(lastGood.weather, _uiState.value.comfortPreference)
                )
                // WS-4: a last-good snapshot may predate the simple-place
                // model; normalize its name at this seam.
                val seededPlace = com.runwear.shared.domain.model.SimplePlace
                    .fromRawDisplayString(lastGood.locationName)
                state.copy(
                    isLoading = false,
                    weather = lastGood.weather,
                    outfit = lastGood.outfit,
                    locationName = seededPlace.display,
                    simplePlace = seededPlace,
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
                // WS-4: normalize a saved name that may predate the
                // simple-place model ("New York, New York" on disk).
                val savedPlace = com.runwear.shared.domain.model.SimplePlace
                    .fromRawDisplayString(savedLocation.name)
                _uiState.update {
                    it.copy(
                        locationName = savedPlace.display,
                        simplePlace = savedPlace,
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

        // Observe preferences. WS-2 (RC-2, D-05): a preference write
        // ONLY updates state — it never refetches weather. Weather is
        // stored canonically (°F) and the unit is a render-time
        // projection, so this state update re-renders every figure in
        // the new unit in the same frame; the pre-WS-2 collector
        // refetched on every emission, which is what painted the
        // screen in the other unit mid-session (and raced the fetch
        // it superseded). Comfort/gender writes have their own
        // recalculation paths in their setters.
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        temperatureUnit = prefs.temperatureUnit,
                        comfortPreference = prefs.comfortPreference,
                        genderPreference = prefs.genderPreference,
                        appearancePreference = prefs.appearancePreference,
                        hasCompletedOnboarding = prefs.hasCompletedOnboarding
                    )
                }
                if (!preferencesApplied.isCompleted) preferencesApplied.complete(Unit)
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
                        weatherRepository.reverseGeocodePlace(newLat, newLon).fold(
                            onSuccess = { place ->
                                _uiState.update {
                                    it.copy(locationName = place.display, simplePlace = place, locationSource = "gps")
                                }
                                preferencesRepository.saveGPSLocation(newLat, newLon, place.display)
                            },
                            onFailure = {
                                _uiState.update {
                                    it.copy(
                                        locationName = "Your Location",
                                        simplePlace = com.runwear.shared.domain.model.SimplePlace("Your Location"),
                                        locationSource = "gps"
                                    )
                                }
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
        /** RW-14: personal-generation poll cadence + bound (~4.5 min total, matching the backend's 2–4 min job time). */
        private const val PERSONAL_POLL_INTERVAL_MS = 15_000L
        private const val PERSONAL_POLL_MAX_ATTEMPTS = 18
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
                        val savedPlace = com.runwear.shared.domain.model.SimplePlace
                            .fromRawDisplayString(savedLocation.name)
                        _uiState.update {
                            it.copy(
                                locationName = savedPlace.display,
                                simplePlace = savedPlace,
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
            weatherRepository.reverseGeocodePlace(lat, lon).fold(
                onSuccess = { place ->
                    _uiState.update {
                        it.copy(locationName = place.display, simplePlace = place, locationSource = "gps")
                    }
                    // Save GPS location for next app launch
                    preferencesRepository.saveGPSLocation(lat, lon, place.display)
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            locationName = "Your Location",
                            simplePlace = com.runwear.shared.domain.model.SimplePlace("Your Location"),
                            locationSource = "gps"
                        )
                    }
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
            // GEN-2 A2 (W-6): the anchor is computed LOCALLY and committed
            // with the fetched conditions in the success write below — the
            // pre-fix code wrote it to state up front, so during a place
            // switch the screen transiently paired the new place's frame
            // with the old place's weather (and selectLocation had already
            // committed the new NAME — one frame literally read
            // "New York, NY" over Kansas City's 44°). No state field that
            // describes the place may move before its weather lands.
            val now = locationNow()
            val dt = _uiState.value.selectedDateTime
            val nowSelected = _uiState.value.isNowSelected

            // ONE forecast fetch per refresh: current conditions, the
            // selected hour and the hourly list all parse from the same
            // response (this used to be two identical serial requests, plus
            // a timezone round-trip ahead of them).
            // WS-2: the fetch carries no unit — canonical (°F) always.
            val forecastResult = weatherRepository.getForecast(lat, lon)

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
                            weatherRepository.parseCurrentWeather(response)
                        } else {
                            weatherRepository.parseWeatherForDateTime(response, dt.toKotlinLocalDateTime())
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
                        weatherRepository.parseHourlyForecast(response)
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

                    // GEN-2 A2 (W-6): ONE commit carries the fetched
                    // conditions AND the pending place (withFetchedConditions):
                    // place name, weather values and the frame anchor swap
                    // in the same state write, so no rendered frame can pair
                    // the new place with the old place's weather.
                    val placeToCommit = pendingPlace
                    _uiState.update {
                        it.withFetchedConditions(
                            weather = weather,
                            outfit = outfit,
                            forecast = forecast,
                            pendingPlace = placeToCommit,
                            locationNowAnchor = now,
                            nowSelected = nowSelected
                        )
                    }
                    if (placeToCommit != null) pendingPlace = null

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
                                    displayUnit = _uiState.value.temperatureUnit,
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
     * RW-16.27: the personal overlay's lookup now also runs BEFORE the
     * write — the exactly-once rule covers the personal photo too (the
     * overlay used to write a second time after this write; RW-16.26).
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
                        gender = gender,
                        comfort = _uiState.value.comfortPreference
                    )
                }
            }.getOrNull()
            if (!isActive) return@launch
            // Keep the winning result whole: its row id / served cell are
            // the identity a hero-flag report needs. A pool result with
            // no URL (shouldn't happen) degrades to the curated fallback.
            val hero = result?.takeIf { it.imageUrl != null }
                ?: com.runwear.shared.domain.model.HeroImageSelector.getFallbackImage(
                    weather, outfit, gender,
                    frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(weather, _uiState.value.comfortPreference)
                ).also { selectorHero ->
                    // GEN-2 A2: the gated selector may resolve to NO
                    // photo (no bundled asset matches the prescription):
                    // that is not a serve and files no serve record.
                    if (selectorHero.imageUrl == null) return@also
                    // WS-7 / RW-26(c): the resolve-timeout substitution
                    // is a bundled serve the repository never emitted a
                    // record for — emit it here, with its trigger named.
                    // (RW-26(b)'s 2,000 ms budget itself is unchanged.)
                    recordHeroServe(
                        com.runwear.shared.domain.model.HeroServeRecord(
                            source = com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK,
                            requestedCombinationId = selectorHero.combinationId,
                            gender = gender,
                            fallbackAssetFile = selectorHero.fallbackAssetFile,
                            fallbackProvenance = selectorHero.fallbackProvenance,
                            trigger = "resolve-timeout"
                        )
                    )
                }
            // If the user already used the refresh control today for
            // this cell, restore their pick over the random one.
            val chosen = applyPersistedHeroChoice(hero)

            // RW-16.27 (FIX 2): when the personal layer may overlay
            // this cell, run its lookup BEFORE any state write and
            // compose the final hero in ONE write. The pre-fix flow
            // wrote the fresh pool pick first and swapped the personal
            // photo in after its network round-trip, so on every
            // re-resolution a pool stranger flashed on screen and the
            // personal photo then crossfaded over it — two people
            // ghosting for half a second (RW-16.26). Now, while a
            // resolution is in flight, the previously displayed hero
            // simply stays on screen; the write that lands is the
            // winner. (The view layer cuts on a person change and
            // crossfades only within a layer — see HeroSection.)
            // Make It Me OFF in this build (MakeItMeAvailability):
            // personalLayerEngages is false whatever the stored
            // opt-in says, so the personal layer never engages —
            // no personal serve, no overlay, no automatic
            // generation request, no upsell evaluation. Pool only.
            if (!com.runwear.app.makeitme.MakeItMeAvailability.personalLayerEngages(
                    personalHeroRepository.state.value.personalizationOn
                )
            ) {
                personalCellInView = null
                writePoolHero(chosen)
                return@launch
            }
            val (cell, items) = personalCombinationFor(weather, outfit)
            personalCellInView = cell
            val look = runCatching { personalHeroRepository.cellLookFor(cell) }.getOrNull()
            if (!isActive || personalCellInView != cell) return@launch
            val hit = look?.hit
            if (hit != null) {
                // RW-14: the user's own verified image for this cell
                // wins (spec §2.4/§4). The pool resolution is kept
                // whole underneath it — its candidate list is what the
                // refresh control rotates through, personal photo
                // included (RW-16.27 FIX 1).
                val status = when (look.status) {
                    // A serving hero with no reported state is READY by
                    // definition; the server's explicit FAILED / MAKING
                    // pass through untouched.
                    com.runwear.app.makeitme.PersonalLookStatus.None ->
                        com.runwear.app.makeitme.PersonalLookStatus.Ready
                    else -> look.status
                }
                recordHeroServe(
                    // WS-9 fix: the record is built by the shared
                    // production seam (HeroServeTelemetry) — the
                    // personal/scaffold classification lives there,
                    // once, for both serve sites.
                    HeroServeTelemetry.personalServeRecord(
                        isScaffold = hit.serveKind ==
                            com.runwear.app.makeitme.PersonalServeKind.SCAFFOLD,
                        requestedCombinationId = chosen.combinationId,
                        servedCombinationId = chosen.servedCombinationId ?: chosen.combinationId,
                        gender = gender,
                        trigger = "personal-overlay"
                    )
                )
                _uiState.update {
                    it.copy(
                        heroImageUrl = hit.imageUrl,
                        heroImageRowId = null,
                        heroCombinationId = chosen.servedCombinationId ?: chosen.combinationId,
                        heroCandidates = chosen.candidates,
                        heroRequestedCombinationId = chosen.combinationId,
                        heroIsPersonal = true,
                        heroServeKind = hit.serveKind,
                        personalHeroId = hit.heroId,
                        personalHeroImageUrl = hit.imageUrl,
                        personalLookStatus = status,
                        showMakeItMeUpsell = false
                    )
                }
                if (status == com.runwear.app.makeitme.PersonalLookStatus.Making) {
                    pollForPersonalHero(cell)
                }
                return@launch
            }
            // No personal hero for this cell: the pool hero serves,
            // written once, and the overlay's remainder (look-state
            // lines, the on-demand request, the upsell evaluation)
            // runs against it.
            writePoolHero(chosen)
            if (!isActive || personalCellInView != cell) return@launch
            applyPersonalHeroOverlay(cell, items, look)
        }
    }

    /**
     * WS-7 / RW-26(c): record one hero serve. Every record rides the
     * HeroServe channel (HeroServeTelemetry); a BUNDLED-FALLBACK serve
     * additionally files remotely under the automated `diagnostics`
     * category (HeroServeDiagnostics) — the bundled serve is the one
     * serve with no server-side trace, and D-18 is the failure that
     * closes. Filing is fire-and-forget; failures are dropped, never
     * queued (a stale serve report would misattribute).
     */
    private fun recordHeroServe(record: com.runwear.shared.domain.model.HeroServeRecord) {
        HeroServeTelemetry.emit(record)
        if (record.source == com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK) {
            viewModelScope.launch {
                runCatching {
                    val payload = HeroServeDiagnostics.payload(
                        record, BuildConfig.VERSION_NAME, feedbackStore.installId()
                    )
                    telemetryClient.sendFeedbackNow(payload)
                }
            }
        }
    }

    /** The pool layer's hero write for one resolution. */
    private fun writePoolHero(chosen: com.runwear.shared.domain.model.HeroImageResult) {
        // WS-7: a bundled paint from the pool layer files its
        // diagnostics record at paint time (the repository emitted the
        // serve record when it resolved; this is the remote filing).
        // GEN-2 A2: a null URL is a resolved NO-photo (the garment
        // gate exhausted) — nothing was served, nothing is filed.
        val chosenUrl = chosen.imageUrl
        if (chosenUrl != null &&
            (chosen.serveSource == com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK ||
                chosenUrl.startsWith("file:///android_asset/hero_fallback/"))
        ) {
            recordHeroServeDiagnosticsOnly(chosen)
        }
        _uiState.update {
            it.copy(
                heroImageUrl = chosen.imageUrl,
                heroImageRowId = chosen.imageRowId,
                heroCombinationId = chosen.servedCombinationId ?: chosen.combinationId,
                heroCandidates = chosen.candidates,
                heroRequestedCombinationId = chosen.combinationId,
                heroIsPersonal = false,
                personalHeroId = null,
                personalHeroImageUrl = null,
                personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.None,
                showMakeItMeUpsell = false
            )
        }
    }

    /** File a bundled serve's diagnostics record without re-emitting its HeroServe record. */
    private fun recordHeroServeDiagnosticsOnly(chosen: com.runwear.shared.domain.model.HeroImageResult) {
        val record = com.runwear.shared.domain.model.HeroServeRecord(
            source = com.runwear.shared.domain.model.HeroServeSource.BUNDLED_FALLBACK,
            requestedCombinationId = chosen.combinationId,
            servedCombinationId = chosen.servedCombinationId,
            fallbackAssetFile = chosen.fallbackAssetFile,
            fallbackProvenance = chosen.fallbackProvenance,
            trigger = "bundled-paint"
        )
        viewModelScope.launch {
            runCatching {
                val payload = HeroServeDiagnostics.payload(
                    record, BuildConfig.VERSION_NAME, feedbackStore.installId()
                )
                telemetryClient.sendFeedbackNow(payload)
            }
        }
    }

    /** The personal cell currently in view (poll guard for the overlay). */
    @Volatile
    private var personalCellInView: String? = null

    /**
     * The current recommendation's PERSONAL combination: built with the
     * user's own gender preference (never the pool's randomized hero
     * gender) so its id matches the backend's per-user keying, plus the
     * outfit item keys the generation request must hash-match.
     */
    fun currentPersonalCombination(): Pair<String, List<String>>? {
        val weather = _uiState.value.weather ?: return null
        val outfit = _uiState.value.outfit ?: return null
        return personalCombinationFor(weather, outfit)
    }

    private fun personalCombinationFor(
        weather: WeatherConditions,
        outfit: OutfitRecommendation
    ): Pair<String, List<String>> {
        val combination = com.runwear.shared.domain.model.OutfitCombination.fromConditions(
            weather, outfit, _uiState.value.genderPreference,
            frameFeelsLikeF = GetOutfitRecommendationUseCase.adjustedFeelsLikeF(weather, _uiState.value.comfortPreference)
        )
        return combination.id to outfit.allItems.map { it.name }
    }

    /**
     * RW-14 overlay remainder, run by [resolveHeroImage] after the
     * hero write for a load has landed with the POOL photo (the
     * personal-hit case is composed into that write — see
     * resolveHeroImage). RW-16.16: the overlay is driven by the
     * SERVER's per-cell look state (get-personal-hero's look_state):
     *
     * - State MAKING (a job is in flight, whoever requested it — the
     *   save flow included) → show the progress line and poll until
     *   the state resolves; a delivery swaps the personal photo in
     *   (the poll's write — a person change, so the view cuts). The
     *   persisted requested-cell set no longer suppresses this: it
     *   gates only the AUTOMATIC request.
     * - State FAILED → the failed line + retry render, durably. No
     *   automatic re-request, ever (the anti-loop rule stands; the
     *   retry is the user's tap, spec §7 G6).
     * - State NONE, user opted in, cell never automatically
     *   requested, and either entitled or free allowance remains →
     *   request generation and poll.
     * - Otherwise → evaluate the 6th-cell upsell card (spec §5.2).
     */
    private suspend fun applyPersonalHeroOverlay(
        cell: String,
        items: List<String>,
        look: PersonalHeroRepository.CellLook?
    ) {
        val personal = personalHeroRepository.state.value

        when (look?.status) {
            com.runwear.app.makeitme.PersonalLookStatus.Making -> {
                _uiState.update {
                    it.copy(personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Making)
                }
                pollForPersonalHero(cell)
                return
            }
            is com.runwear.app.makeitme.PersonalLookStatus.Failed -> {
                _uiState.update { it.copy(personalLookStatus = look.status) }
                updateUpsellVisibility(cell, cellHasPersonalHero = false)
                return
            }
            else -> Unit // NONE (or lookup unreachable): fall through.
        }

        val entitled = personal.entitled
        val allowanceLeft = personal.freeGenerationsUsed < MakeItMeUpsellRules.FREE_ALLOWANCE
        val alreadyRequested = personalHeroRepository.wasCellRequested(cell)
        if (!alreadyRequested && (entitled || allowanceLeft)) {
            _uiState.update {
                it.copy(personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Making)
            }
            val outcome = runCatching {
                personalHeroRepository.requestGeneration(cell, items)
            }.getOrNull()
            val accepted = outcome is PersonalHeroService.RequestOutcome.Queued ||
                outcome is PersonalHeroService.RequestOutcome.AlreadyQueued ||
                outcome is PersonalHeroService.RequestOutcome.AlreadyAvailable
            if (accepted) {
                pollForPersonalHero(cell)
            } else {
                // The request itself was refused (the allowance may
                // have been exhausted server-side between the state
                // read and the request): re-read the server state so
                // the screen tells the truth, refresh the mirror, and
                // fall through to the upsell evaluation.
                val truth = runCatching { personalHeroRepository.cellLookFor(cell) }.getOrNull()
                if (personalCellInView == cell && truth != null) {
                    _uiState.update { it.copy(personalLookStatus = truth.status) }
                } else if (personalCellInView == cell) {
                    _uiState.update {
                        it.copy(personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.None)
                    }
                }
                personalHeroRepository.refreshFromServer()
                updateUpsellVisibility(cell, cellHasPersonalHero = false)
            }
            return
        }

        // No automatic request (already made once, or no allowance):
        // whatever the server last reported stands — including a
        // FAILED the user hasn't retried yet.
        if (look != null && personalCellInView == cell) {
            _uiState.update { it.copy(personalLookStatus = look.status) }
        }
        updateUpsellVisibility(cell, cellHasPersonalHero = false)
    }

    /**
     * Poll get-personal-hero while the cell's look is MAKING, until
     * the state resolves (a delivery swaps the hero in; a terminal
     * failure lands the failed line) or the wait expires (~4.5 min).
     * On expiry the last server-reported status STAYS on screen —
     * it is durable state, not a spinner to dismiss (RW-16.16).
     */
    private suspend fun pollForPersonalHero(cell: String) {
        var attempts = 0
        while (attempts < PERSONAL_POLL_MAX_ATTEMPTS) {
            delay(PERSONAL_POLL_INTERVAL_MS)
            attempts++
            if (personalCellInView != cell) return
            val look = runCatching { personalHeroRepository.cellLookFor(cell) }.getOrNull()
                ?: continue
            val hit = look.hit
            if (hit != null && _uiState.value.heroImageUrl != hit.imageUrl) {
                recordHeroServe(
                    // WS-9 fix: built by the shared production seam
                    // (see the overlay site in resolveHeroImage).
                    HeroServeTelemetry.personalServeRecord(
                        isScaffold = hit.serveKind ==
                            com.runwear.app.makeitme.PersonalServeKind.SCAFFOLD,
                        requestedCombinationId = cell,
                        gender = _uiState.value.genderPreference,
                        trigger = "personal-poll-delivery"
                    )
                )
                // RW-16.27: the pool candidate list survives this swap
                // (it is the refresh rotation; emptying it killed the
                // refresh control in the personal state), and the
                // hero's identity is recorded for the personal flag.
                _uiState.update {
                    it.copy(
                        heroImageUrl = hit.imageUrl,
                        heroImageRowId = null,
                        heroIsPersonal = true,
                        heroServeKind = hit.serveKind,
                        personalHeroId = hit.heroId,
                        personalHeroImageUrl = hit.imageUrl,
                        showMakeItMeUpsell = false
                    )
                }
                // A verified delivery moves the free counter server-side.
                personalHeroRepository.refreshFromServer()
            }
            when (look.status) {
                com.runwear.app.makeitme.PersonalLookStatus.Making -> {
                    _uiState.update {
                        it.copy(personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Making)
                    }
                }
                is com.runwear.app.makeitme.PersonalLookStatus.Failed -> {
                    _uiState.update { it.copy(personalLookStatus = look.status) }
                    return
                }
                else -> {
                    // READY (the delivery above, or a hero that was
                    // already serving) or NONE (state cleared): the
                    // look is resolved; settle the status and stop.
                    _uiState.update {
                        it.copy(
                            personalLookStatus =
                                if (hit != null) com.runwear.app.makeitme.PersonalLookStatus.Ready
                                else look.status
                        )
                    }
                    return
                }
            }
        }
        // Wait expired with the look still in flight server-side:
        // the MAKING status remains rendered (it is the server's
        // truth, re-read on the next overlay run) instead of the old
        // G4 behavior of clearing the line into silence.
    }

    /**
     * RW-16.16 (spec §7 G6): the failed line's "Try again" — a
     * USER-initiated retry of the current cell's look through the
     * existing request path. Deliberately bypasses the persisted
     * requested-cell guard (that guard gates AUTOMATIC requests
     * only): the server dedupes against active jobs, a failed look
     * never charged the free counter, and a retry that delivers
     * charges exactly as a first delivery does. The outcome hands
     * into the same status path as everything else: accepted →
     * MAKING + poll; refused → re-read the server's truth.
     */
    fun retryPersonalLook() {
        // Make It Me OFF in this build (MakeItMeAvailability): the
        // failed line that hosts this retry never renders, and the
        // retry itself is inert — no generation can be requested.
        if (!com.runwear.app.makeitme.MakeItMeAvailability.ENABLED) return
        val cell = personalCellInView ?: return
        val (combinationId, items) = currentPersonalCombination() ?: return
        if (combinationId != cell) return
        viewModelScope.launch {
            val outcome = runCatching {
                personalHeroRepository.requestGeneration(cell, items)
            }.getOrNull()
            if (personalCellInView != cell) return@launch
            when (outcome) {
                is PersonalHeroService.RequestOutcome.Queued,
                is PersonalHeroService.RequestOutcome.AlreadyQueued -> {
                    _uiState.update {
                        it.copy(personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Making)
                    }
                    pollForPersonalHero(cell)
                }
                is PersonalHeroService.RequestOutcome.AlreadyAvailable -> {
                    val look = runCatching {
                        personalHeroRepository.cellLookFor(cell)
                    }.getOrNull() ?: return@launch
                    if (personalCellInView != cell) return@launch
                    _uiState.update {
                        it.copy(
                            heroImageUrl = look.hit?.imageUrl ?: it.heroImageUrl,
                            heroImageRowId = if (look.hit != null) null else it.heroImageRowId,
                            heroIsPersonal = look.hit != null || it.heroIsPersonal,
                            personalHeroId = look.hit?.heroId ?: it.personalHeroId,
                            personalHeroImageUrl = look.hit?.imageUrl ?: it.personalHeroImageUrl,
                            personalLookStatus = look.status
                        )
                    }
                }
                else -> {
                    val look = runCatching {
                        personalHeroRepository.cellLookFor(cell)
                    }.getOrNull()
                    if (personalCellInView == cell && look != null) {
                        _uiState.update { it.copy(personalLookStatus = look.status) }
                    }
                    personalHeroRepository.refreshFromServer()
                }
            }
        }
    }

    private suspend fun updateUpsellVisibility(cell: String, cellHasPersonalHero: Boolean) {
        val personal = personalHeroRepository.state.value
        val dismissal = personalHeroRepository.upsellDismissal()
        val show = MakeItMeUpsellRules.shouldShow(
            personalizationOn = personal.personalizationOn,
            entitled = personal.entitled,
            freeGenerationsUsed = personal.freeGenerationsUsed,
            cellHasPersonalHero = cellHasPersonalHero,
            cellGenerationPending =
                _uiState.value.personalLookStatus == com.runwear.app.makeitme.PersonalLookStatus.Making,
            combinationId = cell,
            lastDismissedAtMs = dismissal.atMs,
            lastDismissedCell = dismissal.cell,
            nowMs = System.currentTimeMillis()
        )
        if (personalCellInView == cell) {
            _uiState.update { it.copy(showMakeItMeUpsell = show) }
        }
    }

    /** Upsell "Not now" (spec §5.2): record the dismissal, hide the card. */
    fun dismissMakeItMeUpsell() {
        val cell = personalCellInView ?: return
        _uiState.update { it.copy(showMakeItMeUpsell = false) }
        viewModelScope.launch { personalHeroRepository.recordUpsellDismissal(cell) }
    }

    /**
     * Re-run hero resolution after a personal-layer change made outside
     * this ViewModel (opt-in first look requested, photos replaced,
     * photos deleted, subscription activated): the overlay re-evaluates
     * against the current recommendation.
     */
    fun refreshHeroAfterPersonalChange() {
        val weather = _uiState.value.weather ?: return
        val outfit = _uiState.value.outfit ?: return
        resolveHeroImage(weather, outfit)
    }

    /** DataStore key for one cell's persisted hero choice ("date|url"). */
    private fun heroChoiceKey(combinationId: String) =
        androidx.datastore.preferences.core.stringPreferencesKey("hero_choice_$combinationId")

    /**
     * Restore today's refresh pick for this cell, if one was persisted
     * and the image is still in the cell's approved candidate list.
     * Any miss (no pick, stale date, image rotated out) keeps the
     * freshly resolved hero.
     */
    private suspend fun applyPersistedHeroChoice(
        hero: com.runwear.shared.domain.model.HeroImageResult
    ): com.runwear.shared.domain.model.HeroImageResult {
        if (hero.candidates.size <= 1) return hero
        val stored = runCatching {
            preferencesRepository.getString(heroChoiceKey(hero.combinationId))
        }.getOrNull() ?: return hero
        val parts = stored.split("|", limit = 2)
        if (parts.size != 2 || parts[0] != locationNow().toLocalDate().toString()) return hero
        val candidate = hero.candidates.firstOrNull { it.imageUrl == parts[1] } ?: return hero
        if (candidate.imageUrl == hero.imageUrl) return hero
        return hero.copy(
            imageUrl = candidate.imageUrl,
            thumbnailUrl = candidate.thumbnailUrl,
            imageRowId = candidate.imageRowId,
            servedCombinationId = candidate.servedCombinationId
        )
    }

    /**
     * Hero refresh control: cycle to the next approved image for the
     * current cell, WITHOUT flagging the one on screen. The pick is
     * persisted for the rest of the (location-local) day so it survives
     * recomposition, reloads and restarts; tomorrow the cell goes back
     * to the pool's own rotation.
     *
     * RW-16.27: the rotation includes the cell's personal hero (it
     * leads — it is the overlay's winner), so the control also works
     * while a personal photo is on screen: cycling steps from the
     * personal photo through the pool candidates and back. Before
     * RW-16.27 the overlay emptied the candidate list, so in the
     * personal state there was nothing to cycle and the chip never
     * rendered. A pool pick is persisted as before; landing back on
     * the personal photo persists nothing (the overlay re-derives it
     * on the next resolution).
     */
    fun cycleHeroImage() {
        val state = _uiState.value
        val rotation = heroRotation(state.heroCandidates, state.personalHeroImageUrl)
        val next = nextHeroInRotation(rotation, state.heroImageUrl) ?: return
        if (next.isPersonal) {
            _uiState.update {
                it.copy(
                    heroImageUrl = next.imageUrl,
                    heroImageRowId = null,
                    heroIsPersonal = true
                )
            }
            return
        }
        val candidate = state.heroCandidates[next.candidateIndex]
        _uiState.update {
            it.copy(
                heroImageUrl = candidate.imageUrl,
                heroImageRowId = candidate.imageRowId,
                heroCombinationId = candidate.servedCombinationId ?: it.heroCombinationId,
                heroIsPersonal = false
            )
        }
        val combinationId = state.heroRequestedCombinationId ?: return
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setString(
                    heroChoiceKey(combinationId),
                    "${locationNow().toLocalDate()}|${candidate.imageUrl}"
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
        // Personal heroes are not pool rows: the pool flag payload cannot
        // identify them — never file a pool flag against a personal
        // image. The personal remedy is [flagCurrentPersonalHero]
        // (RW-16.27): the backend's flag_hero action.
        if (state.heroIsPersonal) return
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

    /**
     * RW-16.27: "This doesn't look like me" (spec §4) — flag the
     * personalized hero on screen with the backend, the remedy iOS
     * has shipped since RW-14 and Android lacked. When the flag
     * lands, the server has retired the hero and queued its
     * regeneration, and the hero is re-resolved: the pool photo
     * serves in its place while the regeneration is pending. When
     * it doesn't land, nothing on screen changes.
     */
    fun flagCurrentPersonalHero(reason: com.runwear.app.makeitme.PersonalHeroFlagReason) {
        val state = _uiState.value
        if (!state.heroIsPersonal) return
        val heroId = state.personalHeroId ?: return
        val cell = personalCellInView ?: return
        viewModelScope.launch {
            if (personalHeroRepository.flagPersonalHero(cell, heroId, reason)) {
                refreshHeroAfterPersonalChange()
            }
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
    
    /**
     * WS-2 (T-1 ruling): the Settings sheet's unit toggle — the ONLY
     * place the unit changes. It persists the preference; the
     * preferences collector applies it to state and every figure
     * re-renders in the new unit in one frame. Nothing refetches.
     * (The hero numeral's hidden tap-to-toggle is removed — the
     * numeral is a readout.)
     */
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
    
    /** RW-25: persist the appearance preference; the theme re-resolves
     * from uiState in MainActivity and every token read follows. */
    fun setAppearancePreference(preference: AppearancePreference) {
        viewModelScope.launch {
            preferencesRepository.setAppearancePreference(preference)
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

            // WS-4: the search result's place is already simple
            // (derived at the search boundary); carry the value, and
            // persist its display — never a raw geocode string.
            val place = location.simplePlace

            // Persist manual location to DataStore
            preferencesRepository.saveManualLocation(
                location.latitude,
                location.longitude,
                place.display
            )

            // GEN-2 A2 (W-6): the place is PENDING until its weather
            // lands — the name commits with the fetched conditions in
            // fetchWeather's success write, never before them.
            pendingPlace = place

            _uiState.update {
                it.copy(
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

            // WS-4: normalize the picked name to the simple form at
            // this seam (picker rows and recents may carry legacy raw
            // strings); state and persistence carry the simple form.
            val place = com.runwear.shared.domain.model.SimplePlace.fromRawDisplayString(name)

            // Persist manual location to DataStore
            preferencesRepository.saveManualLocation(lat, lon, place.display)

            // GEN-2 A2 (W-6): pending until its weather lands (see
            // selectLocation) — name, weather and frame commit together.
            pendingPlace = place

            _uiState.update {
                it.copy(
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
