package com.runwear.app.weariverun

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RW-135 (Direction A-prime, APPROVED consolidated spec Nov 7,
 * 2026 — DESIGN_AND_ANIMATION_SPEC.md): the Wear I've Run Home
 * presence — a floating MINI dock plus the expanded FOOTER
 * section, choreographed per the approved spec. This file
 * supersedes the fix wave's interim footer (spec section 10):
 * the handoff is the continuous position-derived dissolve
 * (section 3), the footer is full-bleed on the page ground with
 * 24dp top corners only (section 2.2), glides follow the
 * section 4.4 duration rule, and creation / update / delete
 * follow sections 4.1 / 4.3 / 4.7 with the section 5
 * Reduce-Motion table (Android expression: animator duration
 * scale 0).
 *
 * Both surfaces render the RECORDED entry and nothing else
 * (spec section 1.2): their line comes from
 * [WearIveRunDockNarration], a pure function of the record. The
 * in-flow band card this replaced stays retired.
 */
object WearIveRunDockPolicy {

    /** MINI geometry (spec section 2.1): inset, lift above the
     * bottom safe edge, and the radius that never animates. */
    const val DOCK_INSET_DP = 12
    const val DOCK_BOTTOM_DP = 14
    const val DOCK_RADIUS_DP = 16

    /** The narration cross-fade (spec section 4.3): opacity only. */
    const val NARRATION_CROSSFADE_MS = 210

    // -- The handoff (spec section 3) --------------------------------
    // a = how far (dp) the footer's top edge is above the scroll
    // viewport's bottom edge. The dissolve runs across the 37dp
    // window in which a travels 83 -> 120 (spec section 11.2:
    // the 37dp window is kept exactly).

    /** a (dp) at which the dissolve starts. */
    const val HANDOFF_START_DP = 83f

    /** The dissolve window's width (dp). */
    const val HANDOFF_WINDOW_DP = 37f

    /** The dock's sink (dp) across the dissolve. */
    const val HANDOFF_SINK_DP = 12f

    /** The dock's scale at full fade (1 - 0.02). */
    const val HANDOFF_SCALE_DROP = 0.02f

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    /** The handoff fade for a footer position [aDp]: 0 at or
     * below 83dp, 1 at or above 120dp, smoothstep between. */
    fun fade(aDp: Float): Float =
        smoothstep(((aDp - HANDOFF_START_DP) / HANDOFF_WINDOW_DP).coerceIn(0f, 1f))

    /** Dock opacity across the handoff: 1 - fade. */
    fun dockOpacity(fade: Float): Float = 1f - fade

    /** Dock sink (dp) across the handoff: 12 * fade. */
    fun dockSinkDp(fade: Float): Float = HANDOFF_SINK_DP * fade

    /** Dock scale across the handoff: 1 - 0.02 * fade. */
    fun dockScale(fade: Float): Float = 1f - HANDOFF_SCALE_DROP * fade

    /** Hit-testing rule (spec section 3): the dock is
     * hit-testable only while its fade is below 0.5. */
    fun dockHitTestable(fade: Float): Boolean = fade < 0.5f

    // -- The glide (spec section 4.4) --------------------------------

    /**
     * Glide duration from distance: clamp(450ms, 350 + 0.55 x
     * distanceDp, 950ms) — the spec section 4.4 rule. Pure;
     * [distanceDp] may be signed (the chevron's inverse glide
     * passes its delta); the magnitude rules.
     */
    fun glideDurationMs(distanceDp: Float): Int =
        (350.0 + 0.55 * abs(distanceDp)).roundToInt().coerceIn(450, 950)

    // -- Motion tokens (spec section 4) -------------------------------

    /** enter = easeOutQuint cubic-bezier(0.22, 1, 0.36, 1). */
    val ENTER_EASING = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** morph = cubic-bezier(0.32, 0.72, 0, 1). */
    val MORPH_EASING = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** glide = cubic-bezier(0.65, 0, 0.15, 1). */
    val GLIDE_EASING = CubicBezierEasing(0.65f, 0f, 0.15f, 1f)

    /** exit = easeInQuad cubic-bezier(0.5, 0, 0.75, 0). */
    val EXIT_EASING = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

    /** fold = easeInOut cubic-bezier(0.65, 0, 0.35, 1). */
    val FOLD_EASING = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Creation (spec section 4.1): dock rise + fade, 520ms enter. */
    const val CREATION_RISE_MS = 520

    /** Creation (spec section 4.1): footer grow, 480ms fold. */
    const val CREATION_GROW_MS = 480

    /** The creation pulse / update bounce (spec sections 4.1/4.3). */
    const val CUE_PULSE_MS = 900
    const val CUE_PULSE_SCALE = 1.018f
    const val UPDATE_BOUNCE_SCALE = 1.015f

    /** Footer re-wrap height morph (spec section 4.5): 380ms morph. */
    const val REWRAP_MS = 380

    /** Delete (spec section 4.7): footer content fade 160ms, then
     * the height folds to 0 over 400ms fold; the dock fades and
     * sinks 26dp over 300ms exit, concurrently. */
    const val DELETE_CONTENT_FADE_MS = 160
    const val DELETE_FOLD_MS = 400
    const val DELETE_DOCK_EXIT_MS = 300
    const val DELETE_DOCK_SINK_DP = 26f

    /** Footer geometry (spec section 2.2): 34dp of sheet above
     * the top edge; internal padding 20 top / 16 sides / 32 bottom. */
    const val FOOTER_SHEET_GAP_DP = 34
    const val FOOTER_TOP_CORNER_DP = 24
    const val FOOTER_SHADOW_STRIP_DP = 18
}

/** Whether the system animator duration scale is 0 — the Android
 * expression of Reduce Motion (spec section 5). */
@Composable
private fun rememberAnimatorScale(): Float {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }
}

/**
 * Lays the content out at [fraction] of its natural height,
 * clipped — the height mechanism for the footer's creation grow
 * (spec section 4.1) and delete fold (section 4.7) inside the
 * lazy hosts, where the item's own height is the animation.
 */
private fun Modifier.heightFraction(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(maxHeight = Constraints.Infinity)
        )
        val h = (placeable.height * fraction).roundToInt().coerceAtLeast(0)
        layout(placeable.width, h) {
            placeable.placeWithLayer(0, 0) { clip = true }
        }
    }

/**
 * The creation cue's pine ring (spec section 4.1): a rounded-rect
 * border in pine blooming — scale ~1 -> 1.05, alpha ~0.45 -> 0 —
 * across the pulse. [progress] runs 0 -> 1 over the pulse.
 */
@Composable
private fun PineRing(progress: Float, radiusDp: Int, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = 1f + 0.05f * progress
                scaleX = s
                scaleY = s
            }
            .border(
                width = 2.dp,
                color = RunWearColors.PineAccent.copy(alpha = 0.45f * (1f - progress)),
                shape = RoundedCornerShape(radiusDp.dp)
            )
    )
}

/** The narration line with its payload span in pine semibold. */
@Composable
private fun DockNarrationText(
    narration: WearIveRunNarration,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    val annotated = buildAnnotatedString {
        append(narration.text.substring(0, narration.payloadStart))
        withStyle(
            SpanStyle(
                color = RunWearColors.PineAccent,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(narration.payload)
        }
        append(narration.text.substring(narration.payloadEnd))
    }
    Text(
        text = annotated,
        fontSize = fontSizeSp.sp,
        color = RunWearColors.TextSecondary,
        maxLines = maxLines,
        modifier = modifier
    )
}

/**
 * The MINI dock: script wordmark 17pt + the record's line +
 * expand/delete controls, translucent card at radius 16, soft
 * shadow. Tapping the bar (or the expand control) is the jump to
 * the footer — there is no separate jump chip. Delete is one
 * tap, no confirmation. The handoff dissolve (spec section 3)
 * is applied by the HOST's wrapper (graphicsLayer from the
 * position-derived fade); this composable owns the creation
 * entrance (section 4.1), the press (section 4.4), the creation
 * pulse + pine ring (section 4.1), and the update bounce
 * (section 4.3).
 *
 * [cueKey]/[cueArmed]: the creation cue — armed by the host only
 * for a session CREATION (an Absent -> Present flip with a fresh
 * cue sequence), never for an update (the update bounce is
 * [bounceKey]'s, and the two never double — spec section 4.3).
 * A cold-start presence (entry already there at first
 * composition) carries cueKey 0 and gets NO entrance.
 *
 * [entranceKey]: the creation this dock rises for; 0 = no
 * entrance (cold start / restore). [hitTestable]: the section 3
 * rule (fade < 0.5), applied to every control.
 */
@Composable
fun WearIveRunDock(
    narration: WearIveRunNarration,
    cueKey: Int,
    cueArmed: Boolean,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    entranceKey: Int = 0,
    bounceKey: Int = 0,
    hitTestable: Boolean = true,
    reduceMotion: Boolean = false
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val animatorScale = rememberAnimatorScale()
    val haptic = LocalHapticFeedback.current
    val pulse = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    var ringActive by remember { mutableStateOf(false) }
    var lastPulsedCue by remember { mutableIntStateOf(0) }
    val bounce = remember { Animatable(1f) }
    var lastBouncedKey by remember { mutableIntStateOf(0) }

    // Creation pulse + pine ring (spec section 4.1): one gentle
    // pulse — scale to 1.018 with the ring blooming and decaying
    // across 900ms, plus one soft haptic. Reduce Motion (spec
    // section 5): no pulse, no ring; the haptic is kept.
    LaunchedEffect(cueKey, cueArmed) {
        if (!cueArmed || cueKey <= 0 || cueKey == lastPulsedCue) {
            return@LaunchedEffect
        }
        lastPulsedCue = cueKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion && animatorScale > 0f) {
            ringActive = true
            ring.snapTo(0f)
            val ringJob = launchRing(ring)
            // One gentle pulse: 900 ms ease-in-out, exactly once.
            pulse.animateTo(
                WearIveRunDockPolicy.CUE_PULSE_SCALE,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            pulse.animateTo(
                1f,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            ringJob.join()
            ringActive = false
        }
    }

    // Update bounce (spec section 4.3, Sol's Nov 7 ruling): when
    // the shown line changes to a different record line for the
    // SAME entry, the visible surface scales to ~1.015 overshoot
    // and back + one soft haptic. The host keys this only for a
    // genuine update landing at scroll rest — never for the first
    // line and never alongside the creation cue. Reduce Motion:
    // the bounce is removed, the haptic kept (spec section 5).
    LaunchedEffect(bounceKey) {
        if (bounceKey <= 0 || bounceKey == lastBouncedKey) {
            return@LaunchedEffect
        }
        lastBouncedKey = bounceKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion && animatorScale > 0f) {
            bounce.animateTo(
                WearIveRunDockPolicy.UPDATE_BOUNCE_SCALE,
                tween(durationMillis = 140, easing = EaseInOut)
            )
            bounce.animateTo(
                1f,
                tween(durationMillis = 160, easing = EaseInOut)
            )
        }
    }

    // Creation entrance (spec section 4.1): rises from +160% of
    // its height with a fade over 520ms enter easing. Reduce
    // Motion: fade in 220ms only, no rise (spec section 5).
    val entrance = remember {
        Animatable(if (entranceKey > 0) 0f else 1f)
    }
    var dockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(entranceKey) {
        if (entranceKey <= 0) return@LaunchedEffect
        entrance.snapTo(0f)
        if (reduceMotion) {
            entrance.animateTo(1f, tween(durationMillis = 220))
        } else {
            entrance.animateTo(
                1f,
                tween(
                    durationMillis = WearIveRunDockPolicy.CREATION_RISE_MS,
                    easing = WearIveRunDockPolicy.ENTER_EASING
                )
            )
        }
    }

    // Press (spec section 4.4): the dock scales to 0.97 over
    // 120ms; the light haptic fires on release (the click).
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "wirDockPress"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = entrance.value
                if (!reduceMotion) {
                    translationY = (1f - entrance.value) * 1.6f * dockHeightPx
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { dockHeightPx = it.height }
                .graphicsLayer {
                    val s = pulse.value * bounce.value * pressScale
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    enabled = hitTestable
                ) {
                    haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                    onExpand()
                },
            shape = RoundedCornerShape(WearIveRunDockPolicy.DOCK_RADIUS_DP.dp),
            color = RunWearColors.MiniBarContainer.copy(
                alpha = WearIveRunMiniBarPolicy.backgroundAlpha(
                    isFullExpression = false,
                    darkTheme = darkTheme
                )
            ),
            shadowElevation = 6.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = WearIveRunNarrationFormatter.WORDMARK,
                    fontFamily = GreatVibesFontFamily,
                    fontSize = 17.sp,
                    color = RunWearColors.TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.width(10.dp))
                Crossfade(
                    targetState = narration,
                    animationSpec = tween(
                        WearIveRunDockPolicy.NARRATION_CROSSFADE_MS
                    ),
                    label = "wirDockNarration",
                    modifier = Modifier.weight(1f)
                ) { line ->
                    DockNarrationText(narration = line, fontSizeSp = 12f)
                }
                // Spec section 9.2: the dock's expand control is
                // the corners-out glyph (goes to the footer).
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                        onExpand()
                    },
                    enabled = hitTestable
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fullscreen,
                        contentDescription = "Expand Wear I've Run",
                        tint = RunWearColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                        onDelete()
                    },
                    enabled = hitTestable
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove from Wear I've Run",
                        tint = RunWearColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (ringActive) {
            PineRing(
                progress = ring.value,
                radiusDp = WearIveRunDockPolicy.DOCK_RADIUS_DP,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** Runs the pine ring's 900ms bloom as a child job of the cue. */
private fun kotlinx.coroutines.CoroutineScope.launchRing(
    ring: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
) = launch {
    ring.animateTo(
        1f,
        tween(durationMillis = WearIveRunDockPolicy.CUE_PULSE_MS)
    )
}

/**
 * The EXPANDED form (spec section 2.2): the entry itself as the
 * page's final section — FULL-BLEED on the page ground (light
 * #F5F1E8, dark #2A251E via [RunWearColors.FooterGround]), top
 * corners 24dp only, bottom square; a 1dp hairline at ink 4% on
 * the top edge and the top shadow expressed as a soft gradient
 * strip above the section (black 7% fading to transparent over
 * ~18dp — the Material expression of the spec's 0/-10/28/7%
 * shadow); [sheetGapDp] of sheet between the preceding section
 * and the footer's top edge (34dp per spec; the grid hosts pass
 * 24 because their 10dp item spacing completes the 34). Internal
 * padding 20 top / 16 sides / 32 bottom. Anatomy: header row
 * (script 31pt + chevron-down + delete), narration 15pt, the
 * entry row (56dp thumbnail radius 14 + the entry line 14.5pt),
 * and the collection doorway.
 *
 * Choreography owned here: the creation grow (section 4.1,
 * keyed by [growInKey] — 0 = silent insertion), the header-zone
 * creation pulse + ring and update bounce when the dock is
 * handed off ([cueArmed]/[bounceKey], armed by the host under
 * the same rules as the dock's), the field cross-fades and
 * re-wrap height morph (section 4.5), and the delete fold
 * (section 4.7, driven by [exiting]; [onExitFinished] fires when
 * the fold completes — the host removes the data then, never
 * before).
 */
@Composable
fun WearIveRunFooterSection(
    narration: WearIveRunNarration,
    entry: WearIveRunEntry,
    entryLine: String,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onOpenCollection: () -> Unit,
    modifier: Modifier = Modifier,
    sheetGapDp: Int = WearIveRunDockPolicy.FOOTER_SHEET_GAP_DP,
    growInKey: Int = 0,
    cueKey: Int = 0,
    cueArmed: Boolean = false,
    bounceKey: Int = 0,
    exiting: Boolean = false,
    reduceMotion: Boolean = false,
    onExitFinished: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val animatorScale = rememberAnimatorScale()

    // Height fraction: 1 at rest; the creation grow runs it
    // 0 -> 1 (480ms fold, content fading in across the second
    // half — spec section 4.1); the delete fold runs it -> 0
    // (400ms fold — section 4.7). Reduce Motion: height snaps.
    val heightFraction = remember {
        Animatable(if (growInKey > 0) 0f else 1f)
    }
    var growing by remember { mutableStateOf(growInKey > 0) }
    LaunchedEffect(growInKey) {
        if (growInKey <= 0) return@LaunchedEffect
        growing = true
        heightFraction.snapTo(0f)
        if (reduceMotion) {
            heightFraction.snapTo(1f)
        } else {
            heightFraction.animateTo(
                1f,
                tween(
                    durationMillis = WearIveRunDockPolicy.CREATION_GROW_MS,
                    easing = WearIveRunDockPolicy.FOLD_EASING
                )
            )
        }
        growing = false
    }

    // Delete / session-end exit (spec sections 4.7/4.8): content
    // fades 160ms, then the height folds to 0 over 400ms fold.
    // Reduce Motion: fade 200ms, height snaps (section 5).
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        if (reduceMotion) {
            contentAlpha.animateTo(0f, tween(durationMillis = 200))
            heightFraction.snapTo(0f)
        } else {
            contentAlpha.animateTo(
                0f,
                tween(durationMillis = WearIveRunDockPolicy.DELETE_CONTENT_FADE_MS)
            )
            heightFraction.animateTo(
                0f,
                tween(
                    durationMillis = WearIveRunDockPolicy.DELETE_FOLD_MS,
                    easing = WearIveRunDockPolicy.FOLD_EASING
                )
            )
        }
        onExitFinished()
    }

    // Header-zone pulse / bounce (spec sections 4.1/4.3): the
    // host arms these only when the dock is handed off, so the
    // cue lands on the surface the user can see — exactly one
    // surface ever pulses or bounces for an event.
    val headerScale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    var ringActive by remember { mutableStateOf(false) }
    var lastPulsedCue by remember { mutableIntStateOf(0) }
    var lastBouncedKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(cueKey, cueArmed) {
        if (!cueArmed || cueKey <= 0 || cueKey == lastPulsedCue) {
            return@LaunchedEffect
        }
        lastPulsedCue = cueKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion && animatorScale > 0f) {
            ringActive = true
            ring.snapTo(0f)
            val ringJob = launchRing(ring)
            headerScale.animateTo(
                WearIveRunDockPolicy.CUE_PULSE_SCALE,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            headerScale.animateTo(
                1f,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            ringJob.join()
            ringActive = false
        }
    }
    LaunchedEffect(bounceKey) {
        if (bounceKey <= 0 || bounceKey == lastBouncedKey) {
            return@LaunchedEffect
        }
        lastBouncedKey = bounceKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion && animatorScale > 0f) {
            headerScale.animateTo(
                WearIveRunDockPolicy.UPDATE_BOUNCE_SCALE,
                tween(durationMillis = 140, easing = EaseInOut)
            )
            headerScale.animateTo(
                1f,
                tween(durationMillis = 160, easing = EaseInOut)
            )
        }
    }

    val growAlpha = if (growing) {
        ((heightFraction.value - 0.5f) * 2f).coerceIn(0f, 1f)
    } else {
        1f
    }

    Column(modifier = modifier.heightFraction(heightFraction.value)) {
        // The sheet gap (spec section 2.2): card sheet between
        // the preceding section and the footer's top edge; the
        // shadow strip occupies its last ~18dp.
        Spacer(
            Modifier.height(
                (sheetGapDp - WearIveRunDockPolicy.FOOTER_SHADOW_STRIP_DP)
                    .coerceAtLeast(0).dp
            )
        )
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlpha.value * growAlpha
            }
        ) {
            // The top shadow (spec section 2.2: 0/-10pt, 28pt
            // blur, black 7%) expressed as a soft gradient strip
            // above the section: black 7% at the footer's edge
            // fading to transparent over ~18dp.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WearIveRunDockPolicy.FOOTER_SHADOW_STRIP_DP.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.07f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = WearIveRunDockPolicy.FOOTER_TOP_CORNER_DP.dp,
                            topEnd = WearIveRunDockPolicy.FOOTER_TOP_CORNER_DP.dp
                        )
                    )
                    .background(RunWearColors.FooterGround)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top hairline: 1dp at ink 4% (spec section 2.2).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                RunWearColors.TextPrimary.copy(alpha = 0.04f)
                            )
                    )
                    Column(
                        modifier = Modifier
                            .animateContentSize(
                                tween(
                                    durationMillis = WearIveRunDockPolicy.REWRAP_MS,
                                    easing = WearIveRunDockPolicy.MORPH_EASING
                                )
                            )
                            .padding(
                                start = 16.dp, end = 16.dp,
                                top = 20.dp, bottom = 32.dp
                            )
                    ) {
                        // Header zone: wordmark + chevron-down +
                        // delete; the creation pulse / update
                        // bounce play here when the dock is
                        // handed off (spec sections 4.1/4.3).
                        Box {
                            Column(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = headerScale.value
                                    scaleY = headerScale.value
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = WearIveRunNarrationFormatter.WORDMARK,
                                        fontFamily = GreatVibesFontFamily,
                                        fontSize = 31.sp,
                                        color = RunWearColors.TextPrimary,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = onCollapse) {
                                        Icon(
                                            imageVector = Icons.Outlined.ExpandMore,
                                            contentDescription = "Collapse Wear I've Run",
                                            tint = RunWearColors.TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                                        onDelete()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Remove from Wear I've Run",
                                            tint = RunWearColors.TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Crossfade(
                                    targetState = narration,
                                    animationSpec = tween(
                                        WearIveRunDockPolicy.NARRATION_CROSSFADE_MS
                                    ),
                                    label = "wirFooterNarration"
                                ) { line ->
                                    DockNarrationText(
                                        narration = line,
                                        fontSizeSp = 15f,
                                        maxLines = 2
                                    )
                                }
                            }
                            if (ringActive) {
                                PineRing(
                                    progress = ring.value,
                                    radiusDp = 12,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCollection() }
                        ) {
                            // Thumbnail (spec section 4.5): cross-
                            // fades when the record's hero image
                            // changes, keyed on the URL, keeping
                            // the decode-gated paint rule — until
                            // decoded the slot is a plain tinted
                            // box, never a broken-image glyph.
                            Crossfade(
                                targetState = entry.thumbnailUrl,
                                animationSpec = tween(
                                    WearIveRunDockPolicy.NARRATION_CROSSFADE_MS
                                ),
                                label = "wirFooterThumbnail"
                            ) { url ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(RunWearColors.Background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    url?.let {
                                        var decoded by remember(it) {
                                            mutableStateOf(false)
                                        }
                                        AsyncImage(
                                            model = it,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.TopCenter,
                                            onState = { painterState ->
                                                if (painterState is
                                                    AsyncImagePainter.State.Success
                                                ) {
                                                    decoded = true
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .alpha(if (decoded) 1f else 0f)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            // Entry line (spec section 4.5):
                            // cross-fades in place, 210ms, keyed
                            // on the line.
                            Crossfade(
                                targetState = entryLine,
                                animationSpec = tween(
                                    WearIveRunDockPolicy.NARRATION_CROSSFADE_MS
                                ),
                                label = "wirFooterEntryLine",
                                modifier = Modifier.weight(1f)
                            ) { line ->
                                Text(
                                    text = line,
                                    fontSize = 14.5.sp,
                                    color = RunWearColors.TextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Open the collection ›",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RunWearColors.PineAccent,
                            modifier = Modifier
                                .clickable { onOpenCollection() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
