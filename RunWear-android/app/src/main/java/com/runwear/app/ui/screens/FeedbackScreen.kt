package com.runwear.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.feedback.FeedbackBundleFactory
import com.runwear.app.feedback.FeedbackCategory
import com.runwear.app.ui.theme.BebasNeueFontFamily
import com.runwear.app.ui.theme.RunWearColors

/**
 * The feedback screen (spec §1). The diagnostic bundle is the product: the
 * user reads exactly what will be sent, in plain labels, before the send
 * button exists for them to tap. Disclosure copy is the spec's §4.3 text,
 * verbatim.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    bundle: FeedbackBundleFactory.Bundle,
    preselectedCategory: FeedbackCategory?,
    onSend: (category: FeedbackCategory, message: String, replyEmail: String?, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(preselectedCategory) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bundleExpanded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) } // true = sent, false = saved for retry

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onDismiss, enabled = !sending) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = RunWearColors.TextPrimary
                )
            }
            Text(
                text = "SEND FEEDBACK",
                fontFamily = BebasNeueFontFamily,
                fontSize = 24.sp,
                letterSpacing = 0.48.sp,
                color = RunWearColors.TextPrimary
            )
        }

        if (result != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (result == true) {
                        "Thanks. That goes straight to the team."
                    } else {
                        "We could not send that just now, so it is saved on your phone. " +
                            "The app will send it when a connection is available. Nothing is lost."
                    },
                    fontSize = 16.sp,
                    color = RunWearColors.TextPrimary
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Tell us what happened. What you write goes to the small team that builds " +
                    "this app, along with a summary of what the app was doing when you wrote it. " +
                    "You can read that summary below before you send it. We never send your " +
                    "health data or your location.",
                fontSize = 14.sp,
                color = RunWearColors.TextSecondary
            )

            Spacer(Modifier.height(24.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeedbackCategory.entries.forEach { entry ->
                    val isSelected = entry == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) RunWearColors.Primary else RunWearColors.BgCardLight)
                            .clickable { category = entry }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = entry.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else RunWearColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("Say what happened in your own words.") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = feedbackFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (optional)") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = feedbackFieldColors()
            )
            Text(
                text = "Email if you want a reply. Otherwise this goes in anonymously.",
                fontSize = 12.sp,
                color = RunWearColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "This is everything the report includes. Nothing else leaves your phone.",
                fontSize = 13.sp,
                color = RunWearColors.TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bundleExpanded = !bundleExpanded }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "What we're sending with this",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RunWearColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (bundleExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (bundleExpanded) "Hide the summary" else "Show the summary",
                    tint = RunWearColors.Primary
                )
            }
            AnimatedVisibility(visible = bundleExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RunWearColors.Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bundle.displayFields.forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = RunWearColors.TextMuted,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value,
                                fontSize = 12.sp,
                                color = RunWearColors.TextPrimary,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val canSend = category != null && message.trim().length >= MIN_MESSAGE_CHARS && !sending
            Button(
                onClick = {
                    val selected = category ?: return@Button
                    sending = true
                    onSend(selected, message, email.ifBlank { null }) { sentNow ->
                        sending = false
                        result = sentNow
                    }
                },
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Send feedback", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            if (category == null || message.trim().length < MIN_MESSAGE_CHARS) {
                Text(
                    text = "Pick a category and write a sentence or two, and the button turns on.",
                    fontSize = 12.sp,
                    color = RunWearColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun feedbackFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RunWearColors.Primary,
    unfocusedBorderColor = RunWearColors.BgCardLight,
    focusedTextColor = RunWearColors.TextPrimary,
    unfocusedTextColor = RunWearColors.TextPrimary,
    cursorColor = RunWearColors.Primary,
    focusedContainerColor = RunWearColors.Surface,
    unfocusedContainerColor = RunWearColors.Surface,
    focusedLabelColor = RunWearColors.Primary,
    unfocusedLabelColor = RunWearColors.TextMuted,
    focusedPlaceholderColor = RunWearColors.TextMuted,
    unfocusedPlaceholderColor = RunWearColors.TextMuted
)

private const val MIN_MESSAGE_CHARS = 10

/**
 * The rating moment (spec Addendum 2): a neutral card at a proven-value
 * moment with two equal doors — feedback or the native rating flow — and an
 * equal-weight way out. There is deliberately no screening question: the user
 * chooses the door, we do not route them.
 */
@Composable
fun RatingMomentDialog(
    onSendFeedback: () -> Unit,
    onRate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunWearColors.Surface,
        title = {
            Text(
                text = "How is RunWear working for you?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSendFeedback,
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Send feedback", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onRate,
                    colors = ButtonDefaults.buttonColors(containerColor = RunWearColors.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Rate RunWear", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Not now", color = RunWearColors.TextPrimary)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
