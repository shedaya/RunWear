package com.runwear.app.makeitme

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
 * The capture itself rides the platform camera (TakePicture into a
 * FileProvider file — no manifest camera permission), so the
 * coaching loop is: capture → measure → chips + one coaching line →
 * retake or accept. [GuidedSelfieChecks] is the pure chip logic.
 */

/** One live check chip's state. */
data class GuidedCheck(val label: String, val passed: Boolean)

/** Pure evaluation of the guided capture's check chips. */
object GuidedSelfieChecks {

    /** A face narrower than this fraction is "too small" for intake. */
    const val MIN_FACE_WIDTH_FRACTION: Float = 0.18f

    /** Head turn (degrees) beyond which the face is not frontal. */
    const val MAX_HEAD_TURN_DEGREES: Float = 18f

    fun evaluate(m: PhotoQualityChecker.Measurements?): List<GuidedCheck> {
        if (m == null) {
            return listOf(
                GuidedCheck("Face in frame", false),
                GuidedCheck("Just you", false),
                GuidedCheck("Face large enough", false),
                GuidedCheck("Facing forward", false),
                GuidedCheck("Bright enough", false)
            )
        }
        return listOf(
            GuidedCheck("Face in frame", m.faceCount >= 1),
            GuidedCheck("Just you", m.faceCount == 1),
            GuidedCheck("Face large enough", m.largestFaceWidthFraction >= MIN_FACE_WIDTH_FRACTION),
            GuidedCheck("Facing forward", m.faceCount >= 1 && m.maxAbsHeadTurnDegrees <= MAX_HEAD_TURN_DEGREES),
            GuidedCheck("Bright enough", m.meanLuminance >= PhotoQualityPolicy.MIN_MEAN_LUMINANCE)
        )
    }

    fun allPassed(m: PhotoQualityChecker.Measurements?): Boolean =
        m != null && evaluate(m).all { it.passed } &&
            PhotoQualityPolicy.decide(
                m.faceCount, m.largestFaceWidthFraction, m.meanLuminance,
                m.subjectHeadRollDegrees
            ) == null

    /** The ONE coaching line, aimed at the first failing check. */
    fun coachingLine(m: PhotoQualityChecker.Measurements?): String {
        if (m == null) return "Fill the oval with your face, with a little room around it."
        val checks = evaluate(m)
        return when {
            !checks[0].passed -> "Move your face into the oval."
            !checks[1].passed -> "Just you in the frame — one face only."
            // WS-6 (F-1): the roll gate coaches before size/framing —
            // a sideways capture is retaken upright, never accepted.
            // (Post-EXIF-decode this is residual-only: the camera's
            // own captures arrive upright at the source.)
            !IntakeOrientation.rollWithinGate(m.subjectHeadRollDegrees) ->
                "Hold your phone upright — that one came out sideways."
            !checks[2].passed -> "Move a little closer — your face should fill the oval."
            !checks[3].passed -> "Face the camera straight on."
            !checks[4].passed -> "Find brighter light — face a window or a lamp."
            else -> "That's it — this photo works."
        }
    }
}

@Composable
fun GuidedSelfieCapture(
    onAccepted: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var measurements by remember { mutableStateOf<PhotoQualityChecker.Measurements?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingUri
        pendingUri = null
        if (success && uri != null) {
            capturedUri = uri
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "makeitme/guided-${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            pendingUri = null
        }
    }

    // Camera-first: open the camera as soon as the surface appears.
    val cameraPresent = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    LaunchedEffect(Unit) {
        if (cameraPresent) launchCamera()
    }
    LaunchedEffect(capturedUri) {
        val uri = capturedUri
        measurements = if (uri != null) PhotoQualityChecker.measure(context, uri) else null
    }

    val checks = GuidedSelfieChecks.evaluate(measurements)
    val passed = GuidedSelfieChecks.allPassed(measurements)

    Box(modifier = Modifier.fillMaxSize().background(com.runwear.app.ui.theme.DarkPalette.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            Text(
                text = "Make It Me",
                fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
                fontSize = 30.sp,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            Spacer(Modifier.height(16.dp))

            // Face oval overlay (framing coach: margin, large, frontal).
            Box(
                modifier = Modifier.size(240.dp).clip(CircleShape)
                    .border(
                        3.dp,
                        if (passed) com.runwear.app.ui.theme.PineAccentOnDark else Color.White.copy(alpha = 0.7f),
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
                } else {
                    Text(
                        text = "Your face here",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Live check chips — re-evaluated after every capture.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                checks.chunked(3).forEach { rowChecks ->
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
                                        Color.White.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = check.label,
                                    fontSize = 12.sp,
                                    color = if (check.passed) {
                                        com.runwear.app.ui.theme.PhotoInk
                                    } else {
                                        Color.White.copy(alpha = 0.6f)
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
                text = GuidedSelfieChecks.coachingLine(measurements),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            if (capturedUri != null && !passed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = MakeItMeStrings.C3E_RETAKE_GUIDED,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.height(20.dp))
            if (passed && capturedUri != null) {
                Button(
                    onClick = { onAccepted(capturedUri!!) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                ) {
                    Text("Use this photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = { launchCamera() },
                    enabled = cameraPresent,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                ) {
                    Text(
                        if (capturedUri == null) "Open camera" else "Retake",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Choose from library instead", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
