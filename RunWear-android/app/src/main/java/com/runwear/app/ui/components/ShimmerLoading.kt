package com.runwear.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearColors

/**
 * Shimmer effect brush for loading placeholders.
 * Creates a smooth left-to-right shimmer animation.
 */
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        RunWearColors.SurfaceElevated,
        RunWearColors.Surface.copy(alpha = 0.5f),
        RunWearColors.SurfaceElevated
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 500, translateAnimation - 500),
        end = Offset(translateAnimation, translateAnimation)
    )
}

/**
 * Shimmer loading placeholder for hero section.
 * Shows animated skeleton while loading.
 */
@Composable
fun HeroShimmerPlaceholder(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
    ) {
        // Full shimmer background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )

        // Content placeholders
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            // Date/Time buttons placeholder
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
            }

            // "Feels Like" label placeholder
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(Modifier.height(8.dp))

            // Temperature placeholder
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )

            Spacer(Modifier.height(12.dp))

            // Weather info placeholder
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(Modifier.height(16.dp))

            // Weather pills placeholder
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
            }
        }
    }
}

/**
 * Shimmer loading placeholder for outfit cards.
 */
@Composable
fun OutfitCardShimmer(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon placeholder
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RunWearColors.Surface)
            )

            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RunWearColors.Surface)
                )
                Spacer(Modifier.height(4.dp))
                // Subtitle placeholder
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RunWearColors.Surface)
                )
            }
        }
    }
}

/**
 * Multiple shimmer cards for loading state.
 */
@Composable
fun OutfitListShimmer(
    itemCount: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) {
            OutfitCardShimmer()
        }
    }
}
