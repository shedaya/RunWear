package com.runwear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.shared.domain.model.GenderPreference

/**
 * PWA v2.9 aligned gender toggle component.
 *
 * Spec:
 * - Two options only: "Male" / "Female" (TEXT labels, not icons)
 * - Neither selected by default (backend = UNISEX)
 * - TOGGLEABLE: tap selected option again to deselect (returns to UNISEX)
 * - Container: background #262626, border-radius 22px, padding 3px, gap 2px
 * - Option: min-width 50px, height 32px, border-radius 16px
 * - Font: 12px, weight 600
 * - Selected: background #00796B (Primary), text white
 * - Unselected: transparent, text #B3B3B3 (TextSecondary)
 */
@Composable
fun GenderToggle(
    selectedGender: GenderPreference,
    onGenderSelected: (GenderPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(RunWearColors.BgCardLight) // #262626
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Male option
        GenderOption(
            label = "Male",
            isSelected = selectedGender == GenderPreference.MALE,
            onClick = {
                // Toggle behavior: if already selected, deselect to UNISEX
                if (selectedGender == GenderPreference.MALE) {
                    onGenderSelected(GenderPreference.UNISEX)
                } else {
                    onGenderSelected(GenderPreference.MALE)
                }
            }
        )

        // Female option
        GenderOption(
            label = "Female",
            isSelected = selectedGender == GenderPreference.FEMALE,
            onClick = {
                // Toggle behavior: if already selected, deselect to UNISEX
                if (selectedGender == GenderPreference.FEMALE) {
                    onGenderSelected(GenderPreference.UNISEX)
                } else {
                    onGenderSelected(GenderPreference.FEMALE)
                }
            }
        )
    }
}

@Composable
private fun GenderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(min = 50.dp) // min-width: 50px
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) RunWearColors.Primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, // 600
                color = if (isSelected) Color.White else RunWearColors.TextSecondary
            )
        )
    }
}
