package com.runwear.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory
import com.runwear.app.feedback.FeedbackCategory
import com.runwear.app.feedback.StartupTrace
import com.runwear.app.feedback.StartupWatchdog
import com.runwear.app.ui.screens.FeedbackScreen
import com.runwear.app.ui.screens.HeroMainScreen
import com.runwear.app.ui.screens.LocationPickerSheet
import com.runwear.app.ui.screens.OnboardingScreen
import com.runwear.app.ui.screens.PermissionScreen
import com.runwear.app.ui.screens.RatingMomentDialog
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

        // Safe-boot diagnostic (1.1.3): first line after super.onCreate,
        // before setContent — entry into the Activity half of startup.
        // The startup watchdog arms here: if the process is still alive
        // 8 s from now but no first frame has drawn, it captures the
        // main thread's stack (hang evidence; its absence = death).
        StartupTrace.mark(this, "setcontent-entry")
        StartupWatchdog.arm(this)

        // Safe-boot diagnostic (1.1.2): the Activity phase was reached.
        StartupTrace.mark(this, "mainactivity-created")

        enableEdgeToEdge()

        // Safe-boot diagnostic (1.1.4): edge-to-edge setup returned.
        // The 1.1.3 trace ended at setcontent-entry with setcontent-
        // called never reached, leaving enableEdgeToEdge() and the
        // setContent{} call itself as the two suspects; this mark
        // splits them.
        StartupTrace.mark(this, "edge-to-edge-done")

        setContent {
            // Safe-boot diagnostic (1.1.2): composition started, and —
            // once the scaffold's content composes — the first screen is up.
            val traceContext = LocalContext.current.applicationContext
            LaunchedEffect(Unit) {
                StartupTrace.mark(traceContext, "composition-start")
            }
            RunWearTheme {
                val uiState by viewModel.uiState.collectAsState()
                val locationSearchState by viewModel.locationSearch.collectAsState()
                val analyticsOptIn by viewModel.analyticsOptIn.collectAsState()
                var showManualLocationPicker by remember { mutableStateOf(false) }

                // Feedback system (spec §1): the screen opens from Settings
                // (no pre-selected category) or from the outfit list
                // (recommendation pre-selected).
                var showFeedback by remember { mutableStateOf(false) }
                var feedbackPreselect by remember { mutableStateOf<FeedbackCategory?>(null) }
                // The intake's entryPoint: "contextual" when the screen
                // opens from the outfit (category pre-selected),
                // "settings" from the neutral entry, null (field
                // omitted) when opened from the rating moment.
                var feedbackEntryPoint by remember { mutableStateOf<String?>(null) }

                // Rating moment (spec Addendum 2): shown only when the
                // ViewModel's triggers and suppression rules say so.
                var showRatingMoment by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    viewModel.ratingMomentDue.collect { showRatingMoment = true }
                }
                LaunchedEffect(showRatingMoment) {
                    if (showRatingMoment) viewModel.onRatingMomentShown()
                }

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
                        // Safe-boot diagnostic (1.1.3): the navigation
                        // host was entered. This app has no androidx
                        // NavHost composable — the Scaffold content lambda
                        // is where the root destination `when` below
                        // dispatches, so its entry is the host entry.
                        // remember{} so the mark fires once, when this
                        // node's composition first executes.
                        remember {
                            StartupTrace.mark(traceContext, "navhost-entered")
                            Unit
                        }
                        // Safe-boot diagnostic (1.1.2): home content composed.
                        LaunchedEffect(Unit) {
                            StartupTrace.mark(traceContext, "home-content-ready")
                        }
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
                                    onResetToNow = viewModel::resetToNow,
                                    onLocationSearch = viewModel::searchLocation,
                                    onLocationSelect = viewModel::selectManualLocation,
                                    isUsingGPS = uiState.locationSource == "gps",
                                    onUseCurrentLocation = {
                                        // Request GPS permission or retry if already granted
                                        if (viewModel.hasLocationPermission()) {
                                            viewModel.retryGPS()
                                        } else {
                                            requestLocationPermission()
                                        }
                                    },
                                    analyticsOptIn = analyticsOptIn,
                                    onAnalyticsOptInChange = viewModel::setAnalyticsOptIn,
                                    onOpenFeedback = { category ->
                                        feedbackPreselect = category
                                        feedbackEntryPoint =
                                            if (category != null) "contextual" else "settings"
                                        showFeedback = true
                                    },
                                    onItemTapped = viewModel::onAffiliateItemTapped,
                                    onFlagHero = viewModel::flagCurrentHero
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

                    // Feedback screen (full-screen overlay)
                    if (showFeedback) {
                        val bundle = remember(showFeedback) { viewModel.buildFeedbackBundle() }
                        FeedbackScreen(
                            bundle = bundle,
                            preselectedCategory = feedbackPreselect,
                            onSend = { category, message, email, onResult ->
                                viewModel.submitFeedback(
                                    category, message, email, feedbackEntryPoint, onResult
                                )
                            },
                            onDismiss = { showFeedback = false }
                        )
                    }

                    // Rating moment: two equal doors, no screening question.
                    if (showRatingMoment) {
                        RatingMomentDialog(
                            onSendFeedback = {
                                showRatingMoment = false
                                feedbackPreselect = null
                                feedbackEntryPoint = null
                                showFeedback = true
                            },
                            onRate = {
                                showRatingMoment = false
                                launchInAppReview()
                            },
                            onDismiss = { showRatingMoment = false }
                        )
                    }

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
                            searchResults = locationSearchState.results,
                            isUsingGPS = uiState.locationSource == "gps",
                            onUseCurrentLocation = {
                                showManualLocationPicker = false
                                requestLocationPermission()
                            }
                        )
                    }
                }
            }
        }

        // Safe-boot diagnostic (1.1.3): setContent has returned — the
        // synchronous half of onCreate (edge-to-edge setup + ComposeView
        // creation) completed. Anything between this mark and
        // "composition-start" is the first composition pass itself.
        StartupTrace.mark(this, "setcontent-called")

        // Safe-boot diagnostic (1.1.3): the first frame. A one-shot
        // pre-draw listener on the content view marks the moment the
        // first frame is about to draw, and disarms the watchdog.
        try {
            val contentView = findViewById<View>(android.R.id.content)
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    try {
                        StartupTrace.mark(this@MainActivity, "first-frame")
                        StartupWatchdog.noteFirstFrame()
                    } catch (_: Throwable) {
                        // Best effort only.
                    }
                    if (contentView.viewTreeObserver.isAlive) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return true
                }
            }
            contentView.viewTreeObserver.addOnPreDrawListener(listener)
        } catch (_: Throwable) {
            // Best effort only.
        }

        viewModel.checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Re-validate the saved GPS location against a fresh fix so the app
        // never shows another city's weather indefinitely (F1). Manual
        // locations are never overwritten; the check is debounced and
        // distance-gated inside the ViewModel.
        viewModel.onForegrounded()
    }

    /**
     * The native Play review flow, from the rating moment's "Rate RunWear"
     * door. The dialog only exists on Play-installed builds — on sideloaded
     * builds (our debug APKs) this checks the installer first and quietly
     * does nothing, so the flow can never error in the user's face.
     */
    private fun launchInAppReview() {
        if (!isPlayInstalled()) return
        try {
            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Fire and forget: the API never reports whether the
                    // dialog actually displayed (platform quota is silent).
                    reviewManager.launchReviewFlow(this, task.result)
                }
            }
        } catch (e: Exception) {
            // A rating ask must never become a crash. Stay silent.
        }
    }

    private fun isPlayInstalled(): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer == "com.android.vending"
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
