package com.runwear.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ============================================================================
// AMAZON AFFILIATE - PWA v2.9 (Amazon ONLY)
// ============================================================================

private const val AMAZON_AFFILIATE_TAG = "runwear-20"
private const val AMAZON_PLATFORM_SUBTAG = "android"

private fun buildAmazonSearchUrl(item: ClothingItem, gender: GenderPreference): String {
    val genderPrefix = when (gender) {
        GenderPreference.MALE -> "mens "
        GenderPreference.FEMALE -> "womens "
        GenderPreference.UNISEX -> ""
    }
    val searchTerm = "${genderPrefix}running ${item.displayName}"
        .replace(" ", "+")
    return "https://www.amazon.com/s?k=$searchTerm&tag=$AMAZON_AFFILIATE_TAG&ascsubtag=$AMAZON_PLATFORM_SUBTAG"
}

// ============================================================================
// DATE PICKER SHEET - PWA v2.9 Style
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val today = LocalDate.now()
    val days = (0..6).map { today.plusDays(it.toLong()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SELECT DATE",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // 2-column grid per PWA
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.chunked(2).forEach { rowDays ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowDays.forEach { date ->
                            val isSelected = date == selectedDate
                            val label = when {
                                date == today -> "Today"
                                date == today.plusDays(1) -> "Tomorrow"
                                else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                            }

                            Surface(
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) RunWearColors.Primary else RunWearColors.BgCardLight
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else RunWearColors.TextPrimary
                                )
                            }
                        }
                        if (rowDays.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================================
// TIME PICKER SHEET - PWA v2.9 Style
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    selectedHour: Int,
    onSelectTime: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val availableHours = (5..21).toList() // 5 AM to 9 PM

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SELECT TIME",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // 3-column grid per PWA
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableHours.chunked(3).forEach { rowHours ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowHours.forEach { hour ->
                            val isSelected = hour == selectedHour
                            val timeText = LocalTime.of(hour, 0)
                                .format(DateTimeFormatter.ofPattern("h a"))

                            Surface(
                                onClick = { onSelectTime(hour) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) RunWearColors.Primary else RunWearColors.BgCardLight
                            ) {
                                Text(
                                    text = timeText,
                                    modifier = Modifier.padding(14.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else RunWearColors.TextPrimary
                                )
                            }
                        }
                        repeat(3 - rowHours.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================================
// SETTINGS SHEET - PWA v2.9 Style
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    temperatureUnit: TemperatureUnit,
    comfortPreference: ComfortPreference,
    genderPreference: GenderPreference,
    onToggleUnit: () -> Unit,
    onSetComfortPreference: (ComfortPreference) -> Unit,
    onSetGenderPreference: (GenderPreference) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "SETTINGS",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Temperature Unit - PWA v2.9 toggle style
            SettingRow(
                label = "Temperature Unit",
                sublabel = "Display temperature in"
            ) {
                UnitToggle(
                    isCelsius = temperatureUnit == TemperatureUnit.CELSIUS,
                    onToggle = { onToggleUnit() }
                )
            }

            HorizontalDivider(color = RunWearColors.BgCardLight, modifier = Modifier.padding(vertical = 16.dp))

            // Body Temperature / Comfort
            Column {
                Text(
                    text = "Body Temperature",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = "How do you typically feel when running?",
                    fontSize = 12.sp,
                    color = RunWearColors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                ComfortSelector(
                    selected = comfortPreference,
                    onSelect = onSetComfortPreference
                )
            }

            HorizontalDivider(color = RunWearColors.BgCardLight, modifier = Modifier.padding(vertical = 16.dp))

            // Fit Preference - PWA v2.9 toggleable Male/Female
            Column {
                Text(
                    text = "Fit Preference",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = "Tap to select, tap again to deselect",
                    fontSize = 12.sp,
                    color = RunWearColors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                GenderToggleSettings(
                    selected = genderPreference,
                    onSelect = onSetGenderPreference
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    sublabel: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextPrimary
            )
            sublabel?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = RunWearColors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        content()
    }
}

/**
 * PWA v2.9 Unit Toggle - BOTH options visible, selected has teal background
 */
@Composable
private fun UnitToggle(
    isCelsius: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RunWearColors.BgCardLight)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // °F option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (!isCelsius) RunWearColors.Primary else Color.Transparent)
                .clickable { if (isCelsius) onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "°F",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (!isCelsius) Color.White else RunWearColors.TextMuted
            )
        }

        // °C option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCelsius) RunWearColors.Primary else Color.Transparent)
                .clickable { if (!isCelsius) onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "°C",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCelsius) Color.White else RunWearColors.TextMuted
            )
        }
    }
}

/**
 * PWA v2.9 Comfort Selector - 5 options in a row
 */
@Composable
private fun ComfortSelector(
    selected: ComfortPreference,
    onSelect: (ComfortPreference) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RunWearColors.BgCardLight)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ComfortPreference.entries.forEach { pref ->
            val isSelected = pref == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable { onSelect(pref) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pref.shortLabel,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else RunWearColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

/**
 * PWA v2.9 Gender Toggle for Settings - Full width Male/Female toggleable
 */
@Composable
private fun GenderToggleSettings(
    selected: GenderPreference,
    onSelect: (GenderPreference) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RunWearColors.BgCardLight)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Male
        val isMale = selected == GenderPreference.MALE
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isMale) RunWearColors.Primary else Color.Transparent)
                .clickable {
                    onSelect(if (isMale) GenderPreference.UNISEX else GenderPreference.MALE)
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Male",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMale) Color.White else RunWearColors.TextSecondary
            )
        }

        // Female
        val isFemale = selected == GenderPreference.FEMALE
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFemale) RunWearColors.Primary else Color.Transparent)
                .clickable {
                    onSelect(if (isFemale) GenderPreference.UNISEX else GenderPreference.FEMALE)
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Female",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isFemale) Color.White else RunWearColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// LOCATION PICKER SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerSheet(
    currentLocation: String,
    onSearch: (String) -> Unit,
    onSelectLocation: (Double, Double, String) -> Unit,
    onDismiss: () -> Unit,
    searchResults: List<LocationResult> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "SET LOCATION",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search input
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) onSearch(it)
                },
                placeholder = { Text("Enter city or ZIP code") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RunWearColors.Primary,
                    unfocusedBorderColor = RunWearColors.BgCardLight
                )
            )

            Spacer(Modifier.height(16.dp))

            // Results
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(searchResults) { result ->
                        Surface(
                            onClick = { onSelectLocation(result.lat, result.lon, result.name) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.name,
                                    color = RunWearColors.TextPrimary
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = RunWearColors.TextMuted
                                )
                            }
                        }
                        HorizontalDivider(color = RunWearColors.BgCardLight)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

data class LocationResult(
    val lat: Double,
    val lon: Double,
    val name: String
)

// ============================================================================
// SHOP ALL SHEET - Shows all outfit items with Amazon links
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopAllSheet(
    items: List<ClothingItem>,
    genderPreference: GenderPreference,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "SHOP YOUR OUTFIT",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Items list
            items.forEach { item ->
                ShopItemRow(
                    item = item,
                    onClick = {
                        val url = buildAmazonSearchUrl(item, genderPreference)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }

            // FTC Disclosure
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = "Affiliate Disclosure: As an Amazon Associate, RunWear earns from qualifying purchases. This helps support the app at no extra cost to you.",
                    fontSize = 11.sp,
                    color = RunWearColors.TextMuted,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShopItemRow(
    item: ClothingItem,
    onClick: () -> Unit
) {
    val categoryColor = getCategoryColor(item.category)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = RunWearColors.BgCardLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        categoryColor.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.icon,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = "Shop on Amazon",
                    fontSize = 12.sp,
                    color = RunWearColors.TextSecondary
                )
            }

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = RunWearColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun getCategoryColor(category: ClothingCategory): Color {
    return when (category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
        ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
        ClothingCategory.HEAD -> RunWearColors.CategoryHead
        ClothingCategory.HANDS -> RunWearColors.CategoryHands
        ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
    }
}

private fun getCategoryDisplayName(category: ClothingCategory): String {
    return when (category) {
        ClothingCategory.TOP_BASE -> "Base Layer"
        ClothingCategory.TOP_OUTER -> "Outer Layer"
        ClothingCategory.BOTTOM -> "Bottom"
        ClothingCategory.HEAD -> "Headwear"
        ClothingCategory.HANDS -> "Gloves"
        ClothingCategory.ACCESSORIES -> "Accessories"
    }
}

// ============================================================================
// SINGLE ITEM SHOP SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSheet(
    item: ClothingItem,
    genderPreference: GenderPreference,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val categoryColor = getCategoryColor(item.category)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Item icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        categoryColor.copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.icon, fontSize = 40.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = item.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )

            Text(
                text = getCategoryDisplayName(item.category),
                fontSize = 14.sp,
                color = RunWearColors.TextSecondary
            )

            Spacer(Modifier.height(24.dp))

            // Amazon button
            Button(
                onClick = {
                    val url = buildAmazonSearchUrl(item, genderPreference)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Shop on Amazon",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================================
// WEATHER DETAIL SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailSheet(
    type: String,
    weather: WeatherConditions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            when (type) {
                "condition" -> WeatherConditionDetail(weather)
                "wind" -> WindDetail(weather)
                "humidity" -> HumidityDetail(weather)
                "precipitation" -> PrecipitationDetail(weather)
                "uv" -> UVDetail(weather)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WeatherConditionDetail(weather: WeatherConditions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = weather.weatherCode.icon, fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = weather.weatherCode.description,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("Cloud Cover", "${weather.cloudCover}%")
        DetailRow("Temperature", "${weather.temperature.toInt()}°")
        DetailRow("Feels Like", "${weather.feelsLike.toInt()}°")
    }
}

@Composable
private fun WindDetail(weather: WeatherConditions) {
    val unit = if (weather.isCelsius) "km/h" else "mph"
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Air,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.Primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Wind",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("Speed", "${weather.windSpeed.toInt()} $unit")
        DetailRow("Gusts", "${weather.windGusts.toInt()} $unit")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Wind can make it feel cooler. Dress slightly warmer on windy days.",
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun HumidityDetail(weather: WeatherConditions) {
    val comfortLevel = when {
        weather.humidity > 70 -> "Muggy"
        weather.humidity < 30 -> "Dry"
        else -> "Comfortable"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.Primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Humidity",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("Relative Humidity", "${weather.humidity}%")
        DetailRow("Comfort Level", comfortLevel)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "High humidity makes sweat evaporate slower. Choose breathable, moisture-wicking fabrics.",
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PrecipitationDetail(weather: WeatherConditions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.WaterDrop,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.Primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Precipitation",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("Chance of Rain", "${weather.precipitationProbability}%")
        if (weather.precipitation > 0) {
            DetailRow("Expected Amount", "${weather.precipitation} mm")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (weather.precipitationProbability > 50)
                "High chance of rain. Consider a water-resistant jacket."
            else
                "Low chance of rain, but be prepared for changing conditions.",
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun UVDetail(weather: WeatherConditions) {
    val riskLevel = when {
        weather.uvIndex >= 11 -> "Extreme"
        weather.uvIndex >= 8 -> "Very High"
        weather.uvIndex >= 6 -> "High"
        weather.uvIndex >= 3 -> "Moderate"
        else -> "Low"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.WbSunny,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.Primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "UV Index",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        DetailRow("UV Index", "${weather.uvIndex.toInt()}")
        DetailRow("Risk Level", riskLevel)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (weather.uvIndex >= 6)
                "High UV! Wear sunscreen, sunglasses, and a hat. Avoid peak hours (10am-4pm)."
            else
                "Moderate UV exposure. Sunglasses recommended for comfort.",
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary
        )
    }
}

// ============================================================================
// COMMON COMPONENTS
// ============================================================================

@Composable
private fun ModalDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}
