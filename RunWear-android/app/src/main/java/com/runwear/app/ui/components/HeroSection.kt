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

    /**
     * WS-1 (Part 1 §1.1): the photo parallaxes at 0.5× the sheet's
     * travel. Expressed on the crop anchor, not a layer translation
     * (a translation would open a gap at the photo's edge): the
     * served slice's vertical anchor bias drifts from top (−1) at
     * HALF the handoff's own drift toward center, so the slice moves
     * at 0.5× while the frame moves at 1×. At rest the anchor is
     * exactly top — see [HeroImageGeometry] for why that keeps the
     * runner's head in frame.
     */
    fun parallaxAnchorBias(progress: Float): Float =
        -1f + PARALLAX_FACTOR * progress

    /** The fraction of handoff travel over which the photo-zone
     * chrome (script logo, location pill, share, settings) fades. */
    const val CHROME_FADE_FRACTION = 0.4f

    /** Parallax rate: the slice anchor moves at half the frame's rate. */
    const val PARALLAX_FACTOR = 0.5f

    /**
     * WS-1 (Part 1 §1.1): chrome opacity 1 → 0 across the FIRST 40%
     * of handoff travel; fully present again at rest. The chrome
     * never slides — opacity only.
     */
    fun chromeAlpha(progress: Float): Float =
        1f - (progress / CHROME_FADE_FRACTION).coerceIn(0f, 1f)

    /**
     * WS-1: the chrome is interactive at rest and becomes
     * non-interactive at the END of its fade (progress ≥ 40% of
     * travel) — a faded-out button must never swallow a tap meant
     * for the sheet beneath it.
     */
    fun chromeInteractive(progress: Float): Boolean =
        progress < CHROME_FADE_FRACTION
}

/**
 * WS-1 motion tokens for the home surface (Part 1 §1.1/§1.2 of the
 * rebuild spec): the hero crossfade's in/out durations and the
 * day-strip selection settle. Named once, here, so no surface
 * re-invents its own timing.
 */
object HeroMotionTokens {
    /** Hero photo crossfade IN — begins only once the incoming
     * image has fully decoded (see HeroSection's painter gating).
     * WS-9 fix (P-1): motion spec §1.1 sets this crossfade at
     * 300 ms — the same value iOS ships (AppTheme crossfadeDuration
     * 0.3) — and the scaffold → exact swap rides this same crossfade. */
    const val HERO_CROSSFADE_IN_MS = 300

    /** Hero photo crossfade OUT (the outgoing layer's fade) — also
     * §1.1's 300 ms (WS-9 fix, P-1). */
    const val HERO_CROSSFADE_OUT_MS = 300

    /** Day/time strip selection settle (micro transition). */
    const val DAY_STRIP_SETTLE_MS = 120
}

/**
 * WS-1 (D-23, home half): the head-crop geometry, COMPUTED from the
 * served image's own aspect at the served width — never eyeballed.
 *
 * Hero serves are 800×1200 (the decode target documented in
 * HeroSection). Display uses Crop: the served slice is the largest
 * centered slice of the source that fills the display box, with its
 * vertical anchor at the TOP at rest ([HeroScrollHandoff]'s parallax
 * drifts it at 0.5× from there). Because the slice starts at source
 * y = 0, the runner's head band — measured on the audited serve at
 * source y ≈ 392–509 of 1200 (RW-27 D-23) — is inside the slice
 * whenever the slice is at least [HEAD_BAND_BOTTOM_FRACTION] tall,
 * and the reserved top chrome zone (status bar + controls row) sits
 * ABOVE the head's displayed top whenever it is no taller than the
 * head's displayed offset. Both constraints are pure functions of
 * (source size, display size) and are pinned by tests across the
 * phone / unfolded-Fold / split-pane width matrix.
 */
object HeroImageGeometry {

    const val SOURCE_WIDTH_PX = 800
    const val SOURCE_HEIGHT_PX = 1200

    /** Audited head band on the served frame (RW-27 D-23 fit). */
    const val HEAD_BAND_TOP_FRACTION = 392f / 1200f
    const val HEAD_BAND_BOTTOM_FRACTION = 509f / 1200f

    /** Crop scale for a display box: the source scales until it
     * fills the box on both axes. */
    fun cropScale(displayWidthPx: Float, displayHeightPx: Float): Float {
        if (displayWidthPx <= 0f || displayHeightPx <= 0f) return 0f
        return maxOf(
            displayWidthPx / SOURCE_WIDTH_PX,
            displayHeightPx / SOURCE_HEIGHT_PX
        )
    }

    /** Height (source px) of the slice Crop shows for a display box. */
    fun sliceHeightSourcePx(displayWidthPx: Float, displayHeightPx: Float): Float {
        val scale = cropScale(displayWidthPx, displayHeightPx)
        return if (scale <= 0f) 0f else displayHeightPx / scale
    }

    /**
     * Source y where the slice starts for a vertical anchor [bias]
     * (−1 = top, 0 = center). At rest the bias is −1, so this is 0:
     * nothing above the slice exists to crop the head away.
     */
    fun sliceTopSourcePx(
        displayWidthPx: Float,
        displayHeightPx: Float,
        bias: Float = -1f
    ): Float {
        val slice = sliceHeightSourcePx(displayWidthPx, displayHeightPx)
        val overflow = (SOURCE_HEIGHT_PX - slice).coerceAtLeast(0f)
        return overflow * ((bias + 1f) / 2f)
    }

    /** True when the whole head band lies inside the served slice. */
    fun headBandVisible(
        displayWidthPx: Float,
        displayHeightPx: Float,
        bias: Float = -1f
    ): Boolean {
        val top = sliceTopSourcePx(displayWidthPx, displayHeightPx, bias)
        val bottom = top + sliceHeightSourcePx(displayWidthPx, displayHeightPx)
        return top <= HEAD_BAND_TOP_FRACTION * SOURCE_HEIGHT_PX &&
            bottom >= HEAD_BAND_BOTTOM_FRACTION * SOURCE_HEIGHT_PX
    }

    /** Displayed offset (px) of the head band's top within the box. */
    fun headTopDisplayPx(displayWidthPx: Float, displayHeightPx: Float): Float {
        val scale = cropScale(displayWidthPx, displayHeightPx)
        val top = sliceTopSourcePx(displayWidthPx, displayHeightPx)
        return (HEAD_BAND_TOP_FRACTION * SOURCE_HEIGHT_PX - top) * scale
    }

    /**
     * The reserved top zone's sizing rule: the chrome zone reserved
     * over the photo (status-bar inset + the controls row) must not
     * extend past the head's displayed top — the runner's head stays
     * out of the chrome zone by construction, at any served width.
     */
    fun reservedTopZoneFits(
        reservedTopZonePx: Float,
        displayWidthPx: Float,
        displayHeightPx: Float
    ): Boolean = reservedTopZonePx <= headTopDisplayPx(displayWidthPx, displayHeightPx)
}

/**
 * WS-1 (D-01/D-04): the stacked unit's geometry — one pure place
 * where the reservation is computed. The unit (the LazyColumn's
 * first item) is exactly hero + sheet − overlap tall; the sheet is
 * anchored to the unit's BOTTOM, so its top sits at hero − overlap
 * and the −28dp overlap of the photo's foot holds at EVERY sheet
 * height and every scroll position. The void the audit measured
 * (≈ sheetHeight − 28dp of blank) is zero by construction here:
 * [voidDp] is identically zero, and the tests pin that across
 * short and long sheet heights.
 */
object HeroStackedGeometry {

    /** The sheet's overlap of the photo's foot: −28dp, constant. */
    val OVERLAP: Dp = HeroSheetOverlap

    /** Total height reserved for the stacked unit. */
    fun unitHeight(heroHeight: Dp, sheetHeight: Dp): Dp =
        heroHeight + sheetHeight - OVERLAP

    /** The sheet's top edge within the unit. */
    fun sheetTop(heroHeight: Dp): Dp = heroHeight - OVERLAP

    /** Unoccupied height inside the reserved unit: zero, always. */
    fun voidHeight(heroHeight: Dp, sheetHeight: Dp): Dp =
        unitHeight(heroHeight, sheetHeight) - (sheetTop(heroHeight) + sheetHeight)
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
    /** RW-26: the scaffold serve's honest badge (null = no badge). */
    scaffoldBadge: String? = null,
    /**
     * GEN-3 A-HERO (WS-A(1)): the synchronous bundled fallback for
     * the CURRENT committed conditions — the photo the band paints
     * from the first frame of a scene, before (and unless) the async
     * resolution lands. Null only when no truthful photo is servable
     * for the prescription (the census's documented defect cells).
     */
    heroFallbackUrl: String? = null,
    /**
     * GEN-3 A-HERO (X-7): the identity of the scene the hero target
     * belongs to — the PLACE (the state commits label + photo
     * atomically per place; hour/time switches within a place are
     * not scene changes and keep the GEN-2 layering, where the base
     * stays whole while the new target decodes). When it changes,
     * the layer policy DISCARDS the previous scene's photo as base —
     * the pre-fix layering kept the old place's photo whole under
     * the new place's label for ~1.3 s (Miami over New York's photo).
     * The new scene's base is its own fallback, never the old photo.
     */
    heroSceneKey: String = "",
    /** GEN-3 A-HERO (WS-A(2)): THE hero pill's content (null = none). */
    heroPill: HeroPillContent? = null,
    /** The pill's trailing action (EXACT_FREE's "Get unlimited"). */
    onHeroPillAction: () -> Unit = {},
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

    // GEN-2 A2 (W-6): the photo layers. The pre-fix hero was ONE
    // painter bound straight to heroImageUrl: the moment the URL
    // changed (a location switch committing the new place's photo),
    // the painter reset to Loading and painted NOTHING — the band sat
    // empty for ~1 s, then the new photo faded in over the bare page
    // background ("through white"). Now the displayed photo is the
    // BASE layer and a new target becomes an INCOMING layer above it:
    // the base stays whole until the incoming image has fully decoded,
    // then the incoming fades in OVER it (or cuts, on a person change)
    // and is promoted to base. No frame is ever empty mid-switch, and
    // no fade ever passes through the background. The layer arithmetic
    // is HeroLayerPolicy (pure, unit-tested).
    var heroLayers by remember {
        mutableStateOf(HeroLayerPolicy.Layers(baseUrl = null, incomingUrl = null))
    }
    // GEN-3 A-HERO (X-7 + WS-A(1)): scene-aware targeting. Within a
    // scene the GEN-2 A2 rules hold (base stays whole while the
    // incoming decodes). A scene CHANGE is a different contract: the
    // old scene's photo may never sit under the new scene's label —
    // the base resets to the NEW scene's own bundled fallback
    // (synchronous, on-device, effectively instant) and the resolved
    // photo, when it lands, fades in over THAT. On the very first
    // scene the same rule gives the band its fallback from frame
    // one, instead of the gradient the walkthrough recorded for
    // ~50 s at Overland Park (X-3) and ~2 s at New York (X-6).
    var heroScene by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(heroImageUrl, heroFallbackUrl, heroSceneKey) {
        heroLayers = if (heroScene != heroSceneKey) {
            heroScene = heroSceneKey
            HeroLayerPolicy.onSceneChange(heroImageUrl, heroFallbackUrl)
        } else {
            HeroLayerPolicy.onTarget(heroLayers, heroImageUrl)
        }
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
    // WS-1: photo-zone chrome fades over the first 40% of travel and
    // is non-interactive from the end of the fade.
    val chromeAlpha = HeroScrollHandoff.chromeAlpha(handoff)
    val chromeInteractive = HeroScrollHandoff.chromeInteractive(handoff)

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
                // GEN-2 A2 (W-6): layered hero (see heroLayers above).
                // The gradient is the ground layer whenever no photo
                // is on screen as base — a true first load, or a
                // resolution that truthfully produced no photo. The
                // BASE photo paints whole above it; the INCOMING photo
                // fades in over the base only once fully decoded
                // (WS-1 §1.1: no placeholder, no broken-image glyph,
                // no half-decoded frame is ever painted; a person
                // change CUTS per HeroTransitionPolicy), then promotes
                // to base. The crop anchor is the parallax bias (top
                // at rest — D-23 geometry); there is NO extra 1.1×
                // zoom (the pre-WS-1 zoom cropped the served frame
                // arbitrarily).
                if (heroLayers.baseUrl == null) {
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
                heroLayers.baseUrl?.let { baseUrl ->
                    HeroPhotoLayer(
                        url = baseUrl,
                        widthPx = heroWidthPx,
                        heightPx = heroHeightPx,
                        alpha = 1f,
                        contentDescription = generateHeroAltText(weather, outfit),
                        verticalBias = HeroScrollHandoff.parallaxAnchorBias(handoff)
                    )
                }
                heroLayers.incomingUrl?.let { incomingUrl ->
                    HeroIncomingPhotoLayer(
                        url = incomingUrl,
                        widthPx = heroWidthPx,
                        heightPx = heroHeightPx,
                        crossfade = crossfadeHero,
                        contentDescription = generateHeroAltText(weather, outfit),
                        verticalBias = HeroScrollHandoff.parallaxAnchorBias(handoff),
                        onReady = { readyUrl ->
                            if (heroLayers.incomingUrl == readyUrl) {
                                heroLayers = HeroLayerPolicy.onIncomingReady(heroLayers)
                            }
                        }
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
            color = com.runwear.app.ui.theme.PhotoInk,
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
                .graphicsLayer { alpha = chromeAlpha }
        )

        // 4. Top Controls (Location + Share + Settings) — glass chrome,
        // theme-independent. WS-1: this row is the photo-zone chrome —
        // it fades with the handoff's first 40% and goes
        // non-interactive at the end of the fade. The LOCATION PILL
        // (A3) is the designed home entry to the Section E picker;
        // its label is the SimplePlace form (WS-4), normalized at
        // this render boundary so a raw geocode string can never
        // reach it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .graphicsLayer { alpha = chromeAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location button (pill with text) - constrained to prevent overflow
            GlassButton(
                icon = Icons.Outlined.LocationOn,
                text = com.runwear.shared.domain.model.SimplePlace
                    .fromRawDisplayString(locationName).display,
                onClick = onLocationClick,
                enabled = chromeInteractive,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(Modifier.width(8.dp))

            // Right side: Settings (icon-only circular button).
            // WS-6 (D-15): no home share button — sharing lives in
            // the collection, card by card, by design.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButtonIcon(
                    icon = Icons.Outlined.Settings,
                    onClick = onSettingsClick,
                    contentDescription = "Settings",
                    enabled = chromeInteractive
                )
            }
        }

        // 4b. GEN-3 A-HERO (WS-A(2)): THE hero pill — ONE component
        // at the bottom of the hero image carrying the personal
        // layer's state in every case (making / scaffold / exact-free
        // count + Get unlimited / Premium). It subsumes the RW-26
        // scaffold badge (same position, same capsule). It renders
        // whenever its state exists — including over the fallback
        // photo while a personal photo generates, which is precisely
        // the frame X-2/X-8 found missing — and a stock serve carries
        // no pill (§9 silence, preserved by HeroPillPolicy).
        if (heroPill != null) {
            HeroPill(
                content = heroPill,
                onAction = onHeroPillAction,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = HeroSheetOverlap + 12.dp)
            )
        } else if (heroImageUrl != null && scaffoldBadge != null) {
            // Legacy badge path (kept for the existing call sites
            // that still pass a badge directly; HeroPillPolicy's
            // SCAFFOLD state is its successor).
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = HeroSheetOverlap + 12.dp),
                shape = RoundedCornerShape(100.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = scaffoldBadge,
                    fontSize = 12.sp,
                    color = com.runwear.app.ui.theme.PhotoInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
 * GEN-2 A2 (W-6): the hero photo's layer arithmetic. [baseUrl] is the
 * photo on screen, painted whole; [incomingUrl] is the photo being
 * transitioned to, painted above the base only after it has fully
 * decoded, and promoted to base when its fade (or cut) completes.
 * The invariants the walkthrough defect violated, stated as rules:
 *  - a new target NEVER displaces the base before it is ready —
 *    [onTarget] only registers the incoming layer;
 *  - the base changes ONLY through [onIncomingReady] (the incoming
 *    photo decoded and its transition finished) or through a null
 *    target (a resolution that produced no photo: the gradient state
 *    is the truthful paint, reached only once resolution completed —
 *    while a resolution is in flight the view model keeps the old
 *    URL in state, so a null target never means "still loading");
 *  - re-targeting mid-transition replaces the incoming layer, never
 *    the base.
 * Pure so the rules are unit-tested without composition.
 */
object HeroLayerPolicy {

    data class Layers(
        val baseUrl: String?,
        val incomingUrl: String?
    )

    /**
     * GEN-3 A-HERO (X-7): the layer state at a SCENE change (a new
     * place, or a new selected moment). The previous scene's photo
     * is discarded outright — it may never appear under the new
     * scene's label, not even for the incoming photo's decode time
     * (the walkthrough's Miami frame: New York's photo under
     * Miami's label for ~1.3 s). The new base is the new scene's own
     * bundled fallback [fallbackUrl] — synchronous and truthful for
     * the new scene's prescription — or the target itself when the
     * resolution already landed on it; the target, when it differs
     * from the fallback, becomes the incoming layer and fades in
     * over the fallback once decoded. When neither exists, the
     * gradient is the truthful paint (the census's defect cells).
     */
    fun onSceneChange(targetUrl: String?, fallbackUrl: String?): Layers = when {
        targetUrl == null -> Layers(baseUrl = fallbackUrl, incomingUrl = null)
        fallbackUrl == null || fallbackUrl == targetUrl ->
            Layers(baseUrl = targetUrl, incomingUrl = null)
        else -> Layers(baseUrl = fallbackUrl, incomingUrl = targetUrl)
    }

    /** Register [targetUrl] as the photo to transition to. */
    fun onTarget(layers: Layers, targetUrl: String?): Layers = when {
        targetUrl == null -> Layers(baseUrl = null, incomingUrl = null)
        targetUrl == layers.baseUrl -> Layers(baseUrl = layers.baseUrl, incomingUrl = null)
        targetUrl == layers.incomingUrl -> layers
        else -> Layers(baseUrl = layers.baseUrl, incomingUrl = targetUrl)
    }

    /** The incoming photo decoded and finished its transition: promote it. */
    fun onIncomingReady(layers: Layers): Layers =
        if (layers.incomingUrl != null) {
            Layers(baseUrl = layers.incomingUrl, incomingUrl = null)
        } else {
            layers
        }
}

/**
 * One decoded hero photo painted whole at a fixed alpha — the BASE
 * layer of the GEN-2 A2 layered hero. (Its bitmap was decoded as the
 * incoming layer before promotion, so Coil serves it from cache.)
 */
@Composable
private fun HeroPhotoLayer(
    url: String,
    widthPx: Int,
    heightPx: Int,
    alpha: Float,
    contentDescription: String,
    verticalBias: Float
) {
    val context = LocalContext.current
    val painter = coil.compose.rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .crossfade(false)
            .build()
    )
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = androidx.compose.ui.BiasAlignment(
            horizontalBias = 0f,
            verticalBias = verticalBias
        ),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    )
}

/**
 * The INCOMING layer of the GEN-2 A2 layered hero: invisible until
 * its image has fully decoded, then it fades in over the base photo
 * (300 ms, [HeroMotionTokens]) — or cuts, when [crossfade] is false
 * (a person change, [HeroTransitionPolicy]) — and reports readiness
 * so the caller can promote it to base. It never paints a loading or
 * half-decoded frame, and because it fades OVER the base, the
 * transition never passes through the page background.
 */
@Composable
private fun HeroIncomingPhotoLayer(
    url: String,
    widthPx: Int,
    heightPx: Int,
    crossfade: Boolean,
    contentDescription: String,
    verticalBias: Float,
    onReady: (String) -> Unit
) {
    val context = LocalContext.current
    val painter = coil.compose.rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .crossfade(false)
            .build()
    )
    val decoded = painter.state is coil.compose.AsyncImagePainter.State.Success
    val alpha = remember(url) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(url, decoded) {
        if (decoded) {
            if (crossfade) {
                alpha.animateTo(
                    1f,
                    androidx.compose.animation.core.tween(
                        HeroMotionTokens.HERO_CROSSFADE_IN_MS
                    )
                )
            } else {
                alpha.snapTo(1f)
            }
            onReady(url)
        }
    }
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = androidx.compose.ui.BiasAlignment(
            horizontalBias = 0f,
            verticalBias = verticalBias
        ),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
    )
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
