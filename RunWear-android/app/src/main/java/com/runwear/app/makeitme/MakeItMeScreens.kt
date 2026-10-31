package com.runwear.app.makeitme

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors

// ============================================================================
// Onboarding fork (spec §2.2) — the final onboarding step, shown after the
// first real recommendation. The recommendation's photo stays visible at
// the top: it is the subject of the sentence.
// ============================================================================

@Composable
fun MakeItMeForkScreen(
    heroImageUrl: String?,
    onUseMyPhotos: () -> Unit,
    onUseStandardPhotos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // The first recommendation's photo — "that's a model in your
        // outfit photo."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (heroImageUrl != null) {
                AsyncImage(
                    model = heroImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = MakeItMeStrings.F1_HEADLINE,
                fontFamily = BebasNeueFontFamily,
                fontSize = 30.sp,
                letterSpacing = 0.5.sp,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MakeItMeStrings.F2_LEAD,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color.White.copy(alpha = 0.65f)
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
                color = Color.White.copy(alpha = 0.5f)
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
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
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
            color = Color.White.copy(alpha = 0.6f),
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
                    containerColor = Color.White.copy(alpha = 0.07f),
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

private enum class UploadStep { CONSENT, PHOTOS, SUBMITTING }

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
    val context = LocalContext.current
    var step by remember { mutableStateOf(UploadStep.CONSENT) }
    var attested by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val photos = remember { mutableStateListOf<PickedPhoto>() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        uris.take(3).forEach { uri ->
            if (photos.none { it.uri == uri }) photos.add(PickedPhoto(uri))
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
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        when (step) {
            UploadStep.CONSENT -> {
                Text(
                    text = MakeItMeStrings.C1A_CONSENT_TITLE,
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp,
                    color = RunWearColors.TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = MakeItMeStrings.C1B_CONSENT_BODY,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.75f)
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
                    Text(text = it, fontSize = 13.sp, color = Color(0xFFFF8A80))
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        working = true
                        errorText = null
                        viewModel.recordConsent(gender) { ok ->
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
                    Text(MakeItMeStrings.C1E_NOT_NOW, color = Color.White.copy(alpha = 0.7f))
                }
            }

            UploadStep.PHOTOS, UploadStep.SUBMITTING -> {
                Text(
                    text = MakeItMeStrings.C2A_CAPTURE_TITLE,
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp,
                    color = RunWearColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = MakeItMeStrings.C2B_CAPTURE_GUIDANCE,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (replace) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = MakeItMeStrings.S7_REPLACE_NOTE,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = 0.6f)
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
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.07f),
                            contentColor = RunWearColors.TextPrimary
                        )
                    ) {
                        Text("Add photos", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                val accepted = photos.filter { it.jpegBytes != null }
                val anyChecking = photos.any { it.checking }
                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, fontSize = 13.sp, color = Color(0xFFFF8A80))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        step = UploadStep.SUBMITTING
                        errorText = null
                        viewModel.submitPhotos(
                            photos = accepted.map { it.jpegBytes!! },
                            replace = replace,
                            todaysCell = todaysCell
                        ) { ok ->
                            if (ok) {
                                onFinished()
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
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
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
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                when {
                    picked.checking -> CircularProgressIndicator(
                        color = RunWearColors.Primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    picked.rejection != null -> Text(
                        text = PhotoQualityPolicy.stringFor(picked.rejection!!),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFFF8A80)
                    )
                    else -> Text(
                        text = "Looks good.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove photo",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
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
    var urls by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.loadReferenceUrls { result ->
            urls = result
            loaded = true
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
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = MakeItMeStrings.S6_PHOTOS_ROW,
            fontFamily = BebasNeueFontFamily,
            fontSize = 28.sp,
            letterSpacing = 0.5.sp,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))
        if (!loaded) {
            CircularProgressIndicator(color = RunWearColors.Primary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                urls.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = MakeItMeStrings.S7_REPLACE_NOTE,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
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
// Generation progress line (spec §2.4, strings G1/G2) — sits with the
// hero while a personal generation is in flight. The recommendation is
// never blocked: the standard photo stays up until the verified
// personal image lands.
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
            color = RunWearColors.Primary,
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
                color = Color.White.copy(alpha = 0.55f)
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
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = MakeItMeStrings.U1A_EYEBROW,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = RunWearColors.PrimaryLight
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MakeItMeStrings.U1B_HEADLINE,
            fontFamily = BebasNeueFontFamily,
            fontSize = 24.sp,
            letterSpacing = 0.5.sp,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MakeItMeStrings.U1C_BODY,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Color.White.copy(alpha = 0.65f)
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
            Text(MakeItMeStrings.U1E_NOT_NOW, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ============================================================================
// Subscription sheet (spec §5.3, strings U2). Prices come from the
// RevenueCat offering when it resolves; the spec strings are the
// fallback so the sheet is never blank.
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakeItMePaywallSheet(
    viewModel: MakeItMeViewModel,
    onDismiss: () -> Unit,
    onPurchased: () -> Unit
) {
    val context = LocalContext.current
    var monthly by remember { mutableStateOf<PackagePrice?>(null) }
    var annual by remember { mutableStateOf<PackagePrice?>(null) }
    var purchasing by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val offering = viewModel.offering()
        monthly = offering?.monthly?.let {
            PackagePrice(it, it.product.price.formatted + " / month")
        }
        annual = offering?.annual?.let {
            PackagePrice(it, it.product.price.formatted + " / year — about 16% less than paying monthly")
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RunWearColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = MakeItMeStrings.U2A_SHEET_TITLE,
                fontFamily = BebasNeueFontFamily,
                fontSize = 26.sp,
                letterSpacing = 0.5.sp,
                color = RunWearColors.TextPrimary
            )
            Spacer(Modifier.height(16.dp))

            PaywallPriceRow(
                label = monthly?.label ?: MakeItMeStrings.U2B_MONTHLY,
                enabled = monthly != null && !purchasing,
                onClick = { buy(monthly) }
            )
            Spacer(Modifier.height(10.dp))
            PaywallPriceRow(
                label = annual?.label ?: MakeItMeStrings.U2B_ANNUAL,
                enabled = annual != null && !purchasing,
                onClick = { buy(annual) }
            )

            if (purchasing) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = RunWearColors.Primary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Completing your purchase…",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            purchaseError?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, fontSize = 13.sp, color = Color(0xFFFF8A80))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = MakeItMeStrings.U2C_FINE_PRINT,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

private class PackagePrice(val pkg: com.revenuecat.purchases.Package, val label: String)

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
            containerColor = Color.White.copy(alpha = 0.08f),
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

@Composable
fun MakeItMeSettingsSection(
    viewModel: MakeItMeViewModel,
    state: MakeItMeUiState,
    onOptInClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onDeleted: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletedNotice by remember { mutableStateOf(false) }

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
                Text(
                    text = MakeItMeStrings.S3_STATUS,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RunWearColors.TextPrimary
                )
                Text(
                    text = if (state.mode == MakeItMeMode.SUBSCRIBED) {
                        MakeItMeStrings.S5_STATE_C_SUB
                    } else {
                        MakeItMeStrings.s4FreeUsed(state.freeGenerationsUsed)
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                MakeItMeSettingsRow(
                    title = MakeItMeStrings.S6_PHOTOS_ROW,
                    sub = MakeItMeStrings.S6_PHOTOS_SUB,
                    onClick = onPhotosClick
                )
                Spacer(Modifier.height(6.dp))
                if (state.mode == MakeItMeMode.FREE) {
                    MakeItMeSettingsRow(
                        title = MakeItMeStrings.S8_STATE_B_SUBSCRIBE_ROW,
                        sub = MakeItMeStrings.S8_STATE_B_SUBSCRIBE_SUB,
                        onClick = onSubscribeClick
                    )
                } else {
                    MakeItMeSettingsRow(
                        title = MakeItMeStrings.S9_MANAGE_SUBSCRIPTION,
                        sub = null,
                        onClick = onManageSubscriptionClick
                    )
                }
                Spacer(Modifier.height(6.dp))
                MakeItMeSettingsRow(
                    title = MakeItMeStrings.S10_DELETE_ROW,
                    sub = null,
                    destructive = true,
                    onClick = { showDeleteDialog = true }
                )
            }
        }
    }

    if (showDeleteDialog) {
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
                color = if (destructive) Color(0xFFFF8A80) else RunWearColors.TextPrimary
            )
            sub?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
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
                        color = Color(0xFFFF8A80)
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
                    color = Color(0xFFFF8A80)
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
