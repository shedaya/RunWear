package com.runwear.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import kotlinx.coroutines.delay

/**
 * New design outfit card with category color accent, icon, and staggered animation.
 *
 * Features:
 * - Colored left border indicating category
 * - Material icon with category-tinted background
 * - Slide-in animation with configurable delay for staggering
 * - Dark surface background matching hero design
 *
 * @param item The clothing item to display
 * @param onClick Handler when card is tapped (opens shop sheet)
 * @param animationDelay Delay in ms before animation starts (for staggering)
 * @param modifier Modifier for the card
 */
@Composable
fun OutfitCard(
    item: ClothingItem,
    onClick: () -> Unit,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        visible = true
    }

    val categoryColor = getCategoryColor(item.category)
    val categoryIcon = getCategoryIcon(item.category)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(400))
    ) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = RunWearColors.SurfaceElevated
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category colored left border
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(categoryColor)
                )

                Spacer(Modifier.width(12.dp))

                // Icon with category color background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.TextPrimary
                    )
                    Text(
                        text = item.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = RunWearColors.TextSecondary
                    )
                }

                // Chevron
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = RunWearColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Get the appropriate Material icon for a clothing category.
 */
fun getCategoryIcon(category: ClothingCategory): ImageVector = when (category) {
    ClothingCategory.TOP_BASE -> Icons.Outlined.Checkroom
    ClothingCategory.TOP_OUTER -> Icons.Outlined.AcUnit // Jacket/layer icon
    ClothingCategory.BOTTOM -> Icons.Outlined.FitnessCenter // Legs/workout
    ClothingCategory.HEAD -> Icons.Outlined.Face
    ClothingCategory.HANDS -> Icons.Outlined.PanTool
    ClothingCategory.ACCESSORIES -> Icons.Outlined.Visibility // Sunglasses etc.
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
 * Tips section for running advice.
 */
@Composable
fun TipsSection(
    tips: List<String>,
    modifier: Modifier = Modifier
) {
    if (tips.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Tips",
            style = MaterialTheme.typography.titleMedium,
            color = RunWearColors.TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        tips.forEach { tip ->
            TipCard(tip = tip)
        }
    }
}

@Composable
private fun TipCard(
    tip: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RunWearColors.SurfaceElevated
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "💡",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = RunWearColors.TextPrimary
            )
        }
    }
}
