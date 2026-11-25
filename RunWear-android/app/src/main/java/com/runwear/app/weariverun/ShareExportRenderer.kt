package com.runwear.app.weariverun

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.service.ClothingThumbnailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * GEN-6 Wave B (RW-31 §6/§7.1 — DA-16): the export is RENDERED at
 * exact export pixels from the same composition the Share Studio
 * previews — never a capture of an on-screen preview at whatever
 * size the sheet happened to be (the gen-5 graphics-layer capture
 * is retired from the share path).
 *
 * The render runs on a [ComposeView] carrying manually installed
 * lifecycle / saved-state owners and a render-owned [Recomposer]
 * installed as the parent composition context before content is
 * set (a view outside the app's own composition has no
 * windowRecomposer to resolve). For the render's duration the
 * view is ATTACHED, invisibly (alpha 0), to the host activity's
 * decor view: Compose UI defers a ComposeView's actual content
 * until its ViewTreeOwners become available, and owners are
 * only assigned at window attach (WrappedComposition gates
 * setContent on setOnViewTreeOwnersAvailable, which an
 * unattached view never receives) — a never-attached view
 * composes an EMPTY root and draws a transparent raster, which
 * the raster verifier then correctly rejects as degenerate.
 * The view is detached and its composition disposed before the
 * render returns. The recomposer runs on a
 * render-owned [BroadcastFrameClock] that the settle loop sends
 * frames to explicitly — a manually created recomposer has no
 * ambient frame clock to borrow (runRecomposeAndApplyChanges
 * throws without a MonotonicFrameClock in its coroutine
 * context), and a static offscreen render should drive its own
 * frames deterministically rather than wait on vsync. Composed
 * at the format's
 * canvas point size under a density of
 * exactly the format's render scale (3×), then measured and laid
 * out at the exact export pixels and drawn into a bitmap of
 * exactly [ShareExportFormat.exportPixelSize]. This is the one
 * production render path — the render tests call this function
 * itself, under Robolectric native graphics, with an activity
 * context.
 */

/** What a finished render publishes for the verify gate: where
 * the raster must be sampled (the face anchor's canvas position,
 * each Kit Card tile's centre), as canvas fractions. */
data class ShareRenderMetadata(
    val faceSamplePoint: ShareSamplePoint?,
    val kitTileSamplePoints: List<ShareSamplePoint>
)

/**
 * Crop [bitmap] to the anchor-computed window for [aspect] (the
 * export path's hero preparation, §4.3): the returned bitmap is
 * what the poster composable draws FillBounds. Returns the input
 * when cropping is impossible (empty window), so the poster never
 * loses its photo to a crop failure.
 */
fun ShareAnchorCrop.cropBitmap(
    bitmap: Bitmap,
    anchor: ShareFaceAnchor,
    aspect: Double
): Bitmap {
    val crop = cropRect(bitmap.width, bitmap.height, anchor, aspect)
    val x = crop.x.roundToInt().coerceIn(0, maxOf(0, bitmap.width - 1))
    val y = crop.y.roundToInt().coerceIn(0, maxOf(0, bitmap.height - 1))
    val w = crop.width.roundToInt().coerceIn(1, bitmap.width - x)
    val h = crop.height.roundToInt().coerceIn(1, bitmap.height - y)
    if (w <= 0 || h <= 0) return bitmap
    if (x == 0 && y == 0 && w == bitmap.width && h == bitmap.height) return bitmap
    return try {
        Bitmap.createBitmap(bitmap, x, y, w, h)
    } catch (e: Exception) {
        bitmap
    }
}

/** Lifecycle + saved-state owners for an unattached ComposeView. */
private class ShareRenderOwners : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}

object ShareExportRenderer {

    /**
     * Render one share artifact OFFSCREEN at the format's exact
     * export pixels. [heroBitmap] is the day's decoded hero (null
     * = the designed no-photo poster, NOT a failure);
     * [garmentBitmaps] carries the Kit Card's decoded garment
     * renders by engine item key (a missing/null entry renders an
     * empty tile — the Studio's input gating holds the share, the
     * renderer still composes). Returns the raster plus the
     * verify gate's sample metadata.
     */
    suspend fun renderShareArtifact(
        context: Context,
        kind: ShareArtifactKind,
        format: ShareExportFormat,
        entry: WearIveRunDayEntry,
        input: ShareCaptionInput,
        posterMoment: String,
        heroBitmap: Bitmap?,
        garmentBitmaps: Map<String, Bitmap?>,
        faceAnchor: ShareFaceAnchor
    ): Pair<Bitmap, ShareRenderMetadata> {
        val pixelSize = format.exportPixelSize
        val croppedHero = heroBitmap?.let {
            ShareAnchorCrop.cropBitmap(it, faceAnchor, format.aspect)
        }
        val tiles = if (kind == ShareArtifactKind.KIT_CARD) {
            buildKitTiles(entry, garmentBitmaps, excludedKeys = emptySet())
                .take(ShareKitCardModel.maxItems(format))
        } else {
            emptyList()
        }
        val metadata = ShareRenderMetadata(
            faceSamplePoint = if (kind == ShareArtifactKind.MORNING_POSTER) {
                faceCanvasPoint(heroBitmap, faceAnchor, format)
            } else {
                null
            },
            kitTileSamplePoints = if (kind == ShareArtifactKind.KIT_CARD) {
                ShareKitCardModel.tileSamplePoints(format, tiles.size)
            } else {
                emptyList()
            }
        )

        val bitmap = withContext(Dispatchers.Main) {
            val owners = ShareRenderOwners()
            // One Recomposer owned by this render: the export
            // view is not part of the app's composition, so there
            // is no windowRecomposer for it to resolve (measure()
            // threw "Cannot locate windowRecomposer" before this).
            // Install it as the parent composition context BEFORE
            // setContent and drive it for the render's duration;
            // close it and cancel its job once the draw is done
            // so nothing leaks past the render.
            //
            // The recomposer also needs a MonotonicFrameClock:
            // runRecomposeAndApplyChanges reads it from its own
            // coroutine context and throws IllegalStateException
            // ("A MonotonicFrameClock is not available in this
            // CoroutineContext") when it is absent — which it is
            // whenever Dispatchers.Main does not happen to resolve
            // to Compose's AndroidUiDispatcher (the Robolectric
            // harness's main dispatcher carries no clock; relying
            // on the ambient dispatcher was the first fix round's
            // defect). So the render owns a BroadcastFrameClock,
            // hands the SAME instance to the Recomposer (its
            // effect context) and to the runner coroutine, and
            // the settle loop below sends frames to it explicitly.
            val frameClock = BroadcastFrameClock()
            val recomposer = Recomposer(Dispatchers.Main + frameClock)
            val recomposeJob = launch(frameClock) {
                recomposer.runRecomposeAndApplyChanges()
            }
            var composeView: ComposeView? = null
            try {
                val view = ComposeView(context)
                composeView = view
                view.setParentCompositionContext(recomposer)
                view.setViewTreeLifecycleOwner(owners)
                view.setViewTreeSavedStateRegistryOwner(owners)
                view.setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = format.renderScale.toFloat(),
                            fontScale = 1f
                        )
                    ) {
                        Box(
                            modifier = Modifier.size(
                                format.canvasPointSize.width.dp,
                                format.canvasPointSize.height.dp
                            )
                        ) {
                            when (kind) {
                                ShareArtifactKind.MORNING_POSTER -> MorningPoster(
                                    entry = entry,
                                    input = input,
                                    posterMoment = posterMoment,
                                    format = format,
                                    heroBitmap = croppedHero,
                                    faceAnchor = faceAnchor
                                )
                                ShareArtifactKind.KIT_CARD -> KitCard(
                                    entry = entry,
                                    input = input,
                                    posterMoment = posterMoment,
                                    format = format,
                                    items = tiles
                                )
                            }
                        }
                    }
                }
                // Attach the view to the host activity's window
                // for the render's duration — invisibly (alpha 0;
                // the manual draw below is into our own bitmap and
                // is not affected by the view's window alpha, which
                // only the window's own draw applies). This attach
                // is what the content was waiting for: Compose UI
                // gates a ComposeView's real setContent on its
                // ViewTreeOwners becoming available, and owners are
                // assigned only in AndroidComposeView's
                // onAttachedToWindow (found by walking up from the
                // view — the owners installed on THIS view above
                // are the ones it finds). Without an attach the
                // composition stays an empty root and the draw is a
                // transparent raster (the verifier's
                // DegenerateRaster(mean=0.0) verdict). When no
                // Activity hosts the render the view stays
                // unattached and the verifier — not a crash — is
                // what reports the empty render, as designed.
                val decor = context.findActivity()?.window?.decorView as? ViewGroup
                if (decor != null) {
                    view.alpha = 0f
                    decor.addView(
                        view,
                        FrameLayout.LayoutParams(pixelSize.width, pixelSize.height)
                    )
                }
                val widthSpec = View.MeasureSpec.makeMeasureSpec(
                    pixelSize.width, View.MeasureSpec.EXACTLY
                )
                val heightSpec = View.MeasureSpec.makeMeasureSpec(
                    pixelSize.height, View.MeasureSpec.EXACTLY
                )
                // Let the composition apply and settle over several
                // frame opportunities, measuring and laying out at
                // the exact export size each pass. Each pass first
                // SENDS a frame on the render's own clock — that
                // send is what wakes the recomposer to compose and
                // apply (a frame sent before it has registered an
                // awaiter is dropped, which the repeated passes
                // absorb) — then delays so the frame's work, posted
                // back to the main dispatcher, actually lands
                // before the measure/layout that follows it.
                var nextFrameTimeNanos = System.nanoTime()
                repeat(10) {
                    nextFrameTimeNanos += 16_666_667L
                    frameClock.sendFrame(nextFrameTimeNanos)
                    delay(50)
                    view.measure(widthSpec, heightSpec)
                    view.layout(0, 0, pixelSize.width, pixelSize.height)
                }
                nextFrameTimeNanos += 16_666_667L
                frameClock.sendFrame(nextFrameTimeNanos)
                delay(50)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, pixelSize.width, pixelSize.height)
                val out = Bitmap.createBitmap(
                    pixelSize.width, pixelSize.height, Bitmap.Config.ARGB_8888
                )
                // Warm draw, then the real one — the harness pattern:
                // the first draw can still carry a pending layout pass.
                view.draw(Canvas(out))
                val finished = Bitmap.createBitmap(
                    pixelSize.width, pixelSize.height, Bitmap.Config.ARGB_8888
                )
                view.draw(Canvas(finished))
                finished
            } finally {
                composeView?.let { view ->
                    runCatching { view.disposeComposition() }
                    (view.parent as? ViewGroup)?.removeView(view)
                }
                recomposer.close()
                recomposeJob.cancel()
            }
        }
        return bitmap to metadata
    }

    /** The face anchor's position on the composed canvas, as
     * fractions — the anchor centre mapped through the crop
     * window for this format. Null when there is no hero. */
    fun faceCanvasPoint(
        heroBitmap: Bitmap?,
        anchor: ShareFaceAnchor,
        format: ShareExportFormat
    ): ShareSamplePoint? {
        val hero = heroBitmap ?: return null
        if (hero.width <= 0 || hero.height <= 0) return null
        val crop = ShareAnchorCrop.cropRect(
            hero.width, hero.height, anchor, format.aspect
        )
        if (crop.width <= 0 || crop.height <= 0) return null
        return ShareSamplePoint(
            x = (anchor.centerX * hero.width - crop.x) / crop.width,
            y = (anchor.centerY * hero.height - crop.y) / crop.height
        )
    }

    /**
     * The Kit Card's tiles for [entry], in wearing order, capped
     * at the format-independent list the caller trims — the cap
     * itself is the caller's (it depends on the format): tiles
     * are built from ALL ordered garments here and the renderer /
     * Studio cap via [ShareKitCardModel.maxItems]. [excludedKeys]
     * are garments the user removed from the card in the Studio.
     */
    fun buildKitTiles(
        entry: WearIveRunDayEntry,
        garmentBitmaps: Map<String, Bitmap?>,
        excludedKeys: Set<String>
    ): List<KitCardTile> {
        val pairs: List<Pair<String, String>> = if (
            entry.itemKeys.size == entry.items.size && entry.itemKeys.isNotEmpty()
        ) {
            ShareKitCardModel.orderedItems(entry.items, entry.itemKeys)
        } else {
            // Legacy record (names only): resolve each name to its
            // engine item's key; an unknown name keeps its own
            // name as its key and simply has no render or link.
            entry.items.map { name ->
                name to (clothingItemForDisplayName(name)?.key ?: name)
            }
        }
        return pairs
            .filter { it.second !in excludedKeys }
            .map { (name, key) ->
                val engineItem = ClothingItem.entries.firstOrNull { it.key == key }
                    ?: clothingItemForDisplayName(name)
                KitCardTile(
                    key = key,
                    name = name,
                    note = engineItem?.description ?: "",
                    bitmap = garmentBitmaps[key] ?: garmentBitmaps[name],
                    hasShopLink = engineItem != null &&
                        ClothingThumbnailService.hasThumbnail(engineItem)
                )
            }
    }

    /**
     * Load the day's hero through Coil. A null [ref] is the
     * designed no-photo state and returns null WITHOUT a load
     * attempt — callers distinguish "no photo" from "photo failed
     * to load" by whether [ref] was null.
     */
    suspend fun loadHeroBitmap(context: Context, ref: String?): Bitmap? {
        if (ref.isNullOrBlank()) return null
        return loadBitmapFrom(context, ref)
    }

    /**
     * Load one garment's bundled render (the same assets the
     * outfit cards show) through Coil: the bundled asset URI
     * first, the remote thumbnail URL as its fallback. Null when
     * the garment has no render or neither source decodes.
     */
    suspend fun loadGarmentBitmap(
        context: Context,
        item: ClothingItem,
        gender: GenderPreference
    ): Bitmap? {
        val assetUri = ClothingThumbnailService.getThumbnailAssetUri(item, gender)
        loadBitmapFrom(context, assetUri)?.let { return it }
        val remoteUrl = ClothingThumbnailService.getThumbnailUrl(item, gender)
        if (remoteUrl != assetUri) {
            loadBitmapFrom(context, remoteUrl)?.let { return it }
        }
        return null
    }

    /** The Activity hosting this render, unwrapping the
     * ContextWrapper chain (the Studio's LocalContext is the
     * activity itself or a wrapper around it). Null when the
     * render was handed a non-activity context. */
    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx != null) {
            if (ctx is Activity) return ctx
            ctx = (ctx as? ContextWrapper)?.baseContext
        }
        return null
    }

    private suspend fun loadBitmapFrom(context: Context, ref: String?): Bitmap? {
        if (ref.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(ref)
                .allowHardware(false)
                .build()
            val result = ImageLoader(context).execute(request)
            (result as? SuccessResult)?.let { success ->
                (success.drawable as? BitmapDrawable)?.bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
