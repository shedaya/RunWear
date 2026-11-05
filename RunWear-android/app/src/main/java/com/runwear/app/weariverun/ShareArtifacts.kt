package com.runwear.app.weariverun

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * WS-6 (D-15/D-16, RC-6): the share pipeline's payload end. What
 * leaves the app from a collection card is a RENDERED IMAGE — the
 * Morning Poster or the Kit Card rasterized to a bitmap — shared
 * via `EXTRA_STREAM` with the caption pre-filled. The pre-fix
 * `fireShare` composed both artifacts for preview and then sent
 * `text/plain` anyway; that path is deleted, and this file is the
 * ONLY share-payload builder for the collection: there is no
 * text-only variant to fall back to.
 *
 * The designed lockups ride INSIDE the images (the Poster's script
 * wordmark carries #RunAnyWear directly beneath it; the Kit Card
 * closes with the hashtag) and the caption repeats the hashtag for
 * the receiving app's text field (design frame 14).
 *
 * GEN-6 Wave B (RW-31, stage 1): the caption is no longer a
 * constant baked into this file — it is a payload PARAMETER,
 * composed by [ShareCaptionComposer] from the day record and
 * chosen in the Share Studio (stage 2 wires the picker; the
 * payload contract lands here first).
 *
 * GEN-6 Wave B (stage 2): the bitmap that arrives here is the
 * OFFSCREEN EXPORT RENDER ([ShareExportRenderer]) at exact export
 * pixels, already inspected by [ShareRasterVerifier] — the gen-5
 * graphics-layer capture of the on-screen preview
 * (ShareCaptureHandle) is retired from the share path and deleted:
 * nothing in the app captures a preview for sharing any more.
 */

/** Which designed artifact a collection card shares. */
enum class ShareArtifactKind(val chooserTitle: String, val fileStem: String) {
    MORNING_POSTER("Share Morning Poster", "morning-poster"),
    KIT_CARD("Share Kit Card", "kit-card")
}

/**
 * The share payload's pure shape — the gate pins it: image stream,
 * PNG, the caller's caption carried verbatim. `carriesTextOnlyPayload`
 * is part of the spec precisely so a test can assert it is always
 * false.
 */
object SharePayload {

    const val MIME_TYPE = "image/png"

    data class Spec(
        val mimeType: String,
        val caption: String,
        val carriesImageStream: Boolean,
        val carriesTextOnlyPayload: Boolean
    )

    fun spec(kind: ShareArtifactKind, caption: String): Spec = Spec(
        mimeType = MIME_TYPE,
        caption = caption,
        carriesImageStream = true,
        carriesTextOnlyPayload = false
    )
}

/** Writing + intent-building for a rendered artifact bitmap. */
object ShareArtifacts {

    private const val SHARE_DIR = "share-artifacts"

    /**
     * Persist [bitmap] as a PNG in the app's cache (the FileProvider
     * `cache-path` root already covers it) and return its
     * content URI for `EXTRA_STREAM`.
     */
    fun writePng(context: Context, bitmap: Bitmap, kind: ShareArtifactKind): Uri {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "${kind.fileStem}-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "artifact bitmap did not encode as PNG"
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** The ACTION_SEND intent for an artifact image — never text-only. */
    fun buildIntent(stream: Uri, kind: ShareArtifactKind, caption: String): Intent {
        val spec = SharePayload.spec(kind, caption)
        return Intent(Intent.ACTION_SEND).apply {
            type = spec.mimeType
            putExtra(Intent.EXTRA_STREAM, stream)
            putExtra(Intent.EXTRA_TEXT, spec.caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Raster file → system share sheet (the OS's own, unmodified). */
    fun share(context: Context, bitmap: Bitmap, kind: ShareArtifactKind, caption: String) {
        val stream = writePng(context, bitmap, kind)
        val intent = buildIntent(stream, kind, caption)
        context.startActivity(Intent.createChooser(intent, kind.chooserTitle))
    }
}
