package com.runwear.app.makeitme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors

/**
 * GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4): the
 * home-sheet band a SUBSCRIBED user sees while his bank of looks is
 * still converging — mock frame 19's seat (in the sheet scroll,
 * below the outfit items), with Sol's final copy:
 *
 *   Heading: "Making your looks"
 *   Line:    "We're generating your outfits across all running
 *             conditions, so it will be ready when you need it."
 *   Progress (plain words, real counts): "14 of your 24 looks are
 *             ready" + a simple progress bar. No bare fractions,
 *             no "14 of 24 ready" labelling.
 *
 * The band exists only while convergence is genuinely in progress:
 * subscribed mode, a successful coverage read, and ready < total.
 * When the bank is complete (or counts are unknown) there is no
 * band — never an invented number.
 */
data class ConvergenceBandContent(
    val heading: String,
    val line: String,
    val progressText: String,
    val fraction: Float
)

object ConvergenceBandPolicy {

    fun content(
        mode: MakeItMeMode,
        coverage: PersonalHeroService.BankCoverage?
    ): ConvergenceBandContent? {
        if (mode != MakeItMeMode.SUBSCRIBED) return null
        val cov = coverage ?: return null
        if (cov.total <= 0 || cov.ready >= cov.total) return null
        return ConvergenceBandContent(
            heading = MakeItMeStrings.BAND_MAKING_HEADING,
            line = MakeItMeStrings.BAND_MAKING_LINE,
            progressText = MakeItMeStrings.bandMakingProgress(cov.ready, cov.total),
            fraction = cov.ready.toFloat() / cov.total.toFloat()
        )
    }
}

/** The band's rendering — the sheet card language of the upsell card. */
@Composable
fun ConvergenceBand(
    content: ConvergenceBandContent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunWearColors.BgCard)
            .border(1.dp, RunWearColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = content.heading,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = content.line,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = RunWearColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { content.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = RunWearColors.PineAccent,
            trackColor = RunWearColors.TextSecondary.copy(alpha = 0.25f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content.progressText,
            fontSize = 13.sp,
            color = RunWearColors.TextPrimary
        )
    }
}
