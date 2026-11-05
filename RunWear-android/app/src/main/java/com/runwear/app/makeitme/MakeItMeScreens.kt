package com.runwear.app.makeitme

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import com.runwear.app.ui.screens.OnboardingMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.runwear.app.ui.theme.GreatVibesFontFamily
import com.runwear.app.ui.theme.RunWearColors

// ============================================================================
// Onboarding fork (spec §2.2) — the final onboarding step, shown after the
// first real recommendation. The recommendation's photo stays visible at
// the top: it is the subject of the sentence.
// ============================================================================

/** What a system Back press does on the onboarding fork screen. */
internal enum class ForkBackAction { EXIT_FLOW }

/**
 * WS-6 (BACKLOG open item from WS-5's Android lane): the fork's back
 * policy, mirroring [UploadBackPolicy] — Back steps back within a
 * flow and exits only at its root. The fork is a single screen and
 * IS the Make It Me onboarding flow's root (its one step forward is
 * the upload flow, whose own root-exit the fork's exit mirrors:
 * leave as stock, the flow's settled end-state). Before this, the
 * fork was a boolean overlay with NO BackHandler: system Back fell
 * through to the activity and closed the app — the F-2 class on the
 * one surface WS-5's conversion did not reach.
 */
internal object ForkBackPolicy {
    fun actionFor(): ForkBackAction = ForkBackAction.EXIT_FLOW
}

/**
 * W-3 (Nov 4 walkthrough): the fork header band's crop geometry.
 *
 * The band shows the SAME hero serve home shows, in a 300 dp-tall
 * full-width box. Home anchors its Crop to the TOP (HeroSection's
 * BiasAlignment verticalBias −1 at rest — see [com.runwear.app.ui
 * .components.HeroImageGeometry]). The fork previously passed NO
 * alignment, so AsyncImage's default CENTER anchor discarded the
 * top (sourceHeight − sliceHeight)/2 of the frame before the slice
 * even started — and on the fork's serve the runner's head sat in
 * that discarded region: the chin crop Sol flagged on both
 * platforms' forks (home was fine; do not touch it).
 *
 * The fix is the anchor alone: top-anchored, exactly as home, so
 * the slice starts at source y = 0 and nothing above the runner
 * can be discarded. The band's height is part of the geometry, not
 * a free knob — at the standard portrait phone widths the 300 dp
 * band's top-anchored slice reaches at least the bottom of the
 * head band D-23 audited on the home serve (source y ≈ 392–509 of
 * 1200); shrinking the band would pull the slice's bottom back up
 * through the face. [headBandFullyVisible] pins that across the
 * width matrix in tests.
 */
internal object ForkBandGeometry {

    /** The band's height — part of the composition, not a free knob. */
    const val BAND_HEIGHT_DP = 300

    // GEN-5 (RW-28, ruling C-2): the fork band is ONE purpose-designed
    // hard-coded image (res/drawable-nodpi/mim_fork_hero.webp,
    // 2048 x 1152, 16:9) — it no longer shows the home hero serve.
    // Anchoring rule of record (asset lane README, binding): plain
    // centre-crop is ruled OUT (the head has only 3.2% headroom).
    //  - Band aspect <= image aspect: full image height is shown;
    //    the horizontal window is centred on the face centre
    //    (x = 0.507), clamped to the image edges.
    //  - Band aspect > image aspect: full image width is shown; the
    //    vertical window is anchored to the TOP of the image.
    const val IMAGE_WIDTH_PX = 2048f
    const val IMAGE_HEIGHT_PX = 1152f
    const val FACE_CENTRE_X_FRACTION = 0.507f

    /** The dashed face box, in image-normalised coordinates. */
    const val FACE_BOX_X = 0.400f
    const val FACE_BOX_Y = 0.018f
    const val FACE_BOX_WIDTH = 0.214f
    const val FACE_BOX_HEIGHT = 0.478f

    /** A rectangle in band coordinates (px). */
    data class RectF(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * The displayed image rect for a band of [bandWidthPx] x
     * [bandHeightPx] under the anchoring rule: the image is scaled
     * to fill (cover), then positioned per the rule. The rect may
     * extend past the band on the cropped axis (negative left/top).
     */
    fun displayedImageRect(bandWidthPx: Float, bandHeightPx: Float): RectF {
        val bandAspect = bandWidthPx / bandHeightPx
        val imageAspect = IMAGE_WIDTH_PX / IMAGE_HEIGHT_PX
        val scale = maxOf(bandWidthPx / IMAGE_WIDTH_PX, bandHeightPx / IMAGE_HEIGHT_PX)
        val displayedW = IMAGE_WIDTH_PX * scale
        val displayedH = IMAGE_HEIGHT_PX * scale
        val left: Float
        val top: Float
        if (bandAspect <= imageAspect) {
            // Full height shown; window centred on the face centre.
            val desired = FACE_CENTRE_X_FRACTION * displayedW - bandWidthPx / 2f
            val offsetX = desired.coerceIn(0f, (displayedW - bandWidthPx).coerceAtLeast(0f))
            left = -offsetX
            top = 0f
        } else {
            // Full width shown; window anchored to the top.
            left = 0f
            top = 0f
        }
        return RectF(left, top, displayedW, displayedH)
    }

    /** The face box in band coordinates, from the displayed rect. */
    fun faceBoxRect(bandWidthPx: Float, bandHeightPx: Float): RectF {
        val img = displayedImageRect(bandWidthPx, bandHeightPx)
        return RectF(
            left = img.left + FACE_BOX_X * img.width,
            top = img.top + FACE_BOX_Y * img.height,
            width = FACE_BOX_WIDTH * img.width,
            height = FACE_BOX_HEIGHT * img.height
        )
    }

    /**
     * True when the FULL head (measured box y 0.032..0.482, x
     * 0.414..0.600) is inside the band window under the anchoring
     * rule — pinned across the form-factor matrix in tests.
     */
    fun headFullyVisible(bandWidthPx: Float, bandHeightPx: Float): Boolean {
        val img = displayedImageRect(bandWidthPx, bandHeightPx)
        val headLeft = img.left + 0.414f * img.width
        val headRight = img.left + 0.600f * img.width
        val headTop = img.top + 0.032f * img.height
        val headBottom = img.top + 0.482f * img.height
        return headLeft >= 0f && headRight <= bandWidthPx &&
            headTop >= 0f && headBottom <= bandHeightPx
    }
}

@Composable
fun MakeItMeForkScreen(
    heroImageUrl: String?,
    onUseMyPhotos: () -> Unit,
    onUseStandardPhotos: () -> Unit,
    modifier: Modifier = Modifier,
    /** System Back at the flow's root (see [ForkBackPolicy]). */
    onBack: () -> Unit = {}
) {
    // The fork owns its Back press — it never falls through to the
    // activity (which would close the app from onboarding's last
    // step). Root exit only, per ForkBackPolicy.
    androidx.activity.compose.BackHandler {
        when (ForkBackPolicy.actionFor()) {
            ForkBackAction.EXIT_FLOW -> onBack()
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // The first recommendation's photo — "that's a model in your
        // outfit photo." W-3: the crop is TOP-anchored
        // (ForkBandGeometry), as home's hero is — the runner's full
        // head is in the band. The column below scrolls
        // (verticalScroll on the root), so the whole fork — both
        // option cards, the "Use standard photos" button included —
        // is reachable however tall the content runs.
        // GEN-5 (RW-28, C-2): the hard-coded fork hero — one
        // purpose-designed bundled image, cover-cropped under the
        // asset lane's anchoring rule (ForkBandGeometry), with the
        // dashed face box drawn as a code overlay in image space so
        // it stays registered to the face at every form factor. The
        // band's placeholder is the designed cream, never white
        // (A-5). Both doors below are fully visible on arrival: the
        // root column scrolls AND the band is exactly one band tall
        // (A-4's clip came from the old serve's composition; the
        // settled fork keeps both cards reachable without a scroll
        // cue hunt — the scroll remains for small screens).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(ForkBandGeometry.BAND_HEIGHT_DP.dp)
                .background(RunWearColors.Background)
                .clip(androidx.compose.ui.graphics.RectangleShape)
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bandWidthPx = with(density) { maxWidth.toPx() }
            val bandHeightPx = with(density) { maxHeight.toPx() }
            val imgRect = ForkBandGeometry.displayedImageRect(bandWidthPx, bandHeightPx)
            val boxRect = ForkBandGeometry.faceBoxRect(bandWidthPx, bandHeightPx)
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    com.runwear.app.R.drawable.mim_fork_hero
                ),
                contentDescription = null,
                contentScale = ContentScale.None,
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            imgRect.left.roundToInt(), imgRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { imgRect.width.toDp() },
                        height = with(density) { imgRect.height.toDp() }
                    )
            )
            // The dashed face box — same dashed language as the
            // guided capture's face oval, pine on the light image.
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = RunWearColors.PineAccent,
                    topLeft = androidx.compose.ui.geometry.Offset(boxRect.left, boxRect.top),
                    size = androidx.compose.ui.geometry.Size(boxRect.width, boxRect.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        boxRect.height * 0.12f, boxRect.height * 0.12f
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx())
                        )
                    )
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
                    // Frame 32: the fork introduces Make It Me under its
        // script wordmark (third wordmark, design §7.5).
        Text(
            text = "Make It Me",
            fontFamily = GreatVibesFontFamily,
            fontSize = 34.sp,
            color = RunWearColors.TextPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
Text(
                text = MakeItMeStrings.F1_HEADLINE,
                fontFamily = FontFamily.Default,
                fontSize = 30.sp,
                letterSpacing = 0.5.sp,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MakeItMeStrings.F2_LEAD,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = RunWearColors.TextSecondary
            )

            Spacer(Modifier.height(24.dp))

            // Option A — customize
            ForkOptionCard(
                title = MakeItMeStrings.F3_OPTION_A_TITLE,
                body = MakeItMeStrings.F4_OPTION_A_BODY,
                buttonText = MakeItMeStrings.F5_OPTION_A_BUTTON,
                primary = true,
                onClick = onUseMyPhotos
            )

            Spacer(Modifier.height(14.dp))

            // Option B — stock (a complete, respected end-state)
            ForkOptionCard(
                title = MakeItMeStrings.F6_OPTION_B_TITLE,
                body = MakeItMeStrings.F7_OPTION_B_BODY,
                buttonText = MakeItMeStrings.F8_OPTION_B_BUTTON,
                primary = false,
                onClick = onUseStandardPhotos
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = MakeItMeStrings.F9_FOOTER,
                fontSize = 13.sp,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ForkOptionCard(
    title: String,
    body: String,
    buttonText: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunWearColors.BgCard)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary
        )
        Text(
            text = body,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = RunWearColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = if (primary) {
                ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = RunWearColors.BgCard,
                    contentColor = RunWearColors.TextPrimary
                )
            }
        ) {
            Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

// ============================================================================
// Upload sub-flow (spec §2.3): consent → attestation → photos (quality
// checked in-flow) → first generation. The order is a privacy commitment:
// no photo UI exists before the consent screen.
// ============================================================================

internal enum class UploadStep { CONSENT, PHOTOS, SUBMITTING, SAVED }

/** What a system Back press does at one upload-flow step (WS-5, F-2). */
internal enum class UploadBackAction { EXIT_FLOW, STEP_BACK_TO_CONSENT, STAY }

/**
 * WS-5 (F-2): the upload flow's back policy — Back steps back
 * through the flow, then exits it; it never falls through to the
 * activity. Pure, so the gate can pin every step's action.
 */
internal object UploadBackPolicy {
    fun actionFor(step: UploadStep): UploadBackAction = when (step) {
        UploadStep.CONSENT -> UploadBackAction.EXIT_FLOW
        UploadStep.PHOTOS -> UploadBackAction.STEP_BACK_TO_CONSENT
        // A submit in flight and the save-confirmation beat own
        // their moment: Back does nothing rather than stranding
        // either half-finished.
        UploadStep.SUBMITTING -> UploadBackAction.STAY
        UploadStep.SAVED -> UploadBackAction.STAY
    }
}

private class PickedPhoto(val uri: Uri) {
    var checking: Boolean by mutableStateOf(true)
    var rejection: PhotoRejection? by mutableStateOf(null)
    var jpegBytes: ByteArray? = null
}

@Composable
fun MakeItMeUploadFlow(
    viewModel: MakeItMeViewModel,
    gender: String,
    replace: Boolean,
    todaysCell: Pair<String, List<String>>?,
    onFinished: () -> Unit,
    onExitAsStock: () -> Unit,
    modifier: Modifier = Modifier
) {
    MakeItMeUploadFlow(
        recordConsent = { g, onResult -> viewModel.recordConsent(g, onResult) },
        submitPhotos = { photos, rep, cell, onResult ->
            viewModel.submitPhotos(photos, rep, cell, onResult)
        },
        gender = gender,
        replace = replace,
        todaysCell = todaysCell,
        onFinished = onFinished,
        onExitAsStock = onExitAsStock,
        modifier = modifier
    )
}

/**
 * GEN-2 A1 (W-2): the upload flow's implementation, with its two
 * view-model calls carried as function seams so the gate can drive
 * the REAL flow (consent -> photos, Back between them) in tests
 * without a network or a RevenueCat init. Production callers use
 * the view-model overload above; behavior is identical.
 */
@Composable
internal fun MakeItMeUploadFlow(
    recordConsent: (String, (Boolean) -> Unit) -> Unit,
    submitPhotos: (List<ByteArray>, Boolean, Pair<String, List<String>>?, (Boolean) -> Unit) -> Unit,
    gender: String,
    replace: Boolean,
    todaysCell: Pair<String, List<String>>?,
    onFinished: () -> Unit,
    onExitAsStock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(UploadStep.CONSENT) }
    // WS-5 (F-2): system Back is the flow's own — step back, then
    // exit the flow to the surface that opened it (the host clears
    // its state via onExitAsStock). BackHandler registers with the
    // activity's OnBackPressedDispatcher, which is also the
    // predictive-back path; before this, the app had no BackHandler
    // anywhere and Back finished the activity from mid-flow.
    androidx.activity.compose.BackHandler {
        when (UploadBackPolicy.actionFor(step)) {
            UploadBackAction.EXIT_FLOW -> onExitAsStock()
            UploadBackAction.STEP_BACK_TO_CONSENT -> step = UploadStep.CONSENT
            UploadBackAction.STAY -> Unit
        }
    }
    var attested by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val photos = remember { mutableStateListOf<PickedPhoto>() }

    // RW-16.15: the save-confirmation beat. A successful submit
    // lands on UploadStep.SAVED (the "Your photos are saved."
    // state) and only after the beat does the flow finish — the
    // explicit confirmation the field report found missing. iOS
    // carries the identical contract in OptInCommitDriver.didSave.
    LaunchedEffect(step) {
        if (step == UploadStep.SAVED) {
            delay(1_500)
            onFinished()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        uris.take(3).forEach { uri ->
            if (photos.none { it.uri == uri }) photos.add(PickedPhoto(uri))
        }
    }

    // RW-16.14: the camera source. TakePicture drives the platform
    // camera app into a FileProvider file in our cache — full
    // resolution, NO manifest camera permission — and the result is
    // a content Uri of exactly the shape the picker produces, so
    // the quality-check effect below treats a capture identically
    // to a library pick. There is one intake path, not two.
    var showSourceChoice by remember { mutableStateOf(false) }
    var showGuidedCapture by remember { mutableStateOf(false) }
    var sourceNote by remember { mutableStateOf<String?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null && photos.none { it.uri == uri }) {
            photos.add(PickedPhoto(uri))
        }
    }
    val cameraPresent = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    fun launchPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "makeitme/capture-${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        pendingCaptureUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // No activity answers the capture intent after all:
            // the policy's fallback — library, with the note.
            pendingCaptureUri = null
            sourceNote = PhotoIntakePolicy.CAMERA_UNAVAILABLE_NOTE
            launchPicker()
        }
    }

    fun chooseSource(source: PhotoIntakePolicy.Source) {
        when (val route = PhotoIntakePolicy.route(source, cameraPresent)) {
            PhotoIntakePolicy.Route.Camera -> launchCamera()
            PhotoIntakePolicy.Route.Library -> {
                sourceNote = null
                launchPicker()
            }
            is PhotoIntakePolicy.Route.LibraryWithNote -> {
                sourceNote = route.note
                launchPicker()
            }
        }
    }

    // Quality-check each newly picked photo in the flow.
    photos.forEach { picked ->
        LaunchedEffect(picked.uri) {
            if (picked.checking && picked.jpegBytes == null && picked.rejection == null) {
                val result = PhotoQualityChecker.check(context, picked.uri)
                picked.rejection = result.rejection
                picked.jpegBytes = result.jpegBytes
                picked.checking = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onExitAsStock) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = RunWearColors.TextSecondary
                )
            }
        }

        // GEN-2 A1 (W-2, P0) — THE OVERLAP MECHANISM, diagnosed:
        // AnimatedContent's transition layout places EVERY root
        // child of its content lambda at the same origin (it is a
        // single-content transition container, like the onboarding
        // host, whose branches each emit ONE root composable).
        // This flow's branches emitted their whole layout as
        // top-level siblings — title, body, attestation row,
        // Continue, Not now — so every element was placed on top
        // of the first: the title rendered over the first
        // paragraph, the attestation was buried under both, and
        // Continue sat underneath the text stack, unreachable.
        // That is the garbled, dead-end consent sheet Sol recorded
        // on Nov 4 in both modes. The fix is structural: each
        // branch now emits exactly ONE root — a Column carrying
        // the step's layout — so the transition container stacks
        // steps, never the elements within a step.
        //
        // WS-10 (§1.4): the flow's steps advance with the same M3
        // shared-axis horizontal slide + fade as onboarding's own
        // steps — consent → capture is a step transition, not a cut.
        androidx.compose.animation.AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val easing = OnboardingMotion.EmphasizedEasing
                val duration = OnboardingMotion.STEP_TRANSITION_MS
                (androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(duration, easing = easing)
                ) { width -> if (forward) width else -width } +
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(duration, easing = easing)
                    ))
                    .togetherWith(
                        androidx.compose.animation.slideOutHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(duration, easing = easing)
                        ) { width -> if (forward) -width else width } +
                            androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(duration, easing = easing)
                            )
                    )
            },
            label = "makeItMeUploadStep"
        ) { animatedStep ->
        when (animatedStep) {
            UploadStep.CONSENT -> {
              Column {
                Text(
                    text = MakeItMeStrings.C1A_CONSENT_TITLE,
                    fontFamily = FontFamily.Default,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp,
                    color = RunWearColors.TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = MakeItMeStrings.C1B_CONSENT_BODY,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = RunWearColors.TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { attested = !attested },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = attested, onCheckedChange = { attested = it })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = MakeItMeStrings.C1C_ATTESTATION,
                        fontSize = 14.sp,
                        color = RunWearColors.TextPrimary
                    )
                }
                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        working = true
                        errorText = null
                        recordConsent(gender) { ok ->
                            working = false
                            if (ok) {
                                step = UploadStep.PHOTOS
                            } else {
                                errorText = MakeItMeStrings.TRANSPORT_ERROR
                            }
                        }
                    },
                    enabled = attested && !working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            MakeItMeStrings.C1D_CONTINUE,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onExitAsStock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(MakeItMeStrings.C1E_NOT_NOW, color = RunWearColors.TextSecondary)
                }
              }
            }

            UploadStep.PHOTOS, UploadStep.SUBMITTING -> {
              Column {
                Text(
                    text = MakeItMeStrings.C2A_CAPTURE_TITLE,
                    fontFamily = FontFamily.Default,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp,
                    color = RunWearColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = MakeItMeStrings.C2B_CAPTURE_GUIDANCE,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = RunWearColors.TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                // RW-26: guided capture is THE primary door (the app
                // dictates the selfie: overlay + live ML Kit chips);
                // upload stays the secondary door below, judged by
                // the identical checks. (The RW-16.14 full-body
                // guidance display is retired by the Nov 2 intake
                // ruling — no full-body slot; the C2D string remains
                // in the deck for the record.)
                Button(
                    onClick = { showGuidedCapture = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                ) {
                    Text("Take a guided selfie", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                }
                if (showGuidedCapture) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showGuidedCapture = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        GuidedSelfieCapture(
                            onAccepted = { uri ->
                                showGuidedCapture = false
                                if (photos.none { it.uri == uri }) photos.add(PickedPhoto(uri))
                            },
                            onDismiss = { showGuidedCapture = false }
                        )
                    }
                }
                if (replace) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = MakeItMeStrings.S7_REPLACE_NOTE,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(18.dp))

                photos.forEach { picked ->
                    PickedPhotoRow(
                        picked = picked,
                        onRemove = { photos.remove(picked) }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (photos.size < 3) {
                    Button(
                        onClick = { showSourceChoice = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunWearColors.BgCard,
                            contentColor = RunWearColors.TextPrimary
                        )
                    ) {
                        Text("Add photos", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                sourceNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, fontSize = 13.sp, color = RunWearColors.TextSecondary)
                }
                if (showSourceChoice) {
                    AlertDialog(
                        onDismissRequest = { showSourceChoice = false },
                        title = { Text(MakeItMeStrings.C2E_SOURCE_DIALOG_TITLE) },
                        text = {
                            Column {
                                TextButton(onClick = {
                                    showSourceChoice = false
                                    chooseSource(PhotoIntakePolicy.Source.CAMERA)
                                }) { Text(MakeItMeStrings.C2F_TAKE_PHOTO) }
                                TextButton(onClick = {
                                    showSourceChoice = false
                                    chooseSource(PhotoIntakePolicy.Source.LIBRARY)
                                }) { Text(MakeItMeStrings.C2G_CHOOSE_FROM_LIBRARY) }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showSourceChoice = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                val accepted = photos.filter { it.jpegBytes != null }
                val anyChecking = photos.any { it.checking }
                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        step = UploadStep.SUBMITTING
                        errorText = null
                        submitPhotos(
                            accepted.map { it.jpegBytes!! }, replace, todaysCell
                        ) { ok ->
                            if (ok) {
                                // RW-16.15: confirm the save on
                                // screen first (UploadStep.SAVED);
                                // the beat effect above finishes.
                                step = UploadStep.SAVED
                            } else {
                                step = UploadStep.PHOTOS
                                errorText = MakeItMeStrings.TRANSPORT_ERROR
                            }
                        }
                    },
                    enabled = accepted.isNotEmpty() && !anyChecking && step != UploadStep.SUBMITTING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                ) {
                    if (step == UploadStep.SUBMITTING) {
                        // GEN-3 A-HERO (WS-B, X-1): a submitting state
                        // is a spinner WITH its label — the bare
                        // greyed spinner with no words is the exact
                        // frame the gen-2 walkthrough condemned.
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (replace) MakeItMeStrings.C2C_CONFIRM_SAVE else MakeItMeStrings.C2C_CONFIRM_FIRST_LOOK,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            if (replace) MakeItMeStrings.C2C_CONFIRM_SAVE else MakeItMeStrings.C2C_CONFIRM_FIRST_LOOK,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

              }
            UploadStep.SAVED -> {
                // RW-16.15 save confirmation (spec C2J): the
                // explicit "it saved," held for the beat by the
                // LaunchedEffect above, then the flow finishes.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = RunWearColors.PineAccent,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = MakeItMeStrings.C2J_SAVE_CONFIRMED,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.TextPrimary
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PickedPhotoRow(picked: PickedPhoto, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = picked.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RunWearColors.BgCard)
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                when {
                    picked.checking -> CircularProgressIndicator(
                        color = RunWearColors.PineAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    picked.rejection != null -> Column {
                        Text(
                            text = PhotoQualityPolicy.stringFor(picked.rejection!!),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        // RW-26: the upload door's retake prompt
                        // (C3F) — camera offered first, same checks.
                        Text(
                            text = MakeItMeStrings.C3F_RETAKE_UPLOAD,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    else -> Text(
                        text = "Looks good.",
                        fontSize = 13.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove photo",
                    tint = RunWearColors.TextSecondary
                )
            }
        }
    }
}

// ============================================================================
// One Your Photos tile (RW-16.15). The contract mirrors iOS build
// (13)'s ReferenceTileLoader: a tile is always in a LABELED phase —
// loading says so, success shows the photo, and failure is a
// terminal, tappable retry tile. A failed load can never again
// present as the silent blank box vc21 shipped (every tile 404'd
// under a wrong URL join and Coil painted nothing).
// ============================================================================

private enum class ReferenceTilePhase { LOADING, LOADED, FAILED }

@Composable
private fun ReferencePhotoTile(url: String, onRetry: () -> Unit) {
    var phase by remember(url) { mutableStateOf(ReferenceTilePhase.LOADING) }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RunWearColors.BgCard)
    ) {
        if (phase != ReferenceTilePhase.FAILED) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { phase = ReferenceTilePhase.LOADED },
                onError = { phase = ReferenceTilePhase.FAILED },
                modifier = Modifier.fillMaxSize()
            )
        }
        when (phase) {
            ReferenceTilePhase.LOADING -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = RunWearColors.PineAccent,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = MakeItMeStrings.S15_PHOTO_LOADING,
                    fontSize = 11.sp,
                    color = RunWearColors.TextSecondary
                )
            }
            ReferenceTilePhase.FAILED -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onRetry)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = MakeItMeStrings.S16_PHOTO_LOAD_FAILED,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    color = RunWearColors.TextSecondary
                )
                Text(
                    text = MakeItMeStrings.S17_PHOTO_RETRY,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RunWearColors.TextPrimary
                )
            }
            ReferenceTilePhase.LOADED -> Unit
        }
    }
}

/**
 * RW-16.15 increment: the Your Photos screen-level failure state —
 * the labeled terminal state the bounded load lands on (watchdog
 * timeout, or a completed load with nothing to show). Retry
 * re-runs the whole load for fresh signed URLs.
 */
@Composable
private fun PhotosLoadError(onRetry: () -> Unit) {
    Column {
        Text(
            text = MakeItMeStrings.S18_PHOTOS_LOAD_FAILED,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry) {
            Text(MakeItMeStrings.S19_TRY_AGAIN)
        }
    }
}

// ============================================================================
// Settings "Your photos" view (spec §3): current references + replace.
// ============================================================================

@Composable
fun MakeItMePhotosScreen(
    viewModel: MakeItMeViewModel,
    onReplaceClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    MakeItMePhotosScreen(
        loadReferenceUrls = { onResult -> viewModel.loadReferenceUrls(onResult) },
        onReplaceClick = onReplaceClick,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * GEN-2 A1 (F-2): the Your Photos implementation, with its one
 * view-model call carried as a function seam so the gate can host
 * the REAL screen in its production Dialog pattern in tests
 * without a network. Production callers use the overload above.
 */
@Composable
internal fun MakeItMePhotosScreen(
    loadReferenceUrls: ((List<String>) -> Unit) -> Unit,
    onReplaceClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urls by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    // RW-16.15: bumping the token re-runs the load — the tile retry
    // path. Fresh signed URLs (not just a Coil re-request), so an
    // expired or bad URL is itself the thing that gets replaced.
    var loadToken by remember { mutableStateOf(0) }
    // RW-16.15 increment: the load is BOUNDED. The field recording
    // showed a bare spinner for ~12 s resolving into blank tiles;
    // now the watchdog lands an unfinished load on the labeled
    // error/retry state instead — the screen always ends on
    // content or a labeled state, never spinner-into-blankness.
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(loadToken) {
        loadFailed = false
        loadReferenceUrls { result ->
            urls = result
            loaded = true
        }
        launch {
            delay(20_000)
            if (!loaded) loadFailed = true
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = RunWearColors.TextSecondary
                )
            }
        }
        Text(
            text = MakeItMeStrings.S6_PHOTOS_ROW,
            fontFamily = FontFamily.Default,
            fontSize = 28.sp,
            letterSpacing = 0.5.sp,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))
        when {
            loadFailed && !loaded -> PhotosLoadError(
                onRetry = { loadToken++ }
            )
            !loaded -> Column {
                CircularProgressIndicator(color = RunWearColors.PineAccent)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = MakeItMeStrings.S15_PHOTO_LOADING,
                    fontSize = 13.sp,
                    color = RunWearColors.TextSecondary
                )
            }
            // A completed load with nothing to show is a failed
            // load (an opted-in user has a registered set) — the
            // labeled state, never an empty row over the note.
            urls.isEmpty() -> PhotosLoadError(
                onRetry = {
                    loaded = false
                    loadToken++
                }
            )
            else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                urls.forEach { url ->
                    ReferencePhotoTile(
                        url = url,
                        onRetry = {
                            loaded = false
                            urls = emptyList()
                            loadToken++
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = MakeItMeStrings.S7_REPLACE_NOTE,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = RunWearColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            // Frame 38: the epoch rule in plain words.
            Text(
                text = MakeItMeStrings.S20_EPOCH_RULE,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = RunWearColors.TextSecondary
            )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onReplaceClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
        ) {
            Text("Replace photos", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ============================================================================
// Generation progress line (spec §2.4, strings G1/G2).
// GEN-3 A-HERO (X-8): this line NO LONGER renders on home — the
// generating state lives ON the hero image now, as the hero pill
// (WS-A(2) state (a)), so the state sits on the photo it belongs to
// instead of in the sheet's scroll content. The composable remains
// as the G1/G2 strings' reference host. (Copy audit, X-8: G2 no
// longer claims a standard photo is staying up — with WS-A(1) a
// truthful photo IS up wherever one is servable, and the line does
// not need to promise it.)
// ============================================================================

@Composable
fun MakeItMeProgressLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RunWearColors.Background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = RunWearColors.PineAccent,
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = MakeItMeStrings.G1_PROGRESS,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextPrimary
            )
            Text(
                text = MakeItMeStrings.G2_PROGRESS_SUB,
                fontSize = 11.sp,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// Look-failure line (spec §7 G5/G6, RW-16.16) — the durable terminal
// state that replaces G4 silence. Sits with the hero in exactly the
// progress line's places, for as long as the server reports the
// cell's look as failed. The standard photo stays up; the retry is
// user-initiated only (automatic re-request stays off).
// ============================================================================

@Composable
fun MakeItMeFailedLine(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RunWearColors.Background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = MakeItMeStrings.G5_LOOK_FAILED,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RunWearColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = MakeItMeStrings.G6_RETRY,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================================
// The 6th-cell upsell card (spec §5.3, strings U1) — in the normal
// scroll, below the outfit. Never a modal, never over the hero.
// ============================================================================

@Composable
fun MakeItMeUpsellCard(
    onSubscribeClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunWearColors.BgCard)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = MakeItMeStrings.U1A_EYEBROW,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = RunWearColors.PineAccent
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MakeItMeStrings.U1B_HEADLINE,
            fontFamily = FontFamily.Default,
            fontSize = 24.sp,
            letterSpacing = 0.5.sp,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MakeItMeStrings.U1C_BODY,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = RunWearColors.TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSubscribeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
        ) {
            Text(
                MakeItMeStrings.U1D_PRIMARY,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(MakeItMeStrings.U1E_NOT_NOW, color = RunWearColors.TextSecondary)
        }
    }
}

// ============================================================================
// Subscription sheet (spec §5.3, strings U2). Every price renders
// from the fetched current RevenueCat offering — the experiment
// requirement (RW-16.30, Nov 2 2026): a user assigned a variant
// offering sees that variant's prices, never a hardcoded one. The
// U2B fallback labels are price-free plan names shown only until
// the offering resolves (rows stay disabled until then).
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakeItMePaywallSheet(
    viewModel: MakeItMeViewModel,
    onDismiss: () -> Unit,
    onPurchased: () -> Unit
) {
    val context = LocalContext.current
    val mimState by viewModel.uiState.collectAsState()
    var monthly by remember { mutableStateOf<PackagePrice?>(null) }
    var annual by remember { mutableStateOf<PackagePrice?>(null) }
    var purchasing by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val offering = viewModel.offering()
        val monthlyMicros = offering?.monthly?.product?.price?.amountMicros
        monthly = offering?.monthly?.let {
            PackagePrice(it, MakeItMeStrings.monthlyRowLabel(it.product.price.formatted))
        }
        annual = offering?.annual?.let {
            PackagePrice(
                it,
                MakeItMeStrings.annualRowLabel(
                    annualFormatted = it.product.price.formatted,
                    annualAmountMicros = it.product.price.amountMicros,
                    monthlyAmountMicros = monthlyMicros
                ),
                formatted = it.product.price.formatted,
                amountMicros = it.product.price.amountMicros
            )
        }
    }

    fun buy(price: PackagePrice?) {
        val pkg = price?.pkg ?: return
        val activity = context as? Activity ?: return
        purchasing = true
        purchaseError = null
        viewModel.purchase(activity, pkg) { outcome ->
            purchasing = false
            when (outcome) {
                BillingManager.PurchaseOutcome.Success -> onPurchased()
                BillingManager.PurchaseOutcome.Cancelled -> Unit
                is BillingManager.PurchaseOutcome.Failed -> purchaseError = outcome.message
            }
        }
    }

    // WS-5 (D-13): one anchor, like every sheet in the app.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Frame 22: the "Make It Me" script title (third wordmark).
            Text(
                text = MakeItMeStrings.U2A_SHEET_TITLE,
                fontFamily = GreatVibesFontFamily,
                fontSize = 34.sp,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            // Free bar + the real meter ("2 of 5 used" pattern).
            Text(
                text = MakeItMeStrings.U2D_FREE_BAR,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextSecondary
            )
            Text(
                text = FreeLooksMeter.paywallLine(mimState.freeGenerationsUsed, mimState.anyLookMaking),
                fontSize = 13.sp,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(16.dp))

            // How Premium works — convergence copy (frame 22 verbatim).
            Text(
                text = MakeItMeStrings.U2E_HOW_PREMIUM_WORKS,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary
            )
            Text(
                text = MakeItMeStrings.U2F_HOW_PREMIUM_WORKS_BODY,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = RunWearColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))

            // The ONE highlighted card: "Premium" / "All of Make It
            // Me." Its price slot is the ONLY price on the card and it
            // renders from the fetched Offering (parameterized —
            // design §6); the CTA carries no price. No Founding /
            // Limited-time / struck-through elements anywhere.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = RunWearColors.BgCard,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, RunWearColors.Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = MakeItMeStrings.U2G_CARD_TITLE,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunWearColors.TextPrimary
                    )
                    Text(
                        text = MakeItMeStrings.U2H_CARD_LINE,
                        fontSize = 14.sp,
                        color = RunWearColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = annual?.label ?: MakeItMeStrings.U2B_ANNUAL,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RunWearColors.TextPrimary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { buy(annual) },
                        enabled = annual != null && !purchasing,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                    ) {
                        Text(
                            MakeItMeStrings.U2I_CTA,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // The monthly product, as a quiet row (price from the
            // same Offering object, never a literal).
            PaywallPriceRow(
                label = monthly?.label ?: MakeItMeStrings.U2B_MONTHLY,
                enabled = monthly != null && !purchasing,
                onClick = { buy(monthly) }
            )

            if (purchasing) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = RunWearColors.PineAccent,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Completing your purchase…",
                        fontSize = 13.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
            }
            purchaseError?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                // WS-F: the per-run framing is DERIVED from the
                // displayed annual product (basis stated in the
                // line); until the offering resolves, the settled
                // fine print stands alone.
                text = annual?.let {
                    MakeItMeStrings.finePrint(it.formatted, it.amountMicros)
                } ?: MakeItMeStrings.U2C_FINE_PRINT,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = RunWearColors.TextSecondary
            )
        }
    }
}

private class PackagePrice(
    val pkg: com.revenuecat.purchases.Package,
    val label: String,
    val formatted: String = "",
    val amountMicros: Long = 0L
)

@Composable
private fun PaywallPriceRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RunWearColors.BgCard,
            contentColor = RunWearColors.TextPrimary
        )
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ============================================================================
// Settings section (spec §3, strings S1–S10) — the permanent home of the
// feature. Four states collapse to three modes here: A (stock), B (free,
// with the real allowance count), C (subscribed). Navigation out (upload
// flow, photos view, paywall) is the host's; deletion is handled inside
// via [MakeItMeDeleteDialog], and the S14 line shows only after the
// server has confirmed the erasure.
// ============================================================================

/**
 * WS-5 (D-14): what the Make It Me settings section presents in
 * each configuration, as row titles — the pure mirror the inventory
 * gate tests, so "the section emits something in every
 * configuration" is a tested fact, not a review finding. The
 * composable below renders exactly these rows (same string
 * constants, same order).
 */
object MakeItMeSettingsPresentation {
    fun rowTitles(makeItMeEnabled: Boolean, mode: MakeItMeMode): List<String> =
        if (!makeItMeEnabled) {
            // Flag OFF: the plain not-started row (frame 36) — the
            // section exists, the feature's doors stay shut.
            listOf(MakeItMeStrings.S2_STATE_A_ROW)
        } else when (mode) {
            MakeItMeMode.STOCK -> listOf(MakeItMeStrings.S2_STATE_A_ROW)
            // Gen-3 WS-D: status group (not a row title — composed
            // from FreeLooksMeter), then Your photos, then the
            // Premium offer card, then the destructive row last.
            MakeItMeMode.FREE -> listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.U2G_CARD_TITLE,
                MakeItMeStrings.S10_DELETE_ROW
            )
            MakeItMeMode.SUBSCRIBED -> listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.U2G_CARD_TITLE,
                MakeItMeStrings.S10_DELETE_ROW
            )
        }
}

@Composable
fun MakeItMeSettingsSection(
    viewModel: MakeItMeViewModel?,
    state: MakeItMeUiState,
    onOptInClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onDeleted: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletedNotice by remember { mutableStateOf(false) }

    // Gen-3 WS-D/X-9: Settings re-reads the profile on open (the
    // gen-2 Android section never did — its only reads were at
    // ViewModel init). The re-read refreshes the DELIVERED count;
    // truthfulness while a look is in flight comes from the
    // anyLookMaking input composed through FreeLooksMeter below,
    // not from this re-read.
    LaunchedEffect(Unit) {
        if (MakeItMeAvailability.ENABLED) viewModel?.refresh()
    }

    // The offer card's price, from the fetched store offering —
    // parameterized, never a literal (design §6).
    var offerAnnualFormatted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.mode) {
        if (state.mode == MakeItMeMode.FREE && viewModel != null) {
            offerAnnualFormatted = viewModel.offering()?.annual?.product?.price?.formatted
        }
    }

    Column {
        Text(
            text = MakeItMeStrings.S1_SECTION_HEADER,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))

        if (deletedNotice && state.mode == MakeItMeMode.STOCK) {
            Text(
                text = MakeItMeStrings.S14_POST_DELETE,
                fontSize = 13.sp,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // WS-5 (D-14): with the feature flag OFF the section still
        // renders — the plain not-started row the design specifies
        // (frame 36). What the flag keeps shut is the feature's
        // function (opt-in flow, photos, purchase, generation —
        // MakeItMeAvailability, consulted at those hosts), not the
        // section's existence. Before WS-5 this composable returned
        // early on the flag and the whole section — and everything
        // hosted inside it — silently vanished from the sheet.
        if (!MakeItMeAvailability.ENABLED) {
            MakeItMeSettingsRow(
                title = MakeItMeStrings.S2_STATE_A_ROW,
                sub = MakeItMeStrings.S2_STATE_A_SUB,
                onClick = onOptInClick
            )
            return@Column
        }

        when (state.mode) {
            MakeItMeMode.STOCK -> {
                MakeItMeSettingsRow(
                    title = MakeItMeStrings.S2_STATE_A_ROW,
                    sub = MakeItMeStrings.S2_STATE_A_SUB,
                    onClick = {
                        deletedNotice = false
                        onOptInClick()
                    }
                )
            }

            MakeItMeMode.FREE, MakeItMeMode.SUBSCRIBED -> {
                val subscribed = state.mode == MakeItMeMode.SUBSCRIBED

                // ── Status group (WS-D group 2): status line +
                // free-look meter composed as ONE block from the
                // single FreeLooksMeter seam — the block can never
                // contradict the flow state at the same moment.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RunWearColors.BgCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val block = FreeLooksMeter.block(
                            deliveredUsed = state.freeGenerationsUsed,
                            lookMaking = state.anyLookMaking,
                            subscribed = subscribed
                        )
                        Text(
                            text = block[0],
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = RunWearColors.TextPrimary
                        )
                        Text(
                            text = block[1],
                            fontSize = 12.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ── Your photos group (WS-D group 3).
                MakeItMeSettingsRow(
                    title = MakeItMeStrings.S6_PHOTOS_ROW,
                    sub = MakeItMeStrings.S6_PHOTOS_SUB,
                    onClick = onPhotosClick
                )
                Spacer(Modifier.height(14.dp))

                // ── Premium offer card (WS-D group 4): a legible
                // offer — the name "Premium", the parameterized
                // price with term, the settled two-benefit line,
                // and a real button opening the paywall. A
                // subscriber sees the subscribed state instead.
                val card = MakeItMeStrings.premiumOfferCard(
                    annualFormatted = offerAnnualFormatted,
                    subscribed = subscribed
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RunWearColors.BgCard,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RunWearColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = card.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = RunWearColors.TextPrimary
                        )
                        Text(
                            text = card.benefitLine,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = RunWearColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (subscribed) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onManageSubscriptionClick,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    MakeItMeStrings.S9_MANAGE_SUBSCRIPTION,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            card.priceLine?.let {
                                Text(
                                    text = it,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RunWearColors.TextPrimary,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onSubscribeClick,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
                            ) {
                                Text(
                                    card.ctaLabel ?: MakeItMeStrings.S27_GET_PREMIUM,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // GEN-3 WS-D ADDITION (Sol, Nov 4): while a PREMIUM
                // user's bank is still generating, the subscribed
                // state says so — with the SAME shared element the
                // home band renders (ConvergenceBand, resolved by
                // the same ConvergenceBandPolicy from the same
                // state; not a re-implementation — whatever count
                // treatment is ruled later applies to both places
                // at once; until then, the bar without a numeric
                // count per the final count ruling). The policy itself
                // gates it to subscribed + not-in-trial +
                // mid-convergence, so free users and completed
                // banks never see it here either. It sits in the
                // offer card's subscribed-state area, directly
                // beneath the card.
                val settingsBand = ConvergenceBandPolicy.content(
                    mode = state.mode,
                    coverage = state.bankCoverage,
                    inTrial = state.inTrial
                )
                if (settingsBand != null) {
                    Spacer(Modifier.height(14.dp))
                    ConvergenceBand(content = settingsBand)
                }

                // ── Destructive group (WS-D group 5): last and
                // visually separated from the offer, so no mis-tap
                // travels between them. Delete my data follows as
                // its own Settings section below.
                Spacer(Modifier.height(22.dp))
                MakeItMeSettingsRow(
                    title = MakeItMeStrings.S10_DELETE_ROW,
                    sub = null,
                    destructive = true,
                    onClick = { showDeleteDialog = true }
                )
            }
        }
    }

    if (showDeleteDialog && viewModel != null) {
        MakeItMeDeleteDialog(
            viewModel = viewModel,
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                showDeleteDialog = false
                deletedNotice = true
                onDeleted()
            }
        )
    }
}

/**
 * WS-5 (D-14): Delete-my-data as its OWN settings row (RW-16.30
 * decision 5a: the account-level erasure, offered in every mode —
 * a stock-mode user still has an account). It used to be composed
 * inside [MakeItMeSettingsSection], so it vanished whenever that
 * section emitted nothing; now Settings hosts it as a registered
 * section of its own, independent of the feature's flag and mode.
 */
@Composable
fun DeleteMyDataSettingsRow(
    viewModel: MakeItMeViewModel?,
    onDeleted: () -> Unit
) {
    var showDeleteDataDialog by remember { mutableStateOf(false) }
    var dataDeletedNotice by remember { mutableStateOf(false) }

    Column {
        if (dataDeletedNotice) {
            Text(
                text = MakeItMeStrings.S25_POST_DELETE_DATA,
                fontSize = 13.sp,
                color = RunWearColors.TextPrimary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        MakeItMeSettingsRow(
            title = MakeItMeStrings.S20_DELETE_DATA_ROW,
            sub = null,
            destructive = true,
            onClick = { showDeleteDataDialog = true }
        )
    }

    if (showDeleteDataDialog && viewModel != null) {
        MakeItMeDeleteDataDialog(
            viewModel = viewModel,
            onDismiss = { showDeleteDataDialog = false },
            onDeleted = {
                showDeleteDataDialog = false
                dataDeletedNotice = true
                onDeleted()
            }
        )
    }
}

@Composable
private fun MakeItMeSettingsRow(
    title: String,
    sub: String?,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else RunWearColors.TextPrimary
            )
            sub?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ============================================================================
// Delete confirmation (spec §3, strings S11–S14) — hosted by the
// Settings section; the S14 confirmation shows only after the server
// has confirmed the erasure.
// ============================================================================

@Composable
fun MakeItMeDeleteDialog(
    viewModel: MakeItMeViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    var deleting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(MakeItMeStrings.S11_DELETE_CONFIRM_TITLE) },
        text = {
            Column {
                Text(MakeItMeStrings.S12_DELETE_CONFIRM_BODY)
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = MakeItMeStrings.TRANSPORT_ERROR,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting,
                onClick = {
                    deleting = true
                    failed = false
                    viewModel.deletePhotos { ok ->
                        deleting = false
                        if (ok) onDeleted() else failed = true
                    }
                }
            ) {
                Text(
                    MakeItMeStrings.S13_DELETE_CONFIRM,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !deleting, onClick = onDismiss) {
                Text(MakeItMeStrings.S13_KEEP)
            }
        }
    )
}

// ============================================================================
// First-look moment (frame 34) — onboarding's end, on the fork's
// success path. The onboarding look counts as look 1 of 5. The ONE
// soft Premium ask lives here: the primary button returns to the
// outfit ("See My Outfit"); "Upgrade to Premium" is the quiet
// second door (Sol's Nov 4 wording for this surface). Shown once,
// never a modal again. The band carries the §9 pair verbatim; NO
// price appears anywhere in onboarding.
// ============================================================================

@Composable
fun FirstLookMoment(
    photoUrl: String?,
    simplePlace: String?,
    weatherLine: String?,
    onSeeOutfit: () -> Unit,
    onSeePremium: () -> Unit,
    /**
     * GEN-3 A-HERO (WS-B, X-1): the moment's presentation state,
     * resolved by [FirstLookPresentation] from the look status and
     * whether the generated photo is actually displayed. MAKING
     * shows the generating state ON the photo with honest copy —
     * no finished headline, no counter, no Premium band; READY is
     * the only state that may claim "You're in the photo now";
     * FAILED is the honest terminal state (no look was used).
     */
    state: FirstLookState = FirstLookState.READY
) {
    // Dismissing the ask IS continuing (design §9) — Back takes the
    // primary door, never traps the user at onboarding's end.
    androidx.activity.compose.BackHandler { onSeeOutfit() }

    // §1.4 reveal sequence: the photo crossfades in (400 ms), the
    // ONE success haptic fires as the photo lands (§1.7 — first
    // look ready = success, once), and the summary band settles
    // 120 ms after the photo lands. Neither button moves or
    // pulses.
    // GEN-3 A-HERO: the success half of the sequence (haptic + band)
    // is re-aimed at the generated photo's ACTUAL landing — it runs
    // when the presentation becomes READY, which may be after the
    // moment has been up in its MAKING state; it never runs for a
    // photo that has not landed.
    var revealed by remember { mutableStateOf(false) }
    var bandSettled by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state) {
        revealed = true
        if (state == FirstLookState.READY) {
            delay(OnboardingMotion.FIRST_LOOK_HAPTIC_AT_MS.toLong())
            haptic.performHapticFeedback(
                // §1.7's first-look confirm-class effect, named by the
                // map (WS-9 fix, P-2).
                com.runwear.app.ui.components.HapticSpec.CONFIRM
            )
            delay(OnboardingMotion.FIRST_LOOK_BAND_DELAY_AFTER_PHOTO_MS.toLong())
            bandSettled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            // Frame 34: the "Make It Me" script wordmark heads the moment.
            Text(
                text = "Make It Me",
                fontFamily = GreatVibesFontFamily,
                fontSize = 34.sp,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = MakeItMeStrings.firstLookHeading(simplePlace),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary
            )
            weatherLine?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = RunWearColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // The look itself — the designed skeleton (a box of the
        // outfit's own shape on the card token) until the photo is
        // known, then the 400 ms crossfade (§1.4: never a
        // placeholder flash, never a broken-image frame).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(RunWearColors.BgCard)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = revealed && photoUrl != null,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(
                        OnboardingMotion.FIRST_LOOK_PHOTO_CROSSFADE_MS,
                        easing = OnboardingMotion.EmphasizedEasing
                    )
                )
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // GEN-3 A-HERO (WS-B, X-1/X-2): while the look is being
            // made, the generating state sits ON the photo — the same
            // hero pill the home hero carries (WS-A(2) state (a)) —
            // over the photo actually serving (the bundled fallback,
            // WS-A(1)). The look/place requested is the one in view:
            // this moment only exists for it.
            if (state == FirstLookState.MAKING) {
                com.runwear.app.ui.components.HeroPill(
                    content = com.runwear.app.ui.components.HeroPillContent(
                        kind = com.runwear.app.ui.components.HeroPillKind.MAKING,
                        text = MakeItMeStrings.HERO_PILL_MAKING,
                        showSpinner = true
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 12.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            // GEN-3 A-HERO: the copy under the photo is state-honest.
            // READY alone may claim the finished state + the counter;
            // MAKING says what is happening (G1/G2 — with WS-A(1) the
            // photo under the pill is real, and G2 no longer claims
            // anything about a photo at all); FAILED is G5 + the
            // no-look-used truth.
            when (state) {
                FirstLookState.READY -> {
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_HEADLINE,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 25.sp,
                        color = RunWearColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_COUNT_LINE,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
                FirstLookState.MAKING -> {
                    Text(
                        text = MakeItMeStrings.G1_PROGRESS,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 25.sp,
                        color = RunWearColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = MakeItMeStrings.G2_PROGRESS_SUB,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
                FirstLookState.FAILED -> {
                    Text(
                        text = MakeItMeStrings.G5_LOOK_FAILED,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 25.sp,
                        color = RunWearColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_FAILED_SUB,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = RunWearColors.TextSecondary
                    )
                }
            }

            // The summary band — the ONE soft ask (§9's pair,
            // verbatim). It slides up and settles after the photo;
            // the primary button below never moves during the reveal.
            androidx.compose.animation.AnimatedVisibility(
                visible = bandSettled,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(
                        OnboardingMotion.FIRST_LOOK_BAND_SETTLE_MS,
                        easing = OnboardingMotion.EmphasizedEasing
                    )
                ) { height -> height / 2 } +
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(
                            OnboardingMotion.FIRST_LOOK_BAND_SETTLE_MS,
                            easing = OnboardingMotion.EmphasizedEasing
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(RunWearColors.BgCard)
                        .border(
                            1.dp,
                            RunWearColors.TextSecondary.copy(alpha = 0.3f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_BAND_HEADER,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        color = RunWearColors.PineAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_BAND_READINESS,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        color = RunWearColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = MakeItMeStrings.FIRST_LOOK_BAND_NO_CAP,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        color = RunWearColors.TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSeeOutfit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary)
            ) {
                Text(
                    MakeItMeStrings.FIRST_LOOK_PRIMARY,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            // GEN-3 A-HERO: the second door is part of the Premium
            // ask — withheld while the look is still being made and
            // on failure (FirstLookPresentation.showsPremiumBand);
            // the primary door is always available.
            if (FirstLookPresentation.showsPremiumBand(state)) {
                TextButton(
                    onClick = onSeePremium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        MakeItMeStrings.FIRST_LOOK_SECOND_DOOR,
                        color = RunWearColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ===========================================================================
// Delete-my-data confirmation (RW-16.30 decision 5a, strings S21–S24) —
// the same destructive pattern as the photos dialog, one level up:
// the safe action is the quiet button, the destructive one is labeled
// with the act itself, and success is claimed only on the server's
// receipt (the account_deleted confirmation), never optimistically.
// ===========================================================================

@Composable
fun MakeItMeDeleteDataDialog(
    viewModel: MakeItMeViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    var deleting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(MakeItMeStrings.S21_DELETE_DATA_CONFIRM_TITLE) },
        text = {
            Column {
                Text(MakeItMeStrings.S22_DELETE_DATA_CONFIRM_BODY)
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = MakeItMeStrings.TRANSPORT_ERROR,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting,
                onClick = {
                    deleting = true
                    failed = false
                    viewModel.deleteData { ok ->
                        deleting = false
                        if (ok) onDeleted() else failed = true
                    }
                }
            ) {
                Text(
                    MakeItMeStrings.S23_DELETE_DATA_CONFIRM,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !deleting, onClick = onDismiss) {
                Text(MakeItMeStrings.S24_DELETE_DATA_KEEP)
            }
        }
    )
}
