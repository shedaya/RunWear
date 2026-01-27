package com.runwear.wear.presentation

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.runwear.wear.presentation.screens.HeroWearMainScreen
import com.runwear.wear.presentation.theme.RunWearWatchTheme
import com.runwear.wear.presentation.viewmodel.WearViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RunWearWatchTheme {
                val uiState by viewModel.uiState.collectAsState()

                // New hero-image paged design
                HeroWearMainScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onToggleUnit = viewModel::toggleUnit,
                    onNextDay = viewModel::goToNextDay,
                    onPreviousDay = viewModel::goToPreviousDay,
                    onResetToNow = viewModel::resetToNow,
                    onRequestPermission = { requestLocationPermission() }
                )
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
