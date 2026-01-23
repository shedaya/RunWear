package com.runwear.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.runwear.app.ui.screens.MainScreen
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
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!uiState.hasLocationPermission && !uiState.isLoading) {
                        PermissionScreen(
                            onRequestPermission = { requestLocationPermission() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        MainScreen(
                            uiState = uiState,
                            onRefresh = viewModel::refresh,
                            onToggleUnit = viewModel::toggleUnit,
                            onNextDay = viewModel::goToNextDay,
                            onPreviousDay = viewModel::goToPreviousDay,
                            onResetToNow = viewModel::resetToNow,
                            onSelectHour = viewModel::selectHour,
                            onSelectDate = viewModel::selectDate,
                            onShowDatePicker = viewModel::showDatePicker,
                            onHideDatePicker = viewModel::hideDatePicker,
                            onShowTimePicker = viewModel::showTimePicker,
                            onHideTimePicker = viewModel::hideTimePicker,
                            onShowSettings = viewModel::showSettings,
                            onHideSettings = viewModel::hideSettings,
                            onSetComfortPreference = viewModel::setComfortPreference,
                            modifier = Modifier.padding(innerPadding)
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
