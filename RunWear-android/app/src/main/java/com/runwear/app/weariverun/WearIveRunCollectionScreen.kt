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
import androidx.compose.ui.layout.ContentScale
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
                                    DayCard(entry = todayEntry, today = today, onShare = { shareEntry = todayEntry })
                                }
                            }
                        }
                        items(chapters, key = { it.place }) { ch ->
                            if (ch.days.isNotEmpty()) {
                                ChapterCover(
                                    chapter = ch,
                                    onClick = { openChapter = ch.place }
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
private fun ChapterCover(
    chapter: WearIveRunCollectionLogic.Chapter,
    onClick: () -> Unit
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
private fun DayCard(entry: WearIveRunDayEntry, today: LocalDate, onShare: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = RunWearColors.BgCard) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Morning Poster",
                    fontSize = 16.sp,
                    color = RunWearColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { artifact = ShareArtifactKind.MORNING_POSTER }
                        .padding(vertical = 14.dp)
                )
                Text(
                    text = "Kit Card",
                    fontSize = 16.sp,
                    color = RunWearColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { artifact = ShareArtifactKind.KIT_CARD }
                        .padding(vertical = 14.dp)
                )
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp)
                )
            } else {
                Box(modifier = capture.recordModifier()) {
                    when (chosen) {
                        ShareArtifactKind.MORNING_POSTER -> MorningPoster(entry = entry)
                        ShareArtifactKind.KIT_CARD -> KitCard(entry = entry, gender = gender)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(
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

/**
 * The Morning Poster artifact (frame 12): hero image, the script
 * "RunWear" lockup with #RunAnyWear directly under it, a dedicated
 * top-area scrim (gray gradient rgba(35,35,39,.62) at the top edge
 * fading to 0 by ~150dp, gone before the runner's head), poster body
 * in the UI sans, and the "Wear I've Run" script at the foot.
 */
@Composable
fun MorningPoster(entry: WearIveRunDayEntry, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp))
            .background(RunWearColors.Background)
    ) {
        if (entry.heroRef != null) {
            AsyncImage(
                model = entry.heroRef,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
    gender: GenderPreference = GenderPreference.UNISEX
) {
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
