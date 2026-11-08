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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.runwear.app.R
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.ClothingCategory
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.service.ClothingThumbnailService
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
    gender: GenderPreference = GenderPreference.UNISEX,
    modifier: Modifier = Modifier,
    /**
     * Wide-layout presentation (the PWA's grid-cell card): the text
     * block centers and the subtitle is the item's description, exactly
     * as the PWA card renders (its text centers via the button UA
     * style; its subtitle is `desc || category`). The default list
     * presentation — left-aligned, category subtitle — is unchanged
     * and remains what COMPACT shows.
     */
    centeredText: Boolean = false
) {
    val categoryColor = getCategoryColor(item.category)
    val context = LocalContext.current
    // Bundled asset first (the set is closed and ships with the app — no
    // network wait); the remote URL is the fallback only, if the asset
    // fails to load.
    val thumbnailAssetUri = remember(item, gender) {
        ClothingThumbnailService.getThumbnailAssetUri(item, gender)
    }
    // RW-144 ICON SWAP SEAM: in the LIGHT theme the row's tile
    // loads the Treatment L set through the SAME service seam
    // (assets/thumbnails-light/<key>-<gender>.png — a file-for-file
    // swap of that directory needs no code change). A failed light
    // asset falls through to the standard asset, then the remote
    // URL, exactly like the standard asset's own failure path.
    val isDarkTheme = RunWearColors.isDarkTheme
    val lightThumbnailAssetUri = remember(item, gender, isDarkTheme) {
        if (isDarkTheme) null
        else ClothingThumbnailService.getLightThumbnailAssetUri(item, gender)
    }
    val thumbnailUrl = remember(item, gender) {
        ClothingThumbnailService.getThumbnailUrl(item, gender)
    }
    var assetFailed by remember(item, gender) { mutableStateOf(false) }
    var lightAssetFailed by remember(item, gender) { mutableStateOf(false) }
    val thumbnailModel = when {
        lightThumbnailAssetUri != null && !lightAssetFailed -> lightThumbnailAssetUri
        thumbnailAssetUri != null && !assetFailed -> thumbnailAssetUri
        else -> thumbnailUrl
    }

    // Track thumbnail load state for fallback
    var thumbnailLoaded by remember { mutableStateOf(false) }

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
                // GEN-5 (RW-29 cream map): the design's outfit
                // rows are PLAIN ROWS on the sheet — no card fill
                // of their own; only the garment tile carries the
                // card fill + hairline. The press state washes the
                // row with the hairline tint instead.
                .background(
                    if (isPressed) RunWearColors.Hairline
                    else Color.Transparent
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
            // Thumbnail container — GEN-5 (cream map): the
            // design's 44dp garment tile, the one element of the
            // row that carries the card fill + a hairline border.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RunWearColors.GarmentTile)
                    .border(1.dp, RunWearColors.Hairline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Category SVG icon (shown as fallback or while thumbnail loads)
                // Matches web PWA: SVG category icon as base layer, no emoji
                if (!thumbnailLoaded) {
                    Icon(
                        painter = painterResource(id = getCategoryIconRes(item.category)),
                        contentDescription = null,
                        tint = RunWearColors.Silhouette,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // AI-generated thumbnail (bundled asset, remote fallback)
                if (thumbnailModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailModel)
                            .crossfade(300)
                            .build(),
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .graphicsLayer { alpha = if (thumbnailLoaded) 1f else 0f },
                        onSuccess = { thumbnailLoaded = true },
                        onError = {
                            if (!lightAssetFailed && lightThumbnailAssetUri != null &&
                                thumbnailModel == lightThumbnailAssetUri
                            ) {
                                lightAssetFailed = true // fall to the standard asset
                            } else if (!assetFailed && thumbnailAssetUri != null) {
                                assetFailed = true // retry once from the remote URL
                            } else {
                                thumbnailLoaded = false
                            }
                        }
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (centeredText) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = item.displayName,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.TextPrimary
                    ),
                    textAlign = if (centeredText) TextAlign.Center else TextAlign.Start
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    // GEN-5 (RW-28 item 30, A-24): the row's second
                    // line is the item's settled one-line NOTE
                    // ("Lightweight, breathable, maximum airflow") in
                    // BOTH presentations — the bare category word
                    // ("Base Layer") the gen-4 list showed carried no
                    // information. Falls back to the category word
                    // only for an item with no note (the PWA's
                    // `desc || category` rule, now on both layouts).
                    text = item.description.ifBlank { item.category.displayName },
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = RunWearColors.TextDescription
                    ),
                    textAlign = if (centeredText) TextAlign.Center else TextAlign.Start,
                    maxLines = 1
                )
            }

            // External link icon (matching PWA v3.12 - direct affiliate link)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Shop item",
                tint = RunWearColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Get the category accent color.
 */
fun getCategoryColor(category: ClothingCategory): Color = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> RunWearColors.CategoryTop
    ClothingCategory.BOTTOM -> RunWearColors.CategoryBottom
    ClothingCategory.HEAD -> RunWearColors.CategoryHead
    ClothingCategory.HANDS -> RunWearColors.CategoryHands
    ClothingCategory.ACCESSORIES -> RunWearColors.CategoryAccessories
}

/**
 * Get the SVG category icon drawable (ported from web PWA categoryIcons).
 * Replaces the emoji fallback for deterministic, on-brand rendering.
 */
fun getCategoryIconRes(category: ClothingCategory): Int = when (category) {
    ClothingCategory.TOP_BASE, ClothingCategory.TOP_MID, ClothingCategory.TOP_OUTER -> R.drawable.ic_category_top
    ClothingCategory.BOTTOM -> R.drawable.ic_category_bottom
    ClothingCategory.HEAD -> R.drawable.ic_category_head
    ClothingCategory.HANDS -> R.drawable.ic_category_hands
    ClothingCategory.ACCESSORIES -> R.drawable.ic_category_accessories
}

/**
 * Extension property for display-friendly category names.
 */
val ClothingCategory.displayName: String
    get() = when (this) {
        ClothingCategory.TOP_BASE -> "Base Layer"
        ClothingCategory.TOP_MID -> "Mid Layer"
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
 * @param gender Gender preference for thumbnail selection
 * @param modifier Modifier for the section
 */
@Composable
fun OutfitSection(
    outfit: com.runwear.shared.domain.model.OutfitRecommendation?,
    onItemClick: (ClothingItem) -> Unit,
    gender: GenderPreference = GenderPreference.UNISEX,
    modifier: Modifier = Modifier
) {
    val items = outfit?.allItems ?: emptyList()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp) // 10dp gap per PWA
    ) {
        // Section header — GEN-6 WAVE C (RW-54): the settled
        // string lives in ONE place (OutfitHeaderCopy) so the
        // rendered header and any other header can never diverge
        // again; this composable's copy was the dead duplicate.
        Text(
            text = OutfitHeaderCopy.header(items.size),
            style = MaterialTheme.typography.titleMedium,
            color = RunWearColors.TextSecondary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Outfit cards with staggered animation
        items.forEachIndexed { index, item ->
            OutfitCard(
                item = item,
                onClick = { onItemClick(item) },
                animationDelay = index * 50, // 50ms stagger
                gender = gender
            )
        }
    }
}

/**
 * PWA-aligned Pro Tip card: shows first tip, tap to expand the rest.
 *
 * PWA values:
 * - background: linear-gradient(135deg, rgba(0, 121, 107, 0.15), rgba(0, 121, 107, 0.05))
 * - border: 1px solid rgba(0, 121, 107, 0.2)
 * - border-radius: 16px, padding: 18px
 * - label: "Pro Tip" (title case), tap toggles additional tips
 */
/**
 * GEN-5 (RW-29 COR-8 / B-20): the engine authors its tips WITH a
 * leading emoji (the 🌡️/☀️ class). iOS strips it at its bridge;
 * Android rendered it raw — the app's settled surfaces carry no
 * emoji in body copy. The strip happens HERE, at the Android
 * render seam, so the shared engine text stays untouched.
 */
internal fun stripTipEmoji(tip: String): String =
    tip.replace(
        Regex("^[\\u2600-\\u27BF\\uFE0F\\u200D\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+\\s*"),
        ""
    )

@Composable
fun TipsSection(
    tips: List<String>,
    modifier: Modifier = Modifier
) {
    // GEN-5 (COR-8): every tip renders emoji-free (see above).
    val displayTips = tips.map { stripTipEmoji(it) }
    if (displayTips.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // GEN-5 (RW-29 cream map): the Pro Tip card is a
            // system card — sheet fill + hairline — not the
            // legacy teal gradient wash it kept from the
            // pre-redesign palette.
            .background(RunWearColors.BgCard)
            .border(
                width = 1.dp,
                color = RunWearColors.Hairline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { if (displayTips.size > 1) expanded = !expanded }
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

            // "Pro Tip" label (title case, matches web PWA)
            Text(
                text = "Pro Tip",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunWearColors.PineAccent,
                    letterSpacing = 0.5.sp
                )
            )

            // Expand indicator when there are more tips
            if (displayTips.size > 1) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = RunWearColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // First tip always visible
        Text(
            text = displayTips[0],
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = RunWearColors.TextSecondary
            )
        )

        // Additional tips revealed on expand
        if (expanded) {
            displayTips.drop(1).forEach { tip ->
                Spacer(modifier = Modifier.height(8.dp))
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
}

/** GEN-6 WAVE C (RW-54): the ONE outfit-header wording —
 * "N items for your run" (design-spec §5). Every rendered header
 * resolves through here. */
object OutfitHeaderCopy {
    fun header(itemCount: Int): String = "$itemCount items for your run"
}

