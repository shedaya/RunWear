package com.runwear.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit

/**
 * Onboarding / welcome flow - two steps matching web PWA:
 * 1. Preferences (temperature unit, fit preference, body temperature comfort)
 * 2. Location (use current location or set manually)
 *
 * Visual design matches the main app interface (HeroMainScreen / SettingsSheet):
 * - Solid RunWearColors.Background, centered column max 600dp wide on large
 *   screens (same adaptive treatment as the main screen's MEDIUM layout)
 * - Wordmark + titles in Bebas Neue (the app's display font, as used for the
 *   hero temperature and sheet titles like "SETTINGS")
 * - Preference sections use the SettingsSheet pattern: 15sp Medium label,
 *   12sp muted sublabel, control 12dp below, sections divided by
 *   HorizontalDivider(BgCardLight) with 16dp vertical padding
 * - All segmented controls share the SettingsSheet toggle spec: container
 *   RoundedCornerShape(10dp) on BgCardLight with 3dp padding / 2dp gap,
 *   segments RoundedCornerShape(8dp), selected = Primary teal fill
 * - Primary/secondary actions are pinned at the bottom of the screen:
 *   full-width 52dp buttons, RoundedCornerShape(16dp), Primary container
 *   (same treatment as the main screen's primary buttons)
 *
 * All copy matches the web PWA verbatim.
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
    // Two-step flow matching web PWA: preferences first, then location
    var showLocationStep by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        // Centered max-width column on large screens (main screen MEDIUM pattern)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxHeight()
            ) {
                // Scrollable content: header + current step's questions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(40.dp))

                    // Logo: "Run**Wear**" (Wear in accent color), Bebas Neue display font
                    Text(
                        text = buildAnnotatedString {
                            append("Run")
                            withStyle(SpanStyle(color = RunWearColors.Primary)) {
                                append("Wear")
                            }
                        },
                        fontFamily = BebasNeueFontFamily,
                        fontSize = 44.sp,
                        letterSpacing = 1.sp,
                        color = RunWearColors.TextPrimary
                    )

                    Spacer(Modifier.height(20.dp))

                    // Title: "Welcome! 👋" (matches web PWA verbatim)
                    Text(
                        text = "Welcome! 👋",
                        fontFamily = BebasNeueFontFamily,
                        fontSize = 28.sp,
                        letterSpacing = 0.48.sp,
                        color = RunWearColors.TextPrimary
                    )

                    Spacer(Modifier.height(8.dp))

                    // Subtitle (matches web PWA verbatim)
                    Text(
                        text = "Let's prepare your perfect run. You can always change these later.",
                        fontSize = 14.sp,
                        color = RunWearColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(Modifier.height(36.dp))

                    if (!showLocationStep) {
                        // ========================================
                        // STEP 1: Preferences (web PWA copy verbatim)
                        // Sections follow the SettingsSheet pattern
                        // ========================================
                        PreferenceSection(
                            title = "Temperature",
                            sublabel = "How do you measure the weather?"
                        ) {
                            WelcomeSegmentedControl(
                                options = listOf("°F", "°C"),
                                selectedIndex = if (temperatureUnit == TemperatureUnit.FAHRENHEIT) 0 else 1,
                                onSelect = { index ->
                                    temperatureUnit = if (index == 0) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                                }
                            )
                        }

                        HorizontalDivider(
                            color = RunWearColors.BgCardLight,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        PreferenceSection(
                            title = "Fit Preference",
                            sublabel = "We'll tailor product recommendations"
                        ) {
                            WelcomeSegmentedControl(
                                options = listOf("Male", "Female"),
                                selectedIndex = when (genderPreference) {
                                    GenderPreference.MALE -> 0
                                    GenderPreference.FEMALE -> 1
                                    GenderPreference.UNISEX -> -1 // Neither selected
                                },
                                onSelect = { index ->
                                    val tapped = if (index == 0) GenderPreference.MALE else GenderPreference.FEMALE
                                    // Toggle behavior (same as Settings GenderToggle):
                                    // tapping the selected option deselects it
                                    genderPreference = if (genderPreference == tapped) {
                                        GenderPreference.UNISEX
                                    } else {
                                        tapped
                                    }
                                }
                            )
                        }

                        HorizontalDivider(
                            color = RunWearColors.BgCardLight,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        PreferenceSection(
                            title = "Do you get cold easily or overheat quickly?",
                            sublabel = "We'll adjust outfit recommendations"
                        ) {
                            WelcomeSegmentedControl(
                                options = listOf("🥶 Get cold", "Neither", "Overheat 🥵"),
                                selectedIndex = when (comfortPreference) {
                                    ComfortPreference.RUNS_VERY_COLD,
                                    ComfortPreference.RUNS_COLD -> 0
                                    ComfortPreference.NEUTRAL -> 1
                                    ComfortPreference.RUNS_WARM,
                                    ComfortPreference.RUNS_VERY_WARM -> 2
                                },
                                onSelect = { index ->
                                    comfortPreference = when (index) {
                                        0 -> ComfortPreference.RUNS_COLD
                                        2 -> ComfortPreference.RUNS_WARM
                                        else -> ComfortPreference.NEUTRAL
                                    }
                                }
                            )
                        }
                    } else {
                        // ========================================
                        // STEP 2: Location (web PWA second step)
                        // ========================================
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = RunWearColors.Primary,
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Set Your Location",
                            fontFamily = BebasNeueFontFamily,
                            fontSize = 24.sp,
                            letterSpacing = 0.48.sp,
                            color = RunWearColors.TextPrimary
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "We need your location to get accurate weather for your run",
                            fontSize = 14.sp,
                            color = RunWearColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // Pinned actions: proper labeled buttons above the system bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 20.dp)
                ) {
                    if (!showLocationStep) {
                        // "Let's Go! 🏃" CTA (matches web PWA verbatim)
                        Button(
                            onClick = { showLocationStep = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RunWearColors.Primary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Let's Go! 🏃",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                onRequestLocationPermission()
                                onComplete(temperatureUnit, genderPreference, comfortPreference)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
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
                                "Use Current Location",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { onComplete(temperatureUnit, genderPreference, comfortPreference) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, RunWearColors.SurfaceElevated),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = RunWearColors.TextPrimary
                            )
                        ) {
                            Text(
                                "Set Location Manually",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preference question block, following the SettingsSheet section pattern:
 * 15sp Medium label, 12sp muted sublabel 2dp below, control 12dp below that.
 * Sections are separated by HorizontalDivider(BgCardLight) in the caller,
 * exactly as in SettingsSheet.
 */
@Composable
private fun PreferenceSection(
    title: String,
    sublabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
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
        Box(modifier = Modifier.padding(top = 12.dp)) {
            content()
        }
    }
}

/**
 * The one segmented control used by every onboarding question, built to the
 * SettingsSheet toggle spec (UnitToggle / GenderToggleSettings in
 * BottomSheets.kt): container RoundedCornerShape(10dp) on BgCardLight with
 * 3dp padding and 2dp gap; equal-width segments RoundedCornerShape(8dp)
 * with 12dp vertical padding; selected = Primary teal fill with white
 * 14sp SemiBold text, unselected = transparent with TextSecondary text.
 */
@Composable
private fun WelcomeSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RunWearColors.BgCardLight) // #262626
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else RunWearColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
