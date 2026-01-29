package com.runwear.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit

/**
 * PWA v2.9 Onboarding Screen - Single welcome page with preference collection.
 *
 * Content:
 * - Logo: "Run**Wear**" (Wear in accent color)
 * - Title: "Welcome! 👋"
 * - Subtitle: "Let's prepare your perfect run. You can always change these later."
 *
 * Preferences collected:
 * 1. Temperature Unit (°F / °C) - Full-width toggle buttons
 * 2. Fit Preference (Male / Female) - Toggleable, neither selected by default
 * 3. Body Temperature (comfort slider) - 5 options
 */
@Composable
fun OnboardingScreen(
    onComplete: (TemperatureUnit, GenderPreference, ComfortPreference) -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local state for preferences
    var temperatureUnit by remember { mutableStateOf(TemperatureUnit.FAHRENHEIT) }
    var genderPreference by remember { mutableStateOf(GenderPreference.UNISEX) } // Neither selected
    var comfortPreference by remember { mutableStateOf(ComfortPreference.NEUTRAL) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RunWearColors.Background,
                        RunWearColors.Surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Logo: "Run**Wear**"
            Text(
                text = buildAnnotatedString {
                    append("Run")
                    withStyle(SpanStyle(color = RunWearColors.Primary)) {
                        append("Wear")
                    }
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // Title: "Welcome! 👋"
            Text(
                text = "Welcome! 👋",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Let's prepare your perfect run. You can always change these later.",
                fontSize = 16.sp,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(40.dp))

            // ========================================
            // 1. Temperature Unit Toggle
            // ========================================
            PreferenceSection(title = "Temperature Unit") {
                FullWidthToggle(
                    options = listOf("°F", "°C"),
                    selectedIndex = if (temperatureUnit == TemperatureUnit.FAHRENHEIT) 0 else 1,
                    onSelect = { index ->
                        temperatureUnit = if (index == 0) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ========================================
            // 2. Fit Preference (Male / Female)
            // ========================================
            PreferenceSection(title = "Fit Preference") {
                FullWidthToggle(
                    options = listOf("Male", "Female"),
                    selectedIndex = when (genderPreference) {
                        GenderPreference.MALE -> 0
                        GenderPreference.FEMALE -> 1
                        GenderPreference.UNISEX -> -1 // Neither selected
                    },
                    onSelect = { index ->
                        val newPref = if (index == 0) GenderPreference.MALE else GenderPreference.FEMALE
                        // Toggle behavior: if already selected, deselect
                        genderPreference = if (genderPreference == newPref) {
                            GenderPreference.UNISEX
                        } else {
                            newPref
                        }
                    },
                    allowDeselect = true
                )
            }

            Spacer(Modifier.height(24.dp))

            // ========================================
            // 3. Body Temperature (Comfort Slider)
            // ========================================
            PreferenceSection(title = "Body Temperature") {
                Text(
                    text = "How do you typically feel when running?",
                    fontSize = 14.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ComfortSelector(
                    selected = comfortPreference,
                    onSelect = { comfortPreference = it }
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            // ========================================
            // Action Buttons
            // ========================================
            Button(
                onClick = {
                    onRequestLocationPermission()
                    onComplete(temperatureUnit, genderPreference, comfortPreference)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunWearColors.Primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Enable Location",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onComplete(temperatureUnit, genderPreference, comfortPreference) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Set Location Manually",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreferenceSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

/**
 * Full-width toggle buttons for Temperature Unit and Fit Preference.
 * PWA spec: Full-width toggle buttons with equal sizing.
 */
@Composable
private fun FullWidthToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    allowDeselect: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(RunWearColors.BgCardLight) // #262626
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable {
                        if (allowDeselect && isSelected) {
                            // Toggle off - pass -1 to indicate deselection
                            onSelect(-1)
                        } else {
                            onSelect(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else RunWearColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Body Temperature / Comfort selector.
 * PWA spec: 5 options - "Run Cold" / "Slightly Cold" / "Neutral" / "Slightly Hot" / "Run Hot"
 */
@Composable
private fun ComfortSelector(
    selected: ComfortPreference,
    onSelect: (ComfortPreference) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunWearColors.BgCardLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ComfortPreference.entries.forEach { preference ->
            val isSelected = preference == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable { onSelect(preference) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preference.shortLabel,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else RunWearColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
