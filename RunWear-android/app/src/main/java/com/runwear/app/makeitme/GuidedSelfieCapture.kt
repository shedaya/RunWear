package com.runwear.app.makeitme

import com.runwear.engine.MR
import dev.icerock.moko.resources.compose.stringResource

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.RunWearColors
import java.io.File

/**
 * RW-26 (§10a, design frames 15–18): the guided-selfie capture — THE
 * primary intake path. The app dictates the capture: a face oval
 * coaches framing (margin around the face, face large, frontal,
 * well-lit) and LIVE check chips re-evaluate after every capture
 * using the platform-native facility — ML Kit face detection via
 * [PhotoQualityChecker] — judged by the IDENTICAL [PhotoQualityPolicy]
 * the upload door uses. There is NO full-body slot and NO
 * turn-your-head step. Upload remains the secondary door (the flow
 * that hosts this screen), judged by the same checks with the
 * C3E/C3F retake strings.
 *
 * GEN-5 (RW-28 item 16): the capture is IN-APP — a CameraX live
 * preview inside the oval, ML Kit face detection over the live
 * frames, the four checks reading out LIVE, one coaching line,
 * and the oval turning pine at the passing moment. The gen-4
 * build handed off to the stock camera app (TakePicture) and
 * coached only after the fact; that hand-off is the defect this
 * replaces (A-8). A captured still is reviewed by the same four
 * checks over [PhotoQualityChecker]'s measurement of the file.
 * [GuidedSelfieChecks] is the pure check logic both paths share.
 */

/** One live check chip's state. */
data class GuidedCheck(val label: String, val passed: Boolean)

/** Pure evaluation of the guided capture's check chips. */
object GuidedSelfieChecks {

    /** A face narrower than this fraction is "too small" for intake. */
    const val MIN_FACE_WIDTH_FRACTION: Float = 0.18f

    /** Head turn (degrees) beyond which the face is not frontal. */
    const val MAX_HEAD_TURN_DEGREES: Float = 18f

    // GEN-5 (RW-28 items 16/17): the FOUR checks of record — the
    // same four names on the live capture readout and on the
    // review: In frame · Face size · Straight on · Light. (The
    // old five-chip set carried "Just you" as its own chip; the
    // single-face rule still gates acceptance through
    // PhotoQualityPolicy in allPassed, and the coaching line
    // still names it when more than one face is in frame.)
    const val CHECK_IN_FRAME = "In frame"
    const val CHECK_FACE_SIZE = "Face size"
    const val CHECK_STRAIGHT_ON = "Straight on"
    const val CHECK_LIGHT = "Light"

    fun evaluate(m: PhotoQualityChecker.Measurements?): List<GuidedCheck> {
        if (m == null) {
            return listOf(
                GuidedCheck(CHECK_IN_FRAME, false),
                GuidedCheck(CHECK_FACE_SIZE, false),
                GuidedCheck(CHECK_STRAIGHT_ON, false),
                GuidedCheck(CHECK_LIGHT, false)
            )
        }
        return listOf(
            GuidedCheck(CHECK_IN_FRAME, m.faceCount >= 1),
            GuidedCheck(CHECK_FACE_SIZE, m.largestFaceWidthFraction >= MIN_FACE_WIDTH_FRACTION),
            GuidedCheck(CHECK_STRAIGHT_ON, m.faceCount >= 1 && m.maxAbsHeadTurnDegrees <= MAX_HEAD_TURN_DEGREES),
            GuidedCheck(CHECK_LIGHT, m.meanLuminance >= PhotoQualityPolicy.MIN_MEAN_LUMINANCE)
        )
    }

    /**
     * The LIVE evaluation for the in-app capture: the same four
     * checks over one analyzed preview frame's readings. The live
     * path and the captured-photo review can never disagree about
     * what the checks are.
     */
    fun evaluateLive(
        faceCount: Int,
        largestFaceWidthFraction: Float,
        maxAbsHeadTurnDegrees: Float,
        meanLuminance: Double
    ): List<GuidedCheck> = evaluate(
        PhotoQualityChecker.Measurements(
            faceCount = faceCount,
            largestFaceWidthFraction = largestFaceWidthFraction,
            meanLuminance = meanLuminance,
            maxAbsHeadTurnDegrees = maxAbsHeadTurnDegrees,
            subjectHeadRollDegrees = 0f
        )
    )

    fun allLivePassed(checks: List<GuidedCheck>): Boolean = checks.all { it.passed }

    /**
     * WS-9 fix (P-2, §1.7): the checks that TURN passing between two
     * evaluations — the selection tick fires once per check named
     * here, and only when it turns (a check already passing, or one
     * still failing, is silent).
     */
    fun turnedPassing(
        previous: List<GuidedCheck>,
        current: List<GuidedCheck>
    ): List<GuidedCheck> = current.filter { now ->
        now.passed && previous.firstOrNull { it.label == now.label }?.passed != true
    }

    fun allPassed(m: PhotoQualityChecker.Measurements?): Boolean =
        m != null && evaluate(m).all { it.passed } &&
            PhotoQualityPolicy.decide(
                m.faceCount, m.largestFaceWidthFraction, m.meanLuminance,
                m.subjectHeadRollDegrees, m.maxAbsHeadTurnDegrees
            ) == null

    /** The settled passing line (design §10.3 verbatim). */
    const val PASSING_LINE = "✓ That's exactly what we need"

    /** The ONE coaching line, aimed at the first failing check. */
    fun coachingLine(m: PhotoQualityChecker.Measurements?): String {
        if (m == null) return "Fill the oval with your face, with a little room around it."
        val checks = evaluate(m)
        return when {
            !checks[0].passed -> "Move your face into the oval."
            // The single-face rule (no longer its own chip) is
            // coached here, before size and angle.
            m.faceCount > 1 -> "Just you in the frame — one face only."
            // WS-6 (F-1): the roll gate coaches before size/framing —
            // a sideways capture is retaken upright, never accepted.
            // (Post-EXIF-decode this is residual-only: the camera's
            // own captures arrive upright at the source.)
            !IntakeOrientation.rollWithinGate(m.subjectHeadRollDegrees) ->
                "Hold your phone upright — that one came out sideways."
            !checks[1].passed -> "Move a little closer — your face should fill the oval."
            !checks[2].passed -> "Face the camera straight on."
            !checks[3].passed -> "Find brighter light — face a window or a lamp."
            else -> PASSING_LINE
        }
    }
}

@Composable
fun GuidedSelfieCapture(
    onAccepted: (Uri) -> Unit,
    onDismiss: () -> Unit,
    /**
     * GEN-6 WAVE C (DA-18): the "Choose from library instead" door —
     * it must OPEN the photo library/picker, not merely dismiss.
     * The host wires this to its photo picker and closes the capture
     * dialog; the default keeps the old dismiss-only behaviour for
     * hosts that have no picker.
     */
    onChooseFromLibrary: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var measurements by remember { mutableStateOf<PhotoQualityChecker.Measurements?>(null) }

    // ── Camera permission (in-app capture declares CAMERA; the
    // stock-camera hand-off that avoided it is retired). ─────────
    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // ── Live analysis state: the four checks over the preview
    // stream, updated as frames are analyzed. ────────────────────
    var liveMeasurements by remember { mutableStateOf<PhotoQualityChecker.Measurements?>(null) }
    val liveChecks = GuidedSelfieChecks.evaluate(liveMeasurements)
    val livePassing = liveMeasurements != null &&
        GuidedSelfieChecks.allLivePassed(liveChecks) && liveMeasurements!!.faceCount == 1

    val imageCapture = remember {
        androidx.camera.core.ImageCapture.Builder()
            .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    // GEN-6 WAVE A (RW-49 follow-through): detector creation must
    // never crash the composition. ML Kit is initialized by its
    // manifest provider on device, but if that context is missing
    // (test runtimes, a blocked provider), getClient throws
    // IllegalStateException — the capture now degrades to its
    // no-live-analysis state (preview + library door intact)
    // instead of taking the whole intake down with it. On device
    // the client is created exactly as before.
    val faceDetector = remember {
        runCatching {
            com.google.mlkit.vision.face.FaceDetection.getClient(
                com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(
                        com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST
                    )
                    .setMinFaceSize(0.12f)
                    .build()
            )
        }.getOrNull()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { faceDetector?.close() }
    }
    val lastAnalyzedMillis = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind front camera: preview + capture + live analysis. Bound
    // once permission exists; stays bound while a captured still is
    // reviewed (the still overlays the preview in the oval).
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = try {
                    future.get()
                } catch (e: Exception) {
                    return@addListener
                }
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()
                analysis.setAnalyzer(
                    androidx.core.content.ContextCompat.getMainExecutor(context)
                ) { proxy ->
                    val nowMillis = System.currentTimeMillis()
                    val last = lastAnalyzedMillis.get()
                    if (nowMillis - last < 250L) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    lastAnalyzedMillis.set(nowMillis)
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    // Mean luminance from the Y plane, sampled.
                    val yBuffer = proxy.planes[0].buffer.duplicate()
                    var sum = 0L
                    var count = 0
                    val step = 24
                    var i = 0
                    while (i < yBuffer.capacity()) {
                        sum += yBuffer.get(i).toInt() and 0xFF
                        count++
                        i += step
                    }
                    val luminance = if (count > 0) {
                        (sum.toDouble() / count) / 255.0
                    } else {
                        0.0
                    }
                    val frameWidth = proxy.width.coerceAtLeast(1)
                    val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                        mediaImage, proxy.imageInfo.rotationDegrees
                    )
                    val detector = faceDetector
                    if (detector == null) {
                        // No live analysis available (see the
                        // creation seam above): close the frame and
                        // leave the measurements unset.
                        proxy.close()
                        return@setAnalyzer
                    }
                    detector.process(input)
                        .addOnSuccessListener { faces ->
                            val largest = faces.maxOfOrNull {
                                it.boundingBox.width().toFloat()
                            } ?: 0f
                            val turn = faces.maxOfOrNull {
                                kotlin.math.abs(it.headEulerAngleY)
                            } ?: 0f
                            liveMeasurements = PhotoQualityChecker.Measurements(
                                faceCount = faces.size,
                                largestFaceWidthFraction = largest / frameWidth,
                                meanLuminance = luminance,
                                maxAbsHeadTurnDegrees = turn,
                                subjectHeadRollDegrees = 0f
                            )
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageCapture,
                        analysis
                    )
                } catch (e: Exception) {
                    // No front camera / busy camera: the surface
                    // keeps its placeholder and the library door
                    // below remains the way in.
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    fun capture() {
        val file = File(context.cacheDir, "makeitme/guided-${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        imageCapture.takePicture(
            androidx.camera.core.ImageCapture.OutputFileOptions.Builder(file).build(),
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    output: androidx.camera.core.ImageCapture.OutputFileResults
                ) {
                    capturedUri = uri
                }

                override fun onError(
                    exception: androidx.camera.core.ImageCaptureException
                ) {
                    // Stay on the live surface; the coaching loop
                    // continues and the user can try again.
                }
            }
        )
    }

    // The captured still is measured by the same checker the
    // upload door uses — the review's four checks.
    LaunchedEffect(capturedUri) {
        val uri = capturedUri
        measurements = if (uri != null) PhotoQualityChecker.measure(context, uri) else null
    }

    // §1.7 (WS-9 fix, P-2): a check TURNING passing fires the
    // selection tick — once per check that turns, over whichever
    // evaluation is on screen (live or review).
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reviewing = capturedUri != null
    val checks = if (reviewing) {
        GuidedSelfieChecks.evaluate(measurements)
    } else {
        liveChecks
    }
    val passed = if (reviewing) {
        GuidedSelfieChecks.allPassed(measurements)
    } else {
        livePassing
    }
    var previousChecks by remember { mutableStateOf<List<GuidedCheck>?>(null) }
    LaunchedEffect(checks) {
        val prev = previousChecks
        if (prev != null) {
            GuidedSelfieChecks.turnedPassing(prev, checks).forEach { _ ->
                haptic.performHapticFeedback(
                    com.runwear.app.ui.components.HapticSpec.SELECTION
                )
            }
        }
        previousChecks = checks
    }

    Box(modifier = Modifier.fillMaxSize().background(com.runwear.app.ui.theme.DarkPalette.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Close", tint = com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.8f))
                }
            }
            Text(
                text = "Make It Me",
                fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
                fontSize = 30.sp,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            Spacer(Modifier.height(16.dp))

            // Face oval: the LIVE preview inside it (the still,
            // once captured). The border turns pine at the passing
            // moment — live or review.
            Box(
                modifier = Modifier.size(240.dp).clip(CircleShape)
                    .border(
                        3.dp,
                        if (passed) com.runwear.app.ui.theme.PineAccentOnDark else com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.7f),
                        CircleShape
                    )
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (capturedUri != null) {
                    AsyncImage(
                        model = capturedUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else if (hasPermission) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = "Your face here",
                        color = com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // The four checks — live readout while framing, the
            // review's readout over the captured still.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                checks.chunked(2).forEach { rowChecks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowChecks.forEach { check ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (check.passed) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = if (check.passed) {
                                        com.runwear.app.ui.theme.PineAccentOnDark
                                    } else {
                                        com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = check.label,
                                    fontSize = 12.sp,
                                    color = if (check.passed) {
                                        com.runwear.app.ui.theme.PhotoInk
                                    } else {
                                        com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = GuidedSelfieChecks.coachingLine(
                    if (reviewing) measurements else liveMeasurements
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            if (capturedUri != null && !passed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(MR.strings.mim_c3e_retake_guided),
                    fontSize = 13.sp,
                    color = com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.height(20.dp))
            when {
                reviewing && passed -> {
                    Button(
                        onClick = { capturedUri?.let(onAccepted) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                    ) {
                        Text("Use this photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                reviewing -> {
                    Button(
                        onClick = {
                            capturedUri = null
                            measurements = null
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                    ) {
                        Text("Retake", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                !hasPermission -> {
                    Button(
                        onClick = {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                    ) {
                        Text("Allow camera", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                else -> {
                    Button(
                        onClick = { capture() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                    ) {
                        Text("Take photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
            TextButton(onClick = onChooseFromLibrary) {
                Text("Choose from library instead", color = com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.7f))
            }
        }
    }
}
