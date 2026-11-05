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
 *  (b) SCAFFOLD — GEN-6 (RW-34): SILENT. The owner ruled Nov 5,
 *      2026 that the shipped "Your photo" scaffold label is not an
 *      approved pill; a banked photo standing in carries no pill.
 *  (c) EXACT_FREE — the exact personal photo is displayed, free
 *      tier: the script mark + the look count ("1 of 5 looks") plus
 *      a "Get unlimited" action that opens the Premium paywall.
 *  (d) PREMIUM — the exact personal photo is displayed, subscribed:
 *      the script mark + "Premium" + a lock glyph.
 *  (e) EXHAUSTED — free tier, all 5 looks used: the script mark +
 *      the honest used-up count + "Get unlimited" as the pill's
 *      main (green) message; the whole pill taps to Premium.
 *
 * A stock serve carries NO pill (the design's §9 silence stands),
 * and neither does a failed look (its durable line + retry live in
 * the sheet, unchanged).
 */
enum class HeroPillKind { MAKING, EXACT_FREE, PREMIUM, EXHAUSTED }

data class HeroPillContent(
    val kind: HeroPillKind,
    val text: String,
    /** The tappable trailing action (EXACT_FREE's "Get unlimited"). */
    val actionText: String? = null,
    val showSpinner: Boolean = false,
    /** GEN-3 DESIGN DELTA: the Make It Me script mark leads states (b)(c)(d). */
    val showScriptMark: Boolean = false,
    /** GEN-3 DESIGN DELTA: the lock glyph trails the PREMIUM state. */
    val showLock: Boolean = false,
    /** GEN-5 C-4 AMENDED: the small green upsell line on the
     *  FREE-tier generating pill only ("Get it instantly next
     *  time"); its tap goes to the same Premium offer as the free
     *  pill's "Get unlimited". Null on every other state. */
    val upsellText: String? = null,
    /** GEN-5: the whole pill is the tap target (EXHAUSTED state). */
    val wholePillAction: Boolean = false
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
            // GEN-5 (RW-28): EVERY pill status leads with the Make
            // It Me script — the generating pill included (A-13/B-14).
            // C-4 AMENDED: the free-tier generating state also
            // carries the one small green upsell line; a subscriber's
            // generating state stays clean.
            return HeroPillContent(
                kind = HeroPillKind.MAKING,
                text = MakeItMeStrings.HERO_PILL_MAKING,
                showSpinner = true,
                showScriptMark = true,
                upsellText = if (mode == MakeItMeMode.FREE) {
                    MakeItMeStrings.HERO_PILL_MAKING_UPSELL
                } else {
                    null
                }
            )
        }
        // GEN-5 (Sol, Nov 5 ~06:30 CST): EXHAUSTED — a free user who
        // has used all 5 free looks gets an upsell pill status of
        // its own: script lead, the honest used-up count, "Get
        // unlimited" as the MAIN message, whole pill tappable to the
        // Premium offer. Driven by the same single meter as every
        // other surface, so it flips at exactly 5.
        if (mode == MakeItMeMode.FREE && freeGenerationsUsed >= 5) {
            return HeroPillContent(
                kind = HeroPillKind.EXHAUSTED,
                text = MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT,
                actionText = MakeItMeStrings.HERO_PILL_GET_UNLIMITED,
                showScriptMark = true,
                wholePillAction = true
            )
        }
        if (!heroIsPersonal) return null
        return when (serveKind) {
            // (b) GEN-6 WAVE A (RW-34): scaffold serve is SILENT.
            // The owner ruled Nov 5, 2026 that the shipped "Your
            // photo" label is NOT an approved pill — a banked photo
            // standing in carries no claim label at all. (This
            // supersedes design-spec §3.6's older scaffold text.)
            PersonalServeKind.SCAFFOLD -> null
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
        modifier = if (content.wholePillAction) {
            modifier.clickable { onAction() }
        } else {
            modifier
        },
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
            if (content.upsellText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = content.upsellText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = com.runwear.app.ui.theme.PineAccentOnDark,
                    modifier = Modifier.clickable { onAction() }
                )
            }
            if (content.actionText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = content.actionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    // GEN-6 (RW-34): on the EXHAUSTED pill the
                    // upsell is the MAIN message — it takes the
                    // green accent; elsewhere the action stays ink.
                    color = if (content.kind == HeroPillKind.EXHAUSTED) {
                        com.runwear.app.ui.theme.PineAccentOnDark
                    } else {
                        com.runwear.app.ui.theme.PhotoInk
                    },
                    modifier = Modifier.clickable { onAction() }
                )
            }
        }
    }
}
