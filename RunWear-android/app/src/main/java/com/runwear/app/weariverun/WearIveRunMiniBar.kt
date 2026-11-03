package com.runwear.app.weariverun

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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors

/**
 * RW-25: the "Wear I've Run" mini bar, fully styled per the settled
 * spec — but in this build it is only ever composed with non-PreAdd
 * states by future collection code: the dock's state holder yields
 * PreAdd only, so nothing renders yet (see WearIveRunModels.kt).
 *
 * Custom rendering is limited to the wordmark element (script face +
 * pine payload span) — everything else is stock M3 (Surface,
 * IconButton, TextButton). The bar's background follows
 * [WearIveRunMiniBarPolicy]: translucent in mini state, fully opaque
 * at full expression; text/icons always at 100% opacity.
 */
@Composable
fun WearIveRunMiniBar(
    state: WearIveRunBarState,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    when (state) {
        WearIveRunBarState.PreAdd -> Unit // the dock guards this too
        is WearIveRunBarState.Mini -> MiniBarContent(
            state = state,
            darkTheme = darkTheme,
            onExpandClick = onExpandClick,
            onRemoveClick = onRemoveClick,
            modifier = modifier
        )
        is WearIveRunBarState.Expanded -> ExpandedBarContent(
            state = state,
            darkTheme = darkTheme,
            onCollapseClick = onCollapseClick,
            onRemoveClick = onRemoveClick,
            modifier = modifier
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
            // Grayed affordances: 14dp glyphs on full 48dp M3
            // IconButton hit areas.
            IconButton(onClick = onExpandClick) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = "Expand Wear I've Run",
                    tint = RunWearColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove from Wear I've Run",
                    tint = RunWearColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedBarContent(
    state: WearIveRunBarState.Expanded,
    darkTheme: Boolean,
    onCollapseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                // Thumbnail slot
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RunWearColors.BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.entry.thumbnailUrl != null) {
                        AsyncImage(
                            model = state.entry.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = RunWearColors.TextMuted,
                            modifier = Modifier.size(22.dp)
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
                IconButton(onClick = onCollapseClick) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Collapse Wear I've Run",
                        tint = RunWearColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // Quiet Remove
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemoveClick) {
                    Text(
                        text = "Remove",
                        color = RunWearColors.TextMuted,
                        style = MaterialTheme.typography.labelLarge
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
        withStyle(SpanStyle(color = RunWearColors.Primary, fontWeight = FontWeight.Medium)) {
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
