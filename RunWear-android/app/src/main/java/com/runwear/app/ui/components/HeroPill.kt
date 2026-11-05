package com.runwear.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.makeitme.ScaffoldRenderPolicy

/**
 * GEN-3 A-HERO (WS-A(2), 2026-11-04): THE hero pill — one component,
 * one position (bottom of the hero image), state-driven. It replaces
 * the RW-26 scaffold badge (which appeared in ZERO frames of the
 * gen-2 walkthrough, census X-14) and the scroll-content progress
 * line (X-8: the generating state sat in the sheet's scroll, not on
 * the photo it belonged to). The states, per Sol's Nov 4 ruling:
 *
 *  (a) MAKING — a personal photo is generating for the cell in view:
 *      "Making this you…" with a spinner, over the fallback photo
 *      (WS-A(1) guarantees a photo is under it wherever one is
 *      servable). UNCHANGED by the Nov 4 design delta (approved as
 *      excellent).
 *  (b) SCAFFOLD — the user's own banked photo served for close-by
 *      weather: the Make It Me script mark + "Your photo" — NOTHING
 *      MORE (GEN-3 DESIGN DELTA, Sol's Nov 4 mockup review: the
 *      "made for close-by weather" line is OUT, found confusing).
 *  (c) EXACT_FREE — the exact personal photo is displayed, free
 *      tier: the script mark + the look count ("1 of 5 looks") plus
 *      a "Get unlimited" action that opens the Premium paywall.
 *  (d) PREMIUM — the exact personal photo is displayed, subscribed:
 *      the script mark + "Premium" + a lock glyph.
 *
 * A stock serve carries NO pill (the design's §9 silence stands),
 * and neither does a failed look (its durable line + retry live in
 * the sheet, unchanged).
 */
enum class HeroPillKind { MAKING, SCAFFOLD, EXACT_FREE, PREMIUM }

data class HeroPillContent(
    val kind: HeroPillKind,
    val text: String,
    /** The tappable trailing action (EXACT_FREE's "Get unlimited"). */
    val actionText: String? = null,
    val showSpinner: Boolean = false,
    /** GEN-3 DESIGN DELTA: the Make It Me script mark leads states (b)(c)(d). */
    val showScriptMark: Boolean = false,
    /** GEN-3 DESIGN DELTA: the lock glyph trails the PREMIUM state. */
    val showLock: Boolean = false
)

object HeroPillPolicy {

    fun resolve(
        lookStatus: PersonalLookStatus,
        serveKind: PersonalServeKind,
        heroIsPersonal: Boolean,
        mode: MakeItMeMode,
        freeGenerationsUsed: Int
    ): HeroPillContent? {
        // (a) Generating for the cell in view outranks the serve
        // states: whatever photo is up (fallback, pool, or an earlier
        // personal photo of another cell), the pill says his is being
        // made — the X-2 fix (the state must be visible for the look
        // actually requested, on the photo).
        if (lookStatus == PersonalLookStatus.Making) {
            return HeroPillContent(
                kind = HeroPillKind.MAKING,
                text = MakeItMeStrings.HERO_PILL_MAKING,
                showSpinner = true
            )
        }
        if (!heroIsPersonal) return null
        return when (serveKind) {
            // (b) Scaffold serve: script mark + "Your photo", nothing more.
            PersonalServeKind.SCAFFOLD -> HeroPillContent(
                kind = HeroPillKind.SCAFFOLD,
                text = ScaffoldRenderPolicy.homeBadge(PersonalServeKind.SCAFFOLD)
                    ?: MakeItMeStrings.HERO_PILL_SCAFFOLD,
                showScriptMark = true
            )
            PersonalServeKind.EXACT -> when (mode) {
                // (d) Subscribed: script mark + Premium status + lock glyph.
                MakeItMeMode.SUBSCRIBED -> HeroPillContent(
                    kind = HeroPillKind.PREMIUM,
                    text = MakeItMeStrings.HERO_PILL_PREMIUM,
                    showScriptMark = true,
                    showLock = true
                )
                // (c) Free tier: script mark + the real look count + the door.
                MakeItMeMode.FREE -> HeroPillContent(
                    kind = HeroPillKind.EXACT_FREE,
                    text = MakeItMeStrings.heroPillLookCount(freeGenerationsUsed),
                    actionText = MakeItMeStrings.HERO_PILL_GET_UNLIMITED,
                    showScriptMark = true
                )
                MakeItMeMode.STOCK -> null
            }
            // A gate-failed cell serving stock quietly: no pill (§9).
            PersonalServeKind.STOCK_QUIET -> null
        }
    }
}

/**
 * The pill's rendering: the same quiet dark-translucent capsule the
 * scaffold badge used (black 45%, PhotoInk text), extended with the
 * optional spinner, the optional Make It Me script mark (Great
 * Vibes, states (b)(c)(d) per the Nov 4 design delta), the optional
 * trailing action, and the PREMIUM lock glyph.
 */
@Composable
fun HeroPill(
    content: HeroPillContent,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = Color.Black.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (content.showSpinner) {
                CircularProgressIndicator(
                    color = com.runwear.app.ui.theme.PhotoInk,
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            if (content.showScriptMark) {
                Text(
                    text = MakeItMeStrings.HERO_PILL_SCRIPT_MARK,
                    fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
                    fontSize = 15.sp,
                    color = com.runwear.app.ui.theme.PhotoInk
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = content.text,
                fontSize = 12.sp,
                color = com.runwear.app.ui.theme.PhotoInk
            )
            if (content.showLock) {
                Spacer(Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = com.runwear.app.ui.theme.PhotoInk,
                    modifier = Modifier.size(11.dp)
                )
            }
            if (content.actionText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = content.actionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.runwear.app.ui.theme.PhotoInk,
                    modifier = Modifier.clickable { onAction() }
                )
            }
        }
    }
}
