package com.runwear.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.feedback.StartupTrace
import com.runwear.app.ui.components.BrandedLoadingScreen
import com.runwear.app.ui.components.HomeSkeletonScreen
import com.runwear.app.ui.components.HeroSection
import com.runwear.app.ui.components.HeroSheetBlock
import com.runwear.app.ui.components.HeroSheetOverlap
import com.runwear.app.ui.components.heroPhotoHeight
import com.runwear.app.ui.components.loadingStatusText
import com.runwear.app.ui.components.OutfitCard
import com.runwear.app.ui.components.OutfitListShimmer
import com.runwear.app.ui.components.TipsSection
import com.runwear.app.ui.theme.RunWearColors
import androidx.compose.runtime.snapshotFlow
import com.runwear.app.weariverun.WearIveRunBarState
import com.runwear.app.weariverun.WearIveRunBarStateHolder
import com.runwear.app.weariverun.WearIveRunElementEvent
import com.runwear.app.weariverun.WearIveRunElementMachine
import com.runwear.app.weariverun.WearIveRunCollectionScreen
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunMiniBar
import com.runwear.app.weariverun.WearIveRunStore
import com.runwear.app.weariverun.WearTransitionDriver
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
    /** RW-25: appearance preference + setter for the Settings sheet. */
    appearancePreference: com.runwear.shared.domain.model.AppearancePreference =
        com.runwear.shared.domain.model.AppearancePreference.SYSTEM,
    onSetAppearancePreference: (com.runwear.shared.domain.model.AppearancePreference) -> Unit = {},
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (Int) -> Unit,
    onResetToNow: () -> Unit,
    onLocationSearch: (String) -> Unit,
    onLocationSelect: (Double, Double, String) -> Unit,
    onUseCurrentLocation: () -> Unit = {},
    isUsingGPS: Boolean = false,
    /** GEN-3 X-13: the ACTUAL location-permission grant state, for the Set Location sheet's Current location row. */
    hasLocationPermission: Boolean = false,
    analyticsOptIn: Boolean = false,
    onAnalyticsOptInChange: (Boolean) -> Unit = {},
    onOpenFeedback: (com.runwear.app.feedback.FeedbackCategory?) -> Unit = {},
    /** GEN-6 WAVE C (RW-56): the Settings "Rate RunWear" row's door. */
    onRateRunWear: () -> Unit = {},
    onFlagHero: (com.runwear.shared.domain.model.HeroFlagReason) -> Unit = {},
    /**
     * RW-16.27: flag the PERSONAL hero on screen ("This doesn't look
     * like me") — routes to the backend's flag_hero action, not the
     * pool insert.
     */
    onFlagPersonalHero: (com.runwear.app.makeitme.PersonalHeroFlagReason) -> Unit = {},
    /** Hero refresh: cycle to the cell's next approved image (no flag). */
    onCycleHero: () -> Unit = {},
    onItemTapped: (ClothingItem) -> Unit = {},
    // ── RW-14 "Make It Me" ─────────────────────────────────────────────
    makeItMeViewModel: com.runwear.app.makeitme.MakeItMeViewModel? = null,
    makeItMeState: com.runwear.app.makeitme.MakeItMeUiState =
        com.runwear.app.makeitme.MakeItMeUiState(),
    /** The current recommendation's personal combination (id + item keys). */
    onPersonalCombinationRequest: () -> Pair<String, List<String>>? = { null },
    /** Re-run hero resolution after a personal-layer change. */
    onPersonalLayerChanged: () -> Unit = {},
    /**
     * GEN-3 A-HERO (WS-B, X-1): the hosted upload flow finished a
     * FIRST look (not a replace) — the host presents the designed
     * first-look moment, whose making/ready/failed presentation is
     * driven by the look status, not by the flow's own completion.
     */
    onFirstLookRequested: () -> Unit = {},
    onDismissMakeItMeUpsell: () -> Unit = {},
    onRetryPersonalLook: () -> Unit = {},
    /**
     * WS-1 (D-03): the Scaffold's content padding from MainActivity.
     * Consumed ONCE, here, by the screen's ONE scroll container: its
     * bottom padding lands in the container's contentPadding (so the
     * last rows clear the navigation bar). The TOP inset is
     * deliberately NOT applied to the root — the photo zone is
     * full-bleed behind the status bar by design, and its chrome
     * consumes the top inset itself via statusBarsPadding() at
     * exactly one point inside the photo zone. Before WS-1 this
     * screen had no inset parameter at all: the Scaffold inset was
     * applied per-destination to Onboarding/Permission and simply
     * dropped here, which is the mechanism that left the (then
     * mis-placed) sheet's eyebrow clipped under the status bar.
     */
    scaffoldContentPadding: PaddingValues = PaddingValues(0.dp),
    /** WS-1: the root modifier, applied once to the screen's root
     * container — no destination-internal node re-applies it. */
    modifier: Modifier = Modifier
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

    // RW-14 surfaces hosted at this level (opened from Settings, the
    // upsell card, or the photos view).
    var showMakeItMeFlow by remember { mutableStateOf(false) }
    var makeItMeFlowReplace by remember { mutableStateOf(false) }
    // GEN-5 (item 21): which door the flow is entered through —
    // the Your Photos doors pre-open their named intake door.
    var makeItMeFlowEntryDoor by remember {
        mutableStateOf(com.runwear.app.makeitme.MakeItMeEntryDoor.STANDARD)
    }
    var showMakeItMePhotos by remember { mutableStateOf(false) }
    // GEN-6 WAVE C (RW-61): the "Your looks" library's host state.
    var showMakeItMeLibrary by remember { mutableStateOf(false) }
    var showMakeItMePaywall by remember { mutableStateOf(false) }

    // GEN-3 A-HERO (WS-A(2)): THE hero pill — one content value,
    // resolved once from the look/serve/entitlement state and handed
    // to every layout's hero (see HeroPillPolicy for the states).
    val heroPill = com.runwear.app.ui.components.HeroPillPolicy.resolve(
        lookStatus = uiState.personalLookStatus,
        serveKind = uiState.heroServeKind,
        heroIsPersonal = uiState.heroIsPersonal,
        mode = makeItMeState.mode,
        freeGenerationsUsed = makeItMeState.freeGenerationsUsed
    )

    // GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4): the
    // home-sheet band while a subscribed user's bank converges —
    // resolved once here, seated below the outfit items in every
    // layout (mock frame 19's seat). Null unless convergence is
    // genuinely in progress with real counts.
    val convergenceBand = com.runwear.app.makeitme.ConvergenceBandPolicy.content(
        mode = makeItMeState.mode,
        coverage = makeItMeState.bankCoverage,
        inTrial = makeItMeState.inTrial
    )

    // GEN-4 DAY CHART: the selected day's model, built from the
    // hourly forecast already in state (the retired HourGrid's own
    // series) in the RW-23 comfort-adjusted frame. One model serves
    // the home glance band and the Choose-a-Time sheet.
    val dayChartModel = remember(
        uiState.hourlyForecast, uiState.selectedDateTime, uiState.comfortPreference,
        uiState.sunTimes
    ) {
        com.runwear.app.ui.components.DayChartBuilder.build(
            hourly = uiState.hourlyForecast,
            date = uiState.selectedDateTime.toLocalDate(),
            comfort = uiState.comfortPreference,
            sunTimes = uiState.sunTimes
        )
    }
    val dayChartBandContent: @Composable () -> Unit = {
        com.runwear.app.ui.components.DayChartGlanceBand(
            model = dayChartModel,
            selectedHour = uiState.selectedDateTime.hour,
            locationToday = uiState.locationNow.toLocalDate(),
            nowHour = uiState.locationNow.hour,
            temperatureUnit = uiState.temperatureUnit,
            onChangeClick = { showTimePicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }

    // Hero-flag flow: the sheet, plus the URL already flagged in this
    // view (one flag per photo view — the chip goes inert for it).
    var showHeroFlagSheet by remember { mutableStateOf(false) }
    // RW-16.27: the personal hero's own sheet (its menu is headed
    // "This doesn't look like me"); the same chip opens it while a
    // personal photo is on screen.
    var showPersonalFlagSheet by remember { mutableStateOf(false) }
    var flaggedHeroUrl by remember { mutableStateOf<String?>(null) }
    val onAnyFlagClick: () -> Unit = {
        if (uiState.heroIsPersonal) showPersonalFlagSheet = true else showHeroFlagSheet = true
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()

    // RW-26 / WS-3 (RC-3): "Wear I've Run" — the collection is built.
    // GEN-3 (WS-C): the store persists ONE entry per SESSION (see
    // WearIveRunSession for the operational definition), keyed by
    // session and updated in place through every location/weather
    // change — the saved entry is the session's last settled
    // version. The holder — the element's ONE state machine
    // (WearIveRunElementMachine) — derives the element's state from
    // them. Entries are created automatically when a generation for
    // TODAY settles (the outfit + weather on screen), never backfilled.
    val wearIveRunStore = remember { WearIveRunStore(context.applicationContext) }
    val wearIveRunHolder = remember { WearIveRunBarStateHolder() }
    val wearIveRunState by wearIveRunHolder.state.collectAsState()
    val wearCueSequence by wearIveRunHolder.cueSequence.collectAsState()
    val wearIveRunEntries by wearIveRunStore.entriesFlow.collectAsState(initial = emptyList())
    var showWearIveRunCollection by remember { mutableStateOf(false) }

    // GEN-5 (RW-28, B-1): the FIRST emission in this process is
    // the stored collection restoring — it primes the holder at
    // rest (no cue, no announcement for entries an earlier process
    // settled). Later emissions are live landings.
    //
    // GEN-6 (DA-09 / RW-42): the holder is driven from the store
    // flow's OWN emissions, collected here — never from the
    // collectAsState state above. That state's initial value
    // (emptyList) reached the gen-5 prime FIRST, so the prime was
    // spent on an empty list and the store's real first emission —
    // the restored collection — fell into onEntriesChanged as a
    // "first presentation", re-firing the creation cue and the
    // "Added …" announcement for an entry an earlier process had
    // settled (the duplicate "Added Miami, FL, Now" on relaunch in
    // Sol's gen-5 recording). A flow collect's first emission IS
    // the store's first value: it primes; later ones are live.
    val wearCurrentUiState = androidx.compose.runtime.rememberUpdatedState(uiState)
    LaunchedEffect(Unit) {
        var primed = false
        wearIveRunStore.entriesFlow.collect { entries ->
            val today = wearCurrentUiState.value.locationNow.toLocalDate()
            if (!primed) {
                primed = true
                wearIveRunHolder.primeRestore(entries, today)
            } else {
                wearIveRunHolder.onEntriesChanged(entries, today)
            }
        }
    }

    // Day rollover: the local day at the presented place changing is
    // a machine driver — the element goes ABSENT until the new day's
    // first generation lands.
    val wearToday = uiState.locationNow.toLocalDate()
    LaunchedEffect(wearToday) {
        wearIveRunHolder.onDayRollover(wearToday)
    }

    // Record the day's settled version at generation time. NOTE
    // (WS-3, abolished drivers): this effect RECORDS the entry; it
    // does not drive the element. The holder turns a recording into a
    // cue only when it is a genuinely new settled version (creation,
    // or a kit change) — a re-recording with the same kit (a weather
    // refresh, a hero URL landing, a location re-render) updates the
    // entry's content silently and never moves the element.
    LaunchedEffect(
        uiState.outfit, uiState.weather, uiState.locationName,
        uiState.effectiveHeroUrl, uiState.isNowSelected, uiState.selectedDateTime
    ) {
        val outfit = uiState.outfit
        val weather = uiState.weather
        // GEN-5 (RW-28, census item 9): a CONFIRMED changed moment
        // also settles the session's entry — the Wear card
        // re-solves (feels-like, count, thumbnail) with the rest of
        // home and the narration names the moment that changed.
        // uiState only carries CONFIRMED moments (the picker's
        // draft never reaches this state), so recording here can
        // never be moved by mere browsing of the picker.
        // GEN-6 (DA-09 / RW-42): record only the selection's OWN
        // solve — inside the window between a confirmed selection
        // and its fetch committing, the weather/outfit in state
        // still belong to the previous moment, and recording them
        // under the new moment's label is how the bar came to
        // announce an update its card did not carry.
        if (outfit != null && weather != null &&
            com.runwear.app.weariverun.WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = uiState.isNowSelected,
                weatherMoment = weather.dateTime,
                selectedMoment = uiState.selectedDateTime
            )
        ) {
            wearIveRunStore.recordSettled(
                WearIveRunDayEntry(
                    // GEN-3 (WS-C): stamped with THIS process
                    // session's id and the settle instant — the
                    // session key replaces the entry in place on
                    // every later change within the session.
                    sessionId = com.runwear.app.weariverun.WearIveRunSession.currentId(),
                    settledAtMillis = System.currentTimeMillis(),
                    date = uiState.locationNow.toLocalDate(),
                    // WS-4 (content seam): the entry's place is the
                    // SIMPLE form from the place model, so the
                    // collection, its chapter covers, and the narration
                    // can never carry a raw geocode string. (WS-3:
                    // this recording is content, not a state driver —
                    // see the abolished-drivers note above.)
                    place = uiState.simplePlace?.display
                        ?: com.runwear.shared.domain.model.SimplePlace
                            .fromRawDisplayString(uiState.locationName).display,
                    // GEN-5: the entry's moment — null at Now, else
                    // the confirmed moment's simple clock reading
                    // ("9 p.m."), the C-7 time payload.
                    momentLabel = if (uiState.isNowSelected) {
                        null
                    } else {
                        com.runwear.app.weariverun.WearMomentLabel.of(
                            uiState.selectedDateTime
                        )
                    },
                    // GEN-5 (RW-28 item 11): the entry records the
                    // RW-23 comfort-adjusted feels-like — the same
                    // number the hero numeral shows for this moment
                    // (B-3: the card kept the raw 78° while the
                    // picked hour read 83°).
                    feelsLikeF = weather.feelsLikeInFahrenheit +
                        uiState.comfortPreference.tempAdjustment,
                    conditionLabel = weather.weatherCode.description,
                    isSnowing = weather.isSnowing,
                    isRaining = weather.isRaining,
                    items = outfit.allItems.map { it.displayName },
                    heroRef = uiState.effectiveHeroUrl
                )
            )
        }
    }

    // WS-3: the cue's timer — the pulse and the soft haptic live in
    // the element itself (WearIveRunMiniBar, keyed on the cue
    // sequence); this effect owns ONLY the 3.0 s timeout. It is
    // keyed on (isCue, cueSequence): leaving CUE for any reason
    // (a touch, trash, a scroll departure, rollover) cancels it, and
    // a silent content landing mid-cue — which changes neither key —
    // cannot restart it. A NEW landing bumps the sequence and the
    // timer restarts (§1.2: stays CUE, timer restarts).
    val wearIsCue = wearIveRunState is WearIveRunBarState.Cue
    LaunchedEffect(wearIsCue, wearCueSequence) {
        if (wearIsCue) {
            kotlinx.coroutines.delay(WearIveRunElementMachine.CUE_TIMEOUT_MS)
            wearIveRunHolder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        }
    }

    val listState = rememberLazyListState()
    // GEN-2 (motion decision §4/§5): the morph's driver for the next
    // tap/scroll transition (cue and collapse are intrinsic to the
    // target state and need no host driver), and whether a morph is
    // currently in flight (reported by the element itself) — the
    // scroll rules below latch on it.
    var wearTransitionDriver by remember { mutableStateOf(WearTransitionDriver.TAP) }
    var wearMorphInFlight by remember { mutableStateOf(false) }
    // WS-3: end-of-scroll, SYMMETRIC (D-07's latch retired). The
    // proximity zone is computed from the list's own layout info:
    // NEAR = the last item is visible and its end is within 8 dp of
    // the viewport's end; BEYOND = the last item is not the final
    // one, or its end is more than 48 dp past the viewport's end;
    // BETWEEN is a hysteresis dead zone that fires nothing. Events
    // are EDGE-triggered: entering NEAR dispatches arrival, leaving
    // NEAR all the way to BEYOND dispatches departure — so a tap
    // expansion away from the end is never instantly collapsed by
    // the element's resting scroll position.
    val wearEndEpsilonPx = with(LocalDensity.current) {
        WearIveRunElementMachine.END_ARRIVAL_EPSILON_DP.dp.roundToPx()
    }
    val wearEndDeparturePx = with(LocalDensity.current) {
        WearIveRunElementMachine.END_DEPARTURE_THRESHOLD_DP.dp.roundToPx()
    }
    val wearEndZone by remember(listState, wearEndEpsilonPx, wearEndDeparturePx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            when {
                info.totalItemsCount == 0 || last == null -> WearEndZone.BETWEEN
                last.index < info.totalItemsCount - 1 -> WearEndZone.BEYOND
                else -> {
                    val pastEnd = (last.offset + last.size) - info.viewportEndOffset
                    when {
                        pastEnd <= wearEndEpsilonPx -> WearEndZone.NEAR
                        pastEnd > wearEndDeparturePx -> WearEndZone.BEYOND
                        else -> WearEndZone.BETWEEN
                    }
                }
            }
        }
    }
    // GEN-2 (motion decision §5): while a morph is in flight the
    // scroll driver LATCHES — no zone edge dispatches, so a user
    // hovering at the boundary gets one move, not a breathing card
    // (§5.3), and during tap/cue transitions the list's offset is
    // frozen: no scroll event moves the element mid-flight (§5.1;
    // the dock is in the layout flow below the scroll area, so its
    // growth alone never writes the list's offset either). When the
    // flight ends, the CURRENT zone is re-read on the next emission
    // and edges dispatch from the latched position.
    LaunchedEffect(listState) {
        var wasNearEnd = false
        snapshotFlow { wearEndZone to wearMorphInFlight }.collect { (zone, inFlight) ->
            if (inFlight) return@collect // latched (§5.3) / frozen (§5.1)
            when {
                zone == WearEndZone.NEAR && !wasNearEnd -> {
                    wasNearEnd = true
                    wearTransitionDriver = WearTransitionDriver.SCROLL
                    wearIveRunHolder.onEvent(WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
                }
                zone == WearEndZone.BEYOND && wasNearEnd -> {
                    wasNearEnd = false
                    wearIveRunHolder.onEvent(WearIveRunElementEvent.SCROLL_DEPARTED_END)
                }
                zone == WearEndZone.BEYOND -> wasNearEnd = false
            }
        }
    }
    // GEN-2 (motion decision §5.2): end-anchored growth is 1:1 —
    // while a SCROLL-driven expansion is in flight at the end, the
    // list scrolls by exactly the dock's growth, so the last row
    // stays stationary relative to the card's top edge. (Collapse on
    // departure is the mirror: the same delta, reversed sign, runs
    // through the COLLAPSE driver, not this seam.)
    var wearDockHeightPx by remember { mutableIntStateOf(0) }
    var wearPrevDockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(wearDockHeightPx) {
        val delta = wearDockHeightPx - wearPrevDockHeightPx
        wearPrevDockHeightPx = wearDockHeightPx
        if (delta != 0 && wearMorphInFlight &&
            wearTransitionDriver == WearTransitionDriver.SCROLL
        ) {
            listState.scrollBy(delta.toFloat())
        }
    }
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

    // WS-6 (D-15): there is NO home-level share. The design moved
    // sharing inside the collection, card by card (Morning Poster /
    // Kit Card, rasterized — see ShareArtifacts); the home text
    // share that lived here is removed with its emoji payload.

    // WS-3 (D-11): the element's dim seam — true whenever ANY sheet
    // or sheet-like surface hosted from home is up. The element dims
    // with the home backdrop under the sheet's scrim. This lane does
    // not rebuild any sheet (WS-5); it only consumes these existing
    // open/closed booleans.
    val wearAnySheetUp = showDatePicker || showTimePicker || showSettings ||
        showLocationPicker || showShopAll || weatherDetailType != null ||
        showHeroFlagSheet || showPersonalFlagSheet || showWearIveRunCollection ||
        showMakeItMeFlow || showMakeItMePhotos || showMakeItMeLibrary || showMakeItMePaywall

    // Adaptive layout breakpoints (Material3 WindowSizeClass-aligned),
    // fluid at every width per the PWA's fully-responsive standard:
    // COMPACT: phone portrait (< 600dp) — single column, unchanged
    // MEDIUM: foldable unfolded / tablet portrait (600-840dp) — full-bleed
    //   stacked layout, outfit cards flowing into a grid as width allows
    // EXPANDED: tablet landscape / desktop (> 840dp) — two-pane rail
    BoxWithConstraints(
        modifier = modifier
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

                // WS-3 (D-06): the content branch is a Column — the
                // scroll area takes the flexible space and the Wear
                // I've Run element docks BELOW it as a citizen of the
                // layout flow (a sticky bottom dock at the end of the
                // home content), never as a root-Box overlay floating
                // over list rows.
                Column(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.weight(1f)
                ) {
                    when (layout) {
                        AdaptiveLayout.COMPACT -> {
                            LazyColumn(
                                state = listState,
                                // RW-25: the scroll's ground IS the sheet
                                // surface — the sheet reads continuous
                                // from its overlap of the photo to the
                                // footer.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(RunWearColors.Surface),
                                contentPadding = PaddingValues(
                                    bottom = 60.dp + scaffoldContentPadding.calculateBottomPadding()
                                )
                            ) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(RunWearColors.Surface)
                                    ) {
                                        HeroStackedUnit(
                                            uiState = uiState,
                                            contentVisible = contentVisible,
                                            scrollOffset = scrollOffset.value,
                                            onDateClick = { showDatePicker = true },
                                            onTimeClick = { showTimePicker = true },
                                            onNowClick = onResetToNow,
                                            onLocationClick = { showLocationPicker = true },
                                            onSettingsClick = { showSettings = true },
                                            onWeatherPillClick = { type -> weatherDetailType = type },
                                            onFlagClick = onAnyFlagClick,
                                            heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                            onCycleHero = onCycleHero,
                                            heroPill = heroPill,
                                            onHeroPillAction = { showMakeItMePaywall = true },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (uiState.personalLookStatus is
                                            com.runwear.app.makeitme.PersonalLookStatus.Failed
                                        ) {
                                            com.runwear.app.makeitme.MakeItMeFailedLine(
                                                onRetry = onRetryPersonalLook
                                            )
                                        }
                                    }
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
                                // RW-14: the 6th-cell upsell card, below
                                // the outfit in the normal scroll (§5.1).
                                if (uiState.showMakeItMeUpsell) {
                                    item {
                                        com.runwear.app.makeitme.MakeItMeUpsellCard(
                                            onSubscribeClick = { showMakeItMePaywall = true },
                                            onDismiss = onDismissMakeItMeUpsell,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-5 (Addendum 4): the convergence
                                // band sits DIRECTLY BELOW THE OUTFIT
                                // AREA — above the day chart, the Pro
                                // Tip and the feedback row — inside
                                // the first screenful, so a user who
                                // has just subscribed sees what is
                                // happening without scrolling.
                                if (convergenceBand != null) {
                                    item {
                                        com.runwear.app.makeitme.ConvergenceBand(
                                            content = convergenceBand,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-4: the day-chart glance band.
                                if (!dayChartModel.isEmpty) {
                                item { dayChartBandContent() }
                                }
                                // GEN-5 (C-3): Pro Tip + feedback,
                                // below the chart.
                                homeTrailingListItems(
                                    uiState = uiState,
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(RunWearColors.Surface),
                                contentPadding = PaddingValues(
                                    start = spec.pagePadding,
                                    end = spec.pagePadding,
                                    bottom = 60.dp + scaffoldContentPadding.calculateBottomPadding()
                                ),
                                horizontalArrangement = Arrangement.spacedBy(spec.gridGap),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(RunWearColors.Surface)
                                    ) {
                                        HeroStackedUnit(
                                            uiState = uiState,
                                            contentVisible = contentVisible,
                                            scrollOffset = gridScrollOffset.value,
                                            onDateClick = { showDatePicker = true },
                                            onTimeClick = { showTimePicker = true },
                                            onNowClick = onResetToNow,
                                            onLocationClick = { showLocationPicker = true },
                                            onSettingsClick = { showSettings = true },
                                            onWeatherPillClick = { type -> weatherDetailType = type },
                                            onFlagClick = onAnyFlagClick,
                                            heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                            onCycleHero = onCycleHero,
                                            heroPill = heroPill,
                                            onHeroPillAction = { showMakeItMePaywall = true },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (uiState.personalLookStatus is
                                            com.runwear.app.makeitme.PersonalLookStatus.Failed
                                        ) {
                                            com.runwear.app.makeitme.MakeItMeFailedLine(
                                                onRetry = onRetryPersonalLook
                                            )
                                        }
                                    }
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
                                if (uiState.showMakeItMeUpsell) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        com.runwear.app.makeitme.MakeItMeUpsellCard(
                                            onSubscribeClick = { showMakeItMePaywall = true },
                                            onDismiss = onDismissMakeItMeUpsell,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-5 (Addendum 4): band directly
                                // below the outfit area, above the
                                // chart and the Pro Tip.
                                if (convergenceBand != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        com.runwear.app.makeitme.ConvergenceBand(
                                            content = convergenceBand,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-4: the day-chart glance band.
                                if (!dayChartModel.isEmpty) {
                                item(span = { GridItemSpan(maxLineSpan) }) { dayChartBandContent() }
                                }
                                // GEN-5 (C-3): Pro Tip + feedback,
                                // below the chart.
                                homeTrailingGridItems(
                                    uiState = uiState,
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
                                        onLocationClick = { showLocationPicker = true },
                                        onSettingsClick = { showSettings = true },
                                        onFlagClick = onAnyFlagClick,
                                        heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                        onCycleHero = onCycleHero,
                                        heroPill = heroPill,
                                        onHeroPillAction = { showMakeItMePaywall = true },
                                        modifier = Modifier.fillMaxSize(),
                                        fillHeight = true
                                    )
                                    if (uiState.personalLookStatus is
                                        com.runwear.app.makeitme.PersonalLookStatus.Failed
                                    ) {
                                        com.runwear.app.makeitme.MakeItMeFailedLine(
                                            onRetry = onRetryPersonalLook,
                                            modifier = Modifier.align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                                // Right pane: outfit grid (independently scrollable)
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = OUTFIT_GRID_MIN_CELL),
                                    state = gridState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(RunWearColors.Surface),
                                    contentPadding = PaddingValues(
                                        start = spec.pagePadding,
                                        end = spec.pagePadding,
                                        bottom = 60.dp + scaffoldContentPadding.calculateBottomPadding()
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(spec.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // RW-25: in the split layout the sheet
                                    // block tops the right pane as its
                                    // first full-span item — no overlap
                                    // (the photo is the left pane).
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        HeroSheetContent(
                                            uiState = uiState,
                                            contentVisible = contentVisible,
                                            onDateClick = { showDatePicker = true },
                                            onTimeClick = { showTimePicker = true },
                                            onNowClick = onResetToNow,
                                            onWeatherPillClick = { type -> weatherDetailType = type },
                                            overlapsPhoto = false,
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
                                    if (uiState.showMakeItMeUpsell) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            com.runwear.app.makeitme.MakeItMeUpsellCard(
                                                onSubscribeClick = { showMakeItMePaywall = true },
                                                onDismiss = onDismissMakeItMeUpsell,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    // GEN-5 (Addendum 4): band
                                    // directly below the outfit area.
                                    if (convergenceBand != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            com.runwear.app.makeitme.ConvergenceBand(
                                                content = convergenceBand,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    // GEN-4: the day-chart glance band.
                                    if (!dayChartModel.isEmpty) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { dayChartBandContent() }
                                    }
                                    // GEN-5 (C-3): Pro Tip + feedback,
                                    // below the chart.
                                    homeTrailingGridItems(
                                        uiState = uiState,
                                        onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) }
                                    )
                                }
                            }
                        }
                    }
                }
                // WS-3: the Wear I've Run dock — the last citizen of
                // the content Column, in flow, below the scroll area.
                // It renders only for present states (the machine's
                // ABSENT renders nothing and takes no space).
                // D-11 seam: `dimmed` consumes the host's sheet state
                // (any sheet up over home) — WS-5 owns sheet
                // internals; this lane only reads their open/closed
                // booleans, listed in wearAnySheetUp below.
                if (wearIveRunState != WearIveRunBarState.Absent) {
                    WearIveRunMiniBar(
                        state = wearIveRunState,
                        cueSequence = wearCueSequence,
                        dimmed = wearAnySheetUp,
                        transitionDriver = wearTransitionDriver,
                        onTransitionRunningChanged = { wearMorphInFlight = it },
                        onExpandClick = {
                            // A tap expansion: the TAP driver (spring
                            // 0.92/400, no overshoot) — set before the
                            // event so the element picks it up with
                            // the state change.
                            wearTransitionDriver = WearTransitionDriver.TAP
                            wearIveRunHolder.onEvent(WearIveRunElementEvent.TAP_BODY)
                        },
                        onCollapseClick = {
                            wearIveRunHolder.onEvent(WearIveRunElementEvent.COLLAPSE_CONTROL)
                        },
                        onRemoveClick = {
                            // Trash: one tap, NO confirmation; the
                            // machine goes ABSENT and the day's entry
                            // is deleted — a later generation
                            // re-creates it and fires the creation cue.
                            val entry = wearIveRunHolder.currentEntry()
                            wearIveRunHolder.onEvent(WearIveRunElementEvent.TRASH)
                            if (entry != null) {
                                screenScope.launch {
                                    // GEN-3: delete by identity —
                                    // a session entry by sessionId
                                    // (its place may have moved), a
                                    // legacy entry by (date, place).
                                    wearIveRunStore.removeByIdentity(entry)
                                }
                            }
                        },
                        onOpenCollection = { showWearIveRunCollection = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 8.dp)
                            .navigationBarsPadding()
                            .onSizeChanged { wearDockHeightPx = it.height }
                    )
                }
                }
            }
            // No content yet: the branded loading state while the first
            // load runs (the low-contrast shimmer read as a black screen),
            // the error screen only when a load actually failed with
            // nothing to show.
            uiState.isLoading -> HomeSkeletonScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
            uiState.error != null -> HeroErrorScreen(uiState.error, onRefresh)
            else -> HomeSkeletonScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
        }

        // Hero-flag confirmation ("Thanks — we'll review this photo").
        // GEN-5 (RW-29 cream map): snackbars render in the app's
        // own tokens — the default takes the inverseSurface role
        // and read as a foreign dark bar on the cream sheet.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) { data ->
            androidx.compose.material3.Snackbar(
                snackbarData = data,
                containerColor = RunWearColors.BgCard,
                contentColor = RunWearColors.TextPrimary,
                actionColor = RunWearColors.PineAccent
            )
        }

        // (WS-3: the Wear I've Run element no longer lives here — it
        // docks in the content branch's Column above, in the layout
        // flow. The collection screen remains hosted at this level;
        // it is a Dialog window of its own, WS-5's concern.)

        if (showWearIveRunCollection) {
            WearIveRunCollectionScreen(
                entries = wearIveRunEntries,
                today = uiState.locationNow.toLocalDate(),
                onDismiss = { showWearIveRunCollection = false },
                gender = uiState.genderPreference
            )
        }
    }

    // Date Picker
    if (showDatePicker) {
        DatePickerSheet(
            selectedDate = uiState.selectedDateTime.toLocalDate(),
            today = uiState.locationNow.toLocalDate(),
            onSelectDate = {
                onDateSelected(it)
                showDatePicker = false
                // RW-53: a confirmed date change re-solves home —
                // the time sheet (if it hosted the day door) closes
                // with it; its draft dies with the old day.
                showTimePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time Picker — GEN-4: the chart sheet. Confirm commits the
    // drafted hour (the same downstream re-solve as a tile pick:
    // hero, headline, outfit, detail); confirming the untouched NOW
    // hour keeps the NOW state (TimePickerDraftPolicy's pinned
    // interpretation) and commits nothing. Cancel simply dismisses —
    // the draft was sheet-local, so the pre-open selection stands.
    if (showTimePicker) {
        TimePickerSheet(
            model = dayChartModel,
            selectedHour = uiState.selectedDateTime.hour,
            locationNow = uiState.locationNow,
            isNowSelected = uiState.isNowSelected,
            temperatureUnit = uiState.temperatureUnit,
            locationName = uiState.locationName,
            onConfirm = { confirm ->
                if (!confirm.keepNow) onTimeSelected(confirm.hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
            // RW-53: the time sheet's day door opens Choose-a-Day.
            onDayClick = { showDatePicker = true }
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
            appearancePreference = appearancePreference,
            onSetAppearancePreference = onSetAppearancePreference,
            analyticsOptIn = analyticsOptIn,
            onAnalyticsOptInChange = onAnalyticsOptInChange,
            onSendFeedback = {
                showSettings = false
                onOpenFeedback(null)
            },
            onRateRunWear = {
                showSettings = false
                onRateRunWear()
            },
            makeItMeViewModel = makeItMeViewModel,
            makeItMeState = makeItMeState,
            onMakeItMeOptInClick = {
                showSettings = false
                makeItMeFlowReplace = false
                showMakeItMeFlow = true
            },
            onMakeItMePhotosClick = {
                showSettings = false
                showMakeItMePhotos = true
            },
            onMakeItMeLooksClick = {
                showSettings = false
                showMakeItMeLibrary = true
            },
            onMakeItMeSubscribeClick = {
                showSettings = false
                showMakeItMePaywall = true
            },
            onMakeItMeManageSubscriptionClick = {
                showSettings = false
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(com.runwear.app.makeitme.BillingManager.MANAGE_SUBSCRIPTIONS_URL)
                    )
                )
            },
            onMakeItMeDeleted = { onPersonalLayerChanged() },
            onDismiss = { showSettings = false }
        )
    }

    // ── RW-14 overlays ──────────────────────────────────────────────────
    // Make It Me OFF in this build (MakeItMeAvailability): the
    // upload flow, photos view, and paywall sheet below never
    // open — their setters are unreachable (the Settings section
    // does not render and the upsell card never shows), and the
    // availability conjunct keeps that true at the render site.
    if (showMakeItMeFlow && makeItMeViewModel != null &&
        com.runwear.app.makeitme.MakeItMeAvailability.ENABLED
    ) {
        com.runwear.app.makeitme.MakeItMeUploadFlow(
            viewModel = makeItMeViewModel,
            gender = uiState.genderPreference.name,
            replace = makeItMeFlowReplace,
            todaysCell = onPersonalCombinationRequest(),
            onFinished = {
                showMakeItMeFlow = false
                makeItMeFlowEntryDoor = com.runwear.app.makeitme.MakeItMeEntryDoor.STANDARD
                if (!makeItMeFlowReplace) onFirstLookRequested()
                onPersonalLayerChanged()
            },
            // From Settings, closing without finishing changes nothing —
            // personalization stays off (State A remains the truth).
            onExitAsStock = {
                showMakeItMeFlow = false
                makeItMeFlowEntryDoor = com.runwear.app.makeitme.MakeItMeEntryDoor.STANDARD
            },
            entryDoor = makeItMeFlowEntryDoor
        )
    }

    // RW-61: the looks library — a full-screen Dialog of its own,
    // hosted exactly like Your Photos.
    if (showMakeItMeLibrary && makeItMeViewModel != null &&
        com.runwear.app.makeitme.MakeItMeAvailability.ENABLED
    ) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMakeItMeLibrary = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            com.runwear.app.makeitme.LooksLibraryScreen(
                viewModel = makeItMeViewModel,
                onDismiss = { showMakeItMeLibrary = false }
            )
        }
    }

    if (showMakeItMePhotos && makeItMeViewModel != null &&
        com.runwear.app.makeitme.MakeItMeAvailability.ENABLED
    ) {
        // WS-5 (F-2): Your Photos is a real presentation structure —
        // a full-screen Dialog window of its own, not a boolean
        // overlay drawn over home. System Back / the predictive-back
        // gesture lands on the Dialog's onDismissRequest and returns
        // to home (the surface that opened it); before this, no
        // BackHandler existed anywhere and Back fell through to the
        // activity, closing the app.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMakeItMePhotos = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            com.runwear.app.makeitme.MakeItMePhotosScreen(
                viewModel = makeItMeViewModel,
                onDismiss = { showMakeItMePhotos = false },
                onReplaceClick = {
                    showMakeItMePhotos = false
                    makeItMeFlowReplace = true
                    showMakeItMeFlow = true
                },
                // GEN-5 (item 21): the two separate doors enter the
                // replace flow through their own entries.
                onRetakeSelfieClick = {
                    showMakeItMePhotos = false
                    makeItMeFlowReplace = true
                    makeItMeFlowEntryDoor =
                        com.runwear.app.makeitme.MakeItMeEntryDoor.GUIDED_SELFIE
                    showMakeItMeFlow = true
                },
                onChooseDifferentPhotoClick = {
                    showMakeItMePhotos = false
                    makeItMeFlowReplace = true
                    makeItMeFlowEntryDoor =
                        com.runwear.app.makeitme.MakeItMeEntryDoor.LIBRARY
                    showMakeItMeFlow = true
                }
            )
        }
    }

    if (showMakeItMePaywall && makeItMeViewModel != null &&
        com.runwear.app.makeitme.MakeItMeAvailability.ENABLED
    ) {
        com.runwear.app.makeitme.MakeItMePaywallSheet(
            viewModel = makeItMeViewModel,
            onDismiss = { showMakeItMePaywall = false },
            onPurchased = {
                showMakeItMePaywall = false
                onPersonalLayerChanged()
            }
        )
    }

    // Location Picker
    // RW-26 Section E: recents (actually-selected places only).
    val recentLocationsStore = remember { RecentLocationsStore(context.applicationContext) }
    val recentLocations by recentLocationsStore.recentsFlow.collectAsState(initial = emptyList())
    var lastRemovedRecent by remember { mutableStateOf<Pair<RecentLocation, Int>?>(null) }

    if (showLocationPicker) {
        LocationPickerSheet(
            currentLocation = uiState.locationName,
            onSearch = onLocationSearch,
            recents = recentLocations,
            onRemoveRecent = { recent ->
                val index = recentLocations.indexOf(recent)
                lastRemovedRecent = recent to index
                screenScope.launch {
                    recentLocationsStore.setAll(
                        RecentLocationsLogic.remove(recentLocations, recent.name)
                    )
                }
            },
            onUndoRemoveRecent = {
                val removed = lastRemovedRecent
                if (removed != null) {
                    screenScope.launch {
                        recentLocationsStore.setAll(
                            RecentLocationsLogic.restore(
                                recentLocations, removed.first, removed.second
                            )
                        )
                    }
                    lastRemovedRecent = null
                }
            },
            onSelectLocation = { lat, lon, name ->
                screenScope.launch {
                    recentLocationsStore.record(RecentLocation(name, lat, lon))
                }
                onLocationSelect(lat, lon, name)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
            searchResults = locationSearchState.results,
            isSearching = locationSearchState.isSearching,
            isUsingGPS = isUsingGPS,
            hasLocationPermission = hasLocationPermission,
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
                onDismiss = { weatherDetailType = null },
                temperatureUnit = uiState.temperatureUnit,
                // RW-63: the unified sheet's context line + tip
                // carry the home state's own values.
                locationName = uiState.locationName,
                momentLabel = if (uiState.isNowSelected) {
                    "Now"
                } else {
                    com.runwear.app.ui.components.DayChartFormat.hourLabel(
                        uiState.selectedDateTime.hour
                    )
                },
                runningTip = uiState.outfit?.tips?.firstOrNull(),
                comfortPreference = uiState.comfortPreference
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

    // Personal Hero Flag Sheet — the personal menu, headed "This
    // doesn't look like me" (spec §4, RW-16.27). Picking a reason
    // files the flag_hero action; when it lands, the ViewModel
    // re-resolves the hero and the pool photo serves while the
    // regeneration the flag queued is pending.
    if (showPersonalFlagSheet &&
        com.runwear.app.makeitme.MakeItMeAvailability.ENABLED
    ) {
        PersonalHeroFlagSheet(
            onSelectReason = { reason ->
                showPersonalFlagSheet = false
                flaggedHeroUrl = uiState.heroImageUrl
                onFlagPersonalHero(reason)
                screenScope.launch {
                    snackbarHostState.showSnackbar("Thanks — we'll make a new version of this look")
                }
            },
            onDismiss = { showPersonalFlagSheet = false }
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

/**
 * WS-3: the home list's end-of-scroll proximity zone, from
 * [WearIveRunElementMachine]'s thresholds — NEAR (within 8 dp of the
 * end), BEYOND (more than 48 dp from it, or the final item not yet
 * visible), BETWEEN (the hysteresis dead zone between them). Only
 * NEAR→BEYOND and →NEAR edges dispatch machine events.
 */
internal enum class WearEndZone { NEAR, BETWEEN, BEYOND }

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
 * Hero PHOTO zone with entrance animation (shared across all adaptive
 * layouts). RW-25: the photo carries chrome only — everything else
 * lives on the sheet ([HeroSheetContent]).
 */
@Composable
private fun HeroSectionContent(
    uiState: MainUiState,
    contentVisible: Boolean,
    scrollOffset: Float,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFlagClick: () -> Unit,
    heroFlagEnabled: Boolean,
    onCycleHero: () -> Unit = {},
    /** GEN-3 A-HERO (WS-A(2)): the hero pill's content + its action. */
    heroPill: com.runwear.app.ui.components.HeroPillContent? = null,
    onHeroPillAction: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** EXPANDED split pane only: the photo fills the pane's height. */
    fillHeight: Boolean = false
) {
    // WS-1 (D-01 mechanism, applied to the photo half): the caller's
    // modifier belongs to THIS root — the direct child of the
    // stacking Box — never forwarded to an inner node, or the Box
    // cannot read its alignment from it.
    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        HeroSection(
            weather = uiState.weather,
            outfit = uiState.outfit,
            // WS-4/WS-1: the photo-zone chrome (the location pill)
            // shows the SIMPLE place form, never the raw geocode
            // string.
            locationName = uiState.simplePlace?.display ?: uiState.locationName,
            heroImageUrl = uiState.heroImageUrl,
            // GEN-3 A-HERO (WS-A(1)/X-7): the scene's bundled fallback
            // is the hero band's base from the scene's first frame —
            // the band never sits on the designed gradient while a
            // truthful photo is servable, and a place switch re-bases
            // on the NEW place's fallback, never the old photo.
            heroFallbackUrl = uiState.heroFallbackUrl,
            heroSceneKey = uiState.locationName,
            heroPill = heroPill,
            onHeroPillAction = onHeroPillAction,
            scrollOffset = scrollOffset,
            onLocationClick = onLocationClick,
            onSettingsClick = onSettingsClick,
            // RW-16.27: the flag chip shows for ANY hero photo — a
            // personal photo most of all (its menu is the personal
            // one) — and the refresh chip follows the ROTATION size
            // (personal photo + pool candidates), so both controls
            // live in the personal state too.
            showFlagButton = uiState.effectiveHeroUrl != null,
            flagEnabled = heroFlagEnabled,
            onFlagClick = onFlagClick,
            heroIsPersonal = uiState.heroIsPersonal,
            // GEN-5 (RW-28 item 32, A-23): the Now-state hero shows
            // the FLAG control only; the refresh chip joins in
            // CHANGED states (a non-Now moment selected), where
            // cycling the moment's photos is meaningful. The gen-4
            // build showed refresh at Now too whenever the cell had
            // more than one candidate.
            showRefreshButton = !uiState.isNowSelected &&
                uiState.effectiveHeroUrl != null &&
                uiState.heroCandidates.size +
                (if (uiState.personalHeroImageUrl != null) 1 else 0) > 1,
            onRefreshHeroClick = onCycleHero,
            scaffoldBadge = if (uiState.heroIsPersonal) {
                com.runwear.app.makeitme.ScaffoldRenderPolicy.homeBadge(uiState.heroServeKind)
            } else {
                null
            },
            modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        )
    }
}

/**
 * The home SHEET block with the same entrance treatment as the photo
 * zone. [overlapsPhoto] is true in the stacked layouts (rounded top
 * over the photo's foot) and false atop the EXPANDED right pane.
 */
@Composable
private fun HeroSheetContent(
    uiState: MainUiState,
    contentVisible: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit,
    overlapsPhoto: Boolean = true,
    modifier: Modifier = Modifier
) {
    // WS-1 (D-01, THE critical fix): [modifier] is applied to THIS
    // AnimatedVisibility — the DIRECT child of the stacking Box — so
    // the Box reads its Alignment.BottomStart from it and the sheet
    // composes overlapping the photo's foot. Before WS-1 the
    // modifier was forwarded to the inner HeroSheetBlock and this
    // root carried none, so the Box placed the sheet at its default
    // TopStart — ON TOP of the photo, burying the chrome row
    // (D-02/A3) and manufacturing the scroll void (D-04).
    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        HeroSheetBlock(
            weather = uiState.weather,
            locationName = uiState.locationName,
            selectedDateTime = uiState.selectedDateTime,
            locationNow = uiState.locationNow,
            isNowSelected = uiState.isNowSelected,
            onDateClick = onDateClick,
            onTimeClick = onTimeClick,
            onNowClick = onNowClick,
            onWeatherPillClick = onWeatherPillClick,
            overlapsPhoto = overlapsPhoto,
            modifier = Modifier.fillMaxWidth(),
            temperatureUnit = uiState.temperatureUnit,
            // GEN-2 A2 (W-7): hold the hero numeral whole while a
            // fetch/switch is in flight; it swaps at the commit.
            holdNumeral = uiState.isLoading,
            // GEN-5 (RW-28 item 11): the RW-23 comfort-adjusted
            // frame for the numeral and the bracket chip.
            comfortPreference = uiState.comfortPreference
        )
    }
}

/**
 * RW-25: the stacked layouts' two-zone unit — the photo zone with the
 * sheet block overlapping its foot by [HeroSheetOverlap]. The Box is
 * sized heroHeight + sheetBlockHeight − overlap; the sheet's height
 * is measured as it composes (seeded with a close estimate so the
 * first frame barely moves).
 */
@Composable
private fun HeroStackedUnit(
    uiState: MainUiState,
    contentVisible: Boolean,
    scrollOffset: Float,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onWeatherPillClick: (String) -> Unit,
    onFlagClick: () -> Unit,
    heroFlagEnabled: Boolean,
    onCycleHero: () -> Unit = {},
    heroPill: com.runwear.app.ui.components.HeroPillContent? = null,
    onHeroPillAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val heroHeight = heroPhotoHeight(configuration.screenHeightDp)
    var sheetHeight by remember { mutableStateOf(248.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // WS-1: the reservation is HeroStackedGeometry's — the
            // first item reserves EXACTLY hero + sheet − 28dp, the
            // sheet is bottom-anchored inside it, and the accessory
            // rows that follow the unit (Make It Me progress/failed
            // lines) live in the same first list item, so they scroll
            // with the page. No height in this item is unoccupied at
            // any sheet height (D-04's void is zero by construction).
            .height(
                com.runwear.app.ui.components.HeroStackedGeometry.unitHeight(
                    heroHeight, sheetHeight
                )
            )
    ) {
        HeroSectionContent(
            uiState = uiState,
            contentVisible = contentVisible,
            scrollOffset = scrollOffset,
            onLocationClick = onLocationClick,
            onSettingsClick = onSettingsClick,
            onFlagClick = onFlagClick,
            heroFlagEnabled = heroFlagEnabled,
            onCycleHero = onCycleHero,
            heroPill = heroPill,
            onHeroPillAction = onHeroPillAction,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
        )
        HeroSheetContent(
            uiState = uiState,
            contentVisible = contentVisible,
            onDateClick = onDateClick,
            onTimeClick = onTimeClick,
            onNowClick = onNowClick,
            onWeatherPillClick = onWeatherPillClick,
            overlapsPhoto = true,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val measured = with(density) { size.height.toDp() }
                    if (measured != sheetHeight) sheetHeight = measured
                }
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
                // "X Items for Your Run" — RW-25: Bebas retired from
                // the home surface; M3 headlineSmall (semibold sans).
                Text(
                    text = com.runwear.app.ui.components.OutfitHeaderCopy.header(itemCount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = RunWearColors.TextPrimary
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
                            tint = com.runwear.app.ui.theme.PhotoInk
                        )
                        Text(
                            text = "Shop",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = com.runwear.app.ui.theme.PhotoInk
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

    // GEN-5 (C-3 + Addendum 4): the Pro Tip and the feedback row
    // NO LONGER live inside the outfit list. The settled home
    // order is hero/headline → outfit → convergence band (while
    // generating) → day-chart glance band → Pro Tip → Wear I've
    // Run, so the trailing elements are emitted by
    // homeTrailingListItems AFTER the band and the chart at each
    // layout's call site.

    // WS-8 (D-26): the hardcoded "v3.12" PWA version footer is
    // removed — no design frame carries it; the real version lives
    // in Settings → About.
}

/**
 * GEN-5 (C-3): the home scroll's trailing elements for the list
 * layout — the Pro Tip, then the contextual feedback entry —
 * emitted after the convergence band and the day-chart glance
 * band in the settled home order.
 */
private fun LazyListScope.homeTrailingListItems(
    uiState: MainUiState,
    onFeedbackClick: () -> Unit
) {
    val outfit = uiState.outfit ?: return

    // Pro Tip Section — below the day chart (C-3).
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
            color = RunWearColors.PineAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFeedbackClick() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
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

    // GEN-5 (C-3 + Addendum 4): Pro Tip + feedback moved out to
    // homeTrailingGridItems, emitted after the band and the chart.

    // WS-8 (D-26): grid presentation carries no version footer either.
}

/**
 * GEN-5 (C-3): the grid layouts' trailing elements — Pro Tip,
 * then the feedback entry, both full-span — emitted after the
 * convergence band and the day-chart glance band.
 */
private fun LazyGridScope.homeTrailingGridItems(
    uiState: MainUiState,
    onFeedbackClick: () -> Unit
) {
    val outfit = uiState.outfit ?: return

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
            color = RunWearColors.PineAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFeedbackClick() }
                .padding(vertical = 10.dp)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

/**
 * RW-16.27: the PERSONAL hero's flag sheet — same sheet idiom as
 * the pool one, but the menu is the personal vocabulary, and every
 * pick files the backend's flag_hero action against the photo on
 * screen: it retires immediately and a regeneration is queued.
 * (The pool sheet's insert cannot identify a personal photo and
 * would retire nothing.)
 *
 * WS-6 (D-17): the hierarchy is design frame 45's — the sheet LEADS
 * with "This doesn't look like me" as its heading (the heading is
 * the lead reason's own control), its consequence stated beneath
 * it; the other reasons follow as rows. The generic "Flag this
 * photo" header belongs to the pool variant only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalHeroFlagSheet(
    onSelectReason: (com.runwear.app.makeitme.PersonalHeroFlagReason) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectReason(
                            com.runwear.app.makeitme.PersonalFlagSheetContent.headingReason
                        )
                    }
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = com.runwear.app.makeitme.PersonalFlagSheetContent.heading,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = com.runwear.app.makeitme.PersonalFlagSheetContent.HEADING_CONSEQUENCE,
                    fontSize = 14.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            com.runwear.app.makeitme.PersonalFlagSheetContent.rowReasons.forEach { reason ->
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
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Couldn't load your weather",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            // GEN-5 (RW-29 FA-15): the error body is AUTHORED
            // (design frame 47) — it names the failure and what
            // is safe. The gen-4 screen printed the exception's
            // own message here ("Unable to resolve host
            // api.open-meteo.com: No address associated with
            // hostname" in Sol's field recordings). The raw
            // message stays in state for diagnostics; it is
            // never rendered.
            Text(
                "The weather service didn't answer.",
                style = MaterialTheme.typography.bodyLarge,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your places, settings, and photos stay on this phone — nothing is lost.",
                style = MaterialTheme.typography.bodyMedium,
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
