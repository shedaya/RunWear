package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.RunWearColors

/**
 * RW-133 (Sol, confirmed Nov 7, 2026): the full-screen hero
 * viewer. Tapping the hero photo (or a Your Looks tile, or a
 * Wear I've Run entry's photo) opens the STORED photograph
 * fitted full-screen — the whole outfit visible, the stored
 * frame fitted as-is (ContentScale.Fit; never cropped further).
 * At most ONE quiet caption line naming the outfit pieces.
 * Dismiss by tap or swipe-down; pinch to zoom. Personal and
 * stock serves alike.
 */
object HeroViewerPolicy {

    /** The one caption line: the outfit pieces, joined quietly.
     * Empty when there are no pieces to name (no caption then). */
    fun captionLine(items: List<String>): String? =
        items.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")

    /** Zoom is clamped to a sane inspection range. */
    fun clampZoom(zoom: Float): Float = zoom.coerceIn(1f, 4f)

    /** A downward drag past this distance (dp) dismisses. */
    const val SWIPE_DOWN_DISMISS_DP: Float = 120f

    fun shouldDismissOnSwipeDown(dragDp: Float): Boolean =
        dragDp >= SWIPE_DOWN_DISMISS_DP
}

@Composable
fun HeroViewer(
    imageRef: String,
    captionItems: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HeroViewerContent(imageRef, captionItems, onDismiss)
    }
}

/** The viewer's content, hosted by the dialog above (internal so
 * rendered tests can compose it without a dialog window). */
@Composable
internal fun HeroViewerContent(
    imageRef: String,
    captionItems: List<String>,
    onDismiss: () -> Unit
) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var dragDown by remember { mutableFloatStateOf(0f) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = HeroViewerPolicy.clampZoom(zoom * zoomChange)
            pan = if (zoom > 1f) pan + panChange else Offset.Zero
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RunWearColors.Background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (HeroViewerPolicy.shouldDismissOnSwipeDown(dragDown)) {
                                onDismiss()
                            }
                            dragDown = 0f
                        },
                        onVerticalDrag = { _, amount ->
                            if (zoom <= 1f && amount > 0) dragDown += amount
                        }
                    )
                }
        ) {
            AsyncImage(
                model = imageRef,
                contentDescription = com.runwear.app.i18n.AppStrings.outfitPhotoFull(),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
            )
            HeroViewerPolicy.captionLine(captionItems)?.let { caption ->
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    color = RunWearColors.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }
}
