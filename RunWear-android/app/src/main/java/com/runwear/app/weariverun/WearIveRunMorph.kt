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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import kotlin.math.roundToInt

/**
 * RW-135 REBUILD (Phase 2, Nov 12, 2026): ONE element, two
 * geometries — conformance map §§3–5 as reconciled by R-1 and
 * RW-152 §§1–2.
 *
 * The choreography is the design's DISSOLVE morph (RW-152
 * §1.3: height + content cross-fade), not a diagonal slide:
 *
 * - the container height interpolates between the two REST
 *   heights, both read from this one layout's own measure;
 * - the wordmark is ONE instance, top-left anchored in both
 *   forms, its font size lerping 17sp -> 31sp in place
 *   (fixed-claim: never wraps/shrinks);
 * - RW-170 Move 4 (Nov 12, 2026): the narration is ONE
 *   composition whose slot MIGRATES with p, the way the
 *   wordmark already does — font size lerps 12sp -> 15sp,
 *   x lerps from the mini slot (beside the wordmark) to the
 *   expanded slot (side pad), y lerps from centred-in-mini-row
 *   to below the header row, width lerps mini slot -> full
 *   width. Exactly ONE narration node exists at every p, so
 *   the legibility invariant (map §5) holds by construction
 *   — there is never a second copy to ghost. (The pre-RW-170
 *   construction composed the narration TWICE with disjoint
 *   alpha windows [0,.35]/[.55,1] and a dead zone; Sol's
 *   device pass showed the ghosted double text.)
 * - the expanded-only content (entry row + doorway) fades in
 *   over the back window [0.45, 1], laid out at expanded
 *   geometry inside the growing, clipped container;
 * - RW-170 Move 1 (Nov 12, 2026): the container paints ONE
 *   token — FooterGround in both appearances (Sol's ruling:
 *   the EXPANDED state's darker warm tone is the unified
 *   surface token; the MINI #FDFBF6 MiniBarContainer token
 *   is superseded for this surface and this surface never
 *   reads it). ALPHA is the only background animation: the
 *   mini translucency (0.85 light / 0.82 dark, per
 *   WearIveRunMiniBarPolicy) -> 1.0 expanded. No colour
 *   lerp. The radius is a CONSTANT 16dp at every progress
 *   value (map §3.1: "16pt — never animates");
 * - RW-170 Move 3: the shadow is the spec §2.1 custom
 *   shadow — shape-following, drawn UNDER the surface,
 *   offset (0, +6dp), blur 24dp, black at 14%, constant
 *   across p and appearances — plus the 1px hairline at
 *   ink 5%. The stock elevation shadow (an ambient+spot
 *   halo on every side) is gone.
 * - padding tokens (RB-04, prototype-measured): MINI rest =
 *   top 11dp / bottom 12dp around a 26dp content row
 *   (≈49dp total); EXPANDED = top 20dp / sides 16dp /
 *   bottom 12dp after the doorway — the panel ends where
 *   the doorway ends (RW-152 §2.1). Controls are 26dp
 *   visual boxes; the bar body itself is the hit target, so
 *   no 48dp button drives the mini height.
 *
 * RB-02 (structural fix): the expand/collapse control is ONE
 * clickable 26dp slot whose glyph cross-fades (Fullscreen <->
 * ExpandMore drawn as two Icons with complementary alpha
 * INSIDE the one clickable). The pre-fix construction stacked
 * two 48dp IconButtons in one Box — the invisible
 * (alpha = p) collapse button sat on top of the expand
 * button, intercepted every real pointer hit at mini rest
 * while disabled, and fired nothing: the icon was a dead tap
 * on device while every semantics-OnClick harness test
 * passed. No alpha-0 clickable sits over another control
 * anywhere in this surface now; the container body click
 * remains the second path to the SAME action (spec §4.4:
 * icon tap == body tap).
 *
 * Height cap (RW-152 §2): the expanded height never exceeds
 * [maxExpandedHeightDp] (viewport minus the top-chrome
 * zone); when the cap binds at rest, the ENTRY region
 * scrolls internally while header, narration and doorway
 * stay pinned — the doorway is a sibling of the scroll
 * region, never inside it.
 */
object WearIveRunMorphPolicy {

    /** Morph duration (spec §4: 380ms morph curve). */
    const val MORPH_MS = 380

    /** Radius (map §3.1): 16dp, NEVER animates. */
    const val RADIUS_DP = 16f

    /** Padding / control tokens (RB-04). */
    const val MINI_TOP_DP = 11f
    const val MINI_BOTTOM_DP = 12f
    const val MINI_SIDE_DP = 12f
    const val EXPANDED_TOP_DP = 20f
    const val EXPANDED_BOTTOM_DP = 12f
    const val EXPANDED_SIDE_DP = 16f
    const val CONTROL_DP = 26f

    /** Shadow of record (spec §2.1; RW-170 Move 3): offset
     * (0, +6dp), blur 24dp, black at 14% — constant across
     * progress and appearances. */
    const val SHADOW_OFFSET_Y_DP = 6f
    const val SHADOW_BLUR_DP = 24f
    const val SHADOW_ALPHA = 0.14f

    /** Hairline (spec §2.1): 1 physical px at ink 5%. */
    const val HAIRLINE_ALPHA = 0.05f

    /** The expanded region's dissolve window (RW-152 §1.3). */
    fun expandedAlpha(progress: Float): Float =
        ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    /** The ONE narration's font size (RW-170 Move 4): lerps
     * 12sp at the mini slot -> 15sp at the expanded slot. */
    fun narrationSp(progress: Float): Float = lerp(12f, 15f, progress)

    /** RW-170 Move 1 — the surface's background alpha: the
     * mini translucency (WearIveRunMiniBarPolicy: 0.85 light
     * / 0.82 dark) lerping to fully opaque at expanded. The
     * TOKEN never changes (FooterGround, both appearances);
     * alpha is the only background animation. */
    fun surfaceAlpha(progress: Float, darkTheme: Boolean): Float =
        lerp(
            WearIveRunMiniBarPolicy.backgroundAlpha(
                isFullExpression = false,
                darkTheme = darkTheme
            ),
            WearIveRunMiniBarPolicy.FULL_ALPHA,
            progress
        )

    /** The single-narration law (RW-170 Move 4): exactly ONE
     * narration node exists at every progress value — the
     * narration is composed once and its slot migrates. The
     * legibility invariant (map §5: never two legible
     * narrations) holds by construction. */
    fun narrationNodeCount(progress: Float): Int = 1

    /** Geometry interpolation between the two rest geometries. */
    fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    fun wordmarkSp(progress: Float): Float = lerp(17f, 31f, progress)

    /** Radius is constant 16dp at every progress (RB-05). */
    fun radiusDp(progress: Float): Float = RADIUS_DP

    fun topPaddingDp(progress: Float): Float =
        lerp(MINI_TOP_DP, EXPANDED_TOP_DP, progress)

    /** Bottom padding is 12dp in BOTH forms (RB-04): the
     * panel ends where the doorway ends. */
    fun bottomPaddingDp(progress: Float): Float =
        lerp(MINI_BOTTOM_DP, EXPANDED_BOTTOM_DP, progress)

    fun sidePaddingDp(progress: Float): Float =
        lerp(MINI_SIDE_DP, EXPANDED_SIDE_DP, progress)

    /** The MINI rest height for a content row of
     * [contentRowDp]: top pad + row + bottom pad. For the
     * 26dp control row and a one-line narration this is the
     * prototype-measured ≈49dp. */
    fun miniRestHeightDp(contentRowDp: Float = CONTROL_DP): Float =
        MINI_TOP_DP + contentRowDp + MINI_BOTTOM_DP
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
    /** RW-170 Move 2: reports the PRESENTED (mini) form's
     * measured rest height in dp (top pad + mini header +
     * bottom pad, at p = 0 geometry) so the host prices the
     * dock band from what is on screen. Fires only at p = 0 —
     * expansion never changes the reported quantity. */
    onMiniHeightChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val darkTheme = androidx.compose.material3.MaterialTheme
        .colorScheme.background.luminance() < 0.5f

    // The morph progress: ONE animated value drives every
    // geometric interpolation below. The token is 380ms with
    // the morph easing cubic-bezier(0.32, 0.72, 0, 1) (S1
    // §4). Reduce Motion: an opacity-class crossfade of
    // <=150ms, no travel (map §6.2 / spec §5).
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 150)
        } else {
            tween(
                durationMillis = WearIveRunMorphPolicy.MORPH_MS,
                easing = WearIveRunDockPolicy.MORPH_EASING
            )
        },
        label = "wirMorphProgress"
    )
    val p = progress

    // ---- Settle-flip narration fade (spec §4.3: opacity only,
    // out 95ms / swap / in 115ms) — the one narration renders the
    // SAME displayed narration, so the record flips atomically.
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

    // ---- Creation entrance (spec §4.1: rise +160% of height
    // with a fade, 520ms enter; Reduce Motion 220ms fade) ------
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

    // Press (spec §4.4): the mini form scales to 0.97 over
    // 120ms while pressed.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion && !expanded) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "wirMorphPress"
    )

    val shape = RoundedCornerShape(WearIveRunMorphPolicy.radiusDp(p).dp)
    // RW-170 Move 1 — ONE SURFACE: FooterGround in both
    // appearances (Sol's ruling); ALPHA is the only background
    // animation (mini translucency -> 1.0). This surface never
    // reads MiniBarContainer — the two-token colour lerp is
    // deleted.
    val containerColor = RunWearColors.FooterGround.copy(
        alpha = WearIveRunMorphPolicy.surfaceAlpha(p, darkTheme)
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
                    // SCRATCH (ci/rw170-disc-shadow): the
                    // pre-RW-170 defect — the stock elevation
                    // shadow restored.
                    elevation = WearIveRunMorphPolicy.lerp(6f, 16f, p).dp,
                    shape = shape
                )
                .clip(shape)
                .background(containerColor)
                .border(
                    // SCRATCH: pre-RW-170 hairline (1dp at 9%).
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
                onMiniHeightChanged = onMiniHeightChanged,
                wordmark = {
                    // Fixed-claim at EVERY progress value: one
                    // instance, top-left anchored, size lerps
                    // in place — never wraps/shrinks.
                    Text(
                        text = WearIveRunNarrationFormatter.WORDMARK,
                        fontFamily = GreatVibesFontFamily,
                        fontSize = WearIveRunMorphPolicy.wordmarkSp(p).sp,
                        color = RunWearColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                narration = {
                    // RW-170 Move 4 — ONE narration composition.
                    // Its slot migrates inside MorphLayout; the
                    // only alpha on it is the shared settle-flip
                    // fade (§4.3), which stays. No second
                    // placement exists to ghost, and no
                    // invisibleToUser swap is needed.
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = narrationAlpha.value
                            }
                    ) {
                        DockNarrationTextPublic(
                            narration = displayedNarration,
                            fontSizeSp =
                                WearIveRunMorphPolicy.narrationSp(p)
                        )
                    }
                },
                controls = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // RB-02: ONE control slot — a single
                        // clickable 26dp box whose glyph
                        // cross-fades inside it. onClick
                        // dispatches expand when collapsed /
                        // collapse when expanded: the same
                        // action as the body tap (spec §4.4).
                        Box(
                            modifier = Modifier
                                .size(WearIveRunMorphPolicy.CONTROL_DP.dp)
                                .semantics {
                                    contentDescription = if (p < 0.5f) {
                                        "Expand Wear I've Run"
                                    } else {
                                        "Collapse Wear I've Run"
                                    }
                                }
                                .clickable {
                                    haptic.performHapticFeedback(
                                        HapticSpec.LIGHT_TICK
                                    )
                                    if (p < 0.5f) onExpand() else onCollapse()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fullscreen,
                                contentDescription = null,
                                tint = RunWearColors.TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { alpha = 1f - p }
                            )
                            Icon(
                                imageVector = Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = RunWearColors.TextPrimary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { alpha = p }
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        // Delete: the same 26dp visual box —
                        // no 48dp button may drive the mini
                        // height (RB-04). One tap, no
                        // confirmation (spec §4.7).
                        Box(
                            modifier = Modifier
                                .size(WearIveRunMorphPolicy.CONTROL_DP.dp)
                                .semantics {
                                    contentDescription =
                                        "Remove from Wear I've Run"
                                }
                                .clickable {
                                    haptic.performHapticFeedback(
                                        HapticSpec.LIGHT_TICK
                                    )
                                    onDelete()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
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
                    // does not exist in the tree at all.
                    if (p > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = WearIveRunMorphPolicy.expandedAlpha(p)
                            }
                            .then(
                                if (WearIveRunMorphPolicy.expandedAlpha(p) < 0.5f) {
                                    Modifier.semantics { invisibleToUser() }
                                } else {
                                    Modifier
                                }
                            )
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
                    // The doorway is ALWAYS present in the
                    // expanded form (map §4.1).
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
                                .then(
                                    if (WearIveRunMorphPolicy
                                            .expandedAlpha(p) < 0.5f
                                    ) {
                                        Modifier.semantics { invisibleToUser() }
                                    } else {
                                        Modifier
                                    }
                                )
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
 * narration, controls, entryRegion, doorway] — in this one
 * hierarchy, per frame. Rest heights are read from this same
 * measure: the mini height is the header block's height at
 * p = 0 geometry (top pad 11 + the one content row + bottom
 * pad 12); the expanded height is the full content height at
 * p = 1 geometry. The container's height at progress p is
 * their interpolation — geometry, not arbitration.
 *
 * RW-170 Move 4: the ONE narration measurable is measured
 * ONCE per frame, at the migrated width for the current p;
 * the two rest header heights are priced from its intrinsic
 * heights at the two rest slot widths (exact at the rest
 * states, where the lerped font size equals the slot's own
 * size). The placed instance migrates: x lerps mini slot ->
 * side pad, y lerps centred-in-mini-row -> below the header
 * row, width lerps mini slot -> full width, and its font
 * size lerps in the composition (12sp -> 15sp). At p = 0 the
 * placement is exactly the old mini rest geometry; at p = 1
 * exactly the old expanded rest geometry; the container
 * height interpolates between the two rest heights
 * throughout.
 *
 * RW-170 Move 2: at p = 0 (and only there — the presented
 * form's geometry is exact only at its own rest), the mini
 * rest height is reported via [onMiniHeightChanged] so the
 * host prices the dock band from the PRESENTED form.
 */
@Composable
private fun MorphLayout(
    progress: Float,
    maxExpandedHeightDp: Float,
    foldFraction: Float,
    onSizeChanged: (Int) -> Unit,
    onMiniHeightChanged: (Float) -> Unit,
    wordmark: @Composable () -> Unit,
    narration: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    entryRegion: @Composable () -> Unit,
    doorway: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Layout(
        content = {
            wordmark()
            narration()
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
        val sidePadPx = with(density) {
            WearIveRunMorphPolicy.sidePaddingDp(p).dp.roundToPx()
        }
        val topPadPx = with(density) {
            WearIveRunMorphPolicy.topPaddingDp(p).dp.roundToPx()
        }
        val bottomPadPx = with(density) {
            WearIveRunMorphPolicy.bottomPaddingDp(p).dp.roundToPx()
        }
        val gapPx = with(density) { 10.dp.roundToPx() }
        val narrGapPx = with(density) { 4.dp.roundToPx() }
        val wordmarkPlaceable = measurables[0].measure(
            Constraints(maxWidth = maxW)
        )
        val controlsPlaceable = measurables[2].measure(
            Constraints(maxWidth = maxW)
        )
        val availW = (maxW - sidePadPx * 2).coerceAtLeast(0)
        // Mini narration slot: beside the wordmark, flexing —
        // it wraps and the bar grows (spec §4.3); the payload
        // never truncates.
        val miniNarrW = (availW - wordmarkPlaceable.width -
            controlsPlaceable.width - gapPx).coerceAtLeast(0)
        // The ONE narration: its heights in the two rest
        // slots come from intrinsic measurement (a Measurable
        // may be measured only once per pass); the placed
        // instance is measured at the migrated width for
        // this p.
        val narrationMiniH = measurables[1].maxIntrinsicHeight(miniNarrW)
        val narrationExpandedH = measurables[1].maxIntrinsicHeight(availW)
        val narrW = WearIveRunMorphPolicy.lerp(
            miniNarrW.toFloat(), availW.toFloat(), p
        ).roundToInt().coerceAtLeast(0)
        val narrationPlaceable = measurables[1].measure(
            Constraints(minWidth = narrW, maxWidth = narrW)
        )
        // Header heights at the two rest geometries, from THIS
        // measure: mini = tallest of the one row's elements;
        // expanded = header row + narration below it.
        val miniHeaderH = maxOf(
            wordmarkPlaceable.height,
            narrationMiniH,
            controlsPlaceable.height
        )
        val expandedRowH = maxOf(
            wordmarkPlaceable.height,
            controlsPlaceable.height
        )
        val expandedHeaderH = expandedRowH + narrGapPx +
            narrationExpandedH
        val headerH = WearIveRunMorphPolicy.lerp(
            miniHeaderH.toFloat(), expandedHeaderH.toFloat(), p
        ).roundToInt()
        val capPx = if (maxExpandedHeightDp == Float.MAX_VALUE) {
            Constraints.Infinity
        } else {
            with(density) { maxExpandedHeightDp.dp.roundToPx() }
        }
        // The doorway is measured at its natural height FIRST:
        // it is pinned at the panel's foot whenever the panel is
        // expanded, so the entry region's scroll budget is what
        // the cap leaves after header + doorway (RW-152 §2:
        // header, narration and doorway pinned; the entry
        // region scrolls internally).
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
            if (p == 0f) {
                // RW-170 Move 2: the PRESENTED height — the
                // p = 0 geometry's own measure (MINI_TOP +
                // mini header + MINI_BOTTOM). Reported only
                // at rest, so expansion can never move the
                // host's band.
                onMiniHeightChanged(
                    WearIveRunMorphPolicy.MINI_TOP_DP +
                        with(density) { miniHeaderH.toDp().value } +
                        WearIveRunMorphPolicy.MINI_BOTTOM_DP
                )
            }
            // Wordmark: top-left anchored in BOTH forms —
            // centred in the mini row, top-aligned in the
            // expanded header row; it never travels.
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
            // The ONE narration MIGRATES with p (RW-170 Move
            // 4): x from the mini slot (sidePad + wordmark +
            // gap) to the expanded slot (sidePad); y from
            // centred in the mini row to below the header row
            // (topPad + expandedRowH + 4dp); its width was
            // lerped at measure time above.
            val narrationX = WearIveRunMorphPolicy.lerp(
                (sidePadPx + wordmarkPlaceable.width + gapPx).toFloat(),
                sidePadPx.toFloat(), p
            ).roundToInt()
            val narrationY = WearIveRunMorphPolicy.lerp(
                topPadPx + (miniHeaderH - narrationPlaceable.height) / 2f,
                (topPadPx + expandedRowH + narrGapPx).toFloat(), p
            ).roundToInt()
            narrationPlaceable.placeRelative(narrationX, narrationY)
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
 * shared so both morph placements carry the identical
 * pine-payload form). */
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
