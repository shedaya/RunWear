package com.runwear.app.ui.viewmodel

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val heroImageUrl: String? = null
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
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _locationSearch = MutableStateFlow(LocationSearchState())
    val locationSearch: StateFlow<LocationSearchState> = _locationSearch.asStateFlow()

    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var searchJob: Job? = null

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
     * (Re)resolve the selected location's timezone and re-anchor the selection
     * in that frame. In NOW mode the selected moment becomes the location's
     * now; an explicit selection keeps its wall-clock numbers (they are
     * location-local by definition) and is reinterpreted in the new frame.
     */
    private suspend fun resolveLocationZone(lat: Double, lon: Double) {
        weatherRepository.getLocationTimeZone(lat, lon).onSuccess { zone ->
            locationZone = zone
        }
        val now = locationNow()
        _uiState.update {
            it.copy(
                locationNow = now,
                selectedDateTime = if (it.isNowSelected) now else it.selectedDateTime
            )
        }
    }

    // Stale-location guard (F1): a saved GPS fix must not be reused forever.
    // Re-poll GPS on foreground; only adopt the new fix if the user moved
    // significantly, so we never cause a weather-refetch storm.
    private var lastGpsRefreshMs: Long = 0L

    init {
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
                resolveLocationZone(savedLocation.latitude, savedLocation.longitude)
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
                        resolveLocationZone(newLat, newLon)
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
    }
    
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showLocationSetup = false) }

            when (val result = locationProvider.getCurrentLocationWithTimeout()) {
                is LocationFetchResult.Success -> {
                    currentLat = result.location.latitude
                    currentLon = result.location.longitude

                    // Get friendly location name and save for future use
                    weatherRepository.reverseGeocode(result.location.latitude, result.location.longitude).fold(
                        onSuccess = { name ->
                            _uiState.update { it.copy(locationName = name, locationSource = "gps") }
                            // Save GPS location for next app launch
                            preferencesRepository.saveGPSLocation(result.location.latitude, result.location.longitude, name)
                        },
                        onFailure = {
                            _uiState.update { it.copy(locationName = "Your Location", locationSource = "gps") }
                            preferencesRepository.saveGPSLocation(result.location.latitude, result.location.longitude, "Your Location")
                        }
                    )

                    resolveLocationZone(result.location.latitude, result.location.longitude)
                    fetchWeather()
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
                        resolveLocationZone(savedLocation.latitude, savedLocation.longitude)
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
    
    private fun fetchWeather() {
        val lat = currentLat ?: return
        val lon = currentLon ?: return
        
        viewModelScope.launch {
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

            // NOW uses the API's current-conditions block (location's real now);
            // an explicit selection is a location-local wall time matched
            // against the location-local hourly forecast.
            val weatherResult = if (_uiState.value.isNowSelected) {
                weatherRepository.getCurrentWeather(lat, lon, unit)
            } else {
                weatherRepository.getWeatherForDateTime(lat, lon, dt, unit)
            }
            
            // Fetch hourly forecast
            val forecastResult = weatherRepository.getHourlyForecast(lat, lon, unit)
            
            weatherResult.fold(
                onSuccess = { weather ->
                    val outfit = getOutfitRecommendation.execute(weather, _uiState.value.comfortPreference)
                    val forecast = forecastResult.getOrDefault(emptyList())

                    // PWA v2.9: Set default hero image IMMEDIATELY based on temp bracket
                    val defaultImageUrl = com.runwear.shared.domain.model.HeroImageSelector.getImageUrl(
                        weather, outfit, _uiState.value.genderPreference
                    )

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weather = weather,
                            outfit = outfit,
                            hourlyForecast = forecast,
                            heroImageUrl = defaultImageUrl // Show default immediately
                        )
                    }

                    // Then fetch AI hero image in background (upgrades if found)
                    fetchHeroImage(weather, outfit)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Weather error") }
                }
            )
        }
    }
    
    /**
     * Fetch hero image with zero-lag architecture:
     * 1. INSTANTLY set weather-aware fallback image (no waiting)
     * 2. BACKGROUND: Try to upgrade to AI image from Supabase
     */
    private fun fetchHeroImage(weather: WeatherConditions, outfit: OutfitRecommendation) {
        val gender = _uiState.value.genderPreference

        // INSTANT: Set weather-aware fallback immediately (no network wait)
        val fallbackUrl = com.runwear.shared.domain.model.HeroImageSelector.getImageUrl(
            weather, outfit, gender
        )
        _uiState.update { it.copy(heroImageUrl = fallbackUrl) }

        // BACKGROUND: Try to get better AI image from Supabase (don't block UI)
        viewModelScope.launch {
            try {
                val result = heroImageRepository.getHeroImage(
                    weather = weather,
                    outfit = outfit,
                    gender = gender
                )
                // Only update if we got a valid URL (Supabase AI image found)
                result.imageUrl?.let { url ->
                    _uiState.update { it.copy(heroImageUrl = url) }
                }
            } catch (e: Exception) {
                // Silently ignore - fallback is already showing
            }
        }
    }
    
    /** Any explicit date/time pick exits NOW into an explicit location-local time. */
    fun setDateTime(dt: LocalDateTime) {
        _uiState.update { it.copy(selectedDateTime = dt, isNowSelected = false) }
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
            // Hero image depends on outfit hash - must reload
            fetchHeroImage(weather, outfit)
        }
    }
    
    fun showDatePicker() { _uiState.update { it.copy(showDatePicker = true) } }
    fun hideDatePicker() { _uiState.update { it.copy(showDatePicker = false) } }
    fun showTimePicker() { _uiState.update { it.copy(showTimePicker = true) } }
    fun hideTimePicker() { _uiState.update { it.copy(showTimePicker = false) } }
    fun showSettings() { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }
    
    fun refresh() = fetchWeather()
    
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
            resolveLocationZone(location.latitude, location.longitude)
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
            resolveLocationZone(lat, lon)
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
            fetchHeroImage(weather, outfit)
        }
    }

    fun getAffiliateLinkForItem(item: ClothingItem): String {
        val partner = _uiState.value.affiliatePartner
        val gender = _uiState.value.genderPreference
        return affiliateRepository.buildAffiliateLink(partner, item, gender)
    }
}
