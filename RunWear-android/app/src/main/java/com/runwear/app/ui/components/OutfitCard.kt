package com.runwear.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import kotlinx.coroutines.delay

/**
 * PWA v2.7 aligned outfit card with staggered animation and press state.
 *
 * PWA CSS values:
 * - background: #1A1A1A (solid, NOT glass)
 * - hover/press: #262626 with translateX(4px)
 * - border-radius: 16px
 * - padding: 14px
 * - gap: 14px
 * - icon: 48x48, border-radius 14px
 * - animation: slideUp 0.4s with 50ms stagger
 */
@Composable
fun OutfitCard(
    item: ClothingItem,
    onClick: () -> Unit,
    animationDelay: Int = 0, // Stagger delay in ms (index * 50)
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(item.category)

    // Staggered animation state
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50L + animationDelay.toLong())
        visible = true
    }

    // Press state for interaction feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(400))
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .offset(x = if (isPressed) 4.dp else 0.dp) // translateX on press
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPressed) RunWearColors.BgCardLight  // #262626 on press
                    else RunWearColors.BgCard                  // #1A1A1A normal
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // We handle feedback via background color
                    onClick = onClick
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Emoji Icon - 48dp, border-radius 14dp per PWA v2.9
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.icon,
                    style = TextStyle(fontSize = 24.sp)
                )
            }

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.category.displayName,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = RunWearColors.TextSecondary
                    )
                )
            }

            // Chevron - 18dp per PWA
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View details",
                tint = RunWearColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Get the category accent color.
 */
fun getCategoryColor(category: ClothingCategory): Color = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
    ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
    ClothingCategory.HEAD -> RunWearColors.CategoryHead
    ClothingCategory.HANDS -> RunWearColors.CategoryHands
    ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
}

/**
 * Extension property for display-friendly category names.
 */
val ClothingCategory.displayName: String
    get() = when (this) {
        ClothingCategory.TOP_BASE -> "Base Layer"
        ClothingCategory.TOP_OUTER -> "Outer Layer"
        ClothingCategory.BOTTOM -> "Bottoms"
        ClothingCategory.HEAD -> "Head"
        ClothingCategory.HANDS -> "Hands"
        ClothingCategory.ACCESSORIES -> "Accessories"
    }

/**
 * Section containing outfit cards with staggered animation.
 * PWA CSS values: gap 10dp, bottom padding 60dp
 *
 * @param outfit The outfit recommendation containing items
 * @param onItemClick Handler when an item is clicked
 * @param modifier Modifier for the section
 */
@Composable
fun OutfitSection(
    outfit: com.runwear.shared.domain.model.OutfitRecommendation?,
    onItemClick: (ClothingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = outfit?.allItems ?: emptyList()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp) // 10dp gap per PWA
    ) {
        // Section header
        Text(
            text = "${items.size} items for your run",
            style = MaterialTheme.typography.titleMedium,
            color = RunWearColors.TextSecondary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Outfit cards with staggered animation
        items.forEachIndexed { index, item ->
            OutfitCard(
                item = item,
                onClick = { onItemClick(item) },
                animationDelay = index * 50 // 50ms stagger
            )
        }
    }
}

/**
 * PWA v2.7 aligned PRO TIP card.
 *
 * PWA CSS values:
 * - background: linear-gradient(135deg, rgba(0, 121, 107, 0.15), rgba(0, 121, 107, 0.05))
 * - border: 1px solid rgba(0, 121, 107, 0.2)
 * - border-radius: 16px
 * - padding: 18px
 */
@Composable
fun TipsSection(
    tips: List<String>,
    modifier: Modifier = Modifier
) {
    if (tips.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x2600796B),  // rgba(0, 121, 107, 0.15)
                        Color(0x0D00796B)   // rgba(0, 121, 107, 0.05)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                width = 1.dp,
                color = Color(0x3300796B),  // rgba(0, 121, 107, 0.2)
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp)
    ) {
        // Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            // Lightbulb Icon in teal circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RunWearColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
            }

            // "PRO TIP" label
            Text(
                text = "PRO TIP",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunWearColors.PrimaryLight,
                    letterSpacing = 0.5.sp
                )
            )
        }

        // Tip texts
        tips.forEachIndexed { index, tip ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = tip,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = RunWearColors.TextSecondary
                )
            )
        }
    }
}
