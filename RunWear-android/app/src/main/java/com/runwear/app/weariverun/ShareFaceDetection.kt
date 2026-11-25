package com.runwear.app.weariverun

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * GEN-6 Wave B (RW-31 §4.3): on-device face detection for the
 * poster crop — the render-time anchor resolution's middle step
 * (stored anchor → detection → [ShareFaceAnchor.DEFAULT_UPPER_MIDDLE]).
 * ML Kit's bundled model, following the usage patterns of
 * makeitme/PhotoQualityChecker (accurate mode, suspend bridge over
 * the Task API, detector closed after use).
 *
 * Returns the LARGEST face's anchor in top-left-normalised
 * coordinates, or null when no face is found or detection fails
 * for any reason — detection is an enhancement to the crop, never
 * a gate: a null here falls through to the default anchor, and
 * this function never throws into the share flow.
 */
suspend fun detectFaceAnchor(bitmap: Bitmap): ShareFaceAnchor? =
    withContext(Dispatchers.Default) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )
        try {
            val faces = suspendCancellableCoroutine { cont ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
            val largest = faces.maxByOrNull {
                it.boundingBox.width().toLong() * it.boundingBox.height().toLong()
            } ?: return@withContext null
            val box = largest.boundingBox
            ShareFaceAnchor.fromFaceRect(
                x = box.left.toDouble() / bitmap.width,
                y = box.top.toDouble() / bitmap.height,
                width = box.width().toDouble() / bitmap.width,
                height = box.height().toDouble() / bitmap.height
            )
        } catch (e: Exception) {
            null
        } finally {
            detector.close()
        }
    }
