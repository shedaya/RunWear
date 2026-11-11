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
 * graphics-layer capture of the on-screen preview and the gen-5
 * preview-capture handle are retired from the share path and
 * deleted: nothing in the app captures a preview for sharing
 * any more.
 */

/** Which designed artifact a collection card shares. */
enum class ShareArtifactKind(val chooserTitle: String, val fileStem: String) {
    // Rename ruling (Nov 10, 2026): the format's user-facing
    // name is Postcard — the chooser title and the shared file's
    // stem (the file name surfaces in the OS share UI). The enum
    // identifier stays MORNING_POSTER (code-only).
    MORNING_POSTER("Share Postcard", "postcard"),
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

    /**
     * RW-168 (Nov 11, 2026): persist [bitmap] as a PNG in the
     * device photo roll (Pictures/RunWear) through MediaStore —
     * the direct save behind the home share menu's "Save to
     * photos". API 29+ needs no permission (app-owned insert,
     * pending-then-publish); API 26–28 writes through the legacy
     * EXTERNAL_CONTENT_URI insert under the manifest's
     * maxSdkVersion=28 WRITE_EXTERNAL_STORAGE. Returns false on
     * any failure, with the pending row removed — the caller
     * surfaces the failure honestly rather than claiming a save.
     *
     * RW-169 §7F scope note: the owner confirmed the save DID work
     * on his device — this is a robustness fix for a latent gap
     * (the publish result was unchecked and nothing read the row
     * back), not a repair of a proven on-device failure.
     */
    fun saveToPhotos(context: Context, bitmap: Bitmap, kind: ShareArtifactKind): Boolean {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                "${kind.fileStem}-${System.currentTimeMillis()}.png"
            )
            put(
                android.provider.MediaStore.Images.Media.MIME_TYPE,
                SharePayload.MIME_TYPE
            )
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/RunWear"
                )
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = try {
            resolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            val encoded = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
            check(encoded) { "card bitmap did not encode as PNG" }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(
                    android.provider.MediaStore.Images.Media.IS_PENDING, 0
                )
                // RW-169 §7F (Nov 11, 2026 — diagnosis §6): the
                // publish update's result is CHECKED. The pre-fix
                // code discarded it and returned true
                // unconditionally: an update matching zero rows
                // left the row IS_PENDING = 1 — invisible to every
                // gallery app by design — while the caller still
                // announced "Saved to photos". Anything but
                // exactly one updated row is a failure.
                val updated = resolver.update(uri, values, null, null)
                check(updated == 1) {
                    "MediaStore publish updated $updated rows (want 1)"
                }
            }
            // RW-169 §7F: READ THE ROW BACK before claiming the
            // save — the row must exist, be published
            // (IS_PENDING cleared on API 29+) and carry bytes.
            // The caller's "Saved to photos" may only follow this
            // read-back; this function returning true IS that
            // read-back passing.
            check(verifyPublishedRow(resolver, uri)) {
                "saved row did not read back as a published photo"
            }
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /**
     * RW-169 §7F: the post-write read-back. Queries [uri] and
     * returns true only when the row exists with size > 0 and —
     * on API 29+, where the pending flag exists — IS_PENDING
     * cleared (a pending row is gallery-invisible). Any query
     * failure or missing column is a failed verification, never
     * an assumed success.
     */
    fun verifyPublishedRow(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): Boolean {
        val columns = if (android.os.Build.VERSION.SDK_INT >= 29) {
            arrayOf(
                android.provider.MediaStore.Images.Media.SIZE,
                android.provider.MediaStore.Images.Media.IS_PENDING
            )
        } else {
            arrayOf(android.provider.MediaStore.Images.Media.SIZE)
        }
        return try {
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val sizeIdx = cursor.getColumnIndex(
                    android.provider.MediaStore.Images.Media.SIZE
                )
                if (sizeIdx < 0 || cursor.getLong(sizeIdx) <= 0L) return false
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val pendingIdx = cursor.getColumnIndex(
                        android.provider.MediaStore.Images.Media.IS_PENDING
                    )
                    if (pendingIdx < 0 || cursor.getInt(pendingIdx) != 0) {
                        return false
                    }
                }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
