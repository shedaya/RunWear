package com.runwear.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PWA v2.9 Glass morphism button styling:
 * - background: rgba(255, 255, 255, 0.12) = 12% white
 * - backdrop-filter: blur(20px) - not available in Compose, simulated with alpha
 * - border: 1px solid rgba(255, 255, 255, 0.15) = 15% white
 * - border-radius: 100px (pill shape)
 * - padding: 10px 16px
 * - font-size: 14px, font-weight: 600
 */
private val GlassBackground = Color.White.copy(alpha = 0.12f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)

/**
 * Glass morphism button with translucent background and subtle border.
 * Used for floating controls over hero images.
 * This is the pill-shaped version with optional text.
 *
 * @param icon The icon to display
 * @param text Optional text label (omit for icon-only button)
 * @param onClick Click handler
 * @param modifier Modifier for the button
 */
@Composable
fun GlassButton(
    icon: ImageVector,
    text: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(100.dp), // Pill shape
        color = GlassBackground,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            text?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * PWA v2.9 Glass morphism icon-only circular button.
 * Used for Share and Settings buttons in hero header.
 *
 * CSS spec:
 * - width: 44px, height: 44px
 * - border-radius: 50% (circle)
 * - Same glass morphism styling as pill button
 */
@Composable
fun GlassButtonIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = GlassBackground,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp)
        )
    }
}
