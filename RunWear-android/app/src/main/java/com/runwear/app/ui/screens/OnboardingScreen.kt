package com.runwear.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit

/**
 * Onboarding — the design's sequence (frames 28–31, notes §§8G/9),
 * rebuilt by WS-10 (A1, T-2/R-2, A2):
 *
 *   PROMISE (frame 28) → PREFERENCES (frame 29) → LOCATION
 *   (frames 30/31) → [host: first recommendation] → fork (32) →
 *   consent (33) → capture → first look (34).
 *
 * The steps are the pure [OnboardingSequence] model's in-screen
 * steps; copy is [OnboardingCopy] verbatim. A1: the free-looks
 * promise appears ONLY on the promise step — it is not in the
 * header, not on the setup form. A2: the promise step's headline
 * is Sol's pick, "Run, weather or not.", character for character.
 *
 * Motion (Part 1 §1.4): steps advance with the M3 shared-axis
 * horizontal slide + fade (300 ms, emphasized easing), Back the
 * exact reverse; preference selections fire the selection haptic
 * once per tap (§1.7). The system permission prompt itself is the
 * OS's own, unskinned.
 *
 * Presentation (WS-8, RC-8 — D-24): every surface is built from the
 * design token table in BOTH appearances — theme Background ground,
 * question cards on the Surface token, segmented controls whose
 * selected segment is the solid Primary pine; no hardcoded
 * white-at-alpha text anywhere in this file.
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
    // The current step — the sequence's in-screen steps only; the
    // fork/consent/capture/first-look steps are hosted downstream.
    var step by remember { mutableStateOf(OnboardingStep.PROMISE) }

    // System Back steps back through the sequence (§1.4: Back is
    // the exact reverse) — at the promise step it is the root and
    // Back keeps its system meaning.
    BackHandler(enabled = step != OnboardingStep.PROMISE) {
        OnboardingSequence.inScreenPrevious(step)?.let { step = it }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val easing = OnboardingMotion.EmphasizedEasing
                    val duration = OnboardingMotion.STEP_TRANSITION_MS
                    (slideInHorizontally(
                        animationSpec = tween(duration, easing = easing)
                    ) { width -> if (forward) width else -width } +
                        fadeIn(animationSpec = tween(duration, easing = easing)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(duration, easing = easing)
                            ) { width -> if (forward) -width else width } +
                                fadeOut(animationSpec = tween(duration, easing = easing))
                        )
                },
                label = "onboardingStep",
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxHeight()
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.PROMISE -> PromiseStep(
                        onGetStarted = { step = OnboardingStep.PREFERENCES }
                    )
                    OnboardingStep.PREFERENCES -> PreferencesStep(
                        temperatureUnit = temperatureUnit,
                        genderPreference = genderPreference,
                        comfortPreference = comfortPreference,
                        onTemperatureUnitChange = { temperatureUnit = it },
                        onGenderPreferenceChange = { genderPreference = it },
                        onComfortPreferenceChange = { comfortPreference = it },
                        onBack = { step = OnboardingStep.PROMISE },
                        onContinue = { step = OnboardingStep.LOCATION }
                    )
                    OnboardingStep.LOCATION -> LocationStep(
                        onBack = { step = OnboardingStep.PREFERENCES },
                        onUseMyLocation = {
                            onRequestLocationPermission()
                            onComplete(temperatureUnit, genderPreference, comfortPreference)
                        },
                        onSetCityInstead = {
                            onComplete(temperatureUnit, genderPreference, comfortPreference)
                        }
                    )
                    // Hosted downstream of this screen (fork, consent,
                    // capture, first look) — never presented here.
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Frame 28 — the standalone promise screen (T-2 ruling: adopted in
 * the Android sequence). Script logo, the headline (A2), one
 * promise per row, nothing to decide yet. The free-looks promise
 * (A1) lives on THIS screen only. No price appears (design §9).
 */
@Composable
private fun PromiseStep(onGetStarted: () -> Unit) {
    StepScaffold(
        actions = {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunWearColors.Primary
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    OnboardingCopy.PROMISE_CTA,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = OnboardingCopy.PROMISE_FOOTER,
                fontSize = 13.sp,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Spacer(Modifier.height(48.dp))

        // The RunWear script logo (genuine Great Vibes) — the brand
        // signature, as on home.
        Text(
            text = "RunWear",
            fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
            fontSize = 52.sp,
            color = RunWearColors.TextPrimary
        )

        Spacer(Modifier.height(22.dp))

        // A2: the welcome headline — Sol's pick, character for
        // character. Static text (Part 1 §1.4).
        Text(
            text = OnboardingCopy.HEADLINE,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(36.dp))

        // One promise per row (frame 28) — GEN-3 ROUND 2: the
        // name-led composition (feature name in pine bold, then
        // the plain phrase in regular weight).
        OnboardingCopy.WELCOME_POINTS.forEachIndexed { index, point ->
            if (index > 0) Spacer(Modifier.height(24.dp))
            PromiseRow(point = point)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One frame-28 promise row: the name-led headline — the feature
 * name in PINE BOLD leading, the plain phrase in regular weight
 * after it (GEN-3 ROUND 2, Sol's chosen composition).
 */
@Composable
private fun PromiseRow(point: OnboardingCopy.WelcomePoint) {
    // GEN-5 (RW-28 item 27): the name-led HEADLINE ("Name —
    // headline phrase", pine bold) on its own line, the body line
    // separate beneath it — the gen-4 build ran the name straight
    // into the body sentence (A-1).
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = point.headlineLine,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            color = RunWearColors.PineAccent,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = point.phrase,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = RunWearColors.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
    }
}

/**
 * Frame 29 — setup, step 1 of 2 (Android expression): the three
 * coded decisions as segmented controls on theme tokens. NO
 * free-looks promise, no Make It Me vocabulary — the feature has
 * not been introduced yet (A1).
 */
@Composable
private fun PreferencesStep(
    temperatureUnit: TemperatureUnit,
    genderPreference: GenderPreference,
    comfortPreference: ComfortPreference,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onGenderPreferenceChange: (GenderPreference) -> Unit,
    onComfortPreferenceChange: (ComfortPreference) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    StepScaffold(
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunWearColors.Primary
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    OnboardingCopy.SETUP_CTA,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    ) {
        Spacer(Modifier.height(16.dp))
        StepHeader(
            label = OnboardingCopy.SETUP_STEP_LABEL,
            title = OnboardingCopy.SETUP_TITLE,
            sub = OnboardingCopy.SETUP_SUB,
            onBack = onBack
        )
        // GEN-5 (RW-28 item 28): tightened so the Body temperature
        // card — the step's last card — arrives FULLY visible above
        // the pinned Continue, not cut off by it (A-2).
        Spacer(Modifier.height(16.dp))

        PreferenceSection(
            title = OnboardingCopy.TEMP_TITLE,
            sublabel = OnboardingCopy.TEMP_SUB
        ) {
            // Sol's onboarding ruling (Nov 4): full words, not symbols.
            TokenSegmentedControl(
                options = listOf(
                    OnboardingCopy.TEMP_OPTION_FAHRENHEIT,
                    OnboardingCopy.TEMP_OPTION_CELSIUS
                ),
                selectedIndex = if (temperatureUnit == TemperatureUnit.FAHRENHEIT) 0 else 1,
                onSelect = { index ->
                    onTemperatureUnitChange(
                        if (index == 0) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        PreferenceSection(
            title = OnboardingCopy.FIT_TITLE,
            sublabel = OnboardingCopy.FIT_SUB
        ) {
            // Sol's onboarding ruling (Nov 4): ALL THREE options are
            // explicit — "No preference" is a real choice (the design
            // frame's two-option version is not followed), replacing
            // the old toggle-to-deselect behaviour on this screen.
            TokenSegmentedControl(
                options = listOf(
                    OnboardingCopy.FIT_OPTION_MENS,
                    OnboardingCopy.FIT_OPTION_WOMENS,
                    OnboardingCopy.FIT_OPTION_NO_PREFERENCE
                ),
                selectedIndex = when (genderPreference) {
                    GenderPreference.MALE -> 0
                    GenderPreference.FEMALE -> 1
                    GenderPreference.UNISEX -> 2
                },
                onSelect = { index ->
                    onGenderPreferenceChange(
                        when (index) {
                            0 -> GenderPreference.MALE
                            1 -> GenderPreference.FEMALE
                            else -> GenderPreference.UNISEX
                        }
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        PreferenceSection(
            title = OnboardingCopy.BODY_TITLE,
            sublabel = OnboardingCopy.BODY_SUB
        ) {
            // Sol's onboarding ruling (Nov 4): the 5-stop slider with
            // named ends, replacing the 3-option segmented control
            // that collapsed the two end stops away.
            Column {
                // GEN-5 (C-1): the slider carries iOS's shared
                // ComfortLevelControl behaviour — a selection
                // haptic each time the STOP changes, and the tint
                // shifting blue → pine → orange across the stops
                // (comfortStopTint, the same policy as Settings).
                val sliderHaptic = LocalHapticFeedback.current
                var lastStopIndex by remember {
                    mutableStateOf(ComfortStops.indexOf(comfortPreference))
                }
                Slider(
                    value = ComfortStops.indexOf(comfortPreference).toFloat(),
                    onValueChange = { value ->
                        val stopIndex = value.roundToInt()
                        if (stopIndex != lastStopIndex) {
                            lastStopIndex = stopIndex
                            sliderHaptic.performHapticFeedback(HapticSpec.SELECTION)
                        }
                        onComfortPreferenceChange(ComfortStops.at(stopIndex))
                    },
                    valueRange = 0f..(ComfortStops.stops.size - 1).toFloat(),
                    steps = ComfortStops.stops.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = comfortStopTint(comfortPreference),
                        activeTrackColor = comfortStopTint(comfortPreference)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        OnboardingCopy.BODY_SLIDER_MIN_LABEL,
                        fontSize = 12.sp,
                        color = RunWearColors.TextSecondary
                    )
                    Text(
                        OnboardingCopy.BODY_SLIDER_MAX_LABEL,
                        fontSize = 12.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Frames 30/31 — the location step, step 2 of 2: the coded honest
 * privacy copy lives IN the step itself (the same PermissionCopy
 * line the permission screen carries — one text, both surfaces).
 * "Use My Location" fires the system prompt; "Set a City Instead"
 * is a full door, not a fallback.
 */
@Composable
private fun LocationStep(
    onBack: () -> Unit,
    onUseMyLocation: () -> Unit,
    onSetCityInstead: () -> Unit
) {
    StepScaffold(
        actions = {
            Button(
                onClick = onUseMyLocation,
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
                    OnboardingCopy.LOCATION_CTA_PRIMARY,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSetCityInstead,
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
                    OnboardingCopy.LOCATION_CTA_SECONDARY,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    ) {
        Spacer(Modifier.height(16.dp))
        StepHeader(
            label = OnboardingCopy.LOCATION_STEP_LABEL,
            title = OnboardingCopy.LOCATION_TITLE,
            sub = null,
            onBack = onBack
        )
        Spacer(Modifier.height(24.dp))

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
            text = OnboardingCopy.LOCATION_BODY,
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = PermissionCopy.LOCATION_PRIVACY_LINE,
            fontSize = 12.sp,
            color = RunWearColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The shared step layout: a scrollable content column (horizontally
 * centered children) over a pinned action bar on a fade-to-background
 * scrim, so scroll content never fights the buttons.
 */
@Composable
private fun StepScaffold(
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
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
                actions()
            }
        }
    }
}

/** A step's header row: Back chevron + step label, title, sublabel. */
@Composable
private fun StepHeader(
    label: String,
    title: String,
    sub: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = RunWearColors.TextSecondary
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextSecondary
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = title,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        color = RunWearColors.TextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
    sub?.let {
        Text(
            text = it,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = RunWearColors.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
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
 * Selection semantics are the caller's, unchanged. A selection fires
 * the §1.7 selection haptic — once per tap.
 */
@Composable
private fun TokenSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
                    .clickable {
                        haptic.performHapticFeedback(HapticSpec.SELECTION)
                        onSelect(index)
                    }
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
