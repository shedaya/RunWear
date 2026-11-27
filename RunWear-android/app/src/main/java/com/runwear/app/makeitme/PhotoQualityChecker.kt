package com.runwear.app.makeitme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The in-flow reference-photo quality check (acceptance 2; spec §2.3
 * step 3): each picked photo is measured on-device BEFORE anything is
 * uploaded — ML Kit face detection (bundled model, works offline) for
 * face presence/count/size, plus a bitmap luminance sample for the
 * too-dark class. The verdict comes from the pure [PhotoQualityPolicy];
 * a failing photo is rejected in the flow with its specific §7 C3
 * reason and is never uploaded.
 *
 * WS-6 (F-1) — the orientation fix at the source. The captured
 * artifact is made UPRIGHT here, before measurement and before
 * encoding:
 *
 *  1. EXIF orientation is applied at decode ([decodeDownscaled]).
 *     The camera (system TakePicture app on the guided path, or the
 *     source app for a gallery pick) stores pixels in sensor
 *     orientation and records the correction in EXIF;
 *     `BitmapFactory` ignores EXIF, so the pre-fix decode produced
 *     the sensor-raw — sideways — bitmap, and [encodeJpeg] then
 *     re-encoded it with the EXIF stripped: F-1's poisoned artifact,
 *     manufactured at exactly this seam.
 *  2. The subject face's roll (ML Kit `headEulerAngleZ` — read here
 *     for the first time in the app) gates intake via
 *     [IntakeOrientation]: within ±30° passes; a residual clean
 *     quarter-turn (an EXIF-less sideways photo) is rotated upright
 *     and re-measured once; anything else is rejected ROTATED.
 *
 * What leaves this object — the encoded JPEG — is therefore upright
 * at the source; the server's ref-prep uprighting (RW-16.30) remains
 * as the safety net behind it, not the fix.
 */
object PhotoQualityChecker {

    data class CheckResult(
        val rejection: PhotoRejection?,
        /** JPEG bytes of the (downscaled, upright) photo to upload when accepted. */
        val jpegBytes: ByteArray?
    )

    /**
     * RW-26: the raw measurements behind a check — what the guided
     * capture's LIVE check chips read. Frontality is the face's
     * head-turn (ML Kit headEulerAngleY, degrees; 0 = straight on).
     * WS-6 adds the subject (largest) face's in-plane roll
     * (headEulerAngleZ; 0 = upright) — the F-1 gate's reading.
     */
    data class Measurements(
        val faceCount: Int,
        val largestFaceWidthFraction: Float,
        val meanLuminance: Double,
        val maxAbsHeadTurnDegrees: Float,
        val subjectHeadRollDegrees: Float = 0f,
        /**
         * RW-180 (Nov 26, 2026): the subject face region's own
         * mean luminance — the backlit reading. Defaults to the
         * frame mean (ratio 1.0, no backlit signal) for callers
         * that cannot measure a region (the live path fills it
         * from the Y plane; a still fills it from the bitmap).
         */
        val faceMeanLuminance: Double = meanLuminance,
        /**
         * RW-180: the ORIGINAL image's short edge / pixel count,
         * read from the source bounds before any downscale — the
         * resolution floor's inputs. MAX = unmeasured (passes).
         */
        val imageShortEdgePx: Int = Int.MAX_VALUE,
        val imagePixelCount: Long = Long.MAX_VALUE
    )

    /** The source image's original pixel dimensions, bounds-only. */
    private fun sourceSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth to bounds.outHeight
        } else {
            null
        }
    }

    /** Measure one photo (same decode + ML Kit path as [check]). */
    suspend fun measure(context: Context, uri: Uri): Measurements? =
        withContext(Dispatchers.IO) {
            val bitmap = decodeDownscaled(context, uri) ?: return@withContext null
            try {
                val faces = detectFacesDetailed(bitmap)
                val subject = faces.maxByOrNull { it.widthFraction }
                val frameLuminance = meanLuminance(bitmap)
                val source = sourceSize(context, uri)
                Measurements(
                    faceCount = faces.size,
                    largestFaceWidthFraction = subject?.widthFraction ?: 0f,
                    meanLuminance = frameLuminance,
                    maxAbsHeadTurnDegrees = faces.maxOfOrNull { kotlin.math.abs(it.headEulerY) } ?: 0f,
                    subjectHeadRollDegrees = subject?.headEulerZ ?: 0f,
                    faceMeanLuminance = subject?.let {
                        meanLuminance(bitmap, it.boundingBox)
                    } ?: frameLuminance,
                    imageShortEdgePx = source?.let {
                        minOf(it.first, it.second)
                    } ?: Int.MAX_VALUE,
                    imagePixelCount = source?.let {
                        it.first.toLong() * it.second.toLong()
                    } ?: Long.MAX_VALUE
                )
            } finally {
                bitmap.recycle()
            }
        }

    private data class FaceMeasure(
        val widthFraction: Float,
        val headEulerY: Float,
        val headEulerZ: Float,
        val boundingBox: android.graphics.Rect
    )

    private suspend fun detectFacesDetailed(bitmap: Bitmap): List<FaceMeasure> {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )
        return try {
            suspendCancellableCoroutine { cont ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { faces ->
                        cont.resume(
                            faces.map { face ->
                                FaceMeasure(
                                    widthFraction = face.boundingBox.width().toFloat() /
                                        bitmap.width.toFloat().coerceAtLeast(1f),
                                    headEulerY = face.headEulerAngleY,
                                    headEulerZ = face.headEulerAngleZ,
                                    boundingBox = android.graphics.Rect(face.boundingBox)
                                )
                            }
                        )
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
        } finally {
            detector.close()
        }
    }

    private const val MAX_DIMENSION_PX = 1280
    private const val LUMINANCE_SAMPLE_STEP = 12

    suspend fun check(context: Context, uri: Uri): CheckResult =
        withContext(Dispatchers.IO) {
            var bitmap = decodeDownscaled(context, uri)
                ?: return@withContext CheckResult(PhotoRejection.FACE_UNCLEAR, null)
            try {
                var faces = detectFacesDetailed(bitmap)
                // F-1 roll correction: an EXIF-less residual quarter-
                // turn is uprighted here and re-measured ONCE. A roll
                // that survives (or was never a quarter-turn) falls to
                // the policy, which rejects it ROTATED.
                val subjectRoll = faces.maxByOrNull { it.widthFraction }?.headEulerZ ?: 0f
                val correction = IntakeOrientation.quarterTurnCorrection(subjectRoll)
                if (correction != null) {
                    // rotateBitmap recycles its input when it rotates.
                    val rotated = rotateBitmap(bitmap, correction)
                    if (rotated != bitmap) {
                        bitmap = rotated
                        faces = detectFacesDetailed(bitmap)
                    }
                }
                val subject = faces.maxByOrNull { it.widthFraction }
                val frameLuminance = meanLuminance(bitmap)
                // RW-180: the resolution floor reads the SOURCE
                // bounds (this bitmap is already downscaled).
                val source = sourceSize(context, uri)
                val rejection = PhotoQualityPolicy.decide(
                    faceCount = faces.size,
                    largestFaceWidthFraction = subject?.widthFraction ?: 0f,
                    meanLuminance = frameLuminance,
                    subjectHeadRollDegrees = subject?.headEulerZ ?: 0f,
                    // GEN-6 (RW-50): head-turn is judged on upload
                    // too (§10.4) — the subject face's yaw against
                    // the guided capture's straight-on gate.
                    maxAbsHeadTurnDegrees = subject?.headEulerY
                        ?.let { kotlin.math.abs(it) } ?: 0f,
                    // RW-180: the face's own light (backlit class)
                    // and the source resolution floor.
                    faceMeanLuminance = subject?.let {
                        meanLuminance(bitmap, it.boundingBox)
                    } ?: frameLuminance,
                    imageShortEdgePx = source?.let {
                        minOf(it.first, it.second)
                    } ?: Int.MAX_VALUE,
                    imagePixelCount = source?.let {
                        it.first.toLong() * it.second.toLong()
                    } ?: Long.MAX_VALUE
                )
                if (rejection != null) {
                    CheckResult(rejection, null)
                } else {
                    CheckResult(null, encodeJpeg(bitmap))
                }
            } finally {
                bitmap.recycle()
            }
        }

    private fun decodeDownscaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION_PX * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        // F-1: apply the file's EXIF orientation NOW, at decode —
        // every later stage (measurement, encode) works on upright
        // pixels. A stream that cannot be re-opened or carries no
        // tag decodes as stored (orientation normal), exactly as
        // before; the roll gate still guards the result.
        val exifOrientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val decoded = applyExifOrientation(raw, exifOrientation)
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= MAX_DIMENSION_PX) return decoded
        val scale = MAX_DIMENSION_PX.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    /**
     * Apply an EXIF orientation value to a decoded bitmap, returning
     * the upright bitmap (the input itself when the tag is normal).
     * The transform table is [IntakeOrientation]'s pure decision;
     * this is only its pixel execution.
     */
    internal fun applyExifOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val transform = IntakeOrientation.transformForExifOrientation(exifOrientation)
        if (transform.rotationDegrees == 0 && !transform.flipHorizontal) return bitmap
        val matrix = Matrix()
        if (transform.flipHorizontal) matrix.postScale(-1f, 1f)
        if (transform.rotationDegrees != 0) {
            matrix.postRotate(transform.rotationDegrees.toFloat())
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    /** Rotate a bitmap by [degreesClockwise] (a quarter-turn in practice). */
    internal fun rotateBitmap(bitmap: Bitmap, degreesClockwise: Int): Bitmap {
        if (degreesClockwise % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degreesClockwise.toFloat()) }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    /** Mean relative luminance (0.0–1.0) over a coarse pixel sample. */
    private fun meanLuminance(bitmap: Bitmap): Double =
        meanLuminance(bitmap, null)

    /**
     * RW-180: mean luminance over one region of the bitmap (the
     * subject face's box) — the face's own light reading, against
     * which the backlit rule is judged. A null or empty region
     * reads the whole frame.
     */
    private fun meanLuminance(bitmap: Bitmap, region: android.graphics.Rect?): Double {
        val left = (region?.left ?: 0).coerceIn(0, bitmap.width - 1)
        val top = (region?.top ?: 0).coerceIn(0, bitmap.height - 1)
        val right = (region?.right ?: bitmap.width).coerceIn(left + 1, bitmap.width)
        val bottom = (region?.bottom ?: bitmap.height).coerceIn(top + 1, bitmap.height)
        var sum = 0.0
        var count = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                count++
                x += LUMINANCE_SAMPLE_STEP
            }
            y += LUMINANCE_SAMPLE_STEP
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }
}
