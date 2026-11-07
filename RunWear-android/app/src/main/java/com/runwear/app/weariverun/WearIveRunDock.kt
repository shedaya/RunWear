package com.runwear.app.weariverun

import android.provider.Settings
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.runwear.app.ui.components.HapticSpec
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors

/**
 * RW-135 (Direction A / A-prime, owner-ruled Nov 7, 2026): the
 * Wear I've Run Home presence — a floating MINI dock plus the
 * expanded FOOTER section. Research section 7 governs; the
 * expanded form is the INTERIM footer (section 7.2 anatomy in the
 * page's card/section language) pending the owner's approval of
 * the A-prime animation package — see the lane report for the
 * named deltas that package will bring.
 *
 * Both surfaces render the RECORDED entry and nothing else
 * (section 7.0): their line comes from
 * [WearIveRunDockNarration], a pure function of the record. The
 * in-flow band card this replaces is retired as the Home presence.
 */
object WearIveRunDockPolicy {

    /** The dock shows only while its footer is off-screen: the
     * two surfaces never present a legible line at the same time
     * (the legibility invariant). A pure function of position —
     * no timers, no state to desynchronise. */
    fun dockVisible(present: Boolean, footerVisible: Boolean): Boolean =
        present && !footerVisible

    /** MINI geometry (research section 7.6): inset, lift above
     * the bottom safe edge, and the radius that never animates. */
    const val DOCK_INSET_DP = 12
    const val DOCK_BOTTOM_DP = 14
    const val DOCK_RADIUS_DP = 16

    /** The narration cross-fade (section 7.4): opacity only. */
    const val NARRATION_CROSSFADE_MS = 210
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
 * the footer — there is no separate jump chip (section 7.3).
 * Delete is one tap, no confirmation; the host animates the bar
 * out as the state leaves.
 *
 * [cueKey]/[cueArmed]: the creation cue — the ONLY pulse in the
 * system (section 7.4). The host arms it only for a session
 * creation (an Absent -> Present transition with a fresh cue
 * sequence); settle changes cross-fade quietly and never pulse.
 */
@Composable
fun WearIveRunDock(
    narration: WearIveRunNarration,
    cueKey: Int,
    cueArmed: Boolean,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pulse = remember { Animatable(1f) }
    var lastPulsedCue by remember { mutableIntStateOf(0) }

    LaunchedEffect(cueKey, cueArmed) {
        if (!cueArmed || cueKey <= 0 || cueKey == lastPulsedCue) {
            return@LaunchedEffect
        }
        lastPulsedCue = cueKey
        haptic.performHapticFeedback(HapticSpec.CUE_TICK)
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (scale > 0f) {
            // One gentle pulse: 900 ms ease-in-out, exactly once.
            pulse.animateTo(
                1.018f,
                tween(durationMillis = 450, easing = EaseInOut)
            )
            pulse.animateTo(
                1f,
                tween(durationMillis = 450, easing = EaseInOut)
            )
        }
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .clickable { onExpand() },
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
            IconButton(onClick = onExpand) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "Expand Wear I've Run",
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticSpec.LIGHT_TICK)
                onDelete()
            }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove from Wear I've Run",
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * The EXPANDED form — INTERIM (see the file header): the entry
 * itself as the page's final section, in the page's card language
 * (card surface, 24 dp radius, section inset — never the flat
 * hard-edged full-bleed slab this item exists to reverse).
 * Anatomy per research section 7.2: header row (script 31pt +
 * chevron + delete), the narration row, the full entry row, and
 * the collection doorway — tapping the entry row (or the explicit
 * doorway line) opens the collection sheet.
 */
@Composable
fun WearIveRunFooterSection(
    narration: WearIveRunNarration,
    entry: WearIveRunEntry,
    entryLine: String,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onOpenCollection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = RunWearColors.BgCard,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp, end = 8.dp, top = 14.dp, bottom = 16.dp
            )
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
                    maxLines = 2,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCollection() }
                    .padding(end = 8.dp)
            ) {
                // Thumbnail — paints only once decoded (the D-12
                // rule the old bar set): until then the slot is a
                // plain tinted box, never a broken-image glyph.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RunWearColors.Background),
                    contentAlignment = Alignment.Center
                ) {
                    entry.thumbnailUrl?.let { url ->
                        var decoded by remember(url) { mutableStateOf(false) }
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
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
                Text(
                    text = entryLine,
                    fontSize = 14.5.sp,
                    color = RunWearColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
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
