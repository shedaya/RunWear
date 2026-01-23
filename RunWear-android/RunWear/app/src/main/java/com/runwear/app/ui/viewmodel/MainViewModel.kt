package com.runwear.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runwear.shared.data.repository.LocationResult
import com.runwear.shared.data.repository.PreferencesRepository
import com.runwear.shared.data.repository.WeatherRepository
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
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
    val hasLocationPermission: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showSettings: Boolean = false
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
                    
                    // Get friendly location name
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
        currentLat = location.latitude
        currentLon = location.longitude
        _uiState.update { it.copy(locationName = location.displayName) }
        _locationSearch.update { LocationSearchState() }
        fetchWeather()
    }
}
