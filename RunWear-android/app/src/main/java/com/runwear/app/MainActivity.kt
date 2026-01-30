package com.runwear.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.runwear.app.debug.DebugOverlay
import com.runwear.app.ui.screens.HeroMainScreen
import com.runwear.app.ui.screens.LocationPickerSheet
import com.runwear.app.ui.screens.OnboardingScreen
import com.runwear.app.ui.screens.PermissionScreen
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RunWearTheme {
                val uiState by viewModel.uiState.collectAsState()
                val locationSearchState by viewModel.locationSearch.collectAsState()
                var showManualLocationPicker by remember { mutableStateOf(false) }

                // Track current screen for debug overlay
                val currentScreen = remember(uiState) {
                    when {
                        !uiState.hasCompletedOnboarding -> "Onboarding"
                        !uiState.hasLocationPermission && !uiState.isLoading -> "Permission"
                        uiState.isLoading -> "Loading"
                        uiState.error != null -> "Error"
                        else -> "Main"
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        when {
                            // Show onboarding for first-time users
                            !uiState.hasCompletedOnboarding -> {
                                OnboardingScreen(
                                    onComplete = { tempUnit, gender, comfort ->
                                        viewModel.completeOnboarding(
                                            tempUnit = tempUnit,
                                            gender = gender,
                                            comfort = comfort
                                        )
                                    },
                                    onRequestLocationPermission = { requestLocationPermission() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            // Show permission screen if no location permission and not loading
                            !uiState.hasLocationPermission && !uiState.isLoading -> {
                                PermissionScreen(
                                    onRequestPermission = { requestLocationPermission() },
                                    onSetManualLocation = { showManualLocationPicker = true },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            else -> {
                                // Main app screen
                                HeroMainScreen(
                                    uiState = uiState,
                                    locationSearchState = locationSearchState,
                                    onRefresh = viewModel::refresh,
                                    onToggleUnit = viewModel::toggleUnit,
                                    onSetComfortPreference = viewModel::setComfortPreference,
                                    onSetGenderPreference = viewModel::setGenderPreference,
                                    onDateSelected = viewModel::selectDate,
                                    onTimeSelected = viewModel::selectHour,
                                    onShopItem = { /* Handled in HeroMainScreen */ },
                                    onLocationSearch = viewModel::searchLocation,
                                    onLocationSelect = viewModel::selectManualLocation
                                )
                            }
                        }
                    }

                    // Debug Overlay - disabled for screenshots
                    // if (BuildConfig.DEBUG) {
                    //     DebugOverlay(
                    //         uiState = uiState,
                    //         currentScreen = currentScreen
                    //     )
                    // }

                    // Manual Location Picker (shown from PermissionScreen)
                    if (showManualLocationPicker) {
                        LocationPickerSheet(
                            currentLocation = uiState.locationName,
                            onSearch = viewModel::searchLocation,
                            onSelectLocation = { lat, lon, name ->
                                viewModel.selectManualLocation(lat, lon, name)
                                showManualLocationPicker = false
                            },
                            onDismiss = { showManualLocationPicker = false },
                            searchResults = locationSearchState.results
                        )
                    }
                }
            }
        }

        viewModel.checkLocationPermission()
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}
