package com.runwear.wear.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runwear.shared.data.repository.HeroImageRepository
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageSelector
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
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

data class WearUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val outfit: OutfitRecommendation? = null,
    val weather: WeatherConditions? = null,
    val locationName: String = "Loading...",
    val selectedDateTime: LocalDateTime = LocalDateTime.now(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val comfortPreference: ComfortPreference = ComfortPreference.NEUTRAL,
    val hasLocationPermission: Boolean = false,
    val heroImageUrl: String? = null  // v3.9: AI hero image support
)

@HiltViewModel
class WearViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationProvider: LocationProvider,
    private val getOutfitRecommendation: GetOutfitRecommendationUseCase,
    private val heroImageRepository: HeroImageRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()
    
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    
    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { 
                    it.copy(
                        temperatureUnit = prefs.temperatureUnit,
                        comfortPreference = prefs.comfortPreference
                    )
                }
                if (currentLat != null) fetchWeather()
            }
        }
    }
    
    fun checkLocationPermission() {
        _uiState.update { it.copy(hasLocationPermission = locationProvider.hasLocationPermission()) }
        if (_uiState.value.hasLocationPermission) {
            fetchCurrentLocation()
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            fetchCurrentLocation()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Location permission required") }
        }
    }
    
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            locationProvider.getCurrentLocation().fold(
                onSuccess = { loc ->
                    currentLat = loc.latitude
                    currentLon = loc.longitude
                    
                    weatherRepository.reverseGeocode(loc.latitude, loc.longitude).fold(
                        onSuccess = { name -> _uiState.update { it.copy(locationName = name) } },
                        onFailure = { _uiState.update { it.copy(locationName = "Your Location") } }
                    )
                    
                    fetchWeather()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Location error") }
                }
            )
        }
    }
    
    private fun fetchWeather() {
        val lat = currentLat ?: return
        val lon = currentLon ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val dt = _uiState.value.selectedDateTime
            val unit = _uiState.value.temperatureUnit
            
            val result = if (isNow(dt)) {
                weatherRepository.getCurrentWeather(lat, lon, unit)
            } else {
                weatherRepository.getWeatherForDateTime(lat, lon, dt, unit)
            }
            
            result.fold(
                onSuccess = { weather ->
                    val outfit = getOutfitRecommendation.execute(weather, _uiState.value.comfortPreference)

                    // ZERO-LAG: Set fallback image immediately
                    val fallbackUrl = HeroImageSelector.getImageUrl(weather, outfit, GenderPreference.UNISEX)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weather = weather,
                            outfit = outfit,
                            heroImageUrl = fallbackUrl
                        )
                    }

                    // BACKGROUND: Try to get AI image from Supabase
                    fetchHeroImage(weather, outfit)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Weather error") }
                }
            )
        }
    }

    /**
     * Fetch AI-generated hero image from Supabase.
     * Uses cascading query (v3.9) - fallback already set, this upgrades if available.
     */
    private fun fetchHeroImage(weather: WeatherConditions, outfit: OutfitRecommendation) {
        viewModelScope.launch {
            try {
                val result = heroImageRepository.getHeroImage(
                    weather = weather,
                    outfit = outfit,
                    gender = GenderPreference.UNISEX  // Watch uses unisex for simplicity
                )
                result.imageUrl?.let { url ->
                    _uiState.update { it.copy(heroImageUrl = url) }
                }
            } catch (e: Exception) {
                // Silently fail - fallback already displayed
                android.util.Log.w("WearViewModel", "Hero image fetch failed", e)
            }
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
    
    fun refresh() = fetchWeather()
}
