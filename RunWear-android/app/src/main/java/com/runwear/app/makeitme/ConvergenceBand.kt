package com.runwear.app.makeitme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors

/**
 * GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4): the
 * home-sheet band a PREMIUM user sees while his bank of looks is
 * still converging — mock frame 19's seat (in the sheet scroll,
 * below the outfit items), with Sol's final copy:
 *
 *   Heading: "Making your looks"
 *   Line:    "We're generating your outfits across all running
 *             conditions, so it will be ready when you need it."
 *   Plus the animated spinner and a progress bar — WITHOUT any
 *   numeric count (COUNT RULED, Sol Nov 4 — FINAL: the count
 *   wording is withdrawn for good; the bar's fraction still
 *   comes from the real banking state, only the number is
 *   withheld. Settled sentence + spinner + unlabelled bar
 *   only, in both placements — home and Settings).
 *
 * PREMIUM CONTEXT ONLY: banking is Premium-only behaviour — free
 * users get 5 on-demand looks and in-trial users likewise have no
 * bank, so the band renders only for paying subscribers (mode
 * SUBSCRIBED and not in a trial period), only while convergence
 * is genuinely in progress (a successful coverage read with
 * ready < total). When the bank is complete (or the state is
 * unknown) there is no band.
 *
 * GEN-3 ROUND 2 (Sol, Nov 4): the band carries the SAME animated
 * spinner as the generating hero pill — the band represents active
 * generation and must visibly say so. Same spinner component, same
 * 12dp / 1.5dp geometry; the tint is the band's own accent (the
 * pill's PhotoInk is for photo surfaces — on the sheet card it
 * would be invisible in light theme).
 */
data class ConvergenceBandContent(
    val heading: String,
    val line: String,
    /** The bar's fraction, from the real banking state (no count shown). */
    val fraction: Float
)

object ConvergenceBandPolicy {

    fun content(
        mode: MakeItMeMode,
        coverage: PersonalHeroService.BankCoverage?,
        inTrial: Boolean
    ): ConvergenceBandContent? {
        // Premium context only — never free, never in-trial.
        if (mode != MakeItMeMode.SUBSCRIBED) return null
        if (inTrial) return null
        val cov = coverage ?: return null
        if (cov.total <= 0 || cov.ready >= cov.total) return null
        return ConvergenceBandContent(
            heading = MakeItMeStrings.BAND_MAKING_HEADING,
            line = MakeItMeStrings.BAND_MAKING_LINE,
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
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The generating hero pill's spinner (GEN-3 round 2).
            CircularProgressIndicator(
                color = RunWearColors.PineAccent,
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = content.heading,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = RunWearColors.TextPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = content.line,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = RunWearColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        // The progress bar — fraction from the real banking state,
        // NO numeric count (COUNT RULED final, Sol Nov 4).
        LinearProgressIndicator(
            progress = { content.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = RunWearColors.PineAccent,
            trackColor = RunWearColors.TextSecondary.copy(alpha = 0.25f)
        )
    }
}
