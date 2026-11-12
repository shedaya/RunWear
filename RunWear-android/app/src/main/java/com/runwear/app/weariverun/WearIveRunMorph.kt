package com.runwear.app.weariverun

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import kotlin.math.roundToInt

/**
 * RW-169 §7A (Nov 11, 2026): ONE element, two geometries.
 *
 * The pre-fix home overlay arbitrated TWO complete layouts — an
 * AnimatedContent crossfade between WearIveRunDock and
 * WearIveRunFooterSection with a SizeTransform — the construction
 * RW-152 §1.3's acceptance ("one element transforming, never two
 * elements swapping") was written against, and the class behind
 * diagnosis D3/D4.
 *
 * This surface is a SINGLE layout of the record's content — one
 * wordmark instance, one narration instance, one controls slot,
 * one entry row, one doorway — whose GEOMETRY interpolates
 * between the mini and expanded rest geometries:
 *
 * - the wordmark's font size lerps 17sp -> 31sp in place (its
 *   width is fixed-claim at every progress value, §7B);
 * - the narration's slot lerps from beside the wordmark (mini) to
 *   the full width below it (expanded), wrapping as it goes — the
 *   bar grows, the payload never truncates (§7B / spec §4.3);
 * - the container height is the interpolation of the two REST
 *   heights, both read from this one layout's own measure (the
 *   header block's height and the full content height) — no
 *   second layout is ever composed to be measured, and no height
 *   is seeded from a measurement pass that runs during the morph;
 * - the expanded-only region (entry row + doorway) fades in over
 *   the back half of the progress (the RW-152 dissolve window:
 *   alpha = clamp((p - 0.45) / 0.55)) — at every progress value
 *   exactly one narration exists in the hierarchy, so the
 *   legibility invariant holds by construction;
 * - the container material lerps mini-translucent -> expanded
 *   ground, and the radius lerps 16 -> 24, in the dock's own
 *   overlay residence (seat unchanged from RW-152/RW-156).
 *
 * Height cap (§7A / RW-152 §2): the expanded height never exceeds
 * [maxExpandedHeightDp] (viewport minus the top-chrome zone);
 * when the cap binds at rest, the ENTRY region scrolls internally
 * while header, narration and doorway stay pinned — the doorway
 * is a sibling of the scroll region, never inside it (diagnosis
 * §2.4's cap clause, made structural in the RW-169 loop-back:
 * previously entry + doorway shared one scroll column).
 */
object WearIveRunMorphPolicy {

    /** Morph duration (spec §4: 380ms morph curve). */
    const val MORPH_MS = 380

    /** The expanded region's dissolve window (RW-152 §1.3). */
    fun expandedAlpha(progress: Float): Float =
        ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    /** Geometry interpolation between the two rest geometries. */
    fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    fun wordmarkSp(progress: Float): Float = lerp(17f, 31f, progress)
    fun narrationSp(progress: Float): Float = lerp(12f, 15f, progress)
    fun radiusDp(progress: Float): Float = lerp(16f, 24f, progress)
    fun topPaddingDp(progress: Float): Float = lerp(0f, 20f, progress)
    fun bottomPaddingDp(progress: Float): Float = lerp(0f, 32f, progress)

    /**
     * The single-legible-narration probe (§7A acceptance): at any
     * morph progress, the surface contains exactly ONE narration
     * instance — this policy's construction guarantee, asserted
     * structurally by the morph's render tests (the semantics
     * tree's narration-node count is 1 at p = 0, .25, .5, .75, 1).
     */
    fun narrationInstanceCount(progress: Float): Int = 1
}

@Composable
fun WearIveRunMorphSurface(
    narration: WearIveRunNarration,
    entry: WearIveRunEntry,
    entryLine: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onOpenCollection: () -> Unit,
    onEntryTap: (() -> Unit)? = null,
    heroResign: (suspend (String) -> String?)? = null,
    heroRecovery: HeroRecoverySeam? = null,
    onHeroRecovered: (WearIveRunDayEntry, String) -> Unit = { _, _ -> },
    cueKey: Int = 0,
    cueArmed: Boolean = false,
    bounceKey: Int = 0,
    entranceKey: Int = 0,
    exiting: Boolean = false,
    reduceMotion: Boolean = false,
    onExitFinished: () -> Unit = {},
    maxExpandedHeightDp: Float = Float.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // The morph progress: ONE animated value drives every
    // geometric interpolation below. Reduce Motion: snap.
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 1)
        } else {
            tween(
                durationMillis = WearIveRunMorphPolicy.MORPH_MS,
                easing = WearIveRunDockPolicy.MORPH_EASING
            )
        },
        label = "wirMorphProgress"
    )
    val p = progress

    // ---- Settle-flip narration fade (spec §4.3: opacity only) --
    // The displayed narration swaps at the fade's midpoint; the
    // single Text instance below renders only [displayedNarration].
    var displayedNarration by remember { mutableStateOf(narration) }
    val narrationAlpha = remember { Animatable(1f) }
    LaunchedEffect(narration) {
        if (narration.text != displayedNarration.text) {
            narrationAlpha.animateTo(0f, tween(durationMillis = 95))
            displayedNarration = narration
            narrationAlpha.animateTo(1f, tween(durationMillis = 115))
        }
    }

    // ---- Creation pulse / update bounce (spec §4.1 / §4.3) -----
    val pulse = remember { Animatable(1f) }
    var lastPulsedCue by remember { mutableStateOf(0) }
    LaunchedEffect(cueKey, cueArmed) {
        if (!cueArmed || cueKey <= 0 || cueKey == lastPulsedCue) return@LaunchedEffect
        lastPulsedCue = cueKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion) {
            pulse.animateTo(
                WearIveRunDockPolicy.CUE_PULSE_SCALE,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            pulse.animateTo(1f, tween(durationMillis = 450, easing = EaseInOut))
        }
    }
    val bounce = remember { Animatable(1f) }
    var lastBouncedKey by remember { mutableStateOf(0) }
    LaunchedEffect(bounceKey) {
        if (bounceKey <= 0 || bounceKey == lastBouncedKey) return@LaunchedEffect
        lastBouncedKey = bounceKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        if (!reduceMotion) {
            bounce.animateTo(
                WearIveRunDockPolicy.UPDATE_BOUNCE_SCALE,
                tween(durationMillis = 140, easing = EaseInOut)
            )
            bounce.animateTo(1f, tween(durationMillis = 160, easing = EaseInOut))
        }
    }

    // ---- Creation entrance (spec §4.1) --------------------------
    val entrance = remember { Animatable(if (entranceKey > 0) 0f else 1f) }
    var surfaceHeightPx by remember { mutableStateOf(0) }
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

    // ---- Delete / session-end exit (spec §4.7) ------------------
    val contentAlpha = remember { Animatable(1f) }
    val foldFraction = remember { Animatable(1f) }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        if (reduceMotion) {
            contentAlpha.animateTo(0f, tween(durationMillis = 200))
            foldFraction.snapTo(0f)
        } else {
            contentAlpha.animateTo(
                0f,
                tween(durationMillis = WearIveRunDockPolicy.DELETE_CONTENT_FADE_MS)
            )
            foldFraction.animateTo(
                0f,
                tween(
                    durationMillis = WearIveRunDockPolicy.DELETE_FOLD_MS,
                    easing = WearIveRunDockPolicy.FOLD_EASING
                )
            )
        }
        onExitFinished()
    }

    // Press (spec §4.4): the mini form scales to 0.97 while pressed.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion && !expanded) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "wirMorphPress"
    )

    val shape = RoundedCornerShape(WearIveRunMorphPolicy.radiusDp(p).dp)
    val miniColor = RunWearColors.MiniBarContainer.copy(
        alpha = WearIveRunMiniBarPolicy.backgroundAlpha(
            isFullExpression = false,
            darkTheme = darkTheme
        )
    )
    val containerColor = androidx.compose.ui.graphics.lerp(
        miniColor, RunWearColors.FooterGround, p
    )

    Box(
        modifier = modifier
            .testTag("rw169MorphSurface")
            .graphicsLayer {
                alpha = entrance.value
                if (!reduceMotion) {
                    translationY = (1f - entrance.value) * 1.6f * surfaceHeightPx
                }
                val s = pulse.value * bounce.value * pressScale
                scaleX = s
                scaleY = s
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = WearIveRunMorphPolicy.lerp(6f, 16f, p).dp,
                    shape = shape
                )
                .clip(shape)
                .background(containerColor)
                .border(
                    width = 1.dp,
                    color = RunWearColors.TextPrimary.copy(alpha = 0.09f),
                    shape = shape
                )
                .graphicsLayer { alpha = contentAlpha.value }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !expanded
                ) {
                    haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                    onExpand()
                }
        ) {
            MorphLayout(
                progress = p,
                maxExpandedHeightDp = maxExpandedHeightDp,
                foldFraction = foldFraction.value,
                onSizeChanged = { surfaceHeightPx = it },
                wordmark = {
                    // §7B: fixed-claim at EVERY progress value.
                    Text(
                        text = WearIveRunNarrationFormatter.WORDMARK,
                        fontFamily = GreatVibesFontFamily,
                        fontSize = WearIveRunMorphPolicy.wordmarkSp(p).sp,
                        color = RunWearColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                narrationContent = {
                    Box(modifier = Modifier.graphicsLayer {
                        alpha = narrationAlpha.value
                    }) {
                        DockNarrationTextPublic(
                            narration = displayedNarration,
                            fontSizeSp = WearIveRunMorphPolicy.narrationSp(p)
                        )
                    }
                },
                controls = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ONE control slot whose glyph morphs:
                        // corners-out (mini, go to expanded) fades
                        // to chevron-down (expanded, collapse).
                        Box(modifier = Modifier.size(48.dp)) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                                    onExpand()
                                },
                                enabled = p < 0.5f,
                                modifier = Modifier.graphicsLayer { alpha = 1f - p }
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
                                    onCollapse()
                                },
                                enabled = p >= 0.5f,
                                modifier = Modifier.graphicsLayer { alpha = p }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = "Collapse Wear I've Run",
                                    tint = RunWearColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                            onDelete()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Remove from Wear I've Run",
                                tint = if (p > 0.5f) {
                                    RunWearColors.TextPrimary
                                } else {
                                    RunWearColors.TextSecondary
                                },
                                modifier = Modifier.size(
                                    WearIveRunMorphPolicy.lerp(18f, 20f, p).dp
                                )
                            )
                        }
                    }
                },
                entryRegion = {
                    // Composed only once the morph is under way
                    // (p > 0): at mini rest the expanded content
                    // does not exist in the tree at all — the
                    // doorway's first appearance is the expansion
                    // itself (RW-152's rest-state pin), and no
                    // unplaced semantics leak from a region the
                    // layout has not seated.
                    if (p > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = WearIveRunMorphPolicy.expandedAlpha(p)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (onEntryTap != null) {
                                        onEntryTap()
                                    } else {
                                        onOpenCollection()
                                    }
                                }
                        ) {
                            val thumbUrl =
                                if (heroResign != null && entry.sourceEntry != null) {
                                    rememberHeldResolvedHeroUrl(
                                        entry.sourceEntry!!,
                                        heroResign, heroRecovery
                                    ) { path ->
                                        onHeroRecovered(entry.sourceEntry!!, path)
                                    }
                                } else {
                                    entry.thumbnailUrl
                                }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RunWearColors.Background),
                                contentAlignment = Alignment.Center
                            ) {
                                HeldHeroThumbnail(
                                    url = thumbUrl,
                                    identityKey = entry.sourceEntry?.let {
                                        WearIveRunCollectionLogic.identityOf(it)
                                    } ?: entry.place,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = entryLine,
                                fontSize = 14.5.sp,
                                color = RunWearColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    }
                },
                doorway = {
                    // Always measurable (a Layout slot must emit
                    // exactly one measurable at every progress);
                    // the doorway CONTENT exists only once the
                    // morph is under way, like the entry region.
                    Box(Modifier.fillMaxWidth()) {
                    if (p > 0f) {
                        Text(
                            text = "Open the collection ›",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RunWearColors.PineAccent,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = WearIveRunMorphPolicy.expandedAlpha(p)
                                }
                                .clickable { onOpenCollection() }
                                .padding(vertical = 4.dp)
                        )
                    }
                    }
                }
            )
        }
    }
}

/**
 * The single-layout measure/place pass. Children: [wordmark,
 * narration, controls, entryRegion, doorway] — measured ONCE each, in
 * this one hierarchy, per frame. Rest heights are read from this
 * same measure: the mini height is the header block's height at
 * p = 0 geometry; the expanded height is the full content height
 * at p = 1 geometry (capped). The container's height at progress
 * p is their interpolation — geometry, not arbitration.
 */
@Composable
private fun MorphLayout(
    progress: Float,
    maxExpandedHeightDp: Float,
    foldFraction: Float,
    onSizeChanged: (Int) -> Unit,
    wordmark: @Composable () -> Unit,
    narrationContent: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    entryRegion: @Composable () -> Unit,
    doorway: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Layout(
        content = {
            wordmark()
            narrationContent()
            controls()
            val expandedScrollState = rememberScrollState()
            Box(
                modifier = Modifier.verticalScroll(
                    expandedScrollState,
                    enabled = progress > 0.99f
                )
            ) { entryRegion() }
            doorway()
        }
    ) { measurables, constraints ->
        val p = progress
        val maxW = constraints.maxWidth
        val sidePadPx = with(density) { 16.dp.roundToPx() }
        val controlsMeasurable = measurables[2]
        val wordmarkPlaceable = measurables[0].measure(
            Constraints(maxWidth = maxW)
        )
        val controlsPlaceable = controlsMeasurable.measure(
            Constraints(maxWidth = maxW)
        )
        val availW = (maxW - sidePadPx * 2).coerceAtLeast(0)
        // Narration slot: beside the wordmark in mini geometry,
        // the full content width in expanded geometry.
        val miniNarrW = (availW - wordmarkPlaceable.width -
            controlsPlaceable.width - with(density) { 10.dp.roundToPx() })
            .coerceAtLeast(0)
        val narrW = WearIveRunMorphPolicy.lerp(
            miniNarrW.toFloat(), availW.toFloat(), p
        ).roundToInt().coerceAtLeast(0)
        val narrationPlaceable = measurables[1].measure(
            Constraints(minWidth = narrW, maxWidth = narrW)
        )
        // Header heights at the two rest geometries, from THIS
        // measure: mini = tallest of the one row's elements;
        // expanded = controls row + narration below it.
        val miniHeaderH = maxOf(
            wordmarkPlaceable.height,
            narrationPlaceable.height,
            controlsPlaceable.height
        )
        val expandedHeaderH = controlsPlaceable.height +
            with(density) { 4.dp.roundToPx() } + narrationPlaceable.height
        val headerH = WearIveRunMorphPolicy.lerp(
            miniHeaderH.toFloat(), expandedHeaderH.toFloat(), p
        ).roundToInt()
        // Expanded region: measured at its natural height, capped
        // by the panel cap's remainder at the expanded geometry.
        val topPadPx = with(density) {
            WearIveRunMorphPolicy.topPaddingDp(p).dp.roundToPx()
        }
        val bottomPadPx = with(density) {
            WearIveRunMorphPolicy.bottomPaddingDp(p).dp.roundToPx()
        }
        val gapPx = with(density) { 10.dp.roundToPx() }
        val capPx = if (maxExpandedHeightDp == Float.MAX_VALUE) {
            Constraints.Infinity
        } else {
            with(density) { maxExpandedHeightDp.dp.roundToPx() }
        }
        // The doorway is measured at its natural height FIRST:
        // it is pinned at the panel's foot whenever the panel is
        // expanded, so the entry region's scroll budget is what
        // the cap leaves after header + doorway (§2.4 cap
        // clause: header, narration and doorway pinned; the
        // entry region scrolls internally).
        val doorwayPlaceable = measurables[4].measure(
            Constraints(maxWidth = availW)
        )
        val doorwayGapPx = with(density) { 10.dp.roundToPx() }
        val expandedMaxH = if (capPx == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            (capPx - topPadPx - bottomPadPx - expandedHeaderH - gapPx -
                doorwayPlaceable.height - doorwayGapPx)
                .coerceAtLeast(0)
        }
        val expandedPlaceable = measurables[3].measure(
            Constraints(
                minWidth = availW, maxWidth = availW,
                maxHeight = expandedMaxH
            )
        )
        val expandedRegionH = (expandedPlaceable.height * p).roundToInt() +
            if (p > 0f) {
                (doorwayGapPx * p).roundToInt() +
                    (doorwayPlaceable.height * p).roundToInt()
            } else {
                0
            }
        val naturalH = topPadPx + headerH +
            (if (p > 0f) gapPx + expandedRegionH else 0) + bottomPadPx
        val cappedH = if (capPx == Constraints.Infinity) {
            naturalH
        } else {
            minOf(naturalH, capPx)
        }
        val totalH = (cappedH * foldFraction).roundToInt().coerceAtLeast(0)
        layout(maxW, totalH) {
            onSizeChanged(totalH)
            val wordmarkY = WearIveRunMorphPolicy.lerp(
                topPadPx + (miniHeaderH - wordmarkPlaceable.height) / 2f,
                topPadPx.toFloat(), p
            ).roundToInt()
            wordmarkPlaceable.placeRelative(sidePadPx, wordmarkY)
            val controlsY = WearIveRunMorphPolicy.lerp(
                topPadPx + (miniHeaderH - controlsPlaceable.height) / 2f,
                topPadPx.toFloat(), p
            ).roundToInt()
            controlsPlaceable.placeRelative(
                maxW - sidePadPx - controlsPlaceable.width, controlsY
            )
            val narrX = WearIveRunMorphPolicy.lerp(
                (sidePadPx + wordmarkPlaceable.width +
                    with(density) { 10.dp.roundToPx() }).toFloat(),
                sidePadPx.toFloat(), p
            ).roundToInt()
            val narrY = WearIveRunMorphPolicy.lerp(
                topPadPx + (miniHeaderH - narrationPlaceable.height) / 2f,
                (topPadPx + controlsPlaceable.height +
                    with(density) { 4.dp.roundToPx() }).toFloat(), p
            ).roundToInt()
            narrationPlaceable.placeRelative(narrX, narrY)
            if (p > 0f) {
                expandedPlaceable.placeRelative(
                    sidePadPx, topPadPx + headerH + gapPx
                )
                doorwayPlaceable.placeRelative(
                    sidePadPx,
                    topPadPx + headerH + gapPx +
                        expandedPlaceable.height + doorwayGapPx
                )
            }
        }
    }
}

/** The narration's annotated display text (the dock's renderer,
 * shared so the morph's single narration instance carries the
 * identical pine-payload form). */
@Composable
fun DockNarrationTextPublic(
    narration: WearIveRunNarration,
    fontSizeSp: Float,
    modifier: Modifier = Modifier
) {
    val annotated = buildWirNarrationText(narration)
    Text(
        text = annotated,
        fontSize = fontSizeSp.sp,
        color = RunWearColors.TextSecondary,
        maxLines = Int.MAX_VALUE,
        modifier = modifier
    )
}
