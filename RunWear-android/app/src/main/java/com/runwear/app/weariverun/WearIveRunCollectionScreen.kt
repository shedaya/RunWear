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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onViewPhoto: (WearIveRunDayEntry) -> Unit = {}
) {
    var filter by remember { mutableStateOf(WearWeatherFilter.ALL) }
    var openChapter by remember { mutableStateOf<String?>(null) }
    var shareEntry by remember { mutableStateOf<WearIveRunDayEntry?>(null) }

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
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_back), tint = RunWearColors.TextPrimary)
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
                        Icon(Icons.Outlined.Close, androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_close), tint = RunWearColors.TextPrimary)
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
                        onDeleteEntry = onDeleteEntry,
                        onDeletePlace = { place ->
                            onDeletePlace(place)
                            openChapter = null
                        },
                        onViewPhoto = onViewPhoto
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
                                        text = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_today_badge),
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
                                        onDelete = { onDeleteEntry(pinnedTodayEntry) },
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
                                    temperatureUnit = temperatureUnit
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
            onDismiss = { shareEntry = null }
        )
    }
}

@Composable
private fun FilterRow(filter: WearWeatherFilter, onFilter: (WearWeatherFilter) -> Unit) {
    val labels = listOf(
        WearWeatherFilter.ALL to androidx.compose.ui.res.stringResource(com.runwear.app.R.string.filter_all),
        WearWeatherFilter.COLD to androidx.compose.ui.res.stringResource(com.runwear.app.R.string.filter_cold),
        WearWeatherFilter.SNOW to androidx.compose.ui.res.stringResource(com.runwear.app.R.string.filter_snow),
        WearWeatherFilter.RAIN to androidx.compose.ui.res.stringResource(com.runwear.app.R.string.filter_rain),
        WearWeatherFilter.HEAT to androidx.compose.ui.res.stringResource(com.runwear.app.R.string.filter_heat)
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
                text = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

/**
 * GEN-6 Wave B: a collection cover — the NEW Morning Poster in
 * FEED format, composed at its canvas size and scaled into the
 * cover slot. Covers are DISPLAY, not export: the hero loads via
 * [rememberHeroBitmap], the anchor is the entry's stored anchor
 * or the default composition point, and no face detection runs.
 */
@Composable
private fun PosterCover(
    entry: WearIveRunDayEntry,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val hero = rememberHeroBitmap(entry.heroRef)
    val today = remember { LocalDate.now() }
    val input = remember(entry, temperatureUnit) {
        ShareCaptionInput.forEntry(entry, temperatureUnit, today, LocalDateTime.now())
    }
    val forms = remember(entry, today) {
        ShareMomentResolver.forms(
            reading = ShareMomentResolver.readingFor(entry),
            dayStart = entry.date,
            lastSavedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.settledAtMillis), ZoneId.systemDefault()
            ),
            now = LocalDateTime.now(),
            today = today
        )
    }
    val anchor = entry.faceAnchor ?: ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
    val cropped = remember(hero, anchor) {
        hero?.let { ShareAnchorCrop.cropBitmap(it, anchor, ShareExportFormat.FEED.aspect) }
    }
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val scale = maxWidth.value / 360f
        Box(
            // RW-99 (Nov 6, 2026): the canvas MUST compose at its
            // full 360x450 design size and be scaled down by the
            // graphics layer. width()/height() are coercible — the
            // cover slot's constraints (46x58 / 58x72) shrank the
            // canvas itself to slot size, so the poster's
            // fixed-canvas content clipped to a top-left fragment
            // (the walkthrough's tiny broken remnant on every
            // collection card). requiredWidth/requiredHeight hold
            // the design size against the slot; the slot-sized,
            // clipped wrapper bounds the scaled render.
            modifier = Modifier.requiredWidth(360.dp).requiredHeight(450.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            MorningPoster(
                entry = entry,
                input = input,
                posterMoment = forms.poster,
                format = ShareExportFormat.FEED,
                heroBitmap = cropped,
                faceAnchor = anchor
            )
        }
    }
}

@Composable
internal fun ChapterCover(
    chapter: WearIveRunCollectionLogic.Chapter,
    onClick: () -> Unit,
    onShare: (WearIveRunDayEntry) -> Unit = {},
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RunWearColors.BgCard,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                // GEN-5 (RW-29 FA-41): the settled chapter cover
                // geometry — the design's chapter row carries a
                // 58x72 PORTRAIT thumbnail (mockups .chap img).
                // GEN-6 Wave B: the slot now renders the NEW
                // Morning Poster (Feed) as the chapter's cover.
                modifier = Modifier.width(58.dp).height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RunWearColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                val cover = chapter.cover
                if (cover != null) {
                    PosterCover(
                        entry = cover,
                        temperatureUnit = temperatureUnit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // GEN-5 (Miami crash addendum): a cover-less
                    // chapter's designed treatment — the tinted slot
                    // carries the run glyph, never a bare blank.
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
                    contentDescription = com.runwear.app.i18n.AppStrings.viewChapter(chapter.place),
                    tint = RunWearColors.TextSecondary
                )
            }
            // GEN-5 (RW-29 FA-66): the cover's share door must
            // make plain WHICH day it shares — the gen-4 bare icon
            // read as sharing the chapter while silently selecting
            // its most recent day. The door now names that day.
            // LOC-1 WP-R2: the share-door labels resolve HERE, in
            // composition — stringResource cannot run inside the
            // semantics DSL block below (not a @Composable context).
            val shareDayLabel = chapter.cover?.let {
                androidx.compose.ui.res.stringResource(
                    com.runwear.app.R.string.collection_share_day,
                    WearIveRunCollectionLogic.spanLabel(it.date, it.date),
                    chapter.place
                )
            }
            val shareLabel = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_share)
            TextButton(
                onClick = { chapter.cover?.let { onShare(it) } },
                enabled = chapter.cover != null,
                modifier = Modifier.semantics {
                    contentDescription = shareDayLabel ?: shareLabel
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
                    } ?: androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_share),
                    fontSize = 12.sp,
                    color = RunWearColors.TextSecondary
                )
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
    onViewPhoto: ((WearIveRunDayEntry) -> Unit)? = null
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
                            text = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_remove_all, chapter.place),
                            color = RunWearColors.TextSecondary
                        )
                    }
                }
            }
        }
        if (chapter.days.isEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_no_match),
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
                                text = group.label() + com.runwear.app.i18n.AppEnglish.WIR_MONTH_COUNT_SUFFIX.format(com.runwear.app.i18n.AppEnglish.WIR_YEAR_COUNT.format(group.count)) +
                                    (group.rangeLabel()?.let { com.runwear.app.i18n.AppEnglish.WIR_MONTH_COUNT_SUFFIX.format(it) } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = RunWearColors.TextPrimary
                            )
                            group.days.forEach { day ->
                                DayRow(
                                    entry = day, today = today,
                                    onShare = { onShare(day) },
                                    onDelete = onDeleteEntry?.let {
                                        { it(day) }
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
                        temperatureUnit = temperatureUnit
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
    onDelete: (() -> Unit)? = null
) {
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
                // WT-3: singular agreement — the same
                // "1 item" / "N items" rule WearIveRunSubtitle
                // already applies (the card read "1 items").
                text = "${entry.feelsLikeF.toInt()}° feels like · ${entry.conditionLabel} · " +
                    androidx.compose.ui.res.pluralStringResource(com.runwear.app.R.plurals.wir_items_count, entry.items.size, entry.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = RunWearColors.TextSecondary
            )
        }
        if (onView != null) {
            IconButton(onClick = onView) {
                Icon(
                    Icons.Outlined.Visibility,
                    androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_view_chapter, entry.place),
                    tint = RunWearColors.TextSecondary
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, androidx.compose.ui.res.stringResource(com.runwear.app.R.string.collection_share_this_day), tint = RunWearColors.TextSecondary)
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
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RunWearColors.BgCard,
        modifier = if (onPhotoClick != null && entry.heroRef != null) {
            Modifier.clickable { onPhotoClick() }
        } else {
            Modifier
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (place != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        // GEN-5 (RW-29 FA-41/L-5): the day-row
                        // thumbnail takes the design's 46x58
                        // portrait geometry (.dayrow img).
                        // GEN-6 Wave B: the slot renders the NEW
                        // Morning Poster (Feed) as the cover.
                        modifier = Modifier.width(46.dp).height(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RunWearColors.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        PosterCover(
                            entry = entry,
                            temperatureUnit = temperatureUnit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = place,
                        style = MaterialTheme.typography.titleMedium,
                        color = RunWearColors.TextPrimary
                    )
                }
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
    val dow = com.runwear.shared.i18n.LocaleFormats.dateWeekdayMonthDay(entry.date)
    return if (entry.date == today) com.runwear.app.i18n.AppEnglish.COLLECTION_DAY_TITLE_TODAY.format(dow) else dow
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
