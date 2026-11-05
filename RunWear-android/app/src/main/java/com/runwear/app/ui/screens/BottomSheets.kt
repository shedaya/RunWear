package com.runwear.app.ui.screens

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.components.conditionIcon
import com.runwear.app.ui.components.getCategoryIconRes
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.data.repository.LocationResult
import com.runwear.shared.domain.model.*
import com.runwear.shared.domain.service.ClothingThumbnailService
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ============================================================================
// AMAZON AFFILIATE - PWA v2.9 (Amazon ONLY)
// ============================================================================

private const val AMAZON_AFFILIATE_TAG = "runwearapp-20"
private const val AMAZON_PLATFORM_SUBTAG = "android"

internal fun buildAmazonSearchUrl(item: ClothingItem, gender: GenderPreference): String {
    val genderPrefix = when (gender) {
        GenderPreference.MALE -> "men's "
        GenderPreference.FEMALE -> "women's "
        GenderPreference.UNISEX -> ""
    }
    // Match web PWA buildAmazonLink: "premium " prefix, apostrophe gender forms, full encoding
    val fullTerm = "premium $genderPrefix${item.amazonSearchTerm}".trim().replace("  ", " ")
    val encoded = URLEncoder.encode(fullTerm, "UTF-8")
    return "https://www.amazon.com/s?k=$encoded&tag=$AMAZON_AFFILIATE_TAG&ascsubtag=$AMAZON_PLATFORM_SUBTAG"
}

// ============================================================================
// DATE PICKER SHEET - PWA v2.9 Style
// ============================================================================

/**
 * RW-26 (§8-I): the forecast-horizon rule for day browsing. The
 * forecast fetch covers [HORIZON_DAYS] days; days past it are NAMED
 * beyond-forecast and are not selectable — no weather is invented
 * for them (the Android equivalent of RW-23's iOS future-day flag).
 * Pure — unit-tested.
 */
object ForecastHorizonPolicy {
    const val HORIZON_DAYS = 7
    const val BROWSE_DAYS = 14
    const val BEYOND_LABEL = "Beyond forecast"

    fun isBeyondHorizon(dayOffset: Int): Boolean = dayOffset >= HORIZON_DAYS

    /** The cell's sublabel: the date inside the horizon, the name past it. */
    fun cellSublabel(dayOffset: Int, dateLabel: String): String =
        if (isBeyondHorizon(dayOffset)) BEYOND_LABEL else dateLabel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    selectedDate: LocalDate,
    /** "Today" in the SELECTED LOCATION's frame — the 7-day window starts there. */
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val days = (0 until ForecastHorizonPolicy.BROWSE_DAYS).map { today.plusDays(it.toLong()) }

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
                style = MaterialTheme.typography.titleLarge,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // Horizontal scroll row with two-line cells (matches web PWA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.forEach { date ->
                    val dayOffset = (date.toEpochDay() - today.toEpochDay()).toInt()
                    val beyondHorizon = ForecastHorizonPolicy.isBeyondHorizon(dayOffset)
                    val isSelected = date == selectedDate && !beyondHorizon
                    val label = when {
                        date == today -> "Today"
                        date == today.plusDays(1) -> "Tomorrow"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE"))
                    }
                    val sublabel = ForecastHorizonPolicy.cellSublabel(
                        dayOffset,
                        date.format(DateTimeFormatter.ofPattern("MMM d"))
                    )

                    Surface(
                        onClick = {
                            if (!beyondHorizon) {
                                // §1.7 (WS-9 fix, P-2): day selection
                                // fires the selection-class tick.
                                haptic.performHapticFeedback(HapticSpec.SELECTION)
                                onSelectDate(date)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) RunWearColors.Primary else RunWearColors.BgCardLight
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else RunWearColors.TextPrimary
                            )
                            Text(
                                text = sublabel,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else RunWearColors.TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Days past the 7-day forecast are beyond the forecast — no weather is invented for them.",
                fontSize = 12.sp,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================================
// TIME PICKER SHEET — GEN-4: moved to DayChartTimePicker.kt. The chart
// replaces the HourGrid there; the tile grid is retired.
// ============================================================================

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
    /** RW-25: appearance (System / Light / Dark); defaulted so other
     * call sites and tests don't churn. */
    appearancePreference: AppearancePreference = AppearancePreference.SYSTEM,
    onSetAppearancePreference: (AppearancePreference) -> Unit = {},
    analyticsOptIn: Boolean,
    onAnalyticsOptInChange: (Boolean) -> Unit,
    onSendFeedback: () -> Unit,
    makeItMeViewModel: com.runwear.app.makeitme.MakeItMeViewModel? = null,
    makeItMeState: com.runwear.app.makeitme.MakeItMeUiState =
        com.runwear.app.makeitme.MakeItMeUiState(),
    onMakeItMeOptInClick: () -> Unit = {},
    onMakeItMePhotosClick: () -> Unit = {},
    onMakeItMeSubscribeClick: () -> Unit = {},
    onMakeItMeManageSubscriptionClick: () -> Unit = {},
    onMakeItMeDeleted: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // WS-5 (D-13): one anchor — the sheet rests at full or hidden,
    // nothing between (skipPartiallyExpanded). Dismissal is the
    // sheet's own: swipe-down in one gesture or scrim tap, both
    // through onDismissRequest.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        SettingsSheetContent(
            temperatureUnit = temperatureUnit,
            comfortPreference = comfortPreference,
            genderPreference = genderPreference,
            onToggleUnit = onToggleUnit,
            onSetComfortPreference = onSetComfortPreference,
            onSetGenderPreference = onSetGenderPreference,
            appearancePreference = appearancePreference,
            onSetAppearancePreference = onSetAppearancePreference,
            analyticsOptIn = analyticsOptIn,
            onAnalyticsOptInChange = onAnalyticsOptInChange,
            onSendFeedback = onSendFeedback,
            makeItMeViewModel = makeItMeViewModel,
            makeItMeState = makeItMeState,
            onMakeItMeOptInClick = onMakeItMeOptInClick,
            onMakeItMePhotosClick = onMakeItMePhotosClick,
            onMakeItMeSubscribeClick = onMakeItMeSubscribeClick,
            onMakeItMeManageSubscriptionClick = onMakeItMeManageSubscriptionClick,
            onMakeItMeDeleted = onMakeItMeDeleted,
            // GEN-2 A1 (W-8): the content is WEIGHT-bounded inside
            // the sheet's own column (this call site is the
            // ModalBottomSheet content's ColumnScope) — see the
            // content's doc for the clipping mechanism this ends.
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * WS-5 (D-13 + D-14): the Settings sheet's content, separated from
 * the sheet wrapper so the inventory is render-testable.
 *
 * Sheet discipline: the content's ONE scroll consumer is this
 * LazyColumn, which the ModalBottomSheet owns through its nested
 * scroll connection. The D-13 ghosting mechanism — a plain Column
 * carrying its own verticalScroll nested inside the anchored sheet
 * (two competing consumers) — is gone by construction.
 *
 * Inventory discipline: the sections are [SettingsInventory]'s
 * registry, one item each, keyed by section id, in registry order.
 * Every section renders in every configuration (see the registry's
 * doc): the Make It Me section is composed unconditionally (it
 * carries the plain not-started row with the feature flag OFF),
 * Delete-my-data is its own section after it, and About closes the
 * sheet, matching iOS's inventory.
 *
 * GEN-2 A1 (W-8) — why About was clipped and could never scroll
 * into view: the sheet's column stacks the drag handle ABOVE this
 * content, but the LazyColumn below was measured against the
 * column's FULL height constraint (a Column measures unweighted
 * children at the full incoming max height, it does not subtract
 * its siblings). The list's viewport therefore extended past the
 * sheet's bottom edge by the handle (and any inset stacked with
 * it): at max scroll the list aligns its end — bottom padding
 * first, then About — with that off-screen viewport bottom, so
 * the last rows stay permanently below the visible edge no
 * matter how far the user scrolls (Sol's Nov 4 recording: the
 * "About" header as a half-height sliver, eight arrows drawn at
 * it). The fix is the Stage-1 sheet discipline's anchor rule
 * applied to the one scroll consumer: [SettingsSheet] passes a
 * weight-bounded [modifier], so the viewport is exactly the space
 * remaining in the sheet's bounded column and its bottom IS the
 * sheet's bottom. The bottom content padding (32 dp) is then
 * honest end-of-list space, fully reachable at max scroll.
 */
@Composable
fun SettingsSheetContent(
    temperatureUnit: TemperatureUnit,
    comfortPreference: ComfortPreference,
    genderPreference: GenderPreference,
    onToggleUnit: () -> Unit,
    onSetComfortPreference: (ComfortPreference) -> Unit,
    onSetGenderPreference: (GenderPreference) -> Unit,
    appearancePreference: AppearancePreference = AppearancePreference.SYSTEM,
    onSetAppearancePreference: (AppearancePreference) -> Unit = {},
    analyticsOptIn: Boolean,
    onAnalyticsOptInChange: (Boolean) -> Unit,
    onSendFeedback: () -> Unit,
    makeItMeViewModel: com.runwear.app.makeitme.MakeItMeViewModel? = null,
    makeItMeState: com.runwear.app.makeitme.MakeItMeUiState =
        com.runwear.app.makeitme.MakeItMeUiState(),
    onMakeItMeOptInClick: () -> Unit = {},
    onMakeItMePhotosClick: () -> Unit = {},
    onMakeItMeSubscribeClick: () -> Unit = {},
    onMakeItMeManageSubscriptionClick: () -> Unit = {},
    onMakeItMeDeleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sections = SettingsInventory.sectionsFor(
        makeItMeEnabled = com.runwear.app.makeitme.MakeItMeAvailability.ENABLED,
        makeItMeMode = makeItMeState.mode
    )
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp)
    ) {
        item(key = "settings-title") {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.titleLarge,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
        sections.forEach { section ->
            when (section) {
                SettingsSectionId.APPEARANCE -> item(key = section.name) {
                    // Appearance FIRST (RW-26 §H) — System follows the
                    // device; Light / Dark pin the app theme.
                    Column {
                        Text(
                            text = "Appearance",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = RunWearColors.TextPrimary
                        )
                        Text(
                            text = "Match your device, or always light or dark",
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        AppearanceSelector(
                            selected = appearancePreference,
                            onSelect = onSetAppearancePreference
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.TEMPERATURE_UNIT -> item(key = section.name) {
                    SettingRow(
                        label = "Temperature Unit",
                        sublabel = "Display temperature in"
                    ) {
                        UnitToggle(
                            isCelsius = temperatureUnit == TemperatureUnit.CELSIUS,
                            onToggle = { onToggleUnit() }
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.BODY_TEMPERATURE -> item(key = section.name) {
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
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        ComfortSelector(
                            selected = comfortPreference,
                            onSelect = onSetComfortPreference
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.FIT_PREFERENCE -> item(key = section.name) {
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
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        GenderToggleSettings(
                            selected = genderPreference,
                            onSelect = onSetGenderPreference
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.MAKE_IT_ME -> item(key = section.name) {
                    // RW-14 "Make It Me" section (spec §3) — between
                    // Fit Preference and Support. WS-5 (D-14):
                    // composed UNCONDITIONALLY — the section itself
                    // decides what its state shows (with the flag
                    // OFF, the plain not-started row of frame 36);
                    // it may never emit nothing.
                    com.runwear.app.makeitme.MakeItMeSettingsSection(
                        viewModel = makeItMeViewModel,
                        state = makeItMeState,
                        onOptInClick = onMakeItMeOptInClick,
                        onPhotosClick = onMakeItMePhotosClick,
                        onSubscribeClick = onMakeItMeSubscribeClick,
                        onManageSubscriptionClick = onMakeItMeManageSubscriptionClick,
                        onDeleted = onMakeItMeDeleted
                    )
                    SettingsDivider()
                }
                SettingsSectionId.DELETE_MY_DATA -> item(key = section.name) {
                    // WS-5 (D-14): Delete-my-data is its OWN section,
                    // reachable without the Make It Me section's
                    // emission — a stock-mode user still has an
                    // account to delete (RW-16.30 decision 5a).
                    com.runwear.app.makeitme.DeleteMyDataSettingsRow(
                        viewModel = makeItMeViewModel,
                        onDeleted = onMakeItMeDeleted
                    )
                    SettingsDivider()
                }
                SettingsSectionId.SEND_FEEDBACK -> item(key = section.name) {
                    // Send feedback — the durable route into the
                    // feedback system.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSendFeedback() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send feedback",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = RunWearColors.TextPrimary
                            )
                            Text(
                                text = "Tell us what happened, in your own words.",
                                fontSize = 12.sp,
                                color = RunWearColors.TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = RunWearColors.TextSecondary
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.HELP_IMPROVE -> item(key = section.name) {
                    // Help improve the app — the analytics opt-in
                    // (spec §4.3 copy). The toggle is OFF until the
                    // user turns it on; while it is off, nothing is
                    // counted and nothing is sent, crash reports
                    // included.
                    Column {
                        SettingRow(
                            label = "Help improve the app",
                            sublabel = "Count how the app is used"
                        ) {
                            Switch(
                                checked = analyticsOptIn,
                                onCheckedChange = onAnalyticsOptInChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RunWearColors.Primary,
                                    uncheckedThumbColor = RunWearColors.TextSecondary,
                                    uncheckedTrackColor = RunWearColors.BgCardLight
                                )
                            )
                        }
                        Text(
                            text = "We count how the app is used — which features get used and where " +
                                "recommendations miss — and nothing else. No account, no advertising ID, " +
                                "and your location never leaves the phone more precisely than the city " +
                                "name. Turn this off and nothing is counted or sent.",
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        if (!analyticsOptIn) {
                            Text(
                                text = "Off. Nothing is being counted.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = RunWearColors.TextPrimary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Text(
                            text = "If the app crashes, it can send us the technical details so we can " +
                                "fix it. This happens only after a crash, and only with your permission here.",
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    SettingsDivider()
                }
                SettingsSectionId.ABOUT -> item(key = section.name) {
                    // WS-5 (D-14): About — the section iOS's Settings
                    // has always carried (Version, in its
                    // "name (code)" form) and Android lacked.
                    Column {
                        Text(
                            text = "About",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = RunWearColors.TextPrimary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version",
                                fontSize = 14.sp,
                                color = RunWearColors.TextSecondary
                            )
                            Text(
                                text = SettingsInventory.versionLabel(
                                    com.runwear.app.BuildConfig.VERSION_NAME,
                                    com.runwear.app.BuildConfig.VERSION_CODE
                                ),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = RunWearColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = RunWearColors.BgCardLight, modifier = Modifier.padding(vertical = 16.dp))
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
                    color = RunWearColors.TextSecondary,
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
    // §1.7 (WS-9 fix, P-2): a Settings preference selection fires
    // the selection-class tick. Both cells below only invoke
    // onToggle when the tap actually changes the unit.
    val haptic = LocalHapticFeedback.current
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
                .clickable {
                    if (isCelsius) {
                        haptic.performHapticFeedback(HapticSpec.SELECTION)
                        onToggle()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "°F",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (!isCelsius) Color.White else RunWearColors.TextSecondary
            )
        }

        // °C option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCelsius) RunWearColors.Primary else Color.Transparent)
                .clickable {
                    if (!isCelsius) {
                        haptic.performHapticFeedback(HapticSpec.SELECTION)
                        onToggle()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "°C",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCelsius) Color.White else RunWearColors.TextSecondary
            )
        }
    }
}

/**
 * RW-25 Appearance Selector — the sheet's picker-row idiom (same
 * track + selected-fill treatment as [ComfortSelector]) with the
 * three appearance options.
 */
@Composable
private fun AppearanceSelector(
    selected: AppearancePreference,
    onSelect: (AppearancePreference) -> Unit
) {
    // §1.7 (WS-9 fix, P-2): preference selection tick, on change
    // (iOS fires its selection feedback on the value changing).
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RunWearColors.BgCardLight)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppearancePreference.entries.forEach { pref ->
            val isSelected = pref == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
                    .clickable {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticSpec.SELECTION)
                        }
                        onSelect(pref)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pref.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else RunWearColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
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
    // §1.7 (WS-9 fix, P-2): preference selection tick, on change.
    val haptic = LocalHapticFeedback.current
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
                    .clickable {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticSpec.SELECTION)
                        }
                        onSelect(pref)
                    }
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
    // §1.7 (WS-9 fix, P-2): preference selection tick — every tap
    // here changes the selection (select, or tap-again to deselect).
    val haptic = LocalHapticFeedback.current
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
                    haptic.performHapticFeedback(HapticSpec.SELECTION)
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
                    haptic.performHapticFeedback(HapticSpec.SELECTION)
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

/**
 * GEN-3 Y-15: the location picker's search-result rows, extracted
 * from [LocationPickerSheet] unchanged (same LazyColumn, same row
 * layout, same Surface onClick) so the tap-registration parity
 * check (LocationPickerTapRegistrationTest) can drive the REAL
 * row composable with real touch events — a ModalBottomSheet
 * cannot be driven to Expanded under Robolectric. The sheet wraps
 * each selection with its haptic + focus handling; this list owns
 * only the rows and their clicks.
 */
@Composable
internal fun LocationSearchResultRows(
    results: List<LocationResult>,
    onSelectResult: (LocationResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 200.dp)
    ) {
        items(results) { result ->
            Surface(
                onClick = { onSelectResult(result) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = RunWearColors.TextPrimary
                        )
                        if (result.detail.isNotEmpty()) {
                            Text(
                                text = result.detail,
                                fontSize = 13.sp,
                                color = RunWearColors.TextSecondary
                            )
                        }
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = RunWearColors.TextSecondary
                    )
                }
            }
            HorizontalDivider(color = RunWearColors.BgCardLight)
        }
    }
}

// ============================================================================
// LOCATION PICKER SHEET - PWA v2.9 Style with GPS + Manual options
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocationPickerSheet(
    currentLocation: String,
    onSearch: (String) -> Unit,
    onSelectLocation: (Double, Double, String) -> Unit,
    onDismiss: () -> Unit,
    searchResults: List<LocationResult> = emptyList(),
    isSearching: Boolean = false,
    isUsingGPS: Boolean = false,
    /**
     * GEN-3 X-13: the ACTUAL location-permission grant state,
     * read by the caller from the permission system (never derived
     * from [isUsingGPS] / the current place's source). The
     * "Current location" row resolves its subtitle from this —
     * see [LocationSheetState].
     */
    hasLocationPermission: Boolean = false,
    onUseCurrentLocation: () -> Unit = {},
    /** RW-26 Section E: actually-selected places, most-recent-first. */
    recents: List<RecentLocation> = emptyList(),
    onRemoveRecent: (RecentLocation) -> Unit = {},
    onUndoRemoveRecent: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    // RW-26: long-press raises the M3 contextual bar over a recent.
    var contextRecent by remember { mutableStateOf<RecentLocation?>(null) }
    val recentSnackbar = remember { SnackbarHostState() }
    val recentScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface,
        dragHandle = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            if (contextRecent != null) {
                // M3 contextual bar (Android removal treatment, frame 24).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RunWearColors.BgCard, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contextRecent!!.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = RunWearColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val removed = contextRecent!!
                        contextRecent = null
                        // §1.7 (WS-9 fix, P-2): recents removal
                        // fires the light tick.
                        haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                        onRemoveRecent(removed)
                        recentScope.launch {
                            val result = recentSnackbar.showSnackbar(
                                message = "Removed ${removed.name}",
                                actionLabel = "Undo"
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndoRemoveRecent()
                            }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remove ${contextRecent!!.name}",
                            tint = RunWearColors.TextPrimary
                        )
                    }
                    IconButton(onClick = { contextRecent = null }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Cancel",
                            tint = RunWearColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "SET LOCATION",
                style = MaterialTheme.typography.titleLarge,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search input FIRST (at top, visible above keyboard)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) onSearch(it)
                },
                placeholder = { Text("Enter city or ZIP code...") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = RunWearColors.PineAccent,
                            strokeWidth = 2.dp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RunWearColors.PineAccent,
                    unfocusedBorderColor = RunWearColors.BgCardLight
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                )
            )

            // Results IMMEDIATELY below search (visible above keyboard)
            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LocationSearchResultRows(
                    results = searchResults,
                    onSelectResult = { result ->
                        // §1.7 / §1.3 (WS-9 fix, P-2): place
                        // selected fires the selection tick.
                        haptic.performHapticFeedback(HapticSpec.SELECTION)
                        focusManager.clearFocus()
                        onSelectLocation(result.latitude, result.longitude, result.displayName)
                    }
                )
            } else if (query.length >= 2 && !isSearching) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No locations found",
                    fontSize = 14.sp,
                    color = RunWearColors.TextSecondary
                )
            }

            // Divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = RunWearColors.BgCardLight)
                Text(
                    text = "or",
                    fontSize = 13.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = RunWearColors.BgCardLight)
            }

            // Use Current Location Option (GPS)
            // GEN-3 X-13: the row's state comes from the ACTUAL
            // permission + whether the current place is the GPS
            // place — resolved in one seam (LocationSheetState) so
            // every sheet mode reads the same truth. Pre-fix the
            // subtitle was keyed on isUsingGPS alone, so selecting
            // a manual place flipped it to "Requires location
            // permission" with permission granted and in use.
            val currentRowStatus = LocationSheetState.currentLocationRowStatus(
                isUsingGPS = isUsingGPS,
                hasLocationPermission = hasLocationPermission
            )
            val isCurrentGpsPlace =
                currentRowStatus == LocationSheetState.CurrentLocationRowStatus.USING_GPS
            Surface(
                onClick = {
                    // §1.7 / §1.3 (WS-9 fix, P-2): choosing the
                    // current location is a place selection too.
                    haptic.performHapticFeedback(HapticSpec.SELECTION)
                    focusManager.clearFocus()
                    onUseCurrentLocation()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isCurrentGpsPlace) RunWearColors.CategoryTop.copy(alpha = 0.2f) else RunWearColors.BgCardLight,
                border = if (isCurrentGpsPlace) BorderStroke(2.dp, RunWearColors.Primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(RunWearColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Current location",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = RunWearColors.TextPrimary
                            )
                            if (isCurrentGpsPlace) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "CURRENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RunWearColors.PineAccent
                                )
                            }
                        }
                        Text(
                            text = LocationSheetState.currentLocationRowSubtitle(currentRowStatus),
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary
                        )
                    }
                    if (isCurrentGpsPlace) {
                        Box(
                            modifier = Modifier
                                .background(RunWearColors.CategoryTop.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            // WS-10 (A4 residue): the selection
                            // check is designed iconography — a
                            // vector, not a text glyph.
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = RunWearColors.PineAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // RW-26 Section E: RECENT LOCATIONS — section ABSENT on
            // first run, with a quiet hint card where it will live.
            Spacer(Modifier.height(16.dp))
            if (recents.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RunWearColors.BgCard
                ) {
                    Text(
                        text = "Places you pick will be kept here, so you can jump back to them.",
                        fontSize = 13.sp,
                        color = RunWearColors.TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = "RECENT LOCATIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                recents.forEach { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    // §1.7 / §1.3 (WS-9 fix, P-2):
                                    // place selected — selection tick.
                                    haptic.performHapticFeedback(HapticSpec.SELECTION)
                                    focusManager.clearFocus()
                                    onSelectLocation(recent.latitude, recent.longitude, recent.name)
                                },
                                // §1.7 (WS-9 fix, P-2): the ONE
                                // specified long-press haptic — the
                                // recents contextual bar.
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticSpec.LONG_PRESS)
                                    contextRecent = recent
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = RunWearColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = recent.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = RunWearColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (recent.name == currentLocation && !isUsingGPS) {
                            Text(
                                text = "CURRENT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RunWearColors.PineAccent
                            )
                        }
                    }
                    HorizontalDivider(color = RunWearColors.BgCardLight)
                }
            }

            SnackbarHost(hostState = recentSnackbar)

            // Currently using indicator
            // GEN-3 X-13: label and place name are ONE line of one
            // Text, and the pill renders only when the name seam
            // yields a name — a visible pill always carries its
            // place name. Pre-fix the name was a second line in
            // this non-scrollable column; with the IME open it fell
            // below the sheet's clip and the pill showed its label
            // with no name (f_105 vs the fully-named f_104).
            val currentlyUsingName = LocationSheetState.currentlyUsingName(currentLocation)
            if (currentlyUsingName != null && !isUsingGPS) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RunWearColors.Primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontSize = 12.sp,
                                    color = RunWearColors.TextSecondary
                                )
                            ) { append("Currently using: ") }
                            withStyle(
                                SpanStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RunWearColors.PineAccent
                                )
                            ) { append(currentlyUsingName) }
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// Note: Use com.runwear.shared.data.repository.LocationResult instead

// ============================================================================
// SHOP ALL SHEET - Shows all outfit items with Amazon links - PWA v2.9 Style
// ============================================================================

private fun buildShopAllUrl(weatherCode: Int, gender: GenderPreference): String {
    // Match web PWA shopAll(): weather-aware term through the same Amazon link builder
    val weatherDesc = getWeatherDescription(weatherCode)
    val genderPrefix = when (gender) {
        GenderPreference.MALE -> "men's "
        GenderPreference.FEMALE -> "women's "
        GenderPreference.UNISEX -> ""
    }
    val fullTerm = "premium $genderPrefix$weatherDesc weather running gear".trim().replace("  ", " ")
    val encoded = URLEncoder.encode(fullTerm, "UTF-8")
    return "https://www.amazon.com/s?k=$encoded&tag=$AMAZON_AFFILIATE_TAG&ascsubtag=$AMAZON_PLATFORM_SUBTAG"
}

/**
 * Weather description for search terms (ported from web PWA getWeatherDescription).
 */
private fun getWeatherDescription(code: Int): String {
    return when {
        code == 0 || code == 1 -> "sunny"
        code in 2..3 -> "cloudy"
        code in 45..48 -> "foggy"
        code in 51..67 -> "rainy"
        code in 71..77 -> "cold weather"
        code in 80..82 -> "rainy"
        code in 85..86 -> "snowy"
        code >= 95 -> "stormy"
        else -> "all weather"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopAllSheet(
    items: List<ClothingItem>,
    genderPreference: GenderPreference,
    weatherCode: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            // Header with title and Shop All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SHOP YOUR OUTFIT",
                    style = MaterialTheme.typography.titleLarge,
                    color = RunWearColors.TextPrimary
                )

                // Shop All button - PWA style
                Surface(
                    onClick = {
                        val url = buildShopAllUrl(weatherCode, genderPreference)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = RunWearColors.Primary
                ) {
                    Text(
                        text = "Shop All →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Items list
            items.forEach { item ->
                ShopItemRow(
                    item = item,
                    gender = genderPreference,
                    onClick = {
                        val url = buildAmazonSearchUrl(item, genderPreference)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }

            // FTC Disclosure (web PWA wording verbatim)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(
                        RunWearColors.BgCard,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Affiliate Disclosure: ")
                        }
                        append("As an Amazon Associate, RunWear earns from qualifying purchases. When you click these links, we may earn a small commission at no extra cost to you.")
                    },
                    fontSize = 11.sp,
                    color = RunWearColors.TextSecondary,
                    lineHeight = 16.sp
                )
            }

            // Close button (matches web PWA explicit Close)
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Close",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShopItemRow(
    item: ClothingItem,
    gender: GenderPreference,
    onClick: () -> Unit
) {
    val categoryColor = getCategoryColor(item.category)
    val context = LocalContext.current
    // Bundled asset first; remote URL is the fallback only (see OutfitCard).
    val thumbnailAssetUri = remember(item, gender) {
        ClothingThumbnailService.getThumbnailAssetUri(item, gender)
    }
    val thumbnailUrl = remember(item, gender) {
        ClothingThumbnailService.getThumbnailUrl(item, gender)
    }
    var assetFailed by remember(item, gender) { mutableStateOf(false) }
    val thumbnailModel =
        if (thumbnailAssetUri != null && !assetFailed) thumbnailAssetUri else thumbnailUrl
    var thumbnailLoaded by remember { mutableStateOf(false) }

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
            // Icon box: SVG category icon base + AI thumbnail overlay (matches web PWA)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        categoryColor.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailLoaded) {
                    Icon(
                        painter = painterResource(id = getCategoryIconRes(item.category)),
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (thumbnailModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailModel)
                            .crossfade(300)
                            .build(),
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer { alpha = if (thumbnailLoaded) 1f else 0f },
                        onSuccess = { thumbnailLoaded = true },
                        onError = {
                            if (!assetFailed && thumbnailAssetUri != null) {
                                assetFailed = true // retry once from the remote URL
                            } else {
                                thumbnailLoaded = false
                            }
                        }
                    )
                }
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
                tint = RunWearColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun getCategoryColor(category: ClothingCategory): Color {
    return when (category) {
        ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
        ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
        ClothingCategory.HEAD -> RunWearColors.CategoryHead
        ClothingCategory.HANDS -> RunWearColors.CategoryHands
        ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
    }
}

private fun getCategoryDisplayName(category: ClothingCategory): String {
    return when (category) {
        ClothingCategory.TOP_BASE -> "Base Layer"
        ClothingCategory.TOP_MID -> "Mid Layer"
        ClothingCategory.TOP_OUTER -> "Outer Layer"
        ClothingCategory.BOTTOM -> "Bottom"
        ClothingCategory.HEAD -> "Headwear"
        ClothingCategory.HANDS -> "Gloves"
        ClothingCategory.ACCESSORIES -> "Accessories"
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
    onDismiss: () -> Unit,
    /** WS-2: the render-time unit projection for every figure in the sheet. */
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                "condition" -> WeatherConditionDetail(weather, temperatureUnit)
                "wind" -> WindDetail(weather, temperatureUnit)
                "humidity" -> HumidityDetail(weather)
                "precipitation" -> PrecipitationDetail(weather, temperatureUnit)
                "uv" -> UVDetail(weather)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WeatherConditionDetail(weather: WeatherConditions, unit: TemperatureUnit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // WS-10 (A4 residue): the condition's designed vector
            // icon at the detail rows' own size (cf. WindDetail's
            // 40 dp Air icon) — not the WeatherCode emoji at 40sp.
            Icon(
                imageVector = weather.weatherCode.conditionIcon(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.PineAccent
            )
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
        DetailRow("Temperature", unit.formatTemperature(weather.temperature))
        DetailRow("Feels Like", unit.formatTemperature(weather.feelsLike))
    }
}

@Composable
private fun WindDetail(weather: WeatherConditions, unit: TemperatureUnit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Air,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.PineAccent
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
        DetailRow("Speed", unit.formatWind(weather.windSpeed))
        DetailRow("Gusts", unit.formatWind(weather.windGusts))
        DetailRow("Direction", "${windDirectionLabel(weather.windDirection)} (${weather.windDirection}°)")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "A headwind cools you faster — layer up on the outbound leg. A tailwind means you'll warm up quicker on the return.",
            fontSize = 14.sp,
            color = RunWearColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

/**
 * Convert wind direction degrees to 16-point compass label.
 */
private fun windDirectionLabel(degrees: Int): String {
    val directions = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )
    val index = (((degrees % 360) + 11.25) / 22.5).toInt() % 16
    return directions[index]
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
                tint = RunWearColors.PineAccent
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
private fun PrecipitationDetail(weather: WeatherConditions, unit: TemperatureUnit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.WaterDrop,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.PineAccent
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
        // Match web: show "None" when 0, use in/mm by unit (Open-Meteo returns mm)
        val amountText = when {
            weather.precipitation <= 0 -> "None"
            unit == TemperatureUnit.CELSIUS -> "${weather.precipitation} mm"
            else -> "${String.format("%.2f", weather.precipitation / 25.4)} in"
        }
        DetailRow("Expected Amount", amountText)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                weather.precipitationProbability > 70 ->
                    "High chance of rain — a water-resistant jacket is worth it. Trails will be slick; road running is the safer call."
                weather.precipitationProbability > 40 ->
                    "Decent chance of rain. A light rain shell packs small and saves the run if skies open up."
                else ->
                    "Low chance of rain, but conditions can shift. A packable shell is cheap insurance."
            },
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
    val protection = when {
        weather.uvIndex >= 6 -> "Sunscreen, sunglasses, hat"
        weather.uvIndex >= 3 -> "Sunglasses recommended"
        else -> "No protection needed"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.WbSunny,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = RunWearColors.PineAccent
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
        DetailRow("Protection", protection)
        DetailRow("Peak Hours", "10am – 4pm")
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (weather.uvIndex >= 6)
                "High UV! Wear sunscreen, sunglasses, and a hat. Avoid peak hours (10am-4pm) or run early."
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
                    RunWearColors.TextSecondary.copy(alpha = 0.4f),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}
