package com.runwear.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.wear.presentation.theme.RunWearColors
import com.runwear.wear.presentation.theme.WeatherColors
import com.runwear.wear.presentation.viewmodel.WearUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WearMainScreen(
    uiState: WearUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            !uiState.hasLocationPermission -> PermissionScreen(onRequestPermission)
            uiState.isLoading -> LoadingScreen()
            uiState.error != null -> ErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)
                ) {
                    // Weather Summary Card
                    item {
                        WeatherSummaryCard(
                            weather = uiState.weather,
                            onToggleUnit = onToggleUnit
                        )
                    }
                    
                    // Date/Time Selector
                    item {
                        DateTimeSelector(
                            uiState = uiState,
                            onPreviousDay = onPreviousDay,
                            onNextDay = onNextDay,
                            onResetToNow = onResetToNow
                        )
                    }
                    
                    // Section Title
                    item {
                        Text(
                            text = "Your Outfit",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    // Clothing Items
                    items(uiState.outfit.allItems) { item ->
                        ClothingItemCard(item)
                    }
                    
                    // Tips
                    if (uiState.outfit.tips.isNotEmpty()) {
                        item {
                            Text(
                                text = "Tips",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        items(uiState.outfit.tips) { tip ->
                            TipCard(tip)
                        }
                    }
                    
                    // Location Footer
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Text(
                            text = uiState.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Refresh Button
                    item {
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSummaryCard(
    weather: com.runwear.shared.domain.model.WeatherConditions,
    onToggleUnit: () -> Unit
) {
    val tempColor = WeatherColors.forTemperature(weather.feelsLikeInFahrenheit)
    val unit = if (weather.isCelsius) "°C" else "°F"
    val windUnit = if (weather.isCelsius) "km/h" else "mph"
    
    Card(
        onClick = onToggleUnit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = weather.weatherCode.icon,
                fontSize = 32.sp
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = "${weather.feelsLike.toInt()}$unit",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = tempColor
            )
            
            Text(
                text = "Feels like",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(4.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeatherDetail("💨", "${weather.windSpeed.toInt()} $windUnit")
                WeatherDetail("💧", "${weather.humidity}%")
                if (weather.uvIndex > 0) {
                    WeatherDetail("☀️", "UV ${weather.uvIndex.toInt()}")
                }
            }
            
            Text(
                text = "Tap to switch °F/°C",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun WeatherDetail(icon: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 12.sp)
        Spacer(Modifier.width(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DateTimeSelector(
    uiState: WearUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToNow: () -> Unit
) {
    val dt = uiState.selectedDateTime
    val isToday = dt.toLocalDate() == LocalDate.now()
    val dateText = when {
        isToday -> "Today"
        dt.toLocalDate() == LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> dt.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    val timeText = dt.format(DateTimeFormatter.ofPattern("h:mm a"))
    
    Card(
        onClick = onResetToNow,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onPreviousDay,
                modifier = Modifier.size(32.dp)
            ) {
                Text("◀", fontSize = 14.sp)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            FilledIconButton(
                onClick = onNextDay,
                modifier = Modifier.size(32.dp)
            ) {
                Text("▶", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ClothingItemCard(item: ClothingItem) {
    val categoryColor = when (item.category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> RunWearColors.Primary
        ClothingCategory.BOTTOM -> RunWearColors.Secondary
        ClothingCategory.HEAD -> RunWearColors.Tertiary
        ClothingCategory.HANDS -> MaterialTheme.colorScheme.tertiary
        ClothingCategory.ACCESSORIES -> MaterialTheme.colorScheme.secondary
    }
    
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.icon, fontSize = 18.sp)
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TipCard(tip: String) {
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "⚠️", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "📍", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Location needed for weather",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
}
