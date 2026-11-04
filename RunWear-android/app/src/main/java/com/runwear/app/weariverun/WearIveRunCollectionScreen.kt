package com.runwear.app.weariverun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.service.ClothingThumbnailService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * RW-26: the Wear I've Run collection screen (design frames 7–10,
 * 50–52). Chapters BY LOCATION with computed summaries; the weather
 * filter applies at collection level and inside a chapter and
 * recomputes every summary it touches; today is pinned; sharing
 * happens inside, card by card (Morning Poster or Kit Card — no
 * Range, no collection-level sharing in v1).
 */
@Composable
fun WearIveRunCollectionScreen(
    entries: List<WearIveRunDayEntry>,
    today: LocalDate,
    onDismiss: () -> Unit,
    /** Fit preference — selects the Kit Card's garment renders. */
    gender: GenderPreference = GenderPreference.UNISEX
) {
    var filter by remember { mutableStateOf(WearWeatherFilter.ALL) }
    var openChapter by remember { mutableStateOf<String?>(null) }
    var shareEntry by remember { mutableStateOf<WearIveRunDayEntry?>(null) }

    val chapters = remember(entries, filter) {
        WearIveRunCollectionLogic.chapters(entries, filter)
    }
    val todayEntry = remember(entries) {
        entries.filter { it.date == today }.maxByOrNull { it.place }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // WS-9 fix (P-3, §1.6): system Back inside a chapter steps
        // back to the chapter LIST (the same step the header's back
        // arrow takes; iOS reaches it via its push stack). The
        // handler is enabled only while a chapter is open — at the
        // list, Back falls through to the Dialog's own dismiss,
        // which lands home via onDismissRequest.
        androidx.activity.compose.BackHandler(enabled = openChapter != null) {
            if (CollectionBackPolicy.actionFor(openChapter) ==
                CollectionBackAction.STEP_BACK_TO_CHAPTER_LIST
            ) {
                openChapter = null
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = RunWearColors.Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (openChapter != null) {
                        IconButton(onClick = { openChapter = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = RunWearColors.TextPrimary)
                        }
                    }
                    Text(
                        text = WearIveRunNarrationFormatter.WORDMARK,
                        fontFamily = GreatVibesFontFamily,
                        fontSize = 30.sp,
                        color = RunWearColors.TextPrimary,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close", tint = RunWearColors.TextPrimary)
                    }
                }

                FilterRow(filter = filter, onFilter = { filter = it })

                val chapter = chapters.firstOrNull { it.place == openChapter }
                if (chapter != null) {
                    ChapterDetail(
                        chapter = chapter,
                        today = today,
                        onShare = { shareEntry = it },
                        modifier = Modifier.weight(1f)
                    )
                } else if (entries.isEmpty()) {
                    HonestEmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (todayEntry != null) {
                            item(key = "today") {
                                Column {
                                    Text(
                                        text = "TODAY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RunWearColors.TextSecondary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    DayCard(
                                        entry = todayEntry,
                                        today = today,
                                        onShare = { shareEntry = todayEntry },
                                        // W-10: the pinned TODAY card names
                                        // its place and shows its photo,
                                        // like the chapter cards below it.
                                        place = todayEntry.place,
                                        thumbnailRef = todayEntry.heroRef
                                    )
                                }
                            }
                        }
                        items(chapters, key = { it.place }) { ch ->
                            if (ch.days.isNotEmpty()) {
                                ChapterCover(
                                    chapter = ch,
                                    onClick = { openChapter = ch.place },
                                    onShare = { shareEntry = it }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    shareEntry?.let { entry ->
        ShareChoiceSheet(
            entry = entry,
            gender = gender,
            onDismiss = { shareEntry = null }
        )
    }
}

@Composable
private fun FilterRow(filter: WearWeatherFilter, onFilter: (WearWeatherFilter) -> Unit) {
    val labels = listOf(
        WearWeatherFilter.ALL to "All",
        WearWeatherFilter.COLD to "Cold",
        WearWeatherFilter.SNOW to "Snow",
        WearWeatherFilter.RAIN to "Rain",
        WearWeatherFilter.HEAT to "Heat"
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(labels) { (value, label) ->
            FilterChip(
                selected = filter == value,
                onClick = { onFilter(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun HonestEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.titleMedium,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your first outfit of the day adds itself here — one entry a day, for each place you run.",
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

@Composable
internal fun ChapterCover(
    chapter: WearIveRunCollectionLogic.Chapter,
    onClick: () -> Unit,
    onShare: (WearIveRunDayEntry) -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RunWearColors.BgCard,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                    .background(RunWearColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (chapter.cover?.heroRef != null) {
                    AsyncImage(
                        model = chapter.cover.heroRef,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.place,
                    style = MaterialTheme.typography.titleMedium,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = chapter.summaryLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // W-1 (b) — Sol's narration (2:38–3:04, circling the blank
            // right side of a chapter row): "there should be two
            // buttons over here — one is a kind of a view and one is
            // a share." The view button opens the chapter (the row's
            // own tap does too); the share button opens the share
            // flow for the chapter's most recent day (its cover).
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = "View ${chapter.place} chapter",
                    tint = RunWearColors.TextSecondary
                )
            }
            IconButton(
                onClick = { chapter.cover?.let { onShare(it) } },
                enabled = chapter.cover != null
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "Share a day from ${chapter.place}",
                    tint = RunWearColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChapterDetail(
    chapter: WearIveRunCollectionLogic.Chapter,
    today: LocalDate,
    onShare: (WearIveRunDayEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = chapter.place,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = chapter.summaryLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RunWearColors.TextSecondary
                )
            }
        }
        if (chapter.days.isEmpty()) {
            item {
                Text(
                    text = "No days match this filter here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RunWearColors.TextSecondary
                )
            }
        } else if (chapter.isHometownScale) {
            // Year -> month groups; the grouping itself is the navigation.
            val years = WearIveRunCollectionLogic.yearCounts(chapter.days)
            years.forEach { (year, count) ->
                item(key = "year-$year") {
                    Text(
                        text = "$year · $count days",
                        style = MaterialTheme.typography.titleMedium,
                        color = RunWearColors.TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val months = WearIveRunCollectionLogic.monthGroups(chapter.days)
                    .filter { it.year == year }
                items(months, key = { "m-${it.year}-${it.month}" }) { group ->
                    Surface(shape = RoundedCornerShape(12.dp), color = RunWearColors.BgCard) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${group.label()} · ${group.count} days" +
                                    (group.rangeLabel()?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = RunWearColors.TextPrimary
                            )
                            group.days.forEach { day ->
                                DayRow(entry = day, today = today, onShare = { onShare(day) })
                            }
                        }
                    }
                }
            }
        } else {
            // Stays newest-first, days newest-first within the stay.
            chapter.stays.forEach { stay ->
                item(key = "stay-${stay.start}") {
                    Text(
                        text = WearIveRunCollectionLogic.spanLabel(stay.start, stay.end),
                        style = MaterialTheme.typography.titleSmall,
                        color = RunWearColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(stay.days, key = { "d-${it.date}-${it.place}" }) { day ->
                    DayCard(entry = day, today = today, onShare = { onShare(day) })
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DayRow(entry: WearIveRunDayEntry, today: LocalDate, onShare: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dayTitle(entry, today),
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextPrimary
            )
            Text(
                text = "${entry.feelsLikeF.toInt()}° feels like · ${entry.conditionLabel} · ${entry.items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextSecondary
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, "Share this day", tint = RunWearColors.TextSecondary)
        }
    }
}

@Composable
internal fun DayCard(
    entry: WearIveRunDayEntry,
    today: LocalDate,
    onShare: () -> Unit,
    /** W-10: when set (the pinned TODAY card), the card names its
     * place and shows its photo thumbnail, like the chapter cards. */
    place: String? = null,
    thumbnailRef: String? = null
) {
    Surface(shape = RoundedCornerShape(14.dp), color = RunWearColors.BgCard) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (place != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                            .background(RunWearColors.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnailRef != null) {
                            AsyncImage(
                                model = thumbnailRef,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = place,
                        style = MaterialTheme.typography.titleMedium,
                        color = RunWearColors.TextPrimary
                    )
                }
            }
            DayRow(entry = entry, today = today, onShare = onShare)
            if (entry.items.isNotEmpty()) {
                Text(
                    text = entry.items.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextSecondary
                )
            }
        }
    }
}

private fun dayTitle(entry: WearIveRunDayEntry, today: LocalDate): String {
    val dow = entry.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    return if (entry.date == today) "Today · $dow" else dow
}

/**
 * GEN-3 share-render: the working state for a rendering share
 * artifact — a labeled pill (spinner + what is rendering), shown
 * OVER the artifact while its render is genuinely in progress.
 * It replaces the in-flow progress rows both share stages used
 * before: a row inside the sheet's column added and removed its
 * own height as the ready-state flipped, shoving the artifact —
 * and the bottom sheet's own content-sized height — around on
 * every flip, the flicker family Sol's walkthrough verdict named.
 * The pill is state-driven (it exists exactly while the
 * artifact's rendered callback has not fired), carries a label
 * naming the work, and never moves the artifact beneath it.
 */
@Composable
private fun ShareRenderProgressPill(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = RunWearColors.BgCard,
        shadowElevation = 2.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = RunWearColors.PineAccent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

/**
 * GEN-3 share-render acceptance: the FAILED render's surface. A
 * render that failed says so — what failed, in words — and offers
 * its own way back (Close dismisses the sheet), instead of the
 * pre-fix outcomes (an endless progress state, or a silently
 * degraded card presented as finished). Overlaid like the
 * progress pill, so it never moves the artifact beneath it.
 */
@Composable
private fun ShareRenderErrorNotice(
    label: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = RunWearColors.BgCard,
        shadowElevation = 2.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

/**
 * The per-card share choice: Morning Poster or Kit Card — NO Range
 * (design frames 11–14). Presented as an M3 modal sheet over the
 * dimmed collection (motion spec §1.3), resting on the one full
 * anchor. Choosing an artifact previews it wrapped in the share
 * capture layer; Share rasterizes THAT preview to a bitmap and
 * sends the image via [ShareArtifacts] — the artifact the user is
 * looking at is the payload (D-16). There is no text payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareChoiceSheet(
    entry: WearIveRunDayEntry,
    gender: GenderPreference,
    onDismiss: () -> Unit
) {
    var artifact by remember { mutableStateOf<ShareArtifactKind?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberShareCapture()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            val chosen = artifact
            if (chosen == null) {
                ShareChooserStage(
                    entry = entry,
                    gender = gender,
                    onChoose = { artifact = it },
                    onCancel = onDismiss
                )
            } else {
                // W-9: while the chosen card renders (its photo
                // decoding can take seconds), the preview stage shows
                // a visible progress state — never a blank wait.
                // GEN-3 share-render: the state is now the labeled
                // pill OVERLAID on the preview (ShareRenderProgressPill),
                // not an in-flow row — the artifact's position and
                // the sheet's height no longer move when it clears.
                var previewReady by remember(chosen) { mutableStateOf(false) }
                // GEN-3 share-render acceptance: a failed render is
                // a state of its own — surfaced as an error with a
                // way back, Share withheld — never a finished card.
                var previewFailed by remember(chosen) { mutableStateOf(false) }
                Box {
                    Box(modifier = capture.recordModifier()) {
                        when (chosen) {
                            ShareArtifactKind.MORNING_POSTER -> MorningPoster(
                                entry = entry,
                                onRendered = { previewReady = true },
                                onRenderFailed = { previewFailed = true }
                            )
                            ShareArtifactKind.KIT_CARD -> KitCard(
                                entry = entry,
                                gender = gender,
                                onRendered = { previewReady = true },
                                onRenderFailed = { previewFailed = true }
                            )
                        }
                    }
                    if (previewFailed) {
                        ShareRenderErrorNotice(
                            label = "Couldn't render this card — its photo couldn't be loaded.",
                            onClose = onDismiss,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        )
                    } else if (!previewReady) {
                        ShareRenderProgressPill(
                            label = "Rendering ${chosenLabel(chosen)}…",
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(
                        // Share only ever captures a SETTLED,
                        // non-failed render (pre-fix it could fire
                        // mid-render and share an unfinished card).
                        enabled = previewReady && !previewFailed,
                        onClick = {
                            scope.launch {
                                val bitmap = capture.captureBitmap()
                                ShareArtifacts.share(context, bitmap, chosen)
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Share")
                    }
                }
            }
        }
    }
}

private fun chosenLabel(kind: ShareArtifactKind): String = when (kind) {
    ShareArtifactKind.MORNING_POSTER -> "Morning Poster"
    ShareArtifactKind.KIT_CARD -> "Kit Card"
}

/**
 * W-9: the share chooser's first stage — the settled design (frames
 * 11–14, and Sol's narration at 3:05–3:25: "We were supposed to show
 * thumbnails of what the morning poster looks like and what the kit
 * card looks like"). Each choice is the ACTUAL rendered artifact,
 * composed live and scaled into a thumbnail — not a bare text row.
 * Tapping a thumbnail selects that card. While either thumbnail is
 * still rendering, a visible progress row stands in place of the
 * ~15 s of nothing the walkthrough recorded.
 */
@Composable
internal fun ShareChooserStage(
    entry: WearIveRunDayEntry,
    gender: GenderPreference,
    onChoose: (ShareArtifactKind) -> Unit,
    onCancel: () -> Unit
) {
    var posterReady by remember(entry) { mutableStateOf(false) }
    var kitReady by remember(entry) { mutableStateOf(false) }
    Text(
        text = "Share this day",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = RunWearColors.TextPrimary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        text = "Choose the card for ${entry.place} — ${dayTitle(entry, LocalDate.now())}.",
        style = MaterialTheme.typography.bodyMedium,
        color = RunWearColors.TextSecondary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    // GEN-3 share-render: the "Rendering previews…" state is the
    // labeled pill overlaid on the thumbnail row — pre-fix it was
    // an in-flow row whose appearance/disappearance shifted both
    // thumbnails (and the sheet height) on every ready flip.
    Box {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShareThumbnailChoice(
                kind = ShareArtifactKind.MORNING_POSTER,
                entry = entry,
                gender = gender,
                onReady = { posterReady = true },
                modifier = Modifier.weight(1f)
            ) { onChoose(ShareArtifactKind.MORNING_POSTER) }
            ShareThumbnailChoice(
                kind = ShareArtifactKind.KIT_CARD,
                entry = entry,
                gender = gender,
                onReady = { kitReady = true },
                modifier = Modifier.weight(1f)
            ) { onChoose(ShareArtifactKind.KIT_CARD) }
        }
        if (!posterReady || !kitReady) {
            ShareRenderProgressPill(
                label = "Rendering previews…",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            )
        }
    }
    Text(
        text = "Cancel",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = RunWearColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
            .clickable { onCancel() }
            .padding(vertical = 14.dp)
    )
}

/**
 * GEN-3 X-11: the scale math for the chooser thumbnails. The
 * artifacts are composed at [NATURAL_WIDTH_DP] — the same width
 * the full-size preview uses — and the composed layer is scaled
 * to the thumbnail frame's width. Kept as a pure object so the
 * geometry pins (PosterThumbnailGeometryTest) read the production
 * numbers, not copies.
 */
internal object ArtifactThumbnailGeometry {

    /** The width every share artifact is composed at, thumbnail and preview alike. */
    const val NATURAL_WIDTH_DP = 340

    /** The thumbnail frame's height; the scaled artifact is top-anchored and clipped to it. */
    const val FRAME_HEIGHT_DP = 150

    /** Layer scale for an artifact of [naturalWidthPx] shown in a frame [frameWidthPx] wide. */
    fun scaleFor(frameWidthPx: Int, naturalWidthPx: Int): Float =
        if (naturalWidthPx <= 0) 1f else frameWidthPx.toFloat() / naturalWidthPx.toFloat()

    /** The on-screen height of an artifact [naturalHeightPx] tall at [scale]. */
    fun scaledHeightPx(naturalHeightPx: Int, scale: Float): Int =
        (naturalHeightPx * scale).roundToInt()
}

/**
 * GEN-3 X-11: measure the artifact at its NATURAL size — fixed
 * [ArtifactThumbnailGeometry.NATURAL_WIDTH_DP] width, unbounded
 * height, so a fixed-geometry artifact (the Morning Poster's
 * 420 dp) keeps its designed layout — then scale the composed
 * layer into the frame, top-anchored; the frame clips the rest.
 *
 * The pre-fix thumbnail instead composed the artifact INSIDE the
 * frame and scaled the result: the frame's constraints (~134 dp
 * wide × 150 dp high) coerced the poster's fixed 340 dp width and
 * 420 dp height down to the frame BEFORE the 0.42 graphicsLayer
 * scale applied, so the poster's top lockup (~80 dp) and its
 * bottom info column (~110 dp) were laid out inside a ≤150 dp
 * box and overlapped — the weather/place line rendered over the
 * wordmark (walkthrough f_080). The Kit Card, with no fixed
 * height and no overlay columns, composed cleanly either way.
 * The full-size poster does NOT collide (its bands sit ~230 dp
 * apart at natural size) — pinned by PosterThumbnailGeometryTest.
 */
@Composable
internal fun ArtifactThumbnailScaler(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val naturalWidthPx = ArtifactThumbnailGeometry.NATURAL_WIDTH_DP.dp.roundToPx()
        val placeable = measurables.single().measure(
            Constraints(
                minWidth = naturalWidthPx,
                maxWidth = naturalWidthPx,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
        )
        val scale = ArtifactThumbnailGeometry.scaleFor(
            frameWidthPx = constraints.maxWidth,
            naturalWidthPx = placeable.width
        )
        val scaledHeightPx = ArtifactThumbnailGeometry.scaledHeightPx(placeable.height, scale)
        val layoutHeight = if (constraints.maxHeight == Constraints.Infinity) {
            scaledHeightPx
        } else {
            minOf(scaledHeightPx, constraints.maxHeight)
        }
        layout(constraints.maxWidth, layoutHeight) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** One chooser thumbnail: the real artifact, composed at natural size and scaled to fit its card. */
@Composable
internal fun ShareThumbnailChoice(
    kind: ShareArtifactKind,
    entry: WearIveRunDayEntry,
    gender: GenderPreference,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RunWearColors.BgCard)
            .clickable { onClick() }
            .semantics { contentDescription = "${chosenLabel(kind)} thumbnail" }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(ArtifactThumbnailGeometry.FRAME_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RunWearColors.Surface)
        ) {
            // GEN-3 X-11: compose at natural size, scale the layer
            // (see ArtifactThumbnailScaler) — never compose the
            // artifact under the frame's own constraints.
            ArtifactThumbnailScaler {
                when (kind) {
                    ShareArtifactKind.MORNING_POSTER -> MorningPoster(
                        entry = entry,
                        modifier = Modifier.width(ArtifactThumbnailGeometry.NATURAL_WIDTH_DP.dp),
                        onRendered = onReady
                    )
                    ShareArtifactKind.KIT_CARD -> KitCard(
                        entry = entry,
                        modifier = Modifier.width(ArtifactThumbnailGeometry.NATURAL_WIDTH_DP.dp),
                        gender = gender,
                        onRendered = onReady
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = chosenLabel(kind),
            fontSize = 16.sp,
            color = RunWearColors.TextPrimary
        )
    }
}

/**
 * The Morning Poster artifact (frame 12): hero image, the script
 * "RunWear" lockup with #RunAnyWear directly under it, a dedicated
 * top-area scrim (gray gradient rgba(35,35,39,.62) at the top edge
 * fading to 0 by ~150dp, gone before the runner's head), poster body
 * in the UI sans, and the "Wear I've Run" script at the foot.
 */
@Composable
fun MorningPoster(
    entry: WearIveRunDayEntry,
    modifier: Modifier = Modifier,
    /** W-9: fires once the artifact has rendered (its hero photo
     * decoded, or immediately when the entry carries no photo) — the
     * share surfaces show a progress state until it fires. */
    onRendered: (() -> Unit)? = null,
    /** GEN-3 share-render acceptance: fires (once, together with
     * [onRendered]) when the artifact settled WITHOUT its photo —
     * the decode failed. The share preview turns this into an
     * explicit error surface with a way back; a failed render must
     * say so, never pass as a finished card. */
    onRenderFailed: (() -> Unit)? = null
) {
    // GEN-3 share-render: onRendered fires EXACTLY ONCE per entry,
    // when the poster is SETTLED — photo decoded, photo failed, or
    // no photo at all (the pattern the Kit Card already had for
    // success, extended to every terminal state). Pre-fix the
    // poster fired only on image SUCCESS and with no once-guard:
    // a decode error left the share sheet's rendering state up
    // forever — a spinner as the settled state — and every painter
    // re-delivery rewrote the sheet's ready-state from inside the
    // painter callback.
    var posterRenderReported by remember(entry) { mutableStateOf(false) }
    fun reportPosterRendered(failed: Boolean) {
        if (!posterRenderReported) {
            posterRenderReported = true
            if (failed) onRenderFailed?.invoke()
            onRendered?.invoke()
        }
    }
    if (entry.heroRef == null) {
        // Nothing to load: the poster is settled the moment its
        // composition applies. SideEffect (not LaunchedEffect) —
        // it IS the composition-applied hook, it carries no frame
        // of effect-scheduling latency, and the once-guard makes
        // its every-recomposition firing a no-op after the first.
        SideEffect { reportPosterRendered(failed = false) }
    }
    Box(
        modifier = modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp))
            .background(RunWearColors.Background)
    ) {
        if (entry.heroRef != null) {
            AsyncImage(
                model = entry.heroRef,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { painterState ->
                    when (painterState) {
                        is coil.compose.AsyncImagePainter.State.Success ->
                            reportPosterRendered(failed = false)
                        is coil.compose.AsyncImagePainter.State.Error ->
                            reportPosterRendered(failed = true)
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // Top lockup scrim — its own layer, gone by ~150dp.
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            com.runwear.app.ui.theme.PosterScrim.copy(alpha = 0.62f),
                            com.runwear.app.ui.theme.PosterScrim.copy(alpha = 0.31f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "RunWear",
                fontFamily = GreatVibesFontFamily,
                fontSize = 37.sp,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            Text(
                text = "#RunAnyWear",
                style = MaterialTheme.typography.labelLarge,
                color = com.runwear.app.ui.theme.PhotoInk
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = "${entry.feelsLikeF.toInt()}° · ${entry.conditionLabel} · ${entry.place}",
                style = MaterialTheme.typography.titleMedium,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            Text(
                text = entry.items.take(4).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            Text(
                text = WearIveRunNarrationFormatter.WORDMARK,
                fontFamily = GreatVibesFontFamily,
                fontSize = 24.sp,
                color = com.runwear.app.ui.theme.PhotoInk,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** The engine item behind an entry's display name, when it is one. */
internal fun clothingItemForDisplayName(displayName: String): ClothingItem? =
    ClothingItem.entries.firstOrNull { it.displayName == displayName }

/**
 * The Kit Card artifact (frame 13): the day's kit as the app's REAL
 * garment renders — each item's bundled clothing render (the same
 * assets the outfit cards show, resolved through
 * [ClothingThumbnailService] for the user's fit preference) beside
 * its name. An item name the engine doesn't know renders name-only
 * rather than borrowing another garment's picture.
 */
@Composable
fun KitCard(
    entry: WearIveRunDayEntry,
    modifier: Modifier = Modifier,
    gender: GenderPreference = GenderPreference.UNISEX,
    /** W-9: fires once the card has rendered (its first garment
     * render decoded, or immediately when no item resolves to a
     * render) — the share surfaces show progress until it fires. */
    onRendered: (() -> Unit)? = null,
    /** GEN-3 share-render acceptance: fires (once, together with
     * [onRendered]) when the card settled on a FAILED garment
     * decode — the preview surfaces it as an error, not a card. */
    onRenderFailed: (() -> Unit)? = null
) {
    val anyRender = entry.items.any { itemName ->
        clothingItemForDisplayName(itemName)?.let {
            ClothingThumbnailService.getThumbnailAssetUri(it, gender)
        } != null
    }
    var renderReported by remember(entry) { mutableStateOf(false) }
    if (!anyRender) {
        // Nothing to load — settled on composition apply (see the
        // poster's SideEffect note); routed through the same
        // once-guard as the painter path.
        SideEffect {
            if (!renderReported) {
                renderReported = true
                onRendered?.invoke()
            }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = RunWearColors.BgCard
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.place,
                style = MaterialTheme.typography.titleMedium,
                color = RunWearColors.TextPrimary
            )
            Text(
                text = "${entry.feelsLikeF.toInt()}° feels like · ${entry.conditionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            entry.items.forEach { itemName ->
                val item = clothingItemForDisplayName(itemName)
                val renderUri = item?.let {
                    ClothingThumbnailService.getThumbnailAssetUri(it, gender)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    if (renderUri != null) {
                        AsyncImage(
                            model = renderUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onState = { painterState ->
                                // GEN-3 share-render: the card
                                // settles on the first render's
                                // TERMINAL state — success OR error
                                // (a failure also reports through
                                // onRenderFailed). Pre-fix only
                                // success reported, so a failed
                                // garment decode hung the share
                                // sheet's rendering state just like
                                // the poster's did.
                                val failed = when (painterState) {
                                    is coil.compose.AsyncImagePainter.State.Success -> false
                                    is coil.compose.AsyncImagePainter.State.Error -> true
                                    else -> null
                                }
                                if (!renderReported && failed != null) {
                                    renderReported = true
                                    if (failed) onRenderFailed?.invoke()
                                    onRendered?.invoke()
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RunWearColors.TextPrimary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "#RunAnyWear",
                style = MaterialTheme.typography.labelLarge,
                color = RunWearColors.PineAccent
            )
        }
    }
}
