package com.runwear.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.feedback.StartupTrace
import com.runwear.app.ui.components.BrandedLoadingScreen
import com.runwear.app.ui.components.HeroSection
import com.runwear.app.ui.components.loadingStatusText
import com.runwear.app.ui.components.OutfitCard
import com.runwear.app.ui.components.OutfitListShimmer
import com.runwear.app.ui.components.TipsSection
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Hero-image design MainScreen - PWA v2.9 aligned.
 *
 * Features:
 * - Full-bleed AI hero image (75vh)
 * - Temperature-reactive color tints
 * - Glass morphism controls (Location, Share, Settings)
 * - Combined Date/Time pill (frosted glass)
 * - Tappable weather pills with detail popups
 * - Shop button opens modal with all items → Amazon
 * - Staggered outfit card animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroMainScreen(
    uiState: MainUiState,
    locationSearchState: LocationSearchState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onSetComfortPreference: (com.runwear.shared.domain.model.ComfortPreference) -> Unit,
    onSetGenderPreference: (com.runwear.shared.domain.model.GenderPreference) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (Int) -> Unit,
    onResetToNow: () -> Unit,
    onLocationSearch: (String) -> Unit,
    onLocationSelect: (Double, Double, String) -> Unit,
    onUseCurrentLocation: () -> Unit = {},
    isUsingGPS: Boolean = false,
    analyticsOptIn: Boolean = false,
    onAnalyticsOptInChange: (Boolean) -> Unit = {},
    onOpenFeedback: (com.runwear.app.feedback.FeedbackCategory?) -> Unit = {},
    onFlagHero: (com.runwear.shared.domain.model.HeroFlagReason) -> Unit = {},
    /** Hero refresh: cycle to the cell's next approved image (no flag). */
    onCycleHero: () -> Unit = {},
    onItemTapped: (ClothingItem) -> Unit = {}
) {
    val context = LocalContext.current

    // Safe-boot diagnostic (1.1.3): the home destination composable was
    // entered — its body executed during composition. remember{} so the
    // mark fires once per composition instance, not per recomposition.
    remember {
        StartupTrace.mark(context.applicationContext, "home-composable-entered")
        Unit
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showShopAll by remember { mutableStateOf(false) }
    var weatherDetailType by remember { mutableStateOf<String?>(null) }

    // Hero-flag flow: the sheet, plus the URL already flagged in this
    // view (one flag per photo view — the chip goes inert for it).
    var showHeroFlagSheet by remember { mutableStateOf(false) }
    var flaggedHeroUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    // Grid state for the MEDIUM/EXPANDED outfit grids (hoisted beside
    // listState so a fold/unfold posture change doesn't reset scroll).
    val gridState = rememberLazyGridState()

    // Track content visibility for animations
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.outfit) {
        if (uiState.outfit != null) {
            contentVisible = true
        }
    }

    // Share function - matches web PWA shareOutfit verbatim
    fun shareOutfit() {
        val weather = uiState.weather ?: return
        val outfit = uiState.outfit ?: return
        val unit = if (weather.isCelsius) "°C" else "°F"
        val tempBracket = getTempBracket(weather.effectiveTemperature, weather.isCelsius)

        val itemsList = outfit.allItems.joinToString("\n") { "• ${it.displayName}" }
        val shareText = """
🏃 RunWear Recommendation

📍 ${uiState.locationName}
🌡️ Feels like ${weather.effectiveTemperature.toInt()}$unit ($tempBracket)
${weather.weatherCode.icon} ${weather.weatherCode.description}

What to wear:
$itemsList

${if (outfit.tips.isNotEmpty()) "💡 Tip: ${outfit.tips.first()}" else ""}

Get your personalized running outfit at runwear.ai
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share your outfit"))
    }

    // Adaptive layout breakpoints (Material3 WindowSizeClass-aligned),
    // fluid at every width per the PWA's fully-responsive standard:
    // COMPACT: phone portrait (< 600dp) — single column, unchanged
    // MEDIUM: foldable unfolded / tablet portrait (600-840dp) — full-bleed
    //   stacked layout, outfit cards flowing into a grid as width allows
    // EXPANDED: tablet landscape / desktop (> 840dp) — two-pane rail
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        val spec = fluidLayoutSpec(maxWidth)
        val layout = spec.layout

        when {
            // Content wins over the loading flag: once anything is on screen
            // (fresh fetch or the seeded last-good snapshot), a refresh
            // updates it IN PLACE — the full-screen shimmer is reserved for
            // the no-content first-ever load. Swapping the whole screen for
            // the skeleton on every refresh also unmounted HeroSection and
            // replayed its (intentional) count-up sweep on unchanged values.
            uiState.outfit != null && uiState.weather != null -> {
                val pullToRefreshState = rememberPullToRefreshState()

                val scrollOffset = remember {
                    derivedStateOf {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }

                val gridScrollOffset = remember {
                    derivedStateOf {
                        gridState.firstVisibleItemScrollOffset.toFloat()
                    }
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (layout) {
                        AdaptiveLayout.COMPACT -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 60.dp)
                            ) {
                                item {
                                    HeroSectionContent(
                                        uiState = uiState,
                                        contentVisible = contentVisible,
                                        scrollOffset = scrollOffset.value,
                                        onDateClick = { showDatePicker = true },
                                        onTimeClick = { showTimePicker = true },
                                        onNowClick = onResetToNow,
                                        onLocationClick = { showLocationPicker = true },
                                        onSettingsClick = { showSettings = true },
                                        onShareClick = { shareOutfit() },
                                        onTempClick = onToggleUnit,
                                        onWeatherPillClick = { type -> weatherDetailType = type },
                                        onFlagClick = { showHeroFlagSheet = true },
                                        heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                        onCycleHero = onCycleHero,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                outfitListItems(
                                    uiState = uiState,
                                    contentVisible = contentVisible,
                                    onShopAllClick = { showShopAll = true },
                                    onItemClick = { item ->
                                        onItemTapped(item)
                                        val url = buildAmazonSearchUrl(item, uiState.genderPreference)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) }
                                )
                            }
                        }
                        AdaptiveLayout.MEDIUM -> {
                            // Full-bleed stacked layout (foldable unfolded,
                            // tablet portrait): hero on top, outfit cards in
                            // an auto-fit grid below — the PWA's fluid
                            // stacked range. No width cap anywhere.
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = OUTFIT_GRID_MIN_CELL),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = spec.pagePadding,
                                    end = spec.pagePadding,
                                    bottom = 60.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(spec.gridGap),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HeroSectionContent(
                                        uiState = uiState,
                                        contentVisible = contentVisible,
                                        scrollOffset = gridScrollOffset.value,
                                        onDateClick = { showDatePicker = true },
                                        onTimeClick = { showTimePicker = true },
                                        onNowClick = onResetToNow,
                                        onLocationClick = { showLocationPicker = true },
                                        onSettingsClick = { showSettings = true },
                                        onShareClick = { shareOutfit() },
                                        onTempClick = onToggleUnit,
                                        onWeatherPillClick = { type -> weatherDetailType = type },
                                        onFlagClick = { showHeroFlagSheet = true },
                                        heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                        onCycleHero = onCycleHero,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                outfitGridItems(
                                    uiState = uiState,
                                    contentVisible = contentVisible,
                                    onShopAllClick = { showShopAll = true },
                                    onItemClick = { item ->
                                        onItemTapped(item)
                                        val url = buildAmazonSearchUrl(item, uiState.genderPreference)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) }
                                )
                            }
                        }
                        AdaptiveLayout.EXPANDED -> {
                            // Two-pane rail (the PWA's ≥900px composition):
                            // hero fills a full-height left pane of
                            // max(400dp, 42% of width); the outfit pane
                            // scrolls independently with cards in an
                            // auto-fit grid (tablet landscape, desktop,
                            // Duo spanned).
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left pane: Hero section (full height)
                                Box(
                                    modifier = Modifier
                                        .width(spec.heroPaneWidth ?: 400.dp)
                                        .fillMaxHeight()
                                ) {
                                    HeroSectionContent(
                                        uiState = uiState,
                                        contentVisible = contentVisible,
                                        scrollOffset = 0f,
                                        onDateClick = { showDatePicker = true },
                                        onTimeClick = { showTimePicker = true },
                                        onNowClick = onResetToNow,
                                        onLocationClick = { showLocationPicker = true },
                                        onSettingsClick = { showSettings = true },
                                        onShareClick = { shareOutfit() },
                                        onTempClick = onToggleUnit,
                                        onWeatherPillClick = { type -> weatherDetailType = type },
                                        onFlagClick = { showHeroFlagSheet = true },
                                        heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                        onCycleHero = onCycleHero,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Right pane: outfit grid (independently scrollable)
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = OUTFIT_GRID_MIN_CELL),
                                    state = gridState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(
                                        start = spec.pagePadding,
                                        end = spec.pagePadding,
                                        bottom = 60.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(spec.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    outfitGridItems(
                                        uiState = uiState,
                                        contentVisible = contentVisible,
                                        onShopAllClick = { showShopAll = true },
                                        onItemClick = { item ->
                                            onItemTapped(item)
                                            val url = buildAmazonSearchUrl(item, uiState.genderPreference)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        },
                                        onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // No content yet: the branded loading state while the first
            // load runs (the low-contrast shimmer read as a black screen),
            // the error screen only when a load actually failed with
            // nothing to show.
            uiState.isLoading -> BrandedLoadingScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
            uiState.error != null -> HeroErrorScreen(uiState.error, onRefresh)
            else -> BrandedLoadingScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
        }

        // Hero-flag confirmation ("Thanks — we'll review this photo").
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }

    // Date Picker
    if (showDatePicker) {
        DatePickerSheet(
            selectedDate = uiState.selectedDateTime.toLocalDate(),
            today = uiState.locationNow.toLocalDate(),
            onSelectDate = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time Picker
    if (showTimePicker) {
        TimePickerSheet(
            selectedHour = uiState.selectedDateTime.hour,
            onSelectTime = { hour ->
                onTimeSelected(hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // Settings
    if (showSettings) {
        SettingsSheet(
            temperatureUnit = uiState.temperatureUnit,
            comfortPreference = uiState.comfortPreference,
            genderPreference = uiState.genderPreference,
            onToggleUnit = onToggleUnit,
            onSetComfortPreference = onSetComfortPreference,
            onSetGenderPreference = onSetGenderPreference,
            analyticsOptIn = analyticsOptIn,
            onAnalyticsOptInChange = onAnalyticsOptInChange,
            onSendFeedback = {
                showSettings = false
                onOpenFeedback(null)
            },
            onDismiss = { showSettings = false }
        )
    }

    // Location Picker
    if (showLocationPicker) {
        LocationPickerSheet(
            currentLocation = uiState.locationName,
            onSearch = onLocationSearch,
            onSelectLocation = { lat, lon, name ->
                onLocationSelect(lat, lon, name)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
            searchResults = locationSearchState.results,
            isSearching = locationSearchState.isSearching,
            isUsingGPS = isUsingGPS,
            onUseCurrentLocation = {
                onUseCurrentLocation()
                showLocationPicker = false
            }
        )
    }

    // Shop All Modal (shows all outfit items)
    if (showShopAll && uiState.outfit != null && uiState.weather != null) {
        ShopAllSheet(
            items = uiState.outfit.allItems,
            genderPreference = uiState.genderPreference,
            weatherCode = uiState.weather.weatherCode.code,
            onDismiss = { showShopAll = false }
        )
    }

    // Weather Detail Popup
    weatherDetailType?.let { type ->
        uiState.weather?.let { weather ->
            WeatherDetailSheet(
                type = type,
                weather = weather,
                onDismiss = { weatherDetailType = null }
            )
        }
    }

    // Hero Flag Sheet — picking a reason sends the flag (fire-and-
    // forget in the ViewModel), marks this photo as flagged so the
    // chip goes inert, and confirms with a snackbar. The send itself
    // has no failure UI: a flag is best-effort feedback.
    if (showHeroFlagSheet) {
        HeroFlagSheet(
            onSelectReason = { reason ->
                showHeroFlagSheet = false
                flaggedHeroUrl = uiState.heroImageUrl
                onFlagHero(reason)
                screenScope.launch {
                    snackbarHostState.showSnackbar("Thanks — we'll review this photo")
                }
            },
            onDismiss = { showHeroFlagSheet = false }
        )
    }
}

// ============================================================================
// ADAPTIVE LAYOUT HELPERS
// ============================================================================

/**
 * Adaptive layout breakpoints aligned with Material3 WindowSizeClass.
 * BoxWithConstraints is used (no new dependencies) to handle foldables,
 * tablets, and desktop windows — recomposes automatically on posture changes.
 */
internal enum class AdaptiveLayout { COMPACT, MEDIUM, EXPANDED }

/** Minimum outfit-card cell width — the PWA grid's minmax(290px, 1fr). */
internal val OUTFIT_GRID_MIN_CELL: Dp = 290.dp

/**
 * The fluid layout resolved for one window width — the native mirror
 * of the PWA's fully-responsive standard (runwearpwa22 @ 4a26106):
 * no width cap at any size; gutters and grid gaps scale continuously
 * (the PWA's clamp(20px, 3vw, 44px) padding and clamp(10px, 1.2vw,
 * 16px) gap, with the 20dp floor keeping COMPACT exactly as it was);
 * the EXPANDED hero pane is the PWA rail's minmax(400px, 42%).
 */
internal data class FluidLayoutSpec(
    val layout: AdaptiveLayout,
    val pagePadding: Dp,
    val gridGap: Dp,
    /** EXPANDED only: the hero pane's fixed width. Null otherwise. */
    val heroPaneWidth: Dp?
)

internal fun fluidLayoutSpec(maxWidth: Dp): FluidLayoutSpec {
    val layout = when {
        maxWidth < 600.dp -> AdaptiveLayout.COMPACT
        maxWidth < 840.dp -> AdaptiveLayout.MEDIUM
        else -> AdaptiveLayout.EXPANDED
    }
    return FluidLayoutSpec(
        layout = layout,
        pagePadding = (maxWidth * 0.03f).coerceIn(20.dp, 44.dp),
        gridGap = (maxWidth * 0.012f).coerceIn(10.dp, 16.dp),
        heroPaneWidth = if (layout == AdaptiveLayout.EXPANDED) {
            maxOf(400.dp, maxWidth * 0.42f)
        } else {
            null
        }
    )
}

/**
 * Column count [GridCells.Adaptive] resolves for [contentWidth] — the
 * same auto-fit arithmetic as the PWA grid (minmax(290px, 1fr) plus
 * gap): a column appears only when a full extra cell + gap fits.
 * Exposed for the continuum unit tests; the grids themselves let
 * GridCells.Adaptive do this at layout time.
 */
internal fun outfitGridColumns(contentWidth: Dp, gap: Dp): Int {
    val cell = OUTFIT_GRID_MIN_CELL + gap
    return maxOf(1, ((contentWidth + gap) / cell).toInt())
}

/**
 * Hero section with entrance animation (shared across all adaptive layouts).
 */
@Composable
private fun HeroSectionContent(
    uiState: MainUiState,
    contentVisible: Boolean,
    scrollOffset: Float,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareClick: () -> Unit,
    onTempClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit,
    onFlagClick: () -> Unit,
    heroFlagEnabled: Boolean,
    onCycleHero: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing))
    ) {
        HeroSection(
            weather = uiState.weather,
            outfit = uiState.outfit,
            locationName = uiState.locationName,
            selectedDateTime = uiState.selectedDateTime,
            locationNow = uiState.locationNow,
            isNowSelected = uiState.isNowSelected,
            heroImageUrl = uiState.heroImageUrl,
            scrollOffset = scrollOffset,
            onDateClick = onDateClick,
            onTimeClick = onTimeClick,
            onNowClick = onNowClick,
            onLocationClick = onLocationClick,
            onSettingsClick = onSettingsClick,
            onShareClick = onShareClick,
            onTempClick = onTempClick,
            onWeatherPillClick = onWeatherPillClick,
            showFlagButton = uiState.heroImageUrl != null,
            flagEnabled = heroFlagEnabled,
            onFlagClick = onFlagClick,
            showRefreshButton = uiState.heroCandidates.size > 1,
            onRefreshHeroClick = onCycleHero,
            modifier = modifier
        )
    }
}

/**
 * "X Items for Your Run" + Shop button — one header for the list and
 * grid outfit presentations. [horizontalPadding] is the caller's own
 * gutter: the COMPACT list passes its fixed 20.dp; the grids pass 0
 * because their content padding already provides the gutter.
 */
@Composable
private fun OutfitSectionHeader(
    itemCount: Int,
    contentVisible: Boolean,
    horizontalPadding: Dp,
    onShopAllClick: () -> Unit
) {
    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(300, delayMillis = 200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                // "X Items for Your Run"
                Text(
                    text = "$itemCount Items for Your Run",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    color = RunWearColors.TextPrimary,
                    letterSpacing = 0.56.sp
                )

                // Shop button - opens shop modal
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = RunWearColors.PrimaryGlow,
                            spotColor = RunWearColors.PrimaryGlow
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    RunWearColors.Primary,
                                    RunWearColors.PrimaryLight
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onShopAllClick() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "Shop",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
}

/**
 * Outfit list items for a LazyColumn: section header, cards, tips, footer.
 * COMPACT's presentation, unchanged by the fluid pass (MEDIUM and
 * EXPANDED use [outfitGridItems]).
 */
private fun LazyListScope.outfitListItems(
    uiState: MainUiState,
    contentVisible: Boolean,
    onShopAllClick: () -> Unit,
    onItemClick: (ClothingItem) -> Unit,
    onFeedbackClick: () -> Unit
) {
    val outfit = uiState.outfit ?: return

    // Outfit Section Header
    item {
        OutfitSectionHeader(
            itemCount = outfit.allItems.size,
            contentVisible = contentVisible,
            horizontalPadding = 20.dp,
            onShopAllClick = onShopAllClick
        )
    }

    // Outfit Cards with AI-generated thumbnails
    // Tap opens Amazon directly (matches web PWA - no intermediate sheet)
    val items = outfit.allItems
    items(items.size) { index ->
        val item = items[index]
        OutfitCard(
            item = item,
            onClick = { onItemClick(item) },
            animationDelay = index * 50,
            gender = uiState.genderPreference,
            modifier = Modifier.padding(
                horizontal = 20.dp,
                vertical = 5.dp
            )
        )
    }

    // Pro Tip Section
    if (outfit.tips.isNotEmpty()) {
        item {
            TipsSection(
                tips = outfit.tips,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp)
            )
        }
    }

    item { Spacer(Modifier.height(16.dp)) }

    // Contextual feedback entry (spec §1.1): the moment the user is most
    // likely to disagree with the outfit is right here, looking at it.
    item {
        Text(
            text = "Not what you'd wear? Tell us",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = RunWearColors.Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFeedbackClick() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }

    // Version footer
    item {
        Text(
            text = "v3.12",
            style = MaterialTheme.typography.bodySmall,
            color = RunWearColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Outfit grid items for a LazyVerticalGrid: the same content as
 * [outfitListItems], composed for the fluid wide layouts — header,
 * tips, feedback and footer span all columns; the cards are grid
 * cells in the PWA's card presentation (centered name + description).
 * The grid's own content padding provides the horizontal gutter, so
 * nothing here pads horizontally.
 */
private fun LazyGridScope.outfitGridItems(
    uiState: MainUiState,
    contentVisible: Boolean,
    onShopAllClick: () -> Unit,
    onItemClick: (ClothingItem) -> Unit,
    onFeedbackClick: () -> Unit
) {
    val outfit = uiState.outfit ?: return

    // Outfit Section Header (spans the pane)
    item(span = { GridItemSpan(maxLineSpan) }) {
        OutfitSectionHeader(
            itemCount = outfit.allItems.size,
            contentVisible = contentVisible,
            horizontalPadding = 0.dp,
            onShopAllClick = onShopAllClick
        )
    }

    // Outfit Cards — grid cells, PWA presentation
    // Tap opens Amazon directly (matches web PWA - no intermediate sheet)
    val items = outfit.allItems
    items(items.size) { index ->
        val item = items[index]
        OutfitCard(
            item = item,
            onClick = { onItemClick(item) },
            animationDelay = index * 50,
            gender = uiState.genderPreference,
            centeredText = true
        )
    }

    // Pro Tip Section (spans the pane, as in the PWA rail)
    if (outfit.tips.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TipsSection(
                tips = outfit.tips,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }

    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }

    // Contextual feedback entry (same moment as the list presentation)
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = "Not what you'd wear? Tell us",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = RunWearColors.Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFeedbackClick() }
                .padding(vertical = 10.dp)
        )
    }

    // Version footer
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = "v3.12",
            style = MaterialTheme.typography.bodySmall,
            color = RunWearColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ============================================================================
// HERO FLAG SHEET
// ============================================================================

/**
 * "Flag this photo" — three reasons and Cancel, nothing else: no
 * account, no text field. Picking a reason reports the exact photo on
 * screen (pool row id, or its URL for a curated fallback) so it can be
 * reviewed and pulled from rotation if it's bad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroFlagSheet(
    onSelectReason: (com.runwear.shared.domain.model.HeroFlagReason) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Flag this photo",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            com.runwear.shared.domain.model.HeroFlagReason.entries.forEach { reason ->
                Text(
                    text = reason.label,
                    fontSize = 16.sp,
                    color = RunWearColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectReason(reason) }
                        .padding(vertical = 14.dp)
                )
            }
            Text(
                text = "Cancel",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 14.dp)
            )
        }
    }
}

// ============================================================================
// ERROR SCREEN
// ============================================================================

/**
 * Temperature bracket label (ported from web PWA getTempBracket).
 * Used in share text to describe conditions.
 */
private fun getTempBracket(temp: Double, isCelsius: Boolean): String {
    val f = if (isCelsius) temp * 9 / 5 + 32 else temp
    return when {
        f >= 80 -> "hot"
        f >= 65 -> "warm"
        f >= 50 -> "mild"
        f >= 35 -> "cool"
        f >= 20 -> "cold"
        else -> "freezing"
    }
}

@Composable
private fun HeroErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = RunWearColors.TempHot,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Oops!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyLarge,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
