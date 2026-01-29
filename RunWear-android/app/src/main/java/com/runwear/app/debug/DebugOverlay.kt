package com.runwear.app.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug overlay that floats on top of the app.
 * Shows a small bug icon that expands to a feedback form.
 */
@Composable
fun DebugOverlay(
    uiState: MainUiState,
    currentScreen: String = "Main",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var showStateInfo by remember { mutableStateOf(false) }

    // Log screen changes
    LaunchedEffect(currentScreen) {
        DebugManager.log("Navigation", "Screen: $currentScreen")
    }

    // Log state changes
    LaunchedEffect(uiState.weather, uiState.outfit, uiState.heroImageUrl) {
        uiState.weather?.let {
            DebugManager.log("Weather", "Temp: ${it.temperature}°, Feels: ${it.feelsLike}°, Code: ${it.weatherCode}")
        }
        uiState.outfit?.let {
            DebugManager.log("Outfit", "${it.allItems.size} items: ${it.allItems.map { i -> i.name }}")
        }
        uiState.heroImageUrl?.let {
            DebugManager.log("HeroImage", "URL: ${it.take(80)}...")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Floating Bug Button (bottom-left)
        if (!isExpanded) {
            FloatingActionButton(
                onClick = { isExpanded = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(48.dp),
                containerColor = Color(0xFFFF5722),
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Debug",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Expanded Feedback Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = Color(0xFFFF5722),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Debug Feedback",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = { isExpanded = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Current State Summary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF262626))
                            .clickable { showStateInfo = !showStateInfo }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "📍 $currentScreen | ${uiState.locationName}",
                                color = Color(0xFF4DB6AC),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                buildString {
                                    append("🌡️ ")
                                    uiState.weather?.let {
                                        append("${it.feelsLike.toInt()}° ${it.weatherCode.icon}")
                                    } ?: append("--")
                                    append(" | 👕 ")
                                    append("${uiState.outfit?.allItems?.size ?: 0} items")
                                    append(" | 🖼️ ")
                                    append(if (uiState.heroImageUrl != null) "loaded" else "none")
                                },
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            // Expandable state details
                            AnimatedVisibility(visible = showStateInfo) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(color = Color(0xFF333333))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        buildString {
                                            appendLine("Gender: ${uiState.genderPreference}")
                                            appendLine("Comfort: ${uiState.comfortPreference}")
                                            appendLine("Unit: ${uiState.temperatureUnit}")
                                            appendLine("Loading: ${uiState.isLoading}")
                                            appendLine("Error: ${uiState.error ?: "none"}")
                                            uiState.heroImageUrl?.let {
                                                appendLine("Hero: ${it.takeLast(50)}")
                                            }
                                        },
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Text(
                                "tap to ${if (showStateInfo) "hide" else "show"} details",
                                color = Color(0xFF666666),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Feedback Input
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = {
                            Text(
                                "Describe what happened or what's wrong...",
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF5722),
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Color(0xFFFF5722)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Send Button
                    Button(
                        onClick = {
                            // Add context to feedback
                            val fullFeedback = buildString {
                                appendLine("=== USER FEEDBACK ===")
                                appendLine("Screen: $currentScreen")
                                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                                appendLine()
                                appendLine("Message:")
                                appendLine(feedbackText)
                            }

                            DebugManager.log("UserFeedback", feedbackText.take(100))
                            DebugManager.shareDebugReport(context, uiState, fullFeedback)
                            feedbackText = ""
                            isExpanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = feedbackText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Send Debug Report", fontWeight = FontWeight.SemiBold)
                    }

                    // Quick Actions
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickFeedbackChip("🐛 Bug", Modifier.weight(1f)) {
                            feedbackText = "[BUG] "
                        }
                        QuickFeedbackChip("💡 Suggestion", Modifier.weight(1f)) {
                            feedbackText = "[SUGGESTION] "
                        }
                        QuickFeedbackChip("❓ Question", Modifier.weight(1f)) {
                            feedbackText = "[QUESTION] "
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFeedbackChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF333333))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}
