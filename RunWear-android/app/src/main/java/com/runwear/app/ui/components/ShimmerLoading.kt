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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.BebasNeueFontFamily
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
 * The status line for the no-content startup wait, derived from what the
 * load is actually blocked on. [locationName] is the state default
 * "Getting location..." until a fix is adopted and named.
 */
fun loadingStatusText(hasLocationPermission: Boolean, locationName: String): String = when {
    !hasLocationPermission -> "Getting your location…"
    locationName == "Getting location..." -> "Finding your location…"
    else -> "Loading your forecast…"
}

/**
 * Branded loading state for the no-content first load. The shimmer
 * skeleton's palette (Surface #1A1A1A / SurfaceElevated #262626 over
 * Background #0A0A0A) has almost no contrast, so on a real display the
 * first-ever load read as a dead black screen for the full GPS + network
 * wait. This state is unmistakably alive and on brand instead: wordmark,
 * spinner, and a line saying what the app is waiting for.
 */
@Composable
fun BrandedLoadingScreen(
    statusText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Wordmark, same treatment as the onboarding header
            Text(
                text = buildAnnotatedString {
                    append("Run")
                    withStyle(SpanStyle(color = RunWearColors.Primary)) {
                        append("Wear")
                    }
                },
                fontFamily = BebasNeueFontFamily,
                fontSize = 44.sp,
                letterSpacing = 1.sp,
                color = RunWearColors.TextPrimary
            )

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(color = RunWearColors.Primary)

            Spacer(Modifier.height(16.dp))

            Text(
                text = statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextSecondary,
                textAlign = TextAlign.Center
            )
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
