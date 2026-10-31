package com.runwear.app.makeitme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 */
object PhotoQualityChecker {

    data class CheckResult(
        val rejection: PhotoRejection?,
        /** JPEG bytes of the (downscaled) photo to upload when accepted. */
        val jpegBytes: ByteArray?
    )

    private const val MAX_DIMENSION_PX = 1280
    private const val LUMINANCE_SAMPLE_STEP = 12

    suspend fun check(context: Context, uri: Uri): CheckResult =
        withContext(Dispatchers.IO) {
            val bitmap = decodeDownscaled(context, uri)
                ?: return@withContext CheckResult(PhotoRejection.FACE_UNCLEAR, null)
            try {
                val luminance = meanLuminance(bitmap)
                val faces = detectFaces(bitmap)
                val largestFaceFraction = faces.maxOfOrNull { it } ?: 0f
                val rejection = PhotoQualityPolicy.decide(
                    faceCount = faces.size,
                    largestFaceWidthFraction = largestFaceFraction,
                    meanLuminance = luminance
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
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
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

    /** Mean relative luminance (0.0–1.0) over a coarse pixel sample. */
    private fun meanLuminance(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
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

    /**
     * Face widths as fractions of the image width (ML Kit bounding
     * boxes). An empty list = no face found. Detector errors degrade to
     * "no faces", which the policy rejects as FACE_UNCLEAR — the check
     * fails closed rather than waving an unmeasured photo through.
     */
    private suspend fun detectFaces(bitmap: Bitmap): List<Float> {
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
                                face.boundingBox.width().toFloat() /
                                    bitmap.width.toFloat().coerceAtLeast(1f)
                            }
                        )
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
        } finally {
            detector.close()
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }
}
