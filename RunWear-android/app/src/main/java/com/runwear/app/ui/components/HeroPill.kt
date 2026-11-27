package com.runwear.app.ui.components

import com.runwear.engine.MR
import com.runwear.engine.*
import dev.icerock.moko.resources.compose.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
    /** GEN-5: the whole pill is the tap target (EXHAUSTED state). */
    val wholePillAction: Boolean = false
    // RW-171 / RW-174 (Nov 14, 2026): there is NO upsell field.
    // The GEN-5 C-4 "Get it instantly next time" line that the
    // FREE-tier generating state used to carry here — and the
    // pill rendered as a stacked second line — is REMOVED by
    // Sol's RW-171 ruling: the pill is ONE line in every state
    // and the upsell copy leaves the pill entirely.
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
            // RW-171 / RW-174: the generating state is ONE line on
            // every tier — the GEN-5 C-4 free-tier upsell line that
            // used to stack beneath it is removed entirely.
            return HeroPillContent(
                kind = HeroPillKind.MAKING,
                text = MakeItMeStrings.HERO_PILL_MAKING,
                showSpinner = true,
                showScriptMark = true
            )
        }
        // GEN-5 (Sol, Nov 5 ~06:30 CST): EXHAUSTED — a free user who
        // has used all 5 free looks gets an upsell pill status of
        // its own: script lead, the honest used-up count, "Get
        // unlimited" as the MAIN message, whole pill tappable to the
        // Premium offer. Driven by the same single meter as every
        // other surface, so it flips at exactly 5.
        if (mode == MakeItMeMode.FREE &&
            freeGenerationsUsed >= com.runwear.app.makeitme.MakeItMeUpsellRules.FREE_ALLOWANCE
        ) {
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
            // RW-130: a SIMILAR serve (ladder tier 2) is silent for
            // the same reason — it is not the exact photo, and every
            // exact-serve label on it would be a false claim.
            PersonalServeKind.SIMILAR -> null
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
 *
 * GEN-6 DA-15(b): the pill is width-capped to the space its host
 * actually offers (BoxWithConstraints + widthIn) and script-led
 * states get extra leading padding — in Sol's recording the
 * capsule's shape clip cut the script's opening swash at the
 * pill's left edge ("› Make It Me").
 *
 * RW-171 / RW-174 (Nov 14, 2026) SUPERSEDES DA-15(b)'s ellipsis:
 * the main text is ONE line and is never truncated — it does not
 * ellipsize (Sol's RW-171 device evidence: the generating pill's
 * primary line truncated while a stacked upsell line was spent
 * beneath it; the upsell line is removed entirely).
 */
@Composable
fun HeroPill(
    content: HeroPillContent,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        Surface(
            modifier = (if (content.wholePillAction) {
                Modifier.clickable { onAction() }
            } else {
                Modifier
            }).widthIn(max = maxWidth)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = Color(0xFF140E08).copy(alpha = 0.25f),
                    spotColor = Color(0xFF140E08).copy(alpha = 0.25f)
                ),
            shape = RoundedCornerShape(100.dp),
            // RW-144: the pill's material is the SHEET's — card
            // cream #FDFBF6 in light, warm charcoal #2A251E in
            // dark (the palette's BgCard in both modes) — because
            // its seat now straddles the hero/sheet boundary in
            // the v8 frames' form, with ink text on it.
            color = com.runwear.app.ui.theme.RunWearColors.BgCard
        ) {
            // RW-171 / RW-174 (Nov 14, 2026) SUPERSEDES WT-4:
            // the pill is ONE ROW in every state, the free-tier
            // generating state included. WT-4's stacked second
            // line (the "Get it instantly next time" upsell) is
            // removed — Sol's device evidence showed the primary
            // line truncating with an ellipsis while the second
            // line was spent beneath it. The primary line is
            // never truncated now (softWrap off, no ellipsis):
            // one line, whole.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = if (content.showScriptMark) 16.dp else 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 8.dp
                )
            ) {
                if (content.showSpinner) {
                    CircularProgressIndicator(
                        color = com.runwear.app.ui.theme.RunWearColors.TextPrimary,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (content.showScriptMark) {
                    Text(
                        text = stringResource(MR.strings.mim_hero_pill_script_mark),
                        fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
                        fontSize = 19.sp,
                        color = com.runwear.app.ui.theme.RunWearColors.TextPrimary
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = content.text,
                    fontSize = 13.5.sp,
                    color = com.runwear.app.ui.theme.RunWearColors.TextSecondary,
                    // RW-171: ONE line, never truncated — no
                    // ellipsis overflow and no wrap; the line
                    // renders whole or the pill grows with its
                    // host's granted width.
                    maxLines = 1,
                    softWrap = false
                )
                if (content.showLock) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = com.runwear.app.ui.theme.RunWearColors.TextPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
                if (content.actionText != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = content.actionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        // GEN-6 (RW-34; RW-68): the "Get unlimited"
                        // action takes the approved green accent on
                        // BOTH free kinds that carry it — EXHAUSTED
                        // (where it is the main message) and EXACT_FREE
                        // (the owner's approved table: the look count
                        // "with a green 'Get unlimited' button", Nov 5,
                        // 2026; the iOS half of this wave fixed its
                        // identical white rendering to the same accent).
                        // actionText exists only on those two kinds.
                        // RW-144 FIX (parent ruling, Nov 8, 2026): the
                        // accent is now the THEME's pine —
                        // RunWearColors.PineAccent is #175E4C in light
                        // mode and #5CC79F in dark, exactly the approved
                        // v8 frames (frame A pine / frame B accent);
                        // the flat PineAccentOnDark in both themes was
                        // the deviation this corrects.
                        color = if (content.kind == HeroPillKind.EXHAUSTED ||
                            content.kind == HeroPillKind.EXACT_FREE
                        ) {
                            com.runwear.app.ui.theme.RunWearColors.PineAccent
                        } else {
                            com.runwear.app.ui.theme.RunWearColors.TextPrimary
                        },
                        modifier = Modifier.clickable { onAction() }
                    )
                }
            }
        }
    }
}
