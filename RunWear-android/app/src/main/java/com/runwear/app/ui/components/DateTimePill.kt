package com.runwear.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Glass morphism date/time selector pill.
 * Shows current date and time as tappable segments.
 *
 * @param dateTime The currently selected date/time
 * @param onDateClick Handler when date section is tapped
 * @param onTimeClick Handler when time section is tapped
 * @param modifier Modifier for the pill
 */
@Composable
fun DateTimePill(
    dateTime: LocalDateTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = dateTime.toLocalDate() == LocalDate.now()
    val isTomorrow = dateTime.toLocalDate() == LocalDate.now().plusDays(1)

    val dateText = when {
        isToday -> "Today"
        isTomorrow -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }

    val timeText = dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = RunWearColors.GlassBackground.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, RunWearColors.GlassBorder.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date button (highlighted)
            Surface(
                onClick = onDateClick,
                shape = RoundedCornerShape(100.dp),
                color = RunWearColors.GlassBackgroundHover
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = "Select date",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Time button
            Surface(
                onClick = onTimeClick,
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = "Select time",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Separate Date button matching HTML design.
 * Primary colored background when isPrimary is true.
 */
@Composable
fun DateButton(
    dateTime: LocalDateTime,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isToday = dateTime.toLocalDate() == LocalDate.now()
    val isTomorrow = dateTime.toLocalDate() == LocalDate.now().plusDays(1)

    val dateText = when {
        isToday -> "Today"
        isTomorrow -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isPrimary) RunWearColors.Primary else Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = "Select date",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Separate Time button matching HTML design.
 * Glass background style.
 */
@Composable
fun TimeButton(
    dateTime: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = "Select time",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
