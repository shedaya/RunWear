package com.runwear.app.weariverun

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * WS-3 (RC-3) + GEN-2 (motion decision, Nov 4 2026): the
 * "Wear I've Run" element — ONE composable for the machine's present
 * states, hosted by HeroMainScreen as a sticky bottom dock IN the
 * home layout flow (never a root-Box overlay — D-06).
 *
 * GEN-2 replaces the three static state renderings with the
 * decision's single grow-and-solidify morph: ONE Animatable
 * `expansionProgress` (0 = MINI, 1 = EXPANDED) drives height, corner
 * radius, background tint alpha (mini tint 85% light / 82% dark ->
 * 1.00) and expanded-content opacity together — every property a
 * pure function of that value ([WearIveRunMotion]), so nothing can
 * desynchronize. Per-driver specs live in [WearIveRunMotion.specFor]:
 * cue entry spring(0.80, 400) with the decision's capped
 * micro-overshoot; tap spring(0.92, 400); end-of-scroll
 * tween(300 ms, EmphasizedDecelerate); collapse tween(280 ms,
 * EmphasizedAccelerate). The animation is retargetable at any
 * moment — a reversal starts from the current progress.
 *
 * Continuity anchors (§2): the wordmark and the narration sit in the
 * one top row in BOTH states — they never move, fade, or scale. The
 * chevron is ONE glyph rotating 180 degrees with progress. The
 * expanded-only section (thumbnail, detail line, and W-1's explicit
 * collection affordance) is clipped by the morphing height and
 * faded on the windowed ramp — never scaled or squished.
 *
 * The cue's pulse + soft haptic fire AT THE SETTLE — the first frame
 * progress reaches 1.00 in CUE — keyed only on the cue sequence; a
 * touch before the settle cancels the unfired pulse/haptic with the
 * timer. Reduced motion (animator duration scale 0, §6): the morph
 * becomes a <= 150 ms crossfade, no overshoot, no pulse — the soft
 * haptic is retained (it is in the closed §1.7 map and is not visual
 * motion). No haptic fires anywhere else on this surface except the
 * trash's light tick (§1.7).
 *
 * W-1 (a): the EXPANDED state's section carries an explicit, labeled
 * "View collection" control that opens the full collection — the
 * doorway is no longer only a hidden body tap (the body tap remains
 * as the secondary target).
 *
 * D-11: [dimmed] is the sheet-backdrop seam — when any sheet is up
 * over home the element dims with the home backdrop, and a morph in
 * flight finishes instantly to its target underneath the dim (§5.4).
 *
 * D-12: the thumbnail paints only once its image has DECODED — no
 * placeholder, no broken-image glyph, ever: the slot is a plain
 * tinted box until the photo lands.
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
    onOpenCollection: () -> Unit = {},
    transitionDriver: WearTransitionDriver = WearTransitionDriver.TAP,
    onTransitionRunningChanged: (Boolean) -> Unit = {}
) {
    if (state !is WearIveRunBarState.Present) return // Absent renders nothing
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    MorphBarContent(
        state = state,
        darkTheme = darkTheme,
        cueSequence = cueSequence,
        transitionDriver = transitionDriver,
        onExpandClick = onExpandClick,
        onCollapseClick = onCollapseClick,
        onRemoveClick = onRemoveClick,
        onOpenCollection = onOpenCollection,
        onTransitionRunningChanged = onTransitionRunningChanged,
        modifier = modifier.alpha(WearIveRunMiniBarPolicy.surfaceAlpha(dimmed)),
        dimmed = dimmed
    )
}

@Composable
private fun MorphBarContent(
    state: WearIveRunBarState.Present,
    darkTheme: Boolean,
    cueSequence: Int,
    transitionDriver: WearTransitionDriver,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onOpenCollection: () -> Unit,
    onTransitionRunningChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val kind = state.kind
    val target = if (kind == WearIveRunElementKind.MINI) 0f else 1f

    // The ONE progress value. First composition snaps to the state's
    // rest value (a state at rest does not animate into itself);
    // every later state change animates from the current value.
    val progress = remember { Animatable(target) }
    // The cue pulse rides its own value, fired at the settle.
    val pulse = remember { Animatable(1f) }
    // ABSENT -> CUE only: the 12 dp rise, folded into progress (§2).
    // Armed only when the element first composes already in CUE
    // (it was ABSENT a frame ago); a MINI -> CUE update never rises.
    var riseArmed by remember { mutableStateOf(kind == WearIveRunElementKind.CUE) }
    // The kind as snapshot state, so the settle watcher below can
    // observe it inside snapshotFlow across recompositions.
    var kindSnapshot by remember { mutableStateOf(kind) }
    SideEffect { kindSnapshot = kind }
    // Reduced-motion crossfade value (1 = fully rendered form).
    val fade = remember { Animatable(1f) }

    fun animatorScale(): Float = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )

    // The morph driver: retargets the one progress value whenever
    // the machine's state (or the host's driver for it) changes.
    LaunchedEffect(kind, transitionDriver, cueSequence) {
        if (progress.value == target) {
            onTransitionRunningChanged(false)
            return@LaunchedEffect
        }
        if (WearIveRunMotion.isReducedMotion(animatorScale())) {
            // §6: <= 150 ms crossfade between the fully rendered
            // forms — no height travel feel, no overshoot, no pulse.
            fade.snapTo(0.35f)
            launch { fade.animateTo(1f, WearIveRunMotion.reducedSpec()) }
            progress.animateTo(target, WearIveRunMotion.reducedSpec())
        } else {
            val driver = when {
                target == 0f -> WearTransitionDriver.COLLAPSE
                kind == WearIveRunElementKind.CUE -> WearTransitionDriver.CUE
                else -> transitionDriver
            }
            progress.animateTo(target, WearIveRunMotion.specFor(driver))
        }
        if (kind != WearIveRunElementKind.CUE) riseArmed = false
    }

    // §5.4: a sheet presenting mid-transition finishes the morph
    // instantly to its target underneath the dim.
    LaunchedEffect(dimmed, kind) {
        if (dimmed && progress.value != target) {
            progress.snapTo(target)
        }
    }

    // The host's scroll latch (§5.1/§5.3) keys on this.
    LaunchedEffect(progress.isRunning) {
        onTransitionRunningChanged(progress.isRunning)
    }

    // The cue's settle beats — keyed ONLY on the cue sequence, so a
    // landing fires them exactly once and content updates mid-cue
    // cannot restart them. The watcher waits for the settle (the
    // first frame progress reaches 1.00 while in CUE): if the user
    // touches the element first, the kind leaves CUE and the watcher
    // simply never fires for this sequence — the unfired pulse and
    // haptic cancel with the timer, as the decision requires.
    LaunchedEffect(cueSequence) {
        if (cueSequence <= 0) return@LaunchedEffect
        snapshotFlow { kindSnapshot to progress.value }
            .first { (k, p) -> k == WearIveRunElementKind.CUE && p >= 0.999f }
        // §1.7's cue tick, named by the map — at the settle, not at
        // the tap/fetch that started the cue. It fires in BOTH motion
        // modes: §6 disables the visual pulse under reduced motion
        // but retains the haptic (it is in the closed map and is not
        // visual motion).
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (WearIveRunMotion.isReducedMotion(animatorScale())) {
            return@LaunchedEffect // §6: no pulse under reduced motion.
        }
        // ONE pulse: 900 ms ease-in-out, exactly once, never loops.
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
        riseArmed = false
    }

    val p = progress.value
    val contentAlpha = WearIveRunMotion.expandedContentAlpha(p)
    val risePx = if (riseArmed && kind == WearIveRunElementKind.CUE) {
        (1f - p) * WearIveRunElementMachine.CUE_RISE_DP
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                translationY = risePx * density.density
                alpha = fade.value
            }
    ) {
        // The pine glow: its alpha rides the pulse's excursion and
        // is zero at rest — the glow exists only inside the pulse.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(WearIveRunMotion.cornerRadiusDp(p).dp))
                .background(
                    RunWearColors.PineAccent.copy(
                        alpha = ((pulse.value - 1f) / 0.03f).coerceIn(0f, 1f) * 0.18f
                    )
                )
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // The body tap: MINI expands; EXPANDED's body is
                    // the collection doorway (a navigation, not a
                    // state driver); a touch on a CUEING element
                    // cancels the timer and lands in EXPANDED.
                    when (kind) {
                        WearIveRunElementKind.EXPANDED -> onOpenCollection()
                        else -> onExpandClick()
                    }
                },
            shape = RoundedCornerShape(WearIveRunMotion.cornerRadiusDp(p).dp),
            // Grow-and-solidify: the background fades from the mini
            // tint to fully opaque WITH the same progress value
            // (color lerped between the two state tokens, alpha per
            // the decision's table — clamped, so the cue's overshoot
            // expresses in height alone).
            color = lerp(
                RunWearColors.MiniBarContainer,
                RunWearColors.Surface,
                p.coerceIn(0f, 1f)
            ).copy(alpha = WearIveRunMotion.backgroundAlpha(p, darkTheme)),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 4.dp,
                    top = (6f + 10f * p).dp,
                    bottom = (6f + 10f * p).dp
                )
            ) {
                // The anchor row — present, identically placed, in
                // both states: wordmark, narration, the rotating
                // chevron, and trash. It never moves or fades.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WearIveRunWordmark()
                    Spacer(Modifier.width(12.dp))
                    NarrationText(
                        narration = state.narration,
                        maxLines = if (kind == WearIveRunElementKind.MINI) {
                            1
                        } else {
                            Int.MAX_VALUE
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // ONE chevron glyph, rotating 180 degrees with
                    // progress — no icon swap, no pop (§2). Its
                    // action and description follow the state: in
                    // MINI it expands; in EXPANDED/CUE it collapses.
                    IconButton(
                        onClick = if (kind == WearIveRunElementKind.MINI) {
                            onExpandClick
                        } else {
                            onCollapseClick
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExpandLess,
                            contentDescription = if (kind == WearIveRunElementKind.MINI) {
                                "Expand Wear I've Run"
                            } else {
                                "Collapse Wear I've Run"
                            },
                            tint = RunWearColors.TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = 180f * p }
                        )
                    }
                    IconButton(onClick = {
                        // §1.7: the element's trash fires the light
                        // tick — the same single control serves both
                        // states, so the tick is identical by
                        // construction.
                        haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                        onRemoveClick()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove from Wear I've Run",
                            tint = RunWearColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                // The expanded-only section: clipped by the morphing
                // height (its container is exactly full-height * p)
                // and faded on the windowed ramp with a <= 4 dp
                // upward drift — text is clipped and faded, never
                // scaled or squished (§2/§7.5).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((WearIveRunMotion.EXPANDED_SECTION_HEIGHT_DP * p).dp)
                        .clip(RoundedCornerShape(0.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(contentAlpha)
                            .graphicsLayer {
                                translationY =
                                    WearIveRunMotion.expandedContentDriftDp(p) * density.density
                            }
                            .padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail slot — D-12: the photo paints
                            // ONLY once decoded (Success); until then
                            // the slot is a plain tinted box.
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
                            // WS-4 (D-08): the detail line — feels-like
                            // and kit count only; place and day live in
                            // the narration and are NOT repeated here.
                            WearIveRunSubtitle.detail(state.entry)?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RunWearColors.TextSecondary
                                )
                            }
                        }
                        // W-1 (a): the explicit doorway into the full
                        // collection — a labeled, visible control in
                        // the expanded state, not a hidden tap target.
                        androidx.compose.material3.FilledTonalButton(
                            onClick = onOpenCollection,
                            modifier = Modifier.padding(top = 10.dp),
                            colors = androidx.compose.material3.ButtonDefaults
                                .filledTonalButtonColors(
                                    containerColor = RunWearColors.PineAccent.copy(alpha = 0.14f),
                                    contentColor = RunWearColors.PineAccent
                                )
                        ) {
                            Text(
                                text = "View collection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "View the full Wear I've Run collection",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The wordmark in the genuine Great Vibes (bundled RW-25 as
 * res/font/greatvibes_regular.ttf). This element is the bar's one
 * justified custom rendering.
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
