package com.runwear.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import com.runwear.app.weariverun.WearIveRunCollectionLogic
import com.runwear.app.weariverun.WearIveRunCollectionScreen
import com.runwear.app.weariverun.ShareStudioSheet
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunDock
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunDockPolicy
import com.runwear.app.weariverun.WearIveRunFooterSection
import com.runwear.app.weariverun.WearIveRunNarration
import com.runwear.app.weariverun.WearIveRunStore
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDate

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
    /** RW-131: the picker session's single (date, hour, keepNow)
     * commit. Null in render proofs, which fall back to the
     * sequential date-then-hour path. */
    onPickerCommit: ((LocalDate, Int, Boolean) -> Unit)? = null,
    onResetToNow: () -> Unit,
    onLocationSearch: (String) -> Unit,
    onLocationSelect: (Double, Double, String) -> Unit,
    onUseCurrentLocation: () -> Unit = {},
    /** RW-141: the sheet reacted to a succeeded Current location request (it closed); let the owner clear the outcome. */
    onCurrentLocationRequestHandled: () -> Unit = {},
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
     * RW-79: the Home paywall was dismissed back to Home — re-read
     * the personal look state (and re-arm its poll) so a look that
     * resolved behind the paywall lands within seconds.
     */
    onPersonalLookRecheck: () -> Unit = {},
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
    // RW-131: the picker session's working day while the time
    // sheet is open. A date chosen through the sheet's day door
    // lands here (session state), NOT in the committed selection;
    // the sheet's model rebuilds for this day and the sheet stays
    // open. Cleared whenever the time sheet closes.
    var pickerWorkingDate by remember { mutableStateOf<LocalDate?>(null) }
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
    // RW-169 §7D: the straddle pill's measured seat. HeroStackedUnit
    // reports its in-list pill's window bounds here; while the WIR
    // panel is expanded (or morphing), the pill is ALSO rendered in
    // this screen's overlay host at those measured bounds, z-ordered
    // ABOVE the panel, so no surface can bisect it (diagnosis D8:
    // the panel's top edge sliced the pill at frame-31.6 s). Both
    // copies render the SAME single heroPill value below — one
    // agreed per-cell presentation state, never a second source.
    var wirPillAnchorBounds by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val heroPill = com.runwear.app.ui.components.HeroPillPolicy.resolve(
        lookStatus = uiState.personalLookStatus,
        serveKind = uiState.heroServeKind,
        heroIsPersonal = uiState.heroIsPersonal,
        mode = makeItMeState.mode,
        freeGenerationsUsed = makeItMeState.freeGenerationsUsed
    )

    // GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4): the
    // home-sheet band while a subscribed user's bank converges —
    // resolved once here. RW-101 (Sol, Nov 6, 2026): seated
    // DIRECTLY UNDER THE HERO in every layout (superseding the
    // gen-3/gen-5 below-the-outfit seat). Null unless convergence
    // is genuinely in progress with real counts.
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
        uiState.sunTimes, uiState.weather, uiState.isNowSelected, uiState.locationNow
    ) {
        // RW-FIXWAVE (Nov 7 ruling): at Now, the chart's current-
        // hour point is the live current-conditions feels-like —
        // the same value the hero numeral quotes — never the
        // hourly forecast row for that hour.
        val nowWeather = uiState.weather
        com.runwear.app.ui.components.DayChartBuilder.build(
            hourly = uiState.hourlyForecast,
            date = uiState.selectedDateTime.toLocalDate(),
            comfort = uiState.comfortPreference,
            sunTimes = uiState.sunTimes,
            nowDate = if (uiState.isNowSelected) {
                uiState.locationNow.toLocalDate()
            } else {
                null
            },
            nowHour = if (uiState.isNowSelected) uiState.locationNow.hour else null,
            nowFeelsLikeF = if (uiState.isNowSelected && nowWeather != null) {
                nowWeather.feelsLikeInFahrenheit +
                    uiState.comfortPreference.tempAdjustment
            } else {
                null
            }
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

    // RW-144 (Nov 4, 2026 ruling, carried into the redesign): below
    // the day-chart glance band sits the COMPLETE detailed weather
    // block — the same unified detail the weather sheet carries
    // (RW-63), rendered in place. It describes the hour the chart
    // marker is on: the marker follows the selection and
    // uiState.weather IS the selected moment's conditions, so the
    // block and the marker can never name different hours.
    val detailedWeatherBlockContent: @Composable () -> Unit = {
        val w = uiState.weather
        if (w != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(RunWearColors.Hairline)
                )
                Spacer(Modifier.height(16.dp))
                UnifiedWeatherDetail(
                    weather = w,
                    unit = uiState.temperatureUnit,
                    locationName = uiState.simplePlace?.display
                        ?: uiState.locationName,
                    momentLabel = com.runwear.app.ui.components.HeroSheetFormat
                        .momentText(
                            uiState.selectedDateTime,
                            uiState.locationNow,
                            uiState.isNowSelected
                        ),
                    runningTip = uiState.outfit?.tips?.firstOrNull(),
                    comfortPreference = uiState.comfortPreference,
                    // RW-167: this call is INLINED in the home
                    // scroll, where homeTrailingListItems renders
                    // the Pro Tip card from the same tips list —
                    // the inlined block drops its tip row (the
                    // weather sheet's call keeps it). The tip is
                    // still passed: suppression is the flag, not
                    // a null (null would render the generic
                    // fallback tip instead).
                    showRunningTip = false
                )
            }
        }
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
    // RW-169 §7E.1: the PresentedEntry stability gate — a viewed
    // frame reaches the record only after it is unchanged for the
    // spec's ≈2 s window (see PresentedEntry.kt).
    val wirStabilityGate = remember {
        com.runwear.app.weariverun.StabilityGate()
    }
    var showWearIveRunCollection by remember { mutableStateOf(false) }
    // RW-133: full-screen hero viewer state — the home hero, or a
    // collection entry's stored photograph.
    var showHeroViewer by remember { mutableStateOf(false) }
    var heroViewerEntry by remember {
        mutableStateOf<WearIveRunDayEntry?>(null)
    }
    // RW-158 (user ruling, Nov 10, 2026): the footer's entry row,
    // when the footer's entry is a PAST entry, opens the share
    // studio for that entry directly.
    var wirFooterShareEntry by remember {
        mutableStateOf<WearIveRunDayEntry?>(null)
    }

    // RW-168 (Nov 11, 2026): the share-card selection the user
    // last made in any Share Studio sheet this session (kind +
    // size format), seeded back into every sheet host — the home
    // share menu's direct "Save to photos" saves today's card in
    // exactly this selection. Standing defaults: Postcard, Feed.
    var shareKind by remember {
        mutableStateOf(
            com.runwear.app.weariverun.ShareArtifactKind.MORNING_POSTER
        )
    }
    var shareFormat by remember {
        mutableStateOf(
            com.runwear.app.weariverun.ShareExportFormat.FEED
        )
    }
    var saveToPhotosBusy by remember { mutableStateOf(false) }

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
        wirStabilityGate.reset()
    }

    // Record the day's settled version at generation time. NOTE
    // (WS-3, abolished drivers): this effect RECORDS the entry; it
    // does not drive the element. The holder turns a recording into a
    // cue only when it is a genuinely new settled version (creation,
    // or a kit change) — a re-recording with the same kit (a weather
    // refresh, a hero URL landing, a location re-render) updates the
    // entry's content silently and never moves the element.
    //
    // RW-142 (Nov 8, 2026): the effect OFFERS; the WRITE runs on the
    // composition scope, not the effect's own lifetime. The pre-fix
    // shape ran the suspend DataStore write as the effect body
    // itself, so every key change (the churn that follows a commit —
    // sheet exit, hero resolution, forecast derivation) cancelled
    // the effect and could strand an admitted write before it
    // landed; a confirmed pick, unlike Now, is never re-solved, so
    // nothing re-offered it (owner device finding on vc41: a
    // confirmed 3 p.m. pick moved the screen to the picked hour's
    // solve while the entry kept the earlier selection). Launched
    // on screenScope (cancelled only when the composition leaves),
    // an admitted write survives every effect relaunch, and
    // WearIveRunStore.recordSettled performs the edit under
    // NonCancellable, so it always completes. Offers stay in launch
    // order on the main dispatcher and DataStore serializes the
    // edits, so the latest admitted solve is the final content.
    LaunchedEffect(
        uiState.outfit, uiState.weather, uiState.locationName,
        uiState.effectiveHeroUrl, uiState.heroImageUrl, uiState.heroFallbackUrl,
        uiState.isNowSelected, uiState.selectedDateTime
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
            // GEN-6 Wave B (RW-31): the day's canonical range for
            // the share record — the min/max of the hourly
            // forecast rows FOR the location's current date
            // (forecast dateTimes are kotlinx, in the location
            // frame; locationNow is java.time, same frame). Null
            // when the forecast carries no rows for that date —
            // never fabricated.
            // RW-FIXWAVE (Nov 7, 2026; future-days ruling): the
            // forecast range is the VIEWED day's, not always
            // today's — a future-day settle records that day's
            // range, held as that day's plan until the day arrives
            // and the ordinary session settle takes over.
            val todaysForecast = uiState.hourlyForecast.filter {
                it.dateTime.date.toJavaLocalDate() == uiState.selectedDateTime.toLocalDate()
            }
            val entry = WearIveRunDayEntry(
                // GEN-3 (WS-C): stamped with THIS process
                // session's id and the settle instant — the
                // session key replaces the entry in place on
                // every later change within the session.
                sessionId = com.runwear.app.weariverun.WearIveRunSession.currentId(),
                settledAtMillis = System.currentTimeMillis(),
                // RW-FIXWAVE (future-days ruling): the entry is
                // dated by the VIEWED day. It was previously
                // stamped with the location's today even for a
                // confirmed future-day pick, so a tomorrow
                // settle masqueraded as today's entry (the
                // Android half of RW-132's mismatch). A place
                // switch while viewing another day now moves
                // the session entry to the new place under that
                // viewed day's date, through the same in-place
                // session upsert.
                date = uiState.selectedDateTime.toLocalDate(),
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
                // RW-169 §7E.4: only a PRIMARY hero serve is
                // recorded as the entry's hero reference — a
                // fallback serve is a stand-in, never the record.
                heroRef = com.runwear.app.weariverun.recordableHeroRef(
                    uiState.heroImageUrl, uiState.heroFallbackUrl
                ),
                // RW-152 §3 (Nov 10, 2026): persist the hero's
                // DURABLE identity alongside the display URL —
                // the signed display form expires in an hour, the
                // durable form (Storage path / stable URL) is
                // what collection surfaces resolve at render.
                heroDurableRef = com.runwear.app.weariverun.HeroRefResolver
                    .durableRefFor(
                        com.runwear.app.weariverun.recordableHeroRef(
                            uiState.heroImageUrl, uiState.heroFallbackUrl
                        )
                    ),
                // GEN-6 Wave B (RW-31): the share record's
                // canonical fields — the day's actual + wind
                // from the same weather frame, the day range
                // from the forecast rows above, and the kit's
                // garment keys in wearing order. faceAnchor
                // stays null: no generation metadata source
                // exists yet (the same honest gap as iOS).
                actualF = weather.temperatureInFahrenheit,
                windMph = weather.windSpeed,
                dayLowF = todaysForecast.minOfOrNull { it.temperatureInFahrenheit },
                dayHighF = todaysForecast.maxOfOrNull { it.temperatureInFahrenheit },
                itemKeys = outfit.allItems.map { it.key }
            )
            // RW-169 §7E.1 (supersedes the immediate RW-142 write
            // in timing only — the write still leaves the effect's
            // lifetime on screenScope and recordSettled still runs
            // under NonCancellable): the candidate is OFFERED to
            // the stability gate. It reaches the record only after
            // its viewed frame (place, day/hour, kit, hero
            // reference) is unchanged for ≈2 s; a re-settle that
            // changes the frame restarts the window, and one that
            // changes nothing the frame names never writes at all.
            // The delayed completion rides screenScope so an
            // effect relaunch cannot strand an admitted frame.
            val frame = com.runwear.app.weariverun.ViewedFrame.of(entry)
            val nowMs = System.currentTimeMillis()
            val committedNow = wirStabilityGate.offer(frame, entry, nowMs)
            if (committedNow != null) {
                screenScope.launch {
                    wearIveRunStore.recordSettled(committedNow)
                }
            } else if (wirStabilityGate.pendingFrame == frame) {
                screenScope.launch {
                    kotlinx.coroutines.delay(
                        com.runwear.app.weariverun.StabilityGate.STABILITY_WINDOW_MS + 50L
                    )
                    // Re-offer ONLY if this frame is still the
                    // pending one — a newer frame owns the gate now.
                    if (wirStabilityGate.pendingFrame == frame) {
                        val committed = wirStabilityGate.offer(
                            frame, entry, System.currentTimeMillis()
                        )
                        if (committed != null) {
                            wearIveRunStore.recordSettled(committed)
                        }
                    }
                }
            }
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

    // RW-135 (Direction A-prime, APPROVED consolidated spec Nov 7,
    // 2026): the Wear I've Run presence is a floating MINI dock
    // (overlay, below) plus the expanded FOOTER section emitted as
    // the lists' last item. Both surfaces present the RECORDED
    // entry only (spec section 1.2): the shown line is
    // WearIveRunDockNarration over the holder's current recorded
    // entry, and it refreshes only at scroll rest (the spec
    // section 4.3 deferral — a settle that lands mid-scroll
    // cross-fades once the scroll rests, never concurrent with a
    // scroll-handoff; that refresh point is also the update
    // bounce's landing, section 4.3).
    val wearPresentState = wearIveRunState
    val wirPresent = wearPresentState != WearIveRunBarState.Absent
    val wirDayEntry by wearIveRunHolder.currentEntryFlow.collectAsState()
    // RW-169 §7E.2/§7E.3: THE presented snapshot — one immutable
    // object (entry + narration + entry line + bar projection,
    // built together from the same recorded entry). Mini, expanded
    // and (through the gated store) the collection TODAY card all
    // render ONLY this; it swaps atomically at scroll rest when
    // the recorded entry changes (the scroll-rest effect below).
    var wirPresented by remember {
        mutableStateOf<com.runwear.app.weariverun.PresentedSnapshot?>(null)
    }
    val wirShownLine: WearIveRunNarration? get() = wirPresented?.narration
    // RW-152 §1 (Nov 10, 2026): the expanded footer is a STATE of
    // the dock's overlay surface, not an in-flow list section.
    // wirExpanded is the one surface's expanded state: summoned by
    // the dock's expand control, dismissed by the panel's chevron,
    // a glide-up on the panel, or Back. It is never the rest state.
    var wirExpanded by remember { mutableStateOf(false) }
    // Reduce Motion (spec section 5) = animator duration scale 0.
    val wirReduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val wirDensity = LocalDensity.current
    val wirAnyScrolling = listState.isScrollInProgress || gridState.isScrollInProgress

    // -- Exit choreography state (spec sections 4.7/4.8) ------------
    // A frozen snapshot of the entry + line the surfaces exit
    // with. While an exit runs, the surfaces render the snapshot;
    // the DATA removal (holder TRASH + store removeByIdentity)
    // fires only when the longest running exit completes.
    data class WirExitSnapshot(
        val line: WearIveRunNarration,
        val barEntry: WearIveRunEntry,
        val dayEntry: WearIveRunDayEntry,
        val entryLine: String,
        val fromTrash: Boolean
    )
    var wirExit by remember { mutableStateOf<WirExitSnapshot?>(null) }
    var wirExitFromTrashDone by remember { mutableStateOf(false) }
    // The last live snapshot, kept so a session end (holder ->
    // Absent without a user trash) can exit with it (section 4.8).
    var wirLastSnapshot by remember { mutableStateOf<WirExitSnapshot?>(null) }
    // The dock's exit progress (fade + 26dp sink over 300ms exit
    // easing — section 4.7), driven here because the dock overlay
    // lives in this host.
    val wirDockExit = remember { Animatable(0f) }

    // Exit completion (spec section 4.7): the data removal fires
    // when the longest running exit completes — never before.
    // Idempotent: the first caller wins; later calls (the other
    // surface finishing, the host's own timer) see wirExit
    // already cleared and do nothing.
    val wirFinishExit: (WirExitSnapshot) -> Unit = { exit ->
        if (wirExit != null) {
            wirExit = null
            // RW-152 §1: an exit leaves the ONE surface in its
            // mini (rest) state — the expanded state is never a
            // rest state, including after a delete.
            wirExpanded = false
            if (exit.fromTrash) {
                wirExitFromTrashDone = true
                wearIveRunHolder.onEvent(WearIveRunElementEvent.TRASH)
                val entry = exit.dayEntry
                screenScope.launch {
                    wearIveRunStore.removeByIdentity(entry)
                }
            }
            screenScope.launch { wirDockExit.snapTo(0f) }
        }
    }

    // The surfaces render while the record is present OR while
    // an exit is running off its frozen snapshot (a session end
    // takes the holder Absent before the exit plays — spec
    // section 4.8).
    val wirSurfacesPresent = wirPresent || wirExit != null

    // RW-168: the direct save's execution — renders + verifies
    // today's card in the tracked selection through the Share
    // Studio's pipeline and writes it to the photo roll,
    // confirmed through the screen's existing snackbar host.
    fun launchSaveToPhotos(
        entry: WearIveRunDayEntry,
        kindSnapshot: com.runwear.app.weariverun.ShareArtifactKind,
        formatSnapshot: com.runwear.app.weariverun.ShareExportFormat
    ) {
        if (saveToPhotosBusy) return
        saveToPhotosBusy = true
        screenScope.launch {
            val saved = runCatching {
                com.runwear.app.weariverun.saveTodaysCardToPhotos(
                    context = context,
                    entry = entry,
                    kind = kindSnapshot,
                    format = formatSnapshot,
                    gender = uiState.genderPreference,
                    temperatureUnit = uiState.temperatureUnit,
                    heroResign = { path ->
                        makeItMeViewModel?.signedHeroUrlForPath(path)
                    },
                    heroRecovery =
                        com.runwear.app.weariverun.HeroRecoverySeam(
                            genderSegment =
                                uiState.genderPreference.name,
                            bankPaths = {
                                makeItMeViewModel?.bankHeroPaths()
                            }
                        ),
                    onHeroRecovered = { path ->
                        screenScope.launch {
                            wearIveRunStore.healHeroReference(
                                entry, path
                            )
                        }
                    }
                )
            }.getOrDefault(false)
            saveToPhotosBusy = false
            snackbarHostState.showSnackbar(
                if (saved) {
                    "Saved to photos"
                } else {
                    "Couldn't save the card — try again"
                }
            )
        }
    }
    // RW-168: API 26–28 needs the storage grant for the
    // MediaStore write (29+ is permission-free); the pending
    // save resumes when the grant lands.
    var pendingSaveEntry by remember {
        mutableStateOf<WearIveRunDayEntry?>(null)
    }
    val savePhotosPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission()
        ) { granted ->
            val pending = pendingSaveEntry
            pendingSaveEntry = null
            if (granted && pending != null) {
                launchSaveToPhotos(pending, shareKind, shareFormat)
            } else if (!granted) {
                screenScope.launch {
                    snackbarHostState.showSnackbar(
                        "Couldn't save the card — photo access is off"
                    )
                }
            }
        }

    // RW-168: the hero share button's doors (existing entries
    // only): the RW-158 sheet for the CURRENT day entry through
    // the same host state the footer's past-entry rows use; the
    // collection through its standing state; and the direct
    // save of today's card in the tracked selection.
    val heroShareActions = com.runwear.app.ui.components.HeroShareActions(
        onShareOutfit = {
            wirDayEntry?.let { wirFooterShareEntry = it }
        },
        onShareFromCollection = { showWearIveRunCollection = true },
        onSaveToPhotos = {
            val entry = wirDayEntry
            if (entry != null && !saveToPhotosBusy) {
                val needsGrant =
                    android.os.Build.VERSION.SDK_INT < 29 &&
                        androidx.core.content.ContextCompat
                            .checkSelfPermission(
                                context,
                                android.Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
                            ) != android.content.pm.PackageManager
                                .PERMISSION_GRANTED
                if (needsGrant) {
                    pendingSaveEntry = entry
                    savePhotosPermissionLauncher.launch(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                } else {
                    launchSaveToPhotos(entry, shareKind, shareFormat)
                }
            }
        },
        outfitEnabled = wirDayEntry != null
    )

    // The handoff (spec section 3): a PURE function of the
    // footer's position in the ACTIVE host. a = how far (dp) the
    // footer's top edge is above the viewport's bottom edge,
    // from layoutInfo (LazyList: viewportEndOffset - footerItem
    // .offset, px -> dp; the footer is the last item; when it is
    // not among visibleItemsInfo, a <= 0 -> fade 0; same via the
    // LazyGrid layoutInfo for the grid hosts).
    // RW-140: the fade STATE is kept as a State (not only a
    // delegated Float) because the dock's graphicsLayer reads it
    // in the DRAW phase (see the dock overlay below) — that is
    // what guarantees every intermediate fade value is drawn in
    // the frame the list moves, instead of waiting on a
    // recomposition that a fling can coalesce away (the vc41
    // defect: the dissolve existed as a function but its
    // intermediate values never reached the display).
    // RW-152 §1: the layout-position handoff is RETIRED for this
    // element — the expanded state no longer lives in the scroll
    // content, so there is no footer position to derive a fade
    // from. The dock surface morphs in place in its overlay (see
    // the overlay below); the fade the legacy graphicsLayer math
    // reads is therefore the constant rest value 0. (The pure
    // WearIveRunDockPolicy fade functions remain for the RW-140
    // harness, which builds its own host.)
    val wirFooterFadeState = remember {
        derivedStateOf { 0f }
    }
    val wirFooterFade by wirFooterFadeState
    // Whether the footer item is among the active host's visible
    // items (its content is composed and on screen) — the delete
    // choreography branches on this (spec section 4.7).
    // RW-152 §1: "the footer is composed" now means the expanded
    // panel state is composed in the overlay — the delete
    // choreography branches on it exactly as before (the panel's
    // fold is the longest exit and reports completion).
    val wirFooterComposed by remember {
        derivedStateOf { wirExpanded }
    }

    // Creation / update cue bookkeeping. A CREATION is an
    // Absent -> Present flip with the cue armed (the holder's Cue
    // state): the dock rises (or, handed off, the footer grows)
    // and the creation pulse plays on the visible surface. A
    // cold-start presence (restore prime -> Mini, no cue) gets
    // NO entrance and NO pulse. An UPDATE is a shown-line change
    // to a different record line for the SAME entry at scroll
    // rest: it fires the update bounce (section 4.3) on the
    // visible surface, and never doubles with the creation cue.
    var wirWasPresent by remember { mutableStateOf(false) }
    var wirCreationKey by remember { mutableIntStateOf(0) }
    var wirFooterGrowKey by remember { mutableIntStateOf(0) }
    var wirDockBounceKey by remember { mutableIntStateOf(0) }
    var wirFooterBounceKey by remember { mutableIntStateOf(0) }
    var wirPrevShownEntry by remember {
        mutableStateOf<WearIveRunDayEntry?>(null)
    }

    // RW-169 §7E.2/§7E.5: the presented snapshot swaps ATOMICALLY
    // at scroll rest — entry, narration, entry line and bar
    // projection together, never piecemeal, never mid-scroll
    // (spec §4.3's deferral). The update bounce keys off the
    // snapshot swap, as before.
    LaunchedEffect(wirDayEntry, wirAnyScrolling) {
        if (!wirAnyScrolling) {
            val newEntry = wirDayEntry
            val newSnapshot = newEntry?.let {
                com.runwear.app.weariverun.PresentedSnapshot.of(
                    it,
                    uiState.locationNow.toLocalDate(),
                    uiState.temperatureUnit
                )
            }
            val newLine = newSnapshot?.narration
            val oldLine = wirShownLine
            val oldEntry = wirPrevShownEntry
            wirPresented = newSnapshot
            wirPrevShownEntry = newEntry
            if (newLine != null && oldLine != null && newEntry != null &&
                oldEntry != null && newLine.text != oldLine.text &&
                wirExit == null &&
                WearIveRunCollectionLogic.sameIdentity(oldEntry, newEntry)
            ) {
                // The settle's landing (spec section 4.3): the
                // update bounce fires on the surface the user can
                // see — the dock while it is not handed off, the
                // footer's header zone once it is — so only that
                // surface's key increments. The first line of a
                // creation never reaches here (oldLine is null):
                // the creation cue owns that moment alone, and
                // the two never double.
                // RW-152 §1: the visible state of the ONE
                // surface takes the bounce — the panel while
                // expanded, the dock otherwise.
                if (wirExpanded) {
                    wirFooterBounceKey += 1
                } else {
                    wirDockBounceKey += 1
                }
            }
        }
    }

    // Absent -> Present / Present -> Absent flips: creation
    // arming, snapshot keeping, session-end exit (section 4.8).
    LaunchedEffect(wirPresent) {
        if (wirPresent && !wirWasPresent) {
            // A flip up. Armed as a creation only when the holder
            // is in its Cue presentation with a fresh sequence —
            // a restore (Mini at first composition) is a
            // cold-start presence and gets no entrance.
            if (wearIveRunState is WearIveRunBarState.Cue &&
                wearCueSequence > 0
            ) {
                wirCreationKey = wearCueSequence
                // Panel grow (section 4.1): only when the
                // expanded state is the visible one; otherwise
                // the creation inserts silently.
                if (wirExpanded) {
                    wirFooterGrowKey = wearCueSequence
                }
            }
        }
        if (!wirPresent && wirWasPresent) {
            // A flip down NOT driven by our trash exit (that path
            // sets wirExitFromTrashDone as it completes): this is
            // a session end (section 4.8) — the record has already
            // passed to the collection in the holder/store, so
            // only the exit choreography runs here, minus the
            // haptic, off the last live snapshot.
            if (wirExit == null && !wirExitFromTrashDone) {
                wirLastSnapshot?.let { snap ->
                    wirExit = snap.copy(fromTrash = false)
                }
            }
            wirExitFromTrashDone = false
        }
        wirWasPresent = wirPresent
    }

    // Keep the last live snapshot current while present — built
    // from the PRESENTED snapshot alone (RW-169 §7E.3), so an exit
    // freezes exactly what the surfaces were showing.
    LaunchedEffect(wirPresented) {
        val snapshot = wirPresented
        if (snapshot != null) {
            wirLastSnapshot = WirExitSnapshot(
                line = snapshot.narration,
                barEntry = snapshot.barEntry,
                dayEntry = snapshot.entry,
                entryLine = snapshot.entryLine,
                fromTrash = false
            )
        }
    }

    // The dock's exit animation + the exit completion for the
    // paths where the footer cannot report it (footer off-screen
    // at trash: dock exit only, 300ms — section 4.7; session end
    // with the footer off-screen likewise). When the footer IS
    // composed, its fold is the longest exit and IT reports
    // completion via onExitFinished; this effect then only drives
    // the dock's own fade/sink.
    LaunchedEffect(wirExit) {
        val exit = wirExit ?: return@LaunchedEffect
        wirDockExit.snapTo(0f)
        if (wirReduceMotion) {
            wirDockExit.animateTo(1f, tween(durationMillis = 200))
        } else {
            wirDockExit.animateTo(
                1f,
                tween(
                    durationMillis = WearIveRunDockPolicy.DELETE_DOCK_EXIT_MS,
                    easing = WearIveRunDockPolicy.EXIT_EASING
                )
            )
        }
        if (!wirFooterComposed) {
            wirFinishExit(exit)
        }
    }

    // The glides (spec section 4.4): duration from distance under
    // the glide easing; the handoff runs off the glide's own
    // scroll position (it is position-derived — there is no
    // separate dock animation). A user touch cancels the glide
    // (Compose scroll mutual exclusion). Reduce Motion: instant
    // jumps (section 5).
    // RW-152 §1 (supersedes RW-135 §4.4's scroll glides for this
    // element): expansion and collapse are IN-PLACE state changes
    // of the overlay surface — there is no footer in the scroll
    // content to glide to, and dismissal restores the page exactly
    // (scroll position is never touched). The names are kept
    // because every control wired to them (dock expand, panel
    // chevron) means the same thing it always meant.
    val wirGlideToFooter: () -> Unit = { wirExpanded = true }
    val wirGlideFromFooter: () -> Unit = { wirExpanded = false }
    // Back dismisses the expanded state (never the page).
    androidx.activity.compose.BackHandler(
        enabled = wirExpanded && wirExit == null
    ) {
        wirExpanded = false
    }

    // One tap, NO confirmation (the settled delete rule). The
    // exit choreography runs FIRST off a frozen snapshot (spec
    // section 4.7); the data removal — holder TRASH + the store
    // delete by identity — fires when the longest running exit
    // completes (wirFinishExit). The collection copy is a
    // separate surface (RW-122) and is untouched by this delete.
    val wirTrash: () -> Unit = {
        if (wirExit == null) {
            wirLastSnapshot?.let { snap ->
                wirExit = snap.copy(fromTrash = true)
            }
        }
    }
    // The creation key arms the pulse on exactly one surface at
    // a time (dock below fade 1, footer at fade 1); clear it once
    // the cue window has passed so a later scroll into the other
    // surface cannot replay the pulse.
    LaunchedEffect(wirCreationKey) {
        if (wirCreationKey > 0) {
            kotlinx.coroutines.delay(2_000)
            wirCreationKey = 0
        }
    }

    // RW-169 §7A/§7E.3: the ONE overlay surface — the shared-layout
    // geometry morph (WearIveRunMorphSurface), rendering ONLY the
    // presented snapshot while present, the frozen exit snapshot
    // while an exit runs (spec section 4.7). The two-layout
    // AnimatedContent crossfade + SizeTransform that arbitrated
    // WearIveRunDock vs WearIveRunFooterSection here is retired;
    // those composables remain for their in-flow/test seats.
    val wirFooterRender: @Composable () -> Unit = {
        val exit = wirExit
        val presented = wirPresented
        val line = exit?.line ?: presented?.narration
        val dayEntry = exit?.dayEntry ?: presented?.entry
        val barEntry = exit?.barEntry ?: presented?.barEntry
        val entryLine = exit?.entryLine ?: presented?.entryLine
        if (line != null && dayEntry != null && barEntry != null &&
            entryLine != null
        ) {
            com.runwear.app.weariverun.WearIveRunMorphSurface(
                narration = line,
                entry = barEntry,
                entryLine = entryLine,
                expanded = wirExpanded,
                onExpand = wirGlideToFooter,
                onCollapse = wirGlideFromFooter,
                onDelete = wirTrash,
                onOpenCollection = { showWearIveRunCollection = true },
                // RW-166: the thumbnail resolves through the
                // recovering HELD resolver (RW-169 §7E.4).
                heroResign = { path ->
                    makeItMeViewModel?.signedHeroUrlForPath(path)
                },
                heroRecovery = com.runwear.app.weariverun.HeroRecoverySeam(
                    genderSegment = uiState.genderPreference.name,
                    bankPaths = { makeItMeViewModel?.bankHeroPaths() }
                ),
                onHeroRecovered = { entry, path ->
                    screenScope.launch {
                        wearIveRunStore.healHeroReference(entry, path)
                    }
                },
                // RW-158 (user ruling, Nov 10, 2026): a PAST
                // entry's row opens its share studio; today's
                // entry keeps the collection doorway.
                onEntryTap = if (
                    dayEntry.date < uiState.locationNow.toLocalDate()
                ) {
                    { wirFooterShareEntry = dayEntry }
                } else {
                    null
                },
                cueKey = wirCreationKey,
                cueArmed = exit == null && wirCreationKey > 0,
                // One surface, one bounce channel: the host keys
                // dock/footer bounce separately by visibility;
                // the morph consumes their sum.
                bounceKey = wirDockBounceKey + wirFooterBounceKey,
                entranceKey = wirCreationKey,
                exiting = exit != null,
                reduceMotion = wirReduceMotion,
                onExitFinished = {
                    wirExit?.let { wirFinishExit(it) }
                },
                maxExpandedHeightDp = WirOverlayPanelPolicy
                    .maxPanelHeightDp(
                        LocalConfiguration.current.screenHeightDp.toFloat()
                    ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            WearIveRunDockPolicy.DOCK_INSET_DP.dp
                    )
                    .navigationBarsPadding()
                    .padding(
                        bottom =
                            WearIveRunDockPolicy.DOCK_BOTTOM_DP.dp
                    )
            )
        }
    }

    // The footer's content (spec section 2.2): header + the
    // record's line + the full entry row + the collection
    // doorway. FULL-BLEED — no call-site inset in either host
    // (the section's own background spans edge to edge; its
    // content keeps the 16dp inset internally). The grid hosts'
    // content padding insets their items, so the grid lambda
    // breaks the footer out of that padding by the pane's

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

                // RW-135: the content branch is a Box — the scroll
                // area fills it and the MINI dock overlays its bottom
                // (the ruled floating form); the expanded form is the
                // footer section inside the scroll content itself.
                // The scaffold's bottom inset, consumed ONCE here
                // for the dock-clearance system (RW-147 boundary +
                // RW-152 band-edge fade); the three scroll
                // containers consume their own (HomeCompositionPin).
                val wirScaffoldBottomInsetDp =
                    scaffoldContentPadding.calculateBottomPadding().value
                Box(modifier = Modifier.fillMaxSize()) {
                // RW-156 REOPENED (Nov 11, 2026): the dock
                // residence's ground. RW-147's layout boundary
                // truncates the Surface-painted scroll area at
                // the dock band's top edge; below it the root
                // BoxWithConstraints' Background (#F5F1E8) showed
                // through as a full-width, straight-edged cream
                // strip behind the translucent dock — the
                // rectangular block Sol reported on 1.2.30 and
                // again on 1.2.32 (the 1.2.31 by-construction
                // removal had only removed the in-flow footer;
                // this strip is a different surface). The
                // residence now paints the SHEET's ground
                // (Surface) for exactly the band the boundary
                // reserves, so the sheet reads continuous to the
                // screen's bottom edge and the dock floats over
                // one uniform ground; the boundary itself (no
                // readable content ever rests under the dock) is
                // unchanged. In the EXPANDED layout the residence
                // belongs to the scrolling pane only, mirroring
                // the dock overlay's own inset.
                if (wirSurfacesPresent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (layout == AdaptiveLayout.EXPANDED) {
                                    Modifier.padding(
                                        start = spec.heroPaneWidth ?: 400.dp
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .height(
                                (DockClearancePolicy.dockBandDp() +
                                    wirScaffoldBottomInsetDp).dp
                            )
                            .background(RunWearColors.Surface)
                    )
                }
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    // RW-147: while the dock is present, the scroll
                    // area's layout ends at the dock band's top
                    // edge — list content can never rest (or be
                    // scrolled) underneath the dock, in any layout,
                    // at any scroll position. The dock floats over
                    // the page ground below the list instead. The
                    // RW-135 handoff is untouched: its fade derives
                    // from the lists' own layoutInfo, whose viewport
                    // is now this bounded one, so the dissolve runs
                    // exactly as specified as the footer rises
                    // through the clear zone.
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (wirSurfacesPresent) {
                                Modifier.padding(
                                    bottom = (
                                        DockClearancePolicy.dockBandDp() +
                                            wirScaffoldBottomInsetDp
                                        ).dp
                                )
                            } else {
                                Modifier
                            }
                        )
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
                                    // RW-135 spec section 2.2: when
                                    // the footer is present it is the
                                    // page's foot — no sheet renders
                                    // below it at max scroll, so the
                                    // trailing bottom padding is 0
                                    // (the footer's own 32dp bottom
                                    // padding carries the clearance).
                                    // RW-147: the dock clearance is
                                    // carried by the scroll area's
                                    // layout boundary (see the
                                    // PullToRefreshBox below), not
                                    // by trailing padding.
                                    bottom = if (wirSurfacesPresent) 16.dp
                                        else 60.dp + scaffoldContentPadding.calculateBottomPadding()
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
                                            shareActions = heroShareActions,
                                            onWeatherPillClick = { type -> weatherDetailType = type },
                                            onFlagClick = onAnyFlagClick,
                                            heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                            onCycleHero = onCycleHero,
                                            heroPill = heroPill,
                                            onHeroPillAction = { showMakeItMePaywall = true },
                                            onPillBounds = { rect ->
                                                wirPillAnchorBounds = rect
                                            },
                                            pillOverlayActive = wirExpanded,
                                            onHeroPhotoClick = {
                                                if (uiState.effectiveHeroUrl != null) {
                                                    showHeroViewer = true
                                                }
                                            },
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
                                // RW-101 (Sol's direction, Nov 6,
                                // 2026): the "Making your looks" band
                                // sits DIRECTLY UNDER THE HERO — a
                                // Premium subscriber sees what is
                                // happening the moment the screen
                                // opens. This supersedes the GEN-5
                                // Addendum-4 seat (below the outfit
                                // area) in every layout.
                                if (convergenceBand != null) {
                                    item {
                                        com.runwear.app.makeitme.ConvergenceBand(
                                            content = convergenceBand,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                        )
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
                                            freeGenerationsUsed = makeItMeState.freeGenerationsUsed,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-4: the day-chart glance band.
                                if (!dayChartModel.isEmpty) {
                                item { dayChartBandContent() }
                                }
                                // RW-144: the complete detailed
                                // weather block below the glance band.
                                item { detailedWeatherBlockContent() }
                                // GEN-5 (C-3): Pro Tip + feedback,
                                // below the chart.
                                homeTrailingListItems(
                                    uiState = uiState,
                                    onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) },
                                    // RW-140: the LIST host takes the
                                    // plain footer content — this
                                    // LazyColumn has NO horizontal
                                    // content padding to break out
                                    // of. (vc41 passed the GRID
                                    // variant here; its breakout
                                    // layout shifted the whole
                                    // section left by pagePadding
                                    // and cropped every footer
                                    // element at the left edge on
                                    // Sol's phone — his Nov 8, 2026
                                    // recording.)
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
                                    // RW-135 spec section 2.2: as
                                    // the list host — no sheet below
                                    // the footer when it is present.
                                    bottom = if (wirSurfacesPresent) 16.dp
                                        else 60.dp + scaffoldContentPadding.calculateBottomPadding()
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
                                            shareActions = heroShareActions,
                                            onWeatherPillClick = { type -> weatherDetailType = type },
                                            onFlagClick = onAnyFlagClick,
                                            heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                            onCycleHero = onCycleHero,
                                            heroPill = heroPill,
                                            onHeroPillAction = { showMakeItMePaywall = true },
                                            onPillBounds = { rect ->
                                                wirPillAnchorBounds = rect
                                            },
                                            pillOverlayActive = wirExpanded,
                                            onHeroPhotoClick = {
                                                if (uiState.effectiveHeroUrl != null) {
                                                    showHeroViewer = true
                                                }
                                            },
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
                                // RW-101 (Sol, Nov 6, 2026): the
                                // band directly under the hero.
                                if (convergenceBand != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        com.runwear.app.makeitme.ConvergenceBand(
                                            content = convergenceBand,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
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
                                            freeGenerationsUsed = makeItMeState.freeGenerationsUsed,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                // GEN-4: the day-chart glance band.
                                if (!dayChartModel.isEmpty) {
                                item(span = { GridItemSpan(maxLineSpan) }) { dayChartBandContent() }
                                }
                                // RW-144: the complete detailed
                                // weather block below the glance band.
                                item(span = { GridItemSpan(maxLineSpan) }) { detailedWeatherBlockContent() }
                                // GEN-5 (C-3): Pro Tip + feedback,
                                // below the chart.
                                homeTrailingGridItems(
                                    uiState = uiState,
                                    onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) },
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
                                            shareActions = heroShareActions,
                                        onFlagClick = onAnyFlagClick,
                                        heroFlagEnabled = flaggedHeroUrl != uiState.heroImageUrl,
                                        onCycleHero = onCycleHero,
                                        heroPill = heroPill,
                                        onHeroPillAction = { showMakeItMePaywall = true },
                                        onHeroPhotoClick = {
                                            if (uiState.effectiveHeroUrl != null) {
                                                showHeroViewer = true
                                            }
                                        },
                                        onDateClick = { showDatePicker = true },
                                        onTimeClick = { showTimePicker = true },
                                        onNowClick = onResetToNow,
                                        // HERO REDESIGN: the weather
                                        // line seats on the hero
                                        // pane's foot (in HeroSection).
                                        onWeatherPillClick = { type -> weatherDetailType = type },
                                        modifier = Modifier.fillMaxSize(),
                                        fillHeight = true,
                                        pillAtHeroFoot = true
                                    )
                                    if (uiState.personalLookStatus is
                                        com.runwear.app.makeitme.PersonalLookStatus.Failed
                                    ) {
                                        com.runwear.app.makeitme.MakeItMeFailedLine(
                                            onRetry = onRetryPersonalLook,
                                            // HERO REDESIGN: the pane's
                                            // foot now carries the
                                            // straddle pill + weather
                                            // line stack — the failed
                                            // line seats above it.
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 132.dp)
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
                                        // RW-135 spec section 2.2:
                                        // no sheet below the footer
                                        // when it is present.
                                        bottom = if (wirSurfacesPresent) 16.dp
                                        else 60.dp + scaffoldContentPadding.calculateBottomPadding()
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(spec.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // HERO REDESIGN (Nov 9, 2026):
                                    // the right pane no longer opens
                                    // with the sheet block — the
                                    // weather line moved onto the
                                    // hero pane's foot.
                                    // RW-101 (Sol, Nov 6, 2026):
                                    // the band tops the right pane,
                                    // directly under the hero unit
                                    // (the sheet block above it).
                                    if (convergenceBand != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            com.runwear.app.makeitme.ConvergenceBand(
                                                content = convergenceBand,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
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
                                            freeGenerationsUsed = makeItMeState.freeGenerationsUsed,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    // GEN-4: the day-chart glance band.
                                    if (!dayChartModel.isEmpty) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { dayChartBandContent() }
                                    // RW-144: the complete detailed weather block below the glance band.
                                    item(span = { GridItemSpan(maxLineSpan) }) { detailedWeatherBlockContent() }
                                    }
                                    // GEN-5 (C-3): Pro Tip + feedback,
                                    // below the chart.
                                    homeTrailingGridItems(
                                        uiState = uiState,
                                        onFeedbackClick = { onOpenFeedback(com.runwear.app.feedback.FeedbackCategory.RECOMMENDATION_OFF) },
                                    )
                                }
                            }
                        }
                    }
                }
                // RW-152 remainder (Nov 10, 2026): the dock
                // band's EDGE TREATMENT. RW-147's layout boundary
                // already guarantees nothing readable rests UNDER
                // the dock, but at initial rest a portrait outfit
                // row can be cut mid-description exactly at the
                // boundary, reading as a graze rather than a
                // scroll edge. A short fade of the sheet ground
                // over the bounded area's bottom edge makes the
                // cut read as the list's designed edge. Purely
                // decorative: it changes no layout, reserves no
                // space, and touches neither the RW-147 clearance
                // (the boundary is unchanged) nor the RW-149
                // first-viewport contract (header + first item
                // sit far above this strip).
                if (wirSurfacesPresent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                bottom = (
                                    DockClearancePolicy.dockBandDp() +
                                        wirScaffoldBottomInsetDp
                                    ).dp
                            )
                            .height(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        RunWearColors.Surface
                                    )
                                )
                            )
                    )
                }
                // RW-135: the MINI dock overlay — inset 12,
                // lifted 14 above the bottom safe edge (over the
                // bottom navigation), radius 16, translucent card,
                // soft shadow. Driven CONTINUOUSLY by the handoff
                // fade (spec section 3): graphicsLayer alpha /
                // translationY / scale are pure functions of the
                // footer's scroll position, evaluated in the draw
                // phase (RW-140) — no binary visibility,
                // no AnimatedVisibility. Hit-testing is off at
                // fade >= 0.5; at fade >= 1 the dock leaves the
                // semantics tree. Reduce Motion (spec section 5):
                // the handoff steps at the window's midpoint with
                // a single 160ms crossfade, no sink/scale. During
                // an exit (sections 4.7/4.8) the snapshot's line
                // renders and the dock's own exit (fade + 26dp
                // sink, 300ms exit) multiplies in.
                if (wirSurfacesPresent) {
                    val exitSnap = wirExit
                    val line = exitSnap?.line ?: wirShownLine
                    if (line != null) {
                        val fade = wirFooterFade
                        val steppedOpacity = animateFloatAsState(
                            targetValue = if (fade < 0.5f) 1f else 0f,
                            animationSpec = tween(160),
                            label = "wirDockHandoffStep"
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // RW-147: in the EXPANDED two-pane
                                // layout the dock belongs to the
                                // scrolling right pane only. The
                                // full-width form floated it over
                                // the hero pane's foot as well —
                                // the straddle pill and the hero's
                                // Actual line sat under the band at
                                // rest, and the hero pane never
                                // scrolls, so they could never be
                                // scrolled clear (owner device
                                // pass, Nov 9, 2026).
                                .then(
                                    if (layout == AdaptiveLayout.EXPANDED) {
                                        Modifier.padding(
                                            start = spec.heroPaneWidth ?: 400.dp
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (fade >= 1f && exitSnap == null) {
                                        Modifier.clearAndSetSemantics {}
                                    } else {
                                        Modifier
                                    }
                                )
                                .graphicsLayer {
                                    // RW-140: the handoff transform
                                    // is evaluated HERE, in the draw
                                    // phase, reading the fade state
                                    // directly — every scroll frame
                                    // draws its own alpha / sink /
                                    // scale in the same frame the
                                    // list moves. (In vc41 these were
                                    // composition locals captured by
                                    // this block; a coalesced
                                    // recomposition during a fling
                                    // dropped the dissolve's
                                    // intermediate values wholesale,
                                    // and the dock and footer read as
                                    // two distinct elements — Sol's
                                    // Nov 8, 2026 device finding.)
                                    val drawFade = wirFooterFadeState.value
                                    val exitP = wirDockExit.value
                                    val handoffAlpha = if (wirReduceMotion) {
                                        steppedOpacity.value
                                    } else {
                                        WearIveRunDockPolicy
                                            .dockOpacity(drawFade)
                                    }
                                    alpha = handoffAlpha * (1f - exitP)
                                    translationY =
                                        ((if (wirReduceMotion) 0f
                                        else WearIveRunDockPolicy
                                            .dockSinkDp(drawFade)) +
                                            WearIveRunDockPolicy
                                                .DELETE_DOCK_SINK_DP * exitP) *
                                            wirDensity.density
                                    val s = (if (wirReduceMotion) 1f
                                    else WearIveRunDockPolicy
                                        .dockScale(drawFade))
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            // RW-152 §1+§2 (Nov 10, 2026): ONE
                            // surface, two states, in this overlay
                            // residence. The mini dock and the
                            // expanded panel morph in place —
                            // height animation + content cross-fade
                            // (the RW-135 §3 dissolve, the RW-154
                            // acceptance) — never two elements
                            // swapping, and the panel is
                            // CONTENT-SIZED: its height is its
                            // content's height, capped at the
                            // viewport minus the top chrome zone
                            // (WirOverlayPanelPolicy); when the cap
                            // binds the panel scrolls internally
                            // and the doorway stays reachable at
                            // its foot. Dismissal: chevron, Back
                            // (BackHandler above), or a glide-up
                            // on the panel.
                            // RW-169 §7A: the morph surface — ONE
                            // layout interpolating between the mini
                            // and expanded rest geometries in this
                            // same overlay residence. A glide-up on
                            // the expanded form dismisses it (the
                            // seat's standing gesture).
                            var glideUpPx by remember {
                                mutableFloatStateOf(0f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                glideUpPx = 0f
                                            },
                                            onDragEnd = {
                                                if (wirExpanded &&
                                                    glideUpPx <
                                                    -48f * wirDensity.density
                                                ) {
                                                    wirGlideFromFooter()
                                                }
                                            },
                                            onVerticalDrag = { _, amount ->
                                                if (amount < 0f) {
                                                    glideUpPx += amount
                                                }
                                            }
                                        )
                                    }
                            ) {
                                wirFooterRender()
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
            uiState.isLoading -> HomeSkeletonScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
            uiState.error != null -> HeroErrorScreen(uiState.error, onRefresh)
            else -> HomeSkeletonScreen(
                statusText = loadingStatusText(uiState.hasLocationPermission, uiState.locationName)
            )
        }

        // RW-169 §7D: the straddle pill's overlay-host copy — while
        // the WIR panel is expanded, the pill renders HERE (above
        // the panel in composition order and z), seated from the
        // in-list pill's MEASURED window bounds, whole and
        // unbisected. Same single heroPill value as the in-list
        // copy, which yields (alpha 0) while this copy is active.
        if (wirExpanded && heroPill != null &&
            wirPillAnchorBounds != null
        ) {
            val anchorRect = wirPillAnchorBounds!!
            val pillDensity = LocalDensity.current
            val pillWidthDp = with(pillDensity) { anchorRect.width.toDp() }
            val pillHeightDp = with(pillDensity) { anchorRect.height.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            anchorRect.left.roundToInt(),
                            anchorRect.top.roundToInt()
                        )
                    }
                    .size(pillWidthDp, pillHeightDp)
                    .zIndex(10f),
                contentAlignment = Alignment.TopCenter
            ) {
                com.runwear.app.ui.components.HeroPill(
                    content = heroPill,
                    onAction = { showMakeItMePaywall = true }
                )
            }
        }

        // Hero-flag confirmation ("Thanks — we'll review this photo").
        // GEN-5 (RW-29 cream map): snackbars render in the app's
        // own tokens — the default takes the inverseSurface role
        // and read as a foreign dark bar on the cream sheet.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // RW-147: while the dock is present the snackbar
                // rests above the dock's band, not beneath it.
                .padding(
                    bottom = if (wirSurfacesPresent) {
                        (DockClearancePolicy.dockBandDp() + 8f).dp
                    } else {
                        24.dp
                    }
                )
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
                gender = uiState.genderPreference,
                temperatureUnit = uiState.temperatureUnit,
                // RW-122: collection deletes land at the store by
                // identity / by place group.
                onDeleteEntry = { entry ->
                    screenScope.launch { wearIveRunStore.removeByIdentity(entry) }
                },
                onDeletePlace = { place ->
                    screenScope.launch { wearIveRunStore.removePlace(place) }
                },
                // RW-133: an entry's stored photograph, full-screen.
                onViewPhoto = { entry -> heroViewerEntry = entry },
                // RW-152 §3: re-sign personal hero Storage paths
                // at render (the resolver's seam).
                heroResign = { path ->
                    makeItMeViewModel?.signedHeroUrlForPath(path)
                },
                // RW-166: the bank re-link recovery — an entry
                // that settled with no hero reference recovers
                // its own banked hero and heals onto it.
                heroRecovery = com.runwear.app.weariverun.HeroRecoverySeam(
                    genderSegment = uiState.genderPreference.name,
                    bankPaths = { makeItMeViewModel?.bankHeroPaths() }
                ),
                onHeroRecovered = { entry, path ->
                    screenScope.launch {
                        wearIveRunStore.healHeroReference(entry, path)
                    }
                },
                shareInitialKind = shareKind,
                shareInitialFormat = shareFormat,
                onShareKindSelected = { shareKind = it },
                onShareFormatSelected = { shareFormat = it }
            )
        }

        // RW-158: the share studio opened straight from the
        // footer's past-entry row — the same sheet the collection
        // opens, with the same resolver seam and photo door.
        wirFooterShareEntry?.let { entry ->
            ShareStudioSheet(
                entry = entry,
                gender = uiState.genderPreference,
                temperatureUnit = uiState.temperatureUnit,
                onDismiss = { wirFooterShareEntry = null },
                heroResign = { path ->
                    makeItMeViewModel?.signedHeroUrlForPath(path)
                },
                heroRecovery = com.runwear.app.weariverun.HeroRecoverySeam(
                    genderSegment = uiState.genderPreference.name,
                    bankPaths = { makeItMeViewModel?.bankHeroPaths() }
                ),
                onHeroRecovered = { path ->
                    screenScope.launch {
                        wearIveRunStore.healHeroReference(entry, path)
                    }
                },
                onViewPhoto = { heroViewerEntry = it },
                initialKind = shareKind,
                initialFormat = shareFormat,
                onKindSelected = { shareKind = it },
                onFormatSelected = { shareFormat = it }
            )
        }
    }

    // RW-133: the full-screen hero viewer — the home hero's
    // stored photograph, fitted as-is, one quiet caption line.
    if (showHeroViewer) {
        val heroRef = uiState.effectiveHeroUrl
        if (heroRef != null) {
            com.runwear.app.ui.components.HeroViewer(
                imageRef = heroRef,
                captionItems = uiState.outfit?.allItems?.map { it.displayName }
                    ?: emptyList(),
                onDismiss = { showHeroViewer = false }
            )
        }
    }
    // RW-133: a collection entry's stored photograph.
    heroViewerEntry?.let { entry ->
        // RW-152 §3: the viewer loads through the resolver — a
        // stored signed URL is re-signed by path, never loaded
        // dead after its hour.
        val ref = com.runwear.app.weariverun.rememberResolvedHeroUrl(
            entry,
            resign = { path ->
                makeItMeViewModel?.signedHeroUrlForPath(path)
            },
            recovery = com.runwear.app.weariverun.HeroRecoverySeam(
                genderSegment = uiState.genderPreference.name,
                bankPaths = { makeItMeViewModel?.bankHeroPaths() }
            ),
            onHeroRecovered = { path ->
                screenScope.launch {
                    wearIveRunStore.healHeroReference(entry, path)
                }
            }
        )
        if (ref != null) {
            com.runwear.app.ui.components.HeroViewer(
                imageRef = ref,
                captionItems = entry.items,
                onDismiss = { heroViewerEntry = null }
            )
        }
    }

    // Date Picker
    if (showDatePicker) {
        DatePickerSheet(
            selectedDate = pickerWorkingDate
                ?: uiState.selectedDateTime.toLocalDate(),
            today = uiState.locationNow.toLocalDate(),
            onSelectDate = {
                if (showTimePicker) {
                    // RW-131: hosted INSIDE the picker session —
                    // the date changes the working day in place
                    // and ONLY the date sheet closes. The time
                    // sheet stays open on the new day; nothing is
                    // committed and home does not re-solve. (This
                    // supersedes RW-53's close-everything wiring
                    // for the in-picker path only.)
                    pickerWorkingDate = it
                    showDatePicker = false
                } else {
                    // Standalone Choose-a-Day (RW-53, unchanged):
                    // a confirmed date change re-solves home.
                    onDateSelected(it)
                    showDatePicker = false
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time Picker — GEN-4: the chart sheet. Confirm commits the
    // drafted (date, hour) as ONE selection (RW-131); confirming
    // the untouched NOW hour keeps the NOW state
    // (TimePickerDraftPolicy's pinned interpretation) and commits
    // nothing. Cancel simply dismisses — the draft was
    // sheet-local, so the pre-open selection stands.
    if (showTimePicker) {
        // RW-131: the sheet's model follows the WORKING day while
        // the session is open (the glance band keeps the
        // committed day's model — DayChartBuilder is a pure
        // function of (hourly, date), so the committed-day model
        // is bit-identical when fed the committed date).
        val pickerModel = remember(
            uiState.hourlyForecast, pickerWorkingDate,
            uiState.selectedDateTime, uiState.comfortPreference,
            uiState.sunTimes, uiState.weather, uiState.isNowSelected,
            uiState.locationNow
        ) {
            val workingDate = pickerWorkingDate
                ?: uiState.selectedDateTime.toLocalDate()
            if (workingDate == uiState.selectedDateTime.toLocalDate()) {
                dayChartModel
            } else {
                com.runwear.app.ui.components.DayChartBuilder.build(
                    hourly = uiState.hourlyForecast,
                    date = workingDate,
                    comfort = uiState.comfortPreference,
                    sunTimes = uiState.sunTimes
                )
            }
        }
        TimePickerSheet(
            model = pickerModel,
            selectedHour = uiState.selectedDateTime.hour,
            locationNow = uiState.locationNow,
            isNowSelected = uiState.isNowSelected,
            temperatureUnit = uiState.temperatureUnit,
            locationName = uiState.locationName,
            onConfirm = { confirm ->
                if (!confirm.keepNow) {
                    val commitDate = confirm.date
                        ?: pickerWorkingDate
                        ?: uiState.selectedDateTime.toLocalDate()
                    if (onPickerCommit != null) {
                        onPickerCommit(commitDate, confirm.hour, false)
                    } else {
                        // Render-proof fallback: sequential path.
                        if (commitDate != uiState.selectedDateTime.toLocalDate()) {
                            onDateSelected(commitDate)
                        }
                        onTimeSelected(confirm.hour)
                    }
                }
                pickerWorkingDate = null
                showTimePicker = false
            },
            onDismiss = {
                pickerWorkingDate = null
                showTimePicker = false
            },
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
            onDismiss = {
                showMakeItMePaywall = false
                // RW-79: returning to Home from the paywall re-reads
                // the look state (the purchase path below already
                // re-resolves the hero wholesale).
                onPersonalLookRecheck()
            },
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
        // RW-141: the sheet closes on a Current location request's
        // SUCCESS — not blindly at tap time. A request this sheet
        // session watched start (Locating) closes the sheet when
        // it succeeds, and the outcome is acknowledged so the next
        // open starts quiet. A success that landed while the sheet
        // was dismissed is acknowledged WITHOUT closing (the place
        // already applied in the ViewModel); a failure never
        // closes the sheet — the row carries its message.
        var pickerSawLocating by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.currentLocationRequest) {
            when (uiState.currentLocationRequest) {
                CurrentLocationRequestState.Locating -> pickerSawLocating = true
                CurrentLocationRequestState.Succeeded -> {
                    if (pickerSawLocating) {
                        showLocationPicker = false
                    }
                    onCurrentLocationRequestHandled()
                }
                else -> Unit
            }
        }
        LocationPickerSheet(
            currentLocation = uiState.locationName,
            currentLocationRequest = uiState.currentLocationRequest,
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
                // RW-141: forward the tap only. The request lives
                // in the ViewModel; the effect above closes this
                // sheet when (and only when) it succeeds.
                onUseCurrentLocation()
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
 * layouts). RW-144: the photo zone now also carries the date/time
 * chip and the feels-like block (HeroSection's own overlays); the
 * Make It Me pill is seated by the CALLER — straddling the
 * hero/sheet boundary in the stacked layouts ([HeroStackedUnit]),
 * or at the hero pane's foot in the EXPANDED split
 * ([pillAtHeroFoot]).
 */
@Composable
private fun HeroSectionContent(
    uiState: MainUiState,
    contentVisible: Boolean,
    scrollOffset: Float,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    /** RW-168: the hero share button + menu doors. */
    shareActions: com.runwear.app.ui.components.HeroShareActions? = null,
    onFlagClick: () -> Unit,
    heroFlagEnabled: Boolean,
    onCycleHero: () -> Unit = {},
    /** GEN-3 A-HERO (WS-A(2)): the hero pill's content + its action. */
    heroPill: com.runwear.app.ui.components.HeroPillContent? = null,
    onHeroPillAction: () -> Unit = {},
    /** RW-133: tapping the photo opens the full-screen viewer. */
    onHeroPhotoClick: () -> Unit = {},
    /** RW-144: the chip's doors (date picker / hourly chart / Now). */
    onDateClick: () -> Unit = {},
    onTimeClick: () -> Unit = {},
    onNowClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** EXPANDED split pane only: the photo fills the pane's height. */
    fillHeight: Boolean = false,
    /** EXPANDED only: the pill seats at the hero pane's foot
     * (there is no hero/sheet boundary inside the pane). */
    pillAtHeroFoot: Boolean = false,
    /** HERO REDESIGN (Nov 9, 2026): the weather line's tap door
     * (the line seats on the hero's foot now, in HeroSection). */
    onWeatherPillClick: (String) -> Unit = {},
    /** HERO REDESIGN: the foot stack's reserve, measured by the
     * stacked host from its straddle pill. Unused when
     * [pillAtHeroFoot] (the reserve is measured locally there). */
    footReserveDp: Float = com.runwear.app.ui.components.HeroComposition.DEFAULT_FOOT_RESERVE_DP
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
        // EXPANDED: the pane's measured height + the foot pill's
        // measured height drive the hero's own composition (the
        // crop geometry and the foot stack solve against the
        // pane's true box, not the screen fraction).
        var paneHeightDp by remember { mutableStateOf<Float?>(null) }
        var footPillHeightPx by remember { mutableIntStateOf(0) }
        val densityHere = LocalDensity.current
        val effectiveReserve = if (pillAtHeroFoot) {
            val pillDp = with(densityHere) { footPillHeightPx.toDp().value }
            com.runwear.app.ui.components.HeroComposition.footReserveDp(pillDp) +
                16f
        } else {
            footReserveDp
        }
        Box(
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .onSizeChanged { size ->
                    if (fillHeight) {
                        paneHeightDp = with(densityHere) {
                            size.height.toDp().value
                        }
                    }
                }
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
                onHeroPhotoClick = onHeroPhotoClick,
                scrollOffset = scrollOffset,
                onLocationClick = onLocationClick,
                onSettingsClick = onSettingsClick,
                shareActions = shareActions,
                // RW-144: chip + feels-like frames.
                selectedDateTime = uiState.selectedDateTime,
                isNowSelected = uiState.isNowSelected,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick,
                onNowClick = onNowClick,
                temperatureUnit = uiState.temperatureUnit,
                comfortPreference = uiState.comfortPreference,
                // HERO REDESIGN: weather line on the hero's foot.
                onWeatherPillClick = onWeatherPillClick,
                footReserveDp = effectiveReserve,
                heroHeightOverride = if (fillHeight) {
                    paneHeightDp?.dp
                } else {
                    null
                },
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
                // CHANGED states (a non-Now moment selected).
                showRefreshButton = !uiState.isNowSelected &&
                    uiState.effectiveHeroUrl != null &&
                    uiState.heroCandidates.size +
                    (if (uiState.personalHeroImageUrl != null) 1 else 0) > 1,
                onRefreshHeroClick = onCycleHero,
                modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            )
            // EXPANDED split only: the pill at the hero pane's foot.
            if (pillAtHeroFoot && heroPill != null) {
                com.runwear.app.ui.components.HeroPill(
                    content = heroPill,
                    onAction = onHeroPillAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .onSizeChanged { footPillHeightPx = it.height }
                )
            }
        }
    }
}

/**
 * RW-144: the stacked layouts' first-screen unit — the hero
 * with the sheet opening DIRECTLY below it on the same surface,
 * and the Make It Me pill STRADDLING the hero/sheet boundary
 * (centered, half over the photo's foot, half over the sheet),
 * exactly as the v8 frames seat it. When no pill state exists
 * (a stock serve), the sheet opens at the hero's foot with its
 * plain inset — no gap is faked.
 *
 * HERO REDESIGN (Nov 9, 2026): the hero is now most of the
 * first viewport (HeroComposition fractions) and the weather
 * line seats on the hero's foot — the unit's sheet-opening
 * block (HeroSheetContent / HeroSheetBlock) is retired from
 * the home path; the unit is the hero plus the straddle
 * clearance spacer.
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
    /** RW-168: the hero share button + menu doors. */
    shareActions: com.runwear.app.ui.components.HeroShareActions? = null,
    onWeatherPillClick: (String) -> Unit,
    onFlagClick: () -> Unit,
    heroFlagEnabled: Boolean,
    onCycleHero: () -> Unit = {},
    heroPill: com.runwear.app.ui.components.HeroPillContent? = null,
    onHeroPillAction: () -> Unit = {},
    /** RW-133: tapping the photo opens the full-screen viewer. */
    onHeroPhotoClick: () -> Unit = {},
    /** RW-169 §7D: report the pill's measured window bounds to
     * the overlay host; while [pillOverlayActive] the in-list copy
     * yields (alpha 0) to the overlay-host copy above the panel. */
    onPillBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    pillOverlayActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val heroHeight = heroPhotoHeight(configuration.screenHeightDp)
    // The pill's half-height: its seat straddles the boundary by
    // the pill's own MEASURED half. Pill states vary in height —
    // the generating state stacks its upsell line under the main
    // row — so a fixed estimate would let a tall pill cover the
    // sheet's weather line (seen in the RW-144 walkthrough's
    // generating frame) or leave a short pill floating off the
    // seam. Measured, the seat and the sheet's clearance move
    // together and the straddle is exact in every state.
    var pillHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val pillHalf = with(density) { (pillHeightPx / 2).toDp() }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HeroSectionContent(
                uiState = uiState,
                contentVisible = contentVisible,
                scrollOffset = scrollOffset,
                onLocationClick = onLocationClick,
                onSettingsClick = onSettingsClick,
                shareActions = shareActions,
                onFlagClick = onFlagClick,
                heroFlagEnabled = heroFlagEnabled,
                onCycleHero = onCycleHero,
                heroPill = heroPill,
                onHeroPillAction = onHeroPillAction,
                onHeroPhotoClick = onHeroPhotoClick,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick,
                onNowClick = onNowClick,
                // HERO REDESIGN (Nov 9, 2026): the weather line
                // seats on the hero's foot (inside HeroSection);
                // the sheet no longer opens with it. The hero's
                // foot reserve is this unit's measured straddle
                // half, so block / weather line / pill compose
                // as one stack with guaranteed gaps.
                onWeatherPillClick = onWeatherPillClick,
                footReserveDp = if (heroPill != null) {
                    com.runwear.app.ui.components.HeroComposition
                        .footReserveDp(pillHalf.value)
                } else {
                    com.runwear.app.ui.components.HeroComposition
                        .DEFAULT_FOOT_RESERVE_DP
                },
                modifier = Modifier.fillMaxWidth()
            )
            // The straddle pill's lower half hangs below the
            // hero; the sheet's first content (the outfit
            // header) clears it by the same measured half + 12 —
            // the clearance the sheet block's top padding used
            // to carry, kept exact now that the block is gone.
            if (heroPill != null) {
                Spacer(Modifier.height(pillHalf + 12.dp))
            }
        }
        if (heroPill != null) {
            com.runwear.app.ui.components.HeroPill(
                content = heroPill,
                onAction = onHeroPillAction,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = heroHeight - pillHalf)
                    .onSizeChanged { pillHeightPx = it.height }
                    .onGloballyPositioned { coords ->
                        onPillBounds(
                            coords.boundsInWindow()
                        )
                    }
                    .graphicsLayer {
                        alpha = if (pillOverlayActive) 0f else 1f
                    }
            )
        }
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
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        lineHeight = 24.sp
                    ),
                    color = RunWearColors.TextPrimary
                )

                // RW-144: the v8 frames' header is the count line
                // alone — no Shop button rides it. (Shop-all remains
                // reachable from an outfit item's own shop door.)
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
    onFeedbackClick: () -> Unit,
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

    // RW-152 §1 (Nov 10, 2026): no in-flow footer item — the
    // expanded Wear I've Run form is a state of the dock
    // overlay (see the host).
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
    onFeedbackClick: () -> Unit,
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

    // RW-152 §1 (Nov 10, 2026): no in-flow footer item — see
    // the list helper's note.
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
