package com.runwear.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runwear.app.feedback.StartupTrace
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.getTemperatureColor
import com.runwear.app.ui.theme.getTemperatureFallbackGradient
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherConditions

/**
 * RW-25: how far the home sheet overlaps the photo's foot. The sheet
 * block (HeroSheet.kt) is laid out against this in HeroMainScreen, and
 * the photo's corner chips inset above it by this plus a 12dp gap.
 */
val HeroSheetOverlap: Dp = 28.dp

/** The hero photo's height: 75% of the screen height (unchanged). */
fun heroPhotoHeight(screenHeightDp: Int): Dp = (screenHeightDp * 0.75).dp

/**
 * RW-25 scroll handoff: as the home list scrolls, the photo settles
 * from full-bleed into a card under the top chrome. Progress is a pure
 * function of the scroll offset — the input is already continuous, so
 * the mapping is 1:1 (no spring on a scroll-linked value) and applied
 * through graphicsLayer + clip.
 */
object HeroScrollHandoff {

    /** Scroll distance (as a fraction of hero height) over which the
     * handoff completes. */
    const val TRAVEL_FRACTION = 0.55f

    fun progress(scrollOffsetPx: Float, heroHeightPx: Float): Float =
        if (heroHeightPx <= 0f) 0f
        else (scrollOffsetPx / (heroHeightPx * TRAVEL_FRACTION)).coerceIn(0f, 1f)

    /** Photo scale at [progress]: 1f → 0.94f. */
    fun scale(progress: Float): Float = 1f - 0.06f * progress

    /** Horizontal inset at [progress]: 0dp → 16dp. */
    fun horizontalInset(progress: Float): Dp = (16f * progress).dp

    /** Corner radius the photo clips to at [progress]: 0dp → 24dp. */
    fun cornerRadius(progress: Float): Dp = (24f * progress).dp
}

/**
 * Hero PHOTO ZONE (RW-25 two-zone home). The photo carries chrome only:
 * the image itself (same loading/crossfade + [HeroTransitionPolicy]
 * cut rule), the 12% temperature tint, a bounded top gradient for
 * chrome legibility, the glass top controls (location pill + share /
 * settings), and the flag/refresh corner chips.
 *
 * Everything that used to be printed on the photo — the date/time
 * pill, the FEELS LIKE number, the Actual line, the weather pills —
 * now lives on the sheet (see HeroSheetBlock), which overlaps this
 * zone's foot by [HeroSheetOverlap].
 */
@Composable
fun HeroSection(
    weather: WeatherConditions?,
    outfit: OutfitRecommendation?,
    locationName: String,
    heroImageUrl: String?,
    scrollOffset: Float = 0f,
    onLocationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareClick: () -> Unit,
    /** Hero-flag affordance: shown only when a hero photo is displayed. */
    showFlagButton: Boolean = false,
    flagEnabled: Boolean = true,
    onFlagClick: () -> Unit = {},
    /**
     * RW-16.27: whether the photo on screen is the user's personal
     * hero (vs a pool photo). Drives the swap transition only: a
     * person change (pool ↔ personal) CUTS; a refresh within the
     * same layer keeps the crossfade ([HeroTransitionPolicy]). The
     * pre-fix hero crossfaded every swap, so the personal photo
     * ghosted over a pool stranger for 500 ms (RW-16.26).
     */
    heroIsPersonal: Boolean = false,
    /**
     * Hero-refresh affordance: shown beside the flag chip when the
     * current cell has more than one approved image. Tapping cycles to
     * the next image for the same cell WITHOUT flagging this one.
     */
    showRefreshButton: Boolean = false,
    onRefreshHeroClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val heroHeight = heroPhotoHeight(configuration.screenHeightDp)

    // RW-16.27: the person layer the last composed hero belonged to;
    // the transition for THIS swap is decided against it before the
    // effect records the new layer.
    var previousHeroIsPersonal by remember { mutableStateOf<Boolean?>(null) }
    val crossfadeHero = HeroTransitionPolicy.shouldCrossfade(
        previousIsPersonal = previousHeroIsPersonal,
        newIsPersonal = heroIsPersonal
    )
    LaunchedEffect(heroImageUrl, heroIsPersonal) {
        previousHeroIsPersonal = heroIsPersonal
    }

    // Decode the hero at display size, not at the source's full 800×1200.
    val density = LocalDensity.current
    val heroWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val heroHeightPx = with(density) { heroHeight.roundToPx() }

    // Temperature tint color with smooth animation (500ms transition)
    val tempFahrenheit = weather?.feelsLikeInFahrenheit ?: 65.0
    val targetTempColor = remember(tempFahrenheit) {
        getTemperatureColor(tempFahrenheit)
    }
    val tempColor by animateColorAsState(
        targetValue = targetTempColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "temperatureColorAnimation"
    )

    val context = LocalContext.current

    // Safe-boot diagnostic (1.1.3): the outfit hero card composable was
    // entered — its body executed during composition. remember{} so the
    // mark fires once per composition instance, not per recomposition.
    remember {
        StartupTrace.mark(context.applicationContext, "hero-card-entered")
        Unit
    }

    // RW-25 scroll handoff progress (0 = full-bleed, 1 = settled card).
    val handoff = HeroScrollHandoff.progress(scrollOffset, heroHeightPx.toFloat())
    val handoffScale = HeroScrollHandoff.scale(handoff)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
    ) {
        // 1. Hero image + temperature tint, transformed by the scroll
        // handoff: the photo scales down, insets and rounds its
        // corners as the sheet takes over the scroll.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HeroScrollHandoff.horizontalInset(handoff))
                .graphicsLayer {
                    scaleX = handoffScale
                    scaleY = handoffScale
                }
                .clip(RoundedCornerShape(HeroScrollHandoff.cornerRadius(handoff)))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (heroImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(heroImageUrl)
                            .size(heroWidthPx, heroHeightPx)
                            .apply { if (crossfadeHero) crossfade(500) else crossfade(false) }
                            .build(),
                        contentDescription = generateHeroAltText(weather, outfit),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.1f)
                    )
                } else {
                    // Fallback gradient when no image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        getTemperatureFallbackGradient(tempFahrenheit),
                                        RunWearColors.Background
                                    )
                                )
                            )
                    )
                }
            }

            // 2. Temperature Tint Overlay (12%, unchanged)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tempColor.copy(alpha = 0.12f))
            )
        }

        // 3. Bounded TOP gradient only — for chrome legibility. The
        // pre-RW-25 6-stop scrim's bottom stops existed to seat the
        // on-photo text block; that text lives on the sheet now.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.55f),
                            0.55f to Color.Black.copy(alpha = 0.25f),
                            1.00f to Color.Transparent
                        )
                    )
                )
        )

        // 3b. RW-26: the RunWear script logo — genuine Great Vibes,
        // warm-white #F5F1EA, ~29sp, centered at the top of home as
        // photo-zone chrome (a brand mark, not data; design §7.3).
        Text(
            text = "RunWear",
            fontFamily = GreatVibesFontFamily,
            fontSize = 29.sp,
            color = Color(0xFFF5F1EA),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.45f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f
                )
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        )

        // 4. Top Controls (Location + Share + Settings) — glass chrome,
        // theme-independent, unchanged.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location button (pill with text) - constrained to prevent overflow
            GlassButton(
                icon = Icons.Outlined.LocationOn,
                text = locationName,
                onClick = onLocationClick,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(Modifier.width(8.dp))

            // Right side: Share + Settings (icon-only circular buttons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButtonIcon(
                    icon = Icons.Outlined.Share,
                    onClick = onShareClick,
                    contentDescription = "Share"
                )
                GlassButtonIcon(
                    icon = Icons.Outlined.Settings,
                    onClick = onSettingsClick,
                    contentDescription = "Settings"
                )
            }
        }

        // 5. Hero corner chips — bottom-trailing corner of the photo:
        // refresh (cycle to the cell's next approved image) beside the
        // flag. Deliberately quiet: small dark translucent discs, no
        // labels. RW-25: the inset no longer derives from a pills row
        // (the pills moved to the sheet) — the chips sit above the
        // sheet's overlap of the photo's foot: overlap (28dp) + a
        // 12dp gap. Rendered only while a photo is actually on screen.
        if (heroImageUrl != null && (showFlagButton || showRefreshButton)) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = HeroSheetOverlap + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRefreshButton) {
                    HeroRefreshChip(onClick = onRefreshHeroClick)
                }
                if (showFlagButton) {
                    HeroFlagChip(
                        enabled = flagEnabled,
                        onClick = onFlagClick
                    )
                }
            }
        }
    }
}

/**
 * The flag affordance overlaid on the hero photo: a 32dp dark
 * translucent disc with a flag glyph. Once a photo has been flagged it
 * renders dimmed and inert — one flag per photo view is enough.
 */
@Composable
private fun HeroFlagChip(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.35f else 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Icon(
            imageVector = Icons.Outlined.Flag,
            contentDescription = "Flag this photo",
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.45f),
            modifier = Modifier
                .padding(8.dp)
                .size(16.dp)
        )
    }
}

/**
 * The refresh affordance overlaid on the hero photo, beside the flag
 * chip: same quiet 32dp dark translucent disc. Tapping cycles the hero
 * to the next approved image for the current cell — the current photo
 * is NOT flagged or removed, just rotated past for today.
 */
@Composable
private fun HeroRefreshChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Show another photo",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .padding(8.dp)
                .size(16.dp)
        )
    }
}

/**
 * RW-16.27: the hero swap transition rule. A swap that changes the
 * PERSON on screen — pool photo ↔ the user's personal photo — must
 * CUT: crossfading two differently-cropped full-body subjects
 * composites them into a ghost overlay for the fade's duration
 * (the RW-16.26 field report). A refresh within the same layer
 * (pool → pool, or a regenerated personal photo replacing its
 * predecessor) may keep the crossfade. The first hero a view
 * composes has no predecessor to ghost over, so it may fade in.
 * Pure so the rule is unit-tested without composition.
 */
object HeroTransitionPolicy {

    fun shouldCrossfade(previousIsPersonal: Boolean?, newIsPersonal: Boolean): Boolean =
        previousIsPersonal == null || previousIsPersonal == newIsPersonal
}

/**
 * Generate alt text for hero image for accessibility.
 */
private fun generateHeroAltText(
    weather: WeatherConditions?,
    outfit: OutfitRecommendation?
): String {
    if (weather == null) return "Runner in running gear"

    val tempDesc = when {
        weather.feelsLikeInFahrenheit < 35 -> "cold"
        weather.feelsLikeInFahrenheit < 65 -> "mild"
        else -> "warm"
    }

    val weatherDesc = weather.weatherCode.description.lowercase()
    val itemCount = outfit?.allItems?.size ?: 0

    return "Runner dressed for $tempDesc $weatherDesc conditions with $itemCount items of gear"
}
