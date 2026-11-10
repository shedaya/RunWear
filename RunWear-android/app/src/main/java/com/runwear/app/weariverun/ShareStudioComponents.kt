package com.runwear.app.weariverun

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.screens.buildAmazonSearchUrl
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.PhotoInk
import com.runwear.app.ui.theme.PosterScrim
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.getTemperatureFallbackGradient
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GEN-6 Wave B (RW-31 — DA-04/05/06/17): the Share Studio and the
 * two cards it composes. ONE modal sheet in the app's design
 * language, in three movements — the card (Morning Poster and Kit
 * Card as LIVE PREVIEW thumbnails of the actual export renders,
 * poster pre-selected, with the three-way size control), the
 * words (the four filled captions as selectable cards, Edit opens
 * the chosen one as editable text), and the door (one pine Share
 * button → render-and-verify → the system's own share sheet via
 * [ShareArtifacts]).
 *
 * The cards in this file are the ONE composition: the Studio's
 * previews and the verified export are the same composables,
 * rendered offscreen at exact export pixels by
 * [ShareExportRenderer] and inspected by [ShareRasterVerifier]
 * before any share can fire (§7). The gen-5 preview-capture flow
 * (graphics-layer capture of an on-screen preview) is retired.
 *
 * Gen-5 resilience behaviours are preserved: a labelled progress
 * pill (never a blank wait) while a render is in flight, a 20 s
 * render timeout landing on an honest failure surface, a failed
 * input holding the share in a labelled error state with retry /
 * removal, and Share enabled only on a settled, verified render.
 */

/**
 * GEN-5 (RW-29 cream map): the Kit Card is a FIXED cream artifact
 * — the design draws it in the light system with no dark variant,
 * because the card leaves the app as an image. (Moved here from
 * the collection screen with the card itself; values unchanged.)
 */
internal object KitCardPalette {
    val Sheet = Color(0xFFFDFBF6)
    val Ink = Color(0xFF17191C)
    val Muted = Color(0xFF5F5B52)
    val Pine = Color(0xFF175E4C)
    val TileBorder = Color(0xFF17191C).copy(alpha = 0.10f)

    /** Warm Charcoal — the design's dark tile ground (the app
     * theme's dark BgCard), so a garment render produced on a dark
     * ground reads as an intentional tile, never a broken black
     * square (§4.5). */
    val TileGround = Color(0xFF2A251E)
}

/** One Kit Card tile: a garment in wearing order. */
data class KitCardTile(
    val key: String,
    val name: String,
    /** The garment's one-line note (the engine item's description). */
    val note: String,
    /** The decoded garment render; null = the empty-tile state. */
    val bitmap: Bitmap?,
    /** True when the garment has a real shop link (its catalogue
     * render exists) — the per-tile Shop › rides only then. */
    val hasShopLink: Boolean
)

/**
 * Load an entry's hero for DISPLAY surfaces (collection covers)
 * through the export loader. Covers are display, not export: no
 * face detection runs here — the anchor is the stored one or the
 * default composition point.
 */
@Composable
fun rememberHeroBitmap(ref: String?): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(ref) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref) {
        bitmap = ShareExportRenderer.loadHeroBitmap(context, ref)
    }
    return bitmap
}

// MARK: - Gen-5 resilience surfaces (moved with the Studio)

/**
 * The working state for a rendering share artifact — a labeled
 * pill (spinner + what is rendering), shown OVER the artifact
 * while its render is genuinely in progress. State-driven: it
 * exists exactly while the render has not settled, and it never
 * moves the artifact beneath it.
 */
@Composable
internal fun ShareRenderProgressPill(label: String, modifier: Modifier = Modifier) {
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
            CircularProgressIndicator(
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
 * The FAILED render's surface: what failed, in words, with its
 * own way back — never an endless progress state or a silently
 * degraded card presented as finished.
 */
@Composable
internal fun ShareRenderErrorNotice(
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
            TextButton(onClick = onClose) { Text(com.runwear.app.i18n.AppStrings.close()) }
        }
    }
}

// MARK: - Morning Poster (§4.2 — DA-06)

/**
 * The Morning Poster — ONE composition rendered onto the three
 * canvases, sized to the format's canvas points: the photograph
 * (already anchor-cropped for this canvas by the caller, drawn
 * FillBounds) fills the frame; the settled top lockup (RunWear
 * script + #RunAnyWear directly beneath, warm white over the top
 * scrim); the informative block in exactly four tiers — (1) the
 * feels-like numeral with "feels like" small beside it, (2)
 * place · moment, (3) the outfit in words, promoted as the clear
 * second read, (4) the quiet conditions micro-line, which yields
 * first under pressure; and the foot as ONE lockup row (Wear
 * I've Run script + runwear.ai). The why-line never appears here
 * — it lives in the captions. On Stories the lockup sits below
 * the top safe zone and the footer folds into the block as its
 * last line, the whole stack ending above the bottom safe line.
 *
 * Wind handling mirrors the iOS lane exactly: the Tier 4 "Actual"
 * figure is the display-unit value (carried by [input]); the wind
 * figure is the record's canonical mph, rounded, labelled "mph"
 * by [SharePosterModel.tier4Line] with NO unit conversion — and a
 * legacy record without a wind figure carries a micro-line
 * without one, never a fabrication.
 */
@Composable
fun MorningPoster(
    entry: WearIveRunDayEntry,
    input: ShareCaptionInput,
    posterMoment: String,
    format: ShareExportFormat,
    /** The hero, ALREADY cropped for this canvas via
     * [ShareAnchorCrop.cropBitmap]; null = the designed no-photo
     * gradient scene (a day with no photo is not a failure). */
    heroBitmap: Bitmap?,
    /** The anchor the crop was computed from. Carried for the
     * composition's contract completeness; the crop itself is the
     * caller's, so the composable only draws. */
    faceAnchor: ShareFaceAnchor,
    modifier: Modifier = Modifier
) {
    val canvas = format.canvasPointSize
    val tier3 = SharePosterModel.tier3Line(input.kitNames)
    val tier4: String? = if (
        SharePosterModel.showsMicroLine(format, tier3)
    ) {
        val wind = entry.windMph
        if (wind != null) {
            SharePosterModel.tier4Line(
                condition = entry.conditionLabel,
                actualDegrees = input.actual,
                windMph = wind.roundToInt()
            )
        } else {
            "${entry.conditionLabel} · Actual ${input.actual}°"
        }
    } else {
        null
    }
    val folds = SharePosterModel.footerFoldsIntoBlock(format)

    Box(
        modifier = modifier.size(canvas.width.dp, canvas.height.dp)
            .background(Color(0xFF16130F))
    ) {
        if (heroBitmap != null) {
            Image(
                bitmap = heroBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // The designed no-photo scene: the temperature-tinted
            // gradient the home hero falls back to, over the dark
            // ground — a photo-less day still composes a poster.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            getTemperatureFallbackGradient(entry.feelsLikeF),
                            Color(0xFF16130F)
                        )
                    )
                )
            )
        }

        // The settled top scrim: charcoal gradient, 0.62 at the
        // top edge, gone by ~31% of the canvas height — before it
        // reaches the runner.
        Box(
            modifier = Modifier.fillMaxWidth()
                .height((canvas.height * 0.31).dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PosterScrim.copy(alpha = 0.62f),
                            PosterScrim.copy(alpha = 0.31f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Lockup: script brand + hashtag directly under it.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = SharePosterModel.lockupTopInsetPoints(format).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RunWear",
                    fontFamily = GreatVibesFontFamily,
                    fontSize = 37.sp,
                    color = PhotoInk
                )
                Text(
                    text = "#RunAnyWear",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PhotoInk
                )
            }

            Spacer(Modifier.weight(1f))

            // The informative block, over the bottom gradient.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = 16.dp,
                        bottom = if (folds) {
                            SharePosterModel.blockBottomInsetPoints(format).dp
                        } else {
                            6.dp
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tier 1 — the numeral.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${input.feels}°",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.W800,
                        color = Color.White,
                        modifier = Modifier.alignByBaseline()
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "feels like",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.alignByBaseline()
                    )
                }
                // Tier 2 — place · moment.
                Text(
                    text = SharePosterModel.tier2Line(input.place, posterMoment),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                // Tier 3 — the outfit in words, promoted.
                if (tier3.isNotEmpty()) {
                    Text(
                        text = tier3,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                // Tier 4 — the quiet conditions micro-line.
                if (tier4 != null) {
                    Text(
                        text = tier4,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
                // Stories: the footer folds in as the block's
                // last line (§6).
                if (folds) {
                    PosterFooterRow(modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (!folds) {
                PosterFooterRow(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(
                            bottom = SharePosterModel.blockBottomInsetPoints(format).dp
                        )
                )
            }
        }
    }
}

/** The poster's foot as ONE lockup row: the Wear I've Run script
 * and runwear.ai composed together, centred — never two elements
 * in opposite corners. */
@Composable
private fun PosterFooterRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = WearIveRunNarrationFormatter.WORDMARK,
            fontFamily = GreatVibesFontFamily,
            fontSize = 21.sp,
            color = PhotoInk
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "·",
            fontSize = 12.sp,
            color = PhotoInk.copy(alpha = 0.55f)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "runwear.ai",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PhotoInk
        )
    }
}

// MARK: - Kit Card (§4.5 — DA-17)

/**
 * The Kit Card — the outfit in the app's own card language, on
 * its fixed cream ground in BOTH appearances (never wrapped in
 * the app theme's dark surface): header (Wear I've Run script
 * small, place as title, the weather line), then a two-column
 * grid of LARGE squares on the Warm Charcoal ground in wearing
 * order — the garment name under every square in every format,
 * its one-line note under the name where the format has room,
 * and a per-tile Shop › printed on the card only when the garment
 * has a real shop link. Foot: #RunAnyWear in pine, runwear.ai
 * beside it, and the Associates disclosure in microcopy. No
 * prices anywhere, and no card-level "Shop this kit →" — the
 * links are per tile (§3, owner amendments Nov 5, 2026).
 */
@Composable
fun KitCard(
    entry: WearIveRunDayEntry,
    input: ShareCaptionInput,
    posterMoment: String,
    format: ShareExportFormat,
    items: List<KitCardTile>,
    modifier: Modifier = Modifier
) {
    val canvas = format.canvasPointSize
    val capped = items.take(ShareKitCardModel.maxItems(format))
    val layout = ShareKitCardModel.gridLayout(format, capped.size)

    Column(
        modifier = modifier.size(canvas.width.dp, canvas.height.dp)
            .background(KitCardPalette.Sheet)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = WearIveRunNarrationFormatter.WORDMARK,
                fontFamily = GreatVibesFontFamily,
                fontSize = 24.sp,
                color = KitCardPalette.Ink
            )
            Text(
                text = input.place,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = KitCardPalette.Ink
            )
            Text(
                text = ShareKitCardModel.weatherLine(
                    feelsDegrees = input.feels,
                    condition = entry.conditionLabel,
                    place = input.place,
                    posterMoment = posterMoment
                ),
                fontSize = 13.sp,
                color = KitCardPalette.Muted
            )
        }

        Spacer(Modifier.height(10.dp))

        // The grid of squares, in wearing order: two FIXED
        // columns (the model's columnWidth — the same geometry
        // ShareKitCardModel.tileSamplePoints samples, and the
        // iOS view's two flexible grid columns). Each tile is
        // centred WITHIN its column; a lone last-row tile
        // stays in the first column. (Centring each row's pack
        // across the canvas instead — the earlier arrangement —
        // moved every tile off the model's centres, a lone
        // last-row tile by half the canvas, and the verifier's
        // tile sample landed on bare cream sheet.)
        val columnWidth = ShareKitCardModel.columnWidth(canvas.width)
        Column(verticalArrangement = Arrangement.spacedBy(ShareKitCardModel.KIT_ROW_SPACING.dp)) {
            capped.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { tile ->
                        Column(
                            modifier = Modifier.width(columnWidth.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(layout.tileSide.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(KitCardPalette.TileGround)
                                    .border(
                                        1.dp, KitCardPalette.TileBorder,
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tile.bitmap != null) {
                                    Image(
                                        bitmap = tile.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }
                            }
                            Text(
                                text = tile.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KitCardPalette.Ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            if (layout.showsNotes && tile.note.isNotBlank()) {
                                Text(
                                    text = tile.note,
                                    fontSize = 12.sp,
                                    color = KitCardPalette.Muted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            if (tile.hasShopLink) {
                                Text(
                                    text = "Shop ›",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KitCardPalette.Pine,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // The foot.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "#RunAnyWear",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KitCardPalette.Pine
            )
            Text(
                text = "runwear.ai",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = KitCardPalette.Muted
            )
        }
        Text(
            text = ShareKitCardModel.DISCLOSURE,
            fontSize = 9.5.sp,
            color = KitCardPalette.Muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// MARK: - Share Studio (§4.1 — DA-04)

private sealed interface HeroLoadState {
    data object Loading : HeroLoadState
    data object NoPhoto : HeroLoadState
    data class Loaded(val bitmap: Bitmap) : HeroLoadState
    data object Failed : HeroLoadState
}

private data class CardRender(
    val bitmap: Bitmap,
    val metadata: ShareRenderMetadata,
    val verdict: ShareRasterResult
)

/**
 * The Share Studio — ONE M3 modal sheet on the one full anchor,
 * opened from every share door in the collection (card share
 * icons, the pinned TODAY card, chapter covers, and — through the
 * collection — the home mini bar). See the file header for the
 * three movements and the verify gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStudioSheet(
    entry: WearIveRunDayEntry,
    gender: GenderPreference,
    temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val input = remember(entry, temperatureUnit) {
        ShareCaptionInput.forEntry(entry, temperatureUnit, today, now)
    }
    val forms = remember(entry) {
        val settledAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(entry.settledAtMillis), ZoneId.systemDefault()
        )
        ShareMomentResolver.forms(
            reading = ShareMomentResolver.readingFor(entry),
            dayStart = entry.date,
            lastSavedAt = settledAt,
            now = now,
            today = today
        )
    }
    val posterMoment = forms.poster

    var kind by remember { mutableStateOf(ShareArtifactKind.MORNING_POSTER) }
    var format by remember { mutableStateOf(ShareExportFormat.FEED) }
    var excludedKeys by remember(entry) { mutableStateOf<Set<String>>(emptySet()) }
    var renderNonce by remember { mutableStateOf(0) }
    var shareFailed by remember { mutableStateOf(false) }

    // — Hero input —
    var heroState by remember(entry) { mutableStateOf<HeroLoadState>(HeroLoadState.Loading) }
    LaunchedEffect(entry) {
        heroState = when {
            entry.heroRef.isNullOrBlank() -> HeroLoadState.NoPhoto
            else -> ShareExportRenderer.loadHeroBitmap(context, entry.heroRef)
                ?.let { HeroLoadState.Loaded(it) } ?: HeroLoadState.Failed
        }
    }
    val heroBitmap = (heroState as? HeroLoadState.Loaded)?.bitmap
    val heroFailed = heroState == HeroLoadState.Failed
    val heroSettled = heroState != HeroLoadState.Loading

    // — Face anchor (stored → detection → default), resolved in
    // the poster render coroutine and kept for every format. —
    var faceAnchor by remember(entry) {
        mutableStateOf(entry.faceAnchor)
    }

    // — Garment inputs —
    val kitSkeleton = remember(entry) {
        ShareExportRenderer.buildKitTiles(entry, emptyMap(), emptySet())
    }
    var garmentBitmaps by remember(entry) {
        mutableStateOf<Map<String, Bitmap?>>(emptyMap())
    }
    var garmentsSettled by remember(entry) { mutableStateOf(false) }
    var garmentRetryNonce by remember { mutableStateOf(0) }
    LaunchedEffect(entry, gender, garmentRetryNonce) {
        garmentsSettled = false
        val loaded = LinkedHashMap<String, Bitmap?>()
        for (tile in kitSkeleton) {
            val engineItem = ClothingItem.entries.firstOrNull { it.key == tile.key }
                ?: clothingItemForDisplayName(tile.name)
            loaded[tile.key] = engineItem?.let {
                ShareExportRenderer.loadGarmentBitmap(context, it, gender)
            }
        }
        garmentBitmaps = loaded
        garmentsSettled = true
    }
    /** Garments that SHOULD carry a render (a catalogue render
     * exists for them) but whose load failed — the §7.2 hold. */
    val missingGarments: List<KitCardTile> = if (garmentsSettled) {
        kitSkeleton.filter { tile ->
            tile.key !in excludedKeys && tile.hasShopLink &&
                garmentBitmaps[tile.key] == null
        }
    } else {
        emptyList()
    }

    // — Export renders (both kinds, at the selected format) —
    var posterRender by remember(entry) { mutableStateOf<CardRender?>(null) }
    var posterRendering by remember(entry) { mutableStateOf(false) }
    var posterRenderFailed by remember(entry) { mutableStateOf(false) }
    var posterRenderTimedOut by remember(entry) { mutableStateOf(false) }
    LaunchedEffect(entry, format, heroSettled, renderNonce) {
        if (!heroSettled) return@LaunchedEffect
        if (heroFailed) {
            // A hero that cannot load FAILS the poster (§7.2) —
            // never a photo-less dark rectangle in its place.
            posterRender = null
            posterRenderFailed = true
            return@LaunchedEffect
        }
        posterRendering = true
        posterRenderFailed = false
        posterRenderTimedOut = false
        val anchor = faceAnchor
            ?: heroBitmap?.let { detectFaceAnchor(it) }
            ?: ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
        if (faceAnchor == null) faceAnchor = anchor
        val outcome = withTimeoutOrNull(20_000) {
            try {
                Result.success(
                    ShareExportRenderer.renderShareArtifact(
                        context, ShareArtifactKind.MORNING_POSTER, format,
                        entry, input, posterMoment, heroBitmap,
                        garmentBitmaps, anchor
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        posterRendering = false
        when {
            outcome == null -> {
                posterRender = null
                posterRenderFailed = true
                posterRenderTimedOut = true
            }
            outcome.isFailure -> {
                posterRender = null
                posterRenderFailed = true
            }
            else -> {
                val (bitmap, metadata) = outcome.getOrThrow()
                val points = metadata.faceSamplePoint
                    ?.let { listOf(ShareLabeledSamplePoint(it, "face")) }
                    ?: emptyList()
                posterRender = CardRender(
                    bitmap, metadata,
                    ShareRasterVerifier.verify(bitmap, format.exportPixelSize, points)
                )
            }
        }
    }

    var kitRender by remember(entry) { mutableStateOf<CardRender?>(null) }
    var kitRendering by remember(entry) { mutableStateOf(false) }
    var kitRenderFailed by remember(entry) { mutableStateOf(false) }
    var kitRenderTimedOut by remember(entry) { mutableStateOf(false) }
    LaunchedEffect(entry, format, excludedKeys, garmentsSettled, renderNonce) {
        if (!garmentsSettled) return@LaunchedEffect
        kitRendering = true
        kitRenderFailed = false
        kitRenderTimedOut = false
        // The card renders WITHOUT the removed garments.
        val included = kitSkeleton.filter { it.key !in excludedKeys }
        val entryForRender = entry.copy(
            items = included.map { it.name },
            itemKeys = if (entry.itemKeys.size == entry.items.size) {
                included.map { it.key }
            } else {
                emptyList()
            }
        )
        val anchor = faceAnchor ?: ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
        val outcome = withTimeoutOrNull(20_000) {
            try {
                Result.success(
                    ShareExportRenderer.renderShareArtifact(
                        context, ShareArtifactKind.KIT_CARD, format,
                        entryForRender, input, posterMoment, heroBitmap,
                        garmentBitmaps, anchor
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        kitRendering = false
        when {
            outcome == null -> {
                kitRender = null
                kitRenderFailed = true
                kitRenderTimedOut = true
            }
            outcome.isFailure -> {
                kitRender = null
                kitRenderFailed = true
            }
            else -> {
                val (bitmap, metadata) = outcome.getOrThrow()
                val points = metadata.kitTileSamplePoints.mapIndexed { index, point ->
                    ShareLabeledSamplePoint(point, "kit tile ${index + 1}")
                }
                kitRender = CardRender(
                    bitmap, metadata,
                    ShareRasterVerifier.verify(bitmap, format.exportPixelSize, points)
                )
            }
        }
    }

    // — Caption state —
    val captions = remember(input) { ShareCaptionComposer.orderedCaptions(input) }
    var selectedTemplate by remember(input) {
        mutableStateOf(captions.first().first)
    }
    var editedCaption by remember(input) { mutableStateOf<String?>(null) }
    var editingCaption by remember { mutableStateOf(false) }
    val currentCaption: String = editedCaption
        ?: captions.firstOrNull { it.first == selectedTemplate }?.second
        ?: captions.first().second

    // — The selected card's gate —
    val currentRender = if (kind == ShareArtifactKind.MORNING_POSTER) {
        posterRender
    } else {
        kitRender
    }
    val currentRendering = if (kind == ShareArtifactKind.MORNING_POSTER) {
        posterRendering
    } else {
        kitRendering
    }
    val currentRenderFailed = if (kind == ShareArtifactKind.MORNING_POSTER) {
        posterRenderFailed
    } else {
        kitRenderFailed
    }
    val currentRenderTimedOut = if (kind == ShareArtifactKind.MORNING_POSTER) {
        posterRenderTimedOut
    } else {
        kitRenderTimedOut
    }
    val verifyFailed = currentRender != null &&
        currentRender.verdict is ShareRasterResult.Invalid
    val shareHeld = currentRender == null ||
        currentRender.verdict !is ShareRasterResult.Valid ||
        (kind == ShareArtifactKind.KIT_CARD && missingGarments.isNotEmpty()) ||
        (kind == ShareArtifactKind.MORNING_POSTER && heroFailed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Share this day",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Choose the card for ${entry.place} — ${dayTitle(entry, today)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // First movement — the card: live preview thumbnails
            // of the ACTUAL export renders for this day.
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioThumbnailChoice(
                        label = "Morning Poster",
                        render = posterRender,
                        rendering = posterRendering,
                        failed = posterRenderFailed,
                        selected = kind == ShareArtifactKind.MORNING_POSTER,
                        modifier = Modifier.weight(1f),
                        onClick = { kind = ShareArtifactKind.MORNING_POSTER }
                    )
                    StudioThumbnailChoice(
                        label = "Kit Card",
                        render = kitRender,
                        rendering = kitRendering,
                        failed = kitRenderFailed,
                        selected = kind == ShareArtifactKind.KIT_CARD,
                        modifier = Modifier.weight(1f),
                        onClick = { kind = ShareArtifactKind.KIT_CARD }
                    )
                }
                if (posterRendering || kitRendering) {
                    ShareRenderProgressPill(
                        label = "Rendering previews…",
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // The three-way size control — one canvas for both
            // cards (§6).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareExportFormat.entries.forEach { option ->
                    FilterChip(
                        selected = format == option,
                        onClick = { format = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // The selected card's full preview — the rendered
            // export bitmap itself, scaled.
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    currentRender != null -> Image(
                        bitmap = currentRender.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(format.aspect.toFloat())
                            .clip(RoundedCornerShape(16.dp))
                    )
                    currentRendering -> Box(
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(format.aspect.toFloat())
                            .clip(RoundedCornerShape(16.dp))
                            .background(RunWearColors.BgCard)
                    )
                    else -> Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RunWearColors.BgCard)
                    )
                }
                if (shareFailed) {
                    ShareRenderErrorNotice(
                        label = "Couldn't share this card — try again.",
                        onClose = onDismiss,
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    )
                } else if (currentRenderFailed) {
                    ShareRenderErrorNotice(
                        label = if (currentRenderTimedOut) {
                            "Couldn't render this card — it took too long."
                        } else if (kind == ShareArtifactKind.MORNING_POSTER && heroFailed) {
                            "Couldn't render this card — its photo couldn't be loaded."
                        } else {
                            "Couldn't render this card — try again."
                        },
                        onClose = onDismiss,
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    )
                } else if (currentRendering) {
                    ShareRenderProgressPill(
                        label = "Rendering ${
                            if (kind == ShareArtifactKind.MORNING_POSTER) {
                                "Morning Poster"
                            } else {
                                "Kit Card"
                            }
                        }…",
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    )
                }
            }

            // Hero failure: a labelled error row; the poster's
            // share is held above until the photo loads.
            if (heroFailed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "This day's photo couldn't load — the Morning Poster can't be shared until it does.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RunWearColors.TextPrimary
                )
            }

            // Kit Card input gating (§7.2): a garment whose render
            // cannot load holds the share — retry, or remove the
            // garment from the card. Live rows carry the per-tile
            // Shop › through the app's affiliate machinery.
            if (kind == ShareArtifactKind.KIT_CARD) {
                if (missingGarments.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    missingGarments.forEach { garment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${garment.name} couldn't load.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RunWearColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { excludedKeys = excludedKeys + garment.key }
                            ) { Text(com.runwear.app.i18n.AppStrings.removeFromCard()) }
                        }
                    }
                    TextButton(onClick = { garmentRetryNonce++ }) {
                        Text(com.runwear.app.i18n.AppStrings.tryAgain())
                    }
                }
                val shoppable = kitSkeleton.filter {
                    it.key !in excludedKeys && it.hasShopLink
                }
                if (shoppable.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    shoppable.forEach { tile ->
                        val engineItem = ClothingItem.entries
                            .firstOrNull { it.key == tile.key }
                            ?: clothingItemForDisplayName(tile.name)
                        if (engineItem != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RunWearColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        val url = buildAmazonSearchUrl(engineItem, gender)
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    }
                                ) { Text(com.runwear.app.i18n.AppStrings.shopArrow()) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Second movement — the words.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Caption",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RunWearColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { editingCaption = !editingCaption }) {
                    Text(com.runwear.app.i18n.AppStrings.edit())
                }
            }
            captions.forEach { (template, text) ->
                val isActive = template == selectedTemplate
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RunWearColors.BgCard)
                        .border(
                            2.dp,
                            if (isActive) RunWearColors.Primary else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            selectedTemplate = template
                            editedCaption = null
                        }
                        .padding(12.dp)
                ) {
                    Text(
                        text = template.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RunWearColors.PineAccent
                    )
                    Text(
                        text = if (isActive) currentCaption else text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RunWearColors.TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (editingCaption) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentCaption,
                    onValueChange = { editedCaption = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }

            // The verify gate's failure line (§7.3): the system
            // sheet never opened; the retry force-renders.
            if (verifyFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "This card didn't render cleanly — try again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RunWearColors.TextPrimary
                )
                TextButton(
                    onClick = { renderNonce++ }
                ) { Text(com.runwear.app.i18n.AppStrings.tryAgain()) }
            }

            Spacer(Modifier.height(16.dp))

            // Third movement — the door: ONE pine Share button,
            // enabled only on a settled, VERIFIED render.
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(com.runwear.app.i18n.AppStrings.close()) }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !shareHeld,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitCardPalette.Pine,
                        contentColor = PhotoInk
                    ),
                    onClick = {
                        val render = currentRender ?: return@Button
                        runCatching {
                            ShareArtifacts.share(
                                context, render.bitmap, kind, currentCaption
                            )
                        }.onSuccess {
                            onDismiss()
                        }.onFailure {
                            shareFailed = true
                        }
                    }
                ) {
                    Text(com.runwear.app.i18n.AppStrings.share())
                }
            }
        }
    }
}

/** One Studio thumbnail choice: the real export render, scaled —
 * not a bare text row. Tapping selects that card. */
@Composable
private fun StudioThumbnailChoice(
    label: String,
    render: CardRender?,
    rendering: Boolean,
    failed: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RunWearColors.BgCard)
            .border(
                2.dp,
                if (selected) RunWearColors.Primary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .semantics { contentDescription = "$label thumbnail" }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(170.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RunWearColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            when {
                render != null -> Image(
                    bitmap = render.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                failed -> Text(
                    text = "Your $label couldn't be made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
                else -> Text(
                    text = "Making your $label…",
                    style = MaterialTheme.typography.bodySmall,
                    color = RunWearColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = RunWearColors.TextPrimary
        )
    }
}
