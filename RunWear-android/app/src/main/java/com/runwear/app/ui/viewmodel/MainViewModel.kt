package com.runwear.app.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runwear.shared.data.repository.AffiliateRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val outfit: OutfitRecommendation? = null,
    val weather: WeatherConditions? = null,
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val locationName: String = "Getting location...",
    val selectedDateTime: LocalDateTime = LocalDateTime.now(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val comfortPreference: ComfortPreference = ComfortPreference.NEUTRAL,
    val genderPreference: GenderPreference = GenderPreference.UNISEX,
    val hasLocationPermission: Boolean = false,
    val hasCompletedOnboarding: Boolean = true, // Assume true, check in init
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showSettings: Boolean = false,
    val showShopSheet: Boolean = false,
    val affiliatePartner: AffiliatePartner = AffiliatePartner.AMAZON,
    // Location fallback state
    val showLocationSetup: Boolean = false,
    val locationSource: String = "gps", // "gps" or "manual"
    val locationErrorReason: LocationErrorReason? = null
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
    private val locationProvider: LocationProvider,
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _locationSearch = MutableStateFlow(LocationSearchState())
    val locationSearch: StateFlow<LocationSearchState> = _locationSearch.asStateFlow()

    private var currentLat: Double? = null
    private var currentLon: Double? = null

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
                // Use saved manual location - skip GPS entirely
                currentLat = savedLocation.latitude
                currentLon = savedLocation.longitude
                _uiState.update {
                    it.copy(
                        locationName = savedLocation.name,
                        locationSource = "manual",
                        hasLocationPermission = true, // Bypass permission check
                        isLoading = true
                    )
                }
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
            
            val dt = _uiState.value.selectedDateTime
            val unit = _uiState.value.temperatureUnit
            
            // Fetch weather for selected time
            val weatherResult = if (isNow(dt)) {
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
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weather = weather,
                            outfit = outfit,
                            hourlyForecast = forecast
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Weather error") }
                }
            )
        }
    }
    
    private fun isNow(dt: LocalDateTime): Boolean {
        val now = LocalDateTime.now()
        return dt.toLocalDate() == now.toLocalDate() && dt.hour == now.hour
    }
    
    fun setDateTime(dt: LocalDateTime) {
        _uiState.update { it.copy(selectedDateTime = dt) }
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
        if (newDateTime.toLocalDate() <= LocalDate.now().plusDays(6)) {
            setDateTime(newDateTime)
        }
    }
    
    fun goToPreviousDay() {
        val prev = _uiState.value.selectedDateTime.minusDays(1)
        val now = LocalDateTime.now()
        setDateTime(if (prev.isAfter(now.minusHours(1))) prev else now)
    }
    
    fun resetToNow() {
        setDateTime(LocalDateTime.now())
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
        if (query.length < 2) {
            _locationSearch.update { it.copy(results = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            _locationSearch.update { it.copy(isSearching = true) }
            weatherRepository.searchLocation(query).fold(
                onSuccess = { results -> _locationSearch.update { it.copy(isSearching = false, results = results) } },
                onFailure = { _locationSearch.update { it.copy(isSearching = false) } }
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
            // MainActivity will need to request permission if not granted
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
        }
    }

    // Shop Sheet
    fun showShopSheet() { _uiState.update { it.copy(showShopSheet = true) } }
    fun hideShopSheet() { _uiState.update { it.copy(showShopSheet = false) } }

    fun getAffiliateLinkForItem(item: ClothingItem): String {
        val partner = _uiState.value.affiliatePartner
        val gender = _uiState.value.genderPreference
        return affiliateRepository.buildAffiliateLink(partner, item, gender)
    }
}
