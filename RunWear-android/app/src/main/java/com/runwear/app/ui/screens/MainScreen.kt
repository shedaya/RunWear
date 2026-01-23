package com.runwear.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.getTemperatureColor
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onSelectHour: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onShowDatePicker: () -> Unit,
    onHideDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit,
    onHideTimePicker: () -> Unit,
    onShowSettings: () -> Unit,
    onHideSettings: () -> Unit,
    onSetComfortPreference: (ComfortPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> LoadingScreen()
            uiState.error != null -> ErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                MainContent(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onToggleUnit = onToggleUnit,
                    onNextDay = onNextDay,
                    onPreviousDay = onPreviousDay,
                    onResetToNow = onResetToNow,
                    onSelectHour = onSelectHour,
                    onShowDatePicker = onShowDatePicker,
                    onShowTimePicker = onShowTimePicker,
                    onShowSettings = onShowSettings
                )
            }
        }
        
        // Date Picker Bottom Sheet
        if (uiState.showDatePicker) {
            DatePickerSheet(
                selectedDate = uiState.selectedDateTime.toLocalDate(),
                onSelectDate = onSelectDate,
                onDismiss = onHideDatePicker
            )
        }
        
        // Time Picker Bottom Sheet
        if (uiState.showTimePicker) {
            TimePickerSheet(
                selectedHour = uiState.selectedDateTime.hour,
                onSelectHour = onSelectHour,
                onDismiss = onHideTimePicker
            )
        }
        
        // Settings Bottom Sheet
        if (uiState.showSettings) {
            SettingsSheet(
                temperatureUnit = uiState.temperatureUnit,
                comfortPreference = uiState.comfortPreference,
                onToggleUnit = onToggleUnit,
                onSetComfortPreference = onSetComfortPreference,
                onDismiss = onHideSettings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onSelectHour: (Int) -> Unit,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit,
    onShowSettings: () -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header with logo and settings
            item {
                Header(
                    locationName = uiState.locationName,
                    onShowSettings = onShowSettings
                )
            }
            
            // Weather Card
            item {
                WeatherCard(
                    weather = uiState.weather!!,
                    onToggleUnit = onToggleUnit
                )
            }
            
            // Date/Time Selector
            item {
                DateTimeSelector(
                    selectedDateTime = uiState.selectedDateTime,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onResetToNow = onResetToNow,
                    onShowDatePicker = onShowDatePicker,
                    onShowTimePicker = onShowTimePicker
                )
            }
            
            // Hourly Chart
            if (uiState.hourlyForecast.isNotEmpty()) {
                item {
                    HourlyForecastChart(
                        forecast = uiState.hourlyForecast,
                        selectedDateTime = uiState.selectedDateTime,
                        isCelsius = uiState.temperatureUnit == TemperatureUnit.CELSIUS,
                        onSelectHour = onSelectHour
                    )
                }
            }
            
            // Section Title: Your Outfit
            item {
                SectionTitle(title = "Your Outfit")
            }
            
            // Clothing Items
            items(uiState.outfit!!.allItems) { item ->
                ClothingItemCard(item = item)
            }
            
            // Tips Section
            if (uiState.outfit.tips.isNotEmpty()) {
                item {
                    SectionTitle(title = "Tips")
                }
                
                items(uiState.outfit.tips) { tip ->
                    TipCard(tip = tip)
                }
            }
            
            // Location footer
            item {
                LocationFooter(locationName = uiState.locationName)
            }
        }
    }
}

@Composable
private fun Header(
    locationName: String,
    onShowSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "RunWear",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        IconButton(onClick = onShowSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherConditions,
    onToggleUnit: () -> Unit
) {
    val tempColor by animateColorAsState(
        targetValue = getTemperatureColor(weather.feelsLikeInFahrenheit),
        label = "tempColor"
    )
    val unit = if (weather.isCelsius) "°C" else "°F"
    val windUnit = if (weather.isCelsius) "km/h" else "mph"
    
    Card(
        onClick = onToggleUnit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weather icon
            Text(
                text = weather.weatherCode.icon,
                fontSize = 56.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Feels like label
            Text(
                text = "Feels Like",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Temperature
            Text(
                text = "${weather.feelsLike.toInt()}$unit",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = tempColor
            )
            
            // Actual temperature
            Text(
                text = "Actual: ${weather.temperature.toInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Weather details row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                WeatherDetail(icon = "💨", value = "${weather.windSpeed.toInt()} $windUnit")
                WeatherDetail(icon = "💧", value = "${weather.humidity}%")
                if (weather.uvIndex > 0) {
                    WeatherDetail(icon = "☀️", value = "UV ${weather.uvIndex.toInt()}")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tap hint
            Text(
                text = "Tap to switch °F/°C",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun WeatherDetail(icon: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 14.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DateTimeSelector(
    selectedDateTime: LocalDateTime,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToNow: () -> Unit,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit
) {
    val isToday = selectedDateTime.toLocalDate() == LocalDate.now()
    val dateText = when {
        isToday -> "Today"
        selectedDateTime.toLocalDate() == LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> selectedDateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    val timeText = selectedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous day button
            FilledIconButton(
                onClick = onPreviousDay,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous day"
                )
            }
            
            // Date and time (clickable)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowDatePicker() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "@",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowTimePicker() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                if (!isToday || selectedDateTime.hour != LocalDateTime.now().hour) {
                    Text(
                        text = "Reset to now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onResetToNow() }
                            .padding(top = 4.dp)
                    )
                }
            }
            
            // Next day button
            FilledIconButton(
                onClick = onNextDay,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next day"
                )
            }
        }
    }
}

@Composable
private fun HourlyForecastChart(
    forecast: List<HourlyForecast>,
    selectedDateTime: LocalDateTime,
    isCelsius: Boolean,
    onSelectHour: (Int) -> Unit
) {
    val selectedDate = selectedDateTime.toLocalDate()
    val selectedHour = selectedDateTime.hour
    
    // Filter to show hours from current time to end of selected day
    val now = LocalDateTime.now()
    val filteredForecast = forecast.filter { hourly ->
        val hourDate = hourly.dateTime.toLocalDate()
        hourDate == selectedDate && 
        (hourDate != now.toLocalDate() || hourly.hour >= now.hour)
    }.take(24)
    
    if (filteredForecast.isEmpty()) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "Hourly Forecast",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, bottom = 12.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(filteredForecast) { _, hourly ->
                val isSelected = hourly.hour == selectedHour && 
                                 hourly.dateTime.toLocalDate() == selectedDate
                
                HourlyItem(
                    hourly = hourly,
                    isCelsius = isCelsius,
                    isSelected = isSelected,
                    onClick = { onSelectHour(hourly.hour) }
                )
            }
        }
    }
}

@Composable
private fun HourlyItem(
    hourly: HourlyForecast,
    isCelsius: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val timeText = when (hourly.hour) {
        0 -> "12 AM"
        12 -> "12 PM"
        in 1..11 -> "${hourly.hour} AM"
        else -> "${hourly.hour - 12} PM"
    }
    
    val temp = if (isCelsius) hourly.temperature else hourly.temperatureInFahrenheit
    val tempColor = getTemperatureColor(hourly.temperatureInFahrenheit)
    
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                      else MaterialTheme.colorScheme.surfaceContainer,
        label = "containerColor"
    )
    
    Card(
        onClick = onClick,
        modifier = Modifier.width(72.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = hourly.weatherCode.icon,
                fontSize = 24.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${temp.toInt()}°",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tempColor
            )
            
            if (hourly.precipitationProbability > 20) {
                Text(
                    text = "${hourly.precipitationProbability}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun ClothingItemCard(item: ClothingItem) {
    val categoryColor = when (item.category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_OUTER -> MaterialTheme.colorScheme.primary
        ClothingCategory.BOTTOM -> MaterialTheme.colorScheme.secondary
        ClothingCategory.HEAD -> MaterialTheme.colorScheme.tertiary
        ClothingCategory.HANDS -> MaterialTheme.colorScheme.tertiary
        ClothingCategory.ACCESSORIES -> MaterialTheme.colorScheme.secondary
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.icon,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TipCard(tip: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun LocationFooter(locationName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = locationName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Getting your outfit...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "⚠️", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Oops!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}
