package com.runwear.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.components.HeroSection
import com.runwear.app.ui.components.OutfitCard
import com.runwear.app.ui.components.TipsSection
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate

/**
 * Hero-image design MainScreen.
 *
 * This is the new design with:
 * - Full-bleed AI hero image (75vh)
 * - Temperature-reactive color tints
 * - Glass morphism controls
 * - Staggered outfit card animations
 * - Bottom sheet pickers for date/time/settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroMainScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (Int) -> Unit,
    onShopItem: (ClothingItem) -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit,
    onGenderChange: (GenderPreference) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Track content visibility for animations
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.outfit) {
        if (uiState.outfit != null) {
            contentVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        when {
            uiState.isLoading -> HeroLoadingScreen()
            uiState.error != null -> HeroErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Hero Section (takes ~75% of screen height)
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
                                genderPreference = uiState.genderPreference,
                                onDateClick = { showDatePicker = true },
                                onTimeClick = { showTimePicker = true },
                                onLocationClick = onLocationClick,
                                onSettingsClick = onSettingsClick,
                                onTempClick = onToggleUnit,
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "${uiState.outfit.allItems.size} items for your run",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RunWearColors.TextSecondary
                                )
                            }
                        }
                    }

                    // Outfit Cards with staggered animation
                    val items = uiState.outfit.allItems
                    items(items.size) { index ->
                        OutfitCard(
                            item = items[index],
                            onClick = { onShopItem(items[index]) },
                            animationDelay = 300 + (index * 50),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Tips Section
                    if (uiState.outfit.tips.isNotEmpty()) {
                        item {
                            TipsSection(
                                tips = uiState.outfit.tips,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    // Bottom spacer
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Date Picker Bottom Sheet
    if (showDatePicker) {
        DatePickerBottomSheet(
            selectedDate = uiState.selectedDateTime.toLocalDate(),
            onDateSelected = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time Picker Bottom Sheet
    if (showTimePicker) {
        TimePickerBottomSheet(
            selectedHour = uiState.selectedDateTime.hour,
            onTimeSelected = { hour ->
                onTimeSelected(hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

// ============================================================================
// DATE PICKER BOTTOM SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerBottomSheet(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Date",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // 7-day grid
            val today = LocalDate.now()
            val days = (0..6).map { today.plusDays(it.toLong()) }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.chunked(4).forEach { rowDays ->
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowDays.forEach { date ->
                            DateChip(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                onClick = { onDateSelected(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if row isn't full
                        repeat(4 - rowDays.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val label = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE"))
    }

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (isSelected) RunWearColors.Primary else RunWearColors.SurfaceElevated,
        border = if (isToday && !isSelected)
            androidx.compose.foundation.BorderStroke(1.dp, RunWearColors.Primary)
        else
            null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) androidx.compose.ui.graphics.Color.White else RunWearColors.TextPrimary
            )
            Text(
                text = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                else
                    RunWearColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// TIME PICKER BOTTOM SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerBottomSheet(
    selectedHour: Int,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // Available hours: 5 AM to 9 PM
    val availableHours = (5..21).toList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableHours.chunked(4).forEach { rowHours ->
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowHours.forEach { hour ->
                            TimeChip(
                                hour = hour,
                                isSelected = hour == selectedHour,
                                onClick = { onTimeSelected(hour) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if row isn't full
                        repeat(4 - rowHours.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TimeChip(
    hour: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = java.time.LocalTime.of(hour, 0)
        .format(java.time.format.DateTimeFormatter.ofPattern("h a"))

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (isSelected) RunWearColors.Primary else RunWearColors.SurfaceElevated
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) androidx.compose.ui.graphics.Color.White else RunWearColors.TextPrimary
            )
        }
    }
}

// ============================================================================
// LOADING & ERROR SCREENS
// ============================================================================

@Composable
private fun HeroLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = RunWearColors.Primary,
                strokeWidth = 4.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Getting your outfit...",
                style = MaterialTheme.typography.bodyLarge,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Checking weather conditions",
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextMuted
            )
        }
    }
}

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
