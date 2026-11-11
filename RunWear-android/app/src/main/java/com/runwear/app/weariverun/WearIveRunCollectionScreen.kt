package com.runwear.app.weariverun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RW-26: the Wear I've Run collection screen (design frames 7–10,
 * 50–52). Chapters BY LOCATION with computed summaries; the weather
 * filter applies at collection level and inside a chapter and
 * recomputes every summary it touches; today is pinned; sharing
 * happens inside, card by card (Morning Poster or Kit Card — no
 * Range, no collection-level sharing in v1).
 *
 * GEN-6 Wave B (RW-31): every share door in this screen — the
 * collection cards' share icons, the pinned TODAY card, the
 * chapter covers — opens the ONE Share Studio
 * ([ShareStudioSheet]); the gen-5 chooser sheet and its
 * preview-capture flow are deleted. Chapter covers and the TODAY
 * card render the NEW Morning Poster (Feed format) as their
 * cover — display only, never an export.
 */
@Composable
fun WearIveRunCollectionScreen(
    entries: List<WearIveRunDayEntry>,
    today: LocalDate,
    onDismiss: () -> Unit,
    /** Fit preference — selects the Kit Card's garment renders. */
    gender: GenderPreference = GenderPreference.UNISEX,
    /** Display-unit preference — fills the share captions and
     * poster figures in the user's unit. */
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /** RW-122: delete one entry (identity delete at the store). */
    onDeleteEntry: (WearIveRunDayEntry) -> Unit = {},
    /** RW-122: delete every entry under one place. */
    onDeletePlace: (String) -> Unit = {},
    /** RW-133: open an entry's stored photograph full-screen. */
    onViewPhoto: (WearIveRunDayEntry) -> Unit = {},
    /**
     * RW-152 §3 (Nov 10, 2026): the HeroRefResolver's re-sign seam
     * — a suspend call that turns a personal-heroes Storage path
     * into a fresh signed display URL at render time. Null in
     * render proofs (their refs are stable file URLs, which pass
     * through the resolver untouched).
     */
    heroResign: (suspend (String) -> String?)? = null
) {
    var filter by remember { mutableStateOf(WearWeatherFilter.ALL) }
    var openChapter by remember { mutableStateOf<String?>(null) }
    var shareEntry by remember { mutableStateOf<WearIveRunDayEntry?>(null) }
    // RW-152 §4 / J1 (owner ruling, Nov 9, 2026): deleting a PAST
    // entry requires a confirmation NAMING the place and the date;
    // today's entry stays one-tap (it re-settles; a past entry
    // never does, and deletion is device-local and unrecoverable).
    var pendingPastDelete by remember {
        mutableStateOf<WearIveRunDayEntry?>(null)
    }
    val requestDelete: (WearIveRunDayEntry) -> Unit = { entry ->
        if (entry.date == today) {
            onDeleteEntry(entry)
        } else {
            pendingPastDelete = entry
        }
    }

    // GEN-3 (X-5): the pinned TODAY card IS today's latest settled
    // entry — the same shared resolution the mini bar's holder
    // uses (never an alphabetical place pick, which froze the card
    // on the day's first place). The chapter list below excludes
    // that very entry, so the pinned card never duplicates a
    // chapter directly beneath it; a chapter for its place still
    // shows when the place holds OTHER entries.
    val todayEntry = remember(entries) {
        WearIveRunCollectionLogic.todaysEntry(entries, today)
    }
    // GEN-5 (RW-29 FA-35): the pinned TODAY card answers to the
    // weather filter like everything else in the collection —
    // the gen-4 pin ignored the filter, so filtering to Rain
    // still pinned a clear-day TODAY card over the filtered list.
    val pinnedTodayEntry = remember(todayEntry, filter) {
        todayEntry?.takeIf {
            WearIveRunCollectionLogic.matches(it, filter)
        }
    }
    val listChapters = remember(entries, filter, pinnedTodayEntry) {
        // GEN-5 (FA-35): the exclusion follows the PIN — an entry
        // is kept out of the chapter list only while it is
        // actually pinned above it; when the filter unpins it,
        // the entry belongs to its chapter again.
        val rest = if (pinnedTodayEntry != null) {
            entries.filterNot {
                WearIveRunCollectionLogic.sameIdentity(it, pinnedTodayEntry)
            }
        } else {
            entries
        }
        WearIveRunCollectionLogic.chapters(rest, filter)
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

                val chapter = listChapters.firstOrNull { it.place == openChapter }
                if (chapter != null) {
                    ChapterDetail(
                        chapter = chapter,
                        today = today,
                        onShare = { shareEntry = it },
                        temperatureUnit = temperatureUnit,
                        modifier = Modifier.weight(1f),
                        onDeleteEntry = requestDelete,
                        onDeletePlace = { place ->
                            onDeletePlace(place)
                            openChapter = null
                        },
                        onViewPhoto = onViewPhoto,
                        heroResign = heroResign
                    )
                } else if (entries.isEmpty()) {
                    HonestEmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pinnedTodayEntry != null) {
                            item(key = "today") {
                                Column {
                                    Text(
                                        text = "TODAY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RunWearColors.TextSecondary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    DayCard(
                                        entry = pinnedTodayEntry,
                                        today = today,
                                        onShare = { shareEntry = pinnedTodayEntry },
                                        // W-10: the pinned TODAY card names
                                        // its place and shows its photo,
                                        // like the chapter cards below it.
                                        place = pinnedTodayEntry.place,
                                        thumbnailRef = pinnedTodayEntry.heroRef,
                                        // GEN-5 (item 10 / B-18): the
                                        // TODAY card carries its "Last
                                        // saved …" line, and the same
                                        // view action the chapter rows
                                        // carry — one action set for
                                        // one entry type. The view
                                        // action exists only when a
                                        // chapter for the place does.
                                        lastSavedLabel = WearIveRunCollectionLogic
                                            .lastSavedLabel(
                                                pinnedTodayEntry,
                                                System.currentTimeMillis()
                                            ),
                                        temperatureUnit = temperatureUnit,
                                        pinned = true,
                                        heroResign = heroResign,
                                        onDelete = { requestDelete(pinnedTodayEntry) },
                                        onPhotoClick = { onViewPhoto(pinnedTodayEntry) },
                                        onView = if (
                                            listChapters.any {
                                                it.place == pinnedTodayEntry.place
                                            }
                                        ) {
                                            { openChapter = pinnedTodayEntry.place }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                        items(listChapters, key = { it.place }) { ch ->
                            if (ch.days.isNotEmpty()) {
                                ChapterCover(
                                    chapter = ch,
                                    onClick = { openChapter = ch.place },
                                    onShare = { shareEntry = it },
                                    temperatureUnit = temperatureUnit,
                                    heroResign = heroResign,
                                    // RW-152 §4 / J2: the cover's
                                    // trash deletes the cover
                                    // (representative) entry ONLY —
                                    // never the whole place group.
                                    onDelete = ch.cover?.let { coverEntry ->
                                        { requestDelete(coverEntry) }
                                    }
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
        ShareStudioSheet(
            entry = entry,
            gender = gender,
            temperatureUnit = temperatureUnit,
            onDismiss = { shareEntry = null },
            heroResign = heroResign,
            // RW-158: the sheet's poster preview is the eye's new
            // home — tapping it opens the entry's full photo
            // (RW-133) through the same host door as before.
            onViewPhoto = onViewPhoto
        )
    }

    // RW-152 §4 / J1: the past-entry delete confirmation — it
    // NAMES the place and the date, and says plainly that the
    // entry is stored only on this device and deleting cannot
    // be undone.
    pendingPastDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingPastDelete = null },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "Delete the ${entry.place} entry from " +
                        entry.date.format(
                            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
                        ) +
                        "? This entry is stored only on this device, " +
                        "and deleting it cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPastDelete = null
                    onDeleteEntry(entry)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPastDelete = null }) {
                    Text("Keep")
                }
            }
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
                // GEN-5 (RW-29 FA-16): the settled empty state
                // (design frame 10) — the gen-4 copy was invented
                // (an invented placeholder line) and spoke of sessions where
                // the frame speaks of days, and it dropped the
                // no-backfill statement entirely.
                text = "Your scrapbook starts with today's run",
                style = MaterialTheme.typography.titleMedium,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Each day's outfit is saved here automatically — one card per day, in the places you run. Days before today aren't backfilled; the collection grows from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

/**
 * RW-152 §3 (Nov 10, 2026): the ONE collection thumbnail loader.
 * Chapter covers and stay cards load the entry's photo DIRECTLY
 * (Coil AsyncImage, top/centre crop per the settled poster
 * framing, decoded at display size x2) through [HeroRefResolver]
 * — the 360x450 MorningPoster composition is no longer rendered
 * into a thumbnail slot; it remains only where a poster is the
 * product (share/export). An entry with no loadable reference
 * keeps the honest empty slot (tinted ground + run glyph).
 */
@Composable
internal fun CollectionThumbnail(
    entry: WearIveRunDayEntry,
    heroResign: (suspend (String) -> String?)? = null,
    modifier: Modifier = Modifier
) {
    val url = rememberResolvedHeroUrl(entry, heroResign)
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RunWearColors.Surface)
            .semantics {
                contentDescription =
                    if (HeroRefResolver.hasLoadableReference(entry)) {
                        "Entry photo"
                    } else {
                        "No photo for this entry"
                    }
            },
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            val context = LocalContext.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
            val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    // Display size x2 — never a full-resolution
                    // decode for a thumbnail slot.
                    .size(widthPx * 2, heightPx * 2)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Outlined.DirectionsRun,
                contentDescription = null,
                tint = RunWearColors.TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
internal fun ChapterCover(
    chapter: WearIveRunCollectionLogic.Chapter,
    onClick: () -> Unit,
    onShare: (WearIveRunDayEntry) -> Unit = {},
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /** RW-152 §3: the resolver's re-sign seam. */
    heroResign: (suspend (String) -> String?)? = null,
    /** RW-152 §4 / J2: trash for the cover (representative) entry
     * only. Null = no delete affordance. */
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RunWearColors.BgCard,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // GEN-5 (RW-29 FA-41): the settled chapter cover
            // geometry — a 58x72 PORTRAIT thumbnail. RW-152 §3:
            // the slot is a direct photo load through the
            // resolver (CollectionThumbnail), not a composed
            // poster; a cover-less chapter keeps the glyph slot.
            val cover = chapter.cover
            if (cover != null) {
                CollectionThumbnail(
                    entry = cover,
                    heroResign = heroResign,
                    modifier = Modifier.width(58.dp).height(72.dp)
                )
            } else {
                Box(
                    modifier = Modifier.width(58.dp).height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RunWearColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.DirectionsRun,
                        contentDescription = null,
                        tint = RunWearColors.TextSecondary,
                        modifier = Modifier.size(26.dp)
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
            // GEN-5 (RW-29 FA-66): the cover's share door must
            // make plain WHICH day it shares — the gen-4 bare icon
            // read as sharing the chapter while silently selecting
            // its most recent day. The door now names that day.
            TextButton(
                onClick = { chapter.cover?.let { onShare(it) } },
                enabled = chapter.cover != null,
                modifier = Modifier.semantics {
                    contentDescription = chapter.cover?.let {
                        "Share ${
                            WearIveRunCollectionLogic.spanLabel(it.date, it.date)
                        } from ${chapter.place}"
                    } ?: "Share"
                }
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = chapter.cover?.let {
                        WearIveRunCollectionLogic.spanLabel(it.date, it.date)
                    } ?: "Share",
                    fontSize = 12.sp,
                    color = RunWearColors.TextSecondary
                )
            }
            // RW-152 §4: every chapter cover carries the same
            // trash affordance the TODAY card has, bound to
            // removeByIdentity for the cover entry ONLY (J2).
            if (onDelete != null && chapter.cover != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete the ${chapter.place} cover entry",
                        tint = RunWearColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChapterDetail(
    chapter: WearIveRunCollectionLogic.Chapter,
    today: LocalDate,
    onShare: (WearIveRunDayEntry) -> Unit,
    modifier: Modifier = Modifier,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /** RW-122: per-entry delete inside the chapter. */
    onDeleteEntry: ((WearIveRunDayEntry) -> Unit)? = null,
    /** RW-122: delete the whole location group (this chapter). */
    onDeletePlace: ((String) -> Unit)? = null,
    /** RW-133: open an entry's stored photograph full-screen. */
    onViewPhoto: ((WearIveRunDayEntry) -> Unit)? = null,
    /** RW-152 §3: the resolver's re-sign seam. */
    heroResign: (suspend (String) -> String?)? = null
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
                // RW-122: the by-location group delete — every
                // entry collated under this place, one action.
                if (onDeletePlace != null) {
                    TextButton(onClick = { onDeletePlace(chapter.place) }) {
                        Text(
                            text = "Remove all ${chapter.place} entries",
                            color = RunWearColors.TextSecondary
                        )
                    }
                }
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
                                DayRow(
                                    entry = day, today = today,
                                    onShare = { onShare(day) },
                                    onDelete = onDeleteEntry?.let {
                                        { it(day) }
                                    },
                                    // RW-158 (user ruling,
                                    // Nov 10, 2026): a PAST day's
                                    // row tap opens its share
                                    // studio; today's row is
                                    // unchanged.
                                    onRowTap = if (day.date != today) {
                                        { onShare(day) }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Stays newest-first, days newest-first within the stay.
            // GEN-5 (Miami crash fix): lazy keys carry the stay and
            // day INDICES, so they are unique for ANY data shape —
            // including two entries for the same date at the same
            // place (two sessions, one day), whose (date, place)
            // keys collided pre-fix and threw
            // IllegalArgumentException ("Key ... was already used")
            // the moment the place detail composed.
            chapter.stays.forEachIndexed { stayIndex, stay ->
                item(key = "stay-$stayIndex-${stay.start}") {
                    Text(
                        text = WearIveRunCollectionLogic.spanLabel(stay.start, stay.end),
                        style = MaterialTheme.typography.titleSmall,
                        color = RunWearColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                itemsIndexed(
                    stay.days,
                    key = { dayIndex, day ->
                        "d-$stayIndex-$dayIndex-${day.date}-${day.place}"
                    }
                ) { _, day ->
                    DayCard(
                        entry = day,
                        today = today,
                        onShare = { onShare(day) },
                        onDelete = onDeleteEntry?.let { { it(day) } },
                        onPhotoClick = onViewPhoto?.let { { it(day) } },
                        // RW-158 (user ruling, Nov 10, 2026): a
                        // PAST day's card tap opens the share
                        // studio for that day; today's card keeps
                        // the RW-133 full-photo tap.
                        onCardTap = if (day.date != today) {
                            { onShare(day) }
                        } else {
                            null
                        },
                        temperatureUnit = temperatureUnit,
                        heroResign = heroResign
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DayRow(
    entry: WearIveRunDayEntry,
    today: LocalDate,
    onShare: () -> Unit,
    onView: (() -> Unit)? = null,
    // RW-122: per-entry delete (the collection previously offered
    // no delete at all). One tap, no confirmation — the same
    // behaviour as the home section's trash.
    onDelete: (() -> Unit)? = null,
    // RW-158 (user ruling, Nov 10, 2026): a PAST day's row tap
    // opens the share studio for that day. Wired only by the
    // chapter detail's hometown rows; the DayCard's own DayRow
    // leaves it null (the card carries the tap there).
    onRowTap: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (onRowTap != null) {
                    Modifier.clickable { onRowTap() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dayTitle(entry, today),
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextPrimary
            )
            Text(
                // WT-3: singular agreement — the same
                // "1 item" / "N items" rule WearIveRunSubtitle
                // already applies (the card read "1 items").
                text = "${entry.feelsLikeF.toInt()}° feels like · ${entry.conditionLabel} · " +
                    if (entry.items.size == 1) "1 item" else "${entry.items.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextSecondary
            )
        }
        if (onView != null) {
            IconButton(onClick = onView) {
                Icon(
                    Icons.Outlined.Visibility,
                    "View ${entry.place} chapter",
                    tint = RunWearColors.TextSecondary
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, "Share this day", tint = RunWearColors.TextSecondary)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    "Delete this entry",
                    tint = RunWearColors.TextSecondary
                )
            }
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
    thumbnailRef: String? = null,
    /** GEN-5 (item 10 / B-18): the "Last saved …" line (TODAY card). */
    lastSavedLabel: String? = null,
    /** GEN-5 (B-18): the view action chapter rows carry — the same
     * action set for the same entry type. Null = no chapter to view. */
    onView: (() -> Unit)? = null,
    /** RW-122: per-entry delete. Null = no delete affordance. */
    onDelete: (() -> Unit)? = null,
    /** RW-133: open the entry's stored photograph full-screen.
     * Wired only when the entry carries a photo reference. */
    onPhotoClick: (() -> Unit)? = null,
    /** RW-158 (user ruling, Nov 10, 2026): pressing a PAST entry
     * opens the share studio for that entry directly — the card
     * tap routes here when set, ahead of the photo. Today's card
     * is never given this (its tap keeps the RW-133 photo). */
    onCardTap: (() -> Unit)? = null,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /** RW-152 §3: the pinned TODAY variant is a FLAG on the one
     * DayCard, not a different call — place == null no longer
     * means "no image slot"; every stay card carries the slot. */
    pinned: Boolean = false,
    /** RW-152 §3: the resolver's re-sign seam. */
    heroResign: (suspend (String) -> String?)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RunWearColors.BgCard,
        modifier = when {
            // RW-158: a past entry's card tap opens its share
            // studio (the caller wires this only for past days).
            onCardTap != null -> Modifier.clickable { onCardTap() }
            onPhotoClick != null && entry.heroRef != null ->
                Modifier.clickable { onPhotoClick() }
            else -> Modifier
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // RW-152 §3: ONE DayCard — the place + thumbnail row
            // renders for every entry (the pinned TODAY card is
            // the same card with pinned = true); the place falls
            // back to the entry's own place, and the slot is the
            // direct resolver load (46x58 geometry, GEN-5 FA-41).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CollectionThumbnail(
                    entry = entry,
                    heroResign = heroResign,
                    modifier = Modifier.width(46.dp).height(58.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = place ?: entry.place,
                    style = MaterialTheme.typography.titleMedium,
                    color = RunWearColors.TextPrimary
                )
            }
            DayRow(
                entry = entry, today = today, onShare = onShare,
                onView = onView, onDelete = onDelete
            )
            if (lastSavedLabel != null) {
                Text(
                    text = lastSavedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextSecondary
                )
            }
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

internal fun dayTitle(entry: WearIveRunDayEntry, today: LocalDate): String {
    val dow = entry.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    return if (entry.date == today) "Today · $dow" else dow
}

/** The engine item behind an entry's display name, when it is one. */
internal fun clothingItemForDisplayName(displayName: String): ClothingItem? =
    ClothingItem.entries.firstOrNull { it.displayName == displayName }

/**
 * GEN-5 (RW-29 FA-21): the Morning Poster's human moment line —
 * the entry's recorded moment reading when it carries one
 * ("Wednesday · 9 p.m."), else the day part of its settled
 * moment ("Wednesday morning"), else the bare weekday for
 * legacy entries that recorded neither.
 *
 * GEN-6 Wave B: the poster itself now composes its moment from
 * [ShareMomentResolver] (the unified §5.1 forms); this formatter
 * is retained as the collection's settled day language.
 */
internal fun posterMomentLine(entry: WearIveRunDayEntry): String {
    val weekday = entry.date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.FULL, java.util.Locale.US
    )
    val moment = entry.momentLabel
    if (!moment.isNullOrBlank()) return "$weekday · $moment"
    if (entry.settledAtMillis > 0L) {
        val hour = java.time.Instant.ofEpochMilli(entry.settledAtMillis)
            .atZone(java.time.ZoneId.systemDefault()).hour
        val part = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
        return "$weekday $part"
    }
    return weekday
}
