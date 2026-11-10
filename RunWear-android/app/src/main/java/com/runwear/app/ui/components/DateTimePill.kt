package com.runwear.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Glass morphism date/time selector pill - PWA v2.9 style.
 * Shows current date and time as tappable segments.
 * Includes "Now" shortcut when viewing a different time.
 *
 * PWA CSS reference:
 * - background: rgba(255, 255, 255, 0.12)
 * - backdrop-filter: blur(12px)
 * - border: 1px solid rgba(255, 255, 255, 0.15)
 * - .active section: rgba(255, 255, 255, 0.15)
 *
 * All values are framed in the SELECTED LOCATION's timezone: [dateTime] is a
 * location-local wall time and [referenceNow] is the location's current time.
 * Nothing here reads the phone's clock — when a different city is selected,
 * "Today" and "Now" belong to that city, not to the device.
 *
 * @param dateTime The currently selected date/time (location-local)
 * @param referenceNow The current date/time at the selected location
 * @param isNowSelected Whether the first-class NOW state is active; the time
 * segment then reads "Now" instead of a clock time
 * @param onDateClick Handler when date section is tapped
 * @param onTimeClick Handler when time section is tapped
 * @param onNowClick Handler when "Now" shortcut is tapped (optional)
 * @param modifier Modifier for the pill
 */
@Composable
fun DateTimePill(
    dateTime: LocalDateTime,
    referenceNow: LocalDateTime,
    isNowSelected: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNowClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * RW-25: the pill's home moved from the photo to the sheet. On the
     * sheet it renders FLAT — tonal surface fill, ink text, no glass,
     * no border — while the default keeps the original PWA glass
     * treatment for any photo-side use.
     */
    onSheet: Boolean = false
) {
    val isToday = dateTime.toLocalDate() == referenceNow.toLocalDate()
    val isTomorrow = dateTime.toLocalDate() == referenceNow.toLocalDate().plusDays(1)

    val dateText = when {
        isToday -> "Today"
        isTomorrow -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }

    val timeText = if (isNowSelected) "Now"
        else dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))

    // PWA glass morphism: rgba(255, 255, 255, 0.12) = 12% white
    val glassBackground = Color.White.copy(alpha = 0.12f)
    // PWA border: rgba(255, 255, 255, 0.15) = 15% white
    val glassBorder = Color.White.copy(alpha = 0.15f)
    // PWA active section: rgba(255, 255, 255, 0.15) = 15% white
    val glassActiveSection = Color.White.copy(alpha = 0.15f)
    // Teal accent for "Now" shortcut
    val tealAccent = RunWearColors.Primary.copy(alpha = 0.25f)
    val tealText = com.runwear.app.ui.theme.PineAccentOnDark

    // RW-25 flat-on-sheet tokens: tonal fill from the M3 scheme, ink
    // from the palette, the "Now" accent in the pine family.
    val containerColor = if (onSheet) MaterialTheme.colorScheme.surfaceVariant else glassBackground
    // WS-1 (Part 1 §1.1): the day/time strip's selection settles in
    // 120 ms — the active section's color crossfades at the micro
    // token instead of snapping when the selection state changes.
    val activeSectionColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (onSheet) MaterialTheme.colorScheme.surface else glassActiveSection,
        animationSpec = androidx.compose.animation.core.tween(
            HeroMotionTokens.DAY_STRIP_SETTLE_MS
        ),
        label = "dayStripSettle"
    )
    val primaryInk = if (onSheet) RunWearColors.TextPrimary else com.runwear.app.ui.theme.PhotoInk
    val secondaryInk = if (onSheet) RunWearColors.TextSecondary else com.runwear.app.ui.theme.PhotoInk.copy(alpha = 0.7f)
    val dividerColor = if (onSheet) {
        RunWearColors.TextSecondary.copy(alpha = 0.3f)
    } else {
        Color.White.copy(alpha = 0.2f)
    }
    val nowAccentBackground = if (onSheet) {
        RunWearColors.Primary.copy(alpha = 0.16f)
    } else {
        tealAccent
    }
    val nowAccentText = if (onSheet) RunWearColors.PineAccent else tealText

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = containerColor,
        border = if (onSheet) null else BorderStroke(1.dp, glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date button (highlighted/active)
            Surface(
                onClick = onDateClick,
                shape = RoundedCornerShape(100.dp),
                color = activeSectionColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = com.runwear.app.i18n.AppStrings.selectDate(),
                        tint = primaryInk,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = dateText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryInk,
                        // Overlay audit (Oct 30): hero overlay labels
                        // never wrap/stack in narrow panes.
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Divider - PWA: rgba(255, 255, 255, 0.2)
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(dividerColor)
            )

            // Time button — in the NOW state it reads "Now" in the teal accent
            // (the selection IS the location's current time; a clock reading
            // here would invite reading it in the phone's frame).
            Surface(
                onClick = onTimeClick,
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = com.runwear.app.i18n.AppStrings.selectTime(),
                        tint = if (isNowSelected) nowAccentText else secondaryInk,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = timeText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isNowSelected) nowAccentText else secondaryInk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // "Now" shortcut - only visible when an explicit time is selected
            AnimatedVisibility(
                visible = !isNowSelected && onNowClick != null,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                Surface(
                    onClick = { onNowClick?.invoke() },
                    shape = RoundedCornerShape(100.dp),
                    color = nowAccentBackground,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = com.runwear.app.i18n.AppStrings.jumpToNow(),
                            tint = nowAccentText,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = nowAccentText
                        )
                    }
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
                contentDescription = com.runwear.app.i18n.AppStrings.selectDate(),
                tint = com.runwear.app.ui.theme.PhotoInk,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = com.runwear.app.ui.theme.PhotoInk,
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
                contentDescription = com.runwear.app.i18n.AppStrings.selectTime(),
                tint = com.runwear.app.ui.theme.PhotoInk,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = com.runwear.app.ui.theme.PhotoInk,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
