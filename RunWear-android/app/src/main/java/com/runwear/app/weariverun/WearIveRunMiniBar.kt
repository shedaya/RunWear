package com.runwear.app.weariverun

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors

/**
 * WS-3 (RC-3): the "Wear I've Run" element — ONE composable for the
 * machine's present states, hosted by HeroMainScreen as a sticky
 * bottom dock IN the home layout flow (never a root-Box overlay —
 * D-06).
 *
 * Both present states carry the SAME two icon buttons at the right
 * end — expand/collapse (the glyph flips with the state) and trash —
 * with identical semantics in both states (D-10: the expanded
 * state's old "Remove" TEXT button is gone). Tapping the mini bar's
 * body expands it (D-07: it no longer opens the collection; the
 * collection doorway is the EXPANDED card's body tap, which is a
 * navigation, not a state driver). Content renders at full opacity
 * in both states; the mini ground takes the settled tint (85% light
 * / 82% dark, [WearIveRunMiniBarPolicy]); the expanded ground is the
 * design's fully opaque sheet.
 *
 * CUE renders the expanded content with the cue motion: a 12 dp rise
 * (350 ms), then ONE 900 ms ease-in-out pulse with a pine glow and
 * ONE soft haptic at the START of the pulse — keyed on the cue
 * sequence so it fires exactly once per landing and never loops.
 * No haptic fires anywhere else on this surface (§1.7).
 *
 * D-11: [dimmed] is the sheet-backdrop seam — when any sheet is up
 * over home the element dims with the home backdrop.
 *
 * D-12: the expanded content (wordmark, subtitle, narration, both
 * controls) is present from frame one. The thumbnail paints only
 * once its image has DECODED — no placeholder, no broken-image
 * glyph, ever: the slot is a plain tinted box until the photo lands.
 */
@Composable
fun WearIveRunMiniBar(
    state: WearIveRunBarState,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
    cueSequence: Int = 0,
    dimmed: Boolean = false,
    onOpenCollection: () -> Unit = {}
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val surfaceModifier = modifier.alpha(WearIveRunMiniBarPolicy.surfaceAlpha(dimmed))
    when (state) {
        WearIveRunBarState.Absent -> Unit // the dock guards this too
        is WearIveRunBarState.Mini -> MiniBarContent(
            state = state,
            darkTheme = darkTheme,
            onExpandClick = onExpandClick,
            onRemoveClick = onRemoveClick,
            modifier = surfaceModifier
        )
        is WearIveRunBarState.Expanded -> ExpandedBarContent(
            state = state,
            darkTheme = darkTheme,
            onCollapseClick = onCollapseClick,
            onRemoveClick = onRemoveClick,
            onBodyClick = onOpenCollection,
            modifier = surfaceModifier
        )
        is WearIveRunBarState.Cue -> CueBarContent(
            state = state,
            darkTheme = darkTheme,
            cueSequence = cueSequence,
            onExpandClick = onExpandClick,
            onCollapseClick = onCollapseClick,
            onRemoveClick = onRemoveClick,
            modifier = surfaceModifier
        )
    }
}

@Composable
private fun MiniBarContent(
    state: WearIveRunBarState.Mini,
    darkTheme: Boolean,
    onExpandClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onExpandClick() },
        shape = RoundedCornerShape(20.dp),
        // Translucent sheet surface (policy alpha); content below is
        // drawn at full opacity by construction.
        // RW-26 tints: light rgba(253,251,246,.85), dark
        // rgba(42,37,30,.82) — the MiniBarContainer token at the
        // policy alpha; text/icons stay 100%.
        color = RunWearColors.MiniBarContainer.copy(
            alpha = WearIveRunMiniBarPolicy.backgroundAlpha(
                isFullExpression = false,
                darkTheme = darkTheme
            )
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WearIveRunWordmark()
            Spacer(Modifier.width(12.dp))
            NarrationText(
                narration = state.narration,
                modifier = Modifier.weight(1f)
            )
            // The two right-end controls, identical in both states:
            // expand (glyph flips to collapse when expanded) + trash.
            // Grayed affordances: 14dp glyphs on full 48dp M3
            // IconButton hit areas.
            IconButton(onClick = onExpandClick) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = "Expand Wear I've Run",
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove from Wear I've Run",
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * The cue presentation: the expanded content, risen into place with
 * one pulse. The effect is keyed ONLY on [cueSequence] — content
 * updates mid-cue (a thumbnail landing, a silent content refresh)
 * recompose this without restarting the motion, the haptic, or (in
 * the host) the timeout.
 */
@Composable
private fun CueBarContent(
    state: WearIveRunBarState.Cue,
    darkTheme: Boolean,
    cueSequence: Int,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pulse = remember { Animatable(1f) }
    val rise = remember { Animatable(WearIveRunElementMachine.CUE_RISE_DP) }
    LaunchedEffect(cueSequence) {
        if (cueSequence <= 0) return@LaunchedEffect
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (!WearIveRunMiniBarPolicy.cuePulseEnabled(scale)) {
            // Reduced motion (§1.0 rule 4): no rise, no pulse, no haptic.
            rise.snapTo(0f)
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        // Rise 12 dp into place (350 ms, emphasized-decelerate)…
        rise.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = WearIveRunElementMachine.CUE_RISE_MS.toInt(),
                easing = FastOutSlowInEasing
            )
        )
        // …then ONE pulse (900 ms ease-in-out), with the soft haptic
        // at the START of the pulse — and then still. Never loops.
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pulse.animateTo(
            targetValue = 1.03f,
            animationSpec = tween(
                durationMillis = (WearIveRunElementMachine.CUE_PULSE_MS / 2).toInt(),
                easing = EaseInOut
            )
        )
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (WearIveRunElementMachine.CUE_PULSE_MS / 2).toInt(),
                easing = EaseInOut
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                translationY = rise.value * density
            }
    ) {
        // The pine glow: its alpha rides the pulse's excursion and
        // is zero at rest — the glow exists only inside the pulse.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    RunWearColors.PineAccent.copy(
                        alpha = ((pulse.value - 1f) / 0.03f).coerceIn(0f, 1f) * 0.18f
                    )
                )
        )
        ExpandedBarContent(
            state = WearIveRunBarState.Expanded(state.narration, state.entry),
            darkTheme = darkTheme,
            onCollapseClick = onCollapseClick,
            onRemoveClick = onRemoveClick,
            // A touch on the cueing element cancels the timer and
            // obeys the touch: the body tap lands in EXPANDED.
            onBodyClick = onExpandClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExpandedBarContent(
    state: WearIveRunBarState.Expanded,
    darkTheme: Boolean,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onBodyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onBodyClick() },
        shape = RoundedCornerShape(20.dp),
        // Full expression: fully opaque sheet surface.
        color = RunWearColors.Surface.copy(
            alpha = WearIveRunMiniBarPolicy.backgroundAlpha(
                isFullExpression = true,
                darkTheme = darkTheme
            )
        ),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail slot — D-12: the photo paints ONLY once
                // decoded (Success); until then the slot is a plain
                // tinted box. No placeholder drawable, no broken-image
                // icon in any frame. All other content is present
                // from frame one regardless of the photo.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RunWearColors.BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    state.entry.thumbnailUrl?.let { url ->
                        var decoded by remember(url) { mutableStateOf(false) }
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onState = { painterState ->
                                if (painterState is AsyncImagePainter.State.Success) {
                                    decoded = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (decoded) 1f else 0f)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    WearIveRunWordmark()
                    // WS-4 (D-08): the subtitle is the entry's detail
                    // line (feels-like · kit count) — place and day
                    // live in the narration below and are NOT repeated
                    // here (the duplication defect).
                    WearIveRunSubtitle.detail(state.entry)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // WS-4 (D-09): the expanded narration wraps
                    // without limit — no mid-phrase truncation. The
                    // mini line stays one line, short by construction
                    // (simple place / first kit item payloads).
                    NarrationText(
                        narration = state.narration,
                        maxLines = Int.MAX_VALUE,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // The SAME two right-end controls as the mini state:
                // collapse (the expand glyph, flipped) + trash — the
                // expanded "Remove" text button is retired (D-10).
                IconButton(onClick = onCollapseClick) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Collapse Wear I've Run",
                        tint = RunWearColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove from Wear I've Run",
                        tint = RunWearColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * The wordmark in the genuine Great Vibes (bundled RW-25 as
 * res/font/greatvibes_regular.ttf; the RW-26 phase-2 swap replaces
 * the phase-1 FontFamily.Cursive fallback call site). This element
 * is the bar's one justified custom rendering.
 */
@Composable
private fun WearIveRunWordmark() {
    Text(
        text = WearIveRunNarrationFormatter.WORDMARK,
        fontFamily = GreatVibesFontFamily,
        fontSize = 19.sp,
        fontWeight = FontWeight.Normal,
        color = RunWearColors.TextPrimary,
        maxLines = 1
    )
}

/** The narration line: base ink with the payload span in pine. */
@Composable
private fun NarrationText(
    narration: WearIveRunNarration,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val annotated = buildAnnotatedString {
        append(narration.text.substring(0, narration.payloadStart))
        withStyle(SpanStyle(color = RunWearColors.PineAccent, fontWeight = FontWeight.Medium)) {
            append(narration.payload)
        }
        append(narration.text.substring(narration.payloadEnd))
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = RunWearColors.TextPrimary,
        maxLines = maxLines,
        modifier = modifier
    )
}
