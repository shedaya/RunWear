package com.runwear.wear.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import coil.compose.AsyncImage
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.wear.presentation.theme.RunWearColors
import com.runwear.wear.presentation.theme.WeatherColors
import com.runwear.wear.presentation.viewmodel.WearUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Hero-image design WearMainScreen with 3-page horizontal layout.
 *
 * Pages:
 * 1. Hero Weather - Image with temp tint, large temperature display
 * 2. Outfit List - Clothing items as chips
 * 3. Tips - Running tips
 */
@Composable
fun HeroWearMainScreen(
    uiState: WearUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        when {
            !uiState.hasLocationPermission -> WearPermissionScreen(onRequestPermission)
            uiState.isLoading -> WearLoadingScreen()
            uiState.error != null -> WearErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                HeroWearContent(
                    uiState = uiState,
                    onToggleUnit = onToggleUnit,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
private fun HeroWearContent(
    uiState: WearUiState,
    onToggleUnit: () -> Unit,
    onRefresh: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HeroWeatherPage(
                    weather = uiState.weather!!,
                    heroImageUrl = uiState.heroImageUrl,
                    locationName = uiState.locationName,
                    outfitItemCount = uiState.outfit!!.allItems.size,
                    onToggleUnit = onToggleUnit
                )
                1 -> OutfitPage(
                    outfit = uiState.outfit!!,
                    onRefresh = onRefresh
                )
                2 -> TipsPage(
                    tips = uiState.outfit!!.tips,
                    locationName = uiState.locationName
                )
            }
        }

        // Page indicator at bottom
        PageIndicator(
            pageCount = 3,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

// ============================================================================
// PAGE 1: HERO WEATHER
// ============================================================================

@Composable
private fun HeroWeatherPage(
    weather: WeatherConditions,
    heroImageUrl: String?,
    locationName: String,
    outfitItemCount: Int,
    onToggleUnit: () -> Unit
) {
    val tempColor = WeatherColors.forTemperature(weather.feelsLikeInFahrenheit)
    val tintColor = WeatherColors.forTemperatureTint(weather.feelsLikeInFahrenheit)

    // Animated temperature
    val actualTemp = weather.effectiveTemperature.roundToInt()
    val startTemp = maxOf(-10, actualTemp - 8)
    var targetTemp by remember { mutableIntStateOf(startTemp) }
    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "temp"
    )

    LaunchedEffect(actualTemp) {
        targetTemp = startTemp
        kotlinx.coroutines.delay(100)
        targetTemp = actualTemp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        // Hero image (circular crop for round watches)
        heroImageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Runner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        // Temperature tint overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tintColor)
        )

        // Gradient for text readability - stronger for temperature area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.5f),
                            0.25f to Color.Black.copy(alpha = 0.3f),
                            0.4f to Color.Black.copy(alpha = 0.5f),
                            0.6f to Color.Black.copy(alpha = 0.5f),
                            0.75f to Color.Black.copy(alpha = 0.3f),
                            1.0f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Weather icon and condition
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = weather.weatherCode.icon,
                    fontSize = 24.sp
                )
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            // Center: Large temperature with glass backdrop (tappable)
            Card(
                onClick = onToggleUnit,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // Temperature with colored accent border/shadow
                    val unitSymbol = if (weather.isCelsius) "C" else "F"
                    Box(contentAlignment = Alignment.Center) {
                        // Shadow layer for depth
                        Text(
                            text = "${animatedTemp}°$unitSymbol",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = tempColor.copy(alpha = 0.4f),
                            letterSpacing = (-1).sp
                        )
                        // Main white text
                        Text(
                            text = "${animatedTemp}°$unitSymbol",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                    }
                    // Color indicator bar
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tempColor)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Feels like",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Bottom: Swipe hint with animated arrow
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val isToday = weather.dateTime.toLocalDate() == LocalDate.now()
                Text(
                    text = if (isToday) "Today" else weather.dateTime.format(
                        DateTimeFormatter.ofPattern("EEE")
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(4.dp))
                // More prominent swipe hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Swipe for $outfitItemCount items",
                        style = MaterialTheme.typography.labelSmall,
                        color = RunWearColors.Primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelMedium,
                        color = RunWearColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// PAGE 2: OUTFIT LIST
// ============================================================================

@Composable
private fun OutfitPage(
    outfit: OutfitRecommendation,
    onRefresh: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)
        ) {
            // Title
            item {
                Text(
                    text = "Your Outfit",
                    style = MaterialTheme.typography.titleSmall,
                    color = RunWearColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Outfit items as compact chips
            items(outfit.allItems) { item ->
                WearOutfitChip(item)
            }

            // Refresh button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.size(width = 100.dp, height = 36.dp)
                ) {
                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WearOutfitChip(item: ClothingItem) {
    val categoryColor = getCategoryColor(item.category)

    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RunWearColors.SurfaceElevated
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category color indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            )

            Spacer(Modifier.width(8.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getCategoryEmoji(item.category),
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Name
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ============================================================================
// PAGE 3: TIPS
// ============================================================================

@Composable
private fun TipsPage(
    tips: List<String>,
    locationName: String
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)
        ) {
            // Title
            item {
                Text(
                    text = "Tips",
                    style = MaterialTheme.typography.titleSmall,
                    color = RunWearColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (tips.isEmpty()) {
                item {
                    Text(
                        text = "No specific tips for today. Enjoy your run!",
                        style = MaterialTheme.typography.bodySmall,
                        color = RunWearColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(tips) { tip ->
                    WearTipCard(tip)
                }
            }

            // Location footer
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = RunWearColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WearTipCard(tip: String) {
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RunWearColors.SurfaceElevated
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(text = "💡", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = tip,
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextPrimary
            )
        }
    }
}

// ============================================================================
// PAGE INDICATOR
// ============================================================================

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) RunWearColors.Primary
                        else RunWearColors.TextMuted.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

// ============================================================================
// HELPER SCREENS
// ============================================================================

@Composable
private fun WearLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

@Composable
private fun WearErrorScreen(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "⚠️", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = RunWearColors.TextSecondary,
                maxLines = 2
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun WearPermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "📍", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Location needed",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
}

// ============================================================================
// HELPERS
// ============================================================================

private fun getCategoryColor(category: ClothingCategory): Color = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
    ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
    ClothingCategory.HEAD -> RunWearColors.CategoryHead
    ClothingCategory.HANDS -> RunWearColors.CategoryHands
    ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
}

private fun getCategoryEmoji(category: ClothingCategory): String = when (category) {
    ClothingCategory.TOP_BASE -> "👕"
    ClothingCategory.TOP_MID -> "🧥"
    ClothingCategory.TOP_OUTER -> "🧥"
    ClothingCategory.BOTTOM -> "👖"
    ClothingCategory.HEAD -> "🧢"
    ClothingCategory.HANDS -> "🧤"
    ClothingCategory.ACCESSORIES -> "🕶️"
}
