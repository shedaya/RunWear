package com.runwear.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.components.HeroSection
import com.runwear.app.ui.components.HeroShimmerPlaceholder
import com.runwear.app.ui.components.OutfitCard
import com.runwear.app.ui.components.OutfitListShimmer
import com.runwear.app.ui.components.TipsSection
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate

/**
 * Hero-image design MainScreen - PWA v2.9 aligned.
 *
 * Features:
 * - Full-bleed AI hero image (75vh)
 * - Temperature-reactive color tints
 * - Glass morphism controls (Location, Share, Settings)
 * - Combined Date/Time pill (frosted glass)
 * - Tappable weather pills with detail popups
 * - Shop button opens modal with all items → Amazon
 * - Staggered outfit card animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroMainScreen(
    uiState: MainUiState,
    locationSearchState: LocationSearchState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onSetComfortPreference: (com.runwear.shared.domain.model.ComfortPreference) -> Unit,
    onSetGenderPreference: (com.runwear.shared.domain.model.GenderPreference) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (Int) -> Unit,
    onResetToNow: () -> Unit,
    onShopItem: (ClothingItem) -> Unit,
    onLocationSearch: (String) -> Unit,
    onLocationSelect: (Double, Double, String) -> Unit,
    onUseCurrentLocation: () -> Unit = {},
    isUsingGPS: Boolean = false
) {
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showShopAll by remember { mutableStateOf(false) }
    var selectedShopItem by remember { mutableStateOf<ClothingItem?>(null) }
    var weatherDetailType by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Track content visibility for animations
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.outfit) {
        if (uiState.outfit != null) {
            contentVisible = true
        }
    }

    // Share function
    fun shareOutfit() {
        val weather = uiState.weather ?: return
        val outfit = uiState.outfit ?: return
        val unit = if (weather.isCelsius) "°C" else "°F"

        val itemsList = outfit.allItems.joinToString("\n") { "• ${it.displayName}" }
        val shareText = """
🏃 RunWear Outfit Recommendation

📍 ${uiState.locationName}
🌡️ Feels like ${weather.effectiveTemperature.toInt()}$unit

${outfit.allItems.size} Items for Your Run:
$itemsList

${if (outfit.tips.isNotEmpty()) "💡 Tip: ${outfit.tips.first()}" else ""}

Get your personalized running outfit at runwear.app
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share your outfit"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        when {
            uiState.isLoading -> HeroShimmerPlaceholder()
            uiState.error != null -> HeroErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                val pullToRefreshState = rememberPullToRefreshState()

                val scrollOffset = remember {
                    derivedStateOf {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 60.dp)
                    ) {
                        // Hero Section
                        item {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = fadeIn(tween(400, easing = FastOutSlowInEasing))
                            ) {
                                HeroSection(
                                    weather = uiState.weather,
                                    outfit = uiState.outfit,
                                    locationName = uiState.locationName,
                                    selectedDateTime = uiState.selectedDateTime,
                                    heroImageUrl = uiState.heroImageUrl,
                                    scrollOffset = scrollOffset.value,
                                    onDateClick = { showDatePicker = true },
                                    onTimeClick = { showTimePicker = true },
                                    onNowClick = onResetToNow,
                                    onLocationClick = { showLocationPicker = true },
                                    onSettingsClick = { showSettings = true },
                                    onShareClick = { shareOutfit() },
                                    onTempClick = onToggleUnit,
                                    onWeatherPillClick = { type -> weatherDetailType = type },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Outfit Section Header
                        item {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = fadeIn(tween(300, delayMillis = 200))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 24.dp, bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // "X Items for Your Run"
                                    Text(
                                        text = "${uiState.outfit.allItems.size} Items for Your Run",
                                        fontFamily = BebasNeueFontFamily,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = RunWearColors.TextPrimary,
                                        letterSpacing = 0.56.sp
                                    )

                                    // Shop button - opens shop modal
                                    Box(
                                        modifier = Modifier
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(50),
                                                ambientColor = RunWearColors.PrimaryGlow,
                                                spotColor = RunWearColors.PrimaryGlow
                                            )
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        RunWearColors.Primary,
                                                        RunWearColors.PrimaryLight
                                                    ),
                                                    start = Offset(0f, 0f),
                                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                                ),
                                                shape = RoundedCornerShape(50)
                                            )
                                            .clickable { showShopAll = true }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ShoppingBag,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = Color.White
                                            )
                                            Text(
                                                text = "Shop",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Outfit Cards
                        val items = uiState.outfit.allItems
                        items(items.size) { index ->
                            OutfitCard(
                                item = items[index],
                                onClick = { selectedShopItem = items[index] },
                                animationDelay = index * 50,
                                modifier = Modifier.padding(
                                    horizontal = 20.dp,
                                    vertical = 5.dp
                                )
                            )
                        }

                        // PRO TIP Section
                        if (uiState.outfit.tips.isNotEmpty()) {
                            item {
                                TipsSection(
                                    tips = uiState.outfit.tips,
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 20.dp)
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }

                        // Version footer
                        item {
                            Text(
                                text = "v2.9",
                                style = MaterialTheme.typography.bodySmall,
                                color = RunWearColors.TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Date Picker
    if (showDatePicker) {
        DatePickerSheet(
            selectedDate = uiState.selectedDateTime.toLocalDate(),
            onSelectDate = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time Picker
    if (showTimePicker) {
        TimePickerSheet(
            selectedHour = uiState.selectedDateTime.hour,
            onSelectTime = { hour ->
                onTimeSelected(hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // Settings
    if (showSettings) {
        SettingsSheet(
            temperatureUnit = uiState.temperatureUnit,
            comfortPreference = uiState.comfortPreference,
            genderPreference = uiState.genderPreference,
            onToggleUnit = onToggleUnit,
            onSetComfortPreference = onSetComfortPreference,
            onSetGenderPreference = onSetGenderPreference,
            onDismiss = { showSettings = false }
        )
    }

    // Location Picker
    if (showLocationPicker) {
        LocationPickerSheet(
            currentLocation = uiState.locationName,
            onSearch = onLocationSearch,
            onSelectLocation = { lat, lon, name ->
                onLocationSelect(lat, lon, name)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
            searchResults = locationSearchState.results,
            isSearching = locationSearchState.isSearching,
            isUsingGPS = isUsingGPS,
            onUseCurrentLocation = {
                onUseCurrentLocation()
                showLocationPicker = false
            }
        )
    }

    // Shop All Modal (shows all outfit items)
    if (showShopAll && uiState.outfit != null) {
        ShopAllSheet(
            items = uiState.outfit.allItems,
            genderPreference = uiState.genderPreference,
            onDismiss = { showShopAll = false }
        )
    }

    // Individual Item Shop Sheet
    selectedShopItem?.let { item ->
        ShopSheet(
            item = item,
            genderPreference = uiState.genderPreference,
            onDismiss = { selectedShopItem = null }
        )
    }

    // Weather Detail Popup
    weatherDetailType?.let { type ->
        uiState.weather?.let { weather ->
            WeatherDetailSheet(
                type = type,
                weather = weather,
                onDismiss = { weatherDetailType = null }
            )
        }
    }
}

// ============================================================================
// ERROR SCREEN
// ============================================================================

@Composable
private fun HeroErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = RunWearColors.TempHot,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Oops!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyLarge,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
