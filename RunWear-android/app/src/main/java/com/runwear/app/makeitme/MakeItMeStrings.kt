package com.runwear.app.makeitme

/**
 * RW-14 "Make It Me" copy deck — spec §7 of
 * ~/workspace/release-prep/marketing/runwear-rw14-ux-identity-spec-2026-10-31.md,
 * verbatim. Strings are NOT edited silently: copy changes amend the spec's
 * §7 by dated note first (spec rule). PROPOSAL strings (P1's first-swap
 * caption) are deliberately ABSENT — they are not in the build until Sol
 * approves them.
 */
object MakeItMeStrings {

    // F — Onboarding fork (spec §2.2)
    const val F1_HEADLINE = "See yourself in every outfit."
    // F2 amended 2026-11-03 (coordinator ruling, RW-27 WS-9 fix):
    // the RW-14 deck lead paragraph is REPLACED by frame 32's
    // sub-headline, verbatim from the frozen final design package —
    // the line the design carries in this slot. The iOS WS-9 fix
    // lane made the identical swap; the fork now leads with the
    // headline, this line, the two choices and the closing line —
    // nothing else (and no free-looks promise sentence: see F4).
    const val F2_LEAD = "Your recommendations, with you in the photo."
    const val F3_OPTION_A_TITLE = "Make It Me"
    // F4 amended 2026-11-03 (coordinator ruling, RW-27 WS-10 fix):
    // the trailing free-looks promise sentence is REMOVED. The
    // frozen final design package carries that sentence only on
    // frame 28's promise row (and a paywall proof row); frame 32's
    // fork carries none — this matches the design and iOS verbatim.
    // GEN-5 note: a wave-B edit rewrote this line to a one-photo
    // claim; the gate caught it — no settlement changes the fork's
    // description of the reference-photo intake (1 to 3 photos),
    // and the gen-4 wording stands.
    const val F4_OPTION_A_BODY = "Upload 1 to 3 photos of yourself, and RunWear makes your outfit photos from them — starting with the one you're looking at."
    const val F5_OPTION_A_BUTTON = "Use my photos"
    const val F6_OPTION_B_TITLE = "Use the standard photos"
    const val F7_OPTION_B_BODY = "Keep the photo library RunWear matches to your outfit and your weather. You can switch to Make It Me any time in Settings."
    const val F8_OPTION_B_BUTTON = "Use standard photos"
    const val F9_FOOTER = "Either way, your recommendations don't change — only the photo does."

    // C — Consent, attestation, capture (spec §2.3)
    const val C1A_CONSENT_TITLE = "Before you add photos"
    const val C1B_CONSENT_BODY = "RunWear will upload your photos to our server. They're used for one thing only: making pictures of you wearing your recommended outfits.\n\nYour photos are never used to train AI models. They're never shown to other users, never added to RunWear's standard photo library, and never shared.\n\nPictures made from your photos are kept only for you, so your outfits are ready when you open the app.\n\nYou can delete your photos and every picture made from them at any time: Settings → Make It Me → Delete my photos. Deleting is permanent and takes effect right away."
    const val C1C_ATTESTATION = "These photos are of me, and I am 18 or older."
    const val C1D_CONTINUE = "Continue"
    const val C1E_NOT_NOW = "Not now"
    const val C2A_CAPTURE_TITLE = "Add a photo of you"
    // GEN-5 (RW-28 item 18): one-photo, face-only guidance — the
    // old "outfit needs a body to land on" line is retired with the
    // multi-photo intake it described.
    const val C2B_CAPTURE_GUIDANCE = "One photo of just your face: look at the camera, good light, no sunglasses. Your look is made from your face."
    // C2D–C2G added Oct 31, 2026 (RW-16.14, Sol's directives — spec §7
    // amended by dated note): the full-body guidance (guidance only,
    // never a validation rule) and the camera/library source choice.
    // C2D is verbatim-identical to the iOS deck (MakeItMeCopy).
    const val C2D_FULL_BODY_GUIDANCE = "Photos of you running work best. Include one full-body photo in plain athletic wear, head to shoes, with just you in the frame — full-length outfits like tights and jackets come out right when your whole body is in the photo. Photos with other people in them can't be used."
    const val C2E_SOURCE_DIALOG_TITLE = "Add a photo"
    const val C2F_TAKE_PHOTO = "Take photo"
    const val C2G_CHOOSE_FROM_LIBRARY = "Choose from library"
    const val C2C_CONFIRM_FIRST_LOOK = "Make my first look"
    const val C2C_CONFIRM_SAVE = "Save photos"
    // C2J added Oct 31, 2026 (RW-16.15 — spec §7 amended by dated
    // note): the save confirmation, shown after a successful commit
    // before the flow dismisses. Verbatim-identical to the iOS deck
    // (MakeItMeCopy.saveConfirmed). Neither platform confirmed a
    // save before this — the flow just vanished, which read to the
    // user as "my photos didn't save."
    const val C2J_SAVE_CONFIRMED = "Your photos are saved."
    const val C3A_REJECT_FACE_UNCLEAR = "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
    const val C3B_REJECT_MULTIPLE = "This photo has more than one person in it. Pick one that's just you."
    const val C3C_REJECT_TOO_DARK = "This one is too dark to use. Try a photo in brighter light."
    const val C3D_REJECT_TOO_CLOSE = "This one is cropped too close. A head-and-shoulders or half-body photo works best."
    // C3E/C3F added Nov 2, 2026 (RW-26 guided-selfie follow-up): the
    // retake prompts the guided capture and the upload door share —
    // same checks, same strings, camera offered first.
    const val C3E_RETAKE_GUIDED = "Let's retake that one with the camera — face the light, keep your whole face inside the oval, and hold still."
    const val C3F_RETAKE_UPLOAD = "That photo can't be used. Retake with the camera, or choose a brighter photo that's just you, face forward."
    // C3G added Nov 3, 2026 (WS-6, F-1): the roll gate's retake line —
    // a photo whose face reads sideways beyond correction is retaken
    // upright, never uploaded rotated (the server's `rotated` key is
    // the same verdict one layer up).
    const val C3G_REJECT_ROTATED = "This photo came out sideways. Retake it holding your phone upright, or pick a photo that's already upright."

    // G — First generation (spec §2.4). G3 (PROPOSAL P1) intentionally absent.
    // G4 was silence by spec — SUPERSEDED Nov 1, 2026 (RW-16.16, spec
    // §7 amended by dated note): a terminal failure is now a visible,
    // durable state at the hero (G5 + the G6 retry action), driven by
    // the server's per-cell look state. G2 amended the same day: the
    // "About 10 seconds" figure was false against a measured 72 s job
    // and a 2–4 minute backend norm — the line no longer promises a
    // number the system misses.
    // GEN-3 A-HERO (WS-A(2)): the ONE hero pill's copy. MAKING is
    // Sol's Nov 4 wording for the on-photo generating state;
    // GET_UNLIMITED is the free tier's paywall door.
    // GEN-3 DESIGN DELTA (Sol's mockup review, Nov 4 — FINAL mocked
    // states, superseding the A-HERO wording): states (b)(c)(d)
    // carry the Make It Me script mark; (b) SCAFFOLD is the script
    // mark + "Your photo" — NOTHING MORE (the "made for close-by
    // weather" line is OUT, Sol found it confusing;
    // ScaffoldRenderPolicy.homeBadge carries the same shortened
    // line, this constant is the pill's fallback if that policy
    // ever returns null); (c) the free-tier count reads
    // "N of 5 looks" per the approved mock.
    const val HERO_PILL_MAKING = "Making this you…"
    const val HERO_PILL_SCAFFOLD = "Your photo"
    const val HERO_PILL_PREMIUM = "Premium"
    const val HERO_PILL_GET_UNLIMITED = "Get unlimited"
    // GEN-5 C-4 AMENDED (Sol, Nov 5, 2026 ~06:28 CST): the FREE-tier
    // generating pill carries ONE small green upsell line, verbatim.
    // Premium generating states carry NO upsell (nothing left to
    // sell). Its truth: Premium looks are banked ahead, so next time
    // the look is already there — instant.
    const val HERO_PILL_MAKING_UPSELL = "Get it instantly next time"
    // GEN-5 ADDENDUM (Sol, Nov 5, 2026 ~06:30 CST): the EXHAUSTED
    // pill status — a free user who has used all 5 free looks. The
    // used-up count, stated honestly; the upsell is the main message.
    const val HERO_PILL_EXHAUSTED_COUNT = "5 of 5 used"

    /** The script mark the pill carries in states (b)(c)(d). */
    const val HERO_PILL_SCRIPT_MARK = "Make It Me"

    /** The pill's free-tier look count: "1 of 5 looks" (real server count). */
    fun heroPillLookCount(freeGenerationsUsed: Int): String =
        "$freeGenerationsUsed of 5 looks"

    const val G1_PROGRESS = "Making this outfit yours…"
    // GEN-3 A-HERO (X-8 copy audit): the pre-fix line promised "the
    // standard photo stays up until yours is done" — false in the
    // walkthrough's Overland Park frames, where no standard photo
    // ever stayed up (X-3). The claim is removed rather than
    // re-promised: WS-A(1)'s fallback makes a photo stay up wherever
    // one is truthfully servable, and the line now claims only what
    // is true in every state.
    const val G2_PROGRESS_SUB = "This usually takes a few minutes. Your recommendation is ready either way."
    // G5/G6 added Nov 1, 2026 (RW-16.16 — spec §7 amended by dated
    // note). One failure line for every reason class in this build;
    // the class travels in the payload for a later class-specific
    // pass. DECIDED Nov 1, 2026 (RW-16.16): Sol picked the final
    // wording from the options put to him — wired as this single
    // constant so the swap is a one-string change. Verbatim-
    // identical to the iOS deck (MakeItMeCopy.lookFailed).
    const val G5_LOOK_FAILED = "Your look couldn't be made from these photos — tap to try again."
    const val G6_RETRY = "Try again"

    // S — Settings (spec §3)
    const val S1_SECTION_HEADER = "Make It Me"
    const val S2_STATE_A_ROW = "See yourself in every outfit"
    const val S2_STATE_A_SUB = "Make your outfit photos from your own photos."
    const val S3_STATUS = "You're in your photos."
    // Gen-3 WS-D/X-9 (Nov 4, 2026): the status block's in-flight
    // line — while any look is generating, the block carries this
    // line instead of presenting the delivered count as settled.
    const val S26_MAKING_STATUS = "Making your look…"
    // Gen-3 WS-D: the Settings Premium offer card's CTA — a real
    // button that opens the paywall.
    const val S27_GET_PREMIUM = "Get Premium"
    // Gen-3 WS-D: the offer card's subscribed state line.
    const val S28_SUBSCRIBED_STATE = "You're subscribed."
    // RW-26 §H: subscribed = management only, NO sales copy — this is
    // a state fact, not a benefit line.
    // GEN-5 (RW-28 item 26): the verbatim subscribed line — middot
    // separator, capital Y, everywhere it renders.
    const val S5_STATE_C_SUB = "Subscribed · Your photo is ready before you open the app."
    // RW-26 §9 (frame 35 settings line, verbatim): the two benefit
    // CONCEPTS (readiness, no cap) in crafted language — the concept
    // labels themselves never print.
    const val S18_SETTINGS_PREMIUM_LINE = "Made ahead, without limit — every outfit is ready when you open the app."
    const val S6_PHOTOS_ROW = "Your photos"
    const val S6_PHOTOS_SUB = "View or replace"
    // S15–S17 added Oct 31, 2026 (RW-16.15 — spec §7 amended by dated
    // note): the Your Photos tile states. A tile that cannot load is
    // a labeled, tappable retry — never a silent blank box (the
    // contract iOS build (13) established with ReferenceTileLoader).
    const val S15_PHOTO_LOADING = "Loading"
    const val S16_PHOTO_LOAD_FAILED = "Couldn't load this photo"
    const val S17_PHOTO_RETRY = "Tap to retry"
    // S18/S19 added Oct 31, 2026 (RW-16.15 increment): the Your
    // Photos SCREEN-level load failure — the bounded load always
    // lands on content or this labeled retry state, never a long
    // spinner into blankness.
    const val S18_PHOTOS_LOAD_FAILED = "Couldn't load your photos"
    const val S19_TRY_AGAIN = "Try again"
    const val S7_REPLACE_NOTE = "Replacing your photos remakes today's look right away. Your other looks update as they're made again."
    // RW-26 frame 38: the epoch rule in plain words, on the selfie
    // management screen — a retake re-makes what's AHEAD and never
    // rewrites the Wear I've Run collection behind it.
    const val S20_EPOCH_RULE = "A new photo re-makes the looks ahead of you. It never changes the outfits already in your Wear I've Run collection."
    const val S8_STATE_B_SUBSCRIBE_ROW = "Every outfit, instantly"
    // Price-free by design (RW-16.30 pricing experiment, Nov 2 2026):
    // prices render ONLY from the fetched RevenueCat offering, so no
    // string in the paywall path may carry one — a user assigned a
    // $19.99/$29.99 experiment offering must never see the $9.99 price.
    const val S8_STATE_B_SUBSCRIBE_SUB = S18_SETTINGS_PREMIUM_LINE
    const val S9_MANAGE_SUBSCRIPTION = "Manage subscription"
    const val S10_DELETE_ROW = "Delete my photos"
    const val S11_DELETE_CONFIRM_TITLE = "Delete your photos?"
    const val S12_DELETE_CONFIRM_BODY = "Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make It Me turns off. This can't be undone."
    const val S13_DELETE_CONFIRM = "Delete my photos"
    const val S13_KEEP = "Keep my photos"
    const val S14_POST_DELETE = "Your photos and pictures are deleted."

    // S20–S25 added Nov 2, 2026 (RW-16.30, privacy decision 5a):
    // Delete my data — the full account deletion, one step beyond
    // S10–S14's photos-only erasure. The body keeps S12's scope
    // discipline (server + phone, what goes, no undo) and adds the
    // two facts the photos copy doesn't carry: the account itself
    // goes, and deleting data does NOT cancel a store subscription.
    // GEN-5 (RW-28 item 21): the settled Your Photos surface —
    // current-photo heading + arrival line, the epoch heading, and
    // the two separate replacement doors.
    const val S30_YOUR_CURRENT_PHOTO = "Your current photo"
    const val S31_CURRENT_PHOTO_LINE = "This is the photo your looks are made from."
    const val S32_EPOCH_HEADING = "A new photo starts a new set"
    const val S33_RETAKE_SELFIE = "Retake Selfie"
    const val S34_CHOOSE_DIFFERENT_PHOTO = "Choose a Different Photo"
    const val S20_DELETE_DATA_ROW = "Delete my data"
    // GEN-5 (RW-28 item 25): the destructive rows' own heading,
    // quarantined at the very end of Settings.
    const val S29_DANGER_ZONE_HEADING = "Danger zone"
    const val S21_DELETE_DATA_CONFIRM_TITLE = "Delete all your data?"
    const val S22_DELETE_DATA_CONFIRM_BODY = "Everything RunWear holds for you will be deleted from our server and this phone: your photos, every picture made from them, and your account itself. This can't be undone. Deleting your data does not cancel a subscription — you can manage that from your store account."
    const val S23_DELETE_DATA_CONFIRM = "Delete my data"
    const val S24_DELETE_DATA_KEEP = "Keep my data"
    const val S25_POST_DELETE_DATA = "Your data and account are deleted."

    /** S4 — free-tier sub-status with the user's real count. */
    fun s4FreeUsed(used: Int): String =
        if (used >= 5) "All 5 free looks used" else "$used of 5 free looks used"

    // U — Upsell and subscription sheet (spec §5)
    const val U1A_EYEBROW = "MAKE IT ME"
    const val U1B_HEADLINE = "This outfit isn't you — yet."
    const val U1C_BODY = "You've seen yourself in 5 outfits. This is a new one, so it wears a standard photo. Subscribe, and RunWear makes your photo for every outfit in your weather ahead of time — you, on screen the moment you open the app."
    const val U1D_PRIMARY = "See myself in every outfit"
    const val U1E_NOT_NOW = "Not now"
    // RW-26 frame 22: the paywall's title is the "Make It Me" SCRIPT
    // wordmark (rendered in Great Vibes by the sheet); this string is
    // its text. The highlighted card is "Premium" / "All of Make It
    // Me." and the CTA carries NO price.
    const val U2A_SHEET_TITLE = "Make It Me"
    const val U2D_FREE_BAR = "Free plan · 5 looks included"
    const val U2E_HOW_PREMIUM_WORKS = "How Premium works"
    const val U2F_HOW_PREMIUM_WORKS_BODY = "Looks of you for every scenario. This week's weather is made first; the rest are made as you need them, and each one is banked for good once it's made."
    const val U2G_CARD_TITLE = "Premium"
    const val U2H_CARD_LINE = "All of Make It Me."
    const val U2I_CTA = "Keep going with Premium"
    /** Free-looks meter line (frame 22 verbatim pattern, real numbers). */
    fun u2MeterLine(used: Int): String =
        "$used of 5 used · Free looks never expire — and your first ones take about a minute to make."
    // Fallback row labels shown only until the current offering
    // resolves (the rows stay disabled until then). Price-free on
    // purpose — see S8 note above. Resolved rows are built by
    // [monthlyRowLabel] / [annualRowLabel] from the offering's own
    // formatted prices.
    const val U2B_MONTHLY = "Monthly"
    const val U2B_ANNUAL = "Annual"

    /** U2 monthly row, from the offering's formatted price.
     * GEN-5 (RW-28 item 29): the row names the plan and reads
     * "$0.99/month" — the compact term form of record. */
    fun monthlyRowLabel(monthlyFormatted: String): String = "Monthly — $monthlyFormatted/month"

    /**
     * U2 annual row, from the offering's formatted price. Gen-3
     * WS-F (Nov 4, 2026): the "about N% less than paying monthly"
     * comparison is REMOVED — the annual value is now framed by
     * the per-run fine print ([perRunLine]) instead, with its
     * basis stated. The row itself is just the fetched price + term.
     */
    fun annualRowLabel(
        annualFormatted: String,
        annualAmountMicros: Long,
        monthlyAmountMicros: Long?
    ): String = "Annual — $annualFormatted/yr"

    /** Runs per year behind the per-run framing: 4.5 runs/week × 52. */
    const val RUNS_PER_YEAR_BASIS = 4.5 * 52.0

    /**
     * Whole cents per run DERIVED from the displayed product's
     * annual price: annual cents ÷ (4.5 × 52) runs, rounded to the
     * nearest cent. Control $9.99 → 4¢; test arms $19.99 → 9¢ and
     * $29.99 → 13¢. Never a hardcoded figure.
     */
    fun perRunCents(annualAmountMicros: Long): Int =
        Math.round((annualAmountMicros / 10_000.0) / RUNS_PER_YEAR_BASIS).toInt()

    /** The WS-F per-run line, with its basis stated in the line. */
    fun perRunLine(annualFormatted: String, annualAmountMicros: Long): String =
        "At four to five runs a week, $annualFormatted/year works out to only about ${perRunCents(annualAmountMicros)}¢ per run."

    /**
     * The paywall fine print. GEN-5 (RW-28 item 29): the per-run
     * line moved OUT of the fine print to directly under the annual
     * price; the fine print is the settled U2C text alone. Kept as
     * a function so every surface resolves the same text.
     */
    fun finePrint(annualFormatted: String, annualAmountMicros: Long): String =
        U2C_FINE_PRINT

    /**
     * Gen-3 WS-D: the Settings Premium offer card's content, built
     * from the fetched offering — the card is a legible offer:
     * the name "Premium", the parameterized price with term, the
     * settled two-benefit line, and a real CTA. For a subscriber
     * the card shows the subscribed state instead of the offer
     * (no price, no CTA).
     */
    data class PremiumOfferCardContent(
        val title: String,
        val priceLine: String?,
        val benefitLine: String,
        val ctaLabel: String?,
        val subscribed: Boolean,
        // GEN-5 (C-5/C-6): the Settings card also carries the
        // monthly price as a modest, tappable element under the
        // annual headline — never a co-equal button.
        val monthlyLine: String? = null
    )

    fun premiumOfferCard(
        annualFormatted: String?,
        subscribed: Boolean,
        monthlyFormatted: String? = null
    ): PremiumOfferCardContent =
        if (subscribed) {
            PremiumOfferCardContent(
                title = U2G_CARD_TITLE,
                priceLine = null,
                benefitLine = S28_SUBSCRIBED_STATE,
                ctaLabel = null,
                subscribed = true
            )
        } else {
            PremiumOfferCardContent(
                title = U2G_CARD_TITLE,
                // GEN-5 (RW-28 item 26): the price reads "$9.99/yr"
                // in the compact term form, everywhere.
                priceLine = annualFormatted?.let { "$it/yr" },
                benefitLine = S18_SETTINGS_PREMIUM_LINE,
                ctaLabel = S27_GET_PREMIUM,
                subscribed = false,
                monthlyLine = monthlyFormatted?.let { "Monthly — $it/month" }
            )
        }

    // GEN-5 (RW-28 item 29): the Android fine print names the
    // store this build is actually sold through — Google Play.
    const val U2C_FINE_PRINT = "Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about 10 seconds to make the first time — after that, it's ready instantly. Cancel any time in your Google Play subscriptions."

    // GEN-3 CONVERGENCE BAND (Sol's settled wording, Nov 4 — word
    // for word; do not paraphrase): the home-sheet band a Premium
    // user sees while the bank converges. COUNT RULED (Sol,
    // Nov 4 — FINAL): NO numeric count is shown — the earlier
    // "N of your M looks are ready" line is withdrawn for good
    // (its total had no basis in the generation model); the
    // progress bar's fraction still comes from the real banking
    // state, only the number is withheld. Neither 24 nor 48 is
    // hard-coded anywhere. (The FREE tier's own "X of 5" counters
    // are a different surface and are KEPT, per Sol.)
    const val BAND_MAKING_HEADING = "Making your looks"
    const val BAND_MAKING_LINE = "We're generating your outfits across all running conditions, so it will be ready when you need it."

    // RW-26 §9 frame 34: the first-look band — the recommended pair,
    // verbatim. The concept labels ("immediate results"/"unlimited
    // results") must NOT appear as copy anywhere.
    const val FIRST_LOOK_BAND_READINESS = "Ready the moment you open the app — made ahead, never while you wait."
    const val FIRST_LOOK_BAND_NO_CAP = "Every outfit, every day — no number to run out of."
    const val FIRST_LOOK_PRIMARY = "See My Outfit"
    // GEN-3 FIRST-LOOK RULING (Sol, Nov 4): the finished surface's
    // CTA reads "Upgrade to Premium" (was "See Premium"). Scope is
    // THIS surface only — other surfaces' premium wording is
    // untouched (Settings' "Get Premium" is approved as mocked).
    const val FIRST_LOOK_SECOND_DOOR = "Upgrade to Premium"
    // WS-10 (frame 34 verbatim): the band's header, the moment's
    // headline, and the honest count line — the onboarding look is
    // look 1 of 5 (design §8 Q5 ruling), stated in the frame's own
    // words, never as a surprise later.
    // GEN-3 A-HERO (WS-B): the first-look moment's FAILED sub-line —
    // the honest companion to G5: a failed look never charged the
    // free counter, and the retry lives with the outfit.
    const val FIRST_LOOK_FAILED_SUB =
        "No look was used. You can try again any time from your outfit."
    const val FIRST_LOOK_BAND_HEADER = "With Premium"
    const val FIRST_LOOK_HEADLINE = "You're in the photo now."
    const val FIRST_LOOK_COUNT_LINE =
        "That was look 1 of your 5 free looks — 4 remain, whenever you want them."

    /**
     * GEN-5 (RW-29 FA-6): the first-look count line COMPOSED from
     * the shared free-look meter — the gen-4 surface rendered the
     * hard-coded "look 1 of 5" constant whatever the meter said,
     * so a user whose first-look moment arrived on a later look
     * was told a count that was not theirs. [FIRST_LOOK_COUNT_LINE]
     * remains the canonical wording for the first look itself
     * (used = 1); every other count composes from the same words.
     */
    fun firstLookCountLine(used: Int, total: Int = 5): String {
        val shownUsed = used.coerceIn(1, total)
        val remaining = (total - shownUsed).coerceAtLeast(0)
        val remainClause = if (remaining == 1) {
            "1 remains, whenever you want it."
        } else {
            "$remaining remain, whenever you want them."
        }
        return "That was look $shownUsed of your $total free looks — $remainClause"
    }

    /** Frame 34's heading: "Your first look · Today, New York". */
    fun firstLookHeading(simplePlace: String?): String =
        if (simplePlace.isNullOrBlank()) {
            "Your first look · Today"
        } else {
            "Your first look · Today, $simplePlace"
        }

    /**
     * The consent-text version recorded with the backend on opt-in
     * (personal_consents.consent_version). Bump when §7's C1 body changes.
     */
    const val CONSENT_VERSION = "rw14-consent-v1"

    /**
     * Operational (non-spec) transport-failure line for the upload flow.
     * The spec defines no string for a dead network — this is UI plumbing,
     * not product copy, and is flagged as such in the build report.
     */
    const val TRANSPORT_ERROR = "Couldn't reach the server. Check your connection and try again."
}
