package com.runwear.app.weariverun

import android.content.Context
import android.graphics.Bitmap
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * RW-168 (Nov 11, 2026): the direct save behind the home share
 * menu's "Save to photos" — today's card, in the currently
 * selected kind + size format, rendered and verified through the
 * Share Studio's OWN pipeline primitives (caption input, moment
 * forms, the RW-152/RW-166 hero resolution, garment loads, the
 * offscreen export renderer, the raster verifier) and then
 * written to the photo roll via [ShareArtifacts.saveToPhotos].
 * No sheet round-trip, and no second render path: every step
 * mirrors ShareStudioSheet's settled behaviour, including its
 * holds — a hero that cannot load fails the Postcard (never a
 * photo-less rectangle in its place), a Kit Card with a missing
 * garment render is held, and a raster that fails verification
 * is never saved. Any hold or failure returns false and the
 * caller says so; a card is only ever reported saved when it
 * actually was.
 */
suspend fun saveTodaysCardToPhotos(
    context: Context,
    entry: WearIveRunDayEntry,
    kind: ShareArtifactKind,
    format: ShareExportFormat,
    gender: GenderPreference,
    temperatureUnit: TemperatureUnit,
    heroResign: (suspend (String) -> String?)? = null,
    heroRecovery: HeroRecoverySeam? = null,
    onHeroRecovered: (String) -> Unit = {}
): Boolean {
    val bitmap = renderVerifiedShareCard(
        context, entry, kind, format, gender, temperatureUnit,
        heroResign, heroRecovery, onHeroRecovered
    ) ?: return false
    return ShareArtifacts.saveToPhotos(context, bitmap, kind)
}

/**
 * Render one verified share card headlessly, mirroring
 * ShareStudioSheet's pipeline step for step. Returns the
 * verified raster, or null when the sheet would have held its
 * Share door for this card.
 */
suspend fun renderVerifiedShareCard(
    context: Context,
    entry: WearIveRunDayEntry,
    kind: ShareArtifactKind,
    format: ShareExportFormat,
    gender: GenderPreference,
    temperatureUnit: TemperatureUnit,
    heroResign: (suspend (String) -> String?)? = null,
    heroRecovery: HeroRecoverySeam? = null,
    onHeroRecovered: (String) -> Unit = {}
): Bitmap? {
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val input = ShareCaptionInput.forEntry(entry, temperatureUnit, today, now)
    val settledAt = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(entry.settledAtMillis), ZoneId.systemDefault()
    )
    val forms = ShareMomentResolver.forms(
        reading = ShareMomentResolver.readingFor(entry),
        dayStart = entry.date,
        lastSavedAt = settledAt,
        now = now,
        today = today
    )
    val posterMoment = forms.poster

    // — Hero resolution: the suspend mirror of
    // rememberHeroResolution (RW-152 §3 re-sign; RW-166 bank
    // recovery for the empty-reference entry, healed once
    // through the host). —
    val stored = HeroRefResolver.storedRef(entry)
    val path = HeroRefResolver.storagePathOf(stored)
    val resigned = if (path != null && heroResign != null) {
        heroResign(path)
    } else {
        null
    }
    var resolvedUrl = HeroRefResolver.resolveDisplay(stored, resigned)
    var recoveredRef = false
    if (stored == null && heroRecovery != null) {
        val bank = runCatching { heroRecovery.bankPaths() }.getOrNull()
        val recoveredPath = bank?.let {
            HeroRecovery.recoveredDurableReference(
                entry, heroRecovery.genderSegment, it
            )
        }
        if (recoveredPath != null) {
            onHeroRecovered(recoveredPath)
            recoveredRef = true
            resolvedUrl = if (heroResign != null) {
                heroResign(recoveredPath)
            } else {
                null
            }
        }
    }
    val heroBitmap = resolvedUrl?.let {
        ShareExportRenderer.loadHeroBitmap(context, it)
    }
    // The sheet's hero gate, settled form: a referenced hero
    // (the entry's own, or a recovered one) that cannot load has
    // FAILED; no reference at all is the designed no-photo card.
    val heroFailed =
        (HeroRefResolver.hasLoadableReference(entry) || recoveredRef) &&
            heroBitmap == null
    if (kind == ShareArtifactKind.MORNING_POSTER && heroFailed) return null

    // — Garment inputs (the Kit Card's tiles; loaded for both
    // kinds exactly as the sheet loads them). —
    val kitSkeleton = ShareExportRenderer.buildKitTiles(entry, emptyMap(), emptySet())
    val garmentBitmaps = LinkedHashMap<String, Bitmap?>()
    for (tile in kitSkeleton) {
        val engineItem = ClothingItem.entries.firstOrNull { it.key == tile.key }
            ?: clothingItemForDisplayName(tile.name)
        garmentBitmaps[tile.key] = engineItem?.let {
            ShareExportRenderer.loadGarmentBitmap(context, it, gender)
        }
    }
    if (kind == ShareArtifactKind.KIT_CARD) {
        // The §7.2 hold: a garment that should carry a render but
        // whose load failed holds the card.
        val missing = kitSkeleton.filter { tile ->
            tile.hasShopLink && garmentBitmaps[tile.key] == null
        }
        if (missing.isNotEmpty()) return null
    }

    val anchor = entry.faceAnchor
        ?: heroBitmap?.let { detectFaceAnchor(it) }
        ?: ShareFaceAnchor.DEFAULT_UPPER_MIDDLE

    val entryForRender = if (kind == ShareArtifactKind.KIT_CARD) {
        entry.copy(
            items = kitSkeleton.map { it.name },
            itemKeys = if (entry.itemKeys.size == entry.items.size) {
                kitSkeleton.map { it.key }
            } else {
                emptyList()
            }
        )
    } else {
        entry
    }

    val outcome = withTimeoutOrNull(20_000) {
        try {
            Result.success(
                ShareExportRenderer.renderShareArtifact(
                    context, kind, format, entryForRender, input,
                    posterMoment, heroBitmap, garmentBitmaps, anchor
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    } ?: return null
    val (bitmap, metadata) = outcome.getOrElse { return null }
    val points = when (kind) {
        ShareArtifactKind.MORNING_POSTER -> metadata.faceSamplePoint
            ?.let { listOf(ShareLabeledSamplePoint(it, "face")) }
            ?: emptyList()
        ShareArtifactKind.KIT_CARD -> metadata.kitTileSamplePoints
            .mapIndexed { index, point ->
                ShareLabeledSamplePoint(point, "kit tile ${index + 1}")
            }
    }
    val verdict = ShareRasterVerifier.verify(bitmap, format.exportPixelSize, points)
    return if (verdict is ShareRasterResult.Valid) bitmap else null
}
