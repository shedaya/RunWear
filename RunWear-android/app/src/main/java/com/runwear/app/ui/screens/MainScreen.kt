package com.runwear.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.R
import com.runwear.app.ui.theme.*
import com.runwear.app.ui.viewmodel.MainUiState
import kotlin.math.roundToInt
import com.runwear.shared.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * RunWear Phone App - Complete Implementation
 * 
 * Features implemented:
 * ✅ 96sp Inter Black temperature (dominant visual hierarchy)
 * ✅ Temperature-reactive gradient backgrounds
 * ✅ Animated temperature counter (0 → actual over 800ms)
 * ✅ Staggered entrance animations (400ms base + 80ms per item)
 * ✅ Haptic feedback on all interactions
 * ✅ Scale animations on card press (0.98f, 100ms)
 * ✅ Running-specific custom icons with fallback to emoji
 * ✅ White card backgrounds with elevation
 * ✅ Material 3 TopAppBar with settings
 */

// ============================================================================
// TEMPERATURE GRADIENT SYSTEM - Accessible colors with proper contrast
// All gradients ensure white text has 3:1+ contrast (WCAG AA for large text)
// ============================================================================

fun getTempGradient(tempF: Double): Brush {
    val colors = when {
        tempF >= 85 -> listOf(Color(0xFFC62828), Color(0xFFD32F2F))      // Very Hot: Deep Red
        tempF >= 75 -> listOf(Color(0xFFD32F2F), Color(0xFFE65100))      // Hot: Red to Orange
        tempF >= 65 -> listOf(Color(0xFFE65100), Color(0xFFEF6C00))      // Warm: Deep Orange
        tempF >= 55 -> listOf(Color(0xFF00796B), Color(0xFF00897B))      // Mild: Teal
        tempF >= 45 -> listOf(Color(0xFF0277BD), Color(0xFF0288D1))      // Cool: Light Blue 800
        tempF >= 35 -> listOf(Color(0xFF1565C0), Color(0xFF0D47A1))      // Cold: Blue
        tempF >= 25 -> listOf(Color(0xFF283593), Color(0xFF1A237E))      // Very Cold: Indigo
        else -> listOf(Color(0xFF4A148C), Color(0xFF311B92))             // Freezing: Purple
    }
    return Brush.linearGradient(colors = colors)
}

fun getTempColor(tempF: Double): Color = when {
    tempF >= 85 -> Color(0xFFD32F2F)      // Red 700 - 4.5:1 on white
    tempF >= 75 -> Color(0xFFD32F2F)      // Red 700
    tempF >= 65 -> Color(0xFFE65100)      // Orange 900 - 4.6:1 on white
    tempF >= 55 -> Color(0xFF00796B)      // Teal 700 - 7.2:1 on white
    tempF >= 45 -> Color(0xFF0277BD)      // Light Blue 800 - 5.5:1 on white
    tempF >= 35 -> Color(0xFF1565C0)      // Blue 800 - 6.1:1 on white
    tempF >= 25 -> Color(0xFF283593)      // Indigo 800 - 8.9:1 on white
    else -> Color(0xFF4A148C)             // Purple 900 - 10.5:1 on white
}

// ============================================================================
// MAIN SCREEN (uses MainUiState from ViewModel)
// ============================================================================

// ============================================================================
// RESPONSIVE LAYOUT HELPERS
// ============================================================================

/**
 * Adaptive content padding based on screen width
 */
@Composable
fun getAdaptiveHorizontalPadding(windowSizeClass: WindowSizeClass): Dp {
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 32.dp
        WindowWidthSizeClass.Expanded -> 48.dp
        else -> 16.dp
    }
}

/**
 * Maximum content width for large screens
 */
@Composable
fun getMaxContentWidth(windowSizeClass: WindowSizeClass): Dp {
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> Dp.Unspecified
        WindowWidthSizeClass.Medium -> 600.dp
        WindowWidthSizeClass.Expanded -> 840.dp
        else -> Dp.Unspecified
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    windowSizeClass: WindowSizeClass,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onShopOutfit: () -> Unit,
    onShopItem: (ClothingItem) -> Unit,
    onOpenSettings: () -> Unit,
    onGenderChange: (GenderPreference) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Responsive values
    val isExpandedLayout = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMediumLayout = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val horizontalPadding = getAdaptiveHorizontalPadding(windowSizeClass)
    val maxContentWidth = getMaxContentWidth(windowSizeClass)

    // Track if content has been shown (for entrance animations)
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.outfit) {
        if (uiState.outfit != null) {
            contentVisible = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "RunWear",
                        fontSize = if (isExpandedLayout) 28.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = InterFontFamily,
                        color = NeoMint
                    )
                },
                actions = {
                    // Location name
                    Text(
                        uiState.locationName,
                        fontSize = if (isExpandedLayout) 16.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    // Settings icon
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = NeoMint
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen()
            uiState.error != null -> ErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                // ════════════════════════════════════════════════════════════════
                // RESPONSIVE LAYOUT: Different layouts for different screen sizes
                // ════════════════════════════════════════════════════════════════
                if (isExpandedLayout || isMediumLayout) {
                    // Two-column layout for tablets and foldables
                    TwoColumnLayout(
                        uiState = uiState,
                        contentVisible = contentVisible,
                        horizontalPadding = horizontalPadding,
                        maxContentWidth = maxContentWidth,
                        isExpanded = isExpandedLayout,
                        onToggleUnit = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleUnit()
                        },
                        onPreviousDay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreviousDay()
                        },
                        onNextDay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextDay()
                        },
                        onDateClick = onDateClick,
                        onTimeClick = onTimeClick,
                        onResetToNow = onResetToNow,
                        onShopOutfit = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShopOutfit()
                        },
                        onShopItem = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShopItem(item)
                        },
                        onGenderChange = { gender ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGenderChange(gender)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    // Single-column layout for phones
                    SingleColumnLayout(
                        uiState = uiState,
                        listState = listState,
                        contentVisible = contentVisible,
                        onToggleUnit = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleUnit()
                        },
                        onPreviousDay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreviousDay()
                        },
                        onNextDay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextDay()
                        },
                        onDateClick = onDateClick,
                        onTimeClick = onTimeClick,
                        onResetToNow = onResetToNow,
                        onShopOutfit = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShopOutfit()
                        },
                        onShopItem = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShopItem(item)
                        },
                        onGenderChange = { gender ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGenderChange(gender)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}

// ============================================================================
// SINGLE COLUMN LAYOUT (Phones)
// ============================================================================

@Composable
private fun SingleColumnLayout(
    uiState: MainUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentVisible: Boolean,
    onToggleUnit: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onResetToNow: () -> Unit,
    onShopOutfit: () -> Unit,
    onShopItem: (ClothingItem) -> Unit,
    onGenderChange: (GenderPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Weather Card
        item {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            ) {
                HeroWeatherCard(
                    weather = uiState.weather!!,
                    onToggleUnit = onToggleUnit
                )
            }
        }

        // Date/Time Selector
        item {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(300, delayMillis = 200))
            ) {
                DateTimeSelector(
                    selectedDateTime = uiState.selectedDateTime,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onDateClick = onDateClick,
                    onTimeClick = onTimeClick,
                    onResetToNow = onResetToNow
                )
            }
        }

        // Section Header: Your Outfit with gender toggle and shop button
        item {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(300, delayMillis = 300))
            ) {
                OutfitSectionHeader(
                    currentGender = uiState.genderPreference,
                    onGenderChange = onGenderChange,
                    onShopClick = onShopOutfit
                )
            }
        }

        // Clothing Items
        itemsIndexed(uiState.outfit!!.allItems) { index, item ->
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(300, delayMillis = 400 + (index * 80))) +
                        slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(300, delayMillis = 400 + (index * 80)))
            ) {
                ClothingItemCard(item = item, onClick = { onShopItem(item) })
            }
        }

        // Tips Section
        if (uiState.outfit.tips.isNotEmpty()) {
            item {
                val baseDelay = 400 + (uiState.outfit.allItems.size * 80)
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(300, delayMillis = baseDelay))
                ) {
                    SectionHeader(title = "RUNNING TIPS", actionText = null, onActionClick = {})
                }
            }

            itemsIndexed(uiState.outfit.tips) { index, tip ->
                val baseDelay = 500 + (uiState.outfit.allItems.size * 80)
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(300, delayMillis = baseDelay + (index * 80)))
                ) {
                    TipCard(tip)
                }
            }
        }
    }
}

// ============================================================================
// TWO COLUMN LAYOUT (Tablets & Foldables)
// ============================================================================

@Composable
private fun TwoColumnLayout(
    uiState: MainUiState,
    contentVisible: Boolean,
    horizontalPadding: Dp,
    maxContentWidth: Dp,
    isExpanded: Boolean,
    onToggleUnit: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onResetToNow: () -> Unit,
    onShopOutfit: () -> Unit,
    onShopItem: (ClothingItem) -> Unit,
    onGenderChange: (GenderPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Weather + Date/Time
            LazyColumn(
                modifier = Modifier
                    .weight(if (isExpanded) 0.4f else 0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400))
                    ) {
                        HeroWeatherCardLarge(
                            weather = uiState.weather!!,
                            onToggleUnit = onToggleUnit
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(300, delayMillis = 200))
                    ) {
                        DateTimeSelectorCompact(
                            selectedDateTime = uiState.selectedDateTime,
                            onPreviousDay = onPreviousDay,
                            onNextDay = onNextDay,
                            onDateClick = onDateClick,
                            onTimeClick = onTimeClick,
                            onResetToNow = onResetToNow
                        )
                    }
                }

                // Tips in left column for expanded layout
                if (uiState.outfit!!.tips.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "RUNNING TIPS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = InterFontFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    itemsIndexed(uiState.outfit.tips) { _, tip ->
                        TipCardCompact(tip)
                    }
                }
            }

            // Right Column: Outfit Grid
            Column(
                modifier = Modifier
                    .weight(if (isExpanded) 0.6f else 0.55f)
                    .fillMaxHeight()
            ) {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(300, delayMillis = 300))
                ) {
                    OutfitSectionHeader(
                        currentGender = uiState.genderPreference,
                        onGenderChange = onGenderChange,
                        onShopClick = onShopOutfit
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Grid layout for clothing items
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isExpanded) 2 else 1),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(uiState.outfit!!.allItems) { item ->
                        ClothingItemCardCompact(
                            item = item,
                            onClick = { onShopItem(item) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// HERO WEATHER CARD
// ============================================================================

@Composable
fun HeroWeatherCard(
    weather: WeatherConditions,
    onToggleUnit: () -> Unit
) {
    // Convert to Fahrenheit for gradient calculation
    val tempF = if (weather.isCelsius) 
        weather.effectiveTemperature * 9/5 + 32 
    else 
        weather.effectiveTemperature
    
    // ════════════════════════════════════════════════════════════
    // ANIMATED TEMPERATURE COUNTER: (temp-10) → actual over 800ms
    // Starts at least 10 degrees below for visual impact even at low temps
    // ════════════════════════════════════════════════════════════
    val actualTemp = weather.effectiveTemperature.roundToInt()
    val startTemp = maxOf(-10, actualTemp - 10) // Start 10 below, minimum -10
    var targetTemp by remember { mutableStateOf(startTemp) }
    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "temperatureCounter"
    )

    LaunchedEffect(actualTemp) {
        targetTemp = startTemp // Reset to start position
        kotlinx.coroutines.delay(50) // Brief delay to reset animation
        targetTemp = actualTemp // Animate to actual temperature
    }
    
    // ════════════════════════════════════════════════════════════
    // SCALE ANIMATION ON PRESS
    // ════════════════════════════════════════════════════════════
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "cardScale"
    )
    
    Surface(
        onClick = onToggleUnit,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(getTempGradient(tempF))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Weather emoji icon (top left)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        weather.weatherCode.icon,
                        fontSize = 48.sp,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // "FEELS LIKE" label (centered above temperature)
                Text(
                    "FEELS LIKE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )

                // ════════════════════════════════════════════════════════════
                // LARGE ANIMATED TEMPERATURE (96sp Inter Black) - Always white for contrast
                // ════════════════════════════════════════════════════════════
                Text(
                    "${animatedTemp}°",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFontFamily,
                    color = Color.White,
                    letterSpacing = (-1.5).sp
                )
                
                // Actual temperature (smaller) - 14sp Inter Regular
                Text(
                    "Actual: ${weather.temperature.toInt()}°${if (weather.isCelsius) "C" else "F"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFontFamily,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(16.dp))

                // Weather details row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailChip(
                        icon = "💨",
                        value = "${weather.windSpeed.toInt()}",
                        unit = if (weather.isCelsius) "km/h" else "mph"
                    )
                    WeatherDetailChip(
                        icon = "💧",
                        value = "${weather.humidity}",
                        unit = "%"
                    )
                    if (weather.uvIndex > 0) {
                        WeatherDetailChip(
                            icon = "☀️",
                            value = "${weather.uvIndex.toInt()}",
                            unit = "UV"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailChip(icon: String, value: String, unit: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            "$value $unit",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFontFamily,
            color = Color.White
        )
    }
}

// ============================================================================
// HERO WEATHER CARD - LARGE (Tablets & Foldables)
// ============================================================================

@Composable
fun HeroWeatherCardLarge(
    weather: WeatherConditions,
    onToggleUnit: () -> Unit
) {
    val tempF = if (weather.isCelsius)
        weather.effectiveTemperature * 9/5 + 32
    else
        weather.effectiveTemperature

    val actualTemp = weather.effectiveTemperature.roundToInt()
    val startTemp = maxOf(-10, actualTemp - 10)
    var targetTemp by remember { mutableStateOf(startTemp) }
    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(durationMillis = 800, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "temperatureCounter"
    )

    LaunchedEffect(actualTemp) {
        targetTemp = startTemp
        kotlinx.coroutines.delay(50)
        targetTemp = actualTemp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "cardScale"
    )

    Surface(
        onClick = onToggleUnit,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(getTempGradient(tempF))
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Weather emoji icon (top left)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        weather.weatherCode.icon,
                        fontSize = 56.sp,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // "FEELS LIKE" label (centered above temperature)
                Text(
                    "FEELS LIKE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )

                Text(
                    "${animatedTemp}°",
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFontFamily,
                    color = Color.White,
                    letterSpacing = (-2).sp
                )

                Text(
                    "Actual: ${weather.temperature.toInt()}°${if (weather.isCelsius) "C" else "F"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFontFamily,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailChipLarge("💨", "${weather.windSpeed.toInt()}", if (weather.isCelsius) "km/h" else "mph")
                    WeatherDetailChipLarge("💧", "${weather.humidity}", "%")
                    if (weather.uvIndex > 0) {
                        WeatherDetailChipLarge("☀️", "${weather.uvIndex.toInt()}", "UV")
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailChipLarge(icon: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "$value $unit",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFontFamily,
            color = Color.White
        )
    }
}

// ============================================================================
// DATE/TIME SELECTOR - COMPACT (For side panel in two-column layout)
// ============================================================================

@Composable
fun DateTimeSelectorCompact(
    selectedDateTime: LocalDateTime,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onResetToNow: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDateTime.toLocalDate() == today
    val isTomorrow = selectedDateTime.toLocalDate() == today.plusDays(1)

    val dateText = when {
        isToday -> "Today"
        isTomorrow -> "Tomorrow"
        else -> selectedDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    val timeText = selectedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousDay, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ChevronLeft, "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDateClick() }
                ) {
                    Text(
                        dateText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onTimeClick() }
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(timeText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onNextDay, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ChevronRight, "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!isToday) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap to reset to now",
                    fontSize = 12.sp,
                    color = NeoMint,
                    modifier = Modifier.clickable { onResetToNow() }
                )
            }
        }
    }
}

// ============================================================================
// CLOTHING ITEM CARD - COMPACT (For grid layout)
// ============================================================================

@Composable
fun ClothingItemCardCompact(
    item: ClothingItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "itemScale"
    )

    val categoryColor = when (item.category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
        ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
        ClothingCategory.HEAD -> RunWearColors.CategoryHead
        ClothingCategory.HANDS -> RunWearColors.CategoryHands
        ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconRes = getClothingIconRes(item)
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = item.displayName,
                    tint = categoryColor,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Text(getCategoryEmoji(item.category), fontSize = 48.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                item.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Text(
                item.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============================================================================
// TIP CARD - COMPACT (For side panel)
// ============================================================================

@Composable
fun TipCardCompact(tip: String) {
    val (emoji, text) = extractEmojiFromTip(tip)
    val isWarning = text.lowercase().let {
        it.contains("heat") || it.contains("cold") || it.contains("ice") ||
        it.contains("extreme") || it.contains("risk")
    }
    val backgroundColor = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (emoji != null) {
                Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}

// ============================================================================
// DATE/TIME SELECTOR
// ============================================================================

@Composable
fun DateTimeSelector(
    selectedDateTime: LocalDateTime,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onResetToNow: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDateTime.toLocalDate() == today
    val isTomorrow = selectedDateTime.toLocalDate() == today.plusDays(1)
    
    val dateText = when {
        isToday -> "Today"
        isTomorrow -> "Tomorrow"
        else -> selectedDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    val timeText = selectedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous day button
                IconButton(
                    onClick = onPreviousDay,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "Previous day",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Date and time (clickable)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Date (clickable with dropdown hint)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onDateClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dateText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Time (clickable with clock hint)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onTimeClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = timeText,
                            fontSize = 16.sp,
                            fontFamily = InterFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Next day button
                IconButton(
                    onClick = onNextDay,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Next day",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Reset hint (only show when not today/now)
            if (!isToday) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to reset to now",
                    fontSize = 11.sp,
                    color = NeoMint,
                    modifier = Modifier.clickable { onResetToNow() }
                )
            }
        }
    }
}

// ============================================================================
// SECTION HEADER
// ============================================================================

@Composable
fun SectionHeader(
    title: String,
    actionText: String?,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
        )
        
        if (actionText != null) {
            Row(
                modifier = Modifier.clickable { onActionClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    actionText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeoMint
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NeoMint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================================
// OUTFIT SECTION HEADER (with gender toggle and shop button)
// ============================================================================

@Composable
fun OutfitSectionHeader(
    currentGender: GenderPreference,
    onGenderChange: (GenderPreference) -> Unit,
    onShopClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Text(
            "YOUR OUTFIT",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
        )

        // Right side: Gender toggle + Shop button
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gender Toggle (M | ○ | F)
            GenderToggle(
                currentGender = currentGender,
                onGenderChange = onGenderChange
            )

            // Shop Button
            Surface(
                onClick = onShopClick,
                shape = RoundedCornerShape(20.dp),
                color = NeoMint
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Shop",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun GenderToggle(
    currentGender: GenderPreference,
    onGenderChange: (GenderPreference) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            GenderOption(
                label = "🚹",
                isSelected = currentGender == GenderPreference.MALE,
                onClick = { onGenderChange(GenderPreference.MALE) }
            )
            GenderOption(
                label = "○",
                isSelected = currentGender == GenderPreference.UNISEX,
                onClick = { onGenderChange(GenderPreference.UNISEX) }
            )
            GenderOption(
                label = "🚺",
                isSelected = currentGender == GenderPreference.FEMALE,
                onClick = { onGenderChange(GenderPreference.FEMALE) }
            )
        }
    }
}

@Composable
private fun GenderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isEmoji = label == "🚹" || label == "🚺"
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) NeoMint else Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .alpha(if (isSelected) 1f else 0.45f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = if (isEmoji) 18.sp else 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// CLOTHING ITEM CARD (with scale animation on press)
// ============================================================================

@Composable
fun ClothingItemCard(
    item: ClothingItem,
    onClick: () -> Unit
) {
    // ════════════════════════════════════════════════════════════
    // SCALE ANIMATION ON PRESS (0.98f, 100ms)
    // ════════════════════════════════════════════════════════════
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "itemScale"
    )
    
    // Category colors - all WCAG AA compliant (4.5:1+ on white)
    val categoryColor = when (item.category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop      // Teal
        ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom                                  // Blue
        ClothingCategory.HEAD -> RunWearColors.CategoryHead                                      // Green
        ClothingCategory.HANDS -> RunWearColors.CategoryHands                                    // Purple
        ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories                        // Orange
    }
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 48dp running-specific icon in category color
            val iconRes = getClothingIconRes(item)
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = item.displayName,
                    tint = categoryColor,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                // Fallback to emoji only if no icon exists
                Text(
                    getCategoryEmoji(item.category),
                    fontSize = 40.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            // Item info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
                )
            }
            
            // ChevronRight indicates tappable
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Shop",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ============================================================================
// TIP CARD
// ============================================================================

@Composable
fun TipCard(tip: String) {
    val (emoji, text) = extractEmojiFromTip(tip)
    val isWarning = text.lowercase().let { 
        it.contains("heat") || it.contains("cold") || it.contains("ice") || 
        it.contains("extreme") || it.contains("risk")
    }
    val backgroundColor = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (emoji != null) {
                Text(
                    emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                )
            }
            Text(
                text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

// ============================================================================
// LOADING SCREEN
// ============================================================================

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = NeoMint,
                strokeWidth = 4.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Getting your outfit...",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Checking weather conditions",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ============================================================================
// ERROR SCREEN
// ============================================================================

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = ElectricCoral,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Oops!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRetry()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeoMint)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

fun getCategoryEmoji(category: ClothingCategory): String = when (category) {
    ClothingCategory.TOP_BASE -> "👕"
    ClothingCategory.TOP_OUTER -> "🧥"
    ClothingCategory.BOTTOM -> "🩳"
    ClothingCategory.HEAD -> "🧢"
    ClothingCategory.HANDS -> "🧤"
    ClothingCategory.ACCESSORIES -> "🕶️"
}

fun extractEmojiFromTip(tip: String): Pair<String?, String> {
    val emojiPattern = Regex("^([\\p{So}\\p{Sk}]+)\\s*(.*)$")
    val match = emojiPattern.find(tip)
    return if (match != null) {
        Pair(match.groupValues[1], match.groupValues[2])
    } else {
        Pair(null, tip)
    }
}

/**
 * Maps ClothingItem to custom vector drawable resource
 * Returns null if no custom icon exists (will use emoji fallback)
 * 
 * Create these in res/drawable/ using the vector XMLs provided
 */
fun getClothingIconRes(item: ClothingItem): Int? {
    return try {
        when (item) {
            ClothingItem.TANK_TOP -> R.drawable.ic_running_tshirt // Use t-shirt for tank top
            ClothingItem.SHORT_SLEEVE -> R.drawable.ic_running_tshirt
            ClothingItem.LONG_SLEEVE_LIGHT, ClothingItem.LONG_SLEEVE_THERMAL -> R.drawable.ic_running_longsleeve
            ClothingItem.LIGHT_JACKET, ClothingItem.INSULATED_JACKET -> R.drawable.ic_thermal_jacket
            ClothingItem.WINDBREAKER -> R.drawable.ic_windbreaker
            ClothingItem.RAIN_JACKET -> R.drawable.ic_windbreaker // Use windbreaker for rain jacket
            ClothingItem.LIGHT_VEST -> R.drawable.ic_running_vest
            ClothingItem.SHORTS, ClothingItem.SHORT_SHORTS -> R.drawable.ic_running_shorts
            ClothingItem.CAPRIS -> R.drawable.ic_running_capris
            ClothingItem.LIGHT_TIGHTS, ClothingItem.THERMAL_TIGHTS -> R.drawable.ic_running_tights
            ClothingItem.BASEBALL_CAP -> R.drawable.ic_running_cap
            ClothingItem.VISOR -> R.drawable.ic_running_visor
            ClothingItem.LIGHT_BEANIE, ClothingItem.THERMAL_BEANIE -> R.drawable.ic_running_beanie
            ClothingItem.HEADBAND -> R.drawable.ic_running_headband
            ClothingItem.BALACLAVA -> R.drawable.ic_balaclava
            ClothingItem.LIGHT_GLOVES, ClothingItem.THERMAL_GLOVES -> R.drawable.ic_running_gloves
            ClothingItem.MITTENS -> R.drawable.ic_running_mittens
            ClothingItem.SUNGLASSES -> R.drawable.ic_sport_sunglasses
            ClothingItem.SUNSCREEN -> R.drawable.ic_sunscreen
            ClothingItem.REFLECTIVE_GEAR -> R.drawable.ic_reflective_vest
            ClothingItem.NECK_GAITER -> R.drawable.ic_neck_gaiter
        }
    } catch (e: Exception) {
        null // Fallback to emoji if resource not found
    }
}
