package com.runwear.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit

/**
 * Onboarding / welcome flow - two steps matching web PWA:
 * 1. Preferences (temperature unit, fit preference, body temperature comfort)
 * 2. Location (use current location or set manually)
 *
 * Presentation (WS-8, RC-8 — D-24): every surface is built from the
 * design token table in BOTH appearances. The pre-WS-8 presentation
 * hardcoded a dark-backdrop treatment — white text at fixed alphas
 * (0.55–0.75) over a gradient that faded into the THEME background —
 * which computed to ≈1.07–1.10:1 on the cream ground in light mode,
 * and the theme-ink headings computed to 1.91:1 over the gradient's
 * dark-teal top. The rebuilt presentation:
 * - the ground is the theme Background token, flat;
 * - each question lives in a card on the Surface token with a
 *   hairline in the secondary ink, carrying the SettingsSheet label
 *   hierarchy (title in TextPrimary, sublabel in TextSecondary);
 * - segmented controls are tracks on the Background token whose
 *   selected segment is the solid Primary pine with white text and
 *   whose unselected labels read TextSecondary — the same treatment
 *   as the Settings GenderToggle;
 * - no hardcoded white-at-alpha text exists anywhere in this file.
 *
 * Behavior is unchanged: same questions, same options, same defaults,
 * same toggle semantics, same persisted values, and all copy matches
 * the web PWA verbatim.
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
                    Spacer(Modifier.height(48.dp))

                    // RW-26: the RunWear script logo (genuine Great
                    // Vibes) — the brand signature, as on home.
                    Text(
                        text = "RunWear",
                        fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
                        fontSize = 52.sp,
                        color = RunWearColors.TextPrimary
                    )

                    Spacer(Modifier.height(22.dp))

                    // Title: "Welcome! 👋" (matches web PWA verbatim)
                    Text(
                        text = "Welcome!",
                        fontFamily = FontFamily.Default,
                        fontSize = 30.sp,
                        letterSpacing = 0.5.sp,
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

                    Spacer(Modifier.height(10.dp))

                    // Frame 28 welcome truth: the free tier, named once.
                    Text(
                        text = "Your first 5 looks are free.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    // Step indicator: the flow has exactly two steps; the
                    // dots only mirror showLocationStep, nothing new.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StepDot(active = !showLocationStep)
                        StepDot(active = showLocationStep)
                    }

                    Spacer(Modifier.height(30.dp))

                    if (!showLocationStep) {
                        // ========================================
                        // STEP 1: Preferences (web PWA copy verbatim)
                        // ========================================
                        PreferenceSection(
                            title = "Temperature",
                            sublabel = "How do you measure the weather?"
                        ) {
                            TokenSegmentedControl(
                                options = listOf("°F", "°C"),
                                selectedIndex = if (temperatureUnit == TemperatureUnit.FAHRENHEIT) 0 else 1,
                                onSelect = { index ->
                                    temperatureUnit = if (index == 0) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        PreferenceSection(
                            title = "Fit Preference",
                            sublabel = "We'll tailor product recommendations"
                        ) {
                            TokenSegmentedControl(
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

                        Spacer(Modifier.height(16.dp))

                        PreferenceSection(
                            title = "Do you get cold easily or overheat quickly?",
                            sublabel = "We'll adjust outfit recommendations"
                        ) {
                            TokenSegmentedControl(
                                options = listOf("Get cold", "Neither", "Overheat"),
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
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(RunWearColors.BgCard)
                                .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = RunWearColors.PineAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Set Your Location",
                            fontFamily = FontFamily.Default,
                            fontSize = 26.sp,
                            letterSpacing = 0.5.sp,
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

                        Spacer(Modifier.height(12.dp))

                        // Frame 30: the coded honest privacy copy
                        // lives IN the location step itself (the same
                        // PermissionCopy line the permission screen
                        // carries — one text, both surfaces).
                        Text(
                            text = com.runwear.app.ui.screens.PermissionCopy.LOCATION_PRIVACY_LINE,
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // Pinned actions over a fade-to-background scrim, so the
                // scroll content never fights the buttons.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    RunWearColors.Background.copy(alpha = 0.92f),
                                    RunWearColors.Background
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 20.dp)
                    ) {
                        if (!showLocationStep) {
                            // "Let's Go! 🏃" CTA (matches web PWA verbatim)
                            Button(
                                onClick = { showLocationStep = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RunWearColors.Primary
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    "Let's Go!",
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
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RunWearColors.Primary
                                ),
                                shape = RoundedCornerShape(18.dp)
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

                            Button(
                                onClick = { onComplete(temperatureUnit, genderPreference, comfortPreference) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RunWearColors.BgCard,
                                    contentColor = RunWearColors.TextPrimary
                                ),
                                border = BorderStroke(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f))
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
}

/** One step-indicator dot: a pine pill for the current step, a faint dot otherwise. */
@Composable
private fun StepDot(active: Boolean) {
    Box(
        modifier = Modifier
            .width(if (active) 20.dp else 7.dp)
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (active) RunWearColors.Primary else RunWearColors.TextSecondary.copy(alpha = 0.35f)
            )
    )
}

/**
 * Preference question block on the Surface token card — the
 * SettingsSheet label hierarchy: 16sp SemiBold label in TextPrimary,
 * 13sp sublabel in TextSecondary 2dp below, control 14dp below that.
 */
@Composable
private fun PreferenceSection(
    title: String,
    sublabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunWearColors.Surface)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary
        )
        sublabel?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(modifier = Modifier.padding(top = 14.dp)) {
            content()
        }
    }
}

/**
 * The one segmented control used by every onboarding question, on
 * tokens: a Background-token track with a secondary-ink hairline;
 * equal-width segments; the selected segment is the solid Primary
 * pine with white SemiBold text, unselected segments are transparent
 * with TextSecondary text (the Settings GenderToggle treatment).
 * Selection semantics are the caller's, unchanged.
 */
@Composable
private fun TokenSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunWearColors.Background)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp, vertical = 14.dp),
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
