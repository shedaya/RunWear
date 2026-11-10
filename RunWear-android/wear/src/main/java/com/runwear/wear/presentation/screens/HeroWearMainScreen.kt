package com.runwear.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
// rememberActiveFocusRequester removed - using manual FocusRequester for pager pages
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.wear.presentation.theme.RunWearColors
import com.runwear.wear.presentation.theme.WeatherColors
import com.runwear.wear.presentation.viewmodel.WearUiState
import kotlinx.datetime.toJavaLocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Hero-image design wear main screen with 3-page horizontal layout.
 *
 * Pages:
 * 1. Hero Weather - Image with temp tint, large temperature display
 * 2. Outfit List - Clothing items as chips
 * 3. Tips - Running tips
 */
@Composable
fun HeroWearMainScreen(
    uiState: WearUiState,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousDay: () -> Unit,
    onResetToNow: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        when {
            !uiState.hasLocationPermission -> WearPermissionScreen(onRequestPermission)
            uiState.isLoading -> WearLoadingScreen()
            uiState.error != null -> WearErrorScreen(uiState.error, onRefresh)
            uiState.outfit != null && uiState.weather != null -> {
                HeroWearContent(
                    uiState = uiState,
                    onToggleUnit = onToggleUnit,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
private fun HeroWearContent(
    uiState: WearUiState,
    onToggleUnit: () -> Unit,
    onRefresh: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    // RW-76 (GEN-6 VQ fix wave, VQ-6.6): the pager indicator owns a
    // RESERVED SLOT in the layout — a 20.dp band below the pager —
    // instead of overlaying the pager's bottom edge. As an overlay
    // (bottom = 20.dp in the old composition) the dots sat on
    // whatever content line the hero page happened to place at that
    // height: the "Actual 60°F" line at font scale 1.3 on both round
    // faces, the "Thu" day line and the "Swipe for N items →" line
    // at default scale in the DA-13 states. Inside the layout flow
    // the pager is clipped to its own bounds above the band, so no
    // content line of any page, at any scroll position, can ever
    // reach the dots; the dots still advertise the swipe on every
    // page, and at the band's centre height the round chord is far
    // wider than the 28.dp dot row, so the dots themselves stay
    // inside the display.
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 0
        ) { page ->
            when (page) {
                0 -> HeroWeatherPage(
                    weather = uiState.weather!!,
                    heroImageUrl = uiState.heroImageUrl,
                    locationName = uiState.locationName,
                    outfitItemCount = uiState.outfit!!.allItems.size,
                    onToggleUnit = onToggleUnit,
                    temperatureUnit = uiState.temperatureUnit,
                    comfortPreference = uiState.comfortPreference
                )
                1 -> OutfitPage(
                    outfit = uiState.outfit!!,
                    onRefresh = onRefresh,
                    isVisible = pagerState.currentPage == 1
                )
                2 -> TipsPage(
                    tips = uiState.outfit!!.tips,
                    locationName = uiState.locationName,
                    isVisible = pagerState.currentPage == 2
                )
            }
        }

        // Page indicator — RW-76: rendered in its reserved layout
        // band below the pager (see the slot note above), never as
        // an overlay on page content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            PageIndicator(
                pageCount = 3,
                currentPage = pagerState.currentPage
            )
        }
    }
}

// ============================================================================
// PAGE 1: HERO WEATHER
// ============================================================================

@Composable
internal fun HeroWeatherPage(
    weather: WeatherConditions,
    heroImageUrl: String?,
    locationName: String,
    outfitItemCount: Int,
    onToggleUnit: () -> Unit,
    /** WS-2: render-time projection of the canonical store. */
    temperatureUnit: com.runwear.shared.domain.model.TemperatureUnit =
        com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT,
    /** GEN-6 WAVE C (DA-13): the comfort frame for the headline. */
    comfortPreference: com.runwear.shared.domain.model.ComfortPreference =
        com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
) {
    val tempColor = WeatherColors.forTemperature(weather.feelsLikeInFahrenheit)
    val tintColor = WeatherColors.forTemperatureTint(weather.feelsLikeInFahrenheit)

    // GEN-6 WAVE C (DA-13): the hero numeral is a READOUT of the
    // comfort-adjusted feels-like (WearTempFrame) — it appears at
    // its final value. The gen-5 build animated the raw figure
    // through a 600 ms tween counting through intermediate values;
    // a numeral is a readout, never a counter, so the tween is gone.
    val headlineTemp = temperatureUnit.convert(
        com.runwear.wear.presentation.WearTempFrame.headlineF(weather, comfortPreference)
    ).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        // Hero image — GEN-5 (RW-29 FA-46): the hero fills the
        // display's ACTUAL shape (design frame 53) with a
        // TOP-anchored crop. The gen-4 layer clipped to a circle
        // of its own and centre-cropped, cutting the runner's
        // head on the watch just as the phone poster did; the
        // display's own shape is the only mask here.
        heroImageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_runner),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Temperature tint overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tintColor)
        )

        // Gradient for text readability - stronger for temperature area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.5f),
                            0.25f to Color.Black.copy(alpha = 0.3f),
                            0.4f to Color.Black.copy(alpha = 0.5f),
                            0.6f to Color.Black.copy(alpha = 0.5f),
                            0.75f to Color.Black.copy(alpha = 0.3f),
                            1.0f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Content — Addendum 10 (Wear Play compliance): the column is
        // inset to the round display's SAFE AREA. On a round face the
        // chord narrows sharply toward the top and bottom edges, so
        // the old 12.dp inset let the top labels and the bottom hint
        // run under the curved edge (Google WATCH SHAPES finding),
        // and the single-line caps cut text off outright at the
        // largest font size (Google LARGE FONT finding). The inset
        // is 26.dp horizontally with deep top/bottom reserves, and
        // the labels WRAP (no maxLines cap) instead of truncating.
        // Addendum 10 (LARGE FONT, vertical budget): at the
        // largest font scale on the smallest round face, the
        // stacked blocks (labels + temperature card + day/hint)
        // exceed the display height; as a fixed SpaceBetween
        // column the overflow was CUT OFF — the feels-like line
        // and the day/hint block fell outside the circle
        // (screenshot 1224's missing feels-like value). The
        // column now scrolls vertically: the initial settle shows
        // the condition, location, the temperature card WITH its
        // feels-like line inside the circle, and the day/hint
        // block is reachable by scroll instead of being clipped.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Condition label and location (the phone app's condition
            // treatment is a text label, not an emoji glyph). The
            // horizontal safe inset lives on the top/bottom blocks:
            // at the vertical centre the round chord is the full
            // diameter and the temperature card needs that width.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    // RW-76: the large face's top reserve is 16.dp
                    // (was 24.dp) — the 8.dp returns to the card so
                    // its Actual line stays inside the pager's
                    // shortened first screenful at font scale 1.3
                    // now that the indicator band sits below the
                    // pager. At 16.dp from the top the large face's
                    // chord is still far wider than the centred
                    // condition line, so chord safety is unchanged.
                    top = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp <= 200) 6.dp else 16.dp,
                    start = 26.dp, end = 26.dp
                )
            ) {
                Text(
                    text = weather.weatherCode.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = RunWearColors.TextPrimary.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = RunWearColors.TextPrimary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Center: Large temperature with glass backdrop (tappable).
            // Addendum 10 supplement (reviewer screenshot
            // IN_APP_EXPERIENCE-1224): this card was WIDER than the
            // circular display — its sides were cut off by the round
            // edge. The card is now width-capped to the round safe
            // area at every size: at most 84% of the display width.
            // At the card's vertical extent (half-height ~52.dp on
            // the smallest face) the round chord is ~161.dp wide
            // against the cap's ~161.dp, so its corners stay inside
            // the chord; the width also lets the feels-like line
            // sit on ONE line inside the card.
            val displayWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            Card(
                onClick = onToggleUnit,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(vertical = 0.dp)
                    // min: the card is at least 80% of the display
                    // wide, so the single-line feels-like value
                    // fits inside it (wrap-content sizing to the
                    // narrower temperature text clipped the unit
                    // letter). max: 84% — with the compressed
                    // vertical budget the card's corners meet a
                    // chord at least ~166.dp wide on the smallest
                    // face, against the cap's ~161.dp.
                    .widthIn(
                        min = (displayWidthDp * 0.80f).dp,
                        max = (displayWidthDp * 0.84f).dp
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // RW-76: no vertical padding inside the card —
                    // the 4.dp returns to the card's Actual line so
                    // it stays inside the pager's first screenful at
                    // font scale 1.3 (see the indicator-slot note).
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    // Temperature with colored accent border/shadow.
                    // Addendum 10: on the smallest round faces the
                    // display step down (48 -> 40 sp) keeps the card
                    // inside the safe area even at the largest system
                    // font scale, where the glyphs grow a further 30%.
                    val unitSymbol = if (temperatureUnit ==
                        com.runwear.shared.domain.model.TemperatureUnit.CELSIUS) "C" else "F"
                    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                    // Sized against the safe-area-capped card: at
                    // the largest font scale the step down keeps
                    // even a three-digit temperature inside the
                    // card (screenshot 1224's clipped card).
                    val tempFontSize = when {
                        screenWidthDp <= 200 && fontScale > 1.15f -> 28.sp
                        screenWidthDp <= 200 -> 40.sp
                        fontScale > 1.15f -> 40.sp
                        else -> 48.sp
                    }
                    // DA-13: the numeral's label — it is the
                    // feels-like, named on the card.
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_feels_like_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = RunWearColors.TextPrimary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Box(contentAlignment = Alignment.Center) {
                        // Shadow layer for depth. The explicit
                        // lineHeight keeps the heavy glyphs' ink
                        // inside the text's layout box at grown
                        // font scales (they were sliced at the
                        // default line height).
                        Text(
                            text = "${headlineTemp}°$unitSymbol",
                            fontSize = tempFontSize,
                            lineHeight = (tempFontSize.value * 1.02f).sp,
                            fontWeight = FontWeight.Black,
                            color = tempColor.copy(alpha = 0.4f),
                            letterSpacing = (-1).sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        // Main white text
                        Text(
                            text = "${headlineTemp}°$unitSymbol",
                            fontSize = tempFontSize,
                            lineHeight = (tempFontSize.value * 1.02f).sp,
                            fontWeight = FontWeight.Black,
                            color = RunWearColors.TextPrimary,
                            letterSpacing = (-1).sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    // Color indicator bar
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tempColor)
                    )
                    // RW-76: 2.dp (was 4.dp) — see the card-padding
                    // note above; the bar-to-Actual gap keeps its
                    // separation at half the reserve.
                    Spacer(Modifier.height(2.dp))
                    // Addendum 10 supplement (screenshot 1224): the
                    // "Feels like" label rendered but its VALUE never
                    // appeared inside the circle. Label and value now
                    // travel together as one line INSIDE the
                    // safe-area-capped card, so both are always
                    // inside the display.
                    // DA-13: the numeral above IS the feels-like;
                    // the subordinate line carries the ACTUAL.
                    Text(
                        text = com.runwear.wear.presentation.WearTempFrame.actualLine(weather, temperatureUnit),
                        style = MaterialTheme.typography.labelSmall,
                        color = RunWearColors.TextPrimary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        // One line, always: the wrap-content card
                        // grows to this line's full width (bounded
                        // by the card's safe-area cap), so label
                        // and value are never split or pushed out
                        // of the card — the 1224 defect.
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Bottom: day label + swipe hint. Addendum 10: the hint
            // is ONE wrapping, centred text lifted 26.dp off the
            // bottom edge (inside the round safe area, clear of the
            // page indicator) — as a fixed Row it overflowed the
            // narrowed bottom chord at the largest font size.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // 34.dp bottom reserve: at the scroll end the block
                // stops short of the round display's bottom chord
                // instead of running under the curved edge. (RW-76:
                // the pager dots no longer share this zone at all —
                // they live in their own layout band below the
                // pager; this reserve is chord safety only now.)
                modifier = Modifier.padding(bottom = 34.dp, start = 26.dp, end = 26.dp)
            ) {
                val isToday = weather.dateTime.toJavaLocalDateTime().toLocalDate() == LocalDate.now()
                Text(
                    text = if (isToday) "Today" else weather.dateTime.toJavaLocalDateTime().format(
                        DateTimeFormatter.ofPattern("EEE")
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = RunWearColors.TextPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(com.runwear.wear.R.plurals.wear_swipe_items, outfitItemCount, outfitItemCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = RunWearColors.Primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ============================================================================
// PAGE 2: OUTFIT LIST
// ============================================================================

@Composable
internal fun OutfitPage(
    outfit: OutfitRecommendation,
    onRefresh: () -> Unit,
    isVisible: Boolean = false
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(200)
            focusRequester.requestFocus()
        }
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .rotaryScrollable(
                behavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(listState),
                focusRequester = focusRequester
            )
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // Addendum 10: 24.dp horizontal / 32.dp vertical content
        // padding keeps chip rows inside the round safe area
        // instead of spanning the full chord (WATCH SHAPES; same
        // defect class as the advice-card screenshot 7999).
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        autoCentering = null
    ) {
        // Title
        item {
            Text(
                text = "Your Outfit",
                style = MaterialTheme.typography.labelMedium,
                color = RunWearColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Outfit items as compact chips
        items(outfit.allItems) { item ->
            WearOutfitChip(item)
        }

        // Refresh button — Addendum 10: no fixed 32.dp height. At
        // the largest font size the label outgrew that height and
        // was cut off vertically; the button now sizes to its
        // (grown) label with a minimum touch height.
        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRefresh,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_refresh), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WearOutfitChip(item: ClothingItem) {
    val categoryColor = getCategoryColor(item.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RunWearColors.SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category color indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(categoryColor)
        )

        Spacer(Modifier.width(6.dp))

        // Category icon — same vector set the phone app uses, tinted with
        // the category color (mirrors OutfitCard.getCategoryIconRes)
        Icon(
            painter = painterResource(id = getCategoryIconRes(item.category)),
            contentDescription = null,
            tint = categoryColor,
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.width(6.dp))

        // Name — Addendum 10: wraps to two lines instead of the
        // single-line truncation that cut item names off at the
        // largest font size.
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = RunWearColors.TextPrimary,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}

// ============================================================================
// PAGE 3: TIPS
// ============================================================================

@Composable
internal fun TipsPage(
    tips: List<String>,
    locationName: String,
    isVisible: Boolean = false
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(200)
            focusRequester.requestFocus()
        }
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .rotaryScrollable(
                behavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(listState),
                focusRequester = focusRequester
            )
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // Addendum 10 supplement (reviewer screenshot
        // IN_APP_EXPERIENCE-7999, the ADVICE page): the advice card
        // was wider than the round safe area — top corners and
        // sides clipped by the circular edge, with a fragment of
        // the preceding content stranded at the top edge. The
        // deep insets (32.dp horizontal, 40.dp vertical) put the
        // first card fully inside the chord at its settled
        // position: at 40.dp from the top of the smallest round
        // face the chord is ~156.dp wide against the card's
        // 128.dp, and nothing of the page settles outside it.
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 40.dp),
        autoCentering = null
    ) {
        // Title
        item {
            Text(
                text = "Tips",
                style = MaterialTheme.typography.titleSmall,
                color = RunWearColors.Primary,
                fontWeight = FontWeight.Bold
            )
        }

        if (tips.isEmpty()) {
            item {
                Text(
                    text = "No specific tips for today. Enjoy your run!",
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(tips) { tip ->
                WearTipCard(tip)
            }
        }

        // Location footer
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = locationName,
                style = MaterialTheme.typography.labelSmall,
                color = RunWearColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WearTipCard(tip: String) {
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RunWearColors.SurfaceElevated
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(RunWearColors.Primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tip,
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextPrimary
            )
        }
    }
}

// ============================================================================
// PAGE INDICATOR
// ============================================================================

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) RunWearColors.Primary
                        else RunWearColors.TextMuted.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

// ============================================================================
// HELPER SCREENS
// ============================================================================

@Composable
private fun WearLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_loading),
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

@Composable
private fun WearErrorScreen(error: String, onRetry: () -> Unit) {
    // Addendum 10: the error text carried a two-line cap, so any
    // real message was cut off — completely at the largest font
    // size. The screen now scrolls inside the round safe area
    // (28.dp horizontal inset) and the message wraps in full.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, modifier = Modifier.height(44.dp)) {
                Text(androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_retry))
            }
        }
    }
}

@Composable
private fun WearPermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_location_needed),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRequest, modifier = Modifier.height(44.dp)) {
                Text(androidx.compose.ui.res.stringResource(com.runwear.wear.R.string.wear_grant))
            }
        }
    }
}

// ============================================================================
// HELPERS
// ============================================================================

private fun getCategoryColor(category: ClothingCategory): Color = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
    ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
    ClothingCategory.HEAD -> RunWearColors.CategoryHead
    ClothingCategory.HANDS -> RunWearColors.CategoryHands
    ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
}

/** Category icon drawable — mirrors the phone app's OutfitCard mapping. */
private fun getCategoryIconRes(category: ClothingCategory): Int = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER ->
        com.runwear.wear.R.drawable.ic_category_top
    ClothingCategory.BOTTOM -> com.runwear.wear.R.drawable.ic_category_bottom
    ClothingCategory.HEAD -> com.runwear.wear.R.drawable.ic_category_head
    ClothingCategory.HANDS -> com.runwear.wear.R.drawable.ic_category_hands
    ClothingCategory.ACCESSORIES -> com.runwear.wear.R.drawable.ic_category_accessories
}
